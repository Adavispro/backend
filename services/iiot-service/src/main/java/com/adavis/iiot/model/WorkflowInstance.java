package com.adavis.iiot.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Document(collection = "iiot_workflow_instances")
public class WorkflowInstance {

    @JsonIgnore
    @Id
    private String id;

    @Indexed(unique = true)
    private String instanceId;

    @Indexed
    private String workflowCode;

    /**
     * Retains the exact version active when this workflow instance started.
     */
    private String workflowVersion;

    @Indexed
    private String entityType;

    @Indexed
    private String entityId; // e.g. "batchNo:lotNo:equipmentCode"

    @Indexed
    private String batchNo;

    @Indexed
    private String lotNo;

    @Indexed
    private String equipmentCode;

    @Indexed
    private String tenantId;

    @Indexed
    private String plantId;

    @Indexed
    private String currentStageCode;

    @Indexed
    private String currentStatus;

    private String initiatedBy;

    private Instant initiatedAt;

    private String lastActionCode;

    private String lastActionBy;

    private Instant lastActionAt;

    private String assignedTo;

    private Boolean isTerminal;

    private Map<String, Object> context;

    private Instant createdAt;

    private Instant updatedAt;
}
