package jmetal_impl.multi_objectives;

import bpmn.BpmnParser;
import bpmn.types.process.Task;
import jmetal_impl.RefactoringProblem;
import jmetal_impl.RefactoringSolution;
import jmetal_impl.single_objective.SingleObjectiveRefactoringProblem;
import jmetal_impl.single_objective.SingleObjectiveRefactoringSolution;
import main.CommandLineParser;
import org.apache.commons.math3.distribution.AbstractRealDistribution;
import refactoring.legacy.Cluster;
import refactoring.legacy.dependencies.Dependency;
import refactoring.legacy.exceptions.BadDependencyException;
import refactoring.step_by_step.*;
import resources.GlobalResourceSet;
import resources.ResourcePool;
import simulator.GlobalInfoEnum;
import simulator.model.SimulationResult;

import java.io.File;
import java.util.*;

public class MultiObjectivesRefactoringProblem extends RefactoringProblem
{
	public MultiObjectivesRefactoringProblem(final Collection<Task> alreadyElectedTasks,
											 final BpmnParser parser,
											 final HashSet<TreeNode> currentLeafs,
											 final CommandLineParser commandLineParser,
											 final Cluster mainCluster,
											 final HashSet<Dependency> globalDependencies,
											 final TreeNode initialNode,
											 final File workingDirectory,
											 final AbstractRealDistribution iat,
											 final int nbInstances,
											 final ResourcePool availablePool,
											 final OptimizationCriteria optimizationCriteria,
											 final GlobalResourceSet globalResourceSet,
											 final Map<GlobalInfoEnum, Object> globalInformation,
											 final Tree tree,
											 final ArrayList<String> timePerStep,
											 final boolean weightElements)
	{
		super(alreadyElectedTasks, parser, currentLeafs, commandLineParser, mainCluster, globalDependencies,
				initialNode, workingDirectory, iat, nbInstances, availablePool, optimizationCriteria, globalResourceSet,
				globalInformation, tree, timePerStep, weightElements);
	}

	@Override
	public int numberOfObjectives()
	{
		return this.optimizationCriteria.nbCriteria();
	}

	@Override
	public String name()
	{
		return "MultiObjectivesRefactoringProblem";
	}

	@Override
	public RefactoringSolution evaluate(RefactoringSolution solution)
	{
		final TreeNode treeNode = solution.treeNode();
		final SimulationResult simulationResult;
		try
		{
			simulationResult = this.simulateNode(treeNode);
		}
		catch (BadDependencyException e)
		{
			throw new RuntimeException(e);
		}
		final double score = TreePrunerV3.computeScoreFromRoot(this.rootNode, treeNode, this.optimizationCriteria, this.considerWeights);
		treeNode.setScore(score);

		int index = 0;

		if (this.optimizationCriteria.aetMustBeOptimized())
		{
			solution.objectives()[index++] = simulationResult.getAvgExecTime();
		}

		if (this.optimizationCriteria.costMustBeOptimized())
		{
			solution.objectives()[index++] = simulationResult.getTotalCost();
		}

		for (String resourceName : this.optimizationCriteria.getResourcesToOptimizeAndTargetUsageAndWeight().keySet())
		{
			//System.out.println("Resource : " + resourceName);
			solution.objectives()[index++] = simulationResult.getUsagePercentage().get(resourceName);
		}

		return solution;
	}

	@Override
	public RefactoringSolution generateNewSolution(final TreeNode treeNode,
												   final OptimizationCriteria optimizationCriteria)
	{
		return new MultiObjectivesRefactoringSolution(treeNode, this.optimizationCriteria);
	}
}
