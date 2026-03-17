package com.example.assistivescroll

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

@Suppress("DEPRECATION")
@SuppressLint("AccessibilityUsage", "ClickableViewAccessibility")
class MyScrollService : AccessibilityService() {

    // --- UIコンポーネント管理 ---
    private lateinit var windowManager: WindowManager
    private lateinit var floatingButton: View
    private lateinit var floatingButtonParams: WindowManager.LayoutParams
    private lateinit var slimeView: SlimeIndicatorView
    private lateinit var slimeParams: WindowManager.LayoutParams

    // --- タッチ・ドラッグ・スクロール操作のステート管理 ---
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var isWheelMode = false

    // ジェスチャー判定（タップ・ダブルタップ）をお任せするプロ
    private lateinit var gestureDetector: GestureDetector

    // --- スレッド制御用ハンドラー ---
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scrollHandler = Handler(Looper.getMainLooper())
    private val idleHandler = Handler(Looper.getMainLooper())

    private var scrollRunnable: Runnable? = null
    private var currentScrollInterval = 100L
    private var currentScrollFactor = 0f
    private var currentScrollIsDown = true
    private var isScrollingActive = false

    // アイドル状態（画面端へ収納）への移行
    private val idleRunnable = Runnable { enterIdleState() }

    // 長押し判定（スライムモード開始）のランナブル
    private val startSlimeRunnable = Runnable {
        if (!isDragging) {
            isWheelMode = true
            floatingButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            startSlime(initialTouchX, initialTouchY)
            startContinuousScrollLoop()
        }
    }

    // ピクセル変換プロパティ
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
    private val Float.dp: Float get() = this * resources.displayMetrics.density

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // ジェスチャー検知器の初期化
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // シングルタップ確定（ダブルタップじゃなかった場合のみ呼ばれる）
                floatingButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                scrollToTop()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                // ダブルタップ検知
                // API 30未満でも動くよう LONG_PRESS を使用
                floatingButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                scrollToBottom()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                // GestureDetectorの長押しは使わず、自前のHandlerで制御するため何もしない
            }
        })

        createSlimeIndicator()
        createFloatingButton()
        resetIdleTimer()
    }

    // ==========================================
    // スライムインジケーター（餅）
    // ==========================================
    private inner class SlimeIndicatorView(context: Context) : View(context) {
        private val fillPaint = Paint().apply { color = Color.argb(200, 245, 245, 245); style = Paint.Style.FILL; isAntiAlias = true }
        private val strokePaint = Paint().apply { color = Color.argb(50, 0, 0, 0); style = Paint.Style.STROKE; strokeWidth = 3f.dp; isAntiAlias = true }

        private val circle1 = Path(); private val circle2 = Path()
        private val bridge = Path(); private val combinedPath = Path()

        var startX = 0f; var startY = 0f; var endX = 0f; var endY = 0f

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (startX == endX && startY == endY) return

            val r1 = 12f.dp; val r2 = 28f.dp
            val angle = atan2((endY - startY).toDouble(), (endX - startX).toDouble())
            val angleOff = Math.PI / 2

            circle1.reset(); circle1.addCircle(startX, startY, r1, Path.Direction.CW)
            circle2.reset(); circle2.addCircle(endX, endY, r2, Path.Direction.CW)

            bridge.reset()
            bridge.moveTo((startX + r1 * cos(angle - angleOff)).toFloat(), (startY + r1 * sin(angle - angleOff)).toFloat())
            bridge.lineTo((startX + r1 * cos(angle + angleOff)).toFloat(), (startY + r1 * sin(angle + angleOff)).toFloat())
            bridge.lineTo((endX + r2 * cos(angle + angleOff)).toFloat(), (endY + r2 * sin(angle + angleOff)).toFloat())
            bridge.lineTo((endX + r2 * cos(angle - angleOff)).toFloat(), (endY + r2 * sin(angle - angleOff)).toFloat())
            bridge.close()

            combinedPath.reset()
            combinedPath.op(bridge, circle1, Path.Op.UNION)
            combinedPath.op(combinedPath, circle2, Path.Op.UNION)

            canvas.drawPath(combinedPath, fillPaint)
            canvas.drawPath(combinedPath, strokePaint)
        }
    }

    private fun createSlimeIndicator() {
        slimeView = SlimeIndicatorView(this).apply { visibility = View.GONE }
        slimeParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(slimeView, slimeParams)
    }

    private fun getRelativeButtonCenter(outPoint: FloatArray) {
        val btnLoc = IntArray(2)
        val slimeLoc = IntArray(2)
        floatingButton.getLocationOnScreen(btnLoc)
        slimeView.getLocationOnScreen(slimeLoc)
        outPoint[0] = (btnLoc[0] - slimeLoc[0]) + (floatingButton.width / 2f)
        outPoint[1] = (btnLoc[1] - slimeLoc[1]) + (floatingButton.height / 2f)
    }

    private fun getRelativeTouchPoint(rawX: Float, rawY: Float, outPoint: FloatArray) {
        val slimeLoc = IntArray(2)
        slimeView.getLocationOnScreen(slimeLoc)
        outPoint[0] = rawX - slimeLoc[0]
        outPoint[1] = rawY - slimeLoc[1]
    }

    private fun startSlime(rawX: Float, rawY: Float) {
        val center = FloatArray(2)
        val touch = FloatArray(2)
        getRelativeButtonCenter(center)
        getRelativeTouchPoint(rawX, rawY, touch)

        slimeView.startX = center[0]
        slimeView.startY = center[1]
        slimeView.endX = touch[0]
        slimeView.endY = touch[1]

        slimeView.visibility = View.VISIBLE
        slimeView.invalidate()
    }

    private fun updateSlime(rawX: Float, rawY: Float) {
        val center = FloatArray(2)
        val touch = FloatArray(2)
        getRelativeButtonCenter(center)
        getRelativeTouchPoint(rawX, rawY, touch)

        slimeView.startX = center[0]
        slimeView.startY = center[1]
        // 以前あった deltaX 計算式は削除（直接計算に変更）
        slimeView.endX = center[0] + ((touch[0] - center[0]) * 0.2f)
        slimeView.endY = touch[1]
        slimeView.invalidate()
    }

    // ==========================================
    // メインのフローティングアクションボタン(FAB)
    // ==========================================
    private fun createFloatingButton() {
        floatingButton = View(this).apply {
            background = createAssistiveTouchDrawable(180)
            isHapticFeedbackEnabled = true
            elevation = 16f.dp
        }
        val size = 56.dp
        floatingButtonParams = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 600 }

        // 引数名 v を _ に変更して警告を抑制
        floatingButton.setOnTouchListener { _, event ->
            // ジェスチャー検知器にイベントを渡す（これがタップ判定を行う）
            // ドラッグやホイールモード中でなければ、ジェスチャー判定を優先
            if (!isDragging && !isWheelMode) {
                gestureDetector.onTouchEvent(event)
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    cancelIdleState()
                    // downTime 変数は削除しました（GestureDetectorが内部で管理するため）

                    initialX = floatingButtonParams.x; initialY = floatingButtonParams.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    isDragging = false; isWheelMode = false; animateButtonAlpha(255)

                    // 長押し判定（スライムモード）開始
                    mainHandler.postDelayed(startSlimeRunnable, 400)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX; val dy = event.rawY - initialTouchY
                    if (!isWheelMode && (abs(dx) > 25 || abs(dy) > 25)) {
                        isDragging = true
                        mainHandler.removeCallbacks(startSlimeRunnable)
                    }

                    if (isWheelMode) {
                        calculateScrollParameters(dy)
                        updateSlime(event.rawX, event.rawY)
                    } else if (isDragging) {
                        floatingButtonParams.x = initialX + dx.toInt()
                        floatingButtonParams.y = initialY + dy.toInt()
                        updateViewSafely(floatingButton, floatingButtonParams)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(startSlimeRunnable)
                    stopContinuousScrollLoop()
                    slimeView.visibility = View.GONE
                    isWheelMode = false
                    animateButtonAlpha(180)

                    if (isDragging) snapToEdge()
                    resetIdleTimer()
                }
            }
            true
        }
        windowManager.addView(floatingButton, floatingButtonParams)
    }

    private fun scrollToTop() {
        val m = resources.displayMetrics
        val centerX = m.widthPixels / 2f
        val startY = m.heightPixels * 0.2f
        val endY = m.heightPixels * 0.9f

        val path = Path().apply { moveTo(centerX, startY); lineTo(centerX, endY) }
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 50L))
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 60, 50L))
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 120, 50L))
        dispatchGesture(gestureBuilder.build(), null, null)
    }

    private fun scrollToBottom() {
        val m = resources.displayMetrics
        val centerX = m.widthPixels / 2f
        val startY = m.heightPixels * 0.8f
        val endY = m.heightPixels * 0.1f

        val path = Path().apply { moveTo(centerX, startY); lineTo(centerX, endY) }
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 50L))
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 60, 50L))
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 120, 50L))
        dispatchGesture(gestureBuilder.build(), null, null)
    }

    private fun calculateScrollParameters(dy: Float) {
        val metrics = resources.displayMetrics
        val absDy = abs(dy)
        val deadZone = 40f
        if (absDy < deadZone) {
            if (isScrollingActive) floatingButton.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            isScrollingActive = false; return
        }
        isScrollingActive = true
        currentScrollIsDown = dy > 0

        val maxReach = metrics.heightPixels * 0.38f
        val progress = ((absDy - deadZone) / (maxReach - deadZone)).coerceAtMost(1.0f)
        val curve = progress.pow(2.2f)
        currentScrollInterval = (400 * (0.04f.pow(curve))).toLong().coerceAtLeast(16L)
        currentScrollFactor = 0.05f + (curve * 0.75f)
    }

    private fun startContinuousScrollLoop() {
        if (scrollRunnable != null) return
        scrollRunnable = object : Runnable {
            override fun run() {
                if (isScrollingActive) performScrollStep(currentScrollIsDown, currentScrollFactor)
                scrollHandler.postDelayed(this, currentScrollInterval)
            }
        }
        scrollHandler.post(scrollRunnable!!)
    }

    private fun performScrollStep(isDown: Boolean, factor: Float) {
        val m = resources.displayMetrics
        val startY = m.heightPixels * 0.5f
        val dist = m.heightPixels * factor * 0.5f
        val endY = if (isDown) startY - dist else startY + dist

        val path = Path().apply { moveTo(m.widthPixels / 2f, startY); lineTo(m.widthPixels / 2f, endY) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 35L)).build(), null, null)
    }

    private fun stopContinuousScrollLoop() {
        isScrollingActive = false
        scrollRunnable?.let { scrollHandler.removeCallbacks(it) }
        scrollRunnable = null
    }

    private fun createAssistiveTouchDrawable(alpha: Int): LayerDrawable {
        val base = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(alpha, 40, 40, 40))
            setStroke(1.dp, Color.argb(70, 255, 255, 255))
        }
        val inner = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb((alpha * 0.8).toInt(), 255, 255, 255))
        }
        return LayerDrawable(arrayOf(base, inner)).apply {
            val inset = 16.dp
            setLayerInset(1, inset, inset, inset, inset)
        }
    }

    private fun animateButtonAlpha(target: Int) {
        ValueAnimator.ofInt(180, target).apply {
            duration = 150
            addUpdateListener { floatingButton.background = createAssistiveTouchDrawable(it.animatedValue as Int) }
            start()
        }
    }

    private fun snapToEdge() {
        val sw = resources.displayMetrics.widthPixels
        val tx = if (floatingButtonParams.x < sw / 2) 0 else sw - floatingButton.width
        ValueAnimator.ofInt(floatingButtonParams.x, tx).apply {
            duration = 250
            addUpdateListener { floatingButtonParams.x = it.animatedValue as Int; updateViewSafely(floatingButton, floatingButtonParams) }
            start()
        }
    }

    private fun resetIdleTimer() { idleHandler.removeCallbacks(idleRunnable); idleHandler.postDelayed(idleRunnable, 3000L) }

    private fun enterIdleState() {
        if (isWheelMode || isDragging) return
        val sw = resources.displayMetrics.widthPixels
        val tx = if (floatingButtonParams.x < sw / 2) -floatingButton.width / 2 else sw - floatingButton.width / 2
        ValueAnimator.ofInt(floatingButtonParams.x, tx).apply {
            duration = 500
            addUpdateListener { floatingButtonParams.x = it.animatedValue as Int; updateViewSafely(floatingButton, floatingButtonParams) }
            start()
        }
    }

    private fun cancelIdleState() {
        idleHandler.removeCallbacks(idleRunnable)
        if (floatingButtonParams.x !in 0..500) snapToEdge()
    }

    private fun updateViewSafely(v: View, p: WindowManager.LayoutParams) { try { windowManager.updateViewLayout(v, p) } catch (_: Exception) {} }

    override fun onDestroy() {
        stopContinuousScrollLoop()
        if (::slimeView.isInitialized) try { windowManager.removeView(slimeView) } catch (_: Exception) {}
        if (::floatingButton.isInitialized) try { windowManager.removeView(floatingButton) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}