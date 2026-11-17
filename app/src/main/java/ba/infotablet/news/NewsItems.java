package ba.infotablet.news;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ba.infotablet.Settings;
import ba.infotablet.data.Cache;
import ba.infotablet.data.SourceState;
import ba.infotablet.sources.NewsParser.Portal;
import ba.infotablet.sources.NewsSource;
import ba.infotablet.ui.Freshness;

/** Merges the cached items of all enabled portals, newest first, with the crime filter applied. */
final class NewsItems {

    static final class Entry {
        final JSONObject item;
        final String portal;
        final boolean stale;
        final String staleNote;

        Entry(JSONObject item, String portal, boolean stale, String staleNote) {
            this.item = item;
            this.portal = portal;
            this.stale = stale;
            this.staleNote = staleNote;
        }
    }

    static final class PortalStatus {
        final String label;
        final boolean ok;

        PortalStatus(String label, boolean ok) {
            this.label = label;
            this.ok = ok;
        }
    }

    private NewsItems() {}

    static List<Entry> load(Context c, long now, String category) {
        Settings s = Settings.of(c);
        List<Entry> out = new ArrayList<Entry>();
        for (Portal p : Portal.values()) {
            NewsSource src = new NewsSource(p);
            if (!s.sourceEnabled(src.id())) continue;
            SourceState st = Cache.get(c, src.id());
            if (!st.hasData()) continue;
            Freshness f = Freshness.of(st, src.staleAfterMs(), now);
            try {
                JSONArray items = new JSONObject(st.payload).getJSONArray("items");
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    if (s.newsCategoryHidden("crna") && (it.optBoolean("crime")
                            || ba.infotablet.sources.NewsParser.soundsLikeCrime(it.optString("title") + " " + it.optString("lead")))) continue;
                    if (s.newsCategoryHidden(it.optString("cat"))) continue;
                    if (category != null && !category.equals(it.optString("cat"))) continue;
                    out.add(new Entry(it, p.label, f.stale, f.note));
                }
            } catch (JSONException ignored) {
                // skip a broken payload
            }
        }
        Collections.sort(out, (a, b) -> {
            long x = a.item.optLong("time"), y = b.item.optLong("time");
            return x > y ? -1 : (x < y ? 1 : 0);
        });
        return out;
    }

    static List<PortalStatus> status(Context c, long now) {
        Settings s = Settings.of(c);
        List<PortalStatus> out = new ArrayList<PortalStatus>();
        for (Portal p : Portal.values()) {
            NewsSource src = new NewsSource(p);
            if (!s.sourceEnabled(src.id())) continue;
            SourceState st = Cache.get(c, src.id());
            out.add(new PortalStatus(p.label, st.hasData() && !Freshness.of(st, src.staleAfterMs(), now).stale));
        }
        return out;
    }

    /** Cache timestamps of all portals plus the filters: the screen only rebuilds when this changes. */
    static String signature(Context c, String category) {
        StringBuilder b = new StringBuilder(category == null ? "all" : category).append(Settings.of(c).getString("news_hidden"));
        for (Portal p : Portal.values()) {
            SourceState st = Cache.get(c, NewsSource.idOf(p));
            b.append('|').append(st.okAt).append(':').append(st.failCount);
        }
        return b.toString();
    }

    static String categoryLabel(String cat) {
        switch (cat) {
            case "bih": return "BiH";
            case "sarajevo": return "Sarajevo";
            case "svijet": return "Svijet";
            case "ekonomija": return "Ekonomija";
            case "kultura": return "Kultura";
            case "sport": return "Sport";
            case "tech": return "Tehnologija";
            case "crna": return "Crna hronika";
            default: return "Vijesti";
        }
    }
}
