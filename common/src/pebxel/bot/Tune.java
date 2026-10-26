package pebxel.bot;

/**
 * The models' tunable numbers, each overridable by an environment variable AS_NAME for the bot's tuning batches (the
 * watch has no environment: it gets the defaults, which are the tuned values). Percentages are given as whole numbers.
 */
public final class Tune {
	private Tune() { }

	public static double get(String name, double def) {
		try { String v = System.getenv("AS_" + name); return v == null || v.isEmpty() ? def : Double.parseDouble(v); } catch (Throwable e) { return def; }
	}
	public static boolean on(String name, boolean def) {
		try { String v = System.getenv("AS_" + name); return v == null || v.isEmpty() ? def : !v.equals("0"); } catch (Throwable e) { return def; }
	}

	// FightModel's hooks (Assist): P(death) thresholds in percent, the most health Healing is drunk at, rest
	public static final double DEATH = get("ODEATH", 10), ESC = get("OESC", 30), UP = get("OUP", 25), AVOID = get("OAVOID", 25), HEALMAX = get("OHEALMAX", 70);
	// (MT) the death odds a Healing potion is worth when drunk (FightModel.healAt), the health share below which it is
	// drunk with a foe after the hero or harm to come whatever the visible fight's odds (what the model can't see: more coming)
	public static final double HEALCOST = get("OHEALCOST", 2) / 100, HEALLOW = get("OHEALLOW", 25);
	public static final boolean CRAB = on("OCRAB", true);   // (MT: the Great Crab's block counted)
	public static final boolean FOES = on("OFOES", true);   // (MT: the trickster's poison, the swarm's splitting)
	public static final boolean HEALTICK = on("OHEALTICK", true);   // (MT: Healing heals over turns, as the game's Healing buff does)
	public static final boolean UPSEEN = on("OUPSEEN", true);   // (MT: the upgrades put into an unknown piece counted)
	public static final double UPLOW = get("OUPLOW", 35);   // (MT: up the stairs below this share of health with a foe after the hero, whatever the fight's odds)
	public static final double GAMBLE = get("OGAMBLE", 35);   // (MT: losing beside a foe, unknown potions and scrolls tried only below this share of health)
	public static final boolean UPPACK = on("OUPPACK", true);   // (MT: a pack piece is upgraded only if it is then worth wearing)
	public static final boolean RINGS = on("ORINGS", true);   // (MT: known fight rings counted in the hero's numbers)
	public static final double HOPE = get("OHOPE", 3) / 100;   // (MT: in a fight likely lost, a drink's edge on near-ties)
	public static final double FOEFX = get("OFOEFX", 100) / 100;   // (MT: the deep kinds' blows' effects past armour, scaled)
	public static final boolean KINDS = on("OKINDS", true);   // (M2: the kinds' behaviour in a fight: a brute's rage running down, the eye's beam cycle, scorpios backing off, spinners fleeing, Goo's pumping dodged, shooters' bolts till the hero is beside them)
	public static final boolean SLOTS = on("OSLOTS", true);   // (MT: FightModel counts the room beside the hero)
	public static final boolean HEAL2 = on("OHEAL2", true);   // (off: O's first rule, P(death) >= ODEATH)
	public static final double RESTK = get("ORESTK", 130) / 100, RESTLO = get("ORESTLO", 50) / 100, RESTHI = get("ORESTHI", 70) / 100;
	// GearValue: the gain needed to swap (share), the risk exponent, the curse rate of random gear, the boss's weight, a missing STR point's slowness
	public static final double GEARNEED = get("OGNEED", 5) / 100, RISK = get("ORISK", 2), CURSE = get("OCURSE", 30) / 100, BOSSW = get("OBOSSW", 10) / 100, SLOW = get("OSLOW", 120) / 100;
	// UpgradePlan: the weight of "now" against the region's end, the gain to read rather than hold, the Mage's staff-first count, TOE's horizon
	public static final double UPNOW = get("OUPNOW", 40) / 100, UPHOLD = get("OUPHOLD", 1) / 100;
	public static final int STAFF = (int) get("OSTAFF", 3), TOEN = (int) get("OTOEN", 6);
	public static final boolean TOE = on("OTOE", true);
	// Identify: keep an unknown potion this likely Healing; Trade: an Ankh's worth, the least worth bought, food when short
	// GearValue: surprise blows' share (the Rogue's cloak; everyone's ambushes: 6% of the honest bot's blows), the bow's shots before a fight
	public static final double SURE = get("OSURE", 6) / 100, SURER = get("OSURER", 25) / 100, BOWSHOTS = get("OBOW", 2);
	// UpgradePlan: armour's edge (a share off its score: armour stays on longer, cuts every blow, the guide's consensus)
	public static final double ARMB = get("OARMB", 0) / 100;
	public static final double KEEPHP = get("OKEEPHP", 85);   // (MT: a likely Healing is drink-tested below this share of health)
	public static final double KEEP = get("OKEEP", 30) / 100, ANKH = get("OANKH", 120), MINW = get("OMINW", 8), FOOD = get("OFOOD", 70);
}
