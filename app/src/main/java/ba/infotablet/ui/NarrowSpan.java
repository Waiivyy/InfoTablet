package ba.infotablet.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ReplacementSpan;

/**
 * In a monospaced font ':' ',' '.' '/' and '°' are as wide as a digit, so "14:32" reads as "14 : 32".
 * This span draws such a character centred in a narrower advance.
 */
public final class NarrowSpan extends ReplacementSpan {

    private final float factor;

    public NarrowSpan(float factor) {
        this.factor = factor;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        if (fm != null) paint.getFontMetricsInt(fm);
        return Math.round(paint.measureText(text, start, end) * factor);
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        float full = paint.measureText(text, start, end);
        float narrow = full * factor;
        canvas.drawText(text, start, end, x - (full - narrow) / 2f, y, paint);
    }

    /** Applies narrow punctuation to a numeric string, for example a time, price or temperature. */
    public static CharSequence numeric(String s) {
        SpannableString out = new SpannableString(s);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ':' || c == ',' || c == '.' || c == '/') {
                out.setSpan(new NarrowSpan(0.56f), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (c == '°') {
                out.setSpan(new NarrowSpan(0.70f), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return out;
    }
}
