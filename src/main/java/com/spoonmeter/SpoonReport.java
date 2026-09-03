package com.spoonmeter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rating itself: every collection log page we have data and rates for, scored against how many
 * uniques the KC should have produced, plus the aggregate verdict for the account.
 */
public class SpoonReport
{
	/** One unique on a page: what it should have cost, and what it actually cost. */
	public static class ItemLine
	{
		private final String name;
		private final double chance;
		private final int obtained;
		private final double expected;
		private final double dryness;

		ItemLine(String name, double chance, int obtained, double expected, double dryness)
		{
			this.name = name;
			this.chance = chance;
			this.obtained = obtained;
			this.expected = expected;
			this.dryness = dryness;
		}

		public String getName()
		{
			return name;
		}

		/**
		 * Effective chance per attempt. On a page whose attempts are split across variants at
		 * different rates - normal and corrupted Gauntlet, say - this is the blend, so it moves as
		 * the split does.
		 */
		public double getChance()
		{
			return chance;
		}

		public int getObtained()
		{
			return obtained;
		}

		public double getExpected()
		{
			return expected;
		}

		/**
		 * Share of players who would already own this item after the same attempts. Only meaningful
		 * while you do not: 0.97 means 97 in 100 have it and you do not.
		 */
		public double getDryness()
		{
			return dryness;
		}
	}

	public static class SourceLine
	{
		private final String name;
		private final Map<String, Integer> counters;
		private final int kc;
		private final int obtained;
		private final double expected;
		private final double percentile;
		private final LuckRating rating;
		private final boolean approximate;
		private final List<ItemLine> items;

		SourceLine(String name, Map<String, Integer> counters, int kc, int obtained, double expected,
			double percentile, boolean approximate, List<ItemLine> items)
		{
			this.name = name;
			this.counters = counters;
			this.kc = kc;
			this.obtained = obtained;
			this.expected = expected;
			this.percentile = percentile;
			this.rating = LuckRating.fromPercentile(percentile);
			this.approximate = approximate;
			this.items = items;
		}

		public String getName()
		{
			return name;
		}

		/** The counters that actually fed the rating, in table order. */
		public Map<String, Integer> getCounters()
		{
			return counters;
		}

		/** Total attempts across every counted variant. */
		public int getKc()
		{
			return kc;
		}

		public int getObtained()
		{
			return obtained;
		}

		public double getExpected()
		{
			return expected;
		}

		public double getPercentile()
		{
			return percentile;
		}

		public LuckRating getRating()
		{
			return rating;
		}

		public boolean isApproximate()
		{
			return approximate;
		}

		public List<ItemLine> getItems()
		{
			return items;
		}

		/** Uniques per expected unique. 2.0 is double rate, 0.5 is half. */
		public double getRatio()
		{
			return expected <= 0 ? 1.0 : obtained / expected;
		}

		/** The item you are furthest behind on, or null if nothing notable is missing. */
		public ItemLine getDriestMissing()
		{
			ItemLine driest = null;

			for (ItemLine item : items)
			{
				if (item.getObtained() == 0 && (driest == null || item.getDryness() > driest.getDryness()))
				{
					driest = item;
				}
			}

			return driest != null && driest.getDryness() >= 0.5 ? driest : null;
		}
	}

	/** Running totals for one item while its variants are being added up. */
	private static final class Accumulator
	{
		private final String name;
		private double expected;
		private double missProbability = 1.0;

		private Accumulator(String name)
		{
			this.name = name;
		}
	}

	private final List<SourceLine> sources;
	private final int obtained;
	private final double expected;
	private final double percentile;
	private final LuckRating rating;
	private final int pagesSeen;

	private SpoonReport(List<SourceLine> sources, int obtained, double expected, double percentile, int pagesSeen)
	{
		this.sources = sources;
		this.obtained = obtained;
		this.expected = expected;
		this.percentile = percentile;
		this.rating = LuckRating.fromPercentile(percentile);
		this.pagesSeen = pagesSeen;
	}

	public List<SourceLine> getSources()
	{
		return Collections.unmodifiableList(sources);
	}

	public int getObtained()
	{
		return obtained;
	}

	public double getExpected()
	{
		return expected;
	}

	public double getPercentile()
	{
		return percentile;
	}

	public LuckRating getRating()
	{
		return rating;
	}

	public int getSpoonScore()
	{
		return LuckRating.spoonScore(percentile);
	}

	/** Pages we have data for, including ones with no drop rates or too little KC to rate. */
	public int getPagesSeen()
	{
		return pagesSeen;
	}

	public boolean isEmpty()
	{
		return sources.isEmpty();
	}

	public double getRatio()
	{
		return expected <= 0 ? 1.0 : obtained / expected;
	}

	/**
	 * Scores every page we can, then rolls them into one account-wide verdict.
	 *
	 * @param uniqueChances per-completion unique chance for the weight-based raid entries, keyed by
	 *                      the variant's uniqueChanceKey
	 * @param minKc         pages below this many attempts are ignored; a handful says nothing
	 */
	public static SpoonReport build(Collection<CollectionLogPage> pages, DropTable dropTable,
		Map<String, Double> uniqueChances, int minKc)
	{
		List<SourceLine> lines = new ArrayList<>();
		int totalObtained = 0;
		double totalExpected = 0;

		for (CollectionLogPage page : pages)
		{
			DropTable.Source source = dropTable.find(page.getName());

			if (source == null)
			{
				continue;
			}

			Map<String, Accumulator> accumulated = new LinkedHashMap<>();
			Map<String, Integer> countersUsed = new LinkedHashMap<>();
			int totalKc = 0;

			for (DropTable.Variant variant : source.getVariants())
			{
				String counter = variant.getCounter();
				int kc = counter == null ? page.getPrimaryCount() : page.counterValue(counter);

				if (kc <= 0)
				{
					continue;
				}

				double uniqueChance = 0.0;

				if (variant.getUniqueChanceKey() != null)
				{
					Double configured = uniqueChances.get(variant.getUniqueChanceKey());

					if (configured == null || configured <= 0)
					{
						continue;
					}

					uniqueChance = configured;
				}

				totalKc += kc;
				countersUsed.put(counter == null ? page.getPrimaryCounterLabel() : counter, kc);

				for (DropTable.Item item : variant.getItems())
				{
					double chance = DropTable.probability(variant, item, uniqueChance);

					if (chance <= 0)
					{
						continue;
					}

					Accumulator accumulator = accumulated.computeIfAbsent(
						DropTable.normalise(item.getName()), key -> new Accumulator(item.getName()));

					accumulator.expected += kc * chance;
					accumulator.missProbability *= Math.pow(1.0 - chance, kc);
				}
			}

			if (totalKc < Math.max(1, minKc) || accumulated.isEmpty())
			{
				continue;
			}

			List<ItemLine> items = new ArrayList<>();
			int pageObtained = 0;
			double pageExpected = 0;

			for (Accumulator accumulator : accumulated.values())
			{
				int count = page.quantityOf(accumulator.name);

				pageObtained += count;
				pageExpected += accumulator.expected;

				items.add(new ItemLine(accumulator.name, accumulator.expected / totalKc, count,
					accumulator.expected, 1.0 - accumulator.missProbability));
			}

			if (pageExpected <= 0)
			{
				continue;
			}

			totalObtained += pageObtained;
			totalExpected += pageExpected;

			lines.add(new SourceLine(page.getName(), countersUsed, totalKc, pageObtained, pageExpected,
				LuckCalculator.luckPercentile(pageExpected, pageObtained), source.isApprox(), items));
		}

		double percentile = LuckCalculator.luckPercentile(totalExpected, totalObtained);
		return new SpoonReport(lines, totalObtained, totalExpected, percentile, pages.size());
	}
}
