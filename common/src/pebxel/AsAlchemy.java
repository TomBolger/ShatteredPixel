package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.Recipe;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Blandfruit;
import com.shatteredpixel.shatteredpixeldungeon.items.food.MysteryMeat;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.*;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.elixirs.*;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.*;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.*;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ExoticScroll;
import com.shatteredpixel.shatteredpixeldungeon.items.spells.MagicalInfusion;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.*;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.Trinket;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.TrinketCatalyst;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.plants.Plant;
import com.shatteredpixel.shatteredpixeldungeon.plants.Sungrass;
import java.util.*;
import pebxel.Assist.Target;
import static pebxel.Assist.*;

/**
 * The alchemy pot, honestly (agent K3, agent WB; strategy-reference 2.8, strategy-v4 6): what is worth brewing from
 * what the hero carries, with the energy he has (crystals picked up, items energized; no cheat): three Sungrass seeds
 * into Healing first, the Trinket Catalyst into a trinket and the trinket (AsEconomy's pick) up to +3, Magical
 * Infusion for an enchanted piece the next upgrade could strip, spare known scrolls into two runestones the bot uses,
 * any three seeds into a potion, spare known potions into exotics and the boss elixirs (Dragon's Blood, Toxic
 * Essence) with the energy left over, mystery meat stewed. Known junk (spares beyond what the bot keeps of a kind, a
 * Flock or Aggression stone) is energized first when the energy is short. Never an unknown scroll or potion (its
 * recipe would tell what it is), Healing, Strength or gear. {@link #suggest} walks to a known pot once a floor while
 * there's something worth making; {@link #plan} is what to put in and {@link #planIdx} which recipe of theirs, for the
 * alchemy screen (BotAlchemy presses its buttons); {@link #energizePick} the next thing to energize.
 */
public final class AsAlchemy {
	private AsAlchemy() {}

	static final boolean ON = opt("ALCHEMY", true);
	static Level doneL;   // the floor the pot was used on (once a floor)
	public static String planOut = "";
	public static int planIdx;

	/** How many of each known kind the bot keeps (the rest is spare: stones, an exotic or energy). */
	static int keep(Item i) {
		Hero h = Dungeon.hero;
		if (i instanceof ScrollOfUpgrade || i instanceof PotionOfHealing || i instanceof PotionOfStrength || i instanceof PotionOfExperience) return 99;
		if (i instanceof ExoticScroll || i instanceof ExoticPotion || i instanceof Elixir) return 99;
		if (i instanceof ScrollOfRage) return 0;
		if (i instanceof ScrollOfRecharging) { boolean any = h.belongings.getItem(com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff.class) != null || h.belongings.artifact() != null; for (Item o : h.belongings.backpack) any |= o instanceof com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand; return any ? 2 : 0; }
		if (i instanceof ScrollOfMirrorImage || i instanceof ScrollOfRemoveCurse || i instanceof ScrollOfTransmutation || i instanceof ScrollOfLullaby || i instanceof ScrollOfRetribution) return 1;
		if (i instanceof Scroll) return 2;   // (Identify, Magic Mapping, Teleportation, Terror)
		if (i instanceof PotionOfMindVision || i instanceof PotionOfPurity || i instanceof PotionOfLevitation) return 1;
		if (i instanceof Potion) return 2;
		return 99;
	}
	static boolean spare(Item i) { return (i instanceof Scroll || i instanceof Potion) && known(i) && !i.cursed && i.quantity() > keep(i); }
	/** The stones the bot has a use for (AsItems' fights and calm uses). */
	static boolean usefulStone(Item s) { return !(s instanceof StoneOfFlock); }

	/** Can this go into a pot (the hero's own spare things only)? */
	static boolean ingredient(Item i) {
		if (i instanceof Plant.Seed || i instanceof MysteryMeat || i instanceof TrinketCatalyst || (i instanceof Blandfruit && ((Blandfruit) i).potionAttrib == null)) return true;
		if (i instanceof Trinket) return i.level() < 3 && AsEconomy.trinketWorth(i.getClass()) > 0;
		if (i instanceof ScrollOfUpgrade) return known(i) && AsEconomy.infusionWanted();
		if (i instanceof PotionOfDragonsBreath || i instanceof PotionOfCorrosiveGas) return true;   // (the boss elixirs)
		return spare(i);
	}

	/** The energy at hand (the pot's; a toolkit's charge is not counted). */
	static int energy() { return Dungeon.energy; }
	/** Energy kept for what matters more: the catalyst, the next trinket upgrade, an Infusion with the scroll held. */
	static int reserve() {
		Hero h = Dungeon.hero; int r = 0;
		if (h.belongings.getItem(TrinketCatalyst.class) != null) r += 6;
		for (Item i : h.belongings.backpack) if (i instanceof Trinket && i.level() < 3 && AsEconomy.trinketWorth(i.getClass()) > 0) {
			try { ArrayList<Item> one = new ArrayList<>(); Item d = i.duplicate(); d.quantity(1); one.add(d); r += new Trinket.UpgradeTrinket().cost(one); } catch (Throwable e) { r += 10; }
		}
		ScrollOfUpgrade su = h.belongings.getItem(ScrollOfUpgrade.class);
		if (su != null && known(su) && AsEconomy.infusionWanted()) r += 12;
		return r;
	}

	/** How much a recipe's output is worth here (0: not worth the ingredients). */
	static int worth(Item out, List<Item> ing) {
		boolean seeds = true, sun = false; for (Item i : ing) { seeds &= i instanceof Plant.Seed; sun |= i instanceof Sungrass.Seed; }
		Item in = ing.size() == 1 ? ing.get(0) : null;
		if (out instanceof PotionOfHealing && seeds) return 10;
		if (in instanceof TrinketCatalyst) return 9;
		if (in instanceof Trinket && out instanceof Trinket) return AsEconomy.trinketWorth(out.getClass()) > 0 ? 8 : 0;   // (strategy-v4 6: the chosen one to +3 before anything optional)
		if (out instanceof MagicalInfusion) return AsEconomy.infusionWanted() ? 7 : 0;
		if (out instanceof Potion && seeds && !sun) return 6;   // (Sungrass kept for Healing or planting)
		if (in instanceof Scroll && out instanceof Runestone) return spare(in) && usefulStone(out) ? 5 : 0;
		if (out instanceof ElixirOfDragonsBlood || out instanceof ElixirOfToxicEssence) return 4;
		if (out instanceof com.shatteredpixel.shatteredpixeldungeon.items.food.StewedMeat) return 4;
		if (in instanceof Potion && out instanceof ExoticPotion) return spare(in) ? 3 : 0;
		if (out instanceof Blandfruit) return 3;
		return 0;
	}

	/** The best recipe the pack makes now (ingredients, one unit each, in order), or null; planIdx: which of its recipes. */
	public static List<Item> plan() {
		Hero h = Dungeon.hero; if (h == null) return null;
		List<Item> pool = new ArrayList<>();
		for (Item i : h.belongings) if (!(i instanceof Bag) && !(i instanceof com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem && ((com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem) i).isEquipped(h)) && ingredient(i) && Recipe.usableInRecipe(i)) pool.add(i);
		List<Item> best = null; int bv = 0;
		int n = Math.min(pool.size(), 20), en = energy(), res = reserve();
		for (int a = 0; a < n; a++) for (int b = a; b <= n; b++) for (int c = b; c <= n; c++) {
			if (b == n && c != n) continue;
			List<Item> pick = new ArrayList<>(); pick.add(pool.get(a)); if (b < n) pick.add(pool.get(b)); if (c < n) pick.add(pool.get(c));
			if (pick.size() > 1) { boolean any = false; for (Item i : pick) any |= i instanceof Plant.Seed || i instanceof MysteryMeat || i instanceof Blandfruit; if (!any) continue; }   // (two or three: seeds, meat, fruit only)
			Map<Item, Integer> need = new IdentityHashMap<>(); boolean ok = true;
			for (Item i : pick) if (need.merge(i, 1, Integer::sum) > i.quantity()) ok = false;
			if (!ok) continue;
			ArrayList<Item> ing = new ArrayList<>();
			for (Item i : pick) { Item d = i.duplicate(); if (d == null) { ok = false; break; } d.quantity(1); ing.add(d); }
			if (!ok) continue;
			try {
				ArrayList<Recipe> rs = Recipe.findRecipes(ing);
				for (int k = 0; k < rs.size(); k++) {
					Recipe r = rs.get(k); int cost = r.cost(ing); if (cost > en) continue;
					Item out = r.sampleOutput(ing); if (out == null) continue;
					int w = worth(out, ing);
					if (w <= 0 || (cost > 0 && w < 8 && en - cost < res)) continue;   // (the energy kept for the trinket and the Infusion)
					int v = w * 10 + pick.size();
					if (v > bv) { bv = v; best = pick; planIdx = k; planOut = out.getClass().getSimpleName(); }
				}
			} catch (Throwable e) { }
		}
		return best;
	}

	/** The next known junk to energize at the pot (energy short of what's wanted), or null: a spare whose stone is no
	 *  use, a spare potion while the trinket or the Infusion wants energy, a Flock or Aggression stone. */
	public static Item energizePick() {
		Hero h = Dungeon.hero; if (h == null) return null;
		boolean short_ = energy() < reserve();
		for (Item i : h.belongings) {
			if (i instanceof Bag || i.energyVal() <= 0 || i.cursed || i.unique) continue;
			if (i instanceof com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem && ((com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem) i).isEquipped(h)) continue;
			if (i instanceof StoneOfFlock) return i;
			if (i instanceof StoneOfAggression && i.quantity() > 1) return i;
			if (i instanceof ScrollOfMirrorImage && spare(i)) return i;   // (its stone is Flock's sheep)
			if (short_ && i instanceof Potion && !(i instanceof ExoticPotion) && spare(i)) return i;
		}
		return null;
	}

	/** To a known pot, once a floor, while there's something worth making (or junk to energize for it) and nothing about. */
	static Target suggest() {
		if (!ON || doneL == L) return null;
		int pot = -1;
		for (int c = 0; c < L.length(); c++) if (L.map[c] == Terrain.ALCHEMY && known[c] && walk(c) < INF) pot = c;
		if (pot < 0) return null;
		String why;
		if (plan() != null) why = planOut;
		else if (energizePick() != null) why = "energize " + energizePick().getClass().getSimpleName();
		else return null;
		return new Target(pot, GO, "alchemy: to the pot (" + why + ")");
	}

	/** The alchemy screen was opened on this floor: not again here. */
	public static void used() { doneL = Dungeon.level; }
}
