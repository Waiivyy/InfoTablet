package ba.infotablet.sources;

import android.content.Context;

import java.util.Calendar;

import ba.infotablet.data.SourceState;
import ba.infotablet.net.Http;
import ba.infotablet.sync.BaseSource;

/**
 * Official hourly measurements from FHMZ stations in Kanton Sarajevo. Values appear about 25 minutes
 * after the full hour, so this runs at about HH:32.
 */
public final class AirStationsSource extends BaseSource {

    public static final String ID = "air_stations";
    private static final String BASE = "https://zrak.fhmzbih.gov.ba/";

    @Override public String id() { return ID; }

    @Override public String name() { return "Zrak, mjerne stanice (FHMZ)"; }

    @Override public long intervalMs(long now) { return 60 * 60_000L; }

    @Override public long staleAfterMs() { return 3 * 3600_000L; }

    @Override
    public long dueAt(SourceState st, long now) {
        if (st.attemptAt == 0 || st.failing()) return super.dueAt(st, now);
        // Next HH:32 after the last success.
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(st.okAt);
        c.set(Calendar.MINUTE, 32);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= st.okAt) c.add(Calendar.HOUR_OF_DAY, 1);
        return c.getTimeInMillis();
    }

    @Override
    public String fetch(Context ctx) throws Exception {
        String eaqi = Http.get(BASE + "rest/index/data/eaqi/0");
        pause();
        String pm25 = Http.get(BASE + "rest/index/data/pm25/0");
        pause();
        String pm10 = Http.get(BASE + "rest/index/data/pm10/0");
        pause();
        String home = null;
        try {
            home = Http.get(BASE, 512 * 1024);
        } catch (Exception ignored) {
            // The measurement hour is a nice-to-have; values are still valid without it.
        }
        return AirParsers.stations(eaqi, pm25, pm10, home).toString();
    }

    private static void pause() throws InterruptedException {
        Thread.sleep(1200); // be polite to a small government server
    }
}
