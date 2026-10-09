package com.example.flashsale

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import android.text.InputType
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {
    private lateinit var store: SettingsStore
    private lateinit var root: LinearLayout
    private lateinit var timeInput: EditText
    private lateinit var leadInput: EditText
    private lateinit var holdInput: EditText
    private lateinit var modeInput: Spinner
    private lateinit var dryRunInput: CheckBox
    private lateinit var pointsPanel: LinearLayout
    private val timePattern = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 20, 24, 24) }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        header("FlashSale Helper")
        label("Accessibility-only overlay and user-defined timed gestures. No root required.")
        button("1. Enable AccessibilityService") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button("2. Show floating controls") {
            val service = FlashAccessibilityService.instance
            if (service == null) toast("Enable the AccessibilityService first")
            else { service.showControls(); toast("Open your target app, then use + on the overlay") }
        }
        label("Configure the countdown, then save. Pick targets from the overlay while the shopping app is visible.")
        val saved = store.read()
        timeInput = input("Next target time (HH:mm:ss.SSS)", saved.targetTime.format(timePattern))
        leadInput = input("Manual early dispatch (ms: 0–2000)", saved.earlyMs.toString(), numeric = true)
        holdInput = input("Tap duration (ms: 1–1000)", saved.tapDurationMs.toString(), numeric = true)
        label("Network RTT lead compensation (heuristic, not verified server latency):")
        modeInput = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                arrayOf("None (recommended)", "Half RTT", "Full RTT"))
            setSelection(saved.compensation.ordinal)
            root.addView(this)
        }
        dryRunInput = CheckBox(this).apply {
            text = "Dry-run only (no touch gestures)"
            isChecked = saved.dryRun
            root.addView(this)
        }
        button("Save settings") {
            try {
                val target = LocalTime.parse(timeInput.text.toString().trim(), timePattern)
                val lead = leadInput.text.toString().toLong()
                val tap = holdInput.text.toString().toLong()
                require(lead in 0..2000 && tap in 1..1000)
                store.saveBasic(target, lead, Compensation.entries[modeInput.selectedItemPosition],
                    dryRunInput.isChecked, tap)
                toast("Settings saved")
            } catch (_: Exception) { toast("Invalid time or limits; use HH:mm:ss.SSS") }
        }

        header("Saved touch targets")
        label("Targets are captured in screen order; each target has a wait-after-completion delay. Long-press Remove to delete safely.")
        pointsPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(pointsPanel)
        button("Clear all targets") {
            store.clear(); renderPoints(); toast("Targets cleared")
        }
        header("How to execute")
        label("In the target app: use + to capture points. ARM validates the saved sequence and locks its deadline. START activates the countdown. STOP cancels pending work. The button remains visible during a run. The target is the next occurrence of the entered time in your phone's timezone.")
        label("NTP synchronization is optional. It estimates a public time server's clock, not the shopping platform's clock. Manually review orders and payments. Do not use automated gestures to bypass restrictions or verification challenges.")
        renderPoints()
    }

    override fun onResume() { super.onResume(); if (::pointsPanel.isInitialized) renderPoints() }

    private fun renderPoints() {
        pointsPanel.removeAllViews()
        val points = store.read().points
        if (points.isEmpty()) {
            labelIn(pointsPanel, "No targets yet. Open your shopping app and press + on the overlay.")
        }
        points.forEachIndexed { i, p ->
            val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 6, 0, 8) }
            labelIn(panel, "#${i + 1}: (${(p.x * 100).toInt()}%, ${(p.y * 100).toInt()}%) — ${p.targetPackage}")
            val delay = EditText(this).apply {
                hint = "Delay after this point (ms)"
                inputType = InputType.TYPE_CLASS_NUMBER
                setSingleLine(true)
                setText(p.delayAfterMs.toString())
            }
            panel.addView(delay)
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val update = Button(this).apply {
                text = "Save delay"
                setOnClickListener {
                    val ms = delay.text.toString().toLongOrNull()
                    if (ms == null || ms !in 0..10_000) toast("Delay must be 0–10000 ms")
                    else { store.updateDelay(i, ms); toast("Saved") }
                }
            }
            row.addView(update)
            val remove = Button(this).apply {
                text = "Remove"
                setOnClickListener {
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setMessage("Remove touch target #${i + 1}?")
                        .setPositiveButton("Remove") { _, _ -> store.remove(i); renderPoints() }
                        .setNegativeButton("Cancel", null).show()
                }
            }
            row.addView(remove)
            panel.addView(row)
            pointsPanel.addView(panel)
        }
    }

    private fun header(value: String) {
        root.addView(TextView(this).apply { text = value; textSize = 22f; setTextColor(Color.BLACK); setPadding(0, 14, 0, 6) })
    }
    private fun label(value: String) = labelIn(root, value)
    private fun labelIn(parent: LinearLayout, value: String) {
        parent.addView(TextView(this).apply { text = value; textSize = 14f; setPadding(0, 3, 0, 5) })
    }
    private fun input(hintText: String, value: String, numeric: Boolean = false): EditText = EditText(this).also {
        it.hint = hintText; it.setSingleLine(true); it.setText(value)
        if (numeric) it.inputType = InputType.TYPE_CLASS_NUMBER
        root.addView(it)
    }
    private fun button(title: String, onClick: () -> Unit) {
        root.addView(Button(this).apply { text = title; setOnClickListener { onClick() } })
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
