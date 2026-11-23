package main;

import bpmn.BpmnParser;
import bpmn.graph.Graph;
import bpmn.graph.GraphToList;
import bpmn.graph.ListToGraph;
import bpmn.types.process.BpmnProcessFactory;
import bpmn.types.process.BpmnProcessObject;
import bpmn.types.process.Task;
import bpmn.writing.generation.GraphicalGenerationWriter;
import constants.CommandLineOption;
import constants.PrintLevel;
import information.InfosManager;
import jmetal_impl.*;
import jmetal_impl.interruptibility.AlgorithmParameters;
import jmetal_impl.interruptibility.DiskSaver;
import jmetal_impl.interruptibility.JSONConstant;
import jmetal_impl.multi_objectives.MultiObjectivesRefactoringSolution;
import jmetal_impl.multi_objectives.characteristics.*;
import jmetal_impl.multi_objectives.MultiObjectivesRefactoringProblem;
import jmetal_impl.multi_objectives.my_algos.*;
import jmetal_impl.single_objective.SingleObjectiveRefactoringProblem;
import jmetal_impl.single_objective.SingleObjectiveRefactoringSolution;
import loops_management.LoopFinder;
import org.apache.commons.math3.distribution.AbstractRealDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.uma.jmetal.algorithm.multiobjective.agemoea.AGEMOEA;
import org.uma.jmetal.algorithm.multiobjective.agemoea.AGEMOEABuilder;
import org.uma.jmetal.algorithm.multiobjective.agemoeaii.AGEMOEAII;
import org.uma.jmetal.algorithm.multiobjective.agemoeaii.AGEMOEAIIBuilder;
import org.uma.jmetal.algorithm.multiobjective.espea.ESPEA;
import org.uma.jmetal.algorithm.multiobjective.espea.ESPEABuilder;
import org.uma.jmetal.algorithm.multiobjective.mosa.cooling.impl.Exponential;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAII;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAIIBuilder;
import org.uma.jmetal.algorithm.multiobjective.randomsearch.RandomSearch;
import org.uma.jmetal.algorithm.multiobjective.randomsearch.RandomSearchBuilder;
import org.uma.jmetal.algorithm.multiobjective.smsemoa.SMSEMOA;
import org.uma.jmetal.algorithm.multiobjective.smsemoa.SMSEMOABuilder;
import org.uma.jmetal.algorithm.multiobjective.spea2.SPEA2;
import org.uma.jmetal.algorithm.multiobjective.spea2.SPEA2Builder;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.crossover.impl.NullCrossover;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.util.archive.BoundedArchive;
import org.uma.jmetal.util.archive.impl.GenericBoundedArchive;
import org.uma.jmetal.util.densityestimator.impl.CrowdingDistanceDensityEstimator;
import other.Dumper;
import other.MathUtils;
import other.Pair;
import other.Utils;
import probabilities.ProbabilityCorrecter;
import refactoring.legacy.Cluster;
import refactoring.legacy.dependencies.DependenciesVerifier;
import refactoring.legacy.dependencies.Dependency;
import refactoring.legacy.exceptions.BadDependencyException;
import refactoring.legacy.partial_order_to_bpmn.AbstractGraphsGenerator;
import refactoring.legacy.partial_order_to_bpmn.BPMNGenerator;
import refactoring.step_by_step.*;
import resources.GlobalResourceSet;
import resources.Resource;
import resources.ResourcePool;
import simulator.GlobalInfoEnum;
import simulator.GlobalInfoGenerator;
import simulator.SimulationResultsAggregator;
import simulator.Simulator;
import simulator.bpmn_to_model_input.DistributionUtils;

import java.io.*;
import java.util.*;

public class Main
{
	public static final List<String> AVAILABLE_ALGORITHMS = Arrays.asList(
		//"age-moea",
		//"age-moeaii",
		//"espea",
		"localsearch",
		"mosa",
		"nsgaii",
		"randomsearch"
		//"smsemoa",
		//"spea2"
	);
	public static final long DUMP_FREQUENCY = 60L * 1000000000; //60s
	public static final long SOFT_MAX_DURATION_NANO = 6L * 60 * 1000000000; //6m
    public static final int NB_REPETITIONS = 20;
    private static final boolean CLEAR_DIRECTORIES = false;
    private static final boolean DUMP_SOLUTIONS = false;
	public static final boolean DUMP_BAD_DEPENDENCY_PROCESSES = false;
    public static final boolean WEIGHT_ELEMENTS = false;
    public static final int PRINT_LEVEL = PrintLevel.PRINT_SOME_IMPORTANT;
    public static final Algorithm JMETAL_ALGORITHM = Algorithm.SPEA2;
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
	private static boolean indeedThrow = false;

    public static void main(String[] args) throws InterruptedException, IOException, BadDependencyException
    {
        if (args.length == 0) throw new IllegalStateException("JAR should be called with the topmost working directory as parameter.");

        final File baseWorkingDir = new File(args[0]);

        if (!baseWorkingDir.exists()
            || !baseWorkingDir.isDirectory())
        {
            throw new IllegalStateException("Path \"" + baseWorkingDir.getAbsolutePath() + "\" does not point to a valid directory.");
        }

		new File(baseWorkingDir.getAbsolutePath() + File.separator + "final_node_metrics.txt").delete();
        final ArrayList<String> algorithmsToRun = new ArrayList<>(AVAILABLE_ALGORITHMS);
		final Random random = new Random();

		while (!algorithmsToRun.isEmpty())
		{
			final String algorithmName = algorithmsToRun.get(random.nextInt(algorithmsToRun.size()));
			final File algorithmDir = new File(baseWorkingDir.getAbsolutePath() + File.separator + algorithmName);

			if (!algorithmDir.isDirectory())
			{
				System.out.println("Error: \"" + algorithmDir.getAbsolutePath() + "\" is not a valid directory.");
				algorithmsToRun.remove(algorithmName);
				continue;
			}

			final ArrayList<Double> scores = new ArrayList<>();

			System.out.println("Current subdir: " + algorithmDir.getAbsolutePath());
			CommandLineParser commandLineParser;

			if (Main.clearDir(algorithmDir))
			{
				continue;
			}

			try
			{
				commandLineParser = new CommandLineParser(algorithmDir.getAbsolutePath(), "-algorithm=" + algorithmName);
			}
			catch (FileNotFoundException e)
			{
				throw new IllegalStateException("Some necessary files have not be found or are not valid.");
			}

			final AlgorithmParameters algorithmParameters = (AlgorithmParameters) commandLineParser.get(CommandLineOption.ALGORITHM_PARAMETERS);
			final Algorithm algorithm = (Algorithm) commandLineParser.get(CommandLineOption.ALGORITHM);
			int nbProcessManaged = algorithmParameters.getNbProcessesAlreadyRefactored();

			System.out.println("Progression: " + nbProcessManaged + "/" + algorithmParameters.getNbProcessesToRefactor());

			if (nbProcessManaged >= algorithmParameters.getNbProcessesToRefactor())
			{
				algorithmsToRun.remove(algorithmName);
				continue;
			}

			try
			{
				//Parse the BPMN file
				final BpmnParser parser;

				try
				{
					parser = new BpmnParser((File) commandLineParser.get(CommandLineOption.BPMN_PROCESS));
					parser.parse();
				}
				catch (Exception e)
				{
					e.printStackTrace();
					logger.error("BPMN process \"{}\" could not be parsed properly. Exiting.", algorithmDir.getPath());
					return;
				}

				//Retrieve process objects, setup process factory IDs and initialise dumper
				final ArrayList<BpmnProcessObject> objects = parser.bpmnProcess().objects();
				BpmnProcessFactory.setObjectIDs(objects);
				Dumper.getInstance().initializeDumper(commandLineParser, parser);

				//Load information
				final InfosManager infosManager = new InfosManager(algorithmDir);
				infosManager.loadData();

				//Get global dependencies
				final HashSet<Dependency> globalDependencies = Main.getGlobalDependencies(commandLineParser, objects);

				//Get optimisation criteria
				final OptimizationCriteria optimizationCriteria = ((OptimizationCriteria) commandLineParser.get(CommandLineOption.OPTIMIZATION_CRITERIA));

				//BPMN objects -> BPMN Graph
				ListToGraph listToGraph = new ListToGraph(objects);
				Graph graph = listToGraph.convert();

				//Compute all loops in the BPMN process
				LoopFinder loopFinder = new LoopFinder(graph);
				loopFinder.findLoops();

				//Correct probabilities
				ProbabilityCorrecter probabilityCorrecter = new ProbabilityCorrecter(graph, loopFinder);
				probabilityCorrecter.correctProbabilities();

				//Extract dependencies from process and keep a copy of the original main cluster
				DependenciesExtractorV2 initialExtractor = new DependenciesExtractorV2(graph, loopFinder);
				final Cluster mainCluster = initialExtractor.extractDependencies();
				final int initialNbTasks = mainCluster.nbTasks();

				//Verify the dependencies
				final HashSet<Dependency> unsatisfiedDependency = DependenciesVerifier.computeUnsatisfiedDependencies(globalDependencies, mainCluster);

				if (!unsatisfiedDependency.isEmpty())
				{
					final Dependency dependency = unsatisfiedDependency.iterator().next();
					throw new IllegalStateException(
						"Dependency (" + dependency.firstNode().bpmnObject().name() + ", " + dependency.secondNode().bpmnObject().name()
						+ ") is already unsatisfied in the original process!!!"
					);
				}

				//Create the tree of possibilities
				final TreeNode initialNode = new TreeNode(mainCluster.copy(), null);
				final Tree tree = new Tree(initialNode);

				//Store already elected tasks
				final HashSet<Task> alreadyElectedTasks = new HashSet<>();
				final HashSet<TreeNode> currentLeafs = new HashSet<>();
				currentLeafs.add(initialNode);

				//Get the global information
				final Map<GlobalInfoEnum, Object> globalInformation = Main.getGlobalInformation(commandLineParser, parser, algorithmDir);

				//Get the available pool of resources
				final ResourcePool availablePool = (ResourcePool) globalInformation.get(GlobalInfoEnum.GLOBAL_RESOURCE_POOL);

				if (availablePool.createsDeadlocks(parser.bpmnProcess().objects()))
				{
					indeedThrow = true;
					throw new RuntimeException("A task can not be executed.");
				}

				Main.setResourceCost(parser.bpmnProcess().objects(), availablePool);
				Main.setTaskImpact(parser.bpmnProcess().objects(), optimizationCriteria);

				if (PRINT_LEVEL >= PrintLevel.PRINT_SOME_LESS_IMPORTANT)
				{
					System.out.println("Available pool: " + availablePool.toString());
				}

				//Compute the set of all resources used
				final GlobalResourceSet globalResourceSet = new GlobalResourceSet(availablePool);
				globalResourceSet.computeGlobalResources();

				//Retrieve the IAT and the number of instances of the process
				final AbstractRealDistribution iat = (AbstractRealDistribution) globalInformation.get(GlobalInfoEnum.IAT);
				final int nbInstances = (Integer) globalInformation.get(GlobalInfoEnum.NB_INSTANCES);

				//Simulate initial node
				final HashMap<String, Double> initialNodeMetrics = Main.simulateInitialNode(commandLineParser, optimizationCriteria, objects, globalResourceSet, availablePool, iat, nbInstances, initialNode);
				Main.dumpNodeInformation(initialNode, "initial", algorithmDir, optimizationCriteria);

				//Print original optimisation criteria values
				if (PRINT_LEVEL >= PrintLevel.PRINT_SOME_IMPORTANT)
				{
					Main.printOptimisationCriteriaValues(initialNode, optimizationCriteria, "INITIAL");
				}

				//Perform refactoring
				final ArrayList<String> timePerStep = new ArrayList<>();
				final MultiObjectivesRefactoringProblem multiObjectivesRefactoringProblem = new MultiObjectivesRefactoringProblem(
					alreadyElectedTasks, parser, currentLeafs, commandLineParser, mainCluster, globalDependencies,
					initialNode, algorithmDir, iat, nbInstances, availablePool, optimizationCriteria, globalResourceSet,
					globalInformation, tree, timePerStep, WEIGHT_ELEMENTS
				);
				final SingleObjectiveRefactoringProblem singleObjectiveRefactoringProblem = new SingleObjectiveRefactoringProblem(
					alreadyElectedTasks, parser, currentLeafs, commandLineParser, mainCluster, globalDependencies,
					initialNode, algorithmDir, iat, nbInstances, availablePool, optimizationCriteria, globalResourceSet,
					globalInformation, tree, timePerStep, WEIGHT_ELEMENTS
				);
				final MutationOperator<RefactoringSolution> mutationOperator = new RefactoringMutation(
					alreadyElectedTasks, parser, currentLeafs, commandLineParser, mainCluster, globalDependencies,
					initialNode, algorithmDir, iat, nbInstances, availablePool, optimizationCriteria, globalResourceSet,
					globalInformation, tree, timePerStep, multiObjectivesRefactoringProblem
				);
				final NullCrossover<RefactoringSolution> crossoverOperator = new NullCrossover<>();
				final RefactoringDominanceComparator dominanceComparator = new RefactoringDominanceComparator(
					initialNode,
					optimizationCriteria
				);
				final List<RefactoringSolution> solutions = new ArrayList<>();
				final long startTime = System.nanoTime();
				final HashMap<Long, Double> dumpedInformation = Main.jmetalizeV2(
					algorithm,
					multiObjectivesRefactoringProblem,
					singleObjectiveRefactoringProblem,
					crossoverOperator,
					mutationOperator,
					dominanceComparator,
					solutions,
					algorithmParameters.getMaxEvaluations(),
					algorithmParameters.getMaxIterations(),
					algorithmParameters.getPopulationSize(),
					initialNode,
					optimizationCriteria
				);
				final long endTime = System.nanoTime();
				final long totalTime = endTime - startTime;
				System.out.println("Run finished. Found " + solutions.size() + " solutions.");

				Main.printOptimisationCriteriaValues(initialNode, optimizationCriteria, "INITIAL");
				int i = 1;

				//Sort the solutions by ascending score
				solutions.sort(Main.refactoringSolutionComparator());

				final RefactoringSolution bestSolution = solutions.get(solutions.size() - 1);
				Main.verifySolution(bestSolution, parser.bpmnProcess().objects(), commandLineParser);
				final double score = bestSolution.variables().get(0).score();
				scores.add(score);

				Main.printOptimisationCriteriaValues(bestSolution.treeNode(), optimizationCriteria, "FINAL");
				System.out.println("Best solution score: " + score);

				System.out.println("The computation took " + Utils.nanoSecToReadable(totalTime));

				final boolean dataSaved = Main.saveData(
					commandLineParser,
					optimizationCriteria,
					solutions,
					initialNodeMetrics,
					totalTime,
					algorithm,
					algorithmParameters,
					dumpedInformation
				);

				if (dataSaved)
				{
					nbProcessManaged++;
				}
				else
				{
					throw new RuntimeException();
				}

			}
			catch (Exception e)
			{
				if (indeedThrow)
				{
					throw e;
				}

				System.out.println(e);
				e.printStackTrace();
				continue;
			}

			//algorithmsToRun.remove(algorithmName);
			System.out.println("Computation performed on " + nbProcessManaged + " processes.");
			System.out.println("Min score: " + MathUtils.getMinDouble(scores));
			System.out.println("Max score: " + MathUtils.getMaxDouble(scores));
			System.out.println("Average score: " + MathUtils.getDoubleAverage(scores));
			System.out.println("Algorithm: " + algorithmName);
		}
    }

    private static void jmetalize(final RefactoringProblem multiObjectivesRefactoringProblem,
                                  final RefactoringProblem singleObjectiveRefactoringProblem,
                                  final CrossoverOperator<RefactoringSolution> crossoverOperator,
                                  final MutationOperator<RefactoringSolution> mutationOperator,
                                  final RefactoringDominanceComparator dominanceComparator,
                                  final List<RefactoringSolution> solutions)
    {
        if (JMETAL_ALGORITHM == Algorithm.AGEMOEA)
        {
            final AGEMOEABuilder<RefactoringSolution> builder = new AGEMOEABuilder<>(
                    multiObjectivesRefactoringProblem
            );
            builder.setCrossoverOperator(crossoverOperator);
            builder.setMutationOperator(mutationOperator);
            builder.setMaxIterations(AGEMOEACharacteristic.MAX_ITERATIONS);
            builder.setPopulationSize(AGEMOEACharacteristic.POPULATION_SIZE);
            final AGEMOEA<RefactoringSolution> agemoea = builder.build();
            agemoea.run();
            solutions.addAll(agemoea.result());
        }
        else if (JMETAL_ALGORITHM == Algorithm.AGEMOEAII)
        {
            final AGEMOEAIIBuilder<RefactoringSolution> builder = new AGEMOEAIIBuilder<>(
                    multiObjectivesRefactoringProblem
            );
            builder.setCrossoverOperator(crossoverOperator);
            builder.setMutationOperator(mutationOperator);
            builder.setMaxIterations(AGEMOEAIICharacteristic.MAX_ITERATIONS);
            builder.setPopulationSize(AGEMOEAIICharacteristic.POPULATION_SIZE);
            final AGEMOEAII<RefactoringSolution> agemoeaii = builder.build();
            agemoeaii.run();
            solutions.addAll(agemoeaii.result());
        }
        else if (JMETAL_ALGORITHM == Algorithm.ESPEA)
        {
            final ESPEABuilder<RefactoringSolution> builder = new ESPEABuilder<>(
                    multiObjectivesRefactoringProblem,
                    crossoverOperator,
                    mutationOperator
            );
            builder.setPopulationSize(ESPEACharacteristic.POPULATION_SIZE);
            builder.setMaxEvaluations(ESPEACharacteristic.MAX_EVALUATIONS);
            final ESPEA<RefactoringSolution> espea = builder.build();
            espea.run();
            solutions.addAll(espea.result());
        }
        else if (JMETAL_ALGORITHM == Algorithm.NSGAII)
        {
            final NSGAIIBuilder<RefactoringSolution> nsgaiiBuilder = new NSGAIIBuilder<>(
                    multiObjectivesRefactoringProblem,
                    crossoverOperator,
                    mutationOperator,
                    NSGAIICharacteristic.POPULATION_SIZE
            );
            nsgaiiBuilder.setDominanceComparator(dominanceComparator);
            nsgaiiBuilder.setMaxEvaluations(NSGAIICharacteristic.MAX_EVALUATIONS);
            final NSGAII<RefactoringSolution> nsgaii = nsgaiiBuilder.build();
            nsgaii.run();
            solutions.addAll(nsgaii.result());
        }
        else if (JMETAL_ALGORITHM == Algorithm.RandomSearch)
        {
            final RandomSearchBuilder<RefactoringSolution> randomSearchBuilder = new RandomSearchBuilder<>(
                    multiObjectivesRefactoringProblem
            );
            randomSearchBuilder.setMaxEvaluations(RandomSearchCharacteristic.MAX_EVALUATIONS);
            final RandomSearch<RefactoringSolution> randomSearch = randomSearchBuilder.build();
            randomSearch.run();
            solutions.addAll(randomSearch.result());
        }
        else if (JMETAL_ALGORITHM == Algorithm.SMSEMOA)
        {
            final SMSEMOABuilder<RefactoringSolution> smsemoaBuilder = new SMSEMOABuilder<>(
                    multiObjectivesRefactoringProblem,
                    crossoverOperator,
                    mutationOperator
            );
            smsemoaBuilder.setPopulationSize(SMSEMOACharacteristic.POPULATION_SIZE);
            smsemoaBuilder.setMaxEvaluations(SMSEMOACharacteristic.MAX_EVALUATIONS);
            final SMSEMOA<RefactoringSolution> smsemoa = smsemoaBuilder.build();
            smsemoa.run();
            solutions.addAll(smsemoa.result());
        }
        else if (JMETAL_ALGORITHM == Algorithm.SPEA2)
        {
            final SPEA2Builder<RefactoringSolution> spea2Builder = new SPEA2Builder<>(
                    multiObjectivesRefactoringProblem,
                    crossoverOperator,
                    mutationOperator
            );
            spea2Builder.setMaxIterations(SPEA2Characteristic.MAX_ITERATIONS);
            spea2Builder.setPopulationSize(SPEA2Characteristic.POPULATION_SIZE);
            final SPEA2<RefactoringSolution> spea2 = spea2Builder.build();
            spea2.run();
            solutions.addAll(spea2.result());
        }
    }

    private static boolean saveData(final CommandLineParser commandLineParser,
                                    final OptimizationCriteria optimizationCriteria,
                                    final List<RefactoringSolution> solutions,
                                    final HashMap<String, Double> originalProcessMetrics,
                                    final long executionTime,
                                    final Algorithm algorithm,
                                    final AlgorithmParameters algorithmParameters,
									final HashMap<Long, Double> dumpedInformation)
    {
        final ArrayList<Pair<RefactoringSolution, HashMap<String, Double>>> bestSolutions = new ArrayList<>();
        Collections.reverse(solutions);

        for (RefactoringSolution refactoringSolution : solutions)
        {
            if (bestSolutions.isEmpty())
            {
                final HashMap<String, Double> solutionMetrics = new HashMap<>();

                if (optimizationCriteria.aetMustBeOptimized())
                {
                    solutionMetrics.put(JSONConstant.AET, refactoringSolution.treeNode().lastResult().getAvgExecTime());
                }

                if (optimizationCriteria.costMustBeOptimized())
                {
                    solutionMetrics.put(JSONConstant.COST, refactoringSolution.treeNode().lastResult().getTotalCost());
                }

                for (String resourceName : optimizationCriteria.getResourcesToOptimizeAndTargetUsageAndWeight().keySet())
                {
                    solutionMetrics.put(resourceName, refactoringSolution.treeNode().lastResult().getUsagePercentage().get(resourceName));
                }

                bestSolutions.add(new Pair<>(refactoringSolution, solutionMetrics));
            }
            else
            {
                if (bestSolutions.get(0).first().treeNode().score() == refactoringSolution.treeNode().score())
                {
                    final HashMap<String, Double> solutionMetrics = new HashMap<>();

                    if (optimizationCriteria.aetMustBeOptimized())
                    {
                        solutionMetrics.put(JSONConstant.AET, refactoringSolution.treeNode().lastResult().getAvgExecTime());
                    }

                    if (optimizationCriteria.costMustBeOptimized())
                    {
                        solutionMetrics.put(JSONConstant.COST, refactoringSolution.treeNode().lastResult().getTotalCost());
                    }

                    for (String resourceName : optimizationCriteria.getResourcesToOptimizeAndTargetUsageAndWeight().keySet())
                    {
                        solutionMetrics.put(resourceName, refactoringSolution.treeNode().lastResult().getUsagePercentage().get(resourceName));
                    }

                    bestSolutions.add(new Pair<>(refactoringSolution, solutionMetrics));
                }
                else
                {
                    //Whenever a worse solution is found, we can break as they are sorted in descending order of score
                    break;
                }
            }
        }

        return DiskSaver.saveToDisk(
            commandLineParser,
            (File) commandLineParser.get(CommandLineOption.BPMN_PROCESS),
            bestSolutions,
            originalProcessMetrics,
            executionTime,
            algorithm,
            algorithmParameters,
            NB_REPETITIONS,
			dumpedInformation
        );
    }

    private static void printOptimisationCriteriaValues(final TreeNode node,
                                                        final OptimizationCriteria optimizationCriteria,
                                                        final String nodeQualification)
    {
        final String message = "-------------------- " + nodeQualification + " OPTIMISATION CRITERIA VALUES --------------------";
        System.out.println(message);

        if (optimizationCriteria.costOptimizationWeight() != 0d)
        {
            System.out.println("- Current cost: " + node.lastResult().getTotalCost());
        }

        if (optimizationCriteria.timeOptimizationWeight() != 0d)
        {
            final double currentAET = node.lastResult().getAvgExecTime();
            System.out.println("- Current AET: " +
                Utils.round(currentAET, currentAET < 10d ?
                    3 :
                    (currentAET < 100d ? 2 : 1)
                ));
        }

        for (String resource : optimizationCriteria.getResourcesToOptimizeAndTargetUsageAndWeight().keySet())
        {
            final double baseResourceUsage = node.lastResult().getUsagePercentage().get(resource);
            final double resourceUsage = Utils.round(baseResourceUsage, 2);
            System.out.println("- Resource \"" + resource + "\" used at " + resourceUsage + "% (target: "
                    + optimizationCriteria.getResourcesToOptimizeAndTargetUsageAndWeight().get(resource).first()
                    + "%)"
            );
        }

        System.out.println("------------------------------------------------------------------------------");
    }

    private static HashSet<Dependency> getGlobalDependencies(final CommandLineParser commandLineParser,
                                                             final Collection<BpmnProcessObject> objects)
    {
        final HashSet<Dependency> globalDependencies = (HashSet<Dependency>) commandLineParser.get(CommandLineOption.GLOBAL_DEPENDENCIES);

        for (Dependency dependency : globalDependencies)
        {
            boolean found = false;

            for (BpmnProcessObject object : objects)
            {
                if (object.name() != null
                    && object.name().equals(dependency.firstNode().bpmnObject().id()))
                {
                    found = true;
                    break;
                }
            }

            if (!found)
                throw new IllegalStateException("ID |" + dependency.firstNode().bpmnObject().id() + "| does not correspond to any existing object.");
            found = false;

            for (BpmnProcessObject object : objects)
            {
                if (object.name() != null
                    && object.name().equals(dependency.secondNode().bpmnObject().id()))
                {
                    found = true;
                    break;
                }
            }

            if (!found)
                throw new IllegalStateException("ID |" + dependency.secondNode().bpmnObject().id() + "| does not correspond to any existing object.");
        }

        return globalDependencies;
    }

    private static Map<GlobalInfoEnum, Object> getGlobalInformation(final CommandLineParser commandLineParser,
                                                                    final BpmnParser parser,
                                                                    final File workingDirectory) throws IOException
	{
        final Map<GlobalInfoEnum, Object> globalInformation;

        if (commandLineParser.containsKey(CommandLineOption.GLOBAL_INFORMATION))
        {
            globalInformation = (Map<GlobalInfoEnum, Object>) commandLineParser.get(CommandLineOption.GLOBAL_INFORMATION);
        }
        else
        {
            globalInformation = GlobalInfoGenerator.generateGlobalInformation(parser.bpmnProcess().objects());
            GlobalInfoGenerator.writeGlobalInfo(globalInformation, workingDirectory, "current");
        }

        return globalInformation;
    }

    private static HashMap<String, Double> simulateInitialNode(final CommandLineParser commandLineParser,
                                                               final OptimizationCriteria optimizationCriteria,
                                                               final ArrayList<BpmnProcessObject> objects,
                                                               final GlobalResourceSet globalResourceSet,
                                                               final ResourcePool availablePool,
                                                               final AbstractRealDistribution iat,
                                                               final int nbInstances,
                                                               final TreeNode initialNode)
    {
        final Simulator initialSimulator = new Simulator(
            (File) commandLineParser.get(CommandLineOption.BPMN_PROCESS),
            objects,
            globalResourceSet,
            availablePool,
            iat,
            nbInstances
        );

        initialNode.addSimulationResult(SimulationResultsAggregator.aggregate(initialSimulator.simulateMultipleInstances()));

        final HashMap<String, Double> initialNodeMetrics = new HashMap<>();

        if (optimizationCriteria.aetMustBeOptimized())
        {
            initialNodeMetrics.put(JSONConstant.AET, initialNode.lastResult().getAvgExecTime());
        }

        if (optimizationCriteria.costMustBeOptimized())
        {
            initialNodeMetrics.put(JSONConstant.COST, initialNode.lastResult().getTotalCost());
        }

        for (String resourceName : optimizationCriteria.getResourcesToOptimizeAndTargetUsageAndWeight().keySet())
        {
            initialNodeMetrics.put(resourceName, initialNode.lastResult().getUsagePercentage().get(resourceName));
        }

        return initialNodeMetrics;
    }

    private static void dumpNodeInformation(final TreeNode treeNode,
                                            final String nodeName,
                                            final File workingDirectory,
                                            final OptimizationCriteria optimizationCriteria) throws IOException
	{
        final String fileName = nodeName + "_node_metrics.txt";
        final File nodeFile = new File(workingDirectory.getAbsolutePath() + File.separator + fileName);
        final FileOutputStream outputStream = new FileOutputStream(nodeFile);
        final PrintWriter printWriter = new PrintWriter(outputStream);

        if (optimizationCriteria.timeOptimizationWeight() != 0d)
        {
            printWriter.print("- AET: ");
            printWriter.println(Utils.round(treeNode.lastResult().getAvgExecTime(), 2));
        }

        if (optimizationCriteria.costOptimizationWeight() != 0d)
        {
            printWriter.print("- Cost: ");
            printWriter.println(Utils.round(treeNode.lastResult().getTotalCost(), 2));
        }

        for (String resource : optimizationCriteria.getResourcesToOptimizeAndTargetUsageAndWeight().keySet())
        {
            printWriter.print("- ");
            printWriter.print(resource);
            printWriter.print(": ");
            printWriter.print(Utils.round(treeNode.lastResult().getUsagePercentage().get(resource), 2));
            printWriter.println("%");
        }

        printWriter.flush();
        printWriter.close();
        outputStream.flush();
        outputStream.close();
    }

    public static boolean clearDir(final File subDir)
    {
        final HashSet<String> filesToDelete = new HashSet<>();

        for (File file : Objects.requireNonNull(subDir.listFiles()))
        {
            if (CLEAR_DIRECTORIES)
            {
                if (!file.getName().equals("global_infos_current.inf")
                    && !file.getName().equals("original_process.bpmn")
                    && !file.getName().equals("dependencies.dep")
                    && !file.getName().equals("criteria.opt"))
                {
                    filesToDelete.add(file.getAbsolutePath());
                }
            }
            else
            {
                if (file.getName().endsWith(".bpmn")
                    && !file.getName().equals("original_process.bpmn"))
                {
                    filesToDelete.add(file.getAbsolutePath());
                }
                else if (file.getName().endsWith(".pif"))
                {
                    filesToDelete.add(file.getAbsolutePath());
                }
                else if (file.getName().equals("final_node_metrics.txt"))
                {
                    return true;
                }
            }
        }

        for (String fileNameOfFileToDelete : filesToDelete)
        {
            System.out.println("Deleting file \"" + fileNameOfFileToDelete + "\".");
            new File(fileNameOfFileToDelete).delete();
        }

		return CLEAR_DIRECTORIES;
	}

    private static void setResourceCost(final Collection<BpmnProcessObject> objects,
                                        final ResourcePool resourcePool)
    {
        for (BpmnProcessObject object : objects)
        {
            if (object instanceof Task)
            {
                final Task task = (Task) object;

                for (Resource resource : task.resourceUsage().resources())
                {
                   for (Resource otherResource : resourcePool.resources())
                   {
                       if (resource.equals(otherResource))
                       {
                           resource.setCost(otherResource.cost());
                       }
                   }
                }
            }
        }
    }

    private static void setTaskImpact(final Collection<BpmnProcessObject> objects,
                                      final OptimizationCriteria optimizationCriteria)
    {
        final Integer[] costMinMax = {Integer.MAX_VALUE, -1};
        final Integer[] durationMinMax = {Integer.MAX_VALUE, -1};
        final HashMap<String, Integer[]> resourceUsageMinMax = new HashMap<>();

        //Setup min/max values for normalisation
        for (BpmnProcessObject object : objects)
        {
            if (!(object instanceof Task)) continue;

            final Task task = (Task) object;

            if (optimizationCriteria.costOptimizationWeight() != 0d)
            {
                //We should consider the cost
                final int taskCost = Main.computeTaskCost(task);

                if (taskCost < costMinMax[0])
                {
                    costMinMax[0] = taskCost;
                }

                if (taskCost > costMinMax[1])
                {
                    costMinMax[1] = taskCost;
                }
            }

            if (optimizationCriteria.timeOptimizationWeight() != 0d)
            {
                //We should consider duration
                final int taskDuration = DistributionUtils.averageIntegerValue(task.duration());

                if (taskDuration < durationMinMax[0])
                {
                    durationMinMax[0] = taskDuration;
                }

                if (taskDuration > durationMinMax[1])
                {
                    durationMinMax[1] = taskDuration;
                }
            }

            for (Resource resource : task.resourceUsage().resources())
            {
                if (optimizationCriteria.getResourcesToOptimizeAndTargetUsageAndWeight().containsKey(resource.name()))
                {
                    final Integer[] resourceMinMax;

                    if (resourceUsageMinMax.containsKey(resource.name()))
                    {
                        resourceMinMax = resourceUsageMinMax.get(resource.name());
                    }
                    else
                    {
                        resourceMinMax = new Integer[]{Integer.MAX_VALUE, -1};
                        resourceUsageMinMax.put(resource.name(), resourceMinMax);
                    }

                    if (task.resourceUsage().getUsageOf(resource) < resourceMinMax[0])
                    {
                        resourceMinMax[0] = task.resourceUsage().getUsageOf(resource);
                    }

                    if (task.resourceUsage().getUsageOf(resource) > resourceMinMax[1])
                    {
                        resourceMinMax[1] = task.resourceUsage().getUsageOf(resource);
                    }
                }
            }
        }

        //Compute the normalised impact of the task to move
        for (BpmnProcessObject object : objects)
        {
            if (!(object instanceof Task)) continue;

            final Task task = (Task) object;
            double currentImpact = 0d;
            final int duration = DistributionUtils.averageIntegerValue(task.duration());
            final int cost = Main.computeTaskCost(task);

            if (costMinMax[1] != -1)
            {
                //Consider cost
                final int costRange = costMinMax[1] - costMinMax[0];
                final double normalisedCost =
                    costRange == 0 ?
                    1d :
                    (double) (cost - costMinMax[0]) / (double) costRange
                ;
                currentImpact += normalisedCost * optimizationCriteria.costOptimizationWeight();
                //System.out.println("Normalised cost: " + normalisedCost);
            }

            if (durationMinMax[1] != -1)
            {
                //Consider duration
                final int durationRange = durationMinMax[1] - durationMinMax[0];
                final double normalisedDuration =
                    durationRange == 0 ?
                    1d :
                    (double) (duration - durationMinMax[0]) / (double) durationRange
                ;
                currentImpact += normalisedDuration * optimizationCriteria.timeOptimizationWeight();
                //System.out.println("Normalised duration: " + normalisedDuration);
            }

            for (Resource resource : task.resourceUsage().resources())
            {
                if (optimizationCriteria.getResourcesToOptimizeAndTargetUsageAndWeight().containsKey(resource.name()))
                {
                    final Pair<Integer, Double> targetUsageAndWeight = optimizationCriteria.getResourcesToOptimizeAndTargetUsageAndWeight().get(resource.name());
                    final int taskResourceUsage = task.resourceUsage().getUsageOf(resource);
                    final int maxResourceUsage = resourceUsageMinMax.get(resource.name())[1];
                    final int minResourceUsage = resourceUsageMinMax.get(resource.name())[0];
                    final int rangeResourceUsage = maxResourceUsage - minResourceUsage;
                    //System.out.println("Task resource usage: " + taskResourceUsage);
                    //System.out.println("Max resource usage: " + maxResourceUsage);
                    //System.out.println("Min resource usage: " + minResourceUsage);
                    final double normalisedUsage =
                        rangeResourceUsage == 0 ?
                        1d :
                        (double) (taskResourceUsage - minResourceUsage) / (double) rangeResourceUsage
                    ;
                   // System.out.println("Normalised " + resource.name() + " usage: " + normalisedUsage);
                    final double weightedNormalisedUsage = normalisedUsage * targetUsageAndWeight.second();

                    currentImpact += weightedNormalisedUsage;
                }
            }

            //System.out.println("Impact of task " + task.name() + " : " + currentImpact);
            //System.out.println();
            task.setImpact(currentImpact);
        }
    }

    private static int computeTaskCost(final Task task)
    {
        int cost = 0;

        for (Resource resource : task.resourceUsage().resources())
        {
            cost += task.resourceUsage().getUsageOf(resource) * resource.cost() * DistributionUtils.averageIntegerValue(task.duration());
        }

        return cost;
    }

    private static Comparator<RefactoringSolution> refactoringSolutionComparator()
    {
        return Comparator.comparingDouble(o -> o.treeNode().score());
    }

    private static void dumpProcess(final RefactoringSolution refactoringSolution,
                                    final CommandLineParser commandLineParser,
                                    final int i) throws IOException, BadDependencyException
	{
        if (refactoringSolution.treeNode().mainCluster().abstractGraphs().isEmpty())
        {
            Utils.resetIndependentNodes(refactoringSolution.treeNode().mainCluster());
            final AbstractGraphsGenerator abstractGraphsGenerator = new AbstractGraphsGenerator(refactoringSolution.treeNode().mainCluster());
            abstractGraphsGenerator.generateAbstractGraphs();
        }

        final BPMNGenerator generator = new BPMNGenerator(refactoringSolution.treeNode().mainCluster());
        final Graph currentGraph = generator.generate();
        generator.unprocessClusters();

        final GraphToList graphToList = new GraphToList(currentGraph);
        graphToList.convert();

        Main.dumpProcess(graphToList.objectsList(), commandLineParser, i);
    }

    private static void dumpProcess(final ArrayList<BpmnProcessObject> bpmnProcessObjects,
                                    final CommandLineParser commandLineParser,
                                    final int i) throws IOException
    {
        final GraphicalGenerationWriter writer = new GraphicalGenerationWriter(
            commandLineParser,
            bpmnProcessObjects,
            "best_step_" + i
        );
        writer.write();
    }

    private static void dumpProcess(final ArrayList<BpmnProcessObject> bpmnProcessObjects,
                                    final CommandLineParser commandLineParser,
                                    final String name) throws IOException
    {
        final GraphicalGenerationWriter writer = new GraphicalGenerationWriter(
            commandLineParser,
            bpmnProcessObjects,
            name
        );
        writer.write();
    }

    public static void verifySolution(final RefactoringSolution refactoringSolution,
                                      final ArrayList<BpmnProcessObject> originalProcessObjects,
                                      final CommandLineParser commandLineParser) throws BadDependencyException, IOException
	{
        /*
            TODO: In some cases the method "verifySolution()" based on the cluster returns properly while
                  the one based on the generated process doesn't.
                  Is there any issue in the traduction from cluster to BPMN?
         */
        final Cluster copyCluster = refactoringSolution.treeNode().mainCluster().copy();
        Main.verifySolution(refactoringSolution.treeNode().mainCluster(), originalProcessObjects, commandLineParser);

        final HashMap<String, Integer> originalTaskPresence = new HashMap<>();
        final HashMap<String, Integer> finalTaskPresence = new HashMap<>();

        if (refactoringSolution.treeNode().mainCluster().abstractGraphs().isEmpty())
        {
            Utils.resetIndependentNodes(refactoringSolution.treeNode().mainCluster());
            final AbstractGraphsGenerator abstractGraphsGenerator = new AbstractGraphsGenerator(refactoringSolution.treeNode().mainCluster(), false);
            abstractGraphsGenerator.generateAbstractGraphs();
        }

        final BPMNGenerator generator = new BPMNGenerator(refactoringSolution.treeNode().mainCluster());
        final Graph currentGraph = generator.generate();
        generator.unprocessClusters();

        final GraphToList graphToList = new GraphToList(currentGraph);
        final HashSet<BpmnProcessObject> generatedProcessObjects = graphToList.convert();

        //Count tasks in original process
        for (BpmnProcessObject object : originalProcessObjects)
        {
            if (object instanceof Task)
            {
                if (originalTaskPresence.containsKey(object.name()))
                {
                    final int newPresence = originalTaskPresence.get(object.name()) + 1;
                    originalTaskPresence.put(object.name(), newPresence);
                }
                else
                {
                    originalTaskPresence.put(object.name(), 1);
                }
            }
        }

        //Count tasks in generated process
        for (BpmnProcessObject object : generatedProcessObjects)
        {
            if (object instanceof Task)
            {
                if (finalTaskPresence.containsKey(object.name()))
                {
                    final int newPresence = finalTaskPresence.get(object.name()) + 1;
                    finalTaskPresence.put(object.name(), newPresence);
                }
                else
                {
                    finalTaskPresence.put(object.name(), 1);
                }
            }
        }

        //Compare numbers
        for (String taskName : originalTaskPresence.keySet())
        {
            final int originalNumberOfTasks = originalTaskPresence.get(taskName);
            final int newNumberOfTasks = finalTaskPresence.getOrDefault(taskName, 0);

            if (originalNumberOfTasks > newNumberOfTasks)
            {
                /*
                    This should never happen, as the only case where the number of occurrences of a task
                    may be subject to changes is when a task enters a choice, in which case its number of
                    occurrences decreases.
                 */
                System.out.println("Copied cluster:\n\n" + copyCluster.stringify(0));
                Main.dumpProcess(graphToList.objectsList(), commandLineParser, "invalid");

                throw new RuntimeException(
                        "Task \"" + taskName + "\" was originally present " + originalNumberOfTasks + " time(s) in the " +
                                "process and is now present " + newNumberOfTasks + " time(s)!"
                );
            }
            else if (originalNumberOfTasks < newNumberOfTasks)
            {
                /*
                    This may happen when a task enters a choice, but this may also be caused by an error somewhere.
                    Thus, warn the user.
                 */
                System.out.println(
                        "Warning: Task \"" + taskName + "\" was originally present only " + originalNumberOfTasks +
                                " time(s) but is now present " + newNumberOfTasks + " times!"
                );
            }
        }
    }

    public static void verifySolution(final Cluster mainCluster,
                                      final ArrayList<BpmnProcessObject> originalProcessObjects,
                                      final CommandLineParser commandLineParser) throws BadDependencyException, IOException
    {
        final HashMap<String, Integer> originalTaskPresence = new HashMap<>();
        final HashMap<String, Integer> finalTaskPresence = mainCluster.getTaskPresence();

        //Count tasks in original process
        for (BpmnProcessObject object : originalProcessObjects)
        {
            if (object instanceof Task)
            {
                if (originalTaskPresence.containsKey(object.name()))
                {
                    final int newPresence = originalTaskPresence.get(object.name()) + 1;
                    originalTaskPresence.put(object.name(), newPresence);
                }
                else
                {
                    originalTaskPresence.put(object.name(), 1);
                }
            }
        }

        //Compare numbers
        for (String taskName : originalTaskPresence.keySet())
        {
            final int originalNumberOfTasks = originalTaskPresence.get(taskName);
            final int newNumberOfTasks = finalTaskPresence.getOrDefault(taskName, 0);

            if (originalNumberOfTasks > newNumberOfTasks)
            {
                /*
                    This should never happen, as the only case where the number of occurrences of a task
                    may be subject to changes is when a task enters a choice, in which case its number of
                    occurrences decreases.
                 */
                if (mainCluster.abstractGraphs().isEmpty())
                {
                    Utils.resetIndependentNodes(mainCluster);
                    final AbstractGraphsGenerator abstractGraphsGenerator = new AbstractGraphsGenerator(mainCluster);
                    abstractGraphsGenerator.generateAbstractGraphs();
                }

                final BPMNGenerator generator = new BPMNGenerator(mainCluster);
                final Graph currentGraph = generator.generate();
                generator.unprocessClusters();

                final GraphToList graphToList = new GraphToList(currentGraph);
                final HashSet<BpmnProcessObject> generatedProcessObjects = graphToList.convert();

                Main.dumpProcess(graphToList.objectsList(), commandLineParser, "invalid");

                throw new RuntimeException(
                        "Task \"" + taskName + "\" was originally present " + originalNumberOfTasks + " time(s) in the " +
                                "process and is now present " + newNumberOfTasks + " time(s)!"
                );
            }
            else if (originalNumberOfTasks < newNumberOfTasks)
            {
                /*
                    This may happen when a task enters a choice, but this may also be caused by an error somewhere.
                    Thus, warn the user.
                 */
                System.out.println(
                        "Warning: Task \"" + taskName + "\" was originally present only " + originalNumberOfTasks +
                                " time(s) but is now present " + newNumberOfTasks + " times!"
                );
            }
        }
    }

    private static void jmetalize(final Algorithm algorithm,
                                  final RefactoringProblem multiObjectivesRefactoringProblem,
                                  final RefactoringProblem singleObjectiveRefactoringProblem,
                                  final CrossoverOperator<RefactoringSolution> crossoverOperator,
                                  final MutationOperator<RefactoringSolution> mutationOperator,
                                  final RefactoringDominanceComparator dominanceComparator,
                                  final List<RefactoringSolution> solutions,
                                  final int maxEvaluations,
                                  final int maxIterations,
                                  final int population)
    {
        if (algorithm == Algorithm.AGEMOEA)
        {
            final AGEMOEABuilder<RefactoringSolution> builder = new AGEMOEABuilder<>(
                    multiObjectivesRefactoringProblem
            );
            builder.setCrossoverOperator(crossoverOperator);
            builder.setMutationOperator(mutationOperator);
            builder.setMaxIterations(maxIterations);
            builder.setPopulationSize(population);
            final AGEMOEA<RefactoringSolution> agemoea = builder.build();
            agemoea.run();
            solutions.addAll(agemoea.result());
        }
        else if (algorithm == Algorithm.AGEMOEAII)
        {
            final AGEMOEAIIBuilder<RefactoringSolution> builder = new AGEMOEAIIBuilder<>(
                    multiObjectivesRefactoringProblem
            );
            builder.setCrossoverOperator(crossoverOperator);
            builder.setMutationOperator(mutationOperator);
            builder.setMaxIterations(maxIterations);
            builder.setPopulationSize(population);
            final AGEMOEAII<RefactoringSolution> agemoeaii = builder.build();
            agemoeaii.run();
            solutions.addAll(agemoeaii.result());
        }
        else if (algorithm == Algorithm.ESPEA)
        {
            final ESPEABuilder<RefactoringSolution> builder = new ESPEABuilder<>(
                    multiObjectivesRefactoringProblem,
                    crossoverOperator,
                    mutationOperator
            );
            builder.setMaxEvaluations(maxEvaluations);
            builder.setPopulationSize(population);
            final ESPEA<RefactoringSolution> espea = builder.build();
            espea.run();
            solutions.addAll(espea.result());
        }
        else if (algorithm == Algorithm.NSGAII)
        {
            final NSGAIIBuilder<RefactoringSolution> builder = new NSGAIIBuilder<>(
                    multiObjectivesRefactoringProblem,
                    crossoverOperator,
                    mutationOperator,
                    population
            );
            builder.setDominanceComparator(dominanceComparator);
            builder.setMaxEvaluations(maxEvaluations);
            final NSGAII<RefactoringSolution> nsgaii = builder.build();
            nsgaii.run();
            solutions.addAll(nsgaii.result());
        }
        else if (algorithm == Algorithm.RandomSearch)
        {
            final RandomSearchBuilder<RefactoringSolution> builder = new RandomSearchBuilder<>(
                    multiObjectivesRefactoringProblem
            );
            builder.setMaxEvaluations(maxEvaluations);
            final RandomSearch<RefactoringSolution> randomSearch = builder.build();
            randomSearch.run();
            solutions.addAll(randomSearch.result());
        }
        else if (algorithm == Algorithm.SMSEMOA)
        {
            final SMSEMOABuilder<RefactoringSolution> builder = new SMSEMOABuilder<>(
                    multiObjectivesRefactoringProblem,
                    crossoverOperator,
                    mutationOperator
            );
            builder.setPopulationSize(population);
            builder.setMaxEvaluations(maxEvaluations);
            builder.setDominanceComparator(dominanceComparator);
            final SMSEMOA<RefactoringSolution> smsemoa = builder.build();
            smsemoa.run();
            solutions.addAll(smsemoa.result());
        }
        else if (algorithm == Algorithm.SPEA2)
        {
            final SPEA2Builder<RefactoringSolution> builder = new SPEA2Builder<>(
                    multiObjectivesRefactoringProblem,
                    crossoverOperator,
                    mutationOperator
            );
            builder.setMaxIterations(maxIterations);
            builder.setPopulationSize(population);
            final SPEA2<RefactoringSolution> spea2 = builder.build();
            spea2.run();
            solutions.addAll(spea2.result());
        }
    }

	private static HashMap<Long, Double> jmetalizeV2(final Algorithm algorithm,
																	final RefactoringProblem multiObjectivesRefactoringProblem,
																	final RefactoringProblem singleObjectiveRefactoringProblem,
																	final CrossoverOperator<RefactoringSolution> crossoverOperator,
																	final MutationOperator<RefactoringSolution> mutationOperator,
																	final RefactoringDominanceComparator dominanceComparator,
																	final List<RefactoringSolution> solutions,
																	final int maxEvaluations,
																	final int maxIterations,
																	final int population,
																	final TreeNode initialNode,
																	final OptimizationCriteria optimizationCriteria)
	{
		if (algorithm == Algorithm.AGEMOEA)
		{
			final AGEMOEABuilder<RefactoringSolution> builder = new AGEMOEABuilder<>(
					multiObjectivesRefactoringProblem
			);
			builder.setCrossoverOperator(crossoverOperator);
			builder.setMutationOperator(mutationOperator);
			builder.setMaxIterations(maxIterations);
			builder.setPopulationSize(population);
			final MyAGEMOEA<RefactoringSolution> agemoea = new MyAGEMOEA<>(builder, initialNode, optimizationCriteria);
			agemoea.run();
			solutions.addAll(agemoea.result());
			return agemoea.dumpedStepsInformation();
		}
		else if (algorithm == Algorithm.AGEMOEAII)
		{
			final AGEMOEAIIBuilder<RefactoringSolution> builder = new AGEMOEAIIBuilder<>(
					multiObjectivesRefactoringProblem
			);
			builder.setCrossoverOperator(crossoverOperator);
			builder.setMutationOperator(mutationOperator);
			builder.setMaxIterations(maxIterations);
			builder.setPopulationSize(population);
			final MyAGEMOEAII<RefactoringSolution> agemoeaii = new MyAGEMOEAII<>(builder, initialNode, optimizationCriteria);
			agemoeaii.run();
			solutions.addAll(agemoeaii.result());
			return agemoeaii.dumpedStepsInformation();
		}
		else if (algorithm == Algorithm.ESPEA)
		{
			final ESPEABuilder<RefactoringSolution> builder = new ESPEABuilder<>(
					multiObjectivesRefactoringProblem,
					crossoverOperator,
					mutationOperator
			);
			builder.setMaxEvaluations(maxEvaluations);
			builder.setPopulationSize(population);
			final MyESPEA<RefactoringSolution> espea = new MyESPEA<>(builder, multiObjectivesRefactoringProblem, crossoverOperator, mutationOperator, initialNode, optimizationCriteria);
			espea.run();
			solutions.addAll(espea.result());
			return espea.dumpedStepsInformation();
		}
		else if (algorithm == Algorithm.NSGAII)
		{
			final NSGAIIBuilder<RefactoringSolution> builder = new NSGAIIBuilder<>(
					multiObjectivesRefactoringProblem,
					crossoverOperator,
					mutationOperator,
					population
			);
			builder.setDominanceComparator(dominanceComparator);
			builder.setMaxEvaluations(maxEvaluations);
			final MyNSGAII<RefactoringSolution> nsgaii = new MyNSGAII<>(builder, dominanceComparator, initialNode, optimizationCriteria);
			nsgaii.run();
			solutions.addAll(nsgaii.result());
			return nsgaii.dumpedStepsInformation();
		}
		else if (algorithm == Algorithm.RandomSearch)
		{
			final MyRandomSearch<RefactoringSolution> randomSearch = new MyRandomSearch<>(multiObjectivesRefactoringProblem, maxEvaluations, initialNode, optimizationCriteria);
			randomSearch.run();
			solutions.addAll(randomSearch.result());
			return randomSearch.dumpedStepsInformation();
		}
		else if (algorithm == Algorithm.SMSEMOA)
		{
			final SMSEMOABuilder<RefactoringSolution> builder = new SMSEMOABuilder<>(
					multiObjectivesRefactoringProblem,
					crossoverOperator,
					mutationOperator
			);
			builder.setPopulationSize(population);
			builder.setMaxEvaluations(maxEvaluations);
			builder.setDominanceComparator(dominanceComparator);
			final MySMSEMOA<RefactoringSolution> smsemoa = new MySMSEMOA<>(builder, dominanceComparator, initialNode, optimizationCriteria);
			smsemoa.run();
			solutions.addAll(smsemoa.result());
			return smsemoa.dumpedStepsInformation();
		}
		else if (algorithm == Algorithm.SPEA2)
		{
			final SPEA2Builder<RefactoringSolution> builder = new SPEA2Builder<>(
					multiObjectivesRefactoringProblem,
					crossoverOperator,
					mutationOperator
			);
			builder.setMaxIterations(maxIterations);
			builder.setPopulationSize(population);
			final MySPEA2<RefactoringSolution> spea2 = new MySPEA2<>(builder, initialNode, optimizationCriteria);
			spea2.run();
			solutions.addAll(spea2.result());
			return spea2.dumpedStepsInformation();
		}
		else if (algorithm == Algorithm.MOSA)
		{
			//Simulated annealing
			final BoundedArchive<RefactoringSolution> archive = new GenericBoundedArchive<>(100, new CrowdingDistanceDensityEstimator<>());
			final MyMOSA<RefactoringSolution> mosa = new MyMOSA<>(
				new MultiObjectivesRefactoringSolution(initialNode, optimizationCriteria),
				multiObjectivesRefactoringProblem,
				25000,
				archive,
				mutationOperator,
				5.0,
				new Exponential(0.8),
				dominanceComparator,
				initialNode,
				optimizationCriteria
			);
			mosa.run();
			solutions.addAll(mosa.result());

			if (solutions.isEmpty())
			{
				throw new RuntimeException("MOSA algorithm returned 0 solution!");
			}

			return mosa.dumpedStepsInformation();
		}
		else if (algorithm == Algorithm.LOCAL_SEARCH)
		{
			final MyBasicLocalSearch<RefactoringSolution> localSearch = new MyBasicLocalSearch<>(
				new SingleObjectiveRefactoringSolution(initialNode, optimizationCriteria),
				Integer.MAX_VALUE,
				singleObjectiveRefactoringProblem,
				mutationOperator,
				dominanceComparator,
				initialNode,
				optimizationCriteria
			);
			localSearch.run();
			solutions.add(localSearch.result());
			return localSearch.dumpedStepsInformation();
		}

		throw new IllegalStateException();
	}
}