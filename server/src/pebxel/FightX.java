package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.NPC;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import java.util.*;

/**
 * Fight forensics (agent HE, env FIGHTX=1; only prints). A fight starts when a seen, awake enemy is beside the hero or
 * hunting within 4, and ends 3 decisions after the last one, on a new floor or at death. One "FIGHTX" line each:
 * depth, health at the start, health lost and damage dealt, died, foes, most beside at once, the open cells round the
 * hero at the start (8 = the open, 2 = a corridor) and whether in a doorway, hero blows / surprise blows / ranged
 * uses, health lost where 4+ cells round were open against fewer, and the foes' kinds.
 */
final class FightX {
	static final boolean ON = "1".equals(System.getenv("FIGHTX"));
	static boolean active; static int hp0, lastHp, depth, quiet, maxAdj, open0, atk, surp, rng, lostOpen, lostTight, dealt, sleepers;
	static boolean door0; static String names;
	static final Map<Integer, Integer> mobHp = new HashMap<>();

	static void newGame() { active = false; mobHp.clear(); }

	static int open(Level L, int c) { int n = 0; for (int k : com.watabou.utils.PathFinder.NEIGHBOURS8) { int x = c + k; if (x >= 0 && x < L.length() && !L.solid[x] && L.passable[x]) n++; } return n; }

	static List<Mob> engaged(Hero h) {
		List<Mob> out = new ArrayList<>();
		for (Mob m : new ArrayList<>(Dungeon.level.mobs)) {
			if (!m.isAlive() || m.alignment != Char.Alignment.ENEMY || m instanceof NPC || m.invisible > 0 || !Dungeon.level.heroFOV[m.pos] || m.state == m.PASSIVE || m.state == m.SLEEPING) continue;
			int d = Dungeon.level.distance(m.pos, h.pos);
			if (d <= 1 || (m.state == m.HUNTING && d <= 4)) out.add(m);
		}
		return out;
	}

	static void decision(Assist.Target t) {
		if (!ON) return;
		Hero h = Dungeon.hero; Level L = Dungeon.level; if (h == null || L == null) return;
		if (active && Dungeon.depth != depth) end(false);
		List<Mob> ms = engaged(h);
		if (!active) {
			if (ms.isEmpty()) return;
			active = true; hp0 = lastHp = h.HP; depth = Dungeon.depth; quiet = 0; maxAdj = 0; atk = surp = rng = lostOpen = lostTight = dealt = 0;
			open0 = open(L, h.pos); door0 = L.map[h.pos] == Terrain.DOOR || L.map[h.pos] == Terrain.OPEN_DOOR; mobHp.clear();
			StringBuilder sb = new StringBuilder(); for (Mob m : ms) { sb.append(m.getClass().getSimpleName()).append(','); mobHp.put(m.id(), m.HP); }
			names = sb.toString();
		}
		int dh = lastHp - h.HP; if (dh > 0) { if (open(L, h.pos) >= 4) lostOpen += dh; else lostTight += dh; }
		lastHp = h.HP;
		for (Mob m : ms) if (!mobHp.containsKey(m.id())) { mobHp.put(m.id(), m.HP); names += "+" + m.getClass().getSimpleName() + ","; }
		for (Map.Entry<Integer, Integer> e : mobHp.entrySet()) {
			Actor a = Actor.findById(e.getKey()); int now = a instanceof Mob && ((Mob) a).isAlive() ? ((Mob) a).HP : 0;
			if (now < e.getValue()) dealt += e.getValue() - now;
			e.setValue(now);
		}
		int adj = 0; for (Mob m : ms) if (L.adjacent(m.pos, h.pos)) adj++;
		maxAdj = Math.max(maxAdj, adj);
		if (t != null && t.kind == Assist.ENEMY && t.action == null && t.cell >= 0 && L.adjacent(t.cell, h.pos)) {
			Char c = Actor.findChar(t.cell); if (c instanceof Mob) { atk++; if (((Mob) c).surprisedBy(h, true)) surp++; } }
		if (t != null && t.kind == Assist.USE && t.action != null && (t.action.contains("THROW") || t.action.contains("ZAP") || t.action.equals("SHOOT"))) rng++;
		if (ms.isEmpty()) { if (++quiet >= 3) end(false); } else quiet = 0;
	}

	static void end(boolean died) {
		if (!ON || !active) return;
		active = false;
		Hero h = Dungeon.hero;
		int loss = hp0 - (died || h == null ? 0 : Math.min(lastHp, h.HP));
		if (!died && loss <= 0 && dealt <= 0) return;
		System.out.printf(Locale.ROOT, "FIGHTX depth %d hp0 %d ht %d loss %d dealt %d died %d foes %d maxAdj %d open0 %d door0 %d atk %d surp %d rng %d lostOpen %d lostTight %d mobs %s%n",
				depth, hp0, h == null ? 0 : h.HT, loss, dealt, died ? 1 : 0, mobHp.size(), maxAdj, open0, door0 ? 1 : 0, atk, surp, rng, lostOpen, lostTight, names);
	}
}
