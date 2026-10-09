package com.example.flashsale

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import java.time.ZoneId
import java.util.concurrent.Executors
import kotlin.math.max

class FlashAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: FlashAccessibilityService? = null
            private set
        private const val TAG = "FlashSaleHelper"
    }

    private val main = Handler(Looper.getMainLooper())
    private val background = Executors.newSingleThreadExecutor()
    private lateinit var store: SettingsStore
    private lateinit var controls: FloatingControls
    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    var state = RunState.IDLE
        private set
    var statusMessage = "Open a shopping app to configure"
        private set
    var syncReading: SyncReading? = null
        private set
    private var latestPackage: String? = null
    private var armedSettings: TimingSettings? = null
    private var deadlineNanos: Long? = null
    private var runToken = 0L
    private var tick: Runnable? = null
    private var overlayClock: Runnable? = null
    private var executingIndex = -1

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        store = SettingsStore(this)
        controls = FloatingControls(this)
        controls.show()
        statusMessage = "Ready. Add targets with +"
        refresh()
        scheduleOverlayClock()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != packageName && pkg != "com.android.systemui") latestPackage = pkg
    }

    override fun onInterrupt() { stopExecution("Accessibility interrupted") }

    override fun onDestroy() {
        stopExecution("Service disconnected")
        tick?.let(main::removeCallbacks)
        overlayClock?.let(main::removeCallbacks)
        overlayClock = null
        if (::controls.isInitialized) controls.destroy()
        background.shutdownNow()
        instance = null
        super.onDestroy()
    }

    fun showControls() { if (::controls.isInitialized) controls.show() }

    private fun scheduleOverlayClock() {
        val runnable = object : Runnable {
            override fun run() {
                if (!::controls.isInitialized) return
                controls.refresh()
                main.postDelayed(this, if (state == RunState.COUNTDOWN) 33L else 80L)
            }
        }
        overlayClock = runnable
        main.post(runnable)
    }

    fun beginPick() {
        if (state == RunState.COUNTDOWN || state == RunState.RUNNING || state == RunState.ARMED) {
            message("STOP the active schedule before changing targets")
            return
        }
        val target = latestPackage
        if (target.isNullOrBlank() || target == "android") {
            message("Open your target shopping app before selecting points")
            return
        }
        @Suppress("DEPRECATION")
        val rotation = wm.defaultDisplay.rotation
        controls.pickPoint { x, y ->
            try {
                store.add(TapPoint(x, y, 150, rotation, target))
                statusMessage = "Saved target #${store.read().points.size}"
                message("Target added to $target")
                refresh()
            } catch (ex: Exception) { message("Cannot save: ${ex.message}") }
        }
    }

    fun syncClock() {
        if (state == RunState.ARMED || state == RunState.COUNTDOWN || state == RunState.RUNNING) {
            message("Stop before changing clock synchronization")
            return
        }
        statusMessage = "Measuring NTP…"
        refresh()
        background.execute {
            val result = runCatching { NtpSync.collect() }
            main.post {
                result.onSuccess {
                    syncReading = it
                    statusMessage = "NTP synchronized"
                    log("NTP offset=${it.offsetMs}ms RTT=${it.roundTripMs}ms jitter=${it.jitterMs}ms")
                }.onFailure {
                    syncReading = null
                    statusMessage = "NTP unavailable; device clock only"
                    message("NTP failed. UDP 123 may be blocked.")
                }
                refresh()
            }
        }
    }

    fun arm() {
        if (state == RunState.RUNNING || state == RunState.COUNTDOWN) {
            message("Stop current execution before rearming")
            return
        }
        try {
            val settings = store.read()
            require(settings.points.isNotEmpty()) { "Add a touch point first" }
            val pkg = settings.points.first().targetPackage
            require(settings.points.all { it.targetPackage == pkg }) { "Multiple target packages not supported" }
            require(latestPackage == pkg) { "Open $pkg before arming" }
            @Suppress("DEPRECATION")
            val rotation = wm.defaultDisplay.rotation
            require(settings.points.all { it.rotation == rotation }) { "Display rotation changed; recreate targets" }
            require(settings.points.size <= 25) { "Too many targets" }
            val nowEpoch = System.currentTimeMillis()
            val nowElapsed = SystemClock.elapsedRealtimeNanos()
            val reading = syncReading?.takeIf {
                SystemClock.elapsedRealtimeNanos() - it.recordedElapsedNanos in 0L..(5L * 60L * 1_000_000_000L)
            }
            if (syncReading != null && reading == null) {
                syncReading = null
                statusMessage = "NTP sample expired; using device clock"
            }
            val offset = reading?.offsetMs ?: 0L
            val rtt = reading?.roundTripMs ?: 0L
            val targetEpoch = Scheduler.nextOccurrenceEpochMs(nowEpoch + offset, settings.targetTime, ZoneId.systemDefault())
            val lead = Scheduler.leadMs(settings.earlyMs, rtt, settings.compensation)
            val deadline = Scheduler.deadlineElapsedNanos(nowEpoch, nowElapsed, targetEpoch, offset, lead)
            val size = Point()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealSize(size)
            require(settings.points.none {
                controls.wouldObscureTarget((it.x * size.x).toInt(), (it.y * size.y).toInt())
            }) { "Touch target overlaps floating overlay; move overlay and rearm" }
            runToken++
            armedSettings = settings
            deadlineNanos = deadline
            state = RunState.ARMED
            statusMessage = "Tap START (${settings.points.size} targets${if (settings.dryRun) ", dry-run" else ""})"
            log("ARM target=$targetEpoch lead=${lead}ms mode=${settings.compensation} dryRun=${settings.dryRun}")
        } catch (ex: Exception) {
            state = RunState.ERROR
            statusMessage = ex.message ?: "Invalid configuration"
            message(statusMessage)
        }
        refresh()
    }

    fun startCountdown() {
        if (state != RunState.ARMED || armedSettings == null || deadlineNanos == null) {
            message("ARM first")
            return
        }
        if ((deadlineNanos ?: 0) <= SystemClock.elapsedRealtimeNanos()) {
            fail("The armed deadline has passed")
            return
        }
        state = RunState.COUNTDOWN
        statusMessage = "Waiting for scheduled dispatch"
        val token = ++runToken
        tick = object : Runnable {
            override fun run() {
                if (token != runToken || state != RunState.COUNTDOWN) return
                val nanosLeft = (deadlineNanos ?: return) - SystemClock.elapsedRealtimeNanos()
                if (nanosLeft <= 0L) { executeStep(0, token); return }
                val waitMs = max(1L, nanosLeft / 1_000_000L - 12L)
                main.postDelayed(this, waitMs.coerceAtMost(1000L))
            }
        }
        main.post(tick!!)
        refresh()
    }

    fun stopExecution(reason: String = "Stopped by user") {
        runToken++
        tick?.let(main::removeCallbacks)
        tick = null
        armedSettings = null
        deadlineNanos = null
        executingIndex = -1
        state = RunState.STOPPED
        statusMessage = reason
        if (::controls.isInitialized) refresh()
        log("STOP: $reason")
    }

    fun remainingMs(): Long? = deadlineNanos?.let {
        max(0L, (it - SystemClock.elapsedRealtimeNanos()) / 1_000_000L)
    }

    private fun executeStep(index: Int, token: Long) {
        if (token != runToken) return
        val settings = armedSettings ?: return
        if (index >= settings.points.size) {
            state = RunState.COMPLETED
            statusMessage = if (settings.dryRun) "Dry-run completed" else "All gestures dispatched"
            log("DONE (${settings.points.size} targets)")
            deadlineNanos = null
            refresh()
            return
        }
        state = RunState.RUNNING
        executingIndex = index
        val point = settings.points[index]
        if (latestPackage != point.targetPackage) {
            fail("Target app no longer foreground; aborted")
            return
        }
        @Suppress("DEPRECATION")
        if (wm.defaultDisplay.rotation != point.rotation) {
            fail("Display rotated; aborted")
            return
        }
        val size = Point()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealSize(size)
        val x = (point.x * size.x).coerceIn(1f, size.x - 1f)
        val y = (point.y * size.y).coerceIn(1f, size.y - 1f)
        if (controls.wouldObscureTarget(x.toInt(), y.toInt())) {
            fail("Target overlaps overlay; aborted")
            return
        }
        val dispatchedElapsed = SystemClock.elapsedRealtimeNanos()
        val deltaMs = if (index == 0 && deadlineNanos != null)
            (dispatchedElapsed - deadlineNanos!!) / 1_000_000.0 else null
        statusMessage = "Step ${index + 1}/${settings.points.size}" +
            (if (deltaMs == null) "" else " deviation %.1f ms".format(deltaMs))
        log("Step ${index + 1} timeDeviationMs=$deltaMs x=$x y=$y dryRun=${settings.dryRun}")
        refresh()
        if (settings.dryRun) {
            main.postDelayed({ if (token == runToken) nextStep(index, token) }, settings.tapDurationMs)
            return
        }
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(path, 0L, settings.tapDurationMs)
        ).build()
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (token == runToken) nextStep(index, token)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (token == runToken) fail("Gesture cancelled at step ${index + 1}")
            }
        }, main)
        if (!accepted && token == runToken) fail("Android rejected gesture at step ${index + 1}")
    }

    private fun nextStep(index: Int, token: Long) {
        if (token != runToken) return
        val delay = armedSettings?.points?.getOrNull(index)?.delayAfterMs ?: 0L
        main.postDelayed({ if (token == runToken) executeStep(index + 1, token) }, delay)
    }

    private fun fail(reason: String) {
        runToken++
        tick?.let(main::removeCallbacks)
        tick = null
        state = RunState.ERROR
        statusMessage = reason
        armedSettings = null
        deadlineNanos = null
        message(reason)
        log("ERROR: $reason")
        refresh()
    }

    private fun refresh() { if (::controls.isInitialized) controls.refresh() }
    private fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun log(text: String) {
        Log.i(TAG, text)
        // Keep a bounded local diagnostic trail; never record screen contents or credentials.
        try {
            val file = getFileStreamPath("execution.log")
            if (file.exists() && file.length() > 128 * 1024) file.delete()
            openFileOutput("execution.log", MODE_APPEND).bufferedWriter().use {
                it.appendLine("${System.currentTimeMillis()} $text")
            }
        } catch (_: Exception) { }
    }
}
