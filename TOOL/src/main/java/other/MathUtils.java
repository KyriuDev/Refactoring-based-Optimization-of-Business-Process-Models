package other;

import java.util.Collection;

public class MathUtils
{
	private MathUtils()
	{

	}

	public static Double getMaxDouble(Collection<Double> doubles)
	{
		Double maxDouble = null;

		for (Double currentDouble : doubles)
		{
			if (maxDouble == null)
			{
				maxDouble = currentDouble;
			}
			else
			{
				if (currentDouble > maxDouble)
				{
					maxDouble = currentDouble;
				}
			}
		}

		return maxDouble;
	}

	public static Double getMinDouble(Collection<Double> doubles)
	{
		Double minDouble = null;

		for (Double currentDouble : doubles)
		{
			if (minDouble == null)
			{
				minDouble = currentDouble;
			}
			else
			{
				if (currentDouble < minDouble)
				{
					minDouble = currentDouble;
				}
			}
		}

		return minDouble;
	}

	public static Double getDoubleAverage(Collection<Double> doubles)
	{
		Double average = 0d;

		for (Double currentDouble : doubles)
		{
			average += currentDouble;
		}

		return average / (double) doubles.size();
	}
}
