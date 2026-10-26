package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Blandfruit;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.*;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.*;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import java.util.*;

/**
 * Where a game's food and turns went, floor by floor (agent K6; for sim/foodstats.py): turns on the floor and the
 * hunger they cost, food picked up and eaten, turns starving, steps walked and the distinct cells they reached, the
 * turns by reason; and at a death the fight it ended: the foes, the health when they first came, what was still held.
 * Harness only (reads what the bot may not: every heap's contents, for the food left behind). "FLOOR" and "DEATH" lines.
 */
final class FoodStats {
	private FoodStats() {}

	static final class Floor { int depth, branch; float t0; int turns, starve, eaten, found, steps, hungerAt0, packAt0, locked; Set<Integer> cells = new HashSet<>(); Map<String, Integer> why = new TreeMap<>(); String left = ""; }
	static final List<Floor> floors = new ArrayList<>();
	static Floor cur; static float lastT; static int lastHunger, lastPack, lastPos; static String lastWhy = "";
	// the fight going on: since a foe came into sight after 10 turns or more with none
	static float foeLastSeen = -99, fightT0; static int fightHp0 = -1, fightHt; static final Map<String, Integer> fightFoes = new TreeMap<>();
	static final ArrayDeque<String> lastWhys = new ArrayDeque<>();

	static void newGame() { floors.clear(); cur = null; lastT = 0; lastHunger = -1; lastWhy = ""; foeLastSeen = -99; fightHp0 = -1; fightFoes.clear(); lastWhys.clear(); }

	static int pack(Hero h) { int t = 0; for (Item i : h.belongings) if (i instanceof Food && !(i instanceof Blandfruit)) t += (int) (((Food) i).energy * i.quantity()); return t; }

	static void decision(Assist.Target t) {
		Hero h = Dungeon.hero; Level L = Dungeon.level; if (h == null || L == null) return;
		float now = Actor.now(); Hunger hu = h.buff(Hunger.class); int hg = hu == null ? 0 : hu.hunger(); int pk = pack(h);
		if (cur == null || cur.depth != Dungeon.depth || cur.branch != Dungeon.branch) {
			cur = new Floor(); cur.depth = Dungeon.depth; cur.branch = Dungeon.branch; cur.t0 = now; cur.hungerAt0 = hg; cur.packAt0 = pk; floors.add(cur);
			lastT = now; lastHunger = hg; lastPack = pk; lastPos = h.pos; foeLastSeen = -99;
		} else {
			float dt = Math.max(0, now - lastT);
			cur.turns += Math.round(dt);
			if (L.locked) cur.locked += Math.round(dt);
			if (hu != null && hu.isStarving()) cur.starve += Math.round(dt);
			if (hg < lastHunger) cur.eaten += lastHunger - hg;
			if (pk > lastPack) cur.found += pk - lastPack;
			if (h.pos != lastPos) { cur.steps++; cur.cells.add(h.pos); }
			cur.why.merge(lastWhy, Math.round(dt), Integer::sum);
			lastT = now; lastHunger = hg; lastPack = pk; lastPos = h.pos;
		}
		cur.left = foodLeft(L);   // (each decision: at the first on the next floor, Dungeon.level is already that floor)
		lastWhy = t == null || t.why == null ? "?" : t.why.replaceAll("[;=,:|]", " ");
		lastWhys.addLast(lastWhy + "@hp" + h.HP); while (lastWhys.size() > 8) lastWhys.removeFirst();
		// the fight
		boolean foe = false;
		for (Mob m : L.mobs) if (m.isAlive() && m.alignment == Char.Alignment.ENEMY && L.heroFOV[m.pos] && m.state != m.PASSIVE) {
			foe = true;
			if (now - foeLastSeen > 10) { fightFoes.clear(); fightHp0 = h.HP; fightHt = h.HT; fightT0 = now; foeLastSeen = now; }
			fightFoes.merge(m.getClass().getSimpleName() + (m.state == m.SLEEPING ? "(z)" : ""), 0, (a, b) -> a);
		}
		if (foe) foeLastSeen = now;
	}

	/** The food on the floor left behind: seen (or mapped) heaps, and the rest (unseen; harness only). */
	static String foodLeft(Level L) {
		if (L == null) return "";
		int seen = 0, unseen = 0; StringBuilder det = new StringBuilder();
		for (Heap hp : L.heaps.valueList()) for (Item i : hp.items) if (i instanceof Food && !(i instanceof Blandfruit)) {
			int e = (int) (((Food) i).energy * i.quantity());
			if (hp.type == Heap.Type.FOR_SALE) continue;
			if (hp.seen || L.visited[hp.pos] || L.mapped[hp.pos]) seen += e; else unseen += e;
			if (DETAIL) det.append(' ').append(i.getClass().getSimpleName()).append('@').append(hp.pos).append(':').append(hp.type).append(hp.seen ? ":seen" : "").append(L.visited[hp.pos] ? ":visited" : "").append(L.secret[hp.pos] ? ":secretcell" : "").append(":room=").append(roomOf(L, hp.pos));
		}
		return seen + "/" + unseen + det;
	}

	static final boolean DETAIL = "1".equals(System.getenv("FOODDETAIL"));
	static String roomOf(Level L, int c) {
		if (!(L instanceof com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel)) return "-";
		com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room r = ((com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel) L).room(c);
		return r == null ? "none" : r.getClass().getSimpleName();
	}

	static void print(String end, String cause) {
		if (cur != null && cur.left.isEmpty()) cur.left = foodLeft(Dungeon.level);
		for (Floor f : floors) {
			StringBuilder sb = new StringBuilder("FLOOR d").append(f.depth).append(f.branch != 0 ? "b" + f.branch : "")
					.append(" turns ").append(f.turns).append(" locked ").append(f.locked).append(" starve ").append(f.starve)
					.append(" eaten ").append(f.eaten).append(" found ").append(f.found).append(" hunger0 ").append(f.hungerAt0).append(" pack0 ").append(f.packAt0)
					.append(" steps ").append(f.steps).append(" cells ").append(f.cells.size()).append(" left ").append(f.left).append(" |");
			for (Map.Entry<String, Integer> e : f.why.entrySet()) if (e.getValue() > 0) sb.append(' ').append(e.getKey()).append(':').append(e.getValue()).append(',');
			System.out.println(sb);
		}
		if (!"died".equals(end)) return;
		Hero h = Dungeon.hero; if (h == null) return;
		int heal = 0, unkP = 0, unkS = 0, esc = 0, wands = 0;
		StringBuilder known = new StringBuilder();
		for (Item i : h.belongings) {
			if (i instanceof PotionOfHealing && ((Potion) i).isKnown()) heal += i.quantity();
			else if (i instanceof Potion && !((Potion) i).isKnown()) unkP += i.quantity();
			else if (i instanceof Scroll && !((Scroll) i).isKnown()) unkS += i.quantity();
			if ((i instanceof ScrollOfTeleportation || i instanceof PotionOfInvisibility || i instanceof ScrollOfMirrorImage || i instanceof ScrollOfLullaby || i instanceof PotionOfHaste || i instanceof ScrollOfRetribution || i instanceof ScrollOfTerror)
					&& (i instanceof Potion ? ((Potion) i).isKnown() : ((Scroll) i).isKnown())) { esc += i.quantity(); known.append(i.getClass().getSimpleName()).append(' '); }
			if (i instanceof Wand && ((Wand) i).curCharges > 0) wands++;
		}
		Hunger hu = h.buff(Hunger.class);
		String killer = cause.contains("-- ") ? cause.substring(cause.lastIndexOf("-- ") + 3).trim() : cause.trim();
		if (killer.length() > 60) killer = killer.substring(0, 60);
		System.out.println("DEATH depth " + Dungeon.depth + " killer " + killer.replace(' ', '_') + " starving " + (hu != null && hu.isStarving())
				+ " hp0 " + (fightHt > 0 ? fightHp0 * 100 / fightHt : -1) + " fightTurns " + Math.round(Actor.now() - fightT0) + " foes " + String.join(",", fightFoes.keySet())
				+ " heal " + heal + " esc " + esc + (known.length() > 0 ? "[" + known.toString().trim().replace(' ', ',') + "]" : "") + " unkP " + unkP + " unkS " + unkS + " wands " + wands
				+ " | " + String.join(" > ", lastWhys));
	}
}
