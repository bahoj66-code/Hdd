package com.haydaybackup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingButtonService : Service() {

    companion object {
        private const val CHANNEL_ID = "floating_backup_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.haydaybackup.STOP_SERVICE"
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isBackingUp = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        addFloatingView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    private fun addFloatingView() {
        val inflater = LayoutInflater.from(this)

        // Root container (FAB + progress indicator)
        val container = android.widget.FrameLayout(this)

        val fab = FloatingActionButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_save)
            size = FloatingActionButton.SIZE_NORMAL
            contentDescription = "Backup Hay Day"
        }

        val progress = CircularProgressIndicator(this).apply {
            isIndeterminate = true
            visibility = View.GONE
            trackThickness = 4
        }

        val fabSize = resources.getDimensionPixelSize(
            com.google.android.material.R.dimen.design_fab_size_normal
        )
        val containerSize = fabSize + 16

        val fabParams = android.widget.FrameLayout.LayoutParams(fabSize, fabSize).apply {
            gravity = Gravity.CENTER
        }
        val progressParams = android.widget.FrameLayout.LayoutParams(containerSize, containerSize).apply {
            gravity = Gravity.CENTER
        }

        container.addView(fab, fabParams)
        container.addView(progress, progressParams)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        // Drag and click logic
        var isDragging = false
        container.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) isDragging = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(container, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // Treat as click → trigger backup
                        triggerBackup(fab, progress)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(container, params)
        floatingView = container
    }

    private fun triggerBackup(fab: FloatingActionButton, progress: CircularProgressIndicator) {
        if (isBackingUp) {
            Toast.makeText(this, "Backup already in progress…", Toast.LENGTH_SHORT).show()
            return
        }
        isBackingUp = true
        fab.isEnabled = false
        progress.visibility = View.VISIBLE

        serviceScope.launch {
            val result = BackupManager.backup()
            progress.visibility = View.GONE
            fab.isEnabled = true
            isBackingUp = false

            when (result) {
                is BackupManager.BackupResult.Success ->
                    Toast.makeText(
                        this@FloatingButtonService,
                        "✅ Backup saved to:\n${result.destPath}",
                        Toast.LENGTH_LONG
                    ).show()

                is BackupManager.BackupResult.Failure ->
                    Toast.makeText(
                        this@FloatingButtonService,
                        "❌ Backup failed:\n${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        floatingView?.let { windowManager.removeView(it) }
        floatingView = null
        super.onDestroy()
    }

    // ── Notification ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_desc)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, FloatingButtonService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }
}
