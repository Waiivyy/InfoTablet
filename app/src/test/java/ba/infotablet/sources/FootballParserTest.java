package ba.infotablet.sources;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class FootballParserTest {

    private static final String STANDINGS = "{\"competition\":{\"code\":\"PL\"},\"season\":{\"currentMatchday\":11},"
            + "\"standings\":[{\"stage\":\"REGULAR_SEASON\",\"type\":\"TOTAL\",\"table\":["
            + "{\"position\":1,\"team\":{\"id\":57,\"name\":\"Arsenal FC\",\"shortName\":\"Arsenal\",\"tla\":\"ARS\"},\"playedGames\":11,\"won\":8,\"draw\":2,\"lost\":1,\"points\":26,\"goalsFor\":20,\"goalsAgainst\":6,\"goalDifference\":14},"
            + "{\"position\":2,\"team\":{\"id\":65,\"name\":\"Manchester City FC\",\"shortName\":\"Man City\",\"tla\":\"MCI\"},\"playedGames\":11,\"points\":24,\"goalDifference\":-2}]},"
            + "{\"stage\":\"REGULAR_SEASON\",\"type\":\"HOME\",\"table\":[]}]}";

    private static String match(long id, String utc, String status, int hId, String home, int aId, String away, String hs, String as) {
        return "{\"id\":" + id + ",\"utcDate\":\"" + utc + "\",\"status\":\"" + status + "\",\"matchday\":12,"
                + "\"competition\":{\"code\":\"PL\"},"
                + "\"homeTeam\":{\"id\":" + hId + ",\"name\":\"" + home + " FC\",\"shortName\":\"" + home + "\"},"
                + "\"awayTeam\":{\"id\":" + aId + ",\"name\":\"" + away + " FC\",\"shortName\":\"" + away + "\"},"
                + "\"score\":{\"winner\":null,\"fullTime\":{\"home\":" + hs + ",\"away\":" + as + "},\"halfTime\":{\"home\":null,\"away\":null}}}";
    }

    @Test
    public void standingsUseTheTotalTable() throws Exception {
        JSONObject s = FootballParser.standings(STANDINGS, "PL");
        assertEquals("Premier League", s.getString("name"));
        assertEquals(11, s.getInt("matchday"));
        JSONArray t = s.getJSONArray("table");
        assertEquals(2, t.length());
        assertEquals("Arsenal", t.getJSONObject(0).getString("name"));
        assertEquals(57, t.getJSONObject(0).getInt("teamId"));
        assertEquals(-2, t.getJSONObject(1).getInt("gd"));
    }

    @Test
    public void matchesAndStatuses() throws Exception {
        String json = "{\"matches\":[" + match(1, "2026-11-21T15:00:00Z", "TIMED", 57, "Arsenal", 65, "Man City", "null", "null") + ","
                + match(2, "2026-11-14T15:00:00Z", "FINISHED", 61, "Chelsea", 57, "Arsenal", "1", "2") + ","
                + match(3, "2026-11-18T20:00:00Z", "IN_PLAY", 57, "Arsenal", 66, "Man United", "0", "0") + "]}";
        JSONArray m = FootballParser.matches(json);
        assertEquals("scheduled", m.getJSONObject(0).getString("status"));
        assertEquals(-1, m.getJSONObject(0).getInt("hs"));
        assertEquals("finished", m.getJSONObject(1).getString("status"));
        assertEquals(2, m.getJSONObject(1).getInt("as"));
        assertEquals("live", m.getJSONObject(2).getString("status"));
        assertEquals("PL", m.getJSONObject(2).getString("comp"));

        long now = ba.infotablet.util.Iso.parse("2026-11-18T20:30:00Z");
        JSONObject[] ln = FootballParser.lastAndNext(m, now);
        assertEquals(2, ln[0].getLong("id"));        // last finished
        assertEquals(3, ln[1].getLong("id"));        // the live one comes before Saturday's
        assertEquals("paused", FootballParser.status("PAUSED"));
        assertEquals("postponed", FootballParser.status("POSTPONED"));
    }

    @Test
    public void openLigaFindsTheClubAndTheScore() throws Exception {
        String json = "[{\"matchID\":83300,\"matchDateTimeUTC\":\"2026-11-21T14:30:00Z\",\"matchIsFinished\":false,"
                + "\"team1\":{\"teamId\":40,\"teamName\":\"FC Bayern München\",\"shortName\":\"Bayern\"},"
                + "\"team2\":{\"teamId\":7,\"teamName\":\"Borussia Dortmund\",\"shortName\":\"Dortmund\"},"
                + "\"matchResults\":[{\"resultTypeID\":1,\"pointsTeam1\":1,\"pointsTeam2\":0}],"
                + "\"goals\":[{\"scoreTeam1\":1,\"scoreTeam2\":0,\"matchMinute\":12},{\"scoreTeam1\":2,\"scoreTeam2\":0,\"matchMinute\":51},{\"scoreTeam1\":2,\"scoreTeam2\":1,\"matchMinute\":63}]},"
                + "{\"matchID\":83301,\"matchDateTimeUTC\":\"2026-11-21T14:30:00Z\",\"matchIsFinished\":true,"
                + "\"team1\":{\"teamName\":\"Borussia Mönchengladbach\",\"shortName\":\"Gladbach\"},\"team2\":{\"teamName\":\"1. FC Köln\",\"shortName\":\"Köln\"},"
                + "\"matchResults\":[{\"resultTypeID\":2,\"pointsTeam1\":3,\"pointsTeam2\":3}],\"goals\":[]}]";
        JSONObject bayern = FootballParser.openLiga(json, "Bayern");
        assertNotNull(bayern);
        assertEquals(2, bayern.getInt("hs"));
        assertEquals(1, bayern.getInt("as"));
        JSONObject dortmund = FootballParser.openLiga(json, "Dortmund");
        assertEquals(83300, dortmund.getLong("id"));
        JSONObject gladbach = FootballParser.openLiga(json, "M'gladbach");
        assertEquals("finished", gladbach.getString("status"));
        assertEquals(3, gladbach.getInt("hs"));
        assertNull(FootballParser.openLiga(json, "Freiburg"));
    }

    @Test
    public void clubRowByIdOrName() throws Exception {
        JSONObject league = FootballParser.standings(STANDINGS, "PL");
        assertEquals(65, FootballSource.clubRow(league, "65").getInt("teamId"));
        assertEquals(57, FootballSource.clubRow(league, "arsenal").getInt("teamId"));
        assertNull(FootballSource.clubRow(league, ""));
    }
}
