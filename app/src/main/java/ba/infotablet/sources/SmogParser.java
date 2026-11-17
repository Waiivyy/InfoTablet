package ba.infotablet.sources;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Smog episodes in Kanton Sarajevo from the cantonal government's news list. Under the plan in force
 * (Sl. novine KS 51/2025) there are two levels, Upozorenje and Uzbuna; "Pripravnost" is recognised
 * in older texts. Zones A to D (E has no measures).
 */
public final class SmogParser {

    public static final String UZBUNA = "UZBUNA", UPOZORENJE = "UPOZORENJE", PRIPRAVNOST = "PRIPRAVNOST";
    private static final String[] ALL_ZONES = {"A", "B", "C", "D"};

    private static final Pattern NODE = Pattern.compile("/node/(\\d+)");
    private static final Pattern DATE = Pattern.compile("(\\d{2})\\.(\\d{2})\\.(\\d{4})");
    private static final Pattern REJECT = Pattern.compile("preventivnih mjera|spasavanja|spašavanja|vjetr|padavin|snijeg|ledenic|poplav|topljenj");
    private static final Pattern LIFT = Pattern.compile("ukinu|ukida|ukidanj|van snage|prestaj|prestanak");
    private static final Pattern DECLARE = Pattern.compile("proglas|proglaš");
    private static final Pattern LEVEL = Pattern.compile("uzbun|upozorenj|pripravnost");
    private static final Pattern REMAINS = Pattern.compile("na snazi ost(?:aje|avila|avilo|aju)\\s+(?:epizod\\w*\\s+)?\"?(uzbun\\w*|upozorenj\\w*|pripravnost\\w*)");
    private static final Pattern ZONES = Pattern.compile("zon\\w*\\s+([a-e](?:\\s*,\\s*[a-e])*,?(?:\\s+i\\s+[a-e])?)\\b");

    /** One list entry: node id, date, title, link, teaser. */
    public static final class Notice {
        public int node;
        public long date;
        public String title = "", href = "", teaser = "";
    }

    private SmogParser() {}

    /** Items of https://vlada.ks.gov.ba/aktuelno/novosti (newest first). */
    public static List<Notice> list(String html) {
        Document d = Jsoup.parse(html, "https://vlada.ks.gov.ba");
        List<Notice> out = new ArrayList<Notice>();
        for (Element row : d.select("#block-gavias-vitaco-content div.views-row")) {
            Element a = row.selectFirst(".views-field-title h3 a, .views-field-title a");
            if (a == null) continue;
            Notice n = new Notice();
            n.title = a.text().trim();
            n.href = a.absUrl("href");
            Matcher nm = NODE.matcher(n.href);
            n.node = nm.find() ? Integer.parseInt(nm.group(1)) : 0;
            Element uid = row.selectFirst(".views-field-uid .field-content");
            Matcher dm = DATE.matcher(uid == null ? "" : uid.text());
            if (dm.find()) {
                java.util.Calendar c = java.util.Calendar.getInstance();
                c.clear();
                c.set(Integer.parseInt(dm.group(3)), Integer.parseInt(dm.group(2)) - 1, Integer.parseInt(dm.group(1)));
                n.date = c.getTimeInMillis();
            }
            Element teaser = row.selectFirst(".views-field-body .field-content");
            n.teaser = teaser == null ? "" : teaser.text().trim();
            out.add(n);
        }
        return out;
    }

    /** True when a notice looks like a smog episode declaration or lift (title, then teaser). */
    public static boolean isCandidate(String title, String teaser) {
        String t = norm(title);
        if (REJECT.matcher(t).find()) return false;
        boolean level = LEVEL.matcher(t).find();
        if (t.contains("epizod") && level) return true;
        String both = t + " " + norm(teaser);
        return level && (both.contains("zrak") || both.contains("zagađ") || both.contains("zagad"));
    }

    /**
     * Applies one notice to the state {zone letter: level}. Lift is checked before declare, because
     * lift wording can also contain "proglašava". Returns true if the state changed.
     */
    public static boolean apply(JSONObject zones, String title, String body) throws JSONException {
        String t = norm(title);
        String all = t + " " + norm(body);
        boolean lift = LIFT.matcher(t).find() || (!DECLARE.matcher(t).find() && LIFT.matcher(all).find());
        boolean declare = !lift && DECLARE.matcher(all).find();
        if (!lift && !declare) return false;

        String remainsLevel = null;
        Set<String> remainsZones = new LinkedHashSet<String>();
        Matcher r = REMAINS.matcher(all);
        String main = all;
        if (r.find()) {
            remainsLevel = level(r.group(1));
            int clauseStart = Math.max(all.lastIndexOf(',', r.start()), all.lastIndexOf(';', r.start()));
            String clause = all.substring(Math.max(0, clauseStart), Math.min(all.length(), r.end() + 40));
            remainsZones.addAll(zonesIn(clause));
            main = all.substring(0, Math.max(0, clauseStart));
        }

        List<String> levels = levelsIn(main);
        if (remainsLevel != null) levels.remove(remainsLevel);
        Set<String> z = zonesIn(main);
        Set<String> affected = new LinkedHashSet<String>();
        boolean changed = false;
        if (lift) {
            for (String zone : new String[] {"A", "B", "C", "D", "?"}) {
                if (!z.isEmpty() && !z.contains(zone) && !zone.equals("?")) continue;
                String cur = zones.optString(zone, null);
                if (cur != null && (levels.isEmpty() || levels.contains(cur))) {
                    zones.remove(zone);
                    affected.add(zone);
                    changed = true;
                }
            }
        } else {
            String lvl = levels.isEmpty() ? null : highest(levels);
            if (lvl != null) {
                if (z.isEmpty()) z.add("?"); // declared, zones not named
                for (String zone : z) {
                    zones.put(zone, lvl);
                    affected.add(zone);
                }
                changed = true;
            }
        }
        // "... a na snazi ostaje Upozorenje": the lower level stays, in the named zones or the lifted ones.
        if (remainsLevel != null) {
            for (String zone : remainsZones.isEmpty() ? affected : remainsZones) {
                if (zone.equals("?") && !remainsZones.isEmpty()) continue;
                zones.put(zone, remainsLevel);
                changed = true;
            }
        }
        return changed;
    }

    /** The most severe level in the state, or null. */
    public static String current(JSONObject zones) {
        List<String> levels = new ArrayList<String>();
        JSONArray names = zones.names();
        for (int i = 0; names != null && i < names.length(); i++) levels.add(zones.optString(names.optString(i)));
        return levels.isEmpty() ? null : highest(levels);
    }

    static Set<String> zonesIn(String text) {
        Set<String> out = new LinkedHashSet<String>();
        if (text.contains("cijelo područje kantona") || text.contains("cijelom području kantona") || text.contains("cijeli kanton")) {
            for (String z : ALL_ZONES) out.add(z);
            return out;
        }
        Matcher m = ZONES.matcher(text);
        while (m.find()) {
            for (String part : m.group(1).split("[,\\s]+|\\bi\\b")) {
                String p = part.trim().toUpperCase(Locale.ROOT);
                if (p.length() == 1 && "ABCD".contains(p)) out.add(p);
            }
        }
        if (out.isEmpty() && (text.contains("pet općina") || text.contains("četiri gradske općine") || text.contains("urbano jezgro"))) out.add("A");
        return out;
    }

    private static List<String> levelsIn(String text) {
        List<String> out = new ArrayList<String>();
        Matcher m = LEVEL.matcher(text);
        while (m.find()) {
            String l = level(m.group());
            if (!out.contains(l)) out.add(l);
        }
        return out;
    }

    private static String level(String word) {
        if (word.startsWith("uzbun")) return UZBUNA;
        if (word.startsWith("upozor")) return UPOZORENJE;
        return PRIPRAVNOST;
    }

    private static String highest(List<String> levels) {
        if (levels.contains(UZBUNA)) return UZBUNA;
        if (levels.contains(UPOZORENJE)) return UPOZORENJE;
        return levels.contains(PRIPRAVNOST) ? PRIPRAVNOST : null;
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[„“”″«»]", "\"").replaceAll("\\s+", " ");
    }
}
