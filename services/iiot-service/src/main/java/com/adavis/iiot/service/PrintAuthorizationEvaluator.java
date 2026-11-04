package com.adavis.iiot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrintAuthorizationEvaluator {

    private static final String DEFAULT_TENANT_ID = "TNT-0001";
    private static final String PRINT_PERMISSIONS_COLLECTION = "mdm_print_permissions";
    private static final String PRINT_AUDIT_LOGS_COLLECTION = "mdm_print_audit_logs";
    private static final String USER_PROFILES_COLLECTION = "mdm_user_profiles";
    private static final String USER_GROUP_ASSIGNMENTS_COLLECTION = "mdm_user_assignments_to_user_groups";
    private static final String ROLE_ASSIGNMENTS_COLLECTION = "mdm_role_assignments_to_user_groups";
    private static final String ROLES_COLLECTION = "mdm_roles";

    private final MongoTemplate mongoTemplate;

    public static class AuthorizationResult {
        private final boolean allowed;
        private final String action;
        private final String message;
        private final String source;

        public AuthorizationResult(boolean allowed, String action, String message, String source) {
            this.allowed = allowed;
            this.action = action;
            this.message = message;
            this.source = source;
        }

        public boolean isAllowed() { return allowed; }
        public String getAction() { return action; }
        public String getMessage() { return message; }
        public String getSource() { return source; }
    }

    /**
     * Checks if a user is authorized for a specific action on a module and report type,
     * and records an immutable audit log entry of the attempt.
     */
    public AuthorizationResult checkAndAuditAccess(
            String tenantId,
            String plantId,
            String userId,
            String userRole,
            String module,
            String reportType,
            String action,
            String documentId,
            Integer copyNumber,
            String reason) {

        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId.trim() : DEFAULT_TENANT_ID;
        String normalizedUser = (userId != null && !userId.isBlank()) ? userId.trim() : "SYSTEM";
        String normalizedModule = (module != null && !module.isBlank()) ? module.trim().toUpperCase(Locale.ROOT) : "IIOT";
        String normalizedReportType = (reportType != null && !reportType.isBlank()) ? reportType.trim().toUpperCase(Locale.ROOT) : "BATCH_DOSSIER";
        String normalizedAction = (action != null && !action.isBlank()) ? action.trim().toUpperCase(Locale.ROOT) : "VIEW";

        boolean isAllowed = false;
        String source = "DEFAULT_DENY";
        String message = "";

        // 1. Super Admin universal access
        if (isSuperAdmin(normalizedUser)) {
            isAllowed = true;
            source = "SUPER_ADMIN_UNIVERSAL";
            message = "Access authorized: Super Administrator universal privilege";
        } else {
            // 2. Fetch user's roles
            Set<String> roleIds = getUserRoleIds(normalizedUser);

            // Check if user has direct User Override in mdm_print_permissions
            Query userQuery = new Query(Criteria.where("tenantId").is(effectiveTenant)
                    .and("targetType").is("USER")
                    .and("targetId").regex("^" + Pattern.quote(normalizedUser) + "$", "i")
                    .and("isActive").is(true));

            List<Document> userPermDocs = mongoTemplate.find(userQuery, Document.class, PRINT_PERMISSIONS_COLLECTION);
            Boolean userExplicitGrant = null;
            Boolean userExplicitDeny = null;

            for (Document doc : userPermDocs) {
                if (matchesModuleAndReport(doc.getString("module"), doc.getString("reportType"), normalizedModule, normalizedReportType)) {
                    List<?> denied = doc.getList("deniedActions", String.class);
                    if (denied != null && denied.contains(normalizedAction)) {
                        userExplicitDeny = true;
                        break;
                    }
                    List<?> allowed = doc.getList("allowedActions", String.class);
                    if (allowed != null && allowed.contains(normalizedAction)) {
                        userExplicitGrant = true;
                    }
                }
            }

            if (Boolean.TRUE.equals(userExplicitDeny)) {
                isAllowed = false;
                source = "USER_OVERRIDE_REVOKE";
                message = "Access denied: Action " + normalizedAction + " is explicitly revoked for user " + normalizedUser;
            } else if (Boolean.TRUE.equals(userExplicitGrant)) {
                isAllowed = true;
                source = "USER_OVERRIDE_GRANT";
                message = "Access authorized: Granted via direct user override";
            } else {
                // 3. Evaluate Role Permissions
                if (!roleIds.isEmpty()) {
                    Query roleQuery = new Query(Criteria.where("tenantId").is(effectiveTenant)
                            .and("targetType").is("ROLE")
                            .and("targetId").in(roleIds)
                            .and("isActive").is(true));

                    List<Document> rolePermDocs = mongoTemplate.find(roleQuery, Document.class, PRINT_PERMISSIONS_COLLECTION);
                    for (Document doc : rolePermDocs) {
                        if (matchesModuleAndReport(doc.getString("module"), doc.getString("reportType"), normalizedModule, normalizedReportType)) {
                            List<?> allowed = doc.getList("allowedActions", String.class);
                            if (allowed != null && allowed.contains(normalizedAction)) {
                                isAllowed = true;
                                source = "ROLE:" + doc.getString("targetId");
                                message = "Access authorized: Granted via role permission " + doc.getString("targetId");
                                break;
                            }
                        }
                    }
                }

                // Fallback default for existing systems if collection is empty
                if (!isAllowed && roleIds.isEmpty() && isKnownAdminRole(userRole)) {
                    isAllowed = true;
                    source = "ROLE_DEFAULT_ADMIN";
                    message = "Access authorized: Administrative role privilege";
                }
            }
        }

        if (!isAllowed && (message == null || message.isBlank())) {
            message = "Access denied: User " + normalizedUser + " lacks " + normalizedAction
                    + " permission for " + normalizedModule + ":" + normalizedReportType;
        }

        // 4. Emit Audit Log to mdm_print_audit_logs
        recordAuditLog(
                effectiveTenant,
                plantId,
                "PRINT_ATTEMPT",
                normalizedAction + "_ATTEMPT",
                isAllowed ? "AUTHORIZED" : "DENIED",
                normalizedUser,
                userRole,
                normalizedModule,
                normalizedReportType,
                documentId,
                copyNumber,
                reason,
                isAllowed ? null : message,
                source
        );

        return new AuthorizationResult(isAllowed, normalizedAction, message, source);
    }

    private boolean isSuperAdmin(String userId) {
        if ("SUPER_ADMIN".equalsIgnoreCase(userId)) {
            return true;
        }
        try {
            Query query = new Query(new Criteria().orOperator(
                    Criteria.where("userId").regex("^" + Pattern.quote(userId) + "$", "i"),
                    Criteria.where("username").regex("^" + Pattern.quote(userId) + "$", "i"),
                    Criteria.where("email").regex("^" + Pattern.quote(userId) + "$", "i")
            ));
            Document profile = mongoTemplate.findOne(query, Document.class, USER_PROFILES_COLLECTION);
            if (profile != null) {
                String title = profile.getString("title");
                if (title != null && (title.equalsIgnoreCase("Super Admin") || title.equalsIgnoreCase("Platform Super Administrator"))) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isKnownAdminRole(String userRole) {
        if (userRole == null) return false;
        String normalized = userRole.toUpperCase(Locale.ROOT);
        return normalized.contains("ADMIN") || normalized.contains("SUPER_ADMIN");
    }

    private Set<String> getUserRoleIds(String userId) {
        Set<String> roleIds = new HashSet<>();
        try {
            Query userAssignQuery = new Query(new Criteria().orOperator(
                    Criteria.where("userId").regex("^" + Pattern.quote(userId.trim()) + "$", "i"),
                    Criteria.where("userId").is(userId.trim())
            ).and("isActive").is(true));

            List<Document> userAssignments = mongoTemplate.find(userAssignQuery, Document.class, USER_GROUP_ASSIGNMENTS_COLLECTION);
            Set<String> groupIds = new HashSet<>();
            for (Document doc : userAssignments) {
                String gid = doc.getString("groupId");
                if (gid != null) groupIds.add(gid);
            }

            if (!groupIds.isEmpty()) {
                Query roleAssignQuery = new Query(Criteria.where("groupId").in(groupIds).and("isActive").is(true));
                List<Document> roleAssignments = mongoTemplate.find(roleAssignQuery, Document.class, ROLE_ASSIGNMENTS_COLLECTION);
                for (Document doc : roleAssignments) {
                    String rid = doc.getString("roleId");
                    if (rid != null) roleIds.add(rid);
                }
            }
        } catch (Exception ex) {
            log.warn("Error fetching user roles for {}: {}", userId, ex.getMessage());
        }
        return roleIds;
    }

    public static boolean isSameModule(String m1, String m2) {
        if (m1 == null || m2 == null) return false;
        if (m1.equalsIgnoreCase(m2)) return true;
        boolean m1IsL2 = m1.equalsIgnoreCase("IIOT") || m1.equalsIgnoreCase("L2_INTEGRATION") || m1.equalsIgnoreCase("MOD-IIOT") || m1.equalsIgnoreCase("MOD-L2-INTEGRATION");
        boolean m2IsL2 = m2.equalsIgnoreCase("IIOT") || m2.equalsIgnoreCase("L2_INTEGRATION") || m2.equalsIgnoreCase("MOD-IIOT") || m2.equalsIgnoreCase("MOD-L2-INTEGRATION");
        return m1IsL2 && m2IsL2;
    }

    private boolean matchesModuleAndReport(String ruleMod, String ruleRep, String targetMod, String targetRep) {
        boolean modMatches = ruleMod == null || "*".equals(ruleMod) || "ALL".equalsIgnoreCase(ruleMod) || isSameModule(ruleMod, targetMod);
        if (!modMatches) return false;

        return ruleRep == null || "*".equals(ruleRep) || "ALL".equalsIgnoreCase(ruleRep) || ruleRep.equalsIgnoreCase(targetRep);
    }

    private void recordAuditLog(
            String tenantId,
            String plantId,
            String eventCategory,
            String action,
            String status,
            String userId,
            String userRole,
            String module,
            String reportType,
            String documentId,
            Integer copyNumber,
            String reason,
            String denialReason,
            String source) {

        try {
            Document audit = new Document();
            audit.put("tenantId", tenantId);
            audit.put("plantId", plantId);
            audit.put("eventCategory", eventCategory);
            audit.put("action", action);
            audit.put("status", status);
            audit.put("userId", userId);
            audit.put("userRole", userRole != null ? userRole : "USER");
            audit.put("module", module);
            audit.put("reportType", reportType);
            audit.put("documentId", documentId);
            audit.put("copyNumber", copyNumber);
            audit.put("justificationReason", reason);
            audit.put("denialReason", denialReason);
            audit.put("timestamp", new Date());

            Map<String, Object> meta = new HashMap<>();
            meta.put("source", source);
            audit.put("metadata", meta);

            mongoTemplate.insert(audit, PRINT_AUDIT_LOGS_COLLECTION);
        } catch (Exception ex) {
            log.error("Failed to write to {}: {}", PRINT_AUDIT_LOGS_COLLECTION, ex.getMessage());
        }
    }
}

