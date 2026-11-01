package com.krishna.aegis.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import com.krishna.aegis.R

class SentinelOverlayManager(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var isShowing = false

    fun canDraw(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun show() {
        if (isShowing || !canDraw()) return

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 350
        }

        val frame = FrameLayout(context).apply {
            val sizePx = (54 * context.resources.displayMetrics.density).toInt()
            this.layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
            // Stark glowing circular background
            val shape = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0xCC0D0F12.toInt())
                setStroke((2 * context.resources.displayMetrics.density).toInt(), 0xFF00E5FF.toInt())
            }
            background = shape

            val icon = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_btn_speak_now)
                setColorFilter(0xFF00E5FF.toInt())
                val padding = (12 * context.resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
            }
            addView(icon)
        }

        // Draggable touch listener
        frame.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isClick = false
                        }
                        layoutParams.x = initialX + dx
                        layoutParams.y = initialY + dy
                        windowManager.updateViewLayout(frame, layoutParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            onOverlayTapped()
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager.addView(frame, layoutParams)
            overlayView = frame
            isShowing = true
        } catch (_: Exception) {}
    }

    private fun onOverlayTapped() {
        Toast.makeText(context, "AEGIS: Listening...", Toast.LENGTH_SHORT).show()
        AegisAccessibilityService.instance?.let { service ->
            service.startAndroidCommandCapture()
        }
    }

    fun hide() {
        if (!isShowing) return
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        overlayView = null
        isShowing = false
    }
}
