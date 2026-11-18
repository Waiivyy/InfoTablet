package ba.infotablet.web;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import ba.infotablet.reminders.Agenda;
import ba.infotablet.reminders.History;
import ba.infotablet.reminders.Reminder;
import ba.infotablet.reminders.ReminderText;
import ba.infotablet.util.Fmt;

/**
 * HTML of the Wi-Fi page: a phone-sized version of the Podsjetnici screen. Everything is served by
 * the tablet itself (styles inline, fonts from the app), so the page makes no outside requests.
 */
public final class Pages {

    private Pages() {}

    private static final String CSS =
            "@font-face{font-family:R;src:url(/f/r.ttf)}@font-face{font-family:R;font-weight:500;src:url(/f/m.ttf)}"
            + "@font-face{font-family:M;font-weight:500;src:url(/f/mm.ttf)}"
            + ":root{--bg:#E6E9ED;--panel:#FAFBFC;--line:#CFD6DD;--soft:#E3E7EB;--fg:#0F1419;--fg2:#4D5864;--fg3:#66717C;"
            + "--accent:#1F46B0;--accent-fg:#FFFFFF;--tint:rgba(31,70,176,.10);--alert:#C2352A;--warn:#965D00;--ok:#1D8A4C;color-scheme:light}"
            + "@media (prefers-color-scheme:dark){:root{--bg:#080B0E;--panel:#10151A;--line:#212931;--soft:#1A2128;--fg:#CED6DE;"
            + "--fg2:#8A96A2;--fg3:#77828E;--accent:#7C9FFF;--accent-fg:#0A0F1A;--tint:rgba(124,159,255,.13);--alert:#EC695D;"
            + "--warn:#E09D36;--ok:#3FB46F;color-scheme:dark}}"
            + "*{box-sizing:border-box}html{-webkit-text-size-adjust:100%}"
            + "body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.4 R,system-ui,sans-serif;padding:16px 16px 40px}"
            + "::selection{background:var(--tint)}main{max-width:560px;margin:0 auto}"
            + "h1{font-size:22px;font-weight:500;margin:4px 0 14px}"
            + ".panel{background:var(--panel);border:1px solid var(--line);border-radius:8px;padding:6px 16px}"
            + ".label{font-size:12px;font-weight:500;letter-spacing:.08em;text-transform:uppercase;color:var(--fg2)}"
            + ".row{display:flex;align-items:center;gap:12px;padding:12px 0;border-top:1px solid var(--soft);color:inherit;text-decoration:none}"
            + ".row:first-child{border-top:0}.when{width:76px;flex:none}"
            + ".t{font:500 19px M,ui-monospace,monospace}.d{font-size:12px;font-weight:500;letter-spacing:.06em;color:var(--fg2);text-transform:uppercase}"
            + ".mid{flex:1;min-width:0}.txt{font-weight:500;font-size:17px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}"
            + ".det{font-size:14px;color:var(--fg2)}.miss .t,.miss .det{color:var(--alert)}.snz .t{color:var(--warn)}.done{opacity:.5}"
            + "svg{width:22px;height:22px;flex:none;fill:none;stroke:currentColor;stroke-width:1.75;stroke-linecap:round;stroke-linejoin:round;color:var(--fg2)}"
            + ".btn{display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:48px;padding:0 18px;border-radius:8px;"
            + "border:1px solid var(--line);background:var(--panel);color:var(--fg);font:500 16px R,system-ui,sans-serif;text-decoration:none;cursor:pointer}"
            + ".btn.primary{background:var(--accent);border-color:var(--accent);color:var(--accent-fg)}.btn.danger{color:var(--alert)}"
            + ".btn:active{background:var(--soft)}.btn.primary:active{filter:brightness(.85)}"
            + ".btn svg{color:inherit}.top{display:flex;justify-content:space-between;align-items:center;margin-bottom:12px}"
            + "form.inline{margin:0}.small{min-height:40px;padding:0 14px;font-size:15px}"
            + "label.f{display:block;margin:16px 0 6px}"
            + "input[type=text],input[type=date],input[type=time],input[type=password]{width:100%;min-height:50px;padding:0 12px;"
            + "border:1px solid var(--line);border-radius:8px;background:var(--panel);color:var(--fg);font:18px R,system-ui,sans-serif;caret-color:var(--accent)}"
            + "input:focus-visible,.btn:focus-visible,.seg input:focus-visible+span{outline:2px solid var(--accent);outline-offset:2px}"
            + ".two{display:flex;gap:10px}.two>div{flex:1}"
            + ".seg{display:grid;grid-template-columns:repeat(6,1fr);gap:6px}.seg label{grid-column:span 2;min-width:0}"
            + ".seg.r5 label:nth-child(n+4){grid-column:span 3}.seg.r3 label{grid-column:span 2}.seg input{position:absolute;opacity:0}"
            + ".seg span{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;min-height:46px;padding:6px 10px;"
            + "border:1px solid var(--line);border-radius:8px;background:var(--panel);font-weight:500;font-size:15px;cursor:pointer;text-align:center}"
            + ".seg input:checked+span{background:var(--accent);border-color:var(--accent);color:var(--accent-fg)}.seg input:checked+span svg{color:inherit}"
            + ".err{color:var(--alert);font-weight:500;margin:12px 0 0}.ok{color:var(--fg2);margin:0 0 12px}"
            + ".actions{display:flex;gap:10px;margin-top:22px}.actions .grow{flex:1}.empty{color:var(--fg2);padding:16px 0}"
            + ".foot{color:var(--fg3);font-size:13px;margin-top:18px;text-align:center}"
            + ".h-ok{color:var(--ok)}.h-miss{color:var(--alert)}.h-open{color:var(--warn)}";

    /** Icon outlines, the same shapes as on the tablet. */
    static String icon(String category) {
        String d;
        if ("racun".equals(category)) d = "<path d='M6 3.5h12v17l-2-1.3-2 1.3-2-1.3-2 1.3-2-1.3-2 1.3zM9 8h6M9 11.5h6M9 15h3.5'/>";
        else if ("doktor".equals(category)) d = "<rect x='3.5' y='3.5' width='17' height='17' rx='4'/><path d='M12 8v8M8 12h8'/>";
        else if ("lijek".equals(category)) d = "<g transform='rotate(-40 12 12)'><rect x='3' y='8.5' width='18' height='7' rx='3.5'/><path d='M12 8.5v7'/></g>";
        else if ("auto".equals(category)) d = "<path d='M4 16.5v-4l2-5h12l2 5v4zM4 16.5V19h3v-2.5M17 16.5V19h3v-2.5M4 12.5h16'/>";
        else if ("rodjendan".equals(category)) d = "<path d='M4.5 20.5h15v-7a1.5 1.5 0 0 0-1.5-1.5H6a1.5 1.5 0 0 0-1.5 1.5zM4.5 16.2c2.5 1.4 4.5 1.4 7.5 0s5-1.4 7.5 0M12 12V8.6M12 5.4v.4'/>";
        else if ("plan".equals(category)) d = "<path d='M5.5 5h13a2 2 0 0 1 2 2v11.5a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM3.5 10h17M8 3v4M16 3v4'/>";
        else d = "<path d='M6.5 16.5v-5a5.5 5.5 0 0 1 11 0v5l1.5 2h-14zM10 20.5a2.2 2.2 0 0 0 4 0'/>";
        return "<svg viewBox='0 0 24 24' aria-hidden='true'>" + d + "</svg>";
    }

    private static final String PLUS = "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M12 5v14M5 12h14'/></svg>";

    static String page(String title, String body) {
        return "<!doctype html><html lang='bs'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<meta name='referrer' content='same-origin'><title>" + esc(title) + "</title><style>" + CSS + "</style></head>"
                + "<body><main>" + body + "</main></body></html>";
    }

    static String login(String error) {
        return page("Podsjetnici", "<h1>Podsjetnici</h1><div class='panel' style='padding:16px'>"
                + "<form method='post' action='/login'><label class='f label' for='pin' style='margin-top:0'>PIN tableta</label>"
                + "<input id='pin' name='pin' type='password' inputmode='numeric' pattern='[0-9]*' autocomplete='current-password' maxlength='8' autofocus>"
                + (error == null ? "" : "<p class='err'>" + esc(error) + "</p>")
                + "<div class='actions'><button class='btn primary grow' type='submit'>Prijava</button></div></form></div>"
                + "<p class='foot'>Isti PIN kao za Postavke na tabletu.</p>");
    }

    static String list(List<Agenda.Entry> entries, long now, String csrf, String flash) {
        StringBuilder b = new StringBuilder();
        b.append("<div class='top'><h1 style='margin:0'>Podsjetnici</h1>")
                .append("<a class='btn primary' href='/new'>").append(PLUS).append("Novi</a></div>");
        if (flash != null) b.append("<p class='ok'>").append(esc(flash)).append("</p>");
        b.append("<div class='panel'>");
        if (entries.isEmpty()) b.append("<p class='empty'>Još nema podsjetnika.</p>");
        boolean doneHeader = false;
        for (Agenda.Entry e : entries) {
            boolean finished = Agenda.finished(e.r, now);
            if (finished && !doneHeader) {
                doneHeader = true;
                b.append("<p class='label' style='margin:18px 0 4px'>Završeno</p>");
            }
            boolean miss = e.state == Agenda.State.MISSED || e.state == Agenda.State.RINGING;
            String cls = "row" + (miss ? " miss" : e.state == Agenda.State.SNOOZED ? " snz" : "") + (finished ? " done" : "");
            String day = finished ? Fmt.dayMonth(e.at) : Fmt.relDay(now, e.at);
            String note = e.state == Agenda.State.MISSED ? "propušteno · " : e.state == Agenda.State.SNOOZED
                    ? "odgođeno do " + Fmt.hhmm(e.r.snoozeUntil) + " · " : "";
            b.append("<div class='").append(cls).append("'>")
                    .append("<a class='when' href='/edit?id=").append(e.r.id).append("' style='color:inherit;text-decoration:none'>")
                    .append(e.r.allDay ? "<div class='t' style='font:500 17px R,system-ui,sans-serif'>" + esc(day) + "</div><div class='d'>cijeli dan</div></a>"
                            : "<div class='t'>" + Fmt.hhmm(e.at) + "</div><div class='d'>" + esc(day) + "</div></a>")
                    .append("<a class='mid' href='/edit?id=").append(e.r.id).append("' style='color:inherit;text-decoration:none'>")
                    .append("<div class='txt'>").append(esc(e.r.text)).append("</div><div class='det'>")
                    .append(esc(note + ReminderText.details(e.r))).append("</div></a>");
            if (miss || e.state == Agenda.State.SNOOZED) {
                b.append("<form class='inline' method='post' action='/done'><input type='hidden' name='csrf' value='").append(csrf)
                        .append("'><input type='hidden' name='id' value='").append(e.r.id)
                        .append("'><button class='btn small' type='submit'>Gotovo</button></form>");
            } else {
                b.append(icon(!e.r.ring && e.r.category.isEmpty() ? "plan" : e.r.category));
            }
            b.append("</div>");
        }
        b.append("</div><form method='post' action='/logout' class='foot'><input type='hidden' name='csrf' value='").append(csrf)
                .append("'><a class='btn small' href='/history'>Historija</a> <a class='btn small' href='/backup'>Preuzmi rezervnu kopiju</a> ")
                .append("<button class='btn small' type='submit'>Odjava</button></form>");
        return page("Podsjetnici", b.toString());
    }

    /** What happened to each ring in the last 30 days, newest first, grouped by day. */
    static String history(List<History.Row> rows, long now) {
        StringBuilder b = new StringBuilder();
        b.append("<div class='top'><h1 style='margin:0'>Historija</h1><a class='btn' href='/'>Nazad</a></div>")
                .append("<p class='ok'>Zadnjih 30 dana: kad je koji podsjetnik zazvonio i šta je bilo s njim.</p><div class='panel'>");
        if (rows.isEmpty()) b.append("<p class='empty'>Još nema zabilježenih zvona.</p>");
        String day = null;
        for (History.Row r : rows) {
            String d = Fmt.relDay(now, r.dueAt);
            if (!d.equals(day)) {
                b.append("<p class='label' style='margin:").append(day == null ? "10px" : "18px").append(" 0 2px'>").append(esc(d)).append("</p>");
                day = d;
            }
            String cls = r.outcome == History.Outcome.DONE ? "h-ok" : r.outcome == History.Outcome.MISSED ? "h-miss" : "h-open";
            String doneTime = Fmt.dayOffset(r.dueAt, r.doneAt) == 0 ? Fmt.hhmm(r.doneAt) : Fmt.hhmm(r.doneAt) + " (" + Fmt.dayMonth(r.doneAt) + ")";
            b.append("<div class='row'><div class='when'><div class='t'>").append(Fmt.hhmm(r.dueAt)).append("</div></div>")
                    .append("<div class='mid'><div class='txt'>").append(esc(r.text)).append("</div><div class='det ").append(cls).append("'>")
                    .append(esc(History.describe(r, doneTime))).append("</div></div>").append(icon(r.category)).append("</div>");
        }
        b.append("</div>");
        return page("Historija", b.toString());
    }

    /** The add/edit form; r carries the values to show (a fresh default for a new reminder). */
    static String form(Reminder r, boolean isNew, String error, String csrf) {
        StringBuilder b = new StringBuilder();
        String what = r.ring ? "podsjetnik" : "plan";
        b.append("<h1>").append(isNew ? "Novi " + what : "Uredi " + what).append("</h1>")
                .append("<form method='post' action='/save' class='panel' style='padding:4px 16px 16px'>")
                .append("<input type='hidden' name='csrf' value='").append(csrf).append("'>")
                .append("<input type='hidden' name='id' value='").append(isNew ? 0 : r.id).append("'>")
                .append("<label class='f label' for='text'>Šta treba zapamtiti</label>")
                .append("<input id='text' name='text' type='text' maxlength='").append(ReminderForm.MAX_TEXT)
                .append("' placeholder='Npr. Lijek za pritisak' value='").append(esc(r.text)).append("'")
                .append(isNew ? " autofocus" : "").append(" required>")
                .append("<div class='two'><div><label class='f label' for='date'>Datum</label>")
                .append("<input id='date' name='date' type='date' required value='")
                .append(String.format(Locale.ROOT, "%04d-%02d-%02d", r.year, r.month, r.day)).append("'></div>")
                .append("<div><label class='f label' for='time'>Vrijeme</label>")
                .append("<input id='time' name='time' type='time' required value='")
                .append(String.format(Locale.ROOT, "%02d:%02d", r.hour, r.minute)).append("'></div></div>")
                .append("<p class='f label'>Zvono</p><div class='seg r3'>");
        for (int i = 0; i < Reminder.MODE_SHORT.length; i++) {
            b.append("<label><input type='radio' name='mode' value='").append(i).append("'")
                    .append(r.mode() == i ? " checked" : "").append("><span>").append(Reminder.MODE_SHORT[i]).append("</span></label>");
        }
        b.append("</div><p class='foot' style='text-align:left;margin:6px 0 0'>„Bez zvona“ i „Cijeli dan“ se samo vide u kalendaru i na početnom ekranu.</p>")
                .append("<p class='f label'>Ponavljanje</p><div class='seg r5'>");
        for (int i = 0; i < Reminder.REPEAT_SHORT.length; i++) {
            b.append("<label><input type='radio' name='repeat' value='").append(i).append("'")
                    .append(r.repeat == i ? " checked" : "").append("><span>").append(Reminder.REPEAT_SHORT[i]).append("</span></label>");
        }
        b.append("</div><p class='f label'>Vrsta</p><div class='seg'>");
        for (String k : Reminder.CATEGORIES) {
            String name = k.isEmpty() ? "Ostalo" : Reminder.categoryName(k);
            b.append("<label><input type='radio' name='category' value='").append(k).append("'")
                    .append(r.category.equals(k) ? " checked" : "").append("><span>").append(icon(k)).append(name).append("</span></label>");
        }
        b.append("</div>");
        if (error != null) b.append("<p class='err'>").append(esc(error)).append("</p>");
        b.append("<div class='actions'>");
        if (!isNew) b.append("<a class='btn danger' href='/delete?id=").append(r.id).append("'>Obriši</a>");
        b.append("<a class='btn' href='/' style='margin-left:auto'>Odustani</a>")
                .append("<button class='btn primary' type='submit'>Sačuvaj</button></div></form>");
        return page(isNew ? "Novi " + what : "Uredi " + what, b.toString());
    }

    static String confirmDelete(Reminder r, String csrf) {
        return page("Obrisati?", "<h1>Obrisati podsjetnik?</h1><div class='panel' style='padding:16px'>"
                + "<p style='margin:0 0 4px;font-weight:500;font-size:18px'>" + esc(r.text) + "</p>"
                + "<p class='det' style='margin:0'>" + esc(ReminderText.details(r)) + "</p>"
                + "<form method='post' action='/delete' class='actions'><input type='hidden' name='csrf' value='" + csrf + "'>"
                + "<input type='hidden' name='id' value='" + r.id + "'><a class='btn grow' href='/edit?id=" + r.id + "'>Ne</a>"
                + "<button class='btn primary grow' type='submit' style='background:var(--alert);border-color:var(--alert);color:#fff'>Da, obriši</button>"
                + "</form></div>");
    }

    static String message(String title, String text) {
        return page(title, "<h1>" + esc(title) + "</h1><div class='panel' style='padding:16px'><p style='margin:0'>" + esc(text)
                + "</p><div class='actions'><a class='btn' href='/'>Nazad</a></div></div>");
    }

    /** Escapes text for HTML element content and single- or double-quoted attributes. */
    static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                case '\'': b.append("&#39;"); break;
                default: b.append(c);
            }
        }
        return b.toString();
    }

    /** Default values for a new reminder: today at the next full hour. */
    static Reminder fresh(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.add(Calendar.HOUR_OF_DAY, 1);
        Reminder r = new Reminder();
        r.year = c.get(Calendar.YEAR);
        r.month = c.get(Calendar.MONTH) + 1;
        r.day = c.get(Calendar.DAY_OF_MONTH);
        r.hour = c.get(Calendar.HOUR_OF_DAY);
        return r;
    }
}
