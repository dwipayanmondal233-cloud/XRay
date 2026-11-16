package com.example.xray

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
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

fun smartColor(t: SmartType): Int {
    return when (t) {
        SmartType.URL -> Color.parseColor("#00E5FF")
        SmartType.EMAIL -> Color.parseColor("#C77DFF")
        SmartType.PHONE -> Color.parseColor("#FFD60A")
        SmartType.CODE -> Color.parseColor("#FF8A00")
    }
}

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
    private val smart: List<SmartItem>,
    private val imgW: Int,
    private val imgH: Int,
    private val onCopy: (String) -> Unit,
    private val onSmart: (SmartItem) -> Unit,
    private val onOutside: () -> Unit
) : View(context) {

    private class Box(val text: String, val rect: Rect)

    private var boxes: List<Box> = ArrayList()
    private var flashRect: Rect? = null
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
    private val smartFill = Paint().apply {
        style = Paint.Style.FILL
    }
    private val smartStroke = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
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
        flashRect = null
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
        if (r.width() > 0 && r.height() > 0) out.add(Box(text, Rect(r)))
    }

    private fun scaled(r: Rect, pad: Float): RectF {
        val sx = width.toFloat() / imgW
        val sy = height.toFloat() / imgH
        return RectF(
            r.left * sx - pad,
            r.top * sy - pad,
            r.right * sx + pad,
            r.bottom * sy + pad
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#44000000"))
        for (b in boxes) {
            val r = scaled(b.rect, 0f)
            canvas.drawRect(r, fillPaint)
            canvas.drawRect(r, strokePaint)
        }
        for (s in smart) {
            val c = smartColor(s.type)
            smartFill.color = (0x55 shl 24) or (c and 0xFFFFFF)
            smartStroke.color = c
            val r = scaled(s.box, 2f * density)
            canvas.drawRect(r, smartFill)
            canvas.drawRect(r, smartStroke)
        }
        val fr = flashRect
        if (fr != null && flashAlpha > 0f) {
            flashPaint.alpha = (flashAlpha * 190).toInt()
            canvas.drawRect(scaled(fr, 0f), flashPaint)
        }
    }

    private fun flash(r: Rect) {
        flashRect = r
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

            var bestSmart: SmartItem? = null
            var bestSmartArea = Float.MAX_VALUE
            for (s in smart) {
                val r = scaled(s.box, 4f * density)
                if (r.contains(x, y)) {
                    val area = r.width() * r.height()
                    if (area < bestSmartArea) {
                        bestSmartArea = area
                        bestSmart = s
                    }
                }
            }
            if (bestSmart != null) {
                flash(bestSmart.box)
                onSmart(bestSmart)
                return true
            }

            var best: Box? = null
            var bestArea = Float.MAX_VALUE
            for (b in boxes) {
                val r = scaled(b.rect, 4f * density)
                if (r.contains(x, y)) {
                    val area = r.width() * r.height()
                    if (area < bestArea) {
                        bestArea = area
                        best = b
                    }
                }
            }
            if (best != null) {
                flash(best.rect)
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
    private var panel: View? = null
    var onDismissed: (() -> Unit)? = null

    private val green = Color.parseColor("#00FF9C")
    private val cyan = Color.parseColor("#00E5FF")
    private val red = Color.parseColor("#FF4D4D")

    private fun dp(v: Int): Int = (v * service.resources.displayMetrics.density).toInt()

    fun isShowing(): Boolean = root != null

    fun dismiss() {
        val r = root ?: return
        root = null
        panel = null
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

    private fun closePanel() {
        val p = panel ?: return
        panel = null
        root?.removeView(p)
    }

    private fun cleanPhone(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            if (ch.isDigit()) {
                sb.append(ch)
            } else if (ch == '+' && sb.isEmpty()) {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun launch(item: SmartItem) {
        val intent: Intent = when (item.type) {
            SmartType.URL -> {
                val t = item.text
                val u = if (t.startsWith("http://", true) || t.startsWith("https://", true)) {
                    t
                } else {
                    "https://$t"
                }
                Intent(Intent.ACTION_VIEW, Uri.parse(u))
            }
            SmartType.EMAIL -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + item.text))
            SmartType.PHONE -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + cleanPhone(item.text)))
            SmartType.CODE -> return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            service.startActivity(intent)
            dismiss()
        } catch (e: Exception) {
            copy(item.text)
            Toast.makeText(service, "Could not open it. Copied instead.", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleSmart(item: SmartItem) {
        if (item.type == SmartType.CODE) {
            copy(item.text)
        } else {
            showPanel(item)
        }
    }

    private fun showPanel(item: SmartItem) {
        val host = root ?: return
        closePanel()
        val color = smartColor(item.type)

        val p = LinearLayout(service)
        p.orientation = LinearLayout.VERTICAL
        p.setPadding(dp(10), dp(10), dp(10), dp(10))
        val shape = GradientDrawable()
        shape.setColor(Color.parseColor("#F005080A"))
        shape.setStroke(dp(2), color)
        shape.cornerRadius = dp(8).toFloat()
        p.background = shape
        p.isClickable = true

        val title = TextView(service)
        title.text = item.type.name + ": " + item.text
        title.typeface = Typeface.MONOSPACE
        title.textSize = 13f
        title.setTextColor(color)
        title.maxLines = 2
        title.ellipsize = TextUtils.TruncateAt.END
        p.addView(title)

        val row = LinearLayout(service)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(makeButton("COPY", green) {
            copy(item.text)
            closePanel()
        })
        val actionLabel = when (item.type) {
            SmartType.URL -> "OPEN"
            SmartType.EMAIL -> "EMAIL"
            SmartType.PHONE -> "DIAL"
            SmartType.CODE -> ""
        }
        if (actionLabel.isNotEmpty()) {
            row.addView(makeButton(actionLabel, color) { launch(item) })
        }
        row.addView(makeButton("CANCEL", red) { closePanel() })
        val rowLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        rowLp.topMargin = dp(8)
        p.addView(row, rowLp)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        lp.gravity = Gravity.BOTTOM
        lp.bottomMargin = systemBarHeight("navigation_bar_height") + dp(80)
        lp.leftMargin = dp(10)
        lp.rightMargin = dp(10)
        host.addView(p, lp)
        panel = p
    }

    fun show(result: OcrResult, imgW: Int, imgH: Int) {
        dismiss()
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val smart = SmartDetector.detect(result)

        val rootView = OverlayRoot(service)
        rootView.isFocusableInTouchMode = true
        rootView.onBack = { dismiss() }

        val boxes = BoxesView(
            service, result, smart, imgW, imgH,
            onCopy = { text -> copy(text) },
            onSmart = { item -> handleSmart(item) },
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
            val sb = SpannableStringBuilder()
            sb.append("> " + boxes.count() + " " + modeNames[m].lowercase() + "s. Tap a box to copy.")
            if (smart.isNotEmpty()) {
                sb.append("\n")
                for (t in SmartType.values()) {
                    var n = 0
                    for (s in smart) {
                        if (s.type == t) n++
                    }
                    if (n > 0) {
                        val start = sb.length
                        sb.append(t.name + " " + n + "  ")
                        sb.setSpan(
                            ForegroundColorSpan(smartColor(t)),
                            start,
                            sb.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            }
            label.text = sb
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
        bar.addView(makeButton("CLOSE", red) { dismiss() })

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