package ba.infotablet.sources;

import org.json.JSONObject;
import org.junit.Test;

import ba.infotablet.Fixtures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WeatherParserTest {

    @Test
    public void parsesCurrentHourlyAndDaily() throws Exception {
        JSONObject w = WeatherParser.parse(Fixtures.read("openmeteo_forecast.json"));

        JSONObject c = w.getJSONObject("current");
        assertEquals(1794874500000L, c.getLong("time"));
        assertEquals(7.7, c.getDouble("t"), 1e-9);
        assertEquals(2, c.getInt("code"));
        assertEquals(0, c.getInt("isDay"));

        assertEquals(25, w.getJSONArray("hourly").length());
        JSONObject h0 = w.getJSONArray("hourly").getJSONObject(0);
        assertTrue(h0.has("t") && h0.has("code") && h0.has("pop"));

        assertEquals(7, w.getJSONArray("daily").length());
        JSONObject d0 = w.getJSONArray("daily").getJSONObject(0);
        assertEquals(1794894203000L, d0.getLong("sunrise"));
        assertTrue(d0.getDouble("tmax") > d0.getDouble("tmin"));
    }

    @Test(expected = org.json.JSONException.class)
    public void rejectsGarbage() throws Exception {
        WeatherParser.parse("{\"error\":true,\"reason\":\"x\"}");
    }
}
