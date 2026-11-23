package jmetal_impl.multi_objectives.my_algos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import jmetal_impl.RefactoringProblem;
import main.Main;
import org.uma.jmetal.algorithm.impl.AbstractGeneticAlgorithm;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAIIBuilder;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.operator.selection.SelectionOperator;
import org.uma.jmetal.operator.selection.impl.RankingAndCrowdingSelection;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.solution.Solution;
import org.uma.jmetal.util.SolutionListUtils;
import org.uma.jmetal.util.comparator.dominanceComparator.DominanceComparator;
import org.uma.jmetal.util.comparator.dominanceComparator.impl.DefaultDominanceComparator;
import org.uma.jmetal.util.evaluator.SolutionListEvaluator;
import other.Utils;
import refactoring.legacy.exceptions.BadDependencyException;
import refactoring.step_by_step.OptimizationCriteria;
import refactoring.step_by_step.TreeNode;

/**
 * @author Antonio J. Nebro
 */
@SuppressWarnings("serial")
public class MyNSGAII<S extends Solution<?>> extends AbstractGeneticAlgorithm<S, List<S>>
{
	protected final long startTime;
	private long lastDumpTime;
	protected final int maxEvaluations;
	protected final SolutionListEvaluator<S> evaluator;
	protected int evaluations;
	protected Comparator<S> dominanceComparator;
	protected int matingPoolSize;
	protected int offspringPopulationSize;
	private final ArrayList<Long> iterationsTime;
	private final TreeNode initialNode;
	private final OptimizationCriteria optimizationCriteria;
	private final HashMap<Long, Double> dumpedStepsInformation;

	/**
	 * Constructor
	 */
	public MyNSGAII(NSGAIIBuilder<S> builder,
					final TreeNode initialNode, final OptimizationCriteria optimizationCriteria)
	{
		this(builder.getProblem(), builder.getMaxIterations(), builder.getPopulationSize(), builder.getPopulationSize(), builder.getPopulationSize(),
				builder.getCrossoverOperator(), builder.getMutationOperator(), builder.getSelectionOperator(), new DefaultDominanceComparator<>(),
				builder.getSolutionListEvaluator(), initialNode, optimizationCriteria);
	}

	public MyNSGAII(NSGAIIBuilder<S> builder,
					final DominanceComparator<S> dominanceComparator,
					final TreeNode initialNode, final OptimizationCriteria optimizationCriteria)
	{
		this(builder.getProblem(), builder.getMaxIterations(), builder.getPopulationSize(), builder.getPopulationSize(), builder.getPopulationSize(),
				builder.getCrossoverOperator(), builder.getMutationOperator(), builder.getSelectionOperator(), dominanceComparator,
				builder.getSolutionListEvaluator(), initialNode, optimizationCriteria);
	}

	/**
	 * Constructor
	 */
	public MyNSGAII(Problem<S> problem, int maxEvaluations, int populationSize,
				  int matingPoolSize, int offspringPopulationSize,
				  CrossoverOperator<S> crossoverOperator, MutationOperator<S> mutationOperator,
				  SelectionOperator<List<S>, S> selectionOperator, SolutionListEvaluator<S> evaluator,
					final TreeNode initialNode, final OptimizationCriteria optimizationCriteria)
	{
		this(problem, maxEvaluations, populationSize, matingPoolSize, offspringPopulationSize,
				crossoverOperator, mutationOperator, selectionOperator, new DefaultDominanceComparator<S>(),
				evaluator, initialNode, optimizationCriteria);
	}

	/**
	 * Constructor
	 */
	public MyNSGAII(Problem<S> problem, int maxEvaluations, int populationSize,
				  int matingPoolSize, int offspringPopulationSize,
				  CrossoverOperator<S> crossoverOperator, MutationOperator<S> mutationOperator,
				  SelectionOperator<List<S>, S> selectionOperator, Comparator<S> dominanceComparator,
				  SolutionListEvaluator<S> evaluator, final TreeNode initialNode, final OptimizationCriteria optimizationCriteria)
	{
		super(problem);
		this.maxEvaluations = maxEvaluations;
		setMaxPopulationSize(populationSize);

		this.crossoverOperator = crossoverOperator;
		this.mutationOperator = mutationOperator;
		this.selectionOperator = selectionOperator;

		this.evaluator = evaluator;
		this.dominanceComparator = dominanceComparator;

		this.matingPoolSize = matingPoolSize;
		this.offspringPopulationSize = offspringPopulationSize;
		this.iterationsTime = new ArrayList<>();
		this.initialNode = initialNode;
		this.optimizationCriteria = optimizationCriteria;
		this.dumpedStepsInformation = new HashMap<>();
		this.startTime = System.nanoTime();
		this.lastDumpTime = this.startTime;
	}

	public void setDominanceComparator(final DominanceComparator<S> dominanceComparator)
	{
		this.dominanceComparator = dominanceComparator;
	}

	@Override
	protected void initProgress()
	{
		evaluations = getMaxPopulationSize();
		System.out.println("Starting iteration n°" + (evaluations / getMaxPopulationSize()));
		iterationsTime.add(System.nanoTime());
	}

	@Override
	protected void updateProgress()
	{
		final long currentTimeStamp = System.nanoTime();
		final long lastTimeStamp = iterationsTime.get(iterationsTime.size() - 1);
		iterationsTime.add(currentTimeStamp);
		System.out.println("Iteration n°" + (evaluations / getMaxPopulationSize()) + " took " + Utils.nanoSecToReadable(currentTimeStamp - lastTimeStamp));
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
					System.out.println("Iteration n°" + (evaluations / getMaxPopulationSize()) + " has a best optimisation of " + (bestSolutionSoFar * 100) + "%.");
				}
			}
		}

		evaluations += offspringPopulationSize;
		System.out.println("Starting iteration n°" + (evaluations / getMaxPopulationSize()));
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

	/**
	 * This method iteratively applies a {@link SelectionOperator} to the population to fill the
	 * mating pool population.
	 *
	 * @param population
	 * @return The mating pool population
	 */
	@Override
	protected List<S> selection(List<S> population)
	{
		List<S> matingPopulation = new ArrayList<>(population.size());
		for (int i = 0; i < matingPoolSize; i++)
		{
			S solution = selectionOperator.execute(population);
			matingPopulation.add(solution);
		}

		return matingPopulation;
	}

	/**
	 * This methods iteratively applies a {@link CrossoverOperator} a  {@link MutationOperator} to the
	 * population to create the offspring population. The population size must be divisible by the
	 * number of parents required by the {@link CrossoverOperator}; this way, the needed parents are
	 * taken sequentially from the population.
	 * <p>
	 * The number of solutions returned by the {@link CrossoverOperator} must be equal to the
	 * offspringPopulationSize state variable
	 *
	 * @param matingPool
	 * @return The new created offspring population
	 */
	@Override
	protected List<S> reproduction(List<S> matingPool)
	{
		int numberOfParents = crossoverOperator.numberOfRequiredParents();

		checkNumberOfParents(matingPool, numberOfParents);

		List<S> offspringPopulation = new ArrayList<>(offspringPopulationSize);
		for (int i = 0; i < matingPool.size(); i += numberOfParents)
		{
			List<S> parents = new ArrayList<>(numberOfParents);
			for (int j = 0; j < numberOfParents; j++)
			{
				parents.add(matingPool.get(i + j));
			}

			List<S> offspring = crossoverOperator.execute(parents);

			for (S s : offspring)
			{
				mutationOperator.execute(s);
				offspringPopulation.add(s);
				if (offspringPopulation.size() >= offspringPopulationSize)
				{
					break;
				}
			}
		}
		return offspringPopulation;
	}

	@Override
	protected List<S> replacement(List<S> population, List<S> offspringPopulation)
	{
		List<S> jointPopulation = new ArrayList<>();
		jointPopulation.addAll(population);
		jointPopulation.addAll(offspringPopulation);

		RankingAndCrowdingSelection<S> rankingAndCrowdingSelection;
		rankingAndCrowdingSelection = new RankingAndCrowdingSelection<S>(getMaxPopulationSize(),
				dominanceComparator);

		return rankingAndCrowdingSelection.execute(jointPopulation);
	}

	@Override
	public List<S> result()
	{
		return SolutionListUtils.getNonDominatedSolutions(getPopulation());
	}

	@Override
	public String name()
	{
		return "NSGAII";
	}

	@Override
	public String description()
	{
		return "Nondominated Sorting Genetic Algorithm version II";
	}

	public HashMap<Long, Double> dumpedStepsInformation()
	{
		return this.dumpedStepsInformation;
	}
}
