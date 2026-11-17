package ba.infotablet.sources;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import ba.infotablet.Fixtures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class SportParserTest {

    private static final long NOW = 1794873600000L; // 17.11.2026 01:00 Sarajevo

    @Test
    public void leagueTableWithPenaltyAndTeamIds() throws Exception {
        JSONObject t = SportParser.table(Fixtures.read("nfsbih_table_bs.html"), "https://www.nfsbih.ba");
        assertEquals("WWIN liga BiH 26/27", t.getString("title"));
        JSONArray rows = t.getJSONArray("rows");
        assertEquals(10, rows.length());
        JSONObject first = rows.getJSONObject(0);
        assertEquals(1, first.getInt("pos"));
        assertEquals("HŠK Zrinjski", first.getString("club"));
        assertEquals(517, first.getInt("teamId"));
        assertEquals(34, first.getInt("pts"));
        assertEquals(31, first.getInt("gf"));
        assertEquals(7, first.getInt("ga"));
        assertEquals("(-1)", rows.getJSONObject(1).getString("penalty"));
        JSONObject sarajevo = rows.getJSONObject(2);
        assertEquals("FK Sarajevo", sarajevo.getString("club"));
        assertEquals(409, sarajevo.getInt("teamId"));
        assertEquals("https://www.nfsbih.ba/stats/team/54152223/409-fk-sarajevo/", sarajevo.getString("href"));
        assertEquals("NK Široki Brijeg", rows.getJSONObject(3).getString("club"));
    }

    @Test
    public void clubLastAndNextDecidedByKickoffNotByScoreText() throws Exception {
        List<JSONObject> m = SportParser.clubMatches(Fixtures.read("nfsbih_club_bs.html"), NOW);
        assertEquals(14, m.size());
        JSONObject[] ln = SportParser.lastAndNext(m, NOW);
        assertEquals("HŠK Zrinjski", ln[0].getString("home"));
        assertEquals("FK Sarajevo", ln[0].getString("away"));
        assertEquals("0:0", ln[0].getString("ft"));
        assertEquals(1794155400000L, ln[0].getLong("kickoff"));
        assertEquals("NK Široki Brijeg", ln[1].getString("away"));
        assertEquals("", ln[1].getString("ft")); // "(0:0) 0:0" on a future match is not a result
        assertEquals(1795365000000L, ln[1].getLong("kickoff"));
    }

    @Test
    public void nationalNextFromCountdownBox() throws Exception {
        JSONObject n = SportParser.nationalNext(Fixtures.read("nfsbih_ateam_bs.html"));
        assertNotNull(n);
        assertEquals("Bosna i Hercegovina", n.getString("home"));
        assertEquals("Rumunija", n.getString("away"));
        assertEquals(1794944700000L, n.getLong("kickoff"));
        assertEquals("Stadion Bilino Polje, Zenica", n.getString("venue"));
        assertEquals("UEFA NATIONS LEAGUE 2026/2027", n.getString("competition"));
    }

    @Test
    public void nationalResultsSkipEmptyRows() throws Exception {
        List<JSONObject> r = SportParser.nationalResults(Fixtures.read("nfsbih_results_bs.html"));
        JSONObject last = null;
        for (JSONObject o : r) if (!o.getString("score").isEmpty()) last = o;
        assertNotNull(last);
        assertEquals("Švedska", last.getString("home"));
        assertEquals("Bosna i Hercegovina", last.getString("away"));
        assertEquals("3:2", last.getString("score"));
        assertEquals(1794610800000L, last.getLong("date"));
        assertEquals("", r.get(r.size() - 1).getString("score")); // BiH vs Romania not played yet
    }

    @Test
    public void clubSettingByIdOrName() throws Exception {
        JSONArray rows = SportParser.table(Fixtures.read("nfsbih_table_bs.html"), "https://www.nfsbih.ba").getJSONArray("rows");
        assertEquals("FK Sarajevo", SportSource.findClub(rows, "409").getString("club"));
        assertEquals("FK Željezničar", SportSource.findClub(rows, "fk željezničar").getString("club"));
        assertNull(SportSource.findClub(rows, ""));
        assertNull(SportSource.findClub(rows, "Real Madrid"));
    }

    @Test
    public void missingCountdownGivesNull() throws Exception {
        assertNull(SportParser.nationalNext("<html><body><p>Nema zakazanih utakmica</p></body></html>"));
    }
}
