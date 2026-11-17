package ba.infotablet.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ReplacementSpan;

/** Letter spacing for small uppercase labels (TextView.setLetterSpacing needs API 21). */
public final class TrackingSpan extends ReplacementSpan {

    private final float em;

    public TrackingSpan(float em) {
        this.em = em;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        if (fm != null) paint.getFontMetricsInt(fm);
        float extra = paint.getTextSize() * em * Math.max(0, end - start - 1);
        return Math.round(paint.measureText(text, start, end) + extra);
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        float gap = paint.getTextSize() * em;
        float cx = x;
        for (int i = start; i < end; i++) {
            canvas.drawText(text, i, i + 1, cx, y, paint);
            cx += paint.measureText(text, i, i + 1) + gap;
        }
    }

    public static CharSequence of(String s, float em) {
        SpannableString out = new SpannableString(s);
        if (s.length() > 0) out.setSpan(new TrackingSpan(em), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return out;
    }
}
