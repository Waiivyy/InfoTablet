package ba.infotablet.home;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

import ba.infotablet.Settings;
import ba.infotablet.data.Cache;
import ba.infotablet.data.SourceState;
import ba.infotablet.sources.RatesSource;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Freshness;
import ba.infotablet.ui.NarrowSpan;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Ui;
import ba.infotablet.util.Fmt;

/** Exchange rates (KM per unit): the fixed euro and the first currencies chosen in Postavke. */
final class RatesTile extends Tile {

    private static final int MAX_ROWS = 3;
    private final long staleMs = new RatesSource().staleAfterMs();

    RatesTile(Context c, Palette p) {
        super(c, p);
    }

    @Override
    void bind(long now) {
        removeAllViews();
        Context c = getContext();
        SourceState st = Cache.get(c, RatesSource.ID);
        Freshness f = Freshness.of(st, staleMs, now);
        JSONObject data = null;
        try {
            if (f.hasData) data = new JSONObject(st.payload);
        } catch (JSONException e) {
            data = null;
        }
        head("Kursna lista", data == null ? "KM" : "KM · " + Fmt.dayMonth(data.optLong("date")));
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(VERTICAL);
        list.addView(row(c, "EUR", "fiksni kurs", "1,95583"), weight());
        int shown = 0;
        JSONObject rates = data == null ? null : data.optJSONObject("rates");
        List<String> codes = Settings.of(c).currencies();
        for (int i = 0; i < codes.size() && shown < MAX_ROWS; i++) {
            JSONObject x = rates == null ? null : rates.optJSONObject(codes.get(i));
            if (x == null) continue;
            double perUnit = x.optDouble("middle") / Math.max(1, x.optInt("units", 1));
            list.addView(Ui.hairline(c, p.soft), new LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1))));
            list.addView(row(c, codes.get(i), ba.infotablet.useful.UsefulScreen.currencyName(codes.get(i)),
                    String.format(Locale.ROOT, "%.4f", perUnit).replace('.', ',')), weight());
            shown++;
        }
        addView(list, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        if (data == null) {
            addView(note(f.note));
        } else if (f.stale) {
            list.setAlpha(Freshness.STALE_ALPHA);
            addView(note(f.note));
        }
    }

    private LayoutParams weight() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f);
    }

    private LinearLayout row(Context c, String code, String name, String value) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView cd = Ui.text(c, 17, Fonts.monoMedium(), p.fg);
        cd.setText(code);
        r.addView(cd, new LayoutParams(Ui.dp(c, 52), LayoutParams.WRAP_CONTENT));
        TextView nm = Ui.text(c, 13, Fonts.regular(), p.fg2);
        nm.setText(name);
        nm.setSingleLine(true);
        nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
        r.addView(nm, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView v = Ui.text(c, 19, Fonts.mono(), p.fg);
        v.setText(NarrowSpan.numeric(value));
        r.addView(v);
        return r;
    }
}
