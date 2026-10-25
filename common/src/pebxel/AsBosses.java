package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.PrisonBossLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import java.util.*;
import pebxel.Assist.Target;
import static pebxel.Assist.*;

/**
 * The Assistant's skill: each boss's own mechanics (Goo, Tengu, DM-300, the Dwarf King, Yog) (agent K3; the strategy
 * reference's chapter 4 and the bosses' source). Two hooks: {@link #danger} adds the boss's telegraphed blows to the
 * cells the plan keeps off (Assist.hazards), {@link #tactics} a move of its own on a boss's floor. Only what the
 * screen shows is used: bombs and shockers lying where they were thrown, the fire's flames, a pylon lit up, a
 * summoning circle glowing, the beams' marked cells.
 */
final class AsBosses {
	private AsBosses() {}

	static final boolean ON = opt("BOSSES", true);

	// =============================================================================================================
	// the cells to keep off (worked out once a turn)
	// =============================================================================================================
	static boolean[] dz = new boolean[0]; static boolean dzAny;
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
		if (dz.length != l.length()) dz = new boolean[l.length()]; else Arrays.fill(dz, false);
		dzAny = false;
		if (!Dungeon.bossLevel()) return;
		if (l instanceof PrisonBossLevel) tengu(l);
		if (dzAny) for (int c = 0; c < dz.length; c++) if (l.solid[c]) dz[c] = false;
	}

	static void mark(Level l, int c) { if (c >= 0 && c < dz.length) { dz[c] = true; dzAny = true; } }

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
					while (!q.isEmpty()) { int c = q.poll(); mark(l, c); if (d[c] >= 2) continue;
						for (int k : com.watabou.utils.PathFinder.NEIGHBOURS8) { int n = c + k; if (n >= 0 && n < l.length() && Math.abs(n % W - c % W) <= 1 && !l.solid[n] && d[n] > d[c] + 1) { d[n] = d[c] + 1; q.add(n); } } }
				} else if (i instanceof Tengu.ShockerAbility.ShockerItem) {
					for (int k : com.watabou.utils.PathFinder.NEIGHBOURS9) mark(l, h.pos + k);
				}
			}
		}
		Blob f = l.blobs.get(Tengu.FireAbility.FireBlob.class);
		if (f != null && f.volume > 0 && f.cur != null) for (int c = 0; c < l.length() && c < f.cur.length; c++) if (f.cur[c] > 0) mark(l, c);
	}

	// =============================================================================================================
	// the boss's own move
	// =============================================================================================================

	/** A tactical move for this turn on a boss's floor, or null (then the usual tactics). */
	static Target tactics() {
		if (!ON || !Dungeon.bossLevel()) return null;
		int hp = Dungeon.hero.pos, LEN = L.length();
		if (L instanceof PrisonBossLevel && DBG) dump();
		danger(0);
		if (dzAny && dz[hp]) {   // a blow coming here: the nearest cell it misses (through the danger, if that's the only way)
			int best = -1, bk = INF;
			for (int c = 0; c < LEN; c++) if (dist[c] < bk && dist[c] > 0 && !dz[c] && !foul(c) && L.passable[c] && !L.avoid[c] && seenChar(c) == null) { bk = dist[c]; best = c; }
			if (best >= 0) return new Target(best, GO, "boss: out of the blow's way");
		}
		return null;
	}

	static void dump() {
		int hp = Dungeon.hero.pos, LEN = L.length();
		StringBuilder sb = new StringBuilder("    TENGU state " + ((PrisonBossLevel) L).state() + " hp " + hp + "\n");
		int W = L.width();
		for (int y = 0; y < L.height(); y++) { sb.append("    ");
			for (int x = 0; x < W; x++) { int c = x + y * W; int t = L.map[c];
				char ch = c == hp ? '@' : dz.length == LEN && dz[c] ? '!' : !known[c] ? ' ' : t == Terrain.WALL || t == Terrain.WALL_DECO ? '#' : t == Terrain.DOOR ? '+' : t == Terrain.LOCKED_DOOR ? 'L' : L.passable[c] ? (dist[c] < INF ? '.' : ',') : '?';
				sb.append(ch); }
			sb.append('\n'); }
		System.out.print(sb);
	}
}
