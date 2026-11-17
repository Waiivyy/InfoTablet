package ba.infotablet.home;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ba.infotablet.data.Cache;
import ba.infotablet.data.SourceState;
import ba.infotablet.sources.AirStationsSource;
import ba.infotablet.ui.AirLevel;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Freshness;
import ba.infotablet.ui.NarrowSpan;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Ui;
import ba.infotablet.util.Fmt;

/** Air quality from the FHMZ stations in Sarajevo: the worst level and the most polluted stations. */
final class AirTile extends Tile {

    private static final int MAX_ROWS = 3;
    private final long staleMs = new AirStationsSource().staleAfterMs();

    AirTile(Context c, Palette p) {
        super(c, p);
    }

    @Override
    void bind(long now) {
        removeAllViews();
        Context c = getContext();
        SourceState st = Cache.get(c, AirStationsSource.ID);
        Freshness f = Freshness.of(st, staleMs, now);
        JSONObject d = null;
        try {
            if (f.hasData) d = new JSONObject(st.payload);
        } catch (JSONException e) {
            d = null;
        }
        long measured = d == null ? 0 : d.optLong("measuredAt");
        head("Zrak · FHMZ", measured > 0 ? Fmt.hhmm(measured) : null);
        if (d == null) {
            empty(f.note, null);
            return;
        }
        List<JSONObject> list = new ArrayList<JSONObject>();
        JSONArray a = d.optJSONArray("stations");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject s = a.optJSONObject(i);
            if (s != null && AirLevel.valid(s.optInt("level", -1))) list.add(s);
        }
        Collections.sort(list, (x, y) -> {
            int lx = x.optInt("level"), ly = y.optInt("level");
            if (lx != ly) return ly - lx;
            return Double.compare(y.optDouble("pm25", 0), x.optDouble("pm25", 0));
        });
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(VERTICAL);
        int worst = list.isEmpty() ? -1 : list.get(0).optInt("level");
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.chip(c, AirLevel.name(worst), AirLevel.color(worst), AirLevel.onColor(worst)));
        TextView where = Ui.text(c, 14, Fonts.regular(), p.fg2);
        where.setText(list.isEmpty() ? "" : "najgore: " + list.get(0).optString("name"));
        where.setSingleLine(true);
        LayoutParams wl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        wl.leftMargin = Ui.dp(c, 10);
        top.addView(where, wl);
        LayoutParams tl = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tl.topMargin = Ui.dp(c, 10);
        tl.bottomMargin = Ui.dp(c, 4);
        body.addView(top, tl);
        for (int i = 0; i < list.size() && i < MAX_ROWS; i++) {
            JSONObject s = list.get(i);
            LinearLayout r = new LinearLayout(c);
            r.setGravity(Gravity.CENTER_VERTICAL);
            android.view.View dot = new android.view.View(c);
            dot.setBackgroundDrawable(Ui.rounded(c, AirLevel.color(s.optInt("level")), 0, 5));
            r.addView(dot, new LayoutParams(Ui.dp(c, 10), Ui.dp(c, 10)));
            TextView n = Ui.text(c, 15, Fonts.regular(), p.fg);
            n.setText(s.optString("name"));
            n.setSingleLine(true);
            LayoutParams nl = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            nl.leftMargin = Ui.dp(c, 10);
            r.addView(n, nl);
            TextView v = Ui.text(c, 16, Fonts.mono(), p.fg);
            v.setText(NarrowSpan.numeric(s.has("pm25") ? Math.round(s.optDouble("pm25")) + "" : "--"));
            r.addView(v);
            TextView u = Ui.text(c, 12, Fonts.regular(), p.fg2);
            u.setText(" PM2,5");
            r.addView(u);
            body.addView(r, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        }
        addView(body, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        if (f.stale) {
            body.setAlpha(Freshness.STALE_ALPHA);
            addView(note(f.note));
        }
    }
}
