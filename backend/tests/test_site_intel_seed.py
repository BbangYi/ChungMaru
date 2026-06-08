import json
import sys
import tempfile
import unittest
from pathlib import Path


API_DIR = Path(__file__).resolve().parents[1] / "api"
BACKEND_DIR = Path(__file__).resolve().parents[1]
SEED_PATH = BACKEND_DIR / "data" / "site_intel_seed_massive.json"

if str(API_DIR) not in sys.path:
    sys.path.insert(0, str(API_DIR))


class SiteIntelSeedTest(unittest.TestCase):
    def test_curated_site_seed_is_present_and_balanced(self):
        self.assertTrue(SEED_PATH.exists(), "site intel seed JSON is required for non-empty site protection")

        entries = json.loads(SEED_PATH.read_text(encoding="utf-8"))
        risk_levels = {str(item.get("risk_level") or "") for item in entries}
        categories = {str(item.get("category") or "") for item in entries}

        self.assertGreaterEqual(len(entries), 100)
        self.assertTrue({"allow", "warning", "block"}.issubset(risk_levels))
        self.assertTrue({"adult", "gambling", "phishing", "malware"}.issubset(categories))

    def test_store_loads_seed_into_empty_database(self):
        from site_intel_store import SiteIntelStore

        with tempfile.TemporaryDirectory() as tmp_dir:
            store = SiteIntelStore(Path(tmp_dir) / "site_intel.sqlite")
            stats = store.stats()

        self.assertGreater(stats["total_sites"], 0)
        self.assertGreater(stats["total_embedding_chunks"], 0)
        self.assertGreater(stats["blocked"], 0)

    def test_site_risk_agent_uses_seed_for_exact_domain_verdicts(self):
        from site_risk_agent import SiteRiskAgent

        agent = SiteRiskAgent()

        blocked = agent.check_site("https://google-account-verify.com/login")
        harmful = agent.check_site("https://adult-webtoon-plus.kr/")
        community = agent.check_site("https://www.dcinside.com/")
        allowed = agent.check_site("https://mozilla.org/")

        self.assertEqual("block", blocked["verdict"])
        self.assertEqual("phishing", blocked["site_category"])
        self.assertEqual("google-account-verify.com", blocked["exact_match"]["domain"])

        self.assertEqual("block", harmful["verdict"])
        self.assertEqual("adult", harmful["site_category"])
        self.assertEqual("adult-webtoon-plus.kr", harmful["exact_match"]["domain"])

        self.assertEqual("warning", community["verdict"])
        self.assertEqual("community", community["site_category"])
        self.assertEqual("dcinside.com", community["exact_match"]["domain"])

        self.assertEqual("allow", allowed["verdict"])
        self.assertEqual("mozilla.org", allowed["exact_match"]["domain"])


if __name__ == "__main__":
    unittest.main()
