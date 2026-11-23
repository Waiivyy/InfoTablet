package ba.infotablet.sources;

import org.json.JSONObject;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import ba.infotablet.data.SourceState;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RatesSourceTest {

    private final RatesSource source = new RatesSource();

    private static long at(String local) throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("Europe/Sarajevo"));
        return f.parse(local).getTime();
    }

    private static SourceState state(String listDay, String attempt) throws Exception {
        String payload = new JSONObject().put("date", at(listDay + " 00:00")).put("number", 1).put("rates", new JSONObject()).toString();
        long t = at(attempt);
        return new SourceState(RatesSource.ID, payload, t, t, 0, 0, null);
    }

    @Test
    public void aTabletBackAfterDaysOffFetchesTodaysListAtOnce() throws Exception {
        // Thursday's fetch brought Friday's list; the tablet was off until Monday afternoon.
        long now = at("2026-11-23 15:47");
        assertTrue(source.dueAt(state("2026-11-20", "2026-11-19 16:05"), now) <= now);
    }

    @Test
    public void oneCatchUpADayThenTheUsualAfternoonCheck() throws Exception {
        // Fetched again on Monday: the list did not change (or the weekend has none), so wait for 16:05.
        assertEquals(at("2026-11-23 16:05"), source.dueAt(state("2026-11-20", "2026-11-23 15:48"), at("2026-11-23 15:50")));
        assertEquals(at("2026-11-28 16:05"), source.dueAt(state("2026-11-27", "2026-11-28 00:05"), at("2026-11-28 09:00")));
    }

    @Test
    public void aCurrentListWaitsForTheNextOne() throws Exception {
        assertEquals(at("2026-11-23 16:05"), source.dueAt(state("2026-11-23", "2026-11-22 16:05"), at("2026-11-23 09:00")));
        assertEquals(at("2026-11-24 16:05"), source.dueAt(state("2026-11-24", "2026-11-23 16:05"), at("2026-11-23 16:06")));
        // After 16:05 without tomorrow's list: every 30 minutes until 19:00.
        assertEquals(at("2026-11-23 16:35"), source.dueAt(state("2026-11-23", "2026-11-23 16:05"), at("2026-11-23 16:10")));
    }
}
