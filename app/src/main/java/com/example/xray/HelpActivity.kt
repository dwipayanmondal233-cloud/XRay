package com.example.xray

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class HelpActivity : Activity() {

    private val green = Color.parseColor("#00FF9C")
    private val cyan = Color.parseColor("#00E5FF")
    private val bg = Color.parseColor("#05080A")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(bg)
        root.setPadding(dp(16), dp(24), dp(16), dp(24))

        fun heading(t: String) {
            val tv = TextView(this)
            tv.text = "> $t"
            tv.textSize = 16f
            tv.typeface = Typeface.MONOSPACE
            tv.setTextColor(green)
            tv.setPadding(0, dp(18), 0, dp(4))
            root.addView(tv)
        }

        fun body(t: String) {
            val tv = TextView(this)
            tv.text = t
            tv.textSize = 14f
            tv.typeface = Typeface.MONOSPACE
            tv.setTextColor(Color.WHITE)
            tv.setLineSpacing(0f, 1.15f)
            root.addView(tv)
        }

        val title = TextView(this)
        title.text = "HELP"
        title.textSize = 28f
        title.typeface = Typeface.MONOSPACE
        title.setTextColor(green)
        root.addView(title)

        heading("HOW TO USE")
        body(
            "1. Turn on the XRay service in Accessibility settings.\n" +
                "2. Pick a trigger: floating bubble, shake, or the Quick Settings tile.\n" +
                "3. Trigger it on any screen. Wait 1 to 3 seconds.\n" +
                "4. Tap a box to copy it.\n" +
                "5. Colors: cyan = link, purple = email, yellow = phone, orange = promo code.\n" +
                "6. REGION MODE lets you drag a box and scan only part of the screen."
        )

        heading("PRIVACY PROMISE")
        body(
            "- Each screenshot is processed in memory only. It is never saved.\n" +
                "- XRay has no internet permission and no analytics.\n" +
                "- History saves text only, on this device. You can turn it off or clear it."
        )

        heading("HONEST LIMITS")
        body(
            "- Needs Android 11 or newer.\n" +
                "- Apps that block screenshots (banking, some video and payment apps) cannot be read. This cannot be bypassed.\n" +
                "- One scan per second at most.\n" +
                "- OCR takes about 0.3 to 3 seconds, more on a slow phone.\n" +
                "- English and Hindi are supported. Bengali is NOT supported.\n" +
                "- Tiny, stylized, low-contrast, or rotated text may be missed or wrong.\n" +
                "- The overlay is a snapshot. If the screen changes, scan again."
        )

        heading("PROBLEMS AND FIXES")
        body(
            "App blocked to protect your device:\n" +
                "Play Store, profile icon, Play Protect, gear icon, turn off \"Scan apps with Play Protect\". Install XRay, then turn it back on.\n\n" +
                "Accessibility switch is greyed out (\"Restricted setting\"):\n" +
                "Settings, Apps, XRay, the 3-dot menu, \"Allow restricted settings\". Then turn the service on.\n\n" +
                "After installing an update:\n" +
                "Turn the XRay accessibility service off and on again.\n\n" +
                "\"App not installed\" when updating:\n" +
                "Debug builds change signature. Uninstall the old XRay first, then install.\n\n" +
                "Permission popups do not appear:\n" +
                "If any app draws over the screen, Android blocks popups. Stop other overlays first, or grant permissions in Settings.\n\n" +
                "The bubble or shake stops working:\n" +
                "Some phones kill background services. Set XRay Battery to Unrestricted.\n\n" +
                "\"This app blocks screenshots\":\n" +
                "That app uses a secure window. Nothing can read it."
        )

        val infoBtn = Button(this)
        infoBtn.text = "OPEN XRAY APP INFO (battery, restricted settings)"
        styled(infoBtn, cyan)
        val lp1 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp1.topMargin = dp(20)
        infoBtn.layoutParams = lp1
        infoBtn.setOnClickListener {
            val i = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
            startActivity(i)
        }
        root.addView(infoBtn)

        val backBtn = Button(this)
        backBtn.text = "BACK"
        styled(backBtn, green)
        val lp2 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp2.topMargin = dp(8)
        backBtn.layoutParams = lp2
        backBtn.setOnClickListener { finish() }
        root.addView(backBtn)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(bg)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun styled(b: Button, color: Int) {
        b.typeface = Typeface.MONOSPACE
        b.setTextColor(color)
        b.isAllCaps = false
        val shape = GradientDrawable()
        shape.setColor(Color.parseColor("#0B1216"))
        shape.setStroke(dp(1), color)
        shape.cornerRadius = dp(6).toFloat()
        b.background = shape
        b.gravity = Gravity.CENTER
    }
}