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
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Document(collection = "mdm_workflow_stages")
public class WorkflowStage {

    @JsonIgnore
    @Id
    private String id;

    @Indexed(unique = true)
    private String stageId;

    @Indexed
    private String workflowCode;

    private String workflowVersion;

    @Indexed
    private String stageCode;

    private String stageName;

    private Integer sequence;

    @Indexed
    private String assignedRole;

    /**
     * INITIAL, INTERMEDIATE, FINAL
     */
    private String stageType;

    private String entryStatus;

    private String exitStatus;

    /**
     * ROLE_BASED, USER_SPECIFIC, SUPERVISOR_ASSIGNED
     */
    private String assignmentRule;

    private Boolean isMandatory;

    private List<String> allowedActionCodes;

    @Indexed
    private String tenantId;

    @Indexed
    private String plantId;

    private Boolean isActive;

    private Instant createdAt;

    private Instant updatedAt;
}
