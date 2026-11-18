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
import ba.infotablet.data.SourceState;
import ba.infotablet.net.Http;
import ba.infotablet.sync.BaseSource;

/**
 * Premier League, Bundesliga and La Liga from football-data.org (free plan, key set on the device):
 * table and this week's matches per league, plus the followed club's matches in every competition.
 * Every 3 hours, every 15 minutes around the followed club's kickoff. The free plan allows 10 calls
 * a minute, so calls are spaced 7 seconds apart. Payload {leagues:{PL:{...}}, club:{...}|absent}.
 */
public final class FootballSource extends BaseSource {

    public static final String ID = "football";
    private static final String API = "https://api.football-data.org/v4/";
    private static final long DAY = 86_400_000L;

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
            JSONObject club = new JSONObject(st.payload).optJSONObject("club");
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
        String from = day(now - 3 * DAY), to = day(now + 6 * DAY);
        JSONObject leagues = new JSONObject();
        boolean first = true;
        for (String code : FootballParser.LEAGUES) {
            if (!first) pause();
            first = false;
            JSONObject league = FootballParser.standings(get("competitions/" + code + "/standings", key), code);
            pause();
            league.put("matches", FootballParser.matches(get("competitions/" + code + "/matches?dateFrom=" + from + "&dateTo=" + to, key)));
            leagues.put(code, league);
        }
        JSONObject out = new JSONObject().put("leagues", leagues);

        String code = s.sportLeague();
        JSONObject row = clubRow(leagues.optJSONObject(code), s.sportClub());
        if (row != null) {
            pause();
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

    /** The followed club's table row, by football-data team id or by name. */
    static JSONObject clubRow(JSONObject league, String wanted) throws JSONException {
        if (league == null || wanted == null || wanted.trim().isEmpty()) return null;
        JSONArray t = league.getJSONArray("table");
        for (int i = 0; i < t.length(); i++) {
            JSONObject r = t.getJSONObject(i);
            if (wanted.trim().equals(String.valueOf(r.optInt("teamId"))) || wanted.trim().equalsIgnoreCase(r.optString("name"))) return r;
        }
        return null;
    }

    private static String get(String path, String key) throws Exception {
        return Http.get(API + path, 1024 * 1024, "X-Auth-Token", key);
    }

    private static String day(long t) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(t));
    }

    /** The free plan allows 10 calls a minute. */
    private static void pause() throws InterruptedException {
        Thread.sleep(7000);
    }
}
