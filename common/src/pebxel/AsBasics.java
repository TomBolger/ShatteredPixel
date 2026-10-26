package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Blandfruit;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.*;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.*;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.watabou.utils.PathFinder;
import java.util.*;
import pebxel.Assist.Target;
import static pebxel.Assist.*;

/**
 * The basics Pebble PD's bot learned (agent K3), hooked in through AsQuests: a fight that goes nowhere broken off (the
 * Great Crab blocking every blow it sees, a necromancer healing its skeleton as fast as it is hurt), and the food
 * budget (no long rest the food can't pay for; short of food, no dawdling on grass or searches with the way on known).
 */
final class AsBasics {
	private AsBasics() {}

	static final boolean LOOPS = opt("BLOOPS", true), FOOD = opt("BFOOD", true);
	static final int STALL = optInt("BSTALL", 8);           // blows at one foe with its health no lower: a stalled fight
	static final int FOODRESERVE = optInt("BFOODRES", 300);   // food (turns) kept back beyond what a rest eats

	// =============================================================================================================
	// a fight going nowhere (BF-smoke4: 8 of 60 games looped on the Great Crab or a necromancer's skeleton)
	// =============================================================================================================
	static int stallId = -1, stallBlows, stallLow, stallOff; static Level stallL;
	static float breakUntil = -1, stallAt = -1; static int breakId = -1;

	/** planA strikes the same foe again and again and its health never goes lower: something else. */
	static Target fightLoop(Target planA) {
		if (!LOOPS) return null;
		Hero h = Dungeon.hero; int hp = h.pos;
		// the blows struck (Assist.act's last act a blow, once a turn), not what plan A would do: the tactics may move instead
		Mob m = null;
		if (actKind == ENEMY && actCell >= 0 && Actor.now() != stallAt) {   // (Assist.act's note of what was done last: a blow at that cell)
			Char c = Actor.findChar(actCell);
			if (c instanceof Mob && c.isAlive() && L.adjacent(c.pos, hp)) m = (Mob) c;
		}
		if (stallL != L) { stallL = L; stallId = -1; breakId = -1; }
		if (Actor.now() != stallAt) {
			stallAt = Actor.now();
			if (m == null) { if (++stallOff > 3) stallId = -1; }
			else if (m.id() != stallId) { stallId = m.id(); stallBlows = 0; stallLow = m.HP; stallOff = 0; }
			else { stallOff = 0; stallBlows++; if (m.HP < stallLow) { stallLow = m.HP; stallBlows = 0; } }
		}
		if (DBG) System.out.println("    STALL act " + actKind + "@" + actCell + " m " + (m == null ? null : m.getClass().getSimpleName()) + " id " + stallId + " blows " + stallBlows + " low " + stallLow + " break " + breakId);
		boolean breaking = breakId >= 0 && Actor.now() < breakUntil;
		if (m == null && !breaking) return null;
		if (m != null && stallBlows >= STALL) { breakId = m.id(); breakUntil = Actor.now() + 6; stallBlows = 0; breaking = true; }
		if (!breaking) return null;
		Mob stuck = null; for (Mob x : mobs()) if (x.id() == breakId && x.isAlive()) stuck = x;
		if (stuck == null) { breakId = -1; return null; }
		// another foe beside the hero: it first
		Mob o = null; for (Mob x : mobs()) if (x != stuck && enemy(x) && adjacent(x.pos, hp) && x.state != x.PASSIVE && !h.isCharmedBy(x) && !(x instanceof com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.NPC) && (o == null || x.HP < o.HP)) o = x;
		if (o != null) return new Target(o.pos, ENEMY, "stalled: another foe first");
		// a skeleton its necromancer keeps healing: the necromancer from afar, else out of its sight
		if (stuck instanceof Necromancer.NecroSkeleton) {
			Mob nc = null; for (Mob x : mobs()) if (x instanceof Necromancer && enemy(x) && (nc == null || distance(x.pos, hp) < distance(nc.pos, hp))) nc = x;
			if (nc != null) {
				Target r = rangedAt(nc); if (r != null) { r.why = "stalled: the necromancer from afar"; return r; }
				int c = hideFrom(nc.pos, stuck); if (c >= 0) return new Target(c, GO, "stalled: out of the necromancer's sight");
			}
		}
		// the crab sees every blow coming: from afar with gas or fire, else away from it (it is the slower)
		if (stuck instanceof GreatCrab && !adjacent(stuck.pos, hp)) { Target r = harmfulAt(stuck); if (r != null) return r; }
		int c = stepAwayFrom(stuck.pos);
		if (c >= 0) return new Target(c, GO, "stalled: away from it");
		// cornered by a foe no blow gets through (the crab sees it coming): unseen (its blows land), away, or a gamble
		Class<?>[] esc = { PotionOfInvisibility.class, com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTeleportation.class, com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfMirrorImage.class, com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfLullaby.class };
		boolean read = !blind();
		for (Class<?> k : esc) for (Item i : h.belongings.backpack) if (k.isInstance(i) && known(i) && (i instanceof Potion || read))
			return use(i, i instanceof Potion ? Potion.AC_DRINK : com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll.AC_READ, -1, "stalled, cornered: a way out");
		if (read) for (Item i : h.belongings.backpack) if (i instanceof com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll && !known(i)) return use(i, com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll.AC_READ, -1, "stalled, cornered: an unknown scroll");
		for (Item i : h.belongings.backpack) if (i instanceof Potion && !known(i)) return use(i, Potion.AC_DRINK, -1, "stalled, cornered: an unknown potion");
		return null;
	}

	// =============================================================================================================
	// the Great Crab's dead ends (it blocks every blow it sees coming: cornered beside it with no way out, the fight
	// never ends). Away from it, and to wait for it, only where there's room to slip off again.
	// =============================================================================================================
	static final boolean CRABEND = opt("BCRABEND", true);

	/** Plan A taking the hero into (or keeping it in) a dead end with the crab hunting it: a cell with room instead. */
	static Target crab(Target planA) {
		if (!CRABEND || planA == null) return null;
		Hero h = Dungeon.hero; int hp = h.pos;
		Mob cr = null; for (Mob m : mobs()) if (m instanceof GreatCrab && enemy(m) && hunting(m)) cr = m;
		if (cr == null || h.rooted) return null;
		int goal = planA.kind == WAIT || planA.kind == ENEMY || planA.kind == USE ? hp : planA.obj >= 0 && planA.obj < L.length() ? planA.obj : planA.cell;
		if (goal < 0) return null;
		int[] cd = crabDist(cr.pos);
		boolean pk = pocket(goal, cr.pos, cd);
		if (DBG) System.out.println("    CRAB at " + cr.pos + " plan " + planA.why + "@" + goal + " pocket " + pk);
		if (!pk) return null;
		if (adjacent(cr.pos, hp) && goal == hp && planA.kind == ENEMY) return null;   // (beside it already: the ambush blow, or the stall rules)
		int best = -1, bs = -INF;
		for (int c = 0; c < L.length(); c++) if (dist[c] >= 1 && dist[c] <= 8 && open[c] && seenChar(c) == null && !danger[c] && !adjacent(c, cr.pos) && dist[c] < cd[c] * 3 / 2 + 1) {
			int sc = 3 * Math.min(distance(c, cr.pos), 5) - 2 * dist[c] + (sight(cr.pos, c) ? 0 : 12);
			if (sc > bs && !pocket(c, cr.pos, cd)) { bs = sc; best = c; } }
		if (best < 0) return null;
		return new Target(best, GO, "the crab: not into a dead end");
	}

	/** Steps from the crab (it walks round walls), INF where it can't go. */
	static int[] crabDist(int from) {
		int N = L.length(), W = L.width(); int[] d = new int[N]; Arrays.fill(d, INF); ArrayDeque<Integer> q = new ArrayDeque<>();
		d[from] = 0; q.add(from);
		while (!q.isEmpty()) { int u = q.poll(); if (d[u] >= 40) continue;
			for (int k : PathFinder.NEIGHBOURS8) { int v = u + k;
				if (v < 0 || v >= N || Math.abs(v % W - u % W) > 1 || d[v] < INF || !L.passable[v] || !(known[v] || L.heroFOV[v])) continue;
				d[v] = d[u] + 1; q.add(v); } }
		return d;
	}

	/** Is c a pocket: with the crab come up beside it (on the side it comes from), few cells left to step off to (14
	 *  or fewer within 6 steps of c, none beside the crab)? There every blow is seen and blocked for good. */
	static boolean pocket(int c, int crab, int[] cd) {
		int N = L.length(), W = L.width(), qc = crab;
		if (!adjacent(c, crab)) { int b = INF; for (int k : PathFinder.NEIGHBOURS8) { int v = c + k; if (v >= 0 && v < N && Math.abs(v % W - c % W) <= 1 && L.passable[v] && cd[v] < b) { b = cd[v]; qc = v; } } if (b >= INF) return false; }
		int[] d = new int[N]; Arrays.fill(d, -1); ArrayDeque<Integer> q = new ArrayDeque<>();
		d[c] = 0; q.add(c); int n = 0;
		while (!q.isEmpty()) { int u = q.poll(); if (++n > 14) return false; if (d[u] >= 6) continue;
			for (int k : PathFinder.NEIGHBOURS8) { int v = u + k;
				if (v < 0 || v >= N || Math.abs(v % W - u % W) > 1 || d[v] >= 0 || !L.passable[v] || !(known[v] || L.heroFOV[v]) || distance(v, qc) <= 1) continue;
				d[v] = d[u] + 1; q.add(v); } }
		return true;
	}

	// =============================================================================================================
	// piranhas (60 honest games to depth 5: 6 of 16 deaths): the plan walked into their water, fled a bite to dry
	// land out of reach, and walked back in, over and over. Piranhas can't leave the water: from the shore, a blow at
	// one beside the hero; a way on through the water waits on the shore for the one that's after the hero.
	// =============================================================================================================
	static final boolean FISH = opt("BFISH", true);
	static final int FISHWAIT = optInt("BFISHWAIT", 12);   // turns waited on the shore for one piranha
	static final Map<Integer, Integer> fishWaited = new HashMap<>(); static float fishLast = -99, fishTurn = -1; static Level fishL;

	static Target piranha(Target planA) {
		if (!FISH || planA == null) return null;
		Hero h = Dungeon.hero; int hp = h.pos;
		if (fishL != L) { fishL = L; fishWaited.clear(); fishLast = -99; }
		Mob adj = null, near = null; int nd = INF;
		for (Mob m : mobs()) if (m instanceof Piranha && enemy(m)) { fishLast = Actor.now();
			if (adjacent(m.pos, hp)) adj = m; if (distance(m.pos, hp) < nd) { nd = distance(m.pos, hp); near = m; } }
		if (L.water[hp] || h.rooted) return null;   // (in the water: out of it, Assist's own rules)
		if (adj != null) return h.HP * 10 >= h.HT * 4 ? new Target(adj.pos, ENEMY, "piranha: strike it from the shore") : null;
		if (Actor.now() - fishLast > 10 || (planA.kind != GO && planA.kind != PICKUP)) return null;
		int t = planA.obj >= 0 && planA.obj < L.length() ? planA.obj : planA.cell;
		if (t < 0 || dist[t] >= INF) return null;
		// the way there: the last dry cell before the water
		List<Integer> path = new ArrayList<>(); int c = t;
		while (c != hp && dist[c] > 0 && dist[c] < INF && path.size() < 200) { path.add(0, c); int b = -1;
			for (int k : PathFinder.NEIGHBOURS8) { int n = c + k; if (n >= 0 && n < L.length() && Math.abs(n % L.width() - c % L.width()) <= 1 && dist[n] == dist[c] - 1) { b = n; break; } }
			if (b < 0) break; c = b; }
		int shore = hp; boolean wet = false;
		for (int p : path) { if (L.water[p] || (near != null && distance(p, near.pos) <= 2 && shoreCell(p))) { wet = true; break; } shore = p; }   // (the water, or the shore within its reach)
		if (!wet) return null;
		int key = near != null ? near.id() : -1;
		int w = fishWaited.getOrDefault(key, 0);
		boolean hurt = h.HP * 2 < h.HT;
		if (w >= (hurt ? FISHWAIT * 5 : FISHWAIT)) return null;   // (it isn't coming: on)
		if (hurt && !shoreCell(hp)) {   // (hurt: off the shore, the wounds closing, till it goes)
			if (Actor.now() != fishTurn) { fishTurn = Actor.now(); fishWaited.put(key, w + 1); }
			Target r = new Target(hp, WAIT, "piranha: hurt, wait off the shore"); r.action = WAIT_ACT; return r;
		}
		if (shore != hp) return new Target(shore, GO, "piranha: to the shore, for it to come");
		if (Actor.now() != fishTurn) { fishTurn = Actor.now(); fishWaited.put(key, w + 1); }
		Target r = new Target(hp, WAIT, "piranha: wait on the shore for it"); r.action = WAIT_ACT; return r;
	}

	// =============================================================================================================
	// demon spawners (depths 21-24; strategy-reference 4.5: "kill the demon spawners on 21-24 first"): each one left
	// alive is a ripper demon in Yog's summons in place of a larva, and it drops a Healing potion. It never moves and
	// never strikes: the bot passed it by as a foe that can't come. Struck at good health with nothing else about.
	// =============================================================================================================
	static final boolean SPAWNERS = opt("BSPAWN", true);
	static final int SPAWNHP = optInt("BSPAWNHP", 70);   // health share to start on one (it sends a ripper as it's hurt)
	static final Set<Integer> spawnAt = new HashSet<>(); static Level spawnL;

	static Target spawner(Target planA) {
		if (!SPAWNERS) return null;
		Hero h = Dungeon.hero; int hp = h.pos;
		if (spawnL != L) { spawnL = L; spawnAt.clear(); }
		for (Mob m : mobs()) if (m instanceof DemonSpawner && m.isAlive() && L.heroFOV[m.pos]) spawnAt.add(m.pos);
		spawnAt.removeIf(c -> L.heroFOV[c] && !(Actor.findChar(c) instanceof DemonSpawner));   // (seen gone)
		if (spawnAt.isEmpty()) return null;
		for (Mob m : mobs()) if (enemy(m) && hostile(m) && !(m instanceof DemonSpawner) && (hunting(m) || adjacent(m.pos, hp))) return null;   // (a fight on: the usual rules)
		if (inDanger || h.rooted) return null;
		int best = -1, bd = INF;
		for (int c : spawnAt) { int w = AsBosses.walkNear(c); if (w < bd) { bd = w; best = c; } }
		if (best < 0) return null;
		Char sp = Actor.findChar(best);
		boolean fighting = adjacent(best, hp) && sp instanceof DemonSpawner && sp.HP < sp.HT;
		if (h.HP * 100 < h.HT * (fighting ? 40 : SPAWNHP)) return null;   // (rest first: Assist's own rules)
		if (adjacent(best, hp)) return sp instanceof DemonSpawner ? new Target(best, ENEMY, "spawner: destroy it") : null;
		if (bd >= INF) return null;
		int st = AsBosses.besideStep(best);
		return st >= 0 ? new Target(st, GO, "spawner: to it (Yog's demons, a Healing potion)") : null;
	}

	static boolean shoreCell(int c) { for (int k : PathFinder.NEIGHBOURS8) { int n = c + k; if (n >= 0 && n < L.length() && Math.abs(n % L.width() - c % L.width()) <= 1 && L.water[n]) return true; } return false; }

	/** A missile or a charged attack wand at m, if the way is clear. */
	static Target rangedAt(Mob m) {
		Hero h = Dungeon.hero;
		if (!line(h.pos, m.pos)) return null;
		for (Item it : h.belongings.backpack) if (it instanceof MissileWeapon) return use(it, Item.AC_THROW, m.pos, "throw");
		MagesStaff st = bag(MagesStaff.class); Object w = st == null ? null : field(st, "wand");
		if (w instanceof Wand && charged((Wand) w, st) && attackWand(w.getClass(), distance(h.pos, m.pos))) return use(st, MagesStaff.AC_ZAP, m.pos, "zap");
		for (Item it : h.belongings.backpack) if (it instanceof Wand && charged((Wand) it, it) && attackWand(it.getClass(), distance(h.pos, m.pos))) return use(it, Wand.AC_ZAP, m.pos, "zap");
		return null;
	}

	/** A known harmful potion thrown at m (gas from 3 off, fire from 2, never onto the hero's own grass). */
	static Target harmfulAt(Mob m) {
		Hero h = Dungeon.hero; int d = distance(h.pos, m.pos);
		for (Item it : h.belongings.backpack) {
			if (!(it instanceof Potion) || !known(it)) continue;
			if (((it instanceof PotionOfToxicGas) && d >= 3) || (it instanceof PotionOfLiquidFlame && d >= 2 && !L.flamable[h.pos]))
				return use(it, Potion.AC_THROW, m.pos, "stalled: a potion at it");
		}
		return null;
	}

	/** A cell within 4 steps out of the sight of src, no other foe beside it, nearest first. */
	static int hideFrom(int src, Mob not) {
		int hp = Dungeon.hero.pos, best = -1, bd = INF;
		for (int c = 0; c < L.length(); c++) {
			if (dist[c] < 1 || dist[c] > 4 || dist[c] >= bd || !open[c] || seenChar(c) != null || danger[c] || sight(src, c)) continue;
			boolean near = false; for (Mob x : mobs()) if (x != not && enemy(x) && hostile(x) && adjacent(x.pos, c)) near = true;
			if (!near) { bd = dist[c]; best = c; }
		}
		return best;
	}

	/** The free step farthest from c (and from the others in sight). */
	static int stepAwayFrom(int c) {
		int hp = Dungeon.hero.pos, best = -1, bs = -INF;
		for (int k : PathFinder.NEIGHBOURS8) { int n = hp + k;
			if (n < 0 || n >= L.length() || !open[n] || seenChar(n) != null || danger[n]) continue;
			int s = distance(n, c) * 4; for (Mob x : mobs()) if (enemy(x) && hostile(x) && adjacent(x.pos, n)) s -= 3;
			if (s > bs) { bs = s; best = n; } }
		return best;
	}

	// =============================================================================================================
	// the food budget (BF-smoke4: 7 of 60 deaths starving; floors take ~600 turns, a ration is 300)
	// =============================================================================================================

	/** Turns of food the hero has: the hunger left before starving plus what's in the pack. */
	static int foodTurns() {
		Hero h = Dungeon.hero; Hunger hu = h.buff(Hunger.class);
		int t = (int) Hunger.STARVING - (hu == null ? 0 : hu.hunger());
		for (Item i : h.belongings) if (i instanceof Food && !(i instanceof Blandfruit)) t += (int) (((Food) i).energy * i.quantity());
		return t;
	}
	/** Turns a rest to full health takes (1 HP a 10 turns: Regeneration). */
	static int restTurns() { Hero h = Dungeon.hero; return Math.max(0, h.HT - h.HP) * 10; }
	static boolean shortOfFood() { return foodTurns() < (int) Hunger.STARVING + FOODRESERVE / 2; }

	/** A hands suggestion vetoed (Assist planList's one hook): a long rest the food can't pay for, unless dying. */
	static boolean veto(Target hk) {
		if (!FOOD || hk == null) return false;
		Hero h = Dungeon.hero;
		if (hk.kind == WAIT && REST_ACT.equals(hk.action) && hk.why.startsWith("hurt")) {
			if (h.HP * 4 < h.HT || L.locked) return false;
			return foodTurns() < restTurns() + FOODRESERVE;
		}
		return false;
	}

	/** Short of food: plan A on grass, a look round or a search is put off for the way on, when that's known. */
	static Target hurry(Target planA) {
		if (!FOOD || planA == null || !shortOfFood() || Dungeon.bossLevel()) return null;
		String w = planA.why;
		if (!(w.startsWith("look") || w.equals("grass") || w.startsWith("search") || w.startsWith("dead end"))) return null;
		int st = L.exit();
		if (st < 0 || st >= L.length() || !known[st] || walk(st) >= INF || L.map[st] == Terrain.LOCKED_EXIT) return null;
		if (w.startsWith("look") && !w.contains("grass")) {   // (a room left unexplored may hold food: only a far one is given up)
			int near = INF; for (int c = 0; c < L.length(); c++) if (dist[c] > 0 && dist[c] < near && unseenBy(c)) near = dist[c];
			if (near <= 12) return null;
		}
		return new Target(st, st == Dungeon.hero.pos ? PICKUP : GO, "short of food: on down");
	}
}
