package com.adavis.mdm.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintUserSearchDto {
    private String userId;           // e.g. "PRODUCTION_OPERATOR_1"
    private String username;         // e.g. "production_operator_1"
    private String fullName;         // e.g. "Production Operator 1"
    private String email;            // e.g. "production_operator_1@adavis.com"
    private String department;
    
    @Builder.Default
    private List<String> assignedRoleCodes = new ArrayList<>(); // e.g. ["PRODUCTION_OPERATOR"]
    
    @Builder.Default
    private List<String> assignedRoleNames = new ArrayList<>(); // e.g. ["Production Operator"]
    
    private boolean isActive;
    private boolean isBlocked;
    private String accountStatus;    // "ACTIVE", "BLOCKED", "INACTIVE"
    private String displayLabel;     // "production_operator_1 — Production Operator 1 — PRODUCTION_OPERATOR"
}

