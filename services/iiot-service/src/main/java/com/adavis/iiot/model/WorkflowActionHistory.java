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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Document(collection = "iiot_workflow_action_history")
public class WorkflowActionHistory {

    @JsonIgnore
    @Id
    private String id;

    @Indexed(unique = true)
    private String historyId;

    @Indexed
    private String instanceId;

    private String workflowCode;

    private String workflowVersion;

    @Indexed
    private String entityId;

    @Indexed
    private String batchNo;

    @Indexed
    private String lotNo;

    @Indexed
    private String equipmentCode;

    private String fromStageCode;

    private String toStageCode;

    @Indexed
    private String actionCode;

    private String actionName;

    private String previousStatus;

    private String newStatus;

    @Indexed
    private String performedBy;

    private String performerName;

    private String performerRole;

    private String comments;

    private String justification;

    private Boolean esignatureVerified;

    private String esignatureReason;

    @Indexed
    private String tenantId;

    @Indexed
    private String plantId;

    @Indexed
    private Instant timestamp;
}
