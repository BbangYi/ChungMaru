"""
v3 문장 분류 모델 학습 — mDeBERTa-v3-base.

설계:
  - 백본: microsoft/mdeberta-v3-base (다국어, 영어/한국어/code-switch 모두 토큰화 가능)
  - 헤드: multi-label 3 heads (profanity / toxicity / hate)
  - Loss: BCEWithLogits + label_mask + class-weighted pos_weight + label smoothing 0.05
    * label_mask: KOLD·K-HATERS는 profanity를 annotate하지 않으므로 해당 손실 역전파 제외
    * dataset conditioning embedding 제거 — 학습/추론 조건 불일치(use_mean_ds)로 FP 폭발
  - Optimizer: AdamW, lr 2e-5(encoder) / 1e-4(head)
  - 5 epochs, warmup 10%, weight_decay 0.01, mixed precision (fp16)
  - Early stop: val macro-F1
  - Per-class threshold calibration: validation set grid search

입력:
  data/v3/train.jsonl
  data/v3/val.jsonl
  data/v3/test.jsonl
  (옵션) data/v3/eval_temp.jsonl
  (옵션) data/v3/eval_codeswitch.jsonl

출력:
  models/v3/             # best 모델 + tokenizer
  models/v3/head.pt      # classifier 가중치
  models/v3/thresholds.json        # per-class 최적 threshold
  models/v3/train_report.json      # 학습 로그 + 최종 평가

사용법 (backend/training/ 기준):
  python train_v3.py
  python train_v3.py --epochs 5 --batch 32
  python train_v3.py --eval-only --model ../../models/v3
"""
import argparse
import json
import os
import sys
import time
from collections import Counter
from pathlib import Path

try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import Dataset, DataLoader
from sklearn.metrics import f1_score, precision_recall_fscore_support
from transformers import (
    AutoTokenizer,
    AutoModel,
    get_linear_schedule_with_warmup,
)

from label_schema import LABEL_NAMES
from normalizer_v3 import normalize_text

# ─────────────────────────────────────────────
# 경로 / 상수
# ─────────────────────────────────────────────
_HERE = Path(__file__).resolve().parent
_BACKEND = _HERE.parent
_PROJECT = _BACKEND.parent
DATA_DIR = _PROJECT / "data" / "v3"
MODELS_DIR = _PROJECT / "models"
OUT_DIR = MODELS_DIR / "v3"

TRAIN_FILE = DATA_DIR / "train.jsonl"
VAL_FILE = DATA_DIR / "val.jsonl"
TEST_FILE = DATA_DIR / "test.jsonl"
EVAL_TEMP_FILE = DATA_DIR / "eval_temp.jsonl"
EVAL_CS_FILE = DATA_DIR / "eval_codeswitch.jsonl"

BASE_MODEL = "microsoft/mdeberta-v3-base"
N_LABELS = 3
LABEL_SMOOTHING = 0.05
DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")


# ─────────────────────────────────────────────
# JSONL I/O
# ─────────────────────────────────────────────
def _load_jsonl(path: Path):
    if not path.exists():
        return []
    with open(path, encoding="utf-8") as f:
        return [json.loads(ln) for ln in f if ln.strip()]


# ─────────────────────────────────────────────
# Dataset
# ─────────────────────────────────────────────
class V3Dataset(Dataset):
    def __init__(self, rows, tokenizer, max_length=160):
        self.rows = rows
        self.tokenizer = tokenizer
        self.max_length = max_length

    def __len__(self):
        return len(self.rows)

    def __getitem__(self, idx):
        r = self.rows[idx]
        enc = self.tokenizer(
            normalize_text(r["text"]),
            truncation=True,
            max_length=self.max_length,
            padding="max_length",
            return_tensors="pt",
        )
        labels = r.get("labels") or [0, 0, 0]
        # label_mask: 0이면 해당 (샘플, 라벨) 손실 역전파 제외
        # KOLD·K-HATERS는 profanity를 annotate하지 않으므로 mask[0]=0
        label_mask = r.get("label_mask") or [1, 1, 1]
        return {
            "input_ids": enc["input_ids"].squeeze(0),
            "attention_mask": enc["attention_mask"].squeeze(0),
            "labels": torch.tensor(labels, dtype=torch.float),
            "label_mask": torch.tensor(label_mask, dtype=torch.float),
        }


# ─────────────────────────────────────────────
# 모델 — mDeBERTa + dataset embedding + multi-label head
# ─────────────────────────────────────────────
class MDeBertaV3Multilabel(nn.Module):
    """mDeBERTa-v3 + mean pooling + multi-label head.

    Dataset conditioning embedding 제거 이유:
      학습 시 dataset_id를 알지만 서비스 추론 시 알 수 없으므로
      train/inference 조건 불일치 → profanity FP 폭발.
      annotation bias는 label_mask로 손실 단계에서 제어.
    """
    def __init__(self, base_model_name=BASE_MODEL, n_labels=N_LABELS, dropout=0.1):
        super().__init__()
        self.encoder = AutoModel.from_pretrained(base_model_name)
        h = self.encoder.config.hidden_size
        self.dropout = nn.Dropout(dropout)
        self.classifier = nn.Sequential(
            nn.Linear(h, h),
            nn.GELU(),
            nn.Dropout(dropout),
            nn.Linear(h, n_labels),
        )

    def forward(self, input_ids, attention_mask, **kwargs):
        out = self.encoder(input_ids=input_ids, attention_mask=attention_mask)
        last = out.last_hidden_state                                    # (B, T, H)
        mask = attention_mask.unsqueeze(-1).float()                     # (B, T, 1)
        pooled = (last * mask).sum(1) / mask.sum(1).clamp(min=1e-6)    # (B, H)
        return self.classifier(self.dropout(pooled))


# ─────────────────────────────────────────────
# Loss — class-weighted BCE + label smoothing
# ─────────────────────────────────────────────
def _make_pos_weight(rows):
    arr = np.array([r["labels"] for r in rows], dtype=np.float32)
    masks = np.array([r.get("label_mask") or [1, 1, 1] for r in rows], dtype=np.float32)
    # profanity pos_weight: label_mask[0]=0인 비지도 소스(KOLD·K-HATERS) 제외
    # → 실제 학습에 사용되는 샘플 기준으로만 불균형 계산
    pos = (arr * masks).sum(axis=0)
    neg = ((1 - arr) * masks).sum(axis=0)
    pw = neg / np.clip(pos, 1, None)
    pw = np.clip(pw, 1.0, 20.0)
    return torch.tensor(pw, dtype=torch.float)


def _bce_loss(logits, targets, pos_weight, label_mask, smoothing=LABEL_SMOOTHING):
    """BCE + label smoothing + label_mask.

    label_mask: (B, 3) float — 0이면 해당 (sample, label) 쌍을 손실에서 제외.
    KOLD·K-HATERS의 profanity(mask[:,0]=0)가 역전파에서 제외되어 FP 폭발 방지.
    """
    targets = targets * (1 - smoothing) + 0.5 * smoothing
    # reduction='none'으로 (B, 3) 손실 행렬 계산
    pw = pos_weight.to(logits.device)
    loss_matrix = F.binary_cross_entropy_with_logits(
        logits, targets, pos_weight=pw, reduction="none"
    )
    # mask 적용 후 유효한 (sample, label) 쌍의 평균
    mask = label_mask.to(logits.device)
    masked = loss_matrix * mask
    n_valid = mask.sum().clamp(min=1.0)
    return masked.sum() / n_valid


# ─────────────────────────────────────────────
# 평가 유틸
# ─────────────────────────────────────────────
def _per_label_metrics(y_true: np.ndarray, y_pred: np.ndarray, y_mask: np.ndarray | None = None):
    """label_mask=0인 행은 해당 라벨 평가에서 제외.

    civil_comments·KOLD처럼 profanity를 annotate하지 않은 소스가 val에 포함될 때
    labels[0]=0(미라벨)인 행을 profanity FP로 집계하는 평가 왜곡을 방지.
    """
    out = {}
    for i, name in enumerate(LABEL_NAMES):
        if y_mask is not None:
            keep = y_mask[:, i].astype(bool)
            yt, yp = y_true[keep, i], y_pred[keep, i]
        else:
            yt, yp = y_true[:, i], y_pred[:, i]
        p, r, f, _ = precision_recall_fscore_support(yt, yp, average="binary", zero_division=0)
        out[name] = {
            "precision": round(float(p), 4),
            "recall": round(float(r), 4),
            "f1": round(float(f), 4),
            "support": int(yt.sum()),
            "eval_rows": int(len(yt)),
        }
    out["macro_f1"] = round(float(np.mean([out[n]["f1"] for n in LABEL_NAMES])), 4)
    return out


def _calibrate_thresholds(y_true: np.ndarray, y_prob: np.ndarray, y_mask: np.ndarray | None = None):
    """validation 기준 per-class threshold grid search (F1 max).

    label_mask=0인 행은 threshold 탐색에서도 제외 — 미라벨 행을 FP로 집계하면
    threshold가 비정상적으로 높아지는 왜곡 방지.
    """
    thresholds = []
    for i in range(y_true.shape[1]):
        if y_mask is not None:
            keep = y_mask[:, i].astype(bool)
            yt, yp = y_true[keep, i], y_prob[keep, i]
        else:
            yt, yp = y_true[:, i], y_prob[:, i]
        best_t, best_f1 = 0.5, -1.0
        for t in np.arange(0.2, 0.81, 0.02):
            pred = (yp >= t).astype(int)
            f = f1_score(yt, pred, zero_division=0)
            if f > best_f1:
                best_f1, best_t = f, float(t)
        thresholds.append(round(best_t, 2))
    return thresholds


# ─────────────────────────────────────────────
# 추론
# ─────────────────────────────────────────────
@torch.no_grad()
def _infer(model, loader):
    model.eval()
    all_prob, all_true, all_mask = [], [], []
    for batch in loader:
        ids = batch["input_ids"].to(DEVICE)
        mask = batch["attention_mask"].to(DEVICE)
        logits = model(ids, mask)
        prob = torch.sigmoid(logits).cpu().numpy()
        all_prob.append(prob)
        all_true.append(batch["labels"].numpy())
        all_mask.append(batch["label_mask"].numpy())
    return np.concatenate(all_prob), np.concatenate(all_true), np.concatenate(all_mask)


def evaluate(model, rows, tokenizer, batch=64, thresholds=None, name="eval"):
    if not rows:
        return None
    ds = V3Dataset(rows, tokenizer)
    loader = DataLoader(ds, batch_size=batch, num_workers=0)
    prob, true, lmask = _infer(model, loader)
    if thresholds is None:
        thresholds = [0.5] * N_LABELS
    pred = (prob >= np.array(thresholds)).astype(int)
    metrics = _per_label_metrics(true.astype(int), pred, lmask)
    print(f"  [{name}] macro_f1={metrics['macro_f1']}  thresholds={thresholds}")
    for n in LABEL_NAMES:
        v = metrics[n]
        print(f"    {n}: F1={v['f1']}  P={v['precision']}  R={v['recall']}  sup={v['support']}  eval_rows={v['eval_rows']}")
    return metrics


# ─────────────────────────────────────────────
# 학습 루프
# ─────────────────────────────────────────────
def train(args):
    print("=" * 60)
    print(f"v3 train (mDeBERTa-v3)")
    print(f"  device: {DEVICE}")
    print(f"  base:   {BASE_MODEL}")
    print(f"  epochs: {args.epochs}  batch: {args.batch}")
    print(f"  lr:     enc={args.lr_enc}  head={args.lr_head}")
    print("=" * 60)

    train_rows = _load_jsonl(TRAIN_FILE)
    val_rows = _load_jsonl(VAL_FILE)
    test_rows = _load_jsonl(TEST_FILE)
    if not train_rows or not val_rows:
        print("[ERROR] train/val 데이터 없음 — build_train_val.py 먼저")
        return

    print(f"\n  train: {len(train_rows):,}  val: {len(val_rows):,}  test: {len(test_rows):,}")
    by_src = Counter(r["source"] for r in train_rows)
    print(f"  train sources: {dict(by_src)}")

    pos_weight = _make_pos_weight(train_rows)
    print(f"  pos_weight: {pos_weight.tolist()}")

    # ── resume: 저장된 best checkpoint 에서 warm start ──
    resume_path = Path(args.resume).resolve() if args.resume else None
    resumed_best_f1 = -1.0
    resumed_best_thresholds = None
    if resume_path and resume_path.exists():
        print(f"\n  [RESUME] loading weights from {resume_path}")
        tokenizer = AutoTokenizer.from_pretrained(resume_path)
        # encoder 는 resume 경로에서, head 는 head.pt 에서
        model = MDeBertaV3Multilabel(base_model_name=str(resume_path)).to(DEVICE)
        head_pt = resume_path / "head.pt"
        if head_pt.exists():
            head_state = torch.load(head_pt, map_location=DEVICE)
            if "dataset_embed" in head_state:
                print(f"  [RESUME] WARNING: head.pt에 dataset_embed 키 발견")
                print(f"           이 checkpoint는 구버전(dataset_embed 포함)으로 학습됨")
                print(f"           classifier shape 불일치로 로드 실패할 수 있음")
                print(f"           처음부터 학습 권장: --resume 없이 실행할 것")
            try:
                model.classifier.load_state_dict(head_state["classifier"])
                print(f"  [RESUME] head.pt loaded (classifier)")
            except RuntimeError as e:
                print(f"  [RESUME] ERROR: classifier 로드 실패 — {e}")
                print(f"           구버전 checkpoint 와 shape 불일치. fresh 학습으로 전환합니다.")
                tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL)
                model = MDeBertaV3Multilabel().to(DEVICE)
        else:
            print(f"  [RESUME] WARNING: head.pt not found — head will be random-init")

        # 이전 best threshold 도 복원 (checkpoint 덮어쓰기 방지용 baseline)
        th_path = resume_path / "thresholds.json"
        if th_path.exists():
            try:
                th_saved = json.loads(th_path.read_text(encoding="utf-8"))
                resumed_best_thresholds = [th_saved[n] for n in LABEL_NAMES]
                print(f"  [RESUME] prev thresholds = {resumed_best_thresholds}")
            except Exception:
                pass

        # 이전 학습이 train_report.json 을 못 남겼을 수도 있음 — 있으면 best_f1 도 복원
        prev_report = resume_path / "train_report.json"
        if prev_report.exists():
            try:
                prev = json.loads(prev_report.read_text(encoding="utf-8"))
                resumed_best_f1 = float(prev.get("final", {}).get("best_val_macro_f1", -1.0))
                print(f"  [RESUME] previous best_val_macro_f1 = {resumed_best_f1}")
            except Exception:
                pass

        # train_report 가 없으면 현재 checkpoint 로 val 돌려서 baseline 을 측정
        if resumed_best_f1 < 0:
            print(f"  [RESUME] no train_report.json — measuring baseline on val set...")
            _val_ds_tmp = V3Dataset(val_rows, tokenizer)
            _val_loader_tmp = DataLoader(_val_ds_tmp, batch_size=args.batch * 2, num_workers=0)
            _vp, _vt, _vm = _infer(model, _val_loader_tmp)
            _th = resumed_best_thresholds or _calibrate_thresholds(_vt.astype(int), _vp, _vm)
            _pred = (_vp >= np.array(_th)).astype(int)
            _m = _per_label_metrics(_vt.astype(int), _pred, _vm)
            resumed_best_f1 = _m["macro_f1"]
            resumed_best_thresholds = _th
            print(f"  [RESUME] baseline val macro_f1 = {resumed_best_f1}  thresholds={_th}")
    else:
        tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL)
        model = MDeBertaV3Multilabel().to(DEVICE)

    train_ds = V3Dataset(train_rows, tokenizer)
    val_ds = V3Dataset(val_rows, tokenizer)
    train_loader = DataLoader(train_ds, batch_size=args.batch, shuffle=True, num_workers=0)
    val_loader = DataLoader(val_ds, batch_size=args.batch * 2, num_workers=0)

    # 파라미터 그룹 — encoder vs head
    enc_params = list(model.encoder.parameters())
    head_params = list(model.classifier.parameters())
    optimizer = torch.optim.AdamW(
        [
            {"params": enc_params, "lr": args.lr_enc, "weight_decay": 0.01},
            {"params": head_params, "lr": args.lr_head, "weight_decay": 0.01},
        ]
    )
    total_steps = len(train_loader) * args.epochs
    scheduler = get_linear_schedule_with_warmup(
        optimizer,
        num_warmup_steps=int(total_steps * 0.1),
        num_training_steps=total_steps,
    )

    scaler = torch.cuda.amp.GradScaler(enabled=(DEVICE.type == "cuda"))
    log = {"epochs": []}
    # resume 시 baseline 유지 — 이보다 나빠진 epoch 는 checkpoint 를 덮어쓰지 않음
    best_f1 = resumed_best_f1 if resumed_best_f1 >= 0 else -1.0
    best_thresholds = resumed_best_thresholds or [0.5, 0.5, 0.5]
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    if resumed_best_f1 >= 0:
        print(f"  [RESUME] best_f1 baseline = {best_f1}  (epoch 이 이 값 초과해야만 저장)")

    no_improve = 0
    for epoch in range(args.epochs):
        model.train()
        t0 = time.time()
        total_loss = 0.0

        for step, batch in enumerate(train_loader):
            ids = batch["input_ids"].to(DEVICE)
            mask = batch["attention_mask"].to(DEVICE)
            labels = batch["labels"].to(DEVICE)
            lmask = batch["label_mask"].to(DEVICE)

            optimizer.zero_grad()
            with torch.cuda.amp.autocast(enabled=(DEVICE.type == "cuda")):
                logits = model(ids, mask)
                loss = _bce_loss(logits, labels, pos_weight, lmask)

            scaler.scale(loss).backward()
            scaler.unscale_(optimizer)
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            scaler.step(optimizer)
            scaler.update()
            scheduler.step()

            total_loss += loss.item()
            if step % 200 == 0:
                print(f"  ep{epoch+1} step {step}/{len(train_loader)} loss={loss.item():.4f}",
                      flush=True)

        avg_loss = total_loss / max(1, len(train_loader))
        elapsed = time.time() - t0

        val_prob, val_true, val_lmask = _infer(model, val_loader)
        thresholds = _calibrate_thresholds(val_true.astype(int), val_prob, val_lmask)
        val_pred = (val_prob >= np.array(thresholds)).astype(int)
        val_metrics = _per_label_metrics(val_true.astype(int), val_pred, val_lmask)

        print(f"\n  ep{epoch+1} done — loss={avg_loss:.4f}  {elapsed:.0f}s")
        print(f"    val macro_f1={val_metrics['macro_f1']}  thresholds={thresholds}")
        for n in LABEL_NAMES:
            v = val_metrics[n]
            print(f"      {n}: F1={v['f1']}  P={v['precision']}  R={v['recall']}")

        log["epochs"].append({
            "epoch": epoch + 1,
            "train_loss": round(avg_loss, 4),
            "val_metrics": val_metrics,
            "thresholds": thresholds,
            "time_sec": round(elapsed, 1),
        })

        if val_metrics["macro_f1"] > best_f1:
            best_f1 = val_metrics["macro_f1"]
            best_thresholds = thresholds
            # 저장
            model.encoder.save_pretrained(OUT_DIR)
            tokenizer.save_pretrained(OUT_DIR)
            torch.save(
                {"classifier": model.classifier.state_dict()},
                OUT_DIR / "head.pt",
            )
            (OUT_DIR / "thresholds.json").write_text(
                json.dumps(
                    {n: t for n, t in zip(LABEL_NAMES, best_thresholds)},
                    ensure_ascii=False, indent=2,
                ),
                encoding="utf-8",
            )
            print(f"    -> best 저장 (macro_f1={best_f1})")
            no_improve = 0
        else:
            no_improve += 1
            if no_improve >= args.patience:
                print(f"    early stop (no improve {no_improve} epochs)")
                break

    print(f"\n학습 완료. best val macro_f1={best_f1}")

    # ── 테스트 + 외부 평가셋 ─────────────────────
    final = {"best_val_macro_f1": best_f1, "best_thresholds": best_thresholds}

    if test_rows:
        print("\n[TEST]")
        final["test"] = evaluate(model, test_rows, tokenizer,
                                 thresholds=best_thresholds, name="test")

    eval_rows = _load_jsonl(EVAL_TEMP_FILE)
    if eval_rows:
        print("\n[EVAL_TEMP] APEACH + HateXplain")
        final["eval_temp"] = evaluate(model, eval_rows, tokenizer,
                                      thresholds=best_thresholds, name="eval_temp")

    cs_rows = _load_jsonl(EVAL_CS_FILE)
    if cs_rows:
        print("\n[EVAL_CS] code-switching stress")
        final["eval_codeswitch"] = evaluate(model, cs_rows, tokenizer,
                                            thresholds=best_thresholds, name="eval_codeswitch")
        from collections import defaultdict
        by_pat = defaultdict(list)
        for r in cs_rows:
            pat = r.get("pattern", "?")
            if pat == "ko_only_baseline":
                continue
            by_pat[pat].append(r)
        per_pat = {}
        for pat, rs in by_pat.items():
            print(f"\n  pattern={pat}")
            per_pat[pat] = evaluate(model, rs, tokenizer,
                                    thresholds=best_thresholds, name=pat)
        final["eval_codeswitch_by_pattern"] = per_pat

    log["final"] = final
    (OUT_DIR / "train_report.json").write_text(
        json.dumps(log, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"\n  saved: {(OUT_DIR / 'train_report.json').name}")


# ─────────────────────────────────────────────
# Eval-only 모드 (이미 학습된 v3 모델 재평가)
# ─────────────────────────────────────────────
def _load_trained(path: Path):
    tokenizer = AutoTokenizer.from_pretrained(path)
    model = MDeBertaV3Multilabel(base_model_name=str(path)).to(DEVICE)
    head = torch.load(path / "head.pt", map_location=DEVICE)
    model.classifier.load_state_dict(head["classifier"])
    thresholds = json.loads((path / "thresholds.json").read_text(encoding="utf-8"))
    th = [thresholds[n] for n in LABEL_NAMES]
    return model, tokenizer, th


def eval_only(args):
    path = Path(args.model).resolve()
    print(f"v3 eval-only: {path}")
    model, tokenizer, thresholds = _load_trained(path)

    for tag, fp in [
        ("test", TEST_FILE),
        ("eval_temp", EVAL_TEMP_FILE),
        ("eval_codeswitch", EVAL_CS_FILE),
    ]:
        rows = _load_jsonl(fp)
        if not rows:
            continue
        print(f"\n[{tag}] {len(rows):,} rows")
        evaluate(model, rows, tokenizer, thresholds=thresholds, name=tag)


# ─────────────────────────────────────────────
# CLI
# ─────────────────────────────────────────────
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--epochs", type=int, default=5)
    parser.add_argument("--batch", type=int, default=32)
    parser.add_argument("--lr-enc", type=float, default=2e-5)
    parser.add_argument("--lr-head", type=float, default=1e-4)
    parser.add_argument("--patience", type=int, default=2)
    parser.add_argument("--resume", default=None,
                        help="저장된 best checkpoint 경로에서 warm start "
                             "(encoder + head 가중치만 load, optimizer 는 새로)")
    parser.add_argument("--eval-only", action="store_true")
    parser.add_argument("--model", default=str(OUT_DIR),
                        help="(--eval-only 전용) 평가할 v3 모델 경로")
    args = parser.parse_args()

    if args.eval_only:
        eval_only(args)
    else:
        train(args)


if __name__ == "__main__":
    main()
