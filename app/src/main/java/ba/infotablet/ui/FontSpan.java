package ba.infotablet.ui;

import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

/** Applies a bundled Typeface to part of a text (TypefaceSpan(Typeface) needs API 28). */
public final class FontSpan extends MetricAffectingSpan {

    private final Typeface typeface;

    public FontSpan(Typeface typeface) {
        this.typeface = typeface;
    }

    @Override
    public void updateDrawState(TextPaint tp) {
        tp.setTypeface(typeface);
    }

    @Override
    public void updateMeasureState(TextPaint tp) {
        tp.setTypeface(typeface);
    }
}
