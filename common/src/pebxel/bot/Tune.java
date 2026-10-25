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
	public static final double KEEP = get("OKEEP", 30) / 100, ANKH = get("OANKH", 120), MINW = get("OMINW", 8), FOOD = get("OFOOD", 70);
}
