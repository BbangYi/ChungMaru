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
            "ㅅ ㅂ 뭐냐": ["ㅅ ㅂ"],
            "ㅅ.ㅂ 뭐냐": ["ㅅ.ㅂ"],
            "ㅈ ㄴ 짜증나": ["ㅈ ㄴ"],
            "ㅂ ㅅ 같네": ["ㅂ ㅅ"],
            "ssibal 뜻": ["ssibal"],
            "qudtls 뜻": ["qudtls"],
            "Tlqkf 공부법": ["Tlqkf"],
            "wlfkf": ["wlfkf"],
            "whssk": ["whssk"],
            "alcls": ["alcls"],
            "rjwu": ["rjwu"],
            "콜걸성인마사지": ["콜걸성인마사지"],
            "콜걸 성인마사지": ["콜걸", "성인마사지"],
            "콜.걸 성인-마사지": ["콜.걸", "성인-마사지"],
            "출장마사지 콜걸": ["출장마사지", "콜걸"],
            "출장 마사지 후기": ["출장 마사지"],
            "출장안마 후기": ["출장안마"],
            "출장 만남 광고": ["출장 만남"],
            "성인업소 홍보": ["성인업소"],
            "유흥업소 후기": ["유흥업소"],
            "유흥 마사지 추천": ["유흥 마사지"],
            "유흥주점 후기": ["유흥주점"],
            "안마방 위치": ["안마방"],
            "키스방 후기": ["키스방"],
            "립카페 추천": ["립카페"],
            "룸싸롱 가격": ["룸싸롱"],
            "룸살롱 후기": ["룸살롱"],
            "셔츠룸 위치": ["셔츠룸"],
            "조건만남 광고": ["조건만남"],
            "성매매 알선": ["성매매"],
            "adult-webtoon-plus.kr 콜걸성인마사지": ["콜걸성인마사지"],
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

    def test_backend_dictionary_spans_do_not_block_adult_word_alone(self):
        for text in [
            "성인 교육 프로그램",
            "성인 대상 금융 교육",
            "성인 문해 교육 기사",
            "성인 대상 마사지 교육",
            "출장 일정 안내",
            "출장 수리 기사 예약",
            "마사지 자격증 교육",
            "유흥 산업 분석 보고서",
            "주점 창업 통계",
            "카페 추천 목록",
            "살롱 문화사",
            "셔츠 보관 룸 인테리어",
            "안내방송 확인",
        ]:
            with self.subTest(text=text):
                normalized = normalize(text)
                mapping = _build_norm_to_orig_map(text, normalized)
                spans = _merge_spans(
                    _extract_original_direct_spans(text) + _extract_dictionary_spans(text, normalized, mapping),
                    text,
                )
                self.assertEqual([], spans)

    def test_backend_dictionary_spans_ignore_normal_korean_phrase_fragments(self):
        for text in [
            "출시 발표",
            "공시 발표",
            "도시 발전",
            "정시 발표",
            "신제품 출시 발표",
            "대한민국 시 발표",
            "지 랄산",
        ]:
            with self.subTest(text=text):
                normalized = normalize(text)
                mapping = _build_norm_to_orig_map(text, normalized)
                spans = _merge_spans(
                    _extract_original_direct_spans(text) + _extract_dictionary_spans(text, normalized, mapping),
                    text,
                )
                self.assertEqual([], spans)

    def test_backend_dictionary_spans_ignore_whitelisted_safe_words(self):
        for text in [
            "병신도",
            "병신자",
            "새끼손가락",
            "새끼줄",
            "새끼고양이",
            "새끼강아지",
            "시발점",
            "시발역",
            "시발택시",
        ]:
            with self.subTest(text=text):
                normalized = normalize(text)
                mapping = _build_norm_to_orig_map(text, normalized)
                spans = _merge_spans(
                    _extract_original_direct_spans(text) + _extract_dictionary_spans(text, normalized, mapping),
                    text,
                )
                self.assertEqual([], spans)

    def test_backend_pipeline_short_circuits_whitelisted_safe_words_with_particles(self):
        from pipeline import ProfanityPipeline

        pipeline = ProfanityPipeline()

        for text in [
            "병신도는",
            "새끼손가락으로",
            "새끼고양이가",
            "새끼강아지는",
            "시발점이",
            "시발역에서",
        ]:
            with self.subTest(text=text):
                result = pipeline.analyze(text)
                self.assertFalse(result["is_offensive"])
                self.assertEqual([], result["evidence_spans"])


if __name__ == "__main__":
    unittest.main()
