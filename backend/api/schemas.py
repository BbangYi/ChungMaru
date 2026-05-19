"""
FastAPI 요청/응답 스키마와 응답 변환 헬퍼.

- app.py에서 Pydantic 정의가 과도하게 비대해지는 것을 막는다.
- 텍스트 분석 축과 사이트 안전성 축의 계약을 한 곳에서 관리한다.
"""
from __future__ import annotations

from pydantic import BaseModel


class AnalyzeRequest(BaseModel):
    text: str
    sensitivity: int | None = None


class AnalyzeBatchRequest(BaseModel):
    texts: list[str]
    sensitivity: int | None = None


class BoundsInScreen(BaseModel):
    top: int
    bottom: int
    left: int
    right: int


class AndroidComment(BaseModel):
    commentText: str
    boundsInScreen: BoundsInScreen
    author_id: str | None = None


class AndroidRequest(BaseModel):
    timestamp: int
    comments: list[AndroidComment]
    sensitivity: int | None = None


class EvidenceSpan(BaseModel):
    text: str
    start: int
    end: int
    score: float


class AnalyzeResponse(BaseModel):
    original: str
    is_offensive: bool
    is_profane: bool
    is_toxic: bool
    is_hate: bool
    scores: dict[str, float]
    evidence_spans: list[EvidenceSpan]
    timing_ms: float | None = None
    model_timing_ms: float | None = None
    llm_timing_ms: float | None = None


class AnalyzeBatchResponse(BaseModel):
    results: list[AnalyzeResponse]


class AgentResponse(BaseModel):
    mode: str
    model: str | None = None
    reason: str | None = None
    response: str
    sub_agents: dict[str, str] | None = None


class AndroidResultItem(BaseModel):
    original: str
    boundsInScreen: BoundsInScreen
    author_id: str | None = None
    is_offensive: bool
    is_profane: bool
    is_toxic: bool
    is_hate: bool
    scores: dict[str, float]
    evidence_spans: list[EvidenceSpan]


class AndroidResponse(BaseModel):
    timestamp: int
    filtered_count: int
    results: list[AndroidResultItem]


class AgentAnalyzeResponse(BaseModel):
    analysis: AnalyzeResponse
    agent: AgentResponse
    timing_ms: float | None = None
    model_timing_ms: float | None = None
    llm_timing_ms: float | None = None


class SiteCheckRequest(BaseModel):
    url: str
    title: str | None = None
    snippet: str | None = None
    force_refresh: bool | None = False


class SiteMatchItem(BaseModel):
    domain: str
    title: str | None = None
    summary: str | None = None
    category: str
    risk_level: str
    security_threat: bool
    harmful_content: bool
    similarity: float | None = None
    source: str | None = None
    tags: list[str] = []
    aliases: list[str] = []
    indicators: list[str] = []
    risk_types: list[str] = []
    region: str | None = None
    language: str | None = None
    matched_chunks: list[dict[str, float | int | str]] = []


class SiteCheckResponse(BaseModel):
    url: str
    domain: str
    verdict: str
    risk_score: float
    site_category: str
    security_threat: bool
    harmful_content: bool
    reasons: list[str]
    matched_entries: list[SiteMatchItem]
    exact_match: SiteMatchItem | None = None
    retrieval_ms: float | None = None
    llm_timing_ms: float | None = None
    timing_ms: float | None = None
    agent: AgentResponse


def format_analysis_result(
    analysis: dict,
    timing_ms: float | None = None,
    model_timing_ms: float | None = None,
    llm_timing_ms: float | None = None,
) -> dict:
    """pipeline.analyze() 결과를 API 응답 형식으로 변환."""
    timing = analysis.get("_timing", {})
    return {
        "original": analysis["text"],
        "is_offensive": analysis["is_offensive"],
        "is_profane": analysis["is_profane"],
        "is_toxic": analysis["is_toxic"],
        "is_hate": analysis["is_hate"],
        "scores": analysis["scores"],
        "evidence_spans": [
            {
                "text": s["text"],
                "start": s.get("start", -1),
                "end": s.get("end", -1),
                "score": s["score"],
            }
            for s in analysis["evidence_spans"]
        ],
        "timing_ms": round(timing_ms, 3) if timing_ms is not None else timing.get("pipeline_ms"),
        "model_timing_ms": round(model_timing_ms, 3) if model_timing_ms is not None else timing.get("model_ms"),
        "llm_timing_ms": round(llm_timing_ms, 3) if llm_timing_ms is not None else None,
    }
