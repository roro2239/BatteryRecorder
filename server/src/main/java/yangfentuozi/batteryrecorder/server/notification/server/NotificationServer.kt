package yangfentuozi.batteryrecorder.server.notification.server

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.Looper
import android.os.Process
import android.system.Os
import yangfentuozi.batteryrecorder.server.notification.LocalNotificationUtil
import yangfentuozi.batteryrecorder.server.notification.NotificationUtil
import yangfentuozi.batteryrecorder.server.notification.stream.StreamProtocol
import yangfentuozi.batteryrecorder.server.notification.stream.StreamReader
import yangfentuozi.batteryrecorder.shared.util.Handlers
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.hiddenapi.compat.ServiceManagerCompat
import java.io.IOException
import kotlin.system.exitProcess

private const val TAG = "NotificationServer"

class NotificationServer(
    val parentServerPID: Int
) {
    @Volatile
    var isStopped = false

    @Volatile
    private var cleanedUp = false

    val notificationUtil: NotificationUtil
    var socket: LocalSocket? = null
    var reader: StreamReader? = null
    val serverSocket: LocalServerSocket
    val serverHandler = Handlers.getHandler("ServerSocketThread")
    val serverRunnable = Runnable {
        try {
            LoggerX.i(TAG, "@serverRunnable: 等待客户端")
            socket = serverSocket.accept()
            LoggerX.i(TAG, "@serverRunnable: 接受客户端")
            reader = StreamReader(socket!!.inputStream)
            while (true) {
                val info = reader!!.readNext() ?: break
                notificationUtil.updateNotification(info)
            }
        } catch (e: IOException) {
            if (!isStopped) LoggerX.e(TAG, "@serverRunnable: 处理客户端请求时出现异常", tr = e)
        } catch (_: StreamProtocol.StopException) {
            isStopped = true
            Handlers.main.post { exitProcess(0) }
        } finally {
            reader?.let { runCatching { it.close() } }
            socket?.let { runCatching { it.close() } }
        }
        reader = null
        socket = null
        notificationUtil.close()
        exitProcess(0)
    }

    init {
        LoggerX.i(
            TAG,
            "init: NotificationServer 初始化开始, uid=${Os.getuid()}, parentServerPID=$parentServerPID"
        )
        if (Looper.getMainLooper() == null) {
            @Suppress("DEPRECATION")
            Looper.prepareMainLooper()
        }
        Handlers.initMainThread()

        if (Os.getuid() != 2000) {
            LoggerX.i(TAG, "init: uid 不为 2000, 执行降权")
            val groups = arrayOf(
                "input",
                "log",
                "adb",
                "sdcard_rw",
                "sdcard_r",
                "ext_data_rw",
                "ext_obb_rw",
                "net_bt_admin",
                "net_bt",
                "inet",
                "net_bw_stats",
                "readproc",
                "uhid",
                "readtracefs"
            )
            for (group in groups) {
                val groupId = Process.getGidForName(group)
                if (groupId != -1) {
                    @Suppress("DEPRECATION")
                    Os.setgid(groupId)
                }
            }
            @Suppress("DEPRECATION")
            Os.setuid(2000)
        }

        LoggerX.i(TAG, "init: 等待 notification 服务")
        ServiceManagerCompat.waitService("notification")
        notificationUtil = LocalNotificationUtil()

        LoggerX.i(TAG, "init: 创建 LocalServerSocket 通信服务")
        serverSocket = LocalServerSocket(SOCKET_NAME)

        Runtime.getRuntime().addShutdownHook(Thread { this.stopServiceImmediately() })
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            LoggerX.a(thread.name, "NotificationServer crashed", tr = throwable)
            LoggerX.writer?.close()
        }

        startServer()
        Looper.loop()
    }

    fun startServer() {
        serverHandler.post(serverRunnable)
    }

    private fun stopServiceImmediately() {
        if (cleanedUp) return
        LoggerX.i(TAG, "停止服务")
        cleanedUp = true
        isStopped = true
        serverHandler.removeCallbacks(serverRunnable)
        runCatching {
            reader?.let { runCatching { it.close() } }
            socket?.let { runCatching { it.close() } }
            notificationUtil.close()
            Handlers.interruptAll()
            serverSocket.close()
        }
    }

    companion object {
        const val SOCKET_NAME = "BatteryRecorder_NotificationServer"
    }
}
