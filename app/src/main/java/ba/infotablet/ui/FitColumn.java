package ba.infotablet.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * A vertical card body that leaves out rows which do not fit completely: rows that end below the
 * card's edge, and rows the layout squeezed into the space that was left. With large text a card
 * then shows fewer rows instead of one cut in half. Rows with a weight (spacers, scrolling lists)
 * are meant to take what is left and are never left out.
 */
public final class FitColumn extends LinearLayout {

    private final List<View> cut = new ArrayList<>();

    public FitColumn(Context c) {
        super(c);
        setOrientation(VERTICAL);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        cut.clear();
        int y = getPaddingTop(), limit = getMeasuredHeight() - getPaddingBottom();
        boolean out = false;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            LayoutParams lp = (LayoutParams) v.getLayoutParams();
            int h = v.getMeasuredHeight();
            if (lp.weight == 0 && !out) {
                int w = v.getMeasuredWidth();
                v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int full = v.getMeasuredHeight();
                v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
                out = full > h + 1 || y + lp.topMargin + full > limit + 1;
            }
            if (out && lp.weight == 0) cut.add(v);   // and every row after it, so nothing shows below a gap
            y += lp.topMargin + h + lp.bottomMargin;
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        return !cut.contains(child) && super.drawChild(canvas, child, drawingTime);
    }
}
