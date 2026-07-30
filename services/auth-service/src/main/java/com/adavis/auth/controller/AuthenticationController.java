package com.adavis.auth.controller;

import com.adavis.auth.dto.request.PasswordPolicyVerifyRequest;
import com.adavis.auth.service.AuthenticationService;
import com.adavis.auth.service.PasswordPolicyService;
import com.adavis.auth.service.SessionService;
import com.adavis.common.dto.ApiResponse;
import com.adavis.common.exception.BusinessException;
import com.adavis.dto.auth.request.*;
import com.adavis.dto.auth.response.AuthResponse;
import com.adavis.dto.auth.response.LoginInitiateResponse;
import com.adavis.dto.auth.response.SessionResponse;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping({"/api/v1/auth", "/api/auth"})
@RequiredArgsConstructor
@Slf4j
public class AuthenticationController {

    private static final Pattern JSON_FIELD_PATTERN_TEMPLATE =
            Pattern.compile("\"%s\"\\s*:\\s*(?:\"([^\"]*)\"|([^,}\\r\\n]+))");

    private final AuthenticationService authService;
    private final PasswordPolicyService passwordPolicyService;
    private final SessionService sessionService;

    // ============================================
    // LOGIN FLOW
    // ============================================

    /**
     * Step 1: Initiate login - Verify user exists and password is set
     */
    @PostMapping("/login-initiate")
    public ResponseEntity<ApiResponse<LoginInitiateResponse>> initiateLogin(
            @RequestBody(required = false) String requestBody) {
        LoginInitiateRequest request = parseLoginInitiateRequest(requestBody);
        log.info("Received login-initiate request for identifier={}", request.getIdentifier());
        LoginInitiateResponse response = authService.initiateLogin(request.getIdentifier());
        log.info("Completed login-initiate request for identifier={}", request.getIdentifier());
        return ResponseEntity.ok(ApiResponse.success("User verified", response));
    }

    /**
     * Step 2: Authenticate user with credentials
     */
    @PostMapping({"/authenticate", "/session-initialize"})
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @RequestBody(required = false) String requestBody,
            HttpServletRequest httpRequest) {
        LoginRequest request = parseLoginRequest(requestBody);
        String deviceInfo = httpRequest.getHeader("User-Agent");
        String ipAddress = getClientIp(httpRequest);
        log.info("Received authenticate request for identifier={} from ip={}", request.getIdentifier(), ipAddress);
        AuthResponse response = authService.login(
                request.getIdentifier(),
                request.getPassword(),
                deviceInfo,
                ipAddress
        );
        log.info("Completed authenticate request for identifier={} from ip={}", request.getIdentifier(), ipAddress);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    /**
     * Refresh access token and rotate refresh token if the session is still active.
     */
    @PostMapping({"/refresh", "/token-refresh"})
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @RequestBody(required = false) String requestBody,
            HttpServletRequest httpRequest) {
        RefreshTokenRequest request = parseRefreshTokenRequest(requestBody);
        String deviceInfo = httpRequest.getHeader("User-Agent");
        String ipAddress = getClientIp(httpRequest);
        AuthResponse response = authService.refreshTokenWithRotation(
                request.getRefreshToken(),
                deviceInfo,
                ipAddress
        );
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }

    /**
     * Return active sessions for the authenticated user.
     */
    @GetMapping("/sessions/active")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> getActiveSessions(
            @RequestHeader("Authorization") String token) {
        String userId = authService.getCurrentUser(token).getUserId();
        List<SessionResponse> sessions = sessionService.getActiveSessions(userId);
        return ResponseEntity.ok(ApiResponse.success("Active sessions fetched successfully", sessions));
    }

    /**
     * Logout - invalidate session and tokens
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletRequest httpRequest) {
        String deviceInfo = httpRequest.getHeader("User-Agent");
        String ipAddress = getClientIp(httpRequest);
        authService.logout(token, ipAddress, deviceInfo);
        return ResponseEntity.ok(ApiResponse.successMessage("Logged out successfully"));
    }

    @GetMapping({"/password-policy", "/password/policy"})
    public ResponseEntity<ApiResponse<PasswordPolicyService.PasswordPolicyInfo>> getPasswordPolicy() {
        return ResponseEntity.ok(ApiResponse.success(passwordPolicyService.getPolicyInfo()));
    }

    @PostMapping({"/password-policy/verify", "/password/policy/verify"})
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyPasswordPolicy(
            @Valid @RequestBody PasswordPolicyVerifyRequest request) {
        List<String> errors = passwordPolicyService.validateAndCollectErrors(request.getPassword());
        boolean valid = errors.isEmpty();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", valid);
        result.put("errors", errors);
        result.put("policy", passwordPolicyService.getPolicyInfo());

        String message = valid ? "Password satisfies policy" : "Password violates policy";
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private LoginInitiateRequest parseLoginInitiateRequest(String requestBody) {
        String identifier = extractIdentifier(requestBody);
        if (!StringUtils.hasText(identifier)) {
            throw new BusinessException("Identifier (userId or email) is required", "VALIDATION_ERROR");
        }

        return LoginInitiateRequest.builder()
                .identifier(identifier)
                .build();
    }

    private LoginRequest parseLoginRequest(String requestBody) {
        String identifier = extractIdentifier(requestBody);
        if (!StringUtils.hasText(identifier)) {
            throw new BusinessException("Identifier (userId or email) is required", "VALIDATION_ERROR");
        }

        String password = extractJsonField(requestBody, "password");
        if (!StringUtils.hasText(password)) {
            throw new BusinessException("Password is required", "VALIDATION_ERROR");
        }

        return LoginRequest.builder()
                .identifier(identifier)
                .password(password)
                .build();
    }

    private RefreshTokenRequest parseRefreshTokenRequest(String requestBody) {
        String refreshToken = extractJsonField(requestBody, "refreshToken");
        if (!StringUtils.hasText(refreshToken) && StringUtils.hasText(requestBody)) {
            String normalizedBody = stripWrappingQuotes(requestBody.trim());
            if (!normalizedBody.startsWith("{")) {
                refreshToken = normalizedBody;
            }
        }

        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException("Refresh token is required", "VALIDATION_ERROR");
        }

        return RefreshTokenRequest.builder()
                .refreshToken(refreshToken)
                .build();
    }

    private String extractIdentifier(String requestBody) {
        String identifier = extractJsonField(requestBody, "identifier");
        if (!StringUtils.hasText(identifier)) {
            identifier = extractJsonField(requestBody, "userId");
        }
        if (!StringUtils.hasText(identifier)) {
            identifier = extractJsonField(requestBody, "email");
        }
        if (!StringUtils.hasText(identifier)) {
            identifier = extractJsonField(requestBody, "username");
        }

        if (StringUtils.hasText(identifier)) {
            return identifier;
        }

        if (!StringUtils.hasText(requestBody)) {
            return null;
        }

        String normalizedBody = stripWrappingQuotes(requestBody.trim());
        if (!normalizedBody.startsWith("{")) {
            return normalizedBody;
        }
        return null;
    }

    private String extractJsonField(String requestBody, String fieldName) {
        if (!StringUtils.hasText(requestBody) || !StringUtils.hasText(fieldName)) {
            return null;
        }

        Pattern fieldPattern = Pattern.compile(String.format(JSON_FIELD_PATTERN_TEMPLATE.pattern(), Pattern.quote(fieldName)));
        Matcher matcher = fieldPattern.matcher(requestBody);
        if (!matcher.find()) {
            return null;
        }

        String quotedValue = matcher.group(1);
        if (quotedValue != null) {
            return quotedValue.trim();
        }

        String bareValue = matcher.group(2);
        return bareValue == null ? null : stripWrappingQuotes(bareValue.trim());
    }

    private String stripWrappingQuotes(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }

        String normalized = value.trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            return normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized;
    }
}