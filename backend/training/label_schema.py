"""
v3 통합 라벨 스키마 + 데이터셋별 매핑 함수.

3-label hierarchical multi-label:
  profanity (P) = 욕설/비속어 표층 (대상 없어도 성립)
  toxicity  (T) = 공격적/모욕적/위협 발화 (대상 있음 가능)
  hate      (H) = 보호집단(성별/인종/성적지향/종교/지역/장애/연령) 대상

Hierarchical constraint:
  hate=1   ⇒ toxicity=1   (혐오는 항상 공격성 동반)
  profanity=1 와 toxicity=1 는 독립 (욕설이 친밀 맥락일 수 있음)

각 매핑 함수는 raw sample dict를 받아 [P, T, H] (int 0/1) 리스트를 반환.
"""
from typing import List

LABEL_NAMES = ["profanity", "toxicity", "hate"]
NUM_LABELS = 3

# bit 인덱스 (lexicon과 호환)
P_IDX = 0
T_IDX = 1
H_IDX = 2


def enforce_hierarchy(labels: List[int]) -> List[int]:
    """hate=1 ⇒ toxicity=1 강제.

    Args:
        labels: [profanity, toxicity, hate]
    Returns:
        hierarchical constraint 적용된 [P, T, H]
    """
    p, t, h = int(labels[0]), int(labels[1]), int(labels[2])
    if h == 1:
        t = 1
    return [p, t, h]


# ═══════════════════════════════════════════════════════════════
# KOLD (한국어, 뉴스+유튜브 댓글)
# ═══════════════════════════════════════════════════════════════
# 원본 필드 (preprocess_kold.py 형식):
#   OFF: "OFF" / "NOT"
#   TGT: target type (group / individual / other / None)
#   GRP: group name (gender / race / ... when TGT=group)
#   OFF_spans: [{"start", "end", "text"}]
#
# 매핑:
#   OFF=NOT             → [0, 0, 0]
#   OFF=OFF, TGT=group  → [0, 1, 1]   (집단 대상)
#   OFF=OFF, TGT=other  → [0, 1, 0]   (개인/기타 대상)
#   profanity는 KOLD가 별도로 표시하지 않음 → 0 (span은 span 모델용)
def map_kold(item: dict) -> List[int]:
    if item.get("OFF") != "OFF":
        return [0, 0, 0]
    tgt = (item.get("TGT") or "").lower()
    is_group = tgt == "group"
    return enforce_hierarchy([0, 1, 1 if is_group else 0])


# ═══════════════════════════════════════════════════════════════
# K-HATERS (한국어, 뉴스 댓글, 192K)
# ═══════════════════════════════════════════════════════════════
# 원본 필드 (preprocess_khaters.py 형식):
#   OFF: "OFF" / "NOT"  (raw label은 normal/offensive/L1_hate/L2_hate)
#   TGT: list of target labels (political/individual/gender/job/region/age/others)
#
# 매핑:
#   normal     → [0, 0, 0]
#   offensive  → [0, 1, 0]
#   L1/L2_hate → [0, 1, 1]   (보호집단 대상이면)
#
# 단, K-HATERS는 우리 전처리에서 raw label이 "OFF/NOT" binary로 평탄화되어
# normal vs offensive vs hate 구분이 손실. 따라서 TGT의 group 카테고리 유무로 hate 추정.
_KHATERS_HATE_GROUPS = {
    "political", "gender", "region", "age", "race", "religion",
    "sexual_orientation", "disability", "nationality", "origin",
}


def map_khaters(item: dict) -> List[int]:
    if item.get("OFF") != "OFF":
        return [0, 0, 0]
    tgt = item.get("TGT") or []
    if isinstance(tgt, str):
        tgt = [tgt]
    is_hate = any(str(t).lower() in _KHATERS_HATE_GROUPS for t in tgt)
    return enforce_hierarchy([0, 1, 1 if is_hate else 0])


# ═══════════════════════════════════════════════════════════════
# K-MHaS (한국어, 뉴스 댓글, 109K) — Profanity 라벨 공식 존재
# ═══════════════════════════════════════════════════════════════
# 원본: comma-separated label IDs
#   0: Politics, 1: Origin, 2: Physical, 3: Age, 4: Gender,
#   5: Religion, 6: Race, 7: Profanity, 8: Not Hate
#   (실제 K-MHaS 9-class taxonomy)
#
# 매핑:
#   Profanity(7)             → P=1
#   Not Hate(8)만            → 모두 0
#   나머지 (0~6)             → T=1, H=1
_KMHAS_HATE_IDS = {0, 1, 2, 3, 4, 5, 6}
_KMHAS_PROFANITY_ID = 7
_KMHAS_NOT_HATE_ID = 8


def map_kmhas(raw_label) -> List[int]:
    """K-MHaS raw label string → [P, T, H]."""
    if raw_label is None:
        return [0, 0, 0]
    if isinstance(raw_label, (list, tuple)):
        labels = [int(x) for x in raw_label]
    else:
        labels = [int(x.strip()) for x in str(raw_label).split(",") if x.strip()]
    if not labels or labels == [_KMHAS_NOT_HATE_ID]:
        return [0, 0, 0]
    p = int(_KMHAS_PROFANITY_ID in labels)
    h = int(any(lb in _KMHAS_HATE_IDS for lb in labels))
    t = int(h or p or any(lb != _KMHAS_NOT_HATE_ID for lb in labels))
    return enforce_hierarchy([p, t, h])


# ═══════════════════════════════════════════════════════════════
# Smilegate UnSmile (한국어, 18K)
# ═══════════════════════════════════════════════════════════════
# 컬럼: 여성/가족, 남성, 성소수자, 인종/국적, 연령, 지역, 종교, 기타 혐오, 악플/욕설, clean
_UNSMILE_HATE_COLS = [
    "여성/가족", "남성", "성소수자", "인종/국적", "연령", "지역", "종교", "기타 혐오"
]


def map_unsmile(row) -> List[int]:
    """UnSmile row(dict) → [P, T, H]."""
    cols = list(row.keys()) if isinstance(row, dict) else row.index.tolist()

    # 컬럼명 자동 탐지
    profanity_col = None
    clean_col = None
    for c in cols:
        s = str(c)
        if "악플" in s or "욕설" in s:
            profanity_col = c
        if "clean" in s.lower():
            clean_col = c

    p = int(row.get(profanity_col, 0)) if profanity_col else 0
    is_clean = int(row.get(clean_col, 0)) == 1 if clean_col else False

    h = 0
    for hc in _UNSMILE_HATE_COLS:
        for c in cols:
            if hc in str(c):
                if int(row.get(c, 0)) == 1:
                    h = 1
                break

    if is_clean and not p and not h:
        return [0, 0, 0]

    t = int(p or h or (not is_clean))
    return enforce_hierarchy([p, t, h])


# ═══════════════════════════════════════════════════════════════
# Civil Comments (영어, 1.8M) — fractional scores
# ═══════════════════════════════════════════════════════════════
# 컬럼: toxicity, severe_toxicity, obscene, threat, insult, identity_attack
# (각 0.0~1.0 — annotator 합의 비율)
_CIVIL_THRESHOLD = 0.5


def map_civil_comments(row: dict) -> List[int]:
    obscene = float(row.get("obscene", 0.0) or 0.0)
    toxicity = float(row.get("toxicity", 0.0) or 0.0)
    severe = float(row.get("severe_toxicity", 0.0) or 0.0)
    threat = float(row.get("threat", 0.0) or 0.0)
    insult = float(row.get("insult", 0.0) or 0.0)
    identity = float(row.get("identity_attack", 0.0) or 0.0)

    p = int(obscene >= _CIVIL_THRESHOLD)
    t = int(max(toxicity, severe, threat, insult) >= _CIVIL_THRESHOLD)
    h = int(identity >= _CIVIL_THRESHOLD)
    return enforce_hierarchy([p, t, h])


# ═══════════════════════════════════════════════════════════════
# Jigsaw Toxic Comment Classification (영어, 159K)
# ═══════════════════════════════════════════════════════════════
# 컬럼: toxic, severe_toxic, obscene, threat, insult, identity_hate (모두 binary 0/1)
def map_jigsaw_toxic(row: dict) -> List[int]:
    obscene = int(row.get("obscene", 0) or 0)
    toxic = int(row.get("toxic", 0) or 0)
    severe = int(row.get("severe_toxic", 0) or 0)
    threat = int(row.get("threat", 0) or 0)
    insult = int(row.get("insult", 0) or 0)
    identity = int(row.get("identity_hate", 0) or 0)

    p = int(obscene)
    t = int(toxic or severe or threat or insult)
    h = int(identity)
    return enforce_hierarchy([p, t, h])


# ═══════════════════════════════════════════════════════════════
# HateXplain (영어, 20K) — 평가 전용
# ═══════════════════════════════════════════════════════════════
# 라벨: hate(0) / offensive(1) / normal(2) (annotator majority)
_HATEXPLAIN_STR2INT = {"hatespeech": 0, "offensive": 1, "normal": 2}


def _normalize_hatexplain_label(lb) -> int:
    """GitHub 원본(string 'hatespeech'/'offensive'/'normal') 과 HF(int 0/1/2) 둘 다 지원."""
    if isinstance(lb, str):
        return _HATEXPLAIN_STR2INT.get(lb.lower(), 2)
    try:
        return int(lb)
    except (TypeError, ValueError):
        return 2


def map_hatexplain(item: dict) -> List[int]:
    """
    HateXplain -> [P, T, H]. 두 annotators 포맷 모두 지원:
      1. HF:     {"label": [0,1,2,...], "annotator_id": [...], ...}
      2. GitHub: [{"label": "hatespeech", ...}, {"label": "normal", ...}, ...]
    매핑: 0=hate→[0,1,1], 1=offensive→[0,1,0], 2=normal→[0,0,0] (majority vote)
    """
    annotators = item.get("annotators")
    raw_labels = []
    if isinstance(annotators, dict):
        raw_labels = annotators.get("label", []) or []
    elif isinstance(annotators, list):
        raw_labels = [a.get("label") for a in annotators if isinstance(a, dict)]

    labels = [_normalize_hatexplain_label(lb) for lb in raw_labels if lb is not None]
    if not labels:
        return [0, 0, 0]

    counts = {0: 0, 1: 0, 2: 0}
    for lb in labels:
        counts[lb] = counts.get(lb, 0) + 1
    majority = max(counts, key=counts.get)
    if majority == 2:  # normal
        return [0, 0, 0]
    if majority == 1:  # offensive → toxicity만 (욕설/비속어 아님, 일반 공격성)
        return enforce_hierarchy([0, 1, 0])
    if majority == 0:  # hate → toxicity + hate
        return enforce_hierarchy([0, 1, 1])
    return [0, 0, 0]


# ═══════════════════════════════════════════════════════════════
# APEACH (한국어 평가 전용, binary hate)
# ═══════════════════════════════════════════════════════════════
def map_apeach(item: dict) -> List[int]:
    label = int(item.get("class", 0) or item.get("label", 0))
    if label == 1:
        return enforce_hierarchy([0, 1, 1])
    return [0, 0, 0]


# ═══════════════════════════════════════════════════════════════
# Dataset registry — 데이터셋 ID (conditioning embedding용)
# ═══════════════════════════════════════════════════════════════
DATASET_IDS = {
    "kold": 0,
    "khaters": 1,
    "kmhas": 2,
    "unsmile": 3,
    "civil_comments": 4,
    "jigsaw_toxic": 5,
    "hatexplain": 6,
    "apeach": 7,
    "scraped": 8,              # production scraped (unlabeled, eval만)
    "synthetic_cs": 9,         # 합성 code-switching
    "kor_hate_relabeled": 10,  # kor_hate Claude 재라벨 (v3 Option A)
    "nsmc_relabeled": 11,      # NSMC Claude 재라벨 (v3 Option A)
    "hard_negatives": 12,      # 하드 네거티브 (v3 Option C)
    "synthetic_profanity": 13, # 합성 욕설 (v3 Sonnet 4.6 생성)
    "gold_test": 14,           # gold test set (v3 Option D, 수동 검증)
}
NUM_DATASETS = len(DATASET_IDS)

# ═══════════════════════════════════════════════════════════════
# 라벨 감독 마스크 — 소스별 어느 라벨을 학습에 사용할지
# ═══════════════════════════════════════════════════════════════
# profanity를 명시적으로 annotate하는 소스 (한국어만):
#   K-MHaS  : label 7 = Profanity (욕설/비속어)
#   UnSmile : 악플/욕설 컬럼
#
# 제외 소스:
#   KOLD, K-HATERS  : profanity 미주석 → "라벨 없음"이므로 제외
#   Civil Comments  : 영어 'obscene' ≠ 한국어 '욕설' → 이질적 신호로 FP 유발
#   Jigsaw Toxic    : 동일 이유로 제외
PROFANITY_SUPERVISED: frozenset = frozenset({
    "kmhas",                # K-MHaS label 7 = 욕설/비속어 (한국어 명시적 정의)
    "unsmile",              # UnSmile 악플/욕설 컬럼 (한국어)
    "kor_hate_relabeled",   # Claude Sonnet 4.6 P 라벨 (한국어 명시적)
    "nsmc_relabeled",       # Claude Sonnet 4.6 P 라벨 (한국어 영화리뷰)
    "hard_negatives",       # 설계 의도된 P 라벨 (표면 욕설 vs 맥락 구분용)
    "synthetic_profanity",  # 100% P=1 합성 샘플 (학습 신호 강화)
    "gold_test",                  # 수동 검증 P 라벨 (한국어 gold standard)
    "jigsaw_toxic_relabeled",     # Claude 재라벨 (영어, P/T/H 우리 기준으로 통일)
    # civil_comments / jigsaw_toxic(원본) 제외:
    # 영어 'obscene'과 한국어 '욕설'은 다른 언어 현상이므로
    # 이 소스의 profanity 신호를 학습에 포함하면 한국어 toxic 댓글에 FP 폭발
})


def get_label_mask(source: str) -> List[int]:
    """소스별 학습 마스크 [P_mask, T_mask, H_mask].

    0 = 해당 (샘플, 라벨) 조합을 loss에서 제외.
    1 = 정상 학습.
    """
    p = 1 if source in PROFANITY_SUPERVISED else 0
    return [p, 1, 1]
