package simulator.bpmn_to_model_input;

import org.apache.commons.math3.distribution.*;
import other.Utils;

public class DistributionUtils
{
	private DistributionUtils()
	{

	}

	public static AbstractRealDistribution valuesToDistribution(final String rawValue)
	{
		final String type = rawValue.substring(rawValue.indexOf(':') + 1, rawValue.indexOf(',')).trim();
		final String line2 = rawValue.substring(rawValue.indexOf(',') + 1);
		final String param1 = line2.substring(0, line2.indexOf(',')).trim();
		final String line3 = line2.substring(line2.indexOf(',') + 1);
		final String param2 = line3.trim();

		return DistributionUtils.valuesToDistribution(type, param1, param2);
	}

	public static AbstractRealDistribution valuesToDistribution(final String value,
																final double param1,
																final double param2)
	{
		if (value.equals(DistributionType.CONSTANT.label()))
		{
			return new ConstantRealDistribution(param1);
		}
		else if (value.equals(DistributionType.NORMAL.label()))
		{
			return new NormalDistribution(param1, param2);
		}
		else if (value.equals(DistributionType.UNIFORM.label()))
		{
			return new UniformRealDistribution(param1, param2 + Double.MIN_VALUE);
		}
		else
		{
			throw new UnsupportedOperationException();
		}
	}

	public static AbstractRealDistribution valuesToDistribution(final String value,
																final String param1Str,
																final String param2Str)
	{
		return DistributionUtils.valuesToDistribution(value, Double.parseDouble(param1Str), Double.parseDouble(param2Str));
	}

	public static int averageIntegerValue(final AbstractRealDistribution distribution)
	{
		if(distribution instanceof ConstantRealDistribution)
		{
			return (int) distribution.getNumericalMean();
		}
		else if (distribution instanceof NormalDistribution)
		{
			return (int) ((NormalDistribution) distribution).getMean();
		}
		else if (distribution instanceof UniformRealDistribution)
		{
			return (int) ((distribution.getSupportUpperBound() - distribution.getSupportLowerBound()) / 2);
		}
		else
		{
			throw new UnsupportedOperationException();
		}
	}

	public static String distributionName(final AbstractRealDistribution distribution)
	{
		if (distribution instanceof ConstantRealDistribution)
		{
			return DistributionType.CONSTANT.label();
		}
		else if (distribution instanceof NormalDistribution)
		{
			return DistributionType.NORMAL.label();
		}
		else if (distribution instanceof UniformRealDistribution)
		{
			return DistributionType.UNIFORM.label();
		}
		else
		{
			throw new UnsupportedOperationException();
		}
	}

	public static double distributionFirstParam(final AbstractRealDistribution distribution)
	{
		if (distribution instanceof ConstantRealDistribution)
		{
			return distribution.getNumericalMean();
		}
		else if (distribution instanceof NormalDistribution)
		{
			return ((NormalDistribution) distribution).getMean();
		}
		else if (distribution instanceof UniformRealDistribution)
		{
			return distribution.getSupportLowerBound();
		}
		else
		{
			throw new UnsupportedOperationException();
		}
	}

	public static double distributionSecondParam(final AbstractRealDistribution distribution)
	{
		if (distribution instanceof ConstantRealDistribution)
		{
			return 0d;
		}
		else if (distribution instanceof NormalDistribution)
		{
			return ((NormalDistribution) distribution).getStandardDeviation();
		}
		else if (distribution instanceof UniformRealDistribution)
		{
			return distribution.getSupportUpperBound();
		}
		else
		{
			throw new UnsupportedOperationException();
		}
	}

	public static String distributionToReadable(final AbstractRealDistribution distribution)
	{
		if (distribution instanceof ConstantRealDistribution)
		{
			return "const, " + Utils.round(distribution.getNumericalMean(), 2) + ", 0";
		}
		else if (distribution instanceof NormalDistribution)
		{
			return "norm, " + Utils.round(((NormalDistribution) distribution).getMean(), 2) + ", " + Utils.round(((NormalDistribution) distribution).getStandardDeviation(), 2);
		}
		else if (distribution instanceof UniformRealDistribution)
		{
			return "unif, " + Utils.round(distribution.getSupportLowerBound(), 2) + ", " + Utils.round(distribution.getSupportUpperBound(), 2);
		}
		else
		{
			throw new UnsupportedOperationException();
		}
	}
}
