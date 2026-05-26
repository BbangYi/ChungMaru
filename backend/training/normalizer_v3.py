"""
v3 정규화 — api/normalizer.normalize()를 학습 환경에서 사용하기 위한 래퍼.

train_v3.py는 normalize_text()를 임포트하며, 실제 구현은 api/normalizer.py에 있음.
"""
import sys
from pathlib import Path

_API = Path(__file__).resolve().parent.parent / "api"
if str(_API) not in sys.path:
    sys.path.insert(0, str(_API))

from normalizer import normalize as normalize_text  # noqa: F401
