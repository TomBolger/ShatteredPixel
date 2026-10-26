package pebxel.bot;

import java.util.*;

/**
 * The lookahead (agent BR): the fight in sight played out a few hundred times from each of the hero's options, on the
 * same random numbers for every option (common random numbers: the options differ by what the hero does, not by luck),
 * with the numbers Char.hit and Char.attack roll with (Fighter: HeroView, FoeView). What the hero does first is the
 * option; after that he follows the policy: strike the weakest foe beside him, shoot one coming, drink a known Healing
 * below {@link Plan#healAt} of his health.
 *
 * The options ({@link Plan}): fight on; drink Healing now (a turn, then Healing's 25% of what's left a turn: Healing.act);
 * spend turns on something that doesn't fight (an equip, a read: {@link Plan#lose}); fight in other gear ({@link Plan#me});
 * walk away {@link Plan#flee} steps (only the faster ones and the shooters strike a hero walking off: a mob moves or
 * strikes, a same-speed one never catches up) and be gone (stairs, a teleport after its turn).
 *
 * The loop itself is FightModel.core (MT merged the two at BR's request): arrival by distance / speed, the room beside
 * the hero, a sleeper's first blow sure, a brute's rage, a skeleton's bones, a bat's bite, Goo's low numbers, a
 * shooter's bolt, the Eye's beam, the deep kinds' effects past armour.
 */
public final class Look {
	private Look() { }

	public static int RUNS = (int) Tune.get("BRUNS", 300);

	/** What the hero does: first moves, then the policy. */
	public static final class Plan {
		public Fighter me;            // the hero (null: as given to run)
		public int lose;              // turns spent first doing something that doesn't fight (the foes act)
		public boolean drink;         // drink Healing first (one of me.heals; a turn)
		public double healAt = Tune.get("BHEALAT", 35) / 100;   // policy: drink below this share of health (0 never)
		public int flee = -1;         // walk away this many steps and be gone (-1: stay and fight)
		public int healing;           // Healing already drunk, still to come (the hero's Healing buff: healingLeft; HeroView puts it in me.healLeft too: the larger counts)
		public boolean sleepersDone = true;   // only sleepers left: the fight is over (FightModel's own sums: walk up to them)
		public Plan drink() { drink = true; return this; }
		public Plan lose(int n) { lose = n; return this; }
		public Plan flee(int n) { flee = n; return this; }
		public Plan healAt(double h) { healAt = h; return this; }
		public Plan me(Fighter f) { me = f; return this; }
		public Plan healing(int h) { healing = h; return this; }
	}

	public static final class Out {
		/** P(dies), P(all foes dead or gone from), the expected health left (on living runs and dead ones: 0), the
		 *  potions drunk, the time. */
		public double pDeath, pDone, hpLeft, healsUsed, time;
		@Override public String toString() { return String.format("death %.3f done %.3f left %.1f heals %.2f t %.1f", pDeath, pDone, hpLeft, healsUsed, time); }
	}

	/** The fight with foes, the hero doing p. */
	public static Out run(Fighter me0, List<Fighter> foes, Plan p, long seed) { return run(me0, foes, p, RUNS, seed); }

	public static Out run(Fighter me0, List<Fighter> foes, Plan p, int runs, long seed) {
		return FightModel.play(me0, foes, p, runs, seed);   // (MT: one simulator for both, FightModel.core: the room beside the hero, the foes' quirks and effects)
	}
}
