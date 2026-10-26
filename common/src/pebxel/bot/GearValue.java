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
		me.hp = me.ht; me.heals = 0;
		zap += prep(me, h, w, wl, a, strPlus);
		double c = 0, W = 0;
		for (int dd = d; dd < d + 3; dd++) {
			String[] ks = MobTable.kinds(dd);
			for (String k : ks) { Fighter f = MobTable.fighter(k); if (f == null) continue; if (zap > 0) f.hp = Math.max(1, (int) Math.round(f.hp - zap)); c += FightModel.duelCost(me, f); W++; }
		}
		c = W > 0 ? c / W : 0;
		Fighter b = MobTable.fighter(MobTable.boss(d));
		if (b != null && zap > 0) b.hp = Math.max(1, (int) Math.round(b.hp - 3 * zap));
		if (b != null) c += Tune.BOSSW * FightModel.duelCost(me, b);   // (the boss: one fight in a region's ~30, but the one that ends runs)
		if (a != null) { int enc = a.STRReq(al) - (h.STR() + strPlus); if (enc > 0) c *= Math.pow(Tune.SLOW, enc); }
		return c;
	}

	/** What a fight's sums need beyond HeroView: the Warrior's seal shield, surprise blows (a dagger's sneak roll), the
	 *  spirit bow's shots while foes come (returned: damage before the blows). */
	static double prep(Fighter me, Hero h, KindOfWeapon w, int wl, Armor a, int strPlus) {
		double zap = 0;
		if (h.heroClass == HeroClass.WARRIOR && a != null) me.shield = 3 + 2 * a.tier;   // (BrokenSeal.maxShield: the seal goes with the armour)
		else me.shield = 0;
		me.sure = h.heroClass == HeroClass.ROGUE ? Tune.SURER : Tune.SURE;
		if (w instanceof com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Dagger || w instanceof com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Dirk
				|| w instanceof com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.AssassinsBlade) {
			int mn = w.min(wl), mx = w.max(wl), ex = h.STR() + strPlus - ((Weapon) w).STRReq(wl);
			Dist sd = Dist.normal(mn + Math.round((mx - mn) * 0.75f), mx).scale(HeroView.augDmg((Weapon) w));
			me.sureDmg = ex > 0 ? sd.plus(Dist.normal(0, ex)) : sd;
		}
		if (me.rangedDmg != null && me.rangedShots >= 99) zap += Tune.BOWSHOTS * 0.7 * me.rangedDmg.mean();   // (the spirit bow: shots while they come)
		me.rangedShots = 0;
		return zap;
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

	/** The worth of wearing i (a weapon or armour) in its slot, the other slot as worn (lower: better); for a piece whose
	 *  level isn't known the risk-weighted average over what it may be, worn or not (MT: the worn piece had been costed
	 *  at its shown +0 and the pack's at the odds of +1/+2 as well, so two unknown weapons each looked better than the
	 *  other once worn: a swap every 2 turns for hundreds of decisions). base: the worn piece's own cost (a lifted
	 *  curse's ceiling). */
	public static double costWith(Hero h, Item i, double base) {
		boolean wpn = i instanceof KindOfWeapon;
		KindOfWeapon w0 = h.belongings.weapon(); Armor a0 = h.belongings.armor();
		// (MT: worked out once per piece and situation: the plan asks every turn, and the sums over curses and floors were most of a plan's time)
		String key = Dungeon.depth + ":" + h.STR() + ":" + h.lvl + ":" + h.HT + ":" + h.heroClass + ":" + liftable(h) + ":" + (base == Double.MAX_VALUE ? "w" : (int) Math.round(base * 1000)) + "|" + desc(i) + "|" + desc(wpn ? a0 : w0) + "|" + (i == w0 || i == a0);
		Double c = memo.get(key);
		if (c != null) return c;
		if (memo.size() > 4000) memo.clear();
		double v = costWith0(h, i, base, wpn, w0, a0);
		memo.put(key, v);
		return v;
	}
	static final Map<String, Double> memo = new HashMap<>();
	static String desc(Item i) {
		if (i == null) return "-";
		String a = i instanceof Weapon ? String.valueOf(((Weapon) i).augment) : i instanceof Armor ? String.valueOf(((Armor) i).augment) : "";
		return i.getClass().getSimpleName() + HeroView.visLevel(i) + (i.levelKnown ? "k" : "u") + (i.cursedKnown ? (i.cursed ? "C" : "c") : "?") + Ench.of(i).name + a;
	}

	static double costWith0(Hero h, Item i, double base, boolean wpn, KindOfWeapon w0, Armor a0) {
		Ench shown = Ench.of(i);
		if (i.levelKnown) return one(h, i, wpn, w0, a0, HeroView.visLevel(i), shown);
		boolean worn = i == w0 || i == a0;
		return expect(h, i, wpn, w0, a0, shown, base, !worn && !(i.cursedKnown && !i.cursed) && shown == Ench.NONE);
	}

	/** The cost of the slot's worn piece (null: an empty slot), by the same sums as a pack piece's. */
	public static double slotCost(Hero h, boolean wpn) {
		KindOfWeapon w0 = h.belongings.weapon(); Armor a0 = h.belongings.armor();
		Item cur = wpn ? w0 : a0;
		if (cur == null) return wpn ? cost(h, null, 0, Ench.NONE, a0, HeroView.visLevel(a0), Ench.of(a0), 0, 0, Dungeon.depth)
				: cost(h, w0, HeroView.visLevel(w0), Ench.of(w0), null, 0, Ench.NONE, 0, 0, Dungeon.depth);
		return costWith(h, cur, Double.MAX_VALUE);
	}

	/** The odds: levels 0/1/2, and (when it can be) a curse from its kind's list. */
	static double expect(Hero h, Item i, boolean wpn, KindOfWeapon w0, Armor a0, Ench shown, double base, boolean mayCurse) {
		double pc = !mayCurse ? 0 : i.cursedKnown && i.cursed ? 1 : Tune.CURSE / (wpn ? 0.9 : 0.85);
		if (shown.curse) pc = 1;
		String[] curses = wpn ? Ench.WEAPON_CURSES : Ench.ARMOR_CURSES;
		boolean lift = liftable(h);
		double s = 0, ws = 0;
		int up = HeroView.upgraded(i);   // (MT: the scrolls the player read onto it count on top of what it may have been)
		for (int k = 0; k < LV.length; k++) {
			if (pc < 1) {   // clean
				double c = one(h, i, wpn, w0, a0, LV[k] + up, shown.curse ? Ench.NONE : shown);
				s += LP[k] * (1 - pc) * Math.pow(c, Tune.RISK); ws += LP[k] * (1 - pc);
			}
			if (pc > 0) for (String cn : curses) {
				Ench ce = shown.curse ? shown : Ench.byName(cn);
				double c = one(h, i, wpn, w0, a0, LV[k] + up, ce);
				if (lift) c = Math.min(c, base * 1.02);   // (a scroll lifts it: some cost, a scroll's worth)
				double p = LP[k] * pc / curses.length;
				s += p * Math.pow(c, Tune.RISK); ws += p;
				if (shown.curse) break;
			}
		}
		return ws > 0 ? Math.pow(s / ws, 1 / Tune.RISK) : base;
	}

	static double one(Hero h, Item i, boolean wpn, KindOfWeapon w0, Armor a0, int l, Ench e) {
		return wpn ? cost(h, (KindOfWeapon) i, l, e, a0, HeroView.visLevel(a0), Ench.of(a0), 0, 0, Dungeon.depth)
				: cost(h, w0, HeroView.visLevel(w0), Ench.of(w0), (Armor) i, l, e, 0, 0, Dungeon.depth);
	}

	/** Pieces the models judge: melee weapons and armour, not the class armour, not the Mage's staff. */
	public static boolean judged(Item i) {
		return (i instanceof MeleeWeapon && !(i instanceof MagesStaff)) || (i instanceof Armor && !(i instanceof ClassArmor));
	}

	/** Pieces taken off for another, and when: not put back on for a while unless something about them came to light
	 *  (a known level or curse), so that two pieces near alike can't take turns. */
	static final List<Item> offI = new ArrayList<>(); static final List<float[]> offT = new ArrayList<>();   // (a list, not a map: hashing an object numbers it, and the game's own hash numbers would shift)
	public static float REWEAR = 300;
	static int offAt(Item i) { for (int k = 0; k < offI.size(); k++) if (offI.get(k) == i) return k; return -1; }

	/** What to put on now (null: nothing): the pack's piece whose cost beats the worn by at least need (a share, e.g.
	 *  0.05: not for a rounding's worth), the most. Worn pieces known cursed stay (they can't come off). */
	/** Set by the assistant when its own armour plan (AsGear) chooses the armour. */
	public static boolean armorElsewhere;
	/** MD: AsWeapon's weapon plan decides the melee weapon worn. */
	public static boolean weaponElsewhere;
	/** That plan's chosen armour, never spare (sold or dropped). */
	public static Item keepArmor, keepWeapon;   // (keepWeapon: MD's weapon plan's pick)

	public static Item bestEquip(Hero h, double need) {
		Item best = null; double bestGain = 0; Item bestCur = null;
		float now = com.shatteredpixel.shatteredpixeldungeon.actors.Actor.now();
		double[] base = { slotCost(h, true), slotCost(h, false) };
		for (Item i : h.belongings.backpack) {
			if (!judged(i)) continue;
			boolean wpn = !(i instanceof Armor);
			if (!wpn && armorElsewhere) continue;   // (HE: AsGear's armour plan decides the armour)
			if (wpn && weaponElsewhere) continue;   // (MD: AsWeapon's weapon plan decides the weapon)
			EquipableItem cur = wpn ? h.belongings.weapon() : h.belongings.armor();
			if (cur != null && cur.cursed && cur.cursedKnown) continue;
			if (cur instanceof MagesStaff || cur instanceof ClassArmor) continue;
			if (i.cursedKnown && i.cursed) continue;
			int o = offAt(i);
			if (o >= 0) { float[] t = offT.get(o); if (now - t[0] < REWEAR && now >= t[0] && t[1] == (i.levelKnown && i.cursedKnown ? 1 : 0)) continue; }
			double b = base[wpn ? 0 : 1], c = costWith(h, i, b);
			if (c < b * (1 - need) && (b - c) / b > bestGain) { bestGain = (b - c) / b; best = i; bestCur = cur; }
		}
		if (bestCur != null) {
			int o = offAt(bestCur); float[] t = { now, bestCur.levelKnown && bestCur.cursedKnown ? 1 : 0 };
			if (o >= 0) offT.set(o, t); else { offI.add(bestCur); offT.add(t); if (offI.size() > 16) { offI.remove(0); offT.remove(0); } }
		}
		return best;
	}

	/** The pack's weapons and armour no use to the hero (worth no more than what is worn, now or with the next region's
	 *  STR, and not the twin kept for anything): sell or drop these first. Best-worth first is the reverse order. */
	public static List<Item> spare(Hero h) {
		double[] bs = { slotCost(h, true), slotCost(h, false) };
		List<Item> out = new ArrayList<>();
		for (Item i : h.belongings.backpack) {
			if (!judged(i) || i.isEquipped(h) || (i == keepArmor && keepArmor != null) || (i == keepWeapon && keepWeapon != null)) continue;
			double base = bs[i instanceof Armor ? 1 : 0], c = costWith(h, i, base);
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
		double base = slotCost(h, !(i instanceof Armor));
		return base - costWith(h, i, base);
	}

	static int tier(Item i) { return i instanceof Armor ? ((Armor) i).tier : i instanceof MeleeWeapon ? ((MeleeWeapon) i).tier : 0; }
	static boolean weaponLike(Item i) { return i instanceof Weapon; }
}
