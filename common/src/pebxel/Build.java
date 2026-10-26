package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import java.util.*;

import static com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.*;

/**
 * The build a good player would pick (docs/strategy-reference.md section 3, the "bot-pick" columns, leaning to talents
 * that work without being used by hand): the subclass for Tengu's mask, and the order to spend talent points in.
 * Player choices, so no hidden information. The bot spends points with {@link #spendTalents}; the watch's assistant can
 * offer {@link #nextTalent} the same way. Env SUBCLASS=0/1 (tests) forces the class's first or second subclass.
 */
public final class Build {
	private Build() { }

	/** Each class's talents, most wanted first, per tier (tier 3 includes both subclasses' own: only those the hero has count). */
	static final Map<HeroClass, Talent[][]> ORDER = new EnumMap<>(HeroClass.class);
	static {
		ORDER.put(HeroClass.WARRIOR, new Talent[][]{
				{IRON_WILL, HEARTY_MEAL, IRON_WILL, HEARTY_MEAL, PROVOKED_ANGER, VETERANS_INTUITION},
				{LETHAL_MOMENTUM, IRON_STOMACH, LETHAL_MOMENTUM, IRON_STOMACH, LIQUID_WILLPOWER, LIQUID_WILLPOWER, RUNIC_TRANSFERENCE, IMPROVISED_PROJECTILES},
				{STRONGMAN, DEATHLESS_FURY, CLEAVE, STRONGMAN, DEATHLESS_FURY, CLEAVE, STRONGMAN, DEATHLESS_FURY, CLEAVE, ENDLESS_RAGE, ENHANCED_COMBO, HOLD_FAST, LETHAL_DEFENSE, ENRAGED_CATALYST},
				{HEROIC_ENERGY}});
		ORDER.put(HeroClass.MAGE, new Talent[][]{
				{BACKUP_BARRIER, SCHOLARS_INTUITION, BACKUP_BARRIER, LINGERING_MAGIC, EMPOWERING_MEAL, SCHOLARS_INTUITION},
				{ENERGIZING_MEAL, ENERGIZING_MEAL, WAND_PRESERVATION, ARCANE_VISION, INSCRIBED_POWER, SHIELD_BATTERY},
				{SOUL_EATER, EMPOWERED_STRIKE, DESPERATE_POWER, SOUL_SIPHON, EXCESS_CHARGE, SOUL_EATER, EMPOWERED_STRIKE, DESPERATE_POWER, MYSTICAL_CHARGE, NECROMANCERS_MINIONS, ALLY_WARP},
				{HEROIC_ENERGY}});
		ORDER.put(HeroClass.ROGUE, new Talent[][]{
				{CACHED_RATIONS, PROTECTIVE_SHADOWS, CACHED_RATIONS, PROTECTIVE_SHADOWS, SUCKER_PUNCH, THIEFS_INTUITION},
				{MYSTICAL_MEAL, SILENT_STEPS, MYSTICAL_MEAL, SILENT_STEPS, WIDE_SEARCH, INSCRIBED_STEALTH, ROGUES_FORESIGHT},
				{ENHANCED_LETHALITY, EVASIVE_ARMOR, LIGHT_CLOAK, ENHANCED_LETHALITY, EVASIVE_ARMOR, LIGHT_CLOAK, ENHANCED_LETHALITY, EVASIVE_ARMOR, ASSASSINS_REACH, SPEEDY_STEALTH, ENHANCED_RINGS, BOUNTY_HUNTER, PROJECTILE_MOMENTUM},
				{HEROIC_ENERGY}});
		ORDER.put(HeroClass.HUNTRESS, new Talent[][]{
				{NATURES_BOUNTY, FOLLOWUP_STRIKE, NATURES_BOUNTY, FOLLOWUP_STRIKE, NATURES_AID, SURVIVALISTS_INTUITION},
				{HEIGHTENED_SENSES, DURABLE_PROJECTILES, HEIGHTENED_SENSES, DURABLE_PROJECTILES, INVIGORATING_MEAL, REJUVENATING_STEPS, LIQUID_NATURE},
				{POINT_BLANK, SHARED_UPGRADES, BARKSKIN, POINT_BLANK, SHARED_UPGRADES, BARKSKIN, POINT_BLANK, SHARED_UPGRADES, SHIELDING_DEW, FARSIGHT, SEER_SHOT, DURABLE_TIPS, SHARED_ENCHANTMENT},
				{HEROIC_ENERGY}});
		ORDER.put(HeroClass.DUELIST, new Talent[][]{
				{PATIENT_STRIKE, AGGRESSIVE_BARRIER, PATIENT_STRIKE, STRENGTHENING_MEAL, AGGRESSIVE_BARRIER, ADVENTURERS_INTUITION},
				{FOCUSED_MEAL, LETHAL_HASTE, FOCUSED_MEAL, LIQUID_AGILITY, LIQUID_AGILITY, LETHAL_HASTE, WEAPON_RECHARGING, SWIFT_EQUIP},
				{PRECISE_ASSAULT, TWIN_UPGRADES, UNENCUMBERED_SPIRIT, PRECISE_ASSAULT, TWIN_UPGRADES, MONASTIC_VIGOR, TWIN_UPGRADES, VARIED_CHARGE, COMBINED_LETHALITY, COMBINED_ENERGY, DEADLY_FOLLOWUP},
				{HEROIC_ENERGY}});
		ORDER.put(HeroClass.CLERIC, new Talent[][]{
				{SATIATED_SPELLS, HOLY_INTUITION, SEARING_LIGHT, SHIELD_OF_LIGHT, SATIATED_SPELLS, SEARING_LIGHT},
				{ENLIGHTENING_MEAL, ENLIGHTENING_MEAL, BLESS, SUNRAY, DIVINE_SENSE, RECALL_INSCRIPTION},
				{LAY_ON_HANDS, HOLY_LANCE, CLEANSE, LAY_ON_HANDS, AURA_OF_PROTECTION, HALLOWED_GROUND, LAY_ON_HANDS, CLEANSE, WALL_OF_LIGHT, MNEMONIC_PRAYER, LIGHT_READING},
				{HEROIC_ENERGY}});
	}

	/** Tengu's mask: the subclass to take (the guide's default, or SUBCLASS=0/1). */
	public static HeroSubClass subclass(HeroClass c) {
		HeroSubClass[] s = c.subClasses();
		String f = null; try { f = System.getenv("SUBCLASS"); } catch (Throwable e) { }
		if (f != null && (f.equals("0") || f.equals("1"))) return s[Integer.parseInt(f)];
		switch (c) {
			case WARRIOR: return HeroSubClass.BERSERKER;
			case MAGE: return HeroSubClass.WARLOCK;
			case ROGUE: return HeroSubClass.ASSASSIN;
			case HUNTRESS: return HeroSubClass.SNIPER;
			case DUELIST: return HeroSubClass.CHAMPION;
			default: return HeroSubClass.PALADIN;
		}
	}

	/** The next talent point to spend, or null (none to spend, or none left to raise). The guide's order, with the
	 *  build's context on top ({@link #boost}: what the hero carries makes some talents worth more, e.g. a Horn of
	 *  Plenty the meal talents); with no context it is the guide's order exactly. */
	public static Talent nextTalent(Hero h) {
		Talent[][] ord = ORDER.get(h.heroClass);
		for (int tier = 1; tier <= Math.min(Talent.MAX_TALENT_TIERS, h.talents.size()); tier++) {
			if (h.talentPointsAvailable(tier) <= 0) continue;
			LinkedHashMap<Talent, Integer> have = h.talents.get(tier - 1);
			Talent best = null; double bs = -1e9; int pos = 0;
			for (Map.Entry<Talent, Integer> e : have.entrySet()) {
				Talent t = e.getKey(); int p = e.getValue(); pos++;
				if (p >= t.maxPoints()) continue;
				int idx = -1, seen = 0;   // (where its next point stands in the guide's order)
				if (ord != null && tier <= ord.length) for (int i = 0; i < ord[tier - 1].length; i++) if (ord[tier - 1][i] == t && ++seen > p) { idx = i; break; }
				double sc = -(idx >= 0 ? idx : 100 + pos) + BOOSTW * boost(t, h);
				if (sc > bs) { bs = sc; best = t; }
			}
			if (best != null) return best;
		}
		return null;
	}
	static final double BOOSTW = envD("BOOSTW", 4);
	static double envD(String k, double d) { try { String v = System.getenv("AS_" + k); return v == null ? d : Double.parseDouble(v); } catch (Throwable e) { return d; } }

	/** The build's context: how much more (or less) talent t is worth for what the hero carries, about -2..3 (each
	 *  point moves it BOOSTW places up the guide's order). A Horn of Plenty: the meal talents fire on every snack; a
	 *  strong staff or wands: the wand talents; a Cloak of Shadows: the stealth ones; rings, thrown weapons, spare
	 *  potions and scrolls, a second weapon. Only what the player knows he has. */
	public static double boost(Talent t, Hero h) {
		try {
			String n = t.name(); double b = 0;
			Ctx c = ctx(h);
			if (c.horn) {
				if (MEAL_FX.contains(n)) b += 2.5;
				if (n.equals("HEARTY_MEAL") || n.equals("IRON_STOMACH")) b += 1;
				if (n.equals("CACHED_RATIONS") || n.equals("NATURES_BOUNTY") || n.equals("SOUL_EATER")) b -= 1.5;   // (food is no worry)
			}
			if (c.wandPower >= 2 && WAND_T.contains(n)) b += Math.min(3, c.wandPower / 2.0);
			if (c.cloak && (n.equals("PROTECTIVE_SHADOWS") || n.equals("MYSTICAL_MEAL") || n.equals("LIGHT_CLOAK") || n.equals("SPEEDY_STEALTH") || n.equals("INSCRIBED_STEALTH") || n.equals("ENHANCED_LETHALITY"))) b += 1.5;
			if (n.equals("MYSTICAL_MEAL") && c.artifacts > 0) b += 1;
			if (n.equals("ENHANCED_RINGS")) b += c.rings > 0 || c.artifacts > 1 ? 2 : -2;
			if (c.missiles >= 3 && (n.equals("DURABLE_PROJECTILES") || n.equals("IMPROVISED_PROJECTILES") || n.equals("PROJECTILE_MOMENTUM") || n.equals("SHARED_ENCHANTMENT") || n.equals("DEADLY_FOLLOWUP") || n.equals("FOLLOWUP_STRIKE"))) b += 1;
			if (c.potions >= 3 && n.startsWith("LIQUID_")) b += 1;
			if (c.scrolls >= 3 && n.startsWith("INSCRIBED_")) b += 1;
			if (n.equals("TWIN_UPGRADES") || n.equals("VARIED_CHARGE") || n.equals("COMBINED_LETHALITY")) b += c.second ? 1 : -2;
			if (n.equals("HOLD_FAST") && c.armorTier >= 4) b += 1;
			if (n.equals("RUNIC_TRANSFERENCE") && !c.glyph) b -= 1;
			return b;
		} catch (Throwable e) { return 0; }
	}
	/** Why the context raises t (for the assistant's talent hint), or null. */
	public static String boostWhy(Talent t, Hero h) {
		Ctx c = ctx(h); String n = t.name();
		if (c.horn && MEAL_FX.contains(n)) return "every Horn of Plenty snack fires it";
		if (c.wandPower >= 2 && WAND_T.contains(n)) return "a strong wand";
		if (c.cloak && boost(t, h) > 0 && !n.contains("MEAL") && !n.equals("ENHANCED_RINGS")) return "the Cloak of Shadows";
		return boost(t, h) > 0 ? "your gear" : null;
	}
	static final Set<String> MEAL_FX = new HashSet<>(Arrays.asList("EMPOWERING_MEAL", "ENERGIZING_MEAL", "MYSTICAL_MEAL", "INVIGORATING_MEAL", "STRENGTHENING_MEAL", "FOCUSED_MEAL", "SATIATED_SPELLS", "ENLIGHTENING_MEAL"));
	static final Set<String> WAND_T = new HashSet<>(Arrays.asList("EMPOWERING_MEAL", "ENERGIZING_MEAL", "WAND_PRESERVATION", "SHIELD_BATTERY", "LINGERING_MAGIC", "DESPERATE_POWER", "EXCESS_CHARGE", "EMPOWERED_STRIKE", "MYSTICAL_CHARGE", "ARCANE_VISION", "INSCRIBED_POWER"));
	static final class Ctx { boolean horn, cloak, second, glyph; int wandPower, rings, artifacts, missiles, potions, scrolls, armorTier; }
	static Ctx ctxMemo; static Object ctxKey;
	static Ctx ctx(Hero h) {
		Object k = Arrays.asList(h, h.belongings.backpack.items.size(), h.belongings.weapon(), h.belongings.armor(), h.belongings.ring(), h.belongings.misc(), h.belongings.artifact(), com.shatteredpixel.shatteredpixeldungeon.Dungeon.depth, h.lvl);
		if (k.equals(ctxKey) && ctxMemo != null) return ctxMemo;
		Ctx c = new Ctx();
		for (com.shatteredpixel.shatteredpixeldungeon.items.Item i : h.belongings) {
			if (i instanceof com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HornOfPlenty && !i.cursed) c.horn = true;
			if (i instanceof com.shatteredpixel.shatteredpixeldungeon.items.artifacts.CloakOfShadows) c.cloak = true;
			if (i instanceof com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact) c.artifacts++;
			if (i instanceof com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring) c.rings++;
			if (i instanceof com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon) c.missiles += i.quantity();
			if (i instanceof com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion && ((com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion) i).isKnown()) c.potions += i.quantity();
			if (i instanceof com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll && ((com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll) i).isKnown()) c.scrolls += i.quantity();
			int lv = i.levelKnown ? i.level() : 0;
			if (i instanceof com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff) c.wandPower += 1 + lv;
			else if (i instanceof com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand) c.wandPower += 1 + Math.max(0, lv - 1);
		}
		c.second = h.belongings.secondWep() != null;
		if (h.belongings.armor() != null) { c.armorTier = h.belongings.armor().tier; c.glyph = h.belongings.armor().glyph != null; }
		ctxKey = k; ctxMemo = c;
		return c;
	}

	/** The armour ability for the King's Crown (the guide's picks: the simplest strong one; the Mage's blast is keyed
	 *  to the staff's wand). */
	public static com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.ArmorAbility ability(HeroClass c) {
		String want;
		switch (c) {
			case WARRIOR: want = "HeroicLeap"; break;
			case MAGE: want = "ElementalBlast"; break;
			case ROGUE: want = "SmokeBomb"; break;
			case HUNTRESS: want = "NaturesPower"; break;
			case DUELIST: want = "ElementalStrike"; break;
			default: want = "AscendedForm";
		}
		for (com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.ArmorAbility a : c.armorAbilities()) if (a.getClass().getSimpleName().equals(want)) return a;
		return c.armorAbilities()[0];
	}

	/** A talent's "type", the family it shares with talents of other classes (Shattered lines its talents up by slot:
	 *  each class has a meal, an intuition, a potion or scroll one...). Tier 3 subclass talents and the armour abilities'
	 *  tier 4 talents get the family of what they do; the abilities' own odd ones are ABILITY. Used by the simulated
	 *  human player (HumanTalents: people stick to the kinds they like) and, later, for the build's context (a Horn of
	 *  Plenty makes the FOOD ones better). The families:
	 *  FOOD (meals, rations, stomach), IDENTIFY (the intuitions), CONSUMABLE (potions and scrolls: liquid, inscribed),
	 *  DEFENSE (shields, barriers, armour, healing, not dying), MELEE (blows, combos, the weapon), RANGED (thrown and
	 *  shot), MAGIC (wands, charges, spells, allies), STEALTH (sneaking, searching, seeing, moving), GEAR (rings,
	 *  upgrades and glyphs moved about), ABILITY (the armour abilities' own). */
	public static String family(Talent t) {
		String f = FAMILY.get(t.name());
		if (f != null) return f;
		String n = t.name();
		if (n.contains("MEAL") || n.contains("RATION")) return "FOOD";
		if (n.contains("INTUITION")) return "IDENTIFY";
		if (n.startsWith("LIQUID") || n.startsWith("INSCRIBED")) return "CONSUMABLE";
		return "ABILITY";
	}
	static final Map<String, String> FAMILY = new HashMap<>();
	static void fam(String f, String... ts) { for (String t : ts) FAMILY.put(t, f); }
	static {
		fam("FOOD", "HEARTY_MEAL", "IRON_STOMACH", "EMPOWERING_MEAL", "ENERGIZING_MEAL", "CACHED_RATIONS", "MYSTICAL_MEAL", "NATURES_BOUNTY",
				"INVIGORATING_MEAL", "STRENGTHENING_MEAL", "FOCUSED_MEAL", "SATIATED_SPELLS", "ENLIGHTENING_MEAL", "SOUL_EATER");
		fam("IDENTIFY", "VETERANS_INTUITION", "SCHOLARS_INTUITION", "THIEFS_INTUITION", "SURVIVALISTS_INTUITION", "ADVENTURERS_INTUITION", "HOLY_INTUITION");
		fam("CONSUMABLE", "LIQUID_WILLPOWER", "INSCRIBED_POWER", "INSCRIBED_STEALTH", "LIQUID_NATURE", "LIQUID_AGILITY", "RECALL_INSCRIPTION", "LIGHT_READING");
		fam("DEFENSE", "IRON_WILL", "HOLD_FAST", "DEATHLESS_FURY", "LETHAL_DEFENSE", "BACKUP_BARRIER", "SHIELD_BATTERY", "DESPERATE_POWER", "SOUL_SIPHON",
				"REACTIVE_BARRIER", "PROTECTIVE_SHADOWS", "EVASIVE_ARMOR", "DEATHLY_DURABILITY", "CLONED_ARMOR", "NATURES_AID", "BARKSKIN", "SHIELDING_DEW",
				"AGGRESSIVE_BARRIER", "MONASTIC_VIGOR", "INVIGORATING_VICTORY", "SHIELD_OF_LIGHT", "CLEANSE", "LAY_ON_HANDS", "AURA_OF_PROTECTION",
				"WALL_OF_LIGHT", "SUSTAINED_RETRIBUTION", "SHRUG_IT_OFF", "LIFE_LINK", "STASIS", "RATSISTANCE");
		fam("MELEE", "PROVOKED_ANGER", "LETHAL_MOMENTUM", "STRONGMAN", "ENDLESS_RAGE", "ENRAGED_CATALYST", "CLEAVE", "ENHANCED_COMBO", "BODY_SLAM",
				"STRIKING_WAVE", "EVEN_THE_ODDS", "EMPOWERED_STRIKE", "SUCKER_PUNCH", "ENHANCED_LETHALITY", "ASSASSINS_REACH", "BOUNTY_HUNTER",
				"SHADOW_BLADE", "FEAR_THE_REAPER", "FOLLOWUP_STRIKE", "PATIENT_STRIKE", "LETHAL_HASTE", "WEAPON_RECHARGING", "SWIFT_EQUIP",
				"PRECISE_ASSAULT", "DEADLY_FOLLOWUP", "VARIED_CHARGE", "COMBINED_LETHALITY", "UNENCUMBERED_SPIRIT", "COMBINED_ENERGY",
				"CLOSE_THE_GAP", "ELIMINATION_MATCH", "STRIKING_FORCE", "EXPOSE_WEAKNESS", "SEARING_LIGHT", "BODY_FORM");
		fam("RANGED", "IMPROVISED_PROJECTILES", "PROJECTILE_MOMENTUM", "DURABLE_PROJECTILES", "POINT_BLANK", "SEER_SHOT", "FARSIGHT",
				"SHARED_ENCHANTMENT", "SHARED_UPGRADES", "DURABLE_TIPS", "FAN_OF_BLADES", "PROJECTING_BLADES", "SPIRIT_BLADES", "EAGLE_EYE", "GO_FOR_THE_EYES");
		fam("MAGIC", "LINGERING_MAGIC", "WAND_PRESERVATION", "MYSTICAL_CHARGE", "EXCESS_CHARGE", "NECROMANCERS_MINIONS", "ALLY_WARP", "BLAST_RADIUS",
				"ELEMENTAL_POWER", "WILD_POWER", "FIRE_EVERYTHING", "CONSERVED_MAGIC", "SUNRAY", "BLESS", "HOLY_LANCE", "HALLOWED_GROUND",
				"MNEMONIC_PRAYER", "DIVINE_INTERVENTION", "JUDGEMENT", "FLASH", "MIND_FORM", "SPIRIT_FORM", "BEAMING_RAY", "ELEMENTAL_REACH", "DIRECTED_POWER", "HEROIC_ENERGY");
		fam("STEALTH", "WIDE_SEARCH", "SILENT_STEPS", "ROGUES_FORESIGHT", "LIGHT_CLOAK", "SPEEDY_STEALTH", "HASTY_RETREAT", "SHADOW_STEP",
				"ARCANE_VISION", "HEIGHTENED_SENSES", "REJUVENATING_STEPS", "DIVINE_SENSE", "SWIFT_SPIRIT", "DOUBLE_JUMP", "FEIGNED_RETREAT");
		fam("GEAR", "RUNIC_TRANSFERENCE", "ENHANCED_RINGS", "TWIN_UPGRADES");
	}

	/** Every point there is to spend, spent. Returns how many. */
	public static int spendTalents(Hero h) {
		int n = 0;
		for (Talent t; n < 40 && (t = nextTalent(h)) != null; n++) h.upgradeTalent(t);
		return n;
	}
}
