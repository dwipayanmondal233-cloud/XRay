package com.example.xray

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class OverlayRoot(context: Context) : FrameLayout(context) {
    var onBack: (() -> Unit)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onBack?.invoke()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

class BoxesView(
    context: Context,
    private val result: OcrResult,
    private val imgW: Int,
    private val imgH: Int,
    private val onCopy: (String) -> Unit,
    private val onOutside: () -> Unit
) : View(context) {

    private class Box(val text: String, val rect: RectF)

    private var boxes: List<Box> = ArrayList()
    private var flashBox: Box? = null
    private var flashAlpha = 0f
    private val density = context.resources.displayMetrics.density

    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#3300FF9C")
    }
    private val strokePaint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#00FF9C")
        strokeWidth = 1.5f * density
        isAntiAlias = true
    }
    private val flashPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    init {
        setMode(1)
    }

    fun setMode(m: Int) {
        boxes = build(m)
        flashBox = null
        invalidate()
    }

    fun count(): Int = boxes.size

    private fun build(m: Int): List<Box> {
        val out = ArrayList<Box>()
        for (b in result.blocks) {
            if (m == 2) {
                add(out, b.text, b.box)
            } else {
                for (l in b.lines) {
                    if (m == 1) {
                        add(out, l.text, l.box)
                    } else {
                        for (e in l.elements) add(out, e.text, e.box)
                    }
                }
            }
        }
        return out
    }

    private fun add(out: ArrayList<Box>, text: String, r: Rect) {
        if (r.width() > 0 && r.height() > 0) out.add(Box(text, RectF(r)))
    }

    private fun scaled(b: Box, pad: Float): RectF {
        val sx = width.toFloat() / imgW
        val sy = height.toFloat() / imgH
        return RectF(
            b.rect.left * sx - pad,
            b.rect.top * sy - pad,
            b.rect.right * sx + pad,
            b.rect.bottom * sy + pad
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#44000000"))
        for (b in boxes) {
            val r = scaled(b, 0f)
            canvas.drawRect(r, fillPaint)
            canvas.drawRect(r, strokePaint)
            if (b === flashBox && flashAlpha > 0f) {
                flashPaint.alpha = (flashAlpha * 190).toInt()
                canvas.drawRect(r, flashPaint)
            }
        }
    }

    private fun flash(b: Box) {
        flashBox = b
        val anim = ValueAnimator.ofFloat(1f, 0f)
        anim.duration = 400
        anim.addUpdateListener {
            flashAlpha = it.animatedValue as Float
            invalidate()
        }
        anim.start()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) return true
        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y
            var best: Box? = null
            var bestArea = Float.MAX_VALUE
            for (b in boxes) {
                val r = scaled(b, 4f * density)
                if (r.contains(x, y)) {
                    val area = r.width() * r.height()
                    if (area < bestArea) {
                        bestArea = area
                        best = b
                    }
                }
            }
            if (best != null) {
                flash(best)
                onCopy(best.text)
            } else {
                onOutside()
            }
            return true
        }
        return true
    }
}

class OverlayController(private val service: AccessibilityService) {

    private var root: OverlayRoot? = null
    var onDismissed: (() -> Unit)? = null

    private val green = Color.parseColor("#00FF9C")
    private val cyan = Color.parseColor("#00E5FF")

    private fun dp(v: Int): Int = (v * service.resources.displayMetrics.density).toInt()

    fun isShowing(): Boolean = root != null

    fun dismiss() {
        val r = root ?: return
        root = null
        try {
            val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
           wm.removeView(r)
            onDismissed?.invoke()
        } catch (e: Exception) {
        }
    }

    private fun copy(text: String) {
        val cm = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("XRay", text))
        Toast.makeText(service, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun systemBarHeight(name: String): Int {
        val id = service.resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) service.resources.getDimensionPixelSize(id) else 0
    }

    private fun styleButton(b: Button, color: Int, selected: Boolean) {
        val shape = GradientDrawable()
        shape.setColor(if (selected) color else Color.parseColor("#0B1216"))
        shape.setStroke(dp(1), color)
        shape.cornerRadius = dp(6).toFloat()
        b.background = shape
        b.setTextColor(if (selected) Color.parseColor("#05080A") else color)
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
        styleButton(b, color, false)
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lp.leftMargin = dp(3)
        lp.rightMargin = dp(3)
        b.layoutParams = lp
        b.setOnClickListener { onClick() }
        return b
    }

    fun show(result: OcrResult, imgW: Int, imgH: Int) {
        dismiss()
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val rootView = OverlayRoot(service)
        rootView.isFocusableInTouchMode = true
        rootView.onBack = { dismiss() }

        val boxes = BoxesView(
            service, result, imgW, imgH,
            onCopy = { text -> copy(text) },
            onOutside = { dismiss() }
        )
        rootView.addView(
            boxes,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val label = TextView(service)
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
        rootView.addView(label, labelLp)

        val modeNames = arrayOf("WORD", "LINE", "BLOCK")
        val modeButtons = ArrayList<Button>()

        fun applyMode(m: Int) {
            boxes.setMode(m)
            for (i in modeButtons.indices) {
                styleButton(modeButtons[i], cyan, i == m)
            }
            label.text = "> " + boxes.count() + " " + modeNames[m].lowercase() +
                "s. Tap a box to copy. Tap outside to close."
        }

        val bar = LinearLayout(service)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(Color.parseColor("#DD05080A"))
        bar.setPadding(dp(4), dp(6), dp(4), dp(6))

        for (i in 0..2) {
            val b = makeButton(modeNames[i], cyan) { applyMode(i) }
            modeButtons.add(b)
            bar.addView(b)
        }
        bar.addView(makeButton("COPY ALL", green) {
            val t = result.allText()
            if (t.isEmpty()) {
                Toast.makeText(service, "No text to copy.", Toast.LENGTH_SHORT).show()
            } else {
                copy(t)
            }
        })
        bar.addView(makeButton("CLOSE", Color.parseColor("#FF4D4D")) { dismiss() })

        val barLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        barLp.gravity = Gravity.BOTTOM
        barLp.bottomMargin = systemBarHeight("navigation_bar_height") + dp(8)
        barLp.leftMargin = dp(6)
        barLp.rightMargin = dp(6)
        rootView.addView(bar, barLp)

        applyMode(1)

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
                "Could not show overlay: " + (e.message ?: "unknown error"),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}