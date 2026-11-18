package ba.infotablet.sources;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import ba.infotablet.Settings;
import ba.infotablet.data.Cache;
import ba.infotablet.data.SourceState;
import ba.infotablet.net.Http;
import ba.infotablet.sync.BaseSource;

/**
 * The live score of the followed foreign club, only while its match is on: Bundesliga from
 * OpenLigaDB (entered by volunteers, free), Premier League and La Liga from football-data.org (the
 * free plan's scores are delayed), every minute. Payload: one compact match.
 */
public final class LiveSource extends BaseSource {

    public static final String ID = "live";
    /** From a few minutes before kickoff until the match has surely ended. */
    private static final long BEFORE = 5 * 60_000L, AFTER = 135 * 60_000L;

    @Override public String id() { return ID; }

    @Override public String name() { return "Rezultat uživo"; }

    @Override
    public boolean active(Context ctx) {
        return next(ctx) != null;
    }

    @Override
    public long intervalMs(long now) {
        return 60_000L;
    }

    @Override public long staleAfterMs() { return 10 * 60_000L; }

    @Override
    public String fetch(Context ctx) throws Exception {
        JSONObject next = next(ctx);
        if (next == null) throw new IllegalStateException("No match on");
        String league = Settings.of(ctx).sportLeague();
        JSONObject club = clubOf(ctx);
        if ("BL1".equals(league) && "BL1".equals(next.optString("comp"))) {
            JSONObject m = FootballParser.openLiga(Http.get("https://api.openligadb.de/getmatchdata/bl1", 512 * 1024),
                    club == null ? "" : club.optString("name"));
            if (m != null) return m.put("source", "openligadb").toString();
        }
        String body = FootballSource.get("matches/" + next.optLong("id"), Settings.of(ctx).footballKey());
        return FootballParser.match(new JSONObject(body)).put("source", "football-data").toString();
    }

    /** The followed club's next match when it is on now (or about to start), else null. */
    static JSONObject next(Context ctx) {
        Settings s = Settings.of(ctx);
        if (s.footballKey().isEmpty() || "bih".equals(s.sportLeague())) return null;
        JSONObject club = clubOf(ctx);
        if (!FootballSource.isFollowed(club, s.sportLeague(), s.sportClub())) return null;   // the club was just changed
        JSONObject next = club.optJSONObject("next");
        if (next == null) return null;
        long now = System.currentTimeMillis(), k = next.optLong("utc");
        return now >= k - BEFORE && now <= k + AFTER ? next : null;
    }

    private static long memoAt = -1;
    private static JSONObject memo;

    /** The club part of the football payload; parsed again only when the payload changed (checked every 30 s). */
    private static synchronized JSONObject clubOf(Context ctx) {
        SourceState st = Cache.get(ctx, FootballSource.ID);
        if (st == null || !st.hasData()) return null;
        if (st.okAt != memoAt) {
            memoAt = st.okAt;
            try {
                memo = new JSONObject(st.payload).optJSONObject("club");
            } catch (JSONException e) {
                memo = null;
            }
        }
        return memo;
    }
}
