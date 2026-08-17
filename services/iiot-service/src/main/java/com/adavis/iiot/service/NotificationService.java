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
     * Authoritatively emit role-aware and scoped workflow notifications.
     */
    public void emitWorkflowTransitionNotification(String tenantId, String plantId, String batchNo,
                                                   String lotNo, String equipmentCode, String previousStatus,
                                                   String targetStatus, String actorUserId, String userRole,
                                                   String comments, Document stage) {
        try {
            String normTarget = targetStatus != null ? targetStatus.toUpperCase(Locale.ROOT).trim() : "";
            Date now = Date.from(Instant.now());
            String entityId = batchNo + ":" + (equipmentCode != null ? equipmentCode : "STAGE");

            Set<String> recipients = new LinkedHashSet<>();
            String eventCode = "WORKFLOW_TRANSITION";
            String title = "Batch Workflow Update";
            String message = "Workflow update on batch " + batchNo;
            String severity = "INFO";

            switch (normTarget) {
                case "UNDER_REVIEW":
                    eventCode = "BATCH_SUBMITTED_FOR_REVIEW";
                    title = "Batch Stage Submitted for QA Review";
                    message = String.format("Batch %s (Lot: %s, Stage: %s) submitted for QA review by %s.",
                            batchNo, lotNo != null ? lotNo : "-", equipmentCode != null ? equipmentCode : "-", actorUserId);
                    severity = "INFO";
                    recipients = recipientResolver.resolveQAReviewers(tenantId, plantId, actorUserId);
                    break;

                case "REVIEWER_REVIEWED":
                    eventCode = "BATCH_REVIEWED";
                    title = "Batch Stage Verified & Ready for Approval";
                    message = String.format("Batch %s (Lot: %s, Stage: %s) verified by QA Reviewer %s and awaiting release approval.",
                            batchNo, lotNo != null ? lotNo : "-", equipmentCode != null ? equipmentCode : "-", actorUserId);
                    severity = "INFO";
                    String assignedSupervisor = stage != null ? stage.getString("supervisorName") : null;
                    recipients = recipientResolver.resolveShiftSupervisors(tenantId, plantId, assignedSupervisor, actorUserId);
                    break;

                case "APPROVED":
                    eventCode = "BATCH_APPROVED";
                    title = "Batch Stage Approved";
                    message = String.format("Batch %s (Lot: %s, Stage: %s) has been approved for release by Supervisor %s.",
                            batchNo, lotNo != null ? lotNo : "-", equipmentCode != null ? equipmentCode : "-", actorUserId);
                    severity = "SUCCESS";
                    recipients = recipientResolver.resolveBatchParticipants(tenantId, plantId, batchNo, equipmentCode, stage, actorUserId);
                    break;

                case "REJECTED":
                    eventCode = "BATCH_REJECTED";
                    title = "Batch Stage Rejected";
                    String reasonText = (comments != null && !comments.isBlank()) ? comments.trim() : "Mandatory rejection reason not specified.";
                    message = String.format("Batch %s (Lot: %s, Stage: %s) was REJECTED by Supervisor %s. Reason: %s",
                            batchNo, lotNo != null ? lotNo : "-", equipmentCode != null ? equipmentCode : "-", actorUserId, reasonText);
                    severity = "ERROR";
                    recipients = recipientResolver.resolveBatchParticipants(tenantId, plantId, batchNo, equipmentCode, stage, actorUserId);
                    break;

                case "PENDING":
                    if ("REJECTED".equalsIgnoreCase(previousStatus)) {
                        eventCode = "BATCH_REWORKED";
                        title = "Batch Stage Reworked";
                        String reworkText = (comments != null && !comments.isBlank()) ? comments.trim() : "None";
                        message = String.format("Batch %s (Lot: %s, Stage: %s) was REWORKED by Operator %s and returned to PENDING. Remarks: %s",
                                batchNo, lotNo != null ? lotNo : "-", equipmentCode != null ? equipmentCode : "-", actorUserId, reworkText);
                        severity = "INFO";
                        recipients = recipientResolver.resolveQAReviewers(tenantId, plantId, actorUserId);
                    } else {
                        return;
                    }
                    break;

                default:
                    log.debug("No automated notifications configured for transition to state: {}", normTarget);
                    return;
            }

            if (recipients.isEmpty()) {
                log.warn("No eligible recipients resolved for workflow event {} on batch={} stage={} tenant={}",
                        eventCode, batchNo, equipmentCode, tenantId);
                return;
            }

            for (String recipient : recipients) {
                if (recipient == null || recipient.isBlank() || recipient.equalsIgnoreCase("SYSTEM")) continue;

                String notifId = "NOTIF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
                String idempotencyKey = String.format("%s:%s:%s:%s:%s",
                        eventCode, batchNo, equipmentCode != null ? equipmentCode : "ALL", normTarget, recipient.toLowerCase(Locale.ROOT));

                NotificationDocument doc = NotificationDocument.builder()
                        .notificationId(notifId)
                        .recipientUserId(recipient)
                        .tenantId(tenantId != null ? tenantId : "TNT-0001")
                        .plantId(plantId != null ? plantId : "PLANT-0001")
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
                        .isRead(false)
                        .createdAt(now)
                        .actorUserId(actorUserId)
                        .idempotencyKey(idempotencyKey)
                        .build();

                try {
                    // Check if already created recently for duplicate suppression
                    Query existQ = new Query(Criteria.where("idempotencyKey").is(idempotencyKey));
                    if (!mongoTemplate.exists(existQ, NOTIFICATIONS_COLLECTION)) {
                        mongoTemplate.insert(doc, NOTIFICATIONS_COLLECTION);
                        log.info("Created notification [{}] for recipient={} event={} batch={}",
                                notifId, recipient, eventCode, batchNo);
                    } else {
                        log.debug("Suppressed duplicate notification for idempotencyKey={}", idempotencyKey);
                    }
                } catch (Exception ex) {
                    log.warn("Failed to persist notification for recipient {}: {}", recipient, ex.getMessage());
                }
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
        String notifId = "NOTIF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String idempotencyKey = String.format("ASSIGNMENT:%s:%s:%s",
                batchNo, equipmentCode != null ? equipmentCode : "ALL", assignedUserId.toLowerCase(Locale.ROOT));

        NotificationDocument doc = NotificationDocument.builder()
                .notificationId(notifId)
                .recipientUserId(assignedUserId)
                .tenantId(tenantId != null ? tenantId : "TNT-0001")
                .plantId(plantId != null ? plantId : "PLANT-0001")
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
                .isRead(false)
                .createdAt(now)
                .actorUserId(actorUserId)
                .idempotencyKey(idempotencyKey)
                .build();

        try {
            Query existQ = new Query(Criteria.where("idempotencyKey").is(idempotencyKey));
            if (!mongoTemplate.exists(existQ, NOTIFICATIONS_COLLECTION)) {
                mongoTemplate.insert(doc, NOTIFICATIONS_COLLECTION);
            }
        } catch (Exception ex) {
            log.warn("Failed to persist assignment notification: {}", ex.getMessage());
        }
    }

    /**
     * Query paginated notifications for the authenticated user.
     */
    public Map<String, Object> getUserNotifications(String userId, String tenantId, Boolean unreadOnly, int page, int limit) {
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("User ID is required to fetch notifications");
        }

        Query query = new Query();
        query.addCriteria(Criteria.where("recipientUserId").regex("^" + userId.trim() + "$", "i"));

        if (tenantId != null && !tenantId.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("tenantId").is(tenantId),
                    Criteria.where("tenantId").exists(false),
                    Criteria.where("tenantId").is(null)
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

        long unreadCount = getUnreadCount(userId, tenantId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", totalCount);
        result.put("unreadCount", unreadCount);
        result.put("page", page);
        result.put("limit", limit);
        result.put("totalPages", (int) Math.ceil((double) totalCount / limit));

        return result;
    }

    /**
     * Get count of unread notifications for the user.
     */
    public long getUnreadCount(String userId, String tenantId) {
        if (userId == null || userId.isBlank()) return 0;

        Query query = new Query();
        query.addCriteria(Criteria.where("recipientUserId").regex("^" + userId.trim() + "$", "i"));
        query.addCriteria(Criteria.where("isRead").is(false));

        if (tenantId != null && !tenantId.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("tenantId").is(tenantId),
                    Criteria.where("tenantId").exists(false),
                    Criteria.where("tenantId").is(null)
            ));
        }

        return mongoTemplate.count(query, NOTIFICATIONS_COLLECTION);
    }

    /**
     * Mark a single notification as read with server-side ownership authorization.
     */
    public Map<String, Object> markAsRead(String notificationId, String userId) {
        if (notificationId == null || notificationId.isBlank()) {
            throw new BusinessException("Notification ID is required");
        }

        Query query = new Query(Criteria.where("notificationId").is(notificationId));
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
     * Mark all unread notifications for user as read.
     */
    public Map<String, Object> markAllAsRead(String userId, String tenantId) {
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("User ID is required");
        }

        Query query = new Query();
        query.addCriteria(Criteria.where("recipientUserId").regex("^" + userId.trim() + "$", "i"));
        query.addCriteria(Criteria.where("isRead").is(false));

        if (tenantId != null && !tenantId.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("tenantId").is(tenantId),
                    Criteria.where("tenantId").exists(false),
                    Criteria.where("tenantId").is(null)
            ));
        }

        Date now = Date.from(Instant.now());
        Update update = new Update().set("isRead", true).set("readAt", now);

        var updateResult = mongoTemplate.updateMulti(query, update, NOTIFICATIONS_COLLECTION);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("updatedCount", updateResult.getModifiedCount());
        res.put("userId", userId);
        return res;
    }
}
