package com.haydaybackup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

class MainActivity : AppCompatActivity() {

    // ── Views ─────────────────────────────────────────────────────────────────
    private lateinit var tvShizukuStatus: MaterialTextView
    private lateinit var btnRequestShizuku: MaterialButton
    private lateinit var btnOverlayPermission: MaterialButton
    private lateinit var btnToggleFloat: MaterialButton
    private lateinit var tvLastBackup: MaterialTextView

    private var isFloatingActive = false

    // ── Shizuku listener ──────────────────────────────────────────────────────
    private val shizukuBinderListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread { refreshShizukuStatus() }
    }
    private val shizukuDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread { refreshShizukuStatus() }
    }
    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            runOnUiThread { refreshShizukuStatus() }
        }

    // ── Overlay permission launcher ───────────────────────────────────────────
    private val overlayLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshOverlayStatus()
        }

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvShizukuStatus = findViewById(R.id.tvShizukuStatus)
        btnRequestShizuku = findViewById(R.id.btnRequestShizuku)
        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        btnToggleFloat = findViewById(R.id.btnToggleFloat)
        tvLastBackup = findViewById(R.id.tvLastBackup)

        Shizuku.addBinderReceivedListenerSticky(shizukuBinderListener)
        Shizuku.addBinderDeadListener(shizukuDeadListener)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        btnRequestShizuku.setOnClickListener { requestShizukuPermission() }

        btnOverlayPermission.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayLauncher.launch(intent)
        }

        btnToggleFloat.setOnClickListener { toggleFloatingButton() }

        refreshShizukuStatus()
        refreshOverlayStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshShizukuStatus()
        refreshOverlayStatus()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(shizukuBinderListener)
        Shizuku.removeBinderDeadListener(shizukuDeadListener)
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
    }

    // ── Status helpers ────────────────────────────────────────────────────────

    private fun refreshShizukuStatus() {
        val binderAlive = Shizuku.pingBinder()
        when {
            !binderAlive -> {
                tvShizukuStatus.text = getString(R.string.shizuku_status_not_running)
                tvShizukuStatus.setTextColor(getColor(android.R.color.holo_red_light))
                btnRequestShizuku.visibility = View.GONE
            }
            !hasShizukuPermission() -> {
                tvShizukuStatus.text = getString(R.string.shizuku_status_no_permission)
                tvShizukuStatus.setTextColor(getColor(android.R.color.holo_orange_light))
                btnRequestShizuku.visibility = View.VISIBLE
            }
            else -> {
                tvShizukuStatus.text = getString(R.string.shizuku_status_ok)
                tvShizukuStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                btnRequestShizuku.visibility = View.GONE
            }
        }
        updateFloatButtonEnabled()
    }

    private fun refreshOverlayStatus() {
        if (Settings.canDrawOverlays(this)) {
            btnOverlayPermission.visibility = View.GONE
        } else {
            btnOverlayPermission.visibility = View.VISIBLE
            // If service is running but permission revoked, stop it
            if (isFloatingActive) stopFloatingService()
        }
        updateFloatButtonEnabled()
    }

    private fun updateFloatButtonEnabled() {
        val ready = hasShizukuPermission() && Settings.canDrawOverlays(this)
        btnToggleFloat.isEnabled = ready
        btnToggleFloat.text = if (isFloatingActive)
            getString(R.string.stop_floating_button)
        else
            getString(R.string.start_floating_button)
    }

    // ── Shizuku permission ────────────────────────────────────────────────────

    private fun hasShizukuPermission(): Boolean {
        return try {
            if (Shizuku.isPreV11()) {
                // Shizuku < v11: check self permission via ShizukuProvider
                checkSelfPermission(ShizukuProvider.PERMISSION) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                Shizuku.checkSelfPermission() ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        } catch (e: IllegalStateException) {
            false
        }
    }

    private fun requestShizukuPermission() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, getString(R.string.shizuku_not_running), Toast.LENGTH_LONG).show()
            return
        }
        try {
            if (Shizuku.isPreV11()) {
                requestPermissions(arrayOf(ShizukuProvider.PERMISSION), 100)
            } else {
                Shizuku.requestPermission(100)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error requesting permission: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Floating button toggle ────────────────────────────────────────────────

    private fun toggleFloatingButton() {
        if (isFloatingActive) {
            stopFloatingService()
        } else {
            startFloatingService()
        }
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingButtonService::class.java)
        startForegroundService(intent)
        isFloatingActive = true
        updateFloatButtonEnabled()
        Toast.makeText(this, "Floating button activated", Toast.LENGTH_SHORT).show()
    }

    private fun stopFloatingService() {
        val intent = Intent(this, FloatingButtonService::class.java).apply {
            action = FloatingButtonService.ACTION_STOP
        }
        startService(intent)
        isFloatingActive = false
        updateFloatButtonEnabled()
    }
}
