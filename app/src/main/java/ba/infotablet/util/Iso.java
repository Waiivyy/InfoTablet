package ba.infotablet.util;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** ISO 8601 timestamps with offsets ("2026-11-17T15:00:00+01:00"); SimpleDateFormat on API 17 has no 'X'. */
public final class Iso {

    private static final Pattern P = Pattern.compile(
            "(\\d{4})-(\\d{2})-(\\d{2})[T ](\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.\\d+)?)?\\s*(Z|[+-]\\d{2}:?\\d{2})?");

    private Iso() {}

    /** Epoch millis; a missing offset is read in {@code fallback}. Returns 0 when unparseable. */
    public static long parse(String s, TimeZone fallback) {
        if (s == null) return 0;
        Matcher m = P.matcher(s.trim());
        if (!m.find()) return 0;
        String off = m.group(7);
        Calendar c = Calendar.getInstance(off == null ? fallback : TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) - 1, Integer.parseInt(m.group(3)),
                Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)), m.group(6) == null ? 0 : Integer.parseInt(m.group(6)));
        long t = c.getTimeInMillis();
        if (off != null && !off.equals("Z")) {
            String d = off.replace(":", "");
            int sign = d.charAt(0) == '-' ? -1 : 1;
            int mins = Integer.parseInt(d.substring(1, 3)) * 60 + Integer.parseInt(d.substring(3, 5));
            t -= sign * mins * 60_000L;
        }
        return t;
    }

    public static long parse(String s) {
        return parse(s, TimeZone.getTimeZone("UTC"));
    }
}
