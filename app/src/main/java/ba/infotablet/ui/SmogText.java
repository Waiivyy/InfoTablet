package ba.infotablet.ui;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ba.infotablet.data.SourceState;

/** Wording for smog episodes under the Kanton Sarajevo plan (Sl. novine KS 51/2025). */
public final class SmogText {

    /** An episode is shown for 7 days after the last notice that kept it active. */
    public static final long SHOW_MS = 7L * 24 * 3600_000L;

    private SmogText() {}

    /** The active episode state, or null. */
    public static JSONObject active(SourceState st, long now) {
        if (!st.hasData()) return null;
        try {
            JSONObject s = new JSONObject(st.payload);
            if (!s.has("level")) return null;
            if (now - s.optLong("declaredAt") > SHOW_MS) return null;
            return s;
        } catch (JSONException e) {
            return null;
        }
    }

    public static String levelName(String level) {
        return "UZBUNA".equals(level) ? "UZBUNA" : "UPOZORENJE".equals(level) ? "UPOZORENJE" : "PRIPRAVNOST";
    }

    public static int levelColor(String level) {
        return "UZBUNA".equals(level) ? Palette.LEVEL_RED : Palette.LEVEL_ORANGE;
    }

    /** "zone A, B i C" for the zones at the given level ("?" when the notice named none). */
    public static String zones(JSONObject state, String level) {
        JSONObject z = state.optJSONObject("zones");
        List<String> list = new ArrayList<String>();
        JSONArray names = z == null ? null : z.names();
        for (int i = 0; names != null && i < names.length(); i++) {
            String k = names.optString(i);
            if (level.equals(z.optString(k)) && !k.equals("?")) list.add(k);
        }
        Collections.sort(list);
        if (list.isEmpty()) return "Kanton Sarajevo";
        if (list.size() == 1) return "zona " + list.get(0);
        StringBuilder b = new StringBuilder("zone ");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) b.append(i == list.size() - 1 ? " i " : ", ");
            b.append(list.get(i));
        }
        return b.toString();
    }

    /** Key measures for the banner (from the plan's measure tables). */
    public static String measures(String level) {
        if ("UZBUNA".equals(level)) {
            return "Zona A: zabrana vožnje za EURO 3 i starija vozila (taxi izuzet). Škola od 8:30, vrtić do 9:00, fakulteti online. "
                    + "Javni prevoz besplatan ili subvencioniran. Zabranjena okupljanja i sport na otvorenom.";
        }
        if ("UPOZORENJE".equals(level)) {
            return "Zona A: zabrana vožnje za automobile i teretna vozila EURO 2 i starija. Zabranjeni građevinski radovi na otvorenom. "
                    + "Djeca u školama i vrtićima ne borave napolju. Rizične grupe: ograničite boravak napolju.";
        }
        return "Pratite obavještenja nadležnih.";
    }

    /** One line for the Početna banner. */
    public static String shortMeasure(String level) {
        if ("UZBUNA".equals(level)) return "zona A: zabrana vožnje za EURO 3 i starija vozila";
        if ("UPOZORENJE".equals(level)) return "zona A: zabrana vožnje za EURO 2 i starija vozila";
        return "pratite obavještenja";
    }
}
