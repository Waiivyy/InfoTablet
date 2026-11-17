package ba.infotablet.util;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;

public class FmtTest {

    private static long at(int y, int m, int d, int h, int min) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, d, h, min);
        return c.getTimeInMillis();
    }

    @Test
    public void dayOffsetAndRelativeDays() {
        long now = at(2026, 11, 17, 1, 59);
        assertEquals(-1, Fmt.dayOffset(now, at(2026, 11, 16, 19, 48)));
        assertEquals(0, Fmt.dayOffset(now, at(2026, 11, 17, 23, 0)));
        assertEquals(1, Fmt.dayOffset(now, at(2026, 11, 18, 0, 0)));
        assertEquals("jučer 19:48", Fmt.when(now, at(2026, 11, 16, 19, 48)));
        assertEquals("sutra", Fmt.relDay(now, at(2026, 11, 18, 15, 0)));
        // Across the end of daylight saving time (25-hour day).
        assertEquals(1, Fmt.dayOffset(at(2026, 10, 24, 12, 0), at(2026, 10, 25, 12, 0)));
    }

    @Test
    public void numbersAndPlurals() {
        assertEquals("4,8", Fmt.dec1(4.8));
        assertEquals("3", Fmt.dec1(3.0));
        assertEquals("3,58", Fmt.dec2(3.58));
        assertEquals("-2°", Fmt.temp(-2.4));
        assertEquals("0°", Fmt.temp(-0.3));
        assertEquals("1 pumpa", Fmt.plural(1, "pumpa", "pumpe", "pumpi"));
        assertEquals("3 pumpe", Fmt.plural(3, "pumpa", "pumpe", "pumpi"));
        assertEquals("12 pumpi", Fmt.plural(12, "pumpa", "pumpe", "pumpi"));
        assertEquals("22 pumpe", Fmt.plural(22, "pumpa", "pumpe", "pumpi"));
        assertEquals("21 pumpa", Fmt.plural(21, "pumpa", "pumpe", "pumpi"));
    }
}
