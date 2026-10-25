package pebxel.ui;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.ui.Button;
import com.shatteredpixel.shatteredpixeldungeon.ui.TalentIcon;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndInfoTalent;
import com.watabou.noosa.Group;
import com.watabou.noosa.ui.Component;
import java.util.List;

/**
 * A talent's window in Pebble PD's style (in place of Shattered's WndInfoTalent, whose long descriptions ran past the
 * screen with the Upgrade button under them: Thomas, "I can't spend tier 2 talents"): the talent's picture at 2x and
 * its name, the description in Gothic 14 on black, and the button (Upgrade, or the metamorphosis' choice) pinned at
 * the foot, always in sight and always the one Select presses. A description too long for the room scrolls inside its
 * well, whole lines at a time: Up/Down a page, a finger's drag, or a tap on the text (on to the next page, then back
 * to the top), with small triangles while there is more above or below.
 */
public class WndWatchTalent extends WatchWindow implements Navigable, Scrolls {
	static final int BTN_H = 34, GAP = 2;
	private final WndInfoTalent.TalentButtonCallback callback;
	private final int w, tx, tw, wellY, rows;
	private final List<List<String[]>> lines;
	private final Group text = new Group();
	private WatchButton button;
	private int top, drag;

	public WndWatchTalent(WndInfoTalent src) { this(src.talent, src.points, src.buttonCallback); }

	public WndWatchTalent(Talent talent, int points, WndInfoTalent.TalentButtonCallback callback) {
		super(left(WndWatchItem.width()), 4, WndWatchItem.width(), 100, false);
		this.callback = callback;
		boolean round = pebxel.Screen.round;
		w = WndWatchItem.width();
		// the head: the talent's picture and its name (up to two lines)
		String name = Messages.titleCase(talent.title()) + (points > 0 ? " +" + points : "");
		List<String> nl = WatchHud.wrap(name, WatchText.G14B, round ? 124 : w - 36);
		int head = Math.max(36, Math.min(2, nl.size()) * lineHeight(WatchText.G14B) + 4);
		// the room the screen leaves for the description (round: the circle's middle), and the lines it makes
		int maxH = round ? 186 : WatchHud.H - 14 - 6;
		int foot = callback != null ? GAP + BTN_H : 0;
		int lh = lineHeight(WatchText.G14);
		boolean meta = (callback != null && callback.metamorphDesc()) || (Dungeon.hero != null && Dungeon.hero.metamorphedTalents.containsValue(talent));
		String desc = talent.desc(meta);
		int room = (maxH - head - 4 - foot) / lh;
		int width = round ? 180 : w - 4;
		List<List<String[]>> ls = layout(desc, WatchText.G14, width);
		if (ls.size() > room) ls = layout(desc, WatchText.G14, width - 9);   // (scrolls: room on the right for its triangles)
		lines = ls;
		rows = Math.max(1, Math.min(room, lines.size()));
		int h = head + rows * lh + 4 + foot;
		resize(w, h);
		place(left(w), Math.max(round ? 37 : 4, (WatchHud.H - h) / 2));
		tw = width; tx = round ? (w - width) / 2 : 2;
		wellY = head;

		TalentIcon icon = new TalentIcon(talent); icon.scale.set(2); icon.y = 1;
		if (round) {
			int nw = 0;
			for (int i = 0; i < Math.min(2, nl.size()); i++) nw = Math.max(nw, WatchText.width(WatchText.G14B, nl.get(i)));
			icon.x = pebxel.Screen.w / 2 - contentX - (36 + nw) / 2;
		}
		add(icon);
		for (int i = 0; i < Math.min(2, nl.size()); i++) text(nl.get(i), WatchText.G14B, PASTEL, (int) icon.x + 36, 2 + i * lineHeight(WatchText.G14B), 0, 0);
		well(0, wellY, w, rows * lh + 4);
		// the text itself pressed: on a page (back to the top from the last)
		if (lines.size() > rows) {
			Button turn = new Button() { @Override protected void onClick() { scrollTo(top + rows >= lines.size() ? 0 : top + rows - 1); } };
			turn.setRect(0, wellY, w, rows * lh + 4);
			add(turn);
		}
		add(text);
		if (callback != null) {
			button = new WatchButton(callback.prompt()) {
				@Override protected void onClick() { hide(); callback.call(); }
			};
			button.labelColor = ICTERINE;
			button.setRect(0, h - BTN_H, w, BTN_H);
			fit(button);
			button.label(button.text());
			add(button);
		}
		lay();
	}

	/** The lines from `top` that fit, and the triangles. */
	private void lay() {
		wipe(text);
		int lh = lineHeight(WatchText.G14);
		for (int i = 0; i < rows && top + i < lines.size(); i++) {
			int px = tx;
			for (String[] seg : lines.get(top + i)) {
				WatchText t = new WatchText(WatchText.G14, seg[0], seg[1] != null ? ICTERINE : WHITE);
				WatchHud.place(t, px, wellY + 2 + i * lh, 0, 0);
				text.add(t);
				px += WatchText.width(WatchText.G14, seg[0]);
			}
		}
		if (lines.size() <= rows) return;
		int ax = tx + tw + 1;
		for (int k = 0; k < 4; k++) {
			if (top > 0) text.add(WatchHud.block(WHITE, ax + 3 - k, wellY + 3 + k, 2 * k + 1, 1));   // pointing up: more above
			if (top + rows < lines.size()) text.add(WatchHud.block(WHITE, ax + k, wellY + rows * lh - 3 + k, 7 - 2 * k, 1));   // down: more below
		}
	}

	private void scrollTo(int t) {
		t = Math.max(0, Math.min(lines.size() - rows, t));
		if (t == top) return;
		top = t;
		lay();
	}

	/** (tests) The first line shown, and how many lines there are. */
	public int[] scrolled() { return new int[]{top, rows, lines.size()}; }

	// Up/Down: a page back or on (one line kept, to read on from)
	public void nav(int dir) { scrollTo(top + dir * Math.max(1, rows - 1)); }

	public Component focused() { return button; }

	// a finger's drag (phone px, down positive): the lines follow it, a line at a time
	public void scrollBy(int dy) {
		drag += dy;
		int step = lineHeight(WatchText.G14) * 2;
		while (drag <= -step) { drag += step; scrollTo(top + 1); }
		while (drag >= step) { drag -= step; scrollTo(top - 1); }
	}
}
