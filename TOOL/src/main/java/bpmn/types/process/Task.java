package bpmn.types.process;

import org.apache.commons.math3.distribution.AbstractRealDistribution;
import resources.ResourcePool;
import resources.Resource;

public class Task extends BpmnProcessObject
{
    private Boolean autoStoreVariables;
    private ResourcePool resourceUsage;
    private AbstractRealDistribution duration;
    private Double impact;

    public Task(String id,
                BpmnProcessType type,
                final AbstractRealDistribution duration)
    {
        super(type, id);
        this.resourceUsage = new ResourcePool();
        this.duration = duration;
        this.impact = null;
    }

    public void setAutoStoreVariables(Boolean b)
    {
        this.autoStoreVariables = b;
    }

    public Boolean autoStoreVariables()
    {
        return this.autoStoreVariables;
    }

    public void addResource(final Resource resource,
                            final int nbReplicas)
    {
        this.resourceUsage.addResource(resource, nbReplicas);
    }

    public void setResourcePool(final ResourcePool resourcePool)
    {
        this.resourceUsage = resourcePool;
    }

    public ResourcePool resourceUsage()
    {
        return this.resourceUsage;
    }

    public AbstractRealDistribution duration()
    {
        return this.duration;
    }

    public void switchToClassicTask()
    {
        this.type = BpmnProcessType.TASK;
    }

    public void setDuration(final AbstractRealDistribution duration)
    {
        this.duration = duration;
    }

    public boolean hasDuration()
    {
        return this.duration.getNumericalMean() != 0;
    }

    public void setImpact(final double impact)
    {
        this.impact = impact;
    }

    public boolean hasImpact()
    {
        return this.impact != null;
    }

    public double getImpact()
    {
        return this.impact;
    }

    @Override
    public BpmnProcessObject copy()
    {
        final Task duplicate = new Task(BpmnProcessFactory.generateID(this), this.type, this.duration);
        duplicate.setAutoStoreVariables(this.autoStoreVariables);
        duplicate.setName(this.name());
        duplicate.setResourcePool(this.resourceUsage);
        duplicate.setImpact(this.impact);

        return duplicate;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();

        builder
                .append("Task \"")
                .append(id)
                .append("\" has the following flows:\n");

        for (SequenceFlow flow : this.flows().keySet())
        {
            builder
                    .append("- Flow \"")
                    .append(flow.id)
                    .append(" (")
                    .append(this.flows().get(flow).toString())
                    .append(" flow)");
        }

        return builder.toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if (o instanceof Task)
        {
            return ((Task) o).id().equals(this.id);
        }

        return false;
    }
}
