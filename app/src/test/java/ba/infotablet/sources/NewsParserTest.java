package ba.infotablet.sources;

import org.junit.Test;
import org.kxml2.io.KXmlParser;

import java.util.Arrays;
import java.util.List;

import ba.infotablet.Fixtures;
import ba.infotablet.sources.NewsParser.Item;
import ba.infotablet.sources.NewsParser.Portal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NewsParserTest {

    @Test
    public void rssItemsWithEntitiesCategoriesAndDates() throws Exception {
        List<Item> items = NewsParser.parseFeed(new KXmlParser(), Fixtures.read("news_rss_sample.xml"));
        assertEquals(4, items.size());
        Item first = items.get(0);
        assertEquals("Novi raspored odvoza otpada", first.title);
        assertEquals("https://www.klix.ba/vijesti/bih/novi-raspored/100001", first.link);
        assertEquals("Općina je objavila \"novi\" raspored odvoza otpada za naredni mjesec.", first.summary);
        assertEquals(1794869940000L, first.time); // 16.11.2026 23:59 CET
        assertEquals(Arrays.asList("BiH"), first.categories);
        assertEquals(2, items.get(2).categories.size());
    }

    @Test
    public void atomEntryWithRfc822Updated() throws Exception {
        List<Item> items = NewsParser.parseFeed(new KXmlParser(), Fixtures.read("news_atom_sample.xml"));
        assertEquals(1, items.size());
        Item e = items.get(0);
        assertEquals("\"Nova\" linija tramvaja od ponedjeljka", e.title);
        assertEquals("https://radiosarajevo.ba/vijesti/lokalne-teme/nova-linija/200001", e.link);
        assertEquals(1794867900000L, e.time);
        assertEquals("", e.summary);
        assertEquals(Arrays.asList("Vijesti"), e.categories);
    }

    @Test
    public void crimeAndCategoryRules() {
        assertTrue(NewsParser.isCrime("https://www.klix.ba/vijesti/crna-hronika/x/1", Arrays.<String>asList()));
        assertTrue(NewsParser.isCrime("https://n1info.ba/vijesti/nesreca/", Arrays.asList("Vijesti", "Crna Hronika")));
        assertFalse(NewsParser.isCrime("https://n1info.ba/regija/x/", Arrays.asList("Regija")));
        assertEquals("crna", NewsParser.category("https://radiosarajevo.ba/vijesti/crna-hronika/x/1", Arrays.<String>asList()));
        assertEquals("sarajevo", NewsParser.category("https://radiosarajevo.ba/vijesti/lokalne-teme/x/1", Arrays.<String>asList()));
        assertEquals("sarajevo", NewsParser.category("https://n1info.ba/vijesti/sarajevo/x/", Arrays.<String>asList()));
        assertEquals("bih", NewsParser.category("https://www.klix.ba/vijesti/bih/x/1", Arrays.<String>asList()));
        assertEquals("svijet", NewsParser.category("https://www.klix.ba/vijesti/svijet/x/1", Arrays.<String>asList()));
        assertEquals("ekonomija", NewsParser.category("https://www.klix.ba/biznis/finansije/x/1", Arrays.<String>asList()));
        assertEquals("sport", NewsParser.category("https://n1info.ba/sport/nogomet/x/", Arrays.<String>asList()));
        assertEquals("kultura", NewsParser.category("https://www.klix.ba/magazin/muzika/x/1", Arrays.<String>asList()));
        assertTrue(NewsParser.skip("https://n1info.ba/video/dnevnik/x/"));
        assertTrue(NewsParser.skip("https://radiosarajevo.ba/multimedia/foto/x/1"));
        assertFalse(NewsParser.skip("https://www.klix.ba/vijesti/bih/x/1"));
    }

    @Test
    public void articleLeadsPerPortal() {
        assertEquals("Općina je objavila raspored radova u ulici Komatin. Radovi će trajati do petka, a saobraćaj će biti usporen. "
                        + "Stanari su obaviješteni putem oglasne table.",
                NewsParser.articleLead(Portal.KLIX, Fixtures.read("news_article_klix.html")));
        assertEquals("Posljednji popis stanovništva urađen je 2013. godine. Od tada nema novog popisa. "
                        + "Procjene se razlikuju za više od pola miliona.",
                NewsParser.articleLead(Portal.N1, Fixtures.read("news_article_n1.html")));
        assertEquals("Policijski službenici uhapsili su M. O. iz Sarajeva po potjernici. "
                        + "Hapšenje je izvršeno 15. novembra 2026. godine u 9 sati. Osoba je predata nadležnima.",
                NewsParser.articleLead(Portal.RADIOSARAJEVO, Fixtures.read("news_article_radiosarajevo.html")));
    }

    @Test
    public void emptyArticleGivesEmptyLead() {
        assertEquals("", NewsParser.articleLead(Portal.KLIX, "<html><body><div id='video'></div></body></html>"));
    }

    @Test
    public void summaryFallbackKeepsOnlyCompleteSentences() {
        assertEquals("", NewsParser.summaryFallback("Procjene stručnjaka o uzrocima još nisu pozna"));
        assertEquals("Općina je objavila raspored.", NewsParser.summaryFallback("Općina je objavila raspored."));
    }

    @Test
    public void crimeWordsInHeadlineOrLead() {
        assertTrue(NewsParser.soundsLikeCrime("Bježao policiji pa udario djevojčicu. Trinaestogodišnja djevojčica poginula je kod Aschaffenburga."));
        assertTrue(NewsParser.soundsLikeCrime("Muškarac izboden nožem u Novom Gradu"));
        assertTrue(NewsParser.soundsLikeCrime("Uhapšen osumnjičeni za ubistvo"));
        assertTrue(NewsParser.soundsLikeCrime("Pucnjava na Ilidži"));
        assertFalse(NewsParser.soundsLikeCrime("Ubijeđen sam da će se cijene goriva smanjiti"));
        assertFalse(NewsParser.soundsLikeCrime("Mané se sprema za veliki povratak u Evropu"));
        assertFalse(NewsParser.soundsLikeCrime("Lesnina otvara novu radnju"));
    }
}
