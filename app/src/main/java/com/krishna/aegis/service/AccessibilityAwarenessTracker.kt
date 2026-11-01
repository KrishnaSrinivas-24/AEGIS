package com.krishna.aegis.service

import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WindowState(
    val currentPackage: String = "",
    val currentClassName: String = "",
    val lastEventTime: Long = System.currentTimeMillis()
)

class AccessibilityAwarenessTracker {
    private val _windowState = MutableStateFlow(WindowState())
    val windowState: StateFlow<WindowState> = _windowState.asStateFlow()

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: _windowState.value.currentPackage
        val cls = event.className?.toString() ?: _windowState.value.currentClassName

        if (pkg.isNotEmpty() && (pkg != _windowState.value.currentPackage || cls != _windowState.value.currentClassName)) {
            _windowState.value = WindowState(
                currentPackage = pkg,
                currentClassName = cls,
                lastEventTime = System.currentTimeMillis()
            )
        }
    }

    fun getCurrentPackage(): String = _windowState.value.currentPackage
}
