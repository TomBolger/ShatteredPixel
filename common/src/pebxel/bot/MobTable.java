package pebxel.bot;

/**
 * The regular monsters' numbers (Shattered v4.0.2 source, cross-checked with Shatterfish's codex/v4.0.0/mobs.json) and
 * each floor's spawn mix (actors/mobs/MobSpawner.standardMobRotation: the rotation's counts as weights), for the models
 * that look at fights not yet seen: gear values, the health to rest up to, the run's projection. No live mob is made
 * (a new Mob would roll the game's numbers and hash objects), so this is a plain table.
 */
public final class MobTable {
	private MobTable() { }

	/** name, HT, attack, defense, damage min/max, armour max, attack delay, speed, shooter. */
	static final Object[][] T = {
			{ "Rat", 8, 8, 2, 1, 4, 1, 1.0, 1.0, false },
			{ "Snake", 4, 10, 25, 1, 4, 0, 1.0, 1.0, false },
			{ "Gnoll", 12, 10, 4, 1, 6, 2, 1.0, 1.0, false },
			{ "Swarm", 50, 10, 5, 1, 4, 0, 1.0, 1.0, false },
			{ "Crab", 15, 12, 5, 1, 7, 4, 1.0, 2.0, false },
			{ "Slime", 20, 12, 5, 2, 5, 0, 1.0, 1.0, false },
			{ "Skeleton", 25, 12, 9, 2, 10, 5, 1.0, 1.0, false },
			{ "Thief", 20, 12, 12, 1, 10, 3, 0.5, 1.0, false },
			{ "DM100", 20, 11, 8, 2, 8, 4, 1.0, 1.0, true },
			{ "Guard", 40, 12, 10, 4, 12, 7, 1.0, 1.0, false },
			{ "Necromancer", 40, 12, 14, 2, 10, 5, 1.0, 1.0, false },   // (it fights by its skeleton: a skeleton's numbers)
			{ "Bat", 30, 16, 15, 5, 18, 4, 1.0, 2.0, false },
			{ "Brute", 40, 20, 15, 5, 25, 8, 1.0, 1.0, false },
			{ "Shaman", 35, 18, 15, 5, 10, 6, 1.0, 1.0, true },
			{ "Spinner", 50, 22, 17, 10, 20, 6, 1.0, 1.0, false },
			{ "DM200", 80, 20, 12, 10, 25, 8, 1.0, 1.0, false },
			{ "Ghoul", 45, 24, 20, 16, 22, 4, 1.0, 1.0, false },
			{ "Elemental", 60, 25, 20, 20, 25, 5, 1.0, 1.0, true },
			{ "Warlock", 70, 25, 18, 12, 18, 8, 1.0, 1.0, true },
			{ "Monk", 70, 30, 30, 12, 25, 2, 0.5, 1.0, false },
			{ "Golem", 120, 28, 15, 25, 30, 12, 1.0, 1.0, false },
			{ "Succubus", 80, 40, 25, 25, 30, 10, 1.0, 1.0, false },
			{ "Eye", 100, 30, 20, 20, 30, 10, 1.0, 1.0, true },
			{ "Scorpio", 110, 36, 24, 30, 40, 16, 1.0, 1.0, true },
			{ "RipperDemon", 60, 30, 22, 15, 25, 4, 0.5, 1.0, false },
			// bosses (their main phase)
			{ "Goo", 100, 10, 8, 1, 8, 2, 1.0, 1.0, false },
			{ "Tengu", 200, 15, 15, 6, 12, 5, 1.0, 1.0, true },
			{ "DM300", 300, 20, 15, 15, 25, 10, 1.0, 1.0, false },
			{ "DwarfKing", 300, 26, 22, 15, 25, 10, 1.0, 1.0, false },
	};

	/** MobSpawner.standardMobRotation by depth (1..26): kind names, repeated by weight. */
	static final String[][] ROT = {
			{},
			{ "Rat", "Rat", "Rat", "Snake" },
			{ "Rat", "Rat", "Snake", "Gnoll", "Gnoll" },
			{ "Rat", "Snake", "Gnoll", "Gnoll", "Gnoll", "Swarm", "Crab" },
			{ "Gnoll", "Swarm", "Crab", "Crab", "Slime", "Slime" },
			{ "Gnoll", "Swarm", "Crab", "Crab", "Slime", "Slime" },
			{ "Skeleton", "Skeleton", "Skeleton", "Thief", "Swarm" },
			{ "Skeleton", "Skeleton", "Skeleton", "Thief", "DM100", "Guard" },
			{ "Skeleton", "Skeleton", "Thief", "DM100", "DM100", "Guard", "Guard", "Necromancer" },
			{ "Skeleton", "Thief", "DM100", "DM100", "Guard", "Guard", "Necromancer", "Necromancer" },
			{ "Skeleton", "Thief", "DM100", "DM100", "Guard", "Guard", "Necromancer", "Necromancer" },
			{ "Bat", "Bat", "Bat", "Brute", "Shaman" },
			{ "Bat", "Bat", "Brute", "Brute", "Shaman", "Spinner" },
			{ "Bat", "Brute", "Brute", "Shaman", "Shaman", "Spinner", "Spinner", "DM200" },
			{ "Bat", "Brute", "Shaman", "Shaman", "Spinner", "Spinner", "DM200", "DM200" },
			{ "Bat", "Brute", "Shaman", "Shaman", "Spinner", "Spinner", "DM200", "DM200" },
			{ "Ghoul", "Ghoul", "Ghoul", "Elemental", "Warlock" },
			{ "Ghoul", "Elemental", "Elemental", "Warlock", "Monk" },
			{ "Ghoul", "Elemental", "Warlock", "Warlock", "Monk", "Monk", "Golem" },
			{ "Elemental", "Warlock", "Warlock", "Monk", "Monk", "Golem", "Golem", "Golem" },
			{ "Elemental", "Warlock", "Warlock", "Monk", "Monk", "Golem", "Golem", "Golem" },
			{ "Succubus", "Succubus", "Eye" },
			{ "Succubus", "Eye" },
			{ "Succubus", "Eye", "Eye", "Scorpio" },
			{ "Succubus", "Eye", "Eye", "Scorpio", "Scorpio", "Scorpio" },
			{ "Succubus", "Eye", "Eye", "Scorpio", "Scorpio", "Scorpio" },
			{ "Succubus", "Eye", "Eye", "Scorpio", "Scorpio", "Scorpio" },
	};

	/** The region's boss (depth 5, 10, 15, 20). */
	public static String boss(int depth) { return depth <= 5 ? "Goo" : depth <= 10 ? "Tengu" : depth <= 15 ? "DM300" : "DwarfKing"; }

	/** A fresh, awake, adjacent mob of this kind as a Fighter (null: not in the table). */
	public static Fighter fighter(String name) {
		for (Object[] r : T) if (r[0].equals(name)) {
			Fighter f = new Fighter();
			f.name = name; f.hp = f.ht = (Integer) r[1]; f.acc = (Integer) r[2]; f.eva = (Integer) r[3];
			f.dmg = Dist.normal((Integer) r[4], (Integer) r[5]); f.dr = Dist.normal(0, (Integer) r[6]);
			f.delay = (Double) r[7]; f.speed = (Double) r[8]; f.ranged = (Boolean) r[9];
			FoeView.special(f, name, f.ht);
			if (name.equals("Goo")) { f.lowAt = f.ht / 2; f.lowAcc = 15; f.lowEva = 12; f.lowDmg = Dist.normal(1, 12); f.boss = true; }
			if (name.equals("Tengu") || name.equals("DM300") || name.equals("DwarfKing")) f.boss = true;
			return f;
		}
		return null;
	}

	/** The spawn mix at depth d: kinds and weights (summing to 1). */
	public static String[] kinds(int d) { return ROT[Math.max(1, Math.min(26, d))]; }
}
