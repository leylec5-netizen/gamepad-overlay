package com.gamepad.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    companion object {
        var isRunning = false
        const val CHANNEL_ID = "gamepad_overlay_channel"
        const val NOTIF_ID   = 1
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var webView: WebView? = null

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
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        webView?.destroy()
        webView = null
    }

    // ─────────────────────────────────────────────
    //  오버레이 WebView 생성
    // ─────────────────────────────────────────────
    private fun showOverlay() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.MATCH_PARENT,
    layoutType,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
    PixelFormat.TRANSLUCENT
)
params.gravity = Gravity.TOP or Gravity.START
params.screenOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // WebView 설정
        val wv = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess   = true
                mediaPlaybackRequiresUserGesture = false
                // 투명 배경
                setBackgroundColor(0x00000000)
            }
            setBackgroundColor(0x00000000)
            isClickable = true
            isFocusable = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()

            // Kotlin ↔ JS 브리지
            addJavascriptInterface(GamePadBridge(this@OverlayService), "AndroidBridge")
        }

        // assets/gamepad.html 로드
        wv.loadUrl("file:///android_asset/gamepad.html")
        webView = wv

        // 터치 처리: 버튼 영역은 WebView가 먹고, 나머지는 아래 앱으로 통과
        wv.setOnTouchListener { v, event ->
            // WebView가 터치를 처리하면 true (소비), 아니면 false (통과)
            v.onTouchEvent(event)
        }

        windowManager.addView(wv, params)
        overlayView = wv
    }

    // ─────────────────────────────────────────────
    //  JS → Kotlin 브리지
    // ─────────────────────────────────────────────
    inner class GamePadBridge(private val ctx: Context) {

        @JavascriptInterface
        fun haptic(type: String) {
            val ms = when (type) {
                "light"  -> 8L
                "medium" -> 20L
                "heavy"  -> 40L
                else     -> 8L
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(ms)
                }
            }
        }

        @JavascriptInterface
        fun stopOverlay() {
            stopSelf()
        }

        @JavascriptInterface
        fun log(msg: String) {
            android.util.Log.d("GamePadOverlay", msg)
        }
    }

    // ─────────────────────────────────────────────
    //  알림 (포그라운드 서비스 유지용)
    // ─────────────────────────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "게임패드 오버레이",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "게임패드를 화면 위에 표시합니다" }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = "STOP"
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎮 게임패드 오버레이 실행 중")
            .setContentText("에뮬레이터 위에 게임패드가 표시되고 있습니다")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "오버레이 중지", stopPending)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") stopSelf()
        return START_STICKY
    }
}
