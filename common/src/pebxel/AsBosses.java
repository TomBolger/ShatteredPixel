package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.levels.CavesBossLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.HallsBossLevel;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.PrisonBossLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.watabou.utils.PathFinder;
import java.util.*;
import pebxel.Assist.Target;
import static pebxel.Assist.*;

/**
 * The Assistant's skill: each boss's own mechanics (Goo, Tengu, DM-300, the Dwarf King, Yog) (agent K3; the strategy
 * reference's chapter 4 and the bosses' source). Two hooks: {@link #danger} adds the bosses' telegraphed blows to the
 * cells the plan keeps off (Assist.hazards), {@link #tactics} a move of its own on a boss's floor. Only what the
 * screen shows is used: bombs and shockers lying where they were thrown, flames, the marked cells of falling rocks and
 * of Yog's beams, a summoning circle's glow, the electrified floor, a pylon lit up, a boss shown as immune.
 */
final class AsBosses {
	private AsBosses() {}

	static final boolean ON = opt("BOSSES", true);
	static final boolean PYLON = opt("BPYLON", true), KING2 = opt("BKING2", true), FISTS = opt("BFISTS", true), LEAPS = opt("BLEAPS", true), YOG = opt("BYOG", true);
	static final int YOGHEAL = optInt("BYOGHEAL", 45);   // (Yog's floor: Healing below this share of health with a foe at hand or bleeding)

	// =============================================================================================================
	// the cells to keep off (worked out once a turn)
	// =============================================================================================================
	static boolean[] dz = new boolean[0], soon = new boolean[0]; static boolean dzAny;
	private static Level dzL; private static float dzAt = -1; private static int dzPos = -1;
	private static Object tenguState; private static Level stateL;

	/** Is c a cell a boss's telegraphed blow will hit (keep off it)? */
	static boolean danger(int c) {
		if (!ON) return false;
		Level l = Dungeon.level; if (l == null || Dungeon.hero == null) return false;
		if (dzL != l || dzAt != Actor.now() || dzPos != Dungeon.hero.pos || dz.length != l.length()) compute(l);
		return c >= 0 && c < dz.length && dz[c];
	}

	static void compute(Level l) {
		dzL = l; dzAt = Actor.now(); dzPos = Dungeon.hero.pos;
		if (dz.length != l.length()) { dz = new boolean[l.length()]; soon = new boolean[l.length()]; } else { Arrays.fill(dz, false); Arrays.fill(soon, false); }
		dzAny = false;
		boolean mine = l instanceof com.shatteredpixel.shatteredpixeldungeon.levels.MiningLevel;
		if (LEAPS) leaps(l);
		if (!Dungeon.bossLevel() && !mine) return;
		rocks(l);   // (falling rocks: DM-300's, the Gnoll Geomancer's)
		if (mine) return;
		if (l instanceof PrisonBossLevel) tengu(l);
		if (l instanceof CavesBossLevel) {   // the electrified floor (traps' tiles and water) while a pylon is lit
			Blob e = l.blobs.get(CavesBossLevel.PylonEnergy.class);
			if (e != null && e.volume > 0 && e.cur != null) for (int c = 0; c < l.length() && c < e.cur.length; c++) if (e.cur[c] > 0) mark(c);
		}
		for (Mob m : new ArrayList<>(l.mobs)) {
			if (m instanceof DwarfKing) for (DwarfKing.Summoning s : m.buffs(DwarfKing.Summoning.class)) mark(s.getPos());   // (standing on a circle as it finishes: 20-40)
			if (m instanceof YogDzewa) {   // the beams' marked cells
				Object tc = field(m, "targetedCells");
				if (tc instanceof List) for (Object o : (List<?>) tc) if (o instanceof Integer) for (int p : new Ballistica(m.pos, (Integer) o, Ballistica.WONT_STOP).path) mark(p);
			}
		}
		if (dzAny) for (int c = 0; c < dz.length; c++) if (l.solid[c]) dz[c] = false;
	}

	/** A ripper demon's leap: the cell it's aimed at is shown (RipperDemon: GameScene.targetedCell, while the demon or
	 *  the cell is in sight), and only whoever stands there as it lands is hit (bleeding: 0.75 of 15-25). */
	static void leaps(Level l) {
		for (Mob m : new ArrayList<>(l.mobs)) if (m instanceof RipperDemon && m.isAlive()) {
			Object lp = field(m, "leapPos");
			if (lp instanceof Integer && (Integer) lp >= 0 && (Integer) lp < l.length() && (l.heroFOV[m.pos] || l.heroFOV[(Integer) lp])) mark((Integer) lp);
		}
	}

	static void mark(int c) { if (c >= 0 && c < dz.length) { dz[c] = true; dzAny = true; } }

	static void rocks(Level l) {
		for (Mob ch : new ArrayList<>(l.mobs)) for (Buff b : ch.buffs()) if (b instanceof DelayedRockFall) {   // (not Actor.chars(): see Assist.gameSafe)
			Object p = field(b, "rockPositions");
			if (p instanceof int[]) for (int c : (int[]) p) mark(c);
		}
	}

	/** Tengu (strategy-reference 4.2): the floor is remade at each phase (the cell's traps go with it); his bombs (2
	 *  steps round, 3 turns), shockers (the 3x3 round each, for good) and the fire wave's flames (each flame cell burns
	 *  whoever stands there as it goes out: Tengu.FireAbility.FireBlob). */
	static void tengu(Level l) {
		Object st = ((PrisonBossLevel) l).state();
		if (stateL != l) { stateL = l; tenguState = st; }
		if (st != tenguState) {   // (the flash and the floor redrawn: the traps shown before are gone)
			tenguState = st;
			if (trapMem != null) Arrays.fill(trapMem, false);
		}
		int W = l.width();
		for (Heap h : l.heaps.valueList()) {
			if (!h.seen || h.pos < 0 || h.pos >= l.length()) continue;
			for (Item i : h.items) {
				if (i instanceof Tengu.BombAbility.BombItem) {   // radius 2 by steps round walls (BombAbility.act)
					int[] d = new int[l.length()]; Arrays.fill(d, 99); d[h.pos] = 0; ArrayDeque<Integer> q = new ArrayDeque<>(); q.add(h.pos);
					while (!q.isEmpty()) { int c = q.poll(); mark(c); if (d[c] >= 2) continue;
						for (int k : PathFinder.NEIGHBOURS8) { int n = c + k; if (n >= 0 && n < l.length() && Math.abs(n % W - c % W) <= 1 && !l.solid[n] && d[n] > d[c] + 1) { d[n] = d[c] + 1; q.add(n); } } }
				} else if (i instanceof Tengu.ShockerAbility.ShockerItem) {
					mark(h.pos);   // (its own cell every turn; round it the sparks shown: Tengu.ShockerAbility.ShockerBlob)
					for (int k : PathFinder.NEIGHBOURS8) { int n = h.pos + k; if (n >= 0 && n < soon.length) soon[n] = true; }   // (lit every other turn)
				}
			}
		}
		Blob sh = l.blobs.get(Tengu.ShockerAbility.ShockerBlob.class);   // (a spark cell shocks as it goes out: those lit now)
		if (sh != null && sh.volume > 0 && sh.cur != null) for (int c = 0; c < l.length() && c < sh.cur.length; c++) if (sh.cur[c] > 0) mark(c);
		Blob f = l.blobs.get(Tengu.FireAbility.FireBlob.class);
		if (f != null && f.volume > 0 && f.cur != null) for (int c = 0; c < l.length() && c < f.cur.length; c++) if (f.cur[c] > 0) mark(c);
	}

	// =============================================================================================================
	// the boss's own move
	// =============================================================================================================

	/** A tactical move for this turn on a boss's floor, or null (then the usual tactics). */
	static Target tactics() {
		if (!ON) return null;
		int hp = Dungeon.hero.pos, LEN = L.length();
		Target dg = dodge(); if (dg != null) return dg;
		if (!Dungeon.bossLevel()) return null;
		if (DBG && L instanceof PrisonBossLevel) dump();
		Target t = null;
		for (Mob m : mobs()) {
			if (m instanceof DM300 && PYLON) t = dm300((DM300) m);
			else if (m instanceof DwarfKing && KING2) t = king((DwarfKing) m);
			else if (m instanceof YogDzewa && FISTS && !YOG) t = yog((YogDzewa) m);
			if (t != null) return t;
		}
		return null;
	}

	/** A blow coming at the hero's cell (a beam, a leap, a bomb...): the nearest cell it misses. */
	static Target dodge() { return dodge(-1); }
	static Target dodge(int obj) {
		int hp = Dungeon.hero.pos, LEN = L.length();
		danger(0);
		if (dzAny && dz[hp]) {   // a blow coming here: the nearest cell it misses (through the danger, if that's the only way)
			// (on the way to plan A's objective when a step or two will do: back and forth through a shocker's sparks
			// went nowhere, they light every other turn)
			if (obj < 0) obj = out != null && !out.isEmpty() ? (out.get(0).obj >= 0 ? out.get(0).obj : out.get(0).cell) : -1;
			if (obj == hp) obj = -1;
			int best = -1, bk = INF;
			for (int c = 0; c < LEN; c++) if (dist[c] < INF && dist[c] > 0 && !dz[c] && !foul(c) && L.passable[c] && !L.avoid[c] && seenChar(c) == null) {
				int k = (dist[c] == 1 ? (obj >= 0 ? L.distance(c, obj) : 0) : 100 + dist[c] * 8) + (soon[c] ? 4 : 0);   // (out of a shocker's ring, not to its other half)   // (a step at a time: the game's own walk of two might cut through the lit ones)
				if (k < bk) { bk = k; best = c; } }
			if (best >= 0) return new Target(best, GO, "boss: out of the blow's way");
		}
		return null;
	}

	/** The hook before the escapes (Assist.planList's skills): a boss fight's own plan where the usual rules (fleeing
	 *  fire, exploring, searching) would lose it. Only Yog's floor so far. */
	static Target suggest(Target first) {
		if (!ON || !YOG || !(L instanceof HallsBossLevel) || Dungeon.hero == null) return null;
		return yogPlan();
	}

	// =============================================================================================================
	// Yog-Dzewa (strategy-reference 4.5; YogDzewa, YogFist). Yog sits for good at the seat (the exit + 3 rows), his
	// beams are shown a turn ahead (dodged), he can't be hurt while a fist lives, and a fist can't be hurt within 4 of
	// his seat: so (1) Yog struck whenever no fist lives, (2) a fist drawn out to 6+ from the seat and fought there, (3)
	// his summons (ripper demons strike twice a turn) killed as they come, (4) Healing drunk early (it cures the
	// bleeding and the Bright fist's blindness too), (5) no exploring or searching on the floor: nothing's hidden.
	// =============================================================================================================
	static int seat() { return L.exit() + 3 * L.width(); }

	static Target yogPlan() {
		Hero h = Dungeon.hero; int hp = h.pos, yp = seat();
		YogDzewa yog = null; for (Mob m : mobs()) if (m instanceof YogDzewa) yog = (YogDzewa) m;
		if (yog == null || !yog.isAlive()) return null;
		boolean fistAlive = false; for (Mob m : mobs()) if (m instanceof YogFist && m.isAlive()) fistAlive = true;
		boolean last = !fistAlive && yog.HP * 10 <= yog.HT;   // (the last 100: the fists all dead, summons and beams every 2-3 turns; his death takes his summons with him)
		// healing: early (the fists and the demons hit hard; Healing also stops the bleeding)
		boolean foe = false, adjFoe = false;
		for (Mob m : mobs()) if (enemy(m) && !(m instanceof YogDzewa)) { foe |= L.distance(m.pos, hp) <= 6; adjFoe |= L.adjacent(m.pos, hp); }
		float bleed = 0; for (Buff b : h.buffs()) if (b instanceof com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Bleeding) bleed = ((com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Bleeding) b).level();
		PotionOfHealing heal = bag(PotionOfHealing.class);
		if (heal != null && heal.isKnown() && (h.HP * 100 < h.HT * YOGHEAL && (foe || bleed > 0 || blind()) || bleed * 3 >= h.HP))
			return use(heal, Potion.AC_DRINK, -1, "Yog: heal");
		if (last && L.adjacent(yog.pos, hp)) return new Target(yog.pos, ENEMY, "Yog: the last blows");
		Target dg = dodge(fistAlive ? -1 : yog.pos); if (dg != null) return dg;
		// the fists alive (seen or not: each comes with a phase, "the darkness pulls in closer")
		Mob fist = null; int fd = INF;
		boolean hurt = boltHP >= 0 && h.HP < boltHP && boltAt < Actor.now();
		if (boltL != L) { boltL = L; boltFrom.clear(); }
		for (Mob m : mobs()) if (m instanceof YogFist && m.isAlive()) {
			if (enemy(m)) { boltFrom.put(m.id(), m.pos); if (L.distance(m.pos, hp) < fd) { fd = L.distance(m.pos, hp); fist = m; } }
			else if (hurt && (m instanceof YogFist.BrightFist || m instanceof YogFist.DarkFist) && L.distance(m.pos, hp) <= 7
					&& new Ballistica(m.pos, hp, Ballistica.MAGIC_BOLT).collisionPos == hp) boltFrom.put(m.id(), m.pos);   // (its bolt is drawn from where it stands: shot from the dark, it shows where)
		}
		boltHP = h.HP; boltAt = Actor.now();
		boltFrom.keySet().removeIf(id -> { Actor a = Actor.findById(id); return !(a instanceof YogFist) || !((YogFist) a).isAlive(); });
		// the nearest foe beside the hero that can be hurt (the demons first: weakest)
		Mob o = weakestAdjacent(yog);
		if (o != null) return new Target(o.pos, ENEMY, o instanceof YogFist ? "Yog: strike the fist" : "Yog: his summons first");
		if (!fistAlive) {   // Yog himself: dormant till approached, open to blows whenever no fist lives
			if (L.adjacent(yog.pos, hp)) return new Target(yog.pos, ENEMY, "Yog: strike him");
			int st = stepTowards(yog.pos);
			return st >= 0 ? new Target(st, GO, "Yog: to him") : null;
		}
		if (fist != null && !nearSeat(fist.pos)) {   // a fist out of his reach: to it (the Bright and Dark ones shoot every turn from afar)
			boolean ranged = fist instanceof YogFist.BrightFist || fist instanceof YogFist.DarkFist;
			if (!ranged && hunting(fist) && fd <= 3 && L.distance(hp, yp) >= 5) return wait(hp, "Yog: let the fist come");
			int st = stepTowards(fist.pos);
			if (st >= 0 && !(L.distance(st, yp) <= 4 && L.distance(hp, yp) > 4)) return new Target(st, GO, "Yog: to the fist");
		}
		if (fist == null) for (Map.Entry<Integer, Integer> e : boltFrom.entrySet()) {   // a Bright or Dark fist shooting from the dark: to where its bolts come from
			Actor a = Actor.findById(e.getKey()); int at = e.getValue();
			if (!(a instanceof YogFist.BrightFist || a instanceof YogFist.DarkFist) || nearSeat(at)) continue;
			if (L.distance(at, hp) <= 1 || (L.heroFOV[at] && seenChar(at) == null)) { boltFrom.remove(e.getKey()); break; }   // (not there now)
			int st = stepTowards(at);
			if (st >= 0) return new Target(st, GO, "Yog: to the fist shooting from the dark");
		}
		// a fist by Yog, or none in sight: wait 6+ from the seat for it
		int spot = lureSpot(fist, yp);
		if (spot < 0 || spot == hp) return wait(hp, "Yog: wait for the fist");
		int st = stepTowards(spot);
		return st >= 0 ? new Target(st, GO, "Yog: draw the fist off him") : wait(hp, "Yog: wait for the fist");
	}

	static final Map<Integer, Integer> boltFrom = new HashMap<>(); static int boltHP = -1; static float boltAt; static Level boltL;

	static boolean nearSeat(int c) { return L.distance(c, seat()) <= 4; }
	static Target wait(int c, String why) { Target t = new Target(c, WAIT, why); t.action = WAIT_ACT; return t; }

	/** Where to wait for a fist: 6+ from the seat (so it's out of his reach beside the hero), the nearest such cell,
	 *  off what helps the fist (water heals the Rotting one; the Burning one's fire can't take on water). */
	static int lureSpot(Mob fist, int yp) {
		int hp = Dungeon.hero.pos, best = -1, bs = INF;
		boolean ranged = fist instanceof YogFist.BrightFist || fist instanceof YogFist.DarkFist;
		for (int c = 0; c < L.length(); c++) {
			if (dist[c] >= INF || L.distance(c, yp) < 6 || !L.passable[c] || L.avoid[c] || danger[c] || (seenChar(c) != null && c != hp)) continue;
			int s = dist[c] * 2;
			if (fist instanceof YogFist.RottingFist && L.water[c]) s += 6;
			if (fist instanceof YogFist.BurningFist && !L.water[c]) s += 3;
			if (ranged && fist != null && new Ballistica(fist.pos, c, Ballistica.MAGIC_BOLT).collisionPos == c && L.distance(fist.pos, c) <= 6) s += 12;   // (out of its line)
			if (s < bs) { bs = s; best = c; }
		}
		return best;
	}

	/** DM-300 supercharged (it sparks, and blows don't hurt it: "the boss can't be hurt"): the lit pylon is what ends
	 *  it (strategy-reference 4.3): to it and strike it. Pylons never move: one seen is remembered. */
	static Target dm300(DM300 dm) {
		Hero h = Dungeon.hero; int hp = h.pos;
		if (pyL != L) { pyL = L; darkSeen.clear(); gone.clear(); wasCharged = false; }
		for (Mob m : mobs()) if (m instanceof Pylon && L.heroFOV[m.pos] && m.alignment == Char.Alignment.NEUTRAL) darkSeen.put(m.pos, Actor.now());
		for (int p : CavesBossLevel.pylonPositions) if (p >= 0 && p < L.length() && L.heroFOV[p] && !(Actor.findChar(p) instanceof Pylon)) gone.add(p);
		if (!dm.isSupercharged() || !dm.isAlive()) { wasCharged = false; return null; }
		Mob py = null; int bd = INF;
		for (Mob m : mobs()) if (m instanceof Pylon && m.alignment == Char.Alignment.ENEMY && m.isAlive() && (L.heroFOV[m.pos] || known[m.pos]) && L.distance(m.pos, hp) < bd) { bd = L.distance(m.pos, hp); py = m; }
		if (chargeL != L || !wasCharged) { chargeL = L; chargeAt = Actor.now(); aim = -1; }
		wasCharged = true;
		if (py != null) {
			if (L.adjacent(py.pos, hp)) return new Target(py.pos, ENEMY, "boss: strike the lit pylon");
			aim = py.pos;
		} else {   // the lit one not seen: the pylons' places are the arena's (CavesBossLevel.pylonPositions); one not seen dark since the charge began
			if (aim >= 0 && ((darkSeen.containsKey(aim) && darkSeen.get(aim) >= chargeAt) || gone.contains(aim))) aim = -1;
			if (aim < 0) { int ad = INF;   // (the nearest not ruled out, kept till it is: no back and forth)
				for (int p : CavesBossLevel.pylonPositions) {
					if (p < 0 || p >= L.length() || (darkSeen.containsKey(p) && darkSeen.get(p) >= chargeAt) || gone.contains(p)) continue;
					if (L.distance(p, hp) < ad) { ad = L.distance(p, hp); aim = p; } } }
		}
		if (aim < 0) return null;
		int st = stepTowards(aim);
		if (st < 0 && py == null) { gone.add(aim); aim = -1; }   // (no way known there, none to explore: another)
		return st >= 0 ? new Target(st, GO, py != null ? "boss: to the lit pylon" : "boss: find the lit pylon") : null;
	}

	/** The first step of the cheapest known way to beside c: the electrified floor and other blows' cells cost 4 a
	 *  step (better crossed than not reached), the unknown can't be walked; none known that far: the known cell
	 *  nearest c. One step at a time, so the game's own walk can't take another way. */
	static int stepTowards(int c) {
		int hp = Dungeon.hero.pos, N = L.length(), W = L.width();
		int[] cost = new int[N]; Arrays.fill(cost, INF); int[] first = new int[N];
		PriorityQueue<int[]> q = new PriorityQueue<>((x, y) -> Integer.compare(x[0], y[0]));
		cost[hp] = 0; first[hp] = -1; q.add(new int[]{0, hp});
		int best = -1, bs = INF;
		while (!q.isEmpty()) {
			int[] e = q.poll(); int u = e[1]; if (e[0] > cost[u]) continue;
			// beside c, else the edge of the known nearest it (once there it is seen past: no stopping short)
			int sc = L.distance(u, c) <= 1 ? cost[u] : unseenBy(u) ? 1000 + L.distance(u, c) * 2 + cost[u] : INF;
			if (u != hp && sc < bs) { bs = sc; best = u; }
			for (int k : PathFinder.NEIGHBOURS8) { int n = u + k;
				if (n < 0 || n >= N || Math.abs(n % W - u % W) > 1 || !L.passable[n] || L.avoid[n] || !(known[n] || L.heroFOV[n]) || (seenChar(n) != null && n != hp)) continue;
				int nc = cost[u] + (danger[n] ? 4 : 1);
				if (nc < cost[n]) { cost[n] = nc; first[n] = u == hp ? n : first[u]; q.add(new int[]{nc, n}); } }
		}
		return best >= 0 ? first[best] : -1;
	}
	static boolean unseenBy(int c) { return Assist.unseenBy(c); }

	static final Map<Integer, Float> darkSeen = new HashMap<>(); static final Set<Integer> gone = new HashSet<>();
	static Level chargeL, pyL; static float chargeAt; static boolean wasCharged; static int litAt = -1, aim = -1;

	/** The Dwarf King on his throne, immune behind his barrier (phase 2): only his minions' deaths wear it down
	 *  (strategy-reference 4.4); the minions, never him. */
	static Target king(DwarfKing dk) {
		int hp = Dungeon.hero.pos;
		if (!dk.isAlive() || !dk.isInvulnerable(Hero.class)) return null;
		Mob o = weakestAdjacent(dk);
		if (o != null) return new Target(o.pos, ENEMY, "boss: his minions first");
		if (L.adjacent(dk.pos, hp)) {   // beside him with nothing else to hit: off his side
			int c = stepOff(dk.pos, 2);
			if (c >= 0) return new Target(c, GO, "boss: off the immune king");
		}
		return null;
	}

	/** Yog-Dzewa: immune while a fist lives, and a fist is immune within 4 of him: draw the fist off before striking it
	 *  (strategy-reference 4.5). */
	static Target yog(YogDzewa yog) {
		int hp = Dungeon.hero.pos, yp = yog.pos;
		Mob fist = null;
		for (Mob m : mobs()) if (m instanceof YogFist && enemy(m) && L.adjacent(m.pos, hp)) fist = m;
		if (fist != null && fist.isInvulnerable(Hero.class)) {   // (shown: "immune")
			int best = -1, bs = -INF;
			for (int k = 0; k < 8; k++) { int c = hp + PathFinder.NEIGHBOURS8[k];
				if (c < 0 || c >= L.length() || !open[c] || seenChar(c) != null || danger[c]) continue;
				int s = L.distance(c, yp) * 4 - adjFoes(c) * 3; if (s > bs) { bs = s; best = c; } }
			if (best >= 0 && L.distance(best, yp) > L.distance(hp, yp)) return new Target(best, GO, "boss: draw the fist off Yog");
		}
		if (fist == null && L.adjacent(yp, hp) && yog.isInvulnerable(Hero.class)) {
			Mob o = weakestAdjacent(yog);
			if (o != null) return new Target(o.pos, ENEMY, "boss: not Yog while a fist lives");
			int c = stepOff(yp, 5);
			if (c >= 0) return new Target(c, GO, "boss: away from immune Yog");
		}
		return null;
	}

	// ---- helpers ----
	static Mob weakestAdjacent(Mob not) {
		Mob best = null; int hp = Dungeon.hero.pos;
		for (Mob m : mobs()) if (m != not && enemy(m) && L.adjacent(m.pos, hp) && !m.isInvulnerable(Hero.class) && !Dungeon.hero.isCharmedBy(m) && m.state != m.PASSIVE && (best == null || m.HP < best.HP)) best = m;
		return best;
	}
	static int adjFoes(int c) { int n = 0; for (Mob m : mobs()) if (enemy(m) && m.state != m.PASSIVE && L.adjacent(m.pos, c)) n++; return n; }
	/** The walk to a free cell beside c (INF: none known). */
	static int walkNear(int c) { int b = INF; for (int k : PathFinder.NEIGHBOURS8) { int n = c + k; if (n >= 0 && n < L.length() && dist[n] < b && (n == Dungeon.hero.pos || seenChar(n) == null) && L.passable[n]) b = dist[n]; } return b; }
	static int besideStep(int c) { int b = -1, bd = INF; for (int k : PathFinder.NEIGHBOURS8) { int n = c + k; if (n >= 0 && n < L.length() && dist[n] < bd && dist[n] > 0 && seenChar(n) == null && L.passable[n] && !L.avoid[n]) { bd = dist[n]; b = n; } } return b; }
	/** A free step that leaves c's side (at least d away from it, else the farthest). */
	static int stepOff(int c, int d) {
		int hp = Dungeon.hero.pos, best = -1, bs = -INF;
		for (int k : PathFinder.NEIGHBOURS8) { int n = hp + k;
			if (n < 0 || n >= L.length() || !open[n] || seenChar(n) != null || danger[n]) continue;
			int s = Math.min(L.distance(n, c), d) * 4 - adjFoes(n) * 3; if (s > bs) { bs = s; best = n; } }
		return best >= 0 && L.distance(best, c) > 1 ? best : -1;
	}

	static void dump() {
		int hp = Dungeon.hero.pos, LEN = L.length();
		StringBuilder sb = new StringBuilder("    BOSSMAP " + (L instanceof PrisonBossLevel ? ((PrisonBossLevel) L).state() : "") + " hp " + hp + "\n");
		int W = L.width();
		for (int y = 0; y < L.height(); y++) { sb.append("    ");
			for (int x = 0; x < W; x++) { int c = x + y * W; int t = L.map[c];
				char ch = c == hp ? '@' : dz.length == LEN && dz[c] ? '!' : !known[c] ? ' ' : t == Terrain.WALL || t == Terrain.WALL_DECO ? '#' : t == Terrain.DOOR ? '+' : t == Terrain.LOCKED_DOOR ? 'L' : L.passable[c] ? (dist[c] < INF ? '.' : ',') : '?';
				sb.append(ch); }
			sb.append('\n'); }
		System.out.print(sb);
	}
}
