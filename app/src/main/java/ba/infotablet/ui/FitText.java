package ba.infotablet.ui;

import android.content.Context;
import android.text.TextUtils;
import android.widget.TextView;

/**
 * Text that shows only whole lines: the number of lines follows the height it is given (lay it
 * out with a weight), and the last line ends with "…". Nothing is cut through the middle of a line,
 * whatever the text size setting.
 */
public final class FitText extends TextView {

    public FitText(Context c) {
        super(c);
        setEllipsize(TextUtils.TruncateAt.END);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        if (MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY) {
            int room = MeasureSpec.getSize(heightSpec) - getPaddingTop() - getPaddingBottom();
            int lines = Math.max(1, room / Math.max(1, getLineHeight()));
            if (lines != getMaxLines()) setMaxLines(lines);
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
