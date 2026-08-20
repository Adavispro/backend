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
 * Workflow service for batch stage approvals, backed by the dynamic Workflow MDM engine.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchWorkflowService {

    private final MongoTemplate mongoTemplate;
    private final NotificationService notificationService;
    private final DynamicWorkflowEngine dynamicWorkflowEngine;

    private static final String BATCH_SUMMARY_COLLECTION = "iiot_batch_summary";
    private static final String AUDIT_COLLECTION = "iiot_workflow_audit_trail";

    // Valid workflow states
    public static final String STATE_PENDING = "PENDING";
    public static final String STATE_UNDER_REVIEW = "UNDER_REVIEW";
    public static final String STATE_REVIEWER_REVIEWED = "REVIEWER_REVIEWED";
    public static final String STATE_APPROVED = "APPROVED";
    public static final String STATE_REJECTED = "REJECTED";
    public static final String STATE_RETURNED_TO_OPERATOR = "RETURNED_TO_OPERATOR";
    public static final String STATE_DEFERRED = "DEFERRED";

    // Role codes that map to permission groups
    public static final String ROLE_OPERATOR = "PRODUCTION_OPERATOR";
    public static final String ROLE_REVIEWER = "PRODUCTION_REVIEWER";
    public static final String ROLE_APPROVER = "QA_APPROVER";
    public static final String ROLE_SUPERVISOR = "SHIFT_SUPERVISOR";
    public static final String ROLE_ADMIN = "PLATFORM_SUPER_ADMIN";

    /**
     * Execute a workflow transition on a batch stage.
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

        targetStatus = targetStatus != null ? targetStatus.toUpperCase(Locale.ROOT).trim() : "";
        userId = userId != null ? userId.trim() : "SYSTEM";
        userRoleCode = userRoleCode != null && !userRoleCode.isBlank() 
                ? userRoleCode.toUpperCase(Locale.ROOT).trim() : dynamicWorkflowEngine.resolveUserRoleCode(userId);

        // Map target status to appropriate Workflow MDM action code
        String actionCode = mapStatusToActionCode(targetStatus, userRoleCode);

        DynamicWorkflowEngine.ActionExecutionRequest req = DynamicWorkflowEngine.ActionExecutionRequest.builder()
                .userId(userId)
                .userRole(userRoleCode)
                .tenantId(tenantId)
                .batchNo(batchNo)
                .lotNo(lotNo)
                .equipmentCode(equipmentCode)
                .actionCode(actionCode)
                .comments(comments)
                .justification(comments)
                .supervisorName(supervisorName)
                .build();

        return dynamicWorkflowEngine.executeAction(req);
    }

    private String mapStatusToActionCode(String targetStatus, String userRoleCode) {
        if ("APPROVED".equalsIgnoreCase(targetStatus)) return "APPROVE";
        if ("REJECTED".equalsIgnoreCase(targetStatus) || "RETURNED_TO_OPERATOR".equalsIgnoreCase(targetStatus)) return "REJECT";
        if ("DEFERRED".equalsIgnoreCase(targetStatus)) return "DEFER";
        if ("REVIEWER_REVIEWED".equalsIgnoreCase(targetStatus) || "PENDING_APPROVAL".equalsIgnoreCase(targetStatus)) return "SEND_FOR_APPROVAL";
        if ("UNDER_REVIEW".equalsIgnoreCase(targetStatus) || "IN_REVIEW".equalsIgnoreCase(targetStatus)) {
            if (userRoleCode.contains("OPERATOR")) return "SEND_FOR_REVIEW";
            return "SEND_FOR_REVIEW";
        }
        if ("JUSTIFICATION_SUBMITTED".equalsIgnoreCase(targetStatus)) return "SUBMIT_JUSTIFICATION";
        return targetStatus;
    }

    /**
     * Retrieve audit trail for batch/lot/equipment.
     */
    public List<Map<String, Object>> getWorkflowAuditTrail(String batchNo, String lotNo, String equipmentCode, String tenantId) {
        return dynamicWorkflowEngine.getWorkflowAuditTrail(batchNo, lotNo, equipmentCode, tenantId);
    }

    /**
     * Retrieve active eligible assignees for a given target workflow state within tenant/plant scope.
     */
    public List<Map<String, Object>> getEligibleAssignees(String targetStatus, String tenantId, String plantId) {
        String status = (targetStatus != null ? targetStatus : "").toUpperCase(Locale.ROOT).trim();
        boolean isReviewerTarget = "UNDER_REVIEW".equals(status);
        boolean isSupervisorTarget = "REVIEWER_REVIEWED".equals(status) || "APPROVED".equals(status);

        Query groupQ = new Query();
        if (tenantId != null && !tenantId.isBlank()) {
            groupQ.addCriteria(Criteria.where("tenantId").is(tenantId));
        }
        List<Document> groups = mongoTemplate.find(groupQ, Document.class, "mdm_user_groups");
        if (groups.isEmpty() && tenantId != null && !tenantId.isBlank()) {
            groups = mongoTemplate.find(new Query(Criteria.where("tenantId").exists(false)), Document.class, "mdm_user_groups");
        }

        Set<String> matchingGroupIds = new HashSet<>();
        for (Document group : groups) {
            String groupId = group.getString("groupId");
            String groupCode = (group.getString("groupCode") != null ? group.getString("groupCode") : "").toUpperCase(Locale.ROOT);
            if (groupId == null) continue;

            if (isReviewerTarget && (groupCode.contains("REVIEWER") || groupCode.contains("QA"))) {
                matchingGroupIds.add(groupId);
            } else if (isSupervisorTarget && (groupCode.contains("SUPERVISOR") || groupCode.contains("APPROVER"))) {
                matchingGroupIds.add(groupId);
            }
        }

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
            } else if (isSupervisorTarget && (roleCode.contains("SUPERVISOR") || roleCode.contains("APPROVER"))) {
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

                String userTenant = userProfile.getString("tenantId");
                if (tenantId != null && !tenantId.isBlank() && userTenant != null && !userTenant.isBlank()) {
                    if (!tenantId.equalsIgnoreCase(userTenant)) continue;
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
                map.put("roleName", isReviewerTarget ? "Production / QA Reviewer" : "Shift Supervisor / QA Approver");
                result.add(map);
            }
        }

        return result;
    }

    /**
     * Legacy bulk transition support (iterates item-by-item with dynamic validation).
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

        for (Map<String, String> item : items) {
            String batchNo = item.getOrDefault("batchNo", "");
            String lotNo = item.getOrDefault("lotNo", "");
            String equipmentCode = item.getOrDefault("equipmentCode", "");

            if (batchNo.isBlank() || lotNo.isBlank() || equipmentCode.isBlank()) {
                failed.add(Map.of("batchNo", batchNo, "lotNo", lotNo, "equipmentCode", equipmentCode, "error", "Missing required fields"));
                continue;
            }

            try {
                Map<String, Object> res = executeTransition(userId, userRoleCode, tenantId, batchNo, lotNo, equipmentCode, targetStatus, comments, supervisorName);
                succeeded.add(Map.of("batchNo", batchNo, "lotNo", lotNo, "equipmentCode", equipmentCode, "result", res));
            } catch (Exception e) {
                failed.add(Map.of("batchNo", batchNo, "lotNo", lotNo, "equipmentCode", equipmentCode, "error", e.getMessage()));
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", items.size());
        summary.put("succeededCount", succeeded.size());
        summary.put("failedCount", failed.size());
        summary.put("succeeded", succeeded);
        summary.put("failed", failed);
        return summary;
    }
}
