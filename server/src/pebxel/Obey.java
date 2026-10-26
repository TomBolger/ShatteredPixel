package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import java.util.*;

/**
 * The obedience check (agent DS): a suggestion the player passed over must not come straight back as plan A
 * (Thomas: "anticipate the human's wants, not push or insist"). Each pass is noted with the skill that proposed it
 * (Assist.Target.src: Brain.offer's name, or the last passes' health / pacing / escape); a plan A that is the same
 * thing (the same item and action, the same objective, the same wait) on the same floor within WINDOW decisions counts
 * as that skill insisting. Fights are left out (a foe passed over is still a foe). Used by AssistBot OBEY=n (every n-th
 * skill suggestion passed over, plan B taken) and by the watch's simulated human (PLAYER=human).
 */
final class Obey {
	static final int WINDOW = 30;
	static final class Passed { final String key, src; final long at; final int depth, from, obj; final Assist.Target t; final float now; final boolean fight; boolean checked; int went, sit;
		Passed(String k, String s, long a, int d, Assist.Target t) { key = k; src = s; at = a; depth = d; this.t = t; from = Dungeon.hero.pos; obj = t.obj >= 0 ? t.obj : t.cell; now = com.shatteredpixel.shatteredpixeldungeon.actors.Actor.now(); fight = Assist.seenFoe || Assist.fight; } }
	/** How long a pass must hold, in game turns: an objective (a walk, a wait) Assist.DEMOTE; an item's use the floor. */
	static final int WALK_TURNS = 20, USE_TURNS = 300, SHORT_TURNS = 10;
	/** A use: the floor; a wait, a skill's step or anything passed in a fight: AsObey's while; a walk: Assist.DEMOTE. */
	static int turns(Passed p) { return p.t.kind == Assist.USE ? USE_TURNS : p.t.kind == Assist.WAIT || p.t.src != null || p.fight ? SHORT_TURNS : WALK_TURNS; }
	final List<Passed> open = new ArrayList<>();
	final Map<String, Integer> passes = new TreeMap<>(), insist = new TreeMap<>(), safe = new TreeMap<>(), onWay = new TreeMap<>();
	final List<String> examples = new ArrayList<>();
	long n;

	static String keyOf(Assist.Target t) {
		if (t == null) return "";
		switch (t.kind) {
			case Assist.USE: return "use:" + (t.item == null ? "-" : System.identityHashCode(t.item)) + ":" + t.action + (t.item == null ? ":" + t.why.replaceAll("[^a-z]", "") : "");
			case Assist.GO: case Assist.PICKUP: return "go:" + (t.obj >= 0 ? t.obj : t.cell);
			case Assist.WAIT: return "wait:" + t.action + ":" + t.cell;
			default: return "";
		}
	}
	static String src(Assist.Target t) {
		if (t.src != null) return t.src;
		String w = t.why == null ? "?" : t.why.replaceAll("[\\[(].*", "").trim(); int c = w.indexOf(':');
		return "(" + (c > 0 ? w.substring(0, c) : w.length() > 20 ? w.substring(0, 20) : w).trim().replace(' ', '_') + ")";
	}
	/** Would passing this over be a test of obedience (not a fight, not a plain step)? */
	static boolean testable(Assist.Target a) {
		if (a == null || a.kind == Assist.ENEMY || keyOf(a).isEmpty()) return false;
		if (a.why != null && (a.why.startsWith("your") || a.why.startsWith("resume"))) return false;   // (his own walk, echoed: no suggestion of ours)
		if (a.kind == Assist.USE && a.item != null && a.cell >= 0 && Dungeon.hero != null && a.cell != Dungeon.hero.pos && com.shatteredpixel.shatteredpixeldungeon.actors.Actor.findChar(a.cell) != null) return false;   // (a throw or zap at a foe: the fight's, no choice to remember, as Controls.passOne)
		return true;
	}
	/** Out of harm under way (fire, gas, piranhas' water): offered again while it lasts is safety, counted apart. */
	static boolean safety(Assist.Target a) { String w = a.why == null ? "" : a.why; return w.startsWith("escape the") || w.startsWith("escape: out of the water") || w.startsWith("burning") || w.startsWith("dying"); }

	/** The player passed plan A over (took another entry, or did something else). */
	void passed(Assist.Target a, Assist.Target chosen) {
		if (!testable(a)) return;
		Passed p = new Passed(keyOf(a), src(a), n, Dungeon.depth, a);
		p.went = chosen != null && chosen.cell >= 0 && !"tap".equals(chosen.why) ? chosen.cell : -1;   // (as Assist.chose: where the choice goes; a tap or something else: where he is next, Controls.obeyed)
		p.sit = a.kind == Assist.USE && a.item != null ? Assist.situation(a.item.getClass(), a.action) : 0;
		open.add(p);
	}
	/** A new decision with plan A: insisting, if it is one passed over lately. */
	void look(Assist.Target a) {
		n++;
		String k = keyOf(a);
		for (Iterator<Passed> it = open.iterator(); it.hasNext(); ) {
			Passed p = it.next();
			float age = com.shatteredpixel.shatteredpixeldungeon.actors.Actor.now() - p.now;
			if (p.depth != Dungeon.depth || age > turns(p)) { it.remove(); continue; }
			if (!p.checked) {   // (as Assist.passed: a walk that went towards it, or the item used after all, was no pass)
				p.checked = true;
				com.shatteredpixel.shatteredpixeldungeon.levels.Level L = Dungeon.level; int h = Dungeon.hero.pos;
				boolean towards = (p.t.kind == Assist.GO || p.t.kind == Assist.PICKUP) && p.obj >= 0 && L.distance(p.went >= 0 ? p.went : h, p.obj) < L.distance(p.from, p.obj);
				boolean used = p.t.kind == Assist.USE && p.t.item != null && !Dungeon.hero.belongings.contains(p.t.item);
				if (towards || used) { it.remove(); continue; }
				passes.merge(p.src, 1, Integer::sum);
			}
			if (p.fight && !Assist.seenFoe && !Assist.inDanger && p.t.kind != Assist.USE) { it.remove(); continue; }   // (passed in a fight: held only till it's over, by design: "combat caused us to ignore pickups")
			if (p.t.kind == Assist.USE && p.t.item != null && Assist.situation(p.t.item.getClass(), p.t.action) > p.sit) { it.remove(); continue; }   // (things changed: more of it, or worse off for a drink or a meal)
			if (!k.isEmpty() && k.equals(p.key) && a.why != null && a.why.startsWith("on the way")) { onWay.merge(p.src, 1, Integer::sum); it.remove(); continue; }   // (offered once more, on his way: by design)
			if (!k.isEmpty() && k.equals(p.key)) {
				if (safety(p.t) || safety(a)) safe.merge(p.src, 1, Integer::sum); else {
				insist.merge(p.src, 1, Integer::sum);
				if (examples.size() < 40) { examples.add(p.src + " after " + (n - p.at) + ": " + a); System.out.println("  INSIST " + p.src + " (passed " + p.t + ") back as plan A after " + (n - p.at) + " decisions, " + Math.round(age) + " turns: " + a + " src " + a.src + (p.fight ? " (fight then)" : "") + (Assist.seenFoe ? " (foe now)" : "")); } }
				it.remove();
			}
		}
	}
	String line() { return ("passes " + passes + " insist " + insist + " safety " + safe + " onway " + onWay).replace(", ", ","); }
}
