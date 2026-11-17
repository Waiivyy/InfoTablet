package ba.infotablet.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Small view factory so every screen uses the same type scale, panels and labels. */
public final class Ui {

    /** Body text grows by this factor with "Veća slova"; big numerals (30 dp and up) keep their size. */
    private static volatile float textScale = 1f;
    /** "Zbijeno": smaller page margins and gaps between cards. */
    private static volatile boolean compact;

    private Ui() {}

    public static void configure(boolean largeText, boolean compactLayout) {
        textScale = largeText ? 1.1f : 1f;
        compact = compactLayout;
    }

    /** Margin around the cards of a screen. */
    public static int pagePad(Context c) {
        return dp(c, compact ? 7 : 12);
    }

    /** Gap between cards. */
    public static int gap(Context c) {
        return dp(c, compact ? 6 : 10);
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) d.setStroke(Math.max(1, dp(c, 1)), stroke);
        return d;
    }

    /** The standard card: panel colour, 1 px border, 8 dp corners. */
    public static GradientDrawable panel(Context c, Palette p) {
        return rounded(c, p.panel, p.line, 8);
    }

    public static TextView text(Context c, float sizeDp, Typeface tf, int color) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp < 30 ? sizeDp * textScale : sizeDp);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Small uppercase data label, for example "SARAJEVO · SADA". */
    public static TextView label(Context c, Palette p, String s) {
        TextView t = text(c, 11.5f, Fonts.medium(), p.fg2);
        t.setText(TrackingSpan.of(s.toUpperCase(Locale.ROOT), 0.08f));
        t.setSingleLine(true);
        return t;
    }

    /**
     * Large numbers carry the font's full ascent and descent (about 45% empty space at 150 dp).
     * Android 4.2 cannot set a line height, so pull the neighbours in with negative margins
     * measured from the real digit bounds. Call after the typeface and size are set.
     */
    public static void trimDigits(TextView t, ViewGroup.MarginLayoutParams lp) {
        Paint paint = t.getPaint();
        Paint.FontMetrics fm = paint.getFontMetrics();
        Rect digits = new Rect();
        paint.getTextBounds("0123456789", 0, 10, digits);
        float pad = t.getTextSize() * 0.05f;
        lp.topMargin += Math.round(fm.ascent - digits.top + pad);
        lp.bottomMargin += Math.round(digits.bottom - fm.descent + pad);
    }

    /** A tinted icon from the generated vector PNGs. */
    public static ImageView icon(Context c, int res, int color, float sizeDp) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.getDrawable().mutate().setColorFilter(color, PorterDuff.Mode.SRC_IN);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    /** A small coloured chip with bold uppercase text, for warning levels and air quality. */
    public static TextView chip(Context c, String text, int fill, int textColor) {
        TextView t = text(c, 11.5f, Fonts.medium(), textColor);
        t.setText(TrackingSpan.of(text.toUpperCase(Locale.ROOT), 0.08f));
        t.setBackgroundDrawable(rounded(c, fill, 0, 4));
        t.setPadding(dp(c, 8), dp(c, 4), dp(c, 8), dp(c, 4));
        t.setSingleLine(true);
        return t;
    }

    /**
     * A large touch button: accent fill (primary) or outlined panel (secondary), darker while
     * pressed and greyed when disabled.
     */
    public static TextView button(Context c, Palette p, String label, boolean primary, float sizeDp) {
        TextView t = text(c, sizeDp, Fonts.medium(), primary ? p.accentFg : p.fg);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setTextColor(new ColorStateList(
                new int[][] {{-android.R.attr.state_enabled}, {}},
                new int[] {p.fg3, primary ? p.accentFg : p.fg}));
        t.setBackgroundDrawable(buttonBackground(c, p, primary));
        t.setClickable(true);
        t.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        return t;
    }

    /** The button background on its own, for buttons that hold an icon instead of text. */
    public static StateListDrawable buttonBackground(Context c, Palette p, boolean primary) {
        int stroke = primary ? 0 : p.line;
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {-android.R.attr.state_enabled}, rounded(c, p.soft, 0, 8));
        s.addState(new int[] {android.R.attr.state_pressed}, rounded(c, primary ? blend(p.accent, 0xFF000000, 0.2f) : p.soft, stroke, 8));
        s.addState(new int[0], rounded(c, primary ? p.accent : p.panel, stroke, 8));
        return s;
    }

    /** Mixes two opaque colours: t = 0 gives a, t = 1 gives b. */
    public static int blend(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    public static View hairline(Context c, int color) {
        View v = new View(c);
        v.setBackgroundColor(color);
        return v;
    }
}
