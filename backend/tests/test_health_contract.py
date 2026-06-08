import sys
import unittest
from pathlib import Path


API_DIR = Path(__file__).resolve().parents[1] / "api"

if str(API_DIR) not in sys.path:
    sys.path.insert(0, str(API_DIR))


class HealthContractTest(unittest.TestCase):
    def test_health_exposes_extension_model_readiness_contract(self):
        from fastapi.testclient import TestClient
        from app import app

        with TestClient(app) as client:
            response = client.get("/health")

        self.assertEqual(200, response.status_code)
        body = response.json()
        models = body.get("models") or {}

        self.assertIn("model_ready", body)
        self.assertIn("missing_model_files", body)
        self.assertIn("text_pipeline_ready", body)
        self.assertIn("pipeline_loaded", body)
        self.assertIn("model_files_ready", models)
        self.assertIn("missing_model_files", models)

        expected_model_ready = (
            body["text_pipeline_ready"]
            and body["pipeline_loaded"]
            and body.get("text_pipeline_error") is None
            and body.get("pipeline_error") is None
            and models["model_files_ready"]
        )
        self.assertEqual(expected_model_ready, body["model_ready"])
        self.assertEqual(models["missing_model_files"], body["missing_model_files"])

    def test_site_check_and_health_lazy_initialize_site_agent(self):
        from fastapi.testclient import TestClient
        import app as app_module

        previous_site_agent = app_module.site_risk_agent
        app_module.site_risk_agent = None
        client = TestClient(app_module.app)
        try:
            site_response = client.post(
                "/site/check",
                json={"url": "https://google-account-verify.com/login"},
            )
            health_response = client.get("/health")
        finally:
            client.close()
            app_module.site_risk_agent = previous_site_agent

        self.assertEqual(200, site_response.status_code)
        site_body = site_response.json()
        self.assertEqual("block", site_body["verdict"])
        self.assertEqual("phishing", site_body["site_category"])

        self.assertEqual(200, health_response.status_code)
        health_body = health_response.json()
        self.assertGreater(health_body["site_intel"]["total_sites"], 0)


if __name__ == "__main__":
    unittest.main()
