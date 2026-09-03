package com.spoonmeter;

/**
 * The statistics behind the rating.
 *
 * <p>Every unique in a collection log page is an independent Bernoulli trial repeated once per kill,
 * so the number of uniques a player owns is the sum of many independent binomials with tiny p and
 * large n. That sum is very well approximated by a Poisson variable with
 * {@code lambda = sum(kc * p_i)}, which is what we use: it needs no per-item bookkeeping, stays
 * numerically sane at any KC, and is exact enough that the difference never moves a rating band.
 *
 * <p>"Luck" is then the mid-p percentile of your observed unique count under that Poisson: the
 * fraction of accounts with your exact KC who would have fewer uniques than you.
 */
public final class LuckCalculator
{
	private LuckCalculator()
	{
	}

	/**
	 * Fraction of accounts (0..1) at the same KC that would be doing worse than this player.
	 * 0.5 is dead average, 1.0 is spooned beyond belief, 0.0 is a war crime.
	 *
	 * <p>Uses the mid-p convention (half of the probability mass at the observed value counts as
	 * "worse") so that the score is centred on 0.5 for an average account instead of being biased
	 * upwards by the discreteness of the distribution.
	 */
	public static double luckPercentile(double lambda, int observed)
	{
		if (lambda <= 0 || observed < 0)
		{
			return 0.5;
		}

		double below = observed == 0 ? 0.0 : regularizedGammaQ(observed, lambda);
		double at = poissonPmf(observed, lambda);
		return clamp(below + 0.5 * at);
	}

	/** P(X = k) for X ~ Poisson(lambda). */
	public static double poissonPmf(int k, double lambda)
	{
		if (k < 0 || lambda < 0)
		{
			return 0.0;
		}

		if (lambda == 0)
		{
			return k == 0 ? 1.0 : 0.0;
		}

		return Math.exp(-lambda + k * Math.log(lambda) - logGamma(k + 1.0));
	}

	/** P(X &lt;= k) for X ~ Poisson(lambda). */
	public static double poissonCdf(int k, double lambda)
	{
		if (k < 0)
		{
			return 0.0;
		}

		if (lambda <= 0)
		{
			return 1.0;
		}

		return clamp(regularizedGammaQ(k + 1.0, lambda));
	}

	/**
	 * Probability of having seen at least one of an item with per-kill chance {@code p} after
	 * {@code kc} kills. Also read as "the share of players who would already have it by now",
	 * which is the number that makes a dry streak hurt.
	 */
	public static double dryness(double p, int kc)
	{
		if (p <= 0 || kc <= 0)
		{
			return 0.0;
		}

		return clamp(1.0 - Math.pow(1.0 - p, kc));
	}

	// --- incomplete gamma -----------------------------------------------------------------
	// P(X <= k) for Poisson(lambda) equals the regularized upper incomplete gamma Q(k + 1, lambda),
	// which stays accurate for large lambda where a naive term-by-term sum would drown in rounding.

	static double regularizedGammaQ(double a, double x)
	{
		if (x <= 0)
		{
			return 1.0;
		}

		if (a <= 0)
		{
			return 0.0;
		}

		return x < a + 1.0 ? 1.0 - gammaSeries(a, x) : gammaContinuedFraction(a, x);
	}

	/** Series expansion for the regularized lower incomplete gamma P(a, x); converges fast for x < a+1. */
	private static double gammaSeries(double a, double x)
	{
		double ap = a;
		double sum = 1.0 / a;
		double del = sum;

		for (int n = 0; n < 10000; n++)
		{
			ap += 1.0;
			del *= x / ap;
			sum += del;

			if (Math.abs(del) < Math.abs(sum) * 1.0e-15)
			{
				break;
			}
		}

		return sum * Math.exp(-x + a * Math.log(x) - logGamma(a));
	}

	/** Lentz's modified continued fraction for Q(a, x); converges fast for x >= a+1. */
	private static double gammaContinuedFraction(double a, double x)
	{
		final double tiny = 1.0e-300;

		double b = x + 1.0 - a;
		double c = 1.0 / tiny;
		double d = 1.0 / b;
		double h = d;

		for (int i = 1; i < 10000; i++)
		{
			double an = -i * (i - a);
			b += 2.0;

			d = an * d + b;
			if (Math.abs(d) < tiny)
			{
				d = tiny;
			}

			c = b + an / c;
			if (Math.abs(c) < tiny)
			{
				c = tiny;
			}

			d = 1.0 / d;
			double del = d * c;
			h *= del;

			if (Math.abs(del - 1.0) < 1.0e-15)
			{
				break;
			}
		}

		return h * Math.exp(-x + a * Math.log(x) - logGamma(a));
	}

	/** Lanczos approximation, good to ~15 significant digits for x > 0. */
	static double logGamma(double x)
	{
		final double[] coefficients = {
			76.18009172947146, -86.50532032941677, 24.01409824083091,
			-1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5
		};

		double y = x;
		double tmp = x + 5.5;
		tmp -= (x + 0.5) * Math.log(tmp);

		double ser = 1.000000000190015;
		for (double coefficient : coefficients)
		{
			ser += coefficient / ++y;
		}

		return -tmp + Math.log(2.5066282746310005 * ser / x);
	}

	private static double clamp(double v)
	{
		if (Double.isNaN(v))
		{
			return 0.5;
		}

		return Math.max(0.0, Math.min(1.0, v));
	}
}
