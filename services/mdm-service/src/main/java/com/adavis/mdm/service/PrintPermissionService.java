package com.adavis.mdm.service;

import com.adavis.common.exception.BusinessException;
import com.adavis.mdm.model.dto.*;
import com.adavis.mdm.model.entity.*;
import com.adavis.mdm.repository.*;
import com.adavis.mdm.security.SecurityContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PrintPermissionService {

    public static final String DEFAULT_TENANT_ID = "TNT-0001";
    public static final Set<String> ALL_PRINT_ACTIONS = Set.of(
            "VIEW",
            "PRINT",
            "REPRINT",
            "MANAGE_PERMISSIONS",
            "PRINT_AUDIT"
    );

    private static final String USER_PROFILES_COLLECTION = "mdm_user_profiles";
    private static final String AUTH_USERS_COLLECTION = "auth_users";
    private static final String USER_GROUP_ASSIGNMENTS_COLLECTION = "mdm_user_assignments_to_user_groups";
    private static final String ROLE_ASSIGNMENTS_COLLECTION = "mdm_role_assignments_to_user_groups";
    private static final String ROLES_COLLECTION = "mdm_roles";

    private final PrintPermissionRepository printPermissionRepository;
    private final PrintDelegationRepository printDelegationRepository;
    private final PrintAuditLogRepository printAuditLogRepository;
    private final SecurityContextService securityContextService;
    private final MongoTemplate mongoTemplate;

    // =========================================================================
    // 1. Authorization & Privilege Escalation Checks
    // =========================================================================

    /**
     * Verifies that the acting user has permission to manage print permissions
     * for the given target module.
     * Allowed if:
     *   - Actor is Super Admin, OR
     *   - Actor is an Admin with an active explicit delegation from Super Admin covering targetModule.
     */
    public void verifyCanManagePermissions(String actorUserId, String tenantId, String targetModule) {
        if (!StringUtils.hasText(actorUserId)) {
            throw new BusinessException("Authentication required to manage print permissions.", "UNAUTHORIZED");
        }

        if (securityContextService.isSuperAdmin(actorUserId)) {
            return; // Super Admin has universal print management authorization
        }

        // Verify if actor has an active delegation
        String effectiveTenant = resolveTenant(tenantId, actorUserId);
        List<PrintDelegation> delegations = printDelegationRepository
                .findByTenantIdAndIsActiveTrue(effectiveTenant);

        Set<String> userRoles = getUserRoleIds(actorUserId);

        boolean hasActiveDelegation = delegations.stream().anyMatch(d -> {
            boolean isTarget = (d.getDelegateeType().equalsIgnoreCase("USER") && actorUserId.equalsIgnoreCase(d.getDelegateeId()))
                    || (d.getDelegateeType().equalsIgnoreCase("ROLE") && userRoles.contains(d.getDelegateeId()));
            if (!isTarget) return false;

            if (d.getExpiresAt() != null && d.getExpiresAt().isBefore(Instant.now())) {
                return false;
            }

            if (d.getAllowedModules() == null || d.getAllowedModules().isEmpty() || d.getAllowedModules().contains("*")) {
                return true;
            }

            return targetModule != null && (d.getAllowedModules().contains(targetModule.toUpperCase(Locale.ROOT))
                    || d.getAllowedModules().stream().anyMatch(m -> isSameModule(m, targetModule)));
        });

        if (!hasActiveDelegation) {
            log.warn("Privilege check failed: User '{}' attempted print management without Super Admin delegation on module '{}'",
                    actorUserId, targetModule);
            throw new BusinessException(
                    "Administrative delegation required: Only Super Admin or explicitly delegated Admins can manage print permissions.",
                    "FORBIDDEN"
            );
        }
    }

    /**
     * Prevents non-Super Admin delegated users from modifying permissions for administrative roles or users.
     */
    private void verifyNoPrivilegeEscalation(String actorUserId, String targetType, String targetId) {
        if (securityContextService.isSuperAdmin(actorUserId)) {
            return;
        }

        if ("ROLE".equalsIgnoreCase(targetType)) {
            Query roleQuery = new Query(Criteria.where("roleId").is(targetId));
            Document role = mongoTemplate.findOne(roleQuery, Document.class, ROLES_COLLECTION);
            if (role != null) {
                String roleCode = role.getString("roleCode");
                if (securityContextService.isAdminRoleCode(roleCode)) {
                    throw new BusinessException(
                            "Privilege escalation prevented: Delegated Admins cannot modify permissions of administrative roles.",
                            "FORBIDDEN"
                    );
                }
            }
        } else if ("USER".equalsIgnoreCase(targetType)) {
            if (actorUserId.equalsIgnoreCase(targetId)) {
                throw new BusinessException(
                        "Privilege escalation prevented: Administrators cannot modify their own print permission overrides.",
                        "FORBIDDEN"
                );
            }
            if (securityContextService.isSuperAdmin(targetId)) {
                throw new BusinessException(
                        "Privilege escalation prevented: Cannot modify permissions of a Super Administrator.",
                        "FORBIDDEN"
                );
            }
        }
    }

    // =========================================================================
    // 2. Permission Management (Role and User Grants / Revocations)
    // =========================================================================

    public PrintPermission savePrintPermission(PrintPermissionRequest req, String actorUserId) {
        String effectiveTenant = resolveTenant(req.getTenantId(), actorUserId);
        String targetModule = req.getModule().toUpperCase(Locale.ROOT);
        String targetReportType = req.getReportType().toUpperCase(Locale.ROOT);

        if (isSameModule(targetModule, "IIOT")) {
            targetModule = "IIOT";
        }

        verifyCanManagePermissions(actorUserId, effectiveTenant, targetModule);
        verifyNoPrivilegeEscalation(actorUserId, req.getTargetType(), req.getTargetId());

        if (!StringUtils.hasText(req.getRemarks()) || req.getRemarks().trim().length() < 3) {
            throw new BusinessException("Appropriate audit justification / remarks is required for print policy changes.", "BAD_REQUEST");
        }

        Optional<PrintPermission> existingOpt = printPermissionRepository
                .findByTenantIdAndTargetTypeAndTargetIdAndModuleAndReportTypeAndIsActiveTrue(
                        effectiveTenant,
                        req.getTargetType().toUpperCase(Locale.ROOT),
                        req.getTargetId(),
                        targetModule,
                        targetReportType
                );
        if (existingOpt.isEmpty() && "IIOT".equals(targetModule)) {
            existingOpt = printPermissionRepository
                    .findByTenantIdAndTargetTypeAndTargetIdAndModuleAndReportTypeAndIsActiveTrue(
                            effectiveTenant,
                            req.getTargetType().toUpperCase(Locale.ROOT),
                            req.getTargetId(),
                            "L2_INTEGRATION",
                            targetReportType
                    );
        }

        PrintPermission permission;
        boolean isNew = false;
        Set<String> previousAllowed = Collections.emptySet();
        Set<String> previousDenied = Collections.emptySet();

        if (existingOpt.isPresent()) {
            permission = existingOpt.get();
            previousAllowed = new HashSet<>(permission.getAllowedActions());
            previousDenied = new HashSet<>(permission.getDeniedActions());
            permission.setAllowedActions(req.getAllowedActions() != null ? req.getAllowedActions() : new HashSet<>());
            permission.setDeniedActions(req.getDeniedActions() != null ? req.getDeniedActions() : new HashSet<>());
            permission.setUpdatedBy(actorUserId);
            permission.setUpdatedAt(Instant.now());
            permission.setRemarks(req.getRemarks());
        } else {
            isNew = true;
            permission = PrintPermission.builder()
                    .tenantId(effectiveTenant)
                    .targetType(req.getTargetType().toUpperCase(Locale.ROOT))
                    .targetId(req.getTargetId())
                    .targetName(req.getTargetName())
                    .targetCode(req.getTargetCode())
                    .module(targetModule)
                    .reportType(targetReportType)
                    .allowedActions(req.getAllowedActions() != null ? req.getAllowedActions() : new HashSet<>())
                    .deniedActions(req.getDeniedActions() != null ? req.getDeniedActions() : new HashSet<>())
                    .isActive(true)
                    .createdBy(actorUserId)
                    .createdAt(Instant.now())
                    .updatedBy(actorUserId)
                    .updatedAt(Instant.now())
                    .remarks(req.getRemarks())
                    .build();
        }

        PrintPermission saved = printPermissionRepository.save(permission);

        // Audit Trail
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("isNew", isNew);
        metadata.put("targetType", saved.getTargetType());
        metadata.put("targetId", saved.getTargetId());
        metadata.put("targetName", saved.getTargetName());
        metadata.put("module", saved.getModule());
        metadata.put("reportType", saved.getReportType());
        metadata.put("previousAllowed", previousAllowed);
        metadata.put("newAllowed", saved.getAllowedActions());
        metadata.put("previousDenied", previousDenied);
        metadata.put("newDenied", saved.getDeniedActions());

        recordAuditLog(
                effectiveTenant,
                "PERMISSION_CHANGE",
                isNew ? "PERMISSION_GRANTED" : "PERMISSION_UPDATED",
                "AUTHORIZED",
                actorUserId,
                null,
                null,
                saved.getModule(),
                saved.getReportType(),
                saved.getId(),
                null,
                req.getRemarks() != null ? req.getRemarks() : "Print permission updated by " + actorUserId,
                null,
                metadata
        );

        return saved;
    }

    public void deactivatePermission(String permissionId, String actorUserId, String reason) {
        PrintPermission perm = printPermissionRepository.findById(permissionId)
                .orElseThrow(() -> new BusinessException("Print permission record not found: " + permissionId, "NOT_FOUND"));

        verifyCanManagePermissions(actorUserId, perm.getTenantId(), perm.getModule());
        verifyNoPrivilegeEscalation(actorUserId, perm.getTargetType(), perm.getTargetId());

        perm.setIsActive(false);
        perm.setUpdatedBy(actorUserId);
        perm.setUpdatedAt(Instant.now());
        printPermissionRepository.save(perm);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("targetType", perm.getTargetType());
        metadata.put("targetId", perm.getTargetId());
        metadata.put("module", perm.getModule());
        metadata.put("reportType", perm.getReportType());
        metadata.put("revokedAllowed", perm.getAllowedActions());

        recordAuditLog(
                perm.getTenantId(),
                "PERMISSION_CHANGE",
                "PERMISSION_REVOKED",
                "AUTHORIZED",
                actorUserId,
                null,
                null,
                perm.getModule(),
                perm.getReportType(),
                perm.getId(),
                null,
                reason != null ? reason : "Deactivated print permission record",
                null,
                metadata
        );
    }

    // =========================================================================
    // 3. Super Admin Delegation Management
    // =========================================================================

    public PrintDelegation delegateAdmin(PrintDelegationRequest req, String actorUserId) {
        String effectiveTenant = resolveTenant(req.getTenantId(), actorUserId);

        if (!securityContextService.isSuperAdmin(actorUserId)) {
            throw new BusinessException(
                    "Super Admin privilege required: Only Super Administrators can delegate print management authority.",
                    "FORBIDDEN"
            );
        }

        PrintDelegation delegation = PrintDelegation.builder()
                .tenantId(effectiveTenant)
                .delegateeType(req.getDelegateeType().toUpperCase(Locale.ROOT))
                .delegateeId(req.getDelegateeId())
                .delegateeName(req.getDelegateeName())
                .allowedModules(req.getAllowedModules() != null && !req.getAllowedModules().isEmpty()
                        ? req.getAllowedModules() : Set.of("*"))
                .canSubDelegate(false) // Sub-delegation strictly disallowed for safety
                .delegatedBy(actorUserId)
                .delegatedAt(Instant.now())
                .expiresAt(req.getExpiresAt())
                .isActive(true)
                .remarks(req.getRemarks())
                .build();

        PrintDelegation saved = printDelegationRepository.save(delegation);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("delegateeType", saved.getDelegateeType());
        metadata.put("delegateeId", saved.getDelegateeId());
        metadata.put("delegateeName", saved.getDelegateeName());
        metadata.put("allowedModules", saved.getAllowedModules());
        metadata.put("expiresAt", saved.getExpiresAt());

        recordAuditLog(
                effectiveTenant,
                "DELEGATION_CHANGE",
                "DELEGATION_GRANTED",
                "AUTHORIZED",
                actorUserId,
                null,
                null,
                "ALL",
                "ALL",
                saved.getId(),
                null,
                "Print management delegated to " + saved.getDelegateeName() + " (" + saved.getDelegateeId() + ")",
                null,
                metadata
        );

        return saved;
    }

    public void revokeDelegation(String delegationId, String reason, String actorUserId) {
        if (!securityContextService.isSuperAdmin(actorUserId)) {
            throw new BusinessException(
                    "Super Admin privilege required: Only Super Administrators can revoke print management delegations.",
                    "FORBIDDEN"
            );
        }

        PrintDelegation delegation = printDelegationRepository.findById(delegationId)
                .orElseThrow(() -> new BusinessException("Delegation record not found: " + delegationId, "NOT_FOUND"));

        delegation.setIsActive(false);
        delegation.setRevokedBy(actorUserId);
        delegation.setRevokedAt(Instant.now());
        delegation.setRevocationReason(reason);
        printDelegationRepository.save(delegation);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("delegateeId", delegation.getDelegateeId());
        metadata.put("delegateeName", delegation.getDelegateeName());
        metadata.put("reason", reason);

        recordAuditLog(
                delegation.getTenantId(),
                "DELEGATION_CHANGE",
                "DELEGATION_REVOKED",
                "AUTHORIZED",
                actorUserId,
                null,
                null,
                "ALL",
                "ALL",
                delegation.getId(),
                null,
                "Delegation revoked for " + delegation.getDelegateeId() + ". Reason: " + reason,
                null,
                metadata
        );
    }

    public List<PrintDelegation> getActiveDelegations(String tenantId, String actorUserId) {
        String effectiveTenant = resolveTenant(tenantId, actorUserId);
        return printDelegationRepository.findByTenantIdAndIsActiveTrue(effectiveTenant);
    }

    // =========================================================================
    // 4. Effective Access Calculation & Real-Time Authorization Check
    // =========================================================================

    /**
     * Calculates the effective print permissions for a given user on a specific module and report type.
     * Evaluates:
     *   1. Super Admin universal grant
     *   2. Inherited role permissions (union across all user's active roles)
     *   3. Direct user-level overrides (both grants and explicit revokes)
     *   4. Active delegations for MANAGE_PERMISSIONS
     *   5. Default-deny for any ungranted action
     */
    public EffectivePrintAccessResponse calculateEffectiveAccess(
            String tenantId, String userId, String module, String reportType) {

        String effectiveTenant = StringUtils.hasText(tenantId) ? tenantId : DEFAULT_TENANT_ID;
        String normalizedUser = userId != null ? userId.trim() : "SYSTEM";
        String normalizedModule = module != null ? module.trim().toUpperCase(Locale.ROOT) : "IIOT";
        String normalizedReportType = reportType != null ? reportType.trim().toUpperCase(Locale.ROOT) : "ALL";

        EffectivePrintAccessResponse response = EffectivePrintAccessResponse.builder()
                .userId(normalizedUser)
                .tenantId(effectiveTenant)
                .module(normalizedModule)
                .reportType(normalizedReportType)
                .allowedActions(new HashSet<>())
                .deniedActions(new HashSet<>())
                .actionSources(new HashMap<>())
                .assignedRoles(new ArrayList<>())
                .authorizedModules(new ArrayList<>(List.of(
                        "L2 Integration (Manufacturing Operations)",
                        "MDM (Master Data Management)"
                )))
                .roleBaselineActions(new HashSet<>())
                .userOverrideAllowedActions(new HashSet<>())
                .userOverrideDeniedActions(new HashSet<>())
                .build();

        // 1. Resolve User Profile Details
        Document profile = findUserProfile(normalizedUser);
        Document authUser = findAuthUser(normalizedUser);
        String username = null;
        if (authUser != null) {
            username = authUser.getString("username");
        }
        if (!StringUtils.hasText(username) && profile != null) {
            username = profile.getString("username");
        }
        if (!StringUtils.hasText(username)) {
            username = normalizedUser.toLowerCase(Locale.ROOT);
        }
        response.setUsername(username);

        if (profile != null) {
            String fn = profile.getString("firstName");
            String ln = profile.getString("lastName");
            String full = (fn != null || ln != null)
                    ? ((fn != null ? fn : "") + " " + (ln != null ? ln : "")).trim()
                    : username;
            response.setFullName(StringUtils.hasText(full) ? full : username);
            response.setDepartment(profile.getString("departmentId"));
        } else {
            response.setFullName(username);
        }

        // 2. Check Super Admin
        if (securityContextService.isSuperAdmin(normalizedUser)) {
            response.setSuperAdmin(true);
            response.setAssignedRoles(List.of("Platform Super Administrator"));
            response.getRoleBaselineActions().addAll(ALL_PRINT_ACTIONS);
            response.getAllowedActions().addAll(ALL_PRINT_ACTIONS);
            for (String act : ALL_PRINT_ACTIONS) {
                response.getActionSources().put(act, "SUPER_ADMIN_UNIVERSAL");
            }
            return response;
        }

        // 3. Resolve User's Roles
        Set<String> roleIds = getUserRoleIds(normalizedUser);
        Map<String, String> roleNames = getRoleNames(roleIds);
        response.setAssignedRoles(new ArrayList<>(roleNames.values()));

        // Fetch role print permissions
        if (!roleIds.isEmpty()) {
            List<PrintPermission> rolePerms = printPermissionRepository
                    .findByTenantIdAndTargetTypeAndTargetIdInAndIsActiveTrue(effectiveTenant, "ROLE", new ArrayList<>(roleIds));

            for (PrintPermission rp : rolePerms) {
                if (matchesModuleAndReport(rp.getModule(), rp.getReportType(), normalizedModule, normalizedReportType)) {
                    String roleLabel = roleNames.getOrDefault(rp.getTargetId(), rp.getTargetId());
                    for (String act : rp.getAllowedActions()) {
                        response.getRoleBaselineActions().add(act);
                        response.getAllowedActions().add(act);
                        response.getActionSources().putIfAbsent(act, "ROLE:" + roleLabel);
                    }
                }
            }
        }

        // 4. Apply User-Level Direct Overrides
        List<PrintPermission> userPerms = printPermissionRepository
                .findByTenantIdAndTargetTypeAndTargetIdAndIsActiveTrue(effectiveTenant, "USER", normalizedUser);

        for (PrintPermission up : userPerms) {
            if (matchesModuleAndReport(up.getModule(), up.getReportType(), normalizedModule, normalizedReportType)) {
                // User direct grants
                if (up.getAllowedActions() != null) {
                    for (String act : up.getAllowedActions()) {
                        response.getUserOverrideAllowedActions().add(act);
                        response.getAllowedActions().add(act);
                        response.getActionSources().put(act, "USER_OVERRIDE_GRANT");
                    }
                }
                // User direct revokes (take precedence over role inheritance)
                if (up.getDeniedActions() != null) {
                    for (String act : up.getDeniedActions()) {
                        response.getUserOverrideDeniedActions().add(act);
                        response.getAllowedActions().remove(act);
                        response.getDeniedActions().add(act);
                        response.getActionSources().put(act, "USER_OVERRIDE_REVOKE (Explicit Deny)");
                    }
                }
            }
        }

        // 4. Check Delegations for MANAGE_PERMISSIONS
        List<PrintDelegation> delegations = printDelegationRepository.findByTenantIdAndIsActiveTrue(effectiveTenant);
        for (PrintDelegation d : delegations) {
            boolean isTarget = (d.getDelegateeType().equalsIgnoreCase("USER") && normalizedUser.equalsIgnoreCase(d.getDelegateeId()))
                    || (d.getDelegateeType().equalsIgnoreCase("ROLE") && roleIds.contains(d.getDelegateeId()));
            if (isTarget && (d.getExpiresAt() == null || d.getExpiresAt().isAfter(Instant.now()))) {
                if (d.getAllowedModules() == null || d.getAllowedModules().contains("*")
                        || d.getAllowedModules().contains(normalizedModule)) {
                    response.getAllowedActions().add("MANAGE_PERMISSIONS");
                    response.getActionSources().put("MANAGE_PERMISSIONS", "DELEGATED_BY_SUPER_ADMIN (" + d.getDelegatedBy() + ")");
                    response.setDelegatedAdmin(true);
                }
            }
        }

        return response;
    }

    /**
     * Authoritative access check invoked before executing any PDF View, Print, or Reprint.
     * Records the authorized or denied attempt in the immutable audit log.
     */
    public PrintAccessCheckResponse verifyPrintAccess(PrintAccessCheckRequest req) {
        String effectiveTenant = StringUtils.hasText(req.getTenantId()) ? req.getTenantId() : DEFAULT_TENANT_ID;
        String action = req.getAction().toUpperCase(Locale.ROOT);

        EffectivePrintAccessResponse access = calculateEffectiveAccess(
                effectiveTenant, req.getUserId(), req.getModule(), req.getReportType());

        boolean isAllowed = access.getAllowedActions().contains(action);

        String status = isAllowed ? "AUTHORIZED" : "DENIED";
        String message = isAllowed
                ? "Access granted for action " + action + " on " + req.getModule() + ":" + req.getReportType()
                : "Access denied: Missing " + action + " permission for " + req.getModule() + ":" + req.getReportType();

        String source = access.getActionSources().getOrDefault(action, isAllowed ? "GRANTED" : "DEFAULT_DENY");

        // Record Audit Trail
        Map<String, Object> meta = new HashMap<>();
        meta.put("source", source);
        meta.put("effectiveAllowedActions", access.getAllowedActions());
        meta.put("effectiveDeniedActions", access.getDeniedActions());
        if (req.getCopyNumber() != null) {
            meta.put("copyNumber", req.getCopyNumber());
        }

        recordAuditLog(
                effectiveTenant,
                "PRINT_ATTEMPT",
                action + "_ATTEMPT",
                status,
                req.getUserId(),
                req.getUserRole(),
                null,
                req.getModule(),
                req.getReportType(),
                req.getDocumentId(),
                req.getCopyNumber(),
                req.getReason(),
                isAllowed ? null : message,
                meta
        );

        return PrintAccessCheckResponse.builder()
                .allowed(isAllowed)
                .action(action)
                .userId(req.getUserId())
                .module(req.getModule())
                .reportType(req.getReportType())
                .message(message)
                .source(source)
                .build();
    }

    // =========================================================================
    // 5. Query & Audit Trail
    // =========================================================================

    public List<PrintPermission> getPermissionsByTarget(String tenantId, String targetType, String targetId) {
        return printPermissionRepository.findByTenantIdAndTargetTypeAndTargetIdAndIsActiveTrue(
                tenantId != null ? tenantId : DEFAULT_TENANT_ID,
                targetType.toUpperCase(Locale.ROOT),
                targetId
        );
    }

    public List<PrintPermission> getAllPermissions(String tenantId) {
        return printPermissionRepository.findByTenantIdAndIsActiveTrue(
                tenantId != null ? tenantId : DEFAULT_TENANT_ID
        );
    }

    public Page<PrintAuditLog> getAuditLogs(String tenantId, int page, int size) {
        return printAuditLogRepository.findByTenantIdOrderByTimestampDesc(
                tenantId != null ? tenantId : DEFAULT_TENANT_ID,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"))
        );
    }

    public void recordAuditLog(
            String tenantId,
            String eventCategory,
            String action,
            String status,
            String userId,
            String userRole,
            String plantId,
            String module,
            String reportType,
            String documentId,
            Integer copyNumber,
            String justificationReason,
            String denialReason,
            Map<String, Object> metadata) {

        try {
            PrintAuditLog logEntry = PrintAuditLog.builder()
                    .tenantId(tenantId != null ? tenantId : DEFAULT_TENANT_ID)
                    .plantId(plantId)
                    .eventCategory(eventCategory)
                    .action(action)
                    .status(status)
                    .userId(userId)
                    .userRole(userRole)
                    .module(module)
                    .reportType(reportType)
                    .documentId(documentId)
                    .copyNumber(copyNumber)
                    .justificationReason(justificationReason)
                    .denialReason(denialReason)
                    .metadata(metadata != null ? metadata : new HashMap<>())
                    .timestamp(Instant.now())
                    .build();

            printAuditLogRepository.save(logEntry);
        } catch (Exception ex) {
            log.error("Failed to record print audit log entry: {}", ex.getMessage(), ex);
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    public static boolean isSameModule(String m1, String m2) {
        if (m1 == null || m2 == null) return false;
        if (m1.equalsIgnoreCase(m2)) return true;
        boolean m1IsL2 = m1.equalsIgnoreCase("IIOT") || m1.equalsIgnoreCase("L2_INTEGRATION") || m1.equalsIgnoreCase("MOD-IIOT") || m1.equalsIgnoreCase("MOD-L2-INTEGRATION");
        boolean m2IsL2 = m2.equalsIgnoreCase("IIOT") || m2.equalsIgnoreCase("L2_INTEGRATION") || m2.equalsIgnoreCase("MOD-IIOT") || m2.equalsIgnoreCase("MOD-L2-INTEGRATION");
        return m1IsL2 && m2IsL2;
    }

    private boolean matchesModuleAndReport(String ruleMod, String ruleRep, String targetMod, String targetRep) {
        boolean modMatches = "*".equals(ruleMod) || "ALL".equalsIgnoreCase(ruleMod) || isSameModule(ruleMod, targetMod);
        if (!modMatches) return false;

        return "*".equals(ruleRep) || "ALL".equalsIgnoreCase(ruleRep) || ruleRep.equalsIgnoreCase(targetRep);
    }

    private Document findUserProfile(String userId) {
        if (!StringUtils.hasText(userId)) return null;
        try {
            Query query = new Query(new Criteria().orOperator(
                    Criteria.where("userId").regex("^" + Pattern.quote(userId.trim()) + "$", "i"),
                    Criteria.where("username").regex("^" + Pattern.quote(userId.trim()) + "$", "i")
            ));
            return mongoTemplate.findOne(query, Document.class, USER_PROFILES_COLLECTION);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Document findAuthUser(String userId) {
        if (!StringUtils.hasText(userId)) return null;
        try {
            Query query = new Query(new Criteria().orOperator(
                    Criteria.where("userId").regex("^" + Pattern.quote(userId.trim()) + "$", "i"),
                    Criteria.where("username").regex("^" + Pattern.quote(userId.trim()) + "$", "i")
            ));
            return mongoTemplate.findOne(query, Document.class, AUTH_USERS_COLLECTION);
        } catch (Exception ignored) {
            return null;
        }
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

    private Map<String, String> getRoleNames(Set<String> roleIds) {
        Map<String, String> names = new HashMap<>();
        if (roleIds.isEmpty()) return names;

        try {
            Query q = new Query(Criteria.where("roleId").in(roleIds));
            List<Document> docs = mongoTemplate.find(q, Document.class, ROLES_COLLECTION);
            for (Document d : docs) {
                String rid = d.getString("roleId");
                String name = d.getString("roleName");
                if (rid != null && name != null) {
                    names.put(rid, name);
                }
            }
        } catch (Exception ignored) {}
        return names;
    }

    public Page<PrintUserSearchDto> getSearchableUsers(String tenantId, String search, int page, int size) {
        String effectiveTenant = resolveTenant(tenantId, null);

        // 1. Fetch user profiles
        List<Document> profiles = mongoTemplate.findAll(Document.class, USER_PROFILES_COLLECTION);

        // 2. Fetch auth_users to map usernames and emails
        List<Document> authUsers = mongoTemplate.findAll(Document.class, AUTH_USERS_COLLECTION);
        Map<String, String> usernameMap = new HashMap<>();
        Map<String, String> emailMap = new HashMap<>();
        for (Document au : authUsers) {
            String uid = au.getString("userId");
            if (uid != null) {
                usernameMap.put(uid.toUpperCase(Locale.ROOT), au.getString("username"));
                emailMap.put(uid.toUpperCase(Locale.ROOT), au.getString("email"));
            }
        }

        // 3. Pre-fetch user assignments, role assignments, and roles to resolve assigned roles
        List<Document> userGroups = mongoTemplate.findAll(Document.class, USER_GROUP_ASSIGNMENTS_COLLECTION);
        List<Document> roleGroups = mongoTemplate.findAll(Document.class, ROLE_ASSIGNMENTS_COLLECTION);
        List<Document> roles = mongoTemplate.findAll(Document.class, ROLES_COLLECTION);

        Map<String, Document> roleMap = new HashMap<>();
        for (Document r : roles) {
            String rid = r.getString("roleId");
            if (rid != null) roleMap.put(rid, r);
        }

        Map<String, List<Document>> groupToRoles = new HashMap<>();
        for (Document rg : roleGroups) {
            String gid = rg.getString("groupId");
            String rid = rg.getString("roleId");
            if (gid != null && rid != null && roleMap.containsKey(rid)) {
                groupToRoles.computeIfAbsent(gid, k -> new ArrayList<>()).add(roleMap.get(rid));
            }
        }

        Map<String, List<Document>> userToRoles = new HashMap<>();
        for (Document ug : userGroups) {
            String uid = ug.getString("userId");
            String gid = ug.getString("groupId");
            if (uid != null && gid != null && groupToRoles.containsKey(gid)) {
                for (Document r : groupToRoles.get(gid)) {
                    List<Document> list = userToRoles.computeIfAbsent(uid.toUpperCase(Locale.ROOT), k -> new ArrayList<>());
                    if (list.stream().noneMatch(existing -> existing.getString("roleId").equals(r.getString("roleId")))) {
                        list.add(r);
                    }
                }
            }
        }

        // 4. Build list of search DTOs
        List<PrintUserSearchDto> dtoList = new ArrayList<>();
        String searchLower = StringUtils.hasText(search) ? search.trim().toLowerCase(Locale.ROOT) : null;

        for (Document p : profiles) {
            String uid = p.getString("userId");
            if (!StringUtils.hasText(uid)) continue;
            String uidUpper = uid.toUpperCase(Locale.ROOT);

            String username = usernameMap.get(uidUpper);
            if (!StringUtils.hasText(username)) {
                username = p.getString("username");
            }
            if (!StringUtils.hasText(username)) {
                username = uid.toLowerCase(Locale.ROOT);
            }

            String fn = p.getString("firstName");
            String ln = p.getString("lastName");
            String fullName = (fn != null || ln != null)
                    ? ((fn != null ? fn : "") + " " + (ln != null ? ln : "")).trim()
                    : username;

            String email = emailMap.getOrDefault(uidUpper, p.getString("email"));
            String department = p.getString("departmentId");
            boolean isActive = p.getBoolean("isActive", true);
            boolean isBlocked = p.getBoolean("isBlocked", false);
            String accountStatus = isBlocked ? "BLOCKED" : (isActive ? "ACTIVE" : "INACTIVE");

            List<Document> assignedRoleDocs = userToRoles.getOrDefault(uidUpper, new ArrayList<>());
            List<String> roleCodes = new ArrayList<>();
            List<String> roleNames = new ArrayList<>();
            for (Document rd : assignedRoleDocs) {
                String rc = rd.getString("roleCode");
                String rn = rd.getString("roleName");
                if (rc != null) roleCodes.add(rc);
                if (rn != null) roleNames.add(rn);
            }

            // Fallback for special system accounts if not explicitly mapped to user groups
            if (roleCodes.isEmpty()) {
                if ("SUPER_ADMIN".equalsIgnoreCase(uid)) {
                    roleCodes.add("SUPER_ADMIN");
                    roleNames.add("Platform Super Administrator");
                } else if ("IT_ADMIN".equalsIgnoreCase(uid)) {
                    roleCodes.add("IT_ADMIN");
                    roleNames.add("IT Administrator");
                }
            }

            String primaryRoleCode = !roleCodes.isEmpty() ? String.join(", ", roleCodes) : "NO_ROLE";
            // Required display format: production_operator_1 — [Actual Full Name] — PRODUCTION_OPERATOR
            String displayLabel = username + " — " + fullName + " — " + primaryRoleCode;

            // Search filtering across User ID, username, name, or assigned role
            if (searchLower != null) {
                boolean matchesUid = uid.toLowerCase(Locale.ROOT).contains(searchLower);
                boolean matchesUname = username.toLowerCase(Locale.ROOT).contains(searchLower);
                boolean matchesName = fullName.toLowerCase(Locale.ROOT).contains(searchLower);
                boolean matchesRoleCode = roleCodes.stream().anyMatch(rc -> rc.toLowerCase(Locale.ROOT).contains(searchLower));
                boolean matchesRoleName = roleNames.stream().anyMatch(rn -> rn.toLowerCase(Locale.ROOT).contains(searchLower));
                if (!matchesUid && !matchesUname && !matchesName && !matchesRoleCode && !matchesRoleName) {
                    continue;
                }
            }

            PrintUserSearchDto dto = PrintUserSearchDto.builder()
                    .userId(uid)
                    .username(username)
                    .fullName(fullName)
                    .email(email)
                    .department(department)
                    .assignedRoleCodes(roleCodes)
                    .assignedRoleNames(roleNames)
                    .isActive(isActive)
                    .isBlocked(isBlocked)
                    .accountStatus(accountStatus)
                    .displayLabel(displayLabel)
                    .build();

            dtoList.add(dto);
        }

        // 5. Pagination
        int totalElements = dtoList.size();
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, size);
        int fromIndex = Math.min(safePage * safeSize, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);
        List<PrintUserSearchDto> pageContent = dtoList.subList(fromIndex, toIndex);

        return new org.springframework.data.domain.PageImpl<>(
                pageContent,
                PageRequest.of(safePage, safeSize),
                totalElements
        );
    }

    private String resolveTenant(String reqTenant, String actor) {
        if (StringUtils.hasText(reqTenant)) {
            return reqTenant.trim();
        }
        return securityContextService.resolveEffectiveTenantId(actor, DEFAULT_TENANT_ID);
    }
}

