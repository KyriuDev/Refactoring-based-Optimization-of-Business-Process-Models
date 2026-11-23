package jmetal_impl.multi_objectives.my_algos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import bpmn.graph.Node;
import jmetal_impl.RefactoringDominanceComparator;
import jmetal_impl.RefactoringProblem;
import jmetal_impl.RefactoringSolution;
import main.Main;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.mosa.cooling.CoolingScheme;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.solution.Solution;
import org.uma.jmetal.util.archive.BoundedArchive;
import org.uma.jmetal.util.comparator.dominanceComparator.DominanceComparator;
import org.uma.jmetal.util.comparator.dominanceComparator.impl.DefaultDominanceComparator;
import org.uma.jmetal.util.errorchecking.Check;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;
import other.Utils;
import refactoring.legacy.exceptions.BadDependencyException;
import refactoring.step_by_step.OptimizationCriteria;
import refactoring.step_by_step.TreeNode;

/**
 * @author Antonio J. Nebro
 * @version 1.0
 * This class implements a multi-objective simulated annealing algorithm.
 */
public class MyMOSA<S extends Solution<?>> implements Algorithm<List<S>>
{
	protected final long startTime;
	private long lastDumpTime;
	private final ArrayList<Long> iterationsTime;
	private final TreeNode initialNode;
	private final OptimizationCriteria optimizationCriteria;
	private final HashMap<Long, Double> dumpedStepsInformation;
	protected S currentSolution;
	protected Problem<S> problem;
	protected MutationOperator<S> mutationOperator;

	protected int maxEvaluations;
	protected int evaluations;

	protected BoundedArchive<S> archive;
	protected Comparator<S> comparator;

	protected static final double minimumTemperature = 0.000001;
	protected double temperature;

	protected CoolingScheme coolingScheme;
	protected int numberOfWorstAcceptedSolutions = 0;

	/**
	 * Constructor
	 */
	public MyMOSA(
			S initialSolution,
			Problem<S> problem,
			int maxEvaluations,
			BoundedArchive<S> archive,
			MutationOperator<S> mutationOperator,
			double initialTemperature,
			CoolingScheme coolingScheme,
			DominanceComparator<S> comparator,
			TreeNode initialNode,
			OptimizationCriteria optimizationCriteria)
	{
		this.problem = problem;
		this.currentSolution = initialSolution;
		this.maxEvaluations = maxEvaluations;
		this.archive = archive;
		this.mutationOperator = mutationOperator;

		this.temperature = initialTemperature;
		this.coolingScheme = coolingScheme;

		this.comparator = comparator;
		this.initialNode = initialNode;
		this.optimizationCriteria = optimizationCriteria;
		this.dumpedStepsInformation = new HashMap<>();
		this.startTime = System.nanoTime();
		this.lastDumpTime = this.startTime;
		iterationsTime = new ArrayList<>();
	}

	public HashMap<Long, Double> dumpedStepsInformation()
	{
		return this.dumpedStepsInformation;
	}

	/* Getters */
	public BoundedArchive<S> getArchive()
	{
		return archive;
	}

	public int getMaxEvaluations()
	{
		return maxEvaluations;
	}

	public MutationOperator<S> getMutationOperator()
	{
		return mutationOperator;
	}

	protected void initProgress()
	{
		evaluations = 1;
		System.out.println("Starting iteration n°" + evaluations);
		iterationsTime.add(System.nanoTime());
	}

	protected void updateProgress()
	{
		final long currentTimeStamp = System.nanoTime();
		final long lastTimeStamp = iterationsTime.get(iterationsTime.size() - 1);
		iterationsTime.add(currentTimeStamp);
		System.out.println("Iteration n°" + evaluations + " took " + Utils.nanoSecToReadable(currentTimeStamp - lastTimeStamp));
		System.out.println();

		if (this.lastDumpTime + Main.DUMP_FREQUENCY < currentTimeStamp)
		{
			this.lastDumpTime = currentTimeStamp;

			if (this.result().isEmpty())
			{
				//No solution
				this.dumpedStepsInformation.put(currentTimeStamp, null);
			}
			else
			{
				final double bestSolutionSoFar;

				try
				{
					bestSolutionSoFar = Utils.getBestSolutionSoFar(this.result(), this.initialNode, this.optimizationCriteria, (RefactoringProblem) this.problem);
				}
				catch (BadDependencyException e)
				{
					throw new RuntimeException(e);
				}

				if (bestSolutionSoFar == -1d)
				{
					//No solution
					this.dumpedStepsInformation.put(currentTimeStamp, null);
				}
				else
				{
					this.dumpedStepsInformation.put(currentTimeStamp, bestSolutionSoFar);
					System.out.println("Iteration n°" + evaluations + " has a best optimisation of " + (bestSolutionSoFar * 100) + "%.");
				}
			}
		}

		evaluations++;
		System.out.println("Starting iteration n°" + (evaluations));
	}

	protected boolean isStoppingConditionReached()
	{
		return this.startTime + Main.SOFT_MAX_DURATION_NANO <= System.nanoTime();
	}

	@Override
	public void run()
	{
		initProgress();
		while (!isStoppingConditionReached())
		{
			//System.out.println("Nb solutions: " + archive.size());
			S mutatedSolution = mutationOperator.execute((S) currentSolution.copy());
			problem.evaluate(mutatedSolution);

			int flag = comparator.compare(currentSolution, mutatedSolution);

			if (flag > 0)
			{
				//System.out.println("Flag > 0 !");
				currentSolution = mutatedSolution;
				archive.add(mutatedSolution);
			}
			else if (flag == 0)
			{
				//System.out.println("Flag = 0 !");
				if (archive.add(mutatedSolution))
				{
					if (archive.comparator().compare(currentSolution, mutatedSolution) > 0)
					{
						currentSolution = mutatedSolution;
					}
				}
			}
			else
			{
				//System.out.println("Flag < 0 :-(");
				double acceptanceProbability = computeAcceptanceProbability(currentSolution, mutatedSolution, temperature);

				if (acceptanceProbability > JMetalRandom.getInstance().nextDouble())
				{
					currentSolution = mutatedSolution;
					numberOfWorstAcceptedSolutions++;
				}
			}

			temperature = coolingScheme.updateTemperature(temperature, evaluations);

			updateProgress();
		}
	}

	protected double computeAcceptanceProbability(S currentSolution, S mutatedSolution, double temperature)
	{
		double value = 0.0;

		for (int i = 0; i < currentSolution.objectives().length; i++)
		{
			value += (mutatedSolution.objectives()[i] - currentSolution.objectives()[i])
					/ Math.max(temperature, minimumTemperature);
		}

		//System.out.println("Value: " + value);

		double probability = Math.exp(-1.0 * value);
		probability = Math.min(probability, 1d);

		return probability;
	}

	public double getNumberOfWorstAcceptedSolutions()
	{
		return numberOfWorstAcceptedSolutions;
	}

	@Override
	public List<S> result()
	{
		return archive.solutions();
	}

	@Override
	public String name()
	{
		return "MOSA";
	}

	@Override
	public String description()
	{
		return "Multi-objective Simulated Annealing";
	}
}
