package pebxel.bot;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClassArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfRemoveCurse;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import java.util.*;

/**
 * Gear worth (OG gear_cost / ev_cost / unk_cost, re-derived for Shattered): a weapon-and-armour pair is worth the health
 * its fights are expected to cost (FightModel.duelCost) over the next three floors' spawn mix (MobTable) and half of the
 * region's boss, the hero's STR and level as now (or projected), a heavy armour's slower walk on top (Armor.speedFactor:
 * 1.2 a missing STR point: more turns, more food). Lower is better.
 *
 * An unidentified piece is worth the odds of what it may be (Weapon.random / Armor.random: +0 75%, +1 20%, +2 5%; 30%
 * cursed with a curse of its kind's list; 10% (weapon) / 15% (armour) enchanted, and an enchantment shows, so a piece
 * that shows none is cursed 1 in 3; Known-uncursed narrows it). A good outcome that disappoints can be taken off; a
 * cursed one is stuck till a known Remove Curse or an Upgrade lifts it. The outcomes are averaged as a root mean square
 * (OG's risk exponent 2: the bad tail weighs more). Thomas's rule: only the shown level and shown enchantment count.
 */
public final class GearValue {
	private GearValue() { }

	/** The cost of fighting with w and a, levels and enchantments as given; strPlus / lvlPlus: projected gains; d: the
	 *  floor to start the mix at. */
	public static double cost(Hero h, KindOfWeapon w, int wl, Ench we, Armor a, int al, Ench ag, int strPlus, int lvlPlus, int d) {
		return cost(h, w, wl, we, a, al, ag, strPlus, lvlPlus, d, 0);
	}

	/** zap: damage dealt before each fight's blows (a staff's charges: the Mage), taken off the foe's health. */
	public static double cost(Hero h, KindOfWeapon w, int wl, Ench we, Armor a, int al, Ench ag, int strPlus, int lvlPlus, int d, double zap) {
		Fighter me = HeroView.of(h, w, a, h.STR() + strPlus, wl, al, lvlPlus, we, ag);
		me.hp = me.ht; me.heals = 0; me.rangedShots = 0;
		if (h.heroClass == HeroClass.WARRIOR && a != null) me.shield = 3 + 2 * a.tier;   // (BrokenSeal.maxShield: the seal goes with the armour)
		else me.shield = 0;
		double c = 0, W = 0;
		for (int dd = d; dd < d + 3; dd++) {
			String[] ks = MobTable.kinds(dd);
			for (String k : ks) { Fighter f = MobTable.fighter(k); if (f == null) continue; if (zap > 0) f.hp = Math.max(1, (int) Math.round(f.hp - zap)); c += FightModel.duelCost(me, f); W++; }
		}
		c = W > 0 ? c / W : 0;
		Fighter b = MobTable.fighter(MobTable.boss(d));
		if (b != null && zap > 0) b.hp = Math.max(1, (int) Math.round(b.hp - 3 * zap));
		if (b != null) c += 0.1 * FightModel.duelCost(me, b);   // (the boss: one fight in a region's ~30, but the one that ends runs)
		if (a != null) { int enc = a.STRReq(al) - (h.STR() + strPlus); if (enc > 0) c *= Math.pow(1.2, enc); }
		return c;
	}

	/** The cost of the pair as the player sees them now. */
	public static double cost(Hero h, KindOfWeapon w, Armor a) {
		return cost(h, w, HeroView.visLevel(w), Ench.of(w), a, HeroView.visLevel(a), Ench.of(a), 0, 0, Dungeon.depth);
	}

	public static double now(Hero h) { return cost(h, h.belongings.weapon(), h.belongings.armor()); }

	static final int[] LV = { 0, 1, 2 }; static final double[] LP = { 0.75, 0.20, 0.05 };

	/** Can a cursed piece worn now be taken off soon: a known Remove Curse in the pack, or a known Upgrade (it lifts the
	 *  curse) and the piece is worth upgrading. */
	static boolean liftable(Hero h) {
		ScrollOfRemoveCurse rc = h.belongings.getItem(ScrollOfRemoveCurse.class);
		ScrollOfUpgrade up = h.belongings.getItem(ScrollOfUpgrade.class);
		return (rc != null && rc.isKnown()) || (up != null && up.isKnown());
	}

	/**
	 * The worth of wearing i (a weapon or armour) in place of what is worn, as the cost then (lower: better); for an
	 * unknown piece the risk-weighted average over what it may be. base: the cost as now.
	 */
	public static double costWith(Hero h, Item i, double base) {
		boolean wpn = i instanceof KindOfWeapon;
		KindOfWeapon w0 = h.belongings.weapon(); Armor a0 = h.belongings.armor();
		Ench shown = Ench.of(i);
		if (i.levelKnown) {
			int l = HeroView.visLevel(i);
			return wpn ? cost(h, (KindOfWeapon) i, l, shown, a0, HeroView.visLevel(a0), Ench.of(a0), 0, 0, Dungeon.depth)
					: cost(h, w0, HeroView.visLevel(w0), Ench.of(w0), (Armor) i, l, shown, 0, 0, Dungeon.depth);
		}
		return expect(h, i, wpn, w0, a0, shown, base, !(i.cursedKnown && !i.cursed) && shown == Ench.NONE);
	}

	/** The odds: levels 0/1/2, and (when it can be) a curse from its kind's list. */
	static double expect(Hero h, Item i, boolean wpn, KindOfWeapon w0, Armor a0, Ench shown, double base, boolean mayCurse) {
		double pc = !mayCurse ? 0 : i.cursedKnown && i.cursed ? 1 : wpn ? 0.3 / 0.9 : 0.3 / 0.85;
		if (shown.curse) pc = 1;
		String[] curses = wpn ? Ench.WEAPON_CURSES : Ench.ARMOR_CURSES;
		boolean lift = liftable(h);
		double s = 0, ws = 0;
		for (int k = 0; k < LV.length; k++) {
			// clean
			if (pc < 1) {
				double c = one(h, i, wpn, w0, a0, LV[k], shown.curse ? Ench.NONE : shown);
				c = Math.min(c, base);   // (not stuck: back to the old piece if it disappoints)
				s += LP[k] * (1 - pc) * c * c; ws += LP[k] * (1 - pc);
			}
			if (pc > 0) for (String cn : curses) {
				Ench ce = shown.curse ? shown : Ench.byName(cn);
				double c = one(h, i, wpn, w0, a0, LV[k], ce);
				if (lift) c = Math.min(c, base * 1.02);   // (a scroll lifts it: some cost, a scroll's worth)
				double p = LP[k] * pc / curses.length;
				s += p * c * c; ws += p;
				if (shown.curse) break;
			}
		}
		return ws > 0 ? Math.sqrt(s / ws) : base;
	}

	static double one(Hero h, Item i, boolean wpn, KindOfWeapon w0, Armor a0, int l, Ench e) {
		return wpn ? cost(h, (KindOfWeapon) i, l, e, a0, HeroView.visLevel(a0), Ench.of(a0), 0, 0, Dungeon.depth)
				: cost(h, w0, HeroView.visLevel(w0), Ench.of(w0), (Armor) i, l, e, 0, 0, Dungeon.depth);
	}

	/** Pieces the models judge: melee weapons and armour, not the class armour, not the Mage's staff. */
	public static boolean judged(Item i) {
		return (i instanceof MeleeWeapon && !(i instanceof MagesStaff)) || (i instanceof Armor && !(i instanceof ClassArmor));
	}

	/** What to put on now (null: nothing): the pack's piece whose cost beats the worn by at least need (a share, e.g.
	 *  0.05: not for a rounding's worth). Worn pieces known cursed stay (they can't come off). */
	public static Item bestEquip(Hero h, double need) {
		double base = now(h); Item best = null; double bc = base * (1 - need);
		for (Item i : h.belongings.backpack) {
			if (!judged(i)) continue;
			EquipableItem cur = i instanceof Armor ? h.belongings.armor() : h.belongings.weapon();
			if (cur != null && cur.cursed && cur.cursedKnown) continue;
			if (cur instanceof MagesStaff || cur instanceof ClassArmor) continue;
			if (i.cursedKnown && i.cursed) continue;
			double c = costWith(h, i, base);
			if (c < bc) { bc = c; best = i; }
		}
		return best;
	}

	/** The pack's weapons and armour no use to the hero (worth no more than what is worn, now or with the next region's
	 *  STR, and not the twin kept for anything): sell or drop these first. Best-worth first is the reverse order. */
	public static List<Item> spare(Hero h) {
		double base = now(h);
		List<Item> out = new ArrayList<>();
		for (Item i : h.belongings.backpack) {
			if (!judged(i) || i.isEquipped(h)) continue;
			double c = costWith(h, i, base);
			if (c >= base * 0.98 && !futureUse(h, i, base)) out.add(i);
		}
		return out;
	}

	/** With 2 more STR (the region's Strength potions) and its shown level + 2 (upgrades), would i beat the worn piece? */
	static boolean futureUse(Hero h, Item i, double base) {
		boolean wpn = i instanceof KindOfWeapon;
		KindOfWeapon w0 = h.belongings.weapon(); Armor a0 = h.belongings.armor();
		int l = HeroView.visLevel(i) + 2;
		double c = wpn ? cost(h, (KindOfWeapon) i, l, Ench.of(i), a0, HeroView.visLevel(a0), Ench.of(a0), 2, 3, Dungeon.depth + 3)
				: cost(h, w0, HeroView.visLevel(w0), Ench.of(w0), (Armor) i, l, Ench.of(i), 2, 3, Dungeon.depth + 3);
		double b = cost(h, w0, HeroView.visLevel(w0) + (wpn ? 2 : 0), Ench.of(w0), a0, HeroView.visLevel(a0) + (wpn ? 0 : 2), Ench.of(a0), 2, 3, Dungeon.depth + 3);
		return c < b * 0.95;
	}

	/** A piece's worth in health a fight (positive: better than the worn one), for ranking. */
	public static double worth(Hero h, Item i) {
		if (!judged(i)) return 0;
		double base = now(h);
		return base - costWith(h, i, base);
	}

	static int tier(Item i) { return i instanceof Armor ? ((Armor) i).tier : i instanceof MeleeWeapon ? ((MeleeWeapon) i).tier : 0; }
	static boolean weaponLike(Item i) { return i instanceof Weapon; }
}
