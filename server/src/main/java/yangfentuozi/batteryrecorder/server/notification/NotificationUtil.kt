package yangfentuozi.batteryrecorder.server.notification

import yangfentuozi.batteryrecorder.shared.data.LineRecord
import java.io.Closeable

interface NotificationUtil: Closeable {
    fun updateNotification(lineRecord: LineRecord)
    override fun close()
}
