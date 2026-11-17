package ba.infotablet.ui;

import org.json.JSONObject;

import ba.infotablet.R;
import ba.infotablet.util.Fmt;

/** Shared wording and colours for MeteoAlarm warnings. */
public final class WarningText {

    private WarningText() {}

    public static String levelName(int level) {
        return level >= 4 ? "CRVENO" : level == 3 ? "NARANDŽASTO" : "ŽUTO";
    }

    public static int levelColor(int level) {
        return level >= 4 ? Palette.LEVEL_RED : level == 3 ? Palette.LEVEL_ORANGE : Palette.LEVEL_YELLOW;
    }

    public static int levelTextColor(int level) {
        return level >= 3 ? 0xFFFFFFFF : Palette.LEVEL_TEXT_DARK;
    }

    public static int icon(int type) {
        switch (type) {
            case 1: return R.drawable.ic_wind;
            case 2: case 6: return R.drawable.ic_snow;
            case 3: return R.drawable.ic_thunder;
            case 4: return R.drawable.ic_fog;
            case 10: case 12: case 13: return R.drawable.ic_rain;
            default: return R.drawable.ic_warning;
        }
    }

    /** "danas od 15:00 do sutra 15:00", "traje do sutra 15:00", "sutra od 06:00 do 18:00". */
    public static String period(long now, long onset, long expires) {
        String endTime = Fmt.hhmm(expires);
        String end = Fmt.dayOffset(now, expires) == 0 ? endTime : Fmt.relDay(now, expires) + " " + endTime;
        if (onset <= now) return "traje do " + end;
        String start = Fmt.relDay(now, onset) + " od " + Fmt.hhmm(onset);
        if (Fmt.dayOffset(onset, expires) == 0) return start + " do " + endTime;
        return start + " do " + Fmt.relDay(now, expires) + " " + endTime;
    }

    public static String sender(String s) {
        if (s == null) return "";
        if (s.startsWith("Federal")) return "FHMZ BiH";
        if (s.startsWith("Republi")) return "RHMZ RS";
        return s;
    }

    /** "Upozorenje: vjetar · Sarajevo · danas od 15:00 do sutra 15:00" (the first part is the bold title). */
    public static String[] line(JSONObject w, long now) {
        return new String[] {
                "Upozorenje: " + w.optString("typeName"),
                " · " + w.optString("regionName") + " · " + period(now, w.optLong("onset"), w.optLong("expires"))};
    }
}
