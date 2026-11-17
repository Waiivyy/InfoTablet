package ba.infotablet.update;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/** The parts of a GitHub release the app needs: its version, the notes and the APK file. */
public final class Release {

    /** The app is about 3 MB; anything far larger is not ours. */
    static final long MAX_APK_BYTES = 25L * 1024 * 1024;
    private static final int MAX_NOTES = 1500;

    public final String version;
    public final String notes;
    public final String apkName;
    public final String apkUrl;
    public final long apkSize;
    /** SHA-256 of the APK as lowercase hex, or null when GitHub did not give one. */
    public final String sha256;

    private Release(String version, String notes, String apkName, String apkUrl, long apkSize, String sha256) {
        this.version = version;
        this.notes = notes;
        this.apkName = apkName;
        this.apkUrl = apkUrl;
        this.apkSize = apkSize;
        this.sha256 = sha256;
    }

    /**
     * Reads the answer of {@code GET /repos/{repo}/releases/latest}. Throws when the release has no
     * usable APK from that repository; the text is shown to the user.
     */
    public static Release parse(String json, String repo) throws JSONException {
        JSONObject o = new JSONObject(json);
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) throw new JSONException("Objava nije konačna verzija.");
        String version = versionOf(o.optString("tag_name"));
        if (version == null) throw new JSONException("Oznaka objave nije broj verzije.");
        String prefix = "https://github.com/" + repo + "/releases/download/";
        JSONArray assets = o.optJSONArray("assets");
        for (int i = 0; assets != null && i < assets.length(); i++) {
            JSONObject a = assets.getJSONObject(i);
            String name = a.optString("name");
            String url = a.optString("browser_download_url");
            long size = a.optLong("size");
            if (!name.toLowerCase(Locale.ROOT).endsWith(".apk") || !"uploaded".equals(a.optString("state"))) continue;
            if (!url.startsWith(prefix) || size <= 0 || size > MAX_APK_BYTES) continue;
            return new Release(version, notes(o.optString("body")), name, url, size, sha256(a.optString("digest")));
        }
        throw new JSONException("Objava " + version + " nema APK datoteku.");
    }

    /** "v1.2.3" or "1.2.3" gives "1.2.3"; null when the tag is not one to three numbers. */
    static String versionOf(String tag) {
        if (tag == null) return null;
        String t = tag.trim();
        if (t.startsWith("v") || t.startsWith("V")) t = t.substring(1);
        return t.matches("\\d{1,4}(\\.\\d{1,4}){0,2}") ? t : null;
    }

    /** Below zero when {@code a} is older than {@code b}, zero when equal, above zero when newer. */
    public static int compare(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < 3; i++) {
            int xi = i < x.length ? Integer.parseInt(x[i]) : 0;
            int yi = i < y.length ? Integer.parseInt(y[i]) : 0;
            if (xi != yi) return xi < yi ? -1 : 1;
        }
        return 0;
    }

    /** "sha256:<64 hex>" from the asset, or null. */
    static String sha256(String digest) {
        if (digest == null || !digest.startsWith("sha256:")) return null;
        String hex = digest.substring(7).toLowerCase(Locale.ROOT);
        return hex.matches("[0-9a-f]{64}") ? hex : null;
    }

    /** Release notes as plain text: no carriage returns or Markdown markers, and not too long. */
    static String notes(String body) {
        if (body == null) return "";
        StringBuilder b = new StringBuilder();
        boolean gap = false;
        for (String line : body.replace("\r", "").split("\n")) {
            String l = line.trim().replaceFirst("^#+\\s*", "").replaceFirst("^[-*]\\s+", "· ").replace("**", "");
            if (l.isEmpty()) {
                gap = b.length() > 0;
                continue;
            }
            if (gap) b.append('\n');
            b.append(l).append('\n');
            gap = false;
        }
        String s = b.toString().trim();
        return s.length() > MAX_NOTES ? s.substring(0, MAX_NOTES).trim() + " …" : s;
    }
}
