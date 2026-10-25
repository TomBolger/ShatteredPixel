package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem;
import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClassArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.Trinket;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.watabou.utils.Reflection;
import java.util.*;

/**
 * The training and test set-ups (server only: the decision code in common/ never reads any of this). Each is set from
 * the environment and they combine; results made with any of them never count as the honest bot getting better.
 * <ul>
 * <li><b>God mode</b>, CHEAT=1 ({@link #ON}): a smoke test of the whole game, not a way to play it. The hero can't
 *   really be hurt (thousands of HP, topped up between turns), is strong and a few levels ahead of the floor, knows
 *   every item it carries (in games with an even seed), never starves and has gold to spend; the server's helpers
 *   (BotQuests, BotItems, BotAlchemy, Rescue) drive the quests and items. STARTDEPTH=n then walks straight down the
 *   stairs to n (stopping at bosses).</li>
 * <li><b>Head start</b>, HEAD=1 with STARTDEPTH=n (no god mode): the hero is taken floor by floor to depth n at once
 *   (each floor made as in a played game, so the drop counts stay right; bosses are passed by), then given what a
 *   player there might have: level STARTLVL (default n+1), strength STARTSTR (default 10 + 0.4 a floor: 2 potions a
 *   region), a weapon and armour of tier STARTTIER (default the best the strength carries at +STARTUPG) at
 *   +STARTUPG each (default 0.3 a floor: half of 3 scrolls a region; the Mage's staff and the Huntress's bow are kept,
 *   the staff upgraded), STARTHEAL known Potions of Healing (default 2) and STARTFOOD rations (default 1). Gear is
 *   identified.</li>
 * <li><b>Spawn kits</b>, KIT=kind,kind... (artifacts, wands, rings, trinkets, armor, weapons; "random": one item of a
 *   random kind of the first four; "all": one of each): given at the start (after any head start). Each kind takes its
 *   pick from ARTIFACTS / WANDS / RINGS / TRINKETS / ARMOR / WEAPONS: class names (HornOfPlenty,CloakOfShadows; a
 *   name's end will do: Lightning for WandOfLightning) or random:N (N of those the dungeon could drop, none twice);
 *   default random:1. A kind's setting alone also gives it. KITID=1 (default) identified, 0 not, mix (each a coin
 *   toss); KITLVL=n upgrades each (default 0); KITEQUIP=1 wears what fits a free slot (default 0: in the bag, for the
 *   bot to decide). Artifacts given are taken out of the dungeon's deck.</li>
 * <li><b>Mild cheat</b>, MILD=1 (no god mode): what "beat the game once in a while with mild cheating" means here:
 *   MILDHP (default 50) percent more base max HP; MILDSOU (default 2) extra known Scrolls of Upgrade on reaching each
 *   region (depths 1, 6, 11, 16, 21); a free known Potion of Healing on every MILDHEAL-th new floor (default 3; 0 none).
 *   Everything else is the game as shipped and the bot plays honestly.</li>
 * </ul>
 * Random picks are seeded by SIMSEED and the dungeon seed: a game replays the same. Each set-up prints a "SETUP" line.
 */
public final class Cheat {
	private Cheat() { }

	public static final boolean ON = "1".equals(System.getenv("CHEAT"));
	static int levelsGiven, healed, upgrades;

	static String env(String k, String d) { String v = System.getenv(k); return v == null || v.isEmpty() ? d : v; }
	static int envInt(String k, int d) { try { return Integer.parseInt(env(k, "" + d)); } catch (NumberFormatException e) { return d; } }

	public static final boolean HEAD = !ON && "1".equals(System.getenv("HEAD"));
	public static final boolean MILD = !ON && "1".equals(System.getenv("MILD"));
	static final String KIT = env("KIT", "");
	static final String[] KINDS = { "ARTIFACTS", "WANDS", "RINGS", "TRINKETS", "ARMOR", "WEAPONS" };
	/** Any set-up at all besides god mode (AssistBot calls {@link #setup} each turn then). */
	public static final boolean SETUP = HEAD || MILD || !KIT.isEmpty() || Arrays.stream(KINDS).anyMatch(k -> System.getenv(k) != null);

	static Random r = new Random(1);
	static boolean headDone, kitDone; static int mildDeepest, mildLvl;

	public static void newGame(long seed) {
		r = new Random(Long.parseLong(env("SIMSEED", "0")) * 1_000_003L + seed * 17 + 3);
		headDone = kitDone = false; mildDeepest = 0; mildLvl = -1;
	}

	/** Between turns (the actor thread waiting). */
	public static void apply() {
		Hero h = Dungeon.hero;
		if (!ON || h == null || !h.isAlive()) return;
		if (h.HTBoost < 5000) { h.HTBoost = 5000; h.updateHT(true); }
		if (h.HP < h.HT) { h.HP = h.HT; healed++; }   // (always full: resting to full on thousands of HP took for ever)
		if (h.STR < 22) h.STR = 22;
		int want = Math.min(30, Dungeon.depth + 4);
		for (int k = 0; k < 30 && h.lvl < want; k++) { h.earnExp(h.maxExp() - h.exp, Cheat.class); levelsGiven++; }
		// (every other game: so Identify, Detect Magic and Intuition get used in the rest)
		if (Dungeon.seed % 2 == 0) for (Item i : h.belongings) if (!i.isIdentified()) i.identify(false);
		Hunger hu = h.buff(Hunger.class);
		if (hu != null && hu.hunger() >= Hunger.STARVING - 5) hu.satisfy(Hunger.STARVING);
		if (Dungeon.gold < 5000) Dungeon.gold = 5000;
		// gear that keeps up (as Scrolls of Upgrade would): the weapon and armour worn at +depth/3 at least
		Item wpn = h.belongings.weapon(), arm = h.belongings.armor();
		for (Item i : new Item[]{wpn, arm}) if (i != null && i.isUpgradable() && i.level() < Dungeon.depth / 3) { i.upgrade(); upgrades++; }
	}

	/**
	 * The head start, kits and mild cheat, between turns (the actor thread waiting, the hero ready). True when it
	 * started a floor change (the caller waits for the next floor).
	 */
	public static boolean setup() {
		Hero h = Dungeon.hero;
		if (!SETUP || h == null || !h.isAlive()) return false;
		int start = envInt("STARTDEPTH", 0);
		if (HEAD && !headDone && Dungeon.depth < start && Dungeon.branch == 0) {   // down a floor (each made as if walked)
			LevelTransition tr = new LevelTransition(Dungeon.level, h.pos, LevelTransition.Type.REGULAR_EXIT, Dungeon.depth + 1, 0, LevelTransition.Type.REGULAR_ENTRANCE);
			Dungeon.level.locked = false;
			return Dungeon.level.activateTransition(h, tr);
		}
		if (HEAD && !headDone) { headDone = true; headStart(h); }
		if (!kitDone) { kitDone = true; kit(h); }
		if (MILD) mild(h);
		return false;
	}

	static void give(Item i, boolean known) {
		if (known) i.identify(false);
		if (!i.collect()) Dungeon.level.drop(i, Dungeon.hero.pos);
	}

	static void headStart(Hero h) {
		int d = Dungeon.depth;
		int lvl = Math.min(30, envInt("STARTLVL", d + 1)), str = envInt("STARTSTR", 10 + Math.round((d - 1) * 0.4f)), upg = envInt("STARTUPG", Math.round((d - 1) * 0.3f));
		for (int k = 0; k < 30 && h.lvl < lvl; k++) h.earnExp(h.maxExp() - h.exp, Cheat.class);
		h.STR = Math.max(h.STR, str);
		// the weapon (the staff and the bow stay: they are the class's own)
		int tier = envInt("STARTTIER", 0);
		StringBuilder got = new StringBuilder();
		if (h.belongings.weapon() instanceof MagesStaff) { for (int k = 0; k < upg; k++) h.belongings.weapon().upgrade(); got.append("staff+").append(upg); }
		else {
			MeleeWeapon w = null;
			for (int t = tier > 0 ? tier : 5; t >= 1 && w == null; t--) {
				MeleeWeapon c = (MeleeWeapon) Reflection.newInstance(pick(Generator.wepTiers[t - 1], null));
				if (c == null) continue;
				for (int k = 0; k < upg; k++) c.upgrade();
				if (tier > 0 || c.STRReq() <= h.STR) w = c;
			}
			if (w != null) {
				w.identify(false);
				h.belongings.weapon = w; w.activate(h);   // (the old one gone, as if sold)
				got.append(w.getClass().getSimpleName()).append('+').append(upg);
			}
		}
		// the armour (the Warrior's seal moved to it)
		Armor a = null;
		for (int t = tier > 0 ? tier : 5; t >= 1 && a == null; t--) {
			Armor c = (Armor) Reflection.newInstance(Generator.Category.ARMOR.classes[t - 1]);
			for (int k = 0; k < upg; k++) c.upgrade();
			if (tier > 0 || c.STRReq() <= h.STR) a = c;
		}
		if (a != null && !(h.belongings.armor instanceof ClassArmor)) {
			a.identify(false);
			Armor old = h.belongings.armor;
			if (old != null && old.checkSeal() != null) { com.shatteredpixel.shatteredpixeldungeon.items.BrokenSeal s = old.detachSeal(); if (s != null) a.affixSeal(s); }
			h.belongings.armor = a; a.activate(h);
			got.append(' ').append(a.getClass().getSimpleName()).append('+').append(upg);
		}
		h.updateHT(true); h.HP = h.HT;
		for (int k = envInt("STARTHEAL", 2); k > 0; k--) give(new PotionOfHealing(), true);
		for (int k = envInt("STARTFOOD", 1); k > 0; k--) give(new Food(), true);
		System.out.println("SETUP head start: depth " + d + " lvl " + h.lvl + " str " + h.STR + " " + got + " heal " + envInt("STARTHEAL", 2));
	}

	/** A class to give from a category: by name (or a name's end), else a random one the dungeon could drop. */
	static Class<?> pick(Generator.Category cat, String name) {
		Class<?>[] cs = cat.classes;
		if (name != null) {
			for (Class<?> c : cs) if (c.getSimpleName().equalsIgnoreCase(name)) return c;
			for (Class<?> c : cs) if (c.getSimpleName().toLowerCase(Locale.ROOT).endsWith(name.toLowerCase(Locale.ROOT))) return c;
			return null;
		}
		List<Class<?>> ok = new ArrayList<>();
		float[] p = cat.defaultProbs != null ? cat.defaultProbs : cat.probs;
		for (int i = 0; i < cs.length; i++) if (p == null || i >= p.length || p[i] > 0) ok.add(cs[i]);
		if (ok.isEmpty()) ok.addAll(Arrays.asList(cs));
		return ok.get(r.nextInt(ok.size()));
	}

	static Generator.Category[] cats(String kind) {
		switch (kind) {
			case "ARTIFACTS": return new Generator.Category[]{ Generator.Category.ARTIFACT };
			case "WANDS": return new Generator.Category[]{ Generator.Category.WAND };
			case "RINGS": return new Generator.Category[]{ Generator.Category.RING };
			case "TRINKETS": return new Generator.Category[]{ Generator.Category.TRINKET };
			case "ARMOR": return new Generator.Category[]{ Generator.Category.ARMOR };
			default: return Generator.wepTiers;
		}
	}

	static void kit(Hero h) {
		Map<String, String> want = new LinkedHashMap<>();
		for (String k : KIT.toUpperCase(Locale.ROOT).split("[, ]+")) {
			if (k.isEmpty()) continue;
			if (k.equals("ALL")) { for (String x : KINDS) want.put(x, "random:1"); continue; }
			if (k.equals("RANDOM")) { want.put(KINDS[r.nextInt(4)], "random:1"); continue; }
			if (!k.endsWith("S") && !k.equals("ARMOR")) k = k + "S";   // (artifact, wand... as well)
			if (k.equals("WEAPONSS")) k = "WEAPONS";
			if (Arrays.asList(KINDS).contains(k)) want.put(k, "random:1");
		}
		for (String k : KINDS) if (System.getenv(k) != null && !System.getenv(k).isEmpty()) want.put(k, System.getenv(k));
		if (want.isEmpty()) return;
		String idm = env("KITID", "1"); int lvl = envInt("KITLVL", 0); boolean equip = "1".equals(System.getenv("KITEQUIP"));
		StringBuilder got = new StringBuilder();
		for (Map.Entry<String, String> e : want.entrySet()) {
			List<Class<?>> cls = new ArrayList<>();
			String spec = e.getValue();
			if (spec.toLowerCase(Locale.ROOT).startsWith("random")) {
				int n = 1; try { n = Integer.parseInt(spec.substring(spec.indexOf(':') + 1)); } catch (Exception x) { }
				for (int k = 0, guard = 0; k < n && guard < 50; guard++) {
					Generator.Category[] cs = cats(e.getKey());
					Class<?> c = pick(cs[cs.length == 1 ? 0 : r.nextInt(cs.length)], null);
					if (cls.contains(c) || h.belongings.getItem(c.asSubclass(Item.class)) != null || c == MagesStaff.class) continue;
					cls.add(c); k++;
				}
			} else for (String name : spec.split("[, ]+")) {
				Class<?> c = null;
				for (Generator.Category cat : cats(e.getKey())) if (c == null) c = pick(cat, name);
				if (c == null) System.out.println("SETUP kit: no " + e.getKey().toLowerCase(Locale.ROOT) + " called " + name);
				else cls.add(c);
			}
			for (Class<?> c : cls) {
				Item i = (Item) Reflection.newInstance(c);
				if (i == null) continue;
				if (i instanceof Wand) i.random();   // (its charges set up), then made plain
				i.cursed = false; i.level(0);
				for (int k = 0; k < lvl; k++) i.upgrade();
				if (i instanceof Wand) ((Wand) i).curCharges = ((Wand) i).maxCharges;
				if (i instanceof Artifact) Generator.removeArtifact(c.asSubclass(Artifact.class));
				boolean known = idm.equals("mix") ? r.nextBoolean() : !idm.equals("0");
				if (known) i.identify(false);
				boolean worn = false;
				if (equip && i instanceof EquipableItem) {
					boolean free = i instanceof Artifact || i instanceof Ring ? (h.belongings.artifact == null || h.belongings.misc == null || h.belongings.ring == null) : true;
					if (free) worn = ((EquipableItem) i).doEquip(h);
				}
				if (!worn) give(i, known);
				got.append(' ').append(c.getSimpleName()).append(known ? "" : "(unknown)").append(lvl > 0 ? "+" + lvl : "").append(worn ? "(worn)" : "");
			}
		}
		System.out.println("SETUP kit:" + got);
	}

	static void mild(Hero h) {
		int pct = envInt("MILDHP", 50);
		if (h.lvl != mildLvl) { mildLvl = h.lvl; int boost = (20 + 5 * (h.lvl - 1)) * pct / 100; if (h.HTBoost != boost) { h.HTBoost = boost; h.updateHT(true); } }
		if (Dungeon.depth > mildDeepest && Dungeon.branch == 0) {
			for (int d = mildDeepest + 1; d <= Dungeon.depth; d++) {
				if (d % 5 == 1) for (int k = envInt("MILDSOU", 2); k > 0; k--) give(new ScrollOfUpgrade(), true);
				int every = envInt("MILDHEAL", 3);
				if (every > 0 && d % every == 0) give(new PotionOfHealing(), true);
			}
			if (mildDeepest == 0) System.out.println("SETUP mild: hp +" + pct + "%, " + envInt("MILDSOU", 2) + " upgrades a region, healing every " + envInt("MILDHEAL", 3) + " floors");
			mildDeepest = Dungeon.depth;
		}
	}

	/** The talent points there are to spend, spent (each game its own picks, from the dungeon seed). Returns how many. */
	public static int spendTalents(Random r) {
		Hero h = Dungeon.hero; int n = 0;
		if (h == null) return 0;
		for (int tier = 1; tier <= Math.min(Talent.MAX_TALENT_TIERS, h.talents.size()); tier++) {
			for (int guard = 0; guard < 20 && h.talentPointsAvailable(tier) > 0; guard++) {
				List<Talent> can = new ArrayList<>();
				for (Map.Entry<Talent, Integer> e : h.talents.get(tier - 1).entrySet()) if (e.getValue() < e.getKey().maxPoints()) can.add(e.getKey());
				if (can.isEmpty()) break;
				h.upgradeTalent(can.get(r.nextInt(can.size()))); n++;
			}
		}
		return n;
	}
}
