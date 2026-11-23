package refactoring.step_by_step;

import bpmn.graph.Node;
import bpmn.types.process.BpmnProcessObject;
import bpmn.types.process.Task;
import other.Pair;
import other.Utils;
import refactoring.legacy.Cluster;
import resources.Resource;
import resources.ResourcePool;
import simulator.bpmn_to_model_input.DistributionUtils;

import java.util.*;


public class TaskElector
{
	private static final int NB_TASKS_TO_TAKE = -1; //0 or less means "take all tasks".
	private static final double DURATION_WEIGHT = 0.5D;
	private static final double COST_WEIGHT = 0.5D;
	private final Collection<Task> tasksAlreadyChosen;
	private final Collection<BpmnProcessObject> allTasks;
	private final Collection<BpmnProcessObject> tasks;
	private final ResourcePool availableResources;
	private final Cluster mainCluster;
	private final TaskElectionHeuristic taskElectionHeuristic;
	private final OptimizationCriteria optimizationCriteria;
	private Pair<Node, Cluster> electedTask;

	public TaskElector(final Collection<BpmnProcessObject> objects,
					   final ResourcePool availableResources,
					   final Cluster cluster,
					   final TaskElectionHeuristic taskElectionHeuristic,
					   final OptimizationCriteria optimizationCriteria)
	{
		this.availableResources = availableResources;
		this.allTasks = new ArrayList<>(objects);
		this.tasksAlreadyChosen = new ArrayList<>();
		this.tasks = this.keepTasksOnly(objects, null);
		this.mainCluster = cluster;
		this.taskElectionHeuristic = taskElectionHeuristic;
		this.optimizationCriteria = optimizationCriteria;
	}

	public TaskElector(final Collection<BpmnProcessObject> objects,
					   final Collection<Task> tasksAlreadyChosen,
					   final ResourcePool availableResources,
					   final Cluster cluster,
					   final TaskElectionHeuristic taskElectionHeuristic,
					   final OptimizationCriteria optimizationCriteria)
	{
		this.allTasks = new ArrayList<>(objects);
		this.tasksAlreadyChosen = tasksAlreadyChosen;
		this.tasks = this.keepTasksOnly(objects, tasksAlreadyChosen);
		this.availableResources = availableResources;
		this.mainCluster = cluster;
		this.taskElectionHeuristic = taskElectionHeuristic;
		this.optimizationCriteria = optimizationCriteria;

		if (NB_TASKS_TO_TAKE > 0
			&& tasksAlreadyChosen.size() >= NB_TASKS_TO_TAKE)
		{
			//We have reached the number of tasks to move, so we stop
			this.tasks.clear();
		}
	}

	public void clear()
	{
		//this.tasksAlreadyChosen.clear();
		this.allTasks.clear();
		this.tasks.clear();
	}

	public Pair<Node, Cluster> electTask()
	{
		if (this.tasks.isEmpty())
		{
			return null;
		}

		switch (this.taskElectionHeuristic)
		{
			case RANDOM:
			{
				Node task = this.randomHeuristic();
				Cluster cluster = this.getTaskCluster(task);
				this.tasksAlreadyChosen.add((Task) task.bpmnObject());

				while (this.invalidateTask(cluster))
				{
					tasks.clear();
					tasks.addAll(this.keepTasksOnly(this.allTasks, this.tasksAlreadyChosen));

					if (tasks.isEmpty()) return null;

					task = this.randomHeuristic();
					this.tasksAlreadyChosen.add((Task) task.bpmnObject());
					cluster = this.getTaskCluster(task);
				}

				return this.electedTask = new Pair<>(task, cluster);
			}

			case LONGEST_FIRST:
			{
				Node task = this.longestFirstHeuristic();
				Cluster cluster = this.getTaskCluster(task);
				tasksAlreadyChosen.add((Task) task.bpmnObject());

				while (this.invalidateTask(cluster))
				{
					tasks.clear();
					tasks.addAll(this.keepTasksOnly(this.allTasks, this.tasksAlreadyChosen));

					if (tasks.isEmpty()) return null;

					task = this.longestFirstHeuristic();
					this.tasksAlreadyChosen.add((Task) task.bpmnObject());
					cluster = this.getTaskCluster(task);
				}

				return this.electedTask = new Pair<>(task, cluster);
			}

			case LONGEST_LAST:
			{
				Node task = this.longestLastHeuristic();
				Cluster cluster = this.getTaskCluster(task);
				tasksAlreadyChosen.add((Task) task.bpmnObject());

				while (this.invalidateTask(cluster))
				{
					tasks.clear();
					tasks.addAll(this.keepTasksOnly(this.allTasks, this.tasksAlreadyChosen));

					if (tasks.isEmpty()) return null;

					task = this.longestLastHeuristic();
					this.tasksAlreadyChosen.add((Task) task.bpmnObject());
					cluster = this.getTaskCluster(task);
				}

				return this.electedTask = new Pair<>(task, cluster);
			}

			case HIGHEST_COST_FIRST:
			{
				Node task = this.highCostFirstHeuristic();
				Cluster cluster = this.getTaskCluster(task);
				tasksAlreadyChosen.add((Task) task.bpmnObject());

				while (this.invalidateTask(cluster))
				{
					tasks.clear();
					tasks.addAll(this.keepTasksOnly(this.allTasks, this.tasksAlreadyChosen));

					if (tasks.isEmpty()) return null;

					task = this.highCostFirstHeuristic();
					this.tasksAlreadyChosen.add((Task) task.bpmnObject());
					cluster = this.getTaskCluster(task);
				}

				return this.electedTask = new Pair<>(task, cluster);
			}

			case HIGHEST_COST_LAST:
			{
				Node task = this.highCostLastHeuristic();
				Cluster cluster = this.getTaskCluster(task);
				tasksAlreadyChosen.add((Task) task.bpmnObject());

				while (this.invalidateTask(cluster))
				{
					tasks.clear();
					tasks.addAll(this.keepTasksOnly(this.allTasks, this.tasksAlreadyChosen));

					if (tasks.isEmpty()) return null;

					task = this.highCostLastHeuristic();
					this.tasksAlreadyChosen.add((Task) task.bpmnObject());
					cluster = this.getTaskCluster(task);
				}

				return this.electedTask = new Pair<>(task, cluster);
			}

			case HYBRID_BIGGEST_FIRST:
			{
				Node task = this.hybridBiggestFirstHeuristic();
				Cluster cluster = this.getTaskCluster(task);
				tasksAlreadyChosen.add((Task) task.bpmnObject());

				while (this.invalidateTask(cluster))
				{
					tasks.clear();
					tasks.addAll(this.keepTasksOnly(this.allTasks, this.tasksAlreadyChosen));

					if (tasks.isEmpty()) return null;

					task = this.hybridBiggestFirstHeuristic();
					this.tasksAlreadyChosen.add((Task) task.bpmnObject());
					cluster = this.getTaskCluster(task);
				}

				return this.electedTask = new Pair<>(task, cluster);
			}

			case HYBRID_BIGGEST_LAST:
			{
				Node task = this.hybridBiggestLastHeuristic();
				Cluster cluster = this.getTaskCluster(task);
				tasksAlreadyChosen.add((Task) task.bpmnObject());

				while (this.invalidateTask(cluster))
				{
					tasks.clear();
					tasks.addAll(this.keepTasksOnly(this.allTasks, this.tasksAlreadyChosen));

					if (tasks.isEmpty()) return null;

					task = this.hybridBiggestLastHeuristic();
					this.tasksAlreadyChosen.add((Task) task.bpmnObject());
					cluster = this.getTaskCluster(task);
				}

				return this.electedTask = new Pair<>(task, cluster);
			}

			case WELL_SUITED:
			{
				Node task = this.hybridBiggestLastHeuristic();
				Cluster cluster = this.getTaskCluster(task);
				tasksAlreadyChosen.add((Task) task.bpmnObject());

				while (this.invalidateTask(cluster))
				{
					tasks.clear();
					tasks.addAll(this.keepTasksOnly(this.allTasks, this.tasksAlreadyChosen));

					if (tasks.isEmpty()) return null;

					task = this.wellSuitedHeuristic();
					this.tasksAlreadyChosen.add((Task) task.bpmnObject());
					cluster = this.getTaskCluster(task);
				}

				return this.electedTask = new Pair<>(task, cluster);
			}

			default: throw new IllegalStateException("Heuristic |" + this.taskElectionHeuristic + "| was not recognized.");
		}
	}

	public Pair<Node, Cluster> electedTask()
	{
		return this.electedTask;
	}

	//Private methods

	private Node randomHeuristic()
	{
		final Random random = new Random();
		final int randomInt = random.nextInt();
		final int taskIndex = Math.abs(randomInt) % this.tasks.size();
		final Iterator<BpmnProcessObject> tasksIterator = this.tasks.iterator();
		int i = 0;

		while (tasksIterator.hasNext())
		{
			final BpmnProcessObject task = tasksIterator.next();

			if (i++ == taskIndex)
			{
				return new Node(task);
			}
		}

		throw new IllegalStateException();
	}

	private Node longestFirstHeuristic()
	{
		int longestDuration = -1;
		BpmnProcessObject longestTask = null;

		for (BpmnProcessObject task : this.tasks)
		{
			final int taskDuration = DistributionUtils.averageIntegerValue(((Task) task).duration());

			if (taskDuration > longestDuration)
			{
				longestDuration = taskDuration;
				longestTask = task;
			}
		}

		if (longestTask == null) throw new IllegalStateException();

		return new Node(longestTask);
	}

	private Node longestLastHeuristic()
	{
		int shortestDuration = Integer.MAX_VALUE;
		BpmnProcessObject shortestTask = null;

		for (BpmnProcessObject task : this.tasks)
		{
			final int taskDuration = DistributionUtils.averageIntegerValue(((Task) task).duration());

			if (taskDuration < shortestDuration)
			{
				shortestDuration = taskDuration;
				shortestTask = task;
			}
		}

		if (shortestTask == null) throw new IllegalStateException();

		return new Node(shortestTask);
	}

	private Node highCostFirstHeuristic()
	{
		double highestCost = -1d;
		BpmnProcessObject highestCostTask = null;

		for (BpmnProcessObject task : this.tasks)
		{
			final double cost = this.computeTaskCost(task);

			if (cost > highestCost)
			{
				highestCost = cost;
				highestCostTask = task;
			}
		}

		if (highestCostTask == null) throw new IllegalStateException();

		return new Node(highestCostTask);
	}

	private Node highCostLastHeuristic()
	{
		double shortestCost = Double.MAX_VALUE;
		BpmnProcessObject shortestCostTask = null;

		for (BpmnProcessObject task : this.tasks)
		{
			final double cost = this.computeTaskCost(task);

			if (cost < shortestCost)
			{
				shortestCost = cost;
				shortestCostTask = task;
			}
		}

		if (shortestCostTask == null) throw new IllegalStateException();

		return new Node(shortestCostTask);
	}

	private Node hybridBiggestFirstHeuristic()
	{
		double biggestValue = -1;
		BpmnProcessObject biggestValueTask = null;

		for (BpmnProcessObject task : this.tasks)
		{
			final double cost = this.computeTaskCost(task);
			final int duration = DistributionUtils.averageIntegerValue(((Task) task).duration());
			final double computedValue = COST_WEIGHT * cost + DURATION_WEIGHT * (double) duration;

			if (computedValue > biggestValue)
			{
				biggestValue = computedValue;
				biggestValueTask = task;
			}
		}

		if (biggestValueTask == null) throw new IllegalStateException();

		return new Node(biggestValueTask);
	}

	private Node hybridBiggestLastHeuristic()
	{
		double smallestValue = Double.MAX_VALUE;
		BpmnProcessObject smallestValueTask = null;

		for (BpmnProcessObject task : this.tasks)
		{
			final double cost = this.computeTaskCost(task);
			final int duration = DistributionUtils.averageIntegerValue(((Task) task).duration());
			final double computedValue = COST_WEIGHT * cost + DURATION_WEIGHT * (double) duration;

			if (computedValue < smallestValue)
			{
				smallestValue = computedValue;
				smallestValueTask = task;
			}
		}

		if (smallestValueTask == null) throw new IllegalStateException();

		return new Node(smallestValueTask);
	}

	/**
	 * This heuristic is based on the optimisation criteria given by the
	 * user.
	 * They are used to compute the next task having the least impact on
	 * the process, based on the intuition that, the less impact a task
	 * has on the process, the more interesting it is to move it before
	 * the others.
	 * The idea is thus to move tasks with high impact later than tasks
	 * with small impact.
	 *
	 * @return the next task (node) to move
	 */
	private Node wellSuitedHeuristic()
	{
		final BpmnProcessObject firstTask = this.tasks.iterator().next();
		final Task smallestImpactTask;

		if (false)
		{
			final Integer[] costMinMax = {Integer.MAX_VALUE, -1};
			final Integer[] durationMinMax = {Integer.MAX_VALUE, -1};
			final HashMap<String, Integer[]> resourceUsageMinMax = new HashMap<>();

			//Setup min/max values for normalisation
			for (BpmnProcessObject object : this.tasks)
			{
				final Task task = (Task) object;

				if (this.optimizationCriteria.costOptimizationWeight() != 0d)
				{
					//We should consider the cost
					final int taskCost = this.computeTaskCost(task);

					if (taskCost < costMinMax[0])
					{
						costMinMax[0] = taskCost;
					}

					if (taskCost > costMinMax[1])
					{
						costMinMax[1] = taskCost;
					}
				}

				if (this.optimizationCriteria.timeOptimizationWeight() != 0d)
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
					if (this.optimizationCriteria.getResourcesToOptimizeAndTargetUsageAndWeight().containsKey(resource.name()))
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
			double smallestImpact = Double.MAX_VALUE;
			Task smallestImpactTaskElse = null;

			for (BpmnProcessObject object : this.tasks)
			{
				final Task task = (Task) object;
				double currentImpact = 0d;
				final int duration = DistributionUtils.averageIntegerValue(task.duration());
				final int cost = this.computeTaskCost(task);

				if (costMinMax[1] != -1)
				{
					//Consider cost
					final double normalisedCost = (double) (cost - costMinMax[0]) / (double) (costMinMax[1] - costMinMax[0]);
					currentImpact += normalisedCost * this.optimizationCriteria.costOptimizationWeight();
					System.out.println("Normalised cost: " + normalisedCost);
				}

				if (durationMinMax[1] != -1)
				{
					//Consider duration
					final double normalisedDuration = (double) (duration - durationMinMax[0]) / (double) (durationMinMax[1] - durationMinMax[0]);
					currentImpact += normalisedDuration * this.optimizationCriteria.timeOptimizationWeight();
					System.out.println("Normalised duration: " + normalisedDuration);
				}

				for (Resource resource : task.resourceUsage().resources())
				{
					if (this.optimizationCriteria.getResourcesToOptimizeAndTargetUsageAndWeight().containsKey(resource.name()))
					{
						final Pair<Integer, Double> targetUsageAndWeight = this.optimizationCriteria.getResourcesToOptimizeAndTargetUsageAndWeight().get(resource.name());
						final double normalisedUsage = (double) (task.resourceUsage().getUsageOf(resource) - resourceUsageMinMax.get(resource.name())[0])
								/ (double) (resourceUsageMinMax.get(resource.name())[1] - resourceUsageMinMax.get(resource.name())[0]);
						System.out.println("Normalised " + resource.name() + " usage: " + normalisedUsage);
						final double weightedNormalisedUsage = normalisedUsage * targetUsageAndWeight.second();

						currentImpact += weightedNormalisedUsage;
					}
				}

				System.out.println("Task impact: " + currentImpact);

				if (currentImpact < smallestImpact)
				{
					smallestImpact = currentImpact;
					smallestImpactTaskElse = task;
				}

				task.setImpact(currentImpact);
			}

			smallestImpactTask = smallestImpactTaskElse;
		}
		else
		{
			double smallestImpact = Double.MAX_VALUE;
			Task smallestImpactTaskIf = null;

			for (BpmnProcessObject object : this.tasks)
			{
				final Task task = (Task) object;

				if (!task.hasImpact()) throw new IllegalStateException();

				if (task.getImpact() < smallestImpact)
				{
					smallestImpact = task.getImpact();
					smallestImpactTaskIf = task;
				}
			}

			smallestImpactTask = smallestImpactTaskIf;
		}

		return new Node(smallestImpactTask);
	}

	private int computeTaskCost(final BpmnProcessObject object)
	{
		final Task task = (Task) object;

		return this.computeTaskCost(task);
	}

	private int computeTaskCost(final Task task)
	{
		int cost = 0;

		for (Resource resource : task.resourceUsage().resources())
		{
			cost += task.resourceUsage().getUsageOf(resource) * resource.cost();
		}

		return cost;
	}

	private ArrayList<BpmnProcessObject> keepTasksOnly(final Collection<BpmnProcessObject> objects,
													   final Collection<Task> tasksAlreadyChosen)
	{
		final ArrayList<BpmnProcessObject> tasks = new ArrayList<>();

		for (BpmnProcessObject object : objects)
		{
			if (object instanceof Task)
			{
				tasks.add(object);
			}
		}

		if (tasksAlreadyChosen != null)
		{
			tasks.removeAll(tasksAlreadyChosen);
		}

		return tasks;
	}

	private Cluster getTaskCluster(final Node task)
	{
		final Cluster taskCluster = Utils.getTaskCluster(this.mainCluster, task);

		if (taskCluster == null)
		{
			System.out.println(this.mainCluster.stringify(0));
			throw new IllegalStateException("Task |" + task.bpmnObject().id() + "| was not found in any cluster.");
		}

		return taskCluster;
	}

	private boolean invalidateTask(final Cluster taskCluster)
	{
		return taskCluster.elements().size() <= 1;
	}
}
