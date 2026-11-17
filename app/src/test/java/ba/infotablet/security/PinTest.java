package ba.infotablet.security;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class PinTest {

    @Test
    public void hashAndMatch() {
        String stored = Pin.hash("4821");
        assertTrue(Pin.matches("4821", stored));
        assertFalse(Pin.matches("4822", stored));
        assertFalse(stored.contains("4821"));
        assertNotEquals(stored, Pin.hash("4821")); // random salt
    }

    @Test
    public void validPins() {
        assertTrue(Pin.valid("0000"));
        assertTrue(Pin.valid("12345678"));
        assertFalse(Pin.valid("123"));
        assertFalse(Pin.valid("123456789"));
        assertFalse(Pin.valid("12a4"));
    }

    @Test
    public void locksAfterFiveWrongAttempts() {
        String stored = Pin.hash("1111");
        long t = 1_000_000L;
        for (int i = 0; i < 5; i++) assertFalse(Pin.attempt("2222", stored, t));
        assertTrue(Pin.lockedFor(t) > 0);
        assertFalse(Pin.attempt("1111", stored, t + 1000)); // right PIN, but locked
        assertEquals(0, Pin.lockedFor(t + 5 * 60_000L));
        assertTrue(Pin.attempt("1111", stored, t + 5 * 60_000L));
    }
}
