package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Ghost;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.NPC;
import com.shatteredpixel.shatteredpixeldungeon.items.*;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.*;
import com.shatteredpixel.shatteredpixeldungeon.items.food.*;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.CrystalKey;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.GoldenKey;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.IronKey;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.*;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.*;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon;
import com.shatteredpixel.shatteredpixeldungeon.journal.Notes;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.plants.*;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import java.util.*;
import pebxel.Assist.Target;

/**
 * The Assistant's skill: artifacts and rings (agent K1): which to wear in the three misc slots and when to swap, and
 * when to use each artifact's own action (eat from the Horn, prick the Chalice, pull with the Chains, freeze time...).
 * Same package as Assist, so its helpers are at hand. Only what the player can see is read: a ring's kind only once
 * known, a curse only once known, an artifact's charge only once it is worn (worn artifacts are identified).
 * Every entry's why starts with "art: " (the bot's counts). {@link #urgent} goes in front of the hands (an emergency,
 * or the Horn before rations), {@link #suggest} after them.
 */
final class AsArtifacts {
	private AsArtifacts() {}

	static final boolean ON = Assist.opt("ART", true);
	static final int PRICKPCT = Assist.optInt("ARTPRICK", 60);   // the Chalice pricked while its worst prick is under this share of health
	static final int SWAPGAIN = Assist.optInt("ARTSWAP", 2);     // a swap only for this much more worth
	static final int ENERGYKEEP = Assist.optInt("ARTENERGY", 12);   // alchemy energy kept back from the Toolkit

	// =============================================================================================================
	// state: what the next window opened by a suggestion should be answered with (the bot; the player answers his own)
	// =============================================================================================================
	static KindofMisc swapOut; static Item swapIn;   // a misc slot swap: the one to take off
	static boolean freezeWanted = true;
	static float snackAt = -99;

	/** The answer (a button's words, lower case) for a window one of these suggestions opened, or null. The bot's
	 *  BotWindows asks; on the watch the player answers it himself. */
	static String windowAnswer(Window w) {
		try {
			Class<?> enc = w.getClass().getEnclosingClass();
			if (enc == null) return null;
			Hero h = Dungeon.hero;
			if (enc == KindofMisc.class && swapOut != null && h != null && swapOut.isEquipped(h)) return Messages.titleCase(swapOut.title()).toLowerCase(Locale.ROOT);
			if (enc == ChaliceOfBlood.class && h != null) {
				ChaliceOfBlood c = h.belongings.getItem(ChaliceOfBlood.class);
				return Messages.get(ChaliceOfBlood.class, c != null && prickSafe(h, c, true) ? "yes" : "no").toLowerCase(Locale.ROOT);
			}
			if (enc == TimekeepersHourglass.class) return Messages.get(TimekeepersHourglass.class, freezeWanted ? "freeze" : "stasis").toLowerCase(Locale.ROOT);
		} catch (Throwable e) { Assist.lastError = e; }
		return null;
	}

	// =============================================================================================================
	// K2's meal talents (AsAbilities.mealWhy / mealWorth), looked up by name: this file builds with or without them
	// =============================================================================================================
	private static java.lang.reflect.Method mWhy, mWorth; private static boolean looked;
	static void lookMeal() {
		if (looked) return; looked = true;
		try { mWhy = Class.forName("pebxel.AsAbilities").getDeclaredMethod("mealWhy"); mWhy.setAccessible(true); } catch (Throwable e) { mWhy = null; }
		try { mWorth = Class.forName("pebxel.AsAbilities").getDeclaredMethod("mealWorth"); mWorth.setAccessible(true); } catch (Throwable e) { mWorth = null; }
	}
	/** Non-null: the hero's meal talents make eating now worth it (K2). */
	static String mealWhy() { lookMeal(); try { Object o = mWhy == null ? null : mWhy.invoke(null); return o instanceof String ? (String) o : null; } catch (Throwable e) { return null; } }
	/** How much the hero's meal talents are worth, 0 to about 10 (K2). */
	static int mealWorth() { lookMeal(); try { Object o = mWorth == null ? null : mWorth.invoke(null); return o instanceof Integer ? (Integer) o : 0; } catch (Throwable e) { return 0; } }

	// =============================================================================================================
	// what's about
	// =============================================================================================================
	static Hero H; static int hp;
	static boolean seen, adj, near, hunted, bossAdj; static int hard;
	static List<Mob> adjFoes = new ArrayList<>();

	static void look() {
		H = Dungeon.hero; hp = H.pos;
		seen = adj = near = hunted = bossAdj = false; hard = 0; adjFoes = new ArrayList<>();
		for (Mob m : Assist.mobs()) {
			if (!Assist.enemy(m) || m.state == m.PASSIVE || m instanceof NPC) continue;
			seen = true;
			int d = Assist.distance(m.pos, hp);
			if (d <= 3) near = true;
			if (Assist.hunting(m)) { hunted = true; hard += Assist.boss(m) ? 2 : 1; }
			if (d <= 1) { adj = true; adjFoes.add(m); if (Assist.boss(m)) bossAdj = true; }
		}
	}
	static boolean calm() { return !seen && !Assist.threatNear; }
	static int charge(Item a) { Object c = Assist.field(a, "charge"); return c instanceof Integer ? (Integer) c : 0; }
	static int chargeCap(Item a) { Object c = Assist.field(a, "chargeCap"); return c instanceof Integer ? (Integer) c : 0; }
	static boolean can(Item it, String action) { try { return it.actions(H).contains(action) && !Assist.declined(it.getClass(), action); } catch (Throwable e) { return false; } }
	static <T extends Item> T worn(Class<T> c) {
		for (Item i : new Item[]{ H.belongings.artifact(), H.belongings.misc(), H.belongings.ring() }) if (c.isInstance(i)) return c.cast(i);
		return null;
	}
	static boolean healAtHand() { PotionOfHealing p = H.belongings.getItem(PotionOfHealing.class); return p != null && p.isKnown(); }
	static Target use(Item it, String action, int aim, String why) { return Assist.use(it, action, aim, "art: " + why); }
	/** A use aimed at the hero himself (Assist.followUp takes the hero's cell when obj is the cell). */
	static Target self(Item it, String action, String why) { Target t = use(it, action, hp, why); t.obj = hp; return t; }

	// =============================================================================================================
	// the hooks
	// =============================================================================================================

	/** In front of the hands: emergencies (time frozen, chained away), and the Horn before rations when starving. */
	static Target urgent(Target planA) { return guard(urgent0(planA)); }
	static Target urgent0(Target planA) {
		if (!ON) return null;
		try {
			if (Dungeon.hero == null || Assist.L == null) return null;
			look();
			if (Assist.foul(hp) || Assist.burning()) return null;
			HornOfPlenty horn = worn(HornOfPlenty.class);
			if (horn != null && Assist.starving() && !near && can(horn, HornOfPlenty.AC_EAT)) return use(horn, HornOfPlenty.AC_EAT, -1, "starving: eat from the horn");
			boolean dying = adj && H.HP * 100 < H.HT * 30 && !healAtHand();
			if (dying) {
				TimekeepersHourglass hg = worn(TimekeepersHourglass.class);
				if (hg != null && can(hg, TimekeepersHourglass.AC_ACTIVATE) && charge(hg) >= 2 && H.buff(TimekeepersHourglass.timeFreeze.class) == null && H.buff(TimekeepersHourglass.timeStasis.class) == null) {
					freezeWanted = true; return use(hg, TimekeepersHourglass.AC_ACTIVATE, -1, "dying: freeze time");
				}
				Target c = chainsAway(); if (c != null) return c;
				SandalsOfNature sn = worn(SandalsOfNature.class);
				if (sn != null && can(sn, SandalsOfNature.AC_ROOT)) {
					Class<?> e = sn.curSeedEffect;
					if (e == Fadeleaf.Seed.class) return self(sn, SandalsOfNature.AC_ROOT, "dying: fadeleaf away");
					if (e == Swiftthistle.Seed.class) return self(sn, SandalsOfNature.AC_ROOT, "dying: swiftthistle");
				}
			}
			// a boss beside the hero and the hourglass full: free blows in frozen time
			TimekeepersHourglass hg = worn(TimekeepersHourglass.class);
			if (bossAdj && hg != null && can(hg, TimekeepersHourglass.AC_ACTIVATE) && charge(hg) >= chargeCap(hg) && chargeCap(hg) > 0 && H.buff(TimekeepersHourglass.timeFreeze.class) == null && H.buff(TimekeepersHourglass.timeStasis.class) == null) {
				freezeWanted = true; return use(hg, TimekeepersHourglass.AC_ACTIVATE, -1, "boss: freeze time for free blows");
			}
			return null;
		} catch (Throwable e) { Assist.lastError = e; return null; }
	}

	/** After the hands: everything else (a fight's artifact use, then, calm, eating, feeding, wearing, charging). */
	static Target suggest(Target planA) { return guard(suggest0(planA)); }
	static Target suggest0(Target planA) {
		if (!ON) return null;
		try {
			if (Dungeon.hero == null || Assist.L == null) return null;
			look();
			if (Assist.foul(hp) || Assist.burning() || Assist.ambushing) return null;
			Target t;
			if ((t = horn()) != null) return t;
			if ((t = rose()) != null) return t;
			if (seen) return fight();
			if (!calm()) return null;
			if ((t = equip()) != null) return t;
			if ((t = chalice()) != null) return t;
			if ((t = sandals()) != null) return t;
			if ((t = spellbook()) != null) return t;
			if ((t = talisman()) != null) return t;
			if ((t = toolkit()) != null) return t;
			if ((t = skeletonKey()) != null) return t;
			return null;
		} catch (Throwable e) { Assist.lastError = e; return null; }
	}

	/** A use that did nothing (plan A last time, no time passed, the hero where he was: the game refused it) is not
	 *  offered again for a while: twice at the same moment and it is held back 20 turns. */
	static final Map<String, Float> noUse = new HashMap<>();
	static float lastNow = -1; static int lastPos = -1, again;
	static Target guard(Target t) {
		float now = Actor.now(); int p = Dungeon.hero == null ? -1 : Dungeon.hero.pos;
		Target last = Assist.last.isEmpty() ? null : Assist.last.get(0);
		boolean same = t != null && last != null && last.why.startsWith("art: ") && last.item == t.item && Objects.equals(last.action, t.action) && last.kind == t.kind;
		if (same && now == lastNow && p == lastPos) again++; else if (t != null) again = 0;
		lastNow = now; lastPos = p;
		noUse.values().removeIf(u -> u < now);
		if (t == null) return null;
		String k = (t.item == null ? "-" : t.item.getClass().getSimpleName()) + ":" + t.action + ":" + t.kind;
		if (again >= 2) { noUse.put(k, now + 20); again = 0; }
		return noUse.containsKey(k) ? null : t;
	}

	// =============================================================================================================
	// the misc slots: what to wear
	// =============================================================================================================

	/** What an artifact is worth worn (its kind is always shown). */
	static double artWorth(Artifact a) {
		double v;
		if (a instanceof CloakOfShadows) v = H.heroClass == HeroClass.ROGUE ? 20 : 6;
		else if (a instanceof HolyTome) v = 20;
		else if (a instanceof HornOfPlenty) v = 8 + mealWorth() / 3.0;
		else if (a instanceof ChaliceOfBlood) v = 8;
		else if (a instanceof DriedRose) v = Ghost.Quest.completed() ? 9 : 1;
		else if (a instanceof EtherealChains) v = 6;
		else if (a instanceof TimekeepersHourglass) v = 6;
		else if (a instanceof TalismanOfForesight) v = 5;
		else if (a instanceof UnstableSpellbook) v = 5;
		else if (a instanceof SandalsOfNature) v = 4;
		else if (a instanceof MasterThievesArmband) v = 4;
		else if (a instanceof SkeletonKey) v = 4;
		else if (a instanceof AlchemistsToolkit) v = 3;
		else v = 2;
		if (a.levelKnown) v += a.visiblyUpgraded() * 0.2;
		return v;
	}

	/** The fight rings' worth by O's fight model (GearValue's sum: the next three floors' mix and a share of the region's
	 *  boss, health lost per fight), as the share of that cost the ring saves, times RINGK. n: its effective level (+1 at
	 *  +0). Cached per hero state. */
	static final double RINGK = Assist.optInt("ARTRINGK", 40);
	static final Map<String, Double> fightMemo = new HashMap<>();
	static double fightWorth(Class<?> kind, int n) {
		Hero h = H; KindOfWeapon w = h.belongings.weapon(); Armor a = h.belongings.armor();
		String key = kind.getSimpleName() + n + ":" + h.lvl + ":" + h.STR() + ":" + Dungeon.depth + ":" + System.identityHashCode(w) + "/" + pebxel.bot.HeroView.visLevel(w) + ":" + System.identityHashCode(a) + "/" + pebxel.bot.HeroView.visLevel(a);
		Double m = fightMemo.get(key); if (m != null) return m;
		if (fightMemo.size() > 200) fightMemo.clear();
		double c0 = fightCost(h, w, a, null, 0, 0), c1;
		double extra = 0;
		if (kind == RingOfAccuracy.class) c1 = fightCost(h, w, a, f -> f.acc *= Math.pow(1.3, n), 0, 0);
		else if (kind == RingOfEvasion.class) c1 = fightCost(h, w, a, f -> f.eva *= Math.pow(1.125, n), 0, 0);
		else if (kind == RingOfFuror.class) c1 = fightCost(h, w, a, f -> f.delay /= Math.pow(1.0905, n), 0, 0);
		else if (kind == RingOfMight.class) { c1 = fightCost(h, w, a, null, n, 0); extra = 0.035 * n; }   // (and 3.5% more health a level)
		else if (kind == RingOfTenacity.class) c1 = fightCost(h, w, a, f -> f.taken *= 1 - (1 - Math.pow(0.85, n)) * (H.subClass == HeroSubClass.BERSERKER ? 0.7 : 0.5), 0, 0);   // (less the lower the health: about half its full effect over a fight)
		else c1 = c0;
		double v = c0 <= 1e-6 ? 0 : RINGK * ((c0 - c1) / c0 + extra);
		fightMemo.put(key, v);
		return v;
	}
	static double fightCost(Hero h, KindOfWeapon w, Armor a, java.util.function.Consumer<pebxel.bot.Fighter> mod, int strPlus, int unused) {
		pebxel.bot.Fighter me = pebxel.bot.HeroView.of(h, w, a, strPlus, 0, 0);
		me.hp = me.ht; me.heals = 0;
		if (mod != null) mod.accept(me);
		double c = 0, W = 0; int d = Dungeon.depth;
		for (int dd = d; dd < d + 3; dd++) for (String k : pebxel.bot.MobTable.kinds(dd)) { pebxel.bot.Fighter f = pebxel.bot.MobTable.fighter(k); if (f == null) continue; c += pebxel.bot.FightModel.duelCost(me, f); W++; }
		c = W > 0 ? c / W : 0;
		pebxel.bot.Fighter b = pebxel.bot.MobTable.fighter(pebxel.bot.MobTable.boss(d));
		if (b != null) c += pebxel.bot.Tune.BOSSW * pebxel.bot.FightModel.duelCost(me, b);
		return c;
	}

	/** What a ring of a known kind is worth (n: its effective level, +1 at +0): the fight rings by O's fight model, the
	 *  others by the guide's table. */
	static double ringWorth(Ring r, int n) {
		if (r instanceof RingOfMight || r instanceof RingOfEvasion || r instanceof RingOfAccuracy || r instanceof RingOfFuror || r instanceof RingOfTenacity) return fightWorth(r.getClass(), n);
		return ringBase(r) * (1 + 0.3 * (n - 1));
	}

	/** What a ring of a known kind is worth at +0 (the guide's table; the fight rings are measured: ringWorth). */
	static double ringBase(Ring r) {
		HeroClass c = H.heroClass; int d = Dungeon.depth;
		if (r instanceof RingOfMight) return 8;
		if (r instanceof RingOfEvasion) return 7;
		if (r instanceof RingOfHaste) return 7;
		if (r instanceof RingOfAccuracy) return 6;
		if (r instanceof RingOfFuror) return 5;
		if (r instanceof RingOfTenacity) return H.subClass == HeroSubClass.BERSERKER ? 7 : 5;
		if (r instanceof RingOfElements) return d > 15 ? 6 : 4;
		if (r instanceof RingOfEnergy) { int w = 0; for (Item i : H.belongings) if (i instanceof Wand) w++; return 3 + (c == HeroClass.MAGE || c == HeroClass.CLERIC ? 3 : 0) + Math.min(2, w / 2); }
		if (r instanceof RingOfSharpshooting) return c == HeroClass.HUNTRESS ? 7 : 2;
		if (r instanceof RingOfForce) return c == HeroClass.DUELIST ? 6 : H.belongings.weapon() == null ? 6 : 2;
		if (r instanceof RingOfArcana) { boolean en = false; Item w = H.belongings.weapon(), a = H.belongings.armor();
			if (w instanceof Weapon && w.isIdentified() && ((Weapon) w).enchantment != null) en = true;
			if (a instanceof Armor && a.isIdentified() && ((Armor) a).glyph != null) en = true;
			return en ? 5 : 2; }
		if (r instanceof RingOfWealth) return d <= 10 ? 5 : 3;
		return 4;
	}

	static boolean removeCurseAtHand() { ScrollOfRemoveCurse rc = H.belongings.getItem(ScrollOfRemoveCurse.class); return rc != null && rc.isKnown() && !Assist.blind(); }

	/** Its worth worn, as the player sees it: an unknown curse weighed at the 30% chance (less with Remove Curse to hand). */
	static double worth(KindofMisc i) {
		if (i.cursedKnown && i.cursed) return i.isEquipped(H) ? 0 : -100;
		double v;
		if (i instanceof Ring) {
			Ring r = (Ring) i;
			int lv = r.levelKnown ? r.level() : 0, n = lv >= 0 ? lv + 1 : lv;
			v = r.isKnown() ? ringWorth(r, n) : 5;
			if (!r.isIdentified() && !r.isEquipped(H)) v += 1;   // (worn a level, it shows what it is)
		} else if (i instanceof Artifact) v = artWorth((Artifact) i);
		else return -100;
		if (!i.cursedKnown && !i.isEquipped(H)) v = 0.7 * v - 0.3 * (removeCurseAtHand() ? 1 : i instanceof Ring ? 12 : 6);
		return v;
	}

	static boolean stuck(KindofMisc i) { return i != null && i.cursed && i.cursedKnown; }

	/** The best ring or artifact to put on now (into a free slot, or in place of a worse one: swapOut), or null. */
	static Target equip() {
		Belongings0 b = new Belongings0();
		Target best = null; double bg = 0;
		for (Item it : H.belongings.backpack) {
			if (!(it instanceof Ring || it instanceof Artifact)) continue;
			KindofMisc k = (KindofMisc) it;
			if (Assist.declined(k.getClass(), EquipableItem.AC_EQUIP)) continue;
			if (k instanceof Artifact && ((b.art != null && b.art.getClass() == k.getClass()) || (b.misc != null && b.misc.getClass() == k.getClass()))) continue;   // (never two alike)
			double v = worth(k);
			if (Assist.DBG) System.out.println("    art worth " + k.getClass().getSimpleName() + " " + Math.round(v * 10) / 10.0);
			if (v < 2) continue;
			KindofMisc out = null; double g;
			if (b.free(k)) g = v;
			else {
				// the slots it may take (KindofMisc.doEquip's own rule), the worst of them that can come off
				List<KindofMisc> outs = new ArrayList<>();
				if (k instanceof Ring) { if (!(b.misc instanceof Ring)) outs.add(b.art); outs.add(b.misc); outs.add(b.ring); }
				else { outs.add(b.art); outs.add(b.misc); if (!(b.misc instanceof Artifact)) outs.add(b.ring); }
				double ow = 1e9;
				for (KindofMisc o : outs) if (o != null && !stuck(o) && worth(o) < ow) { ow = worth(o); out = o; }
				if (out == null) continue;
				g = v - ow - SWAPGAIN;
			}
			if (g > bg) { bg = g; best = use(it, EquipableItem.AC_EQUIP, -1, out == null ? "wear " + it.getClass().getSimpleName() : "wear " + it.getClass().getSimpleName() + " for " + out.getClass().getSimpleName()); swapOut = out; swapIn = it; }
		}
		if (best == null) { swapOut = null; swapIn = null; }
		return best;
	}

	/** The three misc slots as they stand. */
	static final class Belongings0 {
		Artifact art = H.belongings.artifact(); KindofMisc misc = H.belongings.misc(); Ring ring = H.belongings.ring();
		boolean free(KindofMisc k) {
			if (k instanceof Artifact) return art == null || misc == null || (misc instanceof Ring && ring == null);
			return ring == null || misc == null || (misc instanceof Artifact && art == null);
		}
	}

	// =============================================================================================================
	// each artifact's own action
	// =============================================================================================================

	/** Horn of Plenty: eat from it when hungry (before rations), snack for the meal talents (K2) or when it is full,
	 *  and store spare food in it (each 300 of food a level: more and faster charges). */
	static Target horn() {
		HornOfPlenty h = worn(HornOfPlenty.class);
		if (h == null || h.cursed) return null;
		int hu = Assist.hunger(), ch = charge(h), cap = chargeCap(h);
		if (ch > 0) {
			String mw = mealWhy();
			if (mw != null && hu >= 30 && Actor.now() - snackAt >= 10 && can(h, HornOfPlenty.AC_SNACK)) { snackAt = Actor.now(); return use(h, HornOfPlenty.AC_SNACK, -1, "snack for the meal talent (" + mw + ")"); }
			if (!near && hu >= Hunger.HUNGRY && can(h, HornOfPlenty.AC_EAT)) return use(h, mealWorth() >= 5 ? HornOfPlenty.AC_SNACK : HornOfPlenty.AC_EAT, -1, "hungry: eat from the horn");
			if (calm() && ch >= cap && cap > 0 && hu >= Hunger.STARVING / 5 && can(h, HornOfPlenty.AC_SNACK)) return use(h, HornOfPlenty.AC_SNACK, -1, "the horn is full: snack");
		}
		if (calm() && can(h, HornOfPlenty.AC_STORE)) {
			int n = 0; Item pick = null; int pr = 99;
			for (Item i : H.belongings.backpack) {
				if (!(i instanceof Food) || i instanceof Blandfruit) continue;
				n += i.quantity();
				int r = i instanceof Pasty || i instanceof PhantomMeat || i instanceof MeatPie ? 0 : i instanceof MysteryMeat ? 1 : i instanceof SmallRation || i instanceof Berry || i instanceof ChargrilledMeat || i instanceof FrozenCarpaccio || i instanceof StewedMeat ? 2 : 3;
				if (r < pr) { pr = r; pick = i; }
			}
			if (n >= 2 && pick != null) { Target t = use(h, HornOfPlenty.AC_STORE, -1, "store spare food in the horn"); t.on = pick; return t; }
		}
		return null;
	}

	/** Chalice of Blood: pricked at full health, safe, while its worst prick is well under the hero's health. */
	static Target chalice() {
		ChaliceOfBlood c = worn(ChaliceOfBlood.class);
		if (c == null || !can(c, ChaliceOfBlood.AC_PRICK) || !prickSafe(H, c, false)) return null;
		return use(c, ChaliceOfBlood.AC_PRICK, -1, "prick the chalice");
	}
	static int maxPrick(int l) { return (int) Math.floor(7 + 3.5f * l * l); }
	static boolean prickSafe(Hero h, ChaliceOfBlood c, boolean now) {
		if (h.HP < h.HT || c.cursed || Dungeon.level.locked || Assist.starving()) return false;
		if (!now && !calm()) return false;
		if (now) for (Mob m : Dungeon.level.mobs) if (m.alignment == Char.Alignment.ENEMY && Dungeon.level.heroFOV[m.pos] && m.invisible <= 0) return false;
		return maxPrick(c.level()) * 100 <= (h.HP + h.shielding()) * PRICKPCT;
	}

	/** Dried Rose: the ghost called up whenever the rose is charged (it recharges only while the ghost is gone). */
	static Target rose() {
		DriedRose r = worn(DriedRose.class);
		if (r == null || !can(r, DriedRose.AC_SUMMON)) return null;
		if (adj && H.HP * 3 < H.HT) return null;
		boolean room = false; for (int k : com.watabou.utils.PathFinder.NEIGHBOURS8) { int c = hp + k; if (c >= 0 && c < Dungeon.level.length() && Actor.findChar(c) == null && (Dungeon.level.passable[c] || Dungeon.level.avoid[c])) room = true; }
		if (!room) return null;   // (no free cell beside the hero for it)
		return use(r, DriedRose.AC_SUMMON, -1, "call the ghost");
	}

	/** A foe in sight: the chains pull a shooter into reach, the armband blinds and cripples a fresh one beside the hero (at two thirds of its health or more: not one nearly dead), the
	 *  sandals' seed at a foe (or Earthroot on the hero beside one). */
	static Target fight() {
		EtherealChains ec = worn(EtherealChains.class);
		if (ec != null && !adj && can(ec, EtherealChains.AC_CAST)) {
			int ch = charge(ec); Mob best = null; int bd = 99;
			for (Mob m : Assist.mobs()) {
				if (!Assist.enemy(m) || !Assist.hunting(m) || !Assist.ranged(m) || Assist.boss(m) || m instanceof NPC || Char.hasProp(m, Char.Property.IMMOVABLE) || m instanceof Goo || m instanceof FungalSentry) continue;
				int d = Assist.distance(m.pos, hp);
				if (d < 2 || d - 1 > ch || d >= bd) continue;
				if (pullTo(m, ch) < 0) continue;
				best = m; bd = d;
			}
			if (best != null) return use(ec, EtherealChains.AC_CAST, best.pos, "chains: pull the " + best.getClass().getSimpleName() + " into reach");
		}
		MasterThievesArmband ab = worn(MasterThievesArmband.class);
		if (ab != null && adj && can(ab, MasterThievesArmband.AC_STEAL)) {
			for (Mob m : adjFoes) if (m.buff(com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Blindness.class) == null && m.HP >= 8 && m.HP * 3 >= m.HT * 2 && !(m instanceof Mimic)) return use(ab, MasterThievesArmband.AC_STEAL, m.pos, "armband: steal, blind and cripple");   // (blinded: shown on it)
		}
		SandalsOfNature sn = worn(SandalsOfNature.class);
		if (sn != null && can(sn, SandalsOfNature.AC_ROOT) && sn.curSeedEffect != null) {
			Class<?> e = sn.curSeedEffect;
			if (adj && (e == Earthroot.Seed.class || e == Starflower.Seed.class) && hunted) return self(sn, SandalsOfNature.AC_ROOT, "sandals: " + e.getEnclosingClass().getSimpleName() + " on the hero");
			if (e == Mageroyal.Seed.class && poisoned()) return self(sn, SandalsOfNature.AC_ROOT, "sandals: mageroyal, cleanse");
			if (e == Sungrass.Seed.class && !adj && H.HP * 2 < H.HT) return self(sn, SandalsOfNature.AC_ROOT, "sandals: sungrass, heal");
			if (e == Icecap.Seed.class || e == Firebloom.Seed.class || e == Sorrowmoss.Seed.class || e == Stormvine.Seed.class || e == Blindweed.Seed.class || e == Fadeleaf.Seed.class) {
				Mob t = null; int td = 99;
				for (Mob m : Assist.mobs()) {
					if (!Assist.enemy(m) || !Assist.hunting(m) || m instanceof NPC) continue;
					int d = Assist.distance(m.pos, hp); if (d > 3 || d >= td) continue;
					if ((e == Firebloom.Seed.class || e == Sorrowmoss.Seed.class) && (d < 2 || Dungeon.level.flamable[hp])) continue;
					if (e == Fadeleaf.Seed.class && !(d <= 1 && H.HP * 2 < H.HT)) continue;
					if (e != Fadeleaf.Seed.class && m.HP < 10) continue;
					t = m; td = d;
				}
				if (t != null) return use(sn, SandalsOfNature.AC_ROOT, t.pos, "sandals: " + e.getEnclosingClass().getSimpleName() + " at the " + t.getClass().getSimpleName());
			}
		}
		return null;
	}
	/** Where the chains would pull m (EtherealChains.chainEnemy's own rule: the first free cell along the chain from
	 *  the hero, reachable on foot, within the charge), or -1 if the cast would do nothing. */
	static int pullTo(Mob m, int charge) {
		Level L = Dungeon.level;
		Ballistica b = new Ballistica(hp, m.pos, Ballistica.STOP_TARGET);
		if (b.collisionPos == null || b.collisionPos != m.pos || !(L.visited[m.pos] || L.mapped[m.pos])) return -1;
		if (!reach(m.pos, hp)) return -1;
		int best = -1;
		for (int i : b.subPath(1, b.dist)) if (!L.solid[i] && Actor.findChar(i) == null && (!Char.hasProp(m, Char.Property.LARGE) || L.openSpace[i])) { best = i; break; }
		if (best < 0 || L.distance(m.pos, best) > charge || L.distance(m.pos, best) < 1) return -1;
		return best;
	}
	/** Can one walk from a to b (passable or avoided cells: the chains' test), without PathFinder's shared arrays. */
	static boolean reach(int a, int b) {
		Level L = Dungeon.level; int LEN = L.length(), W = L.width();
		boolean[] seenC = new boolean[LEN]; int[] q = new int[LEN]; int qh = 0, qt = 0; q[qt++] = a; seenC[a] = true;
		while (qh < qt) { int c = q[qh++]; if (c == b) return true;
			for (int k : com.watabou.utils.PathFinder.NEIGHBOURS8) { int n = c + k; if (n < 0 || n >= LEN || seenC[n] || Math.abs(n % W - c % W) > 1) continue;
				if (n != b && !(L.passable[n] || L.avoid[n])) continue; seenC[n] = true; q[qt++] = n; } }
		return false;
	}
	static boolean poisoned() { for (Buff b : H.buffs()) { String n = b.getClass().getSimpleName(); if (n.equals("Poison") || n.equals("Bleeding") || n.equals("Ooze") || n.equals("Weakness") || n.equals("Cripple")) return true; } return false; }

	/** Dying beside a foe: the chains pull the hero to a wall's foot well away from every foe. */
	static Target chainsAway() {
		EtherealChains ec = worn(EtherealChains.class);
		if (ec == null || H.rooted || !can(ec, EtherealChains.AC_CAST)) return null;
		int ch = charge(ec); if (ch < 3) return null;
		Level L = Dungeon.level; int LEN = L.length(); int best = -1, bs = -1;
		for (int c = 0; c < LEN; c++) {
			int d = Assist.distance(c, hp);
			if (d < 3 || d > ch || !(Assist.known[c]) || L.solid[c] || !L.passable[c] || Actor.findChar(c) != null || Assist.dist[c] >= Assist.INF || Assist.foul(c)) continue;
			boolean wall = false; for (int k : com.watabou.utils.PathFinder.NEIGHBOURS8) { int n = c + k; if (n >= 0 && n < LEN && L.solid[n]) wall = true; }
			if (!wall) continue;
			Ballistica b = new Ballistica(hp, c, Ballistica.STOP_TARGET);
			if (b.collisionPos == null || b.collisionPos != c || L.distance(hp, c) > ch) continue;
			int md = 99; for (Mob m : Assist.mobs()) if (Assist.enemy(m) && !(m instanceof NPC)) md = Math.min(md, Assist.distance(m.pos, c));
			if (md < 4) continue;
			int s = md * 4 + d - (c == L.entrance() ? -20 : 0);
			if (s > bs) { bs = s; best = c; }
		}
		return best >= 0 ? use(ec, EtherealChains.AC_CAST, best, "dying: chain away") : null;
	}

	/** Sandals of Nature: fed each seed kind they take (the last fed is the one they cast: the best kept for last);
	 *  calm and hurt, a Sungrass root on the hero. */
	static Target sandals() {
		SandalsOfNature sn = worn(SandalsOfNature.class);
		if (sn == null || sn.cursed) return null;
		if (sn.curSeedEffect == Sungrass.Seed.class && H.HP * 100 < H.HT * 60 && can(sn, SandalsOfNature.AC_ROOT) && Dungeon.level.plants.get(hp) == null) return self(sn, SandalsOfNature.AC_ROOT, "sandals: sungrass, heal");
		if (sn.curSeedEffect == Mageroyal.Seed.class && poisoned() && can(sn, SandalsOfNature.AC_ROOT)) return self(sn, SandalsOfNature.AC_ROOT, "sandals: mageroyal, cleanse");
		if (!can(sn, SandalsOfNature.AC_FEED)) return null;
		int cur = seedRank(sn.curSeedEffect);
		Item low = null; int lr = 99; boolean keeper = false;
		for (Item i : H.belongings) {
			if (!(i instanceof Plant.Seed) || !sn.canUseSeed(i)) continue;
			int r = seedRank(i.getClass());
			if (r >= cur) keeper = true;
			if (r < lr) { lr = r; low = i; }
		}
		if (low == null || !(lr >= cur || keeper)) return null;
		Target t = use(sn, SandalsOfNature.AC_FEED, -1, "feed the sandals a " + low.getClass().getEnclosingClass().getSimpleName() + " seed"); t.on = low; return t;
	}
	static int seedRank(Class<?> s) {
		if (s == null) return -1;
		if (s == Sungrass.Seed.class) return 6;
		if (s == Earthroot.Seed.class || s == Fadeleaf.Seed.class || s == Icecap.Seed.class) return 4;
		if (s == Mageroyal.Seed.class || s == Blindweed.Seed.class || s == Stormvine.Seed.class) return 3;
		if (s == Rotberry.Seed.class) return 0;
		return 2;
	}

	/** Unstable Spellbook: fed known scrolls it takes that are spare; read, calm and well, when full or early on a floor. */
	static Target spellbook() {
		UnstableSpellbook sb = worn(UnstableSpellbook.class);
		if (sb == null || sb.cursed || Assist.blind()) return null;
		if (can(sb, UnstableSpellbook.AC_ADD)) {
			boolean cw = false; for (Item i : H.belongings) if (i instanceof EquipableItem && ((EquipableItem) i).isEquipped(H) && i.visiblyCursed()) cw = true;
			for (Item i : H.belongings.backpack) {
				if (!(i instanceof Scroll) || !i.isIdentified() || i instanceof ScrollOfUpgrade) continue;
				if ((i instanceof ScrollOfTeleportation || i instanceof ScrollOfTerror || i instanceof ScrollOfMirrorImage || i instanceof ScrollOfIdentify) && i.quantity() < 2) continue;   // (one kept to read)
				if (i instanceof ScrollOfRemoveCurse && (cw || i.quantity() < 2)) continue;
				boolean takes; try { java.lang.reflect.Field f = UnstableSpellbook.class.getDeclaredField("scrolls"); f.setAccessible(true); List<?> l = (List<?>) f.get(sb); takes = l.indexOf(i.getClass()) >= 0 && l.indexOf(i.getClass()) <= 1; } catch (Throwable e) { takes = false; }
				if (!takes) continue;   // (only the next two it asks for, as its description shows)
				Target t = use(sb, UnstableSpellbook.AC_ADD, -1, "feed the spellbook a " + i.getClass().getSimpleName()); t.on = i; return t;
			}
		}
		int ch = charge(sb), cap = chargeCap(sb);
		Level L = Dungeon.level;
		boolean early = L.exit() <= 0 || !Assist.known[L.exit()];
		if (ch > 0 && (ch >= cap || early) && H.HP * 5 >= H.HT * 4 && !Assist.hungry() && can(sb, UnstableSpellbook.AC_READ)) {
			Target t = use(sb, UnstableSpellbook.AC_READ, -1, "read the spellbook");
			for (Item i : H.belongings) if (!i.isIdentified() && (i instanceof EquipableItem || i instanceof Wand)) { t.on = i; if (i instanceof EquipableItem && ((EquipableItem) i).isEquipped(H)) break; }
			return t;
		}
		return null;
	}

	/** Talisman of Foresight: scry, with a full-reach cone's charge, towards where most is still unknown. */
	static Target talisman() {
		TalismanOfForesight ts = worn(TalismanOfForesight.class);
		if (ts == null || ts.cursed || !can(ts, TalismanOfForesight.AC_SCRY)) return null;
		int ch = charge(ts);
		float reach = Math.min(5 + 2 * ts.level(), 25);
		if (ch < 3 + reach * 1.08f + 2) return null;
		Level L = Dungeon.level; int W = L.width(), Ht = L.height();
		int hx = hp % W, hy = hp / W;
		double half = Math.toRadians(Math.round(200 * Math.pow(0.92, reach)) / 2.0);
		int bestC = -1, bestN = 0;
		for (int k = 0; k < 16; k++) {
			double a = k * Math.PI / 8;
			int tx = (int) Math.round(hx + reach * Math.cos(a)), ty = (int) Math.round(hy + reach * Math.sin(a));
			tx = Math.max(0, Math.min(W - 1, tx)); ty = Math.max(0, Math.min(Ht - 1, ty));
			int tc = tx + ty * W; if (tc == hp || Assist.distance(tc, hp) < 2) continue;
			double ta = Math.atan2(ty - hy, tx - hx);
			int n = 0;
			for (int y = Math.max(0, hy - (int) reach); y <= Math.min(Ht - 1, hy + (int) reach); y++)
				for (int x = Math.max(0, hx - (int) reach); x <= Math.min(W - 1, hx + (int) reach); x++) {
					int c = x + y * W; if (Assist.known[c]) continue;
					double dd = Math.hypot(x - hx, y - hy); if (dd < 1 || dd > reach) continue;
					double da = Math.abs(Math.atan2(y - hy, x - hx) - ta); if (da > Math.PI) da = 2 * Math.PI - da;
					if (da <= half) n++;
				}
			if (n > bestN) { bestN = n; bestC = tc; }
		}
		if (bestC < 0 || bestN < 15) return null;
		return use(ts, TalismanOfForesight.AC_SCRY, bestC, "scry the unknown (" + bestN + " cells)");
	}

	/** Alchemist's Toolkit: spare alchemy energy put into it (each 6 a level), some kept back for brewing. */
	static Target toolkit() {
		AlchemistsToolkit tk = worn(AlchemistsToolkit.class);
		if (tk == null || tk.cursed || Dungeon.energy < 6 + ENERGYKEEP || !can(tk, AlchemistsToolkit.AC_ENERGIZE)) return null;
		return use(tk, AlchemistsToolkit.AC_ENERGIZE, -1, "energize the toolkit");
	}

	/** Skeleton Key: a locked door, crystal door or locked chest of this floor with no key for it: the key, from beside it. */
	static Target skeletonKey() {
		SkeletonKey sk = worn(SkeletonKey.class);
		if (sk == null || sk.cursed || !can(sk, SkeletonKey.AC_INSERT)) return null;
		Level L = Dungeon.level; int ch = charge(sk), LEN = L.length(), d = Dungeon.depth;
		boolean iron = Notes.keyCount(new IronKey(d)) > 0, gold = Notes.keyCount(new GoldenKey(d)) > 0, crystal = Notes.keyCount(new CrystalKey(d)) > 0;
		int best = -1, bw = Assist.INF;
		for (int c = 0; c < LEN; c++) {
			if (!Assist.known[c]) continue;
			boolean want = false;
			if (L.map[c] == Terrain.LOCKED_DOOR && !iron && ch >= 1 && !L.locked && Dungeon.branch == 0) want = true;
			else if (L.map[c] == Terrain.CRYSTAL_DOOR && !crystal && ch >= 5) want = true;
			else { Heap h = L.heaps.get(c); if (h != null && h.seen && h.type == Heap.Type.LOCKED_CHEST && !gold && ch >= 2) want = true; }
			if (!want) continue;
			if (Assist.adjacent(c, hp)) return use(sk, SkeletonKey.AC_INSERT, c, "skeleton key: open it");
			for (int k : com.watabou.utils.PathFinder.NEIGHBOURS8) { int n = c + k; if (n >= 0 && n < LEN && Assist.dist[n] < bw && Assist.open[n] && Actor.findChar(n) == null) { bw = Assist.dist[n]; best = n; } }
		}
		if (best >= 0 && bw < 40) { Target t = new Target(best, Assist.GO, "art: to the lock, for the skeleton key"); return t; }
		return null;
	}
}
