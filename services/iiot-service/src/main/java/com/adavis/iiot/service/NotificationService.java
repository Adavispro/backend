package com.adavis.iiot.service;

import com.adavis.common.exception.BusinessException;
import com.adavis.common.exception.UnauthorizedException;
import com.adavis.iiot.model.NotificationDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final MongoTemplate mongoTemplate;
    private final NotificationRecipientResolver recipientResolver;

    private static final String NOTIFICATIONS_COLLECTION = "notifications";

    /**
     * Authoritatively emit role-aware, tenant-isolated, and plant-scoped workflow notifications.
     */
    public void emitWorkflowTransitionNotification(String tenantId, String plantId, String batchNo,
                                                   String lotNo, String equipmentCode, String previousStatus,
                                                   String targetStatus, String actorUserId, String userRole,
                                                   String comments, Document stage) {
        try {
            String normTarget = targetStatus != null ? targetStatus.toUpperCase(Locale.ROOT).trim() : "";
            Date now = Date.from(Instant.now());
            String entityId = batchNo + ":" + (equipmentCode != null ? equipmentCode : "STAGE");
            String effectivePlantId = (plantId != null && !plantId.isBlank()) ? plantId.trim() : "PLNT-0001";
            String effectiveTenantId = (tenantId != null && !tenantId.isBlank()) ? tenantId.trim() : "TNT-0001";

            Set<String> recipients = new LinkedHashSet<>();
            String eventCode = "WORKFLOW_TRANSITION";
            String title = "Batch Workflow Update";
            String message = "Workflow update on batch " + batchNo;
            String severity = "INFO";
            String deepLink = "/iiot/my-actions?batchNo=" + encode(batchNo);

            switch (normTarget) {
                case "UNDER_REVIEW":
                case "IN_REVIEW":
                    eventCode = "BATCH_SUBMITTED_FOR_REVIEW";
                    title = "Batch Stage Submitted for QA Review";
                    message = String.format("Batch %s (Lot: %s, Stage: %s) submitted for QA review by %s.",
                            batchNo, lotNo != null ? lotNo : "-", equipmentCode != null ? equipmentCode : "-", actorUserId);
                    severity = "INFO";
                    deepLink = String.format("/iiot/my-actions?batchNo=%s&equipmentCode=%s", encode(batchNo), encode(equipmentCode));
                    recipients = recipientResolver.resolveQAReviewers(effectiveTenantId, effectivePlantId, actorUserId);
                    break;

                case "REVIEWER_REVIEWED":
                case "PENDING_APPROVAL":
                    eventCode = "BATCH_PENDING_APPROVAL";
                    title = "Batch Stage Verified & Ready for Approval";
                    message = String.format("Batch %s (Lot: %s, Stage: %s) verified by QA Reviewer %s and awaiting release approval.",
                            batchNo, lotNo != null ? lotNo : "-", equipmentCode != null ? equipmentCode : "-", actorUserId);
                    severity = "INFO";
                    deepLink = String.format("/iiot/my-actions?batchNo=%s&equipmentCode=%s", encode(batchNo), encode(equipmentCode));
                    String assignedSupervisor = stage != null ? stage.getString("supervisorName") : null;
                    recipients = recipientResolver.resolveShiftSupervisors(effectiveTenantId, effectivePlantId, assignedSupervisor, actorUserId);
                    break;

                case "APPROVED":
                    eventCode = "BATCH_APPROVED";
                    title = "Batch Stage Approved";
                    message = String.format("Batch %s (Lot: %s, Stage: %s) has been approved for release by Supervisor %s.",
                            batchNo, lotNo != null ? lotNo : "-", equipmentCode != null ? equipmentCode : "-", actorUserId);
                    severity = "SUCCESS";
                    deepLink = String.format("/iiot/approved-batches?batchNo=%s", encode(batchNo));
                    recipients = recipientResolver.resolveBatchParticipants(effectiveTenantId, effectivePlantId, batchNo, equipmentCode, stage, actorUserId);
                    break;

                case "REJECTED":
                    eventCode = "BATCH_REJECTED";
                    title = "Batch Stage Rejected";
                    String reasonText = (comments != null && !comments.isBlank()) ? comments.trim() : "Mandatory rejection reason not specified.";
                    message = String.format("Batch %s (Lot: %s, Stage: %s) was REJECTED by Supervisor %s. Reason: %s",
                            batchNo, lotNo != null ? lotNo : "-", equipmentCode != null ? equipmentCode : "-", actorUserId, reasonText);
                    severity = "ERROR";
                    deepLink = String.format("/iiot/pending-reports?batchNo=%s&equipmentCode=%s", encode(batchNo), encode(equipmentCode));
                    recipients = recipientResolver.resolveBatchParticipants(effectiveTenantId, effectivePlantId, batchNo, equipmentCode, stage, actorUserId);
                    break;

                case "RETURNED_TO_OPERATOR":
                case "RETURNED":
                    eventCode = "BATCH_RETURNED";
                    title = "Batch Stage Returned for Correction";
                    String returnReason = (comments != null && !comments.isBlank()) ? comments.trim() : "Returned for operator correction.";
                    message = String.format("Batch %s (Lot: %s, Stage: %s) was returned for correction by %s. Reason: %s",
                            batchNo, lotNo != null ? lotNo : "-", equipmentCode != null ? equipmentCode : "-", actorUserId, returnReason);
                    severity = "WARNING";
                    deepLink = String.format("/iiot/pending-reports?batchNo=%s&equipmentCode=%s", encode(batchNo), encode(equipmentCode));
                    recipients = recipientResolver.resolveBatchParticipants(effectiveTenantId, effectivePlantId, batchNo, equipmentCode, stage, actorUserId);
                    break;

                case "DEFERRED":
                    eventCode = "BATCH_DEFERRED";
                    title = "Batch Stage Deferred";
                    String deferReason = (comments != null && !comments.isBlank()) ? comments.trim() : "Processing deferred.";
                    message = String.format("Batch %s (Lot: %s, Stage: %s) processing was DEFERRED by %s. Justification: %s",
                            batchNo, lotNo != null ? lotNo : "-", equipmentCode != null ? equipmentCode : "-", actorUserId, deferReason);
                    severity = "WARNING";
                    deepLink = String.format("/iiot/deferred-batches?batchNo=%s", encode(batchNo));
                    recipients = recipientResolver.resolveBatchParticipants(effectiveTenantId, effectivePlantId, batchNo, equipmentCode, stage, actorUserId);
                    break;

                case "ESCALATED":
                    eventCode = "BATCH_ESCALATED";
                    title = "Batch Stage Escalated";
                    message = String.format("Batch %s (Lot: %s, Stage: %s) was ESCALATED by %s for immediate managerial review.",
                            batchNo, lotNo != null ? lotNo : "-", equipmentCode != null ? equipmentCode : "-", actorUserId);
                    severity = "WARNING";
                    deepLink = String.format("/iiot/my-actions?batchNo=%s", encode(batchNo));
                    recipients = recipientResolver.resolveUsersByRoleCodes(
                            Set.of("QA_APPROVER", "SHIFT_SUPERVISOR", "PLATFORM_SUPER_ADMIN"),
                            effectiveTenantId, effectivePlantId, actorUserId);
                    break;

                default:
                    log.debug("No automated notifications configured for transition to state: {}", normTarget);
                    return;
            }

            if (recipients.isEmpty()) {
                log.warn("No eligible recipients resolved for workflow event {} on batch={} stage={} tenant={}",
                        eventCode, batchNo, equipmentCode, effectiveTenantId);
                return;
            }

            for (String recipient : recipients) {
                if (recipient == null || recipient.isBlank() || recipient.equalsIgnoreCase("SYSTEM")) continue;

                String notifId = "NOTIF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
                String idempotencyKey = String.format("%s:%s:%s:%s:%s:%s",
                        effectiveTenantId, effectivePlantId, eventCode, batchNo, equipmentCode != null ? equipmentCode : "ALL", recipient.toUpperCase(Locale.ROOT));

                NotificationDocument doc = NotificationDocument.builder()
                        .notificationId(notifId)
                        .recipientUserId(recipient.toUpperCase(Locale.ROOT))
                        .tenantId(effectiveTenantId)
                        .plantId(effectivePlantId)
                        .type("WORKFLOW_TRANSITION")
                        .eventCode(eventCode)
                        .title(title)
                        .message(message)
                        .entityType("BATCH_STAGE")
                        .entityId(entityId)
                        .batchNo(batchNo)
                        .lotNo(lotNo)
                        .equipmentCode(equipmentCode)
                        .workflowState(normTarget)
                        .severity(severity)
                        .deepLink(deepLink)
                        .isRead(false)
                        .createdAt(now)
                        .actorUserId(actorUserId)
                        .idempotencyKey(idempotencyKey)
                        .build();

                persistNotificationIfUnique(doc, idempotencyKey);
            }

        } catch (Exception e) {
            log.error("Failed to emit workflow transition notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Authoritatively emit new workflow assignment notification.
     */
    public void emitAssignmentNotification(String tenantId, String plantId, String batchNo,
                                           String lotNo, String equipmentCode, String assignedUserId,
                                           String actorUserId) {
        if (assignedUserId == null || assignedUserId.isBlank() || assignedUserId.equalsIgnoreCase("SYSTEM")) return;

        Date now = Date.from(Instant.now());
        String effectivePlantId = (plantId != null && !plantId.isBlank()) ? plantId.trim() : "PLNT-0001";
        String effectiveTenantId = (tenantId != null && !tenantId.isBlank()) ? tenantId.trim() : "TNT-0001";
        String canonicalRecipient = recipientResolver.resolveCanonicalUserId(assignedUserId, effectiveTenantId, effectivePlantId);
        if (canonicalRecipient == null) return;

        String notifId = "NOTIF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String idempotencyKey = String.format("%s:%s:ASSIGNMENT:%s:%s:%s",
                effectiveTenantId, effectivePlantId, batchNo, equipmentCode != null ? equipmentCode : "ALL", canonicalRecipient);
        String deepLink = String.format("/iiot/my-actions?batchNo=%s&equipmentCode=%s", encode(batchNo), encode(equipmentCode));

        NotificationDocument doc = NotificationDocument.builder()
                .notificationId(notifId)
                .recipientUserId(canonicalRecipient)
                .tenantId(effectiveTenantId)
                .plantId(effectivePlantId)
                .type("ASSIGNMENT")
                .eventCode("WORKFLOW_ASSIGNMENT")
                .title("New Batch Assignment")
                .message(String.format("You have been assigned to batch %s (Lot: %s, Stage: %s) by %s.",
                        batchNo, lotNo != null ? lotNo : "-", equipmentCode != null ? equipmentCode : "-", actorUserId))
                .entityType("BATCH_STAGE")
                .entityId(batchNo + ":" + equipmentCode)
                .batchNo(batchNo)
                .lotNo(lotNo)
                .equipmentCode(equipmentCode)
                .workflowState("ASSIGNED")
                .severity("INFO")
                .deepLink(deepLink)
                .isRead(false)
                .createdAt(now)
                .actorUserId(actorUserId)
                .idempotencyKey(idempotencyKey)
                .build();

        persistNotificationIfUnique(doc, idempotencyKey);
    }

    /**
     * Authoritatively emit critical equipment/machine alarm notification.
     */
    public void emitAlarmNotification(String tenantId, String plantId, String equipmentCode,
                                      String alarmCode, String severity, String message, String actorUserId) {
        if (equipmentCode == null || equipmentCode.isBlank()) return;

        Date now = Date.from(Instant.now());
        String effectivePlantId = (plantId != null && !plantId.isBlank()) ? plantId.trim() : "PLNT-0001";
        String effectiveTenantId = (tenantId != null && !tenantId.isBlank()) ? tenantId.trim() : "TNT-0001";

        Set<String> recipients = recipientResolver.resolveShiftSupervisors(effectiveTenantId, effectivePlantId, null, actorUserId);
        if (recipients.isEmpty()) return;

        String deepLink = String.format("/iiot/reports/alarm-events?equipmentId=%s", encode(equipmentCode));

        for (String recipient : recipients) {
            String notifId = "NOTIF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
            String idempotencyKey = String.format("%s:%s:ALARM:%s:%s:%s",
                    effectiveTenantId, effectivePlantId, equipmentCode, alarmCode != null ? alarmCode : "GEN", recipient);

            NotificationDocument doc = NotificationDocument.builder()
                    .notificationId(notifId)
                    .recipientUserId(recipient)
                    .tenantId(effectiveTenantId)
                    .plantId(effectivePlantId)
                    .type("ALARM")
                    .eventCode("MACHINE_ALARM")
                    .title("Critical Machine Alarm: " + equipmentCode)
                    .message(message != null ? message : "Process alarm triggered on " + equipmentCode)
                    .entityType("EQUIPMENT")
                    .entityId(equipmentCode)
                    .equipmentCode(equipmentCode)
                    .severity(severity != null ? severity.toUpperCase(Locale.ROOT) : "ERROR")
                    .deepLink(deepLink)
                    .isRead(false)
                    .createdAt(now)
                    .actorUserId(actorUserId)
                    .idempotencyKey(idempotencyKey)
                    .build();

            persistNotificationIfUnique(doc, idempotencyKey);
        }
    }

    /**
     * Query paginated notifications for authenticated user with strict tenant and plant scoping.
     */
    public Map<String, Object> getUserNotifications(String userId, String tenantId, String plantId, Boolean unreadOnly, int page, int limit) {
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("User ID is required to fetch notifications");
        }

        Query query = new Query();
        query.addCriteria(Criteria.where("recipientUserId").regex("^" + userId.trim() + "$", "i"));

        if (tenantId != null && !tenantId.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("tenantId").is(tenantId.trim()),
                    Criteria.where("tenantId").exists(false),
                    Criteria.where("tenantId").is(null)
            ));
        }

        if (plantId != null && !plantId.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("plantId").is(plantId.trim()),
                    Criteria.where("plantId").exists(false),
                    Criteria.where("plantId").is(null)
            ));
        }

        if (Boolean.TRUE.equals(unreadOnly)) {
            query.addCriteria(Criteria.where("isRead").is(false));
        }

        long totalCount = mongoTemplate.count(query, NOTIFICATIONS_COLLECTION);

        int skip = Math.max(0, (page - 1) * limit);
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        query.skip(skip).limit(limit);

        List<NotificationDocument> items = mongoTemplate.find(query, NotificationDocument.class, NOTIFICATIONS_COLLECTION);
        long unreadCount = getUnreadCount(userId, tenantId, plantId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", totalCount);
        result.put("unreadCount", unreadCount);
        result.put("page", page);
        result.put("limit", limit);
        result.put("totalPages", (int) Math.ceil((double) totalCount / Math.max(1, limit)));

        return result;
    }

    /**
     * Get count of unread notifications for the user with tenant and plant scoping.
     */
    public long getUnreadCount(String userId, String tenantId, String plantId) {
        if (userId == null || userId.isBlank()) return 0;

        Query query = new Query();
        query.addCriteria(Criteria.where("recipientUserId").regex("^" + userId.trim() + "$", "i"));
        query.addCriteria(Criteria.where("isRead").is(false));

        if (tenantId != null && !tenantId.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("tenantId").is(tenantId.trim()),
                    Criteria.where("tenantId").exists(false),
                    Criteria.where("tenantId").is(null)
            ));
        }

        if (plantId != null && !plantId.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("plantId").is(plantId.trim()),
                    Criteria.where("plantId").exists(false),
                    Criteria.where("plantId").is(null)
            ));
        }

        return mongoTemplate.count(query, NOTIFICATIONS_COLLECTION);
    }

    /**
     * Mark a single notification as read with server-side ownership authorization and tenant/plant verification.
     */
    public Map<String, Object> markAsRead(String notificationId, String userId, String tenantId, String plantId) {
        if (notificationId == null || notificationId.isBlank()) {
            throw new BusinessException("Notification ID is required");
        }

        Query query = new Query(Criteria.where("notificationId").is(notificationId.trim()));
        NotificationDocument doc = mongoTemplate.findOne(query, NotificationDocument.class, NOTIFICATIONS_COLLECTION);

        if (doc == null) {
            throw new BusinessException("Notification not found for id: " + notificationId);
        }

        // Verify recipient ownership
        if (userId != null && !userId.isBlank() && !userId.equalsIgnoreCase("SYSTEM")) {
            if (doc.getRecipientUserId() != null && !doc.getRecipientUserId().equalsIgnoreCase(userId.trim())) {
                throw new UnauthorizedException("You are not authorized to mark another user's notification as read");
            }
        }

        // Verify tenant isolation
        if (tenantId != null && !tenantId.isBlank()) {
            if (doc.getTenantId() != null && !doc.getTenantId().isBlank() && !doc.getTenantId().equalsIgnoreCase(tenantId.trim())) {
                throw new UnauthorizedException("Notification belongs to another organization");
            }
        }

        Date now = Date.from(Instant.now());
        doc.setIsRead(true);
        doc.setReadAt(now);

        mongoTemplate.save(doc, NOTIFICATIONS_COLLECTION);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("notificationId", doc.getNotificationId());
        res.put("isRead", true);
        res.put("readAt", now);
        return res;
    }

    /**
     * Mark all unread notifications for user as read within current plant/tenant scope.
     */
    public Map<String, Object> markAllAsRead(String userId, String tenantId, String plantId) {
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("User ID is required");
        }

        Query query = new Query();
        query.addCriteria(Criteria.where("recipientUserId").regex("^" + userId.trim() + "$", "i"));
        query.addCriteria(Criteria.where("isRead").is(false));

        if (tenantId != null && !tenantId.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("tenantId").is(tenantId.trim()),
                    Criteria.where("tenantId").exists(false),
                    Criteria.where("tenantId").is(null)
            ));
        }

        if (plantId != null && !plantId.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("plantId").is(plantId.trim()),
                    Criteria.where("plantId").exists(false),
                    Criteria.where("plantId").is(null)
            ));
        }

        Date now = Date.from(Instant.now());
        Update update = new Update().set("isRead", true).set("readAt", now);

        var updateResult = mongoTemplate.updateMulti(query, update, NOTIFICATIONS_COLLECTION);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("updatedCount", updateResult.getModifiedCount());
        res.put("userId", userId);
        if (plantId != null) res.put("plantId", plantId);
        return res;
    }

    private void persistNotificationIfUnique(NotificationDocument doc, String idempotencyKey) {
        try {
            Query existQ = new Query(Criteria.where("idempotencyKey").is(idempotencyKey));
            if (!mongoTemplate.exists(existQ, NOTIFICATIONS_COLLECTION)) {
                mongoTemplate.insert(doc, NOTIFICATIONS_COLLECTION);
                log.info("Emitted notification [{}] for recipient={} event={}",
                        doc.getNotificationId(), doc.getRecipientUserId(), doc.getEventCode());
            } else {
                log.debug("Suppressed duplicate notification for idempotencyKey={}", idempotencyKey);
            }
        } catch (Exception ex) {
            log.warn("Failed to persist notification for recipient {}: {}", doc.getRecipientUserId(), ex.getMessage());
        }
    }

    private String encode(String val) {
        if (val == null) return "";
        try {
            return java.net.URLEncoder.encode(val, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return val;
        }
    }
}
