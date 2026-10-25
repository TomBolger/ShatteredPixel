package pebxel.ui;

/** A window that scrolls its own content under a finger's drag (Touches), rather than being moved whole. */
public interface Scrolls {
	/** The finger moved dy phone pixels (down positive). */
	void scrollBy(int dy);
}
