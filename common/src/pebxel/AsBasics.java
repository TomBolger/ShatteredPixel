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
		bossDown();   // (the bosses in sight noted, each decision: AsBasics.restUnaffordable)
		foodSeen(); hazNote(); shotNote(); fishNote();
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
	static int crabPos = -1; static float crabSeenAt = -99; static Level crabL;

	/** Plan A taking the hero into (or keeping it in) a dead end with the crab hunting it: a cell with room instead. */
	static Target crab(Target planA) {
		if (!CRABEND || planA == null) return null;
		Hero h = Dungeon.hero; int hp = h.pos;
		Mob cr = null; for (Mob m : mobs()) if (m instanceof GreatCrab && enemy(m) && hunting(m)) cr = m;
		if (crabL != L) { crabL = L; crabSeenAt = -99; }
		if (cr != null) { crabPos = cr.pos; crabSeenAt = Actor.now(); }
		else if (Actor.now() - crabSeenAt > 20 || L.heroFOV[crabPos]) return null;   // (out of sight lately, hunting: where it was seen, coming)
		if (h.rooted) return null;
		int crp = crabPos;
		int goal = planA.kind == WAIT || planA.kind == ENEMY || planA.kind == USE ? hp : planA.obj >= 0 && planA.obj < L.length() ? planA.obj : planA.cell;
		if (goal < 0) return null;
		int[] cd = crabDist(crp);
		boolean pk = pocket(goal, crp, cd);
		if (DBG) System.out.println("    CRAB at " + crp + " plan " + planA.why + "@" + goal + " pocket " + pk);
		if (!pk) return null;
		if (adjacent(crp, hp) && goal == hp && planA.kind == ENEMY) return null;   // (beside it already: the ambush blow, or the stall rules)
		int best = -1, bs = -INF;
		for (int c = 0; c < L.length(); c++) if (dist[c] >= 1 && dist[c] <= 8 && open[c] && seenChar(c) == null && !danger[c] && !adjacent(c, crp) && dist[c] < cd[c] * 3 / 2 + 1) {
			int sc = 3 * Math.min(distance(c, crp), 5) - 2 * dist[c] + (sight(crp, c) ? 0 : 12);
			if (sc > bs && !pocket(c, crp, cd)) { bs = sc; best = c; } }
		if (best < 0) return null;
		return new Target(best, GO, "the crab: not into a dead end");
	}

	/** A move into a pocket with the crab hunting the hero (seen in the last 20 turns). */
	static boolean crabVeto(Target t) {
		if (!CRABEND || t == null || t.kind != GO || t.cell < 0 || crabL != L || Actor.now() - crabSeenAt > 20 || crabPos < 0) return false;
		boolean v = pocket(t.cell, crabPos, crabDist(crabPos)) && !pocket(Dungeon.hero.pos, crabPos, crabDist(crabPos));
		if (DBG && v) System.out.println("    CRAB veto " + t.why + "@" + t.cell);
		return v;
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
	 *  or fewer within 6 steps of c, none beside the crab)? There every blow is seen and blocked for good. (Any cell
	 *  within 3 that would corner c, not just the one on the crab's way, made most cells pockets: 60 games, no better.) */
	static boolean pocket(int c, int crab, int[] cd) {
		int N = L.length(), W = L.width(), qc = crab;
		if (!adjacent(c, crab)) { int b = INF; for (int k : PathFinder.NEIGHBOURS8) { int v = c + k; if (v >= 0 && v < N && Math.abs(v % W - c % W) <= 1 && L.passable[v] && cd[v] < b) { b = cd[v]; qc = v; } } if (b >= INF) return false; }
		return cornered(c, qc);
	}
	/** The crab at x: 14 or fewer cells left to c, none beside x, within 6 steps. */
	static boolean cornered(int c, int x) {
		int N = L.length(), W = L.width(); int[] d = new int[N]; Arrays.fill(d, -1); ArrayDeque<Integer> q = new ArrayDeque<>();
		d[c] = 0; q.add(c); int n = 0;
		while (!q.isEmpty()) { int u = q.poll(); if (++n > 14) return false; if (d[u] >= 6) continue;
			for (int k : PathFinder.NEIGHBOURS8) { int v = u + k;
				if (v < 0 || v >= N || Math.abs(v % W - u % W) > 1 || d[v] >= 0 || !L.passable[v] || !(known[v] || L.heroFOV[v]) || distance(v, x) <= 1) continue;
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

	// =============================================================================================================
	// a chasm as the way out (Chasm.heroLand: the fall costs at least half the health left, with bleeding and a limp,
	// and foes don't follow): only when losing a fight beside one, with no Healing known (the hands' escapes go first)
	// =============================================================================================================
	static final boolean CHASM = opt("BCHASM", true);
	static final int CHASMHP = optInt("BCHASMHP", 35);   // health share at least: the landing can kill below it

	static Target chasm() {
		if (!CHASM || Dungeon.bossLevel() || Dungeon.depth >= 25) return null;
		Hero h = Dungeon.hero; int hp = h.pos;
		if (h.rooted || h.HP * 100 < h.HT * CHASMHP || h.buff(com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Levitation.class) != null) return null;
		boolean adj = false; for (Mob m : mobs()) if (hostile(m) && adjacent(m.pos, hp)) adj = true;
		if (!adj) return null;
		com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing heal = bag(com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing.class);
		if (heal != null && heal.isKnown()) return null;
		int pit = -1; for (int k : PathFinder.NEIGHBOURS8) { int c = hp + k; if (c >= 0 && c < L.length() && L.pit[c] && !L.solid[c] && known[c] && Actor.findChar(c) == null) pit = c; }
		if (pit < 0 || !(OFIGHT ? oLosing(OESC) : h.HP * 2 < h.HT)) return null;
		return new Target(pit, GO, "losing: down the chasm (half the health left, but out)");
	}

	// =============================================================================================================
	// the sacrificial fire (SacrificeRoom): a foe killed while beside the altar's fire feeds it (2-3x its experience);
	// fed 6 + 4 a depth, it gives a prize. Foes fought from the altar itself are all beside it.
	// =============================================================================================================
	static final boolean SACRIFICE = opt("BSACRIFICE", true);
	static final int SACWALK = optInt("BSACWALK", 8);   // steps to the altar at most, with a foe coming

	static Target sacrifice(Target planA) {
		if (!SACRIFICE || Dungeon.bossLevel()) return null;
		Hero h = Dungeon.hero; int hp = h.pos;
		com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob f = L.blobs.get(com.shatteredpixel.shatteredpixeldungeon.actors.blobs.SacrificialFire.class);
		if (f == null || f.volume <= 0 || f.cur == null) return null;
		int alt = -1; for (int c = 0; c < L.length() && c < f.cur.length; c++) if (f.cur[c] > 0 && known[c]) alt = c;
		if (alt < 0 || h.rooted || h.HP * 2 < h.HT) return null;
		Mob near = null; int nd = INF; boolean adjFoe = false;
		for (Mob m : mobs()) if (hostile(m) && hunting(m) && !(m instanceof Piranha)) { int w = walk(m.pos); if (w < nd) { nd = w; near = m; } adjFoe |= adjacent(m.pos, hp); }
		if (near == null) return null;
		if (hp == alt) {   // on the altar: strike what's beside it (all of it is in the fire's reach), else let it come
			Mob o = null; for (Mob m : mobs()) if (hostile(m) && adjacent(m.pos, hp) && (o == null || m.HP < o.HP)) o = m;
			if (o != null) return new Target(o.pos, ENEMY, "sacrificial fire: strike it beside the altar");
			Target t = new Target(hp, WAIT, "sacrificial fire: let it come to the altar"); t.action = WAIT_ACT; return t;
		}
		if (adjFoe || walk(alt) > SACWALK || walk(alt) >= nd + 2 || seenChar(alt) != null || bad.containsKey(alt)) return null;   // (to the altar ahead of it, not past it)
		return new Target(alt, GO, "sacrificial fire: to the altar, to fight there");
	}

	// =============================================================================================================
	// sleepers (Pebble PD's bot): a blow at a sleeping foe always lands (a surprise), and one left asleep wakes later on
	// its own and comes hunting at a bad time. Crept up on and struck first, when the fight model says it's an easy one.
	// =============================================================================================================
	static final boolean SNEAK = opt("BSNEAK", false);   // (off: 60 honest games to depth 5, deaths 6 -> 8 on, 8 with 1% risk and 6 steps: waking foes better left asleep)
	static final int SNEAKWALK = optInt("BSNEAKWALK", 10), SNEAKRISK = optInt("BSNEAKRISK", 3);   // steps; the fight's chance of death (percent) at most

	static Target sleeper(Target planA) {
		if (!SNEAK || planA == null || planA.kind == ENEMY || planA.kind == USE || Dungeon.bossLevel()) return null;
		Hero h = Dungeon.hero; int hp = h.pos;
		if (h.HP * 10 < h.HT * 7 || inDanger || h.rooted || seenFoe && anyAwake()) return null;
		Mob best = null; int bw = INF;
		for (Mob m : mobs()) {
			if (!enemy(m) || m.state != m.SLEEPING || m instanceof com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.NPC || m instanceof Piranha || m instanceof Mimic || Char.hasProp(m, Char.Property.IMMOVABLE) || Char.hasProp(m, Char.Property.BOSS) || Char.hasProp(m, Char.Property.MINIBOSS)) continue;
			int w = AsBosses.walkNear(m.pos);
			if (w > SNEAKWALK || w >= bw) continue;
			if (pebxel.bot.FightModel.evaluate(h, Collections.singletonList(m)).pDeath * 100 > SNEAKRISK) continue;
			bw = w; best = m;
		}
		if (best == null) return null;
		if (adjacent(best.pos, hp)) return new Target(best.pos, ENEMY, "sleeper: a sure first blow");
		int st = AsBosses.besideStep(best.pos);
		return st >= 0 ? new Target(st, GO, "sleeper: creep up on it") : null;
	}
	static boolean anyAwake() { for (Mob m : mobs()) if (hostile(m)) return true; return false; }

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
	// casters (K6: head start 11, 38 of 47 deaths to shamans' magic bolts; head start 16, 27 of 34 to warlocks' shadow
	// bolts). A bolt ignores armour, a blow beside it doesn't: a shaman's zap 6-15, its blow 5-10 less the armour. The
	// old tactic stepped out of its line and waited, and it stepped back into line from afar and zapped, over and over.
	// Now: a true corner (every cell on its way there that sees the hero is beside him), else close in on it.
	// =============================================================================================================
	static final boolean CASTER = opt("BCASTER", true);
	static final int CASTWALK = optInt("BCASTWALK", 3), CASTRISK = optInt("BCASTRISK", 40);   // steps to a corner at most; the fight's death chance (percent) above which it isn't charged
	static boolean caster(Mob m) { return m instanceof Shaman || m instanceof Warlock || m instanceof DM100; }
	/** Can the caster at s bolt the hero at c (the game's own test, Shaman.canAttack)? */
	static boolean bolts(int s, int c) { try { return new com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica(s, c, com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica.MAGIC_BOLT).collisionPos == c; } catch (Throwable e) { return false; } }
	/** The nearest awake caster after the hero, in sight. */
	static Mob casterNear() {
		Mob b = null; int hp = Dungeon.hero.pos;
		for (Mob m : mobs()) if (caster(m) && hostile(m) && (hunting(m) || bolts(m.pos, hp)) && (b == null || distance(m.pos, hp) < distance(b.pos, hp))) b = m;
		return b;
	}
	/** Is c no true corner against the caster at s: a cell it could reach before c (fewer of its steps) that, two or
	 *  more from c, sees c (it would stop there and bolt)? Any way it may come, not only the shortest. */
	static boolean seenOnWay(int s, int c) { return seenOnWay(s, c, crabDist(s)); }
	static boolean seenOnWay(int s, int c, int[] ds) {
		if (ds[c] >= INF) return true;
		for (int x = 0; x < L.length(); x++) if (ds[x] < ds[c] && distance(x, c) >= 2 && bolts(x, c)) return true;
		return false;
	}
	/** A caster after the hero, not beside him: to a true corner (once per caster), else at it. */
	static Target casterTactic(Target planA) {
		if (!CASTER) return null;
		Hero h = Dungeon.hero; int hp = h.pos;
		if (h.rooted) return null;
		Mob sh = casterNear(); if (sh == null) return null;
		for (Mob m : mobs()) if (hostile(m) && adjacent(m.pos, hp)) return null;   // (one beside him: that fight first)
		if (cornerL != L) { cornerL = L; cornerFailed.clear(); }
		int[] ds = crabDist(sh.pos);
		boolean failed = cornerFailed.contains(sh.id());
		if (!failed && cornerAt == hp && cornerId == sh.id()) {   // at the corner chosen: wait for it, unless it bolts from afar after all
			if (bolts(sh.pos, hp) || Actor.now() >= cornerUntil) { cornerFailed.add(sh.id()); failed = true; cornerAt = -1; }
			else { Target t = new Target(hp, WAIT, "caster: wait at the corner"); t.action = WAIT_ACT; return t; }
		}
		if (!failed && (cornerId != sh.id() || cornerAt < 0)) {
			int best = -1, bd = INF;
			for (int c = 0; c < L.length(); c++) if (dist[c] >= 0 && dist[c] <= CASTWALK && dist[c] < bd && (c == hp || (open[c] && seenChar(c) == null)) && !danger[c] && !L.water[c] && !bad.containsKey(c) && !seenOnWay(sh.pos, c, ds)) { bd = dist[c]; best = c; }
			if (best >= 0 && bd < distance(sh.pos, hp) - 1) { cornerAt = best; cornerId = sh.id(); cornerUntil = Actor.now() + bd + distance(sh.pos, best) + 4; }
			else cornerFailed.add(sh.id());
		}
		if (!cornerFailed.contains(sh.id()) && cornerId == sh.id() && cornerAt >= 0) {
			if (cornerAt == hp) { Target t = new Target(hp, WAIT, "caster: wait at the corner"); t.action = WAIT_ACT; return t; }
			if (walk(cornerAt) <= CASTWALK + 1) return new Target(cornerAt, GO, "caster: to a corner it must come round");
			cornerFailed.add(sh.id());
		}
		java.util.List<Mob> fs = new ArrayList<>(); for (Mob m : mobs()) if (hostile(m) && hunting(m)) fs.add(m);
		double pd = 0; try { pd = pebxel.bot.FightModel.evaluate(h, fs).pDeath * 100; } catch (Throwable e) { }
		if (pd <= CASTRISK && walk(sh.pos) <= 12) return new Target(sh.pos, ENEMY, "caster: close in (its bolt ignores armour)");
		// a fight it would lose: out of its bolts' way for a while (no cell it can reach in two steps sees there), the
		// rules' escapes have their say before this (the stairs, Healing): not picking things up under its bolts
		List<int[]> cds = new ArrayList<>(); for (Mob m : mobs()) if (caster(m) && hostile(m)) cds.add(m == sh ? ds : crabDist(m.pos));
		int hide = -1, hd = INF;
		for (int c = 0; c < L.length(); c++) if (dist[c] >= 1 && dist[c] <= 6 && dist[c] < hd && open[c] && seenChar(c) == null && !danger[c] && !bad.containsKey(c)) {
			boolean seen = false; for (int[] dd : cds) for (int x = 0; x < L.length() && !seen; x++) if (dd[x] <= 2 && bolts(x, c)) seen = true;
			if (!seen) { hd = dist[c]; hide = c; } }
		return hide >= 0 && bolts(sh.pos, hp) ? new Target(hide, GO, "caster: out of its bolts' way (a fight to lose)") : null;
	}
	static final Set<Integer> cornerFailed = new HashSet<>(); static Level cornerL;
	static int cornerAt = -1, cornerId = -1; static float cornerUntil = -1;
	/** The old shooter tactic (Assist.tactics) against a caster: out of its line, only for it to step back into line. */
	static boolean casterVeto(Target t) {
		if (!CASTER || t == null || t.why == null) return false;
		if (!(t.why.startsWith("tactic: out of its line") || t.why.startsWith("tactic: wait round the corner"))) return false;
		Mob sh = casterNear(); return sh != null;
	}
	/** Hit since the last decision by nothing beside the hero nor on him (no burning, poison, bleeding, hunger): a bolt
	 *  or a missile from out of sight (dark floors: a warlock sees farther than the hero). Turns it lasts. */
	static int shotHp = -1; static float shotAt = -99, shotT = -1; static Level shotL;
	static void shotNote() {
		Hero h = Dungeon.hero; float now = Actor.now();
		if (shotL != L) { shotL = L; shotHp = -1; shotAt = -99; }
		if (now == shotT) return;
		boolean adj = false; for (Mob m : mobs()) if (m.isAlive() && m.alignment == Char.Alignment.ENEMY && adjacent(m.pos, h.pos)) adj = true;
		boolean dot = h.buff(com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Burning.class) != null || h.buff(com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Poison.class) != null
				|| h.buff(com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Bleeding.class) != null || Assist.starving() || Assist.hazMem != null && h.pos < Assist.hazMem.length && Assist.hazMem[h.pos];
		if (shotHp >= 0 && h.HP < shotHp && !adj && !dot) shotAt = now;
		shotHp = h.HP; shotT = now;
	}
	static boolean shotLately() { return Actor.now() - shotAt <= 3 && Actor.now() >= shotAt; }
	/** A wait (any) in a caster's line, or with bolts coming from out of sight, not beside a foe: a bolt a turn for nothing. */
	static boolean waitInLine(Target t) {
		if (!CASTER || t == null || t.kind != WAIT || REST_ACT.equals(t.action)) return false;
		int hp = Dungeon.hero.pos;
		for (Mob m : mobs()) if (hostile(m) && adjacent(m.pos, hp)) return false;
		if (t.why != null && t.why.startsWith("caster")) return false;
		if (shotLately()) return true;
		for (Mob m : mobs()) if (caster(m) && hostile(m) && bolts(m.pos, hp)) return true;
		return false;
	}

	// =============================================================================================================
	// food in the water (K6: a floor's food left behind in sight, 65 turns of it a floor; the aquariums and pools hold
	// it, and the meat of the piranhas killed sinks there). Short of food, every piranha seen here dead and none seen
	// for a while: in for it (the plan keeps out of water for good once one is seen).
	// =============================================================================================================
	static final boolean WATERFOOD = opt("BWATERFOOD", true);
	static final List<Mob> fishSeen = new ArrayList<>(); static Level fishSeenL; static float fishSeenAt = -99;
	static void fishNote() {
		if (fishSeenL != L) { fishSeenL = L; fishSeen.clear(); fishSeenAt = -99; }
		for (Mob m : mobs()) if (m instanceof Piranha && enemy(m)) { if (!fishSeen.contains(m)) fishSeen.add(m); fishSeenAt = Actor.now(); }
	}
	static Target waterFood(Target planA) {
		if (!WATERFOOD || planA == null || seenFoe || inDanger || Dungeon.hero.rooted || !(shortOfFood() || Assist.hungry())) return null;
		if (planA.kind == PICKUP && planA.why.startsWith("hungry")) return null;
		for (Mob m : fishSeen) if (m.isAlive()) return null;
		if (Actor.now() - fishSeenAt < 20) return null;
		Hero h = Dungeon.hero; int best = -1, bd = INF;
		for (com.shatteredpixel.shatteredpixeldungeon.items.Heap hp : L.heaps.valueList()) {
			if (!hp.seen || hp.type != com.shatteredpixel.shatteredpixeldungeon.items.Heap.Type.HEAP || !(hp.peek() instanceof Food) || hp.peek() instanceof Blandfruit) continue;
			int c = hp.pos; if (!L.water[c] || !known[c] || bad.containsKey(c) || Actor.findChar(c) != null) continue;
			int d = waterWalk(c); if (d < bd) { bd = d; best = c; }
		}
		if (best < 0 || bd > 30) return null;
		return new Target(best, best == h.pos ? PICKUP : GO, "short of food: the food in the water");
	}
	/** Steps to c over known passable cells, water too (the plan's own walk keeps out of it). */
	static int waterWalk(int c) {
		int N = L.length(), W = L.width(), s = Dungeon.hero.pos; int[] d = new int[N]; Arrays.fill(d, INF); ArrayDeque<Integer> q = new ArrayDeque<>(); d[s] = 0; q.add(s);
		while (!q.isEmpty()) { int u = q.poll(); if (u == c) return d[u]; if (d[u] > 40) continue;
			for (int k : PathFinder.NEIGHBOURS8) { int v = u + k;
				if (v < 0 || v >= N || Math.abs(v % W - u % W) > 1 || d[v] < INF || !L.passable[v] || !known[v] || L.avoid[v] || danger[v]) continue;
				d[v] = d[u] + 1; q.add(v); } }
		return INF;
	}

	// =============================================================================================================
	// stuck (K6): plan A down to searching. In Shattered a search on purpose finds every secret beside the hero at once
	// (Hero.search: intentional, chance 1), so a spot searched is done for good; searching on there is time and food
	// for nothing (a starved game searched one spot 700 times). Out of a branch (the Blacksmith's mine: AsQuests gives
	// up its quests after 1500 steps, the way out with them) by its stairs; else, with every wall searched, a chasm.
	// =============================================================================================================
	static final boolean STUCK = opt("BSTUCK", true);
	static final int HAZAGE = optInt("BHAZAGE", 50);   // turns after which gas or fire last seen (out of sight now) is taken as gone
	static float[] hazSeen; static Level hazL;
	/** Each decision: when the remembered gas / fire was last in sight. */
	static void hazNote() {
		if (hazL != L || hazSeen == null || hazSeen.length != L.length()) { hazL = L; hazSeen = new float[L.length()]; Arrays.fill(hazSeen, Actor.now()); }
		if (Assist.hazMem == null || Assist.hazMem.length != L.length()) return;
		for (int c = 0; c < L.length(); c++) if (Assist.hazMem[c] && L.heroFOV[c]) hazSeen[c] = Actor.now();
	}
	/** Old gas / fire memories out of sight cleared; any cleared? */
	static boolean hazAge() {
		if (hazSeen == null || Assist.hazMem == null || hazSeen.length != Assist.hazMem.length) return false;
		boolean any = false;
		for (int c = 0; c < L.length(); c++) if (Assist.hazMem[c] && !L.heroFOV[c] && Actor.now() - hazSeen[c] > HAZAGE) { Assist.hazMem[c] = false; any = true; }
		return any;
	}
	static Target stuck(Target planA) {
		if (!STUCK || planA == null || seenFoe || inDanger) return null;
		boolean srch = SEARCH_ACT.equals(planA.action) || planA.why.startsWith("search") || planA.why.startsWith("dead end");
		if (!srch) return null;
		Hero h = Dungeon.hero; int hp = h.pos;
		// gas or fire remembered where it was last seen, behind a door shut since: it never clears, and the only way on
		// stays shut for good (K6-b1: 800 searches in a shop, the corridor's toxic gas remembered). Gone by now.
		if (hazAge()) return null;
		if (Dungeon.branch != 0) {
			for (com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition tr : L.transitions) if (tr.type == com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition.Type.BRANCH_ENTRANCE && known[tr.cell()])
				return tr.cell() == hp ? new Target(hp, PICKUP, "stuck: out of the branch") : walk(tr.cell()) < INF ? new Target(tr.cell(), GO, "stuck: out of the branch") : null;
			return null;
		}
		if (Assist.srchSpot(-1, -1) >= 0 || Dungeon.bossLevel() || Dungeon.depth >= 25 || h.HP * 2 < h.HT) return null;   // (walls left to search: Assist's own way)
		int pit = -1, pd = INF;
		for (int c = 0; c < L.length(); c++) if (L.pit[c] && known[c] && !L.solid[c] && Actor.findChar(c) == null) {
			for (int k : PathFinder.NEIGHBOURS8) { int n = c + k; if (n >= 0 && n < L.length() && (n == hp || (dist[n] < INF && open[n])) && (n == hp ? 0 : dist[n]) < pd) { pd = n == hp ? 0 : dist[n]; pit = c; } } }
		if (pit < 0) return null;
		return new Target(pit, GO, "stuck: down the chasm (every wall searched)");
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
	static boolean shortOfFood() {
		if (!BUDGET) return foodTurns() < (int) Hunger.STARVING + FOODRESERVE / 2;
		// (K6) the run's budget: the turns of food before starving, plus the floor's own food while it's still to find,
		// against the rest of this floor's walk and a reserve: short, nothing optional (the next floor has food too)
		int f = foodTurns() - (int) Hunger.STARVING + (foodHere ? 0 : FLOORMEAL);
		return f < FOODRESERVE / 2 + floorNeed();
	}
	static final boolean BUDGET = opt("BBUDGET", true);
	static final int FLOORMEAL = optInt("BFLOORMEAL", 300);   // the food a floor holds (a ration, 4 in 5; a pasty, 1 in 5)

	/** A hands suggestion vetoed (Assist planList's one hook): a long rest the food can't pay for, unless dying. */
	static boolean veto(Target hk) {
		if (!FOOD || hk == null) return false;
		Hero h = Dungeon.hero;
		if (hk.kind == WAIT && REST_ACT.equals(hk.action)) {
			if (restUnaffordable(hk)) return true;
			if (hk.why.startsWith("hurt")) return stairsRest(hk);
		}
		return false;
	}

	static final boolean RESTALL = opt("BRESTALL", true);   // (K6) every rest priced, not only the hands' "hurt" ones: 400-turn rests before (and after) the boss starved runs
	static final int FLOORNEED = optInt("BFLOORNEED", 150);   // turns the rest of a floor not yet explored is reckoned to take
	/** A rest (any: the hands', the boss's, the brain's) the food can't pay for: none while starving (there is no
	 *  regeneration then), and none that leaves less than the reserve plus the rest of this floor's walk. Dying
	 *  (a quarter health), or with the floor locked (hunger stops), only one that would starve the hero. */
	static boolean restUnaffordable(Target t) {
		if (!FOOD || t == null || t.kind != WAIT || !REST_ACT.equals(t.action)) return false;
		if (!RESTALL && !t.why.startsWith("hurt")) return false;
		Hero h = Dungeon.hero;
		if (Assist.starving()) return true;
		if (t.why.startsWith("rest before the boss") && bossDown()) return true;
		if (L.locked) return false;
		int f = foodTurns() - (int) Hunger.STARVING, need = restTurns();   // (turns of food before starving)
		if (h.HP * 4 < h.HT) return f < need;
		return f - need < FOODRESERVE / 2 + floorNeed();
	}
	/** The floor's boss seen to die (its body gone from the floor while in sight, or seen dead): no boss to rest for. */
	static final List<Mob> bossesSeen = new ArrayList<>(); static Level bossL;
	static boolean bossDown() {
		if (bossL != L) { bossL = L; bossesSeen.clear(); }
		for (Mob m : mobs()) if (enemy(m) && Char.hasProp(m, Char.Property.BOSS) && !bossesSeen.contains(m)) bossesSeen.add(m);
		if (bossesSeen.isEmpty()) return false;
		for (Mob m : bossesSeen) if (m.isAlive()) return false;
		return true;
	}
	/** Turns still to walk on this floor: to the stairs if the floor is done, more if not. */
	static int floorNeed() {
		int ex = L.exit(); boolean way = ex >= 0 && ex < L.length() && known[ex] && walk(ex) < INF;
		boolean left = false; for (int c = 0; c < L.length() && !left; c++) if (dist[c] > 0 && dist[c] < INF && unseenBy(c)) left = true;
		return (way ? walk(ex) : 30) + (left ? FLOORNEED : 0);
	}

	// =============================================================================================================
	// resting on the stairs up (Pebble PD's bot): a long rest where a foe that turns up can be left behind (only allies
	// follow up the stairs: strategy-reference 2.3, Assist.upstairs), when the stairs are a short walk off.
	// =============================================================================================================
	static final boolean STAIRSREST = opt("BSTAIRSREST", true);
	static final int STAIRSWALK = optInt("BSTAIRSWALK", 12);   // steps walked to rest on the stairs
	static float stairsAt = -1; static int stairsCell = -1;

	/** The rest vetoed for the walk to the stairs up first (AsBasics.restStairs suggests it). */
	static boolean stairsRest(Target hk) {
		if (!STAIRSREST || Dungeon.depth <= 1 || Dungeon.bossLevel() || seenFoe || inDanger) return false;
		int ent = L.entrance(), hp = Dungeon.hero.pos;
		if (ent < 0 || ent >= L.length() || ent == hp || !known[ent] || walk(ent) > STAIRSWALK || bad.containsKey(ent)) return false;
		if (Dungeon.hero.belongings.getItem(com.shatteredpixel.shatteredpixeldungeon.items.Amulet.class) != null) return false;
		stairsAt = Actor.now(); stairsCell = ent;
		return true;
	}
	static Target restStairs() {
		if (stairsAt != Actor.now() || stairsCell < 0) return null;
		return new Target(stairsCell, GO, "hurt: rest on the stairs up (a way out if one comes)");
	}

	/** Each regular floor holds one food at least (Level.create: a FOOD item on every floor but the bosses', two on a
	 *  large one): picked up here yet? (What the hero carries, before and after: no peeking at the floor.) */
	static Level foodL; static int foodPack = -1; static boolean foodHere;
	static void foodSeen() {
		int pk = 0; for (Item i : Dungeon.hero.belongings) if (i instanceof Food && !(i instanceof Blandfruit) && !(i instanceof com.shatteredpixel.shatteredpixeldungeon.items.food.MysteryMeat)) pk += i.quantity();
		if (foodL != L) { foodL = L; foodHere = Dungeon.bossLevel() || Dungeon.branch != 0; foodPack = pk; return; }
		if (pk > foodPack) foodHere = true;
		foodPack = pk;
	}
	static final boolean FLOORFOOD = opt("BFLOORFOOD", true);

	/** Short of food: plan A on grass, a look round or a search is put off for the way on, when that's known. */
	static Target hurry(Target planA) {
		if (!FOOD || planA == null || !shortOfFood() || Dungeon.bossLevel()) return null;
		String w = planA.why;
		if (!(w.startsWith("look") || w.equals("grass") || w.startsWith("search") || w.startsWith("dead end"))) return null;
		int st = L.exit();
		if (st < 0 || st >= L.length() || !known[st] || walk(st) >= INF || L.map[st] == Terrain.LOCKED_EXIT) return null;
		if (w.startsWith("look") && !w.contains("grass") && FLOORFOOD && !foodHere) return null;   // (the floor's food not found yet: it is somewhere unexplored)
		if (w.startsWith("look") && !w.contains("grass")) {   // (a room left unexplored may hold food: only a far one is given up)
			int near = INF; for (int c = 0; c < L.length(); c++) if (dist[c] > 0 && dist[c] < near && unseenBy(c)) near = dist[c];
			if (near <= 12) return null;
		}
		return new Target(st, st == Dungeon.hero.pos ? PICKUP : GO, "short of food: on down");
	}
}
