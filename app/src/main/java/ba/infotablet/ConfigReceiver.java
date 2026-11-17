package ba.infotablet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Arrays;

import ba.infotablet.sync.Scheduler;

/**
 * Sets device-only values from a computer over adb, so personal data never ends up in the code:
 * adb shell am broadcast -a ba.infotablet.CONFIG --es key sport_club --es value 409
 * The receiver requires android.permission.DUMP, which the adb shell has and normal apps cannot get.
 */
public final class ConfigReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String key = intent.getStringExtra("key");
        String value = intent.getStringExtra("value");
        if (key == null || value == null || !Arrays.asList(Settings.ADB_KEYS).contains(key)) {
            Log.w("InfoTablet", "config: unknown or missing key");
            setResultCode(1);
            return;
        }
        Settings.of(ctx).putString(key, value.trim());
        Log.i("InfoTablet", "config: " + key + " updated");
        Scheduler s = Scheduler.get(ctx);
        if (key.equals("sport_club")) s.refreshNow("sport");
        if (key.equals("water_places")) s.refreshNow("water");
        s.notifyChanged();
        setResultCode(0);
    }
}
