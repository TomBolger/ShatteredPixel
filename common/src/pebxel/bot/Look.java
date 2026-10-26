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
 * Same shape as FightModel.simulate (MT's): arrival by distance / speed, a sleeper's first blow sure, a brute's rage,
 * a skeleton's bones, a bat's bite, Goo's low numbers, a shooter's bolt. Kept apart so that FightModel stays MT's to
 * tune; both read the same Fighters.
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
		public int healing;           // Healing already drunk, still to come (the hero's Healing buff: healingLeft)
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
		Fighter me = p.me != null ? p.me : me0;
		Out o = new Out();
		int n = foes.size();
		if (n == 0) { o.pDone = 1; o.hpLeft = me.hp; return o; }
		SplittableRandom rnd = new SplittableRandom(seed);
		double[] pH = new double[n], pF = new double[n], pR = new double[n], pLow = new double[n], pHLow = new double[n], pZ = new double[n], chase = new double[n];
		Dist[] bH = new Dist[n], bF = new Dist[n], bR = new Dist[n], bRage = new Dist[n], bLow = new Dist[n], bDeath = new Dist[n], bZ = new Dist[n];
		for (int i = 0; i < n; i++) {
			Fighter f = foes.get(i);
			pH[i] = f.blockAll ? 0 : Dist.hit(me.acc, f.eva); bH[i] = Fighter.blow(me.dmg, f);   // (the Great Crab awake blocks every blow it sees: FoeView, MT)
			pF[i] = Dist.hit(f.acc, me.eva); bF[i] = Fighter.blow(f.dmg, me);
			if (f.rageDmg != null) bRage[i] = Fighter.blow(f.rageDmg, me);
			if (f.lowDmg != null) { bLow[i] = Fighter.blow(f.lowDmg, me); pLow[i] = Dist.hit(f.lowAcc, me.eva); pHLow[i] = Dist.hit(me.acc, f.lowEva > 0 ? f.lowEva : f.eva); }
			if (f.deathDmg != null) bDeath[i] = f.deathDmg.minus0(me.dr.plus(me.dr));
			if (f.zapDmg != null) { bZ[i] = f.zapDmg; pZ[i] = Dist.hit(2 * f.acc, me.eva); }
			if (me.rangedDmg != null) { pR[i] = f.blockAll ? 0 : me.rangedSure ? 1 : Dist.hit(me.rangedAcc, f.eva); bR[i] = Fighter.blow(me.rangedDmg, f); }
			// walking off: a faster one strikes every (its blow + the step it takes to catch up); a shooter keeps shooting
			chase[i] = f.ranged ? f.delay / 0.6 : f.speed > me.speed * 1.01 ? f.delay + 1 / f.speed : 0;
		}
		boolean hurtable = false; for (Fighter f : foes) hurtable |= !f.blockAll;
		double horizon = hurtable ? 100 : 30;   // (as FightModel, MT: against what can't be hurt, how fast it goes wrong is what counts)
		int[] hp = new int[n]; double[] next = new double[n], arr = new double[n], atk = new double[n]; boolean[] rage = new boolean[n], awake = new boolean[n];
		int deaths = 0, done = 0; double left = 0, used = 0, tsum = 0;
		for (int run = 0; run < runs; run++) {
			int myHp = me.hp, sh = me.shield, heals = me.heals, shots = p.flee >= 0 ? 0 : me.rangedShots, healLeft = p.healing, usedR = 0;
			boolean drink = p.drink && heals > 0;
			for (int i = 0; i < n; i++) {
				Fighter f = foes.get(i);
				hp[i] = f.hp + f.shield; rage[i] = false; awake[i] = !f.asleep;
				arr[i] = f.dist <= 1 ? 0 : f.asleep ? Double.MAX_VALUE : f.ranged ? (f.dist - 1) / Math.max(0.1, me.speed) : (f.dist - 1) / Math.max(0.1, f.speed);
				atk[i] = f.ranged && !f.asleep ? 0 : arr[i];
				next[i] = awake[i] ? atk[i] + f.delay * rnd.nextDouble() : Double.MAX_VALUE;
				if (p.flee >= 0) next[i] = chase[i] > 0 && awake[i] ? (f.ranged ? 0 : arr[i]) + chase[i] * rnd.nextDouble() : Double.MAX_VALUE;
			}
			double t = 0, tH = 0; boolean dead = false, gone = false; int alive = n, lost = p.lose, steps = p.flee;
			for (int guard = 0; guard < 600 && alive > 0 && !dead && !gone && t <= horizon; guard++) {
				int fi = -1; double ft = Double.MAX_VALUE;
				for (int i = 0; i < n; i++) if (hp[i] > 0 && next[i] < ft) { ft = next[i]; fi = i; }
				if (tH <= ft) {
					t = tH;
					if (healLeft > 0) { int hl = Math.max(1, Math.min(healLeft, Math.round(healLeft * 0.25f))); myHp = Math.min(me.ht, myHp + hl); healLeft -= hl; }   // (Healing.act, a tick a turn)
					if (drink) { drink = false; heals--; usedR++; healLeft = me.healAmount; tH += 1; continue; }
					if (lost > 0) { lost--; tH += 1; continue; }
					if (steps >= 0) { if (steps-- == 0) { gone = true; break; } tH += 1 / Math.max(0.1, me.speed); continue; }
					if (heals > 0 && p.healAt > 0 && healLeft == 0 && myHp < me.ht * p.healAt) { heals--; usedR++; healLeft = me.healAmount; tH += 1; continue; }
					int tg = -1, best = Integer.MAX_VALUE;
					for (int i = 0; i < n; i++) if (hp[i] > 0 && t >= arr[i] - 1e-9 && hp[i] < best) { best = hp[i]; tg = i; }
					if (tg >= 0) {
						Fighter f = foes.get(tg);
						if (!awake[tg] || rnd.nextDouble() < (bLow[tg] != null && hp[tg] <= f.lowAt ? pHLow[tg] : pH[tg])) hp[tg] -= bH[tg].draw(rnd.nextDouble());
						if (!awake[tg]) { awake[tg] = true; atk[tg] = t; next[tg] = t + f.delay * (0.5 + rnd.nextDouble()); }
						if (hp[tg] <= 0 && f.rageHp > 0 && !rage[tg]) { rage[tg] = true; hp[tg] = f.rageHp; }
						if (hp[tg] <= 0) {
							alive--;
							if (bDeath[tg] != null) { int d = bDeath[tg].draw(rnd.nextDouble()); myHp -= Math.max(0, d - sh); sh = Math.max(0, sh - d); if (myHp <= 0) dead = true; }
						}
						tH += me.delay;
						continue;
					}
					int ni = -1; double nd = Double.MAX_VALUE, na = Double.MAX_VALUE; int sl = -1;
					for (int i = 0; i < n; i++) if (hp[i] > 0) {
						if (awake[i]) { na = Math.min(na, arr[i]); double d = foes.get(i).dist - t * foes.get(i).speed; if (d < nd) { nd = d; ni = i; } }
						else if (sl < 0 || foes.get(i).dist < foes.get(sl).dist) sl = i;
					}
					if (shots > 0 && ni >= 0) {
						shots--;
						if (rnd.nextDouble() < pR[ni]) hp[ni] -= bR[ni].draw(rnd.nextDouble());
						if (hp[ni] <= 0 && foes.get(ni).rageHp > 0 && !rage[ni]) { rage[ni] = true; hp[ni] = foes.get(ni).rageHp; }
						if (hp[ni] <= 0) alive--;
						tH += me.rangedDelay;
					} else if (na < Double.MAX_VALUE) tH = Math.max(na, t + 0.01);
					else if (sl >= 0) { gone = true; break; }   // (only sleepers left: the fight is over, they can be left be)
					else tH = t + 1;
				} else {
					Fighter f = foes.get(fi); t = ft;
					boolean lo = bLow[fi] != null && hp[fi] <= f.lowAt, zap = bZ[fi] != null && (t < arr[fi] - 1e-9 || p.flee >= 0);
					if ((p.flee >= 0 || t >= atk[fi] - 1e-9) && rnd.nextDouble() < (zap ? pZ[fi] : lo ? pLow[fi] : pF[fi])) {
						int d = (zap ? bZ[fi] : rage[fi] && bRage[fi] != null ? bRage[fi] : lo ? bLow[fi] : bF[fi]).draw(rnd.nextDouble());
						if (me.taken != 1) d = (int) Math.round(d * me.taken);
						myHp -= Math.max(0, d - sh); sh = Math.max(0, sh - d);
						if (f.leech > 0) hp[fi] = Math.min(f.ht, hp[fi] + d);
						if (myHp <= 0) dead = true;
					}
					next[fi] = t + (p.flee >= 0 ? chase[fi] : f.delay);
				}
			}
			if (dead) deaths++; else if (alive == 0 || gone) done++;
			left += dead ? 0 : Math.min(me.ht, myHp + healLeft); used += usedR; tsum += t;
		}
		o.pDeath = deaths / (double) runs; o.pDone = done / (double) runs; o.hpLeft = left / runs; o.healsUsed = used / runs; o.time = tsum / runs;
		return o;
	}
}
