package refactoring.legacy.dependencies;

import bpmn.graph.Graph;
import bpmn.graph.GraphToList;
import bpmn.graph.Node;
import bpmn.types.process.Gateway;
import bpmn.types.process.Task;
import bpmn.writing.generation.GraphicalGenerationWriter;
import constants.PrintLevel;
import main.Main;
import other.Pair;
import other.Utils;
import refactoring.legacy.Cluster;
import refactoring.legacy.exceptions.BadDependencyException;
import refactoring.legacy.partial_order_to_bpmn.AbstractGraphsGenerator;
import refactoring.legacy.partial_order_to_bpmn.BPMNGenerator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

import static main.Main.PRINT_LEVEL;

public class DependenciesVerifier
{
	private DependenciesVerifier()
	{

	}

	/**
	 * This method checks whether the global dependencies stated by the user are respected in the final process.
	 * A dependency is considered as satisfied whenever there is no possibility to execute the dependent task (i.e.,
	 * the second one) before having executed the task on which it depends (i.e., the first one).
	 * This means that, for instance, the second task may never be executed, yet the dependency is valid.
	 * For instance, dependency (A, B) is satisfied if there is no path in which B occurs before the occurrence of A.
	 * This is done by verifying that any occurrence of B is preceded by a (mandatorily executed) occurrence of A.
	 * In other words, B must be preceded by instances of A that are either:
	 * - In every path of a choice
	 * - In the body of a loop
	 * - Outside a choice/loop
	 *
	 * @param globalDependencies the set of dependencies to verify
	 * @param cluster            the cluster on which the dependencies should be verified
	 * @return false if at least one of the dependencies is not satisfied, true otherwise
	 */
	public static HashSet<Dependency> computeUnsatisfiedDependencies(final HashSet<Dependency> globalDependencies,
																	 final Cluster cluster)
	{
		final HashSet<Dependency> unsatisfiedDependencies = new HashSet<>();

		for (Dependency dependency : globalDependencies)
		{
			final DependencyStatus status = DependenciesVerifier.verifyDependencyV2(dependency, cluster);

			if (status == DependencyStatus.ERROR)
			{
				unsatisfiedDependencies.add(dependency);

				if (PRINT_LEVEL >= PrintLevel.PRINT_SOME_LESS_IMPORTANT)
				{
					System.out.println("Dependency (" + dependency.firstNode().bpmnObject().id() + "," + dependency.secondNode().bpmnObject().id() + ") was not verified!");
					//System.out.println("Responsible cluster:\n\n" + cluster.stringify(0));
				}

				if (Main.DUMP_BAD_DEPENDENCY_PROCESSES)
				{
					try
					{
						DependenciesVerifier.dumpBad(cluster.copy());
					}
					catch (BadDependencyException | IOException e)
					{
						e.printStackTrace();
					}
				}

				return unsatisfiedDependencies;
			}

			if (PRINT_LEVEL >= PrintLevel.PRINT_SOME_LESS_IMPORTANT)
			{
				System.out.println("Dependency (" + dependency.firstNode().bpmnObject().id() + "," + dependency.secondNode().bpmnObject().id() + ") was verified.");
			}
		}

		return unsatisfiedDependencies;
	}

	//Private methods

	private static DependencyStatus verifyDependencyV1(final Dependency dependency,
													   final Cluster currentCluster,
													   final Node currentNode)
	{
		//System.out.println("Current status: " + status);
		//System.out.println("|" + currentNode.bpmnObject().rawName() + "|/|" + dependency.firstNode().bpmnObject().rawID() + "|/|" + dependency.secondNode().bpmnObject().rawID() + "|");
		//System.out.println("Object type: " + currentNode.bpmnObject().type());

		if (dependency.firstNode().bpmnObject().id().equals(currentNode.bpmnObject().name()))
		{
			//If we found the first task before the second one, the dependency is necessarily respected
			return DependencyStatus.FOUND;
		}
		else if (dependency.secondNode().bpmnObject().id().equals(currentNode.bpmnObject().name()))
		{
			//If we are here, it means that we found the second task without having found the first one => error
			return DependencyStatus.ERROR;
		}

		if (currentNode.bpmnObject() instanceof Gateway)
		{
			final EnhancedNode enhancedNode = currentCluster.findEnhancedNodeFrom(currentNode);

			if (enhancedNode.type() == EnhancedType.CHOICE)
			{
				for (Cluster subCluster : ((EnhancedChoice) enhancedNode).clusters())
				{
					for (DependencyGraph dependencyGraph : subCluster.dependencyGraphs())
					{
						for (Node initialNode : dependencyGraph.initialNodes())
						{
							final DependencyStatus currentStatus = DependenciesVerifier.verifyDependencyV1(dependency, subCluster, initialNode);

							if (currentStatus == DependencyStatus.ERROR) return DependencyStatus.ERROR;
						}
					}

					for (EnhancedNode node : subCluster.elements())
					{
						final Node copy = node.node().weakCopy();
						final DependencyStatus nonDependentNodeStatus = DependenciesVerifier.verifyDependencyV1(dependency, subCluster, copy);

						if (nonDependentNodeStatus == DependencyStatus.ERROR) return DependencyStatus.ERROR;
					}
				}
			}
			else
			{
				final EnhancedLoop enhancedLoop = (EnhancedLoop) currentCluster.findEnhancedNodeFrom(currentNode);

				for (DependencyGraph dependencyGraph : enhancedLoop.entryToExitCluster().dependencyGraphs())
				{
					for (Node initialNode : dependencyGraph.initialNodes())
					{
						final DependencyStatus currentStatus = DependenciesVerifier.verifyDependencyV1(dependency, enhancedLoop.entryToExitCluster(), initialNode);

						if (currentStatus == DependencyStatus.ERROR) return DependencyStatus.ERROR;
					}
				}

				for (EnhancedNode node : enhancedLoop.entryToExitCluster().elements())
				{
					final Node copy = node.node().weakCopy();
					final DependencyStatus nonDependentNodeStatus = DependenciesVerifier.verifyDependencyV1(dependency, enhancedLoop.entryToExitCluster(), copy);

					if (nonDependentNodeStatus == DependencyStatus.ERROR) return DependencyStatus.ERROR;
				}

				for (DependencyGraph dependencyGraph : enhancedLoop.exitToEntryCluster().dependencyGraphs())
				{
					for (Node initialNode : dependencyGraph.initialNodes())
					{
						final DependencyStatus currentStatus = DependenciesVerifier.verifyDependencyV1(dependency, enhancedLoop.exitToEntryCluster(), initialNode);

						if (currentStatus == DependencyStatus.ERROR) return DependencyStatus.ERROR;
					}
				}

				for (EnhancedNode node : enhancedLoop.exitToEntryCluster().elements())
				{
					final Node copy = node.node().weakCopy();
					final DependencyStatus nonDependentNodeStatus = DependenciesVerifier.verifyDependencyV1(dependency, enhancedLoop.exitToEntryCluster(), copy);

					if (nonDependentNodeStatus == DependencyStatus.ERROR) return DependencyStatus.ERROR;
				}
			}
		}

		for (Node child : currentNode.childNodes())
		{
			final DependencyStatus childStatus = DependenciesVerifier.verifyDependencyV1(dependency, currentCluster, child);

			if (childStatus == DependencyStatus.ERROR)
			{
				return DependencyStatus.ERROR;
			}
		}

		return DependencyStatus.FOUND;
	}

	private static DependencyStatus verifyDependencyV2(final Dependency dependency,
													   final Cluster mainCluster)
	{
		//System.out.println("Current status: " + status);
		//System.out.println("|" + currentNode.bpmnObject().rawName() + "|/|" + dependency.firstNode().bpmnObject().rawID() + "|/|" + dependency.secondNode().bpmnObject().rawID() + "|");
		//System.out.println("Object type: " + currentNode.bpmnObject().type());
		final String firstNodeName = dependency.firstNode().bpmnObject().name();
		final String secondNodeName = dependency.secondNode().bpmnObject().name();
		final ArrayList<Cluster> closestEnclosingClusters = new ArrayList<>();
		DependenciesVerifier.getClosestEnclosingClustersOf(mainCluster, dependency.secondNode(), closestEnclosingClusters);

		if (closestEnclosingClusters.isEmpty())
		{
			//The second node does not appear in the process, so the dependency is trivially valid
			return DependencyStatus.FOUND;
		}

		for (Cluster closestEnclosingCluster : closestEnclosingClusters)
		{
			//Check whether the task is preceded by the other task, or if the cluster is preceded by the other task
			for (DependencyGraph dependencyGraph : closestEnclosingCluster.dependencyGraphs())
			{
				final Node node = dependencyGraph.getNodeFromName(dependency.secondNode().bpmnObject().name());

				if (node != null)
				{
					final HashSet<Node> predecessors = node.predecessors();

					for (Node predecessor : predecessors)
					{
						if (predecessor.bpmnObject() instanceof Task)
						{
							if (predecessor.bpmnObject().name().equals(dependency.firstNode().bpmnObject().name()))
							{
								return DependencyStatus.FOUND;
							}
						}
						else
						{
							EnhancedNode enhancedNode;

							try
							{
								enhancedNode = mainCluster.findEnhancedNodeFrom(predecessor);
							}
							catch (Exception e)
							{
								enhancedNode = null;
							}

							if (enhancedNode == null) return DependencyStatus.ERROR;

							if (enhancedNode.type() == EnhancedType.LOOP)
							{
								final EnhancedLoop enhancedLoop = (EnhancedLoop) enhancedNode;

								if (enhancedLoop.entryToExitCluster().hasNodeNamed(dependency.firstNode().bpmnObject().name()))
								{
									if (DependenciesVerifier.nodeIsNecessarilyExecutedInCluster(enhancedLoop.entryToExitCluster(), dependency.firstNode()))
									{
										return DependencyStatus.FOUND;
									}
									else
									{
										return DependencyStatus.ERROR;
									}
								}
								else if (enhancedLoop.exitToEntryCluster().hasNodeNamed(dependency.firstNode().bpmnObject().name()))
								{
									//The dependency is invalid as the first node belongs to the optional part of a loop
									return DependencyStatus.ERROR;
								}
							}
							else if (enhancedNode.type() == EnhancedType.CHOICE)
							{
								final EnhancedChoice enhancedChoice = (EnhancedChoice) enhancedNode;
								boolean taskFound = false;
								int nbClustersWithTask = 0;

								for (Cluster cluster : enhancedChoice.clusters())
								{
									if (cluster.hasNodeNamed(firstNodeName))
									{
										taskFound = true;

										if (DependenciesVerifier.nodeIsNecessarilyExecutedInCluster(cluster, dependency.firstNode()))
										{
											nbClustersWithTask++;
										}
									}
								}

								if (taskFound)
								{
									if (nbClustersWithTask == enhancedChoice.clusters().size())
									{
										//Each choice cluster contains the task => the dependency is satisfied
										return DependencyStatus.FOUND;
									}
									else
									{
										//At least one choice cluster contains the task, but not all of them, or some
										// of them do not necessarily execute it => the dependency is unsatisfied
										return DependencyStatus.ERROR;
									}
								}
							}
						}
					}

					break;
				}
			}

			//The first node was not found in the second node's cluster, so we have to go to the upper layer
			final ArrayList<Pair<EnhancedNode, Cluster>> layers = new ArrayList<>();
			DependenciesVerifier.getLayersOf(mainCluster, closestEnclosingCluster, layers);

			if (layers.isEmpty())
			{
				//The current cluster is the main cluster => the dependency is not satisfied
				//System.out.println("No layers found");
				return DependencyStatus.ERROR;
			}

			//Reverse the layers to start from the closest cluster
			Collections.reverse(layers);

			for (Pair<EnhancedNode, Cluster> layer : layers)
			{
				if (layer.first().type() == EnhancedType.LOOP)
				{
					//System.out.println("Layer is a loop");
					final EnhancedLoop enhancedLoop = (EnhancedLoop) layer.first();

					if (enhancedLoop.exitToEntryCluster().hasNodeNamed(secondNodeName))
					{
						//System.out.println("Layer is the exit to entry cluster");
						//The cluster hosting the second dependency node is the optional part of a loop
						// => we need to check whether the first dependency node is necessarily executed in the body of that loop
						if (enhancedLoop.entryToExitCluster().hasNodeNamed(firstNodeName))
						{
							if (DependenciesVerifier.nodeIsNecessarilyExecutedInCluster(enhancedLoop.entryToExitCluster(), dependency.firstNode()))
							{
								return DependencyStatus.FOUND;
							}
							else
							{
								return DependencyStatus.ERROR;
							}
						}
					}
				}

				Node depGraphLayerNode = null;

				for (DependencyGraph dependencyGraph : layer.second().dependencyGraphs())
				{
					final Node depGraphNode = dependencyGraph.getNodeFromID(layer.first().node().bpmnObject().id());

					if (depGraphNode != null)
					{
						depGraphLayerNode = depGraphNode;
					}
				}

				if (depGraphLayerNode == null)
				{
					//Should throw an exception but consider the dependency unsatisfied for now
					return DependencyStatus.ERROR;
					//throw new RuntimeException();
				}

				final HashSet<Node> predecessors = depGraphLayerNode.predecessors();

				for (Node predecessor : predecessors)
				{
					if (predecessor.bpmnObject() instanceof Task)
					{
						if (predecessor.bpmnObject().name().equals(dependency.firstNode().bpmnObject().name()))
						{
							return DependencyStatus.FOUND;
						}
					}
					else
					{
						final EnhancedNode enhancedNode = layer.second().findEnhancedNodeFrom(predecessor);

						if (enhancedNode.type() == EnhancedType.LOOP)
						{
							final EnhancedLoop enhancedLoop = (EnhancedLoop) enhancedNode;

							if (enhancedLoop.entryToExitCluster().hasNodeNamed(dependency.firstNode().bpmnObject().name()))
							{
								if (DependenciesVerifier.nodeIsNecessarilyExecutedInCluster(enhancedLoop.entryToExitCluster(), dependency.firstNode()))
								{
									return DependencyStatus.FOUND;
								}
								else
								{
									return DependencyStatus.ERROR;
								}
							}
							else if (enhancedLoop.exitToEntryCluster().hasNodeNamed(dependency.firstNode().bpmnObject().name()))
							{
								//The dependency is invalid as the first node belongs to the optional part of a loop
								return DependencyStatus.ERROR;
							}
						}
						else if (enhancedNode.type() == EnhancedType.CHOICE)
						{
							final EnhancedChoice enhancedChoice = (EnhancedChoice) enhancedNode;
							boolean taskFound = false;
							int nbClustersWithTask = 0;

							for (Cluster cluster : enhancedChoice.clusters())
							{
								if (cluster.hasNodeNamed(firstNodeName))
								{
									taskFound = true;

									if (DependenciesVerifier.nodeIsNecessarilyExecutedInCluster(cluster, dependency.firstNode()))
									{
										nbClustersWithTask++;
									}
								}
							}

							if (taskFound)
							{
								if (nbClustersWithTask == enhancedChoice.clusters().size())
								{
									//Each choice cluster contains the task => the dependency is satisfied
									return DependencyStatus.FOUND;
								}
								else
								{
									//At least one choice cluster contains the task, but not all of them => the dependency is unsatisfied
									return DependencyStatus.ERROR;
								}
							}
						}
					}
				}
			}
		}

		return DependencyStatus.ERROR;
	}

	private static void getClosestEnclosingClustersOf(final Cluster currentCluster,
													  final Node node,
													  final ArrayList<Cluster> closestEnclosingClusters)
	{
		for (EnhancedNode enhancedNode : currentCluster.elements())
		{
			if (enhancedNode.type() == EnhancedType.TASK)
			{
				if (enhancedNode.node().bpmnObject().name().equals(node.bpmnObject().name()))
				{
					//This cluster is the closest cluster of the node
					closestEnclosingClusters.add(currentCluster);
					return;
				}
			}
		}

		for (EnhancedNode enhancedNode : currentCluster.elements())
		{
			if (enhancedNode.type() == EnhancedType.CHOICE)
			{
				final EnhancedChoice enhancedChoice = (EnhancedChoice) enhancedNode;

				for (Cluster cluster : enhancedChoice.clusters())
				{
					DependenciesVerifier.getClosestEnclosingClustersOf(cluster, node, closestEnclosingClusters);
				}
			}
			else if (enhancedNode.type() == EnhancedType.LOOP)
			{
				final EnhancedLoop enhancedLoop = (EnhancedLoop) enhancedNode;

				DependenciesVerifier.getClosestEnclosingClustersOf(enhancedLoop.entryToExitCluster(), node, closestEnclosingClusters);
				DependenciesVerifier.getClosestEnclosingClustersOf(enhancedLoop.exitToEntryCluster(), node, closestEnclosingClusters);
			}
		}
	}

	private static void getLayersOf(final Cluster currentCluster,
									final Cluster clusterToGetLayersOf,
									final ArrayList<Pair<EnhancedNode, Cluster>> layers)
	{
		//System.out.println("Current cluster id: " + currentCluster.id());
		//System.out.println("Current cluster to get layers of: " + clusterToGetLayersOf.id());

		if (currentCluster.equals(clusterToGetLayersOf))
		{
			return;
		}

		for (EnhancedNode enhancedNode : currentCluster.elements())
		{
			if (enhancedNode.type() == EnhancedType.CHOICE)
			{
				final EnhancedChoice enhancedChoice = (EnhancedChoice) enhancedNode;

				for (Cluster cluster : enhancedChoice.clusters())
				{
					if (cluster.equals(clusterToGetLayersOf))
					{
						layers.add(new Pair<>(enhancedChoice, currentCluster));
					}
					else if (cluster.isParentOfCluster(clusterToGetLayersOf))
					{
						layers.add(new Pair<>(enhancedChoice, currentCluster));
						DependenciesVerifier.getLayersOf(cluster, clusterToGetLayersOf, layers);
					}
				}
			}
			else if (enhancedNode.type() == EnhancedType.LOOP)
			{
				final EnhancedLoop enhancedLoop = (EnhancedLoop) enhancedNode;

				if (enhancedLoop.entryToExitCluster().equals(clusterToGetLayersOf)
						|| enhancedLoop.exitToEntryCluster().equals(clusterToGetLayersOf))
				{
					layers.add(new Pair<>(enhancedLoop, currentCluster));
				}
				else if (enhancedLoop.entryToExitCluster().isParentOfCluster(clusterToGetLayersOf))
				{
					layers.add(new Pair<>(enhancedLoop, currentCluster));
					DependenciesVerifier.getLayersOf(enhancedLoop.entryToExitCluster(), clusterToGetLayersOf, layers);
				}
				else if (enhancedLoop.exitToEntryCluster().isParentOfCluster(clusterToGetLayersOf))
				{
					layers.add(new Pair<>(enhancedLoop, currentCluster));
					DependenciesVerifier.getLayersOf(enhancedLoop.exitToEntryCluster(), clusterToGetLayersOf, layers);
				}
			}
		}
	}

	private static boolean nodeIsNecessarilyExecutedInCluster(final Cluster cluster,
															  final Node node)
	{
		//If the node is a task of the current cluster, it is necessarily executed
		for (EnhancedNode enhancedNode : cluster.elements())
		{
			if (enhancedNode.type() == EnhancedType.TASK)
			{
				if (enhancedNode.node().bpmnObject().name().equals(node.bpmnObject().name()))
				{
					return true;
				}
			}
		}

		for (EnhancedNode enhancedNode : cluster.elements())
		{
			/*
				If the node is in a choice of the current cluster, it has to be necessarily executed
				in each cluster of the choice.
			 */
			if (enhancedNode.type() == EnhancedType.CHOICE)
			{
				final EnhancedChoice enhancedChoice = (EnhancedChoice) enhancedNode;
				boolean choiceContainsNode = false;
				boolean atLeastOnePathDoesNotContainNode = false;

				for (Cluster choiceCluster : enhancedChoice.clusters())
				{
					if (choiceCluster.hasNodeNamed(node.bpmnObject().name()))
					{
						choiceContainsNode = true;

						if (!DependenciesVerifier.nodeIsNecessarilyExecutedInCluster(choiceCluster, node))
						{
							return false;
						}
					}
					else
					{
						atLeastOnePathDoesNotContainNode = true;
					}
				}

				if (choiceContainsNode)
				{
					return !atLeastOnePathDoesNotContainNode;
				}
			}
			else if (enhancedNode.type() == EnhancedType.LOOP)
			{
				final EnhancedLoop enhancedLoop = (EnhancedLoop) enhancedNode;

				//If the node is in a loop of the current cluster, it has to belong to the body of that loop
				if (enhancedLoop.entryToExitCluster().hasNodeNamed(node.bpmnObject().name()))
				{
					return DependenciesVerifier.nodeIsNecessarilyExecutedInCluster(enhancedLoop.entryToExitCluster(), node);
				}
				else if (enhancedLoop.exitToEntryCluster().hasNodeNamed(node.bpmnObject().name()))
				{
					return false;
				}
			}
		}

		throw new RuntimeException("Node \"" + node.bpmnObject().name() + "\" was not found in any enhanced node of cluster\n\n" + cluster.stringify(0));
	}

	private static void dumpBad(final Cluster cluster) throws BadDependencyException, IOException
	{
		if (cluster.abstractGraphs().isEmpty())
		{
			Utils.resetIndependentNodes(cluster);
			final AbstractGraphsGenerator abstractGraphsGenerator = new AbstractGraphsGenerator(cluster);
			abstractGraphsGenerator.generateAbstractGraphs();
		}

		BPMNGenerator generator = new BPMNGenerator(cluster);
		final Graph currentGraph = generator.generate();
		generator.unprocessClusters();

		final GraphToList graphToList = new GraphToList(currentGraph);
		graphToList.convert();

		GraphicalGenerationWriter writer = new GraphicalGenerationWriter(
				"/home/quentin/Documents/Step-by-Step_Refactoring/EXAMPLES/greedy_random/delivery_v3/bad_deps",
				graphToList.objectsList(),
				"bad_" + Utils.generateRandomIdentifier(15)
		);
		writer.write();
	}
}
