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
	/** The least a rescue item is worth, as a share of a death (MT's Tune.HEALCOST: the death odds a Healing drunk costs;
	 *  a full heal saves a long rest, and more crises come than the stretch counts). */
	public static final double MIN = Tune.HEALCOST;
	public static final int LAST = (int) Tune.get("BLAST", 26);

	private static int cDepth = -1, cLvl = -1, cHT = -1, cStr = -1; private static Object cW, cA; private static double[] cQ; private static double cAhead; private static boolean cClimb;

	// (M2) the climb with the Amulet: the floors to come are the ones up to the surface (this one, then depth - 1 to 1),
	// the hero no stronger on the way (no levels, no finds to speak of), each floor's walk stairs to stairs costed
	// against its kinds under the Amulet's curse (MobTable.cursed: a shaman's damage, armour, accuracy, evasion and
	// health x2.25), CLIMBF fights a floor; the run's worth is the win, BWIN floors (a death costs BWIN x P(the rest of the
	// climb survived)); a rescue item must last the whole climb (none found on the way), so one is kept for the Caves.
	public static final double WIN = Tune.get("BWIN", 26), CLIMBF = Tune.get("BCLIMBF", 4);
	public static boolean climbing(Hero h) { return h != null && h.belongings.getItem(com.shatteredpixel.shatteredpixeldungeon.items.Amulet.class) != null; }
	static double climbSurvival(Fighter me, int d) {
		double load = 0; String[] ks = MobTable.kinds(d);
		for (String k : ks) { Fighter f = MobTable.cursed(k); if (f == null) continue; double x = FightModel.duelCost(me, f) / Math.max(1, me.ht); load += CLIMBF / ks.length * x * x; }
		return Math.exp(-(CLIMB0 + 0.55 * load));   // (the floors already explored, their stairs known: a smaller hazard of their own than a new floor's 0.05)
	}
	public static final double CLIMB0 = Tune.get("BCLIMB0", 2) / 100;

	/** q[k]: the chance the k-th floor from here (0: this one) kills, as RunProjection sees it. Cached per depth and build. */
	public static double[] hazards(Hero h) {
		KindOfWeapon w = h.belongings.weapon(); Armor a = h.belongings.armor();
		boolean climb = climbing(h);
		if (cQ != null && cDepth == Dungeon.depth && cLvl == h.lvl && cHT == h.HT && cStr == h.STR() && cW == w && cA == a && cClimb == climb) return cQ;
		if (climb) {
			int n = Math.max(1, Dungeon.depth); double[] q = new double[n]; double alive = 1;
			Fighter me = HeroView.of(h); me.hp = me.ht; me.heals = 0; me.rangedShots = 0;
			for (int k = 0; k < n; k++) { double s; try { s = climbSurvival(me, Dungeon.depth - k); } catch (Throwable e) { s = 0.9; } q[k] = Math.max(0, Math.min(1, 1 - s)); alive *= s; }
			if (Tune.on("BDBG", false)) { StringBuilder b = new StringBuilder("  CLIMB d" + Dungeon.depth + " " + me + " q"); for (double x : q) b.append(String.format(" %.2f", x)); System.out.println(b); }
			cQ = q; cAhead = Math.max(1, WIN * alive); cDepth = Dungeon.depth; cLvl = h.lvl; cHT = h.HT; cStr = h.STR(); cW = w; cA = a; cClimb = true;
			return q;
		}
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
		cQ = q; cClimb = false; cAhead = Math.max(1, ahead + 1); cDepth = Dungeon.depth; cLvl = h.lvl; cHT = h.HT; cStr = h.STR(); cW = w; cA = a;
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
		double[] q = hazards(h); int s = climbing(h) ? q.length : Math.min(q.length, stretch());   // (the climb: nothing more found on the way)
		double l = 0; for (int k = 0; k < s; k++) l += q[k];
		return CRISIS * l;
	}

	/** What the n-th rescue item of a kind (n >= 1) is worth kept, in floors: RESCUE x P(n crises or more) x the floors
	 *  ahead. eff: how well this kind rescues next to Healing (1). */
	public static double rescue(Hero h, int n, double eff) {
		if (n <= 0) return 0;
		double lam = crises(h), term = Math.exp(-lam), cdf = 0;
		for (int k = 0; k < n; k++) { cdf += term; term *= lam / (k + 1); }
		return Math.max(MIN, RESCUE * Math.max(0, 1 - cdf)) * eff * ahead(h);
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
