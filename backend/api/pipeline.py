"""
메인 파이프라인: 텍스트 → 정규화 → 분류 → Span 추출 → 결과 JSON

아키텍처:
  1) TextClassifier  — 문장 분류 (XLM-RoBERTa-base, 비속어/공격/혐오)
  2) SpanDetector    — 욕설 span 추출 (XLM-RoBERTa-large + CRF)
  3) 분류기 주도     — 분류기가 판정, span은 근거 제공 (판정을 뒤집지 않음)

플랫폼별 처리:
  Chrome Extension → analyze() / analyze_batch()       텍스트 직접 입력
  Android App      → analyze_android_batch()           JSON + boundsInScreen 입력/출력
"""
import os
import re
from difflib import SequenceMatcher

from normalizer import normalize
from classifier import TextClassifier
from span_detector import SpanDetector
from input_filter import filter_android_json
from word_router import is_word_like
from word_lexicon import WordLexicon


def _build_norm_to_orig_map(original: str, normalized: str) -> list[int]:
    """정규화 텍스트의 각 문자 인덱스 → 원문 인덱스 매핑 배열 생성.

    difflib.SequenceMatcher로 두 문자열을 정렬하여
    normalized[i]가 original의 어느 위치에서 왔는지 추적한다.
    """
    sm = SequenceMatcher(None, original, normalized, autojunk=False)
    n2o = [-1] * len(normalized)

    for tag, i1, i2, j1, j2 in sm.get_opcodes():
        if tag == "equal":
            for k in range(j2 - j1):
                n2o[j1 + k] = i1 + k
        elif tag == "replace":
            # 길이가 다를 수 있으므로 비례 배분
            orig_len = i2 - i1
            norm_len = j2 - j1
            for k in range(norm_len):
                n2o[j1 + k] = i1 + min(k * orig_len // norm_len, orig_len - 1)

    # -1 갭을 직전 유효 인덱스로 채움
    last = 0
    for i in range(len(n2o)):
        if n2o[i] >= 0:
            last = n2o[i]
        else:
            n2o[i] = last

    return n2o

# ---------------------------------------------------------------------------
# 직접 탐지용 상수 (모델 불필요)
# ---------------------------------------------------------------------------

# ASCII 욕설 패턴 집합: qwerty 오타 + 로마자 표기 (원문 직접 탐지에 사용)
_ASCII_PROFANITY_SET: frozenset = frozenset({
    "rotorrl", "tlqkfwk", "tlqkf", "qudtls", "wlfkf",
    "alcls", "rjwu", "tnwjd", "ehfkdl", "whssk",
    "ssibal", "ssibbal", "sibal", "shibal", "shiball",
    "gaesaekki", "gaesaeki", "kaesaekki",
    "byeongsin", "byungsin", "jiral", "michin",
})

# 한국어 욕설 사전 (정규화 텍스트 → 원문 위치 역매핑용), 긴 것 우선
_KO_PROFANITY_DICT: tuple = tuple(sorted([
    "개새끼", "씨발", "시발", "병신", "지랄",
    "미쳤", "미친", "꺼져", "새끼", "존나", "닥쳐", "좆",
], key=len, reverse=True))

_RE_ASCII_WORD = re.compile(r"[a-zA-Z]+")


def _edit_distance(a: str, b: str) -> int:
    """Levenshtein 편집 거리."""
    m, n = len(a), len(b)
    dp = list(range(n + 1))
    for i in range(1, m + 1):
        prev = dp[:]
        dp[0] = i
        for j in range(1, n + 1):
            dp[j] = prev[j - 1] if a[i - 1] == b[j - 1] else 1 + min(prev[j - 1], prev[j], dp[j - 1])
    return dp[n]


def _extract_original_direct_spans(text: str) -> list[dict]:
    """원문에서 ASCII 욕설 토큰(qwerty/roman)을 직접 탐지.

    1) _ASCII_PROFANITY_SET 과 정확히 일치(대소문자 무시)
    2) 4-9자 단어에 한해 알려진 패턴과 편집 거리 1 이하인 경우
    """
    spans = []
    for m in _RE_ASCII_WORD.finditer(text):
        word = m.group()
        wl = word.lower()
        if wl in _ASCII_PROFANITY_SET:
            spans.append({"text": word, "start": m.start(), "end": m.end(), "score": 1.0})
            continue
        if 4 <= len(wl) <= 9:
            for known in _ASCII_PROFANITY_SET:
                if abs(len(wl) - len(known)) <= 1 and _edit_distance(wl, known) <= 1:
                    spans.append({"text": word, "start": m.start(), "end": m.end(), "score": 0.7})
                    break
    return spans


def _extract_dictionary_spans(text: str, normalized: str, mapping: list) -> list[dict]:
    """정규화 텍스트에서 알려진 한국어 욕설을 탐지, 원문 위치로 역매핑."""
    spans = []
    for word in _KO_PROFANITY_DICT:
        pos = 0
        while True:
            idx = normalized.find(word, pos)
            if idx == -1:
                break
            end = idx + len(word)
            if mapping and idx < len(mapping) and end <= len(mapping):
                orig_start = mapping[idx]
                orig_end = mapping[end - 1] + 1
            else:
                orig_start, orig_end = idx, end
            spans.append({
                "text": text[orig_start:orig_end],
                "start": orig_start,
                "end": orig_end,
                "score": 1.0,
            })
            pos = idx + 1
    return spans


def _merge_spans(spans: list[dict], text: str) -> list[dict]:
    """겹치는 span을 병합하고 원문 순서로 반환."""
    if not spans:
        return []
    ordered = sorted(spans, key=lambda s: s["start"])
    merged = [dict(ordered[0])]
    for s in ordered[1:]:
        last = merged[-1]
        if s["start"] < last["end"]:
            if s["end"] > last["end"]:
                last["end"] = s["end"]
                last["text"] = text[last["start"]:last["end"]]
        else:
            merged.append(dict(s))
    return merged


# 모델 경로: 환경변수 > 기본값(backend/ 기준)
BASE = os.environ.get("MODEL_BASE", os.path.join(os.path.dirname(__file__), ".."))


class ProfanityPipeline:
    def __init__(self,
                 classifier_path: str = None,
                 span_model_path: str = None,
                 threshold: float = 0.5):
        """
        Args:
            classifier_path: 분류 모델 경로
            span_model_path: Span CRF 모델 경로
            threshold: 분류 임계값 (is_profane 등 판정 기준)
        """
        if classifier_path is None:
            classifier_path = os.environ.get(
                "MODEL_CLASSIFIER_PATH",
                os.path.join(BASE, "models", "v2"),
            )
        if span_model_path is None:
            span_model_path = os.environ.get(
                "MODEL_SPAN_PATH",
                os.path.join(BASE, "models", "span_large_combined_crf"),
            )

        self.classifier = TextClassifier(model_path=classifier_path)
        self.span_detector = SpanDetector(model_dir=span_model_path)
        self.threshold = threshold

        # 단어 lexicon — lazy load 가능. 파일 없으면 비활성화 (fallback 전용 동작).
        try:
            self.word_lexicon = WordLexicon()
        except FileNotFoundError as e:
            print(f"[pipeline] word_lexicon 비활성화: {e}")
            self.word_lexicon = None

    def analyze(self, text: str) -> dict:
        """단일 텍스트 분석.

        라우팅:
          1) 단어성 입력 → word_lexicon lookup (hit 시 즉답)
          2) 문장성 입력 OR lexicon miss → 기존 classifier + span_detector 경로
        분류기가 주 판정자, span은 근거 제공자.
        """
        # [1단계] 정규화
        normalized = normalize(text)

        # [1.5단계] 단어 경로 — trie 우선 lookup (1어절이면 형태소 분석 전에 사전 체크)
        _is_single = len(normalized.strip().split()) == 1
        if self.word_lexicon is not None and _is_single:
            lex = self.word_lexicon.lookup(normalized.strip())
            if lex is not None:
                # 사전 hit — 즉답 (lookup ~1μs)
                evidence_spans = []
                if lex["is_offensive"]:
                    evidence_spans = [{
                        "text": text,
                        "start": 0,
                        "end": len(text),
                        "score": 1.0,
                    }]
                return {
                    "text": text,
                    "is_offensive": lex["is_offensive"],
                    "is_profane": lex["is_profane"],
                    "is_toxic": lex["is_toxic"],
                    "is_hate": lex["is_hate"],
                    "scores": {
                        "profanity": 1.0 if lex["is_profane"] else 0.0,
                        "toxicity": 1.0 if lex["is_toxic"] else 0.0,
                        "hate": 1.0 if lex["is_hate"] else 0.0,
                    },
                    "evidence_spans": evidence_spans,
                    "source": "lexicon",
                }
            # lexicon miss — word_like이면 분류기 fallback, 아닌 경우도 fallback

        # [2단계] 문장 분류 (fallback 또는 문장 경로)
        cls_result = self.classifier.predict(normalized, threshold=self.threshold)

        is_offensive = (
            cls_result["is_profane"]
            or cls_result["is_toxic"]
            or cls_result["is_hate"]
        )

        # [3단계] Span 추출 — 분류기가 유해 판정한 경우에만 실행
        evidence_spans = []
        if is_offensive:
            raw_spans = self.span_detector.detect(normalized)

            # 정규화↔원문 인덱스 매핑 (정규화로 좌표가 틀어지는 문제 해결)
            n2o = _build_norm_to_orig_map(text, normalized)

            for s in raw_spans:
                ns, ne = s["start"], s["end"]
                if 0 <= ns < len(n2o) and 0 < ne <= len(n2o):
                    s["start"] = n2o[ns]
                    s["end"] = n2o[ne - 1] + 1
                    s["text"] = text[s["start"]:s["end"]]
                else:
                    s["start"] = -1
                    s["end"] = -1
                evidence_spans.append(s)

        return {
            "text": text,
            "is_offensive": is_offensive,
            "is_profane": cls_result["is_profane"],
            "is_toxic": cls_result["is_toxic"],
            "is_hate": cls_result["is_hate"],
            "scores": cls_result["scores"],
            "evidence_spans": evidence_spans,
            "source": "model",
        }

    def analyze_batch(self, texts: list[str]) -> list[dict]:
        """배치 텍스트 분석."""
        return [self.analyze(text) for text in texts]

    def analyze_android_batch(self, raw: dict) -> dict:
        """Android 앱 수집 JSON 전체 처리.

        0단계 필터 → 분석 → boundsInScreen 보존하여 반환.
        """
        total = len(raw.get("comments", []))
        valid_comments = filter_android_json(raw)

        results = []
        for item in valid_comments:
            text = item["commentText"]
            bounds = item["boundsInScreen"]

            analysis = self.analyze(text)

            results.append({
                "original": text,
                "boundsInScreen": bounds,
                "is_offensive": analysis["is_offensive"],
                "is_profane": analysis["is_profane"],
                "is_toxic": analysis["is_toxic"],
                "is_hate": analysis["is_hate"],
                "scores": analysis["scores"],
                "evidence_spans": analysis["evidence_spans"],
            })

        return {
            "timestamp": raw.get("timestamp"),
            "results": results,
            "filtered_count": total - len(valid_comments),
        }
