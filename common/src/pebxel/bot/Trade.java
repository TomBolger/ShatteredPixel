package pebxel.bot;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Shopkeeper;
import com.shatteredpixel.shatteredpixeldungeon.items.*;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.MagicalHolster;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Blandfruit;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.food.MysteryMeat;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.*;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import java.util.*;

/**
 * Buying and selling by worth (OG want_buy2 and its selling, re-derived for Shattered's shops: ShopRoom's stock,
 * Shopkeeper.sellPrice = value x 5 x (depth / 5 + 1); selling pays value()). A ware's worth is in "points" (about a
 * health point each, OG's scale: a Healing potion ~60): an Ankh is a second life, Healing its heal, an unknown potion its
 * odds of being Healing (Identify), food by how short the pack is of it (starving was 16% of the honest bot's turns),
 * a bag its room, armour and weapons GearValue's gain over the next floors' fights, Magic Mapping, Remove Curse with a
 * cursed piece on, a bomb. Wares are bought greedily, the best worth for the gold first (OG want_buy2: the gold one
 * buys can't buy another). Selling: the pack's gear no use to the hero (GearValue.spare), known-cursed gear not worn,
 * and spare wands known bad, by the gold they bring.
 */
public final class Trade {
	private Trade() { }

	static int foodCount(Hero h) { int n = 0; for (Item i : h.belongings.backpack) if (i instanceof Food && !(i instanceof Blandfruit)) n += i.quantity() * (i instanceof MysteryMeat ? 1 : 2); return n; }

	/** A ware's worth in points (0: not wanted). */
	public static double worth(Hero h, Item i) {
		int d = Dungeon.depth;
		if (i instanceof Ankh) return h.belongings.getItem(Ankh.class) == null ? Tune.ANKH : 10;
		if (i instanceof PotionOfHealing && ((Potion) i).isKnown()) return 0.8 * h.HT + 14;
		if (i instanceof Potion && !((Potion) i).isKnown()) return Identify.pHeal(i) * (0.8 * h.HT + 14) + 6;
		if (i instanceof Food && !(i instanceof Blandfruit)) { int f = foodCount(h); return f <= 1 ? Tune.FOOD : f <= 3 ? Tune.FOOD / 2 : f <= 5 ? Tune.FOOD / 7 : 0; }
		if (i instanceof Bag && !(i instanceof MagicalHolster)) { for (Bag b : h.belongings.getBags()) if (b.getClass() == i.getClass()) return 0; return 45; }
		if (i instanceof Armor || i instanceof MeleeWeapon) {
			double base = GearValue.slotCost(h, !(i instanceof Armor)), g = base - GearValue.costWith(h, i, base);
			return g > base * 0.05 ? g * 6 : 0;   // (health a fight, ~6 fights a floor till the next upgrade)
		}
		if (i instanceof ScrollOfMagicMapping && ((Scroll) i).isKnown()) return d >= 16 ? 25 : 12;
		if (i instanceof ScrollOfRemoveCurse && ((Scroll) i).isKnown()) { for (Item w : h.belongings) if (w instanceof EquipableItem && ((EquipableItem) w).isEquipped(h) && w.cursed && w.cursedKnown) return 50; return 5; }
		if (i instanceof ScrollOfIdentify && ((Scroll) i).isKnown()) return Identify.identifyTarget(h) != null ? 8 : 0;
		if (i instanceof Bomb) return 12;
		if (i instanceof MissileWeapon) return 6;
		return 0;
	}

	/** The wares worth buying, in the order to buy them: by worth for the price, the gold spent as it goes. */
	public static List<Heap> plan(Hero h, Collection<Heap> wares) {
		List<Heap> ws = new ArrayList<>();
		for (Heap w : wares) if (w.type == Heap.Type.FOR_SALE && w.seen && !w.isEmpty() && worth(h, w.peek()) >= Tune.MINW) ws.add(w);
		ws.sort(Comparator.comparingDouble((Heap w) -> -worth(h, w.peek()) / Math.max(1, Math.sqrt(Shopkeeper.sellPrice(w.peek())))));
		// (M2, WB's report: seed 1000 passed the depth-6 shop with 1,256 gold and no Ankh) the first Ankh, when it can be paid for, before anything else: a second life (WB's blessed revives)
		if (h.belongings.getItem(Ankh.class) == null) for (Heap w : ws) if (w.peek() instanceof Ankh && Shopkeeper.sellPrice(w.peek()) <= Dungeon.gold) { ws.remove(w); ws.add(0, w); break; }
		List<Heap> out = new ArrayList<>(); int gold = Dungeon.gold;
		for (Heap w : ws) { int pr = Shopkeeper.sellPrice(w.peek()); if (pr <= gold) { out.add(w); gold -= pr; } }
		return out;
	}

	/** Assist's shop rank for a ware (0: don't; higher first): its place in the plan. */
	public static int buyRank(Hero h, Heap ware, Collection<Heap> wares) {
		List<Heap> p = plan(h, wares);
		int i = p.indexOf(ware);
		return i < 0 ? 0 : Math.max(1, 9 - i);
	}

	/** The next thing to sell (null: nothing): the most gold first. */
	public static Item sellPick(Hero h) {
		Item best = null; int bv = 0;
		Set<Item> spare = Collections.newSetFromMap(new IdentityHashMap<>()); spare.addAll(GearValue.spare(h));
		for (Item i : h.belongings.backpack) {
			if (!Shopkeeper.canSell(i) || i.isEquipped(h) || i.unique) continue;
			boolean sell = spare.contains(i)
					|| ((i instanceof Armor || i instanceof MeleeWeapon || i instanceof Wand) && i.cursed && i.cursedKnown)
					|| (i instanceof Wand && i.levelKnown && i.level() < 0);
			if (sell && i.value() > bv) { bv = i.value(); best = i; }
		}
		return best;
	}

	/** Gold to be had from selling now (to decide whether to walk to the shopkeeper). */
	public static int sellGold(Hero h) {
		int g = 0; Set<Item> spare = Collections.newSetFromMap(new IdentityHashMap<>()); spare.addAll(GearValue.spare(h));
		for (Item i : spare) if (Shopkeeper.canSell(i)) g += i.value();
		return g;
	}
}
