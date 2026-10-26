package pebxel.bot;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import java.util.*;

/**
 * The fight model (OG bot's hp_cost_vs / losing_fight, re-derived for Shattered): the expected health a fight with a set
 * of visible enemies costs, and the odds of dying in it, from Char.hit and Char.attack's own numbers (Fighter).
 *
 * Two forms:
 *  - {@link #duelCost}: the OG's closed sum, (its health / my damage a turn) x (its damage a turn), for gear values;
 *  - {@link #evaluate}: a quick simulation of the fight itself, on the bot's own random numbers (SplittableRandom, a
 *    fixed seed: the same futures for every option compared), with arrival times (distance / speed), free shots while
 *    they come (bow, wand, thrown), the hero hitting the weakest one beside it, every one beside it hitting back at its
 *    own attack delay, a shield, a slime's cap, a brute's rage, a skeleton's burst, a bat's bite healing it, and
 *    optionally a Potion of Healing drunk at a threshold.
 *
 * What it can't see it doesn't count: unseen mobs, a mob's AI state. Calibrated against real fights (FIGHTCAL lines,
 * sim/fightcal.py).
 */
public final class FightModel {
	private FightModel() { }

	public static final class Result {
		/** P(the hero dies), P(all the foes die), the expected health lost (shield first; death counts the health had),
		 *  the 90th percentile of it, the expected time, the expected health left. */
		public double pDeath, pWin, lossMean, loss90, time, hpLeft, healsUsed, healsLive;
		public int runs;
		@Override public String toString() { return String.format("death %.3f win %.3f loss %.1f (p90 %.0f) time %.1f", pDeath, pWin, lossMean, loss90, time); }
	}

	public static int RUNS = 300;

	/** The fight with these mobs as the hero stands now (null / empty: no fight). The mobs: the ones the caller counts
	 *  as in it (seen, hostile, coming or beside). */
	public static Result evaluate(Hero h, List<Mob> mobs) { return evaluate(h, mobs, 0); }

	/** healAt: drink a known Healing below this share of health (0: never). */
	public static Result evaluate(Hero h, List<Mob> mobs, double healAt) {
		Fighter me = HeroView.of(h);
		List<Fighter> fs = new ArrayList<>();
		for (Mob m : mobs) fs.add(FoeView.of(m, h));
		return simulate(me, fs, healAt, RUNS, seedFor(h));
	}

	static long seedFor(Hero h) { return 0x9E3779B97F4A7C15L ^ (Dungeon.depth * 1000003L) ^ (h == null ? 0 : h.pos * 7919L); }

	/** The closed sum: health it costs to kill f with me, alone (OG hp_cost_vs). */
	public static double duelCost(Fighter me, Fighter f) {
		double mine = dps(me, f), its = dps(f, me) * me.taken;
		double hp = f.hp + f.shield + f.rageHp;
		if (mine <= 1e-6) return 1e6;
		double c = hp / mine * its;
		if (f.rageHp > 0 && f.rageDmg != null) c += f.rageHp / mine * (Dist.hit(f.acc, me.eva) * Fighter.blow(f.rageDmg, me).mean() / f.delay - its);
		if (f.leech > 0) c *= 1 + Dist.hit(f.acc, me.eva) * Fighter.blow(f.dmg, me).mean() / Math.max(0.3, mine * f.delay);
		if (f.lowAt > 0 && f.lowDmg != null) { double mineLo = Dist.hit(me.acc, f.lowEva > 0 ? f.lowEva : f.eva) * Fighter.blow(me.dmg, f).mean() / Math.max(0.05, me.delay); int lo = Math.min(f.hp, f.lowAt);
			c += lo / Math.max(1e-6, mineLo) * Dist.hit(f.lowAcc, me.eva) * Fighter.blow(f.lowDmg, me).mean() / f.delay - lo / mine * its; }
		return c + (f.deathDmg == null ? 0 : f.deathDmg.minus0(me.dr.plus(me.dr)).mean());
	}

	/** Damage a turn a deals b (hit odds times the mean blow through b's armour, per a's attack delay). */
	public static double dps(Fighter a, Fighter b) {
		double d = Dist.hit(a.acc, b.eva) * Fighter.blow(a.dmg, b).mean();
		if (a.sure > 0) d = (1 - a.sure) * d + a.sure * Fighter.blow(a.sureDmg != null ? a.sureDmg : a.dmg, b).mean();
		return d / Math.max(0.05, a.delay);
	}

	/** The simulation. */
	public static Result simulate(Fighter me0, List<Fighter> foes0, double healAt, int runs, long seed) { return simulate(me0, foes0, healAt, runs, seed, 1e9); }

	/** The next few turns of the fight as the hero stands now (FightCal's damage-rate check; BR's short looks ahead). */
	public static Result next(Hero h, List<Mob> mobs, double turns) {
		Fighter me = HeroView.of(h); List<Fighter> fs = new ArrayList<>(); for (Mob m : mobs) fs.add(FoeView.of(m, h));
		return simulate(me, fs, 0, RUNS, seedFor(h), turns);
	}

	/** maxT: the turns simulated at most. */
	public static Result simulate(Fighter me0, List<Fighter> foes0, double healAt, int runs, long seed, double maxT) {
		Look.Plan p = new Look.Plan().healAt(healAt); p.sleepersDone = false;
		Result r = new Result(); r.runs = runs;
		core(me0, foes0, p, runs, seed, maxT, r, null);
		return r;
	}

	/** Look's form (agent BR's options: first moves, then the policy): the one simulator for both (BR asked: MT's room
	 *  beside the hero, the foes' quirks and effects count in his look ahead too). */
	public static Look.Out play(Fighter me0, List<Fighter> foes, Look.Plan p, int runs, long seed) {
		Look.Out o = new Look.Out();
		core(p.me != null ? p.me : me0, foes, p, runs, seed, 1e9, null, o);
		return o;
	}

	/**
	 * The simulation, one loop: the hero's first moves (lose turns, drink, walk off: Look.Plan), then the policy (drink a
	 * known Healing below p.healAt of health once the last one is done healing; strike the weakest beside it; shoot the
	 * nearest coming; walk up to a sleeper, or with p.sleepersDone count the fight over), the foes at their own attack
	 * delays as they arrive (distance / speed; at most me.slots of the walkers beside the hero at once), the foes' quirks
	 * (Fighter). Fills r (FightModel's sums) and / or o (Look's).
	 */
	static void core(Fighter me, List<Fighter> foes0, Look.Plan p, int runs, long seed, double maxT, Result r, Look.Out o) {
		long t0 = PROF ? System.nanoTime() : 0;
		try { core0(me, foes0, p, runs, seed, maxT, r, o); }
		finally { if (PROF) { long dt = System.nanoTime() - t0; calls++; nanos += dt; String k = caller(); prof.merge(k, dt, Long::sum); } }
	}

	// (M2) the profiler (env PROF=1): time in the simulation by the first caller outside pebxel.bot, for SLOWPLAN lines
	public static final boolean PROF = Tune.on("PROF", false) || "1".equals(System.getenv("PROF"));
	public static int calls; public static long nanos; public static final Map<String, Long> prof = new TreeMap<>();
	static String caller() {
		for (StackTraceElement e : new Throwable().getStackTrace()) { String c = e.getClassName(); if (!c.startsWith("pebxel.bot.")) return c.substring(c.lastIndexOf('.') + 1) + "." + e.getMethodName() + ":" + e.getLineNumber(); }
		return "?";
	}
	/** The profile since the last call, cleared. */
	public static String profile() {
		StringBuilder b = new StringBuilder(String.format(Locale.ROOT, "sims %d %.1f ms:", calls, nanos / 1e6));
		prof.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue(), x.getValue())).limit(6).forEach(e -> b.append(String.format(Locale.ROOT, " %s %.1f", e.getKey(), e.getValue() / 1e6)));
		calls = 0; nanos = 0; prof.clear();
		return b.toString();
	}

	static void core0(Fighter me, List<Fighter> foes0, Look.Plan p, int runs, long seed, double maxT, Result r, Look.Out o) {
		int n = foes0.size();
		if (n == 0) { if (r != null) { r.pWin = 1; r.hpLeft = me.hp; } if (o != null) { o.pDone = 1; o.hpLeft = me.hp; } return; }
		SplittableRandom rnd = new SplittableRandom(seed);
		boolean fleeing = p.flee >= 0;
		double[] pH = new double[n], pF = new double[n], pR = new double[n], chase = new double[n];
		Dist[] bH = new Dist[n], bF = new Dist[n], bR = new Dist[n], bRage = new Dist[n], bLow = new Dist[n], bDeath = new Dist[n]; double[] pLow = new double[n], pHLow = new double[n], pZ = new double[n], pBurst = new double[n]; Dist[] bZ = new Dist[n];
		for (int i = 0; i < n; i++) {
			Fighter f = foes0.get(i);
			pH[i] = f.blockAll ? 0 : Dist.hit(me.acc, f.eva) * f.reach; bH[i] = Fighter.blow(me.dmg, f);
			pF[i] = Dist.hit(f.acc, me.eva); bF[i] = Fighter.blow(f.dmg, me);
			if (f.rageDmg != null) bRage[i] = Fighter.blow(f.rageDmg, me);
			if (f.lowDmg != null) { bLow[i] = Fighter.blow(f.lowDmg, me); pLow[i] = Dist.hit(f.lowAcc, me.eva); pHLow[i] = Dist.hit(me.acc, f.lowEva > 0 ? f.lowEva : f.eva); }
			if (f.deathDmg != null) bDeath[i] = f.deathDmg.minus0(me.dr.plus(me.dr));
			if (f.zapDmg != null) { bZ[i] = f.zapDmg; pZ[i] = Dist.hit(2 * f.acc, me.eva); }
			if (f.burstDmg != null) pBurst[i] = Dist.hit(2 * f.acc, me.eva);
			if (me.rangedDmg != null) { pR[i] = f.blockAll ? 0 : me.rangedSure ? 1 : Dist.hit(me.rangedAcc, f.eva); bR[i] = Fighter.blow(me.rangedDmg, f); }
			// (BR) walking off: a faster one strikes every (its blow + the step it takes to catch up); a shooter keeps shooting
			chase[i] = f.immobile ? 0 : f.ranged ? f.delay / 0.6 : f.speed > me.speed * 1.01 ? f.delay + 1 / f.speed : 0;
		}
		// (MT) room beside the hero: at most me.slots foes that walk up can be at it at once (a corridor: 2), the others
		// queue and step in as one falls, in the order they'd come; shooters and sleepers take no room
		Integer[] ord = new Integer[n]; for (int i = 0; i < n; i++) ord[i] = i;
		final double[] arrOf = new double[n];
		for (int i = 0; i < n; i++) { Fighter f = foes0.get(i); arrOf[i] = f.dist <= 1 ? 0 : f.asleep ? Double.MAX_VALUE : (f.dist - 1) / Math.max(0.1, f.ranged || f.immobile ? me.speed : f.speed); }
		Arrays.sort(ord, (a, b) -> Double.compare(arrOf[a], arrOf[b]));
		boolean[] queued = new boolean[n], held = new boolean[n]; double[] arrQ = new double[n];
		boolean hurtable = false; for (int i = 0; i < n; i++) hurtable |= pH[i] > 0 || pR[i] > 0 || foes0.get(i).asleep;
		double horizon = Math.min(maxT, hurtable ? 100 : 30);   // (MT: 100 turns at most; 30 against what it can't hurt (the crab awake): what matters then is how fast it loses)
		double[] losses = new double[runs];
		int deaths = 0, wins = 0, done = 0, used = 0, usedLive = 0; double tsum = 0, left = 0, leftO = 0;
		int[] hp = new int[n]; double[] next = new double[n], arr = new double[n], atk = new double[n]; boolean[] rage = new boolean[n], awake = new boolean[n];
		double[] zapEnd = new double[n], rageT = new double[n], burstAt = new double[n]; boolean[] pumpFirst = new boolean[n];   // (M2: Tune.KINDS)
		for (int run = 0; run < runs; run++) {
			int myHp = me.hp, sh = me.shield, heals = me.heals, shots = fleeing ? 0 : me.rangedShots, hl = Math.max(me.healLeft, p.healing), usedR = 0;   // (the same buff, whichever gave it)
			boolean drink = p.drink && heals > 0;
			for (int i = 0; i < n; i++) {
				Fighter f = foes0.get(i);
				hp[i] = f.hp + f.shield; rage[i] = f.ragingNow; rageT[i] = 0; awake[i] = !f.asleep; pumpFirst[i] = f.pumpNow;
				// arr: when it's beside the hero (a shooter stays put: the hero walks up to it); atk: when its blows start
				arr[i] = f.dist <= 1 ? 0 : f.asleep ? Double.MAX_VALUE : f.ranged || f.immobile ? (f.dist - 1) / Math.max(0.1, me.speed) : (f.dist - 1) / Math.max(0.1, f.speed);
				atk[i] = f.ranged && !f.asleep ? 0 : arr[i];
				// (M2) a shooter's bolts until the hero is beside it: from the start of his last step there (a step moves him
				// at once and then takes its time: the arena's shamans and DM-100s at 4 cells got 2 bolts off, not 3)
				zapEnd[i] = Tune.KINDS && f.ranged ? Math.max(0, arr[i] - 1 / Math.max(0.1, me.speed)) : arr[i];
				boolean beams = Tune.KINDS && f.burstEvery > 0 && f.burstDmg != null;   // (the eye's turns begin at once: its beam reaches from afar)
				next[i] = awake[i] ? (beams ? 0 : atk[i]) + f.delay * rnd.nextDouble() : Double.MAX_VALUE;   // (its turn falls somewhere in its first beat)
				burstAt[i] = beams && awake[i] ? next[i] + (f.burstReady ? 0 : f.burstCharge) : Double.MAX_VALUE;
				if (fleeing) next[i] = chase[i] > 0 && awake[i] ? (f.ranged ? 0 : arr[i]) + chase[i] * rnd.nextDouble() : Double.MAX_VALUE;
			}
			int room = me.slots;
			if (!fleeing) for (int oi = 0; oi < n; oi++) { int i = ord[oi]; Fighter f = foes0.get(i); queued[i] = false; held[i] = false;
				if (f.ranged || f.asleep || f.immobile) continue;
				if (room > 0) { room--; held[i] = true; } else { queued[i] = true; arrQ[i] = arr[i]; arr[i] = atk[i] = next[i] = Double.MAX_VALUE; } }
			else for (int i = 0; i < n; i++) { queued[i] = false; held[i] = false; }
			double t = 0, tH = 0; boolean dead = false, gone = false; int alive = n, lost = p.lose, steps = p.flee;
			for (int guard = 0; guard < 600 && alive > 0 && !dead && !gone && t < horizon; guard++) {
				int fi = -1; double ft = Double.MAX_VALUE;
				for (int i = 0; i < n; i++) if (hp[i] > 0 && next[i] < ft) { ft = next[i]; fi = i; }
				if (tH <= ft) {
					t = tH;
					if (hl > 0) { int x = Math.max(1, Math.min(hl, Math.round(hl * 0.25f))); myHp = Math.min(me.ht, myHp + x); hl -= x; }   // (Healing heals a quarter of what's left a turn: Healing.healingThisTick)
					if (drink) { drink = false; heals--; usedR++; hl += me.healAmount; tH += 1; busy(foes0, hp, t, 1, arr, atk, zapEnd); continue; }
					if (lost > 0) { lost--; tH += 1; busy(foes0, hp, t, 1, arr, atk, zapEnd); continue; }
					if (steps >= 0) { if (steps-- == 0) { gone = true; break; } tH += 1 / Math.max(0.1, me.speed); continue; }
					if (heals > 0 && p.healAt > 0 && hl == 0 && myHp < me.ht * p.healAt) { heals--; usedR++; if (Tune.HEALTICK) hl += me.healAmount; else myHp = Math.min(me.ht, myHp + me.healAmount); tH += 1; busy(foes0, hp, t, 1, arr, atk, zapEnd); continue; }
					// a target beside me (the weakest), else a shot at the nearest one coming, else wait for one / walk to a sleeper
					int tg = -1, best = Integer.MAX_VALUE;
					for (int i = 0; i < n; i++) if (hp[i] > 0 && t >= arr[i] - 1e-9 && hp[i] < best) { best = hp[i]; tg = i; }
					if (tg >= 0) {
						Fighter f = foes0.get(tg);
						if (rage[tg] && f.rageDecay > 0 && decay(f, tg, t, hp, rageT)) { alive--; room = free(foes0, tg, room, queued, held, arrQ, arr, atk, next, t, rnd); tH += me.delay; continue; }
						if (!awake[tg] || rnd.nextDouble() < (bLow[tg] != null && hp[tg] <= f.lowAt ? pHLow[tg] : pH[tg])) { int d = bH[tg].draw(rnd.nextDouble()); if (burstAt[tg] < Double.MAX_VALUE && t >= burstAt[tg] - f.burstCharge - 1e-9) d /= 4; hp[tg] -= d; }   // (a sleeper: a surprise blow, sure; M2: an eye charging its beam takes a quarter, Eye.damage)
						if (!awake[tg]) { awake[tg] = true; atk[tg] = t; next[tg] = t + f.delay * (0.5 + rnd.nextDouble()); }
						if (hp[tg] <= 0 && f.rageHp > 0 && !rage[tg]) { rage[tg] = true; hp[tg] = f.rageHp; rageT[tg] = t; if (f.rageDecay > 0) next[tg] = Math.max(next[tg], t) + 1; }   // (Brute.triggerEnrage spends a turn)
						if (hp[tg] <= 0) {
							alive--; room = free(foes0, tg, room, queued, held, arrQ, arr, atk, next, t, rnd);
							if (bDeath[tg] != null) { int d = bDeath[tg].draw(rnd.nextDouble()); myHp -= Math.max(0, d - sh); sh = Math.max(0, sh - d); if (myHp <= 0) dead = true; }
						}
						tH += me.delay; busy(foes0, hp, t, me.delay, arr, atk, zapEnd);
						continue;
					}
					int ni = -1; double nd = Double.MAX_VALUE, na = Double.MAX_VALUE; int sl = -1;
					for (int i = 0; i < n; i++) if (hp[i] > 0) {
						if (awake[i]) { na = Math.min(na, arr[i]); double d = foes0.get(i).dist - t * foes0.get(i).speed; if (d < nd) { nd = d; ni = i; } }
						else if (sl < 0 || foes0.get(i).dist < foes0.get(sl).dist) sl = i;
					}
					if (shots > 0 && ni >= 0) {
						shots--;
						if (rnd.nextDouble() < pR[ni]) hp[ni] -= bR[ni].draw(rnd.nextDouble());
						if (hp[ni] <= 0 && foes0.get(ni).rageHp > 0 && !rage[ni]) { rage[ni] = true; hp[ni] = foes0.get(ni).rageHp; rageT[ni] = t; if (foes0.get(ni).rageDecay > 0) next[ni] = Math.max(next[ni], t) + 1; }
						if (hp[ni] <= 0) { alive--; room = free(foes0, ni, room, queued, held, arrQ, arr, atk, next, t, rnd); }
						tH += me.rangedDelay; busy(foes0, hp, t, me.rangedDelay, arr, atk, zapEnd);
					} else if (na < Double.MAX_VALUE) tH = Math.max(na, t + 0.01);
					else if (sl >= 0 && p.sleepersDone) { gone = true; break; }   // (BR: only sleepers left: the fight is over, they can be left be)
					else if (sl >= 0) { arr[sl] = t + Math.max(0, foes0.get(sl).dist - 1); tH = arr[sl]; }   // (walk up to it: it sleeps on)
					else tH = t + 1;
				} else {
					Fighter f = foes0.get(fi); t = ft;
					if (rage[fi] && f.rageDecay > 0 && decay(f, fi, t, hp, rageT)) { alive--; room = free(foes0, fi, room, queued, held, arrQ, arr, atk, next, t, rnd); continue; }   // (its rage ran out: it dies)
					boolean lo = bLow[fi] != null && hp[fi] <= f.lowAt, zap = bZ[fi] != null && (t < zapEnd[fi] - 1e-9 || fleeing);
					boolean may = fleeing || t >= atk[fi] - 1e-9;
					double nextT = t + (fleeing ? chase[fi] : f.delay);
					boolean beam = burstAt[fi] < Double.MAX_VALUE && t >= burstAt[fi] - 1e-9, charging = !beam && burstAt[fi] < Double.MAX_VALUE && t >= burstAt[fi] - f.burstCharge - 1e-9;
					if (beam || (!Tune.KINDS && !zap && f.burstEvery > 0 && may && rnd.nextDouble() * f.burstEvery < 1)) {   // (MT: the Eye's beam, an attack in so many: magic, past armour; M2: its own cycle, from afar too)
						if (rnd.nextDouble() < pBurst[fi]) { int d = (int) Math.round(f.burstDmg.draw(rnd.nextDouble()) * me.taken); myHp -= Math.max(0, d - sh); sh = Math.max(0, sh - d); if (myHp <= 0) dead = true; }
						if (beam) burstAt[fi] = t + f.burstEvery;
					} else if (charging) {
						// (the eye charging its beam: no blow)
					} else if (f.pump > 0 && may && !fleeing && !zap && (pumpFirst[fi] || rnd.nextDouble() < (lo ? 0.5 : f.pump))) {
						// (M2: Goo pumps up, the hero steps out of the blast's reach and back: neither strikes for a while)
						pumpFirst[fi] = false; nextT = t + 3 * f.delay; tH = Math.max(tH, t + 2);
					} else if (may && rnd.nextDouble() < (zap ? pZ[fi] : lo ? pLow[fi] : pF[fi])) {
						int d = (zap ? bZ[fi] : rage[fi] && bRage[fi] != null ? bRage[fi] : lo ? bLow[fi] : bF[fi]).draw(rnd.nextDouble());
						if (!zap && f.hitDirect > 0) d += (int) Math.round(f.hitDirect);   // (MT: what its blow sets off past armour: fire, poison, bleeding)
						if (me.taken != 1) d = (int) Math.round(d * me.taken);
						myHp -= Math.max(0, d - sh); sh = Math.max(0, sh - d);
						if (f.leech > 0) hp[fi] = Math.min(f.ht, hp[fi] + d);
						if (myHp <= 0) dead = true;
						if (!zap && !fleeing && f.fleeHit > 0 && rnd.nextDouble() < f.fleeHit) { nextT = t + f.fleeT; arr[fi] = atk[fi] = t + f.fleeT; }   // (M2: a spinner runs off once it has poisoned)
					}
					next[fi] = nextT;
				}
			}
			if (dead) deaths++;
			else if (alive == 0) { wins++; done++; }
			else if (gone) done++;
			losses[run] = me.hp - Math.max(0, myHp); tsum += t; left += Math.max(0, myHp); leftO += dead ? 0 : Math.min(me.ht, myHp + hl); used += usedR; if (!dead) usedLive += usedR;
		}
		if (r != null) {
			Arrays.sort(losses);
			double s = 0; for (double l : losses) s += l;
			r.pDeath = deaths / (double) runs; r.pWin = wins / (double) runs; r.lossMean = s / runs; r.loss90 = losses[Math.min(runs - 1, (int) (runs * 0.9))];
			r.time = tsum / runs; r.hpLeft = left / runs; r.healsUsed = used / (double) runs; r.healsLive = usedLive / (double) runs;
		}
		if (o != null) { o.pDeath = deaths / (double) runs; o.pDone = done / (double) runs; o.hpLeft = leftO / runs; o.healsUsed = used / (double) runs; o.healsLive = usedLive / (double) runs; o.time = tsum / runs; }
	}

	/** (M2) The hero busy for dt (a blow, a shot, a drink) with a shooter or a foe that can't move not reached yet: his
	 *  walk to it starts that much later (it bolts on meanwhile; the old sums had him there on time whatever he did). */
	static void busy(List<Fighter> fs, int[] hp, double t, double dt, double[] arr, double[] atk, double[] zapEnd) {
		if (!Tune.KINDS) return;
		for (int i = 0; i < fs.size(); i++) { Fighter f = fs.get(i);
			if (hp[i] <= 0 || !(f.ranged || f.immobile) || f.asleep || arr[i] <= t + 1e-9 || arr[i] == Double.MAX_VALUE) continue;
			arr[i] += dt; zapEnd[i] += dt; if (f.immobile) atk[i] += dt; }
	}

	/** (M2) A raging foe's shield running down (a brute's 4 a turn): true when it's gone (the foe dies). */
	static boolean decay(Fighter f, int i, double t, int[] hp, double[] rageT) {
		int d = (int) Math.floor(f.rageDecay * (t - rageT[i]) + 1e-9);
		if (d > 0) { hp[i] -= d; rageT[i] += d / f.rageDecay; }
		return hp[i] <= 0;
	}

	/** A foe fell: its room beside the hero goes to the first one queued (it steps in now, or when it would have come). */
	static int free(List<Fighter> fs, int dead, int room, boolean[] queued, boolean[] held, double[] arrQ, double[] arr, double[] atk, double[] next, double t, SplittableRandom rnd) {
		if (queued[dead]) { queued[dead] = false; return room; }
		if (!held[dead]) return room;
		held[dead] = false;
		int q = -1; for (int i = 0; i < fs.size(); i++) if (queued[i] && (q < 0 || arrQ[i] < arrQ[q])) q = i;
		if (q < 0) return room + 1;
		queued[q] = false; held[q] = true; arr[q] = atk[q] = Math.max(t + 1, arrQ[q]); next[q] = arr[q] + fs.get(q).delay * rnd.nextDouble();
		return room;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// for the assistant's hooks (Assist, behind AS_OFIGHT)
	// ---------------------------------------------------------------------------------------------------------------
	private static Result cache; private static float cacheT = -1; private static int cachePos = -1, cacheKey = -1; private static Hero cacheH;

	/** The fight with these mobs, worked out once per turn and place (the plan asks several times). */
	public static Result engaged(Hero h, List<Mob> mobs) {
		if (mobs.isEmpty()) return null;
		int key = 0; for (Mob m : mobs) key = key * 31 + m.pos * 7 + m.HP;
		float now = com.shatteredpixel.shatteredpixeldungeon.actors.Actor.now();
		if (cacheH == h && cacheT == now && cachePos == h.pos && cacheKey == key) return cache;
		cacheH = h; cacheT = now; cachePos = h.pos; cacheKey = key;
		return cache = evaluate(h, mobs);
	}

	static final double[] HEALAT = { 0.15, 0.25, 0.35, 0.5, 0.7 };
	private static double planC = -1; private static float planT = -1; private static int planPos = -1, planKey = -1; private static Hero planH;

	/** When to drink a known Healing in this fight (MT): the policy "drink below k of the most health" that the
	 *  simulation finds best, each potion drunk costing Tune.HEALCOST of death odds (it isn't there for the next fight);
	 *  0: not at all. The hero drinks now if its health is below that share. */
	public static double healAt(Hero h, List<Mob> mobs) {
		if (mobs.isEmpty()) return 0;
		int key = 0; for (Mob m : mobs) key = key * 31 + m.pos * 7 + m.HP;
		float now = com.shatteredpixel.shatteredpixeldungeon.actors.Actor.now();
		if (planH == h && planT == now && planPos == h.pos && planKey == key) return planC;
		planH = h; planT = now; planPos = h.pos; planKey = key;
		Fighter me = HeroView.of(h);
		if (me.heals <= 0) return planC = 0;
		List<Fighter> fs = new ArrayList<>(); for (Mob m : mobs) fs.add(FoeView.of(m, h));
		long sd = seedFor(h);
		int runs = Math.max(60, RUNS / 3);
		Result r0 = simulate(me, fs, 0, runs, sd);
		double best = r0.pDeath, bk = 0;
		// (M2, BR's suggestion) a potion drunk costs what it is worth kept (Budget.rescue: the n-th of n, then the one
		// before it), as a share of a death (the floors ahead), on the runs the hero lives through; AS_BHEALK scales it
		double c1 = Tune.HEALCOST, c2 = Tune.HEALCOST;
		if (Tune.HEALBUD) { double R = Math.max(1, Budget.ahead(h)); c1 = Tune.HEALK * Budget.rescue(h, me.heals, 1) / R; c2 = me.heals > 1 ? Tune.HEALK * Budget.rescue(h, me.heals - 1, 1) / R : c1; }
		for (double k : HEALAT) {
			Result r = simulate(me, fs, k, runs, sd);
			double u = r.healsLive, sc = Tune.HEALBUD ? r.pDeath + (u <= 1 ? u * c1 : c1 + (u - 1) * c2) : r.pDeath + Tune.HEALCOST * r.healsUsed * (1 - r.pDeath);
			if (r0.pDeath >= 0.5 && r.healsUsed > 0) sc -= Tune.HOPE;   // (MT: a fight it likely loses: drinking buys turns the simulation doesn't use (a step away, the stairs, an ally): near-ties go to drinking)   // (MT: a potion is only worth keeping to a hero who lives: in a fight it would lose, it was never drunk)
			if (sc < best - 1e-9) { best = sc; bk = k; }
		}
		return planC = bk;
	}

	/** The health to rest up to before going on (OG thrift_need): a fight with the floor's worst common kind (duelCost),
	 *  with a margin; between a quarter and 70% of the most. Resting costs food (a point every 10 turns, 2.6), so less
	 *  resting where the floor is easy. */
	public static int restNeed(Hero h) {
		Fighter me = HeroView.of(h); me.heals = 0; me.rangedShots = 0;
		double worst = 0;
		for (String k : MobTable.kinds(Dungeon.depth)) { Fighter f = MobTable.fighter(k); if (f != null) worst = Math.max(worst, duelCost(me, f)); }
		int need = (int) Math.ceil(worst * Tune.RESTK) + 2, lo = (int) (h.HT * Tune.RESTLO), hi = (int) (h.HT * Tune.RESTHI);
		return Math.max(lo, Math.min(hi, need));
	}
}
