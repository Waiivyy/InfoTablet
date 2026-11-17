package ba.infotablet.sources;

import android.content.Context;
import android.util.Xml;

import org.json.JSONObject;

import java.util.Calendar;

import ba.infotablet.net.Http;
import ba.infotablet.sync.BaseSource;

/** BIHAMK road conditions and border crossings. Hourly 06 to 21 h (the information centre's hours), else every 3 hours. */
public final class RoadsSource extends BaseSource {

    public static final String ID = "roads";
    private static final String BASE = "https://bihamk.ba/spi/stanje-na-cesti-u-bih";

    @Override public String id() { return ID; }

    @Override public String name() { return "Ceste i granice (BIHAMK)"; }

    @Override
    public long intervalMs(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        int h = c.get(Calendar.HOUR_OF_DAY);
        return h >= 6 && h < 21 ? 60 * 60_000L : 3 * 3600_000L;
    }

    @Override public long staleAfterMs() { return 8 * 3600_000L; }

    @Override
    public String fetch(Context ctx) throws Exception {
        JSONObject out = RoadsParser.rss(Xml.newPullParser(), Http.get(BASE + "/rss"));
        Thread.sleep(1200);
        try {
            JSONObject page = RoadsParser.page(Http.get(BASE, 1024 * 1024));
            out.put("updated", page.optLong("updated")).put("summary", page.optJSONArray("summary"));
        } catch (Exception ignored) {
            // categories alone are still useful
        }
        return out.toString();
    }
}
