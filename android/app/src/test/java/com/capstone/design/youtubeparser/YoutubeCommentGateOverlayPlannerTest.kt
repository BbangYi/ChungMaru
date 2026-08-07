package com.capstone.design.youtubeparser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeCommentGateOverlayPlannerTest {

    @Test
    fun buildSpecsFromNodes_gatesEntireVisibleCommentPanelBeforeRowDecision() {
        val specs = YoutubeCommentGateOverlayPlanner.buildSpecsFromNodes(
            nodes = listOf(
                node("댓글 128", 40, 650, 180, 704),
                node("@safe 2분 전", 132, 760, 420, 792),
                node("오늘 영상 너무 좋네요", 132, 810, 780, 858),
                node("@bad 5분 전", 132, 940, 420, 972),
                node("하...씨발..또 다시 보여줘야돼?", 132, 990, 920, 1084),
                node("댓글을 입력하세요...", 48, 1140, 1000, 1200)
            ),
            screenWidth = 1080,
            screenHeight = 2400
        )

        assertEquals(1, specs.size)
        assertTrue(specs.all { it.label == "댓글 검사 중" })
        assertTrue(specs.all { it.debugSource.startsWith("youtube-comment-panel-fast-gate:") })
        assertEquals(0, specs.first().left)
        assertEquals(720, specs.first().top)
        assertEquals(1080, specs.first().width)
        assertEquals(420, specs.first().height)
    }

    @Test
    fun buildSpecsFromNodes_skipsWithoutCommentPanelMarker() {
        val specs = YoutubeCommentGateOverlayPlanner.buildSpecsFromNodes(
            nodes = listOf(
                node("오늘 영상 너무 좋네요", 132, 810, 780, 858),
                node("댓글을 입력하세요...", 48, 1140, 1000, 1200)
            ),
            screenWidth = 1080,
            screenHeight = 2400
        )

        assertTrue(specs.isEmpty())
    }

    @Test
    fun buildBlockedCommentSpecs_prefersWholeCommentBoundsOverNestedEvidenceRow() {
        val specs = YoutubeCommentGateOverlayPlanner.buildBlockedCommentSpecs(
            results = listOf(
                offensiveResult(BoundsRect(132, 810, 920, 916)),
                offensiveResult(BoundsRect(132, 810, 920, 858))
            ),
            screenWidth = 1080,
            screenHeight = 2400
        )

        assertEquals(1, specs.size)
        assertEquals(132, specs.single().left)
        assertEquals(810, specs.single().top)
        assertEquals(788, specs.single().width)
        assertEquals(106, specs.single().height)
    }

    @Test
    fun buildSpecs_usesYoutubeCommentCandidatesBeforeRois() {
        val specs = YoutubeCommentGateOverlayPlanner.buildSpecs(
            visualRoiPlan = VisualTextRoiPlan(
                rois = listOf(roi("youtube-comment-panel", 80, 720, 1040, 880)),
                candidateCount = 1
            ),
            screenCandidates = listOf(
                commentCandidate(
                    text = "하...씨발..또 다시 보여줘야돼? 이게 존나 야마있네",
                    left = 127,
                    top = 1363,
                    right = 1006,
                    bottom = 1677,
                    sourceId = "android-accessibility-comment:@cloudd9619:line:1363"
                )
            ),
            screenWidth = 1080,
            screenHeight = 2400
        )

        assertEquals(1, specs.size)
        assertEquals("댓글 검사 중", specs.single().label)
        assertEquals(127, specs.single().left)
        assertEquals(1363, specs.single().top)
        assertEquals(879, specs.single().width)
        assertTrue(specs.single().debugSource.startsWith("youtube-comment-candidate-gate:"))
    }

    @Test
    fun buildSpecs_usesOnlyYoutubeCommentPanelRois() {
        val specs = YoutubeCommentGateOverlayPlanner.buildSpecs(
            visualRoiPlan = VisualTextRoiPlan(
                rois = listOf(
                    roi("youtube-comment-panel", 80, 720, 1040, 880),
                    roi("youtube-visible-band", 0, 300, 1080, 620),
                    roi("youtube-comment-panel", 80, 900, 1040, 1060)
                ),
                candidateCount = 3
            ),
            screenWidth = 1080,
            screenHeight = 2400
        )

        assertEquals(2, specs.size)
        assertTrue(specs.all { it.label == "댓글 검사 중" })
        assertTrue(specs.all { it.debugSource.startsWith("youtube-comment-panel-gate:") })
        assertEquals(80, specs.first().left)
        assertEquals(720, specs.first().top)
        assertEquals(960, specs.first().width)
    }

    @Test
    fun buildSpecs_rejectsTinyOrOffscreenRegions() {
        val specs = YoutubeCommentGateOverlayPlanner.buildSpecs(
            visualRoiPlan = VisualTextRoiPlan(
                rois = listOf(
                    roi("youtube-comment-panel", 20, 100, 80, 130),
                    roi("youtube-comment-panel", -40, 200, 260, 280)
                ),
                candidateCount = 2
            ),
            screenWidth = 240,
            screenHeight = 480
        )

        assertEquals(1, specs.size)
        assertEquals(0, specs.single().left)
        assertEquals(200, specs.single().top)
    }

    private fun roi(
        source: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): VisualTextRoi {
        return VisualTextRoi(
            boundsInScreen = BoundsRect(left, top, right, bottom),
            source = source,
            priority = 0,
            reason = "test"
        )
    }

    private fun node(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): ParsedTextNode {
        return ParsedTextNode(
            packageName = "com.google.android.youtube",
            text = text,
            contentDescription = null,
            displayText = text,
            className = "android.widget.TextView",
            viewIdResourceName = null,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            approxTop = top,
            isVisibleToUser = true,
            charBoxes = emptyList()
        )
    }

    private fun commentCandidate(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        sourceId: String
    ): ScreenTextCandidate {
        return ScreenTextCandidate(
            id = sourceId,
            packageName = "com.google.android.youtube",
            source = CandidateSource.ACCESSIBILITY_TEXT,
            role = CandidateRole.CONTENT,
            rawText = text,
            screenRect = BoundsRect(left, top, right, bottom),
            backendSourceId = sourceId
        )
    }

    private fun offensiveResult(bounds: BoundsRect): AndroidAnalysisResultItem {
        return AndroidAnalysisResultItem(
            original = "하...씨발..또 다시 보여줘야돼?",
            boundsInScreen = bounds,
            isOffensive = true,
            isProfane = true,
            isToxic = false,
            isHate = false,
            scores = HarmScores(profanity = 1.0),
            evidenceSpans = listOf(EvidenceSpan(text = "씨발", start = 3, end = 5, score = 1.0))
        )
    }
}
