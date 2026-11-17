package ba.infotablet.debug;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import ba.infotablet.alarm.AlarmActivity;
import ba.infotablet.sync.Scheduler;
import ba.infotablet.update.Updater;
import ba.infotablet.util.Clock;

/**
 * Debug builds only. Shift the UI clock, for example to 18:30 today:
 * adb shell am broadcast -a ba.infotablet.DEBUG_TIME --es hhmm 18:30
 * Another day: add --es days 1 (tomorrow), --es days -1 (yesterday).
 * Reset: adb shell am broadcast -a ba.infotablet.DEBUG_TIME --es hhmm off
 * Shorter alarm rings for testing the unanswered path (seconds; 0 restores the normal length):
 * adb shell am broadcast -a ba.infotablet.DEBUG_TIME --es ring_s 15
 * Updates: check another GitHub repository ("off" restores the real one), or offer a local APK
 * as the downloaded new version (it must be newer and signed with the same key):
 * adb shell am broadcast -a ba.infotablet.DEBUG_TIME --es update_repo owner/name
 * adb shell am broadcast -a ba.infotablet.DEBUG_TIME --es update_file /sdcard/x.apk --es update_version 9.9.9
 */
public final class DebugTimeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String repo = intent.getStringExtra("update_repo");
        if (repo != null) {
            Updater.setDebugRepo(repo.equals("off") ? null : repo.trim());
            Log.i("InfoTablet", "debug update repo " + Updater.repo());
            return;
        }
        String file = intent.getStringExtra("update_file");
        if (file != null) {
            String version = intent.getStringExtra("update_version");
            Updater.debugOffer(ctx.getApplicationContext(), new java.io.File(file), version == null ? "9.9.9" : version);
            return;
        }
        String ring = intent.getStringExtra("ring_s");
        if (ring != null) {
            AlarmActivity.setDebugRingMs(Long.parseLong(ring.trim()) * 1000L);
            Log.i("InfoTablet", "debug ring length " + ring + " s");
            return;
        }
        String hhmm = intent.getStringExtra("hhmm");
        long offset = 0;
        if (hhmm != null && hhmm.matches("\\d{1,2}:\\d{2}")) {
            java.util.Calendar c = java.util.Calendar.getInstance();
            long real = c.getTimeInMillis();
            String[] parts = hhmm.split(":");
            c.set(java.util.Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
            c.set(java.util.Calendar.MINUTE, Integer.parseInt(parts[1]));
            String days = intent.getStringExtra("days");
            if (days != null) c.add(java.util.Calendar.DAY_OF_MONTH, Integer.parseInt(days.trim()));
            offset = c.getTimeInMillis() - real;
        }
        Clock.setDebugOffset(offset);
        Log.i("InfoTablet", "debug clock offset " + offset + " ms");
        Scheduler.get(ctx).notifyChanged();
    }
}
