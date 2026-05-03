package yangfentuozi.batteryrecorder.server

import androidx.annotation.Keep
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.hiddenapi.compat.ActivityManagerCompat
import yangfentuozi.hiddenapi.compat.ActivityManagerCompat.UID_OBSERVER_ACTIVE
import yangfentuozi.hiddenapi.compat.ActivityManagerCompat.UID_OBSERVER_CACHED
import yangfentuozi.hiddenapi.compat.ActivityManagerCompat.UID_OBSERVER_GONE
import yangfentuozi.hiddenapi.compat.ActivityManagerCompat.UID_OBSERVER_IDLE
import yangfentuozi.hiddenapi.compat.ProcessObserverAdapter
import yangfentuozi.hiddenapi.compat.UidObserverAdapter

private const val BinderSenderTAG = "BinderSender"
private const val UidObserverTAG = "UidObserver"

@Keep
class BinderSender(
    private val appUid: Int,
    private val sendBinder: () -> Unit
) {
    init {
        try {
            ActivityManagerCompat.registerProcessObserver(ProcessObserver())
        } catch (tr: Throwable) {
            LoggerX.e(BinderSenderTAG, "registerProcessObserver", tr = tr)
        }

        val flags: Int =
            UID_OBSERVER_GONE or UID_OBSERVER_IDLE or UID_OBSERVER_ACTIVE or UID_OBSERVER_CACHED
        try {
            ActivityManagerCompat.registerUidObserver(
                UidObserver(), flags, ActivityManagerCompat.PROCESS_STATE_UNKNOWN, null
            )
        } catch (tr: Throwable) {
            LoggerX.e(BinderSenderTAG, "registerUidObserver", tr = tr)
        }
        sendBinder()
    }

    inner class ProcessObserver : ProcessObserverAdapter() {
        private val tag = "ProcessObserver"

        override fun onForegroundActivitiesChanged(
            pid: Int, uid: Int, foregroundActivities: Boolean
        ) {
            LoggerX.d(
                tag,
                "onForegroundActivitiesChanged: pid=$pid, uid=$uid, foregroundActivities=$foregroundActivities"
            )

            if (uid == appUid) sendBinder()
        }

        override fun onProcessDied(pid: Int, uid: Int) {
            LoggerX.d(tag, "onProcessDied: pid=$pid, uid=$uid")
        }

        override fun onProcessStateChanged(pid: Int, uid: Int, procState: Int) {
            LoggerX.d(
                tag, "onProcessStateChanged: pid=$pid, uid=$uid, procState=$procState"
            )

            if (uid == appUid) sendBinder()
        }
    }

    private inner class UidObserver : UidObserverAdapter() {
        override fun onUidActive(uid: Int) {
            LoggerX.d(UidObserverTAG, "onUidCachedChanged: uid=$uid")

            uidStarts(uid)
        }

        override fun onUidCachedChanged(uid: Int, cached: Boolean) {
            LoggerX.d(
                UidObserverTAG, "onUidCachedChanged: uid=$uid, cached=$cached"
            )

            if (!cached) {
                uidStarts(uid)
            }
        }

        override fun onUidIdle(uid: Int, disabled: Boolean) {
            LoggerX.d(
                UidObserverTAG, "onUidIdle: uid=$uid, disabled=$disabled"
            )

            uidStarts(uid)
        }

        override fun onUidGone(uid: Int, disabled: Boolean) {
            LoggerX.d(
                UidObserverTAG, "onUidGone: uid=$uid, disabled=$disabled"
            )
        }

        fun uidStarts(uid: Int) {
            if (uid == appUid) sendBinder()
        }
    }
}
