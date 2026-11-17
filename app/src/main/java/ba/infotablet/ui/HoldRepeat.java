package ba.infotablet.ui;

import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;

/**
 * A tap runs the action once (through the normal click); holding the button keeps repeating it,
 * so a date months ahead does not need hundreds of taps.
 */
public final class HoldRepeat implements View.OnTouchListener {

    private static final long FIRST_DELAY_MS = 450L;
    private static final long STEP_MS = 90L;

    private final Handler handler = new Handler();
    private final Runnable action;
    private boolean repeated;

    private HoldRepeat(Runnable action) {
        this.action = action;
    }

    public static void attach(View v, Runnable action) {
        v.setOnClickListener(x -> action.run());
        final HoldRepeat h = new HoldRepeat(action);
        v.setOnTouchListener(h);
        // A view removed while held never sees ACTION_UP; stop repeating then as well.
        v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {}

            @Override public void onViewDetachedFromWindow(View view) {
                h.handler.removeCallbacks(h.tick);
            }
        });
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            repeated = true;
            action.run();
            handler.postDelayed(this, STEP_MS);
        }
    };

    // A plain tap is left to the view (returns false), which performs the click itself.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                repeated = false;
                handler.postDelayed(tick, FIRST_DELAY_MS);
                return false; // let the view show its pressed state and handle the tap
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(tick);
                if (repeated) {
                    v.setPressed(false);
                    return true; // the hold already acted; skip the click
                }
                return false;
            default:
                return false;
        }
    }
}
