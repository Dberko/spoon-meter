package com.spoonmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class LuckCalculatorTest
{
	private static final double DELTA = 1.0e-6;

	@Test
	public void poissonPmfMatchesKnownValues()
	{
		assertEquals(Math.exp(-1), LuckCalculator.poissonPmf(0, 1), DELTA);
		assertEquals(0.19536681, LuckCalculator.poissonPmf(4, 4), 1.0e-6);
		assertEquals(0.12511003, LuckCalculator.poissonPmf(10, 10), 1.0e-6);
	}

	@Test
	public void poissonCdfMatchesKnownValues()
	{
		assertEquals(0.36787944, LuckCalculator.poissonCdf(0, 1), 1.0e-6);
		assertEquals(0.43347012, LuckCalculator.poissonCdf(3, 4), 1.0e-6);
		assertEquals(0.58303975, LuckCalculator.poissonCdf(10, 10), 1.0e-6);
	}

	/** The continued fraction branch has to stay sane where a naive sum would not. */
	@Test
	public void poissonCdfIsStableAtLargeLambda()
	{
		assertEquals(0.5, LuckCalculator.poissonCdf(5000, 5000), 0.01);
		assertTrue(LuckCalculator.poissonCdf(4000, 5000) < 1.0e-6);
		assertTrue(LuckCalculator.poissonCdf(6000, 5000) > 0.999999);
	}

	@Test
	public void averageAccountScoresAroundFifty()
	{
		double percentile = LuckCalculator.luckPercentile(30.0, 30);
		assertEquals(0.5, percentile, 0.05);
	}

	@Test
	public void moreUniquesIsAlwaysLuckier()
	{
		double previous = -1;

		for (int obtained = 0; obtained <= 40; obtained++)
		{
			double percentile = LuckCalculator.luckPercentile(20.0, obtained);
			assertTrue("percentile should increase with uniques", percentile > previous);
			previous = percentile;
		}
	}

	@Test
	public void spoonedAndCursedEndsAreExtreme()
	{
		assertTrue(LuckCalculator.luckPercentile(10.0, 0) < 0.001);
		assertTrue(LuckCalculator.luckPercentile(10.0, 25) > 0.99);
	}

	@Test
	public void drynessIsTheChanceYouShouldHaveItByNow()
	{
		assertEquals(0.0, LuckCalculator.dryness(0.01, 0), DELTA);
		assertEquals(0.63396766, LuckCalculator.dryness(0.01, 100), 1.0e-6);
		assertEquals(0.99995482, LuckCalculator.dryness(1.0 / 1024, 10240), 1.0e-6);
	}

	@Test
	public void noDataIsNeitherLuckyNorUnlucky()
	{
		assertEquals(0.5, LuckCalculator.luckPercentile(0, 0), DELTA);
	}

	@Test
	public void ratingBandsFollowThePercentile()
	{
		assertEquals(LuckRating.SPOONED_BY_THE_GODS, LuckRating.fromPercentile(0.999));
		assertEquals(LuckRating.AVERAGE, LuckRating.fromPercentile(0.5));
		assertEquals(LuckRating.RNG_HATES_YOU, LuckRating.fromPercentile(0.002));
		assertEquals(50, LuckRating.spoonScore(0.5));
	}
}
