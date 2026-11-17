package ba.infotablet.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

/**
 * A QR code drawn module by module on a light square (whole pixels per module, so it stays sharp
 * and scannable at any size). Always dark on light, also in the dark theme, because many phone
 * cameras do not read inverted codes.
 */
public final class QrView extends View {

    private static final int QUIET_MODULES = 3;

    private final Paint dark = new Paint();
    private final Paint light = new Paint();
    private BitMatrix matrix;

    public QrView(Context c) {
        super(c);
        dark.setColor(0xFF0F1419);
        light.setColor(0xFFFFFFFF);
    }

    public void setText(String text) {
        matrix = null;
        if (text != null && !text.isEmpty()) {
            try {
                Map<EncodeHintType, Object> hints = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
                hints.put(EncodeHintType.MARGIN, 0);
                hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
                hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
                matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints);
            } catch (WriterException e) {
                matrix = null;
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (matrix == null) return;
        int n = matrix.getWidth();
        int size = Math.min(getWidth(), getHeight());
        int cell = Math.max(1, size / (n + 2 * QUIET_MODULES));
        int total = cell * (n + 2 * QUIET_MODULES);
        int left = (getWidth() - total) / 2, top = (getHeight() - total) / 2;
        canvas.drawRect(left, top, left + total, top + total, light);
        int x0 = left + cell * QUIET_MODULES, y0 = top + cell * QUIET_MODULES;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (matrix.get(x, y)) canvas.drawRect(x0 + x * cell, y0 + y * cell, x0 + (x + 1) * cell, y0 + (y + 1) * cell, dark);
            }
        }
    }
}
