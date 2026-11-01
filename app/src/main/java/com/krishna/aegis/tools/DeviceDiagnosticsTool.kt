package com.krishna.aegis.tools

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import org.json.JSONObject
import java.util.Locale

class DeviceDiagnosticsTool : AegisTool {
    override val name = "device_diagnostics"
    override val description = "Checks the phone's live system telemetry: RAM memory usage, free storage space, battery level, charging status, and running background state."
    override val parameterSchema = emptyMap<String, String>()

    override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome {
        val appContext = context.androidContext

        // 1. Memory / RAM Telemetry
        val actManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)

        val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        val availRamGb = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)
        val usedRamGb = totalRamGb - availRamGb
        val ramPercentUsed = (usedRamGb / totalRamGb) * 100.0

        // 2. Storage Telemetry
        val stat = StatFs(Environment.getDataDirectory().path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availBlocks = stat.availableBlocksLong
        val totalStorageGb = (totalBlocks * blockSize) / (1024.0 * 1024.0 * 1024.0)
        val freeStorageGb = (availBlocks * blockSize) / (1024.0 * 1024.0 * 1024.0)

        // 3. Battery Telemetry
        val bIntent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bLevel = bIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val bScale = bIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val bStatus = bIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = bStatus == BatteryManager.BATTERY_STATUS_CHARGING || bStatus == BatteryManager.BATTERY_STATUS_FULL
        val batteryPct = if (bLevel >= 0 && bScale > 0) (bLevel * 100) / bScale else -1

        val report = JSONObject().apply {
            put("ram", JSONObject().apply {
                put("total", String.format(Locale.US, "%.1f GB", totalRamGb))
                put("available", String.format(Locale.US, "%.1f GB", availRamGb))
                put("used_percent", String.format(Locale.US, "%.1f%%", ramPercentUsed))
                put("low_memory_flag", memInfo.lowMemory)
            })
            put("storage", JSONObject().apply {
                put("total", String.format(Locale.US, "%.1f GB", totalStorageGb))
                put("free", String.format(Locale.US, "%.1f GB", freeStorageGb))
            })
            put("battery", JSONObject().apply {
                put("level_percent", "$batteryPct%")
                put("is_charging", isCharging)
            })
            put("active_package", context.getActivePackageName())
        }

        return ToolOutcome.Ok(report.toString(2))
    }
}
