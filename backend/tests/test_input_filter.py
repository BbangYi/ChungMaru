import sys
import unittest
from pathlib import Path


API_DIR = Path(__file__).resolve().parents[1] / "api"
if str(API_DIR) not in sys.path:
    sys.path.insert(0, str(API_DIR))

from input_filter import filter_android_json
from normalizer import normalize
from pipeline import _build_norm_to_orig_map, _extract_dictionary_spans, _extract_original_direct_spans, _merge_spans


class AndroidInputFilterTest(unittest.TestCase):
    def test_keeps_precise_visual_candidates_with_small_bounds(self):
        raw = {
            "comments": [
                {
                    "commentText": "개새끼",
                    "author_id": "android-accessibility-range:개새끼",
                    "boundsInScreen": {"left": 64, "top": 500, "right": 160, "bottom": 550},
                },
                {
                    "commentText": "씨발",
                    "author_id": "android-accessibility:title",
                    "boundsInScreen": {"left": 72, "top": 220, "right": 142, "bottom": 268},
                },
                {
                    "commentText": "개새끼",
                    "author_id": "screen:accessibility_text:content",
                    "boundsInScreen": {"left": 72, "top": 320, "right": 164, "bottom": 368},
                },
                {
                    "commentText": "tlqkf",
                    "author_id": "youtube-visual-range:Tlqkf",
                    "boundsInScreen": {"left": 110, "top": 80, "right": 160, "bottom": 128},
                },
                {
                    "commentText": "ssibal",
                    "author_id": "ocr:ssibal",
                    "boundsInScreen": {"left": 240, "top": 360, "right": 300, "bottom": 420},
                },
            ],
        }

        filtered = filter_android_json(raw)

        self.assertEqual(
            ["개새끼", "씨발", "개새끼", "tlqkf", "ssibal"],
            [item["commentText"] for item in filtered],
        )

    def test_still_drops_small_non_visual_icon_like_bounds(self):
        raw = {
            "comments": [
                {
                    "commentText": "보기",
                    "boundsInScreen": {"left": 110, "top": 80, "right": 160, "bottom": 128},
                },
            ],
        }

        self.assertEqual([], filter_android_json(raw))

    def test_backend_dictionary_spans_cover_android_visual_terms(self):
        cases = {
            "개새끼 뭐하는 거야": ["개새끼"],
            "병신아 꺼져": ["병신", "꺼져"],
            "ssibal 뜻": ["ssibal"],
            "qudtls 뜻": ["qudtls"],
            "Tlqkf 공부법": ["Tlqkf"],
            "wlfkf": ["wlfkf"],
            "whssk": ["whssk"],
            "alcls": ["alcls"],
            "rjwu": ["rjwu"],
        }

        for text, expected_spans in cases.items():
            with self.subTest(text=text):
                normalized = normalize(text)
                mapping = _build_norm_to_orig_map(text, normalized)
                spans = _merge_spans(
                    _extract_original_direct_spans(text) + _extract_dictionary_spans(text, normalized, mapping),
                    text,
                )
                self.assertEqual(expected_spans, [span["text"] for span in spans])


if __name__ == "__main__":
    unittest.main()
