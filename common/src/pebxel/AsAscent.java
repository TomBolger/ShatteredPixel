package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.AscensionChallenge;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.NPC;
import com.shatteredpixel.shatteredpixeldungeon.items.Amulet;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfMagicMapping;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger;
import java.util.*;
import pebxel.Assist.Target;
import pebxel.bot.*;
import static pebxel.Assist.*;

/**
 * The climb back with the Amulet (agents K7, AS2; strategy-v4 3.x, AscensionChallenge). Taking Yog's stairs up with
 * the Amulet starts the ascension: every floor climbed into adds 2 to the curse (boss floors: none, and they heal in
 * full and feed), every empowered foe killed takes 1 off (a ghoul or ripper demon a half), and as it grows: 2+ every
 * foe on the floor is called to the hero (they all come), 4+ those not yet hunting move twice as fast, 6+ the hero walks
 * at half speed, 8+ he loses (curse - 4) / 4 health a turn off the boss floors. Each floor climbed into is emptied and
 * filled again from one foe, respawning every 5 to 25 turns. Foes are empowered by kind (rats x10 down to scorpios x1.1).
 * A floor gone back down to adds nothing (only the first arrival counts), and nothing follows the hero down.
 *
 * So, a floor at a time (strategy-v4 3.3):
 *  - the way up is the known one: no exploring, no item hunting; what comes is fought;
 *  - "curse 1 or less, then go": at the stairs up the hero holds (searching, the stairs a step away) while the curse is
 *    above it: the foes the curse calls come to him there, and each kill is one off (racing on adds 2 a floor: slowed
 *    by 22, burning by 21). Not on floor 1 (out, no fighting), not past the food the floors to the next boss floor need,
 *    not for ever when nothing comes;
 *  - a fight the lookahead says is lost: up the stairs (a new floor emptied as it's climbed into, the fight left behind)
 *    or down them (a floor climbed adds nothing; coming back, the fight met again: Healing drunk in peace below);
 *  - boss floors are the rest stops: full health, the wands charged; the Imp's shop on 20 is the climb's only shop;
 *  - bombs on the foes coming (30-90 a blast at the climb's depth 26, past only their own armour, not the multiplied);
 *  - every trap is a depth-26 trap: none walked over with a foe about or hurt, the deadly ones not at all;
 *  - before the climb (depth 26 / 25 with the Amulet): eaten.
 *
 * The curse's count: the buff's icon shows it in steps of 2 (its colour) and its description its effects, and a
 * player can count it exactly (+2 a floor, -1 a kill, the "curse weakens" messages): read from the buff.
 * AS_ASCENT=0 turns it off.
 */
final class AsAscent {
	private AsAscent() {}

	static final boolean ON = opt("ASCENT", true);
	static final int PICK = optInt("ASCPICK", 3);         // steps off the way an item is worth (Healing, food when short: twice that)
	static final int RESTAT = optInt("ASCREST", 60);      // the stairs up reached below this % of health, the curse harmless: rest first
	static final double SAFE = optInt("ASCSAFE", 3) / 100.0;   // a fight's death odds low enough to take on for the kill (the curse near its harm)
	static final int LEAVE = optInt("ASCLEAVE", 99);      // leave a floor with the curse at most this (the next a boss floor: one more); 99: no hold (AS2-b/c: holding at 1, 2, 3 won 20, 22, 23 of 48 from 26, not holding 26)
	static final int HOLD = optInt("ASCHOLD", 40);        // turns held at the stairs up with no foe seen before going on all the same
	static final int FLOORT = optInt("ASCFLOORT", 90);    // turns a floor's climb takes (the food the hold leaves for the floors to come)
	static final int UPW = optInt("ASCUPW", 3), DOWNW = optInt("ASCDOWNW", 2);   // steps to the stairs a fight may be left by, up / down
	static final boolean DOWNESC = opt("ASCDOWN", true), BOMBS = opt("ASCBOMB", true), TRAPS = opt("ASCTRAPS", true), SHOP = opt("ASCSHOP", true);
	static final int BOMBD = optInt("ASCBOMBD", 14);      // bombs kept for this depth and below (above: only for a fight the lookahead fears)
	static final int BOSSREST = optInt("ASCBOSSREST", 100);   // turns on a boss floor waiting for the wands' charges
	static final int PREPFOOD = optInt("ASCPREPFOOD", 150);   // hunger at which the hero eats before the climb (with food to spare)
	static final int KEEP = optInt("ASCKEEP", 0);         // Healing kept back for the Caves and below while above them (0: none)
	static final int DRINK = optInt("ASCDRINK", 33);      // % of health below which a known Healing is drunk in a climb fight

	/** Holding the Amulet: the way is up. */
	static boolean on() { Hero h = Dungeon.hero; return ON && h != null && h.belongings.getItem(Amulet.class) != null; }
	/** The climb begun (the curse on the hero). */
	static boolean begun() { Hero h = Dungeon.hero; return h != null && h.buff(AscensionChallenge.class) != null; }
	/** The curse's count (0 before Yog's stairs). */
	static float stacks() {
		Hero h = Dungeon.hero; AscensionChallenge a = h == null ? null : h.buff(AscensionChallenge.class);
		Object s = a == null ? null : field(a, "stacks");
		return s instanceof Float ? (Float) s : 0;
	}
	/** The curse harms the hero here (8+, off the boss floors). */
	static boolean harm() { return stacks() >= 8 && !Dungeon.bossLevel(); }
	/** The curse a floor may be left with: LEAVE, one more when the next floor up is a boss floor (it adds none). */
	static float leaveAt(int d) { return LEAVE + (Dungeon.bossLevel(d - 1) ? 1 : 0); }

	// per floor: arrival, the last foe seen, the curse when last here (the curse it was left with)
	static int floorD = -1, fledDown = -1; static float floorT0, seenT, holdT = -1;
	static final Map<Integer, Float> left = new TreeMap<>(Comparator.reverseOrder());
	static void observe() {
		int d = Dungeon.depth; float now = Actor.now();
		if (d != floorD) {
			if (floorD > 0 && d == floorD + 1 && begun()) fledDown = floorD;   // (down a floor on the climb: out of a fight, or a pitfall)
			else if (d < floorD) fledDown = -1;
			floorD = d; floorT0 = now; seenT = now; holdT = -1;
		}
		if (seenFoe || threatNear) seenT = now;
		if (begun()) left.put(d, stacks());
	}

	// stats (per game)
	static final Map<String, Integer> why = new TreeMap<>();
	static Target note(Target t) { if (t != null) why.merge(t.why.replaceAll(" \\(.*", ""), 1, Integer::sum); return t; }

	/** The hook (Assist.planList's skills, after the hands): the climb's own move, or null (the usual rules: a fight). */
	static Target suggest(Target first) {
		if (!on()) return null;
		try { observe(); return note(climb(first)); } catch (Throwable e) { lastError = e; return null; }
	}

	static Target climb(Target first) {
		Hero h = Dungeon.hero; int hp = h.pos, ent = L.entrance();
		float st = stacks();
		if (h.rooted) return null;   // (held fast: the usual rules, a fight or a wait)
		// in fire or gas (or burning): out of it first, by the escape rule; the stairs a step off and clear of it are
		// the way out (K8: "ascent: up" and "escape the fire" took turns on 21, no time passing)
		if (inDanger || burning()) {
			int e0 = L.entrance();
			if (e0 >= 0 && e0 < L.length() && known[e0] && !danger[e0] && seenChar(e0) == null && (e0 == hp || walk(e0) <= 1) && Dungeon.depth > 1)
				return new Target(e0, e0 == hp ? PICKUP : GO, "ascent: up, out of the fire");
			return null;
		}
		if (!begun()) { Target t = prep(); if (t != null) return t; }
		boolean entKnown = ent >= 0 && ent < L.length() && known[ent] && walk(ent) < INF;
		if (entKnown && ent != hp && seenChar(ent) != null) return null;   // (someone on the stairs: the usual rules; K7-d, a succubus there, "up" for ever)
		int w = entKnown ? (ent == hp ? 0 : walk(ent)) : INF;
		double hs = h.speed();
		// the foes in sight: one beside the hero is fought (or left by the stairs beside him); one that will reach him
		// before the stairs, or blocks the way, too; a shooter in line is the tactics' (out of its line, or at it)
		boolean adj = false, catches = false, shooter = false, coming = false; Mob near = null;
		for (Mob m : mobs()) {
			if (!enemy(m) || m.state == m.PASSIVE || m instanceof NPC || h.isCharmedBy(m)) continue;
			int d = distance(m.pos, hp);
			if (d <= 1) adj = true;
			if (near == null || d < distance(near.pos, hp)) near = m;
			if (m.state == m.SLEEPING) continue;
			if (hunting(m) || d <= 8) coming = true;
			boolean shoots = FoeView.shooter(m) && (line(m.pos, hp) || m instanceof com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Eye && sight(m.pos, hp));
			if (shoots && d <= 8 && !(m instanceof com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Scorpio)) shooter = true;   // (a scorpio never comes: below, out of its line on the way on)
			if (m instanceof com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Scorpio) continue;
			double ms = m.speed();
			if (entKnown && distance(m.pos, ent) < w && d <= w + 2) catches = true;   // (nearer the stairs than the hero: in the way)
			else if (hunting(m) && ms > hs * 1.01 && (d - 1) / Math.max(1e-3, ms - hs) < w / hs) catches = true;   // (one no faster, behind: never catches a hero walking on; left on the floor below, a kill the fewer)
		}
		// a bomb on one coming (before it's beside the hero: the blast must miss him)
		if (!adj && (catches || coming || holding(st))) { Target b = bomb(); if (b != null) return b; }
		if (adj || catches || shooter) return null;   // (a fight: the usual rules; leaving it by the stairs is Rules' row, by the lookahead)
		// a scorpio (it backs off, spitting while it has a line): out of every line, on the way to the stairs
		if (entKnown) { Target t = past(hp, ent); if (t != null) return t; }
		// the curse near its harm and a safe fight in sight, off the way only a little: taken for the kill
		if (near != null && st + 2 >= 8 && Brain.fight && Brain.base != null && Brain.base.pDeath < SAFE && distance(near.pos, hp) <= 4 && near.state != near.SLEEPING) return null;
		// a boss floor: no harm, no foes, healing in full: rest to full first, and for the wands (the next four floors are the curse's)
		if (Dungeon.bossLevel() && calm() && !starving() && !declined(null, REST_ACT)) {
			if (h.HP * 100 < h.HT * 95) return rest("ascent: rest on the boss floor");
			if (!hungry() && Actor.now() - floorT0 < BOSSREST && wandsLow()) return rest("ascent: rest on the boss floor (the wands)");
		}
		// the shop (the Imp's on 20: the only one open on the climb)
		if (SHOP) { Target t = shop(); if (t != null) return t; }
		// down a floor out of a fight: Healing drunk here in peace, then back up
		if (fledDown == Dungeon.depth - 1 && calm() && h.HP * 2 < h.HT) {
			com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing ph = h.belongings.getItem(com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing.class);
			if (ph != null && ph.isKnown() && !declined(ph.getClass(), com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion.AC_DRINK) && h.buff(com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Healing.class) == null)
				return use(ph, com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion.AC_DRINK, -1, "ascent: drink below, then back up");
		}
		// something worth a step or two off the way
		Target p = pick(hp, w);
		if (p != null) return p;
		if (!entKnown && ent >= 0 && ent < L.length() && known[ent]) return null;   // (the stairs seen, the way there shut for now: a foe in a corridor, a door: the usual rules; exploring away from them flip-flopped with the way up, K7)
		if (!entKnown) return explore(hp);
		// the curse above the leave mark: hold a spot by the stairs up for the foes it calls (each kill one off); hurt
		// with the curse harmless and the next floor no boss's: rest there first (the foes that come are kills). (Walking
		// onto the stairs takes them: the spot is beside them, a narrow one if there is one, the way out a step or two.)
		boolean hold = holding(st), rest = !hold && st < 8 && Dungeon.depth > 1 && !Dungeon.bossLevel(Dungeon.depth - 1) && h.HP * 100 < h.HT * RESTAT && calm() && !hungry() && foodSpare() > HOLD && !declined(null, REST_ACT);
		if (hold || rest) {
			int hc = holdCell(ent);
			if (hc >= 0 && hc != hp) return new Target(hc, GO, hold ? "ascent: to the stairs, to hold them" : "ascent: to the stairs, to rest");
			if (hc == hp) {
				if (holdT < 0) holdT = Actor.now();
				if (rest) return rest("ascent: rest by the stairs");
				Target t = new Target(hp, WAIT, "ascent: hold the stairs (curse " + st + ")"); t.action = SEARCH_ACT; t.obj = -1;
				return t;
			}
		}
		return new Target(ent, w == 0 ? PICKUP : GO, "ascent: up");
	}

	/** Hold the stairs up for kills: the curse above the leave mark, a floor of the curse's, food to spare for it, and
	 *  something seen lately (HOLD turns with nothing seen: nothing is coming). */
	static boolean holding(float st) {
		if (Dungeon.bossLevel() || Dungeon.depth <= 1 || st <= leaveAt(Dungeon.depth) || starving()) return false;
		if (holdT >= 0 && Actor.now() - Math.max(holdT, seenT) > HOLD) return false;
		return foodSpare() > HOLD;
	}
	/** The spot to hold beside the stairs up: within 2 steps of them, a narrow one first (fewest open cells about: a
	 *  door, a corridor), nearer the stairs, nearer the hero; never a trap, never in danger. */
	static int holdCell(int ent) {
		int[] sd = fromStairs(ent); int best = -1; double bk = Double.MAX_VALUE; int hp = Dungeon.hero.pos;
		for (int c = 0; c < L.length(); c++) {
			if (c == ent || sd[c] > 2 || sd[c] < 1 || !(c == hp || AsBolts.free(c)) || danger[c] || downAt(c) || L.getTransition(c) != null) continue;
			int o = 0; for (int k = 0; k < 8; k++) { int x = c + N8[k]; if (nb(c, x) && open[x]) o++; }
			double k = o * 3 + sd[c] * 2 + Math.min(20, walk(c)) * 0.2 - (c == hp ? 1 : 0);
			if (k < bk) { bk = k; best = c; }
		}
		return best;
	}
	/** Turns of food beyond what the floors to the next boss floor need (FLOORT each: this one's walk on included). */
	static int foodSpare() {
		int d = Dungeon.depth, floors = 0;
		for (int k = d; k > 1 && !Dungeon.bossLevel(k); k--) floors++;
		int have = (int) Hunger.STARVING - hunger() + foodLeft() * (int) Hunger.HUNGRY;
		return have - floors * FLOORT;
	}
	/** A known wand short of its charges. */
	static boolean wandsLow() {
		for (Item i : Dungeon.hero.belongings) if (i instanceof com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand) {
			com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand wd = (com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand) i;
			if (wd.curChargeKnown && wd.curCharges < wd.maxCharges) return true;
		}
		return false;
	}

	/** Before the climb (the Amulet held, the curse not begun: depth 26 and 25): eaten (strategy-v4 3.3 rule 1, 9: the
	 *  first four floors run on what's eaten here), with food to spare for later. */
	static Target prep() {
		if (!calm() || hunger() < PREPFOOD || foodLeft() < 2) return null;
		Item f = null;
		for (Item i : Dungeon.hero.belongings.backpack) if (foodItem(i) && (f == null || i instanceof Food && ((Food) i).energy > ((Food) f).energy)) f = i;
		if (f == null || declined(f.getClass(), Food.AC_EAT)) return null;
		return use(f, Food.AC_EAT, -1, "ascent: eat before the climb");
	}

	/** The shop's wares wanted and affordable (Trade's ranking, as on the way down). */
	static Target shop() {
		int sb = -1, sk = INF; Hero h = Dungeon.hero;
		for (Heap w : L.heaps.valueList()) {
			if (w.type != Heap.Type.FOR_SALE || !w.seen || w.pos == h.pos || seenChar(w.pos) != null || bad.containsKey(w.pos) || skippedHeaps.contains(w.pos)) continue;
			int d = walk(w.pos);
			if (d >= INF || !roomFor(w.peek())) continue;
			int pr = Trade.buyRank(h, w, L.heaps.valueList());
			if (pr <= 0) continue;
			int sc = d - pr * 8;
			if (sc < sk) { sk = sc; sb = w.pos; }
		}
		return sb < 0 ? null : new Target(sb, PICKUP, "ascent: the shop");
	}

	// =============================================================================================================
	// bombs on the foes the curse calls (strategy-v4 3.3 rule 7: 30-90 a blast at the climb's depth 26)
	// =============================================================================================================
	static boolean blastBomb(Item i) {
		Class<?> c = i.getClass();
		return c == com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb.class || i instanceof com.shatteredpixel.shatteredpixeldungeon.items.bombs.FrostBomb || i instanceof com.shatteredpixel.shatteredpixeldungeon.items.bombs.HolyBomb
				|| i instanceof com.shatteredpixel.shatteredpixeldungeon.items.bombs.FlashBangBomb || i instanceof com.shatteredpixel.shatteredpixeldungeon.items.bombs.WoollyBomb || i instanceof com.shatteredpixel.shatteredpixeldungeon.items.bombs.SmokeBomb;
	}
	/** A lit bomb thrown where a foe coming will be when it goes off (its 2-turn fuse: two of the foe's steps nearer),
	 *  the blast clear of the hero by a step; the most foes caught, the strongest. Kept for the deep floors (BOMBD and
	 *  below, the multipliers x2 to x10) unless the lookahead fears the fight. */
	static Target bomb() {
		if (!BOMBS || blind()) return null;
		if (Dungeon.depth > BOMBD && !(Brain.fight && Brain.base != null && Brain.base.pDeath > 0.05)) return null;
		Hero h = Dungeon.hero; int hp = h.pos;
		Item best = null; int bc = -1; double bv = 0;
		for (Item i : h.belongings.backpack) {
			if (!blastBomb(i) || declined(i.getClass(), "LIGHTTHROW")) continue;
			int r = AsItems.bombRange(i);
			for (Mob m : mobs()) {
				if (!enemy(m) || m instanceof NPC || m.state == m.SLEEPING || m.state == m.PASSIVE || !hunting(m) || h.isCharmedBy(m)) continue;
				if (AscensionChallenge.statModifier(m) <= 1 || m.HP < 30) continue;
				int d = walk(m.pos);
				if (d >= INF || d <= r + 2) continue;
				int steps = (int) Math.round(2 * Math.max(0.5, m.speed())), dp = d - steps;
				if (dp < r + 2) continue;
				// the cell the foe will be in: on its way to the hero (dp steps from him), the nearest to it now
				int c = -1, ck = INF;
				for (int x = 0; x < L.length(); x++) {
					if (walk(x) != dp || !open[x] || distance(x, hp) < r + 2 || L.pit[x] || seenChar(x) != null || !line(hp, x)) continue;
					int k = distance(x, m.pos);
					if (k <= steps + 1 && k < ck) { ck = k; c = x; }
				}
				if (c < 0) continue;
				double v = 0; boolean ally = false;
				for (Mob o : mobs()) {
					if (distance(o.pos, c) > r + 1) continue;
					if (o.alignment == com.shatteredpixel.shatteredpixeldungeon.actors.Char.Alignment.ALLY) ally = true;
					else if (enemy(o) && AscensionChallenge.statModifier(o) > 1) v += AscensionChallenge.statModifier(o);
				}
				if (ally) continue;
				if (v > bv) { bv = v; bc = c; best = i; }
			}
		}
		return best == null ? null : use(best, "LIGHTTHROW", bc, "ascent: bomb the one coming");
	}

	/** Shooters in sight that won't come (scorpios): a cell out of every shooter's line, a few steps off, the nearest
	 *  the stairs (by the known way), when the hero is in a line or the walk on would take him into one. */
	static Target past(int hp, int ent) {
		List<Mob> sh = AsBolts.shooters(); boolean scorp = false;
		for (Mob m : sh) scorp |= m instanceof com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Scorpio;
		if (!scorp) return null;
		int[] sd = fromStairs(ent);
		boolean now = AsBolts.inLine(sh, hp) > 0;
		int best = -1; double bk = now ? Double.MAX_VALUE : sd[hp] + 0.5;
		for (int c = 0; c < L.length(); c++) {
			if (c == hp || !AsBolts.free(c) || walk(c) > (now ? 5 : 3) || sd[c] >= INF || AsBolts.inLine(sh, c) > 0) continue;
			double k = sd[c] + walk(c) * 0.5;
			if (k < bk) { bk = k; best = c; }
		}
		if (best < 0) return null;
		if (best == ent) return new Target(ent, GO, "ascent: up");
		return AsBolts.tried(new Target(best, GO, now ? "ascent: out of the scorpio's line, on" : "ascent: on, out of the scorpio's line"));
	}
	/** Steps from the stairs up to each cell, over the known way. */
	static int[] fromStairs(int ent) {
		int n = L.length(); int[] d = new int[n]; Arrays.fill(d, INF); d[ent] = 0;
		ArrayDeque<Integer> q = new ArrayDeque<>(); q.add(ent);
		while (!q.isEmpty()) { int c = q.poll(); for (int k = 0; k < 8; k++) { int x = c + N8[k]; if (nb(c, x) && d[x] >= INF && (pass[x] || x == Dungeon.hero.pos)) { d[x] = d[c] + 1; q.add(x); } } }
		return d;
	}

	static boolean calm() { return !seenFoe && !threatNear; }
	static Target rest(String why) { Target t = Rules.restTarget(); t.why = why; return t; }

	// =============================================================================================================
	// out of a fight by the stairs (Rules' rows: the lookahead's say)
	// =============================================================================================================
	/** The best way out of the fight by the stairs this decision (up or down), worked out once a decision. */
	static Target esc; static int escKey = -1;
	static Target escape() {
		int key = Brain.decisions;
		if (key == escKey) return esc;
		escKey = key; esc = null;
		if (!on() || !Brain.fight || Brain.base == null || Brain.me == null || Dungeon.hero.rooted) return null;
		Target up = stairs(L.entrance(), true), dn = DOWNESC ? stairs(L.exit(), false) : null;
		esc = up == null ? dn : dn == null || up.hint >= dn.hint ? up : dn;
		return esc;
	}
	/** Leaving the fight by stairs s (w steps off, none of the foes nearer them), when the lookahead says it beats
	 *  fighting on by the margin. Up: a new floor, emptied as it's climbed into (the fight left for good; a kill or two
	 *  the fewer: each foe left a small price; a boss floor next heals in full). Down: a floor climbed (no curse added),
	 *  the fight met again on coming back: at the health left, or whole for a Healing drunk below in peace. */
	static Target stairs(int s, boolean up) {
		Hero h = Dungeon.hero; int hp = h.pos;
		if (s < 0 || s >= L.length() || !known[s] || bad.containsKey(s)) return null;
		if (up ? Dungeon.depth <= 1 : Dungeon.depth >= 26) return null;   // (floor 1's stairs up: the way out, plan A already)
		int w = s == hp ? 0 : walk(s);
		if (w > (up ? UPW : DOWNW)) return null;
		if (s != hp && seenChar(s) != null) return null;
		for (Mob m : Brain.foes) if (s != hp && distance(m.pos, s) < w) return null;
		Look.Out o = Look.run(Brain.me, Brain.fs, Brain.plan().flee(w), Brain.seed);
		int n = Brain.me.heals;
		double vh = Budget.rescue(h, Math.max(1, n), 1), vh2 = Budget.rescue(h, Math.max(1, n - 1), 1);
		double later;
		if (up) later = Dungeon.bossLevel(Dungeon.depth - 1) ? Brain.HPW : -0.05 * Brain.fs.size();
		else {
			double alive = Math.max(0.05, 1 - o.pDeath);
			Fighter f = Brain.me.copy(); f.hp = (int) Math.max(1, Math.min(f.ht, o.hpLeft / alive)); f.shield = 0;
			List<Fighter> l = new ArrayList<>(); for (Fighter x : Brain.fs) { Fighter y = x.copy(); y.dist = Math.max(y.dist, 2); y.asleep = false; l.add(y); }
			later = Brain.fightV(Look.run(f, l, Brain.plan(), Brain.seed ^ 0x99), n, vh, vh2);
			if (n > 0) later = Math.max(later, Brain.whole(n, vh, vh2) - vh);
			later -= 0.05;
		}
		double v = Brain.gone(o, later, 0) - Brain.fightV(Brain.base, n, vh, vh2);
		if (v <= Brain.MARGIN) return null;
		Target t = new Target(s, w == 0 ? PICKUP : GO, up ? "ascent: up, out of the fight" : "ascent: down, out of the fight");
		t.hint = v;
		return t;
	}

	/** An item a few steps off the way: Healing and food (more when short of it) further. */
	static Target pick(int hp, int w) {
		int best = -1, bk = INF; Hero h = Dungeon.hero;
		boolean hurt = h.HP * 2 < h.HT && (seenFoe || threatNear || stacks() >= 2);
		for (Heap hh : L.heaps.valueList()) {
			if (!heapOk(hh) || seenChar(hh.pos) != null && hh.pos != hp) continue;
			int d = hh.pos == hp ? 0 : walk(hh.pos);
			if (d >= INF) continue;
			Item top = hh.peek();
			boolean dear = hh.type == Heap.Type.HEAP && (top instanceof PotionOfHealing || precious(top) || (top instanceof Food && foodLeft() < 2));
			int ent = L.entrance(), off = w >= INF ? d : d + (known[ent] && walk(ent) < INF ? Math.max(0, distance(hh.pos, ent) - w) : 0);   // (the detour: there, then on to the stairs)
			if (off > (dear ? PICK * 2 : PICK) || hh.type != Heap.Type.HEAP && !dear) continue;
			if (!dear && hurt && d > 0) continue;   // (hurt with foes about: on up, the next floor emptied as it's climbed into)
			if (off < bk) { bk = off; best = hh.pos; }
		}
		return best < 0 ? null : new Target(best, PICKUP, "ascent: on the way");
	}
	static int foodLeft() { int n = 0; for (Item i : Dungeon.hero.belongings.backpack) if (foodItem(i)) n += i.quantity(); return n; }

	/** The stairs up not known: Magic Mapping if known, else the nearest unknown (nothing picked up on the way). */
	static Target explore(int hp) {
		ScrollOfMagicMapping mm = bag(ScrollOfMagicMapping.class);
		if (mm != null && known(mm) && !blind() && !declined(ScrollOfMagicMapping.class, Scroll.AC_READ)) return use(mm, Scroll.AC_READ, -1, "ascent: map the floor");
		int best = -1, bk = INF;
		for (int c = 0; c < L.length(); c++) if (dist[c] < bk && dist[c] > 0 && !bad.containsKey(c) && unseenBy(c)) { bk = dist[c]; best = c; }
		return best < 0 ? null : new Target(best, GO, "ascent: find the stairs");
	}

	// =============================================================================================================
	// Rules' rows (Rules.TABLE)
	// =============================================================================================================
	/** A rest under the curse's harm: it costs more than it heals, and the foes keep coming. */
	static boolean restHarm(Brain.Cand c) { return on() && c.role == Brain.Role.REST && harm(); }
	/** Plan A off the way up (an item far off, a search, a spot to explore) while climbing. */
	static boolean offWay(Brain.Cand c) {
		if (!on() || c.t.why.startsWith("ascent")) return false;
		int ent = L.entrance();
		if (ent < 0 || ent >= L.length() || !known[ent] || walk(ent) >= INF) return false;   // (the way up not found: a door to unlock, a search, may be it)
		String y = c.t.why;
		return y.equals("pick up") || y.equals("explore") || y.equals("search") || y.startsWith("look") || y.equals("unlock / well") || y.equals("dead end: search") || y.equals("talk")
				|| y.startsWith("art: to the lock");   // (AS2: the skeleton key's lock across the floor, 40 decisions on a climbed floor)
	}

	/** The stairs down while climbing (a rule made for the way down: "short of food: on down"): never, but to escape. */
	static boolean down(Brain.Cand c) {
		if (!on() || Dungeon.depth >= 26) return false;
		Target t = c.t; int ex = L.exit();
		if (t.kind != GO && t.kind != PICKUP || t.why.startsWith("escape") || t.why.startsWith("ascent: down")) return false;
		if (ex >= 0 && (t.obj == ex || t.cell == ex)) return true;
		return downAt(t.cell) || downAt(t.obj);   // (a way down of more than one cell: the Dwarf King's floor's whole stairway; K7-d, fleeing fire onto it went back down to 21 for ever)
	}
	static boolean downAt(int c) {
		if (c < 0 || c >= L.length()) return false;
		com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition tr = L.getTransition(c);
		return tr != null && tr.inside(c) && (tr.type == com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition.Type.REGULAR_EXIT || tr.type == com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition.Type.BRANCH_EXIT);
	}

	/** Rules: plan A in a fight, a way out of it by the stairs that the lookahead says is the better. */
	static boolean fightStairs(Brain.Cand c) { return ON && c == Rules.planA && escape() != null && !c.t.why.startsWith("ascent: up, out") && !c.t.why.startsWith("ascent: down, out"); }
	static Target escTarget() { Target t = escape(); if (t != null) note(t); return t; }

	/** Every trap a depth-26 trap on the climb: the way over a known trap only whole (90%+), with no foe about, and never
	 *  over the deadly kinds (the trap's kind shows once it's known). */
	static boolean trap(Brain.Cand c) {
		if (!TRAPS || !on() || !begun() || c != Rules.planA) return false;
		Target t = c.t; if (t.kind != GO && t.kind != PICKUP) return false;
		int cell = t.why.equals("over the trap") || t.why.equals("the key, over the trap") ? t.cell : t.cell >= 0 && t.cell < L.length() && L.traps.get(t.cell) != null && L.traps.get(t.cell).visible ? t.cell : -1;
		if (cell < 0) return false;
		com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap tr = L.traps.get(cell);
		if (tr == null || !tr.visible || !tr.active) return false;
		Hero h = Dungeon.hero;
		if (!calm() || h.HP * 10 < h.HT * 9) return true;
		String n = tr.getClass().getSimpleName();
		return n.equals("GrimTrap") || n.equals("DisintegrationTrap") || n.equals("ExplosiveTrap") || n.equals("RockfallTrap") || n.equals("SummoningTrap") || n.equals("DistortionTrap") || n.equals("WarpingTrap") || n.equals("GuardianTrap") || n.equals("PoisonDartTrap") || n.equals("BurningTrap") || n.equals("BlazingTrap") || n.equals("ShockingTrap") || n.equals("StormTrap") || n.equals("CorrosionTrap") || n.equals("ToxicTrap");
	}

	/** Rules: a climb fight with the hero below DRINK% and a known Healing: drink (the climb's blows are multiplied, x4-5
	 *  in the prison: K7-e, 8 of 22 climb deaths held Healing, the DM-100's bolts killing from 46 health; the models'
	 *  worst round doesn't know the multipliers yet). */
	static boolean drinkLow(Brain.Cand c) {
		Hero h = Dungeon.hero;
		return on() && begun() && c == Rules.planA && c.role != Brain.Role.HEAL && (Brain.fight || seenFoe || threatNear) && Rules.healPot() != null
				&& h.HP * 100 < h.HT * DRINK && h.buff(com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Healing.class) == null;   // (not "declined": the hands' heal passed over in a fight stays declined; AS2-a, deaths with 5-7 Healing)
	}
	static Target drinkTarget() {
		Target t = use(Rules.healPot(), com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion.AC_DRINK, -1, "ascent: drink, the blows multiplied");
		t.hint = Math.max(1, Brain.R) * 0.5;
		return note(t);
	}

	/** Healing kept back for the Caves and below (strategy-v4: most climb deaths there) while above them: the last KEEP
	 *  not drunk on the hands' or brain's word above depth 15 unless the next round could kill (Rules' "drink rather
	 *  than die" stands). */
	static boolean keep(Brain.Cand c) {
		if (KEEP <= 0 || !on() || !begun() || Dungeon.depth <= 15 || c.role != Brain.Role.HEAL || c.t.why.startsWith("rescue")) return false;
		int n = 0; for (Item i : Dungeon.hero.belongings.backpack) if (i instanceof PotionOfHealing && ((PotionOfHealing) i).isKnown()) n += i.quantity();
		return n <= KEEP && Rules.worstRound() < Dungeon.hero.HP;
	}

	static void reset() { why.clear(); left.clear(); floorD = fledDown = -1; escKey = -1; esc = null; }
	/** The game's tally: the climb's moves, and the curse each floor was left with. */
	static String stats() { StringBuilder b = new StringBuilder(why.toString()).append(" left"); for (Map.Entry<Integer, Float> e : left.entrySet()) b.append(' ').append(e.getKey()).append(':').append(e.getValue()); reset(); return b.toString(); }
}
