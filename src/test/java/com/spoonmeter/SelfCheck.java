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
		sharedItems(dropTable);
		sharedSlots(dropTable);
		perBossRates(dropTable);
		hidingCombinedPages(dropTable);
		hidingBosses(dropTable);
		effectiveRates(dropTable);
		accountScore(dropTable);
		rarityNotJustCount(dropTable);
		sharedAndCountedRolls(dropTable);

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
		check("which lands at the cursed end, not the spooned one",
			line.getRating() == LuckRating.CURSED || line.getRating() == LuckRating.HARD_CURSED
				|| line.getRating() == LuckRating.RNG_HATES_YOU);

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
		check("a named page shows its kill count", gauntlet.contains("1,498 kc"));
		check("the account line has no kill count", !SpoonSummary.chatLine(big, "").contains(" kc"));
		check("an unknown boss says so rather than lying",
			SpoonSummary.chatLine(big, "zamorak").contains("no rated page matching"));

	}

	private static void sharedItems(DropTable dropTable)
	{
		section("Items with several sources");

		Map<String, Double> chances = new HashMap<>();

		// Straight from a live account. Every Desert Treasure page lists the Virtus pieces and the
		// chromium ingot, and each shows the account-wide total rather than what that boss gave.
		CollectionLogPage vardorvis = dt2("Vardorvis", "Vardorvis kills", 252);
		vardorvis.getItems().put("Executioner's axe head", 1);

		CollectionLogPage duke = dt2("Duke Sucellus", "Duke Sucellus kills", 61);
		CollectionLogPage whisperer = dt2("The Whisperer", "Whisperer kills", 1);
		whisperer.getItems().put("Chromium ingot", 1); // page was read earlier, so its total is stale

		// minKc of 1 so the one-kill page is rated: that is the case being demonstrated.
		SpoonReport report = SpoonReport.build(Arrays.asList(vardorvis, duke, whisperer),
			dropTable, chances, 1);

		// One axe head, one robe top, two ingots. Counting per page would say nine.
		check("a shared item is counted once for the account", report.getObtained() == 4);

		SpoonReport.SourceLine vard = source(report, "Vardorvis");
		SpoonReport.SourceLine whisp = source(report, "The Whisperer");

		check("the page that earned it gets the credit", obtained(vard, "Virtus robe top") == 1);
		check("and the one-kill page gets none", obtained(whisp, "Virtus robe top") == 0);
		// The Whisperer page was read earlier and shows only one ingot; the pool must take the
		// freshest count, two, not the stale one. Which pages it lands on follows their rates.
		check("the stale ingot count does not win",
			obtained(vard, "Chromium ingot") + obtained(source(report, "Duke Sucellus"), "Chromium ingot")
				+ obtained(whisp, "Chromium ingot") == 2);

		check("so one kill no longer looks spooned", whisp.getObtained() == 0);
		System.out.println("      Whisperer: " + whisp.getObtained() + " vs "
			+ SpoonSummary.ONE_DP.format(whisp.getExpected()) + " expected, "
			+ whisp.getRating().getTitle());

		// Dryness is a question about every source at once, not this page alone.
		double vardDry = dryness(vard, "Virtus robe top");
		double whispDry = dryness(whisp, "Virtus robe top");
		check("dryness is pooled across the sources", Math.abs(vardDry - whispDry) < 1e-9);

		// Vorkath and the King Black Dragon both drop the draconic visage.
		CollectionLogPage vorkath = new CollectionLogPage("Vorkath");
		vorkath.getCounters().put("Vorkath kills", 2000);
		vorkath.getItems().put("Draconic visage", 1);

		CollectionLogPage kbd = new CollectionLogPage("King Black Dragon");
		kbd.getCounters().put("King Black Dragon kills", 50);
		kbd.getItems().put("Draconic visage", 1);

		SpoonReport dragons = SpoonReport.build(Arrays.asList(vorkath, kbd), dropTable, chances, 10);

		check("one visage across two bosses counts once",
			obtained(source(dragons, "Vorkath"), "Draconic visage")
				+ obtained(source(dragons, "King Black Dragon"), "Draconic visage") == 1);

		// One uncut onyx, shown in full on all three pages that can drop it.
		CollectionLogPage zulrah = onyx("Zulrah", "Zulrah kills", 169);
		CollectionLogPage skotizo = onyx("Skotizo", "Skotizo kills", 3);
		CollectionLogPage zalcano = onyx("Zalcano", "Zalcano kills", 47);

		SpoonReport onyxes = SpoonReport.build(Arrays.asList(zulrah, skotizo, zalcano),
			dropTable, chances, 1);

		int counted = obtained(source(onyxes, "Zulrah"), "Uncut onyx")
			+ obtained(source(onyxes, "Skotizo"), "Uncut onyx")
			+ obtained(source(onyxes, "Zalcano"), "Uncut onyx");

		check("one onyx across three bosses counts once", counted == 1);
		check("and goes to the likeliest source",
			obtained(source(onyxes, "Zulrah"), "Uncut onyx") == 1);
	}

	private static void hidingCombinedPages(DropTable dropTable)
	{
		section("Hiding combined boss pages");

		CollectionLogPage kings = new CollectionLogPage("Dagannoth Kings");
		kings.getCounters().put("Dagannoth Rex kills", 70);
		kings.getItems().put("Berserker ring", 1);

		CollectionLogPage zulrah = zulrah(1024, 2);
		Map<String, Double> chances = new HashMap<>();

		SpoonReport shown = SpoonReport.build(Arrays.asList(kings, zulrah), dropTable, chances, 10, false);
		SpoonReport hidden = SpoonReport.build(Arrays.asList(kings, zulrah), dropTable, chances, 10, true);

		check("shown by default", shown.getSources().size() == 2);
		check("hidden when asked", hidden.getSources().size() == 1);
		check("and it is the combined page that goes",
			hidden.getSources().get(0).getName().equals("Zulrah"));
		check("its uniques leave the account total too",
			hidden.getObtained() < shown.getObtained());
		check("pages read is still reported honestly", hidden.getPagesSeen() == 2);

		// A page covering one boss in several modes is not a combined page.
		check("the Gauntlet is not treated as combined",
			!dropTable.find("The Gauntlet").isCombined());
		check("nor are the raids", !dropTable.find("Chambers of Xeric").isCombined());
	}

	private static void effectiveRates(DropTable dropTable)
	{
		section("Effective rates, not per-roll ones");

		// Zulrah rolls its table twice per kill, so the per-roll figures the wiki also prints are
		// half the real chance. The giveaway is that the uniques must sum to the table rate.
		DropTable.Source zulrah = dropTable.find("Zulrah");
		double table = 1 / rateOf(zulrah, "Tanzanite fang") + 1 / rateOf(zulrah, "Magic fang")
			+ 1 / rateOf(zulrah, "Serpentine visage") + 1 / rateOf(zulrah, "Uncut onyx");

		near("Zulrah's uniques sum to its 1/128 table", 128.0, 1 / table, 0.01);
		near("so a specific one is 1/512", 512.0, rateOf(zulrah, "Tanzanite fang"), 0.01);
		near("and a mutagen 1/6554", 6554.0, rateOf(zulrah, "Magma mutagen"), 1.0);

		// Tertiaries roll once, so these were never doubled.
		near("the jar stays tertiary", 3000.0, rateOf(zulrah, "Jar of swamp"), 0.01);
		near("and so does the pet", 4000.0, rateOf(zulrah, "Pet snakeling"), 0.01);

		// Same shape of check for the hydra, whose components share one slot.
		DropTable.Source hydra = dropTable.find("Alchemical Hydra");
		double hydraTable = 1 / rateOf(hydra, "Brimstone ring piece") + 1 / rateOf(hydra, "Hydra tail")
			+ 1 / rateOf(hydra, "Hydra leather") + 1 / rateOf(hydra, "Hydra's claw");

		check("the hydra's uniques stay under its 1/87.6 table", 1 / hydraTable > 87.0);

		// A boss's commonest unique is the one that moves its expectation most, so getting it wrong
		// by a factor skews everybody who fights it. Zilyana's sword had been copied from the rates
		// of the pieces that are four times rarer.
		DropTable.Source zilyana = dropTable.find("Commander Zilyana");
		near("Zilyana's sword is the common one at 1/127", 127.0,
			rateOf(zilyana, "Saradomin sword"), 0.01);
		near("her light is 1/254", 254.0, rateOf(zilyana, "Saradomin's light"), 0.01);
		near("the crossbow stays rarer at 1/508", 508.0, rateOf(zilyana, "Armadyl crossbow"), 0.01);
		near("and so does the hilt", 508.0, rateOf(zilyana, "Saradomin hilt"), 0.01);

		// The wiki cross-checks itself here: a specific shard is 1/762, any shard 1/254.
		double shards = 1 / rateOf(zilyana, "Godsword shard 1")
			+ 1 / rateOf(zilyana, "Godsword shard 2") + 1 / rateOf(zilyana, "Godsword shard 3");
		near("any godsword shard is 1/254", 254.0, 1 / shards, 0.01);

		// Her stated 1/66 for any unique bar the shards. The wiki's own figures sit a few percent
		// apart from their sum, so this is held loosely enough to catch a factor and not a rounding.
		double zilyanaTable = 1 / rateOf(zilyana, "Saradomin sword")
			+ 1 / rateOf(zilyana, "Saradomin's light") + 1 / rateOf(zilyana, "Armadyl crossbow")
			+ 1 / rateOf(zilyana, "Saradomin hilt");
		check("her uniques land near the 1/66 she states (" + Math.round(1 / zilyanaTable) + ")",
			Math.abs(1 / zilyanaTable - 66.0) < 5.0);

		near("the kraken's tentacle is 1/400", 400.0,
			rateOf(dropTable.find("Kraken"), "Kraken tentacle"), 0.01);

		// The collection log records a quantity for the whole account but counts only boss kills, so
		// an item that also drops off something the log never counts arrives with a number that
		// means nothing. Occult necklaces come off ordinary smoke devils at 1/512, and a slayer task
		// supplies hundreds of those, so a page showing twenty-six of them says nothing about the
		// boss. It is left out for the same reason a bulk stack is.
		DropTable.Source thermy = dropTable.find("Thermonuclear smoke devil");

		CollectionLogPage devil = new CollectionLogPage("Thermonuclear smoke devil");
		devil.getCounters().put("Thermonuclear Smoke Devil kills", 1608);
		devil.getItems().put("Occult necklace", 26);
		devil.getItems().put("Smoke battlestaff", 3);

		Map<String, Double> none = new HashMap<>();
		SpoonReport.SourceLine def = only(devil, dropTable, none);

		// The reporter's shape: one occult off the boss and twenty-five off slayer tasks. Counting
		// the page's number would call five times dry five times spooned.
		check("the occult is listed", itemNamed(def, "Occult necklace") != null);
		check("but not counted", !itemNamed(def, "Occult necklace").isCounted());
		check("and is marked as something the log cannot count",
			itemNamed(def, "Occult necklace").isUncountable());
		check("so the twenty-six do not reach the total", def.getObtained() == 3);

		// The boss keeps enough to rate: three it alone drops, and a chainbody shared only with the
		// Kalphite Queen, which is counted and so pools properly.
		for (String only : new String[]{"Smoke battlestaff", "Jar of smoke", "Pet smoke devil"})
		{
			check(only + " is still counted", itemNamed(def, only).isCounted());
		}

		check("as is the chainbody, which the queen also drops",
			itemNamed(def, "Dragon chainbody").isCounted()
				&& names(dropTable.find("Kalphite Queen"))
					.contains(DropTable.normalise("Dragon chainbody")));

		// A player who knows their own came off the boss can say so from the right-click menu.
		Set<String> flipped = new HashSet<>(
			Collections.singletonList(DropTable.normalise("Occult necklace")));
		SpoonReport.SourceLine turnedOn = SpoonReport.build(Collections.singletonList(devil),
			dropTable, none, 10, false, Collections.emptySet(), flipped).getSources().get(0);

		check("turning it on counts it", itemNamed(turnedOn, "Occult necklace").isCounted());
		check("and the twenty-six then do reach the total", turnedOn.getObtained() == 29);
		check("which reads as spooned, as they asked for",
			turnedOn.getRatio() > def.getRatio());

		// The same switch silences an ordinary item for anyone who wants it gone.
		Set<String> silenced = new HashSet<>(
			Collections.singletonList(DropTable.normalise("Smoke battlestaff")));
		SpoonReport.SourceLine quieted = SpoonReport.build(Collections.singletonList(devil),
			dropTable, none, 10, false, Collections.emptySet(), silenced).getSources().get(0);

		check("an ordinary item can be turned off too",
			!itemNamed(quieted, "Smoke battlestaff").isCounted());
		check("and then it moves nothing", quieted.getObtained() == 0);
	}

	/**
	 * The account score averages the per-source scores. It used to be one percentile over the pooled
	 * totals, which saturated: every source landed in one Poisson whose band was proportionally tiny,
	 * so a shortfall shared by all of them pinned the score to zero and further pages only tightened
	 * it. These checks hold the averaging in place.
	 */
	private static void accountScore(DropTable dropTable)
	{
		section("The account score averages its sources");

		Map<String, Double> chances = new HashMap<>();

		// Both well below rate: 2 uniques against 8 expected, and 1 visage against roughly 1.4.
		CollectionLogPage zulrah = zulrah(1024, 2);
		CollectionLogPage vorkath = new CollectionLogPage("Vorkath");
		vorkath.getCounters().put("Vorkath kills", 7000);
		vorkath.getItems().put("Draconic visage", 1);

		SpoonReport report = SpoonReport.build(Arrays.asList(zulrah, vorkath), dropTable, chances, 10);
		check("both sources are rated", report.getSources().size() == 2);

		double mean = 0;
		for (SpoonReport.SourceLine line : report.getSources())
		{
			mean += line.getPercentile();
		}
		mean /= report.getSources().size();

		near("the score is the mean of its sources", mean, report.getPercentile(), 1.0e-9);

		// The old pooled figure, kept here as the thing being avoided rather than reproduced.
		double pooled = LuckCalculator.luckPercentile(report.getExpected(), report.getObtained());
		check("which beats pooling the totals", report.getPercentile() > pooled);

		// A spoon at one source lifts the account, where pooling would have drowned it in the rest.
		CollectionLogPage spooned = new CollectionLogPage("Vorkath");
		spooned.getCounters().put("Vorkath kills", 7000);
		spooned.getItems().put("Draconic visage", 5);

		SpoonReport lucky = SpoonReport.build(Arrays.asList(zulrah, spooned), dropTable, chances, 10);
		check("a spoon at one source lifts the account", lucky.getPercentile() > report.getPercentile());

		// The bug report was an account that sat at zero and would not move. Being somewhat below
		// rate has to stay somewhere on the scale: 7 uniques against 8 expected is ordinary dryness.
		SpoonReport ordinary = SpoonReport.build(Collections.singletonList(zulrah(1024, 7)),
			dropTable, chances, 10);

		check("ordinary dryness is not a zero", ordinary.getSpoonScore() > 5);
		check("but still reads dry", ordinary.getSpoonScore() < 50);

		// An average cannot leave the range it averages over. Pooling could and did, which is how a
		// run of unremarkable sources added up to a verdict worse than any of them.
		double worst = 1;
		double best = 0;
		for (SpoonReport.SourceLine line : report.getSources())
		{
			worst = Math.min(worst, line.getPercentile());
			best = Math.max(best, line.getPercentile());
		}

		check("the account never scores below its worst source", report.getPercentile() >= worst);
		check("nor above its best", report.getPercentile() <= best);
	}

	/**
	 * A count cannot tell a mega rare from a common unique. Three Tumeken's shadows and three
	 * Lightbearers are both "three purples" even though the shadow is five times rarer, and scoring
	 * the count alone called them identical.
	 */
	private static void rarityNotJustCount(DropTable dropTable)
	{
		section("Rarity, not just how many");

		Map<String, Double> chances = new HashMap<>();
		chances.put("toa", 0.05);

		SpoonReport.SourceLine shadows = only(toa(128, "Tumeken's shadow", 3), dropTable, chances);
		SpoonReport.SourceLine lesser = only(toa(128, "Lightbearer", 3), dropTable, chances);

		// Whatever the verdicts are, the counting figures have to be identical, which is the whole
		// problem: the old score could not separate these two accounts at all.
		check("both accounts hold three purples", shadows.getObtained() == 3
			&& lesser.getObtained() == 3);
		near("against the same expectation", shadows.getExpected(), lesser.getExpected(), 1.0e-9);

		check("three shadows beat three lightbearers", shadows.getPercentile() > lesser.getPercentile());
		check("and by a wide margin, not a rounding error",
			LuckRating.spoonScore(shadows.getPercentile())
				- LuckRating.spoonScore(lesser.getPercentile()) > 15);

		// The ratio is the number people read first, and counting alone made it identical for both
		// of these, which is what made the scoring look like guesswork from outside.
		near("the same count either way", lesser.getObtained(), shadows.getObtained(), 0);
		near("and so the same ratio by count", lesser.getCountRatio(), shadows.getCountRatio(), 1.0e-9);
		check("but not the same ratio by rarity", shadows.getRatio() > lesser.getRatio() + 0.3);
		check("the rarest is worth more than twice the commonest",
			shadows.getRatio() > 2 * lesser.getRatio());

		// It has to agree with the score, or the row argues with itself.
		check("ratio and score point the same way",
			(shadows.getRatio() > lesser.getRatio())
				== (shadows.getPercentile() > lesser.getPercentile()));

		// One mega rare against one common one, at the same kill count.
		SpoonReport.SourceLine oneShadow = only(toa(60, "Tumeken's shadow", 1), dropTable, chances);
		SpoonReport.SourceLine oneFang = only(toa(60, "Osmumten's fang", 1), dropTable, chances);
		check("one shadow beats one fang", oneShadow.getPercentile() > oneFang.getPercentile());

		// The plain count still has to move the verdict, or the score becomes a pet lottery.
		SpoonReport.SourceLine four = only(zulrah(1024, 4), dropTable, chances);
		SpoonReport.SourceLine eight = only(zulrah(1024, 8), dropTable, chances);
		SpoonReport.SourceLine twelve = only(zulrah(1024, 12), dropTable, chances);

		check("four uniques in 1024 is worse than eight", four.getPercentile() < eight.getPercentile());
		check("and eight is worse than twelve", eight.getPercentile() < twelve.getPercentile());
		check("twelve against eight expected reads lucky",
			LuckRating.spoonScore(twelve.getPercentile()) > 55);

		// Simulated, so it must not wander between panel refreshes.
		check("the score is the same on a second look",
			only(toa(128, "Tumeken's shadow", 3), dropTable, chances).getPercentile()
				== shadows.getPercentile());
	}

	private static CollectionLogPage tfaRaid(int kc, String item, int count)
	{
		CollectionLogPage page = new CollectionLogPage("The Fractured Archive");
		page.getCounters().put("Fractured Archive completions", kc);
		page.getItems().put(item, count);
		return page;
	}

	private static CollectionLogPage toa(int kc, String item, int count)
	{
		CollectionLogPage page = new CollectionLogPage("Tombs of Amascut");
		page.getCounters().put("Tombs of Amascut completions", kc);
		page.getItems().put(item, count);
		return page;
	}

	private static SpoonReport.SourceLine only(CollectionLogPage page, DropTable dropTable,
		Map<String, Double> chances)
	{
		return SpoonReport.build(Collections.singletonList(page), dropTable, chances, 10)
			.getSources().get(0);
	}

	/**
	 * Not every source is rolled exactly once per kill the collection log counts. A minigame may be
	 * rolled by its reward count, and the Hueycoatl splits a single roll between everyone fighting
	 * it, so the log records the kills but not the share of them that was yours.
	 */
	private static void sharedAndCountedRolls(DropTable dropTable)
	{
		section("Sources not rolled once per kill");

		// The minigames whose roll count the log does record must stay on those counters.
		DropTable.Source tempoross = dropTable.find("Tempoross");
		check("Tempoross counts reward permits, not kills",
			"Reward permits claimed".equals(tempoross.getVariants().get(0).getCounter()));
		check("Wintertodt counts crates, not kills",
			"Rewards claimed".equals(dropTable.find("Wintertodt").getVariants().get(0).getCounter()));

		// The Hueycoatl splits one roll by damage rather than rolling many times over.
		DropTable.Source huey = dropTable.find("The Hueycoatl");
		check("the Hueycoatl is scaled by contribution",
			"hueycontribution".equals(huey.getVariants().get(0).getRollsPerKcKey()));

		// Its printed uniques must still add up to the 1/18 unique table they are drawn from. The
		// hide is the missing third of it, left out because it always drops three at a time.
		double table = 1 / rateOf(huey, "Dragon hunter wand") + 1 / rateOf(huey, "Tome of earth")
			+ 11.0 / 315;
		near("its uniques and the hide sum to the 1/18 table", 18.0, 1 / table, 0.001);
		check("the hide itself stays out, dropping three at a time",
			!names(huey).contains(DropTable.normalise("Hueycoatl hide")));

		CollectionLogPage hueyPage = new CollectionLogPage("The Hueycoatl");
		hueyPage.getCounters().put("Hueycoatl kills", 500);
		hueyPage.getItems().put("Dragon hunter wand", 1);

		Map<String, Double> solo = new HashMap<>();
		solo.put("hueycontribution", 1.0);
		Map<String, Double> mass = new HashMap<>();
		mass.put("hueycontribution", 0.2);

		SpoonReport.SourceLine soloLine = only(hueyPage, dropTable, solo);
		SpoonReport.SourceLine massLine = only(hueyPage, dropTable, mass);

		near("a solo expects 500/105 wands", 500 / 105.0,
			itemExpected(soloLine, "Dragon hunter wand"), 0.001);
		near("a fifth of the damage expects a fifth of that", 100 / 105.0,
			itemExpected(massLine, "Dragon hunter wand"), 0.001);
		check("so the same log reads luckier in a mass",
			massLine.getPercentile() > soloLine.getPercentile());

		// A kill is 4,050 damage however many fight it, and the public area caps at twenty, so a
		// full mass is a twentieth of a solo's roll. That is what the setting defaults to.
		Map<String, Double> fullMass = new HashMap<>();
		fullMass.put("hueycontribution", 0.05);
		near("a full mass of twenty expects a twentieth of a solo", 500 / 105.0 / 20,
			itemExpected(only(hueyPage, dropTable, fullMass), "Dragon hunter wand"), 0.001);
		check("while the panel still shows the kills", massLine.getKc() == 500);

		// The share has to reach the panel, or a soloer left on the mass default sees twenty times
		// the drops they should with nothing on the row explaining why.
		near("the row knows a solo was one roll a kill", 1.0, soloLine.getRollsPerKc(), 1.0e-9);
		check("and says nothing about scaling", !soloLine.isScaled());
		near("a fifth of the damage is a fifth of a roll", 0.2, massLine.getRollsPerKc(), 1.0e-9);
		check("which the row does flag", massLine.isScaled());

		// A source nobody splits must not grow a share label.
		check("an ordinary boss is never flagged as scaled",
			!only(zulrah(1024, 7), dropTable, solo).isScaled());

		// An awakened Desert Treasure boss rolls its unique table three times as often, and the log
		// counts a kill the same either way, so a grinder reads as spooned for drops they had three
		// times the chance at.
		DropTable.Source lev = dropTable.find("The Leviathan");
		CollectionLogPage levPage = new CollectionLogPage("The Leviathan");
		levPage.getCounters().put("Leviathan kills", 3000);
		levPage.getItems().put("Venator vestige", 4);

		Map<String, Double> normal = new HashMap<>();
		normal.put("dt2awakened", 1.0);
		Map<String, Double> awakened = new HashMap<>();
		awakened.put("dt2awakened", 3.0);

		near("3000 normal kills expect 3000/768 vestiges", 3000 / 768.0,
			itemExpected(only(levPage, dropTable, normal), "Venator vestige"), 0.001);
		near("awakened expects three times that", 3 * 3000 / 768.0,
			itemExpected(only(levPage, dropTable, awakened), "Venator vestige"), 0.001);

		// The orb and the pet are rolled outside that table and must not move with it.
		near("the orb does not scale with it",
			itemExpected(only(levPage, dropTable, normal), "Awakener's orb"),
			itemExpected(only(levPage, dropTable, awakened), "Awakener's orb"), 1.0e-9);
		near("nor does the pet",
			itemExpected(only(levPage, dropTable, normal), "Lil'viathan"),
			itemExpected(only(levPage, dropTable, awakened), "Lil'viathan"), 1.0e-9);

		// Unlike a damage share, an unset awakened figure means ordinary kills, not an unrated boss.
		check("leaving it unset still rates the boss",
			!SpoonReport.build(Collections.singletonList(levPage), dropTable, new HashMap<>(), 10)
				.isEmpty());
		near("and reads as normal kills", 3000 / 768.0,
			itemExpected(only(levPage, dropTable, new HashMap<>()), "Venator vestige"), 0.001);

		// All four scale, and the within-table shares are unchanged, so each stays a third of its
		// awakened self.
		for (String boss : new String[]{"Vardorvis", "Duke Sucellus", "The Leviathan", "The Whisperer"})
		{
			DropTable.Source dt2 = dropTable.find(boss);
			boolean scales = false;
			boolean orbLeftAlone = true;

			for (DropTable.Item item : dt2.getItems())
			{
				boolean tagged = "dt2awakened".equals(item.getRollsPerKcKey());
				String name = DropTable.normalise(item.getName());

				if (tagged)
				{
					scales = true;
				}

				if (tagged && (name.contains("orb") || name.contains("pet")))
				{
					orbLeftAlone = false;
				}
			}

			check(boss + " scales when awakened", scales);
			check(boss + " leaves its orb alone", orbLeftAlone);
		}

		// The Fractured Archive: its unique table is a weighted pick, so the weights must account
		// for the whole table or every rate inside it is wrong.
		DropTable.Source tfa = dropTable.find("The Fractured Archive");
		check("the Fractured Archive is rated", tfa != null);
		check("and answers to TFA", dropTable.find("TFA") != null);

		Map<String, Double> tfaChance = new HashMap<>();
		tfaChance.put("tfa", 0.08);

		CollectionLogPage tfaPage = new CollectionLogPage("The Fractured Archive");
		tfaPage.getCounters().put("Fractured Archive completions", 60);
		tfaPage.getItems().put("Rondache", 2);

		SpoonReport.SourceLine tfaLine = only(tfaPage, dropTable, tfaChance);

		// The rondache is five fifteenths of the unique table.
		near("a rondache is 5/15 of the unique table", 60 * 0.08 * 5 / 15.0,
			itemExpected(tfaLine, "Rondache"), 0.001);
		// Sixty trios expect nearly five uniques, so two is short on count, and the rondache being
		// the commonest thing in the table makes it shorter still once rarity is weighed.
		check("two rondaches is short of what sixty raids owe", tfaLine.getCountRatio() < 1.0);
		check("and worth less again, being the commonest",
			tfaLine.getRatio() < tfaLine.getCountRatio());
		check("where two obligators would be worth more",
			only(tfaRaid(60, "Obligator", 2), dropTable, tfaChance).getRatio() > tfaLine.getRatio());

		// Milestone and bulk items would wreck it the way granite dust would.
		Set<String> tfaNames = names(tfa);
		for (String out : new String[]{"Living record (tier 1)", "Ascension shards",
			"Archivist's folio", "Fragment of living earth"})
		{
			check(out + " stays out of the rating", !tfaNames.contains(DropTable.normalise(out)));
		}

		// Nobody should be scored against a share they have not set.
		Map<String, Double> none = new HashMap<>();
		none.put("hueycontribution", 0.0);
		check("no share leaves the boss unrated",
			SpoonReport.build(Collections.singletonList(hueyPage), dropTable, none, 10).isEmpty());
		check("and so does leaving it unset",
			SpoonReport.build(Collections.singletonList(hueyPage), dropTable, new HashMap<>(), 10)
				.isEmpty());

	}

	private static SpoonReport.ItemLine itemNamed(SpoonReport.SourceLine line, String item)
	{
		for (SpoonReport.ItemLine candidate : line.getItems())
		{
			if (DropTable.normalise(candidate.getName()).equals(DropTable.normalise(item)))
			{
				return candidate;
			}
		}

		return null;
	}

	private static Set<String> names(DropTable.Source source)
	{
		Set<String> out = new HashSet<>();

		for (DropTable.Item item : source.getItems())
		{
			out.add(DropTable.normalise(item.getName()));
		}

		return out;
	}

	private static double itemExpected(SpoonReport.SourceLine line, String item)
	{
		for (SpoonReport.ItemLine candidate : line.getItems())
		{
			if (DropTable.normalise(candidate.getName()).equals(DropTable.normalise(item)))
			{
				return candidate.getExpected();
			}
		}

		throw new AssertionError("no such item on the line: " + item);
	}

	private static void hidingBosses(DropTable dropTable)
	{
		section("Hiding individual bosses");

		CollectionLogPage zulrah = zulrah(1024, 2);
		CollectionLogPage vorkath = new CollectionLogPage("Vorkath");
		vorkath.getCounters().put("Vorkath kills", 1000);
		vorkath.getItems().put("Draconic visage", 1);

		Map<String, Double> chances = new HashMap<>();
		SpoonReport all = SpoonReport.build(Arrays.asList(zulrah, vorkath), dropTable, chances, 10);

		Set<String> hidden = new HashSet<>(Collections.singletonList(DropTable.normalise("Zulrah")));
		SpoonReport some = SpoonReport.build(Arrays.asList(zulrah, vorkath), dropTable, chances, 10,
			false, hidden);

		check("both rated to begin with", all.getSources().size() == 2);
		check("the hidden one is gone", some.getSources().size() == 1);
		check("and the other one stays", some.getSources().get(0).getName().equals("Vorkath"));
		check("its uniques leave the total", some.getObtained() < all.getObtained());

		// The point of hiding: a boss you have dropped must stop moving the verdict, so its
		// expectation has to leave the total as well, not just its row leave the list.
		check("and so does its expectation", some.getExpected() < all.getExpected());
		check("which moves the score", some.getSpoonScore() != all.getSpoonScore());
		check("upward, having dropped a dry page", some.getSpoonScore() > all.getSpoonScore());
		check("pages read is unchanged", some.getPagesSeen() == 2);

		// Stored names are matched loosely, so the config does not need exact spelling.
		Set<String> loose = new HashSet<>(Collections.singletonList(DropTable.normalise("zulrah")));
		check("matching ignores case",
			SpoonReport.build(Arrays.asList(zulrah, vorkath), dropTable, chances, 10, false, loose)
				.getSources().size() == 1);

		check("hiding nothing changes nothing",
			SpoonReport.build(Arrays.asList(zulrah, vorkath), dropTable, chances, 10, false,
				Collections.emptySet()).getSources().size() == 2);
	}

	private static void perBossRates(DropTable dropTable)
	{
		section("Rates that differ between bosses");

		// The four Desert Treasure bosses do not share a rate card: each has its own unique table,
		// and every unique scales with it. Copying one boss's rates onto the others made the orb
		// look 1.7x rarer at Duke and 2.3x rarer at the Whisperer than it is.
		double[][] expected = {
			// unique table, vestige/weapon, each virtus, orb
			{136, 1088, 3264, 80.6},
			{90, 720, 2160, 48.5},
			{96, 768, 2304, 53.6},
			{64, 512, 1536, 34.5},
		};
		String[] names = {"Vardorvis", "Duke Sucellus", "The Leviathan", "The Whisperer"};
		String[] vestiges = {"Ultor vestige", "Magus vestige", "Venator vestige", "Bellator vestige"};

		boolean ok = true;

		for (int i = 0; i < names.length; i++)
		{
			DropTable.Source source = dropTable.find(names[i]);
			ok &= Math.abs(rateOf(source, vestiges[i]) - expected[i][1]) < 0.01;
			ok &= Math.abs(rateOf(source, "Virtus mask") - expected[i][2]) < 0.01;
			ok &= Math.abs(rateOf(source, "Awakener's orb") - expected[i][3]) < 0.01;

			// The wiki's own structure: the vestige is the table rate over eight, virtus over 24.
			ok &= Math.abs(rateOf(source, vestiges[i]) - expected[i][0] * 8) < 0.01;
			ok &= Math.abs(rateOf(source, "Virtus mask") - expected[i][0] * 24) < 0.01;
		}

		check("every Desert Treasure boss carries its own rates", ok);
		check("the orb is not the same at all four",
			rateOf(dropTable.find("The Whisperer"), "Awakener's orb")
				!= rateOf(dropTable.find("Vardorvis"), "Awakener's orb"));
	}

	private static double rateOf(DropTable.Source source, String item)
	{
		for (DropTable.Variant variant : source.getVariants())
		{
			for (DropTable.Item candidate : variant.getItems())
			{
				if (DropTable.normalise(candidate.getName()).equals(DropTable.normalise(item)))
				{
					return candidate.rate == null ? -1 : candidate.rate;
				}
			}
		}

		return -1;
	}

	private static void sharedSlots(DropTable dropTable)
	{
		section("Items sharing one drop slot");

		// The brimstone ring pieces come from a single 1/181.1 slot, handed out eye, fang, heart by
		// duplicate protection. Three separate entries would treble the real rate.
		CollectionLogPage hydra = new CollectionLogPage("Alchemical Hydra");
		hydra.getCounters().put("Alchemical Hydra kills", 1811);
		hydra.getItems().put("Hydra's eye", 1);
		hydra.getItems().put("Hydra's fang", 1);

		SpoonReport report = SpoonReport.build(Collections.singletonList(hydra),
			dropTable, new HashMap<>(), 10);
		SpoonReport.SourceLine line = report.getSources().get(0);

		double pieces = 0;
		int owned = 0;

		for (SpoonReport.ItemLine item : line.getItems())
		{
			if (item.getName().contains("Brimstone"))
			{
				pieces = item.getExpected();
				owned = item.getObtained();
			}
		}

		near("1811 kills expects ten ring pieces, not thirty", 10.0, pieces, 0.01);
		check("both pieces held count toward the one slot", owned == 2);
		// Hydra's claw is a separate drop and stays its own row; only the ring pieces are grouped.
		check("the three pieces are one row, not three",
			line.getItems().stream().filter(i -> i.getName().equals("Hydra's eye")
				|| i.getName().equals("Hydra's fang") || i.getName().equals("Hydra's heart")).count() == 0);
		check("and the claw is still its own row",
			line.getItems().stream().anyMatch(i -> i.getName().equals("Hydra's claw")));
	}

	private static CollectionLogPage onyx(String name, String counter, int kc)
	{
		CollectionLogPage page = new CollectionLogPage(name);
		page.getCounters().put(counter, kc);
		page.getItems().put("Uncut onyx", 1);
		return page;
	}

	private static CollectionLogPage dt2(String name, String counter, int kc)
	{
		CollectionLogPage page = new CollectionLogPage(name);
		page.getCounters().put(counter, kc);
		page.getItems().put("Virtus robe top", 1);
		page.getItems().put("Chromium ingot", 2);
		return page;
	}

	private static SpoonReport.SourceLine source(SpoonReport report, String name)
	{
		for (SpoonReport.SourceLine line : report.getSources())
		{
			if (line.getName().equals(name))
			{
				return line;
			}
		}

		throw new IllegalStateException(name + " was not rated");
	}

	private static int obtained(SpoonReport.SourceLine line, String item)
	{
		for (SpoonReport.ItemLine candidate : line.getItems())
		{
			if (candidate.getName().equals(item))
			{
				return candidate.getObtained();
			}
		}

		return 0;
	}

	private static double dryness(SpoonReport.SourceLine line, String item)
	{
		for (SpoonReport.ItemLine candidate : line.getItems())
		{
			if (candidate.getName().equals(item))
			{
				return candidate.getDryness();
			}
		}

		return 0;
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
		near("Tanzanite fang is 1/512", 1.0 / 512, probabilityOf(zulrah, "Tanzanite fang", 0), 1e-12);

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
		near("1024 Zulrah expects ~8.9 uniques", 8.91, cursed.getExpected(), 0.05);
		check("1 unique in 1024 reads as cursed", cursed.getPercentile() < 0.1);
		check("and lands at the bottom of the scale", cursed.getRating() == LuckRating.RNG_HATES_YOU);

		SpoonReport spooned = SpoonReport.build(Collections.singletonList(zulrah(1024, 16)), dropTable, chances, 10);
		check("16 uniques in 1024 reads as spooned", spooned.getPercentile() > 0.9);

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
		check("and it is one of the 1/512 drops", driest != null && driest.getChance() >= 1.0 / 512);

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
