package ba.infotablet.reminders;

import org.junit.Test;

import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecurrenceTest {

    private static final long MIN = 60_000L;

    private static long at(int y, int m, int d, int h, int min) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, d, h, min);
        return c.getTimeInMillis();
    }

    private static Reminder r(int y, int m, int d, int h, int min, int repeat) {
        Reminder r = new Reminder();
        r.id = 1;
        r.text = "test";
        r.year = y;
        r.month = m;
        r.day = d;
        r.hour = h;
        r.minute = min;
        r.repeat = repeat;
        return r;
    }

    @Test
    public void onceHasOneOccurrence() {
        Reminder r = r(2026, 11, 18, 9, 30, Reminder.ONCE);
        assertEquals(at(2026, 11, 18, 9, 30), Recurrence.firstAfter(r, at(2026, 11, 17, 12, 0)));
        assertEquals(Recurrence.NONE, Recurrence.firstAfter(r, at(2026, 11, 18, 9, 30))); // strictly after
        assertEquals(at(2026, 11, 18, 9, 30), Recurrence.lastAtOrBefore(r, at(2026, 11, 18, 9, 30)));
        assertEquals(Recurrence.NONE, Recurrence.lastAtOrBefore(r, at(2026, 11, 18, 9, 29)));
    }

    @Test
    public void dailyKeepsWallClockTimeAcrossDaylightSaving() {
        Reminder r = r(2026, 3, 1, 8, 0, Reminder.DAILY);
        assertEquals(at(2026, 3, 30, 8, 0), Recurrence.firstAfter(r, at(2026, 3, 29, 8, 0)));
        assertEquals(at(2026, 10, 26, 8, 0), Recurrence.firstAfter(r, at(2026, 10, 25, 9, 0)));
        assertEquals(at(2026, 10, 25, 8, 0), Recurrence.lastAtOrBefore(r, at(2026, 10, 25, 23, 59)));
        // Far ahead the lower-bound estimate must still land on the right day.
        assertEquals(at(2036, 3, 1, 8, 0), Recurrence.firstAfter(r, at(2036, 2, 29, 8, 0)));
    }

    @Test
    public void weeklyStaysOnTheWeekday() {
        Reminder r = r(2026, 11, 17, 10, 0, Reminder.WEEKLY); // Tuesday
        long next = Recurrence.firstAfter(r, at(2026, 12, 2, 0, 0));
        assertEquals(at(2026, 12, 8, 10, 0), next);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(Recurrence.firstAfter(r, at(2027, 7, 1, 0, 0)));
        assertEquals(Calendar.TUESDAY, c.get(Calendar.DAY_OF_WEEK));
    }

    @Test
    public void monthlyOnThe31stFallsBackAndReturns() {
        Reminder r = r(2026, 1, 31, 12, 0, Reminder.MONTHLY);
        assertEquals(at(2026, 2, 28, 12, 0), Recurrence.firstAfter(r, at(2026, 2, 1, 0, 0)));
        assertEquals(at(2026, 3, 31, 12, 0), Recurrence.firstAfter(r, at(2026, 3, 1, 0, 0)));
        assertEquals(at(2026, 4, 30, 12, 0), Recurrence.firstAfter(r, at(2026, 4, 1, 0, 0)));
        assertEquals(at(2034, 12, 31, 12, 0), Recurrence.firstAfter(r, at(2034, 12, 30, 0, 0)));
    }

    @Test
    public void yearlyBirthdayOnLeapDay() {
        Reminder r = r(2028, 2, 29, 9, 0, Reminder.YEARLY);
        assertEquals(at(2029, 2, 28, 9, 0), Recurrence.firstAfter(r, at(2028, 3, 1, 0, 0)));
        assertEquals(at(2032, 2, 29, 9, 0), Recurrence.firstAfter(r, at(2031, 3, 1, 0, 0)));
    }

    @Test
    public void validDates() {
        assertTrue(Recurrence.validDate(2028, 2, 29, 0, 0));
        assertFalse(Recurrence.validDate(2027, 2, 29, 0, 0));
        assertFalse(Recurrence.validDate(2026, 4, 31, 0, 0));
        assertFalse(Recurrence.validDate(2026, 4, 30, 24, 0));
    }

    // ---- Agenda ----

    @Test
    public void dueRingSnoozeAndDone() {
        Reminder r = r(2026, 11, 17, 16, 0, Reminder.ONCE);
        r.handledUntil = at(2026, 11, 17, 10, 0); // saved in the morning
        long t = at(2026, 11, 17, 16, 0);
        assertEquals(Agenda.NONE, Agenda.due(r, t - 1));
        assertEquals(t, Agenda.due(r, t));
        assertEquals(Agenda.State.RINGING, Agenda.state(r, t, t));

        r.snoozeFor = t;
        r.snoozeUntil = t + 10 * MIN;
        assertEquals(Agenda.State.SNOOZED, Agenda.state(r, t, t + 5 * MIN));
        assertEquals(Agenda.State.RINGING, Agenda.state(r, t, t + 10 * MIN));
        assertEquals(t + 10 * MIN, Agenda.nextWake(java.util.Collections.singletonList(r), t + 5 * MIN));

        r.snoozeUntil = Long.MAX_VALUE; // went quiet after three unanswered rings
        assertEquals(Agenda.State.MISSED, Agenda.state(r, t, t + 60 * MIN));
        assertEquals(Agenda.NONE, Agenda.nextWake(java.util.Collections.singletonList(r), t + 60 * MIN));

        r.handledUntil = t + 61 * MIN; // Gotovo
        assertEquals(Agenda.NONE, Agenda.due(r, t + 62 * MIN));
        assertTrue(Agenda.finished(r, t + 62 * MIN));
    }

    @Test
    public void silencedDailyReminderRingsAgainNextDay() {
        Reminder r = r(2026, 11, 1, 8, 0, Reminder.DAILY);
        r.handledUntil = at(2026, 11, 16, 9, 0);
        long today = at(2026, 11, 17, 8, 0);
        r.snoozeFor = today;
        r.snoozeUntil = Long.MAX_VALUE;
        assertEquals(Agenda.State.MISSED, Agenda.state(r, Agenda.due(r, today + MIN), today + MIN));
        long tomorrow = at(2026, 11, 18, 8, 0);
        assertEquals(tomorrow, Agenda.nextWake(java.util.Collections.singletonList(r), today + MIN));
        assertEquals(tomorrow, Agenda.due(r, tomorrow));
        assertEquals(Agenda.State.RINGING, Agenda.state(r, tomorrow, tomorrow));
    }

    @Test
    public void staleOccurrencesExpireAfterTwelveHours() {
        Reminder once = r(2026, 11, 17, 9, 0, Reminder.ONCE);
        once.handledUntil = at(2026, 11, 16, 0, 0);
        assertEquals(at(2026, 11, 17, 9, 0), Agenda.due(once, at(2026, 11, 17, 20, 59)));
        assertEquals(Agenda.NONE, Agenda.due(once, at(2026, 11, 17, 21, 1)));
        assertTrue(Agenda.finished(once, at(2026, 11, 17, 21, 1)));

        // A daily reminder after the tablet was off for days: only the latest occurrence counts.
        Reminder daily = r(2026, 11, 1, 8, 0, Reminder.DAILY);
        daily.handledUntil = at(2026, 11, 10, 9, 0);
        assertEquals(at(2026, 11, 17, 8, 0), Agenda.due(daily, at(2026, 11, 17, 10, 0)));
        assertEquals(Agenda.NONE, Agenda.due(daily, at(2026, 11, 17, 21, 0)));
        assertEquals(at(2026, 11, 18, 8, 0), Agenda.next(daily, at(2026, 11, 17, 21, 0)));
    }

    @Test
    public void ringingRemindersAreLeftToTheAlarmScreen() {
        Reminder a = r(2026, 11, 17, 16, 0, Reminder.DAILY);
        a.handledUntil = at(2026, 11, 17, 10, 0);
        long t = at(2026, 11, 17, 16, 1);
        assertEquals(1, Agenda.ringing(java.util.Collections.singletonList(a), t).size());
        // The next wake is tomorrow's occurrence, never "now" (that would fire in a loop).
        assertEquals(at(2026, 11, 18, 16, 0), Agenda.nextWake(java.util.Collections.singletonList(a), t));
    }

    @Test
    public void upcomingIsSortedWithWaitingFirst() {
        long now = at(2026, 11, 17, 14, 32);
        Reminder pill = r(2026, 11, 1, 16, 0, Reminder.DAILY);
        pill.text = "Lijek";
        pill.handledUntil = at(2026, 11, 10, 0, 0);
        Reminder bill = r(2026, 11, 17, 14, 0, Reminder.ONCE);
        bill.text = "Račun";
        bill.handledUntil = at(2026, 11, 17, 8, 0);
        Reminder far = r(2026, 12, 24, 9, 0, Reminder.ONCE);
        far.text = "Daleko";
        List<Agenda.Entry> up = Agenda.upcoming(java.util.Arrays.asList(pill, bill, far), now, 7L * 86_400_000L);
        assertEquals(2, up.size());
        assertEquals("Račun", up.get(0).r.text);
        assertEquals(Agenda.State.RINGING, up.get(0).state);
        assertEquals(at(2026, 11, 17, 16, 0), up.get(1).at);
    }
}
