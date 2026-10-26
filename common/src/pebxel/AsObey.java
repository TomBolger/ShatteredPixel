package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import java.util.*;
import pebxel.Assist.Target;
import static pebxel.Assist.*;

/**
 * Obedience, the last word on plan A (agent DS; Pebble PD's pass_hands; Thomas: "anticipate the human's wants, not
 * push or insist"). What the player passed over isn't plan A again until things change, whichever skill proposes it:
 * an item's use (declined: until more of it comes, or for a drink or a meal, until he is a quarter worse hurt or
 * hungrier), a wait (hold, ambush, a piranha's) a while, a walk to the same place in a fight a while (out of a fight
 * Assist.demote does that), a search there a while. It goes behind the rest, still there for a Down. Only getting out
 * of harm under way (fire, gas, a piranha's water) stays first: that is safety, not insisting.
 */
final class AsObey {
	private AsObey() { }

	static final int WAIT_TURNS = 10, FIGHT_TURNS = 10;
	/** Waits and searches passed over (their kind and cell -> till when), walks passed over in a fight (objective -> till). */
	static final Map<String, Float> waits = new HashMap<>();
	static final Map<Integer, Float> fightWalks = new HashMap<>(), skillWalks = new HashMap<>();
	static Object floor;
	/** Plans held back here (tests). */
	static long held;

	static void newFloor(Object l) { if (floor != l) { floor = l; waits.clear(); fightWalks.clear(); skillWalks.clear(); } }

	static String waitKey(Target t) { String w = t.why == null ? "" : t.why; int c = w.indexOf(':'); return (c > 0 ? w.substring(0, c) : w) + "@" + t.cell + ":" + t.action; }

	/** Assist.pass: the entry e was passed over (went: where the hero went, from: where he was). */
	static void passed(Target e, int went, int from) {
		float now = Actor.now();
		if (e.kind == WAIT && !REST_ACT.equals(e.action)) waits.put(waitKey(e), now + (SEARCH_ACT.equals(e.action) || e.action == null ? DEMOTE : WAIT_TURNS));
		int o = e.obj >= 0 ? e.obj : e.cell;
		if (e.kind == PICKUP && o >= 0 && o == heroPos()) {   // (what lies underfoot, passed over: Assist.pass leaves it, the hero being there; as any thing passed over)
			skillWalks.put(o, now + WAIT_TURNS);
			if (fight || seenFoe) combatHeld.add(o); else if (!keyAt(o)) skippedHeaps.add(o);
		}
		if ((e.kind == GO || e.kind == PICKUP) && o >= 0 && o != heroPos()) {
			if (fight || seenFoe) fightWalks.put(o, now + FIGHT_TURNS);
			if (e.src != null) skillWalks.put(o, now + WAIT_TURNS);   // (a skill's step (a puzzle's, a ritual's, a quest's) passed over: a while, whichever way the hero went)
		}
	}

	static boolean safety(Target a) {
		String w = a.why == null ? "" : a.why;
		return w.startsWith("escape the") || w.startsWith("escape: out of the water") || w.startsWith("burning") || w.startsWith("dying") || w.startsWith("starving: eat from");
	}

	/** Is plan A something the player passed over, with nothing changed since? */
	static boolean held(Target a, float now) {
		if (a == null || safety(a)) return false;
		switch (a.kind) {
			case USE: {
				if (a.item == null || a.action == null) return false;
				if (a.cell >= 0 && a.cell != heroPos() && Actor.findChar(a.cell) != null) return false;   // (a throw or zap at a foe: the fight's)
				return declined(a.item.getClass(), a.action);
			}
			case WAIT: {
				if (REST_ACT.equals(a.action)) return declined(null, REST_ACT);
				Float u = waits.get(waitKey(a)); return u != null && u >= now;
			}
			case GO: case PICKUP: {
				int o = a.obj >= 0 ? a.obj : a.cell;
				if (a.kind == PICKUP && skippedHeaps.contains(o) && o != heroPos()) return true;   // (a thing passed over: the brain's weighing brought it back first now and then)
				Float s = skillWalks.get(o); if (s != null && s >= now) return true;   // (a skill's step, or what lay underfoot, passed over: wherever he is now)
				if (!seenFoe && !inDanger) return false;   // (out of a fight Assist.demote has the rest)
				Float u = fightWalks.get(o); return u != null && u >= now;
			}
			default: return false;
		}
	}

	/** planList's last word: plan A held back behind the rest while it is one passed over (a few in a row at most). */
	static void gate(List<Target> out) {
		if (out.size() < 2 || com.shatteredpixel.shatteredpixeldungeon.Dungeon.hero == null) return;
		newFloor(L);
		float now = Actor.now();
		waits.values().removeIf(t -> t < now); fightWalks.values().removeIf(t -> t < now); skillWalks.values().removeIf(t -> t < now);
		for (int k = 0; k < 3 && out.size() > 1; k++) {
			Target a = out.get(0);
			if (!held(a, now)) return;
			out.add(out.remove(0));
			held++;
		}
	}

	/** What declining an item's use waits for: more of it, or for a drink or a meal the hero worse off (a quarter of his
	 *  health, a hunger step): then it is offered again, the need being greater than when he passed it. */
	static int need(Class<?> c, String a) {
		com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero h = com.shatteredpixel.shatteredpixeldungeon.Dungeon.hero;
		if (h == null || c == null) return 0;
		if (Potion.class.isAssignableFrom(c) && Potion.AC_DRINK.equals(a)) return (h.HT - h.HP) * 4 / Math.max(1, h.HT);
		if (Food.class.isAssignableFrom(c) && Food.AC_EAT.equals(a)) return starving() ? 8 + (int) (Actor.now() / 20) : hungry() ? 4 : 0;   // (starving costs health: offered again every 20 turns)
		return 0;
	}
}
