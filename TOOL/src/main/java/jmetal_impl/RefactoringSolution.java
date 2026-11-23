package jmetal_impl;

import jmetal_impl.multi_objectives.MultiObjectivesRefactoringSolution;
import jmetal_impl.single_objective.SingleObjectiveRefactoringSolution;
import org.uma.jmetal.solution.Solution;
import refactoring.step_by_step.OptimizationCriteria;
import refactoring.step_by_step.TreeNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RefactoringSolution implements Solution<TreeNode>
{
	protected final TreeNode treeNode;
	protected final double[] objectives;
	protected final double[] constraints;
	protected final Map<Object, Object> attributes;
	protected final OptimizationCriteria optimizationCriteria;

	public RefactoringSolution(final TreeNode treeNode,
							   final OptimizationCriteria optimizationCriteria,
							   final int nbObjectives)
	{
		this.treeNode = treeNode;
		this.optimizationCriteria = optimizationCriteria;
		this.objectives = new double[nbObjectives];
		this.constraints = new double[0];
		this.attributes = new HashMap<>();
	}

	@Override
	public final List<TreeNode> variables()
	{
		final ArrayList<TreeNode> variables = new ArrayList<>();
		variables.add(this.treeNode);
		return variables;
	}

	@Override
	public final double[] constraints()
	{
		return this.constraints;
	}

	@Override
	public final double[] objectives()
	{
		return this.objectives;
	}

	@Override
	public final Map<Object, Object> attributes()
	{
		return this.attributes;
	}

	@Override
	public Solution<TreeNode> copy()
	{
		throw new IllegalStateException("Method \"copy()\" must be overriden by extending classes!");
	}

	public TreeNode treeNode()
	{
		return this.treeNode;
	}
}
