package jmetal_impl.single_objective;

import jmetal_impl.RefactoringSolution;
import refactoring.step_by_step.OptimizationCriteria;
import refactoring.step_by_step.TreeNode;

public class SingleObjectiveRefactoringSolution extends RefactoringSolution
{
	public SingleObjectiveRefactoringSolution(final TreeNode treeNode,
											  final OptimizationCriteria optimizationCriteria)
	{
		super(treeNode, optimizationCriteria, 1);
	}

	@Override
	public final SingleObjectiveRefactoringSolution copy()
	{
		return new SingleObjectiveRefactoringSolution(
			new TreeNode(treeNode.mainCluster().copy(), treeNode.movedTask()),
			this.optimizationCriteria
		);
	}
}
