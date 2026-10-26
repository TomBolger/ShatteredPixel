package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import pebxel.bot.*;

/** (MT) Prints FoeView's numbers for the kinds the damage-rate check found far off (a diagnosis aid, prints only). */
public final class FoeDump {
	public static void main(String[] a) throws Exception {
		BotModelTest.start(HeroClass.WARRIOR, 1234);
		Class<?>[] ks = { Eye.class, Succubus.class, RipperDemon.class, Elemental.FireElemental.class, Spinner.class, Ghoul.class, Warlock.class, Monk.class, Brute.class, Piranha.class, Goo.class, Bat.class, Scorpio.class };
		for (Class<?> k : ks) {
			Mob m = (Mob) k.getDeclaredConstructor().newInstance();
			m.pos = Dungeon.hero.pos + 1; m.state = m.HUNTING;
			Fighter f = FoeView.of(m, Dungeon.hero);
			System.out.println("FOE " + f + " zap " + f.zapDmg + " dmg mean " + f.dmg.mean() + " shooter " + FoeView.shooter(m));
		}
		System.exit(0);
	}
}
