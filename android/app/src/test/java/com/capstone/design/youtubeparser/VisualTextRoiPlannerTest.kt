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
            top = 100,
            right = 280,
            bottom = 210,
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
        className: String = "android.view.View"
    ): ParsedTextNode {
        return ParsedTextNode(
            packageName = "com.google.android.youtube",
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
}
