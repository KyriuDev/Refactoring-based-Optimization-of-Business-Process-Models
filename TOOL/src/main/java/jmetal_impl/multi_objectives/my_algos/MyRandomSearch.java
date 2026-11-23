package jmetal_impl.multi_objectives.my_algos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import jmetal_impl.RefactoringProblem;
import main.Main;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.solution.Solution;
import org.uma.jmetal.util.archive.impl.NonDominatedSolutionListArchive;
import other.Utils;
import refactoring.legacy.exceptions.BadDependencyException;
import refactoring.step_by_step.OptimizationCriteria;
import refactoring.step_by_step.TreeNode;

/**
 * This class implements a simple random search algorithm.
 *
 * @author Antonio J. Nebro
 */
@SuppressWarnings("serial")
public class MyRandomSearch<S extends Solution<?>> implements Algorithm<List<S>>
{
	protected final long startTime;
	private long lastDumpTime;
	private Problem<S> problem;
	private int maxEvaluations;
	NonDominatedSolutionListArchive<S> nonDominatedArchive;
	private final ArrayList<Long> iterationsTime;
	private final TreeNode initialNode;
	private final OptimizationCriteria optimizationCriteria;
	private final HashMap<Long, Double> dumpedStepsInformation;

	/**
	 * Constructor
	 */
	public MyRandomSearch(Problem<S> problem, int maxEvaluations,
						  final TreeNode initialNode,
						  final OptimizationCriteria optimizationCriteria)
	{
		this.problem = problem;
		this.maxEvaluations = maxEvaluations;
		nonDominatedArchive = new NonDominatedSolutionListArchive<S>();
		this.iterationsTime = new ArrayList<>();
		this.initialNode = initialNode;
		this.optimizationCriteria = optimizationCriteria;
		this.dumpedStepsInformation = new HashMap<>();
		this.startTime = System.nanoTime();
	}

	/* Getter */
	public int getMaxEvaluations()
	{
		return maxEvaluations;
	}

	@Override
	public void run()
	{
		for (int i = 1; i <= maxEvaluations; i++)
		{
			System.out.println("Starting iteration n°" + i);
			iterationsTime.add(System.nanoTime());

			S newSolution = problem.createSolution();
			problem.evaluate(newSolution);
			nonDominatedArchive.add(newSolution);

			final long currentTimeStamp = System.nanoTime();
			final long lastTimeStamp = iterationsTime.get(iterationsTime.size() - 1);
			iterationsTime.add(currentTimeStamp);
			System.out.println("Iteration n°" + i + " took " + Utils.nanoSecToReadable(currentTimeStamp - lastTimeStamp));
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
						System.out.println("Iteration n°" + i + " has a best optimisation of " + (bestSolutionSoFar * 100) + "%.");
					}
				}
			}

			if (this.startTime + Main.SOFT_MAX_DURATION_NANO <= currentTimeStamp)
			{
				break;
			}
		}
	}

	@Override
	public List<S> result()
	{
		return nonDominatedArchive.solutions();
	}

	@Override
	public String name()
	{
		return "RS";
	}

	@Override
	public String description()
	{
		return "Multi-objective random search algorithm";
	}

	public HashMap<Long, Double> dumpedStepsInformation()
	{
		return this.dumpedStepsInformation;
	}
}
