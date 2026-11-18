package ba.infotablet.sources;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Water, power, heating and gas notices that concern the configured streets, from the cached
 * payloads of the four sources, soonest first. Pure logic, so it can be tested.
 */
public final class StreetNotices {

    public static final String WATER = "voda", POWER = "struja", HEAT = "grijanje", GAS = "plin";
    private static final long DAY = 86_400_000L;
    /** Announcements (heating news, gas works) stay up this long after they were posted. */
    private static final long NEWS_DAYS = 5;

    public static final class Notice {
        public final String kind;
        /** Local midnight of the day it is about (for news: the day it was posted). */
        public final long day;
        public final String title, detail;
        /** True for announcements whose exact day is only in the text. */
        public final boolean announcement;

        Notice(String kind, long day, String title, String detail, boolean announcement) {
            this.kind = kind;
            this.day = day;
            this.title = title;
            this.detail = detail;
            this.announcement = announcement;
        }
    }

    private StreetNotices() {}

    /** Payloads may be null (source off or never fetched). */
    public static List<Notice> all(String water, String power, String heat, String gas, String streets, long now) {
        final long today = startOfDay(now);
        List<Notice> out = new ArrayList<Notice>();
        if (streets == null || streets.trim().isEmpty()) return out;
        try { if (water != null) water(new JSONObject(water), streets, today, out); } catch (JSONException ignored) { }
        try { if (power != null) power(new JSONObject(power), streets, today, now, out); } catch (JSONException ignored) { }
        try { if (heat != null) heat(new JSONObject(heat), streets, today, out); } catch (JSONException ignored) { }
        try { if (gas != null) gas(new JSONObject(gas), streets, today, out); } catch (JSONException ignored) { }
        Collections.sort(out, (a, b) -> {
            // Announcements (exact day only in the text) come after concrete notices for today and tomorrow.
            long da = a.announcement ? today + DAY + DAY / 2 : a.day, db = b.announcement ? today + DAY + DAY / 2 : b.day;
            if (da != db) return da < db ? -1 : 1;
            if (a.announcement != b.announcement) return a.announcement ? 1 : -1;
            return 0;
        });
        return out;
    }

    /** Notices for today or tomorrow, for the banner on Početna. */
    public static List<Notice> soon(List<Notice> all, long today) {
        List<Notice> out = new ArrayList<Notice>();
        for (Notice n : all) {
            if (n.announcement ? today - n.day <= 2 * DAY : n.day <= today + DAY) out.add(n);
        }
        return out;
    }

    // ---- per source ----

    private static void water(JSONObject o, String streets, long today, List<Notice> out) throws JSONException {
        JSONArray n = o.getJSONArray("notices");
        for (int i = 0; i < n.length(); i++) {
            JSONObject w = n.getJSONObject(i);
            if (!"voda".equals(w.optString("kind"))) continue;
            String body = w.optString("body");
            if (!WaterParser.matches(body, streets)) continue;
            JSONArray days = w.optJSONArray("days");
            long day = -1;
            for (int k = 0; days != null && k < days.length(); k++) {
                long d = near(days.optLong(k), today);
                if (d >= today && (day < 0 || d < day)) day = d;
            }
            if (day < 0) continue;
            out.add(new Notice(WATER, day, "Mogući prekid vode", WaterParser.relevantPart(body, streets), false));
        }
    }

    private static void power(JSONObject o, String streets, long today, long now, List<Notice> out) throws JSONException {
        JSONArray items = o.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            JSONObject p = items.getJSONObject(i);
            long day = near(p.optLong("day"), today);
            if (day < today) continue;
            String street = p.optString("street");
            if (!WaterParser.matches(street, streets)) continue;
            String from = p.optString("from"), to = p.optString("to");
            if (day == today && ended(to, today, now)) continue;   // today's cut is already over
            String title = from.isEmpty() ? "Bez struje" : "Bez struje od " + from + " do " + to;
            String numbers = p.optString("numbers");
            String detail = street + (numbers.isEmpty() ? "" : ", brojevi: " + numbers);
            out.add(new Notice(POWER, day, title, detail, false));
        }
    }

    private static void heat(JSONObject o, String streets, long today, List<Notice> out) throws JSONException {
        long day = near(o.optLong("day"), today);
        JSONArray items = o.optJSONArray("items");
        for (int i = 0; items != null && day == today && i < items.length(); i++) {
            JSONObject h = items.getJSONObject(i);
            String address = h.optString("address");
            if (!WaterParser.matches(address, streets)) continue;
            String end = h.optString("end");
            String detail = address + " · " + h.optString("reason") + (end.isEmpty() ? "" : " · do " + end);
            out.add(new Notice(HEAT, today, "Bez grijanja", detail, false));
        }
        JSONArray news = o.optJSONArray("news");
        for (int i = 0; news != null && i < news.length(); i++) {
            JSONObject a = news.getJSONObject(i);
            long posted = startOfDay(a.optLong("posted"));
            if (posted <= 0 || today - posted > NEWS_DAYS * DAY) continue;
            String text = a.optString("text");
            if (!WaterParser.matches(text, streets)) continue;
            out.add(new Notice(HEAT, posted, a.optString("title"), firstMatchingLine(text, streets), true));
        }
    }

    private static void gas(JSONObject o, String streets, long today, List<Notice> out) throws JSONException {
        JSONArray items = o.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            JSONObject g = items.getJSONObject(i);
            long posted = startOfDay(g.optLong("posted"));
            if (posted <= 0 || today - posted > NEWS_DAYS * DAY) continue;
            String title = g.optString("title"), text = g.optString("text");
            if (!WaterParser.matches(title + ". " + text, streets)) continue;
            String detail = WaterParser.matches(text, streets) ? WaterParser.relevantPart(text, streets) : text;
            out.add(new Notice(GAS, posted, title, detail, true));
        }
    }

    /** True when "HH:mm" today is before now. */
    static boolean ended(String hhmm, long today, long now) {
        String[] p = hhmm.split(":");
        if (p.length != 2) return false;
        try {
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.setTimeInMillis(today);
            c.set(java.util.Calendar.HOUR_OF_DAY, Integer.parseInt(p[0].trim()));
            c.set(java.util.Calendar.MINUTE, Integer.parseInt(p[1].trim()));
            return c.getTimeInMillis() <= now;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Heating announcements list addresses one per line; prefer the line that names the street. */
    private static String firstMatchingLine(String text, String streets) {
        for (String line : text.split("\n")) if (WaterParser.matches(line, streets)) return line;
        return WaterParser.relevantPart(text.replace('\n', ' '), streets);
    }

    /** Midnights from another time zone or a DST day: snap to the closest local midnight. */
    private static long near(long t, long today) {
        if (t <= 0) return -1;
        long days = Math.round((t - today) / (double) DAY);
        return today + days * DAY;
    }

    private static long startOfDay(long t) {
        if (t <= 0) return 0;
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
