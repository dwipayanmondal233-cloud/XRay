package com.example.xray

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class RegionView(context: Context, private val bmp: Bitmap) : View(context) {

    private var sel: RectF? = null
    private var startX = 0f
    private var startY = 0f
    private val density = context.resources.displayMetrics.density

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dimPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#99000000")
    }
    private val strokePaint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#00E5FF")
        strokeWidth = 2.5f * density
        isAntiAlias = true
    }

    fun hasSelection(): Boolean = sel != null

    fun selectionInBitmap(): Rect? {
        val s = sel ?: return null
        if (width == 0 || height == 0) return null
        val sx = bmp.width.toFloat() / width
        val sy = bmp.height.toFloat() / height
        val l = (s.left * sx).toInt().coerceIn(0, bmp.width - 1)
        val t = (s.top * sy).toInt().coerceIn(0, bmp.height - 1)
        val r = (s.right * sx).toInt().coerceIn(l + 1, bmp.width)
        val b = (s.bottom * sy).toInt().coerceIn(t + 1, bmp.height)
        return Rect(l, t, r, b)
    }

    override fun onDraw(canvas: Canvas) {
        val full = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(bmp, null, full, bitmapPaint)
        canvas.drawRect(full, dimPaint)
        val s = sel
        if (s != null) {
            canvas.save()
            canvas.clipRect(s)
            canvas.drawBitmap(bmp, null, full, bitmapPaint)
            canvas.restore()
            canvas.drawRect(s, strokePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x.coerceIn(0f, width.toFloat())
        val y = event.y.coerceIn(0f, height.toFloat())
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = x
                startY = y
                sel = RectF(x, y, x, y)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                sel = RectF(
                    minOf(startX, x), minOf(startY, y),
                    maxOf(startX, x), maxOf(startY, y)
                )
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val s = RectF(
                    minOf(startX, x), minOf(startY, y),
                    maxOf(startX, x), maxOf(startY, y)
                )
                val minSize = 32f * density
                sel = if (s.width() >= minSize && s.height() >= minSize) s else null
                invalidate()
            }
        }
        return true
    }
}

class RegionSelector(private val service: AccessibilityService) {

    private var root: OverlayRoot? = null

    private val green = Color.parseColor("#00FF9C")
    private val cyan = Color.parseColor("#00E5FF")
    private val red = Color.parseColor("#FF4D4D")

    private fun dp(v: Int): Int = (v * service.resources.displayMetrics.density).toInt()

    fun isShowing(): Boolean = root != null

    fun dismiss() {
        val r = root ?: return
        root = null
        try {
            val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(r)
        } catch (e: Exception) {
        }
    }

    private fun systemBarHeight(name: String): Int {
        val id = service.resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) service.resources.getDimensionPixelSize(id) else 0
    }

    private fun makeButton(label: String, color: Int, onClick: () -> Unit): Button {
        val b = Button(service)
        b.text = label
        b.typeface = Typeface.MONOSPACE
        b.textSize = 11f
        b.isAllCaps = false
        b.minHeight = 0
        b.minimumHeight = 0
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(dp(2), dp(10), dp(2), dp(10))
        val shape = GradientDrawable()
        shape.setColor(Color.parseColor("#0B1216"))
        shape.setStroke(dp(1), color)
        shape.cornerRadius = dp(6).toFloat()
        b.background = shape
        b.setTextColor(color)
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lp.leftMargin = dp(3)
        lp.rightMargin = dp(3)
        b.layoutParams = lp
        b.setOnClickListener { onClick() }
        return b
    }

    fun show(
        bmp: Bitmap,
        onRegion: (Rect) -> Unit,
        onFull: () -> Unit,
        onCancel: () -> Unit
    ) {
        dismiss()
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val rootView = OverlayRoot(service)
        rootView.isFocusableInTouchMode = true
        rootView.onBack = {
            dismiss()
            onCancel()
        }

        val view = RegionView(service, bmp)
        rootView.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val label = TextView(service)
        label.text = "> Drag a box around the text you want. Then tap SCAN AREA."
        label.typeface = Typeface.MONOSPACE
        label.textSize = 12f
        label.setTextColor(cyan)
        label.setBackgroundColor(Color.parseColor("#AA05080A"))
        label.setPadding(dp(8), dp(4), dp(8), dp(4))
        val labelLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        labelLp.gravity = Gravity.TOP or Gravity.START
        labelLp.topMargin = systemBarHeight("status_bar_height") + dp(6)
        labelLp.leftMargin = dp(8)
        labelLp.rightMargin = dp(8)
        rootView.addView(label, labelLp)

        val bar = LinearLayout(service)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(Color.parseColor("#DD05080A"))
        bar.setPadding(dp(4), dp(6), dp(4), dp(6))

        bar.addView(makeButton("SCAN AREA", green) {
            val r = view.selectionInBitmap()
            if (r == null) {
                Toast.makeText(service, "Drag a box first.", Toast.LENGTH_SHORT).show()
            } else {
                dismiss()
                onRegion(r)
            }
        })
        bar.addView(makeButton("FULL SCREEN", cyan) {
            dismiss()
            onFull()
        })
        bar.addView(makeButton("CANCEL", red) {
            dismiss()
            onCancel()
        })

        val barLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        barLp.gravity = Gravity.BOTTOM
        barLp.bottomMargin = systemBarHeight("navigation_bar_height") + dp(8)
        barLp.leftMargin = dp(6)
        barLp.rightMargin = dp(6)
        rootView.addView(bar, barLp)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS

        try {
            wm.addView(rootView, lp)
            root = rootView
            rootView.requestFocus()
        } catch (e: Exception) {
            Toast.makeText(
                service,
                "Could not show selector: " + (e.message ?: "unknown error"),
                Toast.LENGTH_LONG
            ).show()
            onCancel()
        }
    }
}