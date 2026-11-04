package com.adavis.mdm.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintDelegationRequest {

    private String tenantId;

    /**
     * "USER" or "ROLE"
     */
    @NotBlank(message = "delegateeType is required ('USER' or 'ROLE')")
    private String delegateeType;

    @NotBlank(message = "delegateeId is required")
    private String delegateeId;

    private String delegateeName;

    /**
     * Allowed modules: e.g. ["IIOT"], ["L2_INTEGRATION"], ["*"]
     */
    @Builder.Default
    private Set<String> allowedModules = new HashSet<>();

    private Instant expiresAt;

    private String remarks;
}

