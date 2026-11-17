package ba.infotablet.ui;

import ba.infotablet.data.SourceState;
import ba.infotablet.util.Fmt;

/**
 * How a tile should show its source: fresh, stale (greyed, last good data) or empty.
 * Stale means the latest attempt failed or the data is older than the source allows.
 */
public final class Freshness {

    public static final float STALE_ALPHA = 0.45f;

    public final boolean hasData;
    public final boolean stale;
    public final String note;

    private Freshness(boolean hasData, boolean stale, String note) {
        this.hasData = hasData;
        this.stale = stale;
        this.note = note;
    }

    public static Freshness of(SourceState st, long staleAfterMs, long now) {
        if (!st.hasData()) {
            return new Freshness(false, true, st.attemptAt == 0 ? "Učitavanje…" : "Nema podataka");
        }
        boolean old = now - st.okAt > staleAfterMs;
        if (st.failing() || old) {
            return new Freshness(true, true, "Nema podataka · zadnje " + Fmt.when(now, st.okAt));
        }
        return new Freshness(true, false, null);
    }
}
