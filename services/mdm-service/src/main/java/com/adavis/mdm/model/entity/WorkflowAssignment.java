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
@Document(collection = "mdm_workflow_assignments")
public class WorkflowAssignment {

    @JsonIgnore
    @Id
    private String id;

    @Indexed(unique = true)
    private String assignmentId;

    @Indexed
    private String workflowCode;

    private String stageCode;

    @Indexed
    private String roleCode;

    private String groupId;

    /**
     * ALL_IN_ROLE, ASSIGNED_PLANT, DEPT_MATCH
     */
    private String eligibleUserRule;

    /**
     * MANUAL_SELECT, AUTO_ROUND_ROBIN, SUPERVISOR_DIRECT
     */
    private String assignmentRule;

    @Indexed
    private String tenantId;

    @Indexed
    private String plantId;

    private String departmentId;

    private Boolean isActive;

    private Instant createdAt;

    private Instant updatedAt;
}
