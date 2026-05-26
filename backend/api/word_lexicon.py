"""
[단어 lexicon] marisa-trie 기반 단어 분류 lookup

용도:
  - 단어 경로(word path)에서 분류기 대용. p99 < 1μs 수준의 lookup.
  - hit 시 즉답 → 추론 없이 결과 반환
  - miss 시 호출자가 fallback (예: 기존 v2 분류기)

라벨 비트마스크:
  bit 0: profanity
  bit 1: toxicity
  bit 2: hate
  0     : neutral (사전 hit이지만 일반 단어)
  None  : MISS (사전에 없음 → fallback)

빌드: scripts/build_lexicon.py 참조
"""
import os
import unicodedata
from typing import Optional

import marisa_trie

BIT_PROFANITY = 0b001
BIT_TOXICITY = 0b010
BIT_HATE = 0b100

# 기본 trie 경로 — 환경변수 우선
_DEFAULT_TRIE = os.path.join(
    os.path.dirname(__file__),
    "..", "..", "..", "data", "lexicon", "word_lexicon.marisa"
)
TRIE_PATH = os.environ.get("WORD_LEXICON_PATH", _DEFAULT_TRIE)

_trie: Optional[marisa_trie.BytesTrie] = None


def _get_trie() -> marisa_trie.BytesTrie:
    global _trie
    if _trie is None:
        if not os.path.exists(TRIE_PATH):
            raise FileNotFoundError(
                f"word lexicon trie를 찾을 수 없습니다: {TRIE_PATH}\n"
                f"빌드: python scripts/build_lexicon.py"
            )
        _trie = marisa_trie.BytesTrie()
        _trie.load(TRIE_PATH)
    return _trie


class WordLexicon:
    """단어 lexicon lookup wrapper."""

    def __init__(self, trie_path: Optional[str] = None):
        global _trie
        if trie_path is not None:
            _trie = marisa_trie.BytesTrie()
            _trie.load(trie_path)
        else:
            _get_trie()

    def lookup(self, text: str) -> Optional[dict]:
        """단어 분류 lookup.

        Args:
            text: 정규화된 단어 텍스트

        Returns:
            None              : MISS (사전에 없음 — 호출자가 fallback 처리)
            dict (hit 시):
                {
                  "is_profane": bool,
                  "is_toxic":   bool,
                  "is_hate":    bool,
                  "is_offensive": bool,
                  "source": "lexicon",
                }
        """
        if not text:
            return None
        text = unicodedata.normalize("NFC", text).strip()
        if not text:
            return None

        trie = _get_trie()
        hits = trie.get(text)
        if not hits:
            return None  # MISS

        bits = hits[0][0]  # uint8 라벨 비트마스크
        is_profane = bool(bits & BIT_PROFANITY)
        is_toxic = bool(bits & BIT_TOXICITY)
        is_hate = bool(bits & BIT_HATE)

        return {
            "is_profane": is_profane,
            "is_toxic": is_toxic,
            "is_hate": is_hate,
            "is_offensive": is_profane or is_toxic or is_hate,
            "source": "lexicon",
        }
