package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.*;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfLiquidFlame;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.*;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfFireblast;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.MiningLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.plants.Rotberry;
import com.watabou.utils.PathFinder;
import java.util.*;
import pebxel.Assist.Target;
import static pebxel.Assist.*;

/**
 * The Assistant's skill: quests and NPCs, honest (agent K3; strategy-reference chapter 5). Only what the hero has seen:
 * where each quest person was last seen, the quest's own things lying where they were seen (the dust, the candles,
 * the ritual's marked spot, the rot heart, the gold veins), the quest as the dialogue told it. The rewards' choices
 * are AsChoices'. Also the hook for the basics (AsBasics: fights going nowhere, the food budget).
 *
 * Ghost (depth 2-4): talk, the foe is found by exploring and fought by the usual rules (the crab's ambush), then back
 * to where the ghost was last seen. Wandmaker (7-9): the corpse dust from behind its barricade (burnt with a known
 * Liquid Flame or a Wand of Fireblast: there is no other honest way in), the four candles round the ritual spot, the
 * rot heart struck (never burnt); the thing taken back to him. Blacksmith (12-14): his ladder down, the dark gold veins
 * dug (40, or all those seen once the mine is explored), back up and to him; his favours. Imp (17-19): the v4 vault is
 * a stealth floor the bot can't play yet (strategy-reference 5.4: skip it), so the Imp is left alone.
 */
final class AsQuests {
	private AsQuests() {}

	static final boolean ON = opt("QUESTS", true);
	static final int GOLD = optInt("QGOLD", 40);

	// where each quest person was last seen (this floor)
	static final Map<Class<?>, Integer> seenAt = new HashMap<>();
	static Level memL;
	static int steps, stepsDepth = -1;

	/** A suggestion for the hero's hands this turn, or null. planA: the list's first entry so far (may be null). */
	static Target suggest(Target planA) {
		Target t = suggest0(planA);
		if (t != null && (t.kind == GO || t.kind == PICKUP) && (Dungeon.hero.rooted || bad.containsKey(t.cell))) return null;   // (rooted, or it did nothing when tried: Assist's own rules)
		return t;
	}
	static Target suggest0(Target planA) {
		try {
			Target b = AsBasics.fightLoop(planA);
			if (b == null) b = AsBasics.restStairs();
			if (b != null) return b;
			b = AsBasics.crab(planA);
			if (b == null) b = AsBasics.piranha(planA);
			if (b == null) b = AsBasics.spawner(planA);
			if (b == null) b = AsBasics.sleeper(planA);
			if (b != null) return b;
			if (!ON) return AsBasics.hurry(planA);
			remember();
			boolean fightOn = false; for (Mob m : mobs()) if (hostile(m) && (adjacent(m.pos, Dungeon.hero.pos) || hunting(m))) fightOn = true;
			if (fightOn || inDanger) return null;
			if (Dungeon.depth != stepsDepth) { stepsDepth = Dungeon.depth; steps = 0; }
			if (++steps > 1500) return AsBasics.hurry(planA);   // (a step that goes nowhere: given up on this floor)
			Target q = mine();
			if (q == null) q = smith();
			if (q == null) q = ghost(planA);
			if (q == null) q = wandmaker();
			if (q == null) q = AsAlchemy.suggest();
			if (q != null) return q;
			return AsBasics.hurry(planA);
		} catch (Throwable e) { lastError = e; return null; }
	}

	/** A tactic's move vetoed (Assist planList's hook): into the Great Crab's dead end. */
	static boolean vetoMove(Target t) {
		try { return AsBasics.crabVeto(t); } catch (Throwable e) { lastError = e; return false; }
	}

	/** A hands suggestion vetoed (Assist planList's hook). */
	static boolean veto(Target hk) {
		try {
			if (ON && hk != null && "drink to learn".equals(hk.why) && graveOpen()) return true;   // (unknown potions kept for the barricade: one is the floor's Liquid Flame)
			if (AsBosses.vetoAim(hk)) return true;
			return AsBasics.veto(hk);
		} catch (Throwable e) { lastError = e; return false; }
	}
	/** The corpse dust quest on, its barricade seen standing and no known fire to burn it: the Wandmaker's floor. */
	static boolean graveOpen() {
		if (!flag(Wandmaker.class, "spawned") || !flag(Wandmaker.class, "given") || Wandmaker.Quest.wand1 == null || !seenAt.containsKey(Wandmaker.class)) return false;
		Object t = qobj(Wandmaker.class, "type"); if (!(t instanceof Integer) || (Integer) t != 1 || Dungeon.hero.belongings.getItem(CorpseDust.class) != null) return false;
		if (gambleL == L && gambles >= 3) return false;
		for (int c = 0; c < L.length(); c++) if (L.map[c] == Terrain.BARRICADE && known[c]) return true;
		return false;
	}

	static void remember() {
		if (memL != L) { memL = L; seenAt.clear(); }
		for (Mob m : mobs()) if (m instanceof NPC && m.isAlive() && L.heroFOV[m.pos]) seenAt.put(m.getClass(), m.pos);
	}

	static boolean flag(Class<?> npc, String f) { return questFlag(npc, f, false); }
	static Object qobj(Class<?> npc, String f) {
		try { Class<?> q = Class.forName(npc.getName() + "$Quest"); java.lang.reflect.Field fl = q.getDeclaredField(f); fl.setAccessible(true); return fl.get(null); } catch (Throwable e) { return null; }
	}

	/** To the quest person (in sight: to him, which talks; else to where he was last seen). */
	static Target toNpc(Class<?> c, String why) {
		for (Mob m : mobs()) if (c.isInstance(m) && m.isAlive() && L.heroFOV[m.pos]) {
			if (talked.getOrDefault(m.id(), 0) >= 3 && adjacent(m.pos, Dungeon.hero.pos)) return null;   // (talked to and nothing came of it)
			return walk(m.pos) < INF ? new Target(m.pos, GO, why) : null;
		}
		Integer p = seenAt.get(c);
		if (p == null || p == Dungeon.hero.pos || distance(p, Dungeon.hero.pos) <= 1 || walk(p) >= INF) { if (p != null && distance(p, Dungeon.hero.pos) <= 1) seenAt.remove(c); return null; }
		return new Target(p, GO, why);
	}

	// =============================================================================================================
	// the Ghost: his foe killed, back to him for the reward
	// =============================================================================================================
	static Target ghost(Target planA) {
		if (!flag(Ghost.class, "given") || Ghost.Quest.completed()) return null;
		if (Ghost.Quest.processed()) return toNpc(Ghost.class, "quest: back to the ghost");
		// the floor done and his foe not met: it wanders (the crab towards the hero): round the floor a while for it
		if (!seenAt.containsKey(Ghost.class) || planA == null || !(planA.why.equals("stairs") || planA.why.startsWith("search") || planA.why.startsWith("dead end")) || AsBasics.shortOfFood()) return null;
		if (patrolL != L) { patrolL = L; patrolT0 = Actor.now(); patrolGoal = -1; }
		if (Actor.now() - patrolT0 > GHOSTHUNT) return null;
		int hp = Dungeon.hero.pos;
		if (patrolGoal < 0 || distance(patrolGoal, hp) <= 1 || walk(patrolGoal) >= INF) {   // the known cell farthest from the last two goals
			int best = -1, bs = -1;
			for (int c = 0; c < L.length(); c++) if (dist[c] < INF && dist[c] > 0 && open[c] && !L.water[c]) { int sc = distance(c, hp) + (patrolLast >= 0 ? distance(c, patrolLast) : 0); if (sc > bs) { bs = sc; best = c; } }
			patrolLast = patrolGoal < 0 ? hp : patrolGoal; patrolGoal = best;
		}
		return patrolGoal >= 0 ? new Target(patrolGoal, GO, "quest: round the floor for the ghost's foe") : null;
	}
	static Level gambleL; static int gambles;
	static Level patrolL; static float patrolT0; static int patrolGoal = -1, patrolLast = -1;
	static final int GHOSTHUNT = optInt("QGHOST", 400);   // turns spent looking for it once the floor is done

	// =============================================================================================================
	// the Wandmaker
	// =============================================================================================================
	static Target wandmaker() {
		if (!flag(Wandmaker.class, "spawned") || !flag(Wandmaker.class, "given") || Wandmaker.Quest.wand1 == null || Dungeon.depth < 7 || Dungeon.depth > 9) return null;
		if (!seenAt.containsKey(Wandmaker.class)) return null;   // (his floor, him seen)
		Hero h = Dungeon.hero;
		if (h.belongings.getItem(CorpseDust.class) != null || h.belongings.getItem(Embers.class) != null || h.belongings.getItem(Rotberry.Seed.class) != null)
			return toNpc(Wandmaker.class, "quest: back to the Wandmaker");
		Object t = qobj(Wandmaker.class, "type"); int type = t instanceof Integer ? (Integer) t : 0;
		if (DBG) System.out.println("    WANDMAKER type " + type + " given " + flag(Wandmaker.class, "given"));
		if (type == 1) return grave();
		if (type == 2) return candles();
		if (type == 3) return heart();
		return null;
	}

	static final int THROWWALKS = optInt("QTHROWWALKS", 150);   // walks towards a throwing spot on a floor, at most
	static int throwWalks; static Level throwL;

	/** The mass grave: the corpse dust behind its barricade (it burns). */
	static Target grave() {
		Heap dust = null;
		for (Heap hp : L.heaps.valueList()) if (hp.seen) for (Item i : hp.items) if (i instanceof CorpseDust) dust = hp;
		if (dust != null && (walk(dust.pos) < INF || dust.pos == Dungeon.hero.pos)) return new Target(dust.pos, PICKUP, "quest: the corpse dust");
		// the grave is behind a barricade (the Wandmaker said so): the one by the dust, or (the dust not seen yet) any seen
		int bar = -1, bd = INF, ref = dust != null ? dust.pos : Dungeon.hero.pos;
		for (int c = 0; c < L.length(); c++) if (L.map[c] == Terrain.BARRICADE && known[c] && distance(c, ref) < bd) { bd = distance(c, ref); bar = c; }
		if (DBG) { int nb = 0; for (int c = 0; c < L.length(); c++) if (L.map[c] == Terrain.BARRICADE) nb++; System.out.println("    GRAVE bar " + bar + " barricades " + nb + " dust " + (dust == null ? -1 : dust.pos)); }
		if (bar < 0 || (dust != null && bd > 10)) return null;
		Hero h = Dungeon.hero; Item fire = null; String act = Item.AC_THROW;
		PotionOfLiquidFlame lf = h.belongings.getItem(PotionOfLiquidFlame.class); if (lf != null && lf.isKnown()) fire = lf;
		WandOfFireblast fb = h.belongings.getItem(WandOfFireblast.class); if (fire == null && fb != null && charged(fb, fb)) { fire = fb; act = Wand.AC_ZAP; }
		if (fire == null && gambleL == L && gambles >= 3) return null;
		if (fire == null) {   // nothing known to burn it with: the floor holds a Liquid Flame for this (MassGraveRoom): the unknown potion likeliest to be it, by the labels' odds (pebxel.bot.Identify), not one likely Healing
			double bp = 0.15;
			for (Item i : h.belongings.backpack) if (i instanceof com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion && !known(i)) {
				double pf = pebxel.bot.Identify.p(i.getClass(), PotionOfLiquidFlame.class), ph = pebxel.bot.Identify.p(i.getClass(), com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing.class)
						+ pebxel.bot.Identify.p(i.getClass(), com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength.class) + pebxel.bot.Identify.p(i.getClass(), com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfExperience.class);
				if (pf > bp && ph < 0.25) { bp = pf; fire = i; } }   // (never one likely Healing, Strength or Experience)
			if (DBG) System.out.println("    GRAVE gamble " + fire);
			if (fire == null) return null;
		}
		int d = distance(h.pos, bar);
		if (DBG) System.out.println("    GRAVE fire " + fire + " d " + d + " line " + line(h.pos, bar) + " bar " + bar + " hp " + h.pos);
		if (d >= 1 && d <= 4 && line(h.pos, bar) && (!L.flamable[h.pos] || d >= 3)) {   // (beside it from bare floor will do: fire spreads only over what burns)
			if (!known(fire)) { if (gambleL != L) { gambleL = L; gambles = 0; } gambles++; }
			return use(fire, act, bar, known(fire) ? "quest: burn the barricade" : "quest: an unknown potion at the barricade");
		}
		int best = -1, bb = INF;   // a spot 2-3 off with a clear throw
		for (int c = 0; c < L.length(); c++) if (dist[c] < INF && open[c] && seenChar(c) == null && distance(c, bar) >= 1 && distance(c, bar) <= 4 && (!L.flamable[c] || distance(c, bar) >= 3) && line(c, bar)) {   // (the throw's own test, below: a spot where it can't be thrown was walked to for ever)
			int k = dist[c] + (L.flamable[c] ? 6 : 0) + (distance(c, bar) <= 2 ? 2 : 0); if (k < bb) { bb = k; best = c; } }   // (off grass if it can be: the fire spreads)
		if (DBG) { int a = 0, b2 = 0, c3 = 0; for (int c = 0; c < L.length(); c++) if (distance(c, bar) >= 2 && distance(c, bar) <= 4) { if (dist[c] < INF && open[c]) { a++; if (new Ballistica(c, bar, Ballistica.PROJECTILE).collisionPos == bar) b2++; else c3 = new Ballistica(c, bar, Ballistica.PROJECTILE).collisionPos; } }
			System.out.println("    GRAVE spots " + a + " in line " + b2 + " e.g. stops at " + c3 + " best " + best); }
		if (best < 0) { int bk = INF; for (int c = 0; c < L.length(); c++) if (dist[c] > 0 && dist[c] < INF && open[c] && seenChar(c) == null) { int k = distance(c, bar) * 6 + dist[c]; if (k < bk) { bk = k; best = c; } } }   // (none known: nearer it, to see round it)
		if (best < 0 || best == h.pos) return null;
		if (throwL != L) { throwL = L; throwWalks = 0; }
		if (++throwWalks > THROWWALKS) return null;   // (no spot it can be thrown from comes: given up)
		return new Target(best, GO, "quest: to throw at the barricade");
	}

	/** The ritual: a candle onto each of the four cells round the marked spot (thrown, as a player would). */
	static Target candles() {
		Hero h = Dungeon.hero;
		CeremonialCandle cc = h.belongings.getItem(CeremonialCandle.class);
		int rp = CeremonialCandle.ritualPos, W = L.width();
		if (cc == null || rp <= 0 || rp >= L.length() || !known[rp]) return null;
		for (int cell : new int[]{rp - W, rp + 1, rp + W, rp - 1}) {
			Heap hp = L.heaps.get(cell); boolean has = false;
			if (hp != null) for (Item i : hp.items) if (i instanceof CeremonialCandle) has = true;
			if (has) continue;
			if (L.heroFOV[cell] && cell != h.pos && Actor.findChar(cell) == null && distance(h.pos, cell) <= 3 && line(h.pos, cell))
				return use(cc, Item.AC_THROW, cell, "quest: a candle for the ritual");
			int best = -1, bd = INF;   // beside it, off the ritual's cells
			for (int k : PathFinder.NEIGHBOURS8) { int n = cell + k; if (n < 0 || n >= L.length()) continue;
				boolean ritual = n == rp || n == rp - W || n == rp + 1 || n == rp + W || n == rp - 1;
				if (!ritual && open[n] && seenChar(n) == null && dist[n] < bd) { bd = dist[n]; best = n; } }
			return best >= 0 && best != h.pos ? new Target(best, GO, "quest: beside a ritual cell") : null;
		}
		return null;
	}

	/** The rot garden: the heart struck in melee (fire loses the seed). */
	static Target heart() {
		for (Mob m : mobs()) {
			if (!(m instanceof RotHeart) || !m.isAlive() || !(L.heroFOV[m.pos] || known[m.pos])) continue;
			if (adjacent(m.pos, Dungeon.hero.pos)) return new Target(m.pos, ENEMY, "quest: the rot heart");
			int c = AsBosses.besideStep(m.pos);
			return c >= 0 ? new Target(c, GO, "quest: to the rot heart") : null;
		}
		return null;
	}

	// =============================================================================================================
	// the Blacksmith: his mine, then his favours
	// =============================================================================================================
	static int veinTries, lastVein = -1; static final Set<Integer> badVeins = new HashSet<>(); static Level veinL;

	static int gold() { DarkGold g = Dungeon.hero.belongings.getItem(DarkGold.class); return g == null ? 0 : g.quantity(); }

	/** Is the mine done (as the bot sees it): the gold wanted, or no vein known and nothing left to explore. */
	static boolean mineDone() {
		if (gold() >= GOLD) return true;
		if (!(L instanceof MiningLevel)) return false;
		for (int c = 0; c < L.length(); c++) if (L.map[c] == Terrain.WALL_DECO && known[c] && !badVeins.contains(c) && reachVein(c) < INF) return false;
		for (int c = 0; c < L.length(); c++) if (dist[c] > 0 && dist[c] < INF && unseenBy(c)) return false;
		return true;
	}
	static int reachVein(int c) { int b = INF; for (int k : PathFinder.NEIGHBOURS8) { int n = c + k; if (n >= 0 && n < L.length() && (n == Dungeon.hero.pos || (dist[n] < b && seenChar(n) == null))) b = n == Dungeon.hero.pos ? 0 : dist[n]; } return b; }

	static Target mine() {
		if (!(L instanceof MiningLevel)) return null;
		if (veinL != L) { veinL = L; badVeins.clear(); lastVein = -1; }
		Hero h = Dungeon.hero;
		if (gold() < GOLD && h.belongings.getItem(Pickaxe.class) != null) {
			int best = -1, bd = INF;
			for (int c = 0; c < L.length(); c++) if (L.map[c] == Terrain.WALL_DECO && known[c] && !badVeins.contains(c) && L.insideMap(c)) { int r = reachVein(c); if (r < bd) { bd = r; best = c; } }
			if (best >= 0) {
				if (adjacent(best, h.pos)) {   // beside it: dig (a vein that won't go is passed over)
					if (best == lastVein && ++veinTries > 6) { badVeins.add(best); veinTries = 0; }
					if (best != lastVein) { lastVein = best; veinTries = 0; }
					return new Target(best, GO, "quest: dig the gold vein");
				}
				int n = -1, nd = INF; for (int k : PathFinder.NEIGHBOURS8) { int c = best + k; if (c >= 0 && c < L.length() && dist[c] < nd && seenChar(c) == null) { nd = dist[c]; n = c; } }
				if (n >= 0) return new Target(n, GO, "quest: to a gold vein");
			}
		}
		if (!mineDone()) return null;   // (the Assistant explores for more)
		for (LevelTransition tr : L.transitions) if (tr.type == LevelTransition.Type.BRANCH_ENTRANCE && known[tr.cell()])
			return tr.cell() == h.pos ? new Target(h.pos, PICKUP, "quest: out of the mine") : walk(tr.cell()) < INF ? new Target(tr.cell(), GO, "quest: out of the mine") : null;
		return null;
	}

	static Target smith() {
		if (L instanceof MiningLevel || Dungeon.depth < 11 || Dungeon.depth > 14 || !Blacksmith.Quest.given()) return null;
		if (!seenAt.containsKey(Blacksmith.class)) return null;
		Hero h = Dungeon.hero;
		if (!Blacksmith.Quest.started() && h.belongings.getItem(Pickaxe.class) != null) {   // his ladder down
			for (LevelTransition tr : L.transitions) if (tr.type == LevelTransition.Type.BRANCH_EXIT && (known[tr.cell()] || L.heroFOV[tr.cell()]))
				return tr.cell() == h.pos ? new Target(h.pos, PICKUP, "quest: down to the mine") : walk(tr.cell()) < INF ? new Target(tr.cell(), GO, "quest: to the mine's ladder") : null;
			return null;
		}
		if (Blacksmith.Quest.completed() && Blacksmith.Quest.rewardsAvailable() && AsChoices.smithWants() != null) return toNpc(Blacksmith.class, "quest: the Blacksmith's favours");
		return null;
	}
}
