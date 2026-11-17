package ba.infotablet.sources;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import ba.infotablet.Fixtures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WarningsParserTest {

    private static final String RAW = Fixtures.read("meteoalarm_ba.json");

    @Test
    public void sarajevoYellowWindSplitAtMidnightIsMergedIntoOnePeriod() throws Exception {
        long now = 1794871800000L; // 17.11.2026 00:30 CET
        JSONArray ws = WarningsParser.parse(RAW, now, "BA006").getJSONArray("warnings");
        assertEquals(1, ws.length());
        JSONObject w = ws.getJSONObject(0);
        assertEquals("BA006", w.getString("region"));
        assertEquals(2, w.getInt("level"));
        assertEquals(1, w.getInt("type"));
        assertEquals("vjetar", w.getString("typeName"));
        assertEquals(1794924000000L, w.getLong("onset"));   // 17.11. 15:00 CET
        assertEquals(1795010459000L, w.getLong("expires")); // 18.11. 15:00:59 CET
        assertTrue(w.getString("headline").contains("Sarajevo"));
    }

    @Test
    public void elsewhereOnlyOrangeAndRed() throws Exception {
        long now = 1794481200000L; // 12.11.2026 12:00 CET, when orange warnings were active
        JSONArray ws = WarningsParser.parse(RAW, now, "BA006").getJSONArray("warnings");
        boolean sawOrangeElsewhere = false;
        for (int i = 0; i < ws.length(); i++) {
            JSONObject w = ws.getJSONObject(i);
            if (!w.getBoolean("home")) {
                assertTrue("non-home warning below orange: " + w, w.getInt("level") >= 3);
                sawOrangeElsewhere = true;
            }
        }
        assertTrue(sawOrangeElsewhere);
    }

    @Test
    public void expiredWarningsAreDropped() throws Exception {
        long farFuture = 1900000000000L;
        assertEquals(0, WarningsParser.parse(RAW, farFuture, "BA006").getJSONArray("warnings").length());
    }

    @Test
    public void onlyBosnianTexts() throws Exception {
        JSONArray ws = WarningsParser.parse(RAW, 1794871800000L, "BA006").getJSONArray("warnings");
        for (int i = 0; i < ws.length(); i++) {
            assertFalse(ws.getJSONObject(i).getString("headline").contains("Warning for"));
        }
    }
}
