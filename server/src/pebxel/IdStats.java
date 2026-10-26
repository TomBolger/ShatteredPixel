package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.*;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.*;
import java.util.*;
import pebxel.bot.Identify;

/**
 * How the bot's identifying went (agent ID; the harness: it looks at what the unknown items really are, the
 * decisions never do). One "IDSTATS" line a game: test drinks and what they were (Healing wasted, harmful ones),
 * dying gambles, test reads, Healing known by depth 5, Healing drunk on its clues alone, puzzle prizes gone for,
 * a death with an unknown Healing in the pack, and how good the odds were: each label as it became known, the odds
 * its true kind had the decision before (top-1 right, mean log).
 */
final class IdStats {
	private IdStats() {}
	static int testP, wasteHeal, harm, gamble, gambleHeal, readT, bHeal, puz, puzDoor, puzThrow, n, top1; static double logp;
	static int heal5 = -1;
	static final Map<String, Integer> drunk = new TreeMap<>(), read = new TreeMap<>();
	static Map<Class<?>, double[]> lastP = new HashMap<>(), lastS = new HashMap<>();
	static Set<Class<?>> unkP = new HashSet<>(), unkS = new HashSet<>();

	static void newGame() {
		testP = wasteHeal = harm = gamble = gambleHeal = readT = bHeal = puz = puzDoor = puzThrow = n = top1 = 0; logp = 0; heal5 = -1;
		drunk.clear(); read.clear(); lastP = new HashMap<>(); lastS = new HashMap<>(); unkP = new HashSet<>(); unkS = new HashSet<>();
	}
	static boolean harmful(Item i) { return i instanceof PotionOfLiquidFlame || i instanceof PotionOfToxicGas || i instanceof PotionOfParalyticGas || i instanceof PotionOfFrost; }

	static void decision(Assist.Target t) {
		Hero h = Dungeon.hero; if (h == null) return;
		// labels learnt since the last decision: how likely their kind was thought
		learnt(unkP, Potion.getUnknown(), lastP); learnt(unkS, Scroll.getUnknown(), lastS);
		unkP = new HashSet<>(); for (Object o : Potion.getUnknown()) unkP.add((Class<?>) o);
		unkS = new HashSet<>(); for (Object o : Scroll.getUnknown()) unkS.add((Class<?>) o);
		lastP = copy(Identify.odds(true)); lastS = copy(Identify.odds(false));
		if (heal5 < 0 && Dungeon.depth >= 5) heal5 = new PotionOfHealing().isKnown() ? 1 : 0;
		if (t == null || t.why == null) return;
		String w = t.why;
		if (t.kind == Assist.USE && t.item instanceof Potion && Potion.AC_DRINK.equals(t.action) && !Assist.known(t.item)) {
			String k = t.item.getClass().getSimpleName().replace("PotionOf", "");
			if (w.startsWith("drink to learn")) { testP++; drunk.merge(k, 1, Integer::sum); if (t.item instanceof PotionOfHealing) wasteHeal++; if (harmful(t.item)) harm++; }
			else if (w.startsWith("dying: gamble")) { gamble++; if (t.item instanceof PotionOfHealing) gambleHeal++; else if (harmful(t.item)) harm++; }
			else if (w.contains("heal")) bHeal++;
			else if (harmful(t.item)) harm++;
		}
		if (t.kind == Assist.USE && t.item instanceof Scroll && Scroll.AC_READ.equals(t.action) && !Assist.known(t.item) && w.startsWith("read to learn")) { readT++; read.merge(t.item.getClass().getSimpleName().replace("ScrollOf", ""), 1, Integer::sum); }
		if (w.contains(": the prize past") || w.startsWith("it:magical fire") || w.startsWith("it:barricade")) puz++;
		if (w.contains("'s door: test")) puzDoor++;
		if (w.contains("likely Frost") || w.contains("likely Liquid Flame")) puzThrow++;
	}
	static Map<Class<?>, double[]> copy(Map<Class<?>, double[]> m) { Map<Class<?>, double[]> o = new HashMap<>(); for (Map.Entry<Class<?>, double[]> e : m.entrySet()) o.put(e.getKey(), e.getValue().clone()); return o; }
	static void learnt(Set<Class<?>> before, Set<?> now, Map<Class<?>, double[]> odds) {
		for (Class<?> c : before) if (!now.contains(c)) {
			double[] r = odds.get(c); if (r == null) continue;
			boolean pot = Potion.class.isAssignableFrom(c); int k = Identify.pos(Identify.kinds(pot), c); if (k < 0) continue;
			int b = 0; for (int i = 1; i < 12; i++) if (r[i] > r[b]) b = i;
			n++; if (b == k) top1++; logp += Math.log(Math.max(1e-4, r[k]));
		}
	}

	static String line(String end) {
		Hero h = Dungeon.hero; int unkHeal = 0;
		if (h != null) for (Item i : h.belongings) if (i instanceof PotionOfHealing && !((Potion) i).isKnown()) unkHeal += i.quantity();
		StringBuilder sb = new StringBuilder("IDSTATS ");
		sb.append("testP=").append(testP).append(";wasteHeal=").append(wasteHeal).append(";harm=").append(harm).append(";gamble=").append(gamble).append(";gambleHeal=").append(gambleHeal)
				.append(";readT=").append(readT).append(";heal5=").append(heal5).append(";bHeal=").append(bHeal).append(";puz=").append(puz).append(";puzDoor=").append(puzDoor).append(";puzThrow=").append(puzThrow)
				.append(";diedUnkHeal=").append("died".equals(end) && unkHeal > 0 ? 1 : 0).append(";learnt=").append(n).append(";top1=").append(top1).append(";logp=").append(String.format("%.2f", logp))
				.append(";clues=").append(Clues.clues).append(";drunk=");
		for (Map.Entry<String, Integer> e : drunk.entrySet()) sb.append(e.getKey()).append(':').append(e.getValue()).append(',');
		sb.append(";read="); for (Map.Entry<String, Integer> e : read.entrySet()) sb.append(e.getKey()).append(':').append(e.getValue()).append(',');
		return sb.append(';').toString();
	}
}
