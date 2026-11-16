package com.example.xray

import android.graphics.Rect

data class OcrElement(val text: String, val box: Rect)

data class OcrLine(val text: String, val box: Rect, val elements: List<OcrElement>)

data class OcrBlock(val text: String, val box: Rect, val lines: List<OcrLine>)

data class OcrResult(
    val blocks: List<OcrBlock>,
    val millis: Long,
    val script: String
) {
    fun lineCount(): Int {
        var n = 0
        for (b in blocks) n += b.lines.size
        return n
    }

    fun allText(): String {
        val sb = StringBuilder()
        for (b in blocks) {
            for (l in b.lines) {
                sb.append(l.text).append('\n')
            }
        }
        return sb.toString().trim()
    }

    fun offset(dx: Int, dy: Int): OcrResult {
        fun mv(r: Rect): Rect {
            val n = Rect(r)
            n.offset(dx, dy)
            return n
        }
        val nb = ArrayList<OcrBlock>()
        for (b in blocks) {
            val nl = ArrayList<OcrLine>()
            for (l in b.lines) {
                val ne = ArrayList<OcrElement>()
                for (e in l.elements) {
                    ne.add(OcrElement(e.text, mv(e.box)))
                }
                nl.add(OcrLine(l.text, mv(l.box), ne))
            }
            nb.add(OcrBlock(b.text, mv(b.box), nl))
        }
        return OcrResult(nb, millis, script)
    }
}