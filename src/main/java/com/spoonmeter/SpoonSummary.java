package com.spoonmeter;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/** Turns a report into text, for the clipboard button and the chat command. */
final class SpoonSummary
{
	static final DecimalFormat ONE_DP = new DecimalFormat("#,##0.0");
	static final DecimalFormat WHOLE = new DecimalFormat("#,##0");

	private SpoonSummary()
	{
	}

	/** "Cursed - 12/100 - 14 uniques vs 21.3 expected (0.66x)". */
	static String oneLine(SpoonReport report)
	{
		return report.getRating().getTitle()
			+ " - " + report.getSpoonScore() + "/100 - "
			+ WHOLE.format(report.getObtained()) + " uniques vs "
			+ ONE_DP.format(report.getExpected()) + " expected ("
			+ ONE_DP.format(report.getRatio()) + "x)";
	}

	/** The game's public/clan chat box refuses anything longer than this. */
	static final int MAX_CHAT_LENGTH = 80;

	/**
	 * A single line that fits in a game chat message. Detail is dropped in order of importance until
	 * it fits, so a long rating name plus large numbers degrades gracefully instead of being cut off
	 * mid-word.
	 *
	 * @param argument optional boss name; blank for the account-wide verdict
	 */
	static String chatLine(SpoonReport report, String argument)
	{
		return chatLine(report, argument, MAX_CHAT_LENGTH);
	}

	/** As above, but with room reserved for a channel prefix such as "/" for clan chat. */
	static String chatLine(SpoonReport report, String argument, int maxLength)
	{
		String subject = "";
		LuckRating rating = report.getRating();
		int score = report.getSpoonScore();
		int obtained = report.getObtained();
		double expected = report.getExpected();
		double ratio = report.getRatio();

		if (argument != null && !argument.trim().isEmpty())
		{
			SpoonReport.SourceLine match = findSource(report, argument);

			if (match == null)
			{
				return "Spoon Meter: no rated page matching " + argument.trim();
			}

			subject = " " + match.getName();
			rating = match.getRating();
			score = LuckRating.spoonScore(match.getPercentile());
			obtained = match.getObtained();
			expected = match.getExpected();
			ratio = match.getRatio();
		}

		String head = "Spoon Meter" + subject + ": " + rating.getTitle() + " (" + score + "/100)";

		String[] candidates = {
			head + " - " + WHOLE.format(obtained) + " uniques vs " + ONE_DP.format(expected)
				+ " expected, " + ONE_DP.format(ratio) + "x",
			head + " - " + WHOLE.format(obtained) + " vs " + ONE_DP.format(expected)
				+ ", " + ONE_DP.format(ratio) + "x",
			head + " - " + ONE_DP.format(ratio) + "x",
			head
		};

		for (String candidate : candidates)
		{
			if (candidate.length() <= maxLength)
			{
				return candidate;
			}
		}

		String shortest = candidates[candidates.length - 1];
		return shortest.length() <= maxLength ? shortest : shortest.substring(0, maxLength);
	}

	private static SpoonReport.SourceLine findSource(SpoonReport report, String argument)
	{
		String wanted = DropTable.normalise(argument);

		for (SpoonReport.SourceLine source : report.getSources())
		{
			if (DropTable.normalise(source.getName()).equals(wanted))
			{
				return source;
			}
		}

		for (SpoonReport.SourceLine source : report.getSources())
		{
			if (DropTable.normalise(source.getName()).contains(wanted))
			{
				return source;
			}
		}

		return null;
	}

	static String forClipboard(SpoonReport report, SortMode sortMode)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Spoon Meter\n");
		sb.append(oneLine(report)).append('\n');
		sb.append("Luckier than ").append(report.getSpoonScore())
			.append("% of accounts at the same KC.\n\n");

		List<SpoonReport.SourceLine> sources = new ArrayList<>(report.getSources());
		sources.sort(sortMode.getComparator());

		for (SpoonReport.SourceLine source : sources)
		{
			sb.append(source.getName())
				.append(" - ").append(WHOLE.format(source.getKc())).append(" kc - ")
				.append(source.getObtained()).append('/').append(ONE_DP.format(source.getExpected()))
				.append(" (").append(ONE_DP.format(source.getRatio())).append("x) - ")
				.append(source.getRating().getTitle());

			SpoonReport.ItemLine driest = source.getDriestMissing();

			if (driest != null)
			{
				sb.append(" - driest: ").append(driest.getName())
					.append(" (").append(Math.round(driest.getDryness() * 100)).append("% have it)");
			}

			sb.append('\n');
		}

		return sb.toString();
	}
}
