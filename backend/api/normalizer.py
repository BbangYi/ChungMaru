"""
[1단계] 텍스트 정규화
- 영타→한글 변환 (inko)
- 반복 문자 축약
- 특수문자/숫자 끼워넣기 제거
- 이모지 끼워넣기 제거 (한글 사이 삽입형만, 독립 이모지는 유지)
- 일본어(히라가나/가타카나) 끼워넣기 제거 (한글 사이 삽입형만)
- 초성 분리 처리
"""
import re
import unicodedata

try:
    from inko import Inko
    _inko = Inko()
except ImportError:
    _inko = None


# ---------------------------------------------------------------------------
# 정규식 상수 (모듈 로드 시 1회 컴파일)
# ---------------------------------------------------------------------------

# 한글 완성형 + 자모 낱자
_HANGUL = r"[가-힣ㄱ-ㅎㅏ-ㅣ]"

# 이모지/기호 유니코드 범위
# - U+1F300–U+1FAFF : SMP 이모지 (그림 기호, 감정, 교통 등)
# - U+2600–U+27BF   : BMP 기타 기호 + 딩뱃 (☀♥✂ 등)
# - U+FE0E\uFE0F    : 변형 선택자 (텍스트↔이모지 전환)
# - U+200D          : ZWJ (Zero Width Joiner, 복합 이모지 결합자)
# 수량자 +로 ZWJ 시퀀스·연속 이모지를 한 덩어리로 처리
_EMOJI_BLOCK = (
    r"(?:"
    r"[\U0001F300-\U0001FAFF]"
    r"|[\u2600-\u27BF]"
    r"|[\uFE0E\uFE0F]"
    r"|[\u200D]"
    r")+"
)

# 히라가나: U+3041–U+3096, 가타카나: U+30A0–U+30FF
# 수량자 +로 히라가나+가타카나 연속 삽입도 한 번에 처리
_KANA_BLOCK = r"[\u3041-\u3096\u30A0-\u30FF]+"

# 기존 특수문자/숫자 끼워넣기 패턴 (이모지·가나는 전용 함수에서 처리했으므로 제외)
_INSERTED_MISC = (
    r"[^가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z\s"
    r"\U0001F300-\U0001FAFF"
    r"\u2600-\u27BF"
    r"\uFE0E\uFE0F\u200D"
    r"\u3041-\u3096\u30A0-\u30FF"
    r"]"
)

_RE_EMOJI_INSERT    = re.compile(rf"({_HANGUL}){_EMOJI_BLOCK}({_HANGUL})")
_RE_KANA_INSERT     = re.compile(rf"({_HANGUL}){_KANA_BLOCK}({_HANGUL})")
_RE_MISC_INSERT     = re.compile(rf"({_HANGUL}){_INSERTED_MISC}({_HANGUL})")
_RE_COLLAPSE_REPEAT = re.compile(r"(.)\1{2,}")
_RE_VOWEL_FILLER    = re.compile(r"([이으아어우오])\1+")
_RE_WHITESPACE      = re.compile(r"\s+")


# ---------------------------------------------------------------------------
# 공개 API
# ---------------------------------------------------------------------------

def normalize(text: str) -> str:
    """텍스트 정규화 파이프라인. 원문을 정규화된 텍스트로 변환.

    실행 순서:
        1) 영타 → 한글 변환
        2) 유니코드 NFC 정규화 (ZWJ 시퀀스 정합성 보장)
        3) 이모지 끼워넣기 제거 (한글 사이 삽입형만)
        4) 가나 끼워넣기 제거 (한글 사이 삽입형만)
        5) 특수문자/숫자 끼워넣기 제거
        6) 반복 문자 축약
        7) 공백 정리
    """
    result = text

    result = convert_engtypo(result)
    result = unicodedata.normalize("NFC", result)
    result = remove_inserted_emoji(result)
    result = remove_inserted_kana(result)
    result = remove_inserted_chars(result)
    result = collapse_repeats(result)
    result = _RE_WHITESPACE.sub(" ", result).strip()

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
    """한글 글자 사이에 삽입된 이모지/기호 시퀀스를 제거.

    제거: 한글(앞) + 이모지 1개 이상 + 한글(뒤)  →  병🖕신 → 병신
    유지: 공백·구두점으로 분리된 독립 이모지 토큰  →  "좋아요 👍" 그대로

    엣지케이스: ZWJ 복합 이모지(👨‍👩‍👧), 변형 선택자(☀️), 연속 이모지 모두 처리.
    변경 없으면 조기 종료 (최대 4회).
    """
    result = text
    for _ in range(4):
        new = _RE_EMOJI_INSERT.sub(r"\1\2", result)
        if new == result:
            break
        result = new
    return result


def remove_inserted_kana(text: str) -> str:
    """한글 글자 사이에 삽입된 히라가나/가타카나를 제거.

    제거: 한글(앞) + 가나 1자 이상 + 한글(뒤)  →  병バ신 → 병신
    유지: 순수 일본어 댓글, 공백으로 분리된 가나   →  "バカ 진짜" 그대로

    엣지케이스: 히라가나+가타카나 혼용(병ひカ신 → 병신), 연속 가나.
    변경 없으면 조기 종료 (최대 4회).
    """
    result = text
    for _ in range(4):
        new = _RE_KANA_INSERT.sub(r"\1\2", result)
        if new == result:
            break
        result = new
    return result


def remove_inserted_chars(text: str) -> str:
    """한글 글자 사이에 끼워넣은 특수문자/숫자를 제거.

    이모지·가나는 전용 함수가 이미 처리했으므로 _INSERTED_MISC에서 제외.
    변경 없으면 조기 종료 (최대 4회).

    예: 병.신 → 병신, 시1발 → 시발, 개★새끼 → 개새끼, 병..신 → 병신
    """
    result = text
    for _ in range(4):
        new = _RE_MISC_INSERT.sub(r"\1\2", result)
        if new == result:
            break
        result = new
    return result


def collapse_repeats(text: str) -> str:
    """반복 문자 축약.

    - 동일 문자 3회 이상 → 1회 (ㅋㅋㅋㅋ → ㅋ)
    - 한글 모음 늘리기 → 1회 (씨이이이발 → 씨이발)
    """
    result = _RE_COLLAPSE_REPEAT.sub(r"\1", text)
    result = _RE_VOWEL_FILLER.sub(r"\1", result)
    return result
