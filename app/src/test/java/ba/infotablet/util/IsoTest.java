package ba.infotablet.util;

import org.junit.Test;

import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

public class IsoTest {

    @Test
    public void offsetWithColon() {
        assertEquals(1794924000000L, Iso.parse("2026-11-17T15:00:00+01:00"));
    }

    @Test
    public void zuluAndCompactOffset() {
        assertEquals(1794924000000L, Iso.parse("2026-11-17T14:00:00Z"));
        assertEquals(1794924000000L, Iso.parse("2026-11-17T15:00:00+0100"));
    }

    @Test
    public void noOffsetUsesFallbackZone() {
        assertEquals(1794924000000L, Iso.parse("2026-11-17 15:00:00", TimeZone.getTimeZone("Europe/Sarajevo")));
    }

    @Test
    public void garbageIsZero() {
        assertEquals(0L, Iso.parse("not a date"));
        assertEquals(0L, Iso.parse(null));
    }
}
