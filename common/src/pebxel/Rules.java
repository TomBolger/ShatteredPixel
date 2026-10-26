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
		// -- rescue items ---------------------------------------------------------------------------------------------
		// the hands' Healing for what the lookahead doesn't see: harm still to come (fire, poison, bleeding: HeroView.dot)
		// or health this low (MT's Tune.HEALLOW; BR-a2: a burning Mage kept his potion on the brain's say and died)
		boost("rescue: the hands' Healing, hurt to the quick", c -> c.role == Role.HEAL && !"brain".equals(c.src)
				&& (pebxel.bot.HeroView.dot(H()) > 0 || H().HP * 100 < H().HT * Math.max(pebxel.bot.Tune.HEALLOW, 40)), 99),
		// a fight seen going nowhere (AsBasics: blows that never lower its health): the basics' way out stands; the
		// lookahead only has the numbers, the stall was seen (BR-a3: a Rogue kept hitting the Great Crab for 1,100 turns)
		boost("rescue: the basics' way out of a stalled fight", c -> c == Rules.planA && c.t.why.startsWith("stalled"), 99),
		// never die holding Healing (BR-a2: deaths with 5 to 11 known Healing left, burning or under warlocks' bolts: the
		// hands give nothing while the hero burns, "out of the fire first"): the next round's worst (the blows of those
		// beside him at their most, the bolts of the shooters in line, a third of the harm to come) could kill: drink
		propose("rescue: drink rather than die", c -> c == Rules.planA && c.role != Role.HEAL && healPot() != null && Brain.healing == 0 && worstRound() >= H().HP, 0, Rules::drinkTarget),
		// -- getting away by the stairs ------------------------------------------------------------------------------
		// (BR-a1: a starving hero bounced between the floors for ever, up from the foe by the stairs, down again as the
		// floor above was done.) The stairs up are an escape only with a rest to come (food for it), not straight after
		// another escape by the stairs, and the floor fled to is rested on before going back.
		veto("stairs: no rest to come", c -> (c.role == Role.UP || c.role == Role.DOWN) && Assist.starving()),
		veto("stairs: no chained escapes", c -> (c.role == Role.UP || c.role == Role.DOWN) && Actor.now() - Brain.fledAt < 30 && Actor.now() >= Brain.fledAt),
		propose("fled: rest before going back", c -> (stairsDown(c) || stairsUp(c)) && Brain.fledFrom >= 0 && Math.abs(Brain.fledFrom - Dungeon.depth) == 1
				&& H().HP * 10 < H().HT * 9 && calm() && !Assist.starving() && !Assist.declined(null, Assist.REST_ACT), PREP, Rules::restTarget),
		// -- the climb with the Amulet (agent K7: AsAscent) ------------------------------------------------------------
		// (K5: up to 8 games of a run died climbing; K7-base: 6 of 6 from depth 26 died on 24, the first floor up, 250
		// turns spent there picking up, holding doorways for foes the curse calls without end, resting.) Off the way up:
		// no item hunting, exploring or searching; no rest while the curse (8+) takes more a turn than resting gives.
		veto("ascent: nothing off the way", AsAscent::offWay),
		veto("ascent: never down", AsAscent::down),
		veto("ascent: no rest under the curse's harm", AsAscent::restHarm),
		// -- shooters' bolts (agent K7: AsBolts) ------------------------------------------------------------------------
		// an eye's charged beam (shown: the charge, aimed where the hero stood) fires through its line for 30-50: off it
		propose("bolts: off the eye's beam", AsBolts::inBeam, 0, AsBolts::offBeam),
		// two shooters or more: the step out of the nearest's line went into the other's and back (K7: a Cleric between
		// two scorpios lost 150 health in 12 turns): out of every line at once
		veto("bolts: out of one line into another", AsBolts::intoAnother),
		propose("bolts: out of every line", AsBolts::manyLines, 0, AsBolts::outOfAll),
		// a scorpio hunting backs off (Scorpio.getCloser): waiting round the corner for it waits for nothing
		veto("bolts: no waiting for a scorpio", AsBolts::waitScorpio),
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
	static com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing healPot() {
		com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing p = H().belongings.getItem(com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing.class);
		return p != null && p.isKnown() ? p : null;
	}
	/** The most the next round could take: the foes in the fight at their hardest blow (a bolt from afar), a third of the harm to come. */
	static double worstRound() {
		double w = pebxel.bot.HeroView.dot(H()) / 3;
		if (Brain.fight && Brain.me != null) for (pebxel.bot.Fighter f : Brain.fs) {
			if (f.asleep) continue;
			int dr = Brain.me.dr.lo;
			if (f.dist <= 1) w += Math.max(f.zapDmg != null && "Eye".equals(f.name) ? f.zapDmg.hi() : 0, Math.max(0, (f.rageHp == 0 && f.rageDmg != null ? f.rageDmg : f.dmg).hi() - dr) * Math.max(1, Math.round(1 / Math.max(0.25, f.delay))));   // (an eye gazes beside the hero too)
			else if (f.zapDmg != null) w += f.zapDmg.hi();
			else if (f.dist <= 1 + f.speed && !f.ranged) w += Math.max(0, f.dmg.hi() - dr) * 0.5;
		}
		return w;
	}
	static Target drinkTarget() {
		Target t = Assist.use(healPot(), com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion.AC_DRINK, -1, "rescue: drink rather than die");
		t.hint = Math.max(1, Brain.R) * 0.5;   // (a likely death put off: half the floors ahead)
		return t;
	}
	/** c is plan A, the walk to the stairs up. */
	static boolean stairsUp(Cand c) { int en = Assist.L.entrance(); Target t = c.t; return c == planA && (t.kind == Assist.GO || t.kind == Assist.PICKUP) && (t.obj == en || t.cell == en); }
	static Target restTarget() { Target t = new Target(Dungeon.hero.pos, Assist.WAIT, "rest first (brain)"); t.action = Assist.REST_ACT; t.obj = -1; return t; }
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
				else { Target t = r.make.get(); if (t != null) { Cand p = new Cand(t, "rule", Brain.role(t)); p.v = Double.isNaN(t.hint) ? r.boost : t.hint; p.note = r.name; cs.add(p); } }
			}
		}
		planA = null;
	}

	static void reset() { for (Row r : TABLE) r.fired = 0; takenOff.clear(); worn = new Item[0]; }
	static String fired() { StringBuilder b = new StringBuilder(); for (Row r : TABLE) if (r.fired > 0) b.append(b.length() > 0 ? ", " : "").append(r.name).append(' ').append(r.fired); return b.toString(); }
}
