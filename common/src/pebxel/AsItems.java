package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.NPC;
import com.shatteredpixel.shatteredpixeldungeon.items.*;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.*;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.*;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.brews.*;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.elixirs.*;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.*;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.*;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.*;
import com.shatteredpixel.shatteredpixeldungeon.items.spells.*;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.*;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.*;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.darts.*;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.MagicalFireRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.*;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.plants.*;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import java.util.*;
import pebxel.Assist.Target;
import pebxel.bot.FightModel;
import pebxel.bot.Identify;
import pebxel.bot.Tune;
import pebxel.bot.UpgradePlan;

/**
 * The Assistant's skill: the consumables and item mechanics the plan didn't use yet (agent K5; strategy-reference
 * chapter 6 and 8): every wand (Corruption, Transfusion, Warding, Living Earth, Regrowth, Blast Wave's knockback, the
 * Mage's staff imbue), thrown weapons and tipped darts, bombs, potions and exotics, brews and elixirs, scrolls and
 * exotics, runestones, alchemy spells, seeds thrown into a foe's path, traps (the mildest crossing, sprung from afar
 * with a thrown item, sprung on foes), known-cursed gear, and the potion puzzles of special rooms (Levitation over the
 * traps or chasm, Invisibility past piranhas or sentries, Purity through the gas, Frost on the magical fire, fire on a
 * storage barricade). The rooms' walking and other logic (altars, the pit jump) is AsQuests' / AsBasics' (agent K4).
 *
 * Identification honesty: an unknown potion or scroll is used only by its label's odds (pebxel.bot.Identify), never by
 * its class; known ones (isKnown) by kind. Wands, stones, spells, bombs, seeds and thrown weapons show their kind.
 * Foes: only those in sight, their health bars and their buffs as shown.
 *
 * {@link #urgent} (before the hands: the fight's own uses, which the hands' plain throw or zap would otherwise beat)
 * and {@link #suggest} (after the hands: the calm uses). Every entry is a plain item action (Assist.act carries it out:
 * the aim is t.cell, an item selector gets t.on). Whys start "it:<key>:" (BotStats counts them). {@link #choose}: the
 * item windows' buttons for the bot (the Augmentation's choice, the Beacon's set / return); {@link #fireOk}: Fireblast's
 * cone with nothing to burn by the hero. Env AS_ITEMUSE=0 turns it all off; AS_ITEMFIGHT=0 the fight uses only.
 */
final class AsItems {
	private AsItems() {}

	static final boolean ON = Assist.opt("ITEMUSE", true), FIGHT = Assist.opt("ITEMFIGHT", true);
	static final int SPEND = Assist.optInt("ITEMSPEND", 25);   // (K5-b, 48 paired games each: 25 over 10 +0.81 from depth 1, +0.75 from 11)   // a fight worth a dear consumable: P(death) this many percent, or a hard fight (AsAbilities)
	static final String IT = "it:";

	// ---- per turn ----
	static Hero H; static Level L; static int hp, W, LEN; static float hpf;
	static final List<Mob> foes = new ArrayList<>(), near = new ArrayList<>(), adj = new ArrayList<>();
	static double pDeath; static boolean hard, bossNear, dying;
	static float prepAt = -1; static int prepPos = -1; static Level prepL;
	static final Map<String, Float> ban = new HashMap<>();

	static boolean prep() {
		H = Dungeon.hero; L = Dungeon.level;
		if (H == null || L == null || !H.isAlive() || !Assist.setupOk(L)) return false;
		float now = Actor.now();
		if (prepAt == now && prepPos == H.pos && prepL == L) return true;
		if (prepL != L) { ban.clear(); tries.clear(); cross = null; done.clear(); }
		prepAt = now; prepPos = H.pos; prepL = L;
		hp = H.pos; W = L.width(); LEN = L.length(); hpf = H.HP / (float) Math.max(1, H.HT);
		ban.values().removeIf(t -> t < now);
		notePotions();
		foes.clear(); near.clear(); adj.clear(); pDeath = 0; hard = false; bossNear = false;
		if (!AsAbilities.prep()) return false;
		foes.addAll(AsAbilities.foes); near.addAll(AsAbilities.near); adj.addAll(AsAbilities.adj);
		for (Mob m : near) bossNear |= Assist.boss(m);
		if (!near.isEmpty()) {
			try { FightModel.Result r = Assist.oFight(); pDeath = r == null ? 0 : r.pDeath; } catch (Throwable e) { pDeath = 0; }
			hard = AsAbilities.strongFight() || pDeath * 100 >= SPEND || bossNear;
		}
		dying = !adj.isEmpty() && (pDeath * 100 >= Tune.ESC || hpf < 0.25f);
		return true;
	}

	static boolean ok(Item it, String action) { return it != null && !Assist.declined(it.getClass(), action) && !ban.containsKey(key(it, action)); }
	static String key(Item it, String action) { return (it == null ? "-" : it.getClass().getSimpleName()) + ":" + action; }
	static Target use(Item it, String action, int cell, String why) {
		if (!ok(it, action)) return null;
		return Assist.use(it, action, cell, IT + why);
	}
	static Target use(Item it, String action, int cell, Item on, String why) { Target t = use(it, action, cell, why); if (t != null) t.on = on; return t; }
	/** The same entry again and again with no time passing (it did nothing): held back a while (AsAbilities.offered). */
	static Target offered(Target t, int hook) {
		if (t == null || t.item == null) return t;
		String k = key(t.item, t.action) + "@" + t.cell; float now = Actor.now(); int p = Dungeon.hero.pos;
		if (k.equals(offerKey[hook]) && now == offerT[hook] && p == offerPos[hook]) { if (++offerN[hook] >= 3) { ban.put(key(t.item, t.action), now + 20); offerKey[hook] = null; return null; } }
		else { offerKey[hook] = k; offerT[hook] = now; offerPos[hook] = p; offerN[hook] = 0; }
		return t;
	}
	/** Offered again and again over turns with the item never used up (a selector that won't take it, a window
	 *  that comes to nothing): held back a long while. */
	static Target tried(Target t) {
		if (t == null || t.item == null) return t;
		String k = key(t.item, t.action); float now = Actor.now();
		int[] r = tries.get(k); int q = t.item.quantity();
		if (r == null || q < r[1] || r[2] != System.identityHashCode(t.item)) { tries.put(k, new int[]{1, q, System.identityHashCode(t.item), (int) now}); return t; }
		if ((int) now != r[3]) { r[0]++; r[3] = (int) now; }
		if (r[0] > 8) { ban.put(k, now + 300); tries.remove(k); return null; }
		return t;
	}
	static final Map<String, int[]> tries = new HashMap<>();
	static final String[] offerKey = new String[2]; static final float[] offerT = new float[2]; static final int[] offerPos = new int[2], offerN = new int[2];

	/** A known potion or scroll of this kind in the pack (exotics: known with their plain kind), or null. */
	@SuppressWarnings("unchecked")
	static <T extends Item> T knownItem(Class<T> c) {
		for (Item i : H.belongings) if (c.isInstance(i) && Assist.known(i)) return (T) i;
		return null;
	}
	static <T extends Item> T has(Class<T> c) { return knownItem(c); }
	static boolean scrollsOk() { return !Assist.blind() && H.buff(MagicImmune.class) == null; }

	// =============================================================================================================
	// the hooks
	// =============================================================================================================

	static Target urgent(Target planA) { return offered(tried(urgent0(planA)), 0); }
	static Target suggest(Target planA) { return offered(tried(suggest0(planA)), 1); }

	static Target urgent0(Target planA) {
		if (!ON || !FIGHT) return null;
		try {
			if (!prep()) return null;
			Target t;
			if ((t = hazard()) != null) return t;
			if (Assist.foul(hp) || Assist.burning()) return null;   // (out of the gas / fire first: Assist's escape)
			if ((t = litBombs(planA)) != null) return t;
			if ((t = crossing()) != null) return t;   // (a puzzle's crossing under way: its steps)
			if (near.isEmpty()) return null;
			Item ph = Clues.healing();   // (known, or surely Healing by its clues: agent ID)
			if (ph != null && hpf * 100 < Assist.HEALBOSS && (dying || hard)) return null;   // (the hands drink it first)
			if (dying && (t = escapes()) != null) return t;
			if ((t = hasteRun()) != null) return t;
			if ((t = plantStep()) != null) return t;
			if ((t = antiMagic()) != null) return t;
			if (hard && (t = hardFight()) != null) return t;
			if ((t = trapOnFoes()) != null) return t;
			if ((t = thrown()) != null) return t;
			return null;
		} catch (Throwable e) { Assist.lastError = e; return null; }
	}

	static Target suggest0(Target planA) {
		if (!ON) return null;
		try {
			if (!prep()) return null;
			if (Assist.foul(hp) || Assist.burning()) return null;
			Target t;
			if (!near.isEmpty()) return null;   // (a fight on: urgent's)
			if ((t = plantStep()) != null) return t;
			if ((t = trapCrossing(planA)) != null) return t;
			if ((t = puzzles()) != null) return t;
			if ((t = imbue()) != null) return t;
			if ((t = calmPotions()) != null) return t;
			if ((t = calmScrolls()) != null) return t;
			if ((t = stones()) != null) return t;
			if ((t = spells()) != null) return t;
			if ((t = curses()) != null) return t;
			return null;
		} catch (Throwable e) { Assist.lastError = e; return null; }
	}

	// =============================================================================================================
	// helpers
	// =============================================================================================================

	static int x(int c) { return c % W; }
	static int y(int c) { return c / W; }
	static int dist(int a, int b) { return Math.max(Math.abs(x(a) - x(b)), Math.abs(y(a) - y(b))); }
	/** A lit bomb lying in sight (its fuse shows) whose blast reaches c (a step to spare). */
	static boolean blast(int c) {
		for (Heap h : L.heaps.valueList()) { if (!h.seen) continue; for (Item i : h.items) if (i instanceof Bomb && ((Bomb) i).fuse != null && dist(h.pos, c) <= bombRange(i) + (c == hp ? 0 : 1)) return true; }
		return false;
	}
	/** A lit bomb about to go off (ours or a foe's): out of its blast, and plan A not into it (wait instead). */
	static Target litBombs(Target planA) {
		boolean any = false; for (Heap h : L.heaps.valueList()) if (h.seen) for (Item i : h.items) if (i instanceof Bomb && ((Bomb) i).fuse != null) any = true;
		if (!any) return null;
		if (blast(hp)) {
			int best = -1, bd = Assist.INF;
			for (int c = 0; c < LEN; c++) if (Assist.dist[c] > 0 && Assist.dist[c] < bd && Assist.open[c] && Actor.findChar(c) == null && !blast(c)) { bd = Assist.dist[c]; best = c; }
			if (best >= 0) { Target t = new Target(best, Assist.GO, IT + "bomb: out of the blast"); t.obj = best; return t; }
			return null;
		}
		if (planA != null && planA.cell >= 0 && planA.cell != hp && (planA.kind == Assist.GO || planA.kind == Assist.PICKUP || planA.kind == Assist.ENEMY)) {
			int step = planA.cell;
			if (blast(step) || (planA.kind == Assist.ENEMY && dist(step, hp) > 1)) {   // (walking up to a foe by the bomb: no)
				boolean into = blast(step); if (!into) for (int k = 0; k < 8; k++) { int n = hp + Assist.N8[k]; if (Assist.nb(hp, n) && blast(n) && dist(n, step) < dist(hp, step)) into = true; }
				if (into) { Target t = new Target(hp, Assist.WAIT, IT + "bomb: wait for it"); t.action = Assist.WAIT_ACT; t.obj = -1; return t; }
			}
		}
		return null;
	}

	/** A thrown thing aimed at c lands on c (nothing in the way). */
	static boolean lands(int c) { return c != hp && c >= 0 && c < LEN && Assist.line(hp, c); }
	/** A bolt aimed at the char on c hits it. */
	static boolean bolt(int c) { try { Ballistica b = new Ballistica(hp, c, Ballistica.MAGIC_BOLT); return b.collisionPos != null && b.collisionPos == c; } catch (Throwable e) { return false; } }
	static boolean held(Char m) { return Assist.held(m) || m.buff(MagicalSleep.class) != null || m.buff(Paralysis.class) != null || (m instanceof Mob && ((Mob) m).state == ((Mob) m).SLEEPING); }
	static boolean immobile(Mob m) { return Char.hasProp(m, Char.Property.IMMOVABLE) || held(m); }
	static boolean strong(Mob m) { try { return AsAbilities.strong(m); } catch (Throwable e) { return Assist.boss(m); } }
	static Mob strongest(List<Mob> l) { Mob b = null; for (Mob m : l) if (b == null || AsAbilities.cost(m) > AsAbilities.cost(b)) b = m; return b; }
	static int foesWithin(int c, int r) { int n = 0; for (Mob m : foes) if (dist(m.pos, c) <= r) n++; return n; }
	static int alliesWithin(int c, int r) { int n = 0; for (Mob m : Assist.mobs()) if (m.alignment == Char.Alignment.ALLY && m.isAlive() && dist(m.pos, c) <= r) n++; return n; }
	static boolean undead(Char m) { return Char.hasProp(m, Char.Property.UNDEAD); }
	static boolean flam(int c) { return c >= 0 && c < LEN && L.flamable[c]; }
	/** Fire by the hero would spread to him: he stands on or beside something that burns. */
	static boolean fireOk() {
		try {
			Level l = Dungeon.level; Hero h = Dungeon.hero; if (l == null || h == null) return false;
			int w = l.width(), p = h.pos;
			for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) { int n = p + dx + dy * w; if (n >= 0 && n < l.length() && Math.abs(n % w - p % w) <= 1 && l.flamable[n]) return false; }
			return true;
		} catch (Throwable e) { return false; }
	}
	/** Fire at c (radius r) reaches the hero: within r+1, or something that burns joins them. */
	static boolean fireReaches(int c, int r) {
		if (dist(c, hp) <= r + 1) return true;
		boolean[] seen = new boolean[LEN]; int[] q = new int[LEN]; int qh = 0, qt = 0;
		for (int k = 0; k < LEN; k++) if (dist(k, c) <= r && L.flamable[k]) { seen[k] = true; q[qt++] = k; }
		while (qh < qt) { int a = q[qh++]; if (dist(a, hp) <= 1) return true;
			for (int k = 0; k < 8; k++) { int n = a + Assist.N8[k]; if (Assist.nb(a, n) && !seen[n] && L.flamable[n]) { seen[n] = true; q[qt++] = n; } } }
		return false;
	}
	static Wand staffWand() { MagesStaff st = H.belongings.getItem(MagesStaff.class); Object w = st == null ? null : Assist.field(st, "wand"); return w instanceof Wand ? (Wand) w : null; }
	/** A charged wand of this kind (the staff's own counts: the staff is what's zapped), with its holder. */
	static Item wand(Class<? extends Wand> c) {
		MagesStaff st = H.belongings.getItem(MagesStaff.class); Wand sw = staffWand();
		if (st != null && sw != null && c.isInstance(sw) && Assist.charged(sw, st)) return st;
		for (Item i : H.belongings.backpack) if (c.isInstance(i) && Assist.charged((Wand) i, i) && !(((Wand) i).cursed && ((Wand) i).cursedKnown)) return i;
		return null;
	}
	static String zapAct(Item holder) { return holder instanceof MagesStaff ? MagesStaff.AC_ZAP : Wand.AC_ZAP; }
	static int charges() {   // the charges shown on the hero's wands (unknown counts: 1 each if not zapped lately)
		int n = 0; MagesStaff st = H.belongings.getItem(MagesStaff.class); Wand sw = staffWand();
		if (sw != null) n += sw.curChargeKnown ? sw.curCharges : Assist.charged(sw, st) ? 1 : 0;
		for (Item i : H.belongings.backpack) if (i instanceof Wand) { Wand w = (Wand) i; n += w.curChargeKnown ? w.curCharges : Assist.charged(w, w) ? 1 : 0; }
		return n;
	}
	static boolean anyWand() { if (staffWand() != null) return true; for (Item i : H.belongings.backpack) if (i instanceof Wand) return true; return false; }
	/** The free cell farthest from the foes, a clear throw away (for a Blink stone), or -1. */
	static int farCell(int minFoe) {
		int best = -1, bs = -1;
		for (int c = 0; c < LEN; c++) {
			if (!Assist.open[c] || Assist.danger[c] || !Assist.known[c] || Actor.findChar(c) != null || dist(c, hp) < 3 || dist(c, hp) > 8 || !lands(c) || L.pit[c]) continue;
			int f = 99; for (Mob m : foes) f = Math.min(f, dist(m.pos, c));
			if (f < minFoe) continue;
			int s = f * 4 + dist(c, hp); if (s > bs) { bs = s; best = c; }
		}
		return best;
	}
	static boolean fresh() { return Actor.now() - Assist.floorT0 < 40; }

	// =============================================================================================================
	// hazards: gas and fire
	// =============================================================================================================

	static Target hazard() {
		boolean gas = Assist.foul(hp) && !Assist.fireAt(hp), burn = Assist.burning();
		if (!gas && !burn) return null;
		int clean = Assist.INF;   // the nearest clean cell
		for (int c = 0; c < LEN; c++) if (Assist.dist[c] > 0 && Assist.dist[c] < clean && Assist.open[c] && !Assist.foul(c)) clean = Assist.dist[c];
		if (gas && clean > 2) {
			PotionOfPurity pu = has(PotionOfPurity.class);
			if (pu != null) return use(pu, Potion.AC_DRINK, -1, "purity: the gas all round");
		}
		if (burn && !Assist.water(hp)) {
			int wd = Assist.INF; for (int c = 0; c < LEN; c++) if (Assist.water(c) && Assist.dist[c] < wd) wd = Assist.dist[c];
			if (wd > 2) {
				PotionOfCleansing cl = has(PotionOfCleansing.class); if (cl != null) return use(cl, Potion.AC_DRINK, -1, "cleansing: on fire");
				if (hpf < 0.5f) { PotionOfFrost fr = has(PotionOfFrost.class); if (fr != null && foesWithin(hp, 1) == 0) { int c = -1; for (int k = 0; k < 8; k++) { int n = hp + Assist.N8[k]; if (Assist.nb(hp, n) && lands(n)) { c = n; break; } } if (c >= 0) return use(fr, Item.AC_THROW, c, "frost: put out the fire"); } }
			}
		}
		return null;
	}

	// =============================================================================================================
	// the fight
	// =============================================================================================================

	/** Dying beside a foe, no known Healing: a way out the hands don't know (theirs: Teleportation, Invisibility,
	 *  Terror, Lullaby, Mirror Image, the gamble). */
	static Target escapes() {
		Mob s = strongest(adj); Target t;
		boolean sc = scrollsOk();
		// Retribution: the lower the health the harder it hits everything in sight (ScrollOfRetribution: HT/10 + HP x 0.225 x power)
		ScrollOfRetribution rt = has(ScrollOfRetribution.class);
		if (rt != null && sc) { float pw = Math.min(4f, 4.45f * (1 - hpf)); boolean all = true; for (Mob m : adj) all &= m.HT / 10f + m.HP * pw * 0.225f >= m.HP; if (all) return use(rt, Scroll.AC_READ, -1, "retribution: low, it finishes them"); }
		StoneOfBlink bl = has(StoneOfBlink.class);
		if (bl != null) { int c = farCell(4); if (c >= 0) return use(bl, Item.AC_THROW, c, "blink: away"); }
		PhaseShift ps = has(PhaseShift.class);
		if (ps != null && s != null && !Assist.boss(s) && bolt(s.pos)) return use(ps, Spell.AC_CAST, s.pos, "phase shift: it away");
		if (adj.size() == 1 && s != null && !Assist.boss(s)) {
			StoneOfDeepSleep ds = has(StoneOfDeepSleep.class); if (ds != null && lands(s.pos)) return use(ds, Item.AC_THROW, s.pos, "deep sleep: it");
			StoneOfFear fe = has(StoneOfFear.class); if (fe != null && lands(s.pos)) return use(fe, Item.AC_THROW, s.pos, "fear: it");
		}
		ScrollOfDread dr = has(ScrollOfDread.class); if (dr != null && sc) return use(dr, Scroll.AC_READ, -1, "dread: they flee");
		ScrollOfSirensSong ss = has(ScrollOfSirensSong.class); if (ss != null && sc && s != null && !Assist.boss(s)) return use(ss, Scroll.AC_READ, s.pos, "sirens' song: it on our side");
		Item bw = wand(WandOfBlastWave.class); if (bw != null && s != null && bolt(s.pos)) return use(bw, zapAct(bw), s.pos, "blast wave: knock it off");
		Item tf = wand(WandOfTransfusion.class); if (tf != null && s != null && !undead(s) && !Assist.boss(s) && bolt(s.pos)) return use(tf, zapAct(tf), s.pos, "transfusion: charm it");
		for (Class<? extends Potion> c : Arrays.asList(PotionOfShielding.class, ElixirOfHoneyedHealing.class, PotionOfEarthenArmor.class)) { Potion p = has(c); if (p != null) return use(p, Potion.AC_DRINK, -1, "dying: " + c.getSimpleName().replace("PotionOf", "").replace("ElixirOf", "").toLowerCase(Locale.ROOT)); }
		ScrollOfPrismaticImage pi = has(ScrollOfPrismaticImage.class); if (pi != null && sc) return use(pi, Scroll.AC_READ, -1, "prismatic image: a decoy");
		DisplacingDart dd = has(DisplacingDart.class); if (dd != null && s != null && lands(s.pos)) return use(dd, Item.AC_THROW, s.pos, "displacing dart: it away");
		if (!Dungeon.bossLevel()) {
			ScrollOfPassage pa = has(ScrollOfPassage.class);
			if (pa != null && sc && Dungeon.interfloorTeleportAllowed() && Dungeon.depth > 1) return use(pa, Scroll.AC_READ, -1, "passage: out of here");
			BeaconOfReturning be = has(BeaconOfReturning.class);
			BeaconOfReturning.BeaconTracker bt = H.buff(BeaconOfReturning.BeaconTracker.class);
			if (be != null && bt != null && bt.returnDepth >= 0 && bt.returnBranch == Dungeon.branch && (bt.returnDepth != Dungeon.depth || dist(bt.returnPos, hp) > 8)) { beaconReturn = true; return use(be, Spell.AC_CAST, -1, "beacon: return"); }
		}
		return null;
	}
	static boolean beaconReturn;

	/** Low and hunted, Haste known: drink it and run for the way up (Assist's escape takes the stairs when near). */
	static Target hasteRun() {
		if (!dying && !(hpf < 0.35f && pDeath * 100 >= Tune.UP)) return null;
		if (Dungeon.depth <= 1 || Dungeon.bossLevel() || L.locked) return null;
		int ent = L.entrance(); if (ent < 0 || ent >= LEN || !Assist.known[ent] || Assist.dist[ent] >= Assist.INF || Assist.dist[ent] > 25) return null;
		boolean fast = H.buff(Haste.class) != null || H.buff(Stamina.class) != null;
		if (!fast) {
			for (Class<? extends Potion> c : Arrays.asList(PotionOfHaste.class, PotionOfStamina.class)) { Potion p = has(c); if (p != null) return use(p, Potion.AC_DRINK, -1, "haste: run for the stairs"); }
			return null;
		}
		if (Assist.dist[ent] <= 8) return null;   // (Assist.upstairs from here)
		int[] de = new int[LEN]; Assist.tdist(ent, de);
		int step = -1; for (int k = 0; k < 8; k++) { int n = hp + Assist.N8[k]; if (Assist.nb(hp, n) && Assist.open[n] && Actor.findChar(n) == null && de[n] < de[hp] && (step < 0 || de[n] < de[step])) step = n; }
		return step >= 0 ? new Target(step, Assist.GO, IT + "haste: to the stairs") : null;
	}

	/** A fight worth a consumable (a strong foe, a boss, the fight model's odds): the best thing for it. */
	static Target hardFight() {
		Mob s = strongest(near); Target t;
		boolean sc = scrollsOk();
		int nearN = near.size(), adjN = adj.size();
		// the wands the plain plan doesn't zap
		Item cw = wand(WandOfCorruption.class);
		if (cw != null) { Mob best = null; for (Mob m : near) if (!Assist.boss(m) && strong(m) && bolt(m.pos) && corruptSure(m, cw) && (best == null || AsAbilities.cost(m) > AsAbilities.cost(best))) best = m;
			if (best != null) return use(cw, zapAct(cw), best.pos, "corruption: " + best.getClass().getSimpleName()); }
		Item tf = wand(WandOfTransfusion.class);
		if (tf != null && s != null && !Assist.boss(s) && !undead(s) && s.buff(Charm.class) == null && bolt(s.pos)) return use(tf, zapAct(tf), s.pos, "transfusion: charm it");
		if (tf != null) for (Mob m : near) if (undead(m) && bolt(m.pos)) return use(tf, zapAct(tf), m.pos, "transfusion: hurts the undead");
		if (charges() == 0 && anyWand()) {
			ScrollOfRecharging rc = has(ScrollOfRecharging.class); if (rc != null && sc && H.buff(Recharging.class) == null) return use(rc, Scroll.AC_READ, -1, "recharging: wands empty");
			WildEnergy we = has(WildEnergy.class); if (we != null && s != null && bolt(s.pos)) return use(we, Spell.AC_CAST, s.pos, "wild energy: wands empty");
		}
		// crowd control on the strongest one not beside the hero, or a second one
		Mob far = null; for (Mob m : near) if (dist(m.pos, hp) >= 2 && !held(m) && !Assist.boss(m) && lands(m.pos) && (far == null || AsAbilities.cost(m) > AsAbilities.cost(far))) far = m;
		StoneOfDeepSleep ds = has(StoneOfDeepSleep.class);
		if (ds != null && far != null && (strong(far) || adjN > 0)) return use(ds, Item.AC_THROW, far.pos, "deep sleep: " + far.getClass().getSimpleName());
		StoneOfShock sh = has(StoneOfShock.class);
		if (sh != null) { int c = groupCell(2, 3, 2); if (c >= 0) return use(sh, Item.AC_THROW, c, "shock: the group"); }
		// bombs and the stone that is one: on what stands still, or a group, out of the blast
		Target b = bombs(); if (b != null) return b;
		// area potions, brews (known; Assist's hands throw the plain gases at 4+ and Flame / Frost at 3+)
		Target p = areaPotions(); if (p != null) return p;
		// scrolls
		if (sc) {
			ScrollOfRage ra = has(ScrollOfRage.class);
			if (ra != null && foes.size() >= 3 && !bossNear) return use(ra, Scroll.AC_READ, -1, "rage: they fight each other");
			ScrollOfPsionicBlast pb = has(ScrollOfPsionicBlast.class);
			if (pb != null) { int n = 0; for (Mob m : Assist.mobs()) if (L.heroFOV[m.pos]) n++; double self = 0.5 * H.HT * Math.pow(0.9, n);
				if (H.HP - self > H.HT * 0.3 && (pDeath * 100 >= 25 || bossNear || nearN >= 3)) return use(pb, Scroll.AC_READ, -1, "psionic blast"); }
			ScrollOfRetribution rt = has(ScrollOfRetribution.class);
			if (rt != null && hpf < 0.4f) return use(rt, Scroll.AC_READ, -1, "retribution: low");
			ScrollOfPrismaticImage pi = has(ScrollOfPrismaticImage.class);
			if (pi != null && (bossNear || pDeath * 100 >= 20)) return use(pi, Scroll.AC_READ, -1, "prismatic image");
			ScrollOfMirrorImage mi = has(ScrollOfMirrorImage.class);
			if (mi != null && (bossNear || pDeath * 100 >= 25)) return use(mi, Scroll.AC_READ, -1, "mirror image");
			ScrollOfSirensSong ss = has(ScrollOfSirensSong.class);
			if (ss != null && s != null && !Assist.boss(s) && strong(s) && L.heroFOV[s.pos]) return use(ss, Scroll.AC_READ, s.pos, "sirens' song: " + s.getClass().getSimpleName());
		}
		// allies: summoned, a ward, a guardian
		SummonElemental se = has(SummonElemental.class);
		if (se != null && alliesWithin(hp, 8) == 0) return use(se, Spell.AC_CAST, -1, "summon elemental");
		Item wd = wand(WandOfWarding.class);
		if (wd != null && s != null && dist(s.pos, hp) >= 2) { int c = wardCell(s.pos); if (c >= 0) return use(wd, zapAct(wd), c, "warding: a ward"); }
		Item le = wand(WandOfLivingEarth.class);
		if (le != null && s != null && bolt(s.pos)) return use(le, zapAct(le), s.pos, "living earth");
		Item rg = wand(WandOfRegrowth.class);
		if (rg != null && s != null && dist(s.pos, hp) >= 2 && !held(s) && bolt(s.pos)) return use(rg, zapAct(rg), s.pos, "regrowth: roots");
		// the knockback: into a chasm or a trap, else off the hero when it's beside him and winning
		Item bw = wand(WandOfBlastWave.class);
		if (bw != null && s != null && bolt(s.pos)) { int land = knockLand(s, (bw instanceof Wand ? (Wand) bw : staffWand()).buffedLvl() + 3);
			if (land >= 0 && (L.pit[land] || (L.traps.get(land) != null && L.traps.get(land).active))) return use(bw, zapAct(bw), s.pos, "blast wave: into the " + (L.pit[land] ? "chasm" : "trap")); }
		// stones on a strong one
		if (s != null && !Assist.boss(s)) {
			StoneOfAggression ag = has(StoneOfAggression.class);
			if (ag != null && nearN >= 3 && lands(s.pos)) return use(ag, Item.AC_THROW, s.pos, "aggression: the others on it");
			StoneOfFear fe = has(StoneOfFear.class);
			if (fe != null && adjN >= 2 && lands(s.pos) && dist(s.pos, hp) <= 1) return use(fe, Item.AC_THROW, s.pos, "fear: one of them");
		}
		// buffs before the blows
		if (adjN > 0 && hpf < 0.75f) {
			for (Class<? extends Potion> c : Arrays.asList(PotionOfShielding.class, PotionOfEarthenArmor.class)) { Potion q = has(c); if (q != null && H.buff(Barrier.class) == null && H.buff(Barkskin.class) == null) return use(q, Potion.AC_DRINK, -1, c.getSimpleName().replace("PotionOf", "").toLowerCase(Locale.ROOT)); }
		}
		if (bossNear) {
			ElixirOfArcaneArmor aa = has(ElixirOfArcaneArmor.class); if (aa != null && H.buff(ArcaneArmor.class) == null) return use(aa, Potion.AC_DRINK, -1, "arcane armor: the boss");
			for (Class<? extends Potion> c : Arrays.asList(ElixirOfDragonsBlood.class, ElixirOfIcyTouch.class, ElixirOfToxicEssence.class)) { Potion q = has(c); if (q != null) return use(q, Potion.AC_DRINK, -1, c.getSimpleName().replace("ElixirOf", "").toLowerCase(Locale.ROOT) + ": the boss"); }
		}
		// tipped darts on the strong one, seeds into its path
		Target d = tipped(s); if (d != null) return d;
		Target sd = seedPath(); if (sd != null) return sd;
		return null;
	}

	/** A plant beside the hero that helps whoever steps on it (Plant.activate; the Warden gets more): stepped onto.
	 *  Before a hard fight with the foes still a few steps off: a Starflower or Earthroot seed thrown beside him first. */
	static Target plantStep() {
		boolean warden = H.subClass == HeroSubClass.WARDEN, debuff = false;
		for (Buff b : H.buffs()) if (b instanceof Poison || b instanceof Bleeding || b instanceof Cripple || b instanceof Weakness || b instanceof Vertigo) debuff = true;
		int best = -1, bs = 0;
		for (int k = -1; k < 8; k++) {
			int c = k < 0 ? hp : hp + Assist.N8[k]; if (k >= 0 && (!Assist.nb(hp, c) || !Assist.open[c] || Actor.findChar(c) != null)) continue;
			if (c == hp) continue;   // (standing on one: it went off already, or was planted here)
			Plant p = L.plants.get(c); if (p == null) continue;
			int v = 0;
			if (p instanceof Sungrass && hpf < 0.6f && adj.isEmpty()) v = 3;
			if (p instanceof Earthroot && (hard || warden)) v = 4;
			if (p instanceof Starflower) v = 3;
			if (p instanceof Swiftthistle && hard) v = 2;
			if (p instanceof Mageroyal && debuff) v = 3;
			if (warden && p instanceof Blindweed && hard) v = 4;
			if (warden && p instanceof Stormvine) v = 1;
			if (p instanceof Fadeleaf && dying) v = 5;
			if (v > bs) { bs = v; best = c; }
		}
		if (best >= 0) { Target t = new Target(best, Assist.GO, IT + "plant: step on the " + L.plants.get(best).getClass().getSimpleName()); t.obj = best; return t; }
		if (near.isEmpty() && hpf < 0.6f && L.plants.get(hp) instanceof Sungrass) {   // (planted where he stands: it goes off when stepped onto, so off it and back)
			for (int k = 0; k < 8; k++) { int c = hp + Assist.N8[k]; if (Assist.nb(hp, c) && Assist.open[c] && Actor.findChar(c) == null && L.plants.get(c) == null && L.traps.get(c) == null) { Target t = new Target(c, Assist.GO, IT + "plant: off the Sungrass, to step back on"); t.obj = c; return t; } }
		}
		if (hard && adj.isEmpty()) {
			int close = 99; for (Mob m : near) close = Math.min(close, Assist.walk(m.pos));
			if (close >= 3 && close < Assist.INF) for (Class<? extends Plant.Seed> sc : Arrays.asList(Starflower.Seed.class, Earthroot.Seed.class)) {
				Plant.Seed sd = H.belongings.getItem(sc); if (sd == null || !ok(sd, Item.AC_THROW)) continue;
				for (int k = 0; k < 8; k++) { int c = hp + Assist.N8[k]; if (Assist.nb(hp, c) && Assist.open[c] && Actor.findChar(c) == null && L.plants.get(c) == null && L.traps.get(c) == null && L.heaps.get(c) == null && lands(c)) return use(sd, Item.AC_THROW, c, "plant: " + sc.getEnclosingClass().getSimpleName() + " beside, to step on"); }
			}
		}
		return null;
	}

	/** The magic shooters (their bolts and gazes are what MagicImmune / AntiMagic stop: AntiMagic.RESISTS): shamans,
	 *  warlocks, eyes, DM-100s, Yog's fists: in the fight and in line, the hero not at full health or two of them. */
	static Target antiMagic() {
		int n = 0; boolean eye = false;
		for (Mob m : near) if ((m instanceof Shaman || m instanceof Warlock || m instanceof Eye || m instanceof DM100 || m instanceof YogFist) && Assist.line(m.pos, hp)) { n++; eye |= m instanceof Eye || m instanceof YogFist; }
		if (n == 0 || H.buff(MagicImmune.class) != null || H.buff(ArcaneArmor.class) != null) return null;
		if (n < 2 && hpf > 0.7f && !eye) return null;
		ElixirOfArcaneArmor aa = has(ElixirOfArcaneArmor.class); if (aa != null) return use(aa, Potion.AC_DRINK, -1, "arcane armor: magic shooters");
		ScrollOfAntiMagic am = has(ScrollOfAntiMagic.class); if (am != null && scrollsOk()) return use(am, Scroll.AC_READ, -1, "anti-magic: magic shooters");
		return null;
	}

	/** Corruption by WandOfCorruption.onZap's own sum, from what shows: its level, its health bar, its debuffs. */
	static boolean corruptSure(Mob m, Item holder) {
		Wand w = holder instanceof MagesStaff ? staffWand() : (Wand) holder; if (w == null) return false;
		if (m.buff(Corruption.class) != null || m.buff(Doom.class) != null || m instanceof Mimic || m instanceof Statue || m instanceof Wraith || m instanceof Swarm) return false;
		float power = 3 + (w.levelKnown ? w.buffedLvl() : 0) / 3f;
		float resist = 1 + com.shatteredpixel.shatteredpixeldungeon.actors.buffs.AscensionChallenge.AscensionCorruptResist(m);
		resist *= 1 + 4 * Math.pow(m.HP / (float) Math.max(1, m.HT), 2);
		for (Buff b : m.buffs()) if (b.type == Buff.buffType.NEGATIVE) resist *= 0.75f;   // (the minor share for any: a lower bound)
		return power > resist;
	}

	/** The cell a knockback of power from the hero leaves m on (Ballistica along the bolt), or -1. */
	static int knockLand(Mob m, int power) {
		try {
			Ballistica b = new Ballistica(hp, m.pos, Ballistica.MAGIC_BOLT);
			if (b.collisionPos != m.pos || b.path.size() <= b.dist + 1) return -1;
			Ballistica tr = new Ballistica(m.pos, b.path.get(b.dist + 1), Ballistica.MAGIC_BOLT);
			if (Assist.boss(m)) power = (power + 1) / 2;
			int d = Math.min(tr.dist, power);
			int land = tr.path.get(d);
			for (int i = 1; i <= d; i++) { int c = tr.path.get(i); if (L.pit[c] && !m.flying) return c; }
			return land;
		} catch (Throwable e) { return -1; }
	}

	/** A cell for a ward: beside the hero, towards the foe, free and a clear bolt. */
	static int wardCell(int foe) {
		int best = -1, bd = 99;
		for (int k = 0; k < 8; k++) { int n = hp + Assist.N8[k]; if (!Assist.nb(hp, n) || !Assist.open[n] || Actor.findChar(n) != null || !bolt(n)) continue; int d = dist(n, foe); if (d < bd) { bd = d; best = n; } }
		return best;
	}

	/** The foe's cell that hits the most foes within r (at least min, a boss counts 2), the hero more than safe away. */
	static int groupCell(int r, int safe, int min) {
		int best = -1, bn = 0;
		for (Mob m : foes) {
			int c = m.pos; if (dist(c, hp) < safe || !lands(c) || alliesWithin(c, r) > 0) continue;
			int n = 0; for (Mob o : foes) if (dist(o.pos, c) <= r) n += Assist.boss(o) ? 2 : 1;
			if (n > bn) { bn = n; best = c; }
		}
		return bn >= min ? best : -1;
	}

	static int bombRange(Item b) { return b instanceof ShrapnelBomb ? 8 : b instanceof RegrowthBomb ? 3 : (b instanceof ArcaneBomb || b instanceof Firebomb || b instanceof FlashBangBomb || b instanceof FrostBomb || b instanceof HolyBomb || b instanceof SmokeBomb || b instanceof WoollyBomb) ? 2 : 1; }

	/** Bombs (lit: a 2-turn fuse) on foes that will still be there: held, immobile, asleep, keeping their distance
	 *  (shooters), or a boss with its summons; the blast clear of the hero (a step beyond, the foes walk). The Blast stone
	 *  goes off at once: any group. */
	static Target bombs() {
		Item best = null; int bc = -1, bn = 0;
		boolean spend = Assist.bag(com.shatteredpixel.shatteredpixeldungeon.items.Amulet.class) == null && AsEconomy.bombSpendOk();   // (the climb's are AsAscent's; going down, emergencies only: AsEconomy)
		if (spend) for (Item i : H.belongings.backpack) {
			if (!(i instanceof Bomb) || i instanceof Noisemaker || i instanceof RegrowthBomb || i instanceof WoollyBomb || i instanceof SmokeBomb) continue;
			if (!ok(i, "LIGHTTHROW")) continue;
			int r = bombRange(i); if (i instanceof ShrapnelBomb) continue;   // (its 8 cells in sight: it hits the hero too, almost always)
			for (Mob m : foes) {
				int c = m.pos; if (!lands(c) || dist(c, hp) <= r + 1 || alliesWithin(c, r) > 0) continue;
				int n = 0; for (Mob o : foes) if (dist(o.pos, c) <= r && (immobile(o) || !Assist.hunting(o) || pebxel.bot.FoeView.shooter(o) || Assist.boss(o))) n += Assist.boss(o) || strong(o) ? 2 : 1;
				if (i instanceof HolyBomb) for (Mob o : foes) if (dist(o.pos, c) <= r && (undead(o) || Char.hasProp(o, Char.Property.DEMONIC))) n++;
				if (i instanceof Firebomb && fireReaches(c, r)) continue;
				if (n > bn) { bn = n; bc = c; best = i; }
			}
		}
		if (best != null && bn >= 2) return use(best, "LIGHTTHROW", bc, best.getClass().getSimpleName().toLowerCase(Locale.ROOT) + ": " + bn);
		StoneOfBlast sb = has(StoneOfBlast.class);
		if (sb != null) { int c = groupCell(1, 2, 2); if (c >= 0) return use(sb, Item.AC_THROW, c, "blast stone: the group"); }
		return null;
	}

	/** The known area potions and brews the hands don't throw. */
	static Target areaPotions() {
		Mob t = null; int td = 99; for (Mob m : near) { int d = dist(m.pos, hp); if (lands(m.pos) && (t == null || AsAbilities.cost(m) > AsAbilities.cost(t))) { t = m; td = d; } }
		if (t == null) return null;
		int c = t.pos;
		PotionOfCorrosiveGas cg = has(PotionOfCorrosiveGas.class); if (cg != null && td >= 4) return use(cg, Item.AC_THROW, c, "corrosive gas");
		PotionOfSnapFreeze sf = has(PotionOfSnapFreeze.class); if (sf != null && td >= 2 && !held(t)) return use(sf, Item.AC_THROW, c, "snap freeze");
		BlizzardBrew bz = has(BlizzardBrew.class); if (bz != null && td >= 4) return use(bz, Item.AC_THROW, c, "blizzard brew");
		ShockingBrew sk = has(ShockingBrew.class); if (sk != null && td >= 3 && !Assist.water(hp)) return use(sk, Item.AC_THROW, c, "shocking brew");
		CausticBrew ca = has(CausticBrew.class); if (ca != null && td >= 3) return use(ca, Item.AC_THROW, c, "caustic brew");
		InfernalBrew ib = has(InfernalBrew.class); if (ib != null && td >= 4 && !fireReaches(c, 2)) return use(ib, Item.AC_THROW, c, "infernal brew");
		PotionOfDragonsBreath db = has(PotionOfDragonsBreath.class); if (db != null && td >= 2 && td <= 6 && fireOk() && !fireReaches(c, 1)) return use(db, Potion.AC_DRINK, c, "dragon's breath");
		PotionOfShroudingFog fg = has(PotionOfShroudingFog.class);
		if (fg != null && pebxel.bot.FoeView.shooter(t) && td >= 3) return use(fg, Item.AC_THROW, c, "shrouding fog: blind the shooter");
		return null;
	}

	/** A tipped dart for the strong one (each its own job), a healing one for a hurt ally. */
	static Target tipped(Mob s) {
		for (Mob m : Assist.mobs()) if (m.alignment == Char.Alignment.ALLY && m.isAlive() && L.heroFOV[m.pos] && m.HP * 2 < m.HT && lands(m.pos)) {
			HealingDart hd = H.belongings.getItem(HealingDart.class); if (hd != null) return use(hd, Item.AC_THROW, m.pos, "healing dart: the ally");
		}
		if (s == null || !lands(s.pos) || dist(s.pos, hp) < 2) return null;
		List<Class<? extends TippedDart>> order = new ArrayList<>();
		if (!held(s)) { order.add(ParalyticDart.class); order.add(ChillingDart.class); }
		if (s.buff(Blindness.class) == null) order.add(BlindingDart.class);
		if (s.buff(Poison.class) == null && !Char.hasProp(s, Char.Property.INORGANIC)) order.add(PoisonDart.class);
		order.add(RotDart.class); order.add(ShockingDart.class);
		if (!fireReaches(s.pos, 1)) order.add(IncendiaryDart.class);
		for (Class<? extends TippedDart> c : order) { TippedDart d = H.belongings.getItem(c); if (d != null) return use(d, Item.AC_THROW, s.pos, c.getSimpleName().replace("Dart", "").toLowerCase(Locale.ROOT) + " dart: " + s.getClass().getSimpleName()); }
		return null;
	}

	/** A seed thrown onto the cell a coming foe steps onto next (a plant goes off when stepped on). */
	static Target seedPath() {
		Mob m = null; for (Mob o : near) if (Assist.hunting(o) && !held(o) && !Assist.ranged(o) && !o.flying && Assist.walk(o.pos) >= 2 && Assist.walk(o.pos) <= 6 && (m == null || AsAbilities.cost(o) > AsAbilities.cost(m))) m = o;
		if (m == null) return null;
		int nc = -1, nd = 99;   // its next step towards the hero
		for (int k = 0; k < 8; k++) { int n = m.pos + Assist.N8[k]; if (!Assist.nb(m.pos, n) || !L.passable[n] || Actor.findChar(n) != null || L.plants.get(n) != null || L.traps.get(n) != null || n == hp) continue; int d = Assist.walk(n); if (d < nd) { nd = d; nc = n; } }
		if (nc < 0 || !lands(nc)) return null;
		boolean besideHero = dist(nc, hp) <= 1;
		List<Class<? extends Plant.Seed>> order = new ArrayList<>(Arrays.asList(Earthroot.Seed.class, Blindweed.Seed.class, Stormvine.Seed.class, Fadeleaf.Seed.class));
		if (!besideHero) { order.add(0, Icecap.Seed.class); order.add(Sorrowmoss.Seed.class); if (!fireReaches(nc, 1)) order.add(Firebloom.Seed.class); }
		for (Class<? extends Plant.Seed> c : order) { Plant.Seed s = H.belongings.getItem(c); if (s != null) return use(s, Item.AC_THROW, nc, "seed in its path: " + c.getEnclosingClass().getSimpleName()); }
		return null;
	}

	/** Thrown weapons on any foe coming: the plain ones (tipped are for strong foes), the best tier first. */
	static Target thrown() {
		Mob t = null; int td = 99;
		for (Mob m : near) { int d = dist(m.pos, hp); if (d >= 2 && Assist.hunting(m) && lands(m.pos) && d < td && !(m instanceof Piranha)) { t = m; td = d; } }
		if (t == null) return null;
		if (H.belongings.getItem(com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow.class) != null) return null;   // (the bow: Assist's)
		if (t instanceof Tengu || t instanceof Eye || t instanceof Scorpio) return null;   // (they dodge or outshoot it: Assist's rule)
		Wand sw = staffWand(); if (sw != null && Assist.charged(sw, H.belongings.getItem(MagesStaff.class)) && Assist.attackWand(sw.getClass(), td)) return null;   // (the staff's bolt first: Assist's)
		MissileWeapon best = null;
		for (Item i : H.belongings.backpack) if (i instanceof MissileWeapon && !(i instanceof TippedDart) && ok(i, Item.AC_THROW) && (best == null || ((MissileWeapon) i).tier > best.tier)) best = (MissileWeapon) i;
		if (best == null) return null;
		if (best.tier <= 1 && td < 3 && !hard) return null;   // (a dart for 1-4 at two cells: a melee turn is worth more)
		return use(best, Item.AC_THROW, t.pos, "throw: " + best.getClass().getSimpleName());
	}

	// =============================================================================================================
	// traps
	// =============================================================================================================

	/** How bad stepping on it is (0 harmless .. 6 can kill): strategy-reference 8.1. */
	static int trapRank(Trap t) {
		if (t == null) return 0;
		String n = t.getClass().getSimpleName();
		switch (n) {
			case "FlockTrap": return 0;
			case "AlarmTrap": case "OozeTrap": case "ConfusionTrap": case "ChillingTrap": case "WornDartTrap": case "TeleportationTrap": case "GatewayTrap": case "GeyserTrap": return 1;
			case "WeakeningTrap": case "GrippingTrap": case "PoisonDartTrap": case "ShockingTrap": return 2;
			case "ToxicTrap": case "BurningTrap": case "FrostTrap": case "FlashingTrap": case "GuardianTrap": case "WarpingTrap": case "PitfallTrap": case "DisarmingTrap": return 3;
			case "StormTrap": case "CorrosionTrap": case "BlazingTrap": case "RockfallTrap": case "SummoningTrap": case "CursingTrap": case "DistortionTrap": case "ExplosiveTrap": return 4;
			case "DisintegrationTrap": return 5;
			case "GrimTrap": return 6;
			default: return 3;
		}
	}
	/** Its reach when sprung with nobody on it (gas spreads), 0: only the one on it. */
	static int trapArea(Trap t) {
		String n = t.getClass().getSimpleName();
		switch (n) {
			case "ToxicTrap": case "CorrosionTrap": case "ConfusionTrap": case "FrostTrap": case "StormTrap": case "BlazingTrap": return 2;
			case "ChillingTrap": case "ShockingTrap": case "BurningTrap": case "ExplosiveTrap": return 1;
			default: return 0;
		}
	}
	/** Something not worth keeping to throw onto a trap: a plain thrown weapon (picked up again), else a spare seed. */
	static Item junk() {
		Item b = null;
		for (Item i : H.belongings.backpack) if (i instanceof MissileWeapon && !(i instanceof TippedDart) && (b == null || ((MissileWeapon) i).tier < ((MissileWeapon) b).tier)) b = i;
		if (b != null) return b;
		for (Item i : H.belongings.backpack) if (i instanceof Plant.Seed && !(i instanceof Sungrass.Seed) && !(i instanceof Starflower.Seed) && !(i instanceof Rotberry.Seed) && i.quantity() >= 2) return i;
		for (Item i : H.belongings.backpack) if (i instanceof Runestone && (i instanceof StoneOfFlock || i instanceof StoneOfAggression)) return i;   // (Flock's sheep, Aggression with no one there: nothing)
		return null;
	}

	/** Plan A goes over a known trap (the only way on): the mildest such trap, sprung first from afar with a thrown
	 *  thing (it lands on it: Level.drop presses the cell), else crossed with Levitation when it's a bad one. */
	static Target trapCrossing(Target planA) {
		if (planA == null || !"over the trap".equals(planA.why)) return null;
		int c = planA.obj >= 0 ? planA.obj : planA.cell;
		// the mildest trap that opens the way (Assist's own test), nearest among equals
		int best = -1, bk = Assist.INF;
		for (int k = 0; k < LEN; k++) {
			Trap tr = L.traps.get(k);
			if (tr == null || !tr.visible || !tr.active || !Assist.known[k] || L.pit[k] || L.solid[k] || Assist.walk(k) >= Assist.INF || Assist.bad.containsKey(k)) continue;
			boolean opens = false; for (int j = 0; j < 8; j++) { int n = k + Assist.N8[j]; if (Assist.nb(k, n) && (!Assist.known[n] || (Assist.open[n] && Assist.dist[n] >= Assist.INF))) opens = true; }
			if (!opens) continue;
			int s = trapRank(tr) * 12 + Assist.walk(k); if (s < bk) { bk = s; best = k; }
		}
		if (best >= 0) c = best;
		Trap tr = L.traps.get(c); if (tr == null || !tr.active) return null;
		int d = dist(c, hp), area = trapArea(tr);
		Item j = junk();
		if (j != null && lands(c) && d > area && d <= 7) return use(j, Item.AC_THROW, c, "spring the trap from afar: " + tr.getClass().getSimpleName());
		if (trapRank(tr) >= 3 && H.buff(Levitation.class) == null) { PotionOfLevitation lv = has(PotionOfLevitation.class); if (lv != null && d <= 3) return use(lv, Potion.AC_DRINK, -1, "levitation: over the " + tr.getClass().getSimpleName()); }
		if (c != (planA.obj >= 0 ? planA.obj : planA.cell)) { Target t = new Target(c, Assist.GO, IT + "trap: the mildest crossing"); t.obj = c; return t; }
		return null;
	}

	/** A known trap sprung on foes from afar: the foes within its reach, the hero well out of it. */
	static Target trapOnFoes() {
		Item j = junk(); if (j == null) return null;
		int best = -1, bn = 0;
		for (int k = 0; k < LEN; k++) {
			Trap tr = L.traps.get(k); if (tr == null || !tr.visible || !tr.active || !L.heroFOV[k]) continue;
			int a = trapArea(tr); if (a == 0 || dist(k, hp) <= a + 2 || !lands(k) || Actor.findChar(k) != null || alliesWithin(k, a) > 0) continue;
			if ((tr instanceof BurningTrap || tr instanceof BlazingTrap) && fireReaches(k, a)) continue;
			int n = 0; for (Mob m : near) if (dist(m.pos, k) <= a) n += strong(m) ? 2 : 1;
			if (n > bn) { bn = n; best = k; }
		}
		if (best >= 0 && bn >= 1) return use(j, Item.AC_THROW, best, "spring the trap on them: " + L.traps.get(best).getClass().getSimpleName());
		return null;
	}

	// =============================================================================================================
	// special rooms: the potion puzzles (the prize past traps or a chasm, piranha water, the sentries' floor, gas,
	// magical fire, a barricade)
	// =============================================================================================================

	static final int LEV = 1, INVIS = 2, PURE = 3, HASTE = 4;   // (HASTE: the sentry room's own potion, SentryRoom.paint; invisibility doesn't hide from a sentry: Sentry.act)
	static final class Cross { int heap, home, kind, tries; Level l; float until; }
	static final Set<Integer> done = new HashSet<>();   // prizes given up on this floor
	static Cross cross;

	/** Can the remedy's buff take the hero over c (beyond the plain walk)? */
	static boolean crossable(int c, int kind) {
		if (c < 0 || c >= LEN || (L.solid[c] && !L.passable[c]) || (!Assist.known[c] && !L.heroFOV[c])) return false;   // (a door is solid and passable: Terrain.DOOR)
		if (Actor.findChar(c) != null && c != hp && L.heroFOV[c]) return false;
		if (kind == LEV) return L.passable[c] || L.avoid[c];
		if (!L.passable[c] || L.avoid[c]) return false;
		if (Assist.fireAt(c)) return false;
		if (kind == PURE) return true;
		if (kind == HASTE) return !Assist.foul(c);   // (the sentries' floor: their charge runs on the hero's turns, three steps a turn hasted)
		return !Assist.foul(c);   // INVIS: water
	}
	/** The walk over crossable cells from a to b (steps; first step in out[0]), or INF. */
	static int crossWalk(int a, int b, int kind, int[] first) {
		int[] d = new int[LEN]; Arrays.fill(d, Assist.INF); int[] q = new int[LEN], from = new int[LEN]; int qh = 0, qt = 0;
		d[a] = 0; q[qt++] = a; from[a] = -1;
		while (qh < qt) { int c = q[qh++]; if (c == b) break;
			for (int k = 0; k < 8; k++) { int n = c + Assist.N8[k]; if (!Assist.nb(c, n) || d[n] <= d[c] + 1 || (n != b && !crossable(n, kind))) continue; d[n] = d[c] + 1; from[n] = c; q[qt++] = n; } }
		if (d[b] >= Assist.INF) return Assist.INF;
		if (first != null) { int c = b; while (from[c] >= 0 && from[c] != a) c = from[c]; first[0] = c; }
		return d[b];
	}
	static boolean prize(Heap h) {
		if (h == null || h.isEmpty() || !h.seen || Assist.passed.contains(h.pos) || Assist.skippedHeaps.contains(h.pos) || done.contains(h.pos)) return false;
		if (h.type == Heap.Type.FOR_SALE || h.type == Heap.Type.TOMB) return false;
		if (h.type == Heap.Type.LOCKED_CHEST && Notes.keys(new com.shatteredpixel.shatteredpixeldungeon.items.keys.GoldenKey(Dungeon.depth)) <= 0) return false;
		if (h.type == Heap.Type.CRYSTAL_CHEST && Notes.keys(new com.shatteredpixel.shatteredpixeldungeon.items.keys.CrystalKey(Dungeon.depth)) <= 0) return false;
		if (h.type == Heap.Type.HEAP && (!Assist.roomFor(h.peek()) || Assist.junk(h.peek()))) return false;
		return true;
	}
	static final class Notes { static int keys(com.shatteredpixel.shatteredpixeldungeon.items.keys.Key k) { return com.shatteredpixel.shatteredpixeldungeon.journal.Notes.keyCount(k); } }
	static Buff buffOf(int kind) { return kind == LEV ? H.buff(Levitation.class) : kind == INVIS ? H.buff(Invisibility.class) : kind == HASTE ? H.buff(Haste.class) : H.buff(BlobImmunity.class); }
	@SuppressWarnings("unchecked") static final Class<? extends Potion>[] KINDPOT = new Class[]{ null, PotionOfLevitation.class, PotionOfInvisibility.class, PotionOfPurity.class, PotionOfHaste.class };
	static String kindWord(int k) { return k == LEV ? "levitating" : k == INVIS ? "invisible" : k == HASTE ? "hasted" : "purity"; }
	static float left(Buff b) { return b instanceof FlavourBuff ? ((FlavourBuff) b).cooldown() : b != null ? 5 : 0; }

	/** A crossing under way: the next step to the prize (one cell at a time: Assist's own walk keeps off the danger),
	 *  then back to where it started. */
	static Target crossing() {
		Cross c = cross; if (c == null) return null;
		if (c.l != L || Actor.now() > c.until) { cross = null; return null; }
		Buff b = buffOf(c.kind); float t = left(b);
		Heap h = L.heaps.get(c.heap);
		int goal = h != null && !h.isEmpty() && h.seen ? c.heap : c.home;
		if (goal == c.home && (hp == c.home || Assist.dist[c.home] < Assist.INF && !Assist.danger[hp] && !L.pit[hp])) { cross = null; return null; }
		if (b == null) { if (goal == c.heap && Assist.walk(c.heap) >= Assist.INF) { cross = null; return null; } }
		if (hp == goal) {   // (on it: picked up; twice and still there, it can't be: given up)
			if (goal != c.heap) return null;
			if (++c.tries > 2) { done.add(c.heap); cross = null; return null; }
			return new Target(hp, Assist.PICKUP, IT + "puzzle: the prize");
		}
		int[] f = new int[1]; int w = crossWalk(hp, goal, c.kind, f);
		if (w >= Assist.INF) { cross = null; return null; }
		if (b != null && c.kind == LEV && goal == c.heap && w + 2 > t && !hasWayBack(c)) { c.heap = -1; }   // (not enough time left to get back: home)
		int step = w == 1 ? goal : f[0];
		if (Assist.bad.containsKey(step) || H.rooted) { if (c.heap >= 0) done.add(c.heap); cross = null; return null; }   // (the step did nothing when tried: given up)
		Target r = new Target(step, step == c.heap ? Assist.PICKUP : Assist.GO, IT + "puzzle: " + (goal == c.heap ? "to the prize" : "back") + " (" + kindWord(c.kind) + ")");
		r.obj = step; return r;
	}
	static boolean hasWayBack(Cross c) { return Assist.walk(c.heap) < Assist.INF; }

	/** Start a crossing: a prize seen that the plain walk can't reach (or only through danger), within the buff's
	 *  time over the cells it lets the hero cross, and the potion known. */
	static Target puzzles() {
		if (cross != null || Dungeon.bossLevel()) return null;
		Target t;
		if ((t = magicalFire()) != null) return t;
		if ((t = barricade()) != null) return t;
		if ((t = toBarricade()) != null) return t;
		if ((t = grab()) != null) return t;
		if (hpf < 0.6f) return null;
		for (int kind = LEV; kind <= HASTE; kind++) {
			// the potion: a known one, else the unknown one believed it (Clues: the floor's puzzle room puts one about), or
			// its effect on already (a test drink at the room's door that turned out to be it)
			Potion p = has(KINDPOT[kind]); Buff on = buffOf(kind); String bw = "";
			if (p == null && on == null) { Item b = Clues.believed(KINDPOT[kind], Clues.PUZ); if (b instanceof Potion) { p = (Potion) b; bw = " (" + Clues.about(b) + ")"; } }
			if (on == null && (p == null || !ok(p, Potion.AC_DRINK))) continue;
			for (Heap h : L.heaps.valueList()) {
				if (!prize(h) || (Assist.walk(h.pos) < Assist.INF && !Assist.danger[h.pos] && !(kind == HASTE && (sentryWay(h.pos) || Clues.inPuzzle(h.pos, PotionOfHaste.class))))) continue;   // (the sentries' prize: its cell is off their floor, the way to it isn't)
				if (kind == INVIS && !(Assist.fishSeen || wetWay(h.pos))) continue;
				if (kind == HASTE && !sentryWay(h.pos) && !Clues.inPuzzle(h.pos, PotionOfHaste.class)) continue;
				if (kind == PURE && !gasWay(h.pos)) continue;
				int w = crossWalk(hp, h.pos, kind, null);
				boolean back = Assist.walk(h.pos) < Assist.INF;   // (the far side joins the floor: no need to come back over)
				int budget = kind == PURE ? 18 : kind == HASTE ? 40 : 19;
				if (on != null) budget = Math.min(budget, (int) (left(on) * (kind == HASTE ? 3 : 1)) - 1);
				if (w >= Assist.INF || (back ? w : 2 * w + 1) > budget || w > (kind == HASTE ? 16 : 12)) continue;
				cross = new Cross(); cross.heap = h.pos; cross.home = hp; cross.kind = kind; cross.l = L; cross.until = Actor.now() + 40;
				String what = kind == LEV ? "traps / chasm" : kind == INVIS ? "piranhas" : kind == HASTE ? "sentries" : "gas";
				if (on != null) return crossing();
				return use(p, Potion.AC_DRINK, -1, (kind == LEV ? "levitation" : kind == INVIS ? "invisibility" : kind == HASTE ? "haste" : "purity") + ": the prize past the " + what + bw);
			}
		}
		return null;
	}
	static boolean sentryWay(int c) { if (Assist.sentryZone[c]) return true; for (int k = 0; k < 8; k++) { int n = c + Assist.N8[k]; if (Assist.nb(c, n) && Assist.sentryZone[n]) return true; } return false; }
	static boolean wetWay(int c) { for (int k = 0; k < 8; k++) { int n = c + Assist.N8[k]; if (Assist.nb(c, n) && L.water[n]) return true; } return L.water[c]; }
	static boolean gasWay(int c) { if (Assist.foul(c)) return true; for (int k = 0; k < 8; k++) { int n = c + Assist.N8[k]; if (Assist.nb(c, n) && Assist.foul(n)) return true; } return false; }

	/** The magical fire room: a wall of eternal fire; one freeze anywhere on it puts it all out (EternalFire.evolve). */
	static Target magicalFire() {
		Blob ef = L.blobs.get(MagicalFireRoom.EternalFire.class);
		if (fireL != L) { fireL = L; fireSeen.clear(); }
		if (ef == null || ef.volume <= 0 || ef.cur == null) { fireSeen.clear(); return null; }
		for (int c = 0; c < LEN; c++) if (L.heroFOV[c]) { if (ef.cur[c] > 0) fireSeen.add(c); else fireSeen.remove(c); }   // (only what the hero saw of it)
		if (fireSeen.isEmpty()) return null;
		Item rem = null;
		for (Class<? extends Item> c : Arrays.asList(PotionOfFrost.class, PotionOfSnapFreeze.class, BlizzardBrew.class, FrostBomb.class, AquaBrew.class, PotionOfStormClouds.class, PotionOfPurity.class)) {   // (not Icecap: Freezing.affect puts out plain fire only)
			Item i = has(c); if (i != null && ok(i, i instanceof Bomb ? "LIGHTTHROW" : Item.AC_THROW)) { rem = i; break; } }
		// none known: the room put a Potion of Frost among the floor's own items (MagicalFireRoom.paint:
		// addItemToSpawn), so an unknown kind first found on this floor may well be it (K6: the room's food left
		// behind on most floors that had one). Thrown from 3 or more off (a gas it turns out to be stays off the hero).
		Item bel = rem == null ? Clues.believed(PotionOfFrost.class, Clues.PUZ) : null;   // (agent ID: the colour the clues believe is the room's Frost first)
		Potion g = rem == null ? (bel instanceof Potion && ok(bel, Item.AC_THROW) ? (Potion) bel : floorGuess()) : null;
		if (rem == null && (g == null || hpf < 0.5f)) return null;
		// (the fire's cells aren't passable: a throw stops on the cell before one, as at a barricade. So the aim is a
		// free floor cell beside the fire: a Frost potion's freeze there reaches it (EternalFire looks round each cell))
		Set<Integer> aims = new HashSet<>();
		for (int f : fireSeen) for (int k = 0; k < 8; k++) { int n = f + Assist.N8[k]; if (Assist.nb(f, n) && L.passable[n] && Assist.known[n] && !fireSeen.contains(n) && Actor.findChar(n) == null) aims.add(n); }
		boolean safe = fireOff(hp);
		int best = -1, bd = 99;   // (a guess from 3 off when there's room: a gas it turns out to be stays off the hero)
		if (safe) for (int c : aims) if (L.heroFOV[c]) { int d = dist(c, hp); if (d >= 2 && d <= 6 && (d < bd || (rem == null && bd < 3 && d >= 3)) && lands(c)) { bd = d; best = c; } }
		if (K8DBG) System.out.println("    FIRE seen " + fireSeen.size() + " rem " + rem + " guess " + g + " best " + best);
		if (best >= 0) {
			if (rem != null) return use(rem, rem instanceof Bomb ? "LIGHTTHROW" : Item.AC_THROW, best, "magical fire: put it out with " + rem.getClass().getSimpleName());
			Clues.thrown(g.getClass());
			return use(g, Item.AC_THROW, best, g == bel ? "magical fire: put it out with the likely Frost (" + Clues.about(g) + ")" : "magical fire: an unknown potion found here (the room's Frost is among the floor's)");
		}
		// out of sight or out of line: to the nearest spot it can be thrown from (an aim in line, 3 to 5 off)
		if (spotL != L) { spotL = L; spotFail.clear(); }
		if (spotGo == hp) { spotFail.add(hp); spotGo = -1; }   // (sent here and no throw came of it)
		if (spotFail.size() >= 3) return null;
		int s = throwSpot(aims, 3, 5); if (s < 0) s = throwSpot(aims, 2, 5);
		if (s >= 0 && s != hp) spotGo = s;
		if (K8DBG) System.out.println("    FIRE spot " + s + " hp " + hp + " aims " + aims);
		if (s < 0 || s == hp) return null;
		Target t = new Target(s, Assist.GO, IT + "magical fire: to a spot to throw " + (rem != null ? "the remedy" : "a guess") + " from"); t.obj = s; return t;
	}
	/** Out of the eternal fire's reach (it sets alight whoever stands on or beside it). */
	static boolean fireOff(int c) { for (int f : fireSeen) if (dist(f, c) < 2) return false; return true; }
	static final Set<Integer> fireSeen = new HashSet<>(), spotFail = new HashSet<>(); static Level fireL, spotL; static int spotGo = -1;
	/** The nearest cell the hero can walk to (none of the danger) from which a throw lands on one of the cells, dmin to dmax off. */
	static int throwSpot(Collection<Integer> cells, int dmin, int dmax) {
		int best = -1, bd = Assist.INF;
		for (int t : cells) for (int dy = -dmax; dy <= dmax; dy++) for (int dx = -dmax; dx <= dmax; dx++) {
			int d = Math.max(Math.abs(dx), Math.abs(dy)); if (d < dmin) continue;
			int sx = x(t) + dx, sy = y(t) + dy; if (sx < 0 || sy < 0 || sx >= W || sy >= L.height()) continue;
			int c = sx + sy * W;
			if (Assist.dist[c] >= bd || !Assist.open[c] || Actor.findChar(c) != null && c != hp || L.flamable[c] || !fireOff(c) || spotFail.contains(c)) continue;
			if (Assist.line(c, t)) { bd = Assist.dist[c]; best = c; }
		}
		return bd <= 40 ? best : -1;
	}

	// the floors' remedies: a magical fire room puts a Potion of Frost among the floor's items, a storage room (its
	// barricade) and the Wandmaker's grave one of Liquid Flame, a traps room one of Levitation (each Room.paint:
	// level.addItemToSpawn). The source's knowledge, used on what the player saw: which unknown potion kinds were
	// first picked up on which floor.
	static final boolean K8DBG = System.getenv("K8DBG") != null;
	static final Map<Class<?>, Integer> potionFloor = new HashMap<>(); static Object potionHero;
	static void notePotions() {
		if (potionHero != H) { potionHero = H; potionFloor.clear(); }
		for (Item i : H.belongings) if (i instanceof Potion && !Assist.known(i)) potionFloor.putIfAbsent(i.getClass(), Dungeon.depth);
	}
	/** An unknown potion of a kind first found on this floor (the fewest held first: the remedy is one potion), or null. */
	static Potion floorGuess() {
		Potion best = null;
		for (Item i : H.belongings) if (i instanceof Potion && !Assist.known(i) && !(i instanceof com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.ExoticPotion) && Integer.valueOf(Dungeon.depth).equals(potionFloor.get(i.getClass())) && ok(i, Item.AC_THROW))
			if (best == null || i.quantity() < best.quantity()) best = (Potion) i;
		return best;
	}
	/** Unknown potions found on this floor kept back from drinking to learn while a room's remedy is still wanted:
	 *  the magical fire burns in sight, a barricade with the unknown behind it is seen. */
	static boolean remedyHeld(Item i) {
		try {
			H = Dungeon.hero; L = Dungeon.level; if (H == null || L == null || !(i instanceof Potion) || Assist.known(i)) return false;
			notePotions();
			if (!Integer.valueOf(Dungeon.depth).equals(potionFloor.get(i.getClass()))) return false;
			Blob ef = L.blobs.get(MagicalFireRoom.EternalFire.class);
			if (ef != null && ef.volume > 0 && ef.cur != null) for (int c = 0; c < L.length(); c++) if (ef.cur[c] > 0 && Assist.known[c]) return true;
			for (int c = 0; c < L.length(); c++) if (L.map[c] == Terrain.BARRICADE && Assist.known[c]) {
				for (int k = 0; k < 8; k++) { int n = c + Assist.N8[k]; if (Assist.nb(c, n) && !Assist.known[n]) return true; } }
			return false;
		} catch (Throwable e) { return false; }
	}

	/** A barricade with the unknown behind it (a storage room): burnt with known fire, or blown up. (The Wandmaker's
	 *  grave is AsQuests'.) */
	static Target barricade() {
		if (K8DBG && barL == L && barAim >= 0) { StringBuilder b = new StringBuilder("    BAR t " + Actor.now() + " aim " + barAim + ":"); for (int k = 0; k < 8; k++) { int n = barAim + Assist.N8[k]; b.append(' ').append(n).append('=').append(L.map[n]).append(Assist.hazMem[n] ? "H" : "").append(Assist.known[n] ? "k" : "").append(L.heroFOV[n] ? "v" : "").append(Assist.dist[n] < Assist.INF ? "d" + Assist.dist[n] : ""); } System.out.println(b);
			int f0 = barAim; for (int yy = y(f0) - 6; yy <= y(f0) + 6; yy++) { StringBuilder bb = new StringBuilder("     ");
				for (int xx = x(f0) - 10; xx <= x(f0) + 10; xx++) { int c = xx + yy * W; if (c < 0 || c >= LEN || xx < 0 || xx >= W) { bb.append(' '); continue; }
					bb.append(c == hp ? '@' : !Assist.known[c] ? ' ' : L.solid[c] ? '#' : Assist.dist[c] >= Assist.INF ? 'x' : !Assist.open[c] ? 'o' : Assist.unseenBy(c) ? 'u' : '.'); }
				System.out.println(bb); } }
		Target t = barricade0(); if (t != null) { barT = Actor.now(); barL = L; barAim = t.cell; } return t; }
	static int barAim = -1;
	static float barT = -99; static Level barL;
	/** A throw at a barricade lately (fire takes a few turns to burn it: K8, a Frost guess thrown next put out the
	 *  Liquid Flame just thrown), or fire burning beside one. */
	static boolean barWait() {
		if (barL == L && Actor.now() - barT < 6) return true;
		for (int c = 0; c < LEN; c++) if (L.map[c] == Terrain.BARRICADE && Assist.known[c]) for (int k = 0; k < 8; k++) { int n = c + Assist.N8[k]; if (Assist.nb(c, n) && Assist.fireAt(n)) return true; }
		return false;
	}
	static Target barricade0() {
		if (AsQuests.graveOpen() || barWait()) return null;
		// (a barricade is solid: a throw stops on the cell before it, so the aim is a free floor cell beside it, on the
		// hero's side; Liquid Flame's 3x3 of flames, a bomb's blast or a Fireblast cone there reaches it: K4's finding)
		int aim = -1, bar = -1, bd = 99;
		for (int c = 0; c < LEN; c++) if (L.map[c] == Terrain.BARRICADE && Assist.known[c] && L.heroFOV[c]) {
			boolean behind = false; for (int k = 0; k < 8; k++) { int n = c + Assist.N8[k]; if (Assist.nb(c, n) && !Assist.known[n]) behind = true; }
			if (!behind) continue;
			for (int k = 0; k < 8; k++) { int n = c + Assist.N8[k];
				if (!Assist.nb(c, n) || !L.passable[n] || L.solid[n] || Actor.findChar(n) != null || !lands(n)) continue;
				int d = dist(n, hp); if (d >= 2 && d <= 4 && d < bd) { bd = d; aim = n; bar = c; } }
		}
		if (aim < 0) return null;
		if (!L.flamable[hp] && !fireReaches(aim, 1) || bd >= 3) {
			PotionOfLiquidFlame lf = has(PotionOfLiquidFlame.class); if (lf != null && !L.flamable[hp]) return use(lf, Item.AC_THROW, aim, "barricade: burn it");
			Item blf = Clues.believed(PotionOfLiquidFlame.class, Clues.PUZ);   // (the storage room puts a Liquid Flame on its floor: the colour believed it)
			if (lf == null && blf != null && !L.flamable[hp] && ok(blf, Item.AC_THROW)) { Clues.thrown(blf.getClass()); return use(blf, Item.AC_THROW, aim, "barricade: burn it with the likely Liquid Flame (" + Clues.about(blf) + ")"); }
			Item fb = wand(WandOfFireblast.class); if (fb != null && fireOk()) return use(fb, zapAct(fb), aim, "barricade: burn it");
			InfernalBrew ib = has(InfernalBrew.class); if (ib != null && bd >= 4) return use(ib, Item.AC_THROW, aim, "barricade: burn it");
		}
		// (no plain Bomb on a barricade: bombs are kept for the climb, AsEconomy.bombSpendOk)
		// none known: the storage room put a Potion of Liquid Flame among the floor's items (StorageRoom.paint): an
		// unknown kind first found here, thrown from 3 or more off and off grass (K6: the room's food never reached)
		Potion g = floorGuess();
		if (g != null && bd >= 3 && !L.flamable[hp] && hpf >= 0.5f) return use(g, Item.AC_THROW, aim, "barricade: an unknown potion found here (the room's Liquid Flame is among the floor's)");
		return null;
	}
	/** A barricade seen with the unknown behind it, a remedy in hand (known fire, a floor's guess) but no throw
	 *  from here: to the nearest spot one lands from, 3 or 4 off its aim (K6: the storage room's food never seen). */
	static Target toBarricade() {
		if (AsQuests.graveOpen() || hpf < 0.5f || barWait()) return null;
		boolean rem = has(PotionOfLiquidFlame.class) != null || wand(WandOfFireblast.class) != null || has(InfernalBrew.class) != null || floorGuess() != null;
		if (!rem) return null;
		Set<Integer> aims = new HashSet<>();
		for (int c = 0; c < LEN; c++) if (L.map[c] == Terrain.BARRICADE && Assist.known[c]) {
			boolean behind = false; for (int k = 0; k < 8; k++) { int n = c + Assist.N8[k]; if (Assist.nb(c, n) && !Assist.known[n]) behind = true; }
			if (!behind) continue;
			for (int k = 0; k < 8; k++) { int n = c + Assist.N8[k]; if (Assist.nb(c, n) && L.passable[n] && !L.solid[n] && Assist.known[n] && Assist.dist[n] < Assist.INF && Actor.findChar(n) == null) aims.add(n); }
		}
		if (aims.isEmpty()) return null;
		if (spotL != L) { spotL = L; spotFail.clear(); }
		if (spotGo == hp) { spotFail.add(hp); spotGo = -1; }   // (sent here and no throw came of it: K8, sent back and forth between it and a pickup 120 times)
		if (spotFail.size() >= 3) return null;
		int s = throwSpot(aims, 3, 4);
		if (s < 0 || s == hp) return null;
		spotGo = s;
		Target t = new Target(s, Assist.GO, IT + "barricade: to a spot to throw from"); t.obj = s; return t;
	}

	/** Telekinetic Grab: a prize in sight the hero can't walk to (across water with piranhas, a chasm, fire). */
	static Target grab() {
		TelekineticGrab tg = has(TelekineticGrab.class); if (tg == null) return null;
		for (Heap h : L.heaps.valueList()) if (h.type == Heap.Type.HEAP && prize(h) && L.heroFOV[h.pos] && (Assist.walk(h.pos) >= Assist.INF || Assist.danger[h.pos]) && dist(h.pos, hp) >= 2 && lands(h.pos))
			return use(tg, Spell.AC_CAST, h.pos, "telekinetic grab: out of reach");
		return null;
	}

	// =============================================================================================================
	// the Mage's staff
	// =============================================================================================================

	/** How good a wand is in the staff for a bot (strategy-reference 3.2): simple direct damage first. */
	static int staffRank(Class<?> c) {
		HeroSubClass sub = H.subClass;
		if (c == WandOfDisintegration.class || c == WandOfLightning.class) return 9;
		if (c == WandOfBlastWave.class || c == WandOfCorrosion.class || c == WandOfFrost.class) return 8;
		if (c == WandOfFireblast.class || c == WandOfPrismaticLight.class) return 7;
		if (c == WandOfLivingEarth.class) return sub == HeroSubClass.BATTLEMAGE ? 7 : 5;
		if (c == WandOfRegrowth.class || c == WandOfWarding.class || c == WandOfTransfusion.class) return 5;
		if (c == WandOfMagicMissile.class) return 4;
		if (c == WandOfCorruption.class) return sub == HeroSubClass.WARLOCK ? 5 : 3;
		return 2;
	}
	/** Imbue the staff with a better wand from the pack (known uncursed: a cursed one curses the staff); with Wand
	 *  Preservation unspent the old one comes back, so a small step up is worth it too. The staff keeps its levels. */
	static Target imbue() {
		MagesStaff st = H.belongings.getItem(MagesStaff.class); if (st == null || !ok(st, MagesStaff.AC_IMBUE)) return null;
		Wand cur = staffWand(); int cr = cur == null ? 0 : staffRank(cur.getClass());
		boolean keep = H.hasTalent(Talent.WAND_PRESERVATION) && H.buff(Talent.WandPreservationCounter.class) == null;
		Wand best = null; int br = cr + (keep ? 1 : 2);
		for (Item i : H.belongings.backpack) if (i instanceof Wand) { Wand w = (Wand) i;
			if (!w.cursedKnown || w.cursed) continue;
			int r = staffRank(w.getClass()) + (w.levelKnown && w.level() > st.level() ? 1 : 0);
			if (r >= br) { br = r + 1; best = w; } }
		if (best == null) return null;
		return use(st, MagesStaff.AC_IMBUE, -1, best, "imbue the staff: " + best.getClass().getSimpleName().replace("WandOf", "") + (keep ? " (preservation keeps the old)" : ""));
	}

	// =============================================================================================================
	// the calm uses
	// =============================================================================================================

	static Target calmPotions() {
		Potion p;
		// at once: strength-like and talents
		if ((p = has(ElixirOfMight.class)) != null) return use(p, Potion.AC_DRINK, -1, "elixir of might");
		if ((p = has(PotionOfDivineInspiration.class)) != null && divineTier() > 0) return use(p, Potion.AC_DRINK, -1, "divine inspiration");
		if ((p = has(PotionOfMastery.class)) != null) { Item g = masteryTarget(); if (g != null) return use(p, Potion.AC_DRINK, -1, g, "mastery: " + g.getClass().getSimpleName()); }
		// debuffs
		if ((p = has(PotionOfCleansing.class)) != null) { int bad = 0; for (Buff b : H.buffs()) if (b.type == Buff.buffType.NEGATIVE && !(b instanceof Hunger)) bad += b instanceof Poison || b instanceof Corrosion || b instanceof Ooze || b instanceof Bleeding ? 2 : 1; if (bad >= 3) return use(p, Potion.AC_DRINK, -1, "cleansing: debuffs"); }
		if (hpf < 0.5f && (p = has(ElixirOfAquaticRejuvenation.class)) != null && H.buff(ElixirOfAquaticRejuvenation.AquaHealing.class) == null) return use(p, Potion.AC_DRINK, -1, "aquatic rejuvenation: hurt");
		// scouting a new floor (not a boss's: nothing to find)
		if (fresh() && !Dungeon.bossLevel() && Dungeon.depth >= 3 && H.buff(MindVision.class) == null && H.buff(MagicalSight.class) == null) {
			PotionOfMindVision mv = has(PotionOfMindVision.class);
			if (mv != null && (mv.quantity() >= 2 || Dungeon.depth >= 8)) return use(mv, Potion.AC_DRINK, -1, "mind vision: a new floor");
			PotionOfMagicalSight ms = has(PotionOfMagicalSight.class);
			if (ms != null && Dungeon.depth >= 8) return use(ms, Potion.AC_DRINK, -1, "magical sight: a new floor");
		}
		return null;
	}
	/** A tier where Divine Inspiration's 2 points would go (unlocked, not boosted yet), 0 none. */
	static int divineTier() {
		try { Object c = H.buff(PotionOfDivineInspiration.DivineInspirationTracker.class); for (int t = Math.min(4, H.talents.size()); t >= 1; t--) { if (H.lvl < Talent.tierLevelThresholds[t]) continue; if (c == null || !((PotionOfDivineInspiration.DivineInspirationTracker) c).isBoosted(t)) return t; } } catch (Throwable e) { }
		return 0;
	}
	/** Mastery's target: worn gear that asks more strength than the hero has, else the worn armour. */
	static Item masteryTarget() {
		Item a = H.belongings.armor(), w = H.belongings.weapon();
		boolean wo = w instanceof MeleeWeapon && !(w instanceof MagesStaff) && !((Weapon) w).masteryPotionBonus, ao = a instanceof Armor && !((Armor) a).masteryPotionBonus;
		if (wo && ((MeleeWeapon) w).STRReq() > H.STR()) return w;
		if (ao && ((Armor) a).STRReq() > H.STR()) return a;
		return ao ? a : wo ? w : null;
	}

	static Target calmScrolls() {
		if (!scrollsOk()) return null;
		Scroll s;
		int ex = L.exit(); boolean noExit = ex <= 0 || !Assist.known[ex];
		if ((s = has(ScrollOfDivination.class)) != null && unknownKinds() >= 4) return use(s, Scroll.AC_READ, -1, "divination: unknown kinds");
		if ((s = has(ScrollOfForesight.class)) != null && noExit && Actor.now() - Assist.floorT0 > 250 && H.buff(Foresight.class) == null) return use(s, Scroll.AC_READ, -1, "foresight: the way on hidden");
		if ((s = has(ScrollOfMysticalEnergy.class)) != null) { Artifact a = wornArtifact(); if (a != null && artifactLow(a)) return use(s, Scroll.AC_READ, -1, "mystical energy: " + a.getClass().getSimpleName()); }
		if ((s = has(ScrollOfEnchantment.class)) != null) { Item g = enchantTarget(); if (g != null) return use(s, Scroll.AC_READ, -1, g, "enchantment: " + g.getClass().getSimpleName()); }
		if ((s = has(ScrollOfTransmutation.class)) != null) { Item g = transmuteTarget(); if (g != null) return use(s, Scroll.AC_READ, -1, g, "transmute: " + g.getClass().getSimpleName()); }
		return null;
	}
	static int unknownKinds() { return Potion.getUnknown().size() + Scroll.getUnknown().size(); }
	static Artifact wornArtifact() { Item a = H.belongings.artifact(); if (a instanceof Artifact) return (Artifact) a; Item m = H.belongings.misc(); return m instanceof Artifact ? (Artifact) m : null; }
	static boolean artifactLow(Artifact a) { Object c = Assist.field(a, "charge"), cap = Assist.field(a, "chargeCap"); return c instanceof Integer && cap instanceof Integer && (Integer) cap > 0 && (Integer) c * 2 < (Integer) cap; }
	/** Worn gear with no enchantment (or a curse shown), the weapon first (it's what kills). */
	static Item enchantTarget() {
		Item w = H.belongings.weapon(), a = H.belongings.armor();
		if (w instanceof Weapon && !(w instanceof MagesStaff) && (((Weapon) w).enchantment == null || (w.cursedKnown && ((Weapon) w).hasCurseEnchant()))) return w;
		if (a instanceof Armor && (((Armor) a).glyph == null || (a.cursedKnown && ((Armor) a).hasCurseGlyph()))) return a;
		return null;
	}
	/** Transmutation's target: a spare wand the staff and the hero won't use (a low rank, known), else none: never
	 *  main gear (strategy-reference 6.2). */
	static Item transmuteTarget() {
		Wand bad = null; int br = 5;
		for (Item i : H.belongings.backpack) if (i instanceof Wand && ((Wand) i).levelKnown) { int r = staffRank(i.getClass()); if (r < br) { br = r; bad = (Wand) i; } }
		return bad;
	}

	static Target stones() {
		Runestone s;
		if ((s = has(StoneOfAugmentation.class)) != null) { Item w = H.belongings.weapon(), a = H.belongings.armor();
			Item g = w instanceof Weapon && !(w instanceof MagesStaff) && ((Weapon) w).augment == Weapon.Augment.NONE ? w : a instanceof Armor && ((Armor) a).augment == Armor.Augment.NONE ? a : null;
			if (g != null) return use(s, InventoryStone.AC_USE, -1, g, "augmentation: " + g.getClass().getSimpleName()); }
		if ((s = has(StoneOfEnchantment.class)) != null) { Item g = enchantTarget(); if (g != null) return use(s, InventoryStone.AC_USE, -1, g, "enchantment stone: " + g.getClass().getSimpleName()); }
		if ((s = has(StoneOfDetectMagic.class)) != null) { Item g = null; for (Item i : H.belongings.backpack) if ((i instanceof EquipableItem || i instanceof Wand) && !i.isIdentified() && !(i.cursedKnown && i.levelKnown)) { g = i; break; }
			if (g != null) return use(s, InventoryStone.AC_USE, -1, g, "detect magic: " + g.getClass().getSimpleName()); }
		if ((s = has(StoneOfIntuition.class)) != null) { Item g = intuitionTarget(); if (g != null) return use(s, InventoryStone.AC_USE, -1, g, "intuition: the likeliest guess"); }
		if ((s = has(StoneOfClairvoyance.class)) != null) { int ex = L.exit(); if ((ex <= 0 || !Assist.known[ex]) && Actor.now() - Assist.floorT0 > 150) { int c = scoutCell(); if (c >= 0) return use(s, Item.AC_THROW, c, "clairvoyance: the way on hidden"); } }
		return null;
	}
	/** The unknown potion or scroll whose likeliest kind is likeliest (the guess most often right). */
	static Item intuitionTarget() {
		Item best = null; double bp = 0.35;
		for (Item i : H.belongings.backpack) if ((i instanceof Potion || i instanceof Scroll) && !Assist.known(i) && !(i instanceof ExoticPotion) && !(i instanceof ExoticScroll)) { Class<?> k = likeliest(i); double p = k == null ? 0 : Identify.p(i.getClass(), k); if (p > bp) { bp = p; best = i; } }
		return best;
	}
	static Class<?> likeliest(Item i) {
		if (i instanceof ExoticPotion) { Class<?> r = ExoticPotion.exoToReg.get(i.getClass()); if (r == null) return null; Set<?> unk = Potion.getUnknown(); Class<?> b = null; double bp = -1; for (Object o : unk) { double p = Identify.p(r, (Class<?>) o); if (p > bp) { bp = p; b = (Class<?>) o; } } return b; }
		if (i instanceof ExoticScroll) { Class<?> r = ExoticScroll.exoToReg.get(i.getClass()); if (r == null) return null; Set<?> unk = Scroll.getUnknown(); Class<?> b = null; double bp = -1; for (Object o : unk) { double p = Identify.p(r, (Class<?>) o); if (p > bp) { bp = p; b = (Class<?>) o; } } return b; }
		Set<?> unk = i instanceof Potion ? Potion.getUnknown() : Scroll.getUnknown(); Class<?> b = null; double bp = -1;
		for (Object o : unk) { Class<?> k = (Class<?>) o; double p = Identify.p(i.getClass(), k); if (p > bp) { bp = p; b = k; } }
		return b;
	}
	/** The known open cell in a clear throw with the most unknown round it (8 cells). */
	static int scoutCell() {
		int best = -1, bs = 8;
		for (int c = 0; c < LEN; c++) { if (!Assist.open[c] || dist(c, hp) < 3 || !lands(c)) continue;
			int u = 0; for (int k = 0; k < LEN; k++) if (!Assist.known[k] && dist(k, c) <= 8) u++;
			if (u > bs) { bs = u; best = c; } }
		return best;
	}

	static Target spells() {
		Spell s;
		if ((s = has(MagicalInfusion.class)) != null) { Item g = UpgradePlan.target(H); if (g == null) g = H.belongings.armor(); if (g != null && g.isUpgradable()) return use(s, Spell.AC_CAST, -1, g, "magical infusion: " + g.getClass().getSimpleName()); }
		if ((s = has(ReclaimTrap.class)) != null && H.buff(ReclaimTrap.ReclaimedTrap.class) == null) {   // (a known trap to store, then fire it at foes)
			for (int c = 0; c < LEN; c++) { Trap tr = L.traps.get(c); if (tr != null && tr.visible && tr.active && L.heroFOV[c] && trapArea(tr) > 0 && dist(c, hp) <= 6 && bolt(c)) return use(s, Spell.AC_CAST, c, "reclaim the trap: " + tr.getClass().getSimpleName()); }
		}
		BeaconOfReturning be = has(BeaconOfReturning.class);
		if (be != null && H.buff(BeaconOfReturning.BeaconTracker.class) == null && hp == L.entrance() && !Dungeon.bossLevel() && Dungeon.depth % 5 != 0) { beaconReturn = false; return use(be, Spell.AC_CAST, -1, "beacon: set at the stairs"); }
		return null;
	}

	/** Known-cursed gear: a spare Remove Curse on a cursed piece in the pack better than what's worn (tier), when the
	 *  scroll isn't needed for what's worn. (Curse odds of unknown gear: GearValue's.) */
	static Target curses() {
		if (!scrollsOk()) return null;
		ScrollOfRemoveCurse rc = has(ScrollOfRemoveCurse.class); if (rc == null) return null;
		Item w = H.belongings.weapon(), a = H.belongings.armor();
		if ((w != null && w.visiblyCursed()) || (a != null && a.visiblyCursed())) return null;   // (the hands read it on the worn one)
		for (Item i : H.belongings.backpack) {
			if (!i.visiblyCursed()) continue;
			if (i instanceof Armor && (a == null || ((Armor) i).tier > ((Armor) a).tier) && ((Armor) i).STRReq() <= H.STR()) return use(rc, Scroll.AC_READ, -1, i, "remove curse: a better armour");
			if (i instanceof MeleeWeapon && !(w instanceof MagesStaff) && (!(w instanceof MeleeWeapon) || ((MeleeWeapon) i).tier > ((MeleeWeapon) w).tier) && ((MeleeWeapon) i).STRReq() <= H.STR()) return use(rc, Scroll.AC_READ, -1, i, "remove curse: a better weapon");
		}
		return null;
	}

	// =============================================================================================================
	// the item windows, for the bot (the watch's player answers them himself)
	// =============================================================================================================

	/** The button of an item's window to press, or null (no view). */
	static Ui.Part choose(Window w, List<Ui.Part> bs) {
		try {
			String n = w.getClass().getName();
			Hero h = Dungeon.hero; if (h == null) return null;
			if (n.contains("StoneOfAugmentation")) {   // weapon: damage on a slow, heavy one, speed on a light one; armour: defence
				String want = "defen";
				Item wpn = h.belongings.weapon();
				if (n.contains("WndAugment")) for (Ui.Part p : bs) { String t = p.text.toLowerCase(Locale.ROOT); if (t.contains("damage") || t.contains("speed")) { want = wpn instanceof MeleeWeapon && ((MeleeWeapon) wpn).tier >= 4 ? "damage" : "speed"; break; } }
				for (Ui.Part p : bs) if (p.text.toLowerCase(Locale.ROOT).contains(want)) return p;
			}
			if (n.contains("StoneOfIntuition")) {   // the guess: the label's likeliest kind (Identify's odds), then the guess button
				Object mem = Assist.field(w, "members"); if (!(mem instanceof List)) return null;
				for (Object g : new ArrayList<>((List<?>) mem)) if (g instanceof com.shatteredpixel.shatteredpixeldungeon.ui.RedButton) {
					Object it = Assist.field(g, "val$item"); if (!(it instanceof Item)) continue;
					Item i = (Item) it; Class<?> k = likeliest(i); if (k == null) return null;
					if (!(i instanceof ExoticPotion) && !(i instanceof ExoticScroll)) Clues.guess(i.getClass(), k);   // (wrong, it rules the kind out: Clues)
					if (i instanceof ExoticPotion) k = ExoticPotion.regToExo.get(k); else if (i instanceof ExoticScroll) k = ExoticScroll.regToExo.get(k);
					java.lang.reflect.Field f = StoneOfIntuition.class.getDeclaredField("curGuess"); f.setAccessible(true); f.set(null, k);
					return new Ui.Part((com.watabou.noosa.Gizmo) g, "guess", true);
				}
			}
			if (n.contains("BeaconOfReturning")) { String want = beaconReturn ? "return" : "set"; for (Ui.Part p : bs) if (p.text.toLowerCase(Locale.ROOT).contains(want)) return p; }
		} catch (Throwable e) { Assist.lastError = e; }
		return null;
	}
}
