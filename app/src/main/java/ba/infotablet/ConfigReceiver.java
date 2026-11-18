package ba.infotablet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Arrays;

import ba.infotablet.security.Pin;
import ba.infotablet.sync.Scheduler;
import ba.infotablet.web.WebServer;

/**
 * Sets device-only values from a computer over adb, so personal data never ends up in the code:
 * adb shell am broadcast -a ba.infotablet.CONFIG --es key sport_club --es value 409
 * A value of "-" clears the setting (adb cannot pass an empty string). The key "pin" sets the
 * PIN (stored as a hash; "-" removes it, for when it has been forgotten).
 * The receiver requires android.permission.DUMP, which the adb shell has and normal apps cannot get.
 */
public final class ConfigReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String key = intent.getStringExtra("key");
        String value = intent.getStringExtra("value");
        if ("pin".equals(key) && value != null) {
            String v = value.trim();
            if (!v.equals("-") && !Pin.valid(v)) {
                Log.w("InfoTablet", "config: PIN must be 4 to 8 digits");
                setResultCode(1);
                return;
            }
            Settings.of(ctx).setPinHash(v.equals("-") ? "" : Pin.hash(v));
            Log.i("InfoTablet", "config: pin " + (v.equals("-") ? "removed" : "set"));
            WebServer.update(ctx);
            setResultCode(0);
            return;
        }
        if (key == null || value == null || !Arrays.asList(Settings.ADB_KEYS).contains(key)) {
            Log.w("InfoTablet", "config: unknown or missing key");
            setResultCode(1);
            return;
        }
        String v = value.trim();
        Settings.of(ctx).putString(key, v.equals("-") ? "" : v);
        Log.i("InfoTablet", "config: " + key + " updated");
        Scheduler s = Scheduler.get(ctx);
        if (key.equals("sport_club")) s.refreshNow("sport");
        if (key.equals("water_places")) {
            for (String id : new String[] {"water", "power", "heat", "gas"}) s.refreshNow(id);
        }
        if (key.equals("city")) {
            s.refreshNow("weather");
            s.refreshNow("warnings");
            s.refreshNow("air_cams");
        }
        if (key.equals("city2")) s.refreshNow("weather2");
        s.notifyChanged();
        setResultCode(0);
    }
}
