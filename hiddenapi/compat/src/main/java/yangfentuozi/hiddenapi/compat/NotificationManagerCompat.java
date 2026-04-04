package yangfentuozi.hiddenapi.compat;

import android.app.INotificationManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.content.pm.ParceledListSlice;
import android.os.RemoteException;
import android.os.ServiceManager;

public class NotificationManagerCompat {
    private static INotificationManager service;

    private static void init() {
        if (service == null) {
            service = INotificationManager.Stub.asInterface(ServiceManager.getService("notification"));
        }
    }

    public static void createChannel(
            INotificationManager nm,
            String pkg,
            int uid,
            NotificationChannel channel
    ) throws RemoteException {
        init();

        ParceledListSlice<NotificationChannel> slice =
                new ParceledListSlice<>(java.util.Collections.singletonList(channel));
        nm.createNotificationChannelsForPackage(pkg, uid, slice);
    }

    public static void enqueueNotification(
            INotificationManager nm,
            String pkg,
            String tag,
            int id,
            Notification notification,
            int userId
    ) throws RemoteException {
        init();

        nm.enqueueNotificationWithTag(pkg, pkg, tag, id, notification, userId);
    }

    public static void cancelNotification(
            INotificationManager nm,
            String pkg,
            String tag,
            int id,
            int userId
    ) throws RemoteException {
        init();

        nm.cancelNotificationWithTag(pkg, pkg, tag, id, userId);
    }
}
