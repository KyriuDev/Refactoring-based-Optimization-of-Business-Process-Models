package jmetal_impl.multi_objectives.characteristics;

public class NSGAIICharacteristic
{
	private NSGAIICharacteristic()
	{

	}

	/*
		NSGAII starts from POPULATION_SIZE and iterates POPULATION_SIZE by POPULATION_SIZE until reaching MAX_EVALUATIONS => 50 iterations with these parameters
	 */
	public static final int MAX_EVALUATIONS = Integer.MAX_VALUE;
	public static final int POPULATION_SIZE = 100;
}
