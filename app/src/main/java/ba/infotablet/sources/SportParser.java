package ba.infotablet.sources;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** nfsbih.ba (Bosnian pages): WWin liga table, a club's season schedule, the national team's matches. */
public final class SportParser {

    private static final Pattern DATE_TIME = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})\\.?\\s*(?:(\\d{1,2}):(\\d{2}))?");
    private static final Pattern TEAM_ID = Pattern.compile("/stats/team/\\d+/(\\d+)-");
    private static final Pattern SCORE = Pattern.compile("(\\d+)\\s*[:\\-]\\s*(\\d+)\\s*$");
    private static final long MATCH_MS = 2 * 3600_000L;

    private SportParser() {}

    /** {title, rows:[{pos,club,teamId,href,penalty,p,w,d,l,gf,ga,gd,pts}]} */
    public static JSONObject table(String html, String baseUrl) throws JSONException {
        Document d = Jsoup.parse(html, baseUrl);
        JSONArray rows = new JSONArray();
        for (Element tr : d.select("#akeeba-renderjoomla table.table-striped tr")) {
            Elements td = tr.select("> td");
            if (td.size() != 10) continue;
            Element a = td.get(2).selectFirst("a");
            if (a == null) continue;
            String full = td.get(2).text().trim();
            String name = a.text().trim();
            String penalty = full.substring(Math.min(full.length(), full.indexOf(name) + name.length())).trim();
            String[] goals = td.get(7).text().split(":");
            JSONObject r = new JSONObject()
                    .put("pos", toInt(td.get(0).text()))
                    .put("club", clubName(name))
                    .put("href", a.absUrl("href"))
                    .put("teamId", teamId(a.attr("href")))
                    .put("penalty", penalty)
                    .put("p", toInt(td.get(3).text()))
                    .put("w", toInt(td.get(4).text()))
                    .put("d", toInt(td.get(5).text()))
                    .put("l", toInt(td.get(6).text()))
                    .put("gf", goals.length == 2 ? toInt(goals[0]) : 0)
                    .put("ga", goals.length == 2 ? toInt(goals[1]) : 0)
                    .put("gd", toInt(td.get(8).text()))
                    .put("pts", toInt(td.get(9).text()));
            rows.put(r);
        }
        if (rows.length() == 0) throw new JSONException("League table not found");
        Element h4 = d.selectFirst("#akeeba-renderjoomla h4");
        return new JSONObject().put("title", h4 == null ? "Premijer liga BiH" : h4.text().trim()).put("rows", rows);
    }

    /** All matches of a club's season page: [{home,away,kickoff,ft}] (ft empty when not played). */
    public static List<JSONObject> clubMatches(String html, long now) throws JSONException {
        Document d = Jsoup.parse(html);
        List<JSONObject> out = new ArrayList<JSONObject>();
        for (Element tr : d.select("#akeeba-renderjoomla table.table-striped tr")) {
            Elements td = tr.select("> td");
            if (td.size() != 7) continue;
            long kickoff = dateTime(td.get(4).text());
            if (kickoff == 0) continue;
            String ft = "";
            Element strong = td.get(5).selectFirst("strong");
            String scoreText = strong != null ? strong.text() : td.get(5).text();
            Matcher m = SCORE.matcher(scoreText.trim());
            // Future matches also read "(0:0) 0:0", so a result only counts once the match is over.
            if (m.find() && kickoff + MATCH_MS < now) ft = m.group(1) + ":" + m.group(2);
            out.add(new JSONObject()
                    .put("home", clubName(td.get(0).text()))
                    .put("away", clubName(td.get(3).text()))
                    .put("kickoff", kickoff)
                    .put("ft", ft));
        }
        return out;
    }

    /** The latest played match (or null) and the earliest upcoming or running match (or null). */
    public static JSONObject[] lastAndNext(List<JSONObject> matches, long now) {
        JSONObject last = null, next = null;
        for (JSONObject m : matches) {
            long k = m.optLong("kickoff");
            if (k + MATCH_MS < now) {
                if (last == null || k > last.optLong("kickoff")) last = m;
            } else if (next == null || k < next.optLong("kickoff")) {
                next = m;
            }
        }
        return new JSONObject[] {last, next};
    }

    /** Next national team match from the countdown box: {home,away,kickoff,competition,venue}, or null. */
    public static JSONObject nationalNext(String html) throws JSONException {
        Document d = Jsoup.parse(html);
        Element box = d.selectFirst("div.content-counter");
        if (box == null) return null;
        Element counter = box.selectFirst("div.counter[data-live-score-start]");
        Elements teams = box.select("ul.club-logo h6");
        if (counter == null || teams.size() < 2) return null;
        long kickoff;
        try {
            kickoff = Long.parseLong(counter.attr("data-live-score-start").trim());
        } catch (NumberFormatException e) {
            return null;
        }
        Elements h5 = box.select("div.text-center h5");
        Element comp = d.selectFirst("div.title-color h4");
        return new JSONObject()
                .put("home", teams.get(0).text().trim())
                .put("away", teams.get(1).text().trim())
                .put("kickoff", kickoff)
                .put("venue", h5.size() >= 2 ? h5.get(1).text().trim() : "")
                .put("competition", comp == null ? "" : competition(comp.text()));
    }

    /** Rows of the national team results article: [{home,away,date,score,competition}] in page order. */
    public static List<JSONObject> nationalResults(String html) throws JSONException {
        Document d = Jsoup.parse(html);
        List<JSONObject> out = new ArrayList<JSONObject>();
        for (Element tr : d.select("div.itemFullText table.table-striped tr")) {
            Elements td = tr.select("> td");
            if (td.size() < 4) continue;
            long date = dateTime(td.get(0).text());
            String[] teams = td.get(1).text().split("\\s+-\\s+", 2);
            if (date == 0 || teams.length != 2) continue;
            String score = td.get(2).text().trim();
            Matcher m = SCORE.matcher(score);
            out.add(new JSONObject()
                    .put("home", teams[0].trim())
                    .put("away", teams[1].trim())
                    .put("date", date)
                    .put("score", m.find() ? m.group(1) + ":" + m.group(2) : "")
                    .put("competition", competition(td.get(3).text())));
        }
        return out;
    }

    /** "FK SARAJEVO" gives "FK Sarajevo"; short club prefixes (FK, NK, HŠK, BSK) stay in capitals. */
    static String clubName(String raw) {
        StringBuilder b = new StringBuilder();
        for (String w : raw.trim().split("\\s+")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            if (w.length() <= 3 || !w.equals(w.toUpperCase(Locale.ROOT))) b.append(w);
            else b.append(w.charAt(0)).append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return b.toString();
    }

    /** "UEFA LIGA NACIJA 2026/2027" stays; long all-caps names get sentence case otherwise. */
    private static String competition(String raw) {
        return raw.trim().replaceAll("\\s+", " ");
    }

    static int teamId(String href) {
        Matcher m = TEAM_ID.matcher(href == null ? "" : href);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** "08.11.2026 17:30" or "14.11.2026." (Sarajevo time) to epoch millis, 0 if unparseable. */
    static long dateTime(String s) {
        Matcher m = DATE_TIME.matcher(s == null ? "" : s);
        if (!m.find()) return 0;
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Europe/Sarajevo"));
        c.clear();
        c.set(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)) - 1, Integer.parseInt(m.group(1)),
                m.group(4) == null ? 0 : Integer.parseInt(m.group(4)), m.group(5) == null ? 0 : Integer.parseInt(m.group(5)));
        return c.getTimeInMillis();
    }

    private static int toInt(String s) {
        try {
            return Integer.parseInt(s.trim().replace("+", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
