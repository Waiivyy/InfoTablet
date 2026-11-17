package ba.infotablet;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Device-local settings. Personal values (names, links, streets) are entered on the device only
 * and never ship in the code. The settings screen comes in a later step; defaults apply until then.
 */
public final class Settings {

    public static final double SARAJEVO_LAT = 43.8563;
    public static final double SARAJEVO_LON = 18.4131;

    private final SharedPreferences prefs;

    private Settings(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public static Settings of(Context ctx) {
        return new Settings(ctx);
    }

    public double latitude() {
        return Double.longBitsToDouble(prefs.getLong("lat", Double.doubleToLongBits(SARAJEVO_LAT)));
    }

    public double longitude() {
        return Double.longBitsToDouble(prefs.getLong("lon", Double.doubleToLongBits(SARAJEVO_LON)));
    }

    public String locationName() {
        return prefs.getString("location_name", "Sarajevo");
    }

    /** MeteoAlarm region shown at every level (others only orange and red). */
    public String warningRegion() {
        return prefs.getString("warning_region", "BA006");
    }

    /** Per-source on/off switch (all sources on by default). */
    public boolean sourceEnabled(String sourceId) {
        return prefs.getBoolean("source_on_" + sourceId, true);
    }

    public void setSourceEnabled(String sourceId, boolean on) {
        prefs.edit().putBoolean("source_on_" + sourceId, on).apply();
    }

    /** Hide "crna hronika" (crime) news; on by default. */
    public boolean hideCrime() {
        return prefs.getBoolean("news_hide_crime", true);
    }
}
