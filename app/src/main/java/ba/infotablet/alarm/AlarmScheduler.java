package ba.infotablet.alarm;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import java.util.List;

import ba.infotablet.reminders.Agenda;
import ba.infotablet.reminders.Reminder;
import ba.infotablet.reminders.ReminderStore;
import ba.infotablet.util.Clock;
import ba.infotablet.util.Fmt;

/**
 * Keeps exactly one system alarm set for the next reminder or snooze end, and opens the alarm
 * screen when something is ringing. Called after every reminder change, at boot, after a time
 * change, after an app update and when the app process starts.
 */
public final class AlarmScheduler {

    private static final String TAG = "InfoTablet";
    private static PowerManager.WakeLock wakeLock;

    private AlarmScheduler() {}

    public static void reschedule(Context ctx) {
        Context app = ctx.getApplicationContext();
        long now = Clock.now();
        List<Reminder> all = ReminderStore.all(app);
        long wake = Agenda.nextWake(all, now);
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = pending(app);
        if (wake == Agenda.NONE) {
            am.cancel(pi);
            Log.i(TAG, "alarm: none scheduled");
        } else {
            // The debug clock can shift the UI time; the system alarm needs real time.
            setExact(am, wake - Clock.offset(), pi);
            Log.i(TAG, "alarm: next at " + Fmt.dayMonth(wake) + " " + Fmt.hhmm(wake));
        }
        if (!Agenda.ringing(all, now).isEmpty() && !AlarmActivity.isShowing()) open(app);
    }

    /** Opens the alarm screen if something is ringing and it is not already in front. */
    public static void checkRinging(Context ctx) {
        if (AlarmActivity.isShowing()) return;
        if (!Agenda.ringing(ReminderStore.all(ctx), Clock.now()).isEmpty()) open(ctx.getApplicationContext());
    }

    @SuppressLint("ScheduleExactAlarm") // guarded by canScheduleExactAlarms() below
    private static void setExact(AlarmManager am, long at, PendingIntent pi) {
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi);
        } else if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } else if (Build.VERSION.SDK_INT >= 19) {
            am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, at, pi); // exact before Android 4.4
        }
    }

    private static PendingIntent pending(Context app) {
        Intent i = new Intent(app, AlarmReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(app, 0, i, flags);
    }

    private static void open(Context app) {
        acquireWakeLock(app);
        Intent i = new Intent(app, AlarmActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        app.startActivity(i);
        Log.i(TAG, "alarm: ringing");
    }

    /** Wakes the screen until the alarm screen is up (it then keeps the screen on itself). */
    @SuppressWarnings("deprecation")
    static synchronized void acquireWakeLock(Context app) {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
                    | PowerManager.ON_AFTER_RELEASE, "InfoTablet:alarm");
            wakeLock.setReferenceCounted(false);
        }
        wakeLock.acquire(60_000L);
    }

    static synchronized void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }
}
