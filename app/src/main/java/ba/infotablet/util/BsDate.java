package ba.infotablet.util;

import java.util.Calendar;
import java.util.Locale;

/** Bosnian (ijekavica) date and time formatting, independent of the device locale. */
public final class BsDate {

    private static final String[] DAYS = {
            "nedjelja", "ponedjeljak", "utorak", "srijeda", "četvrtak", "petak", "subota"};
    private static final String[] MONTHS = {
            "januar", "februar", "mart", "april", "maj", "juni",
            "juli", "august", "septembar", "oktobar", "novembar", "decembar"};

    private BsDate() {}

    public static String weekday(Calendar c) {
        return DAYS[c.get(Calendar.DAY_OF_WEEK) - 1];
    }

    public static String month(Calendar c) {
        return MONTHS[c.get(Calendar.MONTH)];
    }

    /** "Utorak, 17. novembar 2026." */
    public static String longDate(Calendar c) {
        return capitalize(weekday(c)) + ", " + c.get(Calendar.DAY_OF_MONTH) + ". " + month(c) + " " + c.get(Calendar.YEAR) + ".";
    }

    /** "14:32" (24-hour). */
    public static String hhmm(Calendar c) {
        return String.format(Locale.ROOT, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    public static String capitalize(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
