package yangfentuozi.batteryrecorder.server.notification

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.system.Os
import yangfentuozi.batteryrecorder.server.Global
import yangfentuozi.batteryrecorder.server.Main
import yangfentuozi.batteryrecorder.server.notification.server.NotificationServer
import yangfentuozi.batteryrecorder.server.notification.stream.LineRecordWriter
import yangfentuozi.batteryrecorder.shared.data.LineRecord
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.IOException

private const val TAG = "RemoteNotificationUtil"

class RemoteNotificationUtil: NotificationUtil {
    @Volatile
    private var closed = false
    private var tryConnecting = true

    private lateinit var process: Process
    private lateinit var socket: LocalSocket
    private lateinit var writer: LineRecordWriter

    private val lock = Any()

    init {
        synchronized(lock) {
            startChildServer()
        }
    }

    override fun updateNotification(lineRecord: LineRecord) {
        synchronized(lock) {
            if (tryConnecting || closed) return
            try {
                writer.write(lineRecord)
            } catch (e: IOException) {
                LoggerX.w(TAG, "updateNotification: 写入失败, 尝试重建连接", tr = e)
                closeConnection()
                startChildServer()
                writer.write(lineRecord)
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true

            closeConnection()
        }
    }

    private fun startChildServer() {
        tryConnecting = true
        process = ProcessBuilder(
            "app_process",
            "-Djava.class.path=${Global.appSourceDir}",
            "/system/bin",
            Main::class.java.name,
            "--notificationServer",
            Os.getpid().toString()
        ).start()

        LoggerX.i(TAG, "startChildServer: 子 Server 已启动, process=$process")

        var lastError: IOException? = null

        run {
            repeat(50) { index ->
                if (!process.isAlive) {
                    throw IOException(
                        "NotificationServer 在建连前已退出, exitValue=${runCatching { process.exitValue() }.getOrNull()}",
                        lastError
                    )
                }

                socket = LocalSocket()
                try {
                    socket.connect(LocalSocketAddress(NotificationServer.SOCKET_NAME))
                    LoggerX.i(TAG, "connectSocket: 已连接 NotificationServer, attempt=${index + 1}")
                    return@run
                } catch (e: IOException) {
                    lastError = e
                    runCatching { socket.close() }
                    if (index < 50 - 1) {
                        Thread.sleep(100)
                    }
                }
            }
        }

        if (!socket.isConnected) {
            process.destroyForcibly()
            throw IOException("连接子 Server 失败", lastError)
        }

        writer = LineRecordWriter(socket.outputStream)
        tryConnecting = false
    }

    private fun closeConnection() {
        runCatching { writer.writeClose() }.onFailure {
            LoggerX.w(TAG, "closeConnection: 发送停止信号失败", tr = it)
        }
        Thread.sleep(100)

        runCatching { writer.close() }.onFailure {
            LoggerX.w(TAG, "closeConnection: 关闭 writer 失败", tr = it)
        }
        Thread.sleep(100)

        runCatching { socket.close() }.onFailure {
            LoggerX.w(TAG, "closeConnection: 关闭 socket 失败", tr = it)
        }
        Thread.sleep(100)

        runCatching {
            if (process.isAlive) process.destroyForcibly()
        }.onFailure {
            LoggerX.w(TAG, "closeConnection: 关闭子进程失败", tr = it)
        }
    }
}
