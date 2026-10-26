package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import java.util.*;
import pebxel.Assist.Target;
import static pebxel.Assist.*;

/**
 * The list as a guess at the player's next move (agent DS; Thomas: "the alternate suggestions are what the player
 * might do next ... make the system that promotes and demotes alternate plans feel psychic"). Plan A stays the bot's
 * best unless the player has shown he wants something else (he chose it: Assist's "your choice"; his own walks:
 * Assist.mind). The entries after it are this player's likely next moves:
 * <ul>
 * <li><b>His way.</b> A walk he chose or tapped sets the way he is going. Once what he chose is reached and the way
 *   that way runs out, the next thing that way (a door he has been through, the stairs, the unexplored) stays offered
 *   as plan B, the bot's plan A first, until he takes it (then it leads) or keeps taking plan A (it fades).</li>
 * <li><b>His habits.</b> Each kind of entry (items, grass, doors, the unexplored, searching...) earns a liking when he
 *   picks it over plan A, and loses it when he passes it over. Entries after plan A are ordered by the list's own order,
 *   lifted by his liking and by lying his way. A kind he has passed over twice and never taken since (grass, doors,
 *   searching, talking; never the loot, which wins games) is no plan A (still offered after it) while something else is.</li>
 * <li><b>What he passed over.</b> Passed over in a fight: forgotten while it lasts (Assist's combatHeld); after it, plan
 *   A again once, close by (loot: a few steps) or on the way, else after the rest. Passed
 *   over otherwise: held back (Assist's demotion, longer each time it is passed over again: 20, 60, 180 turns); then
 *   offered as plan A once more, only when it lies on the way to where he is going (a step or two out of the way), at
 *   least a while later. Passed over again: not plan A again on this floor (still last in the list).</li>
 * </ul>
 * Only for the player: the bot never chooses, so its plan A is unchanged.
 */
final class AsIntent {
	private AsIntent() { }

	/** AS_INTENT=0 (tests): the list as before (no reordering, no once-more, "your way on" plan A, demotions flat). */
	static final boolean ON = opt("INTENT", true);
	static Object floor;
	/** The way the player is going (pang 0..63, -1 none), since when, and plan A taken since (it fades after FADE). */
	static int dir = -1; static float dirAt = -99; static int planAs;
	static final int FADE = 4, DIR_TURNS = 80;
	/** His liking per kind of entry (picked over plan A +1, passed over -1; each choice decays the rest a little). */
	static final Map<String, Double> like = new HashMap<>();
	/** What he passed over, by objective: how many times, when, and whether the once-more on the way was used. */
	static final class Skip { int n; float at; boolean again, combat; String why, cat; int kind;
		Skip copy() { Skip c = new Skip(); c.n = n; c.at = at; c.again = again; c.combat = combat; c.why = why; c.cat = cat; c.kind = kind; return c; } }
	static final Map<Integer, Skip> skips = new HashMap<>();
	static final int AGAIN_AFTER = 10, DETOUR = 2, AGAIN_NEAR = 8, NEAR = 3;
	/** Passed over in a fight that is now over: plan A again once, when it lies close by (NEAR steps) or on the way. */
	static final Set<Integer> postFight = new HashSet<>();
	static void afterFight(Set<Integer> held) { if (ON) postFight.addAll(held); }
	/** Counters (tests, the bot's report): lists reordered by his way or liking, plans A held back as disliked, once-mores. */
	static long ranked, disliked, again;

	static void newFloor() {
		if (floor == L) return;
		floor = L; dir = -1; skips.clear(); postFight.clear();
	}

	/** The kind of an entry, for his liking. */
	static String cat(Target t) {
		if (t == null) return "";
		switch (t.kind) {
			case ENEMY: return "fight";
			case USE: return "use";
			case WAIT: return REST_ACT.equals(t.action) ? "rest" : SEARCH_ACT.equals(t.action) ? "search" : "wait";
		}
		String w = t.why == null ? "" : t.why;
		if (w.startsWith("pick up") || w.startsWith("item") || w.startsWith("buy")) return "item";
		if (w.contains("grass")) return "grass";
		if (w.startsWith("door")) return "door";
		if (w.startsWith("unexplored") || w.startsWith("explore") || w.startsWith("look")) return "explore";
		if (w.startsWith("stairs") || w.equals("up")) return "stairs";
		if (w.startsWith("talk")) return "talk";
		if (w.startsWith("dead end") || w.startsWith("search")) return "search";
		if (w.startsWith("unlock")) return "unlock";
		if (w.startsWith("your") || w.startsWith("resume")) return "way";
		return "other";
	}
	/** Kinds a player may simply not care for (never the fight, the way on, a skill's step, his own choice). */
	static boolean optional(String c) { return c.equals("item") || c.equals("grass") || c.equals("door") || c.equals("search") || c.equals("talk"); }
	static double like(String c) { Double v = like.get(c); return v == null ? 0 : v; }
	static void liked(String c, double d) { if (c.isEmpty()) return; like.replaceAll((k, v) -> v * 0.95); like.merge(c, d, Double::sum); }

	/** Assist.chose: the player took t while planA led. */
	static void chose(Target t, Target planA) {
		float now = Actor.now();
		if (t.same(planA)) { if (++planAs >= FADE && dir >= 0) { dir = -1; if (ovrC == WAY) ovrC = -1; } return; }
		planAs = 0;
		String ct = cat(t), ca = cat(planA);
		if (!ct.equals(ca)) { liked(ct, 1); if (optional(ca) && t.kind != ENEMY) liked(ca, -1); }
		int o = t.obj >= 0 ? t.obj : t.cell;
		if ((t.kind == GO || t.kind == PICKUP) && o >= 0 && o != heroPos() && L != null) {
			int dx = L.cellToPoint(o).x - L.cellToPoint(heroPos()).x, dy = L.cellToPoint(o).y - L.cellToPoint(heroPos()).y;
			if (Math.abs(dx) + Math.abs(dy) >= 2) { dir = pang(dx, dy); dirAt = now; }
		}
	}
	/** A walk tapped on the map: his way too. */
	static void tapped(int cell, int from) {
		int dx = L.cellToPoint(cell).x - L.cellToPoint(from).x, dy = L.cellToPoint(cell).y - L.cellToPoint(from).y;
		if (Math.abs(dx) + Math.abs(dy) >= 2) { dir = pang(dx, dy); dirAt = Actor.now(); planAs = 0; }
	}
	/** Assist.pass: e passed over (in a fight or not). */
	static void skipped(Target e) {
		if (e.kind != GO && e.kind != PICKUP) { if (optional(cat(e))) liked(cat(e), -0.5); return; }
		int o = e.obj >= 0 ? e.obj : e.cell; if (o < 0) return;
		if (e.why != null && (e.why.startsWith("your") || e.why.startsWith("resume"))) return;   // (his own way echoed back: no suggestion of ours to offer again)
		Skip s = skips.computeIfAbsent(o, k -> new Skip());
		s.n++; s.at = Actor.now(); s.combat = fight || seenFoe; s.why = e.why; s.kind = e.kind; s.cat = cat(e);
		if (optional(cat(e)) && !s.combat) liked(cat(e), -0.5);
	}
	/** How much longer an objective passed over again stays held back (Assist.pass's demotion): 1, 3, 9 times. */
	static int demoteMult(int o) { if (!ON) return 1; Skip s = skips.get(o); int n = s == null ? 1 : s.n; return n <= 1 ? 1 : n == 2 ? 3 : 9; }

	/** planList's last pass over the list (the player's side of it). */
	static void rank(List<Target> out) {
		newFloor();
		if (!ON || out.isEmpty() || mode() == 0) return;
		float now = Actor.now();
		if (dir >= 0 && now - dirAt > DIR_TURNS && ovrC != WAY) dir = -1;
		boolean calm = !seenFoe && !inDanger;
		Target a = out.get(0);
		boolean walkA = (a.kind == GO || a.kind == PICKUP) && !a.why.startsWith("escape") && !a.why.startsWith("your") && a.src == null;
		// 0. passed over in the fight just over: plan A once more only close by or on the way, else after the rest
		if (calm && !postFight.isEmpty()) {
			int ao = a.obj >= 0 ? a.obj : a.cell;
			if (postFight.contains(ao) && (a.kind == GO || a.kind == PICKUP)) {
				boolean near = walk(ao) <= (a.kind == PICKUP ? AGAIN_NEAR : NEAR);   // (loot: a few steps more; the gear and the resources are what wins)
				boolean onWay = false;
				for (int j = 1; j < out.size() && !near; j++) { Target e = out.get(j); int eo = e.obj >= 0 ? e.obj : e.cell;
					if ((e.kind == GO || e.kind == PICKUP) && eo >= 0 && eo < dist.length && !postFight.contains(eo) && walk(eo) < INF) { onWay = walk(ao) + distance(ao, eo) - walk(eo) <= DETOUR; break; } }
				if (near || onWay) postFight.remove(ao);
				else for (int j = 1; j < out.size(); j++) { Target e = out.get(j); int eo = e.obj >= 0 ? e.obj : e.cell; if (e.kind != ENEMY && !postFight.contains(eo)) { out.add(0, out.remove(j)); a = out.get(0); break; } }
			}
		}
		// 1. once more, on the way: a thing passed over, a while ago, a step or two off the way to plan A's objective
		if (calm && walkA) {
			int ao = a.obj >= 0 ? a.obj : a.cell, best = -1, bd = INF; Skip bs = null;
			for (Map.Entry<Integer, Skip> e : skips.entrySet()) {
				Skip s = e.getValue(); int o = e.getKey();
				if (s.n != 1 || s.again || s.combat || now - s.at < AGAIN_AFTER || o == ao || o == heroPos() || o < 0 || o >= dist.length) continue;
				if (optional(s.cat) && like(s.cat) <= -2) continue;   // (a kind he doesn't care for: not even on the way)
				int w = walk(o); if (w >= INF || w > AGAIN_NEAR) continue;
				if (s.kind == PICKUP && seenHeap(o) == null) continue;   // (gone meanwhile)
				if (s.why != null && s.why.contains("grass") && L.map[o] != Terrain.HIGH_GRASS) continue;
				int detour = w + distance(o, ao) - walk(ao);
				if (detour <= DETOUR && w < bd) { bd = w; best = o; bs = s; }
			}
			if (best >= 0) {
				bs.again = true; again++;
				Target t = null; for (int j = 1; j < out.size(); j++) { Target e = out.get(j); if ((e.obj >= 0 ? e.obj : e.cell) == best) { t = out.remove(j); break; } }
				if (t == null) { t = new Target(best, bs.kind == PICKUP && seenHeap(best) != null ? PICKUP : GO, ""); t.obj = best; }
				t.why = "on the way: " + (bs.why == null ? "" : bs.why.replace("item, passed over", "item"));
				out.add(0, t); if (out.size() > CAP) out.remove(out.size() - 1);
				a = t;
			}
		}
		// 2. a kind he doesn't care for: no plan A while something else is (not in a fight, never a skill's or his own)
		if (calm && a.src == null && (a.kind == GO || a.kind == PICKUP) && !a.why.startsWith("on the way") && optional(cat(a)) && !cat(a).equals("item") && like(cat(a)) <= -2) {   // (never the loot: it wins the game; only lower in B and C)
			for (int j = 1; j < out.size(); j++) { Target e = out.get(j); if (e.kind != ENEMY && !(optional(cat(e)) && like(cat(e)) <= -2)) { out.add(0, out.remove(j)); disliked++; break; } }
		}
		// 3. his way: the next thing that way (what lies about, the unexplored, the stairs, a door he has been through or
		// not) as plan B, while he is going that way and plan A isn't (Thomas: "go north until we have to turn and then
		// suggest previously explored doors ... that should stay as Plan B until it's selected")
		if (calm && dir >= 0 && (now - dirAt <= DIR_TURNS || ovrC == WAY)) way(out);
		// 4. the entries after plan A: the list's order, lifted by his liking and by lying his way
		if (out.size() < 3 || seenFoe) return;
		int fixed = 1;
		double[] sc = new double[out.size()];
		boolean any = false;
		for (int j = fixed; j < out.size(); j++) {
			Target e = out.get(j);
			double s = -j;   // (the list's own order)
			s += 1.5 * Math.max(-2, Math.min(3, like(cat(e))));
			int o = e.obj >= 0 ? e.obj : e.cell;
			if (dir >= 0 && (e.kind == GO || e.kind == PICKUP) && o >= 0 && o < dist.length && o != heroPos() && off(o, dir) <= CONE) s += e.why.startsWith("your way") ? 6 : 2.5;
			if (e.why.startsWith("item, passed over") || e.why.startsWith("grass") && grassSkip) s -= 3;   // (passed over: last)
			sc[j] = s; any |= s != -j;
		}
		if (!any) return;
		List<Integer> idx = new ArrayList<>(); for (int j = fixed; j < out.size(); j++) idx.add(j);
		idx.sort((p, q) -> Double.compare(sc[q], sc[p]));
		List<Target> re = new ArrayList<>(out.subList(0, fixed)); for (int j : idx) re.add(out.get(j));
		boolean moved = false; for (int j = 0; j < re.size(); j++) moved |= re.get(j) != out.get(j);
		if (moved) { ranked++; out.clear(); out.addAll(re); }
	}

	/** The next thing his way, as plan B (unless plan A or plan B is already that way). */
	static void way(List<Target> out) {
		int hp0 = heroPos();
		for (int j = 0; j < Math.min(2, out.size()); j++) { Target e = out.get(j); int o = e.obj >= 0 ? e.obj : e.cell;
			if ((e.kind == GO || e.kind == PICKUP) && o >= 0 && o < dist.length && o != hp0 && off(o, dir) <= CONE && walk(o) >= 3) return; }
		int nt = -1, ns = INF;
		for (int c = 0; c < dist.length; c++) {
			int sd = dist[c]; if (sd < 3 || sd >= INF || off(c, dir) > CONE || bad.containsKey(c)) continue;
			com.shatteredpixel.shatteredpixeldungeon.items.Heap hh = seenHeap(c);
			int t = L.map[c];
			int w = hh != null && heapOk(hh) ? 3 : c == L.exit() || c == L.entrance() || unseenBy(c) ? 4 : known[c] && (t == Terrain.DOOR || t == Terrain.OPEN_DOOR) ? 4 : 0;
			if (w > 0 && sd * w < ns) { ns = sd * w; nt = c; }
		}
		if (nt < 0) return;
		for (int j = 1; j < out.size(); j++) { Target e = out.get(j); if ((e.obj >= 0 ? e.obj : e.cell) == nt) { out.remove(j); break; } }
		boolean z = zero; zero = true; int h = h0; h0 = 0;
		Target t = put(nt, seenHeap(nt) != null ? PICKUP : GO, 0, "your way");
		zero = z; h0 = h;
		if (t == null) return;
		out.remove(t); out.add(Math.min(1, out.size()), t);
		if (out.size() > CAP) out.remove(out.size() - 1);
	}

	static String stats() {
		StringBuilder b = new StringBuilder(); for (Map.Entry<String, Double> e : new TreeMap<>(like).entrySet()) b.append(e.getKey()).append(String.format(Locale.ROOT, "%+.1f", e.getValue())).append(' ');
		return "ranked " + ranked + " disliked " + disliked + " again " + again + " like " + b.toString().trim();
	}
	static void reset() { like.clear(); skips.clear(); postFight.clear(); dir = -1; planAs = 0; floor = null; ranked = disliked = again = 0; }
}
