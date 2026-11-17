package ba.infotablet.sport;

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

import ba.infotablet.data.Cache;
import ba.infotablet.data.SourceState;
import ba.infotablet.sources.SportSource;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Freshness;
import ba.infotablet.ui.NarrowSpan;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Refreshable;
import ba.infotablet.ui.Ui;
import ba.infotablet.util.Fmt;

/** Sport: the national team and the chosen club (next match, last result) and the WWin liga table. */
public final class SportScreen extends FrameLayout implements Refreshable {

    private final Palette p;
    private final LinearLayout national, club, table;
    private final long staleMs = new SportSource().staleAfterMs();

    public SportScreen(Context c, Palette p) {
        super(c);
        this.p = p;
        int pad = Ui.dp(c, 12), gap = Ui.dp(c, 10);
        LinearLayout root = new LinearLayout(c);
        root.setPadding(pad, pad, pad, pad);
        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        national = panel(c);
        club = panel(c);
        left.addView(national, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        cl.topMargin = gap;
        left.addView(club, cl);
        root.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        table = panel(c);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(Ui.dp(c, 340), LinearLayout.LayoutParams.MATCH_PARENT);
        tl.leftMargin = gap;
        root.addView(table, tl);
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private LinearLayout panel(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundDrawable(Ui.panel(c, p));
        l.setPadding(Ui.dp(c, 16), Ui.dp(c, 13), Ui.dp(c, 16), Ui.dp(c, 12));
        return l;
    }

    @Override
    public void refresh(long now) {
        Context c = getContext();
        SourceState st = Cache.get(c, SportSource.ID);
        Freshness f = Freshness.of(st, staleMs, now);
        national.removeAllViews();
        club.removeAllViews();
        table.removeAllViews();
        JSONObject data = null;
        try {
            if (f.hasData) data = new JSONObject(st.payload);
        } catch (JSONException ignored) {
            data = null;
        }
        if (data == null) {
            national.addView(Ui.label(c, p, "Reprezentacija BiH"));
            national.addView(empty(c, f.note), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            club.addView(Ui.label(c, p, "Klub"));
            club.addView(empty(c, f.note), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            table.addView(Ui.label(c, p, "Premijer liga BiH"));
            table.addView(empty(c, f.note), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            return;
        }
        JSONObject nat = data.optJSONObject("national");
        JSONObject next = nat == null ? null : nat.optJSONObject("next");
        String natTitle = "Reprezentacija BiH" + (next != null && !next.optString("competition").isEmpty() ? " · " + next.optString("competition") : "");
        matchCard(c, national, natTitle, next, nat == null ? null : nat.optJSONObject("last"), now);

        JSONObject cl = data.optJSONObject("club");
        if (cl == null) {
            club.addView(Ui.label(c, p, "Klub"));
            TextView t = empty(c, "Klub nije odabran");
            club.addView(t, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            String title = cl.optString("name") + " · " + cl.optInt("pos") + ". mjesto · " + Fmt.plural(cl.optInt("pts"), "bod", "boda", "bodova");
            matchCard(c, club, title, cl.optJSONObject("next"), cl.optJSONObject("last"), now);
        }
        bindTable(c, data.optJSONObject("table"), cl == null ? 0 : cl.optInt("teamId"));

        if (f.stale) {
            for (LinearLayout l : new LinearLayout[] {national, club, table}) {
                for (int i = 0; i < l.getChildCount(); i++) l.getChildAt(i).setAlpha(Freshness.STALE_ALPHA);
            }
            TextView n = Ui.text(c, 12.5f, Fonts.medium(), p.warn);
            n.setText(f.note);
            table.addView(n);
        }
    }

    private void matchCard(Context c, LinearLayout panel, String title, JSONObject next, JSONObject last, long now) {
        TextView label = Ui.label(c, p, title);
        label.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(label);
        if (next == null) {
            panel.addView(empty(c, "Nema zakazane utakmice"), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            long k = next.has("kickoff") ? next.optLong("kickoff") : next.optLong("date");
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(team(c, next.optString("home"), Gravity.END), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout mid = new LinearLayout(c);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.setGravity(Gravity.CENTER_HORIZONTAL);
            boolean live = now >= k && now < k + 2 * 3600_000L;
            TextView day = Ui.text(c, 13, Fonts.medium(), live ? p.accent : p.fg2);
            day.setText(live ? "U TOKU" : Fmt.relDay(now, k));
            mid.addView(day);
            TextView time = Ui.text(c, 32, Fonts.mono(), p.fg);
            time.setText(NarrowSpan.numeric(next.has("kickoff") ? Fmt.hhmm(k) : "--:--"));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tl.topMargin = Ui.dp(c, 2);
            mid.addView(time, tl);
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(Ui.dp(c, 120), LinearLayout.LayoutParams.WRAP_CONTENT);
            row.addView(mid, ml);
            row.addView(team(c, next.optString("away"), Gravity.START), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            panel.addView(new View(c), new LinearLayout.LayoutParams(1, 0, 1f));
            panel.addView(row);
            String venue = next.optString("venue");
            if (!venue.isEmpty()) {
                TextView v = Ui.text(c, 14, Fonts.regular(), p.fg2);
                v.setText(venue);
                v.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                vl.topMargin = Ui.dp(c, 6);
                panel.addView(v, vl);
            }
            panel.addView(new View(c), new LinearLayout.LayoutParams(1, 0, 1f));
        }
        if (last != null) {
            panel.addView(Ui.hairline(c, p.soft), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1))));
            LinearLayout r = new LinearLayout(c);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, Ui.dp(c, 8), 0, 0);
            TextView l = Ui.label(c, p, "Zadnja");
            r.addView(l, new LinearLayout.LayoutParams(Ui.dp(c, 64), LinearLayout.LayoutParams.WRAP_CONTENT));
            String score = last.has("ft") ? last.optString("ft") : last.optString("score");
            long when = last.has("kickoff") ? last.optLong("kickoff") : last.optLong("date");
            TextView t = Ui.text(c, 16, Fonts.regular(), p.fg);
            t.setText(last.optString("home") + "  " + score + "  " + last.optString("away"));
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            r.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView d = Ui.text(c, 13, Fonts.mono(), p.fg2);
            d.setText(NarrowSpan.numeric(Fmt.dayMonth(when)));
            r.addView(d);
            panel.addView(r);
        }
    }

    private TextView team(Context c, String name, int gravity) {
        TextView t = Ui.text(c, 20, Fonts.medium(), p.fg);
        t.setText(name);
        t.setGravity(gravity | Gravity.CENTER_VERTICAL);
        t.setMaxLines(2);
        t.setLineSpacing(Ui.dp(c, 2), 1f);
        return t;
    }

    private void bindTable(Context c, JSONObject t, int highlight) {
        table.addView(Ui.label(c, p, t == null ? "Premijer liga BiH" : t.optString("title")));
        LinearLayout head = row(c);
        head.addView(cell(c, "#", 26, Gravity.START, true));
        head.addView(cell(c, "Klub", 0, Gravity.START, true), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(cell(c, "U", 30, Gravity.END, true));
        head.addView(cell(c, "GR", 44, Gravity.END, true));
        head.addView(cell(c, "B", 38, Gravity.END, true));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hl.topMargin = Ui.dp(c, 10);
        hl.bottomMargin = Ui.dp(c, 4);
        table.addView(head, hl);
        JSONArray rows = t == null ? null : t.optJSONArray("rows");
        for (int i = 0; rows != null && i < rows.length(); i++) {
            JSONObject r = rows.optJSONObject(i);
            LinearLayout line = row(c);
            boolean mine = highlight != 0 && r.optInt("teamId") == highlight;
            if (mine) line.setBackgroundDrawable(Ui.rounded(c, p.accentTint, 0, 6));
            line.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
            TextView pos = Ui.text(c, 14, Fonts.mono(), p.fg3);
            pos.setText(String.valueOf(r.optInt("pos")));
            line.addView(pos, new LinearLayout.LayoutParams(Ui.dp(c, 22), LinearLayout.LayoutParams.WRAP_CONTENT));
            TextView name = Ui.text(c, 15, mine ? Fonts.medium() : Fonts.regular(), mine ? p.accent : p.fg);
            name.setText(r.optString("club") + (r.optString("penalty").isEmpty() ? "" : " " + r.optString("penalty")));
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            line.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            line.addView(cell(c, String.valueOf(r.optInt("p")), 30, Gravity.END, false));
            int gd = r.optInt("gd");
            line.addView(cell(c, (gd > 0 ? "+" : "") + gd, 44, Gravity.END, false));
            TextView pts = cell(c, String.valueOf(r.optInt("pts")), 38, Gravity.END, false);
            pts.setTypeface(Fonts.monoMedium());
            pts.setTextColor(mine ? p.accent : p.fg);
            line.addView(pts);
            table.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        TextView src = Ui.text(c, 12, Fonts.regular(), p.fg2);
        src.setText("Izvor: NFSBiH (nfsbih.ba) · U utakmice · GR gol-razlika · B bodovi");
        src.setPadding(0, Ui.dp(c, 6), 0, 0);
        table.addView(src);
    }

    private LinearLayout row(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private TextView cell(Context c, String text, float widthDp, int gravity, boolean header) {
        TextView t = header ? Ui.label(c, p, text) : Ui.text(c, 14, Fonts.mono(), p.fg2);
        if (!header) t.setText(text);
        t.setGravity(gravity | Gravity.CENTER_VERTICAL);
        if (widthDp > 0) t.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, widthDp), LinearLayout.LayoutParams.WRAP_CONTENT));
        return t;
    }

    private TextView empty(Context c, String note) {
        TextView t = Ui.text(c, 16, Fonts.regular(), p.fg3);
        t.setText(note == null ? "Nema podataka" : note);
        t.setGravity(Gravity.CENTER);
        return t;
    }
}
