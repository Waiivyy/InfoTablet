package ba.infotablet.sources;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** org.json helpers that tolerate nulls in the sources' arrays (org.json rejects NaN). */
final class Json {

    private Json() {}

    static void putNum(JSONObject o, String key, JSONArray arr, int i) throws JSONException {
        if (arr != null && i < arr.length() && !arr.isNull(i)) o.put(key, arr.getDouble(i));
    }

    static void putNum(JSONObject o, String key, JSONObject src, String srcKey) throws JSONException {
        if (src.has(srcKey) && !src.isNull(srcKey)) o.put(key, src.getDouble(srcKey));
    }

    static void putInt(JSONObject o, String key, JSONArray arr, int i) throws JSONException {
        if (arr != null && i < arr.length() && !arr.isNull(i)) o.put(key, arr.getInt(i));
    }
}
