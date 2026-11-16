package com.example.xray

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager

class BubbleView(context: Context) : View(context) {

    private val d = context.resources.displayMetrics.density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E605080A")
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
        color = Color.parseColor("#00FF9C")
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        textSize = 20f * d
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - 2f * d
        canvas.drawCircle(cx, cy, r, fill)
        canvas.drawCircle(cx, cy, r, ring)
        val y = cy - (txt.descent() + txt.ascent()) / 2f
        canvas.drawText("X", cx, y, txt)
    }
}

class BubbleController(
    private val service: AccessibilityService,
    private val onTap: () -> Unit
) {

    private var view: BubbleView? = null
    private var params: WindowManager.LayoutParams? = null

    private fun dp(v: Int): Int = (v * service.resources.displayMetrics.density).toInt()

    private fun wm(): WindowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun prefs() = service.getSharedPreferences("xray", Context.MODE_PRIVATE)

    fun show() {
        val existing = view
        if (existing != null) {
            existing.visibility = View.VISIBLE
            return
        }

        val dm = service.resources.displayMetrics
        val size = dp(52)
        val sw = dm.widthPixels
        val sh = dm.heightPixels

        var x = prefs().getInt("bubble_x", sw - size)
        var y = prefs().getInt("bubble_y", sh / 3)
        x = if (x + size / 2 < sw / 2) 0 else sw - size
        y = y.coerceIn(0, sh - size)

        val lp = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = x
        lp.y = y

        val v = BubbleView(service)
        attachTouch(v, lp, size)

        try {
            wm().addView(v, lp)
            view = v
            params = lp
        } catch (e: Exception) {
            view = null
            params = null
        }
    }

    private fun attachTouch(v: BubbleView, lp: WindowManager.LayoutParams, size: Int) {
        val slop = ViewConfiguration.get(service).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        v.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!moved && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                        moved = true
                    }
                    if (moved) {
                        val dm = service.resources.displayMetrics
                        lp.x = (startX + dx).toInt().coerceIn(0, dm.widthPixels - size)
                        lp.y = (startY + dy).toInt().coerceIn(0, dm.heightPixels - size)
                        try {
                            wm().updateViewLayout(v, lp)
                        } catch (e: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        dock(v, lp, size)
                    } else {
                        onTap()
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun dock(v: BubbleView, lp: WindowManager.LayoutParams, size: Int) {
        val sw = service.resources.displayMetrics.widthPixels
        val target = if (lp.x + size / 2 < sw / 2) 0 else sw - size
        prefs().edit().putInt("bubble_x", target).putInt("bubble_y", lp.y).apply()
        val anim = ValueAnimator.ofInt(lp.x, target)
        anim.duration = 180
        anim.addUpdateListener {
            lp.x = it.animatedValue as Int
            try {
                wm().updateViewLayout(v, lp)
            } catch (e: Exception) {
            }
        }
        anim.start()
    }

    fun hide() {
        view?.visibility = View.INVISIBLE
    }

    fun remove() {
        val v = view ?: return
        view = null
        params = null
        try {
            wm().removeView(v)
        } catch (e: Exception) {
        }
    }
}