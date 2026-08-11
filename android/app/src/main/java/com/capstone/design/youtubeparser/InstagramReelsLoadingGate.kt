package com.capstone.design.youtubeparser

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import kotlin.math.roundToInt

internal object InstagramReelsLoadingGate {
    val commentScrollViewIds = listOf(
        "com.instagram.android:id/sticky_header_list",
        "com.instagram.android:id/main_list_view"
    )

    private const val COMMENT_BUTTON_ID = "comment_button"
    private const val COMMENT_COUNT_ID = "comment_count"
    private const val REELS_ACTIONS_ID = "clips_ufi_component"
    private const val REELS_VIEW_PAGER_ID = "clips_viewer_view_pager"
    private const val REELS_MEDIA_ID = "clips_media_component"
    private const val MAX_ANCESTOR_DEPTH = 7
    private const val MAX_REELS_SCAN_NODES = 120

    fun isReelsScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val ownedNodes = mutableListOf<AccessibilityNodeInfo>()
        queue.add(root)
        ownedNodes += root
        return try {
            var visitedCount = 0
            var detected = false
            while (queue.isNotEmpty() && visitedCount < MAX_REELS_SCAN_NODES) {
                val node = queue.removeFirst()
                visitedCount += 1
                if (isReelsStructureId(node.viewIdResourceName)) {
                    detected = true
                    break
                }
                for (index in 0 until node.childCount) {
                    val child = node.getChild(index) ?: continue
                    queue.addLast(child)
                    ownedNodes += child
                }
            }
            detected
        } catch (_: RuntimeException) {
            false
        } finally {
            ownedNodes.asReversed().forEach { node ->
                @Suppress("DEPRECATION")
                runCatching { node.recycle() }
            }
        }
    }

    fun isReelsCommentTrigger(
        source: AccessibilityNodeInfo?,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        if (source == null) return false

        val ancestorViewIds = mutableListOf<String?>()
        var current: AccessibilityNodeInfo? = source
        repeat(MAX_ANCESTOR_DEPTH) {
            val node = current ?: return@repeat
            ancestorViewIds += node.viewIdResourceName
            current = node.parent
        }
        val androidBounds = Rect()
        source.getBoundsInScreen(androidBounds)
        return matchesTrigger(
            viewIdResourceName = source.viewIdResourceName,
            ancestorViewIds = ancestorViewIds,
            sourceBounds = BoundsRect(
                left = androidBounds.left,
                top = androidBounds.top,
                right = androidBounds.right,
                bottom = androidBounds.bottom
            ),
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )
    }

    internal fun matchesTrigger(
        viewIdResourceName: String?,
        ancestorViewIds: List<String?>,
        sourceBounds: BoundsRect? = null,
        screenWidth: Int = 0,
        screenHeight: Int = 0
    ): Boolean {
        val triggerId = viewIdResourceName.idName()
        if (triggerId != COMMENT_BUTTON_ID && triggerId != COMMENT_COUNT_ID) {
            return false
        }
        val insideReelsActions = ancestorViewIds.any { id ->
            id.idName() == REELS_ACTIONS_ID
        }
        if (insideReelsActions) return true

        val bounds = sourceBounds ?: return false
        if (screenWidth <= 0 || screenHeight <= 0) return false
        val centerY = (bounds.top + bounds.bottom) / 2f
        return bounds.left >= screenWidth * 0.70f &&
            bounds.right <= screenWidth &&
            centerY in (screenHeight * 0.25f)..(screenHeight * 0.75f)
    }

    fun createLoadingSpec(
        screenWidth: Int,
        screenHeight: Int
    ): MaskOverlaySpec? {
        if (screenWidth <= 0 || screenHeight <= 0) return null

        val isTabletReels = screenHeight.toFloat() / screenWidth <= 1.85f
        val left = if (isTabletReels) {
            (screenWidth * 0.1067f).roundToInt()
        } else {
            0
        }
        val right = if (isTabletReels) {
            (screenWidth * 0.8925f).roundToInt()
        } else {
            screenWidth
        }
        val top = (screenHeight * 0.375f).roundToInt()
        val bottom = (screenHeight * 0.90f).roundToInt()
        if (right <= left || bottom <= top) return null

        return MaskOverlaySpec(
            left = left,
            top = top,
            width = right - left,
            height = bottom - top,
            label = "instagram-reels-loading",
            allowScrollTranslation = false,
            debugSource = "instagram-reels-comment-trigger",
            style = MaskOverlayStyle.LOADING
        )
    }

    private fun String?.idName(): String {
        return this.orEmpty()
            .substringAfterLast('/')
            .lowercase()
    }

    private fun isReelsStructureId(viewIdResourceName: String?): Boolean {
        return when (viewIdResourceName.idName()) {
            REELS_ACTIONS_ID,
            REELS_VIEW_PAGER_ID,
            REELS_MEDIA_ID -> true
            else -> false
        }
    }
}
