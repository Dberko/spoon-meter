package com.spoonmeter;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class SpoonReportTest
{
	private DropTable dropTable;
	private Map<String, Double> uniqueChances;

	@Before
	public void setUp() throws IOException
	{
		dropTable = DropTable.load(new Gson(), null);
		uniqueChances = new HashMap<>();
		uniqueChances.put("cox", 0.035);
		uniqueChances.put("tob", 0.037);
		uniqueChances.put("toa", 0.12);
	}

	@Test
	public void oneUniqueInAThousandZulrahIsCursed()
	{
		SpoonReport report = build(zulrah(1024, 1), 10);

		assertEquals(1, report.getObtained());
		assertEquals(4.75, report.getExpected(), 0.05);
		assertTrue("should read as cursed, was " + report.getPercentile(), report.getPercentile() < 0.1);
		assertEquals(LuckRating.HARD_CURSED, report.getRating());
	}

	@Test
	public void nineUniquesInAThousandZulrahIsSpooned()
	{
		SpoonReport report = build(zulrah(1024, 9), 10);

		assertTrue("should read as spooned, was " + report.getPercentile(), report.getPercentile() > 0.9);
	}

	@Test
	public void lowKcPagesAreIgnored()
	{
		assertTrue(build(zulrah(5, 0), 10).isEmpty());
		assertTrue(!build(zulrah(50, 0), 10).isEmpty());
	}

	@Test
	public void pagesWithoutRatesAreSkippedButStillCounted()
	{
		CollectionLogPage page = new CollectionLogPage("Some Unrated Minigame");
		page.getCounters().put("Kill count", 500);

		SpoonReport report = SpoonReport.build(Collections.singletonList(page), dropTable, uniqueChances, 10);

		assertTrue(report.isEmpty());
		assertEquals(1, report.getPagesSeen());
	}

	@Test
	public void theDriestMissingItemIsCalledOut()
	{
		SpoonReport report = build(zulrah(4096, 0), 10);
		List<SpoonReport.SourceLine> sources = report.getSources();

		assertEquals(1, sources.size());

		SpoonReport.ItemLine driest = sources.get(0).getDriestMissing();

		assertNotNull(driest);
		// Four items share the 1/1024 rate, so any of them is a fair answer; what matters is that it
		// is one of those and not the 1/13106 mutagen.
		assertTrue(driest.getChance() >= 1.0 / 1024);
		assertTrue(driest.getDryness() > 0.98);
	}

	@Test
	public void largestCounterWins()
	{
		CollectionLogPage page = new CollectionLogPage("Chambers of Xeric");
		page.getCounters().put("Chest opened", 250);
		page.getCounters().put("Challenge mode chest opened", 3);

		assertEquals(250, page.getPrimaryCount());
		assertEquals("Chest opened", page.getPrimaryCounterLabel());
	}

	@Test
	public void itemNamesMatchLooselyEnoughForTheGamesSpelling()
	{
		CollectionLogPage page = new CollectionLogPage("Theatre of Blood");
		page.getCounters().put("Theatre of Blood completions", 300);
		page.getItems().put("Sanguinesti staff (uncharged)", 1);

		assertEquals(1, page.quantityOf("Sanguinesti staff"));

		SpoonReport report = SpoonReport.build(Collections.singletonList(page), dropTable, uniqueChances, 10);

		assertEquals(1, report.getObtained());
	}

	private SpoonReport build(CollectionLogPage page, int minKc)
	{
		return SpoonReport.build(Collections.singletonList(page), dropTable, uniqueChances, minKc);
	}

	/** A Zulrah page with {@code uniques} spread over its 1/1024 drops. */
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
}
