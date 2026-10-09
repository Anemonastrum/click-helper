package com.example.flashsale

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("flashsale_settings", Context.MODE_PRIVATE)

    fun read(): TimingSettings {
        val points = mutableListOf<TapPoint>()
        val json = JSONArray(prefs.getString("points", "[]") ?: "[]")
        for (i in 0 until json.length()) {
            val item = json.getJSONObject(i)
            try {
                points += TapPoint(
                    item.getDouble("x").toFloat(), item.getDouble("y").toFloat(),
                    item.optLong("delayAfterMs", 150).coerceIn(0, 10_000),
                    item.getInt("rotation"), item.getString("targetPackage")
                )
            } catch (_: Exception) { /* Ignore invalid imported targets. */ }
        }
        val target = try {
            LocalTime.parse(prefs.getString("target", "20:00:00.000") ?: "20:00:00.000", DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
        } catch (_: Exception) { LocalTime.of(20, 0) }
        val compensation = try {
            Compensation.valueOf(prefs.getString("compensation", "NONE") ?: "NONE")
        } catch (_: Exception) { Compensation.NONE }
        return TimingSettings(
            target, prefs.getLong("earlyMs", 0).coerceIn(0, 2_000),
            compensation, prefs.getBoolean("dryRun", true),
            prefs.getLong("tapMs", 65).coerceIn(1, 1_000), points
        )
    }

    fun saveBasic(time: LocalTime, earlyMs: Long, compensation: Compensation, dryRun: Boolean, tapMs: Long) {
        prefs.edit().putString("target", time.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")))
            .putLong("earlyMs", earlyMs).putString("compensation", compensation.name)
            .putBoolean("dryRun", dryRun).putLong("tapMs", tapMs).apply()
    }

    fun add(point: TapPoint) {
        val current = read().points.toMutableList()
        if (current.size >= 25) throw IllegalStateException("Maximum 25 targets")
        if (current.isNotEmpty() && current.first().targetPackage != point.targetPackage)
            throw IllegalStateException("All points must belong to one target app")
        current.add(point)
        savePoints(current)
    }

    fun remove(index: Int) {
        val p = read().points.toMutableList()
        if (index in p.indices) { p.removeAt(index); savePoints(p) }
    }

    fun updateDelay(index: Int, delayMs: Long) {
        val points = read().points.toMutableList()
        if (index in points.indices) {
            points[index] = points[index].copy(delayAfterMs = delayMs)
            savePoints(points)
        }
    }

    fun clear() = savePoints(emptyList())

    private fun savePoints(points: List<TapPoint>) {
        val array = JSONArray()
        points.forEach { p ->
            array.put(JSONObject().put("x", p.x.toDouble()).put("y", p.y.toDouble())
                .put("delayAfterMs", p.delayAfterMs).put("rotation", p.rotation)
                .put("targetPackage", p.targetPackage))
        }
        prefs.edit().putString("points", array.toString()).apply()
    }
}
