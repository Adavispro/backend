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
@Document(collection = "mdm_workflow_definitions")
public class WorkflowDefinition {

    @JsonIgnore
    @Id
    private String id;

    @Indexed(unique = true)
    private String workflowId;

    @Indexed
    private String workflowCode;

    private String workflowName;

    @Indexed
    private String module;

    @Indexed
    private String entity;

    private String description;

    private String version;

    /**
     * DRAFT, ACTIVE, RETIRED
     */
    @Indexed
    private String status;

    private Instant effectiveFrom;

    private Instant effectiveTo;

    @Indexed
    private String tenantId;

    @Indexed
    private String plantId;

    private List<String> stageCodes;

    private Boolean isActive;

    private String createdBy;

    private String updatedBy;

    private Instant createdAt;

    private Instant updatedAt;
}
