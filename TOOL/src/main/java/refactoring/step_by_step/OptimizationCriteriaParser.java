package refactoring.step_by_step;

import other.Pair;
import other.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Scanner;

public class OptimizationCriteriaParser
{
	private final File optimizationCriteriaFile;

	public OptimizationCriteriaParser(final File file)
	{
		this.optimizationCriteriaFile = file;
	}

	public OptimizationCriteria parse() throws FileNotFoundException
	{
		final Scanner scanner = new Scanner(this.optimizationCriteriaFile);
		int aetWeight = 0;
		int costWeight = 0;
		final HashMap<String, Pair<Integer, Double>> resourcesGoalUsageAndWeight = new HashMap<>();

		while (scanner.hasNextLine())
		{
			final String line = scanner.nextLine();

			if (line.startsWith("-"))
			{
				if (line.toLowerCase().contains("aet"))
				{
					//AET
					final int weight = this.retrieveFirstIntInString(line);

					if (weight >= 0 && weight <= 100)
					{
						aetWeight = weight;
					}
				}
				else if (line.toLowerCase().contains("cost"))
				{
					//Cost
					final int weight = this.retrieveFirstIntInString(line);

					if (weight >= 0 && weight <= 100)
					{
						costWeight = weight;
					}
				}
				else
				{
					//Resources
					final String name = line.substring(line.indexOf("-") + 1, line.indexOf("/")).trim();
					final String targetString = line.substring(line.indexOf("Target"), line.indexOf("Weight"));
					final String weightString = line.substring(line.indexOf("Weight"));
					final int target = this.retrieveFirstIntInString(targetString);
					final int weightPercentage = this.retrieveFirstIntInString(weightString);

					final double weight =
						weightPercentage == 100 ?
						1d :
							(weightPercentage < 10 ?
							Double.valueOf("0.0" + weightPercentage) :
							Double.valueOf("0." + weightPercentage)); //Convert percentage to double

					resourcesGoalUsageAndWeight.put(name, new Pair<>(target, weight));
				}
			}
		}

		scanner.close();

		return new OptimizationCriteria(resourcesGoalUsageAndWeight, costWeight, aetWeight);
	}

	private int retrieveFirstIntInString(final String line)
	{
		final StringBuilder builder = new StringBuilder();
		boolean previousCharIsAnInt = false;

		for (char c : line.toCharArray())
		{
			if (Utils.isAnInt(c))
			{
				if (builder.isEmpty()
					|| previousCharIsAnInt)
				{
					builder.append(c);
					previousCharIsAnInt = true;
				}
			}
			else
			{
				previousCharIsAnInt = false;
			}
		}

		return Integer.parseInt(builder.toString());
	}
}
