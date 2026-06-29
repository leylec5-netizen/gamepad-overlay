package com.gamepad.overlay

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gamepad.overlay.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val OVERLAY_PERMISSION_REQ = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateUI()

        // 오버레이 시작 버튼
        binding.btnStart.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                startOverlayService()
            } else {
                requestOverlayPermission()
            }
        }

        // 오버레이 중지 버튼
        binding.btnStop.setOnClickListener {
            stopOverlayService()
        }

        // 권한 설정으로 이동
        binding.btnPermission.setOnClickListener {
            requestOverlayPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val hasPermission = Settings.canDrawOverlays(this)
        val isRunning = OverlayService.isRunning

        binding.tvStatus.text = when {
            !hasPermission -> "⚠️ 오버레이 권한 필요"
            isRunning      -> "✅ 게임패드 실행 중"
            else           -> "준비됨 — 시작 버튼을 눌러주세요"
        }

        binding.btnPermission.isEnabled = !hasPermission
        binding.btnStart.isEnabled = hasPermission && !isRunning
        binding.btnStop.isEnabled = isRunning
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, OVERLAY_PERMISSION_REQ)
        Toast.makeText(this, "\"GamePad Overlay\"의 다른 앱 위에 표시 권한을 허용해주세요", Toast.LENGTH_LONG).show()
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateUI()
        Toast.makeText(this, "게임패드 오버레이 시작! 에뮬레이터로 이동하세요 🎮", Toast.LENGTH_SHORT).show()
        // 홈으로 보내서 에뮬레이터 앱을 앞에 띄울 수 있게
        val home = Intent(Intent.ACTION_MAIN)
        home.addCategory(Intent.CATEGORY_HOME)
        startActivity(home)
    }

    private fun stopOverlayService() {
        stopService(Intent(this, OverlayService::class.java))
        updateUI()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQ) {
            updateUI()
        }
    }
}
