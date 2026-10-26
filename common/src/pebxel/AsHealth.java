package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.NPC;
import com.shatteredpixel.shatteredpixeldungeon.items.Amulet;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import java.util.*;
import pebxel.Assist.Target;
import pebxel.bot.FightModel;
import pebxel.bot.Fighter;
import pebxel.bot.FoeView;
import pebxel.bot.HeroView;
import static pebxel.Assist.*;

/**
 * The health policy (agent EG): the honest early game's caution, as one last pass over plan A (Assist.planList).
 *
 * Forensics (BASE1, 96 honest games, 38 dead in the sewers): the fatal fights nearly all began at full health. They
 * were lost to where and whether the hero fought, not to how hurt he was when he started: two or three foes met in the
 * open (snakes, rats, a gnoll pack), a fight the model already called lost while the stairs were a walk away. So:
 *  - the corridor: two or more coming, the hero in the open, a cell a few steps back where they reach him one at a
 *    time and the model says the fight costs clearly less there: back to it, and hold it while they come;
 *  - the stairs: a fight the model calls lost, the stairs up reachable before them: up (they can't follow; Brain's
 *    "fled: rest before going back" rests on the floor above).
 * Every one is a suggestion with its why, for the assistant as for the bot.
 */
final class AsHealth {
	private AsHealth() {}

	static final boolean DBG = "1".equals(System.getenv("EGDBG"));
	static final boolean ON = opt("EGHEALTH", true), POS = opt("EGPOS", true), FLEE = opt("EGFLEE", true);
	static final int POSGAIN = optInt("EGPOSGAIN", 25);     // percent less cost (death odds weighed in) for the corridor to be worth the steps
	static final int FLEEPD = optInt("EGFLEEPD", 30);       // death odds (percent) at which the stairs up are taken
	static final int FLEEWALK = optInt("EGFLEEWALK", 30);   // the farthest walk to them
	static final int RUNS = optInt("EGRUNS", 120);
	static float fledAt = -99; static Level fledL; static int posGoal = -1; static float posUntil = -1; static Level posL;

	/** The list as the rules made it; entry 0 may be replaced. */
	static void after(List<Target> out) {
		try {
			Hero H = Dungeon.hero; int hp = H.pos;
			if (DBG && !out.isEmpty()) {
				List<Mob> fs = oFightList();
				if (!fs.isEmpty()) {
					FightModel.Result r = FightModel.engaged(H, fs);
					StringBuilder b = new StringBuilder();
					for (Mob m : fs) b.append(m.getClass().getSimpleName()).append(':').append(m.HP).append('@').append(distance(m.pos, hp)).append(hunting(m) ? "h" : "").append(m instanceof Goo ? "p" + field(m, "pumpedUp") + (gooReach(m.pos, hp) ? "R" : "") : "").append(',');
					System.out.println(String.format(Locale.ROOT, "EGF t %.0f d %d hp %d/%d up %d slots %d pd %.2f loss %.1f p90 %.0f | %s | A %s", Actor.now(), Dungeon.depth, H.HP, H.HT, walk(L.entrance()), effSlots(hp, fs), r == null ? -1 : r.pDeath, r == null ? -1 : r.lossMean, r == null ? -1 : r.loss90, b, out.get(0).why));
				}
			}
			if (ON && GOO && !out.isEmpty() && !H.rooted) { Target g = gooGuard(H); if (g != null) { out.add(0, g); if (DBG) System.out.println("EGH t " + Actor.now() + " " + g.why); return; } }
			if (ON && FIRE && !out.isEmpty()) { Target f = fire(H, out.get(0)); if (f != null) { out.add(0, f); return; } }
			if (ON && BOSSREST && !out.isEmpty()) { Target b = bossRest(H, out.get(0)); if (b != null) { out.add(0, b); return; } }
			if (!ON || out.isEmpty() || H.rooted || L.locked || Dungeon.bossLevel() || bag(Amulet.class) != null) return;
			Target a = out.get(0);
			if (a.why != null && (a.why.startsWith("escape") || a.why.startsWith("rescue") || a.why.contains("heal") || a.why.startsWith("dying"))) return;   // (the rules' own way out stands)
			Target t = lasher(H);
			if (t == null && FLEE) t = flee(H, a);
			if (t == null && GATE > 0) t = gate(H, a);
			if (t == null && POS) t = position(H, a);
			if (t != null) { out.add(0, t); if (DBG) System.out.println("EGH t " + Actor.now() + " " + t.why + " -> " + t.cell + " (over " + a.why + ")"); }
		} catch (Throwable e) { lastError = e; if (DBG) e.printStackTrace(System.out); }
	}

	/** The foes in the fight that walk up to the hero (not shooters' bolts, not sleepers, not piranhas, not NPCs). */
	static List<Mob> walkers(Hero H) {
		List<Mob> fs = new ArrayList<>();
		for (Mob m : mobs()) if (enemy(m) && (hunting(m) || (adjacent(m.pos, H.pos) && m.state != m.SLEEPING)) && m.state != m.PASSIVE && !(m instanceof NPC) && !(m instanceof Piranha) && walk(m.pos) <= 10) fs.add(m);
		return fs;
	}
	static boolean anyShooter(List<Mob> fs) { for (Mob m : fs) if (FoeView.shooter(m)) return true; return false; }

	// ---------------------------------------------------------------------------------------------------------
	// the corridor
	// ---------------------------------------------------------------------------------------------------------
	/** The cells beside c the foes can stand on to strike the hero there (reached without going through c). */
	static int effSlots(int c, List<Mob> fs) {
		int N = L.length();
		boolean[] seen = new boolean[N]; ArrayDeque<Integer> q = new ArrayDeque<>();
		for (Mob m : fs) if (m.pos >= 0 && m.pos < N && m.pos != c) { seen[m.pos] = true; q.add(m.pos); }
		seen[c] = true;
		while (!q.isEmpty()) { int u = q.poll();
			for (int k = 0; k < 8; k++) { int v = u + N8[k]; if (!nb(u, v) || seen[v] || !L.passable[v] || !(known[v] || L.heroFOV[v])) continue; seen[v] = true; q.add(v); } }
		int n = 0;
		for (int k = 0; k < 8; k++) { int v = c + N8[k]; if (nb(c, v) && L.passable[v] && seen[v]) n++; }
		return n;
	}
	/** A foe's walk to c (the known passable cells; the others in the way are no wall), INF none. */
	static int foeWalk(Mob m, int c) {
		int N = L.length(); int[] d = new int[N]; Arrays.fill(d, INF); ArrayDeque<Integer> q = new ArrayDeque<>();
		d[m.pos] = 0; q.add(m.pos);
		while (!q.isEmpty()) { int u = q.poll(); if (u == c) return d[u]; if (d[u] > 40) continue;
			for (int k = 0; k < 8; k++) { int v = u + N8[k]; if (!nb(u, v) || d[v] <= d[u] + 1 || !L.passable[v] || !(known[v] || L.heroFOV[v])) continue; d[v] = d[u] + 1; q.add(v); } }
		return INF;
	}
	static FightModel.Result sim(Hero H, List<Mob> fs, int at, int steps) {
		Fighter me = HeroView.of(H); me.slots = Math.max(1, effSlots(at, fs));
		List<Fighter> ff = new ArrayList<>();
		for (Mob m : fs) { Fighter f = FoeView.of(m, H); int d = at == H.pos ? walk(m.pos) : foeWalk(m, at); if (d >= INF) d = distance(m.pos, at) + 4;
			f.dist = Math.max(1, (int) Math.round(d - steps * f.speed)); ff.add(f); }
		return FightModel.simulate(me, ff, 0, RUNS, 0x5EEDL ^ (Dungeon.depth * 7919L) ^ H.pos);
	}
	static double score(FightModel.Result r, Hero H) { return r.pDeath * H.HP * 4 + r.lossMean; }

	static Target position(Hero H, Target a) {
		int hp = H.pos;
		if (posL != L) { posL = L; posGoal = -1; }
		List<Mob> fs = walkers(H);
		if (fs.isEmpty() || anyShooter(fs)) { posGoal = -1; return null; }
		boolean adj = false; for (Mob m : fs) if (adjacent(m.pos, hp)) adj = true;
		int cur = effSlots(hp, fs);
		// the corridor reached: held while they come (the plan would walk out to meet them)
		if (posGoal == hp && Actor.now() <= posUntil && !adj && cur <= 1 && (a.kind == GO || a.kind == PICKUP || (a.kind == ENEMY && a.action == null))) {
			Target w = new Target(hp, WAIT, "corridor: hold it, they come one at a time"); w.action = WAIT_ACT; return w;
		}
		if (posGoal >= 0 && posGoal != hp && Actor.now() <= posUntil && walk(posGoal) < INF && walk(posGoal) <= 4) {   // (on the way back to it)
			boolean ok = true; for (Mob m : fs) if (foeWalk(m, posGoal) <= walk(posGoal)) ok = false;
			if (ok) return new Target(posGoal, GO, "corridor: back to where they come one at a time");
		}
		if (posGoal != hp) posGoal = -1;
		if (cur <= 1 || fs.size() < 2) return null;
		FightModel.Result here = sim(H, fs, hp, 0);
		if (here.pDeath < 0.01 && here.lossMean < H.HP * 0.15) return null;   // (an easy fight: where it is)
		int maxStep = 4;   // (stepping away from a foe as fast as it costs no blow: it follows, its turn spent)
		int best = -1; FightModel.Result br = null; double s0 = score(here, H), bs = s0;
		for (int c = 0; c < L.length(); c++) {
			int k = dist[c]; if (k <= 0 || k > maxStep || !open[c] || danger[c] || seenChar(c) != null || L.water[c]) continue;
			int e = effSlots(c, fs); if (e >= cur || e > 2) continue;
			boolean first = true; for (Mob m : fs) if (foeWalk(m, c) <= k) { first = false; break; }   // (the hero gets there before them, not through them)
			if (!first) continue;
			FightModel.Result r = sim(H, fs, c, k);
			double s = score(r, H) + k * 0.3;
			if (s < bs) { bs = s; best = c; br = r; }
		}
		if (DBG) System.out.println(String.format(Locale.ROOT, "EGPOS? t %.0f fs %d cur %d here pd %.2f loss %.1f s0 %.1f best %d bs %.1f", Actor.now(), fs.size(), cur, here.pDeath, here.lossMean, s0, best, bs));
		if (best < 0 || br == null) return null;
		if (!(bs <= s0 * (1 - POSGAIN / 100.0) && s0 - bs >= 2)) return null;
		posGoal = best; posUntil = Actor.now() + 15;
		if (DBG) System.out.println(String.format(Locale.ROOT, "EGPOS here pd %.2f loss %.1f slots %d | at %d pd %.2f loss %.1f slots %d", here.pDeath, here.lossMean, cur, best, br.pDeath, br.lossMean, effSlots(best, fs)));
		return new Target(best, GO, String.format(Locale.ROOT, "corridor: back where they come one at a time (%d foes)", fs.size()));
	}

	// ---------------------------------------------------------------------------------------------------------
	// Goo's pumped blast (3x damage, 2 cells): out of its reach, whatever else the rules say (BASE1 g48: the step
	// out began, the next decision held a doorway still in its reach, 11 of 55 health in one blow)
	// ---------------------------------------------------------------------------------------------------------
	static final boolean GOO = opt("EGGOO", true);
	static Target gooGuard(Hero H) {
		int hp = H.pos;
		for (Mob m : mobs()) {
			if (!(m instanceof Goo) || !enemy(m) || !pumped(m) || !gooReach(m.pos, hp)) continue;
			Object pv = field(m, "pumpedUp"); int maxW = pv instanceof Integer && (Integer) pv >= 2 ? 1 : 2;   // (pumped twice: the blast comes after one move)
			int best = -1, bk = 99;
			for (int c = 0; c < L.length(); c++) { int w = walk(c); if (w < 1 || w > maxW || !standOk(c) || gooReach(m.pos, c) || danger[c]) continue;
				int sc = w * 10 + (L.water[c] ? 1 : 0); if (sc < bk) { bk = sc; best = c; } }
			if (DBG) { StringBuilder b = new StringBuilder(); for (int c = 0; c < L.length(); c++) if (distance(c, hp) <= 2 && c != hp) b.append(c).append(':').append(walk(c)).append(standOk(c) ? "s" : "").append(gooReach(m.pos, c) ? "R" : "").append(danger[c] ? "D" : "").append(L.passable[c] ? "" : "#").append(' '); System.out.println("EGGOO hp " + hp + " goo " + m.pos + " maxW " + maxW + " best " + best + " | " + b); }
			if (best >= 0) return new Target(best, GO, "Goo pumps up: out of its reach (the blast is triple)");
		}
		return null;
	}

	// ---------------------------------------------------------------------------------------------------------
	// rot lashers (the Wandmaker's garden; 3-4 honest deaths a run): they can't move, bite for 10-20 at accuracy 25 and
	// heal 5 a turn when no one is beside them. Their reach is a hazard on the plan's map (walked round, not into), and
	// one beside the hero is stepped away from unless the model says it's a cheap kill.
	// ---------------------------------------------------------------------------------------------------------
	static final boolean LASH = opt("EGLASH", true);
	static final Set<Integer> lashers = new HashSet<>(); static Level lashL; static float lashAt = -1; static boolean[] lashZone = new boolean[0];
	static boolean danger(int c) {
		if (!LASH) return false;
		Level l = Dungeon.level; if (l == null) return false;
		if (lashL != l) { lashL = l; lashers.clear(); lashAt = -1; }
		if (lashAt != Actor.now() || lashZone.length != l.length()) {
			lashAt = Actor.now();
			for (Mob m : l.mobs) if (m instanceof RotLasher && m.isAlive() && l.heroFOV[m.pos]) lashers.add(m.pos);
			lashers.removeIf(p -> l.heroFOV[p] && !(Actor.findChar(p) instanceof RotLasher));
			if (lashZone.length != l.length()) lashZone = new boolean[l.length()]; else Arrays.fill(lashZone, false);
			for (int p : lashers) for (int k = 0; k < 8; k++) { int n = p + N8[k]; if (nb(p, n)) lashZone[n] = true; }
		}
		return c >= 0 && c < lashZone.length && lashZone[c];
	}
	static Target lasher(Hero H) {
		if (!LASH) return null;
		List<Mob> adj = new ArrayList<>();
		for (Mob m : mobs()) if (m instanceof RotLasher && enemy(m) && adjacent(m.pos, H.pos)) adj.add(m);
		if (adj.isEmpty()) return null;
		try { FightModel.Result r = FightModel.evaluate(H, adj); if (r.pDeath <= 0.01 && r.loss90 <= H.HP * 0.3) return null; } catch (Throwable e) { }
		int best = -1, bd = 9;
		for (int c = 0; c < L.length(); c++) { int w = walk(c); if (w < 1 || w >= bd || !L.passable[c] || L.avoid[c] || seenChar(c) != null || lashZone.length == L.length() && lashZone[c]) continue; bd = w; best = c; }
		return best >= 0 ? new Target(best, GO, "rot lasher: out of its reach (it can't move, and heals)") : null;
	}

	// ---------------------------------------------------------------------------------------------------------
	// a boss floor before the boss: rested to full (of 14 Goo deaths replayed, 6 met it at 35-80% health; it seals the
	// floor once awake, and a sealed floor stops regeneration)
	// ---------------------------------------------------------------------------------------------------------
	static final boolean BOSSREST = opt("EGBOSSREST", true);
	static final int BOSSPCT = optInt("EGBOSSPCT", 90);
	static Target bossRest(Hero H, Target a) {
		if (!Dungeon.bossLevel() || L.locked || bag(Amulet.class) != null || H.HP * 100 >= H.HT * BOSSPCT || seenFoe || threatNear || starving() || declined(null, REST_ACT)) return null;
		if (!(a.kind == GO || a.kind == PICKUP || a.kind == ENEMY && a.action == null || (a.kind == WAIT && !REST_ACT.equals(a.action)))) return null;
		for (Mob m : mobs()) if (enemy(m) && m.state != m.SLEEPING) return null;
		if (AsBasics.foodTurns() < AsBasics.restTurns() + 30) return null;   // (the rest mustn't starve him)
		Target r = new Target(H.pos, WAIT, String.format(Locale.ROOT, "boss floor: rest to full first (%d/%d); it seals the floor when it wakes", H.HP, H.HT)); r.action = REST_ACT; r.obj = -1;
		return r;
	}

	// ---------------------------------------------------------------------------------------------------------
	// fire: the magical fire room's flames set alight whoever stands beside them (EternalFire), and burning eats
	// health every turn. BASE1/EG runs: "escape the fire" stepped between two cells both beside the flames, and the
	// hands rested there, burning, from 14 health to 1. Off any cell burning or beside eternal fire, first.
	// ---------------------------------------------------------------------------------------------------------
	static final boolean FIRE = opt("EGFIRE", true);
	static boolean efire(int c) {
		com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob b = L.blobs.get(com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.MagicalFireRoom.EternalFire.class);
		return b != null && b.volume > 0 && b.cur != null && c >= 0 && c < L.length() && b.cur[c] > 0 && (L.heroFOV[c] || known[c]);
	}
	static boolean nearEfire(int c) { if (efire(c)) return true; for (int k = 0; k < 8; k++) { int n = c + N8[k]; if (nb(c, n) && efire(n)) return true; } return false; }
	static Target fire(Hero H, Target a) {
		int hp = H.pos;
		boolean burn = burning(), ef = nearEfire(hp);
		if (!ef && !(burn && (a.kind == WAIT || a.kind == PICKUP || a.kind == USE && a.item != null && !(a.item instanceof com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion)))) return null;
		int best = -1, bs = INF;
		for (int c = 0; c < L.length(); c++) { int w = walk(c); if (w < 1 || w > 12 || !L.passable[c] || L.avoid[c] || seenChar(c) != null || nearEfire(c) || fireNear(c)) continue;
			int sc = w * 2 + (L.flamable[c] ? 6 : 0) - (burn && L.water[c] ? 8 : 0); if (sc < bs) { bs = sc; best = c; } }
		if (best < 0) return null;
		if ((a.kind == GO || a.kind == PICKUP) && a.cell >= 0 && !nearEfire(a.cell) && !fireNear(a.cell) && !(burn && a.kind == PICKUP)) return null;   // (the plan's own step is off the flames)
		return new Target(best, GO, burn ? "burning: to water or bare floor, off the flames" : "fire: off the cells beside the flames (they set you alight)");
	}

	// ---------------------------------------------------------------------------------------------------------
	// the gate: low, nothing about, the food for it: rest before going on (exploring, picking up, the stairs down)
	// ---------------------------------------------------------------------------------------------------------
	static final int GATE = optInt("EGGATE", 45);
	static Target gate(Hero H, Target a) {
		if (H.HP * 100 >= H.HT * GATE || seenFoe || threatNear || starving() || declined(null, REST_ACT)) return null;
		if (!(a.kind == GO || a.kind == PICKUP || (a.kind == WAIT && !REST_ACT.equals(a.action)))) return null;
		Target r = new Target(H.pos, WAIT, String.format(Locale.ROOT, "health: rest before going on (%d/%d)", H.HP, H.HT)); r.action = REST_ACT; r.obj = -1;
		if (AsBasics.restUnaffordable(r) || Rules.vetoed(new Brain.Cand(r, "health", Brain.role(r)))) return null;
		return r;
	}

	// ---------------------------------------------------------------------------------------------------------
	// the stairs
	// ---------------------------------------------------------------------------------------------------------
	static Target flee(Hero H, Target a) {
		int hp = H.pos;
		if (Dungeon.depth <= 1 || Clues.healing() != null) return null;   // (a known Healing: the hands drink it)
		if (fledL != null && fledL != L && Actor.now() - fledAt < 40 && Actor.now() >= fledAt) return null;   // (just come here by the stairs: no going straight back)
		int ent = L.entrance(); if (ent < 0 || ent >= L.length() || !known[ent] || walk(ent) >= INF || walk(ent) > FLEEWALK || bad.containsKey(ent)) return null;
		List<Mob> fs = new ArrayList<>();
		for (Mob m : mobs()) if (enemy(m) && hunting(m) && m.state != m.PASSIVE && !(m instanceof NPC) && !(m instanceof Piranha)) fs.add(m);
		if (fs.isEmpty()) return null;
		FightModel.Result r = FightModel.engaged(H, oFightList());
		if (r == null || r.pDeath * 100 < FLEEPD) return null;
		int k = walk(ent);
		for (Mob m : fs) {   // (they mustn't be there first, nor on the way: a faster one closes the gap)
			int d = foeWalk(m, ent); double sp = 1; try { sp = m.speed(); } catch (Throwable e) { }
			if (d < INF && d / sp <= k) return null;
			if (sp > 1.01 && adjacent(m.pos, hp) && k > 2) return null;
		}
		if (hp == ent) { fledAt = Actor.now(); fledL = L; }
		return new Target(ent, hp == ent ? PICKUP : GO, String.format(Locale.ROOT, "losing (%.0f%% to die): up the stairs, they can't follow", r.pDeath * 100));
	}
}
