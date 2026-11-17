package ba.infotablet.ui;

/** A screen that redraws itself from the cache when data changes or a minute passes. */
public interface Refreshable {
    void refresh(long now);
}
