package com.adavis.mdm.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "mdm_print_permissions")
@CompoundIndex(def = "{'tenantId': 1, 'targetType': 1, 'targetId': 1, 'module': 1, 'reportType': 1}", name = "print_perm_tenant_target_mod_idx")
public class PrintPermission {

    @Id
    private String id;

    @Indexed
    private String tenantId;

    /**
     * Target type: "ROLE" or "USER"
     */
    @Indexed
    private String targetType;

    /**
     * Role ID (e.g., "ROLE-0017") or User ID (e.g., "USR-0001")
     */
    @Indexed
    private String targetId;

    private String targetName;
    private String targetCode;

    /**
     * Module identifier: "IIOT", "L2_INTEGRATION", "MDM", or "*"
     */
    @Indexed
    private String module;

    /**
     * Report type: "BATCH_DOSSIER", "OEE_REPORT", "CPP_TRENDS_REPORT", "L2_RECIPE_REPORT", "L2_BATCH_RECORD", "ALL", etc.
     */
    @Indexed
    private String reportType;

    /**
     * Allowed actions: "VIEW", "PRINT", "REPRINT", "MANAGE_PERMISSIONS", "PRINT_AUDIT"
     */
    @Builder.Default
    private Set<String> allowedActions = new HashSet<>();

    /**
     * Explicitly denied actions (applies to user-level overrides to revoke inherited role permissions)
     */
    @Builder.Default
    private Set<String> deniedActions = new HashSet<>();

    @Builder.Default
    private Boolean isActive = true;

    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
    private String remarks;
}

