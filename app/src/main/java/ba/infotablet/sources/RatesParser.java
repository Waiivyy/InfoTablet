package ba.infotablet.sources;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.TimeZone;

import ba.infotablet.util.Iso;

/**
 * CBBH daily exchange rate list (GetJson) to {date, number, rates:{USD:{units,buy,middle,sell}, ...}}.
 * All numbers come as strings; rates are KM per {@code units} of the currency.
 */
public final class RatesParser {

    private RatesParser() {}

    public static JSONObject parse(String raw) throws JSONException {
        JSONObject in = new JSONObject(raw);
        JSONArray items = in.getJSONArray("CurrencyExchangeItems");
        JSONObject rates = new JSONObject();
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.getJSONObject(i);
            String code = it.optString("AlphaCode");
            double middle = num(it.optString("Middle"));
            if (code.isEmpty() || Double.isNaN(middle)) continue;
            JSONObject r = new JSONObject().put("units", (int) num(it.optString("Units", "1"))).put("middle", middle);
            double buy = num(it.optString("Buy")), sell = num(it.optString("Sell"));
            if (!Double.isNaN(buy)) r.put("buy", buy);
            if (!Double.isNaN(sell)) r.put("sell", sell);
            rates.put(code, r);
        }
        if (rates.length() == 0) throw new JSONException("No rates in the list");
        long date = Iso.parse(in.optString("Date"), TimeZone.getTimeZone("Europe/Sarajevo"));
        if (date == 0) throw new JSONException("No list date");
        return new JSONObject().put("date", date).put("number", in.optInt("Number")).put("rates", rates);
    }

    private static double num(String s) {
        if (s == null || s.trim().isEmpty()) return Double.NaN;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
