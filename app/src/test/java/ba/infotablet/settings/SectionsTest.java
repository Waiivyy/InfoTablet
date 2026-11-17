package ba.infotablet.settings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SectionsTest {

    @Test
    public void tileStepperSkipsTilesUsedInOtherSlots() {
        String[] tiles = {"reminders", "fuel", "news"};
        assertEquals("city2", Sections.nextFree(tiles, 0, 1));   // fuel and news are taken
        assertEquals("air", Sections.nextFree(tiles, 0, -1));    // wraps backwards
        assertEquals("city2", Sections.nextFree(tiles, 2, 1));
    }
}
