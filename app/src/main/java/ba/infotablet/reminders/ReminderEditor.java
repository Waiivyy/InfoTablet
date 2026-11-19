package ba.infotablet.reminders;

import android.content.Context;
import android.graphics.drawable.StateListDrawable;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

import ba.infotablet.R;
import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.HoldRepeat;
import ba.infotablet.ui.NarrowSpan;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Ui;
import ba.infotablet.util.BsDate;
import ba.infotablet.util.Clock;

/**
 * Add or change one reminder: text, date, time (5-minute steps), repeat and kind. Big buttons
 * instead of the small system pickers; holding a button repeats it.
 */
public final class ReminderEditor extends LinearLayout {

    public interface Listener {
        void onEditorClosed(long savedId);
    }

    private static final int MAX_TEXT = 120;
    private static final long CONFIRM_MS = 4000L;

    private final Palette p;
    private final Listener listener;
    private final Reminder draft;
    private final boolean isNew;

    private final EditText text;
    private final TextView error, date, hour, minute;
    private final TextView[] repeatButtons = new TextView[Reminder.REPEAT_SHORT.length];
    private final TextView[] modeButtons = new TextView[Reminder.MODE_SHORT.length];
    private final TextView heading, allDayNote;
    private final LinearLayout timeSteps;
    private final View[] kindButtons = new View[Reminder.CATEGORIES.length];
    private TextView delete;
    private boolean confirmDelete;

    public ReminderEditor(Context c, Palette p, Reminder existing, Listener listener) {
        super(c);
        this.p = p;
        this.listener = listener;
        this.isNew = existing == null || existing.id == 0;   // id 0: a new entry with preset values
        this.draft = existing == null ? fresh(Clock.now()) : existing.copy();
        setOrientation(VERTICAL);
        setBackgroundDrawable(Ui.panel(c, p));
        setPadding(Ui.dp(c, 16), Ui.dp(c, 13), Ui.dp(c, 16), Ui.dp(c, 14));
        // Takes the focus when the text field lets go of it, so the keyboard does not pop back up.
        setFocusableInTouchMode(true);
        final float inputSize = Ui.size(19);   // what is typed keeps the full size
        Ui.capBegin(1.1f);   // the date, repeat and category buttons have fixed widths
        try {
            LinearLayout head = new LinearLayout(c);
            heading = Ui.label(c, p, "");
            head.addView(heading, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            error = Ui.text(c, 13, Fonts.medium(), p.alert);
            error.setSingleLine(true);
            head.addView(error);
            addView(head);

            text = (EditText) LayoutInflater.from(c).inflate(R.layout.edit_text, this, false);
            text.setId(View.generateViewId());
            heading.setLabelFor(text.getId());
            text.setTextSize(inputSize);
            text.setTypeface(Fonts.regular());
            text.setTextColor(p.fg);
            text.setHintTextColor(p.fg3);
            text.setHighlightColor(p.accentTint);
            text.setHint("Npr. Lijek za pritisak");
            text.setFilters(new InputFilter[] {new InputFilter.LengthFilter(MAX_TEXT)});
            StateListDrawable field = new StateListDrawable();
            field.addState(new int[] {android.R.attr.state_focused}, Ui.rounded(c, p.panel, p.accent, 8));
            field.addState(new int[0], Ui.rounded(c, p.panel, p.line, 8));
            text.setBackgroundDrawable(field);
            text.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0);
            text.setText(draft.text);
            text.setOnEditorActionListener((v, actionId, ev) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) hideKeyboard();
                return false;
            });
            LayoutParams tl = new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 50));
            tl.topMargin = Ui.dp(c, 10);
            addView(text, tl);

            // Date: month and day steps around the full date.
            addView(section(c, "Datum"));
            LinearLayout dateRow = new LinearLayout(c);
            dateRow.setGravity(Gravity.CENTER_VERTICAL);
            dateRow.addView(step(c, R.drawable.ic_chevrons_left, () -> moveDate(Calendar.MONTH, -1)), square(c, 0));
            dateRow.addView(step(c, R.drawable.ic_chevron_left, () -> moveDate(Calendar.DAY_OF_MONTH, -1)), square(c, 6));
            date = Ui.text(c, 18, Fonts.medium(), p.fg);
            date.setGravity(Gravity.CENTER);
            date.setSingleLine(true);
            dateRow.addView(date, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            dateRow.addView(step(c, R.drawable.ic_chevron_right, () -> moveDate(Calendar.DAY_OF_MONTH, 1)), square(c, 0));
            dateRow.addView(step(c, R.drawable.ic_chevrons_right, () -> moveDate(Calendar.MONTH, 1)), square(c, 6));
            addView(dateRow);

            // Time: rings, silent or the whole day; then hours and minutes (5-minute steps).
            LinearLayout timeHead = new LinearLayout(c);
            timeHead.setGravity(Gravity.CENTER_VERTICAL);
            timeHead.setPadding(0, Ui.dp(c, 9), 0, Ui.dp(c, 6));
            timeHead.addView(Ui.label(c, p, "Vrijeme"), new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            for (int i = 0; i < modeButtons.length; i++) {
                final int value = i;
                TextView b = Ui.text(c, 13.5f, Fonts.medium(), p.fg);
                b.setText(Reminder.MODE_SHORT[i]);
                b.setGravity(Gravity.CENTER);
                b.setSingleLine(true);
                b.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0);
                b.setClickable(true);
                b.setOnClickListener(v -> {
                    hideKeyboard();
                    draft.setMode(value);
                    render();
                });
                modeButtons[i] = b;
                LayoutParams bl = new LayoutParams(LayoutParams.WRAP_CONTENT, Ui.dp(c, 34));
                bl.leftMargin = Ui.dp(c, 6);
                timeHead.addView(b, bl);
            }
            addView(timeHead);
            LinearLayout timeRow = new LinearLayout(c);
            timeRow.setGravity(Gravity.CENTER_VERTICAL);
            timeSteps = timeRow;
            timeRow.addView(step(c, R.drawable.ic_minus, () -> moveTime(-60)), square(c, 0));
            hour = number(c);
            timeRow.addView(hour, new LayoutParams(Ui.dp(c, 64), LayoutParams.WRAP_CONTENT));
            timeRow.addView(step(c, R.drawable.ic_plus, () -> moveTime(60)), square(c, 0));
            TextView colon = Ui.text(c, 30, Fonts.monoMedium(), p.fg2);
            colon.setText(":");
            colon.setGravity(Gravity.CENTER);
            timeRow.addView(colon, new LayoutParams(Ui.dp(c, 28), LayoutParams.WRAP_CONTENT));
            timeRow.addView(step(c, R.drawable.ic_minus, () -> moveTime(-5)), square(c, 0));
            minute = number(c);
            timeRow.addView(minute, new LayoutParams(Ui.dp(c, 64), LayoutParams.WRAP_CONTENT));
            timeRow.addView(step(c, R.drawable.ic_plus, () -> moveTime(5)), square(c, 0));
            android.widget.FrameLayout timeBox = new android.widget.FrameLayout(c);
            timeBox.addView(timeRow, new android.widget.FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 48)));
            allDayNote = Ui.text(c, 16, Fonts.regular(), p.fg2);
            allDayNote.setText("Za cijeli dan, bez zvona. Vidi se na početnom ekranu i u kalendaru.");
            allDayNote.setGravity(Gravity.CENTER_VERTICAL);
            timeBox.addView(allDayNote, new android.widget.FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 48)));
            addView(timeBox, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 48)));

            addView(section(c, "Ponavljanje"));
            LinearLayout repeatRow = new LinearLayout(c);
            for (int i = 0; i < repeatButtons.length; i++) {
                final int value = i;
                TextView b = Ui.text(c, 14.5f, Fonts.medium(), p.fg);
                b.setText(Reminder.REPEAT_SHORT[i]);
                b.setGravity(Gravity.CENTER);
                b.setSingleLine(true);
                b.setClickable(true);
                b.setOnClickListener(v -> {
                    hideKeyboard();
                    draft.repeat = value;
                    render();
                });
                repeatButtons[i] = b;
                LayoutParams bl = new LayoutParams(0, Ui.dp(c, 42), 1f);
                if (i > 0) bl.leftMargin = Ui.dp(c, 6);
                repeatRow.addView(b, bl);
            }
            addView(repeatRow);

            addView(section(c, "Vrsta"));
            LinearLayout kindRow = new LinearLayout(c);
            for (int i = 0; i < kindButtons.length; i++) {
                final String key = Reminder.CATEGORIES[i];
                LinearLayout b = new LinearLayout(c);
                b.setOrientation(VERTICAL);
                b.setGravity(Gravity.CENTER);
                b.setClickable(true);
                b.setOnClickListener(v -> {
                    hideKeyboard();
                    draft.category = key;
                    render();
                });
                b.addView(Ui.icon(c, ReminderText.icon(key), p.fg2, 22));
                TextView name = Ui.text(c, 12, Fonts.medium(), p.fg);
                name.setText(key.isEmpty() ? "Ostalo" : Reminder.categoryName(key));
                name.setSingleLine(true);
                LayoutParams nl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                nl.topMargin = Ui.dp(c, 4);
                b.addView(name, nl);
                kindButtons[i] = b;
                LayoutParams bl = new LayoutParams(0, Ui.dp(c, 58), 1f);
                if (i > 0) bl.leftMargin = Ui.dp(c, 6);
                kindRow.addView(b, bl);
            }
            addView(kindRow);

            addView(new View(c), new LayoutParams(1, 0, 1f));

            LinearLayout actions = new LinearLayout(c);
            if (!isNew) {
                delete = Ui.button(c, p, "Obriši", false, 16);
                delete.setTextColor(p.alert);
                delete.setOnClickListener(v -> onDelete());
                actions.addView(delete, new LayoutParams(LayoutParams.WRAP_CONTENT, Ui.dp(c, 50)));
            }
            actions.addView(new View(c), new LayoutParams(0, 1, 1f));
            TextView cancel = Ui.button(c, p, "Odustani", false, 16);
            cancel.setOnClickListener(v -> close(0));
            actions.addView(cancel, new LayoutParams(LayoutParams.WRAP_CONTENT, Ui.dp(c, 50)));
            TextView save = Ui.button(c, p, "Sačuvaj", true, 17);
            save.setOnClickListener(v -> onSave());
            LayoutParams sl = new LayoutParams(Ui.dp(c, 132), Ui.dp(c, 50));
            sl.leftMargin = Ui.dp(c, 10);
            actions.addView(save, sl);
            LayoutParams al = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            al.topMargin = Ui.dp(c, Ui.largerText() ? 6 : 10);
            addView(actions, al);

            render();
            if (isNew) post(() -> {
                text.requestFocus();
                InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(text, InputMethodManager.SHOW_IMPLICIT);
            });
        } finally {
            Ui.capEnd();
        }
    }

    /** True while the editor holds input that has not been saved. */
    public boolean dirty() {
        return isNew ? text.getText().toString().trim().length() > 0 : true;
    }

    /** Today at the next full hour (tomorrow 00:00 late in the evening). */
    static Reminder fresh(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.add(Calendar.HOUR_OF_DAY, 1);
        Reminder r = new Reminder();
        r.year = c.get(Calendar.YEAR);
        r.month = c.get(Calendar.MONTH) + 1;
        r.day = c.get(Calendar.DAY_OF_MONTH);
        r.hour = c.get(Calendar.HOUR_OF_DAY);
        r.minute = 0;
        return r;
    }

    private View section(Context c, String title) {
        TextView l = Ui.label(c, p, title);
        l.setPadding(0, Ui.dp(c, Ui.largerText() ? 11 : 13), 0, Ui.dp(c, 6));   // larger text: tighter, so Sačuvaj fits
        return l;
    }

    private TextView number(Context c) {
        TextView t = Ui.text(c, 30, Fonts.monoMedium(), p.fg);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private View step(Context c, int icon, Runnable action) {
        LinearLayout b = new LinearLayout(c);
        b.setGravity(Gravity.CENTER);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_pressed}, Ui.rounded(c, p.soft, p.line, 8));
        s.addState(new int[0], Ui.rounded(c, p.panel, p.line, 8));
        b.setBackgroundDrawable(s);
        b.setClickable(true);
        ImageView i = Ui.icon(c, icon, p.fg, 22);
        b.addView(i);
        HoldRepeat.attach(b, () -> {
            hideKeyboard();
            action.run();
        });
        return b;
    }

    private LayoutParams square(Context c, int leftMarginDp) {
        LayoutParams l = new LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48));
        l.leftMargin = Ui.dp(c, leftMarginDp);
        return l;
    }

    private void moveDate(int field, int amount) {
        Calendar k = Calendar.getInstance();
        k.clear();
        k.set(draft.year, draft.month - 1, draft.day);
        k.add(field, amount);
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(Clock.now());
        if (k.get(Calendar.YEAR) * 400 + k.get(Calendar.DAY_OF_YEAR) < today.get(Calendar.YEAR) * 400 + today.get(Calendar.DAY_OF_YEAR)) {
            k = today; // no dates in the past
        }
        draft.year = k.get(Calendar.YEAR);
        draft.month = k.get(Calendar.MONTH) + 1;
        draft.day = k.get(Calendar.DAY_OF_MONTH);
        render();
    }

    /** Minutes wrap within the hour and hours within the day, like a clock radio. */
    private void moveTime(int minutes) {
        if (Math.abs(minutes) == 60) {
            draft.hour = (draft.hour + (minutes > 0 ? 1 : 23)) % 24;
        } else {
            int m = draft.minute - draft.minute % 5;
            draft.minute = (m + (minutes > 0 ? 5 : 55)) % 60;
        }
        render();
    }

    private void render() {
        Context c = getContext();
        Calendar k = Calendar.getInstance();
        k.clear();
        k.set(draft.year, draft.month - 1, draft.day);
        date.setText(BsDate.capitalize(BsDate.weekday(k)) + ", " + draft.day + "." + draft.month + "." + draft.year + ".");
        hour.setText(NarrowSpan.numeric(String.format(java.util.Locale.ROOT, "%02d", draft.hour)));
        minute.setText(NarrowSpan.numeric(String.format(java.util.Locale.ROOT, "%02d", draft.minute)));
        boolean allDay = draft.allDay;
        timeSteps.setVisibility(allDay ? INVISIBLE : VISIBLE);
        allDayNote.setVisibility(allDay ? VISIBLE : GONE);
        for (int i = 0; i < modeButtons.length; i++) {
            boolean on = draft.mode() == i;
            modeButtons[i].setBackgroundDrawable(toggle(c, on));
            modeButtons[i].setTextColor(on ? p.accentFg : p.fg);
        }
        boolean plan = !draft.ring;
        heading.setText(ba.infotablet.ui.TrackingSpan.of((isNew ? (plan ? "Novi plan" : "Novi podsjetnik")
                : (plan ? "Uredi plan" : "Uredi podsjetnik")).toUpperCase(java.util.Locale.ROOT), 0.08f));
        for (int i = 0; i < repeatButtons.length; i++) {
            boolean on = draft.repeat == i;
            repeatButtons[i].setBackgroundDrawable(toggle(c, on));
            repeatButtons[i].setTextColor(on ? p.accentFg : p.fg);
        }
        for (int i = 0; i < kindButtons.length; i++) {
            boolean on = draft.category.equals(Reminder.CATEGORIES[i]);
            kindButtons[i].setBackgroundDrawable(toggle(c, on));
            LinearLayout b = (LinearLayout) kindButtons[i];
            ((ImageView) b.getChildAt(0)).getDrawable().mutate().setColorFilter(on ? p.accentFg : p.fg2, android.graphics.PorterDuff.Mode.SRC_IN);
            ((TextView) b.getChildAt(1)).setTextColor(on ? p.accentFg : p.fg);
        }
        error.setText("");
    }

    private StateListDrawable toggle(Context c, boolean on) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_pressed}, Ui.rounded(c, on ? p.accent : p.soft, on ? 0 : p.line, 8));
        s.addState(new int[0], Ui.rounded(c, on ? p.accent : p.panel, on ? 0 : p.line, 8));
        return s;
    }

    private void onSave() {
        hideKeyboard();
        String t = text.getText().toString().trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) {
            error.setText("Upišite šta treba zapamtiti");
            text.requestFocus();
            return;
        }
        draft.text = t;
        long now = Clock.now();
        long first = Recurrence.occurrence(draft, 0);
        boolean past = draft.allDay ? first < ba.infotablet.util.Fmt.startOfDay(now) : first <= now;
        if (draft.repeat == Reminder.ONCE && past) {
            error.setText(draft.allDay ? "Ovaj dan je već prošao" : "Ovo vrijeme je već prošlo");
            return;
        }
        long id = ReminderStore.save(getContext(), draft, now);
        close(id);
    }

    private void onDelete() {
        if (!confirmDelete) {
            confirmDelete = true;
            delete.setText("Sigurno obrisati?");
            delete.setBackgroundDrawable(Ui.rounded(getContext(), p.alert, 0, 8));
            delete.setTextColor(0xFFFFFFFF);
            postDelayed(resetDelete, CONFIRM_MS);
            return;
        }
        removeCallbacks(resetDelete);
        ReminderStore.delete(getContext(), draft.id);
        close(0);
    }

    private final Runnable resetDelete = new Runnable() {
        @Override public void run() {
            confirmDelete = false;
            delete.setText("Obriši");
            delete.setBackgroundDrawable(Ui.buttonBackground(getContext(), p, false));
            delete.setTextColor(p.alert);
        }
    };

    private void close(long savedId) {
        hideKeyboard();
        removeCallbacks(resetDelete);
        listener.onEditorClosed(savedId);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(text.getWindowToken(), 0);
        if (text.hasFocus()) requestFocus();
    }
}
