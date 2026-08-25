package com.adavis.iiot.service;

import com.adavis.common.exception.BusinessException;
import com.adavis.common.exception.ForbiddenException;
import com.adavis.common.exception.UnauthorizedException;
import com.adavis.iiot.model.WorkflowActionHistory;
import com.adavis.iiot.model.WorkflowInstance;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DynamicBatchWorkflowExecutionTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private NotificationService notificationService;

    @Mock
    private BatchPdfGeneratorService batchPdfGeneratorService;

    @InjectMocks
    private DynamicWorkflowEngine workflowEngine;

    private Document activeDefinition;
    private Document submissionStage;
    private Document reviewStage;
    private Document approvalStage;
    private Document sendForReviewAction;
    private Document submitForReviewAction;
    private Document approveAction;
    private Document transition1;
    private Document transition2;
    private String validPasswordHash;

    @BeforeEach
    void setUp() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        validPasswordHash = encoder.encode("Password123!");

        activeDefinition = new Document("workflowCode", "IIOT_BATCH_STAGE_WORKFLOW")
                .append("version", "1.0.0")
                .append("status", "ACTIVE")
                .append("stageCodes", List.of("SUBMISSION", "REVIEW", "APPROVAL"));

        submissionStage = new Document("workflowCode", "IIOT_BATCH_STAGE_WORKFLOW")
                .append("stageCode", "SUBMISSION")
                .append("sequence", 1)
                .append("stageType", "INITIAL")
                .append("assignedRole", "PRODUCTION_OPERATOR")
                .append("allowedActionCodes", List.of("SUBMIT_FOR_REVIEW", "SEND_FOR_REVIEW"));

        reviewStage = new Document("workflowCode", "IIOT_BATCH_STAGE_WORKFLOW")
                .append("stageCode", "REVIEW")
                .append("sequence", 2)
                .append("stageType", "INTERMEDIATE")
                .append("assignedRole", "PRODUCTION_REVIEWER")
                .append("allowedActionCodes", List.of("SEND_FOR_APPROVAL", "REJECT"));

        approvalStage = new Document("workflowCode", "IIOT_BATCH_STAGE_WORKFLOW")
                .append("stageCode", "APPROVAL")
                .append("sequence", 3)
                .append("stageType", "FINAL")
                .append("assignedRole", "QA_APPROVER")
                .append("allowedActionCodes", List.of("APPROVE", "REQUEST_ADDITIONAL_INFO", "DEFER", "REJECT"));

        sendForReviewAction = new Document("actionCode", "SEND_FOR_REVIEW")
                .append("displayName", "Send for Review")
                .append("actionType", "SUBMIT")
                .append("applicableRole", "PRODUCTION_OPERATOR")
                .append("requiresEsign", true)
                .append("requiresComment", false)
                .append("requiresJustification", false);

        submitForReviewAction = new Document("actionCode", "SUBMIT_FOR_REVIEW")
                .append("displayName", "Submit for Review")
                .append("actionType", "SUBMIT")
                .append("applicableRole", "PRODUCTION_OPERATOR")
                .append("requiresEsign", true)
                .append("requiresComment", false)
                .append("requiresJustification", false);

        approveAction = new Document("actionCode", "APPROVE")
                .append("displayName", "Approve")
                .append("actionType", "APPROVE")
                .append("applicableRole", "QA_APPROVER")
                .append("requiresEsign", true)
                .append("requiresComment", true)
                .append("requiresJustification", false);

        transition1 = new Document("workflowCode", "IIOT_BATCH_STAGE_WORKFLOW")
                .append("fromStageCode", "SUBMISSION")
                .append("actionCode", "SEND_FOR_REVIEW")
                .append("toStageCode", "REVIEW")
                .append("resultingStatus", "UNDER_REVIEW");

        transition2 = new Document("workflowCode", "IIOT_BATCH_STAGE_WORKFLOW")
                .append("fromStageCode", "APPROVAL")
                .append("actionCode", "APPROVE")
                .append("toStageCode", "COMPLETED")
                .append("resultingStatus", "APPROVED");

        when(batchPdfGeneratorService.generateAndStoreBatchPdf(any(), any(), any(), any(), any()))
                .thenReturn(BatchPdfGeneratorService.PdfGenerationResult.builder()
                        .documentId("DOC-TEST-001")
                        .fileName("Batch_Dossier_TEST.pdf")
                        .storagePath("TNT-0001/PLNT-0001/DOC-TEST-001.pdf")
                        .sha256Checksum("abcdef1234567890")
                        .generatedAt(java.time.Instant.now())
                        .fileSizeBytes(2048)
                        .build());
    }

    @Test
    @DisplayName("Should evaluate allowed actions for an operator at submission stage")
    void testGetAllowedActionsForOperator() {
        WorkflowInstance instance = WorkflowInstance.builder()
                .instanceId("WFI-0001")
                .workflowCode("IIOT_BATCH_STAGE_WORKFLOW")
                .workflowVersion("1.0.0")
                .currentStageCode("SUBMISSION")
                .currentStatus("PENDING")
                .initiatedBy("USR-0001")
                .build();

        when(mongoTemplate.findOne(any(Query.class), eq(WorkflowInstance.class), eq("iiot_workflow_instances")))
                .thenReturn(instance);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_definitions")))
                .thenReturn(activeDefinition);

        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_stages")))
                .thenReturn(submissionStage);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_transitions")))
                .thenReturn(transition1);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_actions")))
                .thenReturn(sendForReviewAction);

        List<DynamicWorkflowEngine.AllowedActionDto> actions = workflowEngine.getAllowedActions(
                "USR-0001", "PRODUCTION_OPERATOR", "TNT-0001", "PLNT-0001",
                "B-2026-001", "01 of 05", "G5RMG"
        );

        assertNotNull(actions);
        assertEquals(1, actions.size());
        assertEquals("SUBMIT_FOR_REVIEW", actions.get(0).getActionCode());
        assertEquals("UNDER_REVIEW", actions.get(0).getResultingStatus());
    }

    @Test
    @DisplayName("Should enforce Segregation of Duties: Submitter cannot approve during Final QA Approval")
    void testSegregationOfDutiesViolation() {
        WorkflowInstance instance = WorkflowInstance.builder()
                .instanceId("WFI-0001")
                .workflowCode("IIOT_BATCH_STAGE_WORKFLOW")
                .workflowVersion("1.0.0")
                .currentStageCode("APPROVAL")
                .currentStatus("REVIEWER_REVIEWED")
                .initiatedBy("USR-0001")
                .build();

        when(mongoTemplate.findOne(any(Query.class), eq(WorkflowInstance.class), eq("iiot_workflow_instances")))
                .thenReturn(instance);
        Document summaryDoc = new Document("batchNo", "B-2026-001")
                .append("lotNo", "01 of 05")
                .append("equipmentCode", "G5RMG")
                .append("tenantId", "TNT-0001")
                .append("plantId", "PLNT-0001")
                .append("stages", List.of(new Document("equipmentCode", "G5RMG")
                        .append("status", "REVIEWER_REVIEWED")
                        .append("operatorName", "USR-0001")));
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_batch_summary")))
                .thenReturn(summaryDoc);

        Document authCreds = new Document("userId", "USR-0001")
                .append("passwordHash", validPasswordHash);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("auth_credentials")))
                .thenReturn(authCreds);

        WorkflowActionHistory historyRecord = WorkflowActionHistory.builder()
                .performedBy("USR-0001")
                .actionCode("SEND_FOR_REVIEW")
                .fromStageCode("SUBMISSION")
                .performerRole("PRODUCTION_OPERATOR")
                .build();
        when(mongoTemplate.find(any(Query.class), eq(WorkflowActionHistory.class), eq("iiot_workflow_action_history")))
                .thenReturn(List.of(historyRecord));

        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_stages")))
                .thenReturn(approvalStage);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_transitions")))
                .thenReturn(transition2);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_actions")))
                .thenReturn(approveAction);

        DynamicWorkflowEngine.ActionExecutionRequest request = DynamicWorkflowEngine.ActionExecutionRequest.builder()
                .userId("USR-0001")
                .userRole("QA_APPROVER")
                .batchNo("B-2026-001")
                .lotNo("01 of 05")
                .equipmentCode("G5RMG")
                .actionCode("APPROVE")
                .password("Password123!")
                .comments("QA Approval")
                .tenantId("TNT-0001")
                .plantId("PLNT-0001")
                .build();

        assertThrows(ForbiddenException.class, () -> {
            workflowEngine.executeAction(request);
        });
    }

    @Test
    @DisplayName("SUPER_ADMIN should receive empty list for allowed operational workflow actions")
    void testSuperAdminAllowedActionsIsEmpty() {
        WorkflowInstance instance = WorkflowInstance.builder()
                .instanceId("WFI-0001")
                .workflowCode("IIOT_BATCH_STAGE_WORKFLOW")
                .workflowVersion("1.0.0")
                .currentStageCode("SUBMISSION")
                .currentStatus("PENDING")
                .initiatedBy("OPERATOR_01")
                .build();

        when(mongoTemplate.findOne(any(Query.class), eq(WorkflowInstance.class), eq("iiot_workflow_instances")))
                .thenReturn(instance);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_definitions")))
                .thenReturn(activeDefinition);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_stages")))
                .thenReturn(submissionStage);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_transitions")))
                .thenReturn(transition1);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_actions")))
                .thenReturn(sendForReviewAction);

        List<DynamicWorkflowEngine.AllowedActionDto> actions = workflowEngine.getAllowedActions(
                "SUPER_ADMIN", "SUPER_ADMIN", "TNT-0001", "PLNT-0001",
                "B-2026-001", "01 of 05", "G5RMG"
        );

        assertNotNull(actions);
        assertTrue(actions.isEmpty(), "SUPER_ADMIN must not receive operational actions");
    }

    @Test
    @DisplayName("SUPER_ADMIN operational mutation execution must throw ForbiddenException (HTTP 403)")
    void testSuperAdminExecuteActionThrowsForbiddenException() {
        WorkflowInstance instance = WorkflowInstance.builder()
                .instanceId("WFI-0001")
                .workflowCode("IIOT_BATCH_STAGE_WORKFLOW")
                .workflowVersion("1.0.0")
                .currentStageCode("SUBMISSION")
                .currentStatus("PENDING")
                .initiatedBy("OPERATOR_01")
                .build();

        when(mongoTemplate.findOne(any(Query.class), eq(WorkflowInstance.class), eq("iiot_workflow_instances")))
                .thenReturn(instance);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_stages")))
                .thenReturn(submissionStage);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_transitions")))
                .thenReturn(transition1);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_actions")))
                .thenReturn(sendForReviewAction);

        DynamicWorkflowEngine.ActionExecutionRequest request = DynamicWorkflowEngine.ActionExecutionRequest.builder()
                .userId("SUPER_ADMIN")
                .userRole("SUPER_ADMIN")
                .batchNo("B-2026-001")
                .lotNo("01 of 05")
                .equipmentCode("G5RMG")
                .actionCode("SEND_FOR_REVIEW")
                .password("Admin123!")
                .comments("Super Admin direct mutation attempt")
                .tenantId("TNT-0001")
                .plantId("PLNT-0001")
                .build();

        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> {
            workflowEngine.executeAction(request);
        });

        assertEquals("FORBIDDEN", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("not permitted to execute action"));
    }

    @Test
    @DisplayName("Should abort execution on invalid electronic signature password")
    void testInvalidEsignaturePassword() {
        WorkflowInstance instance = WorkflowInstance.builder()
                .instanceId("WFI-0001")
                .workflowCode("IIOT_BATCH_STAGE_WORKFLOW")
                .workflowVersion("1.0.0")
                .currentStageCode("APPROVAL")
                .currentStatus("REVIEWER_REVIEWED")
                .initiatedBy("USR-0002")
                .build();

        when(mongoTemplate.findOne(any(Query.class), eq(WorkflowInstance.class), eq("iiot_workflow_instances")))
                .thenReturn(instance);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_stages")))
                .thenReturn(approvalStage);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_transitions")))
                .thenReturn(transition2);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_actions")))
                .thenReturn(approveAction);

        // Invalid BCrypt hash or wrong password
        Document authCreds = new Document("userId", "USR-0003")
                .append("passwordHash", validPasswordHash);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("auth_credentials")))
                .thenReturn(authCreds);

        DynamicWorkflowEngine.ActionExecutionRequest request = DynamicWorkflowEngine.ActionExecutionRequest.builder()
                .userId("USR-0003")
                .userRole("QA_APPROVER")
                .batchNo("B-2026-001")
                .lotNo("01 of 05")
                .equipmentCode("G5RMG")
                .actionCode("APPROVE")
                .password("WrongPasswordGivenHere")
                .comments("QA Approval")
                .tenantId("TNT-0001")
                .plantId("PLNT-0001")
                .build();

        assertThrows(UnauthorizedException.class, () -> {
            workflowEngine.executeAction(request);
        });

        // Ensure database state update was NOT called
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq("iiot_batch_summary"));
    }

    @Test
    @DisplayName("Should execute canonical DEFER action successfully and update status to DEFERRED")
    void testExecuteDeferAction() {
        WorkflowInstance instance = WorkflowInstance.builder()
                .instanceId("WFI-0002")
                .workflowCode("IIOT_BATCH_STAGE_WORKFLOW")
                .workflowVersion("1.0.0")
                .currentStageCode("APPROVAL")
                .currentStatus("REVIEWER_REVIEWED")
                .initiatedBy("OPERATOR_01")
                .build();

        Document deferAction = new Document("actionCode", "DEFER")
                .append("displayName", "Defer")
                .append("actionType", "DEFER")
                .append("applicableRole", "QA_APPROVER")
                .append("requiresEsign", true)
                .append("requiresComment", true)
                .append("requiresJustification", true);

        Document deferTransition = new Document("workflowCode", "IIOT_BATCH_STAGE_WORKFLOW")
                .append("fromStageCode", "APPROVAL")
                .append("actionCode", "DEFER")
                .append("toStageCode", "APPROVAL")
                .append("resultingStatus", "DEFERRED");

        Document batchSummary = new Document("batchNo", "B-2026-002")
                .append("lotNo", "01 of 05")
                .append("stages", List.of(new Document("equipmentCode", "G5RMG")
                        .append("approval", new Document("status", "REVIEWER_REVIEWED"))));

        Document authCreds = new Document("userId", "QA_APPROVER_01")
                .append("passwordHash", validPasswordHash);

        when(mongoTemplate.findOne(any(Query.class), eq(WorkflowInstance.class), eq("iiot_workflow_instances")))
                .thenReturn(instance);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("auth_credentials")))
                .thenReturn(authCreds);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_stages")))
                .thenReturn(approvalStage);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_transitions")))
                .thenReturn(deferTransition);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_actions")))
                .thenReturn(deferAction);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_batch_summary")))
                .thenReturn(batchSummary);
        when(mongoTemplate.save(any(Document.class), eq("iiot_batch_summary")))
                .thenAnswer(inv -> inv.getArgument(0));

        DynamicWorkflowEngine.ActionExecutionRequest request = DynamicWorkflowEngine.ActionExecutionRequest.builder()
                .userId("QA_APPROVER_01")
                .userRole("QA_APPROVER")
                .batchNo("B-2026-002")
                .lotNo("01 of 05")
                .equipmentCode("G5RMG")
                .actionCode("DEFER")
                .password("Password123!")
                .justification("Awaiting lab OOS investigation report before release.")
                .tenantId("TNT-0001")
                .plantId("PLNT-0001")
                .build();

        var result = workflowEngine.executeAction(request);
        assertNotNull(result);

        @SuppressWarnings("unchecked")
        List<Object> stages = (List<Object>) result.get("stages");
        assertNotNull(stages);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> stage0 = (java.util.Map<String, Object>) stages.get(0);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> app = (java.util.Map<String, Object>) stage0.get("approval");
        assertEquals("DEFERRED", app.get("status"));
        assertEquals("QA_APPROVER_01", app.get("deferredBy"));
        assertEquals("Awaiting lab OOS investigation report before release.", app.get("deferralReason"));
    }

    @Test
    @DisplayName("Should execute canonical REQUEST_ADDITIONAL_INFO and return to operator")
    void testExecuteRequestAdditionalInfo() {
        WorkflowInstance instance = WorkflowInstance.builder()
                .instanceId("WFI-0003")
                .workflowCode("IIOT_BATCH_STAGE_WORKFLOW")
                .workflowVersion("1.0.0")
                .currentStageCode("APPROVAL")
                .currentStatus("REVIEWER_REVIEWED")
                .initiatedBy("OPERATOR_01")
                .build();

        Document reqInfoAction = new Document("actionCode", "REQUEST_ADDITIONAL_INFO")
                .append("displayName", "Request Additional Information")
                .append("actionType", "RETURN")
                .append("applicableRole", "QA_APPROVER")
                .append("requiresEsign", true)
                .append("requiresComment", true)
                .append("requiresAdditionalInfo", true);

        Document reqInfoTransition = new Document("workflowCode", "IIOT_BATCH_STAGE_WORKFLOW")
                .append("fromStageCode", "APPROVAL")
                .append("actionCode", "REQUEST_ADDITIONAL_INFO")
                .append("toStageCode", "SUBMISSION")
                .append("resultingStatus", "RETURNED_TO_OPERATOR")
                .append("returnStageCode", "SUBMISSION");

        Document batchSummary = new Document("batchNo", "B-2026-003")
                .append("lotNo", "01 of 05")
                .append("stages", List.of(new Document("equipmentCode", "G5RMG")
                        .append("approval", new Document("status", "REVIEWER_REVIEWED"))));

        Document authCreds = new Document("userId", "QA_APPROVER_01")
                .append("passwordHash", validPasswordHash);

        when(mongoTemplate.findOne(any(Query.class), eq(WorkflowInstance.class), eq("iiot_workflow_instances")))
                .thenReturn(instance);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("auth_credentials")))
                .thenReturn(authCreds);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_stages")))
                .thenReturn(approvalStage);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_transitions")))
                .thenReturn(reqInfoTransition);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_actions")))
                .thenReturn(reqInfoAction);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_batch_summary")))
                .thenReturn(batchSummary);
        when(mongoTemplate.save(any(Document.class), eq("iiot_batch_summary")))
                .thenAnswer(inv -> inv.getArgument(0));

        DynamicWorkflowEngine.ActionExecutionRequest request = DynamicWorkflowEngine.ActionExecutionRequest.builder()
                .userId("QA_APPROVER_01")
                .userRole("QA_APPROVER")
                .batchNo("B-2026-003")
                .lotNo("01 of 05")
                .equipmentCode("G5RMG")
                .actionCode("REQUEST_ADDITIONAL_INFO")
                .password("Password123!")
                .additionalInformation("Please provide clarification on granulation moisture spike observed at 10:15.")
                .tenantId("TNT-0001")
                .plantId("PLNT-0001")
                .build();

        var result = workflowEngine.executeAction(request);
        assertNotNull(result);

        @SuppressWarnings("unchecked")
        List<Object> stages = (List<Object>) result.get("stages");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> stage0 = (java.util.Map<String, Object>) stages.get(0);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> app = (java.util.Map<String, Object>) stage0.get("approval");
        assertEquals("RETURNED_TO_OPERATOR", app.get("status"));
        assertEquals("QA_APPROVER_01", app.get("additionalInfoRequestedBy"));
        assertEquals("Please provide clarification on granulation moisture spike observed at 10:15.", app.get("additionalInformation"));
    }

    @Test
    @DisplayName("Should prevent concurrent duplicate execution on already approved batch stage")
    void testConcurrencyProtection() {
        WorkflowInstance instance = WorkflowInstance.builder()
                .instanceId("WFI-0004")
                .workflowCode("IIOT_BATCH_STAGE_WORKFLOW")
                .workflowVersion("1.0.0")
                .currentStageCode("APPROVAL")
                .currentStatus("REVIEWER_REVIEWED")
                .initiatedBy("OPERATOR_01")
                .build();

        Document batchSummary = new Document("batchNo", "B-2026-004")
                .append("lotNo", "01 of 05")
                .append("stages", List.of(new Document("equipmentCode", "G5RMG")
                        .append("approval", new Document("status", "APPROVED"))));

        Document authCreds = new Document("userId", "QA_APPROVER_02")
                .append("passwordHash", validPasswordHash);

        when(mongoTemplate.findOne(any(Query.class), eq(WorkflowInstance.class), eq("iiot_workflow_instances")))
                .thenReturn(instance);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("auth_credentials")))
                .thenReturn(authCreds);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_stages")))
                .thenReturn(approvalStage);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_transitions")))
                .thenReturn(transition2);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_actions")))
                .thenReturn(approveAction);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_batch_summary")))
                .thenReturn(batchSummary);

        DynamicWorkflowEngine.ActionExecutionRequest request = DynamicWorkflowEngine.ActionExecutionRequest.builder()
                .userId("QA_APPROVER_02")
                .userRole("QA_APPROVER")
                .batchNo("B-2026-004")
                .lotNo("01 of 05")
                .equipmentCode("G5RMG")
                .actionCode("APPROVE")
                .password("Password123!")
                .comments("Second approval attempt")
                .tenantId("TNT-0001")
                .plantId("PLNT-0001")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            workflowEngine.executeAction(request);
        });

        assertEquals("CONCURRENT_MODIFICATION", ex.getErrorCode());
    }

    @Test
    @DisplayName("Action deduplication resolves canonical SUBMIT_FOR_REVIEW over legacy alias SEND_FOR_REVIEW")
    void testActionDeduplicationCanonicalOverLegacy() {
        DynamicWorkflowEngine.AllowedActionDto canonicalAction = DynamicWorkflowEngine.AllowedActionDto.builder()
                .actionCode("SUBMIT_FOR_REVIEW")
                .actionName("Submit for Review")
                .displayName("Submit for Review")
                .fromStageCode("SUBMISSION")
                .toStageCode("REVIEW")
                .resultingStatus("UNDER_REVIEW")
                .actionType("SUBMIT")
                .build();

        DynamicWorkflowEngine.AllowedActionDto legacyAction = DynamicWorkflowEngine.AllowedActionDto.builder()
                .actionCode("SEND_FOR_REVIEW")
                .actionName("Send for Review")
                .displayName("Submit for Review")
                .fromStageCode("SUBMISSION")
                .toStageCode("REVIEW")
                .resultingStatus("UNDER_REVIEW")
                .actionType("SUBMIT")
                .build();

        List<DynamicWorkflowEngine.AllowedActionDto> duplicates = List.of(legacyAction, canonicalAction);
        List<DynamicWorkflowEngine.AllowedActionDto> deduplicated = DynamicWorkflowEngine.deduplicateActions(duplicates);

        assertEquals(1, deduplicated.size(), "Deduplication must result in exactly 1 action");
        assertEquals("SUBMIT_FOR_REVIEW", deduplicated.get(0).getActionCode(), "Canonical action code must be preserved");
    }

    @Test
    @DisplayName("Bulk action execution processes multiple batch tasks with deterministic result model")
    void testBulkActionExecution() {
        Document authCreds = new Document("userId", "OPERATOR_USER_01")
                .append("passwordHash", validPasswordHash);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("auth_credentials")))
                .thenReturn(authCreds);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_actions")))
                .thenReturn(submitForReviewAction);

        WorkflowInstance instance = WorkflowInstance.builder()
                .instanceId("WFI-0005")
                .workflowCode("IIOT_BATCH_STAGE_WORKFLOW")
                .workflowVersion("1.0.0")
                .currentStageCode("SUBMISSION")
                .currentStatus("PENDING")
                .initiatedBy("OPERATOR_USER_01")
                .build();

        Document batchSummary = new Document("batchNo", "B-BULK-001")
                .append("lotNo", "01 of 05")
                .append("stages", List.of(new Document("equipmentCode", "G5RMG")
                        .append("approval", new Document("status", "PENDING"))));

        when(mongoTemplate.findOne(any(Query.class), eq(WorkflowInstance.class), eq("iiot_workflow_instances")))
                .thenReturn(instance);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_stages")))
                .thenReturn(submissionStage);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_workflow_transitions")))
                .thenReturn(transition1);
        when(mongoTemplate.findOne(argThat(q -> q != null && q.toString().contains("B-BULK-001")), eq(Document.class), eq("iiot_batch_summary")))
                .thenReturn(batchSummary);
        when(mongoTemplate.findOne(argThat(q -> q != null && q.toString().contains("B-BULK-999")), eq(Document.class), eq("iiot_batch_summary")))
                .thenReturn(null);
        when(mongoTemplate.save(any(Document.class), eq("iiot_batch_summary"))).thenAnswer(inv -> inv.getArgument(0));

        DynamicWorkflowEngine.BulkActionExecutionRequest request = DynamicWorkflowEngine.BulkActionExecutionRequest.builder()
                .userId("OPERATOR_USER_01")
                .userRole("PRODUCTION_OPERATOR")
                .actionCode("SUBMIT_FOR_REVIEW")
                .password("Password123!")
                .comments("Bulk submission for review")
                .tenantId("TNT-0001")
                .plantId("PLNT-0001")
                .items(List.of(
                        DynamicWorkflowEngine.BulkActionItem.builder().batchNo("B-BULK-001").lotNo("01 of 05").equipmentCode("G5RMG").build(),
                        DynamicWorkflowEngine.BulkActionItem.builder().batchNo("B-BULK-999").lotNo("99 of 99").equipmentCode("NONEXISTENT").build()
                ))
                .build();

        DynamicWorkflowEngine.BulkExecutionResult result = workflowEngine.executeBulkAction(request);

        assertNotNull(result);
        assertEquals(2, result.getTotalRequested());
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        assertEquals(1, result.getSuccessfulItems().size());
        assertEquals(1, result.getFailedItems().size());
        assertEquals("B-BULK-001", result.getSuccessfulItems().get(0).getBatchNo());
        assertEquals("B-BULK-999", result.getFailedItems().get(0).getBatchNo());
    }

    @Test
    @DisplayName("Bulk action execution rejects PLATFORM_SUPER_ADMIN operational mutation")
    void testBulkActionExecutionRejectsAdmin() {
        DynamicWorkflowEngine.BulkActionExecutionRequest request = DynamicWorkflowEngine.BulkActionExecutionRequest.builder()
                .userId("SUPER_ADMIN")
                .userRole("PLATFORM_SUPER_ADMIN")
                .actionCode("SUBMIT_FOR_REVIEW")
                .password("Password123!")
                .items(List.of(
                        DynamicWorkflowEngine.BulkActionItem.builder().batchNo("B-BULK-001").lotNo("01 of 05").equipmentCode("G5RMG").build()
                ))
                .build();

        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> {
            workflowEngine.executeBulkAction(request);
        });

        assertEquals("FORBIDDEN", ex.getErrorCode());
    }
}
