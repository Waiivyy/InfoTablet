package ba.infotablet.ui;

import android.content.Context;
import android.graphics.Typeface;

/**
 * Bundled static fonts (SIL Open Font License 1.1, see assets/licenses/OFL.txt). Android 4.2 has
 * no Roboto Medium and cannot read variable fonts, so the weights used by the design ship as
 * separate TTF files.
 * Typefaces are loaded once; Typeface.createFromAsset leaks memory if called repeatedly.
 */
public final class Fonts {

    private static Typeface regular, medium, monoLight, monoRegular, monoMedium;

    private Fonts() {}

    public static synchronized void load(Context ctx) {
        if (regular != null) return;
        regular = load(ctx, "fonts/Roboto-Regular.ttf", Typeface.DEFAULT);
        medium = load(ctx, "fonts/Roboto-Medium.ttf", Typeface.DEFAULT_BOLD);
        monoLight = load(ctx, "fonts/RobotoMono-Light.ttf", Typeface.MONOSPACE);
        monoRegular = load(ctx, "fonts/RobotoMono-Regular.ttf", Typeface.MONOSPACE);
        monoMedium = load(ctx, "fonts/RobotoMono-Medium.ttf", Typeface.MONOSPACE);
    }

    private static Typeface load(Context ctx, String path, Typeface fallback) {
        try {
            return Typeface.createFromAsset(ctx.getAssets(), path);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static Typeface regular() { return regular; }
    public static Typeface medium() { return medium; }
    public static Typeface monoLight() { return monoLight; }
    public static Typeface mono() { return monoRegular; }
    public static Typeface monoMedium() { return monoMedium; }
}
