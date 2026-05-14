package com.capstone.design.youtubeparser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProvisionalAccessibilityMaskBuilderTest {

    @Test
    fun buildResponse_createsMaskForDirectYoutubeTitleCandidate() {
        val response = ProvisionalAccessibilityMaskBuilder.buildResponse(
            candidates = listOf(
                candidate(
                    rawText = "What is 'Tlqkf'?_Contemporary Korean Slang",
                    role = CandidateRole.TITLE,
                    bounds = BoundsRect(70, 1060, 640, 1140),
                    backendSourceId = "android-accessibility:youtube_title"
                )
            ),
            timestamp = 123L
        )

        assertNotNull(response)
        val result = response!!.results.single()
        assertEquals("android-accessibility:youtube_title", result.authorId)
        assertEquals(listOf("tlqkf"), result.evidenceSpans.map { it.text })
        assertEquals("What is 'Tlqkf'?_Contemporary Korean Slang", result.original)
    }

    @Test
    fun buildResponse_createsMaskForDirectYoutubeSearchInputCandidate() {
        val response = ProvisionalAccessibilityMaskBuilder.buildResponse(
            candidates = listOf(
                candidate(
                    rawText = "tlqkf",
                    role = CandidateRole.USER_INPUT,
                    bounds = BoundsRect(80, 52, 430, 104),
                    backendSourceId = "android-accessibility:youtube_user_input"
                )
            ),
            timestamp = 123L
        )

        assertNotNull(response)
        val result = response!!.results.single()
        assertEquals("android-accessibility:youtube_user_input", result.authorId)
        assertEquals(listOf("tlqkf"), result.evidenceSpans.map { it.text })
    }

    @Test
    fun buildResponse_createsMaskForDirectCommentCandidate() {
        val response = ProvisionalAccessibilityMaskBuilder.buildResponse(
            candidates = listOf(
                candidate(
                    rawText = "개새끼 뭐하는 거야",
                    role = CandidateRole.CONTENT,
                    bounds = BoundsRect(130, 877, 732, 913),
                    backendSourceId = "android-accessibility-comment:youtube"
                )
            ),
            timestamp = 123L
        )

        assertNotNull(response)
        val result = response!!.results.single()
        assertEquals("android-accessibility-comment:youtube", result.authorId)
        assertEquals(listOf("개새끼"), result.evidenceSpans.map { it.text })
    }

    @Test
    fun buildResponse_keepsEstimatedRangeCandidateWhenRouteAllowsDirectOverlay() {
        val response = ProvisionalAccessibilityMaskBuilder.buildResponse(
            candidates = listOf(
                candidate(
                    rawText = "tlqkf",
                    source = CandidateSource.ACCESSIBILITY_TEXT_WITH_OCR_GEOMETRY,
                    role = CandidateRole.CONTENT,
                    bounds = BoundsRect(40, 900, 160, 940),
                    backendSourceId = "android-accessibility-range:Tlqkf"
                )
            ),
            timestamp = 123L
        )

        assertNotNull(response)
        assertEquals("android-accessibility-range:Tlqkf", response!!.results.single().authorId)
    }

    @Test
    fun buildResponse_excludesLookaheadAndOcrRequiredCandidates() {
        val response = ProvisionalAccessibilityMaskBuilder.buildResponse(
            candidates = listOf(
                candidate(
                    rawText = "Tlqkf 공부법",
                    role = CandidateRole.TITLE,
                    bounds = BoundsRect(20, 1400, 620, 1480),
                    backendSourceId = "android-accessibility-lookahead:android-accessibility:youtube_title"
                ),
                candidate(
                    rawText = "Tlqkf",
                    source = CandidateSource.ACCESSIBILITY_TEXT_WITH_OCR_GEOMETRY,
                    role = CandidateRole.THUMBNAIL_TEXT,
                    bounds = BoundsRect(10, 210, 350, 260),
                    backendSourceId = "youtube-visual-range:Tlqkf"
                )
            ),
            timestamp = 123L
        )

        assertNull(response)
    }

    @Test
    fun buildResponse_excludesBrowserAnalysisOnlyCandidate() {
        val response = ProvisionalAccessibilityMaskBuilder.buildResponse(
            candidates = listOf(
                candidate(
                    packageName = "com.android.chrome",
                    rawText = "Tlqkf meaning",
                    role = CandidateRole.TITLE,
                    bounds = BoundsRect(80, 520, 820, 600),
                    backendSourceId = "android-accessibility-browser:title"
                )
            ),
            timestamp = 123L
        )

        assertNull(response)
    }

    private fun candidate(
        packageName: String = "com.google.android.youtube",
        rawText: String,
        source: CandidateSource = CandidateSource.ACCESSIBILITY_TEXT,
        role: CandidateRole,
        bounds: BoundsRect,
        backendSourceId: String
    ): ScreenTextCandidate {
        return ScreenTextCandidate(
            id = backendSourceId,
            packageName = packageName,
            source = source,
            role = role,
            rawText = rawText,
            screenRect = bounds,
            backendSourceId = backendSourceId
        )
    }
}
