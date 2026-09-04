package com.adavis.auth.controller;

import com.adavis.auth.dto.request.UserProvisionRequest;
import com.adavis.auth.dto.request.UserStatusUpdateRequest;
import com.adavis.auth.service.AuthenticationService;
import com.adavis.auth.service.SessionService;
import com.adavis.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/v1/auth/users")
@RequiredArgsConstructor
public class InternalAuthProvisionController {

    private final AuthenticationService authService;
    private final SessionService sessionService;

    @PostMapping("/provision")
    public ResponseEntity<ApiResponse<Void>> provisionUser(
            @RequestHeader(value = "X-User-Id", required = false) String actorUserId,
            @Valid @RequestBody UserProvisionRequest request) {
        authService.provisionUserWithInitialPassword(
                request.getUserId(), request.getUsername(), request.getEmail(), request.getInitialPassword(), actorUserId);
        return ResponseEntity.ok(ApiResponse.successMessage("User provisioned with initial password"));
    }

    @PostMapping("/status")
    public ResponseEntity<ApiResponse<Void>> updateUserStatus(
            @RequestHeader(value = "X-User-Id", required = false) String actorUserId,
            @Valid @RequestBody UserStatusUpdateRequest request) {
        authService.updateUserStatus(request.getUserId(), request.getStatus(), request.getIsLocked(), actorUserId);
        return ResponseEntity.ok(ApiResponse.successMessage("User status updated"));
    }

    @GetMapping("/{userId}/lock-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserLockStatus(@PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(authService.getUserLockStatus(userId)));
    }

    @GetMapping("/session-presence-summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSessionPresenceSummary(
            @RequestParam(required = false) String tenantId) {
        return ResponseEntity.ok(ApiResponse.success(sessionService.getSessionPresenceSummary(tenantId)));
    }
}