package com.capstone.design.youtubeparser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTextCandidateExtractorTest {

    @Test
    fun extractCandidates_keepsChromeSearchResultTextAsAccessibilityCandidates() {
        val candidates = ScreenTextCandidateExtractor.extractCandidates(
            packageName = CHROME_PACKAGE,
            nodes = listOf(
                node("전체", 80, 180, 170, 230),
                node("시발 진짜 심한 욕 아니야? : r/korea", 120, 520, 820, 600),
                node("시발은 많은 경우에 심각한 욕설이야, 상황에 따라 달라.", 120, 610, 920, 700),
                node("https://ko.wiktionary.org › wiki › 시발", 120, 730, 720, 770),
                node("Tlqkf 발음", 120, 840, 520, 910)
            )
        )

        assertTrue(candidates.any { it.rawText == "시발 진짜 심한 욕 아니야? : r/korea" })
        assertTrue(candidates.any { it.rawText.startsWith("시발은 많은 경우에") })
        assertTrue(candidates.any { it.rawText == "Tlqkf 발음" })
        assertFalse(candidates.any { it.rawText == "전체" })
        assertFalse(candidates.any { it.rawText.startsWith("https://") })
        assertTrue(candidates.all { it.source == CandidateSource.ACCESSIBILITY_TEXT || it.source == CandidateSource.ACCESSIBILITY_TEXT_WITH_OCR_GEOMETRY })
        assertTrue(candidates.all { it.backendSourceId.orEmpty().startsWith("android-accessibility-browser:") })
    }

    @Test
    fun extractCandidates_addsKeyboardRangeCandidateForGenericNonBrowserTextWithoutRemovingContextCandidate() {
        val candidates = ScreenTextCandidateExtractor.extractCandidates(
            packageName = GENERIC_PACKAGE,
            nodes = listOf(
                node("Tlqkf 티셔츠 / Qudtls 뜻 / scripts / warp theme", 20, 300, 920, 370)
            )
        )

        assertTrue(candidates.any { it.rawText == "Tlqkf 티셔츠 / Qudtls 뜻 / scripts / warp theme" })
        assertTrue(candidates.any { it.rawText == "tlqkf" && it.source == CandidateSource.ACCESSIBILITY_TEXT_WITH_OCR_GEOMETRY })
        assertTrue(candidates.any { it.rawText == "qudtls" && it.source == CandidateSource.ACCESSIBILITY_TEXT_WITH_OCR_GEOMETRY })
        assertFalse(candidates.any { it.rawText == "scripts" })
        assertFalse(candidates.any { it.rawText == "warp theme" })
    }

    @Test
    fun extractCandidates_doesNotCreateEstimatedRangeCandidateInsideFullWidthChromeRows() {
        val candidates = ScreenTextCandidateExtractor.extractCandidates(
            packageName = CHROME_PACKAGE,
            nodes = listOf(
                node("Tlqkf 티셔츠", 0, 900, 680, 970)
            )
        )

        assertTrue(candidates.any { it.rawText == "Tlqkf 티셔츠" })
        assertTrue(candidates.single().backendSourceId.orEmpty().startsWith("android-accessibility-browser:"))
        assertFalse(candidates.any {
            it.backendSourceId.orEmpty().startsWith("android-accessibility-range")
        })
    }

    @Test
    fun extractCandidates_rejectsBrowserUrlLikeTextEvenWhenItContainsHarmfulQuery() {
        val candidates = ScreenTextCandidateExtractor.extractCandidates(
            packageName = CHROME_PACKAGE,
            nodes = listOf(
                node("google.com/search?q=시발", 80, 36, 520, 92),
                node("https://www.google.com/search?q=tlqkf", 80, 96, 720, 152),
                node("시발 뭐하는 거야", 80, 340, 620, 410)
            )
        )

        assertFalse(candidates.any { it.rawText.contains("google.com/search") })
        assertEquals(listOf("시발 뭐하는 거야"), candidates.map { it.rawText })
    }

    @Test
    fun extractCandidates_rejectsBrowserAddressEditTextUrlEvenWhenItLooksLikeUserInput() {
        val candidates = ScreenTextCandidateExtractor.extractCandidates(
            packageName = CHROME_PACKAGE,
            nodes = listOf(
                node(
                    text = "google.com/search?q=시발",
                    left = 80,
                    top = 36,
                    right = 520,
                    bottom = 92,
                    className = "android.widget.EditText"
                ),
                node("시발 뭐하는 거야", 80, 520, 620, 590)
            )
        )

        assertFalse(candidates.any { it.rawText.contains("google.com/search") })
        assertEquals(listOf("시발 뭐하는 거야"), candidates.map { it.rawText })
        assertEquals(CandidateRole.TITLE, candidates.single().role)
    }

    @Test
    fun extractCandidates_rejectsBrowserTopSearchEditTextEvenWhenItContainsHarmfulText() {
        val candidates = ScreenTextCandidateExtractor.extractCandidates(
            packageName = CHROME_PACKAGE,
            nodes = listOf(
                node(
                    text = "시발",
                    left = 34,
                    top = 248,
                    right = 1048,
                    bottom = 330,
                    className = "android.widget.EditText"
                ),
                node("시발 진짜 심한 욕 아니야? : r/korea", 42, 520, 1042, 600)
            )
        )

        assertFalse(candidates.any { it.role == CandidateRole.USER_INPUT })
        assertTrue(candidates.any { it.rawText == "시발 진짜 심한 욕 아니야? : r/korea" })
    }

    @Test
    fun extractCandidates_rejectsBrowserTopSearchTextWhenRoleIsMisclassified() {
        val candidates = ScreenTextCandidateExtractor.extractCandidates(
            packageName = CHROME_PACKAGE,
            nodes = listOf(
                node(
                    text = "시발",
                    left = 96,
                    top = 252,
                    right = 240,
                    bottom = 318,
                    className = "android.widget.TextView"
                ),
                node(
                    text = "시발은 많은 경우에 심각한 욕설이야, 상황에 따라 달라.",
                    left = 42,
                    top = 520,
                    right = 1042,
                    bottom = 600,
                    className = "android.widget.TextView"
                )
            )
        )

        assertFalse(candidates.any { it.rawText == "시발" && it.screenRect.top < 420 })
        assertTrue(candidates.any { it.rawText.startsWith("시발은 많은 경우에") })
    }

    @Test
    fun extractCandidates_rejectsLowerMobileChromeSearchBoxButKeepsFirstResult() {
        val candidates = ScreenTextCandidateExtractor.extractCandidates(
            packageName = CHROME_PACKAGE,
            nodes = listOf(
                node(
                    text = "씨발",
                    left = 44,
                    top = 410,
                    right = 980,
                    bottom = 486,
                    className = "android.widget.TextView"
                ),
                node(
                    text = "씨발 진짜 심한 욕 아니야? : r/korea",
                    left = 42,
                    top = 540,
                    right = 1042,
                    bottom = 620,
                    className = "android.widget.TextView"
                )
            )
        )

        assertFalse(candidates.any { it.rawText == "씨발" && it.screenRect.top < 500 })
        assertTrue(candidates.any { it.rawText == "씨발 진짜 심한 욕 아니야? : r/korea" })
    }

    @Test
    fun extractCandidates_doesNotCreateRangeCandidateFromTopChromeControlText() {
        val candidates = ScreenTextCandidateExtractor.extractCandidates(
            packageName = CHROME_PACKAGE,
            nodes = listOf(
                node("Tlqkf 티셔츠", 0, 72, 680, 142),
                node("Tlqkf 발음", 0, 900, 680, 970)
            )
        )

        val topRangeCandidates = candidates.filter {
            it.backendSourceId.orEmpty().startsWith("android-accessibility-range") &&
                it.screenRect.top < 180
        }
        val contentRangeCandidates = candidates.filter {
            it.backendSourceId.orEmpty().startsWith("android-accessibility-range") &&
                it.rawText == "tlqkf" &&
                it.screenRect.top >= 180
        }

        assertTrue(topRangeCandidates.isEmpty())
        assertTrue(contentRangeCandidates.isEmpty())
    }

    @Test
    fun extractCandidates_doesNotCreateEstimatedRangeCandidateForBrowserUserInput() {
        val candidates = ScreenTextCandidateExtractor.extractCandidates(
            packageName = CHROME_PACKAGE,
            nodes = listOf(
                node(
                    text = "시발 뭐하는 거야",
                    left = 44,
                    top = 620,
                    right = 980,
                    bottom = 690,
                    className = "android.widget.EditText"
                )
            )
        )

        assertTrue(candidates.any {
            it.rawText == "시발 뭐하는 거야" &&
                it.role == CandidateRole.USER_INPUT &&
                it.backendSourceId == "android-accessibility-browser:user_input"
        })
        assertFalse(candidates.any {
            it.backendSourceId.orEmpty().startsWith("android-accessibility-range")
        })
    }

    @Test
    fun extractCandidates_treatsGoogleAppSearchAsBrowserLikeGeometry() {
        val candidates = ScreenTextCandidateExtractor.extractCandidates(
            packageName = GOOGLE_APP_PACKAGE,
            nodes = listOf(
                node(
                    text = "Tlqkf 발음",
                    left = 20,
                    top = 900,
                    right = 680,
                    bottom = 970,
                    packageName = GOOGLE_APP_PACKAGE
                )
            )
        )

        assertTrue(candidates.any { it.rawText == "Tlqkf 발음" })
        assertTrue(candidates.all {
            it.backendSourceId.orEmpty().startsWith("android-accessibility-browser:")
        })
        assertFalse(candidates.any {
            it.backendSourceId.orEmpty().startsWith("android-accessibility-range")
        })
    }

    @Test
    fun extractCandidates_doesNotCreateRangeCandidateFromRootLikeBounds() {
        val candidates = ScreenTextCandidateExtractor.extractCandidates(
            packageName = CHROME_PACKAGE,
            nodes = listOf(
                node("Google 전체 이미지 Tlqkf 티셔츠 관련 검색어", 0, 220, 1080, 420)
            )
        )

        assertFalse(candidates.any {
            it.backendSourceId.orEmpty().startsWith("android-accessibility-range")
        })
    }

    @Test
    fun extractCandidates_doesNotCreateRangeCandidateFromLongBrowserParagraphs() {
        val longAiOverviewLikeText =
            "'씨발'은 한국어에서 가장 대표적이고 널리 쓰이는 비속어(욕설)로, " +
                "영어의 'fuck'과 유사하게 강한 불만과 분노를 표현할 때 사용됩니다."

        val candidates = ScreenTextCandidateExtractor.extractCandidates(
            packageName = CHROME_PACKAGE,
            nodes = listOf(
                node(longAiOverviewLikeText, 42, 360, 1042, 610)
            )
        )

        assertTrue(candidates.any { it.rawText == longAiOverviewLikeText })
        assertFalse(candidates.any {
            it.backendSourceId.orEmpty().startsWith("android-accessibility-range")
        })
    }

    @Test
    fun extractCandidates_doesNotSplitExactKoreanWordsOutOfSafeContext() {
        val candidates = ScreenTextCandidateExtractor.extractCandidates(
            packageName = CHROME_PACKAGE,
            nodes = listOf(
                node("카필 시발(Kapil Sibal)은 인도의 변호사이자 정치인이다.", 20, 300, 1020, 370),
                node("시발 - 위키낱말사전", 20, 390, 640, 450)
            )
        )

        assertTrue(candidates.any { it.rawText.startsWith("카필 시발") })
        assertTrue(candidates.any { it.rawText == "시발 - 위키낱말사전" })
        assertFalse(candidates.any { it.rawText == "시발" && it.backendSourceId.orEmpty().startsWith("android-accessibility-range") })
    }

    @Test
    fun extractCandidates_preservesYoutubeSpecificExtractorPath() {
        val candidates = ScreenTextCandidateExtractor.extractCandidates(
            packageName = YOUTUBE_PACKAGE,
            nodes = listOf(
                contentDescriptionNode(
                    "What is 'Tlqkf'?, Contemporary Korean Slang, 40K views, 5 years ago - play video",
                    0,
                    260,
                    807,
                    620
                )
            )
        )

        assertEquals(listOf("tlqkf"), candidates.map { it.rawText })
        assertTrue(candidates.single().backendSourceId.orEmpty().startsWith("youtube-visual-range:"))
    }

    @Test
    fun extractCandidates_marksYoutubeCommentBodiesAsStableCommentSources() {
        val candidates = ScreenTextCandidateExtractor.extractCandidates(
            packageName = YOUTUBE_PACKAGE,
            nodes = listOf(
                node(
                    text = "sampleuser",
                    left = 120,
                    top = 520,
                    right = 420,
                    bottom = 560,
                    packageName = YOUTUBE_PACKAGE
                ),
                node(
                    text = "7 months ago",
                    left = 430,
                    top = 520,
                    right = 620,
                    bottom = 560,
                    packageName = YOUTUBE_PACKAGE
                ),
                node(
                    text = "tlqkf 뭐냐 진짜",
                    left = 120,
                    top = 580,
                    right = 720,
                    bottom = 640,
                    packageName = YOUTUBE_PACKAGE
                )
            )
        )

        val comment = candidates.single { it.rawText == "tlqkf 뭐냐 진짜" }

        assertEquals(CandidateRole.CONTENT, comment.role)
        assertEquals("android-accessibility-comment:youtube", comment.backendSourceId)
    }

    @Test
    fun extractCandidates_rejectsGenericNavigationAndCounters() {
        val candidates = ScreenTextCandidateExtractor.extractCandidates(
            packageName = CHROME_PACKAGE,
            nodes = listOf(
                node("All", 20, 100, 100, 150),
                node("Videos", 120, 100, 220, 150),
                node("8.4K", 20, 200, 120, 240),
                node("6일 전", 20, 250, 120, 290),
                node("개새끼 뭐하는 거야", 20, 350, 620, 420)
            )
        )

        assertEquals(listOf("개새끼 뭐하는 거야"), candidates.map { it.rawText })
    }

    private fun node(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        className: String = "android.widget.TextView",
        packageName: String = CHROME_PACKAGE
    ): ParsedTextNode {
        return ParsedTextNode(
            packageName = packageName,
            text = text,
            contentDescription = null,
            displayText = text,
            className = className,
            viewIdResourceName = null,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            approxTop = top,
            isVisibleToUser = true
        )
    }

    private fun contentDescriptionNode(
        displayText: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): ParsedTextNode {
        return ParsedTextNode(
            packageName = YOUTUBE_PACKAGE,
            text = null,
            contentDescription = displayText,
            displayText = displayText,
            className = "android.view.View",
            viewIdResourceName = null,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            approxTop = top,
            isVisibleToUser = true
        )
    }

    private companion object {
        const val CHROME_PACKAGE = "com.android.chrome"
        const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"
        const val GENERIC_PACKAGE = "com.example.reader"
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    }
}
