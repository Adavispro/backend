package com.adavis.mdm.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EffectivePrintAccessResponse {

    private String userId;
    private String username;
    private String fullName;
    private String department;
    private String tenantId;
    private String module;
    private String reportType;

    @Builder.Default
    private List<String> assignedRoles = new ArrayList<>();

    @Builder.Default
    private List<String> authorizedModules = new ArrayList<>();

    @Builder.Default
    private Set<String> roleBaselineActions = new HashSet<>();

    @Builder.Default
    private Set<String> userOverrideAllowedActions = new HashSet<>();

    @Builder.Default
    private Set<String> userOverrideDeniedActions = new HashSet<>();

    /**
     * Set of effectively allowed actions: "VIEW", "PRINT", "REPRINT", "MANAGE_PERMISSIONS", "PRINT_AUDIT"
     */
    @Builder.Default
    private Set<String> allowedActions = new HashSet<>();

    /**
     * Set of explicitly denied actions
     */
    @Builder.Default
    private Set<String> deniedActions = new HashSet<>();

    /**
     * Provenance/Traceability of each action: e.g. "VIEW" -> "ROLE:ROLE-0017 (QA Approver)", "PRINT" -> "USER_OVERRIDE (Granted)"
     */
    @Builder.Default
    private Map<String, String> actionSources = new HashMap<>();

    private boolean isSuperAdmin;
    private boolean isDelegatedAdmin;

    public boolean canView() {
        return allowedActions.contains("VIEW");
    }

    public boolean canPrint() {
        return allowedActions.contains("PRINT");
    }

    public boolean canReprint() {
        return allowedActions.contains("REPRINT");
    }

    public boolean canManagePermissions() {
        return allowedActions.contains("MANAGE_PERMISSIONS");
    }

    public boolean canViewAudit() {
        return allowedActions.contains("PRINT_AUDIT");
    }
}

