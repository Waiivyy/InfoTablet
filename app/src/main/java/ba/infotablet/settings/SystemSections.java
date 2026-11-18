package ba.infotablet.settings;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;


import java.io.File;
import java.util.List;

import ba.infotablet.BuildConfig;
import ba.infotablet.Settings;
import ba.infotablet.data.Backup;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.NarrowSpan;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.QrView;
import ba.infotablet.ui.Ui;
import ba.infotablet.update.UpdateState;
import ba.infotablet.update.Updater;
import ba.infotablet.util.Clock;
import ba.infotablet.util.Fmt;
import ba.infotablet.web.WebServer;

/** Postavke: the Wi-Fi page, PIN and backups, updates, and O aplikaciji. */
final class SystemSections {

    /** Data sources and the credit each one asks for (see docs/sources.md). */
    private static final String[][] SOURCES = {
            {"Vremenska prognoza", "Weather data by Open-Meteo.com (CC BY 4.0)"},
            {"Prognoza zraka i polena", "Copernicus Atmosphere Monitoring Service (CAMS), preko Open-Meteo (CC BY 4.0)"},
            {"Mjerenja zraka", "FHMZ BiH, zrak.fhmzbih.gov.ba (nevalidirani podaci u realnom vremenu)"},
            {"Meteorološka upozorenja", "MeteoAlarm (EUMETNET), izdaju FHMZ BiH i RHMZ RS (uslovi kao CC BY 4.0)"},
            {"Cijene goriva", "uhvatipopust.ba, podaci sistema eOPC"},
            {"Vijesti", "klix.ba, n1info.ba, radiosarajevo.ba (naslov i kratki uvod, uz izvor)"},
            {"Sport", "Nogometni/Fudbalski savez BiH, nfsbih.ba"},
            {"Kursna lista", "Centralna banka BiH, www.cbbh.ba"},
            {"Voda", "ViK Sarajevo, viksa.ba"},
            {"Struja", "Elektroprivreda BiH, epbih.ba (planirana isključenja)"},
            {"Grijanje", "Toplane-Sarajevo, toplanesarajevo.ba"},
            {"Plin", "Sarajevogas, sarajevogas.ba"},
            {"Epizode zagađenja", "Vlada Kantona Sarajevo, vlada.ks.gov.ba"},
            {"Putevi i granice", "BIHAMK, bihamk.ba"},
            {"Praznici", "Zakon o praznicima FBiH; vjerski praznici prema objavama vjerskih zajednica"},
    };

    private static final String[][] SOFTWARE = {
            {"OkHttp, Okio, Conscrypt, ZXing", "Apache License 2.0"},
            {"jsoup", "MIT License"},
            {"NanoHTTPD", "BSD 3-Clause License"},
            {"Roboto, Roboto Mono", "SIL Open Font License 1.1"},
    };

    private static String lastResult;
    private static boolean confirmReset;
    private static String confirmImport;

    private SystemSections() {}

    // ---- Wi-Fi stranica ----

    static void wifi(final SettingsScreen s, LinearLayout out) {
        Context c = s.getContext();
        Palette p = s.p;
        final Settings st = Settings.of(c);
        String address = WebServer.address(c);
        boolean on = st.webEnabled() && WebServer.isRunning();
        String state = !st.webEnabled() ? "Isključena" : address == null ? "Uključena, ali tablet nije na Wi-Fi mreži" : on ? "Uključena" : "Ne radi";
        Sections.add(out, p, Controls.row(c, p, "Wi-Fi stranica", state, Controls.toggle(c, p, st.webEnabled(), v -> {
            st.setWebEnabled(v);
            WebServer.update(s.getContext());
            s.changed();
        })));
        out.addView(Controls.note(c, p, "Podsjetnike možete dodavati i mijenjati s telefona na istoj kućnoj Wi-Fi mreži. "
                + "Otvorite adresu u pregledniku ili skenirajte kod kamerom, pa se prijavite PIN-om. "
                + "Stranica radi samo unutar kućne mreže i nije dostupna s interneta.", p.fg));
        if (on && address != null) {
            LinearLayout qrRow = new LinearLayout(c);
            qrRow.setGravity(Gravity.CENTER_VERTICAL);
            QrView qr = new QrView(c);
            qr.setText(address);
            qrRow.addView(qr, new LinearLayout.LayoutParams(Ui.dp(c, 210), Ui.dp(c, 210)));
            LinearLayout text = new LinearLayout(c);
            text.setOrientation(LinearLayout.VERTICAL);
            text.addView(Ui.label(c, p, "Adresa"));
            TextView addr = Ui.text(c, 22, Fonts.monoMedium(), p.fg);
            addr.setText(NarrowSpan.numeric(address.replace("http://", "")));
            LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            al.topMargin = Ui.dp(c, 6);
            text.addView(addr, al);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tl.leftMargin = Ui.dp(c, 20);
            qrRow.addView(text, tl);
            out.addView(qrRow);
        }
    }

    static String wifiKey(Context c) {
        return WebServer.address(c) + WebServer.isRunning();
    }

    // ---- Sigurnost i podaci ----

    static void data(final SettingsScreen s, LinearLayout out) {
        Context c = s.getContext();
        Palette p = s.p;
        Sections.add(out, p, Controls.row(c, p, "PIN", "Za Postavke i za Wi-Fi stranicu.",
                Controls.button(c, p, "Promijeni PIN", false, v -> s.changePin())));

        LinearLayout backupButtons = new LinearLayout(c);
        backupButtons.addView(Controls.button(c, p, "Napravi kopiju", false, v -> {
            try {
                File f = Backup.export(s.getContext(), Clock.now());
                lastResult = "Sačuvano: " + f.getAbsolutePath();
            } catch (Exception e) {
                lastResult = "Kopija nije sačuvana: " + e.getMessage();
            }
            s.changed();
        }));
        Sections.add(out, p, Controls.row(c, p, "Rezervna kopija",
                "Postavke i podsjetnici u mapi " + Backup.FOLDER + " na tabletu (ostaje i ako se aplikacija ponovo instalira). "
                        + "Tablet sam pravi kopiju svaki dan kad se nešto promijeni i čuva zadnjih sedam. "
                        + "Kopiju možete preuzeti i na telefon sa Wi-Fi stranice. PIN se ne sprema u kopiju.", backupButtons));
        if (lastResult != null) out.addView(Controls.note(c, p, lastResult, lastResult.startsWith("Sačuvano") || lastResult.startsWith("Vraćeno") ? p.ok : p.alert));

        List<File> files = Backup.list();
        if (!files.isEmpty()) {
            TextView h = Ui.label(c, p, "Vrati iz kopije");
            h.setPadding(0, Ui.dp(c, 14), 0, Ui.dp(c, 2));
            out.addView(h);
            for (int i = 0; i < files.size() && i < 4; i++) {
                final File f = files.get(i);
                boolean asking = f.getName().equals(confirmImport);
                TextView b = Controls.button(c, p, asking ? "Sigurno? Zamijeni sve" : "Vrati", asking, v -> {
                    if (!f.getName().equals(confirmImport)) {
                        confirmImport = f.getName();
                        s.changed();
                        return;
                    }
                    confirmImport = null;
                    try {
                        int n = Backup.restore(s.getContext(), Backup.parse(Backup.read(f)));
                        lastResult = "Vraćeno iz " + f.getName() + " (" + n + " podsjetnika).";
                    } catch (Exception e) {
                        lastResult = "Vraćanje nije uspjelo: " + e.getMessage();
                    }
                    s.changed();
                });
                Sections.add(out, p, Controls.row(c, p, Backup.label(f.getName()),
                        asking ? "Postavke i podsjetnici na tabletu zamijenit će se ovom kopijom." : null, b));
            }
        }

        TextView reset = Controls.button(c, p, confirmReset ? "Sigurno? Vrati zadano" : "Vrati zadano", false, v -> {
            if (!confirmReset) {
                confirmReset = true;
                s.changed();
                return;
            }
            confirmReset = false;
            Settings.of(s.getContext()).reset();
            WebServer.update(s.getContext());
            lastResult = null;
            s.changed();
        });
        reset.setTextColor(p.alert);
        Sections.add(out, p, Controls.row(c, p, "Zadane postavke", "Vraća izgled, pločice, izvore i ostalo na početno. PIN, podsjetnici i podaci o autoru ostaju.", reset));
        Sections.add(out, p, Controls.row(c, p, "Zaključaj Postavke", "Zaključaju se i same kad izađete ili nakon dvije minute bez dodira.",
                Controls.button(c, p, "Zaključaj", false, v -> s.lockNow())));
    }

    // ---- Ažuriranja ----

    static void updates(final SettingsScreen s, LinearLayout out) {
        Context c = s.getContext();
        Palette p = s.p;
        UpdateState st = UpdateState.of(c);
        Sections.add(out, p, Controls.row(c, p, "Instalirana verzija", BuildConfig.VERSION_NAME, null));

        String ready = st.readyVersion();
        if (ready != null && st.readyFile(c) != null) {
            Sections.add(out, p, Controls.row(c, p, "Nova verzija " + ready,
                    "Preuzeta i provjerena. Instalacija traje oko pola minute; postavke i podsjetnici ostaju.",
                    Controls.button(c, p, "Instaliraj", true, v -> Updater.install(v.getContext()))));
            if (!st.readyNotes().isEmpty()) out.addView(Controls.note(c, p, st.readyNotes(), p.fg2));
        }

        String progress = Updater.progress();
        TextView check = Controls.button(c, p, progress != null ? "Provjeravam…" : "Provjeri ažuriranje", false, v -> {
            Updater.checkNow(v.getContext());
            s.changed();
        });
        check.setEnabled(progress == null);
        if (progress != null) check.setAlpha(0.5f);
        String desc;
        if (progress != null) {
            desc = progress;
        } else if (st.resultAt() > 0) {
            desc = Fmt.fullDate(st.resultAt()) + " u " + Fmt.hhmm(st.resultAt()) + ": " + st.result();
        } else {
            desc = "Još nije provjereno.";
        }
        Sections.add(out, p, Controls.row(c, p, "Provjera", desc, check));
        if (!Updater.installAllowed(c)) {
            out.addView(Controls.note(c, p, "Da bi se nova verzija mogla instalirati, u postavkama tableta (Sigurnost) treba uključiti "
                    + "„Nepoznati izvori“.", p.warn));
        }
        out.addView(Controls.note(c, p, "Tablet sam provjerava jednom mjesečno, i odmah kad neki izvor podataka ne radi dva dana. "
                + "Nove verzije dolaze s GitHuba (github.com/" + Updater.repo() + ") i instaliraju se tek kad dodirnete Instaliraj.", p.fg2));
    }

    static String updatesKey(Context c) {
        UpdateState st = UpdateState.of(c);
        return Updater.progress() + "|" + st.resultAt() + "|" + st.readyVersion();
    }

    // ---- O aplikaciji ----

    static void about(final SettingsScreen s, LinearLayout out) {
        Context c = s.getContext();
        Palette p = s.p;
        Settings st = Settings.of(c);
        TextView name = Ui.text(c, 24, Fonts.medium(), p.fg);
        name.setText("InfoTablet");
        out.addView(name);
        TextView ver = Ui.text(c, 14, Fonts.regular(), p.fg2);
        ver.setText("Verzija " + BuildConfig.VERSION_NAME + " · informacije za dom, bez oglasa i bez praćenja");
        ver.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 10));
        out.addView(ver);

        String author = st.getString("creator_name");
        String link = st.getString("creator_link");
        if (!author.isEmpty() || !link.isEmpty()) {
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 12));
            LinearLayout text = new LinearLayout(c);
            text.setOrientation(LinearLayout.VERTICAL);
            if (!author.isEmpty()) {
                TextView a = Ui.text(c, 17, Fonts.medium(), p.fg);
                a.setText("Autor: " + author);
                text.addView(a);
            }
            if (!link.isEmpty()) {
                TextView l = Ui.text(c, 14.5f, Fonts.regular(), p.accent);
                l.setText(link);
                l.setPadding(0, Ui.dp(c, 4), 0, 0);
                text.addView(l);
                TextView hint = Ui.text(c, 13.5f, Fonts.regular(), p.fg2);
                hint.setText("Skenirajte kod kamerom telefona.");
                hint.setPadding(0, Ui.dp(c, 8), 0, 0);
                text.addView(hint);
            }
            row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            if (!link.isEmpty()) {
                QrView qr = new QrView(c);
                qr.setText(link);
                row.addView(qr, new LinearLayout.LayoutParams(Ui.dp(c, 170), Ui.dp(c, 170)));
            }
            out.addView(row);
        }

        out.addView(Controls.divider(c, p));
        TextView h = Ui.label(c, p, "Izvori podataka");
        h.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 4));
        out.addView(h);
        for (String[] src : SOURCES) out.addView(credit(c, p, src[0], src[1]));
        TextView h2 = Ui.label(c, p, "Softver i fontovi");
        h2.setPadding(0, Ui.dp(c, 14), 0, Ui.dp(c, 4));
        out.addView(h2);
        for (String[] sw : SOFTWARE) out.addView(credit(c, p, sw[0], sw[1]));
        out.addView(Controls.note(c, p, "Aplikacija šalje zahtjeve samo ovim izvorima i GitHubu (provjera ažuriranja). Nema analitike ni reklama.", p.fg2));
    }

    private static View credit(Context c, Palette p, String what, String who) {
        LinearLayout r = new LinearLayout(c);
        r.setPadding(0, Ui.dp(c, 5), 0, Ui.dp(c, 5));
        TextView w = Ui.text(c, 14.5f, Fonts.medium(), p.fg);
        w.setText(what);
        r.addView(w, new LinearLayout.LayoutParams(Ui.dp(c, 200), LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView s = Ui.text(c, 14.5f, Fonts.regular(), p.fg2);
        s.setText(who);
        r.addView(s, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return r;
    }
}
