package pebxel.ui;

import com.shatteredpixel.shatteredpixeldungeon.Rankings;
import com.shatteredpixel.shatteredpixeldungeon.scenes.RankingsScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.GroupAccess;
import com.watabou.noosa.ui.Component;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Shattered's rankings, its rows laid out for the watch's bigger text (square screen): rows the full width under the
 * title, each as tall as its text needs (a cause that wraps gets two lines), and as many of the best as fit, as
 * Pebble PD showed its top rows. Shattered's own layout squeezed 11 rows in, so each "Slain by" wrapped onto the row
 * below and the first row ran into the title.
 */
public class WatchRankings extends RankingsScene {
	static final int GAP = 4, TOP = 22;

	@Override
	public void create() {
		super.create();
		try {
			List<Component> rows = new ArrayList<>();
			for (Gizmo g : GroupAccess.members(this)) if (g != null && g.getClass().getSimpleName().equals("Record")) rows.add((Component) g);
			if (rows.isEmpty()) return;
			Field df = rows.get(0).getClass().getDeclaredField("desc"); df.setAccessible(true);
			int w = Camera.main.width, h = Camera.main.height;
			boolean round = pebxel.Screen.round;
			int top0 = round ? 46 : TOP;
			int bottom = Rankings.INSTANCE.totalNumber >= Rankings.TABLE_SIZE ? (round ? 30 : 18) : (round ? 24 : 2);   // (the games-played line)
			if (round) {   // (the title and the games-played line come in off the rim)
				for (Gizmo g : GroupAccess.members(this)) {
					if (g instanceof com.shatteredpixel.shatteredpixeldungeon.windows.IconTitle) ((Component) g).setPos(((Component) g).left(), 26);
					else if (g instanceof com.shatteredpixel.shatteredpixeldungeon.ui.ExitButton) ((Component) g).setPos(w / 2f - ((Component) g).width() / 2f, 3);
					else if (g instanceof RenderedTextBlock) ((Component) g).setPos(((Component) g).left(), h - 24);
				}
			}
			float need = 18;
			for (Component r : rows) {
				r.setRect(GAP, 0, round ? 100 : w - 2 * GAP, 18);
				need = Math.max(need, ((RenderedTextBlock) df.get(r)).height() + 3);
			}
			need = (float) Math.ceil(need);
			if (round) {   // (each as tall as its own text needs, as many as fit; the circle's width at its narrower end)
				float y = top0, rad = w / 2f - 1, cy = h / 2f;
				for (Component r : rows) {
					float g0 = (float) Math.sqrt(Math.max(0, rad * rad - Math.pow(Math.max(Math.abs(y - cy), Math.abs(y + 22 - cy)), 2))) - 3;
					r.setRect(w / 2f - g0, y, 2 * g0, 18);   // (its text wraps to the width it will have)
					float rn = Math.max(18, (float) Math.ceil(((RenderedTextBlock) df.get(r)).height() + 3));
					float dy = Math.max(Math.abs(y - cy), Math.abs(y + rn - cy));
					float half = (float) Math.sqrt(Math.max(0, rad * rad - dy * dy)) - 3;
					if (y + rn > h - bottom || half < 40) { r.killAndErase(); continue; }
					r.setRect(w / 2f - half, y, 2 * half, rn);
					y += rn;
				}
				return;
			}
			int fit = Math.max(1, (int) ((h - top0 - bottom) / need));
			for (int i = 0; i < rows.size(); i++) {
				Component r = rows.get(i);
				if (i >= fit) { r.killAndErase(); continue; }
				if (round) {   // (as wide as the circle is at the row's narrower end, less a margin)
					float ry = top0 + i * need, rad = w / 2f - 1, cy = h / 2f;
					float dy = Math.max(Math.abs(ry - cy), Math.abs(ry + need - cy));
					float half = (float) Math.sqrt(Math.max(0, rad * rad - dy * dy)) - 3;
					r.setRect(w / 2f - half, ry, 2 * half, need);
				} else r.setRect(GAP, TOP + i * need, w - 2 * GAP, need);
			}
		} catch (Exception e) { e.printStackTrace(); }
	}
}
