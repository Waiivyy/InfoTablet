package ba.infotablet.update;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

import ba.infotablet.BuildConfig;

/**
 * What the updater remembers, in its own preferences file so that backups and "Vrati zadano" do
 * not touch it: when it last checked, the last result, and the downloaded, verified new version.
 * Writes use commit(): they happen on the update thread, and installing kills the process right
 * after, so they must be on disk at once.
 */
@SuppressLint("ApplySharedPref")
public final class UpdateState {

    private final SharedPreferences prefs;

    private UpdateState(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public static UpdateState of(Context c) {
        return new UpdateState(c.getApplicationContext().getSharedPreferences("update", Context.MODE_PRIVATE));
    }

    /** Last check that finished (an answer from GitHub, whatever it was). */
    public long lastOk() {
        return prefs.getLong("last_ok", 0);
    }

    public long lastAttempt() {
        return prefs.getLong("last_attempt", 0);
    }

    public long lastFailTrigger() {
        return prefs.getLong("last_fail_trigger", 0);
    }

    /** Text of the last result, for Postavke. */
    public String result() {
        return prefs.getString("result", "");
    }

    public long resultAt() {
        return prefs.getLong("result_at", 0);
    }

    /** A version whose APK was refused; automatic checks do not download it again. */
    String rejected() {
        return prefs.getString("rejected", "");
    }

    void attempted(long now) {
        prefs.edit().putLong("last_attempt", now).commit();
    }

    void failTriggered(long now) {
        prefs.edit().putLong("last_fail_trigger", now).commit();
    }

    void finished(long now, String result) {
        prefs.edit().putLong("last_ok", now).putString("result", result).putLong("result_at", now).commit();
    }

    void failed(long now, String result) {
        prefs.edit().putString("result", result).putLong("result_at", now).commit();
    }

    void reject(String version) {
        prefs.edit().putString("rejected", version).commit();
    }

    // ---- downloaded update ----

    /** Version name of the verified APK waiting to be installed, or null. */
    public String readyVersion() {
        String v = prefs.getString("ready_version", "");
        return v.isEmpty() ? null : v;
    }

    public String readyNotes() {
        return prefs.getString("ready_notes", "");
    }

    /** The waiting APK, or null when there is none (or the file has gone). */
    public File readyFile(Context c) {
        String name = prefs.getString("ready_file", "");
        if (name.isEmpty()) return null;
        File f = new File(c.getFilesDir(), name);
        return f.isFile() ? f : null;
    }

    void setReady(String version, String fileName, int versionCode, String notes) {
        prefs.edit().putString("ready_version", version).putString("ready_file", fileName)
                .putInt("ready_code", versionCode).putString("ready_notes", notes).commit();
    }

    /**
     * After an update has been installed (or the file is gone) the waiting APK is deleted.
     * Returns the version that was installed, or null.
     */
    String settle(Context c) {
        String version = readyVersion();
        if (version == null) return null;
        boolean installed = prefs.getInt("ready_code", 0) <= BuildConfig.VERSION_CODE;
        if (!installed && readyFile(c) != null) return null;
        clearReady(c);
        return installed ? version : null;
    }

    void clearReady(Context c) {
        prefs.edit().remove("ready_version").remove("ready_file").remove("ready_code").remove("ready_notes").commit();
        Updater.deleteDownloads(c);
    }
}
