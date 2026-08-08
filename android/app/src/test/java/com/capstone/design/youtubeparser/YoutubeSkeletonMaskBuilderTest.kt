package com.capstone.design.youtubeparser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeSkeletonMaskBuilderTest {

    @Test
    fun buildCommentPreviewLoadingSpec_limitsMaskToCommentBand() {
        val spec = YoutubeSkeletonMaskBuilder.buildCommentPreviewLoadingSpec(
            actionBounds = BoundsRect(
                left = 980,
                top = 920,
                right = 1160,
                bottom = 1020
            ),
            screenWidth = 1200,
            screenHeight = 1920
        )

        requireNotNull(spec)
        assertEquals(0, spec.left)
        assertEquals(1200, spec.width)
        assertTrue(spec.top > 0)
        assertTrue(spec.height <= 461)
        assertEquals(MaskOverlayStyle.LOADING, spec.style)
        assertEquals("youtube-comment-preview-loading", spec.debugSource)
    }

    @Test
    fun build_createsLoadingSpecOnlyForUnknownYoutubeComments() {
        val unknown = candidate(
            text = "처음 보는 댓글입니다",
            bounds = BoundsRect(100, 800, 720, 850),
            sourceId = "android-accessibility-comment:youtube:unknown"
        )
        val safe = candidate(
            text = "이미 안전한 댓글입니다",
            bounds = BoundsRect(100, 880, 720, 930),
            sourceId = "android-accessibility-comment:youtube:safe"
        )

        val plan = YoutubeSkeletonMaskBuilder.build(
            candidates = listOf(unknown, safe),
            cachedResults = listOf(null, safeResult(safe)),
            screenWidth = 1080,
            screenHeight = 1920,
            timestamp = 1L
        )

        assertEquals(1, plan.unknownCount)
        assertEquals(1, plan.safeCacheHitCount)
        assertEquals(1, plan.specs.size)
        val spec = plan.specs.single()
        assertEquals(MaskOverlayStyle.LOADING, spec.style)
        assertEquals(0, spec.left)
        assertEquals(688, spec.top)
        assertEquals(1080, spec.width)
        assertEquals(1232, spec.height)
        assertTrue(!spec.allowScrollTranslation)
        assertEquals("youtube-comment-pane-loading", spec.debugSource)

        val rowLoading = plan.unknownLoadingSpecs.single()
        assertEquals(MaskOverlayStyle.LOADING, rowLoading.style)
        assertEquals(88, rowLoading.left)
        assertEquals(792, rowLoading.top)
        assertEquals(644, rowLoading.width)
        assertEquals(66, rowLoading.height)
        assertTrue(rowLoading.allowScrollTranslation)
        assertEquals("youtube-comment-row-loading:0", rowLoading.debugSource)
    }

    @Test
    fun buildAttachedViewportSpecs_movesBlockedAndUnknownRowsTogether() {
        val harmful = candidate(
            text = "cached harmful comment",
            bounds = BoundsRect(100, 800, 720, 850),
            sourceId = "android-accessibility-comment:youtube:harmful"
        )
        val unknown = candidate(
            text = "new unchecked comment",
            bounds = BoundsRect(100, 900, 720, 960),
            sourceId = "android-accessibility-comment:youtube:unknown"
        )
        val plan = YoutubeSkeletonMaskBuilder.build(
            candidates = listOf(harmful, unknown),
            cachedResults = listOf(harmfulResult(harmful), null),
            screenWidth = 1080,
            screenHeight = 1920,
            timestamp = 1L
        )

        val attached = YoutubeSkeletonMaskBuilder.buildAttachedViewportSpecs(plan)

        assertEquals(2, attached.size)
        assertEquals(listOf(MaskOverlayStyle.BLOCKED, MaskOverlayStyle.LOADING), attached.map { it.style })
        assertTrue(attached.all { it.allowScrollTranslation })
        assertTrue(attached.none { it.debugSource.startsWith("youtube-comment-pane-") })

        val translated = AndroidMaskOverlayPlanner.translateSpecs(
            specs = attached,
            deltaX = 0,
            deltaY = -120,
            screenWidth = 1080,
            screenHeight = 1920
        )

        assertEquals(attached.map { it.top - 120 }, translated.map { it.top })
        assertEquals(attached.map { it.style }, translated.map { it.style })
    }

    @Test
    fun build_rendersHarmfulCachedCommentWithoutLoadingSkeleton() {
        val harmful = candidate(
            text = "나쁜 댓글",
            bounds = BoundsRect(100, 800, 720, 850),
            sourceId = "android-accessibility-comment:youtube:harmful"
        )

        val plan = YoutubeSkeletonMaskBuilder.build(
            candidates = listOf(harmful),
            cachedResults = listOf(harmfulResult(harmful)),
            screenWidth = 1080,
            screenHeight = 1920,
            timestamp = 1L
        )

        assertEquals(0, plan.unknownCount)
        assertEquals(1, plan.harmfulCacheHitCount)
        assertTrue(plan.specs.isNotEmpty())
        assertTrue(plan.specs.all { it.style == MaskOverlayStyle.BLOCKED })
        val spec = plan.specs.single()
        assertEquals(88, spec.left)
        assertEquals(792, spec.top)
        assertEquals(644, spec.width)
        assertEquals(66, spec.height)
        assertTrue(spec.allowScrollTranslation)
        assertEquals("youtube-comment-blocked-cache:0", spec.debugSource)
    }

    @Test
    fun build_prefersLoadingPaneWhileUnknownCommentsRemain() {
        val harmful = candidate(
            text = "bad cached comment",
            bounds = BoundsRect(100, 800, 720, 850),
            sourceId = "android-accessibility-comment:youtube:harmful"
        )
        val unknown = candidate(
            text = "new unchecked comment",
            bounds = BoundsRect(100, 900, 720, 960),
            sourceId = "android-accessibility-comment:youtube:unknown"
        )

        val plan = YoutubeSkeletonMaskBuilder.build(
            candidates = listOf(harmful, unknown),
            cachedResults = listOf(harmfulResult(harmful), null),
            screenWidth = 1080,
            screenHeight = 1920,
            timestamp = 1L
        )

        assertEquals(1, plan.unknownCount)
        assertEquals(1, plan.harmfulCacheHitCount)
        assertEquals(1, plan.specs.size)
        val spec = plan.specs.single()
        assertEquals(MaskOverlayStyle.LOADING, spec.style)
        assertTrue(!spec.allowScrollTranslation)
        assertEquals("youtube-comment-pane-loading", spec.debugSource)
        assertEquals(1, plan.cachedHarmfulSpecs.size)
        assertEquals(MaskOverlayStyle.BLOCKED, plan.cachedHarmfulSpecs.single().style)
    }

    @Test
    fun build_keepsTrustedHarmfulCacheWhenVisualPanelBandsMissLowerComment() {
        val lowerComment = candidate(
            text = "bad text below visual bands",
            bounds = BoundsRect(100, 1600, 720, 1660),
            sourceId = "android-accessibility-comment:youtube:harmful-lower-row"
        )

        val plan = YoutubeSkeletonMaskBuilder.build(
            candidates = listOf(lowerComment),
            cachedResults = listOf(harmfulResult(lowerComment)),
            screenWidth = 1080,
            screenHeight = 1920,
            timestamp = 1L,
            commentPanelBounds = listOf(BoundsRect(80, 800, 1000, 1200))
        )

        assertEquals(1, plan.harmfulCacheHitCount)
        assertEquals(1, plan.specs.size)
        assertEquals(MaskOverlayStyle.BLOCKED, plan.specs.single().style)
    }

    @Test
    fun build_keepsTrustedLowerHarmfulFallbackBehindUnknownLoadingPane() {
        val lowerComment = candidate(
            text = "bad text below visual bands",
            bounds = BoundsRect(100, 1600, 720, 1660),
            sourceId = "android-accessibility-comment:youtube:harmful-lower-row"
        )
        val unknown = candidate(
            text = "new unchecked comment",
            bounds = BoundsRect(120, 900, 720, 960),
            sourceId = "android-accessibility-comment:youtube:unknown"
        )

        val plan = YoutubeSkeletonMaskBuilder.build(
            candidates = listOf(lowerComment, unknown),
            cachedResults = listOf(harmfulResult(lowerComment), null),
            screenWidth = 1080,
            screenHeight = 1920,
            timestamp = 1L,
            commentPanelBounds = listOf(BoundsRect(80, 800, 1000, 1200))
        )

        assertEquals(1, plan.harmfulCacheHitCount)
        assertEquals(1, plan.unknownCount)
        assertEquals(1, plan.specs.size)
        val spec = plan.specs.single()
        assertEquals(MaskOverlayStyle.LOADING, spec.style)
        assertTrue(!spec.allowScrollTranslation)
        assertEquals("youtube-comment-pane-loading", spec.debugSource)
        assertEquals(1, plan.cachedHarmfulSpecs.size)
        assertEquals("youtube-comment-blocked-cache:0", plan.cachedHarmfulSpecs.single().debugSource)
    }

    @Test
    fun buildCommentContentSpecsFromResults_keepsMaskInsideCommentBounds() {
        val result = harmfulResult(
            candidate(
                text = "tlqkf",
                bounds = BoundsRect(120, 900, 620, 980),
                sourceId = "ocr:youtube-comment-panel:outside"
            )
        )

        val specs = YoutubeSkeletonMaskBuilder.buildCommentContentSpecsFromResults(
            results = listOf(result),
            commentPanelBounds = listOf(BoundsRect(80, 860, 1000, 1100)),
            screenWidth = 1080,
            screenHeight = 1920,
            style = MaskOverlayStyle.BLOCKED,
            label = "comment-blocked",
            debugSource = "youtube-comment-blocked-model"
        )

        val spec = specs.single()
        assertEquals(108, spec.left)
        assertEquals(892, spec.top)
        assertEquals(524, spec.width)
        assertEquals(96, spec.height)
        assertTrue(spec.allowScrollTranslation)
        assertEquals("youtube-comment-blocked-model:0", spec.debugSource)
    }

    @Test
    fun buildCommentContentSpecsFromResults_rejectsBoundsOutsideCommentPanel() {
        val result = harmfulResult(
            candidate(
                text = "tlqkf",
                bounds = BoundsRect(120, 1300, 620, 1380),
                sourceId = "ocr:youtube-comment-panel:outside"
            )
        )

        val specs = YoutubeSkeletonMaskBuilder.buildCommentContentSpecsFromResults(
            results = listOf(result),
            commentPanelBounds = listOf(BoundsRect(80, 860, 1000, 1100)),
            screenWidth = 1080,
            screenHeight = 1920,
            style = MaskOverlayStyle.BLOCKED,
            label = "comment-blocked",
            debugSource = "youtube-comment-blocked-model"
        )

        assertTrue(specs.isEmpty())
    }

    @Test
    fun buildCommentContentSpecsFromResults_keepsTrustedCommentBelowVisualPanelBands() {
        val result = harmfulResult(
            candidate(
                text = "tlqkf",
                bounds = BoundsRect(120, 1300, 620, 1380),
                sourceId = "android-accessibility-comment:youtube:@lower-user"
            )
        )

        val specs = YoutubeSkeletonMaskBuilder.buildCommentContentSpecsFromResults(
            results = listOf(result),
            commentPanelBounds = listOf(BoundsRect(80, 860, 1000, 1100)),
            screenWidth = 1080,
            screenHeight = 1920,
            style = MaskOverlayStyle.BLOCKED,
            label = "comment-blocked",
            debugSource = "youtube-comment-blocked-model"
        )

        assertEquals(1, specs.size)
        assertEquals(1292, specs.single().top)
    }

    @Test
    fun buildCommentContentSpecsFromResults_rejectsYoutubeTitleWithoutConfirmedCommentPanel() {
        val result = harmfulResult(
            candidate(
                text = "harmful-looking video title",
                bounds = BoundsRect(180, 2380, 1188, 2500),
                sourceId = "android-accessibility:youtube_title"
            )
        )

        val specs = YoutubeSkeletonMaskBuilder.buildCommentContentSpecsFromResults(
            results = listOf(result),
            commentPanelBounds = emptyList(),
            screenWidth = 1344,
            screenHeight = 2992,
            style = MaskOverlayStyle.BLOCKED,
            label = "comment-blocked",
            debugSource = "youtube-comment-blocked-model"
        )

        assertTrue(specs.isEmpty())
    }

    @Test
    fun buildCommentContentSpecsFromResults_rejectsPanelSizedBounds() {
        val result = harmfulResult(
            candidate(
                text = "tlqkf",
                bounds = BoundsRect(0, 500, 1080, 1800),
                sourceId = "android-accessibility-comment:youtube:@user"
            )
        )

        val specs = YoutubeSkeletonMaskBuilder.buildCommentContentSpecsFromResults(
            results = listOf(result),
            commentPanelBounds = listOf(BoundsRect(0, 500, 1080, 1800)),
            screenWidth = 1080,
            screenHeight = 1920,
            style = MaskOverlayStyle.BLOCKED,
            label = "comment-blocked",
            debugSource = "youtube-comment-blocked-model"
        )

        assertTrue(specs.isEmpty())
    }

    @Test
    fun stabilizeLoadingPaneSpec_preservesEarliestPanelTopDuringScroll() {
        val previous = MaskOverlaySpec(
            left = 0,
            top = 637,
            width = 1344,
            height = 2355,
            label = "comments-loading",
            allowScrollTranslation = false,
            debugSource = "previous",
            style = MaskOverlayStyle.LOADING
        )
        val current = MaskOverlaySpec(
            left = 0,
            top = 851,
            width = 1344,
            height = 2141,
            label = "comments-loading",
            allowScrollTranslation = false,
            debugSource = "current",
            style = MaskOverlayStyle.LOADING
        )

        val stabilized = YoutubeSkeletonMaskBuilder.stabilizeLoadingPaneSpec(
            previousSpec = previous,
            currentSpec = current,
            screenWidth = 1344,
            screenHeight = 2992
        )

        assertEquals(0, stabilized.left)
        assertEquals(637, stabilized.top)
        assertEquals(1344, stabilized.width)
        assertEquals(2355, stabilized.height)
        assertEquals("current", stabilized.debugSource)
    }
    @Test
    fun buildCommentContentSpecsFromResults_keepsClippedCommentBelowHeader() {
        val result = harmfulResult(
            candidate(
                text = "tlqkf clipped harmful comment",
                bounds = BoundsRect(96, 630, 1260, 776),
                sourceId = "android-accessibility-comment:youtube:clipped:harmful"
            )
        )

        val specs = YoutubeSkeletonMaskBuilder.buildCommentContentSpecsFromResults(
            results = listOf(result),
            commentPanelBounds = emptyList(),
            screenWidth = 1344,
            screenHeight = 2992,
            style = MaskOverlayStyle.BLOCKED,
            label = "comment-blocked",
            debugSource = "youtube-comment-blocked-model"
        )

        val spec = specs.single()
        assertEquals(84, spec.left)
        assertEquals(622, spec.top)
        assertEquals(1188, spec.width)
        assertEquals(162, spec.height)
    }
    @Test
    fun mergeCommentSpecs_dropsNearContainedVisualDuplicate() {
        val exact = maskSpec(left = 84, top = 721, width = 1188, height = 188, source = "exact")
        val visual = maskSpec(left = 80, top = 757, width = 138, height = 63, source = "visual")

        val merged = YoutubeSkeletonMaskBuilder.mergeCommentSpecs(
            primarySpecs = listOf(exact),
            supplementalSpecs = listOf(visual)
        )

        assertEquals(listOf(exact), merged)
    }

    @Test
    fun mergeCommentSpecs_keepsDisjointVisualComment() {
        val exact = maskSpec(left = 84, top = 721, width = 1188, height = 188, source = "exact")
        val visual = maskSpec(left = 80, top = 1100, width = 700, height = 120, source = "visual")

        val merged = YoutubeSkeletonMaskBuilder.mergeCommentSpecs(
            primarySpecs = listOf(exact),
            supplementalSpecs = listOf(visual)
        )

        assertEquals(listOf(exact, visual), merged)
    }
    @Test
    fun buildNativeCommentPaneSpec_usesExactYoutubeCommentContentBounds() {
        val spec = YoutubeSkeletonMaskBuilder.buildNativeCommentPaneSpec(
            contentBounds = BoundsRect(0, 1218, 1344, 2920),
            commentMarkerBounds = BoundsRect(1056, 1071, 1200, 1215),
            screenWidth = 1344,
            screenHeight = 2992
        )

        requireNotNull(spec)
        assertEquals(0, spec.left)
        assertEquals(1218, spec.top)
        assertEquals(1344, spec.width)
        assertEquals(1702, spec.height)
        assertEquals(MaskOverlayStyle.LOADING, spec.style)
        assertTrue(!spec.allowScrollTranslation)
    }

    @Test
    fun buildNativeCommentPaneSpec_rejectsUnrelatedFullScreenPanel() {
        val spec = YoutubeSkeletonMaskBuilder.buildNativeCommentPaneSpec(
            contentBounds = BoundsRect(0, 0, 1344, 2992),
            commentMarkerBounds = BoundsRect(1056, 1071, 1200, 1215),
            screenWidth = 1344,
            screenHeight = 2992
        )

        assertTrue(spec == null)
    }

    @Test
    fun buildNativeCommentPaneSpec_requiresMarkerImmediatelyAboveContent() {
        val spec = YoutubeSkeletonMaskBuilder.buildNativeCommentPaneSpec(
            contentBounds = BoundsRect(0, 1218, 1344, 2920),
            commentMarkerBounds = BoundsRect(1056, 700, 1200, 850),
            screenWidth = 1344,
            screenHeight = 2992
        )

        assertTrue(spec == null)
    }
    private fun candidate(
        text: String,
        bounds: BoundsRect,
        sourceId: String
    ): ScreenTextCandidate {
        return ScreenTextCandidate(
            id = sourceId,
            packageName = "com.google.android.youtube",
            source = CandidateSource.ACCESSIBILITY_TEXT,
            role = CandidateRole.CONTENT,
            rawText = text,
            screenRect = bounds,
            backendSourceId = sourceId
        )
    }

    private fun maskSpec(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        source: String
    ): MaskOverlaySpec {
        return MaskOverlaySpec(
            left = left,
            top = top,
            width = width,
            height = height,
            label = "comment-blocked",
            debugSource = source,
            style = MaskOverlayStyle.BLOCKED
        )
    }
    private fun safeResult(candidate: ScreenTextCandidate): AndroidAnalysisResultItem {
        return AndroidAnalysisResultItem(
            original = candidate.rawText,
            boundsInScreen = candidate.screenRect,
            authorId = candidate.backendSourceId,
            isOffensive = false,
            isProfane = false,
            isToxic = false,
            isHate = false,
            scores = HarmScores(),
            evidenceSpans = emptyList()
        )
    }

    private fun harmfulResult(candidate: ScreenTextCandidate): AndroidAnalysisResultItem {
        return AndroidAnalysisResultItem(
            original = candidate.rawText,
            boundsInScreen = candidate.screenRect,
            authorId = candidate.backendSourceId,
            isOffensive = true,
            isProfane = true,
            isToxic = false,
            isHate = false,
            scores = HarmScores(profanity = 0.9),
            evidenceSpans = emptyList()
        )
    }
}