from __future__ import annotations

import os
from pathlib import Path


def _parse_env_line(line: str) -> tuple[str, str] | None:
    stripped = line.strip()
    if not stripped or stripped.startswith("#") or "=" not in stripped:
        return None
    key, value = stripped.split("=", 1)
    key = key.strip()
    value = value.strip().strip("'").strip('"')
    if not key:
        return None
    return key, value


def load_local_env() -> list[str]:
    """
    backend/.env 또는 backend/api/.env 파일이 있으면 현재 프로세스 환경변수로 로드한다.
    이미 셸 환경에 잡힌 값은 덮어쓰지 않는다.
    """
    api_dir = Path(__file__).resolve().parent
    candidates = [api_dir / ".env", api_dir.parent / ".env"]
    loaded: list[str] = []
    for path in candidates:
        if not path.exists():
            continue
        for line in path.read_text(encoding="utf-8").splitlines():
            parsed = _parse_env_line(line)
            if not parsed:
                continue
            key, value = parsed
            if key not in os.environ:
                os.environ[key] = value
                loaded.append(key)
    return loaded
