package ba.infotablet.sources;

import android.content.Context;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.Locale;

import ba.infotablet.data.SourceState;
import ba.infotablet.net.Http;
import ba.infotablet.sync.BaseSource;

/**
 * CBBH daily exchange rates. The list for the next day is published on working days after 16:00,
 * so from 16:05 the source asks for tomorrow's list and retries every 30 minutes until 19:00.
 */
public final class RatesSource extends BaseSource {

    public static final String ID = "rates";

    @Override public String id() { return ID; }

    @Override public String name() { return "Kursna lista (CBBiH)"; }

    @Override public long intervalMs(long now) { return 24 * 3600_000L; }

    @Override public long staleAfterMs() { return 4 * 24 * 3600_000L; } // a Friday list is valid until Tuesday

    @Override
    public long dueAt(SourceState st, long now) {
        if (st.attemptAt == 0 || st.failing() || !st.hasData()) return super.dueAt(st, now);
        long listDate;
        try {
            listDate = new JSONObject(st.payload).getLong("date");
        } catch (Exception e) {
            return 0;
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        int minutes = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        boolean haveTomorrow = listDate >= startOfDay(now) + 86_400_000L;
        if (!haveTomorrow && minutes >= 16 * 60 + 5 && minutes < 19 * 60) return st.attemptAt + 30 * 60_000L;
        Calendar next = Calendar.getInstance();
        next.setTimeInMillis(now);
        next.set(Calendar.HOUR_OF_DAY, 16);
        next.set(Calendar.MINUTE, 5);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (haveTomorrow || minutes >= 16 * 60 + 5) next.add(Calendar.DAY_OF_MONTH, 1);
        return next.getTimeInMillis();
    }

    @Override
    public String fetch(Context ctx) throws Exception {
        Calendar t = Calendar.getInstance();
        t.add(Calendar.DAY_OF_MONTH, 1);
        String date = String.format(Locale.ROOT, "%04d-%02d-%02d", t.get(Calendar.YEAR), t.get(Calendar.MONTH) + 1, t.get(Calendar.DAY_OF_MONTH));
        // The server answers with the newest list dated on or before this date.
        return RatesParser.parse(Http.get("https://www.cbbh.ba/CurrencyExchange/GetJson?date=" + date)).toString();
    }

    private static long startOfDay(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
