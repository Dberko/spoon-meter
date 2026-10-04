package com.spoonmeter;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.client.util.Filepath;

/**
 * The drop rates the rating is measured against, loaded from {@code drop_rates.json}.
 *
 * <p>A source is either simple - one counter, one set of rates - or split into <b>variants</b>. One
 * collection log page can cover several activities with different rates and their own counters: the
 * Gauntlet page counts normal and corrupted runs separately at 1/120 and 1/50, the raid pages count
 * normal, entry and hard/expert completions, and the Dagannoth Kings page counts all three kings.
 * Each variant names the counter it applies to, and expectations are summed across them.
 *
 * <p>Lookups are done on normalised names so that "Kree'arra", "kreearra" and the collection log's
 * own spelling all land on the same entry, and so that item names survive the log's
 * "(uncharged)" style suffixes.
 */
public class DropTable
{
	public static final String RESOURCE = "/com/spoonmeter/drop_rates.json";

	public static class Definition
	{
		int version;
		String note;
		List<Source> sources;
	}

	public static class Source
	{
		String name;
		List<String> aliases;
		boolean approx;

		// Simple form: one implicit variant.
		String counter;
		String uniqueChanceKey;
		Double totalWeight;
		List<Item> items;

		// Split form.
		List<Variant> variants;

		private transient List<Variant> effective;

		public String getName()
		{
			return name;
		}

		public boolean isApprox()
		{
			return approx;
		}

		/** The split variants, or a single synthesised one for a simple source. */
		public List<Variant> getVariants()
		{
			if (effective == null)
			{
				if (variants != null && !variants.isEmpty())
				{
					effective = variants;
				}
				else
				{
					Variant single = new Variant();
					single.counter = counter;
					single.uniqueChanceKey = uniqueChanceKey;
					single.totalWeight = totalWeight;
					single.items = items;
					effective = Collections.singletonList(single);
				}
			}

			return effective;
		}

		/** Every distinct item across all variants, in table order. */
		public List<Item> getItems()
		{
			Set<String> seen = new LinkedHashSet<>();
			List<Item> out = new ArrayList<>();

			for (Variant variant : getVariants())
			{
				for (Item item : variant.getItems())
				{
					if (seen.add(normalise(item.name)))
					{
						out.add(item);
					}
				}
			}

			return out;
		}
	}

	public static class Variant
	{
		String counter;
		String uniqueChanceKey;
		Double totalWeight;
		List<Item> items;

		/** Name of the page counter this variant is rolled by, or null to use the page's largest. */
		@Nullable
		public String getCounter()
		{
			return counter;
		}

		@Nullable
		public String getUniqueChanceKey()
		{
			return uniqueChanceKey;
		}

		public List<Item> getItems()
		{
			return items == null ? Collections.emptyList() : items;
		}
	}

	public static class Item
	{
		String name;

		/**
		 * Several items sharing a single drop slot, where which one you get is decided by duplicate
		 * protection rather than by a separate roll. The brimstone ring pieces work this way: one
		 * 1/181.1 slot hands out the eye, then the fang, then the heart. Listing them as three
		 * entries would treble the real rate.
		 */
		List<String> names;
		String label;

		Double rate;
		Double weight;
		Double chance;

		/** What the panel shows. */
		public String getName()
		{
			if (name != null)
			{
				return name;
			}

			return label != null ? label : String.join(" / ", getNames());
		}

		/** Every item name this entry covers. */
		public List<String> getNames()
		{
			return names != null && !names.isEmpty() ? names : Collections.singletonList(name);
		}
	}

	private final List<Source> sources;
	private final Map<String, Source> byNormalisedName = new HashMap<>();

	private DropTable(List<Source> sources)
	{
		this.sources = sources;

		for (Source source : sources)
		{
			byNormalisedName.put(normalise(source.name), source);

			if (source.aliases != null)
			{
				for (String alias : source.aliases)
				{
					byNormalisedName.put(normalise(alias), source);
				}
			}
		}
	}

	/**
	 * Loads the bundled table, or the user's own copy if {@code override} exists. Letting people
	 * drop a corrected file next to their RuneLite settings means a wrong rate never needs a
	 * plugin update to fix.
	 */
	public static DropTable load(Gson gson, @Nullable Filepath override) throws IOException
	{
		if (override != null && override.isFile())
		{
			try (Reader reader = override.openBufferedReader())
			{
				return fromReader(gson, reader);
			}
		}

		try (InputStream in = DropTable.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				throw new IOException("Missing bundled drop table: " + RESOURCE);
			}

			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				return fromReader(gson, reader);
			}
		}
	}

	private static DropTable fromReader(Gson gson, Reader reader) throws IOException
	{
		Definition definition = gson.fromJson(reader, Definition.class);

		if (definition == null || definition.sources == null || definition.sources.isEmpty())
		{
			throw new IOException("Drop table is empty or malformed");
		}

		return new DropTable(new ArrayList<>(definition.sources));
	}

	public List<Source> getSources()
	{
		return Collections.unmodifiableList(sources);
	}

	@Nullable
	public Source find(String collectionLogPageName)
	{
		if (collectionLogPageName == null)
		{
			return null;
		}

		return byNormalisedName.get(normalise(collectionLogPageName));
	}

	/**
	 * Per-completion chance of a single item within one variant.
	 *
	 * @param uniqueChance chance of any unique per completion, used by the weight-based raid
	 *                     entries; ignored by entries that state a flat rate.
	 */
	public static double probability(Variant variant, Item item, double uniqueChance)
	{
		if (item.chance != null && item.chance > 0)
		{
			return item.chance;
		}

		if (item.rate != null && item.rate > 0)
		{
			return 1.0 / item.rate;
		}

		if (item.weight != null && variant.totalWeight != null && variant.totalWeight > 0)
		{
			return uniqueChance * (item.weight / variant.totalWeight);
		}

		return 0.0;
	}

	/** Item name to per-completion chance for one variant, in table order. */
	public static Map<String, Double> probabilities(Variant variant, double uniqueChance)
	{
		Map<String, Double> out = new LinkedHashMap<>();

		for (Item item : variant.getItems())
		{
			double p = probability(variant, item, uniqueChance);

			if (p > 0)
			{
				out.put(item.name, p);
			}
		}

		return out;
	}

	/**
	 * Lowercase, drop bracketed qualifiers such as "(uncharged)", and strip punctuation, so that
	 * apostrophes and the game's occasional renames do not break a lookup.
	 */
	public static String normalise(String name)
	{
		if (name == null)
		{
			return "";
		}

		String s = name.toLowerCase(Locale.ENGLISH);
		s = s.replaceAll("\\(.*?\\)", " ");
		s = s.replaceAll("[^a-z0-9 ]", "");
		s = s.replaceAll("\\s+", " ").trim();

		if (s.startsWith("the "))
		{
			s = s.substring(4);
		}

		return s;
	}

	/**
	 * Counter labels get a gentler normalisation than item names: the bracketed part is the whole
	 * distinction between "Chambers of Xeric completions" and "Chambers of Xeric (CM) completions",
	 * so brackets are unwrapped rather than dropped.
	 */
	public static String normaliseCounter(String label)
	{
		if (label == null)
		{
			return "";
		}

		String s = label.toLowerCase(Locale.ENGLISH);
		s = s.replaceAll("[^a-z0-9 ]", " ");
		return s.replaceAll("\\s+", " ").trim();
	}
}
