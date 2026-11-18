package ba.infotablet.reminders;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class HistoryTest {

    private static final long MIN = 60_000L;
    private static final long T = 1_800_000_000_000L;   // a ring time

    private static History.Event e(long id, long due, String kind, long at) {
        return new History.Event(id, due, "Lijek", "lijek", kind, at);
    }

    @Test
    public void doneAfterSnoozes() {
        List<History.Row> rows = History.rows(Arrays.asList(
                e(1, T, History.SNOOZE, T + 2 * MIN),
                e(1, T, History.SNOOZE, T + 13 * MIN),
                e(1, T, History.DONE, T + 24 * MIN)));
        assertEquals(1, rows.size());
        History.Row r = rows.get(0);
        assertEquals(History.Outcome.DONE, r.outcome);
        assertEquals(2, r.snoozes);
        assertEquals(24, r.lateMinutes());
        assertEquals("Gotovo u 08:24 · odgođeno 2×", History.describe(r, "08:24"));
    }

    @Test
    public void missedThenDoneLaterCountsAsDone() {
        List<History.Row> rows = History.rows(Arrays.asList(
                e(1, T, History.MISSED, T + 30 * MIN),
                e(1, T, History.DONE, T + 90 * MIN)));
        assertEquals(History.Outcome.DONE, rows.get(0).outcome);
    }

    @Test
    public void missedStaysMissed() {
        History.Row r = History.rows(Collections.singletonList(e(1, T, History.MISSED, T + 30 * MIN))).get(0);
        assertEquals(History.Outcome.MISSED, r.outcome);
        assertEquals("Propušteno", History.describe(r, ""));
    }

    @Test
    public void oneRowPerRingNewestFirst() {
        List<History.Row> rows = History.rows(Arrays.asList(
                e(1, T, History.DONE, T + MIN),
                e(2, T + 60 * MIN, History.SNOOZE, T + 61 * MIN),
                e(1, T + 1440 * MIN, History.DONE, T + 1441 * MIN)));
        assertEquals(3, rows.size());
        assertEquals(T + 1440 * MIN, rows.get(0).dueAt);
        assertEquals(History.Outcome.OPEN, rows.get(1).outcome);
        assertEquals("Odgođeno 1×", History.describe(rows.get(1), ""));
        assertEquals(T, rows.get(2).dueAt);
    }
}
