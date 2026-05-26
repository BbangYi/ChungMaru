"""
텍스트 정규화 파이프라인 (v3 통합).

적용 순서:
  1. 영타→한글 변환 (inko)
  2. 유니코드 NFC 정규화
  3. 이모지 끼워넣기 제거 (한글 사이 삽입형만, 독립 이모지는 유지)
  4. 가나 끼워넣기 제거 (한글 사이 삽입형만)
  5. 특수문자/숫자 끼워넣기 제거
  6. 반복 문자 축약
  7. 공백 정리
  --- v3 추가 ---
  8. 공백 삽입 우회 병합 (새 끼→새끼, 씨 발→씨발)
  9. 복합 자모 초성 보완 (ㅄ→병신)
 10. qwerty 욕설 단어 직접 치환 (tlqkf→시발, rotorrl→개새끼 등)
 11. 변형 철자 → 표준형 (씨빨→씨발, 빙신→병신 등)

train / inference 양쪽에서 동일하게 호출 필수.
"""
import re
import unicodedata

try:
    from inko import Inko
    _inko = Inko()
except ImportError:
    _inko = None


# ---------------------------------------------------------------------------
# 정규식 상수
# ---------------------------------------------------------------------------

_HANGUL = r"[가-힣ㄱ-ㅎㅏ-ㅣ]"

_EMOJI_BLOCK = (
    r"(?:"
    r"[\U0001F300-\U0001FAFF]"
    r"|[☀-➿]"
    r"|[︎️]"
    r"|[‍]"
    r")+"
)

_KANA_BLOCK = r"[ぁ-ゖ゠-ヿ]+"

_INSERTED_MISC = (
    r"[^가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z\s"
    r"\U0001F300-\U0001FAFF"
    r"☀-➿"
    r"︎️‍"
    r"ぁ-ゖ゠-ヿ"
    r"]"
)

_RE_EMOJI_INSERT    = re.compile(rf"({_HANGUL}){_EMOJI_BLOCK}({_HANGUL})")
_RE_KANA_INSERT     = re.compile(rf"({_HANGUL}){_KANA_BLOCK}({_HANGUL})")
_RE_MISC_INSERT     = re.compile(rf"({_HANGUL}){_INSERTED_MISC}({_HANGUL})")
_RE_COLLAPSE_REPEAT = re.compile(r"(.)\1{2,}")
_RE_VOWEL_FILLER    = re.compile(r"([이으아어우오])\1+")
_RE_WHITESPACE      = re.compile(r"\s+")

# ── v3: 공백 삽입 우회 병합 ───────────────────────────────────────────────
_SPACE_MERGE_RE: list[tuple[re.Pattern, str]] = [
    (re.compile(r"개\s+새\s*끼"), "개새끼"),
    (re.compile(r"씨\s+발"),      "씨발"),
    (re.compile(r"새\s+끼"),      "새끼"),
    (re.compile(r"병\s+신"),      "병신"),
    (re.compile(r"미\s+친"),      "미친"),
    (re.compile(r"지\s+랄"),      "지랄"),
    (re.compile(r"존\s+나"),      "존나"),
]

# ── v3: 복합 자모 초성 보완 ───────────────────────────────────────────────
_EXTRA_CHOSUNG_RE: list[tuple[re.Pattern, str]] = [
    (re.compile(r"ㅄ"), "병신"),
]

# ── v3: qwerty 욕설 단어 직접 치환 ──────────────────────────────────────
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

# ── v3: 변형 철자 → 표준형 ───────────────────────────────────────────────
_VARIANT_MAP: dict[str, str] = {
    "씨빨": "씨발",
    "쒸발": "씨발",
    "씨밥": "씨발",
    "씨바": "씨발",
    "씨팔": "씨발",
    "씨팍": "씨발",
    "새키": "새끼",
    "새기": "새끼",
    "세끼": "새끼",
    "쎄끼": "새끼",
    "빙신": "병신",
    "뻥신": "병신",
    "좃":   "좆",
    "죳":   "좆",
    "줫":   "좆",
}
_VARIANT_SORTED = sorted(_VARIANT_MAP.items(), key=lambda x: -len(x[0]))


# ---------------------------------------------------------------------------
# 공개 API
# ---------------------------------------------------------------------------

def normalize(text: str) -> str:
    """텍스트 정규화 파이프라인 (v3). 원문을 정규화된 텍스트로 변환."""
    if not text:
        return text

    # ── v2 정규화 ──
    result = convert_engtypo(text)
    result = unicodedata.normalize("NFC", result)
    result = remove_inserted_emoji(result)
    result = remove_inserted_kana(result)
    result = remove_inserted_chars(result)
    result = collapse_repeats(result)
    result = _RE_WHITESPACE.sub(" ", result).strip()

    # ── v3 추가 정규화 ──
    for pat, rep in _SPACE_MERGE_RE:
        result = pat.sub(rep, result)
    for pat, rep in _EXTRA_CHOSUNG_RE:
        result = pat.sub(rep, result)
    for pat, rep in _QWERTY_PROFANITY_RE:
        result = pat.sub(rep, result)
    for variant, canonical in _VARIANT_SORTED:
        result = result.replace(variant, canonical)

    return result


# ---------------------------------------------------------------------------
# 내부 처리 함수
# ---------------------------------------------------------------------------

def convert_engtypo(text: str) -> str:
    """영문 키보드로 잘못 입력된 한글을 변환 (inko 미설치 시 원문 반환)."""
    if _inko is None:
        return text
    return _inko.en2ko(text)


def remove_inserted_emoji(text: str) -> str:
    """한글 글자 사이에 삽입된 이모지/기호 시퀀스를 제거."""
    result = text
    for _ in range(4):
        new = _RE_EMOJI_INSERT.sub(r"\1\2", result)
        if new == result:
            break
        result = new
    return result


def remove_inserted_kana(text: str) -> str:
    """한글 글자 사이에 삽입된 히라가나/가타카나를 제거."""
    result = text
    for _ in range(4):
        new = _RE_KANA_INSERT.sub(r"\1\2", result)
        if new == result:
            break
        result = new
    return result


def remove_inserted_chars(text: str) -> str:
    """한글 글자 사이에 끼워넣은 특수문자/숫자를 제거."""
    result = text
    for _ in range(4):
        new = _RE_MISC_INSERT.sub(r"\1\2", result)
        if new == result:
            break
        result = new
    return result


def collapse_repeats(text: str) -> str:
    """반복 문자 축약 (ㅋㅋㅋㅋ→ㅋ, 씨이이이발→씨이발)."""
    result = _RE_COLLAPSE_REPEAT.sub(r"\1", text)
    result = _RE_VOWEL_FILLER.sub(r"\1", result)
    return result


# ── 간단 테스트
if __name__ == "__main__":
    import io as _io, sys as _sys
    _sys.stdout = _io.TextIOWrapper(_sys.stdout.buffer, encoding="utf-8", errors="replace")
    cases = [
        ("ㅅㅂ 진짜",        "씨발 진짜"),
        ("씨빨 이게 뭐야",    "씨발 이게 뭐야"),
        ("새 끼들이",         "새끼들이"),
        ("병.신 같은",        "병신 같은"),
        ("ㅂㅅ이냐",         "병신이냐"),
        ("ㅄ",               "병신"),
        ("tlqkf 진짜",       "시발 진짜"),
        ("qudtls",           "병신"),
        ("rotorrl들",        "개새끼들"),
        ("병🖕신",           "병신"),
        ("시발점에서",        "시발점에서"),
        ("개인정보",          "개인정보"),
    ]
    print(f"{'입력':<20} {'출력':<20} {'기대':<20} {'OK'}")
    print("-" * 70)
    for inp, expected in cases:
        out = normalize(inp)
        ok = "✓" if out == expected else "✗"
        print(f"  {inp:<18} {out:<18} {expected:<18} {ok}")
