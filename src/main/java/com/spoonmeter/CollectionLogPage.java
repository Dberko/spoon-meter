package com.spoonmeter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One page of the collection log as it was last seen: its counters and the uniques already
 * obtained. Only obtained items are stored; anything missing is worked out from the drop table,
 * which keeps the saved file small and stops it going stale when Jagex adds items to a page.
 */
public class CollectionLogPage
{
	private String name;
	private int obtained;
	private int total;
	private Map<String, Integer> counters = new LinkedHashMap<>();
	private Map<String, Integer> items = new LinkedHashMap<>();
	private long updatedMillis;

	public CollectionLogPage()
	{
	}

	public CollectionLogPage(String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public int getObtained()
	{
		return obtained;
	}

	public void setObtained(int obtained)
	{
		this.obtained = obtained;
	}

	public int getTotal()
	{
		return total;
	}

	public void setTotal(int total)
	{
		this.total = total;
	}

	public Map<String, Integer> getCounters()
	{
		return counters;
	}

	public void setCounters(Map<String, Integer> counters)
	{
		this.counters = counters == null ? new LinkedHashMap<>() : counters;
	}

	public Map<String, Integer> getItems()
	{
		return items;
	}

	public void setItems(Map<String, Integer> items)
	{
		this.items = items == null ? new LinkedHashMap<>() : items;
	}

	public long getUpdatedMillis()
	{
		return updatedMillis;
	}

	public void setUpdatedMillis(long updatedMillis)
	{
		this.updatedMillis = updatedMillis;
	}

	/**
	 * The counter the rating is based on. Pages can show several ("Kill count", "Chest opened",
	 * "Reward permit"); the largest is the one that actually generated the drop rolls.
	 */
	public int getPrimaryCount()
	{
		int best = 0;

		for (int value : counters.values())
		{
			best = Math.max(best, value);
		}

		return best;
	}

	public String getPrimaryCounterLabel()
	{
		String label = null;
		int best = -1;

		for (Map.Entry<String, Integer> entry : counters.entrySet())
		{
			if (entry.getValue() > best)
			{
				best = entry.getValue();
				label = entry.getKey();
			}
		}

		return label == null ? "Kill count" : label;
	}

	/**
	 * Value of a named counter, matched loosely so that "Corrupted Gauntlet completion count"
	 * survives punctuation and case differences. 0 when the page has no such counter.
	 */
	public int counterValue(String label)
	{
		String wanted = DropTable.normaliseCounter(label);

		for (Map.Entry<String, Integer> entry : counters.entrySet())
		{
			if (DropTable.normaliseCounter(entry.getKey()).equals(wanted))
			{
				return entry.getValue();
			}
		}

		return 0;
	}

	/** Total number of a given item obtained, matched on the normalised name. */
	public int quantityOf(String itemName)
	{
		String wanted = DropTable.normalise(itemName);

		for (Map.Entry<String, Integer> entry : items.entrySet())
		{
			if (DropTable.normalise(entry.getKey()).equals(wanted))
			{
				return entry.getValue();
			}
		}

		return 0;
	}

	/** Combined quantity of every item in a group that shares one drop slot. */
	public int quantityOfAny(java.util.List<String> itemNames)
	{
		int total = 0;

		for (String itemName : itemNames)
		{
			total += quantityOf(itemName);
		}

		return total;
	}

	/** Cheap fingerprint used to skip re-saving a page that has not changed since the last tick. */
	public String fingerprint()
	{
		return name + "|" + obtained + "/" + total + "|" + counters + "|" + items;
	}
}
