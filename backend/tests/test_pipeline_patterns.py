import sys
import unittest
from pathlib import Path


API_DIR = Path(__file__).resolve().parents[1] / "api"

if str(API_DIR) not in sys.path:
    sys.path.insert(0, str(API_DIR))


class PipelinePatternTest(unittest.TestCase):
    def test_direct_spans_cover_android_qwerty_variants(self):
        from pipeline import _extract_original_direct_spans

        cases = {
            "Tlakf 공부법": "Tlakf",
            "Tlgkf 또 보여줘야 돼": "Tlgkf",
            "TIqkf 자동자": "TIqkf",
            "tlqkf비용 효과 있을까?": "tlqkf",
        }

        for text, expected in cases.items():
            with self.subTest(text=text):
                spans = _extract_original_direct_spans(text)
                self.assertTrue(
                    any(span["text"] == expected for span in spans),
                    spans,
                )

    def test_direct_spans_do_not_trigger_safe_ascii_substrings(self):
        from pipeline import _extract_original_direct_spans

        for text in [
            "abstract factory",
            "assistant script",
            "classic assignment",
            "computational geometry",
            "pussycat dolls",
            "Dickens novel",
        ]:
            with self.subTest(text=text):
                self.assertEqual([], _extract_original_direct_spans(text))


if __name__ == "__main__":
    unittest.main()
