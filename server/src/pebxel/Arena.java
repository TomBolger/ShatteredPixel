package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.*;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.*;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Reflection;
import java.util.*;
import pebxel.bot.*;

/**
 * (M2) The fight model against the whole game: real turns (the actor thread, the mobs' own AI: zaps, beams, a monk's
 * parry, a scorpio keeping away, a brute's rage running down), not just Char.attack as BotModelTest's duels. An open
 * room is carved in the first floor; for each set-up (a hero as a head start would have him, a mob kind at a distance)
 * the hero fights N times by the model's own policy (strike the weakest beside him; walk up to a shooter; else wait),
 * with health he can't run out of, and the health lost over the first 5 turns and over the whole fight is set against
 * FightModel's (the 5 turns: the damage-rate check, FightCal's FIGHTRATE). Prints only.
 * Run: Arena [N] (from spd/core/src/main/assets, like BotModelTest); env ONLY=substring picks set-ups.
 */
public final class Arena {
	private Arena() { }

	static final class Setup {
		String name; int lvl, str, wl, al, depth, hp0, dist, count; Class<? extends KindOfWeapon> w; Class<? extends Armor> ar; Class<? extends Mob> mob; boolean water;
		Setup(String name, int depth, int lvl, int str, Class<? extends KindOfWeapon> w, int wl, Class<? extends Armor> ar, int al, Class<? extends Mob> mob, int dist, int count) {
			this.name = name; this.depth = depth; this.lvl = lvl; this.str = str; this.w = w; this.wl = wl; this.ar = ar; this.al = al; this.mob = mob; this.dist = dist; this.count = count;
			hp0 = 20 + 5 * (lvl - 1);
		}
		Setup water() { water = true; return this; }
		Class<? extends Mob> mob2; int dist2;
		Setup plus(Class<? extends Mob> m, int d) { mob2 = m; dist2 = d; name = name + " + " + m.getSimpleName() + " @" + d; return this; }
	}

	static WatchApp app; static int center; static List<Integer> room = new ArrayList<>();

	public static void main(String[] a) throws Exception {
		int N = a.length > 0 ? Integer.parseInt(a[0]) : 100;
		BotModelTest.start(HeroClass.WARRIOR, 77);
		app = BotModelTest.app;
		app.graphics.delta = 0.2f;
		String only = System.getenv("ONLY");
		List<Setup> ss = setups();
		System.out.println(String.format(Locale.ROOT, "%-30s %6s %6s %5s | %6s %6s | %6s %6s | %5s %5s", "set-up", "eng5", "mod5", "ratio", "engAll", "modAll", "engDie", "modDie", "engT", "modT"));
		for (Setup s : ss) if (only == null || s.name.contains(only)) run(s, N);
		System.exit(0);
	}

	static List<Setup> setups() {
		List<Setup> l = new ArrayList<>();
		// sewers / prison (L5, d4: shortsword+1, leather+1)
		l.add(new Setup("d4 goo", 4, 5, 11, Shortsword.class, 1, LeatherArmor.class, 1, Goo.class, 1, 1));
		l.add(new Setup("d4 piranha", 4, 5, 11, Shortsword.class, 1, LeatherArmor.class, 1, Piranha.class, 1, 1).water());
		l.add(new Setup("d8 piranha", 8, 9, 13, Sword.class, 2, MailArmor.class, 2, Piranha.class, 1, 1).water());
		l.add(new Setup("d8 dm100 @4", 8, 9, 13, Sword.class, 2, MailArmor.class, 2, DM100.class, 4, 1));
		// caves (head start 11: L12, STR 14, tier 3 +3)
		l.add(new Setup("d12 brute", 12, 12, 14, Sword.class, 3, MailArmor.class, 3, Brute.class, 1, 1));
		l.add(new Setup("d12 red shaman @4", 12, 12, 14, Sword.class, 3, MailArmor.class, 3, Shaman.RedShaman.class, 4, 1));
		l.add(new Setup("d12 blue shaman @4", 12, 12, 14, Sword.class, 3, MailArmor.class, 3, Shaman.BlueShaman.class, 4, 1));
		l.add(new Setup("d12 purple shaman @4", 12, 12, 14, Sword.class, 3, MailArmor.class, 3, Shaman.PurpleShaman.class, 4, 1));
		l.add(new Setup("d12 red shaman @1", 12, 12, 14, Sword.class, 3, MailArmor.class, 3, Shaman.RedShaman.class, 1, 1));
		l.add(new Setup("d12 spinner", 12, 12, 14, Sword.class, 3, MailArmor.class, 3, Spinner.class, 1, 1));
		l.add(new Setup("d12 dm200 @3", 12, 12, 14, Sword.class, 3, MailArmor.class, 3, DM200.class, 3, 1));
		l.add(new Setup("d12 dm201 @3", 12, 12, 14, Sword.class, 3, MailArmor.class, 3, DM201.class, 3, 1));
		l.add(new Setup("d12 bat", 12, 12, 14, Sword.class, 3, MailArmor.class, 3, Bat.class, 1, 1));
		l.add(new Setup("d12 piranha", 12, 12, 14, Sword.class, 3, MailArmor.class, 3, Piranha.class, 1, 1).water());
		l.add(new Setup("d12 brute", 12, 12, 14, Sword.class, 3, MailArmor.class, 3, Brute.class, 1, 1).plus(Shaman.BlueShaman.class, 4));
		l.add(new Setup("d12 bat", 12, 12, 14, Sword.class, 3, MailArmor.class, 3, Bat.class, 1, 1).plus(Shaman.RedShaman.class, 5));
		l.add(new Setup("d12 2 brutes", 12, 12, 14, Sword.class, 3, MailArmor.class, 3, Brute.class, 1, 2));
		// city (head start 16: L17, STR 16, tier 4 +5)
		l.add(new Setup("d17 warlock @4", 17, 17, 16, Longsword.class, 5, ScaleArmor.class, 5, Warlock.class, 4, 1));
		l.add(new Setup("d17 warlock @1", 17, 17, 16, Longsword.class, 5, ScaleArmor.class, 5, Warlock.class, 1, 1));
		l.add(new Setup("d17 monk", 17, 17, 16, Longsword.class, 5, ScaleArmor.class, 5, Monk.class, 1, 1));
		l.add(new Setup("d17 golem", 17, 17, 16, Longsword.class, 5, ScaleArmor.class, 5, Golem.class, 1, 1));
		l.add(new Setup("d17 succubus", 17, 17, 16, Longsword.class, 5, ScaleArmor.class, 5, Succubus.class, 1, 1));
		l.add(new Setup("d17 ghoul", 17, 17, 16, Longsword.class, 5, ScaleArmor.class, 5, Ghoul.class, 1, 1));
		l.add(new Setup("d17 fire elemental @3", 17, 17, 16, Longsword.class, 5, ScaleArmor.class, 5, Elemental.FireElemental.class, 3, 1));
		l.add(new Setup("d17 monk", 17, 17, 16, Longsword.class, 5, ScaleArmor.class, 5, Monk.class, 1, 1).plus(Warlock.class, 4));
		l.add(new Setup("d17 2 monks", 17, 17, 16, Longsword.class, 5, ScaleArmor.class, 5, Monk.class, 1, 2));
		// halls (head start 21: L22, STR 18, tier 5 +6)
		l.add(new Setup("d22 eye @4", 22, 22, 18, Greatsword.class, 6, PlateArmor.class, 6, Eye.class, 4, 1));
		l.add(new Setup("d22 eye @1", 22, 22, 18, Greatsword.class, 6, PlateArmor.class, 6, Eye.class, 1, 1));
		l.add(new Setup("d22 scorpio @4", 22, 22, 18, Greatsword.class, 6, PlateArmor.class, 6, Scorpio.class, 4, 1));
		l.add(new Setup("d22 succubus", 22, 22, 18, Greatsword.class, 6, PlateArmor.class, 6, Succubus.class, 1, 1));
		l.add(new Setup("d22 ripper", 22, 22, 18, Greatsword.class, 6, PlateArmor.class, 6, RipperDemon.class, 2, 1));
		l.add(new Setup("d22 golem", 22, 22, 18, Greatsword.class, 6, PlateArmor.class, 6, Golem.class, 1, 1));
		l.add(new Setup("d22 2 eyes @4", 22, 22, 18, Greatsword.class, 6, PlateArmor.class, 6, Eye.class, 4, 2));
		return l;
	}

	/** An open room of the first floor's middle, walls round it, nothing else on the floor. */
	static void carve(boolean water) {
		com.shatteredpixel.shatteredpixeldungeon.levels.Level L = Dungeon.level;
		int w = L.width(), h = L.height(), cx = w / 2, cy = h / 2, r = Math.min(6, Math.min(cx, cy) - 2);
		room.clear();
		for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
			int c = x + y * w; boolean in = Math.abs(x - cx) <= r && Math.abs(y - cy) <= r;
			L.map[c] = in ? (water ? Terrain.WATER : Terrain.EMPTY) : Terrain.WALL;
			if (in) room.add(c);
		}
		L.traps.clear(); L.plants.clear(); L.heaps.clear();
		for (com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob b : L.blobs.values()) b.fullyClear();
		L.buildFlagMaps(); L.cleanWalls();
		try { GameScene.updateMap(); } catch (Throwable e) { }
		center = cx + cy * w;
	}

	static void clearMobs() {
		for (Mob m : new ArrayList<>(Dungeon.level.mobs)) { Dungeon.level.mobs.remove(m); Actor.remove(m); if (m.sprite != null) m.sprite.killAndErase(); }
		for (Char c : new ArrayList<>(Actor.chars())) if (!(c instanceof Hero)) { Actor.remove(c); if (c.sprite != null) c.sprite.killAndErase(); }
	}

	static void hero(Setup s) {
		Hero h = Dungeon.hero;
		BotModelTest.reset(h, new BotModelTest.Setup("", s.lvl, s.str, null, 0, null, 0, null, s.depth, 0));
		KindOfWeapon w = Reflection.newInstance(s.w); w.level(s.wl); w.identify(); w.cursed = false;
		Armor ar = Reflection.newInstance(s.ar); ar.level(s.al); ar.identify(); ar.cursed = false;
		h.belongings.weapon = w; h.belongings.armor = ar; h.belongings.backpack.items.clear();
		for (Buff b : new ArrayList<>(h.buffs())) if (!(b instanceof com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger) && !(b instanceof com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Regeneration)) b.detach();
		h.HTBoost = 0; h.HT = s.hp0; h.HP = s.hp0;
		try { java.lang.reflect.Field f = com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger.class.getDeclaredField("level"); f.setAccessible(true); f.setFloat(h.buff(com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger.class), 0); } catch (Exception e) { throw new RuntimeException(e); }   // (starving takes HT/1000 a turn: a million health)
		if (h.pos != center) { h.pos = center; if (h.sprite != null) h.sprite.place(center); }
	}

	static int cellAt(int d, int k) {
		int w = Dungeon.level.width();
		int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { -1, -1 } };
		int[] v = dirs[k % dirs.length];
		return center + v[0] * d + v[1] * d * w;
	}

	static void run(Setup s, int N) {
		Dungeon.depth = s.depth;
		carve(s.water);
		double e5 = 0, eAll = 0, eT = 0; int eDie = 0, n5 = 0, done = 0, stuck = 0;
		FightModel.Result m5 = null, mAll = null, mAllU = null;
		for (int n = 0; n < N; n++) {
			clearMobs(); hero(s);
			Hero h = Dungeon.hero;
			List<Mob> ms = new ArrayList<>();
			for (int k = 0; k < s.count; k++) {
				Mob m = Reflection.newInstance(s.mob);
				m.pos = cellAt(s.dist, k); m.state = m.HUNTING; m.alignment = Char.Alignment.ENEMY; m.EXP = 0;
				GameScene.add(m); BotModelTest.see(m, h); ms.add(m);
			}
			if (s.mob2 != null) { Mob m = Reflection.newInstance(s.mob2); m.pos = cellAt(s.dist2, 1); m.state = m.HUNTING; m.alignment = Char.Alignment.ENEMY; m.EXP = 0; GameScene.add(m); BotModelTest.see(m, h); ms.add(m); }
			Dungeon.observe();
			if (n == 0) {
				Fighter me = HeroView.of(h); me.heals = 0; me.rangedShots = 0; me.shield = 0;
				List<Fighter> fs = new ArrayList<>(); for (Mob m : ms) { Fighter f = FoeView.of(m, h); f.asleep = false; fs.add(f); }
				Fighter mu = me.copy(); mu.hp = 1000000;
				m5 = FightModel.simulate(mu, fs, 0, 2000, 11, 5);
				mAllU = FightModel.simulate(mu, fs, 0, 2000, 12);
				mAll = FightModel.simulate(me, fs, 0, 2000, 13);
				if (System.getenv("ADBG") != null) { System.out.println("   " + me); for (Fighter f : fs) System.out.println("   " + f + " zap " + f.zapDmg + " burst " + f.burstDmg); }
			}
			h.HTBoost = 1000000; h.updateHT(false); h.HP = h.HT;   // (HTBoost: the game's own updateHT (a Degrade, a level) keeps it)
			int start = h.HP; float t0 = Actor.now(), last = t0, el = 0; boolean got5 = false; int frames = 0, idle = 0;
			while (frames < 20000) {
				Lockstep.frame(app); frames++;
				if (Ui.topWindow() != null) { if (System.getenv("TDBG") != null && frames % 500 == 0) System.out.println("   window " + Ui.topWindow()); Ui.topWindow().hide(); continue; }
				if (!h.ready) { if (++idle > 3000) { stuck++; break; } continue; }
				float nowA = Actor.now(); if (nowA >= last) el += nowA - last; last = nowA; float t = el;   // (Actor.fixTime shifts the clock back now and then)
				if (!got5 && t >= 5) { e5 += start - h.HP; n5++; got5 = true; }
				List<Mob> all = new ArrayList<>(); for (Mob m : Dungeon.level.mobs) if (m.isAlive() && m.alignment == Char.Alignment.ENEMY) all.add(m);   // (a ghoul's partner, a necromancer's skeleton: in the fight too)
				boolean alive = !all.isEmpty();
				if (!alive || t >= 100) { if (!alive) done++; break; }
				act(h, all); idle = 0;
			}
			if (!got5) { e5 += start - h.HP; n5++; }
			int loss = start - h.HP; eAll += loss; if (System.getenv("TDBG") != null) System.out.println("   trial " + n + " frames " + frames + " idle " + idle + " ready " + h.ready + " loss " + loss + " t " + el + " HT " + h.HT + " buffs " + h.buffs()); if (loss >= s.hp0) eDie++; eT += el;
		}
		clearMobs();
		Dungeon.hero.HTBoost = 0; Dungeon.hero.HT = Dungeon.hero.HP = s.hp0;
		double r5 = e5 / Math.max(1, n5);
		System.out.println(String.format(Locale.ROOT, "%-30s %6.1f %6.1f %5.2f | %6.1f %6.1f | %6.3f %6.3f | %5.1f %5.1f%s", s.name, r5, m5.lossMean, m5.lossMean > 0 ? r5 / m5.lossMean : 0,
				eAll / N, mAllU.lossMean, eDie / (double) N, mAll.pDeath, eT / N, mAllU.time, done < N ? "  (" + (N - done) + " unfinished" + (stuck > 0 ? ", " + stuck + " stuck" : "") + ")" : ""));
	}

	/** The model's policy: strike the weakest beside; walk up to a shooter; else wait. */
	static void act(Hero h, List<Mob> ms) {
		Mob tg = null, sh = null;
		for (Mob m : ms) {
			if (!m.isAlive() || !Dungeon.level.mobs.contains(m)) continue;
			if (Dungeon.level.adjacent(m.pos, h.pos)) { if (tg == null || m.HP < tg.HP) tg = m; }
			else if (sh == null || Dungeon.level.distance(m.pos, h.pos) < Dungeon.level.distance(sh.pos, h.pos)) sh = m;
		}
		if (tg != null && !h.isCharmedBy(tg)) { if (h.handle(tg.pos)) h.next(); else h.rest(false); return; }   // (charmed: no blow at the charmer, the turn waited)
		if (sh != null && (FoeView.shooter(sh) || Char.hasProp(sh, Char.Property.IMMOVABLE))) {
			int best = -1, bd = Integer.MAX_VALUE;
			for (int k : PathFinder.NEIGHBOURS8) { int c = h.pos + k; if (!Dungeon.level.passable[c] || Actor.findChar(c) != null) continue; int d = Dungeon.level.distance(c, sh.pos); if (d < bd) { bd = d; best = c; } }
			if (best >= 0 && h.handle(best)) { h.next(); return; }
		}
		h.rest(false);
	}
}
