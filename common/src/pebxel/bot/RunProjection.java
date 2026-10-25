package pebxel.bot;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;

/**
 * The run ahead (OG's TOE, "the Theory of Everything plan", re-derived for Shattered, smaller): the next floors one by
 * one, the hero a level a floor stronger (HP +5, accuracy and evasion +1: Hero.lvlUp), STR from the region's Strength
 * potions (2 a region), the Upgrades to come (3 a region) all going into one piece (the build), each floor's fights
 * (about 6 + depth % 5, its spawn mix: MobTable) costed with FightModel.duelCost against the health then, the region's
 * boss on its floor. A floor's "load" is the sum of its fights' (cost / health)^2 (big fights weigh more) and its survival
 * exp(-(0.05 + 0.55 load)): OG's calibration for PD 1.9.1 (cal1, 384 games), not yet refitted for Shattered.
 *
 * {@link #survival}: the odds of living through the next n floors with a build; UpgradePlan uses it (on by default, AS_OTOE=0 off) to choose
 * the piece whose build lives longest, not just the one that helps most now.
 */
public final class RunProjection {
	private RunProjection() { }

	/** P(alive after n floors) wearing w and a, the Upgrades to come going into build (w or a; null: none to either). */
	public static double survival(Hero h, KindOfWeapon w, int wl, Armor a, int al, Item build, int n) {
		double surv = 1;
		int d0 = Dungeon.depth;
		for (int k = 0; k < n; k++) {
			int d = d0 + k;
			double ups = 0.6 * k, str = 0.5 * k;
			int wlk = wl + (build == w && w != null ? (int) Math.round(ups) : 0), alk = al + (build == a && a != null ? (int) Math.round(ups) : 0);
			Fighter me = HeroView.of(h, w, a, h.STR() + (int) Math.round(str), wlk, alk, k, Ench.of(w), Ench.of(a));
			me.hp = me.ht; me.heals = 0; me.rangedShots = 0;
			double zap = (w instanceof MagesStaff ? UpgradePlan.zap((MagesStaff) w, wlk) : 0) + GearValue.prep(me, h, w, wlk, a, (int) Math.round(str));
			double load = 0;
			if (d % 5 == 0) {
				Fighter b = MobTable.fighter(MobTable.boss(d));
				if (b != null) { if (zap > 0) b.hp = Math.max(1, (int) (b.hp - 3 * zap)); double x = FightModel.duelCost(me, b) / me.ht; load += 0.45 + x * x; }
			} else {
				String[] ks = MobTable.kinds(d); int nf = 6 + d % 5;
				for (String kd : ks) {
					Fighter f = MobTable.fighter(kd); if (f == null) continue;
					if (zap > 0) f.hp = Math.max(1, (int) (f.hp - zap));
					double x = FightModel.duelCost(me, f) / me.ht;
					load += (double) nf / ks.length * x * x;
				}
			}
			surv *= Math.exp(-(0.05 + 0.55 * load));
		}
		return surv;
	}
}
