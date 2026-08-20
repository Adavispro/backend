package com.adavis.mdm.model.entity;

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
@Document(collection = "mdm_workflow_transitions")
public class WorkflowTransition {

    @JsonIgnore
    @Id
    private String id;

    @Indexed(unique = true)
    private String transitionId;

    @Indexed
    private String workflowCode;

    private String workflowVersion;

    @Indexed
    private String fromStageCode;

    @Indexed
    private String actionCode;

    private String toStageCode;

    private String resultingStatus;

    private String returnStageCode;

    private String condition;

    @Indexed
    private String tenantId;

    @Indexed
    private String plantId;

    private Boolean isActive;

    private Instant createdAt;

    private Instant updatedAt;
}
