package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.Recipe;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Blandfruit;
import com.shatteredpixel.shatteredpixeldungeon.items.food.MysteryMeat;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.TrinketCatalyst;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.plants.Plant;
import com.shatteredpixel.shatteredpixeldungeon.plants.Sungrass;
import java.util.*;
import pebxel.Assist.Target;
import static pebxel.Assist.*;

/**
 * The alchemy pot, honestly (agent K3; strategy-reference 2.8): what is worth brewing from what the hero carries,
 * with the energy he has (crystals picked up; no cheat): three Sungrass seeds into Healing first, the Trinket Catalyst
 * into a trinket, any three seeds into a potion (seeds the bot has no other use for), mystery meat stewed. Never a
 * scroll, a potion or gear as an ingredient. {@link #suggest} walks to a known pot once a floor while there's
 * something worth making; {@link #plan} is what to put in, for the alchemy screen (BotAlchemy presses its buttons).
 */
public final class AsAlchemy {
	private AsAlchemy() {}

	static final boolean ON = opt("ALCHEMY", true);
	static Level doneL;   // the floor the pot was used on (once a floor)
	public static String planOut = "";

	/** Can this go into a pot (the hero's own spare things only)? */
	static boolean ingredient(Item i) {
		return i instanceof Plant.Seed || i instanceof MysteryMeat || i instanceof TrinketCatalyst || (i instanceof Blandfruit && ((Blandfruit) i).potionAttrib == null);
	}

	/** How much a recipe's output is worth here (0: not worth the ingredients). */
	static int worth(Item out, List<Item> ing) {
		if (out instanceof PotionOfHealing) return 10;
		if (out instanceof com.shatteredpixel.shatteredpixeldungeon.items.trinkets.Trinket) return 8;
		boolean seeds = true, sun = false; for (Item i : ing) { seeds &= i instanceof Plant.Seed; sun |= i instanceof Sungrass.Seed; }
		if (out instanceof Potion && seeds && !sun) return 6;   // (Sungrass kept for Healing or planting)
		if (out instanceof com.shatteredpixel.shatteredpixeldungeon.items.food.StewedMeat) return 4;
		if (out instanceof Blandfruit) return 3;
		return 0;
	}

	/** The best recipe the pack makes now (ingredients, one unit each, in order), or null. */
	public static List<Item> plan() {
		Hero h = Dungeon.hero; if (h == null) return null;
		List<Item> pool = new ArrayList<>();
		for (Item i : h.belongings) if (!(i instanceof Bag) && ingredient(i) && Recipe.usableInRecipe(i)) pool.add(i);
		List<Item> best = null; int bv = 0;
		int n = Math.min(pool.size(), 20);
		for (int a = 0; a < n; a++) for (int b = a; b <= n; b++) for (int c = b; c <= n; c++) {
			if (b == n && c != n) continue;
			List<Item> pick = new ArrayList<>(); pick.add(pool.get(a)); if (b < n) pick.add(pool.get(b)); if (c < n) pick.add(pool.get(c));
			Map<Item, Integer> need = new IdentityHashMap<>(); boolean ok = true;
			for (Item i : pick) if (need.merge(i, 1, Integer::sum) > i.quantity()) ok = false;
			if (!ok) continue;
			ArrayList<Item> ing = new ArrayList<>();
			for (Item i : pick) { Item d = i.duplicate(); if (d == null) { ok = false; break; } d.quantity(1); ing.add(d); }
			if (!ok) continue;
			try {
				ArrayList<Recipe> rs = Recipe.findRecipes(ing); if (rs.isEmpty()) continue;
				Recipe r = rs.get(0); if (r.cost(ing) > Dungeon.energy) continue;
				Item out = r.sampleOutput(ing); if (out == null) continue;
				int v = worth(out, ing) * 10 + pick.size();
				if (worth(out, ing) > 0 && v > bv) { bv = v; best = pick; planOut = out.getClass().getSimpleName(); }
			} catch (Throwable e) { }
		}
		return best;
	}

	/** To a known pot, once a floor, while there's something worth making and nothing about. */
	static Target suggest() {
		if (!ON || doneL == L) return null;
		int pot = -1;
		for (int c = 0; c < L.length(); c++) if (L.map[c] == Terrain.ALCHEMY && known[c] && walk(c) < INF) pot = c;
		if (pot < 0 || plan() == null) return null;
		return new Target(pot, GO, "alchemy: to the pot (" + planOut + ")");
	}

	/** The alchemy screen was opened on this floor: not again here. */
	public static void used() { doneL = Dungeon.level; }
}
