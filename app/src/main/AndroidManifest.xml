package com.gamepad.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.graphics.PixelFormat
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    companion object {
        var isRunning = false
        const val CHANNEL_ID = "gamepad_overlay_channel"
        const val NOTIF_ID = 1
    }

    private lateinit var wm: WindowManager
    private val overlayViews = mutableListOf<View>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        rebuildButtons()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        clearButtons()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rebuildButtons()
    }

    private fun clearButtons() {
        overlayViews.forEach { runCatching { wm.removeView(it) } }
        overlayViews.clear()
    }

    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val m = wm.currentWindowMetrics
            Pair(m.bounds.width(), m.bounds.height())
        } else {
            val d = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(d)
            Pair(d.widthPixels, d.heightPixels)
        }
    }

    data class BtnDef(
        val label: String,
        val color: Int,
        val isCircle: Boolean,
        val action: String,
        val xFrac: Float, val yFrac: Float,
        val wFrac: Float, val hFrac: Float
    )

    private fun buildDefs(): List<BtnDef> = listOf(
        BtnDef("L1", 0xFF37474F.toInt(), false, "L1",   0.07f, 0.18f, 0.11f, 0.28f),
        BtnDef("L2", 0xFF263238.toInt(), false, "L2",   0.07f, 0.52f, 0.11f, 0.28f),
        BtnDef("R1", 0xFF37474F.toInt(), false, "R1",   0.93f, 0.18f, 0.11f, 0.28f),
        BtnDef("R2", 0xFF263238.toInt(), false, "R2",   0.93f, 0.52f, 0.11f, 0.28f),
        BtnDef("▲", 0xFF455A64.toInt(), false, "UP",    0.14f, 0.30f, 0.09f, 0.20f),
        BtnDef("▼", 0xFF455A64.toInt(), false, "DOWN",  0.14f, 0.68f, 0.09f, 0.20f),
        BtnDef("◀", 0xFF455A64.toInt(), false, "LEFT",  0.08f, 0.49f, 0.09f, 0.20f),
        BtnDef("▶", 0xFF455A64.toInt(), false, "RIGHT", 0.20f, 0.49f, 0.09f, 0.20f),
        BtnDef("Y", 0xFFF9A825.toInt(), true,  "Y",     0.86f, 0.28f, 0.11f, 0.24f),
        BtnDef("A", 0xFF2E7D32.toInt(), true,  "A",     0.86f, 0.70f, 0.11f, 0.24f),
        BtnDef("X", 0xFF1565C0.toInt(), true,  "X",     0.80f, 0.49f, 0.11f, 0.24f),
        BtnDef("B", 0xFFC62828.toInt(), true,  "B",     0.92f, 0.49f, 0.11f, 0.24f),
        BtnDef("◀◀", 0xFF37474F.toInt(), false, "SEL",  0.43f, 0.88f, 0.08f, 0.16f),
        BtnDef("⊙",  0xFF455A64.toInt(), true,  "HOME", 0.50f, 0.88f, 0.08f, 0.18f),
        BtnDef("▶▶", 0xFF37474F.toInt(), false, "STA",  0.57f, 0.88f, 0.08f, 0.16f),
        BtnDef("", 0xFF1C2833.toInt(), true, "LS",      0.25f, 0.75f, 0.24f, 0.52f),
        BtnDef("", 0xFF1C2833.toInt(), true, "RS",      0.75f, 0.75f, 0.24f, 0.52f),
    )

    private fun rebuildButtons() {
        clearButtons()
        val (W, H) = screenSize()
        val lW = maxOf(W, H)
        val lH = minOf(W, H)
        buildDefs().forEach { def ->
            val bw = (lW * def.wFrac).toInt()
            val bh = (lH * def.hFrac).toInt()
            val bx = (lW * def.xFrac - bw / 2f).toInt().coerceIn(0, lW - bw)
            val by = (lH * def.yFrac - bh / 2f).toInt().coerceIn(0, lH - bh)
            addButtonWindow(def, bx, by, bw, bh)
        }
    }

    private fun addButtonWin
