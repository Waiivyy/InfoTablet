package ba.infotablet.ui;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import ba.infotablet.Settings;
import ba.infotablet.data.Cache;
import ba.infotablet.data.SourceState;
import ba.infotablet.sources.WeatherSource;
import ba.infotablet.util.Fmt;
import ba.infotablet.util.Sun;

/**
 * Day and night: light from sunrise, dark from sunset (times from the Open-Meteo forecast, or
 * calculated when it is missing), and the night dimming window. Also turns the look settings into
 * a palette and a "look key" that tells the activity when the screens must be rebuilt.
 */
public final class Theme {

    private static final long MEMO_MS = 10 * 60_000L;
    private static long memoAt, memoDay;
    private static long[] memo;

    private Theme() {}

    /** {sunrise, sunset} for the local date of now (kept for ten minutes: it is asked on every touch). */
    public static synchronized long[] sunTimes(Context c, long now) {
        long day = Fmt.startOfDay(now);
        if (memo != null && day == memoDay && Math.abs(now - memoAt) < MEMO_MS) return memo;
        long[] t = forecastSun(Cache.get(c, WeatherSource.ID), now);
        if (t == null) {
            Settings s = Settings.of(c);
            t = Sun.times(s.latitude(), s.longitude(), now);
        }
        memo = t;
        memoAt = now;
        memoDay = day;
        return t;
    }

    /** Forget the cached times (the location changed). */
    public static synchronized void forgetSun() {
        memo = null;
    }

    /** Today's sunrise and sunset from the cached forecast, or null. */
    static long[] forecastSun(SourceState st, long now) {
        if (!st.hasData()) return null;
        try {
            JSONArray days = new JSONObject(st.payload).getJSONArray("daily");
            long today = Fmt.startOfDay(now);
            for (int i = 0; i < days.length(); i++) {
                JSONObject d = days.getJSONObject(i);
                if (Fmt.startOfDay(d.getLong("date")) == today && d.has("sunrise") && d.has("sunset")) {
                    return new long[] {d.getLong("sunrise"), d.getLong("sunset")};
                }
            }
        } catch (JSONException ignored) {
            // fall back to the calculation
        }
        return null;
    }

    /** True between sunset and the next sunrise. */
    public static boolean afterDark(Context c, long now) {
        long[] sun = sunTimes(c, now);
        if (sun[0] == 0 || sun[1] == 0) return false;
        return now < sun[0] || now >= sun[1];
    }

    public static boolean dark(Context c, long now) {
        String t = Settings.of(c).theme();
        if ("light".equals(t)) return false;
        if ("dark".equals(t)) return true;
        return afterDark(c, now);
    }

    /** Whether the screen should be dimmed now. */
    public static boolean night(Context c, long now) {
        Settings s = Settings.of(c);
        if (!s.nightDim()) return false;
        if (s.nightBySun()) return afterDark(c, now);
        return inWindow(minuteOfDay(now), s.nightStart(), s.nightEnd());
    }

    /** start..end in minutes after midnight, wrapping past midnight (22:00 to 06:00). */
    static boolean inWindow(int minute, int start, int end) {
        if (start == end) return false;
        return start < end ? minute >= start && minute < end : minute >= start || minute < end;
    }

    private static int minuteOfDay(long t) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(t);
        return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
    }

    public static Palette palette(Context c, long now) {
        Settings s = Settings.of(c);
        Palette base = dark(c, now) ? Palette.DARK : Palette.LIGHT;
        return base.with(s.accent(), s.background());
    }

    /** Applies the text size and density settings to the view factory. */
    public static void configureUi(Context c) {
        Settings s = Settings.of(c);
        Ui.configure(s.largeText(), s.compact());
    }

    /** Changes when anything that is baked into the built screens changes. */
    public static String lookKey(Context c, long now) {
        Settings s = Settings.of(c);
        StringBuilder b = new StringBuilder();
        b.append(dark(c, now)).append(s.accent()).append(s.background()).append(s.largeText()).append(s.compact())
                .append(s.clock24h()).append(s.clockSeconds());
        for (String t : s.tiles()) b.append(t);
        for (int i = Settings.FIRST_HIDEABLE_TAB; i <= Settings.LAST_HIDEABLE_TAB; i++) b.append(s.tabVisible(i) ? '1' : '0');
        return b.toString();
    }
}
