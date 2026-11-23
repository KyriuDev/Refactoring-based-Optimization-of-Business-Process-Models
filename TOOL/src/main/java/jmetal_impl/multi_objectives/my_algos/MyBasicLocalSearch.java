package jmetal_impl.multi_objectives.my_algos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import com.fuzzylite.Op;
import jmetal_impl.RefactoringProblem;
import main.Main;
import org.uma.jmetal.algorithm.impl.AbstractLocalSearch;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.solution.Solution;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;
import other.Utils;
import refactoring.legacy.exceptions.BadDependencyException;
import refactoring.step_by_step.OptimizationCriteria;
import refactoring.step_by_step.TreeNode;

public class MyBasicLocalSearch<S extends Solution<?>> extends AbstractLocalSearch<S>
{

	private final long startTime;
	private long lastDumpTime;
	private S initialSolution;
	private int evaluations;
	private int maxEvaluations;
	private MutationOperator<S> mutationOperator;
	private Comparator<S> comparator;
	private Problem<S> problem;
	private final ArrayList<Long> iterationsTime;
	private final TreeNode initialNode;
	private final OptimizationCriteria optimizationCriteria;
	private final HashMap<Long, Double> dumpedStepsInformation;

	public MyBasicLocalSearch(S initialSolution, int maxEvaluations,
							  Problem<S> problem, MutationOperator<S> mutationOperator, Comparator<S> comparator,
							  final TreeNode initialNode,
							  final OptimizationCriteria optimizationCriteria)
	{
		this.initialSolution = initialSolution;
		this.maxEvaluations = maxEvaluations;
		this.mutationOperator = mutationOperator;
		this.comparator = comparator;
		this.problem = problem;
		this.initialNode = initialNode;
		this.optimizationCriteria = optimizationCriteria;
		this.iterationsTime = new ArrayList<>();
		this.dumpedStepsInformation = new HashMap<>();
		this.startTime = System.nanoTime();
		this.lastDumpTime = this.startTime;
	}

	@Override
	protected S setCurrentSolution()
	{
		return initialSolution;
	}

	@Override
	protected void initProgress()
	{
		evaluations = 1;
		System.out.println("Starting iteration n°" + evaluations);
		iterationsTime.add(System.nanoTime());
	}

	@Override
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

			if (this.result() == null)
			{
				//No solution
				this.dumpedStepsInformation.put(currentTimeStamp, null);
			}
			else
			{
				final double bestSolutionSoFar;

				try
				{
					bestSolutionSoFar = Utils.getBestSolutionSoFar(Collections.singletonList(this.result()), this.initialNode, this.optimizationCriteria, (RefactoringProblem) this.problem);
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

	@Override
	protected boolean isStoppingConditionReached()
	{
		return this.startTime + Main.SOFT_MAX_DURATION_NANO <= System.nanoTime();
	}

	@Override
	protected S updateCurrentSolution(S currentSolution)
	{
		S newSolution = mutationOperator.execute((S) currentSolution.copy());
		problem.evaluate(newSolution);

		int result = comparator.compare(newSolution, currentSolution);
		if ((result == -1) || ((result == 0) && (
				JMetalRandom.getInstance().getRandomGenerator().nextDouble() < 0.5)))
		{
			currentSolution = newSolution;
		}

		return currentSolution;
	}

	@Override
	public String name()
	{
		return "Basic local search";
	}

	@Override
	public String description()
	{
		return "Basic local search";
	}

	public HashMap<Long, Double> dumpedStepsInformation()
	{
		return this.dumpedStepsInformation;
	}
}
