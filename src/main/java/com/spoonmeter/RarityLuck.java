package com.spoonmeter;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rates a source by what it dropped, not just how much.
 *
 * <p>Counting uniques cannot tell a mega rare from a common one: three Tumeken's shadows and three
 * Lightbearers are both "three purples", though the shadow is five times rarer. So each drop is
 * weighted by its rarity, and the score is where the weighted total falls among the totals the same
 * kill counts would usually produce. Counting every drop as 1 is the same calculation with every
 * weight set to 1, which is what this replaces.
 *
 * <p>Each drop is worth one over the root of its expectation, which makes a source's total the sum
 * of its items' standardised deviations. Weighting by the full rate instead, so that a drop is
 * worth one over its chance, tracks rarity harder but hands a source over to its pet: at a thousand
 * Zulrah kills a mutagen would be worth more than four extra uniques, and an account running half
 * again over rate on uniques would still read average. Rooting it keeps a rare find worth several
 * common ones while the plain count still moves the verdict.
 *
 * <p>The weighted total is a sum of differently scaled Poissons, which is skewed enough that a
 * normal approximation is worst in the tails where the interesting verdicts are, so the
 * distribution is simulated instead. The seed is fixed and results are cached, so a source's score
 * never drifts between panel refreshes.
 */
final class RarityLuck
{
	private static final int TRIALS = 50_000;
	private static final long SEED = 7_459_221L;

	/** Above this a per-draw loop costs more than it is worth, and the normal fit is already good. */
	private static final double KNUTH_LIMIT = 30.0;

	private static final Map<String, Double> CACHE = new ConcurrentHashMap<>();

	private RarityLuck()
	{
	}

	/**
	 * @param expected  what each item's kill count should have produced
	 * @param collected how many of each the log actually shows
	 * @return the share of accounts at the same kill counts this one is luckier than
	 */
	static double percentile(double[] expected, int[] collected)
	{
		if (expected.length == 0)
		{
			return 0.5;
		}

		return CACHE.computeIfAbsent(key(expected, collected), k -> simulate(expected, collected));
	}

	private static double simulate(double[] expected, int[] collected)
	{
		// A rarer item is worth more. The kill count is a shared factor between one over the chance
		// and one over the expectation, and a shared factor cancels out of a percentile.
		double[] weight = new double[expected.length];
		double target = 0;

		for (int i = 0; i < expected.length; i++)
		{
			if (expected[i] > 0)
			{
				weight[i] = 1.0 / Math.sqrt(expected[i]);
				target += collected[i] * weight[i];
			}
		}

		Random random = new Random(SEED);
		double tolerance = 1.0e-9 * Math.max(1.0, target);
		int below = 0;
		int level = 0;

		for (int trial = 0; trial < TRIALS; trial++)
		{
			double total = 0;

			for (int i = 0; i < expected.length; i++)
			{
				if (expected[i] > 0)
				{
					total += poisson(random, expected[i]) * weight[i];
				}
			}

			if (total < target - tolerance)
			{
				below++;
			}
			else if (total <= target + tolerance)
			{
				level++;
			}
		}

		// Half credit for a tie, so a source nobody has touched sits at the middle rather than the
		// bottom, the same convention the counting version used.
		return (below + 0.5 * level) / TRIALS;
	}

	private static int poisson(Random random, double lambda)
	{
		if (lambda > KNUTH_LIMIT)
		{
			return Math.max(0, (int) Math.round(lambda + Math.sqrt(lambda) * random.nextGaussian()));
		}

		double limit = Math.exp(-lambda);
		double product = random.nextDouble();
		int count = 0;

		while (product > limit)
		{
			count++;
			product *= random.nextDouble();
		}

		return count;
	}

	private static String key(double[] expected, int[] collected)
	{
		StringBuilder key = new StringBuilder(expected.length * 16);

		for (int i = 0; i < expected.length; i++)
		{
			// Rounded, so a redraw that recomputes the same expectation reuses the cached answer.
			key.append(Math.round(expected[i] * 1.0e6)).append(':').append(collected[i]).append(';');
		}

		return key.toString();
	}
}
