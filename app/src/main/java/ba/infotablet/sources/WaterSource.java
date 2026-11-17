package ba.infotablet.sources;

import android.content.Context;

import java.util.Calendar;

import ba.infotablet.net.Http;
import ba.infotablet.sync.BaseSource;

/** ViK Sarajevo service notices. Every 45 minutes on working days 07 to 17 h, otherwise every 2.5 hours. */
public final class WaterSource extends BaseSource {

    public static final String ID = "water";

    @Override public String id() { return ID; }

    @Override public String name() { return "Voda (ViK Sarajevo)"; }

    @Override
    public long intervalMs(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        int dow = c.get(Calendar.DAY_OF_WEEK), h = c.get(Calendar.HOUR_OF_DAY);
        boolean workHours = dow != Calendar.SATURDAY && dow != Calendar.SUNDAY && h >= 7 && h < 17;
        return workHours ? 45 * 60_000L : 150 * 60_000L;
    }

    @Override public long staleAfterMs() { return 8 * 3600_000L; }

    @Override
    public String fetch(Context ctx) throws Exception {
        return WaterParser.parse(Http.get("https://www.viksa.ba/objave/servisne_informacije", 512 * 1024)).toString();
    }
}
