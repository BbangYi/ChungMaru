"""
[라우터] 한국어 형태소 분석 기반 단어/문장 판별 (kiwipiepy)

판별 규칙 (모두 충족 시 단어로 간주):
  1) 어절 수 == 1 (공백 split 결과)
  2) 글자 수 ≤ MAX_WORD_CHARS
  3) 문장부호 (?, !, .) 가 끝에 없음
  4) 형태소 분석 결과에 다음 POS 태그가 하나도 없음:
     - EF  : 종결 어미 (예: 다, 어, 요)
     - EC  : 연결 어미
     - EP  : 선어말 어미 (시제/높임)
     - VV  : 동사
     - VA  : 형용사
     - VX  : 보조 용언
     - VCP : 긍정 지정사 (이다)
     - VCN : 부정 지정사 (아니다)

이 모두를 통과하면 단어 경로(word_classifier)로 보낸다.

예시:
  "병신"   → [(병신, NNG)]                → word
  "안녕"   → [(안녕, IC)]                 → word
  "노래"   → [(노래, NNG)]                → word
  "꺼져"   → [(끄, VV), (어, EF)]         → sentence (VV+EF)
  "고마워" → [(고맙, VA), (어, EF)]       → sentence (VA+EF)
  "사랑해" → [(사랑, NNG),(하, XSV),(어, EF)] → sentence (EF)
  "병신아 꺼져" → 어절 2개                 → sentence
  "몽클레어아니노?" → 문장부호 ?           → sentence
"""
import re

MAX_WORD_CHARS = 10

# 문장 시그널이 되는 POS 태그 (kiwipiepy / 세종 기준)
_SENTENCE_POS_TAGS = frozenset({
    "EF",   # 종결 어미
    "EC",   # 연결 어미
    "EP",   # 선어말 어미 (시제/높임)
    "VV",   # 동사
    "VA",   # 형용사
    "VX",   # 보조 용언
    "VCP",  # 긍정 지정사 (이다)
    "VCN",  # 부정 지정사 (아니다)
})

_PUNCT_REGEX = re.compile(r"[?!.]\s*$")

# Kiwi 인스턴스는 lazy load (모듈 import 시 ~1초 소요 방지)
_kiwi = None


def _get_kiwi():
    """Kiwi 인스턴스를 lazy하게 생성하여 반환."""
    global _kiwi
    if _kiwi is None:
        try:
            from kiwipiepy import Kiwi
        except ImportError as e:
            raise ImportError(
                "kiwipiepy가 설치되어 있지 않습니다. "
                "`pip install kiwipiepy`로 설치해 주세요."
            ) from e
        _kiwi = Kiwi()
    return _kiwi


def is_word_like(text: str) -> bool:
    """입력 텍스트가 단어성(원자성)인지 한국어 형태소 분석으로 판별한다.

    Args:
        text: normalize() 이후의 텍스트

    Returns:
        True  → 단어 경로(word_classifier)로 보낼 것
        False → 문장 경로(기존 classifier + span_detector)로 보낼 것
    """
    if not text or not text.strip():
        return False

    stripped = text.strip()

    # 규칙 1: 어절 수 == 1
    if len(stripped.split()) != 1:
        return False

    # 규칙 2: 글자 수 상한
    if len(stripped) > MAX_WORD_CHARS:
        return False

    # 규칙 3: 문장부호 부재
    if _PUNCT_REGEX.search(stripped):
        return False

    # 규칙 4: 형태소 분석 — 문장 시그널 태그 검사
    kiwi = _get_kiwi()
    tokens = kiwi.tokenize(stripped)
    for tok in tokens:
        if tok.tag in _SENTENCE_POS_TAGS:
            return False

    return True
