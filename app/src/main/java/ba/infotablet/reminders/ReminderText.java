package ba.infotablet.reminders;

import java.util.Calendar;

import ba.infotablet.R;
import ba.infotablet.util.Fmt;

/** Bosnian wording and icons for reminders. */
public final class ReminderText {

    /** Weekdays in the accusative, for "svaki utorak" (index = Calendar.DAY_OF_WEEK - 1). */
    private static final String[] EVERY_WEEKDAY = {"svaku nedjelju", "svaki ponedjeljak", "svaki utorak", "svaku srijedu",
            "svaki četvrtak", "svaki petak", "svaku subotu"};

    private ReminderText() {}

    /** "jednom", "svaki dan", "svaki utorak", "svakog mjeseca, 18.", "svake godine, 18.11." */
    public static String repeat(Reminder r) {
        switch (r.repeat) {
            case Reminder.DAILY: return "svaki dan";
            case Reminder.WEEKLY: {
                Calendar c = Calendar.getInstance();
                c.clear();
                c.set(r.year, r.month - 1, r.day);
                return EVERY_WEEKDAY[c.get(Calendar.DAY_OF_WEEK) - 1];
            }
            case Reminder.MONTHLY: return "svakog mjeseca, " + r.day + ".";
            case Reminder.YEARLY: return "svake godine, " + r.day + "." + r.month + ".";
            default: return "jednom";
        }
    }

    /** The repeat text plus the category name, for list rows: "svaki dan · Lijek", "jednom · bez zvona". */
    public static String details(Reminder r) {
        return details(r, true);
    }

    /** As above; withAllDay false where the row already says "cijeli dan". */
    public static String details(Reminder r, boolean withAllDay) {
        String cat = Reminder.categoryName(r.category);
        String s = cat.isEmpty() ? repeat(r) : repeat(r) + " · " + cat;
        if (r.allDay) return withAllDay ? s + " · cijeli dan" : s;
        return r.ring ? s : s + " · bez zvona";
    }

    /** The time column: "16:00", or "cijeli dan" for a whole-day plan. */
    public static String time(Reminder r, long at) {
        return r.allDay ? "cijeli dan" : Fmt.hhmm(at);
    }

    /** "danas 16:00", "sutra 09:30", "pet 20.11. 10:00". */
    public static String when(long now, long at) {
        return Fmt.relDay(now, at) + " " + Fmt.hhmm(at);
    }

    public static String when(Reminder r, long now, long at) {
        return r.allDay ? Fmt.relDay(now, at) + ", cijeli dan" : when(now, at);
    }

    /** Plans without a category get the calendar icon instead of the bell. */
    public static int icon(Reminder r) {
        if (!r.ring && r.category.isEmpty()) return R.drawable.ic_calendar;
        return icon(r.category);
    }

    public static int icon(String category) {
        if ("racun".equals(category)) return R.drawable.ic_receipt;
        if ("doktor".equals(category)) return R.drawable.ic_med;
        if ("lijek".equals(category)) return R.drawable.ic_pill;
        if ("auto".equals(category)) return R.drawable.ic_car;
        if ("rodjendan".equals(category)) return R.drawable.ic_cake;
        return R.drawable.ic_bell;
    }
}
