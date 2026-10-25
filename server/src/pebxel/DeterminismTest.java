package pebxel;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.watabou.noosa.Game;
import com.badlogic.gdx.Gdx;
import java.io.*;
import java.util.*;

/**
 * A bot game must play the same alone in a fresh JVM as after other games in the same JVM, and whatever the JVM's
 * identity hashes are. Two JVMs side by side: A (the JVM's own hash codes) plays X1 then X2; B (-XX:hashCode=3, as
 * simbot runs) plays Y and Z first, then X1 3rd and X2 4th. Every decision of X1 and X2 (turn, game time, depth, hero
 * cell and HP, the pick, every random generator's state) must match line for line. Run from spd/core/src/main/assets:
 *   java -cp "$H/build/server:$H/build/spd:$H/lib/*" pebxel.DeterminismTest
 * Env: TURNS (default 800 a game: X1 builds depth 3, where SecretLaboratoryRoom drew by hash order); the bot's set-up defaults to BF's batches (SIMSEED=1 MILD=1 TALENTS=human
 * KIT=random KITID=mix), each overridable. A few seconds. Exit 0 when they match, 1 at the first difference.
 */
public class DeterminismTest {
	/** Set while a game is traced: one line a decision, from AssistBot.play (this class is left out of Statics, so it
	 *  stays set through play()'s putting our statics back). */
	static List<String> trace;
	static final String X1 = "2:1014", X2 = "3:1013", Y = "1:1011", Z = "0:1012";

	public static void main(String[] a) throws Exception {
		if (System.getenv("DT_CHILD") != null) { child(a); return; }
		int turns = Integer.parseInt(System.getenv().getOrDefault("TURNS", "800"));
		long t0 = System.nanoTime();
		Process pa = spawn(null, turns, X1, X2), pb = spawn("-XX:hashCode=3", turns, Y, Z, X1, X2);
		Map<String, List<String>> ta = read(pa, "A"), tb = read(pb, "B");
		int ra = pa.waitFor(), rb = pb.waitFor();
		boolean ok = ra == 0 && rb == 0;
		if (!ok) System.out.println("FAIL: a JVM ended with " + ra + " / " + rb);
		for (String g : new String[]{X1, X2}) {
			List<String> x = ta.get(g), y = tb.get(g);
			if (x == null || y == null || x.size() < 20) { System.out.println("FAIL " + g + ": no trace (" + (x == null ? "-" : x.size()) + " / " + (y == null ? "-" : y.size()) + " decisions)"); ok = false; continue; }
			int n = Math.min(x.size(), y.size()), i = 0;
			while (i < n && x.get(i).equals(y.get(i))) i++;
			if (i == n && x.size() == y.size()) { System.out.println("ok " + g + " (class:seed): " + n + " decisions alike, alone and as game " + (g.equals(X1) ? 3 : 4) + " of a JVM with other hash codes"); continue; }
			ok = false;
			System.out.println("FAIL " + g + ": decision " + i + " of " + x.size() + " / " + y.size() + " differs");
			for (int k = Math.max(0, i - 2); k <= i && k < n; k++) System.out.println("  alone " + x.get(k) + "\n  batch " + y.get(k));
			if (i == n) System.out.println("  (one game went on: " + (x.size() > n ? "alone " + x.get(n) : "batch " + y.get(n)) + ")");
		}
		System.out.printf("%s in %.1f s%n", ok ? "PASS" : "FAIL", (System.nanoTime() - t0) / 1e9);
		System.exit(ok ? 0 : 1);
	}

	static Process spawn(String hash, int turns, String... games) throws IOException {
		List<String> cmd = new ArrayList<>();
		cmd.add(ProcessHandle.current().info().command().orElse("java"));
		cmd.addAll(Arrays.asList("-Xmx600m", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1"));
		if (hash != null) { cmd.add("-XX:+UnlockExperimentalVMOptions"); cmd.add(hash); }
		cmd.addAll(Arrays.asList("-cp", System.getProperty("java.class.path"), "pebxel.DeterminismTest", "" + turns));
		cmd.addAll(Arrays.asList(games));
		ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
		Map<String, String> env = pb.environment();
		env.put("DT_CHILD", "1");
		for (String[] kv : new String[][]{{"SIMSEED", "1"}, {"FAST", "1"}, {"MILD", "1"}, {"TALENTS", "human"}, {"KIT", "random"}, {"KITID", "mix"}}) env.putIfAbsent(kv[0], kv[1]);
		env.remove("V"); env.remove("WATCH");
		env.put("SAVETAG", "-dt" + (hash == null ? "a" : "b"));
		return pb.start();
	}

	/** The child's "DT game line" lines, by game; anything else it prints is kept for a failure's report. */
	static Map<String, List<String>> read(Process p, String who) throws IOException {
		Map<String, List<String>> out = new LinkedHashMap<>();
		ArrayDeque<String> tail = new ArrayDeque<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
			for (String l; (l = r.readLine()) != null; ) {
				if (l.startsWith("DT ")) { int s = l.indexOf(' ', 3); out.computeIfAbsent(l.substring(3, s), k -> new ArrayList<>()).add(l.substring(s + 1)); }
				else { if (l.startsWith("RESULT")) System.out.println("  " + who + ": " + l); tail.addLast(l); if (tail.size() > 30) tail.removeFirst(); }
			}
		}
		if (out.isEmpty()) { System.out.println("JVM " + who + " printed no trace; its last lines:"); for (String l : tail) System.out.println("  " + l); }
		return out;
	}

	/** In the child JVM: the games one after another, as AssistBot --games plays them. */
	static void child(String[] a) throws Exception {
		int turns = Integer.parseInt(a[0]);
		PrintStream real = System.out;
		AssistBot.init();
		for (int i = 1; i < a.length; i++) {
			String[] cs = a[i].split(":");
			trace = new ArrayList<>();
			AssistBot.play(HeroClass.values()[Integer.parseInt(cs[0])], Long.parseLong(cs[1]), turns);
			for (String l : trace) real.println("DT " + a[i] + " " + l);
			trace = null;
			Game.switchScene(com.watabou.noosa.Scene.class); for (int k = 0; k < 3; k++) AssistBot.step();
			try { Gdx.files.external(AssistBot.saveDir).deleteDirectory(); Gdx.files.local(AssistBot.saveDir).deleteDirectory(); } catch (Throwable e) { }
		}
		try { Gdx.files.local(AssistBot.dir0).deleteDirectory(); } catch (Throwable e) { }
		real.flush();
		System.exit(0);
	}
}
