package refactoring.step_by_step;

public enum PruningMethod
{
	RANDOM("Random"),
	KEEP_BESTS("Keep bests"),
	KEEP_HALF_BEST_HALF_WORST("Half best half worst"),
	PSEUDO_SIMULATED_ANNEALING("Pseudo simulated annealing");

	private final String name;

	PruningMethod(final String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return this.name;
	}
}
