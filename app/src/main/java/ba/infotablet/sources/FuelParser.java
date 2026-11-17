package ba.infotablet.sources;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.parser.Parser;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * uhvatipopust.ba city page to the fuel payload. The page embeds every station of the city as
 * HTML-escaped JSON in {@code <gas-boxes-for-city :prices="..." :types="...">}; prices are in
 * hundredths of a KM. The static "Danas (..., 16.11.2026.)" sentence gives the data date and the
 * cheapest-per-fuel table is used as a cross-check.
 *
 * Output: {dataDate, stations, fuels:{dizel|super95|super98|lpg:{label,count,min,avg,median,max,
 * top:[{name,address,price,outlier}], cheapest:{...}, crossCheck}}}
 */
public final class FuelParser {

    /** App fuel key, display label, uhvatipopust type slug, fallback type id. */
    static final String[][] FUELS = {
            {"dizel", "Dizel", "dizel-d5", "1"},
            {"super95", "Super 95", "super-95", "3"},
            {"super98", "Super 98", "super-98", "4"},
            {"lpg", "LPG", "lpg", "5"},
    };

    static final double OUTLIER_SHARE = 0.15;

    private static final Pattern DATE = Pattern.compile("Danas \\((\\p{L}+), (\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})\\.?\\)");
    private static final Pattern ROW = Pattern.compile(
            "<tr>\\s*<td>([^<]+)</td>\\s*<td[^>]*>([0-9.,]+)</td>\\s*<td>([^<]+)</td>\\s*</tr>");
    private static final Pattern LEGAL = Pattern.compile("(?i)\\b(d\\.?\\s?o\\.?\\s?o\\.?|d\\.?\\s?d\\.?)(\\s|$|,)");

    private FuelParser() {}

    public static JSONObject parse(String html) throws JSONException {
        long dataDate = dataDate(html);
        if (dataDate == 0) throw new JSONException("No data date on the page");
        JSONArray stations = new JSONArray(attr(html, ":prices"));
        if (stations.length() == 0) throw new JSONException("No stations on the page");
        Map<String, String> typeKey = typeKeys(html);
        Map<String, Double> tableMin = cheapestTable(html);

        JSONObject fuels = new JSONObject();
        for (String[] f : FUELS) {
            String field = typeKey.containsKey(f[2]) ? typeKey.get(f[2]) : "latestPriceForG" + f[3];
            List<JSONObject> list = new ArrayList<JSONObject>();
            for (int i = 0; i < stations.length(); i++) {
                JSONObject s = stations.getJSONObject(i);
                if (s.isNull(field) || !s.has(field)) continue;
                double price = s.getDouble(field) / 100.0;
                if (price <= 0) continue;
                list.add(new JSONObject()
                        .put("name", cleanName(s.optString("name")))
                        .put("address", s.optString("address").replaceAll("\\s+", " ").trim())
                        .put("price", price));
            }
            if (list.isEmpty()) continue;
            Collections.sort(list, (a, b) -> {
                double x = a.optDouble("price"), y = b.optDouble("price");
                if (x != y) return x < y ? -1 : 1;
                return a.optString("name").compareTo(b.optString("name")); // stable order for equal prices
            });
            double sum = 0;
            for (JSONObject o : list) sum += o.getDouble("price");
            double median = median(list);
            JSONArray top = new JSONArray();
            JSONObject cheapest = null;
            for (JSONObject o : list) {
                boolean outlier = list.size() >= 5 && Math.abs(o.getDouble("price") - median) / median > OUTLIER_SHARE;
                o.put("outlier", outlier);
                if (top.length() < 5) top.put(o);
                if (cheapest == null && !outlier) cheapest = o;
            }
            double min = list.get(0).getDouble("price");
            JSONObject out = new JSONObject()
                    .put("label", f[1])
                    .put("count", list.size())
                    .put("min", min)
                    .put("avg", Math.round(sum / list.size() * 100.0) / 100.0)
                    .put("median", median)
                    .put("max", list.get(list.size() - 1).getDouble("price"))
                    .put("top", top);
            if (cheapest != null) out.put("cheapest", cheapest);
            Double t = tableMin.get(f[2]);
            out.put("crossCheck", t == null || Math.abs(t - min) < 0.005);
            fuels.put(f[0], out);
        }
        if (fuels.length() == 0) throw new JSONException("No fuel prices found");
        return new JSONObject().put("dataDate", dataDate).put("stations", stations.length()).put("fuels", fuels);
    }

    /** "BINGO PETROL d.o.o. Tuzla" gives "Bingo Petrol"; "PETROL BH OIL COMPANY" gives "Petrol BH Oil Company". */
    static String cleanName(String raw) {
        String s = Parser.unescapeEntities(raw, false).trim();
        Matcher m = LEGAL.matcher(s);
        if (m.find() && m.start() > 0) s = s.substring(0, m.start());
        s = s.replaceAll("(?i)benzinsk[ea] (stanice|pumpe?)", "").replaceAll("[\\s,]+$", "").replaceAll("\\s+", " ").trim();
        StringBuilder b = new StringBuilder();
        for (String w : s.split(" ")) {
            if (b.length() > 0) b.append(' ');
            b.append(titleWord(w));
        }
        return b.toString();
    }

    private static String titleWord(String w) {
        if (!w.equals(w.toUpperCase(Locale.ROOT))) return w; // already mixed case
        if (w.length() <= 2 && !w.matches(".*[AEIOU].*")) return w; // abbreviations such as BH
        StringBuilder b = new StringBuilder();
        boolean start = true;
        for (char ch : w.toCharArray()) {
            b.append(start ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
            start = ch == '-';
        }
        return b.toString();
    }

    private static double median(List<JSONObject> sorted) throws JSONException {
        int n = sorted.size();
        double a = sorted.get((n - 1) / 2).getDouble("price"), b = sorted.get(n / 2).getDouble("price");
        return Math.round((a + b) / 2 * 100.0) / 100.0;
    }

    /** Local midnight (Sarajevo) of the "Danas (...)" date, or 0. */
    static long dataDate(String html) {
        Matcher m = DATE.matcher(html);
        if (!m.find()) return 0;
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Europe/Sarajevo"));
        c.clear();
        c.set(Integer.parseInt(m.group(4)), Integer.parseInt(m.group(3)) - 1, Integer.parseInt(m.group(2)));
        return c.getTimeInMillis();
    }

    /** Value of an HTML-escaped JSON attribute on the gas-boxes-for-city element. */
    private static String attr(String html, String name) throws JSONException {
        int tag = html.indexOf("<gas-boxes-for-city");
        if (tag < 0) throw new JSONException("Station list element not found");
        String key = name + "=\"";
        int a = html.indexOf(key, tag);
        if (a < 0) throw new JSONException("Attribute " + name + " not found");
        int start = a + key.length();
        int end = html.indexOf('"', start);
        if (end < 0) throw new JSONException("Attribute " + name + " not closed");
        return Parser.unescapeEntities(html.substring(start, end), true);
    }

    /** slug to "latestPriceForG{id}", from the :types attribute (empty if absent). */
    private static Map<String, String> typeKeys(String html) {
        Map<String, String> m = new HashMap<String, String>();
        try {
            JSONArray types = new JSONArray(attr(html, ":types"));
            for (int i = 0; i < types.length(); i++) {
                JSONObject t = types.getJSONObject(i);
                m.put(t.optString("slug"), "latestPriceForG" + t.optInt("id"));
            }
        } catch (JSONException ignored) {
            // fall back to the known ids
        }
        return m;
    }

    /** Fuel slug to the price in the page's own "cheapest" table. */
    private static Map<String, Double> cheapestTable(String html) {
        Map<String, Double> m = new HashMap<String, Double>();
        Matcher r = ROW.matcher(html);
        while (r.find()) {
            String fuel = r.group(1).trim().toLowerCase(Locale.ROOT);
            double price;
            try {
                price = Double.parseDouble(r.group(2).replace(',', '.'));
            } catch (NumberFormatException e) {
                continue;
            }
            if (fuel.equals("dizel d5")) m.put("dizel-d5", price);
            else if (fuel.equals("super 95")) m.put("super-95", price);
            else if (fuel.equals("super 98")) m.put("super-98", price);
            else if (fuel.startsWith("lpg")) m.put("lpg", price);
        }
        return m;
    }
}
