package ba.infotablet.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import ba.infotablet.reminders.ReminderStore;
import ba.infotablet.util.Clock;

/**
 * System alarms are lost at reboot and must follow clock or time zone changes, so set the next
 * one again. Starting the app process here also starts data syncing after a reboot.
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
    }
}
