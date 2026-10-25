package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.ArmorAbility;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.spells.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.NPC;
import com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.KingsCrown;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClassArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HolyTome;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HornOfPlenty;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.food.MysteryMeat;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.*;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.*;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import java.util.*;
import pebxel.Assist.Target;
import pebxel.bot.FightModel;
import pebxel.bot.FoeView;
import pebxel.bot.HeroView;

/**
 * The Assistant's skill: talents that need decisions, subclass buttons, weapon and armour abilities, Cleric spells (agent K2).
 * Same package as Assist, so its helpers are at hand. Everything is unlocked by what the hero has (talents, subclass,
 * ability, gear), so any build a player picked is played; only what the player sees is read (the foes in sight, their
 * health bars, the hero's own buffs and charges). A foe is "strong" when dueling it would cost the hero STRONG percent
 * (default 30) of his health or more (FightModel.duelCost from what is shown), or it is a boss.
 *
 * Entries are USE entries: a real item action (a weapon's or the class armour's ABILITY, a food's EAT, the bow's SHOOT,
 * the Crown's WEAR) or one of ours, "ab:..." on a carrier item (the armour for Berserk, the weapon for a combo move,
 * the tome for a spell), which {@link #act} carries out. The carrier gives the watch an icon and passing one over
 * declines it as any item use (Assist.decline). Their whys start "ab:<key>:" (counted per game by BotStats).
 * {@link #urgent} goes before the hands (dying, a meal before the fight, the openers), {@link #suggest} after them.
 * {@link #mealWhy} / {@link #mealWorth}: the meal talents, for AsArtifacts' Horn of Plenty (agent K1; by reflection).
 * Env AS_ABIL=0 turns all of it off; AS_STRONG=n the strong foe's share.
 */
final class AsAbilities {
	private AsAbilities() {}

	static final boolean ON = Assist.opt("ABIL", true);
	static final int STRONG = Assist.optInt("STRONG", 30);
	static final boolean SNEAKSLEEP = Assist.opt("SNEAKSLEEP", true), SNEAKUP = Assist.opt("SNEAKUP", true), OPENERS = Assist.opt("OPENERS", true);
	public static final String AB = "ab:";

	// ---- per turn ----
	static Hero H; static Level L; static int hp; static float hpf;
	static final List<Mob> foes = new ArrayList<>(), near = new ArrayList<>(), adj = new ArrayList<>();
	static final Map<Mob, Double> costs = new IdentityHashMap<>();
	static double fightCost; static boolean anyStrong;
	static float prepAt = -1; static int prepPos = -1, prepBans; static Level prepLvl; static int prepHP = -1;
	static pebxel.bot.Fighter me;
	// ---- what was tried: an entry that did nothing (no time, nowhere moved) is held back a while ----
	static String actKey; static float actT = -1; static int actPos = -1;
	static final Map<String, Float> ban = new HashMap<>();
	static int waitId = -1, waits; static int foodSeen = -1; static float ateAt = -99;

	static boolean prep() {
		H = Dungeon.hero; L = Dungeon.level;
		if (H == null || L == null || !H.isAlive() || !Assist.setupOk(L)) return false;
		float now = Actor.now();
		if (actKey != null) { if (actT == now && actPos == H.pos && H.ready) ban.put(actKey, now + 20); actKey = null; }   // (it did nothing: held back a while)
		if (prepAt == now && prepPos == H.pos && prepLvl == L && prepHP == H.HP && ban.size() == prepBans) return true;
		if (prepLvl != L) { ban.clear(); waitId = -1; }
		prepAt = now; prepPos = H.pos; prepLvl = L; prepHP = H.HP; prepBans = ban.size();
		hp = H.pos; hpf = H.HP / (float) Math.max(1, H.HT);
		ban.values().removeIf(t -> t < now);
		if (Statistics.foodEaten != foodSeen) { if (foodSeen >= 0) ateAt = now; foodSeen = Statistics.foodEaten; }
		foes.clear(); near.clear(); adj.clear(); costs.clear(); fightCost = 0; anyStrong = false;
		try { me = HeroView.of(H); } catch (Throwable e) { me = null; }
		for (Mob m : Assist.mobs()) {
			if (!Assist.hostile(m) || m.pos < 0) continue;
			foes.add(m);
			int d = Assist.distance(m.pos, hp);
			boolean shooter = FoeView.shooter(m) && d <= 8 && Assist.line(m.pos, hp);
			if (d <= 1 || (Assist.hunting(m) && Assist.walk(m.pos) <= 8) || shooter) {
				near.add(m); fightCost += cost(m); anyStrong |= strong(m);
				if (d <= 1) adj.add(m);
			}
		}
		if (!near.isEmpty()) { if (fightHP < 0 || now - fightSeen > 10) fightHP = H.HP; fightSeen = now; }
		else if (now - fightSeen > 10) fightHP = -1;
		return true;
	}

	/** The health a duel with m would cost (what the player sees: its kind, its health bar, the hero's gear). */
	static double cost(Mob m) {
		Double c = costs.get(m);
		if (c == null) {
			try { c = me == null ? m.HP / 2.0 : FightModel.duelCost(me, FoeView.of(m, H)); } catch (Throwable e) { c = m.HP / 2.0; }
			if (c.isNaN() || c > 1e5) c = 1e5;
			costs.put(m, c);
		}
		return c;
	}
	/** Kinds the duel sums undersell (their bolts and gazes, charms, rage, explosions): strong whatever the sum says. */
	static final Set<String> NASTY = new HashSet<>(Arrays.asList("Eye", "Warlock", "Succubus", "Golem", "RipperDemon", "Scorpio", "AcidicScorpio", "Brute", "ArmoredBrute",
			"DM201", "FireElemental", "FrostElemental", "ShockElemental", "ChaosElemental", "Elemental", "SpectralNecromancer", "Necromancer", "GnollGeomancer", "CrystalGuardian", "Wraith", "TormentedSpirit"));
	static boolean strong(Mob m) { return Assist.boss(m) || NASTY.contains(m.getClass().getSimpleName()) || cost(m) >= Math.max(4, H.HP * STRONG / 100.0); }
	static Mob strongest(List<Mob> l) { Mob b = null; for (Mob m : l) if (b == null || cost(m) > cost(b)) b = m; return b; }
	static boolean losing() { return fightCost >= H.HP * 0.8; }
	/** A hard fight: a strong foe in it, its sum 60% of the hero's health, three foes or more, or it has cost a quarter of his health already. */
	static boolean strongFight() { return anyStrong || fightCost >= H.HP * 0.6 || near.size() >= 3 || hurt() >= 0.25f; }
	static float hurt() { return fightHP < 0 ? 0 : (fightHP - H.HP) / (float) Math.max(1, H.HT); }
	static int fightHP = -1; static float fightSeen = -99;

	static boolean ok(Item it, String action) { return it != null && !Assist.declined(it.getClass(), action) && !ban.containsKey(key(it, action)); }
	static String key(Item it, String action) { return (it == null ? "-" : it.getClass().getSimpleName()) + ":" + action; }
	static Target ab(Item carrier, String action, int cell, String why) {
		if (!ok(carrier, action)) return null;
		return Assist.use(carrier, action, cell, AB + why);
	}
	static Item carrier() { Item i = H.belongings.weapon(); if (i == null) i = H.belongings.armor(); if (i == null) i = H.belongings.artifact(); return i; }
	static boolean has(Class<? extends Buff> c) { return H.buff(c) != null; }

	// =============================================================================================================
	// the hooks
	// =============================================================================================================

	/** Before the hands: what can't wait for them (a death save, a heal, a meal or an opener before the blows start). */
	static Target urgent(Target planA) { return offered(urgent0(planA), 0); }
	static Target suggest(Target planA) { return offered(suggest0(planA), 1); }
	/** The same entry offered again and again with no time passing (acted on, it did nothing: a weapon ability out of
	 *  range...): held back a while. */
	static Target offered(Target t, int hook) {
		if (t == null || t.item == null) return t;
		String k = key(t.item, t.action); float now = Actor.now(); int p = Dungeon.hero.pos;
		if (k.equals(offerKey[hook]) && now == offerT[hook] && p == offerPos[hook]) { if (++offerN[hook] >= 3) { ban.put(k, now + 20); offerKey[hook] = null; return null; } }
		else { offerKey[hook] = k; offerT[hook] = now; offerPos[hook] = p; offerN[hook] = 0; }
		return t;
	}
	static final String[] offerKey = new String[2]; static final float[] offerT = new float[2]; static final int[] offerPos = new int[2], offerN = new int[2];

	static Target urgent0(Target planA) {
		if (!ON) return null;
		try {
			if (!prep()) return null;
			boolean hazard = Assist.foul(hp) || Assist.burning();
			Target t;
			if ((t = cleanse(hazard)) != null) return t;
			if (hazard) return null;   // (out of the gas / fire first)
			if ((t = berserk()) != null) return t;
			if ((t = layOnHands()) != null) return t;
			if ((t = escapeAbility()) != null) return t;
			if ((t = meal()) != null) return t;
			// (the fight's own: before the hands' throws and zaps, which would otherwise always come first; not when the
			// hands are about to drink Healing)
			PotionOfHealing ph = H.belongings.getItem(PotionOfHealing.class);
			if (ph != null && ph.isKnown() && hpf * 100 < Assist.HEALBOSS) return null;
			if ((t = openers()) != null) return t;
			if ((t = armorAbility()) != null) return t;
			if ((t = combo()) != null) return t;
			if ((t = monk()) != null) return t;
			if ((t = weaponAbility()) != null) return t;
			if ((t = meleeSpells()) != null) return t;
			if ((t = snipe()) != null) return t;
			if ((t = preparation()) != null) return t;
			return null;
		} catch (Throwable e) { Assist.lastError = e; return null; }
	}

	/** After the hands: a suggestion for the hero's hands this turn, or null. planA: the list's first entry so far (may be null). */
	static Target suggest0(Target planA) {
		if (!ON) return null;
		try {
			if (!prep()) return null;
			if (Assist.foul(hp) || Assist.burning()) return null;
			Target t;
			if (foes.isEmpty() && (t = crown()) != null) return t;
			if ((t = battery()) != null) return t;
			if ((t = liquidWillpower()) != null) return t;
			if ((t = pointBlank()) != null) return t;
			if ((t = freerun(planA)) != null) return t;
			if ((t = holdFast(planA)) != null) return t;
			if ((t = sneakAttack()) != null) return t;
			return null;
		} catch (Throwable e) { Assist.lastError = e; return null; }
	}

	/** Is this entry one of ours to carry out ({@link #act})? */
	static boolean mine(Target t) { return t != null && t.kind == Assist.USE && t.action != null && t.action.startsWith(AB); }

	/** Carry out one of ours. player: the watch's Select (an aim is still taken: it's what was offered). */
	static boolean act(Target t, boolean player) {
		Hero h = Dungeon.hero; if (h == null || !h.isAlive() || !h.ready) return false;
		String a = t.action.substring(AB.length());
		actKey = key(t.item, t.action); actT = Actor.now(); actPos = h.pos;
		try {
			if (a.equals("berserk")) { Berserk b = h.buff(Berserk.class); if (b == null) return false; b.doAction(); return true; }
			if (a.startsWith("combo:")) { Combo c = h.buff(Combo.class); if (c == null) return false; Combo.ComboMove mv = Combo.ComboMove.valueOf(a.substring(6)); if (!c.canUseMove(mv)) return false; c.useMove(mv); aim(t); return true; }
			if (a.equals("prep")) { Preparation p = h.buff(Preparation.class); if (p == null) return false; p.doAction(); aim(t); return true; }
			if (a.equals("freerun")) { Momentum m = h.buff(Momentum.class); if (m == null) return false; m.doAction(); return true; }
			if (a.equals("snipe")) { SnipersMark s = h.buff(SnipersMark.class); if (s == null) return false; s.doAction(); return true; }
			if (a.startsWith("monk:")) {
				MonkEnergy me = h.buff(MonkEnergy.class); int i = Integer.parseInt(a.substring(5));
				if (me == null || i < 0 || i >= MonkEnergy.MonkAbility.abilities.length) return false;
				MonkEnergy.MonkAbility ma = MonkEnergy.MonkAbility.abilities[i];
				if (!ma.usable(me)) return false;
				ma.doAbility(h, ma.targetingPrompt() != null ? t.cell : null); return true;
			}
			if (a.startsWith("spell:")) {
				HolyTome tome = h.belongings.getItem(HolyTome.class); ClericSpell sp = spell(a.substring(6));
				if (tome == null || sp == null || !tome.canCast(h, sp)) return false;
				sp.onCast(tome, h); aim(t); return true;
			}
			if (a.equals("battery")) {
				if (!(t.item instanceof MagesStaff)) return false;
				t.item.execute(h, MagesStaff.AC_ZAP);
				if (Controls.aiming()) GameScene.handleCell(h.pos);
				return true;
			}
			if (a.equals("wait")) { h.rest(false); return true; }
			if (a.equals("sneak")) { if (t.cell < 0) return false; GameScene.handleCell(t.cell); return true; }
		} catch (Throwable e) { Assist.lastError = e; return false; }
		return false;
	}
	static void aim(Target t) { for (int i = 0; i < 2 && Controls.aiming(); i++) GameScene.handleCell(t.cell >= 0 ? t.cell : Dungeon.hero.pos); }

	// =============================================================================================================
	// the meal talents (and the Horn of Plenty: a one-charge snack fires them in full, Talent.onFoodEaten)
	// =============================================================================================================

	static float eatTime() {
		return H.hasTalent(Talent.IRON_STOMACH) || H.hasTalent(Talent.ENERGIZING_MEAL) || H.hasTalent(Talent.MYSTICAL_MEAL) || H.hasTalent(Talent.INVIGORATING_MEAL)
				|| H.hasTalent(Talent.FOCUSED_MEAL) || H.hasTalent(Talent.ENLIGHTENING_MEAL) ? Food.TIME_TO_EAT - 2 : Food.TIME_TO_EAT;
	}
	static boolean hasWand() { if (H.belongings.weapon() instanceof MagesStaff) return true; for (Item i : H.belongings.backpack) if (i instanceof Wand) return true; return false; }
	static boolean chargedArtifact() {
		for (Item i : new Item[]{ H.belongings.artifact(), H.belongings.misc() }) if (i instanceof Artifact && !(i instanceof HornOfPlenty) && ((Artifact) i).isEquipped(H)) return true;
		return H.hasTalent(Talent.LIGHT_CLOAK) && H.belongings.getItem(com.shatteredpixel.shatteredpixeldungeon.items.artifacts.CloakOfShadows.class) != null;
	}
	static boolean tomeShort() { HolyTome t = H.belongings.getItem(HolyTome.class); Object c = t == null ? null : Assist.field(t, "charge"), cap = t == null ? null : Assist.field(t, "chargeCap"); return c instanceof Integer && cap instanceof Integer && (Integer) c < (Integer) cap; }
	static boolean duelistShort() { MeleeWeapon.Charger c = H.buff(MeleeWeapon.Charger.class); return c != null && c.charges < c.chargeCap(); }

	/** The meal talents' worth for the fight ahead, now (those whose effect is already on count nothing). */
	static double mealFight(boolean chargesToo) {
		double v = 0;
		int p;
		if ((p = H.pointsInTalent(Talent.EMPOWERING_MEAL)) > 0 && hasWand() && !has(WandEmpower.class)) v += p;
		if ((p = H.pointsInTalent(Talent.ENERGIZING_MEAL)) > 0 && hasWand() && chargesToo) v += p;
		if ((p = H.pointsInTalent(Talent.MYSTICAL_MEAL)) > 0 && chargedArtifact() && chargesToo) v += p;
		if ((p = H.pointsInTalent(Talent.INVIGORATING_MEAL)) > 0 && !has(Haste.class)) v += 0.5 * p;
		if ((p = H.pointsInTalent(Talent.STRENGTHENING_MEAL)) > 0 && !has(PhysicalEmpower.class)) v += p;
		if ((p = H.pointsInTalent(Talent.FOCUSED_MEAL)) > 0) { if (H.heroClass == HeroClass.DUELIST) { if (duelistShort() && chargesToo) v += p; } else if (!has(PhysicalEmpower.class)) v += 0.5 * p; }
		if ((p = H.pointsInTalent(Talent.SATIATED_SPELLS)) > 0) { if (H.heroClass == HeroClass.CLERIC) { if (!has(Talent.SatiatedSpellsTracker.class)) v += 0.5 * p; } else if (!has(Barrier.class)) v += p; }
		if ((p = H.pointsInTalent(Talent.ENLIGHTENING_MEAL)) > 0 && chargesToo) { if (H.heroClass == HeroClass.CLERIC) { if (tomeShort()) v += p; } else if (hasWand() || chargedArtifact()) v += 0.5 * p; }
		return v;
	}

	/** How much the hero's meal talents are worth in this build, 0..10 (the Horn of Plenty doubles it: every
	 *  one-charge snack fires them). For AsArtifacts (keep Horn charges for them, store food) and the planner. */
	public static int mealWorth() {
		try {
			Hero h = Dungeon.hero; if (h == null) return 0;
			double v = 0;
			v += 0.5 * h.pointsInTalent(Talent.HEARTY_MEAL) + 0.5 * h.pointsInTalent(Talent.IRON_STOMACH) + 0.5 * h.pointsInTalent(Talent.INVIGORATING_MEAL);
			v += h.pointsInTalent(Talent.EMPOWERING_MEAL) + h.pointsInTalent(Talent.ENERGIZING_MEAL) + h.pointsInTalent(Talent.MYSTICAL_MEAL)
					+ h.pointsInTalent(Talent.STRENGTHENING_MEAL) + h.pointsInTalent(Talent.FOCUSED_MEAL) + h.pointsInTalent(Talent.SATIATED_SPELLS) + h.pointsInTalent(Talent.ENLIGHTENING_MEAL);
			HornOfPlenty horn = h.belongings.getItem(HornOfPlenty.class);
			if (horn != null && !horn.cursed) v *= 2;
			return (int) Math.min(10, Math.round(v));
		} catch (Throwable e) { return 0; }
	}

	/** Why eating now is worth it for the hero's meal talents, or null: a fight coming (a foe after the hero, far enough
	 *  off to eat first; worth the meal when it's a strong one or the fight costs a quarter of the hero's health), or
	 *  Hearty Meal's heal at a third of health with none beside him (Iron Stomach: even then). Only what the player sees.
	 *  The food to eat is the caller's choice (AsArtifacts: the Horn's snack; {@link #meal}: a ration that wouldn't be
	 *  wasted). */
	public static String mealWhy() {
		try {
			if (!ON || !prep()) return null;
			boolean iron = H.hasTalent(Talent.IRON_STOMACH);
			if (!adj.isEmpty() && !iron) return null;
			if (H.hasTalent(Talent.HEARTY_MEAL) && hpf < 0.334f && (!near.isEmpty() || Actor.now() - ateAt > 5)) return "hearty meal: low";
			if (near.isEmpty() || !(anyStrong || fightCost >= H.HP * 0.25)) return null;
			int closest = Assist.INF; for (Mob m : near) if (Assist.hunting(m)) closest = Math.min(closest, Assist.walk(m.pos));
			if (adj.isEmpty() && closest < eatTime() + 1) return null;
			double v = mealFight(Actor.now() - ateAt > 15);
			if (v < 1) return null;
			return "meal before the fight";
		} catch (Throwable e) { return null; }
	}

	/** The meal itself when the Horn can't: the Horn's snack if K1's rule didn't offer it, else a food from the pack that
	 *  the hero is hungry enough for that little of it is lost (Thomas: "keep a food item for it"). */
	static Target meal() {
		String why = mealWhy(); if (why == null) return null;
		HornOfPlenty horn = H.belongings.getItem(HornOfPlenty.class);
		Object hc = horn == null ? null : Assist.field(horn, "charge");
		if (horn != null && horn.isEquipped(H) && !horn.cursed && hc instanceof Integer && (Integer) hc > 0) {
			Target t = ab0(horn, HornOfPlenty.AC_SNACK, "meal:horn: " + why); if (t != null) return t;
		}
		int hunger = Assist.hunger(), slack = why.startsWith("hearty") ? 150 : 60;
		Item best = null; float be = 1e9f;
		for (Item i : H.belongings.backpack) {
			if (!Assist.foodItem(i) || i instanceof MysteryMeat) continue;
			float e = ((Food) i).energy;
			if (hunger + slack >= e && e < be) { be = e; best = i; }
		}
		return best == null ? null : ab0(best, Food.AC_EAT, "meal:" + (why.startsWith("hearty") ? "hearty" : "food") + ": " + why);
	}
	/** A real item action of ours (not an "ab:" one), declinable as any. */
	static Target ab0(Item it, String action, String why) { return ok(it, action) ? Assist.use(it, action, -1, AB + why) : null; }

	// =============================================================================================================
	// the Warrior
	// =============================================================================================================

	/** Berserk (the Berserker's button, at full rage): its shield is bigger the lower his health; when low and in a
	 *  losing fight, a boss beside him, or surrounded. With Deathless Fury it fires by itself at death: later. */
	static Target berserk() {
		Berserk b = H.buff(Berserk.class); if (b == null || adj.isEmpty()) return null;
		Object pw = Assist.field(b, "power"), st = Assist.field(b, "state");
		if (!(pw instanceof Float) || (Float) pw < 1f || st == null || !st.toString().equals("NORMAL")) return null;
		com.shatteredpixel.shatteredpixeldungeon.items.BrokenSeal.WarriorShield ws = H.buff(com.shatteredpixel.shatteredpixeldungeon.items.BrokenSeal.WarriorShield.class);
		if (ws == null || ws.maxShield() <= 0) return null;
		boolean bossAdj = false; for (Mob m : adj) bossAdj |= Assist.boss(m);
		// (rage decays once the fight is over and the shield is bigger the lower his health: spent in the fight it came
		// from, once it's a hard one; with Deathless Fury it fires by itself at death, so then later)
		boolean df = H.hasTalent(Talent.DEATHLESS_FURY);
		boolean go = df ? (hpf < 0.35f && (strongFight() || adj.size() >= 2)) || (bossAdj && hpf < 0.6f)
				: strongFight() || hpf < 0.6f || adj.size() >= 2 || bossAdj;
		if (!go) return null;
		Item c = H.belongings.armor() != null ? H.belongings.armor() : carrier();
		return ab(c, AB + "berserk", -1, "berserk: rage at full, " + (bossAdj ? "a boss" : adj.size() >= 2 ? "surrounded" : "low"));
	}

	/** The Gladiator's combo moves: Fury and Crush on a strong foe, any move before the combo runs out, Slam when low. */
	static Target combo() {
		Combo c = H.buff(Combo.class); if (c == null || adj.isEmpty()) return null;
		Mob e = strongest(adj); int n = c.getComboCount();
		Object tf = Assist.field(c, "comboTime"); float left = tf instanceof Float ? (Float) tf : 5;
		boolean expiring = left <= 1.5f, st = strong(e);
		Combo.ComboMove mv = null;
		if (n >= 10 && c.canUseMove(Combo.ComboMove.FURY) && (st || expiring || n >= 12)) mv = Combo.ComboMove.FURY;
		else if (n >= 8 && c.canUseMove(Combo.ComboMove.CRUSH) && (st || expiring)) mv = Combo.ComboMove.CRUSH;
		else if (n >= 4 && c.canUseMove(Combo.ComboMove.SLAM) && (expiring || (st && hpf < 0.5f))) mv = Combo.ComboMove.SLAM;
		else if (n >= 2 && expiring && c.canUseMove(Combo.ComboMove.CLOBBER) && st && hpf < 0.4f) mv = Combo.ComboMove.CLOBBER;
		if (mv == null) return null;
		return ab(carrier(), AB + "combo:" + mv.name(), e.pos, "combo: " + mv.name().toLowerCase(Locale.ROOT) + " (" + n + ")");
	}

	/** Liquid Willpower: a potion drunk is a shield; a known harmless one drunk in a hard fight. */
	static Target liquidWillpower() {
		if (!H.hasTalent(Talent.LIQUID_WILLPOWER) || adj.isEmpty() || hpf > 0.6f || !strongFight()) return null;
		for (Class<? extends Potion> c : Arrays.asList(PotionOfHaste.class, PotionOfMindVision.class, PotionOfLevitation.class, PotionOfPurity.class)) {
			Potion p = H.belongings.getItem(c); if (p != null && p.isKnown()) { Target t = ab0(p, Potion.AC_DRINK, "liquidwill: a spare potion for the shield"); if (t != null) return t; }
		}
		return null;
	}

	/** Hold Fast (armour while standing still) and Patient Strike (a waited turn adds to the next blow): one coming,
	 *  two steps off, is waited for rather than walked into. Twice at most for one foe. */
	static Target holdFast(Target planA) {
		boolean hf = H.hasTalent(Talent.HOLD_FAST) && (H.heroClass != HeroClass.WARRIOR || has(com.shatteredpixel.shatteredpixeldungeon.items.BrokenSeal.WarriorShield.class));
		boolean ps = H.hasTalent(Talent.PATIENT_STRIKE);
		if ((!hf && !ps) || !adj.isEmpty() || near.isEmpty() || Assist.inDanger) return null;
		if (planA != null && (Assist.WAIT_ACT.equals(planA.action) || (planA.kind == Assist.USE && planA.cell != hp))) return null;   // (already waiting, or a shot / zap first)
		Mob c = null;
		for (Mob m : near) {
			if (FoeView.shooter(m)) return null;   // (a shooter doesn't come: no waiting in its line)
			if (Assist.hunting(m) && Assist.distance(m.pos, hp) == 2 && Assist.walk(m.pos) <= 2 && m.speed() <= 1f) c = m;
		}
		if (c == null) return null;
		if (c.id() != waitId) { waitId = c.id(); waits = 0; }
		if (++waits > 2) return null;
		Item k = hf ? H.belongings.armor() : H.belongings.weapon(); if (k == null) k = carrier();
		return ab(k, AB + "wait", -1, (hf ? "holdfast" : "patient") + ": let it come");
	}

	// =============================================================================================================
	// the Rogue
	// =============================================================================================================

	/** The Assassin's blink-strike: from out of reach with preparation built up, when it would kill or hits hard. */
	static Target preparation() {
		Preparation p = H.buff(Preparation.class); if (p == null || !adj.isEmpty()) return null;
		int lvl = p.attackLevel(); int blink = Preparation.AttackLevel.values()[Math.max(0, Math.min(3, lvl - 1))].blinkDistance();
		if (blink <= 0) return null;
		Mob best = null;
		for (Mob m : foes) {
			int d = Assist.distance(m.pos, hp); if (d < 2 || d > blink + 1 || m instanceof NPC) continue;
			boolean free = false; for (int k : com.watabou.utils.PathFinder.NEIGHBOURS8) { int c = m.pos + k; if (c >= 0 && c < L.length() && L.passable[c] && Actor.findChar(c) == null && Assist.walk(c) <= blink) free = true; }
			if (!free) continue;
			if (p.canKO(m) || lvl >= 3 || (strong(m) && lvl >= 2)) if (best == null || cost(m) > cost(best)) best = m;
		}
		if (best == null) return null;
		return ab(carrier(), AB + "prep", best.pos, "prep: blink and strike (level " + lvl + (p.canKO(best) ? ", a kill" : "") + ")");
	}

	/** The Freerunner: freerun to get away when low and chased (plan A walks off). */
	static Target freerun(Target planA) {
		Momentum mo = H.buff(Momentum.class); if (mo == null || planA == null || planA.kind != Assist.GO || hpf >= 0.4f) return null;
		Object s = Assist.field(mo, "momentumStacks"), ft = Assist.field(mo, "freerunTurns"), cd = Assist.field(mo, "freerunCooldown");
		if (!(s instanceof Integer) || (Integer) s < 2 || !Integer.valueOf(0).equals(ft) || !Integer.valueOf(0).equals(cd)) return null;
		boolean chased = false; for (Mob m : near) chased |= Assist.hunting(m) && Assist.distance(m.pos, hp) <= 3;
		return chased ? ab(carrier(), AB + "freerun", -1, "freerun: away") : null;
	}

	/** A sleeper struck first is a surprise blow (the Rogue's sneak attacks; Silent Steps keeps it asleep longer). */
	static Target sneakAttack() {
		if (!SNEAKSLEEP || H.heroClass != HeroClass.ROGUE || !foes.isEmpty() || hpf < 0.6f) return null;
		Mob best = null;
		for (Mob m : Assist.mobs()) {
			if (!Assist.enemy(m) || m.state != m.SLEEPING || Assist.boss(m) || m instanceof NPC || m.id() == Assist.vetoId) continue;
			if (Assist.walk(m.pos) > 6 || cost(m) >= H.HP * (H.invisible > 0 ? 0.5 : 0.25)) continue;
			if (best == null || Assist.walk(m.pos) < Assist.walk(best.pos)) best = m;
		}
		return best == null ? null : ab(carrier(), AB + "sneak", best.pos, "sneak: a sleeper, a surprise blow");
	}

	// =============================================================================================================
	// the Huntress
	// =============================================================================================================

	/** Point Blank: with 2+ points the bow beside a foe is no worse than at range: shot when it outdoes the melee blow. */
	static Target pointBlank() {
		int pb = H.pointsInTalent(Talent.POINT_BLANK); if (pb < 1 || adj.isEmpty()) return null;
		SpiritBow bow = H.belongings.getItem(SpiritBow.class); if (bow == null) return null;
		Mob e = strongest(adj);
		double b = (bow.min() + bow.max()) / 2.0 * (0.5 + 0.25 * pb), m = 1.5;   // (beside: accuracy x0.5, +0.25 a point; MissileWeapon.adjacentAccFactor)
		KindOfWeapon w = H.belongings.weapon();
		if (w != null) { try { m = (w.min() + w.max()) / 2.0 / Math.max(0.3f, w.delayFactor(H)); } catch (Throwable x) { } }
		if (H.subClass == HeroSubClass.SNIPER) b *= 1.2;   // (its shots pierce armour)
		if (b < m * 0.95) return null;
		Target t = ab0(bow, SpiritBow.AC_SHOOT, "pointblank: shoot beside"); if (t != null) t.cell = e.pos;
		return t;
	}

	/** The Sniper's special shot, after a thrown or shot hit marked the foe. */
	static Target snipe() {
		SnipersMark s = H.buff(SnipersMark.class); if (s == null) return null;
		Char c = (Char) Actor.findById(s.object);
		if (!(c instanceof Mob) || !c.isAlive() || !L.heroFOV[c.pos] || H.belongings.getItem(SpiritBow.class) == null) return null;
		return ab(H.belongings.getItem(SpiritBow.class), AB + "snipe", c.pos, "snipe: the special shot");
	}

	// =============================================================================================================
	// the Mage
	// =============================================================================================================

	/** Shield Battery: the staff zapped at himself turns its charges into a shield; low beside a foe, no Healing. */
	static Target battery() {
		if (!H.hasTalent(Talent.SHIELD_BATTERY) || adj.isEmpty() || hpf >= 0.3f || !(H.belongings.weapon() instanceof MagesStaff)) return null;
		PotionOfHealing ph = H.belongings.getItem(PotionOfHealing.class); if (ph != null && ph.isKnown()) return null;
		MagesStaff st = (MagesStaff) H.belongings.weapon(); Object w = Assist.field(st, "wand");
		if (!(w instanceof Wand) || !((Wand) w).curChargeKnown || ((Wand) w).curCharges < 2) return null;
		return ab(st, AB + "battery", -1, "battery: charges into a shield");
	}

	// =============================================================================================================
	// the Duelist
	// =============================================================================================================

	static final Set<String> CLEAVE = new HashSet<>(Arrays.asList("WornShortsword", "Shortsword", "Sword", "Longsword", "Greatsword")),
			HEAVY = new HashSet<>(Arrays.asList("Mace", "HandAxe", "BattleAxe", "WarHammer", "Cudgel")),
			LUNGE = new HashSet<>(Arrays.asList("Rapier", "Katana")), SPIKE = new HashSet<>(Arrays.asList("Spear", "Glaive")),
			SNEAK = new HashSet<>(Arrays.asList("Dagger", "Dirk", "AssassinsBlade")), COMBO = new HashSet<>(Arrays.asList("Sai", "Gloves", "Gauntlet")),
			HARVEST = new HashSet<>(Arrays.asList("Sickle", "WarScythe")), GUARD = new HashSet<>(Arrays.asList("RoundShield", "Greatshield")),
			STANCE = new HashSet<>(Arrays.asList("Quarterstaff", "Scimitar")), HIT = new HashSet<>(Arrays.asList("Greataxe", "RunicBlade"));

	/** The Duelist's weapon ability, by the weapon's kind: on strong foes, and on any once the charges are full (they'd
	 *  go to waste); the Sneak kinds to get away when dying; a free Cleave after a Cleave kill always. */
	static Target weaponAbility() {
		if (H.heroClass != HeroClass.DUELIST || !(H.belongings.weapon() instanceof MeleeWeapon) || foes.isEmpty()) return null;
		MeleeWeapon w = (MeleeWeapon) H.belongings.weapon();
		if (w instanceof MagesStaff || w.STRReq() > H.STR() || !ok(w, MeleeWeapon.AC_ABILITY)) return null;
		MeleeWeapon.Charger ch = H.buff(MeleeWeapon.Charger.class); if (ch == null) return null;
		Mob e = adj.isEmpty() ? null : strongest(adj);
		float have = ch.charges + ch.partialCharge, need = w.abilityChargeUse(H, e);
		if (have < need) return null;
		boolean full = ch.charges >= ch.chargeCap(), st = e != null && strong(e);
		String n = w.getClass().getSimpleName(); int cell = -1; String why = null;
		if (CLEAVE.contains(n)) {
			if (e != null && (need <= 0 || st || full)) { cell = e.pos; why = need <= 0 ? "cleave again: free" : "cleave"; }
		} else if (HEAVY.contains(n)) {
			if (e != null && (e.state == e.SLEEPING || H.invisible > 0 || st)) { cell = e.pos; why = "heavy blow"; }
		} else if (LUNGE.contains(n)) {
			if (adj.isEmpty()) for (Mob m : near) if (Assist.distance(m.pos, hp) == 2 && (strong(m) || full || FoeView.shooter(m)) && lungeOk(m)) { cell = m.pos; why = "lunge"; break; }
		} else if (SPIKE.contains(n)) {
			if (adj.isEmpty()) for (Mob m : near) if (Assist.distance(m.pos, hp) >= 2 && H.canAttack(m) && (strong(m) || full)) { cell = m.pos; why = "spike"; break; }
		} else if (SNEAK.contains(n)) {
			if (e != null && hpf < 0.3f && losing()) { cell = away(5, false); why = "sneak away"; }
			else if (SNEAKUP && adj.isEmpty() && H.invisible <= 0 && hpf > 0.6f) for (Mob m : near) if (strong(m) && Assist.distance(m.pos, hp) >= 2 && (cell = beside(m, 5)) >= 0) { why = "sneak up: a surprise blow"; break; }
		} else if (COMBO.contains(n)) {
			Sai.ComboStrikeTracker tr = H.buff(Sai.ComboStrikeTracker.class); int hits = tr == null ? 0 : tr.hits;
			if (e != null && (hits >= 5 || (hits >= 3 && (st || full)))) { cell = e.pos; why = "combo strike (" + hits + ")"; }
		} else if (HARVEST.contains(n)) {
			if (e != null && st && !Assist.boss(e)) { cell = e.pos; why = "harvest"; }
		} else if (GUARD.contains(n)) {
			if (e != null && st && hpf < 0.8f && !has(RoundShield.GuardTracker.class)) { cell = hp; why = "guard"; }
		} else if (STANCE.contains(n)) {
			if (e != null && st && !has(Quarterstaff.DefensiveStance.class) && !has(Scimitar.SwordDance.class)) { cell = hp; why = n.equals("Scimitar") ? "sword dance" : "defensive stance"; }
		} else if (HIT.contains(n)) {
			if (e != null && (st || full) && (!n.equals("Greataxe") || hpf < 0.5f)) { cell = e.pos; why = n.equals("Greataxe") ? "retribution" : "runic slash"; }
		} else if (n.equals("Flail")) {   // (spins while it walks up: the next blow is sure and harder per spin, 3 at most)
			Object sp = H.buff(Flail.SpinAbilityTracker.class) == null ? null : Assist.field(H.buff(Flail.SpinAbilityTracker.class), "spins");
			int spins = sp instanceof Integer ? (Integer) sp : 0;
			if (adj.isEmpty() && spins < 3) for (Mob m : near) if (Assist.hunting(m) && Assist.walk(m.pos) <= 4 && !FoeView.shooter(m) && (spins > 0 || strong(m) || full)) { cell = hp; why = "spin (" + (spins + 1) + ")"; break; }
		} else if (n.equals("Whip")) {
			int r = 0; for (Mob m : foes) if (H.canAttack(m)) r++;
			if (r >= 2 || (r >= 1 && (st || full))) { cell = hp; why = "lash"; }
		}
		if (why == null || cell < 0) return null;
		Target t = Assist.use(w, MeleeWeapon.AC_ABILITY, cell, AB + "wep:" + why + (full ? " (full)" : ""));
		return t;
	}
	/** An empty cell beside m within reach steps of the hero, or -1. */
	static int beside(Mob m, int reach) {
		int best = -1;
		for (int k : com.watabou.utils.PathFinder.NEIGHBOURS8) { int c = m.pos + k; if (c >= 0 && c < L.length() && L.passable[c] && !L.avoid[c] && L.heroFOV[c] && Actor.findChar(c) == null && Assist.walk(c) <= reach && (best < 0 || Assist.walk(c) < Assist.walk(best))) best = c; }
		return best;
	}
	static boolean lungeOk(Mob m) {
		if (H.rooted) return false;
		for (int k : com.watabou.utils.PathFinder.NEIGHBOURS8) { int c = hp + k; if (c >= 0 && c < L.length() && L.passable[c] && Actor.findChar(c) == null && Assist.distance(c, m.pos) <= 1) return true; }
		return false;
	}

	/** A cell to get away to (in sight, empty, within reach steps; line: a straight jump), farthest from the foes (3+ off), or -1. */
	static int away(int reach, boolean line) {
		int best = -1, bd = 2;
		for (int c = 0; c < L.length(); c++) {
			if (!L.heroFOV[c] || !L.passable[c] || L.avoid[c] || Actor.findChar(c) != null || Assist.distance(c, hp) > reach || Assist.danger[c]) continue;
			if (line ? new Ballistica(hp, c, Ballistica.STOP_TARGET | Ballistica.STOP_SOLID).collisionPos != c : Assist.walk(c) > reach) continue;
			int md = 99; for (Mob m : foes) md = Math.min(md, Assist.distance(m.pos, c));
			if (md > bd) { bd = md; best = c; }
		}
		return best;
	}

	/** The Monk: Dragon Kick on a strong foe, Focus when low against one, Flurry before the energy caps (it'd be lost). */
	static Target monk() {
		MonkEnergy me = H.buff(MonkEnergy.class); if (me == null || adj.isEmpty()) return null;
		Mob e = strongest(adj); boolean st = strong(e);
		MonkEnergy.MonkAbility[] ab = MonkEnergy.MonkAbility.abilities;
		int pick = -1;
		if (st && ab.length > 3 && ab[3].usable(me)) pick = 3;
		else if (st && hpf < 0.6f && ab.length > 1 && ab[1].usable(me)) pick = 1;
		else if (ab.length > 0 && ab[0].usable(me) && (st || me.energy >= me.energyCap() - 1)) pick = 0;
		if (pick < 0) return null;
		return ab(carrier(), AB + "monk:" + pick, e.pos, "monk: " + ab[pick].getClass().getSimpleName().toLowerCase(Locale.ROOT));
	}

	// =============================================================================================================
	// the Cleric (Guiding Light stays the hands' own)
	// =============================================================================================================

	static ClericSpell spell(String name) {
		for (int tier = 1; tier <= 4; tier++) for (ClericSpell s : ClericSpell.getSpellList(H == null ? Dungeon.hero : H, tier)) if (s.getClass().getSimpleName().equals(name)) return s;
		return null;
	}
	static Target cast(ClericSpell s, int cell, String why) {
		HolyTome tome = H.belongings.getItem(HolyTome.class);
		if (tome == null || tome.cursed || s == null) return null;
		try { if (!tome.canCast(H, s)) return null; } catch (Throwable e) { return null; }
		return ab(tome, AB + "spell:" + s.getClass().getSimpleName(), cell, "spell:" + why.replace(' ', '-'));
	}
	static int tomeCharge() { HolyTome t = H.belongings.getItem(HolyTome.class); Object c = t == null ? null : Assist.field(t, "charge"); return c instanceof Integer ? (Integer) c : 0; }
	static boolean blinded(Char c) { return c.buff(Blindness.class) != null; }

	static Target layOnHands() {
		if (H.heroClass != HeroClass.CLERIC || hpf >= 0.45f || near.isEmpty()) return null;
		return cast(LayOnHands.INSTANCE, hp, "lay on hands");
	}
	static Target cleanse(boolean hazard) {
		if (H.heroClass != HeroClass.CLERIC || !H.hasTalent(Talent.CLEANSE)) return null;
		int bad = 0; for (Buff b : H.buffs()) if (b.type == Buff.buffType.NEGATIVE && !(b instanceof Hunger)) bad++;
		if (bad == 0 || (hpf >= 0.8f && !Assist.burning())) return null;
		return cast(Cleanse.INSTANCE, -1, "cleanse");
	}
	/** A strong fight starting: the spells that set it up (Radiance, Holy Lance, Sunray, Bless, the Ward, the Weapon, the Aura). */
	static Target openers() {
		if (!OPENERS || H.heroClass != HeroClass.CLERIC || near.isEmpty() || !strongFight()) return null;
		Mob s = strongest(near); int ch = tomeCharge(); Target t;
		int hostile = 0; for (Mob m : foes) if (!(m instanceof NPC)) hostile++;
		if (H.subClass == HeroSubClass.PRIEST && (hostile >= 2 || (strong(s) && adj.isEmpty())) && (t = cast(Radiance.INSTANCE, -1, "radiance")) != null) return t;
		if (strong(s) && new Ballistica(hp, s.pos, Ballistica.PROJECTILE).collisionPos == s.pos && (t = cast(HolyLance.INSTANCE, s.pos, "holy lance")) != null) return t;
		if (strong(s) && !blinded(s) && new Ballistica(hp, s.pos, Ballistica.MAGIC_BOLT).collisionPos == s.pos && (t = cast(Sunray.INSTANCE, s.pos, "sunray")) != null) return t;
		if (!has(Bless.class) && (t = cast(BlessSpell.INSTANCE, hp, "bless")) != null) return t;
		if (strong(s) && !has(HolyWard.HolyArmBuff.class) && ch >= 2 && (t = cast(HolyWard.INSTANCE, -1, "holy ward")) != null) return t;
		if (strong(s) && !has(HolyWeapon.HolyWepBuff.class) && ch >= 3 && (t = cast(HolyWeapon.INSTANCE, -1, "holy weapon")) != null) return t;
		if (strong(s) && !has(AuraOfProtection.AuraBuff.class) && (t = cast(AuraOfProtection.INSTANCE, -1, "aura")) != null) return t;
		return null;
	}
	/** Beside a strong foe: Smite (the Paladin), Shield of Light. */
	static Target meleeSpells() {
		if (H.heroClass != HeroClass.CLERIC || adj.isEmpty()) return null;
		Mob e = strongest(adj); if (!strong(e)) return null; Target t;
		if (H.subClass == HeroSubClass.PALADIN && (t = cast(Smite.INSTANCE, e.pos, "smite")) != null) return t;
		if (!has(ShieldOfLight.ShieldOfLightTracker.class) && (t = cast(ShieldOfLight.INSTANCE, e.pos, "shield of light")) != null) return t;
		return null;
	}

	// =============================================================================================================
	// the armour abilities (after the King's Crown)
	// =============================================================================================================

	/** The Crown worn as soon as it's carried (the ability: the player's pick in its window). */
	static Target crown() {
		KingsCrown k = H.belongings.getItem(KingsCrown.class);
		if (k == null || H.belongings.armor() == null) return null;
		return ab0(k, "WEAR", "crown: an armour ability");
	}
	static ClassArmor classArmor() { return H.belongings.armor() instanceof ClassArmor ? (ClassArmor) H.belongings.armor() : null; }
	static boolean abilityReady(ClassArmor ca) { ArmorAbility a = H.armorAbility; return ca != null && a != null && ca.charge >= a.chargeUse(H) && ok(ca, "ABILITY"); }

	/** Dying beside a foe with no Healing: Heroic Leap or Smoke Bomb away. */
	static Target escapeAbility() {
		ClassArmor ca = classArmor(); if (!abilityReady(ca) || adj.isEmpty() || hpf >= 0.3f || !losing()) return null;
		PotionOfHealing ph = H.belongings.getItem(PotionOfHealing.class); if (ph != null && ph.isKnown()) return null;
		String n = H.armorAbility.getClass().getSimpleName(); int c = -1;
		if (n.equals("HeroicLeap")) c = away(8, true);
		else if (n.equals("SmokeBomb")) c = away(6, false);
		if (c < 0) return null;
		return Assist.use(ca, "ABILITY", c, AB + "armor:" + n + " away");
	}

	/** The armour ability in a strong fight, by its kind. */
	static Target armorAbility() {
		ClassArmor ca = classArmor(); if (!abilityReady(ca) || near.isEmpty()) return null;
		boolean spare = ca.charge >= Math.min(95, 2 * H.armorAbility.chargeUse(H));   // (two uses' worth, or nearly full: it'd go to waste)
		if (!strongFight() && !spare) return null;
		String n = H.armorAbility.getClass().getSimpleName();
		Mob s = strongest(near); Mob e = adj.isEmpty() ? null : strongest(adj);
		boolean inLine = new Ballistica(hp, s.pos, Ballistica.PROJECTILE).collisionPos == s.pos;
		int close = 0; for (Mob m : near) if (Assist.distance(m.pos, hp) <= 3) close++;
		int cell = -1;
		switch (n) {
			case "Shockwave": if (Assist.distance(s.pos, hp) <= 4 && inLine) cell = s.pos; break;
			case "Endure": if (e != null && (Assist.boss(e) || losing())) cell = hp; break;
			case "ElementalBlast": if (close >= 2 || e != null) cell = hp; break;
			case "WildMagic": if (inLine && hasWand()) cell = s.pos; break;
			case "DeathMark": case "SpectralBlades": if (inLine && (strong(s) || spare)) cell = s.pos; break;
			case "Ratmogrify": if (inLine && strong(s) && !Assist.boss(s)) cell = s.pos; break;
			case "Challenge": if (strong(s) && !Assist.boss(s) && Assist.distance(s.pos, hp) <= 5) cell = s.pos; break;
			case "ElementalStrike": if (e != null) cell = e.pos; break;
			case "NaturesPower": if (!has(com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.huntress.NaturesPower.naturesPowerTracker.class)) cell = hp; break;
			case "AscendedForm": if (!has(com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.cleric.AscendedForm.AscendBuff.class)) cell = hp; break;
			case "ShadowClone": case "SpiritHawk": {
				boolean ally = false; for (Mob m : Assist.mobs()) ally |= m.alignment == Char.Alignment.ALLY && m.getClass().getName().contains(n);
				if (!ally) { cell = hp; if (H.armorAbility.targetingPrompt() != null) for (int k : com.watabou.utils.PathFinder.NEIGHBOURS8) { int c = hp + k; if (L.passable[c] && Actor.findChar(c) == null) { cell = c; break; } } }
				break;
			}
			default: break;   // (Warp Beacon, Feint, Trinity, Power of Many, and the escapes: not in a fight's middle)
		}
		if (cell < 0) return null;
		return Assist.use(ca, "ABILITY", cell, AB + "armor:" + n);
	}
}
