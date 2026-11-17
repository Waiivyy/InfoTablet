package ba.infotablet.fuel;

import org.json.JSONException;
import org.json.JSONObject;

import ba.infotablet.data.SourceState;
import ba.infotablet.sources.FuelSource;
import ba.infotablet.ui.Freshness;
import ba.infotablet.util.Fmt;

/** The cached fuel payload plus how it should be shown (fresh, stale, too old). */
final class FuelData {

    final JSONObject payload; // null when nothing can be shown
    final boolean stale;
    final String note;        // reason shown instead of or next to the data

    private FuelData(JSONObject payload, boolean stale, String note) {
        this.payload = payload;
        this.stale = stale;
        this.note = note;
    }

    static FuelData of(SourceState st, long staleAfterMs, long now) {
        Freshness f = Freshness.of(st, staleAfterMs, now);
        if (!f.hasData) return new FuelData(null, true, f.note);
        try {
            JSONObject p = new JSONObject(st.payload);
            long date = p.getLong("dataDate");
            if (now - date > FuelSource.MAX_DATA_AGE_MS) {
                return new FuelData(null, true, "Nema svježih cijena · zadnje " + Fmt.fullDate(date));
            }
            return new FuelData(p, f.stale, f.note);
        } catch (JSONException e) {
            return new FuelData(null, true, "Nema podataka");
        }
    }

    JSONObject fuel(String key) {
        return payload == null ? null : payload.optJSONObject("fuels") == null ? null : payload.optJSONObject("fuels").optJSONObject(key);
    }
}
