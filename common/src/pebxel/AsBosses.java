package pebxel;

import pebxel.Assist.Target;

/** The Assistant's skill: each boss's own mechanics (Goo, Tengu, DM-300, the Dwarf King, Yog) (agent K3). */
final class AsBosses {
	private AsBosses() {}
	/** A tactical move for this turn on a boss's floor, or null (then the usual tactics). */
	static Target tactics() { return null; }
}
