package ba.infotablet.fuel;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import ba.infotablet.R;
import ba.infotablet.data.Cache;
import ba.infotablet.data.SourceState;
import ba.infotablet.sources.FuelSource;
import ba.infotablet.ui.FitColumn;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Freshness;
import ba.infotablet.ui.NarrowSpan;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Refreshable;
import ba.infotablet.ui.TrackingSpan;
import ba.infotablet.ui.Ui;
import ba.infotablet.util.Fmt;

/** Gorivo: one button per fuel type, the five cheapest stations, city statistics and other fuels. */
public final class FuelScreen extends FrameLayout implements Refreshable {

    private static final String[][] FUELS = {{"dizel", "Dizel"}, {"super95", "Super 95"}, {"super98", "Super 98"}, {"lpg", "LPG"}};

    private final Palette p;
    private final LinearLayout seg, list, side;
    private final TextView meta;
    private final long staleMs = new FuelSource().staleAfterMs();
    private int selected = 0;
    private long lastNow;

    public FuelScreen(Context c, Palette p) {
        super(c);
        this.p = p;
        int pad = Ui.pagePad(c), gap = Ui.gap(c), sideW = Ui.dp(c, 282);

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout top = new LinearLayout(c);
        seg = new LinearLayout(c);
        seg.setBackgroundDrawable(Ui.panel(c, p));
        int sp = Ui.dp(c, 4);
        seg.setPadding(sp, sp, sp, sp);
        top.addView(seg, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        meta = Ui.text(c, 14, Fonts.regular(), p.fg2);
        meta.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        meta.setLineSpacing(Ui.dp(c, 3), 1f);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(sideW, LinearLayout.LayoutParams.MATCH_PARENT);
        ml.leftMargin = gap;
        top.addView(meta, ml);
        root.addView(top, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 54)));

        LinearLayout bottom = new LinearLayout(c);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundDrawable(Ui.panel(c, p));
        bottom.addView(list, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        side = new FitColumn(c);
        side.setBackgroundDrawable(Ui.panel(c, p));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(sideW, LinearLayout.LayoutParams.MATCH_PARENT);
        sl.leftMargin = gap;
        bottom.addView(side, sl);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        bl.topMargin = gap;
        root.addView(bottom, bl);

        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    @Override
    public void refresh(long now) {
        lastNow = now;
        Context c = getContext();
        SourceState st = Cache.get(c, FuelSource.ID);
        FuelData d = FuelData.of(st, staleMs, now);
        bindSeg(c);
        String key = FUELS[selected][0];
        JSONObject fuel = d.fuel(key);

        if (d.payload == null) {
            meta.setText("Sarajevo");
        } else {
            int count = fuel == null ? 0 : fuel.optInt("count");
            meta.setText("Sarajevo · " + Fmt.plural(count, "pumpa", "pumpe", "pumpi") + "\nCijene važe za " + Fmt.fullDate(d.payload.optLong("dataDate")));
        }
        bindList(c, d, fuel);
        bindSide(c, d, st, fuel);
    }

    private void bindSeg(Context c) {
        seg.removeAllViews();
        for (int i = 0; i < FUELS.length; i++) {
            final int index = i;
            TextView b = Ui.text(c, 17, Fonts.medium(), i == selected ? p.accentFg : p.fg2);
            b.setText(FUELS[i][1]);
            b.setGravity(Gravity.CENTER);
            if (i == selected) b.setBackgroundDrawable(Ui.rounded(c, p.accent, 0, 7));
            b.setOnClickListener(v -> {
                selected = index;
                refresh(lastNow);
            });
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) l.leftMargin = Ui.dp(c, 4);
            seg.addView(b, l);
        }
    }

    private void bindList(Context c, FuelData d, JSONObject fuel) {
        list.removeAllViews();
        LinearLayout head = new LinearLayout(c);
        head.setPadding(0, Ui.dp(c, 10), Ui.dp(c, 18), Ui.dp(c, 8));
        TextView hash = Ui.label(c, p, "#");
        hash.setGravity(Gravity.CENTER);
        head.addView(hash, new LinearLayout.LayoutParams(Ui.dp(c, 46), LinearLayout.LayoutParams.WRAP_CONTENT));
        head.addView(Ui.label(c, p, "Pumpa"), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView unit = Ui.label(c, p, "KM po litru");
        unit.setGravity(Gravity.END);
        head.addView(unit, new LinearLayout.LayoutParams(Ui.dp(c, 150), LinearLayout.LayoutParams.WRAP_CONTENT));
        list.addView(head);
        list.addView(Ui.hairline(c, p.line), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1))));

        JSONArray top = fuel == null ? null : fuel.optJSONArray("top");
        if (d.payload == null || top == null || top.length() == 0) {
            TextView t = Ui.text(c, 17, Fonts.regular(), p.fg3);
            t.setText(d.payload == null ? d.note : "Nema cijena za ovo gorivo");
            t.setGravity(Gravity.CENTER);
            list.addView(t, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            return;
        }
        String date = Fmt.dayMonth(d.payload.optLong("dataDate"));
        int best = -1;
        for (int i = 0; i < top.length() && best < 0; i++) if (!top.optJSONObject(i).optBoolean("outlier")) best = i;
        for (int i = 0; i < 5; i++) {
            if (i > 0) list.addView(Ui.hairline(c, p.soft), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1))));
            View row = i < top.length() ? row(c, i, top.optJSONObject(i), date, i == best) : new View(c);
            if (d.stale) row.setAlpha(Freshness.STALE_ALPHA);
            list.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        if (d.stale && d.note != null) {
            TextView n = Ui.text(c, 12.5f, Fonts.medium(), p.warn);
            n.setText(d.note);
            n.setPadding(Ui.dp(c, 46), 0, 0, Ui.dp(c, 8));
            list.addView(n);
        }
    }

    private View row(Context c, int i, JSONObject s, String date, boolean best) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, 0, Ui.dp(c, 18), 0);
        TextView rank = Ui.text(c, 17, Fonts.mono(), p.fg3);
        rank.setText(String.valueOf(i + 1));
        rank.setGravity(Gravity.CENTER);
        r.addView(rank, new LinearLayout.LayoutParams(Ui.dp(c, 46), LinearLayout.LayoutParams.WRAP_CONTENT));

        boolean outlier = s.optBoolean("outlier");
        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout nameRow = new LinearLayout(c);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = Ui.text(c, 19, Fonts.medium(), p.fg);
        name.setText(s.optString("name"));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        nameRow.addView(name);
        if (outlier) nameRow.addView(checkTag(c));
        mid.addView(nameRow);
        LinearLayout addr = new LinearLayout(c);
        addr.setGravity(Gravity.CENTER_VERTICAL);
        addr.addView(Ui.icon(c, R.drawable.ic_pin, p.fg2, 15));
        TextView a = Ui.text(c, 14.5f, Fonts.regular(), p.fg2);
        a.setText(s.optString("address"));
        a.setSingleLine(true);
        a.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        al.leftMargin = Ui.dp(c, 5);
        addr.addView(a, al);
        LinearLayout.LayoutParams adl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        adl.topMargin = Ui.dp(c, 3);
        mid.addView(addr, adl);
        r.addView(mid, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout pc = new LinearLayout(c);
        pc.setOrientation(LinearLayout.VERTICAL);
        pc.setGravity(Gravity.END);
        TextView price = Ui.text(c, 36, Fonts.monoMedium(), outlier ? p.fg3 : (best ? p.accent : p.fg));
        price.setText(NarrowSpan.numeric(Fmt.dec2(s.optDouble("price"))));
        pc.addView(price, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView d = Ui.text(c, 12.5f, Fonts.regular(), p.fg2);
        d.setText(date);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dl.topMargin = Ui.dp(c, 1);
        pc.addView(d, dl);
        r.addView(pc, new LinearLayout.LayoutParams(Ui.dp(c, 150), LinearLayout.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private void bindSide(Context c, FuelData d, SourceState st, JSONObject fuel) {
        side.removeAllViews();
        int hp = Ui.dp(c, 16);
        LinearLayout stats = block(c, hp);
        stats.addView(Ui.label(c, p, FUELS[selected][1] + " u Sarajevu"));
        if (fuel != null) {
            stats.addView(kv(c, "Najniža", Fmt.dec2(fuel.optDouble("min")), false));
            stats.addView(kv(c, "Prosjek", Fmt.dec2(fuel.optDouble("avg")), false));
            stats.addView(kv(c, "Najviša", Fmt.dec2(fuel.optDouble("max")), false));
        } else {
            stats.addView(kv(c, "Nema podataka", "", false));
        }
        side.addView(stats);
        side.addView(Ui.hairline(c, p.soft), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1))));

        LinearLayout others = block(c, hp);
        others.addView(Ui.label(c, p, "Najjeftinije ostalo"));
        String outlierNote = null;
        for (String[] f : FUELS) {
            if (f[0].equals(FUELS[selected][0])) continue;
            JSONObject o = d.fuel(f[0]);
            JSONArray top = o == null ? null : o.optJSONArray("top");
            JSONObject first = top == null || top.length() == 0 ? null : top.optJSONObject(0);
            boolean outlier = first != null && first.optBoolean("outlier");
            others.addView(kv(c, f[1], first == null ? "-" : Fmt.dec2(first.optDouble("price")), outlier));
            if (outlier && outlierNote == null) {
                double median = o.optDouble("median");
                long pct = Math.round(Math.abs(first.optDouble("price") - median) / median * 100);
                outlierNote = f[1] + " " + Fmt.dec2(first.optDouble("price")) + " je " + pct + "% "
                        + (first.optDouble("price") < median ? "ispod" : "iznad") + " uobičajene cijene u gradu.";
            }
        }
        side.addView(others);
        if (d.stale) {
            stats.setAlpha(Freshness.STALE_ALPHA);
            others.setAlpha(Freshness.STALE_ALPHA);
        }

        side.addView(new View(c), new LinearLayout.LayoutParams(1, 0, 1f));
        LinearLayout foot = new LinearLayout(c);   // divider and source together, so they are left out together
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.addView(Ui.hairline(c, p.soft), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1))));
        TextView src = Ui.text(c, 12.5f, Fonts.regular(), p.fg2);
        src.setLineSpacing(Ui.dp(c, 2), 1f);
        String updated = st.okAt > 0 ? " · osvježeno " + Fmt.when(lastNow, st.okAt) : "";
        // With large text only the source; the "Provjeriti" mark above still flags the odd price.
        if (Ui.hugeText()) src.setText("Izvor: uhvatipopust.ba" + updated);
        else src.setText((outlierNote != null ? outlierNote + "\n" : "") + "Izvor: uhvatipopust.ba (podaci FMT eOPC)" + updated);
        src.setPadding(hp, Ui.dp(c, 11), hp, Ui.dp(c, 12));
        foot.addView(src);
        side.addView(foot);
    }

    private LinearLayout block(Context c, int hp) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(hp, Ui.dp(c, 13), hp, Ui.dp(c, 13));
        return b;
    }

    private View kv(Context c, String k, String v, boolean outlier) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView key = Ui.text(c, 15, Fonts.regular(), p.fg2);
        key.setText(k);
        r.addView(key);
        if (outlier) r.addView(checkTag(c));
        r.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView val = Ui.text(c, 19, Fonts.mono(), p.fg);
        val.setText(NarrowSpan.numeric(v));
        r.addView(val);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        l.topMargin = Ui.dp(c, 9);
        r.setLayoutParams(l);
        return r;
    }

    private TextView checkTag(Context c) {
        TextView t = Ui.text(c, 11, Fonts.medium(), p.warn);
        t.setText(TrackingSpan.of("PROVJERITI", 0.07f));
        t.setBackgroundDrawable(Ui.rounded(c, 0x00000000, p.warn, 4));
        t.setPadding(Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 2));
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        l.leftMargin = Ui.dp(c, 8);
        t.setLayoutParams(l);
        return t;
    }
}
