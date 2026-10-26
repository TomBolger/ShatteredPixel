package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem;
import java.util.*;

/**
 * Hero power forensics (agent HE; only prints): the worn armour and weapon on each floor's arrival, Scrolls of Upgrade
 * and Potions of Strength picked up against used and where each upgrade went, the best armour ever carried, the
 * armour and weapons left in the pack at the end. One "GEAR" line a game (server-side truth: a report, not a decision).
 */
final class GearStats {
	static int lastDepth, souQ, posQ, souFound, souUsed, posFound, posUsed, bestTier, bestTierAt;
	static final IdentityHashMap<Item, Integer> lv = new IdentityHashMap<>();
	static final StringBuilder at = new StringBuilder(), went = new StringBuilder();

	static void newGame() { lastDepth = 0; souQ = posQ = souFound = souUsed = posFound = posUsed = bestTier = bestTierAt = 0; lv.clear(); at.setLength(0); went.setLength(0); }

	static String g(Item i) {
		if (i == null) return "-";
		String n = i.getClass().getSimpleName().replace("Armor", "A");
		if (i instanceof Armor) n = "T" + ((Armor) i).tier + n;
		if (i instanceof MeleeWeapon) n = "T" + ((MeleeWeapon) i).tier + n;
		return n + "+" + i.level() + (i.levelKnown ? "" : "?") + (i.cursed ? "c" : "");
	}

	static void decision() {
		Hero h = Dungeon.hero; if (h == null) return;
		int sq = 0, pq = 0;
		for (Item i : h.belongings) {
			if (i instanceof ScrollOfUpgrade) sq += i.quantity();
			if (i instanceof PotionOfStrength) pq += i.quantity();
			if (i instanceof Armor && ((Armor) i).tier > bestTier) { bestTier = ((Armor) i).tier; bestTierAt = Dungeon.depth; }
		}
		if (sq > souQ) souFound += sq - souQ;
		if (sq < souQ) {
			souUsed += souQ - sq;
			for (Item i : h.belongings) if (i instanceof EquipableItem || i instanceof Wand) {
				Integer o = lv.get(i);
				if (o != null && i.level() > o) went.append(Dungeon.depth).append(':').append(g(i)).append(i instanceof EquipableItem && ((EquipableItem) i).isEquipped(h) ? "" : "(pack)").append(',');
			}
		}
		if (pq > posQ) posFound += pq - posQ;
		if (pq < posQ) posUsed += posQ - pq;
		souQ = sq; posQ = pq;
		lv.clear(); for (Item i : h.belongings) if (i instanceof EquipableItem || i instanceof Wand) lv.put(i, i.level());
		if (Dungeon.depth != lastDepth) {
			lastDepth = Dungeon.depth;
			at.append(Dungeon.depth).append(':').append(g(h.belongings.armor())).append('/').append(g(h.belongings.weapon())).append("/s").append(h.STR()).append("/l").append(h.lvl).append(',');
		}
	}

	static String line() {
		Hero h = Dungeon.hero; StringBuilder sb = new StringBuilder("GEAR ");
		sb.append("souFound=").append(souFound).append(";souUsed=").append(souUsed).append(";posFound=").append(posFound).append(";posUsed=").append(posUsed)
				.append(";bestTier=").append(bestTier).append('@').append(bestTierAt).append(";went=").append(went).append(";at=").append(at).append(";pack=");
		if (h != null) for (Item i : h.belongings.backpack) if (i instanceof Armor || i instanceof MeleeWeapon)
			sb.append(g(i)).append("(str").append(i instanceof Armor ? ((Armor) i).STRReq(0) : ((MeleeWeapon) i).STRReq(0)).append(i.cursedKnown ? "k" : "").append("),");
		if (h != null) sb.append(";worn=").append(g(h.belongings.armor())).append('/').append(g(h.belongings.weapon())).append(";str=").append(h.STR());
		return sb.toString();
	}
}
