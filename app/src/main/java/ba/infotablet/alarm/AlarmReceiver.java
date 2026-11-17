package ba.infotablet.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The system alarm went off: ring what is due and set the next alarm. */
public final class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        AlarmScheduler.acquireWakeLock(ctx.getApplicationContext());
        AlarmScheduler.reschedule(ctx);
    }
}
