package com.example.nelexiumlauncher

import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

data class ThemePreset(
    val name: String,
    val backgroundTint: Int,
    val lineColor: Int
)

object ThemePresets {
    val defaults = listOf(
        ThemePreset("Default", Color.rgb(16, 16, 18), Color.rgb(72, 72, 77)),
        ThemePreset("Red Night", Color.rgb(25, 11, 14), Color.rgb(190, 28, 38))
    )

    fun encode(presets: List<ThemePreset>): String = JSONArray().apply {
        presets.forEach { preset ->
            put(JSONObject().apply {
                put("name", preset.name)
                put("background", preset.backgroundTint)
                put("line", preset.lineColor)
            })
        }
    }.toString()

    fun decode(value: String?): MutableList<ThemePreset> {
        if (value.isNullOrBlank()) return defaults.toMutableList()
        return try {
            val json = JSONArray(value)
            if (json.length() == 0) return defaults.toMutableList()
            MutableList(json.length()) { index ->
                val item = json.getJSONObject(index)
                ThemePreset(
                    item.optString("name", "Preset ${index + 1}"),
                    item.optInt("background", defaults.first().backgroundTint),
                    item.optInt("line", defaults.first().lineColor)
                )
            }
        } catch (_: Exception) {
            defaults.toMutableList()
        }
    }
}
