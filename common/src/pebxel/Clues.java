package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.*;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.ExoticPotion;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfWealth;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.*;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ExoticScroll;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.quest.MassGraveRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretChestChasmRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretRunestoneRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.*;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.standard.StandardRoom;
import com.watabou.utils.Point;
import java.util.*;
import pebxel.Assist.Target;
import pebxel.bot.Identify;
import pebxel.bot.UpgradePlan;

/**
 * Clues to what an unknown potion or scroll is (agent ID, Thomas: "you can avoid guessing as much by combining clues
 * during your descent"), from what the player sees, put into pebxel.bot.Identify's odds as evidence on the labels:
 *
 * <ul>
 * <li><b>Drops.</b> An unknown item that turns up beside a foe just killed is its drop (Mob.rollToDropLoot drops at its
 *   cell). Swarm, Bat, Necromancer (and the Spectral one), Demon Spawner: always Healing (their loot field);
 *   Acidic: Experience; Scorpio: never Healing or Strength (Scorpio.createLoot); Warlock: Healing a third of the time,
 *   less after (Warlock.createLoot), else never Healing; Succubus: never Identify or Upgrade; fire elemental: Liquid
 *   Flame, frost elemental: Frost, shock elemental: Recharging, chaos elemental: Transmutation. A Ring of Wealth worn
 *   (or an unknown ring) can add a drop of its own there: the clue then counts four in five.</li>
 * <li><b>Puzzle rooms.</b> Each puts its potion on its own floor (level.addItemToSpawn): RegularLevel.createItems lays
 *   it as a plain heap in a standard room (randomDropCell), never in a chest, never in the puzzle room. Pool room:
 *   Invisibility; traps room (traps or a chasm): Levitation; magical fire: Frost; toxic gas: Purity; storage
 *   (barricade): Liquid Flame; sentry room: Haste; the secret chasm chest: Levitation; the secret runestone room
 *   and the Wandmaker's mass grave: Liquid Flame. So, a room like that seen, one of the potion colours lying about
 *   on that floor is that kind (unless another special room took it for its own prize: Level.findPrizeItem). The
 *   longer the floor is explored without it, the less likely it's a colour not seen there.</li>
 * <li><b>Shops and libraries.</b> A shop always sells a Healing, an Identify, a Remove Curse and a Magic Mapping
 *   (ShopRoom.generateItems); a library's first book is Identify or Remove Curse (LibraryRoom.paint).</li>
 * <li><b>Shop prices tell nothing</b>: an unknown potion or scroll is worth 30 whatever it is (Potion.value,
 *   Scroll.value: the kind's own price only once known).</li>
 * <li>A Stone of Intuition's wrong guess rules that kind out; an unknown potion thrown that splashes and stays
 *   unknown is none of the four that identify on shattering. Identify scrolls, Divination, Detect Magic and the
 *   rest make a label known (Potion.getUnknown): it drops out of the odds, the others balance up.</li>
 * </ul>
 *
 * And the testing policies the hands use: drink-test at a floor's end, at a puzzle room's door when one waits;
 * never a likely Healing; read-test only when the read is of use whatever it is. AS_CLUES=0: off.
 */
public final class Clues {
	private Clues() {}

	public static final boolean ON = Assist.opt("CLUES", true);
	/** An unknown potion this likely Healing is used as Healing (and never drink-tested). */
	static final double SURE = Assist.optInt("CLUESURE", 90) / 100.0;
	/** ... this likely is kept for an emergency rather than drink-tested. */
	static final double KEEPH = Assist.optInt("CLUEKEEP", 50) / 100.0;
	/** A potion believed the puzzle's kind this likely is drunk for its prize. */
	static final double PUZ = Assist.optInt("CLUEPUZ", 45) / 100.0;

	static Hero H; static Level L;

	// ---- per floor ----
	static Level fl;
	static final Set<Item> seen = Collections.newSetFromMap(new IdentityHashMap<>());
	static final Map<Mob, Integer> lastMobs = new IdentityHashMap<>();
	static final List<int[]> deaths = new ArrayList<>();   // {cell, time}
	static final List<Class<?>> deathCls = new ArrayList<>();
	static final Set<Room> roomsSeen = Collections.newSetFromMap(new IdentityHashMap<>());
	/** The floor's rooms whose potion it guarantees: room -> its kind and evidence. */
	static final Map<Room, Identify.Among> puzzles = new IdentityHashMap<>();
	static final List<Identify.Among> floorAmong = new ArrayList<>();
	// ---- per game ----
	static final Map<Integer, Set<Class<?>>> floorPots = new HashMap<>();
	static Class<?> guessL, guessK, thrownL; static float guessT, thrownT;
	public static int clues;   // (how many clues seen, for the stats)

	static int fkey() { return Dungeon.depth * 10 + Dungeon.branch; }

	/** Each plan, after Assist's look round (known cells, mobs). */
	static void observe() {
		H = Dungeon.hero; L = Dungeon.level; if (!ON || H == null || L == null) return;
		if (Identify.fresh(H)) { floorPots.clear(); fl = null; guessL = thrownL = null; clues = 0; }
		if (fl != L) { fl = L; seen.clear(); lastMobs.clear(); deaths.clear(); deathCls.clear(); roomsSeen.clear(); puzzles.clear(); floorAmong.clear();
			for (Item i : H.belongings) seen.add(i); }
		float now = Actor.now();
		if (guessL != null && now > guessT) { if (Potion.getUnknown().contains(guessL) || Scroll.getUnknown().contains(guessL)) { Identify.exclude(guessL, guessK, null); clues++; } guessL = null; }
		if (thrownL != null && now > thrownT) {
			if (Potion.getUnknown().contains(thrownL)) { for (Class<?> k : Arrays.asList(PotionOfLiquidFlame.class, PotionOfToxicGas.class, PotionOfParalyticGas.class, PotionOfFrost.class)) Identify.exclude(thrownL, k, "it splashed harmlessly"); clues++; }
			thrownL = null;
		}
		// foes killed in sight: where they fell
		for (Map.Entry<Mob, Integer> e : lastMobs.entrySet()) { Mob m = e.getKey();
			if (!m.isAlive() || !L.mobs.contains(m)) { deaths.add(new int[]{ e.getValue(), (int) now }); deathCls.add(m.getClass()); } }
		for (int k = deaths.size() - 1; k >= 0; k--) if (now - deaths.get(k)[1] > 3) { deaths.remove(k); deathCls.remove(k); }
		lastMobs.clear();
		for (Mob m : L.mobs) if (m.isAlive() && m.pos >= 0 && m.pos < L.length() && L.heroFOV[m.pos] && m.invisible <= 0) lastMobs.put(m, m.pos);
		// the rooms seen: puzzles, the shop, the library
		if (L instanceof RegularLevel) for (Room r : ((RegularLevel) L).rooms()) if (!roomsSeen.contains(r) && seenInside(r) >= 3) { roomsSeen.add(r); room(r); }
		// items seen for the first time
		for (Heap h : L.heaps.valueList()) {
			if (!h.seen || h.pos < 0 || h.pos >= L.length() || !L.heroFOV[h.pos]) continue;
			if (h.type != Heap.Type.HEAP && h.type != Heap.Type.FOR_SALE) continue;
			Item i = h.peek(); if (i == null || seen.contains(i)) continue;
			seen.add(i);
			if (!(i instanceof Potion || i instanceof Scroll) || Assist.known(i) || i instanceof ExoticPotion || i instanceof ExoticScroll) continue;
			item(i.getClass(), h);
		}
	}

	static int seenInside(Room r) {
		int n = 0, W = L.width();
		for (int y = r.top + 1; y < r.bottom; y++) for (int x = r.left + 1; x < r.right; x++) { int c = x + y * W; if (c >= 0 && c < L.length() && (L.visited[c] || L.mapped[c]) && ++n >= 3) return n; }
		return n;
	}
	static Room roomAt(int c) { return L instanceof RegularLevel ? ((RegularLevel) L).room(c) : null; }

	static void room(Room r) {
		Class<?> k = null; String what = null;
		if (r instanceof PoolRoom) { k = PotionOfInvisibility.class; what = "the pool room"; }
		else if (r instanceof TrapsRoom) { k = PotionOfLevitation.class; what = "the traps room"; }
		else if (r instanceof MagicalFireRoom) { k = PotionOfFrost.class; what = "the magical fire room"; }
		else if (r instanceof ToxicGasRoom) { k = PotionOfPurity.class; what = "the toxic gas room"; }
		else if (r instanceof StorageRoom) { k = PotionOfLiquidFlame.class; what = "the storage room"; }
		else if (r instanceof SentryRoom) { k = PotionOfHaste.class; what = "the sentry room"; }
		else if (r instanceof SecretChestChasmRoom) { k = PotionOfLevitation.class; what = "the chasm chest room"; }
		else if (r instanceof SecretRunestoneRoom) { k = PotionOfLiquidFlame.class; what = "the runestone room"; }
		else if (r instanceof MassGraveRoom) { k = PotionOfLiquidFlame.class; what = "the mass grave"; }
		if (k != null) {
			Identify.Among a = Identify.among(k, floorPots.computeIfAbsent(fkey(), x -> new HashSet<>()), what + " on this floor");
			a.f = 0.3; puzzles.put(r, a); floorAmong.add(a); clues++;
			return;
		}
		if (r.getClass() == ShopRoom.class) {
			Set<Class<?>> pots = new HashSet<>(), scr = new HashSet<>();
			Identify.Among a = Identify.among(PotionOfHealing.class, pots, "the shop"); a.f = 0.97;
			for (Class<?> s : Arrays.asList(ScrollOfIdentify.class, ScrollOfRemoveCurse.class, ScrollOfMagicMapping.class)) Identify.among(s, scr, "the shop").f = 0.97;
			shopPots = pots; shopScr = scr; shopRoom = r; clues++;
			return;
		}
		if (r instanceof LibraryRoom) {
			Set<Class<?>> s = new HashSet<>();
			Identify.Among a = Identify.among(ScrollOfIdentify.class, s, "the library"); a.f = 0.97; a.w = 0.5;
			a = Identify.among(ScrollOfRemoveCurse.class, s, "the library"); a.f = 0.97; a.w = 0.5;
			libScr = s; libRoom = r; clues++;
		}
	}
	static Set<Class<?>> shopPots, shopScr, libScr; static Room shopRoom, libRoom;

	/** An unknown potion or scroll seen lying for the first time (its label shows). */
	static void item(Class<?> label, Heap h) {
		boolean pot = Potion.class.isAssignableFrom(label);
		// a foe's drop: one just killed on or beside the cell
		for (int k = 0; k < deaths.size(); k++) if (L.distance(deaths.get(k)[0], h.pos) <= 1 && h.type == Heap.Type.HEAP) {
			double[] like = drop(deathCls.get(k), pot);
			if (like != null) {
				if (wealth()) for (int i = 0; i < 12; i++) like[i] = 0.8 * like[i] + 0.2;   // (the ring's own drop: the plain rates)
				Identify.like(label, like, "a " + name(deathCls.get(k)) + " dropped it"); clues++;
			}
			return;
		}
		Room r = roomAt(h.pos);
		if (h.type == Heap.Type.FOR_SALE) {
			if (r == shopRoom && r != null) { if (pot && shopPots != null) shopPots.add(label); else if (!pot && shopScr != null) shopScr.add(label); Identify.changed(); }
			return;
		}
		if (r != null && r == libRoom && !pot && libScr != null) { libScr.add(label); Identify.changed(); return; }
		if (pot && (r == null || r instanceof StandardRoom)) { floorPots.computeIfAbsent(fkey(), x -> new HashSet<>()).add(label); Identify.changed(); }
	}
	static boolean wealth() {
		for (Item i : new Item[]{ H.belongings.ring, H.belongings.misc }) if (i instanceof Ring && (i instanceof RingOfWealth ? true : !((Ring) i).isKnown())) return true;   // (an unknown ring might be Wealth: only the kind's name, never the class, is looked at once known)
		return false;
	}
	static String name(Class<?> c) {
		if (c == Swarm.class) return "fly swarm"; if (c == DemonSpawner.class) return "demon spawner";
		String n = c.getSimpleName(); return n.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
	}
	/** The likelihood of each kind (by Identify's order) for a drop of this foe, or null: no clue. */
	static double[] drop(Class<?> m, boolean pot) {
		double[] r = new double[12]; Class<?>[] K = Identify.kinds(pot);
		if (pot) {
			if (Bat.class.isAssignableFrom(m) || Swarm.class.isAssignableFrom(m) || Necromancer.class.isAssignableFrom(m) || DemonSpawner.class.isAssignableFrom(m)) return one(K, PotionOfHealing.class);
			if (Acidic.class.isAssignableFrom(m)) return one(K, PotionOfExperience.class);
			// (the factors stand in for the plain drop rate the label's count already carries: a scorpio's is the plain
			// rates without two kinds, so 0 or 1; a warlock's a quarter Healing, the rest the plain rates without it)
			if (Scorpio.class.isAssignableFrom(m)) { for (int i = 0; i < 12; i++) r[i] = K[i] == PotionOfHealing.class || K[i] == PotionOfStrength.class ? 0 : 1; return r; }
			if (Warlock.class.isAssignableFrom(m)) { double t = 0; for (int i = 0; i < 12; i++) if (K[i] != PotionOfHealing.class) t += Identify.rate(true, K[i]); for (int i = 0; i < 12; i++) r[i] = K[i] == PotionOfHealing.class ? 0.25 / Identify.rate(true, K[i]) : 0.75 / t; return r; }
			if (Elemental.FrostElemental.class.isAssignableFrom(m)) return one(K, PotionOfFrost.class);
			if (Elemental.FireElemental.class.isAssignableFrom(m)) return one(K, PotionOfLiquidFlame.class);
		} else {
			if (Succubus.class.isAssignableFrom(m)) { for (int i = 0; i < 12; i++) r[i] = K[i] == ScrollOfIdentify.class || K[i] == ScrollOfUpgrade.class ? 0 : 1; return r; }
			if (Elemental.ShockElemental.class.isAssignableFrom(m)) return one(K, ScrollOfRecharging.class);
			if (Elemental.ChaosElemental.class.isAssignableFrom(m)) return one(K, ScrollOfTransmutation.class);
		}
		return null;
	}
	static double[] one(Class<?>[] K, Class<?> k) { double[] r = new double[12]; for (int i = 0; i < 12; i++) r[i] = K[i] == k ? 1 : 0; return r; }

	/** The bot is about to guess with a Stone of Intuition (AsItems.choose): wrong, it rules the kind out. */
	static void guess(Class<?> label, Class<?> kind) { guessL = label; guessK = kind; guessT = Actor.now(); }
	/** An unknown potion thrown: still unknown after, it splashed (none of the four that show themselves). */
	static void thrown(Class<?> label) { thrownL = label; thrownT = Actor.now(); }

	// =============================================================================================================
	// beliefs, for the rest of the Assistant
	// =============================================================================================================

	public static double p(Item i, Class<?> kind) { return i == null ? 0 : Assist.known(i) ? (kind.isInstance(i) ? 1 : 0) : Identify.p(i.getClass(), kind); }

	/** A Potion of Healing to drink: a known one, else an unknown one believed Healing (SURE): a fly's drop. */
	static Item healing() {
		Hero h = Dungeon.hero; if (h == null) return null;
		PotionOfHealing k = h.belongings.getItem(PotionOfHealing.class);
		if (k != null && k.isKnown()) return k;
		if (!ON) return null;
		Item best = null; double bp = SURE;
		for (Item i : h.belongings.backpack) if (i instanceof Potion && !Assist.known(i) && !(i instanceof ExoticPotion)) { double q = Identify.pHeal(i); if (q >= bp) { bp = q; best = i; } }
		return best;
	}

	/** "likely Healing 92% (a bat dropped it)": an unknown item's likeliest kind, in words. */
	static String about(Item i) {
		if (i == null || Assist.known(i)) return "";
		Class<?> k = Identify.likeliest(i.getClass()); if (k == null) return "";
		double q = Identify.p(i.getClass(), k);
		String c = Identify.clue(i.getClass());
		return (q >= 0.9 ? "surely " : q >= 0.5 ? "likely " : "maybe ") + kindName(k) + " " + Math.round(q * 100) + "%" + (c != null ? " (" + c + ")" : "");
	}
	static String kindName(Class<?> k) { String n = k.getSimpleName().replace("PotionOf", "").replace("ScrollOf", ""); return n.replaceAll("([a-z])([A-Z])", "$1 $2"); }

	/** The unknown potion likeliest this kind (at least min, and never one likelier Healing), or null. */
	static Item believed(Class<?> kind, double min) {
		if (!ON) return null;
		Hero h = Dungeon.hero; Item best = null; double bp = min;
		for (Item i : h.belongings.backpack) if (i instanceof Potion && !Assist.known(i) && !(i instanceof ExoticPotion)) {
			double q = Identify.p(i.getClass(), kind); if (q >= bp && q > Identify.pHeal(i)) { bp = q; best = i; } }
		return best;
	}

	// =============================================================================================================
	// testing
	// =============================================================================================================

	/** A puzzle room of this floor still holding its prize (seen), whose potion isn't known: the kind it needs. */
	static final class Puzzle { Room room; Class<?> kind; int stand = -1; String what; }
	static List<Puzzle> open() {
		List<Puzzle> out = new ArrayList<>(); if (fl != L) return out;
		for (Map.Entry<Room, Identify.Among> e : puzzles.entrySet()) {
			Room r = e.getKey(); Class<?> k = e.getValue().kind;
			if (!Potion.getUnknown().contains(k)) continue;
			boolean prize = false; int W = L.width();
			if (r instanceof StorageRoom) for (Room.Door d : r.connected.values()) { int c = d.x + d.y * W; if (L.map[c] == Terrain.BARRICADE) prize = true; }
			else if (r instanceof MagicalFireRoom) { com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob b = L.blobs.get(MagicalFireRoom.EternalFire.class); prize = b != null && b.volume > 0; }
			else for (Heap h : L.heaps.valueList()) if (h.seen && !h.isEmpty() && r.inside(L.cellToPoint(h.pos)) && h.type != Heap.Type.FOR_SALE) prize = true;
			if (!prize) continue;
			Puzzle p = new Puzzle(); p.room = r; p.kind = k; p.what = e.getValue().what.replace(" on this floor", "");
			int bd = Assist.INF;   // where to stand: just outside a door, reachable
			for (Room.Door d : r.connected.values()) { int dc = d.x + d.y * W;
				for (int n : new int[]{ dc - W, dc + W, dc - 1, dc + 1 }) { if (n < 0 || n >= L.length()) continue; Point q = L.cellToPoint(n);
					if (q.x >= r.left && q.x <= r.right && q.y >= r.top && q.y <= r.bottom) continue;
					int w = Assist.walk(n); if (w < bd && !Assist.danger[n] && L.passable[n] && !L.water[n]) { bd = w; p.stand = n; } } }
			out.add(p);
		}
		return out;
	}
	static boolean drinkKind(Class<?> k) { return k == PotionOfInvisibility.class || k == PotionOfLevitation.class || k == PotionOfPurity.class || k == PotionOfHaste.class; }
	static Class<? extends Buff> buffFor(Class<?> k) { return k == PotionOfInvisibility.class ? Invisibility.class : k == PotionOfLevitation.class ? Levitation.class : k == PotionOfPurity.class ? BlobImmunity.class : k == PotionOfHaste.class ? Haste.class : null; }

	/** How sure, by now, the floor's puzzle potion has been seen: the floor explored, or not yet. */
	static void explored(boolean left) {
		double f = left ? 0.45 : 0.95;
		for (Identify.Among a : floorAmong) if (a.f != f && fl == L) { a.f = Math.max(a.f, f); Identify.changed(); }
	}

	/** The expected worth (rough units: a turn's fight is ~1) of drinking this unknown potion now, to learn it. */
	static double drinkWorth(Item i, boolean wet, List<Puzzle> pz) {
		Class<?> lab = i.getClass(); double v = 0;
		Map<Class<?>, double[]> od = Identify.odds(true); double[] r = od.get(lab); if (r == null) return -9;
		double hf = Dungeon.hero.HP / (double) Math.max(1, Dungeon.hero.HT);
		for (int k = 0; k < 12; k++) { if (r[k] <= 0) continue; Class<?> c = Identify.POT[k]; double x;
			if (c == PotionOfHealing.class) x = -4 * hf;            // (wasted at full health: and the emergency kit)
			else if (c == PotionOfStrength.class) x = 3;
			else if (c == PotionOfExperience.class) x = 2;
			else if (c == PotionOfLiquidFlame.class) x = wet ? -0.5 : -2.5;   // (burning; scrolls burn: in water it goes out)
			else if (c == PotionOfToxicGas.class) x = -1.5;
			else if (c == PotionOfParalyticGas.class) x = -1;
			else if (c == PotionOfFrost.class) x = -1.5;            // (frozen: a potion in the pack may shatter)
			else x = 0;
			for (Puzzle p : pz) if (p.kind == c) x -= drinkKind(c) ? 1.5 : 2;   // (the floor's prize potion: better drunk at its door, or thrown at it)
			v += r[k] * x;
		}
		return v + 1.5 + 0.5 * Math.min(2, i.quantity() - 1);   // (knowing it: each copy after)
	}

	/** The hands' drink-test: at the floor's end, safe and well; at a puzzle room's door when one waits. */
	static Target testPotion(boolean threatNear, boolean left) {
		Hero h = Dungeon.hero;
		explored(left);
		if (left || threatNear || Assist.starving() || h.HP * 10 < h.HT * 7) return null;
		List<Item> cand = new ArrayList<>();
		for (Item i : h.belongings.backpack) if (i instanceof Potion && !Assist.known(i) && !(i instanceof ExoticPotion) && !Assist.declined(i.getClass(), Potion.AC_DRINK) && Identify.pHeal(i) < KEEPH) cand.add(i);
		if (cand.isEmpty()) return null;
		List<Puzzle> pz = open();
		for (Puzzle p : pz) {   // a puzzle waiting: test where the right one is of use at once
			if (!drinkKind(p.kind) || p.stand < 0) continue;
			Class<? extends Buff> bf = buffFor(p.kind); if (bf != null && h.buff(bf) != null) return null;   // (it was: AsItems crosses)
			Item best = null; double bp = 0.04;
			for (Item i : cand) { double q = Identify.p(i.getClass(), p.kind); if (q > bp) { bp = q; best = i; } }
			if (best == null) continue;
			if (h.pos != p.stand) { if (Assist.walk(p.stand) >= Assist.INF) continue; Target t = new Target(p.stand, Assist.GO, "to " + p.what + "'s door: test potions there (one is its " + kindName(p.kind) + ")"); t.obj = p.stand; return t; }
			return Assist.use(best, Potion.AC_DRINK, -1, "drink to learn, at " + p.what + ": " + about(best) + "; " + kindName(p.kind) + " " + Math.round(bp * 100) + "%");
		}
		// the plain test: in or beside water (Liquid Flame's fire goes out there: Burning.act), nothing that burns about
		boolean burn = false; int hp = h.pos;
		for (int k = -1; k < 8; k++) { int n = k < 0 ? hp : hp + Assist.N8[k]; if (k < 0 || Assist.nb(hp, n)) burn |= L.flamable[n]; }
		boolean wet = wetAt(hp), lfKnown = new PotionOfLiquidFlame().isKnown();
		double lf = 0; for (Item i : cand) lf = Math.max(lf, Identify.p(i.getClass(), PotionOfLiquidFlame.class));
		if (!lfKnown && lf > 0.02 && (!wet || burn)) {   // (to water first: the nearest wet cell with nothing that burns by it)
			int wc = -1, wd = 30;
			for (int c = 0; c < L.length(); c++) if (Assist.walk(c) < wd && wetAt(c) && !Assist.danger[c] && L.passable[c] && Identify.safeCell(L, c) && Actor.findChar(c) == null) { wd = Assist.walk(c); wc = c; }
			if (wc >= 0) { Target t = new Target(wc, Assist.GO, "to water: test potions there (Liquid Flame goes out in water)"); t.obj = wc; return t; }
			if (burn || h.HP * 10 < h.HT * 9) return null;
		}
		Item best = null; double bv = 0; boolean scr = false; for (Item i : h.belongings.backpack) scr |= i instanceof Scroll;
		for (Item i : cand) { double v = drinkWorth(i, wet, pz) - (!wet && scr && !lfKnown ? 2 * Identify.p(i.getClass(), PotionOfLiquidFlame.class) : 0); if (v > bv) { bv = v; best = i; } }
		if (best == null) return null;
		return Assist.use(best, Potion.AC_DRINK, -1, "drink to learn: " + about(best));
	}
	static boolean wetAt(int c) {
		if (L.water[c]) return true;
		for (int k = 0; k < 8; k++) { int n = c + Assist.N8[k]; if (Assist.nb(c, n) && L.water[n]) return true; }
		return false;
	}

	/** The worth of reading this unknown scroll now, whatever it turns out to be (strategy-reference 2.2: decide the
	 *  upgrade, identify, remove-curse and transmute targets first; not mid-fight: Rage and Teleportation). */
	static double readWorth(Item s, boolean early, StringBuilder why) {
		Hero h = Dungeon.hero; Map<Class<?>, double[]> od = Identify.odds(false); double[] r = od.get(s.getClass()); if (r == null) return -9;
		boolean cursedWorn = false, unknownWorn = false, unid = false, wands = false, junk = false;
		for (Item i : h.belongings) {
			boolean worn = i instanceof EquipableItem && ((EquipableItem) i).isEquipped(h);
			if (worn && i.visiblyCursed()) cursedWorn = true;
			if (worn && !i.cursedKnown) unknownWorn = true;
			if (!i.isIdentified() && (i instanceof EquipableItem || i instanceof Wand)) unid = true;
			if (i instanceof Potion && !Assist.known(i)) unid = true;
			if (i instanceof Wand) wands = true;
			if (!worn && (i instanceof Weapon || i instanceof Armor) && !i.isIdentified()) junk = true;
		}
		Item up = UpgradePlan.target(h); if (up == null) { Item a = h.belongings.armor(); if (a != null && !(a.cursed && a.cursedKnown)) up = a; }
		double v = 0;
		for (int k = 0; k < 12; k++) { if (r[k] <= 0) continue; Class<?> c = Identify.SCR[k]; double x;
			if (c == ScrollOfUpgrade.class) x = up != null ? 4 : -4;
			else if (c == ScrollOfIdentify.class) x = unid ? 1 : -0.5;
			else if (c == ScrollOfRemoveCurse.class) x = cursedWorn ? 3 : unknownWorn ? 0.3 : -0.5;
			else if (c == ScrollOfMagicMapping.class) x = early ? 1.5 : 0;
			else if (c == ScrollOfTransmutation.class) x = junk ? 0.3 : -0.8;
			else if (c == ScrollOfRecharging.class) x = wands ? 0.3 : -0.3;
			else if (c == ScrollOfRage.class) x = early ? -1 : -0.3;   // (the floor's foes come)
			else if (c == ScrollOfTeleportation.class) x = -0.3;
			else x = -0.2;   // (Lullaby, Terror, Mirror Image, Retribution: wasted with nothing about)
			v += r[k] * x;
		}
		if (why != null) why.append(up != null ? "an upgrade target ready" : "no upgrade target").append(cursedWorn ? ", a cursed item worn" : "").append(unid ? ", unknown things" : "").append(early ? ", early on the floor" : "");
		return v + 0.8 + 0.4 * Math.min(2, s.quantity() - 1);
	}

	/** The hands' read-test: only a read that is of use whatever the scroll is, in calm. */
	static Target testScroll(boolean threatNear, boolean early) {
		Hero h = Dungeon.hero;
		if (threatNear || Assist.starving() || h.HP * 10 < h.HT * 6) return null;
		Item best = null; double bv = Assist.optInt("CLUEREAD", 60) / 100.0; String bw = "";
		for (Item i : h.belongings.backpack) if (i instanceof Scroll && !Assist.known(i) && !(i instanceof ExoticScroll) && !Assist.declined(i.getClass(), Scroll.AC_READ)) {
			StringBuilder sb = new StringBuilder(); double v = readWorth(i, early, sb); if (v > bv) { bv = v; best = i; bw = sb.toString(); } }
		if (best == null) return null;
		return Assist.use(best, Scroll.AC_READ, -1, "read to learn: " + about(best) + "; " + bw);
	}
}
