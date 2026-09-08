package com.spoonmeter;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The same checks as the JUnit tests, with no JUnit.
 *
 * <p>Building this plugin without Gradle means there is no test framework on the classpath, so this
 * runs the maths, the drop table and the rating pipeline from a plain {@code main} and reports what
 * failed. Run it with {@code build.ps1 -Verify}.
 */
public class SelfCheck
{
	private static int checks;
	private static int failures;

	public static void main(String[] args) throws Exception
	{
		DropTable dropTable = DropTable.load(new Gson(), null);

		poissonMaths();
		headerParsing();
		dropTableIntegrity(dropTable);
		ratingPipeline(dropTable);
		splitPages(dropTable);
		chatLines(dropTable);

		System.out.println();
		System.out.println(checks + " checks, " + failures + " failed");
		System.exit(failures == 0 ? 0 : 1);
	}

	private static void poissonMaths()
	{
		section("Poisson / incomplete gamma");

		near("pmf(0, 1)", Math.exp(-1), LuckCalculator.poissonPmf(0, 1), 1e-9);
		near("pmf(4, 4)", 0.19536681, LuckCalculator.poissonPmf(4, 4), 1e-6);
		near("pmf(10, 10)", 0.12511003, LuckCalculator.poissonPmf(10, 10), 1e-6);
		near("cdf(0, 1)", 0.36787944, LuckCalculator.poissonCdf(0, 1), 1e-6);
		near("cdf(3, 4)", 0.43347012, LuckCalculator.poissonCdf(3, 4), 1e-6);
		near("cdf(10, 10)", 0.58303975, LuckCalculator.poissonCdf(10, 10), 1e-6);

		// The continued fraction branch has to hold up where a naive sum would not.
		near("cdf(5000, 5000)", 0.5, LuckCalculator.poissonCdf(5000, 5000), 0.01);
		check("cdf(4000, 5000) is tiny", LuckCalculator.poissonCdf(4000, 5000) < 1e-6);
		check("cdf(6000, 5000) is ~1", LuckCalculator.poissonCdf(6000, 5000) > 0.999999);

		near("average account sits near 0.5", 0.5, LuckCalculator.luckPercentile(30.0, 30), 0.05);
		near("no data is 0.5", 0.5, LuckCalculator.luckPercentile(0, 0), 1e-9);
		check("0 of 10 expected is bottom 0.1%", LuckCalculator.luckPercentile(10.0, 0) < 0.001);
		check("25 of 10 expected is top 1%", LuckCalculator.luckPercentile(10.0, 25) > 0.99);

		double previous = -1;
		boolean monotonic = true;

		for (int obtained = 0; obtained <= 40; obtained++)
		{
			double percentile = LuckCalculator.luckPercentile(20.0, obtained);
			monotonic &= percentile > previous;
			previous = percentile;
		}

		check("more uniques is always luckier", monotonic);

		near("dryness(1/100, 100)", 0.63396766, LuckCalculator.dryness(0.01, 100), 1e-6);
		near("dryness(1/1024, 10240)", 0.99995482, LuckCalculator.dryness(1.0 / 1024, 10240), 1e-6);

		check("0.999 is spooned", LuckRating.fromPercentile(0.999) == LuckRating.SPOONED_BY_THE_GODS);
		check("0.5 is average", LuckRating.fromPercentile(0.5) == LuckRating.AVERAGE);
		check("0.002 is a war crime", LuckRating.fromPercentile(0.002) == LuckRating.RNG_HATES_YOU);
	}

	private static void headerParsing()
	{
		section("Collection log header parsing");

		Map<String, Integer> counters = new LinkedHashMap<>();

		// "Personal Best: 9:51" read as a counter named "Personal Best: 9" worth 51 beat the real
		// kill count on any page with fewer kills than that - Vorkath at 10 kills scored as 29.
		check("personal best times are not counters",
			!CollectionLogParser.addCounter(counters, "Personal Best: 9:51"));
		check("nor corrupted personal bests",
			!CollectionLogParser.addCounter(counters, "Personal Best Corrupted: 7:56"));
		check("real counters still parse",
			CollectionLogParser.addCounter(counters, "Corrupted Gauntlet completion count: 1,487"));
		check("with thousands separators", counters.get("Corrupted Gauntlet completion count") == 1487);
		check("and nothing else got in", counters.size() == 1);
	}

	private static void splitPages(DropTable dropTable)
	{
		section("Pages with several counters");

		Map<String, Double> chances = new HashMap<>();
		chances.put("cox", 0.035);
		chances.put("coxcm", 0.05);
		chances.put("tob", 0.037);
		chances.put("tobhm", 0.044);
		chances.put("toa", 0.12);
		chances.put("toaexpert", 0.15);

		CollectionLogPage gauntlet = gauntletPage();

		SpoonReport report = SpoonReport.build(Collections.singletonList(gauntlet), dropTable, chances, 10);
		SpoonReport.SourceLine line = report.getSources().get(0);

		near("both Gauntlet counters are added up", 1498, line.getKc(), 0);
		near("and both rate tables are blended", 65.25, line.getExpected(), 0.05);
		check("the cape is not counted as a drop", line.getObtained() == 52);
		check("52 uniques against 65 expected reads as dry", line.getPercentile() < 0.10);
		check("which is cursed, not spooned", line.getRating() == LuckRating.CURSED
			|| line.getRating() == LuckRating.HARD_CURSED);

		CollectionLogPage kings = new CollectionLogPage("Dagannoth Kings");
		kings.getCounters().put("Dagannoth Rex kills", 70);
		kings.getCounters().put("Dagannoth Prime kills", 0);
		kings.getCounters().put("Dagannoth Supreme kills", 0);

		SpoonReport dks = SpoonReport.build(Collections.singletonList(kings), dropTable, chances, 10);

		check("Dagannoth Kings is one page, not three", !dks.isEmpty());
		near("and only the king you fought counts", 1.65, dks.getExpected(), 0.01);

		// The bracketed part is the only thing separating these two counters.
		CollectionLogPage cox = new CollectionLogPage("Chambers of Xeric");
		cox.getCounters().put("Chambers of Xeric completions", 44);
		cox.getCounters().put("Chambers of Xeric (CM) completions", 0);

		SpoonReport raids = SpoonReport.build(Collections.singletonList(cox), dropTable, chances, 10);

		near("CM completions do not leak into normal mode", 44 * 0.035, raids.getExpected(), 1e-9);
	}

	private static void chatLines(DropTable dropTable)
	{
		section("Chat line");

		Map<String, Double> chances = new HashMap<>();
		chances.put("cox", 0.035);
		chances.put("coxcm", 0.05);
		chances.put("tob", 0.037);
		chances.put("tobhm", 0.044);
		chances.put("toa", 0.12);
		chances.put("toaexpert", 0.15);

		SpoonReport small = SpoonReport.build(Collections.singletonList(zulrah(1024, 1)),
			dropTable, chances, 10);
		String smallLine = SpoonSummary.chatLine(small, "");
		System.out.println("      " + smallLine);
		check("a modest account fits in a chat message", smallLine.length() <= SpoonSummary.MAX_CHAT_LENGTH);

		// Longest rating name plus four digit counts is where the full form stops fitting.
		SpoonReport big = SpoonReport.build(Arrays.asList(zulrah(200000, 2000), gauntletPage()),
			dropTable, chances, 10);
		String bigLine = SpoonSummary.chatLine(big, "");
		System.out.println("      " + bigLine);
		check("and so does a huge one, by dropping detail", bigLine.length() <= SpoonSummary.MAX_CHAT_LENGTH);
		check("without truncating mid-word", !bigLine.endsWith("expected") && !bigLine.endsWith("vs"));

		String gauntlet = SpoonSummary.chatLine(big, "gauntlet");
		System.out.println("      " + gauntlet);
		check("a boss argument rates that page", gauntlet.contains("Gauntlet"));
		check("and fits too", gauntlet.length() <= SpoonSummary.MAX_CHAT_LENGTH);
		check("an unknown boss says so rather than lying",
			SpoonSummary.chatLine(big, "zamorak").contains("no rated page matching"));

		// The panel prefixes with "/" so Enter sends to clan chat; that costs one character.
		String clan = "/" + SpoonSummary.chatLine(big, "", SpoonSummary.MAX_CHAT_LENGTH - 1);
		System.out.println("      " + clan);
		check("the clan-prefixed line still fits", clan.length() <= SpoonSummary.MAX_CHAT_LENGTH);
	}

	private static void dropTableIntegrity(DropTable dropTable)
	{
		section("Drop table");

		check("loaded more than 20 sources", dropTable.getSources().size() > 20);
		check("finds Zulrah", dropTable.find("Zulrah") != null);
		check("finds KREE'ARRA", dropTable.find("KREE'ARRA") != null);
		check("finds Nightmare without 'The'", dropTable.find("Nightmare") != null);
		check("does not invent sources", dropTable.find("Some Boss That Does Not Exist") == null);
		check("strips (uncharged)", "sanguinesti staff".equals(DropTable.normalise("Sanguinesti staff (uncharged)")));

		int items = 0;
		boolean allUsable = true;
		boolean weightsSum = true;

		for (DropTable.Source source : dropTable.getSources())
		{
			for (DropTable.Variant variant : source.getVariants())
			{
				double weightSum = 0;

				for (DropTable.Item item : variant.getItems())
				{
					double p = DropTable.probability(variant, item, 0.05);
					items++;

					if (p <= 0 || p > 1)
					{
						allUsable = false;
						System.out.println("      bad rate: " + source.getName() + " / " + item.getName());
					}

					if (item.weight != null)
					{
						weightSum += item.weight;
					}
				}

				if (variant.totalWeight != null && Math.abs(weightSum - variant.totalWeight) > 1e-9)
				{
					weightsSum = false;
					System.out.println("      weights do not sum: " + source.getName());
				}
			}
		}

		check("every one of " + items + " items has a usable probability", allUsable);
		check("every weight set sums to its declared total", weightsSum);

		// The log reports items received, not drops, so anything that arrives in a stack scores once
		// per item. One 132-axe drop at Hydra read as 132 uniques and pinned the account at 100/100.
		Set<String> stackDrops = new HashSet<>(Arrays.asList(
			"dragon thrownaxe", "dragon knife", "dragon dart", "zulrahs scales", "araxyte venom sac",
			"key master teleport", "zulandra teleport", "spider cave teleport", "onyx bolts",
			"runite bolts", "death rune", "blood rune", "cannonball"));

		boolean noStackDrops = true;

		for (DropTable.Source source : dropTable.getSources())
		{
			for (DropTable.Item item : source.getItems())
			{
				if (stackDrops.contains(DropTable.normalise(item.getName())))
				{
					noStackDrops = false;
					System.out.println("      stack drop listed: " + source.getName() + " / " + item.getName());
				}
			}
		}

		check("no stack drops are rated as uniques", noStackDrops);

		DropTable.Source zulrah = dropTable.find("Zulrah");
		near("Tanzanite fang is 1/1024", 1.0 / 1024, probabilityOf(zulrah, "Tanzanite fang", 0), 1e-12);

		DropTable.Source cox = dropTable.find("Chambers of Xeric");
		near("Twisted bow is 2/60 of a purple", 0.035 * 2 / 60, probabilityOf(cox, "Twisted bow", 0.035), 1e-12);
	}

	private static void ratingPipeline(DropTable dropTable)
	{
		section("Rating");

		Map<String, Double> chances = new HashMap<>();
		chances.put("cox", 0.035);
		chances.put("tob", 0.037);
		chances.put("toa", 0.12);

		SpoonReport cursed = SpoonReport.build(Collections.singletonList(zulrah(1024, 1)), dropTable, chances, 10);
		near("1024 Zulrah expects ~4.75 uniques", 4.75, cursed.getExpected(), 0.05);
		check("1 unique in 1024 reads as cursed", cursed.getPercentile() < 0.1);
		check("and lands in Hard Cursed", cursed.getRating() == LuckRating.HARD_CURSED);

		SpoonReport spooned = SpoonReport.build(Collections.singletonList(zulrah(1024, 9)), dropTable, chances, 10);
		check("9 uniques in 1024 reads as spooned", spooned.getPercentile() > 0.9);

		check("5 kc is ignored", SpoonReport.build(Collections.singletonList(zulrah(5, 0)), dropTable, chances, 10).isEmpty());
		check("50 kc is rated", !SpoonReport.build(Collections.singletonList(zulrah(50, 0)), dropTable, chances, 10).isEmpty());

		CollectionLogPage unrated = new CollectionLogPage("Some Unrated Minigame");
		unrated.getCounters().put("Kill count", 500);
		SpoonReport unknown = SpoonReport.build(Collections.singletonList(unrated), dropTable, chances, 10);
		check("pages with no rates are skipped", unknown.isEmpty());
		check("but still counted as read", unknown.getPagesSeen() == 1);

		SpoonReport dry = SpoonReport.build(Collections.singletonList(zulrah(4096, 0)), dropTable, chances, 10);
		SpoonReport.ItemLine driest = dry.getSources().get(0).getDriestMissing();
		check("the driest missing item is called out", driest != null);
		check("and it is one of the 1/1024 drops", driest != null && driest.getChance() >= 1.0 / 1024);

		CollectionLogPage tob = new CollectionLogPage("Theatre of Blood");
		tob.getCounters().put("Theatre of Blood completions", 300);
		tob.getItems().put("Sanguinesti staff (uncharged)", 1);
		check("item names match the game's spelling",
			SpoonReport.build(Collections.singletonList(tob), dropTable, chances, 10).getObtained() == 1);

		CollectionLogPage counters = new CollectionLogPage("Chambers of Xeric");
		counters.getCounters().put("Chest opened", 250);
		counters.getCounters().put("Challenge mode chest opened", 3);
		check("the largest counter wins", counters.getPrimaryCount() == 250);
		check("and names itself", "Chest opened".equals(counters.getPrimaryCounterLabel()));

		System.out.println();
		System.out.println("  Sample verdict: " + SpoonSummary.oneLine(cursed));
	}

	/** Real numbers from a live account: 11 normal runs, 1,487 corrupted. */
	private static CollectionLogPage gauntletPage()
	{
		CollectionLogPage gauntlet = new CollectionLogPage("The Gauntlet");
		gauntlet.getCounters().put("Gauntlet completion count", 11);
		gauntlet.getCounters().put("Corrupted Gauntlet completion count", 1487);
		gauntlet.getItems().put("Crystal armour seed", 28);
		gauntlet.getItems().put("Crystal weapon seed", 23);
		gauntlet.getItems().put("Enhanced crystal weapon seed", 1);
		gauntlet.getItems().put("Gauntlet cape", 1);
		return gauntlet;
	}

	private static CollectionLogPage zulrah(int kc, int uniques)
	{
		CollectionLogPage page = new CollectionLogPage("Zulrah");
		page.setCounters(new LinkedHashMap<>(Collections.singletonMap("Kill count", kc)));

		String[] names = {"Tanzanite fang", "Magic fang", "Serpentine visage", "Uncut onyx"};
		Map<String, Integer> items = new LinkedHashMap<>();

		for (int i = 0; i < uniques; i++)
		{
			items.merge(names[i % names.length], 1, Integer::sum);
		}

		page.setItems(items);
		return page;
	}

	private static double probabilityOf(DropTable.Source source, String itemName, double uniqueChance)
	{
		for (DropTable.Variant variant : source.getVariants())
		{
			for (DropTable.Item item : variant.getItems())
			{
				if (DropTable.normalise(item.getName()).equals(DropTable.normalise(itemName)))
				{
					return DropTable.probability(variant, item, uniqueChance);
				}
			}
		}

		return Double.NaN;
	}

	private static void section(String name)
	{
		System.out.println();
		System.out.println("== " + name);
	}

	private static void check(String what, boolean ok)
	{
		checks++;

		if (!ok)
		{
			failures++;
		}

		System.out.println((ok ? "  pass  " : "  FAIL  ") + what);
	}

	private static void near(String what, double expected, double actual, double delta)
	{
		boolean ok = Math.abs(expected - actual) <= delta;
		check(what + " (" + actual + ")", ok);
	}
}
