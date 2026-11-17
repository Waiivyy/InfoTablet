package ba.infotablet.web;

import org.junit.Test;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import ba.infotablet.reminders.Reminder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WebTest {

    private static long at(int y, int m, int d, int h, int min) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, d, h, min);
        return c.getTimeInMillis();
    }

    private static Map<String, String> form(String text, String date, String time, String repeat, String category) {
        Map<String, String> f = new HashMap<String, String>();
        f.put("text", text);
        f.put("date", date);
        f.put("time", time);
        f.put("repeat", repeat);
        f.put("category", category);
        return f;
    }

    @Test
    public void validFormBecomesAReminder() {
        ReminderForm f = ReminderForm.parse(form("  Rođendan:\tAmra  ", "2026-12-03", "09:30", "4", "rodjendan"), at(2026, 11, 17, 12, 0));
        assertNull(f.error);
        Reminder r = f.reminder;
        assertEquals("Rođendan: Amra", r.text);
        assertEquals(2026, r.year);
        assertEquals(12, r.month);
        assertEquals(3, r.day);
        assertEquals(9, r.hour);
        assertEquals(30, r.minute);
        assertEquals(Reminder.YEARLY, r.repeat);
    }

    @Test
    public void formErrorsAreExplained() {
        long now = at(2026, 11, 17, 12, 0);
        assertEquals("Upišite šta treba zapamtiti.", ReminderForm.parse(form(" ", "2026-12-03", "09:30", "0", ""), now).error);
        assertEquals("Ovo vrijeme je već prošlo.", ReminderForm.parse(form("x", "2026-11-17", "11:59", "0", ""), now).error);
        assertNull(ReminderForm.parse(form("x", "2026-11-17", "11:59", "1", ""), now).error); // repeating may start in the past
        assertEquals("Taj datum ne postoji.", ReminderForm.parse(form("x", "2027-02-29", "10:00", "0", ""), now).error);
        assertEquals("Neispravan unos.", ReminderForm.parse(form("x", "2026-12-03", "10:00", "9", ""), now).error);
        assertEquals("Neispravan unos.", ReminderForm.parse(form("x", "2026-12-03", "10:00", "0", "<script>"), now).error);
        assertEquals("Odaberite vrijeme.", ReminderForm.parse(form("x", "2026-12-03", "", "0", ""), now).error);
    }

    @Test
    public void htmlIsEscaped() {
        assertEquals("&lt;b&gt; &amp; &quot;x&quot; &#39;y&#39;", Pages.esc("<b> & \"x\" 'y'"));
        Reminder r = new Reminder();
        r.text = "<img src=x onerror=alert(1)>";
        r.year = 2026;
        r.month = 12;
        r.day = 1;
        String html = Pages.form(r, false, null, "tok");
        assertFalse(html.contains("<img src=x"));
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"));
    }

    @Test
    public void onlyTheHomeNetworkIsServed() {
        assertTrue(WebServer.fromHomeNetwork("192.168.1.40"));
        assertTrue(WebServer.fromHomeNetwork("10.0.0.7"));
        assertTrue(WebServer.fromHomeNetwork("172.20.1.2"));
        assertTrue(WebServer.fromHomeNetwork("127.0.0.1"));
        assertFalse(WebServer.fromHomeNetwork("8.8.8.8"));
        assertFalse(WebServer.fromHomeNetwork("172.32.0.1"));
        assertFalse(WebServer.fromHomeNetwork(null));
    }

    @Test
    public void foreignOriginsAreRejected() {
        assertTrue(WebServer.sameOrigin("http://192.168.1.23:8080", "192.168.1.23:8080"));
        assertTrue(WebServer.sameOrigin(null, "192.168.1.23:8080"));
        assertFalse(WebServer.sameOrigin("http://evil.example", "192.168.1.23:8080"));
    }
}
