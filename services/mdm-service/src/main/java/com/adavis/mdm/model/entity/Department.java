package com.adavis.mdm.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Document(collection = "mdm_departments")
public class Department {

    @JsonIgnore
    @Id
    private String id;

    @Indexed(unique = true)
    private String departmentId;

    private String tenantId;

    private String plantId;

    private String departmentCode;

    private String departmentName;

    private String path;

    private String name;
    private String description;
    private String parentDepartmentId;
    private Boolean isActive;

    @org.springframework.data.annotation.Transient
    private String remarks;

    @org.springframework.data.annotation.Transient
    private String reason;

    @org.springframework.data.annotation.Transient
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String esignPassword;

    @org.springframework.data.annotation.Transient
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String password;

    private Instant createdAt;
    private Instant updatedAt;
}