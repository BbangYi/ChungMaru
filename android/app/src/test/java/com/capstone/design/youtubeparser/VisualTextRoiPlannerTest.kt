package com.capstone.design.youtubeparser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualTextRoiPlannerTest {

    @Test
    fun planFromNodes_selectsTopVisibleCompositeCardsAndLimitsCount() {
        val nodes = (0 until 8).map { index ->
            contentDescriptionNode(
                displayText = "시발 자동자, 채널 $index, 조회수 ${index + 1}만회, ${index + 1}일 전 - 동영상 재생",
                left = 24,
                top = 180 + index * 150,
                right = 330,
                bottom = 310 + index * 150
            )
        }

        val rois = VisualTextRoiPlanner.planFromNodes(
            nodes = nodes,
            screenWidth = 720,
            screenHeight = 1280
        )

        assertEquals(6, rois.size)
        assertTrue(rois.all { it.source == "youtube-composite-card" })
        assertEquals(listOf(174, 324, 474, 624, 774, 924), rois.map { it.boundsInScreen.top })
    }

    @Test
    fun buildPlanFromNodes_keepsCandidateCountSeparateFromSelectedRois() {
        val plan = VisualTextRoiPlanner.buildPlanFromNodes(
            nodes = (0 until 6).map { index ->
                contentDescriptionNode(
                    displayText = "시발 자동자, 채널 $index, 조회수 ${index + 1}만회, ${index + 1}일 전 - 동영상 재생",
                    left = 24,
                    top = 180 + index * 140,
                    right = 330,
                    bottom = 300 + index * 140
                )
            },
            screenWidth = 720,
            screenHeight = 1280
        )

        assertEquals(6, plan.candidateCount)
        assertEquals(6, plan.rois.size)
    }

    @Test
    fun planFromNodes_rejectsFullscreenAndSystemSizedRegions() {
        val rois = VisualTextRoiPlanner.planFromNodes(
            nodes = listOf(
                contentDescriptionNode(
                    displayText = "시발 자동자, 조회수 1만회, 1일 전 - 동영상 재생",
                    left = 0,
                    top = 0,
                    right = 720,
                    bottom = 900
                ),
                contentDescriptionNode(
                    displayText = "개새끼 공부법, 조회수 2만회, 2일 전 - 동영상 재생",
                    left = 0,
                    top = 1,
                    right = 720,
                    bottom = 480
                )
            ),
            screenWidth = 720,
            screenHeight = 1280
        )

        assertTrue(rois.isEmpty())
    }

    @Test
    fun planFromNodes_sortsByPriorityThenPosition() {
        val generic = contentDescriptionNode(
            displayText = "이미지 안에 있는 긴 테스트 문구입니다",
            left = 20,
            top = 260,
            right = 280,
            bottom = 370,
            className = "android.widget.ImageView"
        )
        val youtube = contentDescriptionNode(
            displayText = "시발 자동자, 채널, 조회수 1만회, 1일 전 - 동영상 재생",
            left = 360,
            top = 300,
            right = 650,
            bottom = 460
        )

        val rois = VisualTextRoiPlanner.planFromNodes(
            nodes = listOf(generic, youtube),
            screenWidth = 720,
            screenHeight = 1280
        )

        assertEquals(listOf("youtube-composite-card", "generic-visual-region"), rois.map { it.source })
    }

    @Test
    fun planFromNodes_keepsLargeAnalyzableCardEvenWithoutMetadataSuffix() {
        val rois = VisualTextRoiPlanner.planFromNodes(
            nodes = listOf(
                contentDescriptionNode(
                    displayText = "What is 'Tlqkf'?_Contemporary Korean Slang",
                    left = 0,
                    top = 246,
                    right = 807,
                    bottom = 708
                )
            ),
            screenWidth = 807,
            screenHeight = 1792
        )

        assertEquals(1, rois.size)
        assertEquals("youtube-composite-card", rois.single().source)
    }

    @Test
    fun planFromNodes_dropsTopSearchControlVisualRegions() {
        val rois = VisualTextRoiPlanner.planFromNodes(
            nodes = listOf(
                contentDescriptionNode(
                    displayText = "tlqkf",
                    left = 0,
                    top = 57,
                    right = 153,
                    bottom = 195,
                    className = "android.widget.ImageView"
                ),
                contentDescriptionNode(
                    displayText = "What is 'Tlqkf'?_Contemporary Korean Slang",
                    left = 0,
                    top = 246,
                    right = 807,
                    bottom = 708
                )
            ),
            screenWidth = 807,
            screenHeight = 1792
        )

        assertEquals(1, rois.size)
        assertEquals(246 - 6, rois.single().boundsInScreen.top)
        assertEquals("youtube-composite-card", rois.single().source)
    }

    @Test
    fun planFromNodes_keepsTopHeroYoutubeCompositeCardBelowAppBar() {
        val rois = VisualTextRoiPlanner.planFromNodes(
            nodes = listOf(
                contentDescriptionNode(
                    displayText = "Tlqkf 또 다시 보여줘야돼!!!, 세모플, 조회수 91만회, 7개월 전 - 동영상 재생",
                    left = 0,
                    top = 108,
                    right = 681,
                    bottom = 484
                )
            ),
            screenWidth = 681,
            screenHeight = 1454
        )

        assertEquals(1, rois.size)
        assertEquals("youtube-composite-card", rois.single().source)
        assertEquals(102, rois.single().boundsInScreen.top)
    }

    @Test
    fun planFromNodes_doesNotCreateGenericVisualRoisForBrowserPackages() {
        val rois = VisualTextRoiPlanner.planFromNodes(
            nodes = listOf(
                contentDescriptionNode(
                    displayText = "시발 검색 결과 이미지 설명",
                    left = 20,
                    top = 260,
                    right = 660,
                    bottom = 460,
                    className = "android.widget.ImageView",
                    packageName = "com.android.chrome"
                )
            ),
            screenWidth = 720,
            screenHeight = 1280
        )

        assertTrue(rois.isEmpty())
    }

    @Test
    fun planFromNodes_addsBrowserTextNodeRoiForChromeHarmfulVisibleText() {
        val rois = VisualTextRoiPlanner.planFromNodes(
            nodes = listOf(
                textNode(
                    displayText = "많이 답답하시거나 화가 나는 일이 있으셨나요? 시발 문서 설명처럼 표현하셨을 텐데요.",
                    left = 34,
                    top = 150,
                    right = 660,
                    bottom = 310,
                    packageName = "com.android.chrome"
                )
            ),
            screenWidth = 720,
            screenHeight = 1280
        )

        assertEquals(1, rois.size)
        assertEquals("browser-text-node", rois.single().source)
        assertEquals("browser-accessibility-text-hit", rois.single().reason)
        assertTrue(rois.single().boundsInScreen.left <= 34)
        assertTrue(rois.single().boundsInScreen.bottom >= 310)
    }

    @Test
    fun planFromNodes_skipsBrowserEditTextRoiBecauseAccessibilityHandlesInput() {
        val rois = VisualTextRoiPlanner.planFromNodes(
            nodes = listOf(
                textNode(
                    displayText = "시발",
                    left = 80,
                    top = 92,
                    right = 610,
                    bottom = 154,
                    packageName = "com.android.chrome",
                    className = "android.widget.EditText",
                    viewIdResourceName = "com.android.chrome:id/search_box_text"
                )
            ),
            screenWidth = 720,
            screenHeight = 1280
        )

        assertTrue(rois.isEmpty())
    }

    @Test
    fun planFromNodes_skipsBrowserTextNodeRoiWhenExactCharBoxesCanMaskImmediately() {
        val text = "시발의 어원과 용례에 대한 설명입니다."
        val rois = VisualTextRoiPlanner.planFromNodes(
            nodes = listOf(
                textNode(
                    displayText = text,
                    left = 28,
                    top = 240,
                    right = 690,
                    bottom = 330,
                    packageName = "com.android.chrome",
                    charBoxes = charBoxesFor(
                        text = text,
                        startLeft = 28,
                        top = 240,
                        charWidth = 26,
                        height = 38
                    )
                )
            ),
            screenWidth = 720,
            screenHeight = 1280
        )

        assertTrue(rois.none { it.source == "browser-text-node" })
    }

    @Test
    fun planFromNodes_doesNotCreateGenericVisualRoisForGoogleAppPackages() {
        val rois = VisualTextRoiPlanner.planFromNodes(
            nodes = listOf(
                contentDescriptionNode(
                    displayText = "시발 검색 결과 이미지 설명",
                    left = 20,
                    top = 260,
                    right = 660,
                    bottom = 460,
                    className = "android.widget.ImageView",
                    packageName = "com.google.android.googlequicksearchbox"
                )
            ),
            screenWidth = 720,
            screenHeight = 1280
        )

        assertTrue(rois.isEmpty())
    }

    @Test
    fun planFromNodes_addsBrowserTextNodeRoiForGoogleAppHarmfulVisibleText() {
        val rois = VisualTextRoiPlanner.planFromNodes(
            nodes = listOf(
                textNode(
                    displayText = "시발의 어원과 용례에 대한 설명은 검색 결과 문서에서 확인할 수 있습니다.",
                    left = 28,
                    top = 240,
                    right = 690,
                    bottom = 370,
                    packageName = "com.google.android.googlequicksearchbox"
                )
            ),
            screenWidth = 720,
            screenHeight = 1280
        )

        assertEquals(1, rois.size)
        assertEquals("browser-text-node", rois.single().source)
    }

    @Test
    fun planFromNodes_addsFallbackVisibleBandForYoutubeSearchResults() {
        val rois = VisualTextRoiPlanner.planFromNodes(
            nodes = listOf(
                textNode("All", 10, 166, 98, 230),
                textNode("Shorts", 116, 166, 244, 230),
                textNode("Unwatched", 260, 166, 445, 230),
                textNode("Contemporary Korean Slang", 112, 826, 480, 870)
            ),
            screenWidth = 807,
            screenHeight = 1792
        )

        assertEquals(3, rois.size)
        assertTrue(rois.all { it.source == "youtube-visible-band" })
        assertEquals("fallback-first-viewport-band", rois[0].reason)
        assertTrue(rois[0].boundsInScreen.top > 230)
        assertTrue(rois[1].boundsInScreen.top > rois[0].boundsInScreen.top)
        assertTrue(rois[2].boundsInScreen.top > rois[1].boundsInScreen.top)
        assertTrue(rois.all { roi ->
            roi.boundsInScreen.bottom - roi.boundsInScreen.top <= (1792 * 0.27f).toInt()
        })
    }

    @Test
    fun planFromNodes_addsFallbackBandWhenYoutubeFilterLabelsAreMissing() {
        val rois = VisualTextRoiPlanner.planFromNodes(
            nodes = listOf(
                textNode("tlqkf", 147, 84, 596, 168),
                textNode("4 minutes, 46 seconds", 986, 729, 1048, 767),
                textNode("🔥\"Tlqkf 또 보여줘야 돼!\" : 식케이", 159, 828, 943, 933)
            ),
            screenWidth = 1080,
            screenHeight = 2400
        )

        assertEquals(3, rois.size)
        assertTrue(rois.all { it.source == "youtube-visible-band" })
        assertEquals(9, rois.first().priority)
        assertEquals(174, rois.first().boundsInScreen.top)
        assertTrue(rois.first().boundsInScreen.bottom >= 790)
        assertTrue(rois.last().boundsInScreen.bottom > 1800)
    }

    @Test
    fun planFromNodes_doesNotMixFallbackBandWithConcreteVisualRois() {
        val rois = VisualTextRoiPlanner.planFromNodes(
            nodes = listOf(
                textNode("All", 10, 166, 98, 230),
                contentDescriptionNode(
                    displayText = "Tlqkf 공부법, 채널, 조회수 1만회, 1일 전 - 동영상 재생",
                    left = 24,
                    top = 620,
                    right = 330,
                    bottom = 820
                )
            ),
            screenWidth = 720,
            screenHeight = 1280
        )

        assertEquals(1, rois.size)
        assertEquals("youtube-composite-card", rois.single().source)
    }

    @Test
    fun planFromNodes_addsVisibleBandForClippedTopCompositeBeforeConcreteCards() {
        val rois = VisualTextRoiPlanner.planFromNodes(
            nodes = listOf(
                textNode("All", 42, 210, 146, 294),
                textNode("Shorts", 167, 210, 336, 294),
                textNode("Videos", 844, 210, 1016, 294),
                contentDescriptionNode(
                    displayText = "🔥\"Tlqkf 또 보여줘야 돼!\" : 식케이 (Sik-K), Lil Moshpit - LOV3 - 4 minutes, 46 seconds - Go to channel 세모플 semo playlist - 920 thousand views - 7 months ago - play video",
                    left = 0,
                    top = 315,
                    right = 1080,
                    bottom = 352
                ),
                contentDescriptionNode(
                    displayText = "What is 'Tlqkf'?_Contemporary Korean Slang - 46 seconds - Go to channel Contemporary Korean Slang - 40 thousand views - 5 years ago - play video",
                    left = 0,
                    top = 553,
                    right = 1080,
                    bottom = 1350
                )
            ),
            screenWidth = 1080,
            screenHeight = 2400
        )

        assertEquals("youtube-visible-band", rois.first().source)
        assertEquals("fallback-clipped-top-composite", rois.first().reason)
        assertEquals(315, rois.first().boundsInScreen.top)
        assertTrue(rois.first().boundsInScreen.bottom >= 540)
        assertTrue(rois.any { it.source == "youtube-composite-card" })
    }

    @Test
    fun planFromNodes_expandsShortCompositeCardToIncludeVisibleTitleTextBelow() {
        val rois = VisualTextRoiPlanner.planFromNodes(
            nodes = listOf(
                contentDescriptionNode(
                    displayText = "외국인도 'tlqkf'은 못 참지 ㅋㅋㅋ 이제는 대세가 된 K-욕 - " +
                        "Go to channel 게임부록 - 10 thousand views - 3 years ago - play video",
                    left = 545,
                    top = 309,
                    right = 1054,
                    bottom = 459
                ),
                textNode(
                    displayText = "외국인도 'tlqkf'은 못 참지 ㅋㅋㅋ 이제는 대세가 된 K-욕",
                    left = 572,
                    top = 495,
                    right = 1027,
                    bottom = 603
                )
            ),
            screenWidth = 1080,
            screenHeight = 2400
        )

        assertEquals(1, rois.size)
        assertEquals("youtube-composite-card", rois.single().source)
        assertEquals("expanded-short-composite-title", rois.single().reason)
        assertTrue(rois.single().boundsInScreen.top <= 309)
        assertTrue(rois.single().boundsInScreen.bottom >= 603)
    }

    @Test
    fun planFromNodes_keepsTopShortsCardAndCropsThumbnailSegment() {
        val rois = VisualTextRoiPlanner.planFromNodes(
            nodes = listOf(
                contentDescriptionNode(
                    displayText = "Tlqkf 공부법, 2.1 million views, 미미미누, 10 months ago - play Short",
                    left = 32,
                    top = 189,
                    right = 529,
                    bottom = 941
                )
            ),
            screenWidth = 1080,
            screenHeight = 2400
        )

        assertEquals(1, rois.size)
        assertEquals("youtube-composite-card", rois.single().source)
        assertEquals("short-card-thumbnail-segment", rois.single().reason)
        assertTrue(rois.single().boundsInScreen.top <= 189)
        assertTrue(rois.single().boundsInScreen.bottom < 880)
        assertTrue(rois.single().boundsInScreen.bottom > 760)
    }

    @Test
    fun planFromNodes_keepsFallbackBandWhenOnlyGenericYoutubeRoisExist() {
        val rois = VisualTextRoiPlanner.planFromNodes(
            nodes = listOf(
                textNode("All", 10, 166, 98, 230),
                contentDescriptionNode(
                    displayText = "이미지 안에 있는 긴 테스트 문구입니다",
                    left = 20,
                    top = 260,
                    right = 320,
                    bottom = 420,
                    className = "android.widget.ImageView"
                )
            ),
            screenWidth = 720,
            screenHeight = 1280
        )

        assertEquals(3, rois.size)
        assertTrue(rois.all { it.source == "youtube-visible-band" })
    }

    @Test
    fun planFromNodes_addsCompactCommentPanelRoisFromAuthorRows() {
        val rois = VisualTextRoiPlanner.planFromNodes(
            nodes = listOf(
                textNode("Comments", 24, 420, 260, 492),
                textNode("Top", 36, 548, 140, 612),
                textNode("Newest", 160, 548, 320, 612),
                textNode("@cloud9619 · 3mo ago (edited)", 88, 690, 520, 728),
                textNode("@그랜드슬램1 · 4mo ago", 88, 930, 460, 968),
                textNode("Reminds me of...", 24, 2228, 486, 2312)
            ),
            screenWidth = 1080,
            screenHeight = 2400
        )

        assertEquals(2, rois.size)
        assertTrue(rois.all { it.source == "youtube-comment-panel" })
        assertEquals("comment-panel-author-body-band", rois.first().reason)
        assertTrue(rois.first().boundsInScreen.left <= 88)
        assertTrue(rois.first().boundsInScreen.top > 728)
        assertTrue(rois.first().boundsInScreen.bottom < 930)
        assertTrue(rois.first().boundsInScreen.right < 1080)
    }

    @Test
    fun planFromNodes_doesNotAddCommentPanelRoisWithoutPanelMarker() {
        val rois = VisualTextRoiPlanner.planFromNodes(
            nodes = listOf(
                textNode("@TLQKF-wh1", 210, 1360, 520, 1410),
                textNode("61 subscribers", 210, 1420, 520, 1470)
            ),
            screenWidth = 1080,
            screenHeight = 2400
        )

        assertTrue(rois.none { it.source == "youtube-comment-panel" })
    }

    @Test
    fun planFromNodes_deduplicatesOverlappingRegions() {
        val rois = VisualTextRoiPlanner.planFromNodes(
            nodes = listOf(
                contentDescriptionNode(
                    displayText = "시발 자동자, 조회수 1만회, 1일 전 - 동영상 재생",
                    left = 24,
                    top = 200,
                    right = 330,
                    bottom = 360
                ),
                contentDescriptionNode(
                    displayText = "시발 자동자, 조회수 1만회, 1일 전 - 동영상 재생",
                    left = 28,
                    top = 206,
                    right = 326,
                    bottom = 354
                )
            ),
            screenWidth = 720,
            screenHeight = 1280
        )

        assertEquals(1, rois.size)
    }

    @Test
    fun planFromNodes_returnsEmptyForInvalidScreen() {
        val rois = VisualTextRoiPlanner.planFromNodes(
            nodes = listOf(
                contentDescriptionNode(
                    displayText = "시발 자동자, 조회수 1만회, 1일 전 - 동영상 재생",
                    left = 24,
                    top = 200,
                    right = 330,
                    bottom = 360
                )
            ),
            screenWidth = 0,
            screenHeight = 1280
        )

        assertTrue(rois.isEmpty())
    }

    private fun contentDescriptionNode(
        displayText: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        className: String = "android.view.View",
        packageName: String = "com.google.android.youtube"
    ): ParsedTextNode {
        return ParsedTextNode(
            packageName = packageName,
            text = null,
            contentDescription = displayText,
            displayText = displayText,
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

    private fun textNode(
        displayText: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        packageName: String = "com.google.android.youtube",
        className: String = "android.widget.TextView",
        viewIdResourceName: String? = null,
        charBoxes: List<CharBox> = emptyList()
    ): ParsedTextNode {
        return ParsedTextNode(
            packageName = packageName,
            text = displayText,
            contentDescription = null,
            displayText = displayText,
            className = className,
            viewIdResourceName = viewIdResourceName,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            approxTop = top,
            isVisibleToUser = true,
            charBoxes = charBoxes
        )
    }

    private fun charBoxesFor(
        text: String,
        startLeft: Int,
        top: Int,
        charWidth: Int,
        height: Int
    ): List<CharBox> {
        val boxes = mutableListOf<CharBox>()
        var charIndex = 0
        var codePointIndex = 0
        var left = startLeft
        while (charIndex < text.length) {
            val value = String(Character.toChars(Character.codePointAt(text, charIndex)))
            boxes += CharBox(
                start = codePointIndex,
                end = codePointIndex + 1,
                boundsInScreen = BoundsRect(
                    left = left,
                    top = top,
                    right = left + charWidth,
                    bottom = top + height
                ),
                text = value
            )
            charIndex += value.length
            codePointIndex += 1
            left += charWidth
        }
        return boxes
    }
}
