package ba.infotablet.data;

/** What the app knows about one data source: the last good payload and the latest attempt. */
public final class SourceState {

    public final String id;
    /** Normalised JSON written by the source, or null if it never succeeded. */
    public final String payload;
    public final long okAt;
    public final long attemptAt;
    public final int failCount;
    /** Start of the current failure streak (0 when the last attempt succeeded). */
    public final long firstFailAt;
    public final String error;

    public SourceState(String id, String payload, long okAt, long attemptAt, int failCount, long firstFailAt, String error) {
        this.id = id;
        this.payload = payload;
        this.okAt = okAt;
        this.attemptAt = attemptAt;
        this.failCount = failCount;
        this.firstFailAt = firstFailAt;
        this.error = error;
    }

    public static SourceState empty(String id) {
        return new SourceState(id, null, 0, 0, 0, 0, null);
    }

    public boolean hasData() {
        return payload != null;
    }

    /** True when the latest attempt failed (data, if any, is from an earlier success). */
    public boolean failing() {
        return failCount > 0;
    }
}
