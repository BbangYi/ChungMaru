"""
검색 증거 기반 사이트 판별 LLM Agent

- 입력:
  - URL/도메인
  - 규칙 기반 신호
  - 임베딩 검색 결과
- 출력:
  - verdict / risk_score / category / reasons / explanation
"""
from __future__ import annotations

import json
import os
from typing import Any

from env_loader import load_local_env


SYSTEM_PROMPT = (
    "너는 사이트 접속 전 위험도를 판별하는 보안 심사 Agent다. "
    "입력으로 제공되는 URL, 규칙 기반 위험 신호, 임베딩 검색으로 회수된 사이트 인텔만 근거로 사용한다. "
    "절대 근거 없이 새로운 사실을 만들어내지 말고, 불확실하면 warning 쪽으로 보수적으로 판단한다. "
    "반드시 JSON만 출력한다. 키는 verdict, risk_score, site_category, harmful_content, security_threat, reasons, explanation 이다. "
    "verdict는 allow, warning, block 중 하나여야 한다. "
    "reasons는 한국어 문자열 배열이며 2개 이상 권장한다. "
    "explanation은 사용자 안내 문구이며 3문장 이하로 작성한다."
)


def _compact_match(entry: dict[str, Any] | None) -> dict[str, Any] | None:
    if not entry:
        return None
    compact = {
        "domain": entry.get("domain"),
        "title": entry.get("title"),
        "summary": entry.get("summary"),
        "category": entry.get("category"),
        "risk_level": entry.get("risk_level"),
        "security_threat": bool(entry.get("security_threat")),
        "harmful_content": bool(entry.get("harmful_content")),
        "source": entry.get("source"),
        "region": entry.get("region"),
        "language": entry.get("language"),
        "risk_types": entry.get("risk_types", []),
        "similarity": entry.get("similarity"),
    }
    return compact


def _compact_payload(payload: dict[str, Any]) -> dict[str, Any]:
    return {
        "target": payload.get("target", {}),
        "heuristic": payload.get("heuristic", {}),
        "exact_match": _compact_match(payload.get("exact_match")),
        "retrieved_entries": [
            _compact_match(item)
            for item in (payload.get("retrieved_entries") or [])[:2]
            if _compact_match(item)
        ],
    }


def _fallback(payload: dict[str, Any], reason: str) -> dict[str, Any]:
    heuristic = payload["heuristic"]
    result = {
        "verdict": heuristic["verdict"],
        "risk_score": heuristic["risk_score"],
        "site_category": heuristic["site_category"],
        "harmful_content": heuristic["harmful_content"],
        "security_threat": heuristic["security_threat"],
        "reasons": heuristic["reasons"][:6],
        "explanation": (
            "입력된 주소와 저장된 사이트 인텔을 기준으로 위험도를 추정했습니다. "
            "로그인, 결제, 파일 다운로드가 필요한 경우에는 주소와 출처를 다시 확인하는 것이 좋습니다."
        ),
    }
    return {
        "mode": "fallback",
        "model": None,
        "reason": reason,
        "result": result,
    }


def _coerce_bool(value: Any) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return bool(value)
    return str(value).strip().lower() in {"true", "1", "yes", "y"}


def _normalize_verdict(value: Any) -> str:
    verdict = str(value or "warning").strip().lower()
    if verdict not in {"allow", "warning", "block"}:
        return "warning"
    return verdict


class SiteRiskLLMAgent:
    def __init__(self):
        load_local_env()
        self.enabled = False
        self.reason = ""
        self.model_name = os.environ.get("OPENAI_MODEL", "gpt-4o-mini")
        self.timeout_seconds = float(os.environ.get("OPENAI_TIMEOUT_SECONDS", "12"))
        self.client = None
        self._try_init()

    def _try_init(self) -> None:
        if not os.environ.get("OPENAI_API_KEY"):
            self.reason = "OPENAI_API_KEY가 설정되지 않음"
            return

        try:
            from openai import OpenAI
        except Exception as exc:
            self.reason = f"openai import 실패: {exc}"
            return

        self.client = OpenAI(timeout=self.timeout_seconds, max_retries=0)
        self.enabled = True
        self.reason = ""

    def judge(self, payload: dict[str, Any]) -> dict[str, Any]:
        if not self.enabled or self.client is None:
            return _fallback(payload, self.reason or "LLM 비활성화")

        try:
            compact_payload = _compact_payload(payload)
            response = self.client.chat.completions.create(
                model=self.model_name,
                temperature=0,
                max_tokens=220,
                response_format={"type": "json_object"},
                messages=[
                    {"role": "system", "content": SYSTEM_PROMPT},
                    {"role": "user", "content": json.dumps(compact_payload, ensure_ascii=False, indent=2)},
                ],
            )
            text = ""
            if getattr(response, "choices", None):
                text = response.choices[0].message.content or ""
            if not text.strip():
                return _fallback(payload, "LLM 응답이 비어 있음")
            data = json.loads(text)
            result = {
                "verdict": _normalize_verdict(data.get("verdict")),
                "risk_score": round(max(0.0, min(1.0, float(data.get("risk_score", 0.5)))), 4),
                "site_category": str(data.get("site_category") or payload["heuristic"]["site_category"] or "unknown"),
                "harmful_content": _coerce_bool(data.get("harmful_content")),
                "security_threat": _coerce_bool(data.get("security_threat")),
                "reasons": [str(item) for item in (data.get("reasons") or []) if str(item).strip()][:6],
                "explanation": str(data.get("explanation") or "").strip(),
            }
            if not result["reasons"]:
                result["reasons"] = payload["heuristic"]["reasons"][:6]
            if not result["explanation"]:
                result["explanation"] = (
                    "검색된 사이트 인텔과 URL 신호를 기준으로 위험도를 판별했습니다. "
                    "개인정보 입력이나 파일 다운로드 전 주소와 출처를 다시 확인하세요."
                )
            return {
                "mode": "judge",
                "model": self.model_name,
                "reason": None,
                "result": result,
            }
        except Exception as exc:
            return _fallback(payload, f"LLM 호출 실패({type(exc).__name__}): {exc}")
