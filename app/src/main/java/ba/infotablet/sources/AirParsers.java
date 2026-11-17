package ba.infotablet.sources;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Air quality: CAMS model forecast (Open-Meteo) and official FHMZ station measurements. */
public final class AirParsers {

    private static final String CANTON = "Kanton Sarajevo";
    private static final Pattern HOUR = Pattern.compile(
            "option[^>]*value=\"0\"[^>]*title=\"(\\d{2})\\.(\\d{2})\\.(\\d{4})\\. u (\\d{2}):(\\d{2})\"");

    private AirParsers() {}

    /** {time, aqi, pm25, pm10, pollen:{alder,birch,grass,mugwort,olive,ragweed}, hourly:[{time,aqi}]} */
    public static JSONObject cams(String raw) throws JSONException {
        JSONObject in = new JSONObject(raw);
        JSONObject cur = in.getJSONObject("current");
        JSONObject out = new JSONObject();
        out.put("time", cur.getLong("time") * 1000L);
        Json.putNum(out, "aqi", cur, "european_aqi");
        Json.putNum(out, "pm25", cur, "pm2_5");
        Json.putNum(out, "pm10", cur, "pm10");
        JSONObject pollen = new JSONObject();
        for (String p : new String[] {"alder", "birch", "grass", "mugwort", "olive", "ragweed"}) {
            Json.putNum(pollen, p, cur, p + "_pollen");
        }
        out.put("pollen", pollen);
        JSONObject h = in.optJSONObject("hourly");
        JSONArray hourly = new JSONArray();
        if (h != null) {
            JSONArray t = h.getJSONArray("time"), aqi = h.optJSONArray("european_aqi");
            for (int i = 0; i < t.length(); i++) {
                JSONObject o = new JSONObject().put("time", t.getLong(i) * 1000L);
                Json.putNum(o, "aqi", aqi, i);
                hourly.put(o);
            }
        }
        out.put("hourly", hourly);
        return out;
    }

    /**
     * FHMZ zrak portal: /rest/index/data/{eaqi,pm25,pm10}/0 plus the home page for the measurement hour.
     * Rows are [id, code, name, lat, lon, canton, value, level, isComplete, ...]; levels are 0 (good) to 5.
     * Output {measuredAt, stations:[{id,name,level,complete,pm25,pm25Level,pm10,pm10Level}]} for Kanton Sarajevo.
     */
    public static JSONObject stations(String eaqiRaw, String pm25Raw, String pm10Raw, String homeHtml) throws JSONException {
        Map<Integer, double[]> pm25 = values(pm25Raw);
        Map<Integer, double[]> pm10 = values(pm10Raw);
        JSONArray eaqi = new JSONArray(eaqiRaw);
        JSONArray stations = new JSONArray();
        for (int i = 0; i < eaqi.length(); i++) {
            JSONArray r = eaqi.getJSONArray(i);
            if (!CANTON.equals(r.optString(5))) continue;
            int id = r.getInt(0);
            JSONObject s = new JSONObject();
            s.put("id", id);
            s.put("name", r.getString(2).replace("Sarajevo ", ""));
            s.put("level", r.optInt(7, -1));
            s.put("complete", r.optBoolean(8, false));
            double[] a = pm25.get(id), b = pm10.get(id);
            if (a != null) { s.put("pm25", a[0]); s.put("pm25Level", (int) a[1]); }
            if (b != null) { s.put("pm10", b[0]); s.put("pm10Level", (int) b[1]); }
            stations.put(s);
        }
        if (stations.length() == 0) throw new JSONException("No Sarajevo stations in FHMZ data");
        return new JSONObject().put("measuredAt", measuredAt(homeHtml)).put("stations", stations);
    }

    private static Map<Integer, double[]> values(String raw) throws JSONException {
        Map<Integer, double[]> m = new HashMap<Integer, double[]>();
        if (raw == null) return m;
        JSONArray arr = new JSONArray(raw);
        for (int i = 0; i < arr.length(); i++) {
            JSONArray r = arr.getJSONArray(i);
            if (r.isNull(6)) continue;
            m.put(r.getInt(0), new double[] {r.getDouble(6), r.optInt(7, -1)});
        }
        return m;
    }

    /** "17.11.2026. u 01:00" (Sarajevo time; "u 24:00" is midnight of the next day). 0 if absent. */
    static long measuredAt(String html) {
        if (html == null) return 0;
        Matcher m = HOUR.matcher(html);
        if (!m.find()) return 0;
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Europe/Sarajevo"));
        c.clear();
        c.set(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)) - 1, Integer.parseInt(m.group(1)), 0, Integer.parseInt(m.group(5)));
        c.add(Calendar.HOUR_OF_DAY, Integer.parseInt(m.group(4)));
        return c.getTimeInMillis();
    }
}
