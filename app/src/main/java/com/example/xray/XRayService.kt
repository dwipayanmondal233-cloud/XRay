package com.example.xray

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
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
    private val regionSel = RegionSelector(this)
    private val bubble = BubbleController(this) { onBubbleTap() }
    private var lastCaptureTime = 0L
    private var busy = false
    private var pendingOverlay = false
    private var sensorManager: SensorManager? = null
    private var shake: ShakeDetector? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlay.onDismissed = { restoreBubble() }
        applyBubble()
        applyShake()
        listener?.invoke()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay.dismiss()
        if (regionSel.isShowing()) regionSel.dismiss()
        bubble.remove()
        restoreBubble()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopShake()
        overlay.dismiss()
        regionSel.dismiss()
        bubble.remove()
        if (instance === this) instance = null
        listener?.invoke()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopShake()
        overlay.dismiss()
        regionSel.dismiss()
        bubble.remove()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun prefs() = getSharedPreferences("xray", MODE_PRIVATE)

    private fun bubbleWanted(): Boolean = prefs().getBoolean("bubble", false)

    private fun regionWanted(): Boolean = prefs().getBoolean("region", false)

    fun applyBubble() {
        restoreBubble()
    }

    private fun restoreBubble() {
        if (bubbleWanted()) {
            if (!overlay.isShowing() && !regionSel.isShowing()) bubble.show()
        } else {
            bubble.remove()
        }
    }

    fun applyShake() {
        stopShake()
        val p = prefs()
        if (!p.getBoolean("shake", false)) return
        val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (acc == null) {
            Toast.makeText(this, "This device has no accelerometer.", Toast.LENGTH_LONG).show()
            return
        }
        val d = ShakeDetector(p.getInt("sens", 1)) { onShake() }
        sm.registerListener(d, acc, SensorManager.SENSOR_DELAY_GAME)
        sensorManager = sm
        shake = d
    }

    private fun stopShake() {
        val d = shake
        if (d != null) {
            sensorManager?.unregisterListener(d)
        }
        shake = null
    }

    private fun onShake() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return
        if (busy || overlay.isShowing() || regionSel.isShowing()) return
        xray(700)
    }

    private fun onBubbleTap() {
        if (busy) {
            Toast.makeText(this, "Still working. Wait a moment.", Toast.LENGTH_SHORT).show()
            return
        }
        xray(150)
    }

    private fun collapseShade() {
        if (Build.VERSION.SDK_INT >= 31) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
            try {
                sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
            } catch (e: Exception) {
            }
        }
    }

    fun xrayFromShade() {
        collapseShade()
        xray(900)
    }

    fun captureDelayed(delayMs: Long) {
        handler.postDelayed({ capture() }, delayMs)
    }

    fun xray(delayMs: Long) {
        overlay.dismiss()
        regionSel.dismiss()
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
                        } else if (pendingOverlay && regionWanted()) {
                            busy = false
                            pendingOverlay = false
                            lastBitmap = null
                            lastResult = null
                            lastStatus = "Select an area to scan."
                            listener?.invoke()
                            showRegion(bmp)
                        } else {
                            finish(bmp, "Captured " + bmp.width + "x" + bmp.height + ". Reading text...")
                            runOcr(bmp, null)
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

    private fun showRegion(bmp: Bitmap) {
        regionSel.show(
            bmp,
            onRegion = { r ->
                busy = true
                pendingOverlay = true
                runOcr(bmp, r)
            },
            onFull = {
                busy = true
                pendingOverlay = true
                runOcr(bmp, null)
            },
            onCancel = {
                lastStatus = "Area selection cancelled."
                restoreBubble()
                listener?.invoke()
            }
        )
    }

    private fun runOcr(bmp: Bitmap, crop: Rect?) {
        val w = bmp.width
        val h = bmp.height
        var input = bmp
        var ox = 0
        var oy = 0
        if (crop != null) {
            try {
                input = Bitmap.createBitmap(bmp, crop.left, crop.top, crop.width(), crop.height())
                ox = crop.left
                oy = crop.top
            } catch (e: Exception) {
                input = bmp
                ox = 0
                oy = 0
            }
        }
        val deva = prefs().getBoolean("deva", false)
        OcrEngine.run(
            input,
            deva,
            onDone = { raw ->
                busy = false
                val r = if (ox != 0 || oy != 0) raw.offset(ox, oy) else raw
                lastResult = r
                HistoryStore.addIfEnabled(this, r)
                val where = if (crop != null) " (area)" else ""
                val status = if (r.blocks.isEmpty()) {
                    "No text found. (" + r.millis + " ms, " + r.script + ")" + where
                } else {
                    "Found " + r.lineCount() + " lines in " + r.millis + " ms (" + r.script + ")" + where
                }
                lastStatus = status
                if (pendingOverlay) {
                    pendingOverlay = false
                    lastBitmap = null
                    if (r.blocks.isEmpty()) {
                        Toast.makeText(this, "No text found in that area.", Toast.LENGTH_SHORT).show()
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