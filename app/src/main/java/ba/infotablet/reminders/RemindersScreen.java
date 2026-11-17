package ba.infotablet.reminders;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

import ba.infotablet.R;
import ba.infotablet.ui.Busy;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Freshness;
import ba.infotablet.ui.NarrowSpan;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Refreshable;
import ba.infotablet.ui.TrackingSpan;
import ba.infotablet.ui.Ui;
import ba.infotablet.util.Fmt;

/** Podsjetnici: every reminder on the left, the editor (or how-to) on the right. */
public final class RemindersScreen extends FrameLayout implements Refreshable, Busy, ReminderEditor.Listener {

    private final Palette p;
    private final TextView count;
    private final LinearLayout list;
    private final FrameLayout side;
    private ReminderEditor editor;
    private long editingId = -1;   // -1 none, 0 new
    private String shown = "";

    public RemindersScreen(Context c, Palette p) {
        super(c);
        this.p = p;
        int pad = Ui.dp(c, 12);
        LinearLayout root = new LinearLayout(c);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setBackgroundDrawable(Ui.panel(c, p));
        left.setPadding(Ui.dp(c, 16), Ui.dp(c, 13), Ui.dp(c, 16), Ui.dp(c, 4));
        LinearLayout head = new LinearLayout(c);
        head.addView(Ui.label(c, p, "Podsjetnici"), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        count = Ui.label(c, p, "");
        head.addView(count);
        left.addView(head);

        TextView add = Ui.button(c, p, "Novi podsjetnik", true, 17);
        Drawable plus = c.getResources().getDrawable(R.drawable.ic_plus).mutate();
        plus.setColorFilter(p.accentFg, PorterDuff.Mode.SRC_IN);
        plus.setBounds(0, 0, Ui.dp(c, 22), Ui.dp(c, 22));
        add.setCompoundDrawables(plus, null, null, null);
        add.setCompoundDrawablePadding(Ui.dp(c, 8));
        add.setOnClickListener(v -> openEditor(null));
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(c, 50));
        al.topMargin = Ui.dp(c, 10);
        al.bottomMargin = Ui.dp(c, 6);
        left.addView(add, al);

        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        left.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        side = new FrameLayout(c);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Ui.dp(c, 452), LinearLayout.LayoutParams.MATCH_PARENT);
        sl.leftMargin = Ui.dp(c, 10);
        root.addView(side, sl);
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        showHelp();
    }

    @Override
    public boolean busy() {
        return editor != null && editor.dirty();
    }

    @Override
    public void refresh(long now) {
        String key = ReminderStore.version() + "/" + editingId + "/" + (now / 60_000L);
        if (key.equals(shown)) return;
        shown = key;
        bindList(now);
    }

    private void bindList(long now) {
        Context c = getContext();
        list.removeAllViews();
        List<Agenda.Entry> all = Agenda.all(ReminderStore.all(c), now);
        int open = 0;
        boolean doneHeader = false;
        for (int i = 0; i < all.size(); i++) {
            Agenda.Entry e = all.get(i);
            boolean finished = Agenda.finished(e.r, now);
            if (!finished) open++;
            if (finished && !doneHeader) {
                doneHeader = true;
                TextView h = Ui.label(c, p, "Završeno");
                h.setPadding(0, Ui.dp(c, 16), 0, Ui.dp(c, 4));
                list.addView(h);
            } else if (i > 0) {
                list.addView(Ui.hairline(c, p.soft), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1))));
            }
            list.addView(row(c, e, finished, now), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 62)));
        }
        count.setText(TrackingSpan.of(open == 0 ? "" : Fmt.plural(open, "aktivan", "aktivna", "aktivnih").toUpperCase(Locale.ROOT), 0.08f));
        if (all.isEmpty()) {
            TextView t = Ui.text(c, 16, Fonts.regular(), p.fg2);
            t.setText("Još nema podsjetnika.");
            t.setPadding(0, Ui.dp(c, 18), 0, 0);
            list.addView(t);
        }
    }

    private View row(Context c, final Agenda.Entry e, boolean finished, long now) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        int side = Ui.dp(c, 8);
        r.setPadding(side, 0, side, 0);
        if (e.r.id == editingId) r.setBackgroundDrawable(Ui.rounded(c, p.accentTint, 0, 6));
        boolean waiting = e.state == Agenda.State.RINGING || e.state == Agenda.State.MISSED;

        LinearLayout when = new LinearLayout(c);
        when.setOrientation(LinearLayout.VERTICAL);
        TextView time = Ui.text(c, 19, Fonts.monoMedium(), waiting ? p.alert : e.state == Agenda.State.SNOOZED ? p.warn : p.fg);
        time.setText(NarrowSpan.numeric(Fmt.hhmm(e.at)));
        when.addView(time);
        TextView day = Ui.text(c, 11.5f, Fonts.medium(), p.fg2);
        String d = finished ? Fmt.dayMonth(e.at) : Fmt.relDay(now, e.at);
        day.setText(TrackingSpan.of(d.toUpperCase(Locale.ROOT), 0.06f));
        day.setSingleLine(true);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dl.topMargin = Ui.dp(c, 4);
        when.addView(day, dl);
        r.addView(when, new LinearLayout.LayoutParams(Ui.dp(c, 92), LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(LinearLayout.VERTICAL);
        TextView text = Ui.text(c, 17, Fonts.medium(), p.fg);
        text.setText(e.r.text);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(text);
        TextView details = Ui.text(c, 13.5f, Fonts.regular(), waiting ? p.alert : p.fg2);
        String note = e.state == Agenda.State.MISSED ? "propušteno · " : e.state == Agenda.State.SNOOZED
                ? "odgođeno do " + Fmt.hhmm(e.r.snoozeUntil) + " · " : "";
        details.setText(note + ReminderText.details(e.r));
        details.setSingleLine(true);
        details.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.topMargin = Ui.dp(c, 4);
        mid.addView(details, tl);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        ml.rightMargin = Ui.dp(c, 10);
        r.addView(mid, ml);

        if (waiting || e.state == Agenda.State.SNOOZED) {
            TextView done = Ui.button(c, p, "Gotovo", false, 15);
            done.setOnClickListener(v -> ReminderStore.done(getContext(), e.r.id, ba.infotablet.util.Clock.now()));
            r.addView(done, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(c, 40)));
        } else {
            r.addView(Ui.icon(c, ReminderText.icon(e.r.category), p.fg2, 22));
        }
        if (finished) r.setAlpha(Freshness.STALE_ALPHA);
        r.setClickable(true);
        r.setOnClickListener(v -> openEditor(e.r));
        return r;
    }

    private void openEditor(Reminder r) {
        editingId = r == null ? 0 : r.id;
        side.removeAllViews();
        editor = new ReminderEditor(getContext(), p, r, this);
        side.addView(editor, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        shown = "";
        bindList(ba.infotablet.util.Clock.now());
    }

    @Override
    public void onEditorClosed(long savedId) {
        editingId = -1;
        editor = null;
        showHelp();
        shown = "";
        refresh(ba.infotablet.util.Clock.now());
    }

    private void showHelp() {
        Context c = getContext();
        side.removeAllViews();
        LinearLayout help = new LinearLayout(c);
        help.setOrientation(LinearLayout.VERTICAL);
        help.setBackgroundDrawable(Ui.panel(c, p));
        help.setPadding(Ui.dp(c, 18), Ui.dp(c, 13), Ui.dp(c, 18), Ui.dp(c, 14));
        help.addView(Ui.label(c, p, "Kako radi"));
        help.addView(step(c, "1", "Dodirnite „Novi podsjetnik“ ili postojeći podsjetnik da ga promijenite."));
        help.addView(step(c, "2", "U zadano vrijeme tablet zvoni i prikaže podsjetnik preko cijelog ekrana."));
        help.addView(step(c, "3", "Dodirnite „Gotovo“ kad završite, ili „Odgodi 10 min“ da se podsjetnik javi ponovo."));
        help.addView(step(c, "4", "Ako niko ne odgovori, javlja se još dva puta, a zatim ostaje na početnom ekranu kao propušten."));
        side.addView(help, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private View step(Context c, String n, String s) {
        LinearLayout l = new LinearLayout(c);
        TextView num = Ui.text(c, 16, Fonts.monoMedium(), p.accent);
        num.setText(n);
        l.addView(num, new LinearLayout.LayoutParams(Ui.dp(c, 26), LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView t = Ui.text(c, 16, Fonts.regular(), p.fg);
        t.setText(s);
        t.setLineSpacing(Ui.dp(c, 3), 1f);
        l.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        l.setPadding(0, Ui.dp(c, 14), 0, 0);
        return l;
    }
}
