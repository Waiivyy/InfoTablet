package ba.infotablet.sources;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

import ba.infotablet.Settings;
import ba.infotablet.net.Http;
import ba.infotablet.sync.BaseSource;

/**
 * Toplane-Sarajevo: buildings without heating today, plus announced cut-offs from the homepage
 * news (at most three articles). Every 45 minutes in the heating season by day, otherwise rarely.
 */
public final class HeatSource extends BaseSource {

    public static final String ID = "heat";
    private static final String BASE = "https://www.toplanesarajevo.ba";
    private static final int MAX_ARTICLES = 3;

    @Override public String id() { return ID; }

    @Override public String name() { return "Grijanje (Toplane-Sarajevo)"; }

    @Override public boolean active(Context ctx) { return Settings.of(ctx).hasStreets(); }

    @Override
    public long intervalMs(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        int month = c.get(Calendar.MONTH), h = c.get(Calendar.HOUR_OF_DAY);
        boolean season = month >= Calendar.OCTOBER || month <= Calendar.APRIL;
        if (!season) return 6 * 3600_000L;
        return h >= 6 && h < 21 ? 45 * 60_000L : 3 * 3600_000L;
    }

    @Override public long staleAfterMs() { return 8 * 3600_000L; }

    @Override
    public String fetch(Context ctx) throws Exception {
        JSONObject out = UtilityParsers.heatToday(Http.get(BASE + "/korisnici/obavijest-o-radovima", 256 * 1024));
        JSONArray news = new JSONArray();
        try {
            JSONArray links = UtilityParsers.heatNewsLinks(Http.get(BASE + "/", 256 * 1024));
            for (int i = 0; i < links.length() && i < MAX_ARTICLES; i++) {
                JSONObject l = links.getJSONObject(i);
                news.put(UtilityParsers.heatArticle(Http.get(l.getString("url"), 256 * 1024), l.getInt("id")));
            }
        } catch (Exception e) {
            Log.w("InfoTablet", "heat news: " + e.getMessage());   // the table alone is still useful
        }
        return out.put("news", news).toString();
    }
}
