package ba.infotablet.sources;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.kxml2.io.KXmlParser;

import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StreetNoticesTest {

    private static long midnight(int y, int m, int d) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, d);
        return c.getTimeInMillis();
    }

    private static final String POWER_HTML = "<table class=\"table iskljucenja_tabela d-none\"><tbody>"
            + "<tr class=\"item\" data-ed=\"edze\" data-opcina=\"zenica\"><td>Zenica</td><td class=\"ulica\">Radakovo</td><td>5A</td><td>18.11.2026</td><td>08:00-11:00</td></tr>"
            + "<tr class=\"item\" data-ed=\"edsa\" data-opcina=\"centar\"><td>Centar</td><td class=\"ulica\">Armije Rbih</td><td>128, 147, 136C</td><td>19.11.2026</td><td>08:30-15:00</td></tr>"
            + "<tr class=\"item\" data-ed=\"edsa\" data-opcina=\"novi-grad\"><td>Novi Grad</td><td class=\"ulica\">Vroce</td><td>BB</td><td>20.11.2026</td><td>09:00-13:30</td></tr>"
            + "</tbody></table>";

    private static final String HEAT_HTML = "<article><div class=\"blank-header\"><h1>Obavijest o radovima</h1>"
            + "<h3>Pregled objekata koji se ne griju na dan 18.11.2026.</h3></div>"
            + "<table class=\"table\"><thead><tr><th>Kotlovnica</th></tr></thead><tbody>"
            + "<tr><td>Kotlovnica Test</td><td>Zmaja od Bosne br. 74</td><td>Banka</td><td>Banka</td>"
            + "<td>Ponedjeljak, 12.10.2026. 05:00</td><td>-</td><td>1</td><td>Kvar u objektu</td></tr></tbody></table></article>";

    private static final String HEAT_HOME = "<div class=\"news\"><a href=\"/novost/1271/saopstenje\"></a><div class=\"title\">Saopštenje</div>"
            + "<div class=\"description\">49 godina</div></div>"
            + "<div class=\"news\"><a href=\"/novost/1270/obavjestenje\"></a><div class=\"title\">OBAVJEŠTENJE</div>"
            + "<div class=\"description\">OBAVJEŠTENJE O PRIVREMENOM PREKIDU ISPORUKE TOPLOTNE ENERGIJE</div></div>";

    private static final String HEAT_ARTICLE = "<article class=\"news\"><div class=\"col-md-9\"><time datetime=\"2026-11-17 15:18:00\">x</time>"
            + "<h1>OBAVJEŠTENJE</h1><h3>OBAVJEŠTENJE O PRIVREMENOM PREKIDU ISPORUKE TOPLOTNE ENERGIJE</h3></div>"
            + "<div class=\"col-lg-9\"><p>Prekid u isporuci obuhvatit će korisnike na sljedećim adresama:</p>"
            + "<p>· Ive Andrića 2,4,6,8</p><p>· Trg međunarodnog prijateljstva 1,2,3</p></div></article>";

    private static final String GAS_RSS = "<?xml version=\"1.0\"?><rss xmlns:content=\"http://purl.org/rss/1.0/modules/content/\"><channel>"
            + "<item><title>Sanacija curenja gasa u Dobrinjskoj ulici</title><link>https://www.sarajevogas.ba/a/</link>"
            + "<pubDate>Tue, 17 Nov 2026 10:00:00 +0100</pubDate><description>Kratko</description>"
            + "<content:encoded><![CDATA[<p>Zbog radova u Dobrinjskoj ulici prekid isporuke gasa od 9 do 14 sati.</p>]]></content:encoded></item>"
            + "<item><title>Stručno usavršavanje</title><link>https://www.sarajevogas.ba/b/</link>"
            + "<pubDate>Wed, 04 Nov 2026 10:00:00 +0100</pubDate><description>Ništa</description></item>"
            + "</channel></rss>";

    @Test
    public void powerKeepsOnlySarajevoRows() throws Exception {
        JSONArray items = UtilityParsers.power(POWER_HTML).getJSONArray("items");
        assertEquals(2, items.length());
        JSONObject first = items.getJSONObject(0);
        assertEquals("Armije Rbih", first.getString("street"));
        assertEquals("08:30", first.getString("from"));
        assertEquals("15:00", first.getString("to"));
        assertEquals(midnight(2026, 11, 19), first.getLong("day"));
    }

    @Test
    public void heatTableAndAnnouncement() throws Exception {
        JSONObject today = UtilityParsers.heatToday(HEAT_HTML);
        assertEquals(midnight(2026, 11, 18), today.getLong("day"));
        JSONObject row = today.getJSONArray("items").getJSONObject(0);
        assertEquals("Zmaja od Bosne br. 74", row.getString("address"));
        assertEquals("", row.getString("end"));

        JSONArray links = UtilityParsers.heatNewsLinks(HEAT_HOME);
        assertEquals(1, links.length());
        assertEquals(1270, links.getJSONObject(0).getInt("id"));

        JSONObject a = UtilityParsers.heatArticle(HEAT_ARTICLE, 1270);
        assertEquals("Obavještenje o privremenom prekidu isporuke toplotne energije", a.getString("title"));
        assertTrue(a.getString("text").contains("Ive Andrića 2,4,6,8"));
    }

    @Test
    public void noticesMatchStreetsAndSortByDay() throws Exception {
        long today = midnight(2026, 11, 18);
        String power = UtilityParsers.power(POWER_HTML).toString();
        String heat = UtilityParsers.heatToday(HEAT_HTML)
                .put("news", new JSONArray().put(UtilityParsers.heatArticle(HEAT_ARTICLE, 1270))).toString();
        String gas = UtilityParsers.gas(NewsParser.parseFeed(new KXmlParser(), GAS_RSS)).toString();

        List<StreetNotices.Notice> all = StreetNotices.all(null, power, heat, gas, "Armije RBiH, Zmaja od Bosne, Ive Andrića, Dobrinjska", today);
        assertEquals(4, all.size());
        assertEquals(StreetNotices.HEAT, all.get(0).kind);          // today, no heating
        assertEquals("Bez grijanja", all.get(0).title);
        assertEquals(StreetNotices.POWER, all.get(1).kind);         // tomorrow, power cut
        assertEquals("Bez struje od 08:30 do 15:00", all.get(1).title);
        assertTrue(all.get(2).announcement && all.get(3).announcement);

        List<StreetNotices.Notice> soon = StreetNotices.soon(all, today);
        assertEquals(4, soon.size());
        assertEquals(0, StreetNotices.all(null, power, heat, gas, "Ferhadija", today).size());
        assertEquals(0, StreetNotices.all(null, power, heat, gas, "", today).size());
    }

    @Test
    public void todaysCutDisappearsWhenItEnds() throws Exception {
        String power = UtilityParsers.power(POWER_HTML).toString();
        long day = midnight(2026, 11, 19);
        assertEquals(1, StreetNotices.all(null, power, null, null, "Armije RBiH", day + 10 * 3600_000L).size());
        assertEquals(0, StreetNotices.all(null, power, null, null, "Armije RBiH", day + 16 * 3600_000L).size());
    }

    @Test
    public void pastAndFarDaysDropOut() throws Exception {
        String power = UtilityParsers.power(POWER_HTML).toString();
        long later = midnight(2026, 11, 20);
        assertEquals(0, StreetNotices.all(null, power, null, null, "Armije RBiH", later).size());   // the 19th is over
        List<StreetNotices.Notice> vroce = StreetNotices.all(null, power, null, null, "Vroće", midnight(2026, 11, 18));
        assertEquals(1, vroce.size());                                                               // diacritics folded
        assertEquals(0, StreetNotices.soon(vroce, midnight(2026, 11, 18)).size());                    // two days ahead: not yet on Početna
    }
}
