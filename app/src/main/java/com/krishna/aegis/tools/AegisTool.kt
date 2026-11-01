package com.krishna.aegis.tools

import android.content.Context
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo

sealed class ToolOutcome {
    data class Ok(val result: String) : ToolOutcome()
    data class TransientErr(val message: String) : ToolOutcome()
    data class LogicErr(val message: String) : ToolOutcome()
    data class AuthErr(val message: String) : ToolOutcome()
    data class FatalErr(val message: String) : ToolOutcome()
}

interface ToolServiceContext {
    val androidContext: Context
    fun getRootInActiveWindow(): AccessibilityNodeInfo?
    fun getActivePackageName(): String
    suspend fun dispatchClick(node: AccessibilityNodeInfo): Boolean
    suspend fun dispatchSetText(node: AccessibilityNodeInfo, text: String): Boolean
    suspend fun dispatchScroll(forward: Boolean): Boolean
    suspend fun dispatchGlobalBack(): Boolean
    suspend fun dispatchGlobalHome(): Boolean
    suspend fun launchApp(packageName: String): Boolean
    fun resolvePackageForApp(appName: String): String?
    suspend fun takeScreenCapture(): Bitmap?
}

interface AegisTool {
    val name: String
    val description: String
    val parameterSchema: Map<String, String>
    suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome
}
