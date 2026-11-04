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
@Document(collection = "mdm_print_delegations")
@CompoundIndex(def = "{'tenantId': 1, 'delegateeType': 1, 'delegateeId': 1, 'isActive': 1}", name = "print_delegation_idx")
public class PrintDelegation {

    @Id
    private String id;

    @Indexed
    private String tenantId;

    /**
     * Delegatee type: "USER" or "ROLE"
     */
    private String delegateeType;

    /**
     * User ID or Role ID who is receiving the delegation
     */
    @Indexed
    private String delegateeId;

    private String delegateeName;

    /**
     * Module scope for this delegation: e.g. ["IIOT", "L2_INTEGRATION"] or ["*"] for all
     */
    @Builder.Default
    private Set<String> allowedModules = new HashSet<>();

    /**
     * Whether this delegatee can further delegate to others (Default false; only Super Admin can sub-delegate)
     */
    @Builder.Default
    private Boolean canSubDelegate = false;

    /**
     * Super Admin who issued this delegation
     */
    private String delegatedBy;
    private Instant delegatedAt;

    /**
     * Optional expiration timestamp
     */
    private Instant expiresAt;

    @Builder.Default
    private Boolean isActive = true;

    private String remarks;

    private String revokedBy;
    private Instant revokedAt;
    private String revocationReason;
}

