package ba.infotablet.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * A minimal grid: fixed number of equal columns, rows with fixed heights or a share of the
 * remaining height, a uniform gap, and children spanning cells. Android 4.2's GridLayout has
 * no column weights, which the dashboard layout needs to line up exactly.
 */
public final class SpanGrid extends ViewGroup {

    private final int columns;
    private final int gap;
    private int[] rowSpec = new int[0]; // > 0 fixed px, <= 0 weight (-1 = one share)

    public SpanGrid(Context ctx, int columns, int gap) {
        super(ctx);
        this.columns = columns;
        this.gap = gap;
    }

    /** Row heights in px; use -1, -2 ... for shares of the remaining height. */
    public void setRows(int... spec) {
        rowSpec = spec.clone();
        requestLayout();
    }

    public void add(View child, int col, int row, int colSpan, int rowSpan) {
        addView(child, new Cell(col, row, colSpan, rowSpan));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        int[] rows = rowHeights(h - getPaddingTop() - getPaddingBottom());
        int colW = colWidth(w - getPaddingLeft() - getPaddingRight());
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            Cell p = (Cell) c.getLayoutParams();
            int cw = colW * p.colSpan + gap * (p.colSpan - 1);
            int ch = span(rows, p.row, p.rowSpan);
            c.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int[] rows = rowHeights(b - t - getPaddingTop() - getPaddingBottom());
        int colW = colWidth(r - l - getPaddingLeft() - getPaddingRight());
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            Cell p = (Cell) c.getLayoutParams();
            int x = getPaddingLeft() + p.col * (colW + gap);
            int y = getPaddingTop();
            for (int k = 0; k < p.row; k++) y += rows[k] + (rows[k] > 0 ? gap : 0);
            c.layout(x, y, x + c.getMeasuredWidth(), y + c.getMeasuredHeight());
        }
    }

    private int colWidth(int inner) {
        return (inner - gap * (columns - 1)) / columns;
    }

    /** Rows whose children are all GONE collapse to zero height, gap included. */
    private int[] rowHeights(int inner) {
        int n = rowSpec.length;
        int[] out = new int[n];
        boolean[] empty = new boolean[n];
        for (int k = 0; k < n; k++) empty[k] = true;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            Cell p = (Cell) c.getLayoutParams();
            for (int k = p.row; k < p.row + p.rowSpan && k < n; k++) empty[k] = false;
        }
        int fixed = 0, shares = 0, visible = 0;
        for (int k = 0; k < n; k++) {
            if (empty[k]) continue;
            visible++;
            if (rowSpec[k] > 0) fixed += rowSpec[k]; else shares += Math.max(1, -rowSpec[k]);
        }
        int rest = Math.max(0, inner - fixed - gap * Math.max(0, visible - 1));
        for (int k = 0; k < n; k++) {
            if (empty[k]) out[k] = 0;
            else out[k] = rowSpec[k] > 0 ? rowSpec[k] : (shares == 0 ? 0 : rest * Math.max(1, -rowSpec[k]) / shares);
        }
        return out;
    }

    private int span(int[] rows, int row, int rowSpan) {
        int h = 0;
        for (int k = row; k < row + rowSpan && k < rows.length; k++) h += rows[k] + (k > row ? gap : 0);
        return h;
    }

    static final class Cell extends LayoutParams {
        final int col, row, colSpan, rowSpan;

        Cell(int col, int row, int colSpan, int rowSpan) {
            super(0, 0);
            this.col = col;
            this.row = row;
            this.colSpan = colSpan;
            this.rowSpan = rowSpan;
        }
    }
}
