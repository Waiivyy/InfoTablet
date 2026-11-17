package ba.infotablet.reminders;

import java.util.Calendar;

/**
 * Occurrence arithmetic. Occurrence k is the start date plus k days, weeks, months or years,
 * always counted from the start, so a reminder on the 31st falls on the 30th in shorter months
 * and is back on the 31st afterwards (29 February falls on the 28th in other years).
 */
public final class Recurrence {

    public static final long NONE = Long.MAX_VALUE;
    private static final long DAY = 86_400_000L;

    private Recurrence() {}

    /** Occurrence number k (k >= 0), or NONE. */
    public static long occurrence(Reminder r, int k) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(r.year, r.month - 1, r.day, r.hour, r.minute, 0);
        switch (r.repeat) {
            case Reminder.DAILY: c.add(Calendar.DAY_OF_MONTH, k); break;
            case Reminder.WEEKLY: c.add(Calendar.DAY_OF_MONTH, 7 * k); break;
            case Reminder.MONTHLY: c.add(Calendar.MONTH, k); break;
            case Reminder.YEARLY: c.add(Calendar.YEAR, k); break;
            default: if (k > 0) return NONE;
        }
        return c.getTimeInMillis();
    }

    /** Index of the first occurrence strictly after t, or -1 when there is none. */
    static int indexAfter(Reminder r, long t) {
        long first = occurrence(r, 0);
        if (first > t) return 0;
        if (r.repeat == Reminder.ONCE) return -1;
        // Start from a safe lower bound (units taken at their longest, minus two for daylight saving) and step up.
        long unit = r.repeat == Reminder.DAILY ? DAY : r.repeat == Reminder.WEEKLY ? 7 * DAY
                : r.repeat == Reminder.MONTHLY ? 31 * DAY : 366 * DAY;
        int k = (int) Math.max(1, (t - first) / unit - 2);
        while (occurrence(r, k) <= t) k++;
        return k;
    }

    /** The first occurrence strictly after t, or NONE. */
    public static long firstAfter(Reminder r, long t) {
        int k = indexAfter(r, t);
        return k < 0 ? NONE : occurrence(r, k);
    }

    /** The last occurrence at or before t, or NONE when the first one is still ahead. */
    public static long lastAtOrBefore(Reminder r, long t) {
        int k = indexAfter(r, t);
        if (k == 0) return NONE;
        return k < 0 ? occurrence(r, 0) : occurrence(r, k - 1);
    }

    /** True when the date fields form a real calendar date and the time is valid. */
    public static boolean validDate(int year, int month, int day, int hour, int minute) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12 || day < 1 || hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return false;
        }
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month - 1, 1);
        return day <= c.getActualMaximum(Calendar.DAY_OF_MONTH);
    }
}
