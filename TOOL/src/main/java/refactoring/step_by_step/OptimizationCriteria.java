package refactoring.step_by_step;

import other.Pair;

import java.util.HashMap;

public class OptimizationCriteria
{
	private final HashMap<String, Pair<Integer, Double>> resourcesToOptimizeAndTargetUsageAndWeight;
	private final Double costOptimizationWeight; //A value of 0 means that the cost should not be optimized
	private final Double timeOptimizationWeight; //A value of 0 means that the time should not be optimized

	public OptimizationCriteria(final HashMap<String, Pair<Integer, Double>> resourcesToOptimizeAndTargetUsageAndWeight,
								final Integer costOptimizationWeight,
								final Integer timeOptimizationWeight)
	{
		this.resourcesToOptimizeAndTargetUsageAndWeight = resourcesToOptimizeAndTargetUsageAndWeight;
		this.costOptimizationWeight =
			costOptimizationWeight == 100 ?
				1d :
				costOptimizationWeight < 10 ?
					Double.valueOf("0.0" + costOptimizationWeight) :
					Double.valueOf("0." + costOptimizationWeight); //Convert percentage to double
		this.timeOptimizationWeight =
			timeOptimizationWeight == 100 ?
				1d :
				timeOptimizationWeight < 10 ?
					Double.valueOf("0.0" + timeOptimizationWeight) :
					Double.valueOf("0." + timeOptimizationWeight); //Convert percentage to double
	}

	public OptimizationCriteria(final HashMap<String, Pair<Integer, Double>> resourcesToOptimizeAndTargetUsageAndWeight)
	{
		this.resourcesToOptimizeAndTargetUsageAndWeight = resourcesToOptimizeAndTargetUsageAndWeight;
		this.costOptimizationWeight = 0d;
		this.timeOptimizationWeight = 0d;
	}

	public HashMap<String, Pair<Integer, Double>> getResourcesToOptimizeAndTargetUsageAndWeight()
	{
		return resourcesToOptimizeAndTargetUsageAndWeight;
	}

	public Double costOptimizationWeight()
	{
		return costOptimizationWeight;
	}

	public Double timeOptimizationWeight()
	{
		return timeOptimizationWeight;
	}

	public boolean costMustBeOptimized()
	{
		return this.costOptimizationWeight != 0d;
	}

	public boolean aetMustBeOptimized()
	{
		return this.timeOptimizationWeight != 0d;
	}

	public int nbCriteria()
	{
		return this.resourcesToOptimizeAndTargetUsageAndWeight.size() +
				(this.costMustBeOptimized() ? 1 : 0) +
				(this.aetMustBeOptimized() ? 1 : 0);
	}
}
