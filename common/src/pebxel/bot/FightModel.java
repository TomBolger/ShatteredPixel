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
		public double pDeath, pWin, lossMean, loss90, time, hpLeft;
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
		if (f.lowAt > 0 && f.lowDmg != null) c += Math.min(f.hp, f.lowAt) / mine * (Dist.hit(f.lowAcc, me.eva) * Fighter.blow(f.lowDmg, me).mean() / f.delay - its);
		return c + (f.deathDmg == null ? 0 : f.deathDmg.minus0(me.dr.plus(me.dr)).mean());
	}

	/** Damage a turn a deals b (hit odds times the mean blow through b's armour, per a's attack delay). */
	public static double dps(Fighter a, Fighter b) { return Dist.hit(a.acc, b.eva) * Fighter.blow(a.dmg, b).mean() / Math.max(0.05, a.delay); }

	/** The simulation. */
	public static Result simulate(Fighter me0, List<Fighter> foes0, double healAt, int runs, long seed) {
		Result r = new Result(); r.runs = runs;
		if (foes0.isEmpty()) { r.pWin = 1; r.hpLeft = me0.hp; return r; }
		SplittableRandom rnd = new SplittableRandom(seed);
		int n = foes0.size();
		// per foe: hit odds both ways, blow distributions (fixed for the fight)
		double[] pH = new double[n], pF = new double[n], pR = new double[n];
		Dist[] bH = new Dist[n], bF = new Dist[n], bR = new Dist[n], bRage = new Dist[n], bLow = new Dist[n], bDeath = new Dist[n]; double[] pLow = new double[n];
		for (int i = 0; i < n; i++) {
			Fighter f = foes0.get(i);
			pH[i] = Dist.hit(me0.acc, f.eva); bH[i] = Fighter.blow(me0.dmg, f);
			pF[i] = Dist.hit(f.acc, me0.eva); bF[i] = Fighter.blow(f.dmg, me0);
			if (f.rageDmg != null) bRage[i] = Fighter.blow(f.rageDmg, me0);
			if (f.lowDmg != null) { bLow[i] = Fighter.blow(f.lowDmg, me0); pLow[i] = Dist.hit(f.lowAcc, me0.eva); }
			if (f.deathDmg != null) bDeath[i] = f.deathDmg.minus0(me0.dr.plus(me0.dr));
			if (me0.rangedDmg != null) { pR[i] = me0.rangedSure ? 1 : Dist.hit(me0.rangedAcc, f.eva); bR[i] = Fighter.blow(me0.rangedDmg, f); }
		}
		double[] losses = new double[runs];
		int deaths = 0, wins = 0; double tsum = 0, left = 0;
		int[] hp = new int[n]; double[] next = new double[n], arr = new double[n]; boolean[] rage = new boolean[n], awake = new boolean[n];
		for (int run = 0; run < runs; run++) {
			int myHp = me0.hp, sh = me0.shield, heals = me0.heals, shots = me0.rangedShots;
			for (int i = 0; i < n; i++) {
				Fighter f = foes0.get(i);
				hp[i] = f.hp + f.shield; rage[i] = false; awake[i] = !f.asleep;
				arr[i] = f.ranged || f.dist <= 1 ? 0 : f.asleep ? Double.MAX_VALUE : (f.dist - 1) / Math.max(0.1, f.speed);
				next[i] = awake[i] ? arr[i] + f.delay * rnd.nextDouble() : Double.MAX_VALUE;   // (its turn falls somewhere in its first beat)
			}
			double t = 0, tH = 0; boolean dead = false; int alive = n;
			for (int guard = 0; guard < 600 && alive > 0 && !dead; guard++) {
				int fi = -1; double ft = Double.MAX_VALUE;
				for (int i = 0; i < n; i++) if (hp[i] > 0 && next[i] < ft) { ft = next[i]; fi = i; }
				if (tH <= ft) {
					t = tH;
					if (heals > 0 && healAt > 0 && myHp < me0.ht * healAt) { heals--; myHp = Math.min(me0.ht, myHp + me0.healAmount); tH += 1; continue; }
					// a target beside me (the weakest), else a shot at the nearest one coming, else wait for one / walk to a sleeper
					int tg = -1, best = Integer.MAX_VALUE;
					for (int i = 0; i < n; i++) if (hp[i] > 0 && t >= arr[i] - 1e-9 && hp[i] < best) { best = hp[i]; tg = i; }
					if (tg >= 0) {
						Fighter f = foes0.get(tg);
						if (!awake[tg] || rnd.nextDouble() < pH[tg]) hp[tg] -= bH[tg].draw(rnd.nextDouble());   // (a sleeper: a surprise blow, sure)
						if (!awake[tg]) { awake[tg] = true; next[tg] = t + f.delay * (0.5 + rnd.nextDouble()); }
						if (hp[tg] <= 0 && f.rageHp > 0 && !rage[tg]) { rage[tg] = true; hp[tg] = f.rageHp; }
						if (hp[tg] <= 0) {
							alive--;
							if (bDeath[tg] != null) { int d = bDeath[tg].draw(rnd.nextDouble()); myHp -= Math.max(0, d - sh); sh = Math.max(0, sh - d); if (myHp <= 0) dead = true; }
						}
						tH += me0.delay;
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
						if (hp[ni] <= 0 && foes0.get(ni).rageHp > 0 && !rage[ni]) { rage[ni] = true; hp[ni] = foes0.get(ni).rageHp; }
						if (hp[ni] <= 0) alive--;
						tH += me0.rangedDelay;
					} else if (na < Double.MAX_VALUE) tH = Math.max(na, t + 0.01);
					else if (sl >= 0) { arr[sl] = t + Math.max(0, foes0.get(sl).dist - 1); tH = arr[sl]; }   // (walk up to it: it sleeps on)
					else tH = t + 1;
				} else {
					Fighter f = foes0.get(fi); t = ft;
					boolean lo = bLow[fi] != null && hp[fi] <= f.lowAt;
					if (t >= arr[fi] - 1e-9 && rnd.nextDouble() < (lo ? pLow[fi] : pF[fi])) {
						int d = (rage[fi] && bRage[fi] != null ? bRage[fi] : lo ? bLow[fi] : bF[fi]).draw(rnd.nextDouble());
						if (me0.taken != 1) d = (int) Math.round(d * me0.taken);
						myHp -= Math.max(0, d - sh); sh = Math.max(0, sh - d);
						if (f.leech > 0) hp[fi] = Math.min(f.ht, hp[fi] + d);
						if (myHp <= 0) dead = true;
					}
					next[fi] = t + f.delay;
				}
			}
			if (dead) deaths++;
			else if (alive == 0) wins++;
			losses[run] = me0.hp - Math.max(0, myHp); tsum += t; left += Math.max(0, myHp);
		}
		Arrays.sort(losses);
		double s = 0; for (double l : losses) s += l;
		r.pDeath = deaths / (double) runs; r.pWin = wins / (double) runs; r.lossMean = s / runs; r.loss90 = losses[Math.min(runs - 1, (int) (runs * 0.9))];
		r.time = tsum / runs; r.hpLeft = left / runs;
		return r;
	}

}
