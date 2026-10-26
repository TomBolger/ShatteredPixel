package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.NPC;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.watabou.utils.PathFinder;
import java.util.*;
import pebxel.Assist.Target;
import pebxel.Brain.Cand;
import pebxel.bot.*;
import static pebxel.Assist.*;

/**
 * Lines of fire (agent CB, for Rules' table). Bolts were the top killer of every run (BASE1: 44 of the 63 deaths on the
 * climb, where a DM-100 zaps for 13-45 through any armour, a shaman for 13-34, a warlock for 18-27). The forensics of
 * 28 of those deaths (seed replays) found two failures, not a want of tactics:
 *
 *  - ping-pong (10 of 28): the corner tactic stepped out of the caster's line; out of sight, the caster was forgotten,
 *    and the next plan (the stairs, resume, a pick-up) stepped straight back into the line. A bolt every other turn
 *    till death, the caster never needing to come;
 *  - fighting in its line (11 of 28): beside a melee foe the hero struck where he stood while one or more casters
 *    behind it bolted him every turn: DM-100s over a skeleton, a warlock over a monk, 200 turns in one place.
 *
 * So the casters seen are remembered where they were last seen (only what the player saw), with what a bolt of theirs
 * takes; the cells they have a line on are "in the fire". The rows:
 *
 *  - no step into the fire: a walk whose first step goes from out of every line into one is held back; the hero waits
 *    where he is, out of its line (it must come round, and is met beside him);
 *  - no waiting in the fire, and out of it: a wait or a walk in a line (no foe beside him, no caster tactic at work)
 *    gives way to the nearest cell out of every line;
 *  - the fight out of the fire: beside a foe and in a caster's line, a step to a cell beside it out of every line (the
 *    foe follows, the bolts can't).
 *
 * AS_FIRE=0 turns them all off; AS_FIREMEM (turns a caster out of sight is remembered, default 10).
 */
final class AsFire {
	private AsFire() {}

	static final boolean ON = opt("FIRE", true);
	static final int MEM = optInt("FIREMEM", 10), RANGE = optInt("FIRERANGE", 9);
	static final boolean STEP = opt("FIRESTEP", true), FIGHT = opt("FIREFIGHT", true), OUT = opt("FIREOUT", true);

	/** A shooter as last seen. comes: it walks round to the hero when out of its line (not a scorpio, which backs off,
	 *  nor anything rooted). */
	static final class Seen { Mob m; int pos; float at; double dmg; boolean eye, comes, hunting; }
	static final Map<Integer, Seen> seen = new LinkedHashMap<>();
	static Level seenL; static float noteT = -1; static int notePos = -1, noteDec = -1;

	static boolean shooter(Mob m) {
		return m instanceof DM100 || m instanceof Shaman || m instanceof Warlock || m instanceof Eye || m instanceof Scorpio || m instanceof GnollTrickster
				|| m instanceof Elemental || m instanceof FungalSentry;
	}
	static boolean comes(Mob m) { return (m instanceof DM100 || m instanceof Shaman || m instanceof Warlock || m instanceof Eye) && !Char.hasProp(m, Char.Property.IMMOVABLE); }

	/** A bolt's mean (the Amulet's curse in it: FoeView); an eye's beam a third (one in 4-7 turns); an elemental's a quarter (it mostly strikes). */
	static double boltOf(Mob m) {
		try {
			Fighter f = FoeView.of(m, Dungeon.hero);
			double d = f.zapDmg != null ? f.zapDmg.mean() : f.burstDmg != null ? f.burstDmg.mean() / 3 : f.rangedDmg != null ? f.rangedDmg.mean() : f.dmg.mean();
			if (m instanceof Elemental) d /= 4;
			return d;
		} catch (Throwable e) { return 10; }
	}

	/** What the hero sees now, into the memory (once a decision). */
	static void note() {
		Hero h = Dungeon.hero; float now = Actor.now();
		if (seenL != L) { seenL = L; seen.clear(); }
		if (now == noteT && h.pos == notePos && Brain.decisions == noteDec) return;
		noteT = now; notePos = h.pos; noteDec = Brain.decisions;
		for (Mob m : mobs()) {
			if (!shooter(m) || m instanceof NPC || !m.isAlive() || m.alignment != Char.Alignment.ENEMY || !L.heroFOV[m.pos] || m.invisible > 0) continue;
			if (m.state == m.SLEEPING || m.state == m.PASSIVE || h.isCharmedBy(m)) { seen.remove(m.id()); continue; }
			Seen s = seen.get(m.id()); if (s == null) { s = new Seen(); s.m = m; s.dmg = boltOf(m); s.eye = m instanceof Eye; s.comes = comes(m); seen.put(m.id(), s); }
			s.pos = m.pos; s.at = now; s.hunting = hunting(m);
		}
		// forgotten: long out of sight, or its last place in view with nobody there (it moved: where to, the hero can't say)
		seen.values().removeIf(s -> now - s.at > MEM || now < s.at || (s.at < now && L.heroFOV[s.pos] && !(seenChar(s.pos) instanceof Mob && ((Mob) seenChar(s.pos)).id() == s.m.id())));
	}

	/** s has a line on c (its bolt stops at the first one in the way the hero can see, the hero himself not: he's the one
	 *  moving; an eye's gaze goes through). */
	static boolean fireLine(Seen s, int c) {
		if (s.pos == c) return false;
		int d = distance(s.pos, c); if (d <= 1 || d > RANGE) return false;
		if (s.m instanceof Scorpio && d <= 1) return false;
		try {
			Ballistica b = new Ballistica(s.pos, c, Ballistica.STOP_SOLID | Ballistica.STOP_TARGET);
			if (b.collisionPos == null || b.collisionPos != c) return false;
			if (s.eye) return true;
			int hp = Dungeon.hero.pos;
			for (int x : b.subPath(1, b.dist - 1)) { if (x == hp || x == s.pos) continue; Char ch = seenChar(x); if (ch != null && ch != s.m) return false; }
			return true;
		} catch (Throwable e) { return false; }
	}
	/** The bolts a turn at c from the shooters remembered (comers: only those that would come round). */
	static double fireAt(int c, boolean comers) {
		double f = 0;
		for (Seen s : seen.values()) { if (comers && !s.comes) continue; if (fireLine(s, c)) f += s.dmg; }
		return f;
	}
	static boolean any(boolean comers) { for (Seen s : seen.values()) if (!comers || s.comes) return true; return false; }
	static boolean adjFoe() { int hp = Dungeon.hero.pos; for (Mob m : mobs()) if (hostile(m) && adjacent(m.pos, hp)) return true; return false; }

	/** Steps to c over the passable cells known (no limit, PathFinder's arrays untouched). */
	static int[] from(int c) {
		int N = L.length(), W = L.width(); int[] d = new int[N]; Arrays.fill(d, INF); int[] q = new int[N]; int qh = 0, qt = 0;
		d[c] = 0; q[qt++] = c;
		while (qh < qt) { int u = q[qh++];
			for (int k : PathFinder.NEIGHBOURS8) { int v = u + k;
				if (v < 0 || v >= N || Math.abs(v % W - u % W) > 1 || d[v] < INF || !L.passable[v] || !(known[v] || L.heroFOV[v])) continue;
				d[v] = d[u] + 1; q[qt++] = v; } }
		return d;
	}
	/** Every first step of the shortest walks to c is in the fire of a caster that would come. */
	static boolean stepsInFire(int c) {
		int hp = Dungeon.hero.pos; if (c == hp || c < 0) return false;
		int[] d = from(c); if (d[hp] >= INF) return false;
		boolean anyStep = false;
		for (int k : PathFinder.NEIGHBOURS8) { int n = hp + k;
			if (n < 0 || n >= L.length() || !adjacent(n, hp) || d[n] != d[hp] - 1) continue;
			if (n != c && seenChar(n) != null) continue;
			anyStep = true;
			if (fireAt(n, true) <= 0) return false;
		}
		return anyStep;
	}

	/** The plans that answer a caster themselves (theirs to walk into a line, or out of it). */
	static boolean own(Target t) {
		String w = t.why == null ? "" : t.why;
		return w.startsWith("caster") || w.startsWith("bolts") || w.startsWith("fire") || w.startsWith("escape") || w.startsWith("boss") || w.startsWith("tactic")
				|| w.startsWith("ascent: out of the fight") || w.startsWith("ascent: down") || w.startsWith("rescue") || w.startsWith("your ");
	}
	/** A walk ending at the stairs a step or two off (the floor left behind, and its casters). */
	static boolean stairsNear(Target t) {
		int c = t.obj >= 0 ? t.obj : t.cell;
		return (c == L.entrance() || c == L.exit()) && walk(c) <= 2;
	}

	// =============================================================================================================
	// the rows (Rules.TABLE)
	// =============================================================================================================
	static final String NOSTEP = "fire: no step into a line";
	/** VETO: a walk out of every line whose first step is into a coming caster's. */
	static boolean stepVeto(Cand c) {
		if (!ON || !STEP) return false;
		Target t = c.t; if (t == null || (t.kind != GO && t.kind != PICKUP) || t.cell < 0) return false;
		note(); if (!any(true)) return false;
		int hp = Dungeon.hero.pos;
		if (t.cell == hp || own(t) || stairsNear(t)) return false;
		if (Dungeon.hero.rooted || fireAt(hp, false) > 0) return false;   // (already in a line: the other rows' business)
		for (Mob m : mobs()) if (hostile(m) && adjacent(m.pos, t.cell) && comes(m)) return false;   // (closing in on the caster itself)
		return stepsInFire(t.cell);
	}
	/** PROPOSE: plan A held back by that: wait where he is, out of its line. */
	static boolean holdWhen(Cand c) { return ON && STEP && c == Rules.planA && c.veto && c.note.contains(NOSTEP); }
	static Target holdTarget() {
		Target t = new Target(Dungeon.hero.pos, WAIT, "fire: wait out of its line (it must come round)"); t.action = SEARCH_ACT; t.obj = -1;
		t.hint = 0.25;
		return t;
	}

	/** PROPOSE: plan A leaves the hero in a line (a wait or a walk, no foe beside him, no caster plan at work): out of it. */
	static boolean outWhen(Cand c) {
		if (!ON || !OUT || c != Rules.planA || c.t == null || Dungeon.hero.rooted) return false;
		Target t = c.t;
		if (t.kind == ENEMY || t.kind == USE || own(t) || (REST_ACT.equals(t.action) && false)) return false;
		note(); if (seen.isEmpty() || adjFoe()) return false;
		int hp = Dungeon.hero.pos;
		if (fireAt(hp, false) <= 0) return false;
		if ((t.kind == GO || t.kind == PICKUP) && stairsNear(t)) return false;
		if ((t.kind == GO || t.kind == PICKUP) && t.cell >= 0 && t.cell != hp && !stepsInFire(t.cell) && fireAt(t.cell, false) <= 0 && walk(t.cell) <= 2) return false;   // (on its way out already)
		return true;
	}
	static Target outTarget() {
		int hp = Dungeon.hero.pos; Hero h = Dungeon.hero;
		int best = -1; double bk = Double.MAX_VALUE;
		for (int c = 0; c < L.length(); c++) {
			int w = walk(c); if (w < 1 || w > 4 || !free(c)) continue;
			if (fireAt(c, false) > 0) continue;
			int adj = 0; for (Mob m : mobs()) if (hostile(m) && !shooter(m) && distance(m.pos, c) <= 1) adj++;
			double k = w * 10 + adj * 15;
			if (k < bk) { bk = k; best = c; }
		}
		if (best < 0) return null;
		double f = fireAt(hp, false);
		Target t = new Target(best, GO, "fire: out of its line (" + Math.round(f) + " a turn)");
		t.hint = 0.2 + Math.max(1, Brain.R) * Math.min(1, f * 2 / Math.max(1, h.HP)) * 0.4;
		return t;
	}
	static boolean free(int c) { return c >= 0 && c < L.length() && open[c] && known[c] && seenChar(c) == null && !danger[c] && !L.avoid[c] && walk(c) < INF; }

	/** PROPOSE: beside a foe, in a line from afar: a step beside it out of every line. */
	static boolean fightWhen(Cand c) {
		if (!ON || !FIGHT || c != Rules.planA || c.t == null || Dungeon.hero.rooted) return false;
		if (own(c.t) || c.t.kind == USE && c.t.why != null && c.t.why.startsWith("ascent: drink")) return false;
		note(); if (seen.isEmpty() || !adjFoe()) return false;
		return fireAt(Dungeon.hero.pos, false) > 0 && fightCell() >= 0;
	}
	static int fightCell() {
		int hp = Dungeon.hero.pos; double here = fireAt(hp, false);
		List<Mob> adjNow = new ArrayList<>(); for (Mob m : mobs()) if (hostile(m) && adjacent(m.pos, hp)) adjNow.add(m);
		int best = -1; double bk = Double.MAX_VALUE;
		for (int k : PathFinder.NEIGHBOURS8) { int n = hp + k;
			if (!free(n) || !adjacent(n, hp) || L.water[n] && !L.water[hp]) continue;
			double f = fireAt(n, false); if (f >= here / 3) continue;
			int keep = 0, adj = 0; for (Mob m : adjNow) if (adjacent(m.pos, n)) keep++;
			for (Mob m : mobs()) if (hostile(m) && !shooter(m) && adjacent(m.pos, n)) adj++;
			double s = f * 3 + adj * 6 - keep * 4;
			if (s < bk) { bk = s; best = n; } }
		return best;
	}
	static Target fightTarget() {
		int n = fightCell(); if (n < 0) return null;
		Hero h = Dungeon.hero; double f = fireAt(h.pos, false);
		Target t = new Target(n, GO, "fire: the fight out of its line (" + Math.round(f) + " a turn, past armour)");
		t.hint = 0.2 + Math.max(1, Brain.R) * Math.min(1, f * 3 / Math.max(1, h.HP)) * 0.5;
		return t;
	}

	static void reset() { seen.clear(); seenL = null; noteT = -1; notePos = -1; }
}
