package pebxel.bot;

/**
 * Small integer distributions for the bot's models: the game's own rolls (Random.NormalIntRange: the sum of two
 * uniform floats, floored; Random.IntRange: uniform), shifted, scaled, added together, and the one sum combat needs:
 * E[max(damage - armour, 0)]. Plain math, no game state: unit-tested by BotModelTest.
 *
 * A distribution is an array p[] over the values lo .. lo + p.length - 1.
 */
public final class Dist {
	public final int lo;
	public final double[] p;

	public Dist(int lo, double[] p) { this.lo = lo; this.p = p; }

	public static Dist point(int v) { return new Dist(v, new double[] { 1 }); }

	/** Random.NormalIntRange(min, max): min + (int)((Float() + Float()) * (max - min + 1) / 2). */
	public static Dist normal(int min, int max) {
		if (max <= min) return point(min);
		int n = max - min + 1; double[] p = new double[n];
		for (int k = 0; k < n; k++) p[k] = tri(2.0 * (k + 1) / n) - tri(2.0 * k / n);
		return new Dist(min, p);
	}
	private static double tri(double s) { return s <= 0 ? 0 : s >= 2 ? 1 : s <= 1 ? s * s / 2 : 1 - (2 - s) * (2 - s) / 2; }

	/** Random.IntRange(min, max): uniform. */
	public static Dist uniform(int min, int max) {
		if (max <= min) return point(min);
		int n = max - min + 1; double[] p = new double[n];
		for (int k = 0; k < n; k++) p[k] = 1.0 / n;
		return new Dist(min, p);
	}

	/** An empirical distribution from samples (the game's own roll, taken on a generator of the bot's own). */
	public static Dist of(int[] v, int n) {
		if (n <= 0) return point(0);
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int i = 0; i < n; i++) { lo = Math.min(lo, v[i]); hi = Math.max(hi, v[i]); }
		double[] p = new double[hi - lo + 1];
		for (int i = 0; i < n; i++) p[v[i] - lo] += 1.0 / n;
		return new Dist(lo, p);
	}

	public int hi() { return lo + p.length - 1; }

	public double mean() { double m = 0; for (int k = 0; k < p.length; k++) m += p[k] * (lo + k); return m; }

	/** X + Y (independent). */
	public Dist plus(Dist o) {
		double[] r = new double[p.length + o.p.length - 1];
		for (int i = 0; i < p.length; i++) if (p[i] != 0) for (int j = 0; j < o.p.length; j++) r[i + j] += p[i] * o.p[j];
		return new Dist(lo + o.lo, r);
	}

	public Dist shift(int d) { return new Dist(lo + d, p); }

	/** Math.round(X * f) (an augment's damage factor, a multiplier). */
	public Dist scale(double f) {
		if (f == 1) return this;
		int a = (int) Math.round(lo * f), b = (int) Math.round(hi() * f);
		int l2 = Math.min(a, b), h2 = Math.max(a, b);
		double[] r = new double[h2 - l2 + 1];
		for (int k = 0; k < p.length; k++) r[(int) Math.round((lo + k) * f) - l2] += p[k];
		return new Dist(l2, r);
	}

	/** max(X, m). */
	public Dist atLeast(int m) {
		if (lo >= m) return this;
		int h = Math.max(hi(), m); double[] r = new double[h - m + 1];
		for (int k = 0; k < p.length; k++) r[Math.max(lo + k, m) - m] += p[k];
		return new Dist(m, r);
	}

	/** max(X - Y, 0): the damage a blow does through armour (Char.attack: effectiveDamage - dr, floored at 0). */
	public Dist minus0(Dist y) {
		int h = Math.max(0, hi() - y.lo); double[] r = new double[h + 1];
		for (int i = 0; i < p.length; i++) if (p[i] != 0) for (int j = 0; j < y.p.length; j++) {
			int v = lo + i - (y.lo + j); r[v > 0 ? v : 0] += p[i] * y.p[j];
		}
		return new Dist(0, r);
	}

	/** A mixture: this with weight w, o with 1 - w. */
	public Dist mix(Dist o, double w) {
		int l = Math.min(lo, o.lo), h = Math.max(hi(), o.hi()); double[] r = new double[h - l + 1];
		for (int k = 0; k < p.length; k++) r[lo + k - l] += w * p[k];
		for (int k = 0; k < o.p.length; k++) r[o.lo + k - l] += (1 - w) * o.p[k];
		return new Dist(l, r);
	}

	/** A value drawn with u in [0, 1) (the bot's own random numbers, never the game's). */
	public int draw(double u) {
		double c = 0;
		for (int k = 0; k < p.length; k++) { c += p[k]; if (u < c) return lo + k; }
		return hi();
	}

	/** P(Random.Float(acc) >= Random.Float(def)): Char.hit with no buffs (the attacker's roll at least the defender's). */
	public static double hit(double acc, double def) {
		if (acc <= 0) return 0;
		if (def <= 0) return 1;
		return acc >= def ? 1 - def / (2 * acc) : acc / (2 * def);
	}

	@Override public String toString() { return String.format("[%d..%d mean %.2f]", lo, hi(), mean()); }
}
