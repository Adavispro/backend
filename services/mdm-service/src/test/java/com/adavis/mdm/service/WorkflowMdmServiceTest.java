package com.adavis.mdm.service;

import com.adavis.mdm.model.entity.WorkflowDefinition;
import com.adavis.mdm.model.entity.WorkflowStage;
import com.adavis.mdm.model.entity.WorkflowAction;
import com.adavis.mdm.model.entity.WorkflowTransition;
import com.adavis.mdm.model.entity.WorkflowAssignment;
import com.adavis.mdm.repository.WorkflowDefinitionRepository;
import com.adavis.mdm.repository.WorkflowStageRepository;
import com.adavis.mdm.repository.WorkflowActionRepository;
import com.adavis.mdm.repository.WorkflowTransitionRepository;
import com.adavis.mdm.repository.WorkflowAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WorkflowMdmServiceTest {

    @Mock
    private WorkflowDefinitionRepository definitionRepository;

    @Mock
    private WorkflowStageRepository stageRepository;

    @Mock
    private WorkflowActionRepository actionRepository;

    @Mock
    private WorkflowTransitionRepository transitionRepository;

    @Mock
    private WorkflowAssignmentRepository assignmentRepository;

    @Mock
    private BusinessIdGeneratorService idGeneratorService;

    @InjectMocks
    private WorkflowMdmService workflowMdmService;

    private WorkflowDefinition definition;

    @BeforeEach
    void setUp() {
        definition = new WorkflowDefinition();
        definition.setWorkflowId("WFD-0001");
        definition.setWorkflowCode("IIOT_BATCH_STAGE_WORKFLOW");
        definition.setWorkflowName("IIoT Batch Stage Workflow");
        definition.setModule("IIOT");
        definition.setEntity("BATCH_STAGE");
        definition.setVersion("1.0.0");
        definition.setStatus("DRAFT");
        definition.setTenantId("TNT-0001");
    }

    @Test
    @DisplayName("Should create a draft workflow definition with generated ID")
    void testCreateWorkflowDefinition() {
        when(idGeneratorService.nextId(eq("mdm_workflow_definitions"), eq("workflowId"), eq("WFD-"), eq(4)))
                .thenReturn("WFD-0001");
        when(definitionRepository.findByWorkflowCodeAndVersion("IIOT_BATCH_STAGE_WORKFLOW", "1.0.0"))
                .thenReturn(Optional.empty());
        when(definitionRepository.save(any(WorkflowDefinition.class))).thenAnswer(i -> i.getArguments()[0]);

        WorkflowDefinition created = workflowMdmService.createDefinition(definition);

        assertNotNull(created);
        assertEquals("WFD-0001", created.getWorkflowId());
        assertEquals("DRAFT", created.getStatus());
        verify(definitionRepository, times(1)).save(any(WorkflowDefinition.class));
    }

    @Test
    @DisplayName("Should validate and activate a workflow, retiring prior active versions")
    void testActivateWorkflow() {
        WorkflowDefinition activeExisting = new WorkflowDefinition();
        activeExisting.setWorkflowId("WFD-0000");
        activeExisting.setWorkflowCode("IIOT_BATCH_STAGE_WORKFLOW");
        activeExisting.setVersion("0.9.0");
        activeExisting.setStatus("ACTIVE");
        activeExisting.setTenantId("TNT-0001");

        when(definitionRepository.findByWorkflowId("WFD-0001")).thenReturn(Optional.of(definition));
        when(definitionRepository.findFirstByWorkflowCodeAndStatus("IIOT_BATCH_STAGE_WORKFLOW", "ACTIVE"))
                .thenReturn(Optional.of(activeExisting));
        when(definitionRepository.save(any(WorkflowDefinition.class))).thenAnswer(i -> i.getArguments()[0]);

        // Mock stages and transitions for validation
        WorkflowStage stage1 = new WorkflowStage();
        stage1.setStageCode("SUBMISSION");
        stage1.setSequence(1);
        stage1.setStageType("INITIAL");
        stage1.setAssignedRole("PRODUCTION_OPERATOR");
        stage1.setAllowedActionCodes(List.of("SEND_FOR_REVIEW"));

        WorkflowStage stage2 = new WorkflowStage();
        stage2.setStageCode("APPROVAL");
        stage2.setSequence(2);
        stage2.setStageType("FINAL");
        stage2.setAssignedRole("QA_APPROVER");
        stage2.setAllowedActionCodes(List.of("APPROVE"));

        when(stageRepository.findByWorkflowCode("IIOT_BATCH_STAGE_WORKFLOW"))
                .thenReturn(List.of(stage1, stage2));

        WorkflowTransition t1 = new WorkflowTransition();
        t1.setFromStageCode("SUBMISSION");
        t1.setActionCode("SEND_FOR_REVIEW");
        t1.setToStageCode("APPROVAL");

        when(transitionRepository.findByWorkflowCode("IIOT_BATCH_STAGE_WORKFLOW"))
                .thenReturn(List.of(t1));

        WorkflowDefinition activated = workflowMdmService.activateWorkflow("WFD-0001");

        assertNotNull(activated);
        assertEquals("ACTIVE", activated.getStatus());
        assertEquals("RETIRED", activeExisting.getStatus());
        verify(definitionRepository, times(2)).save(any(WorkflowDefinition.class));
    }

    @Test
    @DisplayName("Should successfully retire an active workflow")
    void testRetireWorkflow() {
        definition.setStatus("ACTIVE");
        when(definitionRepository.findByWorkflowId("WFD-0001")).thenReturn(Optional.of(definition));
        when(definitionRepository.save(any(WorkflowDefinition.class))).thenAnswer(i -> i.getArguments()[0]);

        WorkflowDefinition retired = workflowMdmService.retireWorkflow("WFD-0001");

        assertNotNull(retired);
        assertEquals("RETIRED", retired.getStatus());
        assertFalse(retired.getIsActive());
    }
}
