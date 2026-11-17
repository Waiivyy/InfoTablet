package ba.infotablet.util;

/**
 * The time the UI shows. Always the real time in release builds; debug builds can shift it
 * (adb broadcast, see the debug source set) to check evening and night layouts.
 */
public final class Clock {

    private static volatile long offsetMs;

    private Clock() {}

    public static long now() {
        return System.currentTimeMillis() + offsetMs;
    }

    public static void setDebugOffset(long ms) {
        offsetMs = ms;
    }
}
