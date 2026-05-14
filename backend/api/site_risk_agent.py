"""
임베딩 DB + LLM 기반 사이트 위험 판별 Agent

1. URL/도메인 신호를 정리
2. 임베딩 DB에서 관련 사이트 인텔을 검색
3. LLM이 검색 결과와 규칙 신호를 바탕으로 최종 verdict를 판단
4. LLM이 없으면 규칙 기반 fallback verdict를 사용
"""
from __future__ import annotations

import time
from typing import Any
from urllib.parse import urlparse

from site_intel_store import SiteIntelStore
from site_llm_agent import SiteRiskLLMAgent


RISK_KEYWORD_GROUPS = {
    "adult": [
        "porn", "adult", "sex", "cam", "escort", "hentai", "xvideo", "redtube", "xnxx", "jav"
    ],
    "gambling": [
        "casino", "bet", "betting", "slot", "poker", "gamble", "sportsbook", "toto", "바카라", "카지노", "토토"
    ],
    "malware": [
        "crack", "keygen", "warez", "hacktool", "exploit", "malware", "trojan", "ransomware",
        "stealer", "botnet", "loader", "backdoor"
    ],
    "phishing": [
        "verify-account", "account-verify", "wallet-connect", "airdrop", "gift-card", "free-gift",
        "bank-secure", "security-check", "suspended-account", "password-reset", "login-verify",
        "otp-verify", "본인확인", "계정복구", "인증확인"
    ],
    "piracy": [
        "torrent", "magnet", "streamingfree", "watchfree", "pirate", "downloadfree", "apkmod", "modapk"
    ],
}

SAFE_HIGH_TRUST_DOMAINS = {
    "google.com",
    "naver.com",
    "daum.net",
    "youtube.com",
    "github.com",
    "wikipedia.org",
    "docs.python.org",
    "openai.com",
}

VERDICT_ORDER = {"allow": 0, "warning": 1, "block": 2}


def _normalize_domain(value: str) -> str:
    domain = (value or "").strip().lower()
    if domain.startswith("www."):
        domain = domain[4:]
    return domain


def _matches_domain_or_parent(domain: str, known_root: str) -> bool:
    d = _normalize_domain(domain)
    k = _normalize_domain(known_root)
    return bool(d and k and (d == k or d.endswith("." + k)))


def _parse_url(url: str) -> tuple[str, str, str]:
    try:
        parsed = urlparse(str(url or "").strip())
    except Exception:
        return "", "", ""
    domain = _normalize_domain(parsed.hostname or "")
    path = (parsed.path or "").strip().lower()
    scheme = (parsed.scheme or "").strip().lower()
    return domain, path, scheme


def _contains_any(text: str, keywords: list[str]) -> bool:
    lower = (text or "").lower()
    return any(keyword in lower for keyword in keywords)


def _category_from_matches(text: str) -> str:
    for category, keywords in RISK_KEYWORD_GROUPS.items():
        if _contains_any(text, keywords):
            return category
    return "unknown"


def _merge_verdict(current: str, incoming: str) -> str:
    current_key = str(current or "allow").lower()
    incoming_key = str(incoming or "allow").lower()
    return incoming_key if VERDICT_ORDER.get(incoming_key, 0) > VERDICT_ORDER.get(current_key, 0) else current_key


class SiteRiskAgent:
    def __init__(self):
        self.store = SiteIntelStore()
        self.llm_agent = SiteRiskLLMAgent()

    def check_site(self, url: str, title: str = "", snippet: str = "", force_refresh: bool = False) -> dict[str, Any]:
        started = time.perf_counter()
        domain, path, scheme = _parse_url(url)

        retrieval_started = time.perf_counter()
        exact_entry = self.store.get_domain_entry(domain)
        similar_entries = self.store.search_similar(url, title=title, snippet=snippet, limit=12)
        similar_entries = self._refine_similar_entries(domain, exact_entry, similar_entries)
        retrieval_ms = (time.perf_counter() - retrieval_started) * 1000

        heuristic = self._build_heuristic(url, domain, path, scheme, title, snippet, exact_entry, similar_entries)
        llm_entries = similar_entries[:5]
        if exact_entry or (
            heuristic.get("verdict") == "allow"
            and not any(float(item.get("similarity", 0.0)) >= 0.7 for item in similar_entries)
        ):
            llm_entries = []
        llm_payload = {
            "target": {
                "url": url,
                "domain": domain,
                "title": title,
                "snippet": snippet,
            },
            "heuristic": heuristic,
            "retrieved_entries": [self._serialize_match(item) for item in llm_entries],
            "exact_match": self._serialize_match(exact_entry) if exact_entry else None,
        }

        llm_started = time.perf_counter()
        llm_output = self.llm_agent.judge(llm_payload)
        llm_ms = (time.perf_counter() - llm_started) * 1000

        final_result = dict(llm_output["result"])
        final_result = self._stabilize_result(final_result, heuristic)
        visible_matches = [] if exact_entry else [self._serialize_match(item) for item in similar_entries[:5]]
        final_result.update(
            {
                "url": url,
                "domain": domain,
                "matched_entries": visible_matches,
                "exact_match": self._serialize_match(exact_entry) if exact_entry else None,
                "retrieval_ms": round(retrieval_ms, 3),
                "llm_timing_ms": round(llm_ms, 3),
                "timing_ms": round((time.perf_counter() - started) * 1000, 3),
                "agent": {
                    "mode": llm_output["mode"],
                    "model": llm_output["model"],
                    "reason": llm_output["reason"],
                    "response": final_result["explanation"],
                },
                "force_refresh": bool(force_refresh),
            }
        )
        return final_result

    def _refine_similar_entries(
        self,
        domain: str,
        exact_entry: dict[str, Any] | None,
        similar_entries: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        refined: list[dict[str, Any]] = []
        trusted_allow_exact = bool(
            exact_entry
            and str(exact_entry.get("risk_level") or "allow").lower() == "allow"
            and any(_matches_domain_or_parent(domain, root) for root in SAFE_HIGH_TRUST_DOMAINS)
        )
        exact_category = str(exact_entry.get("category") or "") if exact_entry else ""

        for entry in similar_entries:
            entry_domain = _normalize_domain(entry.get("domain") or "")
            similarity = float(entry.get("similarity", 0.0))
            if not entry_domain or entry_domain == domain:
                continue

            if trusted_allow_exact:
                if similarity < 0.82:
                    continue
                if exact_category and str(entry.get("category") or "") != exact_category:
                    continue
                if str(entry.get("risk_level") or "allow").lower() != "allow":
                    continue
            elif exact_entry:
                if similarity < 0.58:
                    continue
            else:
                if similarity < 0.32:
                    continue

            matched_chunks = []
            for chunk in entry.get("matched_chunks", []):
                chunk_text = str(chunk.get("chunk_text") or "")
                chunk_similarity = float(chunk.get("similarity", 0.0))
                if chunk_similarity < 0.28:
                    continue
                if chunk_text.startswith("위험 유형:") and chunk_similarity < 0.65:
                    continue
                matched_chunks.append(chunk)
            if not matched_chunks and similarity < 0.72:
                continue

            cloned = dict(entry)
            cloned["matched_chunks"] = matched_chunks[:3]
            refined.append(cloned)

        refined.sort(key=lambda item: item.get("similarity", 0.0), reverse=True)
        return refined[:5]

    def _build_heuristic(
        self,
        url: str,
        domain: str,
        path: str,
        scheme: str,
        title: str,
        snippet: str,
        exact_entry: dict[str, Any] | None,
        similar_entries: list[dict[str, Any]],
    ) -> dict[str, Any]:
        reasons: list[str] = []
        text_blob = " ".join(part for part in [domain, path, title, snippet] if part).lower()
        site_category = "unknown"
        verdict = "allow"
        risk_score = 0.06
        harmful_content = False
        security_threat = False

        if not domain:
            verdict = "warning"
            risk_score = max(risk_score, 0.45)
            reasons.append("도메인을 정상적으로 식별하지 못해 출처 검증이 어렵다.")

        if scheme and scheme not in {"http", "https"}:
            verdict = "warning"
            risk_score = max(risk_score, 0.52)
            reasons.append(f"웹 표준 접속이 아닌 '{scheme}' 스킴을 사용하고 있다.")

        if scheme == "http":
            verdict = _merge_verdict(verdict, "warning")
            risk_score = max(risk_score, 0.34)
            reasons.append("HTTPS가 아닌 HTTP 연결이라 전송 구간 보호 수준이 낮을 수 있다.")

        if any(_matches_domain_or_parent(domain, root) for root in SAFE_HIGH_TRUST_DOMAINS):
            risk_score = min(risk_score, 0.08)
            reasons.append("널리 알려진 고신뢰 서비스 도메인과 일치한다.")

        matched_category = _category_from_matches(text_blob)
        if matched_category != "unknown":
            site_category = matched_category
            harmful_content = matched_category in {"adult", "gambling", "piracy"}
            security_threat = matched_category in {"malware", "phishing"}
            if security_threat:
                verdict = "block"
                risk_score = max(risk_score, 0.96)
            elif harmful_content:
                verdict = "block" if matched_category in {"adult", "gambling"} else "warning"
                risk_score = max(risk_score, 0.78 if verdict == "warning" else 0.9)
            reasons.append(f"URL 또는 메타데이터에 {matched_category} 계열 위험 키워드가 포함되어 있다.")

        if _matches_domain_or_parent(domain, "dcinside.com"):
            site_category = "community"
            verdict = _merge_verdict(verdict, "warning")
            risk_score = max(risk_score, 0.38)
            reasons.append("커뮤니티형 사이트로 게시판별 유해성 편차가 커 사전 주의가 필요하다.")

        if exact_entry:
            if site_category == "unknown":
                site_category = exact_entry.get("category") or site_category
            security_threat = security_threat or bool(exact_entry.get("security_threat"))
            harmful_content = harmful_content or bool(exact_entry.get("harmful_content"))
            verdict = _merge_verdict(verdict, exact_entry.get("risk_level", "allow"))
            risk_score = max(risk_score, self._score_from_risk_level(exact_entry.get("risk_level")))
            reasons.append(
                f"임베딩 DB에 저장된 동일 도메인 인텔에서 '{exact_entry.get('category')}' / '{exact_entry.get('risk_level')}'로 분류되어 있다."
            )

        trusted_allow_exact = bool(
            exact_entry
            and str(exact_entry.get("risk_level") or "allow").lower() == "allow"
            and any(_matches_domain_or_parent(domain, root) for root in SAFE_HIGH_TRUST_DOMAINS)
        )

        for entry in similar_entries[:3]:
            similarity = float(entry.get("similarity", 0.0))
            if similarity < 0.24:
                continue
            if exact_entry and entry.get("domain") != domain and similarity < 0.9:
                continue
            if entry.get("domain") == domain and entry.get("risk_level") == "allow":
                continue
            if trusted_allow_exact and similarity < 0.82:
                continue
            if not exact_entry and site_category == "unknown":
                site_category = entry.get("category") or site_category
            if similarity >= 0.82:
                security_threat = security_threat or bool(entry.get("security_threat"))
                harmful_content = harmful_content or bool(entry.get("harmful_content"))
            if similarity >= 0.82:
                verdict = _merge_verdict(verdict, entry.get("risk_level", "warning"))
                risk_score = max(risk_score, min(0.99, 0.2 + similarity * 0.85))
                reasons.append(
                    f"임베딩 DB에서 '{entry.get('domain')}'와 높은 유사도({similarity:.2f})가 관측되어 유사 범주 가능성이 높다."
                )
            elif similarity >= 0.58 and str(entry.get("risk_level") or "allow").lower() != "allow":
                verdict = _merge_verdict(verdict, "warning")
                risk_score = max(risk_score, 0.48)
                reasons.append(
                    f"임베딩 DB에서 '{entry.get('domain')}'와 중간 이상 유사도({similarity:.2f})가 확인되었다."
                )

        if security_threat:
            verdict = "block"
            risk_score = max(risk_score, 0.95)
        elif harmful_content:
            verdict = _merge_verdict(verdict, "warning")
            risk_score = max(risk_score, 0.6)

        if verdict == "allow" and not reasons:
            reasons.append("위험 키워드나 저장된 유사 위험 사이트 근거가 뚜렷하게 관측되지 않았다.")

        return {
            "verdict": verdict,
            "risk_score": round(max(0.0, min(1.0, risk_score)), 4),
            "site_category": site_category,
            "harmful_content": bool(harmful_content),
            "security_threat": bool(security_threat),
            "reasons": reasons[:8],
        }

    def _stabilize_result(self, llm_result: dict[str, Any], heuristic: dict[str, Any]) -> dict[str, Any]:
        final_verdict = _merge_verdict(llm_result.get("verdict"), heuristic.get("verdict"))
        if heuristic.get("security_threat"):
            final_verdict = "block"

        return {
            "verdict": final_verdict,
            "risk_score": round(max(float(heuristic["risk_score"]), float(llm_result.get("risk_score", 0.0))), 4),
            "site_category": str(llm_result.get("site_category") or heuristic.get("site_category") or "unknown"),
            "harmful_content": bool(llm_result.get("harmful_content") or heuristic.get("harmful_content")),
            "security_threat": bool(llm_result.get("security_threat") or heuristic.get("security_threat")),
            "reasons": list(dict.fromkeys((llm_result.get("reasons") or []) + heuristic.get("reasons", [])))[:8],
            "explanation": str(llm_result.get("explanation") or "").strip(),
        }

    def _serialize_match(self, entry: dict[str, Any] | None) -> dict[str, Any] | None:
        if not entry:
            return None
        return {
            "domain": entry.get("domain"),
            "title": entry.get("title"),
            "summary": entry.get("summary"),
            "category": entry.get("category"),
            "risk_level": entry.get("risk_level"),
            "security_threat": bool(entry.get("security_threat")),
            "harmful_content": bool(entry.get("harmful_content")),
            "similarity": round(float(entry.get("similarity", 1.0)), 4)
            if entry.get("similarity") is not None
            else None,
            "source": entry.get("source"),
            "region": entry.get("region"),
            "language": entry.get("language"),
            "tags": entry.get("tags", []),
            "aliases": entry.get("aliases", []),
            "indicators": entry.get("indicators", []),
            "risk_types": entry.get("risk_types", []),
            "matched_chunks": entry.get("matched_chunks", []),
        }

    @staticmethod
    def _score_from_risk_level(value: Any) -> float:
        risk_level = str(value or "allow").lower()
        if risk_level == "block":
            return 0.94
        if risk_level == "warning":
            return 0.58
        return 0.08
