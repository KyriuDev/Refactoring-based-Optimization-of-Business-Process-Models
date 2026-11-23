package jmetal_impl.interruptibility;

public class AlgorithmParameters
{
	private final int maxIterations;
	private final int maxEvaluations;
	private final int populationSize;
	private final int nbProcessesAlreadyRefactored;
	private final int nbProcessesToRefactor;

	public AlgorithmParameters(final int maxIterations,
							   final int maxEvaluations,
							   final int populationSize,
							   final int nbProcessesAlreadyRefactored,
							   final int nbProcessesToRefactor)
	{
		this.maxIterations = maxIterations;
		this.maxEvaluations = maxEvaluations;
		this.populationSize = populationSize;
		this.nbProcessesAlreadyRefactored = nbProcessesAlreadyRefactored;
		this.nbProcessesToRefactor = nbProcessesToRefactor;
	}

	public int getMaxIterations()
	{
		return maxIterations;
	}

	public int getMaxEvaluations()
	{
		return maxEvaluations;
	}

	public int getPopulationSize()
	{
		return populationSize;
	}

	public int getNbProcessesAlreadyRefactored()
	{
		return nbProcessesAlreadyRefactored;
	}

	public int getNbProcessesToRefactor()
	{
		return nbProcessesToRefactor;
	}
}
