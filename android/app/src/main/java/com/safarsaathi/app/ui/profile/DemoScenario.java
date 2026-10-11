package com.safarsaathi.app.ui.profile;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.safarsaathi.app.MainActivity;
import com.safarsaathi.app.R;

/**
 * "Demo scenario" (debug builds only, Profile): fires the alerts the finished app will send, so the presentation can
 * show them without a live airline feed or the server's demo buttons. Every alert says DEMO SCENARIO and uses the
 * demo trip's values (IJ 512, the SAMPLE bag), never real flight data. Nothing on the server changes.
 */
public final class DemoScenario {

    private static final String CHANNEL_ID = "demo_scenario";
    private static int nextId = 7100;

    private DemoScenario() {
    }

    static void show(Fragment fragment) {
        Context context = fragment.requireContext();
        String[] choices = context.getResources().getStringArray(R.array.demo_scenario_choices);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.demo_scenario_title)
                .setItems(choices, (dialog, which) -> fire(fragment, which))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void fire(Fragment fragment, int which) {
        Context context = fragment.requireContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // Ask once; the traveller taps the scenario again after allowing.
            ActivityCompat.requestPermissions(fragment.requireActivity(),
                    new String[] {Manifest.permission.POST_NOTIFICATIONS}, 71);
            return;
        }
        String[] titles = context.getResources().getStringArray(R.array.demo_scenario_titles);
        String[] texts = context.getResources().getStringArray(R.array.demo_scenario_texts);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.demo_scenario_channel), NotificationManager.IMPORTANCE_HIGH));

        // Tapping the alert opens its next step (MainActivity.handleDemoTap): route to the gate, Home, Bags, or the
        // missed-connection plan. SINGLE_TOP + CLEAR_TOP reach the running screen instead of a second copy.
        Intent open = new Intent(context, MainActivity.class)
                .putExtra(EXTRA_SCENARIO, which)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(context, 100 + which, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder alert = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_navigation)
                .setContentTitle(titles[which])
                .setContentText(texts[which])
                .setStyle(new NotificationCompat.BigTextStyle().bigText(texts[which]))
                .setSubText(context.getString(R.string.demo_scenario_label))
                .setColor(ContextCompat.getColor(context, R.color.brand_logo_orange))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(tap);
        try {
            NotificationManagerCompat.from(context).notify(nextId++, alert.build());
        } catch (SecurityException e) {
            // Notifications were switched off in the phone's settings: nothing to show.
        }
    }

    /** Intent extra on a demo alert's tap: which scenario it was (index into demo_scenario_choices). */
    public static final String EXTRA_SCENARIO = "onboard.demo_scenario";
    public static final int GATE_ANNOUNCED = 0;
    public static final int GATE_CHANGED = 1;
    public static final int DELAYED = 2;
    public static final int BAG_ON_BELT = 3;
    /** The 5th scenario: what OnBoard tells a traveller who will miss a connection (rebook, options, refund). */
    public static final int MISSED_CONNECTION = 4;

    public static void showMissedConnectionPlan(Context context) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.demo_missed_title)
                .setMessage(R.string.demo_missed_body)
                .setPositiveButton(R.string.demo_missed_email, (dialog, which) -> {
                    android.net.Uri uri = android.net.Uri.parse("mailto:?subject="
                            + android.net.Uri.encode(context.getString(R.string.demo_missed_email_subject))
                            + "&body=" + android.net.Uri.encode(context.getString(R.string.demo_missed_email_body)));
                    try {
                        context.startActivity(new Intent(Intent.ACTION_SENDTO, uri));
                    } catch (android.content.ActivityNotFoundException e) {
                        android.widget.Toast.makeText(context, R.string.demo_missed_no_mail,
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(R.string.demo_missed_close, null)
                .show();
    }
}
