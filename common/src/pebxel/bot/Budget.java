package pebxel.bot;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;

/**
 * The run's budget (agent BR): what things are worth in the brain's one currency, the floors the run is expected to
 * go on for. A death now costs {@link #ahead} floors; a resource kept is worth the floors it is expected to save later.
 *
 * The floors ahead come from RunProjection (the next floors one by one, the hero growing, each floor's fights costed,
 * a boss on every fifth): q(k), the chance the k-th floor from here kills, and ahead = the sum of the chances to be
 * alive after each floor (to depth 26). A rescue item (Healing, an escape) is worth what it saves: crises come at
 * CRISIS times the floors' death chances over the stretch before more turn up (a few floors, the region's boss in it
 * when that's near), Poisson; the n-th one only matters when there are n crises or more, and it saves the run from
 * one of them RESCUE of the time. So the last Healing is worth more than the third, and more with the boss ahead.
 * All of it from what the hero knows: his own gear as shown, the depth, the kinds of the floors to come (the source).
 */
public final class Budget {
	private Budget() { }

	public static final double CRISIS = Tune.get("BCRISIS", 250) / 100, RESCUE = Tune.get("BRESCUE", 70) / 100, FIND = Tune.get("BFIND", 35) / 100;
	public static final int LAST = (int) Tune.get("BLAST", 26);

	private static int cDepth = -1, cLvl = -1, cHT = -1, cStr = -1; private static Object cW, cA; private static double[] cQ; private static double cAhead;

	/** q[k]: the chance the k-th floor from here (0: this one) kills, as RunProjection sees it. Cached per depth and build. */
	public static double[] hazards(Hero h) {
		KindOfWeapon w = h.belongings.weapon(); Armor a = h.belongings.armor();
		if (cQ != null && cDepth == Dungeon.depth && cLvl == h.lvl && cHT == h.HT && cStr == h.STR() && cW == w && cA == a) return cQ;
		int n = Math.max(1, LAST - Dungeon.depth + 1);
		double[] q = new double[n]; double prev = 1, ahead = 0;
		Item build = null; try { build = UpgradePlan.target(h); } catch (Throwable e) { }
		if (build != w && build != a) build = a != null ? a : w;
		int wl = w == null ? 0 : HeroView.visLevel(w), al = a == null ? 0 : HeroView.visLevel(a);
		for (int k = 0; k < n; k++) {
			double s;
			try { s = RunProjection.survival(h, w, wl, a, al, build, k + 1); } catch (Throwable e) { s = prev * 0.9; }
			q[k] = prev <= 1e-9 ? 0.5 : Math.max(0, Math.min(1, 1 - s / prev));
			prev = s; ahead += s;
		}
		cQ = q; cAhead = Math.max(1, ahead + 1); cDepth = Dungeon.depth; cLvl = h.lvl; cHT = h.HT; cStr = h.STR(); cW = w; cA = a;
		return q;
	}

	/** The floors the run is expected to go on for from here, this one counted: what a death now costs. */
	public static double ahead(Hero h) { hazards(h); return cAhead; }

	/** Floors to the region's boss floor (0: on it). */
	public static int toBoss() { int d = Dungeon.depth; return (5 - d % 5) % 5; }

	/** The stretch a rescue item must last: till more are likely found (1 / FIND floors), the boss in it when within reach. */
	static int stretch() { int s = (int) Math.ceil(1 / Math.max(0.05, FIND)); int b = toBoss(); return Math.max(s, b < 4 ? b + 1 : s); }

	/** The expected crises over the stretch (this floor's counted from now). */
	public static double crises(Hero h) {
		double[] q = hazards(h); int s = Math.min(q.length, stretch());
		double l = 0; for (int k = 0; k < s; k++) l += q[k];
		return CRISIS * l;
	}

	/** What the n-th rescue item of a kind (n >= 1) is worth kept, in floors: RESCUE x P(n crises or more) x the floors
	 *  ahead. eff: how well this kind rescues next to Healing (1). */
	public static double rescue(Hero h, int n, double eff) {
		if (n <= 0) return 0;
		double lam = crises(h), term = Math.exp(-lam), cdf = 0;
		for (int k = 0; k < n; k++) { cdf += term; term *= lam / (k + 1); }
		return RESCUE * eff * Math.max(0, 1 - cdf) * ahead(h);
	}

	/** The value of n such items together (the first n marginal values). */
	public static double rescueAll(Hero h, int n, double eff) { double v = 0; for (int k = 1; k <= n; k++) v += rescue(h, k, eff); return v; }

	public static String dump(Hero h) {
		double[] q = hazards(h); StringBuilder b = new StringBuilder("ahead " + String.format("%.2f", ahead(h)) + " crises " + String.format("%.2f", crises(h)) + " q");
		for (int k = 0; k < Math.min(6, q.length); k++) b.append(String.format(" %.2f", q[k]));
		b.append(String.format(" heal1 %.2f heal2 %.2f", rescue(h, 1, 1), rescue(h, 2, 1)));
		return b.toString();
	}
}
