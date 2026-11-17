package ba.infotablet.sync;

import android.content.Context;

/** One external data source. Implementations fetch, parse and return a small normalised JSON string. */
public interface Source {

    /** Stable id, used as the cache key. */
    String id();

    /** Display name in Bosnian, for the status list in Postavke. */
    String name();

    /** Time between successful refreshes; may depend on the time of day. */
    long intervalMs(long now);

    /** Data older than this is shown greyed out as stale. */
    long staleAfterMs();

    /** Runs on the scheduler thread. Throws on any failure; the previous data is kept. */
    String fetch(Context ctx) throws Exception;
}
