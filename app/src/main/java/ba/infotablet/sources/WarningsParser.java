package ba.infotablet.sources;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ba.infotablet.util.Iso;

/**
 * MeteoAlarm JSON feed for BA to the app's payload {warnings:[{region,regionName,level,type,typeName,
 * headline,description,instruction,onset,expires,sender}]}. Rules: Bosnian texts only, expired warnings
 * dropped, every level for the home region, only orange (3) and red (4) elsewhere, duplicates removed.
 */
public final class WarningsParser {

    private WarningsParser() {}

    public static JSONObject parse(String raw, long now, String homeRegion) throws JSONException {
        JSONArray ws = new JSONObject(raw).getJSONArray("warnings");
        List<JSONObject> out = new ArrayList<JSONObject>();
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < ws.length(); i++) {
            JSONArray infos = ws.getJSONObject(i).getJSONObject("alert").optJSONArray("info");
            if (infos == null) continue;
            for (int k = 0; k < infos.length(); k++) {
                JSONObject info = infos.getJSONObject(k);
                if (!info.optString("language").startsWith("bs")) continue;
                long expires = Iso.parse(info.optString("expires", null));
                if (expires <= now) continue;
                long onset = Iso.parse(info.optString("onset", info.optString("effective", null)));
                int level = levelOf(info);
                if (level < 2) continue;
                int type = typeOf(info);
                JSONArray areas = info.optJSONArray("area");
                if (areas == null) continue;
                for (int a = 0; a < areas.length(); a++) {
                    JSONObject area = areas.getJSONObject(a);
                    String region = emmaId(area);
                    boolean home = homeRegion.equals(region);
                    if (!home && level < 3) continue;
                    String key = region + "|" + type + "|" + level + "|" + onset + "|" + expires;
                    if (!seen.add(key)) continue;
                    JSONObject o = new JSONObject();
                    o.put("region", region);
                    o.put("regionName", area.optString("areaDesc"));
                    o.put("home", home);
                    o.put("level", level);
                    o.put("type", type);
                    o.put("typeName", typeName(type));
                    o.put("headline", info.optString("headline"));
                    o.put("description", info.optString("description"));
                    o.put("instruction", info.optString("instruction"));
                    o.put("onset", onset);
                    o.put("expires", expires);
                    o.put("sender", info.optString("senderName"));
                    out.add(o);
                }
            }
        }
        // Home region first, then most severe, then soonest.
        // (Boolean.compare and Long.compare need API 19, so compare by hand.)
        Collections.sort(out, (x, y) -> {
            int h = (y.optBoolean("home") ? 1 : 0) - (x.optBoolean("home") ? 1 : 0);
            if (h != 0) return h;
            int l = y.optInt("level") - x.optInt("level");
            if (l != 0) return l;
            long a = x.optLong("onset"), b = y.optLong("onset");
            return a < b ? -1 : (a > b ? 1 : 0);
        });
        JSONArray arr = new JSONArray();
        for (JSONObject o : merge(out)) arr.put(o);
        return new JSONObject().put("warnings", arr);
    }

    /**
     * MeteoAlarm often splits one event at midnight (15:00 to 23:59, then 00:00 to 15:00). Join
     * back-to-back warnings of the same region, type and level into one period.
     */
    private static List<JSONObject> merge(List<JSONObject> sorted) throws JSONException {
        List<JSONObject> out = new ArrayList<JSONObject>();
        for (JSONObject w : sorted) {
            JSONObject joined = null;
            for (JSONObject o : out) {
                boolean same = o.getString("region").equals(w.getString("region"))
                        && o.getInt("type") == w.getInt("type") && o.getInt("level") == w.getInt("level");
                boolean adjacent = w.getLong("onset") <= o.getLong("expires") + 2 * 60_000L
                        && w.getLong("expires") >= o.getLong("onset");
                if (same && adjacent) {
                    joined = o;
                    break;
                }
            }
            if (joined == null) {
                out.add(w);
            } else {
                joined.put("onset", Math.min(joined.getLong("onset"), w.getLong("onset")));
                joined.put("expires", Math.max(joined.getLong("expires"), w.getLong("expires")));
            }
        }
        return out;
    }

    /** "2; yellow; Moderate" gives 2 (1 green, 2 yellow, 3 orange, 4 red). */
    static int levelOf(JSONObject info) throws JSONException {
        String v = param(info, "awareness_level");
        if (v != null) {
            try {
                return Integer.parseInt(v.split(";")[0].trim());
            } catch (NumberFormatException ignored) {
                // fall through to severity
            }
        }
        String sev = info.optString("severity");
        if ("Extreme".equals(sev)) return 4;
        if ("Severe".equals(sev)) return 3;
        if ("Moderate".equals(sev)) return 2;
        return 1;
    }

    /** "1; Wind" gives 1. */
    static int typeOf(JSONObject info) throws JSONException {
        String v = param(info, "awareness_type");
        try {
            return v == null ? 0 : Integer.parseInt(v.split(";")[0].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String param(JSONObject info, String name) throws JSONException {
        JSONArray ps = info.optJSONArray("parameter");
        if (ps == null) return null;
        for (int i = 0; i < ps.length(); i++) {
            JSONObject p = ps.getJSONObject(i);
            if (name.equals(p.optString("valueName"))) return p.optString("value");
        }
        return null;
    }

    private static String emmaId(JSONObject area) throws JSONException {
        JSONArray g = area.optJSONArray("geocode");
        if (g == null) return "";
        for (int i = 0; i < g.length(); i++) {
            JSONObject o = g.getJSONObject(i);
            if ("EMMA_ID".equals(o.optString("valueName"))) return o.optString("value");
        }
        return "";
    }

    /** MeteoAlarm awareness types in Bosnian. */
    public static String typeName(int type) {
        switch (type) {
            case 1: return "vjetar";
            case 2: return "snijeg i led";
            case 3: return "grmljavina";
            case 4: return "magla";
            case 5: return "visoke temperature";
            case 6: return "niske temperature";
            case 7: return "obalni uslovi";
            case 8: return "šumski požari";
            case 9: return "lavine";
            case 10: return "kiša";
            case 12: return "poplave";
            case 13: return "kiša i poplave";
            default: return "vremenske prilike";
        }
    }
}
