package pebxel.bot;

/**
 * One side of a fight as the models see it: the numbers Char.hit and Char.attack roll with (Shattered v4.0.2,
 * actors/Char.java), nothing else. Built from the hero ({@link HeroView}) or a mob ({@link FoeView}); plain data, so
 * the models can also be run on made-up fighters (tests, projections).
 */
public final class Fighter {
	public String name = "?";
	/** Accuracy (attackSkill) and evasion (defenseSkill): hit chance = Dist.hit(acc, other's eva). */
	public double acc, eva;
	/** The damage roll, before the other's armour. */
	public Dist dmg = Dist.point(1);
	/** The armour roll (drRoll). */
	public Dist dr = Dist.point(0);
	/** Time per attack (attackDelay) and moves per turn (speed()). */
	public double delay = 1, speed = 1;
	public int hp = 1, ht = 1, shield;
	/** Foe only: cells away (1 beside the hero), attacks from where it stands (a shooter), asleep (the first blow is a
	 *  surprise: a sure hit), a boss. */
	public int dist = 1;
	public boolean ranged, asleep, boss;
	/** Foe only: the damage it takes is capped as a slime's (Slime.damage); extra health it fights on with (a brute's
	 *  rage shield) and the damage roll it fights with then. */
	public boolean slimeCap;
	public int rageHp; public Dist rageDmg;
	/** Foe only: the damage its death does to the hero beside it (a skeleton's bones: armour counts twice), and the health it heals per blow
	 *  that lands (a vampire bat). */
	public Dist deathDmg; public double leech;
	/** Foe only: at or below this health it fights with other numbers (Goo below half: accuracy 15, evasion x1.5, 1-12). */
	/** Foe only: a shooter's bolt from afar (Char.hit with magic: accuracy x2, no armour): DM-100, shamans, warlocks, eyes. */
	public Dist zapDmg;
	public int lowAt = -1; public double lowAcc, lowEva; public Dist lowDmg;
	/** Hero only: shots before contact (the spirit bow, thrown weapons, wand charges): accuracy factor at range (Dist.hit
	 *  is skipped when sure: magic bolts always hit), damage, delay, how many. */
	public double rangedAcc; public Dist rangedDmg; public double rangedDelay = 1; public int rangedShots; public boolean rangedSure;
	/** Hero only: healing it can drink mid-fight (a known Potion of Healing: 0.8 HT + 14), how many. */
	public int heals; public int healAmount;
	/** Hero only: a factor on the damage the hero takes (glyphs, a weapon that slows or dazes: {@link Ench#taken}). */
	public double taken = 1;

	public Fighter copy() {
		Fighter f = new Fighter();
		f.name = name; f.acc = acc; f.eva = eva; f.dmg = dmg; f.dr = dr; f.delay = delay; f.speed = speed; f.hp = hp; f.ht = ht; f.shield = shield;
		f.dist = dist; f.ranged = ranged; f.asleep = asleep; f.boss = boss; f.slimeCap = slimeCap; f.rageHp = rageHp; f.rageDmg = rageDmg;
		f.deathDmg = deathDmg; f.leech = leech; f.zapDmg = zapDmg; f.lowAt = lowAt; f.lowAcc = lowAcc; f.lowEva = lowEva; f.lowDmg = lowDmg; f.rangedAcc = rangedAcc; f.rangedDmg = rangedDmg; f.rangedDelay = rangedDelay; f.rangedShots = rangedShots;
		f.rangedSure = rangedSure; f.heals = heals; f.healAmount = healAmount; f.taken = taken;
		return f;
	}

	/** Slime.damage: 5/6/7/8/9/10 taken at 5/7/10/14/19/25 dealt. */
	static int slime(int d) { return d >= 5 ? (int) (4 + (Math.sqrt(8 * (d - 4) + 1) - 1) / 2) : d; }

	/** The damage per blow that lands from a on b (after b's armour; a slime's cap), as a distribution. */
	public static Dist blow(Dist dmg, Fighter b) {
		Dist d = dmg.minus0(b.dr);
		if (!b.slimeCap) return d;
		double[] r = new double[slime(d.hi()) + 1];
		for (int k = 0; k < d.p.length; k++) r[slime(d.lo + k)] += d.p[k];
		return new Dist(0, r);
	}

	@Override public String toString() {
		return String.format("%s hp %d/%d acc %.1f eva %.1f dmg %s dr %s dly %.2f spd %.2f%s%s", name, hp, ht, acc, eva, dmg, dr, delay, speed,
				dist > 1 ? " dist " + dist : "", ranged ? " ranged" : "");
	}
}
