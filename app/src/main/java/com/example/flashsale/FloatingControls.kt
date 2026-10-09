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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * ComposeView inside a WindowManager TYPE_ACCESSIBILITY_OVERLAY.
 * View-tree lifecycle and saved-state owners are required: an AccessibilityService
 * has no Activity decor view and Compose must not retain a destroyed service.
 */
private class OverlayWindowOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun attach() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
}

private data class OverlaySnapshot(
    val localMs: Long = System.currentTimeMillis(),
    val remainingMs: Long? = null,
    val syncReading: SyncReading? = null,
    val state: RunState = RunState.IDLE,
    val status: String = "Ready"
)

/**
 * Miuix Compose floating control panel.
 * Crosshair picker intentionally uses a raw Android full-screen view to preserve
 * absolute physical screen coordinate accuracy.
 */
class FloatingControls(private val service: FlashAccessibilityService) {
    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlay: ComposeView? = null
    private var owner: OverlayWindowOwner? = null
    private var params: WindowManager.LayoutParams? = null
    private var picker: FrameLayout? = null
    private var compact by mutableStateOf(false)
    private var snapshot by mutableStateOf(OverlaySnapshot())
    private val displayFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).toInt()
    private fun background(color: Int, radius: Int = 12): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    fun show() {
        if (overlay != null || picker != null) return
        val lifecycleOwner = OverlayWindowOwner()
        lifecycleOwner.attach()
        val panel = ComposeView(service).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setBackgroundColor(Color.TRANSPARENT)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                val themeController = remember { ThemeController(ColorSchemeMode.System) }
                MiuixTheme(controller = themeController) {
                    OverlayPanel(snapshot)
                }
            }
        }
        val width = (service.resources.displayMetrics.widthPixels - dp(20)).coerceAtMost(dp(390))
        val lp = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(10)
            y = dp(90)
        }
        try {
            wm.addView(panel, lp)
            owner = lifecycleOwner
            params = lp
            overlay = panel
            refresh()
        } catch (_: Exception) {
            lifecycleOwner.destroy()
            panel.disposeComposition()
        }
    }

    @Composable
    private fun OverlayPanel(data: OverlaySnapshot) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiuixText(
                        "≡ Click Helper",
                        modifier = Modifier.weight(1f).pointerInput(Unit) {
                            detectDragGestures { change, distance ->
                                change.consume()
                                moveBy(distance.x, distance.y)
                            }
                        },
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    )
                    MiuixButton(onClick = { compact = !compact }) {
                        MiuixText(if (compact) "Expand" else "Min")
                    }
                }
                MiuixText(
                    "Local  " + displayFormat.format(Date(data.localMs)),
                    style = TextStyle(fontSize = 21.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                )
                if (!compact) {
                    MiuixText(
                        if (data.syncReading == null) "NTP    not synchronized" else
                            "NTP    " + displayFormat.format(Date(data.localMs + data.syncReading.offsetMs)),
                        style = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    )
                    MiuixText(
                        "T-minus " + (data.remainingMs?.let(::formatRemaining) ?: "--:--:--.---"),
                        style = TextStyle(fontSize = 17.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                    )
                    MiuixText(data.state.name + ": " + data.status, style = TextStyle(fontSize = 12.sp))
                    val sync = data.syncReading
                    MiuixText(
                        if (sync == null) "RTT --  |  Offset --  |  Jitter --"
                        else "RTT ${sync.roundTripMs}ms | Offset ${sync.offsetMs}ms | Jitter ±${sync.jitterMs}ms",
                        style = TextStyle(fontSize = 11.sp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        MiuixButton(modifier = Modifier.weight(1f), onClick = { service.beginPick() }) { MiuixText("+ Target") }
                        MiuixButton(modifier = Modifier.weight(1f), onClick = { service.syncClock() }) { MiuixText("Sync") }
                        MiuixButton(modifier = Modifier.weight(1f), onClick = {
                            service.startActivity(android.content.Intent(service, MainActivity::class.java).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        }) { MiuixText("Settings") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    MiuixButton(
                        modifier = Modifier.weight(1f),
                        enabled = data.state !in setOf(RunState.ARMED, RunState.COUNTDOWN, RunState.RUNNING),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        onClick = { service.arm() }
                    ) { MiuixText("ARM") }
                    MiuixButton(
                        modifier = Modifier.weight(1f),
                        enabled = data.state == RunState.ARMED,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        onClick = { service.startCountdown() }
                    ) { MiuixText("START") }
                    MiuixButton(
                        modifier = Modifier.weight(1f),
                        enabled = data.state in setOf(RunState.ARMED, RunState.COUNTDOWN, RunState.RUNNING),
                        onClick = { service.stopExecution() }
                    ) { MiuixText("STOP") }
                }
            }
        }
    }

    private fun formatRemaining(ms: Long): String {
        val value = ms.coerceAtLeast(0L)
        return "%02d:%02d:%02d.%03d".format(
            Locale.US, value / 3_600_000, (value / 60_000) % 60,
            (value / 1000) % 60, value % 1000
        )
    }

    private fun moveBy(deltaX: Float, deltaY: Float) {
        val current = params ?: return
        val panel = overlay ?: return
        current.x = (current.x + deltaX.toInt()).coerceAtLeast(0)
        current.y = (current.y + deltaY.toInt()).coerceAtLeast(0)
        runCatching { wm.updateViewLayout(panel, current) }
    }

    fun hide() {
        overlay?.let { runCatching { wm.removeView(it) } }
        overlay = null
        params = null
        owner?.destroy()
        owner = null
    }

    fun refresh() {
        if (overlay != null) {
            snapshot = OverlaySnapshot(
                localMs = System.currentTimeMillis(),
                remainingMs = service.remainingMs(),
                syncReading = service.syncReading,
                state = service.state,
                status = service.statusMessage
            )
        }
    }

    fun wouldObscureTarget(x: Int, y: Int): Boolean {
        val panel = overlay ?: return false
        val position = IntArray(2)
        panel.getLocationOnScreen(position)
        return x >= position[0] && x <= position[0] + panel.width &&
            y >= position[1] && y <= position[1] + panel.height
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
