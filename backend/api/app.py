"""
FastAPI 백엔드 — 혐오 표현 탐지 API

엔드포인트:
  POST /analyze              Chrome Extension 단일 텍스트
  POST /analyze_batch        Chrome Extension 배치
  POST /analyze_android      Android App 배치 (boundsInScreen 포함)
  GET  /health               헬스체크
"""

from contextlib import asynccontextmanager
from fastapi import FastAPI
from pydantic import BaseModel

from pipeline import ProfanityPipeline


# ── Pydantic 모델 ──────────────────────────────────────────

class AnalyzeRequest(BaseModel):
    text: str


class AnalyzeBatchRequest(BaseModel):
    texts: list[str]


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


class AnalyzeBatchResponse(BaseModel):
    results: list[AnalyzeResponse]


class AndroidResultItem(BaseModel):
    original: str
    boundsInScreen: BoundsInScreen
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


# ── 파이프라인 싱글톤 ──────────────────────────────────────

pipeline: ProfanityPipeline | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global pipeline
    pipeline = ProfanityPipeline()
    print("파이프라인 초기화 완료")
    yield
    print("서버 종료")


app = FastAPI(
    title="Offensive Language Detection API",
    version="1.0.0",
    lifespan=lifespan,
)


# ── 헬퍼 ───────────────────────────────────────────────────

def _format_result(analysis: dict) -> dict:
    """pipeline.analyze() 결과를 API 응답 형식으로 변환."""
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
    }


# ── 엔드포인트 ─────────────────────────────────────────────

@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/analyze", response_model=AnalyzeResponse)
async def analyze(req: AnalyzeRequest):
    """Chrome Extension — 단일 텍스트 분석."""
    result = pipeline.analyze(req.text)
    return _format_result(result)


@app.post("/analyze_batch", response_model=AnalyzeBatchResponse)
async def analyze_batch(req: AnalyzeBatchRequest):
    """Chrome Extension — 배치 텍스트 분석."""
    results = pipeline.analyze_batch(req.texts)
    return {"results": [_format_result(r) for r in results]}


@app.post("/analyze_android", response_model=AndroidResponse)
async def analyze_android(req: AndroidRequest):
    """Android App — 배치 분석 (boundsInScreen 보존)."""
    raw = req.model_dump()
    result = pipeline.analyze_android_batch(raw)
    return result


# ── 실행 ───────────────────────────────────────────────────

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app:app", host="0.0.0.0", port=8000, reload=True)
