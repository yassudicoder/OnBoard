package com.safarsaathi.app.ui.navigate;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;

/**
 * Keeps step counting going while the traveller walks to the gate with the screen off or in another app: Android only
 * sends sensor events to an app in the background while it runs a "foreground service". This service does nothing
 * else. It shows {@link NavNotification}'s notification (which the walk keeps up to date) and stops when the walk ends.
 *
 * <p>Its type is "health" (step counting). Android allows that type only with the physical-activity permission, so
 * {@link NavNotification} starts it only when the traveller allowed step counting.
 */
public class NavigationService extends Service {

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        Notification notification = NavNotification.current();
        if (notification == null) {
            stopSelf(); // the walk ended before the service got going
            return START_NOT_STICKY;
        }
        try {
            ServiceCompat.startForeground(this, NavNotification.ID, notification,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                            ? ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH : 0);
        } catch (RuntimeException e) {
            // E.g. the app went to the background in the same moment: the walk goes on with a plain notification.
            Log.w("SafarSaathi", "Navigation service could not run in the foreground", e);
            stopSelf();
        }
        // Not restarted after Android stops the app: the walk (kept in memory) is gone then anyway.
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // started, never bound
    }
}
