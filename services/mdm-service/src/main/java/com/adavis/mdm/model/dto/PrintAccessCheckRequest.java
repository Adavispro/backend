package com.adavis.mdm.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintAccessCheckRequest {

    private String tenantId;

    @NotBlank(message = "userId is required")
    private String userId;

    private String userRole;

    @NotBlank(message = "module is required")
    private String module;

    @NotBlank(message = "reportType is required")
    private String reportType;

    /**
     * Action to check: "VIEW", "PRINT", "REPRINT", "MANAGE_PERMISSIONS", "PRINT_AUDIT"
     */
    @NotBlank(message = "action is required")
    private String action;

    private String documentId;
    private Integer copyNumber;
    private String reason;
}

