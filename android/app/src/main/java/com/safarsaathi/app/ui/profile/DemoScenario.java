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
final class DemoScenario {

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

        Intent open = new Intent(context, MainActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        PendingIntent tap = PendingIntent.getActivity(context, 1, open,
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
}
