package ba.infotablet.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Background style "Mreža": the background colour with thin grid lines, like graph paper. */
public final class GridDrawable extends Drawable {

    private final int bg;
    private final int step;
    private final Paint line = new Paint();

    public GridDrawable(int bg, int lineColor, int stepPx) {
        this.bg = bg;
        this.step = Math.max(8, stepPx);
        line.setColor(lineColor);
        line.setStrokeWidth(1);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        canvas.drawColor(bg);
        for (int x = b.left + step / 2; x < b.right; x += step) canvas.drawLine(x, b.top, x, b.bottom, line);
        for (int y = b.top + step / 2; y < b.bottom; y += step) canvas.drawLine(b.left, y, b.right, y, line);
    }

    @Override public void setAlpha(int alpha) {}

    @Override public void setColorFilter(ColorFilter cf) {}

    @Override public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
