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
	static final boolean PYLON = opt("BPYLON", true), KING2 = opt("BKING2", true), FISTS = opt("BFISTS", true), LEAPS = opt("BLEAPS", true), YOG = opt("BYOG", true), SOON = opt("BSOON", true);
	static final int YOGHEAL = optInt("BYOGHEAL", 45);   // (Yog's floor: Healing below this share of health with a foe at hand or bleeding)

	// =============================================================================================================
	// the cells to keep off (worked out once a turn)
	// =============================================================================================================
	static boolean[] dz = new boolean[0], soon = new boolean[0], elec = new boolean[0]; static boolean dzAny, elecAny;
	private static Level dzL; private static float dzAt = -1; private static int dzPos = -1;
	private static Object tenguState; private static Level stateL;

	/** Is c a cell a boss's telegraphed blow will hit (keep off it)? */
	static boolean danger(int c) {
		if (!ON) return false;
		Level l = Dungeon.level; if (l == null || Dungeon.hero == null) return false;
		if (dzL != l || dzAt != Actor.now() || dzPos != Dungeon.hero.pos || dz.length != l.length()) compute(l);
		return c >= 0 && c < dz.length && (dz[c] || elec[c] || (SOON && soon[c]));   // (a shocker's ring too, lit every other turn: a walk through it when dark was dodged back out of when lit, for ever)
	}

	static void compute(Level l) {
		dzL = l; dzAt = Actor.now(); dzPos = Dungeon.hero.pos;
		if (dz.length != l.length()) { dz = new boolean[l.length()]; soon = new boolean[l.length()]; elec = new boolean[l.length()]; } else { Arrays.fill(dz, false); Arrays.fill(soon, false); Arrays.fill(elec, false); }
		dzAny = false; elecAny = false;
		boolean mine = l instanceof com.shatteredpixel.shatteredpixeldungeon.levels.MiningLevel;
		if (LEAPS) leaps(l);
		if (NEWBORN) newborn(l);
		if (!Dungeon.bossLevel() && !mine) return;
		rocks(l);   // (falling rocks: DM-300's, the Gnoll Geomancer's)
		if (mine) return;
		if (l instanceof PrisonBossLevel) tengu(l);
		if (l instanceof CavesBossLevel) {   // the electrified floor (traps' tiles and water) while a pylon is lit (and for good once two are down)
			Blob e = l.blobs.get(CavesBossLevel.PylonEnergy.class);
			if (e != null && e.volume > 0 && e.cur != null) for (int c = 0; c < l.length() && c < e.cur.length; c++) if (e.cur[c] > 0) { elec[c] = true; elecAny = true; }
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

	/** The Wandmaker's newborn fire elemental (Elemental.NewbornFireElemental): its blast is shown on the 3x3 round the
	 *  cell it aims at a turn ahead (GameScene.targetedCell), while it or the cell is in sight: kept off. */
	static final boolean NEWBORN = opt("BNEWBORN", true);
	static void newborn(Level l) {
		for (Mob m : new ArrayList<>(l.mobs)) if (m instanceof Elemental.NewbornFireElemental && m.isAlive()) {
			Object tp = field(m, "targetingPos");
			if (!(tp instanceof Integer) || (Integer) tp < 0 || (Integer) tp >= l.length() || !(l.heroFOV[m.pos] || l.heroFOV[(Integer) tp])) continue;
			for (int k : PathFinder.NEIGHBOURS9) { int c = (Integer) tp + k; if (c >= 0 && c < l.length() && !l.solid[c]) mark(c); }
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
		if (DBG && (L instanceof PrisonBossLevel || L instanceof CavesBossLevel || L instanceof com.shatteredpixel.shatteredpixeldungeon.levels.CityBossLevel)) dump();
		if (PYLON && L instanceof CavesBossLevel) for (Mob m : mobs()) if (m instanceof DM300 && m.isAlive() && ((DM300) m).isSupercharged()) {   // (its own plan dodges: the dodge off the electrified floor and the walk to the pylon over it took turns, for ever)
			Target t = dm300((DM300) m); if (t != null) return t; }
		Target dg = dodge(); if (dg != null) return dg;
		if (!Dungeon.bossLevel()) return null;
		Target t = null;
		if (TENGU && L instanceof PrisonBossLevel && (t = tenguPlan()) != null) return t;
		for (Mob m : mobs()) {
			if (m instanceof DM300 && PYLON) t = dm300((DM300) m);
			else if (m instanceof DwarfKing && KING2) t = king((DwarfKing) m);
			else if (m instanceof Goo && GOOWATER) t = goo((Goo) m);
			else if (m instanceof YogDzewa && FISTS && !YOG) t = yog((YogDzewa) m);
			if (t != null) return t;
		}
		return null;
	}

	// =============================================================================================================
	// Tengu (strategy-reference 4.2; Tengu.java): his aim is 10 beside the hero and 20 from afar (attackSkill), he never
	// walks (only jumps, 5-7 off in the arena, a bracket of 25 health at a time), and once out of sight he stays where
	// he landed: so to him and struck from beside him; Blink to close the gap, Invisibility drunk for the walk in; lost
	// from sight, to where he was last seen (his jump's puff shows where he lands when it's in sight), then round the
	// arena to the cells not in sight (a hero who lost him searched the floor for 4,000 turns: the floor's timeouts).
	// =============================================================================================================
	static final boolean TENGU = opt("BTENGU", true), TBLINK = opt("BTBLINK", true), TINVIS = opt("BTINVIS", true);
	static int tenguAt = -1; static Level tenguL; static final Set<Integer> tenguLooked = new HashSet<>();

	static Target tenguPlan() {
		Object st = ((PrisonBossLevel) L).state();
		if (st != PrisonBossLevel.State.FIGHT_START && st != PrisonBossLevel.State.FIGHT_ARENA) return null;
		Hero h = Dungeon.hero; int hp = h.pos;
		if (tenguL != L) { tenguL = L; tenguAt = -1; tenguLooked.clear(); }
		Tengu tg = null; for (Mob m : mobs()) if (m instanceof Tengu && m.isAlive() && L.mobs.contains(m)) tg = (Tengu) m;
		if (tg == null) return null;
		boolean seen = L.heroFOV[tg.pos] && tg.invisible <= 0;
		if (seen) { tenguAt = tg.pos; tenguLooked.clear(); }
		if (h.rooted) return null;
		if (seen) {
			if (L.adjacent(tg.pos, hp)) return h.isCharmedBy(tg) ? null : new Target(tg.pos, ENEMY, "Tengu: strike from beside him (his aim halves)");
			int d = L.distance(tg.pos, hp);
			if (TINVIS && st == PrisonBossLevel.State.FIGHT_ARENA && d >= 4 && h.invisible <= 0) {   // (he can't aim at what he can't see: the walk in unshot, the first blow a surprise)
				com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfInvisibility iv = bag(com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfInvisibility.class);
				if (iv != null && known(iv) && h.HP * 2 < h.HT * 3 / 2 && tg.HP * 2 <= tg.HT) return use(iv, Potion.AC_DRINK, -1, "Tengu: invisible for the walk in");
			}
			if (TBLINK && d >= 4) {   // a Stone of Blink thrown beside him: there at once
				com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfBlink bl = bag(com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfBlink.class);
				if (bl != null) { int best = -1, bk = INF;
					for (int k : PathFinder.NEIGHBOURS8) { int c = tg.pos + k;
						if (c < 0 || c >= L.length() || !L.passable[c] || L.avoid[c] || danger[c] || Actor.findChar(c) != null || !L.heroFOV[c]) continue;
						if (new Ballistica(hp, c, Ballistica.PROJECTILE).collisionPos != c) continue;
						int kk = L.distance(c, hp); if (kk < bk) { bk = kk; best = c; } }
					if (best >= 0) return use(bl, Item.AC_THROW, best, "Tengu: blink beside him"); }
			}
			int s1 = kingStep(tg.pos, -1);
			return s1 >= 0 ? new Target(s1, GO, "Tengu: to him (his aim halves beside him)") : null;
		}
		// out of sight: where he was last seen, then the cells not in sight
		if (tenguAt >= 0 && (L.heroFOV[tenguAt] || L.distance(tenguAt, hp) <= 1)) { tenguLooked.add(tenguAt); tenguAt = -1; }
		if (tenguAt >= 0) { int s1 = kingStep(tenguAt, -1); if (s1 >= 0) return new Target(s1, GO, "Tengu: to where he was last seen"); tenguAt = -1; }
		int best = -1, bk = INF;
		for (int c = 0; c < L.length(); c++) if (dist[c] > 0 && dist[c] < INF && !L.heroFOV[c] && open[c] && !danger[c] && !tenguLooked.contains(c) && dist[c] < bk) { bk = dist[c]; best = c; }
		if (best < 0) return null;
		int s1 = kingStep(best, -1); if (s1 < 0) return null;
		if (L.distance(best, hp) <= 1) tenguLooked.add(best);
		return new Target(s1, GO, "Tengu: look for him");
	}

	/** Goo heals in water (Goo.act: +1 a turn while it stands there, and the floor's lock gives the time back): beside
	 *  the hero in water, a step to a dry cell not beside it, where every cell it can step to beside the hero is dry
	 *  (strategy-reference 4.1); it follows out of the water. Not while it's pumped up (Assist.tactics keeps out of
	 *  its reach then). */
	static final boolean GOOWATER = opt("BGOOWATER", true);
	static Target goo(Goo g) {
		Hero h = Dungeon.hero; int hp = h.pos;
		if (!enemy(g) || !L.water[g.pos] || g.HP >= g.HT || !L.adjacent(g.pos, hp) || h.rooted || Assist.pumped(g)) return null;
		int best = -1, bk = INF;
		for (int k : PathFinder.NEIGHBOURS8) { int n = hp + k;
			if (n < 0 || n >= L.length() || !open[n] || L.water[n] || danger[n] || seenChar(n) != null || L.adjacent(n, g.pos)) continue;
			boolean dry = true; int opts = 0;
			for (int j : PathFinder.NEIGHBOURS8) { int c = n + j; if (c < 0 || c >= L.length() || !L.adjacent(c, g.pos) || !L.passable[c] || c == hp) continue; opts++; if (L.water[c]) dry = false; }
			if (!dry && L.water[hp]) continue;
			int sc = (dry ? 0 : 10) + adjFoes(n) * 3;
			if (dry && opts > 0 && sc < bk) { bk = sc; best = n; } }
		return best >= 0 ? new Target(best, GO, "boss: Goo out of the water (it heals there)") : null;
	}

	/** A blow coming at the hero's cell (a beam, a leap, a bomb...): the nearest cell it misses. */
	static Target dodge() { return dodge(-1); }
	static Target dodge(int obj) {
		int hp = Dungeon.hero.pos, LEN = L.length();
		danger(0);
		boolean onElec = elecAny && elec[hp];
		if ((dzAny && dz[hp]) || onElec) {   // a blow coming here: the nearest cell it misses (through the danger, if that's the only way)
			// (on the way to plan A's objective when a step or two will do: back and forth through a shocker's sparks
			// went nowhere, they light every other turn)
			if (obj < 0) obj = out != null && !out.isEmpty() ? (out.get(0).obj >= 0 ? out.get(0).obj : out.get(0).cell) : -1;
			if (obj == hp) obj = -1;
			int best = -1, bk = INF;
			for (int c = 0; c < LEN; c++) if (dist[c] < INF && dist[c] > 0 && !dz[c] && !elec[c] && !foul(c) && L.passable[c] && !L.avoid[c] && seenChar(c) == null) {
				int k = (dist[c] == 1 ? (obj >= 0 ? L.distance(c, obj) : 0) : 100 + dist[c] * 8) + (soon[c] ? 4 : 0);   // (out of a shocker's ring, not to its other half)   // (a step at a time: the game's own walk of two might cut through the lit ones)
				if (k < bk) { bk = k; best = c; } }
			if (best >= 0) return new Target(best, GO, onElec && !dz[hp] ? "boss: off the electrified floor" : "boss: out of the blow's way");
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
		if (heal != null && heal.isKnown() && (h.HP * 100 < h.HT * YOGHEAL && (foe || bleed > 0 || blind() || (boltHP >= 0 && h.HP < boltHP)) || bleed * 3 >= h.HP))
			return use(heal, Potion.AC_DRINK, -1, "Yog: heal");
		if (last && L.adjacent(yog.pos, hp)) return new Target(yog.pos, ENEMY, "Yog: the last blows");
		Target dg = dodge(fistAlive ? -1 : yog.pos); if (dg != null) return dg;
		// the fists alive (seen or not: each comes with a phase, "the darkness pulls in closer")
		Mob fist = null; int fd = INF;
		boolean hurt = boltHP >= 0 && h.HP < boltHP && boltAt < Actor.now();
		if (boltL != L) { boltL = L; boltFrom.clear(); fistSeenAt = Actor.now(); }
		for (Mob m : mobs()) if (m instanceof YogFist && m.isAlive()) {
			if (enemy(m)) { boltFrom.put(m.id(), m.pos); fistSeenAt = Actor.now(); if (L.distance(m.pos, hp) < fd) { fd = L.distance(m.pos, hp); fist = m; } }
			else if (hurt && (m instanceof YogFist.BrightFist || m instanceof YogFist.DarkFist) && L.distance(m.pos, hp) <= 7
					&& new Ballistica(m.pos, hp, Ballistica.MAGIC_BOLT).collisionPos == hp) boltFrom.put(m.id(), m.pos);   // (its bolt is drawn from where it stands: shot from the dark, it shows where)
		}
		boltHP = h.HP; boltAt = Actor.now();
		if (DBG) for (Mob m : mobs()) if (m instanceof YogFist) System.out.println("    YOGDBG " + m.getClass().getSimpleName() + " at " + m.pos + " seen " + enemy(m) + " d " + L.distance(m.pos, hp) + " seat " + L.distance(m.pos, yp) + " line " + (new Ballistica(m.pos, hp, Ballistica.MAGIC_BOLT).collisionPos == hp) + " bolt " + boltFrom);
		boltFrom.keySet().removeIf(id -> { Actor a = Actor.findById(id); return !(a instanceof YogFist) || !((YogFist) a).isAlive(); });
		if (last) { int st = stepTowards(yog.pos); if (st >= 0 && L.distance(st, yog.pos) < L.distance(hp, yog.pos)) return new Target(st, GO, "Yog: to him, past his summons"); }   // (his summons come every 3 turns: they never end)
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
		if (fist == null && Actor.now() - fistSeenAt > 20) for (Map.Entry<Integer, Integer> e : boltFrom.entrySet()) {   // one not come for 20 turns (the Rusted one is large: narrow ways stop it): where it was last seen
			int at = e.getValue();
			if (L.distance(at, hp) <= 1 || (L.heroFOV[at] && seenChar(at) == null)) { boltFrom.remove(e.getKey()); break; }
			int st = stepTowards(at);
			if (st >= 0) return new Target(st, GO, "Yog: look for the fist");
		}
		// a fist by Yog, or none in sight: wait for it out of his reach (6+ from the seat; a Bright or Dark one shoots
		// rather than comes while it sees the hero: out of its line, and far enough that where it first sees the hero
		// is out of his reach)
		int from = -1; Mob rf = fist;
		if (fist != null) from = fist.pos;
		else for (Map.Entry<Integer, Integer> e : boltFrom.entrySet()) { Actor a = Actor.findById(e.getKey()); if (a instanceof YogFist.BrightFist || a instanceof YogFist.DarkFist) { from = e.getValue(); rf = (Mob) a; } }
		if (fist != null) {   // one that doesn't come (the large Rusted one can't pass narrow ways): another place to wait
			if (fist.pos != stillPos) { stillPos = fist.pos; stillSince = Actor.now(); }
			else if (Actor.now() - stillSince > 8) { badSpots.add(hp); stillSince = Actor.now(); }
		}
		if (badL != L) { badL = L; badSpots.clear(); }
		int spot = -1;
		for (int r = rangedFist(rf) ? 11 : 6; r >= 6 && spot < 0; r -= 2) spot = lureSpot(rf, from, yp, r);
		if (spot < 0 || spot == hp) return wait(hp, "Yog: wait for the fist");
		int st = stepTowards(spot);
		return st >= 0 ? new Target(st, GO, "Yog: draw the fist off him") : wait(hp, "Yog: wait for the fist");
	}

	static boolean rangedFist(Mob m) { return m instanceof YogFist.BrightFist || m instanceof YogFist.DarkFist; }

	/** Where to wait for a fist: r+ from the seat (so it's out of his reach beside the hero), the nearest such cell,
	 *  off what helps the fist (water heals the Rotting one; the Burning one's fire can't take on water), out of a
	 *  Bright or Dark one's line from where it was seen or shot from. */
	static int lureSpot(Mob fist, int from, int yp, int r) {
		int hp = Dungeon.hero.pos, best = -1, bs = INF;
		boolean ranged = rangedFist(fist);
		for (int c = 0; c < L.length(); c++) {
			if (dist[c] >= INF || L.distance(c, yp) < r || !L.passable[c] || L.avoid[c] || danger[c] || (seenChar(c) != null && c != hp)) continue;
			if (fist != null && Char.hasProp(fist, Char.Property.LARGE) && !L.openSpace[c]) continue;   // (it can't stand beside a narrow cell's)
			boolean bad = false; for (int b : badSpots) bad |= L.distance(b, c) <= 2; if (bad) continue;
			int s = dist[c] * 2;
			if (fist instanceof YogFist.RottingFist && L.water[c]) s += 6;
			if (fist instanceof YogFist.BurningFist && !L.water[c]) s += 3;
			if (ranged && from >= 0 && L.distance(from, c) <= 7 && new Ballistica(from, c, Ballistica.MAGIC_BOLT).collisionPos == c) s += 40;   // (out of its line)
			if (s < bs) { bs = s; best = c; }
		}
		return best;
	}

	static final Map<Integer, Integer> boltFrom = new HashMap<>(); static int boltHP = -1; static float boltAt, fistSeenAt; static Level boltL;

	static final Set<Integer> badSpots = new HashSet<>(); static int stillPos = -1; static float stillSince; static Level badL;

	/** A shot, throw or zap at a foe shown immune (a fist by Yog, Yog while a fist lives, the Dwarf King behind his
	 *  barrier): wasted turns (a Huntress shot an immune fist till she died). */
	static boolean vetoAim(Target hk) {
		if (!ON || hk == null || hk.kind != USE || hk.cell < 0 || hk.cell == Dungeon.hero.pos) return false;
		Char c = Actor.findChar(hk.cell);
		return c instanceof Mob && L.heroFOV[c.pos] && Char.hasProp(c, Char.Property.BOSS) && c.isInvulnerable(Hero.class);
	}

	/** A Stone of Aggression at a boss (Yog's fists too): it no longer works on them (v4; strategy-v4 14). */
	static boolean vetoAggression(Target hk) {
		if (!ON || hk == null || hk.kind != USE || hk.cell < 0 || !(hk.item instanceof com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfAggression)) return false;
		Char c = Actor.findChar(hk.cell);
		return c instanceof Mob && Assist.boss(c);
	}

	static boolean nearSeat(int c) { return L.distance(c, seat()) <= 4; }
	static Target wait(int c, String why) { Target t = new Target(c, WAIT, why); t.action = WAIT_ACT; return t; }

	/** DM-300 supercharged (it sparks, and blows don't hurt it: "the boss can't be hurt"): the lit pylon is what ends
	 *  it (strategy-reference 4.3): to it and strike it. Pylons never move: one seen is remembered. The electrified
	 *  floor's sparks fly to the lit pylon (CavesBossLevel.PylonEnergy's DIRECTED_SPARKS): with any of it in sight, the
	 *  lit one is known. The way there is the cheapest over the floor as it is (6-12 a turn on the electrified cells),
	 *  kept to from step to step (a dodge off the electrified cells and the walk back over them took turns for ever,
	 *  the commonest death on depth 15), and the blow struck from a cell off them where there is one. */
	static Target dm300(DM300 dm) {
		Hero h = Dungeon.hero; int hp = h.pos;
		if (pyL != L) { pyL = L; darkSeen.clear(); gone.clear(); wasCharged = false; }
		for (Mob m : mobs()) if (m instanceof Pylon && L.heroFOV[m.pos] && m.alignment == Char.Alignment.NEUTRAL) darkSeen.put(m.pos, Actor.now());
		for (int p : CavesBossLevel.pylonPositions) if (p >= 0 && p < L.length() && L.heroFOV[p] && !(Actor.findChar(p) instanceof Pylon)) gone.add(p);
		if (!dm.isSupercharged() || !dm.isAlive()) { wasCharged = false; return TRAPS ? dmTraps(dm) : null; }
		danger(0);
		boolean sparks = false; for (int c = 0; c < L.length() && !sparks; c++) sparks = elec[c] && L.heroFOV[c];
		Mob py = null; int bd = INF;
		for (Mob m : mobs()) if (m instanceof Pylon && m.alignment == Char.Alignment.ENEMY && m.isAlive() && (L.heroFOV[m.pos] || known[m.pos] || (SPARKS && sparks)) && L.distance(m.pos, hp) < bd) { bd = L.distance(m.pos, hp); py = m; }
		if (chargeL != L || !wasCharged) { chargeL = L; chargeAt = Actor.now(); aim = -1; }
		wasCharged = true;
		if (py != null) {
			if (L.adjacent(py.pos, hp)) {
				if (elec[hp] && !dz[hp]) for (int k : PathFinder.NEIGHBOURS8) { int n = hp + k;   // (beside it on the electrified floor: a step to a dry cell beside it, once)
					if (n >= 0 && n < L.length() && L.adjacent(n, py.pos) && !elec[n] && !dz[n] && L.passable[n] && !L.avoid[n] && seenChar(n) == null) return new Target(n, GO, "boss: beside the pylon, off the electrified floor"); }
				return new Target(py.pos, ENEMY, "boss: strike the lit pylon");
			}
			aim = py.pos;
		} else {   // the lit one not seen: the pylons' places are the arena's (CavesBossLevel.pylonPositions); one not seen dark since the charge began
			if (aim >= 0 && ((darkSeen.containsKey(aim) && darkSeen.get(aim) >= chargeAt) || gone.contains(aim))) aim = -1;
			if (aim < 0) { int ad = INF;   // (the nearest not ruled out, kept till it is: no back and forth)
				for (int p : CavesBossLevel.pylonPositions) {
					if (p < 0 || p >= L.length() || (darkSeen.containsKey(p) && darkSeen.get(p) >= chargeAt) || gone.contains(p)) continue;
					if (L.distance(p, hp) < ad) { ad = L.distance(p, hp); aim = p; } } }
		}
		if (aim < 0) return null;
		int st = ELECSTEP ? elecStep(aim) : stepTowards(aim);
		if (st < 0 && py == null) { gone.add(aim); aim = -1; }   // (no way known there, none to explore: another)
		return st >= 0 ? new Target(st, GO, py != null ? "boss: to the lit pylon" : "boss: find the lit pylon") : null;
	}
	static final boolean SPARKS = opt("BSPARKS", true), ELECSTEP = opt("BELECSTEP", true), TRAPS = opt("BTRAPS", true);
	static final int ELECCOST = optInt("BELEC", 10);   // (a turn on the electrified floor, in steps: 6-12 a turn)

	/** The first step of the cheapest known way to beside c: a turn on the electrified floor costs ELECCOST steps and
	 *  a cell a blow is coming at 30, others' cells can't be walked; the end cell beside c costs 3 turns more on the
	 *  electrified floor (it's struck from there). One step at a time, so the game's own walk can't take another way. */
	static int elecStep(int c) {
		int hp = Dungeon.hero.pos, N = L.length(), W = L.width();
		int[] cost = new int[N]; Arrays.fill(cost, INF); int[] first = new int[N];
		PriorityQueue<int[]> q = new PriorityQueue<>((x, y) -> Integer.compare(x[0], y[0]));
		cost[hp] = 0; first[hp] = -1; q.add(new int[]{0, hp});
		int best = -1, bs = INF;
		while (!q.isEmpty()) {
			int[] e = q.poll(); int u = e[1]; if (e[0] > cost[u]) continue;
			int sc = L.distance(u, c) <= 1 && u != c ? cost[u] + (elec[u] ? 3 * ELECCOST : 0) : unseenBy(u) ? 1000 + L.distance(u, c) * 2 + cost[u] : INF;
			if (u != hp && sc < bs) { bs = sc; best = u; }
			for (int k : PathFinder.NEIGHBOURS8) { int n = u + k;
				if (n < 0 || n >= N || Math.abs(n % W - u % W) > 1 || !L.passable[n] || L.avoid[n] || !(known[n] || L.heroFOV[n]) || (seenChar(n) != null && n != hp)) continue;
				int nc = cost[u] + 1 + (elec[n] ? ELECCOST : 0) + (dz[n] ? 30 : !elec[n] && danger[n] ? 4 : 0);
				if (nc < cost[n]) { cost[n] = nc; first[n] = u == hp ? n : first[u]; q.add(new int[]{nc, n}); } }
		}
		return best >= 0 ? first[best] : -1;
	}

	/** DM-300 not charged: each step it takes onto one of the arena's trap tiles while hunting shields it (30 + a tenth
	 *  of its lost health: DM300.move), unless the tile is electrified. Coming at the hero, its last step is onto a cell
	 *  beside him: met where no trap tile is beside the hero (strategy-reference 4.3), a step or two away. */
	static Target dmTraps(DM300 dm) {
		int hp = Dungeon.hero.pos;
		if (!enemy(dm) || !hunting(dm) || L.adjacent(dm.pos, hp) || L.distance(dm.pos, hp) > 6 || L.distance(dm.pos, hp) < 2 || Dungeon.hero.rooted) return null;
		int here = trapsBeside(hp);
		if (here == 0) return null;
		int best = -1, bk = here * 10;
		for (int c = 0; c < L.length(); c++) {
			if (dist[c] <= 0 || dist[c] > 2 || !open[c] || danger[c] || seenChar(c) != null || L.adjacent(c, dm.pos)) continue;
			int k = trapsBeside(c) * 10 + dist[c] * 3;
			if (k < bk) { bk = k; best = c; } }
		return best >= 0 ? new Target(best, GO, "boss: meet DM-300 off its trap tiles") : null;
	}
	static int trapsBeside(int c) { int n = 0; for (int k : PathFinder.NEIGHBOURS8) { int x = c + k; if (x >= 0 && x < L.length() && L.map[x] == Terrain.INACTIVE_TRAP && !elec[x]) n++; } return n; }

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

	/** The Dwarf King (strategy-reference 4.4; DwarfKing): his minions first in every phase (in phase 2 he is immune
	 *  and only their deaths wear his barrier down: DwarfKing.KingDamager; in 1 and 3 they are what kills). Beside the
	 *  hero: the warlock first (its bolt ignores armour), then the weakest. None beside him and a warlock bolting him:
	 *  to it, round the king's side when he's immune (the walk to a warlock beside the throne and the step back off
	 *  the immune king took turns while it shot, the commonest death on depth 20). Nothing to strike: off the immune
	 *  king's side, to a cell no warlock's bolt reaches (a minion between them), else where fewest foes touch. */
	static Target king(DwarfKing dk) {
		int hp = Dungeon.hero.pos;
		if (!dk.isAlive()) return null;
		boolean immune = dk.isInvulnerable(Hero.class);
		if (!immune && !KINGALL) return null;
		Mob o = minionAdjacent(dk);
		// (not immune: he summons for as long as he lives, so only the warlocks and monks before him: a hero who
		// struck only the minions, the king beside him, killed ghouls for 400 turns till he died)
		if (o != null && !immune && L.adjacent(dk.pos, hp) && !(o instanceof DwarfKing.DKWarlock || o instanceof DwarfKing.DKMonk) && enemy(dk) && !Dungeon.hero.isCharmedBy(dk))
			return new Target(dk.pos, ENEMY, "boss: the king, his ghouls can wait");
		if (o != null) return new Target(o.pos, ENEMY, immune ? "boss: his minions first" : "boss: his minions before the king");
		if (Dungeon.hero.rooted) return null;
		Mob wl = null; int wd = INF;
		for (Mob m : mobs()) if (m instanceof DwarfKing.DKWarlock && enemy(m) && m.isAlive() && L.heroFOV[m.pos] && !m.isInvulnerable(Hero.class) && bolt(m.pos, hp) && L.distance(m.pos, hp) < wd) { wd = L.distance(m.pos, hp); wl = m; }
		if (KINGWL && wl != null && (immune || !L.adjacent(dk.pos, hp))) {
			int st = kingStep(wl.pos, immune ? dk.pos : -1);
			if (st >= 0) return new Target(st, GO, "boss: to his warlock (its bolt ignores armour)");
		}
		if (immune && L.adjacent(dk.pos, hp)) {   // beside him with nothing else to hit: off his side
			int c = stepOffKing(dk.pos);
			if (c >= 0) return new Target(c, GO, "boss: off the immune king");
		}
		return null;
	}
	static final boolean KINGALL = opt("BKINGALL", true), KINGWL = opt("BKINGWL", true);

	/** A shadow bolt from a to b: MAGIC_BOLT stops at the first one in its way (a minion between is a shield). */
	static boolean bolt(int a, int b) { return new Ballistica(a, b, Ballistica.MAGIC_BOLT).collisionPos == b; }

	/** The minion beside the hero to strike: a warlock first, then the weakest (never the king himself). */
	static Mob minionAdjacent(DwarfKing dk) {
		Mob best = null; int hp = Dungeon.hero.pos, bs = INF;
		for (Mob m : mobs()) if (m != dk && enemy(m) && L.adjacent(m.pos, hp) && !m.isInvulnerable(Hero.class) && !Dungeon.hero.isCharmedBy(m) && m.state != m.PASSIVE) {
			int sc = m.HP + (m instanceof DwarfKing.DKWarlock ? -1000 : m instanceof DwarfKing.DKMonk ? -500 : 0);
			if (sc < bs) { bs = sc; best = m; } }
		return best;
	}

	/** The first step to beside c, round the immune king's side (each cell beside him costs 6 more) and off the
	 *  summoning circles and other blows' cells. */
	static int kingStep(int c, int king) {
		int hp = Dungeon.hero.pos, N = L.length(), W = L.width();
		int[] cost = new int[N]; Arrays.fill(cost, INF); int[] first = new int[N];
		PriorityQueue<int[]> q = new PriorityQueue<>((x, y) -> Integer.compare(x[0], y[0]));
		cost[hp] = 0; first[hp] = -1; q.add(new int[]{0, hp});
		int best = -1, bs = INF;
		while (!q.isEmpty()) {
			int[] e = q.poll(); int u = e[1]; if (e[0] > cost[u]) continue;
			if (u != hp && L.adjacent(u, c) && cost[u] < bs) { bs = cost[u]; best = u; }
			for (int k : PathFinder.NEIGHBOURS8) { int n = u + k;
				if (n < 0 || n >= N || Math.abs(n % W - u % W) > 1 || !L.passable[n] || L.avoid[n] || !(known[n] || L.heroFOV[n]) || (seenChar(n) != null && n != hp)) continue;
				int nc = cost[u] + 1 + (danger[n] ? 8 : 0) + (king >= 0 && L.adjacent(n, king) ? 6 : 0);
				if (nc < cost[n]) { cost[n] = nc; first[n] = u == hp ? n : first[u]; q.add(new int[]{nc, n}); } }
		}
		return best >= 0 && bs < 40 ? first[best] : -1;
	}

	/** A free step off the immune king's side: out of his warlocks' bolts where it can be, fewest foes touching. */
	static int stepOffKing(int king) {
		int hp = Dungeon.hero.pos, best = -1, bs = INF;
		for (int k : PathFinder.NEIGHBOURS8) { int n = hp + k;
			if (n < 0 || n >= L.length() || !open[n] || seenChar(n) != null || danger[n] || L.adjacent(n, king)) continue;
			int s = adjFoes(n) * 3;
			for (Mob m : mobs()) if (m instanceof DwarfKing.DKWarlock && enemy(m) && m.isAlive() && bolt(m.pos, n)) s += 5;
			if (s < bs) { bs = s; best = n; } }
		return best >= 0 ? best : stepOff(king, 2);
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
		StringBuilder sb = new StringBuilder("    BOSSMAP " + (L instanceof PrisonBossLevel ? ((PrisonBossLevel) L).state() : "") + " hp " + hp + " t " + Actor.now() + "\n");
		int W = L.width();
		for (int y = 0; y < L.height(); y++) { sb.append("    ");
			for (int x = 0; x < W; x++) { int c = x + y * W; int t = L.map[c];
				Char who = Actor.findChar(c);
				char ch = c == hp ? '@' : who instanceof DM300 || who instanceof DwarfKing ? 'D' : who instanceof DwarfKing.DKWarlock ? 'W' : who instanceof DwarfKing.DKMonk ? 'M' : who instanceof DwarfKing.DKGhoul ? 'G' : who instanceof DwarfKing.DKGolem ? 'O' : who instanceof Pylon ? (((Mob) who).alignment == Char.Alignment.ENEMY ? 'P' : 'p') : dz.length == LEN && dz[c] ? '!' : elec.length == LEN && elec[c] ? 'E' : t == Terrain.INACTIVE_TRAP ? '^' : t == Terrain.WATER ? '~' : !known[c] ? ' ' : t == Terrain.WALL || t == Terrain.WALL_DECO ? '#' : t == Terrain.DOOR ? '+' : t == Terrain.LOCKED_DOOR ? 'L' : L.passable[c] ? (dist[c] < INF ? '.' : ',') : '?';
				sb.append(ch); }
			sb.append('\n'); }
		for (Mob m : L.mobs) sb.append("    mob " + m.getClass().getSimpleName() + " at " + m.pos + " fov " + (m.pos >= 0 && m.pos < LEN && L.heroFOV[m.pos]) + " hp " + m.HP + " state " + (m.state == null ? "" : m.state.getClass().getSimpleName()) + " inv " + m.invisible + "\n");
		System.out.print(sb);
	}
}
