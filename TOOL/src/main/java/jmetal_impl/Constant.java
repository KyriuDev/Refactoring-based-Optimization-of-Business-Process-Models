package jmetal_impl;

import java.util.List;

public class Constant
{
	private Constant()
	{

	}

	public static final List<Integer> ITERATIONS_TO_DUMP = List.of(
		1,
		2,
		3,
		4,
		5,
		10,
		20,
		50,
		100
	);

	public static final List<Integer> EVALUATIONS_TO_DUMP = List.of(
		100,
		200,
		300,
		400,
		500,
		1000,
		2000,
		5000,
		10000
	);
}
