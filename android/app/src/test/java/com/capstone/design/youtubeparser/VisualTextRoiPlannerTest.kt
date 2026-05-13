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

        assertEquals(4, rois.size)
        assertTrue(rois.all { it.source == "youtube-composite-card" })
        assertEquals(listOf(174, 324, 474, 624), rois.map { it.boundsInScreen.top })
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
        assertEquals(4, plan.rois.size)
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

        assertEquals(1, rois.size)
        assertTrue(rois.all { it.source == "youtube-visible-band" })
        assertEquals("fallback-first-viewport-band", rois[0].reason)
        assertTrue(rois[0].boundsInScreen.top > 230)
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

        assertEquals(1, rois.size)
        assertEquals("youtube-visible-band", rois.single().source)
        assertEquals(9, rois.single().priority)
        assertEquals(174, rois.single().boundsInScreen.top)
        assertTrue(rois.single().boundsInScreen.bottom >= 790)
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

        assertEquals(1, rois.size)
        assertEquals("youtube-visible-band", rois.single().source)
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
        bottom: Int
    ): ParsedTextNode {
        return ParsedTextNode(
            packageName = "com.google.android.youtube",
            text = displayText,
            contentDescription = null,
            displayText = displayText,
            className = "android.widget.TextView",
            viewIdResourceName = null,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            approxTop = top,
            isVisibleToUser = true
        )
    }
}
