"""
[3단계] 문장 분류 래퍼
- v3 mDeBERTa encoder + head.pt 조합 우선 지원
- 기존 AutoModelForSequenceClassification 모델도 fallback 지원
- 출력: is_profane, is_toxic, (is_hate)
"""
import json
import os
from pathlib import Path

import torch
import torch.nn as nn
from transformers import AutoModel, AutoTokenizer, AutoModelForSequenceClassification

MODEL_PATH = str(Path(__file__).resolve().parents[1] / "v3")
LABEL_NAMES = ["profanity", "toxicity", "hate"]
DEFAULT_THRESHOLDS = {
    "profanity": 0.5,
    "toxicity": 0.5,
    "hate": 0.5,
}


def _get_device() -> torch.device:
    if getattr(torch.backends, "mps", None) is not None and torch.backends.mps.is_available():
        return torch.device("mps")
    if torch.cuda.is_available():
        return torch.device("cuda")
    return torch.device("cpu")


DEVICE = _get_device()


class _MDeBertaV3Head(nn.Module):
    """Head architecture used by backend/training/train_v3.py."""

    def __init__(self, hidden_size: int, num_labels: int, dropout: float = 0.1):
        super().__init__()
        self.classifier = nn.Sequential(
            nn.Linear(hidden_size, hidden_size),
            nn.GELU(),
            nn.Dropout(dropout),
            nn.Linear(hidden_size, num_labels),
        )

    def forward(self, last_hidden_state: torch.Tensor, attention_mask: torch.Tensor) -> torch.Tensor:
        mask = attention_mask.unsqueeze(-1).float()
        pooled = (last_hidden_state * mask).sum(1) / mask.sum(1).clamp(min=1e-6)
        return self.classifier(pooled)


def _load_thresholds(model_path: str | os.PathLike) -> dict[str, float]:
    path = Path(model_path) / "thresholds.json"
    if not path.exists():
        return DEFAULT_THRESHOLDS.copy()
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return DEFAULT_THRESHOLDS.copy()
    return {
        label: max(0.0, min(1.01, float(raw.get(label, DEFAULT_THRESHOLDS[label]))))
        for label in LABEL_NAMES
    }


def _resolve_thresholds(base_thresholds: dict[str, float], threshold: float | dict | None) -> dict[str, float]:
    if isinstance(threshold, dict):
        return {
            label: max(0.0, min(1.01, float(threshold.get(label, base_thresholds[label]))))
            for label in LABEL_NAMES
        }

    try:
        request_threshold = float(threshold)
    except (TypeError, ValueError):
        request_threshold = 0.5

    # The pipeline still passes a legacy single threshold. Treat 0.5, the classifier
    # default, as the calibrated baseline and move each v3 label threshold by the
    # same offset instead of flattening the labels to one cutoff.
    offset = request_threshold - 0.5
    return {
        label: max(0.0, min(1.01, base_thresholds[label] + offset))
        for label in LABEL_NAMES
    }


class TextClassifier:
    def __init__(self, model_path: str = MODEL_PATH, device=DEVICE):
        self.device = device
        self.model_path = str(model_path)
        self.tokenizer = AutoTokenizer.from_pretrained(model_path)
        # 모델 라벨: [비속어(P), 공격성(A), 혐오(H)]
        self.label_names = LABEL_NAMES
        self.thresholds = _load_thresholds(model_path)
        self.model_kind = "sequence-classification"
        self.model = None
        self.encoder = None
        self.head = None

        head_path = Path(model_path) / "head.pt"
        if head_path.exists():
            self.model_kind = "mdeberta-v3-head"
            self.encoder = AutoModel.from_pretrained(model_path).to(device)
            self.encoder.eval()
            self.head = _MDeBertaV3Head(
                hidden_size=int(self.encoder.config.hidden_size),
                num_labels=len(self.label_names),
            ).to(device)
            head_state = torch.load(head_path, map_location="cpu")
            classifier_state = head_state.get("classifier", head_state)
            self.head.classifier.load_state_dict(classifier_state)
            self.head.eval()
        else:
            self.model = AutoModelForSequenceClassification.from_pretrained(model_path).to(device)
            self.model.eval()

    def _predict_probabilities(self, texts: list[str]) -> list[list[float]]:
        inputs = self.tokenizer(
            texts, return_tensors="pt", truncation=True,
            max_length=160 if self.model_kind == "mdeberta-v3-head" else 128,
            padding=True
        ).to(self.device)

        with torch.no_grad():
            if self.model_kind == "mdeberta-v3-head":
                outputs = self.encoder(
                    input_ids=inputs["input_ids"],
                    attention_mask=inputs["attention_mask"],
                )
                logits = self.head(outputs.last_hidden_state, inputs["attention_mask"])
            else:
                logits = self.model(**inputs).logits
            probs = torch.sigmoid(logits).detach().cpu().tolist()
        return probs

    def _build_result(self, probabilities: list[float], threshold: float | dict | None) -> dict:
        scores = {name: float(probabilities[i]) for i, name in enumerate(self.label_names)}
        thresholds = _resolve_thresholds(self.thresholds, threshold)
        return {
            "is_profane": scores["profanity"] >= thresholds["profanity"],
            "is_toxic": scores["toxicity"] >= thresholds["toxicity"],
            "is_hate": scores["hate"] >= thresholds["hate"],
            "scores": scores,
        }

    def predict(self, text: str, threshold: float = 0.5) -> dict:
        """단일 텍스트 분류.

        Returns:
            {
                "is_profane": bool,
                "is_toxic": bool,
                "is_hate": bool,
                "scores": {"profanity": float, "toxicity": float, "hate": float}
            }
        """
        probs = self._predict_probabilities([text])[0]
        return self._build_result(probs, threshold)

    def predict_batch(self, texts: list[str], threshold: float = 0.5) -> list[dict]:
        """배치 텍스트 분류."""
        return [
            self._build_result(probs, threshold)
            for probs in self._predict_probabilities(texts)
        ]
