package com.adavis.mdm.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintPermissionRequest {

    private String tenantId;

    /**
     * "ROLE" or "USER"
     */
    @NotBlank(message = "targetType is required ('ROLE' or 'USER')")
    private String targetType;

    /**
     * Role ID or User ID
     */
    @NotBlank(message = "targetId is required")
    private String targetId;

    private String targetName;
    private String targetCode;

    /**
     * Module: "IIOT", "L2_INTEGRATION", "MDM", or "*"
     */
    @NotBlank(message = "module is required")
    private String module;

    /**
     * Report type: "BATCH_DOSSIER", "OEE_REPORT", "CPP_TRENDS_REPORT", "L2_RECIPE_REPORT", "ALL", etc.
     */
    @NotBlank(message = "reportType is required")
    private String reportType;

    /**
     * Set of allowed actions: VIEW, PRINT, REPRINT, MANAGE_PERMISSIONS, PRINT_AUDIT
     */
    @Builder.Default
    private Set<String> allowedActions = new HashSet<>();

    /**
     * Set of denied actions (for user override revoking role permission)
     */
    @Builder.Default
    private Set<String> deniedActions = new HashSet<>();

    private String remarks;
}

