package com.adavis.iiot.service;

import com.adavis.common.exception.BusinessException;
import com.adavis.iiot.model.WorkflowInstance;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DynamicBatchAssignmentWorkflowTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private NotificationService notificationService;

    @Mock
    private BatchPdfGeneratorService batchPdfGeneratorService;

    @InjectMocks
    private DynamicWorkflowEngine workflowEngine;

    private static final String BATCH_SUMMARY_COLLECTION = "iiot_batch_summary";
    private static final String INSTANCE_COLLECTION = "iiot_workflow_instances";
    private static final String AUDIT_COLLECTION = "iiot_workflow_audit_trail";

    private Document unassignedSummary;
    private Document assignedSummary;
    private Document approvedSummary;

    @BeforeEach
    void setUp() {
        // Sample in-flight unassigned batch stage
        Document stage1 = new Document("equipmentCode", "G5RMG")
                .append("equipmentType", "RMG")
                .append("sequenceOrder", 1)
                .append("approval", new Document("status", "PENDING"));

        unassignedSummary = new Document("batchNo", "B-PEND-001")
                .append("lotNo", "01 of 05")
                .append("productCode", "STFS7000")
                .append("productName", "Finasteride USP 5 mg")
                .append("tenantId", "TNT-0001")
                .append("plantId", "PLNT-0001")
                .append("stages", List.of(stage1));

        // Sample in-flight stage assigned to operator_1
        Document stageAssigned = new Document("equipmentCode", "G5FBD")
                .append("equipmentType", "FBD")
                .append("sequenceOrder", 2)
                .append("approval", new Document("status", "UNDER_REVIEW")
                        .append("assignedTo", "operator_1")
                        .append("activeReviewer", "operator_1"));

        assignedSummary = new Document("batchNo", "B-ASSIGNED-001")
                .append("lotNo", "01 of 05")
                .append("productCode", "STFS7000")
                .append("productName", "Finasteride USP 5 mg")
                .append("tenantId", "TNT-0001")
                .append("plantId", "PLNT-0001")
                .append("assignedTo", "operator_1")
                .append("stages", List.of(stageAssigned));

        // Sample completed/approved batch
        Document stageApproved = new Document("equipmentCode", "G5OGB")
                .append("equipmentType", "OGB")
                .append("sequenceOrder", 3)
                .append("approval", new Document("status", "APPROVED")
                        .append("assignedTo", "operator_1"));

        approvedSummary = new Document("batchNo", "B-APPR-001")
                .append("lotNo", "01 of 05")
                .append("productCode", "STFS7000")
                .append("productName", "Finasteride USP 5 mg")
                .append("tenantId", "TNT-0001")
                .append("plantId", "PLNT-0001")
                .append("stages", List.of(stageApproved));
    }

    @Test
    @DisplayName("getPendingBatches returns in-flight unassigned batches, excludes batches assigned to others, and excludes terminal batches")
    void testGetPendingBatches_ReturnsAvailableQueueOnly() {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq(BATCH_SUMMARY_COLLECTION)))
                .thenReturn(List.of(unassignedSummary, assignedSummary, approvedSummary));

        // Operator 2 queries Pending Batches
        List<Map<String, Object>> pending = workflowEngine.getPendingBatches(
                "operator_2", "PRODUCTION_OPERATOR", "TNT-0001", "PLNT-0001", Collections.emptyMap());

        assertNotNull(pending);
        assertEquals(1, pending.size(), "Only unassigned in-flight batch should be in pending available queue");
        assertEquals("B-PEND-001", pending.get(0).get("batchNo"));
        assertEquals("G5RMG", pending.get(0).get("equipmentCode"));
    }

    @Test
    @DisplayName("getMyActions returns empty list when no batches are assigned to current user")
    void testGetMyActions_EmptyWhenNoBatchesAssigned() {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq(BATCH_SUMMARY_COLLECTION)))
                .thenReturn(List.of(unassignedSummary, approvedSummary));
        when(mongoTemplate.find(any(Query.class), eq(WorkflowInstance.class), eq(INSTANCE_COLLECTION)))
                .thenReturn(Collections.emptyList());

        List<Map<String, Object>> actions = workflowEngine.getMyActions(
                "operator_2", "PRODUCTION_OPERATOR", "TNT-0001", "PLNT-0001", Collections.emptyMap());

        assertNotNull(actions);
        assertTrue(actions.isEmpty(), "My Actions must be strictly empty when no batches are assigned");
    }

    @Test
    @DisplayName("getMyActions returns only batches assigned to authenticated user and still in-flight")
    void testGetMyActions_ReturnsAssignedInFlightBatches() {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq(BATCH_SUMMARY_COLLECTION)))
                .thenReturn(List.of(unassignedSummary, assignedSummary, approvedSummary));

        List<Map<String, Object>> actions = workflowEngine.getMyActions(
                "operator_1", "PRODUCTION_OPERATOR", "TNT-0001", "PLNT-0001", Collections.emptyMap());

        assertNotNull(actions);
        assertEquals(1, actions.size(), "My Actions must contain only the assigned actionable batch");
        assertEquals("B-ASSIGNED-001", actions.get(0).get("batchNo"));
        assertEquals("G5FBD", actions.get(0).get("equipmentCode"));
        assertEquals("operator_1", actions.get(0).get("assignedTo"));
    }

    @Test
    @DisplayName("getMyActions enforces user isolation: User A cannot see User B's assignments")
    void testGetMyActions_UserIsolation() {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq(BATCH_SUMMARY_COLLECTION)))
                .thenReturn(List.of(assignedSummary));
        when(mongoTemplate.find(any(Query.class), eq(WorkflowInstance.class), eq(INSTANCE_COLLECTION)))
                .thenReturn(Collections.emptyList());

        // operator_2 tries to retrieve My Actions (should NOT see operator_1's batch)
        List<Map<String, Object>> actionsOp2 = workflowEngine.getMyActions(
                "operator_2", "PRODUCTION_OPERATOR", "TNT-0001", "PLNT-0001", Collections.emptyMap());

        assertNotNull(actionsOp2);
        assertEquals(0, actionsOp2.size(), "Operator 2 must not see assignments belonging to Operator 1");
    }

    @Test
    @DisplayName("getMyActions excludes completed/approved batches even if previously assigned to the user")
    void testGetMyActions_ExcludesCompletedBatches() {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq(BATCH_SUMMARY_COLLECTION)))
                .thenReturn(List.of(approvedSummary));
        when(mongoTemplate.find(any(Query.class), eq(WorkflowInstance.class), eq(INSTANCE_COLLECTION)))
                .thenReturn(Collections.emptyList());

        List<Map<String, Object>> actions = workflowEngine.getMyActions(
                "operator_1", "PRODUCTION_OPERATOR", "TNT-0001", "PLNT-0001", Collections.emptyMap());

        assertNotNull(actions);
        assertTrue(actions.isEmpty(), "Approved/completed batches must leave My Actions");
    }

    @Test
    @DisplayName("claimWorkflowTask successfully assigns unassigned batch and writes immutable audit event")
    void testClaimWorkflowTask_SuccessAndAudit() {
        WorkflowInstance instance = WorkflowInstance.builder()
                .batchNo("B-PEND-001")
                .lotNo("01 of 05")
                .equipmentCode("G5RMG")
                .tenantId("TNT-0001")
                .plantId("PLNT-0001")
                .currentStatus("PENDING")
                .build();

        when(mongoTemplate.findOne(any(Query.class), eq(WorkflowInstance.class), eq(INSTANCE_COLLECTION)))
                .thenReturn(instance);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq(BATCH_SUMMARY_COLLECTION)))
                .thenReturn(unassignedSummary);

        Map<String, Object> result = workflowEngine.claimWorkflowTask(
                "B-PEND-001", "01 of 05", "G5RMG", "operator_1", "PRODUCTION_OPERATOR", "TNT-0001", "PLNT-0001");

        assertNotNull(result);
        assertTrue((Boolean) result.get("success"));
        assertEquals("operator_1", result.get("assignedTo"));
        assertEquals("operator_1", instance.getAssignedTo());

        // Verify audit event written to AUDIT_COLLECTION
        ArgumentCaptor<Document> auditCaptor = ArgumentCaptor.forClass(Document.class);
        verify(mongoTemplate).insert(auditCaptor.capture(), eq(AUDIT_COLLECTION));
        Document auditDoc = auditCaptor.getValue();
        assertEquals("ASSIGN_TO_ME", auditDoc.get("action"));
        assertEquals("CLAIM_TASK", auditDoc.get("actionCode"));
        assertEquals("operator_1", auditDoc.get("userId"));
        assertEquals("operator_1", auditDoc.get("newAssignment"));
        assertNull(auditDoc.get("previousAssignment"));
    }

    @Test
    @DisplayName("claimWorkflowTask rejects dual ownership when batch is already claimed by another user")
    void testClaimWorkflowTask_ConcurrencyConflict() {
        WorkflowInstance instance = WorkflowInstance.builder()
                .batchNo("B-PEND-001")
                .lotNo("01 of 05")
                .equipmentCode("G5RMG")
                .tenantId("TNT-0001")
                .plantId("PLNT-0001")
                .currentStatus("PENDING")
                .assignedTo("operator_1") // Already claimed by operator_1
                .build();

        when(mongoTemplate.findOne(any(Query.class), eq(WorkflowInstance.class), eq(INSTANCE_COLLECTION)))
                .thenReturn(instance);

        // operator_2 attempts to claim the same batch
        BusinessException ex = assertThrows(BusinessException.class, () ->
                workflowEngine.claimWorkflowTask(
                        "B-PEND-001", "01 of 05", "G5RMG", "operator_2", "PRODUCTION_OPERATOR", "TNT-0001", "PLNT-0001")
        );

        assertEquals("DUPLICATE_ASSIGNMENT_CONFLICT", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("already assigned to operator_1"));
        assertEquals("operator_1", instance.getAssignedTo(), "Original ownership must remain intact");
    }

    @Test
    @DisplayName("unclaimWorkflowTask releases assignment and logs immutable audit trail event")
    void testUnclaimWorkflowTask_SuccessAndAudit() {
        WorkflowInstance instance = WorkflowInstance.builder()
                .batchNo("B-ASSIGNED-001")
                .lotNo("01 of 05")
                .equipmentCode("G5FBD")
                .tenantId("TNT-0001")
                .plantId("PLNT-0001")
                .currentStatus("UNDER_REVIEW")
                .assignedTo("operator_1")
                .build();

        when(mongoTemplate.findOne(any(Query.class), eq(WorkflowInstance.class), eq(INSTANCE_COLLECTION)))
                .thenReturn(instance);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq(BATCH_SUMMARY_COLLECTION)))
                .thenReturn(assignedSummary);

        Map<String, Object> result = workflowEngine.unclaimWorkflowTask(
                "B-ASSIGNED-001", "01 of 05", "G5FBD", "operator_1", "TNT-0001");

        assertNotNull(result);
        assertTrue((Boolean) result.get("success"));
        assertNull(instance.getAssignedTo());

        // Verify audit event written to AUDIT_COLLECTION
        ArgumentCaptor<Document> auditCaptor = ArgumentCaptor.forClass(Document.class);
        verify(mongoTemplate).insert(auditCaptor.capture(), eq(AUDIT_COLLECTION));
        Document auditDoc = auditCaptor.getValue();
        assertEquals("RELEASE_ASSIGNMENT", auditDoc.get("action"));
        assertEquals("UNCLAIM_TASK", auditDoc.get("actionCode"));
        assertEquals("operator_1", auditDoc.get("userId"));
        assertEquals("operator_1", auditDoc.get("previousAssignment"));
        assertNull(auditDoc.get("newAssignment"));
    }
}
