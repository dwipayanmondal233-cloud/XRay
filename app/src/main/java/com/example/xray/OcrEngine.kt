package com.example.xray

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

object OcrEngine {

    private var latin: TextRecognizer? = null
    private var devanagari: TextRecognizer? = null

    private fun recognizer(useDevanagari: Boolean): TextRecognizer {
        if (useDevanagari) {
            var r = devanagari
            if (r == null) {
                r = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
                devanagari = r
            }
            return r
        }
        var r = latin
        if (r == null) {
            r = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            latin = r
        }
        return r
    }

    fun run(
        bitmap: Bitmap,
        useDevanagari: Boolean,
        onDone: (OcrResult) -> Unit,
        onError: (String) -> Unit
    ) {
        val start = SystemClock.elapsedRealtime()
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer(useDevanagari).process(image)
                .addOnSuccessListener { text ->
                    val ms = SystemClock.elapsedRealtime() - start
                    val name = if (useDevanagari) "Hindi+English" else "English"
                    onDone(convert(text, ms, name))
                }
                .addOnFailureListener { e ->
                    onError(e.message ?: "unknown error")
                }
        } catch (e: Exception) {
            onError(e.message ?: "unknown error")
        }
    }

    private fun convert(text: Text, ms: Long, script: String): OcrResult {
        val blocks = ArrayList<OcrBlock>()
        for (b in text.textBlocks) {
            val lines = ArrayList<OcrLine>()
            for (l in b.lines) {
                val els = ArrayList<OcrElement>()
                for (e in l.elements) {
                    els.add(OcrElement(e.text, e.boundingBox ?: Rect()))
                }
                lines.add(OcrLine(l.text, l.boundingBox ?: Rect(), els))
            }
            blocks.add(OcrBlock(b.text, b.boundingBox ?: Rect(), lines))
        }
        return OcrResult(blocks, ms, script)
    }
}