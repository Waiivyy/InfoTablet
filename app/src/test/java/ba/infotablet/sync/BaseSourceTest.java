package ba.infotablet.sync;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BaseSourceTest {

    private static final long MIN = 60_000L;

    @Test
    public void backoffDoublesAndIsCappedByInterval() {
        long interval = 60 * MIN;
        assertEquals(5 * MIN, BaseSource.backoffMs(1, interval));
        assertEquals(10 * MIN, BaseSource.backoffMs(2, interval));
        assertEquals(20 * MIN, BaseSource.backoffMs(3, interval));
        assertEquals(40 * MIN, BaseSource.backoffMs(4, interval));
        assertEquals(60 * MIN, BaseSource.backoffMs(5, interval));
        assertEquals(60 * MIN, BaseSource.backoffMs(50, interval));
        assertEquals(30 * MIN, BaseSource.backoffMs(5, 30 * MIN));
    }
}
