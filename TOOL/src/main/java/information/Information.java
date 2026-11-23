package information;

import org.activiti.engine.impl.util.json.JSONArray;
import org.activiti.engine.impl.util.json.JSONObject;

import java.util.ArrayList;

public class Information
{
	private final Integer step;
	private final Double originalAET;
	private final Double resultingAET;
	private final ArrayList<String> deadTasks;
	private final String movedTask;


	public Information(final Integer step,
					   final Double originalAET,
					   final Double resultingAET,
					   final ArrayList<String> deadTasks,
					   final String movedTask)
	{
		this.step = step;
		this.originalAET = originalAET;
		this.resultingAET = resultingAET;
		this.deadTasks = deadTasks;
		this.movedTask = movedTask;
	}

	public Integer step()
	{
		return this.step;
	}

	public Double originalAET()
	{
		return this.originalAET;
	}

	public Double resultingAET()
	{
		return this.resultingAET;
	}

	public ArrayList<String> deadTasks()
	{
		return this.deadTasks;
	}

	public String movedTask()
	{
		return this.movedTask;
	}

	public JSONObject jsonize()
	{
		final JSONObject jsonObject = new JSONObject();
		jsonObject.put(InfoConstants.STEP, this.step);
		jsonObject.put(InfoConstants.RESULTING_AET, this.resultingAET);
		jsonObject.put(InfoConstants.MOVED_TASK, this.movedTask);
		jsonObject.put(InfoConstants.DEAD_TASKS, new JSONArray(this.deadTasks));

		if (this.originalAET != null)
		{
			jsonObject.put(InfoConstants.ORIGINAL_AET, this.originalAET);
		}

		return jsonObject;
	}

	//Static

	public static Information load(final JSONObject jsonObject)
	{
		final String stepStr = jsonObject.optString(InfoConstants.STEP);
		final String originalAETStr = jsonObject.optString(InfoConstants.ORIGINAL_AET);
		final String resultingAETStr = jsonObject.optString(InfoConstants.RESULTING_AET);
		final JSONArray deadTasksArray = jsonObject.optJSONArray(InfoConstants.DEAD_TASKS);
		final String movedTask = jsonObject.optString(InfoConstants.MOVED_TASK);

		if (stepStr.isEmpty()
			|| resultingAETStr.isEmpty()
			|| movedTask.isEmpty()
			|| deadTasksArray == null)
		{
			throw new RuntimeException("Some information are missing.");
		}

		final Integer step = Integer.parseInt(stepStr);
		final Double originalAET = originalAETStr.isEmpty() ? null : Double.parseDouble(originalAETStr);
		final Double resultingAET = Double.parseDouble(resultingAETStr);
		final ArrayList<String> deadTasks = new ArrayList<>();

		for (int i = 0; i < deadTasksArray.length(); i++)
		{
			final String deadTask = deadTasksArray.getString(i);
			deadTasks.add(deadTask);
		}

		return new Information(step, originalAET, resultingAET, deadTasks, movedTask);
	}

	//Private methods
}
