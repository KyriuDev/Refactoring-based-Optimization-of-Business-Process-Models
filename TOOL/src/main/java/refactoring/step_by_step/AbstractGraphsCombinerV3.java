package refactoring.step_by_step;

import bpmn.graph.Node;
import bpmn.types.process.BpmnProcessObject;
import bpmn.types.process.Task;
import constants.PrintLevel;
import hash.cluster.ClusterHasher;
import main.CommandLineParser;
import main.Main;
import other.Pair;
import other.Triple;
import other.Utils;
import refactoring.legacy.Cluster;
import refactoring.legacy.dependencies.*;
import refactoring.legacy.exceptions.BadDependencyException;
import refactoring.legacy.partial_order_to_bpmn.AbstractGraph;
import refactoring.legacy.partial_order_to_bpmn.AbstractGraphsGenerator;
import refactoring.legacy.partial_order_to_bpmn.AbstractNode;

import java.io.IOException;
import java.util.*;

import static main.Main.PRINT_LEVEL;

public class AbstractGraphsCombinerV3
{
	private static final int NB_MUTATIONS_MAX = 1;
	private final Cluster mainCluster;
	private final Cluster nodeCluster;
	private final Node nodeToCombine;
	private final ArrayList<Triple<String, Cluster, Cluster>> generatedClusters;
	private final int nbMutations;
	private final HashSet<Dependency> globalDependencies;
	private final ArrayList<BpmnProcessObject> originalObjects;
	private final CommandLineParser commandLineParser;

	public AbstractGraphsCombinerV3(final Cluster mainCluster,
									final Pair<Node, Cluster> nodeWithCluster,
									final int nbMutations,
									final HashSet<Dependency> globalDependencies,
									final ArrayList<BpmnProcessObject> originalObjects,
									final CommandLineParser commandLineParser)
	{
		this.mainCluster = mainCluster;
		this.nodeToCombine = nodeWithCluster.first();
		this.nodeCluster = nodeWithCluster.second();
		this.generatedClusters = new ArrayList<>();
		this.nbMutations = nbMutations;
		this.globalDependencies = globalDependencies;
		this.originalObjects = originalObjects;
		this.commandLineParser = commandLineParser;
	}

	public AbstractGraphsCombinerV3(final Cluster mainCluster,
									final Pair<Node, Cluster> nodeWithCluster,
									final int nbMutations,
									final HashSet<Dependency> globalDependencies)
	{
		this.mainCluster = mainCluster;
		this.nodeToCombine = nodeWithCluster.first();
		this.nodeCluster = nodeWithCluster.second();
		this.generatedClusters = new ArrayList<>();
		this.nbMutations = nbMutations;
		this.globalDependencies = globalDependencies;
		this.originalObjects = new ArrayList<>();
		this.commandLineParser = null;
	}

	public AbstractGraphsCombinerV3(final Cluster mainCluster,
									final Pair<Node, Cluster> nodeWithCluster,
									final HashSet<Dependency> globalDependencies)
	{
		this(mainCluster, nodeWithCluster, NB_MUTATIONS_MAX, globalDependencies);
	}

	public ArrayList<TreeNode> generateMutations() throws BadDependencyException, IOException
	{
		//Mutations
		final ArrayList<TreeNode> mutations = new ArrayList<>();

		//Generate all abstract graphs from the dependencies of the clusters
		final HashSet<String> newAlreadyBuiltDependencies = new HashSet<>();
		Utils.resetIndependentNodes(this.mainCluster);
		final AbstractGraphsGenerator abstractGraphsGenerator = new AbstractGraphsGenerator(this.mainCluster);
		abstractGraphsGenerator.generateAbstractGraphs();

		this.generateClusters(this.generatedClusters, this.mainCluster, this.nodeCluster, this.nodeToCombine);

		if (this.generatedClusters.isEmpty()) return new ArrayList<>();

		final ArrayList<Triple<String, Cluster, Cluster>> modifiableList = new ArrayList<>(this.generatedClusters);
		final Random random = new Random();

		while (!modifiableList.isEmpty()
				&& mutations.size() < NB_MUTATIONS_MAX)
		{
			final Triple<String, Cluster, Cluster> randomCluster = modifiableList.remove(random.nextInt(modifiableList.size()));
			final Cluster newMainCluster = randomCluster.third().copy();
			Utils.recomputeDependencies(newMainCluster, newMainCluster);
			final ClusterHasher hasher = new ClusterHasher(newMainCluster);
			final String hash = hasher.hash();

			if (!DependenciesVerifier.computeUnsatisfiedDependencies(globalDependencies, newMainCluster).isEmpty()
				|| newAlreadyBuiltDependencies.contains(hash))
			{
				continue;
			}

			newAlreadyBuiltDependencies.add(hash);
			final TreeNode nextNode = new TreeNode(newMainCluster, (Task) this.nodeToCombine.bpmnObject());
			mutations.add(nextNode);
		}

		return mutations;
	}

	public ArrayList<Triple<String, Cluster, Cluster>> fullInfoGeneratedClusters()
	{
		return this.generatedClusters;
	}

	public ArrayList<Cluster> onlyGeneratedClusters()
	{
		final ArrayList<Cluster> clusters = new ArrayList<>();

		for (Triple<String, Cluster, Cluster> triple : this.generatedClusters)
		{
			clusters.add(triple.third());
		}

		return clusters;
	}

	//Private methods

	private void generateClusters(final ArrayList<Triple<String, Cluster, Cluster>> generatedClusters,
								  final Cluster mainCluster,
								  final Cluster taskCluster,
								  final Node nodeToCombine) throws BadDependencyException, IOException
	{
		final int initialNbTasks = this.mainCluster.nbTasks();
		//System.out.println("Initial number of tasks: " + initialNbTasks);

		//Generate all combinations of locations of the task inside its cluster (this.nodeCluster)
		//		- Put the task in parallel of all the abstract graphs of the cluster

			final int previousNumberOfGeneratedClusters = generatedClusters.size();
			this.putTaskInParallelOfAllAbstractGraphs(generatedClusters, mainCluster, taskCluster, nodeToCombine);

			if (this.mainCluster.equals(mainCluster))
			{
				for (Triple<String, Cluster, Cluster> generatedCluster : generatedClusters)
				{
					if (generatedCluster.third().nbTasks() < initialNbTasks)
					{
						throw new IllegalStateException("Some tasks are missing after \"putTaskInParallelOfAllAbstractGraphs\"!");
					}

					Main.verifySolution(generatedCluster.third().copy(), this.originalObjects, this.commandLineParser);
				}
			}

			/*if (newNumberOfGeneratedClusters > 1)
			{
				throw new IllegalStateException("A single possibility should have been produced, but " + newNumberOfGeneratedClusters + " were.");
			}*/

			//		- Put the task in sequence at the beginning/end of all combinations of size >= 2 of abstract graphs of the cluster

			this.putTaskInSequenceOfAnyCombinationOfAbstractGraph(generatedClusters, mainCluster, taskCluster, nodeToCombine);

			if (this.mainCluster.equals(mainCluster))
			{
				for (Triple<String, Cluster, Cluster> generatedCluster : generatedClusters)
				{
					if (generatedCluster.third().nbTasks() < initialNbTasks)
					{
						throw new IllegalStateException("Some tasks are missing after \"putTaskInSequenceOfAnyCombinationOfAbstractGraph\"!");
					}

					Main.verifySolution(generatedCluster.third().copy(), this.originalObjects, this.commandLineParser);
				}
			}


			/*if (newNumberOfGeneratedClusters > 1)
			{
				throw new IllegalStateException("A single possibility should have been produced, but " + newNumberOfGeneratedClusters + " were.");
			}*/

			//		- Put the task in parallel of any ordered combination of node of each abstract graph of the cluster

			this.putTaskInParallelOfAllOrderedCombinationsOfNodes(generatedClusters, mainCluster, taskCluster, nodeToCombine);

			if (this.mainCluster.equals(mainCluster))
			{
				for (Triple<String, Cluster, Cluster> generatedCluster : generatedClusters)
				{
					if (generatedCluster.third().nbTasks() < initialNbTasks)
					{
						throw new IllegalStateException("Some tasks are missing after \"putTaskInParallelOfAllOrderedCombinationsOfNodes\"!");
					}

					Main.verifySolution(generatedCluster.third().copy(), this.originalObjects, this.commandLineParser);
				}
			}


			/*if (newNumberOfGeneratedClusters > 1)
			{
				throw new IllegalStateException("A single possibility should have been produced, but " + newNumberOfGeneratedClusters + " were.");
			}*/

			//		- Put the task in sequence at the beginning/end or between any node of any abstract graph of the cluster

			this.putTaskInSequenceBeforeAfterOrBetweenAnyNode(generatedClusters, mainCluster, taskCluster, nodeToCombine);

			if (this.mainCluster.equals(mainCluster))
			{
				for (Triple<String, Cluster, Cluster> generatedCluster : generatedClusters)
				{
					if (generatedCluster.third().nbTasks() < initialNbTasks)
					{
						throw new IllegalStateException("Some tasks are missing after \"putTaskInSequenceBeforeAfterOrBetweenAnyNode\"!");
					}

					Main.verifySolution(generatedCluster.third().copy(), this.originalObjects, this.commandLineParser);
				}
			}

			/*if (newNumberOfGeneratedClusters > 1)
			{
				throw new IllegalStateException("A single possibility should have been produced, but " + newNumberOfGeneratedClusters + " were.");
			}*/

			//		- Put the task in sequence at beginning/end of any combination of size >= 2 of task/subgraph of any abstract node of any abstract graph

			this.putTaskInSequenceOfAllCombinationsOfTaskAndSubgraph(generatedClusters, mainCluster, taskCluster, nodeToCombine);

			if (this.mainCluster.equals(mainCluster))
			{
				for (Triple<String, Cluster, Cluster> generatedCluster : generatedClusters)
				{
					if (generatedCluster.third().nbTasks() < initialNbTasks)
					{
						throw new IllegalStateException("Some tasks are missing after \"putTaskInSequenceOfAllCombinationsOfTaskAndSubgraph\"!");
					}

					Main.verifySolution(generatedCluster.third().copy(), this.originalObjects, this.commandLineParser);
				}
			}


			/*if (newNumberOfGeneratedClusters > 1)
			{
				throw new IllegalStateException("A single possibility should have been produced, but " + newNumberOfGeneratedClusters + " were.");
			}*/

			//		- Put the task inside a choice belonging to its cluster

				if (false)
				{
					final ArrayList<Triple<String, Cluster, Cluster>> choiceClusters = new ArrayList<>();
					this.putTaskInsideAChoice(choiceClusters, mainCluster, taskCluster, nodeToCombine);

					if (this.mainCluster.equals(mainCluster))
					{
						for (Triple<String, Cluster, Cluster> generatedCluster : generatedClusters)
						{
							if (generatedCluster.third().nbTasks() < initialNbTasks)
							{
								throw new IllegalStateException("Some tasks are missing after \"putTaskInsideAChoice\"!");
							}

							Main.verifySolution(generatedCluster.third().copy(), this.originalObjects, this.commandLineParser);
						}
					}
				}

				/*if (choiceClusters.size() > 1)
				{
					throw new IllegalStateException("A single possibility should have been produced, but " + choiceClusters.size() + " were.");
				}*/


	}

	private void putTaskInParallelOfAllAbstractGraphs(final ArrayList<Triple<String, Cluster, Cluster>> generatedClusters,
													  final Cluster mainCluster,
													  final Cluster taskCluster,
													  final Node nodeToCombine)
	{
		final int nbClustersBefore = generatedClusters.size();

		if (taskCluster.abstractGraphs().size() <= 1) return;

		final Cluster newMainCluster = mainCluster == null ? null : mainCluster.copy();
		final Cluster newTaskCluster = taskCluster.copy();
		final AbstractNode abstractNode = new AbstractNode();
		abstractNode.addNode(nodeToCombine.weakCopy());
		final AbstractGraph abstractGraph = new AbstractGraph(abstractNode);
		newTaskCluster.addAbstractGraph(abstractGraph);
		newTaskCluster.addElement(new EnhancedNode(nodeToCombine));

		if (mainCluster == null)
		{
			generatedClusters.add(new Triple<>("putTaskInParallelOfAllAbstractGraphs", taskCluster, newTaskCluster));
		}
		else
		{
			if (newMainCluster.equals(newTaskCluster))
			{
				generatedClusters.add(new Triple<>("putTaskInParallelOfAllAbstractGraphs", taskCluster, newTaskCluster));
			}
			else
			{
				generatedClusters.add(new Triple<>("putTaskInParallelOfAllAbstractGraphs", mainCluster, newMainCluster));
				newMainCluster.replaceSubClusterBy(newTaskCluster);
			}
		}

		if (PRINT_LEVEL >= PrintLevel.PRINT_ALL)
		{
			System.out.println("Generated " + (generatedClusters.size() - nbClustersBefore) + " clusters with method putTaskInParallelOfAllAbstractGraphs()");
		}
	}

	private void putTaskInSequenceOfAnyCombinationOfAbstractGraph(final ArrayList<Triple<String, Cluster, Cluster>> generatedClusters,
																  final Cluster mainCluster,
																  final Cluster taskCluster,
																  final Node nodeToCombine)
	{
		final int nbClustersBefore = generatedClusters.size();

		final Collection<Collection<AbstractGraph>> combinations = Utils.getCombinationsOf(taskCluster.abstractGraphs());

		if (PRINT_LEVEL >= PrintLevel.PRINT_ALL)
		{
			System.out.println("Number of abstract graphs of the current cluster: " + taskCluster.abstractGraphs().size());
		}

		for (Collection<AbstractGraph> combination : combinations)
		{
			if (combination.size() <= 1) continue;

			final Cluster newMainClusterBefore = mainCluster == null ? null : mainCluster.copy();
			final Cluster newTaskClusterBefore = taskCluster.copy();

			//We remove all the abstract graphs corresponding to the current combination
			newTaskClusterBefore.abstractGraphs().removeAll(combination);

			//We add a new abstract graph corresponding to the removed graphs preceded/followed by the elected task
			final AbstractNode abstractNodeBefore = new AbstractNode();
			abstractNodeBefore.addNode(nodeToCombine.weakCopy());
			final AbstractGraph abstractGraphBefore = new AbstractGraph(abstractNodeBefore);
			newTaskClusterBefore.addAbstractGraph(abstractGraphBefore);
			newTaskClusterBefore.addElement(new EnhancedNode(nodeToCombine));
			final AbstractNode summaryNodeBefore = new AbstractNode();
			abstractNodeBefore.setSuccessor(summaryNodeBefore);

			for (AbstractGraph abstractGraph : combination)
			{
				summaryNodeBefore.addSubgraph(abstractGraph.copy());
			}

			if (mainCluster == null)
			{
				generatedClusters.add(new Triple<>("putTaskInSequenceOfAnyCombinationOfAbstractGraph", taskCluster, newTaskClusterBefore));
			}
			else
			{
				if (newMainClusterBefore.equals(newTaskClusterBefore))
				{
					generatedClusters.add(new Triple<>("putTaskInSequenceOfAnyCombinationOfAbstractGraph", taskCluster, newTaskClusterBefore));
				}
				else
				{
					generatedClusters.add(new Triple<>("putTaskInSequenceOfAnyCombinationOfAbstractGraph", taskCluster, newMainClusterBefore));
					newMainClusterBefore.replaceSubClusterBy(newTaskClusterBefore);
				}
			}

			//-----------------------------------------------------------------------------------------------

			final Cluster newMainClusterAfter = mainCluster == null ? null : mainCluster.copy();
			final Cluster newTaskClusterAfter = taskCluster.copy();

			//We remove all the abstract graphs corresponding to the current combination
			newTaskClusterAfter.abstractGraphs().removeAll(combination);

			final AbstractNode abstractNodeAfter = new AbstractNode();
			abstractNodeAfter.addNode(nodeToCombine.weakCopy());
			final AbstractNode summaryNodeAfter = new AbstractNode();
			summaryNodeAfter.setSuccessor(abstractNodeAfter);
			final AbstractGraph abstractGraphAfter = new AbstractGraph(summaryNodeAfter);
			newTaskClusterAfter.addAbstractGraph(abstractGraphAfter);
			newTaskClusterAfter.addElement(new EnhancedNode(nodeToCombine));

			for (AbstractGraph abstractGraph : combination)
			{
				summaryNodeAfter.addSubgraph(abstractGraph.copy());
			}

			if (mainCluster == null)
			{
				generatedClusters.add(new Triple<>("putTaskInSequenceOfAnyCombinationOfAbstractGraph", taskCluster, newTaskClusterAfter));
			}
			else
			{
				if (newMainClusterAfter.equals(newTaskClusterAfter))
				{
					generatedClusters.add(new Triple<>("putTaskInSequenceOfAnyCombinationOfAbstractGraph", taskCluster, newTaskClusterAfter));
				}
				else
				{
					generatedClusters.add(new Triple<>("putTaskInSequenceOfAnyCombinationOfAbstractGraph", mainCluster, newMainClusterAfter));
					newMainClusterAfter.replaceSubClusterBy(newTaskClusterAfter);
				}
			}
		}

		if (PRINT_LEVEL >= PrintLevel.PRINT_ALL)
		{
			System.out.println("Generated " + (generatedClusters.size() - nbClustersBefore) + " clusters with method putTaskInSequenceOfAnyCombinationOfAbstractGraph()");
		}
	}

	private void putTaskInParallelOfAllOrderedCombinationsOfNodes(final ArrayList<Triple<String, Cluster, Cluster>> generatedClusters,
																  final Cluster mainCluster,
																  final Cluster taskCluster,
																  final Node nodeToCombine)
	{
		final int nbClustersBefore = generatedClusters.size();

		//Handle main abstract graphs of the cluster
		for (AbstractGraph abstractGraph : taskCluster.abstractGraphs())
		{
			final ArrayList<AbstractNode> nodesSequence = abstractGraph.extractNodes();
			final List<List<AbstractNode>> orderedCombinations = Utils.getOrderedCombinationsOf(nodesSequence);

			for (List<AbstractNode> combination : orderedCombinations)
			{
				final AbstractGraph newAbstractGraph = new AbstractGraph();
				final List<AbstractNode> nodesBefore = this.getAllNodesBefore(nodesSequence, combination.get(0));
				final List<AbstractNode> nodesAfter = this.getAllNodesAfter(nodesSequence, combination.get(combination.size() - 1));

				//Generate the new node containing the elected task
				final AbstractNode intermediaryNode = new AbstractNode();
				intermediaryNode.addNode(nodeToCombine.weakCopy());

				//Add it the combination in the form of a subgraph
				AbstractNode subgraphCurrentNode = combination.remove(0).copy();
				final AbstractGraph combinedSubgraph = new AbstractGraph(subgraphCurrentNode);
				intermediaryNode.addSubgraph(combinedSubgraph);

				for (AbstractNode abstractNode : combination)
				{
					final AbstractNode nextSubgraphNode = abstractNode.copy();
					subgraphCurrentNode.setSuccessor(nextSubgraphNode);
					subgraphCurrentNode = nextSubgraphNode;
				}

				//Add all the nodes before the intermediary node
				if (nodesBefore.isEmpty())
				{
					newAbstractGraph.setStartNode(intermediaryNode);
				}
				else
				{
					AbstractNode currentNode = nodesBefore.remove(0).copy();
					newAbstractGraph.setStartNode(currentNode);

					for (AbstractNode abstractNode : nodesBefore)
					{
						final AbstractNode nextNode = abstractNode.copy();
						currentNode.setSuccessor(nextNode);
						currentNode = nextNode;
					}

					currentNode.setSuccessor(intermediaryNode);
				}

				//Add all the nodes after the intermediary node
				if (!nodesAfter.isEmpty())
				{
					AbstractNode currentNode = intermediaryNode;

					for (AbstractNode abstractNode : nodesAfter)
					{
						final AbstractNode nextNode = abstractNode.copy();
						currentNode.setSuccessor(nextNode);
						currentNode = nextNode;
					}
				}

				//Add the generated combination to the list
				final Cluster newMainCluster = mainCluster == null ? null : mainCluster.copy();
				final Cluster newTaskCluster = taskCluster.copy();

				newTaskCluster.abstractGraphs().remove(abstractGraph);
				newTaskCluster.abstractGraphs().add(newAbstractGraph);
				newTaskCluster.addElement(new EnhancedNode(nodeToCombine));

				if (mainCluster == null)
				{
					generatedClusters.add(new Triple<>("putTaskInParallelOfAllOrderedCombinationsOfNodes", taskCluster, newTaskCluster));
				}
				else
				{
					if (newMainCluster.equals(newTaskCluster))
					{
						generatedClusters.add(new Triple<>("putTaskInParallelOfAllOrderedCombinationsOfNodes", taskCluster, newTaskCluster));
					}
					else
					{
						generatedClusters.add(new Triple<>("putTaskInParallelOfAllOrderedCombinationsOfNodes", mainCluster, newMainCluster));
						newMainCluster.replaceSubClusterBy(newTaskCluster);
					}
				}
			}
		}

		//Handle subgraphs of the cluster
		//Map with key = main abstract graph concerned, value = map with key = abstract node concerned and value = list of subgraphs
		final Map<AbstractGraph, ArrayList<AbstractGraph>> correspondence = new HashMap<>();

		for (AbstractGraph abstractGraph : taskCluster.abstractGraphs())
		{
			correspondence.put(abstractGraph, null);
		}

		final Map<AbstractGraph, Map<AbstractNode, ArrayList<AbstractGraph>>> currentSubGraphs = this.findSubGraphsOf(correspondence);

		while (!currentSubGraphs.isEmpty())
		{
			for (AbstractGraph mainGraph : currentSubGraphs.keySet())
			{
				final Map<AbstractNode, ArrayList<AbstractGraph>> currentMap = currentSubGraphs.get(mainGraph);

				for (AbstractNode currentAbstractNode : currentMap.keySet())
				{
					final ArrayList<AbstractGraph> graphsToProceed = currentMap.get(currentAbstractNode);

					for (AbstractGraph abstractGraph : graphsToProceed)
					{
						final ArrayList<AbstractNode> nodesSequence = abstractGraph.extractNodes();
						final List<List<AbstractNode>> orderedCombinations = Utils.getOrderedCombinationsOf(nodesSequence);

						for (List<AbstractNode> combination : orderedCombinations)
						{
							if (combination.size() == nodesSequence.size()) continue;

							final AbstractGraph newAbstractGraph = new AbstractGraph();
							final List<AbstractNode> nodesBefore = this.getAllNodesBefore(nodesSequence, combination.get(0));
							final List<AbstractNode> nodesAfter = this.getAllNodesAfter(nodesSequence, combination.get(combination.size() - 1));

							//Generate the new node containing the elected task
							final AbstractNode intermediaryNode = new AbstractNode();
							intermediaryNode.addNode(nodeToCombine.weakCopy());

							//Add it the combination in the form of a subgraph
							AbstractNode subgraphCurrentNode = combination.remove(0).copy();
							final AbstractGraph combinedSubgraph = new AbstractGraph(subgraphCurrentNode);
							intermediaryNode.addSubgraph(combinedSubgraph);

							for (AbstractNode abstractNode : combination)
							{
								final AbstractNode nextSubgraphNode = abstractNode.copy();
								subgraphCurrentNode.setSuccessor(nextSubgraphNode);
								subgraphCurrentNode = nextSubgraphNode;
							}

							//Add all the nodes before the intermediary node
							if (nodesBefore.isEmpty())
							{
								newAbstractGraph.setStartNode(intermediaryNode);
							}
							else
							{
								AbstractNode currentNode = nodesBefore.remove(0).copy();
								newAbstractGraph.setStartNode(currentNode);

								for (AbstractNode abstractNode : nodesBefore)
								{
									final AbstractNode nextNode = abstractNode.copy();
									currentNode.setSuccessor(nextNode);
									currentNode = nextNode;
								}

								currentNode.setSuccessor(intermediaryNode);
							}

							//Add all the nodes after the intermediary node
							if (!nodesAfter.isEmpty())
							{
								AbstractNode currentNode = intermediaryNode;

								for (AbstractNode abstractNode : nodesAfter)
								{
									final AbstractNode nextNode = abstractNode.copy();
									currentNode.setSuccessor(nextNode);
									currentNode = nextNode;
								}
							}

							//Add the generated combination to the list
							final Cluster newMainCluster = mainCluster == null ? null : mainCluster.copy();
							final Cluster newTaskCluster = taskCluster.copy();

							newTaskCluster.abstractGraphs().remove(mainGraph);

							final AbstractGraph newAbstractGraphFinal = mainGraph.copy();
							final AbstractNode nodeContainingSubgraph = newAbstractGraphFinal.findNodeOfID(currentAbstractNode.id());
							nodeContainingSubgraph.subGraphs().remove(abstractGraph);
							nodeContainingSubgraph.addSubgraph(newAbstractGraph);

							newTaskCluster.addAbstractGraph(newAbstractGraphFinal);
							newTaskCluster.addElement(new EnhancedNode(nodeToCombine));

							if (mainCluster == null)
							{
								generatedClusters.add(new Triple<>("putTaskInParallelOfAllOrderedCombinationsOfNodes", taskCluster, newTaskCluster));
							}
							else
							{
								if (newMainCluster.equals(newTaskCluster))
								{
									generatedClusters.add(new Triple<>("putTaskInParallelOfAllOrderedCombinationsOfNodes", taskCluster, newTaskCluster));
								}
								else
								{
									generatedClusters.add(new Triple<>("putTaskInParallelOfAllOrderedCombinationsOfNodes", mainCluster, newMainCluster));
									newMainCluster.replaceSubClusterBy(newTaskCluster);
								}
							}
						}
					}
				}
			}

			currentSubGraphs.clear();
			currentSubGraphs.putAll(this.findSubGraphsOf(correspondence));
		}

		if (PRINT_LEVEL >= PrintLevel.PRINT_ALL)
		{
			System.out.println("Generated " + (generatedClusters.size() - nbClustersBefore) + " clusters with method putTaskInParallelOfAllOrderedCombinationsOfNodes()");
		}
	}

	private Map<AbstractGraph, Map<AbstractNode, ArrayList<AbstractGraph>>> findSubGraphsOf(final Map<AbstractGraph, ArrayList<AbstractGraph>> correspondence)
	{
		final Map<AbstractGraph, Map<AbstractNode, ArrayList<AbstractGraph>>> subgraphs = new HashMap<>();
		final Map<AbstractGraph, ArrayList<AbstractGraph>> nextCorrespondences = new HashMap<>();

		for (AbstractGraph mainAbstractGraph : correspondence.keySet())
		{
			final ArrayList<AbstractGraph> processedSubgraphs = correspondence.get(mainAbstractGraph);

			if (processedSubgraphs == null)
			{
				//We look for the subgraphs of the main abstract graphs
				final ArrayList<AbstractNode> mainAbstractNodes = mainAbstractGraph.extractNodes();

				for (AbstractNode mainAbstractNode : mainAbstractNodes)
				{
					if (!mainAbstractNode.subGraphs().isEmpty())
					{
						final Map<AbstractNode, ArrayList<AbstractGraph>> currentMainMap = subgraphs.computeIfAbsent(mainAbstractGraph, m -> new HashMap<>());
						final ArrayList<AbstractGraph> currentSubList = currentMainMap.computeIfAbsent(mainAbstractNode, a -> new ArrayList<>());
						currentSubList.addAll(mainAbstractNode.subGraphs());

						final ArrayList<AbstractGraph> nextGraphs = nextCorrespondences.computeIfAbsent(mainAbstractGraph, a -> new ArrayList<>());
						nextGraphs.addAll(mainAbstractNode.subGraphs());
					}
				}
			}
			else
			{
				//We look for subgraphs of subgraphs
				for (AbstractGraph subgraph : processedSubgraphs)
				{
					final ArrayList<AbstractNode> abstractSubNodes = subgraph.extractNodes();

					for (AbstractNode abstractSubNode : abstractSubNodes)
					{
						if (!abstractSubNode.subGraphs().isEmpty())
						{
							final Map<AbstractNode, ArrayList<AbstractGraph>> currentMainMap = subgraphs.computeIfAbsent(mainAbstractGraph, m -> new HashMap<>());
							final ArrayList<AbstractGraph> currentSubList = currentMainMap.computeIfAbsent(abstractSubNode, a -> new ArrayList<>());
							currentSubList.addAll(abstractSubNode.subGraphs());

							final ArrayList<AbstractGraph> nextGraphs = nextCorrespondences.computeIfAbsent(mainAbstractGraph, a -> new ArrayList<>());
							nextGraphs.addAll(abstractSubNode.subGraphs());
						}
					}
				}
			}
		}

		correspondence.clear();
		correspondence.putAll(nextCorrespondences);
		return subgraphs;
	}

	private void putTaskInSequenceBeforeAfterOrBetweenAnyNode(final ArrayList<Triple<String, Cluster, Cluster>> generatedClusters,
															  final Cluster mainCluster,
															  final Cluster taskCluster,
															  final Node nodeToCombine)
	{
		final int nbClustersBefore = generatedClusters.size();

		//Handle main graphs
		for (AbstractGraph abstractGraph : taskCluster.abstractGraphs())
		{
			for (int i = 0; i < abstractGraph.extractNodes().size() - 1; i++)
			{
				final AbstractGraph abstractGraphCopy = abstractGraph.copy();
				final ArrayList<AbstractNode> copiedNodes = abstractGraphCopy.extractNodes();
				final AbstractNode previousNode = copiedNodes.get(i);
				final AbstractNode nextNode = copiedNodes.get(i + 1);
				final AbstractNode newIntermediaryNode = new AbstractNode();
				newIntermediaryNode.addNode(nodeToCombine.weakCopy());

				previousNode.setSuccessor(newIntermediaryNode);
				newIntermediaryNode.setSuccessor(nextNode);

				//Add the generated combination to the list
				final Cluster newMainCluster = mainCluster == null ? null : mainCluster.copy();
				final Cluster newTaskCluster = taskCluster.copy();
				newTaskCluster.addElement(new EnhancedNode(nodeToCombine));

				newTaskCluster.abstractGraphs().remove(abstractGraph);
				newTaskCluster.abstractGraphs().add(abstractGraphCopy);

				if (mainCluster == null)
				{
					generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", taskCluster, newTaskCluster));
				}
				else
				{
					if (newMainCluster.equals(newTaskCluster))
					{
						generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", taskCluster, newTaskCluster));
					}
					else
					{
						generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", mainCluster, newMainCluster));
						newMainCluster.replaceSubClusterBy(newTaskCluster);
					}
				}
			}

			//BEFORE

			final AbstractGraph abstractGraphCopyBefore = abstractGraph.copy();
			final AbstractNode initialNode = new AbstractNode();
			initialNode.addNode(nodeToCombine.weakCopy());
			initialNode.setSuccessor(abstractGraphCopyBefore.startNode());
			abstractGraphCopyBefore.setStartNode(initialNode);

			//Add the generated combination to the list
			final Cluster newMainClusterBefore = mainCluster == null ? null : mainCluster.copy();
			final Cluster newTaskClusterBefore = taskCluster.copy();
			newTaskClusterBefore.addElement(new EnhancedNode(nodeToCombine));

			newTaskClusterBefore.abstractGraphs().remove(abstractGraph);
			newTaskClusterBefore.abstractGraphs().add(abstractGraphCopyBefore);

			if (mainCluster == null)
			{
				generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", taskCluster, newTaskClusterBefore));
			}
			else
			{
				if (newMainClusterBefore.equals(newTaskClusterBefore))
				{
					generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", taskCluster, newTaskClusterBefore));
				}
				else
				{
					generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", mainCluster, newMainClusterBefore));
					newMainClusterBefore.replaceSubClusterBy(newTaskClusterBefore);
				}
			}

			//AFTER

			final AbstractGraph abstractGraphCopyAfter = abstractGraph.copy();
			final AbstractNode lastNode = new AbstractNode();
			abstractGraphCopyAfter.lastNode().setSuccessor(lastNode);
			lastNode.addNode(nodeToCombine.weakCopy());

			//Add the generated combination to the list
			final Cluster newMainClusterAfter = mainCluster == null ? null : mainCluster.copy();
			final Cluster newTaskClusterAfter = taskCluster.copy();
			newTaskClusterAfter.addElement(new EnhancedNode(nodeToCombine));

			newTaskClusterAfter.abstractGraphs().remove(abstractGraph);
			newTaskClusterAfter.abstractGraphs().add(abstractGraphCopyAfter);

			if (mainCluster == null)
			{
				generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", taskCluster, newTaskClusterAfter));
			}
			else
			{
				if (newMainClusterAfter.equals(newTaskClusterAfter))
				{
					generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", taskCluster, newTaskClusterAfter));
				}
				else
				{
					generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", mainCluster, newMainClusterAfter));
					newMainClusterAfter.replaceSubClusterBy(newTaskClusterAfter);
				}
			}
		}

		//Handle subgraphs
		final Map<AbstractGraph, ArrayList<AbstractGraph>> correspondence = new HashMap<>();

		//System.out.println("Task cluster:\n\n" + taskCluster.stringify(0));
		//System.out.println("Task cluster has " + taskCluster.abstractGraphs().size() + " abstract graphs");

		for (AbstractGraph abstractGraph : taskCluster.abstractGraphs())
		{
			correspondence.put(abstractGraph, null);
		}

		//System.out.println("Correspondence contains " + correspondence.size() + " elements.");
		final Map<AbstractGraph, Map<AbstractNode, ArrayList<Node>>> currentIsolatedNodes = this.findIsolatedNodesOf(correspondence);
		//System.out.println("Correspondence contains " + correspondence.size() + " elements before.");
		final Map<AbstractGraph, Map<AbstractNode, ArrayList<AbstractGraph>>> currentSubGraphs = this.findSubGraphsOf(correspondence);

		//System.out.println("Found " + currentSubGraphs.size() + " subgraphs and " + currentIsolatedNodes.size() + " isolated nodes.");

		while (!currentSubGraphs.isEmpty()
				|| !currentIsolatedNodes.isEmpty())
		{
			for (AbstractGraph currentMainGraph : currentSubGraphs.keySet())
			{
				final Map<AbstractNode, ArrayList<AbstractGraph>> mainMap = currentSubGraphs.get(currentMainGraph);

				for (AbstractNode currentAbstractNode : mainMap.keySet())
				{
					final ArrayList<AbstractGraph> currentSubgraphs = mainMap.get(currentAbstractNode);

					for (AbstractGraph currentSubgraph : currentSubgraphs)
					{
						for (int i = 0; i < currentSubgraph.extractNodes().size() - 1; i++)
						{
							final AbstractGraph abstractGraphCopy = currentSubgraph.copy();
							final ArrayList<AbstractNode> copiedNodes = abstractGraphCopy.extractNodes();
							final AbstractNode previousNode = copiedNodes.get(i);
							final AbstractNode nextNode = copiedNodes.get(i + 1);
							final AbstractNode newIntermediaryNode = new AbstractNode();
							newIntermediaryNode.addNode(nodeToCombine.weakCopy());

							previousNode.setSuccessor(newIntermediaryNode);
							newIntermediaryNode.setSuccessor(nextNode);

							//Add the generated combination to the list
							final Cluster newMainCluster = mainCluster == null ? null : mainCluster.copy();
							final Cluster newTaskCluster = taskCluster.copy();

							newTaskCluster.abstractGraphs().remove(currentMainGraph);

							final AbstractGraph newAbstractGraphFinal = currentMainGraph.copy();
							final AbstractNode nodeContainingSubgraph = newAbstractGraphFinal.findNodeOfID(currentAbstractNode.id());
							nodeContainingSubgraph.subGraphs().remove(currentSubgraph);
							nodeContainingSubgraph.addSubgraph(abstractGraphCopy);

							newTaskCluster.addAbstractGraph(newAbstractGraphFinal);
							newTaskCluster.addElement(new EnhancedNode(nodeToCombine));

							if (mainCluster == null)
							{
								generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", taskCluster, newTaskCluster));
							}
							else
							{
								if (newMainCluster.equals(newTaskCluster))
								{
									generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", taskCluster, newTaskCluster));
								}
								else
								{
									generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", mainCluster, newMainCluster));
									newMainCluster.replaceSubClusterBy(newTaskCluster);
								}
							}
						}

						//BEFORE

						final AbstractGraph abstractGraphCopyBefore = currentSubgraph.copy();
						final AbstractNode initialNode = new AbstractNode();
						initialNode.addNode(nodeToCombine.weakCopy());
						initialNode.setSuccessor(abstractGraphCopyBefore.startNode());
						abstractGraphCopyBefore.setStartNode(initialNode);

						//Add the generated combination to the list
						final Cluster newMainClusterBefore = mainCluster == null ? null : mainCluster.copy();
						final Cluster newTaskClusterBefore = taskCluster.copy();

						newTaskClusterBefore.abstractGraphs().remove(currentMainGraph);

						final AbstractGraph newAbstractGraphFinalBefore = currentMainGraph.copy();
						final AbstractNode nodeContainingSubgraphBefore = newAbstractGraphFinalBefore.findNodeOfID(currentAbstractNode.id());
						nodeContainingSubgraphBefore.subGraphs().remove(currentSubgraph);
						nodeContainingSubgraphBefore.addSubgraph(abstractGraphCopyBefore);

						newTaskClusterBefore.addAbstractGraph(newAbstractGraphFinalBefore);
						newTaskClusterBefore.addElement(new EnhancedNode(nodeToCombine));

						if (mainCluster == null)
						{
							generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", taskCluster, newTaskClusterBefore));
						}
						else
						{
							if (newMainClusterBefore.equals(newTaskClusterBefore))
							{
								generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", taskCluster, newTaskClusterBefore));
							}
							else
							{
								generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", mainCluster, newMainClusterBefore));
								newMainClusterBefore.replaceSubClusterBy(newTaskClusterBefore);
							}
						}

						//AFTER

						final AbstractGraph abstractGraphCopyAfter = currentSubgraph.copy();
						final AbstractNode lastNode = new AbstractNode();
						abstractGraphCopyAfter.lastNode().setSuccessor(lastNode);
						lastNode.addNode(nodeToCombine.weakCopy());

						//Add the generated combination to the list
						final Cluster newMainCluster = mainCluster == null ? null : mainCluster.copy();
						final Cluster newTaskCluster = taskCluster.copy();

						newTaskCluster.abstractGraphs().remove(currentMainGraph);

						final AbstractGraph newAbstractGraphFinal = currentMainGraph.copy();
						final AbstractNode nodeContainingSubgraph = newAbstractGraphFinal.findNodeOfID(currentAbstractNode.id());
						nodeContainingSubgraph.subGraphs().remove(currentSubgraph);
						nodeContainingSubgraph.addSubgraph(abstractGraphCopyAfter);

						newTaskCluster.addAbstractGraph(newAbstractGraphFinal);
						newTaskCluster.addElement(new EnhancedNode(nodeToCombine));

						if (mainCluster == null)
						{
							generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", taskCluster, newTaskCluster));
						}
						else
						{
							if (newMainCluster.equals(newTaskCluster))
							{
								generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", taskCluster, newTaskCluster));
							}
							else
							{
								generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", mainCluster, newMainCluster));
								newMainCluster.replaceSubClusterBy(newTaskCluster);
							}
						}
					}
				}
			}

			for (AbstractGraph currentMainGraph : currentIsolatedNodes.keySet())
			{
				final Map<AbstractNode, ArrayList<Node>> currentList = currentIsolatedNodes.get(currentMainGraph);

				for (AbstractNode currentAbstractNode : currentList.keySet())
				{
					final ArrayList<Node> isolatedNodes = currentList.get(currentAbstractNode);

					for (Node isolatedNode : isolatedNodes)
					{
						final AbstractNode abstractIsolatedNode1 = new AbstractNode();
						abstractIsolatedNode1.addNode(isolatedNode.weakCopy());

						final AbstractNode abstractNodeToMove1 = new AbstractNode();
						abstractNodeToMove1.addNode(nodeToCombine.weakCopy());

						final AbstractGraph abstractGraph1 = new AbstractGraph(abstractIsolatedNode1);
						abstractIsolatedNode1.setSuccessor(abstractNodeToMove1);

						//Add the generated combination to the list
						final Cluster newMainClusterBefore = mainCluster == null ? null : mainCluster.copy();
						final Cluster newTaskClusterBefore = taskCluster.copy();

						newTaskClusterBefore.abstractGraphs().remove(currentMainGraph);

						final AbstractGraph newAbstractGraphBefore = currentMainGraph.copy();
						final AbstractNode nodeContainingSubgraphBefore = newAbstractGraphBefore.findNodeOfID(currentAbstractNode.id());
						nodeContainingSubgraphBefore.listNodes().remove(isolatedNode);
						nodeContainingSubgraphBefore.addSubgraph(abstractGraph1);

						newTaskClusterBefore.addAbstractGraph(newAbstractGraphBefore);
						newTaskClusterBefore.addElement(new EnhancedNode(nodeToCombine));

						if (mainCluster == null)
						{
							generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", taskCluster, newTaskClusterBefore));
						}
						else
						{
							if (newMainClusterBefore.equals(newTaskClusterBefore))
							{
								generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", taskCluster, newTaskClusterBefore));
							}
							else
							{
								generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", taskCluster, newMainClusterBefore));
								newMainClusterBefore.replaceSubClusterBy(newTaskClusterBefore);
							}
						}

						final AbstractNode abstractIsolatedNode2 = new AbstractNode();
						abstractIsolatedNode2.addNode(isolatedNode.weakCopy());

						final AbstractNode abstractNodeToMove2 = new AbstractNode();
						abstractNodeToMove2.addNode(nodeToCombine.weakCopy());

						final AbstractGraph abstractGraph2 = new AbstractGraph(abstractNodeToMove2);
						abstractNodeToMove2.setSuccessor(abstractIsolatedNode2);

						//Add the generated combination to the list
						final Cluster newMainClusterAfter = mainCluster == null ? null : mainCluster.copy();
						final Cluster newTaskClusterAfter = taskCluster.copy();

						newTaskClusterAfter.abstractGraphs().remove(currentMainGraph);

						final AbstractGraph newAbstractGraphAfter = currentMainGraph.copy();
						final AbstractNode nodeContainingSubgraphAfter = newAbstractGraphAfter.findNodeOfID(currentAbstractNode.id());
						nodeContainingSubgraphAfter.listNodes().remove(isolatedNode);
						nodeContainingSubgraphAfter.addSubgraph(abstractGraph2);

						newTaskClusterAfter.addAbstractGraph(newAbstractGraphAfter);
						newTaskClusterAfter.addElement(new EnhancedNode(nodeToCombine));

						if (mainCluster == null)
						{
							generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", taskCluster, newTaskClusterAfter));
						}
						else
						{
							if (newMainClusterAfter.equals(newTaskClusterAfter))
							{
								generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", taskCluster, newTaskClusterAfter));
							}
							else
							{
								generatedClusters.add(new Triple<>("putTaskInSequenceBeforeAfterOrBetweenAnyNode", taskCluster, newMainClusterAfter));
								newMainClusterAfter.replaceSubClusterBy(newTaskClusterAfter);
							}
						}
					}
				}
			}

			currentSubGraphs.clear();
			currentSubGraphs.putAll(this.findSubGraphsOf(correspondence));

			currentIsolatedNodes.clear();
			currentIsolatedNodes.putAll(this.findIsolatedNodesOf(correspondence));
		}

		if (PRINT_LEVEL >= PrintLevel.PRINT_ALL)
		{
			System.out.println("Generated " + (generatedClusters.size() - nbClustersBefore) + " clusters with method putTaskInSequenceBeforeAfterOrBetweenAnyNode()");
		}
	}

	private Map<AbstractGraph, Map<AbstractNode, ArrayList<Node>>> findIsolatedNodesOf(final Map<AbstractGraph, ArrayList<AbstractGraph>> correspondence)
	{
		final Map<AbstractGraph, Map<AbstractNode, ArrayList<Node>>> isolatedNodes = new HashMap<>();

		for (AbstractGraph mainAbstractGraph : correspondence.keySet())
		{
			final ArrayList<AbstractGraph> processedSubgraphs = correspondence.get(mainAbstractGraph);

			if (processedSubgraphs == null)
			{
				//We look for the isolated nodes of the main abstract graphs
				final ArrayList<AbstractNode> mainAbstractNodes = mainAbstractGraph.extractNodes();

				if (PRINT_LEVEL >= PrintLevel.PRINT_ALL)
				{
					System.out.println("Processed abstract graph:\n\n" + mainAbstractGraph.stringify(0));
				}

				for (AbstractNode mainAbstractNode : mainAbstractNodes)
				{
					if (!mainAbstractNode.listNodes().isEmpty())
					{
						if (mainAbstractNode.listNodes().size() > 1
								|| !mainAbstractNode.subGraphs().isEmpty())
						{
							final Map<AbstractNode, ArrayList<Node>> currentMainMap = isolatedNodes.computeIfAbsent(mainAbstractGraph, m -> new HashMap<>());
							final ArrayList<Node> currentSubList = currentMainMap.computeIfAbsent(mainAbstractNode, a -> new ArrayList<>());
							currentSubList.addAll(mainAbstractNode.listNodes());
						}
					}
				}
			}
			else
			{
				//We look for isolatedNodes of isolatedNodes
				for (AbstractGraph subgraph : processedSubgraphs)
				{
					final ArrayList<AbstractNode> abstractSubNodes = subgraph.extractNodes();

					for (AbstractNode abstractSubNode : abstractSubNodes)
					{
						if (!abstractSubNode.listNodes().isEmpty())
						{
							if (abstractSubNode.listNodes().size() > 1
									|| !abstractSubNode.subGraphs().isEmpty())
							{
								final Map<AbstractNode, ArrayList<Node>> currentMainMap = isolatedNodes.computeIfAbsent(mainAbstractGraph, m -> new HashMap<>());
								final ArrayList<Node> currentSubList = currentMainMap.computeIfAbsent(abstractSubNode, a -> new ArrayList<>());
								currentSubList.addAll(abstractSubNode.listNodes());
							}
						}
					}
				}
			}
		}

		return isolatedNodes;
	}

	private void putTaskInSequenceOfAllCombinationsOfTaskAndSubgraph(final ArrayList<Triple<String, Cluster, Cluster>> generatedClusters,
																	 final Cluster mainCluster,
																	 final Cluster taskCluster,
																	 final Node nodeToCombine)
	{
		final int nbClustersBefore = generatedClusters.size();

		//Handle main graphs
		for (AbstractGraph abstractGraph : taskCluster.abstractGraphs())
		{
			final ArrayList<AbstractNode> extractedNodes = abstractGraph.extractNodes();

			for (AbstractNode abstractNode : extractedNodes)
			{
				final int modifiedNodeIndex = extractedNodes.indexOf(abstractNode);
				final Collection<AbstractGraph> mergeSetForCombination = new ArrayList<>(abstractNode.subGraphs());

				for (Node node : abstractNode.listNodes())
				{
					final AbstractNode subNode = new AbstractNode();
					subNode.addNode(node.weakCopy());
					final AbstractGraph subGraph = new AbstractGraph(subNode);
					mergeSetForCombination.add(subGraph);
				}

				final Collection<Collection<AbstractGraph>> combinations = Utils.getCombinationsOf(mergeSetForCombination);

				for (Collection<AbstractGraph> combination : combinations)
				{
					if (combination.size() >= 2
							&& combination.size() < mergeSetForCombination.size())
					{
						//Compute subgraphs to put back in parallel of the new node
						final Collection<AbstractGraph> remainingGraphs = new ArrayList<>(mergeSetForCombination);
						remainingGraphs.removeAll(combination);

						//BEFORE

						//Replace the old node by the new one
						final AbstractNode replacingNodeBefore = new AbstractNode();
						final AbstractGraph newAbstractGraphBefore = abstractGraph.copy();
						final ArrayList<AbstractNode> newExtractedNodesBefore = newAbstractGraphBefore.extractNodes();
						this.removeSuccessors(newExtractedNodesBefore);
						newExtractedNodesBefore.set(modifiedNodeIndex, replacingNodeBefore);

						//Setup new node properly
						for (AbstractGraph remainingGraph : remainingGraphs)
						{
							replacingNodeBefore.addSubgraph(remainingGraph.copy());
						}

						final AbstractNode initialNode = new AbstractNode();
						final AbstractGraph summarizingGraphBefore = new AbstractGraph(initialNode);
						replacingNodeBefore.addSubgraph(summarizingGraphBefore);
						initialNode.addNode(nodeToCombine.weakCopy());

						final AbstractNode summaryNodeBefore = new AbstractNode();
						initialNode.setSuccessor(summaryNodeBefore);

						for (AbstractGraph graph : combination)
						{
							summaryNodeBefore.addSubgraph(graph.copy());
						}

						//Regenerate abstract graph from node list
						final AbstractGraph finalAbstractGraphBefore = new AbstractGraph(newExtractedNodesBefore.remove(0));
						AbstractNode currentNodeBefore = finalAbstractGraphBefore.startNode();

						for (AbstractNode copiedNodeBefore : newExtractedNodesBefore)
						{
							currentNodeBefore.setSuccessor(copiedNodeBefore);
							currentNodeBefore = copiedNodeBefore;
						}

						//Add the generated combination to the list
						final Cluster newMainClusterBefore = mainCluster == null ? null : mainCluster.copy();
						final Cluster newTaskClusterBefore = taskCluster.copy();
						newTaskClusterBefore.addElement(new EnhancedNode(nodeToCombine));

						newTaskClusterBefore.abstractGraphs().remove(abstractGraph);
						newTaskClusterBefore.abstractGraphs().add(finalAbstractGraphBefore);

						if (mainCluster == null)
						{
							generatedClusters.add(new Triple<>("putTaskInSequenceOfAllCombinationsOfTaskAndSubgraph", taskCluster, newTaskClusterBefore));
						}
						else
						{
							if (newMainClusterBefore.equals(newTaskClusterBefore))
							{
								generatedClusters.add(new Triple<>("putTaskInSequenceOfAllCombinationsOfTaskAndSubgraph", taskCluster, newTaskClusterBefore));
							}
							else
							{
								generatedClusters.add(new Triple<>("putTaskInSequenceOfAllCombinationsOfTaskAndSubgraph", mainCluster, newMainClusterBefore));
								newMainClusterBefore.replaceSubClusterBy(newTaskClusterBefore);
							}
						}

						//AFTER

						//Replace the old node by the new one
						final AbstractNode replacingNodeAfter = new AbstractNode();
						final AbstractGraph newAbstractGraphAfter = abstractGraph.copy();
						final ArrayList<AbstractNode> newExtractedNodesAfter = newAbstractGraphAfter.extractNodes();
						this.removeSuccessors(newExtractedNodesAfter);
						newExtractedNodesAfter.set(modifiedNodeIndex, replacingNodeAfter);

						//Setup new node properly
						for (AbstractGraph remainingGraph : remainingGraphs)
						{
							replacingNodeAfter.addSubgraph(remainingGraph.copy());
						}

						final AbstractNode lastNode = new AbstractNode();
						final AbstractNode summaryNodeAfter = new AbstractNode();
						summaryNodeAfter.setSuccessor(lastNode);

						final AbstractGraph summarizingGraphAfter = new AbstractGraph(summaryNodeAfter);
						replacingNodeAfter.addSubgraph(summarizingGraphAfter);
						lastNode.addNode(nodeToCombine.weakCopy());

						for (AbstractGraph graph : combination)
						{
							summaryNodeAfter.addSubgraph(graph.copy());
						}

						//Regenerate abstract graph from node list
						final AbstractGraph finalAbstractGraphAfter = new AbstractGraph(newExtractedNodesAfter.remove(0));
						AbstractNode currentNodeAfter = finalAbstractGraphAfter.startNode();

						for (AbstractNode copiedNodeAfter : newExtractedNodesAfter)
						{
							currentNodeAfter.setSuccessor(copiedNodeAfter);
							currentNodeAfter = copiedNodeAfter;
						}

						//Add the generated combination to the list
						final Cluster newMainClusterAfter = mainCluster == null ? null : mainCluster.copy();
						final Cluster newTaskClusterAfter = taskCluster.copy();

						newTaskClusterAfter.abstractGraphs().remove(abstractGraph);
						newTaskClusterAfter.abstractGraphs().add(finalAbstractGraphAfter);
						newTaskClusterAfter.addElement(new EnhancedNode(nodeToCombine));

						if (mainCluster == null)
						{
							generatedClusters.add(new Triple<>("putTaskInSequenceOfAllCombinationsOfTaskAndSubgraph", taskCluster, newTaskClusterAfter));
						}
						else
						{
							if (newMainClusterAfter.equals(newTaskClusterAfter))
							{
								generatedClusters.add(new Triple<>("putTaskInSequenceOfAllCombinationsOfTaskAndSubgraph", taskCluster, newTaskClusterAfter));
							}
							else
							{
								generatedClusters.add(new Triple<>("putTaskInSequenceOfAllCombinationsOfTaskAndSubgraph", mainCluster, newMainClusterAfter));
								newMainClusterAfter.replaceSubClusterBy(newTaskClusterAfter);
							}
						}
					}
				}
			}
		}

		//Handle subgraphs
		final Map<AbstractGraph, ArrayList<AbstractGraph>> correspondence = new HashMap<>();

		for (AbstractGraph abstractGraph : taskCluster.abstractGraphs())
		{
			correspondence.put(abstractGraph, null);
		}

		final Map<AbstractGraph, Map<AbstractNode, ArrayList<AbstractGraph>>> currentSubGraphs = this.findSubGraphsOf(correspondence);

		while (!currentSubGraphs.isEmpty())
		{
			for (AbstractGraph currentMainGraph : currentSubGraphs.keySet())
			{
				final Map<AbstractNode, ArrayList<AbstractGraph>> currentMainMap = currentSubGraphs.get(currentMainGraph);

				for (AbstractNode currentAbstractNode : currentMainMap.keySet())
				{
					final ArrayList<AbstractGraph> currentSubgraphs = currentMainMap.get(currentAbstractNode);

					for (AbstractGraph currentSubgraph : currentSubgraphs)
					{
						final ArrayList<AbstractNode> extractedNodes = currentSubgraph.extractNodes();

						for (AbstractNode abstractNode : extractedNodes)
						{
							final int modifiedNodeIndex = extractedNodes.indexOf(abstractNode);
							final Collection<AbstractGraph> mergeSetForCombination = new ArrayList<>(abstractNode.subGraphs());

							for (Node node : abstractNode.listNodes())
							{
								final AbstractNode subNode = new AbstractNode();
								subNode.addNode(node.weakCopy());
								final AbstractGraph subGraph = new AbstractGraph(subNode);
								mergeSetForCombination.add(subGraph);
							}

							final Collection<Collection<AbstractGraph>> combinations = Utils.getCombinationsOf(mergeSetForCombination);

							for (Collection<AbstractGraph> combination : combinations)
							{
								if (combination.size() >= 2)
								{
									//Compute subgraphs to put back in parallel of the new node
									final Collection<AbstractGraph> remainingGraphs = new ArrayList<>(mergeSetForCombination);
									remainingGraphs.removeAll(combination);

									//BEFORE

									//Replace the old node by the new one
									final AbstractNode replacingNodeBefore = new AbstractNode();
									final AbstractGraph newAbstractGraphBefore = currentSubgraph.copy();
									final ArrayList<AbstractNode> newExtractedNodesBefore = newAbstractGraphBefore.extractNodes();
									this.removeSuccessors(newExtractedNodesBefore);
									newExtractedNodesBefore.set(modifiedNodeIndex, replacingNodeBefore);

									//Setup new node properly
									for (AbstractGraph remainingGraph : remainingGraphs)
									{
										replacingNodeBefore.addSubgraph(remainingGraph.copy());
									}

									final AbstractNode initialNode = new AbstractNode();
									final AbstractGraph summarizingGraphBefore = new AbstractGraph(initialNode);
									replacingNodeBefore.addSubgraph(summarizingGraphBefore);
									initialNode.addNode(nodeToCombine.weakCopy());

									final AbstractNode summaryNodeBefore = new AbstractNode();
									initialNode.setSuccessor(summaryNodeBefore);

									for (AbstractGraph graph : combination)
									{
										summaryNodeBefore.addSubgraph(graph.copy());
									}

									//Regenerate abstract graph from node list
									final AbstractGraph finalAbstractGraphBefore = new AbstractGraph(newExtractedNodesBefore.remove(0));
									AbstractNode currentNodeBefore = finalAbstractGraphBefore.startNode();

									for (AbstractNode copiedNodeBefore : newExtractedNodesBefore)
									{
										currentNodeBefore.setSuccessor(copiedNodeBefore);
										currentNodeBefore = copiedNodeBefore;
									}

									//Add the generated combination to the list
									final Cluster newMainClusterBefore = mainCluster == null ? null : mainCluster.copy();
									final Cluster newTaskClusterBefore = taskCluster.copy();

									newTaskClusterBefore.abstractGraphs().remove(currentMainGraph);

									final AbstractGraph newMainAbstractGraphBefore = currentMainGraph.copy();
									final AbstractNode nodeContainingSubgraphBefore = newMainAbstractGraphBefore.findNodeOfID(currentAbstractNode.id());
									nodeContainingSubgraphBefore.subGraphs().remove(currentSubgraph);
									nodeContainingSubgraphBefore.addSubgraph(finalAbstractGraphBefore);

									newTaskClusterBefore.addAbstractGraph(newMainAbstractGraphBefore);
									newTaskClusterBefore.addElement(new EnhancedNode(nodeToCombine));

									if (mainCluster == null)
									{
										generatedClusters.add(new Triple<>("putTaskInSequenceOfAllCombinationsOfTaskAndSubgraph", taskCluster, newTaskClusterBefore));
									}
									else
									{
										if (newMainClusterBefore.equals(newTaskClusterBefore))
										{
											generatedClusters.add(new Triple<>("putTaskInSequenceOfAllCombinationsOfTaskAndSubgraph", taskCluster, newTaskClusterBefore));
										}
										else
										{
											generatedClusters.add(new Triple<>("putTaskInSequenceOfAllCombinationsOfTaskAndSubgraph", mainCluster, newMainClusterBefore));
											newMainClusterBefore.replaceSubClusterBy(newTaskClusterBefore);
										}
									}

									//AFTER

									//Replace the old node by the new one
									final AbstractNode replacingNodeAfter = new AbstractNode();
									final AbstractGraph newAbstractGraphAfter = currentSubgraph.copy();
									final ArrayList<AbstractNode> newExtractedNodesAfter = newAbstractGraphAfter.extractNodes();
									this.removeSuccessors(newExtractedNodesAfter);
									newExtractedNodesAfter.set(modifiedNodeIndex, replacingNodeAfter);

									//Setup new node properly
									for (AbstractGraph remainingGraph : remainingGraphs)
									{
										replacingNodeAfter.addSubgraph(remainingGraph.copy());
									}

									final AbstractNode lastNode = new AbstractNode();
									final AbstractNode summaryNodeAfter = new AbstractNode();
									summaryNodeAfter.setSuccessor(lastNode);

									final AbstractGraph summarizingGraphAfter = new AbstractGraph(summaryNodeAfter);
									replacingNodeAfter.addSubgraph(summarizingGraphAfter);
									lastNode.addNode(nodeToCombine.weakCopy());

									for (AbstractGraph graph : combination)
									{
										summaryNodeAfter.addSubgraph(graph.copy());
									}

									//Regenerate abstract graph from node list
									final AbstractGraph finalAbstractGraphAfter = new AbstractGraph(newExtractedNodesAfter.remove(0));
									AbstractNode currentNodeAfter = finalAbstractGraphAfter.startNode();

									for (AbstractNode copiedNodeAfter : newExtractedNodesAfter)
									{
										currentNodeAfter.setSuccessor(copiedNodeAfter);
										currentNodeAfter = copiedNodeAfter;
									}

									//Add the generated combination to the list
									final Cluster newMainClusterAfter = mainCluster == null ? null : mainCluster.copy();
									final Cluster newTaskClusterAfter = taskCluster.copy();

									newTaskClusterAfter.abstractGraphs().remove(currentMainGraph);

									final AbstractGraph newMainAbstractGraphAfter = currentMainGraph.copy();
									final AbstractNode nodeContainingSubgraphAfter = newMainAbstractGraphAfter.findNodeOfID(currentAbstractNode.id());
									nodeContainingSubgraphAfter.subGraphs().remove(currentSubgraph);
									nodeContainingSubgraphAfter.addSubgraph(finalAbstractGraphAfter);

									newTaskClusterAfter.addAbstractGraph(newMainAbstractGraphAfter);
									newTaskClusterAfter.addElement(new EnhancedNode(nodeToCombine));

									if (mainCluster == null)
									{
										generatedClusters.add(new Triple<>("putTaskInSequenceOfAllCombinationsOfTaskAndSubgraph", taskCluster, newTaskClusterAfter));
									}
									else
									{
										if (newMainClusterAfter.equals(newTaskClusterAfter))
										{
											generatedClusters.add(new Triple<>("putTaskInSequenceOfAllCombinationsOfTaskAndSubgraph", taskCluster, newTaskClusterAfter));
										}
										else
										{
											generatedClusters.add(new Triple<>("putTaskInSequenceOfAllCombinationsOfTaskAndSubgraph", mainCluster, newMainClusterAfter));
											newMainClusterAfter.replaceSubClusterBy(newTaskClusterAfter);
										}
									}
								}
							}
						}
					}
				}
			}

			currentSubGraphs.clear();
			currentSubGraphs.putAll(this.findSubGraphsOf(correspondence));
		}

		if (PRINT_LEVEL >= PrintLevel.PRINT_ALL)
		{
			System.out.println("Generated " + (generatedClusters.size() - nbClustersBefore) + " clusters with method putTaskInSequenceOfAllCombinationsOfTaskAndSubgraph()");
		}
	}

	//TODO VOIR COMMENT AJOUTER LA TACHE AUX CLUSTERS CORRECTEMENT (DUPLICATION DEMANDE CREATION D'UNE NOUVELLE ID DE TACHE)
	private void putTaskInsideAChoice(final ArrayList<Triple<String, Cluster, Cluster>> generatedClusters,
									  final Cluster mainCluster,
									  final Cluster taskCluster,
									  final Node nodeToCombine) throws BadDependencyException, IOException
	{
		final int nbClustersBefore = generatedClusters.size();

		for (EnhancedNode enhancedNode : taskCluster.elements())
		{
			if (enhancedNode.type() == EnhancedType.CHOICE)
			{
				final EnhancedChoice enhancedChoice = (EnhancedChoice) enhancedNode;
				final int nbPaths = enhancedChoice.clusters().size();
				final List<List<Pair<Node, Cluster>>> clustersPerPath = new ArrayList<>();

				for (Node node : enhancedChoice.choicePaths().keySet())
				{
					final Node nodeToCombineCopy = new Node(nodeToCombine.bpmnObject().copy());
					final Cluster cluster = enhancedChoice.choicePaths().get(node);
					final ArrayList<Triple<String, Cluster, Cluster>> subGeneratedClusters = new ArrayList<>();
					this.generateClusters(subGeneratedClusters, null, cluster, nodeToCombineCopy);
					final ArrayList<Pair<Node, Cluster>> currentPathClusters = new ArrayList<>();

					for (Triple<String, Cluster, Cluster> subGeneratedCluster : subGeneratedClusters)
					{
						currentPathClusters.add(new Pair<>(node, subGeneratedCluster.third()));
					}

					clustersPerPath.add(currentPathClusters);
				}

				final List<List<Pair<Node, Cluster>>> cartesianClusters = Utils.getCartesianProductOf(clustersPerPath);

				for (List<Pair<Node, Cluster>> cartesianCluster : cartesianClusters)
				{
					//We should have the same number of clusters than path after cartesianisation
					if (cartesianCluster.size() != nbPaths) throw new IllegalArgumentException();

					final Cluster taskClusterCopy = taskCluster.copy();
					final EnhancedChoice newEnhancedChoice = new EnhancedChoice(enhancedChoice.node());

					for (Pair<Node, Cluster> cluster : cartesianCluster)
					{
						newEnhancedChoice.putClusterWithKey(cluster.first(), cluster.second().copy());
					}

					taskClusterCopy.elements().remove(enhancedNode);
					taskClusterCopy.elements().add(newEnhancedChoice);

					if (mainCluster == null)
					{
						generatedClusters.add(new Triple<>("putTaskInsideAChoice", taskCluster, taskClusterCopy));
					}
					else
					{
						final Cluster newMainCluster = mainCluster.copy();

						if (newMainCluster.equals(taskClusterCopy))
						{
							generatedClusters.add(new Triple<>("putTaskInsideAChoice", taskCluster, taskClusterCopy));
						}
						else
						{
							generatedClusters.add(new Triple<>("putTaskInsideAChoice", mainCluster, newMainCluster));
							newMainCluster.replaceSubClusterBy(taskClusterCopy);
						}
					}
				}
			}
		}

		if (PRINT_LEVEL >= PrintLevel.PRINT_ALL)
		{
			System.out.println("Generated " + (generatedClusters.size() - nbClustersBefore) + " clusters with method putTaskInsideAChoice()");
		}
	}

	private List<AbstractNode> getAllNodesBefore(final ArrayList<AbstractNode> abstractNodes,
												 final AbstractNode firstExcludedNode)
	{
		final List<AbstractNode> nodesBefore = new ArrayList<>();

		for (AbstractNode abstractNode : abstractNodes)
		{
			if (abstractNode.id().equals(firstExcludedNode.id()))
			{
				break;
			}

			nodesBefore.add(abstractNode);
		}

		return nodesBefore;
	}

	private List<AbstractNode> getAllNodesAfter(final ArrayList<AbstractNode> abstractNodes,
												final AbstractNode lastExcludedNode)
	{
		final List<AbstractNode> nodesAfter = new ArrayList<>();
		boolean nodeFound = false;

		for (AbstractNode abstractNode : abstractNodes)
		{
			if (nodeFound)
			{
				nodesAfter.add(abstractNode);
			}

			if (abstractNode.id().equals(lastExcludedNode.id()))
			{
				nodeFound = true;
			}
		}

		return nodesAfter;
	}

	private void removeSuccessors(ArrayList<AbstractNode> abstractNodes)
	{
		for (AbstractNode abstractNode : abstractNodes)
		{
			abstractNode.setSuccessor(null);
		}
	}
}
