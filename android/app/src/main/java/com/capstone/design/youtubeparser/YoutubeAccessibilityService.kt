package com.capstone.design.youtubeparser

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class YoutubeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "YTParserService"
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val TIKTOK_PACKAGE = "com.zhiliaoapp.musically"
        private const val TIKTOK_ALT_PACKAGE = "com.ss.android.ugc.trill"
        private const val CHROME_PACKAGE = "com.android.chrome"
        private val BROWSER_PACKAGES = setOf(
            CHROME_PACKAGE,
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "com.google.android.googlequicksearchbox",
            "com.google.android.apps.searchlite",
            "com.android.browser",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.sec.android.app.sbrowser"
        )
        private const val MIN_UPLOAD_INTERVAL_MS = 1000L
        private const val PARSE_DELAY_TEXT_MS = 12L
        private const val PARSE_DELAY_SCROLL_MS = 8L
        private const val SCROLL_OVERLAY_STABILIZATION_MS = 24L
        private const val CONTENT_OVERLAY_STABILIZATION_MS = 24L
        private const val SCROLL_CONTENT_CHANGE_PRESERVE_MS =
            SCROLL_OVERLAY_STABILIZATION_MS + CONTENT_OVERLAY_STABILIZATION_MS
        private const val OVERLAY_SELF_CONTENT_CHANGE_GRACE_MS = 220L
        private const val PARSE_DELAY_CONTENT_MS = 40L
        private const val PARSE_DELAY_WINDOW_MS = 60L
        private const val RETRY_AFTER_IN_FLIGHT_MS = 16L
        private const val VISUAL_SUPPLEMENT_CACHE_TTL_MS = 1800L
        private const val YOUTUBE_SAFE_FADE_OUT_MS = 200L
        private const val YOUTUBE_LOADING_MAX_VISIBLE_MS = 2_500L
        private const val YOUTUBE_LOADING_SUPPRESS_AFTER_MAX_MS = 6_000L
        private const val YOUTUBE_SCROLL_LOADING_HOLD_MS = 500L
        private const val YOUTUBE_ANCHOR_TRACK_INTERVAL_MS = 16L
        private const val YOUTUBE_ANCHOR_TRACK_TAIL_MS = 400L
        private const val YOUTUBE_RESUME_LOADING_CACHE_TTL_MS = 5 * 60_000L
        private const val YOUTUBE_NATIVE_PANEL_CONFIRMATION_TTL_MS = 12_000L
        private const val YOUTUBE_COMMENT_PANEL_CONTENT_VIEW_ID =
            "com.google.android.youtube:id/panel_content_touch_wrapper"
        private val YOUTUBE_COMMENT_PANEL_CONTENT_VIEW_IDS = setOf(
            YOUTUBE_COMMENT_PANEL_CONTENT_VIEW_ID,
            "com.google.android.youtube:id/panel_content",
            "com.google.android.youtube:id/engagement_panel_content"
        )
        private const val YOUTUBE_COMMENT_SORT_MARKER_VIEW_ID =
            "com.google.android.youtube:id/sort_menu_anchor"
        private const val YOUTUBE_COMMENT_PANEL_CLOSE_VIEW_ID =
            "com.google.android.youtube:id/close_button"
        private const val YOUTUBE_COMMENT_RESULTS_VIEW_ID =
            "com.google.android.youtube:id/results"
        private const val YOUTUBE_COMMENT_PANEL_TITLE_VIEW_ID =
            "com.google.android.youtube:id/modern_title"
        private const val YOUTUBE_COMMENT_ROW_MAX_PARENT_DEPTH = 7
        private const val YOUTUBE_COMMENT_ROW_MAX_SEARCH_DEPTH = 8
        private const val YOUTUBE_REPLY_REBIND_INTERVAL_MS = 80L
        private const val YOUTUBE_NATIVE_FAST_SCAN_MAX_DEPTH = 10
        private const val YOUTUBE_NATIVE_FAST_SCAN_MAX_NODES = 48
        private const val YOUTUBE_SAFE_MIRROR_ENABLED = true
        private const val YOUTUBE_VISUAL_OCR_ENABLED = false
        private const val INSTAGRAM_SAFE_MIRROR_ENABLED = true
        private const val INSTAGRAM_PANEL_PROBE_INTERVAL_MS = 90L
        private val INSTAGRAM_PANEL_PROBE_RETRY_DELAYS_MS =
            longArrayOf(60L, 180L, 420L)
        private const val YOUTUBE_MIRROR_NEXT_STEP_DELAY_MS = 120L
        private const val YOUTUBE_MIRROR_PREFETCH_SCROLL_SETTLE_MS = 620L
        private const val YOUTUBE_MIRROR_CAPTURE_HIDE_SETTLE_MS = 48L
        private const val YOUTUBE_MIRROR_SEED_CAPTURE_THROTTLE_MS = 80L
        private const val YOUTUBE_MIRROR_SEED_TARGET_COUNT = 3
        private const val YOUTUBE_MIRROR_CAPTURE_SETTLE_MS = 120L
        private const val YOUTUBE_MIRROR_BATCH_TIMEOUT_MS = 15_000L
        private const val YOUTUBE_MIRROR_INITIAL_TIMEOUT_MS = 28_000L
        private const val YOUTUBE_MIRROR_VISUAL_ANALYSIS_TIMEOUT_MS = 15_000L
        private const val YOUTUBE_MIRROR_MAX_EMPTY_RETRIES = 2
        private const val YOUTUBE_MIRROR_PANEL_PRESENCE_DELAY_MS = 120L
        private const val YOUTUBE_MIRROR_PANEL_AUDIT_INTERVAL_MS = 250L
        private const val YOUTUBE_MIRROR_PANEL_MISSING_GRACE_MS = 900L
        private const val YOUTUBE_MIRROR_PANEL_OPENING_GRACE_MS = 900L
        private const val YOUTUBE_MIRROR_REOPEN_GUARD_MS = 1_200L
        private const val YOUTUBE_MIRROR_SEED_TTL_MS = 6_000L
        private const val YOUTUBE_AUTO_PRECHECK_ENABLED = false
        private const val YOUTUBE_PRECHECK_FORWARD_STEPS = 5
        private const val YOUTUBE_PRECHECK_RETRIGGER_USER_SCROLLS = 5
        private const val YOUTUBE_PRECHECK_SCROLL_DELAY_MS = 560L
        private const val YOUTUBE_PRECHECK_RETURN_DELAY_MS = 260L
        private const val YOUTUBE_PRECHECK_MAX_DURATION_MS = 16_000L
        private const val YOUTUBE_PRECHECK_COOLDOWN_MS = 2_500L
        private const val YOUTUBE_PRECHECK_SYNTHETIC_SCROLL_GRACE_MS = 1_200L
        private const val VISUAL_ANALYSIS_TIMEOUT_MS = 4_200L
        private const val MAX_VISUAL_ANALYSIS_CANDIDATES = 16
        private const val MAX_FALLBACK_VISUAL_CANDIDATES = 12
        private const val FAST_PROVISIONAL_MAX_DEPTH = 3
        private const val FAST_PROVISIONAL_MAX_NODES = 12
        private const val FAST_PROVISIONAL_MAX_RESULTS = 3
        private const val FAST_PROVISIONAL_MIN_WIDTH_PX = 18
        private const val FAST_PROVISIONAL_MIN_HEIGHT_PX = 14
        private const val FAST_PROVISIONAL_MAX_TEXT_LENGTH = 180
        private const val FAST_PROVISIONAL_MAX_SCREEN_WIDTH_RATIO = 0.96f
        private const val FAST_PROVISIONAL_MAX_SCREEN_HEIGHT_RATIO = 0.38f
        private const val FAST_BROWSER_ROOT_MIN_INTERVAL_MS = 48L
        private const val FAST_BROWSER_ROOT_RETRY_DELAY_MS = 160L
        private const val FAST_BROWSER_ROOT_MAX_ATTEMPTS = 24
        private const val FAST_BROWSER_ROOT_MAX_DEPTH = 5
        private const val FAST_BROWSER_ROOT_MAX_NODES = 48
        private const val FAST_BROWSER_ROOT_MAX_RESULTS = 3
        private const val FAST_BROWSER_ROOT_MAX_TEXT_LENGTH = 220
        private const val FAST_BROWSER_ROOT_COMPACT_MIN_TOP_PX = 180
        private const val FAST_BROWSER_ROOT_COMPACT_MAX_WIDTH_PX = 520
        private const val FAST_BROWSER_ROOT_COMPACT_MAX_HEIGHT_PX = 110
        private val FAST_PROVISIONAL_WHITESPACE_PATTERN = Regex("\\s+")
        private const val RISK_GATE_MIN_SENSITIVITY = 70
        private const val RISK_GATE_TTL_MS = 1200L
        private const val RISK_GATE_MIN_INTERVAL_MS = 80L
        private const val RISK_GATE_SIDE_MARGIN_RATIO = 0.06f
        private const val RISK_GATE_MIN_WIDTH_PX = 120
        private const val RISK_GATE_MIN_HEIGHT_PX = 56
        private const val RISK_GATE_SOURCE_PADDING_PX = 18
        private const val RISK_GATE_MAX_SOURCE_HEIGHT_RATIO = 0.42f
        private const val VISUAL_DUPLICATE_OVERLAP_RATIO = 0.45f
        private const val VISUAL_GEOMETRY_DUPLICATE_OVERLAP_RATIO = 0.72f
        private const val VISUAL_CONTAINED_DUPLICATE_OVERLAP_RATIO = 0.28f
        private const val VISUAL_COARSE_BASE_AREA_MULTIPLIER = 3.0f
        private const val TOP_CONTROL_OCR_EXCLUSION_MAX_PX = 220
        private const val TOP_CONTROL_OCR_EXCLUSION_RATIO = 0.12f
        private const val CACHE_PROMOTION_THROTTLE_MS = 120L
        private const val YOUTUBE_CACHE_PROMOTION_THROTTLE_MS = 32L
        private const val DEBUG_YOUTUBE_HARNESS_ACTIVITY =
            "com.capstone.design.DebugYoutubeMaskHarnessActivity"
        private const val DEBUG_INSTAGRAM_HARNESS_ACTIVITY =
            "com.capstone.design.DebugInstagramMirrorHarnessActivity"
        private const val MAX_CHARACTER_LOCATION_TEXT_LENGTH = 320
        private val PRECISE_YOUTUBE_VISUAL_SOURCES = setOf(
            "youtube-composite-card",
            "youtube-visible-band",
            "youtube-comment-panel"
        )
        private const val BROWSER_TEXT_NODE_SOURCE = "browser-text-node"
        private const val BROWSER_VISUAL_NODE_SOURCE = "browser-visual-region"
        private const val FULL_SCREEN_BASELINE_SOURCE = "full-screen-baseline"
        private const val YOUTUBE_SEMANTIC_FALLBACK_SOURCE = "youtube-semantic-card"
        private val OBSERVATION_EXCLUDED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.sec.android.inputmethod",
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.android.settings"
        )
        private val OVERLAY_EXIT_PACKAGES = setOf(
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.android.settings"
        )
    }

    private data class YoutubeReplyAnchorMatch(
        val replyNode: AccessibilityNodeInfo,
        val replyBounds: BoundsRect,
        val authorBounds: BoundsRect,
        val rowBounds: BoundsRect
    )

    private data class YoutubeInferredReplyAnchor(
        val authorLabel: String,
        val match: YoutubeReplyAnchorMatch
    )

    private data class YoutubeKnownHarmfulComment(
        val key: String,
        var authorLabel: String?,
        var fallbackSpec: MaskOverlaySpec,
        var anchoredMask: YoutubeReplyAnchoredMask? = null,
        var replyNode: AccessibilityNodeInfo? = null
    )

    private enum class YoutubeMirrorCollectionMode {
        IDLE,
        INITIAL,
        PREFETCH
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastSnapshotSignature: String? = null
    private var lastUploadAt: Long = 0L
    private var lastObservedPackage: String? = null
    private val maskOverlayController by lazy { MaskOverlayController(this) }
    private val youtubeSafeCommentMirrorController by lazy {
        YoutubeSafeCommentMirrorController(
            service = this,
            onNeedMore = { startYoutubeMirrorPrefetch() },
            onDismiss = { dismissYoutubeCommentPanelFromMirror() }
        )
    }
    private val instagramSafeCommentMirrorSession by lazy {
        InstagramSafeCommentMirrorSession(
            service = this,
            handler = handler,
            isInstagramForeground = { lastObservedPackage == INSTAGRAM_PACKAGE },
            captureSnapshot = { source -> buildInstagramMirrorSnapshot(source) },
            scrollForward = { performInstagramCommentScroll() },
            isPanelPresent = { detectInstagramCommentSurface() != null },
            cancelGeneralAnalysis = { cancelScheduledParse() },
            clearLegacyOverlay = { clearMaskOverlay() }
        )
    }
    private val youtubeSafeCommentBuffer = YoutubeSafeCommentBuffer()
    @Volatile private var analysisInFlight = false
    @Volatile private var pendingParseAfterAnalysis = false
    @Volatile private var followUpParseRequested = false
    @Volatile private var overlayRevision = 0L
    @Volatile private var visualSceneRevision = 0L
    private var parseScheduled = false
    private var scheduledParseAtMs = 0L
    private var scheduledParseRequestedAtMs = 0L
    private var scheduledParseEventType: Int? = null
    private var lastScrollEventAtMs = 0L
    private var lastAbsoluteScrollX: Int? = null
    private var lastAbsoluteScrollY: Int? = null
    private var lastPointerInteractionAtMs = 0L
    private var lastOverlayContentChangeAtMs = 0L
    private var lastCachePromotionAtMs = 0L
    private var debugYoutubeHarnessActive = false
    private var debugInstagramHarnessActive = false
    private var lastInstagramPanelProbeAtMs = 0L
    private var instagramPanelProbeGeneration = 0L
    private var instagramMirrorReopenSuppressedUntilMs = 0L
    private var lastAppliedSensitivity: Int? = null
    private var lastAppliedExperimentMode: PipelineExperimentMode? = null
    private var visualCaptureState: VisualTextCaptureState =
        VisualTextCaptureSupport.inspect(serviceInfo = null)
    private val visualExecutor = Executors.newSingleThreadExecutor()
    private val parseComputeExecutor = Executors.newFixedThreadPool(2)
    private val visualTextOcrProcessor by lazy { VisualTextOcrProcessor() }
    @Volatile private var visualAnalysisInFlight = false
    @Volatile private var visualAnalysisRunId = 0L
    @Volatile private var lastVisualSupplement: VisualSupplementCache? = null
    @Volatile private var lastVisualAnalysisStartedAtMs = 0L
    @Volatile private var lastVisualRefreshSignature: String? = null
    @Volatile private var lastVisualRefreshCompletedAtMs: Long = 0L
    private var lastScreenshotRequestAtMs = 0L
    private var preservedRecentVisualMiss = false
    private var preservedRecentAnalysisFailure = false
    private var provisionalVisualMaskActive = false
    private var provisionalAccessibilityMaskActive = false
    private var riskGateActive = false
    private var lastYoutubeCommentPaneSpec: MaskOverlaySpec? = null
    private var lastYoutubeBlockedSpecs: List<MaskOverlaySpec> = emptyList()
    private var lastYoutubeScrollLoadingSpec: MaskOverlaySpec? = null
    private var lastYoutubeCommentPanelConfirmedAtMs = 0L
    private var lastYoutubeNativeCommentPanelConfirmedAtMs = 0L
    private var youtubeCommentInitialAnalysisCompleted = false
    private val youtubeKnownHarmfulComments = linkedMapOf<String, YoutubeKnownHarmfulComment>()
    private var lastYoutubeReplyRebindAtMs = 0L
    private var youtubeAnchorTrackingRunId = 0L
    private var youtubeAnchorTrackingUntilMs = 0L
    private var youtubeKnownCommentPanelSpec: MaskOverlaySpec? = null
    private var youtubeKnownCommentPanelCapturedAtMs = 0L
    private var youtubeResumeLoadingSpec: MaskOverlaySpec? = null
    private var youtubeResumeLoadingCapturedAtMs = 0L
    private var youtubeResumeWindowClassName: String? = null
    private var lastYoutubeWindowClassName: String? = null
    private var youtubeLoadingOverlayStartedAtMs = 0L
    private var youtubeLoadingSuppressedUntilMs = 0L
    private var youtubeTouchInteractionActive = false
    private var youtubeAutoPrecheckActive = false
    private var youtubeAutoPrecheckRunId = 0L
    private var youtubeAutoPrecheckForwardSteps = 0
    private var youtubeAutoPrecheckPendingAnalyses = 0
    private var youtubeAutoPrecheckReturnDone = false
    private var youtubeUserScrollsSincePrecheck = 0
    private var youtubeSyntheticScrollUntilMs = 0L
    private var lastYoutubeAutoPrecheckAnchorKey: String? = null
    private var lastYoutubeAutoPrecheckFinishedAtMs = 0L
    private var youtubeMirrorPanelSpec: MaskOverlaySpec? = null
    private var youtubeMirrorSessionRunId = 0L
    private var youtubeMirrorCollectionMode = YoutubeMirrorCollectionMode.IDLE
    private var youtubeMirrorNativeScrollNode: AccessibilityNodeInfo? = null
    private var youtubeMirrorCapturedViewports = 0
    private var youtubeMirrorEmptyRetries = 0
    private var youtubeMirrorAwaitingBatch = false
    private var youtubeMirrorExpectedSnapshotTimestampMs = 0L
    private var youtubeMirrorParsedCommentCount = 0
    private var youtubeMirrorAnalysisError: String? = null

    private var youtubeMirrorReachedEnd = false
    private var youtubeMirrorCaptureAttemptId = 0L
    private var youtubeMirrorPanelMissingSinceMs = 0L
    private var youtubeMirrorPanelNativeObserved = false
    private var youtubeMirrorPanelAuditScheduledRunId = -1L
    private var youtubeMirrorReopenSuppressedUntilMs = 0L
    private var youtubeMirrorSessionStartedAtUptimeMs = 0L
    private var youtubeMirrorSessionStartedAtEpochMs = 0L
    private var youtubeMirrorSeedSnapshot: ParseSnapshot? = null
    private var youtubeMirrorSeedCapturedAtUptimeMs = 0L
    private var youtubeMirrorLastSeedCaptureAtMs = 0L
    private val youtubeMirrorSeenViewportSignatures = linkedSetOf<String>()
    private var riskGateRevision = 0L
    private var lastRiskGateAtMs = 0L
    @Volatile private var lastRiskGateMaskMs: Long = -1L
    @Volatile private var lastRiskGateEventAgeMs: Long = -1L
    @Volatile private var lastRiskGateReceiveToMaskMs: Long = -1L
    private var lastBrowserRootFastScanAtMs = 0L
    @Volatile private var lastFastProvisionalMaskMs: Long = -1L
    @Volatile private var lastFastProvisionalEventAgeMs: Long = -1L
    @Volatile private var lastFastProvisionalBuildMs: Long = -1L
    @Volatile private var lastFastProvisionalOverlayMs: Long = -1L
    @Volatile private var lastFastProvisionalReceiveToMaskMs: Long = -1L
    @Volatile private var lastFastProvisionalAtMs: Long = 0L
    private val sensitivityPreferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (
                key != AnalysisSensitivityStore.KEY_ANALYSIS_SENSITIVITY &&
                key != PipelineExperimentStore.KEY_PIPELINE_EXPERIMENT_MODE
            ) {
                return@OnSharedPreferenceChangeListener
            }

            handler.post {
                syncSensitivityState()
                if (lastObservedPackage != null) {
                    scheduleParse(0L)
                }
            }
        }

    private data class VisualSupplementCache(
        val packageName: String,
        val sensitivity: Int,
        val visualRoiSignature: String,
        val response: AndroidAnalysisResponse,
        val expiresAtUptimeMs: Long
    )

    private data class AnalysisTextLocation(
        val keys: Set<String>,
        val boundsInScreen: BoundsRect,
        val authorId: String?
    )

    private data class ParseCandidateComputation(
        val visualRoiPlan: VisualTextRoiPlan,
        val screenCandidates: List<ScreenTextCandidate>,
        val visualRoiPlanningMs: Long,
        val screenCandidateExtractionMs: Long,
        val parallelWaitMs: Long
    )

    private data class TimedVisualRoiPlan(
        val plan: VisualTextRoiPlan,
        val elapsedMs: Long
    )

    private data class TimedScreenCandidates(
        val candidates: List<ScreenTextCandidate>,
        val elapsedMs: Long
    )

    private data class ScrollTranslationResult(
        val status: MaskOverlayTranslationStatus?,
        val hasResolvedScrollDelta: Boolean
    ) {
        val translated: Boolean
            get() = status == MaskOverlayTranslationStatus.TRANSLATED

        val shouldHideUntilRecapture: Boolean
            get() = status == MaskOverlayTranslationStatus.REJECTED_DELTA ||
                status == MaskOverlayTranslationStatus.NO_TRANSLATABLE_MASKS ||
                status == MaskOverlayTranslationStatus.ALL_OFFSCREEN
    }

    private val parseRunnable = Runnable {
        val parseStartedAtMs = SystemClock.uptimeMillis()
        val triggerEventType = scheduledParseEventType
        val parseDelayMs = if (scheduledParseRequestedAtMs > 0L) {
            parseStartedAtMs - scheduledParseRequestedAtMs
        } else {
            -1L
        }
        parseScheduled = false
        scheduledParseAtMs = 0L
        scheduledParseRequestedAtMs = 0L
        scheduledParseEventType = null
        parseAndUploadCurrentWindow(
            triggerEventType = triggerEventType,
            parseStartedAtMs = parseStartedAtMs,
            parseDelayMs = parseDelayMs
        )
        if (followUpParseRequested && !analysisInFlight) {
            followUpParseRequested = false
            scheduleDeferredFollowUpParse()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        visualCaptureState = VisualTextCaptureSupport.inspect(serviceInfo)
        applicationContext
            .getSharedPreferences(AnalysisSensitivityStore.PREFS_NAME, MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(sensitivityPreferenceListener)
        syncSensitivityState()
        Log.d(TAG, "service connected")
        maskOverlayController.prewarm()
        Log.d(
            TAG,
            "visual text capture supported=${visualCaptureState.supported} " +
                "sdk=${visualCaptureState.sdkInt} reason=${visualCaptureState.reason}"
        )

    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (
            event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode == KeyEvent.KEYCODE_BACK &&
            lastObservedPackage == YOUTUBE_PACKAGE &&
            youtubeSafeCommentMirrorController.isActive
        ) {
            Log.d(TAG, "remove youtube mirror immediately for back key")
            clearMaskOverlay()
            invalidateYoutubeCommentPanelSession("comment-panel-back-key")
        }
        if (
            event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode == KeyEvent.KEYCODE_BACK &&
            lastObservedPackage == INSTAGRAM_PACKAGE &&
            instagramSafeCommentMirrorSession.isActive
        ) {
            Log.d(TAG, "remove instagram mirror immediately for back key")
            invalidateInstagramMirrorSession("comment-panel-back-key")
            clearMaskOverlay()
        }
        return false
    }

    @SuppressLint("SwitchIntDef")
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val callbackReceivedAtMs = SystemClock.uptimeMillis()

        val eventPackageName = event.packageName?.toString() ?: return
        val packageName = resolveObservedPackage(eventPackageName, event)
        if (!shouldObservePackage(packageName)) {
            if (
                lastObservedPackage == INSTAGRAM_PACKAGE &&
                MaskOverlayEventPolicy.shouldClearOverlayForExitPackage(
                    eventType = event.eventType,
                    isExitPackage = packageName in OVERLAY_EXIT_PACKAGES
                )
            ) {
                invalidateInstagramMirrorSession("package-exit:$packageName")
            }
            val launchGateRendered = renderYoutubeResumeLoadingGateForLaunchClick(
                event = event,
                packageName = packageName
            )
            if (!launchGateRendered) {
                clearOverlayForExitPackageIfNeeded(
                    packageName = packageName,
                    eventType = event.eventType
                )
            }
            return
        }

        val previousObservedPackage = lastObservedPackage
        if (previousObservedPackage == YOUTUBE_PACKAGE && packageName != YOUTUBE_PACKAGE) {
            rememberYoutubeResumeLoadingGate()
        }
        if (
            previousObservedPackage == INSTAGRAM_PACKAGE &&
            packageName != INSTAGRAM_PACKAGE
        ) {
            invalidateInstagramMirrorSession("package-change:$packageName")
        }
        lastObservedPackage = packageName

        if (packageName == YOUTUBE_PACKAGE) {
            restoreYoutubeResumeLoadingGateIfEligible(
                event = event,
                wasYoutubeObserved = previousObservedPackage == YOUTUBE_PACKAGE
            )
            val eventWindowClassName = event.className?.toString().orEmpty()
            if (
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                eventWindowClassName.isNotBlank() &&
                (eventPackageName == YOUTUBE_PACKAGE || eventWindowClassName.endsWith("Activity"))
            ) {
                lastYoutubeWindowClassName = eventWindowClassName
            }
        }

        if (eventPackageName == YOUTUBE_PACKAGE) {
            captureYoutubeMirrorSeedFromEvent(event)
        }

        if (
            packageName == YOUTUBE_PACKAGE &&
            youtubeSafeCommentMirrorController.isActive
        ) {
            if (
                event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
                isYoutubeCommentPanelCloseClick(event.source)
            ) {
                clearMaskOverlay()
                invalidateYoutubeCommentPanelSession("comment-close-click")
                return
            }
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_TOUCH_INTERACTION_END,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOWS_CHANGED ->
                    scheduleYoutubeMirrorPanelPresenceAudit("event-${event.eventType}")
            }
        }
        if (
            packageName == YOUTUBE_PACKAGE &&
            youtubeSafeCommentMirrorController.isActive
        ) {
            return
        }
        if (packageName == INSTAGRAM_PACKAGE && handleInstagramSafeMirrorEvent(event)) {
            return
        }

        if (packageName != YOUTUBE_PACKAGE) {
            youtubeTouchInteractionActive = false
            invalidateYoutubeCommentPanelSession("package-change:$packageName")
            cancelYoutubeAutoPrecheck("package-change:$packageName")
        } else if (youtubeAutoPrecheckActive && event.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
            cancelYoutubeAutoPrecheck("user-touch")
        } else if (shouldSuppressYoutubeAutoPrecheckEvent(event.eventType)) {
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                lastScrollEventAtMs = SystemClock.uptimeMillis()
            }
            Log.d(TAG, "suppress youtube event during auto precheck type=${event.eventType}")
            return
        }


        if (handleAttachedYoutubeCommentMotionEvent(event, packageName)) {
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (packageName == YOUTUBE_PACKAGE) {
                    if (isYoutubeCommentPanelCloseClick(event.source)) {
                        clearMaskOverlay()
                        invalidateYoutubeCommentPanelSession("comment-close-click")
                    } else {
                        renderYoutubeCommentButtonLoadingGate(
                            event = event,
                            packageName = packageName,
                            serviceReceivedAtMs = callbackReceivedAtMs
                        )
                    }
                }
            }

            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START,            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                lastPointerInteractionAtMs = SystemClock.uptimeMillis()
                if (event.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
                    if (packageName == YOUTUBE_PACKAGE) {
                        youtubeTouchInteractionActive = true
                        startYoutubeHarmfulAnchorTracking()
                        if (!youtubeCommentInitialAnalysisCompleted) {
                            renderYoutubeScrollLoadingGate()
                        }
                    } else {
                        renderRiskGateForEvent(
                            event = event,
                            packageName = packageName,
                            eventTimeMs = event.eventTime,
                            serviceReceivedAtMs = callbackReceivedAtMs
                        )
                    }
                }
                if (event.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_END) {
                    if (packageName == YOUTUBE_PACKAGE) {
                        translateYoutubeMaskToCurrentHarmfulAuthor("touch-end")
                        youtubeTouchInteractionActive = false
                        finishYoutubeHarmfulAnchorTracking()
                    }
                    if (usesViewportStableBrowserOverlay(packageName)) {
                        scheduleBrowserRootFastScan(
                            packageName = packageName,
                            delayMs = SCROLL_OVERLAY_STABILIZATION_MS
                        )
                    }
                    scheduleDeferredFollowUpParse(waitForScrollStabilization = true)
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                val hasActiveMasks = maskOverlayController.hasActiveMasks()
                val overlaySelfContentChange = MaskOverlayEventPolicy.isLikelySelfContentChange(
                    eventType = event.eventType,
                    hasActiveMasks = hasActiveMasks,
                    overlayUpdatedRecently = maskOverlayController.wasUpdatedWithin(
                        OVERLAY_SELF_CONTENT_CHANGE_GRACE_MS
                    )
                )
                if (
                    packageName == YOUTUBE_PACKAGE &&
                    youtubeCommentInitialAnalysisCompleted &&
                    hasActiveMasks &&
                    !overlaySelfContentChange &&
                    event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED
                ) {
                    translateYoutubeMaskToCurrentHarmfulAuthor(
                        "event-${event.eventType}"
                    )
                }
                val contentChangedWithActiveMask =
                    event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                        hasActiveMasks &&
                        !overlaySelfContentChange
                val youtubeNativePanelGateMs = if (!overlaySelfContentChange) {
                    renderYoutubeNativeCommentPanelLoadingGate(
                        event = event,
                        packageName = packageName,
                        eventTimeMs = event.eventTime,
                        serviceReceivedAtMs = callbackReceivedAtMs
                    )
                } else {
                    -1L
                }
                if (
                    packageName == YOUTUBE_PACKAGE &&
                    youtubeSafeCommentMirrorController.isActive
                ) {
                    return
                }
                val shouldPrimeYoutubeLoading =
                    MaskOverlayEventPolicy.shouldPrimeYoutubeLoadingForPotentialScroll(
                        eventType = event.eventType,
                        isYoutubePackage = packageName == YOUTUBE_PACKAGE,
                        isLikelySelfContentChange = overlaySelfContentChange,
                        hasConfirmedCommentPanel = lastYoutubeScrollLoadingSpec != null &&
                            lastYoutubeCommentPanelConfirmedAtMs > 0L
                    )
                if (shouldPrimeYoutubeLoading) {
                    lastScrollEventAtMs = SystemClock.uptimeMillis()
                    val rendered = renderYoutubeScrollLoadingGate()
                    Log.d(TAG, "prime youtube loading before scroll event rendered=$rendered")
                }
                val elapsedSinceVisualAnalysisStartMs =
                    SystemClock.uptimeMillis() - lastVisualAnalysisStartedAtMs
                val deferVisualInvalidationForContentChange =
                    MaskOverlayEventPolicy.shouldDeferVisualInvalidationForContentChange(
                        eventType = event.eventType,
                        visualAnalysisInFlight = visualAnalysisInFlight,
                        elapsedSinceVisualAnalysisStartMs = elapsedSinceVisualAnalysisStartMs
                    )
                val visualSceneChanged = shouldInvalidateVisualScene(
                    event.eventType,
                    contentChangedWithActiveMask || overlaySelfContentChange,
                    visualAnalysisInFlight,
                    elapsedSinceVisualAnalysisStartMs
                )
                if (contentChangedWithActiveMask) {
                    lastOverlayContentChangeAtMs = SystemClock.uptimeMillis()
                }
                if (visualSceneChanged) {
                    markVisualSceneChanged(event.eventType)
                }

                val riskGateMaskMs = if (!overlaySelfContentChange && youtubeNativePanelGateMs < 0L) {
                    renderRiskGateForEvent(
                        event = event,
                        packageName = packageName,
                        eventTimeMs = event.eventTime,
                        serviceReceivedAtMs = callbackReceivedAtMs
                    )
                } else {
                    -1L
                }

                val fastProvisionalMaskMs = if (
                    !overlaySelfContentChange &&
                    youtubeNativePanelGateMs < 0L &&
                    riskGateMaskMs < 0L
                ) {
                    renderFastProvisionalMaskFromEventSource(
                        event = event,
                        packageName = packageName,
                        eventTimeMs = event.eventTime,
                        serviceReceivedAtMs = callbackReceivedAtMs
                    )
                } else {
                    -1L
                }
                val browserRootFastMaskMs =
                    if (
                        !overlaySelfContentChange &&
                        riskGateMaskMs < 0L &&
                        fastProvisionalMaskMs < 0L &&
                        event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED
                    ) {
                        renderFastProvisionalMaskFromActiveBrowserWindow(
                            packageName = packageName,
                            triggerEventType = event.eventType,
                            eventTimeMs = event.eventTime,
                            serviceReceivedAtMs = callbackReceivedAtMs
                        )
                    } else {
                        -1L
                    }
                val anyFastProvisionalMaskMs =
                    when {
                        youtubeNativePanelGateMs >= 0L -> youtubeNativePanelGateMs
                        fastProvisionalMaskMs >= 0L -> fastProvisionalMaskMs
                        else -> browserRootFastMaskMs
                    }

                if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                    lastScrollEventAtMs = SystemClock.uptimeMillis()
                    recordYoutubeUserScrollForAutoPrecheck(packageName)
                    if (usesViewportStableBrowserOverlay(packageName)) {
                        if (hasActiveMasks) {
                            Log.d(TAG, "hide browser mask overlay during scroll; waiting for stable recapture")
                        }
                        clearMaskOverlay()
                        scheduleBrowserRootFastScan(
                            packageName = packageName,
                            delayMs = SCROLL_OVERLAY_STABILIZATION_MS
                        )
                        scheduleDeferredFollowUpParse(waitForScrollStabilization = true)
                    } else {
                        val scrollTranslation = translateMaskOverlayForScroll(event)
                        var shouldPromoteCachedMasks = true
                        if (scrollTranslation.translated) {
                            markOverlayRevisionStale()
                            shouldPromoteCachedMasks = false
                        } else if (
                            MaskOverlayEventPolicy.shouldHideOnUnresolvedScrollDelta(
                                eventType = event.eventType,
                                hasActiveMasks = hasActiveMasks,
                                hasResolvedScrollDelta = scrollTranslation.hasResolvedScrollDelta,
                                overlayUpdatedRecently = maskOverlayController.wasUpdatedWithin(
                                    SCROLL_CONTENT_CHANGE_PRESERVE_MS
                                )
                            )
                        ) {
                            if (packageName == YOUTUBE_PACKAGE) {
                                if (youtubeCommentInitialAnalysisCompleted) {
                                    Log.d(TAG, "preserve attached youtube masks until unresolved-delta recapture")
                                    markOverlayRevisionStale()
                                    scheduleDeferredFollowUpParse(waitForScrollStabilization = true)
                                } else {
                                    Log.d(TAG, "drop stale youtube masks before unresolved-delta recapture")
                                    clearMaskOverlay()
                                }
                            } else {
                                Log.d(TAG, "hide mask overlay until scroll recapture: unresolved delta")
                                clearMaskOverlay()
                                scheduleDeferredFollowUpParse()
                            }
                        } else if (scrollTranslation.shouldHideUntilRecapture && hasActiveMasks) {
                            if (packageName == YOUTUBE_PACKAGE) {
                                if (youtubeCommentInitialAnalysisCompleted) {
                                    Log.d(
                                        TAG,
                                        "preserve attached youtube masks until recapture status=${scrollTranslation.status}"
                                    )
                                    markOverlayRevisionStale()
                                    scheduleDeferredFollowUpParse(waitForScrollStabilization = true)
                                } else {
                                    Log.d(
                                        TAG,
                                        "drop stale youtube masks before recapture status=${scrollTranslation.status}"
                                    )
                                    clearMaskOverlay()
                                }
                            } else {
                                Log.d(TAG, "hide mask overlay until scroll recapture status=${scrollTranslation.status}")
                                clearMaskOverlay()
                                scheduleDeferredFollowUpParse(waitForScrollStabilization = true)
                            }
                        } else {
                            markOverlayRevisionStale()
                            shouldPromoteCachedMasks = !hasActiveMasks
                        }
                        if (packageName == YOUTUBE_PACKAGE) {
                            if (youtubeCommentInitialAnalysisCompleted) {
                                Log.d(TAG, "defer youtube re-anchor until scroll events settle")
                                scheduleDeferredFollowUpParse(waitForScrollStabilization = true)
                            } else {
                                renderYoutubeScrollLoadingGate()
                                promoteCachedMasksForCurrentWindow(
                                    minIntervalMs = YOUTUBE_CACHE_PROMOTION_THROTTLE_MS
                                )
                            }
                        } else if (shouldPromoteCachedMasks) {
                            promoteCachedMasksForCurrentWindow()
                        }
                    }
                } else if (overlaySelfContentChange) {
                    Log.d(TAG, "ignore overlay self content change")
                } else if (
                    MaskOverlayEventPolicy.shouldPreserveOnScrollContentChange(
                        eventType = event.eventType,
                        hasActiveMasks = hasActiveMasks,
                        isScrollStabilizing = isInScrollContentChangePreserveWindow(),
                        isLikelySelfContentChange = overlaySelfContentChange
                    )
                ) {
                    Log.d(TAG, "preserve mask overlay during scroll content change")
                    markOverlayRevisionStale()
                    if (!deferVisualInvalidationForContentChange) {
                        markVisualSceneChanged(event.eventType)
                    }
                } else if (
                    event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
                    maskOverlayController.hasActiveMasks() &&
                    riskGateMaskMs < 0L &&
                    anyFastProvisionalMaskMs < 0L
                ) {
                    clearMaskOverlay()
                } else if (contentChangedWithActiveMask) {
                    Log.d(TAG, "preserve mask overlay until content recapture")
                    markOverlayRevisionStale()
                    if (!deferVisualInvalidationForContentChange) {
                        markVisualSceneChanged(event.eventType)
                    }
                    scheduleDeferredFollowUpParse(waitForScrollStabilization = true)
                } else if (
                    shouldClearOverlayImmediately(event.eventType) &&
                    riskGateMaskMs < 0L &&
                    anyFastProvisionalMaskMs < 0L
                ) {
                    clearMaskOverlay()
                } else if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                    markOverlayRevisionStale()
                }

                val delayMs = when (event.eventType) {
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> PARSE_DELAY_TEXT_MS
                    AccessibilityEvent.TYPE_VIEW_SCROLLED -> PARSE_DELAY_SCROLL_MS
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED -> PARSE_DELAY_WINDOW_MS
                    else -> PARSE_DELAY_CONTENT_MS
                }

                scheduleParse(
                    delayMs = delayMs,
                    eventType = event.eventType,
                    replaceExisting = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
                )
            }
        }
    }

    override fun onInterrupt() {
        cancelScheduledParse()
        resetYoutubeMirrorSession("service-interrupt")
        invalidateInstagramMirrorSession("service-interrupt")
        clearMaskOverlay()
        Log.d(TAG, "service interrupted")
    }

    override fun onDestroy() {
        cancelScheduledParse()
        resetYoutubeMirrorSession("service-destroy")
        invalidateInstagramMirrorSession("service-destroy")
        releaseMaskOverlay()
        applicationContext
            .getSharedPreferences(AnalysisSensitivityStore.PREFS_NAME, MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(sensitivityPreferenceListener)
        parseComputeExecutor.shutdownNow()
        visualExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun scheduleParse(
        delayMs: Long,
        eventType: Int? = null,
        replaceExisting: Boolean = false
    ) {
        val safeDelayMs = delayMs.coerceAtLeast(0L)
        val targetTimeMs = SystemClock.uptimeMillis() + safeDelayMs
        if (eventType != null) {
            scheduledParseEventType = eventType
        }

        if (analysisInFlight) {
            followUpParseRequested = true
            return
        }

        if (parseScheduled) {
            if (replaceExisting || targetTimeMs < scheduledParseAtMs) {
                handler.removeCallbacks(parseRunnable)
            } else {
                followUpParseRequested = true
                return
            }
        }

        parseScheduled = true
        scheduledParseAtMs = targetTimeMs
        scheduledParseRequestedAtMs = SystemClock.uptimeMillis()
        handler.postDelayed(parseRunnable, safeDelayMs)
    }

    private fun scheduleBrowserRootFastScan(
        packageName: String,
        delayMs: Long,
        attemptsRemaining: Int = FAST_BROWSER_ROOT_MAX_ATTEMPTS
    ) {
        if (!usesViewportStableBrowserOverlay(packageName)) return
        if (attemptsRemaining <= 0) return

        handler.postDelayed(
            {
                if (packageName != lastObservedPackage) return@postDelayed
                val receivedAtMs = SystemClock.uptimeMillis()
                val elapsedMs = renderFastProvisionalMaskFromActiveBrowserWindow(
                    packageName = packageName,
                    triggerEventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                    eventTimeMs = 0L,
                    serviceReceivedAtMs = receivedAtMs
                )
                if (elapsedMs < 0L && attemptsRemaining > 1) {
                    scheduleBrowserRootFastScan(
                        packageName = packageName,
                        delayMs = FAST_BROWSER_ROOT_RETRY_DELAY_MS,
                        attemptsRemaining = attemptsRemaining - 1
                    )
                }
            },
            delayMs.coerceAtLeast(0L)
        )
    }

    private fun cancelScheduledParse() {
        handler.removeCallbacks(parseRunnable)
        parseScheduled = false
        scheduledParseAtMs = 0L
        scheduledParseRequestedAtMs = 0L
        scheduledParseEventType = null
        followUpParseRequested = false
    }

    private fun parseAndUploadCurrentWindow(
        triggerEventType: Int?,
        parseStartedAtMs: Long,
        parseDelayMs: Long
    ) {
        val currentPackage = lastObservedPackage ?: run {
            Log.d(TAG, "lastObservedPackage is null")
            return
        }
        val isYoutubePackage = currentPackage == YOUTUBE_PACKAGE
        val youtubeMirrorBatchExpected = YoutubeMirrorParserPolicy.isExpectedBatch(
            isYoutube = isYoutubePackage,
            mirrorEnabled = YOUTUBE_SAFE_MIRROR_ENABLED,
            mirrorActive = youtubeSafeCommentMirrorController.isActive,
            collectionActive = youtubeMirrorCollectionMode != YoutubeMirrorCollectionMode.IDLE,
            awaitingBatch = youtubeMirrorAwaitingBatch
        )
        if (
            YoutubeMirrorParserPolicy.shouldSkipUnsolicitedAnalysis(
                isYoutube = isYoutubePackage,
                mirrorEnabled = YOUTUBE_SAFE_MIRROR_ENABLED,
                mirrorActive = youtubeSafeCommentMirrorController.isActive,
                expectedBatch = youtubeMirrorBatchExpected
            )
        ) {
            Log.d(TAG, "skip unsolicited youtube analysis while safe mirror is ready")
            return
        }
        if (
            currentPackage == INSTAGRAM_PACKAGE &&
            INSTAGRAM_SAFE_MIRROR_ENABLED &&
            (
                instagramSafeCommentMirrorSession.isActive ||
                    renderInstagramCommentSurfaceIfPresent("general-parse", force = true)
                )
        ) {
            Log.d(TAG, "skip general instagram analysis: safe mirror owns comment panel")
            return
        }
        syncSensitivityState()
        val currentSensitivity = AnalysisSensitivityStore.get(applicationContext)
        if (currentSensitivity <= 0) {
            clearMaskOverlay()
            Log.d(TAG, "skip analysis: sensitivity disabled")
            return
        }

        if (shouldDeferAnalysisDuringActiveScroll(triggerEventType)) {
            Log.d(TAG, "defer analysis: scroll stabilization active")
            scheduleDeferredFollowUpParse(waitForScrollStabilization = true)
            return
        }

        val experimentMode = currentExperimentMode()

        val nodeCollectionStartedAtMs = SystemClock.uptimeMillis()
        val nodes = when (currentPackage) {
            YOUTUBE_PACKAGE -> extractVisibleTextNodesFromYoutubeWindows()
            INSTAGRAM_PACKAGE -> extractVisibleTextNodesFromInstagramWindows()
            else -> extractVisibleTextNodesFromCurrentWindow(currentPackage)
        }
        val nodeCollectionMs = SystemClock.uptimeMillis() - nodeCollectionStartedAtMs

        if (nodes.isEmpty()) {
            Log.d(TAG, "no visible nodes found")
            if (youtubeMirrorBatchExpected) {
                onYoutubeMirrorAccessibilityResults(
                    results = emptyList(),
                    snapshotTimestampMs = System.currentTimeMillis()
                )
                return
            }
            clearMaskOverlay()
            return
        }

        val metrics = resources.displayMetrics
        val candidateComputation = buildParseCandidateComputation(
            packageName = currentPackage,
            nodes = nodes,
            experimentMode = experimentMode,
            sceneRevision = visualSceneRevision,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels
        )
        val visualRoiPlan = candidateComputation.visualRoiPlan
        val screenCandidates = candidateComputation.screenCandidates
        val candidatePostProcessingStartedAtMs = SystemClock.uptimeMillis()
        val lookaheadCandidateCount = screenCandidates.count { candidate ->
            candidate.backendSourceId.orEmpty().startsWith("android-accessibility-lookahead:")
        }
        val charLocationNodeCount = nodes.count { node -> node.charBoxes.isNotEmpty() }
        val charRangeCandidateCount = screenCandidates.count { candidate ->
            candidate.backendSourceId.orEmpty().startsWith("android-accessibility-char-range:")
        }
        val candidateRouteSamples = CandidateRoutingPolicy.summarize(screenCandidates)
        val comments = screenCandidates.map { it.toParsedComment() }
        val shouldCacheYoutubeMirrorSeed =
            YoutubeMirrorParserPolicy.shouldCachePreMirrorSeed(
                isYoutube = currentPackage == YOUTUBE_PACKAGE,
                mirrorEnabled = YOUTUBE_SAFE_MIRROR_ENABLED,
                expectedBatch = youtubeMirrorBatchExpected,
                nativeCommentPanelConfirmed =
                    currentPackage == YOUTUBE_PACKAGE &&
                        hasYoutubeCommentPanelStructureInAnyWindow()
            )
        if (shouldCacheYoutubeMirrorSeed) {
            rememberYoutubeMirrorSeedSnapshot(comments)
        } else if (
            currentPackage == YOUTUBE_PACKAGE &&
            !youtubeSafeCommentMirrorController.isActive
        ) {
            youtubeMirrorSeedSnapshot = null
            youtubeMirrorSeedCapturedAtUptimeMs = 0L
        }
        val candidatePostProcessingMs = SystemClock.uptimeMillis() - candidatePostProcessingStartedAtMs
        val candidateExtractionMs = SystemClock.uptimeMillis() - parseStartedAtMs

        if (
            currentPackage == YOUTUBE_PACKAGE &&
            YOUTUBE_SAFE_MIRROR_ENABLED &&
            youtubeSafeCommentMirrorController.isActive &&
            !youtubeMirrorBatchExpected
        ) {
            Log.d(TAG, "cache and discard pre-mirror youtube candidates after mirror takeover")
            return
        }

        Log.d(
            TAG,
                "package=$currentPackage parsed analysis target count=${comments.size} " +
                "screenCandidates=${screenCandidates.size} " +
                "lookaheadCandidates=$lookaheadCandidateCount " +
                "charLocationNodes=$charLocationNodeCount charRangeCandidates=$charRangeCandidateCount " +
                "visualRoiCandidates=${visualRoiPlan.candidateCount} visualRois=${visualRoiPlan.rois.size} " +
                "parseDelayMs=$parseDelayMs candidateExtractionMs=$candidateExtractionMs " +
                "nodeCollectionMs=$nodeCollectionMs " +
                "visualRoiPlanningMs=${candidateComputation.visualRoiPlanningMs} " +
                "screenCandidateExtractionMs=${candidateComputation.screenCandidateExtractionMs} " +
                "candidatePostProcessingMs=$candidatePostProcessingMs " +
                "candidateParallelWaitMs=${candidateComputation.parallelWaitMs} " +
                "routes=${candidateRouteSamples.joinToString(";")}"
        )

        if (shouldLogRawNodes() && currentPackage == YOUTUBE_PACKAGE && comments.size <= 3) {
            nodes.take(80).forEachIndexed { index, node ->
                Log.d(
                    TAG,
                    "YT_RAW[$index] text=${node.displayText} | cls=${node.className} | id=${node.viewIdResourceName} " +
                        "| bounds=${node.left},${node.top},${node.right},${node.bottom}"
                )
            }
        }

        if (shouldLogRawNodes() && currentPackage == INSTAGRAM_PACKAGE && comments.isEmpty()) {
            nodes.take(40).forEachIndexed { index, node ->
                Log.d(
                    TAG,
                    "IG_RAW[$index] text=${node.displayText} | cls=${node.className} | id=${node.viewIdResourceName}"
                )
            }
        }

        if (shouldLogRawNodes() && (currentPackage == TIKTOK_PACKAGE || currentPackage == TIKTOK_ALT_PACKAGE) && comments.isEmpty()) {
            nodes.take(40).forEachIndexed { index, node ->
                Log.d(
                    TAG,
                    "TT_RAW[$index] text=${node.displayText} | cls=${node.className} | id=${node.viewIdResourceName}"
                )
            }
        }

        val now = System.currentTimeMillis()

        if (experimentMode == PipelineExperimentMode.S1_COLLECT_ONLY) {
            saveExperimentStageDiagnostics(
                packageName = currentPackage,
                experimentMode = experimentMode,
                parseDelayMs = parseDelayMs,
                candidateExtractionMs = candidateExtractionMs,
                nodeCollectionMs = nodeCollectionMs,
                candidatePostProcessingMs = candidatePostProcessingMs,
                candidateComputation = candidateComputation,
                nodes = nodes,
                screenCandidates = screenCandidates,
                comments = comments,
                visualRoiPlan = visualRoiPlan,
                url = "experiment-s1-collect-only",
                riskGateMaskMs = recentRiskGateMaskMs(parseStartedAtMs),
                riskGateEventAgeMs = recentRiskGateEventAgeMs(parseStartedAtMs),
                riskGateReceiveToMaskMs = recentRiskGateReceiveToMaskMs(parseStartedAtMs),
                fastProvisionalMaskMs = recentFastProvisionalMaskMs(parseStartedAtMs)
            )
            clearMaskOverlay()
            return
        }

        if (experimentMode == PipelineExperimentMode.S4_COORD_ONLY) {
            saveExperimentStageDiagnostics(
                packageName = currentPackage,
                experimentMode = experimentMode,
                parseDelayMs = parseDelayMs,
                candidateExtractionMs = candidateExtractionMs,
                nodeCollectionMs = nodeCollectionMs,
                candidatePostProcessingMs = candidatePostProcessingMs,
                candidateComputation = candidateComputation,
                nodes = nodes,
                screenCandidates = screenCandidates,
                comments = comments,
                visualRoiPlan = visualRoiPlan,
                url = "experiment-s4-coordinate-only",
                accessibilityMaskLatencyMs = candidateComputation.parallelWaitMs,
                riskGateMaskMs = recentRiskGateMaskMs(parseStartedAtMs),
                riskGateEventAgeMs = recentRiskGateEventAgeMs(parseStartedAtMs),
                riskGateReceiveToMaskMs = recentRiskGateReceiveToMaskMs(parseStartedAtMs),
                fastProvisionalMaskMs = recentFastProvisionalMaskMs(parseStartedAtMs)
            )
            clearMaskOverlay()
            return
        }

        if (experimentMode == PipelineExperimentMode.S5_OVERLAY_ONLY) {
            val response = ProvisionalAccessibilityMaskBuilder.buildResponse(
                candidates = screenCandidates,
                timestamp = now
            )
            val overlayLatencyMs = if (response != null) {
                renderInitialAccessibilityMaskOverlay(
                    packageName = currentPackage,
                    screenCandidates = screenCandidates,
                    candidateRouteSamples = candidateRouteSamples,
                    visualRoiPlan = visualRoiPlan,
                    snapshotOverlayRevision = overlayRevision,
                    timestamp = now,
                    parseStartedAtMs = parseStartedAtMs,
                    parseDelayMs = parseDelayMs,
                    candidateExtractionMs = candidateExtractionMs,
                    nodeCollectionMs = nodeCollectionMs,
                    candidatePostProcessingMs = candidatePostProcessingMs,
                    experimentMode = experimentMode,
                    nodes = nodes,
                    candidateComputation = candidateComputation
                )
            } else {
                -1L
            }
            saveExperimentStageDiagnostics(
                packageName = currentPackage,
                experimentMode = experimentMode,
                parseDelayMs = parseDelayMs,
                candidateExtractionMs = candidateExtractionMs,
                nodeCollectionMs = nodeCollectionMs,
                candidatePostProcessingMs = candidatePostProcessingMs,
                candidateComputation = candidateComputation,
                nodes = nodes,
                screenCandidates = screenCandidates,
                comments = comments,
                visualRoiPlan = visualRoiPlan,
                url = "experiment-s5-overlay-only",
                response = response,
                accessibilityMaskLatencyMs = overlayLatencyMs,
                riskGateMaskMs = recentRiskGateMaskMs(parseStartedAtMs),
                riskGateEventAgeMs = recentRiskGateEventAgeMs(parseStartedAtMs),
                riskGateReceiveToMaskMs = recentRiskGateReceiveToMaskMs(parseStartedAtMs),
                fastProvisionalMaskMs = recentFastProvisionalMaskMs(parseStartedAtMs),
                includeOverlayDiagnostics = response != null
            )
            return
        }

        if (comments.isEmpty()) {
            if (youtubeMirrorBatchExpected) {
                onYoutubeMirrorAccessibilityResults(
                    results = emptyList(),
                    snapshotTimestampMs = now
                )
                return
            }
            val deferClearForVisualOnlyAnalysis =
                MaskOverlayEventPolicy.shouldDeferClearForVisualOnlyAnalysis(
                    hasActiveMasks = maskOverlayController.hasActiveMasks(),
                    hasRenderableVisualRois = visualRoiPlan.hasRenderableVisualRois()
                )
            if (currentPackage == YOUTUBE_PACKAGE && experimentMode.overlayStageEnabled) {
                renderYoutubeVisualCommentPanelOverlay(
                    visualRoiPlan = visualRoiPlan,
                    parseStartedAtMs = parseStartedAtMs
                )
                maybeStartYoutubeAutoPrecheck(
                    screenCandidates = screenCandidates,
                    visualRoiPlan = visualRoiPlan
                )
            }
            if (
                experimentMode.ocrStageEnabled &&
                (currentPackage != YOUTUBE_PACKAGE || YOUTUBE_VISUAL_OCR_ENABLED) &&
                startVisualTextAnalysis(
                    packageName = currentPackage,
                    visualRoiPlan = visualRoiPlan,
                    experimentMode = experimentMode,
                    parseStartedAtMs = parseStartedAtMs,
                    parseDelayMs = parseDelayMs,
                    candidateExtractionMs = candidateExtractionMs,
                    nodeCollectionMs = nodeCollectionMs,
                    candidatePostProcessingMs = candidatePostProcessingMs,
                    candidateComputation = candidateComputation,
                    nodes = nodes,
                    screenCandidates = screenCandidates,
                    clearExistingOverlay = !deferClearForVisualOnlyAnalysis,
                    clearExistingOverlayOnMiss = deferClearForVisualOnlyAnalysis
                )
            ) {
                return
            }
            saveVisualOnlyDiagnostics(currentPackage, visualRoiPlan)
            if (deferClearForVisualOnlyAnalysis) {
                markOverlayRevisionStale()
                return
            }
            clearMaskOverlay()
            return
        }

        val signature = buildString {
            append(currentPackage)
            append("||sensitivity=")
            append(currentSensitivity)
            append("||pipeline=")
            append(experimentMode.id)
            append("||")
            append(
                comments.joinToString("||") {
                    "${it.commentText}|${it.boundsInScreen.top}|${it.boundsInScreen.left}|${it.authorId.orEmpty()}"
                }
            )
            append("||visual=")
            append(visualRoiPlan.signature())
        }
        if (signature == lastSnapshotSignature) {
            val snapshotOverlayRevision = overlayRevision
            val duplicateBaseResponse = ProvisionalAccessibilityMaskBuilder.buildResponse(
                candidates = screenCandidates,
                timestamp = now
            )
            val reusableVisualResponse = if (visualRoiPlan.canReuseVisualSupplement()) {
                reusableVisualSupplement(
                    packageName = currentPackage,
                    visualRoiSignature = visualRoiPlan.signature()
                )
            } else {
                null
            }
            val duplicateResponse = mergeAnalysisResponses(duplicateBaseResponse, reusableVisualResponse)
                ?: reusableVisualResponse
                ?: duplicateBaseResponse
            if (duplicateResponse != null && experimentMode.overlayStageEnabled) {
                val duplicateAnalysis = AndroidAnalysisAttempt(
                    ok = true,
                    packageName = currentPackage,
                    url = if (reusableVisualResponse != null) {
                        "duplicate-snapshot-visual-cache"
                    } else {
                        "duplicate-snapshot-provisional"
                    },
                    sensitivity = currentSensitivity,
                    latencyMs = 0L,
                    riskGateMaskMs = recentRiskGateMaskMs(parseStartedAtMs),
                    riskGateEventAgeMs = recentRiskGateEventAgeMs(parseStartedAtMs),
                    riskGateReceiveToMaskMs = recentRiskGateReceiveToMaskMs(parseStartedAtMs),
                    fastProvisionalMaskMs = recentFastProvisionalMaskMs(parseStartedAtMs),
                    commentCount = duplicateResponse.results.size,
                    offensiveCount = duplicateResponse.results.size,
                    filteredCount = duplicateResponse.filteredCount,
                    response = duplicateResponse,
                    candidateRouteSamples = candidateRouteSamples
                )
                    .withPipelineDiagnostics(
                        experimentMode = experimentMode,
                        nodeCount = nodes.size,
                        screenCandidateCount = screenCandidates.size,
                        charLocationNodeCount = charLocationNodeCount,
                        charRangeCandidateCount = charRangeCandidateCount,
                        candidateParallelWaitMs = candidateComputation.parallelWaitMs,
                        nodeCollectionMs = nodeCollectionMs,
                        visualRoiPlanningMs = candidateComputation.visualRoiPlanningMs,
                        screenCandidateExtractionMs = candidateComputation.screenCandidateExtractionMs,
                        candidatePostProcessingMs = candidatePostProcessingMs
                    )
                    .withOverlayDiagnostics(currentPackage, visualRoiPlan)
                Log.d(
                    TAG,
                    "refresh duplicate snapshot masks results=${duplicateResponse.results.size} " +
                        "visualCached=${reusableVisualResponse != null}"
                )
                updateMaskOverlay(
                    currentPackage = currentPackage,
                    analysis = duplicateAnalysis,
                    snapshotOverlayRevision = snapshotOverlayRevision,
                    visualRoiPlan = visualRoiPlan,
                    isProvisionalAccessibilityMask = reusableVisualResponse == null,
                    allowDuringScrollStabilization = !usesViewportStableBrowserOverlay(currentPackage),
                    preserveExistingPreciseVisualMasks = true
                )
            } else {
                Log.d(TAG, "skip duplicate snapshot without renderable masks")
            }
            if (
                experimentMode.ocrStageEnabled &&
                    (currentPackage != YOUTUBE_PACKAGE || YOUTUBE_VISUAL_OCR_ENABLED) &&
                    MaskOverlayEventPolicy.shouldRunVisualRefreshForDuplicateSnapshot(
                    hasRenderableVisualRois = visualRoiPlan.hasRenderableVisualRois(),
                    visualAnalysisInFlight = visualAnalysisInFlight,
                    hasReusableVisualSupplement = reusableVisualResponse != null
                )
            ) {
                startVisualTextAnalysis(
                    packageName = currentPackage,
                    visualRoiPlan = visualRoiPlan,
                    experimentMode = experimentMode,
                    parseStartedAtMs = parseStartedAtMs,
                    parseDelayMs = parseDelayMs,
                    candidateExtractionMs = candidateExtractionMs,
                    nodeCollectionMs = nodeCollectionMs,
                    candidatePostProcessingMs = candidatePostProcessingMs,
                    candidateComputation = candidateComputation,
                    nodes = nodes,
                    screenCandidates = screenCandidates,
                    clearExistingOverlay = false,
                    baseResponse = duplicateBaseResponse
                )
            }
            return
        }

        if (analysisInFlight) {
            if (currentPackage == YOUTUBE_PACKAGE && experimentMode.overlayStageEnabled) {
                renderYoutubeSkeletonMaskOverlay(
                    screenCandidates = screenCandidates,
                    visualRoiPlan = visualRoiPlan,
                    timestamp = now,
                    parseStartedAtMs = parseStartedAtMs
                )
            }
            pendingParseAfterAnalysis = true
            Log.d(TAG, "defer snapshot: analysis already in flight")
            return
        }

        if (!experimentMode.backendStageEnabled) {
            val visualStarted = experimentMode.ocrStageEnabled &&
                (currentPackage != YOUTUBE_PACKAGE || YOUTUBE_VISUAL_OCR_ENABLED) &&
                startVisualTextAnalysis(
                    packageName = currentPackage,
                    visualRoiPlan = visualRoiPlan,
                    experimentMode = experimentMode,
                    parseStartedAtMs = parseStartedAtMs,
                    parseDelayMs = parseDelayMs,
                    candidateExtractionMs = candidateExtractionMs,
                    nodeCollectionMs = nodeCollectionMs,
                    candidatePostProcessingMs = candidatePostProcessingMs,
                    candidateComputation = candidateComputation,
                    nodes = nodes,
                    screenCandidates = screenCandidates,
                    clearExistingOverlay = true,
                    clearExistingOverlayOnMiss = false
                )
            if (!visualStarted) {
                saveExperimentStageDiagnostics(
                    packageName = currentPackage,
                    experimentMode = experimentMode,
                    parseDelayMs = parseDelayMs,
                    candidateExtractionMs = candidateExtractionMs,
                    nodeCollectionMs = nodeCollectionMs,
                    candidatePostProcessingMs = candidatePostProcessingMs,
                    candidateComputation = candidateComputation,
                    nodes = nodes,
                    screenCandidates = screenCandidates,
                    comments = comments,
                    visualRoiPlan = visualRoiPlan,
                    url = "experiment-${experimentMode.id}"
                )
                clearMaskOverlay()
            }
            return
        }

        val snapshot = ParseSnapshot(
            timestamp = now,
            comments = comments
        )
        val youtubeMirrorRunIdAtSnapshot = youtubeMirrorSessionRunId
        val youtubeMirrorAttemptIdAtSnapshot = youtubeMirrorCaptureAttemptId
        if (youtubeMirrorBatchExpected) {
            youtubeMirrorParsedCommentCount = max(
                youtubeMirrorParsedCommentCount,
                comments.count { comment ->
                    YoutubeSafeCommentAssembler.isYoutubeAccessibilitySource(comment.authorId)
                }
            )
            youtubeMirrorExpectedSnapshotTimestampMs = snapshot.timestamp
        }
        val shouldUpload = now - lastUploadAt >= MIN_UPLOAD_INTERVAL_MS
        val savedFile = if (shouldUpload) {
            JsonFileStore.saveSnapshot(applicationContext, snapshot, currentPackage)
        } else {
            null
        }

        lastSnapshotSignature = signature
        if (shouldUpload) {
            lastUploadAt = now
        }
        val snapshotOverlayRevision = overlayRevision
        val snapshotVisualSceneRevision = visualSceneRevision
        val accessibilityMaskLatencyMs = if (
            experimentMode.overlayStageEnabled &&
            !youtubeMirrorBatchExpected
        ) {
            renderInitialAccessibilityMaskOverlay(
                packageName = currentPackage,
                screenCandidates = screenCandidates,
                candidateRouteSamples = candidateRouteSamples,
                visualRoiPlan = visualRoiPlan,
                snapshotOverlayRevision = snapshotOverlayRevision,
                timestamp = now,
                parseStartedAtMs = parseStartedAtMs,
                parseDelayMs = parseDelayMs,
                candidateExtractionMs = candidateExtractionMs,
                nodeCollectionMs = nodeCollectionMs,
                candidatePostProcessingMs = candidatePostProcessingMs,
                experimentMode = experimentMode,
                nodes = nodes,
                candidateComputation = candidateComputation
            )
        } else {
            -1L
        }
        val earlyVisualTextAnalysisStarted =
            experimentMode.ocrStageEnabled &&
                currentPackage == YOUTUBE_PACKAGE &&
                YOUTUBE_VISUAL_OCR_ENABLED &&
                !youtubeMirrorBatchExpected &&
                startVisualTextAnalysis(
                    packageName = currentPackage,
                    visualRoiPlan = visualRoiPlan,
                    experimentMode = experimentMode,
                    parseStartedAtMs = parseStartedAtMs,
                    parseDelayMs = parseDelayMs,
                    candidateExtractionMs = candidateExtractionMs,
                    nodeCollectionMs = nodeCollectionMs,
                    candidatePostProcessingMs = candidatePostProcessingMs,
                    candidateComputation = candidateComputation,
                    nodes = nodes,
                    screenCandidates = screenCandidates,
                    clearExistingOverlay = false
                )
        analysisInFlight = true

        Thread {
            var analysisForOverlay: AndroidAnalysisAttempt? = null
            var releasedAnalysisGate = false

            fun releaseAnalysisGate() {
                if (releasedAnalysisGate) return
                releasedAnalysisGate = true
                analysisInFlight = false
                if (pendingParseAfterAnalysis || followUpParseRequested) {
                    handler.post {
                        pendingParseAfterAnalysis = false
                        followUpParseRequested = false
                        scheduleDeferredFollowUpParse()
                    }
                }
            }

            try {
                val rawAnalysis = AndroidAnalysisClient
                    .analyzeSnapshot(applicationContext, snapshot)
                    .copy(
                        packageName = currentPackage,
                        candidateRouteSamples = candidateRouteSamples
                    )
                val currentSensitivity = AnalysisSensitivityStore.get(applicationContext)
                if (rawAnalysis.sensitivity != null && rawAnalysis.sensitivity != currentSensitivity) {
                    Log.d(
                        TAG,
                        "drop analysis: stale sensitivity analysis=${rawAnalysis.sensitivity} current=$currentSensitivity"
                    )
                    return@Thread
                }

                if (youtubeMirrorBatchExpected) {
                    val mirrorRequestStillCurrent =
                        youtubeMirrorSessionRunId == youtubeMirrorRunIdAtSnapshot &&
                            youtubeMirrorCaptureAttemptId == youtubeMirrorAttemptIdAtSnapshot &&
                            youtubeMirrorAwaitingBatch &&
                            youtubeSafeCommentMirrorController.isActive
                    if (mirrorRequestStillCurrent && rawAnalysis.ok) {
                        onYoutubeMirrorAccessibilityResults(
                            results = rawAnalysis.response?.results.orEmpty(),
                            snapshotTimestampMs = snapshot.timestamp
                        )
                    } else if (mirrorRequestStillCurrent) {
                        handleYoutubeMirrorAnalysisFailure(
                            runId = youtubeMirrorRunIdAtSnapshot,
                            attemptId = youtubeMirrorAttemptIdAtSnapshot,
                            snapshotTimestampMs = snapshot.timestamp,
                            error = rawAnalysis.error ?: "ANALYSIS_FAILED"
                        )
                    } else if (!mirrorRequestStillCurrent) {
                        Log.d(
                            TAG,
                            "drop stale youtube mirror parser response run=$youtubeMirrorRunIdAtSnapshot " +
                                "attempt=$youtubeMirrorAttemptIdAtSnapshot"
                        )
                    }
                }
                val mergedResponse = mergeAnalysisResponses(
                    baseResponse = rawAnalysis.response,
                    visualResponse = if (visualRoiPlan.canReuseVisualSupplement()) {
                        reusableVisualSupplement(
                            packageName = currentPackage,
                            visualRoiSignature = visualRoiPlan.signature()
                        )
                    } else {
                        null
                    }
                )
                val analysisBase = rawAnalysis
                    .copy(
                        response = mergedResponse,
                        parseDelayMs = parseDelayMs,
                        candidateExtractionMs = candidateExtractionMs,
                        nodeCollectionMs = nodeCollectionMs,
                        visualRoiPlanningMs = candidateComputation.visualRoiPlanningMs,
                        screenCandidateExtractionMs = candidateComputation.screenCandidateExtractionMs,
                        candidatePostProcessingMs = candidatePostProcessingMs,
                        accessibilityMaskLatencyMs = accessibilityMaskLatencyMs,
                        riskGateMaskMs = recentRiskGateMaskMs(parseStartedAtMs),
                        riskGateEventAgeMs = recentRiskGateEventAgeMs(parseStartedAtMs),
                        riskGateReceiveToMaskMs = recentRiskGateReceiveToMaskMs(parseStartedAtMs),
                        fastProvisionalMaskMs = recentFastProvisionalMaskMs(parseStartedAtMs),
                        fastProvisionalEventAgeMs = recentFastProvisionalEventAgeMs(parseStartedAtMs),
                        fastProvisionalBuildMs = recentFastProvisionalBuildMs(parseStartedAtMs),
                        fastProvisionalOverlayMs = recentFastProvisionalOverlayMs(parseStartedAtMs),
                        fastProvisionalReceiveToMaskMs = recentFastProvisionalReceiveToMaskMs(parseStartedAtMs),
                        backendMaskLatencyMs = SystemClock.uptimeMillis() - parseStartedAtMs,
                        commentCount = mergedResponse?.results?.size ?: rawAnalysis.commentCount,
                        offensiveCount = countActionableResults(mergedResponse),
                        filteredCount = mergedResponse?.filteredCount ?: rawAnalysis.filteredCount
                    )
                    .withPipelineDiagnostics(
                        experimentMode = experimentMode,
                        nodeCount = nodes.size,
                        screenCandidateCount = screenCandidates.size,
                        charLocationNodeCount = charLocationNodeCount,
                        charRangeCandidateCount = charRangeCandidateCount,
                        candidateParallelWaitMs = candidateComputation.parallelWaitMs,
                        nodeCollectionMs = nodeCollectionMs,
                        visualRoiPlanningMs = candidateComputation.visualRoiPlanningMs,
                        screenCandidateExtractionMs = candidateComputation.screenCandidateExtractionMs,
                        candidatePostProcessingMs = candidatePostProcessingMs
                    )
                val analysis = if (experimentMode.overlayStageEnabled) {
                    analysisBase.withOverlayDiagnostics(currentPackage, visualRoiPlan)
                } else {
                    analysisBase.withVisualCaptureDiagnostics(visualRoiPlan)
                }
                analysisForOverlay = analysis
                if (experimentMode.overlayStageEnabled && !youtubeMirrorBatchExpected) {
                    handler.post {
                        if (
                            currentPackage == YOUTUBE_PACKAGE &&
                            YOUTUBE_SAFE_MIRROR_ENABLED &&
                            youtubeSafeCommentMirrorController.isActive
                        ) {
                            Log.d(TAG, "skip stale youtube overlay response after safe mirror takeover")
                            return@post
                        }
                        val awaitsYoutubeCommentVisualAnalysis =
                            currentPackage == YOUTUBE_PACKAGE &&
                                visualRoiPlan.hasYoutubeCommentPanelRoi()
                        updateMaskOverlay(
                            currentPackage = currentPackage,
                            analysis = analysis,
                            snapshotOverlayRevision = snapshotOverlayRevision,
                            visualRoiPlan = visualRoiPlan,
                            isProvisionalAccessibilityMask = awaitsYoutubeCommentVisualAnalysis,
                            preserveExistingPreciseVisualMasks = visualRoiPlan.hasRenderableVisualRois()
                        )
                    }
                }
                AnalysisDiagnosticsStore.saveAttempt(applicationContext, analysis)

                analysis.response?.let { response ->
                    JsonFileStore.saveAnalysisResponse(applicationContext, response, currentPackage)
                }

                val shouldStartVisualSupplement =
                    experimentMode.ocrStageEnabled &&
                        !youtubeMirrorBatchExpected &&
                        (currentPackage != YOUTUBE_PACKAGE || YOUTUBE_VISUAL_OCR_ENABLED) &&
                        !earlyVisualTextAnalysisStarted &&
                        shouldRunVisualTextSupplement(currentPackage, analysis, visualRoiPlan)

                // Masking must not be blocked by the optional upload channel.
                releaseAnalysisGate()
                if (shouldStartVisualSupplement) {
                    handler.post {
                        if (snapshotVisualSceneRevision != visualSceneRevision) {
                            Log.d(
                                TAG,
                                "skip visual OCR start: stale base scene " +
                                    "snapshot=$snapshotVisualSceneRevision current=$visualSceneRevision"
                            )
                            return@post
                        }
                        startVisualTextAnalysis(
                            packageName = currentPackage,
                            visualRoiPlan = visualRoiPlan,
                            experimentMode = experimentMode,
                            parseStartedAtMs = parseStartedAtMs,
                            parseDelayMs = parseDelayMs,
                            candidateExtractionMs = candidateExtractionMs,
                            nodeCollectionMs = nodeCollectionMs,
                            candidatePostProcessingMs = candidatePostProcessingMs,
                            candidateComputation = candidateComputation,
                            nodes = nodes,
                            screenCandidates = screenCandidates,
                            clearExistingOverlay = false,
                            baseResponse = analysis.response
                        )
                    }
                }

                val uploadOk = savedFile?.let {
                    ServerUploader.uploadJsonFile(applicationContext, it, currentPackage)
                } ?: false

                Log.d(
                    TAG,
                    "snapshot processed package=$currentPackage uploadOk=$uploadOk " +
                        "uploadSkipped=${savedFile == null} analysisOk=${analysis.ok} " +
                        "comments=${analysis.commentCount} offensive=${analysis.offensiveCount} " +
                        "filtered=${analysis.filteredCount} analysisLatencyMs=${analysis.latencyMs} " +
                        "analysisError=${analysis.error.orEmpty()}"
                )
            } finally {
                val youtubeMirrorSupersededAnalysis =
                    currentPackage == YOUTUBE_PACKAGE &&
                        YOUTUBE_SAFE_MIRROR_ENABLED &&
                        (
                            youtubeSafeCommentMirrorController.isActive ||
                                youtubeMirrorSessionRunId != youtubeMirrorRunIdAtSnapshot
                            )
                if (analysisForOverlay == null && !youtubeMirrorSupersededAnalysis) {
                    handler.post {
                        updateMaskOverlay(
                            currentPackage = currentPackage,
                            analysis = null,
                            snapshotOverlayRevision = snapshotOverlayRevision,
                            visualRoiPlan = visualRoiPlan
                        )
                    }
                }
                releaseAnalysisGate()
            }
        }.start()
    }

    private fun renderInitialAccessibilityMaskOverlay(
        packageName: String,
        screenCandidates: List<ScreenTextCandidate>,
        candidateRouteSamples: List<String>,
        visualRoiPlan: VisualTextRoiPlan,
        snapshotOverlayRevision: Long,
        timestamp: Long,
        parseStartedAtMs: Long,
        parseDelayMs: Long,
        candidateExtractionMs: Long,
        nodeCollectionMs: Long = -1L,
        candidatePostProcessingMs: Long = -1L,
        experimentMode: PipelineExperimentMode = currentExperimentMode(),
        nodes: List<ParsedTextNode> = emptyList(),
        candidateComputation: ParseCandidateComputation? = null
    ): Long {
        if (packageName == YOUTUBE_PACKAGE) {
            return renderYoutubeSkeletonMaskOverlay(
                screenCandidates = screenCandidates,
                visualRoiPlan = visualRoiPlan,
                timestamp = timestamp,
                parseStartedAtMs = parseStartedAtMs
            )
        }

        return renderProvisionalAccessibilityMaskOverlay(
            packageName = packageName,
            screenCandidates = screenCandidates,
            candidateRouteSamples = candidateRouteSamples,
            visualRoiPlan = visualRoiPlan,
            snapshotOverlayRevision = snapshotOverlayRevision,
            timestamp = timestamp,
            parseStartedAtMs = parseStartedAtMs,
            parseDelayMs = parseDelayMs,
            candidateExtractionMs = candidateExtractionMs,
            nodeCollectionMs = nodeCollectionMs,
            candidatePostProcessingMs = candidatePostProcessingMs,
            experimentMode = experimentMode,
            nodes = nodes,
            candidateComputation = candidateComputation
        )
    }

    private fun renderYoutubeSkeletonMaskOverlay(
        screenCandidates: List<ScreenTextCandidate>,
        visualRoiPlan: VisualTextRoiPlan,
        timestamp: Long,
        parseStartedAtMs: Long
    ): Long {
        if (screenCandidates.isEmpty()) return -1L
        if (!hasConfirmedYoutubeCommentPanel(visualRoiPlan)) {
            if (
                youtubeCommentInitialAnalysisCompleted &&
                hasFreshYoutubeCommentPanelConfirmation()
            ) {
                Log.d(TAG, "preserve attached youtube masks during transient comment ROI miss")
                return SystemClock.uptimeMillis() - parseStartedAtMs
            }
            if (hasFreshNativeYoutubeCommentPanel()) {
                Log.d(TAG, "preserve native youtube comment loading while visual ROI catches up")
                return SystemClock.uptimeMillis() - parseStartedAtMs
            }
            lastYoutubeScrollLoadingSpec = null
            lastYoutubeCommentPanelConfirmedAtMs = 0L
            Log.d(
                TAG,
                "skip youtube skeleton overlay: comment panel ROI not confirmed " +
                    "visualRois=${visualRoiPlan.rois.size}"
            )
            return -1L
        }
        val metrics = resources.displayMetrics
        val commentPanelBounds = youtubeCommentPanelBounds(visualRoiPlan)
        YoutubeSkeletonMaskBuilder.buildCommentPaneSpecFromBounds(
            bounds = commentPanelBounds,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            style = MaskOverlayStyle.LOADING,
            label = "comments-loading",
            debugSource = "youtube-comment-pane-loading-scroll"
        )?.let { spec ->
            val stabilizedSpec = YoutubeSkeletonMaskBuilder.stabilizeLoadingPaneSpec(
                previousSpec = lastYoutubeScrollLoadingSpec,
                currentSpec = spec,
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels
            )
            lastYoutubeScrollLoadingSpec = stabilizedSpec
            rememberYoutubeKnownCommentPanel(stabilizedSpec)
            lastYoutubeCommentPanelConfirmedAtMs = SystemClock.uptimeMillis()
        }
        val comments = screenCandidates.map { candidate -> candidate.toParsedComment() }
        val cachedResults = comments.map { comment ->
            AndroidAnalysisClient.cachedResultForComment(applicationContext, comment)
        }
        val plan = YoutubeSkeletonMaskBuilder.build(
            candidates = screenCandidates,
            cachedResults = cachedResults,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            timestamp = timestamp,
            commentPanelBounds = commentPanelBounds
        )
        if (!youtubeCommentInitialAnalysisCompleted || youtubeKnownHarmfulComments.isEmpty()) {
            lastYoutubeBlockedSpecs = plan.cachedHarmfulSpecs
        }
        val fallbackLoadingSpec = if (
            !youtubeCommentInitialAnalysisCompleted &&
                plan.unknownCount > 0 &&
            plan.specs.isEmpty() &&
            commentPanelBounds.isNotEmpty()
        ) {
            YoutubeSkeletonMaskBuilder.buildCommentPaneSpecFromBounds(
                bounds = commentPanelBounds,
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels,
                style = MaskOverlayStyle.LOADING,
                label = "comments-loading",
                debugSource = "youtube-comment-pane-loading-panel"
            )
        } else {
            null
        }
        val scrollHoldLoadingSpec = lastYoutubeScrollLoadingSpec
            ?.takeIf {
                !youtubeCommentInitialAnalysisCompleted &&
                    isYoutubeScrollLoadingHoldActive()
            }
        val visualAnalysisLoadingSpec = lastYoutubeScrollLoadingSpec
            ?.takeIf {
                !youtubeCommentInitialAnalysisCompleted &&
                    commentPanelBounds.isNotEmpty()
            }
        val specs = if (youtubeCommentInitialAnalysisCompleted) {
            YoutubeSkeletonMaskBuilder.buildAttachedViewportSpecs(plan)
        } else if (visualAnalysisLoadingSpec != null) {
            listOf(visualAnalysisLoadingSpec)
        } else if (scrollHoldLoadingSpec != null) {
            listOf(scrollHoldLoadingSpec)
        } else {
            (plan.specs + listOfNotNull(fallbackLoadingSpec)).distinctBy { spec ->
                "${spec.left}|${spec.top}|${spec.width}|${spec.height}|${spec.style}"
            }
        }

        val commentGeometrySamples = screenCandidates
            .filter { candidate -> candidate.route.surface == CandidateSurface.YOUTUBE_COMMENT }
            .take(4)
            .joinToString(";") { candidate ->
                val bounds = candidate.screenRect
                val hash = Integer.toHexString(
                    candidate.rawText.replace(Regex("\\s+"), " ").trim().lowercase().hashCode()
                )
                "$hash@${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}:${candidate.backendSourceId}"
            }
        Log.d(
            TAG,
            "youtube skeleton cache safe=${plan.safeCacheHitCount} harmful=${plan.harmfulCacheHitCount} " +
                "unknown=${plan.unknownCount} skipped=${plan.skippedCount} specs=${specs.size} " +
                "attached=$youtubeCommentInitialAnalysisCompleted " +
                "panelFallback=${fallbackLoadingSpec != null} " +
                "scrollHold=${scrollHoldLoadingSpec != null} " +
                "visualHold=${visualAnalysisLoadingSpec != null} " +
                "samples=${plan.cacheSamples.joinToString(";")} geometry=$commentGeometrySamples"
        )
        if (YOUTUBE_SAFE_MIRROR_ENABLED) {
            val mirrorSpec = specs.firstOrNull { spec ->
                spec.style == MaskOverlayStyle.LOADING
            } ?: lastYoutubeScrollLoadingSpec ?: youtubeMirrorPanelSpec
            if (mirrorSpec != null && (youtubeSafeCommentMirrorController.isActive || specs.isNotEmpty())) {
                renderYoutubeCommentPanelSurface(
                    spec = mirrorSpec,
                    reason = "youtube-skeleton-safe-mirror"
                )
                return SystemClock.uptimeMillis() - parseStartedAtMs
            }
        }
        if (youtubeCommentInitialAnalysisCompleted && youtubeKnownHarmfulComments.isNotEmpty()) {
            rememberYoutubeHarmfulCommentAnchor(
                results = cachedResults.filterNotNull(),
                fallbackSpecs = plan.cachedHarmfulSpecs
            )
            return SystemClock.uptimeMillis() - parseStartedAtMs
        }

        val loadingSpec = specs.firstOrNull { spec ->
            spec.style == MaskOverlayStyle.LOADING
        }
        val previousLoadingSpec = lastYoutubeCommentPaneSpec
        val loadingOnly = specs.isNotEmpty() && specs.all { spec ->
            spec.style == MaskOverlayStyle.LOADING
        }
        val nowMs = SystemClock.uptimeMillis()
        if (loadingOnly && nowMs < youtubeLoadingSuppressedUntilMs) {
            Log.d(
                TAG,
                "skip youtube loading overlay: suppressed remainingMs=${youtubeLoadingSuppressedUntilMs - nowMs}"
            )
            if (restoreYoutubeCachedHarmfulMasks("youtube-loading-suppressed-blocked-cache")) {
                return SystemClock.uptimeMillis() - parseStartedAtMs
            }
            return -1L
        }
        if (
            youtubeCommentInitialAnalysisCompleted &&
            loadingOnly &&
            loadingSpec != null &&
            youtubeLoadingOverlayStartedAtMs > 0L &&
            previousLoadingSpec?.debugSource == loadingSpec.debugSource &&
            nowMs - youtubeLoadingOverlayStartedAtMs >= YOUTUBE_LOADING_MAX_VISIBLE_MS
        ) {
            Log.d(TAG, "expire overlong youtube loading before redraw source=${loadingSpec.debugSource}")
            expireYoutubeLoadingOverlay(
                reason = "accessibility-max-visible",
                nowMs = nowMs,
                suppressFurtherLoading = true
            )
            return -1L
        }
        if (specs.isEmpty()) {
            if (
                youtubeTouchInteractionActive &&
                previousLoadingSpec?.style == MaskOverlayStyle.LOADING &&
                maskOverlayController.hasActiveMasks()
            ) {
                Log.d(TAG, "preserve youtube scroll loading while touch interaction is active")
                return SystemClock.uptimeMillis() - parseStartedAtMs
            }
            youtubeLoadingOverlayStartedAtMs = 0L
            if (plan.safeCacheHitCount > 0 && maskOverlayController.hasActiveMasks()) {
                Log.d(TAG, "youtube skeleton all cached safe; fade existing masks")
                maskOverlayController.fadeOutAndClear(
                    durationMs = YOUTUBE_SAFE_FADE_OUT_MS,
                    reason = "youtube-cache-safe"
                )
                provisionalAccessibilityMaskActive = false
            }
            return -1L
        }
        lastYoutubeCommentPaneSpec = loadingSpec

        val beforeOverlayMs = SystemClock.uptimeMillis()
        if (!maskOverlayController.renderDirect(specs, reason = "youtube-skeleton-cache")) {
            return -1L
        }
        if (youtubeCommentInitialAnalysisCompleted) {
            rememberYoutubeHarmfulCommentAnchor(cachedResults.filterNotNull())
        }
        if (loadingSpec != null) {
            if (
                youtubeLoadingOverlayStartedAtMs == 0L ||
                previousLoadingSpec?.debugSource != loadingSpec.debugSource ||
                !provisionalAccessibilityMaskActive
            ) {
                youtubeLoadingOverlayStartedAtMs = beforeOverlayMs
            }
        } else {
            youtubeLoadingOverlayStartedAtMs = 0L
        }
        provisionalAccessibilityMaskActive = loadingSpec != null && maskOverlayController.hasActiveMasks()
        riskGateActive = false
        if (loadingSpec != null) {
            scheduleYoutubeLoadingExpiry("accessibility")
        }
        if (plan.unknownCount > 0 && loadingSpec != null) {
            maybeStartYoutubeAutoPrecheck(screenCandidates)
        }
        val elapsedMs = SystemClock.uptimeMillis() - parseStartedAtMs
        Log.d(
            TAG,
            "youtube skeleton loading shown elapsedMs=$elapsedMs drawLatencyMs=${SystemClock.uptimeMillis() - beforeOverlayMs} " +
                "activeMasks=${maskOverlayController.hasActiveMasks()}"
        )
        return elapsedMs
    }

    private fun renderYoutubeCommentPanelSurface(
        spec: MaskOverlaySpec,
        reason: String
    ): Boolean {
        if (!YOUTUBE_SAFE_MIRROR_ENABLED) {
            return maskOverlayController.renderDirect(listOf(spec), reason)
        }

        val nowMs = SystemClock.uptimeMillis()
        val reopenGuardRemainingMs = youtubeMirrorReopenSuppressedUntilMs - nowMs
        if (
            reopenGuardRemainingMs > 0L &&
            !hasYoutubeCommentPanelStructureInAnyWindow()
        ) {
            Log.d(
                TAG,
                "skip stale youtube mirror reopen after panel dismissal " +
                    "reason=$reason remainingMs=$reopenGuardRemainingMs"
            )
            return false
        }

        val metrics = resources.displayMetrics
        val stabilizedSpec = YoutubeSkeletonMaskBuilder.stabilizeLoadingPaneSpec(
            previousSpec = youtubeMirrorPanelSpec,
            currentSpec = spec,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels
        )
        val activeRoot = rootInActiveWindow
        val surfaceSpec = if (
            activeRoot != null &&
            isYoutubeAccessibilityRoot(activeRoot)
        ) {
            alignYoutubePaneBelowHeader(
                root = activeRoot,
                spec = stabilizedSpec,
                screenHeight = metrics.heightPixels
            )
        } else {
            stabilizedSpec
        }
        youtubeMirrorPanelSpec = surfaceSpec
        if (!youtubeSafeCommentMirrorController.isActive) {
            beginYoutubeMirrorSession(surfaceSpec)
        } else if (youtubeSafeCommentMirrorController.isReady) {
            youtubeSafeCommentMirrorController.showComments(
                spec = surfaceSpec,
                comments = youtubeSafeCommentBuffer.comments(),
                prefetching = youtubeMirrorCollectionMode == YoutubeMirrorCollectionMode.PREFETCH,
                emptyMessage = youtubeMirrorEmptyStateMessage()
            )
        } else {
            youtubeSafeCommentMirrorController.updateLoading(
                spec = surfaceSpec,
                collectedCount = youtubeSafeCommentBuffer.comments().size
            )
        }

        if (!youtubeSafeCommentMirrorController.isActive) {
            resetYoutubeMirrorSession("overlay-create-failed")
            return maskOverlayController.renderDirect(listOf(surfaceSpec), reason)
        }

        maskOverlayController.clear()
        Log.d(
            TAG,
            "render youtube safe mirror surface reason=$reason " +
                "ready=${youtubeSafeCommentMirrorController.isReady} " +
                "safe=${youtubeSafeCommentBuffer.comments().size}"
        )
        return true
    }

    private fun rememberYoutubeMirrorSeedSnapshot(
        comments: List<ParsedComment>,
        source: String = "general-parser"
    ) {
        val commentOnly = comments.filter { comment ->
            YoutubeSafeCommentAssembler.isYoutubeAccessibilitySource(comment.authorId)
        }
        if (commentOnly.isEmpty()) return

        val existing = youtubeMirrorSeedSnapshot?.comments.orEmpty()
        val merged = (existing + commentOnly).distinctBy { comment ->
            val author = comment.authorId.orEmpty()
                .removePrefix("android-accessibility-lookahead:")
                .substringBefore(":line:")
            val text = comment.commentText
                .replace(Regex("\\s+"), " ")
                .trim()
                .lowercase()
            "$author|$text"
        }
        if (merged.size == existing.size) return
        youtubeMirrorSeedSnapshot = ParseSnapshot(
            timestamp = System.currentTimeMillis(),
            comments = merged
        )
        youtubeMirrorSeedCapturedAtUptimeMs = SystemClock.uptimeMillis()
        Log.d(
            TAG,
            "capture youtube mirror parser seed source=$source " +
                "added=${commentOnly.size} total=${merged.size}"
        )
    }

    private fun consumeYoutubeMirrorSeedSnapshot(): ParseSnapshot? {
        val snapshot = youtubeMirrorSeedSnapshot
        val capturedAtMs = youtubeMirrorSeedCapturedAtUptimeMs
        youtubeMirrorSeedSnapshot = null
        youtubeMirrorSeedCapturedAtUptimeMs = 0L
        if (snapshot == null || capturedAtMs <= 0L) return null

        val ageMs = SystemClock.uptimeMillis() - capturedAtMs
        if (ageMs !in 0..YOUTUBE_MIRROR_SEED_TTL_MS) {
            Log.d(TAG, "drop stale youtube parser seed ageMs=$ageMs")
            return null
        }
        return snapshot.copy(timestamp = System.currentTimeMillis())
    }

    private fun captureYoutubeMirrorSeedFromCurrentWindows() {
        val nodes = runCatching {
            extractVisibleTextNodesFromYoutubeWindows(requestCharacterBoxes = false)
        }.onFailure { error ->
            Log.w(TAG, "youtube mirror pre-overlay seed capture failed", error)
        }.getOrElse { emptyList() }

        rememberYoutubeMirrorSeedSnapshot(
            comments = extractYoutubeMirrorSeedComments(nodes),
            source = "pre-overlay"
        )
    }

    private fun captureYoutubeMirrorSeedFromEvent(event: AccessibilityEvent) {
        if (!youtubeSafeCommentMirrorController.isActive) return
        if (youtubeMirrorCollectionMode == YoutubeMirrorCollectionMode.IDLE) return
        if (youtubeMirrorExpectedSnapshotTimestampMs > 0L) return
        if (
            youtubeMirrorSeedSnapshot?.comments.orEmpty().size >=
            YOUTUBE_MIRROR_SEED_TARGET_COUNT
        ) {
            return
        }
        if (
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) {
            return
        }
        val nowMs = SystemClock.uptimeMillis()
        if (
            nowMs - youtubeMirrorLastSeedCaptureAtMs <
            YOUTUBE_MIRROR_SEED_CAPTURE_THROTTLE_MS
        ) {
            return
        }
        youtubeMirrorLastSeedCaptureAtMs = nowMs

        val sourceNode = event.source ?: return
        val nodes = runCatching {
            var candidateRoot = sourceNode
            var depth = 0
            while (depth < YOUTUBE_COMMENT_ROW_MAX_PARENT_DEPTH) {
                val parent = candidateRoot.parent ?: break
                if (!isYoutubeAccessibilityRoot(parent)) break
                candidateRoot = parent
                depth += 1
                if (
                    candidateRoot.viewIdResourceName.orEmpty() in
                    YOUTUBE_COMMENT_PANEL_CONTENT_VIEW_IDS ||
                    candidateRoot.viewIdResourceName == YOUTUBE_COMMENT_RESULTS_VIEW_ID
                ) {
                    break
                }
            }
            collectRawNodesFromRoot(
                root = candidateRoot,
                requestCharacterBoxes = false
            )
        }.onFailure { error ->
            Log.w(TAG, "youtube mirror accessibility-event capture failed", error)
        }.getOrElse { emptyList() }

        rememberYoutubeMirrorSeedSnapshot(
            comments = extractYoutubeMirrorSeedComments(nodes),
            source = "event-${event.eventType}"
        )
    }

    private fun extractYoutubeMirrorSeedComments(
        nodes: List<ParsedTextNode>
    ): List<ParsedComment> {
        if (nodes.isEmpty()) return emptyList()
        return YoutubeAnalysisTargetExtractor
            .extractTargets(
                nodes = nodes,
                screenHeight = resources.displayMetrics.heightPixels
            )
            .filter { comment ->
                YoutubeSafeCommentAssembler.isYoutubeAccessibilitySource(comment.authorId)
            }
            .distinctBy { comment ->
                val source = comment.authorId.orEmpty()
                    .removePrefix("android-accessibility-lookahead:")
                    .substringBefore(":line:")
                val text = comment.commentText
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .lowercase()
                "$source|$text"
            }
    }

    private fun analyzeYoutubeMirrorSeedSnapshot(
        runId: Long,
        attemptId: Long,
        source: String
    ): Boolean {
        val snapshot = consumeYoutubeMirrorSeedSnapshot() ?: return false
        if (snapshot.comments.isEmpty()) return false
        youtubeMirrorParsedCommentCount = max(
            youtubeMirrorParsedCommentCount,
            snapshot.comments.size
        )
        analyzeYoutubeMirrorSnapshot(
            runId = runId,
            attemptId = attemptId,
            snapshot = snapshot,
            source = source
        )
        return true
    }

    private fun collectVisibleYoutubeCommentOcrAnchors(
        panelSpec: MaskOverlaySpec,
        includeMirrorObscuredNodes: Boolean = false
    ): List<YoutubeCommentOcrAnchor> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let(roots::add)
        windows?.forEach { window ->
            val root = window.root ?: return@forEach
            if (isYoutubeAccessibilityRoot(root)) roots += root
        }

        val panelBounds = BoundsRect(
            left = panelSpec.left,
            top = panelSpec.top,
            right = panelSpec.left + panelSpec.width,
            bottom = panelSpec.top + panelSpec.height
        )
        val anchors = mutableListOf<YoutubeCommentOcrAnchor>()
        val seenRoots = mutableSetOf<String>()
        val seenAuthors = mutableSetOf<String>()
        val allowHiddenNodes = includeMirrorObscuredNodes &&
            youtubeSafeCommentMirrorController.isActive
        var visited = 0

        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 18 || visited >= 2_400) return
            visited += 1
            val label = visibleYoutubeAuthorLabel(node, allowHiddenNodes)
            if (label != null) {
                val authorBounds = youtubeNodeBounds(node, allowHiddenNodes)
                val authorKey = authorBounds?.let { bounds ->
                    "$label@${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
                }
                if (authorBounds != null && authorKey != null && seenAuthors.add(authorKey)) {
                    findYoutubeCommentRowForAuthor(
                        authorNode = node,
                        authorLabel = label,
                        allowHiddenNodes = allowHiddenNodes
                    )?.let { match ->
                        val intersectsPanel = match.rowBounds.bottom > panelBounds.top &&
                            match.rowBounds.top < panelBounds.bottom
                        if (intersectsPanel) {
                            anchors += YoutubeCommentOcrAnchor(
                                authorLabel = label,
                                authorBounds = match.authorBounds,
                                rowBounds = match.rowBounds,
                                replyBounds = match.replyBounds
                            )
                        }
                    }
                }
            }

            for (index in 0 until node.childCount) {
                val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                visit(child, depth + 1)
            }
        }

        for (root in roots) {
            if (!isYoutubeAccessibilityRoot(root)) continue
            val rootRect = Rect().also { rect -> root.getBoundsInScreen(rect) }
            val rootKey = "${rootRect.left},${rootRect.top},${rootRect.right},${rootRect.bottom},${root.className}"
            if (!seenRoots.add(rootKey)) continue

            val panelRoots = YOUTUBE_COMMENT_PANEL_CONTENT_VIEW_IDS
                .flatMap { viewId ->
                    runCatching { root.findAccessibilityNodeInfosByViewId(viewId) }
                        .getOrDefault(emptyList())
                }
                .filter { node -> node.isVisibleToUser || allowHiddenNodes }
            if (panelRoots.isEmpty()) {
                visit(root, 0)
            } else {
                panelRoots.forEach { panelRoot -> visit(panelRoot, 0) }
            }
        }

        return anchors.distinctBy { anchor ->
            val bounds = anchor.rowBounds
            "${anchor.authorLabel.lowercase()}@${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
        }
    }

    private fun requestYoutubeMirrorCommentOcr(
        runId: Long,
        panelSpec: MaskOverlaySpec,
        captureBehindMirror: Boolean = false
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !visualCaptureState.supported) {
            return false
        }
        val metrics = resources.displayMetrics
        val panelBounds = BoundsRect(
            left = panelSpec.left,
            top = panelSpec.top,
            right = panelSpec.left + panelSpec.width,
            bottom = panelSpec.top + panelSpec.height
        )
        val rows = YoutubeCommentOcrFallback.planRows(
            anchors = collectVisibleYoutubeCommentOcrAnchors(
                panelSpec = panelSpec,
                includeMirrorObscuredNodes = captureBehindMirror
            ),
            panelBounds = panelBounds,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels
        )
        if (rows.isEmpty()) {
            Log.d(TAG, "skip youtube mirror body OCR: no visible comment rows")
            return false
        }

        val rois = rows.map { row -> row.toRoi() }
        val started = requestYoutubeMirrorScreenshot(
            runId = runId,
            captureBehindMirror = captureBehindMirror,
            onCaptured = { screenshot ->
                visualTextOcrProcessor.recognize(screenshot, rois) { ocrCandidates ->
                    if (!screenshot.isRecycled) screenshot.recycle()
                    val comments = YoutubeCommentOcrFallback.assembleComments(
                        rows = rows,
                        ocrCandidates = ocrCandidates
                    )
                    handler.post {
                        if (
                            runId != youtubeMirrorSessionRunId ||
                            youtubeMirrorCollectionMode == YoutubeMirrorCollectionMode.IDLE ||
                            !youtubeSafeCommentMirrorController.isActive
                        ) {
                            return@post
                        }
                        if (comments.isEmpty()) {
                            continueYoutubeMirrorAfterOcrMiss(runId, "no-body-text")
                            return@post
                        }

                        val attemptId = youtubeMirrorCaptureAttemptId + 1L
                        youtubeMirrorCaptureAttemptId = attemptId
                        youtubeMirrorAwaitingBatch = true
                        youtubeMirrorExpectedSnapshotTimestampMs = 0L
                        youtubeMirrorAnalysisError = null
                        youtubeMirrorParsedCommentCount = max(
                            youtubeMirrorParsedCommentCount,
                            comments.size
                        )
                        val snapshot = ParseSnapshot(
                            timestamp = System.currentTimeMillis(),
                            comments = comments
                        )
                        Log.d(
                            TAG,
                            "capture youtube mirror comment bodies with focused OCR " +
                                "run=$runId rows=${rows.size} comments=${comments.size} " +
                                "behindMirror=$captureBehindMirror"
                        )
                        analyzeYoutubeMirrorSnapshot(
                            runId = runId,
                            attemptId = attemptId,
                            snapshot = snapshot,
                            source = "focused-comment-ocr"
                        )
                    }
                }
            },
            onFailure = { reason ->
                continueYoutubeMirrorAfterOcrMiss(runId, reason)
            }
        )
        if (started) {
            Log.d(
                TAG,
                "request youtube mirror focused OCR run=$runId rows=${rows.size} " +
                    "behindMirror=$captureBehindMirror"
            )
        }
        return started
    }

    private fun requestYoutubeMirrorScreenshot(
        runId: Long,
        captureBehindMirror: Boolean,
        onCaptured: (Bitmap) -> Unit,
        onFailure: (String) -> Unit
    ): Boolean {
        fun isCurrentRequest(): Boolean {
            return runId == youtubeMirrorSessionRunId &&
                youtubeMirrorCollectionMode != YoutubeMirrorCollectionMode.IDLE &&
                lastObservedPackage == YOUTUBE_PACKAGE &&
                (!captureBehindMirror || youtubeSafeCommentMirrorController.isActive)
        }

        fun restoreMirror(wasSuspended: Boolean) {
            if (!wasSuspended) return
            handler.post {
                youtubeSafeCommentMirrorController.restoreAfterCapture(true)
            }
        }

        fun handleSuccess(
            screenshotResult: ScreenshotResult,
            mirrorWasSuspended: Boolean
        ) {
            restoreMirror(mirrorWasSuspended)
            val screenshot = screenshotResult.toSoftwareBitmap()
            if (screenshot == null) {
                onFailure("bitmap-unavailable")
                return
            }
            if (!isCurrentRequest()) {
                if (!screenshot.isRecycled) screenshot.recycle()
                return
            }
            onCaptured(screenshot)
        }

        fun requestDisplayScreenshot() {
            if (!isCurrentRequest()) return
            val mirrorWasSuspended = captureBehindMirror &&
                youtubeSafeCommentMirrorController.suspendForCapture()
            val request = Runnable {
                if (!isCurrentRequest()) {
                    restoreMirror(mirrorWasSuspended)
                    return@Runnable
                }
                try {
                    lastScreenshotRequestAtMs = SystemClock.uptimeMillis()
                    takeScreenshot(
                        Display.DEFAULT_DISPLAY,
                        visualExecutor,
                        object : TakeScreenshotCallback {
                            override fun onSuccess(screenshotResult: ScreenshotResult) {
                                handleSuccess(screenshotResult, mirrorWasSuspended)
                            }

                            override fun onFailure(errorCode: Int) {
                                restoreMirror(mirrorWasSuspended)
                                onFailure("screenshot-$errorCode")
                            }
                        }
                    )
                } catch (error: RuntimeException) {
                    restoreMirror(mirrorWasSuspended)
                    Log.w(TAG, "youtube mirror display screenshot request failed", error)
                    onFailure("screenshot-request-failed")
                }
            }
            if (mirrorWasSuspended) {
                handler.postDelayed(request, YOUTUBE_MIRROR_CAPTURE_HIDE_SETTLE_MS)
            } else {
                request.run()
            }
        }

        val youtubeWindowId = if (
            captureBehindMirror &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            currentYoutubeApplicationWindowId()
        } else {
            null
        }
        if (youtubeWindowId == null) {
            requestDisplayScreenshot()
            return true
        }

        return try {
            lastScreenshotRequestAtMs = SystemClock.uptimeMillis()
            takeScreenshotOfWindow(
                youtubeWindowId,
                visualExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        handleSuccess(screenshotResult, mirrorWasSuspended = false)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(
                            TAG,
                            "youtube window screenshot failed code=$errorCode; use display fallback"
                        )
                        handler.post { requestDisplayScreenshot() }
                    }
                }
            )
            true
        } catch (error: RuntimeException) {
            Log.w(TAG, "youtube window screenshot request failed; use display fallback", error)
            handler.post { requestDisplayScreenshot() }
            true
        }
    }

    private fun currentYoutubeApplicationWindowId(): Int? {
        val activeRoot = rootInActiveWindow
        if (activeRoot != null && isYoutubeAccessibilityRoot(activeRoot)) {
            return activeRoot.windowId.takeIf { windowId -> windowId >= 0 }
        }
        return windows
            ?.asSequence()
            ?.mapNotNull { window ->
                val root = runCatching { window.root }.getOrNull() ?: return@mapNotNull null
                if (!isYoutubeAccessibilityRoot(root)) return@mapNotNull null
                window.id.takeIf { windowId -> windowId >= 0 }
            }
            ?.firstOrNull()
    }

    private fun continueYoutubeMirrorAfterOcrMiss(runId: Long, reason: String) {
        handler.post {
            if (
                runId != youtubeMirrorSessionRunId ||
                youtubeMirrorCollectionMode == YoutubeMirrorCollectionMode.IDLE ||
                !youtubeSafeCommentMirrorController.isActive ||
                youtubeMirrorAwaitingBatch
            ) {
                return@post
            }
            Log.d(TAG, "youtube mirror focused OCR miss run=$runId reason=$reason")
            if (youtubeMirrorCollectionMode == YoutubeMirrorCollectionMode.PREFETCH) {
                if (debugYoutubeHarnessActive) {
                    scheduleYoutubeMirrorCurrentViewportCapture("prefetch-debug-ocr-$reason")
                    return@post
                }
                youtubeMirrorAnalysisError = "PREFETCH_OCR_MISS:$reason"
                finishYoutubeMirrorCollection("prefetch-ocr-miss:$reason")
                return@post
            }
            scheduleYoutubeMirrorCurrentViewportCapture("ocr-fallback-$reason")
        }
    }


    private fun beginYoutubeMirrorSession(spec: MaskOverlaySpec) {
        cancelScheduledParse()
        youtubeMirrorSessionRunId += 1L
        youtubeMirrorCaptureAttemptId += 1L
        youtubeMirrorPanelSpec = spec
        youtubeMirrorCollectionMode = YoutubeMirrorCollectionMode.INITIAL
        youtubeMirrorCapturedViewports = 0
        youtubeMirrorEmptyRetries = 0
        youtubeMirrorAwaitingBatch = false
        youtubeMirrorExpectedSnapshotTimestampMs = 0L
        youtubeMirrorParsedCommentCount = 0
        youtubeMirrorAnalysisError = null
        youtubeMirrorLastSeedCaptureAtMs = 0L
        youtubeMirrorReachedEnd = false
        youtubeMirrorPanelMissingSinceMs = 0L
        youtubeMirrorPanelNativeObserved =
            hasVisibleYoutubeCommentPanelStructureInAnyWindow()
        youtubeMirrorSessionStartedAtUptimeMs = SystemClock.uptimeMillis()
        youtubeMirrorSessionStartedAtEpochMs = System.currentTimeMillis()
        youtubeMirrorSeenViewportSignatures.clear()
        youtubeSafeCommentBuffer.clear()
        youtubeMirrorSeedSnapshot = null
        youtubeMirrorSeedCapturedAtUptimeMs = 0L
        captureYoutubeMirrorSeedFromCurrentWindows()
        val runId = youtubeMirrorSessionRunId
        val focusedOcrStarted = requestYoutubeMirrorCommentOcr(runId, spec)
        youtubeSafeCommentMirrorController.startLoading(spec)
        scheduleYoutubeMirrorPanelPresenceAudit("session-start")
        youtubeCommentInitialAnalysisCompleted = false
        scheduleYoutubeMirrorInitialTimeout(runId, YOUTUBE_MIRROR_INITIAL_TIMEOUT_MS)

        if (!focusedOcrStarted) {
            scheduleYoutubeMirrorCurrentViewportCapture("initial-current")
        }
        Log.d(
            TAG,
            "begin youtube safe mirror session run=$runId " +
                "seedComments=${youtubeMirrorSeedSnapshot?.comments.orEmpty().size} " +
                "focusedOcr=$focusedOcrStarted"
        )
    }

    private fun analyzeYoutubeMirrorSnapshot(
        runId: Long,
        attemptId: Long,
        snapshot: ParseSnapshot,
        source: String
    ) {
        youtubeMirrorExpectedSnapshotTimestampMs = snapshot.timestamp
        Log.d(
            TAG,
            "analyze youtube parser snapshot run=$runId source=$source " +
                "comments=${snapshot.comments.size}"
        )

        Thread(
            {
                val analysis = runCatching {
                    AndroidAnalysisClient.analyzeSnapshot(applicationContext, snapshot)
                }
                val attempt = analysis.getOrNull()
                val response = attempt?.takeIf { result -> result.ok }?.response
                if (response != null) {
                    Log.d(
                        TAG,
                        "youtube parser snapshot analyzed run=$runId source=$source " +
                            "comments=${snapshot.comments.size} results=${response.results.size}"
                    )
                    onYoutubeMirrorAccessibilityResults(
                        results = response.results,
                        snapshotTimestampMs = snapshot.timestamp
                    )
                    return@Thread
                }

                analysis.exceptionOrNull()?.let { error ->
                    Log.w(TAG, "youtube parser analysis failed run=$runId source=$source", error)
                }
                val analysisError = analysis.exceptionOrNull()?.message
                    ?.takeIf { message -> message.isNotBlank() }
                    ?: attempt?.error
                    ?: "ANALYSIS_FAILED"
                if (attempt != null) {
                    Log.w(
                        TAG,
                        "youtube parser analysis failed run=$runId source=$source " +
                            "url=${attempt.url} error=$analysisError"
                    )
                }
                handleYoutubeMirrorAnalysisFailure(
                    runId = runId,
                    attemptId = attemptId,
                    snapshotTimestampMs = snapshot.timestamp,
                    error = analysisError
                )
            },
            "YoutubeMirrorAnalysis-$runId"
        ).start()
    }

    private fun handleYoutubeMirrorAnalysisFailure(
        runId: Long,
        attemptId: Long,
        snapshotTimestampMs: Long,
        error: String
    ) {
        handler.post {
            if (
                runId != youtubeMirrorSessionRunId ||
                attemptId != youtubeMirrorCaptureAttemptId ||
                !youtubeMirrorAwaitingBatch ||
                youtubeMirrorExpectedSnapshotTimestampMs != snapshotTimestampMs
            ) {
                return@post
            }
            youtubeMirrorAnalysisError = error
            youtubeMirrorAwaitingBatch = false
            youtubeMirrorExpectedSnapshotTimestampMs = 0L
            finishYoutubeMirrorCollection("analysis-failure")
        }
    }
    private fun onYoutubeMirrorAccessibilityResults(
        results: List<AndroidAnalysisResultItem>,
        snapshotTimestampMs: Long
    ) {
        if (!YOUTUBE_SAFE_MIRROR_ENABLED || !youtubeSafeCommentMirrorController.isActive) return
        val runId = youtubeMirrorSessionRunId
        val accessibilityResults = results.filter { item ->
            YoutubeSafeCommentAssembler.isYoutubeAccessibilitySource(item.authorId)
        }
        val batch = YoutubeSafeCommentAssembler.assembleAccessibilityResults(accessibilityResults)
        val signature = accessibilityResults
            .sortedBy { item ->
                "${item.authorId.orEmpty()}|${item.original}"
            }
            .joinToString("|") { item ->
                val source = item.authorId.orEmpty()
                    .removePrefix("android-accessibility-lookahead:")
                    .lowercase()
                val normalized = item.original.replace(Regex("\\s+"), " ").trim().lowercase()
                "$source|$normalized|${item.isOffensive}"
            }

        handler.post {
            if (runId != youtubeMirrorSessionRunId) return@post
            if (youtubeMirrorCollectionMode == YoutubeMirrorCollectionMode.IDLE) return@post
            if (snapshotTimestampMs < youtubeMirrorSessionStartedAtEpochMs) {
                Log.d(
                    TAG,
                    "drop stale youtube mirror accessibility batch " +
                        "snapshot=$snapshotTimestampMs session=$youtubeMirrorSessionStartedAtEpochMs"
                )
                return@post
            }
            val expectedSnapshotTimestampMs = youtubeMirrorExpectedSnapshotTimestampMs
            if (
                expectedSnapshotTimestampMs > 0L &&
                snapshotTimestampMs != expectedSnapshotTimestampMs
            ) {
                Log.d(
                    TAG,
                    "drop unexpected youtube mirror accessibility batch " +
                        "snapshot=$snapshotTimestampMs expected=$expectedSnapshotTimestampMs"
                )
                return@post
            }
            if (!youtubeMirrorAwaitingBatch) {
                Log.d(TAG, "drop unscheduled youtube mirror accessibility batch")
                return@post
            }

            youtubeMirrorAwaitingBatch = false
            youtubeMirrorExpectedSnapshotTimestampMs = 0L
            youtubeMirrorAnalysisError = null
            youtubeMirrorCaptureAttemptId += 1L
            val isNewViewport = signature.isNotBlank() &&
                youtubeMirrorSeenViewportSignatures.add(signature)
            var added = 0
            if (isNewViewport) {
                youtubeMirrorCapturedViewports += 1
                added = youtubeSafeCommentBuffer.add(batch)
                Log.d(
                    TAG,
                    "collect youtube mirror accessibility viewport run=$runId " +
                        "mode=$youtubeMirrorCollectionMode viewport=$youtubeMirrorCapturedViewports " +
                        "raw=${batch.rawLineCount} added=$added " +
                        "safe=${youtubeSafeCommentBuffer.comments().size} " +
                        "harmful=${youtubeSafeCommentBuffer.harmfulCommentCount}"
                )
            }

            val panelSpec = youtubeMirrorPanelSpec ?: return@post
            val safeComments = youtubeSafeCommentBuffer.comments()
            if (youtubeSafeCommentMirrorController.isReady || safeComments.isNotEmpty()) {
                youtubeSafeCommentMirrorController.showComments(
                    spec = panelSpec,
                    comments = safeComments,
                    prefetching = youtubeMirrorCollectionMode != YoutubeMirrorCollectionMode.IDLE,
                    emptyMessage = youtubeMirrorEmptyStateMessage()
                )
            } else {
                youtubeSafeCommentMirrorController.updateLoading(
                    spec = panelSpec,
                    collectedCount = safeComments.size
                )
            }

            if (
                batch.rawLineCount == 0 &&
                youtubeMirrorCollectionMode == YoutubeMirrorCollectionMode.PREFETCH
            ) {
                youtubeMirrorAnalysisError = "EMPTY_PREFETCH_BATCH"
                finishYoutubeMirrorCollection("prefetch-empty-current-viewport")
                return@post
            }
            if (batch.rawLineCount == 0 && youtubeMirrorEmptyRetries < YOUTUBE_MIRROR_MAX_EMPTY_RETRIES) {
                youtubeMirrorEmptyRetries += 1
                scheduleYoutubeMirrorCurrentViewportCapture("empty-${youtubeMirrorEmptyRetries}")
                return@post
            }
            youtubeMirrorEmptyRetries = 0

            when (youtubeMirrorCollectionMode) {
                YoutubeMirrorCollectionMode.INITIAL -> {
                    finishYoutubeMirrorCollection("initial-current-viewport")
                }

                YoutubeMirrorCollectionMode.PREFETCH -> {
                    finishYoutubeMirrorCollection(
                        "prefetch-current-viewport:new=$isNewViewport:added=$added"
                    )
                }

                YoutubeMirrorCollectionMode.IDLE -> Unit
            }
        }
    }

    private fun scheduleYoutubeMirrorCurrentViewportCapture(reason: String) {
        if (!isYoutubeMirrorCollectionActive()) return
        val runId = youtubeMirrorSessionRunId
        val attemptId = youtubeMirrorCaptureAttemptId + 1L
        youtubeMirrorCaptureAttemptId = attemptId
        youtubeMirrorAwaitingBatch = true
        youtubeMirrorExpectedSnapshotTimestampMs = 0L
        lastSnapshotSignature = null
        lastVisualSupplement = null
        lastVisualRefreshSignature = null
        lastVisualRefreshCompletedAtMs = 0L
        visualSceneRevision += 1L

        handler.postDelayed(
            {
                if (
                    runId != youtubeMirrorSessionRunId ||
                    attemptId != youtubeMirrorCaptureAttemptId ||
                    !youtubeMirrorAwaitingBatch
                ) {
                    return@postDelayed
                }
                if (
                    reason == "initial-current" &&
                    analyzeYoutubeMirrorSeedSnapshot(
                        runId = runId,
                        attemptId = attemptId,
                        source = reason
                    )
                ) {
                    return@postDelayed
                }
                scheduleParse(
                    delayMs = 0L,
                    eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                    replaceExisting = true
                )
                Log.d(
                    TAG,
                    "capture youtube mirror with existing parser run=$runId reason=$reason"
                )
            },
            if (reason == "initial-current") {
                YOUTUBE_MIRROR_CAPTURE_SETTLE_MS
            } else {
                YOUTUBE_MIRROR_NEXT_STEP_DELAY_MS
            }
        )
        handler.postDelayed(
            {
                if (
                    runId == youtubeMirrorSessionRunId &&
                    attemptId == youtubeMirrorCaptureAttemptId &&
                    youtubeMirrorAwaitingBatch
                ) {
                    youtubeMirrorAwaitingBatch = false
                    youtubeMirrorExpectedSnapshotTimestampMs = 0L
                    youtubeMirrorAnalysisError = "ANALYSIS_TIMEOUT"
                    youtubeMirrorEmptyRetries = 0
                    finishYoutubeMirrorCollection("current-viewport-timeout")
                }
            },
            YOUTUBE_MIRROR_BATCH_TIMEOUT_MS
        )
    }

    private fun scheduleYoutubeMirrorInitialTimeout(runId: Long, delayMs: Long) {
        handler.postDelayed(
            {
                if (
                    runId != youtubeMirrorSessionRunId ||
                    youtubeMirrorCollectionMode != YoutubeMirrorCollectionMode.INITIAL
                ) {
                    return@postDelayed
                }
                youtubeMirrorAnalysisError =
                    youtubeMirrorAnalysisError ?: "ANALYSIS_TIMEOUT"
                finishYoutubeMirrorCollection(
                    if (youtubeSafeCommentBuffer.rawLineCount == 0) {
                        "initial-timeout-empty"
                    } else {
                        "initial-timeout"
                    }
                )
            },
            delayMs
        )
    }
    private fun startYoutubeMirrorPrefetch() {
        if (!YOUTUBE_SAFE_MIRROR_ENABLED) return
        if (!youtubeSafeCommentMirrorController.isReady) return
        if (youtubeMirrorCollectionMode != YoutubeMirrorCollectionMode.IDLE) return
        if (youtubeMirrorReachedEnd || lastObservedPackage != YOUTUBE_PACKAGE) return

        val panelSpec = youtubeMirrorPanelSpec ?: return
        val runId = youtubeMirrorSessionRunId
        youtubeMirrorCollectionMode = YoutubeMirrorCollectionMode.PREFETCH
        youtubeMirrorEmptyRetries = 0
        youtubeMirrorAwaitingBatch = false
        youtubeMirrorExpectedSnapshotTimestampMs = 0L
        youtubeMirrorAnalysisError = null
        youtubeSafeCommentMirrorController.setPrefetching(true)

        if (!performYoutubeCommentScroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            youtubeMirrorReachedEnd = true
            finishYoutubeMirrorCollection("prefetch-native-end")
            return
        }

        youtubeSyntheticScrollUntilMs = max(
            youtubeSyntheticScrollUntilMs,
            SystemClock.uptimeMillis() + YOUTUBE_PRECHECK_SYNTHETIC_SCROLL_GRACE_MS
        )
        handler.postDelayed(
            {
                if (
                    runId != youtubeMirrorSessionRunId ||
                    youtubeMirrorCollectionMode != YoutubeMirrorCollectionMode.PREFETCH ||
                    !youtubeSafeCommentMirrorController.isActive
                ) {
                    return@postDelayed
                }
                val started = requestYoutubeMirrorCommentOcr(
                    runId = runId,
                    panelSpec = youtubeMirrorPanelSpec ?: panelSpec,
                    captureBehindMirror = true
                )
                if (!started) {
                    if (debugYoutubeHarnessActive) {
                        scheduleYoutubeMirrorCurrentViewportCapture("prefetch-debug-current")
                        return@postDelayed
                    }
                    youtubeMirrorAnalysisError = "PREFETCH_OCR_UNAVAILABLE"
                    finishYoutubeMirrorCollection("prefetch-ocr-unavailable")
                }
            },
            YOUTUBE_MIRROR_PREFETCH_SCROLL_SETTLE_MS
        )
        handler.postDelayed(
            {
                if (
                    runId == youtubeMirrorSessionRunId &&
                    youtubeMirrorCollectionMode == YoutubeMirrorCollectionMode.PREFETCH
                ) {
                    youtubeMirrorAnalysisError = "PREFETCH_TIMEOUT"
                    finishYoutubeMirrorCollection("prefetch-timeout")
                }
            },
            YOUTUBE_MIRROR_BATCH_TIMEOUT_MS
        )
        Log.d(TAG, "start youtube mirror one-viewport prefetch run=$runId")
    }

    private fun finishYoutubeMirrorCollection(reason: String) {
        if (!youtubeSafeCommentMirrorController.isActive) return
        val previousMode = youtubeMirrorCollectionMode
        val readySpec = youtubeMirrorPanelSpec
            ?: lastYoutubeScrollLoadingSpec
            ?: lastYoutubeCommentPaneSpec
        youtubeMirrorCollectionMode = YoutubeMirrorCollectionMode.IDLE
        youtubeMirrorAwaitingBatch = false
        youtubeMirrorSeedSnapshot = null
        youtubeMirrorSeedCapturedAtUptimeMs = 0L
        youtubeMirrorExpectedSnapshotTimestampMs = 0L
        youtubeMirrorCaptureAttemptId += 1L
        youtubeCommentInitialAnalysisCompleted = true
        youtubeLoadingOverlayStartedAtMs = 0L
        lastYoutubeCommentPaneSpec = null
        provisionalAccessibilityMaskActive = false
        provisionalVisualMaskActive = false
        maskOverlayController.clear()
        readySpec?.let { spec ->
            youtubeMirrorPanelSpec = spec
            youtubeSafeCommentMirrorController.showComments(
                spec = spec,
                comments = youtubeSafeCommentBuffer.comments(),
                prefetching = false,
                emptyMessage = youtubeMirrorEmptyStateMessage()
            )
        }
        Log.d(
            TAG,
            "finish youtube safe mirror collection reason=$reason mode=$previousMode " +
                "safe=${youtubeSafeCommentBuffer.comments().size} " +
                "raw=${youtubeSafeCommentBuffer.rawLineCount} " +
                "parsed=$youtubeMirrorParsedCommentCount " +
                "analysisError=${youtubeMirrorAnalysisError.orEmpty()} " +
                "harmful=${youtubeSafeCommentBuffer.harmfulCommentCount} " +
                "spec=${readySpec != null} ready=${youtubeSafeCommentMirrorController.isReady}"
        )
    }


    private fun resetYoutubeMirrorSession(reason: String) {
        if (youtubeSafeCommentMirrorController.isActive) {
            Log.d(TAG, "reset youtube safe mirror reason=$reason")
        }
        youtubeMirrorSessionRunId += 1L
        youtubeMirrorCaptureAttemptId += 1L
        youtubeMirrorPanelSpec = null
        youtubeMirrorCollectionMode = YoutubeMirrorCollectionMode.IDLE
        youtubeMirrorNativeScrollNode = null
        youtubeMirrorCapturedViewports = 0
        youtubeMirrorEmptyRetries = 0
        youtubeMirrorAwaitingBatch = false
        youtubeMirrorExpectedSnapshotTimestampMs = 0L
        youtubeMirrorParsedCommentCount = 0
        youtubeMirrorAnalysisError = null
        youtubeMirrorReachedEnd = false
        youtubeMirrorPanelMissingSinceMs = 0L
        youtubeMirrorPanelNativeObserved = false
        youtubeMirrorPanelAuditScheduledRunId = -1L
        youtubeMirrorSessionStartedAtUptimeMs = 0L
        youtubeMirrorSessionStartedAtEpochMs = 0L
        youtubeMirrorSeedSnapshot = null
        youtubeMirrorSeedCapturedAtUptimeMs = 0L
        youtubeMirrorLastSeedCaptureAtMs = 0L
        youtubeMirrorSeenViewportSignatures.clear()
        youtubeSafeCommentBuffer.clear()
        youtubeSafeCommentMirrorController.clear()
    }

    private fun isYoutubeMirrorCollectionActive(): Boolean {
        return YOUTUBE_SAFE_MIRROR_ENABLED &&
            youtubeSafeCommentMirrorController.isActive &&
            youtubeMirrorCollectionMode != YoutubeMirrorCollectionMode.IDLE &&
            lastObservedPackage == YOUTUBE_PACKAGE
    }


    private fun handleInstagramSafeMirrorEvent(event: AccessibilityEvent): Boolean {
        if (!INSTAGRAM_SAFE_MIRROR_ENABLED) return false

        if (instagramSafeCommentMirrorSession.isActive) {
            if (
                event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
                isInstagramCommentPanelCloseClick(event.source)
            ) {
                invalidateInstagramMirrorSession("comment-close-click")
                clearMaskOverlay()
                return true
            }
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_TOUCH_INTERACTION_END,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED,
                AccessibilityEvent.TYPE_WINDOWS_CHANGED ->
                    instagramSafeCommentMirrorSession.schedulePresenceAudit(
                        "event-${event.eventType}"
                    )
            }
            return true
        }

        val isProbeEvent = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> true
            else -> false
        }
        if (!isProbeEvent) return false

        val forceProbe =
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (
            renderInstagramCommentSurfaceIfPresent(
                reason = "event-${event.eventType}",
                force = forceProbe
            )
        ) {
            return true
        }

        if (forceProbe) {
            scheduleInstagramPanelProbeRetries("event-${event.eventType}")
        }
        return false
    }

    private fun scheduleInstagramPanelProbeRetries(reason: String) {
        if (!INSTAGRAM_SAFE_MIRROR_ENABLED) return
        val generation = instagramPanelProbeGeneration + 1L
        instagramPanelProbeGeneration = generation
        INSTAGRAM_PANEL_PROBE_RETRY_DELAYS_MS.forEachIndexed { index, delayMs ->
            handler.postDelayed(
                {
                    if (
                        generation != instagramPanelProbeGeneration ||
                        lastObservedPackage != INSTAGRAM_PACKAGE ||
                        instagramSafeCommentMirrorSession.isActive
                    ) {
                        return@postDelayed
                    }
                    renderInstagramCommentSurfaceIfPresent(
                        reason = "$reason-retry-${index + 1}",
                        force = true
                    )
                },
                delayMs
            )
        }
    }

    private fun renderInstagramCommentSurfaceIfPresent(
        reason: String,
        force: Boolean = false
    ): Boolean {
        if (
            !INSTAGRAM_SAFE_MIRROR_ENABLED ||
            lastObservedPackage != INSTAGRAM_PACKAGE
        ) {
            return false
        }
        val nowMs = SystemClock.uptimeMillis()
        if (nowMs < instagramMirrorReopenSuppressedUntilMs) {
            return false
        }
        if (
            !force &&
            nowMs - lastInstagramPanelProbeAtMs < INSTAGRAM_PANEL_PROBE_INTERVAL_MS
        ) {
            return false
        }
        lastInstagramPanelProbeAtMs = nowMs

        val surface = detectInstagramCommentSurface() ?: return false
        val bounds = surface.boundsInScreen
        val spec = MaskOverlaySpec(
            left = bounds.left,
            top = bounds.top,
            width = bounds.right - bounds.left,
            height = bounds.bottom - bounds.top,
            label = "instagram-comments-loading",
            allowScrollTranslation = false,
            debugSource = "instagram-safe-comment-mirror",
            style = MaskOverlayStyle.LOADING
        )
        val rendered = instagramSafeCommentMirrorSession.showSurface(spec, reason)
        if (rendered) {
            instagramPanelProbeGeneration += 1L
            Log.d(
                TAG,
                "instagram panel confirmed reason=$reason " +
                    "confidence=${surface.confidence} comments=${surface.commentCount} " +
                    "bounds=${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
            )
        }
        return rendered
    }

    private fun detectInstagramCommentSurface(): InstagramCommentSurface? {
        if (lastObservedPackage != INSTAGRAM_PACKAGE) return null
        val nodes = extractInstagramPanelProbeNodes()
        if (nodes.isEmpty()) return null
        val metrics = resources.displayMetrics
        val surface = InstagramCommentSurfaceDetector.detect(
            nodes = nodes,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            density = metrics.density
        )
        if (debugInstagramHarnessActive) {
            val titleCount = nodes.count { node ->
                node.displayText.orEmpty().trim().equals("Comments", ignoreCase = true)
            }
            val visibleCount = nodes.count { node -> node.isVisibleToUser }
            val visibleTitleCount = nodes.count { node ->
                node.isVisibleToUser &&
                    node.displayText.orEmpty().trim().equals("Comments", ignoreCase = true)
            }
            val parsedCount = InstagramCommentExtractor.extractComments(nodes).size
            Log.d(
                TAG,
                "instagram debug panel probe nodes=${nodes.size} titles=$titleCount " +
                    "visible=$visibleCount visibleTitles=$visibleTitleCount parsed=$parsedCount " +
                    "metrics=${metrics.widthPixels}x${metrics.heightPixels}@${metrics.density} " +
                    "detected=${surface != null}"
            )
        }
        return surface
    }

    private fun buildInstagramMirrorSnapshot(source: String): ParseSnapshot {
        val captureStartedAtMs = SystemClock.uptimeMillis()
        val nodes = extractVisibleTextNodesFromInstagramWindows(
            requestCharacterBoxes = false
        )
        val parsedComments = InstagramCommentExtractor.extractComments(nodes)
        val comments = InstagramCommentAnalysisAdapter.adapt(parsedComments)
        val snapshot = ParseSnapshot(
            timestamp = System.currentTimeMillis(),
            comments = comments
        )
        Log.d(
            TAG,
            "capture instagram parser snapshot source=$source nodes=${nodes.size} " +
                "comments=${comments.size} captureMs=" +
                (SystemClock.uptimeMillis() - captureStartedAtMs)
        )
        return snapshot
    }

    private fun invalidateInstagramMirrorSession(reason: String) {
        instagramPanelProbeGeneration += 1L
        lastInstagramPanelProbeAtMs = 0L
        if (instagramSafeCommentMirrorSession.isActive) {
            instagramMirrorReopenSuppressedUntilMs =
                SystemClock.uptimeMillis() + 1_200L
        }
        instagramSafeCommentMirrorSession.reset(reason)
    }

    private fun isInstagramCommentPanelCloseClick(
        source: AccessibilityNodeInfo?
    ): Boolean {
        var current = source ?: return false
        repeat(4) {
            val id = current.viewIdResourceName.orEmpty().lowercase()
            val labels = listOfNotNull(
                current.text?.toString(),
                current.contentDescription?.toString()
            ).map { value ->
                value.replace(Regex("\\s+"), " ").trim().lowercase()
            }
            val closeId =
                id.endsWith("close") ||
                    id.endsWith("close_button") ||
                    id.endsWith("back") ||
                    id.endsWith("back_button") ||
                    (
                        id.contains("comment") &&
                            (id.contains("close") || id.contains("back"))
                        )
            val closeLabel = labels.any { label ->
                label == "close" ||
                    label == "back" ||
                    label == "\uB2EB\uAE30" ||
                    label == "\uB4A4\uB85C" ||
                    (
                        label.contains("comment") &&
                            (label.contains("close") || label.contains("back"))
                        ) ||
                    (
                        label.contains("\uB313\uAE00") &&
                            label.contains("\uB2EB\uAE30")
                        )
            }
            if (closeId || closeLabel) return true
            current = runCatching { current.parent }.getOrNull() ?: return false
        }
        return false
    }

    private fun performInstagramCommentScroll(): Boolean {
        val paneSpec = instagramSafeCommentMirrorSession.currentPanelSpec
            ?: return false
        val node = findInstagramCommentScrollNode()
        if (node != null) {
            val handled = runCatching {
                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }.onFailure { error ->
                Log.w(TAG, "instagram comment node scroll failed", error)
            }.getOrDefault(false)
            val rect = Rect().also { node.getBoundsInScreen(it) }
            Log.d(
                TAG,
                "instagram comment node scroll handled=$handled " +
                    "id=${node.viewIdResourceName.orEmpty()} " +
                    "bounds=${rect.left},${rect.top},${rect.right},${rect.bottom}"
            )
            if (handled) return true
        } else {
            Log.d(TAG, "instagram comment scroll node unavailable; use gesture fallback")
        }
        return dispatchInstagramCommentScrollGesture(paneSpec)
    }

    private fun dispatchInstagramCommentScrollGesture(paneSpec: MaskOverlaySpec): Boolean {
        val centerX = paneSpec.left + paneSpec.width * 0.5f
        val startY = paneSpec.top + paneSpec.height * 0.82f
        val endY = paneSpec.top + paneSpec.height * 0.28f
        if (startY - endY < 120f) return false

        val swipePath = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(swipePath, 0L, 360L))
            .build()
        val accepted = dispatchGesture(gesture, null, null)
        Log.d(
            TAG,
            "instagram comment gesture scroll accepted=$accepted " +
                "from=${centerX.toInt()},${startY.toInt()} " +
                "to=${centerX.toInt()},${endY.toInt()}"
        )
        return accepted
    }

    private fun findInstagramCommentScrollNode(): AccessibilityNodeInfo? {
        val paneSpec = instagramSafeCommentMirrorSession.currentPanelSpec
            ?: return null
        val roots = mutableListOf<AccessibilityNodeInfo>()
        findBestInstagramAccessibilityRoot()?.let(roots::add)
        rootInActiveWindow?.let(roots::add)
        windows?.forEach { window ->
            window.root?.let(roots::add)
        }

        var bestNode: AccessibilityNodeInfo? = null
        var bestScore = Int.MIN_VALUE
        val seenNodes = mutableSetOf<String>()
        var visitedNodeCount = 0

        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 24 || visitedNodeCount >= 2_000) return
            visitedNodeCount += 1
            val packageName = node.packageName?.toString().orEmpty()
            val expectedPackage = if (debugInstagramHarnessActive) {
                applicationContext.packageName
            } else {
                INSTAGRAM_PACKAGE
            }
            if (packageName.isNotBlank() && packageName != expectedPackage) return

            val rect = Rect().also { node.getBoundsInScreen(it) }
            val nodeKey =
                "${node.windowId}|${rect.left},${rect.top},${rect.right},${rect.bottom}," +
                    "${node.className},${node.viewIdResourceName}"
            if (!seenNodes.add(nodeKey)) return

            val supportsForward = node.actionList.any { action ->
                action.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }
            if (debugInstagramHarnessActive && (node.isScrollable || supportsForward)) {
                Log.d(
                    TAG,
                    "instagram scroll candidate package=$packageName class=${node.className} " +
                        "visible=${node.isVisibleToUser} scrollable=${node.isScrollable} " +
                        "forward=$supportsForward intersects=${rectIntersectsSpec(rect, paneSpec)}"
                )
            }
            if (
                (node.isScrollable || supportsForward) &&
                rect.width() >= 160 &&
                rect.height() >= 180 &&
                rectIntersectsSpec(rect, paneSpec)
            ) {
                val id = node.viewIdResourceName.orEmpty().lowercase()
                val className = node.className?.toString().orEmpty()
                var score = rect.height() + rect.width() / 4
                if (supportsForward) score += 2_000
                if (id.contains("comment")) score += 5_000
                if (
                    id.contains("recycler") ||
                    id.contains("list") ||
                    id.contains("thread")
                ) {
                    score += 2_000
                }
                if (
                    className.contains("RecyclerView", ignoreCase = true) ||
                    className.contains("ScrollView", ignoreCase = true) ||
                    className.contains("ListView", ignoreCase = true)
                ) {
                    score += 1_000
                }
                if (
                    rect.left >= paneSpec.left - 48 &&
                    rect.right <= paneSpec.left + paneSpec.width + 48
                ) {
                    score += 800
                }
                if (rect.top <= paneSpec.top + 180) score += 400
                if (score > bestScore) {
                    bestScore = score
                    bestNode = node
                }
            }

            for (index in 0 until node.childCount) {
                visit(runCatching { node.getChild(index) }.getOrNull(), depth + 1)
            }
        }

        roots
            .asSequence()
            .filter(::isInstagramAccessibilityRoot)
            .forEach { root -> visit(root, 0) }
        if (debugInstagramHarnessActive) {
            Log.d(
                TAG,
                "instagram scroll search roots=${roots.size} visited=$visitedNodeCount " +
                    "bestScore=$bestScore found=${bestNode != null}"
            )
        }
        return bestNode
    }

    private fun renderYoutubeVisualCommentPanelOverlay(
        visualRoiPlan: VisualTextRoiPlan,
        parseStartedAtMs: Long
    ): Long {
        if (youtubeCommentInitialAnalysisCompleted) {
            return -1L
        }
        val commentPanelBounds = youtubeCommentPanelBounds(visualRoiPlan)
        if (commentPanelBounds.isEmpty()) return -1L
        if (!hasConfirmedYoutubeCommentPanel(visualRoiPlan)) {
            Log.d(TAG, "skip youtube visual loading: comment sort marker not confirmed")
            return -1L
        }

        val metrics = resources.displayMetrics
        val spec = buildStrictYoutubeVisualCommentPanelSpec(
            bounds = commentPanelBounds,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            style = MaskOverlayStyle.LOADING,
            label = "comments-loading",
            debugSource = "youtube-comment-pane-loading-visual"
        ) ?: return -1L

        val previousLoadingSpec = lastYoutubeCommentPaneSpec
        val nowMs = SystemClock.uptimeMillis()
        if (nowMs < youtubeLoadingSuppressedUntilMs) {
            Log.d(
                TAG,
                "skip youtube visual loading overlay: suppressed remainingMs=${youtubeLoadingSuppressedUntilMs - nowMs}"
            )
            return -1L
        }
        if (
            youtubeCommentInitialAnalysisCompleted &&
            youtubeLoadingOverlayStartedAtMs > 0L &&
            previousLoadingSpec?.debugSource == spec.debugSource &&
            nowMs - youtubeLoadingOverlayStartedAtMs >= YOUTUBE_LOADING_MAX_VISIBLE_MS
        ) {
            Log.d(TAG, "expire overlong youtube visual loading before redraw source=${spec.debugSource}")
            youtubeLoadingOverlayStartedAtMs = 0L
            youtubeLoadingSuppressedUntilMs = nowMs + YOUTUBE_LOADING_SUPPRESS_AFTER_MAX_MS
            lastYoutubeCommentPaneSpec = null
            provisionalAccessibilityMaskActive = false
            maskOverlayController.fadeOutAndClear(
                durationMs = YOUTUBE_SAFE_FADE_OUT_MS,
                reason = "youtube-loading-max-visible"
            )
            return -1L
        }
        lastYoutubeCommentPaneSpec = spec
        val beforeOverlayMs = SystemClock.uptimeMillis()
        if (!renderYoutubeCommentPanelSurface(spec, reason = "youtube-comment-panel-visual-loading")) {
            return -1L
        }
        if (
            youtubeLoadingOverlayStartedAtMs == 0L ||
            previousLoadingSpec?.debugSource != spec.debugSource ||
            !provisionalAccessibilityMaskActive
        ) {
            youtubeLoadingOverlayStartedAtMs = beforeOverlayMs
        }
        provisionalAccessibilityMaskActive = true
        riskGateActive = false
        scheduleYoutubeLoadingExpiry("visual-panel")
        val elapsedMs = SystemClock.uptimeMillis() - parseStartedAtMs
        Log.d(
            TAG,
            "youtube visual comment panel loading shown elapsedMs=$elapsedMs " +
                "drawLatencyMs=${SystemClock.uptimeMillis() - beforeOverlayMs} bounds=${commentPanelBounds.size}"
        )
        return elapsedMs
    }


    private fun buildStrictYoutubeVisualCommentPanelSpec(
        bounds: List<BoundsRect>,
        screenWidth: Int,
        screenHeight: Int,
        style: MaskOverlayStyle,
        label: String,
        debugSource: String
    ): MaskOverlaySpec? {
        if (screenWidth <= 0 || screenHeight <= 0 || bounds.isEmpty()) return null
        val usableBounds = bounds.mapNotNull { bound ->
            val left = bound.left.coerceIn(0, screenWidth)
            val top = bound.top.coerceIn(0, screenHeight)
            val right = bound.right.coerceIn(left, screenWidth)
            val bottom = bound.bottom.coerceIn(top, screenHeight)
            if (right - left < 80 || bottom - top < 40) {
                null
            } else {
                BoundsRect(left, top, right, bottom)
            }
        }
        if (usableBounds.isEmpty()) return null

        val minTop = usableBounds.minOf { it.top }
        val maxBottom = usableBounds.maxOf { it.bottom }
        val minAllowedTop = (screenHeight * 0.22f).toInt()
        if (minTop < minAllowedTop) {
            Log.d(TAG, "skip youtube visual loading: panel too high top=$minTop min=$minAllowedTop")
            return null
        }

        val minLeft = usableBounds.minOf { it.left }
        val maxRight = usableBounds.maxOf { it.right }
        val left = max(0, minLeft - 18)
        val top = max(0, minTop - 24)
        val right = min(screenWidth, maxRight + 18)
        val bottom = min(screenHeight, maxBottom + 28)
        val width = right - left
        val height = bottom - top
        if (width < 120 || height < 90) return null
        if (width >= (screenWidth * 0.96f).toInt() && height >= (screenHeight * 0.64f).toInt()) {
            Log.d(TAG, "skip youtube visual loading: panel too large width=$width height=$height")
            return null
        }

        return MaskOverlaySpec(
            left = left,
            top = top,
            width = width,
            height = height,
            label = label,
            allowScrollTranslation = false,
            debugSource = debugSource,
            style = style
        )
    }

    private fun renderYoutubeScrollLoadingGate(): Boolean {
        if (lastObservedPackage != YOUTUBE_PACKAGE) return false
        if (AnalysisSensitivityStore.get(applicationContext) <= 0) return false
        if (youtubeCommentInitialAnalysisCompleted) {
            Log.d(TAG, "skip youtube full-panel scroll loading: attached comment session active")
            return false
        }

        val nowMs = SystemClock.uptimeMillis()
        if (lastYoutubeCommentPanelConfirmedAtMs <= 0L) return false
        val panelAgeMs = nowMs - lastYoutubeCommentPanelConfirmedAtMs
        if (nowMs < youtubeLoadingSuppressedUntilMs) return false
        val spec = lastYoutubeScrollLoadingSpec ?: return false
        val beforeOverlayMs = SystemClock.uptimeMillis()
        if (!renderYoutubeCommentPanelSurface(spec, reason = "youtube-scroll-loading-gate")) {
            return false
        }

        lastYoutubeCommentPaneSpec = spec
        youtubeLoadingOverlayStartedAtMs = beforeOverlayMs
        provisionalAccessibilityMaskActive = true
        riskGateActive = false
        scheduleYoutubeLoadingExpiry("scroll-gate")
        Log.d(TAG, "youtube scroll loading gate shown panelAgeMs=$panelAgeMs")
        return true
    }

    private fun renderYoutubeNativeCommentPanelLoadingGate(
        event: AccessibilityEvent,
        packageName: String,
        eventTimeMs: Long,
        serviceReceivedAtMs: Long
    ): Long {
        if (packageName != YOUTUBE_PACKAGE) return -1L
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) return -1L
        if (AnalysisSensitivityStore.get(applicationContext) <= 0) return -1L

        val lookupStartedAtMs = SystemClock.uptimeMillis()
        val eventSource = event.source
        val root = rootInActiveWindow ?: eventSource ?: return -1L
        if (root.packageName?.toString() != YOUTUBE_PACKAGE) return -1L
        val sourcePanelNodes = eventSource?.let { source ->
            findYoutubeNativeCommentPanelNodes(source)
        }
        val markerNode = sourcePanelNodes?.first
            ?: findFirstNodeByViewId(
                root = root,
                viewId = YOUTUBE_COMMENT_SORT_MARKER_VIEW_ID
            )
            ?: findYoutubeCommentPanelHeaderCloseNode(root)
            ?: return -1L
        val contentNode = sourcePanelNodes?.second
            ?: findYoutubeCommentContentFromMarker(markerNode)
            ?: YOUTUBE_COMMENT_PANEL_CONTENT_VIEW_IDS
                .asSequence()
                .mapNotNull { viewId ->
                    findFirstNodeByViewId(root = root, viewId = viewId)
                }
                .firstOrNull()
            ?: return -1L
        val contentRect = Rect()
        val markerRect = Rect()
        contentNode.getBoundsInScreen(contentRect)
        markerNode.getBoundsInScreen(markerRect)
        val metrics = resources.displayMetrics
        val rawSpec = YoutubeSkeletonMaskBuilder.buildNativeCommentPaneSpec(
            contentBounds = BoundsRect(
                left = contentRect.left,
                top = contentRect.top,
                right = contentRect.right,
                bottom = contentRect.bottom
            ),
            commentMarkerBounds = BoundsRect(
                left = markerRect.left,
                top = markerRect.top,
                right = markerRect.right,
                bottom = markerRect.bottom
            ),
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels
        ) ?: return -1L
        val spec = alignYoutubePaneBelowHeader(
            root = root,
            spec = rawSpec,
            screenHeight = metrics.heightPixels
        )

        val confirmedAtMs = SystemClock.uptimeMillis()
        youtubeMirrorPanelNativeObserved = true
        lastYoutubeScrollLoadingSpec = spec
        lastYoutubeCommentPanelConfirmedAtMs = confirmedAtMs
        lastYoutubeNativeCommentPanelConfirmedAtMs = confirmedAtMs
        rememberYoutubeKnownCommentPanel(spec, confirmedAtMs)
        if (youtubeCommentInitialAnalysisCompleted) {
            Log.d(TAG, "refresh youtube native panel anchor without replacing attached comment masks")
            return 0L
        }

        val beforeOverlayMs = SystemClock.uptimeMillis()
        youtubeLoadingSuppressedUntilMs = 0L
        if (!renderYoutubeCommentPanelSurface(spec, reason = "youtube-native-comment-panel-gate")) {
            return -1L
        }
        val renderedAtMs = SystemClock.uptimeMillis()
        lastYoutubeCommentPaneSpec = spec
        lastYoutubeCommentPanelConfirmedAtMs = renderedAtMs
        lastYoutubeNativeCommentPanelConfirmedAtMs = renderedAtMs
        rememberYoutubeKnownCommentPanel(spec, renderedAtMs)
        if (
            youtubeLoadingOverlayStartedAtMs == 0L ||
            !provisionalAccessibilityMaskActive
        ) {
            youtubeLoadingOverlayStartedAtMs = beforeOverlayMs
        }
        provisionalAccessibilityMaskActive = true
        riskGateActive = false
        scheduleYoutubeLoadingExpiry("native-panel")

        val eventAgeMs = if (eventTimeMs > 0L) {
            (renderedAtMs - eventTimeMs).coerceAtLeast(0L)
        } else {
            -1L
        }
        Log.d(
            TAG,
            "youtube native comment loading shown lookupMs=${beforeOverlayMs - lookupStartedAtMs} " +
                "drawMs=${renderedAtMs - beforeOverlayMs} eventAgeMs=$eventAgeMs " +
                "receiveToMaskMs=${renderedAtMs - serviceReceivedAtMs} " +
                "sourceId=${eventSource?.viewIdResourceName.orEmpty()} " +
                "sourceClass=${eventSource?.className?.toString().orEmpty()} eventType=${event.eventType} " +
                "bounds=${spec.left},${spec.top},${spec.left + spec.width},${spec.top + spec.height}"
        )
        return if (eventTimeMs > 0L) eventAgeMs else renderedAtMs - serviceReceivedAtMs
    }

    private fun alignYoutubePaneBelowHeader(
        root: AccessibilityNodeInfo,
        spec: MaskOverlaySpec,
        screenHeight: Int
    ): MaskOverlaySpec {
        val closeNode = findYoutubeCommentPanelHeaderCloseNode(root) ?: return spec
        val closeRect = Rect().also(closeNode::getBoundsInScreen)
        val paneBottom = (spec.top + spec.height).coerceIn(0, screenHeight)
        val alignedTop = closeRect.bottom.coerceIn(0, paneBottom)
        if (paneBottom - alignedTop < 180) return spec
        if (alignedTop != spec.top) {
            Log.d(TAG, "align youtube mirror below header from=${spec.top} to=$alignedTop")
        }
        return spec.copy(
            top = alignedTop,
            height = paneBottom - alignedTop,
            allowScrollTranslation = false
        )
    }

    private fun findFirstNodeByViewId(
        root: AccessibilityNodeInfo,
        viewId: String,
        includeHidden: Boolean = false
    ): AccessibilityNodeInfo? {
        return runCatching {
            root.findAccessibilityNodeInfosByViewId(viewId)
                .firstOrNull { node -> includeHidden || node.isVisibleToUser }
        }.getOrNull()
    }


    private fun isYoutubeCommentPanelCloseClick(source: AccessibilityNodeInfo?): Boolean {
        val node = source ?: return false
        if (node.viewIdResourceName == YOUTUBE_COMMENT_PANEL_CLOSE_VIEW_ID) return true
        return listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString()
        ).any { label ->
            val normalized = label.trim().lowercase()
            normalized == "close" || normalized == "닫기"
        }
    }

    private fun dismissYoutubeCommentPanelFromMirror() {
        if (
            lastObservedPackage != YOUTUBE_PACKAGE ||
            !youtubeSafeCommentMirrorController.isActive
        ) {
            return
        }

        val nativePanelPresent = hasYoutubeCommentPanelStructureInAnyWindow()
        val closeActionHandled = performYoutubeCommentPanelCloseAction()
        val backActionHandled = if (!closeActionHandled && nativePanelPresent) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        } else {
            false
        }

        clearMaskOverlay()
        invalidateYoutubeCommentPanelSession("mirror-pull-down")
        Log.d(
            TAG,
            "dismiss youtube panel from mirror pull-down " +
                "panelPresent=$nativePanelPresent close=$closeActionHandled back=$backActionHandled"
        )
    }

    private fun performYoutubeCommentPanelCloseAction(): Boolean {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let(roots::add)
        windows?.forEach { window ->
            window.root?.let(roots::add)
        }

        for (root in roots.distinct()) {
            if (!isYoutubeAccessibilityRoot(root)) continue
            val closeNodes = runCatching {
                root.findAccessibilityNodeInfosByViewId(YOUTUBE_COMMENT_PANEL_CLOSE_VIEW_ID)
            }.getOrDefault(emptyList())
            for (closeNode in closeNodes) {
                var candidate: AccessibilityNodeInfo? = closeNode
                var parentDepth = 0
                while (candidate != null && parentDepth <= 4) {
                    if (
                        candidate.isClickable &&
                        runCatching {
                            candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        }.getOrDefault(false)
                    ) {
                        return true
                    }
                    candidate = runCatching { candidate.parent }.getOrNull()
                    parentDepth += 1
                }
            }
        }
        return false
    }


    private fun youtubeMirrorOpeningGraceRemainingMs(): Long {
        if (youtubeMirrorPanelNativeObserved) return 0L
        val startedAtMs = youtubeMirrorSessionStartedAtUptimeMs
        if (startedAtMs <= 0L) return 0L
        val elapsedMs = SystemClock.uptimeMillis() - startedAtMs
        return (YOUTUBE_MIRROR_PANEL_OPENING_GRACE_MS - elapsedMs).coerceAtLeast(0L)
    }

    private fun scheduleYoutubeMirrorPanelPresenceAudit(
        reason: String,
        delayMs: Long = YOUTUBE_MIRROR_PANEL_PRESENCE_DELAY_MS
    ) {
        if (!youtubeSafeCommentMirrorController.isActive) return
        val runId = youtubeMirrorSessionRunId
        if (youtubeMirrorPanelAuditScheduledRunId == runId) return
        youtubeMirrorPanelAuditScheduledRunId = runId
        handler.postDelayed(
            {
                if (youtubeMirrorPanelAuditScheduledRunId == runId) {
                    youtubeMirrorPanelAuditScheduledRunId = -1L
                }
                if (
                    runId != youtubeMirrorSessionRunId ||
                    !youtubeSafeCommentMirrorController.isActive ||
                    lastObservedPackage != YOUTUBE_PACKAGE
                ) {
                    return@postDelayed
                }
                if (hasVisibleYoutubeCommentPanelStructureInAnyWindow()) {
                    youtubeMirrorPanelNativeObserved = true
                    youtubeMirrorPanelMissingSinceMs = 0L
                    scheduleYoutubeMirrorPanelPresenceAudit(
                        reason = "heartbeat",
                        delayMs = YOUTUBE_MIRROR_PANEL_AUDIT_INTERVAL_MS
                    )
                    return@postDelayed
                }

                val openingGraceRemainingMs = youtubeMirrorOpeningGraceRemainingMs()
                if (openingGraceRemainingMs > 0L) {
                    scheduleYoutubeMirrorPanelPresenceAudit(
                        reason = "opening-grace:$reason",
                        delayMs = openingGraceRemainingMs
                    )
                    return@postDelayed
                }

                val nowMs = SystemClock.uptimeMillis()
                if (youtubeMirrorPanelMissingSinceMs == 0L) {
                    youtubeMirrorPanelMissingSinceMs = nowMs
                }
                val missingForMs = nowMs - youtubeMirrorPanelMissingSinceMs
                if (
                    !MaskOverlayEventPolicy.shouldRemoveYoutubeMirrorAfterPanelMiss(
                        panelPresent = false,
                        missingForMs = missingForMs,
                        missingGraceMs = YOUTUBE_MIRROR_PANEL_MISSING_GRACE_MS
                    )
                ) {
                    scheduleYoutubeMirrorPanelPresenceAudit(
                        reason = reason,
                        delayMs = YOUTUBE_MIRROR_PANEL_MISSING_GRACE_MS - missingForMs
                    )
                    return@postDelayed
                }

                Log.d(
                    TAG,
                    "remove youtube mirror after comment panel disappeared " +
                        "reason=$reason missingForMs=$missingForMs"
                )
                clearMaskOverlay()
                invalidateYoutubeCommentPanelSession("comment-panel-missing:$reason")
            },
            delayMs.coerceAtLeast(1L)
        )
    }

    private fun hasYoutubeCommentPanelStructureInAnyWindow(): Boolean {
        val includeHidden = YOUTUBE_SAFE_MIRROR_ENABLED &&
            youtubeSafeCommentMirrorController.isActive &&
            youtubeMirrorPanelSpec != null
        return hasYoutubeCommentPanelStructureInAnyWindow(includeHidden)
    }

    private fun hasVisibleYoutubeCommentPanelStructureInAnyWindow(): Boolean {
        return hasYoutubeCommentPanelStructureInAnyWindow(includeHidden = false)
    }

    private fun hasYoutubeCommentPanelStructureInAnyWindow(
        includeHidden: Boolean
    ): Boolean {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { root -> roots.add(root) }
        windows?.forEach { window ->
            window.root?.let { root -> roots.add(root) }
        }
        return roots
            .asSequence()
            .filter { root -> isYoutubeAccessibilityRoot(root) }
            .any { root -> hasYoutubeCommentPanelStructure(root, includeHidden) }
    }

    private fun hasYoutubeCommentPanelStructure(
        root: AccessibilityNodeInfo,
        includeHidden: Boolean
    ): Boolean {
        if (
            debugYoutubeHarnessActive &&
            root.packageName?.toString() == applicationContext.packageName
        ) {
            return true
        }
        if (
            findFirstNodeByViewId(
                root = root,
                viewId = YOUTUBE_COMMENT_RESULTS_VIEW_ID,
                includeHidden = includeHidden
            ) != null
        ) {
            return true
        }
        if (
            findFirstNodeByViewId(
                root = root,
                viewId = YOUTUBE_COMMENT_SORT_MARKER_VIEW_ID,
                includeHidden = includeHidden
            ) != null
        ) {
            return true
        }
        if (
            YOUTUBE_COMMENT_PANEL_CONTENT_VIEW_IDS.any { viewId ->
                findFirstNodeByViewId(
                    root = root,
                    viewId = viewId,
                    includeHidden = includeHidden
                ) != null
            }
        ) {
            return true
        }
        return findYoutubeCommentPanelHeaderCloseNode(root) != null
    }

    private fun youtubeMirrorEmptyStateMessage(): String? {
        if (youtubeSafeCommentBuffer.rawLineCount > 0) return null
        return when {
            youtubeMirrorParsedCommentCount > 0 && youtubeMirrorAnalysisError != null ->
                "댓글 분석 서버에 연결하지 못했습니다. 다시 시도해 주세요"
            else -> "댓글을 불러오지 못했습니다. 다시 열어주세요"
        }
    }
    private fun findYoutubeCommentPanelHeaderCloseNode(
        root: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {
        val closeNodes = (
            runCatching {
                root.findAccessibilityNodeInfosByViewId(YOUTUBE_COMMENT_PANEL_CLOSE_VIEW_ID)
            }.getOrDefault(emptyList()) +
                runCatching {
                    root.findAccessibilityNodeInfosByText("Close")
                }.getOrDefault(emptyList()) +
                runCatching {
                    root.findAccessibilityNodeInfosByText("닫기")
                }.getOrDefault(emptyList())
            ).filter { node -> node.isVisibleToUser }
        if (closeNodes.isEmpty()) return null

        val metrics = resources.displayMetrics
        val maximumVerticalGap = max(
            (96f * metrics.density).toInt(),
            (metrics.heightPixels * 0.08f).toInt()
        )
        fun matchingCloseForHeader(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (!node.isVisibleToUser) return null
            val labels = listOfNotNull(
                node.text?.toString(),
                node.contentDescription?.toString()
            ).map { value -> value.replace(Regex("\\s+"), " ").trim().lowercase() }
            val isCommentHeader = labels.any { label ->
                label == "comments" ||
                    label.startsWith("comments ") ||
                    label == "댓글" ||
                    label.startsWith("댓글 ")
            }
            if (!isCommentHeader) return null

            val headerRect = Rect().also(node::getBoundsInScreen)
            return closeNodes.firstOrNull { closeNode ->
                val closeRect = Rect().also(closeNode::getBoundsInScreen)
                !headerRect.isEmpty &&
                    !closeRect.isEmpty &&
                    headerRect.left < (metrics.widthPixels * 0.55f).toInt() &&
                    closeRect.left > (metrics.widthPixels * 0.62f).toInt() &&
                    abs(headerRect.centerY() - closeRect.centerY()) <= maximumVerticalGap
            }
        }

        val directTitleNodes = (
            runCatching {
                root.findAccessibilityNodeInfosByViewId(YOUTUBE_COMMENT_PANEL_TITLE_VIEW_ID)
            }.getOrDefault(emptyList()) +
                runCatching {
                    root.findAccessibilityNodeInfosByText("Comments")
                }.getOrDefault(emptyList()) +
                runCatching {
                    root.findAccessibilityNodeInfosByText("댓글")
                }.getOrDefault(emptyList())
            ).filter { node -> node.isVisibleToUser }
        for (titleNode in directTitleNodes) {
            matchingCloseForHeader(titleNode)?.let {
                Log.d(TAG, "youtube comment panel confirmed by header/title geometry")
                return it
            }
        }

        val pending = mutableListOf(root to 0)
        var visited = 0
        while (pending.isNotEmpty() && visited < 600) {
            val (node, depth) = pending.removeAt(pending.lastIndex)
            visited += 1
            matchingCloseForHeader(node)?.let { return it }
            if (depth >= 14) continue
            val childLimit = min(node.childCount, 16)
            for (index in childLimit - 1 downTo 0) {
                val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                pending += child to (depth + 1)
            }
        }
        Log.d(
            TAG,
            "youtube comment panel header probe miss closes=${closeNodes.size} " +
                "titles=${directTitleNodes.size}"
        )
        return null
    }

    private fun findYoutubeNativeCommentPanelNodes(
        source: AccessibilityNodeInfo
    ): Pair<AccessibilityNodeInfo, AccessibilityNodeInfo>? {
        val pending = mutableListOf(source to 0)
        var markerNode: AccessibilityNodeInfo? = null
        var contentNode: AccessibilityNodeInfo? = null
        var visited = 0
        while (pending.isNotEmpty() && visited < YOUTUBE_NATIVE_FAST_SCAN_MAX_NODES) {
            val (node, depth) = pending.removeAt(pending.lastIndex)
            visited += 1
            when (node.viewIdResourceName) {
                YOUTUBE_COMMENT_SORT_MARKER_VIEW_ID -> {
                    if (node.isVisibleToUser) markerNode = node
                }
                in YOUTUBE_COMMENT_PANEL_CONTENT_VIEW_IDS -> {
                    if (node.isVisibleToUser) contentNode = node
                }
            }
            val marker = markerNode
            val content = contentNode
            if (marker != null && content != null) return marker to content
            if (depth >= YOUTUBE_NATIVE_FAST_SCAN_MAX_DEPTH) continue

            val childLimit = min(node.childCount, 8)
            for (index in childLimit - 1 downTo 0) {
                val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                pending += child to (depth + 1)
            }
        }
        return null
    }
    private fun findYoutubeCommentContentFromMarker(
        markerNode: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = markerNode
        repeat(12) {
            val node = current ?: return null
            if (
                node.viewIdResourceName.orEmpty() in YOUTUBE_COMMENT_PANEL_CONTENT_VIEW_IDS &&
                node.isVisibleToUser
            ) {
                return node
            }
            val childLimit = min(node.childCount, 6)
            for (index in 0 until childLimit) {
                val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                if (
                    child.viewIdResourceName.orEmpty() in YOUTUBE_COMMENT_PANEL_CONTENT_VIEW_IDS &&
                    child.isVisibleToUser
                ) {
                    return child
                }
            }
            current = runCatching { node.parent }.getOrNull()
        }
        return null
    }

    private fun rememberYoutubeKnownCommentPanel(
        spec: MaskOverlaySpec,
        capturedAtMs: Long = SystemClock.uptimeMillis()
    ) {
        youtubeKnownCommentPanelSpec = spec.copy(
            label = "comments-loading",
            allowScrollTranslation = false,
            debugSource = "youtube-comment-pane-loading-known",
            style = MaskOverlayStyle.LOADING
        )
        youtubeKnownCommentPanelCapturedAtMs = capturedAtMs
    }

    private fun renderYoutubeCommentButtonLoadingGate(
        event: AccessibilityEvent,
        packageName: String,
        serviceReceivedAtMs: Long
    ): Long {
        val source = event.source ?: return -1L
        val cachedSpec = youtubeKnownCommentPanelSpec
        val nowMs = SystemClock.uptimeMillis()
        val cacheAgeMs = nowMs - youtubeKnownCommentPanelCapturedAtMs
        val sourceRect = Rect()
        source.getBoundsInScreen(sourceRect)
        val metrics = resources.displayMetrics
        val sourceLabel = buildString {
            append(event.contentDescription?.toString().orEmpty())
            append(' ')
            append(source.contentDescription?.toString().orEmpty())
            event.text.forEach { value ->
                append(' ')
                append(value?.toString().orEmpty())
            }
        }.trim()
        val labelLooksLikeComments = sourceLabel.contains("comment", ignoreCase = true) ||
            sourceLabel.contains("댓글")
        val sourceWidth = sourceRect.width()
        val sourceHeight = sourceRect.height()
        val isCompactTrailingAction =
            sourceRect.left >= (metrics.widthPixels * 0.65f).toInt() &&
                sourceRect.top >= (metrics.heightPixels * 0.25f).toInt() &&
                sourceWidth in 40..(metrics.widthPixels * 0.35f).toInt() &&
                sourceHeight in 40..(metrics.heightPixels * 0.20f).toInt()
        if (
            isYoutubeCompactCommentCardClick(
                source = source,
                sourceRect = sourceRect,
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels
            ) &&
            AnalysisSensitivityStore.get(applicationContext) > 0
        ) {
            val cachedGateSpec = cachedSpec?.takeIf {
                cacheAgeMs in 0..YOUTUBE_RESUME_LOADING_CACHE_TTL_MS
            }
            val gateSpec = cachedGateSpec
                ?: YoutubeSkeletonMaskBuilder.buildCommentPreviewLoadingSpec(
                    actionBounds = BoundsRect(
                        left = sourceRect.left,
                        top = sourceRect.top,
                        right = sourceRect.right,
                        bottom = sourceRect.bottom
                    ),
                    screenWidth = metrics.widthPixels,
                    screenHeight = metrics.heightPixels
                )
                ?: return -1L

            youtubeCommentInitialAnalysisCompleted = false
            lastYoutubeBlockedSpecs = emptyList()
            clearYoutubeHarmfulCommentAnchor()
            val spec = gateSpec.copy(
                debugSource = if (cachedGateSpec != null) {
                    "youtube-comment-button-loading"
                } else {
                    "youtube-comment-preview-loading"
                }
            )
            youtubeLoadingSuppressedUntilMs = 0L
            if (!renderYoutubeCommentPanelSurface(spec, reason = "youtube-comment-button-loading-gate")) {
                return -1L
            }
            val renderedAtMs = SystemClock.uptimeMillis()
            lastYoutubeCommentPaneSpec = spec
            if (cachedGateSpec != null) {
                lastYoutubeScrollLoadingSpec = spec
                lastYoutubeCommentPanelConfirmedAtMs = renderedAtMs
            } else {
                lastYoutubeScrollLoadingSpec = null
            }
            youtubeLoadingOverlayStartedAtMs = renderedAtMs
            provisionalAccessibilityMaskActive = true
            riskGateActive = false
            scheduleYoutubeLoadingExpiry("comment-card")
            Log.d(
                TAG,
                "youtube comment card loading shown receiveToMaskMs=${renderedAtMs - serviceReceivedAtMs} " +
                    "cache=${cachedGateSpec != null} sourceBounds=${sourceRect.left},${sourceRect.top}," +
                    "${sourceRect.right},${sourceRect.bottom}"
            )
            return renderedAtMs - serviceReceivedAtMs
        }
        if (youtubeCommentInitialAnalysisCompleted) return -1L
        val shouldPrime = MaskOverlayEventPolicy.shouldPrimeYoutubeLoadingForCommentButtonClick(
            eventType = event.eventType,
            isYoutubePackage = packageName == YOUTUBE_PACKAGE,
            hasCachedCommentPanel = cachedSpec != null,
            isCacheFresh = cacheAgeMs in 0..YOUTUBE_RESUME_LOADING_CACHE_TTL_MS,
            labelLooksLikeComments = labelLooksLikeComments,
            isCompactTrailingAction = isCompactTrailingAction
        )
        if (!shouldPrime || cachedSpec == null) return -1L
        if (AnalysisSensitivityStore.get(applicationContext) <= 0) return -1L

        youtubeCommentInitialAnalysisCompleted = false
        lastYoutubeBlockedSpecs = emptyList()
        val spec = cachedSpec.copy(debugSource = "youtube-comment-button-loading")
        youtubeLoadingSuppressedUntilMs = 0L
        if (!renderYoutubeCommentPanelSurface(spec, reason = "youtube-comment-button-loading-gate")) {
            return -1L
        }
        val renderedAtMs = SystemClock.uptimeMillis()
        lastYoutubeScrollLoadingSpec = spec
        lastYoutubeCommentPaneSpec = spec
        lastYoutubeCommentPanelConfirmedAtMs = renderedAtMs
        youtubeLoadingOverlayStartedAtMs = renderedAtMs
        provisionalAccessibilityMaskActive = true
        riskGateActive = false
        scheduleYoutubeLoadingExpiry("comment-button")
        val receiveToMaskMs = renderedAtMs - serviceReceivedAtMs
        Log.d(
            TAG,
            "youtube comment button loading shown receiveToMaskMs=$receiveToMaskMs " +
                "cacheAgeMs=$cacheAgeMs label=$sourceLabel " +
                "sourceBounds=${sourceRect.left},${sourceRect.top},${sourceRect.right},${sourceRect.bottom}"
        )
        return receiveToMaskMs
    }
    private fun isYoutubeCompactCommentCardClick(
        source: AccessibilityNodeInfo,
        sourceRect: Rect,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        if (screenWidth <= 0 || screenHeight <= 0) return false
        var current: AccessibilityNodeInfo? = source
        repeat(7) { depth ->
            val node = current ?: return false
            val nodeRect = if (depth == 0) {
                sourceRect
            } else {
                Rect().also(node::getBoundsInScreen)
            }
            val looksLikeWideCommentCard =
                nodeRect.width() >= (screenWidth * 0.72f).toInt() &&
                    nodeRect.height() in 48..(screenHeight * 0.18f).toInt() &&
                    nodeRect.top in
                        (screenHeight * 0.24f).toInt()..(screenHeight * 0.82f).toInt()
            if (looksLikeWideCommentCard && youtubeNodeContainsCommentComposer(node)) return true
            current = runCatching { node.parent }.getOrNull()
        }
        return false
    }

    private fun youtubeNodeContainsCommentComposer(root: AccessibilityNodeInfo): Boolean {
        val pending = mutableListOf(root to 0)
        var visited = 0
        while (pending.isNotEmpty() && visited < 64) {
            val (node, depth) = pending.removeAt(pending.lastIndex)
            visited += 1
            val labels = listOfNotNull(
                node.contentDescription?.toString(),
                node.text?.toString()
            ).map { value -> value.trim().lowercase() }
            val viewId = node.viewIdResourceName.orEmpty().lowercase()
            if (
                labels.any { label ->
                    label == "comment..." ||
                        label.contains("add a comment") ||
                        label.contains("\uB313\uAE00")
                } ||
                viewId.contains("comment_composer")
            ) {
                return true
            }
            if (depth >= 6) continue
            for (index in 0 until node.childCount) {
                val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                pending += child to (depth + 1)
            }
        }
        return false
    }
    private fun hasFreshYoutubeCommentPanelConfirmation(
        nowMs: Long = SystemClock.uptimeMillis()
    ): Boolean {
        return lastYoutubeCommentPanelConfirmedAtMs > 0L &&
            nowMs - lastYoutubeCommentPanelConfirmedAtMs in 0L..YOUTUBE_NATIVE_PANEL_CONFIRMATION_TTL_MS
    }
    private fun hasFreshNativeYoutubeCommentPanel(nowMs: Long = SystemClock.uptimeMillis()): Boolean {
        return lastYoutubeNativeCommentPanelConfirmedAtMs > 0L &&
            nowMs - lastYoutubeNativeCommentPanelConfirmedAtMs in
                0L..YOUTUBE_NATIVE_PANEL_CONFIRMATION_TTL_MS &&
            lastYoutubeScrollLoadingSpec?.debugSource == "youtube-comment-pane-loading-native"
    }
    private fun renderYoutubeResumeLoadingGateForLaunchClick(
        event: AccessibilityEvent,
        packageName: String
    ): Boolean {
        val cachedSpec = youtubeResumeLoadingSpec
        val nowMs = SystemClock.uptimeMillis()
        val cacheAgeMs = nowMs - youtubeResumeLoadingCapturedAtMs
        val isYoutubeTarget = event.text.any { label ->
            label?.toString()?.trim()?.equals("YouTube", ignoreCase = true) == true
        } || event.contentDescription?.toString()?.trim()?.equals("YouTube", ignoreCase = true) == true
        val shouldPrime = MaskOverlayEventPolicy.shouldPrimeYoutubeLoadingForLaunchClick(
            eventType = event.eventType,
            isTrustedLauncherPackage = packageName in OVERLAY_EXIT_PACKAGES ||
                packageName == "com.android.systemui",
            hasCachedCommentPanel = cachedSpec != null,
            isCacheFresh = cacheAgeMs in 0..YOUTUBE_RESUME_LOADING_CACHE_TTL_MS,
            isYoutubeLaunchTarget = isYoutubeTarget
        )
        if (!shouldPrime || cachedSpec == null) return false
        if (AnalysisSensitivityStore.get(applicationContext) <= 0) return false

        youtubeLoadingSuppressedUntilMs = 0L
        if (!maskOverlayController.renderDirect(listOf(cachedSpec), reason = "youtube-launch-click-loading-gate")) {
            return false
        }
        lastYoutubeCommentPaneSpec = cachedSpec
        youtubeLoadingOverlayStartedAtMs = nowMs
        provisionalAccessibilityMaskActive = true
        riskGateActive = false
        scheduleYoutubeLoadingExpiry("launch-click-gate")
        Log.d(TAG, "prime youtube loading before launch cacheAgeMs=$cacheAgeMs package=$packageName")
        return true
    }

    private fun rememberYoutubeResumeLoadingGate() {
        val spec = lastYoutubeScrollLoadingSpec ?: return
        val windowClassName = lastYoutubeWindowClassName ?: return
        youtubeResumeLoadingSpec = spec.copy(
            label = "검열중",
            allowScrollTranslation = false,
            debugSource = "youtube-resume-loading",
            style = MaskOverlayStyle.LOADING
        )
        youtubeResumeLoadingCapturedAtMs = SystemClock.uptimeMillis()
        youtubeResumeWindowClassName = windowClassName
        Log.d(TAG, "remember youtube loading gate for foreground resume class=$windowClassName")
    }

    private fun restoreYoutubeResumeLoadingGateIfEligible(
        event: AccessibilityEvent,
        wasYoutubeObserved: Boolean
    ): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return false

        val cachedSpec = youtubeResumeLoadingSpec
        val nowMs = SystemClock.uptimeMillis()
        val cacheAgeMs = nowMs - youtubeResumeLoadingCapturedAtMs
        val eventWindowClassName = event.className?.toString()
        val shouldRestore = MaskOverlayEventPolicy.shouldRestoreYoutubeLoadingOnForeground(
            eventType = event.eventType,
            isYoutubePackage = lastObservedPackage == YOUTUBE_PACKAGE,
            wasYoutubeObserved = wasYoutubeObserved,
            hasCachedCommentPanel = cachedSpec != null,
            isCacheFresh = cacheAgeMs in 0..YOUTUBE_RESUME_LOADING_CACHE_TTL_MS,
            windowClassMatches = eventWindowClassName != null &&
                eventWindowClassName == youtubeResumeWindowClassName
        )
        youtubeResumeLoadingSpec = null
        youtubeResumeLoadingCapturedAtMs = 0L
        youtubeResumeWindowClassName = null
        if (!shouldRestore || cachedSpec == null) return false
        if (AnalysisSensitivityStore.get(applicationContext) <= 0) return false

        youtubeLoadingSuppressedUntilMs = 0L
        lastYoutubeScrollLoadingSpec = cachedSpec
        lastYoutubeCommentPanelConfirmedAtMs = nowMs
        lastScrollEventAtMs = nowMs
        if (!maskOverlayController.renderDirect(listOf(cachedSpec), reason = "youtube-resume-loading-gate")) {
            return false
        }

        lastYoutubeCommentPaneSpec = cachedSpec
        youtubeLoadingOverlayStartedAtMs = nowMs
        provisionalAccessibilityMaskActive = true
        riskGateActive = false
        scheduleYoutubeLoadingExpiry("resume-gate")
        Log.d(
            TAG,
            "restore youtube loading gate on foreground cacheAgeMs=$cacheAgeMs class=$eventWindowClassName"
        )
        return true
    }

    private fun invalidateYoutubeCommentPanelSession(reason: String) {
        val hadActiveCommentPanelSession =
            youtubeSafeCommentMirrorController.isActive ||
                lastYoutubeScrollLoadingSpec != null ||
                lastYoutubeCommentPanelConfirmedAtMs > 0L
        if (hadActiveCommentPanelSession) {
            youtubeMirrorReopenSuppressedUntilMs = max(
                youtubeMirrorReopenSuppressedUntilMs,
                SystemClock.uptimeMillis() + YOUTUBE_MIRROR_REOPEN_GUARD_MS
            )
        }
        if (lastYoutubeScrollLoadingSpec != null || lastYoutubeCommentPanelConfirmedAtMs > 0L) {
            Log.d(TAG, "invalidate youtube comment panel session reason=$reason")
        }
        lastYoutubeScrollLoadingSpec = null
        lastYoutubeCommentPanelConfirmedAtMs = 0L
        lastYoutubeNativeCommentPanelConfirmedAtMs = 0L
        lastYoutubeBlockedSpecs = emptyList()
        youtubeCommentInitialAnalysisCompleted = false
        stopYoutubeHarmfulAnchorTracking()
        clearYoutubeHarmfulCommentAnchor()
        youtubeTouchInteractionActive = false
        resetYoutubeMirrorSession(reason)
    }

    private fun hasActiveYoutubeLoadingGate(): Boolean {
        if (YOUTUBE_SAFE_MIRROR_ENABLED && youtubeSafeCommentMirrorController.isActive) {
            return !youtubeSafeCommentMirrorController.isReady
        }
        return lastYoutubeCommentPaneSpec?.style == MaskOverlayStyle.LOADING &&
            lastYoutubeCommentPaneSpec?.allowScrollTranslation == false &&
            provisionalAccessibilityMaskActive &&
            maskOverlayController.hasActiveMasks()
    }

    private fun isYoutubeScrollLoadingHoldActive(): Boolean {
        if (lastObservedPackage != YOUTUBE_PACKAGE || lastScrollEventAtMs <= 0L) return false
        val elapsedMs = SystemClock.uptimeMillis() - lastScrollEventAtMs
        return elapsedMs in 0..YOUTUBE_SCROLL_LOADING_HOLD_MS
    }

    private fun restoreYoutubeCachedHarmfulMasks(reason: String): Boolean {
        if (AnalysisSensitivityStore.get(applicationContext) <= 0) return false
        if (
            youtubeCommentInitialAnalysisCompleted &&
            youtubeKnownHarmfulComments.isNotEmpty()
        ) {
            return refreshYoutubeReplyAnchoredMasks(reason = reason, allowRebind = true)
        }
        val specs = lastYoutubeBlockedSpecs
        if (specs.isEmpty()) return false
        if (!maskOverlayController.renderDirect(specs, reason = reason)) return false

        lastYoutubeCommentPaneSpec = null
        youtubeLoadingOverlayStartedAtMs = 0L
        provisionalAccessibilityMaskActive = false
        riskGateActive = false
        return true
    }

    private fun expireYoutubeLoadingOverlay(
        reason: String,
        nowMs: Long,
        suppressFurtherLoading: Boolean
    ) {
        youtubeLoadingOverlayStartedAtMs = 0L
        if (suppressFurtherLoading) {
            youtubeLoadingSuppressedUntilMs = nowMs + YOUTUBE_LOADING_SUPPRESS_AFTER_MAX_MS
        }
        lastYoutubeCommentPaneSpec = null
        provisionalAccessibilityMaskActive = false
        if (!restoreYoutubeCachedHarmfulMasks("youtube-loading-expire-blocked-cache:$reason")) {
            maskOverlayController.fadeOutAndClear(
                durationMs = YOUTUBE_SAFE_FADE_OUT_MS,
                reason = "youtube-loading-expire:$reason"
            )
        }
    }
    private fun scheduleYoutubeLoadingExpiry(reason: String) {
        if (YOUTUBE_SAFE_MIRROR_ENABLED && youtubeSafeCommentMirrorController.isActive) {
            return
        }
        val expectedSource = lastYoutubeCommentPaneSpec?.debugSource ?: return
        handler.postDelayed(
            {
                val activePane = lastYoutubeCommentPaneSpec
                val isLaunchGate = expectedSource == "youtube-resume-loading"
                if (
                    (lastObservedPackage != YOUTUBE_PACKAGE && !isLaunchGate) ||
                    activePane?.style != MaskOverlayStyle.LOADING ||
                    activePane.debugSource != expectedSource ||
                    !maskOverlayController.hasActiveMasks()
                ) {
                    return@postDelayed
                }

                val nowMs = SystemClock.uptimeMillis()
                val visibleForMs = if (youtubeLoadingOverlayStartedAtMs > 0L) {
                    nowMs - youtubeLoadingOverlayStartedAtMs
                } else {
                    YOUTUBE_LOADING_MAX_VISIBLE_MS
                }
                if (
                    !youtubeCommentInitialAnalysisCompleted &&
                    (
                        analysisInFlight ||
                            visualAnalysisInFlight ||
                            hasFreshYoutubeCommentPanelConfirmation(nowMs) ||
                            hasFreshNativeYoutubeCommentPanel(nowMs)
                        )
                ) {
                    Log.d(
                        TAG,
                        "keep initial youtube loading until analysis completes reason=$reason visibleMs=$visibleForMs"
                    )
                    scheduleYoutubeLoadingExpiry(reason)
                    return@postDelayed
                }
                if (visibleForMs >= YOUTUBE_LOADING_MAX_VISIBLE_MS) {
                    Log.d(
                        TAG,
                        "expire max-visible youtube loading overlay reason=$reason " +
                            "source=$expectedSource visibleMs=$visibleForMs"
                    )
                    expireYoutubeLoadingOverlay(
                        reason = "max-visible:$reason",
                        nowMs = nowMs,
                        suppressFurtherLoading = true
                    )
                    return@postDelayed
                }

                if (
                    analysisInFlight ||
                    visualAnalysisInFlight ||
                    isYoutubeScrollLoadingHoldActive()
                ) {
                    Log.d(
                        TAG,
                        "keep youtube loading overlay while analysis active reason=$reason " +
                            "backend=$analysisInFlight visual=$visualAnalysisInFlight visibleMs=$visibleForMs"
                    )
                    scheduleYoutubeLoadingExpiry(reason)
                    return@postDelayed
                }

                Log.d(TAG, "expire idle youtube loading overlay reason=$reason source=$expectedSource")
                expireYoutubeLoadingOverlay(
                    reason = "idle:$reason",
                    nowMs = nowMs,
                    suppressFurtherLoading = false
                )
            },
            YOUTUBE_LOADING_MAX_VISIBLE_MS
        )
    }
    private fun hasConfirmedYoutubeCommentPanel(visualRoiPlan: VisualTextRoiPlan?): Boolean {
        if (visualRoiPlan?.let { youtubeCommentPanelBounds(it).isNotEmpty() } != true) {
            return false
        }
        if (debugYoutubeHarnessActive) return true
        if (YOUTUBE_SAFE_MIRROR_ENABLED && youtubeSafeCommentMirrorController.isActive) {
            return true
        }
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() != YOUTUBE_PACKAGE) return false
        return findFirstNodeByViewId(
            root = root,
            viewId = YOUTUBE_COMMENT_SORT_MARKER_VIEW_ID
        ) != null || findYoutubeCommentPanelHeaderCloseNode(root) != null
    }

    private fun youtubeCommentPanelBounds(visualRoiPlan: VisualTextRoiPlan): List<BoundsRect> {
        val explicitBounds = visualRoiPlan.rois
            .filter { roi -> roi.source == "youtube-comment-panel" }
            .map { roi -> roi.boundsInScreen }
        if (explicitBounds.isNotEmpty() || !debugYoutubeHarnessActive) {
            return explicitBounds
        }

        val scrollNode = findDebugYoutubeHarnessScrollNode() ?: return emptyList()
        youtubeMirrorNativeScrollNode = scrollNode
        val rect = Rect().also { scrollNode.getBoundsInScreen(it) }
        return if (rect.width() >= 240 && rect.height() >= 240) {
            listOf(BoundsRect(rect.left, rect.top, rect.right, rect.bottom))
        } else {
            emptyList()
        }
    }

    private fun renderProvisionalAccessibilityMaskOverlay(
        packageName: String,
        screenCandidates: List<ScreenTextCandidate>,
        candidateRouteSamples: List<String>,
        visualRoiPlan: VisualTextRoiPlan,
        snapshotOverlayRevision: Long,
        timestamp: Long,
        parseStartedAtMs: Long,
        parseDelayMs: Long,
        candidateExtractionMs: Long,
        nodeCollectionMs: Long = -1L,
        candidatePostProcessingMs: Long = -1L,
        experimentMode: PipelineExperimentMode = currentExperimentMode(),
        nodes: List<ParsedTextNode> = emptyList(),
        candidateComputation: ParseCandidateComputation? = null
    ): Long {
        val response = ProvisionalAccessibilityMaskBuilder.buildResponse(
            candidates = screenCandidates,
            timestamp = timestamp
        ) ?: return -1L

        val analysis = AndroidAnalysisAttempt(
            ok = true,
            packageName = packageName,
            url = "accessibility-provisional",
            sensitivity = AnalysisSensitivityStore.get(applicationContext),
            latencyMs = 0L,
            parseDelayMs = parseDelayMs,
            candidateExtractionMs = candidateExtractionMs,
            nodeCollectionMs = nodeCollectionMs,
            visualRoiPlanningMs = candidateComputation?.visualRoiPlanningMs ?: -1L,
            screenCandidateExtractionMs = candidateComputation?.screenCandidateExtractionMs ?: -1L,
            candidatePostProcessingMs = candidatePostProcessingMs,
            accessibilityMaskLatencyMs = SystemClock.uptimeMillis() - parseStartedAtMs,
            riskGateMaskMs = recentRiskGateMaskMs(parseStartedAtMs),
            riskGateEventAgeMs = recentRiskGateEventAgeMs(parseStartedAtMs),
            riskGateReceiveToMaskMs = recentRiskGateReceiveToMaskMs(parseStartedAtMs),
            fastProvisionalMaskMs = recentFastProvisionalMaskMs(parseStartedAtMs),
            commentCount = response.results.size,
            offensiveCount = response.results.size,
            filteredCount = response.filteredCount,
            response = response,
            candidateRouteSamples = candidateRouteSamples
        )
            .withPipelineDiagnostics(
                experimentMode = experimentMode,
                nodeCount = nodes.size,
                screenCandidateCount = screenCandidates.size,
                charLocationNodeCount = nodes.count { node -> node.charBoxes.isNotEmpty() },
                charRangeCandidateCount = screenCandidates.count { candidate ->
                    candidate.backendSourceId.orEmpty().startsWith("android-accessibility-char-range:")
                },
                candidateParallelWaitMs = candidateComputation?.parallelWaitMs ?: -1L,
                nodeCollectionMs = nodeCollectionMs,
                visualRoiPlanningMs = candidateComputation?.visualRoiPlanningMs ?: -1L,
                screenCandidateExtractionMs = candidateComputation?.screenCandidateExtractionMs ?: -1L,
                candidatePostProcessingMs = candidatePostProcessingMs
            )
            .withOverlayDiagnostics(packageName, visualRoiPlan)

        Log.d(TAG, "render provisional accessibility masks count=${response.results.size}")
        updateMaskOverlay(
            currentPackage = packageName,
            analysis = analysis,
            snapshotOverlayRevision = snapshotOverlayRevision,
            visualRoiPlan = visualRoiPlan,
            isProvisionalAccessibilityMask = true,
            allowDuringScrollStabilization = !usesViewportStableBrowserOverlay(packageName),
            preserveExistingPreciseVisualMasks = true
        )
        return SystemClock.uptimeMillis() - parseStartedAtMs
    }

    private fun renderRiskGateForEvent(
        event: AccessibilityEvent,
        packageName: String,
        eventTimeMs: Long,
        serviceReceivedAtMs: Long
    ): Long {
        if (!supportsRiskGatePackage(packageName)) return -1L
        if (
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            usesViewportStableBrowserOverlay(packageName)
        ) {
            return -1L
        }
        if (AnalysisSensitivityStore.get(applicationContext) < RISK_GATE_MIN_SENSITIVITY) return -1L

        val nowMs = SystemClock.uptimeMillis()
        if (nowMs - lastRiskGateAtMs in 0L until RISK_GATE_MIN_INTERVAL_MS) return -1L

        val specs = buildRiskGateSpecs(event, packageName)
        if (specs.isEmpty()) return -1L

        val beforeOverlayMs = SystemClock.uptimeMillis()
        if (!maskOverlayController.renderDirect(specs, reason = "risk-gate")) return -1L

        val afterOverlayMs = SystemClock.uptimeMillis()
        val eventAgeMs = if (eventTimeMs > 0L) {
            (beforeOverlayMs - eventTimeMs).coerceAtLeast(0L)
        } else {
            -1L
        }
        val receiveToMaskMs = afterOverlayMs - serviceReceivedAtMs
        val elapsedMs = if (eventTimeMs > 0L) {
            afterOverlayMs - eventTimeMs
        } else {
            receiveToMaskMs
        }
        rememberRiskGateMask(
            elapsedMs = elapsedMs,
            eventAgeMs = eventAgeMs,
            receiveToMaskMs = receiveToMaskMs
        )

        provisionalAccessibilityMaskActive = true
        riskGateActive = true
        riskGateRevision += 1L
        val snapshotRiskGateRevision = riskGateRevision
        handler.postDelayed(
            {
                clearRiskGateOverlayIfCurrent(snapshotRiskGateRevision)
            },
            RISK_GATE_TTL_MS
        )

        AnalysisDiagnosticsStore.saveAttempt(
            applicationContext,
            AndroidAnalysisAttempt(
                ok = true,
                packageName = packageName,
                url = "risk-gate-provisional",
                sensitivity = AnalysisSensitivityStore.get(applicationContext),
                latencyMs = 0L,
                parseDelayMs = 0L,
                accessibilityMaskLatencyMs = elapsedMs,
                riskGateMaskMs = elapsedMs,
                riskGateEventAgeMs = eventAgeMs,
                riskGateReceiveToMaskMs = receiveToMaskMs,
                commentCount = specs.size,
                offensiveCount = specs.size,
                filteredCount = 0,
                overlayCandidateCount = specs.size,
                overlayRenderedCount = specs.size,
                overlayRenderedSamples = specs.mapNotNull { spec ->
                    spec.debugSource.takeIf { it.isNotBlank() }
                },
                visualCaptureSupported = visualCaptureState.supported,
                visualCaptureReason = visualCaptureState.reason,
                candidateRouteSamples = listOf("risk-gate")
            ).withPipelineDiagnostics(
                experimentMode = currentExperimentMode(),
                nodeCount = 0,
                screenCandidateCount = 0,
                charLocationNodeCount = 0,
                charRangeCandidateCount = 0,
                candidateParallelWaitMs = -1L
            )
        )

        Log.d(
            TAG,
            "risk gate mask specs=${specs.size} elapsedMs=$elapsedMs eventAgeMs=$eventAgeMs " +
                "receiveToMaskMs=$receiveToMaskMs eventType=${event.eventType}"
        )
        return elapsedMs
    }

    private fun buildRiskGateSpecs(
        event: AccessibilityEvent,
        packageName: String
    ): List<MaskOverlaySpec> {
        val sourceText = event.text
            .joinToString(" ") { value -> value?.toString().orEmpty() }
            .replace(FAST_PROVISIONAL_WHITESPACE_PATTERN, " ")
            .trim()
        val hintedSourceSpec = if (sourceText.isNotBlank() && mayContainFastProvisionalHit(sourceText)) {
            buildRiskGateSourceSpec(event, packageName)
        } else {
            null
        }
        if (hintedSourceSpec != null) return listOf(hintedSourceSpec)
        if (usesViewportStableBrowserOverlay(packageName)) return emptyList()

        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        if (screenWidth <= 0 || screenHeight <= 0) return emptyList()

        val sideMargin = (screenWidth * RISK_GATE_SIDE_MARGIN_RATIO).toInt()
        val left = sideMargin.coerceAtLeast(0)
        val width = (screenWidth - sideMargin * 2).coerceAtLeast(RISK_GATE_MIN_WIDTH_PX)
        val topRatio: Float
        val heightRatio: Float
        when (packageName) {
            CHROME_PACKAGE -> {
                topRatio = 0.11f
                heightRatio = 0.44f
            }
            YOUTUBE_PACKAGE -> {
                topRatio = 0.34f
                heightRatio = 0.36f
            }
            INSTAGRAM_PACKAGE,
            TIKTOK_PACKAGE,
            TIKTOK_ALT_PACKAGE -> {
                topRatio = 0.56f
                heightRatio = 0.30f
            }
            else -> return emptyList()
        }

        val top = (screenHeight * topRatio).toInt()
        val height = (screenHeight * heightRatio).toInt()
            .coerceAtLeast(RISK_GATE_MIN_HEIGHT_PX)
        return listOf(
            constrainedRiskGateSpec(
                left = left,
                top = top,
                right = left + width,
                bottom = top + height,
                source = "risk-gate-band:$packageName:${event.eventType}"
            )
        )
    }

    private fun buildRiskGateSourceSpec(
        event: AccessibilityEvent,
        packageName: String
    ): MaskOverlaySpec? {
        val source = event.source ?: return null
        if (!source.isVisibleToUser) return null
        val rect = Rect().also { source.getBoundsInScreen(it) }
        if (rect.width() < RISK_GATE_MIN_WIDTH_PX || rect.height() < RISK_GATE_MIN_HEIGHT_PX) {
            return null
        }
        val maxHeight = (resources.displayMetrics.heightPixels * RISK_GATE_MAX_SOURCE_HEIGHT_RATIO).toInt()
        if (rect.height() > maxHeight) return null
        return constrainedRiskGateSpec(
            left = rect.left - RISK_GATE_SOURCE_PADDING_PX,
            top = rect.top - RISK_GATE_SOURCE_PADDING_PX,
            right = rect.right + RISK_GATE_SOURCE_PADDING_PX,
            bottom = rect.bottom + RISK_GATE_SOURCE_PADDING_PX,
            source = "risk-gate-source:$packageName:${event.eventType}"
        )
    }

    private fun constrainedRiskGateSpec(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        source: String
    ): MaskOverlaySpec {
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels.coerceAtLeast(1)
        val screenHeight = metrics.heightPixels.coerceAtLeast(1)
        val safeLeft = left.coerceIn(0, screenWidth - 1)
        val safeTop = top.coerceIn(0, screenHeight - 1)
        val safeRight = right.coerceIn(safeLeft + 1, screenWidth)
        val safeBottom = bottom.coerceIn(safeTop + 1, screenHeight)
        return MaskOverlaySpec(
            left = safeLeft,
            top = safeTop,
            width = (safeRight - safeLeft).coerceAtLeast(1),
            height = (safeBottom - safeTop).coerceAtLeast(1),
            label = "Risk gate",
            allowScrollTranslation = false,
            debugSource = source
        )
    }

    private fun supportsRiskGatePackage(packageName: String): Boolean {
        return packageName == CHROME_PACKAGE ||
            packageName == INSTAGRAM_PACKAGE ||
            packageName == TIKTOK_PACKAGE ||
            packageName == TIKTOK_ALT_PACKAGE
    }

    private fun clearRiskGateOverlayIfCurrent(snapshotRiskGateRevision: Long) {
        if (!riskGateActive || riskGateRevision != snapshotRiskGateRevision) return
        riskGateActive = false
        provisionalAccessibilityMaskActive = false
        Log.d(TAG, "clear expired risk gate overlay")
        maskOverlayController.clear()
        resetAbsoluteScrollPosition()
    }

    private fun rememberRiskGateMask(
        elapsedMs: Long,
        eventAgeMs: Long,
        receiveToMaskMs: Long
    ) {
        if (elapsedMs < 0L) return
        lastRiskGateMaskMs = elapsedMs
        lastRiskGateEventAgeMs = eventAgeMs
        lastRiskGateReceiveToMaskMs = receiveToMaskMs
        lastRiskGateAtMs = SystemClock.uptimeMillis()
    }

    private fun recentRiskGateMaskMs(parseStartedAtMs: Long): Long {
        return recentRiskGateValue(parseStartedAtMs, lastRiskGateMaskMs)
    }

    private fun recentRiskGateEventAgeMs(parseStartedAtMs: Long): Long {
        return recentRiskGateValue(parseStartedAtMs, lastRiskGateEventAgeMs)
    }

    private fun recentRiskGateReceiveToMaskMs(parseStartedAtMs: Long): Long {
        return recentRiskGateValue(parseStartedAtMs, lastRiskGateReceiveToMaskMs)
    }

    private fun recentRiskGateValue(parseStartedAtMs: Long, value: Long): Long {
        val latencyMs = lastRiskGateMaskMs
        val renderedAtMs = lastRiskGateAtMs
        if (latencyMs < 0L || value < 0L || renderedAtMs <= 0L) return -1L

        val nowMs = SystemClock.uptimeMillis()
        val ageMs = nowMs - renderedAtMs
        val offsetFromParseStartMs = renderedAtMs - parseStartedAtMs
        return if (ageMs in 0L..5_000L && offsetFromParseStartMs >= -1_500L) {
            value
        } else {
            -1L
        }
    }

    private fun renderFastProvisionalMaskFromEventSource(
        event: AccessibilityEvent,
        packageName: String,
        eventTimeMs: Long,
        serviceReceivedAtMs: Long
    ): Long {
        if (packageName == YOUTUBE_PACKAGE) return -1L
        if (!supportsMaskOverlay(packageName)) return -1L
        if (AnalysisSensitivityStore.get(applicationContext) <= 0) return -1L
        if (
            event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            return -1L
        }

        val startedAtMs = SystemClock.uptimeMillis()
        val eventAgeMs = if (eventTimeMs > 0L) {
            (startedAtMs - eventTimeMs).coerceAtLeast(0L)
        } else {
            -1L
        }
        val response = buildFastProvisionalResponseFromEventSource(
            event = event,
            packageName = packageName,
            timestamp = System.currentTimeMillis()
        ) ?: return -1L

        val buildMs = SystemClock.uptimeMillis() - startedAtMs
        val beforeOverlayMs = SystemClock.uptimeMillis()
        val fastProvisionalMaskMs = if (eventTimeMs > 0L) {
            beforeOverlayMs - eventTimeMs
        } else {
            beforeOverlayMs - serviceReceivedAtMs
        }
        val analysis = AndroidAnalysisAttempt(
            ok = true,
            packageName = packageName,
            url = "event-source-fast-provisional",
            sensitivity = AnalysisSensitivityStore.get(applicationContext),
            latencyMs = 0L,
            parseDelayMs = 0L,
            candidateExtractionMs = buildMs,
            nodeCollectionMs = buildMs,
            accessibilityMaskLatencyMs = fastProvisionalMaskMs,
            riskGateMaskMs = recentRiskGateMaskMs(startedAtMs),
            riskGateEventAgeMs = recentRiskGateEventAgeMs(startedAtMs),
            riskGateReceiveToMaskMs = recentRiskGateReceiveToMaskMs(startedAtMs),
            fastProvisionalMaskMs = fastProvisionalMaskMs,
            fastProvisionalEventAgeMs = eventAgeMs,
            fastProvisionalBuildMs = buildMs,
            commentCount = response.results.size,
            offensiveCount = response.results.size,
            filteredCount = response.filteredCount,
            response = response,
            candidateRouteSamples = listOf("event-source-fast-provisional")
        )

        updateMaskOverlay(
            currentPackage = packageName,
            analysis = analysis,
            snapshotOverlayRevision = overlayRevision,
            visualRoiPlan = VisualTextRoiPlan(rois = emptyList(), candidateCount = 0),
            isProvisionalAccessibilityMask = true,
            allowDuringScrollStabilization = !usesViewportStableBrowserOverlay(packageName),
            preserveExistingPreciseVisualMasks = true
        )

        val afterOverlayMs = SystemClock.uptimeMillis()
        val overlayMs = afterOverlayMs - beforeOverlayMs
        val receiveToMaskMs = afterOverlayMs - serviceReceivedAtMs
        val elapsedMs = if (eventTimeMs > 0L) {
            afterOverlayMs - eventTimeMs
        } else {
            receiveToMaskMs
        }
        rememberFastProvisionalMask(
            elapsedMs = elapsedMs,
            eventAgeMs = eventAgeMs,
            buildMs = buildMs,
            overlayMs = overlayMs,
            receiveToMaskMs = receiveToMaskMs
        )
        AnalysisDiagnosticsStore.saveAttempt(
            applicationContext,
            analysis.copy(
                accessibilityMaskLatencyMs = elapsedMs,
                riskGateMaskMs = recentRiskGateMaskMs(startedAtMs),
                riskGateEventAgeMs = recentRiskGateEventAgeMs(startedAtMs),
                riskGateReceiveToMaskMs = recentRiskGateReceiveToMaskMs(startedAtMs),
                fastProvisionalMaskMs = elapsedMs,
                fastProvisionalOverlayMs = overlayMs,
                fastProvisionalReceiveToMaskMs = receiveToMaskMs
            ).withPipelineDiagnostics(
                experimentMode = currentExperimentMode(),
                nodeCount = 0,
                screenCandidateCount = response.results.size,
                charLocationNodeCount = 0,
                charRangeCandidateCount = 0,
                candidateParallelWaitMs = -1L,
                nodeCollectionMs = buildMs,
                screenCandidateExtractionMs = buildMs
            )
        )
        Log.d(
            TAG,
            "fast provisional mask results=${response.results.size} elapsedMs=$elapsedMs " +
                "eventAgeMs=$eventAgeMs buildMs=$buildMs overlayMs=$overlayMs " +
                "receiveToMaskMs=$receiveToMaskMs eventType=${event.eventType}"
        )
        return elapsedMs
    }

    private fun renderFastProvisionalMaskFromActiveBrowserWindow(
        packageName: String,
        triggerEventType: Int,
        eventTimeMs: Long,
        serviceReceivedAtMs: Long
    ): Long {
        if (!usesViewportStableBrowserOverlay(packageName)) return -1L
        if (AnalysisSensitivityStore.get(applicationContext) <= 0) return -1L
        if (
            triggerEventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            triggerEventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            triggerEventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            triggerEventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            triggerEventType != AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) {
            return -1L
        }

        val startedAtMs = SystemClock.uptimeMillis()
        if (startedAtMs - lastBrowserRootFastScanAtMs in 0L until FAST_BROWSER_ROOT_MIN_INTERVAL_MS) {
            return -1L
        }
        val root = rootInActiveWindow ?: return -1L
        if (root.packageName?.toString() !in BROWSER_PACKAGES) return -1L
        lastBrowserRootFastScanAtMs = startedAtMs

        val eventAgeMs = if (eventTimeMs > 0L) {
            (startedAtMs - eventTimeMs).coerceAtLeast(0L)
        } else {
            -1L
        }
        val response = buildFastBrowserRootResponse(
            root = root,
            packageName = packageName,
            timestamp = System.currentTimeMillis()
        ) ?: return -1L

        val buildMs = SystemClock.uptimeMillis() - startedAtMs
        val beforeOverlayMs = SystemClock.uptimeMillis()
        val fastProvisionalMaskMs = if (eventTimeMs > 0L) {
            beforeOverlayMs - eventTimeMs
        } else {
            beforeOverlayMs - serviceReceivedAtMs
        }
        val analysis = AndroidAnalysisAttempt(
            ok = true,
            packageName = packageName,
            url = "browser-root-fast-provisional",
            sensitivity = AnalysisSensitivityStore.get(applicationContext),
            latencyMs = 0L,
            parseDelayMs = 0L,
            candidateExtractionMs = buildMs,
            nodeCollectionMs = buildMs,
            accessibilityMaskLatencyMs = fastProvisionalMaskMs,
            riskGateMaskMs = recentRiskGateMaskMs(startedAtMs),
            riskGateEventAgeMs = recentRiskGateEventAgeMs(startedAtMs),
            riskGateReceiveToMaskMs = recentRiskGateReceiveToMaskMs(startedAtMs),
            fastProvisionalMaskMs = fastProvisionalMaskMs,
            fastProvisionalEventAgeMs = eventAgeMs,
            fastProvisionalBuildMs = buildMs,
            commentCount = response.results.size,
            offensiveCount = response.results.size,
            filteredCount = response.filteredCount,
            response = response,
            candidateRouteSamples = listOf("browser-root-fast-provisional")
        )

        updateMaskOverlay(
            currentPackage = packageName,
            analysis = analysis,
            snapshotOverlayRevision = overlayRevision,
            visualRoiPlan = VisualTextRoiPlan(rois = emptyList(), candidateCount = 0),
            isProvisionalAccessibilityMask = true,
            allowDuringScrollStabilization = false,
            preserveExistingPreciseVisualMasks = true
        )

        val afterOverlayMs = SystemClock.uptimeMillis()
        val overlayMs = afterOverlayMs - beforeOverlayMs
        val receiveToMaskMs = afterOverlayMs - serviceReceivedAtMs
        val elapsedMs = if (eventTimeMs > 0L) {
            afterOverlayMs - eventTimeMs
        } else {
            receiveToMaskMs
        }
        rememberFastProvisionalMask(
            elapsedMs = elapsedMs,
            eventAgeMs = eventAgeMs,
            buildMs = buildMs,
            overlayMs = overlayMs,
            receiveToMaskMs = receiveToMaskMs
        )
        AnalysisDiagnosticsStore.saveAttempt(
            applicationContext,
            analysis.copy(
                accessibilityMaskLatencyMs = elapsedMs,
                riskGateMaskMs = recentRiskGateMaskMs(startedAtMs),
                riskGateEventAgeMs = recentRiskGateEventAgeMs(startedAtMs),
                riskGateReceiveToMaskMs = recentRiskGateReceiveToMaskMs(startedAtMs),
                fastProvisionalMaskMs = elapsedMs,
                fastProvisionalOverlayMs = overlayMs,
                fastProvisionalReceiveToMaskMs = receiveToMaskMs
            ).withPipelineDiagnostics(
                experimentMode = currentExperimentMode(),
                nodeCount = 0,
                screenCandidateCount = response.results.size,
                charLocationNodeCount = response.results.count { item ->
                    item.authorId.orEmpty().startsWith("android-accessibility-char-range:browser:")
                },
                charRangeCandidateCount = response.results.count { item ->
                    item.authorId.orEmpty().startsWith("android-accessibility-char-range:browser:")
                },
                candidateParallelWaitMs = -1L,
                nodeCollectionMs = buildMs,
                screenCandidateExtractionMs = buildMs
            )
        )
        Log.d(
            TAG,
            "browser root fast provisional results=${response.results.size} elapsedMs=$elapsedMs " +
                "eventAgeMs=$eventAgeMs buildMs=$buildMs overlayMs=$overlayMs " +
                "receiveToMaskMs=$receiveToMaskMs eventType=$triggerEventType"
        )
        return elapsedMs
    }

    private fun buildFastBrowserRootResponse(
        root: AccessibilityNodeInfo,
        packageName: String,
        timestamp: Long
    ): AndroidAnalysisResponse? {
        val results = mutableListOf<AndroidAnalysisResultItem>()
        val seen = mutableSetOf<String>()
        var visited = 0

        fun maybeAddBrowserText(node: AccessibilityNodeInfo, text: String?, bounds: BoundsRect, sourceId: String) {
            val rawText = text
                ?.replace(FAST_PROVISIONAL_WHITESPACE_PATTERN, " ")
                ?.trim()
                .orEmpty()
            if (rawText.length < 2 || rawText.length > FAST_BROWSER_ROOT_MAX_TEXT_LENGTH) return
            if (isFastBrowserUrlLikeText(rawText)) return
            if (!mayContainFastProvisionalHit(rawText)) return
            val ranges = VisualTextOcrCandidateFilter.findAnalysisRanges(rawText)
            if (ranges.isEmpty()) return

            var addedExactRange = false
            ranges.forEach { range ->
                if (results.size >= FAST_BROWSER_ROOT_MAX_RESULTS) return
                val exactBounds = fastBrowserExactRangeBounds(node, rawText, range) ?: return@forEach
                val exactText = range.analysisText.trim().ifBlank { range.visualText.trim() }
                if (exactText.isBlank()) return@forEach
                val exactLength = exactText.codePointCount(0, exactText.length)
                if (exactLength <= 0) return@forEach
                val exactKey = "exact|$exactText|${exactBounds.left}|${exactBounds.top}|${exactBounds.right}|${exactBounds.bottom}"
                if (!seen.add(exactKey)) return@forEach
                results += AndroidAnalysisResultItem(
                    original = exactText,
                    boundsInScreen = exactBounds,
                    authorId = "android-accessibility-char-range:browser:fast-root:${range.visualText}",
                    isOffensive = true,
                    isProfane = true,
                    isToxic = false,
                    isHate = false,
                    scores = HarmScores(profanity = 1.0, toxicity = 0.0, hate = 0.0),
                    evidenceSpans = listOf(
                        EvidenceSpan(
                            text = exactText,
                            start = 0,
                            end = exactLength,
                            score = 1.0
                        )
                    )
                )
                addedExactRange = true
            }
            if (addedExactRange || results.size >= FAST_BROWSER_ROOT_MAX_RESULTS) return
            if (!isFastBrowserCompactBounds(bounds)) return

            val compactKey = "compact|$rawText|${bounds.left}|${bounds.top}|${bounds.right}|${bounds.bottom}"
            if (!seen.add(compactKey)) return
            val spans = ranges.mapNotNull { range ->
                val start = rawText.codePointCount(0, range.start.coerceIn(0, rawText.length))
                val end = rawText.codePointCount(0, range.end.coerceIn(range.start, rawText.length))
                if (end <= start) return@mapNotNull null
                EvidenceSpan(
                    text = range.analysisText,
                    start = start,
                    end = end,
                    score = 1.0
                )
            }
            if (spans.isEmpty()) return
            results += AndroidAnalysisResultItem(
                original = rawText,
                boundsInScreen = bounds,
                authorId = "android-accessibility-browser-compact:fast-root:$sourceId",
                isOffensive = true,
                isProfane = true,
                isToxic = false,
                isHate = false,
                scores = HarmScores(profanity = 1.0, toxicity = 0.0, hate = 0.0),
                evidenceSpans = spans
            )
        }

        fun nodeBounds(node: AccessibilityNodeInfo): BoundsRect? {
            if (!node.isVisibleToUser) return null
            val rect = Rect().also { node.getBoundsInScreen(it) }
            val width = rect.width()
            val height = rect.height()
            if (width < FAST_PROVISIONAL_MIN_WIDTH_PX || height < FAST_PROVISIONAL_MIN_HEIGHT_PX) {
                return null
            }
            return BoundsRect(rect.left, rect.top, rect.right, rect.bottom)
        }

        fun dfs(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null) return
            if (depth > FAST_BROWSER_ROOT_MAX_DEPTH) return
            if (visited >= FAST_BROWSER_ROOT_MAX_NODES) return
            if (results.size >= FAST_BROWSER_ROOT_MAX_RESULTS) return
            if (!node.isVisibleToUser) return
            visited += 1

            val bounds = nodeBounds(node)
            if (bounds != null) {
                val sourceId = "$packageName:$depth:$visited"
                maybeAddBrowserText(node, node.text?.toString(), bounds, sourceId)
                if (results.size >= FAST_BROWSER_ROOT_MAX_RESULTS) return
                maybeAddBrowserText(node, node.contentDescription?.toString(), bounds, sourceId)
            }

            for (index in 0 until node.childCount) {
                dfs(node.getChild(index), depth + 1)
                if (visited >= FAST_BROWSER_ROOT_MAX_NODES || results.size >= FAST_BROWSER_ROOT_MAX_RESULTS) {
                    return
                }
            }
        }

        dfs(root, 0)
        if (results.isEmpty()) return null
        return AndroidAnalysisResponse(
            timestamp = timestamp,
            filteredCount = 0,
            results = results.take(FAST_BROWSER_ROOT_MAX_RESULTS)
        )
    }

    private fun fastBrowserExactRangeBounds(
        node: AccessibilityNodeInfo,
        rawText: String,
        range: VisualTextOcrCandidateFilter.CandidateRange
    ): BoundsRect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val start = range.start.coerceIn(0, rawText.length)
        val end = range.end.coerceIn(start, rawText.length)
        if (end <= start) return null

        val boxes = requestTextCharacterBoxesForRange(
            node = node,
            rawText = rawText,
            startIndex = start,
            length = end - start
        )
        if (boxes.isEmpty()) return null

        val left = boxes.minOf { box -> box.boundsInScreen.left }
        val top = boxes.minOf { box -> box.boundsInScreen.top }
        val right = boxes.maxOf { box -> box.boundsInScreen.right }
        val bottom = boxes.maxOf { box -> box.boundsInScreen.bottom }
        if (right - left < FAST_PROVISIONAL_MIN_WIDTH_PX || bottom - top < FAST_PROVISIONAL_MIN_HEIGHT_PX) {
            return null
        }
        return BoundsRect(
            left = left.coerceAtLeast(0),
            top = top.coerceAtLeast(0),
            right = right,
            bottom = bottom
        )
    }

    private fun isFastBrowserCompactBounds(bounds: BoundsRect): Boolean {
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        return bounds.top >= FAST_BROWSER_ROOT_COMPACT_MIN_TOP_PX &&
            width in FAST_PROVISIONAL_MIN_WIDTH_PX..FAST_BROWSER_ROOT_COMPACT_MAX_WIDTH_PX &&
            height in FAST_PROVISIONAL_MIN_HEIGHT_PX..FAST_BROWSER_ROOT_COMPACT_MAX_HEIGHT_PX
    }

    private fun isFastBrowserUrlLikeText(text: String): Boolean {
        val lower = text.lowercase()
        return lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("www.") ||
            lower.contains("://") ||
            lower.contains("/search?q=") ||
            lower.contains("?q=") ||
            lower.contains("&q=")
    }

    private fun rememberFastProvisionalMask(
        elapsedMs: Long,
        eventAgeMs: Long,
        buildMs: Long,
        overlayMs: Long,
        receiveToMaskMs: Long
    ) {
        if (elapsedMs < 0L) return
        lastFastProvisionalMaskMs = elapsedMs
        lastFastProvisionalEventAgeMs = eventAgeMs
        lastFastProvisionalBuildMs = buildMs
        lastFastProvisionalOverlayMs = overlayMs
        lastFastProvisionalReceiveToMaskMs = receiveToMaskMs
        lastFastProvisionalAtMs = SystemClock.uptimeMillis()
    }

    private fun recentFastProvisionalMaskMs(parseStartedAtMs: Long): Long {
        return recentFastProvisionalValue(parseStartedAtMs, lastFastProvisionalMaskMs)
    }

    private fun recentFastProvisionalEventAgeMs(parseStartedAtMs: Long): Long {
        return recentFastProvisionalValue(parseStartedAtMs, lastFastProvisionalEventAgeMs)
    }

    private fun recentFastProvisionalBuildMs(parseStartedAtMs: Long): Long {
        return recentFastProvisionalValue(parseStartedAtMs, lastFastProvisionalBuildMs)
    }

    private fun recentFastProvisionalOverlayMs(parseStartedAtMs: Long): Long {
        return recentFastProvisionalValue(parseStartedAtMs, lastFastProvisionalOverlayMs)
    }

    private fun recentFastProvisionalReceiveToMaskMs(parseStartedAtMs: Long): Long {
        return recentFastProvisionalValue(parseStartedAtMs, lastFastProvisionalReceiveToMaskMs)
    }

    private fun recentFastProvisionalValue(parseStartedAtMs: Long, value: Long): Long {
        val latencyMs = lastFastProvisionalMaskMs
        val renderedAtMs = lastFastProvisionalAtMs
        if (latencyMs < 0L || value < 0L || renderedAtMs <= 0L) return -1L

        val nowMs = SystemClock.uptimeMillis()
        val ageMs = nowMs - renderedAtMs
        val offsetFromParseStartMs = renderedAtMs - parseStartedAtMs
        return if (ageMs in 0L..5_000L && offsetFromParseStartMs >= -750L) {
            value
        } else {
            -1L
        }
    }

    private fun buildFastProvisionalResponseFromEventSource(
        event: AccessibilityEvent,
        packageName: String,
        timestamp: Long
    ): AndroidAnalysisResponse? {
        val metrics = resources.displayMetrics
        val results = mutableListOf<AndroidAnalysisResultItem>()
        val seen = mutableSetOf<String>()
        var visited = 0

        fun maybeAddText(text: String?, bounds: BoundsRect, sourceId: String) {
            val rawText = text
                ?.replace(FAST_PROVISIONAL_WHITESPACE_PATTERN, " ")
                ?.trim()
                .orEmpty()
            if (rawText.length < 2 || rawText.length > FAST_PROVISIONAL_MAX_TEXT_LENGTH) return
            if (!mayContainFastProvisionalHit(rawText)) return
            val ranges = VisualTextOcrCandidateFilter.findAnalysisRanges(rawText)
            if (ranges.isEmpty()) return
            val key = "$rawText|${bounds.left}|${bounds.top}|${bounds.right}|${bounds.bottom}"
            if (!seen.add(key)) return

            val spans = ranges.mapNotNull { range ->
                val start = rawText.codePointCount(0, range.start.coerceIn(0, rawText.length))
                val end = rawText.codePointCount(0, range.end.coerceIn(0, rawText.length))
                if (end <= start) return@mapNotNull null
                EvidenceSpan(
                    text = range.analysisText,
                    start = start,
                    end = end,
                    score = 1.0
                )
            }
            if (spans.isEmpty()) return

            results += AndroidAnalysisResultItem(
                original = rawText,
                boundsInScreen = bounds,
                authorId = sourceId,
                isOffensive = true,
                isProfane = true,
                isToxic = false,
                isHate = false,
                scores = HarmScores(profanity = 1.0, toxicity = 0.0, hate = 0.0),
                evidenceSpans = spans
            )
        }

        fun nodeBounds(node: AccessibilityNodeInfo): BoundsRect? {
            if (!node.isVisibleToUser) return null
            val rect = Rect().also { node.getBoundsInScreen(it) }
            val width = rect.width()
            val height = rect.height()
            if (width < FAST_PROVISIONAL_MIN_WIDTH_PX || height < FAST_PROVISIONAL_MIN_HEIGHT_PX) return null
            if (
                width > metrics.widthPixels * FAST_PROVISIONAL_MAX_SCREEN_WIDTH_RATIO &&
                height > metrics.heightPixels * FAST_PROVISIONAL_MAX_SCREEN_HEIGHT_RATIO
            ) {
                return null
            }
            return BoundsRect(rect.left, rect.top, rect.right, rect.bottom)
        }

        val source = event.source
        val sourceBounds = source?.let { nodeBounds(it) }
        if (sourceBounds != null && event.text.isNotEmpty()) {
            event.text.forEachIndexed { index, value ->
                maybeAddText(
                    text = value?.toString(),
                    bounds = sourceBounds,
                    sourceId = "event-text-fast:${packageName}:$index"
                )
            }
            if (results.isNotEmpty()) {
                return AndroidAnalysisResponse(
                    timestamp = timestamp,
                    filteredCount = 0,
                    results = results.take(FAST_PROVISIONAL_MAX_RESULTS)
                )
            }
        }

        fun dfs(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null) return
            if (depth > FAST_PROVISIONAL_MAX_DEPTH) return
            if (visited >= FAST_PROVISIONAL_MAX_NODES) return
            if (results.size >= FAST_PROVISIONAL_MAX_RESULTS) return
            visited += 1

            val bounds = nodeBounds(node)
            if (bounds != null) {
                val sourceId = "event-source-fast:${packageName}:${depth}:${visited}"
                maybeAddText(node.text?.toString(), bounds, sourceId)
                maybeAddText(node.contentDescription?.toString(), bounds, sourceId)
            }

            for (index in 0 until node.childCount) {
                dfs(node.getChild(index), depth + 1)
                if (visited >= FAST_PROVISIONAL_MAX_NODES || results.size >= FAST_PROVISIONAL_MAX_RESULTS) {
                    return
                }
            }
        }

        dfs(source, 0)

        if (results.isEmpty()) return null
        return AndroidAnalysisResponse(
            timestamp = timestamp,
            filteredCount = 0,
            results = results.take(FAST_PROVISIONAL_MAX_RESULTS)
        )
    }

    private fun mayContainFastProvisionalHit(text: String): Boolean {
        if (text.any { char ->
                char == '시' || char == '씨' || char == 'ㅅ' || char == 'ㅆ' ||
                    char == '발' || char == '병' || char == 'ㅂ' || char == '비' ||
                    char == '개' || char == '새' ||
                    char == '존' || char == 'ㅈ' || char == '지' || char == '좆' ||
                    char == '미' || char == 'ㅁ' || char == '뒤' || char == '뒈' ||
                    char == '죽' || char == '닥' || char == 'ㄷ' || char == '꺼' ||
                    char == 'ㄲ' || char == '엿'
            }) {
            return true
        }

        val lower = text.lowercase()
        if (
            lower.contains("fuck") ||
            lower.contains("shit") ||
            lower.contains("bitch") ||
            lower.contains("sibal") ||
            lower.contains("qudtls") ||
            lower.contains("wlfkf") ||
            lower.contains("whssk") ||
            lower.contains("alcls") ||
            lower.contains("rjwu")
        ) {
            return true
        }

        return lower.contains('t') &&
            (lower.contains('q') || lower.contains('k') || lower.contains('f') || lower.contains('1'))
    }

    private fun updateMaskOverlay(
        currentPackage: String,
        analysis: AndroidAnalysisAttempt?,
        snapshotOverlayRevision: Long,
        visualRoiPlan: VisualTextRoiPlan? = null,
        isProvisionalVisualMask: Boolean = false,
        isProvisionalAccessibilityMask: Boolean = false,
        allowDuringScrollStabilization: Boolean = false,
        preserveExistingPreciseVisualMasks: Boolean = false
    ) {
        if (currentPackage != lastObservedPackage) {
            Log.d(
                TAG,
                "skip mask overlay: stale package current=$currentPackage observed=$lastObservedPackage"
            )
            clearMaskOverlay()
            return
        }
        if (
            currentPackage == INSTAGRAM_PACKAGE &&
            instagramSafeCommentMirrorSession.isActive
        ) {
            maskOverlayController.clear()
            riskGateActive = false
            preservedRecentVisualMiss = false
            preservedRecentAnalysisFailure = false
            provisionalVisualMaskActive = false
            provisionalAccessibilityMaskActive = false
            Log.d(TAG, "skip legacy instagram overlay: safe mirror owns comment panel")
            return
        }

        if (snapshotOverlayRevision != overlayRevision) {
            Log.d(
                TAG,
                "skip mask overlay: stale overlay revision snapshot=$snapshotOverlayRevision current=$overlayRevision"
            )
            if (
                MaskOverlayEventPolicy.shouldRetryAfterStaleOverlayResult(
                    analysisOk = analysis?.ok == true,
                    snapshotOverlayRevision = snapshotOverlayRevision,
                    currentOverlayRevision = overlayRevision
                )
            ) {
                lastSnapshotSignature = null
                scheduleDeferredFollowUpParse(waitForScrollStabilization = true)
            }
            return
        }

        val analysisSensitivity = analysis?.sensitivity
        val currentSensitivity = AnalysisSensitivityStore.get(applicationContext)
        if (currentSensitivity <= 0) {
            Log.d(TAG, "clear mask overlay: sensitivity disabled")
            clearMaskOverlay()
            return
        }
        if (analysisSensitivity != null && analysisSensitivity != currentSensitivity) {
            Log.d(
                TAG,
                "skip mask overlay: stale sensitivity analysis=$analysisSensitivity current=$currentSensitivity"
            )
            return
        }

        if (
            currentPackage == YOUTUBE_PACKAGE &&
            isYoutubeScrollLoadingHoldActive() &&
            hasActiveYoutubeLoadingGate()
        ) {
            Log.d(TAG, "preserve youtube loading until scroll geometry stabilizes")
            scheduleDeferredFollowUpParse(waitForScrollStabilization = true)
            return
        }

        if (analysis?.ok == true && isInScrollStabilizationWindow() && !allowDuringScrollStabilization) {
            Log.d(TAG, "defer mask overlay render: scroll stabilization active")
            scheduleDeferredFollowUpParse(waitForScrollStabilization = true)
            return
        }

        if (supportsMaskOverlay(currentPackage) && analysis?.ok == true) {
            if (
                currentPackage == YOUTUBE_PACKAGE &&
                !youtubeCommentInitialAnalysisCompleted &&
                (isProvisionalAccessibilityMask || isProvisionalVisualMask) &&
                hasActiveYoutubeLoadingGate()
            ) {
                Log.d(TAG, "preserve initial youtube pane loading over provisional result")
                riskGateActive = false
                preservedRecentVisualMiss = false
                preservedRecentAnalysisFailure = false
                return
            }
            if (
                currentPackage == YOUTUBE_PACKAGE &&
                youtubeCommentInitialAnalysisCompleted &&
                youtubeKnownHarmfulComments.isNotEmpty() &&
                (isProvisionalAccessibilityMask || isProvisionalVisualMask)
            ) {
                refreshYoutubeReplyAnchoredMasks(
                    reason = "provisional-preserve-known",
                    allowRebind = true
                )
                riskGateActive = false
                preservedRecentVisualMiss = false
                preservedRecentAnalysisFailure = false
                provisionalVisualMaskActive = false
                provisionalAccessibilityMaskActive = false
                return
            }
            if (currentPackage == YOUTUBE_PACKAGE && isProvisionalAccessibilityMask) {
                val provisionalCommentSpecs = buildBlockedYoutubeCommentSpecs(
                    response = analysis.response,
                    visualRoiPlan = visualRoiPlan
                )
                if (provisionalCommentSpecs.isEmpty()) {
                    Log.d(TAG, "skip youtube provisional accessibility overlay without trusted comments")
                    return
                }
                val rendered = maskOverlayController.renderDirect(
                    specs = provisionalCommentSpecs,
                    reason = "youtube-provisional-accessibility-comments"
                )
                Log.d(
                    TAG,
                    "render youtube provisional accessibility comment masks " +
                        "count=${provisionalCommentSpecs.size} rendered=$rendered"
                )
                if (rendered) {
                    lastYoutubeCommentPaneSpec = null
                    lastYoutubeBlockedSpecs = provisionalCommentSpecs
                }
                riskGateActive = false
                preservedRecentVisualMiss = false
                preservedRecentAnalysisFailure = false
                provisionalVisualMaskActive = false
                provisionalAccessibilityMaskActive = false
                return
            }
            if (
                currentPackage == YOUTUBE_PACKAGE &&
                isProvisionalVisualMask &&
                lastYoutubeBlockedSpecs.isNotEmpty()
            ) {
                val visualBlockedSpecs = buildBlockedYoutubeCommentSpecs(
                    response = analysis.response,
                    visualRoiPlan = visualRoiPlan
                )
                val mergedBlockedSpecs = YoutubeSkeletonMaskBuilder.mergeCommentSpecs(
                    primarySpecs = lastYoutubeBlockedSpecs,
                    supplementalSpecs = visualBlockedSpecs
                )
                val rendered = maskOverlayController.renderDirect(
                    specs = mergedBlockedSpecs,
                    reason = "youtube-preserve-exact-comments-over-visual"
                )
                Log.d(
                    TAG,
                    "preserve exact youtube comment masks over visual OCR " +
                        "existing=${lastYoutubeBlockedSpecs.size} visual=${visualBlockedSpecs.size} " +
                        "merged=${mergedBlockedSpecs.size} rendered=$rendered"
                )
                if (rendered) {
                    lastYoutubeCommentPaneSpec = null
                    lastYoutubeBlockedSpecs = mergedBlockedSpecs
                }
                riskGateActive = false
                preservedRecentVisualMiss = false
                preservedRecentAnalysisFailure = false
                provisionalVisualMaskActive = false
                provisionalAccessibilityMaskActive = false
                return
            }
            val isFinalYoutubeAnalysis = currentPackage == YOUTUBE_PACKAGE &&
                !isProvisionalVisualMask &&
                !isProvisionalAccessibilityMask
            if (
                YOUTUBE_SAFE_MIRROR_ENABLED &&
                youtubeSafeCommentMirrorController.isActive &&
                isFinalYoutubeAnalysis
            ) {
                maskOverlayController.clear()
                riskGateActive = false
                preservedRecentVisualMiss = false
                preservedRecentAnalysisFailure = false
                provisionalVisualMaskActive = false
                provisionalAccessibilityMaskActive = false
                return
            }
            if (isFinalYoutubeAnalysis && !hasConfirmedYoutubeCommentPanel(visualRoiPlan)) {
                if (
                    youtubeCommentInitialAnalysisCompleted &&
                    hasFreshYoutubeCommentPanelConfirmation()
                ) {
                    Log.d(TAG, "preserve attached youtube masks during transient final ROI miss")
                    scheduleDeferredFollowUpParse(waitForScrollStabilization = true)
                    return
                }
                if (hasFreshNativeYoutubeCommentPanel()) {
                    Log.d(TAG, "preserve native youtube comment loading until comment ROI is available")
                    scheduleDeferredFollowUpParse(waitForScrollStabilization = true)
                    return
                }
                if (maskOverlayController.hasActiveMasks()) {
                    Log.d(TAG, "clear youtube masks: comment panel ROI not confirmed")
                    maskOverlayController.fadeOutAndClear(
                        durationMs = YOUTUBE_SAFE_FADE_OUT_MS,
                        reason = "youtube-no-comment-panel"
                    )
                } else {
                    Log.d(TAG, "skip youtube final overlay: comment panel ROI not confirmed")
                }
                lastYoutubeCommentPaneSpec = null
                lastYoutubeBlockedSpecs = emptyList()
                lastYoutubeScrollLoadingSpec = null
                lastYoutubeCommentPanelConfirmedAtMs = 0L
                youtubeCommentInitialAnalysisCompleted = false
                clearYoutubeHarmfulCommentAnchor()
                riskGateActive = false
                preservedRecentVisualMiss = false
                preservedRecentAnalysisFailure = false
                provisionalVisualMaskActive = false
                provisionalAccessibilityMaskActive = false
                return
            }
            if (isFinalYoutubeAnalysis) {
                youtubeCommentInitialAnalysisCompleted = true
            }
            val finalYoutubeHarmfulCount = if (isFinalYoutubeAnalysis) {
                countHarmfulResults(analysis.response)
            } else {
                0
            }
            if (isFinalYoutubeAnalysis && finalYoutubeHarmfulCount == 0) {
                if (youtubeKnownHarmfulComments.isNotEmpty()) {
                    val renderedKnownMasks = refreshYoutubeReplyAnchoredMasks(
                        reason = "final-safe-preserve-known",
                        allowRebind = true
                    )
                    if (!renderedKnownMasks) {
                        val activeSpecs = maskOverlayController.activeSpecsSnapshot()
                        if (activeSpecs.any { spec -> spec.style == MaskOverlayStyle.LOADING }) {
                            maskOverlayController.clear()
                        }
                    }
                    lastYoutubeCommentPaneSpec = null
                    youtubeLoadingOverlayStartedAtMs = 0L
                    riskGateActive = false
                    preservedRecentVisualMiss = false
                    preservedRecentAnalysisFailure = false
                    provisionalVisualMaskActive = false
                    provisionalAccessibilityMaskActive = false
                    return
                }
                if (maskOverlayController.hasActiveMasks()) {
                    Log.d(TAG, "youtube safe result fade-out start durationMs=$YOUTUBE_SAFE_FADE_OUT_MS")
                    maskOverlayController.fadeOutAndClear(
                        durationMs = YOUTUBE_SAFE_FADE_OUT_MS,
                        reason = "youtube-model-safe"
                    )
                }
                lastYoutubeCommentPaneSpec = null
                lastYoutubeBlockedSpecs = emptyList()
                clearYoutubeHarmfulCommentAnchor()
                riskGateActive = false
                preservedRecentVisualMiss = false
                preservedRecentAnalysisFailure = false
                provisionalVisualMaskActive = false
                provisionalAccessibilityMaskActive = false
                return
            }
            if (isFinalYoutubeAnalysis && finalYoutubeHarmfulCount > 0) {
                val pendingLoadingSpec = lastYoutubeCommentPaneSpec
                val blockedCommentSpecs = buildBlockedYoutubeCommentSpecs(
                    response = analysis.response,
                    visualRoiPlan = visualRoiPlan
                )
                val renderedRawSpecs = blockedCommentSpecs.isNotEmpty() && maskOverlayController.renderDirect(
                        specs = blockedCommentSpecs,
                        reason = "youtube-model-harmful-comments"
                    )
                if (renderedRawSpecs) {
                    lastYoutubeBlockedSpecs = blockedCommentSpecs
                }
                val renderedAnchoredSpecs = rememberYoutubeHarmfulCommentAnchor(
                    results = analysis.response?.results.orEmpty(),
                    fallbackSpecs = blockedCommentSpecs
                )
                if (renderedAnchoredSpecs || renderedRawSpecs) {
                    lastYoutubeCommentPaneSpec = null
                    youtubeLoadingOverlayStartedAtMs = 0L
                    riskGateActive = false
                    preservedRecentVisualMiss = false
                    preservedRecentAnalysisFailure = false
                    provisionalVisualMaskActive = false
                    provisionalAccessibilityMaskActive = false
                    return
                }

                val loadingSpec = pendingLoadingSpec ?: lastYoutubeScrollLoadingSpec
                if (loadingSpec != null) {
                    val restoredLoadingSpec = loadingSpec.copy(
                        label = "comments-loading",
                        allowScrollTranslation = false,
                        debugSource = "youtube-comment-pane-loading-unresolved",
                        style = MaskOverlayStyle.LOADING
                    )
                    maskOverlayController.renderDirect(
                        specs = listOf(restoredLoadingSpec),
                        reason = "youtube-harmful-anchor-unresolved"
                    )
                    lastYoutubeCommentPaneSpec = restoredLoadingSpec
                    youtubeLoadingOverlayStartedAtMs = SystemClock.uptimeMillis()
                    provisionalAccessibilityMaskActive = true
                    scheduleYoutubeLoadingExpiry("harmful-anchor-unresolved")
                }
                youtubeCommentInitialAnalysisCompleted = false
                Log.d(
                    TAG,
                    "retry youtube harmful anchor: no visible raw or reply-anchored mask " +
                        "raw=${blockedCommentSpecs.size} harmful=$finalYoutubeHarmfulCount"
                )
                scheduleDeferredFollowUpParse(waitForScrollStabilization = true)
                return
            }
            val preserveExistingIfEmpty = MaskOverlayEventPolicy.shouldPreserveExistingOnEmptyPlan(
                hasActiveMasks = maskOverlayController.hasActiveMasks(),
                snapshotOverlayRevision = snapshotOverlayRevision,
                currentOverlayRevision = overlayRevision,
                isScrollStabilizing = isInScrollStabilizationWindow(),
                hasProvisionalMasks = provisionalVisualMaskActive || provisionalAccessibilityMaskActive,
                isProvisionalPlan = isProvisionalVisualMask || isProvisionalAccessibilityMask
            )
            val responseResultCount = analysis.response?.results?.size ?: 0
            Log.d(
                TAG,
                "render mask overlay package=$currentPackage results=$responseResultCount " +
                    "preserveExistingIfEmpty=$preserveExistingIfEmpty " +
                    "provisionalVisual=$isProvisionalVisualMask provisionalAccessibility=$isProvisionalAccessibilityMask"
            )
            maskOverlayController.render(
                response = analysis.response,
                preserveExistingIfEmpty = preserveExistingIfEmpty,
                preserveExistingPreciseVisualMasks = preserveExistingPreciseVisualMasks
            )
            riskGateActive = false
            preservedRecentVisualMiss = false
            preservedRecentAnalysisFailure = false
            val hasActiveMasksAfterRender = maskOverlayController.hasActiveMasks()
            val preservedExistingEmptyPlan = preserveExistingIfEmpty && responseResultCount == 0
            provisionalVisualMaskActive =
                if (preservedExistingEmptyPlan) {
                    provisionalVisualMaskActive && hasActiveMasksAfterRender
                } else {
                    isProvisionalVisualMask && hasActiveMasksAfterRender
                }
            provisionalAccessibilityMaskActive =
                if (preservedExistingEmptyPlan) {
                    provisionalAccessibilityMaskActive && hasActiveMasksAfterRender
                } else {
                    isProvisionalAccessibilityMask && hasActiveMasksAfterRender
                }
        } else {
            if (
                supportsMaskOverlay(currentPackage) &&
                !MaskOverlayEventPolicy.shouldClearAfterAnalysisFailure(
                    hasActiveMasks = maskOverlayController.hasActiveMasks(),
                    hasRenderableVisualRois = visualRoiPlan?.hasRenderableVisualRois() == true,
                    hasProvisionalMasks = provisionalVisualMaskActive || provisionalAccessibilityMaskActive,
                    visualAnalysisInFlight = visualAnalysisInFlight
                )
            ) {
                Log.d(TAG, "preserve mask overlay after analysis failure")
                preservedRecentAnalysisFailure = true
                return
            }
            Log.d(
                TAG,
                "clear mask overlay package=$currentPackage analysisOk=${analysis?.ok}"
            )
            clearMaskOverlay()
        }
    }

    private fun clearMaskOverlay() {
        cancelYoutubeAutoPrecheck("clear-overlay")
        lastYoutubeCommentPaneSpec = null
        overlayRevision += 1
        lastSnapshotSignature = null
        preservedRecentVisualMiss = false
        preservedRecentAnalysisFailure = false
        provisionalVisualMaskActive = false
        provisionalAccessibilityMaskActive = false
        riskGateActive = false
        lastVisualRefreshSignature = null
        lastVisualRefreshCompletedAtMs = 0L
        invalidateVisualAnalysis(reason = "clear-overlay", requestFollowUp = false)
        maskOverlayController.clear()
        resetAbsoluteScrollPosition()
    }

    private fun releaseMaskOverlay() {
        cancelYoutubeAutoPrecheck("release-overlay")
        lastYoutubeCommentPaneSpec = null
        overlayRevision += 1
        lastSnapshotSignature = null
        preservedRecentVisualMiss = false
        preservedRecentAnalysisFailure = false
        provisionalVisualMaskActive = false
        provisionalAccessibilityMaskActive = false
        riskGateActive = false
        lastVisualRefreshSignature = null
        lastVisualRefreshCompletedAtMs = 0L
        invalidateVisualAnalysis(reason = "release-overlay", requestFollowUp = false)
        maskOverlayController.release()
        resetAbsoluteScrollPosition()
    }

    private fun markOverlayRevisionStale() {
        overlayRevision += 1
        lastSnapshotSignature = null
    }

    private fun markVisualSceneChanged(eventType: Int) {
        preservedRecentVisualMiss = false
        preservedRecentAnalysisFailure = false
        provisionalAccessibilityMaskActive = false
        invalidateVisualAnalysis(reason = "eventType=$eventType", requestFollowUp = true)
    }

    private fun shouldInvalidateVisualScene(
        eventType: Int,
        contentChangedWithActiveMask: Boolean,
        visualAnalysisInFlight: Boolean,
        elapsedSinceVisualAnalysisStartMs: Long
    ): Boolean {
        if (contentChangedWithActiveMask) return false

        if (
            MaskOverlayEventPolicy.shouldDeferVisualInvalidationForFreshCapture(
                eventType = eventType,
                visualAnalysisInFlight = visualAnalysisInFlight,
                elapsedSinceVisualAnalysisStartMs = elapsedSinceVisualAnalysisStartMs
            )
        ) {
            return false
        }

        return when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> !contentChangedWithActiveMask
            else -> true
        }
    }

    private fun invalidateVisualAnalysis(reason: String, requestFollowUp: Boolean) {
        visualSceneRevision += 1
        lastVisualSupplement = null
        lastSnapshotSignature = null

        if (!visualAnalysisInFlight) return

        visualAnalysisRunId += 1L
        visualAnalysisInFlight = false
        if (requestFollowUp) {
            followUpParseRequested = true
        }
        Log.d(
            TAG,
            "invalidate visual OCR reason=$reason sceneRevision=$visualSceneRevision"
        )
        if (requestFollowUp) {
            scheduleFollowUpAfterVisualGate()
        }
    }

    private fun syncSensitivityState() {
        val currentSensitivity = AnalysisSensitivityStore.get(applicationContext)
        val currentExperimentMode = PipelineExperimentStore.get(applicationContext)
        val previousSensitivity = lastAppliedSensitivity
        val previousExperimentMode = lastAppliedExperimentMode
        if (previousSensitivity == null) {
            lastAppliedSensitivity = currentSensitivity
            lastAppliedExperimentMode = currentExperimentMode
            if (currentSensitivity <= 0) {
                AndroidAnalysisClient.clearCache()
                clearMaskOverlay()
                invalidateInstagramMirrorSession("sensitivity-disabled")
            }
            return
        }
        if (
            previousSensitivity == currentSensitivity &&
            previousExperimentMode == currentExperimentMode
        ) return

        lastAppliedSensitivity = currentSensitivity
        lastAppliedExperimentMode = currentExperimentMode
        AndroidAnalysisClient.clearCache()
        clearMaskOverlay()
        invalidateInstagramMirrorSession("analysis-settings-changed")
        Log.d(
            TAG,
            "analysis settings changed sensitivity=$previousSensitivity->$currentSensitivity " +
                "pipeline=${previousExperimentMode?.id}->${currentExperimentMode.id}; cleared cache and overlay"
        )
    }

    private fun currentExperimentMode(): PipelineExperimentMode {
        return PipelineExperimentStore.get(applicationContext)
    }

    private fun shouldClearOverlayImmediately(eventType: Int): Boolean {
        return eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
    }

    private fun isInScrollStabilizationWindow(): Boolean {
        return isInLastMotionWindow(SCROLL_OVERLAY_STABILIZATION_MS) ||
            isYoutubeScrollLoadingHoldActive()
    }

    private fun isInScrollContentChangePreserveWindow(): Boolean {
        return isInLastMotionWindow(SCROLL_CONTENT_CHANGE_PRESERVE_MS)
    }

    private fun isInLastMotionWindow(windowMs: Long): Boolean {
        val lastMotionEventAtMs = max(lastScrollEventAtMs, lastPointerInteractionAtMs)
        if (lastMotionEventAtMs <= 0L) return false

        val elapsedMs = SystemClock.uptimeMillis() - lastMotionEventAtMs
        return elapsedMs in 0..windowMs
    }

    private fun shouldDeferAnalysisDuringActiveScroll(triggerEventType: Int?): Boolean {
        if (isInScrollStabilizationWindow()) {
            // Content-change bursts often arrive immediately after scroll. If we
            // let them analyze while geometry is still moving, stale masks get
            // reattached to old coordinates and appear to flicker or drift.
            return triggerEventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
                triggerEventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                triggerEventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        }

        if (!maskOverlayController.hasActiveMasks()) return false
        if (!isInOverlayStabilizationWindow()) return false

        return triggerEventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            triggerEventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            triggerEventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
    }

    private fun scheduleDeferredFollowUpParse(waitForScrollStabilization: Boolean = false) {
        val remainingScrollDelayMs = if (waitForScrollStabilization) {
            remainingOverlayStabilizationMs()
        } else {
            remainingScrollDebounceMs()
        }
        if (remainingScrollDelayMs > RETRY_AFTER_IN_FLIGHT_MS) {
            scheduleParse(
                delayMs = remainingScrollDelayMs + RETRY_AFTER_IN_FLIGHT_MS,
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                replaceExisting = true
            )
        } else {
            scheduleParse(RETRY_AFTER_IN_FLIGHT_MS)
        }
    }

    private fun remainingScrollDebounceMs(): Long {
        val lastMotionEventAtMs = max(lastScrollEventAtMs, lastPointerInteractionAtMs)
        if (lastMotionEventAtMs <= 0L) return 0L

        val elapsedMs = SystemClock.uptimeMillis() - lastMotionEventAtMs
        if (elapsedMs < 0L) return PARSE_DELAY_SCROLL_MS

        return (PARSE_DELAY_SCROLL_MS - elapsedMs).coerceAtLeast(0L)
    }

    private fun remainingScrollStabilizationMs(): Long {
        val lastMotionEventAtMs = max(lastScrollEventAtMs, lastPointerInteractionAtMs)
        val baseRemainingMs = if (lastMotionEventAtMs <= 0L) {
            0L
        } else {
            val elapsedMs = SystemClock.uptimeMillis() - lastMotionEventAtMs
            if (elapsedMs < 0L) {
                SCROLL_OVERLAY_STABILIZATION_MS
            } else {
                (SCROLL_OVERLAY_STABILIZATION_MS - elapsedMs).coerceAtLeast(0L)
            }
        }

        if (lastObservedPackage != YOUTUBE_PACKAGE || lastScrollEventAtMs <= 0L) {
            return baseRemainingMs
        }

        val youtubeElapsedMs = SystemClock.uptimeMillis() - lastScrollEventAtMs
        val youtubeRemainingMs = if (youtubeElapsedMs < 0L) {
            YOUTUBE_SCROLL_LOADING_HOLD_MS
        } else {
            (YOUTUBE_SCROLL_LOADING_HOLD_MS - youtubeElapsedMs).coerceAtLeast(0L)
        }
        return max(baseRemainingMs, youtubeRemainingMs)
    }

    private fun isInOverlayStabilizationWindow(): Boolean {
        return isInScrollStabilizationWindow() || remainingOverlayContentStabilizationMs() > 0L
    }

    private fun remainingOverlayStabilizationMs(): Long {
        return max(remainingScrollStabilizationMs(), remainingOverlayContentStabilizationMs())
    }

    private fun remainingOverlayContentStabilizationMs(): Long {
        if (lastOverlayContentChangeAtMs <= 0L) return 0L

        val elapsedMs = SystemClock.uptimeMillis() - lastOverlayContentChangeAtMs
        if (elapsedMs < 0L) return CONTENT_OVERLAY_STABILIZATION_MS

        return (CONTENT_OVERLAY_STABILIZATION_MS - elapsedMs).coerceAtLeast(0L)
    }

    private fun buildParseCandidateComputation(
        packageName: String,
        nodes: List<ParsedTextNode>,
        experimentMode: PipelineExperimentMode,
        sceneRevision: Long,
        screenWidth: Int,
        screenHeight: Int
    ): ParseCandidateComputation {
        val startedAtMs = SystemClock.uptimeMillis()
        return try {
            val visualPlanFuture = parseComputeExecutor.submit<TimedVisualRoiPlan> {
                val stageStartedAtMs = SystemClock.uptimeMillis()
                if (experimentMode.ocrStageEnabled) {
                    TimedVisualRoiPlan(
                        plan = buildVisualTextRoiPlan(nodes, experimentMode),
                        elapsedMs = SystemClock.uptimeMillis() - stageStartedAtMs
                    )
                } else {
                    TimedVisualRoiPlan(
                        plan = VisualTextRoiPlan(rois = emptyList(), candidateCount = 0),
                        elapsedMs = SystemClock.uptimeMillis() - stageStartedAtMs
                    )
                }
            }
            val candidateFuture = parseComputeExecutor.submit<TimedScreenCandidates> {
                val stageStartedAtMs = SystemClock.uptimeMillis()
                TimedScreenCandidates(
                    candidates = extractScreenTextCandidates(
                        packageName = packageName,
                        nodes = nodes,
                        experimentMode = experimentMode,
                        sceneRevision = sceneRevision,
                        screenWidth = screenWidth,
                        screenHeight = screenHeight
                    ),
                    elapsedMs = SystemClock.uptimeMillis() - stageStartedAtMs
                )
            }
            val visualPlan = visualPlanFuture.get()
            val screenCandidates = candidateFuture.get()

            ParseCandidateComputation(
                visualRoiPlan = visualPlan.plan,
                screenCandidates = screenCandidates.candidates,
                visualRoiPlanningMs = visualPlan.elapsedMs,
                screenCandidateExtractionMs = screenCandidates.elapsedMs,
                parallelWaitMs = SystemClock.uptimeMillis() - startedAtMs
            )
        } catch (error: Exception) {
            if (error is InterruptedException) {
                Thread.currentThread().interrupt()
            }
            Log.w(TAG, "parallel candidate computation failed; falling back to sequential", error)
            val visualPlanStartedAtMs = SystemClock.uptimeMillis()
            val visualRoiPlan = if (experimentMode.ocrStageEnabled) {
                buildVisualTextRoiPlan(nodes, experimentMode)
            } else {
                VisualTextRoiPlan(rois = emptyList(), candidateCount = 0)
            }
            val visualRoiPlanningMs = SystemClock.uptimeMillis() - visualPlanStartedAtMs
            val screenCandidateStartedAtMs = SystemClock.uptimeMillis()
            val screenCandidates = extractScreenTextCandidates(
                packageName = packageName,
                nodes = nodes,
                experimentMode = experimentMode,
                sceneRevision = sceneRevision,
                screenWidth = screenWidth,
                screenHeight = screenHeight
            )
            val screenCandidateExtractionMs = SystemClock.uptimeMillis() - screenCandidateStartedAtMs
            ParseCandidateComputation(
                visualRoiPlan = visualRoiPlan,
                screenCandidates = screenCandidates,
                visualRoiPlanningMs = visualRoiPlanningMs,
                screenCandidateExtractionMs = screenCandidateExtractionMs,
                parallelWaitMs = SystemClock.uptimeMillis() - startedAtMs
            )
        }
    }

    private fun extractScreenTextCandidates(
        packageName: String,
        nodes: List<ParsedTextNode>,
        experimentMode: PipelineExperimentMode,
        sceneRevision: Long,
        screenWidth: Int?,
        screenHeight: Int?
    ): List<ScreenTextCandidate> {
        return when (experimentMode.candidateCollectionStrategy) {
            CandidateCollectionStrategy.ALL_VISIBLE_TEXT ->
                ScreenTextCandidateExtractor.extractAllVisibleTextCandidates(
                    packageName = packageName,
                    nodes = nodes,
                    sceneRevision = sceneRevision
                )
            CandidateCollectionStrategy.OPTIMIZED ->
                ScreenTextCandidateExtractor.extractCandidates(
                    packageName = packageName,
                    nodes = nodes,
                    sceneRevision = sceneRevision,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight
                )
        }
    }

    private fun handleAttachedYoutubeCommentMotionEvent(
        event: AccessibilityEvent,
        packageName: String
    ): Boolean {
        if (packageName != YOUTUBE_PACKAGE) return false
        if (!youtubeCommentInitialAnalysisCompleted) return false
        if (youtubeKnownHarmfulComments.isEmpty()) return false
        if (
            event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return false
        }

        val reason = if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            "fast-scroll"
        } else {
            "fast-content"
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            lastScrollEventAtMs = SystemClock.uptimeMillis()
            recordYoutubeUserScrollForAutoPrecheck(packageName)
            val scrollTranslation = translateMaskOverlayForScroll(event)
            if (scrollTranslation.translated) {
                markOverlayRevisionStale()
            }
        }
        refreshYoutubeReplyAnchoredMasks(reason = reason, allowRebind = true)
        markOverlayRevisionStale()
        scheduleDeferredFollowUpParse(waitForScrollStabilization = true)
        return true
    }

    private fun rememberYoutubeHarmfulCommentAnchor(
        results: List<AndroidAnalysisResultItem>,
        fallbackSpecs: List<MaskOverlaySpec> = lastYoutubeBlockedSpecs
    ): Boolean {
        val harmfulResults = results.filter { result -> result.isOffensive }
        val specsByResultIndex = fallbackSpecs.mapNotNull { spec ->
            val resultIndex = spec.debugSource.substringAfterLast(':').toIntOrNull()
            resultIndex?.let { index -> index to spec }
        }.toMap()

        harmfulResults.forEachIndexed { index, result ->
            val bounds = result.boundsInScreen
            val spec = specsByResultIndex[index] ?: fallbackSpecs.getOrNull(index) ?: MaskOverlaySpec(
                left = bounds.left.coerceAtLeast(0),
                top = bounds.top.coerceAtLeast(0),
                width = (bounds.right - bounds.left).coerceAtLeast(1),
                height = (bounds.bottom - bounds.top).coerceAtLeast(1),
                label = "comment-blocked",
                allowScrollTranslation = true,
                debugSource = "youtube-comment-blocked-anchor-fallback:$index",
                style = MaskOverlayStyle.BLOCKED
            )
            val directAuthorLabel = youtubeCommentAuthorLabel(result.authorId)
            val inferredAnchor = if (directAuthorLabel == null) {
                inferVisibleYoutubeReplyAnchor(spec)
            } else {
                null
            }
            val authorLabel = directAuthorLabel ?: inferredAnchor?.authorLabel
            val key = youtubeHarmfulCommentKey(result, authorLabel)
            val existing = youtubeKnownHarmfulComments[key]
            if (existing == null) {
                val anchoredMask = inferredAnchor?.match?.let { match ->
                    YoutubeReplyAnchorPlanner.anchor(
                        spec = spec,
                        rowBounds = match.rowBounds,
                        replyBounds = match.replyBounds,
                        screenWidth = resources.displayMetrics.widthPixels,
                        screenHeight = resources.displayMetrics.heightPixels
                    )
                }
                youtubeKnownHarmfulComments[key] = YoutubeKnownHarmfulComment(
                    key = key,
                    authorLabel = authorLabel,
                    fallbackSpec = spec,
                    anchoredMask = anchoredMask,
                    replyNode = inferredAnchor?.match?.replyNode
                )
                Log.d(
                    TAG,
                    "remember harmful youtube comment key=$key author=$authorLabel " +
                        "inferred=${inferredAnchor != null} anchored=${anchoredMask != null}"
                )
            } else if (existing.anchoredMask == null) {
                existing.fallbackSpec = spec
            }
        }

        return refreshYoutubeReplyAnchoredMasks(reason = "remember", allowRebind = true)
    }

    private fun youtubeHarmfulCommentKey(
        result: AndroidAnalysisResultItem,
        authorLabel: String?
    ): String {
        if (authorLabel != null) return "author:$authorLabel"
        val normalizedText = result.original
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
        return "text:${Integer.toHexString(normalizedText.hashCode())}"
    }

    private fun refreshYoutubeReplyAnchoredMasks(
        reason: String,
        allowRebind: Boolean
    ): Boolean {
        if (youtubeKnownHarmfulComments.isEmpty()) return false

        val metrics = resources.displayMetrics
        val nowMs = SystemClock.uptimeMillis()
        val canRebind = allowRebind &&
            nowMs - lastYoutubeReplyRebindAtMs >= YOUTUBE_REPLY_REBIND_INTERVAL_MS
        if (canRebind) {
            lastYoutubeReplyRebindAtMs = nowMs
        }

        val visibleSpecs = youtubeKnownHarmfulComments.values.mapNotNull { knownComment ->
            val match = resolveYoutubeReplyAnchor(knownComment, canRebind) ?: return@mapNotNull null
            val anchoredMask = knownComment.anchoredMask ?: YoutubeReplyAnchorPlanner.anchor(
                spec = knownComment.fallbackSpec,
                rowBounds = match.rowBounds,
                replyBounds = match.replyBounds,
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels
            )?.also { anchored ->
                knownComment.anchoredMask = anchored
                Log.d(
                    TAG,
                    "bind harmful mask to reply key=${knownComment.key} " +
                        "replyTop=${match.replyBounds.top} topOffset=${anchored.topOffsetFromReply}"
                )
            } ?: return@mapNotNull null

            anchoredMask.specAt(
                replyTop = match.replyBounds.top,
                visibleRowBounds = match.rowBounds,
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels
            )
        }

        val previousVisibleCount = lastYoutubeBlockedSpecs.size
        if (visibleSpecs.isNotEmpty()) {
            val rendered = maskOverlayController.renderDirect(
                specs = visibleSpecs,
                reason = "youtube-reply-anchored"
            )
            if (!rendered) return false
            lastYoutubeCommentPaneSpec = null
            lastYoutubeBlockedSpecs = visibleSpecs
            youtubeLoadingOverlayStartedAtMs = 0L
            provisionalAccessibilityMaskActive = false
            if (previousVisibleCount != visibleSpecs.size) {
                Log.d(
                    TAG,
                    "refresh reply anchored masks reason=$reason visible=${visibleSpecs.size} " +
                        "known=${youtubeKnownHarmfulComments.size}"
                )
            }
            return true
        }

        val activeSpecs = maskOverlayController.activeSpecsSnapshot()
        val hasUnresolvedAnchor = youtubeKnownHarmfulComments.values.any { knownComment ->
            knownComment.authorLabel == null
        }
        val keepFreshFallback = (reason == "remember" || hasUnresolvedAnchor) &&
            activeSpecs.isNotEmpty() &&
            activeSpecs.all { spec -> spec.style == MaskOverlayStyle.BLOCKED } &&
            (
                hasUnresolvedAnchor ||
                    activeSpecs.none { spec -> spec.debugSource.endsWith(":reply-anchor") }
                )
        if (!keepFreshFallback) {
            lastYoutubeBlockedSpecs = emptyList()
        } else if (hasUnresolvedAnchor) {
            lastYoutubeBlockedSpecs = activeSpecs
            Log.d(
                TAG,
                "preserve unresolved youtube masks reason=$reason " +
                    "active=${activeSpecs.size} known=${youtubeKnownHarmfulComments.size}"
            )
        }
        if (
            youtubeCommentInitialAnalysisCompleted &&
            activeSpecs.isNotEmpty() &&
            activeSpecs.all { spec -> spec.style == MaskOverlayStyle.BLOCKED } &&
            !keepFreshFallback
        ) {
            maskOverlayController.clear()
        }
        if (previousVisibleCount > 0) {
            Log.d(
                TAG,
                "hide offscreen reply anchored masks reason=$reason known=${youtubeKnownHarmfulComments.size}"
            )
        }
        return false
    }

    private fun youtubeCommentAuthorLabel(authorId: String?): String? {
        return YoutubeSafeCommentAssembler.youtubeAuthorLabel(authorId)
    }

    private fun resolveYoutubeReplyAnchor(
        knownComment: YoutubeKnownHarmfulComment,
        allowRebind: Boolean
    ): YoutubeReplyAnchorMatch? {
        if (knownComment.authorLabel == null && allowRebind) {
            inferVisibleYoutubeReplyAnchor(knownComment.fallbackSpec)?.let { inferred ->
                knownComment.authorLabel = inferred.authorLabel
                knownComment.replyNode = inferred.match.replyNode
                Log.d(
                    TAG,
                    "late bind harmful comment to reply key=${knownComment.key} " +
                        "author=${inferred.authorLabel}"
                )
                return inferred.match
            }
        }
        val authorLabel = knownComment.authorLabel ?: return null
        val retainedReply = knownComment.replyNode
        if (retainedReply != null) {
            val refreshed = runCatching { retainedReply.refresh() }.getOrDefault(false)
            if (refreshed) {
                findYoutubeCommentRowForReply(retainedReply, authorLabel)?.let { match ->
                    return match
                }
            }
            knownComment.replyNode = null
            Log.d(TAG, "drop recycled youtube reply anchor key=${knownComment.key}")
        }
        if (!allowRebind) return null

        return findVisibleYoutubeReplyAnchor(
            label = authorLabel,
            fallbackSpec = knownComment.fallbackSpec
        )?.also { match ->
            knownComment.replyNode = match.replyNode
        }
    }

    private fun findVisibleYoutubeReplyAnchor(
        label: String,
        fallbackSpec: MaskOverlaySpec
    ): YoutubeReplyAnchorMatch? {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let(roots::add)
        windows?.forEach { window ->
            val root = window.root ?: return@forEach
            if (isYoutubeAccessibilityRoot(root)) {
                roots += root
            }
        }

        val seenRoots = mutableSetOf<String>()
        val matches = mutableListOf<YoutubeReplyAnchorMatch>()
        for (root in roots) {
            if (!isYoutubeAccessibilityRoot(root)) continue
            val rootRect = Rect().also { rect -> root.getBoundsInScreen(rect) }
            val rootKey = "${rootRect.left},${rootRect.top},${rootRect.right},${rootRect.bottom},${root.className}"
            if (!seenRoots.add(rootKey)) continue

            val matchingNodes = runCatching {
                root.findAccessibilityNodeInfosByText(label)
            }.getOrDefault(emptyList())
            for (node in matchingNodes) {
                if (!isExactVisibleYoutubeAuthorNode(node, label)) continue
                findYoutubeCommentRowForAuthor(node, label)?.let(matches::add)
            }
        }

        val targetCenter = fallbackSpec.top + fallbackSpec.height / 2
        return matches
            .distinctBy { match ->
                val bounds = match.replyBounds
                "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
            }
            .minByOrNull { match ->
                abs((match.rowBounds.top + match.rowBounds.bottom) / 2 - targetCenter)
            }
    }

    private fun inferVisibleYoutubeReplyAnchor(
        fallbackSpec: MaskOverlaySpec
    ): YoutubeInferredReplyAnchor? {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let(roots::add)
        windows?.forEach { window ->
            val root = window.root ?: return@forEach
            if (isYoutubeAccessibilityRoot(root)) roots += root
        }

        val panelSpec = lastYoutubeScrollLoadingSpec ?: youtubeKnownCommentPanelSpec
        val matches = mutableListOf<YoutubeInferredReplyAnchor>()
        val seenRoots = mutableSetOf<String>()
        val seenAuthors = mutableSetOf<String>()
        val seenReplies = mutableSetOf<String>()
        var visited = 0
        var authorCount = 0
        var replyCount = 0
        var panelRootCount = 0

        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 18 || visited >= 2_400) return
            visited += 1

            val label = visibleYoutubeAuthorLabel(node)
            if (label != null) {
                authorCount += 1
                val authorBounds = youtubeNodeBounds(node)
                val insidePanel = authorBounds != null && (
                    panelSpec == null ||
                        authorBounds.bottom > panelSpec.top &&
                        authorBounds.top < panelSpec.top + panelSpec.height
                    )
                val authorKey = authorBounds?.let { bounds ->
                    "$label@${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
                }
                if (insidePanel && authorKey != null && seenAuthors.add(authorKey)) {
                    findYoutubeCommentRowForAuthor(node, label)?.let { match ->
                        matches += YoutubeInferredReplyAnchor(
                            authorLabel = label,
                            match = match
                        )
                    }
                }
            }

            if (isYoutubeReplyNode(node)) {
                val replyBounds = youtubeNodeBounds(node)
                val replyKey = replyBounds?.let { bounds ->
                    "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
                }
                if (replyKey != null && seenReplies.add(replyKey)) {
                    replyCount += 1
                    findYoutubeCommentRowForReply(node)?.let(matches::add)
                }
            }

            for (index in 0 until node.childCount) {
                val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                visit(child, depth + 1)
            }
        }

        for (root in roots) {
            if (!isYoutubeAccessibilityRoot(root)) continue
            val rootRect = Rect().also { rect -> root.getBoundsInScreen(rect) }
            val rootKey = "${rootRect.left},${rootRect.top},${rootRect.right},${rootRect.bottom},${root.className}"
            if (!seenRoots.add(rootKey)) continue

            val panelRoots = YOUTUBE_COMMENT_PANEL_CONTENT_VIEW_IDS
                .flatMap { viewId ->
                    runCatching {
                        root.findAccessibilityNodeInfosByViewId(viewId)
                    }.getOrDefault(emptyList())
                }
                .filter { node -> node.isVisibleToUser }
                .distinctBy { node ->
                    val bounds = youtubeNodeBounds(node)
                    "${node.viewIdResourceName}:${bounds?.left},${bounds?.top}," +
                        "${bounds?.right},${bounds?.bottom}"
                }
            if (panelRoots.isNotEmpty()) {
                panelRootCount += panelRoots.size
                panelRoots.forEach { panelRoot -> visit(panelRoot, 0) }
            } else {
                visit(root, 0)
            }
        }

        val targetCenter = fallbackSpec.top + fallbackSpec.height / 2
        val selected = matches
            .distinctBy { inferred ->
                val bounds = inferred.match.replyBounds
                "${inferred.authorLabel}@${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
            }
            .minByOrNull { inferred ->
                val row = inferred.match.rowBounds
                abs((row.top + row.bottom) / 2 - targetCenter)
            }
        Log.d(
            TAG,
            "infer youtube reply anchor visited=$visited panelRoots=$panelRootCount " +
                "authors=$authorCount replies=$replyCount matches=${matches.size} " +
                "selected=${selected?.authorLabel}"
        )
        return selected
    }

    private fun visibleYoutubeAuthorLabel(
        node: AccessibilityNodeInfo,
        allowHiddenNode: Boolean = false
    ): String? {
        if (!node.isVisibleToUser && !allowHiddenNode) return null
        return listOfNotNull(
            node.contentDescription?.toString(),
            node.text?.toString()
        )
            .asSequence()
            .map { value -> value.replace(Regex("\\s+"), " ").trim() }
            .firstOrNull { label ->
                label.startsWith('@') &&
                    label.length in 3..96 &&
                    !label.contains('/') &&
                    !label.contains('\\')
            }
    }

    private fun isYoutubeAccessibilityRoot(node: AccessibilityNodeInfo): Boolean {
        val sourcePackage = if (debugYoutubeHarnessActive) {
            applicationContext.packageName
        } else {
            YOUTUBE_PACKAGE
        }
        return node.packageName?.toString() == sourcePackage
    }

    private fun findYoutubeCommentRowForAuthor(
        authorNode: AccessibilityNodeInfo,
        authorLabel: String,
        allowHiddenNodes: Boolean = false
    ): YoutubeReplyAnchorMatch? {
        val authorBounds = youtubeNodeBounds(authorNode, allowHiddenNodes) ?: return null
        var current: AccessibilityNodeInfo? = authorNode
        repeat(YOUTUBE_COMMENT_ROW_MAX_PARENT_DEPTH + 1) {
            val rowNode = current ?: return null
            val replyNode = findYoutubeReplyDescendant(
                node = rowNode,
                allowHiddenNodes = allowHiddenNodes
            )
            if (
                replyNode != null &&
                rowContainsExactYoutubeAuthor(rowNode, authorLabel, allowHiddenNodes)
            ) {
                buildYoutubeReplyAnchorMatch(
                    replyNode = replyNode,
                    rowNode = rowNode,
                    authorBounds = authorBounds,
                    allowHiddenNodes = allowHiddenNodes
                )?.let { return it }
            }
            current = runCatching { rowNode.parent }.getOrNull()
        }
        return null
    }

    private fun findYoutubeCommentRowForReply(
        replyNode: AccessibilityNodeInfo,
        authorLabel: String
    ): YoutubeReplyAnchorMatch? {
        if (!isYoutubeReplyNode(replyNode)) return null
        var current: AccessibilityNodeInfo? = replyNode
        repeat(YOUTUBE_COMMENT_ROW_MAX_PARENT_DEPTH + 1) {
            val rowNode = current ?: return null
            val authorBounds = findExactYoutubeAuthorBounds(rowNode, authorLabel)
            if (authorBounds != null) {
                buildYoutubeReplyAnchorMatch(
                    replyNode = replyNode,
                    rowNode = rowNode,
                    authorBounds = authorBounds
                )?.let { return it }
            }
            current = runCatching { rowNode.parent }.getOrNull()
        }
        return null
    }

    private fun findYoutubeCommentRowForReply(
        replyNode: AccessibilityNodeInfo
    ): YoutubeInferredReplyAnchor? {
        if (!isYoutubeReplyNode(replyNode)) return null
        val replyBounds = youtubeNodeBounds(replyNode) ?: return null
        var current: AccessibilityNodeInfo? = replyNode
        repeat(YOUTUBE_COMMENT_ROW_MAX_PARENT_DEPTH + 1) {
            val rowNode = current ?: return null
            findVisibleYoutubeAuthors(rowNode)
                .filter { (_, bounds) -> bounds.bottom < replyBounds.top }
                .minByOrNull { (_, bounds) -> replyBounds.top - bounds.bottom }
                ?.let { (authorLabel, authorBounds) ->
                    buildYoutubeReplyAnchorMatch(
                        replyNode = replyNode,
                        rowNode = rowNode,
                        authorBounds = authorBounds
                    )?.let { match ->
                        return YoutubeInferredReplyAnchor(
                            authorLabel = authorLabel,
                            match = match
                        )
                    }
                }
            current = runCatching { rowNode.parent }.getOrNull()
        }
        return null
    }

    private fun findVisibleYoutubeAuthors(
        rowNode: AccessibilityNodeInfo
    ): List<Pair<String, BoundsRect>> {
        val authors = mutableListOf<Pair<String, BoundsRect>>()
        var visited = 0
        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > YOUTUBE_COMMENT_ROW_MAX_SEARCH_DEPTH || visited >= 128) {
                return
            }
            visited += 1
            val label = visibleYoutubeAuthorLabel(node)
            val bounds = label?.let { youtubeNodeBounds(node) }
            if (label != null && bounds != null) {
                authors += label to bounds
            }
            for (index in 0 until node.childCount) {
                val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                visit(child, depth + 1)
            }
        }
        visit(rowNode, 0)
        return authors.distinctBy { (label, bounds) ->
            "$label@${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
        }
    }

    private fun buildYoutubeReplyAnchorMatch(
        replyNode: AccessibilityNodeInfo,
        rowNode: AccessibilityNodeInfo,
        authorBounds: BoundsRect,
        allowHiddenNodes: Boolean = false
    ): YoutubeReplyAnchorMatch? {
        val replyBounds = youtubeNodeBounds(replyNode, allowHiddenNodes) ?: return null
        val rawRowBounds = youtubeNodeBounds(rowNode, allowHiddenNodes) ?: return null
        if (
            authorBounds.left < rawRowBounds.left - 8 ||
            authorBounds.right > rawRowBounds.right + 8 ||
            authorBounds.top < rawRowBounds.top - 8 ||
            authorBounds.bottom > rawRowBounds.bottom + 8
        ) {
            return null
        }
        if (replyBounds.top <= authorBounds.bottom + 8) return null
        val rowBounds = BoundsRect(
            left = rawRowBounds.left,
            top = maxOf(rawRowBounds.top, authorBounds.top),
            right = rawRowBounds.right,
            bottom = minOf(rawRowBounds.bottom, replyBounds.bottom + 8)
        )
        val metrics = resources.displayMetrics
        val rowWidth = rowBounds.right - rowBounds.left
        val rowHeight = rowBounds.bottom - rowBounds.top
        if (rowWidth < (metrics.widthPixels * 0.65f).toInt()) return null
        if (rowHeight > (metrics.heightPixels * 0.45f).toInt()) return null
        if (replyBounds.top <= rowBounds.top + 48) return null
        if (replyBounds.bottom > rowBounds.bottom + 8) return null
        if (replyBounds.left < rowBounds.left - 8 || replyBounds.right > rowBounds.right + 8) {
            return null
        }

        val panel = lastYoutubeScrollLoadingSpec ?: youtubeKnownCommentPanelSpec
        if (
            panel != null &&
            (
                rowBounds.bottom <= panel.top ||
                    rowBounds.top >= panel.top + panel.height
                )
        ) {
            return null
        }
        return YoutubeReplyAnchorMatch(
            replyNode = replyNode,
            replyBounds = replyBounds,
            authorBounds = authorBounds,
            rowBounds = rowBounds
        )
    }

    private fun findYoutubeReplyDescendant(
        node: AccessibilityNodeInfo,
        depth: Int = 0,
        allowHiddenNodes: Boolean = false
    ): AccessibilityNodeInfo? {
        if (depth > YOUTUBE_COMMENT_ROW_MAX_SEARCH_DEPTH) return null
        if (isYoutubeReplyNode(node, allowHiddenNodes)) return node
        for (index in 0 until node.childCount) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            findYoutubeReplyDescendant(child, depth + 1, allowHiddenNodes)?.let { return it }
        }
        return null
    }

    private fun isYoutubeReplyNode(
        node: AccessibilityNodeInfo,
        allowHiddenNode: Boolean = false
    ): Boolean {
        if ((!node.isVisibleToUser && !allowHiddenNode) || !node.isClickable) return false
        val labels = listOfNotNull(
            node.contentDescription?.toString(),
            node.text?.toString()
        ).map { value -> value.trim().lowercase() }
        return labels.any { label ->
            label == "reply" ||
                label == "\uB2F5\uAE00" ||
                label == "\uB2F5\uAE00 \uB2EC\uAE30"
        }
    }

    private fun rowContainsExactYoutubeAuthor(
        rowNode: AccessibilityNodeInfo,
        authorLabel: String,
        allowHiddenNodes: Boolean = false
    ): Boolean {
        return findExactYoutubeAuthorBounds(rowNode, authorLabel, allowHiddenNodes) != null
    }

    private fun findExactYoutubeAuthorBounds(
        rowNode: AccessibilityNodeInfo,
        authorLabel: String,
        allowHiddenNodes: Boolean = false
    ): BoundsRect? {
        var visited = 0
        fun visit(node: AccessibilityNodeInfo?, depth: Int): BoundsRect? {
            if (node == null || depth > YOUTUBE_COMMENT_ROW_MAX_SEARCH_DEPTH || visited >= 96) {
                return null
            }
            visited += 1
            if (isExactVisibleYoutubeAuthorNode(node, authorLabel, allowHiddenNodes)) {
                return youtubeNodeBounds(node, allowHiddenNodes)
            }
            for (index in 0 until node.childCount) {
                val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                visit(child, depth + 1)?.let { return it }
            }
            return null
        }
        return visit(rowNode, 0)
    }

    private fun isExactVisibleYoutubeAuthorNode(
        node: AccessibilityNodeInfo,
        authorLabel: String,
        allowHiddenNode: Boolean = false
    ): Boolean {
        if (!node.isVisibleToUser && !allowHiddenNode) return false
        return node.contentDescription?.toString()?.trim() == authorLabel ||
            node.text?.toString()?.trim() == authorLabel
    }

    private fun youtubeNodeBounds(
        node: AccessibilityNodeInfo,
        allowHiddenNode: Boolean = false
    ): BoundsRect? {
        if (!node.isVisibleToUser && !allowHiddenNode) return null
        val rect = Rect()
        runCatching { node.getBoundsInScreen(rect) }.getOrElse { return null }
        if (rect.width() <= 0 || rect.height() <= 0) return null
        return BoundsRect(rect.left, rect.top, rect.right, rect.bottom)
    }

    private fun startYoutubeHarmfulAnchorTracking() {
        if (
            !youtubeCommentInitialAnalysisCompleted ||
            youtubeKnownHarmfulComments.isEmpty()
        ) {
            return
        }

        youtubeAnchorTrackingUntilMs = Long.MAX_VALUE
        val runId = ++youtubeAnchorTrackingRunId
        handler.post { trackYoutubeHarmfulAnchor(runId) }
    }

    private fun finishYoutubeHarmfulAnchorTracking() {
        if (youtubeAnchorTrackingRunId <= 0L) return
        youtubeAnchorTrackingUntilMs = SystemClock.uptimeMillis() + YOUTUBE_ANCHOR_TRACK_TAIL_MS
    }

    private fun stopYoutubeHarmfulAnchorTracking() {
        youtubeAnchorTrackingRunId += 1L
        youtubeAnchorTrackingUntilMs = 0L
    }

    private fun trackYoutubeHarmfulAnchor(runId: Long) {
        if (runId != youtubeAnchorTrackingRunId) return
        if (
            lastObservedPackage != YOUTUBE_PACKAGE ||
            !youtubeCommentInitialAnalysisCompleted ||
            youtubeKnownHarmfulComments.isEmpty()
        ) {
            return
        }

        val nowMs = SystemClock.uptimeMillis()
        if (!youtubeTouchInteractionActive && nowMs > youtubeAnchorTrackingUntilMs) return

        translateYoutubeMaskToCurrentHarmfulAuthor("frame")

        handler.postDelayed(
            { trackYoutubeHarmfulAnchor(runId) },
            YOUTUBE_ANCHOR_TRACK_INTERVAL_MS
        )
    }

    private fun translateYoutubeMaskToCurrentHarmfulAuthor(reason: String): Boolean {
        return refreshYoutubeReplyAnchoredMasks(
            reason = reason,
            allowRebind = reason != "frame"
        )
    }

    private fun syncYoutubeBlockedSpecsFromActiveOverlay() {
        lastYoutubeBlockedSpecs = maskOverlayController.activeSpecsSnapshot()
            .filter { spec ->
                spec.style == MaskOverlayStyle.BLOCKED &&
                    spec.allowScrollTranslation &&
                    spec.debugSource.startsWith("youtube-comment-")
            }
    }

    private fun clearYoutubeHarmfulCommentAnchor() {
        youtubeKnownHarmfulComments.values.forEach { knownComment ->
            knownComment.replyNode = null
        }
        youtubeKnownHarmfulComments.clear()
        lastYoutubeReplyRebindAtMs = 0L
    }

    private fun translateMaskOverlayForScroll(event: AccessibilityEvent): ScrollTranslationResult {
        val hasActiveMasks = maskOverlayController.hasActiveMasks()
        val eventScrollDelta = MaskOverlayEventPolicy.resolveScrollTranslationDelta(
            eventType = event.eventType,
            hasActiveMasks = hasActiveMasks,
            explicitScrollDeltaX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                event.scrollDeltaX
            } else {
                0
            },
            explicitScrollDeltaY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                event.scrollDeltaY
            } else {
                0
            },
            absoluteScrollX = event.scrollX,
            absoluteScrollY = event.scrollY,
            lastAbsoluteScrollX = lastAbsoluteScrollX,
            lastAbsoluteScrollY = lastAbsoluteScrollY
        )
        rememberAbsoluteScrollPosition(event)
        val scrollDelta = eventScrollDelta

        if (scrollDelta == null) {
            return ScrollTranslationResult(
                status = null,
                hasResolvedScrollDelta = false
            )
        }

        val translationStatus = maskOverlayController.translateBy(
            deltaX = scrollDelta.deltaX,
            deltaY = scrollDelta.deltaY
        )
        if (translationStatus == MaskOverlayTranslationStatus.TRANSLATED) {
            if (lastObservedPackage == YOUTUBE_PACKAGE) {
                syncYoutubeBlockedSpecsFromActiveOverlay()
            }
            Log.d(
                TAG,
                "translate mask overlay scroll source=${scrollDelta.source} " +
                    "delta=${scrollDelta.deltaX},${scrollDelta.deltaY}"
            )
        }
        return ScrollTranslationResult(
            status = translationStatus,
            hasResolvedScrollDelta = true
        )
    }


    private fun shouldSuppressYoutubeAutoPrecheckEvent(eventType: Int): Boolean {
        if (!youtubeAutoPrecheckActive && !isYoutubeMirrorCollectionActive()) return false
        return eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
    }

    private fun recordYoutubeUserScrollForAutoPrecheck(packageName: String) {
        if (!YOUTUBE_AUTO_PRECHECK_ENABLED) return
        if (packageName != YOUTUBE_PACKAGE) return
        if (youtubeAutoPrecheckActive) return
        if (SystemClock.uptimeMillis() <= youtubeSyntheticScrollUntilMs) return

        val previousCount = youtubeUserScrollsSincePrecheck
        youtubeUserScrollsSincePrecheck = (youtubeUserScrollsSincePrecheck + 1)
            .coerceAtMost(YOUTUBE_PRECHECK_RETRIGGER_USER_SCROLLS)
        if (
            previousCount < YOUTUBE_PRECHECK_RETRIGGER_USER_SCROLLS &&
            youtubeUserScrollsSincePrecheck >= YOUTUBE_PRECHECK_RETRIGGER_USER_SCROLLS
        ) {
            lastYoutubeAutoPrecheckAnchorKey = null
            Log.d(TAG, "youtube auto precheck retrigger armed after user scrolls=$youtubeUserScrollsSincePrecheck")
            scheduleDeferredFollowUpParse(waitForScrollStabilization = true)
        }
    }

    private fun maybeStartYoutubeAutoPrecheck(
        screenCandidates: List<ScreenTextCandidate>,
        visualRoiPlan: VisualTextRoiPlan = VisualTextRoiPlan(rois = emptyList(), candidateCount = 0)
    ) {
        if (!YOUTUBE_AUTO_PRECHECK_ENABLED) return
        if (youtubeAutoPrecheckActive) return
        if (lastObservedPackage != YOUTUBE_PACKAGE) return

        val anchorKey = youtubeAutoPrecheckAnchorKey(screenCandidates)
            ?: youtubeAutoPrecheckAnchorKey(visualRoiPlan)
            ?: return
        val forcedByUserScrolls = youtubeUserScrollsSincePrecheck >= YOUTUBE_PRECHECK_RETRIGGER_USER_SCROLLS
        val nowMs = SystemClock.uptimeMillis()
        if (!forcedByUserScrolls && anchorKey == lastYoutubeAutoPrecheckAnchorKey) return
        if (!forcedByUserScrolls && nowMs - lastYoutubeAutoPrecheckFinishedAtMs < YOUTUBE_PRECHECK_COOLDOWN_MS) return
        if (findYoutubeCommentScrollNode() == null) {
            Log.d(TAG, "skip youtube auto precheck: comment scroll node not found")
            return
        }

        val runId = youtubeAutoPrecheckRunId + 1L
        youtubeAutoPrecheckRunId = runId
        youtubeAutoPrecheckActive = true
        youtubeAutoPrecheckForwardSteps = 0
        youtubeAutoPrecheckPendingAnalyses = 0
        youtubeAutoPrecheckReturnDone = false
        youtubeUserScrollsSincePrecheck = 0
        lastYoutubeAutoPrecheckAnchorKey = anchorKey
        youtubeSyntheticScrollUntilMs = nowMs + YOUTUBE_PRECHECK_SYNTHETIC_SCROLL_GRACE_MS

        Log.d(TAG, "start youtube auto precheck run=$runId anchor=$anchorKey")
        analyzeCurrentYoutubeViewportForPrecheck(runId, "anchor")
        handler.postDelayed(
            { runYoutubeAutoPrecheckForwardStep(runId) },
            YOUTUBE_PRECHECK_SCROLL_DELAY_MS
        )
        handler.postDelayed(
            { forceFinishYoutubeAutoPrecheck(runId, "timeout") },
            YOUTUBE_PRECHECK_MAX_DURATION_MS
        )
    }

    private fun runYoutubeAutoPrecheckForwardStep(runId: Long) {
        if (!isCurrentYoutubeAutoPrecheck(runId)) return
        if (youtubeAutoPrecheckForwardSteps >= YOUTUBE_PRECHECK_FORWARD_STEPS) {
            runYoutubeAutoPrecheckReturnStep(runId, youtubeAutoPrecheckForwardSteps)
            return
        }

        if (!performYoutubeCommentScroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            Log.d(TAG, "youtube auto precheck forward stopped run=$runId steps=$youtubeAutoPrecheckForwardSteps")
            runYoutubeAutoPrecheckReturnStep(runId, youtubeAutoPrecheckForwardSteps)
            return
        }

        youtubeAutoPrecheckForwardSteps += 1
        youtubeSyntheticScrollUntilMs = SystemClock.uptimeMillis() + YOUTUBE_PRECHECK_SYNTHETIC_SCROLL_GRACE_MS
        val step = youtubeAutoPrecheckForwardSteps
        handler.postDelayed(
            {
                if (!isCurrentYoutubeAutoPrecheck(runId)) return@postDelayed
                analyzeCurrentYoutubeViewportForPrecheck(runId, "forward-$step")
                runYoutubeAutoPrecheckForwardStep(runId)
            },
            YOUTUBE_PRECHECK_SCROLL_DELAY_MS
        )
    }

    private fun runYoutubeAutoPrecheckReturnStep(runId: Long, remainingSteps: Int) {
        if (!isCurrentYoutubeAutoPrecheck(runId)) return
        if (remainingSteps <= 0) {
            youtubeAutoPrecheckReturnDone = true
            maybeFinishYoutubeAutoPrecheck(runId)
            return
        }

        performYoutubeCommentScroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        youtubeSyntheticScrollUntilMs = SystemClock.uptimeMillis() + YOUTUBE_PRECHECK_SYNTHETIC_SCROLL_GRACE_MS
        handler.postDelayed(
            { runYoutubeAutoPrecheckReturnStep(runId, remainingSteps - 1) },
            YOUTUBE_PRECHECK_RETURN_DELAY_MS
        )
    }

    private fun analyzeCurrentYoutubeViewportForPrecheck(runId: Long, label: String) {
        if (!isCurrentYoutubeAutoPrecheck(runId)) return

        val nodes = extractVisibleTextNodesFromYoutubeWindows()
        if (nodes.isEmpty()) return

        val metrics = resources.displayMetrics
        val candidateComputation = buildParseCandidateComputation(
            packageName = YOUTUBE_PACKAGE,
            nodes = nodes,
            experimentMode = currentExperimentMode(),
            sceneRevision = visualSceneRevision,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels
        )
        val commentCandidates = candidateComputation.screenCandidates
            .filter { candidate -> candidate.isYoutubeAutoPrecheckCommentCandidate() }
        val comments = commentCandidates.map { candidate -> candidate.toParsedComment() }
        if (comments.isEmpty()) return

        val snapshot = ParseSnapshot(
            timestamp = System.currentTimeMillis(),
            comments = comments
        )
        youtubeAutoPrecheckPendingAnalyses += 1
        Thread(
            {
                try {
                    val analysis = AndroidAnalysisClient.analyzeSnapshot(applicationContext, snapshot)
                    Log.d(
                        TAG,
                        "youtube auto precheck analyzed run=$runId label=$label " +
                            "comments=${comments.size} offensive=${analysis.offensiveCount} ok=${analysis.ok}"
                    )
                } catch (error: Exception) {
                    Log.w(TAG, "youtube auto precheck analysis failed run=$runId label=$label", error)
                } finally {
                    handler.post {
                        if (runId == youtubeAutoPrecheckRunId) {
                            youtubeAutoPrecheckPendingAnalyses = (youtubeAutoPrecheckPendingAnalyses - 1)
                                .coerceAtLeast(0)
                            maybeFinishYoutubeAutoPrecheck(runId)
                        }
                    }
                }
            },
            "YoutubeAutoPrecheck-$runId-$label"
        ).start()
    }

    private fun maybeFinishYoutubeAutoPrecheck(runId: Long) {
        if (!isCurrentYoutubeAutoPrecheck(runId)) return
        if (!youtubeAutoPrecheckReturnDone || youtubeAutoPrecheckPendingAnalyses > 0) return

        youtubeAutoPrecheckActive = false
        lastYoutubeAutoPrecheckFinishedAtMs = SystemClock.uptimeMillis()
        youtubeSyntheticScrollUntilMs = lastYoutubeAutoPrecheckFinishedAtMs + YOUTUBE_PRECHECK_SYNTHETIC_SCROLL_GRACE_MS
        Log.d(TAG, "finish youtube auto precheck run=$runId steps=$youtubeAutoPrecheckForwardSteps")
        scheduleParse(
            delayMs = CONTENT_OVERLAY_STABILIZATION_MS,
            eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
            replaceExisting = true
        )
    }

    private fun forceFinishYoutubeAutoPrecheck(runId: Long, reason: String) {
        if (!isCurrentYoutubeAutoPrecheck(runId)) return
        Log.d(TAG, "force finish youtube auto precheck run=$runId reason=$reason")
        youtubeAutoPrecheckActive = false
        youtubeAutoPrecheckPendingAnalyses = 0
        youtubeAutoPrecheckReturnDone = true
        lastYoutubeAutoPrecheckFinishedAtMs = SystemClock.uptimeMillis()
        youtubeSyntheticScrollUntilMs = lastYoutubeAutoPrecheckFinishedAtMs + YOUTUBE_PRECHECK_SYNTHETIC_SCROLL_GRACE_MS
        scheduleParse(
            delayMs = CONTENT_OVERLAY_STABILIZATION_MS,
            eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
            replaceExisting = true
        )
    }

    private fun cancelYoutubeAutoPrecheck(reason: String) {
        if (!youtubeAutoPrecheckActive && youtubeAutoPrecheckPendingAnalyses <= 0) return
        youtubeAutoPrecheckRunId += 1L
        youtubeAutoPrecheckActive = false
        youtubeAutoPrecheckForwardSteps = 0
        youtubeAutoPrecheckPendingAnalyses = 0
        youtubeAutoPrecheckReturnDone = false
        Log.d(TAG, "cancel youtube auto precheck reason=$reason")
    }

    private fun isCurrentYoutubeAutoPrecheck(runId: Long): Boolean {
        return youtubeAutoPrecheckActive &&
            youtubeAutoPrecheckRunId == runId &&
            lastObservedPackage == YOUTUBE_PACKAGE
    }

    private fun performYoutubeCommentScroll(action: Int): Boolean {
        val node = if (debugYoutubeHarnessActive) {
            findDebugYoutubeHarnessScrollNode()
        } else {
            findYoutubeCommentScrollNode(action)
        } ?: run {
            Log.w(
                TAG,
                "stop youtube comment collection: native results list not found action=$action"
            )
            return false
        }
        val handled = try {
            node.performAction(action)
        } catch (error: RuntimeException) {
            Log.w(TAG, "youtube comment results scroll failed action=$action", error)
            false
        }
        val rect = Rect().also { node.getBoundsInScreen(it) }
        Log.d(
            TAG,
            "youtube comment results scroll action=$action handled=$handled " +
                "id=${node.viewIdResourceName.orEmpty()} " +
                "bounds=${rect.left},${rect.top},${rect.right},${rect.bottom}"
        )
        return handled
    }

    private fun findYoutubeCommentScrollNode(
        action: Int = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
    ): AccessibilityNodeInfo? {
        youtubeMirrorNativeScrollNode?.let { cachedNode ->
            val refreshed = runCatching { cachedNode.refresh() }.getOrDefault(false)
            val supportsAction = refreshed && cachedNode.actionList.any { candidate ->
                candidate.id == action
            }
            if (refreshed && (cachedNode.isScrollable || supportsAction)) {
                return cachedNode
            }
            youtubeMirrorNativeScrollNode = null
        }
        val paneSpec = youtubeMirrorPanelSpec
            ?: lastYoutubeCommentPaneSpec
            ?: lastYoutubeScrollLoadingSpec
        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { root -> roots.add(root) }
        windows?.forEach { window ->
            window.root?.let { root -> roots.add(root) }
        }
        val youtubeRoots = roots.filter { root -> isYoutubeAccessibilityRoot(root) }
        val mirrorActive = YOUTUBE_SAFE_MIRROR_ENABLED &&
            youtubeSafeCommentMirrorController.isActive
        val trustedCommentScope = paneSpec != null

        fun canReadNode(node: AccessibilityNodeInfo): Boolean {
            return YoutubeMirrorNodeVisibilityPolicy.canReadNode(
                isVisibleToUser = node.isVisibleToUser,
                mirrorActive = mirrorActive,
                trustedCommentScope = trustedCommentScope
            )
        }

        var exactNode: AccessibilityNodeInfo? = null
        var exactArea = -1
        youtubeRoots.forEach { root ->
            val candidates = runCatching {
                root.findAccessibilityNodeInfosByViewId(YOUTUBE_COMMENT_RESULTS_VIEW_ID)
            }.getOrNull().orEmpty()
            candidates.forEach { node ->
                val supportsAction = node.actionList.any { candidate -> candidate.id == action }
                if (!canReadNode(node) || (!node.isScrollable && !supportsAction)) {
                    return@forEach
                }
                val rect = Rect().also { node.getBoundsInScreen(it) }
                if (
                    rect.width() < 240 ||
                    rect.height() < 240 ||
                    (paneSpec != null && !rectIntersectsSpec(rect, paneSpec))
                ) {
                    return@forEach
                }
                val area = rect.width() * rect.height()
                if (area > exactArea) {
                    exactArea = area
                    exactNode = node
                }
            }
        }
        exactNode?.let { node ->
            youtubeMirrorNativeScrollNode = node
            return node
        }

        var bestNode: AccessibilityNodeInfo? = null
        var bestScore = Int.MIN_VALUE
        val seenNodes = mutableSetOf<String>()
        var visitedNodeCount = 0

        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null) return
            if (depth > 24 || visitedNodeCount >= 2_000) return
            visitedNodeCount += 1
            if (!isYoutubeAccessibilityRoot(node)) return
            if (!canReadNode(node)) return

            val rect = Rect().also { node.getBoundsInScreen(it) }
            val nodeKey = "${rect.left},${rect.top},${rect.right},${rect.bottom},${node.className},${node.viewIdResourceName}"
            if (!seenNodes.add(nodeKey)) return

            val supportsAction = node.actionList.any { candidate -> candidate.id == action }
            if (
                (node.isScrollable || supportsAction) &&
                rect.width() >= 240 &&
                rect.height() >= 240
            ) {
                var score = rect.height() + rect.width() / 4
                if (node.viewIdResourceName == YOUTUBE_COMMENT_RESULTS_VIEW_ID) score += 20_000
                if (supportsAction) score += 2_000
                if (paneSpec != null && rectIntersectsSpec(rect, paneSpec)) score += 10_000
                if (node.className?.toString()?.contains("RecyclerView", ignoreCase = true) == true) score += 800
                if (node.viewIdResourceName?.contains("comment", ignoreCase = true) == true) score += 600
                if (paneSpec != null && rect.top <= paneSpec.top + 180 && rect.bottom >= paneSpec.top + 240) score += 500
                if (score > bestScore) {
                    bestScore = score
                    bestNode = node
                }
            }

            for (index in 0 until node.childCount) {
                visit(node.getChild(index), depth + 1)
            }
        }

        youtubeRoots.forEach { root -> visit(root, 0) }
        return bestNode?.also { node -> youtubeMirrorNativeScrollNode = node }
    }

    private fun findDebugYoutubeHarnessScrollNode(): AccessibilityNodeInfo? {
        if (!debugYoutubeHarnessActive) return null
        val root = rootInActiveWindow ?: return null
        if (root.packageName?.toString() != applicationContext.packageName) return null

        var bestNode: AccessibilityNodeInfo? = null
        var bestArea = -1
        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 24) return
            if (node.packageName?.toString() != applicationContext.packageName) return

            val className = node.className?.toString().orEmpty()
            if (className.contains("ScrollView", ignoreCase = true)) {
                val rect = Rect().also { node.getBoundsInScreen(it) }
                val area = rect.width() * rect.height()
                if (rect.width() >= 240 && rect.height() >= 240 && area > bestArea) {
                    bestArea = area
                    bestNode = node
                }
            }
            for (index in 0 until node.childCount) {
                visit(node.getChild(index), depth + 1)
            }
        }

        visit(root, 0)
        return bestNode
    }
    private fun rectIntersectsSpec(rect: Rect, spec: MaskOverlaySpec): Boolean {
        val specRight = spec.left + spec.width
        val specBottom = spec.top + spec.height
        return rect.right > spec.left &&
            rect.left < specRight &&
            rect.bottom > spec.top &&
            rect.top < specBottom
    }

    private fun youtubeAutoPrecheckAnchorKey(candidates: List<ScreenTextCandidate>): String? {
        val comments = candidates
            .filter { candidate -> candidate.isYoutubeAutoPrecheckCommentCandidate() }
            .sortedWith(compareBy<ScreenTextCandidate> { it.screenRect.top }.thenBy { it.screenRect.left })
            .take(4)
        if (comments.isEmpty()) return null

        return comments.joinToString("|") { candidate ->
            val normalized = candidate.rawText.replace(FAST_PROVISIONAL_WHITESPACE_PATTERN, " ")
                .trim()
                .take(32)
            "$normalized@${candidate.screenRect.top}"
        }
    }


    private fun youtubeAutoPrecheckAnchorKey(visualRoiPlan: VisualTextRoiPlan): String? {
        val panelBounds = youtubeCommentPanelBounds(visualRoiPlan)
            .sortedWith(compareBy<BoundsRect> { it.top }.thenBy { it.left })
            .take(4)
        if (panelBounds.isEmpty()) return null

        return panelBounds.joinToString("|") { bounds ->
            "panel:${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
        }
    }

    private fun ScreenTextCandidate.isYoutubeAutoPrecheckCommentCandidate(): Boolean {
        return packageName == YOUTUBE_PACKAGE &&
            route.surface == CandidateSurface.YOUTUBE_COMMENT &&
            route.renderPolicy == CandidateRenderPolicy.DIRECT_OVERLAY
    }

    private fun promoteCachedMasksForCurrentWindow(
        minIntervalMs: Long = CACHE_PROMOTION_THROTTLE_MS
    ) {
        val now = SystemClock.uptimeMillis()
        if (now - lastCachePromotionAtMs < minIntervalMs) return
        lastCachePromotionAtMs = now

        val currentPackage = lastObservedPackage ?: return
        if (!supportsMaskOverlay(currentPackage)) return
        if (usesViewportStableBrowserOverlay(currentPackage)) return
        val experimentMode = currentExperimentMode()
        if (!experimentMode.overlayStageEnabled) return
        if (AnalysisSensitivityStore.get(applicationContext) <= 0) return

        val nodes = when (currentPackage) {
            YOUTUBE_PACKAGE -> extractVisibleTextNodesFromYoutubeWindows()
            INSTAGRAM_PACKAGE -> extractVisibleTextNodesFromInstagramWindows()
            else -> extractVisibleTextNodesFromCurrentWindow(currentPackage)
        }
        if (nodes.isEmpty()) return

        val metrics = resources.displayMetrics
        val screenCandidates = ScreenTextCandidateExtractor.extractCandidates(
            packageName = currentPackage,
            nodes = nodes,
            sceneRevision = visualSceneRevision,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels
        )
        var visualRoiPlan: VisualTextRoiPlan? = null
        fun currentVisualRoiPlan(): VisualTextRoiPlan {
            return visualRoiPlan ?: buildVisualTextRoiPlan(nodes).also { plan ->
                visualRoiPlan = plan
            }
        }

        if (currentPackage == YOUTUBE_PACKAGE) {
            renderYoutubeSkeletonMaskOverlay(
                screenCandidates = screenCandidates,
                visualRoiPlan = currentVisualRoiPlan(),
                timestamp = System.currentTimeMillis(),
                parseStartedAtMs = SystemClock.uptimeMillis()
            )
            if (
                lastYoutubeCommentPaneSpec?.style == MaskOverlayStyle.LOADING &&
                provisionalAccessibilityMaskActive &&
                maskOverlayController.hasActiveMasks()
            ) {
                Log.d(TAG, "keep youtube loading over partial cached harmful results")
                return
            }
        } else {
            val provisionalResponse = ProvisionalAccessibilityMaskBuilder.buildResponse(
                candidates = screenCandidates,
                timestamp = System.currentTimeMillis()
            )
            if (provisionalResponse != null) {
                val currentVisualRoiPlan = currentVisualRoiPlan()
                Log.d(
                    TAG,
                    "promote provisional masks during scroll results=${provisionalResponse.results.size} " +
                        "candidates=${screenCandidates.size}"
                )
                updateMaskOverlay(
                    currentPackage = currentPackage,
                    analysis = AndroidAnalysisAttempt(
                        ok = true,
                        packageName = currentPackage,
                        url = "scroll-provisional",
                        sensitivity = AnalysisSensitivityStore.get(applicationContext),
                        latencyMs = 0L,
                        commentCount = provisionalResponse.results.size,
                        offensiveCount = provisionalResponse.results.size,
                        filteredCount = provisionalResponse.filteredCount,
                        response = provisionalResponse,
                        candidateRouteSamples = CandidateRoutingPolicy.summarize(screenCandidates)
                    ).withOverlayDiagnostics(currentPackage, currentVisualRoiPlan),
                    snapshotOverlayRevision = overlayRevision,
                    visualRoiPlan = currentVisualRoiPlan,
                    isProvisionalAccessibilityMask = true,
                    allowDuringScrollStabilization = true,
                    preserveExistingPreciseVisualMasks = true
                )
            }
        }

        val stableCandidates = screenCandidates.filter { candidate ->
            canPromoteCachedMaskCandidate(candidate)
        }
        if (stableCandidates.isEmpty()) return

        val comments = stableCandidates.map { candidate -> candidate.toParsedComment() }
        if (comments.isEmpty()) return

        val snapshot = ParseSnapshot(
            timestamp = System.currentTimeMillis(),
            comments = comments
        )
        val analysis = AndroidAnalysisClient
            .analyzeSnapshotFromCache(applicationContext, snapshot)
            .copy(packageName = currentPackage)
        if (analysis.offensiveCount <= 0) return

        Log.d(
            TAG,
            "promote cached masks during scroll comments=${analysis.commentCount} " +
                "offensive=${analysis.offensiveCount} stableCandidates=${stableCandidates.size}"
        )
        updateMaskOverlay(
            currentPackage = currentPackage,
            analysis = analysis,
            snapshotOverlayRevision = overlayRevision,
            visualRoiPlan = currentVisualRoiPlan(),
            allowDuringScrollStabilization = true,
            preserveExistingPreciseVisualMasks = true
        )
    }

    private fun canPromoteCachedMaskCandidate(candidate: ScreenTextCandidate): Boolean {
        if (candidate.route.renderPolicy != CandidateRenderPolicy.DIRECT_OVERLAY) return false

        return when (candidate.route.geometryPolicy) {
            CandidateGeometryPolicy.ACCESSIBILITY_EXACT,
            CandidateGeometryPolicy.VISUAL_OCR_EXACT -> true
            CandidateGeometryPolicy.ACCESSIBILITY_ESTIMATED,
            CandidateGeometryPolicy.ACCESSIBILITY_LOOKAHEAD,
            CandidateGeometryPolicy.VISUAL_FALLBACK,
            CandidateGeometryPolicy.ANALYSIS_ONLY -> false
        }
    }

    private fun rememberAbsoluteScrollPosition(event: AccessibilityEvent) {
        MaskOverlayEventPolicy.knownAbsoluteScroll(event.scrollX)?.let { scrollX ->
            lastAbsoluteScrollX = scrollX
        }
        MaskOverlayEventPolicy.knownAbsoluteScroll(event.scrollY)?.let { scrollY ->
            lastAbsoluteScrollY = scrollY
        }
    }

    private fun resetAbsoluteScrollPosition() {
        lastAbsoluteScrollX = null
        lastAbsoluteScrollY = null
    }

    private fun shouldLogRawNodes(): Boolean {
        return Log.isLoggable(TAG, Log.VERBOSE)
    }

    private fun shouldObservePackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (packageName == applicationContext.packageName) return false

        return packageName !in OBSERVATION_EXCLUDED_PACKAGES
    }

    private fun isDebugBuild(): Boolean {
        return applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    private fun resolveObservedPackage(
        eventPackageName: String,
        event: AccessibilityEvent
    ): String {
        if (!isDebugBuild()) return eventPackageName

        val appPackageName = applicationContext.packageName
        if (eventPackageName != appPackageName) {
            val isDefinitiveHarnessExit =
                eventPackageName !in OBSERVATION_EXCLUDED_PACKAGES ||
                    eventPackageName in OVERLAY_EXIT_PACKAGES
            if (
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                isDefinitiveHarnessExit
            ) {
                debugYoutubeHarnessActive = false
                debugInstagramHarnessActive = false
            }
            return eventPackageName
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val eventClassName = event.className?.toString().orEmpty()
            val wasYoutubeActive = debugYoutubeHarnessActive
            val wasInstagramActive = debugInstagramHarnessActive
            if (eventClassName.endsWith("Activity")) {
                debugYoutubeHarnessActive =
                    eventClassName == DEBUG_YOUTUBE_HARNESS_ACTIVITY
                debugInstagramHarnessActive =
                    eventClassName == DEBUG_INSTAGRAM_HARNESS_ACTIVITY
            }

            if (wasYoutubeActive && !debugYoutubeHarnessActive) {
                Log.d(TAG, "leave debug youtube harness")
                rememberYoutubeResumeLoadingGate()
                invalidateYoutubeCommentPanelSession("leave-debug-harness")
                clearMaskOverlay()
            } else if (!wasYoutubeActive && debugYoutubeHarnessActive) {
                Log.d(TAG, "enter debug youtube harness")
            }

            if (wasInstagramActive && !debugInstagramHarnessActive) {
                Log.d(TAG, "leave debug instagram harness")
                invalidateInstagramMirrorSession("leave-debug-harness")
                clearMaskOverlay()
            } else if (!wasInstagramActive && debugInstagramHarnessActive) {
                Log.d(TAG, "enter debug instagram harness")
            }
        }

        return when {
            debugYoutubeHarnessActive -> YOUTUBE_PACKAGE
            debugInstagramHarnessActive -> INSTAGRAM_PACKAGE
            else -> eventPackageName
        }
    }

    private fun supportsMaskOverlay(packageName: String): Boolean {
        return shouldObservePackage(packageName)
    }

    private fun usesViewportStableBrowserOverlay(packageName: String): Boolean {
        return packageName in BROWSER_PACKAGES
    }

    private fun clearOverlayForExitPackageIfNeeded(
        packageName: String,
        eventType: Int
    ) {
        if (
            !MaskOverlayEventPolicy.shouldClearOverlayForExitPackage(
                eventType = eventType,
                isExitPackage = packageName in OVERLAY_EXIT_PACKAGES
            )
        ) {
            return
        }

        val hasActiveMasks = maskOverlayController.hasActiveMasks()
        if (!hasActiveMasks && lastObservedPackage == null) return

        if (lastObservedPackage == YOUTUBE_PACKAGE) {
            rememberYoutubeResumeLoadingGate()
        }
        cancelScheduledParse()
        lastObservedPackage = null
        invalidateYoutubeCommentPanelSession("exit-package:$packageName")
        if (hasActiveMasks) {
            Log.d(TAG, "clear mask overlay after leaving observed app package=$packageName")
        }
        clearMaskOverlay()
    }

    private fun buildVisualTextRoiPlan(
        nodes: List<ParsedTextNode>,
        experimentMode: PipelineExperimentMode = currentExperimentMode()
    ): VisualTextRoiPlan {
        if (!visualCaptureState.supported) {
            return VisualTextRoiPlan(rois = emptyList(), candidateCount = 0)
        }

        val metrics = resources.displayMetrics
        if (experimentMode.visualRoiStrategy == VisualRoiStrategy.FULL_SCREEN) {
            return VisualTextRoiPlan(
                rois = listOf(
                    VisualTextRoi(
                        boundsInScreen = BoundsRect(
                            left = 0,
                            top = 0,
                            right = metrics.widthPixels,
                            bottom = metrics.heightPixels
                        ),
                        source = FULL_SCREEN_BASELINE_SOURCE,
                        priority = 0,
                        reason = "optimization-baseline-full-screen-ocr"
                    )
                ),
                candidateCount = 1
            )
        }

        return VisualTextRoiPlanner.buildPlanFromNodes(
            nodes = nodes,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels
        )
    }

    private fun saveVisualOnlyDiagnostics(
        packageName: String,
        visualRoiPlan: VisualTextRoiPlan,
        experimentMode: PipelineExperimentMode = currentExperimentMode(),
        visualOcrRawCount: Int = 0,
        visualOcrSelectedCount: Int = 0
    ) {
        if (visualRoiPlan.candidateCount <= 0 && visualRoiPlan.rois.isEmpty()) return

        AnalysisDiagnosticsStore.saveAttempt(
            applicationContext,
            AndroidAnalysisClient
                .analyzeSnapshot(
                    applicationContext,
                    ParseSnapshot(
                        timestamp = System.currentTimeMillis(),
                        comments = emptyList()
                    )
                )
                .copy(
                    packageName = packageName,
                    experimentMode = experimentMode.id,
                    experimentStages = experimentMode.stageMask,
                    visualOcrRawCount = visualOcrRawCount,
                    visualOcrSelectedCount = visualOcrSelectedCount,
                    actionableSamples = visualRoiPlan.rois.take(3).map { roi ->
                        "OCR 후보(${roi.source}): ${roi.boundsInScreen.left},${roi.boundsInScreen.top}," +
                            "${roi.boundsInScreen.right},${roi.boundsInScreen.bottom}"
                    }
                )
                .withVisualCaptureDiagnostics(visualRoiPlan)
        )
    }

    private fun shouldRunVisualTextSupplement(
        packageName: String,
        analysis: AndroidAnalysisAttempt,
        visualRoiPlan: VisualTextRoiPlan
    ): Boolean {
        if (packageName == YOUTUBE_PACKAGE && !YOUTUBE_VISUAL_OCR_ENABLED) return false
        if (
            visualRoiPlan.canReuseVisualSupplement() &&
            reusableVisualSupplement(packageName, visualRoiPlan.signature()) != null
        ) {
            return false
        }

        return analysis.ok &&
            visualRoiPlan.rois.isNotEmpty() &&
            visualRoiPlan.hasRenderableVisualRois() &&
            !visualAnalysisInFlight &&
            (packageName == YOUTUBE_PACKAGE || analysis.offensiveCount == 0)
    }

    private fun startVisualTextAnalysis(
        packageName: String,
        visualRoiPlan: VisualTextRoiPlan,
        experimentMode: PipelineExperimentMode = currentExperimentMode(),
        parseStartedAtMs: Long = -1L,
        parseDelayMs: Long = -1L,
        candidateExtractionMs: Long = -1L,
        nodeCollectionMs: Long = -1L,
        candidatePostProcessingMs: Long = -1L,
        candidateComputation: ParseCandidateComputation? = null,
        nodes: List<ParsedTextNode> = emptyList(),
        screenCandidates: List<ScreenTextCandidate> = emptyList(),
        clearExistingOverlay: Boolean = true,
        clearExistingOverlayOnMiss: Boolean = false,
        baseResponse: AndroidAnalysisResponse? = null
    ): Boolean {
        if (!experimentMode.ocrStageEnabled) return false
        if (packageName == YOUTUBE_PACKAGE && !YOUTUBE_VISUAL_OCR_ENABLED) {
            Log.d(TAG, "skip visual OCR: disabled for youtube accessibility collection")
            return false
        }
        if (!supportsMaskOverlay(packageName)) return false
        if (!visualCaptureState.supported) return false
        if (visualRoiPlan.rois.isEmpty()) return false
        if (!visualRoiPlan.hasRenderableVisualRois()) return false
        if (AnalysisSensitivityStore.get(applicationContext) <= 0) return false
        if (visualAnalysisInFlight) {
            followUpParseRequested = true
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false

        val visualSignature = visualRoiPlan.signature()
        if (
            !clearExistingOverlay &&
            MaskOverlayEventPolicy.shouldThrottleRecentVisualRefresh(
                hasActiveMasks = maskOverlayController.hasActiveMasks(),
                currentVisualSignature = visualSignature,
                lastVisualSignature = lastVisualRefreshSignature,
                elapsedSinceLastRefreshMs = SystemClock.uptimeMillis() - lastVisualRefreshCompletedAtMs
            )
        ) {
            Log.d(TAG, "skip visual OCR start: recent visual refresh signature=$visualSignature")
            return false
        }

        val screenshotThrottleDelayMs = remainingScreenshotThrottleDelayMs()
        if (screenshotThrottleDelayMs > 0L) {
            Log.d(TAG, "defer visual OCR screenshot: throttleDelayMs=$screenshotThrottleDelayMs")
            scheduleParse(
                delayMs = screenshotThrottleDelayMs,
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                replaceExisting = true
            )
            return true
        }

        if (clearExistingOverlay) {
            clearMaskOverlay()
        }
        val snapshotOverlayRevision = overlayRevision
        val snapshotVisualSceneRevision = visualSceneRevision
        val visualRunId = visualAnalysisRunId + 1L
        visualAnalysisRunId = visualRunId
        visualAnalysisInFlight = true
        lastVisualAnalysisStartedAtMs = SystemClock.uptimeMillis()
        val metrics = resources.displayMetrics
        val semanticFallbackCandidates = VisualTextSemanticFallbackPlanner.selectCandidates(
            visualRoiPlan = visualRoiPlan,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            baseResponse = baseResponse
        )
        val earlyRenderableSemanticFallbackCandidates = semanticFallbackCandidates
            .filterNot { candidate ->
                candidate.visualOcrSource() in setOf(
                    "youtube-visible-band",
                    "youtube-semantic-card"
                )
            }
        if (experimentMode.overlayStageEnabled && earlyRenderableSemanticFallbackCandidates.isNotEmpty()) {
            renderProvisionalVisualMaskOverlay(
                packageName = packageName,
                visualRoiPlan = visualRoiPlan,
                selectedOcrCandidates = earlyRenderableSemanticFallbackCandidates,
                baseResponse = baseResponse,
                parseDelayMs = parseDelayMs,
                candidateExtractionMs = candidateExtractionMs,
                nodeCollectionMs = nodeCollectionMs,
                candidatePostProcessingMs = candidatePostProcessingMs,
                experimentMode = experimentMode,
                candidateComputation = candidateComputation,
                nodes = nodes,
                screenCandidates = screenCandidates,
                visualStartedAtMs = lastVisualAnalysisStartedAtMs,
                visualOcrLatencyMs = -1L,
                snapshotOverlayRevision = snapshotOverlayRevision,
                snapshotVisualSceneRevision = snapshotVisualSceneRevision,
                visualRunId = visualRunId
            )
        }
        Log.d(
            TAG,
            "start visual OCR rois=${visualRoiPlan.rois.size} " +
                "semanticFallback=${semanticFallbackCandidates.size} signature=${visualRoiPlan.signature()}"
        )
        handler.postDelayed(
            {
                timeoutVisualAnalysis(
                    visualRunId = visualRunId,
                    packageName = packageName,
                    visualRoiPlan = visualRoiPlan,
                    snapshotVisualSceneRevision = snapshotVisualSceneRevision,
                    clearExistingOverlayOnMiss = clearExistingOverlayOnMiss
                )
            },
            if (
                YOUTUBE_SAFE_MIRROR_ENABLED &&
                packageName == YOUTUBE_PACKAGE &&
                visualRoiPlan.hasYoutubeCommentPanelRoi()
            ) {
                YOUTUBE_MIRROR_VISUAL_ANALYSIS_TIMEOUT_MS
            } else {
                VISUAL_ANALYSIS_TIMEOUT_MS
            }
        )

        val youtubeMirrorSuspendedForCapture =
            !YOUTUBE_SAFE_MIRROR_ENABLED &&
                packageName == YOUTUBE_PACKAGE &&
                visualRoiPlan.hasYoutubeCommentPanelRoi() &&
                youtubeSafeCommentMirrorController.suspendForCapture()
        val youtubeLoadingSpecHiddenForCapture = if (
            !YOUTUBE_SAFE_MIRROR_ENABLED &&
            packageName == YOUTUBE_PACKAGE &&
            !youtubeCommentInitialAnalysisCompleted &&
            visualRoiPlan.hasYoutubeCommentPanelRoi()
        ) {
            maskOverlayController.activeSpecsSnapshot()
                .firstOrNull { spec -> spec.style == MaskOverlayStyle.LOADING }
        } else {
            null
        }
        if (youtubeLoadingSpecHiddenForCapture != null) {
            maskOverlayController.clear()
            Log.d(TAG, "temporarily hide youtube loading overlay for OCR capture")
        }
        fun restoreYoutubeLoadingAfterCapture() {
            handler.post {
                youtubeSafeCommentMirrorController.restoreAfterCapture(
                    youtubeMirrorSuspendedForCapture
                )
                val loadingSpec = youtubeLoadingSpecHiddenForCapture ?: return@post
                if (
                    lastObservedPackage == YOUTUBE_PACKAGE &&
                    !youtubeCommentInitialAnalysisCompleted &&
                    visualAnalysisRunId == visualRunId
                ) {
                    maskOverlayController.renderDirect(
                        specs = listOf(loadingSpec),
                        reason = "youtube-ocr-capture-restore"
                    )
                }
            }
        }

        val requestScreenshot = Runnable {
            if (
                packageName == YOUTUBE_PACKAGE &&
                YOUTUBE_SAFE_MIRROR_ENABLED &&
                youtubeSafeCommentMirrorController.isActive
            ) {
                restoreYoutubeLoadingAfterCapture()
                finishVisualAnalysis(visualRunId)
                return@Runnable
            }
            if (isVisualAnalysisStale(visualRunId, snapshotVisualSceneRevision)) {
                restoreYoutubeLoadingAfterCapture()
                finishVisualAnalysis(visualRunId)
                return@Runnable
            }
            try {
                lastScreenshotRequestAtMs = SystemClock.uptimeMillis()
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                visualExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        if (isVisualAnalysisStale(visualRunId, snapshotVisualSceneRevision)) {
                            restoreYoutubeLoadingAfterCapture()
                            finishVisualAnalysis(visualRunId)
                            return
                        }

                        val screenshot = screenshotResult.toSoftwareBitmap()

                        restoreYoutubeLoadingAfterCapture()
                        if (screenshot == null) {
                            saveVisualFailureDiagnostics(
                                packageName = packageName,
                                visualRoiPlan = visualRoiPlan,
                                error = "SCREENSHOT_BITMAP_UNAVAILABLE"
                            )
                            if (clearExistingOverlayOnMiss && semanticFallbackCandidates.isEmpty()) {
                                clearMaskOverlayAfterVisualMiss(
                                    packageName = packageName,
                                    visualRoiPlan = visualRoiPlan,
                                    visualRunId = visualRunId,
                                    snapshotVisualSceneRevision = snapshotVisualSceneRevision
                                )
                            }
                            finishVisualAnalysis(visualRunId)
                            return
                        }

                        val screenshotWidth = screenshot.width
                        val screenshotHeight = screenshot.height
                        visualTextOcrProcessor.recognize(screenshot, visualRoiPlan.rois) { ocrCandidates ->
                            if (!screenshot.isRecycled) {
                                screenshot.recycle()
                            }

                            if (isVisualAnalysisStale(visualRunId, snapshotVisualSceneRevision)) {
                                finishVisualAnalysis(visualRunId)
                                return@recognize
                            }

                            analyzeVisualTextCandidates(
                                packageName = packageName,
                                visualRoiPlan = visualRoiPlan,
                                ocrCandidates = ocrCandidates,
                                experimentMode = experimentMode,
                                candidateComputation = candidateComputation,
                                nodes = nodes,
                                screenCandidates = screenCandidates,
                                screenWidth = screenshotWidth,
                                screenHeight = screenshotHeight,
                                parseStartedAtMs = parseStartedAtMs,
                                parseDelayMs = parseDelayMs,
                                candidateExtractionMs = candidateExtractionMs,
                                nodeCollectionMs = nodeCollectionMs,
                                candidatePostProcessingMs = candidatePostProcessingMs,
                                visualStartedAtMs = lastVisualAnalysisStartedAtMs,
                                visualOcrLatencyMs = SystemClock.uptimeMillis() - lastVisualAnalysisStartedAtMs,
                                snapshotOverlayRevision = snapshotOverlayRevision,
                                snapshotVisualSceneRevision = snapshotVisualSceneRevision,
                                baseResponse = baseResponse,
                                semanticFallbackCandidates = semanticFallbackCandidates,
                                clearExistingOverlayOnMiss = clearExistingOverlayOnMiss,
                                visualRunId = visualRunId
                            )
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        restoreYoutubeLoadingAfterCapture()
                        val retryDelayMs = screenshotFailureRetryDelayMs(errorCode)
                        saveVisualFailureDiagnostics(
                            packageName = packageName,
                            visualRoiPlan = visualRoiPlan,
                            error = "SCREENSHOT_FAILED_$errorCode"
                        )
                        if (clearExistingOverlayOnMiss && semanticFallbackCandidates.isEmpty()) {
                            clearMaskOverlayAfterVisualMiss(
                                packageName = packageName,
                                visualRoiPlan = visualRoiPlan,
                                visualRunId = visualRunId,
                                snapshotVisualSceneRevision = snapshotVisualSceneRevision
                            )
                        }
                        finishVisualAnalysis(visualRunId, retryDelayMs = retryDelayMs)
                    }
                }
            )
        } catch (error: RuntimeException) {
            restoreYoutubeLoadingAfterCapture()
            Log.w(TAG, "visual text screenshot request failed", error)
            saveVisualFailureDiagnostics(
                packageName = packageName,
                visualRoiPlan = visualRoiPlan,
                error = error.javaClass.simpleName.takeIf { it.isNotBlank() }
                    ?: "SCREENSHOT_REQUEST_FAILED"
            )
            if (clearExistingOverlayOnMiss && semanticFallbackCandidates.isEmpty()) {
                clearMaskOverlayAfterVisualMiss(
                    packageName = packageName,
                    visualRoiPlan = visualRoiPlan,
                    visualRunId = visualRunId,
                    snapshotVisualSceneRevision = snapshotVisualSceneRevision
                )
            }
                finishVisualAnalysis(visualRunId)
            }
        }

        if (youtubeMirrorSuspendedForCapture || youtubeLoadingSpecHiddenForCapture != null) {
            handler.postDelayed(requestScreenshot, YOUTUBE_MIRROR_CAPTURE_SETTLE_MS)
        } else {
            requestScreenshot.run()
        }
        return true
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun ScreenshotResult.toSoftwareBitmap(): Bitmap? {
        val hardwareBuffer = hardwareBuffer
        return try {
            val wrapped = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
            wrapped?.copy(Bitmap.Config.ARGB_8888, false)
        } catch (error: RuntimeException) {
            Log.w(TAG, "failed to convert screenshot to bitmap", error)
            null
        } finally {
            hardwareBuffer.close()
        }
    }

    private fun analyzeVisualTextCandidates(
        packageName: String,
        visualRoiPlan: VisualTextRoiPlan,
        ocrCandidates: List<ParsedComment>,
        experimentMode: PipelineExperimentMode,
        candidateComputation: ParseCandidateComputation?,
        nodes: List<ParsedTextNode>,
        screenCandidates: List<ScreenTextCandidate>,
        screenWidth: Int,
        screenHeight: Int,
        parseStartedAtMs: Long,
        parseDelayMs: Long,
        candidateExtractionMs: Long,
        nodeCollectionMs: Long,
        candidatePostProcessingMs: Long,
        visualStartedAtMs: Long,
        visualOcrLatencyMs: Long,
        snapshotOverlayRevision: Long,
        snapshotVisualSceneRevision: Long,
        baseResponse: AndroidAnalysisResponse?,
        semanticFallbackCandidates: List<ParsedComment> = emptyList(),
        clearExistingOverlayOnMiss: Boolean,
        visualRunId: Long
    ) {
        Thread {
            try {
                if (isVisualAnalysisStale(visualRunId, snapshotVisualSceneRevision)) return@Thread

                val selectedOcrCandidates = selectVisualTextCandidates(
                    ocrCandidates = ocrCandidates,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    baseResponse = baseResponse
                )
                val selectedVisualCandidates = mergeVisualCandidateSelections(
                    selectedOcrCandidates = selectedOcrCandidates,
                    semanticFallbackCandidates = semanticFallbackCandidates
                )
                if (selectedVisualCandidates.isEmpty()) {
                    Log.d(
                        TAG,
                        "visual OCR candidates selected=0 raw=${ocrCandidates.size} " +
                            "semanticFallback=${semanticFallbackCandidates.size} " +
                            "base=${baseResponse?.results?.size ?: 0}"
                    )
                    markVisualRefreshCompleted(visualRoiPlan)
                    saveVisualOnlyDiagnostics(
                        packageName = packageName,
                        visualRoiPlan = visualRoiPlan,
                        visualOcrRawCount = ocrCandidates.size,
                        visualOcrSelectedCount = 0
                    )

                    if (clearExistingOverlayOnMiss) {
                        clearMaskOverlayAfterVisualMiss(
                            packageName = packageName,
                            visualRoiPlan = visualRoiPlan,
                            visualRunId = visualRunId,
                            snapshotVisualSceneRevision = snapshotVisualSceneRevision
                        )
                    }
                    return@Thread
                }
                Log.d(
                    TAG,
                    "visual OCR candidates selected=${selectedVisualCandidates.size} " +
                        "raw=${ocrCandidates.size} semanticFallback=${semanticFallbackCandidates.size}"
                )
                markVisualRefreshCompleted(visualRoiPlan)
                if (experimentMode.overlayStageEnabled) {
                    renderProvisionalVisualMaskOverlay(
                        packageName = packageName,
                        visualRoiPlan = visualRoiPlan,
                        selectedOcrCandidates = selectedVisualCandidates,
                        baseResponse = baseResponse,
                        parseDelayMs = parseDelayMs,
                        candidateExtractionMs = candidateExtractionMs,
                        nodeCollectionMs = nodeCollectionMs,
                        candidatePostProcessingMs = candidatePostProcessingMs,
                        experimentMode = experimentMode,
                        candidateComputation = candidateComputation,
                        nodes = nodes,
                        screenCandidates = screenCandidates,
                        parseStartedAtMs = parseStartedAtMs,
                        visualStartedAtMs = visualStartedAtMs,
                        visualOcrLatencyMs = visualOcrLatencyMs,
                        snapshotOverlayRevision = snapshotOverlayRevision,
                        snapshotVisualSceneRevision = snapshotVisualSceneRevision,
                        visualRunId = visualRunId
                    )
                }

                if (!experimentMode.backendStageEnabled) {
                    AnalysisDiagnosticsStore.saveAttempt(
                        applicationContext,
                        AndroidAnalysisAttempt(
                            ok = true,
                            packageName = packageName,
                            url = "experiment-${experimentMode.id}",
                            sensitivity = AnalysisSensitivityStore.get(applicationContext),
                            latencyMs = 0L,
                            parseDelayMs = parseDelayMs,
                            candidateExtractionMs = candidateExtractionMs,
                            nodeCollectionMs = nodeCollectionMs,
                            visualRoiPlanningMs = candidateComputation?.visualRoiPlanningMs ?: -1L,
                            screenCandidateExtractionMs = candidateComputation?.screenCandidateExtractionMs ?: -1L,
                            candidatePostProcessingMs = candidatePostProcessingMs,
                            riskGateMaskMs = if (parseStartedAtMs > 0L) {
                                recentRiskGateMaskMs(parseStartedAtMs)
                            } else {
                                -1L
                            },
                            riskGateEventAgeMs = if (parseStartedAtMs > 0L) {
                                recentRiskGateEventAgeMs(parseStartedAtMs)
                            } else {
                                -1L
                            },
                            riskGateReceiveToMaskMs = if (parseStartedAtMs > 0L) {
                                recentRiskGateReceiveToMaskMs(parseStartedAtMs)
                            } else {
                                -1L
                            },
                            fastProvisionalMaskMs = if (parseStartedAtMs > 0L) {
                                recentFastProvisionalMaskMs(parseStartedAtMs)
                            } else {
                                -1L
                            },
                            visualOcrLatencyMs = visualOcrLatencyMs,
                            visualMaskLatencyMs = if (visualStartedAtMs > 0L) {
                                SystemClock.uptimeMillis() - visualStartedAtMs
                            } else {
                                -1L
                            },
                            commentCount = selectedVisualCandidates.size,
                            offensiveCount = 0,
                            filteredCount = 0,
                            visualOcrRawCount = ocrCandidates.size,
                            visualOcrSelectedCount = selectedVisualCandidates.size
                        )
                            .withPipelineDiagnostics(
                                experimentMode = experimentMode,
                                nodeCount = nodes.size,
                                screenCandidateCount = screenCandidates.size,
                                charLocationNodeCount = nodes.count { node -> node.charBoxes.isNotEmpty() },
                                charRangeCandidateCount = screenCandidates.count { candidate ->
                                    candidate.backendSourceId.orEmpty().startsWith("android-accessibility-char-range:")
                                },
                                candidateParallelWaitMs = candidateComputation?.parallelWaitMs ?: -1L,
                                nodeCollectionMs = nodeCollectionMs,
                                visualRoiPlanningMs = candidateComputation?.visualRoiPlanningMs ?: -1L,
                                screenCandidateExtractionMs = candidateComputation?.screenCandidateExtractionMs ?: -1L,
                                candidatePostProcessingMs = candidatePostProcessingMs
                            )
                            .withVisualCaptureDiagnostics(visualRoiPlan)
                    )
                    return@Thread
                }

                val snapshot = ParseSnapshot(
                    timestamp = System.currentTimeMillis(),
                    comments = selectedVisualCandidates
                )
                val rawAnalysis = AndroidAnalysisClient
                    .analyzeSnapshot(applicationContext, snapshot)
                    .copy(packageName = packageName)
                if (isVisualAnalysisStale(visualRunId, snapshotVisualSceneRevision)) return@Thread

                val currentSensitivity = AnalysisSensitivityStore.get(applicationContext)
                if (rawAnalysis.sensitivity != null && rawAnalysis.sensitivity != currentSensitivity) {
                    Log.d(
                        TAG,
                        "drop visual analysis: stale sensitivity analysis=${rawAnalysis.sensitivity} current=$currentSensitivity"
                    )
                    return@Thread
                }

                if (!rawAnalysis.ok) {

                    val failedAnalysisBase = rawAnalysis
                        .copy(
                            parseDelayMs = parseDelayMs,
                            candidateExtractionMs = candidateExtractionMs,
                            nodeCollectionMs = nodeCollectionMs,
                            visualRoiPlanningMs = candidateComputation?.visualRoiPlanningMs ?: -1L,
                            screenCandidateExtractionMs = candidateComputation?.screenCandidateExtractionMs ?: -1L,
                            candidatePostProcessingMs = candidatePostProcessingMs,
                            riskGateMaskMs = if (parseStartedAtMs > 0L) {
                                recentRiskGateMaskMs(parseStartedAtMs)
                            } else {
                                -1L
                            },
                            riskGateEventAgeMs = if (parseStartedAtMs > 0L) {
                                recentRiskGateEventAgeMs(parseStartedAtMs)
                            } else {
                                -1L
                            },
                            riskGateReceiveToMaskMs = if (parseStartedAtMs > 0L) {
                                recentRiskGateReceiveToMaskMs(parseStartedAtMs)
                            } else {
                                -1L
                            },
                            fastProvisionalMaskMs = if (parseStartedAtMs > 0L) {
                                recentFastProvisionalMaskMs(parseStartedAtMs)
                            } else {
                                -1L
                            },
                            visualOcrLatencyMs = visualOcrLatencyMs,
                            visualMaskLatencyMs = if (visualStartedAtMs > 0L) {
                                SystemClock.uptimeMillis() - visualStartedAtMs
                            } else {
                                -1L
                            },
                            visualOcrRawCount = ocrCandidates.size,
                            visualOcrSelectedCount = selectedVisualCandidates.size
                        )
                        .withPipelineDiagnostics(
                            experimentMode = experimentMode,
                            nodeCount = nodes.size,
                            screenCandidateCount = screenCandidates.size,
                            charLocationNodeCount = nodes.count { node -> node.charBoxes.isNotEmpty() },
                            charRangeCandidateCount = screenCandidates.count { candidate ->
                                candidate.backendSourceId.orEmpty().startsWith("android-accessibility-char-range:")
                            },
                            candidateParallelWaitMs = candidateComputation?.parallelWaitMs ?: -1L,
                            nodeCollectionMs = nodeCollectionMs,
                            visualRoiPlanningMs = candidateComputation?.visualRoiPlanningMs ?: -1L,
                            screenCandidateExtractionMs = candidateComputation?.screenCandidateExtractionMs ?: -1L,
                            candidatePostProcessingMs = candidatePostProcessingMs
                        )
                    AnalysisDiagnosticsStore.saveAttempt(
                        applicationContext,
                        if (experimentMode.overlayStageEnabled) {
                            failedAnalysisBase.withOverlayDiagnostics(packageName, visualRoiPlan)
                        } else {
                            failedAnalysisBase.withVisualCaptureDiagnostics(visualRoiPlan)
                        }
                    )
                    if (clearExistingOverlayOnMiss) {
                        clearMaskOverlayAfterVisualMiss(
                            packageName = packageName,
                            visualRoiPlan = visualRoiPlan,
                            visualRunId = visualRunId,
                            snapshotVisualSceneRevision = snapshotVisualSceneRevision
                        )
                    }
                    return@Thread
                }


                val mergedBaseResponse = mergeAnalysisResponses(
                    baseResponse = baseResponse,
                    visualResponse = if (visualRoiPlan.canReuseVisualSupplement()) {
                        reusableVisualSupplement(
                            packageName = packageName,
                            visualRoiSignature = visualRoiPlan.signature()
                        )
                    } else {
                        null
                    }
                )
                val visualMaskResponse = buildProvisionalVisualResponse(selectedVisualCandidates)
                val mergedVisualResponse = mergeAnalysisResponses(rawAnalysis.response, visualMaskResponse)
                val mergedResponse = mergeAnalysisResponses(mergedBaseResponse, mergedVisualResponse)
                storeVisualSupplement(
                    packageName = packageName,
                    visualRoiPlan = visualRoiPlan,
                    response = mergedResponse
                )
                val analysisBase = rawAnalysis
                    .copy(
                        response = mergedResponse,
                        parseDelayMs = parseDelayMs,
                        candidateExtractionMs = candidateExtractionMs,
                        nodeCollectionMs = nodeCollectionMs,
                        visualRoiPlanningMs = candidateComputation?.visualRoiPlanningMs ?: -1L,
                        screenCandidateExtractionMs = candidateComputation?.screenCandidateExtractionMs ?: -1L,
                        candidatePostProcessingMs = candidatePostProcessingMs,
                        backendMaskLatencyMs = if (parseStartedAtMs > 0L) {
                            SystemClock.uptimeMillis() - parseStartedAtMs
                        } else {
                            -1L
                        },
                        riskGateMaskMs = if (parseStartedAtMs > 0L) {
                            recentRiskGateMaskMs(parseStartedAtMs)
                        } else {
                            -1L
                        },
                        riskGateEventAgeMs = if (parseStartedAtMs > 0L) {
                            recentRiskGateEventAgeMs(parseStartedAtMs)
                        } else {
                            -1L
                        },
                        riskGateReceiveToMaskMs = if (parseStartedAtMs > 0L) {
                            recentRiskGateReceiveToMaskMs(parseStartedAtMs)
                        } else {
                            -1L
                        },
                        fastProvisionalMaskMs = if (parseStartedAtMs > 0L) {
                            recentFastProvisionalMaskMs(parseStartedAtMs)
                        } else {
                            -1L
                        },
                        visualOcrLatencyMs = visualOcrLatencyMs,
                        visualMaskLatencyMs = if (visualStartedAtMs > 0L) {
                            SystemClock.uptimeMillis() - visualStartedAtMs
                        } else {
                            -1L
                        },
                        commentCount = mergedResponse?.results?.size ?: rawAnalysis.commentCount,
                        offensiveCount = countActionableResults(mergedResponse),
                        filteredCount = mergedResponse?.filteredCount ?: rawAnalysis.filteredCount,
                        visualOcrRawCount = ocrCandidates.size,
                        visualOcrSelectedCount = selectedVisualCandidates.size
                    )
                    .withPipelineDiagnostics(
                        experimentMode = experimentMode,
                        nodeCount = nodes.size,
                        screenCandidateCount = screenCandidates.size,
                        charLocationNodeCount = nodes.count { node -> node.charBoxes.isNotEmpty() },
                        charRangeCandidateCount = screenCandidates.count { candidate ->
                            candidate.backendSourceId.orEmpty().startsWith("android-accessibility-char-range:")
                        },
                        candidateParallelWaitMs = candidateComputation?.parallelWaitMs ?: -1L,
                        nodeCollectionMs = nodeCollectionMs,
                        visualRoiPlanningMs = candidateComputation?.visualRoiPlanningMs ?: -1L,
                        screenCandidateExtractionMs = candidateComputation?.screenCandidateExtractionMs ?: -1L,
                        candidatePostProcessingMs = candidatePostProcessingMs
                    )
                val analysis = if (experimentMode.overlayStageEnabled) {
                    analysisBase.withOverlayDiagnostics(packageName, visualRoiPlan)
                } else {
                    analysisBase.withVisualCaptureDiagnostics(visualRoiPlan)
                }

                AnalysisDiagnosticsStore.saveAttempt(applicationContext, analysis)
                if (experimentMode.overlayStageEnabled) {
                    handler.post {
                        if (isVisualAnalysisStale(visualRunId, snapshotVisualSceneRevision)) return@post
                        updateMaskOverlay(
                            currentPackage = packageName,
                            analysis = analysis,
                            snapshotOverlayRevision = snapshotOverlayRevision,
                            visualRoiPlan = visualRoiPlan,
                            isProvisionalVisualMask = packageName == YOUTUBE_PACKAGE &&
                                !visualRoiPlan.hasYoutubeCommentPanelRoi()
                        )
                    }
                }
            } finally {
                finishVisualAnalysis(visualRunId)
            }
        }.start()
    }

    private fun markVisualRefreshCompleted(visualRoiPlan: VisualTextRoiPlan) {
        lastVisualRefreshSignature = visualRoiPlan.signature()
        lastVisualRefreshCompletedAtMs = SystemClock.uptimeMillis()
    }

    private fun renderProvisionalVisualMaskOverlay(
        packageName: String,
        visualRoiPlan: VisualTextRoiPlan,
        selectedOcrCandidates: List<ParsedComment>,
        baseResponse: AndroidAnalysisResponse? = null,
        parseDelayMs: Long = -1L,
        candidateExtractionMs: Long = -1L,
        nodeCollectionMs: Long = -1L,
        candidatePostProcessingMs: Long = -1L,
        experimentMode: PipelineExperimentMode = currentExperimentMode(),
        candidateComputation: ParseCandidateComputation? = null,
        nodes: List<ParsedTextNode> = emptyList(),
        screenCandidates: List<ScreenTextCandidate> = emptyList(),
        parseStartedAtMs: Long = -1L,
        visualStartedAtMs: Long,
        visualOcrLatencyMs: Long,
        snapshotOverlayRevision: Long,
        snapshotVisualSceneRevision: Long,
        visualRunId: Long
    ) {
        val visualResponse = buildProvisionalVisualResponse(selectedOcrCandidates)
        val response = mergeAnalysisResponses(baseResponse, visualResponse) ?: visualResponse
        val analysis = AndroidAnalysisAttempt(
            ok = true,
            packageName = packageName,
            url = "visual-ocr-provisional",
            sensitivity = AnalysisSensitivityStore.get(applicationContext),
            latencyMs = 0L,
            parseDelayMs = parseDelayMs,
            candidateExtractionMs = candidateExtractionMs,
            nodeCollectionMs = nodeCollectionMs,
            visualRoiPlanningMs = candidateComputation?.visualRoiPlanningMs ?: -1L,
            screenCandidateExtractionMs = candidateComputation?.screenCandidateExtractionMs ?: -1L,
            candidatePostProcessingMs = candidatePostProcessingMs,
            riskGateMaskMs = if (parseStartedAtMs > 0L) {
                recentRiskGateMaskMs(parseStartedAtMs)
            } else {
                -1L
            },
            riskGateEventAgeMs = if (parseStartedAtMs > 0L) {
                recentRiskGateEventAgeMs(parseStartedAtMs)
            } else {
                -1L
            },
            riskGateReceiveToMaskMs = if (parseStartedAtMs > 0L) {
                recentRiskGateReceiveToMaskMs(parseStartedAtMs)
            } else {
                -1L
            },
            fastProvisionalMaskMs = if (parseStartedAtMs > 0L) {
                recentFastProvisionalMaskMs(parseStartedAtMs)
            } else {
                -1L
            },
            visualOcrLatencyMs = visualOcrLatencyMs,
            visualMaskLatencyMs = if (visualStartedAtMs > 0L) {
                SystemClock.uptimeMillis() - visualStartedAtMs
            } else {
                -1L
            },
            commentCount = response.results.size,
            offensiveCount = response.results.size,
            filteredCount = response.filteredCount,
            response = response,
            visualOcrSelectedCount = selectedOcrCandidates.size
        )
            .withPipelineDiagnostics(
                experimentMode = experimentMode,
                nodeCount = nodes.size,
                screenCandidateCount = screenCandidates.size,
                charLocationNodeCount = nodes.count { node -> node.charBoxes.isNotEmpty() },
                charRangeCandidateCount = screenCandidates.count { candidate ->
                    candidate.backendSourceId.orEmpty().startsWith("android-accessibility-char-range:")
                },
                candidateParallelWaitMs = candidateComputation?.parallelWaitMs ?: -1L,
                nodeCollectionMs = nodeCollectionMs,
                visualRoiPlanningMs = candidateComputation?.visualRoiPlanningMs ?: -1L,
                screenCandidateExtractionMs = candidateComputation?.screenCandidateExtractionMs ?: -1L,
                candidatePostProcessingMs = candidatePostProcessingMs
            )
            .withOverlayDiagnostics(packageName, visualRoiPlan)

        handler.post {
            if (isVisualAnalysisStale(visualRunId, snapshotVisualSceneRevision)) return@post
            Log.d(TAG, "render provisional visual OCR masks count=${selectedOcrCandidates.size}")
            updateMaskOverlay(
                currentPackage = packageName,
                analysis = analysis,
                snapshotOverlayRevision = snapshotOverlayRevision,
                visualRoiPlan = visualRoiPlan,
                isProvisionalVisualMask = true
            )
        }
    }

    private fun buildProvisionalVisualResponse(
        selectedOcrCandidates: List<ParsedComment>
    ): AndroidAnalysisResponse {
        val results = selectedOcrCandidates.map { candidate ->
            val text = candidate.commentText
            val textLength = text.codePointCount(0, text.length).coerceAtLeast(1)
            AndroidAnalysisResultItem(
                original = text,
                boundsInScreen = candidate.boundsInScreen,
                authorId = candidate.authorId,
                isOffensive = true,
                isProfane = true,
                isToxic = false,
                isHate = false,
                scores = HarmScores(profanity = 1.0, toxicity = 0.0, hate = 0.0),
                evidenceSpans = listOf(
                    EvidenceSpan(
                        text = text,
                        start = 0,
                        end = textLength,
                        score = 1.0
                    )
                )
            )
        }
        return AndroidAnalysisResponse(
            timestamp = System.currentTimeMillis(),
            filteredCount = results.size,
            results = results
        )
    }

    private fun timeoutVisualAnalysis(
        visualRunId: Long,
        packageName: String,
        visualRoiPlan: VisualTextRoiPlan,
        snapshotVisualSceneRevision: Long,
        clearExistingOverlayOnMiss: Boolean
    ) {
        if (visualRunId != visualAnalysisRunId || !visualAnalysisInFlight) return

        Log.w(TAG, "visual OCR timed out runId=$visualRunId")
        visualAnalysisRunId += 1L
        visualAnalysisInFlight = false
        saveVisualFailureDiagnostics(
            packageName = packageName,
            visualRoiPlan = visualRoiPlan,
            error = "VISUAL_OCR_TIMEOUT"
        )
        if (
            clearExistingOverlayOnMiss &&
            packageName == lastObservedPackage &&
            snapshotVisualSceneRevision == visualSceneRevision
        ) {
            handleVisualAnalysisMissOnMain(visualRoiPlan)
        }
        scheduleFollowUpAfterVisualGate()
    }

    private fun finishVisualAnalysis(
        visualRunId: Long,
        retryDelayMs: Long? = null
    ) {
        if (visualRunId != visualAnalysisRunId) return
        visualAnalysisInFlight = false
        if (retryDelayMs != null) {
            followUpParseRequested = false
            Log.d(TAG, "retry visual OCR after screenshot throttle delayMs=$retryDelayMs")
            handler.post {
                scheduleParse(
                    delayMs = retryDelayMs,
                    eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                    replaceExisting = true
                )
            }
            return
        }
        scheduleFollowUpAfterVisualGate()
    }

    private fun remainingScreenshotThrottleDelayMs(): Long {
        val lastRequestAtMs = lastScreenshotRequestAtMs
        if (lastRequestAtMs <= 0L) return 0L

        return MaskOverlayEventPolicy.screenshotRequestThrottleDelay(
            elapsedSinceLastRequestMs = SystemClock.uptimeMillis() - lastRequestAtMs
        )
    }

    private fun screenshotFailureRetryDelayMs(errorCode: Int): Long? {
        val lastRequestAtMs = lastScreenshotRequestAtMs
        if (lastRequestAtMs <= 0L) return null

        return MaskOverlayEventPolicy.screenshotFailureRetryDelay(
            errorCode = errorCode,
            elapsedSinceLastRequestMs = SystemClock.uptimeMillis() - lastRequestAtMs
        )
    }

    private fun clearMaskOverlayAfterVisualMiss(
        packageName: String,
        visualRoiPlan: VisualTextRoiPlan,
        visualRunId: Long,
        snapshotVisualSceneRevision: Long
    ) {
        handler.post {
            if (packageName != lastObservedPackage) return@post
            if (isVisualAnalysisStale(visualRunId, snapshotVisualSceneRevision)) return@post
            handleVisualAnalysisMissOnMain(visualRoiPlan)
        }
    }

    private fun handleVisualAnalysisMissOnMain(visualRoiPlan: VisualTextRoiPlan) {
        if (
            !MaskOverlayEventPolicy.shouldClearAfterVisualAnalysisMiss(
                hasActiveMasks = maskOverlayController.hasActiveMasks(),
                hasRenderableVisualRois = visualRoiPlan.hasRenderableVisualRois(),
                isOverlayStabilizing = isInOverlayStabilizationWindow(),
                hasPreservedRecentVisualMiss = preservedRecentVisualMiss
            )
        ) {
            Log.d(TAG, "preserve mask overlay after transient visual OCR miss")
            preservedRecentVisualMiss = true
            markOverlayRevisionStale()
            scheduleDeferredFollowUpParse(waitForScrollStabilization = true)
            return
        }
        preservedRecentVisualMiss = false
        clearMaskOverlay()
    }

    private fun scheduleFollowUpAfterVisualGate() {
        if (followUpParseRequested && !analysisInFlight) {
            handler.post {
                if (analysisInFlight) return@post
                followUpParseRequested = false
                scheduleDeferredFollowUpParse()
            }
        }
    }

    private fun isVisualAnalysisStale(
        visualRunId: Long,
        snapshotVisualSceneRevision: Long
    ): Boolean {
        return visualRunId != visualAnalysisRunId ||
            snapshotVisualSceneRevision != visualSceneRevision
    }

    private fun mergeVisualCandidateSelections(
        selectedOcrCandidates: List<ParsedComment>,
        semanticFallbackCandidates: List<ParsedComment> = emptyList()
    ): List<ParsedComment> {
        val selected = mutableListOf<ParsedComment>()

        fun appendCandidates(candidates: List<ParsedComment>) {
            candidates
                .sortedWith(
                    compareBy<ParsedComment> { visualCandidateSourceRank(it) }
                        .thenBy { it.boundsInScreen.top }
                        .thenBy { it.boundsInScreen.left }
                )
                .forEach { candidate ->
                    if (selected.size >= MAX_VISUAL_ANALYSIS_CANDIDATES) return@forEach
                    if (selected.none { existing -> isSameVisualCandidate(existing, candidate) }) {
                        selected += candidate
                    }
                }
        }

        appendCandidates(selectedOcrCandidates)
        appendCandidates(semanticFallbackCandidates)
        return selected
    }

    private fun selectVisualTextCandidates(
        ocrCandidates: List<ParsedComment>,
        screenWidth: Int,
        screenHeight: Int,
        baseResponse: AndroidAnalysisResponse?
    ): List<ParsedComment> {
        val baseLocations = baseResponse?.results
            ?.mapNotNull { result ->
                val keys = analysisTextKeys(result.original)
                if (keys.isEmpty()) {
                    null
                } else {
                    AnalysisTextLocation(
                        keys = keys,
                        boundsInScreen = result.boundsInScreen,
                        authorId = result.authorId
                    )
                }
            }
            .orEmpty()

        val sortedCandidates = ocrCandidates
            .asSequence()
            .filter { candidate ->
                val key = normalizeAnalysisTextKey(candidate.commentText)
                key.isNotBlank() &&
                    !isTopControlOcrCandidate(candidate, screenWidth, screenHeight) &&
                    !matchesBaseTextLocation(candidate, baseLocations)
            }
            .sortedWith(
                compareBy<ParsedComment> { visualCandidateSourceRank(it) }
                    .thenBy { it.boundsInScreen.top }
                    .thenBy { it.boundsInScreen.left }
            )
            .toList()

        val distinctCandidates = mutableListOf<ParsedComment>()
        var fallbackCandidateCount = 0
        for (candidate in sortedCandidates) {
            if (distinctCandidates.any { existing -> isSameVisualCandidate(existing, candidate) }) {
                continue
            }

            val isFallbackCandidate = candidate.visualOcrSource() == "youtube-visible-band"
            if (isFallbackCandidate) {
                if (fallbackCandidateCount >= MAX_FALLBACK_VISUAL_CANDIDATES) continue
            }

            distinctCandidates += candidate
            if (isFallbackCandidate) {
                fallbackCandidateCount += 1
            }
            if (distinctCandidates.size >= MAX_VISUAL_ANALYSIS_CANDIDATES) break
        }

        return distinctCandidates
    }

    private fun visualCandidateSourceRank(candidate: ParsedComment): Int {
        return when (candidate.visualOcrSource()) {
            "youtube-comment-panel" -> -1
            BROWSER_TEXT_NODE_SOURCE -> -1
            BROWSER_VISUAL_NODE_SOURCE -> -1
            "youtube-visible-band" -> 9
            "youtube-composite-card" -> 0
            "generic-visual-region" -> 1
            else -> 3
        }
    }

    private fun normalizeAnalysisTextKey(text: String): String {
        return text.replace(Regex("\\s+"), " ").trim().lowercase()
    }

    private fun analysisTextKeys(text: String): Set<String> {
        val normalized = normalizeAnalysisTextKey(text)
        val rangeKeys = VisualTextOcrCandidateFilter.findAnalysisRanges(text)
            .map { range -> normalizeAnalysisTextKey(range.analysisText) }
            .filter { key -> key.isNotBlank() }

        return (listOf(normalized) + rangeKeys)
            .filter { key -> key.isNotBlank() }
            .toSet()
    }

    private fun ParsedComment.visualOcrMetadata(): VisualTextOcrMetadata? {
        return VisualTextOcrMetadataCodec.decode(authorId)
    }

    private fun ParsedComment.visualOcrSource(): String? {
        return visualOcrMetadata()?.source
    }

    private fun isTopControlOcrCandidate(
        candidate: ParsedComment,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        if (candidate.visualOcrSource() == null) return false
        if (
            VisualTextGeometryPolicy.isTrustedVisibleBandOcr(
                authorId = candidate.authorId,
                left = candidate.boundsInScreen.left,
                top = candidate.boundsInScreen.top,
                right = candidate.boundsInScreen.right,
                bottom = candidate.boundsInScreen.bottom
            )
        ) {
            return false
        }
        if (VisualTextGeometryPolicy.isTopHeroYoutubeComposite(candidate.authorId, screenWidth)) {
            return false
        }
        val cutoff = min(
            TOP_CONTROL_OCR_EXCLUSION_MAX_PX,
            (screenHeight * TOP_CONTROL_OCR_EXCLUSION_RATIO).toInt()
        )
        return candidate.boundsInScreen.top < cutoff
    }

    private fun matchesBaseTextLocation(
        candidate: ParsedComment,
        baseLocations: List<AnalysisTextLocation>
    ): Boolean {
        val candidateKeys = analysisTextKeys(candidate.commentText)
        if (candidateKeys.isEmpty()) return true
        val visualMetadata = candidate.visualOcrMetadata()

        return baseLocations.any { baseLocation ->
            val overlapRatio = boundsOverlapRatio(candidate.boundsInScreen, baseLocation.boundsInScreen)
            val sameText = candidateKeys.any { key -> key in baseLocation.keys }

            when {
                !sameText -> false
                visualMetadata != null && !baseLocation.isRenderableForOverlay() -> false
                visualMetadata != null &&
                    isCoarseBaseLocation(candidate.boundsInScreen, baseLocation.boundsInScreen) -> false
                overlapRatio >= VISUAL_GEOMETRY_DUPLICATE_OVERLAP_RATIO && visualMetadata != null -> true
                overlapRatio >= VISUAL_DUPLICATE_OVERLAP_RATIO && sameText -> true
                visualMetadata?.source == "youtube-composite-card" &&
                    visualMetadata.roiBoundsInScreen?.contains(baseLocation.boundsInScreen) == true &&
                    overlapRatio >= VISUAL_CONTAINED_DUPLICATE_OVERLAP_RATIO -> true
                else -> false
            }
        }
    }

    private fun AnalysisTextLocation.isRenderableForOverlay(): Boolean {
        val source = authorId ?: return false
        return source == "android-accessibility:user_input" ||
            source == "android-accessibility:youtube_user_input" ||
            source.startsWith("android-accessibility-range:") ||
            source.startsWith("android-accessibility-browser-compact:") ||
            source.startsWith("ocr:youtube-composite-card:") ||
            source.startsWith("ocr:youtube-visible-band:") ||
            source.startsWith("ocr:youtube-comment-panel:") ||
            source.startsWith("ocr:$BROWSER_TEXT_NODE_SOURCE:") ||
            source.startsWith("ocr:$BROWSER_VISUAL_NODE_SOURCE:")
    }

    private fun isCoarseBaseLocation(candidateBounds: BoundsRect, baseBounds: BoundsRect): Boolean {
        val candidateArea = boundsArea(candidateBounds).coerceAtLeast(1)
        val baseArea = boundsArea(baseBounds).coerceAtLeast(1)
        return baseArea.toFloat() / candidateArea.toFloat() >= VISUAL_COARSE_BASE_AREA_MULTIPLIER
    }

    private fun boundsArea(bounds: BoundsRect): Int {
        return max(0, bounds.right - bounds.left) * max(0, bounds.bottom - bounds.top)
    }

    private fun BoundsRect.contains(inner: BoundsRect): Boolean {
        return inner.left >= left &&
            inner.top >= top &&
            inner.right <= right &&
            inner.bottom <= bottom
    }

    private fun isSameVisualCandidate(left: ParsedComment, right: ParsedComment): Boolean {
        if (isSameVisualCandidateWithinRoi(left, right)) return true

        val overlapRatio = boundsOverlapRatio(left.boundsInScreen, right.boundsInScreen)
        return (
            normalizeAnalysisTextKey(left.commentText) == normalizeAnalysisTextKey(right.commentText) &&
                overlapRatio >= VISUAL_DUPLICATE_OVERLAP_RATIO
            ) || overlapRatio >= VISUAL_GEOMETRY_DUPLICATE_OVERLAP_RATIO
    }

    private fun isSameVisualCandidateWithinRoi(left: ParsedComment, right: ParsedComment): Boolean {
        val leftMetadata = left.visualOcrMetadata() ?: return false
        val rightMetadata = right.visualOcrMetadata() ?: return false
        val leftRoi = leftMetadata.roiBoundsInScreen ?: return false
        val rightRoi = rightMetadata.roiBoundsInScreen ?: return false
        if (leftRoi != rightRoi) return false

        val leftSource = leftMetadata.source
        val rightSource = rightMetadata.source
        val hasPrecise = leftSource in PRECISE_YOUTUBE_VISUAL_SOURCES ||
            rightSource in PRECISE_YOUTUBE_VISUAL_SOURCES
        val hasSemanticFallback = leftSource == YOUTUBE_SEMANTIC_FALLBACK_SOURCE ||
            rightSource == YOUTUBE_SEMANTIC_FALLBACK_SOURCE
        if (!hasPrecise || !hasSemanticFallback) return false

        val leftKeys = analysisTextKeys(left.commentText)
        if (leftKeys.isEmpty()) return false
        val rightKeys = analysisTextKeys(right.commentText)
        return rightKeys.any { key -> key in leftKeys }
    }

    private fun boundsOverlapRatio(left: BoundsRect, right: BoundsRect): Float {
        val intersectionLeft = max(left.left, right.left)
        val intersectionTop = max(left.top, right.top)
        val intersectionRight = min(left.right, right.right)
        val intersectionBottom = min(left.bottom, right.bottom)
        val intersectionWidth = max(0, intersectionRight - intersectionLeft)
        val intersectionHeight = max(0, intersectionBottom - intersectionTop)
        val intersectionArea = intersectionWidth * intersectionHeight
        if (intersectionArea <= 0) return 0f

        val leftArea = max(0, left.right - left.left) * max(0, left.bottom - left.top)
        val rightArea = max(0, right.right - right.left) * max(0, right.bottom - right.top)
        val smallerArea = min(leftArea, rightArea)
        if (smallerArea <= 0) return 0f

        return intersectionArea.toFloat() / smallerArea.toFloat()
    }

    private fun VisualTextRoiPlan.signature(): String {
        return rois.joinToString("|") { roi ->
            val bounds = roi.boundsInScreen
            "${roi.source}:${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
        }
    }

    private fun VisualTextRoiPlan.canReuseVisualSupplement(): Boolean {
        return rois.isNotEmpty() && rois.none { roi ->
            roi.source == "youtube-visible-band" ||
                roi.source == "youtube-composite-card" ||
                roi.source == "youtube-comment-panel" ||
                roi.source == BROWSER_TEXT_NODE_SOURCE ||
                roi.source == BROWSER_VISUAL_NODE_SOURCE ||
                roi.source == FULL_SCREEN_BASELINE_SOURCE
        }
    }

    private fun VisualTextRoiPlan.hasYoutubeCommentPanelRoi(): Boolean {
        return rois.any { roi -> roi.source == "youtube-comment-panel" }
    }

    private fun VisualTextRoiPlan.hasRenderableVisualRois(): Boolean {
        return rois.any { roi ->
            roi.source == "youtube-composite-card" ||
                roi.source == "youtube-visible-band" ||
                roi.source == "youtube-comment-panel" ||
                roi.source == BROWSER_TEXT_NODE_SOURCE ||
                roi.source == BROWSER_VISUAL_NODE_SOURCE ||
                roi.source == FULL_SCREEN_BASELINE_SOURCE
        }
    }

    private fun reusableVisualSupplement(
        packageName: String,
        visualRoiSignature: String? = null
    ): AndroidAnalysisResponse? {
        val cached = lastVisualSupplement ?: return null
        if (cached.packageName != packageName) return null
        val currentSensitivity = AnalysisSensitivityStore.get(applicationContext)
        if (cached.sensitivity != currentSensitivity) return null
        if (visualRoiSignature != null && cached.visualRoiSignature != visualRoiSignature) return null
        if (cached.expiresAtUptimeMs <= SystemClock.uptimeMillis()) {
            lastVisualSupplement = null
            return null
        }
        Log.d(TAG, "reuse visual supplement signature=${cached.visualRoiSignature}")
        return cached.response
    }

    private fun storeVisualSupplement(
        packageName: String,
        visualRoiPlan: VisualTextRoiPlan,
        response: AndroidAnalysisResponse?
    ) {
        if (!visualRoiPlan.canReuseVisualSupplement()) return
        if (response == null || countActionableResults(response) <= 0) return
        lastVisualSupplement = VisualSupplementCache(
            packageName = packageName,
            sensitivity = AnalysisSensitivityStore.get(applicationContext),
            visualRoiSignature = visualRoiPlan.signature(),
            response = response,
            expiresAtUptimeMs = SystemClock.uptimeMillis() + VISUAL_SUPPLEMENT_CACHE_TTL_MS
        )
    }

    private fun mergeAnalysisResponses(
        baseResponse: AndroidAnalysisResponse?,
        visualResponse: AndroidAnalysisResponse?
    ): AndroidAnalysisResponse? {
        if (baseResponse == null) return visualResponse
        if (visualResponse == null) return baseResponse

        val merged = (baseResponse.results + visualResponse.results)
            .distinctBy { result ->
                val bounds = result.boundsInScreen
                "${result.original}|${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
            }

        return AndroidAnalysisResponse(
            timestamp = maxOf(baseResponse.timestamp, visualResponse.timestamp),
            filteredCount = baseResponse.filteredCount + visualResponse.filteredCount,
            results = merged
        )
    }

    private fun countActionableResults(response: AndroidAnalysisResponse?): Int {
        return response?.results
            ?.count { result -> result.isOffensive && result.evidenceSpans.isNotEmpty() }
            ?: 0
    }

    private fun countHarmfulResults(response: AndroidAnalysisResponse?): Int {
        return response?.results
            ?.count { result -> result.isOffensive }
            ?: 0
    }

    private fun buildBlockedYoutubeCommentSpecs(
        response: AndroidAnalysisResponse?,
        visualRoiPlan: VisualTextRoiPlan?
    ): List<MaskOverlaySpec> {
        val metrics = resources.displayMetrics
        val commentPanelBounds = visualRoiPlan?.let { youtubeCommentPanelBounds(it) }.orEmpty()
        val results = response?.results.orEmpty()
        val specs = YoutubeSkeletonMaskBuilder.buildCommentContentSpecsFromResults(
            results = results,
            commentPanelBounds = commentPanelBounds,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            style = MaskOverlayStyle.BLOCKED,
            label = "comment-blocked",
            debugSource = "youtube-comment-blocked-model"
        )
        val inputSamples = results
            .filter { result -> result.isOffensive }
            .take(4)
            .joinToString(";") { result ->
                val bounds = result.boundsInScreen
                "${result.authorId}@${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
            }
        val specSamples = specs.take(4).joinToString(";") { spec ->
            "${spec.left},${spec.top},${spec.width},${spec.height}:${spec.debugSource}"
        }
        Log.d(TAG, "youtube blocked geometry inputs=$inputSamples specs=$specSamples")
        return specs
    }
    private fun saveVisualFailureDiagnostics(
        packageName: String,
        visualRoiPlan: VisualTextRoiPlan,
        error: String,
        experimentMode: PipelineExperimentMode = currentExperimentMode()
    ) {
        AnalysisDiagnosticsStore.saveAttempt(
            applicationContext,
            AndroidAnalysisClient
                .analyzeSnapshot(
                    applicationContext,
                    ParseSnapshot(
                        timestamp = System.currentTimeMillis(),
                        comments = emptyList()
                    )
                )
                .copy(
                    ok = false,
                    packageName = packageName,
                    experimentMode = experimentMode.id,
                    experimentStages = experimentMode.stageMask,
                    error = error
                )
                .withVisualCaptureDiagnostics(visualRoiPlan)
        )
    }

    private fun saveExperimentStageDiagnostics(
        packageName: String,
        experimentMode: PipelineExperimentMode,
        parseDelayMs: Long,
        candidateExtractionMs: Long,
        nodeCollectionMs: Long,
        candidatePostProcessingMs: Long,
        candidateComputation: ParseCandidateComputation,
        nodes: List<ParsedTextNode>,
        screenCandidates: List<ScreenTextCandidate>,
        comments: List<ParsedComment>,
        visualRoiPlan: VisualTextRoiPlan,
        url: String,
        response: AndroidAnalysisResponse? = null,
        accessibilityMaskLatencyMs: Long = -1L,
        riskGateMaskMs: Long = -1L,
        riskGateEventAgeMs: Long = -1L,
        riskGateReceiveToMaskMs: Long = -1L,
        fastProvisionalMaskMs: Long = -1L,
        backendMaskLatencyMs: Long = -1L,
        visualOcrLatencyMs: Long = -1L,
        visualMaskLatencyMs: Long = -1L,
        ok: Boolean = true,
        error: String? = null,
        includeOverlayDiagnostics: Boolean = false
    ) {
        val attempt = AndroidAnalysisAttempt(
            ok = ok,
            packageName = packageName,
            url = url,
            sensitivity = AnalysisSensitivityStore.get(applicationContext),
            latencyMs = 0L,
            parseDelayMs = parseDelayMs,
            candidateExtractionMs = candidateExtractionMs,
            nodeCollectionMs = nodeCollectionMs,
            visualRoiPlanningMs = candidateComputation.visualRoiPlanningMs,
            screenCandidateExtractionMs = candidateComputation.screenCandidateExtractionMs,
            candidatePostProcessingMs = candidatePostProcessingMs,
            candidateParallelWaitMs = candidateComputation.parallelWaitMs,
            accessibilityMaskLatencyMs = accessibilityMaskLatencyMs,
            riskGateMaskMs = riskGateMaskMs,
            riskGateEventAgeMs = riskGateEventAgeMs,
            riskGateReceiveToMaskMs = riskGateReceiveToMaskMs,
            fastProvisionalMaskMs = fastProvisionalMaskMs,
            backendMaskLatencyMs = backendMaskLatencyMs,
            visualOcrLatencyMs = visualOcrLatencyMs,
            visualMaskLatencyMs = visualMaskLatencyMs,
            commentCount = comments.size,
            offensiveCount = response?.results?.size ?: 0,
            filteredCount = response?.filteredCount ?: 0,
            response = response,
            candidateRouteSamples = CandidateRoutingPolicy.summarize(screenCandidates),
            error = error
        ).withPipelineDiagnostics(
            experimentMode = experimentMode,
            nodeCount = nodes.size,
            screenCandidateCount = screenCandidates.size,
            charLocationNodeCount = nodes.count { node -> node.charBoxes.isNotEmpty() },
            charRangeCandidateCount = screenCandidates.count { candidate ->
                candidate.backendSourceId.orEmpty().startsWith("android-accessibility-char-range:")
            },
            candidateParallelWaitMs = candidateComputation.parallelWaitMs,
            nodeCollectionMs = nodeCollectionMs,
            visualRoiPlanningMs = candidateComputation.visualRoiPlanningMs,
            screenCandidateExtractionMs = candidateComputation.screenCandidateExtractionMs,
            candidatePostProcessingMs = candidatePostProcessingMs
        )

        val diagnosed = if (includeOverlayDiagnostics) {
            attempt.withOverlayDiagnostics(packageName, visualRoiPlan)
        } else {
            attempt.withVisualCaptureDiagnostics(visualRoiPlan)
        }
        AnalysisDiagnosticsStore.saveAttempt(applicationContext, diagnosed)
    }

    private fun AndroidAnalysisAttempt.withPipelineDiagnostics(
        experimentMode: PipelineExperimentMode,
        nodeCount: Int,
        screenCandidateCount: Int,
        charLocationNodeCount: Int,
        charRangeCandidateCount: Int,
        candidateParallelWaitMs: Long,
        nodeCollectionMs: Long = -1L,
        visualRoiPlanningMs: Long = -1L,
        screenCandidateExtractionMs: Long = -1L,
        candidatePostProcessingMs: Long = -1L
    ): AndroidAnalysisAttempt {
        return copy(
            experimentMode = experimentMode.id,
            experimentStages = experimentMode.stageMask,
            nodeCount = nodeCount,
            screenCandidateCount = screenCandidateCount,
            charLocationNodeCount = charLocationNodeCount,
            charRangeCandidateCount = charRangeCandidateCount,
            nodeCollectionMs = nodeCollectionMs,
            visualRoiPlanningMs = visualRoiPlanningMs,
            screenCandidateExtractionMs = screenCandidateExtractionMs,
            candidatePostProcessingMs = candidatePostProcessingMs,
            candidateParallelWaitMs = candidateParallelWaitMs
        )
    }

    private fun AndroidAnalysisAttempt.withOverlayDiagnostics(
        packageName: String,
        visualRoiPlan: VisualTextRoiPlan
    ): AndroidAnalysisAttempt {
        if (!supportsMaskOverlay(packageName)) return withVisualCaptureDiagnostics(visualRoiPlan)
        val response = response ?: return withVisualCaptureDiagnostics(visualRoiPlan)
        val metrics = resources.displayMetrics
        val plan = AndroidMaskOverlayPlanner.buildPlan(
            response = response,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels
        )

        return copy(
            overlayCandidateCount = plan.candidateCount,
            overlayRenderedCount = plan.specs.size,
            overlaySkippedUnstableCount = plan.skippedUnstableCount,
            overlayRenderedSamples = plan.renderedSamples,
            visualCaptureSupported = visualCaptureState.supported,
            visualCaptureReason = visualCaptureState.reason,
            visualRoiCandidateCount = visualRoiPlan.candidateCount,
            visualRoiSelectedCount = visualRoiPlan.rois.size
        )
    }

    private fun AndroidAnalysisAttempt.withVisualCaptureDiagnostics(
        visualRoiPlan: VisualTextRoiPlan = VisualTextRoiPlan(rois = emptyList(), candidateCount = 0)
    ): AndroidAnalysisAttempt {
        return copy(
            visualCaptureSupported = visualCaptureState.supported,
            visualCaptureReason = visualCaptureState.reason,
            visualRoiCandidateCount = visualRoiPlan.candidateCount,
            visualRoiSelectedCount = visualRoiPlan.rois.size
        )
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

    private fun extractVisibleTextNodesFromYoutubeWindows(
        requestCharacterBoxes: Boolean = true
    ): List<ParsedTextNode> {
        val out = mutableListOf<ParsedTextNode>()
        val seenRootKeys = mutableSetOf<String>()
        val sourcePackage = if (debugYoutubeHarnessActive) {
            applicationContext.packageName
        } else {
            YOUTUBE_PACKAGE
        }

        fun addRoot(root: AccessibilityNodeInfo?) {
            if (root == null) return
            if (root.packageName?.toString() != sourcePackage) return

            val rect = Rect().also { root.getBoundsInScreen(it) }
            val rootKey = "${rect.left},${rect.top},${rect.right},${rect.bottom},${root.className}"
            if (!seenRootKeys.add(rootKey)) return

            val rawNodes = collectRawNodesFromRoot(root, requestCharacterBoxes)
            out += if (debugYoutubeHarnessActive) {
                rawNodes.map { node -> node.copy(packageName = YOUTUBE_PACKAGE) }
            } else {
                rawNodes
            }
        }

        addRoot(rootInActiveWindow)
        windows?.forEach { window ->
            addRoot(window.root)
        }

        return deduplicateNodes(out)
    }

    private fun extractVisibleTextNodesFromInstagramWindows(
        requestCharacterBoxes: Boolean = true
    ): List<ParsedTextNode> {
        val pickedRoot = findBestInstagramAccessibilityRoot() ?: return emptyList()
        val nodes = collectFilteredNodesFromRoot(
            root = pickedRoot,
            instagramMode = true,
            tiktokMode = false,
            requestCharacterBoxes = requestCharacterBoxes
        )
        return if (debugInstagramHarnessActive) {
            nodes.map { node -> node.copy(packageName = INSTAGRAM_PACKAGE) }
        } else {
            nodes
        }
    }

    private fun extractInstagramPanelProbeNodes(): List<ParsedTextNode> {
        val pickedRoot = findBestInstagramAccessibilityRoot() ?: return emptyList()
        val nodes = collectRawNodesFromRoot(
            root = pickedRoot,
            requestCharacterBoxes = false
        )
        return if (debugInstagramHarnessActive) {
            nodes.map { node -> node.copy(packageName = INSTAGRAM_PACKAGE) }
        } else {
            nodes
        }
    }

    private fun findBestInstagramAccessibilityRoot(): AccessibilityNodeInfo? {
        val candidates = mutableListOf<WindowCandidate>()
        val activeRoot = rootInActiveWindow

        if (activeRoot != null && isInstagramAccessibilityRoot(activeRoot)) {
            val raw = collectRawNodesFromRoot(
                root = activeRoot,
                requestCharacterBoxes = false
            )
            candidates += WindowCandidate(
                label = "active",
                root = activeRoot,
                rawNodes = raw,
                score = scoreInstagramWindow(raw)
            )
        }

        windows?.forEachIndexed { index, window ->
            val root = window.root ?: return@forEachIndexed
            if (!isInstagramAccessibilityRoot(root)) return@forEachIndexed
            val raw = collectRawNodesFromRoot(
                root = root,
                requestCharacterBoxes = false
            )
            candidates += WindowCandidate(
                label = "window-$index-${windowTypeName(window)}",
                root = root,
                rawNodes = raw,
                score = scoreInstagramWindow(raw)
            )
        }

        val best = candidates.maxByOrNull { candidate -> candidate.score }
        return when {
            best != null && best.score > 0 -> best.root
            activeRoot != null && isInstagramAccessibilityRoot(activeRoot) -> activeRoot
            else -> candidates.firstOrNull()?.root
        }
    }

    private fun isInstagramAccessibilityRoot(node: AccessibilityNodeInfo): Boolean {
        val sourcePackage = if (debugInstagramHarnessActive) {
            applicationContext.packageName
        } else {
            INSTAGRAM_PACKAGE
        }
        return node.packageName?.toString() == sourcePackage
    }

    private fun collectFilteredNodesFromRoot(
        root: AccessibilityNodeInfo,
        instagramMode: Boolean,
        tiktokMode: Boolean,
        requestCharacterBoxes: Boolean = true
    ): List<ParsedTextNode> {
        val out = mutableListOf<ParsedTextNode>()
        val rootRect = Rect().also { root.getBoundsInScreen(it) }
        val screenHeight = if (rootRect.height() > 0) rootRect.height() else rootRect.bottom
        val upperCutoff = when {
            instagramMode -> (screenHeight * 0.08f).toInt()
            !tiktokMode -> (screenHeight * 0.12f).toInt()
            else -> (screenHeight * 0.28f).toInt()
        }

        fun dfs(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (!node.isVisibleToUser) return

            val rect = Rect().also { node.getBoundsInScreen(it) }
            val hasUsableBounds = rect.width() > 0 && rect.height() > 0
            if (hasUsableBounds && rootRect.height() > 0 && isOutsideRootBounds(rect, rootRect)) {
                return
            }

            val parsed = nodeToParsedTextNode(
                node = node,
                precomputedRect = rect.takeIf { hasUsableBounds },
                requestCharacterBoxes = requestCharacterBoxes
            ) ?: run {
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    dfs(child)
                }
                return
            }

            val parsedRect = Rect(parsed.left, parsed.top, parsed.right, parsed.bottom)
            if (shouldKeepNode(node, parsed.displayText.orEmpty(), parsedRect, upperCutoff, instagramMode, tiktokMode)) {
                out += parsed
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                dfs(child)
            }
        }

        dfs(root)
        return deduplicateNodes(out)
    }

    private fun isOutsideRootBounds(rect: Rect, rootRect: Rect): Boolean {
        return rect.right < rootRect.left ||
            rect.left > rootRect.right ||
            rect.bottom < rootRect.top ||
            rect.top > rootRect.bottom
    }

    private fun collectRawNodesFromRoot(
        root: AccessibilityNodeInfo,
        requestCharacterBoxes: Boolean = true
    ): List<ParsedTextNode> {
        val out = mutableListOf<ParsedTextNode>()

        fun dfs(node: AccessibilityNodeInfo?) {
            if (node == null) return

            val parsed = nodeToParsedTextNode(
                node = node,
                requestCharacterBoxes = requestCharacterBoxes
            )
            if (parsed != null) {
                out += parsed
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                dfs(child)
            }
        }

        dfs(root)
        return deduplicateNodes(out)
    }

    private fun nodeToParsedTextNode(
        node: AccessibilityNodeInfo,
        precomputedRect: Rect? = null,
        requestCharacterBoxes: Boolean = true
    ): ParsedTextNode? {
        val packageName = node.packageName?.toString().orEmpty()
        val viewIdResourceName = node.viewIdResourceName
        val text = node.text?.toString()
        val contentDescription = node.contentDescription?.toString()
        val rect = precomputedRect ?: Rect().also { node.getBoundsInScreen(it) }
        val isYoutubeCommentPanelStructure = packageName == YOUTUBE_PACKAGE &&
            viewIdResourceName.orEmpty() in YOUTUBE_COMMENT_PANEL_CONTENT_VIEW_IDS
        val isInstagramCommentPanelStructure =
            (packageName == INSTAGRAM_PACKAGE ||
                (debugInstagramHarnessActive && packageName == applicationContext.packageName)) &&
                InstagramCommentSurfaceDetector.isCommentStructureViewId(viewIdResourceName)
        val value = when {
            !text.isNullOrBlank() -> text.trim()
            !contentDescription.isNullOrBlank() -> contentDescription.trim()
            isYoutubeCommentPanelStructure -> ""
            isInstagramCommentPanelStructure -> ""
            else -> null
        } ?: return null

        if (rect.width() <= 0 || rect.height() <= 0) {
            return null
        }

        return ParsedTextNode(
            packageName = packageName,
            text = text,
            contentDescription = contentDescription,
            displayText = value,
            className = node.className?.toString(),
            viewIdResourceName = viewIdResourceName,
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom,
            approxTop = rect.top,
            isVisibleToUser = node.isVisibleToUser,
            charBoxes = if (requestCharacterBoxes) {
                requestTextCharacterBoxes(node = node, text = text, displayText = value)
            } else {
                emptyList()
            }
        )
    }

    private fun requestTextCharacterBoxes(
        node: AccessibilityNodeInfo,
        text: String?,
        displayText: String
    ): List<CharBox> {
        if (!currentExperimentMode().coordinateStageEnabled) return emptyList()
        val rawText = text ?: return emptyList()
        if (rawText.isBlank()) return emptyList()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
        val requestRanges = AccessibilityCharacterBoxPolicy.requestRanges(
            rawText = rawText,
            displayText = displayText,
            className = node.className?.toString(),
            viewIdResourceName = node.viewIdResourceName
        )
        if (requestRanges.isEmpty()) return emptyList()

        val extraData = try {
            node.availableExtraData
        } catch (_: RuntimeException) {
            emptyList()
        }
        if (!extraData.contains(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY)) {
            return emptyList()
        }

        return requestRanges.flatMap { range ->
            requestTextCharacterBoxesForRange(
                node = node,
                rawText = rawText,
                startIndex = range.start,
                length = range.length
            )
        }
            .distinctBy { box -> "${box.start}|${box.end}|${box.boundsInScreen}" }
    }

    private fun requestTextCharacterBoxesForRange(
        node: AccessibilityNodeInfo,
        rawText: String,
        startIndex: Int,
        length: Int
    ): List<CharBox> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
        if (length <= 0 || startIndex !in 0 until rawText.length) return emptyList()
        val safeLength = min(length, rawText.length - startIndex)
        if (safeLength <= 0) return emptyList()

        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, startIndex)
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, safeLength)
        }
        val refreshed = try {
            node.refreshWithExtraData(
                AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY,
                args
            )
        } catch (_: RuntimeException) {
            false
        }
        if (!refreshed) return emptyList()

        val rects = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            node.extras.getParcelableArray(
                AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY,
                RectF::class.java
            ) ?: return emptyList()
        } else {
            @Suppress("DEPRECATION")
            node.extras.getParcelableArray(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY)
                ?: return emptyList()
        }
        return rects.mapIndexedNotNull { localIndex, value ->
            val charIndex = startIndex + localIndex
            val rect = value as? RectF ?: return@mapIndexedNotNull null
            if (rect.width() <= 0f || rect.height() <= 0f) return@mapIndexedNotNull null
            if (charIndex >= rawText.length || Character.isLowSurrogate(rawText[charIndex])) {
                return@mapIndexedNotNull null
            }

            val codePoint = Character.codePointAt(rawText, charIndex)
            val nextCharIndex = (charIndex + Character.charCount(codePoint)).coerceAtMost(rawText.length)
            val start = rawText.codePointCount(0, charIndex)
            val end = rawText.codePointCount(0, nextCharIndex)
            if (end <= start) return@mapIndexedNotNull null

            CharBox(
                start = start,
                end = end,
                boundsInScreen = BoundsRect(
                    left = floor(rect.left).toInt(),
                    top = floor(rect.top).toInt(),
                    right = ceil(rect.right).toInt(),
                    bottom = ceil(rect.bottom).toInt()
                ),
                text = String(Character.toChars(codePoint))
            )
        }
    }

    private fun legacyRequestTextCharacterBoxes(
        node: AccessibilityNodeInfo,
        text: String?,
        displayText: String
    ): List<CharBox> {
        val rawText = text ?: return emptyList()
        if (rawText.isBlank() || rawText.length > MAX_CHARACTER_LOCATION_TEXT_LENGTH) return emptyList()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
        if (
            !AccessibilityCharacterBoxPolicy.shouldRequest(
                rawText = rawText,
                displayText = displayText,
                className = node.className?.toString(),
                viewIdResourceName = node.viewIdResourceName
            )
        ) {
            return emptyList()
        }

        val extraData = try {
            node.availableExtraData
        } catch (_: RuntimeException) {
            emptyList()
        }
        if (!extraData.contains(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY)) {
            return emptyList()
        }

        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, rawText.length)
        }
        val refreshed = try {
            node.refreshWithExtraData(
                AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY,
                args
            )
        } catch (_: RuntimeException) {
            false
        }
        if (!refreshed) return emptyList()

        val rects = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            node.extras.getParcelableArray(
                AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY,
                RectF::class.java
            ) ?: return emptyList()
        } else {
            @Suppress("DEPRECATION")
            node.extras.getParcelableArray(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY)
                ?: return emptyList()
        }
        return rects.mapIndexedNotNull { charIndex, value ->
            val rect = value as? RectF ?: return@mapIndexedNotNull null
            if (rect.width() <= 0f || rect.height() <= 0f) return@mapIndexedNotNull null
            if (charIndex >= rawText.length || Character.isLowSurrogate(rawText[charIndex])) {
                return@mapIndexedNotNull null
            }

            val codePoint = Character.codePointAt(rawText, charIndex)
            val nextCharIndex = (charIndex + Character.charCount(codePoint)).coerceAtMost(rawText.length)
            val start = rawText.codePointCount(0, charIndex)
            val end = rawText.codePointCount(0, nextCharIndex)
            if (end <= start) return@mapIndexedNotNull null

            CharBox(
                start = start,
                end = end,
                boundsInScreen = BoundsRect(
                    left = floor(rect.left).toInt(),
                    top = floor(rect.top).toInt(),
                    right = ceil(rect.right).toInt(),
                    bottom = ceil(rect.bottom).toInt()
                ),
                text = String(Character.toChars(codePoint))
            )
        }
    }

    private fun shouldKeepNode(
        node: AccessibilityNodeInfo,
        value: String,
        rect: Rect,
        upperCutoff: Int,
        instagramMode: Boolean,
        tiktokMode: Boolean
    ): Boolean {
        val className = node.className?.toString().orEmpty()
        val trimmed = value.trim()
        val lower = trimmed.lowercase()
        val viewId = node.viewIdResourceName.orEmpty()
        val isUserInputLike =
            className.contains("EditText", ignoreCase = true) ||
                viewId.contains("search", ignoreCase = true) ||
                viewId.contains("query", ignoreCase = true) ||
                viewId.contains("input", ignoreCase = true)
        val looksActionable = VisualTextOcrCandidateFilter.shouldAnalyze(trimmed)
        if (!node.isVisibleToUser) return false
        if (rect.bottom <= upperCutoff && !isUserInputLike && !looksActionable) return false
        if (trimmed.length == 1 && !trimmed[0].isLetterOrDigit() && trimmed[0] !in listOf('@', '#')) return false
        if (Regex(""".+님의 프로필$""").matches(trimmed)) return false
        if (Regex("""^[\u200E\u200F\u202A-\u202E]*댓글\s*\d+개$""").matches(trimmed)) return false

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
                lower == "엄지척" ||
                lower == "아래" ||
                lower == "동영상" ||
                lower == "댓글" ||
                lower == "video" ||
                lower == "notification" ||
                lower == "sticker" ||
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
        if (className.contains("Button", ignoreCase = true)) return false

        return true
    }

    private fun scoreInstagramWindow(nodes: List<ParsedTextNode>): Int {
        var score = 0

        for (node in nodes) {
            val text = node.displayText.orEmpty().trim()
            val viewId = node.viewIdResourceName.orEmpty()

            if (text.endsWith("님의 프로필로 이동") || text.endsWith("님의 스토리 보기")) score += 3
            if (text.contains("답글") && text.contains("더 보기")) score += 3
            if (looksLikeDate(text)) score += 2
            if (looksLikeUsername(text)) score += 2
            if (looksLikeInstagramCombinedComment(text)) score += 4

            if (viewId.contains("news_tab") || viewId.contains("creation_tab") || viewId.contains("profile_tab")) score -= 6
            if (viewId.contains("comment_composer_left_image_view") || viewId.contains("scrubber")) score -= 6

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
        val trimmed = text.trim()
        return trimmed.endsWith("초 전") ||
            trimmed.endsWith("분 전") ||
            trimmed.endsWith("시간 전") ||
            trimmed.endsWith("일 전") ||
            trimmed.endsWith("주 전") ||
            Regex("""^\d+월\s*\d+일$""").matches(trimmed)
    }

    private fun looksLikeUsername(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.startsWith("@")) return true

        return !trimmed.contains(" ") &&
            trimmed.length in 3..30 &&
            trimmed.all { it.isLetterOrDigit() || it == '_' || it == '.' }
    }

    private fun looksLikeInstagramCombinedComment(text: String): Boolean {
        val trimmed = text.trim()
        val match = Regex("""^([A-Za-z0-9._]{3,30})\s+(.+)$""").find(trimmed) ?: return false
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
