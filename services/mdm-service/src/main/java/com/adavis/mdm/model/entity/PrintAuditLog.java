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
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "mdm_print_audit_logs")
@CompoundIndex(def = "{'tenantId': 1, 'timestamp': -1}", name = "print_audit_tenant_time_idx")
@CompoundIndex(def = "{'userId': 1, 'timestamp': -1}", name = "print_audit_user_time_idx")
@CompoundIndex(def = "{'module': 1, 'reportType': 1, 'status': 1}", name = "print_audit_mod_rep_idx")
public class PrintAuditLog {

    @Id
    private String id;

    @Indexed
    private String tenantId;

    private String plantId;

    /**
     * Event category: "PERMISSION_CHANGE", "PRINT_ATTEMPT", "DELEGATION_CHANGE"
     */
    @Indexed
    private String eventCategory;

    /**
     * Action performed: "VIEW_PDF", "PRINT_PDF", "REPRINT_PDF",
     * "PERMISSION_GRANTED", "PERMISSION_REVOKED", "DELEGATION_GRANTED", "DELEGATION_REVOKED"
     */
    @Indexed
    private String action;

    /**
     * Status: "AUTHORIZED" (or "SUCCESS") vs "DENIED" (or "FAILED")
     */
    @Indexed
    private String status;

    @Indexed
    private String userId;

    private String username;
    private String userRole;

    private String module;
    private String reportType;
    private String documentId;     // e.g. batchNo "B260901"
    private Integer copyNumber;

    private String justificationReason; // reason entered during print/reprint or permission change
    private String denialReason;        // reason why access was denied (if status is DENIED)

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @Indexed
    private Instant timestamp;
}

