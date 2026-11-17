package ba.infotablet.sources;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import ba.infotablet.Fixtures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FuelParserTest {

    private static final String HTML = Fixtures.read("uhvatipopust_sarajevo_excerpt.html");

    @Test
    public void dateStationsAndDieselTopFive() throws Exception {
        JSONObject f = FuelParser.parse(HTML);
        assertEquals(1794783600000L, f.getLong("dataDate")); // 16.11.2026 00:00 Sarajevo
        assertEquals(30, f.getInt("stations"));

        JSONObject d = f.getJSONObject("fuels").getJSONObject("dizel");
        assertEquals("Dizel", d.getString("label"));
        assertEquals(20, d.getInt("count"));
        assertEquals(3.58, d.getDouble("min"), 1e-9);
        assertEquals(3.87, d.getDouble("max"), 1e-9);
        assertEquals(3.74, d.getDouble("median"), 1e-9);
        assertTrue(d.getBoolean("crossCheck"));

        JSONArray top = d.getJSONArray("top");
        assertEquals(5, top.length());
        assertEquals("Green Oil", top.getJSONObject(0).getString("name"));
        assertEquals("Marka Marulića 4", top.getJSONObject(0).getString("address"));
        assertEquals("Bingo Petrol", top.getJSONObject(1).getString("name"));
        assertEquals("Džemala Bijedića 179 A", top.getJSONObject(1).getString("address"));
        assertFalse(top.getJSONObject(0).getBoolean("outlier"));
        assertEquals("Green Oil", d.getJSONObject("cheapest").getString("name"));
    }

    @Test
    public void lpgOutlierIsMarkedAndSkippedForCheapest() throws Exception {
        JSONObject lpg = FuelParser.parse(HTML).getJSONObject("fuels").getJSONObject("lpg");
        JSONObject first = lpg.getJSONArray("top").getJSONObject(0);
        assertEquals(1.27, first.getDouble("price"), 1e-9);
        assertTrue(first.getBoolean("outlier"));
        assertTrue(lpg.getJSONObject("cheapest").getDouble("price") > 1.27);
        assertTrue(lpg.getBoolean("crossCheck"));
    }

    @Test
    public void allFourFuels() throws Exception {
        JSONObject fuels = FuelParser.parse(HTML).getJSONObject("fuels");
        assertTrue(fuels.has("dizel") && fuels.has("super95") && fuels.has("super98") && fuels.has("lpg"));
        assertEquals(2.79, fuels.getJSONObject("super95").getDouble("min"), 1e-9);
        assertEquals(3.12, fuels.getJSONObject("super98").getDouble("min"), 1e-9);
    }

    @Test
    public void stationNames() {
        assertEquals("Bingo Petrol", FuelParser.cleanName("BINGO PETROL d.o.o. Tuzla"));
        assertEquals("Petrol BH Oil Company", FuelParser.cleanName("PETROL BH OIL COMPANY"));
        assertEquals("El Tarik Oil", FuelParser.cleanName("EL TARIK OIL benzinske stanice d.o.o."));
        assertEquals("Energopetrol", FuelParser.cleanName("ENERGOPETROL D.D."));
        assertEquals("Ti Oil", FuelParser.cleanName("TI OIL d.o.o."));
        assertEquals("G-Petrol", FuelParser.cleanName("G-Petrol d.o.o."));
        assertEquals("Hifa Oil", FuelParser.cleanName("HIFA OIL d.o.o"));
        assertEquals("Brkić Petrol", FuelParser.cleanName("BRKIĆ PETROL d.o.o."));
        assertEquals("Bucomerc", FuelParser.cleanName("Bucomerc d.o.o."));
    }

    @Test(expected = org.json.JSONException.class)
    public void pageWithoutDataFails() throws Exception {
        FuelParser.parse("<html><body>Održavanje</body></html>");
    }
}
