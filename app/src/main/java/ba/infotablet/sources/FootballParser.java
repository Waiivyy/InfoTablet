package ba.infotablet.sources;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

import ba.infotablet.util.Iso;

/**
 * football-data.org v4 (Premier League, Bundesliga, La Liga: tables, matches, the followed club)
 * and OpenLigaDB (live Bundesliga scores) into one compact match shape:
 * {id, utc, status, home, away, homeId, awayId, hs, as, matchday, comp}. hs/as are -1 when unknown.
 * Status is one of "scheduled", "live", "paused", "finished", "postponed".
 */
public final class FootballParser {

    public static final String[] LEAGUES = {"PL", "BL1", "PD"};
    public static final String[] LEAGUE_NAMES = {"Premier League", "Bundesliga", "La Liga"};

    private FootballParser() {}

    public static String leagueName(String code) {
        for (int i = 0; i < LEAGUES.length; i++) if (LEAGUES[i].equals(code)) return LEAGUE_NAMES[i];
        return code;
    }

    // ---- football-data.org ----

    /** /v4/competitions/{code}/standings: {code, name, matchday, table:[{pos, teamId, name, played, gd, pts}]}. */
    public static JSONObject standings(String json, String code) throws JSONException {
        JSONObject o = new JSONObject(json);
        JSONArray groups = o.getJSONArray("standings");
        JSONArray total = null;
        for (int i = 0; i < groups.length(); i++) {
            JSONObject g = groups.getJSONObject(i);
            if ("TOTAL".equals(g.optString("type"))) {
                total = g.getJSONArray("table");
                break;
            }
        }
        if (total == null || total.length() == 0) throw new JSONException("No total table for " + code);
        JSONArray table = new JSONArray();
        for (int i = 0; i < total.length(); i++) {
            JSONObject r = total.getJSONObject(i);
            JSONObject team = r.getJSONObject("team");
            table.put(new JSONObject()
                    .put("pos", r.optInt("position"))
                    .put("teamId", team.optInt("id"))
                    .put("name", teamName(team))
                    .put("played", r.optInt("playedGames"))
                    .put("gd", r.optInt("goalDifference"))
                    .put("pts", r.optInt("points")));
        }
        JSONObject season = o.optJSONObject("season");
        return new JSONObject().put("code", code).put("name", leagueName(code))
                .put("matchday", season == null ? 0 : season.optInt("currentMatchday")).put("table", table);
    }

    /** /v4/.../matches: the compact matches, in the order given (football-data sorts by kickoff). */
    public static JSONArray matches(String json) throws JSONException {
        JSONArray in = new JSONObject(json).getJSONArray("matches");
        JSONArray out = new JSONArray();
        for (int i = 0; i < in.length(); i++) out.put(match(in.getJSONObject(i)));
        return out;
    }

    /** One football-data match object (also the body of /v4/matches/{id}). */
    public static JSONObject match(JSONObject m) throws JSONException {
        JSONObject home = m.getJSONObject("homeTeam"), away = m.getJSONObject("awayTeam");
        JSONObject score = m.optJSONObject("score");
        JSONObject ft = score == null ? null : score.optJSONObject("fullTime");
        JSONObject comp = m.optJSONObject("competition");
        return new JSONObject()
                .put("id", m.optLong("id"))
                .put("utc", Iso.parse(m.optString("utcDate")))
                .put("status", status(m.optString("status")))
                .put("home", teamName(home)).put("homeId", home.optInt("id"))
                .put("away", teamName(away)).put("awayId", away.optInt("id"))
                .put("hs", goals(ft, "home")).put("as", goals(ft, "away"))
                .put("matchday", m.optInt("matchday"))
                .put("comp", comp == null ? "" : comp.optString("code"));
    }

    static String status(String s) {
        switch (s) {
            case "IN_PLAY": case "LIVE": return "live";
            case "PAUSED": return "paused";
            case "FINISHED": case "AWARDED": return "finished";
            case "POSTPONED": case "SUSPENDED": case "CANCELLED": return "postponed";
            default: return "scheduled";
        }
    }

    private static int goals(JSONObject ft, String side) {
        return ft == null || ft.isNull(side) ? -1 : ft.optInt(side, -1);
    }

    /** Short names read better on the tablet: "Arsenal" rather than "Arsenal FC". */
    static String teamName(JSONObject team) {
        String s = team.optString("shortName");
        if (s.isEmpty() || "null".equals(s)) s = team.optString("name");
        return s;
    }

    /** The followed club's last finished match and its next (or live) one: [last, next], either may be null. */
    public static JSONObject[] lastAndNext(JSONArray matches, long now) throws JSONException {
        JSONObject last = null, next = null;
        for (int i = 0; i < matches.length(); i++) {
            JSONObject m = matches.getJSONObject(i);
            String st = m.optString("status");
            long t = m.optLong("utc");
            if ("finished".equals(st)) {
                if (last == null || t > last.optLong("utc")) last = m;
            } else if (!"postponed".equals(st) && (t > now - 3 * 3600_000L || "live".equals(st) || "paused".equals(st))) {
                if (next == null || t < next.optLong("utc")) next = m;
            }
        }
        return new JSONObject[] {last, next};
    }

    // ---- OpenLigaDB (Bundesliga live) ----

    /**
     * The match of the current Bundesliga matchday (getmatchdata/bl1) that the followed club plays,
     * found by name, with the current score from the goal list. Null when not found.
     */
    public static JSONObject openLiga(String json, String clubName) throws JSONException {
        JSONArray in = new JSONArray(json);
        String want = fold(clubName);
        for (int i = 0; i < in.length(); i++) {
            JSONObject m = in.getJSONObject(i);
            JSONObject t1 = m.getJSONObject("team1"), t2 = m.getJSONObject("team2");
            if (!sameClub(want, t1) && !sameClub(want, t2)) continue;
            int hs = -1, as = -1;
            JSONArray goals = m.optJSONArray("goals");
            if (goals != null && goals.length() > 0) {
                JSONObject g = goals.getJSONObject(goals.length() - 1);
                hs = g.optInt("scoreTeam1");
                as = g.optInt("scoreTeam2");
            }
            JSONArray results = m.optJSONArray("matchResults");
            for (int k = 0; results != null && k < results.length(); k++) {
                JSONObject r = results.getJSONObject(k);
                if (r.optInt("resultTypeID") == 2 || hs < 0) {   // the final result, or half time before any goal list
                    hs = Math.max(hs, r.optInt("pointsTeam1"));
                    as = Math.max(as, r.optInt("pointsTeam2"));
                }
            }
            long kickoff = Iso.parse(m.optString("matchDateTimeUTC"));
            boolean finished = m.optBoolean("matchIsFinished");
            String status = finished ? "finished" : kickoff > System.currentTimeMillis() ? "scheduled" : "live";
            if ("live".equals(status) && hs < 0) {
                hs = 0;
                as = 0;
            }
            return new JSONObject().put("id", m.optLong("matchID")).put("utc", kickoff).put("status", status)
                    .put("home", t1.optString("shortName", t1.optString("teamName"))).put("away", t2.optString("shortName", t2.optString("teamName")))
                    .put("hs", hs).put("as", as).put("comp", "BL1");
        }
        return null;
    }

    private static boolean sameClub(String want, JSONObject team) {
        String a = fold(team.optString("teamName")), b = fold(team.optString("shortName"));
        return !want.isEmpty() && (a.contains(want) || want.contains(b) && !b.isEmpty() || b.equals(want));
    }

    /** Lower case without accents and club prefixes, for matching names across the two sources. */
    static String fold(String s) {
        String t = s.toLowerCase(Locale.ROOT)
                .replace('ü', 'u').replace('ö', 'o').replace('ä', 'a').replace("ß", "ss")
                .replace('é', 'e').replace('á', 'a').replace('í', 'i').replace('ó', 'o').replace('ñ', 'n');
        t = t.replaceAll("\\b(fc|sv|vfl|vfb|tsg|1\\.|fsv|sc|bv|borussia|hertha|bsc|ac|cf|rcd|cd|ud|sd|afc)\\b", " ");
        return t.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }
}
