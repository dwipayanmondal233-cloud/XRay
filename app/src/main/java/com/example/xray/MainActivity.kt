package com.example.xray

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#05080A"))
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "XRAY"
            textSize = 40f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#00FF9C"))
            gravity = Gravity.CENTER
        }

        val sub = TextView(this).apply {
            text = "> XRay ready_"
            textSize = 18f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
        }

        root.addView(title)
        root.addView(sub)
        setContentView(root)
    }
}