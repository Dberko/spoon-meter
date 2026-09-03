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
