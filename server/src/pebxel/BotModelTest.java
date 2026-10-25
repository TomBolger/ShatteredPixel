package pebxel;

import com.badlogic.gdx.Files;
import com.shatteredpixel.shatteredpixeldungeon.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.*;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.*;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.watabou.noosa.Game;
import com.watabou.utils.FileUtils;
import com.watabou.utils.Random;
import com.watabou.utils.Reflection;
import java.util.*;
import pebxel.bot.*;

/**
 * Checks the bot's decision models (common/src/pebxel/bot) against the game itself. Part 1, plain math: Dist's
 * distributions against Random.NormalIntRange / Random.Float drawn many times. Part 2, the real engine: a headless
 * game is started, then for each set-up (class, level, STR, weapon, armour, a mob kind, depth) the hero and a fresh mob
 * fight it out N times with the game's own Char.attack (hit rolls, damage, armour, procs, a slime's cap, a brute's
 * rage, a skeleton's burst), on generators of the test's own, and the health lost is compared with FightModel's
 * simulation and its closed sum (duelCost). Prints a table and "MODELTEST pass/fail" lines; exit code 1 on a fail.
 * Run: ./botmodel.sh (from shattered/).
 */
public final class BotModelTest {
	static int fails;

	public static void main(String[] a) throws Exception {
		math();
		engine(a.length > 0 ? Integer.parseInt(a[0]) : 400);
		decisions();
		System.out.println("MODELTEST " + (fails == 0 ? "all pass" : fails + " fail"));
		System.exit(fails == 0 ? 0 : 1);
	}

	static void check(String what, boolean ok, String detail) {
		System.out.println("MODELTEST " + (ok ? "pass" : "FAIL") + " " + what + "  " + detail);
		if (!ok) fails++;
	}

	static void math() {
		Random.pushGenerator(42);
		for (int[] r : new int[][] { { 1, 4 }, { 2, 10 }, { 0, 16 }, { 5, 25 }, { 3, 3 } }) {
			Dist d = Dist.normal(r[0], r[1]); int N = 200000; double[] c = new double[r[1] - r[0] + 1];
			for (int i = 0; i < N; i++) c[Random.NormalIntRange(r[0], r[1]) - r[0]]++;
			double err = 0; for (int k = 0; k < c.length; k++) err = Math.max(err, Math.abs(c[k] / N - d.p[k]));
			check("NormalIntRange(" + r[0] + "," + r[1] + ")", err < 0.005, String.format("max pmf error %.4f, mean %.3f", err, d.mean()));
		}
		for (double[] r : new double[][] { { 10, 5 }, { 5, 10 }, { 12, 12 }, { 30, 4 } }) {
			int N = 200000, h = 0; for (int i = 0; i < N; i++) if (Random.Float((float) r[0]) >= Random.Float((float) r[1])) h++;
			double p = Dist.hit(r[0], r[1]);
			check("hit(" + r[0] + "," + r[1] + ")", Math.abs(h / (double) N - p) < 0.006, String.format("model %.4f engine %.4f", p, h / (double) N));
		}
		Dist x = Dist.normal(2, 10), y = Dist.normal(0, 5); int N = 200000; double s = 0;
		for (int i = 0; i < N; i++) s += Math.max(0, Random.NormalIntRange(2, 10) - Random.NormalIntRange(0, 5));
		check("E[max(X-Y,0)]", Math.abs(s / N - x.minus0(y).mean()) < 0.03, String.format("model %.3f engine %.3f", x.minus0(y).mean(), s / N));
		Random.popGenerator();
	}

	static WatchApp app;

	static void start(HeroClass cls, long seed) {
		String dir = ".pebxel-modeltest-" + ProcessHandle.current().pid();
		Game.version = "4.0.2"; Game.versionCode = 922;
		Screen.set("emery");
		app = new WatchApp(new WatchGame(), dir, false);
		SPDSettings.set(app.getPreferences(SPDSettings.DEFAULT_PREFS_FILE));
		FileUtils.setDefaultFileProperties(Files.FileType.External, dir + "/");
		app.create();
		for (int i = 0; i < 5; i++) Lockstep.frame(app);
		SPDSettings.intro(false);
		GamesInProgress.selectedClass = cls; GamesInProgress.curSlot = 1;
		Dungeon.hero = null; Dungeon.daily = Dungeon.dailyReplay = false; Dungeon.initSeed(); Dungeon.seed = seed;
		InterlevelScene.mode = InterlevelScene.Mode.DESCEND;
		Game.switchScene(InterlevelScene.class);
		for (int f = 0; f < 20000; f++) {
			Lockstep.frame(app);
			if (Game.scene() instanceof InterlevelScene) { if (f % 3 == 0) HandoffTest.pressOn(); continue; }
			if (Game.scene() instanceof GameScene && Dungeon.hero != null && Dungeon.hero.ready && Ui.topWindow() == null) return;
			if (Ui.topWindow() != null) Ui.topWindow().hide();
		}
		throw new RuntimeException("no game");
	}

	/** One set-up: the hero's level / STR / gear, the mob kind and depth. */
	static final class Setup {
		String name; int lvl, str, wl, al, depth, hp0; Class<? extends KindOfWeapon> w; Class<? extends Armor> ar; Class<? extends Mob> mob;
		Setup(String name, int lvl, int str, Class<? extends KindOfWeapon> w, int wl, Class<? extends Armor> ar, int al, Class<? extends Mob> mob, int depth, int hp0) {
			this.name = name; this.lvl = lvl; this.str = str; this.w = w; this.wl = wl; this.ar = ar; this.al = al; this.mob = mob; this.depth = depth; this.hp0 = hp0;
		}
	}

	static void engine(int N) {
		start(HeroClass.WARRIOR, 77);
		Hero h = Dungeon.hero;
		List<Setup> ss = Arrays.asList(
				new Setup("L1 shortsword/cloth vs rat", 1, 10, WornShortsword.class, 0, ClothArmor.class, 0, Rat.class, 1, 20),
				new Setup("L1 vs snake", 1, 10, WornShortsword.class, 0, ClothArmor.class, 0, Snake.class, 1, 20),
				new Setup("L2 vs gnoll", 2, 10, WornShortsword.class, 0, ClothArmor.class, 0, Gnoll.class, 2, 25),
				new Setup("L3 vs crab", 3, 10, WornShortsword.class, 0, ClothArmor.class, 0, Crab.class, 3, 30),
				new Setup("L4 vs slime (cap)", 4, 11, Sword.class, 0, LeatherArmor.class, 0, Slime.class, 4, 35),
				new Setup("L4 vs swarm", 4, 11, WornShortsword.class, 1, LeatherArmor.class, 1, Swarm.class, 4, 35),
				new Setup("L5 mail+2 vs skeleton (burst)", 5, 12, Shortsword.class, 1, MailArmor.class, 2, Skeleton.class, 6, 40),
				new Setup("L6 heavy mace STR-2 vs guard", 6, 12, Mace.class, 0, LeatherArmor.class, 2, Guard.class, 7, 45),
				new Setup("L6 plate STR-6 vs thief", 6, 12, Shortsword.class, 0, PlateArmor.class, 0, Thief.class, 7, 45),
				new Setup("L9 vs bat (leech)", 9, 13, Sword.class, 2, MailArmor.class, 3, Bat.class, 11, 60),
				new Setup("L10 vs brute (rage)", 10, 14, Sword.class, 2, MailArmor.class, 3, Brute.class, 11, 65),
				new Setup("L5 vs goo", 5, 11, Shortsword.class, 1, LeatherArmor.class, 2, Goo.class, 5, 40),
				new Setup("L14 vs ghoul", 14, 15, Longsword.class, 2, ScaleArmor.class, 4, Ghoul.class, 16, 85),
				new Setup("L16 spear (slow) vs monk", 16, 16, Spear.class, 4, ScaleArmor.class, 5, Monk.class, 17, 95));
		System.out.println(String.format("%-34s %8s %8s %8s | %8s %8s %8s | %8s", "set-up", "eng loss", "sim loss", "sum", "eng die", "sim die", "eng hits", "sim hits"));
		for (Setup s : ss) {
			Dungeon.depth = s.depth;
			Object fld = null;
			try { java.lang.reflect.Field fa = Hero.class.getDeclaredField("attackSkill"), fd = Hero.class.getDeclaredField("defenseSkill"); fa.setAccessible(true); fd.setAccessible(true); fa.setInt(h, 9 + s.lvl); fd.setInt(h, 4 + s.lvl); } catch (Exception e) { throw new RuntimeException(e); }
			h.lvl = s.lvl; h.STR = s.str; h.HT = 20 + 5 * (s.lvl - 1);
			KindOfWeapon w = Reflection.newInstance(s.w); w.level(s.wl); w.identify(); w.cursed = false;
			Armor ar = Reflection.newInstance(s.ar); ar.level(s.al); ar.identify(); ar.cursed = false;
			h.belongings.weapon = w; h.belongings.armor = ar;
			int cell = -1; for (int k : com.watabou.utils.PathFinder.NEIGHBOURS8) { int c = h.pos + k; if (Dungeon.level.passable[c] && Actor.findChar(c) == null) { cell = c; break; } }
			// the engine: N duels, the hero first; hero time += attackDelay, the mob's too; the health lost counted (the hero
			// can't die here: its HP is set high and the loss compared with hp0)
			double lossSum = 0; int dies = 0; long hHits = 0, hTries = 0, mTries = 0; double hDmg = 0, mDmg = 0;
			Fighter me = null, foe = null;
			for (int n = 0; n < N; n++) {
				reset(h, s);
				Mob m = Reflection.newInstance(s.mob);
				m.pos = cell; m.state = m.HUNTING; m.alignment = Char.Alignment.ENEMY;
				GameScene.add(m);
				see(m, h);
				if (n == 0) { h.HP = s.hp0; me = HeroView.of(h); foe = FoeView.of(m, h); foe.asleep = false; }
				h.HP = h.HT = 100000;
				Random.pushGenerator(1000 + n);
				double tH = 0, tM = Random.Float() * m.attackDelay(); int start = h.HP;
				for (int g = 0; g < 2000 && m.isAlive(); g++) {
					if (tH <= tM) { int hp = m.HP + m.shielding(); h.attack(m); hTries++; if (m.HP + m.shielding() < hp || !m.isAlive()) hHits++; hDmg += hp - Math.max(0, m.HP + m.shielding()); tH += h.attackDelay(); }
					else { int hp = h.HP; m.attack(h); mTries++; mDmg += hp - h.HP; tM += m.attackDelay(); }
				}
				Random.popGenerator();
				int loss = start - h.HP; lossSum += loss; if (loss >= s.hp0) dies++;
				if (m.isAlive() || Dungeon.level.mobs.contains(m)) { Dungeon.level.mobs.remove(m); Actor.remove(m); }
				if (m.sprite != null) m.sprite.killAndErase();
				for (com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff b : new ArrayList<>(h.buffs())) if (!(b instanceof com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger) && !(b instanceof com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Regeneration)) b.detach();
			}
			h.HT = 20 + 5 * (s.lvl - 1); h.HP = h.HT;
			me.hp = s.hp0; me.shield = 0; me.heals = 0; me.rangedShots = 0;
			FightModel.Result r = FightModel.simulate(me, Collections.singletonList(foe), 0, 2000, 5);
			// the simulation's hero dies at hp0, the engine's can't: compare the loss of the fights the hero lives through too
			double engLoss = lossSum / N, sum = FightModel.duelCost(me, foe);
			double hitE = hHits / (double) Math.max(1, hTries), hitM = Dist.hit(me.acc, foe.eva) * (1 - Fighter.blow(me.dmg, foe).p[0]);
			System.out.println(String.format("%-34s %8.1f %8.1f %8.1f | %8.3f %8.3f %8.3f | %8.3f", s.name, engLoss, simLossUncapped(me, foe), sum, dies / (double) N, r.pDeath, hitE, hitM));
			double simU = simLossUncapped(me, foe);
			check(s.name + ": mean loss", Math.abs(simU - engLoss) <= Math.max(1.5, 0.2 * engLoss), String.format("engine %.1f model %.1f", engLoss, simU));
			check(s.name + ": P(loss >= " + s.hp0 + ")", Math.abs(dies / (double) N - r.pDeath) <= 0.08, String.format("engine %.3f model %.3f", dies / (double) N, r.pDeath));
			if (System.getenv("MTDBG") != null) System.out.println(String.format("   per blow: hero engine %.2f model %.2f | mob engine %.2f model %.2f | delays hero %.2f mob %.2f\n   %s\n   %s",
					hDmg / Math.max(1, hTries), Dist.hit(me.acc, foe.eva) * Fighter.blow(me.dmg, foe).mean(), mDmg / Math.max(1, mTries), Dist.hit(foe.acc, me.eva) * Fighter.blow(foe.dmg, me).mean(), h.attackDelay(), foe.delay, me, foe));
		}
	}

	/** The mob has seen the hero (else every blow on it is a surprise: Mob.surprisedBy). */
	static void see(Mob m, Hero h) {
		try {
			java.lang.reflect.Field es = Mob.class.getDeclaredField("enemySeen"); es.setAccessible(true); es.setBoolean(m, true);
			java.lang.reflect.Field en = Mob.class.getDeclaredField("enemy"); en.setAccessible(true); en.set(m, h);
			m.fieldOfView = new boolean[Dungeon.level.length()]; Dungeon.level.updateFieldOfView(m, m.fieldOfView);
		} catch (Exception e) { throw new RuntimeException(e); }
	}

	static void reset(Hero h, Setup s) {
		try { java.lang.reflect.Field fa = Hero.class.getDeclaredField("attackSkill"), fd = Hero.class.getDeclaredField("defenseSkill"); fa.setAccessible(true); fd.setAccessible(true); fa.setInt(h, 9 + s.lvl); fd.setInt(h, 4 + s.lvl); } catch (Exception e) { throw new RuntimeException(e); }
		h.lvl = s.lvl; h.exp = 0; h.STR = s.str;
	}

	/** The simulation with a hero that can't die (as the engine duels here): the mean loss. */
	static double simLossUncapped(Fighter me, Fighter foe) {
		Fighter m2 = me.copy(); m2.hp = 100000;
		FightModel.Result r = FightModel.simulate(m2, Collections.singletonList(foe), 0, 2000, 6);
		return r.lossMean;
	}

	static <T extends Item> T make(Class<T> c, int lvl, boolean known) {
		T i = Reflection.newInstance(c); i.level(lvl); i.cursed = false;
		if (known) i.identify(); else { i.levelKnown = false; i.cursedKnown = false; }
		return i;
	}
	static void gear(Hero h, int lvl, int str, KindOfWeapon w, Armor ar) {
		Setup s = new Setup("", lvl, str, null, 0, null, 0, null, Dungeon.depth, 0); reset(h, s);
		h.HT = h.HP = 20 + 5 * (lvl - 1); h.belongings.weapon = w; h.belongings.armor = ar; h.belongings.backpack.items.clear();
	}

	/** GearValue, UpgradePlan, Identify, Trade on made-up set-ups: the choices a good player would make. */
	static void decisions() {
		Hero h = Dungeon.hero;
		Dungeon.depth = 3;
		// 1. armour too heavy for the STR: not worn; once the STR is there: worn
		gear(h, 4, 10, make(WornShortsword.class, 0, true), make(ClothArmor.class, 0, true));
		MailArmor mail = make(MailArmor.class, 0, true); h.belongings.backpack.items.add(mail);
		check("gear: mail at STR 10 (4 short) not worn", GearValue.bestEquip(h, 0.05) == null, String.format("now %.2f with %.2f", GearValue.now(h), GearValue.costWith(h, mail, GearValue.now(h))));
		h.STR = 14;
		check("gear: mail at STR 14 worn", GearValue.bestEquip(h, 0.05) == mail, String.format("now %.2f with %.2f", GearValue.now(h), GearValue.costWith(h, mail, GearValue.now(h))));
		// 2. an unknown leather vs known cloth at STR 12: worth the risk (75% +0 and up; a curse 1 in 3 but most are mild)
		gear(h, 4, 12, make(WornShortsword.class, 0, true), make(ClothArmor.class, 0, true));
		LeatherArmor la = make(LeatherArmor.class, 0, false); h.belongings.backpack.items.add(la);
		double b = GearValue.now(h), u = GearValue.costWith(h, la, b);
		LeatherArmor lk = make(LeatherArmor.class, 1, true);
		double k = GearValue.costWith(h, lk, b);
		check("gear: unknown leather costs more than a known +1 one", u > k, String.format("cloth %.2f unknown leather %.2f known +1 %.2f", b, u, k));
		check("gear: unknown leather still beats cloth at STR 12", GearValue.bestEquip(h, 0.05) == la, String.format("cloth %.2f unknown %.2f", b, u));
		// 3. a better weapon: a sword (tier 3) at STR 14 over the worn shortsword
		gear(h, 5, 14, make(WornShortsword.class, 0, true), make(LeatherArmor.class, 0, true));
		Sword sw = make(Sword.class, 0, true); h.belongings.backpack.items.add(sw);
		check("gear: sword at STR 14 over the worn shortsword", GearValue.bestEquip(h, 0.05) == sw, String.format("now %.2f with %.2f", GearValue.now(h), GearValue.costWith(h, sw, GearValue.now(h))));
		check("gear: the worn shortsword is spare once the sword is in the pack? (no: it's worn)", !GearValue.spare(h).contains(h.belongings.weapon), "");
		Dirk dk = make(Dirk.class, 0, true); h.belongings.backpack.items.add(dk);
		h.belongings.weapon = sw; h.belongings.backpack.items.remove(sw);
		check("gear: a dirk +0 is spare beside a worn sword", GearValue.spare(h).contains(dk), "spare " + GearValue.spare(h));
		// 4. upgrades: early, cloth +0 and a shortsword: armour or weapon? (printed); the Mage's staff first
		gear(h, 4, 11, make(WornShortsword.class, 0, true), make(LeatherArmor.class, 0, true));
		Item t = UpgradePlan.target(h);
		System.out.println("  upgrade at depth 3, leather + worn shortsword, STR 11: " + (t == null ? "hold" : t.getClass().getSimpleName()) + "  " + UpgradePlan.last);
		check("upgrade: a target chosen at depth 3 with leather", t != null, "");
		h.belongings.backpack.items.add(make(ScaleArmor.class, 0, true));
		Dungeon.depth = 8; gear(h, 9, 13, make(Shortsword.class, 2, true), make(MailArmor.class, 1, true));
		ScaleArmor sc = make(ScaleArmor.class, 0, true); h.belongings.backpack.items.add(sc);
		t = UpgradePlan.target(h);
		System.out.println("  upgrade at depth 8, mail+1 worn, scale+0 in the pack, STR 13: " + (t == null ? "hold" : t.getClass().getSimpleName()) + "  " + UpgradePlan.last);
		// 5. identify: a label found 4 times by depth 4 is likelier Healing than one found once
		Dungeon.depth = 4;
		java.util.List<Class<?>> unk = new java.util.ArrayList<>(com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion.getUnknown());
		if (unk.size() >= 2) {
			Identify.observe(h);
			try {
				java.lang.reflect.Field f = Identify.class.getDeclaredField("found"); f.setAccessible(true);
				@SuppressWarnings("unchecked") java.util.Map<Class<?>, Integer> m = (java.util.Map<Class<?>, Integer>) f.get(null); m.clear();
				m.put(unk.get(0), 4); m.put(unk.get(1), 1);
				java.lang.reflect.Field md = Identify.class.getDeclaredField("maxDepth"); md.setAccessible(true); md.setInt(null, 4);
			} catch (Exception e) { throw new RuntimeException(e); }
			Class<?> heal = com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength.class;   // (the commonest unknown kind: Healing is the Warrior's from the start)
			double p4 = Identify.p(unk.get(0), heal), p1 = Identify.p(unk.get(1), heal), sum = 0;
			for (Class<?> c : unk) sum += Identify.p(c, heal);
			check("identify: found 4x likelier Strength than found 1x", p4 > p1, String.format("P(heal) 4x %.2f 1x %.2f (sum over labels %.2f)", p4, p1, sum));
			check("identify: P(Strength) sums to 1 over the unknown labels", Math.abs(sum - 1) < 0.02 || !unk.contains(heal), String.format("%.3f", sum));
		}
		// 6. trade: with 300 gold and little food, an Ankh and food before a scroll of magic mapping
		gear(h, 6, 12, make(Shortsword.class, 1, true), make(LeatherArmor.class, 1, true));
		Dungeon.gold = 300;
		double wa = Trade.worth(h, new com.shatteredpixel.shatteredpixeldungeon.items.Ankh()), wf = Trade.worth(h, new com.shatteredpixel.shatteredpixeldungeon.items.food.Food());
		check("trade: an Ankh and food worth buying with no food", wa > 50 && wf > 30, String.format("ankh %.0f food %.0f", wa, wf));
		h.belongings.backpack.items.add(dk);
		h.belongings.backpack.items.add(make(Dirk.class, 0, true));
		check("trade: a spare dirk is sold", Trade.sellPick(h) instanceof Dirk, "sell " + Trade.sellPick(h));
	}
}
