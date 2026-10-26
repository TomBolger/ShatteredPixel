package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.NPC;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import java.util.*;
import pebxel.bot.FightModel;

/**
 * FightModel's calibration against real games (env FIGHTCAL=1; only prints, changes nothing: the model rolls on its
 * own generators). A fight starts when a seen enemy is beside the hero or after it within 4 (the AI state is read
 * here, for the log only); its prediction is taken then; it ends when no enemy has been in it for 3 decisions, on a
 * new floor, or at death; one where no blow landed either way isn't counted (a mob only in sight). One line each: "FIGHTCAL depth d hp0 h pdeath p loss l | real loss r died 0/1 healed 0/1
 * mobs ...". sim/fightcal.py sums them up by predicted risk.
 */
final class FightCal {
	static final boolean ON = "1".equals(System.getenv("FIGHTCAL"));
	static boolean active; static int hp0, minHp, quiet, depth, heals0; static FightModel.Result pred; static String names;
	static String how = "";   // (M2: how the fight ended: won (one of its foes dead), left (all alive, out of sight), floor (stairs, a fall, a teleport off the floor), died)
	static Set<Integer> ids = new HashSet<>(); static Map<Integer, Integer> mobHp = new HashMap<>(); static boolean blows;

	static void newGame() { rT = -1; active = false; ids.clear(); mobHp.clear(); }

	static List<Mob> engaged(Hero h) {
		List<Mob> out = new ArrayList<>();
		for (Mob m : new ArrayList<>(Dungeon.level.mobs)) {
			if (!m.isAlive() || m.alignment != Char.Alignment.ENEMY || m instanceof NPC || m.invisible > 0 || !Dungeon.level.heroFOV[m.pos] || m.state == m.PASSIVE) continue;
			int d = Dungeon.level.distance(m.pos, h.pos);
			if (d <= 1 || (m.state == m.HUNTING && d <= 4)) out.add(m);
		}
		return out;
	}

	static int heals(Hero h) { PotionOfHealing p = h.belongings.getItem(PotionOfHealing.class); return p == null ? 0 : p.quantity(); }

	/** Each decision (the hero ready). */
	static void decision() {
		if (!ON) return;
		Hero h = Dungeon.hero; if (h == null) return;
		if (active && Dungeon.depth != depth) { how = "floor"; end(false); }
		List<Mob> ms = engaged(h);
		rate(h, ms);
		if (!active) {
			if (ms.isEmpty()) return;
			active = true; hp0 = h.HP; minHp = h.HP; quiet = 0; depth = Dungeon.depth; heals0 = heals(h); ids.clear(); mobHp.clear(); blows = false;
			pred = FightModel.evaluate(h, ms);
			StringBuilder sb = new StringBuilder(); for (Mob m : ms) { sb.append(m.getClass().getSimpleName()).append(m.state == m.SLEEPING ? "(z)" : "").append(','); ids.add(m.id()); mobHp.put(m.id(), m.HP); }
			names = sb.toString();
			return;
		}
		minHp = Math.min(minHp, h.HP);
		if (h.HP < hp0) blows = true;
		for (Integer id : mobHp.keySet()) { com.shatteredpixel.shatteredpixeldungeon.actors.Actor a = com.shatteredpixel.shatteredpixeldungeon.actors.Actor.findById(id); if (!(a instanceof Mob) || !((Mob) a).isAlive() || ((Mob) a).HP < mobHp.get(id)) blows = true; }
		for (Mob m : ms) if (ids.add(m.id())) names += "+" + m.getClass().getSimpleName() + ",";
		if (ms.isEmpty()) { if (++quiet >= 3) { how = "left"; for (Integer id : mobHp.keySet()) { com.shatteredpixel.shatteredpixeldungeon.actors.Actor a = com.shatteredpixel.shatteredpixeldungeon.actors.Actor.findById(id); if (!(a instanceof Mob) || !((Mob) a).isAlive()) { how = "won"; break; } } end(false); } } else quiet = 0;
	}

	// (MT) the damage rate: at a decision in a fight, the model's mean loss over the next RATE turns against the health
	// actually lost by then (no potion drunk between; deaths count the health there was). One check open at a time.
	static final double RATE = 5; static float rT = -1; static int rHp, rHeals, rDepth; static double rPred, rPd; static String rNames;
	static void rate(Hero h, List<Mob> ms) {
		float now = com.shatteredpixel.shatteredpixeldungeon.actors.Actor.now();
		if (rT >= 0 && (now - rT >= RATE || !h.isAlive() || Dungeon.depth != rDepth)) {
			if (heals(h) >= rHeals && Dungeon.depth == rDepth)
				System.out.printf(Locale.ROOT, "FIGHTRATE depth %d hp %d pred %.1f pd %.3f real %d mobs %s%n", rDepth, rHp, rPred, rPd, rHp - Math.max(0, h.HP), rNames);
			rT = -1;
		}
		if (rT < 0 && !ms.isEmpty() && h.isAlive()) {
			FightModel.Result r = FightModel.next(h, ms, RATE);
			rT = now; rHp = h.HP; rHeals = heals(h); rDepth = Dungeon.depth; rPred = r.lossMean; rPd = r.pDeath;
			StringBuilder sb = new StringBuilder(); for (Mob m : ms) sb.append(m.getClass().getSimpleName()).append('@').append(Dungeon.level.distance(m.pos, h.pos)).append(','); rNames = sb.toString();
		}
	}

	/** The fight ends (died: the hero is dead). */
	static void end(boolean died) {
		if (ON && died && rT >= 0) { System.out.printf(Locale.ROOT, "FIGHTRATE depth %d hp %d pred %.1f pd %.3f real %d mobs %s%n", rDepth, rHp, rPred, rPd, rHp, rNames); rT = -1; }
		if (!ON || !active) return;
		active = false;
		if (!died && !blows) return;   // (no blow either way: no fight, it was only in sight)
		Hero h = Dungeon.hero;
		int min = died ? 0 : Math.min(minHp, h == null ? 0 : h.HP);
		System.out.printf(Locale.ROOT, "FIGHTCAL depth %d hp0 %d pdeath %.3f loss %.1f p90 %.0f | real loss %d died %d healed %d end %s mobs %s%n",
				depth, hp0, pred.pDeath, pred.lossMean, pred.loss90, hp0 - min, died ? 1 : 0, h != null && heals(h) < heals0 ? 1 : 0, died ? "died" : how, names);
	}
}
