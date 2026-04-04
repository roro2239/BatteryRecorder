package yangfentuozi.batteryrecorder.server

import android.ddm.DdmHandleAppName
import androidx.annotation.Keep
import yangfentuozi.batteryrecorder.server.notification.server.NotificationServer
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.File
import java.io.IOException

private const val TAG = "Main"

@Keep
object Main {

    @Keep
    @JvmStatic
    fun main(args: Array<String>) {
        val isNotificationServer = args.size > 1 && args[0] == "--notificationServer"
        DdmHandleAppName.setAppName("batteryrecorder_server", 0)

        // 配置 LoggerX
        LoggerX.logDirPath = "${Constants.SHELL_DATA_DIR_PATH}/${Constants.SHELL_LOG_DIR_PATH}"
        if (isNotificationServer) LoggerX.suffix = "-notificationServer"
        LoggerX.d(TAG, "main: LoggerX 配置完成, dir=${LoggerX.logDirPath}, suffix=${LoggerX.suffix}")

        // 设置OOM保活
        setSelfOomScoreAdj()

        if (isNotificationServer) {
            LoggerX.i(TAG, "main: 初始化 NotificationServer")
            NotificationServer(parentServerPID = args[1].toInt())
        } else {
            LoggerX.i(TAG, "main: 初始化 Server")
            Server()
        }
    }

    private fun setSelfOomScoreAdj() {
        val oomScoreAdjFile = File("/proc/self/oom_score_adj")
        val oomScoreAdjValue = -1000
        try {
            oomScoreAdjFile.writeText("$oomScoreAdjValue\n")
            val actualValue: String = oomScoreAdjFile.readText().trim()
            if (oomScoreAdjValue.toString() != actualValue) {
                LoggerX.e(TAG, 
                    "setSelfOomScoreAdj: 设置 oom_score_adj 失败, expected=$oomScoreAdjValue actual=$actualValue"
                )
                return
            }
            LoggerX.i(TAG, "setSelfOomScoreAdj: 设置 oom_score_adj 成功, actual=$oomScoreAdjValue")
        } catch (e: IOException) {
            LoggerX.e(TAG, "setSelfOomScoreAdj: 设置 oom_score_adj 失败", tr = e)
        } catch (e: RuntimeException) {
            LoggerX.e(TAG, "setSelfOomScoreAdj: 设置 oom_score_adj 失败", tr = e)
        }
    }
}
