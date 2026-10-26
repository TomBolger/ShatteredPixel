package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Eye;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTeleportation;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.ShadowCaster;
import com.watabou.utils.PathFinder;
import java.util.*;
import pebxel.Assist.Target;
import pebxel.Brain.Cand;
import static pebxel.Assist.*;

/**
 * The evil eyes (agent ST, for Rules' table). The deathgaze was the top killer on the way down from 11, 16 and 21
 * (CB-8: 52 deaths, many with the hero stepping "off the eye's beam" or fighting beside it). What the game does
 * (Eye.canAttack, doAttack, Hunting.act):
 *
 *  - an eye ready (no cooldown) that sees the hero (its field of view, and its beam's line reaching him, or beside him)
 *    charges: 2 turns, shown (the charge, aimed where he stood);
 *  - when it fires it aims AGAIN at the hero if it still sees him in a line: a step to the side in its sight is
 *    useless (the old rule's "off the eye's beam" step). Only out of its sight (round a corner; off a door, which shuts
 *    behind him and stops the beam) does it fire along the old line, past him;
 *  - charged, it takes a quarter of every blow; after the beam it can't fire for 4-6 of its turns, and comes to
 *    the hero and bites (20-30): the window to strike.
 *
 * So the rows: out of its sight while it's charged (a step or two: the beam comes after two); in its sight and ready,
 * with no cover a step or two off, to cover first; no walking back into a ready eye's sight from cover (it comes
 * round); at it between beams; and a walk in an eye's sight a step at a time (a charge seen mid-walk is a beam taken).
 * Only what the player sees: the eyes in sight, the charge, the beam fired (the charge gone), the known map.
 * AS_EYES=0 turns them off (and gives the eyes back to AsBolts and AsFire).
 */
final class AsEyes {
	private AsEyes() {}

	static final boolean ON = opt("EYES", true);
	static final int COOL = optInt("EYECOOL", 3), MEM = optInt("EYEMEM", 10), HIDEMAX = optInt("EYEHIDE", 4);
	static final boolean STEP = opt("EYESTEP", true), HIDE = opt("EYEHIDEON", true), HOLD = opt("EYEHOLD", true), CLOSE = opt("EYECLOSE", true), TELE = opt("EYETELE", true);
	static final double DODGEV = optInt("EYEDODGEV", 200);
	static final boolean DBGE = opt("EYEDBG", false);

	/** An eye as seen: where, charged or not, when it was last seen charged and when it fired (the charge gone). */
	static final class Seen { Mob m; int pos; float at, chargedAt = -99, firedAt = -99, chargeSeen = -99; boolean charged; }
	static final Map<Integer, Seen> seen = new LinkedHashMap<>();
	static Level seenL; static float noteT = -1; static int notePos = -1, noteDec = -1;

	static boolean awake(Mob m) { return m instanceof Eye && enemy(m) && m.state != m.SLEEPING && m.state != m.PASSIVE && !Dungeon.hero.isCharmedBy(m); }

	/** What the hero sees now, into the memory (once a decision). */
	static void note() {
		Hero h = Dungeon.hero; float now = Actor.now();
		if (seenL != L) { seenL = L; seen.clear(); }
		if (now == noteT && h.pos == notePos && Brain.decisions == noteDec) return;
		noteT = now; notePos = h.pos; noteDec = Brain.decisions;
		for (Mob m : mobs()) {
			if (!(m instanceof Eye) || !m.isAlive() || m.alignment != Char.Alignment.ENEMY || !L.heroFOV[m.pos] || m.invisible > 0) continue;
			if (!awake(m)) { seen.remove(m.id()); continue; }
			Seen s = seen.get(m.id()); if (s == null) { s = new Seen(); s.m = m; seen.put(m.id(), s); }
			boolean c = ((Eye) m).beamCharged;
			if (s.charged && !c && s.at >= now - 3) s.firedAt = Math.max(s.chargedAt, s.at);   // (charged when last seen, not now: it fired)
			if (c && !s.charged) s.chargeSeen = now;
			if (c) s.chargedAt = now;
			s.charged = c; s.pos = m.pos; s.at = now;
		}
		seen.values().removeIf(s -> !s.m.isAlive() || now - s.at > MEM || now < s.at || (s.at < now && L.heroFOV[s.pos] && !(seenChar(s.pos) == s.m)));
	}
	/** Can't fire for a while: it fired lately (4-6 of its turns' cooldown; COOL counted safe). */
	static boolean cooling(Seen s) { return !s.charged && Actor.now() - s.firedAt < COOL && Actor.now() >= s.firedAt; }
	static boolean ready(Seen s) { return !s.charged && !cooling(s); }

	// =============================================================================================================
	// what an eye sees and where its beam goes (the known map; the door the hero stands in shuts when he steps off)
	// =============================================================================================================
	static int heroDoor() {
		int hp = Dungeon.hero.pos;
		return L.map[hp] == Terrain.OPEN_DOOR && L.heaps.get(hp) == null ? hp : -1;
	}
	static final Map<Long, boolean[]> fovs = new HashMap<>(); static float fovT = -1; static int fovDec = -1;
	/** The eye's field of view from p (unknown cells taken as see-through), the hero's door shut or not. */
	static boolean[] fov(Mob m, int p, int door) {
		if (fovT != Actor.now() || fovDec != Brain.decisions) { fovs.clear(); fovT = Actor.now(); fovDec = Brain.decisions; }
		long key = (long) p * 100000L + (door + 1);
		boolean[] f = fovs.get(key);
		if (f != null) return f;
		int N = L.length(), W = L.width();
		boolean[] b = new boolean[N];
		for (int i = 0; i < N; i++) b[i] = (known[i] || L.heroFOV[i]) && L.losBlocking[i];
		if (door >= 0) b[door] = true;
		f = new boolean[N];
		try { ShadowCaster.castShadow(p % W, p / W, W, f, b, Math.max(1, m.viewDistance)); } catch (Throwable e) { Arrays.fill(f, true); }
		fovs.put(key, f);
		return f;
	}
	/** The eye's beam from p reaches c (Ballistica STOP_SOLID, the hero's door shut). */
	static boolean reach(int p, int c, int door) {
		boolean was = door >= 0 && L.solid[door];
		try {
			if (door >= 0) L.solid[door] = true;
			Ballistica b = new Ballistica(p, c, Ballistica.STOP_SOLID);
			return b.subPath(1, b.dist).contains(c);
		} catch (Throwable e) { return true; }
		finally { if (door >= 0) L.solid[door] = was; }
	}
	/** The old line: from p through t to the first solid. */
	static boolean onLine(int p, int t, int c, int door) {
		if (t < 0) return false;
		boolean was = door >= 0 && L.solid[door];
		try {
			if (door >= 0) L.solid[door] = true;
			Ballistica b = new Ballistica(p, t, Ballistica.STOP_SOLID);
			return b.subPath(1, b.dist).contains(c);
		} catch (Throwable e) { return true; }
		finally { if (door >= 0) L.solid[door] = was; }
	}
	/** An eye at p would aim at the hero standing at c (sees him, in its beam's line or beside it). */
	static boolean targets(Seen s, int p, int c) {
		if (p == c) return false;
		int door = c != Dungeon.hero.pos ? heroDoor() : -1;
		if (distance(p, c) > s.m.viewDistance) return false;
		if (!fov(s.m, p, door)[c]) return false;
		return adjacent(p, c) || reach(p, c, door);
	}
	/** A charged eye's beam would hit the hero at c: aimed at him again if it sees him, else along the old line. */
	static boolean hits(Seen s, int c) {
		if (targets(s, s.pos, c)) return true;
		Object bt = field(s.m, "beamTarget");
		int t = bt instanceof Integer ? (Integer) bt : -1;
		return onLine(s.pos, t, c, c != Dungeon.hero.pos ? heroDoor() : -1);
	}
	static double beamHi() { return 50 * (Assist.bag(com.shatteredpixel.shatteredpixeldungeon.items.Amulet.class) != null ? 1.1 : 1); }

	/** A cell to stand on: free, open, known, not in danger. */
	static boolean free(int c) { return c >= 0 && c < L.length() && open[c] && (known[c] || L.heroFOV[c]) && seenChar(c) == null && !danger[c] && !L.avoid[c] && L.passable[c]; }
	/** The cells within n steps over the free cells: steps to each (INF none). */
	static int[] steps(int n) {
		int N = L.length(), W = L.width(), hp = Dungeon.hero.pos; int[] d = new int[N]; Arrays.fill(d, INF);
		int[] q = new int[N]; int qh = 0, qt = 0; d[hp] = 0; q[qt++] = hp;
		while (qh < qt) { int u = q[qh++]; if (d[u] >= n) continue;
			for (int k : PathFinder.NEIGHBOURS8) { int v = u + k;
				if (v < 0 || v >= N || Math.abs(v % W - u % W) > 1 || d[v] < INF || !free(v)) continue;
				d[v] = d[u] + 1; q[qt++] = v; } }
		return d;
	}
	static List<Seen> charged() { List<Seen> l = new ArrayList<>(); float now = Actor.now(); for (Seen s : seen.values()) if (s.charged && s.at == now) l.add(s); return l; }
	static List<Seen> readyNow() { List<Seen> l = new ArrayList<>(); float now = Actor.now(); for (Seen s : seen.values()) if (ready(s) && s.at == now) l.add(s); return l; }
	static int melee(int c) { int n = 0; for (Mob m : mobs()) if (hostile(m) && !(m instanceof Eye) && adjacent(m.pos, c)) n++; return n; }

	// =============================================================================================================
	// the rows (Rules.TABLE)
	// =============================================================================================================
	/** PROPOSE: a charged eye's beam would hit the hero where he is: out of its sight, a step or two. */
	static boolean dodgeWhen(Cand c) {
		if (!ON || c != Rules.planA || Dungeon.hero.rooted) return false;
		note(); int hp = Dungeon.hero.pos;
		for (Seen s : charged()) if (hits(s, hp)) return true;
		return false;
	}
	static Target dodgeTarget() {
		List<Seen> ch = charged(), rd = readyNow(); Hero h = Dungeon.hero; int hp = h.pos; float now = Actor.now();
		boolean fresh = true; for (Seen s : ch) if (hits(s, hp) && s.chargeSeen < now) fresh = false;   // (seen charged before: one step left)
		int[] d = steps(fresh ? 2 : 1);
		int best = -1; double bk = Double.MAX_VALUE;
		for (int c = 0; c < L.length(); c++) {
			if (d[c] >= INF || c == hp) continue;
			boolean hit = false; for (Seen s : ch) if (hits(s, c)) { hit = true; break; }
			if (hit) continue;
			int r = 0; for (Seen s : rd) if (targets(s, s.pos, c)) r++;
			boolean nextTo = false; for (Seen s : ch) if (adjacent(s.pos, c)) nextTo = true;
			double k = d[c] * 10 + r * 6 + melee(c) * 4 + (L.water[c] ? 2 : 0) - (nextTo ? 1 : 0);
			if (k < bk) { bk = k; best = c; }
		}
		if (DBGE) System.out.println("    EYES dodge from " + hp + " fresh " + fresh + " -> " + best + " (" + ch.size() + " charged)");
		if (best < 0) {
			ScrollOfTeleportation st = bag(ScrollOfTeleportation.class);
			if (TELE && st != null && known(st) && !blind() && h.HP <= beamHi() * 1.2 && !declined(ScrollOfTeleportation.class, com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll.AC_READ)) {
				Target t = use(st, com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll.AC_READ, -1, "eye: away before its beam (no cover near)");
				t.hint = DODGEV; return t;
			}
			return null;
		}
		Target t = new Target(best, GO, "eye: out of its sight before it fires");
		t.hint = DODGEV;
		return t;
	}

	/** PROPOSE: a ready eye sees the hero (it charges on its turn) and there's no cover a step or two off: to cover now. */
	static boolean hideWhen(Cand c) {
		if (!ON || !HIDE || c != Rules.planA || Dungeon.hero.rooted || c.t == null) return false;
		if (c.t.why != null && c.t.why.startsWith("eye:")) return false;
		note(); int hp = Dungeon.hero.pos;
		List<Seen> rd = readyNow(); if (rd.isEmpty()) return false;
		boolean seenBy = false; for (Seen s : rd) if (targets(s, s.pos, hp)) seenBy = true;
		if (!seenBy) return false;
		if (c.t.kind == GO || c.t.kind == PICKUP) { int dst = c.t.cell; boolean ok = true; for (Seen s : rd) if (targets(s, s.pos, dst)) ok = false; if (ok && walk(dst) <= 2) return false; }   // (on its way out of sight already)
		return coverNear(rd, hp) < 0;
	}
	/** A cell within 2 steps of c out of every ready eye's sight (the escape after a charge), or -1. */
	static int coverNear(List<Seen> rd, int c0) {
		int hp = Dungeon.hero.pos; if (c0 != hp) return -1;
		int[] d = steps(2);
		for (int c = 0; c < L.length(); c++) { if (d[c] >= INF || c == hp) continue; boolean ok = true; for (Seen s : rd) if (targets(s, s.pos, c)) { ok = false; break; } if (ok) return c; }
		return -1;
	}
	static Target hideTarget() {
		List<Seen> rd = readyNow(); Hero h = Dungeon.hero; int hp = h.pos;
		int[] d = steps(HIDEMAX);
		int best = -1; double bk = Double.MAX_VALUE;
		for (int c = 0; c < L.length(); c++) {
			if (d[c] >= INF || c == hp) continue;
			boolean in = false; for (Seen s : rd) if (targets(s, s.pos, c)) { in = true; break; }
			if (in) continue;
			double k = d[c] * 10 + melee(c) * 8 + (L.water[c] ? 2 : 0);
			if (k < bk) { bk = k; best = c; }
		}
		if (best < 0) return null;
		Target t = new Target(best, GO, "eye: to cover before it charges (" + d[best] + " steps)");
		t.hint = 0.3 + Math.max(1, Brain.R) * Math.min(1, 40.0 / Math.max(1, h.HP)) * 0.6;
		return t;
	}

	/** VETO: a walk whose first step goes from out of a ready eye's sight into it (one seen lately, coming): it comes
	 *  round to the hero instead (held: hold). Not a walk at an eye between beams. */
	static final String NOSTEP = "eye: no step into a ready eye's sight";
	static boolean stepVeto(Cand c) {
		if (!ON || !HOLD) return false;
		Target t = c.t; if (t == null || t.cell < 0 || (t.kind != GO && t.kind != PICKUP)) return false;
		if (t.why != null && (t.why.startsWith("eye:") || t.why.startsWith("escape") || t.why.startsWith("ascent: out of the fight"))) return false;
		note(); if (seen.isEmpty()) return false;
		Hero h = Dungeon.hero; int hp = h.pos; if (h.rooted || t.cell == hp) return false;
		if (AsFire.stairsNear(t)) return false;
		List<Seen> rd = new ArrayList<>(); for (Seen s : seen.values()) if (ready(s)) rd.add(s);
		if (rd.isEmpty()) return false;
		for (Seen s : rd) if (targets(s, s.pos, hp)) return false;   // (in its sight already: the other rows)
		int first = firstStep(t.cell); if (first < 0) return false;
		for (Seen s : rd) if (targets(s, s.pos, first)) return true;
		return false;
	}
	/** VETO: a step from out of a charged eye's beam into it (K: dodged to the side, the next plan walked straight back). */
	static final String NOCHARGED = "eye: no step into a charged beam";
	static boolean chargedVeto(Cand c) {
		if (!ON) return false;
		Target t = c.t; if (t == null || t.cell < 0) return false;
		if (t.why != null && t.why.startsWith("eye: out of its sight")) return false;
		note(); List<Seen> ch = charged(); if (ch.isEmpty()) return false;
		Hero h = Dungeon.hero; int hp = h.pos; if (h.rooted || t.cell == hp) return false;
		int first;
		if (t.kind == GO || t.kind == PICKUP) first = adjacent(t.cell, hp) ? t.cell : firstStep(t.cell);
		else if (t.kind == ENEMY && t.action == null && !adjacent(t.cell, hp)) first = firstStep(t.cell);
		else return false;
		if (first < 0) return false;
		for (Seen s : ch) if (hits(s, first) && !hits(s, hp)) return true;
		return false;
	}
	static boolean holdWhen(Cand c) { return ON && HOLD && c == Rules.planA && c.veto && (c.note.contains(NOSTEP) || c.note.contains(NOCHARGED)); }
	static Target holdTarget() {
		Target t = new Target(Dungeon.hero.pos, WAIT, "eye: wait in cover (it must come round)"); t.action = WAIT_ACT; t.obj = -1;
		t.hint = 0.25;
		return t;
	}

	/** PROPOSE: an eye that fired lately (it can't again for a few turns), near: at it. */
	static boolean closeWhen(Cand c) {
		if (!ON || !CLOSE || c != Rules.planA || c.t == null) return false;
		if (c.t.kind == ENEMY && c.t.why != null && !c.t.why.startsWith("eye")) { Char at = seenChar(c.t.cell); if (at instanceof Eye) return false; }
		note(); return closeEye() != null;
	}
	static Seen closeEye() {
		float now = Actor.now(); Seen b = null; int bw = INF;
		for (Seen s : seen.values()) if (s.at == now && cooling(s)) { int w = walk(s.pos); int left = (int) Math.ceil(COOL - (now - s.firedAt)); if (w <= left + 2 && w < bw) { bw = w; b = s; } }
		return b;
	}
	static Target closeTarget() {
		Seen s = closeEye(); if (s == null) return null;
		Target t = new Target(s.pos, ENEMY, "eye: at it between beams");
		t.hint = 0.3;
		return t;
	}

	/** The first step of the walk to c (over the known passable cells, the cells with someone in them barred). */
	static int firstStep(int c) {
		int hp = Dungeon.hero.pos; if (c == hp || c < 0) return -1;
		int[] d = AsFire.from(c); if (d[hp] >= INF) return -1;
		int best = -1;
		for (int k : PathFinder.NEIGHBOURS8) { int n = hp + k;
			if (n < 0 || n >= L.length() || !adjacent(n, hp) || d[n] != d[hp] - 1) continue;
			if (n != c && seenChar(n) != null) continue;
			if (best < 0 || (danger[best] && !danger[n])) best = n; }
		return best;
	}

	/** After the brain: a walk of more than a step with an eye awake in sight goes a step at a time (the hero walking is
	 *  deaf to a charge: the game stops a walk for a foe come into view or a blow, not for a charge). */
	static void stepwise(List<Target> out) {
		if (!ON || !STEP || out == null || out.isEmpty()) return;
		try {
			Target t = out.get(0);
			if ((t.kind != GO && t.kind != PICKUP) || t.cell < 0) return;
			if (t.why != null && t.why.startsWith("eye: out of its sight")) return;   // (the dodge: both steps before the beam)
			Hero h = Dungeon.hero; int hp = h.pos;
			if (t.cell == hp || adjacent(t.cell, hp)) return;
			boolean eye = false; for (Mob m : mobs()) if (awake(m) && distance(m.pos, hp) <= m.viewDistance + 2) eye = true;
			if (!eye) return;
			int n = firstStep(t.cell); if (n < 0) return;
			if (t.obj < 0 || t.obj == t.cell) t.obj = t.cell;
			t.cell = n; t.kind = GO;
			stepped++;
		} catch (Throwable e) { lastError = e; }
	}
	static int stepped;

	static boolean on() { return ON; }
	static void reset() { seen.clear(); seenL = null; noteT = -1; notePos = -1; fovs.clear(); }
}
