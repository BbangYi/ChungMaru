package com.capstone.design.youtubeparser

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
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
        private const val PARSE_DELAY_SCROLL_MS = 32L
        private const val SCROLL_OVERLAY_STABILIZATION_MS = 64L
        private const val CONTENT_OVERLAY_STABILIZATION_MS = 48L
        private const val SCROLL_CONTENT_CHANGE_PRESERVE_MS =
            SCROLL_OVERLAY_STABILIZATION_MS + CONTENT_OVERLAY_STABILIZATION_MS
        private const val OVERLAY_SELF_CONTENT_CHANGE_GRACE_MS = 64L
        private const val PARSE_DELAY_CONTENT_MS = 40L
        private const val PARSE_DELAY_WINDOW_MS = 60L
        private const val RETRY_AFTER_IN_FLIGHT_MS = 16L
        private const val VISUAL_SUPPLEMENT_CACHE_TTL_MS = 1800L
        private const val VISUAL_ANALYSIS_TIMEOUT_MS = 1800L
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
        private const val CACHE_PROMOTION_THROTTLE_MS = 48L
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

    private val handler = Handler(Looper.getMainLooper())
    private var lastSnapshotSignature: String? = null
    private var lastUploadAt: Long = 0L
    private var lastObservedPackage: String? = null
    private val maskOverlayController by lazy { MaskOverlayController(this) }
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
        if (visualCaptureState.supported) {
            visualExecutor.execute {
                visualTextOcrProcessor.warmUp()
            }
        }
    }

    @SuppressLint("SwitchIntDef")
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val callbackReceivedAtMs = SystemClock.uptimeMillis()

        val packageName = event.packageName?.toString() ?: return
        if (!shouldObservePackage(packageName)) {
            clearOverlayForExitPackageIfNeeded(packageName)
            return
        }

        lastObservedPackage = packageName

        when (event.eventType) {
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                lastPointerInteractionAtMs = SystemClock.uptimeMillis()
                if (event.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
                    renderRiskGateForEvent(
                        event = event,
                        packageName = packageName,
                        eventTimeMs = event.eventTime,
                        serviceReceivedAtMs = callbackReceivedAtMs
                    )
                }
                if (event.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_END) {
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
                val contentChangedWithActiveMask =
                    event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                        hasActiveMasks &&
                        !overlaySelfContentChange
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

                val riskGateMaskMs = if (!overlaySelfContentChange) {
                    renderRiskGateForEvent(
                        event = event,
                        packageName = packageName,
                        eventTimeMs = event.eventTime,
                        serviceReceivedAtMs = callbackReceivedAtMs
                    )
                } else {
                    -1L
                }

                val fastProvisionalMaskMs = if (!overlaySelfContentChange && riskGateMaskMs < 0L) {
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
                    if (fastProvisionalMaskMs >= 0L) fastProvisionalMaskMs else browserRootFastMaskMs

                if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                    lastScrollEventAtMs = SystemClock.uptimeMillis()
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
                            Log.d(TAG, "hide mask overlay until scroll recapture: unresolved delta")
                            clearMaskOverlay()
                            scheduleDeferredFollowUpParse()
                        } else if (scrollTranslation.shouldHideUntilRecapture && hasActiveMasks) {
                            Log.d(TAG, "hide mask overlay until scroll recapture status=${scrollTranslation.status}")
                            clearMaskOverlay()
                            scheduleDeferredFollowUpParse(waitForScrollStabilization = true)
                        } else {
                            markOverlayRevisionStale()
                            shouldPromoteCachedMasks = !hasActiveMasks
                        }
                        if (shouldPromoteCachedMasks) {
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
                } else if (shouldClearOverlayImmediately(event.eventType) && riskGateMaskMs < 0L) {
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
        clearMaskOverlay()
        Log.d(TAG, "service interrupted")
    }

    override fun onDestroy() {
        cancelScheduledParse()
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
        val candidatePostProcessingMs = SystemClock.uptimeMillis() - candidatePostProcessingStartedAtMs
        val candidateExtractionMs = SystemClock.uptimeMillis() - parseStartedAtMs

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
                renderProvisionalAccessibilityMaskOverlay(
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
            val deferClearForVisualOnlyAnalysis =
                MaskOverlayEventPolicy.shouldDeferClearForVisualOnlyAnalysis(
                    hasActiveMasks = maskOverlayController.hasActiveMasks(),
                    hasRenderableVisualRois = visualRoiPlan.hasRenderableVisualRois()
                )
            if (
                experimentMode.ocrStageEnabled &&
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
            pendingParseAfterAnalysis = true
            Log.d(TAG, "defer snapshot: analysis already in flight")
            return
        }

        if (!experimentMode.backendStageEnabled) {
            val visualStarted = experimentMode.ocrStageEnabled &&
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
        val accessibilityMaskLatencyMs = if (experimentMode.overlayStageEnabled) {
            renderProvisionalAccessibilityMaskOverlay(
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
                if (experimentMode.overlayStageEnabled) {
                    handler.post {
                        updateMaskOverlay(
                            currentPackage = currentPackage,
                            analysis = analysis,
                            snapshotOverlayRevision = snapshotOverlayRevision,
                            visualRoiPlan = visualRoiPlan,
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
                if (analysisForOverlay == null) {
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
            packageName == YOUTUBE_PACKAGE ||
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

        if (analysis?.ok == true && isInScrollStabilizationWindow() && !allowDuringScrollStabilization) {
            Log.d(TAG, "defer mask overlay render: scroll stabilization active")
            scheduleDeferredFollowUpParse(waitForScrollStabilization = true)
            return
        }

        if (supportsMaskOverlay(currentPackage) && analysis?.ok == true) {
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
        return isInLastMotionWindow(SCROLL_OVERLAY_STABILIZATION_MS)
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
        if (lastMotionEventAtMs <= 0L) return 0L

        val elapsedMs = SystemClock.uptimeMillis() - lastMotionEventAtMs
        if (elapsedMs < 0L) return SCROLL_OVERLAY_STABILIZATION_MS

        return (SCROLL_OVERLAY_STABILIZATION_MS - elapsedMs).coerceAtLeast(0L)
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

    private fun translateMaskOverlayForScroll(event: AccessibilityEvent): ScrollTranslationResult {
        val hasActiveMasks = maskOverlayController.hasActiveMasks()
        val scrollDelta = MaskOverlayEventPolicy.resolveScrollTranslationDelta(
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

    private fun promoteCachedMasksForCurrentWindow() {
        val now = SystemClock.uptimeMillis()
        if (now - lastCachePromotionAtMs < CACHE_PROMOTION_THROTTLE_MS) return
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
        val provisionalResponse = ProvisionalAccessibilityMaskBuilder.buildResponse(
            candidates = screenCandidates,
            timestamp = System.currentTimeMillis()
        )
        var visualRoiPlan: VisualTextRoiPlan? = null
        fun currentVisualRoiPlan(): VisualTextRoiPlan {
            return visualRoiPlan ?: buildVisualTextRoiPlan(nodes).also { plan ->
                visualRoiPlan = plan
            }
        }
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

    private fun supportsMaskOverlay(packageName: String): Boolean {
        return shouldObservePackage(packageName)
    }

    private fun usesViewportStableBrowserOverlay(packageName: String): Boolean {
        return packageName in BROWSER_PACKAGES
    }

    private fun clearOverlayForExitPackageIfNeeded(packageName: String) {
        if (packageName !in OVERLAY_EXIT_PACKAGES) return

        val hasActiveMasks = maskOverlayController.hasActiveMasks()
        if (!hasActiveMasks && lastObservedPackage == null) return

        cancelScheduledParse()
        lastObservedPackage = null
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
            VISUAL_ANALYSIS_TIMEOUT_MS
        )

        try {
            lastScreenshotRequestAtMs = SystemClock.uptimeMillis()
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                visualExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        if (isVisualAnalysisStale(visualRunId, snapshotVisualSceneRevision)) {
                            finishVisualAnalysis(visualRunId)
                            return
                        }

                        val screenshot = screenshotResult.toSoftwareBitmap()
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
            return false
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
                            visualRoiPlan = visualRoiPlan
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

    private fun extractVisibleTextNodesFromYoutubeWindows(): List<ParsedTextNode> {
        val out = mutableListOf<ParsedTextNode>()
        val seenRootKeys = mutableSetOf<String>()

        fun addRoot(root: AccessibilityNodeInfo?) {
            if (root == null) return
            if (root.packageName?.toString() != YOUTUBE_PACKAGE) return

            val rect = Rect().also { root.getBoundsInScreen(it) }
            val rootKey = "${rect.left},${rect.top},${rect.right},${rect.bottom},${root.className}"
            if (!seenRootKeys.add(rootKey)) return

            out += collectRawNodesFromRoot(root)
        }

        addRoot(rootInActiveWindow)
        windows?.forEach { window ->
            addRoot(window.root)
        }

        return deduplicateNodes(out)
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
            candidates += WindowCandidate(
                label = "window-$index-${windowTypeName(window)}",
                root = root,
                rawNodes = raw,
                score = scoreInstagramWindow(raw)
            )
        }

        val best = candidates.maxByOrNull { it.score }
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

            val parsed = nodeToParsedTextNode(node, rect.takeIf { hasUsableBounds }) ?: run {
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

    private fun collectRawNodesFromRoot(root: AccessibilityNodeInfo): List<ParsedTextNode> {
        val out = mutableListOf<ParsedTextNode>()

        fun dfs(node: AccessibilityNodeInfo?) {
            if (node == null) return

            val parsed = nodeToParsedTextNode(node)
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

    private fun nodeToParsedTextNode(node: AccessibilityNodeInfo, precomputedRect: Rect? = null): ParsedTextNode? {
        val text = node.text?.toString()
        val contentDescription = node.contentDescription?.toString()
        val value = when {
            !text.isNullOrBlank() -> text.trim()
            !contentDescription.isNullOrBlank() -> contentDescription.trim()
            else -> null
        } ?: return null

        val rect = precomputedRect ?: Rect().also { node.getBoundsInScreen(it) }
        if (rect.width() <= 0 || rect.height() <= 0) {
            return null
        }

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
            charBoxes = requestTextCharacterBoxes(node = node, text = text, displayText = value)
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
