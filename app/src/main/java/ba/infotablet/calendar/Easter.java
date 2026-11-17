package ba.infotablet.calendar;

import java.util.Calendar;

/** Easter dates: Catholic (Gregorian, Meeus/Butcher) and Orthodox (Julian, converted to Gregorian). */
public final class Easter {

    private Easter() {}

    /** {month (1-12), day} of Catholic Easter. */
    public static int[] catholic(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = ((h + l - 7 * m + 114) % 31) + 1;
        return new int[] {month, day};
    }

    /** {month (1-12), day} of Orthodox Easter in the Gregorian calendar (valid 1900 to 2099). */
    public static int[] orthodox(int y) {
        int a = y % 4, b = y % 7, c = y % 19, d = (19 * c + 15) % 30, e = (2 * a + 4 * b - d + 34) % 7;
        int month = (d + e + 114) / 31, day = ((d + e + 114) % 31) + 1;
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(y, month - 1, day);
        cal.add(Calendar.DAY_OF_MONTH, 13); // Julian to Gregorian offset in this century
        return new int[] {cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)};
    }
}
