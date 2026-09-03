package com.spoonmeter;

import java.awt.Color;

/**
 * The verdict bands. Each covers a slice of the luck percentile, from "the drop table loves you"
 * down to "the drop table has personally wronged you".
 */
public enum LuckRating
{
	SPOONED_BY_THE_GODS(0.99, "Spooned by the Gods", new Color(0xFFD700), "Top 1%. Please stop."),
	ABSOLUTELY_SPOONED(0.95, "Absolutely Spooned", new Color(0xFFB300), "You have been handed things."),
	SPOONED(0.85, "Spooned", new Color(0x8BE28B), "Comfortably ahead of rate."),
	BLESSED(0.65, "Blessed", new Color(0x5DBB63), "A quietly lucky account."),
	AVERAGE(0.35, "Painfully Average", new Color(0xC8C8C8), "Exactly what the wiki promised."),
	A_BIT_DRY(0.15, "A Bit Dry", new Color(0xE0A030), "Behind rate, but survivable."),
	CURSED(0.05, "Cursed", new Color(0xFF7043), "The drop table is ignoring you."),
	HARD_CURSED(0.01, "Hard Cursed", new Color(0xE53935), "Bottom 5%. Genuinely unlucky."),
	RNG_HATES_YOU(0.0, "RNG Hates You", new Color(0xC2185B), "Bottom 1%. Consider a new hobby.");

	private final double minPercentile;
	private final String title;
	private final Color color;
	private final String blurb;

	LuckRating(double minPercentile, String title, Color color, String blurb)
	{
		this.minPercentile = minPercentile;
		this.title = title;
		this.color = color;
		this.blurb = blurb;
	}

	public String getTitle()
	{
		return title;
	}

	public Color getColor()
	{
		return color;
	}

	public String getBlurb()
	{
		return blurb;
	}

	public double getMinPercentile()
	{
		return minPercentile;
	}

	public static LuckRating fromPercentile(double percentile)
	{
		for (LuckRating rating : values())
		{
			if (percentile >= rating.minPercentile)
			{
				return rating;
			}
		}

		return RNG_HATES_YOU;
	}

	/** 0-100 "spoon score", which is just the percentile in a friendlier costume. */
	public static int spoonScore(double percentile)
	{
		return (int) Math.round(percentile * 100.0);
	}
}
