"""
사이트 인텔 임베딩 DB

- SQLite를 로컬 벡터 저장소처럼 사용한다.
- `site_intel`에는 사이트 단위 메타데이터를 저장한다.
- `site_embeddings`에는 사이트 설명 청크별 임베딩을 저장한다.
- 검색은 chunk 유사도 기반으로 수행한 뒤 domain 단위로 집계한다.
"""
from __future__ import annotations

import hashlib
import json
import math
import os
import sqlite3
import time
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


EMBED_DIM = 64
PROJECT_ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = PROJECT_ROOT / "data"
DB_PATH = Path(os.environ.get("SITE_INTEL_DB_PATH", DATA_DIR / "site_intel.sqlite"))
SEED_PATH = DATA_DIR / "site_intel_seed.json"
SEED_GLOB = "site_intel_seed*.json"


def _normalize_domain(value: str) -> str:
    domain = (value or "").strip().lower()
    if domain.startswith("www."):
        domain = domain[4:]
    return domain


def _domain_from_url(url: str) -> str:
    try:
        return _normalize_domain(urlparse(str(url or "")).hostname or "")
    except Exception:
        return ""


def _domain_candidates(domain: str) -> list[str]:
    normalized = _normalize_domain(domain)
    if not normalized:
        return []
    parts = normalized.split(".")
    candidates = []
    for idx in range(len(parts) - 1):
        candidate = ".".join(parts[idx:])
        if candidate and candidate not in candidates:
            candidates.append(candidate)
    return candidates


def _tokenize(text: str) -> list[str]:
    raw = "".join(ch if ch.isalnum() else " " for ch in str(text or "").lower())
    return [token for token in raw.split() if token]


def _embed_text(text: str) -> list[float]:
    vector = [0.0] * EMBED_DIM
    tokens = _tokenize(text)
    if not tokens:
        return vector

    for token in tokens:
        digest = hashlib.sha256(token.encode("utf-8")).digest()
        bucket = int.from_bytes(digest[:2], "big") % EMBED_DIM
        sign = 1.0 if digest[2] % 2 == 0 else -1.0
        weight = 1.0 + (digest[3] / 255.0)
        vector[bucket] += sign * weight

    norm = math.sqrt(sum(value * value for value in vector))
    if norm <= 1e-9:
        return vector
    return [value / norm for value in vector]


def _cosine_similarity(left: list[float], right: list[float]) -> float:
    if not left or not right or len(left) != len(right):
        return 0.0
    return max(-1.0, min(1.0, float(sum(a * b for a, b in zip(left, right)))))


def _chunk_penalty(chunk_text: str) -> float:
    text = str(chunk_text or "").strip().lower()
    if not text:
        return 0.0
    if text.startswith("위험 유형:"):
        return 0.62
    if text.startswith("태그:"):
        return 0.78
    if text.startswith("별칭:"):
        return 0.82
    if text.startswith("위험 신호:"):
        return 0.92
    return 1.0


def _chunk_entry_texts(entry: dict[str, Any]) -> list[str]:
    domain = str(entry.get("domain") or "").strip()
    title = str(entry.get("title") or "").strip()
    summary = str(entry.get("summary") or "").strip()
    category = str(entry.get("category") or "").strip()
    region = str(entry.get("region") or "").strip()
    tags = [str(item).strip() for item in entry.get("tags", []) if str(item).strip()]
    aliases = [str(item).strip() for item in entry.get("aliases", []) if str(item).strip()]
    indicators = [str(item).strip() for item in entry.get("indicators", []) if str(item).strip()]
    risk_types = [str(item).strip() for item in entry.get("risk_types", []) if str(item).strip()]

    chunks = []
    if domain or title:
        chunks.append(" ".join(part for part in [domain, title, category, region] if part))
    if summary:
        chunks.append(summary)
    if tags:
        chunks.append("태그: " + ", ".join(tags))
    if aliases:
        chunks.append("별칭: " + ", ".join(aliases))
    if indicators:
        chunks.append("위험 신호: " + ", ".join(indicators))
    if risk_types:
        chunks.append("위험 유형: " + ", ".join(risk_types))

    merged = []
    seen = set()
    for chunk in chunks:
        normalized = " ".join(chunk.split())
        if normalized and normalized not in seen:
            seen.add(normalized)
            merged.append(normalized)
    return merged


class SiteIntelStore:
    def __init__(self, db_path: Path | str = DB_PATH):
        self.db_path = Path(db_path)
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._ensure_schema()
        self._ensure_seed_data()

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def _ensure_schema(self) -> None:
        with self._connect() as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS site_intel (
                    domain TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    summary TEXT NOT NULL,
                    category TEXT NOT NULL,
                    risk_level TEXT NOT NULL,
                    security_threat INTEGER NOT NULL DEFAULT 0,
                    harmful_content INTEGER NOT NULL DEFAULT 0,
                    source TEXT NOT NULL DEFAULT 'seed',
                    region TEXT NOT NULL DEFAULT 'global',
                    language TEXT NOT NULL DEFAULT 'mixed',
                    tags_json TEXT NOT NULL DEFAULT '[]',
                    aliases_json TEXT NOT NULL DEFAULT '[]',
                    indicators_json TEXT NOT NULL DEFAULT '[]',
                    risk_types_json TEXT NOT NULL DEFAULT '[]',
                    vector_json TEXT NOT NULL DEFAULT '[]',
                    created_at REAL NOT NULL,
                    updated_at REAL NOT NULL
                )
                """
            )
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS site_embeddings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    domain TEXT NOT NULL,
                    chunk_index INTEGER NOT NULL,
                    chunk_text TEXT NOT NULL,
                    vector_json TEXT NOT NULL,
                    created_at REAL NOT NULL,
                    updated_at REAL NOT NULL,
                    UNIQUE(domain, chunk_index)
                )
                """
            )
            conn.commit()
            self._ensure_column(conn, "site_intel", "region", "TEXT NOT NULL DEFAULT 'global'")
            self._ensure_column(conn, "site_intel", "language", "TEXT NOT NULL DEFAULT 'mixed'")
            self._ensure_column(conn, "site_intel", "aliases_json", "TEXT NOT NULL DEFAULT '[]'")
            self._ensure_column(conn, "site_intel", "indicators_json", "TEXT NOT NULL DEFAULT '[]'")
            self._ensure_column(conn, "site_intel", "risk_types_json", "TEXT NOT NULL DEFAULT '[]'")
            self._ensure_column(conn, "site_intel", "vector_json", "TEXT NOT NULL DEFAULT '[]'")
            conn.commit()

    def _ensure_column(self, conn: sqlite3.Connection, table: str, column: str, ddl: str) -> None:
        row = conn.execute(f"PRAGMA table_info({table})").fetchall()
        columns = {item["name"] for item in row}
        if column not in columns:
            conn.execute(f"ALTER TABLE {table} ADD COLUMN {column} {ddl}")

    def _ensure_seed_data(self) -> None:
        entries = self._load_seed_entries()
        if entries:
            self.bulk_upsert(entries)

    def rebuild_from_seed(self, seed_path: Path | str = SEED_PATH) -> dict[str, Any]:
        if str(seed_path) == str(SEED_PATH):
            entries = self._load_seed_entries()
        else:
            payload = json.loads(Path(seed_path).read_text(encoding="utf-8"))
            entries = payload if isinstance(payload, list) else []
        with self._connect() as conn:
            conn.execute("DELETE FROM site_embeddings")
            conn.execute("DELETE FROM site_intel")
            conn.commit()
        stored = self.bulk_upsert(entries)
        stats = self.stats()
        return {"stored": stored, "stats": stats}

    def _load_seed_entries(self) -> list[dict[str, Any]]:
        entries: list[dict[str, Any]] = []
        for path in sorted(DATA_DIR.glob(SEED_GLOB)):
            try:
                payload = json.loads(path.read_text(encoding="utf-8"))
            except Exception:
                continue
            if isinstance(payload, list):
                entries.extend(payload)
        return entries

    def upsert_entry(self, entry: dict[str, Any]) -> dict[str, Any]:
        domain = _normalize_domain(entry.get("domain") or _domain_from_url(entry.get("url", "")))
        if not domain:
            raise ValueError("domain is required")

        normalized = {
            "domain": domain,
            "title": str(entry.get("title") or domain),
            "summary": str(entry.get("summary") or ""),
            "category": str(entry.get("category") or "unknown"),
            "risk_level": str(entry.get("risk_level") or "allow"),
            "security_threat": bool(entry.get("security_threat", False)),
            "harmful_content": bool(entry.get("harmful_content", False)),
            "source": str(entry.get("source") or "seed"),
            "region": str(entry.get("region") or "global"),
            "language": str(entry.get("language") or "mixed"),
            "tags": [str(tag).strip() for tag in (entry.get("tags") or []) if str(tag).strip()],
            "aliases": [str(tag).strip() for tag in (entry.get("aliases") or []) if str(tag).strip()],
            "indicators": [str(tag).strip() for tag in (entry.get("indicators") or []) if str(tag).strip()],
            "risk_types": [str(tag).strip() for tag in (entry.get("risk_types") or []) if str(tag).strip()],
        }
        chunks = _chunk_entry_texts(normalized)
        entry_vector = _embed_text(" ".join(chunks))
        now = time.time()

        with self._connect() as conn:
            conn.execute(
                """
                INSERT INTO site_intel (
                    domain, title, summary, category, risk_level,
                    security_threat, harmful_content, source, region, language,
                    tags_json, aliases_json, indicators_json, risk_types_json, vector_json,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(domain) DO UPDATE SET
                    title=excluded.title,
                    summary=excluded.summary,
                    category=excluded.category,
                    risk_level=excluded.risk_level,
                    security_threat=excluded.security_threat,
                    harmful_content=excluded.harmful_content,
                    source=excluded.source,
                    region=excluded.region,
                    language=excluded.language,
                    tags_json=excluded.tags_json,
                    aliases_json=excluded.aliases_json,
                    indicators_json=excluded.indicators_json,
                    risk_types_json=excluded.risk_types_json,
                    vector_json=excluded.vector_json,
                    updated_at=excluded.updated_at
                """,
                (
                    normalized["domain"],
                    normalized["title"],
                    normalized["summary"],
                    normalized["category"],
                    normalized["risk_level"],
                    int(normalized["security_threat"]),
                    int(normalized["harmful_content"]),
                    normalized["source"],
                    normalized["region"],
                    normalized["language"],
                    json.dumps(normalized["tags"], ensure_ascii=False),
                    json.dumps(normalized["aliases"], ensure_ascii=False),
                    json.dumps(normalized["indicators"], ensure_ascii=False),
                    json.dumps(normalized["risk_types"], ensure_ascii=False),
                    json.dumps(entry_vector),
                    now,
                    now,
                ),
            )
            conn.execute("DELETE FROM site_embeddings WHERE domain = ?", (normalized["domain"],))
            for index, chunk in enumerate(chunks):
                conn.execute(
                    """
                    INSERT INTO site_embeddings (
                        domain, chunk_index, chunk_text, vector_json, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    (
                        normalized["domain"],
                        index,
                        chunk,
                        json.dumps(_embed_text(chunk)),
                        now,
                        now,
                    ),
                )
            conn.commit()

        return normalized

    def bulk_upsert(self, entries: list[dict[str, Any]]) -> int:
        count = 0
        for entry in entries:
            self.upsert_entry(entry)
            count += 1
        return count

    def get_domain_entry(self, domain_or_url: str) -> dict[str, Any] | None:
        raw = str(domain_or_url or "")
        domain = _normalize_domain(raw)
        if "://" in raw or "/" in raw:
            domain = _domain_from_url(domain_or_url)
        if not domain:
            return None

        with self._connect() as conn:
            row = None
            for candidate in _domain_candidates(domain):
                row = conn.execute("SELECT * FROM site_intel WHERE domain = ?", (candidate,)).fetchone()
                if row:
                    break
        return self._row_to_entry(row) if row else None

    def search_similar(self, url: str, title: str = "", snippet: str = "", limit: int = 5) -> list[dict[str, Any]]:
        query_text = " ".join(part for part in [url, title, snippet] if part).strip()
        query_vector = _embed_text(query_text)
        if not query_text:
            return []

        with self._connect() as conn:
            rows = conn.execute(
                """
                SELECT
                    e.domain,
                    e.chunk_index,
                    e.chunk_text,
                    e.vector_json,
                    i.title,
                    i.summary,
                    i.category,
                    i.risk_level,
                    i.security_threat,
                    i.harmful_content,
                    i.source,
                    i.region,
                    i.language,
                    i.tags_json,
                    i.aliases_json,
                    i.indicators_json,
                    i.risk_types_json
                FROM site_embeddings e
                JOIN site_intel i ON i.domain = e.domain
                """
            ).fetchall()

        aggregated: dict[str, dict[str, Any]] = {}
        for row in rows:
            score = _cosine_similarity(query_vector, json.loads(row["vector_json"] or "[]"))
            score *= _chunk_penalty(row["chunk_text"])
            if score <= 0.16:
                continue
            domain = row["domain"]
            current = aggregated.get(domain)
            chunk_hit = {
                "chunk_index": int(row["chunk_index"]),
                "chunk_text": row["chunk_text"],
                "similarity": round(score, 4),
            }
            if current is None:
                current = {
                    "domain": domain,
                    "title": row["title"],
                    "summary": row["summary"],
                    "category": row["category"],
                    "risk_level": row["risk_level"],
                    "security_threat": bool(row["security_threat"]),
                    "harmful_content": bool(row["harmful_content"]),
                    "source": row["source"],
                    "region": row["region"],
                    "language": row["language"],
                    "tags": json.loads(row["tags_json"] or "[]"),
                    "aliases": json.loads(row["aliases_json"] or "[]"),
                    "indicators": json.loads(row["indicators_json"] or "[]"),
                    "risk_types": json.loads(row["risk_types_json"] or "[]"),
                    "similarity": round(score, 4),
                    "matched_chunks": [chunk_hit],
                }
                aggregated[domain] = current
                continue

            current["similarity"] = round(max(float(current["similarity"]), score), 4)
            current["matched_chunks"].append(chunk_hit)

        ranked = list(aggregated.values())
        for item in ranked:
            item["matched_chunks"].sort(key=lambda chunk: chunk["similarity"], reverse=True)
            item["matched_chunks"] = item["matched_chunks"][:3]
        ranked.sort(key=lambda item: item.get("similarity", 0.0), reverse=True)
        return ranked[: max(1, int(limit or 5))]

    def stats(self) -> dict[str, Any]:
        with self._connect() as conn:
            site_row = conn.execute(
                """
                SELECT
                  COUNT(*) AS total,
                  SUM(CASE WHEN risk_level = 'block' THEN 1 ELSE 0 END) AS blocked,
                  SUM(CASE WHEN risk_level = 'warning' THEN 1 ELSE 0 END) AS warned,
                  SUM(CASE WHEN security_threat = 1 THEN 1 ELSE 0 END) AS security_threats,
                  SUM(CASE WHEN harmful_content = 1 THEN 1 ELSE 0 END) AS harmful_sites
                FROM site_intel
                """
            ).fetchone()
            embedding_row = conn.execute("SELECT COUNT(*) AS total FROM site_embeddings").fetchone()
            region_rows = conn.execute(
                "SELECT region, COUNT(*) AS count FROM site_intel GROUP BY region ORDER BY count DESC"
            ).fetchall()

        return {
            "db_path": str(self.db_path),
            "total_sites": int(site_row["total"] or 0),
            "total_embedding_chunks": int(embedding_row["total"] or 0),
            "blocked": int(site_row["blocked"] or 0),
            "warned": int(site_row["warned"] or 0),
            "security_threats": int(site_row["security_threats"] or 0),
            "harmful_sites": int(site_row["harmful_sites"] or 0),
            "regions": {row["region"]: int(row["count"] or 0) for row in region_rows},
        }

    def _row_to_entry(self, row: sqlite3.Row) -> dict[str, Any]:
        return {
            "domain": row["domain"],
            "title": row["title"],
            "summary": row["summary"],
            "category": row["category"],
            "risk_level": row["risk_level"],
            "security_threat": bool(row["security_threat"]),
            "harmful_content": bool(row["harmful_content"]),
            "source": row["source"],
            "region": row["region"],
            "language": row["language"],
            "tags": json.loads(row["tags_json"] or "[]"),
            "aliases": json.loads(row["aliases_json"] or "[]"),
            "indicators": json.loads(row["indicators_json"] or "[]"),
            "risk_types": json.loads(row["risk_types_json"] or "[]"),
        }
