package com.adavis.auth.service;

import com.adavis.auth.model.entity.Credential;
import com.adavis.auth.model.entity.Session;
import com.adavis.auth.model.entity.User;
import com.adavis.auth.repository.CredentialRepository;
import com.adavis.auth.repository.SessionRepository;
import com.adavis.auth.repository.UserRepository;
import com.adavis.common.exception.BusinessException;
import com.adavis.common.exception.ResourceNotFoundException;
import com.adavis.common.exception.UnauthorizedException;
import com.adavis.dto.auth.response.AuthResponse;
import com.adavis.dto.auth.response.CurrentUserResponse;
import com.adavis.dto.auth.response.LoginInitiateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthenticationService {

    private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionService sessionService;
    private final PasswordPolicyService passwordPolicyService;
    private final AuditEventPublisher auditEventPublisher;
    private final RedisTemplate<String, String> redisTemplate;
    private final MongoTemplate mongoTemplate;

    private static final String BLACKLIST_PREFIX = "blacklist:";
    private static final String SUPER_ADMIN_USER_ID = "SUPER_ADMIN";

    @Value("${services.mdm.base-url:http://mdm-service:9083}")
    private String mdmServiceBaseUrl;

    @Value("${services.license.base-url:http://license-service:8082}")
    private String licenseServiceBaseUrl;

    @Value("${auth.super-admin.default-tenant-id:TNT-0001}")
    private String superAdminDefaultTenantId;

    private final RestTemplate restTemplate = new RestTemplate();

    public LoginInitiateResponse initiateLogin(String identifier) {
        log.info("Initiating login lookup for identifier={}", identifier);
        User user = findUserByIdentifierForInitiate(identifier);
        validateUserCanLogin(user);
        log.info("Found user for login-initiate: userId={}, identifier={}", user.getUserId(), identifier);
        Credential credential = credentialRepository.findByUserId(user.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Password not set for this user"));

        log.info("Credential lookup completed for userId={}, passwordSet={}", user.getUserId(), credential.getPasswordHash() != null);

        return LoginInitiateResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .status(user.getStatus())
                .passwordSet(credential.getPasswordHash() != null)
                .build();
    }

    public AuthResponse login(String identifier, String password, String deviceInfo, String ipAddress) {
        User user = findUserByIdentifier(identifier);
        validateUserCanLogin(user);

        Credential credential = credentialRepository.findByUserId(user.getUserId())
                .orElseThrow(() -> new BusinessException("Credentials not configured", "NO_CREDENTIALS"));

        if (!passwordEncoder.matches(password, credential.getPasswordHash())) {
            user.setFailedAttempts(user.getFailedAttempts() == null ? 1 : user.getFailedAttempts() + 1);
            if (user.getFailedAttempts() >= 5) {
                user.setIsLocked(true);
            }
            userRepository.save(user);
            String failedTenantId = SUPER_ADMIN_USER_ID.equalsIgnoreCase(user.getUserId())
                    ? firstNonBlank(resolveTenantIdSilently(user.getUserId()), superAdminDefaultTenantId)
                    : resolveTenantIdSilently(user.getUserId());
            auditEventPublisher.publish(user.getUserId(), user.getUsername(), "LOGIN", "AUTH_USER", user.getUserId(),
                    "FAILURE", "Invalid credentials", Map.of(
                            "identifier", identifier,
                            "tenantId", failedTenantId == null ? "" : failedTenantId));
                throw new BusinessException("Invalid credentials", "INVALID_CREDENTIALS");
        }

        user.setFailedAttempts(0);
        user.setIsLocked(false);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String tenantId = null;
        if (SUPER_ADMIN_USER_ID.equalsIgnoreCase(user.getUserId())) {
            tenantId = firstNonBlank(resolveTenantIdSilently(user.getUserId()), superAdminDefaultTenantId);
        } else {
            tenantId = resolveTenantId(user.getUserId());
            validateTenantLicenseForLogin(tenantId, user.getUserId());
        }

        String refreshToken = jwtService.generateRefreshToken(user.getUserId(), user.getUsername());

        Session session = sessionService.createOrExtendSession(user.getUserId(), tenantId, refreshToken, deviceInfo, ipAddress);
        List<String> roles = List.of();
        String accessToken = jwtService.generateAccessToken(user.getUserId(), user.getUsername(), roles, session.getSessionId());
        auditEventPublisher.publish(user.getUserId(), user.getUsername(), "LOGIN", "AUTH_USER", user.getUserId(),
            "SUCCESS", null, Map.of(
                "ipAddress", ipAddress == null ? "" : ipAddress,
            "tenantId", tenantId == null ? "" : tenantId,
            "sessionId", session.getSessionId() == null ? "" : session.getSessionId(),
            "userAgent", deviceInfo == null ? "" : deviceInfo));

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600000L)
                .refreshExpiresIn(86400000L)
            .sessionId(session.getSessionId())
                .userId(user.getUserId())
                .tenantId(tenantId)
                .build();
    }

    public AuthResponse refreshTokenWithRotation(String refreshToken, String deviceInfo, String ipAddress) {
        sessionService.expireSessions();
        if (!jwtService.validateToken(refreshToken)) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        String userId = jwtService.extractUserId(refreshToken);
        if (userId == null) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        if (isTokenBlacklisted(refreshToken)) {
            throw new UnauthorizedException("Refresh token has been revoked");
        }

        Session existingSession = sessionRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new UnauthorizedException("Session not found"));

        if (!Boolean.TRUE.equals(existingSession.getIsActive())) {
            throw new UnauthorizedException("Session is inactive");
        }

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        validateUserCanLogin(user);

        blacklistToken(refreshToken, jwtService.getExpirationDate(refreshToken));

        String newRefreshToken = jwtService.generateRefreshToken(user.getUserId(), user.getUsername());

        existingSession.setIsActive(false);
        sessionRepository.save(existingSession);

        String tenantId = SUPER_ADMIN_USER_ID.equalsIgnoreCase(user.getUserId())
            ? firstNonBlank(resolveTenantIdSilently(user.getUserId()), superAdminDefaultTenantId)
            : resolveTenantIdSilently(user.getUserId());
        Session session = sessionService.createOrExtendSession(user.getUserId(), tenantId, newRefreshToken, deviceInfo, ipAddress);
        List<String> roles = List.of();
        String newAccessToken = jwtService.generateAccessToken(user.getUserId(), user.getUsername(), roles, session.getSessionId());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(3600000L)
                .refreshExpiresIn(86400000L)
            .sessionId(session.getSessionId())
                .userId(user.getUserId())
            .tenantId(tenantId)
                .build();
    }

    public AuthResponse refreshToken(String refreshToken) {
        sessionService.expireSessions();
        if (!jwtService.validateToken(refreshToken)) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        String userId = jwtService.extractUserId(refreshToken);
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        validateUserCanLogin(user);

        Session existingSession = sessionRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new UnauthorizedException("Session not found"));
        if (!Boolean.TRUE.equals(existingSession.getIsActive())) {
            throw new UnauthorizedException("Session is inactive");
        }

        existingSession.setLastActivity(Instant.now());
        existingSession.setExpiresAt(Instant.now().plusSeconds(30L * 60L));
        sessionRepository.save(existingSession);
        String newAccessToken = jwtService.generateAccessToken(userId, user.getUsername(), List.of(), existingSession.getSessionId());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600000L)
                .refreshExpiresIn(86400000L)
                .sessionId(existingSession.getSessionId())
                .userId(userId)
                .tenantId(existingSession.getTenantId())
                .build();
    }

    public void logout(String token, String ipAddress, String deviceInfo) {
        String userId = null;
        String username = null;
        String bearerToken = token;

        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            bearerToken = bearerToken.substring(7);
        }

        try {
            if (bearerToken != null && !bearerToken.isBlank()) {
                userId = jwtService.extractUserId(bearerToken);
                username = jwtService.extractUsername(bearerToken);
            }
        } catch (Exception ignored) {
        }

        if (bearerToken != null && !bearerToken.isBlank()) {
            Date expiry = jwtService.getExpirationDate(bearerToken);
            if (expiry != null) {
                blacklistToken(bearerToken, expiry);
            }
        }

        if (userId != null) {
            sessionService.terminateLatestActiveSession(userId, ipAddress, deviceInfo);
        } else if (bearerToken != null && !bearerToken.isBlank()) {
            sessionService.terminateSessionByRefreshToken(bearerToken);
        }

        auditEventPublisher.publish(userId, username, "LOGOUT", "AUTH_USER", userId,
                "SUCCESS", null, Map.of(
                        "ipAddress", ipAddress == null ? "" : ipAddress,
                        "userAgent", deviceInfo == null ? "" : deviceInfo));
    }

    public void provisionUserWithInitialPassword(String userId, String username, String email, String initialPassword) {
        if (initialPassword == null || initialPassword.isBlank()) {
            throw new BusinessException("Initial password is required", "INITIAL_PASSWORD_REQUIRED");
        }

        User user = upsertActiveUser(userId, username, email);
        passwordPolicyService.validateOrThrow(initialPassword);

        Credential credential = getOrCreateCredential(userId, email);
        credential.setPasswordHash(passwordEncoder.encode(initialPassword));
        credential.setMustChangePassword(true);
        credential.setPasswordUpdatedAt(Instant.now());
        credential.setUpdatedAt(Instant.now());
        credentialRepository.save(credential);

        auditEventPublisher.publish(userId, username, "USER_PROVISION", "AUTH_USER", userId,
                "SUCCESS", null, Map.of("mode", "INITIAL_PASSWORD"));
    }

    public void updateUserStatus(String userId, String status, Boolean isLocked) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException("User ID is required", "USER_ID_REQUIRED");
        }
        if (status == null || status.isBlank()) {
            throw new BusinessException("Status is required", "STATUS_REQUIRED");
        }

        String normalizedStatus = status.trim().toUpperCase();
        if (!"ACTIVE".equals(normalizedStatus)
                && !"DEACTIVATED".equals(normalizedStatus)
                && !"BLOCKED".equals(normalizedStatus)) {
            throw new BusinessException("Unsupported status: " + status, "INVALID_STATUS");
        }

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        user.setStatus(normalizedStatus);
        if (isLocked != null) {
            user.setIsLocked(isLocked);
        } else if ("BLOCKED".equals(normalizedStatus)) {
            user.setIsLocked(true);
        } else if ("ACTIVE".equals(normalizedStatus)) {
            user.setIsLocked(false);
        }
        if (!"BLOCKED".equals(normalizedStatus)) {
            user.setFailedAttempts(0);
        }
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        auditEventPublisher.publish(userId, user.getUsername(), "USER_STATUS_UPDATED", "AUTH_USER", userId,
                "SUCCESS", null, Map.of(
                        "status", normalizedStatus,
                        "isLocked", String.valueOf(Boolean.TRUE.equals(user.getIsLocked()))));
    }

    public CurrentUserResponse getCurrentUser(String token) {
        String bearerToken = token;
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            bearerToken = bearerToken.substring(7);
        }

        String userId = jwtService.extractUserId(bearerToken);
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        String[] nameParts = deriveNameParts(user);

        return CurrentUserResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .status(user.getStatus())
                .firstName(nameParts[0])
                .lastName(nameParts[1])
                .build();
    }

    public Map<String, Object> getUserLockStatus(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        int failedAttempts = user.getFailedAttempts() == null ? 0 : user.getFailedAttempts();
        boolean isLocked = failedAttempts >= 5;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", user.getUserId());
        payload.put("failedAttempts", failedAttempts);
        payload.put("isLocked", isLocked);
        return payload;
    }

    private User findUserByIdentifier(String identifier) {
        String normalizedIdentifier = identifier == null ? null : identifier.trim();
        return userRepository.findByUserId(normalizedIdentifier)
            .or(() -> userRepository.findByUsername(normalizedIdentifier))
            .or(() -> userRepository.findByEmail(normalizedIdentifier))
            .or(() -> userRepository.findByUserIdIgnoreCase(normalizedIdentifier))
            .or(() -> userRepository.findByUsernameIgnoreCase(normalizedIdentifier))
            .or(() -> userRepository.findByEmailIgnoreCase(normalizedIdentifier))
            .orElseThrow(() -> new UnauthorizedException("User not found"));
    }

    private User findUserByIdentifierForInitiate(String identifier) {
        String normalizedIdentifier = identifier == null ? null : identifier.trim();
        return userRepository.findByUserId(normalizedIdentifier)
            .or(() -> userRepository.findByUsername(normalizedIdentifier))
            .or(() -> userRepository.findByEmail(normalizedIdentifier))
            .or(() -> userRepository.findByUserIdIgnoreCase(normalizedIdentifier))
            .or(() -> userRepository.findByUsernameIgnoreCase(normalizedIdentifier))
            .or(() -> userRepository.findByEmailIgnoreCase(normalizedIdentifier))
            .orElseThrow(() -> new ResourceNotFoundException("User", normalizedIdentifier));
    }

    private User upsertActiveUser(String userId, String username, String email) {
        User user = userRepository.findByUserId(userId)
                .orElse(User.builder().userId(userId).createdAt(Instant.now()).build());

        user.setUsername(username);
        user.setEmail(email);
        user.setStatus("ACTIVE");
        user.setIsLocked(false);
        user.setFailedAttempts(0);
        user.setIsDeleted(false);
        user.setUpdatedAt(Instant.now());
        return userRepository.save(user);
    }

    private Credential getOrCreateCredential(String userId, String email) {
        Credential credential = credentialRepository.findByUserId(userId)
                .orElse(Credential.builder().userId(userId).createdAt(Instant.now()).build());
        if (email != null && !email.isBlank()) {
            credential.setEmail(email);
        }
        return credential;
    }

    private void validateUserCanLogin(User user) {
        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new BusinessException("Account has been deleted", "ACCOUNT_DELETED");
        }

        String normalizedStatus = user.getStatus() == null ? "" : user.getStatus().trim().toUpperCase();
        if (!"ACTIVE".equals(normalizedStatus)) {
            if ("BLOCKED".equals(normalizedStatus) || Boolean.TRUE.equals(user.getIsLocked())) {
                throw new BusinessException("Account is locked or blocked", "ACCOUNT_LOCKED");
            }
            if ("DEACTIVATED".equals(normalizedStatus)) {
                throw new BusinessException("Account is deactivated", "ACCOUNT_DEACTIVATED");
            }
            throw new BusinessException("Account is not active", "ACCOUNT_INACTIVE");
        }

        if (Boolean.TRUE.equals(user.getIsLocked())) {
            throw new BusinessException("Account is locked or blocked", "ACCOUNT_LOCKED");
        }
    }

    private boolean isTokenBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
    }

    private void blacklistToken(String token, Date expiry) {
        long ttlSeconds = (expiry.getTime() - System.currentTimeMillis()) / 1000;
        if (ttlSeconds > 0) {
            redisTemplate.opsForValue().set(BLACKLIST_PREFIX + token, "revoked", ttlSeconds, TimeUnit.SECONDS);
        }
    }

    private String resolveTenantId(String userId) {
        String resolved = tryResolveTenantIdForUserId(userId);
        if (StringUtils.hasText(resolved)) {
            return resolved;
        }

        String upper = userId == null ? null : userId.toUpperCase(Locale.ROOT);
        if (StringUtils.hasText(upper) && !upper.equals(userId)) {
            resolved = tryResolveTenantIdForUserId(upper);
            if (StringUtils.hasText(resolved)) {
                return resolved;
            }
        }

        String lower = userId == null ? null : userId.toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(lower) && !lower.equals(userId)) {
            resolved = tryResolveTenantIdForUserId(lower);
            if (StringUtils.hasText(resolved)) {
                return resolved;
            }
        }

        throw new BusinessException("Unable to resolve tenant context", "TENANT_CONTEXT_UNAVAILABLE");
    }

    private String tryResolveTenantIdForUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }

        String url = mdmServiceBaseUrl + "/api/v1/mdm/users/" + userId;
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }

            Object data = response.getBody().get("data");
            if (!(data instanceof Map<?, ?> dataMap)) {
                return null;
            }

            Object tenantId = dataMap.get("tenantId");
            String tenantValue = tenantId == null ? null : String.valueOf(tenantId).trim();
            return StringUtils.hasText(tenantValue) ? tenantValue : null;
        } catch (RestClientException ex) {
            log.warn("Tenant resolution lookup failed for userId {}: {}", userId, ex.getMessage());
            return resolveTenantIdFromLocalProfile(userId);
        }
    }

    private String resolveTenantIdFromLocalProfile(String userId) {
        try {
            Query query = Query.query(Criteria.where("userId").is(userId));
            org.bson.Document profile = mongoTemplate.findOne(query, org.bson.Document.class, "mdm_user_profiles");
            if (profile == null) {
                return null;
            }

            Object tenantId = profile.get("tenantId");
            String tenantValue = tenantId == null ? null : String.valueOf(tenantId).trim();
            if (StringUtils.hasText(tenantValue)) {
                log.info("Resolved tenant context from local profile for userId={}", userId);
                return tenantValue;
            }
            return null;
        } catch (Exception ex) {
            log.warn("Local tenant resolution lookup failed for userId {}: {}", userId, ex.getMessage());
            return null;
        }
    }

    private String resolveTenantIdSilently(String userId) {
        try {
            return resolveTenantId(userId);
        } catch (BusinessException ex) {
            log.warn("Tenant resolution skipped for user {}: {}", userId, ex.getMessage());
            return null;
        }
    }

    private String[] deriveNameParts(User user) {
        String username = user == null ? null : user.getUsername();
        String userId = user == null ? null : user.getUserId();
        String source = StringUtils.hasText(username) ? username : userId;
        if (!StringUtils.hasText(source)) {
            return new String[] { "Unknown", "User" };
        }

        String[] tokens = source.trim().replace('-', ' ').replace('_', ' ').split("\\s+");
        List<String> normalizedTokens = new java.util.ArrayList<>();
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            StringBuilder normalized = new StringBuilder();
            normalized.append(Character.toUpperCase(token.charAt(0)));
            if (token.length() > 1) {
                normalized.append(token.substring(1).toLowerCase());
            }
            normalizedTokens.add(normalized.toString());
        }

        if (normalizedTokens.isEmpty()) {
            return new String[] { "Unknown", "User" };
        }

        String firstName = normalizedTokens.get(0);
        String lastName = normalizedTokens.size() > 1
                ? String.join(" ", normalizedTokens.subList(1, normalizedTokens.size()))
                : "";
        return new String[] { firstName, lastName };
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private void validateTenantLicenseForLogin(String tenantId, String userId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BusinessException("Tenant context missing for user", "TENANT_CONTEXT_UNAVAILABLE");
        }

        String url = licenseServiceBaseUrl + "/internal/v1/mdm/license/tenant/" + tenantId + "/validate";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of(), headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new BusinessException("License validation failed", "LICENSE_VALIDATION_FAILED");
            }

            Object data = response.getBody().get("data");
            boolean valid = data instanceof Boolean value && value;
            if (!valid) {
                auditEventPublisher.publish(userId, userId, "LOGIN", "AUTH_USER", userId,
                        "FAILURE", "License expired/inactive", Map.of("tenantId", tenantId));
                throw new BusinessException("License expired or inactive", "LICENSE_EXPIRED");
            }
        } catch (RestClientException ex) {
            throw new BusinessException("License validation failed: " + ex.getMessage(), "LICENSE_VALIDATION_FAILED");
        }
    }
}
