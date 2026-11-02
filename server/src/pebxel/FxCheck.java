package pebxel;

import com.badlogic.gdx.Files;
import com.shatteredpixel.shatteredpixeldungeon.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Game;
import com.watabou.utils.FileUtils;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/**
 * Visual-effect audit: does each effect of stock Shattered reach the watch's picture? Each check triggers one effect
 * in a real game, runs the frames of its life through the watch simulator (WatchSim draws what the watch draws), and
 * counts the pixels that differ from the frame just before (a control run with no trigger gives the background's own
 * drift). The verdict is SHOWS when the peak change is clear, WEAK when it is small, MISSING when it is nothing.
 * Args: picture-folder [check names...] (none: all). Env SEED, SCREEN. Output: one line per check (name, peak changed
 * pixels, control drift, frames with change) and a strip of frames in the folder.
 */
public class FxCheck {
	static WatchSim sim; static Link link; static Controls controls; static Touches touches; static com.shatteredpixel.shatteredpixeldungeon.ui.Window dummy;
	static WatchApp app;
	static final String P = "com.shatteredpixel.shatteredpixeldungeon.";

	interface Fx { void run() throws Exception; }
	static final LinkedHashMap<String, Fx> FX = new LinkedHashMap<>();
	static final Map<String, Integer> LIFE = new HashMap<>();   // frames to watch
	static void fx(String n, int frames, Fx f) { FX.put(n, f); LIFE.put(n, frames); }

	static void frame() throws Exception {
		Gallery.tick();
		if (Game.scene() instanceof InterlevelScene) HandoffTest.pressOn();
		app.frame(); controls.frame();
		Thread.sleep(1);
		sim.advance(Game.elapsed); link.frame(Game.elapsed); for (byte[] m : link.take(100000)) sim.apply(m);
	}
	static void pump(int n) throws Exception { for (int i = 0; i < n; i++) frame(); }
	static int[] snap() { sim.render(); return sim.fb.clone(); }
	static boolean[] noisy;   // pixels that change on their own (water, torches, the hero's breathing)
	static int diff(int[] a, int[] b) {
		int n = 0, W = Screen.w;
		for (int i = 0; i < a.length; i++) { if (a[i] == b[i] || (noisy != null && noisy[i])) continue; int y = i / W; if (y < 24 || y > Screen.h - 36) continue; n++; }   // (the HUD rows aside)
		return n;
	}
	static Mob mob(String cls, int cell) throws Exception {
		Mob m = (Mob) Class.forName(P + "actors.mobs." + cls).getDeclaredConstructor().newInstance();
		m.pos = cell; GameScene.add(m); m.state = m.PASSIVE; return m;
	}
	/** A free cell a few steps from the hero, in his view. */
	static int near(int k) { List<Integer> s = Gallery.spots(1, 6); return s.get(Math.min(k, s.size() - 1)); }

	static void setup() throws Exception {
		Game.version = "4.0.2"; Game.versionCode = 922;
		Screen.set(System.getenv().getOrDefault("SCREEN", "emery"));
		WatchGame game = new WatchGame(); game.drawAlways = true;
		app = new WatchApp(game, ".pebxel-fx", true);
		SPDSettings.set(app.getPreferences(SPDSettings.DEFAULT_PREFS_FILE));
		FileUtils.setDefaultFileProperties(Files.FileType.External, ".pebxel-fx/");
		app.create();
		link = new Link(new Assets()); sim = new WatchSim(new File("."), new Assets());
		controls = new Controls(); link.planner = controls::plan; touches = new Touches();
		for (int i = 0; i < 5; i++) app.frame();
		SPDSettings.intro(false);
		GamesInProgress.selectedClass = HeroClass.values()[Integer.parseInt(System.getenv().getOrDefault("CLASS", "0"))];
		GamesInProgress.curSlot = 1;
		Dungeon.hero = null; Dungeon.daily = Dungeon.dailyReplay = false; Dungeon.initSeed();
		Dungeon.seed = Long.parseLong(System.getenv().getOrDefault("SEED", "5"));
		InterlevelScene.mode = InterlevelScene.Mode.DESCEND;
		Game.switchScene(InterlevelScene.class);
		app.graphics.soft.raster = false;
		for (int i = 0; i < 1500 && !(Game.scene() instanceof GameScene && Dungeon.hero != null && Dungeon.hero.ready && Ui.topWindow() == null); i++) {
			if (i % 40 == 39 && (Game.scene() instanceof InterlevelScene || Ui.topWindow() != null)) controls.button(Controls.SELECT);
			frame();
		}
		Dungeon.hero.HP = Dungeon.hero.HT = 1000;
		for (Mob m : Dungeon.level.mobs.toArray(new Mob[0])) { m.destroy(); if (m.sprite != null) m.sprite.killAndErase(); }
		pump(60);
	}

	public static void main(String[] a) throws Exception {
		File dir = new File(a[0]); dir.mkdirs();
		registry();
		setup();
		Set<String> only = new LinkedHashSet<>(Arrays.asList(a).subList(1, a.length));
		// the background's own drift: the same number of frames with nothing triggered
		int[] c0 = snap(); noisy = new boolean[c0.length]; int nz = 0;
		for (int i = 0; i < 160; i++) { frame(); int[] s = snap(); for (int k = 0; k < s.length; k++) if (s[k] != c0[k] && !noisy[k]) { noisy[k] = true; nz++; } }
		int drift = 0;
		System.out.println("control: " + nz + " px change on their own (left out)");
		for (Map.Entry<String, Fx> e : FX.entrySet()) {
			String n = e.getKey(); if (!only.isEmpty() && !only.contains(n)) continue;
			try {
				for (com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob b : new ArrayList<>(Dungeon.level.blobs.values())) { b.fullyClear(); b.volume = 0; }
				pump(40);   // (the last effect dies away)
				if (Dungeon.hero.HP < Dungeon.hero.HT) Dungeon.hero.HP = Dungeon.hero.HT;
				while (!Dungeon.hero.ready) frame();
				if (rat != null) { rat.destroy(); if (rat.sprite != null) rat.sprite.killAndErase(); rat = null; }   // (a fresh one: no state kept from the last check)
				rat(); pump(5);   // (the stand-in creature is there before the picture is taken)
				int frames = LIFE.get(n), peak = 0, shown = 0; List<int[]> strip = new ArrayList<>();
				// a control window first: what changes by itself over as many frames (the stand-in's own animation) is left out
				int[] c1 = snap(); boolean[] keep = noisy.clone();
				for (int f = 0; f < frames; f++) { frame(); int[] s2 = snap(); for (int k = 0; k < s2.length; k++) if (s2[k] != c1[k]) keep[k] = true; }
				boolean[] was = noisy; noisy = keep;
				pump(10);
				int[] before = snap();
				e.getValue().run();
				int step = Math.max(1, frames / 6);
				for (int f = 0; f < frames; f++) {
					frame(); int[] s2 = snap(); int d = diff(before, s2);
					if (d > peak) peak = d; if (d >= 8) shown++;
					if (f % step == step - 1 && strip.size() < 6) strip.add(s2);
				}
				noisy = was;
				String v = peak < 8 ? "MISSING?" : peak < 40 ? "WEAK?" : "shows";
				System.out.printf("%-22s peak %5d  frames-with-change %3d/%d  %s%n", n, peak, shown, frames, v);
				save(new File(dir, n.replace(':', '_') + ".png"), before, strip, focus(n));
			} catch (Throwable t) { System.out.println(n + ": EXCEPTION " + t); t.printStackTrace(System.out); }
		}
		System.out.println("pictures the link could not make: " + link.unknown);
		System.exit(0);
	}

	/** Where on the watch's screen to look for an effect: the stand-in creature's, or between him and the hero. */
	static com.watabou.utils.PointF fxAt;   // a check's own place to look (world pixels)
	static int[] focus(String n) {
		if (fxAt != null) { com.watabou.utils.PointF p = fxAt; fxAt = null; Camera c = Camera.main; float k = (float) Screen.w / Game.width;
			return new int[]{(int) (((p.x - c.scroll.x) * c.zoom + c.x) * k), (int) (((p.y - c.scroll.y) * c.zoom + c.y) * k)}; }
		if (n.startsWith("banner:") || n.startsWith("screen:")) return null;
		try {
			Camera c = Camera.main; float k = (float) Screen.w / Game.width;
			com.watabou.utils.PointF a = rat().sprite.center(), b = hc();
			boolean r = n.startsWith("sprite:") || n.startsWith("emo:") || n.startsWith("effect:");
			float x = r ? a.x : (a.x + b.x) / 2, y = r ? a.y : (a.y + b.y) / 2;
			return new int[]{(int) (((x - c.scroll.x) * c.zoom + c.x) * k), (int) (((y - c.scroll.y) * c.zoom + c.y) * k)};
		} catch (Exception e) { return new int[]{Screen.w / 2, Screen.h / 2}; }
	}

	/** One row: the picture before, then frames of the effect, each the 64x64 round the focus, enlarged 3x. */
	static void save(File f, int[] before, List<int[]> strip, int[] at) throws Exception {
		int W = Screen.w, H = Screen.h, R = Integer.getInteger("fxr", 64), Z = Integer.getInteger("fxz", 3);
		if (at == null) { R = Math.min(W, H); Z = 1; at = new int[]{W / 2, H / 2}; }   // (the whole screen at its size: banners, flashes)
		List<int[]> all = new ArrayList<>(); all.add(before); all.addAll(strip);
		BufferedImage img = new BufferedImage(R * Z * all.size(), R * Z, BufferedImage.TYPE_INT_RGB);
		int x0 = Math.max(0, Math.min(W - R, at[0] - R / 2)), y0 = Math.max(0, Math.min(H - R, at[1] - R / 2));
		for (int k = 0; k < all.size(); k++) for (int y = 0; y < R * Z; y++) for (int x = 0; x < R * Z; x++) img.setRGB(k * R * Z + x, y, all.get(k)[(y0 + y / Z) * W + x0 + x / Z]);
		javax.imageio.ImageIO.write(img, "png", f);
	}

	static Mob rat; static final String S = P + "sprites.CharSprite$State";
	static Mob rat() throws Exception {
		if (rat == null || !rat.isAlive() || rat.sprite == null || rat.sprite.parent == null) {
			int c = Gallery.spots(1, 6).get(1);
			rat = mob("Rat", c); rat.HP = rat.HT = 1000; rat.state = rat.PASSIVE;
			for (int i = 0; i < 3; i++) frame();
		}
		return rat;
	}
	static com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite hs() { return Dungeon.hero.sprite; }
	static com.watabou.noosa.Group par() { return Dungeon.hero.sprite.parent; }
	static com.watabou.utils.PointF hc() { return Dungeon.hero.sprite.center(); }
	@SuppressWarnings({"unchecked", "rawtypes"})
	static void state(String n, String st) {
		com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite.State s = com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite.State.valueOf(st);
		fx("sprite:" + n, 50, () -> { Mob m = rat(); m.sprite.remove(s); m.sprite.add(s); });
	}
	static void burst(String n, int speck, int count) { fx("speck:" + n, 40, () -> com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter.center(near(0)).burst(com.shatteredpixel.shatteredpixeldungeon.effects.Speck.factory(speck), count)); }
	static void gas(String n) {
		fx("gas:" + n.toLowerCase(), 50, () -> GameScene.add(com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob.seed(near(2), 200, (Class<? extends com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob>) Class.forName(P + "actors.blobs." + n))));
	}
	static void bolt(String n, int type) {
		fx("bolt:" + n, 40, () -> com.shatteredpixel.shatteredpixeldungeon.effects.MagicMissile.boltFromChar(par(), type, hs(), rat().pos, () -> { }));
	}

	static void registry() {
		fx("cell:search", 40, () -> Dungeon.hero.search(true));
		fx("cell:clairvoyance", 40, () -> { for (int c = 0; c < Dungeon.level.length(); c++) if (Dungeon.level.distance(c, Dungeon.hero.pos) <= 6) GameScene.checkedCell(c, Dungeon.hero.pos); });
		fx("cell:targeted", 40, () -> GameScene.targetedCell(near(2), 0.8f));
		fx("cell:ripple", 40, () -> { int w = -1; for (int c = 0; c < Dungeon.level.length(); c++) if (Dungeon.level.map[c] == com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.WATER && Dungeon.level.heroFOV[c] && Dungeon.level.distance(c, Dungeon.hero.pos) < 6) { w = c; break; } if (w >= 0) { GameScene.ripple(w); fxAt = com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap.tileCenterToWorld(w); } else System.out.println("no water in view"); });
		// ---- the sprite's own states and signs
		for (String st : new String[]{"BURNING", "LEVITATING", "INVISIBLE", "PARALYSED", "FROZEN", "ILLUMINATED", "CHILLED", "DARKENED", "MARKED", "HEALING", "SHIELDED", "HEARTS", "GLOWING", "AURA"}) state(st.toLowerCase(), st);
		fx("sprite:frozen-clear", 20, () -> { Mob m = rat(); m.sprite.remove(com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite.State.FROZEN); });
		fx("sprite:status-damage", 40, () -> rat().sprite.showStatus(com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite.NEGATIVE, "-7"));
		fx("sprite:status-heal", 40, () -> hs().showStatus(com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite.POSITIVE, "+5"));
		fx("sprite:status-word", 40, () -> hs().showStatus(com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite.WARNING, "poisoned"));
		fx("sprite:status-levelup", 60, () -> { Dungeon.hero.earnExp(Dungeon.hero.maxExp() - Dungeon.hero.exp, Gallery.class); });
		fx("sprite:hit-flash", 8, () -> rat().sprite.flash());
		fx("sprite:blood", 30, () -> rat().sprite.bloodBurstA(hs().center(), 10));
		fx("sprite:burst", 30, () -> rat().sprite.burst(0xFFCC00, 8));
		fx("sprite:die-fade", 40, () -> { Mob m = rat(); m.sprite.die(); });
		fx("sprite:attack", 20, () -> rat().sprite.attack(Dungeon.hero.pos));
		fx("sprite:zap", 20, () -> rat().sprite.zap(Dungeon.hero.pos));
		fx("sprite:operate", 20, () -> hs().operate(Dungeon.hero.pos));
		fx("emo:alert", 40, () -> rat().sprite.showAlert());
		fx("emo:sleep", 60, () -> rat().sprite.showSleep());
		fx("emo:lost", 40, () -> rat().sprite.showLost());
		fx("effect:surprise", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.Surprise.hit(rat()));
		fx("effect:wound", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.Wound.hit(rat()));
		fx("effect:swap", 30, () -> { Mob m = rat(); int a = m.pos, b = Dungeon.hero.pos; new com.shatteredpixel.shatteredpixeldungeon.effects.Swap(m, Dungeon.hero); });
		fx("effect:push", 30, () -> { Mob m = rat(); int to = near(3); if (to != m.pos) { Actor.add(new com.shatteredpixel.shatteredpixeldungeon.effects.Pushing(m, m.pos, to)); } });
		fx("item:drop-fall", 30, () -> Dungeon.level.drop(new com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing(), near(0)).sprite.drop());
		fx("item:glow-enchanted", 60, () -> { com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Sword w = new com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Sword(); w.enchant(new com.shatteredpixel.shatteredpixeldungeon.items.weapon.enchantments.Blazing()); w.identify(); Dungeon.level.drop(w, near(0)).sprite.drop(); });
		fx("item:glow-heap-gold", 30, () -> Dungeon.level.drop(new com.shatteredpixel.shatteredpixeldungeon.items.Gold(50), near(1)).sprite.drop());
		// ---- bolts and rays
		String[] bn = {"MAGIC_MISSILE", "FROST", "FIRE", "CORROSION", "FOLIAGE", "FORCE", "BEACON", "SHADOW", "RAINBOW", "EARTH", "WARD", "SHAMAN_RED", "SHAMAN_BLUE", "SHAMAN_PURPLE", "SHAMAN_ELMO", "ELMO", "POISON", "LIGHT_MISSILE"};
		for (int i = 0; i < bn.length; i++) bolt(bn[i].toLowerCase(), i);
		for (int t : new int[]{100, 101, 102, 103, 107, 108, 109, 110, 111, 112}) bolt("cone" + t, t);
		fx("ray:lightning", 30, () -> par().add(new com.shatteredpixel.shatteredpixeldungeon.effects.Lightning(Dungeon.hero.pos, rat().pos, null)));
		fx("ray:death", 30, () -> par().add(new com.shatteredpixel.shatteredpixeldungeon.effects.Beam.DeathRay(hc(), rat().sprite.center())));
		fx("ray:light", 30, () -> par().add(new com.shatteredpixel.shatteredpixeldungeon.effects.Beam.LightRay(hc(), rat().sprite.center())));
		fx("ray:health", 30, () -> par().add(new com.shatteredpixel.shatteredpixeldungeon.effects.Beam.HealthRay(hc(), rat().sprite.center())));
		fx("ray:chains", 40, () -> par().add(new com.shatteredpixel.shatteredpixeldungeon.effects.Chains(Dungeon.hero.pos, rat().pos, com.shatteredpixel.shatteredpixeldungeon.effects.Effects.Type.CHAIN, () -> { })));
		fx("ray:fireball", 30, () -> par().add(new com.shatteredpixel.shatteredpixeldungeon.effects.Fireball()));
		fx("flare", 40, () -> new com.shatteredpixel.shatteredpixeldungeon.effects.Flare(6, 32).color(0xFFFF00, true).show(hs(), 2f));
		fx("flare:pink", 40, () -> new com.shatteredpixel.shatteredpixeldungeon.effects.Flare(6, 32).color(0xFF4CD2, true).show(hs(), 2f));
		fx("circlearc", 40, () -> new com.shatteredpixel.shatteredpixeldungeon.effects.CircleArc(18, 12).color(0xFFFF00, true).show(hs(), 1.5f));
		// ---- items
		fx("item:identify", 60, () -> par().add(new com.shatteredpixel.shatteredpixeldungeon.effects.Identification(hc().offset(0, -16))));
		fx("item:enchant", 60, () -> com.shatteredpixel.shatteredpixeldungeon.effects.Enchanting.show(Dungeon.hero, new com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Sword()));
		fx("item:transmute", 60, () -> com.shatteredpixel.shatteredpixeldungeon.effects.Transmuting.show(Dungeon.hero, new com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Sword(), new com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Mace()));
		fx("item:degrade-weapon", 40, () -> par().add(com.shatteredpixel.shatteredpixeldungeon.effects.Degradation.weapon(hc())));
		fx("item:degrade-armor", 40, () -> par().add(com.shatteredpixel.shatteredpixeldungeon.effects.Degradation.armor(hc())));
		fx("item:spell-food", 40, () -> com.shatteredpixel.shatteredpixeldungeon.effects.SpellSprite.show(Dungeon.hero, com.shatteredpixel.shatteredpixeldungeon.effects.SpellSprite.FOOD));
		fx("item:spell-map", 40, () -> com.shatteredpixel.shatteredpixeldungeon.effects.SpellSprite.show(Dungeon.hero, com.shatteredpixel.shatteredpixeldungeon.effects.SpellSprite.MAP));
		fx("item:spell-charge", 40, () -> com.shatteredpixel.shatteredpixeldungeon.effects.SpellSprite.show(Dungeon.hero, com.shatteredpixel.shatteredpixeldungeon.effects.SpellSprite.CHARGE));
		fx("item:upgrade-glow", 40, () -> new com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade().upgrade(Dungeon.hero));
		fx("screen:flash-white", 20, () -> GameScene.flash(0x80FFFFFF));
		fx("screen:flash-red", 20, () -> GameScene.flash(0x80FF0000));
		fx("screen:levelup-stars", 60, () -> { Dungeon.hero.earnExp(Dungeon.hero.maxExp() - Dungeon.hero.exp, Gallery.class); });
		fx("banner:boss-slain", 100, () -> GameScene.bossSlain());
		fx("banner:badge", 100, () -> com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene.showBadge(com.shatteredpixel.shatteredpixeldungeon.Badges.Badge.MONSTERS_SLAIN_1));
		// ---- specks (CellEmitter bursts)
		String[] sk = {"HEALING", "STAR", "LIGHT", "QUESTION", "UP", "SCREAM", "BONE", "WOOL", "ROCK", "NOTE", "CHANGE", "HEART", "BUBBLE", "STEAM", "COIN", "DISCOVER", "EVOKE", "MASTERY", "KIT", "RATTLE", "JET", "TOXIC", "PARALYSIS", "DUST", "FORGE", "CONFUSION", "RED_LIGHT", "CALM", "SMOKE", "STENCH", "BLIZZARD"};
		for (String k : sk) { try { burst(k.toLowerCase(), com.shatteredpixel.shatteredpixeldungeon.effects.Speck.class.getField(k).getInt(null), 6); } catch (Exception e) { System.out.println("no speck " + k); } }
		fx("emit:blast", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter.center(near(1)).burst(com.shatteredpixel.shatteredpixeldungeon.effects.particles.BlastParticle.FACTORY, 20));
		fx("emit:spark", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter.center(near(1)).burst(com.shatteredpixel.shatteredpixeldungeon.effects.particles.SparkParticle.FACTORY, 8));
		fx("emit:flame", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter.get(near(1)).burst(com.shatteredpixel.shatteredpixeldungeon.effects.particles.FlameParticle.FACTORY, 8));
		fx("emit:elmo", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter.get(near(1)).burst(com.shatteredpixel.shatteredpixeldungeon.effects.particles.ElmoParticle.FACTORY, 8));
		fx("emit:shadow", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter.get(near(1)).burst(com.shatteredpixel.shatteredpixeldungeon.effects.particles.ShadowParticle.CURSE, 8));
		fx("emit:purple", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter.get(near(1)).burst(com.shatteredpixel.shatteredpixeldungeon.effects.particles.PurpleParticle.BURST, 8));
		fx("emit:rainbow", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter.get(near(1)).burst(com.shatteredpixel.shatteredpixeldungeon.effects.particles.RainbowParticle.BURST, 8));
		fx("emit:energy", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter.get(near(1)).burst(com.shatteredpixel.shatteredpixeldungeon.effects.particles.EnergyParticle.FACTORY, 8));
		fx("emit:leaf", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter.get(near(1)).burst(com.shatteredpixel.shatteredpixeldungeon.effects.particles.LeafParticle.GENERAL, 8));
		fx("emit:snow", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter.get(near(1)).burst(com.shatteredpixel.shatteredpixeldungeon.effects.particles.SnowParticle.FACTORY, 8));
		fx("emit:web", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter.get(near(1)).burst(com.shatteredpixel.shatteredpixeldungeon.effects.particles.WebParticle.FACTORY, 8));
		fx("emit:wool", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter.get(near(1)).burst(com.shatteredpixel.shatteredpixeldungeon.effects.particles.WoolParticle.FACTORY, 8));
		fx("emit:earth", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter.get(near(1)).burst(com.shatteredpixel.shatteredpixeldungeon.effects.particles.EarthParticle.FACTORY, 8));
		fx("emit:smoke", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter.get(near(1)).burst(com.shatteredpixel.shatteredpixeldungeon.effects.particles.SmokeParticle.FACTORY, 8));
		fx("emit:blood", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter.get(near(1)).burst(com.shatteredpixel.shatteredpixeldungeon.effects.particles.BloodParticle.BURST, 8));
		fx("emit:corrosion", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter.get(near(1)).burst(com.shatteredpixel.shatteredpixeldungeon.effects.particles.CorrosionParticle.SPLASH, 8));
		fx("emit:poison", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter.get(near(1)).burst(com.shatteredpixel.shatteredpixeldungeon.effects.particles.PoisonParticle.SPLASH, 8));
		fx("emit:shaft", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter.get(near(1)).burst(com.shatteredpixel.shatteredpixeldungeon.effects.particles.ShaftParticle.FACTORY, 8));
		fx("emit:sacrificial", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter.get(near(1)).burst(com.shatteredpixel.shatteredpixeldungeon.effects.particles.SacrificialParticle.FACTORY, 8));
		fx("emit:wind", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter.get(near(1)).burst(com.shatteredpixel.shatteredpixeldungeon.effects.particles.WindParticle.FACTORY, 8));
		fx("emit:challenge", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter.get(near(1)).burst(com.shatteredpixel.shatteredpixeldungeon.effects.particles.ChallengeParticle.FACTORY, 8));
		fx("emit:spectralwall", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter.get(near(1)).burst(com.shatteredpixel.shatteredpixeldungeon.effects.particles.SpectralWallParticle.FACTORY, 8));
		fx("emit:splash", 30, () -> com.shatteredpixel.shatteredpixeldungeon.effects.Splash.at(near(1), 0xFFCC2222, 12));
		// ---- gas and terrain blobs
		for (String g : new String[]{"ToxicGas", "ParalyticGas", "ConfusionGas", "Fire", "Freezing", "Electricity", "Regrowth", "SmokeScreen", "StormCloud", "Blizzard", "Inferno", "CorrosiveGas", "Web", "Foliage", "SacrificialFire"}) gas(g);
	}
}
