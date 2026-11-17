package ba.infotablet.sources;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ViK Sarajevo service notices (list page, full text included) and matching against the streets and
 * neighbourhoods set on the device. Payload {notices:[{id,posted,title,body,kind,days:[...]}]}.
 */
public final class WaterParser {

    private static final Pattern POSTED = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})\\s*u\\s*(\\d{1,2}):(\\d{2})");
    private static final Pattern DATE = Pattern.compile("(\\d{1,2})\\.\\s*(\\d{1,2})\\.\\s*(\\d{4})");
    private static final Pattern DAY = Pattern.compile("(\\d{1,2})\\.");
    private static final Pattern ID = Pattern.compile("/clanak/(\\d+)");
    private static final String[] TAIL = {"Normalizaciju u vodosnabdijevanju", "Za sve informacije", "Na sve informacije", "Za sve dodatne informacije"};

    private WaterParser() {}

    public static JSONObject parse(String html) throws JSONException {
        Document d = Jsoup.parse(html, "https://www.viksa.ba");
        JSONArray notices = new JSONArray();
        for (Element item : d.select("div.service-item-top")) {
            Element date = item.selectFirst("p.fs-6");
            Element title = item.selectFirst("h5.news-card-title");
            Element body = item.selectFirst("p.fw-light");
            Element link = item.selectFirst("a[href^=/clanak/]");
            if (title == null || body == null) continue;
            String t = title.text().replaceAll("\\s+", " ").trim();
            String text = stripTail(body.text().replaceAll("\\s+", " ").trim());
            JSONObject n = new JSONObject()
                    .put("id", link == null ? 0 : idOf(link.attr("href")))
                    .put("posted", date == null ? 0 : posted(date.text()))
                    .put("title", t)
                    .put("body", text)
                    .put("kind", t.toLowerCase(Locale.ROOT).contains("kanalizacion") ? "kanalizacija" : "voda");
            JSONArray days = new JSONArray();
            for (Long day : targetDays(t)) days.put(day);
            n.put("days", days);
            notices.put(n);
        }
        if (notices.length() == 0) throw new JSONException("No service notices found");
        return new JSONObject().put("notices", notices);
    }

    /** Local midnights of the days a notice is about: "za dan 17.11.2026." or "za dane 14., 15. i 16.11.2026.". */
    static List<Long> targetDays(String title) {
        List<Long> out = new ArrayList<Long>();
        Matcher m = DATE.matcher(title);
        int end = -1, month = 0, year = 0, lastDay = 0;
        while (m.find()) { // the last full date of the title carries month and year
            end = m.start();
            lastDay = Integer.parseInt(m.group(1));
            month = Integer.parseInt(m.group(2));
            year = Integer.parseInt(m.group(3));
        }
        if (end < 0) return out;
        int from = title.toLowerCase(Locale.ROOT).lastIndexOf("za dan", end);
        if (from >= 0) {
            Matcher dm = DAY.matcher(title.substring(from, end));
            while (dm.find()) out.add(midnight(year, month, Integer.parseInt(dm.group(1))));
        }
        out.add(midnight(year, month, lastDay));
        return out;
    }

    /** True when the text mentions one of the configured places (comma separated, aliases with "|"). */
    public static boolean matches(String text, String places) {
        if (places == null || places.trim().isEmpty()) return false;
        String[] tokens = normalize(text).split(" ");
        for (String place : places.split("[,;\\n]")) {
            for (String alias : place.split("\\|")) {
                String p = normalize(alias);
                if (!p.isEmpty() && contains(tokens, p.split(" "))) return true;
            }
        }
        return false;
    }

    /**
     * The part of a notice that concerns the configured places: the sentence that mentions them,
     * starting at its street list ("... u slijedećim ulicama: Orahov brijeg, ...") when there is one.
     */
    public static String relevantPart(String body, String places) {
        for (String sentence : ba.infotablet.util.Sentences.split(body)) {
            if (!matches(sentence, places)) continue;
            int colon = sentence.indexOf(':');
            String lower = sentence.toLowerCase(Locale.ROOT);
            if (colon > 0 && lower.lastIndexOf("ulicama", colon) >= 0) {
                return "Ulice: " + sentence.substring(colon + 1).trim();
            }
            return sentence;
        }
        return body;
    }

    /** Lower case, no diacritics, punctuation to spaces, without "ul."/"ulica". */
    static String normalize(String s) {
        String t = s.toLowerCase(Locale.ROOT)
                .replace('č', 'c').replace('ć', 'c').replace('š', 's').replace('ž', 'z').replace("đ", "dj");
        t = t.replaceAll("[\\-\u2013\u2014.,;:()\"'„“”]", " ");
        t = t.replaceAll("\\b(ul|ulica|ulici|ulicama)\\b", " ");
        return t.replaceAll("\\s+", " ").trim();
    }

    /** Whole-word sequence match, tolerant of Bosnian case endings (see wordMatches). */
    private static boolean contains(String[] tokens, String[] want) {
        outer:
        for (int i = 0; i + want.length <= tokens.length; i++) {
            for (int k = 0; k < want.length; k++) {
                if (!wordMatches(tokens[i + k], want[k])) continue outer;
            }
            return true;
        }
        return false;
    }

    /** Case endings: 7+ letters may differ in the last two, 5 to 6 letters in the last one, shorter words not at all. */
    private static boolean wordMatches(String token, String want) {
        if (token.equals(want)) return true;
        if (want.length() < 5) return false;
        String stem = want.substring(0, want.length() - (want.length() >= 7 ? 2 : 1));
        return token.startsWith(stem) && token.length() <= want.length() + 3;
    }

    private static String stripTail(String s) {
        int cut = s.length();
        for (String t : TAIL) {
            int i = s.indexOf(t);
            if (i > 0) cut = Math.min(cut, i);
        }
        return s.substring(0, cut).trim();
    }

    private static long posted(String s) {
        Matcher m = POSTED.matcher(s);
        if (!m.find()) return 0;
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)) - 1, Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)));
        return c.getTimeInMillis();
    }

    private static long midnight(int y, int m, int d) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, d);
        return c.getTimeInMillis();
    }

    private static int idOf(String href) {
        Matcher m = ID.matcher(href);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }
}
