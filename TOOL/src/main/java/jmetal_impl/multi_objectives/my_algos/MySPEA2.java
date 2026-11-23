package jmetal_impl.multi_objectives.my_algos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import jmetal_impl.RefactoringProblem;
import main.Main;
import org.uma.jmetal.algorithm.impl.AbstractGeneticAlgorithm;
import org.uma.jmetal.algorithm.multiobjective.spea2.SPEA2Builder;
import org.uma.jmetal.algorithm.multiobjective.spea2.util.EnvironmentalSelection;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.operator.selection.SelectionOperator;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.solution.Solution;
import org.uma.jmetal.util.densityestimator.impl.StrenghtRawFitnessDensityEstimator;
import org.uma.jmetal.util.evaluator.SolutionListEvaluator;
import other.Utils;
import refactoring.legacy.exceptions.BadDependencyException;
import refactoring.step_by_step.OptimizationCriteria;
import refactoring.step_by_step.TreeNode;

/**
 * @author Juan J. Durillo
 **/
public class MySPEA2<S extends Solution<?>> extends AbstractGeneticAlgorithm<S, List<S>>
{
	private final long startTime;
	private long lastDumpTime;
	protected final int maxIterations;
	protected final SolutionListEvaluator<S> evaluator;
	protected int iterations;
	protected List<S> archive;
	protected final StrenghtRawFitnessDensityEstimator<S> densityEstimator = new StrenghtRawFitnessDensityEstimator<S>(1);
	protected final EnvironmentalSelection<S> environmentalSelection;
	protected final int k;
	private final ArrayList<Long> iterationsTime;
	private final TreeNode initialNode;
	private final OptimizationCriteria optimizationCriteria;
	private final HashMap<Long, Double> dumpedStepsInformation;

	public MySPEA2(SPEA2Builder<S> builder,
				   final TreeNode initialNode,
				   final OptimizationCriteria optimizationCriteria)
	{
		this(builder.getProblem(), builder.getMaxIterations(), builder.getPopulationSize(), builder.getCrossoverOperator(),
				builder.getMutationOperator(), builder.getSelectionOperator(), builder.getSolutionListEvaluator(), 1, initialNode, optimizationCriteria);
	}

	public MySPEA2(Problem<S> problem, int maxIterations, int populationSize,
				 CrossoverOperator<S> crossoverOperator, MutationOperator<S> mutationOperator,
				 SelectionOperator<List<S>, S> selectionOperator, SolutionListEvaluator<S> evaluator,
				 int k, final TreeNode initialNode, final OptimizationCriteria optimizationCriteria)
	{
		super(problem);
		this.maxIterations = maxIterations;
		this.setMaxPopulationSize(populationSize);

		this.k = k;
		this.crossoverOperator = crossoverOperator;
		this.mutationOperator = mutationOperator;
		this.selectionOperator = selectionOperator;
		this.environmentalSelection = new EnvironmentalSelection<S>(populationSize, k);

		this.archive = new ArrayList<>(populationSize);

		this.evaluator = evaluator;
		this.iterationsTime = new ArrayList<>();
		this.initialNode = initialNode;
		this.optimizationCriteria = optimizationCriteria;
		this.dumpedStepsInformation = new HashMap<>();
		this.startTime = System.nanoTime();
	}

	@Override
	protected void initProgress()
	{
		iterations = 1;
		System.out.println("Starting iteration n°" + iterations);
		iterationsTime.add(System.nanoTime());
	}

	@Override
	protected void updateProgress()
	{
		final long currentTimeStamp = System.nanoTime();
		final long lastTimeStamp = iterationsTime.get(iterationsTime.size() - 1);
		iterationsTime.add(currentTimeStamp);
		System.out.println("Iteration n°" + iterations + " took " + Utils.nanoSecToReadable(currentTimeStamp - lastTimeStamp));
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
					System.out.println("Iteration n°" + iterations + " has a best optimisation of " + (bestSolutionSoFar * 100) + "%.");
				}
			}
		}

		iterations++;
		System.out.println("Starting iteration n°" + (iterations));
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
	protected List<S> selection(List<S> population)
	{
		List<S> union = new ArrayList<>(2 * getMaxPopulationSize());
		union.addAll(archive);
		union.addAll(population);
		densityEstimator.compute(union);
		archive = environmentalSelection.execute(union);
		return archive;
	}

	@Override
	protected List<S> reproduction(List<S> population)
	{
		List<S> offSpringPopulation = new ArrayList<>(getMaxPopulationSize());

		while (offSpringPopulation.size() < getMaxPopulationSize())
		{
			List<S> parents = new ArrayList<>(2);
			S candidateFirstParent = selectionOperator.execute(population);
			parents.add(candidateFirstParent);
			S candidateSecondParent;
			candidateSecondParent = selectionOperator.execute(population);
			parents.add(candidateSecondParent);

			List<S> offspring = crossoverOperator.execute(parents);
			mutationOperator.execute(offspring.get(0));
			offSpringPopulation.add(offspring.get(0));
		}
		return offSpringPopulation;
	}

	@Override
	protected List<S> replacement(List<S> population,
								  List<S> offspringPopulation)
	{
		return offspringPopulation;
	}

	@Override
	public List<S> result()
	{
		return archive;
	}

	@Override
	public String name()
	{
		return "SPEA2";
	}

	@Override
	public String description()
	{
		return "Strength Pareto. Evolutionary Algorithm";
	}

	public HashMap<Long, Double> dumpedStepsInformation()
	{
		return this.dumpedStepsInformation;
	}
}
