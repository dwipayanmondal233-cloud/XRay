package com.example.xray

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class XRayService : AccessibilityService() {

    companion object {
        var instance: XRayService? = null
        var lastBitmap: Bitmap? = null
        var lastResult: OcrResult? = null
        var lastStatus: String = "No capture yet."
        var listener: (() -> Unit)? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private val overlay = OverlayController(this)
    private val bubble = BubbleController(this) { onBubbleTap() }
    private var lastCaptureTime = 0L
    private var busy = false
    private var pendingOverlay = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlay.onDismissed = { restoreBubble() }
        applyBubble()
        listener?.invoke()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay.dismiss()
        bubble.remove()
        restoreBubble()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        overlay.dismiss()
        bubble.remove()
        if (instance === this) instance = null
        listener?.invoke()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        overlay.dismiss()
        bubble.remove()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun bubbleWanted(): Boolean {
        return getSharedPreferences("xray", MODE_PRIVATE).getBoolean("bubble", false)
    }

    fun applyBubble() {
        if (bubbleWanted()) {
            if (!overlay.isShowing()) bubble.show()
        } else {
            bubble.remove()
        }
    }

    private fun restoreBubble() {
        if (bubbleWanted()) {
            if (!overlay.isShowing()) bubble.show()
        } else {
            bubble.remove()
        }
    }

    private fun onBubbleTap() {
        if (busy) {
            Toast.makeText(this, "Still working. Wait a moment.", Toast.LENGTH_SHORT).show()
            return
        }
        xray(150)
    }

    fun captureDelayed(delayMs: Long) {
        handler.postDelayed({ capture() }, delayMs)
    }

    fun xray(delayMs: Long) {
        overlay.dismiss()
        bubble.hide()
        val wait = if (delayMs < 200) 200L else delayMs
        handler.postDelayed({
            if (busy) {
                Toast.makeText(this, "Still working. Wait a moment.", Toast.LENGTH_SHORT).show()
                restoreBubble()
            } else {
                pendingOverlay = true
                capture()
            }
        }, wait)
    }

    fun capture() {
        if (busy) {
            Toast.makeText(this, "Still working. Wait a moment.", Toast.LENGTH_SHORT).show()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastCaptureTime < 1100) {
            pendingOverlay = false
            Toast.makeText(this, "Too fast. Wait 1 second and try again.", Toast.LENGTH_SHORT).show()
            restoreBubble()
            return
        }
        busy = true
        lastCaptureTime = now

        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        var bmp: Bitmap? = null
                        try {
                            val hw = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                            bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                            hw?.recycle()
                        } catch (e: Exception) {
                            bmp = null
                        } finally {
                            buffer.close()
                        }
                        if (bmp == null) {
                            busy = false
                            finish(null, "Could not read the screenshot image.")
                        } else if (isBlank(bmp)) {
                            busy = false
                            pendingOverlay = false
                            finish(
                                bmp,
                                "Blank capture (" + bmp.width + "x" + bmp.height +
                                    "). This app may block screenshots, or the screen is one solid color."
                            )
                        } else {
                            finish(bmp, "Captured " + bmp.width + "x" + bmp.height + ". Reading text...")
                            runOcr(bmp)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        busy = false
                        finish(null, errorText(errorCode))
                    }
                }
            )
        } catch (e: Exception) {
            busy = false
            finish(null, "Capture failed: " + (e.message ?: "unknown error"))
        }
    }

    private fun runOcr(bmp: Bitmap) {
        val w = bmp.width
        val h = bmp.height
        val deva = getSharedPreferences("xray", MODE_PRIVATE).getBoolean("deva", false)
        OcrEngine.run(
            bmp,
            deva,
            onDone = { r ->
                busy = false
                lastResult = r
                val status = if (r.blocks.isEmpty()) {
                    "No text found. (" + r.millis + " ms, " + r.script + ")"
                } else {
                    "Found " + r.lineCount() + " lines in " + r.millis + " ms (" + r.script + ")"
                }
                lastStatus = status
                if (pendingOverlay) {
                    pendingOverlay = false
                    lastBitmap = null
                    if (r.blocks.isEmpty()) {
                        Toast.makeText(this, "No text found on this screen.", Toast.LENGTH_SHORT).show()
                        restoreBubble()
                    } else {
                        overlay.show(r, w, h)
                        if (!overlay.isShowing()) restoreBubble()
                    }
                } else {
                    Toast.makeText(this, status, Toast.LENGTH_SHORT).show()
                    restoreBubble()
                }
                listener?.invoke()
            },
            onError = { msg ->
                busy = false
                pendingOverlay = false
                lastResult = null
                lastStatus = "Text reading failed: $msg"
                Toast.makeText(this, lastStatus, Toast.LENGTH_LONG).show()
                restoreBubble()
                listener?.invoke()
            }
        )
    }

    private fun errorText(code: Int): String {
        return when (code) {
            1 -> "Android had an internal error. Try again."
            2 -> "No screenshot access. Turn the XRay service off and on again."
            3 -> "Too fast. Wait 1 second and try again."
            4 -> "Invalid display."
            5 -> "That window is not available for capture."
            6 -> "This app blocks screenshots."
            else -> "Unknown error code: $code"
        }
    }

    private fun isBlank(b: Bitmap): Boolean {
        val first = b.getPixel(0, 0)
        val stepX = maxOf(1, b.width / 20)
        val stepY = maxOf(1, b.height / 20)
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) {
                if (b.getPixel(x, y) != first) return false
                x += stepX
            }
            y += stepY
        }
        return true
    }

    private fun finish(bmp: Bitmap?, status: String) {
        if (bmp == null) pendingOverlay = false
        lastBitmap = bmp
        lastResult = null
        lastStatus = status
        Toast.makeText(this, status, Toast.LENGTH_SHORT).show()
        if (!pendingOverlay) restoreBubble()
        listener?.invoke()
    }
}