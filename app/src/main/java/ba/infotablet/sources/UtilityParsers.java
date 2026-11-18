package ba.infotablet.sources;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ba.infotablet.util.Iso;

/**
 * Outage notices besides water: planned power cuts (Elektroprivreda BiH, Sarajevo area), buildings
 * without heating and announced heating cut-offs (Toplane-Sarajevo), and gas works (Sarajevogas).
 * Each parser keeps every notice; matching against the configured streets happens when shown.
 */
public final class UtilityParsers {

    private static final Pattern DMY = Pattern.compile("(\\d{1,2})\\.\\s*(\\d{1,2})\\.\\s*(\\d{4})");
    private static final Pattern HM_RANGE = Pattern.compile("(\\d{1,2}[:.]\\d{2})\\s*-\\s*(\\d{1,2}[:.]\\d{2})");
    private static final Pattern NEWS_ID = Pattern.compile("/novost/(\\d+)/");
    private static final int MAX_TEXT = 1500;

    private UtilityParsers() {}

    // ---- electricity: https://www.epbih.ba/stranica/servisne-informacije ----

    /** Rows of the Sarajevo distribution area: {items:[{area, street, numbers, day, from, to}]}. */
    public static JSONObject power(String html) throws JSONException {
        Document d = Jsoup.parse(html);
        Elements rows = d.select("table.iskljucenja_tabela tr.item");
        if (rows.isEmpty()) throw new JSONException("No outage table found");
        JSONArray items = new JSONArray();
        for (Element tr : rows) {
            if (!"edsa".equals(tr.attr("data-ed"))) continue;
            Elements td = tr.select("> td");
            if (td.size() < 5) continue;
            long day = day(td.get(3).text());
            if (day == 0) continue;
            Matcher hm = HM_RANGE.matcher(td.get(4).text());
            String from = "", to = "";
            if (hm.find()) {
                from = hm.group(1).replace('.', ':');
                to = hm.group(2).replace('.', ':');
            }
            items.put(new JSONObject()
                    .put("area", td.get(0).text().trim())
                    .put("street", td.get(1).text().trim())
                    .put("numbers", cap(td.get(2).text().trim(), 300))
                    .put("day", day)
                    .put("from", from)
                    .put("to", to));
        }
        return new JSONObject().put("items", items);
    }

    // ---- heating: https://www.toplanesarajevo.ba/korisnici/obavijest-o-radovima ----

    /** Buildings without heating today: {day, items:[{boiler, address, start, end, flats, reason}]}. */
    public static JSONObject heatToday(String html) throws JSONException {
        Document d = Jsoup.parse(html);
        Element heading = d.selectFirst("div.blank-header h3");
        Element table = d.selectFirst("article table");
        if (heading == null || table == null) throw new JSONException("No heating table found");
        JSONArray items = new JSONArray();
        for (Element tr : table.select("tbody tr")) {
            Elements td = tr.select("> td");
            if (td.size() < 8) continue;
            items.put(new JSONObject()
                    .put("boiler", td.get(0).text().trim())
                    .put("address", td.get(1).text().trim())
                    .put("start", td.get(4).text().trim())
                    .put("end", dash(td.get(5).text()))
                    .put("flats", td.get(6).text().trim())
                    .put("reason", td.get(7).text().trim()));
        }
        return new JSONObject().put("day", day(heading.text())).put("items", items);
    }

    /** Ids and links of homepage news that sound like a cut-off ("prekid", "isporuk", "obustav"). */
    public static JSONArray heatNewsLinks(String html) throws JSONException {
        Document d = Jsoup.parse(html, "https://www.toplanesarajevo.ba");
        JSONArray out = new JSONArray();
        for (Element n : d.select("div.news")) {
            Element a = n.selectFirst("a[href*=/novost/]");
            if (a == null) continue;
            String text = (n.select(".title").text() + " " + n.select(".description").text()).toLowerCase(Locale.ROOT);
            if (!text.contains("prekid") && !text.contains("isporuk") && !text.contains("obustav")) continue;
            Matcher m = NEWS_ID.matcher(a.attr("href"));
            if (!m.find()) continue;
            out.put(new JSONObject().put("id", Integer.parseInt(m.group(1))).put("url", a.absUrl("href")));
        }
        return out;
    }

    /** One announcement: {id, posted, title, text}. */
    public static JSONObject heatArticle(String html, int id) throws JSONException {
        Document d = Jsoup.parse(html);
        Element article = d.selectFirst("article.news");
        if (article == null) throw new JSONException("No article found");
        Element time = article.selectFirst("time[datetime]");
        Element h3 = article.selectFirst("h3");
        StringBuilder text = new StringBuilder();
        for (Element p : article.select("div.col-lg-9 p")) {
            String t = p.text().replace(' ', ' ').replaceAll("\\s+", " ").trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("·")) t = t.substring(1).trim();
            if (text.length() > 0) text.append('\n');
            text.append(t);
        }
        return new JSONObject()
                .put("id", id)
                .put("posted", time == null ? 0 : Iso.parse(time.attr("datetime").replace(' ', 'T')))
                .put("title", h3 == null ? "" : sentenceCase(h3.text().trim()))
                .put("text", cap(text.toString(), MAX_TEXT));
    }

    // ---- gas: https://www.sarajevogas.ba/feed/ ----

    /** Recent posts: {items:[{title, posted, text, link}]}. */
    public static JSONObject gas(List<NewsParser.Item> feed) throws JSONException {
        JSONArray items = new JSONArray();
        for (NewsParser.Item it : feed) {
            String text = it.content.isEmpty() ? it.summary : it.content;
            items.put(new JSONObject().put("title", it.title).put("posted", it.time)
                    .put("text", cap(text, MAX_TEXT)).put("link", it.link));
        }
        if (items.length() == 0) throw new JSONException("Empty gas feed");
        return new JSONObject().put("items", items);
    }

    // ---- helpers ----

    /** Local midnight of the first d.m.yyyy in the text, or 0. */
    static long day(String s) {
        Matcher m = DMY.matcher(s);
        if (!m.find()) return 0;
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)) - 1, Integer.parseInt(m.group(1)));
        return c.getTimeInMillis();
    }

    private static String dash(String s) {
        String t = s.trim();
        return t.equals("-") || t.equals("–") ? "" : t;
    }

    private static String cap(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1).trim() + "…";
    }

    /** "OBAVJEŠTENJE O PRIVREMENOM PREKIDU" becomes "Obavještenje o privremenom prekidu". */
    static String sentenceCase(String s) {
        int letters = 0, upper = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isLetter(ch)) {
                letters++;
                if (Character.isUpperCase(ch)) upper++;
            }
        }
        if (letters == 0 || upper * 10 < letters * 8) return s;
        String lower = s.toLowerCase(new Locale("bs"));
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
