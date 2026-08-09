package com.capstone.design.youtubeparser

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.SystemClock
import android.util.Log

internal class InstagramSafeCommentMirrorSession(
    private val service: AccessibilityService,
    private val handler: Handler,
    private val isInstagramForeground: () -> Boolean,
    private val captureSnapshot: (String) -> ParseSnapshot,
    private val scrollForward: () -> Boolean,
    private val isPanelPresent: () -> Boolean,
    private val cancelGeneralAnalysis: () -> Unit,
    private val clearLegacyOverlay: () -> Unit
) {
    companion object {
        private const val TAG = "InstagramSafeMirror"
        private const val INITIAL_FORWARD_STEPS = 5
        private const val PREFETCH_FORWARD_STEPS = 2
        private const val VIEWPORT_SETTLE_MS = 620L
        private const val NEXT_STEP_DELAY_MS = 120L
        private const val BATCH_TIMEOUT_MS = 15_000L
        private const val INITIAL_TIMEOUT_MS = 28_000L
        private const val INITIAL_RECOVERY_TIMEOUT_MS = 17_000L
        private const val MAX_EMPTY_RETRIES = 2
        private const val MAX_INITIAL_RECOVERY_ATTEMPTS = 1
        private const val PANEL_PRESENCE_DELAY_MS = 120L
        private const val PANEL_MISSING_GRACE_MS = 900L
        private const val PANEL_OPENING_GRACE_MS = 900L
        private const val SYNTHETIC_SCROLL_GRACE_MS = 1_200L
    }

    private enum class CollectionMode {
        IDLE,
        INITIAL,
        PREFETCH
    }

    private val mirrorController by lazy {
        YoutubeSafeCommentMirrorController(
            service = service,
            onNeedMore = { startPrefetch() },
            visualStyle = SafeCommentMirrorVisualStyle.INSTAGRAM
        )
    }
    private val safeCommentBuffer = YoutubeSafeCommentBuffer()

    private var panelSpec: MaskOverlaySpec? = null
    private var sessionRunId = 0L
    private var captureAttemptId = 0L
    private var collectionMode = CollectionMode.IDLE
    private var forwardSteps = 0
    private var capturedViewports = 0
    private var emptyRetries = 0
    private var awaitingBatch = false
    private var expectedSnapshotTimestampMs = 0L
    private var reachedEnd = false
    private var initialRecoveryAttempts = 0
    private var panelMissingSinceMs = 0L
    private var panelNativeObserved = false
    private var sessionStartedAtUptimeMs = 0L
    private var sessionStartedAtEpochMs = 0L
    private var syntheticScrollUntilMs = 0L
    private val seenViewportSignatures = linkedSetOf<String>()

    val isActive: Boolean
        get() = mirrorController.isActive

    val isReady: Boolean
        get() = mirrorController.isReady

    val currentPanelSpec: MaskOverlaySpec?
        get() = panelSpec

    fun showSurface(spec: MaskOverlaySpec, reason: String): Boolean {
        if (!isInstagramForeground()) return false
        val metrics = service.resources.displayMetrics
        val surfaceSpec = YoutubeSkeletonMaskBuilder.stabilizeLoadingPaneSpec(
            previousSpec = panelSpec,
            currentSpec = spec,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels
        )
        panelSpec = surfaceSpec

        if (!mirrorController.isActive) {
            beginSession(surfaceSpec)
        } else if (mirrorController.isReady) {
            mirrorController.showComments(
                spec = surfaceSpec,
                comments = safeCommentBuffer.comments(),
                prefetching = collectionMode == CollectionMode.PREFETCH,
                emptyMessage = emptyStateMessage()
            )
        } else {
            mirrorController.updateLoading(
                spec = surfaceSpec,
                collectedCount = safeCommentBuffer.comments().size
            )
        }

        if (!mirrorController.isActive) {
            reset("overlay-create-failed")
            return false
        }
        clearLegacyOverlay()
        Log.d(
            TAG,
            "render surface reason=$reason ready=${mirrorController.isReady} " +
                "safe=${safeCommentBuffer.comments().size}"
        )
        return true
    }

    fun schedulePresenceAudit(
        reason: String,
        delayMs: Long = PANEL_PRESENCE_DELAY_MS
    ) {
        if (!mirrorController.isActive) return
        val runId = sessionRunId
        handler.postDelayed(
            {
                if (
                    runId != sessionRunId ||
                    !mirrorController.isActive ||
                    !isInstagramForeground()
                ) {
                    return@postDelayed
                }
                if (isPanelPresent()) {
                    panelNativeObserved = true
                    panelMissingSinceMs = 0L
                    return@postDelayed
                }

                val openingGraceRemainingMs = openingGraceRemainingMs()
                if (openingGraceRemainingMs > 0L) {
                    schedulePresenceAudit(
                        reason = "opening-grace:$reason",
                        delayMs = openingGraceRemainingMs
                    )
                    return@postDelayed
                }

                val nowMs = SystemClock.uptimeMillis()
                val transitionActive =
                    collectionMode != CollectionMode.IDLE ||
                        nowMs < syntheticScrollUntilMs
                if (transitionActive) {
                    panelMissingSinceMs = 0L
                    schedulePresenceAudit(
                        reason = "panel-transition:$reason",
                        delayMs = PANEL_MISSING_GRACE_MS
                    )
                    return@postDelayed
                }

                if (panelMissingSinceMs == 0L) {
                    panelMissingSinceMs = nowMs
                }
                val missingForMs = nowMs - panelMissingSinceMs
                if (
                    !MaskOverlayEventPolicy.shouldRemoveYoutubeMirrorAfterPanelMiss(
                        mirrorReady = false,
                        panelPresent = false,
                        panelTransitionActive = false,
                        missingForMs = missingForMs,
                        missingGraceMs = PANEL_MISSING_GRACE_MS
                    )
                ) {
                    schedulePresenceAudit(
                        reason = reason,
                        delayMs = PANEL_MISSING_GRACE_MS - missingForMs
                    )
                    return@postDelayed
                }

                Log.d(
                    TAG,
                    "remove mirror after panel disappeared reason=$reason " +
                        "missingForMs=$missingForMs"
                )
                reset("comment-panel-missing:$reason")
                clearLegacyOverlay()
            },
            delayMs.coerceAtLeast(1L)
        )
    }

    fun reset(reason: String) {
        if (mirrorController.isActive) {
            Log.d(TAG, "reset reason=$reason")
        }
        sessionRunId += 1L
        captureAttemptId += 1L
        panelSpec = null
        collectionMode = CollectionMode.IDLE
        forwardSteps = 0
        capturedViewports = 0
        emptyRetries = 0
        awaitingBatch = false
        expectedSnapshotTimestampMs = 0L
        reachedEnd = false
        initialRecoveryAttempts = 0
        panelMissingSinceMs = 0L
        panelNativeObserved = false
        sessionStartedAtUptimeMs = 0L
        sessionStartedAtEpochMs = 0L
        syntheticScrollUntilMs = 0L
        seenViewportSignatures.clear()
        safeCommentBuffer.clear()
        mirrorController.clear()
    }

    private fun beginSession(spec: MaskOverlaySpec) {
        cancelGeneralAnalysis()
        sessionRunId += 1L
        captureAttemptId += 1L
        panelSpec = spec
        collectionMode = CollectionMode.INITIAL
        forwardSteps = 0
        capturedViewports = 0
        emptyRetries = 0
        awaitingBatch = false
        expectedSnapshotTimestampMs = 0L
        reachedEnd = false
        initialRecoveryAttempts = 0
        panelMissingSinceMs = 0L
        panelNativeObserved = isPanelPresent()
        sessionStartedAtUptimeMs = SystemClock.uptimeMillis()
        sessionStartedAtEpochMs = System.currentTimeMillis()
        syntheticScrollUntilMs = 0L
        seenViewportSignatures.clear()
        safeCommentBuffer.clear()
        mirrorController.startLoading(spec)

        val runId = sessionRunId
        scheduleInitialTimeout(runId, INITIAL_TIMEOUT_MS)
        scheduleCurrentViewportCapture("initial-top")
        Log.d(TAG, "begin session run=$runId")
    }

    private fun analyzeSnapshot(
        runId: Long,
        attemptId: Long,
        snapshot: ParseSnapshot,
        source: String
    ) {
        expectedSnapshotTimestampMs = snapshot.timestamp
        Thread(
            {
                val analysis = runCatching {
                    AndroidAnalysisClient.analyzeSnapshot(service.applicationContext, snapshot)
                }
                val response = analysis.getOrNull()
                    ?.takeIf { attempt -> attempt.ok }
                    ?.response
                if (response != null) {
                    onAccessibilityResults(
                        runId = runId,
                        attemptId = attemptId,
                        results = response.results,
                        snapshotTimestampMs = snapshot.timestamp
                    )
                    return@Thread
                }

                analysis.exceptionOrNull()?.let { error ->
                    Log.w(TAG, "analysis failed run=$runId source=$source", error)
                }
                handler.post {
                    if (
                        runId != sessionRunId ||
                        attemptId != captureAttemptId ||
                        !awaitingBatch ||
                        expectedSnapshotTimestampMs != snapshot.timestamp
                    ) {
                        return@post
                    }
                    awaitingBatch = false
                    expectedSnapshotTimestampMs = 0L
                    if (emptyRetries < MAX_EMPTY_RETRIES) {
                        emptyRetries += 1
                        scheduleCurrentViewportCapture(
                            "analysis-retry-$emptyRetries"
                        )
                    } else {
                        finishCollection("analysis-failure")
                    }
                }
            },
            "InstagramMirrorAnalysis-$runId"
        ).start()
    }

    private fun onAccessibilityResults(
        runId: Long,
        attemptId: Long,
        results: List<AndroidAnalysisResultItem>,
        snapshotTimestampMs: Long
    ) {
        val accessibilityResults = results.filter { item ->
            item.authorId.orEmpty()
                .removePrefix("android-accessibility-lookahead:")
                .startsWith(INSTAGRAM_COMMENT_AUTHOR_SOURCE_PREFIX)
        }
        val batch = InstagramSafeCommentAssembler
            .assembleAccessibilityResults(accessibilityResults)
        val signature = accessibilityResults
            .sortedBy { item -> "${item.authorId.orEmpty()}|${item.original}" }
            .joinToString("|") { item ->
                val source = item.authorId.orEmpty()
                    .removePrefix("android-accessibility-lookahead:")
                    .lowercase()
                val normalized = item.original
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .lowercase()
                "$source|$normalized|${item.isOffensive}"
            }

        handler.post {
            if (
                runId != sessionRunId ||
                attemptId != captureAttemptId ||
                collectionMode == CollectionMode.IDLE
            ) {
                return@post
            }
            if (snapshotTimestampMs < sessionStartedAtEpochMs) {
                Log.d(
                    TAG,
                    "drop stale batch snapshot=$snapshotTimestampMs " +
                        "session=$sessionStartedAtEpochMs"
                )
                return@post
            }
            if (
                expectedSnapshotTimestampMs > 0L &&
                snapshotTimestampMs != expectedSnapshotTimestampMs
            ) {
                Log.d(
                    TAG,
                    "drop unexpected batch snapshot=$snapshotTimestampMs " +
                        "expected=$expectedSnapshotTimestampMs"
                )
                return@post
            }
            if (!awaitingBatch) return@post

            awaitingBatch = false
            expectedSnapshotTimestampMs = 0L
            captureAttemptId += 1L
            val isNewViewport = signature.isNotBlank() &&
                seenViewportSignatures.add(signature)
            if (isNewViewport) {
                capturedViewports += 1
                val added = safeCommentBuffer.add(batch)
                Log.d(
                    TAG,
                    "collect viewport mode=$collectionMode viewport=$capturedViewports " +
                        "raw=${batch.rawLineCount} added=$added " +
                        "safe=${safeCommentBuffer.comments().size} " +
                        "harmful=${safeCommentBuffer.harmfulCommentCount}"
                )
            }

            val currentSpec = panelSpec ?: return@post
            val safeComments = safeCommentBuffer.comments()
            val keepInitialLoading =
                collectionMode == CollectionMode.INITIAL &&
                    forwardSteps < INITIAL_FORWARD_STEPS
            if (!keepInitialLoading) {
                if (mirrorController.isReady || safeComments.isNotEmpty()) {
                    mirrorController.showComments(
                        spec = currentSpec,
                        comments = safeComments,
                        prefetching = collectionMode != CollectionMode.IDLE,
                        emptyMessage = emptyStateMessage()
                    )
                } else {
                    mirrorController.updateLoading(
                        spec = currentSpec,
                        collectedCount = safeComments.size
                    )
                }
            }

            if (batch.rawLineCount == 0 && emptyRetries < MAX_EMPTY_RETRIES) {
                emptyRetries += 1
                scheduleCurrentViewportCapture("empty-$emptyRetries")
                return@post
            }
            emptyRetries = 0

            when (collectionMode) {
                CollectionMode.INITIAL -> {
                    val shouldFinish = reachedEnd ||
                        safeCommentBuffer.shouldFinishInitialCollection(
                            capturedViewports = capturedViewports,
                            forwardSteps = forwardSteps,
                            maxForwardSteps = INITIAL_FORWARD_STEPS
                        )
                    if (shouldFinish) {
                        finishCollection("initial-target")
                    } else {
                        advanceCollection()
                    }
                }

                CollectionMode.PREFETCH -> {
                    if (reachedEnd || forwardSteps >= PREFETCH_FORWARD_STEPS) {
                        finishCollection("prefetch-target")
                    } else {
                        advanceCollection()
                    }
                }

                CollectionMode.IDLE -> Unit
            }
        }
    }

    private fun advanceCollection() {
        if (!isCollectionActive()) return
        val maxSteps = when (collectionMode) {
            CollectionMode.INITIAL -> INITIAL_FORWARD_STEPS
            CollectionMode.PREFETCH -> PREFETCH_FORWARD_STEPS
            CollectionMode.IDLE -> 0
        }
        if (forwardSteps >= maxSteps) {
            finishCollection("step-limit")
            return
        }
        if (!scrollForward()) {
            reachedEnd = true
            finishCollection("native-end")
            return
        }

        forwardSteps += 1
        syntheticScrollUntilMs =
            SystemClock.uptimeMillis() + SYNTHETIC_SCROLL_GRACE_MS
        scheduleCurrentViewportCapture("forward-$forwardSteps")
    }

    private fun scheduleCurrentViewportCapture(reason: String) {
        if (!isCollectionActive()) return
        val runId = sessionRunId
        val attemptId = captureAttemptId + 1L
        captureAttemptId = attemptId
        awaitingBatch = true
        expectedSnapshotTimestampMs = 0L

        handler.postDelayed(
            {
                if (
                    runId != sessionRunId ||
                    attemptId != captureAttemptId ||
                    !awaitingBatch
                ) {
                    return@postDelayed
                }
                val snapshot = runCatching {
                    captureSnapshot(reason)
                }.onFailure { error ->
                    Log.w(TAG, "snapshot capture failed source=$reason", error)
                }.getOrElse {
                    ParseSnapshot(
                        timestamp = System.currentTimeMillis(),
                        comments = emptyList()
                    )
                }
                analyzeSnapshot(
                    runId = runId,
                    attemptId = attemptId,
                    snapshot = snapshot,
                    source = reason
                )
            },
            if (reason.startsWith("forward-") || reason == "initial-top") {
                VIEWPORT_SETTLE_MS
            } else {
                NEXT_STEP_DELAY_MS
            }
        )
        handler.postDelayed(
            {
                if (
                    runId == sessionRunId &&
                    attemptId == captureAttemptId &&
                    awaitingBatch
                ) {
                    awaitingBatch = false
                    expectedSnapshotTimestampMs = 0L
                    if (emptyRetries < MAX_EMPTY_RETRIES) {
                        emptyRetries += 1
                        scheduleCurrentViewportCapture("timeout-$emptyRetries")
                    } else {
                        emptyRetries = 0
                        advanceCollection()
                    }
                }
            },
            BATCH_TIMEOUT_MS
        )
    }

    private fun scheduleInitialTimeout(runId: Long, delayMs: Long) {
        handler.postDelayed(
            {
                if (
                    runId != sessionRunId ||
                    collectionMode != CollectionMode.INITIAL
                ) {
                    return@postDelayed
                }
                if (
                    safeCommentBuffer.rawLineCount == 0 &&
                    initialRecoveryAttempts < MAX_INITIAL_RECOVERY_ATTEMPTS
                ) {
                    initialRecoveryAttempts += 1
                    captureAttemptId += 1L
                    awaitingBatch = false
                    emptyRetries = 0
                    scheduleCurrentViewportCapture(
                        "initial-recovery-$initialRecoveryAttempts"
                    )
                    scheduleInitialTimeout(
                        runId = runId,
                        delayMs = INITIAL_RECOVERY_TIMEOUT_MS
                    )
                } else {
                    finishCollection(
                        if (safeCommentBuffer.rawLineCount == 0) {
                            "initial-timeout-empty"
                        } else {
                            "initial-timeout"
                        }
                    )
                }
            },
            delayMs
        )
    }

    private fun startPrefetch() {
        if (!mirrorController.isReady) return
        if (collectionMode != CollectionMode.IDLE) return
        if (reachedEnd || !isInstagramForeground()) return

        collectionMode = CollectionMode.PREFETCH
        forwardSteps = 0
        capturedViewports = 0
        emptyRetries = 0
        awaitingBatch = false
        mirrorController.setPrefetching(true)
        mirrorController.setInputEnabled(false)
        Log.d(TAG, "start prefetch run=$sessionRunId")
        advanceCollection()
    }

    private fun finishCollection(reason: String) {
        if (!mirrorController.isActive) return
        val previousMode = collectionMode
        val readySpec = panelSpec
        collectionMode = CollectionMode.IDLE
        awaitingBatch = false
        expectedSnapshotTimestampMs = 0L
        captureAttemptId += 1L
        clearLegacyOverlay()
        readySpec?.let { spec ->
            mirrorController.showComments(
                spec = spec,
                comments = safeCommentBuffer.comments(),
                prefetching = false,
                emptyMessage = emptyStateMessage()
            )
        }
        Log.d(
            TAG,
            "finish reason=$reason mode=$previousMode " +
                "safe=${safeCommentBuffer.comments().size} " +
                "raw=${safeCommentBuffer.rawLineCount} " +
                "harmful=${safeCommentBuffer.harmfulCommentCount}"
        )
    }

    private fun isCollectionActive(): Boolean {
        return mirrorController.isActive &&
            collectionMode != CollectionMode.IDLE &&
            isInstagramForeground()
    }

    private fun openingGraceRemainingMs(): Long {
        if (panelNativeObserved) return 0L
        if (sessionStartedAtUptimeMs <= 0L) return 0L
        val elapsedMs = SystemClock.uptimeMillis() - sessionStartedAtUptimeMs
        return (PANEL_OPENING_GRACE_MS - elapsedMs).coerceAtLeast(0L)
    }

    private fun emptyStateMessage(): String? {
        return if (safeCommentBuffer.rawLineCount == 0) {
            "\uB313\uAE00\uC744 \uBD88\uB7EC\uC624\uC9C0 " +
                "\uBABB\uD588\uC2B5\uB2C8\uB2E4. " +
                "\uB2E4\uC2DC \uC5F4\uC5B4\uC8FC\uC138\uC694"
        } else {
            null
        }
    }
}
