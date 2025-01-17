package com.simplemobiletools.clock.services

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.simplemobiletools.clock.R
import com.simplemobiletools.clock.extensions.getFormattedDuration
import com.simplemobiletools.clock.extensions.getOpenTimerTabIntent
import com.simplemobiletools.clock.extensions.timerHelper
import com.simplemobiletools.clock.helpers.INVALID_TIMER_ID
import com.simplemobiletools.clock.helpers.TIMER_RUNNING_NOTIF_ID
import com.simplemobiletools.clock.models.TimerEvent
import com.simplemobiletools.clock.models.TimerState
import com.simplemobiletools.commons.helpers.isOreoPlus
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class TimerService : Service() {
    private val bus = EventBus.getDefault()
    private var isStopping = false

    override fun onCreate() {
        super.onCreate()
        bus.register(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        isStopping = false
        updateNotification()
        startForeground(TIMER_RUNNING_NOTIF_ID, notification(getString(R.string.app_name), getString(R.string.timers_notification_msg), INVALID_TIMER_ID))
        return START_NOT_STICKY
    }

    private fun updateNotification() {
        timerHelper.getTimers { timers ->
            val runningTimers = timers.filter { it.state is TimerState.Running }
            if (runningTimers.isNotEmpty()) {
                val firstTimer = runningTimers.first()
                val formattedDuration = (firstTimer.state as TimerState.Running).tick.getFormattedDuration()
                val contextText = when {
                    firstTimer.label.isNotEmpty() -> getString(R.string.timer_single_notification_label_msg, firstTimer.label)
                    else -> resources.getQuantityString(R.plurals.timer_notification_msg, runningTimers.size, runningTimers.size)
                }
                startForeground(TIMER_RUNNING_NOTIF_ID, notification(formattedDuration, contextText, firstTimer.id!!))
            } else {
                stopService()
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: TimerStopService) {
        stopService()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: TimerEvent.Refresh) {
        if(!isStopping){
            updateNotification()
        }
    }

    private fun stopService() {
        isStopping = true
        if (isOreoPlus()) {
            stopForeground(true)
        } else {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bus.unregister(this)
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun notification(title: String, contentText: String, firstRunningTimerId: Int): Notification {
        val channelId = "simple_alarm_timer"
        val label = getString(R.string.timer)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (isOreoPlus()) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            NotificationChannel(channelId, label, importance).apply {
                setSound(null, null)
                notificationManager.createNotificationChannel(this)
            }
        }

        val builder = NotificationCompat.Builder(this)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_timer)
            .setPriority(Notification.PRIORITY_DEFAULT)
            .setSound(null)
            .setOngoing(true)
            .setAutoCancel(true)
            .setChannelId(channelId)

        if (firstRunningTimerId != INVALID_TIMER_ID) {
            builder.setContentIntent(this.getOpenTimerTabIntent(firstRunningTimerId))
        }

        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        return builder.build()
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun startTimerService(context: Context) {
    if (isOreoPlus()) {
        context.startForegroundService(Intent(context, TimerService::class.java))
    } else {
        context.startService(Intent(context, TimerService::class.java))
    }
}

object TimerStopService
