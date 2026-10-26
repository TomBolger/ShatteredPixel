package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;

/** The test harness's view of the hero's kit on each new floor (server only; true levels, for the report, never for decisions). */
final class GearLog {
	private GearLog() { }

	static String item(Item i) {
		if (i == null) return "-";
		String t = i instanceof MeleeWeapon ? "t" + ((MeleeWeapon) i).tier : i instanceof Armor ? "t" + ((Armor) i).tier : "";
		String e = i instanceof Weapon && ((Weapon) i).enchantment != null ? "/" + ((Weapon) i).enchantment.getClass().getSimpleName()
				: i instanceof Armor && ((Armor) i).glyph != null ? "/" + ((Armor) i).glyph.getClass().getSimpleName() : "";
		return i.getClass().getSimpleName() + t + "+" + i.level() + (i.levelKnown ? "" : "?") + (i.cursed ? "!" : "") + e;
	}

	static void print() {
		Hero h = Dungeon.hero; if (h == null) return;
		int sou = 0, souUnk = 0, heal = 0, healUnk = 0, str = 0, food = 0, pots = 0;
		StringBuilder rest = new StringBuilder(), packGear = new StringBuilder();
		for (Item i : h.belongings) {
			if (i instanceof ScrollOfUpgrade) { if (((ScrollOfUpgrade) i).isKnown()) sou += i.quantity(); else souUnk += i.quantity(); }
			else if (i instanceof PotionOfHealing) { if (((PotionOfHealing) i).isKnown()) heal += i.quantity(); else healUnk += i.quantity(); }
			else if (i instanceof PotionOfStrength) str += i.quantity();
			else if (i instanceof Potion) pots += i.quantity();
			else if (i instanceof Food) food += (int) (((Food) i).energy * i.quantity());
			boolean worn = i == h.belongings.weapon() || i == h.belongings.armor();
			if (!worn && (i instanceof Ring || i instanceof Artifact || i instanceof Wand)) rest.append(' ').append(item(i)).append(i instanceof Wand ? "" : (i instanceof com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem && ((com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem) i).isEquipped(h) ? "(w)" : ""));
			if (!worn && (i instanceof MeleeWeapon || i instanceof Armor)) packGear.append(' ').append(item(i));
		}
		System.out.println("GEAR d" + Dungeon.depth + " lvl " + h.lvl + " str " + h.STR() + " hp " + h.HP + "/" + h.HT
				+ " wpn " + item(h.belongings.weapon()) + " arm " + item(h.belongings.armor())
				+ " sou " + sou + "+" + souUnk + "? heal " + heal + "+" + healUnk + "? strpot " + str + " pots " + pots + " food " + food
				+ " | other" + rest + " | pack" + packGear);
	}
}
