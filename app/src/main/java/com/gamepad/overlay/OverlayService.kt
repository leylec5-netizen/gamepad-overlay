package com.gamepad.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    companion object {
        var isRunning = false
        const val CHANNEL_ID = "gamepad_overlay_channel"
        const val NOTIF_ID   = 1
    }

    private lateinit var windowManager: WindowManager
    private var gamepadView: GamepadView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        showOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        gamepadView?.let { windowManager.removeView(it) }
        gamepadView = null
    }

    private fun showOverlay() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            // FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL:
            // GamepadView.onTouchEvent이 false 반환하면 터치가 아래 앱으로 통과됨
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        // 가로 고정
        params.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val view = GamepadView(this)

        // 버튼 이벤트 → 필요 시 여기서 Gamepad API나 키 이벤트로 확장 가능
        view.onButtonDown = { id -> android.util.Log.d("GamePad", "DOWN: $id") }
        view.onButtonUp   = { id -> android.util.Log.d("GamePad", "UP:   $id") }

        windowManager.addView(view, params)
        gamepadView = view
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "게임패드 오버레이",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, OverlayService::class.java).apply { action = "STOP" }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPending = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎮 게임패드 오버레이 실행 중")
            .setContentText("에뮬레이터 위에 게임패드가 표시됩니다")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_delete, "중지", stopPending)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") stopSelf()
        return START_STICKY
    }
}
