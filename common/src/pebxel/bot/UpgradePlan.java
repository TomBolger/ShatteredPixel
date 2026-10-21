package pebxel.bot;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClassArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.DamageWand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import java.util.*;

/**
 * Where a Scroll of Upgrade goes (OG upg_plan, re-derived for Shattered), or hold it.
 *
 * The candidates: the worn armour and weapon, the Mage's staff, and a weapon or armour in the pack the hero would wear
 * once upgraded (an upgrade lowers its STR need at +1, +3, +6: Weapon/Armor.STRReq). Each is scored as OG did, not
 * myopically: 0.4 x the gear cost (GearValue) right after this scroll (and the others in hand), + 0.6 x the cost at the
 * region's boss floor with the scrolls still to come there all going the same way (3 a region on its floors 1-4:
 * Dungeon.souNeeded), the Strength potions to come (2 a region: posNeeded) and a hero level a floor. The lower wins.
 *
 * The guide's consensus is kept as priors where the model can't see the reason: the Mage's first three into the staff
 * (its zaps; strategy-reference 2.1, H-cls1 +0.50); the Spirit Bow takes none (SpiritBow.isUpgradable() is false in
 * v4.0.2: the reference's "the bow is an SoU target" is wrong); the class armour and artifacts take none. A known
 * cursed piece worn is freed by the scroll: it's a candidate like the others (its curse counted as shown).
 *
 * Hold: when no candidate helps now by 1% and the boss floor isn't next (the bag keeps it: SoU never burns).
 */
public final class UpgradePlan {
	private UpgradePlan() { }

	public static String last = "";

	/** The scrolls in hand (known), at least 1 (the one being read). */
	static int inHand(Hero h) {
		ScrollOfUpgrade s = h.belongings.getItem(ScrollOfUpgrade.class);
		return s != null && s.isKnown() ? Math.max(1, s.quantity()) : 1;
	}

	/** Floors left in this region before its boss (the current one counted as half: part of it is seen). */
	static double floorsLeft(int d) { int boss = ((d - 1) / 5 + 1) * 5; return d >= boss ? 0 : boss - 1 - d + 0.5; }

	/** The staff's zaps a fight: its wand's mean damage at level l, about 1.2 charges a fight (Wand.Charger: ~25 turns a
	 *  charge with charges missing, fights ~30 turns apart). */
	static double zap(MagesStaff st, int l) {
		Object w = HeroView.field(st, "wand");
		if (!(w instanceof DamageWand)) return 0;
		DamageWand dw = (DamageWand) w;
		return 1.2 * (dw.min(l) + dw.max(l)) / 2.0;
	}

	/** The target for a scroll read now (null: hold). */
	public static Item target(Hero h) {
		int n = inHand(h), d = Dungeon.depth;
		double fl = floorsLeft(d), F = 0.75 * fl, Sg = 0.5 * fl;
		int boss = ((d - 1) / 5 + 1) * 5, dl = Math.max(0, boss - d);
		KindOfWeapon w0 = h.belongings.weapon(); Armor a0 = h.belongings.armor();
		int wl0 = HeroView.visLevel(w0), al0 = HeroView.visLevel(a0); Ench we0 = Ench.of(w0), ae0 = Ench.of(a0);
		MagesStaff st = w0 instanceof MagesStaff ? (MagesStaff) w0 : null;
		double z0 = st != null ? zap(st, wl0) : 0;
		// the Mage: the staff first, to +3 (the guide; H's STAFFUP)
		if (st != null && h.heroClass == HeroClass.MAGE && wl0 < 3) { last = "staff first (+" + wl0 + ")"; return st; }
		double base = GearValue.cost(h, w0, wl0, we0, a0, al0, ae0, 0, 0, d, z0);
		double baseLate = GearValue.cost(h, w0, wl0, we0, a0, al0, ae0, (int) Math.round(Sg), dl, boss, z0);
		List<Item> cands = new ArrayList<>();
		if (a0 != null && !(a0 instanceof ClassArmor)) cands.add(a0);
		if (w0 != null && (w0 instanceof MeleeWeapon)) cands.add(w0);
		for (Item i : h.belongings.backpack) if (GearValue.judged(i) && !(i.cursedKnown && i.cursed) && !(i.levelKnown && i.level() < 0)) cands.add(i);
		Item best = null; double bs = Double.MAX_VALUE, bNow = base; StringBuilder why = new StringBuilder();
		for (Item c : cands) {
			boolean arm = c instanceof Armor;
			int l = HeroView.visLevel(c);
			int Fi = (int) Math.round(F);
			double now, late;
			if (arm) {
				now = GearValue.cost(h, w0, wl0, we0, (Armor) c, l + n, Ench.of(c), 0, 0, d, z0);
				late = GearValue.cost(h, w0, wl0, we0, (Armor) c, l + n + Fi, Ench.of(c), (int) Math.round(Sg), dl, boss, z0);
			} else {
				double z = c instanceof MagesStaff ? zap((MagesStaff) c, l + n) : z0, zl = c instanceof MagesStaff ? zap((MagesStaff) c, l + n + Fi) : z0;
				now = GearValue.cost(h, (KindOfWeapon) c, l + n, Ench.of(c), a0, al0, ae0, 0, 0, d, z);
				late = GearValue.cost(h, (KindOfWeapon) c, l + n + Fi, Ench.of(c), a0, al0, ae0, (int) Math.round(Sg), dl, boss, zl);
			}
			// an enchanted piece upgraded from +4 may lose it (10/20/40/80/100% from +4..+8: Weapon.upgrade): at that level, mixed in
			Ench e = Ench.of(c);
			if (e != Ench.NONE && !e.curse && l + n >= 4) { double pl = Math.min(1, 0.1 * Math.pow(2, l + n - 4)); now = now * (1 - pl) + pl * now / Math.max(0.5, 1 / e.taken / e.dmg); }
			if (!c.isEquipped(h)) { now = Math.min(now, base); }   // (not worn yet: the old piece stays on till this one is better)
			double sc = 0.4 * Math.min(now, base) + 0.6 * Math.min(late, baseLate);
			why.append(String.format(Locale.ROOT, " %s+%d: now %.2f late %.2f score %.2f;", c.getClass().getSimpleName(), l, now, late, sc));
			if (sc < bs) { bs = sc; best = c; bNow = now; }
		}
		last = String.format(Locale.ROOT, "base %.2f late %.2f F %.1f S %.1f |%s", base, baseLate, F, Sg, why);
		if (best == null) return null;
		if (bNow >= base * 0.99 && dl > 1 && Dungeon.depth % 5 != 4) { last = "hold: " + last; return null; }
		return best;
	}

}
