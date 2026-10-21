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
 * new floor, or at death. One line each: "FIGHTCAL depth d hp0 h pdeath p loss l | real loss r died 0/1 healed 0/1
 * mobs ...". sim/fightcal.py sums them up by predicted risk.
 */
final class FightCal {
	static final boolean ON = "1".equals(System.getenv("FIGHTCAL"));
	static boolean active; static int hp0, minHp, quiet, depth, heals0; static FightModel.Result pred; static String names;
	static Set<Integer> ids = new HashSet<>();

	static void newGame() { active = false; ids.clear(); }

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
		if (active && Dungeon.depth != depth) end(false);
		List<Mob> ms = engaged(h);
		if (!active) {
			if (ms.isEmpty()) return;
			active = true; hp0 = h.HP; minHp = h.HP; quiet = 0; depth = Dungeon.depth; heals0 = heals(h); ids.clear();
			pred = FightModel.evaluate(h, ms);
			StringBuilder sb = new StringBuilder(); for (Mob m : ms) { sb.append(m.getClass().getSimpleName()).append(m.state == m.SLEEPING ? "(z)" : "").append(','); ids.add(m.id()); }
			names = sb.toString();
			return;
		}
		minHp = Math.min(minHp, h.HP);
		for (Mob m : ms) if (ids.add(m.id())) names += "+" + m.getClass().getSimpleName() + ",";
		if (ms.isEmpty()) { if (++quiet >= 3) end(false); } else quiet = 0;
	}

	/** The fight ends (died: the hero is dead). */
	static void end(boolean died) {
		if (!ON || !active) return;
		active = false;
		Hero h = Dungeon.hero;
		int min = died ? 0 : Math.min(minHp, h == null ? 0 : h.HP);
		System.out.printf(Locale.ROOT, "FIGHTCAL depth %d hp0 %d pdeath %.3f loss %.1f p90 %.0f | real loss %d died %d healed %d mobs %s%n",
				depth, hp0, pred.pDeath, pred.lossMean, pred.loss90, hp0 - min, died ? 1 : 0, h != null && heals(h) < heals0 ? 1 : 0, names);
	}
}
