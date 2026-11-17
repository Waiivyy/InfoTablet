package ba.infotablet.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import ba.infotablet.MainActivity;
import ba.infotablet.reminders.ReminderStore;
import ba.infotablet.util.Clock;

/**
 * System alarms are lost at reboot and must follow clock or time zone changes, so set the next
 * one again. Starting the app process here also starts data syncing after a reboot, and after an
 * update the dashboard is opened again.
 */
public final class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String a = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_TIME_CHANGED.equals(a)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            return;
        }
        ReminderStore.cleanup(ctx, Clock.now());
        AlarmScheduler.reschedule(ctx);
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            // After an update the installer's "App installed" box would stay on the wall until
            // someone taps it; bring the dashboard back instead.
            try {
                ctx.startActivity(new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException e) {
                android.util.Log.w("InfoTablet", "could not reopen after the update: " + e);
            }
        }
    }
}
