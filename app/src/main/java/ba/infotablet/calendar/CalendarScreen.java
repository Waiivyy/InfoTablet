package ba.infotablet.calendar;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ba.infotablet.reminders.Agenda;
import ba.infotablet.reminders.Reminder;
import ba.infotablet.reminders.ReminderStore;
import ba.infotablet.reminders.ReminderText;

import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Refreshable;
import ba.infotablet.ui.Ui;
import ba.infotablet.util.BsDate;
import ba.infotablet.util.Fmt;

/**
 * Kalendar: this month with holidays and reminder days marked, and what comes next (holidays,
 * birthdays and other dated reminders) with countdowns. Daily and weekly reminders stay out of
 * the list so it does not fill up with routine.
 */
public final class CalendarScreen extends FrameLayout implements Refreshable {

    private static final String[] WEEK = {"pon", "uto", "sri", "čet", "pet", "sub", "ned"};
    private static final int LIST_ROWS = 7;
    private static final long LIST_HORIZON_MS = 366L * 86_400_000L;

    /** One line of the "what comes next" list: a holiday or a reminder occurrence. */
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
    private final LinearLayout month, list;
    private String shownDay = "";

    public CalendarScreen(Context c, Palette p) {
        super(c);
        this.p = p;
        int pad = Ui.pagePad(c);
        LinearLayout root = new LinearLayout(c);
        root.setPadding(pad, pad, pad, pad);
        month = panel(c);
        root.addView(month, new LinearLayout.LayoutParams(Ui.dp(c, 430), LinearLayout.LayoutParams.MATCH_PARENT));
        list = panel(c);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        ll.leftMargin = Ui.gap(c);
        root.addView(list, ll);
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
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(now);
        String day = today.get(Calendar.YEAR) + "-" + today.get(Calendar.DAY_OF_YEAR) + "/" + ReminderStore.version();
        if (day.equals(shownDay)) return; // nothing changes within a day unless reminders do
        shownDay = day;
        Context c = getContext();
        List<Holidays.Day> all = Holidays.all(c);
        List<Reminder> reminders = ReminderStore.all(c);
        bindMonth(c, today, all, reminders);
        bindList(c, now, all, reminders);
    }

    private void bindMonth(Context c, Calendar today, List<Holidays.Day> all, List<Reminder> reminders) {
        month.removeAllViews();
        month.addView(Ui.label(c, p, BsDate.month(today) + " " + today.get(Calendar.YEAR)));

        Map<Integer, Holidays.Day> marks = new HashMap<Integer, Holidays.Day>();
        Calendar k = Calendar.getInstance();
        for (Holidays.Day d : all) {
            k.setTimeInMillis(d.date);
            if (k.get(Calendar.YEAR) == today.get(Calendar.YEAR) && k.get(Calendar.MONTH) == today.get(Calendar.MONTH)) {
                Holidays.Day prev = marks.get(k.get(Calendar.DAY_OF_MONTH));
                if (prev == null || (d.state && !prev.state)) marks.put(k.get(Calendar.DAY_OF_MONTH), d);
            }
        }

        Set<Integer> reminderDays = new HashSet<Integer>();
        Calendar from = (Calendar) today.clone();
        from.set(Calendar.DAY_OF_MONTH, 1);
        from.set(Calendar.HOUR_OF_DAY, 0);
        from.set(Calendar.MINUTE, 0);
        from.set(Calendar.SECOND, 0);
        from.set(Calendar.MILLISECOND, 0);
        Calendar to = (Calendar) from.clone();
        to.add(Calendar.MONTH, 1);
        for (Reminder r : reminders) {
            if (r.repeat == Reminder.DAILY) continue; // would mark every day
            for (long t : Agenda.occurrences(r, from.getTimeInMillis(), to.getTimeInMillis(), 31)) {
                k.setTimeInMillis(t);
                reminderDays.add(k.get(Calendar.DAY_OF_MONTH));
            }
        }

        LinearLayout head = new LinearLayout(c);
        for (String w : WEEK) {
            TextView t = Ui.label(c, p, w);
            t.setGravity(Gravity.CENTER);
            head.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hl.topMargin = Ui.dp(c, 12);
        hl.bottomMargin = Ui.dp(c, 6);
        month.addView(head, hl);

        Calendar first = (Calendar) today.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        int offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Monday first
        int days = today.getActualMaximum(Calendar.DAY_OF_MONTH);
        int dayNum = 1 - offset;
        for (int week = 0; week < 6; week++) {
            LinearLayout row = new LinearLayout(c);
            for (int col = 0; col < 7; col++, dayNum++) {
                TextView cell = Ui.text(c, 18, Fonts.mono(), p.fg);
                cell.setGravity(Gravity.CENTER);
                if (dayNum >= 1 && dayNum <= days) {
                    cell.setText(String.valueOf(dayNum));
                    Holidays.Day h = marks.get(dayNum);
                    boolean isToday = dayNum == today.get(Calendar.DAY_OF_MONTH);
                    if (isToday) {
                        cell.setBackgroundDrawable(Ui.rounded(c, p.accent, 0, 8));
                        cell.setTextColor(p.accentFg);
                    } else if (h != null && h.state) {
                        cell.setBackgroundDrawable(Ui.rounded(c, 0x00000000, p.alert, 8));
                        cell.setTextColor(p.alert);
                    } else if (h != null) {
                        cell.setTextColor(p.warn);
                    } else if (col >= 5) {
                        cell.setTextColor(p.fg2);
                    }
                    if (reminderDays.contains(dayNum)) {
                        cell.setCompoundDrawablesWithIntrinsicBounds(null, null, null, dot(c, isToday ? p.accentFg : p.accent));
                        cell.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 4));
                        cell.setCompoundDrawablePadding(Ui.dp(c, 2));
                    }
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
        legend.addView(legendItem(c, dot(c, p.accent), 6, "podsjetnik", p.accent));
        legend.setPadding(0, Ui.dp(c, 8), 0, 0);
        month.addView(legend);
    }

    /** A legend entry with a drawn swatch (the same shape as in the grid). */
    private View legendItem(Context c, android.graphics.drawable.Drawable swatch, int sizeDp, String text, int color) {
        TextView t = Ui.text(c, 13, Fonts.regular(), color);
        t.setText(text);
        swatch.setBounds(0, 0, Ui.dp(c, sizeDp), Ui.dp(c, sizeDp));
        t.setCompoundDrawables(swatch, null, null, null);
        t.setCompoundDrawablePadding(Ui.dp(c, 6));
        t.setPadding(0, 0, Ui.dp(c, 18), 0);
        return t;
    }

    private static GradientDrawable dot(Context c, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        d.setSize(Ui.dp(c, 5), Ui.dp(c, 5));
        return d;
    }

    /** Holidays and dated reminders (one-time, monthly, yearly) from today on, by date. */
    private static List<Item> upcoming(List<Holidays.Day> all, List<Reminder> reminders, long now) {
        List<Item> out = new ArrayList<Item>();
        for (Holidays.Day d : Holidays.upcoming(all, now, LIST_ROWS)) out.add(new Item(d.date, d, null));
        for (Reminder r : reminders) {
            if (r.repeat == Reminder.DAILY || r.repeat == Reminder.WEEKLY) continue;
            long due = Agenda.due(r, now);
            long at = due != Agenda.NONE ? due : Agenda.next(r, now);
            if (at != Agenda.NONE && at - now <= LIST_HORIZON_MS) out.add(new Item(at, null, r));
        }
        Collections.sort(out, (a, b) -> {
            long da = Fmt.startOfDay(a.at), db = Fmt.startOfDay(b.at);
            if (da != db) return da < db ? -1 : 1;
            if ((a.holiday == null) != (b.holiday == null)) return a.holiday != null ? -1 : 1; // holiday first on a day
            return a.at < b.at ? -1 : (a.at > b.at ? 1 : 0);
        });
        return out.size() > LIST_ROWS ? out.subList(0, LIST_ROWS) : out;
    }

    private void bindList(Context c, long now, List<Holidays.Day> all, List<Reminder> reminders) {
        list.removeAllViews();
        List<Item> up = upcoming(all, reminders, now);
        boolean anyReminder = false;
        for (Item it : up) anyReminder |= it.reminder != null;
        list.addView(Ui.label(c, p, anyReminder ? "Praznici i podsjetnici" : "Praznici i neradni dani"));
        Calendar k = Calendar.getInstance();
        for (int i = 0; i < up.size(); i++) {
            Item it = up.get(i);
            Holidays.Day d = it.holiday;
            if (i > 0) list.addView(Ui.hairline(c, p.soft), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1))));
            k.setTimeInMillis(it.at);
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout date = new LinearLayout(c);
            date.setOrientation(LinearLayout.VERTICAL);
            date.setGravity(Gravity.CENTER_HORIZONTAL);
            int numColor = d == null ? p.accent : d.state ? p.alert : p.fg;
            TextView num = Ui.text(c, 24, Fonts.mono(), numColor);
            num.setText(String.valueOf(k.get(Calendar.DAY_OF_MONTH)));
            date.addView(num);
            TextView mon = Ui.label(c, p, BsDate.month(k).substring(0, 3));
            date.addView(mon);
            row.addView(date, new LinearLayout.LayoutParams(Ui.dp(c, 54), LinearLayout.LayoutParams.WRAP_CONTENT));

            LinearLayout mid = new LinearLayout(c);
            mid.setOrientation(LinearLayout.VERTICAL);
            TextView name = Ui.text(c, 17, Fonts.medium(), p.fg);
            name.setText(d != null ? d.name : it.reminder.text);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            mid.addView(name);
            TextView kind = Ui.text(c, 13.5f, Fonts.regular(), p.fg2);
            String weekday = BsDate.capitalize(BsDate.weekday(k));
            if (d != null) {
                kind.setText(weekday + " · " + (d.state ? "neradni dan" : d.provisional ? "vjerski praznik, okviran datum" : "vjerski praznik, za vjernike"));
            } else {
                kind.setText(weekday + " " + Fmt.hhmm(it.at) + " · " + ReminderText.details(it.reminder));
            }
            kind.setSingleLine(true);
            kind.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            kl.topMargin = Ui.dp(c, 2);
            mid.addView(kind, kl);
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            ml.leftMargin = Ui.dp(c, 8);
            ml.rightMargin = Ui.dp(c, 8);
            row.addView(mid, ml);

            TextView cd = Ui.text(c, 15, Fonts.regular(), p.fg2);
            cd.setText(Holidays.countdown(Holidays.daysUntil(now, Fmt.startOfDay(it.at))));
            row.addView(cd);
            list.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        TextView src = Ui.text(c, 12, Fonts.regular(), p.fg2);
        src.setText("Datumi bajrama nakon 2026. su okvirni dok ih Islamska zajednica ne objavi.");
        src.setPadding(0, Ui.dp(c, 6), 0, 0);
        list.addView(src);
    }
}
