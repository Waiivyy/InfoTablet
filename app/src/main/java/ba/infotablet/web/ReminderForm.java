package ba.infotablet.web;

import java.util.Map;

import ba.infotablet.reminders.Recurrence;
import ba.infotablet.reminders.Reminder;

/** Reads and checks the reminder form of the Wi-Fi page. */
public final class ReminderForm {

    public static final int MAX_TEXT = 120;

    /** The parsed reminder, or null with an error message for the person filling in the form. */
    public final Reminder reminder;
    public final String error;

    private ReminderForm(Reminder r, String error) {
        this.reminder = r;
        this.error = error;
    }

    /** Fields: text, date (yyyy-mm-dd), time (HH:MM, ignored for a whole-day plan), mode (0 to 2), repeat (0 to 4), category. */
    public static ReminderForm parse(Map<String, String> f, long now) {
        Reminder r = new Reminder();
        String text = clean(f.get("text"));
        r.text = text;
        r.category = f.get("category") == null ? "" : f.get("category");
        String date = f.get("date") == null ? "" : f.get("date").trim();
        String time = f.get("time") == null ? "" : f.get("time").trim();
        try {
            r.repeat = Integer.parseInt(f.get("repeat") == null ? "0" : f.get("repeat"));
        } catch (NumberFormatException e) {
            r.repeat = -1;
        }
        if (text.isEmpty()) return new ReminderForm(r, "Upišite šta treba zapamtiti.");
        if (text.length() > MAX_TEXT) return new ReminderForm(r, "Tekst je predug (najviše " + MAX_TEXT + " znakova).");
        if (r.repeat < Reminder.ONCE || r.repeat > Reminder.YEARLY || !Reminder.validCategory(r.category)) {
            return new ReminderForm(r, "Neispravan unos.");
        }
        int mode;
        try {
            mode = Integer.parseInt(f.get("mode") == null ? "0" : f.get("mode"));
        } catch (NumberFormatException e) {
            mode = -1;
        }
        if (mode < Reminder.MODE_RING || mode > Reminder.MODE_ALL_DAY) return new ReminderForm(r, "Neispravan unos.");
        if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) return new ReminderForm(r, "Odaberite datum.");
        boolean allDay = mode == Reminder.MODE_ALL_DAY;
        if (!allDay && !time.matches("\\d{1,2}:\\d{2}(:\\d{2})?")) return new ReminderForm(r, "Odaberite vrijeme.");
        r.year = Integer.parseInt(date.substring(0, 4));
        r.month = Integer.parseInt(date.substring(5, 7));
        r.day = Integer.parseInt(date.substring(8, 10));
        if (!allDay) {
            String[] hm = time.split(":");
            r.hour = Integer.parseInt(hm[0]);
            r.minute = Integer.parseInt(hm[1]);
        }
        r.setMode(mode);
        if (!Recurrence.validDate(r.year, r.month, r.day, r.hour, r.minute)) return new ReminderForm(r, "Taj datum ne postoji.");
        long first = Recurrence.occurrence(r, 0);
        if (r.repeat == Reminder.ONCE && (allDay ? first < ba.infotablet.util.Fmt.startOfDay(now) : first <= now)) {
            return new ReminderForm(r, allDay ? "Ovaj dan je već prošao." : "Ovo vrijeme je već prošlo.");
        }
        return new ReminderForm(r, null);
    }

    /** Trims, folds whitespace and drops control characters. */
    static String clean(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append(Character.isISOControl(c) ? ' ' : c);
        }
        return b.toString().trim().replaceAll("\\s+", " ");
    }
}
