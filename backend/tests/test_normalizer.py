"""
api/normalizer.py 단위 테스트.

각 정규화 단계를 독립적으로 검증하고,
전체 파이프라인(normalize)이 욕설 변형을 표준형으로 바꾸면서
정상 단어는 건드리지 않는지 확인한다.
"""
import sys
import unittest
from pathlib import Path

API_DIR = Path(__file__).resolve().parents[1] / "api"
if str(API_DIR) not in sys.path:
    sys.path.insert(0, str(API_DIR))

from normalizer import (
    normalize,
    collapse_repeats,
    compose_jamo,
    convert_fullwidth,
    expand_chosung,
    remove_inserted_chars,
    remove_inserted_emoji,
    remove_inserted_kana,
)


class TestInvisibleRemoval(unittest.TestCase):
    """0단계: invisible 유니코드 문자 제거"""

    def test_zero_width_space(self):
        self.assertEqual("병신", normalize("병​신"))

    def test_word_joiner(self):
        self.assertEqual("씨발", normalize("씨⁠발"))

    def test_bom(self):
        self.assertEqual("개새끼", normalize("﻿개새끼"))

    def test_soft_hyphen(self):
        self.assertEqual("병신", normalize("병­신"))


class TestFullwidthConversion(unittest.TestCase):
    """0.5단계: 전각→반각 변환"""

    def test_fullwidth_lowercase(self):
        self.assertEqual("abc", convert_fullwidth("ａｂｃ"))

    def test_fullwidth_uppercase(self):
        self.assertEqual("ABC", convert_fullwidth("ＡＢＣ"))

    def test_fullwidth_digits(self):
        self.assertEqual("012", convert_fullwidth("０１２"))

    def test_fullwidth_symbols(self):
        self.assertEqual("!@#", convert_fullwidth("！＠＃"))

    def test_fullwidth_space(self):
        self.assertEqual("a b", convert_fullwidth("ａ　ｂ"))

    def test_fullwidth_qwerty_pipeline(self):
        # ｔｌｑｋｆ → tlqkf → (inko 없으면 qwerty치환) → 시발
        self.assertEqual("시발", normalize("ｔｌｑｋｆ"))

    def test_fullwidth_ssibal_pipeline(self):
        # ｓｓｉｂａｌ → step0.5: ssibal → step0.8(roman): 씨발
        self.assertEqual("씨발", normalize("ｓｓｉｂａｌ"))

    def test_normal_ascii_unchanged(self):
        self.assertEqual("hello", convert_fullwidth("hello"))


class TestInsertedCharRemoval(unittest.TestCase):
    """5단계: 특수문자/숫자 끼워넣기 제거"""

    def test_dot_between_hangul(self):
        self.assertEqual("병신", remove_inserted_chars("병.신"))

    def test_number_between_hangul(self):
        self.assertEqual("시발", remove_inserted_chars("시1발"))

    def test_consecutive_special(self):
        self.assertEqual("병신", remove_inserted_chars("병..신"))

    def test_english_between_hangul_preserved(self):
        # 영문은 제거하지 않음
        self.assertEqual("한a글", remove_inserted_chars("한a글"))


class TestInsertedEmojiRemoval(unittest.TestCase):
    """3단계: 이모지 끼워넣기 제거"""

    def test_single_emoji(self):
        self.assertEqual("병신", remove_inserted_emoji("병🖕신"))

    def test_multiple_emoji(self):
        self.assertEqual("병신", remove_inserted_emoji("병🖕💀신"))

    def test_star_between_hangul(self):
        # ★ (U+2605) 은 ☀-➿ 범위 → emoji 제거가 처리
        self.assertEqual("개새끼", remove_inserted_emoji("개★새끼"))

    def test_standalone_emoji_preserved(self):
        # 독립 이모지는 유지
        self.assertEqual("좋아요 👍", remove_inserted_emoji("좋아요 👍"))


class TestInsertedKanaRemoval(unittest.TestCase):
    """4단계: 가나 끼워넣기 제거"""

    def test_katakana(self):
        self.assertEqual("병신", remove_inserted_kana("병バ신"))

    def test_hiragana(self):
        self.assertEqual("병신", remove_inserted_kana("병ひ신"))

    def test_mixed_kana(self):
        self.assertEqual("병신", remove_inserted_kana("병ひカ신"))

    def test_standalone_japanese_preserved(self):
        self.assertEqual("バカ 진짜", remove_inserted_kana("バカ 진짜"))


class TestCollapseRepeats(unittest.TestCase):
    """6단계: 반복 문자 축약"""

    def test_triple_consonant(self):
        self.assertEqual("ㅋ", collapse_repeats("ㅋㅋㅋㅋ"))

    def test_vowel_elongation(self):
        self.assertEqual("씨이발", collapse_repeats("씨이이이발"))

    def test_normal_double_preserved(self):
        self.assertEqual("ㅋㅋ", collapse_repeats("ㅋㅋ"))


class TestComposeJamo(unittest.TestCase):
    """7단계: 자모 조립"""

    def test_sibal_jamo(self):
        self.assertEqual("시발", compose_jamo("ㅅㅣㅂㅏㄹ"))

    def test_byeongsin_jamo(self):
        self.assertEqual("병신", compose_jamo("ㅂㅕㅇㅅㅣㄴ"))

    def test_spaced_jamo(self):
        self.assertEqual("시발", compose_jamo("ㅅ ㅣ ㅂ ㅏ ㄹ"))

    def test_chosung_only_unchanged(self):
        # 초성만 있으면 조립 불가 → 그대로
        result = compose_jamo("ㅋㅋ")
        self.assertIn("ㅋ", result)


class TestExpandChosung(unittest.TestCase):
    """8단계: 초성 전사"""

    def test_ssb(self):
        self.assertEqual("씨발", expand_chosung("ㅅㅂ"))

    def test_bs(self):
        self.assertEqual("병신", expand_chosung("ㅂㅅ"))

    def test_jrl(self):
        self.assertEqual("지랄", expand_chosung("ㅈㄹ"))

    def test_gsk(self):
        self.assertEqual("개새끼", expand_chosung("ㄱㅅㄲ"))

    def test_mc(self):
        self.assertEqual("미쳤", expand_chosung("ㅁㅊ"))


class TestSpaceMerge(unittest.TestCase):
    """10단계 (v3): 공백 삽입 우회 병합"""

    def test_saekki_spaced(self):
        self.assertEqual("새끼들이", normalize("새 끼들이"))

    def test_sibal_spaced(self):
        self.assertEqual("씨발", normalize("씨 발"))

    def test_gaesaekki_spaced(self):
        self.assertEqual("개새끼", normalize("개 새끼"))

    def test_byeongsin_spaced(self):
        self.assertEqual("병신", normalize("병 신"))


class TestQwertySubstitution(unittest.TestCase):
    """12단계 (v3): qwerty 욕설 치환"""

    def test_tlqkf(self):
        self.assertEqual("시발 진짜", normalize("tlqkf 진짜"))

    def test_tlqkfwk(self):
        self.assertEqual("시발아", normalize("tlqkfwk"))

    def test_qudtls(self):
        self.assertEqual("병신", normalize("qudtls"))

    def test_rotorrl(self):
        self.assertEqual("개새끼들", normalize("rotorrl들"))

    def test_wlfkf(self):
        self.assertEqual("지랄", normalize("wlfkf"))

    def test_alcls(self):
        self.assertEqual("미친", normalize("alcls"))

    def test_tnwjd(self):
        self.assertEqual("씨발", normalize("tnwjd"))

    def test_rjwu(self):
        self.assertEqual("꺼져", normalize("rjwu"))


class TestVariantSpelling(unittest.TestCase):
    """13단계 (v3): 변형 철자 → 표준형"""

    def test_ssippal(self):
        self.assertEqual("씨발 이게 뭐야", normalize("씨빨 이게 뭐야"))

    def test_sswibbal(self):
        self.assertEqual("씨발", normalize("쒸발"))

    def test_ssibab(self):
        self.assertEqual("씨발", normalize("씨밥"))

    def test_saeki(self):
        self.assertEqual("새끼", normalize("새키"))

    def test_bingsin(self):
        self.assertEqual("병신", normalize("빙신"))

    def test_jot(self):
        self.assertEqual("좆", normalize("좃"))


class TestChosungWithDot(unittest.TestCase):
    """특수문자 제거 후 초성 전사 (복합 우회 패턴)"""

    def test_dot_separated_chosung(self):
        # ㅅ.ㅂ → remove_inserted_chars → ㅅㅂ → expand_chosung → 씨발
        self.assertEqual("씨발", normalize("ㅅ.ㅂ"))

    def test_number_separated_chosung(self):
        self.assertEqual("씨발", normalize("ㅅ1ㅂ"))


class TestFalsePositivePrevention(unittest.TestCase):
    """정상 단어가 변형되지 않아야 함 (FP 방지)"""

    def test_sibaljeom(self):
        # 시발점 = 출발점(기점) — 변형 없어야 함
        self.assertEqual("시발점에서", normalize("시발점에서"))

    def test_gaein_jeongbo(self):
        self.assertEqual("개인정보", normalize("개인정보"))

    def test_byeongwon(self):
        self.assertEqual("병원에서", normalize("병원에서"))

    def test_micheon(self):
        # '미천하다' — 미친 전사가 오발동하면 안 됨
        self.assertEqual("미천한", normalize("미천한"))

    def test_jiral_no_match_in_sentence(self):
        # 정상 문장의 ㅈ+ㄹ 패턴 없음
        self.assertEqual("자랑스럽다", normalize("자랑스럽다"))

    def test_english_word_unchanged(self):
        self.assertEqual("hello world", normalize("hello world"))


class TestFullPipeline(unittest.TestCase):
    """전체 파이프라인 통합 케이스"""

    def test_chosung_bs(self):
        self.assertEqual("씨발 진짜", normalize("ㅅㅂ 진짜"))

    def test_chosung_bp(self):
        self.assertEqual("병신이냐", normalize("ㅂㅅ이냐"))

    def test_jamo_assembly(self):
        self.assertEqual("시발", normalize("ㅅㅣㅂㅏㄹ"))

    def test_complex_chosung(self):
        self.assertEqual("ㅄ", "ㅄ")  # 단일 복합 자모
        self.assertEqual("병신", normalize("ㅄ"))

    def test_dot_inserted(self):
        self.assertEqual("병신 같은", normalize("병.신 같은"))

    def test_emoji_inserted(self):
        self.assertEqual("병신", normalize("병🖕신"))

    def test_zero_width_space_bypass(self):
        self.assertEqual("병신", normalize("병​신"))


class TestRomanProfanity(unittest.TestCase):
    """0.8단계: 로마자 욕설 역변환 (inko 이전)"""

    def test_ssibal(self):
        self.assertEqual("씨발", normalize("ssibal"))

    def test_sibal(self):
        self.assertEqual("씨발", normalize("sibal"))

    def test_shibal(self):
        self.assertEqual("씨발", normalize("shibal"))

    def test_gaesaekki(self):
        self.assertEqual("개새끼", normalize("gaesaekki"))

    def test_byeongsin(self):
        self.assertEqual("병신", normalize("byeongsin"))

    def test_jiral(self):
        self.assertEqual("지랄", normalize("jiral"))

    def test_michin(self):
        self.assertEqual("미친", normalize("michin"))

    def test_in_sentence(self):
        self.assertEqual("진짜 씨발", normalize("진짜 ssibal"))

    def test_case_insensitive(self):
        self.assertEqual("씨발", normalize("SSIBAL"))

    def test_fullwidth_then_roman(self):
        # ｓｓｉｂａｌ → step0.5: ssibal → step0.8: 씨발
        self.assertEqual("씨발", normalize("ｓｓｉｂａｌ"))


class TestPartialSyllable(unittest.TestCase):
    """5.5단계: 자모+음절 혼합 패턴 및 숫자 말미 대체"""

    def test_jamo_syllable_sibal(self):
        # ㅅ1발 → step5: ㅅ발 → step5.5: 씨발
        self.assertEqual("씨발", normalize("ㅅ1발"))

    def test_jamo_syllable_direct(self):
        # ㅅ발 직접 입력
        self.assertEqual("씨발", normalize("ㅅ발"))

    def test_jamo_syllable_gaesaekki(self):
        # 개ㅅ1끼 → step5: 개ㅅ끼 → step5.5: 개새끼
        self.assertEqual("개새끼", normalize("개ㅅ1끼"))

    def test_number_terminal(self):
        # 씨8 — 8이 말미에서 발을 대체
        self.assertEqual("씨발", normalize("씨8"))

    def test_number_inserted_still_works(self):
        # 씨8발 — 기존 remove_inserted_chars가 처리 (step5)
        self.assertEqual("씨발", normalize("씨8발"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
