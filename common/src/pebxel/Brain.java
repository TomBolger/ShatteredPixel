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
	enum Role { CARRY, HEAL, ESCAPE, UP, DOWN, EQUIP, REST, OTHER }

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
	static double R; static long seed; static Look.Out base; static boolean fight;
	static String lastWhy = "";
	static Role lastRole; static float lastAt = -99;   // the brain's last change of plan A (Rules: keep going)

	// ---- stats (per game: AssistBot prints them) ----
	static int decisions, fights, overrides, vetoes, slow; static long nanos, maxNanos;
	static final Map<String, Integer> overBy = new TreeMap<>();

	/** A new list: the base list's plan A so far. */
	static void begin(Target planA) {
		first = planA; offers.clear(); H = Dungeon.hero;
		try { Rules.observe(); } catch (Throwable e) { Assist.lastError = e; }
		try { setupFight(); } catch (Throwable e) { Assist.lastError = e; fight = false; base = null; }
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
		if (t.kind == Assist.USE && t.item instanceof PotionOfHealing && Potion.AC_DRINK.equals(t.action)) return Role.HEAL;
		if (t.kind == Assist.USE && escapeItem(t.item) && (Potion.AC_DRINK.equals(t.action) || Scroll.AC_READ.equals(t.action))) return Role.ESCAPE;
		if (t.kind == Assist.USE && EquipableItem.AC_EQUIP.equals(t.action)) return Role.EQUIP;
		if (t.kind == Assist.WAIT && Assist.REST_ACT.equals(t.action)) return Role.REST;
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
		long dt = System.nanoTime() - t0; nanos += dt; maxNanos = Math.max(maxNanos, dt); if (dt > 20_000_000L) slow++;
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
		for (Mob m : Assist.mobs()) if (Assist.enemy(m) && m.state != m.PASSIVE && !(m instanceof NPC) && (Assist.adjacent(m.pos, hp) || (Assist.hunting(m) && Assist.walk(m.pos) <= 8 && !(m instanceof Piranha)) || (FoeView.shooter(m) && m.state != m.SLEEPING && Assist.distance(m.pos, hp) <= 8 && Assist.line(m.pos, hp)))) foes.add(m);
		if (foes.isEmpty()) return;
		me = HeroView.of(h);
		for (Mob m : foes) fs.add(FoeView.of(m, h));
		R = Budget.ahead(h);
		seed = 0x51ED ^ (Dungeon.depth * 1000003L) ^ (hp * 7919L) ^ ((long) Actor.now() * 31L);
		base = Look.run(me, fs, new Look.Plan(), seed);
		fight = true; fights++;
	}

	/** The brain's own options in a fight, when the rules didn't offer them: drink Healing, a known escape, the stairs. */
	static void own(List<Cand> cs) {
		Hero h = H;
		PotionOfHealing heal = h.belongings.getItem(PotionOfHealing.class);
		if (heal != null && heal.isKnown() && !has(cs, Role.HEAL) && !Assist.declined(PotionOfHealing.class, Potion.AC_DRINK)) cs.add(new Cand(Assist.use(heal, Potion.AC_DRINK, -1, "brain: heal"), "brain", Role.HEAL));
		if (!has(cs, Role.ESCAPE) && !Assist.blind()) for (Class<? extends Item> k : Arrays.asList(ScrollOfTeleportation.class, PotionOfInvisibility.class, ScrollOfTerror.class)) {
			Item i = h.belongings.getItem(k);
			if (i != null && Assist.known(i) && !Assist.declined(k, i instanceof Potion ? Potion.AC_DRINK : Scroll.AC_READ)) { cs.add(new Cand(Assist.use(i, i instanceof Potion ? Potion.AC_DRINK : Scroll.AC_READ, -1, "brain: escape"), "brain", Role.ESCAPE)); break; }
		}
		if (STAIRSV && !Dungeon.bossLevel() && !Assist.L.locked && Assist.bag(com.shatteredpixel.shatteredpixeldungeon.items.Amulet.class) == null) {
			int ent = Assist.L.entrance(), ex = Assist.L.exit();
			if (Dungeon.depth > 1 && !has(cs, Role.UP) && stairsOk(ent)) cs.add(new Cand(new Target(ent, ent == h.pos ? Assist.PICKUP : Assist.GO, "escape: up the stairs (brain)"), "brain", Role.UP));
			if (!has(cs, Role.DOWN) && stairsOk(ex) && Assist.L.map[ex] != Terrain.LOCKED_EXIT && Dungeon.depth % 5 != 4) cs.add(new Cand(new Target(ex, ex == h.pos ? Assist.PICKUP : Assist.GO, "escape: down the stairs (brain)"), "brain", Role.DOWN));
		}
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
				if (n <= 0) return;
				Look.Out o = Look.run(me, fs, new Look.Plan().drink(), seed);
				c.v = fightV(o, n, vh, vh2) - fightV(base, n, vh, vh2);
				c.note = "drink " + o + " | on " + base;
				return;
			}
			case ESCAPE: {
				int k = 0; for (Item i : h.belongings.backpack) if (i.getClass() == c.t.item.getClass()) k += i.quantity();
				Look.Out o = Look.run(me, fs, new Look.Plan().lose(1).flee(0), seed);
				c.v = gone(o, LATER * whole(n, vh, vh2), Budget.rescue(h, Math.max(1, k), ESCEFF)) - fightV(base, n, vh, vh2);
				c.note = "escape " + o;
				return;
			}
			case UP: case DOWN: {
				int w = Math.max(0, Assist.walk(c.t.obj >= 0 ? c.t.obj : c.t.cell));
				Look.Out o = Look.run(me, fs, new Look.Plan().flee(w), seed);
				double later = c.role == Role.UP ? LATER * whole(n, vh, vh2) : -arrive(o) * R;
				c.v = gone(o, later, 0) - fightV(base, n, vh, vh2) - (c.role == Role.UP ? 0.02 : Assist.floorLeft() ? 0.2 : 0.05);
				c.note = (c.role == Role.UP ? "up " : "down ") + w + " " + o;
				return;
			}
			case EQUIP: {
				if (!GEARV) return;
				Fighter m2 = gear(c.t.item);
				if (m2 == null) return;
				Look.Out o = Look.run(me, fs, new Look.Plan().me(m2).lose(1), seed);
				c.v = fightV(o, n, vh, vh2) - fightV(base, n, vh, vh2);
				c.note = "equip " + o;
				return;
			}
			default: return;
		}
	}

	/** A fight's value: death costs the floors ahead, each Healing drunk what it was worth kept, the health left a little. */
	static double fightV(Look.Out o, int n, double vh, double vh2) {
		double u = o.healsUsed, spent = u <= 1 ? u * vh : vh + (u - 1) * vh2;
		return -o.pDeath * R - spent + HPW * o.hpLeft / Math.max(1, me.ht);
	}
	/** Gone from the fight (after o's walk): later, the value of what comes after (the fight met again, a new floor
	 *  hurt); spent: the item used. */
	static double gone(Look.Out o, double later, double spent) { return -o.pDeath * R + (1 - o.pDeath) * later - spent + HPW * (1 - o.pDeath); }
	/** The fight met again later with full health (rested), on the same numbers, its Healing counted as any fight's. */
	static double whole(int n, double vh, double vh2) {
		Fighter f = me.copy(); f.hp = f.ht; f.shield = 0;
		List<Fighter> l = new ArrayList<>(); for (Fighter x : fs) { Fighter y = x.copy(); y.dist = Math.max(y.dist, 4); y.asleep = false; l.add(y); }
		Look.Out o = Look.run(f, l, new Look.Plan(), seed ^ 0x77);
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
		System.out.println("  BRAIN d" + Dungeon.depth + " t" + (int) Actor.now() + " hp " + H.HP + "/" + H.HT + " R " + String.format("%.2f", R) + " foes " + fs.size() + (base != null ? " base " + base : "") + " pick " + (pick == null ? "-" : pick.toString()));
		for (Cand c : cs) if (c.valued() || c.veto) System.out.println("    " + c + "  " + c.note);
	}

	/** Plan A's kind, counted (the bot acts on every plan A: equips, drinks, escapes, rests made). */
	static void tally(Target t) { Role r = role(t); if (r != Role.CARRY && r != Role.OTHER) planAs.merge(r.name().toLowerCase(), 1, Integer::sum); }
	static final Map<String, Integer> planAs = new TreeMap<>();

	/** The game's tally (AssistBot prints it with the result). */
	static String stats() {
		String s = String.format("decisions %d fights %d overrides %d (agree %.1f%%) vetoes %d ms avg %.2f max %.1f slow %d by %s rows {%s} planA %s", decisions, fights, overrides,
				decisions == 0 ? 100 : 100.0 * (decisions - overrides) / decisions, vetoes, decisions == 0 ? 0 : nanos / 1e6 / decisions, maxNanos / 1e6, slow, overBy, Rules.fired(), planAs);
		decisions = fights = overrides = vetoes = slow = 0; nanos = maxNanos = 0; overBy.clear(); planAs.clear(); Rules.reset();
		return s;
	}
}
