package com.smsexpense.tracker.service.quicklaunch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.smsexpense.tracker.MainActivity
import com.smsexpense.tracker.R
import com.smsexpense.tracker.appContainer
import com.smsexpense.tracker.service.panel.QuickPanelService
import com.smsexpense.tracker.util.AppLog
import kotlinx.coroutines.launch

/**
 * Listens to the accelerometer and opens the quick panel on a triple back tap.
 *
 * A foreground service is mandatory: since Android 9 an app in the background
 * receives no sensor events at all, so this cannot be done invisibly. That is
 * also why the feature is opt-in and off by default — it costs a permanent
 * notification and real battery.
 */
class BackTapService : LifecycleService(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private val detector = BackTapDetector()

    override fun onCreate() {
        super.onCreate()
        createChannel()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        lifecycleScope.launch {
            appContainer().settingsRepository.backTapSensitivity.collect { name ->
                detector.setSensitivity(BackTapSensitivity.fromName(name))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val sensor = accelerometer
        if (sensor == null) {
            AppLog.w("No accelerometer on this device; back tap unavailable")
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (e: Exception) {
            AppLog.e("Back tap service could not go foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }
        // GAME rate (~50 Hz) is the slowest that still resolves a tap reliably.
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val fired = detector.onSample(
            event.timestamp, event.values[0], event.values[1], event.values[2],
        )
        if (fired) {
            AppLog.d("Back tap detected")
            // Legal from here: an app running a foreground service may start another.
            runCatching {
                startForegroundService(Intent(this, QuickPanelService::class.java))
            }.onFailure { AppLog.e("Could not open quick panel", it) }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Back tap active")
            .setContentText("Tap the back of your phone 3 times to open quick actions")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Back tap", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Required by Android to read motion sensors in the background"
            }
        )
    }

    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "back_tap"

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, BackTapService::class.java))
            }.onFailure { AppLog.e("Could not start back tap service", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BackTapService::class.java))
        }
    }
}
