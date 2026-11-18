package ba.infotablet.sources;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import ba.infotablet.Settings;
import ba.infotablet.data.Cache;
import ba.infotablet.data.SourceState;
import ba.infotablet.net.Http;
import ba.infotablet.sync.BaseSource;

/**
 * Premier League, Bundesliga and La Liga from football-data.org (free plan, key set on the device):
 * table and this week's matches per league, plus the followed club's matches in every competition.
 * Every 3 hours, every 15 minutes around the followed club's kickoff. When only the followed club
 * changed and the tables are recent, just the club's matches are fetched (one call instead of seven).
 * The free plan allows 10 calls a minute, so calls are spaced 7 seconds apart.
 * Payload {leagues:{PL:{...}}, leaguesAt, club:{...}|absent}.
 */
public final class FootballSource extends BaseSource {

    public static final String ID = "football";
    private static final String API = "https://api.football-data.org/v4/";
    private static final long DAY = 86_400_000L;
    /** How old the tables may be and still be reused when only the followed club changed. */
    static final long REUSE_MS = 2 * 3600_000L;

    @Override public String id() { return ID; }

    @Override public String name() { return "Lige (football-data.org)"; }

    @Override public boolean active(Context ctx) { return !Settings.of(ctx).footballKey().isEmpty(); }

    @Override public long intervalMs(long now) { return 3 * 3600_000L; }

    @Override public long staleAfterMs() { return 12 * 3600_000L; }

    @Override
    public long dueAt(SourceState st, long now) {
        long normal = super.dueAt(st, now);
        if (st.failing() || !st.hasData()) return normal;
        try {
            JSONObject o = new JSONObject(st.payload);
            long leaguesAt = o.optLong("leaguesAt");
            if (leaguesAt > 0 && leaguesAt <= now) normal = Math.min(normal, leaguesAt + intervalMs(now));
            JSONObject club = o.optJSONObject("club");
            JSONObject next = club == null ? null : club.optJSONObject("next");
            if (next != null) {
                long k = next.optLong("utc");
                if (now > k - 30 * 60_000L && now < k + 150 * 60_000L) return Math.min(normal, st.okAt + 15 * 60_000L);
            }
        } catch (JSONException ignored) {
            // normal schedule
        }
        return normal;
    }

    @Override
    public String fetch(Context ctx) throws Exception {
        Settings s = Settings.of(ctx);
        String key = s.footballKey();
        long now = System.currentTimeMillis();
        String code = s.sportLeague(), wanted = s.sportClub();
        JSONObject cached = cached(ctx);
        JSONObject leagues;
        long leaguesAt;
        if (reuseLeagues(cached, code, wanted, now)) {
            leagues = cached.getJSONObject("leagues");
            leaguesAt = cached.getLong("leaguesAt");
        } else {
            String from = day(now - 3 * DAY), to = day(now + 6 * DAY);
            leagues = new JSONObject();
            for (String c : FootballParser.LEAGUES) {
                JSONObject league = FootballParser.standings(get("competitions/" + c + "/standings", key), c);
                league.put("matches", FootballParser.matches(get("competitions/" + c + "/matches?dateFrom=" + from + "&dateTo=" + to, key)));
                leagues.put(c, league);
            }
            leaguesAt = now;
        }
        JSONObject out = new JSONObject().put("leagues", leagues).put("leaguesAt", leaguesAt);

        JSONObject row = clubRow(leagues.optJSONObject(code), wanted);
        if (row != null) {
            JSONArray matches = FootballParser.matches(get("teams/" + row.getInt("teamId") + "/matches?dateFrom="
                    + day(now - 20 * DAY) + "&dateTo=" + day(now + 20 * DAY), key));
            JSONObject[] ln = FootballParser.lastAndNext(matches, now);
            JSONObject club = new JSONObject().put("league", code).put("teamId", row.getInt("teamId"))
                    .put("name", row.optString("name")).put("pos", row.optInt("pos")).put("pts", row.optInt("pts"));
            if (ln[0] != null) club.put("last", ln[0]);
            if (ln[1] != null) club.put("next", ln[1]);
            out.put("club", club);
        }
        return out.toString();
    }

    private static JSONObject cached(Context ctx) {
        SourceState st = Cache.get(ctx, ID);
        if (st == null || !st.hasData()) return null;
        try {
            return new JSONObject(st.payload);
        } catch (JSONException e) {
            return null;
        }
    }

    /** True when only the followed club changed since the last fetch and its tables are recent enough to keep. */
    static boolean reuseLeagues(JSONObject cached, String league, String wanted, long now) {
        if (cached == null || cached.optJSONObject("leagues") == null) return false;
        long age = now - cached.optLong("leaguesAt");
        if (cached.optLong("leaguesAt") <= 0 || age < 0 || age >= REUSE_MS) return false;
        JSONObject club = cached.optJSONObject("club");
        boolean wantsClub = !"bih".equals(league) && wanted != null && !wanted.trim().isEmpty();
        return wantsClub ? !isFollowed(club, league, wanted) : club != null;
    }

    /** True when the payload's club part is the club currently chosen in the settings. */
    public static boolean isFollowed(JSONObject club, String league, String wanted) {
        if (club == null || wanted == null || !league.equals(club.optString("league"))) return false;
        String w = wanted.trim();
        return w.equals(String.valueOf(club.optInt("teamId"))) || w.equalsIgnoreCase(club.optString("name"));
    }

    /** The followed club's table row, by football-data team id or by name. */
    public static JSONObject clubRow(JSONObject league, String wanted) throws JSONException {
        if (league == null || wanted == null || wanted.trim().isEmpty()) return null;
        JSONArray t = league.getJSONArray("table");
        for (int i = 0; i < t.length(); i++) {
            JSONObject r = t.getJSONObject(i);
            if (wanted.trim().equals(String.valueOf(r.optInt("teamId"))) || wanted.trim().equalsIgnoreCase(r.optString("name"))) return r;
        }
        return null;
    }

    private static long nextCallAt;

    /**
     * Every football-data.org call goes through here, also the live score's: the free plan allows
     * 10 calls a minute, so each call waits until 7 seconds after the one before.
     */
    static String get(String path, String key) throws Exception {
        long wait;
        synchronized (FootballSource.class) {
            long t = System.nanoTime() / 1_000_000L;
            long at = Math.max(t, nextCallAt);
            nextCallAt = at + 7000;
            wait = at - t;
        }
        if (wait > 0) Thread.sleep(wait);
        return Http.get(API + path, 1024 * 1024, "X-Auth-Token", key);
    }

    private static String day(long t) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(t));
    }
}
