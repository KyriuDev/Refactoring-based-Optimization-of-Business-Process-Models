package jmetal_impl.multi_objectives.my_algos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import jmetal_impl.RefactoringProblem;
import main.Main;
import org.uma.jmetal.algorithm.impl.AbstractGeneticAlgorithm;
import org.uma.jmetal.algorithm.multiobjective.espea.ESPEABuilder;
import org.uma.jmetal.algorithm.multiobjective.espea.util.EnergyArchive;
import org.uma.jmetal.algorithm.multiobjective.espea.util.EnergyArchive.ReplacementStrategy;
import org.uma.jmetal.algorithm.multiobjective.espea.util.ScalarizationWrapper;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.operator.selection.impl.RandomSelection;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.solution.Solution;
import org.uma.jmetal.util.evaluator.SolutionListEvaluator;
import org.uma.jmetal.util.evaluator.impl.SequentialSolutionListEvaluator;
import other.Utils;
import refactoring.legacy.exceptions.BadDependencyException;
import refactoring.step_by_step.OptimizationCriteria;
import refactoring.step_by_step.TreeNode;

/**
 * Implementation of the Electrostatic Potential Energy Evolutionary Algorithm
 * (ESPEA) from the paper "Obtaining Optimal Pareto Front Approximations using
 * Scalarized Preference Information" by M. Braun et al.
 *
 * <p>
 * The algorithm generates preference-biased Pareto front approximations that
 * cover the entire front but focus more solutions in those regions that are
 * interesting to the decision maker. Preferences are presented to the algorithm
 * in the form of a scalarization function (value function) that maps the vector
 * of objective to a real value. Smaller values are deemed to indicate higher
 * desirability to comply with minimization.
 *
 * <p>
 * If no scalarized preference is specified, uniform preferences are assumed and
 * ESPEA generates a uniform approximation of the Pareto front.
 *
 * @author Marlon Braun
 */
@SuppressWarnings("serial")
public class MyESPEA<S extends Solution<?>> extends AbstractGeneticAlgorithm<S, List<S>>
{
	protected final long startTime;
	private long lastDumpTime;
	private final ArrayList<Long> iterationsTime;
	private final TreeNode initialNode;
	private final OptimizationCriteria optimizationCriteria;
	private final HashMap<Long, Double> dumpedStepsInformation;

	/**
	 * The number of function evaluations that have been executed so far.
	 */
	protected int evaluations;

	/**
	 * Maximum number of functions evaluations that are executed.
	 */
	protected int maxEvaluations;

	/**
	 * ESPEA uses two different crossover operators depending on the current
	 * archive size. If the archive is not full, it uses the crossover operator
	 * provided by {@link #getCrossoverOperator()}. If the archive is full,
	 * {@link #fullArchiveCrossoverOperator} is used.
	 */
	protected CrossoverOperator<S> fullArchiveCrossoverOperator;

	/**
	 * Evaluates the solutions
	 */
	protected final SolutionListEvaluator<S> evaluator;

	/**
	 * An archive of nondominated solutions that approximates the energy minimum
	 * state based on the chosen scalarization function.
	 */
	protected final EnergyArchive<S> archive;

	/**
	 * Constructor for setting all parameters of ESPEA.
	 */

	public MyESPEA(final ESPEABuilder<S> builder,
				   Problem<S> problem,
				   CrossoverOperator<S> crossoverOperator,
				   MutationOperator<S> mutationOperator,
				   final TreeNode initialNode,
				   final OptimizationCriteria optimizationCriteria)
	{
		super(problem);
		this.maxEvaluations = builder.getMaxEvaluations();
		setMaxPopulationSize(builder.getPopulationSize());
		this.crossoverOperator = crossoverOperator;
		this.fullArchiveCrossoverOperator = crossoverOperator;
		this.mutationOperator = mutationOperator;
		this.selectionOperator = new RandomSelection<>();
		this.archive = new EnergyArchive<>(builder.getPopulationSize(), new ScalarizationWrapper(ScalarizationWrapper.ScalarizationType.UNIFORM), true, ReplacementStrategy.LARGEST_DIFFERENCE);
		this.evaluator = new SequentialSolutionListEvaluator<>();
		this.iterationsTime = new ArrayList<>();
		this.initialNode = initialNode;
		this.optimizationCriteria = optimizationCriteria;
		this.dumpedStepsInformation = new HashMap<>();
		this.startTime = System.nanoTime();
		this.lastDumpTime = this.startTime;
	}

	@Override
	public String name()
	{
		return "ESPEA";
	}

	@Override
	public String description()
	{
		return "Electrostatic Potential Energy Evolutionary Algorithms";
	}

	@Override
	protected void initProgress()
	{
		evaluations = getMaxPopulationSize();
		// Initialize archive
		population.forEach(s -> archive.add(s));
		System.out.println("Starting iteration n°" + (evaluations - getMaxPopulationSize() + 1));
		iterationsTime.add(System.nanoTime());
	}

	@Override
	protected void updateProgress()
	{
		// ESPEA is steady-state
		final long currentTimeStamp = System.nanoTime();
		final long lastTimeStamp = iterationsTime.get(iterationsTime.size() - 1);
		iterationsTime.add(currentTimeStamp);
		System.out.println("Iteration n°" + (evaluations - getMaxPopulationSize() + 1) + " took " + Utils.nanoSecToReadable(currentTimeStamp - lastTimeStamp));
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
					bestSolutionSoFar = Utils.getBestSolutionSoFar(this.result(), this.initialNode, this.optimizationCriteria, (RefactoringProblem) this.getProblem());
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
					System.out.println("Iteration n°" + (evaluations - getMaxPopulationSize() + 1) + " has a best optimisation of " + (bestSolutionSoFar * 100) + "%.");
				}
			}
		}

		evaluations++;
		System.out.println("Starting iteration n°" + (evaluations - getMaxPopulationSize() + 1));
	}

	@Override
	protected boolean isStoppingConditionReached()
	{
		return this.startTime + Main.SOFT_MAX_DURATION_NANO <= System.nanoTime();
	}

	@Override
	protected List<S> evaluatePopulation(List<S> population)
	{
		population = evaluator.evaluate(population, getProblem());
		return population;
	}

	@Override
	protected List<S> replacement(List<S> population, List<S> offspringPopulation)
	{
		// population and archive are always in sync
		offspringPopulation.forEach(s -> archive.add(s));
		return archive.solutions();
	}

	@Override
	public List<S> result()
	{
		return archive.solutions();
	}

	/*
	 * Steady-state implementation of mating selection.
	 *
	 * @see
	 * org.uma.jmetal.algorithm.impl.AbstractGeneticAlgorithm#selection(java.
	 * util.List)
	 */
	@Override
	protected List<S> selection(List<S> population)
	{
		// Chosen operator depends on archive size
		CrossoverOperator<S> chosenOperator = archive.isFull() ? fullArchiveCrossoverOperator : crossoverOperator;

		List<S> matingPopulation = new ArrayList<>(chosenOperator.numberOfRequiredParents());

		for (int i = 0; i < chosenOperator.numberOfRequiredParents(); i++)
		{
			S solution = selectionOperator.execute(population);
			matingPopulation.add(solution);
		}

		return matingPopulation;
	}

	/*
	 * Steady-state implementation of reproduction.
	 *
	 * @see
	 * org.uma.jmetal.algorithm.impl.AbstractGeneticAlgorithm#reproduction(java.
	 * util.List)
	 */
	@Override
	protected List<S> reproduction(List<S> population)
	{
		List<S> offspring = crossoverOperator.execute(population);
		mutationOperator.execute(offspring.get(0));

		return offspring;
	}

	public HashMap<Long, Double> dumpedStepsInformation()
	{
		return this.dumpedStepsInformation;
	}
}
