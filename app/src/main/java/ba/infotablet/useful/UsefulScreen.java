package ba.infotablet.useful;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import ba.infotablet.R;
import ba.infotablet.Settings;
import ba.infotablet.data.Cache;
import ba.infotablet.data.SourceState;
import ba.infotablet.sources.RatesSource;
import ba.infotablet.sources.RoadsParser;
import ba.infotablet.sources.RoadsSource;
import ba.infotablet.sources.SmogSource;
import ba.infotablet.sources.GasSource;
import ba.infotablet.sources.HeatSource;
import ba.infotablet.sources.PowerSource;
import ba.infotablet.sources.StreetNotices;
import ba.infotablet.sources.WaterParser;
import ba.infotablet.sources.WaterSource;
import ba.infotablet.ui.FitColumn;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Freshness;
import ba.infotablet.ui.NarrowSpan;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Refreshable;
import ba.infotablet.ui.SmogText;
import ba.infotablet.ui.SpanGrid;
import ba.infotablet.ui.Ui;
import ba.infotablet.util.BsDate;
import ba.infotablet.util.Fmt;

/** Korisno: exchange rates, water works for your streets, smog episode, roads and borders, winter equipment. */
public final class UsefulScreen extends FrameLayout implements Refreshable {

    public static final String[][] CURRENCY_NAMES = {
            {"USD", "američki dolar"}, {"CHF", "švicarski franak"}, {"GBP", "britanska funta"}, {"TRY", "turska lira"},
            {"RSD", "srpski dinar"}, {"HUF", "mađarska forinta"}, {"SEK", "švedska kruna"}, {"NOK", "norveška kruna"},
            {"DKK", "danska kruna"}, {"CAD", "kanadski dolar"}, {"AUD", "australski dolar"}, {"JPY", "japanski jen"}, {"CNY", "kineski juan"}};

    private final Palette p;
    private final LinearLayout rates, water, smog, roads, winter;

    public UsefulScreen(Context c, Palette p) {
        super(c);
        this.p = p;
        SpanGrid grid = new SpanGrid(c, 3, Ui.gap(c));
        int pad = Ui.pagePad(c);
        grid.setPadding(pad, pad, pad, pad);
        grid.setRows(-1, Ui.dp(c, 200));
        rates = panel(c);
        water = panel(c);
        smog = panel(c);
        roads = panel(c);
        winter = panel(c);
        grid.add(rates, 0, 0, 1, 1);
        grid.add(water, 1, 0, 1, 1);
        grid.add(smog, 2, 0, 1, 1);
        grid.add(roads, 0, 1, 2, 1);
        grid.add(winter, 2, 1, 1, 1);
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
        bindRates(c, Cache.get(c, RatesSource.ID), new RatesSource().staleAfterMs(), now);
        bindWater(c, now);
        bindSmog(c, Cache.get(c, SmogSource.ID), new SmogSource().staleAfterMs(), now);
        bindRoads(c, Cache.get(c, RoadsSource.ID), new RoadsSource().staleAfterMs(), now);
        bindWinter(c, now);
    }

    // ---- Exchange rates ----

    private void bindRates(Context c, SourceState st, long staleMs, long now) {
        rates.removeAllViews();
        rates.addView(Ui.label(c, p, "Kursna lista · KM"));
        Freshness f = Freshness.of(st, staleMs, now);
        LinearLayout list = new FitColumn(c);
        list.addView(rateRow(c, "EUR", "euro, fiksni kurs", "1,95583"));
        JSONObject data = null;
        try {
            if (f.hasData) data = new JSONObject(st.payload);
        } catch (JSONException ignored) {
            data = null;
        }
        if (data != null) {
            JSONObject r = data.optJSONObject("rates");
            for (String code : Settings.of(c).currencies()) {
                JSONObject x = r == null ? null : r.optJSONObject(code.trim());
                if (x == null) continue;
                double perUnit = x.optDouble("middle") / Math.max(1, x.optInt("units", 1));
                list.addView(rateRow(c, code.trim(), currencyName(code.trim()), String.format(Locale.ROOT, "%.4f", perUnit).replace('.', ',')));
            }
        }
        rates.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        if (data == null) {
            rates.addView(note(c, f.note));
        } else {
            if (f.stale) list.setAlpha(Freshness.STALE_ALPHA);
            TextView foot = small(c, "Lista br. " + data.optInt("number") + " · važi od " + Fmt.fullDate(data.optLong("date")) + "\nIzvor: Centralna banka BiH, www.cbbh.ba");
            rates.addView(foot);
        }
    }

    private View rateRow(Context c, String code, String name, String value) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView cd = Ui.text(c, 17, Fonts.monoMedium(), p.fg);
        cd.setText(code);
        left.addView(cd);
        TextView nm = Ui.text(c, 12.5f, Fonts.regular(), p.fg2);
        nm.setText(name);
        left.addView(nm);
        r.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = Ui.text(c, 20, Fonts.mono(), p.fg);
        v.setText(NarrowSpan.numeric(value));
        r.addView(v);
        if (Ui.hugeText()) {   // natural height; FitColumn leaves out the currencies that do not fit
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            l.topMargin = Ui.dp(c, 6);
            r.setLayoutParams(l);
        } else {
            r.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        return r;
    }

    public static String currencyName(String code) {
        for (String[] n : CURRENCY_NAMES) if (n[0].equals(code)) return n[1];
        return "";
    }

    // ---- Water, power, heating and gas for the configured streets ----

    private static final String[][] NOTICE_SOURCES = {
            {WaterSource.ID, "voda"}, {PowerSource.ID, "struja"}, {HeatSource.ID, "grijanje"}, {GasSource.ID, "plin"}};

    private void bindWater(Context c, long now) {
        water.removeAllViews();
        water.addView(Ui.label(c, p, Ui.hugeText() ? "Komunalne obavijesti" : "Voda · struja · grijanje · plin"));
        String places = Settings.of(c).streets();
        if (places.trim().isEmpty()) {
            water.addView(center(c, "Ulice nisu podešene"), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            water.addView(small(c, "Ulice i naselje se podešavaju u postavkama."));
            return;
        }
        String[] payloads = new String[4];
        long oldest = Long.MAX_VALUE;
        int withData = 0;
        StringBuilder missing = new StringBuilder();
        for (int i = 0; i < NOTICE_SOURCES.length; i++) {
            SourceState st = Cache.get(c, NOTICE_SOURCES[i][0]);
            Freshness f = Freshness.of(st, 12 * 3600_000L, now);
            if (f.hasData) withData++;
            if (f.hasData && !f.stale) {
                payloads[i] = st.payload;
                oldest = Math.min(oldest, st.okAt);
            } else {
                if (f.hasData) payloads[i] = st.payload;
                missing.append(missing.length() == 0 ? "" : ", ").append(NOTICE_SOURCES[i][1]);
            }
        }
        long today = Fmt.startOfDay(now);
        List<StreetNotices.Notice> all = StreetNotices.all(payloads[0], payloads[1], payloads[2], payloads[3], places, now);
        if (all.isEmpty()) {
            water.addView(center(c, withData == 0 ? "Nema podataka" : "Nema obavijesti za vaše ulice"),
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            LinearLayout list = new LinearLayout(c);
            list.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < all.size() && i < 3; i++) list.addView(noticeItem(c, all.get(i), now, all.size() > 1));
            water.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        String foot = missing.length() > 0 ? "Bez svježih podataka: " + missing
                : oldest == Long.MAX_VALUE ? "" : "Provjereno " + Fmt.when(now, oldest);
        if (all.size() > 3) foot = "Još " + (all.size() - 3) + " · " + foot;
        water.addView(small(c, foot));
    }

    private View noticeItem(Context c, StreetNotices.Notice n, long now, boolean several) {
        LinearLayout item = new LinearLayout(c);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(0, Ui.dp(c, 8), 0, 0);
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        int off = Fmt.dayOffset(now, n.day);
        String when = n.announcement ? "Najava" : off <= 0 ? "Danas" : off == 1 ? "Sutra" : BsDate.capitalize(Fmt.shortWeekday(n.day)) + " " + Fmt.dayMonth(n.day);
        head.addView(Ui.chip(c, when, p.warnTint, p.warn));
        int res = StreetNotices.POWER.equals(n.kind) ? R.drawable.ic_bolt
                : StreetNotices.WATER.equals(n.kind) ? R.drawable.ic_droplet : R.drawable.ic_flame;
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18));
        il.leftMargin = Ui.dp(c, 8);
        head.addView(Ui.icon(c, res, p.fg2, 18), il);
        TextView title = Ui.text(c, 15, Fonts.medium(), p.fg);
        title.setText(n.title);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = Ui.dp(c, 6);
        head.addView(title, tl);
        item.addView(head);
        TextView t = Ui.text(c, 14, Fonts.regular(), p.fg);
        t.setText(n.detail);
        t.setMaxLines(several ? 2 : 4);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setLineSpacing(Ui.dp(c, 2), 1f);
        t.setPadding(0, Ui.dp(c, 4), 0, 0);
        item.addView(t);
        return item;
    }

    // ---- Smog ----

    private void bindSmog(Context c, SourceState st, long staleMs, long now) {
        smog.removeAllViews();
        smog.addView(Ui.label(c, p, "Smog · Kanton Sarajevo"));
        JSONObject s = SmogText.active(st, now);
        Freshness f = Freshness.of(st, staleMs, now);
        if (s == null) {
            smog.addView(center(c, f.hasData ? "Nema proglašene epizode" : f.note), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            smog.addView(small(c, "Epizode Upozorenje i Uzbuna proglašava Kanton Sarajevo.\nIzvor: vlada.ks.gov.ba"));
            return;
        }
        String level = s.optString("level");
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 6));
        head.addView(Ui.chip(c, SmogText.levelName(level), SmogText.levelColor(level), 0xFFFFFFFF));
        TextView z = Ui.text(c, 15, Fonts.medium(), p.fg);
        z.setText(SmogText.zones(s, level));
        LinearLayout.LayoutParams zl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        zl.leftMargin = Ui.dp(c, 10);
        head.addView(z, zl);
        smog.addView(head);
        TextView m = Ui.text(c, 14, Fonts.regular(), p.fg);
        m.setText(SmogText.measures(level));
        m.setLineSpacing(Ui.dp(c, 3), 1f);
        smog.addView(m, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        smog.addView(small(c, "Proglašeno " + Fmt.fullDate(s.optLong("declaredAt")) + " · Vlada Kantona Sarajevo"));
    }

    // ---- Roads and borders ----

    private void bindRoads(Context c, SourceState st, long staleMs, long now) {
        roads.removeAllViews();
        Freshness f = Freshness.of(st, staleMs, now);
        JSONObject data = null;
        try {
            if (f.hasData) data = new JSONObject(st.payload);
        } catch (JSONException ignored) {
            data = null;
        }
        long updated = data == null ? 0 : data.optLong("updated");
        roads.addView(Ui.label(c, p, "Ceste i granice · BIHAMK" + (updated > 0 ? " · ažurirano " + Fmt.when(now, updated) : "")));
        if (data == null) {
            roads.addView(center(c, f.note), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            return;
        }
        LinearLayout list = new FitColumn(c);
        list.setPadding(0, Ui.dp(c, 6), 0, 0);
        String places = Settings.of(c).getString("water_places");
        int borderIssues = 0, roadItems = 0;
        JSONArray cats = data.optJSONArray("categories");
        StringBuilder borders = new StringBuilder();
        for (int i = 0; cats != null && i < cats.length(); i++) {
            JSONObject cat = cats.optJSONObject(i);
            JSONArray entries = cat.optJSONArray("entries");
            boolean border = cat.optString("name").startsWith("Granični");
            for (int k = 0; entries != null && k < entries.length(); k++) {
                JSONObject e = entries.optJSONObject(k);
                String text = e.optString("text");
                if (border) {
                    if (!text.startsWith(RoadsParser.BORDER_DEFAULT) && borderIssues < 2) {
                        list.addView(roadLine(c, e.optString("title"), text));
                        borderIssues++;
                    }
                } else if (roadItems < 3 && relevant(e.optString("title") + " " + text, places)) {
                    list.addView(roadLine(c, e.optString("title"), text));
                    roadItems++;
                }
            }
        }
        if (borderIssues == 0) list.addView(roadLine(c, "Granični prelazi", "Zadržavanja putničkih vozila nisu duža od 30 minuta."), 0);
        if (roadItems == 0) {
            JSONArray summary = data.optJSONArray("summary");
            if (summary != null && summary.length() > 0) list.addView(roadLine(c, "Opšte stanje", summary.optString(0)));
        }
        if (f.stale) list.setAlpha(Freshness.STALE_ALPHA);
        roads.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        if (f.stale) roads.addView(note(c, f.note));
    }

    private static boolean relevant(String text, String places) {
        String t = text.toLowerCase(Locale.ROOT);
        return t.contains("sarajev") || t.contains("ilidž") || t.contains("vogošć") || t.contains("hadžić")
                || (places != null && !places.trim().isEmpty() && WaterParser.matches(text, places));
    }

    private View roadLine(Context c, String title, String text) {
        TextView t = Ui.text(c, 14, Fonts.regular(), p.fg);
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(title);
        b.setSpan(new ba.infotablet.ui.FontSpan(Fonts.medium()), 0, b.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.append(": ").append(text);
        t.setText(b);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));
        return t;
    }

    // ---- Winter equipment ----

    private void bindWinter(Context c, long now) {
        winter.removeAllViews();
        winter.addView(Ui.label(c, p, "Zimska oprema"));
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        int m = cal.get(Calendar.MONTH) + 1, y = cal.get(Calendar.YEAR);
        boolean season = m >= 11 || m <= 3;
        TextView big = Ui.text(c, 20, Fonts.medium(), season ? p.fg : p.fg2);
        big.setText(season ? "Obavezna do 1. aprila " + (m >= 11 ? y + 1 : y) + "." : "Obavezna od 1. novembra.");
        big.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 6));
        winter.addView(big);
        TextView t = Ui.text(c, 14, Fonts.regular(), p.fg2);
        t.setText("Zimska oprema je zakonska obaveza u BiH od 1. novembra do 1. aprila.");
        t.setLineSpacing(Ui.dp(c, 2), 1f);
        winter.addView(t);
    }

    // ---- Shared ----

    private TextView center(Context c, String text) {
        TextView t = Ui.text(c, 16, Fonts.regular(), p.fg3);
        t.setText(text == null ? "Nema podataka" : text);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private TextView small(Context c, String text) {
        TextView t = Ui.text(c, 12, Fonts.regular(), p.fg2);
        t.setText(text);
        t.setLineSpacing(Ui.dp(c, 2), 1f);
        t.setPadding(0, Ui.dp(c, 6), 0, 0);
        return t;
    }

    private TextView note(Context c, String text) {
        TextView t = Ui.text(c, 12.5f, Fonts.medium(), p.warn);
        t.setText(text);
        return t;
    }
}
