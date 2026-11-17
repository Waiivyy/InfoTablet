package ba.infotablet.settings;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import ba.infotablet.Cities;
import ba.infotablet.Settings;
import ba.infotablet.sources.AirCamsSource;
import ba.infotablet.sources.SecondCitySource;
import ba.infotablet.sources.WarningsSource;
import ba.infotablet.sources.WeatherSource;
import ba.infotablet.sync.Scheduler;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Rail;
import ba.infotablet.ui.Theme;
import ba.infotablet.ui.Ui;
import ba.infotablet.util.Clock;
import ba.infotablet.util.Fmt;

/** The look sections of Postavke (Izgled, Početni ekran, Noć, Lokacija) and the dispatch to the others. */
final class Sections {

    static final String[] TILE_NAMES = {"Podsjetnici", "Gorivo", "Vijesti", "Drugi grad", "Kursna lista", "Zrak"};

    private static android.os.Handler later; // created on first use (not available in JVM unit tests)
    private static Runnable pending;

    private Sections() {}

    /**
     * Refreshes sources a few seconds after the last change, so stepping through a list of cities
     * sends one request instead of one per tap.
     */
    static void refreshLater(Context c, final String... ids) {
        final Context app = c.getApplicationContext();
        if (later == null) later = new android.os.Handler(android.os.Looper.getMainLooper());
        if (pending != null) later.removeCallbacks(pending);
        pending = () -> {
            for (String id : ids) Scheduler.get(app).refreshNow(id);
            pending = null;
        };
        later.postDelayed(pending, 4000);
    }

    static void build(SettingsScreen s, int index, LinearLayout out) {
        switch (index) {
            case 0: look(s, out); break;
            case 1: home(s, out); break;
            case 2: night(s, out); break;
            case 3: location(s, out); break;
            case 4: ContentSections.content(s, out); break;
            case 5: ContentSections.sources(s, out); break;
            case 6: SystemSections.wifi(s, out); break;
            case 7: SystemSections.data(s, out); break;
            case 8: SystemSections.updates(s, out); break;
            default: SystemSections.about(s, out); break;
        }
    }

    /** What a section shows that changes on its own (source status, Wi-Fi address). */
    static String liveKey(SettingsScreen s, int index) {
        if (index == 5) return ContentSections.sourcesKey(s.getContext());
        if (index == 6) return SystemSections.wifiKey(s.getContext());
        return "";
    }

    static void add(LinearLayout out, Palette p, View row) {
        if (out.getChildCount() > 1) out.addView(Controls.divider(out.getContext(), p));
        out.addView(row);
    }

    // ---- Izgled ----

    private static void look(final SettingsScreen s, LinearLayout out) {
        Context c = s.getContext();
        Palette p = s.p;
        final Settings st = Settings.of(c);
        long[] sun = Theme.sunTimes(c, Clock.now());
        String sunText = sun[0] == 0 ? "" : " Danas: svijetla od " + Fmt.hhmm(sun[0]) + ", tamna od " + Fmt.hhmm(sun[1]) + ".";

        final String[] themes = {"auto", "light", "dark"};
        add(out, p, Controls.block(c, p, "Tema", "Automatski: svijetla od izlaska sunca, tamna od zalaska." + sunText,
                Controls.choice(c, p, new String[] {"Automatski", "Svijetla", "Tamna"}, Arrays.asList(themes).indexOf(st.theme()), i -> {
                    st.putString("theme", themes[i]);
                    s.changed();
                })));

        LinearLayout swatches = new LinearLayout(c);
        for (int i = 0; i < Palette.ACCENTS.length; i++) {
            final String key = (String) Palette.ACCENTS[i][0];
            boolean on = key.equals(st.accent());
            LinearLayout b = new LinearLayout(c);
            b.setGravity(Gravity.CENTER);
            b.setBackgroundDrawable(Ui.rounded(c, on ? p.accentTint : p.panel, on ? p.accent : p.line, 8));
            b.setClickable(true);
            b.setOnClickListener(v -> {
                st.putString("accent", key);
                s.changed();
            });
            View dot = new View(c);
            dot.setBackgroundDrawable(Ui.rounded(c, Palette.accentOf(key, p.dark), 0, 9));
            b.addView(dot, new LinearLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18)));
            TextView name = Ui.text(c, 15, Fonts.medium(), p.fg);
            name.setText((String) Palette.ACCENTS[i][1]);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            nl.leftMargin = Ui.dp(c, 8);
            b.addView(name, nl);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, Ui.dp(c, 44), 1f);
            if (i > 0) bl.leftMargin = Ui.dp(c, 6);
            swatches.addView(b, bl);
        }
        add(out, p, Controls.block(c, p, "Boja naglaska", "Za odabranu karticu, dugmad i oznake.", swatches));

        add(out, p, Controls.row(c, p, "Veličina slova", "Veća slova za tekst; sat i brojevi ostaju isti.",
                fixed(c, Controls.choice(c, p, new String[] {"Normalna", "Veća"}, st.largeText() ? 1 : 0, i -> {
                    st.putBoolean("large_text", i == 1);
                    s.changed();
                }), 260)));
        add(out, p, Controls.row(c, p, "Gustoća", "Zbijeno: manji razmaci između kartica.",
                fixed(c, Controls.choice(c, p, new String[] {"Normalna", "Zbijena"}, st.compact() ? 1 : 0, i -> {
                    st.putBoolean("compact", i == 1);
                    s.changed();
                }), 260)));
        final String[] bgs = {"standard", "contrast", "grid"};
        add(out, p, Controls.block(c, p, "Pozadina", "Kontrastna: tamnija pozadina iza kartica. Mreža: tanke linije kao na milimetarskom papiru.",
                Controls.choice(c, p, new String[] {"Standardna", "Kontrastna", "Mreža"}, Arrays.asList(bgs).indexOf(st.background()), i -> {
                    st.putString("background", bgs[i]);
                    s.changed();
                })));
        add(out, p, Controls.row(c, p, "Veliki sat", null,
                fixed(c, Controls.choice(c, p, new String[] {"24 sata", "12 sati"}, st.clock24h() ? 0 : 1, i -> {
                    st.putBoolean("clock_24h", i == 0);
                    s.changed();
                }), 260)));
        add(out, p, Controls.row(c, p, "Sekunde na satu", null, Controls.toggle(c, p, st.clockSeconds(), on -> {
            st.putBoolean("clock_seconds", on);
            s.changed();
        })));
    }

    private static View fixed(Context c, View v, float widthDp) {
        v.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, widthDp), LinearLayout.LayoutParams.WRAP_CONTENT));
        return v;
    }

    // ---- Početni ekran ----

    private static void home(final SettingsScreen s, LinearLayout out) {
        Context c = s.getContext();
        Palette p = s.p;
        final Settings st = Settings.of(c);
        final List<String> keys = Arrays.asList(Settings.TILES);
        String[] slotNames = {"Lijeva pločica", "Srednja pločica", "Desna pločica"};
        for (int slot = 0; slot < 3; slot++) {
            final int sl = slot;
            final String[] tiles = st.tiles();
            int cur = keys.indexOf(tiles[slot]);
            add(out, p, Controls.row(c, p, slotNames[slot], slot == 0 ? "Donji red na Početnoj. Sat, vrijeme i upozorenja su uvijek gore." : null,
                    Controls.stepper(c, p, TILE_NAMES[Math.max(0, cur)], 150, true,
                            () -> {
                                st.setTile(sl, nextFree(tiles, sl, -1));
                                s.changed();
                            },
                            () -> {
                                st.setTile(sl, nextFree(tiles, sl, 1));
                                s.changed();
                            })));
        }
        TextView tabs = Ui.label(c, p, "Kartice lijevo");
        tabs.setPadding(0, Ui.dp(c, 18), 0, 0);
        out.addView(tabs);
        out.addView(Controls.note(c, p, "Početna i Postavke su uvijek vidljive.", p.fg2));
        for (int t = Settings.FIRST_HIDEABLE_TAB; t <= Settings.LAST_HIDEABLE_TAB; t++) {
            final int tab = t;
            add(out, p, Controls.row(c, p, Rail.LABELS[t], null, Controls.toggle(c, p, st.tabVisible(t), on -> {
                st.setTabVisible(tab, on);
                s.changed();
            })));
        }
    }

    /** The next tile in the list (direction ±1) that no other slot uses. */
    static String nextFree(String[] tiles, int slot, int dir) {
        List<String> keys = Arrays.asList(Settings.TILES);
        int i = Math.max(0, keys.indexOf(tiles[slot]));
        for (int step = 0; step < keys.size(); step++) {
            i = (i + dir + keys.size()) % keys.size();
            String k = keys.get(i);
            boolean used = false;
            for (int o = 0; o < tiles.length; o++) if (o != slot && tiles[o].equals(k)) used = true;
            if (!used) return k;
        }
        return tiles[slot];
    }

    // ---- Noć ----

    private static void night(final SettingsScreen s, LinearLayout out) {
        Context c = s.getContext();
        Palette p = s.p;
        final Settings st = Settings.of(c);
        add(out, p, Controls.row(c, p, "Zatamni ekran noću", "Ekran se nikad ne gasi, da se podsjetnici vide. Dodir noću ga posvijetli na minutu.",
                Controls.toggle(c, p, st.nightDim(), on -> {
                    st.putBoolean("night_dim", on);
                    s.changed();
                })));
        if (!st.nightDim()) return;
        final int level = st.nightBrightness();
        LinearLayout bright = new LinearLayout(c);
        bright.setGravity(Gravity.CENTER_VERTICAL);
        bright.addView(Controls.stepper(c, p, level + " %", 70, false,
                () -> {
                    st.putInt("night_brightness", Math.max(5, level - 5));
                    s.changed();
                    s.host.previewNight();
                },
                () -> {
                    st.putInt("night_brightness", Math.min(60, level + 5));
                    s.changed();
                    s.host.previewNight();
                }));
        TextView test = Controls.button(c, p, "Probaj", false, v -> s.host.previewNight());
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(c, 44));
        tl.leftMargin = Ui.dp(c, 10);
        bright.addView(test, tl);
        add(out, p, Controls.row(c, p, "Svjetlina noću", "Promjena se odmah pokaže na nekoliko sekundi.", bright));

        long[] sun = Theme.sunTimes(c, Clock.now());
        String sunText = sun[0] == 0 ? "" : "Danas od " + Fmt.hhmm(sun[1]) + " do " + Fmt.hhmm(sun[0]) + ".";
        add(out, p, Controls.block(c, p, "Kada", st.nightBySun() ? sunText : "Vrijeme po izboru.",
                Controls.choice(c, p, new String[] {"Od zalaska do izlaska sunca", "Ručno"}, st.nightBySun() ? 0 : 1, i -> {
                    st.putBoolean("night_by_sun", i == 0);
                    s.changed();
                })));
        if (!st.nightBySun()) {
            add(out, p, Controls.row(c, p, "Početak", null, timeStepper(s, st, "night_start", st.nightStart())));
            add(out, p, Controls.row(c, p, "Kraj", null, timeStepper(s, st, "night_end", st.nightEnd())));
        }
    }

    private static View timeStepper(final SettingsScreen s, final Settings st, final String key, final int minutes) {
        String text = String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
        return Controls.stepper(s.getContext(), s.p, text, 80, false,
                () -> {
                    st.putInt(key, (minutes + 24 * 60 - 30) % (24 * 60));
                    s.changed();
                },
                () -> {
                    st.putInt(key, (minutes + 30) % (24 * 60));
                    s.changed();
                });
    }

    // ---- Lokacija ----

    private static void location(final SettingsScreen s, LinearLayout out) {
        Context c = s.getContext();
        Palette p = s.p;
        final Settings st = Settings.of(c);
        final Cities.City[] home = Cities.HOME;
        int cur = 0;
        for (int i = 0; i < home.length; i++) if (home[i].name.equals(st.locationName())) cur = i;
        final int ci = cur;
        add(out, p, Controls.row(c, p, "Grad", "Vremenska prognoza i upozorenja. Gorivo, zrak, voda i smog su uvijek za Sarajevo.",
                Controls.stepper(c, p, home[cur].name, 150, true,
                        () -> setCity(s, st, home[(ci + home.length - 1) % home.length].name),
                        () -> setCity(s, st, home[(ci + 1) % home.length].name))));

        final Cities.City[] other = Cities.OTHER;
        Cities.City c2 = st.city2();
        int oi = -1;
        for (int i = 0; c2 != null && i < other.length; i++) if (other[i].name.equals(c2.name)) oi = i;
        final int o = oi; // -1 means none
        add(out, p, Controls.row(c, p, "Drugi grad", "Pločica s vremenom u drugom gradu. Dodajte je u Početni ekran.",
                Controls.stepper(c, p, o < 0 ? "Nijedan" : other[o].name, 150, true,
                        () -> setCity2(s, st, o <= 0 ? (o == 0 ? "" : other[other.length - 1].name) : other[o - 1].name),
                        () -> setCity2(s, st, o >= other.length - 1 ? "" : other[o + 1].name))));
    }

    private static void setCity(SettingsScreen s, Settings st, String name) {
        st.putString("city", name);
        Theme.forgetSun();
        refreshLater(s.getContext(), WeatherSource.ID, WarningsSource.ID, AirCamsSource.ID);
        s.changed();
    }

    private static void setCity2(SettingsScreen s, Settings st, String name) {
        st.putString("city2", name);
        if (!name.isEmpty()) refreshLater(s.getContext(), SecondCitySource.ID);
        s.changed();
    }
}
