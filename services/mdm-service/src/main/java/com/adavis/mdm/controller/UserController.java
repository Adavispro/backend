package com.adavis.mdm.controller;

import com.adavis.common.dto.ApiResponse;
import com.adavis.common.dto.PageResponse;
import com.adavis.mdm.dto.request.UserOnboardingRequest;
import com.adavis.mdm.model.entity.UserProfile;
import com.adavis.mdm.security.InternalRequestValidator;
import com.adavis.mdm.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mdm/users")
@RequiredArgsConstructor
public class UserController {

    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";

    private final UserService userService;
    private final InternalRequestValidator internalRequestValidator;

    @PostMapping("/onboard")
    public ResponseEntity<ApiResponse<UserProfile>> createUser(
            @RequestHeader("X-User-Id") String currentUserId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @Valid @RequestBody UserOnboardingRequest request) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
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

        UserProfile created = userService.createUser(
            userProfile,
            request.getInitialPassword(),
            currentUserId,
            currentUserId,
            request.getSupportingDocumentIds(),
            request.getSupportingDocuments(),
            request.getSupportingDocumentType(),
            request.getReason());
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
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @Valid @RequestBody UserProfile userProfile) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        UserProfile updated = userService.updateUser(userId, userProfile);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", updated));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable String userId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        userService.deleteUser(userId);
        return ResponseEntity.ok(ApiResponse.successMessage("User deleted successfully"));
    }

    @PatchMapping("/{userId}/lifecycle")
    public ResponseEntity<ApiResponse<UserProfile>> updateUserLifecycle(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String currentUserId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestBody Map<String, Object> request) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        Object action = request == null ? null : request.get("action");
        String actionValue = action == null ? null : String.valueOf(action);
        String reason = request == null || request.get("reason") == null
            ? null
            : String.valueOf(request.get("reason"));
        String supportingDocumentType = request == null || request.get("supportingDocumentType") == null
            ? null
            : String.valueOf(request.get("supportingDocumentType"));
        UserProfile updated = userService.updateLifecycle(
            userId,
            actionValue,
            currentUserId,
            toStringList(request == null ? null : request.get("supportingDocumentIds")),
            toMapList(getSupportingDocumentsValue(request)),
            supportingDocumentType,
            reason);
        return ResponseEntity.ok(ApiResponse.success("User lifecycle updated successfully", updated));
    }

    @PostMapping("/{userId}/password-reset")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resetUserPassword(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String currentUserId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestBody(required = false) Map<String, Object> request) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
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
                currentUserId,
                toStringList(request == null ? null : request.get("supportingDocumentIds")),
                toMapList(getSupportingDocumentsValue(request)),
                supportingDocumentType,
                reason);
        return ResponseEntity.ok(ApiResponse.success("Password reset completed", response));
    }

        @PostMapping("/{userId}/deactivate")
        public ResponseEntity<ApiResponse<UserProfile>> deactivateUser(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String currentUserId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestBody(required = false) Map<String, Object> request) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        UserProfile updated = userService.updateLifecycle(
            userId,
            "deactivate",
            currentUserId,
            toStringList(request == null ? null : request.get("supportingDocumentIds")),
            toMapList(getSupportingDocumentsValue(request)),
            request == null ? null : String.valueOf(request.get("supportingDocumentType")),
            request == null ? null : String.valueOf(request.get("reason")));
        return ResponseEntity.ok(ApiResponse.success("User deactivated successfully", updated));
        }

        @PostMapping("/{userId}/activate")
        public ResponseEntity<ApiResponse<UserProfile>> reactivateUser(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String currentUserId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestBody(required = false) Map<String, Object> request) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        UserProfile updated = userService.updateLifecycle(
            userId,
            "reactivate",
            currentUserId,
            toStringList(request == null ? null : request.get("supportingDocumentIds")),
            toMapList(getSupportingDocumentsValue(request)),
            request == null ? null : String.valueOf(request.get("supportingDocumentType")),
            request == null ? null : String.valueOf(request.get("reason")));
        return ResponseEntity.ok(ApiResponse.success("User reactivated successfully", updated));
        }

        @PostMapping("/{userId}/block")
        public ResponseEntity<ApiResponse<UserProfile>> blockUser(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String currentUserId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestBody(required = false) Map<String, Object> request) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        UserProfile updated = userService.updateLifecycle(
            userId,
            "block",
            currentUserId,
            toStringList(request == null ? null : request.get("supportingDocumentIds")),
            toMapList(getSupportingDocumentsValue(request)),
            request == null ? null : String.valueOf(request.get("supportingDocumentType")),
            request == null ? null : String.valueOf(request.get("reason")));
        return ResponseEntity.ok(ApiResponse.success("User blocked successfully", updated));
        }

        @PostMapping("/{userId}/unblock")
        public ResponseEntity<ApiResponse<UserProfile>> unblockUser(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String currentUserId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestBody(required = false) Map<String, Object> request) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        UserProfile updated = userService.updateLifecycle(
            userId,
            "unblock",
            currentUserId,
            toStringList(request == null ? null : request.get("supportingDocumentIds")),
            toMapList(getSupportingDocumentsValue(request)),
            request == null ? null : String.valueOf(request.get("supportingDocumentType")),
            request == null ? null : String.valueOf(request.get("reason")));
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