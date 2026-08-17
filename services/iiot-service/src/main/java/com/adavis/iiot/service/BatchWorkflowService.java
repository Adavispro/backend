package com.adavis.iiot.service;

import com.adavis.common.exception.BusinessException;
import com.adavis.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Centralized workflow state machine for batch stage approvals.
 *
 * Canonical workflow stages:
 *   PENDING → UNDER_REVIEW → REVIEWER_REVIEWED → APPROVED
 *                                               → REJECTED
 *
 * Roles and allowed transitions:
 *   PRODUCTION_OPERATOR  → can transition PENDING → UNDER_REVIEW (send for review)
 *   SHIFT_SUPERVISOR     → can transition UNDER_REVIEW → REVIEWER_REVIEWED (send for approval)
 *                          OR UNDER_REVIEW → APPROVED / REJECTED (direct approve/reject)
 *   PLATFORM_SUPER_ADMIN → can perform any transition
 *
 * Every transition records: actor, previous state, new state, timestamp, comments, and emits an audit event.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchWorkflowService {

    private final MongoTemplate mongoTemplate;
    private final NotificationService notificationService;

    private static final String BATCH_SUMMARY_COLLECTION = "iiot_batch_summary";
    private static final String AUDIT_COLLECTION = "iiot_workflow_audit_trail";

    // Valid workflow states
    public static final String STATE_PENDING = "PENDING";
    public static final String STATE_UNDER_REVIEW = "UNDER_REVIEW";
    public static final String STATE_REVIEWER_REVIEWED = "REVIEWER_REVIEWED";
    public static final String STATE_APPROVED = "APPROVED";
    public static final String STATE_REJECTED = "REJECTED";

    // Role codes that map to permission groups
    public static final String ROLE_OPERATOR = "PRODUCTION_OPERATOR";
    public static final String ROLE_REVIEWER = "QA_REVIEWER";
    public static final String ROLE_SUPERVISOR = "SHIFT_SUPERVISOR";
    public static final String ROLE_ADMIN = "PLATFORM_SUPER_ADMIN";

    // Allowed transitions: Map<currentState, Map<targetState, Set<allowedRoles>>>
    private static final Map<String, Map<String, Set<String>>> TRANSITION_RULES;

    static {
        Map<String, Map<String, Set<String>>> rules = new LinkedHashMap<>();

        // From PENDING: only operator can send for review
        rules.put(STATE_PENDING, Map.of(
                STATE_UNDER_REVIEW, Set.of(ROLE_OPERATOR, "OPERATOR", ROLE_ADMIN, "PLATFORM_ADMIN", "SUPER_ADMIN")
        ));

        // From UNDER_REVIEW: only reviewer can send to reviewer_reviewed
        rules.put(STATE_UNDER_REVIEW, Map.of(
                STATE_REVIEWER_REVIEWED, Set.of(ROLE_REVIEWER, "REVIEWER", ROLE_ADMIN, "PLATFORM_ADMIN", "SUPER_ADMIN")
        ));

        // From REVIEWER_REVIEWED: supervisor can approve or reject
        rules.put(STATE_REVIEWER_REVIEWED, Map.of(
                STATE_APPROVED, Set.of(ROLE_SUPERVISOR, "SUPERVISOR", ROLE_ADMIN, "PLATFORM_ADMIN", "SUPER_ADMIN"),
                STATE_REJECTED, Set.of(ROLE_SUPERVISOR, "SUPERVISOR", ROLE_ADMIN, "PLATFORM_ADMIN", "SUPER_ADMIN")
        ));

        // From REJECTED: authorized operator can rework to PENDING
        rules.put(STATE_REJECTED, Map.of(
                STATE_PENDING, Set.of(ROLE_OPERATOR, "OPERATOR", ROLE_ADMIN, "PLATFORM_ADMIN", "SUPER_ADMIN")
        ));

        TRANSITION_RULES = Collections.unmodifiableMap(rules);
    }

    /**
     * Execute a workflow transition on a batch stage.
     *
     * @param userId        The acting user's ID (from X-User-Id gateway header)
     * @param userRoleCode  The acting user's role code (resolved from permission context)
     * @param tenantId      The tenant scope
     * @param batchNo       Batch number
     * @param lotNo         Lot number
     * @param equipmentCode Equipment code identifying the stage
     * @param targetStatus  The desired target workflow state
     * @param comments      Optional comments/reason
     * @param supervisorName Optional supervisor name for display
     * @return Updated batch summary document as map
     */
    public Map<String, Object> executeTransition(
            String userId,
            String userRoleCode,
            String tenantId,
            String batchNo,
            String lotNo,
            String equipmentCode,
            String targetStatus,
            String comments,
            String supervisorName) {

        // Normalize inputs
        targetStatus = targetStatus.toUpperCase(Locale.ROOT).trim();
        userId = userId != null ? userId.trim() : "SYSTEM";
        userRoleCode = userRoleCode != null ? userRoleCode.toUpperCase(Locale.ROOT).trim() : "";
        comments = comments != null ? comments.trim() : "";

        // If role not provided, resolve from user's group assignment in MongoDB
        if (userRoleCode.isBlank()) {
            userRoleCode = resolveUserRoleCode(userId);
        }

        // Find the batch summary
        Query query = new Query();
        if (tenantId != null && !tenantId.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("tenantId").is(tenantId),
                    Criteria.where("tenantId").exists(false),
                    Criteria.where("tenantId").is(null)
            ));
        }
        query.addCriteria(Criteria.where("batchNo").is(batchNo));
        query.addCriteria(Criteria.where("lotNo").is(lotNo));

        Document summary = mongoTemplate.findOne(query, Document.class, BATCH_SUMMARY_COLLECTION);
        if (summary == null) {
            Query fallback = new Query(Criteria.where("batchNo").is(batchNo).and("lotNo").is(lotNo));
            summary = mongoTemplate.findOne(fallback, Document.class, BATCH_SUMMARY_COLLECTION);
        }

        if (summary == null) {
            throw new BusinessException("Batch summary not found for batchNo=" + batchNo
                    + ", lotNo=" + lotNo + ", equipmentCode=" + equipmentCode);
        }

        @SuppressWarnings("unchecked")
        List<Document> stages = (List<Document>) summary.get("stages");
        if (stages == null || stages.isEmpty()) {
            throw new BusinessException("No stages available in batch summary");
        }

        // Find the target stage
        Document targetStage = null;
        for (Document stage : stages) {
            String stageCode = stage.getString("equipmentCode");
            String stageId = stage.getString("equipmentId");
            if (equipmentCode.equalsIgnoreCase(stageCode != null ? stageCode : "")
                    || equipmentCode.equalsIgnoreCase(stageId != null ? stageId : "")) {
                targetStage = stage;
                break;
            }
        }

        if (targetStage == null) {
            targetStage = stages.get(0);
        }

        // Get current approval state
        Document approval = targetStage.get("approval", Document.class);
        String currentStatus = STATE_PENDING;
        if (approval != null) {
            String s = approval.getString("status");
            if (s != null && !s.isBlank()) {
                currentStatus = s.toUpperCase(Locale.ROOT);
            }
        }

        // === VALIDATE TRANSITION ===
        if (STATE_REJECTED.equals(targetStatus) && (comments == null || comments.trim().isEmpty())) {
            throw new BusinessException("Rejection comments are mandatory. A valid rejection reason must be provided.");
        }
        validateTransition(currentStatus, targetStatus, userRoleCode, userId, batchNo, equipmentCode);

        // === APPLY TRANSITION ===
        Date now = Date.from(Instant.now());
        if (approval == null) {
            approval = new Document();
        }

        String previousStatus = currentStatus;
        approval.put("status", targetStatus);
        approval.put("comments", comments);
        approval.put("previousStatus", previousStatus);
        approval.put("transitionedBy", userId);
        approval.put("transitionedAt", now);

        if (STATE_APPROVED.equals(targetStatus)) {
            approval.put("approvedBy", userId);
            approval.put("approvedAt", now);
        } else if (STATE_REJECTED.equals(targetStatus)) {
            approval.put("rejectedBy", userId);
            approval.put("rejectedAt", now);
            approval.put("rejectionReason", comments);
            approval.put("approvedBy", userId);
            approval.put("approvedAt", now);
        } else if (STATE_UNDER_REVIEW.equals(targetStatus)) {
            approval.put("approvedBy", "");
            approval.put("approvedAt", null);
            approval.put("requestedBy", userId);
            approval.put("requestedAt", now);
            targetStage.put("requestedBy", userId);
            targetStage.put("requestedAt", now);
            if (supervisorName != null && !supervisorName.isBlank()) {
                targetStage.put("supervisorName", supervisorName.trim());
            }
        } else if (STATE_REVIEWER_REVIEWED.equals(targetStatus)) {
            approval.put("reviewedBy", userId);
            approval.put("reviewedAt", now);
        } else if (STATE_PENDING.equals(targetStatus)) {
            approval.put("approvedBy", "");
            approval.put("approvedAt", null);
            approval.put("reviewedBy", "");
            approval.put("reviewedAt", null);
            approval.put("reworkedBy", userId);
            approval.put("reworkedAt", now);
            approval.put("reworkComments", comments);
        }

        targetStage.put("approval", approval);

        // Derive overall batch status
        summary.put("overallStatus", deriveBatchOverallStatus(stages));
        summary.put("updatedAt", now);

        // Save
        Document saved = mongoTemplate.save(summary, BATCH_SUMMARY_COLLECTION);

        String plantId = summary.getString("plantId");

        // Emit audit event
        emitWorkflowAuditEvent(tenantId, plantId, batchNo, lotNo, equipmentCode,
                previousStatus, targetStatus, userId, userRoleCode, comments, now);

        // Emit role-aware workflow notifications
        notificationService.emitWorkflowTransitionNotification(
                tenantId, plantId, batchNo, lotNo, equipmentCode,
                previousStatus, targetStatus, userId, userRoleCode, comments, targetStage);

        // Emit assignment notification if a new supervisor was assigned
        if (supervisorName != null && !supervisorName.isBlank() && STATE_UNDER_REVIEW.equals(targetStatus)) {
            notificationService.emitAssignmentNotification(
                    tenantId, plantId, batchNo, lotNo, equipmentCode, supervisorName, userId);
        }

        log.info("Workflow transition: batch={} stage={} [{}] → [{}] by user={} role={}",
                batchNo, equipmentCode, previousStatus, targetStatus, userId, userRoleCode);

        return toMap(saved);
    }

    /**
     * Validate that the requested transition is legal according to the state machine rules.
     */
    private void validateTransition(String currentStatus, String targetStatus,
                                    String userRoleCode, String userId,
                                    String batchNo, String equipmentCode) {
        // Check if transition from currentStatus is defined
        Map<String, Set<String>> allowedTargets = TRANSITION_RULES.get(currentStatus);
        if (allowedTargets == null) {
            throw new BusinessException(
                    "No transitions allowed from state '" + currentStatus
                            + "' for batch=" + batchNo + " stage=" + equipmentCode
                            + ". Current state is terminal.");
        }

        // Check if targetStatus is a valid target from currentStatus
        Set<String> allowedRoles = allowedTargets.get(targetStatus);
        if (allowedRoles == null) {
            throw new BusinessException(
                    "Invalid transition: [" + currentStatus + "] → [" + targetStatus
                            + "] is not permitted for batch=" + batchNo + " stage=" + equipmentCode
                            + ". Valid targets from [" + currentStatus + "]: " + allowedTargets.keySet());
        }

        // Check if user's role is authorized for this transition
        if (!allowedRoles.contains(userRoleCode) && !ROLE_ADMIN.equals(userRoleCode)) {
            throw new UnauthorizedException(
                    "User '" + userId + "' with role '" + userRoleCode
                            + "' is not authorized for transition [" + currentStatus + "] → [" + targetStatus
                            + "]. Required roles: " + allowedRoles);
        }
    }

    /**
     * Derive overall batch status from all stage approval statuses.
     */
    private String deriveBatchOverallStatus(List<Document> stages) {
        boolean hasUnderReview = false;
        boolean hasReviewed = false;
        boolean allApprovedOrNotStarted = true;

        for (Document stage : stages) {
            String executionStatus = "";
            Object execObj = stage.get("executionStatus");
            if (execObj != null) {
                executionStatus = execObj.toString().toUpperCase(Locale.ROOT);
            }

            Document approval = stage.get("approval", Document.class);
            String approvalStatus = STATE_PENDING;
            if (approval != null) {
                String s = approval.getString("status");
                if (s != null && !s.isBlank()) {
                    approvalStatus = s.toUpperCase(Locale.ROOT);
                }
            }

            if (STATE_REJECTED.equals(approvalStatus)) {
                return STATE_REJECTED;
            }
            if (STATE_UNDER_REVIEW.equals(approvalStatus)) {
                hasUnderReview = true;
            }
            if (STATE_REVIEWER_REVIEWED.equals(approvalStatus)) {
                hasReviewed = true;
            }
            if (!"NOT_STARTED".equals(executionStatus)
                    && !STATE_APPROVED.equals(approvalStatus)
                    && !"RELEASED".equals(approvalStatus)) {
                allApprovedOrNotStarted = false;
            }
        }

        if (allApprovedOrNotStarted) {
            return STATE_APPROVED;
        }
        if (hasReviewed) {
            return STATE_REVIEWER_REVIEWED;
        }
        if (hasUnderReview) {
            return STATE_UNDER_REVIEW;
        }
        return "IN_PROGRESS";
    }

    /**
     * Resolve the user's role code by looking up their group assignment in MongoDB.
     * Group codes map to workflow role codes:
     *   GRP with groupCode "PRODUCTION_OPERATOR" → PRODUCTION_OPERATOR
     *   GRP with groupCode "SHIFT_SUPERVISOR"    → SHIFT_SUPERVISOR
     *   GRP with groupCode "PLATFORM_SUPER_ADMIN"→ PLATFORM_SUPER_ADMIN
     */
    private String resolveUserRoleCode(String userId) {
        try {
            // Find user's group assignments
            Query groupQuery = new Query(Criteria.where("userId").regex("^" + userId + "$", "i")
                    .and("isActive").is(true));
            List<Document> assignments = mongoTemplate.find(groupQuery, Document.class,
                    "mdm_user_assignments_to_user_groups");

            if (assignments.isEmpty()) {
                log.warn("No group assignments found for userId={}", userId);
                return "";
            }

            // Get the group codes and role assignments for assigned groups
            for (Document assignment : assignments) {
                String groupId = assignment.getString("groupId");
                if (groupId == null) continue;

                Query grpQuery = new Query(Criteria.where("groupId").is(groupId));
                Document group = mongoTemplate.findOne(grpQuery, Document.class, "mdm_user_groups");
                if (group != null) {
                    String groupCode = group.getString("groupCode");
                    if (groupCode != null) {
                        String code = groupCode.toUpperCase(Locale.ROOT);
                        if (code.contains("SUPER_ADMIN") || code.contains("PLATFORM_ADMIN")) return ROLE_ADMIN;
                        if (code.contains("SUPERVISOR")) return ROLE_SUPERVISOR;
                        if (code.contains("REVIEWER") || code.contains("QA")) return ROLE_REVIEWER;
                        if (code.contains("OPERATOR")) return ROLE_OPERATOR;
                    }
                }

                // Check role assignments for this group
                Query roleAssignQ = new Query(Criteria.where("groupId").is(groupId).and("isActive").is(true));
                List<Document> roleAssignments = mongoTemplate.find(roleAssignQ, Document.class, "mdm_role_assignments_to_user_groups");
                for (Document roleAssign : roleAssignments) {
                    String roleId = roleAssign.getString("roleId");
                    if (roleId == null) continue;
                    Document roleDoc = mongoTemplate.findOne(new Query(Criteria.where("roleId").is(roleId)), Document.class, "mdm_roles");
                    if (roleDoc != null) {
                        String roleCode = roleDoc.getString("roleCode");
                        if (roleCode != null) {
                            String r = roleCode.toUpperCase(Locale.ROOT);
                            if (r.contains("SUPER_ADMIN") || r.contains("PLATFORM_ADMIN")) return ROLE_ADMIN;
                            if (r.contains("SUPERVISOR")) return ROLE_SUPERVISOR;
                            if (r.contains("REVIEWER") || r.contains("QA")) return ROLE_REVIEWER;
                            if (r.contains("OPERATOR")) return ROLE_OPERATOR;
                        }
                    }
                }
            }

            log.warn("Could not resolve role code for userId={}", userId);
            return "";
        } catch (Exception e) {
            log.error("Failed to resolve role for userId={}: {}", userId, e.getMessage());
            return "";
        }
    }

    /**
     * Retrieve the workflow audit trail history for a batch stage.
     */
    public List<Map<String, Object>> getWorkflowAuditTrail(String batchNo, String lotNo, String equipmentCode, String tenantId) {
        Query query = new Query();
        if (tenantId != null && !tenantId.isBlank()) {
            query.addCriteria(Criteria.where("tenantId").is(tenantId));
        }
        if (batchNo != null && !batchNo.isBlank()) {
            query.addCriteria(Criteria.where("batchNo").is(batchNo));
        }
        if (lotNo != null && !lotNo.isBlank()) {
            query.addCriteria(Criteria.where("lotNo").is(lotNo));
        }
        if (equipmentCode != null && !equipmentCode.isBlank()) {
            query.addCriteria(Criteria.where("equipmentCode").is(equipmentCode));
        }
        query.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "timestamp"));
        query.limit(100);

        List<Document> docs = mongoTemplate.find(query, Document.class, AUDIT_COLLECTION);
        return docs.stream().map(this::toMap).toList();
    }

    /**
     * Record an audit trail entry for every workflow transition.
     */
    private void emitWorkflowAuditEvent(String tenantId, String plantId, String batchNo, String lotNo,
                                         String equipmentCode, String previousStatus,
                                         String newStatus, String userId, String userRole,
                                         String comments, Date timestamp) {
        Document auditEvent = new Document();
        auditEvent.put("tenantId", tenantId);
        auditEvent.put("plantId", plantId);
        auditEvent.put("batchNo", batchNo);
        auditEvent.put("lotNo", lotNo);
        auditEvent.put("equipmentCode", equipmentCode);
        auditEvent.put("previousStatus", previousStatus);
        auditEvent.put("newStatus", newStatus);

        String actionName = previousStatus + "_TO_" + newStatus;
        if (STATE_REJECTED.equals(newStatus)) {
            actionName = "REJECT";
        } else if (STATE_PENDING.equals(newStatus) && STATE_REJECTED.equals(previousStatus)) {
            actionName = "REWORK";
        } else if (STATE_APPROVED.equals(newStatus)) {
            actionName = "APPROVE";
        } else if (STATE_UNDER_REVIEW.equals(newStatus)) {
            actionName = "SEND_FOR_REVIEW";
        } else if (STATE_REVIEWER_REVIEWED.equals(newStatus)) {
            actionName = "SEND_FOR_APPROVAL";
        }

        auditEvent.put("action", actionName);
        auditEvent.put("userId", userId);
        auditEvent.put("userRole", userRole);
        auditEvent.put("comments", comments);
        auditEvent.put("timestamp", timestamp);
        auditEvent.put("createdAt", timestamp);

        try {
            mongoTemplate.insert(auditEvent, AUDIT_COLLECTION);
        } catch (Exception e) {
            log.error("Failed to persist workflow audit event for batch={} stage={}: {}",
                    batchNo, equipmentCode, e.getMessage());
        }
    }

    /**
     * Retrieve active eligible assignees for a given target workflow state within tenant/plant scope.
     */
    /**
     * Retrieve active eligible assignees for a given target workflow state within tenant/plant scope.
     */
    public List<Map<String, Object>> getEligibleAssignees(String targetStatus, String tenantId, String plantId) {
        String status = (targetStatus != null ? targetStatus : "").toUpperCase(Locale.ROOT).trim();
        boolean isReviewerTarget = "UNDER_REVIEW".equals(status);
        boolean isSupervisorTarget = "REVIEWER_REVIEWED".equals(status) || "APPROVED".equals(status);

        // Find all groups in the tenant
        Query groupQ = new Query();
        if (tenantId != null && !tenantId.isBlank()) {
            groupQ.addCriteria(Criteria.where("tenantId").is(tenantId));
        }
        List<Document> groups = mongoTemplate.find(groupQ, Document.class, "mdm_user_groups");
        if (groups.isEmpty() && tenantId != null && !tenantId.isBlank()) {
            // Fallback for groups without tenantId if global
            groups = mongoTemplate.find(new Query(Criteria.where("tenantId").exists(false)), Document.class, "mdm_user_groups");
        }

        Set<String> matchingGroupIds = new HashSet<>();

        for (Document group : groups) {
            String groupId = group.getString("groupId");
            String groupCode = (group.getString("groupCode") != null ? group.getString("groupCode") : "").toUpperCase(Locale.ROOT);
            if (groupId == null) continue;

            if (isReviewerTarget && (groupCode.contains("REVIEWER") || groupCode.contains("QA"))) {
                matchingGroupIds.add(groupId);
            } else if (isSupervisorTarget && groupCode.contains("SUPERVISOR")) {
                matchingGroupIds.add(groupId);
            }
        }

        // Also check role assignments to user groups
        Query roleQ = new Query();
        if (tenantId != null && !tenantId.isBlank()) {
            roleQ.addCriteria(Criteria.where("tenantId").is(tenantId));
        }
        List<Document> roles = mongoTemplate.find(roleQ, Document.class, "mdm_roles");
        if (roles.isEmpty() && tenantId != null && !tenantId.isBlank()) {
            roles = mongoTemplate.find(new Query(Criteria.where("tenantId").exists(false)), Document.class, "mdm_roles");
        }

        Set<String> matchingRoleIds = new HashSet<>();
        for (Document role : roles) {
            String roleId = role.getString("roleId");
            String roleCode = (role.getString("roleCode") != null ? role.getString("roleCode") : "").toUpperCase(Locale.ROOT);
            if (roleId == null) continue;

            if (isReviewerTarget && (roleCode.contains("REVIEWER") || roleCode.contains("QA"))) {
                matchingRoleIds.add(roleId);
            } else if (isSupervisorTarget && roleCode.contains("SUPERVISOR")) {
                matchingRoleIds.add(roleId);
            }
        }

        if (!matchingRoleIds.isEmpty()) {
            Query roleGrpQ = new Query(Criteria.where("roleId").in(matchingRoleIds).and("isActive").is(true));
            List<Document> roleGrps = mongoTemplate.find(roleGrpQ, Document.class, "mdm_role_assignments_to_user_groups");
            for (Document rg : roleGrps) {
                String gid = rg.getString("groupId");
                if (gid != null) matchingGroupIds.add(gid);
            }
        }

        if (matchingGroupIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Find users assigned to these groups
        Query userAssignQ = new Query(Criteria.where("groupId").in(matchingGroupIds).and("isActive").is(true));
        List<Document> userAssigns = mongoTemplate.find(userAssignQ, Document.class, "mdm_user_assignments_to_user_groups");
        Set<String> eligibleUserIds = new LinkedHashSet<>();
        for (Document ua : userAssigns) {
            String uid = ua.getString("userId");
            if (uid != null && !uid.isBlank()) eligibleUserIds.add(uid);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String uid : eligibleUserIds) {
            Query userQ = new Query(Criteria.where("userId").regex("^" + uid + "$", "i"));
            Document userProfile = mongoTemplate.findOne(userQ, Document.class, "mdm_user_profiles");
            if (userProfile == null) {
                userProfile = mongoTemplate.findOne(userQ, Document.class, "auth_users");
            }
            if (userProfile != null) {
                Boolean isActive = userProfile.getBoolean("isActive");
                if (isActive != null && !isActive) continue;

                // Validate tenant match if tenantId provided
                String userTenant = userProfile.getString("tenantId");
                if (tenantId != null && !tenantId.isBlank() && userTenant != null && !userTenant.isBlank()) {
                    if (!tenantId.equalsIgnoreCase(userTenant)) {
                        continue;
                    }
                }

                // Validate plant assignment if plantId provided
                if (plantId != null && !plantId.isBlank()) {
                    Query plantAssignQ = new Query(Criteria.where("userId").regex("^" + uid + "$", "i")
                            .and("plantId").is(plantId)
                            .and("isActive").is(true));
                    long assignCount = mongoTemplate.count(plantAssignQ, "mdm_user_assignments");
                    if (assignCount == 0) {
                        // User is not assigned to this plant
                        continue;
                    }
                }

                Map<String, Object> map = new LinkedHashMap<>();
                map.put("userId", userProfile.getString("userId"));
                map.put("userTrackId", userProfile.getString("userTrackId"));
                map.put("firstName", userProfile.getString("firstName"));
                map.put("lastName", userProfile.getString("lastName"));
                String fullName = ((userProfile.getString("firstName") != null ? userProfile.getString("firstName") : "") + " "
                        + (userProfile.getString("lastName") != null ? userProfile.getString("lastName") : "")).trim();
                map.put("fullName", fullName.isBlank() ? userProfile.getString("userId") : fullName);
                map.put("email", userProfile.getString("email"));
                map.put("title", userProfile.getString("title"));
                map.put("tenantId", userTenant);
                map.put("roleName", isReviewerTarget ? "QA Reviewer" : "Shift Supervisor");
                result.add(map);
            }
        }

        return result;
    }

    /**
     * Execute bulk workflow transitions with per-item validation and deterministic error reporting.
     */
    public Map<String, Object> executeBulkTransition(
            String userId,
            String userRoleCode,
            String tenantId,
            List<Map<String, String>> items,
            String targetStatus,
            String comments,
            String supervisorName) {

        List<Map<String, Object>> succeeded = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        Set<String> processedKeys = new HashSet<>();

        for (Map<String, String> item : items) {
            String batchNo = item.getOrDefault("batchNo", "");
            String lotNo = item.getOrDefault("lotNo", "");
            String equipmentCode = item.getOrDefault("equipmentCode", "");
            String key = batchNo + ":" + lotNo + ":" + equipmentCode;

            if (batchNo.isBlank() || lotNo.isBlank() || equipmentCode.isBlank()) {
                failed.add(Map.of(
                        "batchNo", batchNo,
                        "lotNo", lotNo,
                        "equipmentCode", equipmentCode,
                        "errorCode", "VALIDATION_FAILED",
                        "reason", "Missing required batch, lot, or equipment identifier"
                ));
                continue;
            }

            if (!processedKeys.add(key)) {
                failed.add(Map.of(
                        "batchNo", batchNo,
                        "lotNo", lotNo,
                        "equipmentCode", equipmentCode,
                        "errorCode", "DUPLICATE_ITEM",
                        "reason", "Duplicate batch/stage item in bulk request"
                ));
                continue;
            }

            try {
                executeTransition(
                        userId, userRoleCode, tenantId, batchNo, lotNo, equipmentCode,
                        targetStatus, comments, supervisorName);
                succeeded.add(Map.of(
                        "batchNo", batchNo,
                        "lotNo", lotNo,
                        "equipmentCode", equipmentCode,
                        "status", targetStatus
                ));
            } catch (BusinessException bex) {
                String msg = bex.getMessage();
                String errCode = "INVALID_WORKFLOW_STATE";
                if (msg != null && msg.toLowerCase().contains("not found")) {
                    errCode = "NOT_FOUND";
                } else if (msg != null && msg.toLowerCase().contains("unauthorized")) {
                    errCode = "UNAUTHORIZED";
                }
                failed.add(Map.of(
                        "batchNo", batchNo,
                        "lotNo", lotNo,
                        "equipmentCode", equipmentCode,
                        "errorCode", errCode,
                        "reason", msg != null ? msg : "Business validation failed"
                ));
            } catch (Exception ex) {
                log.warn("Bulk transition failed for batch={} lot={} stage={}: {}",
                        batchNo, lotNo, equipmentCode, ex.getMessage());
                failed.add(Map.of(
                        "batchNo", batchNo,
                        "lotNo", lotNo,
                        "equipmentCode", equipmentCode,
                        "errorCode", "INTERNAL_ERROR",
                        "reason", ex.getMessage() != null ? ex.getMessage() : "Internal execution failure"
                ));
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", items.size());
        response.put("succeededCount", succeeded.size());
        response.put("failedCount", failed.size());
        response.put("succeeded", succeeded);
        response.put("failed", failed);
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Document doc) {
        if (doc == null) return Collections.emptyMap();
        Map<String, Object> map = new LinkedHashMap<>(doc);
        map.remove("_class");
        Object id = map.get("_id");
        if (id != null) {
            map.put("_id", id.toString());
        }
        // Recursively handle sub-documents
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof Document) {
                entry.setValue(toMap((Document) entry.getValue()));
            } else if (entry.getValue() instanceof List) {
                List<?> list = (List<?>) entry.getValue();
                List<Object> converted = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Document) {
                        converted.add(toMap((Document) item));
                    } else {
                        converted.add(item);
                    }
                }
                entry.setValue(converted);
            }
        }
        return map;
    }
}
