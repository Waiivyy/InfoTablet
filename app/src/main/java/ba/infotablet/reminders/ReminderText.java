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

    /** The repeat text plus the category name, for list rows: "svaki dan · Lijek". */
    public static String details(Reminder r) {
        String cat = Reminder.categoryName(r.category);
        return cat.isEmpty() ? repeat(r) : repeat(r) + " · " + cat;
    }

    /** "danas 16:00", "sutra 09:30", "pet 20.11. 10:00". */
    public static String when(long now, long at) {
        return Fmt.relDay(now, at) + " " + Fmt.hhmm(at);
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
