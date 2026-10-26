package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.NPC;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.BurningTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import java.util.*;
import pebxel.Assist.Target;
import pebxel.bot.FightModel;
import static pebxel.Assist.*;

/**
 * Monsters and rooms (agent WC, work package C of strategy-coverage.md): the per-monster rules, the floor's feeling,
 * the burnt rooms. One hook (Assist.planList's skills: "foes") and a few rows in Rules' table.
 *
 *  - thieves (Thief, Bandit): one that stole (the log says what, and its description: "carries ...") is chased while
 *    in sight and hit from afar when it can be (it runs at 5/6 of the hero's pace: Thief.speed); out of sight, after it
 *    the way it ran for a while (it gets away only out of sight and 6+ cells off: Thief.Fleeing.escaped). Killed, it
 *    drops what it took. One beside the hero with others is hit first (it steals on a blow and runs);
 *  - animated statues: passive till struck, with a sure enchanted, uncursed weapon (Statue.createWeapon): fought when
 *    the fight model says the hero clearly outclasses it, left alone otherwise;
 *  - ghouls: one knocked down gets up in a few turns while its partner is near (Ghoul.die, GhoulLifeLink): finish the
 *    partner, no waiting about (a Rules row vetoes the waits);
 *  - swarms (each blow that doesn't kill splits it) and vampire bats: into a corridor before they arrive, and let them
 *    come there;
 *  - fire elementals: met standing in water (neither its blow nor its bolt sets one in water alight: Elemental);
 *  - the floor's feeling: dark (foes seen late, back sooner: rest in a corridor, not in the open), secrets (one more
 *    hidden room: the dead ends searched before the stairs), traps (the trodden way kept to on long walks);
 *  - burnt rooms (BurnedRoom: a fifth of the plain floor in the burnt patch hides a fire trap; the embers and the burnt
 *    traps are safe): searched before stepping on the plain floor there, the trodden way kept to.
 *
 * Skeletons (the bones burst beside it on death) are priced by the lookahead (FoeView: deathDmg), not moved for. The
 * pit and weak-floor rooms (the jump down into the pit room) are K8's (AsBasics).
 * Only what the player sees: foes in sight, the log, the map as shown (a hidden trap looks like plain floor and is
 * read as plain floor), where the hero has walked and searched. AS_FOES=0 turns the rules off (the counts stay).
 */
final class AsFoes {
	private AsFoes() {}

	static final boolean ON = opt("FOES", true);
	static final boolean THIEF = opt("FTHIEF", true), STATUE = opt("FSTATUE", true), GHOUL = opt("FGHOUL", true), SWARM = opt("FSWARM", true),
			FIRE = opt("FFIRE", true), DARK = opt("FDARK", true), SECRETS = opt("FSECRETS", true), TRAPS = opt("FTRAPS", true), BURNT = opt("FBURNT", true);
	static final int STATP = optInt("FSTATP", 2);      // statue: the fight model's odds of death at most (%)
	static final int STATL = optInt("FSTATL", 50);     // statue: the health it's expected to cost at most (% of the health now)
	static final int STATHP = optInt("FSTATHP", 70);   // statue: the hero's health share at least
	static final int CHASE = optInt("FCHASE", 12);     // thief: turns it's chased out of sight
	static final int SECRETN = optInt("FSECRETN", 5);  // secrets: dead ends searched a floor
	static final int BURNTN = optInt("FBURNTN", 8);    // burnt rooms: searches a floor
	static final int WAITN = optInt("FWAITN", 4);      // swarm / bat / fire elemental: turns waited for it at the spot

	static Target note(Target t) { if (t != null) why.merge(t.why.replaceAll(" \\(.*", ""), 1, Integer::sum); return t; }
	static final Map<String, Integer> why = new TreeMap<>();

	/** The hook (Assist.planList's skills): a move against the foes in sight or for the floor, or null. */
	static Target suggest(Target first) {
		try { observe(); } catch (Throwable e) { lastError = e; }
		if (!ON) return null;
		try {
			Target t = pick(first);
			if (t != null && (t.kind == GO || t.kind == PICKUP) && (Dungeon.hero.rooted || bad.containsKey(t.cell) || t.cell == Dungeon.hero.pos)) return null;
			return note(t);
		} catch (Throwable e) { lastError = e; return null; }
	}

	static Target pick(Target first) {
		Hero h = Dungeon.hero;
		if (h.rooted || inDanger || burning() || foul(h.pos) || blind()) return null;   // (out of the fire / gas first: escape's)
		if (Dungeon.bossLevel()) return null;   // (a boss's floor: AsBosses' own rules; the Dwarf King's ghouls are his)
		Target t = null;
		if (THIEF) t = thief(first);
		if (t == null && GHOUL) t = ghoul(first);
		if (t == null && FIRE) t = fireElemental(first);
		if (t == null && SWARM) t = corridor(first);
		if (t == null && DARK) t = darkRest();
		if (t == null && STATUE) t = statue(first);
		if (t == null && SECRETS) t = secrets(first);
		if (t == null && (BURNT || TRAPS)) t = trodden(first);
		return t;
	}

	// =============================================================================================================
	// what the hero has seen: the floor, the way walked, the thieves, the statues, the ghouls downed
	// =============================================================================================================
	static Level floor; static boolean[] trod; static int lastPos = -1;
	static int secretDone, burntDone;

	static final class Steal { Mob m; String what; int pos, dx, dy; float seen; boolean open = true; }
	static final Map<Integer, Steal> steals = new LinkedHashMap<>();
	static final class Down { Mob g; int pos; float at; }
	static final List<Down> downs = new ArrayList<>();
	static final Map<Mob, Integer> ghoulsSeen = new IdentityHashMap<>();
	static final Map<Mob, Boolean> statues = new IdentityHashMap<>();   // statues set on (fought yet?)
	static final Map<Integer, float[]> verdict = new HashMap<>();       // statue id -> {when, hero lvl, hp, ok}

	// the game's counts (AssistBot prints them)
	static int stolen, recovered, lost, statSeen, statTried, statFought, statWon, ghoulDowns;
	static final Set<Integer> statSeenIds = new HashSet<>();

	static void observe() {
		Hero h = Dungeon.hero; Level l = Dungeon.level; if (h == null || l == null) return;
		float now = Actor.now();
		if (l != floor || trod == null || trod.length != l.length()) {
			for (Steal s : steals.values()) if (s.open) { s.open = false; lost++; }   // (left on the floor it ran off on)
			for (Map.Entry<Mob, Boolean> e : statues.entrySet()) if (e.getValue() && !e.getKey().isAlive() && wonCounted.add(e.getKey())) statWon++;
			steals.clear(); downs.clear(); ghoulsSeen.clear(); statues.clear(); verdict.clear(); waits.clear();
			floor = l; trod = new boolean[l.length()]; lastPos = -1; secretDone = burntDone = 0;
		}
		// the way walked since the last decision (the game's own shortest way over the known map, as Hero walks it)
		int p = h.pos;
		if (lastPos >= 0 && lastPos != p && l.distance(lastPos, p) > 1 && l.distance(lastPos, p) < 30) {
			boolean[] m = new boolean[l.length()];
			for (int i = 0; i < m.length; i++) m[i] = l.passable[i] && (l.visited[i] || l.mapped[i]);
			List<Integer> w = gamePath(lastPos, p, m);
			if (w != null) for (int c : w) trod[c] = true;
		}
		trod[p] = true; lastPos = p;
		// thieves: one in sight carrying what it took
		for (Mob m : mobs()) {
			if (!(m instanceof Thief) || !m.isAlive()) continue;
			Item it = ((Thief) m).item;
			boolean vis = l.heroFOV[m.pos] && m.invisible <= 0;
			if (it == null || !vis || m.alignment != com.shatteredpixel.shatteredpixeldungeon.actors.Char.Alignment.ENEMY) continue;
			Steal s = steals.get(m.id());
			if (s == null) { s = new Steal(); s.m = m; s.what = it.name(); s.pos = m.pos; steals.put(m.id(), s); stolen++; }
			if (s.pos != m.pos) { s.dx = Integer.signum(l.cellToPoint(m.pos).x - l.cellToPoint(s.pos).x); s.dy = Integer.signum(l.cellToPoint(m.pos).y - l.cellToPoint(s.pos).y); }
			s.pos = m.pos; s.seen = now;
		}
		for (Steal s : steals.values()) {
			if (!s.open) continue;
			if (!s.m.isAlive()) { s.open = false; recovered++; }                      // (killed: it dropped it)
			else if (((Thief) s.m).item == null) { s.open = false; lost++; }          // ("the thief escapes with ...")
		}
		// statues: seen, set on, fought, beaten
		for (Mob m : mobs()) if (m instanceof Statue && m.isAlive() && l.heroFOV[m.pos] && m.state == m.PASSIVE && statSeenIds.add(m.id())) statSeen++;
		for (Map.Entry<Mob, Boolean> e : statues.entrySet()) {
			Mob m = e.getKey();
			if (!e.getValue() && m.state != m.PASSIVE) { e.setValue(true); statFought++; }
			if (e.getValue() && !m.isAlive() && !wonCounted.contains(m)) { wonCounted.add(m); statWon++; }
		}
		// ghouls: one in sight last time, gone from the floor's foes now but not dead (its body lies there, crumpled)
		List<Mob> ms = mobs();
		Set<Mob> now0 = Collections.newSetFromMap(new IdentityHashMap<>()); now0.addAll(ms);
		for (Map.Entry<Mob, Integer> e : ghoulsSeen.entrySet()) {
			Mob g = e.getKey();
			if (!now0.contains(g) && g.isAlive()) { boolean had = false; for (Down d : downs) had |= d.g == g; if (!had) { Down d = new Down(); d.g = g; d.pos = e.getValue(); d.at = now; downs.add(d); ghoulDowns++; } }
		}
		downs.removeIf(d -> !d.g.isAlive() || now0.contains(d.g) || now - d.at > 30);   // (dead for good, up again, or long ago)
		ghoulsSeen.clear();
		for (Mob m : ms) if (m instanceof Ghoul && m.isAlive() && l.heroFOV[m.pos]) ghoulsSeen.put(m, m.pos);
	}
	static final Set<Mob> wonCounted = Collections.newSetFromMap(new IdentityHashMap<>());

	static String stats() {
		for (Map.Entry<Mob, Boolean> e : statues.entrySet()) if (e.getValue() && !e.getKey().isAlive() && !wonCounted.contains(e.getKey())) statWon++;
		int open = 0; for (Steal s : steals.values()) if (s.open) open++;
		String r = String.format("stolen %d recovered %d lost %d (carried at the end %d) statues seen %d tried %d fought %d won %d ghoul downs %d by %s verdicts%s",
				stolen, recovered, lost, open, statSeen, statTried, statFought, statWon, ghoulDowns, why, verdicts);
		verdicts = new StringBuilder();
		stolen = recovered = lost = statSeen = statTried = statFought = statWon = ghoulDowns = 0;
		why.clear(); statSeenIds.clear(); wonCounted.clear(); steals.clear(); statues.clear(); downs.clear(); ghoulsSeen.clear(); verdict.clear(); waits.clear(); floor = null; trod = null;
		return r;
	}

	// =============================================================================================================
	// helpers
	// =============================================================================================================
	static boolean awake(Mob m) { return enemy(m) && m.state != m.SLEEPING && m.state != m.PASSIVE && !(m instanceof NPC) && !Dungeon.hero.isCharmedBy(m); }
	/** An awake foe beside the hero other than x. */
	static boolean adjOther(Mob x) { int hp = Dungeon.hero.pos; for (Mob m : mobs()) if (m != x && awake(m) && adjacent(m.pos, hp)) return true; return false; }
	/** No awake foe in sight (a statue, passive, doesn't count), none lately coming, the floor not locked. */
	static boolean quiet() {
		if (Brain.fight || threatNear || L.locked) return false;
		for (Mob m : mobs()) if (enemy(m) && m.state != m.PASSIVE && !(m instanceof NPC) && !hiddenMimic(m)) return false;
		return true;
	}
	static boolean healthy(int pct) { Hero h = Dungeon.hero; return h.HP * 100 >= h.HT * pct; }
	/** Open cells round c (the ways a foe can stand beside it). */
	static int openRound(int c) { int o = 0; for (int k = 0; k < 8; k++) { int n = c + N8[k]; if (nb(c, n) && L.passable[n] && !L.avoid[n]) o++; } return o; }
	static boolean narrow(int c) { return openRound(c) <= 2; }
	static boolean freeCell(int c) { return c >= 0 && c < L.length() && open[c] && known[c] && seenChar(c) == null && !danger[c] && !bad.containsKey(c) && walk(c) < INF; }
	static Target enemyAt(Mob m, String w) { Target t = new Target(m.pos, ENEMY, w); t.obj = -1; return t; }
	static Target waitFor(Mob m, String w) { Target t = new Target(m.pos, ENEMY, w); t.action = WAIT_ACT; t.obj = -1; return t; }
	/** Waits at one spot for one foe, counted (it may never come). */
	static final Map<String, Integer> waits = new HashMap<>();
	static boolean mayWait(Mob m) { String k = m.id() + "@" + Dungeon.hero.pos; int n = waits.merge(k, 1, Integer::sum); return n <= WAITN; }

	/** From afar at m: the bow, a thrown weapon, a charged attack wand (in line, 2+ cells off). */
	static Target rangedAt(Mob m, String w) {
		Hero h = Dungeon.hero; int d = distance(h.pos, m.pos);
		if (d < 2 || !line(h.pos, m.pos)) return null;
		SpiritBow bow = bag(SpiritBow.class);
		if (bow != null) return use(bow, SpiritBow.AC_SHOOT, m.pos, w);
		for (Item i : h.belongings.backpack) if (i instanceof MissileWeapon && !i.cursed && !declined(i.getClass(), Item.AC_THROW)) return use(i, Item.AC_THROW, m.pos, w);
		for (Item i : h.belongings.backpack) if (i instanceof Wand && charged((Wand) i, i) && attackWand(i.getClass(), d)) return use(i, Wand.AC_ZAP, m.pos, w);
		return null;
	}

	// =============================================================================================================
	// thieves
	// =============================================================================================================
	static Target thief(Target first) {
		Hero h = Dungeon.hero; int hp = h.pos;
		// one beside the hero with another: hit the thief first (it steals on a blow and runs)
		if (first != null && first.kind == ENEMY && first.action == null) {
			Mob at = null; for (Mob m : mobs()) if (m.pos == first.cell) at = m;
			if (!(at instanceof Thief)) for (Mob m : mobs()) if (m instanceof Thief && awake(m) && ((Thief) m).item == null && adjacent(m.pos, hp) && at != null && adjacent(at.pos, hp) && !boss(at))
				return enemyAt(m, "thief: hit it first (it steals and runs)");
		}
		Steal best = null;
		for (Steal s : steals.values()) if (s.open && (best == null || distance(s.pos, hp) < distance(best.pos, hp))) best = s;
		if (best == null) return null;
		if (!healthy(30) || adjOther(best.m)) return null;   // (what's beside the hero first; too hurt to run after anything)
		Mob m = best.m;
		boolean vis = L.heroFOV[m.pos] && m.invisible <= 0 && m.isAlive();
		if (vis) {
			if (adjacent(m.pos, hp)) { Target t = enemyAt(m, "thief: hit it (it has your " + best.what + ")"); t.hint = HINT; return t; }
			Target r = rangedAt(m, "thief: shoot it (it has your " + best.what + ")");
			if (r != null) { r.hint = HINT; return r; }
			if (walk(m.pos) >= INF) return null;
			Target t = enemyAt(m, "thief: after it (it has your " + best.what + ")"); t.hint = HINT; return t;
		}
		if (Actor.now() - best.seen > CHASE) return null;
		// out of sight: on the way it ran, from where it was last seen (it gets away only 6+ cells from where it last
		// saw the hero: keep close). Reached and still not in sight: it ran on that way (the guess moved on).
		int P = ahead(best.pos, best.dx, best.dy, 5);
		if (distance(P, hp) <= 1) { best.pos = P; P = ahead(P, best.dx, best.dy, 5); }
		int g = -1, gs = INF;
		for (int c = 0; c < L.length(); c++) {
			if (!known[c] || !L.passable[c] || walk(c) >= INF || walk(c) > 20 || distance(c, hp) < 2 || bad.containsKey(c)) continue;
			int sc = 3 * distance(c, P) + walk(c);
			if (sc < gs) { gs = sc; g = c; }
		}
		if (g < 0) return null;
		Target t = new Target(g, GO, "thief: after it (seen running this way with your " + best.what + ")");
		t.hint = HINT; return t;
	}
	static final double HINT = optInt("FHINT", 20) / 100.0;   // a stolen item got back, in floors (the brain's currency): the chase over a plain walk
	/** From c on along (dx, dy), up to n cells over the known open map. */
	static int ahead(int c, int dx, int dy, int n) {
		int W = L.width();
		if (dx == 0 && dy == 0) return c;
		for (int k = 0; k < n; k++) { int m = c + dx + dy * W; if (m < 0 || m >= L.length() || !nb(c, m) || !L.passable[m] || !known[m]) break; c = m; }
		return c;
	}

	// =============================================================================================================
	// ghouls
	// =============================================================================================================
	/** The partner of a ghoul lying down: the nearest awake ghoul in sight within 4 of it (Ghoul.searchForHost). */
	static Mob partner() {
		Mob best = null; int bd = INF, hp = Dungeon.hero.pos;
		for (Down d : downs) for (Mob m : mobs()) if (m instanceof Ghoul && awake(m) && distance(m.pos, d.pos) <= 4 && distance(m.pos, hp) < bd) { bd = distance(m.pos, hp); best = m; }
		return best;
	}
	static Target ghoul(Target first) {
		if (downs.isEmpty() || !healthy(35)) return null;
		Mob p = partner(); if (p == null) return null;
		if (first != null && first.kind == ENEMY && first.action == null && first.cell == p.pos) return null;   // (on it already)
		if (!adjacent(p.pos, Dungeon.hero.pos) && (walk(p.pos) > 5 || adjOther(p))) return null;
		return enemyAt(p, "ghouls: finish the partner (the one down gets up in a few turns)");
	}
	/** Rules: no waiting or resting while a ghoul lies down with its partner in sight. */
	static boolean ghoulWait(Brain.Cand c) {
		if (!ON || !GHOUL || downs.isEmpty() || c.t == null) return false;
		boolean waits = (c.t.kind == WAIT) || (c.t.kind == ENEMY && WAIT_ACT.equals(c.t.action));
		return waits && partner() != null && healthy(35);
	}

	// =============================================================================================================
	// fire elementals: in the water
	// =============================================================================================================
	static Target fireElemental(Target first) {
		Hero h = Dungeon.hero; int hp = h.pos;
		Mob e = null;
		for (Mob m : mobs()) if (m instanceof Elemental.FireElemental && awake(m) && hunting(m) && (e == null || distance(m.pos, hp) < distance(e.pos, hp))) e = m;
		if (e == null || h.flying) return null;
		int d = distance(e.pos, hp);
		if (water(hp)) {   // in the water: let it come (its blows can't set the hero alight here)
			if (d >= 2 && !adjOther(null) && mayWait(e)) return waitFor(e, "fire elemental: wait for it in the water");
			return null;
		}
		if (d < 2 && adjOther(e)) return null;
		int best = -1, bk = INF;
		for (int c = 0; c < L.length(); c++) {
			if (!water(c) || !freeCell(c) || walk(c) > 3 || walk(c) >= bk) continue;
			if (distance(e.pos, c) < walk(c) && d >= 2) continue;   // (it gets there first)
			boolean other = false; for (Mob m : mobs()) if (m != e && awake(m) && distance(m.pos, c) <= 1) other = true;
			if (other) continue;
			bk = walk(c); best = c;
		}
		return best >= 0 ? new Target(best, GO, "fire elemental: into the water (it can't set you alight there)") : null;
	}

	// =============================================================================================================
	// swarms and bats: a corridor
	// =============================================================================================================
	static Target corridor(Target first) {
		Hero h = Dungeon.hero; int hp = h.pos;
		Mob f = null;
		for (Mob m : mobs()) if ((m instanceof Swarm || m instanceof Bat) && awake(m) && hunting(m) && distance(m.pos, hp) <= 7 && (f == null || distance(m.pos, hp) < distance(f.pos, hp))) f = m;
		if (f == null) return null;
		for (Mob m : mobs()) if (awake(m) && adjacent(m.pos, hp)) return null;   // (one beside: the fight)
		for (Mob m : mobs()) if (awake(m) && FoeView_shooter(m) && line(m.pos, hp)) return null;   // (a shooter in line: the tactics')
		String name = f instanceof Swarm ? "swarm" : "bat";
		if (narrow(hp)) return mayWait(f) ? waitFor(f, name + ": let it come into the corridor") : null;
		double sp = Math.max(1, f.speed());
		int best = -1, bk = INF;
		for (int c = 0; c < L.length(); c++) {
			if (!narrow(c) || !freeCell(c) || walk(c) > 4 || walk(c) >= bk || water(c) || c == L.exit() || c == L.entrance()) continue;
			if (distance(f.pos, c) <= walk(c) * sp) continue;   // (it's there first)
			bk = walk(c); best = c;
		}
		return best >= 0 ? new Target(best, GO, name + ": into a corridor (" + (f instanceof Swarm ? "it splits on every blow" : "fewer at a time") + ")") : null;
	}
	static boolean FoeView_shooter(Mob m) { return pebxel.bot.FoeView.shooter(m); }

	// =============================================================================================================
	// statues
	// =============================================================================================================
	static Target statue(Target first) {
		Hero h = Dungeon.hero;
		if (!quiet() || (first != null && first.kind == ENEMY) || starving() || !healthy(STATHP)) return null;
		Mob best = null; int bw = INF;
		for (Mob m : mobs()) {
			if (!(m instanceof Statue) || !m.isAlive() || m.state != m.PASSIVE || !L.heroFOV[m.pos] || walk(m.pos) >= INF || walk(m.pos) > 30 || bad.containsKey(m.pos)) continue;
			if (!outclassed((Statue) m)) continue;
			if (walk(m.pos) < bw) { bw = walk(m.pos); best = m; }
		}
		if (best == null) return null;
		if (!statues.containsKey(best)) { statues.put(best, false); statTried++; }
		Statue s = (Statue) best;
		String w = s.weapon() == null ? "" : " (" + s.weapon().name() + ")";
		return enemyAt(best, "statue: fight it for its enchanted weapon" + w);
	}
	/** The fight model's verdict: the hero clearly outclasses it (kept while the hero's level and health stand). */
	static StringBuilder verdicts = new StringBuilder();
	static boolean outclassed(Statue m) {
		Hero h = Dungeon.hero; float now = Actor.now();
		float[] v = verdict.get(m.id());
		if (v != null && v[1] == h.lvl && Math.abs(v[2] - h.HP) < h.HT / 10f && now - v[0] < 200) return v[3] > 0;
		FightModel.Result r = FightModel.evaluate(h, Collections.singletonList(m));
		boolean ok = r.pDeath * 100 <= STATP && r.lossMean * 100 <= h.HP * STATL;
		if (v == null) verdicts.append(String.format(" d%d:%s/%.2f/%.0f-of-%d", Dungeon.depth, ok ? "yes" : "no", r.pDeath, r.lossMean, h.HP));
		verdict.put(m.id(), new float[]{ now, h.lvl, h.HP, ok ? 1 : 0 });
		return ok;
	}

	// =============================================================================================================
	// the floor's feeling
	// =============================================================================================================
	static Level.Feeling feeling() { return Dungeon.level == null ? Level.Feeling.NONE : Dungeon.level.feeling; }

	// ---- dark: rest in a corridor (foes are seen late; they come back sooner: Level.respawnCooldown) ----
	static int restVetoPos = -1; static float restVetoAt = -1;
	/** Rules: a rest in the open on a dark floor, a corridor a few steps off. */
	static boolean restOpen(Brain.Cand c) {
		if (!ON || !DARK || c.role != Brain.Role.REST || feeling() != Level.Feeling.DARK || Dungeon.bossLevel()) return false;
		int hp = Dungeon.hero.pos;
		if (narrow(hp) || restSpot() < 0) return false;
		restVetoPos = hp; restVetoAt = Actor.now();
		return true;
	}
	static int restSpot() {
		int best = -1, bk = INF;
		for (int c = 0; c < L.length(); c++) if (narrow(c) && freeCell(c) && walk(c) <= 6 && walk(c) < bk && !water(c) && c != L.exit() && c != L.entrance() && L.traps.get(c) == null) { bk = walk(c); best = c; }
		return best;
	}
	static Target darkRest() {
		Hero h = Dungeon.hero;
		if (restVetoPos != h.pos || restVetoAt != Actor.now() || !quiet()) return null;
		int c = restSpot();
		return c >= 0 ? new Target(c, GO, "dark floor: rest in a corridor (foes are seen late here)") : null;
	}

	// ---- secrets: the dead ends searched before the stairs ----
	static Target secrets(Target first) {
		if (feeling() != Level.Feeling.SECRETS || secretDone >= SECRETN || first == null || !"stairs".equals(first.why) || !quiet() || hungry()) return null;
		int hp = Dungeon.hero.pos;
		if (deadEnd(hp)) { secretDone++; Target t = new Target(hp, WAIT, "secrets floor: search the dead end"); t.action = SEARCH_ACT; return t; }
		int best = -1, bk = INF;
		for (int c = 0; c < L.length(); c++) if (walk(c) <= 20 && walk(c) < bk && deadEnd(c) && !bad.containsKey(c)) { bk = walk(c); best = c; }
		return best >= 0 ? new Target(best, GO, "secrets floor: to a dead end to search (a hidden room more here)") : null;
	}

	// ---- traps / burnt rooms: the trodden way, a search before the burnt patch's plain floor ----
	/** A cell that looks like plain floor (a hidden trap looks so too: both read the same) the hero hasn't stood on or searched. */
	static boolean untried(int c) {
		int t = L.map[c];
		return (t == Terrain.EMPTY || t == Terrain.SECRET_TRAP || t == Terrain.EMPTY_DECO) && !trod[c] && !(checked != null && checked[c]);
	}
	/** In a burnt patch (BurnedRoom): burnt-out fire traps or shown ones, embers about. */
	static boolean burnt(int c) {
		int traps = 0, em = 0, W = L.width();
		for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) {
			int n = c + dx + dy * W; if (n < 0 || n >= L.length() || Math.abs(n % W - c % W) > 2 || !known[n]) continue;
			int t = L.map[n];
			if (t == Terrain.EMBERS) em++;
			if (t == Terrain.TRAP || t == Terrain.INACTIVE_TRAP) { Trap tr = L.traps.get(n); if (tr instanceof BurningTrap && tr.visible) traps++; }
		}
		return traps >= 1 && traps + em >= 3;
	}
	static boolean suspect(int c) {
		if (!untried(c)) return false;
		if (TRAPS && feeling() == Level.Feeling.TRAPS) return true;
		return BURNT && burnt(c);
	}
	static Target trodden(Target first) {
		if (first == null || (first.kind != GO && first.kind != PICKUP) || first.cell < 0 || !quiet()) return null;
		Hero h = Dungeon.hero; int hp = h.pos, to = first.cell;
		boolean[] p = new boolean[L.length()];
		for (int i = 0; i < p.length; i++) p[i] = L.passable[i] && (L.visited[i] || L.mapped[i]);
		for (Mob m : mobs()) if (m.pos >= 0 && L.heroFOV[m.pos]) p[m.pos] = false;
		List<Integer> way = gamePath(hp, to, p);
		if (way == null) return null;
		int first0 = -1; for (int i = 0; i < way.size(); i++) if (way.get(i) != to && suspect(way.get(i))) { first0 = i; break; }
		if (first0 < 0) return null;
		// a way round over trodden ground, not much longer: walked to its farthest point the game's own walk takes clean
		List<Integer> mine = cleanWay(hp, to, p, way.size() + 4);
		if (mine != null) {
			for (int i = mine.size() - 1; i >= 1; i -= Math.max(1, (i + 1) / 3)) {
				int w = mine.get(i);
				List<Integer> g = gamePath(hp, w, p); if (g == null) continue;
				boolean clean = true; for (int c : g) if (c != to && suspect(c)) { clean = false; break; }
				if (clean && w != hp) return new Target(w, GO, feeling() == Level.Feeling.TRAPS ? "traps floor: the trodden way" : "burnt room: round the burnt floor");
			}
		}
		// none: a search before the first step onto it (burnt patches only: a trap floor is all such ground)
		if (!BURNT || !burnt(way.get(first0)) || burntDone >= BURNTN || hungry()) return null;
		if (first0 == 0) { burntDone++; Target t = new Target(hp, WAIT, "burnt room: search before stepping on (fire traps hide in the plain floor)"); t.action = SEARCH_ACT; return t; }
		return new Target(way.get(first0 - 1), GO, "burnt room: up to the burnt floor, then search");
	}
	/** The shortest way from a to b avoiding untried cells (except b), at most max long, or null. */
	static List<Integer> cleanWay(int a, int b, boolean[] p, int max) {
		int n = L.length(); int[] d = new int[n]; Arrays.fill(d, Integer.MAX_VALUE); int[] q = new int[n]; int qh = 0, qt = 0;
		d[a] = 0; q[qt++] = a;
		while (qh < qt) {
			int c = q[qh++]; if (c == b) break; if (d[c] >= max) continue;
			for (int k = 0; k < 8; k++) { int m = c + N8[k]; if (!nb(c, m) || d[m] != Integer.MAX_VALUE || !p[m] || (m != b && suspect(m))) continue; d[m] = d[c] + 1; q[qt++] = m; }
		}
		if (d[b] == Integer.MAX_VALUE) return null;
		LinkedList<Integer> r = new LinkedList<>();
		for (int c = b; c != a; ) { r.addFirst(c); int nx = -1; for (int k = 0; k < 8; k++) { int m = c + N8[k]; if (nb(c, m) && d[m] == d[c] - 1) { nx = m; break; } } if (nx < 0) return null; c = nx; }
		return new ArrayList<>(r);
	}
}
