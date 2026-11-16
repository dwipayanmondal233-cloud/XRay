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
}