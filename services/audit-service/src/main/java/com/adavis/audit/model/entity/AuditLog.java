package com.adavis.audit.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "mdm_audit_trails")
@CompoundIndex(name = "entity_idx", def = "{'entity': 1, 'entityId': 1}")
@CompoundIndex(name = "user_timestamp_idx", def = "{'userId': 1, 'timestamp': -1}")
@CompoundIndex(name = "tenant_timestamp_idx", def = "{'tenantId': 1, 'timestamp': -1}")
@CompoundIndex(name = "tenant_user_timestamp_idx", def = "{'tenantId': 1, 'userId': 1, 'timestamp': -1}")
public class AuditLog {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String username;

    @Indexed
    private String action; // CREATE, UPDATE, DELETE, LOGIN, LOGOUT, PERMISSION_CHANGE

    private String entity; // USER, GROUP, ROLE, PERMISSION, LICENSE, MODULE, SCREEN, FEATURE
    private String entityId;

    private Map<String, Object> before;
    private Map<String, Object> after;

    private Map<String, Object> metadata; // Additional context

    private String ipAddress;
    private String sessionId;
    private String userAgent;

    @Indexed
    private String tenantId;

    @Indexed
    private String status; // SUCCESS, FAILURE

    private String failureReason;

    @Indexed
    private Instant timestamp;

    private Integer version;
}