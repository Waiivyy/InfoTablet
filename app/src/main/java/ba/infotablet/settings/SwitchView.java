package ba.infotablet.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import ba.infotablet.ui.Palette;
import ba.infotablet.ui.Ui;

/** An on/off switch drawn in the palette (the Holo switch of Android 4.2 is small and off-theme). */
final class SwitchView extends View {

    interface Listener {
        void onToggle(boolean on);
    }

    private final Palette p;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private boolean on;

    SwitchView(Context c, Palette p, boolean on, Listener listener) {
        super(c);
        this.p = p;
        this.on = on;
        setClickable(true);
        setContentDescription(on ? "Uključeno" : "Isključeno");
        setOnClickListener(v -> {
            this.on = !this.on;
            setContentDescription(this.on ? "Uključeno" : "Isključeno");
            invalidate();
            listener.onToggle(this.on);
        });
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(Ui.dp(getContext(), 56), Ui.dp(getContext(), 32));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight(), r = h / 2f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(on ? p.accent : p.soft);
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, paint);
        if (!on) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1, Ui.dp(getContext(), 1)));
            paint.setColor(p.line);
            rect.set(0.5f, 0.5f, w - 0.5f, h - 0.5f);
            canvas.drawRoundRect(rect, r, r, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(on ? p.accentFg : p.fg3);
        float knob = r - Ui.dp(getContext(), 5);
        canvas.drawCircle(on ? w - r : r, r, knob, paint);
    }
}
