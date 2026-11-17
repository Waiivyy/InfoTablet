package ba.infotablet.update;

import org.json.JSONException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ReleaseTest {

    private static final String REPO = "owner/InfoTablet";
    private static final String HEX = "0ab2b9b5103bd3173cb4540f9a2fe660d93a44ac17254e7d13b26d1d8f012192";

    private static String release(String tag, String assets) {
        return "{\"tag_name\":\"" + tag + "\",\"draft\":false,\"prerelease\":false,"
                + "\"body\":\"## Novo\\r\\n\\r\\n- Brže vijesti\\r\\n- **Ispravka** sata\\r\\n\\r\\n\\r\\nKraj\","
                + "\"assets\":[" + assets + "]}";
    }

    private static String asset(String name, long size, String url) {
        return "{\"name\":\"" + name + "\",\"state\":\"uploaded\",\"size\":" + size
                + ",\"digest\":\"sha256:" + HEX.toUpperCase() + "\",\"browser_download_url\":\"" + url + "\"}";
    }

    @Test
    public void picksTheApkOfThisRepository() throws JSONException {
        String url = "https://github.com/owner/InfoTablet/releases/download/v1.2.0/InfoTablet-1.2.0.apk";
        Release r = Release.parse(release("v1.2.0",
                asset("notes.txt", 10, "https://github.com/owner/InfoTablet/releases/download/v1.2.0/notes.txt") + ","
                        + asset("InfoTablet-1.2.0.apk", 3_000_000, url)), REPO);
        assertEquals("1.2.0", r.version);
        assertEquals(url, r.apkUrl);
        assertEquals(3_000_000, r.apkSize);
        assertEquals(HEX, r.sha256);
        assertEquals("Novo\n\n· Brže vijesti\n· Ispravka sata\n\nKraj", r.notes);
    }

    @Test
    public void refusesApksFromElsewhereOrTooLarge() {
        String[] bad = {
                asset("x.apk", 3_000_000, "https://github.com/other/InfoTablet/releases/download/v1.2.0/x.apk"),
                asset("x.apk", 3_000_000, "http://github.com/owner/InfoTablet/releases/download/v1.2.0/x.apk"),
                asset("x.apk", 90_000_000, "https://github.com/owner/InfoTablet/releases/download/v1.2.0/x.apk"),
                asset("x.apk", 0, "https://github.com/owner/InfoTablet/releases/download/v1.2.0/x.apk"),
                asset("x.zip", 3_000_000, "https://github.com/owner/InfoTablet/releases/download/v1.2.0/x.zip"),
        };
        for (String a : bad) {
            try {
                Release.parse(release("v1.2.0", a), REPO);
                fail("accepted " + a);
            } catch (JSONException expected) {
                // refused
            }
        }
    }

    @Test
    public void refusesDraftsPrereleasesAndOddTags() {
        String a = asset("x.apk", 3, "https://github.com/owner/InfoTablet/releases/download/v1/x.apk");
        for (String json : new String[] {
                release("nightly", a),
                release("v1.2.0", a).replace("\"prerelease\":false", "\"prerelease\":true"),
                release("v1.2.0", a).replace("\"draft\":false", "\"draft\":true")}) {
            try {
                Release.parse(json, REPO);
                fail("accepted " + json);
            } catch (JSONException expected) {
                // refused
            }
        }
    }

    @Test
    public void versions() {
        assertEquals("1.2.3", Release.versionOf("v1.2.3"));
        assertEquals("2", Release.versionOf("V2"));
        assertNull(Release.versionOf("1.2.3-beta"));
        assertNull(Release.versionOf("1.2.3.4"));
        assertTrue(Release.compare("1.10.0", "1.9.9") > 0);
        assertTrue(Release.compare("1.0", "1.0.1") < 0);
        assertEquals(0, Release.compare("1", "1.0.0"));
    }

    @Test
    public void digestAndNotes() {
        assertNull(Release.sha256("sha512:" + HEX));
        assertNull(Release.sha256("sha256:abc"));
        assertNull(Release.sha256(null));
        assertEquals("", Release.notes(null));
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 400; i++) longText.append("riječ ");
        String n = Release.notes(longText.toString());
        assertTrue(n.length() <= 1502);
        assertTrue(n.endsWith("…"));
    }

    @Test
    public void whenToCheck() {
        long day = 86_400_000L;
        long now = 1_800_000_000_000L;
        // never checked
        assertEquals("mjesečno", Updater.due(now, 0, 0, 0, 0));
        // checked yesterday, sources fine
        assertNull(Updater.due(now, now - day, now - day, 0, 0));
        // checked 31 days ago
        assertEquals("mjesečno", Updater.due(now, now - 31 * day, now - 31 * day, 0, 0));
        // a source failing for 2 days: check at once, even right after a monthly check...
        assertEquals("izvor", Updater.due(now, now - 2 * Updater.RETRY, now - 2 * Updater.RETRY, 0, 2 * day));
        // ...but not twice within a day
        assertNull(Updater.due(now, now - 2 * Updater.RETRY, now - 2 * Updater.RETRY, now - day / 2, 3 * day));
        assertEquals("izvor", Updater.due(now, now - 2 * day, now - 2 * day, now - day, 4 * day));
        // failing for a day and a half is not enough
        assertNull(Updater.due(now, now - day, now - day, 0, day + day / 2));
        // a check that did not finish is retried after an hour, not every 30 seconds
        assertNull(Updater.due(now, 0, now - 10 * 60_000L, 0, 0));
        assertEquals("mjesečno", Updater.due(now, 0, now - Updater.RETRY, 0, 0));
        // dates in the future (clock moved back) count as never
        assertEquals("mjesečno", Updater.due(now, now + 40 * day, now + 40 * day, now + 40 * day, 0));
    }

    @Test
    public void downloadHosts() {
        assertTrue(Updater.allowedUrl("https://github.com/owner/InfoTablet/releases/download/v1/x.apk"));
        assertTrue(Updater.allowedUrl("https://release-assets.githubusercontent.com/github-production-release-asset/1?sig=a"));
        assertTrue(Updater.allowedUrl("https://objects.githubusercontent.com/x"));
        assertFalse(Updater.allowedUrl("http://github.com/x"));
        assertFalse(Updater.allowedUrl("https://github.com.example.org/x"));
        assertFalse(Updater.allowedUrl("https://evilgithubusercontent.com/x"));
        assertFalse(Updater.allowedUrl("https://example.org/github.com"));
        assertFalse(Updater.allowedUrl("not a url"));
    }
}
