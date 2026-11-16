package com.example.xray

import android.app.Activity
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

    private val green = Color.parseColor("#00FF9C")
    private val cyan = Color.parseColor("#00E5FF")
    private val bg = Color.parseColor("#05080A")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

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

        root.addView(makeButton("CLEAR IMAGE", cyan) {
            XRayService.lastBitmap = null
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

        image = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.parseColor("#0B1216"))
        }
        root.addView(
            image,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

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

    private fun render() {
        if (XRayService.instance != null) {
            serviceText.text = "SERVICE: ON"
            serviceText.setTextColor(green)
        } else {
            serviceText.text = "SERVICE: OFF"
            serviceText.setTextColor(Color.parseColor("#FF4D4D"))
        }
        resultText.text = "> " + XRayService.lastStatus
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