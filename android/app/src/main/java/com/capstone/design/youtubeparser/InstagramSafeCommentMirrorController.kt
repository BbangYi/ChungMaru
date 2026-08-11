package com.capstone.design.youtubeparser

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.OverScroller
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal class InstagramSafeCommentMirrorController(
    private val service: AccessibilityService,
    private val onNeedMore: () -> Unit,
    private val onDismiss: () -> Unit
) {
    companion object {
        private const val TAG = "InstagramMirrorUi"
    }

    private val windowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var mirrorView: InstagramSafeCommentMirrorView? = null
    private var currentSpec: MaskOverlaySpec? = null
    private var currentTouchEnabled = false

    val isActive: Boolean
        get() = mirrorView != null

    val isReady: Boolean
        get() = mirrorView?.isReady == true

    fun startLoading(spec: MaskOverlaySpec) {
        ensureView(spec, touchEnabled = false)?.setLoading(collectedCount = 0)
    }

    fun updateLoading(spec: MaskOverlaySpec, collectedCount: Int) {
        val view = ensureView(spec, touchEnabled = false) ?: return
        if (!view.isReady) view.setLoading(collectedCount)
    }

    fun showComments(
        spec: MaskOverlaySpec,
        comments: List<InstagramSafeComment>,
        prefetching: Boolean,
        emptyMessage: String? = null
    ) {
        val view = ensureView(spec, touchEnabled = true) ?: return
        view.setComments(comments, prefetching, emptyMessage)
        Log.d(TAG, "show comments count=${comments.size} ready=${view.isReady}")
    }

    fun setPrefetching(prefetching: Boolean) {
        mirrorView?.setPrefetching(prefetching)
    }

    fun setInputEnabled(enabled: Boolean) {
        val spec = currentSpec ?: return
        ensureView(spec, touchEnabled = enabled)
    }

    fun clear() {
        val view = mirrorView ?: return
        mirrorView = null
        currentSpec = null
        currentTouchEnabled = false
        runCatching { windowManager.removeView(view) }
            .onFailure { error -> Log.w(TAG, "remove mirror failed", error) }
    }

    private fun ensureView(
        spec: MaskOverlaySpec,
        touchEnabled: Boolean
    ): InstagramSafeCommentMirrorView? {
        val normalizedSpec = normalizeSpec(spec) ?: return null
        val existing = mirrorView
        if (existing == null) {
            return try {
                InstagramSafeCommentMirrorView(
                    context = service,
                    onNeedMore = onNeedMore,
                    onDismiss = onDismiss
                ).also { view ->
                    view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    view.setInputEnabled(touchEnabled)
                    windowManager.addView(view, createLayoutParams(normalizedSpec, touchEnabled))
                    mirrorView = view
                    currentSpec = normalizedSpec
                    currentTouchEnabled = touchEnabled
                }
            } catch (error: RuntimeException) {
                Log.w(TAG, "show mirror failed", error)
                null
            }
        }

        existing.setInputEnabled(touchEnabled)
        if (currentSpec != normalizedSpec || currentTouchEnabled != touchEnabled) {
            runCatching {
                windowManager.updateViewLayout(
                    existing,
                    createLayoutParams(normalizedSpec, touchEnabled)
                )
                currentSpec = normalizedSpec
                currentTouchEnabled = touchEnabled
            }.onFailure { error ->
                Log.w(TAG, "resize mirror failed", error)
            }
        }
        existing.alpha = 1f
        return existing
    }

    private fun normalizeSpec(spec: MaskOverlaySpec): MaskOverlaySpec? {
        val metrics = service.resources.displayMetrics
        val left = spec.left.coerceIn(0, metrics.widthPixels)
        val top = spec.top.coerceIn(0, metrics.heightPixels)
        val right = (spec.left + spec.width).coerceIn(left, metrics.widthPixels)
        val bottom = (spec.top + spec.height).coerceIn(top, metrics.heightPixels)
        if (right - left < 160 || bottom - top < 180) return null
        return spec.copy(
            left = left,
            top = top,
            width = right - left,
            height = bottom - top
        )
    }

    private fun createLayoutParams(
        spec: MaskOverlaySpec,
        touchEnabled: Boolean
    ): WindowManager.LayoutParams {
        val interactionFlags = if (touchEnabled) {
            0
        } else {
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        val flags =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                interactionFlags
        return WindowManager.LayoutParams(
            spec.width,
            spec.height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = spec.left
            y = spec.top
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
    }
}

private class InstagramSafeCommentMirrorView(
    context: Context,
    private val onNeedMore: () -> Unit,
    private val onDismiss: () -> Unit
) : View(context) {
    private enum class Mode {
        LOADING,
        READY
    }

    private data class BodyLine(
        val text: String,
        val left: Float,
        val baselineOffset: Float
    )

    private data class RowLayout(
        val comment: InstagramSafeComment,
        val top: Float,
        val height: Float,
        val avatarCenterX: Float,
        val textLeft: Float,
        val authorBaselineOffset: Float,
        val bodyLines: List<BodyLine>,
        val metadataBaselineOffset: Float
    )

    private val density = resources.displayMetrics.density
    private val backgroundColor = Color.rgb(33, 35, 40)
    private val primaryTextColor = Color.rgb(245, 245, 245)
    private val secondaryTextColor = Color.rgb(185, 187, 193)
    private val skeletonColor = Color.rgb(48, 51, 58)
    private val skeletonHighlightColor = Color.rgb(67, 71, 80)
    private val avatarColors = intArrayOf(
        Color.rgb(224, 64, 107),
        Color.rgb(131, 58, 180),
        Color.rgb(64, 93, 230),
        Color.rgb(252, 175, 69),
        Color.rgb(53, 168, 83)
    )

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create(
            "sans-serif",
            android.graphics.Typeface.NORMAL
        )
    }
    private val boldTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create(
            "sans-serif-medium",
            android.graphics.Typeface.NORMAL
        )
    }
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scroller = OverScroller(context)
    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean {
                scroller.forceFinished(true)
                return true
            }

            override fun onScroll(
                first: MotionEvent?,
                current: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (scrollOffset <= 0f && distanceY < 0f) {
                    pullDownDistance = (pullDownDistance - distanceY)
                        .coerceAtMost(dp(150f))
                    invalidate()
                } else {
                    pullDownDistance = 0f
                    scrollByInternal(distanceY)
                }
                return true
            }

            override fun onFling(
                first: MotionEvent?,
                current: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (pullDownDistance > 0f) return true
                scroller.fling(
                    0,
                    scrollOffset.toInt(),
                    0,
                    (-velocityY).toInt(),
                    0,
                    0,
                    0,
                    maxScroll().toInt()
                )
                postInvalidateOnAnimation()
                return true
            }
        }
    )

    private var mode = Mode.LOADING
    private var comments: List<InstagramSafeComment> = emptyList()
    private var rows: List<RowLayout> = emptyList()
    private var contentHeight = 0f
    private var scrollOffset = 0f
    private var collectedCount = 0
    private var prefetching = false
    private var inputEnabled = false
    private var emptyMessage: String? = null
    private var lastNeedMoreAtMs = 0L
    private var needMoreArmed = false
    private var pullDownDistance = 0f

    val isReady: Boolean
        get() = mode == Mode.READY

    fun setLoading(collectedCount: Int) {
        mode = Mode.LOADING
        this.collectedCount = collectedCount
        scrollOffset = 0f
        pullDownDistance = 0f
        needMoreArmed = false
        invalidate()
    }

    fun setComments(
        comments: List<InstagramSafeComment>,
        prefetching: Boolean,
        emptyMessage: String?
    ) {
        val preservePosition =
            mode == Mode.READY &&
                this.comments.map { comment -> comment.key } ==
                comments.take(this.comments.size).map { comment -> comment.key }
        mode = Mode.READY
        this.comments = comments
        this.prefetching = prefetching
        this.emptyMessage = emptyMessage
        rebuildRows()
        scrollOffset = if (preservePosition) {
            scrollOffset.coerceAtMost(maxScroll())
        } else {
            0f
        }
        invalidate()
    }

    fun setPrefetching(prefetching: Boolean) {
        this.prefetching = prefetching
        rebuildRows()
        invalidate()
    }

    fun setInputEnabled(enabled: Boolean) {
        inputEnabled = enabled
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        rebuildRows()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        backgroundPaint.color = backgroundColor
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        drawReelsHandle(canvas)
        if (mode == Mode.LOADING) {
            drawLoading(canvas)
        } else {
            drawComments(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mode != Mode.READY || !inputEnabled) return true
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            needMoreArmed = true
            pullDownDistance = 0f
        }
        gestureDetector.onTouchEvent(event)
        if (
            event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            if (
                event.actionMasked == MotionEvent.ACTION_UP &&
                scrollOffset <= 0f &&
                pullDownDistance >= dp(64f)
            ) {
                pullDownDistance = 0f
                onDismiss()
                return true
            }
            pullDownDistance = 0f
            requestMoreIfNeeded()
            invalidate()
        }
        return true
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollOffset = scroller.currY.toFloat().coerceIn(0f, maxScroll())
            requestMoreIfNeeded()
            postInvalidateOnAnimation()
        }
    }

    private fun drawLoading(canvas: Canvas) {
        val elapsed = SystemClock.uptimeMillis()
        val pulse = ((sin(elapsed / 430.0 * PI) + 1.0) * 0.5).toFloat()
        shapePaint.color = blendColor(skeletonColor, skeletonHighlightColor, pulse)
        var rowTop = dp(42f)
        repeat(5) { index ->
            val replyIndent = if (index == 1 || index == 4) dp(28f) else 0f
            val avatarCenterX = dp(18f) + replyIndent
            canvas.drawCircle(avatarCenterX, rowTop + dp(16f), dp(16f), shapePaint)
            drawRoundedBar(
                canvas,
                left = dp(46f) + replyIndent,
                top = rowTop + dp(5f),
                right = width - dp(if (index % 2 == 0) 92f else 132f),
                height = dp(10f)
            )
            drawRoundedBar(
                canvas,
                left = dp(46f) + replyIndent,
                top = rowTop + dp(26f),
                right = width - dp(if (index % 2 == 0) 56f else 86f),
                height = dp(11f)
            )
            drawRoundedBar(
                canvas,
                left = dp(46f) + replyIndent,
                top = rowTop + dp(49f),
                right = dp(112f) + replyIndent,
                height = dp(8f)
            )
            rowTop += dp(82f)
        }

        val spinnerY = min(height - dp(42f), dp(492f))
        drawSpinner(canvas, width / 2f, spinnerY, dp(10f), elapsed)
        textPaint.color = secondaryTextColor
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = sp(12f)
        val status = if (collectedCount > 0) {
            "\uB313\uAE00 \uD655\uC778 \uC911  $collectedCount"
        } else {
            "\uB313\uAE00 \uD655\uC778 \uC911"
        }
        canvas.drawText(status, width / 2f, spinnerY + dp(31f), textPaint)
        textPaint.textAlign = Paint.Align.LEFT
        postInvalidateDelayed(32L)
    }

    private fun drawComments(canvas: Canvas) {
        if (rows.isEmpty()) {
            drawEmptyState(canvas)
            return
        }
        canvas.save()
        canvas.translate(0f, -scrollOffset)
        rows.forEach { row ->
            if (row.top + row.height >= scrollOffset && row.top <= scrollOffset + height) {
                drawCommentRow(canvas, row)
            }
        }
        if (prefetching) drawPrefetchFooter(canvas)
        canvas.restore()
    }

    private fun drawReelsHandle(canvas: Canvas) {
        shapePaint.color = Color.rgb(174, 178, 187)
        val handleWidth = dp(32f)
        val handleHeight = dp(2f)
        val top = dp(12f) + pullDownDistance * 0.08f
        canvas.drawRoundRect(
            RectF(
                width / 2f - handleWidth / 2f,
                top,
                width / 2f + handleWidth / 2f,
                top + handleHeight
            ),
            handleHeight / 2f,
            handleHeight / 2f,
            shapePaint
        )
    }

    private fun drawEmptyState(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f - dp(18f)
        shapePaint.style = Paint.Style.STROKE
        shapePaint.strokeWidth = dp(1.5f)
        shapePaint.color = secondaryTextColor
        canvas.drawCircle(centerX, centerY - dp(20f), dp(17f), shapePaint)
        canvas.drawLine(
            centerX - dp(8f),
            centerY - dp(5f),
            centerX - dp(13f),
            centerY + dp(3f),
            shapePaint
        )
        shapePaint.style = Paint.Style.FILL
        textPaint.color = secondaryTextColor
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = sp(14f)
        canvas.drawText(
            emptyMessage ?: "\uD45C\uC2DC\uD560 \uC218 \uC788\uB294 \uC548\uC804\uD55C \uB313\uAE00\uC774 \uC5C6\uC2B5\uB2C8\uB2E4",
            centerX,
            centerY + dp(28f),
            textPaint
        )
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawCommentRow(canvas: Canvas, row: RowLayout) {
        val avatarCenterY = row.top + dp(18f)
        shapePaint.color = avatarColors[
            (row.comment.author.hashCode() and Int.MAX_VALUE) % avatarColors.size
        ]
        canvas.drawCircle(row.avatarCenterX, avatarCenterY, dp(16f), shapePaint)
        boldTextPaint.color = Color.WHITE
        boldTextPaint.textAlign = Paint.Align.CENTER
        boldTextPaint.textSize = sp(11f)
        val initial = row.comment.author.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
        canvas.drawText(
            initial,
            row.avatarCenterX,
            avatarCenterY - (boldTextPaint.ascent() + boldTextPaint.descent()) / 2f,
            boldTextPaint
        )
        boldTextPaint.textAlign = Paint.Align.LEFT

        boldTextPaint.color = primaryTextColor
        boldTextPaint.textSize = sp(13f)
        canvas.drawText(
            row.comment.author,
            row.textLeft,
            row.top + row.authorBaselineOffset,
            boldTextPaint
        )

        textPaint.color = primaryTextColor
        textPaint.textSize = sp(14f)
        row.bodyLines.forEach { line ->
            canvas.drawText(
                line.text,
                line.left,
                row.top + line.baselineOffset,
                textPaint
            )
        }

        boldTextPaint.color = secondaryTextColor
        boldTextPaint.textSize = sp(11.5f)
        canvas.drawText(
            "\uB2F5\uAE00 \uB2EC\uAE30",
            row.textLeft,
            row.top + row.metadataBaselineOffset,
            boldTextPaint
        )
        drawHeartOutline(
            canvas = canvas,
            centerX = width - dp(22f),
            centerY = row.top + dp(25f),
            size = dp(7.5f)
        )
    }

    private fun rebuildRows() {
        if (width <= 0 || mode != Mode.READY) return
        val newRows = mutableListOf<RowLayout>()
        var top = dp(38f)
        comments.forEach { comment ->
            val indent = if (comment.isReply) dp(30f) else 0f
            val avatarCenterX = dp(18f) + indent
            val textLeft = dp(46f) + indent
            val textRight = width - dp(43f)
            val authorBaseline = dp(21f)
            boldTextPaint.textSize = sp(13f)
            val authorWidth = boldTextPaint.measureText(comment.author)
            val bodyLines = layoutBody(
                text = comment.text,
                textLeft = textLeft,
                textRight = textRight,
                firstLeft = textLeft + authorWidth + dp(5f),
                authorBaseline = authorBaseline
            )
            val lastBodyBaseline = bodyLines.lastOrNull()?.baselineOffset ?: authorBaseline
            val metadataBaseline = max(dp(47f), lastBodyBaseline + dp(22f))
            val rowHeight = max(dp(74f), metadataBaseline + dp(20f))
            newRows += RowLayout(
                comment = comment,
                top = top,
                height = rowHeight,
                avatarCenterX = avatarCenterX,
                textLeft = textLeft,
                authorBaselineOffset = authorBaseline,
                bodyLines = bodyLines,
                metadataBaselineOffset = metadataBaseline
            )
            top += rowHeight
        }
        if (prefetching) top += dp(50f)
        rows = newRows
        contentHeight = top
        scrollOffset = scrollOffset.coerceAtMost(maxScroll())
    }

    private fun layoutBody(
        text: String,
        textLeft: Float,
        textRight: Float,
        firstLeft: Float,
        authorBaseline: Float
    ): List<BodyLine> {
        if (text.isBlank()) return emptyList()
        textPaint.textSize = sp(14f)
        val lines = mutableListOf<BodyLine>()
        var offset = 0
        var baseline = authorBaseline
        var lineLeft = firstLeft
        var availableWidth = textRight - lineLeft
        if (availableWidth < dp(64f)) {
            baseline += dp(20f)
            lineLeft = textLeft
            availableWidth = textRight - textLeft
        }

        while (offset < text.length && lines.size < 8) {
            val count = textPaint.breakText(
                text,
                offset,
                text.length,
                true,
                availableWidth.coerceAtLeast(dp(24f)),
                null
            ).coerceAtLeast(1)
            var end = (offset + count).coerceAtMost(text.length)
            if (end < text.length) {
                val whitespaceIndex = text.lastIndexOf(' ', end - 1)
                if (whitespaceIndex > offset + count / 2) end = whitespaceIndex
            }
            var line = text.substring(offset, end).trim()
            offset = end
            while (offset < text.length && text[offset].isWhitespace()) offset += 1
            if (lines.size == 7 && offset < text.length) {
                line = line.trimEnd() + "\u2026"
                offset = text.length
            }
            if (line.isNotEmpty()) {
                lines += BodyLine(line, lineLeft, baseline)
                baseline += dp(20f)
            }
            lineLeft = textLeft
            availableWidth = textRight - textLeft
        }
        return lines
    }

    private fun drawHeartOutline(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float
    ) {
        val path = Path().apply {
            moveTo(centerX, centerY + size * 0.88f)
            cubicTo(
                centerX - size * 1.38f,
                centerY + size * 0.05f,
                centerX - size * 1.05f,
                centerY - size,
                centerX - size * 0.45f,
                centerY - size
            )
            cubicTo(
                centerX - size * 0.12f,
                centerY - size,
                centerX,
                centerY - size * 0.7f,
                centerX,
                centerY - size * 0.7f
            )
            cubicTo(
                centerX,
                centerY - size * 0.7f,
                centerX + size * 0.12f,
                centerY - size,
                centerX + size * 0.45f,
                centerY - size
            )
            cubicTo(
                centerX + size * 1.05f,
                centerY - size,
                centerX + size * 1.38f,
                centerY + size * 0.05f,
                centerX,
                centerY + size * 0.88f
            )
            close()
        }
        shapePaint.style = Paint.Style.STROKE
        shapePaint.strokeWidth = dp(1.45f)
        shapePaint.strokeJoin = Paint.Join.ROUND
        shapePaint.strokeCap = Paint.Cap.ROUND
        shapePaint.color = primaryTextColor
        canvas.drawPath(path, shapePaint)
        shapePaint.style = Paint.Style.FILL
    }

    private fun drawPrefetchFooter(canvas: Canvas) {
        val footerTop = contentHeight - dp(50f)
        drawSpinner(
            canvas,
            width / 2f,
            footerTop + dp(24f),
            dp(9f),
            SystemClock.uptimeMillis()
        )
        postInvalidateDelayed(48L)
    }

    private fun drawSpinner(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        elapsed: Long
    ) {
        val dotRadius = max(dp(1.4f), radius * 0.16f)
        repeat(8) { index ->
            val angle = (index * 45.0 - 90.0) * PI / 180.0
            val alpha = 48 + ((index + elapsed / 90L) % 8).toInt() * 24
            shapePaint.color = Color.argb(
                alpha.coerceIn(0, 255),
                Color.red(secondaryTextColor),
                Color.green(secondaryTextColor),
                Color.blue(secondaryTextColor)
            )
            canvas.drawCircle(
                centerX + cos(angle).toFloat() * radius,
                centerY + sin(angle).toFloat() * radius,
                dotRadius,
                shapePaint
            )
        }
    }

    private fun scrollByInternal(deltaY: Float) {
        scrollOffset = (scrollOffset + deltaY).coerceIn(0f, maxScroll())
        requestMoreIfNeeded()
        invalidate()
    }

    private fun maxScroll(): Float = max(0f, contentHeight - height)

    private fun requestMoreIfNeeded() {
        if (prefetching || comments.isEmpty() || !needMoreArmed) return
        val remainingPx = maxScroll() - scrollOffset
        if (remainingPx > height * 0.68f) return
        val nowMs = SystemClock.uptimeMillis()
        if (nowMs - lastNeedMoreAtMs < 1_000L) return
        lastNeedMoreAtMs = nowMs
        needMoreArmed = false
        onNeedMore()
    }

    private fun drawRoundedBar(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        height: Float
    ) {
        val safeRight = max(left + dp(16f), right)
        canvas.drawRoundRect(
            RectF(left, top, safeRight, top + height),
            height / 2f,
            height / 2f,
            shapePaint
        )
    }

    private fun blendColor(start: Int, end: Int, ratio: Float): Int {
        val safeRatio = ratio.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(start) + (Color.red(end) - Color.red(start)) * safeRatio).toInt(),
            (Color.green(start) + (Color.green(end) - Color.green(start)) * safeRatio).toInt(),
            (Color.blue(start) + (Color.blue(end) - Color.blue(start)) * safeRatio).toInt()
        )
    }

    private fun dp(value: Float): Float = value * density

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics
    )
}
