package ba.infotablet.calendar;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Federation of BiH holidays from assets/holidays.csv (shipped with the app, corrected by app
 * updates). State days are non-working for everyone; religious days are leave days for believers.
 */
public final class Holidays {

    public static final class Day {
        public final long date;        // local midnight
        public final String name;
        public final boolean state;    // true: neradni dan for everyone
        public final boolean provisional;

        Day(long date, String name, boolean state, boolean provisional) {
            this.date = date;
            this.name = name;
            this.state = state;
            this.provisional = provisional;
        }
    }

    private static List<Day> cache;

    private Holidays() {}

    public static synchronized List<Day> all(Context ctx) {
        if (cache == null) {
            try {
                cache = parse(read(ctx.getAssets().open("holidays.csv")));
            } catch (IOException e) {
                cache = Collections.emptyList();
            }
        }
        return cache;
    }

    /** Parses "date,name,kind,status" rows; '#' lines and the header are skipped; names may be quoted. */
    public static List<Day> parse(String csv) {
        List<Day> out = new ArrayList<Day>();
        for (String line : csv.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("date,")) continue;
            List<String> f = fields(line);
            if (f.size() < 4 || f.get(0).length() != 10) continue;
            Calendar c = Calendar.getInstance();
            c.clear();
            c.set(Integer.parseInt(f.get(0).substring(0, 4)), Integer.parseInt(f.get(0).substring(5, 7)) - 1, Integer.parseInt(f.get(0).substring(8, 10)));
            out.add(new Day(c.getTimeInMillis(), f.get(1), "state".equals(f.get(2)), "provisional".equals(f.get(3))));
        }
        Collections.sort(out, (a, b) -> a.date < b.date ? -1 : (a.date > b.date ? 1 : (a.state == b.state ? 0 : (a.state ? -1 : 1))));
        return out;
    }

    /** Holidays from today on, state days first on the same date. */
    public static List<Day> upcoming(List<Day> days, long now, int max) {
        long today = startOfDay(now);
        List<Day> out = new ArrayList<Day>();
        for (Day d : days) {
            if (d.date < today) continue;
            out.add(d);
            if (out.size() >= max) break;
        }
        return out;
    }

    /** The next holiday within {@code withinDays}, or null. */
    public static Day next(List<Day> days, long now, int withinDays) {
        List<Day> u = upcoming(days, now, 1);
        if (u.isEmpty()) return null;
        return daysUntil(now, u.get(0).date) <= withinDays ? u.get(0) : null;
    }

    public static int daysUntil(long now, long date) {
        return (int) Math.round((date - startOfDay(now)) / 86_400_000.0);
    }

    /** "danas", "sutra", "za 8 dana", "za 21 dan". */
    public static String countdown(int days) {
        if (days <= 0) return "danas";
        if (days == 1) return "sutra";
        boolean one = days % 10 == 1 && days % 100 != 11;
        return "za " + days + (one ? " dan" : " dana");
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

    private static List<String> fields(String line) {
        List<String> out = new ArrayList<String>();
        StringBuilder b = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    b.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                out.add(b.toString());
                b.setLength(0);
            } else {
                b.append(ch);
            }
        }
        out.add(b.toString());
        return out;
    }

    private static String read(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        } finally {
            in.close();
        }
    }
}
