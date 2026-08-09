package com.capstone.design.youtubeparser

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.OverScroller
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal enum class SafeCommentMirrorVisualStyle {
    YOUTUBE,
    INSTAGRAM
}

internal class YoutubeSafeCommentMirrorController(
    private val service: AccessibilityService,
    private val onNeedMore: () -> Unit,
    private val visualStyle: SafeCommentMirrorVisualStyle =
        SafeCommentMirrorVisualStyle.YOUTUBE
) {
    companion object {
        private const val TAG = "YoutubeSafeMirror"
    }

    private val windowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var mirrorView: YoutubeSafeCommentMirrorView? = null
    private var currentSpec: MaskOverlaySpec? = null
    private var currentTouchEnabled = false

    val isActive: Boolean
        get() = mirrorView != null

    val isReady: Boolean
        get() = mirrorView?.isReady == true

    fun startLoading(spec: MaskOverlaySpec) {
        val view = ensureView(spec, touchEnabled = false) ?: return
        view.setLoading(collectedCount = 0)
    }

    fun updateLoading(spec: MaskOverlaySpec, collectedCount: Int) {
        val view = ensureView(spec, touchEnabled = false) ?: return
        if (!view.isReady) {
            view.setLoading(collectedCount)
        }
    }

    fun showComments(
        spec: MaskOverlaySpec,
        comments: List<YoutubeSafeComment>,
        prefetching: Boolean,
        emptyMessage: String? = null
    ) {
        val view = ensureView(spec, touchEnabled = true) ?: return
        view.setComments(comments, prefetching, emptyMessage)
        Log.d(TAG, "show safe comments count=${comments.size} ready=${view.isReady}")
    }

    fun setPrefetching(prefetching: Boolean) {
        mirrorView?.setPrefetching(prefetching)
    }

    fun setInputEnabled(enabled: Boolean) {
        val spec = currentSpec ?: return
        ensureView(spec, touchEnabled = enabled)
    }


    fun suspendForCapture(): Boolean {
        val view = mirrorView ?: return false
        if (view.alpha <= 0f) return false
        view.animate().cancel()
        view.alpha = 0f
        return true
    }

    fun restoreAfterCapture(wasSuspended: Boolean) {
        if (!wasSuspended) return
        mirrorView?.alpha = 1f
    }


    fun clear() {
        val view = mirrorView ?: return
        mirrorView = null
        currentSpec = null
        currentTouchEnabled = false
        runCatching { windowManager.removeView(view) }
            .onFailure { error -> Log.w(TAG, "remove safe comment mirror failed", error) }
    }


    private fun ensureView(
        spec: MaskOverlaySpec,
        touchEnabled: Boolean
    ): YoutubeSafeCommentMirrorView? {
        val normalizedSpec = normalizeSpec(spec) ?: return null
        val existing = mirrorView
        if (existing == null) {
            return try {
                YoutubeSafeCommentMirrorView(
                    context = service,
                    onNeedMore = onNeedMore,
                    visualStyle = visualStyle
                ).also { view ->
                    view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    view.setInputEnabled(touchEnabled)
                    windowManager.addView(view, createLayoutParams(normalizedSpec, touchEnabled))
                    mirrorView = view
                    currentSpec = normalizedSpec
                    currentTouchEnabled = touchEnabled
                }
            } catch (error: RuntimeException) {
                Log.w(TAG, "show safe comment mirror failed", error)
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
                Log.w(TAG, "resize safe comment mirror failed", error)
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

        val baseFlags =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                interactionFlags
        return WindowManager.LayoutParams(
            spec.width,
            spec.height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            baseFlags,
            PixelFormat.TRANSLUCENT
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

private class YoutubeSafeCommentMirrorView(
    context: Context,
    private val onNeedMore: () -> Unit,
    private val visualStyle: SafeCommentMirrorVisualStyle
) : View(context) {
    private enum class Mode {
        LOADING,
        READY
    }

    private data class RowLayout(
        val comment: YoutubeSafeComment,
        val top: Float,
        val height: Float,
        val bodyLines: List<String>
    )

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val isDark =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    private val backgroundColor = when {
        visualStyle == SafeCommentMirrorVisualStyle.INSTAGRAM && isDark -> Color.BLACK
        isDark -> Color.rgb(15, 15, 15)
        else -> Color.WHITE
    }
    private val loadingColor =
        when {
            visualStyle == SafeCommentMirrorVisualStyle.INSTAGRAM && isDark ->
                Color.rgb(8, 8, 8)
            isDark -> Color.rgb(24, 24, 24)
            visualStyle == SafeCommentMirrorVisualStyle.INSTAGRAM -> Color.WHITE
            else -> Color.rgb(247, 248, 250)
        }
    private val primaryTextColor = if (isDark) Color.rgb(242, 242, 242) else Color.rgb(15, 15, 15)
    private val secondaryTextColor =
        if (isDark) Color.rgb(170, 170, 170) else Color.rgb(96, 96, 96)
    private val separatorColor =
        if (isDark) Color.rgb(45, 45, 45) else Color.rgb(232, 232, 232)
    private val skeletonColor =
        if (isDark) Color.rgb(58, 58, 58) else Color.rgb(220, 224, 229)
    private val skeletonHighlightColor =
        if (isDark) Color.rgb(82, 82, 82) else Color.rgb(239, 241, 244)
    private val avatarColors = intArrayOf(
        Color.rgb(36, 132, 122),
        Color.rgb(65, 105, 180),
        Color.rgb(176, 82, 91),
        Color.rgb(111, 126, 65)
    )

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
    }
    private val boldTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = separatorColor
        strokeWidth = dp(1f)
    }
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
                scrollByInternal(distanceY)
                return true
            }

            override fun onFling(
                first: MotionEvent?,
                current: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
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
    private var comments: List<YoutubeSafeComment> = emptyList()
    private var rows: List<RowLayout> = emptyList()
    private var contentHeight = 0f
    private var scrollOffset = 0f
    private var collectedCount = 0
    private var prefetching = false
    private var inputEnabled = false
    private var emptyMessage: String? = null
    private var lastNeedMoreAtMs = 0L
    private var needMoreArmed = false

    val isReady: Boolean
        get() = mode == Mode.READY

    fun setLoading(collectedCount: Int) {
        mode = Mode.LOADING
        this.collectedCount = collectedCount
        scrollOffset = 0f
        needMoreArmed = false
        invalidate()
    }

    fun setComments(
        comments: List<YoutubeSafeComment>,
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
        if (!preservePosition) {
            scrollOffset = 0f
        } else {
            scrollOffset = scrollOffset.coerceAtMost(maxScroll())
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
        if (mode == Mode.LOADING) {
            drawLoading(canvas)
        } else {
            drawComments(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mode != Mode.READY || !inputEnabled) return true
        if (event.actionMasked == MotionEvent.ACTION_DOWN) needMoreArmed = true
        val handled = gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            requestMoreIfNeeded()
        }
        return handled || true
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollOffset = scroller.currY.toFloat().coerceIn(0f, maxScroll())
            requestMoreIfNeeded()
            postInvalidateOnAnimation()
        }
    }

    private fun drawLoading(canvas: Canvas) {
        backgroundPaint.color = loadingColor
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val elapsed = SystemClock.uptimeMillis()
        val pulse = ((sin(elapsed / 420.0 * PI) + 1.0) * 0.5).toFloat()
        val skeleton = blendColor(skeletonColor, skeletonHighlightColor, pulse)
        shapePaint.color = skeleton

        val isInstagram = visualStyle == SafeCommentMirrorVisualStyle.INSTAGRAM
        val horizontal = dp(if (isInstagram) 16f else 24f)
        val avatarRadius = dp(if (isInstagram) 18f else 20f)
        val avatarCenter = horizontal + avatarRadius
        val textLeft = horizontal + dp(if (isInstagram) 52f else 56f)
        var rowTop = dp(if (isInstagram) 20f else 28f)
        repeat(if (isInstagram) 5 else 4) { index ->
            canvas.drawCircle(
                avatarCenter,
                rowTop + avatarRadius,
                avatarRadius,
                shapePaint
            )
            drawRoundedBar(
                canvas,
                left = textLeft,
                top = rowTop + dp(4f),
                right = min(
                    width - horizontal - dp(if (isInstagram) 48f else 0f),
                    textLeft + dp(118f + index * 11f)
                ),
                height = dp(if (isInstagram) 10f else 12f)
            )
            drawRoundedBar(
                canvas,
                left = textLeft,
                top = rowTop + dp(if (isInstagram) 25f else 28f),
                right = width - horizontal -
                    dp(if (isInstagram) 56f else if (index % 2 == 0) 36f else 92f),
                height = dp(if (isInstagram) 12f else 14f)
            )
            drawRoundedBar(
                canvas,
                left = textLeft,
                top = rowTop + dp(if (isInstagram) 44f else 50f),
                right = width - horizontal -
                    dp(if (isInstagram) {
                        if (index % 2 == 0) 104f else 72f
                    } else if (index % 2 == 0) {
                        112f
                    } else {
                        54f
                    }),
                height = dp(if (isInstagram) 12f else 14f)
            )
            rowTop += dp(if (isInstagram) 92f else 116f)
        }

        val centerX = width / 2f
        val centerY = min(height - dp(94f), dp(540f))
        shapePaint.style = Paint.Style.STROKE
        shapePaint.strokeWidth = dp(3f)
        shapePaint.strokeCap = Paint.Cap.ROUND
        shapePaint.color = if (isDark) Color.rgb(190, 190, 190) else Color.rgb(94, 101, 111)
        val rotation = (elapsed % 900L) * 360f / 900f
        canvas.drawArc(
            RectF(
                centerX - dp(15f),
                centerY - dp(15f),
                centerX + dp(15f),
                centerY + dp(15f)
            ),
            rotation,
            255f,
            false,
            shapePaint
        )
        shapePaint.style = Paint.Style.FILL

        textPaint.color = secondaryTextColor
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = sp(14f)
        val status = if (collectedCount > 0) {
            "안전한 댓글을 정리하고 있어요 · $collectedCount"
        } else {
            "댓글을 안전하게 정리하고 있어요"
        }
        canvas.drawText(status, centerX, centerY + dp(46f), textPaint)
        textPaint.textAlign = Paint.Align.LEFT
        postInvalidateDelayed(32L)
    }

    private fun drawComments(canvas: Canvas) {
        backgroundPaint.color = backgroundColor
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        if (rows.isEmpty()) {
            textPaint.color = secondaryTextColor
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = sp(15f)
            canvas.drawText(
                emptyMessage ?: "표시할 수 있는 안전한 댓글이 없습니다",
                width / 2f,
                height / 2f,
                textPaint
            )
            textPaint.textAlign = Paint.Align.LEFT
            return
        }

        canvas.save()
        canvas.translate(0f, -scrollOffset)
        rows.forEach { row ->
            if (row.top + row.height < scrollOffset || row.top > scrollOffset + height) {
                return@forEach
            }
            drawCommentRow(canvas, row)
        }
        if (prefetching) {
            drawPrefetchFooter(canvas)
        }
        canvas.restore()
    }

    private fun drawCommentRow(canvas: Canvas, row: RowLayout) {
        if (visualStyle == SafeCommentMirrorVisualStyle.INSTAGRAM) {
            drawInstagramCommentRow(canvas, row)
        } else {
            drawYoutubeCommentRow(canvas, row)
        }
    }

    private fun drawYoutubeCommentRow(canvas: Canvas, row: RowLayout) {
        val leftPadding = dp(20f)
        val avatarCenterX = leftPadding + dp(20f)
        val avatarCenterY = row.top + dp(30f)
        val textLeft = leftPadding + dp(54f)
        val textRight = width - dp(24f)

        shapePaint.color = avatarColors[(row.comment.author.hashCode() and Int.MAX_VALUE) % avatarColors.size]
        canvas.drawCircle(avatarCenterX, avatarCenterY, dp(20f), shapePaint)
        boldTextPaint.color = Color.WHITE
        boldTextPaint.textAlign = Paint.Align.CENTER
        boldTextPaint.textSize = sp(13f)
        val initial = row.comment.author
            .trimStart('@')
            .firstOrNull()
            ?.uppercaseChar()
            ?.toString()
            .orEmpty()
        canvas.drawText(
            initial,
            avatarCenterX,
            avatarCenterY - (boldTextPaint.ascent() + boldTextPaint.descent()) / 2f,
            boldTextPaint
        )
        boldTextPaint.textAlign = Paint.Align.LEFT

        boldTextPaint.color = secondaryTextColor
        boldTextPaint.textSize = sp(13f)
        canvas.drawText(row.comment.author, textLeft, row.top + dp(25f), boldTextPaint)
        if (row.comment.metadata.isNotBlank()) {
            val authorWidth = boldTextPaint.measureText(row.comment.author)
            textPaint.color = secondaryTextColor
            textPaint.textSize = sp(12f)
            canvas.drawText(
                " · ${row.comment.metadata}",
                min(textLeft + authorWidth, textRight - dp(80f)),
                row.top + dp(25f),
                textPaint
            )
        }

        textPaint.color = primaryTextColor
        textPaint.textSize = sp(15f)
        var baseline = row.top + dp(52f)
        row.bodyLines.forEach { line ->
            canvas.drawText(line, textLeft, baseline, textPaint)
            baseline += dp(22f)
        }

        shapePaint.color = secondaryTextColor
        val menuX = width - dp(22f)
        repeat(3) { index ->
            canvas.drawCircle(menuX, row.top + dp(19f + index * 5f), dp(1.4f), shapePaint)
        }

        canvas.drawLine(
            textLeft,
            row.top + row.height - dp(1f),
            width.toFloat(),
            row.top + row.height - dp(1f),
            separatorPaint
        )
    }

    private fun drawInstagramCommentRow(canvas: Canvas, row: RowLayout) {
        val leftPadding = dp(16f)
        val avatarCenterX = leftPadding + dp(18f)
        val avatarCenterY = row.top + dp(25f)
        val textLeft = leftPadding + dp(50f)
        val textRight = width - dp(56f)

        shapePaint.color =
            avatarColors[(row.comment.author.hashCode() and Int.MAX_VALUE) % avatarColors.size]
        canvas.drawCircle(avatarCenterX, avatarCenterY, dp(18f), shapePaint)
        boldTextPaint.color = Color.WHITE
        boldTextPaint.textAlign = Paint.Align.CENTER
        boldTextPaint.textSize = sp(12f)
        val initial = row.comment.author
            .trimStart('@')
            .firstOrNull()
            ?.uppercaseChar()
            ?.toString()
            .orEmpty()
        canvas.drawText(
            initial,
            avatarCenterX,
            avatarCenterY - (boldTextPaint.ascent() + boldTextPaint.descent()) / 2f,
            boldTextPaint
        )
        boldTextPaint.textAlign = Paint.Align.LEFT

        boldTextPaint.color = primaryTextColor
        boldTextPaint.textSize = sp(13f)
        canvas.drawText(row.comment.author.trimStart('@'), textLeft, row.top + dp(20f), boldTextPaint)
        if (row.comment.metadata.isNotBlank()) {
            val authorWidth = boldTextPaint.measureText(row.comment.author.trimStart('@'))
            textPaint.color = secondaryTextColor
            textPaint.textSize = sp(12f)
            canvas.drawText(
                "  ${row.comment.metadata}",
                min(textLeft + authorWidth, textRight - dp(64f)),
                row.top + dp(20f),
                textPaint
            )
        }

        textPaint.color = primaryTextColor
        textPaint.textSize = sp(14f)
        var baseline = row.top + dp(44f)
        row.bodyLines.forEach { line ->
            canvas.drawText(line, textLeft, baseline, textPaint)
            baseline += dp(20f)
        }

        drawHeartOutline(
            canvas = canvas,
            centerX = width - dp(25f),
            centerY = row.top + dp(42f),
            size = dp(15f)
        )
    }

    private fun drawHeartOutline(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float
    ) {
        val path = Path().apply {
            moveTo(centerX, centerY + size * 0.72f)
            cubicTo(
                centerX - size * 1.08f,
                centerY + size * 0.08f,
                centerX - size * 0.92f,
                centerY - size * 0.72f,
                centerX - size * 0.36f,
                centerY - size * 0.72f
            )
            cubicTo(
                centerX - size * 0.08f,
                centerY - size * 0.72f,
                centerX,
                centerY - size * 0.5f,
                centerX,
                centerY - size * 0.5f
            )
            cubicTo(
                centerX,
                centerY - size * 0.5f,
                centerX + size * 0.08f,
                centerY - size * 0.72f,
                centerX + size * 0.36f,
                centerY - size * 0.72f
            )
            cubicTo(
                centerX + size * 0.92f,
                centerY - size * 0.72f,
                centerX + size * 1.08f,
                centerY + size * 0.08f,
                centerX,
                centerY + size * 0.72f
            )
            close()
        }
        shapePaint.style = Paint.Style.STROKE
        shapePaint.strokeWidth = dp(1.5f)
        shapePaint.strokeJoin = Paint.Join.ROUND
        shapePaint.strokeCap = Paint.Cap.ROUND
        shapePaint.color = primaryTextColor
        canvas.drawPath(path, shapePaint)
        shapePaint.style = Paint.Style.FILL
    }

    private fun drawPrefetchFooter(canvas: Canvas) {
        val footerTop = contentHeight - dp(54f)
        if (visualStyle == SafeCommentMirrorVisualStyle.INSTAGRAM) {
            shapePaint.style = Paint.Style.STROKE
            shapePaint.strokeWidth = dp(2f)
            shapePaint.strokeCap = Paint.Cap.ROUND
            shapePaint.color = secondaryTextColor
            val centerX = width / 2f
            val centerY = footerTop + dp(27f)
            val rotation = (SystemClock.uptimeMillis() % 900L) * 360f / 900f
            canvas.drawArc(
                RectF(
                    centerX - dp(9f),
                    centerY - dp(9f),
                    centerX + dp(9f),
                    centerY + dp(9f)
                ),
                rotation,
                255f,
                false,
                shapePaint
            )
            shapePaint.style = Paint.Style.FILL
            postInvalidateDelayed(48L)
            return
        }
        textPaint.color = secondaryTextColor
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = sp(13f)
        canvas.drawText("댓글 불러오는 중", width / 2f, footerTop + dp(31f), textPaint)
        textPaint.textAlign = Paint.Align.LEFT
        postInvalidateDelayed(48L)
    }

    private fun rebuildRows() {
        if (width <= 0 || mode != Mode.READY) return
        val isInstagram = visualStyle == SafeCommentMirrorVisualStyle.INSTAGRAM
        val availableTextWidth = width - dp(if (isInstagram) 122f else 98f)
        val newRows = mutableListOf<RowLayout>()
        var top = 0f
        comments.forEach { comment ->
            val lines = wrapText(comment.text, availableTextWidth, maxLines = 8)
            val rowHeight = if (isInstagram) {
                max(dp(82f), dp(52f) + lines.size * dp(20f))
            } else {
                max(dp(92f), dp(69f) + lines.size * dp(22f))
            }
            newRows += RowLayout(
                comment = comment,
                top = top,
                height = rowHeight,
                bodyLines = lines
            )
            top += rowHeight
        }
        if (prefetching) top += dp(54f)
        rows = newRows
        contentHeight = top
        scrollOffset = scrollOffset.coerceAtMost(maxScroll())
    }

    private fun wrapText(text: String, maxWidth: Float, maxLines: Int): List<String> {
        if (text.isBlank() || maxWidth <= 0f) return emptyList()
        textPaint.textSize = sp(15f)
        val lines = mutableListOf<String>()
        var offset = 0
        while (offset < text.length && lines.size < maxLines) {
            val count = textPaint.breakText(text, offset, text.length, true, maxWidth, null)
                .coerceAtLeast(1)
            var end = (offset + count).coerceAtMost(text.length)
            if (end < text.length) {
                val whitespaceIndex = text.lastIndexOf(' ', end - 1)
                if (whitespaceIndex > offset + count / 2) {
                    end = whitespaceIndex
                }
            }
            var line = text.substring(offset, end).trim()
            offset = end
            while (offset < text.length && text[offset].isWhitespace()) offset += 1
            if (lines.size == maxLines - 1 && offset < text.length) {
                line = line.trimEnd() + "…"
                offset = text.length
            }
            if (line.isNotEmpty()) lines += line
        }
        return lines
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
        if (remainingPx > height * 0.72f) return
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

    private fun sp(value: Float): Float = value * scaledDensity
}
