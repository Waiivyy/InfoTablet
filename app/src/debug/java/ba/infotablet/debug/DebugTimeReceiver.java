package ba.infotablet.debug;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import ba.infotablet.sync.Scheduler;
import ba.infotablet.util.Clock;

/**
 * Debug builds only. Shift the UI clock, for example to 18:30 today:
 * adb shell am broadcast -a ba.infotablet.DEBUG_TIME --es hhmm 18:30
 * Reset: adb shell am broadcast -a ba.infotablet.DEBUG_TIME --es hhmm off
 */
public final class DebugTimeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String hhmm = intent.getStringExtra("hhmm");
        long offset = 0;
        if (hhmm != null && hhmm.matches("\\d{1,2}:\\d{2}")) {
            java.util.Calendar c = java.util.Calendar.getInstance();
            long real = c.getTimeInMillis();
            String[] parts = hhmm.split(":");
            c.set(java.util.Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
            c.set(java.util.Calendar.MINUTE, Integer.parseInt(parts[1]));
            offset = c.getTimeInMillis() - real;
        }
        Clock.setDebugOffset(offset);
        Log.i("InfoTablet", "debug clock offset " + offset + " ms");
        Scheduler.get(ctx).notifyChanged();
    }
}
