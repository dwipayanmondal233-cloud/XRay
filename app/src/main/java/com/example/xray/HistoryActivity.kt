package com.example.xray

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : Activity() {

    private lateinit var listBox: LinearLayout
    private lateinit var toggleBtn: Button

    private val green = Color.parseColor("#00FF9C")
    private val cyan = Color.parseColor("#00E5FF")
    private val red = Color.parseColor("#FF4D4D")
    private val bg = Color.parseColor("#05080A")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(bg)
        root.setPadding(dp(16), dp(24), dp(16), dp(24))

        val title = TextView(this)
        title.text = "HISTORY"
        title.textSize = 28f
        title.typeface = Typeface.MONOSPACE
        title.setTextColor(green)
        root.addView(title)

        val note = TextView(this)
        note.text = "> Last 20 extractions. Text only, saved on this device. Screenshots are never saved."
        note.textSize = 12f
        note.typeface = Typeface.MONOSPACE
        note.setTextColor(Color.parseColor("#6B8A80"))
        note.setPadding(0, dp(4), 0, dp(8))
        root.addView(note)

        toggleBtn = bigButton("", green) {
            HistoryStore.setEnabled(this, !HistoryStore.enabled(this))
            render()
        }
        root.addView(toggleBtn)

        root.addView(bigButton("CLEAR ALL", red) {
            AlertDialog.Builder(this)
                .setTitle("Clear all history?")
                .setPositiveButton("CLEAR") { _, _ ->
                    HistoryStore.clear(this)
                    render()
                }
                .setNegativeButton("CANCEL", null)
                .show()
        })

        root.addView(bigButton("BACK", cyan) { finish() })

        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        root.addView(listBox)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(bg)
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        toggleBtn.text = if (HistoryStore.enabled(this)) {
            "SAVE HISTORY: ON (tap to turn off)"
        } else {
            "SAVE HISTORY: OFF (tap to turn on)"
        }

        listBox.removeAllViews()
        val items = HistoryStore.load(this)
        if (items.isEmpty()) {
            val empty = TextView(this)
            empty.text = "> No history yet."
            empty.textSize = 14f
            empty.typeface = Typeface.MONOSPACE
            empty.setTextColor(cyan)
            empty.setPadding(0, dp(16), 0, 0)
            listBox.addView(empty)
            return
        }
        for (e in items) addCard(e)
    }

    private fun addCard(e: HistoryStore.Entry) {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(10), dp(10), dp(10), dp(10))
        val shape = GradientDrawable()
        shape.setColor(Color.parseColor("#0B1216"))
        shape.setStroke(dp(1), Color.parseColor("#1E3A34"))
        shape.cornerRadius = dp(6).toFloat()
        card.background = shape
        val cardLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        cardLp.topMargin = dp(10)
        card.layoutParams = cardLp

        val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
        val head = TextView(this)
        head.text = fmt.format(Date(e.time)) + "  |  " + e.script + "  |  " + e.text.length + " chars"
        head.textSize = 12f
        head.typeface = Typeface.MONOSPACE
        head.setTextColor(green)
        card.addView(head)

        val body = TextView(this)
        body.textSize = 14f
        body.typeface = Typeface.MONOSPACE
        body.setTextColor(Color.WHITE)
        body.setPadding(0, dp(6), 0, dp(6))
        var expanded = false
        val preview = if (e.text.length > 160) e.text.substring(0, 160) + "..." else e.text
        body.text = preview
        body.setOnClickListener {
            expanded = !expanded
            body.text = if (expanded) e.text else preview
        }
        card.addView(body)

        val hint = TextView(this)
        hint.text = "(tap the text to expand or collapse)"
        hint.textSize = 10f
        hint.typeface = Typeface.MONOSPACE
        hint.setTextColor(Color.parseColor("#6B8A80"))
        card.addView(hint)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, dp(8), 0, 0)
        row.addView(smallButton("COPY", green) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("XRay", e.text))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        })
        row.addView(smallButton("SHARE", cyan) { Sharer.send(this, e.text) })
        row.addView(smallButton("DELETE", red) {
            HistoryStore.delete(this, e.time)
            render()
        })
        card.addView(row)

        listBox.addView(card)
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

    private fun bigButton(label: String, color: Int, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        styled(b, color)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(8)
        b.layoutParams = lp
        b.setOnClickListener { onClick() }
        return b
    }

    private fun smallButton(label: String, color: Int, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.textSize = 12f
        b.minHeight = 0
        b.minimumHeight = 0
        b.setPadding(dp(2), dp(10), dp(2), dp(10))
        styled(b, color)
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lp.leftMargin = dp(3)
        lp.rightMargin = dp(3)
        b.layoutParams = lp
        b.setOnClickListener { onClick() }
        return b
    }
}