package ba.infotablet.sources;

import android.content.Context;

import ba.infotablet.Settings;
import ba.infotablet.net.Http;
import ba.infotablet.sync.BaseSource;

/** MeteoAlarm warnings for Bosnia and Herzegovina (issued by FHMZ and RHMZ RS). Every 30 minutes. */
public final class WarningsSource extends BaseSource {

    public static final String ID = "warnings";
    private static final String URL = "https://feeds.meteoalarm.org/api/v1/warnings/feeds-bosnia-herzegovina";

    @Override public String id() { return ID; }

    @Override public String name() { return "Upozorenja (MeteoAlarm)"; }

    @Override public long intervalMs(long now) { return 30 * 60_000L; }

    @Override public long staleAfterMs() { return 2 * 3600_000L; }

    @Override
    public String fetch(Context ctx) throws Exception {
        String raw = Http.get(URL, 4L * 1024 * 1024);
        return WarningsParser.parse(raw, System.currentTimeMillis(), Settings.of(ctx).warningRegion()).toString();
    }
}
