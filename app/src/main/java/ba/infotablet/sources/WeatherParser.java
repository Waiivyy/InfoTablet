package ba.infotablet.sources;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Open-Meteo forecast (timeformat=unixtime) to the app's weather payload:
 * {current:{time,t,feels,rh,code,wind,windDir,precip,isDay}, hourly:[{time,t,code,pop,precip,snow,uv,isDay}],
 *  daily:[{date,code,tmax,tmin,sunrise,sunset,pop,precip,snow,uv}]}; all times are epoch millis.
 */
public final class WeatherParser {

    private WeatherParser() {}

    public static JSONObject parse(String raw) throws JSONException {
        JSONObject in = new JSONObject(raw);
        JSONObject out = new JSONObject();

        JSONObject cur = in.getJSONObject("current");
        JSONObject c = new JSONObject();
        c.put("time", cur.getLong("time") * 1000L);
        c.put("t", cur.getDouble("temperature_2m"));
        Json.putNum(c, "feels", cur, "apparent_temperature");
        Json.putNum(c, "rh", cur, "relative_humidity_2m");
        c.put("code", cur.getInt("weather_code"));
        Json.putNum(c, "wind", cur, "wind_speed_10m");
        Json.putNum(c, "windDir", cur, "wind_direction_10m");
        Json.putNum(c, "precip", cur, "precipitation");
        c.put("isDay", cur.optInt("is_day", 1));
        out.put("current", c);

        JSONObject h = in.getJSONObject("hourly");
        JSONArray times = h.getJSONArray("time");
        JSONArray hourly = new JSONArray();
        for (int i = 0; i < times.length(); i++) {
            JSONObject o = new JSONObject();
            o.put("time", times.getLong(i) * 1000L);
            Json.putNum(o, "t", h.optJSONArray("temperature_2m"), i);
            Json.putInt(o, "code", h.optJSONArray("weather_code"), i);
            Json.putInt(o, "pop", h.optJSONArray("precipitation_probability"), i);
            Json.putNum(o, "precip", h.optJSONArray("precipitation"), i);
            Json.putNum(o, "snow", h.optJSONArray("snowfall"), i);
            Json.putNum(o, "uv", h.optJSONArray("uv_index"), i);
            Json.putInt(o, "isDay", h.optJSONArray("is_day"), i);
            hourly.put(o);
        }
        out.put("hourly", hourly);

        JSONObject d = in.getJSONObject("daily");
        JSONArray days = d.getJSONArray("time");
        JSONArray daily = new JSONArray();
        for (int i = 0; i < days.length(); i++) {
            JSONObject o = new JSONObject();
            o.put("date", days.getLong(i) * 1000L);
            Json.putInt(o, "code", d.optJSONArray("weather_code"), i);
            Json.putNum(o, "tmax", d.optJSONArray("temperature_2m_max"), i);
            Json.putNum(o, "tmin", d.optJSONArray("temperature_2m_min"), i);
            JSONArray sr = d.optJSONArray("sunrise"), ss = d.optJSONArray("sunset");
            if (sr != null && !sr.isNull(i)) o.put("sunrise", sr.getLong(i) * 1000L);
            if (ss != null && !ss.isNull(i)) o.put("sunset", ss.getLong(i) * 1000L);
            Json.putInt(o, "pop", d.optJSONArray("precipitation_probability_max"), i);
            Json.putNum(o, "precip", d.optJSONArray("precipitation_sum"), i);
            Json.putNum(o, "snow", d.optJSONArray("snowfall_sum"), i);
            Json.putNum(o, "uv", d.optJSONArray("uv_index_max"), i);
            daily.put(o);
        }
        if (daily.length() == 0) throw new JSONException("No daily forecast");
        out.put("daily", daily);
        return out;
    }

    /**
     * The second-city tile: {current:{t,code,isDay}, max, min, utcOffset} from a one-day Open-Meteo
     * forecast requested with timezone=auto (so the day and the offset are the city's own).
     */
    public static JSONObject secondCity(String raw) throws JSONException {
        JSONObject in = new JSONObject(raw);
        JSONObject cur = in.getJSONObject("current");
        JSONObject daily = in.getJSONObject("daily");
        JSONObject c = new JSONObject()
                .put("time", cur.getLong("time") * 1000L)
                .put("t", cur.getDouble("temperature_2m"))
                .put("code", cur.getInt("weather_code"))
                .put("isDay", cur.optInt("is_day", 1));
        return new JSONObject()
                .put("current", c)
                .put("max", daily.getJSONArray("temperature_2m_max").getDouble(0))
                .put("min", daily.getJSONArray("temperature_2m_min").getDouble(0))
                .put("utcOffset", in.optLong("utc_offset_seconds") * 1000L);
    }
}
