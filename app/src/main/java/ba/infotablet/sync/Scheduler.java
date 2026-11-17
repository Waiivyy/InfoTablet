package ba.infotablet.sync;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import ba.infotablet.data.Cache;
import ba.infotablet.data.SourceState;

/**
 * Runs due sources one at a time on a background thread, every 30 seconds, and tells the UI
 * when data changed. The screen is kept on, so an in-process loop is reliable; alarms are only
 * needed for reminders.
 */
public final class Scheduler {

    public interface Listener {
        void onDataChanged();
    }

    /** Counts for the status line. */
    public static final class Summary {
        public final int ok, total;
        public final long lastOkAt;

        Summary(int ok, int total, long lastOkAt) {
            this.ok = ok;
            this.total = total;
            this.lastOkAt = lastOkAt;
        }
    }

    private static final String TAG = "InfoTablet";
    private static final long TICK_MS = 30_000L;

    private static Scheduler instance;

    private final Context ctx;
    private final List<BaseSource> sources;
    private final Handler bg;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    private boolean started;

    public static synchronized Scheduler get(Context c) {
        if (instance == null) instance = new Scheduler(c.getApplicationContext());
        return instance;
    }

    private Scheduler(Context ctx) {
        this.ctx = ctx;
        this.sources = Sources.all();
        HandlerThread t = new HandlerThread("sync");
        t.start();
        bg = new Handler(t.getLooper());
    }

    public synchronized void start() {
        if (started) return;
        started = true;
        bg.post(tick);
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public List<BaseSource> sources() {
        return sources;
    }

    /** Refresh one source now, regardless of its schedule. */
    public void refreshNow(final String id) {
        bg.post(() -> {
            for (BaseSource s : sources) if (s.id().equals(id)) run(s);
        });
    }

    public Summary summary() {
        int ok = 0;
        long last = 0;
        for (BaseSource s : sources) {
            SourceState st = Cache.get(ctx, s.id());
            if (st.hasData() && !st.failing()) ok++;
            last = Math.max(last, st.okAt);
        }
        return new Summary(ok, sources.size(), last);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            try {
                runDue();
            } finally {
                bg.postDelayed(this, TICK_MS);
            }
        }
    };

    private void runDue() {
        if (!online()) return;
        for (BaseSource s : sources) {
            long now = System.currentTimeMillis();
            if (now >= s.dueAt(Cache.get(ctx, s.id()), now)) run(s);
        }
    }

    private void run(BaseSource s) {
        long t0 = System.currentTimeMillis();
        try {
            String payload = s.fetch(ctx);
            Cache.putOk(ctx, s.id(), payload, System.currentTimeMillis());
            Log.i(TAG, "sync ok " + s.id() + " " + (System.currentTimeMillis() - t0) + "ms " + payload.length() + "B");
        } catch (Throwable e) {
            String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
            Cache.putFail(ctx, s.id(), msg, System.currentTimeMillis());
            Log.w(TAG, "sync fail " + s.id() + " " + msg);
        }
        notifyChanged();
    }

    /** Ask every listener (the visible screen) to redraw from the cache. */
    public void notifyChanged() {
        main.post(() -> {
            for (Listener l : listeners) l.onDataChanged();
        });
    }

    private boolean online() {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }
}
