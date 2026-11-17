package ba.infotablet.calendar;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Refreshable;
import ba.infotablet.ui.Ui;
import ba.infotablet.util.BsDate;

/** Kalendar: this month with holidays marked, and the upcoming holidays with countdowns. */
public final class CalendarScreen extends FrameLayout implements Refreshable {

    private static final String[] WEEK = {"pon", "uto", "sri", "čet", "pet", "sub", "ned"};

    private final Palette p;
    private final LinearLayout month, list;
    private String shownDay = "";

    public CalendarScreen(Context c, Palette p) {
        super(c);
        this.p = p;
        int pad = Ui.dp(c, 12);
        LinearLayout root = new LinearLayout(c);
        root.setPadding(pad, pad, pad, pad);
        month = panel(c);
        root.addView(month, new LinearLayout.LayoutParams(Ui.dp(c, 430), LinearLayout.LayoutParams.MATCH_PARENT));
        list = panel(c);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        ll.leftMargin = Ui.dp(c, 10);
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
        String day = today.get(Calendar.YEAR) + "-" + today.get(Calendar.DAY_OF_YEAR);
        if (day.equals(shownDay)) return; // nothing changes within a day
        shownDay = day;
        Context c = getContext();
        List<Holidays.Day> all = Holidays.all(c);
        bindMonth(c, today, all);
        bindList(c, now, all);
    }

    private void bindMonth(Context c, Calendar today, List<Holidays.Day> all) {
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
        legend.addView(legendItem(c, p.alert, "neradni dan"));
        View gap = new View(c);
        legend.addView(gap, new LinearLayout.LayoutParams(Ui.dp(c, 18), 1));
        legend.addView(legendItem(c, p.warn, "vjerski praznik"));
        legend.setPadding(0, Ui.dp(c, 8), 0, 0);
        month.addView(legend);
    }

    private View legendItem(Context c, int color, String text) {
        TextView t = Ui.text(c, 13, Fonts.regular(), color);
        t.setText("■ " + text);
        return t;
    }

    private void bindList(Context c, long now, List<Holidays.Day> all) {
        list.removeAllViews();
        list.addView(Ui.label(c, p, "Praznici i neradni dani"));
        List<Holidays.Day> up = Holidays.upcoming(all, now, 7);
        Calendar k = Calendar.getInstance();
        for (int i = 0; i < up.size(); i++) {
            Holidays.Day d = up.get(i);
            if (i > 0) list.addView(Ui.hairline(c, p.soft), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1))));
            k.setTimeInMillis(d.date);
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout date = new LinearLayout(c);
            date.setOrientation(LinearLayout.VERTICAL);
            date.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView num = Ui.text(c, 24, Fonts.mono(), d.state ? p.alert : p.fg);
            num.setText(String.valueOf(k.get(Calendar.DAY_OF_MONTH)));
            date.addView(num);
            TextView mon = Ui.label(c, p, BsDate.month(k).substring(0, 3));
            date.addView(mon);
            row.addView(date, new LinearLayout.LayoutParams(Ui.dp(c, 54), LinearLayout.LayoutParams.WRAP_CONTENT));

            LinearLayout mid = new LinearLayout(c);
            mid.setOrientation(LinearLayout.VERTICAL);
            TextView name = Ui.text(c, 17, Fonts.medium(), p.fg);
            name.setText(d.name);
            name.setSingleLine(true);
            mid.addView(name);
            TextView kind = Ui.text(c, 13.5f, Fonts.regular(), p.fg2);
            kind.setText(BsDate.capitalize(BsDate.weekday(k)) + " · " + (d.state ? "neradni dan" : d.provisional ? "vjerski praznik, okviran datum" : "vjerski praznik, za vjernike"));
            kind.setSingleLine(true);
            LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            kl.topMargin = Ui.dp(c, 2);
            mid.addView(kind, kl);
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            ml.leftMargin = Ui.dp(c, 8);
            row.addView(mid, ml);

            TextView cd = Ui.text(c, 15, Fonts.regular(), p.fg2);
            cd.setText(Holidays.countdown(Holidays.daysUntil(now, d.date)));
            row.addView(cd);
            list.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        TextView src = Ui.text(c, 12, Fonts.regular(), p.fg2);
        src.setText("Datumi bajrama nakon 2026. su okvirni dok ih Islamska zajednica ne objavi.");
        src.setPadding(0, Ui.dp(c, 6), 0, 0);
        list.addView(src);
    }
}
