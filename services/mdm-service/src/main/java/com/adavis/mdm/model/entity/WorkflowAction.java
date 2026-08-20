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
@Document(collection = "mdm_workflow_actions")
public class WorkflowAction {

    @JsonIgnore
    @Id
    private String id;

    @Indexed(unique = true)
    private String actionId;

    @Indexed
    private String actionCode;

    private String actionName;

    private String displayName;

    /**
     * SUBMIT, REVIEW, APPROVE, REJECT, DEFER, JUSTIFY
     */
    private String actionType;

    @Indexed
    private String applicableRole;

    private Boolean requiresEsign;

    private Boolean requiresComment;

    private Boolean requiresJustification;

    private Boolean requiresUserSelection;

    private Boolean requiresConfirmation;

    @Indexed
    private String tenantId;

    @Indexed
    private String plantId;

    private Boolean isActive;

    private Instant createdAt;

    private Instant updatedAt;
}
