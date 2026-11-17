package ba.infotablet.sources;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.kxml2.io.KXmlParser;

import java.util.Calendar;
import java.util.List;

import ba.infotablet.Fixtures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UtilitiesParsersTest {

    private static int dayOfMonth(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return c.get(Calendar.DAY_OF_MONTH);
    }

    // ---- ViK water works ----

    @Test
    public void vikNoticesWithDaysKindAndCleanBody() throws Exception {
        JSONArray n = WaterParser.parse(Fixtures.read("viksa_list.html")).getJSONArray("notices");
        assertEquals(10, n.length());
        JSONObject first = n.getJSONObject(0);
        assertEquals(12146, first.getInt("id"));
        assertEquals("voda", first.getString("kind"));
        assertEquals(17, dayOfMonth(first.getJSONArray("days").getLong(0)));
        assertTrue(first.getString("body").contains("Orahov brijeg"));
        assertFalse(first.getString("body").contains("Normalizaciju"));
        assertEquals("kanalizacija", n.getJSONObject(1).getString("kind"));
        JSONArray weekend = n.getJSONObject(3).getJSONArray("days");
        assertEquals(3, weekend.length()); // "za dane 14., 15. i 16.11.2026."
        assertEquals(14, dayOfMonth(weekend.getLong(0)));
        assertEquals(16, dayOfMonth(weekend.getLong(2)));
    }

    @Test
    public void streetMatchingToleratesDiacriticsCaseEndingsAndAliases() {
        String t = "Bez vode će ostati potrošači u ulicama: Kobilja glava (dio), Ejuba Delalića, kao i dio Orahovog brijega. Montaža priključaka u Rakovici.";
        assertTrue(WaterParser.matches(t, "Orahov brijeg"));
        assertTrue(WaterParser.matches(t, "Rakovica"));
        assertTrue(WaterParser.matches(t, "ejuba delalica"));
        assertTrue(WaterParser.matches(t, "Kobilja Glava"));
        assertTrue(WaterParser.matches("u slijedećim ulicama:Orahov brijeg, Kačanik mali", "Mali Kačanik|Kačanik mali"));
        assertFalse(WaterParser.matches(t, "Ilidža"));
        assertFalse(WaterParser.matches(t, ""));
        assertFalse(WaterParser.matches("Ulica Mala", "Malta")); // short words must match exactly
    }

    @Test
    public void relevantPartStartsAtTheStreetList() {
        String body = "U okviru redovnih aktivnosti izvodit će se radovi zbog čega može doći do prekida vodosnabdijevanja u slijedećim ulicama:Orahov brijeg, Smaila Šikala i Dola. Normalizacija se očekuje u popodnevnim satima.";
        assertEquals("Ulice: Orahov brijeg, Smaila Šikala i Dola.", WaterParser.relevantPart(body, "Smaila Šikala"));
    }

    // ---- Smog episodes (real notices of January 2026) ----

    @Test
    public void episodeSequenceFromJanuary2026() throws Exception {
        JSONObject z = new JSONObject();
        SmogParser.apply(z, "Proglašena epizoda „Upozorenje\" za zone A, B i C",
                "Ministarstvo je donijelo Naredbu da se u zonama A, B i C proglašava epizoda „Upozorenje\".");
        assertEquals("UPOZORENJE", SmogParser.current(z));
        assertEquals(3, z.length());

        SmogParser.apply(z, "Vlada KS proglasila epizodu \"Uzbuna\" za Zonu A",
                "Na prijedlog Ministarstva, Vlada Kantona Sarajevo donijela je odluku o proglašenju epizode \"Uzbuna\" za Zonu A "
                        + "(Novi Grad, Centar, Stari Grad, Novo Sarajevo i Ilidža), dok u zonama B i C na snazi ostaje epizoda \"Upozorenje\".");
        assertEquals("UZBUNA", z.getString("A"));
        assertEquals("UPOZORENJE", z.getString("B"));
        assertEquals("UPOZORENJE", z.getString("C"));

        SmogParser.apply(z, "Ukinute epizode \"Uzbuna\" i \"Upozorenje\" u Kantonu Sarajevo",
                "Vlada Kantona Sarajevo donijela je Odluku o ukidanju epizode \"Uzbuna\" koja je od petka bila na snazi na području "
                        + "četiri gradske općine i općine Ilidža. Ministarstvo je ukinulo i \"Upozorenje\" na cijelom području Kantona. "
                        + "Time prestaju važiti sve ranije propisane restriktivne mjere.");
        assertNull(SmogParser.current(z));

        SmogParser.apply(z, "Zbog pogoršanja kvaliteta zraka proglašena epizoda „Upozorenje\" za veći dio Kantona Sarajevo",
                "Ispunjeni su uslovi za današnje proglašenje epizode „Upozorenje\" za zone A, B, C i D (gradsko područje Sarajeva, Ilidža, Vogošća, Ilijaš i Hadžići).");
        assertEquals(4, z.length());
        assertEquals("UPOZORENJE", z.getString("D"));

        SmogParser.apply(z, "Ministarstvo ukinulo epizodu \"Upozorenje\" u Kantonu Sarajevo",
                "Ministarstvo je ukinulo epizodu \"Upozorenje\" za cijelo područje Kantona Sarajevo, uslijed poboljšanja kvaliteta zraka.");
        assertNull(SmogParser.current(z));
    }

    @Test
    public void liftingUzbunaLeavesUpozorenje() throws Exception {
        JSONObject z = new JSONObject().put("A", "UZBUNA");
        SmogParser.apply(z, "Vlada KS ukinula „Uzbunu\", a na snazi ostavila „Upozorenje\"", "Kvalitet zraka je poboljšan.");
        assertEquals("UPOZORENJE", z.getString("A"));
    }

    @Test
    public void weatherAndCivilProtectionWarningsAreNotEpisodes() {
        assertFalse(SmogParser.isCandidate("Upozorenje za preduzimanje preventivnih mjera zaštite i spašavanja od jakog vjetra i kišnih padavina", ""));
        assertFalse(SmogParser.isCandidate("KUCZ KS: UPOZORENJE PRAVNIM I FIZIČKIM LICIMA ZA PODUZIMANJE PREVENTIVNIH MJERA", ""));
        assertFalse(SmogParser.isCandidate("Kanton Sarajevo dobija digitalni sistem za efikasniju kontrolu emisija u zrak", ""));
        assertTrue(SmogParser.isCandidate("Vlada KS proglasila epizodu \"Uzbuna\" za Zonu A", ""));
        assertTrue(SmogParser.isCandidate("Vlada KS ukinula „Uzbunu\"", "Zbog poboljšanja kvaliteta zraka ..."));
    }

    @Test
    public void newsListItems() {
        String html = "<div id='block-gavias-vitaco-content'>"
                + "<div class='views-row'><div class='views-field-uid'><span class='field-content'>16.01.2026 (urednik)</span></div>"
                + "<div class='views-field-title'><h3 class='field-content'><a href='/node/127287'>Vlada KS proglasila epizodu \"Uzbuna\" za Zonu A</a></h3></div>"
                + "<div class='views-field-body'><div class='field-content'><p>Na prijedlog Ministarstva...</p></div></div></div></div>";
        List<SmogParser.Notice> l = SmogParser.list(html);
        assertEquals(1, l.size());
        assertEquals(127287, l.get(0).node);
        assertEquals("https://vlada.ks.gov.ba/node/127287", l.get(0).href);
        assertEquals(16, dayOfMonth(l.get(0).date));
    }

    // ---- BIHAMK ----

    @Test
    public void bihamkCategoriesBordersAndUpdateTime() throws Exception {
        JSONArray cats = RoadsParser.rss(new KXmlParser(), Fixtures.read("bihamk_rss.xml")).getJSONArray("categories");
        assertEquals("Granični prijelazi", cats.getJSONObject(0).getString("name"));
        JSONArray borders = cats.getJSONObject(0).getJSONArray("entries");
        assertTrue(borders.length() > 40);
        assertTrue(borders.getJSONObject(0).getString("text").startsWith("Zadržavanja putničkih vozila"));
        assertEquals(5, cats.length());
        JSONObject page = RoadsParser.page(Fixtures.read("bihamk_page_excerpt.html"));
        assertEquals(1794854894000L, page.getLong("updated")); // 16.11.2026 19:48:14 Sarajevo
        assertTrue(page.getJSONArray("summary").length() > 0);
    }
}
