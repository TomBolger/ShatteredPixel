package pebxel.bot;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import com.watabou.utils.Random;

/**
 * A mob's side of a fight. What a player knows: its kind's numbers (the source, Thomas's rule allows that), its health
 * (the health bar), its buffs as drawn (enraged, paralysed...), where it is. The damage and armour rolls are the mob's
 * own methods, run on a generator of the bot's own (Random.pushGenerator(seed) ... popGenerator: the game's numbers
 * are untouched) and read as distributions; Goo's roll is built here instead (its damageRoll clears its pump-up).
 * Its evasion is the defenseSkill field (Mob.defenseSkill(enemy) is 0 for a mob that hasn't seen the hero: that's the
 * AI's own state; the models take surprise from what is drawn, the Zz).
 */
public final class FoeView {
	private FoeView() { }

	static final int SAMPLES = 600;

	public static Fighter of(Mob m, Hero h) {
		Fighter f = new Fighter();
		f.name = m.getClass().getSimpleName();
		f.hp = m.HP; f.ht = m.HT; f.shield = m.shielding();
		f.boss = Char.hasProp(m, Char.Property.BOSS) || Char.hasProp(m, Char.Property.MINIBOSS);
		try { f.acc = m.attackSkill(h); } catch (Throwable e) { f.acc = 10; }
		f.eva = m.defenseSkill;
		if (m.paralysed > 0) f.eva = 0;
		try { f.delay = m.attackDelay(); } catch (Throwable e) { f.delay = 1; }
		try { f.speed = m.speed(); } catch (Throwable e) { f.speed = 1; }
		f.dist = h == null ? 1 : Math.max(1, com.shatteredpixel.shatteredpixeldungeon.Dungeon.level.distance(m.pos, h.pos));
		f.asleep = m.state == m.SLEEPING;
		f.ranged = shooter(m);
		if (Tune.KINDS && h != null && f.ranged && f.dist > 1) f.dist = Math.max(f.dist, walk(h.pos, m.pos, f.dist + 8));   // (M2: the hero's walk to a shooter, round what he has seen of the walls: every step a bolt)
		if (m instanceof Goo) {   // (Goo.attackSkill / damageRoll: enraged below half health, pumped up x3)
			boolean low = m.HP * 2 <= m.HT; if (low) { f.acc = 15; f.eva = (int) (m.defenseSkill * 1.5); } Object pu = HeroView.field(m, "pumpedUp"); boolean pumped = pu instanceof Integer && (Integer) pu > 0;
			int max = low ? 12 : 8;
			f.dmg = pumped && !Tune.KINDS ? Dist.normal(3, 3 * max) : Dist.normal(1, max);
			f.dr = Dist.normal(0, 2);
			if (!low && (!pumped || Tune.KINDS)) { f.lowAt = m.HT / 2; f.lowAcc = 15; f.lowEva = (int) (m.defenseSkill * 1.5); f.lowDmg = Dist.normal(1, 12); }
			if (Tune.KINDS) { f.pump = 0.2; f.pumpNow = pumped; f.hitDirect = 1.2 * Tune.FOEFX; }   // (M2: Goo.doAttack pumps up 1 turn in 5 (2 when low): the Assist's tactic steps out of reach and lets it waste the blast; its ooze, a third of its blows, ~1 a turn till washed)
		} else {
			int[] v = new int[SAMPLES];
			Random.pushGenerator(0x5eed + m.getClass().getName().hashCode());
			try {
				for (int i = 0; i < SAMPLES; i++) v[i] = m.damageRoll();
				f.dmg = Dist.of(v, SAMPLES);
				for (int i = 0; i < SAMPLES; i++) v[i] = m.drRoll();
				f.dr = Dist.of(v, SAMPLES);
			} catch (Throwable e) {
				f.dmg = Dist.normal(1, 4 + com.shatteredpixel.shatteredpixeldungeon.Dungeon.depth); f.dr = Dist.point(0);
			} finally { Random.popGenerator(); }
		}
		special(f, m.getClass().getSimpleName(), m.HT);
		if (m instanceof GreatCrab && !f.asleep && Tune.CRAB) f.blockAll = true;
		if (Tune.FOES) effects(f, f.name);
		if (Tune.FOES && m instanceof Ghoul) for (Mob o : com.shatteredpixel.shatteredpixeldungeon.Dungeon.level.mobs)   // (MT: a ghoul gets up again while its pair is near: Ghoul.die)
			if (o != m && o instanceof Ghoul && o.isAlive() && com.shatteredpixel.shatteredpixeldungeon.Dungeon.level.heroFOV[o.pos]) { f.rageHp = (int) (f.ht / 2 * Tune.FOEFX); f.rageDmg = f.dmg; break; }
		if (Tune.FOES && m instanceof GnollTrickster) f.dmg = f.dmg.shift(4);   // (MT: each dart that lands poisons, more each time: GnollTrickster.attackProc, ~4 a hit early on)
		if (Tune.FOES && m instanceof Swarm && m.HP > 8) f.delay /= 1.6;   // (MT: each blow that doesn't kill it splits it: more of them biting, Swarm.defenseProc)   // (MT: GreatCrab.defenseSkill / damage: every blow, shot and zap from the one it faces, as the screen shows: "blocks")
		if (m instanceof Brute && rage(m)) { f.rageHp = 0; f.ragingNow = Tune.KINDS; }   // (already raging: its shield is in shielding())
		if (Tune.KINDS) kinds(f, m);
		return f;
	}

	/** (M2) The kinds' behaviour that changes a fight's course (their source; sized against the game in the Arena):
	 *  AS_OKINDS=0 for the old sums. */
	static void kinds(Fighter f, Mob m) {
		if (m instanceof Brute) f.rageDecay = 4;   // (BruteRage.act: the shield loses 4 a turn, it dies when it's gone)
		if (m instanceof Elemental) f.ranged = false;   // (it walks up and strikes; its bolt (a fire, a frost) only every 3-5 turns)
		if (Char.hasProp(m, Char.Property.IMMOVABLE)) { f.immobile = true; f.ranged = false; }   // (DM-201: stays put)
		if (m instanceof Eye) {   // (Eye.doAttack: a charge of 2 turns, the beam, 4-6 turns' cooldown (blows beside it only): beam to beam 7)
			f.ranged = false; f.burstEvery = 7; f.burstCharge = 2; f.burstReady = m.HP > 0 && Boolean.TRUE.equals(HeroView.field(m, "beamCharged"));
		}
		if (m instanceof Monk) f.reach = 0.75;   // (Monk.defenseSkill: focused, it parries a blow, focus back in 6-7 turns of its time (moving, sooner): the arena's monks lasted 1.4 times the old sums)
		if (m instanceof Scorpio) f.reach = 0.7;   // (Scorpio.getCloser: beside the hero it backs off: struck less often. In an open room it shot half as often too, but in the games' corridors it shot as often as ever (M2-a: 9.9 health in 5 turns against the model's 4.3 with half the shots): its shots as they are)
		if (m instanceof Spinner) { f.fleeHit = 0.5; f.fleeT = 7; f.hitDirect = 5 * Tune.FOEFX; }   // (Spinner.attackProc: half its bites poison for 7-8 (15 in all, Poison.act) and it flees till the poison is done)
	}

	/** The kinds' quirks that change a fight's sums (from their source). */
	static void special(Fighter f, String n, int ht) {
		switch (n) {
			case "Slime": case "CausticSlime": f.slimeCap = true; break;
			case "Skeleton": f.deathDmg = Dist.normal(6, 12); break;   // (its bones burst for 6-12 beside it, less armour rolled twice)
			case "Bat": f.leech = 1; break;            // (it heals what it bites)
			case "DM100": f.zapDmg = Dist.normal(3, 10); break;   // (DM100.zap, Shaman.zap, Warlock.zap, Eye.deathGaze)
			case "BlueShaman": case "RedShaman": case "PurpleShaman": case "Shaman": f.zapDmg = Dist.normal(6, 15); break;
			case "Warlock": f.zapDmg = Dist.normal(12, 18); break;
			case "Eye": f.zapDmg = Dist.normal(30, 50); break;
			case "Brute": case "ArmoredBrute": f.rageHp = ht / 2 + 4; f.rageDmg = Dist.normal(15, 40); break;   // (BruteRage: it fights on, shielded, at 15-40)
		}
	}

	/** (MT) What the deep kinds' blows set off past armour, from their source, sized by the damage-rate check (FIGHTRATE:
	 *  the model had these at 1/2 to 1/20 of the real health lost over 5 turns): AS_OFOEFX scales them. */
	static void effects(Fighter f, String n) {
		double k = Tune.FOEFX;
		switch (n) {
			case "Eye": f.burstDmg = Dist.normal(30, 50); f.burstEvery = Tune.KINDS ? f.burstEvery : 6; f.zapDmg = null; break;   // (Eye.doAttack: charge, beam, 4-6 turns' cooldown, beside the hero too)
			case "FireElemental": f.hitDirect = 6 * k; break;   // (Elemental.meleeProc: sets it burning)
			case "Spinner": if (!Tune.KINDS) f.hitDirect = 5 * k; break;         // (half its bites poison for 7-8: M2's kinds() with its fleeing)
			case "RipperDemon": f.hitDirect = 2 * k; break;     // (it leaps in for more)
			case "Succubus": f.hitDirect = 1 * k; f.leech = 1; break;   // (charm: a blow lost, its health back)
		}
	}

	/** (M2) Steps from a to b over the passable cells the hero has seen (b itself counted whatever it is), at most
	 *  limit (more: limit); its own search, PathFinder's shared arrays untouched (the game reads them). */
	static int walk(int a, int b, int limit) {
		com.shatteredpixel.shatteredpixeldungeon.levels.Level L = com.shatteredpixel.shatteredpixeldungeon.Dungeon.level;
		int len = L.length(), w = L.width(); if (a == b) return 0;
		int[] d = new int[len]; java.util.Arrays.fill(d, -1); int[] q = new int[len]; int qh = 0, qt = 0;
		d[a] = 0; q[qt++] = a;
		while (qh < qt) {
			int c = q[qh++]; if (d[c] >= limit) break;
			for (int k : com.watabou.utils.PathFinder.NEIGHBOURS8) {
				int n = c + k; if (n < 0 || n >= len || d[n] >= 0 || Math.abs(n % w - c % w) > 1) continue;
				if (n == b) return d[c] + 1;
				if (!L.passable[n] || !(L.visited[n] || L.mapped[n] || L.heroFOV[n])) continue;
				d[n] = d[c] + 1; q[qt++] = n;
			}
		}
		return limit;
	}

	static boolean rage(Mob m) { for (Buff b : m.buffs()) if (b.getClass().getSimpleName().equals("BruteRage")) return true; return false; }

	/** Attacks from afar (no walk up to the hero first). */
	public static boolean shooter(Mob m) {
		return m instanceof Shaman || m instanceof Warlock || m instanceof Eye || m instanceof Scorpio || m instanceof GnollTrickster || m instanceof DM100
				|| m instanceof Tengu || m instanceof Elemental || m instanceof FungalSentry || m instanceof DwarfKing || m instanceof YogFist;
	}
}
