package yangfentuozi.batteryrecorder.server.fakecontext;

import android.app.ContentProviderHolder;
import android.app.IActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.IContentProvider;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class ExternalProviderResolver extends ContentResolver {

    private final IActivityManager activityManager;
    private final IBinder providerToken;
    private final ConcurrentHashMap<IBinder, ProviderRef> providerRefs =
            new ConcurrentHashMap<>();

    ExternalProviderResolver(Context context,
                             IActivityManager activityManager,
                             IBinder providerToken) {
        super(context);
        this.activityManager = activityManager;
        this.providerToken = providerToken;
    }

    @SuppressWarnings({"unused", "ProtectedMemberInFinalClass"})
    protected IContentProvider acquireProvider(Context context, String auth) {
        return acquireExternalProvider(auth);
    }

    @SuppressWarnings({"unused", "ProtectedMemberInFinalClass"})
    protected IContentProvider acquireExistingProvider(Context context, String auth) {
        return acquireExternalProvider(auth);
    }

    @SuppressWarnings("unused")
    public boolean releaseProvider(IContentProvider provider) {
        return releaseExternalProvider(provider);
    }

    @SuppressWarnings({"unused", "ProtectedMemberInFinalClass"})
    protected IContentProvider acquireUnstableProvider(Context context, String auth) {
        return acquireExternalProvider(auth);
    }

    @SuppressWarnings("unused")
    public boolean releaseUnstableProvider(IContentProvider provider) {
        return releaseExternalProvider(provider);
    }

    @SuppressWarnings("unused")
    public void unstableProviderDied(IContentProvider provider) {
    }

    @SuppressWarnings("unused")
    public void appNotRespondingViaProvider(IContentProvider provider) {
    }

    private IContentProvider acquireExternalProvider(String auth) {
        try {
            ContentProviderHolder holder =
                    activityManager.getContentProviderExternal(auth, 0, providerToken, auth);
            if (holder == null || holder.provider == null) {
                return null;
            }
            IContentProvider provider = holder.provider;
            providerRefs.compute(provider.asBinder(), (binder, ref) -> {
                if (ref != null) {
                    ref.count.incrementAndGet();
                    return ref;
                }
                return new ProviderRef(auth);
            });
            return provider;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private boolean releaseExternalProvider(IContentProvider provider) {
        ProviderRef ref = providerRefs.get(provider.asBinder());
        if (ref == null) {
            return false;
        }
        if (ref.count.decrementAndGet() > 0) {
            return true;
        }
        providerRefs.remove(provider.asBinder(), ref);
        try {
            activityManager.removeContentProviderExternal(ref.authority, providerToken);
            return true;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private static final class ProviderRef {
        private final String authority;
        private final AtomicInteger count = new AtomicInteger(1);

        private ProviderRef(String authority) {
            this.authority = authority;
        }
    }
}
