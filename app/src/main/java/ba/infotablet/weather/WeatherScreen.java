package ba.infotablet.weather;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ba.infotablet.data.Cache;
import ba.infotablet.data.SourceState;
import ba.infotablet.sources.AirCamsSource;
import ba.infotablet.sources.AirStationsSource;
import ba.infotablet.sources.WarningsSource;
import ba.infotablet.sources.WeatherSource;
import ba.infotablet.ui.AirLevel;
import ba.infotablet.ui.FontSpan;
import ba.infotablet.ui.FitColumn;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Freshness;
import ba.infotablet.ui.NarrowSpan;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Refreshable;
import ba.infotablet.ui.SpanGrid;
import ba.infotablet.ui.Ui;
import ba.infotablet.ui.WarningText;
import ba.infotablet.ui.Wmo;
import ba.infotablet.util.BsDate;
import ba.infotablet.util.Fmt;

/** Vrijeme: warnings, next 12 hours, 7 days, sun, measured air quality, CAMS outlook and pollen. */
public final class WeatherScreen extends FrameLayout implements Refreshable {

    /** Urban stations of Kanton Sarajevo in display order (Ivan Sedlo is a mountain background station). */
    private static final int[] STATION_ORDER = {12, 34, 3, 17, 13, 27, 26, 18};

    private final Palette p;
    private final SpanGrid grid;
    private final LinearLayout warnings, hourly, daily, air;

    public WeatherScreen(Context c, Palette p) {
        super(c);
        this.p = p;
        grid = new SpanGrid(c, 3, Ui.gap(c));
        int pad = Ui.pagePad(c);
        grid.setPadding(pad, pad, pad, pad);
        warnings = panel(c);
        hourly = panel(c);
        daily = panel(c);
        air = panel(c);
        grid.add(warnings, 0, 0, 3, 1);
        grid.add(hourly, 0, 1, 2, 1);
        grid.add(daily, 0, 2, 2, 1);
        grid.add(air, 2, 1, 1, 2);
        addView(grid, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private LinearLayout panel(Context c) {
        LinearLayout l = new FitColumn(c);   // with large text, rows that do not fit are left out
        l.setBackgroundDrawable(Ui.panel(c, p));
        l.setPadding(Ui.dp(c, 16), Ui.dp(c, 13), Ui.dp(c, 16), Ui.dp(c, 12));
        return l;
    }

    @Override
    public void refresh(long now) {
        Context c = getContext();
        int warnRows = bindWarnings(c, Cache.get(c, WarningsSource.ID), now);
        grid.setRows(warnRows == 0 ? Ui.dp(c, 1) : Ui.dp(c, 18 + 34 * warnRows), Ui.dp(c, 150), -1);
        WeatherSource ws = new WeatherSource();
        SourceState weather = Cache.get(c, WeatherSource.ID);
        bindHourly(c, weather, ws.staleAfterMs(), now);
        bindDaily(c, weather, ws.staleAfterMs(), now);
        bindAir(c, Cache.get(c, AirStationsSource.ID), new AirStationsSource().staleAfterMs(),
                Cache.get(c, AirCamsSource.ID), new AirCamsSource().staleAfterMs(), now);
    }

    // ---- Warnings -------------------------------------------------------------------------

    private int bindWarnings(Context c, SourceState st, long now) {
        warnings.removeAllViews();
        List<JSONObject> list = new ArrayList<JSONObject>();
        if (st.hasData()) {
            try {
                JSONArray ws = new JSONObject(st.payload).getJSONArray("warnings");
                for (int i = 0; i < ws.length() && list.size() < 3; i++) {
                    if (ws.getJSONObject(i).optLong("expires") > now) list.add(ws.getJSONObject(i));
                }
            } catch (JSONException ignored) {
                // no warnings shown
            }
        }
        warnings.setVisibility(list.isEmpty() ? GONE : VISIBLE);
        if (list.isEmpty()) return 0;
        warnings.setBackgroundDrawable(Ui.rounded(c, p.warnTint, p.warnLine, 8));
        warnings.setPadding(Ui.dp(c, 14), Ui.dp(c, 9), Ui.dp(c, 14), Ui.dp(c, 9));
        for (JSONObject w : list) {
            LinearLayout row = hrow(c);
            int level = w.optInt("level");
            TextView chip = Ui.chip(c, WarningText.levelName(level), WarningText.levelColor(level), WarningText.levelTextColor(level));
            row.addView(chip, new LinearLayout.LayoutParams(Ui.dp(c, 112), LinearLayout.LayoutParams.WRAP_CONTENT));
            chip.setGravity(Gravity.CENTER);
            View icon = Ui.icon(c, WarningText.icon(w.optInt("type")), p.warn, 22);
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22));
            il.leftMargin = Ui.dp(c, 12);
            il.rightMargin = Ui.dp(c, 10);
            row.addView(icon, il);
            String[] parts = WarningText.line(w, now);
            SpannableStringBuilder b = new SpannableStringBuilder(parts[0]);
            b.setSpan(new FontSpan(Fonts.medium()), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.append(parts[1]);
            String desc = w.optString("description");
            if (!desc.isEmpty()) b.append(" · ").append(desc);
            TextView t = Ui.text(c, 15, Fonts.regular(), p.fg);
            t.setText(b);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            warnings.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 34)));
        }
        return list.size();
    }

    // ---- Next 12 hours --------------------------------------------------------------------

    private void bindHourly(Context c, SourceState st, long staleAfter, long now) {
        hourly.removeAllViews();
        Freshness f = Freshness.of(st, staleAfter, now);
        hourly.addView(header(c, "Sljedećih 12 sati", f.stale ? f.note : "Open-Meteo", f.stale));
        if (!f.hasData) {
            hourly.addView(empty(c, f.note), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            return;
        }
        LinearLayout strip = hrow(c);
        try {
            JSONArray hs = new JSONObject(st.payload).getJSONArray("hourly");
            int step = Ui.hugeText() ? 2 : 1;   // with large text: six wider cells, every second hour
            int seen = 0;
            for (int i = 0; i < hs.length() && seen < 12; i++) {
                JSONObject h = hs.getJSONObject(i);
                if (h.getLong("time") < now - 30 * 60_000L) continue;
                if (seen++ % step != 0) continue;
                strip.addView(hourCell(c, h), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            }
        } catch (JSONException e) {
            hourly.addView(empty(c, "Nema podataka"));
            return;
        }
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        sl.topMargin = Ui.dp(c, 6);
        hourly.addView(strip, sl);
        if (f.stale) strip.setAlpha(Freshness.STALE_ALPHA);
    }

    private View hourCell(Context c, JSONObject h) throws JSONException {
        LinearLayout cell = new LinearLayout(c);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView time = Ui.text(c, 12.5f, Fonts.mono(), p.fg2);
        time.setText(NarrowSpan.numeric(Fmt.hhmm(h.getLong("time"))));
        cell.addView(time);
        View icon = Ui.icon(c, Wmo.icon(h.optInt("code"), h.optInt("isDay", 1) == 1), p.fg, 26);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26));
        boolean huge = Ui.hugeText();
        int gap = Ui.largerText() ? 4 : 7;   // larger text: tighter, so the rain chance still fits under the temperature
        il.topMargin = Ui.dp(c, gap);
        cell.addView(icon, il);
        TextView t = Ui.text(c, 19, Fonts.mono(), p.fg);
        t.setText(NarrowSpan.numeric(h.has("t") ? Fmt.temp(h.getDouble("t")) : "-"));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.topMargin = Ui.dp(c, gap);
        cell.addView(t, tl);
        if (huge) return cell;   // the chance of rain is in the 7-day list below
        int pop = h.optInt("pop", 0);
        TextView pr = Ui.text(c, 12, Fonts.mono(), pop >= 50 ? p.accent : p.fg2);
        pr.setText(pop + "%");
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pl.topMargin = Ui.dp(c, Ui.largerText() ? 3 : 5);
        cell.addView(pr, pl);
        return cell;
    }

    // ---- 7 days ---------------------------------------------------------------------------

    private void bindDaily(Context c, SourceState st, long staleAfter, long now) {
        daily.removeAllViews();
        Freshness f = Freshness.of(st, staleAfter, now);
        String right = f.stale ? f.note : null;
        JSONArray days = null;
        try {
            if (f.hasData) days = new JSONObject(st.payload).getJSONArray("daily");
            if (right == null && days != null && days.length() > 0) {
                JSONObject d0 = days.getJSONObject(0);
                right = "izlazak " + Fmt.hhmm(d0.getLong("sunrise")) + " · zalazak " + Fmt.hhmm(d0.getLong("sunset"))
                        + " · UV maks " + Fmt.dec1(d0.optDouble("uv", 0));
            }
        } catch (JSONException e) {
            days = null;
        }
        daily.addView(header(c, "7 dana", right, f.stale));
        if (days == null) {
            daily.addView(empty(c, f.note != null ? f.note : "Nema podataka"), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            return;
        }
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        try {
            for (int i = 0; i < days.length(); i++) {
                if (i > 0) list.addView(Ui.hairline(c, p.soft), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1))));
                list.addView(dayRow(c, days.getJSONObject(i), now), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            }
        } catch (JSONException ignored) {
            // partial list is fine
        }
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        ll.topMargin = Ui.dp(c, 4);
        daily.addView(list, ll);
        if (f.stale) list.setAlpha(Freshness.STALE_ALPHA);
    }

    private View dayRow(Context c, JSONObject d, long now) throws JSONException {
        LinearLayout row = hrow(c);
        long date = d.getLong("date");
        int off = Fmt.dayOffset(now, date);
        String name = off == 0 ? "Danas" : off == 1 ? "Sutra" : BsDate.capitalize(Fmt.shortWeekday(date)) + " " + Fmt.dayMonth(date);
        TextView day = Ui.text(c, 16, off <= 1 ? Fonts.medium() : Fonts.regular(), p.fg);
        day.setText(name);
        day.setSingleLine(true);
        row.addView(day, new LinearLayout.LayoutParams(Ui.dp(c, 104), LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(Ui.icon(c, Wmo.icon(d.optInt("code"), true), p.fg, 24));
        TextView desc = Ui.text(c, 15, Fonts.regular(), p.fg2);
        desc.setText(Wmo.text(d.optInt("code")));
        desc.setSingleLine(true);
        desc.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        dl.leftMargin = Ui.dp(c, 12);
        row.addView(desc, dl);
        int pop = d.optInt("pop", 0);
        row.addView(cell(c, pop + "%", pop >= 50 ? p.accent : p.fg2, 15, 56));
        double snow = d.optDouble("snow", 0), rain = d.optDouble("precip", 0);
        String amount = snow > 0 ? Fmt.dec1(snow) + " cm" : rain >= 0.1 ? Fmt.dec1(rain) + " mm" : "";
        row.addView(cell(c, amount, p.fg2, 14, 78));
        row.addView(cell(c, Fmt.temp(d.optDouble("tmin")) + "/" + Fmt.temp(d.optDouble("tmax")), p.fg, 17, 96));
        return row;
    }

    private TextView cell(Context c, String text, int color, float size, float widthDp) {
        TextView t = Ui.text(c, size, Fonts.mono(), color);
        t.setText(NarrowSpan.numeric(text));
        t.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        t.setSingleLine(true);
        t.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, widthDp), LinearLayout.LayoutParams.WRAP_CONTENT));
        return t;
    }

    // ---- Air ------------------------------------------------------------------------------

    private void bindAir(Context c, SourceState stations, long stationsStale, SourceState cams, long camsStale, long now) {
        air.removeAllViews();
        Freshness fs = Freshness.of(stations, stationsStale, now);
        JSONObject sp = null;
        try {
            if (fs.hasData) sp = new JSONObject(stations.payload);
        } catch (JSONException ignored) {
            sp = null;
        }
        long measured = sp == null ? 0 : sp.optLong("measuredAt");
        String title = measured > 0 ? "Zrak · izmjereno " + Fmt.hhmm(measured) : "Zrak · mjerne stanice";
        air.addView(header(c, title, null, false));

        LinearLayout block = new LinearLayout(c);
        block.setOrientation(LinearLayout.VERTICAL);
        if (sp == null) {
            block.addView(empty(c, fs.note));
        } else {
            List<JSONObject> list = orderedStations(sp.optJSONArray("stations"));
            int worst = -1;
            String worstName = "";
            for (JSONObject s : list) {
                if (s.optInt("level", -1) > worst) {
                    worst = s.optInt("level", -1);
                    worstName = s.optString("name");
                }
            }
            LinearLayout head = hrow(c);
            head.addView(Ui.chip(c, AirLevel.name(worst), AirLevel.color(worst), AirLevel.onColor(worst)));
            TextView wn = Ui.text(c, 14, Fonts.regular(), p.fg2);
            wn.setText("najgore: " + worstName);
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            wl.leftMargin = Ui.dp(c, 10);
            head.addView(wn, wl);
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            hl.topMargin = Ui.dp(c, 8);
            hl.bottomMargin = Ui.dp(c, 6);
            block.addView(head, hl);

            LinearLayout cols = hrow(c);
            cols.addView(Ui.label(c, p, "Stanica"), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            cols.addView(rightLabel(c, "PM2.5"));
            cols.addView(rightLabel(c, "PM10"));
            block.addView(cols);
            for (JSONObject s : list) block.addView(stationRow(c, s), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 26)));
            if (fs.stale) {
                block.setAlpha(Freshness.STALE_ALPHA);
            }
        }
        air.addView(block);
        if (fs.stale && fs.hasData) air.addView(warnNote(c, fs.note));

        // Heading and text in one block each, so that with large text they are left out together.
        LinearLayout model = new LinearLayout(c), pollenBlock = new LinearLayout(c);
        model.setOrientation(LinearLayout.VERTICAL);
        pollenBlock.setOrientation(LinearLayout.VERTICAL);
        model.addView(Ui.hairline(c, p.soft), divider(c));
        model.addView(Ui.label(c, p, "Prognoza modela (CAMS)"));
        Freshness fc = Freshness.of(cams, camsStale, now);
        TextView forecast = Ui.text(c, 14.5f, Fonts.regular(), p.fg);
        TextView pollen = Ui.text(c, 14.5f, Fonts.regular(), p.fg);
        try {
            if (!fc.hasData) throw new JSONException("none");
            JSONObject a = new JSONObject(cams.payload);
            int nowLevel = AirLevel.fromAqi(a.optDouble("aqi"));
            int max = -1;
            JSONArray hs = a.optJSONArray("hourly");
            for (int i = 0; hs != null && i < hs.length(); i++) {
                JSONObject h = hs.getJSONObject(i);
                if (h.getLong("time") > now && h.getLong("time") < now + 24 * 3600_000L) {
                    max = Math.max(max, AirLevel.fromAqi(h.optDouble("aqi")));
                }
            }
            forecast.setText("Sada " + AirLevel.name(nowLevel).toLowerCase(Locale.ROOT)
                    + (max >= 0 ? " · narednih 24 h najviše " + AirLevel.name(max).toLowerCase(Locale.ROOT) : ""));
            pollen.setText(pollenText(a.optJSONObject("pollen")));
            if (fc.stale) {
                forecast.setAlpha(Freshness.STALE_ALPHA);
                pollen.setAlpha(Freshness.STALE_ALPHA);
            }
        } catch (JSONException e) {
            forecast.setText(fc.note);
            forecast.setTextColor(p.fg3);
            pollen.setText("");
        }
        forecast.setPadding(0, Ui.dp(c, 3), 0, 0);
        model.addView(forecast);
        air.addView(model);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ll.topMargin = Ui.dp(c, 10);
        pollenBlock.addView(Ui.label(c, p, "Polen"));
        pollen.setPadding(0, Ui.dp(c, 3), 0, 0);
        pollenBlock.addView(pollen);
        air.addView(pollenBlock, ll);

        air.addView(new View(c), new LinearLayout.LayoutParams(1, 0, 1f));
        TextView src = Ui.text(c, 12, Fonts.regular(), p.fg2);
        src.setText("FHMZ BiH (µg/m³) · Copernicus CAMS");
        air.addView(src);
    }

    private List<JSONObject> orderedStations(JSONArray arr) {
        List<JSONObject> out = new ArrayList<JSONObject>();
        if (arr == null) return out;
        for (int id : STATION_ORDER) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject s = arr.optJSONObject(i);
                if (s != null && s.optInt("id") == id) out.add(s);
            }
        }
        return out;
    }

    private View stationRow(Context c, JSONObject s) {
        LinearLayout row = hrow(c);
        int level = s.optInt("level", -1);
        View dot = new View(c);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(AirLevel.color(level));
        dot.setBackgroundDrawable(d);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(c, 10), Ui.dp(c, 10));
        dl.rightMargin = Ui.dp(c, 8);
        row.addView(dot, dl);
        TextView name = Ui.text(c, 15, Fonts.regular(), p.fg);
        name.setText(s.optString("name"));
        name.setSingleLine(true);
        row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(cell(c, s.has("pm25") ? String.valueOf(Math.round(s.optDouble("pm25"))) : "-", p.fg, 15, 56));
        row.addView(cell(c, s.has("pm10") ? String.valueOf(Math.round(s.optDouble("pm10"))) : "-", p.fg, 15, 56));
        return row;
    }

    private static String pollenText(JSONObject pollen) {
        if (pollen == null) return "Nema podataka";
        String[][] names = {{"alder", "Joha"}, {"birch", "Breza"}, {"grass", "Trave"}, {"mugwort", "Pelin"}, {"olive", "Maslina"}, {"ragweed", "Ambrozija"}};
        StringBuilder b = new StringBuilder();
        for (String[] n : names) {
            double v = pollen.optDouble(n[0], 0);
            if (v < 1) continue;
            if (b.length() > 0) b.append(" · ");
            b.append(n[1]).append(' ').append(v < 10 ? "nisko" : v < 50 ? "umjereno" : "visoko");
        }
        return b.length() == 0 ? "Nema polena u zraku" : b.toString();
    }

    // ---- Shared bits ----------------------------------------------------------------------

    private LinearLayout hrow(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private View header(Context c, String title, String right, boolean rightIsWarning) {
        LinearLayout h = hrow(c);
        h.addView(Ui.label(c, p, title), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (right != null) {
            TextView r = Ui.text(c, 12.5f, rightIsWarning ? Fonts.medium() : Fonts.regular(), rightIsWarning ? p.warn : p.fg2);
            r.setText(NarrowSpan.numeric(right));
            r.setSingleLine(true);
            h.addView(r);
        }
        return h;
    }

    private TextView rightLabel(Context c, String s) {
        TextView t = Ui.label(c, p, s);
        t.setGravity(Gravity.END);
        t.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 56), LinearLayout.LayoutParams.WRAP_CONTENT));
        return t;
    }

    private TextView empty(Context c, String note) {
        TextView t = Ui.text(c, 16, Fonts.regular(), p.fg3);
        t.setText(note == null ? "Nema podataka" : note);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private TextView warnNote(Context c, String note) {
        TextView t = Ui.text(c, 12.5f, Fonts.medium(), p.warn);
        t.setText(note);
        t.setPadding(0, Ui.dp(c, 4), 0, 0);
        return t;
    }

    private LinearLayout.LayoutParams divider(Context c) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1)));
        l.topMargin = Ui.dp(c, 10);
        l.bottomMargin = Ui.dp(c, 10);
        return l;
    }
}
