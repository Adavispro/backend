package com.adavis.mdm.controller;

import com.adavis.common.dto.ApiResponse;
import com.adavis.common.dto.PageResponse;
import com.adavis.mdm.dto.request.UserOnboardingRequest;
import com.adavis.mdm.model.entity.UserProfile;
import com.adavis.mdm.security.InternalRequestValidator;
import com.adavis.mdm.security.SecurityContextService;
import com.adavis.mdm.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mdm/users")
@RequiredArgsConstructor
public class UserController {

    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";
    private static final String USER_ID_HEADER = "X-User-Id";

    private final UserService userService;
    private final InternalRequestValidator internalRequestValidator;
    private final SecurityContextService securityContextService;

    @PostMapping("/onboard")
    public ResponseEntity<ApiResponse<UserProfile>> createUser(
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @Valid @RequestBody UserOnboardingRequest request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        UserProfile userProfile = UserProfile.builder()
                .userId(request.getUserId())
            .userTrackId(request.getUserTrackId())
            .tenantId(request.getTenantId())
                .username(request.getUsername())
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
            .phoneNumber(request.getPhoneNumber())
            .title(request.getTitle())
            .userType(request.getUserType())
            .lifecycleStatus(request.getLifecycleStatus())
            .empId(request.getEmpId())
                .departmentId(request.getDepartmentId())
                .designation(request.getDesignation())
                .isExternal(request.getIsExternal())
            .isActive(request.getIsActive())
                .build();

        String remarks = StringUtils.hasText(request.getRemarks()) ? request.getRemarks() : request.getReason();
        String esignPassword = StringUtils.hasText(request.getEsignPassword()) ? request.getEsignPassword() : request.getPassword();

        UserProfile created = userService.createUser(
            userProfile,
            request.getInitialPassword(),
            actor,
            actor,
            request.getSupportingDocumentIds(),
            request.getSupportingDocuments(),
            request.getSupportingDocumentType(),
            remarks,
            remarks,
            esignPassword);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully", created));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserProfile>> getUser(@PathVariable String userId) {
        UserProfile user = userService.getUserByUserId(userId);
        user.setUsername(null);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @GetMapping("/{userId}/login-context")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLoginContext(
            @PathVariable String userId,
            @RequestParam(defaultValue = "true") Boolean includePermissionMatrix) {
        Map<String, Object> context = userService.getLoginContext(userId, includePermissionMatrix);
        return ResponseEntity.ok(ApiResponse.success(context));
    }

    @PostMapping("/{userId}/select-plant")
    public ResponseEntity<ApiResponse<Map<String, Object>>> selectPlantContext(
            @PathVariable String userId,
            @RequestBody Map<String, Object> request,
            @RequestParam(defaultValue = "true") Boolean includePermissionMatrix) {
        String plantId = request == null || request.get("plantId") == null
                ? null
                : String.valueOf(request.get("plantId"));
        Map<String, Object> context = userService.selectPlantContext(userId, plantId, includePermissionMatrix);
        return ResponseEntity.ok(ApiResponse.success("Plant selected successfully", context));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<UserProfile>>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) Boolean isBlocked,
            @RequestParam(required = false) String lifecycleStatus,
            @RequestParam(required = false) String sessionPresence,
            @RequestParam(required = false) String tenantId) {
        Pageable pageable = PageRequest.of(page, size);
        Page<UserProfile> userPage = userService.getAllUsers(pageable, isActive, isBlocked, lifecycleStatus, sessionPresence, tenantId);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(userPage)));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserProfile>> updateUser(
            @PathVariable String userId,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @Valid @RequestBody UserProfile userProfile,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        String remarks = userProfile.getRemarks();
        String esignPassword = StringUtils.hasText(userProfile.getEsignPassword())
                ? userProfile.getEsignPassword()
                : userProfile.getPassword();
        UserProfile updated = userService.updateUser(userId, userProfile, actor, remarks, esignPassword);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", updated));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable String userId,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestParam(required = false) String remarks,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) String esignPassword,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        String effectiveRemarks = request != null && request.get("remarks") != null
                ? String.valueOf(request.get("remarks"))
                : remarks;
        if (!StringUtils.hasText(effectiveRemarks) && request != null && request.get("reason") != null) {
            effectiveRemarks = String.valueOf(request.get("reason"));
        }
        String effectivePassword = request != null && request.get("esignPassword") != null
                ? String.valueOf(request.get("esignPassword"))
                : (request != null && request.get("password") != null
                        ? String.valueOf(request.get("password"))
                        : (StringUtils.hasText(esignPassword) ? esignPassword : password));
        userService.deleteUser(userId, actor, effectiveRemarks, effectivePassword);
        return ResponseEntity.ok(ApiResponse.successMessage("User deleted successfully"));
    }

    @PatchMapping("/{userId}/lifecycle")
    public ResponseEntity<ApiResponse<UserProfile>> updateUserLifecycle(
            @PathVariable String userId,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        Object action = request == null ? null : request.get("action");
        String actionValue = action == null ? null : String.valueOf(action);
        String reason = request == null || request.get("reason") == null
            ? null
            : String.valueOf(request.get("reason"));
        String remarks = request == null || request.get("remarks") == null
            ? null
            : String.valueOf(request.get("remarks"));
        String password = request == null || request.get("password") == null
            ? null
            : String.valueOf(request.get("password"));
        String esignPassword = request == null || request.get("esignPassword") == null
            ? password
            : String.valueOf(request.get("esignPassword"));
        String supportingDocumentType = request == null || request.get("supportingDocumentType") == null
            ? null
            : String.valueOf(request.get("supportingDocumentType"));
        UserProfile updated = userService.updateLifecycle(
            userId,
            actionValue,
            actor,
            toStringList(request == null ? null : request.get("supportingDocumentIds")),
            toMapList(getSupportingDocumentsValue(request)),
            supportingDocumentType,
            reason,
            remarks,
            esignPassword);
        return ResponseEntity.ok(ApiResponse.success("User lifecycle updated successfully", updated));
    }

    @PostMapping("/{userId}/password-reset")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resetUserPassword(
            @PathVariable String userId,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        String tempPassword = request == null || request.get("tempPassword") == null
                ? null
                : String.valueOf(request.get("tempPassword"));
        String reason = request == null || request.get("reason") == null
                ? null
                : String.valueOf(request.get("reason"));
        String supportingDocumentType = request == null || request.get("supportingDocumentType") == null
                ? null
                : String.valueOf(request.get("supportingDocumentType"));
        Map<String, Object> response = userService.adminResetPassword(
                userId,
            null,
                tempPassword,
                actor,
                toStringList(request == null ? null : request.get("supportingDocumentIds")),
                toMapList(getSupportingDocumentsValue(request)),
                supportingDocumentType,
                reason);
        return ResponseEntity.ok(ApiResponse.success("Password reset completed", response));
    }

    @PostMapping("/{userId}/deactivate")
    public ResponseEntity<ApiResponse<UserProfile>> deactivateUser(
            @PathVariable String userId,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        String remarks = request == null || request.get("remarks") == null ? null : String.valueOf(request.get("remarks"));
        String password = request == null || request.get("password") == null ? null : String.valueOf(request.get("password"));
        String esignPassword = request == null || request.get("esignPassword") == null ? password : String.valueOf(request.get("esignPassword"));
        UserProfile updated = userService.updateLifecycle(
            userId,
            "deactivate",
            actor,
            toStringList(request == null ? null : request.get("supportingDocumentIds")),
            toMapList(getSupportingDocumentsValue(request)),
            request == null ? null : String.valueOf(request.get("supportingDocumentType")),
            request == null ? null : String.valueOf(request.get("reason")),
            remarks,
            esignPassword);
        return ResponseEntity.ok(ApiResponse.success("User deactivated successfully", updated));
    }

    @PostMapping("/{userId}/activate")
    public ResponseEntity<ApiResponse<UserProfile>> reactivateUser(
            @PathVariable String userId,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        String remarks = request == null || request.get("remarks") == null ? null : String.valueOf(request.get("remarks"));
        String password = request == null || request.get("password") == null ? null : String.valueOf(request.get("password"));
        String esignPassword = request == null || request.get("esignPassword") == null ? password : String.valueOf(request.get("esignPassword"));
        UserProfile updated = userService.updateLifecycle(
            userId,
            "reactivate",
            actor,
            toStringList(request == null ? null : request.get("supportingDocumentIds")),
            toMapList(getSupportingDocumentsValue(request)),
            request == null ? null : String.valueOf(request.get("supportingDocumentType")),
            request == null ? null : String.valueOf(request.get("reason")),
            remarks,
            esignPassword);
        return ResponseEntity.ok(ApiResponse.success("User reactivated successfully", updated));
    }

    @PostMapping("/{userId}/block")
    public ResponseEntity<ApiResponse<UserProfile>> blockUser(
            @PathVariable String userId,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        String remarks = request == null || request.get("remarks") == null ? null : String.valueOf(request.get("remarks"));
        String password = request == null || request.get("password") == null ? null : String.valueOf(request.get("password"));
        String esignPassword = request == null || request.get("esignPassword") == null ? password : String.valueOf(request.get("esignPassword"));
        UserProfile updated = userService.updateLifecycle(
            userId,
            "block",
            actor,
            toStringList(request == null ? null : request.get("supportingDocumentIds")),
            toMapList(getSupportingDocumentsValue(request)),
            request == null ? null : String.valueOf(request.get("supportingDocumentType")),
            request == null ? null : String.valueOf(request.get("reason")),
            remarks,
            esignPassword);
        return ResponseEntity.ok(ApiResponse.success("User blocked successfully", updated));
    }

    @PostMapping("/{userId}/unblock")
    public ResponseEntity<ApiResponse<UserProfile>> unblockUser(
            @PathVariable String userId,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        String remarks = request == null || request.get("remarks") == null ? null : String.valueOf(request.get("remarks"));
        String password = request == null || request.get("password") == null ? null : String.valueOf(request.get("password"));
        String esignPassword = request == null || request.get("esignPassword") == null ? password : String.valueOf(request.get("esignPassword"));
        UserProfile updated = userService.updateLifecycle(
            userId,
            "unblock",
            actor,
            toStringList(request == null ? null : request.get("supportingDocumentIds")),
            toMapList(getSupportingDocumentsValue(request)),
            request == null ? null : String.valueOf(request.get("supportingDocumentType")),
            request == null ? null : String.valueOf(request.get("reason")),
            remarks,
            esignPassword);
        return ResponseEntity.ok(ApiResponse.success("User unblocked successfully", updated));
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object entry : rawList) {
            if (entry != null) {
                String normalized = String.valueOf(entry).trim();
                if (!normalized.isEmpty()) {
                    out.add(normalized);
                }
            }
        }
        return out;
    }

    private List<Map<String, Object>> toMapList(Object value) {
        if (value instanceof Map<?, ?> map) {
            return List.of(map.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                    e -> String.valueOf(e.getKey()),
                    Map.Entry::getValue,
                    (left, right) -> right,
                    java.util.LinkedHashMap::new)));
        }
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object entry : rawList) {
            if (entry instanceof Map<?, ?> map) {
                out.add(map.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        e -> String.valueOf(e.getKey()),
                        Map.Entry::getValue,
                        (left, right) -> right,
                        java.util.LinkedHashMap::new)));
            }
        }
        return out;
    }

    private Object getSupportingDocumentsValue(Map<String, Object> request) {
        if (request == null) {
            return null;
        }
        Object supportingDocuments = request.get("supportingDocuments");
        return supportingDocuments != null ? supportingDocuments : request.get("supportDocuments");
    }
}