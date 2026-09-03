package com.spoonmeter;

import java.util.Comparator;

public enum SortMode
{
	CURSED_FIRST("Cursed first", Comparator.comparingDouble(SpoonReport.SourceLine::getPercentile)),
	SPOONED_FIRST("Spooned first", Comparator.comparingDouble(SpoonReport.SourceLine::getPercentile).reversed()),
	KC("Highest KC", Comparator.comparingInt(SpoonReport.SourceLine::getKc).reversed()),
	NAME("Name", Comparator.comparing(SpoonReport.SourceLine::getName, String.CASE_INSENSITIVE_ORDER));

	private final String label;
	private final Comparator<SpoonReport.SourceLine> comparator;

	SortMode(String label, Comparator<SpoonReport.SourceLine> comparator)
	{
		this.label = label;
		this.comparator = comparator;
	}

	public Comparator<SpoonReport.SourceLine> getComparator()
	{
		return comparator;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
