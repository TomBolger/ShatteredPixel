package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Shopkeeper;
import com.shatteredpixel.shatteredpixeldungeon.items.Ankh;
import com.shatteredpixel.shatteredpixeldungeon.items.Honeypot;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.Waterskin;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.*;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.*;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.*;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.*;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.*;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.*;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.*;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.darts.*;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.plants.*;
import java.util.*;
import pebxel.Assist.Target;
import pebxel.Brain.Cand;
import pebxel.Brain.Role;

/**
 * Items and economy (agent WB; strategy-reference 2.7, 2.8, 3.2, 6.10): what the shop, the waterskin and the pot are
 * for, as suggestions with their reasons.
 *
 *   the Ankh      blessed with a full waterskin (a second life in place, the pack kept, not WndResurrect's loss);
 *                 the dew kept for it while one is held unblessed ({@link #blessAnkh}, {@link #dewVeto});
 *   darts         plain darts tipped with a seed the bot has a job for (paralysis, chill, blindness, poison, shock);
 *   the shop      nothing that reaches the shopkeeper: a second harm makes him flee with all the stock
 *                 (Shopkeeper.processHarm), so bombs, gas, fire, sprung traps, area scrolls and bolts are vetoed near
 *                 him ({@link #shopVeto}: a Rules row);
 *   Lightning     never zapped when its arcs reach the hero (one in water arcs 2 cells; the hero beside the chain is
 *                 struck: WandOfLightning.arc) ({@link #lightningVeto});
 *   trinkets      the bot-safe ones (Salt Cube, Clover, Mossy Clump), never the Mimic Tooth or Cracked Spyglass
 *                 ({@link #trinketWorth}: AsChoices' pick, AsAlchemy's upgrades);
 *   Infusion      a Scroll of Upgrade kept for Magical Infusion with the pot on the floor, for an enchanted piece the
 *                 next upgrade could strip ({@link #infusionWanted}).
 *
 * Only what the player sees: known items, visible characters, the shopkeeper's warning (he yells it).
 * AS_ECON=0 turns the suggestions off (the Rules rows have their own switches).
 */
final class AsEconomy {
	private AsEconomy() {}

	static final boolean ON = Assist.opt("ECON", true);
	static final String WHY = "econ: ";

	// =============================================================================================================
	// the hook
	// =============================================================================================================

	static Target suggest(Target planA) {
		try {
			observe();
			if (!ON) return null;
			Hero h = Dungeon.hero; if (h == null || !h.isAlive() || !calm()) return null;
			Target t;
			if ((t = blessAnkh()) != null) return t;
			if ((t = tipDarts()) != null) return t;
			return null;
		} catch (Throwable e) { Assist.lastError = e; return null; }
	}

	static boolean calm() { return !Assist.seenFoe && !Assist.threatNear && !Assist.inDanger && !Assist.foul(Dungeon.hero.pos) && !Assist.burning(); }

	// =============================================================================================================
	// the Ankh
	// =============================================================================================================

	/** An Ankh the hero holds, not blessed, or null. */
	static Ankh unblessed() {
		Hero h = Dungeon.hero; if (h == null) return null;
		for (Ankh a : h.belongings.getAllItems(Ankh.class)) if (!a.isBlessed()) return a;
		return null;
	}
	static boolean blessedHeld() { Hero h = Dungeon.hero; if (h == null) return false; for (Ankh a : h.belongings.getAllItems(Ankh.class)) if (a.isBlessed()) return true; return false; }

	/** Bless the held Ankh now (the waterskin full: 20 dew), or null. For the boss preparation too (agent WA). */
	public static Target blessAnkh() {
		Ankh a = unblessed(); if (a == null) return null;
		Waterskin ws = Dungeon.hero.belongings.getItem(Waterskin.class);
		if (ws == null || !ws.isFull() || !a.actions(Dungeon.hero).contains(Ankh.AC_BLESS) || Assist.declined(Ankh.class, Ankh.AC_BLESS)) return null;
		return Assist.use(a, Ankh.AC_BLESS, -1, WHY + "bless the Ankh: the full waterskin buys a second life in place, the pack kept");
	}

	/** The dew still wanted: an unblessed Ankh held and the waterskin not full (agent WA: fill it before a boss). */
	public static boolean dewWanted() {
		if (unblessed() == null) return false;
		Waterskin ws = Dungeon.hero.belongings.getItem(Waterskin.class);
		return ws != null && !ws.isFull();
	}

	/** Rules: the dew drunk for a scratch ("hurt: dew, not food") while an Ankh waits for its blessing. */
	static boolean dewVeto(Cand c) {
		Target t = c.t;
		return t != null && t.kind == Assist.USE && t.item instanceof Waterskin && "DRINK".equals(t.action) && t.why.startsWith("hurt: dew") && unblessed() != null;
	}

	// =============================================================================================================
	// darts
	// =============================================================================================================

	/** Seeds worth more on a dart than in a pot or a path (AsItems.tipped throws these), in that order. */
	static final List<Class<? extends Plant.Seed>> TIP = Arrays.asList(Earthroot.Seed.class, Icecap.Seed.class, Blindweed.Seed.class, Sorrowmoss.Seed.class, Stormvine.Seed.class);

	static Target tipDarts() {
		Hero h = Dungeon.hero; Dart d = null;
		for (Item i : h.belongings.backpack) if (i.getClass() == Dart.class && i.quantity() >= 2) d = (Dart) i;
		if (d == null || Assist.declined(Dart.class, "TIP")) return null;
		for (Class<? extends Plant.Seed> c : TIP) {
			Plant.Seed s = h.belongings.getItem(c); if (s == null) continue;
			Class<? extends TippedDart> out = TippedDart.types.get(c);
			Target t = Assist.use(d, "TIP", -1, WHY + "tip darts: " + (out == null ? c.getSimpleName() : out.getSimpleName()) + " (a seed makes two)");
			t.on = s;
			return t;
		}
		return null;
	}

	// =============================================================================================================
	// the shopkeeper
	// =============================================================================================================

	/** The shopkeepers the hero knows of on this floor (seen, or their cell seen: they never move). */
	static List<Shopkeeper> shopkeepers() {
		List<Shopkeeper> out = new ArrayList<>(); Level L = Dungeon.level; if (L == null) return out;
		for (Mob m : L.mobs) if (m instanceof Shopkeeper && m.isAlive() && m.pos >= 0 && m.pos < L.length() && (L.heroFOV[m.pos] || L.visited[m.pos])) out.add((Shopkeeper) m);
		return out;
	}
	/** He yelled his warning (the next harm a turn on makes him flee). */
	static boolean warned(Shopkeeper s) { Object v = Assist.field(s, "turnsSinceHarmed"); return v instanceof Integer && (Integer) v >= 0; }

	/** Rules: an item use whose effect reaches a shopkeeper (a rescue only while he hasn't warned yet: one harm is a warning). */
	static boolean shopVeto(Cand c) {
		try {
			List<Shopkeeper> sks = shopkeepers(); if (sks.isEmpty()) return false;
			if (c.role == Role.ESCAPE || c.role == Role.HEAL) { boolean any = false; for (Shopkeeper s : sks) any |= warned(s); if (!any) return false; }
			return reaches(c.t, sks);
		} catch (Throwable e) { Assist.lastError = e; return false; }
	}

	static int d(int a, int b) { int w = Dungeon.level.width(); return Math.max(Math.abs(a % w - b % w), Math.abs(a / w - b / w)); }

	/** Does t's effect reach one of sks? (thrown: where it lands, the trap it springs, its spread; zapped: the bolt's
	 *  path and its area; read: the scrolls that strike everyone in view) */
	static boolean reaches(Target t, List<Shopkeeper> sks) {
		if (t == null || t.kind != Assist.USE || t.item == null || t.action == null) return false;
		Hero h = Dungeon.hero; Level L = Dungeon.level; Item i = t.item; String a = t.action; int hp = h.pos;
		int land = t.cell, r = -1;
		if (Item.AC_THROW.equals(a) || "LIGHTTHROW".equals(a)) {   // (a bomb: lit and thrown)
			if (t.cell < 0 || t.cell == hp) return false;
			Ballistica b = new Ballistica(hp, t.cell, Ballistica.PROJECTILE); land = b.collisionPos == null ? t.cell : b.collisionPos;
			for (Shopkeeper s : sks) if (s.pos == land) return true;   // (a thrown thing stops on the first one in its way)
			if (i instanceof Bomb) r = AsItems.bombRange(i) + 1;
			else if (i instanceof PotionOfFrost || i instanceof PotionOfSnapFreeze || i instanceof PotionOfLiquidFlame || i instanceof PotionOfDragonsBreath) r = 3;
			else if (i instanceof Potion && !(i instanceof PotionOfHealing && Assist.known(i))) r = 6;   // (gas spreads through the room; an unknown one may be gas)
			else if (i instanceof StoneOfBlast || i instanceof StoneOfShock || i instanceof StoneOfDeepSleep || i instanceof StoneOfFear || i instanceof StoneOfAggression) r = 3;
			else if (i instanceof IncendiaryDart || i instanceof Honeypot || i instanceof ShockingDart) r = 3;
			Trap tr = L.traps.get(land);
			if (tr != null && tr.visible && tr.active) r = Math.max(r, 3 + AsItems.trapArea(tr));   // (a trap sprung on purpose)
		} else if (Wand.AC_ZAP.equals(a) || MagesStaff.AC_ZAP.equals(a)) {
			Wand w = i instanceof Wand ? (Wand) i : i instanceof MagesStaff ? staffWand((MagesStaff) i) : null;
			if (w == null || t.cell < 0) return false;
			Ballistica b = new Ballistica(hp, t.cell, w.collisionProperties(t.cell)); land = b.collisionPos == null ? t.cell : b.collisionPos;
			int far = w instanceof WandOfDisintegration || w instanceof WandOfFireblast ? Math.min(b.dist, 12) : b.dist;
			for (int c : b.subPath(1, far)) for (Shopkeeper s : sks) if (s.pos == c) return true;
			if (w instanceof WandOfLightning) { Set<Char> ch = chain(land); for (Shopkeeper s : sks) if (ch.contains(s)) return true; return false; }
			if (w instanceof WandOfFireblast) { for (Shopkeeper s : sks) if (cone(hp, t.cell, s.pos, 7)) return true; return false; }
			if (w instanceof WandOfBlastWave) r = 2;
			else if (w instanceof WandOfCorrosion || w instanceof WandOfRegrowth) r = 4;
			else r = -1;
		} else if (Scroll.AC_READ.equals(a)) {
			boolean area = i instanceof ScrollOfRetribution || i instanceof ScrollOfTerror || i instanceof ScrollOfLullaby || i instanceof ScrollOfRage
					|| i instanceof ScrollOfPsionicBlast || i instanceof ScrollOfDread || i instanceof ScrollOfSirensSong || i instanceof ScrollOfChallenge;
			if (!area || !Assist.known(i)) return false;
			for (Shopkeeper s : sks) if (L.heroFOV[s.pos]) return true;
			return false;
		} else if (Potion.AC_DRINK.equals(a) && i instanceof PotionOfDragonsBreath) {
			for (Shopkeeper s : sks) if (d(s.pos, hp) <= 7) return true;   // (the breath is aimed after drinking: anywhere near)
			return false;
		}
		if (r < 0 || land < 0) return false;
		for (Shopkeeper s : sks) if (d(s.pos, land) <= r) return true;
		return false;
	}

	// =============================================================================================================
	// bombs kept for the climb
	// =============================================================================================================

	static final int BOMBKEEP = Assist.optInt("BOMBKEEP", 40);   // P(death) in percent (or health below it) that spends one on the way down

	/** Rules: a bomb thrown on the way down that isn't an emergency (strategy-v4 rule 7: on the climb they do 30-90 past
	 *  the multiplied armour; spend them going down only on a boss or a fight that could kill). */
	static boolean bombVeto(Cand c) {
		Target t = c.t;
		if (t == null || t.kind != Assist.USE || !(t.item instanceof Bomb) || !"LIGHTTHROW".equals(t.action)) return false;
		Hero h = Dungeon.hero;
		if (h.belongings.getItem(com.shatteredpixel.shatteredpixeldungeon.items.Amulet.class) != null) return false;
		if (AsItems.bossNear || AsItems.dying || AsItems.pDeath * 100 >= BOMBKEEP || h.HP * 100 < h.HT * BOMBKEEP) return false;
		return true;
	}

	static Wand staffWand(MagesStaff st) { Object w = Assist.field(st, "wand"); return w instanceof Wand ? (Wand) w : null; }

	/** Is cell s in the cone from a aimed at b (Fireblast's: about 60 degrees, this many cells)? */
	static boolean cone(int a, int b, int s, int len) {
		int w = Dungeon.level.width();
		double ax = b % w - a % w, ay = b / w - a / w, sx = s % w - a % w, sy = s / w - a / w;
		if (d(a, s) > len || (sx == 0 && sy == 0)) return false;
		double cos = (ax * sx + ay * sy) / Math.max(1e-9, Math.hypot(ax, ay) * Math.hypot(sx, sy));
		return cos > 0.80;   // (37 degrees either side, a little wider than the cone)
	}

	// =============================================================================================================
	// Lightning
	// =============================================================================================================

	/** The characters a Lightning bolt striking cell c would arc through, as the hero sees them (WandOfLightning.arc:
	 *  each struck one arcs 1 cell, 2 from water, +1 with the hero's Lightning Charge; the hero only when beside one). */
	static Set<Char> chain(int c) {
		Set<Char> out = new LinkedHashSet<>(); Level L = Dungeon.level; Hero h = Dungeon.hero;
		Char first = at(c); if (first == null) return out;
		boolean charged = h.buff(WandOfLightning.LightningCharge.class) != null;
		ArrayDeque<Char> q = new ArrayDeque<>(); out.add(first); q.add(first);
		while (!q.isEmpty()) {
			Char ch = q.poll(); int r = (L.water[ch.pos] ? 2 : 1) + (charged ? 1 : 0);
			for (Map.Entry<Integer, Integer> e : reach(ch.pos, r).entrySet()) {
				Char n = at(e.getKey()); if (n == null || out.contains(n)) continue;
				if (n == h && e.getValue() > 1) continue;
				out.add(n); q.add(n);
			}
		}
		return out;
	}
	/** The visible character on c (the hero included), or null. */
	static Char at(int c) {
		Hero h = Dungeon.hero; if (h.pos == c) return h;
		Level L = Dungeon.level;
		for (Mob m : L.mobs) if (m.pos == c && m.isAlive() && c >= 0 && c < L.length() && L.heroFOV[c] && m.invisible <= 0) return m;
		return null;
	}
	/** Cells within r steps of c through cells that aren't solid (PathFinder's 8-way steps), with their steps. */
	static Map<Integer, Integer> reach(int c, int r) {
		Level L = Dungeon.level; int w = L.width(), len = L.length();
		Map<Integer, Integer> d = new LinkedHashMap<>(); d.put(c, 0);
		ArrayDeque<Integer> q = new ArrayDeque<>(); q.add(c);
		while (!q.isEmpty()) {
			int a = q.poll(), da = d.get(a); if (da >= r) continue;
			for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
				if (dx == 0 && dy == 0) continue;
				int n = a + dx + dy * w; if (n < 0 || n >= len || Math.abs(n % w - a % w) > 1 || L.solid[n] || d.containsKey(n)) continue;
				d.put(n, da + 1); q.add(n);
			}
		}
		return d;
	}

	/** Rules: a Lightning zap whose arcs come back to the hero (in water with the target, or beside the chain). */
	static boolean lightningVeto(Cand c) {
		try {
			Target t = c.t;
			if (t == null || t.kind != Assist.USE || t.item == null || t.cell < 0 || !(Wand.AC_ZAP.equals(t.action) || MagesStaff.AC_ZAP.equals(t.action))) return false;
			Wand w = t.item instanceof Wand ? (Wand) t.item : t.item instanceof MagesStaff ? staffWand((MagesStaff) t.item) : null;
			if (!(w instanceof WandOfLightning)) return false;
			Hero h = Dungeon.hero;
			Ballistica b = new Ballistica(h.pos, t.cell, w.collisionProperties(t.cell));
			return b.collisionPos != null && chain(b.collisionPos).contains(h);
		} catch (Throwable e) { Assist.lastError = e; return false; }
	}

	// =============================================================================================================
	// trinkets
	// =============================================================================================================

	/** How good a trinket is for this bot (above 0: worth picking and upgrading; strategy-v4 section 6). Parchment
	 *  Scrap first when the energy for its +1 is there now (at +2/+3 random gear is enchanted and, at +3, never cursed;
	 *  it costs 45 to +3 and raises curses below +2), else the Salt Cube (halves hunger at +3: starving is a common
	 *  death), the Clover, the Mossy Clump. The Cracked Spyglass's items are drawn at 10% and count as unseen until
	 *  Thomas rules on them, so it gives the bot nothing yet. The 4th, random choice only when all three are avoided. */
	static int trinketWorth(Class<?> c) {
		if (c == ParchmentScrap.class) return Dungeon.energy >= 16 ? 11 : 6;   // (choosing at the pot: the catalyst's 6, then 10 for +1)
		if (c == SaltCube.class) return 10;
		if (c == ThirteenLeafClover.class) return 9;
		if (c == MossyClump.class) return 8;
		if (c == PetrifiedSeed.class) return 4;
		if (c == CrackedSpyglass.class) return 2;   // (its items unseen: policy pending)
		if (c == WondrousResin.class) return anyWand() ? 2 : 1;
		if (c == TrinketCatalyst.RandomTrinket.class) return 0;   // (above only the avoided ones)
		if (c == FerretTuft.class) return -1;   // (foes dodge more too: the bot fights many at once)
		if (c == ExoticCrystals.class) return -2;
		if (c == TrapMechanism.class || c == EyeOfNewt.class) return -3;
		if (c == VialOfBlood.class || c == RatSkull.class) return -4;   // (healing slowed: the drink-in-time rescues come too late; rare foes x5)
		if (c == ChaoticCenser.class || c == DimensionalSundial.class) return -5;   // (gas near the hero; the wall clock)
		if (c == ShardOfOblivion.class) return -6;   // (no identifying by use)
		if (c == MimicTooth.class) return -10;
		return 0;
	}
	static boolean anyWand() { Hero h = Dungeon.hero; if (h.belongings.getItem(MagesStaff.class) != null) return true; for (Item i : h.belongings.backpack) if (i instanceof Wand) return true; return false; }

	// =============================================================================================================
	// Magical Infusion
	// =============================================================================================================

	/** The piece the upgrades go on, if an upgrade there could strip its good enchantment or glyph (+4 and up, not
	 *  hardened): Magical Infusion (an upgrade scroll and 12 energy) keeps it. */
	static Item infusionTarget(int minLvl) {
		Hero h = Dungeon.hero; Item g = null;
		try { g = pebxel.bot.UpgradePlan.target(h); } catch (Throwable e) { }
		if (g == null) g = h.belongings.armor();
		if (g == null || !g.levelKnown || g.level() < minLvl) return null;
		if (g instanceof Weapon && ((Weapon) g).hasGoodEnchant() && !((Weapon) g).enchantHardened) return g;
		if (g instanceof Armor && ((Armor) g).hasGoodGlyph() && !((Armor) g).glyphHardened) return g;
		return null;
	}
	static boolean infusionWanted() { return infusionTarget(4) != null; }

	/** Rules: an upgrade read on a piece it could strip, with the pot on this floor and the energy for the Infusion. */
	static boolean upgradeVeto(Cand c) {
		try {
			Target t = c.t;
			if (t == null || t.kind != Assist.USE || !(t.item instanceof ScrollOfUpgrade) || !Scroll.AC_READ.equals(t.action) || !Assist.known(t.item)) return false;
			if (!AsAlchemy.ON || AsAlchemy.doneL == Dungeon.level || Dungeon.energy < 12 || !infusionWanted()) return false;
			if (t.on != null && t.on != infusionTarget(4)) return false;   // (read on another piece: no loss to fear)
			return potReachable();
		} catch (Throwable e) { Assist.lastError = e; return false; }
	}
	static boolean potReachable() {
		Level L = Dungeon.level; if (!Assist.setupOk(L)) return false;
		for (int c = 0; c < L.length(); c++) if (L.map[c] == Terrain.ALCHEMY && Assist.known[c] && Assist.walk(c) < Assist.INF) return true;
		return false;
	}

	// =============================================================================================================
	// what happened (per game: AssistBot's ECON line)
	// =============================================================================================================

	static int revives, lost, warns, fled, blesses, energized; static String trinket = "";
	static final Map<String, Integer> brewed = new TreeMap<>();
	static int lastBlessed = -1, lastAnkhs = -1; static Hero lastHero; static Level shopL; static final Set<Shopkeeper> shopSeen = new HashSet<>(), shopWarned = new HashSet<>();

	static void newGame() { revives = lost = warns = fled = blesses = energized = 0; trinket = ""; brewed.clear(); lastBlessed = lastAnkhs = -1; lastHero = null; shopL = null; shopSeen.clear(); shopWarned.clear(); }

	/** Each list: Ankhs used up (a revival), the shopkeepers' warnings and flights. */
	static void observe() {
		Hero h = Dungeon.hero; Level L = Dungeon.level; if (h == null || L == null) return;
		if (h != lastHero) { lastHero = h; lastBlessed = lastAnkhs = -1; }
		int b = 0, n = 0; for (Ankh a : h.belongings.getAllItems(Ankh.class)) { n++; if (a.isBlessed()) b++; }
		if (lastAnkhs >= 0 && h.isAlive()) {
			if (b < lastBlessed && n < lastAnkhs) revives++;
			else if (n < lastAnkhs) lost++;   // (an unblessed one: WndResurrect, the pack lost)
			if (b > lastBlessed && n == lastAnkhs) blesses++;
		}
		lastBlessed = b; lastAnkhs = n;
		if (shopL != L) { shopL = L; shopSeen.clear(); }
		Set<Shopkeeper> now = new HashSet<>();
		for (Mob m : L.mobs) if (m instanceof Shopkeeper && m.isAlive()) { now.add((Shopkeeper) m); if (L.heroFOV[m.pos]) shopSeen.add((Shopkeeper) m); if (warned((Shopkeeper) m) && shopWarned.add((Shopkeeper) m)) { warns++; log("shopkeeper warned on depth " + Dungeon.depth); } }
		for (Iterator<Shopkeeper> it = shopSeen.iterator(); it.hasNext(); ) { Shopkeeper s = it.next(); if (!now.contains(s)) { fled++; it.remove(); log("shopkeeper fled on depth " + Dungeon.depth); } }
	}
	static void brewed(String what) { brewed.merge(what, 1, Integer::sum); }
	static void log(String s) { if (Assist.DBG || "1".equals(System.getenv("ECONLOG"))) System.out.println("  ECON " + s); }

	static String stats() {
		Hero h = Dungeon.hero; String tr = "";
		if (h != null) for (Item i : h.belongings.backpack) if (i instanceof Trinket) tr = i.getClass().getSimpleName() + "+" + i.level();
		return "revives " + revives + " lost " + lost + " blesses " + blesses + " ankhs " + Math.max(0, lastAnkhs) + "/" + Math.max(0, lastBlessed) + "b shopWarn " + warns + " shopFled " + fled
				+ " trinket " + (tr.isEmpty() ? trinket.isEmpty() ? "-" : trinket : tr) + " energized " + energized + " brewed " + brewed;
	}
}
