package com.example.youtubeparser

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlin.math.abs

class YoutubeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "YTParserService"
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val MIN_SAVE_INTERVAL_MS = 1500L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastSavedSignature: String? = null
    private var lastSavedAt: Long = 0L
    private var lastObservedPackage: String? = null

    private val parseRunnable = Runnable {
        parseAndSaveCurrentWindow()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName != YOUTUBE_PACKAGE && packageName != INSTAGRAM_PACKAGE) return

        lastObservedPackage = packageName

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                handler.removeCallbacks(parseRunnable)

                val delayMs = when (event.eventType) {
                    AccessibilityEvent.TYPE_VIEW_SCROLLED -> 450L
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED -> 700L
                    else -> 550L
                }

                handler.postDelayed(parseRunnable, delayMs)
            }
        }
    }

    override fun onInterrupt() {
        handler.removeCallbacks(parseRunnable)
        Log.d(TAG, "service interrupted")
    }

    override fun onDestroy() {
        handler.removeCallbacks(parseRunnable)
        super.onDestroy()
    }

    private fun parseAndSaveCurrentWindow() {
        val currentPackage = lastObservedPackage ?: run {
            Log.d(TAG, "lastObservedPackage is null")
            return
        }

        val nodes = if (currentPackage == INSTAGRAM_PACKAGE) {
            extractVisibleTextNodesFromInstagramWindows()
        } else {
            extractVisibleTextNodesFromCurrentWindow()
        }

        if (nodes.isEmpty()) {
            Log.d(TAG, "no visible nodes found")
            return
        }

        Log.d(TAG, "current package = $currentPackage")

        val comments = when (currentPackage) {
            YOUTUBE_PACKAGE -> YoutubeCommentExtractor.extractComments(nodes)
            INSTAGRAM_PACKAGE -> InstagramCommentExtractor.extractComments(nodes)
            else -> emptyList()
        }

        Log.d(TAG, "parsed comment count = ${comments.size}")

        if (currentPackage == INSTAGRAM_PACKAGE && comments.isEmpty()) {
            Log.d(TAG, "instagram filtered node count = ${nodes.size}")
            nodes.take(60).forEachIndexed { index, node ->
                Log.d(
                    TAG,
                    "IG_RAW[$index] text=${node.displayText} | cls=${node.className} | id=${node.viewIdResourceName} | bounds=(${node.left},${node.top},${node.right},${node.bottom})"
                )
            }
        }

        if (comments.isEmpty()) return

        comments.take(20).forEachIndexed { index, comment ->
            Log.d(TAG, "[$index] COMMENT=${comment.commentText} | BOUNDS=${comment.boundsInScreen}")
        }

        val signature = comments.joinToString("||") {
            "${it.commentText}|${it.boundsInScreen.top}|${it.boundsInScreen.left}"
        }

        val now = System.currentTimeMillis()
        if (signature == lastSavedSignature && now - lastSavedAt < MIN_SAVE_INTERVAL_MS) {
            Log.d(TAG, "skip duplicate snapshot")
            return
        }

        val snapshot = ParseSnapshot(
            timestamp = now,
            comments = comments
        )

        val savedFile = JsonFileStore.saveSnapshot(applicationContext, snapshot, currentPackage)
        Log.d(TAG, "snapshot saved = ${savedFile.absolutePath}")

        lastSavedSignature = signature
        lastSavedAt = now
    }

    private fun extractVisibleTextNodesFromCurrentWindow(): List<ParsedTextNode> {
        val root = rootInActiveWindow ?: return emptyList()
        return collectFilteredNodesFromRoot(root, instagramMode = false)
    }

    private fun extractVisibleTextNodesFromInstagramWindows(): List<ParsedTextNode> {
        val candidates = mutableListOf<WindowCandidate>()

        val activeRoot = rootInActiveWindow
        if (activeRoot != null && activeRoot.packageName?.toString() == INSTAGRAM_PACKAGE) {
            val raw = collectRawNodesFromRoot(activeRoot)
            candidates += WindowCandidate("active", activeRoot, raw, scoreInstagramWindow(raw))
        }

        windows?.forEachIndexed { index, window ->
            val root = window.root ?: return@forEachIndexed
            if (root.packageName?.toString() != INSTAGRAM_PACKAGE) return@forEachIndexed

            val raw = collectRawNodesFromRoot(root)
            val score = scoreInstagramWindow(raw)
            candidates += WindowCandidate("window-$index-${windowTypeName(window)}", root, raw, score)
        }

        val best = candidates.maxByOrNull { it.score }
        if (best != null) {
            Log.d(TAG, "instagram best window = ${best.label}, score=${best.score}, raw=${best.rawNodes.size}")
        }

        val pickedRoot = when {
            best != null && best.score > 0 -> best.root
            activeRoot != null && activeRoot.packageName?.toString() == INSTAGRAM_PACKAGE -> activeRoot
            else -> candidates.firstOrNull()?.root
        } ?: return emptyList()

        return collectFilteredNodesFromRoot(pickedRoot, instagramMode = true)
    }

    private fun collectFilteredNodesFromRoot(
        root: AccessibilityNodeInfo,
        instagramMode: Boolean
    ): List<ParsedTextNode> {
        val out = mutableListOf<ParsedTextNode>()

        fun dfs(node: AccessibilityNodeInfo?) {
            if (node == null) return

            val parsed = nodeToParsedTextNode(node) ?: run {
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    dfs(child)
                    child?.recycle()
                }
                return
            }

            val rect = Rect(parsed.left, parsed.top, parsed.right, parsed.bottom)
            if (shouldKeepNode(node, parsed.displayText.orEmpty(), rect, root, instagramMode)) {
                out += parsed
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                dfs(child)
                child?.recycle()
            }
        }

        dfs(root)
        return deduplicateNodes(out)
    }

    private fun collectRawNodesFromRoot(root: AccessibilityNodeInfo): List<ParsedTextNode> {
        val out = mutableListOf<ParsedTextNode>()

        fun dfs(node: AccessibilityNodeInfo?) {
            if (node == null) return

            val parsed = nodeToParsedTextNode(node)
            if (parsed != null) out += parsed

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                dfs(child)
                child?.recycle()
            }
        }

        dfs(root)
        return deduplicateNodes(out)
    }

    private fun nodeToParsedTextNode(node: AccessibilityNodeInfo): ParsedTextNode? {
        val text = node.text?.toString()
        val contentDescription = node.contentDescription?.toString()
        val value = when {
            !text.isNullOrBlank() -> text.trim()
            !contentDescription.isNullOrBlank() -> contentDescription.trim()
            else -> null
        } ?: return null

        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) return null

        return ParsedTextNode(
            packageName = node.packageName?.toString().orEmpty(),
            text = text,
            contentDescription = contentDescription,
            displayText = value,
            className = node.className?.toString(),
            viewIdResourceName = node.viewIdResourceName,
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom,
            approxTop = rect.top,
            isVisibleToUser = node.isVisibleToUser
        )
    }

    private fun shouldKeepNode(
        node: AccessibilityNodeInfo,
        value: String,
        rect: Rect,
        root: AccessibilityNodeInfo,
        instagramMode: Boolean = false
    ): Boolean {
        val className = node.className?.toString().orEmpty()
        val lower = value.lowercase()
        val viewId = node.viewIdResourceName.orEmpty()
        val rootRect = Rect().also { root.getBoundsInScreen(it) }
        val screenHeight = if (rootRect.height() > 0) rootRect.height() else rect.bottom
        val upperCutoff = if (instagramMode) (screenHeight * 0.08f).toInt() else (screenHeight * 0.28f).toInt()

        if (!node.isVisibleToUser) return false
        if (rect.bottom <= upperCutoff) return false
        if (value.length == 1 && !value[0].isLetterOrDigit() && value[0] !in listOf('@', '#')) return false

        if (instagramMode) {
            if (
                viewId.contains("news_tab") ||
                viewId.contains("creation_tab") ||
                viewId.contains("profile_tab") ||
                viewId.contains("comment_composer_left_image_view") ||
                viewId.contains("scrubber") ||
                viewId.contains("clips_author_profile_pic") ||
                viewId.contains("inline_follow_button") ||
                viewId.contains("media_reactions_sheet_recycler_view")
            ) return false

            if (
                value == "활동" ||
                value == "만들기" ||
                value == "프로필" ||
                value == "프로필 사진" ||
                value == "대화 참여하기..." ||
                value == "회원님의 생각을 남겨보세요." ||
                value == "댓글 달기" ||
                value == "저장" ||
                value == "관심 없음" ||
                value == "관심 있음" ||
                value == "숨겨진 댓글 보기" ||
                value == "캡션" ||
                value == "릴스" ||
                value.contains("님에게 댓글 추가") ||
                (value.contains("님 외") && value.contains("좋아합니다")) ||
                value.contains("명이 좋아합니다")
            ) return false
        }

        if (
            lower.startsWith("comments.") ||
            lower == "sort comments" ||
            lower == "reply..." ||
            lower == "comment..." ||
            lower == "view reply" ||
            (lower.startsWith("view ") && lower.contains(" total replies"))
        ) return false

        if (
            lower.contains("like this comment") ||
            lower.contains("like this reply") ||
            lower.contains("dislike this comment") ||
            lower.contains("dislike this reply") ||
            lower == "reply" ||
            lower.contains("action menu") ||
            lower.contains("open camera") ||
            lower.contains("drag handle") ||
            lower.contains("video player") ||
            lower.contains("minutes") ||
            lower.contains("seconds") ||
            lower == "back" ||
            lower == "close" ||
            value == "답글" ||
            value.contains("정렬") ||
            value == "뒤로" ||
            value == "닫기"
        ) return false

        if (instagramMode) {
            if (lower == "검색 및 탐색하기" || lower == "검색" || lower == "search") return false
            if (lower == "공유" || lower == "share") return false
            if (lower == "리포스트") return false
            if (lower == "좋아요" || lower.endsWith("좋아요")) return false
            if (lower.contains("님이 만든 릴스입니다")) return false
            if (lower.contains("재생하거나 일시 중지하려면")) return false
            if (lower.contains("팔로우") || lower.contains("follow")) return false
            if (lower.contains("님에게 댓글 추가")) return false
            if (lower == "댓글 달기") return false
            if (lower == "저장") return false
            if (lower == "관심 없음") return false
            if (lower == "관심 있음") return false
            if (lower == "숨겨진 댓글 보기") return false
            if (lower == "캡션") return false
            if (lower == "릴스") return false
            if ((lower.contains("님 외") && lower.contains("좋아합니다")) || lower.contains("명이 좋아합니다")) return false
        }

        if (lower.endsWith(" likes") || lower.endsWith(" like")) return false
        if (value.endsWith("좋아요")) return false
        if (className.contains("Button", ignoreCase = true)) return false

        return true
    }

    private fun scoreInstagramWindow(nodes: List<ParsedTextNode>): Int {
        var score = 0
        for (node in nodes) {
            val text = node.displayText.orEmpty().trim()
            val id = node.viewIdResourceName.orEmpty()

            if (text.endsWith("님의 프로필로 이동") || text.endsWith("님의 스토리 보기")) score += 3
            if (text.contains("답글") && text.contains("더 보기")) score += 3
            if (looksLikeDate(text)) score += 2
            if (looksLikeUsername(text)) score += 2
            if (looksLikeInstagramCombinedComment(text)) score += 4

            if (id.contains("news_tab") || id.contains("creation_tab") || id.contains("profile_tab")) score -= 6
            if (id.contains("comment_composer_left_image_view") || id.contains("scrubber")) score -= 6

            if (text == "회원님의 생각을 남겨보세요." || text == "대화 참여하기...") score -= 4
            if (text.contains("님이 만든 릴스입니다") || text.contains("재생하거나 일시 중지하려면")) score -= 4
            if (text == "검색 및 탐색하기") score -= 4
            if (text.contains("님에게 댓글 추가")) score -= 4
            if ((text.contains("님 외") && text.contains("좋아합니다")) || text.contains("명이 좋아합니다")) score -= 4
            if (text == "캡션" || text == "릴스") score -= 3
        }
        return score
    }

    private fun deduplicateNodes(nodes: List<ParsedTextNode>): List<ParsedTextNode> {
        val sorted = nodes.sortedWith(
            compareBy<ParsedTextNode> { it.top }
                .thenBy { it.left }
                .thenBy { priorityOf(it) }
        )

        val result = mutableListOf<ParsedTextNode>()

        for (node in sorted) {
            val index = result.indexOfFirst { existing ->
                existing.displayText == node.displayText &&
                        abs(existing.top - node.top) <= 8 &&
                        abs(existing.left - node.left) <= 120
            }

            if (index == -1) {
                result += node
            } else {
                val existing = result[index]
                if (priorityOf(node) < priorityOf(existing)) {
                    result[index] = node
                }
            }
        }

        return result
    }

    private fun priorityOf(node: ParsedTextNode): Int {
        val className = node.className.orEmpty()
        return when {
            node.text != null -> 0
            className.contains("TextView", ignoreCase = true) -> 1
            className.contains("ViewGroup", ignoreCase = true) -> 2
            className.contains("ImageView", ignoreCase = true) -> 3
            className.contains("Button", ignoreCase = true) -> 4
            else -> 5
        }
    }

    private fun looksLikeDate(text: String): Boolean {
        val t = text.trim()
        return t.endsWith("초 전") ||
                t.endsWith("분 전") ||
                t.endsWith("시간 전") ||
                t.endsWith("일 전") ||
                t.endsWith("주 전") ||
                Regex("""^\d+월\s*\d+일$""").matches(t)
    }

    private fun looksLikeUsername(text: String): Boolean {
        val t = text.trim()
        if (t.startsWith("@")) return true
        return !t.contains(" ") &&
                t.length in 3..30 &&
                t.all { it.isLetterOrDigit() || it == '_' || it == '.' }
    }

    private fun looksLikeInstagramCombinedComment(text: String): Boolean {
        val t = text.trim()
        val match = Regex("""^([A-Za-z0-9._]{3,30})\s+(.+)$""").find(t) ?: return false
        val body = match.groupValues[2]
        return body.length >= 2 && !looksLikeDate(body)
    }

    private fun windowTypeName(window: AccessibilityWindowInfo): String {
        return when (window.type) {
            AccessibilityWindowInfo.TYPE_APPLICATION -> "app"
            AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "ime"
            AccessibilityWindowInfo.TYPE_SYSTEM -> "system"
            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "overlay"
            else -> window.type.toString()
        }
    }

    private data class WindowCandidate(
        val label: String,
        val root: AccessibilityNodeInfo,
        val rawNodes: List<ParsedTextNode>,
        val score: Int
    )
}