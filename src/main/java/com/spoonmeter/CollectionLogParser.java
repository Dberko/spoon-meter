package com.spoonmeter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

/**
 * Reads whichever collection log page is currently on screen.
 *
 * <p>Deliberately structural rather than hardcoded to child indices: it walks the interface looking
 * for the "Obtained: x/y" line to identify the header, and for the widget holding the most item
 * children to identify the item grid. Jagex reshuffles these child ids from time to time, and this
 * survives that.
 */
class CollectionLogParser
{
	static final int COLLECTION_LOG_GROUP_ID = 621;

	private static final int MAX_ROOT_CHILDREN = 100;
	private static final int MAX_DEPTH = 8;

	private static final Pattern OBTAINED = Pattern.compile("^Obtained:\\s*(\\d+)\\s*/\\s*(\\d+)$");

	// The label deliberately cannot contain a colon. Pages also show personal best times such as
	// "Personal Best: 9:51", and a looser pattern reads those as a counter named "Personal Best: 9"
	// with a value of 51 - which then beats the real kill count on any page with a low KC.
	private static final Pattern COUNTER = Pattern.compile("^([^:]+):\\s*([\\d,]+)$");

	private final ItemManager itemManager;

	CollectionLogParser(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	boolean isLogOpen(Client client)
	{
		for (int childId = 0; childId < MAX_ROOT_CHILDREN; childId++)
		{
			if (client.getWidget(COLLECTION_LOG_GROUP_ID, childId) != null)
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * @return the page currently displayed, or null if the log is closed or no page is selected.
	 */
	@Nullable
	CollectionLogPage parseOpenPage(Client client)
	{
		Scan scan = new Scan();

		for (int childId = 0; childId < MAX_ROOT_CHILDREN; childId++)
		{
			Widget root = client.getWidget(COLLECTION_LOG_GROUP_ID, childId);

			if (root != null)
			{
				walk(root, 0, scan);
			}
		}

		if (scan.header == null || scan.itemContainer == null)
		{
			return null;
		}

		CollectionLogPage page = readHeader(scan.header);

		if (page == null)
		{
			return null;
		}

		page.setItems(readItems(scan.itemContainer));
		page.setUpdatedMillis(System.currentTimeMillis());
		return page;
	}

	private void walk(Widget widget, int depth, Scan scan)
	{
		if (widget == null || depth > MAX_DEPTH)
		{
			return;
		}

		List<Widget> children = childrenOf(widget);

		if (!children.isEmpty())
		{
			int itemCount = 0;

			for (Widget child : children)
			{
				if (child.getItemId() > 0)
				{
					itemCount++;
				}

				String text = Text.removeTags(nullToEmpty(child.getText()));

				if (OBTAINED.matcher(text).matches())
				{
					scan.header = widget;
				}
			}

			// The item grid is the one widget on the page holding a wall of items; the tab list and
			// header hold at most a handful, so "most items wins" is a safe way to pick it out.
			if (itemCount > scan.itemCount)
			{
				scan.itemCount = itemCount;
				scan.itemContainer = widget;
			}
		}

		for (Widget child : children)
		{
			walk(child, depth + 1, scan);
		}
	}

	@Nullable
	private CollectionLogPage readHeader(Widget header)
	{
		List<Widget> children = childrenOf(header);

		String title = null;
		int obtainedIndex = -1;
		int obtained = 0;
		int total = 0;
		Map<String, Integer> counters = new LinkedHashMap<>();

		for (int i = 0; i < children.size(); i++)
		{
			String text = Text.removeTags(nullToEmpty(children.get(i).getText())).trim();

			if (text.isEmpty())
			{
				continue;
			}

			Matcher obtainedMatcher = OBTAINED.matcher(text);

			if (obtainedMatcher.matches())
			{
				obtainedIndex = i;
				obtained = parseInt(obtainedMatcher.group(1));
				total = parseInt(obtainedMatcher.group(2));
				continue;
			}

			if (obtainedIndex < 0)
			{
				// Anything before the "Obtained" line is the page title.
				if (title == null)
				{
					title = text;
				}

				continue;
			}

			addCounter(counters, text);
		}

		if (title == null || obtainedIndex < 0)
		{
			return null;
		}

		CollectionLogPage page = new CollectionLogPage(title);
		page.setObtained(obtained);
		page.setTotal(total);
		page.setCounters(counters);
		return page;
	}

	/**
	 * Records a "Label: 1,234" header line as a counter.
	 *
	 * @return false if the line is not a counter at all, such as a "Personal Best: 9:51" time
	 */
	static boolean addCounter(Map<String, Integer> counters, String text)
	{
		Matcher matcher = COUNTER.matcher(text);

		if (!matcher.matches())
		{
			return false;
		}

		counters.put(matcher.group(1).trim(), parseInt(matcher.group(2)));
		return true;
	}

	private Map<String, Integer> readItems(Widget container)
	{
		Map<String, Integer> items = new LinkedHashMap<>();

		for (Widget child : childrenOf(container))
		{
			int itemId = child.getItemId();

			if (itemId <= 0)
			{
				continue;
			}

			// Unobtained entries are drawn faded; obtained ones are fully opaque.
			if (child.getOpacity() != 0)
			{
				continue;
			}

			String name = Text.removeTags(nullToEmpty(child.getName())).trim();

			if (name.isEmpty() && itemManager != null)
			{
				name = itemManager.getItemComposition(itemId).getName();
			}

			if (name.isEmpty())
			{
				continue;
			}

			int quantity = Math.max(1, child.getItemQuantity());
			items.merge(name, quantity, Integer::sum);
		}

		return items;
	}

	private static List<Widget> childrenOf(Widget widget)
	{
		List<Widget> out = new ArrayList<>();
		addAll(out, widget.getStaticChildren());
		addAll(out, widget.getDynamicChildren());
		addAll(out, widget.getNestedChildren());
		return out;
	}

	private static void addAll(List<Widget> out, @Nullable Widget[] widgets)
	{
		if (widgets == null)
		{
			return;
		}

		for (Widget widget : widgets)
		{
			if (widget != null)
			{
				out.add(widget);
			}
		}
	}

	private static int parseInt(String raw)
	{
		try
		{
			return Integer.parseInt(raw.replace(",", "").trim());
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	private static String nullToEmpty(@Nullable String s)
	{
		return s == null ? "" : s;
	}

	private static final class Scan
	{
		private Widget header;
		private Widget itemContainer;
		private int itemCount;
	}
}
