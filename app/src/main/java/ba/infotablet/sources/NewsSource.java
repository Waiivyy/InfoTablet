package ba.infotablet.sources;

import android.content.Context;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ba.infotablet.data.Cache;
import ba.infotablet.data.SourceState;
import ba.infotablet.net.Http;
import ba.infotablet.sources.NewsParser.Item;
import ba.infotablet.sources.NewsParser.Portal;
import ba.infotablet.sync.BaseSource;

/**
 * One news portal: its feed, plus the first sentences of each new article read from the article
 * page itself (no summarising). Article text is fetched once per article and reused afterwards.
 * Payload {portal, items:[{title,link,time,cat,crime,lead,full}]}.
 */
public final class NewsSource extends BaseSource {

    public static final String PREFIX = "news_";
    private static final int MAX_ITEMS = 12;
    private static final int MAX_ARTICLE_FETCHES = 6;

    private final Portal portal;

    public NewsSource(Portal portal) {
        this.portal = portal;
    }

    public static String idOf(Portal p) {
        return PREFIX + p.name().toLowerCase(Locale.ROOT);
    }

    public Portal portal() {
        return portal;
    }

    @Override public String id() { return idOf(portal); }

    @Override public String name() { return "Vijesti (" + portal.label + ")"; }

    @Override public long intervalMs(long now) { return 45 * 60_000L; }

    @Override public long staleAfterMs() { return 3 * 3600_000L; }

    @Override
    public String fetch(Context ctx) throws Exception {
        Map<String, JSONObject> known = previous(Cache.get(ctx, id()));
        List<Item> items = NewsParser.parseFeed(Xml.newPullParser(), Http.get(portal.feed));
        Collections.sort(items, (a, b) -> a.time > b.time ? -1 : (a.time < b.time ? 1 : 0));

        JSONArray out = new JSONArray();
        int fetched = 0;
        for (Item it : items) {
            if (out.length() >= MAX_ITEMS) break;
            if (NewsParser.skip(it.link)) continue;
            JSONObject o = new JSONObject()
                    .put("title", it.title)
                    .put("link", it.link)
                    .put("time", it.time)
                    .put("cat", NewsParser.category(it.link, it.categories))
                    .put("crime", NewsParser.isCrime(it.link, it.categories));
            JSONObject prev = known.get(it.link);
            if (prev != null && prev.optBoolean("full")) {
                o.put("lead", prev.optString("lead")).put("full", true);
            } else {
                String lead = "";
                if (fetched < MAX_ARTICLE_FETCHES) {
                    fetched++;
                    Thread.sleep(1000); // one article at a time, politely spaced
                    try {
                        lead = NewsParser.articleLead(portal, Http.getPartial(it.link, portal.marker, portal.extraBytes, portal.cap));
                    } catch (Exception ignored) {
                        lead = "";
                    }
                }
                if (!lead.isEmpty()) {
                    o.put("lead", lead).put("full", true);
                } else {
                    o.put("lead", NewsParser.summaryFallback(it.summary)).put("full", false); // retried next time
                }
            }
            out.put(o);
        }
        if (out.length() == 0) throw new JSONException("Feed has no readable items");
        return new JSONObject().put("portal", portal.label).put("items", out).toString();
    }

    private static Map<String, JSONObject> previous(SourceState st) {
        Map<String, JSONObject> m = new HashMap<String, JSONObject>();
        if (!st.hasData()) return m;
        try {
            JSONArray arr = new JSONObject(st.payload).getJSONArray("items");
            for (int i = 0; i < arr.length(); i++) m.put(arr.getJSONObject(i).optString("link"), arr.getJSONObject(i));
        } catch (JSONException ignored) {
            // start fresh
        }
        return m;
    }
}
