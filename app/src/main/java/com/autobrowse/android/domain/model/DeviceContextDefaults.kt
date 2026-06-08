package com.autobrowse.android.domain.model

import android.app.ActivityManager
import android.content.Context
import kotlin.math.roundToInt

object DeviceContextDefaults {
    fun totalRamGb(context: Context): Int {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return (memoryInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0))
            .roundToInt()
            .coerceAtLeast(1)
    }

    fun defaultContextTokens(context: Context, model: LocalLlmModel): Int {
        val ramGb = totalRamGb(context)
        return defaultContextTokens(ramGb, model)
    }

    fun defaultContextTokens(ramGb: Int, model: LocalLlmModel): Int {
        val bounds = LocalLlmCatalog.contextBounds(model)
        val recommended = when {
            ramGb >= 24 -> 24_576
            ramGb >= 16 -> 16_384
            ramGb >= 12 -> 12_288
            else -> 8_192
        }
        return recommended.coerceIn(bounds.first, bounds.last)
    }
}