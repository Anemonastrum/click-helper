package com.example.flashsale

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Accessibility-owned overlays, no SYSTEM_ALERT_WINDOW or root.
 * Picker is modal: it captures user touches and never passes a picker tap to the target app.
 */
class FloatingControls(private val service: FlashAccessibilityService) {
    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlay: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var picker: FrameLayout? = null
    private var compact = false
    private lateinit var localTime: TextView
    private lateinit var syncTime: TextView
    private lateinit var countdown: TextView
    private lateinit var status: TextView
    private lateinit var metrics: TextView
    private lateinit var actionRow: LinearLayout
    private lateinit var arm: Button
    private lateinit var start: Button
    private lateinit var stop: Button
    private lateinit var toggle: Button
    private val displayFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).toInt()
    private fun background(color: Int, radius: Int = 12): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    fun show() {
        if (overlay != null || picker != null) return
        val panel = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), dp(6), dp(9), dp(7))
            background = background(Color.rgb(27, 33, 44))
            elevation = dp(6).toFloat()
        }
        val heading = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
        val title = text("≡ FlashSale Helper", 13f, true).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        heading.addView(title)
        toggle = button("MIN") { compact = !compact; updateVisibility() }
        heading.addView(toggle)
        panel.addView(heading)
        title.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var downX = 0f
            private var downY = 0f
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                val lp = params ?: return false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = lp.x; initialY = lp.y; downX = event.rawX; downY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = (initialX + event.rawX - downX).toInt().coerceAtLeast(0)
                        lp.y = (initialY + event.rawY - downY).toInt().coerceAtLeast(0)
                        runCatching { wm.updateViewLayout(panel, lp) }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
                }
                return false
            }
        })

        localTime = text("Local  --:--:--.---", 22f, true).apply { typeface = Typeface.MONOSPACE }
        syncTime = text("NTP    --:--:--.---", 14f).apply { typeface = Typeface.MONOSPACE }
        countdown = text("T-minus --:--:--.---", 15f, true).apply { typeface = Typeface.MONOSPACE }
        status = text("IDLE", 11f)
        metrics = text("NTP not synced", 11f)
        listOf(localTime, syncTime, countdown, status, metrics).forEach(panel::addView)

        actionRow = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow.addView(button("+ TARGET") { service.beginPick() })
        actionRow.addView(button("SYNC") { service.syncClock() })
        actionRow.addView(button("SETTINGS") {
            service.startActivity(android.content.Intent(service, MainActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        })
        panel.addView(actionRow)
        val controls = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
        arm = button("ARM") { service.arm() }
        start = button("START") { service.startCountdown() }
        stop = button("STOP") { service.stopExecution() }
        controls.addView(arm); controls.addView(start); controls.addView(stop)
        panel.addView(controls)
        val width = (service.resources.displayMetrics.widthPixels - dp(20)).coerceAtMost(dp(380))
        val lp = WindowManager.LayoutParams(
            width, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(10); y = dp(90) }
        try {
            wm.addView(panel, lp); params = lp; overlay = panel
            updateVisibility()
            refresh()
        } catch (_: Exception) { }
    }

    private fun updateVisibility() {
        if (overlay == null) return
        val visibility = if (compact) View.GONE else View.VISIBLE
        syncTime.visibility = visibility
        metrics.visibility = visibility
        status.visibility = visibility
        actionRow.visibility = visibility
        toggle.text = if (compact) "EXPAND" else "MIN"
        // STOP remains visible even in compact mode.
    }

    fun hide() {
        overlay?.let { runCatching { wm.removeView(it) } }
        overlay = null
        params = null
    }

    fun refresh() {
        if (overlay == null) return
        val now = System.currentTimeMillis()
        val sync = service.syncReading
        localTime.text = "Local  ${displayFormat.format(Date(now))}"
        syncTime.text = if (sync == null) "NTP    not synchronized"
            else "NTP    ${displayFormat.format(Date(now + sync.offsetMs))}"
        val remainingMs = service.remainingMs()
        countdown.text = if (remainingMs == null) "T-minus --:--:--.---" else {
            val ms = remainingMs.coerceAtLeast(0L)
            "T-minus %02d:%02d:%02d.%03d".format(Locale.US,
                ms / 3_600_000, (ms / 60_000) % 60, (ms / 1000) % 60, ms % 1000)
        }
        status.text = "${service.state.name}: ${service.statusMessage}"
        metrics.text = if (sync == null) "RTT -- | Offset -- | Jitter --"
            else "RTT ${sync.roundTripMs}ms | Offset ${sync.offsetMs}ms | Jitter ±${sync.jitterMs}ms"
        arm.isEnabled = service.state !in setOf(RunState.ARMED, RunState.COUNTDOWN, RunState.RUNNING)
        start.isEnabled = service.state == RunState.ARMED
        stop.isEnabled = service.state in setOf(RunState.ARMED, RunState.COUNTDOWN, RunState.RUNNING)
    }

    fun wouldObscureTarget(x: Int, y: Int): Boolean {
        val view = overlay ?: return false
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return x >= location[0] && x <= location[0] + view.width &&
            y >= location[1] && y <= location[1] + view.height
    }

    /** Drag or tap anywhere to position a marker; SAVE captures the mark, not the SAVE button. */
    fun pickPoint(onPicked: (Float, Float) -> Unit) {
        if (picker != null) return
        hide()
        val size = Point()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealSize(size)
        val root = FrameLayout(service).apply { setBackgroundColor(0x22000000) }
        var targetX = size.x * .5f
        var targetY = size.y * .5f
        val marker = object : View(service) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val at = IntArray(2)
                getLocationOnScreen(at)
                val x = targetX - at[0]
                val y = targetY - at[1]
                paint.color = Color.YELLOW
                paint.strokeWidth = dp(2).toFloat()
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(x, y, dp(13).toFloat(), paint)
                canvas.drawLine(x - dp(26), y, x + dp(26), y, paint)
                canvas.drawLine(x, y - dp(26), x, y + dp(26), paint)
            }
        }
        root.addView(marker, FrameLayout.LayoutParams(-1, -1))
        val top = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            background = background(Color.rgb(27, 33, 44))
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        top.addView(text("Drag the crosshair onto the target", 16f, true))
        val coordinates = text("X: ${targetX.toInt()}  Y: ${targetY.toInt()}", 13f)
        top.addView(coordinates)
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        val bottom = LinearLayout(service).apply {
            background = background(Color.rgb(27, 33, 44))
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        bottom.addView(button("CANCEL") { endPicker() })
        bottom.addView(button("SAVE POINT") {
            val nx = (targetX / size.x).coerceIn(0f, 1f)
            val ny = (targetY / size.y).coerceIn(0f, 1f)
            endPicker()
            onPicked(nx, ny)
        })
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        marker.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                targetX = event.rawX.coerceIn(1f, size.x - 1f)
                targetY = event.rawY.coerceIn(1f, size.y - 1f)
                coordinates.text = "X: ${targetX.toInt()}  Y: ${targetY.toInt()}"
                marker.invalidate()
            }
            true
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        try { picker = root; wm.addView(root, lp) } catch (_: Exception) { picker = null; show() }
    }

    private fun endPicker() {
        picker?.let { runCatching { wm.removeView(it) } }
        picker = null
        show()
    }

    fun destroy() {
        picker?.let { runCatching { wm.removeView(it) } }
        picker = null
        hide()
    }

    private fun text(value: String, size: Float, bold: Boolean = false) = TextView(service).apply {
        text = value; textSize = size; setTextColor(Color.WHITE)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(3), dp(3), dp(3), dp(3))
    }

    private fun button(value: String, action: () -> Unit) = Button(service).apply {
        text = value; textSize = 10f; minHeight = dp(40); minimumHeight = dp(40)
        setOnClickListener { action() }
    }
}
