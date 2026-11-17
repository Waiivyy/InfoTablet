package ba.infotablet.sources;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import ba.infotablet.data.Cache;
import ba.infotablet.data.SourceState;
import ba.infotablet.net.Http;
import ba.infotablet.sync.BaseSource;

/**
 * Smog episodes declared and lifted by Kanton Sarajevo, read from the government's news list.
 * The episode state is kept across refreshes because a long episode can scroll off the first page.
 * Payload {zones:{A:"UPOZORENJE",...}, level, declaredAt, title, href, lastNode}.
 */
public final class SmogSource extends BaseSource {

    public static final String ID = "smog";
    private static final String LIST = "https://vlada.ks.gov.ba/aktuelno/novosti";

    @Override public String id() { return ID; }

    @Override public String name() { return "Smog epizode (Vlada KS)"; }

    /** Every 30 minutes in the plan season (15 October to 15 April), otherwise every 3 hours. */
    @Override
    public long intervalMs(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        int m = c.get(Calendar.MONTH) + 1, d = c.get(Calendar.DAY_OF_MONTH);
        boolean season = m >= 11 || m <= 3 || (m == 10 && d >= 15) || (m == 4 && d <= 15);
        return season ? 30 * 60_000L : 3 * 3600_000L;
    }

    @Override public long staleAfterMs() { return 6 * 3600_000L; }

    @Override
    public String fetch(Context ctx) throws Exception {
        JSONObject state = previous(Cache.get(ctx, id()));
        JSONObject zones = state.optJSONObject("zones");
        if (zones == null) zones = new JSONObject();
        int lastNode = state.optInt("lastNode", 0);

        List<SmogParser.Notice> notices = SmogParser.list(Http.get(LIST, 1024 * 1024));
        if (notices.isEmpty()) throw new JSONException("News list is empty");
        Collections.sort(notices, (a, b) -> a.node - b.node); // oldest first
        int fetched = 0;
        for (SmogParser.Notice n : notices) {
            if (n.node <= lastNode) continue;
            lastNode = Math.max(lastNode, n.node);
            if (!SmogParser.isCandidate(n.title, n.teaser)) continue;
            String body = n.teaser;
            if (fetched < 3 && !n.href.isEmpty()) { // the article names the zones more reliably than the teaser
                fetched++;
                Thread.sleep(1200);
                try {
                    Element b = Jsoup.parse(Http.get(n.href, 1024 * 1024)).selectFirst("div.field--name-body");
                    if (b != null) body = b.text();
                } catch (Exception ignored) {
                    // keep the teaser
                }
            }
            if (SmogParser.apply(zones, n.title, body)) {
                // The latest notice that leaves an episode active starts the 7-day display window.
                if (SmogParser.current(zones) != null) state.put("declaredAt", n.date > 0 ? n.date : System.currentTimeMillis());
                state.put("title", n.title).put("href", n.href).put("noticeDate", n.date);
            }
        }
        String level = SmogParser.current(zones);
        state.put("zones", zones).put("lastNode", lastNode);
        if (level == null) state.remove("level"); else state.put("level", level);
        return state.toString();
    }

    private static JSONObject previous(SourceState st) {
        if (st.hasData()) {
            try {
                return new JSONObject(st.payload);
            } catch (JSONException ignored) {
                // start fresh
            }
        }
        return new JSONObject();
    }
}
