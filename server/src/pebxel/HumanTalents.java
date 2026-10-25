package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.ArmorAbility;
import java.util.*;

/**
 * TALENTS=human (training): a simulated human's build, so the bot learns to play whatever a player picked. Each talent
 * point goes to a random talent the hero can raise, weighted 1 + TALBIAS (default 4) x the points already spent in
 * that talent's family ({@link Build#family}: FOOD, IDENTIFY, CONSUMABLE, DEFENSE, MELEE, RANGED, MAGIC, STEALTH, GEAR,
 * ABILITY): people stick to the kinds they like, and raise again what they picked. The subclass (Tengu's mask) and the
 * armour ability (the Dwarf King's crown) are random too. Seeded by SIMSEED and the dungeon seed: a game replays the
 * same build. TALENTS=plan (the default) keeps the guide's build (Build); CHEAT=1 without TALENTS keeps its uniform picks.
 */
public final class HumanTalents {
	private HumanTalents() { }

	public static final boolean ON = "human".equals(System.getenv("TALENTS"));
	static final double BIAS = Double.parseDouble(System.getenv().getOrDefault("TALBIAS", "4"));
	static Random r = new Random(1);
	static HeroSubClass sub; static ArmorAbility abil;
	static final List<String> picks = new ArrayList<>();

	public static void newGame(long seed) {
		long s = Long.parseLong(System.getenv().getOrDefault("SIMSEED", "0"));
		r = new Random(s * 1_000_003L + seed * 31 + 7);
		sub = null; abil = null; picks.clear();
	}

	/** Points spent in each family so far. */
	static Map<String, Integer> families(Hero h) {
		Map<String, Integer> m = new HashMap<>();
		for (LinkedHashMap<Talent, Integer> tier : h.talents) for (Map.Entry<Talent, Integer> e : tier.entrySet())
			if (e.getValue() > 0) m.merge(Build.family(e.getKey()), e.getValue(), Integer::sum);
		return m;
	}

	/** Every talent point there is, spent as a human might. Returns how many. */
	public static int spend(Hero h) {
		int n = 0;
		for (int guard = 0; guard < 40; guard++) {
			Map<String, Integer> fam = families(h);
			List<Talent> can = new ArrayList<>(); List<Double> w = new ArrayList<>(); double sum = 0;
			for (int tier = 1; tier <= Math.min(Talent.MAX_TALENT_TIERS, h.talents.size()); tier++) {
				if (h.talentPointsAvailable(tier) <= 0) continue;
				for (Map.Entry<Talent, Integer> e : h.talents.get(tier - 1).entrySet()) {
					if (e.getValue() >= e.getKey().maxPoints()) continue;
					double x = 1 + BIAS * fam.getOrDefault(Build.family(e.getKey()), 0);
					can.add(e.getKey()); w.add(x); sum += x;
				}
			}
			if (can.isEmpty()) break;
			double p = r.nextDouble() * sum; int k = 0;
			while (k < can.size() - 1 && (p -= w.get(k)) >= 0) k++;
			Talent t = can.get(k);
			h.upgradeTalent(t); n++;
			picks.add(t.name() + "/" + Build.family(t));
		}
		return n;
	}

	/** The subclass this game's human takes. */
	public static HeroSubClass subclass(HeroClass c) {
		if (sub == null) { HeroSubClass[] s = c.subClasses(); sub = s[r.nextInt(s.length)]; }
		return sub;
	}

	/** The armour ability this game's human takes. */
	public static ArmorAbility ability(HeroClass c) {
		if (abil == null) { ArmorAbility[] a = c.armorAbilities(); abil = a[r.nextInt(a.length)]; }
		return abil;
	}

	/** For the game's report: the families by points, then the subclass and ability taken. */
	static String summary(Hero h) {
		StringBuilder sb = new StringBuilder();
		families(h).entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).forEach(e -> sb.append(e.getKey()).append(':').append(e.getValue()).append(' '));
		sb.append("sub ").append(h.subClass).append(" ability ").append(h.armorAbility == null ? "-" : h.armorAbility.getClass().getSimpleName());
		return sb.toString();
	}
}
