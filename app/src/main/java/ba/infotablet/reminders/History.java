package ba.infotablet.reminders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the reminder log into one line per ring: what was due, and whether it ended with
 * "Gotovo" (and when), was missed, or is still snoozed. Pure logic, so it can be tested.
 */
public final class History {

    public static final String DONE = "done", SNOOZE = "snooze", MISSED = "missed";

    /** How long the log is kept, and how far back the history view goes. */
    public static final long KEEP_MS = 60L * 86_400_000L;
    public static final long SHOW_MS = 30L * 86_400_000L;

    public enum Outcome { DONE, MISSED, OPEN }

    /** One log row as stored. */
    public static final class Event {
        public final long reminderId, dueAt, at;
        public final String text, category, kind;

        public Event(long reminderId, long dueAt, String text, String category, String kind, long at) {
            this.reminderId = reminderId;
            this.dueAt = dueAt;
            this.text = text;
            this.category = category;
            this.kind = kind;
            this.at = at;
        }
    }

    /** One ring of one reminder. */
    public static final class Row {
        public long reminderId, dueAt;
        public String text = "", category = "";
        public Outcome outcome = Outcome.OPEN;
        /** When "Gotovo" was pressed (DONE only). */
        public long doneAt;
        public int snoozes;

        /** Minutes between the ring and "Gotovo" (DONE only). */
        public long lateMinutes() {
            return Math.max(0, (doneAt - dueAt) / 60_000L);
        }
    }

    private History() {}

    /** Rows newest first. A later "Gotovo" overrides "missed" for the same ring. */
    public static List<Row> rows(List<Event> events) {
        Map<String, Row> byRing = new LinkedHashMap<String, Row>();
        List<Event> sorted = new ArrayList<Event>(events);
        Collections.sort(sorted, (a, b) -> a.at < b.at ? -1 : a.at > b.at ? 1 : 0);
        for (Event e : sorted) {
            String key = e.reminderId + "@" + e.dueAt;
            Row r = byRing.get(key);
            if (r == null) {
                r = new Row();
                r.reminderId = e.reminderId;
                r.dueAt = e.dueAt;
                byRing.put(key, r);
            }
            r.text = e.text;
            r.category = e.category;
            if (DONE.equals(e.kind)) {
                r.outcome = Outcome.DONE;
                r.doneAt = e.at;
            } else if (MISSED.equals(e.kind)) {
                if (r.outcome != Outcome.DONE) r.outcome = Outcome.MISSED;
            } else if (SNOOZE.equals(e.kind)) {
                r.snoozes++;
            }
        }
        List<Row> out = new ArrayList<Row>(byRing.values());
        Collections.sort(out, (a, b) -> a.dueAt > b.dueAt ? -1 : a.dueAt < b.dueAt ? 1 : 0);
        return out;
    }

    /** "Gotovo u 08:03", "Gotovo u 09:40 · odgođeno 2×", "Propušteno", "Odgođeno". */
    public static String describe(Row r, String doneTime) {
        String snoozed = r.snoozes == 0 ? "" : " · odgođeno " + r.snoozes + "×";
        switch (r.outcome) {
            case DONE: return "Gotovo u " + doneTime + snoozed;
            case MISSED: return "Propušteno" + snoozed;
            default: return r.snoozes > 0 ? "Odgođeno " + r.snoozes + "×" : "Bez odgovora";
        }
    }
}
