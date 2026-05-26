"""
텍스트 정규화 파이프라인 (v3 통합).

적용 순서:
  0. invisible 유니코드 문자 제거 (ZW Space, BOM, Soft Hyphen 등 SNS 우회 삽입)
  0.5. 전각→반각 변환 (ａ-ｚ, Ａ-Ｚ, ０-９, 전각기호 → ASCII)
  1. 영타→한글 변환 (inko)
  2. 유니코드 NFC 정규화
  3. 이모지 끼워넣기 제거 (한글 사이 삽입형만, 독립 이모지는 유지)
  4. 가나 끼워넣기 제거 (한글 사이 삽입형만)
  5. 특수문자/숫자 끼워넣기 제거
  6. 반복 문자 축약
  7. 자모 조립 (ㅅㅣㅂㅏㄹ→시발, 공백 분리 자모도 처리)
  8. 초성 전사 (ㅅㅂ→씨발, ㅂㅅ→병신, ㅈㄹ→지랄 등)
  9. 공백 정리
  --- v3 추가 ---
 10. 공백 삽입 우회 병합 (새 끼→새끼, 씨 발→씨발)
 11. 복합 자모 초성 보완 (ㅄ→병신)
 12. qwerty 욕설 단어 직접 치환 (tlqkf→시발, rotorrl→개새끼 등)
 13. 변형 철자 → 표준형 (씨빨→씨발, 빙신→병신 등)

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

# SNS에서 탐지 우회에 삽입되는 invisible 문자들
# U+200B: Zero Width Space  U+200C: ZW Non-Joiner  U+2060: Word Joiner
# U+FEFF: BOM               U+00AD: Soft Hyphen     U+034F: Combining Grapheme Joiner
# U+2028: Line Separator    U+2029: Paragraph Separator
_RE_INVISIBLE = re.compile(
    r"[​‌⁠﻿­͏  ]"
)

_RE_EMOJI_INSERT    = re.compile(rf"({_HANGUL}){_EMOJI_BLOCK}({_HANGUL})")
_RE_KANA_INSERT     = re.compile(rf"({_HANGUL}){_KANA_BLOCK}({_HANGUL})")
_RE_MISC_INSERT     = re.compile(rf"({_HANGUL}){_INSERTED_MISC}+({_HANGUL})")
_RE_COLLAPSE_REPEAT = re.compile(r"(.)\1{2,}")
_RE_VOWEL_FILLER    = re.compile(r"([이으아어우오])\1+")
_RE_WHITESPACE      = re.compile(r"\s+")

# 자모 조립 상수 (호환 자모 U+3131-U+3163 → 완성형 음절)
_CHO  = ['ㄱ','ㄲ','ㄴ','ㄷ','ㄸ','ㄹ','ㅁ','ㅂ','ㅃ','ㅅ','ㅆ','ㅇ','ㅈ','ㅉ','ㅊ','ㅋ','ㅌ','ㅍ','ㅎ']
_JUNG = ['ㅏ','ㅐ','ㅑ','ㅒ','ㅓ','ㅔ','ㅕ','ㅖ','ㅗ','ㅘ','ㅙ','ㅚ','ㅛ','ㅜ','ㅝ','ㅞ','ㅟ','ㅠ','ㅡ','ㅢ','ㅣ']
_JONG = ['','ㄱ','ㄲ','ㄳ','ㄴ','ㄵ','ㄶ','ㄷ','ㄹ','ㄺ','ㄻ','ㄼ','ㄽ','ㄾ','ㄿ','ㅀ','ㅁ','ㅂ','ㅄ','ㅅ','ㅆ','ㅇ','ㅈ','ㅊ','ㅋ','ㅌ','ㅍ','ㅎ']
_CHO_IDX  = {c: i for i, c in enumerate(_CHO)}
_JUNG_IDX = {v: i for i, v in enumerate(_JUNG)}
_JONG_IDX = {c: i for i, c in enumerate(_JONG) if c}

_RE_JAMO_SEQ    = re.compile(r'[ㄱ-ㅣ]+')
_RE_JAMO_SPACED = re.compile(r'([ㄱ-ㅣ]) ([ㄱ-ㅣ])')

# 초성 전사 — 모호한 약어(ㅅㄱ=수고, ㄱㅅ=감사) 제외, 긴 패턴 우선
_RE_CHOSUNG = [
    (re.compile(r"ㄱㅅㄲ"), "개새끼"),
    (re.compile(r"ㅆㅂ"),   "씨발"),
    (re.compile(r"ㅅㅂ"),   "씨발"),
    (re.compile(r"ㅂㅅ"),   "병신"),
    (re.compile(r"ㅈㄹ"),   "지랄"),
    (re.compile(r"ㄷㅊ"),   "닥쳐"),
    (re.compile(r"ㅎㄷ"),   "혐도"),
    (re.compile(r"ㅁㅊ"),   "미쳤"),
]

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

    result = _RE_INVISIBLE.sub("", text)        # 0) invisible 제거
    result = convert_fullwidth(result)           # 0.5) 전각→반각
    result = convert_engtypo(result)             # 1) 영타→한글
    result = unicodedata.normalize("NFC", result)# 2) NFC
    result = remove_inserted_emoji(result)       # 3) 이모지 삽입 제거
    result = remove_inserted_kana(result)        # 4) 가나 삽입 제거
    result = remove_inserted_chars(result)       # 5) 특수문자 삽입 제거
    result = collapse_repeats(result)            # 6) 반복 축약
    result = compose_jamo(result)                # 7) 자모 조립
    result = expand_chosung(result)              # 8) 초성 전사
    result = _RE_WHITESPACE.sub(" ", result).strip()  # 9) 공백 정리

    # ── v3 추가 ──
    for pat, rep in _SPACE_MERGE_RE:             # 10) 공백 삽입 병합
        result = pat.sub(rep, result)
    for pat, rep in _EXTRA_CHOSUNG_RE:           # 11) 복합 초성
        result = pat.sub(rep, result)
    for pat, rep in _QWERTY_PROFANITY_RE:        # 12) qwerty 치환
        result = pat.sub(rep, result)
    for variant, canonical in _VARIANT_SORTED:  # 13) 변형 철자
        result = result.replace(variant, canonical)

    return result


# ---------------------------------------------------------------------------
# 내부 처리 함수
# ---------------------------------------------------------------------------

def convert_fullwidth(text: str) -> str:
    """전각 문자 → 반각(ASCII) 변환.

    U+FF01-FF5E (전각 기호/영숫자) → U+0021-U+007E (ASCII 대응 문자, 0xFEE0 감산)
    U+3000 (이상적 공백, 전각 스페이스) → U+0020 (공백)
    """
    result = []
    for ch in text:
        cp = ord(ch)
        if 0xFF01 <= cp <= 0xFF5E:
            result.append(chr(cp - 0xFEE0))
        elif cp == 0x3000:
            result.append(' ')
        else:
            result.append(ch)
    return ''.join(result)


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


def compose_jamo(text: str) -> str:
    """호환 자모 연속을 완성형 음절로 조립.

    ㅅㅣㅂㅏㄹ → 시발,  ㅂㅕㅇㅅㅣㄴ → 병신
    공백으로 분리된 자모(ㅅ ㅣ ㅂ ㅏ ㄹ)도 먼저 공백 제거 후 조립.
    초성만 나열된 경우(ㅋㅋ, ㄷㄷ)는 조립 불가 → 그대로 반환.
    """
    result = text
    for _ in range(10):
        new = _RE_JAMO_SPACED.sub(r'\1\2', result)
        if new == result:
            break
        result = new
    return _RE_JAMO_SEQ.sub(lambda m: _assemble_jamo(m.group()), result)


def _assemble_jamo(s: str) -> str:
    """자모 문자열 → 음절 조립. 조립 불가한 자모는 그대로 남김."""
    chars = list(s)
    out = []
    i = 0
    while i < len(chars):
        ch = chars[i]
        if ch in _CHO_IDX and i + 1 < len(chars) and chars[i + 1] in _JUNG_IDX:
            cho  = _CHO_IDX[ch]
            jung = _JUNG_IDX[chars[i + 1]]
            jong = 0
            if (i + 2 < len(chars)
                    and chars[i + 2] in _JONG_IDX
                    and (i + 3 >= len(chars) or chars[i + 3] not in _JUNG_IDX)):
                jong = _JONG_IDX[chars[i + 2]]
                i += 3
            else:
                i += 2
            out.append(chr((cho * 21 + jung) * 28 + jong + 0xAC00))
        else:
            out.append(ch)
            i += 1
    return ''.join(out)


def expand_chosung(text: str) -> str:
    """초성 전사 (collapse_repeats 이후 적용: ㅅㅅㅂ→ㅅㅂ→씨발).

    모호한 약어(ㅅㄱ=수고, ㄱㅅ=감사)는 제외, 명확한 비속어만 전사.
    """
    for pattern, replacement in _RE_CHOSUNG:
        text = pattern.sub(replacement, text)
    return text
