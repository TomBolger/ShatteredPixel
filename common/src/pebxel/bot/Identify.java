package pebxel.bot;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.*;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.*;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import java.util.*;

/**
 * Identify by use (OG idb_p and its policies, re-derived for Shattered). An unknown potion colour or scroll rune is an
 * opaque label: the bot never reads what it is, only how often each label has turned up. With the kinds' drop rates
 * (Generator's two 15-card decks, items/Generator.java: Healing 3, Mind Vision 2, ... a deck; Strength and Upgrade
 * by the quota instead: 2 / 3 a region) each label's count is a Poisson likelihood for each kind, and as every kind
 * has exactly one label, the odds are balanced both ways (rows and columns, Sinkhorn) until they agree. The label sets
 * are Potion.getUnknown() / Scroll.getUnknown() (unknown to the player); a label is keyed by its class only as an
 * opaque name (the same key the game draws a colour by), never compared with a kind.
 *
 * Policies: the potion to gamble on when dying (the likeliest Healing); a likely Healing is not drink-tested at good
 * health (wasted: it heals 0.8 HT + 14, and the unknown one is the emergency kit); the cell to drink-test on (in or
 * beside water, nothing that burns beside it: Liquid Flame's fire goes out in water, Burning.java); what a Scroll of
 * Identify is read on.
 */
public final class Identify {
	private Identify() { }

	// the kinds and their rates a floor (random: the decks' mean / 15 x about one potion and one scroll a floor)
	static final Class<?>[] POT = { PotionOfStrength.class, PotionOfHealing.class, PotionOfMindVision.class, PotionOfFrost.class, PotionOfLiquidFlame.class,
			PotionOfToxicGas.class, PotionOfHaste.class, PotionOfInvisibility.class, PotionOfLevitation.class, PotionOfParalyticGas.class, PotionOfPurity.class, PotionOfExperience.class };
	static final double[] POTR = { 0.5, 3, 2, 1.5, 1.5, 1.5, 1, 1, 1, 1, 1, 0.5 };
	static final Class<?>[] SCR = { ScrollOfUpgrade.class, ScrollOfIdentify.class, ScrollOfRemoveCurse.class, ScrollOfMirrorImage.class, ScrollOfRecharging.class,
			ScrollOfTeleportation.class, ScrollOfLullaby.class, ScrollOfMagicMapping.class, ScrollOfRage.class, ScrollOfRetribution.class, ScrollOfTerror.class, ScrollOfTransmutation.class };
	static final double[] SCRR = { 0.75, 3, 2, 1.5, 1.5, 1.5, 1, 1, 1, 1, 1, 0.5 };
	static { for (int i = 1; i < 12; i++) { POTR[i] /= 15; SCRR[i] /= 15; } }

	// per game: the count of each label found, the deepest floor
	static Hero game; static long seed;
	static final Map<Class<?>, Integer> found = new HashMap<>(), held = new HashMap<>();
	static int maxDepth;

	static void ensure(Hero h) {
		if (game == h && seed == Dungeon.seed) return;
		game = h; seed = Dungeon.seed; found.clear(); held.clear(); maxDepth = 0;
	}

	/** Called each plan: new copies of an unknown label in the pack count as found. */
	public static void observe(Hero h) {
		ensure(h);
		maxDepth = Math.max(maxDepth, Dungeon.depth);
		Map<Class<?>, Integer> now = new HashMap<>();
		for (Item i : h.belongings) if ((i instanceof Potion && !((Potion) i).isKnown()) || (i instanceof Scroll && !((Scroll) i).isKnown())) now.merge(i.getClass(), i.quantity(), Integer::sum);
		for (Map.Entry<Class<?>, Integer> e : now.entrySet()) { int d = e.getValue() - held.getOrDefault(e.getKey(), 0); if (d > 0) found.merge(e.getKey(), d, Integer::sum); }
		held.clear(); held.putAll(now);
	}

	/** P(label is kind): the balanced odds. A known label: 1 or 0. */
	public static double p(Class<?> label, Class<?> kind) {
		boolean pot = Potion.class.isAssignableFrom(label);
		Class<?>[] K = pot ? POT : SCR; double[] R = pot ? POTR : SCRR;
		Set<?> unk = pot ? Potion.getUnknown() : Scroll.getUnknown();
		if (!unk.contains(label)) return label == kind ? 1 : 0;
		if (!unk.contains(kind)) return 0;
		List<Class<?>> L = new ArrayList<>(); for (Class<?> c : K) if (unk.contains(c)) L.add(c);
		int n = L.size(), F = Math.max(1, maxDepth);
		double[][] M = new double[n][n];
		for (int a = 0; a < n; a++) {
			int cnt = found.getOrDefault(L.get(a), 0); double mx = -1e300;
			for (int b = 0; b < n; b++) { double r = R[idx(K, L.get(b))]; M[a][b] = cnt * Math.log(r) - r * F; mx = Math.max(mx, M[a][b]); }
			for (int b = 0; b < n; b++) M[a][b] = Math.exp(M[a][b] - mx);
		}
		for (int it = 0; it < 60; it++) {
			for (int a = 0; a < n; a++) { double t = 0; for (int b = 0; b < n; b++) t += M[a][b]; if (t > 0) for (int b = 0; b < n; b++) M[a][b] /= t; }
			for (int b = 0; b < n; b++) { double t = 0; for (int a = 0; a < n; a++) t += M[a][b]; if (t > 0) for (int a = 0; a < n; a++) M[a][b] /= t; }
		}
		int a = L.indexOf(label), b = L.indexOf(kind); double t = 0; for (int k = 0; k < n; k++) t += M[a][k];
		return t > 0 ? M[a][b] / t : 0;
	}
	static int idx(Class<?>[] K, Class<?> c) { for (int i = 0; i < K.length; i++) if (K[i] == c) return i; return 1; }

	public static double pHeal(Item i) { return i instanceof Potion ? p(i.getClass(), PotionOfHealing.class) : 0; }

	/** Dying, no known way out: the unknown potion likeliest to be Healing (null: none). */
	public static Item bestGamble(Hero h) {
		observe(h);
		Item best = null; double bp = -1;
		for (Item i : h.belongings.backpack) if (i instanceof Potion && !((Potion) i).isKnown()) { double q = pHeal(i); if (q > bp) { bp = q; best = i; } }
		return best;
	}

	/** Keep this unknown potion for an emergency rather than drink-test it now (likely Healing, health good). */
	public static boolean keep(Hero h, Item i) {
		observe(h);
		return i instanceof Potion && !((Potion) i).isKnown() && pHeal(i) >= 0.3 && h.HP * 2 > h.HT;
	}

	/** A cell to drink-test on: in water or beside it, nothing that burns on or beside it. */
	public static boolean safeCell(Level l, int c) {
		int W = l.width(); boolean wet = l.water[c];
		for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
			int n = c + dx + dy * W; if (n < 0 || n >= l.length() || Math.abs(n % W - c % W) > 1) continue;
			if (l.flamable[n]) return false;
			wet |= l.water[n];
		}
		return wet;
	}

	/** What to read a Scroll of Identify on: an unknown piece of gear the hero might wear (its curse decides), then an
	 *  unknown wand or ring, then the biggest stack of an unknown potion, then of an unknown scroll. */
	public static Item identifyTarget(Hero h) {
		Item best = null; int bk = Integer.MAX_VALUE;
		for (Item i : h.belongings) {
			if (i.isIdentified()) continue;
			int k;
			if (GearValue.judged(i)) k = ((EquipableItem) i).isEquipped(h) ? 30 : 10 - Math.max(0, (int) Math.round(GearValue.worth(h, i)));
			else if (i instanceof Wand || i instanceof Ring) k = 20;
			else if (i instanceof Potion && !((Potion) i).isKnown()) k = 40 - i.quantity();
			else if (i instanceof Scroll && !((Scroll) i).isKnown()) k = 50 - i.quantity();
			else continue;
			if (k < bk) { bk = k; best = i; }
		}
		return best;
	}
}
