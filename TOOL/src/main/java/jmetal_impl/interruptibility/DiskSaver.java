package jmetal_impl.interruptibility;

import bpmn.graph.Graph;
import bpmn.graph.GraphToList;
import bpmn.writing.generation.GraphicalGenerationWriter;
import constants.CommandLineOption;
import jmetal_impl.Algorithm;
import jmetal_impl.RefactoringSolution;
import main.CommandLineParser;
import org.json.JSONArray;
import org.json.JSONObject;
import other.Pair;
import other.Utils;
import refactoring.legacy.exceptions.BadDependencyException;
import refactoring.legacy.partial_order_to_bpmn.AbstractGraphsGenerator;
import refactoring.legacy.partial_order_to_bpmn.BPMNGenerator;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class DiskSaver
{
	/**
	 * DiskSaver is used to store permanently the data that has already been computed.
	 * It stores the algorithm used, the parameters that were given to it, and the results obtained
	 * both in terms of quality and execution time.
	 *
	 */
	private DiskSaver()
	{

	}

	public static boolean saveToDisk(final CommandLineParser commandLineParser,
									 final File originalProcess,
									 final ArrayList<Pair<RefactoringSolution, HashMap<String, Double>>> results,
									 final HashMap<String, Double> originalProcessMetrics,
									 final long executionTime,
									 final Algorithm algorithm,
									 final AlgorithmParameters algorithmParameters,
									 final int nbRepetitions,
									 final HashMap<Long, Double> dumpedInformation)
	{
		try
		{
			final File workingDirectory = (File) commandLineParser.get(CommandLineOption.WORKING_DIRECTORY);
			final File resultsDir = DiskSaver.createResultsDirIfNeeded(workingDirectory);
			final File algorithmDir = DiskSaver.createAlgorithmDirIfNeeded(resultsDir, algorithm);
			final File currentSubDir = DiskSaver.createCurrentSubDirectory(algorithmDir);
			DiskSaver.copyOriginalProcess(currentSubDir, originalProcess);
			DiskSaver.writeNbRepetitionsToPerform(algorithmDir, nbRepetitions);
			DiskSaver.saveAlgorithmParameters(currentSubDir, algorithmParameters);
			DiskSaver.saveResults(currentSubDir, originalProcessMetrics, executionTime, -1d, "original");
			DiskSaver.saveDumpedInformation(currentSubDir, dumpedInformation);
			int processIndex = 1;

			for (Pair<RefactoringSolution, HashMap<String, Double>> result : results)
			{
				DiskSaver.saveGeneratedProcess(currentSubDir, result.first(), processIndex);
				DiskSaver.saveResults(currentSubDir, result.second(), executionTime, result.first().treeNode().score(), processIndex);
				processIndex++;
			}
		}
		catch (Exception e)
		{
			return false;
		}

		return true;
	}

	//Private methods

	private static File createResultsDirIfNeeded(final File workingDirectory)
	{
		final File resultsDir = new File(
			workingDirectory.getAbsolutePath() + File.separator +
			JSONConstant.RESULTS_DIR_NAME
		);

		if (!resultsDir.exists())
		{
			if (!resultsDir.mkdir())
			{
				throw new RuntimeException();
			}
		}

		return resultsDir;
	}

	private static File createAlgorithmDirIfNeeded(final File resultsDir,
												   final Algorithm algorithm)
	{
		final File algorithmDir = new File(
	resultsDir.getAbsolutePath() + File.separator +
			algorithm.getName()
		);

		if (!algorithmDir.exists())
		{
			if (!algorithmDir.mkdir())
			{
				throw new RuntimeException();
			}
		}

		return algorithmDir;
	}

	private static File createCurrentSubDirectory(final File algorithmDir)
	{
		int dirIndex = 1;

		File subDirectory = new File(algorithmDir.getAbsolutePath() + File.separator + dirIndex);

		while (subDirectory.exists())
		{
			subDirectory = new File(algorithmDir.getAbsolutePath() + File.separator + ++dirIndex);
		}

		if (!subDirectory.mkdir())
		{
			throw new RuntimeException();
		}

		return subDirectory;
	}

	private static void copyOriginalProcess(final File currentSubDir,
											final File originalProcess) throws IOException
	{
		final File copiedProcess = new File(
	currentSubDir.getAbsolutePath() + File.separator +
			originalProcess.getName()
		);

		if (copiedProcess.exists())
		{
			throw new RuntimeException();
		}

		Files.copy(
			Path.of(originalProcess.getAbsolutePath()),
			Path.of(copiedProcess.getAbsolutePath()),
			StandardCopyOption.REPLACE_EXISTING
		);

		if (!copiedProcess.exists())
		{
			throw new RuntimeException();
		}
	}

	private static void saveAlgorithmParameters(final File currentSubDir,
												final AlgorithmParameters parameters) throws FileNotFoundException
	{
		final JSONObject parametersJSON = new JSONObject();

		if (parameters.getMaxEvaluations() != -1)
		{
			parametersJSON.put(JSONConstant.MAX_EVALUATIONS, parameters.getMaxEvaluations());
		}

		if (parameters.getMaxIterations() != -1)
		{
			parametersJSON.put(JSONConstant.MAX_ITERATIONS, parameters.getMaxIterations());
		}

		if (parameters.getPopulationSize() != -1)
		{
			parametersJSON.put(JSONConstant.POPULATION_SIZE, parameters.getPopulationSize());
		}

		final File parametersFile = new File(
	currentSubDir.getAbsolutePath() + File.separator +
			JSONConstant.ALGORITHM_PARAMETERS_FILENAME
		);

		if (parametersFile.exists())
		{
			throw new RuntimeException();
		}

		final PrintWriter printWriter = new PrintWriter(parametersFile);
		printWriter.print(parametersJSON);
		printWriter.flush();
		printWriter.close();

		if (!parametersFile.exists())
		{
			throw new RuntimeException();
		}
	}

	private static void saveGeneratedProcess(final File currentSubDir,
											 final RefactoringSolution refactoringSolution,
											 final int processIndex) throws BadDependencyException, IOException
	{
		final File refactoredProcess = new File(
	currentSubDir.getAbsolutePath() + File.separator +
			"refactored_process_" + processIndex + ".bpmn"
		);

		if (refactoredProcess.exists())
		{
			throw new RuntimeException();
		}

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

		final GraphicalGenerationWriter writer = new GraphicalGenerationWriter(
			currentSubDir,
			graphToList.objectsList(),
			processIndex
		);
		writer.write();

		if (!refactoredProcess.exists())
		{
			throw new RuntimeException();
		}
	}

	private static void saveResults(final File currentSubDir,
									final HashMap<String, Double> results,
									final Long executionTime,
									final double score,
									final int resultsIndex) throws FileNotFoundException
	{
		DiskSaver.saveResults(currentSubDir, results, executionTime, score, String.valueOf(resultsIndex));
	}

	private static void saveResults(final File currentSubDir,
									final HashMap<String, Double> results,
									final Long executionTime,
									final double score,
									final String resultsIndex) throws FileNotFoundException
	{
		final File resultsFile = new File(
				currentSubDir.getAbsolutePath() + File.separator +
						JSONConstant.RESULTS_FILENAME_WITHOUT_EXTENSION + "_" + resultsIndex + ".json"
		);

		if (resultsFile.exists())
		{
			throw new RuntimeException();
		}

		final JSONObject resultsJSON = new JSONObject();
		resultsJSON.put(JSONConstant.EXECUTION_TIME_NANO, executionTime);

		for (String optimisationCriterion : results.keySet())
		{
			resultsJSON.put(optimisationCriterion, results.get(optimisationCriterion));
		}

		resultsJSON.put(JSONConstant.SCORE, score);

		final PrintWriter printWriter = new PrintWriter(resultsFile);
		printWriter.print(resultsJSON);
		printWriter.flush();
		printWriter.close();

		if (!resultsFile.exists())
		{
			throw new RuntimeException();
		}
	}

	private static void writeNbRepetitionsToPerform(final File algorithmDir,
													final int nbRepetitionsDesired) throws FileNotFoundException
	{
		final File nbRepetitionsFile = new File(algorithmDir.getAbsolutePath() + File.separator + JSONConstant.NUMBER_OF_REPETITIONS_DESIRED_FILENAME);

		if (nbRepetitionsFile.exists())
		{
			final Scanner scanner = new Scanner(nbRepetitionsFile);
			final StringBuilder builder = new StringBuilder();

			while (scanner.hasNextLine())
			{
				builder.append(scanner.nextLine().replace("\n", "").trim());
			}

			scanner.close();

			final String content = builder.toString();

			if (!Utils.isAnInt(content))
			{
				throw new RuntimeException();
			}

			if (Integer.parseInt(content) != nbRepetitionsDesired)
			{
				throw new RuntimeException();
			}
		}
		else
		{
			final PrintWriter printWriter = new PrintWriter(nbRepetitionsFile);
			printWriter.print(nbRepetitionsDesired);
			printWriter.flush();
			printWriter.close();
		}

		if (!nbRepetitionsFile.exists())
		{
			throw new RuntimeException();
		}
	}

	public static void saveDumpedInformation(final File currentSubDir,
											 final HashMap<Long, Double> dumpedInformation) throws FileNotFoundException
	{
		final File dumpedInformationFile = new File(
	currentSubDir.getAbsolutePath() + File.separator +
			JSONConstant.DUMPED_INFORMATION_FILENAME
		);

		if (dumpedInformationFile.exists())
		{
			throw new RuntimeException();
		}

		final JSONArray array = new JSONArray();

		for (long timestamp : dumpedInformation.keySet())
		{
			if (dumpedInformation.get(timestamp) == null) continue;

			final JSONObject currentInformation = new JSONObject();
			currentInformation.put(JSONConstant.TIMESTAMP, timestamp);
			currentInformation.put(JSONConstant.PERCENTAGE_OF_OPTIMISATION, dumpedInformation.get(timestamp));
			//currentInformation.put(JSONConstant.EXECUTION_TIME_NANO, dumpedInformation.get(iteration).second());
			array.put(currentInformation);
		}

		final PrintWriter printWriter = new PrintWriter(dumpedInformationFile);
		printWriter.print(array);
		printWriter.flush();
		printWriter.close();

		if (!dumpedInformationFile.exists())
		{
			throw new RuntimeException();
		}
	}
}
