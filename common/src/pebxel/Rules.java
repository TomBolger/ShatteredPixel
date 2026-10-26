package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import pebxel.Assist.Target;
import pebxel.Brain.Cand;
import pebxel.Brain.Role;
import pebxel.bot.UpgradePlan;

/**
 * The brain's table of situational rules (agent BR): "the if/then tree", one row each, in plain view, for the
 * seed-by-seed work to extend. Three kinds of row:
 *
 *   VETO     an option never made plan A while the condition holds (it stays in the list for a Down; a vetoed hands
 *            suggestion makes way for the hands' next one);
 *   BOOST    floors added to an option's value (an option with no value of its own starts at 0, carrying on);
 *   PROPOSE  a new option with a value, when the condition on the decision holds.
 *
 * Each row: a name (counted per game: Brain.stats), what it's for, the condition, the effect, and why (the evidence).
 * Only what the player knows goes into a condition. Rows are tried in order; every matching one applies.
 * AS_R_<name with spaces as _>=0 turns a row off (e.g. AS_R_gear_swap_back=0).
 */
final class Rules {
	private Rules() {}

	static final int SWAPBACK = Assist.optInt("BSWAPBACK", 150);   // turns a piece taken off isn't put back on (unless something about it was learnt)
	static final double PREP = Assist.optInt("BPREP", 30) / 100.0;   // floors: getting ready for the boss before its stairs

	static final class Row {
		final String name, kind; final Predicate<Cand> when; final double boost; final Supplier<Target> make; int fired; final boolean on;
		Row(String name, String kind, Predicate<Cand> when, double boost, Supplier<Target> make) {
			this.name = name; this.kind = kind; this.when = when; this.boost = boost; this.make = make; this.on = Assist.opt("R_" + name.replace(' ', '_').replace(":", ""), true);
		}
	}
	static Row veto(String n, Predicate<Cand> w) { return new Row(n, "VETO", w, 0, null); }
	static Row boost(String n, Predicate<Cand> w, double b) { return new Row(n, "BOOST", w, b, null); }
	static Row propose(String n, Predicate<Cand> w, double v, Supplier<Target> m) { return new Row(n, "PROPOSE", w, v, m); }

	// =============================================================================================================
	// THE TABLE
	// =============================================================================================================
	static final Row[] TABLE = {
		// -- gear --------------------------------------------------------------------------------------------------
		// K1: swaps looping mid-fight. A piece taken off lately is not put back on unless something was learnt about it
		// or what replaced it (its level, a curse) since: the rules that chose each disagree, and every swap is a turn.
		veto("gear: swap back", c -> c.role == Role.EQUIP && swappedBack(c.t.item)),
		// a swap in a fight costs a turn of free blows: only when the lookahead says the new piece wins it (Brain.value)
		veto("gear: blind swap in a fight", c -> c.role == Role.EQUIP && Brain.fight && !(c.valued() && c.v > Brain.MARGIN)),
		// -- the brain's own steadiness ------------------------------------------------------------------------------
		// a walk to the stairs, chosen last turn over the fight, kept on: re-weighed from scratch each step it went back
		// and forth (a step nearer, the foe a step nearer too)
		boost("keep going: the stairs", c -> (c.role == Role.UP || c.role == Role.DOWN) && c.role == Brain.lastRole && Actor.now() - Brain.lastAt <= 2, Brain.MARGIN),
		// -- the boss ahead: arrive whole, with the scrolls read ---------------------------------------------------
		// (strategy-reference 4.x: every boss guide starts "full health, upgrades in"; Thomas: "boss preparation")
		propose("boss next: rest first", c -> stairsDown(c) && bossNext() && calm() && H().HP * 100 < H().HT * 95 && !Assist.starving()
				&& !Assist.declined(null, Assist.REST_ACT) && !AsBasics.veto(restTarget()), PREP, Rules::restTarget),
		propose("boss next: read the upgrades", c -> stairsDown(c) && bossNext() && calm() && !Assist.blind() && upgrade() != null
				&& !Assist.declined(ScrollOfUpgrade.class, Scroll.AC_READ) && upgradeOn() != null, PREP, Rules::upgradeTarget),
	};

	// =============================================================================================================
	// the conditions' helpers
	// =============================================================================================================
	static Hero H() { return Dungeon.hero; }
	/** No foe about (none in sight and none lately coming). */
	static boolean calm() { return !Brain.fight && !Assist.seenFoe && !Assist.threatNear && !Assist.L.locked; }
	/** The next floor down is a boss's. */
	static boolean bossNext() { return Dungeon.depth % 5 == 4 && Dungeon.depth < 25; }
	/** c is plan A, the walk to the stairs down (the floor done). */
	static boolean stairsDown(Cand c) {
		int ex = Assist.L.exit(); Target t = c.t;
		return c == planA && (t.kind == Assist.GO || t.kind == Assist.PICKUP) && (t.obj == ex || t.cell == ex) && Assist.bag(com.shatteredpixel.shatteredpixeldungeon.items.Amulet.class) == null;
	}
	static Target restTarget() { Target t = new Target(Dungeon.hero.pos, Assist.WAIT, "boss next: rest first"); t.action = Assist.REST_ACT; t.obj = -1; return t; }
	static ScrollOfUpgrade upgrade() { ScrollOfUpgrade s = H().belongings.getItem(ScrollOfUpgrade.class); return s != null && s.isKnown() ? s : null; }
	static Item upgradeOn() {
		Item on = null; try { on = UpgradePlan.target(H()); } catch (Throwable e) { }
		if (on == null) { Armor a = H().belongings.armor(); on = a != null && !(a.cursed && a.cursedKnown) ? a : H().belongings.weapon(); }
		return on;
	}
	static Target upgradeTarget() { Target t = Assist.use(upgrade(), Scroll.AC_READ, -1, "boss next: read the upgrades"); t.on = upgradeOn(); return t; }

	// ---- what was worn: the swap-back guard (only what the hero did, as he saw it) ----
	static final class Off { float at; int stamp; Item by; int byStamp; }
	static final Map<Item, Off> takenOff = new IdentityHashMap<>();
	static Item[] worn = new Item[0];
	static int stamp(Item i) { return i == null ? 0 : (i.levelKnown ? 1 : 0) | (i.cursedKnown ? 2 : 0) | (i.isIdentified() ? 4 : 0) | (i.visiblyCursed() ? 8 : 0); }
	/** Each list: what the hero wears now against before; a piece gone from a slot was taken off. */
	static void observe() {
		Hero h = H(); if (h == null) return;
		Item[] w = { h.belongings.weapon(), h.belongings.armor(), h.belongings.artifact(), h.belongings.misc(), h.belongings.ring() };
		float now = Actor.now();
		if (worn.length == w.length) for (int k = 0; k < w.length; k++) if (worn[k] != null && worn[k] != w[k]) {
			Off o = new Off(); o.at = now; o.stamp = stamp(worn[k]); o.by = w[k]; o.byStamp = stamp(w[k]); takenOff.put(worn[k], o);
		}
		worn = w;
		takenOff.values().removeIf(o -> now - o.at > SWAPBACK || now < o.at);
	}
	static boolean swappedBack(Item i) {
		Off o = i == null ? null : takenOff.get(i);
		if (o == null || stamp(i) != o.stamp) return false;
		if (o.by != null && stamp(o.by) != o.byStamp) return false;   // (learnt something about the one that replaced it: a curse, its level)
		return o.by == null || isWorn(o.by);
	}
	static boolean isWorn(Item i) { return i instanceof EquipableItem && ((EquipableItem) i).isEquipped(H()); }

	// =============================================================================================================
	// applying the table
	// =============================================================================================================
	static Cand planA;

	/** Does a VETO row hold for c? */
	static boolean vetoed(Cand c) {
		for (Row r : TABLE) if (r.on && r.kind.equals("VETO") && r.when.test(c)) { r.fired++; c.veto = true; c.note += " veto: " + r.name; return true; }
		return false;
	}

	/** Every row on the decision's options (cs: the list as the rules made it, plus the brain's own); proposals added. */
	static void apply(List<Cand> cs, Target legacyA) {
		planA = cs.isEmpty() ? null : cs.get(0);
		for (Cand c : new ArrayList<>(cs)) {
			if (!c.veto) vetoed(c);
			for (Row r : TABLE) {
				if (!r.on || r.kind.equals("VETO") || !r.when.test(c)) continue;
				r.fired++;
				if (r.kind.equals("BOOST")) { c.v = (c.valued() ? c.v : 0) + r.boost; c.note += " +" + r.name; }
				else { Target t = r.make.get(); if (t != null) { Cand p = new Cand(t, "rule", Brain.role(t)); p.v = r.boost; p.note = r.name; cs.add(p); } }
			}
		}
		planA = null;
	}

	static void reset() { for (Row r : TABLE) r.fired = 0; takenOff.clear(); worn = new Item[0]; }
	static String fired() { StringBuilder b = new StringBuilder(); for (Row r : TABLE) if (r.fired > 0) b.append(b.length() > 0 ? ", " : "").append(r.name).append(' ').append(r.fired); return b.toString(); }
}
