package jmetal_impl;

public enum Algorithm
{
	AGEMOEA("AGE-MOEA"),
	AGEMOEAII("AGE-MOEAII"),
	ESPEA("ESPEA"),
	LOCAL_SEARCH("LocalSearch"),
	MOSA("MOSA"),
	NSGAII("NSGAII"),
	RandomSearch("RandomSearch"),
	SMSEMOA("SMSEMOA"),
	SPEA2("SPEA2");

	private final String name;

	Algorithm(final String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return this.name;
	}
}
