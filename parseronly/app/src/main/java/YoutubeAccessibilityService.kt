package com.example.youtubeparser

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
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
        private const val TIKTOK_PACKAGE = "com.zhiliaoapp.musically"
        private const val TIKTOK_ALT_PACKAGE = "com.ss.android.ugc.trill"
        private const val MIN_SAVE_INTERVAL_MS = 1500L
        private const val AUTOMATION_VIDEO_STEP_MS = 14_000L
        private const val AUTOMATION_AFTER_LAUNCH_MS = 5_000L
        private const val COMMENT_PANEL_OPEN_MS = 1_200L
        private const val COMMENT_SCROLL_1_MS = 2_600L
        private const val COMMENT_SCROLL_2_MS = 4_100L
        private const val COMMENT_SCROLL_3_MS = 5_600L
        private const val COMMENT_CLOSE_MS = 7_200L
        private const val NEXT_VIDEO_MS = 8_600L
        private const val AD_SKIP_RECHECK_MS = 3_000L
        private const val GESTURE_DURATION_MS = 420L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastSavedSignature: String? = null
    private var lastSavedAt: Long = 0L
    private var lastObservedPackage: String? = null
    private var automationPlatform: AutomationPlatform? = null
    private var automationStepScheduled = false
    private var automationRotationScheduled = false
    private var shortFormEntryAttemptedFor: AutomationPlatform? = null
    private var launchAttemptedFor: AutomationPlatform? = null
    private var commentPanelOpenedFor: AutomationPlatform? = null
    private var automationCycleId = 0L

    private val parseRunnable = Runnable {
        parseAndSaveCurrentWindow()
    }

    private val automationStepRunnable = Runnable {
        automationStepScheduled = false
        runAutomationVideoStep()
    }

    private val automationRotationRunnable = Runnable {
        automationRotationScheduled = false
        rotateAutomationPlatform()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ensureAutomationLoop()
        Log.d(TAG, "service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (
            packageName != YOUTUBE_PACKAGE &&
            packageName != INSTAGRAM_PACKAGE &&
            packageName != TIKTOK_PACKAGE &&
            packageName != TIKTOK_ALT_PACKAGE
        ) return

        lastObservedPackage = packageName
        ensureAutomationLoop()

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
        cancelAutomationLoop()
        Log.d(TAG, "service interrupted")
    }

    override fun onDestroy() {
        handler.removeCallbacks(parseRunnable)
        cancelAutomationLoop()
        super.onDestroy()
    }

    private fun ensureAutomationLoop() {
        if (AutomationSettingsStore.getPlatformMode(applicationContext) == AutomationSettingsStore.PLATFORM_INSTAGRAM) {
            if (AutomationSettingsStore.isEnabled(applicationContext)) {
                AutomationSettingsStore.setEnabled(applicationContext, false)
                AutomationSettingsStore.saveStatus(
                    applicationContext,
                    "Instagram parse-only mode: open comments manually"
                )
            }
            cancelAutomationLoop()
            return
        }

        if (!AutomationSettingsStore.isEnabled(applicationContext)) {
            cancelAutomationLoop()
            return
        }

        if (automationPlatform?.let { !AutomationSettingsStore.isPlatformAllowed(applicationContext, it.name) } == true) {
            handler.removeCallbacks(automationStepRunnable)
            handler.removeCallbacks(automationRotationRunnable)
            automationPlatform = null
            automationStepScheduled = false
            automationRotationScheduled = false
        }

        if (automationPlatform == null) {
            rotateAutomationPlatform()
            return
        }

        scheduleAutomationRotation()
        scheduleAutomationStep(AUTOMATION_AFTER_LAUNCH_MS)
    }

    private fun cancelAutomationLoop() {
        handler.removeCallbacks(automationStepRunnable)
        handler.removeCallbacks(automationRotationRunnable)
        automationStepScheduled = false
        automationRotationScheduled = false
        automationPlatform = null
        shortFormEntryAttemptedFor = null
        launchAttemptedFor = null
        commentPanelOpenedFor = null
        automationCycleId++
    }

    private fun rotateAutomationPlatform() {
        if (!AutomationSettingsStore.isEnabled(applicationContext)) {
            cancelAutomationLoop()
            return
        }

        if (closeCommentPanelBeforeAppSwitch()) {
            return
        }

        switchToNextAutomationPlatform()
    }

    private fun switchToNextAutomationPlatform() {
        val platforms = allowedAutomationPlatforms()
        if (platforms.isEmpty()) {
            AutomationSettingsStore.saveStatus(applicationContext, "자동 운전 가능 앱 없음")
            return
        }

        val index = AutomationSettingsStore.getPlatformIndex(applicationContext) % platforms.size
        val platform = platforms[index]
        automationPlatform = platform
        shortFormEntryAttemptedFor = null
        launchAttemptedFor = platform
        commentPanelOpenedFor = null
        automationCycleId++
        AutomationSettingsStore.savePlatformIndex(applicationContext, (index + 1) % platforms.size)
        AutomationSettingsStore.saveStatus(
            applicationContext,
            "${platform.label} 진입: 댓글 자동 파싱 준비"
        )

        launchPlatform(platform)
        scheduleAutomationStep(AUTOMATION_AFTER_LAUNCH_MS)
        scheduleAutomationRotation()
    }

    private fun closeCommentPanelBeforeAppSwitch(): Boolean {
        val currentPlatform = automationPlatform ?: return false
        val observedPackage = lastObservedPackage ?: return false
        if (!currentPlatform.matches(observedPackage)) return false

        val shouldClose = commentPanelOpenedFor == currentPlatform || isCommentPanelLikelyOpen(currentPlatform)
        if (!shouldClose) return false

        automationCycleId++
        handler.removeCallbacks(automationStepRunnable)
        automationStepScheduled = false

        AutomationSettingsStore.saveStatus(applicationContext, "${currentPlatform.label} 앱 전환 전 댓글창 닫는 중")
        val closedByButton = closeCommentPanel()
        if (!closedByButton) {
            dismissCommentPanelWithGesture()
        }

        handler.postDelayed(
            {
                if (AutomationSettingsStore.isEnabled(applicationContext)) {
                    commentPanelOpenedFor = null
                    switchToNextAutomationPlatform()
                }
            },
            if (closedByButton) 550L else 1_100L
        )
        return true
    }

    private fun scheduleAutomationRotation() {
        if (allowedAutomationPlatforms().size <= 1) return
        if (automationRotationScheduled) return
        automationRotationScheduled = true
        handler.postDelayed(
            automationRotationRunnable,
            AutomationSettingsStore.getRotationIntervalMs(applicationContext)
        )
    }

    private fun allowedAutomationPlatforms(): List<AutomationPlatform> {
        return AutomationPlatform.values().filter {
            it != AutomationPlatform.INSTAGRAM &&
                isPlatformAvailable(it) &&
                AutomationSettingsStore.isPlatformAllowed(applicationContext, it.name)
        }
    }

    private fun scheduleAutomationStep(delayMs: Long = AUTOMATION_VIDEO_STEP_MS) {
        if (automationStepScheduled) return
        automationStepScheduled = true
        handler.postDelayed(automationStepRunnable, delayMs)
    }

    private fun runAutomationVideoStep() {
        if (!AutomationSettingsStore.isEnabled(applicationContext)) {
            cancelAutomationLoop()
            return
        }

        val platform = automationPlatform ?: run {
            rotateAutomationPlatform()
            return
        }

        if (platform == AutomationPlatform.INSTAGRAM) {
            parseAndSaveCurrentWindow()
            cancelAutomationLoop()
            return
        }

        val observedPackage = lastObservedPackage
        if (observedPackage == null || !platform.matches(observedPackage)) {
            AutomationSettingsStore.saveStatus(
                applicationContext,
                "${platform.label} 화면 대기 중: 앱을 다시 엽니다."
            )
            if (launchAttemptedFor != platform) {
                launchAttemptedFor = platform
                launchPlatform(platform)
            }
            scheduleAutomationStep(AUTOMATION_AFTER_LAUNCH_MS)
            return
        }
        launchAttemptedFor = null

        AutomationSettingsStore.saveStatus(applicationContext, "${platform.label} 댓글 파싱 사이클 시작")
        if (platform == AutomationPlatform.INSTAGRAM && !isInstagramReelsSelected()) {
            clickInstagramReelsTab()
            scheduleAutomationStep(AUTOMATION_AFTER_LAUNCH_MS)
            return
        }

        if (enterShortFormIfNeeded(platform)) {
            scheduleAutomationStep(AUTOMATION_AFTER_LAUNCH_MS)
            return
        }

        val cycleId = ++automationCycleId

        if (skipAdIfNeeded(platform)) {
            scheduleAutomationStep(AD_SKIP_RECHECK_MS)
            return
        }

        val instagramPanelAlreadyOpen =
            platform == AutomationPlatform.INSTAGRAM && isCommentPanelLikelyOpen(platform)

        if (instagramPanelAlreadyOpen) {
            commentPanelOpenedFor = platform
            AutomationSettingsStore.saveStatus(applicationContext, "${platform.label} 댓글창 열린 상태: 현재 릴스 댓글 파싱")
            parseAndSaveCurrentWindow()
        } else {
            parseAndSaveCurrentWindow()
            commentPanelOpenedFor = null
            val commentsOpened = openCommentPanel(platform)
            if (!commentsOpened) {
                AutomationSettingsStore.saveStatus(applicationContext, "${platform.label} comments not opened; moving next")
                moveToNextVideo(platform, strongSwipe = platform != AutomationPlatform.TIKTOK)
                scheduleAutomationStep(if (platform == AutomationPlatform.YOUTUBE) AD_SKIP_RECHECK_MS else AUTOMATION_VIDEO_STEP_MS)
                return
            }
        }

        runLaterIfActive(platform, cycleId, COMMENT_PANEL_OPEN_MS) {
            if (platform == AutomationPlatform.INSTAGRAM && !isCommentPanelLikelyOpen(platform)) {
                AutomationSettingsStore.saveStatus(applicationContext, "${platform.label} 댓글창 미확인: 스크롤 생략")
                return@runLaterIfActive
            }
            if (platform == AutomationPlatform.INSTAGRAM) {
                parseAndSaveCurrentWindow()
                return@runLaterIfActive
            }
            expandCommentPanel()
            parseAndSaveCurrentWindow()
        }

        runLaterIfActive(platform, cycleId, COMMENT_SCROLL_1_MS) {
            if (platform == AutomationPlatform.INSTAGRAM && !isCommentPanelLikelyOpen(platform)) {
                AutomationSettingsStore.saveStatus(applicationContext, "${platform.label} 댓글창 미확인: 스크롤 생략")
                return@runLaterIfActive
            }
            scrollCommentPanel(platform)
            parseAndSaveCurrentWindow()
        }

        runLaterIfActive(platform, cycleId, COMMENT_SCROLL_2_MS) {
            if (platform == AutomationPlatform.INSTAGRAM && !isCommentPanelLikelyOpen(platform)) {
                AutomationSettingsStore.saveStatus(applicationContext, "${platform.label} 댓글창 미확인: 스크롤 생략")
                return@runLaterIfActive
            }
            scrollCommentPanel(platform)
            parseAndSaveCurrentWindow()
        }

        runLaterIfActive(platform, cycleId, COMMENT_SCROLL_3_MS) {
            if (platform == AutomationPlatform.INSTAGRAM && !isCommentPanelLikelyOpen(platform)) {
                AutomationSettingsStore.saveStatus(applicationContext, "${platform.label} 댓글창 미확인: 스크롤 생략")
                return@runLaterIfActive
            }
            scrollCommentPanel(platform)
            parseAndSaveCurrentWindow()
        }

        runLaterIfActive(platform, cycleId, COMMENT_CLOSE_MS) {
            if (platform != AutomationPlatform.INSTAGRAM) {
                closeCommentPanel()
            }
        }

        runLaterIfActive(platform, cycleId, NEXT_VIDEO_MS) {
            moveToNextVideo(platform, strongSwipe = platform == AutomationPlatform.INSTAGRAM)
        }

        scheduleAutomationStep(AUTOMATION_VIDEO_STEP_MS)
    }

    private fun runLaterIfActive(
        platform: AutomationPlatform,
        cycleId: Long,
        delayMs: Long,
        action: () -> Unit
    ) {
        handler.postDelayed(
            {
                if (
                    AutomationSettingsStore.isEnabled(applicationContext) &&
                    automationPlatform == platform &&
                    automationCycleId == cycleId &&
                    lastObservedPackage?.let { platform.matches(it) } == true
                ) {
                    if (skipAdIfNeeded(platform)) return@postDelayed
                    action()
                }
            },
            delayMs
        )
    }

    private fun launchPlatform(platform: AutomationPlatform) {
        val intent = when (platform) {
            AutomationPlatform.YOUTUBE -> packageManager.getLaunchIntentForPackage(YOUTUBE_PACKAGE)
            AutomationPlatform.INSTAGRAM -> packageManager.getLaunchIntentForPackage(INSTAGRAM_PACKAGE)
            AutomationPlatform.TIKTOK -> packageManager.getLaunchIntentForPackage(TIKTOK_PACKAGE)
                ?: packageManager.getLaunchIntentForPackage(TIKTOK_ALT_PACKAGE)
        }?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent == null) {
            AutomationSettingsStore.saveStatus(applicationContext, "${platform.label} 앱 실행 intent 없음")
            return
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            AutomationSettingsStore.saveStatus(
                applicationContext,
                "${platform.label} 앱 실행 실패: ${e.javaClass.simpleName}"
            )
        }
    }

    private fun isPlatformAvailable(platform: AutomationPlatform): Boolean {
        return when (platform) {
            AutomationPlatform.YOUTUBE -> packageManager.getLaunchIntentForPackage(YOUTUBE_PACKAGE) != null
            AutomationPlatform.INSTAGRAM -> packageManager.getLaunchIntentForPackage(INSTAGRAM_PACKAGE) != null
            AutomationPlatform.TIKTOK -> packageManager.getLaunchIntentForPackage(TIKTOK_PACKAGE) != null ||
                packageManager.getLaunchIntentForPackage(TIKTOK_ALT_PACKAGE) != null
        }
    }

    private fun enterShortFormIfNeeded(platform: AutomationPlatform): Boolean {
        if (platform == AutomationPlatform.TIKTOK) return false
        if (shortFormEntryAttemptedFor == platform) return false

        val clicked = when (platform) {
            AutomationPlatform.YOUTUBE -> clickNamedButton("YouTube Shorts", shortFormButtonKeywords(platform))
            AutomationPlatform.INSTAGRAM -> clickNamedButton("Instagram Reels", shortFormButtonKeywords(platform))
            AutomationPlatform.TIKTOK -> false
        }

        if (clicked) {
            shortFormEntryAttemptedFor = platform
            AutomationSettingsStore.saveStatus(applicationContext, "${platform.label} short-form tab opened; waiting for screen")
        }

        return clicked
    }

    private fun openCommentPanel(platform: AutomationPlatform): Boolean {
        if (skipAdIfNeeded(platform)) return false

        val clicked = when (platform) {
            AutomationPlatform.YOUTUBE -> clickNamedButton(
                "YouTube comments",
                commentButtonKeywords(platform)
            )
            AutomationPlatform.TIKTOK -> clickNamedButton(
                "TikTok comments",
                commentButtonKeywords(platform)
            )
            AutomationPlatform.INSTAGRAM -> clickInstagramCommentButton()
        }

        if (!clicked) {
            val message = "${platform.label} 댓글 버튼을 이름으로 찾지 못함"
            Log.w(TAG, message)
            AutomationSettingsStore.saveStatus(applicationContext, message)
        } else {
            commentPanelOpenedFor = platform
        }

        return clicked
    }

    private fun expandCommentPanel() {
        swipeFraction(0.50f, 0.82f, 0.50f, 0.22f)
    }

    private fun scrollCommentPanel() {
        swipeFraction(0.50f, 0.78f, 0.50f, 0.34f)
    }

    private fun scrollCommentPanel(platform: AutomationPlatform) {
        when (platform) {
            AutomationPlatform.INSTAGRAM -> scrollInstagramCommentPanel()
            else -> scrollCommentPanel()
        }
    }

    private fun closeCommentPanel(): Boolean {
        val closed = clickNamedButton("close comments", closeButtonKeywords())
        if (closed) {
            commentPanelOpenedFor = null
        }
        if (!closed) {
            Log.w(TAG, "close comments button not found; skip global back to avoid closing app")
            AutomationSettingsStore.saveStatus(applicationContext, "close button not found; app kept open")
        }
        return closed
    }

    private fun moveToNextVideo(platform: AutomationPlatform, strongSwipe: Boolean = false) {
        if (commentPanelOpenedFor == platform || isCommentPanelLikelyOpen(platform)) {
            if (platform == AutomationPlatform.INSTAGRAM) {
                if (isInstagramTwoPaneCommentsOpen()) {
                    swipeNextVideo(platform, strongSwipe = true)
                    AutomationSettingsStore.saveStatus(applicationContext, "${platform.label} 다음 릴스로 이동")
                    commentPanelOpenedFor = null
                    return
                }

                dismissCommentPanelWithGesture()
                commentPanelOpenedFor = null

                handler.postDelayed(
                    {
                        if (
                            AutomationSettingsStore.isEnabled(applicationContext) &&
                            automationPlatform == platform &&
                            lastObservedPackage?.let { platform.matches(it) } == true
                        ) {
                            if (!isInstagramReelsSelected()) {
                                clickInstagramReelsTab()
                                return@postDelayed
                            }
                            swipeNextVideo(platform, strongSwipe = true)
                            AutomationSettingsStore.saveStatus(applicationContext, "${platform.label} 다음 영상으로 이동")
                            commentPanelOpenedFor = null
                        }
                    },
                    1_650L
                )
                return
            }

            val closedByButton = closeCommentPanel()
            if (!closedByButton) {
                dismissCommentPanelWithGesture()
                handler.postDelayed(
                    {
                        if (
                            AutomationSettingsStore.isEnabled(applicationContext) &&
                            automationPlatform == platform &&
                            lastObservedPackage?.let { platform.matches(it) } == true &&
                            platform == AutomationPlatform.TIKTOK &&
                            (commentPanelOpenedFor == platform || isCommentPanelLikelyOpen(platform))
                        ) {
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            AutomationSettingsStore.saveStatus(applicationContext, "${platform.label} comment panel closed by back")
                            commentPanelOpenedFor = null
                        }
                    },
                    550L
                )
            }

            handler.postDelayed(
                {
                    if (
                        AutomationSettingsStore.isEnabled(applicationContext) &&
                        automationPlatform == platform &&
                        lastObservedPackage?.let { platform.matches(it) } == true
                    ) {
                        swipeNextVideo(platform, strongSwipe)
                        AutomationSettingsStore.saveStatus(applicationContext, "${platform.label} 다음 영상으로 이동")
                        commentPanelOpenedFor = null
                    }
                },
                if (platform == AutomationPlatform.TIKTOK) 1_700L else 1_200L
            )
            return
        }

        if (platform == AutomationPlatform.INSTAGRAM && !isInstagramReelsSelected()) {
            clickInstagramReelsTab()
            return
        }

        swipeNextVideo(platform, strongSwipe)
        AutomationSettingsStore.saveStatus(applicationContext, "${platform.label} 다음 영상으로 이동")
        commentPanelOpenedFor = null
    }

    private fun isInstagramReelsSelected(): Boolean {
        val root = rootInActiveWindow ?: return false
        return findNodeByViewId(root, "clips_tab")?.isSelected == true
    }

    private fun clickInstagramReelsTab(): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNodeByViewId(root, "clips_tab") ?: return false
        val clicked = clickNodeOrParent(node)
        if (clicked) {
            AutomationSettingsStore.saveStatus(applicationContext, "Instagram Reels tab selected")
            shortFormEntryAttemptedFor = AutomationPlatform.INSTAGRAM
            commentPanelOpenedFor = null
        }
        return clicked
    }

    private fun clickInstagramCommentButton(): Boolean {
        if (!isInstagramReelsSelected()) {
            clickInstagramReelsTab()
            return false
        }

        val root = rootInActiveWindow ?: return false
        val node = findInstagramCommentButton(root) ?: run {
            AutomationSettingsStore.saveStatus(applicationContext, "Instagram comment_button not found")
            return false
        }

        val clicked = tapNodeCenter(node)
        if (clicked) {
            AutomationSettingsStore.saveStatus(applicationContext, "Instagram comment_button clicked")
            Log.d(TAG, "Instagram comment_button clicked")
        }
        return clicked
    }

    private fun isInstagramTwoPaneCommentsOpen(): Boolean {
        return isInstagramTwoPaneCommentsOpen(rootInActiveWindow)
    }

    private fun isInstagramTwoPaneCommentsOpen(root: AccessibilityNodeInfo?): Boolean {
        root ?: return false
        val pane = findInstagramCommentsPane(root) ?: return false
        val rect = Rect()
        pane.getBoundsInScreen(rect)
        val rootRect = Rect().also { root.getBoundsInScreen(it) }
        val bounds = if (rootRect.width() > 0 && rootRect.height() > 0) rootRect else activeScreenBounds()
        return pane.isVisibleToUser &&
            rect.width() >= bounds.width() * 0.35f &&
            rect.height() >= bounds.height() * 0.45f
    }

    private fun isInstagramCommentSurfaceOpenAcrossWindows(): Boolean {
        if (isInstagramCommentSurfaceRoot(rootInActiveWindow)) return true

        windows?.forEach { window ->
            if (isInstagramCommentSurfaceRoot(window.root)) return true
        }

        return false
    }

    private fun isInstagramCommentSurfaceRoot(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        if (root.packageName?.toString() != INSTAGRAM_PACKAGE) return false
        if (isInstagramTwoPaneCommentsOpen(root)) return true
        if (findInstagramCommentsPane(root) != null) return true
        return containsAnyLabel(root, instagramCommentPanelKeywords())
    }

    private fun findInstagramCommentsPane(root: AccessibilityNodeInfo? = rootInActiveWindow): AccessibilityNodeInfo? {
        if (root == null) return null
        return findNodeByViewId(root, "comments_pane")
            ?: findNodeByViewId(root, "comments_container")
            ?: findNodeByViewId(root, "comments_title")
    }

    private fun findInstagramCommentButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val candidates = mutableListOf<AccessibilityNodeInfo>()

        fun dfs(node: AccessibilityNodeInfo?) {
            if (node == null) return

            val viewId = node.viewIdResourceName.orEmpty()
            val description = node.contentDescription?.toString().orEmpty()
            if (
                node.isVisibleToUser &&
                hasClickableSelfOrParent(node) &&
                (
                    viewId == "com.instagram.android:id/comment_button" ||
                        viewId.endsWith(":id/comment_button") ||
                        description == "댓글 달기" ||
                        description.equals("comment", ignoreCase = true)
                    )
            ) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) {
                    candidates += node
                }
            }

            for (index in 0 until node.childCount) {
                dfs(node.getChild(index))
            }
        }

        dfs(root)
        val bounds = activeScreenBounds()
        return candidates.maxByOrNull { node ->
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val viewId = node.viewIdResourceName.orEmpty()
            val description = node.contentDescription?.toString().orEmpty()
            var score = 0
            if (viewId == "com.instagram.android:id/comment_button") score += 220
            if (viewId.endsWith(":id/comment_button")) score += 160
            if (description == "댓글 달기") score += 140
            if (description.contains("comment", ignoreCase = true)) score += 100
            if (rect.left >= bounds.left + bounds.width() * 0.55f) score += 80
            if (rect.top >= bounds.top + bounds.height() * 0.18f) score += 40
            if (rect.left <= bounds.left + 120 && rect.top <= bounds.top + 120) score -= 160
            if (viewId.startsWith("litho:id/")) score -= 80
            score
        }
    }

    private fun scrollInstagramCommentPanel(): Boolean {
        val x = instagramCommentsPaneXFraction()
        return gestureFraction(x, 0.68f, x, 0.30f, GESTURE_DURATION_MS)
    }

    private fun instagramCommentsPaneXFraction(): Float {
        val bounds = activeScreenBounds()
        val pane = findInstagramCommentsPane() ?: return 0.50f
        val rect = Rect()
        pane.getBoundsInScreen(rect)
        if (rect.width() <= 0 || bounds.width() <= 0) return 0.50f

        val x = rect.left + rect.width() / 2f
        return ((x - bounds.left) / bounds.width()).coerceIn(0.12f, 0.88f)
    }

    private fun instagramVideoSwipeXFraction(): Float {
        val bounds = activeScreenBounds()
        val pane = findInstagramCommentsPane() ?: return 0.50f
        val rect = Rect()
        pane.getBoundsInScreen(rect)
        if (rect.width() <= 0 || bounds.width() <= 0) return 0.50f

        val rightSpace = bounds.right - rect.right
        val leftSpace = rect.left - bounds.left
        val x = when {
            rightSpace >= 160 -> rect.right + rightSpace * 0.55f
            leftSpace >= 160 -> bounds.left + leftSpace * 0.45f
            else -> bounds.left + bounds.width() * 0.50f
        }

        return ((x - bounds.left) / bounds.width()).coerceIn(0.08f, 0.92f)
    }

    private fun findNodeByViewId(node: AccessibilityNodeInfo?, viewIdPart: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (
            node.isVisibleToUser &&
            node.viewIdResourceName.orEmpty().contains(viewIdPart, ignoreCase = true)
        ) {
            return node
        }

        for (index in 0 until node.childCount) {
            val found = findNodeByViewId(node.getChild(index), viewIdPart)
            if (found != null) return found
        }

        return null
    }

    private fun dismissCommentPanelWithGesture(): Boolean {
        return gestureFraction(0.50f, 0.30f, 0.50f, 0.90f, 520L)
    }

    private fun swipeNextVideo(platform: AutomationPlatform, strongSwipe: Boolean = false) {
        val duration = if (strongSwipe) 760L else 650L
        val swiped = when (platform) {
            AutomationPlatform.INSTAGRAM -> {
                val x = instagramVideoSwipeXFraction()
                gestureFraction(x, 0.66f, x, 0.12f, duration)
            }
            AutomationPlatform.TIKTOK -> gestureFraction(0.50f, 0.76f, 0.50f, 0.16f, duration)
            AutomationPlatform.YOUTUBE -> gestureFraction(0.50f, 0.88f, 0.50f, 0.08f, duration)
        }
        if (swiped) {
            Log.d(TAG, "next video swipe dispatched")
        }
    }

    private fun skipAdIfNeeded(platform: AutomationPlatform): Boolean {
        val observedPackage = lastObservedPackage ?: return false
        if (!platform.matches(observedPackage)) return false

        val adVisible = when (platform) {
            AutomationPlatform.INSTAGRAM -> isInstagramAdVisible()
            else -> isAdVisible(platform)
        }
        if (!adVisible) return false

        val message = "${platform.label} 광고 감지: 다음 영상으로 스와이프"
        Log.d(TAG, message)
        AutomationSettingsStore.saveStatus(applicationContext, message)
        swipeAdToNextVideo(platform)
        return true
    }

    private fun swipeAdToNextVideo(platform: AutomationPlatform) {
        automationCycleId++
        commentPanelOpenedFor = null
        swipeNextVideo(platform, strongSwipe = true)
    }

    private fun isAdVisible(platform: AutomationPlatform): Boolean {
        val root = rootInActiveWindow ?: return false
        return containsLikelyAdNode(root, adKeywords(platform))
    }

    private fun isInstagramAdVisible(): Boolean {
        if (isCommentPanelLikelyOpen(AutomationPlatform.INSTAGRAM)) return false

        val root = rootInActiveWindow ?: return false
        return containsVisibleInstagramAdLabel(root)
    }

    private fun containsVisibleInstagramAdLabel(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        if (node.isVisibleToUser) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val label = buildString {
                append(node.text?.toString().orEmpty())
                append(' ')
                append(node.contentDescription?.toString().orEmpty())
            }.trim().lowercase()

            if (rect.width() > 0 && rect.height() > 0 && isInstagramAdLabel(label)) {
                return true
            }
        }

        for (index in 0 until node.childCount) {
            if (containsVisibleInstagramAdLabel(node.getChild(index))) return true
        }

        return false
    }

    private fun isInstagramAdLabel(label: String): Boolean {
        if (label.isBlank()) return false
        return label == "광고" ||
            label == "스폰서" ||
            label == "sponsored" ||
            label.contains("스폰서") ||
            label.contains("sponsored") ||
            label.contains("promoted")
    }

    private fun containsLikelyAdNode(node: AccessibilityNodeInfo?, keywords: List<String>): Boolean {
        if (node == null) return false

        val text = buildString {
            append(node.text?.toString().orEmpty())
            append(' ')
            append(node.contentDescription?.toString().orEmpty())
            append(' ')
            append(node.viewIdResourceName.orEmpty())
        }.trim()
        val lower = text.lowercase()

        if (keywords.any { keyword -> lower.contains(keyword.lowercase()) }) {
            return true
        }

        for (index in 0 until node.childCount) {
            if (containsLikelyAdNode(node.getChild(index), keywords)) return true
        }

        return false
    }

    private fun adKeywords(platform: AutomationPlatform): List<String> {
        val common = listOf(
            "광고",
            "스폰서",
            "sponsored",
            "promoted",
            "learn more",
            "더 알아보기",
            "자세히 알아보기",
            "사이트 방문",
            "visit site",
            "shop now",
            "install now",
            "앱 설치"
        )

        return when (platform) {
            AutomationPlatform.YOUTUBE -> common + listOf(
                "skip ad",
                "skip ads",
                "visit advertiser",
                "why this ad",
                "광고주",
                "광고 정보",
                "광고 건너뛰기"
            )
            AutomationPlatform.INSTAGRAM -> common + listOf(
                "더 알아보기 버튼",
                "learn more button",
                "sponsored post",
                "sponsored reel"
            )
            AutomationPlatform.TIKTOK -> emptyList()
        }
    }

    private fun isCommentPanelLikelyOpen(platform: AutomationPlatform): Boolean {
        val root = rootInActiveWindow ?: return false
        if (platform == AutomationPlatform.INSTAGRAM && isInstagramCommentSurfaceOpenAcrossWindows()) {
            return true
        }

        val panelKeywords = when (platform) {
            AutomationPlatform.YOUTUBE -> listOf(
                "add a comment",
                "write a comment",
                "comment as",
                "sort comments",
                "댓글 추가",
                "댓글 달기",
                "댓글 정렬",
                "관련성 높은 댓글"
            )
            AutomationPlatform.TIKTOK -> listOf(
                "add comment",
                "write a comment",
                "comment as",
                "comments panel",
                "댓글 추가",
                "댓글 달기",
                "댓글을 추가",
                "댓글 패널"
            )
            AutomationPlatform.INSTAGRAM -> instagramCommentPanelKeywords()
        }

        return containsAnyLabel(root, panelKeywords)
    }

    private fun instagramCommentPanelKeywords(): List<String> {
        return listOf(
            "comments",
            "add a comment",
            "write a comment",
            "comment as",
            "댓글",
            "댓글 추가",
            "댓글 달기",
            "답글 달기",
            "댓글을 남겨보세요",
            "대화 참여하기"
        )
    }

    private fun containsAnyLabel(node: AccessibilityNodeInfo?, keywords: List<String>): Boolean {
        if (node == null) return false
        val label = nodeLabel(node).lowercase()
        if (keywords.any { label.contains(it.lowercase()) }) return true

        for (index in 0 until node.childCount) {
            if (containsAnyLabel(node.getChild(index), keywords)) return true
        }

        return false
    }

    private fun shortFormButtonKeywords(platform: AutomationPlatform): List<String> {
        return when (platform) {
            AutomationPlatform.YOUTUBE -> listOf("shorts", "쇼츠")
            AutomationPlatform.INSTAGRAM -> listOf(
                "reels",
                "reel",
                "릴스",
                "릴스 탭",
                "clips",
                "clips tab"
            )
            AutomationPlatform.TIKTOK -> emptyList()
        }
    }

    private fun commentButtonKeywords(platform: AutomationPlatform): List<String> {
        return when (platform) {
            AutomationPlatform.YOUTUBE -> listOf(
                "comments",
                "comment",
                "댓글",
                "댓글 보기",
                "댓글 열기",
                "view comments",
                "open comments"
            )
            AutomationPlatform.TIKTOK -> listOf(
                "comments",
                "comment",
                "댓글",
                "댓글 보기",
                "댓글 열기",
                "view comments",
                "open comments"
            )
            AutomationPlatform.INSTAGRAM -> listOf(
                "comments",
                "comment",
                "댓글",
                "view comments",
                "open comments"
            )
        }
    }

    private fun closeButtonKeywords(): List<String> {
        return listOf(
            "close",
            "닫기",
            "뒤로",
            "취소",
            "collapse",
            "댓글 닫기",
            "댓글 패널 닫기",
            "close comments",
            "close comment",
            "close panel",
            "dismiss",
            "닫습니다"
        )
    }

    private fun hasNamedButton(keywords: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        return findBestNamedButton(root, keywords) != null
    }

    private fun clickNamedButton(actionLabel: String, keywords: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        val candidate = findBestNamedButton(root, keywords) ?: run {
            logClickableCandidates(actionLabel, root)
            return false
        }
        val clicked = clickNodeOrParent(candidate)
        if (clicked) {
            val message = "$actionLabel 버튼 클릭: ${nodeLabel(candidate).ifBlank { candidate.className?.toString().orEmpty() }}"
            Log.d(TAG, message)
            AutomationSettingsStore.saveStatus(applicationContext, message)
        }
        return clicked
    }

    private fun logClickableCandidates(actionLabel: String, root: AccessibilityNodeInfo) {
        val labels = mutableListOf<Pair<Int, String>>()

        fun dfs(node: AccessibilityNodeInfo?) {
            if (node == null) return

            val label = nodeLabel(node)
            if (node.isVisibleToUser && label.isNotBlank() && hasClickableSelfOrParent(node)) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                labels += rect.top to "${label.take(80)} @${rect.flattenToString()}"
            }

            for (index in 0 until node.childCount) {
                dfs(node.getChild(index))
            }
        }

        dfs(root)
        val snapshot = labels
            .sortedBy { it.first }
            .map { it.second }
            .distinct()
            .take(18)
            .joinToString(" | ")

        if (snapshot.isNotBlank()) {
            Log.d(TAG, "$actionLabel not found. visible clickable labels: $snapshot")
        }
    }

    private fun findBestNamedButton(
        root: AccessibilityNodeInfo,
        keywords: List<String>
    ): AccessibilityNodeInfo? {
        val candidates = mutableListOf<AccessibilityNodeInfo>()

        fun dfs(node: AccessibilityNodeInfo?) {
            if (node == null) return

            if (
                node.isVisibleToUser &&
                matchesAnyKeyword(node, keywords) &&
                hasClickableSelfOrParent(node)
            ) {
                candidates += node
            }

            for (index in 0 until node.childCount) {
                dfs(node.getChild(index))
            }
        }

        dfs(root)
        return candidates.minWithOrNull(
            compareByDescending<AccessibilityNodeInfo> { nodeMatchScore(it, keywords) }
                .thenBy { nodeArea(it) }
        )
    }

    private fun matchesAnyKeyword(node: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        val label = nodeLabel(node).lowercase()
        return keywords.any { keyword -> label.contains(keyword.lowercase()) }
    }

    private fun nodeMatchScore(node: AccessibilityNodeInfo, keywords: List<String>): Int {
        val text = node.text?.toString().orEmpty().lowercase()
        val description = node.contentDescription?.toString().orEmpty().lowercase()
        val viewId = node.viewIdResourceName.orEmpty().lowercase()
        val className = node.className?.toString().orEmpty()
        val keywordMatchedExactly = keywords.any { keyword ->
            val normalized = keyword.lowercase()
            text == normalized || description == normalized
        }

        var score = 0
        if (keywordMatchedExactly) score += 100
        if (keywords.any { description.contains(it.lowercase()) }) score += 60
        if (keywords.any { text.contains(it.lowercase()) }) score += 40
        if (keywords.any { viewId.contains(it.lowercase()) }) score += 20
        if (node.isClickable) score += 20
        if (className.contains("Button", ignoreCase = true)) score += 15
        if (className.contains("ImageButton", ignoreCase = true)) score += 15
        return score
    }

    private fun nodeLabel(node: AccessibilityNodeInfo): String {
        return buildString {
            append(node.text?.toString().orEmpty())
            append(' ')
            append(node.contentDescription?.toString().orEmpty())
            append(' ')
            append(node.viewIdResourceName.orEmpty())
        }.trim()
    }

    private fun nodeArea(node: AccessibilityNodeInfo): Int {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return rect.width().coerceAtLeast(0) * rect.height().coerceAtLeast(0)
    }

    private fun hasClickableSelfOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(6) {
            val candidate = current ?: return false
            if (hasClickAction(candidate)) return true
            current = candidate.parent
        }
        return false
    }

    private fun hasClickAction(node: AccessibilityNodeInfo): Boolean {
        return node.isClickable ||
                (node.actions and AccessibilityNodeInfo.ACTION_CLICK) != 0 ||
                node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(6) {
            val candidate = current ?: return false
            if (candidate.isClickable && candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = candidate.parent
        }

        return tapNodeCenter(node)
    }

    private fun tapNodeCenter(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) return false

        return gestureAbsolute(rect.centerX().toFloat(), rect.centerY().toFloat(), rect.centerX().toFloat(), rect.centerY().toFloat(), 1L)
    }

    private fun gestureAbsolute(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
            .build()

        val dispatched = dispatchGesture(gesture, null, null)
        if (!dispatched) {
            val message = "gesture dispatch failed: 접근성 서비스 권한/gesture 설정 확인 필요"
            Log.w(TAG, message)
            AutomationSettingsStore.saveStatus(applicationContext, message)
        }
        return dispatched
    }

    private fun swipeFraction(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ): Boolean {
        return gestureFraction(startX, startY, endX, endY, GESTURE_DURATION_MS)
    }

    private fun gestureFraction(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long
    ): Boolean {
        val bounds = activeScreenBounds()
        val path = Path().apply {
            moveTo(bounds.left + bounds.width() * startX, bounds.top + bounds.height() * startY)
            lineTo(bounds.left + bounds.width() * endX, bounds.top + bounds.height() * endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
            .build()

        val dispatched = dispatchGesture(gesture, null, null)
        if (!dispatched) {
            val message = "gesture dispatch failed: 접근성 서비스 권한/gesture 설정 확인 필요"
            Log.w(TAG, message)
            AutomationSettingsStore.saveStatus(applicationContext, message)
        }
        return dispatched
    }

    private fun activeScreenBounds(): Rect {
        val rootRect = Rect()
        rootInActiveWindow?.getBoundsInScreen(rootRect)
        if (rootRect.width() > 0 && rootRect.height() > 0) {
            return rootRect
        }

        val metrics = resources.displayMetrics
        return Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    private fun parseAndSaveCurrentWindow() {
        val parseStartedAt = System.currentTimeMillis()
        val currentPackage = lastObservedPackage ?: run {
            Log.d(TAG, "lastObservedPackage is null")
            return
        }

        if (currentPackage == YOUTUBE_PACKAGE && !isCommentPanelLikelyOpen(AutomationPlatform.YOUTUBE)) {
            Log.d(TAG, "youtube comment panel is not open; skip parse/save")
            return
        }

        if (currentPackage == INSTAGRAM_PACKAGE && !isInstagramCommentSurfaceOpenAcrossWindows()) {
            Log.d(TAG, "instagram comment surface is not open; skip parse/save")
            return
        }

        val nodes = if (currentPackage == INSTAGRAM_PACKAGE) {
            extractVisibleTextNodesFromInstagramWindows()
        } else {
            extractVisibleTextNodesFromCurrentWindow(currentPackage)
        }

        if (nodes.isEmpty()) {
            Log.d(TAG, "no visible nodes found")
            return
        }

        Log.d(TAG, "current package = $currentPackage")

        val comments = when (currentPackage) {
            YOUTUBE_PACKAGE -> YoutubeCommentExtractor.extractComments(nodes)
            INSTAGRAM_PACKAGE -> InstagramCommentExtractor.extractComments(nodes)
            TIKTOK_PACKAGE, TIKTOK_ALT_PACKAGE -> TiktokCommentExtractor.extractComments(nodes)
            else -> emptyList()
        }
        val parseFinishedAt = System.currentTimeMillis()
        val parseDurationMs = (parseFinishedAt - parseStartedAt).coerceAtLeast(1L)
        val commentsPerSecond = comments.size * 1_000.0 / parseDurationMs

        Log.d(
            TAG,
            "parsed comment count = ${comments.size}, parseDurationMs=$parseDurationMs, commentsPerSecond=$commentsPerSecond"
        )

        if (currentPackage == INSTAGRAM_PACKAGE && comments.isEmpty()) {
            Log.d(TAG, "instagram filtered node count = ${nodes.size}")
            nodes.take(60).forEachIndexed { index, node ->
                Log.d(
                    TAG,
                    "IG_RAW[$index] text=${node.displayText} | cls=${node.className} | id=${node.viewIdResourceName} | bounds=(${node.left},${node.top},${node.right},${node.bottom})"
                )
            }
        }

        if ((currentPackage == TIKTOK_PACKAGE || currentPackage == TIKTOK_ALT_PACKAGE) && comments.isEmpty()) {
            Log.d(TAG, "tiktok filtered node count = ${nodes.size}")
            nodes.take(60).forEachIndexed { index, node ->
                Log.d(
                    TAG,
                    "TT_RAW[$index] text=${node.displayText} | cls=${node.className} | id=${node.viewIdResourceName} | click=${node.isClickable}/${node.hasClickAction}/${node.hasClickableAncestor} | bounds=(${node.left},${node.top},${node.right},${node.bottom})"
                )
            }
        }

        if (comments.isEmpty()) return

        comments.take(20).forEachIndexed { index, comment ->
            Log.d(TAG, "[$index] AUTHOR=${comment.authorId.orEmpty()} | COMMENT=${comment.commentText} | BOUNDS=${comment.boundsInScreen}")
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
            sourcePackage = currentPackage,
            parseStartedAt = parseStartedAt,
            parseFinishedAt = parseFinishedAt,
            parseDurationMs = parseDurationMs,
            visibleNodeCount = nodes.size,
            parsedCommentCount = comments.size,
            commentsPerSecond = commentsPerSecond,
            comments = comments
        )

        val savedFile = JsonFileStore.saveSnapshot(applicationContext, snapshot, currentPackage)
        if (savedFile == null) {
            Log.d(TAG, "snapshot save skipped after duplicate filtering")
            lastSavedSignature = signature
            lastSavedAt = now
            return
        }

        Log.d(TAG, "snapshot saved = ${savedFile.absolutePath}")

        lastSavedSignature = signature
        lastSavedAt = now
    }

    private fun extractVisibleTextNodesFromCurrentWindow(currentPackage: String): List<ParsedTextNode> {
        val root = rootInActiveWindow ?: return emptyList()
        val tiktokMode = currentPackage == TIKTOK_PACKAGE || currentPackage == TIKTOK_ALT_PACKAGE
        return collectFilteredNodesFromRoot(
            root = root,
            instagramMode = false,
            tiktokMode = tiktokMode
        )
    }

    private fun extractVisibleTextNodesFromInstagramWindows(): List<ParsedTextNode> {
        if (!isInstagramCommentSurfaceOpenAcrossWindows()) return emptyList()

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

        return collectFilteredNodesFromRoot(
            root = pickedRoot,
            instagramMode = true,
            tiktokMode = false
        )
    }

    private fun collectFilteredNodesFromRoot(
        root: AccessibilityNodeInfo,
        instagramMode: Boolean,
        tiktokMode: Boolean
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
            if (shouldKeepNode(node, parsed.displayText.orEmpty(), rect, root, instagramMode, tiktokMode)) {
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
            isVisibleToUser = node.isVisibleToUser,
            isClickable = node.isClickable,
            hasClickAction = hasClickAction(node),
            hasClickableAncestor = hasClickableSelfOrParent(node)
        )
    }

    private fun shouldKeepNode(
        node: AccessibilityNodeInfo,
        value: String,
        rect: Rect,
        root: AccessibilityNodeInfo,
        instagramMode: Boolean = false,
        tiktokMode: Boolean = false
    ): Boolean {
        val className = node.className?.toString().orEmpty()
        val trimmed = value.trim()
        val lower = trimmed.lowercase()
        val viewId = node.viewIdResourceName.orEmpty()
        val rootRect = Rect().also { root.getBoundsInScreen(it) }
        val screenHeight = if (rootRect.height() > 0) rootRect.height() else rect.bottom
        val upperCutoff = if (instagramMode) (screenHeight * 0.08f).toInt() else (screenHeight * 0.28f).toInt()

        if (!node.isVisibleToUser) return false
        if (rect.bottom <= upperCutoff) return false
        if (trimmed.length == 1 && !trimmed[0].isLetterOrDigit() && trimmed[0] !in listOf('@', '#')) return false

        if (!tiktokMode && Regex(""".+님의 프로필$""").matches(trimmed)) return false
        if (Regex("""^[\u200E\u200F\u202A-\u202E]*댓글\s*[\d,]+개$""").matches(trimmed)) return false

        if (instagramMode) {
            if (isInstagramNonCommentUiText(trimmed)) return false
            if (looksLikeCountOnlyText(trimmed)) return false

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
                trimmed == "활동" ||
                trimmed == "만들기" ||
                trimmed == "프로필" ||
                trimmed == "프로필 사진" ||
                trimmed == "대화 참여하기..." ||
                trimmed == "회원님의 생각을 남겨보세요." ||
                trimmed == "댓글 달기" ||
                trimmed == "저장" ||
                trimmed == "관심 없음" ||
                trimmed == "관심 있음" ||
                trimmed == "숨겨진 댓글 보기" ||
                trimmed == "캡션" ||
                trimmed == "릴스" ||
                trimmed.contains("님에게 댓글 추가") ||
                (trimmed.contains("님 외") && trimmed.contains("좋아합니다")) ||
                trimmed.contains("명이 좋아합니다")
            ) return false
        }

        if (tiktokMode) {
            if (
                lower == "알림" ||
                lower == "스티커" ||
                lower == "사진" ||
                lower == "엄지척" ||
                lower == "아래" ||
                lower == "동영상" ||
                lower == "댓글" ||
                lower == "게시물" ||
                lower == "첫 댓글" ||
                lower == "ai 생성 미디어 포함" ||
                lower == "크리에이터가 댓글 액세스를 제한했습니다." ||
                lower.contains("효과 사용") ||
                lower == "video" ||
                lower == "notification" ||
                lower == "sticker" ||
                isTiktokNonCommentUiText(trimmed) ||
                lower.contains("멘션") ||
                lower.contains("말 한마디 해주세요") ||
                lower.contains("자세히") ||
                lower.startsWith("#") ||
                Regex("""^[\d,]+$""").matches(trimmed) ||
                Regex("""^\d{2}-\d{2}$""").matches(trimmed)
            ) return false

            if (
                viewId.contains("sticker", ignoreCase = true) ||
                viewId.contains("notification", ignoreCase = true)
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
            trimmed == "답글" ||
            trimmed.contains("정렬") ||
            trimmed == "뒤로" ||
            trimmed == "닫기"
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
        if (trimmed.endsWith("좋아요")) return false
        if (className.contains("Button", ignoreCase = true)) {
            if (!tiktokMode || !looksLikeTiktokAuthorButton(node, trimmed)) return false
        }

        return true
    }

    private fun looksLikeTiktokAuthorButton(node: AccessibilityNodeInfo, text: String): Boolean {
        val t = text.trim()
        if (!hasClickableSelfOrParent(node)) return false
        if (t.isBlank() || t.contains('\n')) return false
        if (t.length !in 2..40) return false
        if (isLikelyTiktokUiLabel(t)) return false
        if (hasEmojiOrOtherSymbol(t)) return false

        val author = t
            .removePrefix("@")
            .replace(Regex("""님의\s*프로필(로\s*이동)?$"""), "")
            .replace(Regex("""의\s*프로필(로\s*이동)?$"""), "")
            .trim()

        if (author.isBlank()) return false
        if (author.any { it.isWhitespace() }) return false
        if (author.all { it.isDigit() }) return false
        if (!author.all { it.isLetterOrDigit() || it == '_' || it == '.' }) return false

        return t.startsWith("@") ||
                author.any { it.isDigit() || it == '_' || it == '.' } ||
                author.any { it in 'A'..'Z' || it in 'a'..'z' } ||
                author.count { it in '\uAC00'..'\uD7A3' } in 2..12
    }

    private fun isLikelyTiktokUiLabel(text: String): Boolean {
        val lower = text.trim().lowercase()
        val compact = lower.replace(Regex("\\s+"), "")

        if (isTiktokNonCommentUiText(text)) return true

        if (compact in setOf(
                "now",
                "justnow",
                "방금",
                "방금전",
                "오늘",
                "어제",
                "편집효과",
                "음악",
                "숨겨짐",
                "검색",
                "공유",
                "좋아요",
                "댓글",
                "답글",
                "팔로우",
                "프로필",
                "동영상",
                "스티커",
                "알림",
                "작성자",
                "사진",
                "게시물",
                "첫댓글",
                "·효과사용",
                "ai생성미디어포함"
            )
        ) return true

        return Regex("""^\d{1,2}-\d{1,2}$""").matches(lower) ||
                Regex("""^\d{1,2}:\d{2}$""").matches(lower) ||
                Regex("""^\d+(\.\d+)?[smhdw]$""").matches(lower) ||
                Regex("""^\d+(\.\d+)?(초|분|시간|일|주|개월|달|년)(전)?$""").matches(compact) ||
                Regex("""^\d+([.,]\d+)?(천|만|개|명|회|k|m)?$""").matches(compact) ||
                lower.endsWith("좋아요") ||
                lower.endsWith("likes") ||
                lower.endsWith("like")
    }

    private fun isTiktokNonCommentUiText(text: String): Boolean {
        val lower = text.trim()
            .lowercase()
            .replace(Regex("""[\u200E\u200F\u202A-\u202E\u2066-\u2069]"""), "")
            .trim()
        val compact = lower.replace(Regex("\\s+"), "")

        return lower.startsWith("검색 ·") ||
                lower.startsWith("검색·") ||
                lower.startsWith("검색:") ||
                lower.startsWith("search ·") ||
                Regex("""^@\d{5,}$""").matches(lower) ||
                lower == "ai 생성 미디어 포함" ||
                lower == "사진" ||
                lower == "게시물" ||
                lower == "첫 댓글" ||
                compact == "ai생성미디어포함" ||
                compact == "첫댓글" ||
                lower.contains("효과 사용") ||
                Regex("""^댓글\s*[\d,]+개$""").matches(lower) ||
                Regex("""^협업자\s*[\d,]+명$""").matches(lower) ||
                Regex("""^게시물\s*[\d,.]+[km천만]?개$""").matches(lower) ||
                lower.contains("님이 게시한 동영상이 여기에 나타납니다")
                || lower.contains("크리에이터가 댓글 액세스를 제한했습니다")
    }

    private fun hasEmojiOrOtherSymbol(text: String): Boolean {
        return text.any {
            val type = Character.getType(it)
            type == Character.OTHER_SYMBOL.toInt() ||
                    type == Character.SURROGATE.toInt()
        }
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

    private fun looksLikeCountOnlyText(text: String): Boolean {
        val t = text.trim()
        return Regex("""^\d{1,3}(,\d{3})+$""").matches(t) ||
                Regex("""^\d+(\.\d+)?[kKmM만천]?$""").matches(t)
    }

    private fun isInstagramNonCommentUiText(text: String): Boolean {
        val lower = text.trim().lowercase()
        if (lower.isBlank()) return true
        return lower.contains("님의 스토리") ||
                lower.contains("읽지 않은 스토리") ||
                lower.contains("게시했습니다") ||
                (lower.contains("게시물") && lower.contains("태그했습니다")) ||
                (lower.contains("님의 사진") && lower.contains("좋아요") && lower.contains("댓글")) ||
                (lower.contains("님의 동영상") && lower.contains("좋아요") && lower.contains("댓글")) ||
                (lower.contains("님의 carousel") && lower.contains("좋아요") && lower.contains("댓글")) ||
                lower.contains("photo을(를) 게시") ||
                lower.contains("video을(를) 게시") ||
                lower.contains("carousel을(를) 게시")
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

    private enum class AutomationPlatform(val label: String) {
        YOUTUBE("YouTube"),
        TIKTOK("TikTok"),
        INSTAGRAM("Instagram");

        fun matches(packageName: String): Boolean {
            return when (this) {
                YOUTUBE -> packageName == "com.google.android.youtube"
                TIKTOK -> packageName == "com.zhiliaoapp.musically" ||
                    packageName == "com.ss.android.ugc.trill"
                INSTAGRAM -> packageName == "com.instagram.android"
            }
        }
    }
}
