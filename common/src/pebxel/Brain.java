package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.NPC;
import com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClassArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfInvisibility;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTeleportation;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTerror;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import java.util.*;
import pebxel.Assist.Target;
import pebxel.bot.*;

/**
 * The brain (agent BR): where the Assistant's list is arbitrated. Every rule that has something to say proposes an
 * entry (the skills' hooks, the hands, escape, tactics: {@link #offer}); they are placed as they always were (the
 * legacy order: Assist.planList), and then the brain weighs the ones it can value in one currency, the floors the run
 * is expected to go on for (pebxel.bot.Budget):
 *
 *   value = - P(death) x the floors ahead  - the rescue items spent (what each is worth kept: Budget.rescue)  + ...
 *
 * In a fight the odds come from the lookahead (pebxel.bot.Look: the fight played out from each option on the same
 * random numbers): fight on (drinking a known Healing below 35% when it comes to that), drink now, read / drink a
 * known escape, walk to the stairs up (rest there, come back whole) or down, put on other gear. Plan A changes only
 * when the brain is sure: another option better than plan A by {@link #MARGIN} floors. Then the situational rules
 * ({@link Rules}: a table of conditions and boosts / vetoes / proposals) have their say.
 *
 * The list keeps its shape (entry 0 plan A, at most 8; passes and demotions as before): an entry the brain puts off
 * stays in the list for a Down. AS_BRAIN=0: the legacy order, the brain only counts where it would differ (the
 * agreement rate: {@link #stats}).
 *
 * Plugging in: a skill hook returns its Target as before; it may set {@code t.hint} (its value in floors, relative to
 * carrying on) and the brain weighs it with the rest; or add a row to Rules' table.
 */
final class Brain {
	private Brain() {}

	static final boolean ON = Assist.opt("BRAIN", true);
	static final double MARGIN = Assist.optInt("BMARGIN", 10) / 100.0;   // floors: how much better an option must be to change plan A
	static final double LATER = Assist.optInt("BLATER", 50) / 100.0;     // a foe escaped from: the share of the time it's met again (whole)
	static final double HPW = Assist.optInt("BHPW", 5) / 100.0;          // a full health bar after the fight, in floors (the rest it saves)
	static final double ESCEFF = Assist.optInt("BESCEFF", 80) / 100.0;   // an escape item's worth kept, next to Healing's
	static final boolean FIGHTV = Assist.opt("BFIGHT", true), STAIRSV = Assist.opt("BSTAIRS", true), GEARV = Assist.opt("BGEAR", true);

	/** What a candidate is, for the brain. */
	enum Role { CARRY, HEAL, ESCAPE, UP, DOWN, EQUIP, REST, RANGED, OTHER }

	static final class Cand {
		final Target t; final String src; final Role role; double v = Double.NaN; boolean veto; String note = "";
		Cand(Target t, String src, Role role) { this.t = t; this.src = src; this.role = role; }
		boolean valued() { return !Double.isNaN(v); }
		@Override public String toString() { return src + ":" + role + (valued() ? String.format("%+.2f", v) : "") + (veto ? " VETO" : "") + " " + t; }
	}

	// ---- per decision ----
	static Hero H; static Target first;
	static final List<Cand> offers = new ArrayList<>();
	static List<Mob> foes = new ArrayList<>(); static Fighter me; static List<Fighter> fs = new ArrayList<>();
	static long beginNanos;
	static double R; static long seed; static Look.Out base; static boolean fight;
	static String lastWhy = "";
	static Role lastRole; static float lastAt = -99;   // the brain's last change of plan A (Rules: keep going)

	// ---- stats (per game: AssistBot prints them) ----
	static int decisions, fights, overrides, vetoes, slow; static long nanos, maxNanos;
	static final Map<String, Integer> overBy = new TreeMap<>();

	/** A new list: the base list's plan A so far. */
	static void begin(Target planA) {
		first = planA; offers.clear(); H = Dungeon.hero;
		if (Dungeon.depth != lastDepth && lastDepth >= 0 && (lastPlanRole == Role.UP || lastPlanRole == Role.DOWN)) { fledFrom = lastDepth; fledAt = Actor.now(); }
		lastDepth = Dungeon.depth;
		try { Rules.observe(); } catch (Throwable e) { Assist.lastError = e; }
		long t0 = System.nanoTime();
		try { setupFight(); } catch (Throwable e) { Assist.lastError = e; fight = false; base = null; }
		beginNanos = System.nanoTime() - t0;
	}

	/** A proposal from a rule (returned as is, for the legacy placement), screened by the table's vetoes: a vetoed one
	 *  is not pinned (AS_BRAIN=0: counted only). */
	static Target offer(String src, Target t) {
		if (t == null) return null;
		Cand c = new Cand(t, src, role(t)); offers.add(c);
		return screened(c) ? null : t;
	}

	/** The hands' suggestion, screened: one the table vetoes is passed over for the hands' next (it is declined for the
	 *  moment, then not: the hands give one suggestion, and a vetoed swap must not keep a rest from being offered). */
	static Target hands(Target planA) {
		Target t = Assist.hands(planA);
		for (int k = 0; k < 3 && t != null; k++) {
			Cand c = new Cand(t, "hands", role(t)); offers.add(c);
			if (!screened(c)) return t;
			if (t.item == null && t.action == null) return null;
			String key = Assist.dk(t.item == null ? null : t.item.getClass(), t.action); Integer was = Assist.declined.get(key);
			Assist.declined.put(key, Assist.situation(t.item == null ? null : t.item.getClass(), t.action));
			try { t = Assist.hands(planA); } finally { if (was == null) Assist.declined.remove(key); else Assist.declined.put(key, was); }
		}
		return t;
	}

	/** Is c vetoed (and the brain on)? */
	static boolean screened(Cand c) {
		try {
			if (!c.valued()) value(c);
			if (!Rules.vetoed(c)) return false;
			vetoes++;
			return ON;
		} catch (Throwable e) { Assist.lastError = e; return false; }
	}

	static Role role(Target t) {
		if (t == null) return Role.OTHER;
		if (t.kind == Assist.USE && t.item instanceof Potion && Potion.AC_DRINK.equals(t.action) && Clues.p(t.item, PotionOfHealing.class) >= 0.9) return Role.HEAL;   // (ID: by what the hero knows or believes, never an unknown potion's true kind)
		if (t.kind == Assist.USE && escapeItem(t.item) && (Potion.AC_DRINK.equals(t.action) || Scroll.AC_READ.equals(t.action))) return Role.ESCAPE;
		if (t.kind == Assist.USE && EquipableItem.AC_EQUIP.equals(t.action)) return Role.EQUIP;
		if (t.kind == Assist.WAIT && Assist.REST_ACT.equals(t.action)) return Role.REST;
		if (t.kind == Assist.USE && t.why != null && t.why.startsWith("brain: from afar")) return Role.RANGED;
		if ((t.kind == Assist.GO || t.kind == Assist.PICKUP) && t.why.startsWith("escape: up")) return Role.UP;
		if ((t.kind == Assist.GO || t.kind == Assist.PICKUP) && t.why.startsWith("escape: down")) return Role.DOWN;
		return Role.CARRY;
	}
	static boolean escapeItem(Item i) { return (i instanceof ScrollOfTeleportation || i instanceof PotionOfInvisibility || i instanceof ScrollOfTerror) && Assist.known(i); }

	// =============================================================================================================
	// the judgement (after the legacy placement: Assist.out is the list as the rules made it)
	// =============================================================================================================

	static void judge() {
		long t0 = System.nanoTime();
		try {
			judge0();
		} catch (Throwable e) { Assist.lastError = e; }
		try { if (ON && DITHER) steady(); } catch (Throwable e) { Assist.lastError = e; }
		long dt = System.nanoTime() - t0 + beginNanos; nanos += dt;
		if (DBG && dt > 20_000_000L) System.out.println("  BRAIN slow " + dt / 1000000 + " ms (begin " + beginNanos / 1000000 + ") d" + Dungeon.depth); maxNanos = Math.max(maxNanos, dt); if (dt > 20_000_000L) slow++;
	}

	static void judge0() {
		List<Target> out = Assist.out;
		decisions++;
		if (out.isEmpty()) return;
		Target legacyA = out.get(0);
		// the candidates: the list's entries (their sources where a rule proposed them), plus the brain's own in a fight
		List<Cand> cs = new ArrayList<>();
		for (Target t : out) { Cand c = null; for (Cand o : offers) if (o.t == t) c = o; cs.add(c != null ? c : new Cand(t, "list", role(t))); }
		if (fight && FIGHTV) own(cs);
		if (!fight && SKIPV) try { skip(cs); } catch (Throwable e) { Assist.lastError = e; }
		// the values, then the table's rows
		for (Cand c : cs) if (!c.valued()) value(c);
		Rules.apply(cs, legacyA);
		for (Cand c : cs) if (!c.valued() && !Double.isNaN(c.t.hint)) c.v = c.t.hint;
		// plan A: the best option when it beats plan A by the margin (carrying on, an entry with no value of its own, is 0)
		Cand a = cs.get(0), best = null;
		double va = a.veto ? Double.NEGATIVE_INFINITY : a.valued() ? a.v : 0, vb = Double.NEGATIVE_INFINITY;
		for (Cand c : cs) {
			if (c == a || c.veto) continue;
			double v = c.valued() ? c.v : c.role == Role.CARRY && (a.valued() || a.veto) ? 0 : Double.NaN;   // (carrying on: only against a plan A that has a value of its own)
			if (!Double.isNaN(v) && v > vb) { vb = v; best = c; }
		}
		Cand pick = best != null && (vb - va > MARGIN || a.veto) ? best : null;
		if (DBG && (fight || pick != null)) dump(cs, pick);
		if (pick == null) { tally(a.t); return; }
		String key = pick.role + "<" + a.role + (a.veto ? " veto" : "");
		lastWhy = key + String.format(" %+.2f/%+.2f", vb, va);
		overBy.merge(key, 1, Integer::sum); overrides++;
		if (!ON) { tally(a.t); return; }
		if (!out.contains(pick.t)) { if (out.size() >= Assist.CAP) out.remove(out.size() - 1); out.add(0, pick.t); }
		else { out.remove(pick.t); out.add(0, pick.t); }
		if (a.veto && out.size() > 1) { out.remove(a.t); out.add(a.t); }   // (vetoed: last, still there for a Down)
		if (pick.role != Role.CARRY) pick.t.why = pick.t.why + " [brain " + String.format("%+.2f", vb - Math.max(va, -99)) + "]";
		lastRole = pick.role; lastAt = Actor.now();
		tally(out.get(0));
	}

	/** The fight in sight, as the fight model sees it (Assist.oFight's choice of foes). */
	static void setupFight() {
		foes = new ArrayList<>(); fs = new ArrayList<>(); fight = false; base = null;
		Hero h = H; int hp = h.pos;
		com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Healing hb = h.buff(com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Healing.class);
		Object hl = hb == null ? null : Assist.field(hb, "healingLeft"); healing = hl instanceof Integer ? (Integer) hl : 0;
		R = Budget.ahead(h);
		for (Mob m : Assist.mobs()) if (Assist.enemy(m) && m.state != m.PASSIVE && !(m instanceof NPC) && (Assist.adjacent(m.pos, hp) || (Assist.hunting(m) && Assist.walk(m.pos) <= 8 && !(m instanceof Piranha)) || (FoeView.shooter(m) && m.state != m.SLEEPING && Assist.distance(m.pos, hp) <= 8 && (Assist.line(m.pos, hp) || (m instanceof Eye && Assist.sight(m.pos, hp)))))) foes.add(m);   // (an eye's gaze goes through whoever stands between: BR-a3, a Mage killed by the eye behind the one he fought)
		if (foes.isEmpty()) return;
		me = HeroView.of(h);
		for (Mob m : foes) fs.add(FoeView.of(m, h));
		R = Budget.ahead(h);
		seed = 0x51ED ^ (Dungeon.depth * 1000003L) ^ (hp * 7919L) ^ ((long) Actor.now() * 31L);
		policy = me.heals > 0 ? FightModel.healAt(h, foes) : 0;   // (the drinking policy the hands follow: MT's, FightModel.healAt)
		base = Look.run(me, fs, plan(), seed);
		fight = true; fights++;
	}

	static double policy;
	/** The hero's way on after his first move: the hands' drinking policy. */
	static Look.Plan plan() { return new Look.Plan().healAt(policy).healing(healing); }
	static int healing;

	/** The brain's own options in a fight, when the rules didn't offer them: drink Healing, a known escape, the stairs. */
	static void own(List<Cand> cs) {
		Hero h = H;
		Item heal = Clues.healing();   // (ID: a known Healing, else an unknown one believed Healing)
		if (heal != null && !has(cs, Role.HEAL) && !Assist.declined(PotionOfHealing.class, Potion.AC_DRINK)) cs.add(new Cand(Assist.use(heal, Potion.AC_DRINK, -1, "brain: heal"), "brain", Role.HEAL));
		if (!has(cs, Role.ESCAPE) && !Assist.blind()) for (Class<? extends Item> k : Arrays.asList(ScrollOfTeleportation.class, PotionOfInvisibility.class, ScrollOfTerror.class)) {
			Item i = h.belongings.getItem(k);
			if (i != null && Assist.known(i) && !Assist.declined(k, i instanceof Potion ? Potion.AC_DRINK : Scroll.AC_READ)) { cs.add(new Cand(Assist.use(i, i instanceof Potion ? Potion.AC_DRINK : Scroll.AC_READ, -1, "brain: escape"), "brain", Role.ESCAPE)); break; }
		}
		if (RANGEDV && !Assist.blind()) afar(cs);
		if (STAIRSV && !Dungeon.bossLevel() && !Assist.L.locked && Assist.bag(com.shatteredpixel.shatteredpixeldungeon.items.Amulet.class) == null) {
			int ent = Assist.L.entrance(), ex = Assist.L.exit();
			if (Dungeon.depth > 1 && !has(cs, Role.UP) && stairsOk(ent)) cs.add(new Cand(new Target(ent, ent == h.pos ? Assist.PICKUP : Assist.GO, "escape: up the stairs (brain)"), "brain", Role.UP));
			if (!has(cs, Role.DOWN) && stairsOk(ex) && Assist.L.map[ex] != Terrain.LOCKED_EXIT && Dungeon.depth % 5 != 4) cs.add(new Cand(new Target(ex, ex == h.pos ? Assist.PICKUP : Assist.GO, "escape: down the stairs (brain)"), "brain", Role.DOWN));
		}
	}
	// (M2) descend or explore: the rest of a floor is worth its finds (food, XP, items: BLOOT floors for a whole floor,
	// by the share still unexplored) and costs its danger: a foe in sight, awake, not yet after the hero, that the fight
	// model says would likely kill him (met again BMEET of the time if he stays). When the danger outweighs the finds,
	// the stairs down (known, a short walk, not past the foe; not the boss's) are offered with that value. The food
	// side (short of food: on down) is K6's AsBasics.hurry.
	static final boolean SKIPV = Assist.opt("BSKIP", true);
	static final double LOOT = Assist.optInt("BLOOT", 60) / 100.0, MEET = Assist.optInt("BMEET", 50) / 100.0, SKIPPD = Assist.optInt("BSKIPPD", 15) / 100.0;
	static void skip(List<Cand> cs) {
		Hero h = H;
		if (has(cs, Role.DOWN) || Dungeon.bossLevel() || Dungeon.depth % 5 == 4 || Assist.L.locked || Assist.bag(com.shatteredpixel.shatteredpixeldungeon.items.Amulet.class) != null || !Assist.floorLeft()) return;
		int ex = Assist.L.exit();
		if (ex < 0 || ex >= Assist.L.length() || !Assist.known[ex] || Assist.L.map[ex] == Terrain.LOCKED_EXIT || Assist.walk(ex) > 20 || Assist.bad.containsKey(ex)) return;
		List<Mob> ds = new ArrayList<>();
		for (Mob m : Assist.mobs()) if (Assist.enemy(m) && m.state != m.SLEEPING && m.state != m.PASSIVE && !(m instanceof NPC) && !(m instanceof Piranha) && !Char.hasProp(m, Char.Property.IMMOVABLE)) ds.add(m);
		if (ds.isEmpty()) return;
		for (Mob m : ds) if (Assist.distance(m.pos, ex) < Assist.walk(ex)) return;   // (it's nearer the stairs than he is)
		double pd = FightModel.evaluate(h, ds).pDeath;
		if (pd < SKIPPD) return;
		int seen = 0, left = 0; for (int c = 0; c < Assist.L.length(); c++) if (Assist.dist[c] > 0 && Assist.dist[c] < Assist.INF) { if (Assist.unseenBy(c)) left++; else seen++; }
		double share = Math.min(1, Math.max(0.1, left * 4.0 / Math.max(1, seen + left * 4)));   // (an unknown edge cell stands for the room beyond it)
		double r = Budget.ahead(h), v = MEET * pd * r - LOOT * share;
		Cand c = new Cand(new Target(ex, ex == h.pos ? Assist.PICKUP : Assist.GO, "escape: down the stairs (brain: the floor's danger)"), "brain", Role.DOWN);
		c.v = v; c.note = String.format(java.util.Locale.ROOT, "skip pd %.2f share %.2f R %.1f", pd, share, r);
		cs.add(c); skips++;
	}
	static int skips;

	// (M2) from afar or closing in: a caster's bolt ignores armour and the Assist never shoots at casters (Assist.ranged:
	// "melee from round a corner"); with plan A a walk at a foe not beside the hero, the bow / a charged attack wand at
	// the nearest caster in line is offered and weighed: the fight with the hero's shots first (the lookahead's policy)
	// against the same fight walking up at once (no shots). A wand charge used costs BCHARGE floors.
	static final boolean RANGEDV = Assist.opt("BRANGED", true);
	static final double CHARGE = Assist.optInt("BCHARGE", 1) / 100.0;
	static void afar(List<Cand> cs) {
		Target a = cs.get(0).t; Hero h = H;
		if (a.kind != Assist.ENEMY && !(a.why != null && a.why.startsWith("caster: close in"))) return;
		for (Mob m : foes) if (Assist.adjacent(m.pos, h.pos)) return;
		Mob c = null; for (Mob m : foes) if ((m instanceof Shaman || m instanceof Warlock || m instanceof DM100) && Assist.line(h.pos, m.pos) && (c == null || Assist.distance(m.pos, h.pos) < Assist.distance(c.pos, h.pos))) c = m;
		if (c == null || me.rangedShots <= 0) return;
		Item it = null; String act = null; boolean wand = false;
		com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow bow = Assist.bag(com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow.class);
		if (bow != null) { it = bow; act = com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow.AC_SHOOT; }
		int ed = Assist.distance(c.pos, h.pos);
		if (it == null) for (Item i : h.belongings.backpack) if (i instanceof com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand) {
			com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand w = (com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand) i;
			if (Assist.charged(w, w) && Assist.attackWand(w.getClass(), ed)) { it = w; act = com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand.AC_ZAP; wand = true; break; } }
		if (it == null || Assist.declined(it.getClass(), act)) return;
		Look.Out walk = base, shoot = Look.run(me, fs, plan().afar(), seed);
		Cand k = new Cand(Assist.use(it, act, c.pos, "brain: from afar (a caster)"), "brain", Role.RANGED);
		k.v = fightV(shoot, me.heals, Budget.rescue(h, Math.max(1, me.heals), 1), Budget.rescue(h, Math.max(1, me.heals - 1), 1)) - fightV(walk, me.heals, Budget.rescue(h, Math.max(1, me.heals), 1), Budget.rescue(h, Math.max(1, me.heals - 1), 1)) - (wand ? CHARGE : 0);
		k.note = "afar " + shoot + " | walk " + walk;
		cs.add(k);
	}

	// =============================================================================================================
	// (M2, the coordinator's ask) steadiness across decisions: several skills propose competing moves, and plan A has
	// gone back and forth between them (K7's, WD's loops; the Dwarf King's two tactics, M2-c). Two guards, each logged
	// (DITHER / ZEROTIME lines) so the owners can fix the cause:
	//   dithering: over the last BDITHERN decisions plan A took turns among 2-3 targets, the hero on 3 cells or fewer,
	//     no foe's health down and his own not up: one of them is kept as plan A for BDITHERK decisions (a boss's or a
	//     fight's tactic first, else the latest), while it stays in the list;
	//   zero time: BZEROT decisions with the same plan A and the game's clock not moving: that one is put last.
	// =============================================================================================================
	static final boolean DITHER = Assist.opt("BDITHER", true);
	static int acted, lastActed = -1, commitCell = -1; static final Set<String> ditherKeys = new HashSet<>(); static final Map<Integer, Integer> lastFoeHp = new HashMap<>();
	static final int DN = Assist.optInt("BDITHERN", 6), DK = Assist.optInt("BDITHERK", 5), ZT = Assist.optInt("BZEROT", 8);
	static final ArrayDeque<String> hKeys = new ArrayDeque<>(); static final ArrayDeque<Integer> hPos = new ArrayDeque<>(); static final ArrayDeque<Integer> hFoe = new ArrayDeque<>(), hHp = new ArrayDeque<>(); static final ArrayDeque<Float> hT = new ArrayDeque<>();
	static String commitKey; static int commitLeft, zeroSame; static String zeroKey, zeroVeto; static int zeroVetoLeft; static int dithers, zeros; static int hDepth = -1;
	static String key(Target t) { String w = t.why == null ? "" : t.why; int c = w.indexOf(" ["); if (c > 0) w = w.substring(0, c); return t.kind + "@" + t.cell + ":" + w; }
	static boolean tactic(String k) { String w = k.substring(k.indexOf(':') + 1); return w.startsWith("boss") || w.startsWith("tactic") || w.startsWith("fight"); }
	static void steady() {
		List<Target> out = Assist.out; if (out.isEmpty()) return;
		Hero h = H; float now = Actor.now();
		if (Dungeon.depth != hDepth) { hDepth = Dungeon.depth; hKeys.clear(); hPos.clear(); hFoe.clear(); hHp.clear(); hT.clear(); commitLeft = 0; zeroSame = 0; zeroVetoLeft = 0; }
		boolean fresh = acted != lastActed;   // (a re-plan with nothing done since (the watch's list redrawn, a Down) isn't a decision of its own)
		// the zero-time veto in force: that plan A put last
		if (zeroVetoLeft > 0) { if (fresh) zeroVetoLeft--; if (out.size() > 1 && key(out.get(0)).equals(zeroVeto)) { Target t = out.remove(0); out.add(t); } }
		// a commitment in force: its target kept first while it's in the list
		if (commitLeft > 0) {
			if (fresh) commitLeft--; Target c = null; for (Target t : out) if (key(t).equals(commitKey)) { c = t; break; }
			if (c != null && out.get(0) != c) { out.remove(c); out.add(0, c); }
			else if (c == null && commitCell == h.pos) {   // (the kept one's cell reached: the others it took turns with put last, the hero stays)
				List<Target> rest = new ArrayList<>(); for (Target t : out) if (ditherKeys.contains(key(t))) rest.add(t);
				if (rest.size() < out.size()) { out.removeAll(rest); out.addAll(rest); }
			} else if (c == null) commitLeft = 0;
		}
		if (!fresh) return;
		lastActed = acted;
		String k = key(out.get(0));
		int foe = 0;   // (the damage dealt since the last decision: health off each foe in sight, a kill its last health; new foes coming don't hide it)
		Map<Integer, Integer> now2 = new HashMap<>(); for (Mob m : Assist.mobs()) if (Assist.enemy(m)) { int v = m.HP + m.shielding(); now2.put(m.id(), v); Integer p0 = lastFoeHp.get(m.id()); if (p0 != null && p0 > v) foe += p0 - v; }
		for (Map.Entry<Integer, Integer> e : lastFoeHp.entrySet()) if (!now2.containsKey(e.getKey())) { com.shatteredpixel.shatteredpixeldungeon.actors.Actor a = Actor.findById(e.getKey()); if (!(a instanceof Mob) || !((Mob) a).isAlive()) foe += e.getValue(); }
		lastFoeHp.clear(); lastFoeHp.putAll(now2);
		hKeys.addLast(k); hPos.addLast(h.pos); hFoe.addLast(foe); hHp.addLast(h.HP); hT.addLast(now);
		while (hKeys.size() > Math.max(DN, ZT)) { hKeys.removeFirst(); hPos.removeFirst(); hFoe.removeFirst(); hHp.removeFirst(); hT.removeFirst(); }
		// zero time: the same plan A, the clock still
		if (k.equals(zeroKey) && hT.size() >= 2 && now == ((ArrayDeque<Float>) hT).toArray(new Float[0])[hT.size() - 2]) zeroSame++; else { zeroSame = 0; zeroKey = k; }
		if (zeroSame >= ZT && out.size() > 1) {
			zeros++; System.out.println("ZEROTIME d" + Dungeon.depth + " t" + (int) now + " " + ZT + " decisions on '" + k + "' with no time passing: put last (next: " + key(out.get(1)) + ")");
			zeroVeto = k; zeroVetoLeft = DK; zeroSame = 0; Target t = out.remove(0); out.add(t); return;
		}
		if (commitLeft > 0 || hKeys.size() < DN) return;
		// dithering over the last DN decisions
		String[] ks = hKeys.toArray(new String[0]); Integer[] ps = hPos.toArray(new Integer[0]), fo = hFoe.toArray(new Integer[0]), hp = hHp.toArray(new Integer[0]);
		int from = ks.length - DN; Set<String> kset = new LinkedHashSet<>(); Set<Integer> pset = new HashSet<>(); int switches = 0;
		for (int i = from; i < ks.length; i++) { kset.add(ks[i]); pset.add(ps[i]); if (i > from && !ks[i].equals(ks[i - 1])) switches++; }
		int dealt = 0; for (int i = from + 1; i < ks.length; i++) dealt += fo[i];
		boolean progress = dealt > 0 || hp[ks.length - 1] > hp[from];
		if (kset.size() < 2 || kset.size() > 3 || pset.size() > 3 || switches < DN - 2 || progress) return;
		String pick = null; for (String x : kset) if (tactic(x)) pick = x;
		if (pick == null) pick = ks[ks.length - 1];
		dithers++; System.out.println("DITHER d" + Dungeon.depth + " t" + (int) now + " plan A took turns among " + kset + " on " + pset.size() + " cells, no progress: kept '" + pick + "' for " + DK);
		commitKey = pick; commitLeft = DK; ditherKeys.clear(); ditherKeys.addAll(kset); ditherKeys.remove(pick);
		try { commitCell = Integer.parseInt(pick.substring(pick.indexOf('@') + 1, pick.indexOf(':'))); } catch (Throwable e) { commitCell = -1; }
		Target c = null; for (Target t : out) if (key(t).equals(pick)) { c = t; break; }
		if (c != null && out.get(0) != c) { out.remove(c); out.add(0, c); }
		hKeys.clear(); hPos.clear(); hFoe.clear(); hHp.clear(); hT.clear();
	}

	static boolean has(List<Cand> cs, Role r) { for (Cand c : cs) if (c.role == r) return true; return false; }
	/** Stairs known, a short walk off, no foe nearer to them than the hero. */
	static boolean stairsOk(int s) {
		if (s < 0 || s >= Assist.L.length() || !Assist.known[s] || Assist.walk(s) > 10 || Assist.bad.containsKey(s)) return false;
		int w = Assist.walk(s);
		for (Mob m : foes) if (Assist.distance(m.pos, s) < w) return false;
		return true;
	}

	/** The candidate's value in floors, relative to carrying on (NaN: the brain has no view). */
	static void value(Cand c) {
		if (!fight || base == null) return;
		Hero h = H; int n = me.heals;
		double vh = Budget.rescue(h, Math.max(1, n), 1), vh2 = Budget.rescue(h, Math.max(1, n - 1), 1);
		switch (c.role) {
			case CARRY: return;
			case HEAL: {
				Fighter m1 = me;
				if (n <= 0) { if (c.t.item == null || Assist.known(c.t.item)) return; m1 = me.copy(); m1.heals = 1; m1.healAmount = Math.round(0.8f * me.ht + 14); vh = Budget.rescue(h, 1, 1); }   // (a believed Healing: drunk, it's the one)
				Look.Out o = Look.run(m1, fs, plan().drink(), seed);
				c.v = fightV(o, Math.max(1, n), vh, vh2) - fightV(base, n, vh, vh2);
				c.note = "drink " + o + " | on " + base;
				return;
			}
			case ESCAPE: {
				int k = 0; for (Item i : h.belongings.backpack) if (i.getClass() == c.t.item.getClass()) k += i.quantity();
				Look.Out o = Look.run(me, fs, plan().lose(1).flee(0), seed);
				c.v = gone(o, LATER * whole(n, vh, vh2), Budget.rescue(h, Math.max(1, k), ESCEFF)) - fightV(base, n, vh, vh2);
				c.note = "escape " + o;
				return;
			}
			case UP: case DOWN: {
				int w = Math.max(0, Assist.walk(c.t.obj >= 0 ? c.t.obj : c.t.cell));
				Look.Out o = Look.run(me, fs, plan().flee(w), seed);
				double later = c.role == Role.UP ? LATER * whole(n, vh, vh2) : -arrive(o) * R;
				c.v = gone(o, later, 0) - fightV(base, n, vh, vh2) - (c.role == Role.UP ? 0.02 : Assist.floorLeft() ? 0.2 : 0.05);
				c.note = (c.role == Role.UP ? "up " : "down ") + w + " " + o;
				return;
			}
			case EQUIP: {
				if (!GEARV) return;
				Fighter m2 = gear(c.t.item);
				if (m2 == null) return;
				Look.Out o = Look.run(me, fs, plan().me(m2).lose(1), seed);
				c.v = fightV(o, n, vh, vh2) - fightV(base, n, vh, vh2);
				c.note = "equip " + o;
				return;
			}
			default: return;
		}
	}

	/** A fight's value: death costs the floors ahead, each Healing drunk what it was worth kept, the health left a little. */
	static double fightV(Look.Out o, int n, double vh, double vh2) {
		double u = Tune.HEALBUD ? o.healsLive : o.healsUsed, spent = u <= 1 ? u * vh : vh + (u - 1) * vh2;
		return -o.pDeath * R - spent + HPW * o.hpLeft / Math.max(1, me.ht);
	}
	/** Gone from the fight (after o's walk): later, the value of what comes after (the fight met again, a new floor
	 *  hurt); spent: the item used. */
	static double gone(Look.Out o, double later, double spent) { return -o.pDeath * R + (1 - o.pDeath) * later - spent + HPW * (1 - o.pDeath); }
	/** The fight met again later with full health (rested), on the same numbers, its Healing counted as any fight's. */
	static double whole(int n, double vh, double vh2) {
		Fighter f = me.copy(); f.hp = f.ht; f.shield = 0;
		List<Fighter> l = new ArrayList<>(); for (Fighter x : fs) { Fighter y = x.copy(); y.dist = Math.max(y.dist, 4); y.asleep = false; l.add(y); }
		Look.Out o = Look.run(f, l, plan(), seed ^ 0x77);
		return fightV(o, n, vh, vh2) - HPW;   // (the rest it took, a full bar's worth)
	}
	/** Down the stairs hurt: the next floor's danger, worse the less health there is to meet it with. */
	static double arrive(Look.Out o) {
		double[] q = Budget.hazards(H);
		double q1 = q.length > 1 ? q[1] : q[0], share = Math.max(0.1, o.hpLeft / Math.max(1, me.ht * Math.max(0.05, 1 - o.pDeath)));
		return Math.min(1, q1 / share);
	}
	/** The hero in other gear (a weapon or armour as shown), or null when the models can't tell. */
	static Fighter gear(Item i) {
		Hero h = H;
		if (i instanceof ClassArmor) return null;
		if (i instanceof Armor) return HeroView.of(h, h.belongings.weapon(), (Armor) i, 0, 0, -1);
		if (i instanceof MeleeWeapon) return HeroView.of(h, (KindOfWeapon) i, h.belongings.armor(), 0, 0, -1);
		return null;
	}

	static final boolean DBG = Assist.opt("ADBG", false) || Assist.opt("BDBG", false);
	static void dump(List<Cand> cs, Cand pick) {
		System.out.println("  BRAIN d" + Dungeon.depth + " t" + (int) Actor.now() + " hp " + H.HP + "/" + H.HT + " R " + String.format("%.2f", R) + " [" + Budget.dump(H) + "]" + " foes " + fs.size() + (base != null ? " base " + base : "") + " pick " + (pick == null ? "-" : pick.toString()));
		for (Cand c : cs) if (c.valued() || c.veto) System.out.println("    " + c + "  " + c.note);
	}

	/** Plan A's kind, counted (the bot acts on every plan A: equips, drinks, escapes, rests made). */
	static void tally(Target t) { Role r = role(t); lastPlanRole = r; lastDepth = Dungeon.depth; if (r != Role.CARRY && r != Role.OTHER) planAs.merge(r.name().toLowerCase(), 1, Integer::sum); }
	static final Map<String, Integer> planAs = new TreeMap<>();
	// the stairs taken to get away (Rules: rest before going back, no chained escapes)
	static Role lastPlanRole; static int lastDepth = -1, fledFrom = -1; static float fledAt = -999;

	/** The game's tally (AssistBot prints it with the result). */
	static String stats() {
		String s = String.format("dithers %d zerotime %d skips %d decisions %d fights %d overrides %d (agree %.1f%%) vetoes %d ms avg %.2f max %.1f slow %d by %s rows {%s} planA %s", dithers, zeros, skips, decisions, fights, overrides,
				decisions == 0 ? 100 : 100.0 * (decisions - overrides) / decisions, vetoes, decisions == 0 ? 0 : nanos / 1e6 / decisions, maxNanos / 1e6, slow, overBy, Rules.fired(), planAs);
		skips = 0; lastFoeHp.clear(); dithers = zeros = 0; hDepth = -1; commitLeft = 0; zeroVetoLeft = 0; zeroSame = 0; zeroKey = null;
		decisions = fights = overrides = vetoes = slow = 0; nanos = maxNanos = 0; overBy.clear(); planAs.clear(); Rules.reset(); lastDepth = fledFrom = -1; fledAt = -999; lastPlanRole = null;
		return s;
	}
}
