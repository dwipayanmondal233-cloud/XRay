package com.example.xray

import android.graphics.Rect

enum class SmartType { URL, EMAIL, PHONE, CODE }

data class SmartItem(val type: SmartType, val text: String, val box: Rect)

object SmartDetector {

    private val emailRe = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    private val urlRe = Regex(
        "(?i)(?:https?://|www\\.)[^\\s<>\"']+|" +
            "\\b[a-z0-9][a-z0-9-]*(?:\\.[a-z0-9-]+)*\\." +
            "(?:com|org|net|in|io|co|me|app|dev|gov|edu|info|xyz|ly|gl|be)\\b" +
            "(?:/[^\\s<>\"']*)?"
    )

    private val phoneRe = Regex("(?<![\\w+])\\+?\\d[\\d ()-]{6,17}\\d(?![\\w])")

    private val dateRe = Regex("\\d{1,2}-\\d{1,2}-\\d{2,4}|\\d{4}-\\d{1,2}-\\d{1,2}")

    private val keywordCodeRe = Regex(
        "(?:[Cc]ode|CODE|[Cc]oupon|COUPON|[Pp]romo|PROMO)\\s*[:\\-]\\s*([A-Z0-9]{4,12})(?![A-Za-z0-9])"
    )

    private val tokenCodeRe = Regex("(?<![A-Za-z0-9])[A-Z0-9]{5,12}(?![A-Za-z0-9])")

    private class Span(val start: Int, val end: Int, val rect: Rect)

    fun detect(result: OcrResult): List<SmartItem> {
        val out = ArrayList<SmartItem>()
        for (b in result.blocks) {
            for (l in b.lines) {
                detectLine(l, out)
            }
        }
        return out
    }

    private fun detectLine(line: OcrLine, out: MutableList<SmartItem>) {
        val sb = StringBuilder()
        val spans = ArrayList<Span>()
        if (line.elements.isEmpty()) {
            sb.append(line.text)
            spans.add(Span(0, sb.length, line.box))
        } else {
            for (e in line.elements) {
                if (sb.isNotEmpty()) sb.append(' ')
                val s = sb.length
                sb.append(e.text)
                spans.add(Span(s, sb.length, e.box))
            }
        }
        val text = sb.toString()
        if (text.length < 5) return

        val taken = ArrayList<IntArray>()

        fun add(type: SmartType, s: Int, e: Int) {
            if (e <= s) return
            for (t in taken) {
                if (s < t[1] && e > t[0]) return
            }
            var box: Rect? = null
            for (sp in spans) {
                if (sp.start < e && sp.end > s && sp.rect.width() > 0 && sp.rect.height() > 0) {
                    if (box == null) {
                        box = Rect(sp.rect)
                    } else {
                        box.union(sp.rect)
                    }
                }
            }
            if (box == null) return
            taken.add(intArrayOf(s, e))
            out.add(SmartItem(type, text.substring(s, e).trim(), box))
        }

        for (m in emailRe.findAll(text)) {
            add(SmartType.EMAIL, m.range.first, m.range.last + 1)
        }

        for (m in urlRe.findAll(text)) {
            val s = m.range.first
            var e = m.range.last + 1
            while (e > s && ".,;:!?)]}\"'".indexOf(text[e - 1]) >= 0) {
                e--
            }
            add(SmartType.URL, s, e)
        }

        for (m in phoneRe.findAll(text)) {
            var digits = 0
            for (c in m.value) {
                if (c.isDigit()) digits++
            }
            if (digits < 8 || digits > 15) continue
            if (dateRe.matches(m.value.trim())) continue
            add(SmartType.PHONE, m.range.first, m.range.last + 1)
        }

        for (m in keywordCodeRe.findAll(text)) {
            val g = m.groups[1] ?: continue
            add(SmartType.CODE, g.range.first, g.range.last + 1)
        }

        for (m in tokenCodeRe.findAll(text)) {
            var hasLetter = false
            var hasDigit = false
            for (c in m.value) {
                if (c.isDigit()) hasDigit = true else hasLetter = true
            }
            if (hasLetter && hasDigit) {
                add(SmartType.CODE, m.range.first, m.range.last + 1)
            }
        }
    }
}