package refactoring.step_by_step;

import bpmn.graph.Graph;
import bpmn.graph.GraphToList;
import bpmn.types.process.BpmnProcessObject;
import bpmn.writing.direct.DirectWriter;
import org.apache.commons.math3.distribution.AbstractRealDistribution;
import other.Pair;
import other.Utils;
import refactoring.legacy.Cluster;
import refactoring.legacy.exceptions.BadDependencyException;
import refactoring.legacy.partial_order_to_bpmn.AbstractGraphsGenerator;
import refactoring.legacy.partial_order_to_bpmn.BPMNGenerator;
import resources.GlobalResourceSet;
import resources.ResourcePool;
import simulator.SimulationResultsAggregator;
import simulator.Simulator;
import simulator.model.SimulationResult;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TreePrunerV3
{
	private static final boolean CONSIDER_THRESHOLD = false;
	private static final boolean WEIGHT_ELEMENTS = false;

	private TreePrunerV3()
	{

	}

	public static void prune(final HashSet<TreeNode> currentNodes,
							 final File workingDirectory,
							 final ResourcePool availablePool,
							 final AbstractRealDistribution iat,
							 final int nbInstances,
							 final OptimizationCriteria optimizationCriteria,
							 final int nbLeafsToKeep,
							 final boolean completePrune,
							 final long maxTimePerStep,
							 final PruningMethod pruningMethod,
							 final int stepNumber,
							 final int nbTasksMax,
							 final boolean weightElements) throws InterruptedException, BadDependencyException
	{
		TreePrunerV3.effectivePrune(
			currentNodes, workingDirectory, availablePool, iat, nbInstances, optimizationCriteria, nbLeafsToKeep,
			completePrune, maxTimePerStep, pruningMethod, stepNumber, nbTasksMax, weightElements
		);
	}

	public static void prune(final HashSet<TreeNode> currentNodes,
							 final File workingDirectory,
							 final ResourcePool availablePool,
							 final AbstractRealDistribution iat,
							 final int nbInstances,
							 final OptimizationCriteria optimizationCriteria,
							 final int nbLeafsToKeep,
							 final boolean completePrune,
							 final long maxTimePerStep,
							 final PruningMethod pruningMethod,
							 final int stepNumber,
							 final int nbTasksMax) throws InterruptedException, BadDependencyException
	{
		TreePrunerV3.effectivePrune(
			currentNodes, workingDirectory, availablePool, iat, nbInstances, optimizationCriteria, nbLeafsToKeep,
			completePrune, maxTimePerStep, pruningMethod, stepNumber, nbTasksMax, WEIGHT_ELEMENTS
		);
	}

	//Private methods

	private static void effectivePrune(final HashSet<TreeNode> currentNodes,
									   final File workingDirectory,
									   final ResourcePool availablePool,
									   final AbstractRealDistribution iat,
									   final int nbInstances,
									   final OptimizationCriteria optimizationCriteria,
									   final int nbLeafsToKeep,
									   final boolean completePrune,
									   final long maxTimePerStep,
									   final PruningMethod pruningMethod,
									   final int stepNumber,
									   final int nbTasksMax,
									   final boolean weightElements) throws InterruptedException, BadDependencyException
	{
		final int effectiveNbLeafsToKeep = completePrune ? 1 : nbLeafsToKeep;
		final HashSet<TreeNode> leafsToKeep = new HashSet<>();
		final PruningMethod effectivePruningMethod =
			(effectiveNbLeafsToKeep <= 1 || effectiveNbLeafsToKeep >= currentNodes.size()) ?
			PruningMethod.KEEP_BESTS :
			pruningMethod;
		int index = 0;

		ExecutorService executorService = Executors.newWorkStealingPool();

		for (TreeNode child : currentNodes)
		{
			//child.addAllSimulationResults(child.parentNodes().iterator().next());
			final String fileName = "tmp_diagram_" + ++index + ".bpmn";
			final Cluster copy = child.mainCluster().copy();

			if (copy.abstractGraphs().isEmpty())
			{
				Utils.resetIndependentNodes(copy);
				final AbstractGraphsGenerator abstractGraphsGenerator = new AbstractGraphsGenerator(copy);
				abstractGraphsGenerator.generateAbstractGraphs();
			}

			BPMNGenerator generator = new BPMNGenerator(copy);
			Graph currentGraph = generator.generate();
			generator = null;

			//Dumper.getInstance().graphicDump(currentGraph, fileName + "1");

			GraphToList graphToList = new GraphToList(currentGraph);
			HashSet<BpmnProcessObject> objects = graphToList.convert();
			graphToList = null;
			currentGraph = null;

			DirectWriter directWriter;
			try
			{
				directWriter = new DirectWriter(
						workingDirectory,
						new ArrayList<>(objects),
						fileName
				);
			}
			catch (FileNotFoundException e)
			{
				throw new RuntimeException(e);
			}
			directWriter.writeInitialBpmnFile();
			directWriter = null;

			final GlobalResourceSet globalSet = new GlobalResourceSet(availablePool);
			globalSet.computeGlobalResources();

			executorService.execute(() ->
			{
				final SimulationResult result = TreePrunerV3.simulate(workingDirectory, availablePool, iat, nbInstances, fileName, globalSet, objects);
				child.addSimulationResult(result);
			});
		}

		executorService.shutdown();
		executorService.awaitTermination(maxTimePerStep < 0 ? Long.MAX_VALUE : maxTimePerStep, TimeUnit.NANOSECONDS);
		executorService.shutdownNow();

		int i = 1;

		TreePrunerV3.computeLeafsToKeep(
			leafsToKeep, currentNodes, effectiveNbLeafsToKeep, effectivePruningMethod, optimizationCriteria,
			stepNumber, nbTasksMax, weightElements
		);
		System.out.println("Nb leafs to keep: " + leafsToKeep.size());

		currentNodes.clear();
		currentNodes.addAll(leafsToKeep);
		System.out.println("Nb current nodes: " + currentNodes.size());
	}

	private static void computeLeafsToKeep(final HashSet<TreeNode> leafsToKeep,
										   final HashSet<TreeNode> currentNodes,
										   final int effectiveNbLeafsToKeep,
										   final PruningMethod pruningMethod,
										   final OptimizationCriteria optimizationCriteria,
										   final int stepNumber,
										   final int nbTasksMax,
										   final boolean weightElements)
	{
		if (pruningMethod == PruningMethod.KEEP_BESTS)
		{
			for (TreeNode child : currentNodes)
			{
				//May happen if the simulation reached the time bound
				if (child.results().isEmpty())
				{
					System.out.println("Child was not simulated due to time limitation.");
					continue;
				}

				final SimulationResult parentResult = child.parentNodes().iterator().next().lastResult();
				final SimulationResult childResult = child.lastResult();
				final double score = TreePrunerV3.computeScoreV4(parentResult, childResult, optimizationCriteria, weightElements);
				child.setScore(score);
				//System.out.println("Node " + i++ + " has a score of " + score + "\n\n");

				if (effectiveNbLeafsToKeep == -1
					|| leafsToKeep.size() < effectiveNbLeafsToKeep)
				{
					leafsToKeep.add(child);
				}
				else
				{
					final TreeNode currentWorstNode = TreePrunerV3.computeWorstNode(leafsToKeep);

					if (child.score() > currentWorstNode.score())
					{
						leafsToKeep.remove(currentWorstNode);
						leafsToKeep.add(child);
					}
				}
			}
		}
		else if (pruningMethod == PruningMethod.RANDOM)
		{
			final Random random = new Random();
			final HashSet<TreeNode> currentNodesModifiable = new HashSet<>(currentNodes);

			while (leafsToKeep.size() != effectiveNbLeafsToKeep
					&& leafsToKeep.size() < nbTasksMax)
			{
				for (Iterator<TreeNode> iterator = currentNodesModifiable.iterator(); iterator.hasNext(); )
				{
					final TreeNode child = iterator.next();

					if (child.results().isEmpty())
					{
						System.out.println("Child was not simulated due to time limitation.");
						continue;
					}

					child.computeScore(optimizationCriteria);

					if (random.nextBoolean())
					{
						leafsToKeep.add(child);
						iterator.remove();
					}
				}
			}
		}
		else if (pruningMethod == PruningMethod.KEEP_HALF_BEST_HALF_WORST)
		{
			//We possibly keep one more "best leaf" than "worst leaf".
			final int nbBestLeafsToKeep = effectiveNbLeafsToKeep / 2 + effectiveNbLeafsToKeep % 2;
			final int nbWorstLeafsToKeep = effectiveNbLeafsToKeep / 2;
			System.out.println("Nb best leafs to keep: " + nbBestLeafsToKeep);
			System.out.println("Nb worst leafs to keep: " + nbWorstLeafsToKeep);
			final HashSet<TreeNode> bestLeafs = new HashSet<>();
			final HashSet<TreeNode> worstLeafs = new HashSet<>();

			for (TreeNode child : currentNodes)
			{
				//May happen if the simulation reached the time bound
				if (child.results().isEmpty())
				{
					System.out.println("Child was not simulated due to time limitation.");
					continue;
				}

				final SimulationResult parentResult = child.parentNodes().iterator().next().lastResult();
				final SimulationResult childResult = child.lastResult();
				final double score = TreePrunerV3.computeScoreV4(parentResult, childResult, optimizationCriteria, weightElements);
				child.setScore(score);
				//System.out.println("Node " + i++ + " has a score of " + score + "\n\n");

				if (bestLeafs.size() < nbBestLeafsToKeep)
				{
					bestLeafs.add(child);
				}
				else
				{
					final TreeNode currentWorstNode = TreePrunerV3.computeWorstNode(bestLeafs);

					if (child.score() > currentWorstNode.score())
					{
						bestLeafs.remove(currentWorstNode);
						bestLeafs.add(child);
					}
				}

				if (worstLeafs.size() < nbWorstLeafsToKeep)
				{
					worstLeafs.add(child);
				}
				else
				{
					final TreeNode currentBestNode = TreePrunerV3.computeBestNode(worstLeafs);

					if (child.score() < currentBestNode.score())
					{
						worstLeafs.remove(currentBestNode);
						worstLeafs.add(child);
					}
				}
			}

			System.out.println("Nb best leafs generated: " + bestLeafs.size());
			System.out.println("Nb worst leafs generated: " + worstLeafs.size());
			leafsToKeep.addAll(bestLeafs);
			leafsToKeep.addAll(worstLeafs);
		}
		else if (pruningMethod == PruningMethod.PSEUDO_SIMULATED_ANNEALING)
		{
			//Compute the current temperature
			double multiplicationFactor = 0.8d;
			double temperature = 100d;

			//Compute all best leafs
			for (int i = 0; i < stepNumber; i++)
			{
				temperature = temperature * multiplicationFactor;
			}

			final ArrayList<TreeNode> bestLeafs = new ArrayList<>();

			for (TreeNode child : currentNodes)
			{
				//May happen if the simulation reached the time bound
				if (child.results().isEmpty())
				{
					System.out.println("Child was not simulated due to time limitation.");
					continue;
				}

				final SimulationResult parentResult = child.parentNodes().iterator().next().lastResult();
				final SimulationResult childResult = child.lastResult();
				final double score = TreePrunerV3.computeScoreV4(parentResult, childResult, optimizationCriteria, weightElements);
				child.setScore(score);
				//System.out.println("Node " + i++ + " has a score of " + score + "\n\n");

				if (effectiveNbLeafsToKeep == -1
					|| bestLeafs.size() < effectiveNbLeafsToKeep)
				{
					bestLeafs.add(child);
				}
				else
				{
					final TreeNode currentWorstNode = TreePrunerV3.computeWorstNode(bestLeafs);

					if (child.score() > currentWorstNode.score())
					{
						bestLeafs.remove(currentWorstNode);
						bestLeafs.add(child);
					}
				}
			}

			System.out.println("Nb best leafs: " + bestLeafs.size());

			//Remove all the beast leafs from the set of current nodes
			final HashSet<TreeNode> tempCurrentNodes = new HashSet<>(currentNodes);
			bestLeafs.forEach(tempCurrentNodes::remove);

			System.out.println("Nb best leafs after: " + bestLeafs.size());

			//Stochastically replace the best nodes by remaining non-best leafs
			final Random random = new Random();

			for (int i = 0; i < bestLeafs.size(); i++)
			{
				final TreeNode currentBestLeaf = bestLeafs.get(i);
				final TreeNode randomRemainingNode = tempCurrentNodes.stream().skip(new Random().nextInt(tempCurrentNodes.size())).findFirst().orElse(null);
				tempCurrentNodes.remove(randomRemainingNode);

				if (randomRemainingNode == null) throw new IllegalStateException();

				final double deltaEnergy = TreePrunerV3.getNegativeValueOf(
						(randomRemainingNode.computeScore(optimizationCriteria) - currentBestLeaf.computeScore(optimizationCriteria)) * 100d
				);
				final double probability = Math.exp(deltaEnergy / temperature);
				final boolean replace = random.nextDouble() < probability;

				if (replace)
				{
					bestLeafs.set(i, randomRemainingNode);
				}
			}

			System.out.println("Nb best leafs in the end: " + bestLeafs.size());
			System.out.println("Nb leafs to keep before: " + leafsToKeep.size());
			leafsToKeep.addAll(bestLeafs);
			System.out.println("Nb leafs to keep after: " + leafsToKeep.size());
		}
		else
		{
			throw new IllegalStateException("Pruning method |" + pruningMethod + "| is not known.");
		}
	}

	private static TreeNode computeWorstNode(final Collection<TreeNode> nodes)
	{
		final Iterator<TreeNode> iterator = nodes.iterator();
		TreeNode worstNode = iterator.next();

		while (iterator.hasNext())
		{
			final TreeNode currentNode = iterator.next();

			if (currentNode.score() < worstNode.score())
			{
				worstNode = currentNode;
			}
		}

		return worstNode;
	}

	private static TreeNode computeBestNode(final Collection<TreeNode> nodes)
	{
		TreeNode bestNode = null;

		for (TreeNode currentNode : nodes)
		{
			if (bestNode == null
				|| currentNode.score() > bestNode.score())
			{
				bestNode = currentNode;
			}
		}

		return bestNode;
	}

	private static SimulationResult simulate(final File workingDirectory,
											 final ResourcePool availablePool,
											 final AbstractRealDistribution iat,
											 final int nbInstances,
											 final String fileName,
											 final GlobalResourceSet globalSet,
											 final HashSet<BpmnProcessObject> objects)
	{
		final Simulator simulator = new Simulator(
				new File(Path.of(workingDirectory.getPath(), fileName).toString()),
				objects,
				globalSet,
				availablePool,
				iat,
				nbInstances
		);

		final SimulationResult result = SimulationResultsAggregator.aggregate(simulator.simulateMultipleInstances());

		new File(Path.of(workingDirectory.getPath(), fileName).toString()).delete();

		return result;
	}

	//SCORES

	public static double computeScoreFromRoot(final TreeNode rootNode,
											  final TreeNode currentNode,
											  final OptimizationCriteria optimizationCriteria,
											  final boolean weightElements)
	{
		return TreePrunerV3.computeScoreV5(rootNode.lastResult(), currentNode.lastResult(), optimizationCriteria, weightElements);
	}

	public static double computeScore(final TreeNode node,
									  final OptimizationCriteria optimizationCriteria,
									  final boolean weightElements)
	{
		return TreePrunerV3.computeScoreV4(node.parentNodes().iterator().next().lastResult(), node.lastResult(), optimizationCriteria, weightElements);
	}

	public static double computeScoreV4(final SimulationResult parentResult,
										final SimulationResult childResult,
										final OptimizationCriteria optimizationCriteria,
										final boolean weightElements)
	{
		double score = 0d;

		if (optimizationCriteria.costOptimizationWeight() != 0)
		{
			final double parentCost = parentResult.getTotalCost();
			//System.out.println("Parent cost: " + parentCost);
			final double childCost = childResult.getTotalCost();
			//System.out.println("Child cost: " + childCost);
			final double costGain = 1d - (childCost / parentCost);
			//System.out.println("Cost gain: " + costGain);
			final double costScore = costGain * (weightElements ? optimizationCriteria.costOptimizationWeight() : 1d);
			//System.out.println("Cost score: " + costScore);
			score += costScore;
		}

		if (optimizationCriteria.timeOptimizationWeight() != 0)
		{
			final double parentAET = parentResult.getAvgExecTime();
			//System.out.println("Parent AET: " + parentAET);
			final double childAET = childResult.getAvgExecTime();
			//System.out.println("Child AET: " + childAET);
			final double aetGain = 1d - (childAET / parentAET);
			//System.out.println("AET gain: " + aetGain);
			final double aetScore = aetGain * (weightElements ? optimizationCriteria.timeOptimizationWeight() : 1d);
			//System.out.println("AET score: " + aetScore);
			score += aetScore;
		}

		final HashMap<String, Pair<Integer, Double>> resourcesToOptimizeAndTargetUsageAndWeight = optimizationCriteria.getResourcesToOptimizeAndTargetUsageAndWeight();

		for (String resourceName : resourcesToOptimizeAndTargetUsageAndWeight.keySet())
		{
			final Pair<Integer, Double> targetUsageAndWeight = resourcesToOptimizeAndTargetUsageAndWeight.get(resourceName);
			final Map<String, Double> parentGlobalResUsage = parentResult.getUsagePercentage();
			final Map<String, Double> childGlobalResUsage = childResult.getUsagePercentage();

			if (parentGlobalResUsage.containsKey(resourceName))
			{
				final double resourceScore;
				//System.out.println("-----Resource " + resourceName + "-----");
				//System.out.println("Target usage: " + targetUsageAndWeight.first());
				//System.out.println("Weight: " + targetUsageAndWeight.second());
				final double parentResUsage = parentGlobalResUsage.get(resourceName);
				//System.out.println("Parent usage: " + parentResUsage);
				final double childResUsage = childGlobalResUsage.get(resourceName);
				//System.out.println("Child usage: " + childResUsage);
				final double resourceGain;

				if (CONSIDER_THRESHOLD)
				{
					final double parentProximity = Math.abs(parentResUsage - (double) targetUsageAndWeight.first());
					//System.out.println("Parent proximity: " + parentProximity);
					final double childProximity = Math.abs(childResUsage - (double) targetUsageAndWeight.first());
					//System.out.println("Child proximity: " + childProximity);
					resourceGain = (parentProximity - childProximity) / 100d;
				}
				else
				{
					//In this case, we focus on minimising the resource usage; we do not target a precise usage.
					resourceGain = (parentResUsage - childResUsage) / 100d;
				}

				//System.out.println("Resource gain: " + resourceGain);
				resourceScore = resourceGain * (weightElements ? targetUsageAndWeight.second() : 1d);
				//System.out.println("Resource score: " + resourceScore);

				score += resourceScore;
			}
			else
			{
				throw new IllegalStateException("Resource \"" + resourceName + "\" does not appear in the pool of resources!");
			}
		}

		return score;
	}

	public static double computeScoreV5(final SimulationResult parentResult,
										final SimulationResult childResult,
										final OptimizationCriteria optimizationCriteria,
										final boolean weightElements)
	{
		double score = 0d;

		if (optimizationCriteria.costOptimizationWeight() != 0)
		{
			final double parentCost = parentResult.getTotalCost();
			//System.out.println("Parent cost: " + parentCost);
			final double childCost = childResult.getTotalCost();
			//System.out.println("Child cost: " + childCost);
			final double costGain = 1d - (childCost / parentCost);
			//System.out.println("Cost gain: " + costGain);
			final double costScore = costGain * (weightElements ? optimizationCriteria.costOptimizationWeight() : 1d);
			//System.out.println("Cost score: " + costScore);
			score += costScore;
		}

		if (optimizationCriteria.timeOptimizationWeight() != 0)
		{
			final double parentAET = parentResult.getAvgExecTime();
			//System.out.println("Parent AET: " + parentAET);
			final double childAET = childResult.getAvgExecTime();
			//System.out.println("Child AET: " + childAET);
			final double aetGain = 1d - (childAET / parentAET);
			//System.out.println("AET gain: " + aetGain);
			final double aetScore = aetGain * (weightElements ? optimizationCriteria.timeOptimizationWeight() : 1d);
			//System.out.println("AET score: " + aetScore);
			score += aetScore;
		}

		final HashMap<String, Pair<Integer, Double>> resourcesToOptimizeAndTargetUsageAndWeight = optimizationCriteria.getResourcesToOptimizeAndTargetUsageAndWeight();

		for (String resourceName : resourcesToOptimizeAndTargetUsageAndWeight.keySet())
		{
			final Pair<Integer, Double> targetUsageAndWeight = resourcesToOptimizeAndTargetUsageAndWeight.get(resourceName);
			final Map<String, Double> parentGlobalResUsage = parentResult.getUsagePercentage();
			final Map<String, Double> childGlobalResUsage = childResult.getUsagePercentage();

			if (parentGlobalResUsage.containsKey(resourceName))
			{
				final double resourceScore;
				//System.out.println("-----Resource " + resourceName + "-----");
				//System.out.println("Target usage: " + targetUsageAndWeight.first());
				//System.out.println("Weight: " + targetUsageAndWeight.second());
				final double parentResUsage = parentGlobalResUsage.get(resourceName);
				//System.out.println("Parent usage: " + parentResUsage);
				final double childResUsage = childGlobalResUsage.get(resourceName);
				//System.out.println("Child usage: " + childResUsage);
				final double resourceGain;

				if (CONSIDER_THRESHOLD)
				{
					final double parentProximity = Math.abs(parentResUsage - (double) targetUsageAndWeight.first());
					//System.out.println("Parent proximity: " + parentProximity);
					final double childProximity = Math.abs(childResUsage - (double) targetUsageAndWeight.first());
					//System.out.println("Child proximity: " + childProximity);
					resourceGain = (parentProximity - childProximity) / 100d;
				}
				else
				{
					//In this case, we focus on minimising the resource usage; we do not target a precise usage.
					resourceGain = (parentResUsage - childResUsage) / 100d;
				}

				//System.out.println("Resource gain: " + resourceGain);
				resourceScore = resourceGain * (weightElements ? targetUsageAndWeight.second() : 1d);
				//System.out.println("Resource score: " + resourceScore);

				score += resourceScore;
			}
			else
			{
				throw new IllegalStateException("Resource \"" + resourceName + "\" does not appear in the pool of resources!");
			}
		}

		return score;
	}

	private static double getNegativeValueOf(final double d)
	{
		return -Math.abs(d);
	}
}
