package ba.infotablet.home;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

import ba.infotablet.R;
import ba.infotablet.data.SourceState;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Freshness;
import ba.infotablet.ui.NarrowSpan;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Ui;
import ba.infotablet.ui.Wmo;
import ba.infotablet.util.BsDate;
import ba.infotablet.util.Fmt;

/**
 * Weather card on Početna. Until 18:00 it shows the current conditions and today's range;
 * after 18:00 it shows tomorrow ("Sutra"), as agreed in the mockups.
 */
public final class WeatherPanel extends LinearLayout {

    private final Palette p;

    public WeatherPanel(Context c, Palette p) {
        super(c);
        this.p = p;
        setOrientation(VERTICAL);
        setBackgroundDrawable(Ui.panel(c, p));
        setPadding(Ui.dp(c, 16), Ui.dp(c, 13), Ui.dp(c, 16), 0);
    }

    public void bind(SourceState st, long staleAfterMs, String place, long now) {
        removeAllViews();
        Context c = getContext();
        Freshness f = Freshness.of(st, staleAfterMs, now);
        boolean tomorrow = hourOf(now) >= 18;
        try {
            if (!f.hasData) throw new JSONException("no data");
            JSONObject w = new JSONObject(st.payload);
            JSONObject cur = w.getJSONObject("current");
            JSONArray daily = w.getJSONArray("daily");
            JSONObject day = dayAt(daily, now, tomorrow ? 1 : 0);
            if (day == null) throw new JSONException("no day");

            // Header: place and time frame, plus sunrise/sunset (today) or the current temperature (tomorrow).
            LinearLayout head = row(c);
            String title = tomorrow ? "Sutra · " + weekdayOf(day.getLong("date")) : place + " · sada";
            head.addView(Ui.label(c, p, title), new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            if (tomorrow) {
                head.addView(Ui.label(c, p, "sada " + Fmt.temp(cur.getDouble("t"))));
            } else if (day.has("sunrise") && day.has("sunset") && !Ui.hugeText()) {   // on Vrijeme with large text
                head.addView(sun(c, R.drawable.ic_sunrise, Fmt.hhmm(day.getLong("sunrise"))));
                View gap = new View(c);
                head.addView(gap, new LayoutParams(Ui.dp(c, 8), 1));
                head.addView(sun(c, R.drawable.ic_sunset, Fmt.hhmm(day.getLong("sunset"))));
            }
            addView(head);

            int code = tomorrow ? day.optInt("code") : cur.optInt("code");
            boolean isDay = tomorrow || cur.optInt("isDay", 1) == 1;
            double big = tomorrow ? day.optDouble("tmax") : cur.getDouble("t");

            LinearLayout main = row(c);
            main.addView(Ui.icon(c, Wmo.icon(code, isDay), p.fg, 56));
            TextView temp = Ui.text(c, 84, Fonts.monoLight(), p.fg);
            temp.setText(NarrowSpan.numeric(Fmt.temp(big)));
            LayoutParams tl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            tl.leftMargin = Ui.dp(c, 10);
            Ui.trimDigits(temp, tl);
            main.addView(temp, tl);
            LayoutParams ml = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            ml.topMargin = Ui.dp(c, 8);
            addView(main, ml);

            TextView desc = Ui.text(c, 17, Fonts.regular(), p.fg);
            desc.setText(Wmo.text(code));
            desc.setSingleLine(true);
            LayoutParams dl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            dl.topMargin = Ui.dp(c, 8);
            addView(desc, dl);

            TextView sub = Ui.text(c, 14, Fonts.regular(), p.fg2);
            sub.setSingleLine(true);
            if (tomorrow) {
                sub.setText("Najniža " + Fmt.temp(day.optDouble("tmin"))
                        + (day.has("sunrise") ? " · izlazak sunca " + Fmt.hhmm(day.getLong("sunrise")) : ""));
            } else {
                sub.setText("Osjećaj " + Fmt.temp(cur.optDouble("feels", cur.getDouble("t")))
                        + (cur.has("rh") ? " · vlažnost " + Math.round(cur.getDouble("rh")) + "%" : ""));
            }
            LayoutParams sl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            sl.topMargin = Ui.dp(c, 4);
            addView(sub, sl);

            addView(new View(c), new LayoutParams(1, 0, 1f));
            if (f.stale) addView(note(c, f.note));

            String third, thirdValue, thirdUnit;
            if (tomorrow) {
                double snow = day.optDouble("snow", 0);
                third = snow > 0 ? "Snijeg" : "Količina";
                thirdValue = Fmt.dec1(snow > 0 ? snow : day.optDouble("precip", 0));
                thirdUnit = snow > 0 ? " cm" : " mm";
            } else {
                third = "Vjetar";
                thirdValue = String.valueOf(Math.round(cur.optDouble("wind", 0)));
                thirdUnit = " km/h";
            }
            String minMaxLabel = Ui.largerText() ? "Min/maks" : "Min / maks";   // fits the narrower cell with larger text
            CharSequence minMax = value(c, Fmt.temp(day.optDouble("tmin")) + "/" + Fmt.temp(day.optDouble("tmax")), "");
            CharSequence rain = value(c, String.valueOf(day.optInt("pop", 0)), "%");
            if (Ui.hugeText()) {   // two wider cells; wind and amounts stay on Vrijeme
                addView(cells(c, new String[] {minMaxLabel, "Kiša"}, new CharSequence[] {minMax, rain}));
            } else {
                addView(cells(c, new String[] {minMaxLabel, "Kiša", third},
                        new CharSequence[] {minMax, rain, value(c, thirdValue, thirdUnit)}));
            }

            if (f.stale) {
                for (int i = 0; i < getChildCount(); i++) {
                    View v = getChildAt(i);
                    if (v.getTag() == null) v.setAlpha(Freshness.STALE_ALPHA);
                }
            }
        } catch (JSONException e) {
            removeAllViews();
            addView(Ui.label(c, p, place + " · sada"));
            TextView t = Ui.text(c, 16, Fonts.regular(), p.fg3);
            t.setText(f.note != null ? f.note : "Nema podataka");
            t.setGravity(Gravity.CENTER);
            addView(t, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        }
    }

    private LinearLayout row(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private View sun(Context c, int icon, String time) {
        LinearLayout r = row(c);
        r.addView(Ui.icon(c, icon, p.fg2, 15));
        TextView t = Ui.text(c, 11.5f, Fonts.monoMedium(), p.fg2);
        t.setText(NarrowSpan.numeric(time));
        LayoutParams l = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        l.leftMargin = Ui.dp(c, 3);
        r.addView(t, l);
        return r;
    }

    private TextView note(Context c, String text) {
        TextView t = Ui.text(c, 12.5f, Fonts.medium(), p.warn);
        t.setText(text);
        t.setSingleLine(true);
        t.setTag("keep"); // not faded
        t.setPadding(0, 0, 0, Ui.dp(c, 6));
        return t;
    }

    private CharSequence value(Context c, String v, String unit) {
        SpannableStringBuilder b = new SpannableStringBuilder(NarrowSpan.numeric(v));
        if (!unit.isEmpty()) {
            int s = b.length();
            b.append(unit);
            b.setSpan(new AbsoluteSizeSpan(Ui.dp(c, 12)), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new ForegroundColorSpan(p.fg2), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return b;
    }

    /** Bottom strip of three label/value cells with hairlines, flush with the card edges. */
    private View cells(Context c, String[] labels, CharSequence[] values) {
        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(VERTICAL);
        LayoutParams wl = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        wl.leftMargin = -Ui.dp(c, 16);
        wl.rightMargin = -Ui.dp(c, 16);
        wrap.setLayoutParams(wl);
        wrap.addView(Ui.hairline(c, p.soft), new LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1))));
        LinearLayout r = row(c);
        for (int i = 0; i < labels.length; i++) {
            if (i > 0) r.addView(Ui.hairline(c, p.soft), new LayoutParams(Math.max(1, Ui.dp(c, 1)), LayoutParams.MATCH_PARENT));
            LinearLayout cell = new LinearLayout(c);
            cell.setOrientation(VERTICAL);
            cell.setPadding(Ui.dp(c, 16), Ui.dp(c, 7), Ui.dp(c, 4), Ui.dp(c, 9));
            cell.addView(Ui.label(c, p, labels[i]));
            TextView v = Ui.text(c, 18, Fonts.mono(), p.fg);
            v.setText(values[i]);
            v.setSingleLine(true);
            LayoutParams vl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            vl.topMargin = Ui.dp(c, 3);
            cell.addView(v, vl);
            r.addView(cell, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        }
        wrap.addView(r);
        return wrap;
    }

    static JSONObject dayAt(JSONArray daily, long now, int offset) throws JSONException {
        for (int i = 0; i < daily.length(); i++) {
            JSONObject d = daily.getJSONObject(i);
            if (Fmt.dayOffset(now, d.getLong("date")) == offset) return d;
        }
        return null;
    }

    private static int hourOf(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return c.get(Calendar.HOUR_OF_DAY);
    }

    private static String weekdayOf(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return BsDate.weekday(c);
    }
}
