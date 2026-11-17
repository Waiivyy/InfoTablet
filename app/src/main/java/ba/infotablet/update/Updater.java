package ba.infotablet.update;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import ba.infotablet.BuildConfig;
import ba.infotablet.Settings;
import ba.infotablet.data.Cache;
import ba.infotablet.data.SourceState;
import ba.infotablet.net.Http;
import ba.infotablet.net.Net;
import ba.infotablet.sync.BaseSource;
import ba.infotablet.sync.Scheduler;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * New versions from the GitHub Releases of this app's public repository. Checks once a month, at
 * once when a data source has been failing for two days (a site may have changed and a fixed
 * version may be out), and from the button in Postavke. A newer APK is downloaded, checked
 * (size, SHA-256 from GitHub, package, version and signing key) and then offered for installing.
 */
public final class Updater {

    private static final String TAG = "InfoTablet";
    static final String MIME = "application/vnd.android.package-archive";
    private static final long HOUR = 3_600_000L;
    private static final long DAY = 24 * HOUR;
    static final long MONTH = 30 * DAY;
    static final long FAIL_STREAK = 2 * DAY;
    /** After a check that did not finish (no network, GitHub down), try again an hour later. */
    static final long RETRY = HOUR;

    private static final AtomicBoolean running = new AtomicBoolean();
    private static volatile String progress;
    private static volatile String debugRepo;

    private Updater() {}

    /** "owner/name" of the GitHub repository. */
    public static String repo() {
        String r = debugRepo;
        return r != null ? r : BuildConfig.UPDATE_REPO;
    }

    /** Debug builds only (adb broadcast): check another repository; null restores the real one. */
    public static void setDebugRepo(String repo) {
        debugRepo = repo;
    }

    /** What the running check is doing ("Provjeravam…", "Preuzimam 1.1.0 · 40 %"), or null. */
    public static String progress() {
        return progress;
    }

    /** Called when the app starts: deletes the APK of an update that has been installed. */
    public static void settle(Context c) {
        UpdateState st = UpdateState.of(c);
        String installed = st.settle(c);
        if (installed != null) {
            st.failed(System.currentTimeMillis(), "Instalirana je nova verzija " + installed + ".");
            ba.infotablet.data.HealthLog.event(c, "UPDATE", "installed " + installed);
        }
    }

    // ---- when to check ----

    /** From the scheduler loop (background thread, network up). */
    public static void maybeCheck(Context c, List<BaseSource> sources, long now) {
        UpdateState st = UpdateState.of(c);
        String why = due(now, st.lastOk(), st.lastAttempt(), st.lastFailTrigger(), longestFailure(c, sources, now));
        if (why == null) return;
        if (why.equals("izvor")) st.failTriggered(now);
        start(c, why);
    }

    /**
     * Why a check is due now ("mjesečno" or "izvor"), or null. Times in the future (the clock was
     * moved back) count as never.
     */
    static String due(long now, long lastOk, long lastAttempt, long lastFailTrigger, long longestFailure) {
        if (lastOk > now + HOUR) lastOk = 0;
        if (lastAttempt > now + HOUR) lastAttempt = 0;
        if (lastFailTrigger > now + HOUR) lastFailTrigger = 0;
        if (now - lastAttempt < RETRY) return null;
        if (longestFailure >= FAIL_STREAK && now - lastFailTrigger >= DAY) return "izvor";
        if (now - lastOk >= MONTH) return "mjesečno";
        return null;
    }

    /** How long the longest current failure streak of an enabled source has lasted. */
    private static long longestFailure(Context c, List<BaseSource> sources, long now) {
        Settings s = Settings.of(c);
        long longest = 0;
        for (BaseSource src : sources) {
            if (!s.sourceEnabled(src.id()) || !src.active(c)) continue;
            SourceState st = Cache.get(c, src.id());
            if (st.failing() && st.firstFailAt > 0) longest = Math.max(longest, now - st.firstFailAt);
        }
        return longest;
    }

    /** "Provjeri ažuriranje" in Postavke. False when a check is already running. */
    public static boolean checkNow(Context c) {
        return start(c, "ručno");
    }

    private static boolean start(Context c, final String why) {
        if (!running.compareAndSet(false, true)) return false;
        final Context app = c.getApplicationContext();
        progress = "Provjeravam…";
        changed(app);
        new Thread(() -> {
            try {
                run(app, why);
            } catch (Throwable t) {
                Log.e(TAG, "update check crashed", t);
            } finally {
                progress = null;
                running.set(false);
                changed(app);
            }
        }, "update").start();
        return true;
    }

    // ---- check and download ----

    private static void run(Context c, String why) {
        UpdateState st = UpdateState.of(c);
        st.attempted(System.currentTimeMillis());
        String installed = installedVersion();
        Release r;
        try {
            r = latest();
        } catch (Exception e) {
            st.failed(System.currentTimeMillis(), "Provjera nije uspjela: " + reason(e));
            Log.w(TAG, "update check failed (" + why + "): " + e);
            return;
        }
        if (r == null) {
            st.finished(System.currentTimeMillis(), "Još nema objavljenih verzija.");
            Log.i(TAG, "update: no release yet (" + why + ")");
            return;
        }
        if (Release.compare(r.version, installed) <= 0) {
            st.clearReady(c);
            st.finished(System.currentTimeMillis(), "Imate najnoviju verziju.");
            Log.i(TAG, "update: " + installed + " is current, latest " + r.version + " (" + why + ")");
            return;
        }
        if (r.version.equals(st.readyVersion()) && st.readyFile(c) != null) {
            st.finished(System.currentTimeMillis(), "Nova verzija " + r.version + " čeka instalaciju.");
            return;
        }
        if (r.version.equals(st.rejected()) && !why.equals("ručno")) {
            st.finished(System.currentTimeMillis(), "Verzija " + r.version + " je ranije odbijena.");
            return;
        }
        File apk;
        try {
            apk = download(c, r);
        } catch (Exception e) {
            deleteDownloads(c);
            st.failed(System.currentTimeMillis(), "Preuzimanje verzije " + r.version + " nije uspjelo: " + reason(e));
            Log.w(TAG, "update download failed: " + e);
            return;
        }
        try {
            progress = "Provjeravam preuzetu verziju…";
            changed(c);
            int code = ApkCheck.verify(c, apk);
            st.setReady(r.version, apk.getName(), code, r.notes);
            st.finished(System.currentTimeMillis(), "Preuzeta je nova verzija " + r.version + ".");
            Log.i(TAG, "update: " + r.version + " (" + code + ") downloaded and verified (" + why + ")");
        } catch (IOException e) {
            deleteDownloads(c);
            st.reject(r.version);
            st.finished(System.currentTimeMillis(), "Verzija " + r.version + " nije prihvaćena: " + e.getMessage());
            Log.w(TAG, "update " + r.version + " rejected: " + e.getMessage());
        }
    }

    /** The latest release, or null when the repository has none yet. */
    private static Release latest() throws Exception {
        String url = "https://api.github.com/repos/" + repo() + "/releases/latest";
        Response resp = Net.client().newCall(new Request.Builder().url(url)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()).execute();
        try {
            if (resp.code() == 404) return null;
            if (!resp.isSuccessful()) throw new IOException("GitHub je odgovorio " + resp.code());
            ResponseBody body = resp.body();
            if (body == null) throw new IOException("GitHub nije poslao odgovor");
            return Release.parse(Http.read(body, 512 * 1024), repo());
        } finally {
            resp.close();
        }
    }

    private static File download(Context c, Release r) throws IOException {
        deleteDownloads(c);
        File part = new File(c.getFilesDir(), "update.part");
        // Redirects are followed by hand so that every hop is checked against the allowed hosts.
        OkHttpClient client = Net.client().newBuilder()
                .followRedirects(false).followSslRedirects(false)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        String url = r.apkUrl;
        for (int hop = 0; hop < 6; hop++) {
            if (!allowedUrl(url)) throw new IOException("preusmjerenje na nepoznatu adresu");
            Response resp = client.newCall(new Request.Builder().url(url).build()).execute();
            try {
                if (resp.isRedirect()) {
                    HttpUrl next = resp.request().url().resolve(resp.header("Location", ""));
                    if (next == null) throw new IOException("neispravno preusmjerenje");
                    url = next.toString();
                    continue;
                }
                if (!resp.isSuccessful()) throw new IOException("server je odgovorio " + resp.code());
                ResponseBody body = resp.body();
                if (body == null) throw new IOException("prazan odgovor");
                save(c, body.byteStream(), part, r);
            } finally {
                resp.close();
            }
            File apk = new File(c.getFilesDir(), "InfoTablet-" + r.version + ".apk");
            if (!part.renameTo(apk)) throw new IOException("datoteka se ne može spremiti");
            return apk;
        }
        throw new IOException("previše preusmjeravanja");
    }

    /** Only HTTPS to GitHub and its file servers. */
    static boolean allowedUrl(String url) {
        HttpUrl u = HttpUrl.parse(url);
        if (u == null || !u.isHttps()) return false;
        String h = u.host();
        return h.equals("github.com") || h.endsWith(".githubusercontent.com");
    }

    private static void save(Context c, InputStream in, File out, Release r) throws IOException {
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e.toString());
        }
        OutputStream os = new FileOutputStream(out);
        long total = 0;
        int shown = -1;
        try {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > r.apkSize) throw new IOException("datoteka je veća od najavljene");
                sha.update(buf, 0, n);
                os.write(buf, 0, n);
                int pct = (int) (total * 100 / r.apkSize);
                if (pct / 10 != shown / 10) {
                    shown = pct;
                    progress = "Preuzimam verziju " + r.version + " · " + pct + " %";
                    changed(c);
                }
            }
        } finally {
            os.close();
        }
        if (total != r.apkSize) throw new IOException("preuzimanje je prekinuto");
        if (r.sha256 != null && !r.sha256.equals(ApkCheck.hex(sha.digest()))) throw new IOException("kontrolni zbir se ne slaže");
    }

    static void deleteDownloads(Context c) {
        File[] files = c.getFilesDir().listFiles();
        if (files == null) return;
        for (File f : files) {
            String n = f.getName();
            if ((n.endsWith(".apk") || n.equals("update.part")) && !f.delete()) Log.w(TAG, "could not delete " + n);
        }
    }

    // ---- install ----

    /** Opens the system installer for the waiting APK. */
    @SuppressLint("SetWorldReadable") // see below: a public release, readable only below Android 7
    public static void install(Context c) {
        UpdateState st = UpdateState.of(c);
        File apk = st.readyFile(c);
        if (apk == null) {
            st.clearReady(c);
            changed(c);
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        if (Build.VERSION.SDK_INT >= 24) {
            i.setDataAndType(ApkProvider.uri(c, apk.getName()), MIME);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            // Android 4.x installs only from file:// paths, read by the system installer, so the
            // file (a public release) must be readable by it. The app's own folder stays private.
            if (!apk.setReadable(true, false)) Log.w(TAG, "could not make the update readable");
            i.setDataAndType(Uri.fromFile(apk), MIME);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (ActivityNotFoundException e) {
            st.failed(System.currentTimeMillis(), "Instalacija se ne može pokrenuti na ovom tabletu.");
            changed(c);
        }
    }

    /** Whether the system lets this app install updates ("Nepoznati izvori"). */
    @SuppressWarnings("deprecation") // the setting is read only on the versions that have it
    public static boolean installAllowed(Context c) {
        if (Build.VERSION.SDK_INT >= 26) return c.getPackageManager().canRequestPackageInstalls();
        String key = android.provider.Settings.Secure.INSTALL_NON_MARKET_APPS;
        if (Build.VERSION.SDK_INT < 21) return android.provider.Settings.Global.getInt(c.getContentResolver(), key, 0) == 1;
        return android.provider.Settings.Secure.getInt(c.getContentResolver(), key, 0) == 1;
    }

    // ---- debug ----

    /**
     * Debug builds only (adb broadcast): treat a local APK as the downloaded new version, to test
     * the check and the installer without publishing a release.
     */
    public static void debugOffer(final Context c, final File source, final String version) {
        new Thread(() -> {
            try {
                deleteDownloads(c);
                File apk = new File(c.getFilesDir(), "InfoTablet-" + version + ".apk");
                InputStream in = new java.io.FileInputStream(source);
                OutputStream out = new FileOutputStream(apk);
                try {
                    byte[] buf = new byte[16 * 1024];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                } finally {
                    in.close();
                    out.close();
                }
                int code = ApkCheck.verify(c, apk);
                UpdateState.of(c).setReady(version, apk.getName(), code, "Probna verzija s računara.");
                Log.i(TAG, "debug update " + version + " (" + code + ") offered");
            } catch (IOException e) {
                deleteDownloads(c);
                Log.w(TAG, "debug update refused: " + e.getMessage());
            }
            changed(c);
        }, "update").start();
    }

    // ---- helpers ----

    static String installedVersion() {
        String v = Release.versionOf(BuildConfig.VERSION_NAME);
        return v == null ? "0" : v;
    }

    private static String reason(Exception e) {
        if (e instanceof java.net.UnknownHostException) return "nema veze s internetom.";
        if (e instanceof java.net.SocketTimeoutException) return "GitHub se ne javlja.";
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    private static void changed(Context c) {
        Scheduler.get(c).notifyChanged();
    }
}
