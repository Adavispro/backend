package com.adavis.iiot.service;

import com.adavis.common.exception.UnauthorizedException;
import com.adavis.iiot.model.NotificationDocument;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private NotificationRecipientResolver recipientResolver;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(mongoTemplate, recipientResolver);
    }

    @Test
    @DisplayName("1. Emit UNDER_REVIEW transition sends notification to QA Reviewers with correct deepLink")
    void testEmitUnderReviewNotification() {
        when(recipientResolver.resolveQAReviewers(eq("TNT-0001"), eq("PLNT-0001"), eq("OP_01")))
                .thenReturn(Set.of("QA_REV_01"));
        when(mongoTemplate.exists(any(Query.class), eq("notifications"))).thenReturn(false);

        Document stage = new Document("operatorName", "OP_01");
        notificationService.emitWorkflowTransitionNotification(
                "TNT-0001", "PLNT-0001", "BATCH-101", "LOT-01", "G5RMG",
                "PENDING", "UNDER_REVIEW", "OP_01", "PRODUCTION_OPERATOR", "Ready for review", stage);

        ArgumentCaptor<NotificationDocument> captor = ArgumentCaptor.forClass(NotificationDocument.class);
        verify(mongoTemplate, times(1)).insert(captor.capture(), eq("notifications"));

        NotificationDocument saved = captor.getValue();
        assertEquals("QA_REV_01", saved.getRecipientUserId());
        assertEquals("BATCH_SUBMITTED_FOR_REVIEW", saved.getEventCode());
        assertEquals("TNT-0001", saved.getTenantId());
        assertEquals("PLNT-0001", saved.getPlantId());
        assertEquals("INFO", saved.getSeverity());
        assertTrue(saved.getDeepLink().contains("/iiot/my-actions"));
        assertTrue(saved.getDeepLink().contains("BATCH-101"));
        assertFalse(saved.getIsRead());
    }

    @Test
    @DisplayName("2. Emit REVIEWER_REVIEWED / PENDING_APPROVAL sends notification to Shift Supervisors")
    void testEmitPendingApprovalNotification() {
        when(recipientResolver.resolveShiftSupervisors(eq("TNT-0001"), eq("PLNT-0001"), isNull(), eq("QA_REV_01")))
                .thenReturn(Set.of("SUPERVISOR_01"));
        when(mongoTemplate.exists(any(Query.class), eq("notifications"))).thenReturn(false);

        Document stage = new Document();
        notificationService.emitWorkflowTransitionNotification(
                "TNT-0001", "PLNT-0001", "BATCH-102", "LOT-01", "G5RMG",
                "UNDER_REVIEW", "PENDING_APPROVAL", "QA_REV_01", "QA_REVIEWER", "Verified", stage);

        ArgumentCaptor<NotificationDocument> captor = ArgumentCaptor.forClass(NotificationDocument.class);
        verify(mongoTemplate, times(1)).insert(captor.capture(), eq("notifications"));

        NotificationDocument saved = captor.getValue();
        assertEquals("SUPERVISOR_01", saved.getRecipientUserId());
        assertEquals("BATCH_PENDING_APPROVAL", saved.getEventCode());
        assertTrue(saved.getDeepLink().contains("BATCH-102"));
    }

    @Test
    @DisplayName("3. Emit APPROVED transition sends notification to batch participants with success severity")
    void testEmitApprovedNotification() {
        when(recipientResolver.resolveBatchParticipants(eq("TNT-0001"), eq("PLNT-0001"), eq("BATCH-103"), eq("G5RMG"), any(), eq("SUPERVISOR_01")))
                .thenReturn(Set.of("OP_01", "QA_REV_01"));
        when(mongoTemplate.exists(any(Query.class), eq("notifications"))).thenReturn(false);

        Document stage = new Document("operatorName", "OP_01");
        notificationService.emitWorkflowTransitionNotification(
                "TNT-0001", "PLNT-0001", "BATCH-103", "LOT-01", "G5RMG",
                "REVIEWER_REVIEWED", "APPROVED", "SUPERVISOR_01", "SHIFT_SUPERVISOR", "Approved release", stage);

        ArgumentCaptor<NotificationDocument> captor = ArgumentCaptor.forClass(NotificationDocument.class);
        verify(mongoTemplate, times(2)).insert(captor.capture(), eq("notifications"));

        List<NotificationDocument> saved = captor.getAllValues();
        assertEquals(2, saved.size());
        assertEquals("SUCCESS", saved.get(0).getSeverity());
        assertTrue(saved.get(0).getDeepLink().contains("/iiot/approved-batches"));
    }

    @Test
    @DisplayName("4. Emit REJECTED transition sends notification with error severity and deepLink to pending reports")
    void testEmitRejectedNotification() {
        when(recipientResolver.resolveBatchParticipants(eq("TNT-0001"), eq("PLNT-0001"), eq("BATCH-104"), eq("G5RMG"), any(), eq("SUPERVISOR_01")))
                .thenReturn(Set.of("OP_01"));
        when(mongoTemplate.exists(any(Query.class), eq("notifications"))).thenReturn(false);

        Document stage = new Document("operatorName", "OP_01");
        notificationService.emitWorkflowTransitionNotification(
                "TNT-0001", "PLNT-0001", "BATCH-104", "LOT-01", "G5RMG",
                "REVIEWER_REVIEWED", "REJECTED", "SUPERVISOR_01", "SHIFT_SUPERVISOR", "Granulation out of spec", stage);

        ArgumentCaptor<NotificationDocument> captor = ArgumentCaptor.forClass(NotificationDocument.class);
        verify(mongoTemplate, times(1)).insert(captor.capture(), eq("notifications"));

        NotificationDocument saved = captor.getValue();
        assertEquals("ERROR", saved.getSeverity());
        assertEquals("BATCH_REJECTED", saved.getEventCode());
        assertTrue(saved.getMessage().contains("Granulation out of spec"));
        assertTrue(saved.getDeepLink().contains("/iiot/pending-reports"));
    }

    @Test
    @DisplayName("5. Emit RETURNED_TO_OPERATOR transition sends notification with warning severity")
    void testEmitReturnedNotification() {
        when(recipientResolver.resolveBatchParticipants(eq("TNT-0001"), eq("PLNT-0001"), eq("BATCH-105"), eq("G5RMG"), any(), eq("QA_REV_01")))
                .thenReturn(Set.of("OP_01"));
        when(mongoTemplate.exists(any(Query.class), eq("notifications"))).thenReturn(false);

        Document stage = new Document("operatorName", "OP_01");
        notificationService.emitWorkflowTransitionNotification(
                "TNT-0001", "PLNT-0001", "BATCH-105", "LOT-01", "G5RMG",
                "UNDER_REVIEW", "RETURNED_TO_OPERATOR", "QA_REV_01", "QA_REVIEWER", "Please attach log sheet", stage);

        ArgumentCaptor<NotificationDocument> captor = ArgumentCaptor.forClass(NotificationDocument.class);
        verify(mongoTemplate, times(1)).insert(captor.capture(), eq("notifications"));

        NotificationDocument saved = captor.getValue();
        assertEquals("WARNING", saved.getSeverity());
        assertEquals("BATCH_RETURNED", saved.getEventCode());
    }

    @Test
    @DisplayName("6. Duplicate event notification is suppressed via idempotency key")
    void testDuplicateNotificationSuppression() {
        when(recipientResolver.resolveQAReviewers(eq("TNT-0001"), eq("PLNT-0001"), eq("OP_01")))
                .thenReturn(Set.of("QA_REV_01"));
        when(mongoTemplate.exists(any(Query.class), eq("notifications"))).thenReturn(true);

        Document stage = new Document();
        notificationService.emitWorkflowTransitionNotification(
                "TNT-0001", "PLNT-0001", "BATCH-106", "LOT-01", "G5RMG",
                "PENDING", "UNDER_REVIEW", "OP_01", "PRODUCTION_OPERATOR", "Ready", stage);

        verify(mongoTemplate, never()).insert(any(NotificationDocument.class), anyString());
    }

    @Test
    @DisplayName("7. Mark notification as read enforces recipient ownership and tenant isolation")
    void testMarkAsReadOwnershipEnforcement() {
        NotificationDocument doc = NotificationDocument.builder()
                .notificationId("NOTIF-1234")
                .recipientUserId("OP_01")
                .tenantId("TNT-0001")
                .plantId("PLNT-0001")
                .isRead(false)
                .build();

        when(mongoTemplate.findOne(any(Query.class), eq(NotificationDocument.class), eq("notifications")))
                .thenReturn(doc);

        // Attempting to mark another user's notification throws UnauthorizedException
        assertThrows(UnauthorizedException.class, () -> {
            notificationService.markAsRead("NOTIF-1234", "ANOTHER_USER", "TNT-0001", "PLNT-0001");
        });

        // Attempting to mark notification belonging to another tenant throws UnauthorizedException
        assertThrows(UnauthorizedException.class, () -> {
            notificationService.markAsRead("NOTIF-1234", "OP_01", "TNT-OTHER", "PLNT-0001");
        });

        // Valid owner and tenant succeeds
        Map<String, Object> res = notificationService.markAsRead("NOTIF-1234", "OP_01", "TNT-0001", "PLNT-0001");
        assertTrue((Boolean) res.get("isRead"));
        verify(mongoTemplate, times(1)).save(doc, "notifications");
    }

    @Test
    @DisplayName("8. Machine alarm notification is emitted with critical error severity and deepLink")
    void testEmitMachineAlarmNotification() {
        when(recipientResolver.resolveShiftSupervisors(eq("TNT-0001"), eq("PLNT-0001"), isNull(), eq("SYSTEM")))
                .thenReturn(Set.of("SUPERVISOR_01"));
        when(mongoTemplate.exists(any(Query.class), eq("notifications"))).thenReturn(false);

        notificationService.emitAlarmNotification(
                "TNT-0001", "PLNT-0001", "G5RMG", "TEMP_HIGH", "CRITICAL",
                "Bowl temperature exceeded 65C threshold", "SYSTEM");

        ArgumentCaptor<NotificationDocument> captor = ArgumentCaptor.forClass(NotificationDocument.class);
        verify(mongoTemplate, times(1)).insert(captor.capture(), eq("notifications"));

        NotificationDocument saved = captor.getValue();
        assertEquals("SUPERVISOR_01", saved.getRecipientUserId());
        assertEquals("MACHINE_ALARM", saved.getEventCode());
        assertEquals("CRITICAL", saved.getSeverity());
        assertTrue(saved.getDeepLink().contains("/iiot/reports/alarm-events"));
    }
}
