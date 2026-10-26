package pebxel;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * Obedience across the skills (agent DS; Thomas: "anticipate the human's wants, not push or insist"). Plays short bot
 * games (AssistBot, each set-up in a JVM of its own: the set-ups are read once a JVM) with OBEY=3: every third
 * suggestion that isn't a blow is passed over, plan B taken, as a player pressing Down then Select. Obey counts a
 * suggestion passed over that comes straight back as plan A (an item's use on the floor with nothing changed; a wait,
 * a skill's step or anything in a fight within 10 turns; a walk within 20) as insisting, by the skill that proposed it.
 * The set-ups bring the newer skills out: artifacts and rings (kits), abilities and foes (a head start to 16), the
 * ascent (the climb from 26), health, items, the economy, the armour plan and escape (mild games).
 * Fails if a newer skill insisted more than once in 20 passes over (and at least twice). Run from
 * spd/core/src/main/assets with the server's classpath; GAMES=n games per set-up (default 4).
 */
public final class ObeyTest {
	static final String[] SKILLS = {"artifacts", "artifacts!", "abilities", "urgent", "items", "items!", "econ", "foes", "ascent", "health", "escape", "hands", "quests", "bosses", "bosses+", "tactics", "pacing"};
	static final String[][] SETUPS = {
			{"mild", "MILD=1", "TALENTS=human", "KIT=random", "KITID=mix"},
			{"kits", "MILD=1", "TALENTS=human", "KIT=artifacts,rings,wands", "ARTIFACTS=random:2", "KITID=1"},
			{"head16", "MILD=1", "TALENTS=human", "HEAD=1", "STARTDEPTH=16"},
			{"climb", "MILD=1", "TALENTS=human", "HEAD=1", "STARTDEPTH=26"},
	};

	public static void main(String[] a) throws Exception {
		int games = Integer.parseInt(System.getenv().getOrDefault("GAMES", "4"));
		String cp = System.getProperty("java.class.path");
		Map<String, Integer> passes = new TreeMap<>(), insist = new TreeMap<>();
		Pattern op = Pattern.compile("passes \\{(.*?)\\} insist \\{(.*?)\\}");
		List<String> examples = new ArrayList<>();
		for (String[] su : SETUPS) {
			List<String> cmd = new ArrayList<>(Arrays.asList("java", "-Xmx600m", "-XX:+UseSerialGC", "-XX:+UnlockExperimentalVMOptions", "-XX:hashCode=3", "-XX:TieredStopAtLevel=1", "-cp", cp, "pebxel.AssistBot", "--games", "0", "" + games));
			ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
			Map<String, String> env = pb.environment();
			env.put("OBEY", "3"); env.put("SIMSEED", "1"); env.put("SEEDBASE", "" + (2000 + 100 * Arrays.asList(SETUPS).indexOf(su))); env.put("MAXTURNS", "6000");
			for (int i = 1; i < su.length; i++) { String[] kv = su[i].split("=", 2); env.put(kv[0], kv[1]); }
			long t0 = System.currentTimeMillis();
			Process p = pb.start();
			int n = 0;
			try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
				for (String l; (l = r.readLine()) != null; ) {
					if (l.startsWith("  INSIST") && examples.size() < 30) examples.add(su[0] + ": " + l.trim());
					if (!l.startsWith("OBEY")) continue;
					n++;
					Matcher m = op.matcher(l); if (!m.find()) continue;
					add(passes, m.group(1)); add(insist, m.group(2));
				}
			}
			p.waitFor();
			System.out.println("obey: " + su[0] + ": " + n + " games, " + (System.currentTimeMillis() - t0) / 1000 + " s");
		}
		boolean ok = true;
		System.out.println(String.format("%-14s %7s %7s", "skill", "passes", "insist"));
		for (String k : new TreeSet<>(passes.keySet())) {
			int ps = passes.get(k), in = insist.getOrDefault(k, 0);
			boolean newer = Arrays.asList(SKILLS).contains(k);
			boolean bad = newer && in >= 2 && in * 20 > ps;
			if (bad) ok = false;
			System.out.println(String.format("%-14s %7d %7d%s", k, ps, in, bad ? "   <- insists" : newer ? "" : "   (the explorer's own: Assist.demote)"));
		}
		for (String e : examples) System.out.println("  " + e);
		System.out.println(ok ? "PASS: obedience: the skills don't insist on what was passed over" : "FAIL: a skill insists");
		System.exit(ok ? 0 : 1);
	}

	static void add(Map<String, Integer> m, String list) {
		for (String kv : list.split(",")) { int e = kv.lastIndexOf('='); if (e > 0) m.merge(kv.substring(0, e).trim(), Integer.parseInt(kv.substring(e + 1).trim()), Integer::sum); }
	}
}
