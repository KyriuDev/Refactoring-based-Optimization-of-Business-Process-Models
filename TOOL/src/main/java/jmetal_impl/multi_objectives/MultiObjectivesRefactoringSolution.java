package jmetal_impl.multi_objectives;

import jmetal_impl.RefactoringSolution;
import jmetal_impl.single_objective.SingleObjectiveRefactoringSolution;
import org.uma.jmetal.solution.Solution;
import refactoring.step_by_step.OptimizationCriteria;
import refactoring.step_by_step.TreeNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultiObjectivesRefactoringSolution extends RefactoringSolution
{
	public MultiObjectivesRefactoringSolution(final TreeNode treeNode,
											  final OptimizationCriteria optimizationCriteria)
	{
		super(treeNode, optimizationCriteria, optimizationCriteria.nbCriteria());
	}

	@Override
	public final MultiObjectivesRefactoringSolution copy()
	{
		return new MultiObjectivesRefactoringSolution(
			new TreeNode(treeNode.mainCluster().copy(), treeNode.movedTask()),
			this.optimizationCriteria
		);
	}
}
