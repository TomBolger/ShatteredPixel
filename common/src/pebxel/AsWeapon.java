package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClassArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
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
		boolean aOk = a != null && !(a instanceof ClassArmor) && a.isUpgradable() && !(a.cursed && a.cursedKnown) && a.levelKnown;
		boolean wOk = w instanceof MeleeWeapon && w.isUpgradable() && !(w.cursed && w.cursedKnown) && w.levelKnown;
		if (dflt == null || !(dflt == a || dflt == w)) return dflt;   // (a hold, or a piece in the pack: the plan's own call)
		if (w instanceof MagesStaff && w.level() < STAFF1) return dflt;   // (the Mage: the staff first, to +3)
		if (DBG) System.out.println("    MDUP d" + Dungeon.depth + " a " + (a == null ? "-" : a.getClass().getSimpleName() + a.level() + " ok " + aOk) + " w " + (w == null ? "-" : w.getClass().getSimpleName() + w.level() + " ok " + wOk));
		if (!aOk || !wOk) return dflt;
		int al = a.level(), wl = w.level(), mark = ARMBASE + Dungeon.depth / Math.max(1, ARMDIV);
		// a low-tier weapon takes no scrolls (MD-he1: with the split from depth 1, 57 of 161 heroes at 16 swung a tier-1
		// weapon at +5, kept for its upgrades over the tier-3 and -4 ones found): a scroll goes into a tier WPNTIER weapon
		// or better (from LATE on, tier 2), upgrades on a higher tier counting for more (max +tier+1 a level)
		if (!(w instanceof MagesStaff) && ((MeleeWeapon) w).tier < (Dungeon.depth >= LATE ? 2 : WPNTIER)) return dflt;
		if (al < mark) { why = "upgrade: the armour, to +" + mark + " by this depth (its block meets every hit)"; return a; }
		if (wl + WPNGAP < al) { why = "upgrade: the weapon, +" + wl + " behind the armour's +" + al + " (shorter fights: less taken, less rested)"; return w; }
		why = "upgrade: the armour (the weapon is close behind)"; return a;
	}
}
