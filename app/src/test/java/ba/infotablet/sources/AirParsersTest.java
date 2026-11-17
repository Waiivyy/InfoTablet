package ba.infotablet.sources;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import ba.infotablet.Fixtures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AirParsersTest {

    @Test
    public void camsCurrentPollenAndHourly() throws Exception {
        JSONObject a = AirParsers.cams(Fixtures.read("openmeteo_air.json"));
        assertEquals(1794873600000L, a.getLong("time"));
        assertEquals(41, a.getInt("aqi"));
        assertEquals(17.2, a.getDouble("pm25"), 1e-9);
        assertEquals(0.0, a.getJSONObject("pollen").getDouble("birch"), 1e-9);
        assertEquals(48, a.getJSONArray("hourly").length());
    }

    @Test
    public void fhmzStationsForSarajevoWithMeasurementHour() throws Exception {
        JSONObject s = AirParsers.stations(Fixtures.read("fhmz_eaqi.json"), Fixtures.read("fhmz_pm25.json"),
                Fixtures.read("fhmz_pm10.json"), Fixtures.read("fhmz_home_excerpt.html"));
        assertEquals(1794873600000L, s.getLong("measuredAt")); // 17.11.2026 01:00 Sarajevo
        JSONArray st = s.getJSONArray("stations");
        assertEquals(9, st.length());
        JSONObject otoka = null;
        for (int i = 0; i < st.length(); i++) {
            if (st.getJSONObject(i).getInt("id") == 12) otoka = st.getJSONObject(i);
        }
        assertTrue(otoka != null);
        assertEquals("Otoka", otoka.getString("name"));
        assertEquals(3, otoka.getInt("level"));
        assertEquals(73.098, otoka.getDouble("pm25"), 1e-9);
        assertEquals(75.133, otoka.getDouble("pm10"), 1e-9);
    }

    @Test
    public void midnightIsWrittenAs24() {
        long t = AirParsers.measuredAt("<option value=\"0\" selected title=\"16.11.2026. u 24:00\">");
        assertEquals(1794870000000L, t); // 17.11.2026 00:00 Sarajevo
    }

    @Test
    public void missingHourGivesZero() {
        assertEquals(0L, AirParsers.measuredAt("<html></html>"));
    }
}
