package com.timemanager.android.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.timemanager.android.R

/**
 * SYSTEM_ALERT_WINDOW overlay shown when a limit is reached.
 * Honest best-effort blocking — user can dismiss via Home, but we log enforcement.
 */
class BlockOverlayService : Service() {
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ruleName = intent?.getStringExtra("ruleName") ?: "Limit reached"
        val target = intent?.getStringExtra("ruleTarget") ?: ""
        showOverlay(ruleName, target)
        return START_NOT_STICKY
    }

    private fun showOverlay(ruleName: String, target: String) {
        if (overlayView != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48,48,48,48)
            setBackgroundColor(0xEE1A1A1A.toInt())
            gravity = android.view.Gravity.CENTER
        }
        val title = TextView(this).apply {
            text = "⏰ Time's up"
            textSize = 28f; setTextColor(0xFFFFFFFF.toInt()); gravity = android.view.Gravity.CENTER
            setPadding(0,0,0,24)
        }
        val body = TextView(this).apply {
            text = "\"$ruleName\" ($target) has reached its limit.\nTake a break — this app is blocked until midnight."
            textSize = 16f; setTextColor(0xFFFFFFFF.toInt()); gravity = android.view.Gravity.CENTER
        }
        val btn = Button(this).apply {
            text = "Go Home"
            setOnClickListener { dismiss() }
        }
        val dismissBtn = Button(this).apply {
            text = "Dismiss (5s)"
            isEnabled = false
            postDelayed({ isEnabled = true; text = "Dismiss" }, 5000)
            setOnClickListener { dismiss() }
        }
        layout.addView(title); layout.addView(body); layout.addView(btn); layout.addView(dismissBtn)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        overlayView = layout
        windowManager?.addView(overlayView, params)
    }

    private fun dismiss() {
        try { overlayView?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        overlayView = null; stopSelf()
        // navigate home already done by executor
    }

    override fun onDestroy() {
        try { overlayView?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        super.onDestroy()
    }
}
