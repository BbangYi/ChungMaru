"""
한국어/영어 욕설 탐지용 텍스트 정규화.

v2 normalizer(src/normalizer.py)를 기반으로
EDA에서 발굴한 변형 철자 패턴을 추가 적용.

적용 순서:
  1. v2 normalize() — invisible 제거, qwerty→한글(inko), NFC, 이모지/가나/특수문자 삽입
                      제거, 반복 축약, 자모 조립, 초성 전사, 공백 정리
  2. 공백 삽입 우회 병합 (새 끼→새끼, 씨 발→씨발 등 — v2는 한글 사이 공백 유지)
  3. 복합 자모 초성 보완 (ㅄ→병신 — v2는 ㅂ+ㅅ 두 글자만 처리)
  4. qwerty 욕설 단어 직접 치환 (inko 미설치 시 fallback; 설치 시에도 안전망)
  5. 변형 철자 → 표준형 (씨빨→씨발, 빙신→병신 등 EDA 확인 패턴)

train / inference 양쪽에서 동일하게 호출 필수.
"""
import re
import sys
from pathlib import Path

# backend/api/normalizer.py (v2) 임포트
_API = Path(__file__).resolve().parent.parent / "api"
if str(_API) not in sys.path:
    sys.path.insert(0, str(_API))

try:
    from normalizer import normalize as _v2_normalize
    _HAS_V2 = True
except ImportError:
    _HAS_V2 = False


# ── 1. 공백 삽입 우회 병합 ─────────────────────────────────────────────
# v2의 remove_inserted_chars는 \s를 제거 대상에서 제외 → 한글 사이 공백을 유지함.
# 실제 데이터에서 "새 끼", "씨 발" 등의 패턴이 발견됨(eda_profanity.py 확인).
# 개새끼는 세 음절이므로 두 패턴이 겹칠 수 있어 먼저 처리.
_SPACE_MERGE_RE: list[tuple[re.Pattern, str]] = [
    (re.compile(r"개\s+새\s*끼"), "개새끼"),
    (re.compile(r"씨\s+발"),      "씨발"),
    (re.compile(r"새\s+끼"),      "새끼"),
    (re.compile(r"병\s+신"),      "병신"),
    (re.compile(r"미\s+친"),      "미친"),
    (re.compile(r"지\s+랄"),      "지랄"),
    (re.compile(r"존\s+나"),      "존나"),
]

# ── 2. 복합 자모 초성 보완 ────────────────────────────────────────────
# v2의 _RE_CHOSUNG는 ㅂ+ㅅ(두 글자) 형태만 처리.
# ㅄ(U+3144, 복합 자모 단일 문자)는 별도로 처리 필요.
_EXTRA_CHOSUNG_RE: list[tuple[re.Pattern, str]] = [
    (re.compile(r"ㅄ"), "병신"),
]

# ── 3. qwerty 욕설 단어 직접 치환 ────────────────────────────────────
# inko 미설치 시 fallback; 설치 시에도 안전망으로 동작.
# 긴 패턴을 먼저 배치해 부분 매칭 방지 (tlqkfwk > tlqkf).
# (?![a-zA-Z]) : 뒤에 영문자가 이어지면 무시 (한글/숫자/특수문자는 허용 — rotorrl들).
_QWERTY_PROFANITY_RE: list[tuple[re.Pattern, str]] = [
    (re.compile(r"rotorrl(?![a-zA-Z])", re.IGNORECASE), "개새끼"),
    (re.compile(r"tlqkfwk(?![a-zA-Z])", re.IGNORECASE), "시발아"),
    (re.compile(r"tlqkf(?![a-zA-Z])",   re.IGNORECASE), "시발"),
    (re.compile(r"qudtls(?![a-zA-Z])",  re.IGNORECASE), "병신"),
    (re.compile(r"wlfkf(?![a-zA-Z])",   re.IGNORECASE), "지랄"),
    (re.compile(r"alcls(?![a-zA-Z])",   re.IGNORECASE), "미친"),
    (re.compile(r"rjwu(?![a-zA-Z])",    re.IGNORECASE), "꺼져"),
    (re.compile(r"tnwjd(?![a-zA-Z])",   re.IGNORECASE), "씨발"),
    (re.compile(r"ehfkdl(?![a-zA-Z])",  re.IGNORECASE), "도라이"),
]

# ── 4. 변형 철자 → 표준형 ────────────────────────────────────────────
# EDA에서 확인된 변형 철자 (v2 normalizer가 커버 못 하는 것만).
# ratio 극히 높고 neg≈0인 것 위주, 시발은 시발점 등 정상 단어 포함으로 제외.
VARIANT_MAP: dict[str, str] = {
    "씨빨": "씨발",   # ratio=937x, neg=0
    "쒸발": "씨발",   # ratio=931x, neg=0
    "씨밥": "씨발",   # ratio=931x, neg=0
    "씨바": "씨발",   # ratio=104x
    "씨팔": "씨발",   # ratio=50x
    "씨팍": "씨발",
    "새키": "새끼",   # 147건
    "새기": "새끼",   # 27건
    "세끼": "새끼",   # 24건
    "쎄끼": "새끼",
    "빙신": "병신",   # 60건
    "뻥신": "병신",
    "좃":   "좆",     # 262건
    "죳":   "좆",     # 3건
    "줫":   "좆",     # 8건
}


def normalize_text(text: str) -> str:
    if not text:
        return text

    # 1. v2 normalize — invisible 제거, qwerty→한글(inko), NFC, 이모지/가나/특수문자
    #    삽입 제거, 반복 축약, 자모 조립, 초성 전사(ㅂㅅ/ㅅㅂ/ㅈㄹ/ㄷㅊ/ㅎㄷ/ㅁㅊ), 공백 정리
    if _HAS_V2:
        text = _v2_normalize(text)

    # 2. 공백 삽입 우회 병합 (v2는 한글 사이 공백 유지)
    for pat, rep in _SPACE_MERGE_RE:
        text = pat.sub(rep, text)

    # 3. 복합 자모 초성 보완 (ㅄ → 병신)
    for pat, rep in _EXTRA_CHOSUNG_RE:
        text = pat.sub(rep, text)

    # 4. qwerty 욕설 단어 직접 치환 (inko fallback + 안전망)
    for pat, rep in _QWERTY_PROFANITY_RE:
        text = pat.sub(rep, text)

    # 5. 변형 철자 (긴 것 먼저로 부분 매칭 방지)
    for variant, canonical in sorted(VARIANT_MAP.items(), key=lambda x: -len(x[0])):
        text = text.replace(variant, canonical)

    return text


# ── 간단 테스트
if __name__ == "__main__":
    import io as _io, sys as _sys
    _sys.stdout = _io.TextIOWrapper(_sys.stdout.buffer, encoding="utf-8", errors="replace")
    cases = [
        ("ㅅㅂ 진짜",          "씨발 진짜"),
        ("씨빨 이게 뭐야",      "씨발 이게 뭐야"),
        ("쒸발 진짜",          "씨발 진짜"),
        ("새 끼들이",           "새끼들이"),
        ("병.신 같은",          "병신 같은"),
        ("ㅂㅅ이냐",           "병신이냐"),
        ("ㅄ",                 "병신"),
        ("tlqkf 진짜",         "시발 진짜"),
        ("qudtls",             "병신"),
        ("rotorrl들",          "개새끼들"),
        ("시발점에서",          "시발점에서"),   # 정상 단어 → 변형 없어야 함
        ("개인정보",            "개인정보"),    # FP 방지
        ("병원에서",            "병원에서"),    # FP 방지
    ]
    print(f"{'입력':<20} {'출력':<20} {'기대':<20} {'OK'}")
    print("-" * 70)
    for inp, expected in cases:
        out = normalize_text(inp)
        ok = "✓" if out == expected else "✗"
        print(f"  {inp:<18} {out:<18} {expected:<18} {ok}")
