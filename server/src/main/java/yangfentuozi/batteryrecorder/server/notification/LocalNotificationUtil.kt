package yangfentuozi.batteryrecorder.server.notification

import android.app.INotificationManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.RemoteException
import android.os.ServiceManager
import yangfentuozi.batteryrecorder.server.fakecontext.FakeContext
import yangfentuozi.batteryrecorder.shared.data.LineRecord
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.hiddenapi.compat.NotificationManagerCompat

private const val TAG = "LocalNotificationUtil"

class LocalNotificationUtil : NotificationUtil {
    private val lock = Any()
    private val notificationManager: INotificationManager =
        INotificationManager.Stub.asInterface(ServiceManager.getService("notification"))
            ?: throw IllegalStateException("notification 服务未就绪")
    private val context = FakeContext()

    @Volatile
    private var channelCreated = false

    init {
        synchronized(lock) {
            if (channelCreated) return@synchronized
            try {
                NotificationManagerCompat.createChannel(
                    notificationManager,
                    SHELL_PACKAGE_NAME,
                    SHELL_UID,
                    NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        enableLights(false)
                        enableVibration(false)
                    }
                )
                channelCreated = true
                LoggerX.i(TAG, "init: 通知渠道创建成功")
            } catch (e: RemoteException) {
                LoggerX.e(TAG, "init: 通知渠道创建失败", tr = e)
            }
        }
    }

    override fun updateNotification(lineRecord: LineRecord) {
        synchronized(lock) {
            try {
                NotificationManagerCompat.enqueueNotification(
                    notificationManager,
                    SHELL_PACKAGE_NAME,
                    NOTIFICATION_TAG,
                    NOTIFICATION_ID,
                    buildNotification(lineRecord),
                    0
                )
            } catch (e: RemoteException) {
                LoggerX.e(TAG, "updateNotification: 发送通知失败", tr = e)
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            NotificationManagerCompat.cancelNotification(
                notificationManager,
                SHELL_PACKAGE_NAME,
                NOTIFICATION_TAG,
                NOTIFICATION_ID,
                0
            )
        }
    }

    private fun buildNotification(lineRecord: LineRecord): Notification {
        val contentText = buildContentText(lineRecord)
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(Icon.createWithResource("android", android.R.drawable.stat_notify_sync))
            .setContentTitle(NOTIFICATION_TITLE)
            .setContentText(contentText)
            .setTicker(contentText)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
            .apply {
                contentView = null
                bigContentView = null
                headsUpContentView = null
                color = Color.TRANSPARENT
            }
    }

    private fun buildContentText(lineRecord: LineRecord): String {
        val appName = lineRecord.packageName?.takeIf { it.isNotBlank() } ?: "无"
        return "功耗 ${lineRecord.power} | 电量 ${lineRecord.capacity}% | 前台应用 $appName"
    }

    companion object {
        private const val SHELL_PACKAGE_NAME = "com.android.shell"
        private const val SHELL_UID = 2000
        private const val CHANNEL_ID = "batteryrecorder_notification"
        private const val CHANNEL_NAME = "BatteryRecorder"
        private const val NOTIFICATION_TAG = "batteryrecorder_notification"
        private const val NOTIFICATION_ID = 10086
        private const val NOTIFICATION_TITLE = "BatteryRecorder"
    }

}

