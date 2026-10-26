package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import pebxel.Assist.Target;
import static pebxel.Assist.*;

/**
 * Starving, no waiting (agent MD). Starving stops regeneration and takes HT/1000 a turn: at 150 HT about 0.25 health
 * a turn against a fed hero. Full mild games (MD-up1, 576 games) lost 14% of all their health to starving, and the
 * deaths at 11-21 were full of waits that never ended while starving: "fire: wait out of its line (it must come round)"
 * 766 turns in one game (seed 1006, depth 11), ambush waits, "let it come". A wait is a bet that the foe comes; starving,
 * each turn of it costs health for certain. So: starving, a waiting move held more than WAITCAP turns in a row on this
 * floor is vetoed (the Brain takes its next option: going at it, or on) for the next WAITOFF turns; fed, after WAITFED
 * (the same game waited there 400 turns at full health, then starved). The why: "a wait that has not paid: on".
 */
final class AsPacing {
	private AsPacing() {}

	static final boolean ON = opt("MDWAIT", true);
	static final int WAITCAP = optInt("MDWAITCAP", 15), WAITFED = optInt("MDWAITFED", 60), WAITOFF = optInt("MDWAITOFF", 40);
	static float streak0 = -1, last = -1, offUntil = -1; static Level lvl;

	static boolean waiting(Target t) {
		if (t == null || t.why == null) return false;
		String w = t.why;
		if (w.startsWith("search") || w.startsWith("dead end") || w.contains("rest")) return false;   // (searching and resting have their own food rules)
		return w.contains("wait") || w.contains("come round") || w.contains("let it come") || w.startsWith("hold") || w.startsWith("ambush");
	}

	static int fired;
	/** The last pass over plan A (after AsHealth's): a wait gone on too long goes to the back of the list. */
	static void after(java.util.List<Target> out) {
		if (out.size() < 2 || !veto(out.get(0))) return;
		Target w = out.remove(0); out.add(w); fired++;
		Target a = out.get(0); if (a.why != null && !a.why.endsWith("]")) a.why = a.why + " [a wait that has not paid: on]";
	}

	/** Starving (or not, but longer), the waits on this floor have gone on long enough. */
	static boolean veto(Target t) {
		if (!ON || !waiting(t)) return false;
		float now = Actor.now();
		if (lvl != Dungeon.level) { lvl = Dungeon.level; streak0 = last = offUntil = -1; }
		if (now < offUntil) return true;
		if (last < 0 || now - last > 3) streak0 = now;
		last = now;
		if (now - streak0 > (starving() ? WAITCAP : WAITFED)) { offUntil = now + WAITOFF; streak0 = -1; last = -1; return true; }
		return false;
	}
}
