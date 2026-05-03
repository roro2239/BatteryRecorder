package yangfentuozi.batteryrecorder.server

import android.content.pm.ApplicationInfo
import android.os.FileObserver
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.hiddenapi.compat.PackageManagerCompat
import java.io.File

class AppReinstallObserver(
    private val apkFile: File,
    private val onAppReinstall: (ApplicationInfo?) -> Unit
) : FileObserver(
    apkFile, MODIFY or ATTRIB or CLOSE_WRITE
            or CLOSE_NOWRITE or OPEN or MOVED_FROM or MOVED_TO or DELETE or CREATE
            or DELETE_SELF or MOVE_SELF
) {

    override fun onEvent(event: Int, path: String?) {
        check()
    }

    fun check() {
        val appInfo = runCatching {
            PackageManagerCompat.getApplicationInfo(
                Constants.APP_PACKAGE_NAME,
                0L,
                0
            )
        }.getOrNull()
        if (appInfo?.sourceDir != apkFile.absolutePath || !apkFile.exists()) {
            stopWatching()
            onAppReinstall(appInfo)
        }
    }
}
