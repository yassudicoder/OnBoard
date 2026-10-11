package com.safarsaathi.app.ui.navigate;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.safarsaathi.app.MainActivity;
import com.safarsaathi.app.R;
import com.safarsaathi.app.ui.trip.TripTime;
import com.safarsaathi.routing.map.MapNode;
import com.safarsaathi.routing.route.InstructionText;
import com.safarsaathi.routing.route.Maneuver;
import com.safarsaathi.routing.route.RouteStep;

import java.time.Instant;

/**
 * The notification while navigating, like Google Maps': "Gate 32 · 4 min", the next instruction ("30 m · Turn left at
 * Check-in row D") and the arrival time (an estimate). It stays while the traveller walks with the screen off or uses
 * another app, tapping it opens the walk again, and it goes away when the walk ends.
 *
 * <p>When the phone may count steps, {@link NavigationService} shows it as a "foreground service": Android only keeps
 * sending step events to an app in the background while it runs one, so the arrow (and this notification) keep moving
 * with the screen off. Without step counting it is a plain notification: the arrow then only moves with QR codes,
 * "I'm here" or the demo walk anyway.
 *
 * <p>Since Android 13 the traveller has to allow notifications; without that, navigation works the same, only
 * without this notification.
 */
final class NavNotification {

    static final String CHANNEL_ID = "navigation";
    static final int ID = 1001;
    private static final String TAG = "SafarSaathi";

    /** The notification on show, for {@link NavigationService}; null when not navigating. */
    @Nullable
    private static volatile Notification current;

    private final Context context;
    private final NotificationManagerCompat manager;
    /** What is on show, so the notification is only re-posted when its text changes (Android limits updates). */
    @Nullable
    private String shownKey;
    @Nullable
    private Maneuver iconManeuver;
    @Nullable
    private Bitmap icon;
    private boolean serviceStarted;

    NavNotification(Context context) {
        this.context = context.getApplicationContext();
        manager = NotificationManagerCompat.from(this.context);
        manager.createNotificationChannel(new NotificationChannelCompat.Builder(CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW) // silent, no pop-up: it changes every few seconds
                .setName(this.context.getString(R.string.nav_notification_channel))
                .setDescription(this.context.getString(R.string.nav_notification_channel_detail))
                .setShowBadge(false)
                .build());
        manager.cancel(ID); // left over from an app process that Android stopped in the middle of a walk
    }

    @Nullable
    static Notification current() {
        return current;
    }

    /** After every change of the walk: shows or updates the notification while navigating, removes it otherwise. */
    void update(NavSession session, boolean demoWalk) {
        RouteStep step = session.bannerStep();
        MapNode destination = session.destination();
        if (session.mode() != NavSession.Mode.NAVIGATING || step == null || destination == null
                || session.map() == null) {
            cancel();
            return;
        }
        Resources res = context.getResources();
        String language = NavText.language(res);
        String instruction = InstructionText.forLanguage(language).text(step, session.map());
        String title = res.getString(R.string.nav_notification_title, destination.name(language),
                NavText.minutes(res, session.remainingSeconds()));
        String text;
        if (session.missedTurn() != null) {
            text = res.getString(R.string.nav_missed_turn);
        } else if (step.maneuver.changesFloor()) {
            text = instruction; // "Take Lift A down to Arrivals": no distance while riding
        } else {
            text = res.getString(R.string.nav_notification_text, NavText.distance(res, session.bannerDistance()),
                    instruction);
        }
        String arrival = TripTime.time(Instant.now().plusSeconds(Math.round(session.remainingSeconds())));
        String note = res.getString(demoWalk ? R.string.nav_notification_demo : R.string.nav_notification_arrival,
                arrival);
        String key = title + '|' + text + '|' + note + '|' + step.maneuver;
        if (key.equals(shownKey)) {
            return;
        }
        shownKey = key;
        Notification notification = liveUpdate(build(title, text, note, step.maneuver),
                NavText.minutes(res, session.remainingSeconds()));
        current = notification;
        post(notification);
        startServiceIfStepsCounted();
    }

    /** Shows it again at the next update, e.g. after the traveller allowed notifications or step counting. */
    void refresh() {
        shownKey = null;
    }

    /** The walk ended (or the screen's data was thrown away): no notification, no service. */
    void cancel() {
        current = null;
        shownKey = null;
        if (serviceStarted) {
            serviceStarted = false;
            context.stopService(new Intent(context, NavigationService.class));
        }
        manager.cancel(ID);
    }

    // ---- Inside ----

    private Notification build(String title, String text, String note, Maneuver maneuver) {
        // Like tapping the app icon: back to the running app (the Navigate tab), never a second copy of it.
        Intent open = new Intent(context, MainActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        PendingIntent tap = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_navigation)
                .setLargeIcon(icon(maneuver))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setSubText(note)
                .setColor(ContextCompat.getColor(context, R.color.nav_banner))
                .setColorized(true) // green like the banner (Android only does this for a foreground service)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // directions only, readable on the lock screen
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(tap)
                .build();
    }

    /**
     * Android 16+ "Live Updates", Android's answer to a Dynamic Island: the walk shows as a small chip in the status bar
     * (e.g. "4 min") while the traveller is in another app, and at the top of the notification shade. Android only
     * promotes plain (not colorized) ongoing notifications, so the green colour is dropped there. Older Android keeps
     * the normal green notification.
     */
    private Notification liveUpdate(Notification notification, String chip) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            return notification;
        }
        return Notification.Builder.recoverBuilder(context, notification)
                .setColorized(false)
                .setRequestPromotedOngoing(true)
                .setShortCriticalText(chip)
                .build();
    }

    private void post(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return; // the traveller did not allow notifications
        }
        try {
            manager.notify(ID, notification);
        } catch (SecurityException e) {
            Log.w(TAG, "Navigation notification not allowed", e);
        }
    }

    /** Keeps the step counter going with the screen off: only useful (and only allowed) when steps are counted. */
    private void startServiceIfStepsCounted() {
        if (serviceStarted || !canCountSteps()) {
            return;
        }
        try {
            context.startService(new Intent(context, NavigationService.class));
            serviceStarted = true;
        } catch (IllegalStateException | SecurityException e) {
            // E.g. the app is in the background just now: the walk goes on with a plain notification.
            Log.w(TAG, "Navigation service not started", e);
        }
    }

    private boolean canCountSteps() {
        SensorManager sensors = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        boolean hasSensor = sensors != null && sensors.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null;
        boolean allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
        return hasSensor && allowed;
    }

    /** The turn arrow in a green circle, like the banner (made once per kind of turn). */
    private Bitmap icon(Maneuver maneuver) {
        if (icon == null || maneuver != iconManeuver) {
            int size = Math.round(48 * context.getResources().getDisplayMetrics().density);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(ContextCompat.getColor(context, R.color.nav_banner));
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
            ManeuverDrawable arrow = new ManeuverDrawable(maneuver, Color.WHITE);
            int inset = size / 5;
            arrow.setBounds(inset, inset, size - inset, size - inset);
            arrow.draw(canvas);
            icon = bitmap;
            iconManeuver = maneuver;
        }
        return icon;
    }
}
