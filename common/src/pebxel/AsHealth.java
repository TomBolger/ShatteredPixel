package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import java.util.*;
import pebxel.Assist.Target;
import pebxel.bot.FightModel;
import static pebxel.Assist.*;

/**
 * The health policy (agent EG): the honest early game's caution, as one last pass over the plan (Assist.planList).
 */
final class AsHealth {
	private AsHealth() {}

	static final boolean DBG = "1".equals(System.getenv("EGDBG"));

	/** The list as the rules made it; entry 0 may be replaced. */
	static void after(List<Target> out) {
		try {
			Hero H = Dungeon.hero; int hp = H.pos;
			if (DBG && !out.isEmpty()) {
				List<Mob> fs = oFightList();
				if (!fs.isEmpty()) {
					FightModel.Result r = FightModel.engaged(H, fs);
					StringBuilder b = new StringBuilder();
					for (Mob m : fs) b.append(m.getClass().getSimpleName()).append(':').append(m.HP).append('@').append(distance(m.pos, hp)).append(hunting(m) ? "h" : "").append(',');
					System.out.println(String.format(Locale.ROOT, "EGF t %.0f d %d hp %d/%d up %d pd %.2f loss %.1f p90 %.0f | %s | A %s", Actor.now(), Dungeon.depth, H.HP, H.HT, walk(L.entrance()), r == null ? -1 : r.pDeath, r == null ? -1 : r.lossMean, r == null ? -1 : r.loss90, b, out.get(0).why));
				}
			}
		} catch (Throwable e) { lastError = e; }
	}
}
