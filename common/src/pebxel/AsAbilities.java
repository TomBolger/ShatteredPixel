package pebxel;

import pebxel.Assist.Target;

/** The Assistant's skill: talents that need decisions, subclass buttons, weapon and armour abilities, Cleric spells (agent K2). Same package as Assist, so its helpers are at hand. */
final class AsAbilities {
	private AsAbilities() {}
	/** A suggestion for the hero's hands this turn, or null. planA: the list's first entry so far (may be null). */
	static Target suggest(Target planA) { return null; }
}
