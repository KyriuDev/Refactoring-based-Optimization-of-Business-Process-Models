package refactoring.step_by_step;

public enum TaskElectionHeuristic
{
	RANDOM("Random"),
	LONGEST_FIRST("Longest first"),
	LONGEST_LAST("Longest last"),
	HIGHEST_COST_FIRST("Highest cost first"),
	HIGHEST_COST_LAST("Highest cost last"),
	HYBRID_BIGGEST_FIRST("Hybrid biggest first"),
	HYBRID_BIGGEST_LAST("Hybrid biggest last"),
	WELL_SUITED("Well suited");

	private final String name;

	TaskElectionHeuristic(final String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return this.name;
	}
}
