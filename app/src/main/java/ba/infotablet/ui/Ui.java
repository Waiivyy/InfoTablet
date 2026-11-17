package ba.infotablet.ui;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Small view factory so every screen uses the same type scale, panels and labels. */
public final class Ui {

    private Ui() {}

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
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp);
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

    public static View hairline(Context c, int color) {
        View v = new View(c);
        v.setBackgroundColor(color);
        return v;
    }
}
