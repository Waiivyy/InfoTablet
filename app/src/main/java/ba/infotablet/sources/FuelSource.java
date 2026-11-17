package ba.infotablet.sources;

import android.content.Context;

import java.util.Calendar;

import ba.infotablet.data.SourceState;
import ba.infotablet.net.Http;
import ba.infotablet.sync.BaseSource;

/**
 * Fuel prices for Sarajevo from uhvatipopust.ba (data from the Federal Ministry of Trade's eOPC
 * register). Three times a day: 07:30, 12:30 and 18:30.
 */
public final class FuelSource extends BaseSource {

    public static final String ID = "fuel";
    private static final String URL = "https://uhvatipopust.ba/cijene-goriva/sarajevo";
    private static final int[][] SLOTS = {{7, 30}, {12, 30}, {18, 30}};
    /** Prices whose data date is older than this are not shown. */
    public static final long MAX_DATA_AGE_MS = 7L * 24 * 3600_000L;

    @Override public String id() { return ID; }

    @Override public String name() { return "Gorivo (uhvatipopust.ba)"; }

    @Override public long intervalMs(long now) { return 8 * 3600_000L; }

    @Override public long staleAfterMs() { return 30 * 3600_000L; }

    @Override
    public long dueAt(SourceState st, long now) {
        if (st.attemptAt == 0 || st.failing()) return super.dueAt(st, now);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(st.okAt);
        for (int day = 0; day < 2; day++) {
            for (int[] slot : SLOTS) {
                Calendar s = (Calendar) c.clone();
                s.add(Calendar.DAY_OF_MONTH, day);
                s.set(Calendar.HOUR_OF_DAY, slot[0]);
                s.set(Calendar.MINUTE, slot[1]);
                s.set(Calendar.SECOND, 0);
                s.set(Calendar.MILLISECOND, 0);
                if (s.getTimeInMillis() > st.okAt) return s.getTimeInMillis();
            }
        }
        return st.okAt + intervalMs(now);
    }

    @Override
    public String fetch(Context ctx) throws Exception {
        return FuelParser.parse(Http.get(URL, 1024 * 1024)).toString();
    }
}
