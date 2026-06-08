#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import io
import json
import statistics
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime
from pathlib import Path
from typing import Any


DEFAULT_RESULTS_DIR = Path("evaluation/latency/results/current")
DEFAULT_OUTPUT = DEFAULT_RESULTS_DIR / "chungmaru-latency-samples.csv"
DEFAULT_SUMMARY_OUTPUT = DEFAULT_RESULTS_DIR / "chungmaru-latency-summary.csv"
DEFAULT_REPORT_OUTPUT = DEFAULT_RESULTS_DIR / "chungmaru-latency-report.md"
DEFAULT_PIPELINE_OUTPUT = DEFAULT_RESULTS_DIR / "chungmaru-pipeline-latency.csv"
DEFAULT_MATRIX_SCENARIOS = [
    "clean",
    "clean-topic",
    "profanity",
    "toxicity",
    "hate",
    "bypass",
    "parser-noise",
    "search-result",
    "mixed",
]
DEFAULT_MATRIX_BATCH_SIZES = [1, 4, 8, 16, 24, 32]
DEFAULT_AGGREGATE_METRICS = [
    "risk_gate_mask_ms",
    "backend_roundtrip_ms",
    "backend_internal_avg_ms",
    "backend_model_avg_ms",
    "total_to_mask_ms",
    "first_mask_ms",
]

CSV_COLUMNS = [
    "run_id",
    "sample_id",
    "measured_at",
    "source",
    "scenario",
    "platform",
    "url",
    "run_reason",
    "backend_url",
    "sensitivity",
    "batch_size",
    "total_candidate_count",
    "dirty_candidate_count",
    "foreground_candidate_count",
    "analysis_unit_count",
    "requested_text_count",
    "backend_request_count",
    "backend_cache_hit_count",
    "worker_cache_hit_count",
    "settings_load_ms",
    "candidate_collect_ms",
    "dirty_select_ms",
    "prioritize_ms",
    "foreground_select_ms",
    "parser_ms",
    "pre_backend_ms",
    "local_preflight_ms",
    "risk_gate_mask_ms",
    "risk_gate_event_age_ms",
    "risk_gate_receive_to_mask_ms",
    "fast_provisional_mask_ms",
    "fast_provisional_event_age_ms",
    "fast_provisional_build_ms",
    "fast_provisional_overlay_ms",
    "fast_provisional_receive_to_mask_ms",
    "backend_roundtrip_ms",
    "backend_reported_ms",
    "backend_queue_wait_ms",
    "backend_request_duration_max_ms",
    "backend_internal_avg_ms",
    "backend_internal_max_ms",
    "backend_model_avg_ms",
    "backend_model_max_ms",
    "decision_build_ms",
    "mask_apply_ms",
    "post_backend_to_mask_ms",
    "first_mask_ms",
    "visible_first_mask_ms",
    "preexisting_mask_count",
    "total_to_mask_ms",
    "reconcile_ms",
    "masked_span_count",
    "returned_span_count",
    "dropped_span_count",
    "blocked_node_count",
    "positive_count",
    "profane_count",
    "toxic_count",
    "hate_count",
    "status",
    "error_code",
    "notes",
]

SUMMARY_COLUMNS = [
    "run_id",
    "source",
    "scenario",
    "batch_size",
    "metric",
    "count",
    "avg",
    "median",
    "p95",
    "min",
    "max",
]

PIPELINE_STAGE_COLUMNS = [
    "run_id",
    "sample_id",
    "measured_at",
    "source",
    "platform",
    "scenario",
    "query_or_url",
    "expected_result",
    "status",
    "result",
    "masked_span_count",
    "returned_span_count",
    "preexisting_mask_count",
    "pipeline_path",
    "settings_load_ms",
    "candidate_collect_ms",
    "dirty_select_ms",
    "prioritize_ms",
    "foreground_select_ms",
    "parser_ms",
    "pre_backend_checkpoint_ms",
    "local_preflight_ms",
    "backend_roundtrip_ms",
    "backend_reported_ms",
    "decision_build_ms",
    "mask_apply_ms",
    "post_backend_checkpoint_ms",
    "first_visible_mask_ms",
    "calculated_stage_sum_ms",
    "pipeline_done_ms",
    "visible_result_ms",
    "unaccounted_ms",
    "notes",
]

NON_OVERLAPPING_STAGE_FIELDS = [
    "settings_load_ms",
    "candidate_collect_ms",
    "dirty_select_ms",
    "prioritize_ms",
    "foreground_select_ms",
    "parser_ms",
    "local_preflight_ms",
    "backend_roundtrip_ms",
    "decision_build_ms",
    "mask_apply_ms",
]

BASE_FIXTURE_POOLS = {
    "clean": [
        "안녕하세요 좋은 영상 감사합니다",
        "오늘 자료가 도움이 됐습니다",
        "설명 방식이 이해하기 쉬웠습니다",
        "다음 영상도 기대하겠습니다",
        "이 부분은 다시 확인해보면 좋겠습니다",
        "서로 다른 의견을 비교해볼 필요가 있습니다",
        "관련 기사 링크가 있으면 공유해주세요",
        "토론 주제가 흥미롭습니다",
        "근거를 조금 더 보강하면 좋겠습니다",
        "이 문장은 정상 댓글입니다",
    ],
    "clean-topic": [
        "차별금지법 관련 기사 읽어봤습니다",
        "성소수자 인권 관련 토론입니다",
        "장애인 이동권 정책에 대한 설명입니다",
        "이주민 노동 정책 관련 자료입니다",
        "여성 안전 정책에 대한 의견입니다",
        "종교 자유와 표현의 자유를 비교한 글입니다",
        "다문화 교육 사례를 소개합니다",
        "혐오 표현 규제 기준에 대한 학술 토론입니다",
        "사회적 소수자 보호 제도 설명입니다",
        "인권 조례 관련 보도자료입니다",
    ],
    "profanity": [
        "씨발 뭐냐 진짜",
        "병신같은 소리 하지마",
        "개새끼야 꺼져",
        "존나 짜증나네",
        "지랄하지 마라",
        "닥쳐 진짜",
        "꺼져라",
        "미친놈인가",
        "뭐 이런 병신 같은 상황이 있냐",
        "시발 이건 아니지",
    ],
    "toxicity": [
        "너 때문에 분위기 다 망쳤다",
        "그런 식으로 말하면 아무도 안 들어준다",
        "계속 그렇게 굴면 신고한다",
        "남들한테 피해 주지 말고 그만해라",
        "너는 대화할 기본이 안 되어 있다",
        "상대방을 비꼬는 댓글입니다",
        "괜히 시비 걸지 말고 지나가라",
        "그 말투는 공격적으로 들립니다",
        "일부러 분란 만들려고 쓴 댓글처럼 보입니다",
        "상대를 조롱하는 표현이 포함된 문장입니다",
    ],
    "hate": [
        "특정 집단을 낮춰 부르는 표현이 포함된 문장",
        "사회적 집단 전체를 비하하는 댓글",
        "정체성을 이유로 배제하자는 주장",
        "소수자 집단을 조롱하는 표현",
        "집단 전체를 문제로 일반화하는 문장",
        "차별적 고정관념을 반복하는 댓글",
        "국적을 이유로 모욕하는 표현",
        "종교 집단을 싸잡아 비난하는 문장",
        "성별을 이유로 무시하는 표현",
        "장애를 조롱의 소재로 쓰는 문장",
    ],
    "bypass": [
        "ssibal 같은 우회 표현",
        "tlqkf 이거 뭐냐",
        "qudtls 같은 표기",
        "ㅅ ㅂ 진짜",
        "ㅂ ㅅ 같은 소리",
        "s i b a l 이라고 쓰는 경우",
        "시-발처럼 기호를 섞은 문장",
        "병 신처럼 띄어쓴 표현",
        "개  새  끼처럼 공백을 많이 넣은 표현",
        "초성으로만 적은 ㅈㄴ 짜증",
    ],
    "parser-noise": [
        "좋아요 34개 · 답글달기 · 2시간 전 · 실제 댓글 본문",
        "더보기 설정 댓글 좋아요 12개 이 영상 좋네요",
        "답글 3개 보기 1일 전 자료 감사합니다",
        "구독 좋아요 알림 설정 정상 댓글입니다",
        "댓글 더보기 공유 저장 5시간 전 이 부분 공감합니다",
        "좋아요 · 싫어요 · 답글 · 신고 · 본문 후보",
        "Pinned by creator 3시간 전 설명이 좋습니다",
        "Translate to Korean 답글달기 실제 의견입니다",
        "댓글 없음 새로고침 설정",
        "사용자명 2일 전 좋아요 9개 좋은 의견입니다",
    ],
    "search-result": [
        "씨발 - 나무위키 검색 결과 제목",
        "한국어 욕설 뜻과 용례를 설명한 문서",
        "시발 자동차 역사 자료 검색 결과",
        "욕설 필터링 연구 논문 검색 결과",
        "청소년 유해 콘텐츠 차단 서비스 소개",
        "성소수자 차별금지법 관련 뉴스",
        "혐오 표현 규제 기준 검색 결과",
        "브라우저 확장 프로그램 사용법",
        "나무위키 검색 결과 스니펫",
        "구글 검색 카드 안의 제목과 설명",
    ],
}

VARIATION_PREFIXES = ["", "댓글: ", "사용자 의견 - ", "검색 결과: "]
VARIATION_SUFFIXES = ["", " ㅋㅋ", " / 추가 의견", " · 답글달기", " · 2시간 전"]


def now_iso() -> str:
    return datetime.now().astimezone().isoformat(timespec="seconds")


def run_id() -> str:
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    return f"latency-{stamp}-{uuid.uuid4().hex[:8]}"


def number(value: Any, default: float = 0.0) -> float:
    try:
        parsed = float(value)
    except (TypeError, ValueError):
        return default
    if parsed != parsed:
        return default
    return parsed


def rounded(value: Any, digits: int = 3) -> str:
    parsed = number(value)
    return str(round(parsed, digits))


def optional_number(value: Any) -> float | None:
    if value in (None, ""):
        return None
    try:
        parsed = float(value)
    except (TypeError, ValueError):
        return None
    if parsed != parsed:
        return None
    return parsed


def optional_rounded(value: Any, digits: int = 3) -> str:
    parsed = optional_number(value)
    return "" if parsed is None else str(round(parsed, digits))


def parse_json_object(value: Any) -> dict[str, Any]:
    if isinstance(value, dict):
        return value
    if not isinstance(value, str) or not value.strip():
        return {}
    try:
        parsed = json.loads(value)
    except json.JSONDecodeError:
        return {}
    return parsed if isinstance(parsed, dict) else {}


def blank_row() -> dict[str, Any]:
    return {column: "" for column in CSV_COLUMNS}


def write_rows(output: Path, rows: list[dict[str, Any]], overwrite: bool = False) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    write_header = overwrite or not output.exists() or output.stat().st_size == 0
    mode = "w" if overwrite else "a"
    if not overwrite and not write_header:
        raw = output.read_bytes()
        text = raw.decode("utf-8-sig")
        reader = csv.DictReader(io.StringIO(text))
        existing_fields = reader.fieldnames or []
        if existing_fields != CSV_COLUMNS:
            existing_rows = list(reader)
            with output.open("w", encoding="utf-8", newline="") as handle:
                writer = csv.DictWriter(handle, fieldnames=CSV_COLUMNS, lineterminator="\n")
                writer.writeheader()
                for row in existing_rows:
                    writer.writerow({column: row.get(column, "") for column in CSV_COLUMNS})
    with output.open(mode, encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_COLUMNS, lineterminator="\n")
        if write_header:
            writer.writeheader()
        for row in rows:
            writer.writerow({column: row.get(column, "") for column in CSV_COLUMNS})


def write_summary_rows(output: Path, rows: list[dict[str, Any]], overwrite: bool = True) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    write_header = overwrite or not output.exists() or output.stat().st_size == 0
    with output.open("w" if overwrite else "a", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=SUMMARY_COLUMNS, lineterminator="\n")
        if write_header:
            writer.writeheader()
        for row in rows:
            writer.writerow({column: row.get(column, "") for column in SUMMARY_COLUMNS})


def expand_fixture_pool(values: list[str]) -> list[str]:
    expanded: list[str] = []
    seen: set[str] = set()
    for value in values:
        for prefix in VARIATION_PREFIXES:
            for suffix in VARIATION_SUFFIXES:
                candidate = f"{prefix}{value}{suffix}".strip()
                if candidate and candidate not in seen:
                    seen.add(candidate)
                    expanded.append(candidate)
    return expanded


def get_fixture_pool(scenario: str) -> list[str]:
    normalized = str(scenario or "mixed").strip()
    if normalized == "mixed":
        mixed: list[str] = []
        for key in DEFAULT_MATRIX_SCENARIOS:
            if key == "mixed":
                continue
            mixed.extend(BASE_FIXTURE_POOLS.get(key, []))
        return expand_fixture_pool(mixed)

    if normalized in BASE_FIXTURE_POOLS:
        return expand_fixture_pool(BASE_FIXTURE_POOLS[normalized])

    return expand_fixture_pool(BASE_FIXTURE_POOLS["clean"])


def parse_csv_list(raw_value: str, default: list[str]) -> list[str]:
    values = [item.strip() for item in str(raw_value or "").split(",") if item.strip()]
    return values or list(default)


def parse_int_list(raw_value: str, default: list[int]) -> list[int]:
    values: list[int] = []
    for item in str(raw_value or "").split(","):
        item = item.strip()
        if not item:
            continue
        values.append(max(1, int(item)))
    return values or list(default)


def select_texts(batch_size: int, offset: int = 0, scenario: str = "mixed") -> list[str]:
    size = max(1, int(batch_size))
    fixture_pool = get_fixture_pool(scenario)
    return [fixture_pool[(offset + index) % len(fixture_pool)] for index in range(size)]


def post_analyze_batch(
    backend_url: str,
    texts: list[str],
    sensitivity: int,
    timeout_s: float,
) -> tuple[float, dict[str, Any]]:
    payload = json.dumps(
        {"texts": texts, "sensitivity": sensitivity},
        ensure_ascii=False,
    ).encode("utf-8")
    request = urllib.request.Request(
        f"{backend_url.rstrip('/')}/analyze_batch",
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    started = time.perf_counter()
    with urllib.request.urlopen(request, timeout=timeout_s) as response:
        body = json.loads(response.read())
    return (time.perf_counter() - started) * 1000, body


def summarize_result_flags(results: list[dict[str, Any]]) -> dict[str, int]:
    return {
        "positive_count": sum(1 for item in results if item.get("is_offensive")),
        "profane_count": sum(1 for item in results if item.get("is_profane")),
        "toxic_count": sum(1 for item in results if item.get("is_toxic")),
        "hate_count": sum(1 for item in results if item.get("is_hate")),
    }


def summarize_result_timings(results: list[dict[str, Any]]) -> dict[str, float]:
    pipeline_values = [
        number(item.get("timing_ms"))
        for item in results
        if isinstance(item.get("timing_ms"), (int, float))
    ]
    model_values = [
        number(item.get("model_timing_ms"))
        for item in results
        if isinstance(item.get("model_timing_ms"), (int, float))
    ]

    def avg(values: list[float]) -> float:
        return statistics.mean(values) if values else 0.0

    def max_value(values: list[float]) -> float:
        return max(values) if values else 0.0

    return {
        "backend_internal_avg_ms": avg(pipeline_values),
        "backend_internal_max_ms": max_value(pipeline_values),
        "backend_model_avg_ms": avg(model_values),
        "backend_model_max_ms": max_value(model_values),
    }


def measure_backend_sample(
    *,
    backend_url: str,
    sensitivity: int,
    timeout_s: float,
    current_run_id: str,
    sample_id: int,
    scenario: str,
    batch_size: int,
    offset: int,
) -> tuple[dict[str, Any], float | None]:
    texts = select_texts(batch_size, offset, scenario)
    row = blank_row()
    row.update(
        {
            "run_id": current_run_id,
            "sample_id": sample_id,
            "measured_at": now_iso(),
            "source": "backend-direct",
            "scenario": scenario,
            "platform": "backend",
            "backend_url": backend_url,
            "sensitivity": sensitivity,
            "batch_size": len(texts),
            "requested_text_count": len(texts),
            "backend_request_count": 1,
        }
    )

    try:
        wall_ms, body = post_analyze_batch(backend_url, texts, sensitivity, timeout_s)
        results = [item for item in body.get("results", []) if isinstance(item, dict)]
        row.update(summarize_result_flags(results))
        row.update({key: rounded(value) for key, value in summarize_result_timings(results).items()})
        row.update(
            {
                "backend_roundtrip_ms": rounded(wall_ms),
                "backend_reported_ms": rounded(wall_ms),
                "total_to_mask_ms": "",
                "status": "ok",
                "notes": "direct /analyze_batch only; parser and masking phases not measured",
            }
        )
        return row, wall_ms
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError) as error:
        row.update(
            {
                "status": "failed",
                "error_code": type(error).__name__,
                "notes": str(error),
            }
        )
        return row, None


def benchmark_backend(args: argparse.Namespace) -> None:
    current_run_id = args.run_id or run_id()
    rows: list[dict[str, Any]] = []
    wall_values: list[float] = []

    for warmup_index in range(max(0, args.warmup)):
        try:
            post_analyze_batch(
                args.backend,
                select_texts(args.batch_size, warmup_index, args.scenario),
                args.sensitivity,
                args.timeout,
            )
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError):
            break

    for sample_index in range(1, args.samples + 1):
        row, wall_ms = measure_backend_sample(
            backend_url=args.backend,
            sensitivity=args.sensitivity,
            timeout_s=args.timeout,
            current_run_id=current_run_id,
            sample_id=sample_index,
            scenario=args.scenario,
            batch_size=args.batch_size,
            offset=sample_index - 1,
        )
        if wall_ms is not None:
            wall_values.append(wall_ms)

        rows.append(row)
        if args.flush_every and len(rows) >= args.flush_every:
            write_rows(args.output, rows, overwrite=args.overwrite and sample_index == len(rows))
            args.overwrite = False
            rows = []
        if args.sleep_ms > 0:
            time.sleep(args.sleep_ms / 1000)

    if rows:
        write_rows(args.output, rows, overwrite=args.overwrite)

    print(f"wrote={args.output}")
    print(f"run_id={current_run_id}")
    if wall_values:
        print(
            "backend_roundtrip_ms "
            f"avg={statistics.mean(wall_values):.2f} "
            f"median={statistics.median(wall_values):.2f} "
            f"min={min(wall_values):.2f} "
            f"max={max(wall_values):.2f}"
        )


def load_json_records(path: Path) -> list[dict[str, Any]]:
    if path.suffix.lower() == ".jsonl":
        records = []
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.strip():
                records.append(json.loads(line))
        return records

    payload = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(payload, list):
        return [item for item in payload if isinstance(item, dict)]
    if isinstance(payload, dict):
        if isinstance(payload.get("records"), list):
            return [item for item in payload["records"] if isinstance(item, dict)]
        if isinstance(payload.get("samples"), list):
            return [item for item in payload["samples"] if isinstance(item, dict)]
        return [payload]
    return []


def pick_nested(record: dict[str, Any], *keys: str) -> Any:
    value: Any = record
    for key in keys:
        if not isinstance(value, dict):
            return None
        value = value.get(key)
    return value


def normalize_extension_record(record: dict[str, Any], sample_index: int, args: argparse.Namespace) -> dict[str, Any]:
    stats = record.get("lastStats") if isinstance(record.get("lastStats"), dict) else record
    diagnostics = stats.get("lastForegroundDiagnostics") if isinstance(stats.get("lastForegroundDiagnostics"), dict) else {}
    phases = stats.get("phaseTimings") if isinstance(stats.get("phaseTimings"), dict) else {}
    internal = (
        stats.get("backendInternalTimingSummary")
        if isinstance(stats.get("backendInternalTimingSummary"), dict)
        else diagnostics.get("backendInternalTimingSummary")
        if isinstance(diagnostics.get("backendInternalTimingSummary"), dict)
        else {}
    )
    pipeline = internal.get("backendPipeline") if isinstance(internal.get("backendPipeline"), dict) else {}
    model = internal.get("backendModel") if isinstance(internal.get("backendModel"), dict) else {}
    request_timings = diagnostics.get("backendRequestTimings")
    if not isinstance(request_timings, list):
        request_timings = []
    request_duration_max = max([number(item.get("durationMs")) for item in request_timings if isinstance(item, dict)] or [0])

    row = blank_row()
    row.update(
        {
            "run_id": str(record.get("run_id") or args.run_id or run_id()),
            "sample_id": record.get("sample_id") or sample_index,
            "measured_at": record.get("measured_at") or record.get("lastRunAt") or now_iso(),
            "source": "extension-lastStats",
            "scenario": record.get("scenario") or args.scenario,
            "platform": args.platform,
            "url": record.get("url") or stats.get("url") or "",
            "run_reason": stats.get("runReason") or "",
            "backend_url": stats.get("backendEndpoint") or diagnostics.get("apiBaseUrl") or "",
            "sensitivity": record.get("sensitivity") or "",
            "batch_size": record.get("batch_size")
            or diagnostics.get("batchSize")
            or stats.get("visibleContainerBatchSize")
            or "",
            "total_candidate_count": stats.get("totalCandidateCount") or "",
            "dirty_candidate_count": stats.get("dirtyCandidateCount") or "",
            "foreground_candidate_count": stats.get("foregroundCandidateCount") or "",
            "analysis_unit_count": stats.get("visibleContainerBatchSize") or diagnostics.get("batchSize") or "",
            "requested_text_count": diagnostics.get("requestedTextCount") or stats.get("requestedAnalysisCount") or "",
            "backend_request_count": diagnostics.get("requestCount") or stats.get("foregroundRequestCount") or "",
            "backend_cache_hit_count": stats.get("backendCacheHitCount") or diagnostics.get("backendCacheHitCount") or "",
            "worker_cache_hit_count": stats.get("workerCacheHitCount") or diagnostics.get("cacheHitCount") or "",
            "settings_load_ms": phases.get("settingsLoadMs") or "",
            "candidate_collect_ms": phases.get("candidateCollectMs") or "",
            "dirty_select_ms": phases.get("dirtySelectMs") or "",
            "prioritize_ms": phases.get("prioritizeMs") or "",
            "foreground_select_ms": phases.get("foregroundSelectMs") or "",
            "parser_ms": phases.get("parserMs") or stats.get("foregroundUnitBuildMs") or "",
            "pre_backend_ms": phases.get("preBackendMs") or "",
            "local_preflight_ms": phases.get("localPreflightMs") or "",
            "backend_roundtrip_ms": phases.get("backendRoundTripMs") or stats.get("foregroundBackendLatencyMs") or "",
            "backend_reported_ms": phases.get("backendReportedMs") or stats.get("hotPathLatencyMs") or stats.get("backendDurationMs") or "",
            "backend_queue_wait_ms": stats.get("foregroundBackendQueueWaitMs") or diagnostics.get("backendQueueWaitMs") or "",
            "backend_request_duration_max_ms": rounded(request_duration_max),
            "backend_internal_avg_ms": pipeline.get("avgMs") or "",
            "backend_internal_max_ms": pipeline.get("maxMs") or "",
            "backend_model_avg_ms": model.get("avgMs") or "",
            "backend_model_max_ms": model.get("maxMs") or "",
            "decision_build_ms": phases.get("decisionBuildMs") or "",
            "mask_apply_ms": phases.get("maskApplyMs") or "",
            "post_backend_to_mask_ms": phases.get("postBackendToMaskMs") or "",
            "first_mask_ms": stats.get("firstMaskLatencyMs") or "",
            "total_to_mask_ms": phases.get("totalToMaskMs") or stats.get("durationMs") or "",
            "reconcile_ms": stats.get("backendReconcileLatencyMs") or "",
            "masked_span_count": stats.get("maskedSpanCount") or diagnostics.get("appliedSpanCount") or "",
            "returned_span_count": stats.get("returnedSpanCount") or diagnostics.get("returnedSpanCount") or "",
            "dropped_span_count": stats.get("droppedSpanCount") or diagnostics.get("droppedSpanCount") or "",
            "blocked_node_count": stats.get("blockedNodeCount") or "",
            "status": stats.get("backendStatus") or "unknown",
            "error_code": stats.get("foregroundLastBackendErrorCode") or diagnostics.get("lastBackendErrorCode") or "",
            "notes": record.get("notes") or "from extension lastStats export",
        }
    )
    return row


def import_extension_export(args: argparse.Namespace) -> None:
    records = load_json_records(args.input)
    rows = [
        normalize_extension_record(record, index + 1, args)
        for index, record in enumerate(records)
    ]
    write_rows(args.output, rows, overwrite=args.overwrite)
    print(f"imported={len(rows)}")
    print(f"wrote={args.output}")


def normalize_chrome_quick_qa_row(
    record: dict[str, Any],
    sample_index: int,
    args: argparse.Namespace,
) -> dict[str, Any]:
    row = blank_row()
    kind = str(record.get("kind") or "")
    query = str(record.get("query_or_url") or "")
    scenario = str(record.get("query_scenario_id") or record.get("query_category") or record.get("scenario") or kind)
    status = "ok" if str(record.get("ok") or "").lower() in {"true", "1", "yes"} else "unknown"
    if kind == "backend-warmup":
        status = "ok" if str(record.get("backend_ok") or "").lower() in {"true", "1", "yes"} else "failed"

    raw_note = parse_json_object(record.get("note"))
    notes = {
        "kind": kind,
        "scenario_label": record.get("scenario") or "",
        "query": query,
        "query_category": record.get("query_category") or "",
        "expected_result": record.get("expected_result") or "",
        "latency_note": record.get("latency_note") or "",
        "pipeline_total_to_mask_ms": raw_note.get("pipeline_total_to_mask_ms") or "",
        "decision_source": raw_note.get("decision_source") or "",
        "can_continue": record.get("can_continue") or "",
    }

    row.update(
        {
            "run_id": str(record.get("run_id") or args.run_id or run_id()),
            "sample_id": sample_index,
            "measured_at": record.get("captured_at") or args.measured_at or now_iso(),
            "source": "chrome-quick-qa",
            "scenario": scenario,
            "platform": "backend" if kind == "backend-warmup" else args.platform,
            "url": query,
            "run_reason": kind or record.get("scenario") or "",
            "backend_url": args.backend,
            "sensitivity": record.get("sensitivity") or "",
            "batch_size": record.get("requested_analysis_count") or "",
            "total_candidate_count": record.get("total_candidate_count") or "",
            "foreground_candidate_count": record.get("foreground_candidate_count") or "",
            "analysis_unit_count": record.get("requested_analysis_count") or "",
            "requested_text_count": record.get("requested_analysis_count") or "",
            "backend_request_count": record.get("requested_analysis_count") or "",
            "settings_load_ms": record.get("settings_load_ms") or "",
            "candidate_collect_ms": record.get("candidate_collect_ms") or "",
            "dirty_select_ms": record.get("dirty_select_ms") or "",
            "prioritize_ms": record.get("prioritize_ms") or "",
            "foreground_select_ms": record.get("foreground_select_ms") or "",
            "parser_ms": record.get("parser_ms") or "",
            "pre_backend_ms": record.get("pre_backend_ms") or "",
            "local_preflight_ms": record.get("local_preflight_ms") or "",
            "backend_roundtrip_ms": record.get("backend_round_trip_ms") or "",
            "backend_reported_ms": record.get("backend_reported_ms") or "",
            "decision_build_ms": record.get("decision_build_ms") or "",
            "mask_apply_ms": record.get("mask_apply_ms") or "",
            "post_backend_to_mask_ms": record.get("post_backend_to_mask_ms") or "",
            "first_mask_ms": record.get("first_mask_latency_ms") or "",
            "visible_first_mask_ms": record.get("visible_first_mask_ms") or "",
            "preexisting_mask_count": record.get("preexisting_mask_count") or "",
            "total_to_mask_ms": record.get("total_to_mask_ms") or "",
            "masked_span_count": record.get("effective_masked_span_count") or "",
            "returned_span_count": record.get("returned_span_count") or "",
            "status": status,
            "error_code": "" if status == "ok" else "QUICK_QA_NOT_OK",
            "notes": json.dumps(notes, ensure_ascii=False, separators=(",", ":")),
        }
    )
    return row


def import_chrome_quick_qa(args: argparse.Namespace) -> None:
    with args.input.open(encoding="utf-8-sig", newline="") as handle:
        records = list(csv.DictReader(handle))
    if args.run_id:
        records = [record for record in records if str(record.get("run_id") or "") == args.run_id]
    rows = [
        normalize_chrome_quick_qa_row(record, index + 1, args)
        for index, record in enumerate(records)
    ]
    write_rows(args.output, rows, overwrite=args.overwrite)
    print(f"imported={len(rows)}")
    print(f"wrote={args.output}")


def normalize_android_pipeline_row(
    record: dict[str, Any],
    sample_index: int,
    args: argparse.Namespace,
    current_run_id: str,
) -> dict[str, Any]:
    row = blank_row()
    mode = str(record.get("mode") or "")
    stages = str(record.get("stages") or "")
    artifact_dir = str(record.get("artifact_dir") or "")
    is_skipped = mode == "skipped" or str(record.get("scenario") or "") == "android-no-device"
    collect_ms = record.get("collect_ms") or ""
    backend_api_ms = record.get("backend_api_ms") or ""
    backend_e2e_ms = record.get("backend_e2e_ms") or ""
    coord_ms = record.get("coord_ms") or ""
    display_ms = record.get("display_ms") or ""
    observed_total_ms = record.get("observed_total_ms") or ""
    risk_gate_mask_ms = record.get("risk_gate_mask_ms") or ""
    risk_gate_event_age_ms = record.get("risk_gate_event_age_ms") or ""
    risk_gate_receive_to_mask_ms = record.get("risk_gate_receive_to_mask_ms") or ""
    fast_provisional_mask_ms = record.get("fast_provisional_mask_ms") or ""
    fast_provisional_event_age_ms = record.get("fast_provisional_event_age_ms") or ""
    fast_provisional_build_ms = record.get("fast_provisional_build_ms") or ""
    fast_provisional_overlay_ms = record.get("fast_provisional_overlay_ms") or ""
    fast_provisional_receive_to_mask_ms = record.get("fast_provisional_receive_to_mask_ms") or ""

    overlay_rendered = number(record.get("overlay_rendered"), default=0)
    first_mask_ms = (
        risk_gate_mask_ms or
        fast_provisional_mask_ms or
        (observed_total_ms if overlay_rendered > 0 else "")
    )
    backend_roundtrip_ms = backend_e2e_ms or backend_api_ms

    row.update(
        {
            "run_id": current_run_id,
            "sample_id": sample_index,
            "measured_at": args.measured_at or now_iso(),
            "source": "android-pipeline-benchmark",
            "scenario": record.get("scenario") or args.scenario or mode,
            "platform": args.platform,
            "run_reason": mode,
            "backend_url": args.backend or "",
            "batch_size": record.get("screen_candidates") or "",
            "total_candidate_count": record.get("screen_candidates") or "",
            "foreground_candidate_count": record.get("screen_candidates") or "",
            "analysis_unit_count": record.get("screen_candidates") or "",
            "candidate_collect_ms": collect_ms,
            "pre_backend_ms": collect_ms if backend_roundtrip_ms else "",
            "local_preflight_ms": (
                risk_gate_receive_to_mask_ms or
                risk_gate_mask_ms or
                fast_provisional_receive_to_mask_ms or
                fast_provisional_mask_ms
            ),
            "risk_gate_mask_ms": risk_gate_mask_ms,
            "risk_gate_event_age_ms": risk_gate_event_age_ms,
            "risk_gate_receive_to_mask_ms": risk_gate_receive_to_mask_ms,
            "fast_provisional_mask_ms": fast_provisional_mask_ms,
            "fast_provisional_event_age_ms": fast_provisional_event_age_ms,
            "fast_provisional_build_ms": fast_provisional_build_ms,
            "fast_provisional_overlay_ms": fast_provisional_overlay_ms,
            "fast_provisional_receive_to_mask_ms": fast_provisional_receive_to_mask_ms,
            "backend_roundtrip_ms": backend_roundtrip_ms,
            "backend_reported_ms": backend_api_ms,
            "backend_request_duration_max_ms": backend_api_ms,
            "decision_build_ms": coord_ms,
            "mask_apply_ms": display_ms,
            "post_backend_to_mask_ms": max_nonempty_ms(coord_ms, display_ms),
            "first_mask_ms": first_mask_ms,
            "total_to_mask_ms": observed_total_ms,
            "masked_span_count": record.get("overlay_rendered") or "",
            "returned_span_count": record.get("overlay_candidates") or "",
            "blocked_node_count": record.get("overlay_candidates") or "",
            "positive_count": record.get("offensive") or "",
            "status": "skipped" if is_skipped else "ok",
            "error_code": "NO_ANDROID_DEVICE" if is_skipped else "",
            "notes": (
                f"android pipeline benchmark; mode={mode}; stages={stages}; "
                f"device={record.get('device') or ''}; artifact_dir={artifact_dir}"
            ),
        }
    )
    return row


def max_nonempty_ms(*values: Any) -> str:
    parsed = [number(value, default=-1) for value in values if str(value or "") not in {"", "n/a", "-1"}]
    if not parsed:
        return ""
    return rounded(max(parsed))


def import_android_pipeline(args: argparse.Namespace) -> None:
    with args.input.open(encoding="utf-8", newline="") as handle:
        records = list(csv.DictReader(handle))
    current_run_id = args.run_id or infer_android_pipeline_run_id(records, args.input)
    rows = [
        normalize_android_pipeline_row(record, index + 1, args, current_run_id)
        for index, record in enumerate(records)
    ]
    write_rows(args.output, rows, overwrite=args.overwrite)
    print(f"imported={len(rows)}")
    print(f"wrote={args.output}")


def write_pipeline_rows(output: Path, rows: list[dict[str, Any]], overwrite: bool = True) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    mode = "w" if overwrite else "a"
    write_header = overwrite or not output.exists() or output.stat().st_size == 0
    with output.open(mode, encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=PIPELINE_STAGE_COLUMNS, lineterminator="\n")
        if write_header:
            writer.writeheader()
        for row in rows:
            writer.writerow({column: row.get(column, "") for column in PIPELINE_STAGE_COLUMNS})


def pipeline_path_for(row: dict[str, Any], notes: dict[str, Any]) -> str:
    source = str(row.get("source") or "")
    masked = number(row.get("masked_span_count"), default=0)
    preexisting = number(row.get("preexisting_mask_count"), default=0)
    expected = str(notes.get("expected_result") or "")
    decision_source = str(notes.get("decision_source") or "")

    if preexisting > 0 and masked > 0:
        has_stage_work = any(row.get(field) for field in NON_OVERLAPPING_STAGE_FIELDS)
        return "preexisting-visible-mask + background-check" if has_stage_work else "preexisting-visible-mask"
    if source == "android-pipeline-benchmark":
        if row.get("risk_gate_mask_ms"):
            return "android-risk-gate"
        if row.get("fast_provisional_mask_ms"):
            return "android-fast-provisional"
        return "android-pipeline"
    if row.get("local_preflight_ms") and number(row.get("local_preflight_ms"), default=-1) >= 0:
        return "local-preflight"
    if row.get("backend_roundtrip_ms"):
        return decision_source or "backend-foreground"
    if expected == "allow":
        return "allow-no-mask"
    if masked > 0:
        return "local-high-signal"
    return decision_source or "unknown"


def pipeline_result_for(row: dict[str, Any], notes: dict[str, Any]) -> str:
    expected = str(notes.get("expected_result") or "")
    masked = number(row.get("masked_span_count"), default=0)
    status = str(row.get("status") or "")
    if status not in {"", "ok", "unknown"}:
        return status
    if expected in {"mask", "mask-spans"}:
        return "pass" if masked > 0 else "miss"
    if expected == "allow":
        return "pass" if masked <= 0 else "false-positive"
    return status or "unknown"


def stage_sum_for(row: dict[str, Any]) -> str:
    values = [
        optional_number(row.get(field))
        for field in NON_OVERLAPPING_STAGE_FIELDS
    ]
    present = [value for value in values if value is not None]
    if not present:
        return ""
    return optional_rounded(sum(present))


def pipeline_done_ms_for(row: dict[str, Any], notes: dict[str, Any]) -> str:
    raw_pipeline_total = notes.get("pipeline_total_to_mask_ms")
    if raw_pipeline_total not in (None, ""):
        return optional_rounded(raw_pipeline_total)
    return optional_rounded(row.get("total_to_mask_ms"))


def visible_result_ms_for(row: dict[str, Any], notes: dict[str, Any]) -> str:
    expected = str(notes.get("expected_result") or "")
    masked = number(row.get("masked_span_count"), default=0)
    if expected == "allow" or masked <= 0:
        return optional_rounded(row.get("total_to_mask_ms"))
    visible = row.get("visible_first_mask_ms") or row.get("first_mask_ms")
    return optional_rounded(visible)


def unaccounted_ms_for(pipeline_done_ms: str, stage_sum_ms: str) -> str:
    done = optional_number(pipeline_done_ms)
    stage_sum = optional_number(stage_sum_ms)
    if done is None or stage_sum is None:
        return ""
    return optional_rounded(done - stage_sum)


def compact_pipeline_row(row: dict[str, Any]) -> dict[str, Any]:
    notes = parse_json_object(row.get("notes"))
    stage_sum_ms = stage_sum_for(row)
    pipeline_done_ms = pipeline_done_ms_for(row, notes)
    result = pipeline_result_for(row, notes)
    expected = str(notes.get("expected_result") or "")
    query = str(notes.get("query") or row.get("url") or "")
    visible_result_ms = visible_result_ms_for(row, notes)
    return {
        "run_id": row.get("run_id") or "",
        "sample_id": row.get("sample_id") or "",
        "measured_at": row.get("measured_at") or "",
        "source": row.get("source") or "",
        "platform": row.get("platform") or "",
        "scenario": row.get("scenario") or "",
        "query_or_url": query,
        "expected_result": expected,
        "status": row.get("status") or "",
        "result": result,
        "masked_span_count": row.get("masked_span_count") or "",
        "returned_span_count": row.get("returned_span_count") or "",
        "preexisting_mask_count": row.get("preexisting_mask_count") or "",
        "pipeline_path": pipeline_path_for(row, notes),
        "settings_load_ms": optional_rounded(row.get("settings_load_ms")),
        "candidate_collect_ms": optional_rounded(row.get("candidate_collect_ms")),
        "dirty_select_ms": optional_rounded(row.get("dirty_select_ms")),
        "prioritize_ms": optional_rounded(row.get("prioritize_ms")),
        "foreground_select_ms": optional_rounded(row.get("foreground_select_ms")),
        "parser_ms": optional_rounded(row.get("parser_ms")),
        "pre_backend_checkpoint_ms": optional_rounded(row.get("pre_backend_ms")),
        "local_preflight_ms": optional_rounded(row.get("local_preflight_ms")),
        "backend_roundtrip_ms": optional_rounded(row.get("backend_roundtrip_ms")),
        "backend_reported_ms": optional_rounded(row.get("backend_reported_ms")),
        "decision_build_ms": optional_rounded(row.get("decision_build_ms")),
        "mask_apply_ms": optional_rounded(row.get("mask_apply_ms")),
        "post_backend_checkpoint_ms": optional_rounded(row.get("post_backend_to_mask_ms")),
        "first_visible_mask_ms": optional_rounded(row.get("visible_first_mask_ms") or row.get("first_mask_ms")),
        "calculated_stage_sum_ms": stage_sum_ms,
        "pipeline_done_ms": pipeline_done_ms,
        "visible_result_ms": visible_result_ms,
        "unaccounted_ms": unaccounted_ms_for(pipeline_done_ms, stage_sum_ms),
        "notes": str(notes.get("latency_note") or ""),
    }


def export_pipeline_csv(args: argparse.Namespace) -> None:
    with args.input.open(encoding="utf-8", newline="") as handle:
        source_rows = list(csv.DictReader(handle))

    rows: list[dict[str, Any]] = []
    for row in source_rows:
        if args.run_id and str(row.get("run_id") or "") != args.run_id:
            continue
        if args.source and str(row.get("source") or "") != args.source:
            continue
        if args.exclude_control and str(row.get("scenario") or "") in {"backend-warmup", "site-warning-direct"}:
            continue
        rows.append(compact_pipeline_row(row))

    write_pipeline_rows(args.output, rows, overwrite=True)
    print(f"pipeline_rows={len(rows)}")
    print(f"wrote={args.output}")


def infer_android_pipeline_run_id(records: list[dict[str, Any]], input_path: Path) -> str:
    for record in records:
        raw_run_id = str(record.get("run_id") or "")
        if "-" in raw_run_id:
            return f"android-pipeline-{raw_run_id.split('-', 1)[0]}"
    return f"android-pipeline-{input_path.parent.name or run_id()}"


def percentile(values: list[float], quantile: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, int(round((len(ordered) - 1) * quantile))))
    return ordered[index]


def aggregate_rows(
    csv_path: Path,
    metrics: list[str],
    run_id_filter: str = "",
) -> list[dict[str, Any]]:
    with csv_path.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))

    grouped: dict[tuple[str, str, str, str, str], list[float]] = {}
    for row in rows:
        row_run_id = str(row.get("run_id") or "")
        if run_id_filter and row_run_id != run_id_filter:
            continue
        for metric in metrics:
            value = number(row.get(metric), default=-1)
            if value < 0:
                continue
            key = (
                row_run_id,
                str(row.get("source") or ""),
                str(row.get("scenario") or ""),
                str(row.get("batch_size") or ""),
                metric,
            )
            grouped.setdefault(key, []).append(value)

    summary_rows: list[dict[str, Any]] = []
    for (run_id_value, source, scenario, batch_size, metric), values in sorted(grouped.items()):
        if not values:
            continue
        summary_rows.append(
            {
                "run_id": run_id_value,
                "source": source,
                "scenario": scenario,
                "batch_size": batch_size,
                "metric": metric,
                "count": len(values),
                "avg": rounded(statistics.mean(values)),
                "median": rounded(statistics.median(values)),
                "p95": rounded(percentile(values, 0.95)),
                "min": rounded(min(values)),
                "max": rounded(max(values)),
            }
        )
    return summary_rows


def aggregate_csv(args: argparse.Namespace) -> None:
    metrics = parse_csv_list(args.metrics, DEFAULT_AGGREGATE_METRICS)
    summary_rows = aggregate_rows(args.input, metrics, args.run_id)
    write_summary_rows(args.output, summary_rows, overwrite=True)
    print(f"summary_rows={len(summary_rows)}")
    print(f"wrote={args.output}")


def report_md(args: argparse.Namespace) -> None:
    metrics = parse_csv_list(args.metrics, DEFAULT_AGGREGATE_METRICS)
    write_latency_report(args.input, args.output, metrics, args.run_id)
    print(f"wrote={args.output}")


def write_current_run_summary(csv_path: Path, summary_output: Path, current_run_id: str) -> None:
    summary_rows = aggregate_rows(csv_path, DEFAULT_AGGREGATE_METRICS, current_run_id)
    write_summary_rows(summary_output, summary_rows, overwrite=True)


def write_latency_report(
    sample_csv_path: Path,
    report_output: Path,
    metrics: list[str],
    run_id_filter: str = "",
) -> None:
    summary_rows = aggregate_rows(sample_csv_path, metrics, run_id_filter)
    by_metric: dict[str, dict[tuple[str, str], dict[str, Any]]] = {}
    scenarios: list[str] = []
    batch_sizes: list[str] = []

    for row in summary_rows:
        metric = str(row.get("metric") or "")
        scenario = str(row.get("scenario") or "")
        batch_size = str(row.get("batch_size") or "")
        if scenario and scenario not in scenarios:
            scenarios.append(scenario)
        if batch_size and batch_size not in batch_sizes:
            batch_sizes.append(batch_size)
        by_metric.setdefault(metric, {})[(scenario, batch_size)] = row

    scenario_order = [scenario for scenario in DEFAULT_MATRIX_SCENARIOS if scenario in scenarios]
    scenario_order.extend(sorted(set(scenarios) - set(scenario_order)))
    batch_order = sorted(batch_sizes, key=lambda item: number(item))

    lines = [
        "# Chungmaru Latency Report",
        "",
        f"- Generated: {now_iso()}",
        f"- Source CSV: `{sample_csv_path}`",
    ]
    if run_id_filter:
        lines.append(f"- Run ID: `{run_id_filter}`")
    lines.extend(["- Cell format: `avg / p95 ms`", ""])

    for metric in metrics:
        metric_rows = by_metric.get(metric, {})
        if not metric_rows:
            continue
        lines.extend(
            [
                f"## {metric}",
                "",
                "| Scenario | " + " | ".join(batch_order) + " |",
                "| --- | " + " | ".join("---" for _ in batch_order) + " |",
            ]
        )
        for scenario in scenario_order:
            cells = []
            for batch_size in batch_order:
                row = metric_rows.get((scenario, batch_size))
                cells.append(f"{row.get('avg')} / {row.get('p95')}" if row else "-")
            lines.append("| " + scenario + " | " + " | ".join(cells) + " |")
        lines.append("")

    outliers = slowest_samples(sample_csv_path, metrics, run_id_filter, limit=12)
    if outliers:
        lines.extend(
            [
                "## Slowest sample candidates",
                "",
                "| Metric | ms | Source | Scenario | Batch | Sample | Likely first check |",
                "| --- | ---: | --- | --- | ---: | ---: | --- |",
            ]
        )
        for row in outliers:
            lines.append(
                "| "
                + " | ".join(
                    [
                        str(row["metric"]),
                        str(row["value"]),
                        str(row["source"]),
                        str(row["scenario"]),
                        str(row["batch_size"]),
                        str(row["sample_id"]),
                        str(row["first_check"]),
                    ]
                )
                + " |"
            )
        lines.append("")

    report_output.parent.mkdir(parents=True, exist_ok=True)
    report_output.write_text("\n".join(lines), encoding="utf-8")


def outlier_first_check(metric: str, row: dict[str, Any]) -> str:
    source = str(row.get("source") or "")
    if metric in {"candidate_collect_ms", "total_to_mask_ms"} and source == "android-pipeline-benchmark":
        return "screen state / accessibility node count"
    if metric in {"candidate_collect_ms", "parser_ms", "pre_backend_ms"}:
        return "DOM candidates / parser noise / dedupe"
    if metric in {"backend_roundtrip_ms", "backend_reported_ms"}:
        return "backend cold path / queue / network reuse"
    if metric in {"backend_internal_avg_ms", "backend_model_avg_ms"}:
        return "model or backend pipeline cost"
    if metric in {"post_backend_to_mask_ms", "mask_apply_ms", "decision_build_ms"}:
        return "span/bounds verification or overlay render"
    if metric in {"first_mask_ms", "total_to_mask_ms"}:
        return "combined visible pipeline"
    return "inspect row notes and artifact"


def slowest_samples(
    sample_csv_path: Path,
    metrics: list[str],
    run_id_filter: str = "",
    limit: int = 12,
) -> list[dict[str, Any]]:
    with sample_csv_path.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))

    candidates: list[dict[str, Any]] = []
    for row in rows:
        if run_id_filter and str(row.get("run_id") or "") != run_id_filter:
            continue
        for metric in metrics:
            value = number(row.get(metric), default=-1)
            if value < 0:
                continue
            candidates.append(
                {
                    "metric": metric,
                    "value": rounded(value),
                    "source": row.get("source") or "",
                    "scenario": row.get("scenario") or "",
                    "batch_size": row.get("batch_size") or "",
                    "sample_id": row.get("sample_id") or "",
                    "first_check": outlier_first_check(metric, row),
                }
            )
    return sorted(candidates, key=lambda item: number(item["value"]), reverse=True)[:limit]


def benchmark_matrix(args: argparse.Namespace) -> None:
    current_run_id = args.run_id or run_id()
    scenarios = parse_csv_list(args.scenarios, DEFAULT_MATRIX_SCENARIOS)
    batch_sizes = parse_int_list(args.batch_sizes, DEFAULT_MATRIX_BATCH_SIZES)
    combinations = [(scenario, batch_size) for scenario in scenarios for batch_size in batch_sizes]
    if not combinations:
        raise SystemExit("No scenario/batch-size combinations to run.")

    if args.samples_per_combo > 0:
        sample_counts = [args.samples_per_combo for _ in combinations]
    else:
        target = max(1, int(args.target_samples))
        base = target // len(combinations)
        remainder = target % len(combinations)
        sample_counts = [base + (1 if index < remainder else 0) for index in range(len(combinations))]

    rows: list[dict[str, Any]] = []
    wall_values: list[float] = []
    sample_id = 0
    combo_count = len(combinations)

    for combo_index, ((scenario, batch_size), samples_for_combo) in enumerate(
        zip(combinations, sample_counts),
        start=1,
    ):
        if samples_for_combo <= 0:
            continue

        for warmup_index in range(max(0, args.warmup)):
            try:
                post_analyze_batch(
                    args.backend,
                    select_texts(batch_size, warmup_index, scenario),
                    args.sensitivity,
                    args.timeout,
                )
            except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError):
                break

        print(
            f"[{combo_index}/{combo_count}] scenario={scenario} "
            f"batch_size={batch_size} samples={samples_for_combo}"
        )

        for local_index in range(samples_for_combo):
            sample_id += 1
            row, wall_ms = measure_backend_sample(
                backend_url=args.backend,
                sensitivity=args.sensitivity,
                timeout_s=args.timeout,
                current_run_id=current_run_id,
                sample_id=sample_id,
                scenario=scenario,
                batch_size=batch_size,
                offset=local_index,
            )
            if wall_ms is not None:
                wall_values.append(wall_ms)
            rows.append(row)

            if args.flush_every and len(rows) >= args.flush_every:
                write_rows(args.output, rows, overwrite=args.overwrite and sample_id == len(rows))
                args.overwrite = False
                rows = []
            if args.sleep_ms > 0:
                time.sleep(args.sleep_ms / 1000)

    if rows:
        write_rows(args.output, rows, overwrite=args.overwrite)

    write_current_run_summary(args.output, args.summary_output, current_run_id)
    write_latency_report(
        args.output,
        args.report_output,
        DEFAULT_AGGREGATE_METRICS,
        current_run_id,
    )

    print(f"wrote={args.output}")
    print(f"summary={args.summary_output}")
    print(f"report={args.report_output}")
    print(f"run_id={current_run_id}")
    print(f"samples={sample_id}")
    if wall_values:
        print(
            "backend_roundtrip_ms "
            f"avg={statistics.mean(wall_values):.2f} "
            f"median={statistics.median(wall_values):.2f} "
            f"p95={percentile(wall_values, 0.95):.2f} "
            f"min={min(wall_values):.2f} "
            f"max={max(wall_values):.2f}"
        )


def init_csv(args: argparse.Namespace) -> None:
    write_rows(args.output, [], overwrite=args.overwrite)
    print(f"initialized={args.output}")


def summarize_csv(args: argparse.Namespace) -> None:
    with args.input.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))

    groups: dict[tuple[str, str, str], list[float]] = {}
    for row in rows:
        key = (row.get("source", ""), row.get("scenario", ""), row.get("batch_size", ""))
        value = number(row.get(args.metric), default=-1)
        if value >= 0:
            groups.setdefault(key, []).append(value)

    for key, values in sorted(groups.items()):
        if not values:
            continue
        print(
            ",".join(key)
            + f",count={len(values)},avg={statistics.mean(values):.3f},"
            + f"median={statistics.median(values):.3f},p95={percentile(values, 0.95):.3f},"
            + f"min={min(values):.3f},max={max(values):.3f}"
        )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Collect Chungmaru latency samples into one CSV schema.")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--overwrite", action="store_true")

    subparsers = parser.add_subparsers(dest="command", required=True)

    init = subparsers.add_parser("init", help="create an empty CSV with the canonical header")
    init.set_defaults(func=init_csv)

    backend = subparsers.add_parser("backend", help="append direct /analyze_batch benchmark samples")
    backend.add_argument("--backend", default="http://127.0.0.1:8000")
    backend.add_argument("--samples", type=int, default=1000)
    backend.add_argument("--batch-size", type=int, default=16)
    backend.add_argument("--sensitivity", type=int, default=60)
    backend.add_argument("--timeout", type=float, default=30.0)
    backend.add_argument("--warmup", type=int, default=1)
    backend.add_argument("--sleep-ms", type=float, default=0.0)
    backend.add_argument("--flush-every", type=int, default=100)
    backend.add_argument("--scenario", default="mixed-fixture")
    backend.add_argument("--run-id", default="")
    backend.set_defaults(func=benchmark_backend)

    matrix = subparsers.add_parser("matrix", help="append a scenario x batch-size latency matrix")
    matrix.add_argument("--backend", default="http://127.0.0.1:8000")
    matrix.add_argument("--target-samples", type=int, default=1000)
    matrix.add_argument("--samples-per-combo", type=int, default=0)
    matrix.add_argument("--batch-sizes", default=",".join(str(value) for value in DEFAULT_MATRIX_BATCH_SIZES))
    matrix.add_argument("--scenarios", default=",".join(DEFAULT_MATRIX_SCENARIOS))
    matrix.add_argument("--sensitivity", type=int, default=60)
    matrix.add_argument("--timeout", type=float, default=30.0)
    matrix.add_argument("--warmup", type=int, default=1)
    matrix.add_argument("--sleep-ms", type=float, default=0.0)
    matrix.add_argument("--flush-every", type=int, default=100)
    matrix.add_argument("--summary-output", type=Path, default=DEFAULT_SUMMARY_OUTPUT)
    matrix.add_argument("--report-output", type=Path, default=DEFAULT_REPORT_OUTPUT)
    matrix.add_argument("--run-id", default="")
    matrix.set_defaults(func=benchmark_matrix)

    extension = subparsers.add_parser("extension-export", help="append exported extension lastStats JSON/JSONL")
    extension.add_argument("--input", type=Path, required=True)
    extension.add_argument("--scenario", default="browser-smoke")
    extension.add_argument("--platform", default="chrome")
    extension.add_argument("--run-id", default="")
    extension.set_defaults(func=import_extension_export)

    quick_qa = subparsers.add_parser(
        "chrome-quick-qa-import",
        help="append Chrome quick QA CSV rows into the canonical latency samples CSV",
    )
    quick_qa.add_argument("--input", type=Path, default=DEFAULT_RESULTS_DIR / "chrome-quick-qa.csv")
    quick_qa.add_argument("--platform", default="chrome-google")
    quick_qa.add_argument("--backend", default="http://127.0.0.1:8000")
    quick_qa.add_argument("--measured-at", default="")
    quick_qa.add_argument("--run-id", default="")
    quick_qa.set_defaults(func=import_chrome_quick_qa)

    android_pipeline = subparsers.add_parser(
        "android-pipeline-import",
        help="append Android pipeline benchmark raw_runs.csv",
    )
    android_pipeline.add_argument("--input", type=Path, required=True)
    android_pipeline.add_argument("--scenario", default="")
    android_pipeline.add_argument("--platform", default="android")
    android_pipeline.add_argument("--backend", default="http://10.0.2.2:8000")
    android_pipeline.add_argument("--measured-at", default="")
    android_pipeline.add_argument("--run-id", default="")
    android_pipeline.set_defaults(func=import_android_pipeline)

    pipeline = subparsers.add_parser(
        "pipeline-csv",
        help="write a compact stage-by-stage pipeline latency CSV",
    )
    pipeline.add_argument("--input", type=Path, default=DEFAULT_OUTPUT)
    pipeline.add_argument("--output", type=Path, default=DEFAULT_PIPELINE_OUTPUT)
    pipeline.add_argument("--run-id", default="")
    pipeline.add_argument("--source", default="")
    pipeline.add_argument("--include-control", action="store_false", dest="exclude_control")
    pipeline.set_defaults(exclude_control=True)
    pipeline.set_defaults(func=export_pipeline_csv)

    summary = subparsers.add_parser("summary", help="print grouped averages from a CSV")
    summary.add_argument("--input", type=Path, default=DEFAULT_OUTPUT)
    summary.add_argument("--metric", default="backend_roundtrip_ms")
    summary.set_defaults(func=summarize_csv)

    aggregate = subparsers.add_parser("aggregate", help="write grouped summary CSV")
    aggregate.add_argument("--input", type=Path, default=DEFAULT_OUTPUT)
    aggregate.add_argument("--output", type=Path, default=DEFAULT_SUMMARY_OUTPUT)
    aggregate.add_argument("--metrics", default=",".join(DEFAULT_AGGREGATE_METRICS))
    aggregate.add_argument("--run-id", default="")
    aggregate.set_defaults(func=aggregate_csv)

    report = subparsers.add_parser("report-md", help="write a human-readable latency report")
    report.add_argument("--input", type=Path, default=DEFAULT_OUTPUT)
    report.add_argument("--output", type=Path, default=DEFAULT_REPORT_OUTPUT)
    report.add_argument("--metrics", default=",".join(DEFAULT_AGGREGATE_METRICS))
    report.add_argument("--run-id", default="")
    report.set_defaults(func=report_md)

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    args.func(args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
