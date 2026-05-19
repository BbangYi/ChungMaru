"""
사이트 안전성 Agent 서비스 레이어.

- FastAPI 엔드포인트는 이 레이어만 호출한다.
- 향후 캐시, 정책, 로깅, Android/WebView 연동 분기를 이곳에 모을 수 있다.
"""
from __future__ import annotations

from site_risk_agent import SiteRiskAgent


class SiteRiskService:
    def __init__(self):
        self.agent = SiteRiskAgent()

    def health_summary(self) -> dict:
        return self.agent.store.stats()

    def check(self, url: str, title: str = "", snippet: str = "", force_refresh: bool = False) -> dict:
        return self.agent.check_site(
            url,
            title=title,
            snippet=snippet,
            force_refresh=bool(force_refresh),
        )
