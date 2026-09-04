package com.adavis.mdm.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserOnboardingRequest {

    @NotBlank(message = "User ID is required")
    private String userId;

    private String userTrackId;

    private String tenantId;

    private String username;

    @Email(message = "Valid email is required")
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

    private String itAdminUserId;
    private List<String> supportingDocumentIds;
    @JsonAlias("supportDocuments")
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<Map<String, Object>> supportingDocuments;
    private String supportingDocumentType;
    private String reason;
    private String remarks;

    @NotBlank(message = "Initial password is required")
    private String initialPassword;

    private String esignPassword;
    private String password;
}
