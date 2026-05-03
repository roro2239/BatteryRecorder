package yangfentuozi.batteryrecorder.server.util

import android.system.ErrnoException
import android.system.Os
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.File

private const val TAG = "FileUtil"

fun File.changeOwner(uid: Int) {
    try {
        Os.chown(absolutePath, uid, uid)
    } catch (e: ErrnoException) {
        LoggerX.e(
            TAG,
            "changeOwner: 设置文件(夹)所有者和组失败, path=${absolutePath}",
            tr = e
        )
    }
}

fun File.changeOwnerRecursively(uid: Int) {
    changeOwner(uid)
    if (isDirectory()) {
        val files = listFiles()
        if (files != null) {
            for (child in files) {
                child.changeOwnerRecursively(uid)
            }
        }
    }
}