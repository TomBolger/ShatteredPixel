package pebxel;

import com.badlogic.gdx.Files;
import com.shatteredpixel.shatteredpixeldungeon.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Blacksmith;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Ghost;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Imp;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClassArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretRoom;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.watabou.noosa.Game;
import com.watabou.utils.FileUtils;
import com.watabou.utils.Random;
import java.util.*;

/**
 * The gear odds table's source (agent UP, harness only): the real v4.0.2 level generator run for N seeds, floors 1-25,
 * nothing played. For each floor, the armour and melee weapons a hero could get there, by tier and by source: heaps
 * (open, skeletons, chests), locked chests (a golden key on the floor), crystal chests (vaults: one of them), secret
 * rooms, shops, mimics, statues (their weapon / armour), the Ghost's reward (floors 2-4: armour or weapon), the
 * Blacksmith's and the Imp's, and the mobs' drops (guards: armour, skeletons: weapons, golems, armoured brutes; each
 * floor's spawned mobs all killed, at their loot chances with the decay: Guard.lootChance etc.). Also the Upgrade
 * scrolls and Strength potions a floor holds. Prints the per-floor expected counts (lambda) and P(at least one) by tier,
 * which pebxel.bot.GearOdds carries as constants. Run: java ... pebxel.GearOddsGen N (from spd/core/src/main/assets).
 */
public final class GearOddsGen {
	public static void main(String[] a) throws Exception {
		int N = a.length > 0 ? Integer.parseInt(a[0]) : 200;
		String dir = ".pebxel-odds-" + ProcessHandle.current().pid();
		Game.version = "4.0.2"; Game.versionCode = 922;
		Screen.set("emery");
		WatchApp app = new WatchApp(new WatchGame(), dir, false);
		SPDSettings.set(app.getPreferences(SPDSettings.DEFAULT_PREFS_FILE));
		FileUtils.setDefaultFileProperties(Files.FileType.External, dir + "/");
		app.create();
		for (int i = 0; i < 5; i++) Lockstep.frame(app);
		SPDSettings.intro(false);
		String[] SRC = { "heap", "chest", "locked", "crystal", "secret", "shop", "mimic", "statue", "ghost", "smith", "imp", "drop" };
		int S = SRC.length;
		// [floor][src][tier 1..5] expected count (armour); same for weapons; also [floor] any (summed over sources) P(>=1 of tier >= t)
		double[][][] ar = new double[27][S][7], wp = new double[27][S][7];
		double[][] arAny = new double[27][7], wpAny = new double[27][7], arAnyOpen = new double[27][7], wpAnyOpen = new double[27][7];
		double[] sou = new double[27], pos = new double[27];
		// the share of each source a hero is counted to get (heaps 1; a secret room's 0.4: found by searching; a vault's
		// crystal chests 0.5: one key; the shop's 0.8: its price; the Ghost's 0.5: armour or weapon; the Blacksmith's and
		// the Imp's 0.3: their quests, then one choice among several)
		double[] W = { 1, 1, 1, 0.5, 0.4, 0.8, 1, 1, 0.5, 0.3, 0.3, 1 };
		double[][][] wq = new double[2][27][7];   // (the weighted chance of tier >= t on the floor)
		for (int g = 0; g < N; g++) {
			GamesInProgress.selectedClass = HeroClass.values()[g % 6]; GamesInProgress.curSlot = 1;
			Dungeon.hero = null; Dungeon.daily = Dungeon.dailyReplay = false; Dungeon.initSeed(); Dungeon.seed = 5000 + g;
			Dungeon.init();
			Random.pushGenerator(Dungeon.seed * 31 + 7);   // (the mobs' loot rolls: our own stream)
			for (int d = 1; d <= 25; d++) {
				Dungeon.depth = d; Dungeon.branch = 0;
				Random.pushGenerator(Dungeon.seedCurDepth());
				Level L = Dungeon.newLevel();
				Random.popGenerator();
				Dungeon.level = L;
				boolean[][] got = new boolean[2][7], gotOpen = new boolean[2][7]; double[][] miss = new double[2][7]; for (double[] m : miss) Arrays.fill(m, 1);
				List<Object[]> found = new ArrayList<>();   // {item, src}
				for (Heap h : L.heaps.valueList()) {
					String src;
					boolean secret = L instanceof com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel && ((com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel) L).room(h.pos) instanceof SecretRoom;
					switch (h.type) {
						case FOR_SALE: src = "shop"; break;
						case LOCKED_CHEST: src = "locked"; break;
						case CRYSTAL_CHEST: src = "crystal"; break;
						case CHEST: src = "chest"; break;
						default: src = "heap";
					}
					if (secret) src = "secret";
					for (Item i : h.items) found.add(new Object[] { i, src });
				}
				for (Mob m : L.mobs) {
					if (m instanceof Mimic && ((Mimic) m).items != null) for (Item i : ((Mimic) m).items) found.add(new Object[] { i, "mimic" });
					if (m instanceof Statue && ((Statue) m).weapon() != null) found.add(new Object[] { ((Statue) m).weapon(), "statue" });
					if (m instanceof ArmoredStatue && ((ArmoredStatue) m).armor() != null) found.add(new Object[] { ((ArmoredStatue) m).armor(), "statue" });
					if (m instanceof Ghost) { if (Ghost.Quest.armor != null) found.add(new Object[] { Ghost.Quest.armor, "ghost" }); if (Ghost.Quest.weapon != null) found.add(new Object[] { Ghost.Quest.weapon, "ghost" }); }
					if (m instanceof Blacksmith && Blacksmith.Quest.smithRewards != null) for (Item i : Blacksmith.Quest.smithRewards) found.add(new Object[] { i, "smith" });
					if (m instanceof com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Imp) for (Item i : Imp.Quest.rewardOptions) found.add(new Object[] { i, "imp" });
					if (m instanceof Guard || m instanceof Skeleton || m instanceof Golem || m instanceof ArmoredBrute || m instanceof HermitCrab) {
						try { if (Random.Float() < m.lootChance()) { Item i = m.createLoot(); if (i != null) found.add(new Object[] { i, "drop" }); } } catch (Throwable e) { }
					}
				}
				for (Object[] f : found) {
					Item i = (Item) f[0]; int s = Arrays.asList(SRC).indexOf((String) f[1]);
					if (i instanceof ScrollOfUpgrade) sou[d] += i.quantity();
					if (i instanceof PotionOfStrength) pos[d] += i.quantity();
					int t = i instanceof ClassArmor ? 0 : i instanceof Armor ? ((Armor) i).tier : i instanceof MeleeWeapon && !(i instanceof MagesStaff) ? ((MeleeWeapon) i).tier : 0;
					if (t <= 0) continue;
					int k = i instanceof Armor ? 0 : 1;
					(k == 0 ? ar : wp)[d][s][t] += 1.0 / N;
					got[k][t] = true;
					for (int u = 1; u <= t; u++) miss[k][u] *= 1 - W[s];
					if (s <= 2) gotOpen[k][t] = true;   // (heaps, chests, locked chests: what the bot surely gets)
				}
				for (int t = 1; t <= 5; t++) {
					boolean x0 = false, x1 = false, o0 = false, o1 = false;
					for (int u = t; u <= 5; u++) { x0 |= got[0][u]; x1 |= got[1][u]; o0 |= gotOpen[0][u]; o1 |= gotOpen[1][u]; }
					if (x0) arAny[d][t] += 1.0 / N; if (x1) wpAny[d][t] += 1.0 / N; if (o0) arAnyOpen[d][t] += 1.0 / N; if (o1) wpAnyOpen[d][t] += 1.0 / N;
				}
				for (int k = 0; k < 2; k++) for (int t = 1; t <= 5; t++) wq[k][d][t] += (1 - miss[k][t]) / N;
				Dungeon.level = null;
			}
			Random.popGenerator();
			if (g % 20 == 0) System.err.println("game " + g);
		}
		for (int k = 0; k < 2; k++) {
			System.out.println(k == 0 ? "== ARMOUR: expected count per floor by source, tiers 2..5" : "== WEAPONS (melee, no staff): expected count per floor by source, tiers 2..5");
			double[][][] x = k == 0 ? ar : wp;
			for (int d = 1; d <= 25; d++) {
				StringBuilder b = new StringBuilder(String.format("d%2d", d));
				for (int s = 0; s < S; s++) { double tot = 0; for (int t = 1; t <= 5; t++) tot += x[d][s][t]; if (tot < 0.005) continue; b.append(' ').append(SRC[s]).append('['); for (int t = 1; t <= 5; t++) b.append(String.format("%.2f%s", x[d][s][t], t < 5 ? " " : "")); b.append(']'); }
				System.out.println(b);
			}
			System.out.println(k == 0 ? "== ARMOUR P(>=1 of tier >= t on the floor), t=2..5 | open sources only" : "== WEAPONS P(>=1 of tier >= t on the floor), t=2..5 | open sources only");
			double[][] any = k == 0 ? arAny : wpAny, op = k == 0 ? arAnyOpen : wpAnyOpen;
			for (int d = 1; d <= 25; d++) System.out.println(String.format("d%2d  %.3f %.3f %.3f %.3f | %.3f %.3f %.3f %.3f", d, any[d][2], any[d][3], any[d][4], any[d][5], op[d][2], op[d][3], op[d][4], op[d][5]));
		}
		StringBuilder b = new StringBuilder("== SOU / POS per floor:");
		for (int d = 1; d <= 25; d++) b.append(String.format(" d%d %.2f/%.2f", d, sou[d], pos[d]));
		System.out.println(b);
		for (int k = 0; k < 2; k++) {
			StringBuilder j = new StringBuilder("	static final float[][] " + (k == 0 ? "ARM" : "WPN") + " = {   // (floor 1..25: P(tier >= 2..5 found there), " + N + " seeds)\n		");
			for (int d = 1; d <= 25; d++) j.append(String.format(Locale.ROOT, "{ %.3ff, %.3ff, %.3ff, %.3ff }%s", wq[k][d][2], wq[k][d][3], wq[k][d][4], wq[k][d][5], d < 25 ? (d % 5 == 0 ? ",\n		" : ", ") : " };\n"));
			System.out.print(j);
		}
		System.exit(0);
	}
}
