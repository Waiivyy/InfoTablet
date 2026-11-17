package ba.infotablet.data;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import ba.infotablet.BuildConfig;

/**
 * A small health diary on the tablet itself, for checking a wall display that nobody watches:
 * every 15 minutes one line with memory, battery and how each source fared, plus app starts and
 * crashes. One tab-separated file per day in InfoTablet/log, kept for 14 days. Nothing leaves
 * the tablet; read it with adb pull.
 */
public final class HealthLog {

    private static final String TAG = "InfoTablet";
    private static final long EVERY_MS = 15 * 60_000L;
    private static final long KEEP_MS = 14 * 86_400_000L;

    /** Per source: {ok, failed} since the last line. */
    private static final Map<String, int[]> counts = new TreeMap<String, int[]>();
    private static long lastLineAt;
    private static final long startedAt = SystemClock.elapsedRealtime();

    private HealthLog() {}

    /** Called once from Application.onCreate: logs the start and catches crashes. */
    public static void start(final Context c) {
        final Context app = c.getApplicationContext();
        event(app, "START", BuildConfig.VERSION_NAME + " android " + Build.VERSION.SDK_INT);
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            try {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                String trace = sw.toString();
                if (trace.length() > 3000) trace = trace.substring(0, 3000);
                event(app, "CRASH", thread.getName() + " " + trace.replace('\t', ' ').replace("\n", " | "));
            } catch (Throwable ignored) {
                // never let the diary hide the real crash
            }
            if (previous != null) previous.uncaughtException(thread, e);
        });
    }

    /** One source attempt (from the scheduler). */
    public static synchronized void count(String source, boolean ok) {
        int[] n = counts.get(source);
        if (n == null) counts.put(source, n = new int[2]);
        n[ok ? 0 : 1]++;
    }

    /** From the scheduler loop: writes a line when 15 minutes have passed. */
    public static void maybeWrite(Context c, long now) {
        if (lastLineAt != 0 && now - lastLineAt < EVERY_MS && now >= lastLineAt) return;
        lastLineAt = now;
        String sources;
        synchronized (HealthLog.class) {
            StringBuilder b = new StringBuilder();
            for (Map.Entry<String, int[]> e : counts.entrySet()) {
                if (b.length() > 0) b.append(' ');
                b.append(e.getKey()).append('=').append(e.getValue()[0]).append('/').append(e.getValue()[1]);
            }
            counts.clear();
            sources = b.length() == 0 ? "-" : b.toString();
        }
        write(c, now, "STATUS\t" + status(c) + "\t" + sources);
    }

    public static void event(Context c, String kind, String text) {
        write(c, System.currentTimeMillis(), kind + "\t" + text.replace('\t', ' ').replace('\n', ' '));
    }

    /** Memory, battery and network, as key=value pairs. */
    static String status(Context c) {
        StringBuilder b = new StringBuilder();
        b.append("up=").append((SystemClock.elapsedRealtime() - startedAt) / 60_000L).append("m");
        Debug.MemoryInfo mi = new Debug.MemoryInfo();
        Debug.getMemoryInfo(mi);
        Runtime rt = Runtime.getRuntime();
        b.append(" pss=").append(mi.getTotalPss()).append("k");
        b.append(" heap=").append((rt.totalMemory() - rt.freeMemory()) / 1024).append("k/").append(rt.maxMemory() / 1024).append("k");
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo sys = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(sys);
            b.append(" sysfree=").append(sys.availMem / (1024 * 1024)).append("M").append(sys.lowMemory ? " LOWMEM" : "");
        }
        Intent bat = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bat != null) {
            b.append(" temp=").append(bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f).append("C");
            b.append(" level=").append(bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)).append("%");
            b.append(" volt=").append(bat.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)).append("mV");
            b.append(" health=").append(health(bat.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)));
            int plugged = bat.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            b.append(" power=").append(plugged == BatteryManager.BATTERY_PLUGGED_AC ? "ac" : plugged == BatteryManager.BATTERY_PLUGGED_USB ? "usb" : plugged == 0 ? "battery" : "other");
        }
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
        b.append(" net=").append(ni != null && ni.isConnected() ? "up" : "down");
        return b.toString();
    }

    private static String health(int h) {
        switch (h) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "OVERHEAT";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "DEAD";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "OVERVOLTAGE";
            case BatteryManager.BATTERY_HEALTH_COLD: return "cold";
            default: return "unknown";
        }
    }

    public static File folder(Context c) {
        // Shared storage so that adb can read it from a release build (Android 10+ uses the app's own folder).
        if (Build.VERSION.SDK_INT >= 29) return new File(c.getExternalFilesDir(null), "log");
        return new File(Environment.getExternalStorageDirectory(), Backup.FOLDER + "/log");
    }

    private static synchronized void write(Context c, long now, String line) {
        File dir = folder(c);
        if (!dir.isDirectory() && !dir.mkdirs()) return;
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(now));
        File f = new File(dir, "health-" + day + ".tsv");
        boolean fresh = !f.exists();
        try {
            OutputStream out = new FileOutputStream(f, true);
            try {
                if (fresh) {
                    out.write("time\tkind\tdetail\tsources (ok/failed since the line before)\n".getBytes("UTF-8"));
                    prune(dir, now);
                }
                String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date(now));
                out.write((time + "\t" + line + "\n").getBytes("UTF-8"));
            } finally {
                out.close();
            }
        } catch (IOException e) {
            Log.w(TAG, "health log: " + e.getMessage());
        }
    }

    private static void prune(File dir, long now) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName().startsWith("health-") && now - f.lastModified() > KEEP_MS && !f.delete()) {
                Log.w(TAG, "health log: could not delete " + f.getName());
            }
        }
    }
}
