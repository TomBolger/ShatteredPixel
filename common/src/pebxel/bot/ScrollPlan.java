package pebxel.bot;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.*;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfRemoveCurse;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.*;
import java.util.*;

/**
 * The expected-value upgrade planner (agent UP): where each Scroll of Upgrade goes, or whether it is held for a better
 * piece the odds say is coming. Thomas: "We know the odds that each armor type spawns or is given as a reward. We
 * should be able to have the bot do the math on upgrade vs wait and hold the scrolls."
 *
 * The odds (ARM / WPN below) are the real v4.0.2 level generator's, run for 4,000 seeds (server GearOddsGen): for each
 * floor, the chance a piece of tier 2..5 or better is there to take, each source at the share a hero gets of it (heaps,
 * chests, locked chests, mimics, statues, the mobs' drops (guards: armour, skeletons: weapons, golems, armoured brutes):
 * all; secret rooms 0.4, vault crystal chests 0.5, the shops (6: leather, 11: mail, 16: scale) 0.8, the Ghost's reward
 * (floors 2-4) 0.5 each way, the Blacksmith's and the Imp's 0.3). The tier odds by floor are Generator.floorSetTierProbs
 * (floors 1-4: 75/20/4/1 leather/mail/scale/plate, 5-9: 25/50/20/5, 10-14: 0/40/50/10, 15-19: 0/20/40/40, 20-: 0/0/20/80;
 * special rooms one set deeper). Upgrades: 3 a region on its four regular floors (Dungeon.souNeeded), Strength 2
 * (posNeeded), none on boss floors; the rate seen this run counts too (MILD's extra two). Upgrades can't be moved: the
 * Blacksmith's reforge destroys one piece for a +1 on the other, Transmutation keeps the tier; the Warrior's seal
 * carries one (not counted). So a scroll in the interim piece is lost when a better one is taken up.
 *
 * The currency is the Brain's, the floors the run goes on for: the next HOR floors one by one (the hero a level a floor
 * stronger, the region's Strength found as it comes), each floor's survival exp(-(HZ0 + HZK x load)), load its fights'
 * (cost / health)^2 at its spawn mix (FightModel.duelCost, MobTable; a boss on every fifth: RunProjection's model), and
 * the run's expected floors the sum of the chances to be alive after each. The options for a scroll: into the worn (or
 * the committed) armour now, into the weapon now, or held up to W floors: if a better piece turns up meanwhile the
 * scroll goes into it, else into the armour or weapon then. Each is valued over the arrival scenarios (the floor and the
 * tier of the next better piece, from the odds), and at an arrival the hero takes whichever is better for the rest of
 * the run (keeping on with the old piece, or the new one with the scrolls from then on). Survival now weighs most:
 * a death on this floor costs every floor after it; a floor far ahead counts only if the hero gets there. So in the
 * sewers holding rarely pays (HOLD1's lesson), deeper it can.
 *
 * Only what the player sees: shown levels and enchantments (HeroView.visLevel), known scrolls, the source's odds.
 */
public final class ScrollPlan {
	private ScrollPlan() { }

	public static final boolean ON = Tune.on("UPLAN", true);
	static final double HZ0 = Tune.get("UPHZ0", 5) / 100, HZK = Tune.get("UPHZK", 55) / 100;
	static final int HOR = (int) Tune.get("UPHOR", 12), WMAX = (int) Tune.get("UPWMAX", 4);
	static final double SHARE = Tune.get("UPSHARE", 50) / 100, SOUR = Tune.get("UPSOUR", 75) / 100, POSR = Tune.get("UPPOSR", 50) / 100;
	static final double FIND = Tune.get("UPFIND", 100) / 100, HOLDM = Tune.get("UPHOLDM", 0) / 1000, CHYST = Tune.get("UPCHYST", 10) / 1000;
	static final int HOLDMIN = (int) Tune.get("UPHOLDMIN", 1);   // (the first floor a scroll may be held on)
	static final double UNK = Tune.get("UPUNK", 40) / 100;   // (an unknown piece's level counted this much below its shown +0: a third are cursed, -1..-3)
	public static final boolean SPLIT = Tune.on("UPSPLIT", true);   // (off: MD's split (AsWeapon) once the planner spends)
	public static final boolean HOLD = Tune.on("UPHOLDON", true), WPLAN = Tune.on("UPWPLAN", false), PCOMMIT = Tune.on("UPCOMMIT", false);
	/** The armour plan's own commit (AsGear's, HE's) when the planner's isn't used (PCOMMIT off: UP-a1, 288 full games:
	 *  the planner's 12-floor commit kept cloth+n over mail on 68% of heroes at depth 17, against 33%; 15.44 against 16.26). */
	public static java.util.function.Function<Hero, Armor> armourCommit;
	static final boolean DBG = Tune.on("UPDBG", false);

	// ---- the odds (GearOddsGen 4000, v4.0.2) ----
	static final float[][] ARM = {   // (floor 1..25: P(tier >= 2..5 found there), 4000 seeds)
		{ 0.304f, 0.137f, 0.034f, 0.006f }, { 0.509f, 0.262f, 0.097f, 0.021f }, { 0.466f, 0.228f, 0.076f, 0.017f }, { 0.466f, 0.238f, 0.075f, 0.020f }, { 0.000f, 0.000f, 0.000f, 0.000f },
		{ 0.882f, 0.361f, 0.179f, 0.030f }, { 0.557f, 0.480f, 0.226f, 0.048f }, { 0.553f, 0.467f, 0.210f, 0.046f }, { 0.555f, 0.472f, 0.222f, 0.037f }, { 0.000f, 0.000f, 0.000f, 0.000f },
		{ 0.886f, 0.886f, 0.312f, 0.114f }, { 0.580f, 0.580f, 0.456f, 0.162f }, { 0.547f, 0.547f, 0.423f, 0.151f }, { 0.539f, 0.539f, 0.412f, 0.151f }, { 0.000f, 0.000f, 0.000f, 0.000f },
		{ 0.885f, 0.885f, 0.877f, 0.268f }, { 0.511f, 0.511f, 0.469f, 0.357f }, { 0.525f, 0.525f, 0.484f, 0.369f }, { 0.587f, 0.587f, 0.561f, 0.448f }, { 0.000f, 0.000f, 0.000f, 0.000f },
		{ 0.450f, 0.450f, 0.450f, 0.369f }, { 0.463f, 0.463f, 0.463f, 0.394f }, { 0.416f, 0.416f, 0.416f, 0.349f }, { 0.418f, 0.418f, 0.418f, 0.351f }, { 0.000f, 0.000f, 0.000f, 0.000f } };
	static final float[][] WPN = {   // (floor 1..25: P(tier >= 2..5 found there), 4000 seeds; melee, the staff not counted)
		{ 0.323f, 0.109f, 0.029f, 0.008f }, { 0.525f, 0.236f, 0.068f, 0.017f }, { 0.489f, 0.215f, 0.067f, 0.017f }, { 0.492f, 0.209f, 0.062f, 0.012f }, { 0.000f, 0.000f, 0.000f, 0.000f },
		{ 0.936f, 0.584f, 0.255f, 0.055f }, { 0.636f, 0.531f, 0.232f, 0.049f }, { 0.517f, 0.430f, 0.184f, 0.040f }, { 0.457f, 0.374f, 0.160f, 0.037f }, { 0.000f, 0.000f, 0.000f, 0.000f },
		{ 0.888f, 0.888f, 0.313f, 0.076f }, { 0.592f, 0.592f, 0.458f, 0.157f }, { 0.565f, 0.565f, 0.427f, 0.150f }, { 0.561f, 0.561f, 0.432f, 0.158f }, { 0.000f, 0.000f, 0.000f, 0.000f },
		{ 0.890f, 0.890f, 0.877f, 0.234f }, { 0.538f, 0.538f, 0.479f, 0.296f }, { 0.550f, 0.550f, 0.500f, 0.330f }, { 0.610f, 0.610f, 0.570f, 0.399f }, { 0.000f, 0.000f, 0.000f, 0.000f },
		{ 0.471f, 0.471f, 0.471f, 0.389f }, { 0.480f, 0.480f, 0.480f, 0.413f }, { 0.442f, 0.442f, 0.442f, 0.373f }, { 0.455f, 0.455f, 0.455f, 0.383f }, { 0.000f, 0.000f, 0.000f, 0.000f } };

	/** P(a piece of tier >= t is found on floor d), armour or (melee) weapon. */
	public static double pFloor(boolean arm, int d, int t) {
		if (t <= 1) return 1; if (t > 5 || d < 1 || d > 25) return 0;
		return Math.min(1, FIND * (arm ? ARM : WPN)[d - 1][t - 2]);
	}
	/** P(one is found from the rest of floor d (half of it) to floor D). */
	public static double pBy(boolean arm, int d, int D, int t) {
		double miss = 1; for (int f = d; f <= D; f++) miss *= 1 - pFloor(arm, f, t) * (f == d ? 0.5 : 1);
		return 1 - miss;
	}
	static boolean regular(int d) { return d % 5 != 0 && d <= 25; }
	/** Regular floors from d + 1 to d + k (where Upgrades and Strength are found). */
	static int reg(int d, int k) { int n = 0; for (int f = d + 1; f <= d + k; f++) if (regular(f)) n++; return n; }

	// ---- the scroll rate seen this run (the MILD cheat's two a region more are seen, never read from the cheat) ----
	static Hero seenHero; static int seenWas, seen;
	static void observe(Hero h) {
		ScrollOfUpgrade s = h.belongings.getItem(ScrollOfUpgrade.class);
		int q = s != null && s.isKnown() ? s.quantity() : 0;
		if (seenHero != h) { seenHero = h; seenWas = 0; seen = 0; }
		if (q > seenWas) seen += q - seenWas;
		seenWas = q;
	}
	/** Upgrades a regular floor: the source's 0.75, moved by the run's own count (4 floors' worth of prior). */
	static double rate() {
		int d = Dungeon.depth, fl = 0; for (int f = 1; f <= Math.min(25, d); f++) if (regular(f)) fl++;
		return Math.max(SOUR, (seen + SOUR * 4) / (fl + 4.0));
	}

	/** (for the armour plan's commit) The Upgrades the armour can count on over the next floors: the run's rate, the
	 *  regular floors, the armour's share (FSH) of them. */
	public static int ahead(Hero h, int floors) { observe(h); return (int) Math.round(rate() * reg(Dungeon.depth, floors) * FSH); }
	static final double FSH = Tune.get("UPFSH", 70) / 100;

	// ---- the projection context: one per state (depth, gear, scrolls); its floor values memoised ----
	static final class Ctx {
		Hero h; int d0, K, kh; int[] str; double[] m; long key;
		KindOfWeapon w0; Armor a0; boolean wSlot, aSlot; double shA, shW;
		Item cA, cW;   // the committed pieces
		double[] base;   // the base trajectory's load each floor (the anchor's reference)
		double strong;   // 0: a plain hero's health for his level, 1: half again (MILD)
		Armor[] armB; int[] armL; KindOfWeapon[] wpnB; int[] wpnL;   // the base trajectory (the other slot's, while one is valued)
		final List<Object> ids = new ArrayList<>();
		final Map<Long, Double> memo = new HashMap<>();
	}
	static Ctx ctx;
	static Armor[] TA; static MeleeWeapon[] TW;   // the tiers' stand-ins for pieces not yet found
	static void templates() {
		if (TA != null) return;
		TA = new Armor[]{ null, new ClothArmor(), new LeatherArmor(), new MailArmor(), new ScaleArmor(), new PlateArmor() };
		TW = new MeleeWeapon[]{ null, new WornShortsword(), new Shortsword(), new Sword(), new Longsword(), new Greatsword() };
	}
	static int id(Ctx c, Object o) { if (o == null) return 0; for (int i = 0; i < c.ids.size(); i++) if (c.ids.get(i) == o) return i + 1; c.ids.add(o); return c.ids.size(); }

	static boolean upgradable(Item i) { return i != null && i.isUpgradable() && !(i instanceof com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow); }
	static boolean liftable(Hero h) {
		ScrollOfRemoveCurse rc = h.belongings.getItem(ScrollOfRemoveCurse.class); ScrollOfUpgrade up = h.belongings.getItem(ScrollOfUpgrade.class);
		return (rc != null && rc.isKnown()) || (up != null && up.isKnown());
	}
	static int inHand(Hero h) { ScrollOfUpgrade s = h.belongings.getItem(ScrollOfUpgrade.class); return s != null && s.isKnown() ? s.quantity() : 0; }
	/** The level a piece is counted at: shown, an unknown one a little under (it may be cursed). */
	static double lv(Item i) { return HeroView.visLevel(i) - (i.levelKnown ? 0 : UNK); }
	static boolean usableArm(Hero h, Item i) { return i instanceof Armor && !(i instanceof ClassArmor) && !(i.cursedKnown && i.cursed && !liftable(h)) && !(i.levelKnown && i.level() < -1); }
	static boolean usableWpn(Hero h, Item i) { return i instanceof MeleeWeapon && !(i instanceof MagesStaff) && !(i.cursedKnown && i.cursed && !liftable(h)) && !(i.levelKnown && i.level() < -1); }

	static Ctx context(Hero h) {
		observe(h);
		KindOfWeapon w = h.belongings.weapon(); Armor a = h.belongings.armor();
		int kh = inHand(h);
		long key = 1469598103934665603L;
		key = (key ^ Dungeon.depth) * 1099511628211L; key = (key ^ h.STR()) * 1099511628211L; key = (key ^ kh) * 1099511628211L; key = (key ^ seen) * 1099511628211L;
		for (Item i : h.belongings) if (i instanceof Armor || i instanceof KindOfWeapon) { key = (key ^ i.getClass().getName().hashCode()) * 1099511628211L; /* (no identity hashes: hashing numbers an object, and the game own numbers would shift) */ key = (key ^ (HeroView.visLevel(i) * 31 + (i.isEquipped(h) ? 7 : 0) + (i.cursedKnown ? 3 : 0) + (i.levelKnown ? 1 : 0))) * 1099511628211L; }
		if (ctx != null && ctx.h == h && ctx.key == key) return ctx;
		Item oldA = ctx != null && ctx.h == h ? ctx.cA : null, oldW = ctx != null && ctx.h == h ? ctx.cW : null;
		templates();
		Ctx c = new Ctx(); c.h = h; c.key = key; c.d0 = Dungeon.depth; c.kh = kh; c.w0 = w; c.a0 = a;
		c.K = Math.max(1, Math.min(HOR, 26 - c.d0));
		c.strong = Math.max(0, Math.min(1, (h.HT / (20.0 + 5 * (h.lvl - 1)) - 1) / 0.5));
		c.str = new int[c.K]; c.m = new double[c.K];
		double r = rate();
		for (int k = 0; k < c.K; k++) { c.str[k] = h.STR() + (int) Math.round(POSR * reg(c.d0, k)); c.m[k] = r * reg(c.d0, k); }
		c.aSlot = a != null && upgradable(a);
		c.wSlot = w != null && upgradable(w) && (w instanceof MeleeWeapon);
		c.shA = c.aSlot ? (c.wSlot ? 1 - SHARE : 1) : 0; c.shW = c.wSlot ? (c.aSlot ? SHARE : 1) : 0;
		c.cA = a; c.cW = w;
		ctx = c;
		// the base trajectory: the worn pieces, their share of the scrolls to come
		c.armB = new Armor[c.K]; c.armL = new int[c.K]; c.wpnB = new KindOfWeapon[c.K]; c.wpnL = new int[c.K];
		for (int k = 0; k < c.K; k++) {
			c.armB[k] = a; c.armL[k] = a == null ? 0 : (int) Math.round(HeroView.visLevel(a) + (c.aSlot ? c.shA * (Math.max(0, kh - 1) + c.m[k]) : 0));
			c.wpnB[k] = w; c.wpnL[k] = w == null ? 0 : (int) Math.round(HeroView.visLevel(w) + (c.wSlot ? c.shW * (Math.max(0, kh - 1) + c.m[k]) : 0));
		}
		c.base = new double[c.K];
		StringBuilder db = DBG ? new StringBuilder("    UPLAN base d" + c.d0 + " load/turns") : null;
		for (int k = 0; k < c.K; k++) { c.memo.clear(); c.base[k] = load(c, k, c.armB[k], c.armL[k], c.wpnB[k], c.wpnL[k]); if (db != null) db.append(String.format(Locale.ROOT, " %.2f/%.0f", c.base[k], lastTurns)); }
		c.memo.clear();
		if (db != null) System.out.println(db);
		// the committed pieces (which one the scrolls go into), then the base trajectory again with them
		if (c.aSlot || a == null) c.cA = PCOMMIT || armourCommit == null ? commit(c, true, oldA) : armourCommit.apply(h);
		if (c.wSlot) c.cW = WPLAN ? commit(c, false, oldW) : w;
		for (int k = 0; k < c.K; k++) {
			if (c.cA != null && c.cA != a) { int l = (int) Math.round(lv(c.cA) + c.shA * (Math.max(0, kh - 1) + c.m[k])); if (a == null || ls(c, k, (Armor) c.cA, l, c.wpnB[k], c.wpnL[k]) > ls(c, k, a, c.armL[k], c.wpnB[k], c.wpnL[k])) { c.armB[k] = (Armor) c.cA; c.armL[k] = l; } }
			if (c.cW != null && c.cW != w) { int l = (int) Math.round(lv(c.cW) + c.shW * (Math.max(0, kh - 1) + c.m[k])); if (w == null || ls(c, k, c.armB[k], c.armL[k], (KindOfWeapon) c.cW, l) > ls(c, k, c.armB[k], c.armL[k], w, c.wpnL[k])) { c.wpnB[k] = (KindOfWeapon) c.cW; c.wpnL[k] = l; } }
		}
		return c;
	}

	// ---- a floor's log-survival with this gear (RunProjection's model, the hero k floors on) ----
	/** A floor's load with this gear: its fights' (cost / health)^2 (a boss's BOSSK of it, plus 0.45), and the turns
	 *  they take (TIME a thousand: the food and the rest, the others who join a long fight). */
	static double load(Ctx c, int k, Armor a, int al, KindOfWeapon w, int wl) {
		long key = ((((long) k * 64 + id(c, a)) * 256 + al + 64) * 64 + id(c, w)) * 256 + wl + 64;
		Double v = c.memo.get(key); if (v != null) return v;
		Hero h = c.h; int d = Math.min(26, c.d0 + k);
		double load, turns = 0;
		try {
			Fighter me = HeroView.of(h, w, a, c.str[k], wl, al, k, Ench.of(w), Ench.of(a));
			me.hp = me.ht; me.heals = 0;
			double zap = (w instanceof MagesStaff ? UpgradePlan.zap((MagesStaff) w, wl) : 0) + GearValue.prep(me, h, w, wl, a, c.str[k] - h.STR());
			load = 0;
			if (d % 5 == 0 && d <= 25) {
				Fighter b = MobTable.fighter(MobTable.boss(d));
				if (b != null) { if (zap > 0) b.hp = Math.max(1, (int) (b.hp - 3 * zap)); double x = FightModel.duelCost(me, b) / me.ht; load += SQ ? 0.45 + BOSSK * x * x : BOSSK * Math.min(x, 5); }
			} else {
				String[] ks = MobTable.kinds(d); int nf = 6 + d % 5;
				for (String kd : ks) {
					Fighter f = MobTable.fighter(kd); if (f == null) continue;
					if (zap > 0) f.hp = Math.max(1, (int) (f.hp - zap));
					double x = FightModel.duelCost(me, f) / me.ht;
					load += (double) nf / ks.length * (SQ ? x * x : Math.min(x, 3));
					double tt = nf / (double) ks.length * Math.min(200, (f.hp + f.shield) / Math.max(0.05, FightModel.dps(me, f)));
					turns += tt; load += TIME * tt;
				}
			}
		} catch (Throwable e) { load = 1; }
		load = Math.min(load, 20);
		c.memo.put(key, load); lastTurns = turns;
		return load;
	}
	/** A floor's log-survival with this gear: anchored (ANCHOR) on the floor's death rate as the bot plays it now (SEMP,
	 *  honest games), scaled by the gear's load against the base trajectory's: h = h_emp x (load / base)^GAM (a fight
	 *  that costs half the health is a quarter of the load, and about a quarter of the risk); else the model's own. */
	static double ls(Ctx c, int k, Armor a, int al, KindOfWeapon w, int wl) {
		double L = load(c, k, a, al, w, wl);
		if (!ANCHOR || c.base == null) return -(HZ0 + HZK * L);
		int d = Math.max(1, Math.min(25, c.d0 + k));
		double h0 = (1 - c.strong) * (1 - SEMP[d - 1]) + c.strong * (1 - SMILD[d - 1]);
		double hz = h0 * Math.pow((L + D0) / (c.base[k] + D0), GAM);
		return Math.log(1 - Math.min(0.9, hz));
	}
	static final boolean ANCHOR = Tune.on("UPANCHOR", true), SQ = Tune.on("UPSQ", false);
	static final double D0 = Tune.get("UPD0", 20) / 100;   // (the damping: a floor's health lost, in whole bars, added to both sides)
	static double lastTurns;
	static final double GAM = Tune.get("UPGAM", 100) / 100, BOSSK = Tune.get("UPBOSSK", 100) / 100, TIME = Tune.get("UPTIME", 0) / 1000;
	/** Each floor's survival as the bot plays now (MD-base2 and HE-final honest games, 192; the deep floors from the full
	 *  games, a little lower): the anchor. */
	/** The same with half again the health (the MILD training set-up: UP-a1, MD-base2, HE-final, MD-fin1 full games, 768):
	 *  the hero's health against a plain hero's of his level says which applies (strong 0..1), nothing hidden. */
	static final double[] SMILD = { 0.99, 0.99, 0.99, 0.99, 0.98, 0.99, 0.98, 0.97, 0.97, 0.98,
			0.98, 0.96, 0.90, 0.87, 0.95, 0.88, 0.81, 0.86, 0.81, 0.65, 0.81, 0.72, 0.55, 0.60, 0.55 };
	static final double[] SEMP = { 0.96, 0.91, 0.91, 0.91, 0.81, 0.92, 0.85, 0.84, 0.85, 0.86,
			0.79, 0.65, 0.55, 0.56, 0.60, 0.70, 0.62, 0.68, 0.62, 0.50, 0.65, 0.55, 0.40, 0.45, 0.40 };
	/** The armour slot's floor value: armour a at level l (between whole levels: the mix), the weapon on its base. */
	static double lsA(Ctx c, int k, Armor a, double l) {
		int f = (int) Math.floor(l); double x = l - f;
		double v = ls(c, k, a, f, c.wpnB[k], c.wpnL[k]);
		return x < 1e-6 ? v : v * (1 - x) + x * ls(c, k, a, f + 1, c.wpnB[k], c.wpnL[k]);
	}
	static double lsW(Ctx c, int k, KindOfWeapon w, double l) {
		int f = (int) Math.floor(l); double x = l - f;
		double v = ls(c, k, c.armB[k], c.armL[k], w, f);
		return x < 1e-6 ? v : v * (1 - x) + x * ls(c, k, c.armB[k], c.armL[k], w, f + 1);
	}
	static double lsS(Ctx c, boolean arm, int k, Item p, double l) { return arm ? lsA(c, k, (Armor) p, l) : lsW(c, k, (KindOfWeapon) p, l); }

	/** The expected floors from floor k0 on (alive at its start), floor values v[k0..K). The current floor counts half. */
	static double floors(double[] v, int k0) {
		double cum = 0, e = 0;
		for (int k = k0; k < v.length; k++) { cum += (k == 0 ? 0.5 : 1) * v[k]; e += Math.exp(cum); }
		return e;
	}
	static double alive(double[] v, int k1) { double cum = 0; for (int k = 0; k < k1; k++) cum += (k == 0 ? 0.5 : 1) * v[k]; return Math.exp(cum); }

	/** A slot's floors with piece p getting the scrolls (extra: in hand now, beyond its share), worn while it beats the worn one. */
	static double[] seq(Ctx c, boolean arm, Item p, double extra) {
		Item worn = arm ? c.a0 : c.w0; double sh = arm ? c.shA : c.shW;
		double[] v = new double[c.K];
		for (int k = 0; k < c.K; k++) {
			double l = lv(p) + sh * (Math.max(0, c.kh - 1) + c.m[k]) + extra;
			double x = lsS(c, arm, k, p, l);
			if (p != worn && worn != null) x = Math.max(x, lsS(c, arm, k, worn, HeroView.visLevel(worn)));
			v[k] = x;
		}
		return v;
	}

	/** The piece the slot's scrolls go into: the worn one or one carried, the one the run lives longest with. */
	static Item commit(Ctx c, boolean arm, Item old) {
		Item worn = arm ? c.a0 : c.w0, best = worn; double bv = -1, ov = -1;
		if (worn instanceof MagesStaff || worn instanceof ClassArmor) return worn;   // (the Mage's staff, the class armour: the scrolls stay with them)
		List<Item> cs = new ArrayList<>(); if (worn != null && upgradable(worn)) cs.add(worn);
		for (Item i : c.h.belongings.backpack) if (arm ? usableArm(c.h, i) : usableWpn(c.h, i)) cs.add(i);
		StringBuilder b = DBG ? new StringBuilder("    UPLAN commit " + (arm ? "armour" : "weapon")) : null;
		for (Item i : cs) {
			double v = floors(seq(c, arm, i, c.kh > 0 ? 1 : 0), 0);
			if (b != null) b.append(String.format(Locale.ROOT, " %s%+.0f %.3f", i.getClass().getSimpleName(), lv(i), v));
			if (i == old) ov = v;
			if (v > bv) { bv = v; best = i; }
		}
		if (old != null && ov >= 0 && best != old && bv < ov + CHYST) best = old;
		if (b != null) System.out.println(b + " -> " + (best == null ? "-" : best.getClass().getSimpleName()));
		return best;
	}

	// ---- the scroll: now into a slot, or held ----
	static final class Val { double now, hold; int holdW, holdT; double holdP; }
	/** The slot's options: the marginal scroll into its committed piece now, or held W floors (1..WMAX) for a better piece. */
	static Val value(Ctx c, boolean arm) {
		Item p = arm ? c.cA : c.cW; Val r = new Val(); r.now = r.hold = -1;
		if (p == null || !upgradable(p)) return r;
		int tp = p instanceof Armor ? ((Armor) p).tier : p instanceof MeleeWeapon ? ((MeleeWeapon) p).tier : 9;
		boolean arrivals = !(p instanceof ClassArmor) && !(p instanceof MagesStaff) && tp < 5;
		double sh = arm ? c.shA : c.shW;
		// the arrival scenarios: the first floor a better piece turns up, and its tier
		List<double[]> scn = new ArrayList<>(); double none = 1;
		if (arrivals) for (int k = 0; k < c.K; k++) {
			int f = c.d0 + k; double half = k == 0 ? 0.5 : 1, pAny = pFloor(arm, f, tp + 1) * half;
			if (pAny <= 0) continue;
			for (int T = tp + 1; T <= 5; T++) { double pt = (pFloor(arm, f, T) - pFloor(arm, f, T + 1)) * half; if (pt > 0) scn.add(new double[]{ k, T, none * pt }); }
			none *= 1 - pAny;
		}
		// the Warrior's seal takes the first upgrade of the armour it is on and carries it to the next (Armor.upgrade,
		// BrokenSeal.affixToArmor): that scroll isn't lost when the armour changes
		boolean seal = arm && p == c.a0 && ((Armor) p).checkSeal() != null && ((Armor) p).checkSeal().level() == 0;
		// (a hold is a plan kept: its deadline, set when it began, isn't pushed back floor after floor; none with a boss next)
		int Wn = HOLD && arrivals && !bossNear(c.d0) ? (holdUntil >= 0 && holdHero == c.h ? Math.min(WMAX, holdUntil - c.d0) : WMAX) : 0;
		for (int W = 0; W <= Wn && W < c.K; W++) {   // (W 0: now)
			double e = 0;
			double[] base = new double[c.K];
			for (int k = 0; k < c.K; k++) base[k] = lsP(c, arm, p, k, sh, k >= W ? 1 : 0);
			e += none * floors(base, 0);
			for (double[] s : scn) {
				int D = (int) s[0], T = (int) s[1]; boolean held = D < W;
				Item nw = arm ? TA[T] : TW[T];
				double pre = 0, cum = 0; for (int k = 0; k < D; k++) { cum += (k == 0 ? 0.5 : 1) * base[k]; pre += Math.exp(cum); }
				double alive = Math.exp(cum);
				// keep on with the old piece (the scroll in it), or take up the new one (the scroll in it if still held)
				double[] old = new double[c.K], neu = new double[c.K];
				double lD = lv(p) + sh * (Math.max(0, c.kh - 1) + c.m[D]);
				for (int k = D; k < c.K; k++) {
					old[k] = lsP(c, arm, p, k, sh, 1);
					double nl = (held || seal ? 1 : 0) + sh * (c.m[k] - c.m[D]);
					neu[k] = Math.max(lsS(c, arm, k, p, lD + (held ? 0 : 1)), lsS(c, arm, k, nw, nl));
				}
				e += s[2] * (pre + alive * Math.max(floors(old, D), floors(neu, D)));
			}
			if (W == 0) r.now = e;
			else if (e > r.hold) { r.hold = e; r.holdW = W; }
		}
		if (r.holdW > 0) {   // the why: the tier most likely to make the hold pay, and its odds in the window
			int bt = tp + 1; double bp = 0;
			for (int T = tp + 1; T <= 5; T++) { double q = pBy(arm, c.d0, c.d0 + r.holdW - 1 + 1, T); if (q * T > bp * bt || bp == 0) { bp = q; bt = T; } }
			r.holdT = bt; r.holdP = pBy(arm, c.d0, c.d0 + r.holdW, bt);
		}
		return r;
	}
	/** The committed piece's floor value at floor k, its share of the scrolls, plus (marginal) the scroll weighed. */
	static double lsP(Ctx c, boolean arm, Item p, int k, double sh, int marginal) {
		Item worn = arm ? c.a0 : c.w0;
		double x = lsS(c, arm, k, p, lv(p) + sh * (Math.max(0, c.kh - 1) + c.m[k]) + marginal);
		if (p != worn && worn != null) x = Math.max(x, lsS(c, arm, k, worn, HeroView.visLevel(worn)));
		return x;
	}

	public static String why = "", last = "";
	static Item lastT; static long lastKey;

	/** Where a Scroll of Upgrade read now should go: a piece, or null to hold it (why says which and why). */
	public static Item target(Hero h) {
		Ctx c = context(h);
		if (lastKey == c.key && c.memo.size() > 0 && lastWhy != null) { why = lastWhy; return lastT; }
		Val va = c.aSlot || (c.cA != null && c.cA != c.a0) ? value(c, true) : new Val(), vw = c.wSlot ? value(c, false) : new Val();
		if (va.now < 0 && va.hold < 0 && vw.now < 0 && vw.hold < 0) { lastKey = c.key; lastWhy = why = "upgrade"; return lastT = c.a0 != null ? c.a0 : c.w0; }
		boolean aBest = va.now >= vw.now;
		double now = Math.max(va.now, vw.now);
		Val vh = va.hold >= vw.hold ? va : vw; boolean hArm = vh == va;
		Item t;
		String nm = aBest ? "the armour" : "the weapon";
		Item p = aBest ? c.cA : c.cW;
		if (HOLD && vh.hold > now + HOLDM && c.d0 < 25 && c.d0 >= HOLDMIN && !Budget.climbing(h) && vh.holdW > 0) {   // (the climb: nothing new is found on the way up)
			t = null;
			if (holdHero != h || holdUntil < 0) { holdHero = h; holdUntil = c.d0 + vh.holdW; }
			why = String.format(Locale.ROOT, "holding the upgrade: %s likely by floor %d (%.0f%%)", tierName(hArm, vh.holdT), c.d0 + vh.holdW, 100 * vh.holdP);
		} else {
			t = p;
			if (holdHero == h) holdUntil = -1;   // (the hold over: the scroll spent)
			why = String.format(Locale.ROOT, "upgrade %s%s (%s next best by %.2f floors)", nm, p != null && !p.isEquipped(h) ? " (" + p.name() + ", to wear)" : "", (aBest ? (vw.now >= 0 ? "the weapon" : "holding") : "the armour"), now - Math.max(aBest ? vw.now : va.now, vh.hold));
		}
		last = String.format(Locale.ROOT, "d%d kh %d rate %.2f arm %s now %.3f hold %.3f(W%d) | wpn %s now %.3f hold %.3f(W%d) -> %s", c.d0, c.kh, rate(),
				c.cA == null ? "-" : c.cA.getClass().getSimpleName() + HeroView.visLevel(c.cA), va.now, va.hold, va.holdW,
				c.cW == null ? "-" : c.cW.getClass().getSimpleName() + HeroView.visLevel(c.cW), vw.now, vw.hold, vw.holdW, t == null ? "hold" : t.getClass().getSimpleName());
		if (DBG) { StringBuilder b = new StringBuilder(" | s"); double[] v = c.cA != null ? seq(c, true, c.cA, 0) : c.cW != null ? seq(c, false, c.cW, 0) : new double[0]; for (double x : v) b.append(String.format(Locale.ROOT, " %.2f", Math.exp(x))); System.out.println("    UPLAN " + last + " | " + why + b); }
		lastKey = c.key; lastT = t; lastWhy = why;
		return t;
	}
	static String lastWhy;
	static int holdUntil = -1; static Hero holdHero;
	/** The boss's floor or the one before it: every boss guide says arrive with the upgrades read. */
	static boolean bossNear(int d) { return d % 5 == 4 || d % 5 == 0; }
	static String tierName(boolean arm, int t) {
		String[] a = { "", "cloth", "leather", "mail", "scale", "plate" }, w = { "", "a tier-1 weapon", "a tier-2 weapon", "a tier-3 weapon", "a tier-4 weapon", "a tier-5 weapon" };
		return (arm ? a : w)[Math.max(0, Math.min(5, t))] + (t < 5 ? " or better" : "");
	}

	/** The armour the scrolls are for (worn or carried). */
	public static Armor commitArmor(Hero h) { Ctx c = context(h); return c.cA instanceof Armor ? (Armor) c.cA : h.belongings.armor(); }
	/** The weapon the scrolls are for. */
	public static Item commitWeapon(Hero h) { Ctx c = context(h); return c.cW != null ? c.cW : h.belongings.weapon(); }
	/** The floors the run is worth with the slot's piece p worn as it is now against the worn one, this floor only (wear it?). */
	public static boolean better(Hero h, Item p) {
		Ctx c = context(h); boolean arm = p instanceof Armor; Item worn = arm ? c.a0 : c.w0;
		if (worn == null) return true;
		double a = lsS(c, arm, 0, p, HeroView.visLevel(p) - (p.levelKnown ? 0 : UNK)), b = lsS(c, arm, 0, worn, HeroView.visLevel(worn));
		return a > b + 0.002;
	}
}
