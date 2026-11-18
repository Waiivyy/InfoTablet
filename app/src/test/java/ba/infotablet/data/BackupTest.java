package ba.infotablet.data;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ba.infotablet.reminders.Reminder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class BackupTest {

    @Test
    public void reminderRoundTrip() throws Exception {
        Reminder r = new Reminder();
        r.text = "Rođendan: Amra";
        r.year = 2027;
        r.month = 2;
        r.day = 28;
        r.hour = 9;
        r.minute = 30;
        r.repeat = Reminder.YEARLY;
        r.category = "rodjendan";
        r.handledUntil = 123L;
        Reminder back = Backup.fromJson(Backup.toJson(r));
        assertEquals("Rođendan: Amra", back.text);
        assertEquals(2027, back.year);
        assertEquals(28, back.day);
        assertEquals(Reminder.YEARLY, back.repeat);
        assertEquals("rodjendan", back.category);
        assertEquals(123L, back.handledUntil);
    }

    @Test
    public void brokenRemindersAreSkipped() throws Exception {
        JSONObject ok = Backup.toJson(sample());
        assertNull(Backup.fromJson(new JSONObject(ok.toString()).put("day", 31).put("month", 4)));
        assertNull(Backup.fromJson(new JSONObject(ok.toString()).put("category", "x")));
        assertNull(Backup.fromJson(new JSONObject(ok.toString()).put("text", " ")));
        assertNull(Backup.fromJson(new JSONObject(ok.toString()).put("repeat", 7)));
    }

    @Test
    public void foreignFilesAreRefusedBeforeAnythingChanges() {
        try {
            Backup.parse("{\"app\":\"Other\"}");
            fail();
        } catch (JSONException e) {
            assertEquals("Ovo nije rezervna kopija InfoTableta.", e.getMessage());
        }
        try {
            Backup.parse("{\"app\":\"InfoTablet\",\"format\":9,\"settings\":{},\"reminders\":[]}");
            fail();
        } catch (JSONException e) {
            assertEquals("Kopija je iz novije verzije aplikacije.", e.getMessage());
        }
    }

    @Test
    public void autoCopiesBeyondSevenAreDeletedButHandMadeOnesStay() {
        List<String> names = new ArrayList<String>();
        for (int day = 20; day >= 10; day--) names.add("infotablet-2026-11-" + day + "-0300-auto.json");
        names.add(3, "infotablet-2026-11-17-1530.json");
        names.add("infotablet-2026-01-02-0900.json");
        List<String> gone = Backup.autoToDelete(names);
        assertEquals(Arrays.asList("infotablet-2026-11-13-0300-auto.json", "infotablet-2026-11-12-0300-auto.json",
                "infotablet-2026-11-11-0300-auto.json", "infotablet-2026-11-10-0300-auto.json"), gone);
        assertEquals(0, Backup.autoToDelete(names.subList(0, 8)).size());
    }

    @Test
    public void autoNameSortsWithHandMadeCopies() {
        String auto = Backup.autoName(1763434800000L);
        String hand = Backup.fileName(1763434800000L);
        assertEquals(hand.replace(".json", "-auto.json"), auto);
        assertEquals(hand.substring(0, 26), auto.substring(0, 26));
    }

    @Test
    public void labels() {
        assertEquals("18.11.2026. u 03:00 · automatska", Backup.label("infotablet-2026-11-18-0300-auto.json"));
        assertEquals("02.01.2027. u 21:45", Backup.label("infotablet-2027-01-02-2145.json"));
        assertEquals("nesto", Backup.label("infotablet-nesto.json"));
    }

    @Test
    public void fingerprintIgnoresCreationTime() throws Exception {
        JSONObject a = new JSONObject().put("app", "InfoTablet").put("created", 1L).put("reminders", 3);
        JSONObject b = new JSONObject().put("app", "InfoTablet").put("created", 2L).put("reminders", 3);
        JSONObject c = new JSONObject().put("app", "InfoTablet").put("created", 2L).put("reminders", 4);
        assertEquals(Backup.fingerprint(a), Backup.fingerprint(b));
        assertNotEquals(Backup.fingerprint(a), Backup.fingerprint(c));
    }

    private static Reminder sample() {
        Reminder r = new Reminder();
        r.text = "Lijek";
        r.year = 2026;
        r.month = 12;
        r.day = 1;
        r.hour = 8;
        return r;
    }
}
