package ba.infotablet.sport;

import android.content.Context;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import ba.infotablet.Settings;
import ba.infotablet.data.Cache;
import ba.infotablet.data.SourceState;
import ba.infotablet.sources.FootballParser;
import ba.infotablet.sources.FootballSource;
import ba.infotablet.sources.LiveSource;
import ba.infotablet.sources.SportSource;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Freshness;
import ba.infotablet.ui.NarrowSpan;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Refreshable;
import ba.infotablet.ui.Ui;
import ba.infotablet.util.Fmt;

/**
 * Sport: the followed club (next match, live score, last result) on top left; below it the national
 * team (BiH) or this week's matches of the chosen league; on the right the league table with tabs
 * for BiH, Premier League, Bundesliga and La Liga. The club and its league are set in Postavke.
 */
public final class SportScreen extends FrameLayout implements Refreshable {

    private static final String[] TABS = {"bih", "PL", "BL1", "PD"};
    private static final String[] TAB_LABELS = {"BiH", "Premier", "Bundesliga", "La Liga"};

    private final Palette p;
    private final LinearLayout club, second, table;
    private final long bihStaleMs = new SportSource().staleAfterMs(), footballStaleMs = new FootballSource().staleAfterMs();
    /** The table tab; follows the club's league until a tab is tapped. */
    private String tab;
    private String shown = "";

    public SportScreen(Context c, Palette p) {
        super(c);
        this.p = p;
        int pad = Ui.pagePad(c), gap = Ui.gap(c);
        LinearLayout root = new LinearLayout(c);
        root.setPadding(pad, pad, pad, pad);
        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        club = panel(c);
        second = panel(c);
        left.addView(club, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        sl.topMargin = gap;
        left.addView(second, sl);
        root.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        table = panel(c);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(Ui.dp(c, 360), LinearLayout.LayoutParams.MATCH_PARENT);
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
        Settings s = Settings.of(c);
        String league = s.sportLeague();
        if (tab == null) tab = league;
        SourceState bih = Cache.get(c, SportSource.ID), fd = Cache.get(c, FootballSource.ID), live = Cache.get(c, LiveSource.ID);
        String key = tab + "/" + league + "/" + s.sportClub() + "/" + okAt(bih) + "/" + okAt(fd) + "/" + okAt(live) + "/" + (now / 60_000L);
        if (key.equals(shown)) return;
        shown = key;
        club.removeAllViews();
        second.removeAllViews();
        table.removeAllViews();

        JSONObject bihData = parse(bih, bihStaleMs, now), fdData = parse(fd, footballStaleMs, now);
        boolean foreign = !"bih".equals(league);
        JSONObject cl = foreign ? (fdData == null ? null : fdData.optJSONObject("club")) : (bihData == null ? null : bihData.optJSONObject("club"));
        bindClub(c, cl, foreign, s, liveMatch(live, cl, now), now);

        if ("bih".equals(tab)) {
            JSONObject nat = bihData == null ? null : bihData.optJSONObject("national");
            JSONObject next = nat == null ? null : nat.optJSONObject("next");
            String natTitle = "Reprezentacija BiH" + (next != null && !next.optString("competition").isEmpty() ? " · " + next.optString("competition") : "");
            if (bihData == null) {
                second.addView(Ui.label(c, p, "Reprezentacija BiH"));
                second.addView(empty(c, Freshness.of(bih, bihStaleMs, now).note), weightRow());
            } else {
                matchCard(c, second, natTitle, next, nat == null ? null : nat.optJSONObject("last"), null, now);
            }
            bindBihTable(c, bihData, Freshness.of(bih, bihStaleMs, now),
                    !foreign && cl != null ? cl.optInt("teamId") : 0);
        } else {
            JSONObject lg = fdData == null || fdData.optJSONObject("leagues") == null ? null : fdData.optJSONObject("leagues").optJSONObject(tab);
            int mine = foreign && tab.equals(league) && cl != null ? cl.optInt("teamId") : 0;
            bindRound(c, lg, s, mine, now);
            bindLeagueTable(c, lg, s, Freshness.of(fd, footballStaleMs, now), mine);
        }
    }

    private static long okAt(SourceState st) {
        return st == null ? 0 : st.okAt;
    }

    private static JSONObject parse(SourceState st, long staleMs, long now) {
        Freshness f = Freshness.of(st, staleMs, now);
        if (!f.hasData) return null;
        try {
            return new JSONObject(st.payload);
        } catch (JSONException e) {
            return null;
        }
    }

    /** The live payload when it is fresh and about the club's next match. */
    private static JSONObject liveMatch(SourceState live, JSONObject club, long now) {
        if (live == null || !live.hasData() || club == null || now - live.okAt > 10 * 60_000L) return null;
        JSONObject next = club.optJSONObject("next");
        if (next == null) return null;
        try {
            JSONObject m = new JSONObject(live.payload);
            boolean same = m.optLong("id") == next.optLong("id")
                    || Math.abs(m.optLong("utc") - next.optLong("utc")) < 30 * 60_000L;   // OpenLigaDB has its own ids
            return same ? m : null;
        } catch (JSONException e) {
            return null;
        }
    }

    // ---- the followed club ----

    private void bindClub(Context c, JSONObject cl, boolean foreign, Settings s, JSONObject live, long now) {
        if (cl == null) {
            club.addView(Ui.label(c, p, "Moj klub"));
            String note = s.sportClub().isEmpty() ? "Klub se bira u Postavkama, Sadržaj"
                    : foreign && s.footballKey().isEmpty() ? "Strane lige još nisu podešene" : "Nema podataka";
            club.addView(empty(c, note), weightRow());
            return;
        }
        String title = cl.optString("name") + " · " + cl.optInt("pos") + ". mjesto · " + Fmt.plural(cl.optInt("pts"), "bod", "boda", "bodova")
                + (foreign ? " · " + FootballParser.leagueName(cl.optString("league")) : "");
        matchCard(c, club, title, cl.optJSONObject("next"), cl.optJSONObject("last"), live, now);
    }

    /** Next (or live) match in the middle, the last result at the bottom. Handles both payload shapes. */
    private void matchCard(Context c, LinearLayout panel, String title, JSONObject next, JSONObject last, JSONObject live, long now) {
        TextView label = Ui.label(c, p, title);
        label.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(label);
        if (next == null) {
            panel.addView(empty(c, "Nema zakazane utakmice"), weightRow());
        } else {
            long k = next.has("utc") ? next.optLong("utc") : next.has("kickoff") ? next.optLong("kickoff") : next.optLong("date");
            boolean timed = next.has("utc") || next.has("kickoff");
            String status = live != null ? live.optString("status") : next.optString("status");
            boolean isLive = "live".equals(status) || "paused".equals(status);
            boolean guessLive = !isLive && live == null && !"finished".equals(status) && now >= k && now < k + 2 * 3600_000L;
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(team(c, next.optString("home"), Gravity.END), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout mid = new LinearLayout(c);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView day = Ui.text(c, 13, Fonts.medium(), isLive ? p.alert : guessLive ? p.accent : p.fg2);
            TextView big = Ui.text(c, 32, Fonts.mono(), p.fg);
            if (isLive && live != null) {
                day.setText("paused".equals(status) ? "POLUVRIJEME" : "UŽIVO");
                big.setText(NarrowSpan.numeric(score(live.optInt("hs"), live.optInt("as"))));
                big.setTypeface(Fonts.monoMedium());
            } else if ("finished".equals(status) && live != null) {
                day.setText("KRAJ");
                big.setText(NarrowSpan.numeric(score(live.optInt("hs"), live.optInt("as"))));
            } else {
                day.setText(guessLive ? "U TOKU" : Fmt.relDay(now, k));
                big.setText(NarrowSpan.numeric(timed ? Fmt.hhmm(k) : "--:--"));
            }
            mid.addView(day);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bl.topMargin = Ui.dp(c, 2);
            mid.addView(big, bl);
            row.addView(mid, new LinearLayout.LayoutParams(Ui.dp(c, 130), LinearLayout.LayoutParams.WRAP_CONTENT));
            row.addView(team(c, next.optString("away"), Gravity.START), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            panel.addView(new View(c), new LinearLayout.LayoutParams(1, 0, 1f));
            panel.addView(row);
            String note = next.optString("venue");
            if (live != null && "football-data".equals(live.optString("source")) && isLive) note = "Rezultat može kasniti nekoliko minuta";
            String comp = next.optString("comp");
            if (note.isEmpty() && !comp.isEmpty() && !isLeague(comp)) note = competitionName(comp);
            if (!note.isEmpty()) {
                TextView v = Ui.text(c, 14, Fonts.regular(), p.fg2);
                v.setText(note);
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
            r.addView(Ui.label(c, p, "Zadnja"), new LinearLayout.LayoutParams(Ui.dp(c, 64), LinearLayout.LayoutParams.WRAP_CONTENT));
            String sc = last.has("hs") ? score(last.optInt("hs"), last.optInt("as")) : last.has("ft") ? last.optString("ft") : last.optString("score");
            long when = last.has("utc") ? last.optLong("utc") : last.has("kickoff") ? last.optLong("kickoff") : last.optLong("date");
            TextView t = Ui.text(c, 16, Fonts.regular(), p.fg);
            t.setText(last.optString("home") + "  " + sc + "  " + last.optString("away"));
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            r.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView d = Ui.text(c, 13, Fonts.mono(), p.fg2);
            d.setText(NarrowSpan.numeric(Fmt.dayMonth(when)));
            r.addView(d);
            panel.addView(r);
        }
    }

    private static boolean isLeague(String code) {
        for (String l : FootballParser.LEAGUES) if (l.equals(code)) return true;
        return false;
    }

    private static String competitionName(String code) {
        switch (code) {
            case "CL": return "Liga prvaka";
            case "EL": return "Evropska liga";
            case "EC": return "Evropsko prvenstvo";
            case "WC": return "Svjetsko prvenstvo";
            default: return "";
        }
    }

    private static String score(int h, int a) {
        return (h < 0 ? "-" : String.valueOf(h)) + ":" + (a < 0 ? "-" : String.valueOf(a));
    }

    private TextView team(Context c, String name, int gravity) {
        TextView t = Ui.text(c, 20, Fonts.medium(), p.fg);
        t.setText(name);
        t.setGravity(gravity | Gravity.CENTER_VERTICAL);
        t.setMaxLines(2);
        t.setLineSpacing(Ui.dp(c, 2), 1f);
        return t;
    }

    // ---- this week's matches of a foreign league ----

    private void bindRound(Context c, JSONObject lg, Settings s, int mine, long now) {
        second.addView(Ui.label(c, p, lg == null ? FootballParser.leagueName(tab) : lg.optString("name") + " · ova sedmica"));
        JSONArray matches = lg == null ? null : lg.optJSONArray("matches");
        if (matches == null || matches.length() == 0) {
            second.addView(empty(c, lg == null ? (s.footballKey().isEmpty() ? "Potreban je ključ za football-data.org" : "Nema podataka")
                    : "Ove sedmice nema utakmica"), weightRow());
            return;
        }
        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        String day = null;
        int firstOpen = -1, index = 0;
        for (int i = 0; i < matches.length(); i++) {
            JSONObject m = matches.optJSONObject(i);
            if (m == null) continue;
            long t = m.optLong("utc");
            String d = Fmt.relDay(now, t);
            if (!d.equals(day)) {
                day = d;
                TextView h = Ui.label(c, p, d);
                h.setPadding(0, Ui.dp(c, index == 0 ? 6 : 10), 0, Ui.dp(c, 2));
                list.addView(h);
                index++;
            }
            boolean ours = mine != 0 && (m.optInt("homeId") == mine || m.optInt("awayId") == mine);
            if (firstOpen < 0 && !"finished".equals(m.optString("status"))) firstOpen = index;
            list.addView(roundRow(c, m, ours));
            index++;
        }
        second.addView(scroll, weightRow());
        final int target = firstOpen;
        if (target > 2) scroll.post(() -> {
            View v = list.getChildAt(Math.max(0, target - 1));
            if (v != null) scroll.scrollTo(0, v.getTop());
        });
        TextView src = Ui.text(c, 12, Fonts.regular(), p.fg2);
        src.setText("Football data provided by the Football-Data.org API");
        src.setPadding(0, Ui.dp(c, 6), 0, 0);
        second.addView(src);
    }

    private View roundRow(Context c, JSONObject m, boolean ours) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 4));
        if (ours) r.setBackgroundDrawable(Ui.rounded(c, p.accentTint, 0, 6));
        String st = m.optString("status");
        boolean live = "live".equals(st) || "paused".equals(st);
        TextView home = Ui.text(c, 15, ours ? Fonts.medium() : Fonts.regular(), p.fg);
        home.setText(m.optString("home"));
        home.setGravity(Gravity.END);
        home.setSingleLine(true);
        home.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(home, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView mid = Ui.text(c, 15, Fonts.monoMedium(), live ? p.alert : "finished".equals(st) ? p.fg : p.fg2);
        mid.setGravity(Gravity.CENTER);
        if ("finished".equals(st) || live) mid.setText(NarrowSpan.numeric(score(m.optInt("hs"), m.optInt("as"))));
        else if ("postponed".equals(st)) mid.setText("odg.");
        else mid.setText(NarrowSpan.numeric(Fmt.hhmm(m.optLong("utc"))));
        r.addView(mid, new LinearLayout.LayoutParams(Ui.dp(c, 66), LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView away = Ui.text(c, 15, ours ? Fonts.medium() : Fonts.regular(), p.fg);
        away.setText(m.optString("away"));
        away.setSingleLine(true);
        away.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(away, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return r;
    }

    // ---- tables ----

    private void tabs(Context c) {
        LinearLayout row = new LinearLayout(c);
        for (int i = 0; i < TABS.length; i++) {
            final String code = TABS[i];
            boolean on = code.equals(tab);
            TextView b = Ui.text(c, 13.5f, Fonts.medium(), on ? p.accentFg : p.fg);
            b.setText(TAB_LABELS[i]);
            b.setGravity(Gravity.CENTER);
            b.setSingleLine(true);
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[] {android.R.attr.state_pressed}, Ui.rounded(c, on ? p.accent : p.soft, on ? 0 : p.line, 7));
            bg.addState(new int[0], Ui.rounded(c, on ? p.accent : p.panel, on ? 0 : p.line, 7));
            b.setBackgroundDrawable(bg);
            b.setClickable(true);
            b.setOnClickListener(v -> {
                tab = code;
                shown = "";
                refresh(ba.infotablet.util.Clock.now());
            });
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, Ui.dp(c, 34), code.equals("bih") ? 0.7f : code.equals("BL1") ? 1.25f : 1f);
            if (i > 0) bl.leftMargin = Ui.dp(c, 5);
            row.addView(b, bl);
        }
        table.addView(row);
    }

    private void tableHead(Context c, String title) {
        TextView t = Ui.label(c, p, title);
        t.setPadding(0, Ui.dp(c, 10), 0, 0);
        table.addView(t);
        LinearLayout head = row(c);
        head.addView(cell(c, "#", 26, Gravity.START, true));
        head.addView(cell(c, "Klub", 0, Gravity.START, true), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(cell(c, "U", 30, Gravity.END, true));
        head.addView(cell(c, "GR", 44, Gravity.END, true));
        head.addView(cell(c, "B", 38, Gravity.END, true));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hl.topMargin = Ui.dp(c, 6);
        hl.bottomMargin = Ui.dp(c, 2);
        table.addView(head, hl);
    }

    private void bindBihTable(Context c, JSONObject data, Freshness f, int highlight) {
        tabs(c);
        JSONObject t = data == null ? null : data.optJSONObject("table");
        tableHead(c, t == null ? "WWIN liga BiH" : t.optString("title"));
        JSONArray rows = t == null ? null : t.optJSONArray("rows");
        if (rows == null) {
            table.addView(empty(c, f.note), weightRow());
            return;
        }
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject r = rows.optJSONObject(i);
            list.addView(tableRow(c, r.optInt("pos"), r.optString("club") + (r.optString("penalty").isEmpty() ? "" : " " + r.optString("penalty")),
                    r.optInt("p"), r.optInt("gd"), r.optInt("pts"), highlight != 0 && r.optInt("teamId") == highlight),
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 36)));
        }
        scrollTable(c, list, rows.length() > 10);
        footer(c, "Izvor: NFSBiH (nfsbih.ba) · U utakmice · GR gol-razlika · B bodovi", f);
    }

    private void bindLeagueTable(Context c, JSONObject lg, Settings s, Freshness f, int highlight) {
        tabs(c);
        tableHead(c, lg == null ? FootballParser.leagueName(tab) : lg.optString("name") + (lg.optInt("matchday") > 0 ? " · " + lg.optInt("matchday") + ". kolo" : ""));
        JSONArray rows = lg == null ? null : lg.optJSONArray("table");
        if (rows == null) {
            table.addView(empty(c, s.footballKey().isEmpty() ? "Potreban je ključ za football-data.org" : f.note), weightRow());
            return;
        }
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        int mineAt = -1;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject r = rows.optJSONObject(i);
            boolean mine = highlight != 0 && r.optInt("teamId") == highlight;
            if (mine) mineAt = i;
            list.addView(tableRow(c, r.optInt("pos"), r.optString("name"), r.optInt("played"), r.optInt("gd"), r.optInt("pts"), mine),
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 32)));
        }
        ScrollView sv = scrollTable(c, list, true);
        final int at = mineAt;
        if (at > 6) sv.post(() -> sv.scrollTo(0, Math.max(0, list.getChildAt(at).getTop() - Ui.dp(c, 32) * 4)));
        footer(c, "Football data provided by the Football-Data.org API · U utakmice · GR gol-razlika · B bodovi", f);
    }

    private ScrollView scrollTable(Context c, LinearLayout list, boolean scroll) {
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(scroll);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(list);
        table.addView(sv, weightRow());
        return sv;
    }

    private void footer(Context c, String text, Freshness f) {
        TextView src = Ui.text(c, 12, Fonts.regular(), f.stale ? p.warn : p.fg2);
        src.setText(f.stale ? f.note : text);
        src.setPadding(0, Ui.dp(c, 6), 0, 0);
        table.addView(src);
    }

    private View tableRow(Context c, int position, String club, int played, int gd, int points, boolean mine) {
        LinearLayout line = row(c);
        if (mine) line.setBackgroundDrawable(Ui.rounded(c, p.accentTint, 0, 6));
        line.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
        TextView pos = Ui.text(c, 14, Fonts.mono(), p.fg3);
        pos.setText(String.valueOf(position));
        line.addView(pos, new LinearLayout.LayoutParams(Ui.dp(c, 22), LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView name = Ui.text(c, 15, mine ? Fonts.medium() : Fonts.regular(), mine ? p.accent : p.fg);
        name.setText(club);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        line.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        line.addView(cell(c, String.valueOf(played), 30, Gravity.END, false));
        line.addView(cell(c, (gd > 0 ? "+" : "") + gd, 44, Gravity.END, false));
        TextView pts = cell(c, String.valueOf(points), 38, Gravity.END, false);
        pts.setTypeface(Fonts.monoMedium());
        pts.setTextColor(mine ? p.accent : p.fg);
        line.addView(pts);
        return line;
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

    private static LinearLayout.LayoutParams weightRow() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
    }

    private TextView empty(Context c, String note) {
        TextView t = Ui.text(c, 16, Fonts.regular(), p.fg3);
        t.setText(note == null ? "Nema podataka" : note);
        t.setGravity(Gravity.CENTER);
        return t;
    }
}
