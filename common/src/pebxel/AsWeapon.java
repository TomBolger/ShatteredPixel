package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClassArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import pebxel.bot.HeroView;
import static pebxel.Assist.*;

/**
 * Where a Scroll of Upgrade goes (agent MD): the armour to a floor's mark, then the weapon kept close behind it.
 *
 * Forensics (MD-gear1, full mild games): every scroll went into the armour, so at depth 19 a Warrior wore mail +14 and
 * swung a Dirk +1. Fights in the Caves and the City (golems' and monks' armour, 120-HP golems) lasted hundreds of turns,
 * the health lost there was rested back at 1 HP every 10 turns, the food ran out and starving took the rest. The head
 * start's heroes, who win from depth 21, carry a weapon upgraded as much as the armour. So: the armour first, to
 * ARMBASE + depth / ARMDIV (+4 by the Caves, about +9 by Yog: strategy-v4 §5), then the weapon until it is at most
 * WPNGAP below the armour, then the armour again: each scroll to the one further behind. A weapon blow's every point
 * shortens every fight; the armour's block meets every hit. Why, for the assistant: the reason string.
 */
final class AsWeapon {
	private AsWeapon() {}

	static final boolean ON = opt("MDUP", true);
	static final int ARMBASE = optInt("MDARMBASE", 2), ARMDIV = optInt("MDARMDIV", 3), WPNGAP = optInt("MDWPNGAP", 2), STAFF1 = optInt("MDSTAFF1", 3), WPNTIER = optInt("MDWPNTIER", 3), LATE = optInt("MDLATE", 16);
	static String why = "upgrade";
	static final boolean DBG = System.getenv("MDDBG") != null;

	/** The piece the scroll goes on, or the old choice (dflt) when this plan has no say. */
	static Item target(Hero h, Item dflt) {
		if (!ON) return dflt;
		Armor a = h.belongings.armor(); KindOfWeapon w = h.belongings.weapon();
		if (pebxel.bot.ScrollPlan.ON) {   // (UP: the expected-value planner decides; the Mage's staff first to +3 stays the guide's)
			if (w instanceof MagesStaff && w.level() < STAFF1 && dflt == w) return dflt;
			Item t = pebxel.bot.ScrollPlan.target(h); why = pebxel.bot.ScrollPlan.why; return t;
		}
		boolean aOk = a != null && !(a instanceof ClassArmor) && a.isUpgradable() && !(a.cursed && a.cursedKnown) && a.levelKnown;
		if (dflt == null || !(dflt == a || dflt == w)) return dflt;   // (a hold, or a piece in the pack: the plan's own call)
		if (w instanceof MagesStaff && w.level() < STAFF1) return dflt;   // (the Mage: the staff first, to +3)
		Item c = w instanceof MagesStaff ? w : commit(h);   // (the weapon the coming scrolls are for: worn, or a better one carried)
		boolean wOk = c instanceof MeleeWeapon && c.isUpgradable() && !(c.cursed && c.cursedKnown) && (c.levelKnown || c != w);
		if (DBG) System.out.println("    MDUP d" + Dungeon.depth + " a " + (a == null ? "-" : a.getClass().getSimpleName() + a.level() + " ok " + aOk) + " w " + (c == null ? "-" : c.getClass().getSimpleName() + HeroView.visLevel(c) + " ok " + wOk));
		if (!aOk || !wOk) return dflt;
		int al = a.level(), wl = Math.max(0, HeroView.visLevel(c)), mark = ARMBASE + Dungeon.depth / Math.max(1, ARMDIV);
		// a low-tier weapon takes no scrolls (MD-he1: with the split from depth 1, 57 of 161 heroes at 16 swung a tier-1
		// weapon at +5, kept for its upgrades over the tier-3 and -4 ones found): a scroll goes into a tier WPNTIER weapon
		// or better (from LATE on, tier 2), upgrades on a higher tier counting for more (max +tier+1 a level)
		if (!(c instanceof MagesStaff) && ((MeleeWeapon) c).tier < (Dungeon.depth >= LATE ? 2 : WPNTIER)) return dflt;
		if (al < mark) { why = "upgrade: the armour, to +" + mark + " by this depth (its block meets every hit)"; return a; }
		if (wl + WPNGAP < al) { why = "upgrade: the weapon, +" + wl + " behind the armour's +" + al + " (shorter fights: less taken, less rested)"; return c; }
		why = "upgrade: the armour (the weapon is close behind)"; return a;
	}

	// ---- the weapon plan: which weapon the scrolls are for, and when it goes on ----
	// MD-he1: at depth 16, 103 of 125 heroes (not Mages) carried a higher-tier weapon than the one they swung: a +6 tier-2
	// beat any unknown tier-4 as it stood, and the one point of STR it lacked. As HE's armour plan does for armour: each
	// weapon counted at the level it will reach (its shown level plus FUT scrolls), its need at that level (upgrades lower
	// it), and worn once it hits as hard as the worn one does now.
	static final boolean PLAN = opt("MDWPLAN", pebxel.bot.ScrollPlan.ON && pebxel.bot.ScrollPlan.WPLAN);   // (UP: on with the planner's weapon commit (off by default: UP-a1, its unknown weapons only worn with a curse liftable kept 84% of heroes in a tier-1 weapon at depth 5))   // (off: MD-fin1, 288 full games each, 15.79 on against 16.22 off; MD-lw1 15.77 / 16.19. Kept for tuning)
	static { if (PLAN && ON) pebxel.bot.GearValue.weaponElsewhere = true; }
	static final int FUT = optInt("MDWFUT", 2), HYST = optInt("MDWHYST", 10), DRK = optInt("MDWDR", 50);   // (scrolls ahead counted; percent a new one must win by)

	/** A blow's worth: mean damage (and the STR to spare) times accuracy over delay, the STR it lacks costing both. */
	static double worth(Hero h, MeleeWeapon m, int lvl) {
		int enc = m.STRReq(lvl) - h.STR();
		if (enc >= 2) return -1;
		double dmg = (m.min(lvl) + m.max(lvl)) / 2.0 + (enc < 0 ? -enc / 2.0 : 0);
		// less the foes' armour: about depth / 2 a blow on average (prison skeletons 0-5, golems 0-12, demons more):
		// fast weak blows lose most to it (MD-bf1: a Huntress kept her Gloves, two blows a turn for nothing, over a
		// known Sickle +2, and fought two foes for 2,000 decisions)
		dmg = Math.max(0.5, dmg - Dungeon.depth * DRK / 100.0);
		double v = dmg * m.ACC / m.DLY;
		if (enc == 1) v /= 1.5 * 1.2;
		return v;
	}
	static boolean liftable(Hero h) {
		com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfRemoveCurse rc = h.belongings.getItem(com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfRemoveCurse.class);
		com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade up = h.belongings.getItem(com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade.class);
		return (rc != null && rc.isKnown()) || (up != null && up.isKnown());
	}
	static boolean usable(Item i) { return i instanceof MeleeWeapon && !(i instanceof MagesStaff) && !(i.cursed && i.cursedKnown) && !(i.levelKnown && i.level() < 0); }
	/** What a weapon will be worth with the scrolls ahead. */
	static double ahead(Hero h, MeleeWeapon m) {
		int lv = Math.max(0, HeroView.visLevel(m));
		double v = worth(h, m, lv + FUT);
		if (v > 0 && m != h.belongings.weapon() && !m.cursedKnown && !liftable(h)) v *= 0.85;   // (an unknown one may be cursed)
		return v;
	}
	/** The weapon the coming scrolls go into: the worn one, or a better one carried. */
	static Item commit(Hero h) {
		KindOfWeapon w = h.belongings.weapon();
		if (pebxel.bot.ScrollPlan.ON) { Item c = pebxel.bot.ScrollPlan.commitWeapon(h); pebxel.bot.GearValue.keepWeapon = c; return c; }
		if (!PLAN || !(w instanceof MeleeWeapon) || w instanceof MagesStaff || (w.cursed && w.cursedKnown)) return w;
		Item best = w; double bv = ahead(h, (MeleeWeapon) w) * (1 + HYST / 100.0);
		for (Item i : h.belongings.backpack) if (usable(i)) { double v = ahead(h, (MeleeWeapon) i); if (v > bv) { bv = v; best = i; } }
		pebxel.bot.GearValue.keepWeapon = best;
		return best;
	}
	/** A weapon to put on now: the commit piece, once it hits as hard as the worn one now (or the worn one is no use). */
	static Item wear(Hero h) {
		if (!PLAN || !ON) return null;
		KindOfWeapon w = h.belongings.weapon();
		if (w instanceof MagesStaff || (w != null && w.cursed && w.cursedKnown)) return null;
		if (w != null && !(w instanceof MeleeWeapon)) return null;
		Item c = commit(h);
		if (pebxel.bot.ScrollPlan.ON) {   // (UP: the committed weapon, once it beats the worn one on this floor; an unknown one only with a curse liftable)
			if (c == null || c == w || !(c instanceof MeleeWeapon)) return null;
			if (!(c.cursedKnown && !c.cursed) && !liftable(h)) return null;
			return pebxel.bot.ScrollPlan.better(h, c) ? c : null;
		}
		if (w == null) { Item b = null; double bv = 0; for (Item i : h.belongings.backpack) if (usable(i)) { double v = worth(h, (MeleeWeapon) i, Math.max(0, HeroView.visLevel(i))); if (v > bv) { bv = v; b = i; } } return b; }
		if (c == null || c == w) return null;
		double nc = worth(h, (MeleeWeapon) c, Math.max(0, HeroView.visLevel(c))), nw = worth(h, (MeleeWeapon) w, Math.max(0, HeroView.visLevel(w)));
		return nc > 0 && nc >= nw ? c : null;
	}
	static String wearWhy(Hero h, Item c) {
		if (pebxel.bot.ScrollPlan.ON) return "weapon plan: " + c.name() + " (the one the upgrades go into; better on this floor)";
		return String.format(java.util.Locale.ROOT, "weapon plan: %s (about %.0f a blow with the scrolls ahead)", c.name(), worth(h, (MeleeWeapon) c, Math.max(0, HeroView.visLevel(c)) + FUT));
	}
}
