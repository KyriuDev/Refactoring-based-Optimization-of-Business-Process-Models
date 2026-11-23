package jmetal_impl;

import org.uma.jmetal.util.comparator.dominanceComparator.DominanceComparator;
import other.Utils;
import refactoring.step_by_step.OptimizationCriteria;
import refactoring.step_by_step.TreeNode;
import refactoring.step_by_step.TreePrunerV3;

public class RefactoringDominanceComparator implements DominanceComparator<RefactoringSolution>
{
	private final TreeNode rootNode;
	private final OptimizationCriteria optimizationCriteria;

	public RefactoringDominanceComparator(final TreeNode rootNode,
										  final OptimizationCriteria optimizationCriteria)
	{
		this.rootNode = rootNode;
		this.optimizationCriteria = optimizationCriteria;
	}

	@Override
	public int compare(RefactoringSolution o1,
					   RefactoringSolution o2)
	{
		final double scoreSolution1 = TreePrunerV3.computeScoreFromRoot(this.rootNode, o1.treeNode(), this.optimizationCriteria, false);
		final double scoreSolution2 = TreePrunerV3.computeScoreFromRoot(this.rootNode, o2.treeNode(), this.optimizationCriteria, false);
		//System.out.println("Score solution 1 : " + scoreSolution1);
		//System.out.println("Score solution 2 : " + scoreSolution2);

		return -Double.compare(
			Utils.round(scoreSolution1, 2),
			Utils.round(scoreSolution2, 2)
		);
	}

	@Override
	public boolean equals(Object obj)
	{
		return obj.equals(this);
	}
}
