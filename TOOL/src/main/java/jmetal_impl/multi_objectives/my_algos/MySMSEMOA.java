package jmetal_impl.multi_objectives.my_algos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import jmetal_impl.RefactoringProblem;
import main.Main;
import org.uma.jmetal.algorithm.impl.AbstractGeneticAlgorithm;
import org.uma.jmetal.algorithm.multiobjective.smsemoa.SMSEMOABuilder;
import org.uma.jmetal.solution.Solution;
import org.uma.jmetal.util.comparator.dominanceComparator.DominanceComparator;
import org.uma.jmetal.util.legacy.qualityindicator.impl.hypervolume.Hypervolume;
import org.uma.jmetal.util.legacy.qualityindicator.impl.hypervolume.impl.PISAHypervolume;
import org.uma.jmetal.util.ranking.Ranking;
import org.uma.jmetal.util.ranking.impl.MergeNonDominatedSortRanking;
import other.Utils;
import refactoring.legacy.exceptions.BadDependencyException;
import refactoring.step_by_step.OptimizationCriteria;
import refactoring.step_by_step.TreeNode;

/**
 * @author Antonio J. Nebro
 */
public class MySMSEMOA<S extends Solution<?>> extends AbstractGeneticAlgorithm<S, List<S>>
{
	private final long startTime;
	private long lastDumpTime;
	protected final int maxEvaluations;
	protected final double offset;

	protected int evaluations;

	private Hypervolume<S> hypervolume;
	protected Comparator<S> dominanceComparator;
	private final ArrayList<Long> iterationsTime;
	private final TreeNode initialNode;
	private final OptimizationCriteria optimizationCriteria;
	private final HashMap<Long, Double> dumpedStepsInformation;

	/**
	 * Constructor
	 */
	public MySMSEMOA(SMSEMOABuilder<S> builder,
					 final DominanceComparator<S> dominanceComparator,
					 final TreeNode initialNode,
					 final OptimizationCriteria optimizationCriteria)
	{
		super(builder.getProblem());
		this.maxEvaluations = builder.getMaxEvaluations();
		setMaxPopulationSize(builder.getPopulationSize());

		this.offset = builder.getOffset();

		this.crossoverOperator = builder.getCrossoverOperator();
		this.mutationOperator = builder.getMutationOperator();
		this.selectionOperator = builder.getSelectionOperator();
		this.dominanceComparator = dominanceComparator;
		this.hypervolume = new PISAHypervolume<>();
		hypervolume.setOffset(offset);
		this.iterationsTime = new ArrayList<>();
		this.initialNode = initialNode;
		this.optimizationCriteria = optimizationCriteria;
		this.dumpedStepsInformation = new HashMap<>();
		this.startTime = System.nanoTime();
	}

	@Override
	protected void initProgress()
	{
		evaluations = getMaxPopulationSize();
		System.out.println("Starting iteration n°" + (evaluations - getMaxPopulationSize() + 1));
		iterationsTime.add(System.nanoTime());
	}

	@Override
	protected void updateProgress()
	{
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
		for (S solution : population)
		{
			getProblem().evaluate(solution);
		}
		return population;
	}

	@Override
	protected List<S> selection(List<S> population)
	{
		List<S> matingPopulation = new ArrayList<>(2);
		for (int i = 0; i < 2; i++)
		{
			S solution = selectionOperator.execute(population);
			matingPopulation.add(solution);
		}

		return matingPopulation;
	}

	@Override
	protected List<S> reproduction(List<S> population)
	{
		List<S> offspringPopulation = new ArrayList<>(1);

		List<S> parents = new ArrayList<>(2);
		parents.add(population.get(0));
		parents.add(population.get(1));

		List<S> offspring = crossoverOperator.execute(parents);

		mutationOperator.execute(offspring.get(0));

		offspringPopulation.add(offspring.get(0));
		return offspringPopulation;
	}

	@Override
	protected List<S> replacement(List<S> population, List<S> offspringPopulation)
	{
		List<S> jointPopulation = new ArrayList<>();
		jointPopulation.addAll(population);
		jointPopulation.addAll(offspringPopulation);

		Ranking<S> ranking = new MergeNonDominatedSortRanking<>();
		ranking.compute(jointPopulation);

		List<S> lastSubfront = ranking.getSubFront(ranking.getNumberOfSubFronts() - 1);

		lastSubfront = hypervolume.computeHypervolumeContribution(lastSubfront, jointPopulation);

		List<S> resultPopulation = new ArrayList<>();
		for (int i = 0; i < ranking.getNumberOfSubFronts() - 1; i++)
		{
			for (S solution : ranking.getSubFront(i))
			{
				resultPopulation.add(solution);
			}
		}

		for (int i = 0; i < lastSubfront.size() - 1; i++)
		{
			resultPopulation.add(lastSubfront.get(i));
		}

		return resultPopulation;
	}

	@Override
	public List<S> result()
	{
		return getPopulation();
	}

	@Override
	public String name()
	{
		return "SMSEMOA";
	}

	@Override
	public String description()
	{
		return "S metric selection EMOA";
	}

	public HashMap<Long, Double> dumpedStepsInformation()
	{
		return this.dumpedStepsInformation;
	}
}