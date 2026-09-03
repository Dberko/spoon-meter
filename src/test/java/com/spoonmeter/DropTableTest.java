package com.spoonmeter;

import com.google.gson.Gson;
import java.io.IOException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class DropTableTest
{
	private DropTable dropTable;

	@Before
	public void setUp() throws IOException
	{
		dropTable = DropTable.load(new Gson(), null);
	}

	@Test
	public void loadsTheBundledTable()
	{
		assertTrue(dropTable.getSources().size() > 20);
		assertNotNull(dropTable.find("Zulrah"));
		assertNotNull(dropTable.find("Chambers of Xeric"));
		assertNull(dropTable.find("Some Boss That Does Not Exist"));
	}

	@Test
	public void lookupSurvivesPunctuationAndCase()
	{
		assertNotNull(dropTable.find("KREE'ARRA"));
		assertNotNull(dropTable.find("kreearra"));
		assertNotNull(dropTable.find("The Nightmare"));
		assertNotNull(dropTable.find("Nightmare"));
		assertEquals("sanguinesti staff", DropTable.normalise("Sanguinesti staff (uncharged)"));
	}

	@Test
	public void flatRatesAreInverted()
	{
		DropTable.Source zulrah = dropTable.find("Zulrah");
		assertEquals(1.0 / 1024, probabilityOf(zulrah, "Tanzanite fang", 0), 1.0e-12);
		assertEquals(1.0 / 4000, probabilityOf(zulrah, "Pet snakeling", 0), 1.0e-12);
	}

	@Test
	public void raidWeightsScaleWithTheConfiguredUniqueChance()
	{
		DropTable.Source cox = dropTable.find("Chambers of Xeric");
		assertEquals(0.035 * 2 / 60, probabilityOf(cox, "Twisted bow", 0.035), 1.0e-12);
		assertEquals(0.035 * 14 / 60, probabilityOf(cox, "Dexterous prayer scroll", 0.035), 1.0e-12);
	}

	/** A typo in the data file would otherwise quietly hand out free luck. */
	@Test
	public void everySourceProducesUsableProbabilities()
	{
		for (DropTable.Source source : dropTable.getSources())
		{
			assertTrue(source.getName() + " has no items", !source.getItems().isEmpty());

			for (DropTable.Variant variant : source.getVariants())
			{
				double weightSum = 0;

				for (DropTable.Item item : variant.getItems())
				{
					double p = DropTable.probability(variant, item, 0.05);

					assertTrue(source.getName() + " / " + item.getName() + " has no rate", p > 0);
					assertTrue(source.getName() + " / " + item.getName() + " is not a probability", p <= 1);

					if (item.weight != null)
					{
						assertNotNull(source.getName() + " uses weights without a totalWeight", variant.totalWeight);
						weightSum += item.weight;
					}
				}

				if (variant.totalWeight != null)
				{
					assertEquals(source.getName() + " weights do not sum to totalWeight",
						variant.totalWeight, weightSum, 1.0e-9);
				}
			}
		}
	}

	/** Counter labels keep their brackets, or the two Chambers counters become the same key. */
	@Test
	public void counterNormalisationKeepsTheModeApart()
	{
		assertEquals("chambers of xeric completions",
			DropTable.normaliseCounter("Chambers of Xeric completions"));
		assertEquals("chambers of xeric cm completions",
			DropTable.normaliseCounter("Chambers of Xeric (CM) completions"));
		assertNotEquals(DropTable.normaliseCounter("Theatre of Blood completions"),
			DropTable.normaliseCounter("Theatre of Blood (Hard) completions"));
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

		throw new AssertionError("No such item: " + itemName);
	}
}
