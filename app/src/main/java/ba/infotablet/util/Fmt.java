package ba.infotablet.util;

import java.util.Calendar;
import java.util.Locale;

/** Number, temperature and relative-day formatting in Bosnian conventions (decimal comma). */
public final class Fmt {

    private static final String[] SHORT_DAYS = {"ned", "pon", "uto", "sri", "čet", "pet", "sub"};

    private Fmt() {}

    /** "13°" (rounded; "-0°" avoided). */
    public static String temp(double t) {
        long r = Math.round(t);
        return (r == 0 ? 0 : r) + "°";
    }

    /** One decimal with a comma, trailing ",0" dropped: 4.8 gives "4,8", 3.0 gives "3". */
    public static String dec1(double v) {
        String s = String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
        return s.endsWith(",0") ? s.substring(0, s.length() - 2) : s;
    }

    /** Two decimals with a comma: 3.58 gives "3,58". */
    public static String dec2(double v) {
        return String.format(Locale.ROOT, "%.2f", v).replace('.', ',');
    }

    public static String hhmm(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return BsDate.hhmm(c);
    }

    /** "17.11." */
    public static String dayMonth(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return c.get(Calendar.DAY_OF_MONTH) + "." + (c.get(Calendar.MONTH) + 1) + ".";
    }

    public static String shortWeekday(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return SHORT_DAYS[c.get(Calendar.DAY_OF_WEEK) - 1];
    }

    /** Whole days between the local dates of now and t (0 today, 1 tomorrow, -1 yesterday). */
    public static int dayOffset(long now, long t) {
        return (int) ((startOfDay(t) - startOfDay(now) + 12 * 3600_000L) / 86_400_000L);
    }

    public static long startOfDay(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** "danas", "sutra", "prekosutra", "jučer", otherwise "pet 20.11.". */
    public static String relDay(long now, long t) {
        switch (dayOffset(now, t)) {
            case -1: return "jučer";
            case 0: return "danas";
            case 1: return "sutra";
            case 2: return "prekosutra";
            default: return shortWeekday(t) + " " + dayMonth(t);
        }
    }

    /** "danas 14:05" style, for "zadnje ..." notes; just the time when it is today. */
    public static String when(long now, long t) {
        return dayOffset(now, t) == 0 ? hhmm(t) : relDay(now, t) + " " + hhmm(t);
    }
}
