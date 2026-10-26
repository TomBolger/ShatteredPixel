package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClassArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfRemoveCurse;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import pebxel.bot.HeroView;

/**
 * The armour plan (agent HE). Forensics on 288 honest games (HE-f0): of 109 heroes who died in the prison, 66 still
 * wore cloth armour and 70 carried a better-tier armour unworn (39 had found mail, 19 scale, 11 plate); 421 of about
 * 1,050 Scrolls of Upgrade went into cloth. The old swap rule (2 x tier + level, an unknown piece at -1) made cloth+2
 * beat any unknown leather and cloth+3 beat unknown mail, so every early scroll locked the hero into cloth.
 *
 * Here an armour is worth its average block per hit, (min + max) / 2 = (L + tier x (2 + L)) / 2 (Armor.DRMin/DRMax),
 * counted at the level it will reach: its shown level plus the known Scrolls of Upgrade in hand plus FUT more to come
 * (all of them go into the worn armour: strategy-v4 §5, "armour first"). So the higher tier wins as soon as the
 * scrolls ahead would make it better, and the scrolls follow it. STR: the need at that level (upgrades lower it);
 * one point short costs a quarter (evasion / 1.5, walking / 1.2), two or more rules it out (strategy-reference 1.3:
 * "never wear gear more than 1 STR short"). An unknown piece may be cursed (30%): with no known Upgrade or Remove
 * Curse in hand to lift a curse, it counts 15% less. Only what the player sees: the shown level and curse.
 */
final class AsGear {
	private AsGear() { }

	static final boolean ON = Assist.opt("ARMPLAN", true);
	static { pebxel.bot.GearValue.armorElsewhere = ON; }
	static final int FUT = Assist.optInt("ARMFUT", 1);          // (the scrolls still to come counted in the comparison)
	static final int HYST = Assist.optInt("ARMHYST", 50);       // (hundredths of a block point a new armour must win by)
	// (HE-f1: with FUT 1 a cloth+3 still beat an unknown mail at STR 12, and 52 of 77 prison deaths still wore
	// cloth. From LONGD on, the commit looks further: LFUT scrolls and LSTR Strength ahead, the rest of the run's)
	static final int LONGD = Assist.optInt("ARMLONGD", 99), LFUT = Assist.optInt("ARMLFUT", 3), LSTR = Assist.optInt("ARMLSTR", 2);
	static final int ENCMAX = Assist.optInt("ARMENC", 1);      // (STR points short a piece may be worn at now)
	static final boolean HOLD1 = Assist.opt("ARMHOLD1", false); // (hold the scrolls while only cloth is worn, till the boss)

	static final boolean DBG = System.getenv("ARMDBG") != null;
	static double block(int tier, int lvl) { return (lvl + tier * (2 + lvl)) / 2.0; }

	static int sou(Hero h) { ScrollOfUpgrade s = h.belongings.getItem(ScrollOfUpgrade.class); return s != null && s.isKnown() ? s.quantity() : 0; }
	static boolean liftable(Hero h) {
		ScrollOfRemoveCurse rc = h.belongings.getItem(ScrollOfRemoveCurse.class);
		return sou(h) > 0 || (rc != null && rc.isKnown());
	}

	/** What a piece is worth to commit the coming upgrades to (higher: better; below 0: not wearable). */
	static double worth(Hero h, Armor a) {
		if (a == null) return -1;
		if (a instanceof ClassArmor) return 999;
		boolean worn = a == h.belongings.armor();
		if (!worn && a.cursedKnown && a.cursed && !liftable(h)) return -1;
		int lv = HeroView.visLevel(a); if (lv < 0 && !a.levelKnown) lv = 0;
		boolean lng = Dungeon.depth >= LONGD;
		int k = sou(h) + (lng ? LFUT : FUT), at = Math.max(0, lv + k);
		int enc = a.STRReq(at) - h.STR() - (lng ? LSTR : 0);   // (long: the Strength potions ahead too, 2 a region)
		if (enc >= 2) return -1;
		double v = block(a.tier, at);
		if (enc == 1) v *= 0.75;
		if (!worn && !a.cursedKnown && !liftable(h)) v *= 0.85;
		return v;
	}

	/** The armour the coming upgrades should go into: the worn one, or a better one carried. */
	static Armor commit(Hero h) {
		Armor cur = h.belongings.armor(), best = cur; double bv = worth(h, cur) + HYST / 100.0;
		for (Item i : h.belongings.backpack) if (i instanceof Armor) { double v = worth(h, (Armor) i); if (v > bv) { bv = v; best = (Armor) i; } }
		pebxel.bot.GearValue.keepArmor = best;
		return best;
	}

	/** What a piece blocks now, as it would be worn at once: its shown level, the STR it needs now (HE-a1: heavier
	 *  armour worn 2 STR short in the sewers, evasion / 2.25 and walking / 1.44, cost more sewer deaths than the
	 *  prison saved). Below 0: not to be worn yet. */
	static double now(Hero h, Armor a) {
		if (a == null) return 0;
		if (a instanceof ClassArmor) return 999;
		int lv = Math.max(0, HeroView.visLevel(a));
		int enc = a.STRReq(lv) - h.STR();
		if (enc > (a.levelKnown ? ENCMAX : 0)) return -1;   // (an unknown piece only at its shown +0's full STR: RefinementTest, nothing read from the hidden level)
		double v = block(a.tier, lv);
		if (enc == 1) v *= 0.75;
		return v;
	}

	/** An armour to put on now (the commit piece, once it blocks more than the worn one as it is now), or null. */
	static Armor wear(Hero h) {
		if (!ON) return null;
		Armor cur = h.belongings.armor();
		if (cur instanceof ClassArmor || (cur != null && cur.cursed && cur.cursedKnown)) return null;
		Armor c = commit(h);
		if (DBG) { StringBuilder b = new StringBuilder("    ARMPLAN worn " + (cur == null ? "-" : cur.name() + " " + worth(h, cur) + "/" + now(h, cur)) + " ->"); for (Item i : h.belongings.backpack) if (i instanceof Armor) b.append(' ').append(i.name()).append(' ').append(worth(h, (Armor) i)).append('/').append(now(h, (Armor) i)); System.out.println(b); }
		if (c == null || c == cur) return null;
		double nc = now(h, c);
		return nc >= 0 && nc >= now(h, cur) ? c : null;
	}

	/** The Scroll of Upgrade's target among the armour: the commit piece, worn or carried (carried: each scroll
	 *  raises its block and lowers its STR need till it beats the worn one), or null to hold. */
	static Item upgradeOn(Hero h) {
		Armor c = commit(h);
		if (c == null) return null;
		if (HOLD1 && c.tier == 1 && !(c instanceof ClassArmor) && !bossNext()) return null;
		return c;
	}
	static boolean bossNext() { int d = Dungeon.depth; return d % 5 == 4 || d % 5 == 0; }

	static String why(Hero h, Armor a) {
		return "armour plan: " + a.name() + " (blocks about " + Math.round(block(a.tier, Math.max(0, HeroView.visLevel(a)) + sou(h) + FUT)) + " a hit with the scrolls ahead)";
	}
}
