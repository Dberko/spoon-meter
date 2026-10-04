package com.spoonmeter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

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

		/** What the page displays, which for a shared item is the account-wide total. */
		private int shown;

		/** This page's share of {@link #shown} once the item has been pooled. */
		private int allocated;
		private double remainder;

		private Accumulator(String name)
		{
			this.name = name;
		}
	}

	/** One page's expectations, before shared items have been divided up. */
	private static final class Scored
	{
		private final String name;
		private final Map<String, Integer> counters;
		private final int kc;
		private final boolean approximate;
		private final Map<String, Accumulator> items;

		private Scored(String name, Map<String, Integer> counters, int kc, boolean approximate,
			Map<String, Accumulator> items)
		{
			this.name = name;
			this.counters = counters;
			this.kc = kc;
			this.approximate = approximate;
			this.items = items;
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
	 * <p>Done in two passes because of shared items. A collection log page shows your <b>total</b>
	 * quantity of an item, not the amount that source gave you, so an item with several sources
	 * appears in full on each of their pages: one Virtus robe top reads as three across the
	 * Desert Treasure bosses, and one uncut onyx reads as three across Zulrah, Zalcano and Skotizo.
	 * Counting each page separately would multiply it by the number of sources.
	 *
	 * <p>So the first pass works out what each page expects, the second pools each item across its
	 * sources - expectations summed, the count taken once - and hands the count back out in
	 * proportion to how much of the expectation each page is responsible for. A page with one kill
	 * can no longer claim a drop that a page with 252 earned.
	 *
	 * @param uniqueChances per-completion unique chance for the weight-based raid entries, keyed by
	 *                      the variant's uniqueChanceKey
	 * @param minKc         pages below this many attempts are ignored; a handful says nothing
	 */
	public static SpoonReport build(Collection<CollectionLogPage> pages, DropTable dropTable,
		Map<String, Double> uniqueChances, int minKc)
	{
		List<Scored> scored = new ArrayList<>();

		for (CollectionLogPage page : pages)
		{
			Scored candidate = expectations(page, dropTable, uniqueChances, minKc);

			if (candidate != null)
			{
				scored.add(candidate);
			}
		}

		shareItems(scored);

		List<SourceLine> lines = new ArrayList<>();
		int totalObtained = 0;
		double totalExpected = 0;

		for (Scored page : scored)
		{
			List<ItemLine> items = new ArrayList<>();
			int pageObtained = 0;
			double pageExpected = 0;

			for (Accumulator accumulator : page.items.values())
			{
				pageObtained += accumulator.allocated;
				pageExpected += accumulator.expected;

				items.add(new ItemLine(accumulator.name, accumulator.expected / page.kc,
					accumulator.allocated, accumulator.expected, 1.0 - accumulator.missProbability));
			}

			if (pageExpected <= 0)
			{
				continue;
			}

			totalObtained += pageObtained;
			totalExpected += pageExpected;

			lines.add(new SourceLine(page.name, page.counters, page.kc, pageObtained, pageExpected,
				LuckCalculator.luckPercentile(pageExpected, pageObtained), page.approximate, items));
		}

		double percentile = LuckCalculator.luckPercentile(totalExpected, totalObtained);
		return new SpoonReport(lines, totalObtained, totalExpected, percentile, pages.size());
	}

	/** First pass: what this page's kill counts should have produced, and what the log shows. */
	@Nullable
	private static Scored expectations(CollectionLogPage page, DropTable dropTable,
		Map<String, Double> uniqueChances, int minKc)
	{
		DropTable.Source source = dropTable.find(page.getName());

		if (source == null)
		{
			return null;
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

				String key = DropTable.normalise(item.getName());
				Accumulator accumulator = accumulated.computeIfAbsent(key, k -> new Accumulator(item.getName()));

				accumulator.expected += kc * chance;
				accumulator.missProbability *= Math.pow(1.0 - chance, kc);
				accumulator.shown = page.quantityOfAny(item.getNames());
			}
		}

		if (totalKc < Math.max(1, minKc) || accumulated.isEmpty())
		{
			return null;
		}

		return new Scored(page.getName(), countersUsed, totalKc, source.isApprox(), accumulated);
	}

	/**
	 * Second pass: pool each item across the pages that can drop it, then hand the count back out
	 * by expectation share. Largest remainder keeps the parts whole and still summing to the total.
	 */
	private static void shareItems(List<Scored> scored)
	{
		Map<String, List<Accumulator>> byItem = new LinkedHashMap<>();

		for (Scored page : scored)
		{
			for (Map.Entry<String, Accumulator> entry : page.items.entrySet())
			{
				byItem.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
			}
		}

		for (List<Accumulator> sources : byItem.values())
		{
			if (sources.size() == 1)
			{
				Accumulator only = sources.get(0);
				only.allocated = only.shown;
				continue;
			}

			// Every page shows the same running total, so the largest is simply the freshest read.
			int owned = 0;
			double expected = 0;

			for (Accumulator source : sources)
			{
				owned = Math.max(owned, source.shown);
				expected += source.expected;
			}

			// The chance of owning one at all is a question about every source at once.
			double miss = 1.0;

			for (Accumulator source : sources)
			{
				miss *= source.missProbability;
			}

			int handedOut = 0;

			for (Accumulator source : sources)
			{
				source.missProbability = miss;

				double share = expected <= 0 ? 0 : owned * (source.expected / expected);
				source.allocated = (int) Math.floor(share);
				source.remainder = share - source.allocated;
				handedOut += source.allocated;
			}

			// Whatever rounding left over goes to the pages with the strongest claim.
			List<Accumulator> byRemainder = new ArrayList<>(sources);
			byRemainder.sort(Comparator.comparingDouble((Accumulator a) -> a.remainder).reversed());

			for (int i = 0; handedOut < owned && i < byRemainder.size(); i++, handedOut++)
			{
				byRemainder.get(i).allocated++;
			}
		}
	}
}
