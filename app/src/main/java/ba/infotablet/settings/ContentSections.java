package ba.infotablet.settings;

import android.content.Context;
import android.graphics.drawable.StateListDrawable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import ba.infotablet.R;
import ba.infotablet.Settings;
import ba.infotablet.data.Cache;
import ba.infotablet.data.SourceState;
import ba.infotablet.sources.NewsParser;
import ba.infotablet.sources.NewsSource;
import ba.infotablet.sources.RatesSource;
import ba.infotablet.sources.FootballSource;
import ba.infotablet.sources.SportSource;
import ba.infotablet.sources.WaterSource;
import ba.infotablet.sync.BaseSource;
import ba.infotablet.sync.Scheduler;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Ui;
import ba.infotablet.useful.UsefulScreen;
import ba.infotablet.util.Clock;
import ba.infotablet.util.Fmt;

/** Postavke: what is shown (news, club, currencies, streets) and the data sources with their status. */
final class ContentSections {

    private static final String[][] CATEGORY_NAMES = {
            {"crna", "Crna hronika"}, {"sport", "Sport"}, {"ekonomija", "Ekonomija"}, {"svijet", "Svijet i regija"},
            {"kultura", "Kultura i zabava"}, {"tech", "Tehnologija"}};

    private ContentSections() {}

    // ---- Sadržaj ----

    static void content(final SettingsScreen s, LinearLayout out) {
        Context c = s.getContext();
        Palette p = s.p;
        final Settings st = Settings.of(c);

        TextView portals = Ui.label(c, p, "Vijesti: portali");
        portals.setPadding(0, Ui.dp(c, 8), 0, 0);
        out.addView(portals);
        for (NewsParser.Portal portal : NewsParser.Portal.values()) {
            final String id = NewsSource.idOf(portal);
            Sections.add(out, p, Controls.row(c, p, portal.label, null, Controls.toggle(c, p, st.sourceEnabled(id), on -> {
                st.setSourceEnabled(id, on);
                if (on) Scheduler.get(s.getContext()).refreshNow(id);
                s.changed();
            })));
        }
        TextView cats = Ui.label(c, p, "Vijesti: kategorije");
        cats.setPadding(0, Ui.dp(c, 18), 0, 0);
        out.addView(cats);
        out.addView(Controls.note(c, p, "Isključene kategorije se ne prikazuju ni na Početnoj ni u Vijestima.", p.fg2));
        for (String[] cat : CATEGORY_NAMES) {
            final String key = cat[0];
            Sections.add(out, p, Controls.row(c, p, cat[1], null, Controls.toggle(c, p, !st.newsCategoryHidden(key), on -> {
                st.setNewsCategoryHidden(key, !on);
                s.changed();
            })));
        }

        TextView other = Ui.label(c, p, "Ostalo");
        other.setPadding(0, Ui.dp(c, 18), 0, 0);
        out.addView(other);
        club(s, st, out);
        currencies(s, st, out);
        streets(s, st, out);
    }

    private static final String[] LEAGUE_CODES = {"bih", "PL", "BL1", "PD"};
    private static final String[] LEAGUE_LABELS = {"BiH", "Premier League", "Bundesliga", "La Liga"};

    /** The followed club: first the league (BiH or one of the three foreign ones), then the club from its table. */
    private static void club(final SettingsScreen s, final Settings st, LinearLayout out) {
        Context c = s.getContext();
        Palette p = s.p;
        final String league = st.sportLeague();
        int li = 0;
        for (int i = 0; i < LEAGUE_CODES.length; i++) if (LEAGUE_CODES[i].equals(league)) li = i;
        Sections.add(out, p, Controls.block(c, p, "Liga kluba",
                st.footballKey().isEmpty() ? "Strane lige trebaju ključ za football-data.org (podešava se preko adb, vidi README)." : null,
                Controls.choice(c, p, LEAGUE_LABELS, li, index -> {
                    st.setSportClub(LEAGUE_CODES[index], "");
                    Sections.refreshLater(s.getContext(), SportSource.ID, FootballSource.ID);
                    s.changed();
                })));

        final List<String[]> clubs = new ArrayList<String[]>(); // {teamId, name}
        clubs.add(new String[] {"", "Nijedan"});
        try {
            if ("bih".equals(league)) {
                SourceState state = Cache.get(c, SportSource.ID);
                if (state.hasData()) {
                    JSONArray rows = new JSONObject(state.payload).getJSONObject("table").getJSONArray("rows");
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject r = rows.getJSONObject(i);
                        clubs.add(new String[] {String.valueOf(r.optInt("teamId")), r.optString("club")});
                    }
                }
            } else {
                SourceState state = Cache.get(c, FootballSource.ID);
                if (state.hasData()) {
                    JSONArray rows = new JSONObject(state.payload).getJSONObject("leagues").getJSONObject(league).getJSONArray("table");
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject r = rows.getJSONObject(i);
                        clubs.add(new String[] {String.valueOf(r.optInt("teamId")), r.optString("name")});
                    }
                }
            }
        } catch (JSONException ignored) {
            // only "Nijedan" then
        }
        int cur = 0;
        for (int i = 0; i < clubs.size(); i++) {
            if (clubs.get(i)[0].equals(st.sportClub()) || clubs.get(i)[1].equalsIgnoreCase(st.sportClub())) cur = i;
        }
        final int ci = cur;
        String desc = clubs.size() > 1 ? "Klub za ekran Sport i rezultat uživo."
                : "bih".equals(league) ? "Lista klubova se pojavi kad se učita tabela Premijer lige BiH."
                : "Lista klubova se pojavi kad se učita tabela lige.";
        Sections.add(out, p, Controls.row(c, p, "Klub", desc, Controls.stepper(c, p, clubs.get(cur)[1], 170, true,
                () -> setClub(s, st, league, clubs.get((ci + clubs.size() - 1) % clubs.size())[0]),
                () -> setClub(s, st, league, clubs.get((ci + 1) % clubs.size())[0]))));
    }

    private static void setClub(SettingsScreen s, Settings st, String league, String teamId) {
        st.setSportClub(league, teamId);
        Sections.refreshLater(s.getContext(), "bih".equals(league) ? SportSource.ID : FootballSource.ID);
        s.changed();
    }

    private static void currencies(final SettingsScreen s, final Settings st, LinearLayout out) {
        Context c = s.getContext();
        Palette p = s.p;
        final List<String> chosen = st.currencies();
        JSONObject rates = null;
        try {
            SourceState state = Cache.get(c, RatesSource.ID);
            if (state.hasData()) rates = new JSONObject(state.payload).optJSONObject("rates");
        } catch (JSONException ignored) {
            rates = null;
        }
        LinearLayout grid = new LinearLayout(c);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout line = null;
        int n = 0;
        for (final String[] cur : UsefulScreen.CURRENCY_NAMES) {
            if (rates != null && !rates.has(cur[0])) continue;
            if (n % 5 == 0) {
                line = new LinearLayout(c);
                LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 42));
                if (n > 0) ll.topMargin = Ui.dp(c, 6);
                grid.addView(line, ll);
            }
            final boolean on = chosen.contains(cur[0]);
            TextView chip = Ui.text(c, 15, Fonts.monoMedium(), on ? p.accentFg : p.fg);
            chip.setText(cur[0]);
            chip.setGravity(Gravity.CENTER);
            chip.setBackgroundDrawable(Controls.toggleBg(c, p, on));
            chip.setClickable(true);
            chip.setContentDescription(cur[1]);
            chip.setOnClickListener(v -> {
                List<String> next = new ArrayList<String>();
                for (String[] k : UsefulScreen.CURRENCY_NAMES) {
                    boolean want = k[0].equals(cur[0]) ? !on : chosen.contains(k[0]);
                    if (want) next.add(k[0]);
                }
                st.putString("currencies", TextUtils.join(",", next));
                s.changed();
            });
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (n % 5 > 0) cl.leftMargin = Ui.dp(c, 6);
            line.addView(chip, cl);
            n++;
        }
        while (line != null && n % 5 != 0) { // keep the last row's chips the same width
            LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(0, 1, 1f);
            fl.leftMargin = Ui.dp(c, 6);
            line.addView(new View(c), fl);
            n++;
        }
        Sections.add(out, p, Controls.block(c, p, "Valute", "Euro je uvijek prikazan (fiksni kurs). Pločica na Početnoj pokazuje prve tri odabrane.", grid));
    }

    private static void streets(final SettingsScreen s, final Settings st, LinearLayout out) {
        Context c = s.getContext();
        Palette p = s.p;
        LinearLayout box = new LinearLayout(c);
        box.setGravity(Gravity.CENTER_VERTICAL);
        final EditText field = (EditText) LayoutInflater.from(c).inflate(R.layout.edit_text, box, false);
        field.setTextSize(17);
        field.setTypeface(Fonts.regular());
        field.setTextColor(p.fg);
        field.setHintTextColor(p.fg3);
        field.setHighlightColor(p.accentTint);
        field.setHint("Npr. Otoka, Kemala Kapetanovića");
        field.setFilters(new InputFilter[] {new InputFilter.LengthFilter(300)});
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, Ui.rounded(c, p.panel, p.accent, 8));
        bg.addState(new int[0], Ui.rounded(c, p.panel, p.line, 8));
        field.setBackgroundDrawable(bg);
        field.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0);
        field.setText(st.getString("water_places"));
        box.addView(field, new LinearLayout.LayoutParams(0, Ui.dp(c, 46), 1f));
        TextView save = Controls.button(c, p, "Sačuvaj", true, v -> {
            InputMethodManager imm = (InputMethodManager) s.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
            st.putString("water_places", field.getText().toString().trim().replaceAll("\\s+", " "));
            for (String id : new String[] {WaterSource.ID, "power", "heat", "gas"}) Scheduler.get(s.getContext()).refreshNow(id);
            s.changed();
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(c, 46));
        sl.leftMargin = Ui.dp(c, 10);
        box.addView(save, sl);
        Sections.add(out, p, Controls.block(c, p, "Ulice za obavještenja",
                "Naselje i ulice odvojene zarezom. Više naziva iste ulice: Kačanik mali|Mali Kačanik. Obavijesti za vodu, struju, grijanje i plin se prikažu kad se neko od njih spominje.", box));
    }

    // ---- Izvori podataka ----

    static void sources(final SettingsScreen s, LinearLayout out) {
        Context c = s.getContext();
        Palette p = s.p;
        final Settings st = Settings.of(c);
        final int[] factors = {1, 2, 4};
        int fi = st.refreshFactor() == 4 ? 2 : st.refreshFactor() == 2 ? 1 : 0;
        Sections.add(out, p, Controls.block(c, p, "Osvježavanje", "Rjeđe: svaki izvor upola rjeđe. Najrjeđe: četiri puta rjeđe. Gorivo i kursna lista imaju svoj raspored.",
                Controls.choice(c, p, new String[] {"Normalno", "Rjeđe", "Najrjeđe"}, fi, i -> {
                    st.putInt("refresh_factor", factors[i]);
                    s.changed();
                })));
        long now = System.currentTimeMillis();
        for (final BaseSource src : Scheduler.get(c).sources()) {
            boolean enabled = st.sourceEnabled(src.id());
            SourceState state = Cache.get(c, src.id());
            String status;
            int color;
            if (!src.active(c)) {
                status = "Nije u upotrebi";
                color = p.fg3;
            } else if (!enabled) {
                status = "Isključen";
                color = p.fg3;
            } else if (state.failing()) {
                status = "Greška od " + Fmt.when(now, state.firstFailAt) + ": " + shortError(state.error)
                        + (state.okAt > 0 ? " · zadnji uspjeh " + Fmt.when(now, state.okAt) : "");
                color = p.alert;
            } else if (state.okAt > 0) {
                status = "OK · zadnje osvježeno " + Fmt.when(now, state.okAt);
                color = p.ok;
            } else {
                status = "Još nije učitan";
                color = p.fg2;
            }
            LinearLayout controls = new LinearLayout(c);
            controls.setGravity(Gravity.CENTER_VERTICAL);
            if (enabled && src.active(c)) {
                TextView now1 = Controls.button(c, p, "Osvježi", false, v -> {
                    Scheduler.get(s.getContext()).refreshNow(src.id());
                    ((TextView) v).setText("…");
                    v.setEnabled(false);
                });
                controls.addView(now1);
                View gap = new View(c);
                controls.addView(gap, new LinearLayout.LayoutParams(Ui.dp(c, 12), 1));
            }
            controls.addView(Controls.toggle(c, p, enabled, on -> {
                st.setSourceEnabled(src.id(), on);
                if (on) Scheduler.get(s.getContext()).refreshNow(src.id());
                s.changed();
            }));
            LinearLayout row = Controls.row(c, p, src.name(), status, controls);
            // Colour the status line by state.
            TextView d = (TextView) ((LinearLayout) row.getChildAt(0)).getChildAt(1);
            d.setTextColor(color);
            Sections.add(out, p, row);
        }
    }

    /** Changes when any source's status changes, so the list redraws by itself. */
    static String sourcesKey(Context c) {
        StringBuilder b = new StringBuilder();
        for (BaseSource src : Scheduler.get(c).sources()) {
            SourceState st = Cache.get(c, src.id());
            b.append(st.okAt).append(':').append(st.failCount).append('|');
        }
        b.append(Clock.now() / 60_000L);
        return b.toString();
    }

    private static String shortError(String e) {
        if (e == null) return "nepoznato";
        if (e.contains("UnknownHost") || e.contains("ConnectException") || e.contains("SocketTimeout")) return "nema veze sa serverom";
        if (e.contains("HTTP 4") || e.contains("HTTP 5") || e.contains(" 40") || e.contains(" 50")) return "server je odbio zahtjev";
        if (e.contains("JSON") || e.contains("Parse") || e.contains("Selector")) return "stranica je promijenila izgled";
        return e.length() > 60 ? e.substring(0, 60) + "…" : e;
    }
}
