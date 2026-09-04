package com.adavis.mdm.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "mdm_user_profiles")
public class UserProfile {

    @JsonIgnore
    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    @Indexed(unique = true)
    private String userTrackId;

    private String tenantId;

    @Indexed(unique = true)
    private String username;

    @Indexed(unique = true, sparse = true)
    private String email;

    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String title;
    private String userType;
    private String lifecycleStatus;
    private String empId;
    private String departmentId;
    private String designation;

    private Boolean isExternal;
    private Boolean isActive;

    private Boolean isBlocked;

    @Transient
    private List<Map<String, Object>> supportingDocuments;

    @Transient
    private String remarks;

    @Transient
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String esignPassword;

    @Transient
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String password;

    private Instant createdAt;
    private Instant updatedAt;
}