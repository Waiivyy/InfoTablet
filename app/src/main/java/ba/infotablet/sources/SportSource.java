package ba.infotablet.sources;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import ba.infotablet.Settings;
import ba.infotablet.data.SourceState;
import ba.infotablet.net.Http;
import ba.infotablet.sync.BaseSource;

/**
 * Football from nfsbih.ba (the BiH football federation): WWin liga table, the national team and the
 * club chosen in settings. Every 3 hours, every 15 minutes around a relevant kickoff.
 * Payload {table, national:{next,last}, club:{teamId,name,pos,pts,next,last}|absent}.
 */
public final class SportSource extends BaseSource {

    public static final String ID = "sport";
    private static final String BASE = "https://www.nfsbih.ba";
    private static final String TABLE = BASE + "/competitions/football-men/wwin-league-bh/";
    private static final String NATIONAL = BASE + "/reprezentacije/muske-reprezentacije/a-reprezentacija/";

    @Override public String id() { return ID; }

    @Override public String name() { return "Sport (nfsbih.ba)"; }

    @Override public long intervalMs(long now) { return 3 * 3600_000L; }

    @Override public long staleAfterMs() { return 12 * 3600_000L; }

    @Override
    public long dueAt(SourceState st, long now) {
        long normal = super.dueAt(st, now);
        if (st.failing() || !st.hasData()) return normal;
        try {
            JSONObject p = new JSONObject(st.payload);
            for (JSONObject m : new JSONObject[] {nextOf(p.optJSONObject("national")), nextOf(p.optJSONObject("club"))}) {
                if (m == null) continue;
                long k = m.optLong("kickoff");
                if (now > k - 30 * 60_000L && now < k + 150 * 60_000L) return Math.min(normal, st.okAt + 15 * 60_000L);
            }
        } catch (JSONException ignored) {
            // normal schedule
        }
        return normal;
    }

    private static JSONObject nextOf(JSONObject o) {
        return o == null ? null : o.optJSONObject("next");
    }

    @Override
    public String fetch(Context ctx) throws Exception {
        long now = System.currentTimeMillis();
        JSONObject table = SportParser.table(Http.get(TABLE), BASE);
        JSONObject out = new JSONObject().put("table", table);

        pause();
        JSONObject national = new JSONObject();
        JSONObject next = SportParser.nationalNext(Http.get(NATIONAL));
        pause();
        int year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        String resultsHtml;
        try {
            resultsHtml = Http.get(resultsUrl(year));
        } catch (java.io.IOException e) {
            pause(); // early January: this year's results page may not exist yet
            resultsHtml = Http.get(resultsUrl(year - 1));
        }
        List<JSONObject> results = SportParser.nationalResults(resultsHtml);
        JSONObject last = null;
        for (JSONObject r : results) {
            if (!r.optString("score").isEmpty() && (last == null || r.optLong("date") > last.optLong("date"))) last = r;
            if (next == null && r.optString("score").isEmpty() && r.optLong("date") >= now - 86_400_000L) next = r;
        }
        if (next != null) national.put("next", next);
        if (last != null) national.put("last", last);
        out.put("national", national);

        String wanted = Settings.of(ctx).sportClub();
        JSONObject row = findClub(table.getJSONArray("rows"), wanted);
        if (row != null) {
            pause();
            List<JSONObject> matches = SportParser.clubMatches(Http.get(row.getString("href")), now);
            JSONObject[] ln = SportParser.lastAndNext(matches, now);
            JSONObject club = new JSONObject()
                    .put("teamId", row.optInt("teamId"))
                    .put("name", row.optString("club"))
                    .put("pos", row.optInt("pos"))
                    .put("pts", row.optInt("pts"));
            if (ln[0] != null) club.put("last", ln[0]);
            if (ln[1] != null) club.put("next", ln[1]);
            out.put("club", club);
        }
        return out.toString();
    }

    /** The national team results article; its URL contains the year. */
    private static String resultsUrl(int year) {
        return NATIONAL + "rezultati-a-reprezentacije/rezultati-a-reprezentacije-za-" + year + "-godinu/";
    }

    /** Matches the setting by nfsbih team id ("409") or by club name ("FK Sarajevo"). */
    static JSONObject findClub(JSONArray rows, String wanted) throws JSONException {
        if (wanted == null || wanted.trim().isEmpty()) return null;
        String w = wanted.trim();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject r = rows.getJSONObject(i);
            if (w.equals(String.valueOf(r.optInt("teamId"))) || w.equalsIgnoreCase(r.optString("club"))) return r;
        }
        return null;
    }

    private static void pause() throws InterruptedException {
        Thread.sleep(1200);
    }
}
