package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.*;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.*;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.*;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.*;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.*;
import com.shatteredpixel.shatteredpixeldungeon.plants.*;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import java.util.*;

/**
 * The healthy-hero case (agent AS): a hero at high health with a full pack against one ordinary foe (SKEL: Skeleton by
 * default) hunting him; prints the Assistant's list. Env: HPPCT (default 92), DEPTH (7), FOE (class name), DIST (4).
 * EXPECT=enemy|heal: exit 1 unless plan A is that (HT=70 LVL=8 WL=3 AL=3 FOES=1 DIST=1: HPPCT=92 enemy, HPPCT=18 heal). ADBG=1 shows the brain's values. Run as the other tests, from spd/core/src/main/assets.
 */
public final class SkeletonCase {
	static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }

	@SuppressWarnings("unchecked")
	public static void main(String[] a) throws Exception {
		BotModelTest.start(HeroClass.WARRIOR, 77);
		Hero h = Dungeon.hero;
		WatchSettings.assist(2); Assist.reset();
		int hpPct = Integer.parseInt(System.getenv().getOrDefault("HPPCT", "92")), depth = Integer.parseInt(System.getenv().getOrDefault("DEPTH", "7"));
		Class<? extends Mob> foe = (Class<? extends Mob>) Class.forName("com.shatteredpixel.shatteredpixeldungeon.actors.mobs." + System.getenv().getOrDefault("FOE", "Skeleton"));
		Dungeon.depth = depth;
		int ht = Integer.parseInt(System.getenv().getOrDefault("HT", "65")); h.lvl = Integer.parseInt(System.getenv().getOrDefault("LVL", "7")); h.STR = 12; h.HT = ht; h.HP = h.HT * hpPct / 100;
		Weapon0.set(h);
		h.belongings.backpack.items.clear();
		for (Class<? extends Item> k : Arrays.asList(PotionOfHealing.class, PotionOfHealing.class, PotionOfHaste.class, PotionOfFrost.class, PotionOfInvisibility.class, ScrollOfTeleportation.class, ScrollOfRage.class,
				Firebloom.Seed.class, Stormvine.Seed.class, Earthroot.Seed.class, Icecap.Seed.class, StoneOfDeepSleep.class)) {
			Item i = k.getDeclaredConstructor().newInstance(); i.identify(); h.belongings.backpack.items.add(i);
		}
		com.shatteredpixel.shatteredpixeldungeon.levels.Level L = Dungeon.level;
		int dist = Integer.parseInt(System.getenv().getOrDefault("DIST", "4")), cell = -1; Dungeon.observe();
		for (int d = dist; d >= 1 && cell < 0; d--) for (int c = 0; c < L.length() && cell < 0; c++) if (L.passable[c] && L.heroFOV[c] && Actor.findChar(c) == null && Dungeon.level.distance(c, h.pos) == d) { cell = c; dist = d; }
		check(cell >= 0, "a cell for the foe");
		java.lang.reflect.Field hm = Assist.class.getDeclaredField("huntMem"); hm.setAccessible(true);
		int n = Integer.parseInt(System.getenv().getOrDefault("FOES", "1"));
		for (int k = 0; k < n; k++) {
			int fc = -1; for (int c = 0; c < L.length() && fc < 0; c++) if (L.passable[c] && L.heroFOV[c] && Actor.findChar(c) == null && Dungeon.level.distance(c, h.pos) == dist) fc = c;
			if (fc < 0) fc = cell;
			Mob m = foe.getDeclaredConstructor().newInstance(); m.pos = fc; m.state = m.HUNTING; GameScene.add(m);
			BotModelTest.see(m, h); Dungeon.observe(); h.checkVisibleMobs();
			((Map<Integer, Boolean>) hm.get(null)).put(m.id(), true);
		}
		for (int k = 0; k < Integer.parseInt(System.getenv().getOrDefault("PLANS", "1")); k++) {
			List<Assist.Target> l = Assist.plan(false, null);
			System.out.println("HP " + h.HP + "/" + h.HT + " " + foe.getSimpleName() + " dist " + dist + " depth " + depth + ": plan A = " + (l.isEmpty() ? "-" : l.get(0)));
			for (int i = 1; i < l.size(); i++) System.out.println("   then " + l.get(i));
			String ex = System.getenv("EXPECT"), got = l.isEmpty() ? "-" : l.get(0).kind == Assist.ENEMY ? "enemy" : l.get(0).item instanceof PotionOfHealing ? "heal" : "other";
			if (ex != null && !ex.equals(got)) { System.out.println("FAIL: expected plan A " + ex + ", got " + got); System.exit(1); }
		}
		System.out.println("fight " + Brain.fight + " easy " + Brain.easy + " foes " + Brain.foes.size() + " base " + Brain.base + " hard " + AsItems.hard + " near " + AsItems.near.size() + " itemsOn " + Assist.itemsOn());
		if (Assist.lastError != null) Assist.lastError.printStackTrace(System.out);
		System.exit(0);
	}

	static final class Weapon0 {
		static void set(Hero h) {
			Sword w = new Sword(); w.level(Integer.parseInt(System.getenv().getOrDefault("WL", "2"))); w.identify(); w.cursed = false; h.belongings.weapon = w;
			MailArmor ar = new MailArmor(); ar.level(Integer.parseInt(System.getenv().getOrDefault("AL", "2"))); ar.identify(); ar.cursed = false; h.belongings.armor = ar;
		}
	}
}
