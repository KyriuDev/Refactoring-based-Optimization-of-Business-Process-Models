package jmetal_impl.multi_objectives.my_algos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import jmetal_impl.RefactoringProblem;
import main.Main;
import org.uma.jmetal.algorithm.multiobjective.agemoea.AGEMOEA;
import org.uma.jmetal.algorithm.multiobjective.agemoea.AGEMOEABuilder;
import org.uma.jmetal.algorithm.multiobjective.agemoeaii.AGEMOEAIIBuilder;
import org.uma.jmetal.algorithm.multiobjective.agemoeaii.util.AGEMOEA2EnvironmentalSelection;
import org.uma.jmetal.solution.Solution;
import org.uma.jmetal.util.SolutionListUtils;
import org.uma.jmetal.util.evaluator.SolutionListEvaluator;
import other.Utils;
import refactoring.legacy.exceptions.BadDependencyException;
import refactoring.step_by_step.OptimizationCriteria;
import refactoring.step_by_step.TreeNode;

/**
 * Implementation of AGE-MOEA-II (Adaptive GEometry-based Many-objective Evolutionary Algorithm II).
 * <p>
 * Reference: A. Panichella, "An Improved Pareto Front Modeling Algorithm for Large-scale Many-Objective Optimization",
 * Proceedings of the Genetic and Evolutionary Computation Conference (GECCO). 2022.
 *
 * @author Annibale Panichella
 */
public class MyAGEMOEAII<S extends Solution<?>> extends AGEMOEA<S>
{
	protected final long startTime;
	private long lastDumpTime;
	protected SolutionListEvaluator<S> evaluator;
	private final ArrayList<Long> iterationsTime;
	private final TreeNode initialNode;
	private final OptimizationCriteria optimizationCriteria;
	private final HashMap<Long, Double> dumpedStepsInformation;

	/**
	 * Constructor
	 */
	public MyAGEMOEAII(AGEMOEAIIBuilder<S> builder,
					   final TreeNode initialNode,
					   final OptimizationCriteria optimizationCriteria)
	{ // can be created from the AGEMOEAIIBuilder within the same package
		super((AGEMOEABuilder<S>) builder);
		maxIterations = builder.getMaxIterations();
		crossoverOperator = builder.getCrossoverOperator();
		mutationOperator = builder.getMutationOperator();
		selectionOperator = builder.getSelectionOperator();
		evaluator = builder.getEvaluator();
		maxPopulationSize = builder.getPopulationSize();
		this.iterationsTime = new ArrayList<>();
		this.initialNode = initialNode;
		this.optimizationCriteria = optimizationCriteria;
		this.dumpedStepsInformation = new HashMap<>();
		this.startTime = System.nanoTime();
		this.lastDumpTime = this.startTime;
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
		System.out.println("Starting iteration n°" + iterations);
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
		List<S> matingPopulation = new ArrayList<>(population.size());
		for (int i = 0; i < getMaxPopulationSize(); i++)
		{
			S solution = selectionOperator.execute(population);
			matingPopulation.add((S) solution.copy());
		}

		return matingPopulation;
	}

	@Override
	protected List<S> reproduction(List<S> matingPool)
	{
		int numberOfParents = crossoverOperator.numberOfRequiredParents();

		checkNumberOfParents(matingPool, numberOfParents);

		List<S> offspringPopulation = new ArrayList<>(maxPopulationSize);
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
				if (offspringPopulation.size() >= maxPopulationSize)
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


		AGEMOEA2EnvironmentalSelection<S> environmentalSelection;
		environmentalSelection = new AGEMOEA2EnvironmentalSelection(getProblem().numberOfObjectives());

		return environmentalSelection.execute(jointPopulation, maxPopulationSize);
	}

	@Override
	public List<S> result()
	{
		return getNonDominatedSolutions(getPopulation());
	}

	protected List<S> getNonDominatedSolutions(List<S> solutionList)
	{
		return SolutionListUtils.getNonDominatedSolutions(solutionList);
	}

	@Override
	public String name()
	{
		return "AGE-MOEA-II";
	}

	@Override
	public String description()
	{
		return "Adaptive Geometry Estimation based MOEA II";
	}

	@Override
	public void run()
	{
		List<S> offspringPopulation;
		List<S> matingPopulation;

		population = createInitialPopulation();
		population = evaluatePopulation(population);

		// compute the survival score for the initial population
		population = replacement(population, new ArrayList<>());
		initProgress();
		while (!isStoppingConditionReached())
		{
			matingPopulation = selection(population);
			offspringPopulation = reproduction(matingPopulation);
			offspringPopulation = evaluatePopulation(offspringPopulation);
			population = replacement(population, offspringPopulation);
			updateProgress();
		}
	}

	public HashMap<Long, Double> dumpedStepsInformation()
	{
		return this.dumpedStepsInformation;
	}
}
