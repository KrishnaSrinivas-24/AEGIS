package com.krishna.aegis.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume

object ScreenCapture {

    suspend fun capture(service: AccessibilityService): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null
        }

        return suspendCancellableCoroutine { cont ->
            val executor = Executor { command -> command.run() }
            val callback = object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val hardwareBitmap = Bitmap.wrapHardwareBuffer(
                        screenshot.hardwareBuffer,
                        screenshot.colorSpace
                    )
                    screenshot.hardwareBuffer.close()
                    val softwareBitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    if (cont.isActive) {
                        cont.resume(softwareBitmap)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    if (cont.isActive) {
                        cont.resume(null)
                    }
                }
            }

            try {
                service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback)
            } catch (e: Exception) {
                if (cont.isActive) {
                    cont.resume(null)
                }
            }
        }
    }
}
