package ba.infotablet.ui;

import org.junit.Test;

import java.util.Calendar;

import ba.infotablet.Fixtures;
import ba.infotablet.data.SourceState;
import ba.infotablet.sources.WeatherParser;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ThemeTest {

    private static long at(int y, int m, int d, int h, int min) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, d, h, min);
        return c.getTimeInMillis();
    }

    @Test
    public void nightWindowWrapsPastMidnight() {
        assertTrue(Theme.inWindow(23 * 60, 22 * 60, 6 * 60));
        assertTrue(Theme.inWindow(5 * 60 + 59, 22 * 60, 6 * 60));
        assertFalse(Theme.inWindow(6 * 60, 22 * 60, 6 * 60));
        assertFalse(Theme.inWindow(12 * 60, 22 * 60, 6 * 60));
        assertTrue(Theme.inWindow(14 * 60, 13 * 60, 15 * 60));
        assertFalse(Theme.inWindow(10, 600, 600)); // start = end: never
    }

    @Test
    public void sunTimesComeFromTheCachedForecast() throws Exception {
        String payload = WeatherParser.parse(Fixtures.read("openmeteo_forecast.json")).toString();
        SourceState st = new SourceState("weather", payload, 1, 1, 0, 0, null);
        assertArrayEquals(new long[] {1794894203000L, 1794928726000L}, Theme.forecastSun(st, at(2026, 11, 17, 9, 0)));
        assertNull(Theme.forecastSun(st, at(2027, 1, 5, 9, 0))); // not in the forecast: calculated instead
    }

    @Test
    public void accentPresetsKeepStatusColours() {
        Palette violet = Palette.LIGHT.with("ljubicasta", "standard");
        assertEquals(0xFF5A3DAA, violet.accent);
        assertEquals(Palette.LIGHT.ok, violet.ok);
        assertEquals(0x1A5A3DAA, violet.accentTint);
        Palette darkGrid = Palette.DARK.with("petrol", "grid");
        assertTrue(darkGrid.grid);
        assertEquals(0xFF0A0F1A, darkGrid.accentFg);
        assertNotEquals(Palette.LIGHT.bg, Palette.LIGHT.with("kobalt", "contrast").bg);
    }
}
