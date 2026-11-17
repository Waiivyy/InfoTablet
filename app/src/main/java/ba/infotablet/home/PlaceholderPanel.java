package ba.infotablet.home;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import ba.infotablet.ui.Fonts;
import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Ui;

/** A titled card whose content is not built yet. */
public final class PlaceholderPanel extends LinearLayout {

    public PlaceholderPanel(Context c, Palette p, String title, String note) {
        super(c);
        setOrientation(VERTICAL);
        setBackgroundDrawable(Ui.panel(c, p));
        setPadding(Ui.dp(c, 16), Ui.dp(c, 13), Ui.dp(c, 16), Ui.dp(c, 13));
        addView(Ui.label(c, p, title));
        TextView body = Ui.text(c, 16, Fonts.regular(), p.fg3);
        body.setText(note);
        body.setGravity(Gravity.CENTER);
        addView(body, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }
}
