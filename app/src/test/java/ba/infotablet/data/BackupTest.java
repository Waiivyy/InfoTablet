package ba.infotablet.data;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import ba.infotablet.reminders.Reminder;

import static org.junit.Assert.assertEquals;
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
