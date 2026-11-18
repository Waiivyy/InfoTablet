package ba.infotablet.reminders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * What is due, ringing, snoozed or coming up. An occurrence waits for "Gotovo" for up to 12 hours;
 * after that a one-time reminder counts as finished and a repeating one moves on to its next date.
 */
public final class Agenda {

    public static final long NONE = Recurrence.NONE;
    public static final long SNOOZE_MS = 10 * 60_000L;
    /** One ring lasts this long; unanswered, it snoozes, and after MAX_RINGS it goes quiet. */
    public static final long RING_MS = 5 * 60_000L;
    public static final int MAX_RINGS = 3;
    public static final long MISSED_WINDOW_MS = 12 * 3600_000L;
    /** Finished one-time reminders stay in the list (dimmed) this long. */
    public static final long KEEP_FINISHED_MS = 30L * 86_400_000L;

    public enum State { UPCOMING, RINGING, SNOOZED, MISSED }

    public static final class Entry {
        public final Reminder r;
        public final long at;
        public final State state;

        Entry(Reminder r, long at, State state) {
            this.r = r;
            this.at = at;
            this.state = state;
        }
    }

    private Agenda() {}

    /** The occurrence waiting for "Gotovo", or NONE. For repeating reminders only the latest one counts. */
    public static long due(Reminder r, long now) {
        if (!r.ring) return NONE;   // plans never wait for "Gotovo"
        long occ = Recurrence.firstAfter(r, r.handledUntil);
        if (occ > now) return NONE;
        long last = r.repeat == Reminder.ONCE ? occ : Recurrence.lastAtOrBefore(r, now);
        return now - last > MISSED_WINDOW_MS ? NONE : last;
    }

    public static State state(Reminder r, long due, long now) {
        if (due == NONE) return State.UPCOMING;
        if (r.snoozeFor == due && r.snoozeUntil > now) {
            return r.snoozeUntil == Long.MAX_VALUE ? State.MISSED : State.SNOOZED;
        }
        return State.RINGING;
    }

    /** The next occurrence ahead of now that has not been handled yet. */
    public static long next(Reminder r, long now) {
        if (r.allDay) return Recurrence.firstAfter(r, ba.infotablet.util.Fmt.startOfDay(now) - 1);   // stays up all day
        if (!r.ring) return Recurrence.firstAfter(r, now);
        return Recurrence.firstAfter(r, Math.max(r.handledUntil, now));
    }

    public static boolean finished(Reminder r, long now) {
        return r.repeat == Reminder.ONCE && due(r, now) == NONE && next(r, now) == NONE;
    }

    /** Reminders ringing now, earliest first. */
    public static List<Reminder> ringing(List<Reminder> all, long now) {
        List<Entry> out = new ArrayList<Entry>();
        for (Reminder r : all) {
            long d = due(r, now);
            if (d != NONE && state(r, d, now) == State.RINGING) out.add(new Entry(r, d, State.RINGING));
        }
        sort(out);
        List<Reminder> list = new ArrayList<Reminder>();
        for (Entry e : out) list.add(e.r);
        return list;
    }

    /**
     * When the alarm must fire next: a snooze ending or a new occurrence. Reminders that are ringing
     * now are left to the alarm screen, which snoozes them when nobody answers.
     */
    public static long nextWake(List<Reminder> all, long now) {
        long best = NONE;
        for (Reminder r : all) {
            if (!r.ring) continue;
            long d = due(r, now);
            if (d != NONE && state(r, d, now) == State.SNOOZED) best = Math.min(best, r.snoozeUntil);
            best = Math.min(best, next(r, now));
        }
        return best;
    }

    /** One entry per reminder: the occurrence waiting for "Gotovo", or the next one within the horizon. */
    public static List<Entry> upcoming(List<Reminder> all, long now, long horizonMs) {
        List<Entry> out = new ArrayList<Entry>();
        for (Reminder r : all) {
            long d = due(r, now);
            if (d != NONE) {
                out.add(new Entry(r, d, state(r, d, now)));
                continue;
            }
            long n = next(r, now);
            if (n != NONE && n - now <= horizonMs) out.add(new Entry(r, n, State.UPCOMING));
        }
        sort(out);
        return out;
    }

    /** Every reminder for the list screen: waiting and upcoming by date, then finished ones (newest first). */
    public static List<Entry> all(List<Reminder> all, long now) {
        List<Entry> open = new ArrayList<Entry>();
        final List<Entry> done = new ArrayList<Entry>();
        for (Reminder r : all) {
            long d = due(r, now);
            if (d != NONE) {
                open.add(new Entry(r, d, state(r, d, now)));
            } else if (finished(r, now)) {
                done.add(new Entry(r, Recurrence.occurrence(r, 0), State.UPCOMING));
            } else {
                open.add(new Entry(r, next(r, now), State.UPCOMING));
            }
        }
        sort(open);
        sort(done);
        Collections.reverse(done);
        open.addAll(done);
        return open;
    }

    /** Scheduled occurrences in [from, to), whatever their state, for the calendar. */
    public static List<Long> occurrences(Reminder r, long from, long to, int max) {
        List<Long> out = new ArrayList<Long>();
        long t = Recurrence.firstAfter(r, from - 1);
        while (t != NONE && t < to && out.size() < max) {
            out.add(t);
            t = Recurrence.firstAfter(r, t);
        }
        return out;
    }

    private static void sort(List<Entry> list) {
        Collections.sort(list, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) {
                if (a.at != b.at) return a.at < b.at ? -1 : 1;
                return a.r.text.compareToIgnoreCase(b.r.text);
            }
        });
    }
}
