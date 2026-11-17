package ba.infotablet.sources;

import org.json.JSONObject;
import org.junit.Test;

import ba.infotablet.Fixtures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RatesParserTest {

    @Test
    public void listDateNumberAndRates() throws Exception {
        JSONObject r = RatesParser.parse(Fixtures.read("cbbh_2026-11-17.json"));
        assertEquals(1794870000000L, r.getLong("date")); // 17.11.2026 00:00 Sarajevo
        assertEquals(226, r.getInt("number"));
        JSONObject rates = r.getJSONObject("rates");
        assertEquals(1.775767, rates.getJSONObject("USD").getDouble("middle"), 1e-9);
        assertEquals(2.115783, rates.getJSONObject("CHF").getDouble("middle"), 1e-9);
        assertEquals(1.955830, rates.getJSONObject("EUR").getDouble("middle"), 1e-9);
        assertEquals(100, rates.getJSONObject("RSD").getInt("units"));
        assertFalse(rates.getJSONObject("XDR").has("buy")); // empty strings in the source
    }

    @Test(expected = org.json.JSONException.class)
    public void emptyListFails() throws Exception {
        RatesParser.parse("{\"CurrencyExchangeItems\":[],\"Date\":\"2026-11-17T00:00:00\",\"Number\":1}");
    }
}
