package jmetal_impl.interruptibility;

import jmetal_impl.Algorithm;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import other.Utils;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Scanner;

public class DiskLoader
{
	private DiskLoader()
	{

	}

	public static HashMap<String, Integer> loadFromDisk(final File workingDirectory,
														final Algorithm algorithm)
	{
		final HashMap<String, Integer> infos = new HashMap<>();

		try
		{
			final File algorithmDir = DiskLoader.getAlgorithmDir(workingDirectory, algorithm);

			if (algorithmDir == null)
			{
				return new HashMap<>();
			}

			final int nbRepetitionsDesired = DiskLoader.getNbRepetitionsDesired(algorithmDir);
			final int nbRepetitionsDone = DiskLoader.getNbRepetitionsDone(algorithmDir);
			System.out.println("Nb repetitions desired: " + nbRepetitionsDesired);
			System.out.println("Nb repetitions done: " + nbRepetitionsDone);

			if (nbRepetitionsDone <= nbRepetitionsDesired)
			{
				infos.put(JSONConstant.NUMBER_OF_REPETITIONS_DESIRED, nbRepetitionsDesired);
				infos.put(JSONConstant.NUMBER_OF_REPETITIONS_DONE, nbRepetitionsDone);
			}
			else
			{
				throw new RuntimeException();
			}

			System.out.println("avant");
			infos.putAll(DiskLoader.getAlgorithmParams(algorithmDir));

			System.out.println("après");
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}

		return infos;
	}

	//Private methods

	private static File getAlgorithmDir(final File workingDirectory,
										final Algorithm algorithm)
	{
		final File algorithmDir = new File(
	workingDirectory.getAbsolutePath() + File.separator +
			JSONConstant.RESULTS_DIR_NAME + File.separator +
			algorithm.getName()
		);

		if (!algorithmDir.exists())
		{
			return null;
		}

		return algorithmDir;
	}

	private static int getNbRepetitionsDesired(final File algorithmDir) throws FileNotFoundException
	{
		final File nbRepetitionsDesiredFile = new File(algorithmDir.getAbsolutePath() + File.separator + JSONConstant.NUMBER_OF_REPETITIONS_DESIRED_FILENAME);

		if (!nbRepetitionsDesiredFile.exists())
		{
			return -1;
		}

		final Scanner scanner = new Scanner(nbRepetitionsDesiredFile);
		final StringBuilder builder = new StringBuilder();

		while (scanner.hasNextLine())
		{
			builder.append(scanner.nextLine().replace("\n", "").trim());
		}

		scanner.close();

		if (!Utils.isAnInt(builder.toString()))
		{
			return -1;
		}

		return Integer.parseInt(builder.toString());
	}

	private static int getNbRepetitionsDone(final File algorithmDir)
	{
		int nbRepetitionsDone = 0;
		final HashSet<File> dirsToDelete = new HashSet<>();

		for (File file : Objects.requireNonNull(algorithmDir.listFiles()))
		{
			//System.out.println("File name: " + file.getName());

			if (file.isDirectory()
				&& Utils.isAnInt(file.getName()))
			{
				final File resultsFile = new File(
					file.getAbsolutePath() + File.separator + JSONConstant.RESULTS_FILENAME_WITHOUT_EXTENSION + "_1.json"
				);

				if (resultsFile.exists())
				{
					nbRepetitionsDone++;
				}
				else
				{
					dirsToDelete.add(file);
				}
			}
		}

		for (File dirToDelete : dirsToDelete)
		{
			try
			{
				FileUtils.deleteDirectory(dirToDelete);
				System.out.println("Directory \"" + dirToDelete.getAbsolutePath() + "\" deleted.");
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}

		return nbRepetitionsDone;
	}

	private static HashMap<String, Integer> getAlgorithmParams(final File algorithmDir) throws FileNotFoundException
	{
		final HashMap<String, Integer> algorithmParams = new HashMap<>();

		for (File file : Objects.requireNonNull(algorithmDir.listFiles()))
		{
			if (file.isDirectory()
				&& Utils.isAnInt(file.getName()))
			{
				final File parametersFile = new File(
			file.getAbsolutePath() + File.separator + JSONConstant.ALGORITHM_PARAMETERS_FILENAME
				);

				if (parametersFile.exists())
				{
					final Scanner scanner = new Scanner(parametersFile);
					final StringBuilder builder = new StringBuilder();

					while (scanner.hasNextLine())
					{
						builder.append(scanner.nextLine().replace("\n", "").trim());
					}

					scanner.close();

					final JSONObject parametersJSON = new JSONObject(builder.toString());
					algorithmParams.put(JSONConstant.MAX_ITERATIONS, parametersJSON.optInt(JSONConstant.MAX_ITERATIONS, -1));
					algorithmParams.put(JSONConstant.MAX_EVALUATIONS, parametersJSON.optInt(JSONConstant.MAX_EVALUATIONS, -1));
					algorithmParams.put(JSONConstant.POPULATION_SIZE, parametersJSON.optInt(JSONConstant.POPULATION_SIZE, -1));

					break;
				}
			}
		}

		return algorithmParams;
	}
}
