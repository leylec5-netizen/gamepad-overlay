package com.gamepad.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
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
        const val NOTIF_ID = 1
    }

    private lateinit var windowManager: WindowManager
    private var webView: WebView? = null

    // JS에서 보고한 버튼 영역 목록 (화면 좌표)
    private val buttonRects = mutableListOf<Rect>()

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
        webView?.let { windowManager.removeView(it) }
        webView?.destroy()
        webView = null
    }

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

        val wv = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                setBackgroundColor(0x00000000)
            }
            setBackgroundColor(0x00000000)
            isClickable = true
            isFocusable = false  // 포커스 없애야 아래 앱이 키 입력 받음
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            addJavascriptInterface(GamePadBridge(this@OverlayService), "AndroidBridge")
        }

        // ── 핵심: 터치가 버튼 위면 WebView가 처리, 아니면 아래 앱으로 통과 ──
        wv.setOnTouchListener { v, event ->
            val x = event.rawX.toInt()
            val y = event.rawY.toInt()
            val onButton = buttonRects.any { it.contains(x, y) }
            if (onButton) {
                v.onTouchEvent(event)  // 버튼 터치 → WebView 처리
                true
            } else {
                false  // 빈 영역 터치 → 아래 앱으로 통과
            }
        }

        wv.loadUrl("file:///android_asset/gamepad.html")
        webView = wv
        windowManager.addView(wv, params)
    }

    // ── JS → Kotlin 브리지 ──
    inner class GamePadBridge(private val ctx: Context) {

        // JS에서 버튼 위치를 보고 (페이지 로드 후 + 화면 회전 시 호출)
        @JavascriptInterface
        fun reportButtonRects(json: String) {
            // json 형식: [{"l":10,"t":20,"r":60,"b":70}, ...]
            buttonRects.clear()
            try {
                val arr = org.json.JSONArray(json)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    buttonRects.add(Rect(
                        o.getInt("l"), o.getInt("t"),
                        o.getInt("r"), o.getInt("b")
                    ))
                }
            } catch (e: Exception) {
                android.util.Log.e("GamePadOverlay", "rect parse error: $e")
            }
        }

        @JavascriptInterface
        fun haptic(type: String) {
            val ms = when (type) { "light" -> 8L; "medium" -> 20L; "heavy" -> 40L; else -> 8L }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                else
                    @Suppress("DEPRECATION")
                    v.vibrate(ms)
            }
        }

        @JavascriptInterface
        fun stopOverlay() { stopSelf() }

        @JavascriptInterface
        fun log(msg: String) { android.util.Log.d("GamePadOverlay", msg) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "게임패드 오버레이", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val stopPending = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).apply { action = "STOP" },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎮 게임패드 오버레이 실행 중")
            .setContentText("에뮬레이터 위에 게임패드가 표시됩니다")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .addAction(android.R.drawable.ic_delete, "오버레이 중지", stopPending)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") stopSelf()
        return START_STICKY
    }
}
