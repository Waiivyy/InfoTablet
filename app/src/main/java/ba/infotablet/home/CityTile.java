package ba.infotablet.home;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.TimeZone;

import ba.infotablet.Cities;
import ba.infotablet.Settings;
import ba.infotablet.data.Cache;
import ba.infotablet.data.SourceState;
import ba.infotablet.sources.SecondCitySource;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Freshness;
import ba.infotablet.ui.NarrowSpan;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Ui;
import ba.infotablet.ui.Wmo;
import ba.infotablet.util.Fmt;

/** Weather in the second city (for example where family lives), with its local time when it differs. */
final class CityTile extends Tile {

    private final long staleMs = new SecondCitySource().staleAfterMs();

    CityTile(Context c, Palette p) {
        super(c, p);
    }

    @Override
    void bind(long now) {
        removeAllViews();
        Context c = getContext();
        Cities.City city = Settings.of(c).city2();
        if (city == null) {
            head("Drugi grad", null);
            empty("Grad nije odabran", "Postavke · Lokacija");
            return;
        }
        SourceState st = Cache.get(c, SecondCitySource.ID);
        Freshness f = Freshness.of(st, staleMs, now);
        JSONObject d = null;
        try {
            if (f.hasData) d = new JSONObject(st.payload);
            if (d != null && !city.name.equals(d.optString("city"))) d = null; // data of a previous choice
        } catch (JSONException e) {
            d = null;
        }
        long offset = d == null ? 0 : d.optLong("utcOffset");
        long localOffset = TimeZone.getDefault().getOffset(now);
        String right = d != null && offset != localOffset ? Fmt.hhmmUtc(now + offset) + " lokalno" : null;
        head(city.name + " · sada", right);
        if (d == null) {
            empty(f.hasData ? "Učitava se…" : f.note, null);
            return;
        }
        JSONObject cur = d.optJSONObject("current");
        LinearLayout main = new LinearLayout(c);
        main.setGravity(android.view.Gravity.CENTER_VERTICAL);
        main.addView(Ui.icon(c, Wmo.icon(cur.optInt("code"), cur.optInt("isDay", 1) == 1), p.fg, 48));
        TextView temp = Ui.text(c, 64, Fonts.monoLight(), p.fg);
        temp.setText(NarrowSpan.numeric(Fmt.temp(cur.optDouble("t"))));
        LayoutParams tl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        tl.leftMargin = Ui.dp(c, 10);
        Ui.trimDigits(temp, tl);
        main.addView(temp, tl);
        LayoutParams ml = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        ml.topMargin = Ui.dp(c, 12);
        addView(main, ml);

        TextView desc = Ui.text(c, 16, Fonts.regular(), p.fg);
        desc.setText(Wmo.text(cur.optInt("code")));
        desc.setSingleLine(true);
        LayoutParams dl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        dl.topMargin = Ui.dp(c, 10);
        addView(desc, dl);
        TextView mm = Ui.text(c, 14, Fonts.regular(), p.fg2);
        mm.setText(NarrowSpan.numeric("Danas " + Fmt.temp(d.optDouble("min")) + " / " + Fmt.temp(d.optDouble("max"))));
        LayoutParams nl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        nl.topMargin = Ui.dp(c, 4);
        addView(mm, nl);
        addView(new View(c), new LayoutParams(1, 0, 1f));
        if (f.stale) {
            for (int i = 1; i < getChildCount(); i++) getChildAt(i).setAlpha(Freshness.STALE_ALPHA);
            addView(note(f.note));
        }
    }
}
