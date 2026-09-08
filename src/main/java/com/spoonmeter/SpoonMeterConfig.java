package com.spoonmeter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(SpoonMeterConfig.GROUP)
public interface SpoonMeterConfig extends Config
{
	String GROUP = "spoonmeter";

	@ConfigSection(
		name = "Raid drop rates",
		description = "Raid unique chances depend on your points, team size and invocation, so set them to match how you actually raid",
		position = 20,
		closedByDefault = true
	)
	String raidSection = "raidSection";

	@ConfigItem(
		keyName = "minimumKc",
		name = "Minimum KC",
		description = "Pages below this kill count are ignored. Ten kills tell you nothing about your luck",
		position = 1
	)
	@Range(min = 1, max = 1000)
	default int minimumKc()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "sortMode",
		name = "Sort by",
		description = "Order of the per-boss list",
		position = 2
	)
	default SortMode sortMode()
	{
		return SortMode.CURSED_FIRST;
	}

	@ConfigItem(
		keyName = "chatCommand",
		name = "Enable ::spoon",
		description = "Type ::spoon in the chat box to print your rating to the game chat. Only you see it",
		position = 3
	)
	default boolean chatCommand()
	{
		return true;
	}

	@ConfigItem(
		keyName = "publicChatCommand",
		name = "Enable !spoon in chat",
		description = "Replaces your own !spoon message in public, clan or friends chat with your rating, the way !kc works. Only your client shows the formatted version - everyone else receives the literal text, because the client cannot change what is sent and nobody else has your collection log. Add a boss name to rate one page, e.g. !spoon gauntlet",
		position = 4
	)
	default boolean publicChatCommand()
	{
		return true;
	}

	@ConfigItem(
		keyName = "coxUniqueChance",
		name = "CoX purple chance",
		description = "Your average chance of a unique per Chambers raid. Points / 8675 gives the percent, so 30k points is 3.5",
		position = 21,
		section = raidSection
	)
	@Units(Units.PERCENT)
	default double coxUniqueChance()
	{
		return 3.5;
	}

	@ConfigItem(
		keyName = "coxCmUniqueChance",
		name = "CoX (CM) purple chance",
		description = "Your average chance of a unique per Challenge Mode raid. Same points / 8675 rule, on the points a CM raid earns you",
		position = 22,
		section = raidSection
	)
	@Units(Units.PERCENT)
	default double coxCmUniqueChance()
	{
		return 5.0;
	}

	@ConfigItem(
		keyName = "tobUniqueChance",
		name = "ToB purple chance",
		description = "Your average chance of a unique per Theatre raid. The team rate is 1/9.1 (11%) split between the team, so a trio is about 3.7",
		position = 22,
		section = raidSection
	)
	@Units(Units.PERCENT)
	default double tobUniqueChance()
	{
		return 3.7;
	}

	@ConfigItem(
		keyName = "tobHardUniqueChance",
		name = "ToB (HM) purple chance",
		description = "Your average chance of a unique per Hard Mode Theatre raid. The team rate is 13% (1/7.7) split between the team, so a trio is about 4.4",
		position = 24,
		section = raidSection
	)
	@Units(Units.PERCENT)
	default double tobHardUniqueChance()
	{
		return 4.4;
	}

	@ConfigItem(
		keyName = "toaUniqueChance",
		name = "ToA purple chance",
		description = "Your average chance of a unique per Tombs raid. Roughly points / (10500 - 20 x raid level) as a percent: about 5 for a 300 solo, 12 for a 400. This one setting can dominate your account total, so set it to the raids you actually run",
		position = 25,
		section = raidSection
	)
	@Units(Units.PERCENT)
	default double toaUniqueChance()
	{
		return 5.0;
	}

	@ConfigItem(
		keyName = "toaExpertUniqueChance",
		name = "ToA (Expert) purple chance",
		description = "Your average chance of a unique per Expert Tombs raid. Higher raid levels raise both the points and the rate: about 8 for a 350, 15 for a 500",
		position = 26,
		section = raidSection
	)
	@Units(Units.PERCENT)
	default double toaExpertUniqueChance()
	{
		return 8.0;
	}
}
