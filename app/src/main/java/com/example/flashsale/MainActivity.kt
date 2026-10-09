package com.example.flashsale

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * MIUI / HyperOS styled configuration screen.
 * The underlying AccessibilityService and scheduler remain framework-independent.
 */
class MainActivity : ComponentActivity() {
    private val store by lazy { SettingsStore(this) }
    private val timePattern = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private var refreshVersion by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeController = remember { ThemeController(ColorSchemeMode.System) }
            MiuixTheme(controller = themeController) {
                SettingsScreen(refreshVersion)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshVersion++
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    @Composable
    private fun SettingsScreen(refresh: Int) {
        val original = remember { store.read() }
        var clock by remember { mutableStateOf(original.targetTime.format(timePattern)) }
        var early by remember { mutableStateOf(original.earlyMs.toString()) }
        var duration by remember { mutableStateOf(original.tapDurationMs.toString()) }
        var compensation by remember { mutableStateOf(original.compensation) }
        var dryRun by remember { mutableStateOf(original.dryRun) }
        var points by remember { mutableStateOf(original.points) }
        LaunchedEffect(refresh) { points = store.read().points }
        val serviceEnabled = FlashAccessibilityService.instance != null

        Scaffold(
            topBar = { TopAppBar(title = "Click Helper", subtitle = "Flash-sale timer & gestures") }
        ) { insets ->
            Column(
                modifier = Modifier.fillMaxSize()
                    .padding(top = insets.calculateTopPadding())
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SectionTitle("Quick access")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = if (serviceEnabled) "Accessibility service is connected" else "Accessibility service is disabled",
                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            "The floating controls run on top of your selected app. No root or Xposed needed.",
                            style = TextStyle(fontSize = 13.sp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                                colors = ButtonDefaults.buttonColorsPrimary()
                            ) { Text("Enable service") }
                            Button(modifier = Modifier.weight(1f), onClick = {
                                val service = FlashAccessibilityService.instance
                                if (service == null) toast("Enable AccessibilityService first")
                                else { service.showControls(); toast("Open your target app, then choose + Target") }
                            }) { Text("Show overlay") }
                        }
                    }
                }

                SectionTitle("Execution timing")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Scheduled time", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium))
                        TextField(
                            value = clock,
                            onValueChange = { clock = it },
                            label = "HH:mm:ss.SSS (device timezone)",
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextField(
                            value = early,
                            onValueChange = { early = it.filter(Char::isDigit) },
                            label = "Manual early trigger (0–2000 ms)",
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextField(
                            value = duration,
                            onValueChange = { duration = it.filter(Char::isDigit) },
                            label = "Tap duration (1–1000 ms)",
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("RTT compensation is optional and heuristic.", style = TextStyle(fontSize = 13.sp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Compensation.entries.forEach { entry ->
                                Button(
                                    modifier = Modifier.weight(1f),
                                    onClick = { compensation = entry },
                                    colors = if (entry == compensation) ButtonDefaults.buttonColorsPrimary()
                                             else ButtonDefaults.buttonColors()
                                ) {
                                    Text(when (entry) {
                                        Compensation.NONE -> "None"
                                        Compensation.HALF_RTT -> "½ RTT"
                                        Compensation.FULL_RTT -> "RTT"
                                    })
                                }
                            }
                        }
                        SwitchPreference(
                            title = "Dry-run mode",
                            summary = "Simulate steps without sending touches (recommended for first tests)",
                            checked = dryRun,
                            onCheckedChange = { dryRun = it }
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            onClick = {
                                try {
                                    val target = LocalTime.parse(clock.trim(), timePattern)
                                    val lead = requireNotNull(early.toLongOrNull())
                                    val tap = requireNotNull(duration.toLongOrNull())
                                    require(lead in 0..2000 && tap in 1..1000)
                                    store.saveBasic(target, lead, compensation, dryRun, tap)
                                    toast("Timing settings saved")
                                } catch (_: Exception) { toast("Enter a valid HH:mm:ss.SSS time and allowed timing values") }
                            }
                        ) { Text("Save timing settings") }
                    }
                }

                SectionTitle("Touch sequence")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Use + TARGET in the floating overlay to place a crosshair over each button. Keep all points within one app.", style = TextStyle(fontSize = 13.sp))
                        if (points.isEmpty()) {
                            Text("No points saved yet", style = TextStyle(fontSize = 16.sp))
                        } else {
                            points.forEachIndexed { index, point ->
                                androidx.compose.runtime.key(index, point) {
                                    TargetEditor(index, point, onChange = { points = store.read().points })
                                }
                            }
                        }
                        Button(modifier = Modifier.fillMaxWidth(), onClick = {
                            android.app.AlertDialog.Builder(this@MainActivity)
                                .setTitle("Clear all targets?")
                                .setMessage("This deletes the saved touch sequence.")
                                .setPositiveButton("Clear") { _, _ ->
                                    store.clear()
                                    points = emptyList()
                                    toast("Targets cleared")
                                }.setNegativeButton("Cancel", null).show()
                        }) { Text("Clear all targets") }
                    }
                }

                SectionTitle("How it works")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("1. Pick screen targets with + TARGET.")
                        Text("2. Sync the clock if desired, then ARM.")
                        Text("3. Press START to begin the countdown.")
                        Text("4. STOP cancels pending steps. An in-flight Android gesture may finish.")
                        Text("Server RTT does not guarantee merchant-side timing. Review all purchases manually.", style = TextStyle(fontSize = 12.sp))
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    @Composable
    private fun SectionTitle(value: String) {
        Text(value, style = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold))
    }

    @Composable
    private fun TargetEditor(index: Int, point: TapPoint, onChange: () -> Unit) {
        var delay by remember(index, point.delayAfterMs) { mutableStateOf(point.delayAfterMs.toString()) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Target ${index + 1} • ${point.targetPackage}",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
            )
            Text(
                "X: ${(point.x * 100).toInt()}%   Y: ${(point.y * 100).toInt()}%",
                style = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            )
            TextField(
                value = delay,
                onValueChange = { delay = it.filter(Char::isDigit) },
                label = "Delay after this step (ms)",
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(modifier = Modifier.weight(1f), onClick = {
                    val ms = delay.toLongOrNull()
                    if (ms == null || ms !in 0..10_000) toast("Delay must be 0–10000 ms")
                    else { store.updateDelay(index, ms); onChange(); toast("Delay saved") }
                }) { Text("Save delay") }
                Button(modifier = Modifier.weight(1f), onClick = {
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("Remove target ${index + 1}?")
                        .setPositiveButton("Remove") { _, _ -> store.remove(index); onChange() }
                        .setNegativeButton("Cancel", null).show()
                }) { Text("Remove") }
            }
        }
    }
}
