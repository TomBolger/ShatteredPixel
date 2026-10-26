package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.NPC;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import java.util.*;
import pebxel.Assist.Target;
import pebxel.bot.*;
import static pebxel.Assist.*;

/**
 * Shooters' bolts (agent K7, for Rules' table): the top killers after the bosses are bolts and gazes, the shamans' and
 * DM-201s' in the Caves, the warlocks' in the City, the scorpios' spit and the eyes' beams in the Halls (K5, BR-a2).
 * The tactics (Assist.tactics) step out of the nearest shooter's line; with two in sight that step went into the
 * other's line and back for ever while both shot (K7: a Cleric on 23 between two scorpios, 150 health in 12 turns).
 *
 *  - an eye's charged beam: the eye shows it (the charge, aimed at where the hero stood); it fires along that line a
 *    turn or two later, through whoever stands in it, for 30-50. Off the line, by the nearest step (Eye.doAttack);
 *  - several shooters with a line on the hero: the nearest cell out of every line, not the nearest out of one;
 *  - a scorpio never comes to the hero (hunting, it backs off: Scorpio.getCloser) and can't spit at one beside it:
 *    waiting round the corner for it waits for nothing.
 *
 * Only what the player sees: the foes in sight, their lines over the known map, the eye's charge.
 * AS_BOLTS=0 turns the rows off.
 */
final class AsBolts {
	private AsBolts() {}

	static final boolean ON = opt("BOLTS", true);

	static boolean shooter(Mob m) { return FoeView.shooter(m) && !(m instanceof DwarfKing) && !(m instanceof Tengu); }
	/** The shooters awake in sight. */
	static List<Mob> shooters() {
		List<Mob> l = new ArrayList<>();
		for (Mob m : mobs()) if (enemy(m) && shooter(m) && m.state != m.SLEEPING && m.state != m.PASSIVE && !(m instanceof NPC) && !Dungeon.hero.isCharmedBy(m) && distance(m.pos, Dungeon.hero.pos) <= 9) l.add(m);
		return l;
	}
	/** m can shoot at cell c (an eye's gaze goes through whoever stands between; a bolt or spit stops at the first). */
	static boolean lineOn(Mob m, int c) {
		if (m instanceof Eye) return sight(m.pos, c);
		if (m instanceof Scorpio && distance(m.pos, c) <= 1) return false;   // (beside it: it can't spit)
		return line(m.pos, c);
	}
	static int inLine(List<Mob> sh, int c) { int n = 0; for (Mob m : sh) if (lineOn(m, c)) n++; return n; }

	/** A step offered last decision that came to nothing (the hero where he was, no time gone): not offered again
	 *  there (K7-b: twice a step off an eye's beam that the game never took, offered for ever). */
	static int triedPos = -1, triedCell = -1; static float triedAt = -1; static final Set<Integer> fails = new HashSet<>();
	static Target tried(Target t) {
		if (t != null) { see(); triedCell = t.cell; }
		return t;
	}
	/** The hero still where he was and no time gone since the last step offered: that step failed (kept, with any
	 *  others failed there); else the list starts again. */
	static void see() {
		int p = Dungeon.hero.pos; float n = com.shatteredpixel.shatteredpixeldungeon.actors.Actor.now();
		if (p == triedPos && n == triedAt) { if (triedCell >= 0) fails.add(triedCell); } else { fails.clear(); triedPos = p; triedAt = n; }
		triedCell = -1;
	}
	static boolean failed(int c) { return Dungeon.hero.pos == triedPos && com.shatteredpixel.shatteredpixeldungeon.actors.Actor.now() == triedAt && (fails.contains(c) || c == triedCell); }

	/** A cell a foe can stand by: free, open, known, not in danger. */
	static boolean free(int c) { return !failed(c) && c >= 0 && c < L.length() && open[c] && known[c] && seenChar(c) == null && !danger[c] && !L.avoid[c] && walk(c) < INF; }

	// =============================================================================================================
	// an eye's charged beam
	// =============================================================================================================
	/** The cells of a charged eye's beam (empty: none charged in sight). */
	static Set<Integer> beams() {
		Set<Integer> s = new HashSet<>();
		for (Mob m : mobs()) {
			if (!(m instanceof Eye) || !m.isAlive() || !L.heroFOV[m.pos] || !((Eye) m).beamCharged) continue;
			Object bt = field(m, "beamTarget");
			if (!(bt instanceof Integer) || (Integer) bt < 0) continue;
			try { for (int c : new Ballistica(m.pos, (Integer) bt, Ballistica.STOP_SOLID).subPath(1, Integer.MAX_VALUE)) s.add(c); } catch (Throwable e) { }
		}
		return s;
	}
	static int beamHi() { int hi = 0; Hero h = Dungeon.hero; for (Mob m : mobs()) if (m instanceof Eye && ((Eye) m).beamCharged && L.heroFOV[m.pos]) { Fighter f = FoeView.of(m, h); hi = Math.max(hi, f.zapDmg != null ? f.zapDmg.hi() : 55); } return hi; }

	/** Rules: the hero in a charged beam's line: the nearest step out of it (and out of every other line if it can). */
	static boolean inBeam(Brain.Cand c) { if (!ON || c != Rules.planA) return false; Set<Integer> b = beams(); return !b.isEmpty() && b.contains(Dungeon.hero.pos); }
	static Target offBeam() {
		Set<Integer> b = beams(); int hp = Dungeon.hero.pos; List<Mob> sh = shooters();
		int best = -1, bk = INF;
		for (int c = 0; c < L.length(); c++) {
			if (b.contains(c) || !free(c) || walk(c) > 2) continue;
			int k = walk(c) * 10 + inLine(sh, c) * 4;
			if (k < bk) { bk = k; best = c; }
		}
		if (best < 0) return null;
		Hero h = Dungeon.hero;
		Target t = tried(new Target(best, GO, "bolts: off the eye's beam"));
		t.hint = 0.2 + Math.max(1, Brain.R) * Math.min(1, beamHi() / (double) Math.max(1, h.HP)) * 0.5;
		return t;
	}

	// =============================================================================================================
	// several lines on the hero
	// =============================================================================================================
	/** The tactic's step out of one line ends in another's. */
	static boolean intoAnother(Brain.Cand c) {
		if (!ON || !c.t.why.equals("tactic: out of its line")) return false;
		if (failed(c.t.cell)) return true;   // (the same step offered again with the hero where he was, no time gone: K7-e, two loops)
		List<Mob> sh = shooters();
		if (sh.size() >= 2 && inLine(sh, c.t.cell) > 0) return true;
		tried(c.t);
		return false;
	}
	/** Rules: plan A leaves the hero in two or more lines (no foe beside him), or is a step from one line to another. */
	static boolean manyLines(Brain.Cand c) {
		if (!ON || c != Rules.planA || Brain.fight && adjFoe()) return false;
		List<Mob> sh = shooters();
		if (sh.size() < 2) return false;
		int hp = Dungeon.hero.pos;
		int dest = (c.t.kind == GO || c.t.kind == PICKUP) && c.t.cell >= 0 ? c.t.cell : hp;
		return inLine(sh, hp) >= 1 && inLine(sh, dest) >= 1;
	}
	static boolean adjFoe() { int hp = Dungeon.hero.pos; for (Mob m : mobs()) if (enemy(m) && m.state != m.PASSIVE && adjacent(m.pos, hp)) return true; return false; }
	static Target outOfAll() {
		List<Mob> sh = shooters(); int hp = Dungeon.hero.pos; Hero h = Dungeon.hero;
		int best = -1, bk = INF;
		for (int c = 0; c < L.length(); c++) {
			if (c == hp || !free(c) || walk(c) > 5 || inLine(sh, c) > 0) continue;
			boolean melee = false; for (Mob m : mobs()) if (enemy(m) && !shooter(m) && hunting(m) && distance(m.pos, c) <= 1) melee = true;
			if (melee) continue;
			if (walk(c) < bk) { bk = walk(c); best = c; }
		}
		if (best < 0) return null;
		double hi = 0; for (Mob m : sh) if (lineOn(m, hp)) { Fighter f = FoeView.of(m, h); Dist d = f.zapDmg != null ? f.zapDmg : f.rangedDmg != null ? f.rangedDmg : f.dmg; hi += d.mean(); }
		Target t = tried(new Target(best, GO, "bolts: out of every line"));
		t.hint = 0.15 + Math.max(1, Brain.R) * Math.min(1, hi * 2 / Math.max(1, h.HP)) * 0.3;
		return t;
	}

	// =============================================================================================================
	// the scorpio that never comes
	// =============================================================================================================
	/** Waiting round the corner for a scorpio (the nearest shooter, the only one in sight): it backs off, never comes. */
	static boolean waitScorpio(Brain.Cand c) {
		if (!ON || !c.t.why.equals("tactic: wait round the corner")) return false;
		List<Mob> sh = shooters(); if (sh.isEmpty()) return false;
		for (Mob m : sh) if (!(m instanceof Scorpio)) return false;
		return true;
	}
}
