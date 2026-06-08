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
import time
from difflib import SequenceMatcher
from threading import RLock

from normalizer import LATIN_LEFT_BOUNDARY, LATIN_RIGHT_BOUNDARY, QWERTY_SIBAL_PATTERN, normalize
from classifier import TextClassifier
from span_detector import SpanDetector
from input_filter import filter_android_json
from profanity_dict import COMPILED_PATTERNS, WHITELIST

try:
    from word_router import is_word_like
    from word_lexicon import WordLexicon
except (ImportError, OSError):
    WordLexicon = None

    def is_word_like(_text: str) -> bool:
        return False


ZERO_SCORES = {"profanity": 0.0, "toxicity": 0.0, "hate": 0.0}

LATIN_PROFANITY_PATTERN = re.compile(
    LATIN_LEFT_BOUNDARY +
    r"(?:"
    r"s{1,2}[\W_]*(?:h[\W_]*)?i[\W_]*b[\W_]*a[\W_]*l|"
    + QWERTY_SIBAL_PATTERN +
    r"|"
    r"q[\W_]*u[\W_]*d[\W_]*t[\W_]*l[\W_]*s|qudtkf|"
    r"by[eou]+ng[\W_]*s?in|gae[\W_]*s(?:ae|e|a)[\W_]*k{1,2}i|rotori|"
    r"jiral|wlfkf|jonna|whssk|michin|alcls|k{1,2}eoj(?:ye)?o|rjwu|"
    r"f[\W_]*u[\W_]*c[\W_]*k(?:[\W_]*i[\W_]*n[\W_]*g)?|"
    r"s[\W_]*h[\W_]*i[\W_]*t|b[\W_]*i[\W_]*t[\W_]*c[\W_]*h|"
    r"asshole|bastard|cunt|dick|pussy|slut|whore|prick|twat|wanker|"
    r"mother[\W_]*fucker|douchebag|"
    r"puta|puto|mierda|joder|cabron|cabr[oó]n|pendejo|gilipollas|co[nñ]o|chingad[ao]|maric[oó]n|"
    r"putain|merde|connard|salope|encul[eé]|ta[\W_]+gueule|nique[\W_]+ta[\W_]+m[eè]re|"
    r"schei(?:ss|ß)e|arschloch|wichser|fotze|"
    r"porra|caralho|viado|"
    r"orospu|siktir|"
    r"nigg(?:er|a)|faggot|retard"
    r")" +
    LATIN_RIGHT_BOUNDARY,
    re.IGNORECASE,
)

LATIN_SAFE_EXACT = {
    "abstract factory",
    "abstract factory pattern",
    "android",
    "api",
    "backend",
    "branch",
    "chungmaru",
    "code",
    "commit",
    "docs",
    "github",
    "insights",
    "issues",
    "linear",
    "main",
    "pull requests",
    "readme",
    "repository",
    "scripts",
    "security",
    "settings",
    "shared",
    "warp",
    "warp theme",
    "warp themes",
}

SAFE_CONTEXT_PATTERNS = [
    re.compile(r"카필\s+시발|\bkapil\s+sibal\b", re.IGNORECASE),
    re.compile(r"국제차량제작\s+시발|시발자동차|시발\s*자동차"),
    re.compile(r"시발점|시발역|시발택시"),
]

DICTIONARY_SAFE_PATTERN = re.compile(
    r"(시발\s*[-–]\s*(?:위키낱말사전|나무위키|위키백과)|"
    r"위키(?:낱말사전|백과).*?\b시발\b|"
    r"나무위키.*?\b시발\b.*?(?:kapil|카필|자동차|국제차량제작|r\d+\s*판))",
    re.IGNORECASE,
)

EXPLICIT_DEFINITION_PATTERN = re.compile(
    r"(성기|생식기|성교|섹스|sex|sexual|욕설|비속어|비하|저속한|공격적인|모욕)",
    re.IGNORECASE,
)

WHITELIST_SAFE_SUFFIX_PATTERN = re.compile(
    r"^(?:(?:이|가|은|는|을|를|도|만|에|에서|으로|로|와|과|랑|하고|의|야|아|께서|한테|에게|처럼|부터|까지|"
    r"이다|입니다|였다|였습니다|인|이라|라고|이라도|이면|이라면)(?:도|만|은|는|요)?)?$"
)

EXTRA_SPAN_PATTERNS = [
    re.compile(
        LATIN_LEFT_BOUNDARY +
        r"(?:"
        r"s{1,2}[\W_]*(?:h[\W_]*)?i[\W_]*b[\W_]*a[\W_]*l|"
        + QWERTY_SIBAL_PATTERN +
        r"|"
        r"q[\W_]*u[\W_]*d[\W_]*t[\W_]*l[\W_]*s|qudtkf|"
        r"by[eou]+ng[\W_]*s?in|gae[\W_]*s(?:ae|e|a)[\W_]*k{1,2}i|rotori|"
        r"jiral|wlfkf|jonna|whssk|michin|alcls|k{1,2}eoj(?:ye)?o|rjwu"
        r")" +
        LATIN_RIGHT_BOUNDARY,
        re.IGNORECASE,
    ),
    re.compile(
        LATIN_LEFT_BOUNDARY +
        r"(?:"
        r"f[\W_]*u[\W_]*c[\W_]*k(?:[\W_]*i[\W_]*n[\W_]*g)?|"
        r"s[\W_]*h[\W_]*i[\W_]*t|b[\W_]*i[\W_]*t[\W_]*c[\W_]*h|"
        r"asshole|bastard|cunt|dick|pussy|slut|whore|prick|twat|wanker|"
        r"mother[\W_]*fucker|douchebag|"
        r"puta|puto|mierda|joder|cabron|cabr[oó]n|pendejo|gilipollas|co[nñ]o|chingad[ao]|maric[oó]n|"
        r"putain|merde|connard|salope|encul[eé]|ta[\W_]+gueule|nique[\W_]+ta[\W_]+m[eè]re|"
        r"schei(?:ss|ß)e|arschloch|wichser|fotze|"
        r"porra|caralho|viado|"
        r"orospu|siktir|"
        r"nigg(?:er|a)|faggot|retard"
        r")" +
        LATIN_RIGHT_BOUNDARY,
        re.IGNORECASE,
    ),
    re.compile(
        r"くそ|クソ|馬鹿|バカ|死ね|"
        r"操你妈|草你妈|傻逼|他妈的|去死|"
        r"бля(?:дь|ть)?|сука|хуй|пизд[аеуы]?|еба(?:ть|н[а-я]*)|мудак|долбо[её]б|"
        r"كسمك|كس امك|ابن الكلب",
        re.IGNORECASE,
    ),
]

ORIGINAL_DIRECT_SPAN_PATTERNS = [
    re.compile(
        r"ㄱ[\s\W_]*ㅅ[\s\W_]*ㄲ|"
        r"ㅆ[\s\W_]*ㅂ|"
        r"ㅅ[\s\W_]*ㅂ|"
        r"ㅂ[\s\W_]*ㅅ|"
        r"ㅈ[\s\W_]*ㄹ|"
        r"ㅈ[\s\W_]*ㄴ|"
        r"ㅅ[\s\W_]*ㄲ|"
        r"ㅁ[\s\W_]*ㅊ|"
        r"ㄲ[\s\W_]*ㅈ|"
        r"ㄷ[\s\W_]*ㅊ|"
        r"ㄴ[\s\W_]*ㄱ[\s\W_]*ㅁ|"
        r"ㅄ|ㄷㅊ|ㅂㄹ"
    ),
]


def _threshold_from_sensitivity(sensitivity: int | None, default: float) -> float:
    """UI 민감도(높을수록 더 많이 차단)를 모델 threshold로 변환."""
    if sensitivity is None:
        return default
    try:
        value = max(0, min(100, int(sensitivity)))
    except (TypeError, ValueError):
        return default
    if value <= 0:
        return 1.01
    # 0은 차단을 사실상 비활성화하고, 100은 더 공격적으로 판정한다.
    return max(0.35, min(0.99, 0.95 - (value / 100) * 0.55))


def _empty_result(text: str, scores: dict[str, float] | None = None) -> dict:
    return {
        "text": text,
        "is_offensive": False,
        "is_profane": False,
        "is_toxic": False,
        "is_hate": False,
        "scores": scores or ZERO_SCORES.copy(),
        "evidence_spans": [],
        "_timing": {
            "normalize_ms": 0.0,
            "classifier_ms": 0.0,
            "span_ms": 0.0,
            "model_ms": 0.0,
            "pipeline_ms": 0.0,
        },
    }


def _is_latin_safe_text(text: str) -> bool:
    value = re.sub(r"\s+", " ", text or "").strip().lower()
    if not value or LATIN_PROFANITY_PATTERN.search(value):
        return False
    if re.search(r"[가-힣ㄱ-ㅎㅏ-ㅣ]", value):
        return False
    if value in LATIN_SAFE_EXACT:
        return True
    # 브라우저 UI, GitHub 네비게이션, 개발 용어는 모델이 자주 오탐한다.
    return len(value) <= 80 and bool(re.fullmatch(r"[a-z0-9_./:#()&+\-\s]+", value))


def _is_safe_context(text: str, normalized: str) -> bool:
    combined = f"{text}\n{normalized}"
    if any(pattern.search(combined) for pattern in SAFE_CONTEXT_PATTERNS):
        return True
    if not DICTIONARY_SAFE_PATTERN.search(combined):
        return False
    # Dictionary/title-only citations are safe, but explanatory result cards
    # that expose offensive meaning should still be filtered for the extension.
    return not EXPLICIT_DEFINITION_PATTERN.search(combined)


def _is_whitelisted_span_text(value: str) -> bool:
    token = re.sub(r"\s+", "", value or "").strip()
    return bool(token) and token in WHITELIST


def _is_whitelisted_safe_text(text: str, normalized: str) -> bool:
    candidates = {
        re.sub(r"\s+", "", text or "").strip(),
        re.sub(r"\s+", "", normalized or "").strip(),
    }
    for candidate in candidates:
        if not candidate:
            continue
        if candidate in WHITELIST:
            return True
        for safe_word in WHITELIST:
            if (
                safe_word
                and candidate.startswith(safe_word)
                and WHITELIST_SAFE_SUFFIX_PATTERN.fullmatch(candidate[len(safe_word):])
            ):
                return True
    return False


def _is_whitelisted_span_context(span: dict, text: str) -> bool:
    source = str(text or "")
    if not source:
        return False

    start = max(0, int(span.get("start", 0)))
    end = min(len(source), int(span.get("end", 0)))
    if end <= start:
        return False

    for safe_word in WHITELIST:
        if not safe_word:
            continue
        search_start = max(0, start - len(safe_word))
        search_end = min(len(source), end + len(safe_word))
        context = source[search_start:search_end]
        context_offset = search_start
        for match in re.finditer(re.escape(safe_word), context):
            absolute_start = context_offset + match.start()
            absolute_end = context_offset + match.end()
            if absolute_start <= start and end <= absolute_end:
                return True
    return False


def _is_valid_span_text(value: str) -> bool:
    span_text = (value or "").strip()
    if not span_text or _is_whitelisted_span_text(span_text):
        return False
    if LATIN_PROFANITY_PATTERN.search(span_text):
        return True
    return any(pattern.search(span_text) for pattern, _canonical, _category in COMPILED_PATTERNS)


def _merge_spans(spans: list[dict], text: str) -> list[dict]:
    valid = [
        {
            "text": text[max(0, int(s["start"])):min(len(text), int(s["end"]))],
            "start": max(0, int(s["start"])),
            "end": min(len(text), int(s["end"])),
            "score": float(s.get("score", 0.0)),
        }
        for s in spans
        if int(s.get("end", 0)) > int(s.get("start", 0))
    ]
    valid = [
        s for s in valid
        if _is_valid_span_text(s["text"]) and not _is_whitelisted_span_context(s, text)
    ]
    valid.sort(key=lambda item: (item["start"], item["end"]))

    merged: list[dict] = []
    for span in valid:
        previous = merged[-1] if merged else None
        if previous and span["start"] <= previous["end"]:
            previous["end"] = max(previous["end"], span["end"])
            previous["text"] = text[previous["start"]:previous["end"]]
            previous["score"] = max(previous["score"], span["score"])
        else:
            merged.append(span)
    return merged


def _extract_dictionary_spans(original: str, normalized: str, n2o: list[int]) -> list[dict]:
    spans: list[dict] = []
    patterns = [pattern for pattern, _canonical, _category in COMPILED_PATTERNS] + EXTRA_SPAN_PATTERNS
    for pattern in patterns:
        for match in pattern.finditer(normalized):
            if not match.group(0).strip():
                continue
            start, end = match.span()
            if 0 <= start < len(n2o) and 0 < end <= len(n2o):
                orig_start = n2o[start]
                orig_end = n2o[end - 1] + 1
                spans.append({
                    "text": original[orig_start:orig_end],
                    "start": orig_start,
                    "end": orig_end,
                    "score": 0.99,
                })
    return _merge_spans(spans, original)


def _extract_original_direct_spans(original: str) -> list[dict]:
    spans: list[dict] = []
    for pattern in ORIGINAL_DIRECT_SPAN_PATTERNS + EXTRA_SPAN_PATTERNS:
        for match in pattern.finditer(original):
            start, end = match.span()
            spans.append({
                "text": original[start:end],
                "start": start,
                "end": end,
                "score": 0.99,
            })
    return _merge_spans(spans, original)


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
                os.path.join(BASE, "v3"),
            )
        if span_model_path is None:
            span_model_path = os.environ.get(
                "MODEL_SPAN_PATH",
                os.path.join(BASE, "models", "span_large_combined_crf"),
            )

        self.classifier_path = classifier_path
        self.span_model_path = span_model_path
        self.threshold = threshold
        self._classifier: TextClassifier | None = None
        self._span_detector: SpanDetector | None = None
        self._model_lock = RLock()
        self.word_lexicon = None
        if WordLexicon is not None:
            try:
                self.word_lexicon = WordLexicon()
            except (FileNotFoundError, ImportError, OSError) as error:
                print(f"[pipeline] word_lexicon disabled: {error}")

    def _get_classifier(self) -> TextClassifier:
        if self._classifier is None:
            with self._model_lock:
                if self._classifier is None:
                    self._classifier = TextClassifier(model_path=self.classifier_path)
        return self._classifier

    def _get_span_detector(self) -> SpanDetector:
        if self._span_detector is None:
            with self._model_lock:
                if self._span_detector is None:
                    self._span_detector = SpanDetector(model_dir=self.span_model_path)
        return self._span_detector

    def runtime_status(self) -> dict:
        classifier_model_file = os.path.join(self.classifier_path, "model.safetensors")
        classifier_head_file = os.path.join(self.classifier_path, "head.pt")
        span_extra_weights_file = os.path.join(self.span_model_path, "extra_weights.pt")
        span_encoder_model_file = os.path.join(self.span_model_path, "encoder", "model.safetensors")
        classifier_required_files = [classifier_model_file]
        if (
            os.path.exists(classifier_head_file)
            or os.path.basename(os.path.normpath(self.classifier_path)) == "v3"
        ):
            classifier_required_files.append(classifier_head_file)
        missing_model_files = [
            path for path in [
                *classifier_required_files,
                span_extra_weights_file,
                span_encoder_model_file,
            ]
            if not os.path.exists(path)
        ]
        return {
            "classifier_loaded": self._classifier is not None,
            "span_detector_loaded": self._span_detector is not None,
            "classifier_path": self.classifier_path,
            "span_model_path": self.span_model_path,
            "model_files_ready": len(missing_model_files) == 0,
            "missing_model_files": missing_model_files,
        }

    def warmup(
        self,
        *,
        load_classifier: bool = True,
        load_span_detector: bool = True,
        run_span_probe: bool = True,
        sensitivity: int | None = None,
    ) -> dict:
        """Eagerly load model components that otherwise spike on first live use."""
        started = time.perf_counter()
        threshold = _threshold_from_sensitivity(sensitivity, self.threshold)
        before = self.runtime_status()
        timings: dict[str, float] = {}

        if load_classifier:
            classifier_load_started = time.perf_counter()
            classifier = self._get_classifier()
            timings["classifier_load_ms"] = round((time.perf_counter() - classifier_load_started) * 1000, 3)

            classifier_probe_started = time.perf_counter()
            classifier.predict_batch(["안녕하세요", "씨발 테스트"], threshold=threshold)
            timings["classifier_probe_ms"] = round((time.perf_counter() - classifier_probe_started) * 1000, 3)

        if load_span_detector:
            span_load_started = time.perf_counter()
            span_detector = self._get_span_detector()
            timings["span_detector_load_ms"] = round((time.perf_counter() - span_load_started) * 1000, 3)

            if run_span_probe:
                span_probe_started = time.perf_counter()
                span_detector.detect(normalize("씨발 테스트"))
                timings["span_detector_probe_ms"] = round((time.perf_counter() - span_probe_started) * 1000, 3)

        after = self.runtime_status()
        return {
            "ok": True,
            "before": before,
            "after": after,
            "timings": timings,
            "total_ms": round((time.perf_counter() - started) * 1000, 3),
        }

    def _analyze_word_lexicon(
        self,
        *,
        text: str,
        normalized: str,
        normalize_ms: float,
        pipeline_ms: float,
    ) -> dict | None:
        """Return a lexicon hit for single-word inputs; fallback on miss."""
        if self.word_lexicon is None:
            return None
        word = normalized.strip()
        try:
            if not is_word_like(word):
                return None
        except (ImportError, RuntimeError, OSError):
            return None

        lex = self.word_lexicon.lookup(word)
        if lex is None:
            return None

        is_profane = bool(lex["is_profane"])
        is_toxic = bool(lex["is_toxic"])
        is_hate = bool(lex["is_hate"])
        is_offensive = is_profane or is_toxic or is_hate
        evidence_spans = []
        if is_offensive:
            evidence_spans.append({
                "text": text,
                "start": 0,
                "end": len(text),
                "score": 1.0,
            })

        return {
            "text": text,
            "is_offensive": is_offensive,
            "is_profane": is_profane,
            "is_toxic": is_toxic,
            "is_hate": is_hate,
            "scores": {
                "profanity": 1.0 if is_profane else 0.0,
                "toxicity": 1.0 if is_toxic else 0.0,
                "hate": 1.0 if is_hate else 0.0,
            },
            "evidence_spans": evidence_spans,
            "source": "lexicon",
            "_timing": {
                "normalize_ms": round(normalize_ms, 3),
                "classifier_ms": 0.0,
                "span_ms": 0.0,
                "model_ms": 0.0,
                "pipeline_ms": round(pipeline_ms, 3),
            },
        }

    def analyze(self, text: str, sensitivity: int | None = None) -> dict:
        """단일 텍스트 분석.

        분류기가 주 판정자, span은 근거 제공자.
        분류기 판정(is_offensive)을 span이 뒤집지 않는다.
        """
        total_started = time.perf_counter()

        # [1단계] 정규화
        normalize_started = time.perf_counter()
        normalized = normalize(text)
        normalize_ms = (time.perf_counter() - normalize_started) * 1000
        threshold = _threshold_from_sensitivity(sensitivity, self.threshold)

        lexicon_result = self._analyze_word_lexicon(
            text=text,
            normalized=normalized,
            normalize_ms=normalize_ms,
            pipeline_ms=(time.perf_counter() - total_started) * 1000,
        )
        if lexicon_result is not None:
            return lexicon_result

        if (
            _is_latin_safe_text(text)
            or _is_latin_safe_text(normalized)
            or _is_safe_context(text, normalized)
            or _is_whitelisted_safe_text(text, normalized)
        ):
            result = _empty_result(text)
            result["_timing"]["normalize_ms"] = round(normalize_ms, 3)
            result["_timing"]["pipeline_ms"] = round((time.perf_counter() - total_started) * 1000, 3)
            return result

        n2o = _build_norm_to_orig_map(text, normalized)
        direct_spans = _merge_spans(
            _extract_original_direct_spans(text) + _extract_dictionary_spans(text, normalized, n2o),
            text,
        )

        # [2단계] 문장 분류
        classifier_started = time.perf_counter()
        cls_result = self._get_classifier().predict(normalized, threshold=threshold)
        classifier_ms = (time.perf_counter() - classifier_started) * 1000

        direct_is_offensive = any(span["score"] >= threshold for span in direct_spans)
        is_offensive = (
            cls_result["is_profane"]
            or cls_result["is_toxic"]
            or cls_result["is_hate"]
            or direct_is_offensive
        )

        # [3단계] Span 추출 — 분류기가 유해 판정한 경우에만 실행
        evidence_spans = list(direct_spans) if direct_is_offensive else []
        span_ms = 0.0
        if is_offensive and not evidence_spans:
            span_started = time.perf_counter()
            raw_spans = self._get_span_detector().detect(normalized)
            span_ms = (time.perf_counter() - span_started) * 1000

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

            evidence_spans = _merge_spans(evidence_spans, text)

        total_pipeline_ms = (time.perf_counter() - total_started) * 1000
        model_ms = classifier_ms + span_ms

        return {
            "text": text,
            "is_offensive": is_offensive,
            "is_profane": cls_result["is_profane"],
            "is_toxic": cls_result["is_toxic"],
            "is_hate": cls_result["is_hate"],
            "scores": cls_result["scores"],
            "evidence_spans": evidence_spans,
            "_timing": {
                "normalize_ms": round(normalize_ms, 3),
                "classifier_ms": round(classifier_ms, 3),
                "span_ms": round(span_ms, 3),
                "model_ms": round(model_ms, 3),
                "pipeline_ms": round(total_pipeline_ms, 3),
            },
        }

    def analyze_batch(self, texts: list[str], sensitivity: int | None = None) -> list[dict]:
        """배치 텍스트 분석."""
        threshold = _threshold_from_sensitivity(sensitivity, self.threshold)

        normalized_items: list[str] = []
        normalize_times: list[float] = []
        results: list[dict | None] = [None] * len(texts)
        classifier_indexes: list[int] = []
        classifier_texts: list[str] = []

        for index, text in enumerate(texts):
            normalize_started = time.perf_counter()
            normalized = normalize(text)
            normalize_ms = (time.perf_counter() - normalize_started) * 1000
            normalized_items.append(normalized)
            normalize_times.append(normalize_ms)

            lexicon_result = self._analyze_word_lexicon(
                text=text,
                normalized=normalized,
                normalize_ms=normalize_ms,
                pipeline_ms=normalize_ms,
            )
            if lexicon_result is not None:
                results[index] = lexicon_result
                continue

            if (
                _is_latin_safe_text(text)
                or _is_latin_safe_text(normalized)
                or _is_safe_context(text, normalized)
                or _is_whitelisted_safe_text(text, normalized)
            ):
                result = _empty_result(text)
                result["_timing"]["normalize_ms"] = round(normalize_ms, 3)
                result["_timing"]["pipeline_ms"] = round(normalize_ms, 3)
                results[index] = result
                continue

            classifier_indexes.append(index)
            classifier_texts.append(normalized)

        if classifier_texts:
            classifier_started = time.perf_counter()
            cls_results = self._get_classifier().predict_batch(classifier_texts, threshold=threshold)
            total_classifier_ms = (time.perf_counter() - classifier_started) * 1000
            per_item_classifier_ms = total_classifier_ms / max(1, len(classifier_texts))

            for batch_pos, index in enumerate(classifier_indexes):
                post_classifier_started = time.perf_counter()
                text = texts[index]
                normalized = normalized_items[index]
                cls_result = cls_results[batch_pos]
                n2o = _build_norm_to_orig_map(text, normalized)
                direct_spans = _merge_spans(
                    _extract_original_direct_spans(text) + _extract_dictionary_spans(text, normalized, n2o),
                    text,
                )

                direct_is_offensive = any(span["score"] >= threshold for span in direct_spans)
                is_offensive = (
                    cls_result["is_profane"]
                    or cls_result["is_toxic"]
                    or cls_result["is_hate"]
                    or direct_is_offensive
                )

                evidence_spans = list(direct_spans) if direct_is_offensive else []
                span_ms = 0.0
                if is_offensive and not evidence_spans:
                    span_started = time.perf_counter()
                    raw_spans = self._get_span_detector().detect(normalized)
                    span_ms = (time.perf_counter() - span_started) * 1000

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

                    evidence_spans = _merge_spans(evidence_spans, text)

                post_classifier_ms = (time.perf_counter() - post_classifier_started) * 1000
                model_ms = per_item_classifier_ms + span_ms
                pipeline_ms = normalize_times[index] + per_item_classifier_ms + post_classifier_ms
                results[index] = {
                    "text": text,
                    "is_offensive": is_offensive,
                    "is_profane": cls_result["is_profane"],
                    "is_toxic": cls_result["is_toxic"],
                    "is_hate": cls_result["is_hate"],
                    "scores": cls_result["scores"],
                    "evidence_spans": evidence_spans,
                    "_timing": {
                        "normalize_ms": round(normalize_times[index], 3),
                        "classifier_ms": round(per_item_classifier_ms, 3),
                        "span_ms": round(span_ms, 3),
                        "model_ms": round(model_ms, 3),
                        "pipeline_ms": round(pipeline_ms, 3),
                    },
                }

        return [result if result is not None else _empty_result(texts[index]) for index, result in enumerate(results)]

    def analyze_android_batch(self, raw: dict, sensitivity: int | None = None) -> dict:
        """Android 앱 수집 JSON 전체 처리.

        0단계 필터 → 분석 → boundsInScreen 보존하여 반환.
        """
        total = len(raw.get("comments", []))
        valid_comments = filter_android_json(raw)
        request_sensitivity = raw.get("sensitivity") if sensitivity is None else sensitivity

        results = []
        analyses = self.analyze_batch(
            [item["commentText"] for item in valid_comments],
            sensitivity=request_sensitivity,
        )

        for item, analysis in zip(valid_comments, analyses, strict=True):
            text = item["commentText"]
            bounds = item["boundsInScreen"]

            results.append({
                "original": text,
                "boundsInScreen": bounds,
                "author_id": item.get("author_id"),
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
