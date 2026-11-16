package com.example.xray

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var serviceText: TextView
    private lateinit var resultText: TextView
    private lateinit var image: ImageView
    private lateinit var scriptBtn: Button
    private lateinit var bubbleBtn: Button
    private lateinit var listBox: LinearLayout

    private val green = Color.parseColor("#00FF9C")
    private val cyan = Color.parseColor("#00E5FF")
    private val bg = Color.parseColor("#05080A")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun prefs() = getSharedPreferences("xray", Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }

        val title = TextView(this).apply {
            text = "XRAY"
            textSize = 32f
            typeface = Typeface.MONOSPACE
            setTextColor(green)
        }
        root.addView(title)

        serviceText = TextView(this).apply {
            textSize = 16f
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(serviceText)

        root.addView(makeButton("OPEN ACCESSIBILITY SETTINGS", cyan) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        bubbleBtn = makeButton("", green) {
            val now = prefs().getBoolean("bubble", false)
            prefs().edit().putBoolean("bubble", !now).apply()
            val svc = XRayService.instance
            if (svc == null) {
                if (!now) {
                    Toast.makeText(
                        this,
                        "Bubble will appear once the XRay service is ON.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                svc.applyBubble()
            }
            render()
        }
        root.addView(bubbleBtn)

        scriptBtn = makeButton("", cyan) {
            val now = prefs().getBoolean("deva", false)
            prefs().edit().putBoolean("deva", !now).apply()
            render()
        }
        root.addView(scriptBtn)

        root.addView(makeButton("CAPTURE NOW (THIS SCREEN)", green) {
            val svc = XRayService.instance
            if (svc == null) {
                Toast.makeText(this, "Turn on XRay in Accessibility first.", Toast.LENGTH_LONG).show()
            } else {
                svc.capture()
            }
        })

        root.addView(makeButton("CAPTURE IN 5s (GO TO ANOTHER APP)", green) {
            val svc = XRayService.instance
            if (svc == null) {
                Toast.makeText(this, "Turn on XRay in Accessibility first.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Capturing in 5 seconds. Open any app now.", Toast.LENGTH_LONG).show()
                svc.captureDelayed(5000)
                moveTaskToBack(true)
            }
        })

        root.addView(makeButton("X-RAY OVERLAY IN 3s", green) {
            val svc = XRayService.instance
            if (svc == null) {
                Toast.makeText(this, "Turn on XRay in Accessibility first.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "X-ray in 3 seconds. Open any app now.", Toast.LENGTH_LONG).show()
                svc.xray(3000)
                moveTaskToBack(true)
            }
        })

        root.addView(makeButton("COPY ALL TEXT", green) {
            val r = XRayService.lastResult
            if (r == null || r.blocks.isEmpty()) {
                Toast.makeText(this, "No text to copy.", Toast.LENGTH_SHORT).show()
            } else {
                copy(r.allText())
            }
        })

        root.addView(makeButton("CLEAR", cyan) {
            XRayService.lastBitmap = null
            XRayService.lastResult = null
            XRayService.lastStatus = "Cleared."
            render()
        })

        resultText = TextView(this).apply {
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setTextColor(cyan)
            setPadding(0, dp(12), 0, dp(8))
        }
        root.addView(resultText)

        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(listBox)

        image = ImageView(this).apply {
            adjustViewBounds = true
            maxHeight = dp(320)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.parseColor("#0B1216"))
        }
        val imgLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        imgLp.topMargin = dp(12)
        root.addView(image, imgLp)

        val privacy = TextView(this).apply {
            text = "> Privacy: screenshots stay in memory only. Nothing is saved or sent anywhere."
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#6B8A80"))
            setPadding(0, dp(16), 0, 0)
        }
        root.addView(privacy)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(bg)
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        XRayService.listener = { render() }
        render()
    }

    override fun onPause() {
        super.onPause()
        XRayService.listener = null
    }

    private fun copy(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("XRay", text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun render() {
        if (XRayService.instance != null) {
            serviceText.text = "SERVICE: ON"
            serviceText.setTextColor(green)
        } else {
            serviceText.text = "SERVICE: OFF"
            serviceText.setTextColor(Color.parseColor("#FF4D4D"))
        }

        if (prefs().getBoolean("bubble", false)) {
            bubbleBtn.text = "FLOATING BUBBLE: ON (tap to turn off)"
        } else {
            bubbleBtn.text = "FLOATING BUBBLE: OFF (tap to turn on)"
        }

        if (prefs().getBoolean("deva", false)) {
            scriptBtn.text = "OCR LANGUAGE: HINDI + ENGLISH (tap to switch)"
        } else {
            scriptBtn.text = "OCR LANGUAGE: ENGLISH (tap to switch)"
        }

        resultText.text = "> " + XRayService.lastStatus

        listBox.removeAllViews()
        val r = XRayService.lastResult
        if (r != null) {
            var blockNo = 1
            for (b in r.blocks) {
                val head = TextView(this)
                head.text = "[BLOCK $blockNo]"
                head.textSize = 12f
                head.typeface = Typeface.MONOSPACE
                head.setTextColor(green)
                head.setPadding(0, dp(10), 0, dp(2))
                listBox.addView(head)
                for (l in b.lines) {
                    val tv = TextView(this)
                    tv.text = l.text
                    tv.textSize = 15f
                    tv.typeface = Typeface.MONOSPACE
                    tv.setTextColor(Color.WHITE)
                    tv.setPadding(dp(8), dp(8), dp(8), dp(8))
                    val shape = GradientDrawable()
                    shape.setColor(Color.parseColor("#0B1216"))
                    shape.setStroke(dp(1), Color.parseColor("#1E3A34"))
                    shape.cornerRadius = dp(4).toFloat()
                    tv.background = shape
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.topMargin = dp(4)
                    tv.layoutParams = lp
                    val line = l.text
                    tv.setOnClickListener { copy(line) }
                    listBox.addView(tv)
                }
                blockNo++
            }
        }

        val bmp = XRayService.lastBitmap
        image.setImageBitmap(bmp)
        image.visibility = if (bmp == null) View.GONE else View.VISIBLE
    }

    private fun makeButton(label: String, color: Int, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.typeface = Typeface.MONOSPACE
        b.setTextColor(color)
        b.isAllCaps = false
        val shape = GradientDrawable()
        shape.setColor(Color.parseColor("#0B1216"))
        shape.setStroke(dp(1), color)
        shape.cornerRadius = dp(6).toFloat()
        b.background = shape
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(8)
        b.layoutParams = lp
        b.gravity = Gravity.CENTER
        b.setOnClickListener { onClick() }
        return b
    }
}