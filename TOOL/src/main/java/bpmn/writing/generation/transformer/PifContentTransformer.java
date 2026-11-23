package bpmn.writing.generation.transformer;

import bpmn.writing.generation.pif.*;
import bpmn.writing.generation.pif.Process;
import bpmn.writing.generation.util.BpmnBuilder;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.activiti.bpmn.BpmnAutoLayout;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * @author ajayk Transforms PIF to BPMN
 */
public class PifContentTransformer implements ContentTransformer
{
	static final Logger logger = LoggerFactory.getLogger(PifContentTransformer.class);

	private final File pifInput;
	private final File bpmnOutput;

	public PifContentTransformer(final File pifInput,
								 final File bpmnOutput)
	{
		this.pifInput = pifInput;
		this.bpmnOutput = bpmnOutput;
	}

	@Override
	public void transform()
	{
		FileOutputStream fileOutputStream = null;

		try
		{
			final JAXBContext jaxbContext = JAXBContext.newInstance(Process.class);
			final Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
			final Process pifProcess = (Process) jaxbUnmarshaller.unmarshal(pifInput);
			final BpmnModel model = new BpmnModel();
			final org.activiti.bpmn.model.Process bpmProcess = new org.activiti.bpmn.model.Process();
			bpmProcess.setId(pifProcess.getName());
			bpmProcess.setName(pifProcess.getName());

			final BpmnBuilder modelBuilder = new BpmnBuilder();
			final InitialEvent startNode = pifProcess.getBehaviour().getInitialNode();
			final List<JAXBElement<Object>> endNodes = pifProcess.getBehaviour().getFinalNodes();
			bpmProcess.addFlowElement(modelBuilder.createStartEvent(startNode.getId()));

			for (final JAXBElement<Object> node : endNodes)
			{
				final EndEvent event = (EndEvent) node.getValue();
				bpmProcess.addFlowElement(modelBuilder.createEndEvent(event.getId()));
			}

			final List<WorkflowNode> pifNodes = pifProcess.getBehaviour().getNodes();

			for (WorkflowNode wfNode : pifNodes)
			{
				if (wfNode instanceof Task)
				{
					final Task task = (Task) wfNode;
					bpmProcess.addFlowElement(modelBuilder.createUserTask(task.getId(), task.getId()));
				}
				else if (wfNode instanceof AndJoinGateway)
				{
					final AndJoinGateway gateway = (AndJoinGateway) wfNode;
					bpmProcess.addFlowElement(modelBuilder.createParallelGateway(gateway.getId(), gateway.getId(), null));

				}
				else if (wfNode instanceof AndSplitGateway)
				{
					final AndSplitGateway gateway = (AndSplitGateway) wfNode;
					bpmProcess.addFlowElement(modelBuilder.createParallelGateway(gateway.getId(), gateway.getId(), null));

				}
				else if (wfNode instanceof XOrJoinGateway)
				{
					final XOrJoinGateway gateway = (XOrJoinGateway) wfNode;
					bpmProcess.addFlowElement(modelBuilder.createExclusiveGateway(gateway.getId(), gateway.getId(), null));

				}
				else if (wfNode instanceof XOrSplitGateway)
				{
					final XOrSplitGateway gateway = (XOrSplitGateway) wfNode;
					bpmProcess.addFlowElement(modelBuilder.createExclusiveGateway(gateway.getId(), gateway.getId(), null));

				}
				else if (wfNode instanceof OrJoinGateway)
				{
					final OrJoinGateway gateway = (OrJoinGateway) wfNode;
					bpmProcess.addFlowElement(modelBuilder.createInclusiveGateway(gateway.getId(), gateway.getId(), null));
				}
				else if (wfNode instanceof OrSplitGateway)
				{
					final OrSplitGateway gateway = (OrSplitGateway) wfNode;
					bpmProcess.addFlowElement(
							modelBuilder.createInclusiveGateway(gateway.getId(), gateway.getId(), null));

				}
				else
				{
					if (!wfNode.getId().equals("EndEvent")
						&& !wfNode.getId().equals("StartEvent"))
					{
						logger.error("Unable to determine the PIF node instance - {}: {}", wfNode.getId(), wfNode.getClass().getName());
					}
				}
			}

			final List<SequenceFlow> flows = pifProcess.getBehaviour().getSequenceFlows();

			for (SequenceFlow flow : flows)
			{
				final org.activiti.bpmn.model.SequenceFlow bpmFlow = modelBuilder.createSequenceFlow(flow.getSource().getId(), flow.getTarget().getId());
				bpmProcess.addFlowElement(bpmFlow);
			}

			model.addProcess(bpmProcess);
			new BpmnAutoLayout(model).execute();
			byte[] bpmnXml = new BpmnXMLConverter().convertToXML(model);

			fileOutputStream = new FileOutputStream(bpmnOutput);
			IOUtils.write(bpmnXml, fileOutputStream);
			fileOutputStream.flush();
			fileOutputStream.close();
		}
		catch (JAXBException | IOException e)
		{
			if (fileOutputStream != null)
			{
				try
				{
					fileOutputStream.flush();
				}
				catch (IOException ignored)
				{

				}

				try
				{
					fileOutputStream.close();
				}
				catch (IOException ignored)
				{

				}
			}

			bpmnOutput.delete();
			logger.error("Error transforming the input", e);
			throw new RuntimeException(e);
		}
	}

	@Override
	public void generateOutput()
	{
		// TODO Auto-generated method stub
	}
}
