package ba.infotablet.sync;

import ba.infotablet.data.SourceState;

/** Default timing: refresh {@link #intervalMs} after the last success, back off after failures. */
public abstract class BaseSource implements Source {

    private static final long MINUTE = 60_000L;

    /** When this source should run next. */
    public long dueAt(SourceState st, long now) {
        if (st.attemptAt == 0 || st.attemptAt > now + 60 * MINUTE) return 0; // never run, or clock moved back
        if (st.failing()) return st.attemptAt + backoffMs(st.failCount, intervalMs(now));
        return st.okAt + intervalMs(now);
    }

    /** 5, 10, 20, 40, 80 minutes, never longer than the normal interval. */
    static long backoffMs(int fails, long interval) {
        long b = 5 * MINUTE << Math.min(Math.max(fails - 1, 0), 4);
        return Math.min(b, interval);
    }
}
