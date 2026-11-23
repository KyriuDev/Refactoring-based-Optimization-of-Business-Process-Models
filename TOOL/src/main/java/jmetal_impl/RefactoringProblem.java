package jmetal_impl;

import bpmn.BpmnParser;
import bpmn.graph.Graph;
import bpmn.graph.GraphToList;
import bpmn.graph.Node;
import bpmn.types.process.BpmnProcessObject;
import bpmn.types.process.Task;
import bpmn.writing.direct.DirectWriter;
import jmetal_impl.multi_objectives.MultiObjectivesRefactoringSolution;
import jmetal_impl.single_objective.SingleObjectiveRefactoringProblem;
import jmetal_impl.single_objective.SingleObjectiveRefactoringSolution;
import main.CommandLineParser;
import org.apache.commons.math3.distribution.AbstractRealDistribution;
import org.uma.jmetal.problem.Problem;
import other.Pair;
import other.Utils;
import refactoring.legacy.Cluster;
import refactoring.legacy.dependencies.Dependency;
import refactoring.legacy.exceptions.BadDependencyException;
import refactoring.legacy.partial_order_to_bpmn.AbstractGraphsGenerator;
import refactoring.legacy.partial_order_to_bpmn.BPMNGenerator;
import refactoring.step_by_step.*;
import resources.GlobalResourceSet;
import resources.ResourcePool;
import simulator.GlobalInfoEnum;
import simulator.SimulationResultsAggregator;
import simulator.Simulator;
import simulator.model.SimulationResult;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public abstract class RefactoringProblem implements Problem<RefactoringSolution>
{
	private final Random random;
	private final ArrayList<TreeNode> candidates;
	protected final TreeNode rootNode;
	private final Collection<Task> alreadyElectedTasks;
	private final BpmnParser parser;
	private final HashSet<TreeNode> currentLeafs;
	private final CommandLineParser commandLineParser;
	private final Cluster mainCluster;
	private final HashSet<Dependency> globalDependencies;
	private final File workingDirectory;
	private final AbstractRealDistribution iat;
	private final int nbInstances;
	private final ResourcePool availablePool;
	protected final OptimizationCriteria optimizationCriteria;
	private final GlobalResourceSet globalResourceSet;
	private final Map<GlobalInfoEnum, Object> globalInformation;
	private final Tree tree;
	private final ArrayList<String> timePerStep;
	protected final boolean considerWeights;

	public RefactoringProblem(final Collection<Task> alreadyElectedTasks,
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
							  final boolean considerWeights)
	{
		this.alreadyElectedTasks = alreadyElectedTasks;
		this.parser = parser;
		this.currentLeafs = currentLeafs;
		this.commandLineParser = commandLineParser;
		this.mainCluster = mainCluster;
		this.globalDependencies = globalDependencies;
		this.rootNode = initialNode;
		this.workingDirectory = workingDirectory;
		this.iat = iat;
		this.nbInstances = nbInstances;
		this.availablePool = availablePool;
		this.optimizationCriteria = optimizationCriteria;
		this.globalResourceSet = globalResourceSet;
		this.globalInformation = globalInformation;
		this.tree = tree;
		this.timePerStep = timePerStep;
		this.candidates = new ArrayList<>();
		this.candidates.add(this.rootNode);
		this.random = new Random();
		this.considerWeights = considerWeights;
	}

	@Override
	public final int numberOfVariables()
	{
		return 1;
	}

	@Override
	public int numberOfObjectives()
	{
		throw new RuntimeException("Method \"numberOfObjectives()\" must be overriden by the classes extending it!");
	}

	@Override
	public final int numberOfConstraints()
	{
		return 0;
	}

	@Override
	public String name()
	{
		return "RefactoringProblem";
	}

	@Override
	public RefactoringSolution evaluate(RefactoringSolution solution)
	{
		throw new RuntimeException("Method \"evaluate(RefactoringSolution solution)\" must be overriden by the classes extending it!");
	}

	@Override
	public final RefactoringSolution createSolution()
	{
		//System.out.println("Entering create solution");
		//Randomly pick a candidate for the solution
		final TreeNode candidate = this.candidates.get(random.nextInt(this.candidates.size()));
		final Cluster baseCluster = candidate.mainCluster().copy();

		//System.out.println("Base cluster for task election:\n\n" + baseCluster.stringify(0));

		//Randomly pick a task
		final ArrayList<TreeNode> mutations = new ArrayList<>();
		Pair<Node, Cluster> electedTask;

		do
		{
			final Cluster baseClusterCopy = baseCluster.copy();
			final TaskElector taskElector = new TaskElector(
				this.parser.bpmnProcess().objects(),
				new ArrayList<>(),
				this.availablePool,
				baseClusterCopy,
				TaskElectionHeuristic.RANDOM,
				this.optimizationCriteria
			);
			electedTask = taskElector.electTask();

			//System.out.println("Task elected: " + electedTask.first().bpmnObject().name());

			//Set cluster corresponding to current graph
			electedTask.setSecond(Utils.getTaskCluster(baseClusterCopy, electedTask.first()));

			//Correct dependencies
			final DependenciesCorrectorV2 dependenciesCorrectorV2 = new DependenciesCorrectorV2(baseClusterCopy, electedTask.first());
			dependenciesCorrectorV2.correctDependencies();

			//System.out.println("Dependencies corrected");

			final AbstractGraphsCombinerV3 combinerV3 = new AbstractGraphsCombinerV3(baseClusterCopy, electedTask, this.globalDependencies);
			final ArrayList<TreeNode> tempMutations;
			try
			{
				tempMutations = new ArrayList<>(combinerV3.generateMutations());
				mutations.addAll(tempMutations);
			}
			catch (BadDependencyException | IOException e)
			{
				throw new RuntimeException(e);
			}
		}
		while (mutations.isEmpty());

		//System.out.println("Mutations found");

		if (mutations.size() != 1) throw new IllegalStateException();

		this.candidates.add(new TreeNode(mutations.get(0).mainCluster().copy(), (Task) electedTask.first().bpmnObject()));
		//System.out.println("Solution created");

		return this.generateNewSolution(mutations.get(0), this.optimizationCriteria);
	}

	public abstract RefactoringSolution generateNewSolution(final TreeNode treeNode,
															final OptimizationCriteria optimizationCriteria);

	public final SimulationResult simulateNode(final TreeNode treeNode) throws BadDependencyException
	{
		final String fileName = "tmp_diagram.bpmn";
		final Cluster copy = treeNode.mainCluster().copy();

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

		final SimulationResult result = this.simulate(workingDirectory, availablePool, iat, nbInstances, fileName, globalSet, objects);
		treeNode.addSimulationResult(result);

		return result;
	}

	private SimulationResult simulate(final File workingDirectory,
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
}
