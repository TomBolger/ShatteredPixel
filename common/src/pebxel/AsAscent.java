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
import java.util.*;
import pebxel.Assist.Target;
import pebxel.bot.*;
import static pebxel.Assist.*;

/**
 * The climb back with the Amulet (agent K7; strategy-reference 1.2, AscensionChallenge). Taking Yog's stairs up with
 * the Amulet starts the ascension: every floor climbed into adds 2 to the curse (boss floors: none, and they heal in
 * full and feed), every empowered foe killed takes 1 off (a ghoul or ripper demon a half), and as it grows: 2+ every
 * foe on the floor is called to the hero (they all come), 4+ those not yet hunting move twice as fast, 6+ the hero walks
 * at half speed, 8+ he loses (curse - 4) / 4 health a turn off the boss floors. Each floor climbed into is emptied and
 * filled again from one foe, respawning every 5 to 25 turns. Foes are empowered by kind (rats x10 down to scorpios x1.1).
 *
 * So the floors are a race to the stairs up by the known way (no exploring, no item hunting, no holding a doorway for
 * foes that never stop coming), fighting what reaches the hero anyway (each kill lowers the curse) and, with the curse
 * near its harm, the safe fights on the way too; leaving by the stairs a fight the lookahead says is lost; resting only
 * where it pays (a boss floor: no harm, no foes; the stairs with the curse below its harm).
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

	/** Holding the Amulet: the way is up. */
	static boolean on() { Hero h = Dungeon.hero; return ON && h != null && h.belongings.getItem(Amulet.class) != null; }
	/** The curse's count (0 before Yog's stairs). */
	static float stacks() {
		Hero h = Dungeon.hero; AscensionChallenge a = h == null ? null : h.buff(AscensionChallenge.class);
		Object s = a == null ? null : field(a, "stacks");
		return s instanceof Float ? (Float) s : 0;
	}
	/** The curse harms the hero here (8+, off the boss floors). */
	static boolean harm() { return stacks() >= 8 && !Dungeon.bossLevel(); }

	// stats (per game)
	static final Map<String, Integer> why = new TreeMap<>();
	static Target note(Target t) { if (t != null) why.merge(t.why, 1, Integer::sum); return t; }

	/** The hook (Assist.planList's skills, after the hands): the climb's own move, or null (the usual rules: a fight). */
	static Target suggest(Target first) {
		if (!on()) return null;
		try { return note(climb(first)); } catch (Throwable e) { lastError = e; return null; }
	}

	static Target climb(Target first) {
		Hero h = Dungeon.hero; int hp = h.pos, ent = L.entrance();
		float st = stacks();
		if (h.rooted) return null;   // (held fast: the usual rules, a fight or a wait)
		boolean entKnown = ent >= 0 && ent < L.length() && known[ent] && walk(ent) < INF;
		int w = entKnown ? (ent == hp ? 0 : walk(ent)) : INF;
		double hs = h.speed();
		// the foes in sight: one beside the hero is fought (or left by the stairs beside him); one that will reach him
		// before the stairs, or blocks the way, too; a shooter in line is the tactics' (out of its line, or at it)
		boolean adj = false, catches = false, shooter = false; Mob near = null;
		for (Mob m : mobs()) {
			if (!enemy(m) || m.state == m.PASSIVE || m instanceof NPC || h.isCharmedBy(m)) continue;
			int d = distance(m.pos, hp);
			if (d <= 1) adj = true;
			if (near == null || d < distance(near.pos, hp)) near = m;
			if (m.state == m.SLEEPING) continue;
			boolean shoots = FoeView.shooter(m) && (line(m.pos, hp) || m instanceof com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Eye && sight(m.pos, hp));
			if (shoots && d <= 8 && !(m instanceof com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Scorpio)) shooter = true;   // (a scorpio never comes: below, out of its line on the way on)
			if (m instanceof com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Scorpio) continue;
			double ms = m.speed();
			if (entKnown && distance(m.pos, ent) < w && d <= w + 2) catches = true;   // (nearer the stairs than the hero: in the way)
			else if (hunting(m) && (ms > hs ? (d - 1) / Math.max(1e-3, ms - hs) < w / hs : d <= 2)) catches = true;
		}
		// on the stairs (or a step off) with a fight on: up and away when the lookahead says the fight is the worse
		if (adj && entKnown && w <= 2) { Target t = leave(w, ent); if (t != null) return t; }
		if (adj || catches || shooter) {
			// the curse near its harm: kills are worth taking; else the usual rules all the same (it's fought)
			return null;
		}
		// a scorpio (it backs off, spitting while it has a line): out of every line, on the way to the stairs
		if (entKnown) { Target t = past(hp, ent); if (t != null) return t; }
		// the curse near its harm and a safe fight in sight, off the way only a little: taken for the kill
		if (near != null && st + 2 >= 8 && Brain.fight && Brain.base != null && Brain.base.pDeath < SAFE && distance(near.pos, hp) <= 4 && near.state != near.SLEEPING) return null;
		// a boss floor: no harm, no foes, healing in full: rest to full first (the next four floors are the curse's)
		if (Dungeon.bossLevel() && calm() && h.HP * 100 < h.HT * 95 && !starving() && !declined(null, REST_ACT)) return rest("ascent: rest on the boss floor");
		// something worth a step or two off the way
		Target p = pick(hp, w);
		if (p != null) return p;
		if (!entKnown) return explore(hp);
		// at the stairs: hurt, the curse harmless, the next floor no boss's: rest first (the foes that come are kills)
		if (w == 0 && st < 8 && Dungeon.depth > 1 && !Dungeon.bossLevel(Dungeon.depth - 1) && h.HP * 100 < h.HT * RESTAT && calm() && !hungry() && !declined(null, REST_ACT))
			return rest("ascent: rest at the stairs");
		return new Target(ent, w == 0 ? PICKUP : GO, "ascent: up");
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
		return new Target(best, GO, now ? "ascent: out of the scorpio's line, on" : "ascent: on, out of the scorpio's line");
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

	/** A fight beside the stairs: the stairs taken, when the lookahead says leaving (w steps and gone) beats fighting on
	 *  by the margin (each kill lowers the curse: a small price for going without them). */
	static Target leave(int w, int ent) {
		if (!Brain.fight || Brain.base == null || Brain.me == null) return null;
		Look.Out o = Look.run(Brain.me, Brain.fs, Brain.plan().flee(w), Brain.seed);
		int n = Brain.me.heals; Hero h = Dungeon.hero;
		double vh = Budget.rescue(h, Math.max(1, n), 1), vh2 = Budget.rescue(h, Math.max(1, n - 1), 1);
		double v = Brain.gone(o, 0, 0) - Brain.fightV(Brain.base, n, vh, vh2) - 0.05 * Brain.fs.size();
		if (v <= Brain.MARGIN) return null;
		Target t = new Target(ent, w == 0 ? PICKUP : GO, "ascent: up, out of the fight");
		t.hint = v;
		return t;
	}

	/** An item a few steps off the way: Healing and food (more when short of it) further. */
	static Target pick(int hp, int w) {
		int best = -1, bk = INF;
		for (Heap hh : L.heaps.valueList()) {
			if (!heapOk(hh) || seenChar(hh.pos) != null && hh.pos != hp) continue;
			int d = hh.pos == hp ? 0 : walk(hh.pos);
			if (d >= INF) continue;
			Item top = hh.peek();
			boolean dear = hh.type == Heap.Type.HEAP && (top instanceof PotionOfHealing || precious(top) || (top instanceof Food && foodLeft() < 2));
			int ent = L.entrance(), off = w >= INF ? d : d + (known[ent] && walk(ent) < INF ? Math.max(0, distance(hh.pos, ent) - w) : 0);   // (the detour: there, then on to the stairs)
			if (off > (dear ? PICK * 2 : PICK) || hh.type != Heap.Type.HEAP && !dear) continue;
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
		return y.equals("pick up") || y.equals("explore") || y.equals("search") || y.startsWith("look") || y.equals("unlock / well") || y.equals("dead end: search") || y.equals("talk");
	}

	/** The stairs down while climbing (a rule made for the way down: "short of food: on down"): never, but to escape. */
	static boolean down(Brain.Cand c) {
		if (!on() || Dungeon.depth >= 26) return false;
		Target t = c.t; int ex = L.exit();
		return (t.kind == GO || t.kind == PICKUP) && ex >= 0 && (t.obj == ex || t.cell == ex) && !t.why.startsWith("escape");
	}

	static void reset() { why.clear(); }
	static String stats() { String s = why.toString(); reset(); return s; }
}
