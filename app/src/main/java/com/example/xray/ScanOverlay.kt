package com.example.xray

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator

class ScanView(context: Context) : View(context) {

    private val d = context.resources.displayMetrics.density
    private val trail = 140f * d

    private val dimPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#33000000")
    }
    private val trailPaint = Paint().apply {
        style = Paint.Style.FILL
        shader = LinearGradient(
            0f, 0f, 0f, trail,
            Color.parseColor("#0000FF9C"),
            Color.parseColor("#AA00FF9C"),
            Shader.TileMode.CLAMP
        )
    }
    private val linePaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#00FF9C")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        textSize = 16f * d
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }

    var progress = 0f

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, dimPaint)
        val y = progress * h
        canvas.save()
        canvas.translate(0f, y - trail)
        canvas.drawRect(0f, 0f, w, trail, trailPaint)
        canvas.restore()
        canvas.drawRect(0f, y - 1.5f * d, w, y + 1.5f * d, linePaint)
        canvas.drawText("> SCANNING...", w / 2f, h * 0.5f, textPaint)
    }
}

class ScanOverlay(private val service: AccessibilityService) {

    private var view: ScanView? = null
    private var anim: ValueAnimator? = null

    fun show() {
        if (view != null) return
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val v = ScanView(service)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        try {
            wm.addView(v, lp)
            view = v
            val a = ValueAnimator.ofFloat(0f, 1f)
            a.duration = 1100
            a.repeatCount = ValueAnimator.INFINITE
            a.interpolator = LinearInterpolator()
            a.addUpdateListener {
                v.progress = it.animatedValue as Float
                v.invalidate()
            }
            a.start()
            anim = a
        } catch (e: Exception) {
            view = null
        }
    }

    fun dismiss() {
        anim?.cancel()
        anim = null
        val v = view ?: return
        view = null
        try {
            val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(v)
        } catch (e: Exception) {
        }
    }
}