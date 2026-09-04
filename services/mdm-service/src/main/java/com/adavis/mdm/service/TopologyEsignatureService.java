package com.adavis.mdm.service;

import com.adavis.common.exception.BusinessException;
import com.adavis.common.exception.UnauthorizedException;
import com.adavis.security.PasswordEncoderConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopologyEsignatureService {

    private static final String CREDENTIALS_COLLECTION = "mdm_user_auth_credentials";
    private static final String USER_PROFILES_COLLECTION = "mdm_user_profiles";
    private static final int MAX_REMARKS_LENGTH = 500;

    private final MongoTemplate mongoTemplate;

    public void validateRemarks(String remarks) {
        if (!StringUtils.hasText(remarks)) {
            throw new BusinessException("Remarks are required for controlled action.", "REMARKS_REQUIRED");
        }
        String trimmed = remarks.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException("Remarks are required for controlled action.", "REMARKS_REQUIRED");
        }
        if (trimmed.length() > MAX_REMARKS_LENGTH) {
            throw new BusinessException("Remarks cannot exceed " + MAX_REMARKS_LENGTH + " characters.", "REMARKS_TOO_LONG");
        }
    }

    public boolean verifyEsignature(String userId, String rawPassword, String actionCode, String tenantId) {
        if (!StringUtils.hasText(rawPassword)) {
            throw new BusinessException("Electronic signature password is required.", "ESIGN_PASSWORD_REQUIRED");
        }
        if (!StringUtils.hasText(userId) || "SYSTEM".equalsIgnoreCase(userId.trim())) {
            throw new UnauthorizedException("Authenticated user context is required for electronic signature.", "AUTH_CONTEXT_REQUIRED");
        }

        String normalizedUserId = userId.trim();

        // 1. Verify user profile exists, is active, and is not blocked
        Query profileQuery = new Query(new Criteria().orOperator(
                Criteria.where("userId").regex("^" + Pattern.quote(normalizedUserId) + "$", "i"),
                Criteria.where("username").regex("^" + Pattern.quote(normalizedUserId) + "$", "i"),
                Criteria.where("email").regex("^" + Pattern.quote(normalizedUserId) + "$", "i")
        ));
        Document profile = mongoTemplate.findOne(profileQuery, Document.class, USER_PROFILES_COLLECTION);
        if (profile != null) {
            Boolean isActive = profile.getBoolean("isActive");
            if (Boolean.FALSE.equals(isActive)) {
                log.warn("E-signature rejected: User '{}' is deactivated", normalizedUserId);
                throw new UnauthorizedException("User account is inactive or deactivated.", "USER_INACTIVE");
            }
            Boolean isBlocked = profile.getBoolean("isBlocked");
            if (Boolean.TRUE.equals(isBlocked)) {
                log.warn("E-signature rejected: User '{}' is blocked", normalizedUserId);
                throw new UnauthorizedException("User account is blocked.", "USER_BLOCKED");
            }

            // Verify tenant boundary if user is not a super administrator
            String title = profile.getString("title");
            boolean isSuperAdmin = "SUPER_ADMIN".equalsIgnoreCase(normalizedUserId)
                    || (title != null && (title.equalsIgnoreCase("Super Admin") || title.equalsIgnoreCase("Platform Super Administrator")));
            if (!isSuperAdmin && StringUtils.hasText(tenantId)) {
                String userTenantId = profile.getString("tenantId");
                if (StringUtils.hasText(userTenantId) && !userTenantId.equalsIgnoreCase(tenantId.trim())) {
                    log.warn("E-signature rejected: User '{}' tenant '{}' does not match target tenant '{}'",
                            normalizedUserId, userTenantId, tenantId);
                    throw new UnauthorizedException("User does not have access to sign for tenant: " + tenantId, "UNAUTHORIZED_TENANT_ACCESS");
                }
            }
        }

        // 2. Authoritative credential lookup strictly in mdm_user_auth_credentials
        Query credQuery = new Query(new Criteria().orOperator(
                Criteria.where("userId").regex("^" + Pattern.quote(normalizedUserId) + "$", "i"),
                Criteria.where("username").regex("^" + Pattern.quote(normalizedUserId) + "$", "i"),
                Criteria.where("email").regex("^" + Pattern.quote(normalizedUserId) + "$", "i")
        ));

        Document credential = mongoTemplate.findOne(credQuery, Document.class, CREDENTIALS_COLLECTION);
        if (credential == null) {
            log.warn("E-signature verification failed: No credential found for user '{}'", normalizedUserId);
            throw new UnauthorizedException("E-signature validation failed: Invalid credentials.", "INVALID_ESIGN_CREDENTIALS");
        }

        String passwordHash = credential.getString("passwordHash");
        if (!StringUtils.hasText(passwordHash)) {
            passwordHash = credential.getString("password");
        }

        if (!StringUtils.hasText(passwordHash) || !PasswordEncoderConfig.matches(rawPassword.trim(), passwordHash)) {
            log.warn("E-signature verification failed: Invalid password supplied for user '{}' on action '{}'", normalizedUserId, actionCode);
            throw new UnauthorizedException("E-signature validation failed: Invalid password supplied.", "INVALID_ESIGN_CREDENTIALS");
        }

        log.info("E-signature successfully verified for user '{}' on action '{}' (tenant: '{}')", normalizedUserId, actionCode, tenantId);
        return true;
    }
}
