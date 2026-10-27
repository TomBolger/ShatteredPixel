package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Game;
import com.watabou.noosa.Scene;
import com.watabou.noosa.ui.Component;
import java.util.*;

/**
 * The full-screen scenes the window sweep skipped (alchemy, rankings, the Amulet, ...): each opened with real content, its
 * buttons all whole on the screen (inside the circle on Gabbro), each reached with Down, a focus box drawn, and Back
 * leaving. Called by RefinementTest after WindowFit (SCENEFIT=name for one; WINSHOTS=folder for pictures).
 */
public final class SceneFit {
	static final String P = "com.shatteredpixel.shatteredpixeldungeon.";
	static final List<String> fails = new ArrayList<>();

	static void shot(String name) {
		String shots = System.getenv("WINSHOTS");
		if (shots != null) try { RefinementTest.pic(shots, name); } catch (Exception x) { throw new RuntimeException(x); }
	}

	static void clear(Runnable frame) { for (int i = 0; i < 20 && Ui.topWindow() != null; i++) { Ui.topWindow().hide(); frame.run(); } }

	/** The button's rectangle in watch px. */
	static float[] rect(Component k) {
		Camera c = k.camera();
		float[] r = {(c.x + (k.left() - c.scroll.x) * c.zoom) / 2, (c.y + (k.top() - c.scroll.y) * c.zoom) / 2, 0, 0};
		r[2] = r[0] + k.width() * c.zoom / 2; r[3] = r[1] + k.height() * c.zoom / 2;
		return r;
	}

	static void run(String name, Runnable frame, Controls oc, Runnable open, Runnable act) {
		if (System.getenv("SCENEFIT") != null && !name.contains(System.getenv("SCENEFIT"))) return;
		try {
			clear(frame);
			open.run();
			for (int i = 0; i < 30; i++) frame.run();
			Scene sc = Game.scene();
			System.out.println("  camera " + Camera.main.width + "x" + Camera.main.height + " zoom " + Camera.main.zoom + " at " + Camera.main.x + "," + Camera.main.y + " screen " + Screen.w + "x" + Screen.h + " insets " + com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene.class.getSimpleName());
			shot("scene_" + name);
			List<Ui.Part> bs = sc instanceof GameScene ? new ArrayList<>() : Controls.buttons(sc);   // (the game's own HUD is the sweep's)
			StringBuilder sb = new StringBuilder();
			for (Ui.Part p : bs) {
				if (((Component) p.g).width() < 1) continue;
				float[] r = rect((Component) p.g);
				sb.append(" [").append(p.text.length() > 14 ? p.text.substring(0, 14) : p.text).append(" ").append((int) r[0]).append(",").append((int) r[1]).append("..").append((int) r[2]).append(",").append((int) r[3]).append("]");
				WindowFit.inside(r, name + ": button " + p.text);
			}
			System.out.println("  scene: " + name + " (" + sc.getClass().getSimpleName() + ") " + bs.size() + " buttons:" + sb);
			if (act != null) act.run();
		} catch (AssertionError | RuntimeException x) { fails.add(name + ": " + x.getMessage()); System.out.println("  FAIL scene " + name + ": " + x.getMessage()); x.printStackTrace(System.out); }
	}

	/** Every text and button of a scene whole on the screen (a round one's circle), no button over another, none over a text. */
	static void layout(String name, Scene sc) {
		List<Ui.Part> all = Ui.parts(sc);
		List<float[]> bs = new ArrayList<>(), ts = new ArrayList<>(); List<String> bn = new ArrayList<>(), tn = new ArrayList<>();
		for (Ui.Part p : all) {
			if (!(p.g instanceof Component) || p.g.camera() == null || p.g instanceof com.shatteredpixel.shatteredpixeldungeon.ui.ExitButton && false) continue;
			Component k = (Component) p.g;
			if (k.width() < 1 || k.height() < 1) continue;
			if (k.camera() != Camera.main && k.camera() != com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene.uiCamera) continue;   // (inside a scroll pane: the pane is checked)
			float[] r = rect(k);
			if (r[2] < 0 || r[0] > Screen.w || r[3] < 0 || r[1] > Screen.h) continue;   // (not on this screen at all: a hidden one)
			WindowFit.inside(r, name + ": " + (p.button ? "button " : "text '") + (p.text.length() > 20 ? p.text.substring(0, 20) : p.text) + "'");
			(p.button ? bs : ts).add(r); (p.button ? bn : tn).add(p.text);
		}
		for (int a = 0; a < bs.size(); a++) {
			for (int b = a + 1; b < bs.size(); b++) check(!over(bs.get(a), bs.get(b), 2), name + ": button '" + bn.get(a) + "' over button '" + bn.get(b) + "' " + java.util.Arrays.toString(bs.get(a)) + " " + java.util.Arrays.toString(bs.get(b)));
			for (int t = 0; t < ts.size(); t++) check(!over(bs.get(a), ts.get(t), 1) || bn.get(a).equals(tn.get(t)) || bn.get(a).contains(tn.get(t)), name + ": button '" + bn.get(a) + "' over text '" + tn.get(t) + "' " + java.util.Arrays.toString(bs.get(a)) + " " + java.util.Arrays.toString(ts.get(t)));
		}
	}
	static boolean over(float[] a, float[] b, float m) { return Math.min(a[2], b[2]) - Math.max(a[0], b[0]) > m && Math.min(a[3], b[3]) - Math.max(a[1], b[1]) > m; }
	static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }

	/** Down reaches every button of the scene (a focus box drawn inside the screen); Up goes back. */
	static void reach(String name, Scene sc, Runnable frame, Controls oc) {
		List<Ui.Part> bs = Controls.buttons(sc);
		if (bs.isEmpty()) return;
		Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		for (int i = 0; i <= bs.size() + 1 + (sc instanceof com.shatteredpixel.shatteredpixeldungeon.scenes.AmuletScene ? 12 : 0); i++) {
			oc.button(Controls.DOWN); frame.run();
			Component f = oc.focused();
			if (f == null) continue;
			seen.add(f);
			WindowFit.inside(rect(f), name + ": focused " + WindowFit.label(f));
			Object[] hl = oc.highlight();
			check(hl != null || f instanceof pebxel.ui.Focusable, name + ": a focus box is drawn round " + WindowFit.label(f));
			if (hl != null) {
				float[] q = (float[]) hl[1]; Camera c = f.camera();
				float[] b = {(c.x + (q[0] - c.scroll.x) * c.zoom) / 2, (c.y + (q[1] - c.scroll.y) * c.zoom) / 2, 0, 0};
				b[2] = b[0] + q[2] * c.zoom / 2; b[3] = b[1] + q[3] * c.zoom / 2;
				WindowFit.inside(b, name + ": focus box on " + WindowFit.label(f));
			}
		}
		check(seen.size() == bs.size(), name + ": Down reaches every button (" + seen.size() + " of " + bs.size() + ")");
	}

	/** The window on top, as WindowFit judges it: inside the view or scrolling inside a whole frame, buttons usable, Back closes. */
	static void win(String name, Runnable frame, Controls oc, boolean mustClose) {
		Window w = Ui.topWindow();
		check(w != null, name + ": a window opens");
		for (int i = 0; i < 6; i++) frame.run();
		w = Ui.topWindow();
		String n = name + " (" + w.getClass().getSimpleName() + ")";
		shot(name.replaceAll("[^A-Za-z0-9]+", "_"));
		if (w instanceof pebxel.ui.WatchWindow && ((pebxel.ui.WatchWindow) w).fixed) { System.out.println("    win: " + n + ": fills the screen by design"); }
		else {
			WindowFit.edges(w, n, oc);
			if (!Controls.tall(w)) {
				Camera c = w.camera; int vt = Controls.viewTop(w), vb = Controls.viewBottom(w);
				check(c.y >= vt - 2 && c.y + c.screenHeight() <= vb + 2, n + ": fits inside the view");
				WindowFit.usable(w, n, oc, frame);
				System.out.println("    win: " + n + ": fits");
			} else {
				for (int end : new int[]{100000, -100000}) { Controls.scroll(w, end); frame.run(); frame.run(); WindowFit.edges(w, n + " scrolled " + end, oc); if (end == 100000) shot(name.replaceAll("[^A-Za-z0-9]+", "_") + "_top"); }
				int k = WindowFit.reach(w, n, oc, frame);
				System.out.println("    win: " + n + ": tall, scrolls, " + k + " buttons reached");
			}
		}
		if (mustClose && Ui.topWindow() == w) {
			try { oc.button(Controls.BACK); for (int i = 0; i < 4; i++) frame.run(); } catch (RuntimeException x) { }
			check(Ui.topWindow() != w, n + ": Back closes it");
		}
	}

	static Ui.Part find(Scene sc, String text) {
		for (Ui.Part p : Ui.parts(sc)) if (p.button && p.text.contains(text)) return p;
		return null;
	}
	static Ui.Part findIn(Window w, String text) {
		for (Ui.Part p : Ui.parts(w)) if (p.button && p.text.contains(text)) return p;
		return null;
	}
	static boolean until(Runnable frame, int cap, java.util.function.BooleanSupplier ok) { for (int i = 0; i < cap && !ok.getAsBoolean(); i++) frame.run(); return ok.getAsBoolean(); }

	static void toGame(Runnable frame) {
		if (Game.scene() instanceof GameScene) return;
		Game.switchScene(GameScene.class);
		for (int i = 0; i < 600 && !(Game.scene() instanceof GameScene && Dungeon.hero != null && Dungeon.hero.ready); i++) frame.run();
	}

	public static void stock() {
		com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero h = Dungeon.hero;
		h.belongings.backpack.items.clear();
		Item[] its = {new com.shatteredpixel.shatteredpixeldungeon.plants.Sungrass.Seed(), new com.shatteredpixel.shatteredpixeldungeon.plants.Firebloom.Seed(), new com.shatteredpixel.shatteredpixeldungeon.plants.Icecap.Seed(),
				new com.shatteredpixel.shatteredpixeldungeon.plants.Rotberry.Seed(), new com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing(), new com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfIdentify(),
				new com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade(), new com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb(), new com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb(), new com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfEnchantment()};
		for (Item i : its) { i.identify(); i.collect(); }
	}

	static void alchemy(Runnable frame, Controls oc) {
		run("alchemy", frame, oc, () -> { stock(); Dungeon.energy = 60; Game.switchScene(com.shatteredpixel.shatteredpixeldungeon.scenes.AlchemyScene.class); }, () -> {
			Scene sc = Game.scene();
			layout("alchemy", sc); reach("alchemy", sc, frame, oc);
			// each ingredient picked from the bag, three seeds
			for (int n = 0; n < 3; n++) {
				Object[] inputs = (Object[]) WatchDriver.getStatic(com.shatteredpixel.shatteredpixeldungeon.scenes.AlchemyScene.class, "inputs");
				Object slot = null;
				for (Object in : inputs) if (in != null && WatchDriver.get(in, "item") == null) { slot = WatchDriver.get(in, "slot"); break; }
				check(slot != null, "alchemy: an empty slot for ingredient " + (n + 1));
				Ui.press((com.watabou.noosa.Gizmo) slot); for (int i = 0; i < 6; i++) frame.run();
				win("alchemy bag " + (n + 1), frame, oc, false);
				Window bag = Ui.topWindow();
				Ui.Part pick = null; String[] want = {"Sungrass", "Firebloom", "Icecap"};
				Item it = null;
				for (Item x : Dungeon.hero.belongings.backpack.items) if (x.getClass().getSimpleName().equals("Seed") ? x.name().contains(want[n]) || x.getClass().getName().contains(want[n]) : false) it = x;
				check(it != null && ((pebxel.ui.WndWatchBag) bag).focusOn(it), "alchemy: the bag lists " + want[n]);
				for (int i = 0; i < 3; i++) frame.run();
				oc.button(Controls.SELECT); for (int i = 0; i < 8; i++) frame.run();
				check(Ui.topWindow() == null, "alchemy: picking closes the bag");
				layout("alchemy with " + (n + 1), Game.scene());
			}
			shot("scene_alchemy_filled");
			Object[] combines = (Object[]) WatchDriver.getStatic(com.shatteredpixel.shatteredpixeldungeon.scenes.AlchemyScene.class, "combines");
			Object b = WatchDriver.get(combines[0], "button");
			check(b instanceof com.shatteredpixel.shatteredpixeldungeon.ui.RedButton && ((com.shatteredpixel.shatteredpixeldungeon.ui.RedButton) b).active, "alchemy: the combine button is on with a recipe");
			float[] r = rect((Component) b); WindowFit.inside(r, "alchemy combine button");
			reach("alchemy filled", Game.scene(), frame, oc);
			Ui.press((com.watabou.noosa.Gizmo) b); for (int i = 0; i < 90; i++) frame.run();
			shot("scene_alchemy_made");
			layout("alchemy made", Game.scene());
			// the guide
			Ui.Part g = find(Game.scene(), "Guide"); check(g != null, "alchemy: a guide button");
			Ui.press(g.g); for (int i = 0; i < 6; i++) frame.run();
			win("alchemy guide", frame, oc, true);
			// the energy's plus
			Object add = WatchDriver.get(Game.scene(), "energyAdd");
			Ui.press((com.watabou.noosa.Gizmo) add); for (int i = 0; i < 6; i++) frame.run();
			if (Ui.topWindow() != null) win("alchemy energize bag", frame, oc, true);
			// Back leaves
			oc.button(Controls.BACK); toGame(frame);
			check(Game.scene() instanceof GameScene, "alchemy: Back leaves for the game");
		});
	}

	/** The records the rankings need: eleven games, every class, wins and deaths, causes short and long, a challenge, a custom seed. */
	public static void records() {
		com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero h = Dungeon.hero;
		com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass keep = h.heroClass;
		Class<?>[] causes = {com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Goo.class, com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GnollGeomancer.class, com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat.class,
				com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Succubus.class, com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Burning.class, com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DwarfKing.class,
				com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Crab.class, com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger.class, com.shatteredpixel.shatteredpixeldungeon.actors.mobs.YogDzewa.class, com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Eye.class, com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Scorpio.class};
		com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass[] cls = com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass.values();
		h.belongings.backpack.items.clear(); stock();
		for (int i = 0; i < causes.length; i++) {
			h.heroClass = cls[i % cls.length]; h.lvl = 3 + i * 2;
			com.shatteredpixel.shatteredpixeldungeon.Statistics.deepestFloor = 1 + i * 2; com.shatteredpixel.shatteredpixeldungeon.Statistics.goldCollected = 500 + i * 900;
			Dungeon.challenges = i >= causes.length - 2 ? 3 : 0; Dungeon.customSeedText = i == 4 ? "ABC-DEF-GHI" : "";
			com.shatteredpixel.shatteredpixeldungeon.Rankings.INSTANCE.submit(i == 5, causes[i]);
		}
		Dungeon.challenges = 0; Dungeon.customSeedText = ""; h.heroClass = keep;
	}

	static void amulet(Runnable frame, Controls oc) {
		run("amulet", frame, oc, () -> Game.switchScene(com.shatteredpixel.shatteredpixeldungeon.scenes.AmuletScene.class), () -> {
			Scene sc = Game.scene();
			layout("amulet", sc);
			com.shatteredpixel.shatteredpixeldungeon.scenes.AmuletScene am = (com.shatteredpixel.shatteredpixeldungeon.scenes.AmuletScene) sc;
			check(am.pebxelPane != null, "amulet: its story is in a scrolling pane");
			float[] pr = rect(am.pebxelPane); WindowFit.inside(pr, "amulet: the story's pane");
			for (Ui.Part p : Controls.buttons(sc)) check(!over(rect((Component) p.g), pr, 1), "amulet: button '" + p.text + "' clear of the story");
			float h0 = am.pebxelPane.content().height(), vis = am.pebxelPane.height();
			check(h0 > vis, "amulet: the story is longer than its pane here (" + h0 + " in " + vis + ")");
			int n = 0; while (am.pebxelScroll(1) && n < 20) n++;
			check(n >= 1 && !am.pebxelScroll(1), "amulet: Down reads the story to its end (" + n + " pages)");
			while (am.pebxelScroll(-1) && n-- > -20) { }
			check(am.pebxelAtTop(), "amulet: Up reads back to its top");
			reach("amulet", sc, frame, oc);
			check(find(sc, "") != null && Controls.buttons(sc).size() >= 2, "amulet: Leave and Stay both pressable");
		});
	}

	static void others(Runnable frame, Controls oc) {
		for (String n : new String[]{"StartScene", "SurfaceScene"}) {
			run(n, frame, oc, () -> { try { Game.switchScene(Class.forName(P + "scenes." + n).asSubclass(Scene.class)); } catch (Exception e) { throw new RuntimeException(e); } }, () -> {
				Scene sc = Game.scene(); layout(n, sc); reach(n, sc, frame, oc);
			});
		}
	}

	/** The rankings, several entries; a row's detail window, each of its tabs; Back out. Then the game-over road to them. */
	static void rankings(Runnable frame, Controls oc) {
		run("rankings", frame, oc, () -> { records(); Game.switchScene(com.shatteredpixel.shatteredpixeldungeon.scenes.RankingsScene.class); }, () -> {
			Scene sc = Game.scene();
			layout("rankings", sc); reach("rankings", sc, frame, oc);
			int rows = 0; Ui.Part first = null;
			for (Ui.Part p : Controls.buttons(sc)) if (p.g.getClass().getSimpleName().equals("Record")) { rows++; if (first == null) first = p; }
			check(rows >= 5, "rankings: several rows are there (" + rows + ")");
			// a row opens the detail window; its tabs (the first row, and the row of the game played with challenges)
			String[] tn = {"stats", "talents", "items", "badges", "challenges"};
			boolean five = false; int idx = 0;
			for (Ui.Part row : new ArrayList<>(Controls.buttons(sc))) {
				if (!row.g.getClass().getSimpleName().equals("Record")) continue;
				Ui.press(row.g); for (int i = 0; i < 10; i++) frame.run();
				Window w = Ui.topWindow(); check(w instanceof com.shatteredpixel.shatteredpixeldungeon.windows.WndRanking, "rankings: a row opens its game");
				List<Component> tabs = WindowFit.tabs(w);
				if (idx == 0 || (tabs.size() == 5 && !five)) {
					five |= tabs.size() == 5;
					for (int t = 0; t < tabs.size(); t++) {
						Ui.press(tabs.get(t)); for (int i = 0; i < 6; i++) frame.run();
						win("ranking " + (tabs.size() == 5 ? "(challenged) " : "") + tn[Math.min(t, 4)], frame, oc, false);
					}
				}
				idx++;
				oc.button(Controls.BACK); for (int i = 0; i < 4; i++) frame.run();
				check(Ui.topWindow() == null, "rankings: Back closes the game's window");
			}
			System.out.println("    rankings: " + idx + " rows opened, a challenged game " + (five ? "seen" : "not among them"));
			oc.button(Controls.BACK); for (int i = 0; i < 30; i++) frame.run();
			check(!(Game.scene() instanceof com.shatteredpixel.shatteredpixeldungeon.scenes.RankingsScene), "rankings: Back leaves the rankings (" + Game.scene().getClass().getSimpleName() + ")");
		});
	}

	/** The hero dies: the game-over menu by a button, through to the rankings. */
	static void gameOver(Runnable frame, Controls oc) {
		run("game over", frame, oc, () -> {
			toGame(frame);
			com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat k = new com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat();
			Dungeon.hero.HP = 1; Dungeon.fail(k); Dungeon.hero.die(k);
			for (int i = 0; i < 200; i++) frame.run();
		}, () -> {
			for (int i = 0; i < 3 && Ui.topWindow() == null; i++) { oc.button(Controls.SELECT); for (int j = 0; j < 20; j++) frame.run(); }
			Window w = Ui.topWindow();
			System.out.println("    game over: window " + (w == null ? null : w.getClass().getSimpleName()) + " scene " + Game.scene().getClass().getSimpleName());
			check(w != null, "game over: a button opens the menu");
			win("game over menu", frame, oc, false);
			Ui.Part r = findIn(w, "Rankings"); check(r != null, "game over: the menu offers the Rankings");
			Ui.press(r.g); for (int i = 0; i < 60; i++) frame.run();
			check(Game.scene() instanceof com.shatteredpixel.shatteredpixeldungeon.scenes.RankingsScene, "game over: Rankings opens the rankings (" + Game.scene().getClass().getSimpleName() + ")");
			layout("rankings after a death", Game.scene()); reach("rankings after a death", Game.scene(), frame, oc);
			shot("scene_rankings_after_death");
		});
	}

	public static void check(Runnable frame, Controls oc) {
		alchemy(frame, oc);
		amulet(frame, oc);
		others(frame, oc);
		gameOver(frame, oc);
		rankings(frame, oc);
		if (!fails.isEmpty()) throw new AssertionError(fails.size() + " scenes fail: " + fails);
	}
}
