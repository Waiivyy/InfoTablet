package ba.infotablet.calendar;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HolidaysTest {

    private static List<Holidays.Day> table() throws Exception {
        return Holidays.parse(new String(Files.readAllBytes(new File("src/main/assets/holidays.csv").toPath()), "UTF-8"));
    }

    private static long at(int y, int m, int d) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, d, 1, 0);
        return c.getTimeInMillis();
    }

    private static boolean has(List<Holidays.Day> days, int y, int m, int d, String name) {
        Calendar c = Calendar.getInstance();
        for (Holidays.Day day : days) {
            c.setTimeInMillis(day.date);
            if (c.get(Calendar.YEAR) == y && c.get(Calendar.MONTH) + 1 == m && c.get(Calendar.DAY_OF_MONTH) == d && day.name.startsWith(name)) return true;
        }
        return false;
    }

    @Test
    public void computedEasterMatchesTheTable() throws Exception {
        List<Holidays.Day> days = table();
        for (int y = 2026; y <= 2030; y++) {
            int[] cat = Easter.catholic(y), orth = Easter.orthodox(y);
            assertTrue("Catholic Easter " + y, has(days, y, cat[0], cat[1], "Uskrs"));
            assertTrue("Orthodox Easter " + y, has(days, y, orth[0], orth[1], "Vaskrs"));
        }
        assertEquals(4, Easter.catholic(2026)[0]);
        assertEquals(5, Easter.catholic(2026)[1]);
        assertEquals(5, Easter.orthodox(2027)[0]);
        assertEquals(2, Easter.orthodox(2027)[1]);
    }

    @Test
    public void stateDaysAndSundayCarryOver() throws Exception {
        List<Holidays.Day> days = table();
        assertTrue(has(days, 2026, 11, 25, "Dan državnosti"));
        assertTrue(has(days, 2027, 5, 3, "Praznik rada (prenesen dan)"));
        assertTrue(has(days, 2028, 1, 3, "Nova godina (prenesen dan)"));
        for (Holidays.Day d : days) {
            if (d.name.startsWith("Dan državnosti") || d.name.startsWith("Nova godina")) assertTrue(d.state);
            if (d.name.startsWith("Ramazanski") || d.name.startsWith("Božić")) assertFalse(d.state);
        }
    }

    @Test
    public void nextHolidayAndCountdown() throws Exception {
        List<Holidays.Day> days = table();
        Holidays.Day next = Holidays.next(days, at(2026, 11, 17), 30);
        assertNotNull(next);
        assertEquals("Dan državnosti", next.name);
        assertEquals(8, Holidays.daysUntil(at(2026, 11, 17), next.date));
        assertEquals("za 8 dana", Holidays.countdown(8));
        assertEquals("za 21 dan", Holidays.countdown(21));
        assertEquals("sutra", Holidays.countdown(1));
        assertEquals("danas", Holidays.countdown(0));
        // On the same date the state day comes first.
        assertTrue(Holidays.next(days, at(2027, 5, 2), 0).state);
    }

    @Test
    public void provisionalIslamicDatesAreMarked() throws Exception {
        for (Holidays.Day d : table()) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(d.date);
            if (d.name.startsWith("Ramazanski") && c.get(Calendar.YEAR) >= 2027) assertTrue(d.provisional);
            if (d.name.startsWith("Ramazanski") && c.get(Calendar.YEAR) == 2026) assertFalse(d.provisional);
        }
    }
}
