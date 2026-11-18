package ba.infotablet.data;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import ba.infotablet.Settings;
import ba.infotablet.reminders.Reminder;
import ba.infotablet.reminders.ReminderStore;

/**
 * Settings plus reminders as one JSON file, in the "InfoTablet" folder of the tablet's storage
 * (it survives reinstalling the app) or downloaded from the Wi-Fi page. The PIN is never written
 * to the file; after an import the tablet keeps its current PIN. Besides the copies made by hand,
 * one is written automatically each day when something changed, and the newest seven are kept.
 */
public final class Backup {

    public static final String FOLDER = "InfoTablet";
    private static final int FORMAT = 1;
    private static final List<String> NEVER_EXPORTED = Arrays.asList("pin_hash", "football_key");

    private Backup() {}

    public static JSONObject create(Context ctx, long now) throws JSONException {
        JSONObject settings = new JSONObject();
        for (Map.Entry<String, ?> e : new TreeMap<String, Object>(Settings.of(ctx).all()).entrySet()) {
            if (NEVER_EXPORTED.contains(e.getKey())) continue;
            Object v = e.getValue();
            String type = v instanceof Boolean ? "b" : v instanceof Integer ? "i" : v instanceof Long ? "l" : v instanceof String ? "s" : null;
            if (type != null) settings.put(e.getKey(), new JSONObject().put("t", type).put("v", v));
        }
        JSONArray reminders = new JSONArray();
        for (Reminder r : ReminderStore.all(ctx)) reminders.put(toJson(r));
        return new JSONObject().put("app", "InfoTablet").put("format", FORMAT).put("created", now)
                .put("settings", settings).put("reminders", reminders);
    }

    public static JSONObject toJson(Reminder r) throws JSONException {
        return new JSONObject().put("text", r.text).put("year", r.year).put("month", r.month).put("day", r.day)
                .put("hour", r.hour).put("minute", r.minute).put("repeat", r.repeat).put("category", r.category)
                .put("handledUntil", r.handledUntil).put("createdAt", r.createdAt).put("updatedAt", r.updatedAt)
                .put("ring", r.ring).put("allDay", r.allDay);
    }

    /** A reminder from the file, or null when a field is missing or out of range. */
    public static Reminder fromJson(JSONObject o) {
        Reminder r = new Reminder();
        r.text = o.optString("text", "").trim();
        r.year = o.optInt("year");
        r.month = o.optInt("month");
        r.day = o.optInt("day");
        r.hour = o.optInt("hour");
        r.minute = o.optInt("minute");
        r.repeat = o.optInt("repeat", -1);
        r.category = o.optString("category", "");
        r.handledUntil = o.optLong("handledUntil");
        r.createdAt = o.optLong("createdAt");
        r.updatedAt = o.optLong("updatedAt");
        r.ring = o.optBoolean("ring", true);   // copies from before plans existed always ring
        r.allDay = o.optBoolean("allDay", false);
        if (r.allDay) r.setMode(Reminder.MODE_ALL_DAY);
        boolean ok = !r.text.isEmpty() && r.text.length() <= 200 && r.repeat >= Reminder.ONCE && r.repeat <= Reminder.YEARLY
                && Reminder.validCategory(r.category)
                && ba.infotablet.reminders.Recurrence.validDate(r.year, r.month, r.day, r.hour, r.minute);
        return ok ? r : null;
    }

    /** Checks a file before anything is changed; throws with a readable reason. */
    public static JSONObject parse(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        if (!"InfoTablet".equals(o.optString("app"))) throw new JSONException("Ovo nije rezervna kopija InfoTableta.");
        if (o.optInt("format") > FORMAT) throw new JSONException("Kopija je iz novije verzije aplikacije.");
        if (o.optJSONObject("settings") == null || o.optJSONArray("reminders") == null) throw new JSONException("Kopija je nepotpuna.");
        return o;
    }

    /** Replaces settings and reminders with the backup's (the PIN stays). Returns the number of reminders. */
    public static int restore(Context ctx, JSONObject backup) throws JSONException {
        JSONObject s = backup.getJSONObject("settings");
        Map<String, Object> values = new HashMap<String, Object>();
        for (Iterator<String> it = s.keys(); it.hasNext(); ) {
            String k = it.next();
            if (NEVER_EXPORTED.contains(k)) continue;
            JSONObject e = s.getJSONObject(k);
            String t = e.optString("t");
            if ("b".equals(t)) values.put(k, e.getBoolean("v"));
            else if ("i".equals(t)) values.put(k, e.getInt("v"));
            else if ("l".equals(t)) values.put(k, e.getLong("v"));
            else if ("s".equals(t)) values.put(k, e.getString("v"));
        }
        List<Reminder> reminders = new ArrayList<Reminder>();
        JSONArray a = backup.getJSONArray("reminders");
        for (int i = 0; i < a.length(); i++) {
            Reminder r = fromJson(a.getJSONObject(i));
            if (r != null) reminders.add(r);
        }
        Settings st = Settings.of(ctx);
        st.reset();
        st.restore(values);
        ReminderStore.clearForRestore(ctx);
        for (Reminder r : reminders) ReminderStore.restore(ctx, r);
        return reminders.size();
    }

    public static File folder() {
        return new File(Environment.getExternalStorageDirectory(), FOLDER);
    }

    public static String fileName(long now) {
        return "infotablet-" + new SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.ROOT).format(new Date(now)) + ".json";
    }

    /** Writes a backup file and returns it. */
    public static File export(Context ctx, long now) throws IOException, JSONException {
        return write(fileName(now), create(ctx, now));
    }

    private static File write(String name, JSONObject backup) throws IOException, JSONException {
        File dir = folder();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Mapa " + dir + " se ne može napraviti.");
        File f = new File(dir, name);
        OutputStream out = new FileOutputStream(f);
        try {
            out.write(backup.toString(1).getBytes("UTF-8"));
        } finally {
            out.close();
        }
        return f;
    }

    // ---- automatic daily copy ----

    private static final String AUTO_SUFFIX = "-auto.json";
    static final int AUTO_KEEP = 7;
    private static final long DAY_MS = 86_400_000L;

    static String autoName(long now) {
        return fileName(now).replace(".json", AUTO_SUFFIX);
    }

    /**
     * From the scheduler loop: at most once a day, writes a copy when settings or reminders changed
     * since the last automatic one, then deletes automatic copies beyond the newest seven. Copies
     * made by hand are never deleted.
     */
    @SuppressLint("ApplySharedPref") // background thread; the time must survive a crash right after
    public static void maybeAuto(Context ctx, long now) {
        SharedPreferences sp = ctx.getSharedPreferences("backup", Context.MODE_PRIVATE);
        long last = sp.getLong("auto_at", 0);
        if (last != 0 && now >= last && now - last < DAY_MS) return;
        sp.edit().putLong("auto_at", now).commit();
        try {
            JSONObject backup = create(ctx, now);
            String fingerprint = fingerprint(backup);
            if (fingerprint.equals(sp.getString("auto_fingerprint", "")) && !list().isEmpty()) return;
            write(autoName(now), backup);
            sp.edit().putString("auto_fingerprint", fingerprint).commit();
            for (String name : autoToDelete(names(list()))) {
                if (!new File(folder(), name).delete()) Log.w("InfoTablet", "backup: could not delete " + name);
            }
        } catch (IOException | JSONException e) {
            HealthLog.event(ctx, "ERROR", "auto backup " + e);
        }
    }

    /** The content without its creation time, so that an unchanged tablet writes no new copy. */
    static String fingerprint(JSONObject backup) throws JSONException {
        JSONObject copy = new JSONObject(backup.toString());
        copy.remove("created");
        return Integer.toHexString(copy.toString().hashCode()) + ":" + copy.toString().length();
    }

    /** Automatic copies beyond the newest {@link #AUTO_KEEP}, from names sorted newest first. */
    static List<String> autoToDelete(List<String> newestFirst) {
        List<String> out = new ArrayList<String>();
        int seen = 0;
        for (String name : newestFirst) {
            if (name.endsWith(AUTO_SUFFIX) && ++seen > AUTO_KEEP) out.add(name);
        }
        return out;
    }

    private static List<String> names(List<File> files) {
        List<String> out = new ArrayList<String>();
        for (File f : files) out.add(f.getName());
        return out;
    }

    /** "infotablet-2026-11-18-0300-auto.json" becomes "18.11.2026. u 03:00 · automatska". */
    public static String label(String name) {
        String core = name.replace("infotablet-", "").replace(AUTO_SUFFIX, "").replace(".json", "");
        String[] p = core.split("-");
        if (p.length != 4 || p[3].length() != 4) return core;
        String text = p[2] + "." + p[1] + "." + p[0] + ". u " + p[3].substring(0, 2) + ":" + p[3].substring(2);
        return name.endsWith(AUTO_SUFFIX) ? text + " · automatska" : text;
    }

    /** Backup files in the folder, newest first. */
    public static List<File> list() {
        List<File> out = new ArrayList<File>();
        File[] files = folder().listFiles();
        if (files != null) {
            for (File f : files) if (f.isFile() && f.getName().startsWith("infotablet-") && f.getName().endsWith(".json")) out.add(f);
        }
        Collections.sort(out, (a, b) -> b.getName().compareTo(a.getName()));
        return out;
    }

    public static String read(File f) throws IOException {
        if (f.length() > 1024 * 1024) throw new IOException("Datoteka je prevelika.");
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } finally {
            in.close();
        }
    }
}
