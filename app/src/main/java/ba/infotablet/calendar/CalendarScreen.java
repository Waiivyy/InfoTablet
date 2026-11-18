package ba.infotablet.calendar;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ba.infotablet.R;
import ba.infotablet.reminders.Agenda;
import ba.infotablet.reminders.Reminder;
import ba.infotablet.reminders.ReminderEditor;
import ba.infotablet.reminders.ReminderStore;
import ba.infotablet.reminders.ReminderText;
import ba.infotablet.ui.Busy;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.NarrowSpan;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Refreshable;
import ba.infotablet.ui.Ui;
import ba.infotablet.util.BsDate;
import ba.infotablet.util.Clock;
import ba.infotablet.util.Fmt;

/**
 * Kalendar: a month with holidays and planned days marked (arrows move between months, a tap
 * opens a day), and on the right this week's plans, then holidays and dated entries further
 * ahead. A day shows everything on it and can take a new plan; the editor opens in the same
 * panel. Plans are reminders that do not ring, so they also show on Početna and the Wi-Fi page.
 */
public final class CalendarScreen extends FrameLayout implements Refreshable, Busy, ReminderEditor.Listener {

    private static final String[] WEEK = {"pon", "uto", "sri", "čet", "pet", "sub", "ned"};
    private static final long DAY_MS = 86_400_000L;
    private static final int LATER_ROWS = 4;
    private static final long LATER_HORIZON_MS = 366L * DAY_MS;
    private static final int MAX_MONTHS_AHEAD = 18;

    /** One line: a holiday or a reminder occurrence. */
    private static final class Item {
        final long at;
        final Holidays.Day holiday;
        final Reminder reminder;

        Item(long at, Holidays.Day holiday, Reminder reminder) {
            this.at = at;
            this.holiday = holiday;
            this.reminder = reminder;
        }
    }

    private final Palette p;
    private final LinearLayout month;
    private final FrameLayout side;
    private ReminderEditor editor;
    /** Months from the current one (0 = this month). */
    private int monthOffset;
    /** The opened day (local midnight), or 0 for the week view. */
    private long selectedDay;
    private String shown = "";

    public CalendarScreen(Context c, Palette p) {
        super(c);
        this.p = p;
        int pad = Ui.pagePad(c);
        LinearLayout root = new LinearLayout(c);
        root.setPadding(pad, pad, pad, pad);
        month = panel(c);
        root.addView(month, new LinearLayout.LayoutParams(Ui.dp(c, 430), LinearLayout.LayoutParams.MATCH_PARENT));
        side = new FrameLayout(c);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        sl.leftMargin = Ui.gap(c);
        root.addView(side, sl);
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
    public boolean busy() {
        return editor != null && editor.dirty();
    }

    @Override
    public void refresh(long now) {
        if (editor != null) return;   // never rebuild under an open editor
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(now);
        String key = today.get(Calendar.YEAR) + "-" + today.get(Calendar.DAY_OF_YEAR) + "/" + ReminderStore.version()
                + "/" + monthOffset + "/" + selectedDay;
        if (key.equals(shown)) return;   // nothing changes within a day unless reminders do
        shown = key;
        Context c = getContext();
        List<Holidays.Day> all = Holidays.all(c);
        List<Reminder> reminders = ReminderStore.all(c);
        bindMonth(c, now, all, reminders);
        side.removeAllViews();
        side.addView(selectedDay != 0 ? dayPanel(c, now, all, reminders) : weekPanel(c, now, all, reminders),
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private void rebuild() {
        shown = "";
        refresh(Clock.now());
    }

    // ---- month ----

    private void bindMonth(Context c, long now, List<Holidays.Day> all, List<Reminder> reminders) {
        month.removeAllViews();
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(now);
        Calendar shownMonth = (Calendar) today.clone();
        shownMonth.set(Calendar.DAY_OF_MONTH, 1);
        shownMonth.set(Calendar.HOUR_OF_DAY, 0);
        shownMonth.set(Calendar.MINUTE, 0);
        shownMonth.set(Calendar.SECOND, 0);
        shownMonth.set(Calendar.MILLISECOND, 0);
        shownMonth.add(Calendar.MONTH, monthOffset);
        boolean thisMonth = monthOffset == 0;

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(arrow(c, R.drawable.ic_chevron_left, monthOffset > 0, () -> {
            monthOffset--;
            rebuild();
        }), new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 36)));
        TextView title = Ui.text(c, 17, Fonts.medium(), p.fg);
        title.setText(BsDate.capitalize(BsDate.month(shownMonth)) + " " + shownMonth.get(Calendar.YEAR));
        title.setGravity(Gravity.CENTER);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(arrow(c, R.drawable.ic_chevron_right, monthOffset < MAX_MONTHS_AHEAD, () -> {
            monthOffset++;
            rebuild();
        }), new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 36)));
        month.addView(head);

        Map<Integer, Holidays.Day> marks = new HashMap<Integer, Holidays.Day>();
        Calendar k = Calendar.getInstance();
        for (Holidays.Day d : all) {
            k.setTimeInMillis(d.date);
            if (k.get(Calendar.YEAR) == shownMonth.get(Calendar.YEAR) && k.get(Calendar.MONTH) == shownMonth.get(Calendar.MONTH)) {
                Holidays.Day prev = marks.get(k.get(Calendar.DAY_OF_MONTH));
                if (prev == null || (d.state && !prev.state)) marks.put(k.get(Calendar.DAY_OF_MONTH), d);
            }
        }

        Set<Integer> planned = new HashSet<Integer>();
        Calendar to = (Calendar) shownMonth.clone();
        to.add(Calendar.MONTH, 1);
        for (Reminder r : reminders) {
            if (r.repeat == Reminder.DAILY) continue;   // would mark every day
            for (long t : Agenda.occurrences(r, shownMonth.getTimeInMillis(), to.getTimeInMillis(), 31)) {
                k.setTimeInMillis(t);
                planned.add(k.get(Calendar.DAY_OF_MONTH));
            }
        }

        LinearLayout week = new LinearLayout(c);
        for (String w : WEEK) {
            TextView t = Ui.label(c, p, w);
            t.setGravity(Gravity.CENTER);
            week.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wl.topMargin = Ui.dp(c, 8);
        wl.bottomMargin = Ui.dp(c, 4);
        month.addView(week, wl);

        int offset = (shownMonth.get(Calendar.DAY_OF_WEEK) + 5) % 7;   // Monday first
        int days = shownMonth.getActualMaximum(Calendar.DAY_OF_MONTH);
        int todayNum = thisMonth ? today.get(Calendar.DAY_OF_MONTH) : -1;
        long todayStart = Fmt.startOfDay(now);
        int dayNum = 1 - offset;
        for (int w = 0; w < 6; w++) {
            LinearLayout row = new LinearLayout(c);
            for (int col = 0; col < 7; col++, dayNum++) {
                TextView cell = Ui.text(c, 18, Fonts.mono(), p.fg);
                cell.setGravity(Gravity.CENTER);
                if (dayNum >= 1 && dayNum <= days) {
                    Calendar d = (Calendar) shownMonth.clone();
                    d.set(Calendar.DAY_OF_MONTH, dayNum);
                    final long dayStart = d.getTimeInMillis();
                    cell.setText(String.valueOf(dayNum));
                    Holidays.Day h = marks.get(dayNum);
                    boolean isToday = dayNum == todayNum;
                    boolean selected = dayStart == selectedDay;
                    boolean past = dayStart < todayStart;
                    int border = 0;
                    if (isToday) {
                        cell.setTextColor(p.accentFg);
                    } else if (h != null && h.state) {
                        cell.setTextColor(p.alert);
                        border = p.alert;
                    } else if (h != null) {
                        cell.setTextColor(p.warn);
                    } else if (past) {
                        cell.setTextColor(p.fg3);
                    } else if (col >= 5) {
                        cell.setTextColor(p.fg2);
                    }
                    int fill = isToday ? p.accent : selected ? p.accentTint : 0x00000000;
                    if (selected && !isToday) border = p.accent;
                    StateListDrawable bg = new StateListDrawable();
                    bg.addState(new int[] {android.R.attr.state_pressed}, Ui.rounded(c, isToday ? p.accent : p.soft, border, 8));
                    bg.addState(new int[0], Ui.rounded(c, fill, border, 8));
                    cell.setBackgroundDrawable(bg);
                    if (planned.contains(dayNum)) {
                        cell.setCompoundDrawablesWithIntrinsicBounds(null, null, null, dot(c, isToday ? p.accentFg : p.accent));
                        cell.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 4));
                        cell.setCompoundDrawablePadding(Ui.dp(c, 2));
                    }
                    cell.setClickable(true);
                    cell.setOnClickListener(v -> {
                        selectedDay = selectedDay == dayStart ? 0 : dayStart;
                        rebuild();
                    });
                }
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                cl.setMargins(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3));
                row.addView(cell, cl);
            }
            month.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            if (dayNum > days) break;
        }
        LinearLayout legend = new LinearLayout(c);
        legend.setGravity(Gravity.CENTER_VERTICAL);
        legend.addView(legendItem(c, Ui.rounded(c, 0x00000000, p.alert, 3), 10, "neradni dan", p.alert));
        legend.addView(legendItem(c, Ui.rounded(c, p.warn, 0, 3), 10, "vjerski praznik", p.warn));
        legend.addView(legendItem(c, dot(c, p.accent), 6, "plan ili podsjetnik", p.accent));
        legend.setPadding(0, Ui.dp(c, 6), 0, 0);
        month.addView(legend);
    }

    private View arrow(Context c, int icon, boolean enabled, Runnable action) {
        LinearLayout b = new LinearLayout(c);
        b.setGravity(Gravity.CENTER);
        if (enabled) {
            b.setBackgroundDrawable(Ui.buttonBackground(c, p, false));
            b.setClickable(true);
            b.setOnClickListener(v -> action.run());
        }
        b.addView(Ui.icon(c, icon, enabled ? p.fg : p.fg3, 20));
        return b;
    }

    /** A legend entry with a drawn swatch (the same shape as in the grid). */
    private View legendItem(Context c, Drawable swatch, int sizeDp, String text, int color) {
        TextView t = Ui.text(c, 13, Fonts.regular(), color);
        t.setText(text);
        swatch.setBounds(0, 0, Ui.dp(c, sizeDp), Ui.dp(c, sizeDp));
        t.setCompoundDrawables(swatch, null, null, null);
        t.setCompoundDrawablePadding(Ui.dp(c, 6));
        t.setPadding(0, 0, Ui.dp(c, 16), 0);
        return t;
    }

    private static GradientDrawable dot(Context c, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        d.setSize(Ui.dp(c, 5), Ui.dp(c, 5));
        return d;
    }

    // ---- what is on a day ----

    /** Holidays first, then whole-day plans, then entries by time. Daily reminders only when asked for. */
    static List<Item> onDay(List<Holidays.Day> all, List<Reminder> reminders, long dayStart, boolean withDaily) {
        List<Item> out = new ArrayList<Item>();
        for (Holidays.Day d : all) if (d.date == dayStart) out.add(new Item(d.date, d, null));
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(dayStart);
        k.add(Calendar.DAY_OF_MONTH, 1);
        long dayEnd = k.getTimeInMillis();
        for (Reminder r : reminders) {
            if (!withDaily && r.repeat == Reminder.DAILY) continue;
            for (long t : Agenda.occurrences(r, dayStart, dayEnd, 2)) out.add(new Item(t, null, r));
        }
        Collections.sort(out, (a, b) -> {
            if ((a.holiday == null) != (b.holiday == null)) return a.holiday != null ? -1 : 1;
            if (a.holiday != null) return 0;
            if (a.reminder.allDay != b.reminder.allDay) return a.reminder.allDay ? -1 : 1;
            return a.at < b.at ? -1 : a.at > b.at ? 1 : a.reminder.text.compareToIgnoreCase(b.reminder.text);
        });
        return out;
    }

    // ---- week view ----

    private View weekPanel(Context c, long now, List<Holidays.Day> all, List<Reminder> reminders) {
        LinearLayout panel = panel(c);
        long today = Fmt.startOfDay(now);
        panel.addView(header(c, "Ova sedmica", "Dodaj plan", () -> openEditor(null, today)));
        LinearLayout body = scrollBody(c, panel);

        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(today);
        int shownItems = 0;
        for (int i = 0; i < 7; i++) {
            long day = k.getTimeInMillis();
            List<Item> items = onDay(all, reminders, day, false);
            if (i == 0) items = withoutPast(items, now);
            if (!items.isEmpty()) {
                body.addView(dayHeading(c, now, day, shownItems == 0));
                for (Item it : items) body.addView(itemRow(c, it, now, false));
                shownItems += items.size();
            }
            k.add(Calendar.DAY_OF_MONTH, 1);
        }
        long weekEnd = k.getTimeInMillis();
        if (shownItems == 0) {
            TextView t = Ui.text(c, 16, Fonts.regular(), p.fg2);
            t.setText("Ove sedmice nema ništa planirano. Dodirnite dan u kalendaru ili „Dodaj plan“.");
            t.setLineSpacing(Ui.dp(c, 3), 1f);
            t.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 4));
            body.addView(t);
        }

        List<Item> later = later(all, reminders, now, weekEnd);
        if (!later.isEmpty()) {
            TextView h = Ui.label(c, p, "Kasnije");
            h.setPadding(0, Ui.dp(c, 18), 0, Ui.dp(c, 2));
            body.addView(h);
            for (Item it : later) body.addView(itemRow(c, it, now, true));
        }
        TextView src = Ui.text(c, 12, Fonts.regular(), p.fg2);
        src.setText("Datumi bajrama nakon 2026. su okvirni dok ih Islamska zajednica ne objavi.");
        src.setPadding(0, Ui.dp(c, 12), 0, 0);
        body.addView(src);
        return panel;
    }

    /** Today's timed entries that are already over drop out; whole-day ones and holidays stay. */
    private static List<Item> withoutPast(List<Item> items, long now) {
        List<Item> out = new ArrayList<Item>();
        for (Item it : items) {
            if (it.holiday != null || it.reminder.allDay || it.at >= now) out.add(it);
        }
        return out;
    }

    /** Holidays and dated entries (one-time, monthly, yearly) after this week. */
    private static List<Item> later(List<Holidays.Day> all, List<Reminder> reminders, long now, long after) {
        List<Item> out = new ArrayList<Item>();
        for (Holidays.Day d : Holidays.upcoming(all, after, LATER_ROWS)) out.add(new Item(d.date, d, null));
        for (Reminder r : reminders) {
            if (r.repeat == Reminder.DAILY || r.repeat == Reminder.WEEKLY) continue;
            long at = Agenda.next(r, after);
            if (at != Agenda.NONE && at - now <= LATER_HORIZON_MS) out.add(new Item(at, null, r));
        }
        Collections.sort(out, (a, b) -> {
            long da = Fmt.startOfDay(a.at), db = Fmt.startOfDay(b.at);
            if (da != db) return da < db ? -1 : 1;
            if ((a.holiday == null) != (b.holiday == null)) return a.holiday != null ? -1 : 1;
            return a.at < b.at ? -1 : (a.at > b.at ? 1 : 0);
        });
        return out.size() > LATER_ROWS ? out.subList(0, LATER_ROWS) : out;
    }

    // ---- one day ----

    private View dayPanel(Context c, long now, List<Holidays.Day> all, List<Reminder> reminders) {
        LinearLayout panel = panel(c);
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(selectedDay);
        boolean past = selectedDay < Fmt.startOfDay(now);
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(c, 19, Fonts.medium(), p.fg);
        title.setText(BsDate.capitalize(BsDate.weekday(k)) + ", " + k.get(Calendar.DAY_OF_MONTH) + ". " + BsDate.month(k));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = Ui.button(c, p, "Sedmica", false, 15);
        close.setOnClickListener(v -> {
            selectedDay = 0;
            rebuild();
        });
        head.addView(close, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(c, 40)));
        panel.addView(head);
        TextView rel = Ui.label(c, p, past ? "prošlo" : Holidays.countdown(Holidays.daysUntil(now, selectedDay)));
        rel.setPadding(0, Ui.dp(c, 2), 0, 0);
        panel.addView(rel);

        LinearLayout body = scrollBody(c, panel);
        List<Item> items = onDay(all, reminders, selectedDay, true);
        for (Item it : items) body.addView(itemRow(c, it, now, false));
        if (items.isEmpty()) {
            TextView t = Ui.text(c, 16, Fonts.regular(), p.fg2);
            t.setText(past ? "Ništa nije bilo planirano." : "Za ovaj dan još nema ništa.");
            t.setPadding(0, Ui.dp(c, 14), 0, 0);
            body.addView(t);
        }
        if (!past) {
            TextView add = Ui.button(c, p, "Dodaj plan za ovaj dan", true, 17);
            Drawable plus = c.getResources().getDrawable(R.drawable.ic_plus).mutate();
            plus.setColorFilter(p.accentFg, PorterDuff.Mode.SRC_IN);
            plus.setBounds(0, 0, Ui.dp(c, 22), Ui.dp(c, 22));
            add.setCompoundDrawables(plus, null, null, null);
            add.setCompoundDrawablePadding(Ui.dp(c, 8));
            final long day = selectedDay;
            add.setOnClickListener(v -> openEditor(null, day));
            LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(c, 50));
            al.topMargin = Ui.dp(c, 8);
            panel.addView(add, al);
        }
        return panel;
    }

    // ---- shared pieces ----

    private View header(Context c, String label, String button, Runnable action) {
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.label(c, p, label), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView b = Ui.button(c, p, button, true, 15);
        Drawable plus = c.getResources().getDrawable(R.drawable.ic_plus).mutate();
        plus.setColorFilter(p.accentFg, PorterDuff.Mode.SRC_IN);
        plus.setBounds(0, 0, Ui.dp(c, 18), Ui.dp(c, 18));
        b.setCompoundDrawables(plus, null, null, null);
        b.setCompoundDrawablePadding(Ui.dp(c, 6));
        b.setOnClickListener(v -> action.run());
        head.addView(b, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(c, 40)));
        return head;
    }

    private LinearLayout scrollBody(Context c, LinearLayout panel) {
        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        panel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return body;
    }

    private View dayHeading(Context c, long now, long day, boolean first) {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(day);
        int off = Fmt.dayOffset(now, day);
        String text = off == 0 ? "danas" : off == 1 ? "sutra" : BsDate.weekday(k) + " " + Fmt.dayMonth(day);
        TextView h = Ui.label(c, p, text);
        h.setTextColor(off == 0 ? p.accent : p.fg2);
        h.setPadding(0, Ui.dp(c, first ? 10 : 16), 0, Ui.dp(c, 2));
        return h;
    }

    /** A holiday or an entry; entries open in the editor. withDate: a date column instead of the time. */
    private View itemRow(Context c, final Item it, long now, boolean withDate) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(c, 6), Ui.dp(c, 7), Ui.dp(c, 6), Ui.dp(c, 7));
        Holidays.Day d = it.holiday;
        Reminder r = it.reminder;

        TextView lead;
        if (withDate) {
            Calendar k = Calendar.getInstance();
            k.setTimeInMillis(it.at);
            LinearLayout date = new LinearLayout(c);
            date.setOrientation(LinearLayout.VERTICAL);
            date.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView num = Ui.text(c, 22, Fonts.mono(), d == null ? p.accent : d.state ? p.alert : p.fg);
            num.setText(String.valueOf(k.get(Calendar.DAY_OF_MONTH)));
            date.addView(num);
            date.addView(Ui.label(c, p, BsDate.month(k).substring(0, 3)));
            row.addView(date, new LinearLayout.LayoutParams(Ui.dp(c, 58), LinearLayout.LayoutParams.WRAP_CONTENT));
        } else {
            if (d != null) {
                lead = Ui.text(c, 12, Fonts.medium(), d.state ? p.alert : p.warn);
                lead.setText(d.state ? "PRAZNIK" : "VJERSKI");
            } else if (r.allDay) {
                lead = Ui.text(c, 12, Fonts.medium(), p.fg2);
                lead.setText("CIJELI DAN");
                lead.setMaxLines(2);
            } else {
                lead = Ui.text(c, 18, Fonts.monoMedium(), p.fg);
                lead.setText(NarrowSpan.numeric(Fmt.hhmm(it.at)));
            }
            row.addView(lead, new LinearLayout.LayoutParams(Ui.dp(c, 78), LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.text(c, 17, Fonts.medium(), p.fg);
        name.setText(d != null ? d.name : r.text);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(name);
        TextView kind = Ui.text(c, 13.5f, Fonts.regular(), p.fg2);
        if (d != null) {
            kind.setText(d.state ? "neradni dan" : d.provisional ? "vjerski praznik, okviran datum" : "vjerski praznik, za vjernike");
        } else {
            kind.setText(ReminderText.details(r, false));
        }
        kind.setSingleLine(true);
        kind.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        kl.topMargin = Ui.dp(c, 2);
        mid.addView(kind, kl);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        ml.leftMargin = Ui.dp(c, 6);
        ml.rightMargin = Ui.dp(c, 8);
        row.addView(mid, ml);

        if (withDate) {
            TextView cd = Ui.text(c, 15, Fonts.regular(), p.fg2);
            cd.setText(Holidays.countdown(Holidays.daysUntil(now, Fmt.startOfDay(it.at))));
            row.addView(cd);
        } else if (r != null) {
            row.addView(Ui.icon(c, ReminderText.icon(r), p.fg2, 20));
        }
        if (r != null) {
            final Reminder target = r;
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[] {android.R.attr.state_pressed}, Ui.rounded(c, p.soft, 0, 6));
            bg.addState(new int[0], Ui.rounded(c, 0x00000000, 0, 6));
            row.setBackgroundDrawable(bg);
            row.setClickable(true);
            row.setOnClickListener(v -> openEditor(target, 0));
        }
        return row;
    }

    // ---- editor in the right panel ----

    /** Edits an entry, or (existing null) starts a whole-day plan on the given day. */
    private void openEditor(Reminder existing, long day) {
        Reminder r = existing;
        if (r == null) {
            Calendar k = Calendar.getInstance();
            k.setTimeInMillis(day);
            r = new Reminder();
            r.year = k.get(Calendar.YEAR);
            r.month = k.get(Calendar.MONTH) + 1;
            r.day = k.get(Calendar.DAY_OF_MONTH);
            r.setMode(Reminder.MODE_ALL_DAY);
        }
        side.removeAllViews();
        editor = new ReminderEditor(getContext(), p, r, this);
        side.addView(editor, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    @Override
    public void onEditorClosed(long savedId) {
        editor = null;
        rebuild();
    }
}
