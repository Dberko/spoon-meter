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
		keyName = "hideCombinedPages",
		name = "Hide combined boss pages",
		description = "Leave out log pages that cover several different bosses at once - Callisto and Artio, the Dagannoth Kings and the other wilderness pairs. One verdict across two bosses says little about either, so this drops them from the list and from your account total",
		position = 5
	)
	default boolean hideCombinedPages()
	{
		return false;
	}

	@ConfigItem(
		keyName = "flippedItems",
		name = "",
		description = "Items whose counting has been turned around from the right-click menu",
		hidden = true,
		position = 7
	)
	default String flippedItems()
	{
		return "";
	}

	@ConfigItem(
		keyName = "hiddenSources",
		name = "Hidden bosses",
		description = "Bosses left out of the list and the account total, separated by commas. Right-click a boss in the panel to add one, or edit the list here",
		position = 6
	)
	default String hiddenSources()
	{
		return "";
	}

	@ConfigItem(
		keyName = "doomUniqueChance",
		name = "Doom unique chance",
		description = "Your average chance of a unique per delve at Doom of Mokhaiotl. It climbs with depth: about 0.1 at delve 3, 0.39 at delve 6, 0.56 at delve 9 and beyond",
		position = 27,
		section = raidSection
	)
	@Units(Units.PERCENT)
	default double doomUniqueChance()
	{
		return 0.39;
	}

	@ConfigItem(
		keyName = "coxUniqueChance",
		name = "CoX purple chance",
		description = "YOUR OWN chance of a unique per Chambers raid. Use your own points, not the "
			+ "team's: your points over 8,675 gives the percent, so 30k of your own is 3.5. A raid "
			+ "hands its uniques out weighted by the points each player earned, so a bigger team "
			+ "splits the same pot more ways",
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
		description = "YOUR OWN chance of a unique per Theatre raid, which is not the team's. The "
			+ "raid rolls one unique at 1/9.1 (11%) however many of you there are, and hands it to "
			+ "one player weighted by damage and deaths, so your share falls as the team grows."
			+ "<br><br>Solo: 11<br>Duo: 5.5<br>Trio: 3.7<br>Four: 2.8<br>Five: 2.2"
			+ "<br><br>Raise it if you are usually the MVP, who the wiki says is likeliest to get it.",
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
		description = "YOUR OWN chance of a unique per Hard Mode Theatre raid, which is not the "
			+ "team's. The raid rolls one unique at 1/7.7 (13%) however many of you there are."
			+ "<br><br>Solo: 13<br>Duo: 6.5<br>Trio: 4.4<br>Four: 3.3<br>Five: 2.6",
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
		description = "YOUR OWN chance of a unique per Tombs raid, which is not the team's. Roughly "
			+ "points / (10500 - 20 x raid level) as a percent: about 5 for a 300 SOLO, 12 for a 400 "
			+ "solo. Tombs rolls one unique per raid and gives it to one player, so divide by your "
			+ "team size: a 300 trio is nearer 1.7 than 5."
			+ "<br><br>This one setting can dominate your account total, so set it to the raids you "
			+ "actually run",
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

	@ConfigItem(
		keyName = "tfaUniqueChance",
		name = "TFA unique chance",
		description = "YOUR OWN chance of a unique per Fractured Archive raid, which is not the "
			+ "team's. The raid rolls one unique and hands it to somebody by damage, so your share "
			+ "of it falls as the team grows."
			+ "<br><br>Solo: 5.9"
			+ "<br>Duo: 2.9"
			+ "<br>Trio: 2.7"
			+ "<br>Four: 2.4"
			+ "<br>Five: 2.1"
			+ "<br>Six: 2.0"
			+ "<br>Seven: 1.8"
			+ "<br>Eight: 1.7"
			+ "<br><br>Raise it if you out-damage the rest of the team, lower it if you carry less.",
		position = 28,
		section = raidSection
	)
	@Units(Units.PERCENT)
	default double tfaUniqueChance()
	{
		return 2.7;
	}

	@ConfigSection(
		name = "How you fight them",
		description = "Some bosses roll their drops at a rate that depends on how you fight them, which the collection log cannot see: it records the kills, not whether you shared them or awakened them",
		position = 30,
		closedByDefault = true
	)
	String minigameSection = "minigameSection";

	@ConfigItem(
		keyName = "hueycoatlContribution",
		name = "Hueycoatl damage share",
		// RuneLite wraps a description in <html> for the tooltip, so this lays the figures out as a
		// list. Nobody knows their damage share off the top of their head, but they do know how many
		// people they fight it with, and a kill is a fixed 4,050 damage however many that is.
		description = "Your average share of the damage you deal to the Hueycoatl."
			+ "<br><br>Its 1/18 unique chance is per player and scaled by contribution, so a mass "
			+ "splits one roll between everyone fighting. A kill is 4,050 damage however many of "
			+ "you there are, so your share is your own damage over that."
			+ "<br><br>Solo: 100"
			+ "<br>Duo: 50"
			+ "<br>Trio: 33"
			+ "<br>Five: 20"
			+ "<br>Ten: 10"
			+ "<br>A full mass of twenty: 5"
			+ "<br><br>Raise it if you out-damage the rest of the crowd, or lower it if you turn up "
			+ "late and only get a few hits in. Zero leaves the boss out of the rating entirely.",
		position = 32,
		section = minigameSection
	)
	@Units(Units.PERCENT)
	default double hueycoatlContribution()
	{
		return 5.0;
	}

	@ConfigItem(
		keyName = "dt2AwakenedShare",
		name = "Awakened DT2 kills",
		description = "How many of your Desert Treasure II kills were awakened."
			+ "<br><br>An awakened Vardorvis, Duke, Leviathan or Whisperer rolls its unique table "
			+ "three times as often, and the collection log counts a kill the same either way, so a "
			+ "plugin that assumes none of them were awakened will call you spooned for drops you "
			+ "had three times the chance at."
			+ "<br><br>Leave it at 0 if you only fight them normally. The orb and the pet are rolled "
			+ "outside that table and are left alone.",
		position = 33,
		section = minigameSection
	)
	@Units(Units.PERCENT)
	default double dt2AwakenedShare()
	{
		return 0.0;
	}
}
