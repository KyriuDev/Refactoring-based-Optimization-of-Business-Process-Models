package main;

import jmetal_impl.Algorithm;
import jmetal_impl.interruptibility.AlgorithmParameters;
import jmetal_impl.interruptibility.DiskLoader;
import jmetal_impl.interruptibility.JSONConstant;
import jmetal_impl.multi_objectives.characteristics.*;
import other.Triple;
import other.Utils;
import constants.CommandLineOption;
import refactoring.legacy.dependencies.DependenciesParser;
import refactoring.step_by_step.OptimizationCriteriaParser;
import resources.ResourcePool;
import resources.ResourcePoolParser;
import simulator.bpmn_to_model_input.GlobalInfosParser;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CommandLineParser
{
    private final Map<CommandLineOption, Object> commands;

    public CommandLineParser(final String... args) throws FileNotFoundException
    {
        this.commands = new HashMap<>();
        this.parse(args);

        if (this.commands.containsKey(CommandLineOption.HELP))
        {
            System.out.println(this.helpMessage());
            this.commands.clear();
        }

        this.retrieveArgsFromWorkingDirectory();

        if (!this.verifyArgs())
        {
            throw new IllegalStateException("Necessary arguments are missing. Please make sure you have specified the" +
                    "following elements: IAT, BPMN process, resource pool, or that the working directory that you have" +
                    "specified contains all of these elements.");
        }
    }

    public Object get(CommandLineOption commandLineOption)
    {
        return this.commands.get(commandLineOption);
    }

    public boolean containsKey(final CommandLineOption key)
    {
        return this.commands.containsKey(key);
    }

    //Private methods

    private String helpMessage()
    {
        //TODO
        return "";
    }

    private void parse(String[] commandLineArgs)
    {
        for (String arg : commandLineArgs)
        {
            System.out.println("Argument: " + arg);

            if (this.isWorkingDirectory(arg))
            {
                this.commands.put(CommandLineOption.WORKING_DIRECTORY, new File(arg));
            }
            else if (this.isOverwrite(arg))
            {
                this.commands.put(CommandLineOption.OVERWRITE, true);
            }
            else if (this.isAlgorithm(arg))
            {
                final String algorithm = arg.replace("-algorithm=", "").trim().toLowerCase();

                if (algorithm.equals(Algorithm.AGEMOEA.getName().toLowerCase()))
                {
                    this.commands.put(CommandLineOption.ALGORITHM, Algorithm.AGEMOEA);
                }
                else if (algorithm.equals(Algorithm.AGEMOEAII.getName().toLowerCase()))
                {
                    this.commands.put(CommandLineOption.ALGORITHM, Algorithm.AGEMOEAII);
                }
                else if (algorithm.equals(Algorithm.ESPEA.getName().toLowerCase()))
                {
                    this.commands.put(CommandLineOption.ALGORITHM, Algorithm.ESPEA);
                }
                else if (algorithm.equals(Algorithm.NSGAII.getName().toLowerCase()))
                {
                    this.commands.put(CommandLineOption.ALGORITHM, Algorithm.NSGAII);
                }
                else if (algorithm.equals(Algorithm.RandomSearch.getName().toLowerCase()))
                {
                    this.commands.put(CommandLineOption.ALGORITHM, Algorithm.RandomSearch);
                }
                else if (algorithm.equals(Algorithm.SMSEMOA.getName().toLowerCase()))
                {
                    this.commands.put(CommandLineOption.ALGORITHM, Algorithm.SMSEMOA);
                }
                else if (algorithm.equals(Algorithm.SPEA2.getName().toLowerCase()))
                {
                    this.commands.put(CommandLineOption.ALGORITHM, Algorithm.SPEA2);
                }
                else if (algorithm.equals(Algorithm.MOSA.getName().toLowerCase()))
                {
                    this.commands.put(CommandLineOption.ALGORITHM, Algorithm.MOSA);
                }
                else if (algorithm.equals(Algorithm.LOCAL_SEARCH.getName().toLowerCase()))
                {
                    this.commands.put(CommandLineOption.ALGORITHM, Algorithm.LOCAL_SEARCH);
                }
                else
                {
                    throw new IllegalStateException("Unrecognized algorithm |" + algorithm + "|.");
                }
            }
            else
            {
                throw new IllegalStateException("Unrecognized argument |" + arg + "|.");
            }
        }
    }

    private boolean isIAT(final String arg)
    {
        return Utils.isAnInt(arg);
    }

    private boolean isResourcePool(final String arg)
    {
        return (arg.endsWith(".rsp") || arg.endsWith(".rp"))
                && new File(arg).isFile();
    }

    private boolean isGlobalInfo(final String arg)
    {
        return arg.endsWith(".inf")
                && new File(arg).isFile();
    }

    private boolean isBpmnProcess(final String arg)
    {
        return arg.endsWith(".bpmn")
                && new File(arg).isFile();
    }

    private boolean isWorkingDirectory(final String arg)
    {
        return new File(arg).isDirectory();
    }

    private boolean isDependencies(final String arg)
    {
        return arg.endsWith(".dep")
                && new File(arg).isFile();
    }

    private boolean isOptimizationCriteria(final String arg)
    {
        return arg.endsWith(".opt")
                && new File(arg).isFile();
    }

    private boolean isOverwrite(final String arg)
    {
        final String lowerArg = arg.toLowerCase();

        return lowerArg.equals("-f")
                || lowerArg.equals("--f")
                || lowerArg.equals("-overwrite")
                || lowerArg.equals("--overwrite");
    }

    private boolean isAlgorithm(final String arg)
    {
        return arg.toLowerCase().trim().startsWith("-algorithm=");
    }

    private void retrieveArgsFromWorkingDirectory() throws FileNotFoundException
    {
        if (this.commands.get(CommandLineOption.WORKING_DIRECTORY) == null
            || this.commands.get(CommandLineOption.ALGORITHM) == null) throw new RuntimeException();

        //Working directory was specified --> check whether it contains a BPMN process and/or a resource file and load all the saved information
        final File workingDirectory = (File) this.commands.get(CommandLineOption.WORKING_DIRECTORY);
        final HashMap<String, Integer> infos = DiskLoader.loadFromDisk(workingDirectory, (Algorithm) this.commands.get(CommandLineOption.ALGORITHM));
		final Triple<Integer, Integer, Integer> defaultParameters = this.getDefaultParametersFromAlgo((Algorithm) this.commands.get(CommandLineOption.ALGORITHM));
		final int maxIterations = infos.getOrDefault(JSONConstant.MAX_ITERATIONS, defaultParameters.first());
		final int maxEvaluations = infos.getOrDefault(JSONConstant.MAX_EVALUATIONS, defaultParameters.second());
		final int populationSize = infos.getOrDefault(JSONConstant.POPULATION_SIZE, defaultParameters.third());
		final int nbProcessesAlreadyRefactored = infos.getOrDefault(JSONConstant.NUMBER_OF_REPETITIONS_DONE, 0);
		final int nbProcessesToRefactor = infos.getOrDefault(JSONConstant.NUMBER_OF_REPETITIONS_DESIRED, Main.NB_REPETITIONS);
		final AlgorithmParameters algorithmParameters = new AlgorithmParameters(
			maxIterations,
			maxEvaluations,
			populationSize,
			nbProcessesAlreadyRefactored,
			nbProcessesToRefactor
		);
		this.commands.put(CommandLineOption.ALGORITHM_PARAMETERS, algorithmParameters);

		for (File file : Objects.requireNonNull(workingDirectory.listFiles()))
        {
            if (this.isBpmnProcess(file.getPath()))
            {
                if (this.commands.containsKey(CommandLineOption.BPMN_PROCESS))
                {
                    System.out.println("Warning: BPMN Process |" +
                            ((File) this.commands.get(CommandLineOption.BPMN_PROCESS)).getPath() + "| will be" +
                            " overwritten by BPMN process |" + file.getPath() + "|.");
                }

                this.commands.put(CommandLineOption.BPMN_PROCESS, file);
            }
            else if (this.isResourcePool(file.getPath()))
            {
                final ResourcePool resourcePool = ResourcePoolParser.parse(file);

                if (this.commands.containsKey(CommandLineOption.REAL_POOL))
                {
                    System.out.println("Warning: Resource pool |" +
                            this.commands.get(CommandLineOption.REAL_POOL).toString() + "| will be" +
                            " overwritten by Resource pool |" + resourcePool + "|.");
                }

                this.commands.put(CommandLineOption.REAL_POOL, resourcePool);
            }
            else if (this.isGlobalInfo(file.getPath()))
            {
                final GlobalInfosParser parser = new GlobalInfosParser(file);

                if (this.commands.containsKey(CommandLineOption.GLOBAL_INFORMATION))
                {
                    System.out.println("Warning: Global information |" +
                            this.commands.get(CommandLineOption.GLOBAL_INFORMATION).toString() + "| will be" +
                            " overwritten by global information |" + file.getPath() + "|.");
                }

                this.commands.put(CommandLineOption.GLOBAL_INFORMATION, parser.parse());
            }
            else if (this.isDependencies(file.getPath()))
            {
                final DependenciesParser parser = new DependenciesParser(file);

                if (this.commands.containsKey(CommandLineOption.GLOBAL_DEPENDENCIES))
                {
                    System.out.println("Warning: Global dependencies |" +
                            this.commands.get(CommandLineOption.GLOBAL_DEPENDENCIES).toString() + "| will be" +
                            " overwritten by global dependencies |" + file.getPath() + "|.");
                }

                this.commands.put(CommandLineOption.GLOBAL_DEPENDENCIES, parser.parse());
            }
            else if (this.isOptimizationCriteria(file.getPath()))
            {
                final OptimizationCriteriaParser parser = new OptimizationCriteriaParser(file);

                if (this.commands.containsKey(CommandLineOption.OPTIMIZATION_CRITERIA))
                {
                    System.out.println("Warning: Optimization criteria |" +
                            this.commands.get(CommandLineOption.OPTIMIZATION_CRITERIA).toString() + "| will be" +
                            " overwritten by optimization criteria |" + file.getPath() + "|.");
                }

                this.commands.put(CommandLineOption.OPTIMIZATION_CRITERIA, parser.parse());
            }
        }
    }

    private boolean verifyArgs()
    {
        return this.commands.get(CommandLineOption.WORKING_DIRECTORY) != null
                && this.commands.get(CommandLineOption.BPMN_PROCESS) != null
                && this.commands.get(CommandLineOption.GLOBAL_DEPENDENCIES) != null
                && this.commands.get(CommandLineOption.OPTIMIZATION_CRITERIA) != null;
    }

    private Triple<Integer, Integer, Integer> getDefaultParametersFromAlgo(final Algorithm algorithm)
    {
        switch (algorithm)
        {
            case AGEMOEA:
            {
                return new Triple<>(AGEMOEACharacteristic.MAX_ITERATIONS, -1, AGEMOEACharacteristic.POPULATION_SIZE);
            }

            case AGEMOEAII:
            {
                return new Triple<>(AGEMOEAIICharacteristic.MAX_ITERATIONS, -1, AGEMOEAIICharacteristic.POPULATION_SIZE);
            }

            case ESPEA:
            {
                return new Triple<>(-1, ESPEACharacteristic.MAX_EVALUATIONS, ESPEACharacteristic.POPULATION_SIZE);
            }

            case NSGAII:
            {
                return new Triple<>(-1, NSGAIICharacteristic.MAX_EVALUATIONS, NSGAIICharacteristic.POPULATION_SIZE);
            }

            case RandomSearch:
            {
                return new Triple<>(-1, RandomSearchCharacteristic.MAX_EVALUATIONS, -1);
            }

            case SMSEMOA:
            {
                return new Triple<>(-1, SMSEMOACharacteristic.MAX_EVALUATIONS, SMSEMOACharacteristic.POPULATION_SIZE);
            }

            case SPEA2:
            {
                return new Triple<>(SPEA2Characteristic.MAX_ITERATIONS, -1, SPEA2Characteristic.POPULATION_SIZE);
            }

            case MOSA:
            case LOCAL_SEARCH:
            {
                return new Triple<>(-1, -1, -1);
            }

			default:
            {
                throw new IllegalStateException("Unknown algorithm: \"" + algorithm.getName() + "\".");
            }
        }
    }
}
