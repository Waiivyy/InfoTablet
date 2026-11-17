package ba.infotablet.sync;

import ba.infotablet.data.SourceState;

/** Default timing: refresh {@link #intervalMs} after the last success, back off after failures. */
public abstract class BaseSource implements Source {

    private static final long MINUTE = 60_000L;

    /** "Osvježavanje" in Postavke: 1 normal, 2 or 4 times less often (set by the scheduler). */
    static volatile int slowdown = 1;

    /** False when the source has nothing to do, for example no second city chosen. */
    public boolean active(android.content.Context ctx) {
        return true;
    }

    /** The normal interval stretched by the refresh setting. */
    protected long interval(long now) {
        return intervalMs(now) * slowdown;
    }

    /** When this source should run next. */
    public long dueAt(SourceState st, long now) {
        if (st.attemptAt == 0 || st.attemptAt > now + 60 * MINUTE) return 0; // never run, or clock moved back
        if (st.failing()) return st.attemptAt + backoffMs(st.failCount, interval(now));
        return st.okAt + interval(now);
    }

    /** 5, 10, 20, 40, 80 minutes, never longer than the normal interval. */
    static long backoffMs(int fails, long interval) {
        long b = 5 * MINUTE << Math.min(Math.max(fails - 1, 0), 4);
        return Math.min(b, interval);
    }
}
