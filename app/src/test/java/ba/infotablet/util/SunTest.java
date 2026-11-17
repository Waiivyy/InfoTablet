package ba.infotablet.util;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SunTest {

    private static long at(int y, int m, int d, int h, int min) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, d, h, min);
        return c.getTimeInMillis();
    }

    @Test
    public void matchesOpenMeteoForSarajevoWithinTwoMinutes() {
        long[] t = Sun.times(43.8563, 18.4131, at(2026, 11, 17, 12, 0));
        // Compare as longs: assertEquals with a delta would pick the float overload and lose minutes.
        assertTrue(Math.abs(t[0] - 1794894203000L) < 120_000); // 06:43:23 from the Open-Meteo fixture
        assertTrue(Math.abs(t[1] - 1794928726000L) < 120_000); // 16:18:46
    }

    @Test
    public void summerDayIsLong() {
        long[] t = Sun.times(43.8563, 18.4131, at(2026, 6, 21, 12, 0));
        long hours = (t[1] - t[0]) / 3600_000L;
        assertTrue(hours >= 15 && hours <= 16);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t[0]);
        assertEquals(5, c.get(Calendar.HOUR_OF_DAY)); // about 05:05 summer time (solar noon is 12:48)
    }

    @Test
    public void polarNightHasNoSunrise() {
        long[] t = Sun.times(78.22, 15.65, at(2026, 12, 21, 12, 0)); // Svalbard
        assertEquals(0, t[0]);
    }
}
