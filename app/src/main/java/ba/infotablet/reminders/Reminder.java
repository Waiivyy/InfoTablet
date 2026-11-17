package ba.infotablet.reminders;

/**
 * One reminder. The date and time are kept as local wall-clock fields, not as an instant, so a
 * daily 08:00 reminder stays at 08:00 across daylight saving changes.
 */
public final class Reminder {

    public static final int ONCE = 0, DAILY = 1, WEEKLY = 2, MONTHLY = 3, YEARLY = 4;

    public static final String[] CATEGORIES = {"", "racun", "doktor", "lijek", "auto", "rodjendan"};

    public long id;
    public String text = "";
    public int year, month, day;   // month 1 to 12
    public int hour, minute;
    public int repeat = ONCE;
    public String category = "";

    /** Every occurrence at or before this instant has been dealt with ("Gotovo", or edited). */
    public long handledUntil;
    /** The occurrence a snooze applies to, the time it ends (Long.MAX_VALUE: silenced) and how often it rang unanswered. */
    public long snoozeFor, snoozeUntil;
    public int rings;
    public long createdAt, updatedAt;

    public Reminder copy() {
        Reminder r = new Reminder();
        r.id = id;
        r.text = text;
        r.year = year;
        r.month = month;
        r.day = day;
        r.hour = hour;
        r.minute = minute;
        r.repeat = repeat;
        r.category = category;
        r.handledUntil = handledUntil;
        r.snoozeFor = snoozeFor;
        r.snoozeUntil = snoozeUntil;
        r.rings = rings;
        r.createdAt = createdAt;
        r.updatedAt = updatedAt;
        return r;
    }

    public static String categoryName(String key) {
        if ("racun".equals(key)) return "Račun";
        if ("doktor".equals(key)) return "Doktor";
        if ("lijek".equals(key)) return "Lijek";
        if ("auto".equals(key)) return "Auto";
        if ("rodjendan".equals(key)) return "Rođendan";
        return "";
    }

    public static boolean validCategory(String key) {
        for (String k : CATEGORIES) if (k.equals(key)) return true;
        return false;
    }

    /** Short segment labels for the editor. */
    public static final String[] REPEAT_SHORT = {"Jednom", "Dnevno", "Sedmično", "Mjesečno", "Godišnje"};
}
