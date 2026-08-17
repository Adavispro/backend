package com.adavis.license.service;

import com.adavis.common.exception.BusinessException;
import com.adavis.common.exception.ResourceNotFoundException;
import com.adavis.dto.license.request.ApplyLicenseRequest;
import com.adavis.dto.license.request.ValidateLicenseRequest;
import com.adavis.dto.license.response.LicenseResponse;
import com.adavis.dto.license.response.ModuleResponse;
import com.adavis.license.model.entity.License;
import com.adavis.license.model.entity.LicenseHistory;
import com.adavis.license.repository.LicenseHistoryRepository;
import com.adavis.license.repository.LicenseRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class LicenseService {

    private final LicenseRepository licenseRepository;
    private final LicenseHistoryRepository licenseHistoryRepository;
    private final ResourceLoader resourceLoader;

    @Value("${license.jwt.public-key-path:../../license_generation_module/keys/public_key.pem}")
    private String publicKeyPath;

    @Value("${license.jwt.private-key-path:../../license_generation_module/keys/private_key.pem}")
    private String privateKeyPath;

    @Value("${license.jwt.issuer:ADAVIS}")
    private String expectedIssuer;

    @Value("${audit.enabled:true}")
    private boolean auditEnabled;

    @Value("${audit.service-url:http://audit-service:8084}")
    private String auditServiceUrl;

    @Value("${audit.endpoint:/internal/v1/audit/logs}")
    private String auditEndpoint;

    private final RestTemplate restTemplate = new RestTemplate();

    @Transactional
    @CacheEvict(value = {"license", "licenseModules"}, allEntries = true)
    public LicenseResponse applyLicense(ApplyLicenseRequest request) {
        String actionType = normalizeActionType(request.getActionType());
        String actor = (request.getPerformedBy() == null || request.getPerformedBy().isBlank())
                ? "SYSTEM"
                : request.getPerformedBy();
        String reason = firstNonBlank(request.getReason(), "License " + actionType.toLowerCase());

        return switch (actionType) {
            case "ACTIVATE" -> activateLicense(request.getEncryptedLicenseToken(), actor);
            case "UPGRADE", "RENEW" -> upgradeLicense(request.getEncryptedLicenseToken(), actor, reason);
            case "SUSPEND", "REACTIVATE" -> {
                License existing = findLicenseByEncryptedToken(request.getEncryptedLicenseToken());
                String nextStatus = "SUSPEND".equals(actionType) ? "SUSPENDED" : "ACTIVE";
                updateLicenseStatus(existing.getId(), nextStatus, reason);
                yield mapToResponse(refreshLicense(existing.getId()));
            }
            default -> throw new BusinessException("Unsupported actionType: " + actionType);
        };
    }

    @Transactional
    private LicenseResponse activateLicense(String encryptedLicenseToken, String actor) {
        log.info("Activating license token");

        Claims claims = parseAndValidateLicenseToken(encryptedLicenseToken);
        String tenantId = claims.get("tenantId", String.class);
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException("tenantId is required in license token");
        }
        String resolvedLicenseKey = firstNonBlank(stringClaim(claims.get("licenseKey")), encryptedLicenseToken);

        // Idempotent activate: one license per tenant.
        var existingLicense = licenseRepository.findByTenantIdAndIsDeletedFalse(tenantId);
        if (existingLicense.isPresent()) {
            return mapToResponse(existingLicense.get());
        }

        Map<String, Object> planClaims = safeMap(claims.get("plan"));
        String planId = firstNonBlank(stringClaim(planClaims.get("planId")), "UNKNOWN_PLAN");
        String planName = firstNonBlank(stringClaim(planClaims.get("planName")), "Unknown");
        String planType = firstNonBlank(stringClaim(planClaims.get("planType")), "PAID");

        List<String> modules = toStringList(claims.get("modules"));
        if (modules == null || modules.isEmpty()) {
            throw new BusinessException("modules is required in license token");
        }

        Integer maxUsers = intClaim(claims.get("maxUsers"));
        if (maxUsers == null) {
            throw new BusinessException("maxUsers is required in license token");
        }

        Instant startDate = resolveInstantClaim(claims.get("startDate"), claims.getIssuedAt(), Instant.now());
        Instant expiryDate = resolveInstantClaim(claims.get("expiryDate"), claims.getExpiration(), null);
        if (expiryDate == null) {
            throw new BusinessException("expiryDate is required in license token");
        }

        Integer currentUsers = intClaim(claims.get("currentUsers"));
        if (currentUsers == null) {
            currentUsers = 0;
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tenantId", tenantId);
        metadata.put("version", intClaim(claims.get("version")));
        metadata.put("tokenIssuer", claims.getIssuer());
        metadata.put("tokenIssuedAt", claims.getIssuedAt());
        metadata.put("tokenExpiresAt", claims.getExpiration());
        metadata.put("jwtId", claims.getId());

        // Create new license
        License license = License.builder()
            .tenantId(tenantId)
                .licenseKey(resolvedLicenseKey)
                .plan(License.Plan.builder()
                        .planId(planId)
                        .planName(planName)
                        .planType(planType)
                    .features(safeMap(planClaims.get("features")))
                        .build())
                .modules(modules)
                .maxUsers(maxUsers)
                .currentUsers(currentUsers)
                .status("ACTIVE")
                .startDate(startDate)
                .expiryDate(expiryDate)
                .metadata(metadata)
                .upgradeCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy(actor)
                .updatedBy(actor)
                .isDeleted(false)
                .build();

        License savedLicense;
        try {
            savedLicense = licenseRepository.save(license);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("License already activated for this tenant");
        }

        // Log history
        createHistory(savedLicense, "ACTIVATED", null, savedLicense.getStatus(),
                null, savedLicense.getMaxUsers(), null, savedLicense.getModules(),
            null, savedLicense.getExpiryDate(), "License activated", actor);

        publishAuditEvent(savedLicense, "LICENSE_ACTIVATED", actor, "SUCCESS", null,
            null, snapshot(savedLicense));

        return mapToResponse(savedLicense);
    }

    public boolean validateLicenseByTenantId(String tenantId, ValidateLicenseRequest request) {
        log.info("Validating license for tenant: {}", tenantId);
        License license = getActiveLicenseEntityByTenantId(tenantId);
        return validateLicenseEntity(license, request, tenantId);
    }

    @Transactional
    private LicenseResponse upgradeLicense(String encryptedLicenseToken, String actor, String reason) {
        log.info("Upgrading license for actor: {}", actor);

        Claims tokenClaims = parseAndValidateLicenseToken(encryptedLicenseToken);
        String tenantId = tokenClaims.get("tenantId", String.class);
        License license = getActiveLicenseEntityByTenantId(tenantId);

        return applyTokenUpgrade(license, tokenClaims, actor, reason);
    }

    private LicenseResponse applyTokenUpgrade(License license, Claims tokenClaims, String actor, String reason) {

        if (!"ACTIVE".equals(license.getStatus())) {
            throw new BusinessException("Cannot upgrade non-active license. Current status: " + license.getStatus());
        }

        // Store old values
        String oldPlanId = license.getPlan() != null ? license.getPlan().getPlanId() : null;
        List<String> oldModules = new ArrayList<>(license.getModules());
        Integer oldMaxUsers = license.getMaxUsers();
        Instant oldExpiry = license.getExpiryDate();

        Map<String, Object> planClaims = tokenClaims != null ? safeMap(tokenClaims.get("plan")) : Collections.emptyMap();
        String upgradedLicenseKey = tokenClaims != null ? stringClaim(tokenClaims.get("licenseKey")) : null;
        String effectivePlanId = stringClaim(planClaims.get("planId"));
        String effectivePlanName = stringClaim(planClaims.get("planName"));
        String effectivePlanType = stringClaim(planClaims.get("planType"));
        Map<String, Object> tokenFeatures = safeMap(planClaims.get("features"));
        Map<String, Object> effectiveFeatures = tokenFeatures.isEmpty() ? null : tokenFeatures;

        List<String> effectiveModules = tokenClaims != null ? toStringList(tokenClaims.get("modules")) : Collections.emptyList();
        Integer effectiveMaxUsers = tokenClaims != null ? intClaim(tokenClaims.get("maxUsers")) : null;
        Instant effectiveExpiryDate = tokenClaims != null
                ? resolveInstantClaim(tokenClaims.get("expiryDate"), tokenClaims.getExpiration(), null)
                : null;

        if ((effectiveModules == null || effectiveModules.isEmpty())
                && effectiveMaxUsers == null
                && effectiveExpiryDate == null
                && effectivePlanId == null
                && effectivePlanName == null
                && effectivePlanType == null
                && effectiveFeatures == null) {
            throw new BusinessException("No upgradable claims found in encrypted license token");
        }

        // Apply upgrades
        if (upgradedLicenseKey != null && !upgradedLicenseKey.isBlank()) {
            license.setLicenseKey(upgradedLicenseKey);
        }

        if (effectivePlanId != null || effectivePlanName != null || effectivePlanType != null || effectiveFeatures != null) {
            if (license.getPlan() == null) {
                license.setPlan(License.Plan.builder().build());
            }
            if (effectivePlanId != null) {
                license.getPlan().setPlanId(effectivePlanId);
            }
            if (effectivePlanName != null) {
                license.getPlan().setPlanName(effectivePlanName);
            }
            if (effectivePlanType != null) {
                license.getPlan().setPlanType(effectivePlanType);
            }
            if (effectiveFeatures != null) {
                license.getPlan().setFeatures(effectiveFeatures);
            }
        }

        if (effectiveModules != null && !effectiveModules.isEmpty()) {
            license.setModules(effectiveModules);
        }

        if (effectiveMaxUsers != null) {
            license.setMaxUsers(effectiveMaxUsers);
        }

        if (effectiveExpiryDate != null) {
            license.setExpiryDate(effectiveExpiryDate);
        }

        if (tokenClaims != null) {
            Map<String, Object> mergedMetadata = license.getMetadata() == null
                    ? new HashMap<>()
                    : new HashMap<>(license.getMetadata());
            mergedMetadata.put("tokenIssuer", tokenClaims.getIssuer());
            mergedMetadata.put("tokenIssuedAt", tokenClaims.getIssuedAt());
            mergedMetadata.put("tokenExpiresAt", tokenClaims.getExpiration());
            mergedMetadata.put("jwtId", tokenClaims.getId());
            license.setMetadata(mergedMetadata);
        }

        // Track upgrade
        if (license.getUpgradeCount() == null) {
            license.setUpgradeCount(0);
        }
        license.setUpgradeCount(license.getUpgradeCount() + 1);
        license.setUpdatedAt(Instant.now());
        license.setUpdatedBy(actor);

        License upgradedLicense = licenseRepository.save(license);

        // Log history
        createHistory(upgradedLicense, "UPGRADED", null, "ACTIVE",
                oldMaxUsers, license.getMaxUsers(), 
                oldModules, license.getModules(),
                oldExpiry, license.getExpiryDate(), 
            reason, actor);

        Map<String, Object> beforeSnapshot = new HashMap<>();
        beforeSnapshot.put("planId", oldPlanId);
        beforeSnapshot.put("maxUsers", oldMaxUsers);
        beforeSnapshot.put("modules", oldModules);
        beforeSnapshot.put("expiryDate", oldExpiry);
        publishAuditEvent(upgradedLicense, "LICENSE_UPGRADED", actor, "SUCCESS", reason,
            beforeSnapshot, snapshot(upgradedLicense));

        String tenantId = resolveTenantId(license);
        log.info("License upgraded successfully for tenant {} (Upgrade #{})", tenantId, license.getUpgradeCount());

        return mapToResponse(upgradedLicense);
    }

    @Transactional
    @CacheEvict(value = {"license", "licenseModules"}, allEntries = true)
    public LicenseResponse updateUserCountByTenantId(String tenantId, Integer newCount) {
        License license = getActiveLicenseEntityByTenantId(tenantId);
        return updateUserCountForLicense(license, newCount);
    }

    private LicenseResponse updateUserCountForLicense(License license, Integer newCount) {
        if (newCount != null && newCount < 0) {
            throw new BusinessException("User count cannot be negative");
        }

        if (license.getMaxUsers() != null && newCount > license.getMaxUsers()) {
            throw new BusinessException("User count exceeds license limit: " + license.getMaxUsers());
        }

        license.setCurrentUsers(newCount);
        license.setUpdatedAt(Instant.now());
        License updatedLicense = licenseRepository.save(license);

        // Log history
        createHistory(updatedLicense, "USER_COUNT_UPDATED", null, null,
                null, null, null, null,
                null, null, "User count updated to " + newCount, "SYSTEM");

        Map<String, Object> after = new HashMap<>();
        after.put("currentUsers", updatedLicense.getCurrentUsers());
        after.put("maxUsers", updatedLicense.getMaxUsers());
        publishAuditEvent(updatedLicense, "LICENSE_USER_COUNT_UPDATED", "SYSTEM", "SUCCESS",
            "User count updated", null, after);

        return mapToResponse(updatedLicense);
    }

    @Transactional
    public void updateLicenseStatus(String licenseId, String status, String reason) {
        License license = licenseRepository.findById(licenseId)
                .orElseThrow(() -> new ResourceNotFoundException("License not found: " + licenseId));

        String oldStatus = license.getStatus();
        license.setStatus(status);
        license.setUpdatedAt(Instant.now());
        licenseRepository.save(license);

        // Log history
        createHistory(license, "STATUS_CHANGED", oldStatus, status,
                null, null, null, null,
                null, null, reason, "SYSTEM");

        Map<String, Object> before = new HashMap<>();
        before.put("status", oldStatus);
        Map<String, Object> after = new HashMap<>();
        after.put("status", status);
        publishAuditEvent(license, "LICENSE_STATUS_CHANGED", "SYSTEM", "SUCCESS", reason, before, after);

        log.info("License {} status changed from {} to {}", licenseId, oldStatus, status);
    }

    @Scheduled(cron = "0 0 * * * *") // Run every hour
    public void checkExpiredLicenses() {
        log.info("Checking for expired licenses");
        List<License> expiredLicenses = licenseRepository.findByExpiryDateBeforeAndStatusNotAndIsDeletedFalse(
                Instant.now(), "EXPIRED");

        for (License license : expiredLicenses) {
            updateLicenseStatus(license.getId(), "EXPIRED", "License expired automatically");
        }
    }

    public List<LicenseResponse> getAllLicenses() {
        return licenseRepository.findByStatusAndIsDeletedFalse("ACTIVE")
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public LicenseResponse getActiveLicenseByTenantId(String tenantId) {
        return mapToResponse(getActiveLicenseEntityByTenantId(tenantId));
    }

    public ModuleResponse getModulesByTenantId(String tenantId) {
        License license = getActiveLicenseEntityByTenantId(tenantId);
        return ModuleResponse.builder()
            .licenseKey(license.getLicenseKey())
            .modules(license.getModules())
            .status(license.getStatus())
            .build();
    }

    public Map<String, Object> getInternalLicenseKeyByTenantId(String tenantId) {
        License license = getActiveLicenseEntityByTenantId(tenantId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("licenseId", license.getId());
        payload.put("tenantId", resolveTenantId(license));
        payload.put("status", license.getStatus());
        payload.put("licenseKey", license.getLicenseKey());
        return payload;
    }

    @Transactional
    @CacheEvict(value = {"license", "licenseModules"}, allEntries = true)
    public LicenseResponse upgradeLicenseById(String licenseId,
                                              String encryptedLicenseToken,
                                              List<String> modules,
                                              Integer maxUsers,
                                              String reason,
                                              String upgradedBy) {
        License license = refreshLicense(licenseId);
        if (Boolean.TRUE.equals(license.getIsDeleted())) {
            throw new ResourceNotFoundException("License not found: " + licenseId);
        }

        String actor = firstNonBlank(upgradedBy, "SYSTEM");
        String effectiveReason = firstNonBlank(reason, "License upgraded");

        if (encryptedLicenseToken != null && !encryptedLicenseToken.isBlank()) {
            Claims tokenClaims = parseAndValidateLicenseToken(encryptedLicenseToken);
            String tokenTenantId = tokenClaims.get("tenantId", String.class);
            String existingTenantId = resolveTenantId(license);

            if (tokenTenantId != null && !tokenTenantId.isBlank()
                    && existingTenantId != null && !existingTenantId.isBlank()
                    && !tokenTenantId.equalsIgnoreCase(existingTenantId)) {
                throw new BusinessException("Encrypted license token tenant does not match selected license");
            }

            return applyTokenUpgrade(license, tokenClaims, actor, effectiveReason);
        }

        List<String> oldModules = license.getModules() == null ? List.of() : new ArrayList<>(license.getModules());
        Integer oldMaxUsers = license.getMaxUsers();
        Instant oldExpiry = license.getExpiryDate();

        if (modules != null && !modules.isEmpty()) {
            license.setModules(modules);
        }
        if (maxUsers != null) {
            if (license.getCurrentUsers() != null && maxUsers < license.getCurrentUsers()) {
                throw new BusinessException("maxUsers cannot be less than currentUsers: " + license.getCurrentUsers());
            }
            license.setMaxUsers(maxUsers);
        }

        if (license.getUpgradeCount() == null) {
            license.setUpgradeCount(0);
        }
        license.setUpgradeCount(license.getUpgradeCount() + 1);

        license.setUpdatedAt(Instant.now());
        license.setUpdatedBy(actor);
        License saved = licenseRepository.save(license);

        createHistory(saved, "UPGRADED", null, saved.getStatus(), oldMaxUsers, saved.getMaxUsers(),
                oldModules, saved.getModules(), oldExpiry, saved.getExpiryDate(), effectiveReason, actor);

        Map<String, Object> beforeSnapshot = new HashMap<>();
        beforeSnapshot.put("maxUsers", oldMaxUsers);
        beforeSnapshot.put("modules", oldModules);
        beforeSnapshot.put("expiryDate", oldExpiry);
        publishAuditEvent(saved, "LICENSE_UPGRADED", actor, "SUCCESS", effectiveReason, beforeSnapshot, snapshot(saved));

        return mapToResponse(saved);
    }

    @Transactional
    @CacheEvict(value = {"license", "licenseModules"}, allEntries = true)
    public LicenseResponse upgradeLicenseByTenantId(String tenantId,
                                                    String encryptedLicenseToken,
                                                    List<String> modules,
                                                    Integer maxUsers,
                                                    String reason,
                                                    String upgradedBy) {
        License license = getActiveLicenseEntityByTenantId(tenantId);
        return upgradeLicenseById(
                license.getId(),
                encryptedLicenseToken,
                modules,
                maxUsers,
                reason,
                upgradedBy
        );
    }

    public List<LicenseHistory> getLicenseHistoryByTenantId(String tenantId) {
        return licenseHistoryRepository.findByTenantIdOrderByPerformedAtDesc(tenantId);
    }

    private void createHistory(License license, String action, String beforeStatus, String afterStatus,
                               Integer beforeMaxUsers, Integer afterMaxUsers,
                               List<String> beforeModules, List<String> afterModules,
                               Instant beforeExpiry, Instant afterExpiry,
                               String reason, String performedBy) {

        LicenseHistory history = LicenseHistory.builder()
            .tenantId(resolveTenantId(license))
                .licenseId(license.getId())
                .action(action)
                .beforeStatus(beforeStatus)
                .afterStatus(afterStatus)
                .beforeMaxUsers(beforeMaxUsers)
                .afterMaxUsers(afterMaxUsers)
                .beforeModules(beforeModules)
                .afterModules(afterModules)
                .beforeExpiry(beforeExpiry)
                .afterExpiry(afterExpiry)
                .reason(reason)
                .performedBy(performedBy)
                .performedAt(Instant.now())
                .build();

        licenseHistoryRepository.save(history);
    }

    private LicenseResponse mapToResponse(License license) {
        return LicenseResponse.builder()
                .id(license.getId())
                .licenseKey(license.getLicenseKey())
                .planId(license.getPlan() != null ? license.getPlan().getPlanId() : null)
                .planName(license.getPlan() != null ? license.getPlan().getPlanName() : null)
                .planType(license.getPlan() != null ? license.getPlan().getPlanType() : null)
                .modules(license.getModules())
                .maxUsers(license.getMaxUsers())
                .currentUsers(license.getCurrentUsers())
                .status(license.getStatus())
                .startDate(license.getStartDate())
                .expiryDate(license.getExpiryDate())
                .metadata(license.getMetadata())
                .createdAt(license.getCreatedAt())
                .updatedAt(license.getUpdatedAt())
                .build();
    }

    private Claims parseAndValidateLicenseToken(String token) {
        try {
            RSAPublicKey publicKey = loadPublicKey();
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(expectedIssuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IOException | GeneralSecurityException ex) {
            throw new BusinessException("Invalid license token: " + ex.getMessage());
        }
    }

    private License findLicenseByEncryptedToken(String encryptedLicenseToken) {
        Claims tokenClaims = parseAndValidateLicenseToken(encryptedLicenseToken);
        String tenantId = tokenClaims.get("tenantId", String.class);
        return getActiveLicenseEntityByTenantId(tenantId);
    }

    private License refreshLicense(String id) {
        return licenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("License not found: " + id));
    }

    private License getActiveLicenseEntityByTenantId(String tenantId) {
        return licenseRepository.findByTenantIdAndStatusAndIsDeletedFalse(tenantId, "ACTIVE")
                .orElseThrow(() -> new ResourceNotFoundException("Active license not found for tenant: " + tenantId));
    }

    private boolean validateLicenseEntity(License license, ValidateLicenseRequest request, String tenantId) {
        if (!"ACTIVE".equals(license.getStatus())) {
            log.warn("License not active for tenant {}: {}", tenantId, license.getStatus());
            publishValidationAudit(tenantId, request, "FAILED", "License not active: " + license.getStatus());
            return false;
        }

        if (license.getExpiryDate() != null && license.getExpiryDate().isBefore(Instant.now())) {
            log.warn("License expired for tenant {} at: {}", tenantId, license.getExpiryDate());
            updateLicenseStatus(license.getId(), "EXPIRED", "License expired");
            publishValidationAudit(tenantId, request, "FAILED", "License expired");
            return false;
        }

        if (request.getCurrentUserCount() != null &&
                license.getMaxUsers() != null &&
                request.getCurrentUserCount() > license.getMaxUsers()) {
            log.warn("User limit exceeded for tenant {}: {} > {}", tenantId, request.getCurrentUserCount(), license.getMaxUsers());
            publishValidationAudit(tenantId, request, "FAILED", "User limit exceeded");
            return false;
        }

        if (request.getModuleId() != null) {
            Set<String> licensedModules = license.getModules() == null
                ? Collections.emptySet()
                : license.getModules().stream()
                    .filter(Objects::nonNull)
                    .flatMap(module -> Stream.of(module, normalizeModuleCode(module)))
                    .filter(s -> s != null && !s.isBlank())
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());

            String requestedModule = request.getModuleId().toUpperCase();
            String normalizedRequestedModule = normalizeModuleCode(requestedModule);

            if (!licensedModules.contains(requestedModule)
                    && (normalizedRequestedModule == null || !licensedModules.contains(normalizedRequestedModule))) {
                log.warn("Module not licensed for tenant {}: {}", tenantId, request.getModuleId());
                publishValidationAudit(tenantId, request, "FAILED", "Module not licensed: " + request.getModuleId());
                return false;
            }
        }

        publishValidationAudit(tenantId, request, "SUCCESS", null);
        return true;
    }

    @Transactional
    @CacheEvict(value = {"license", "licenseModules"}, allEntries = true)
    public LicenseResponse renewTenantLicense(String tenantId, String encryptedLicenseToken,
                                              Integer validityYears, String planId,
                                              List<String> modules, Integer maxUsers,
                                              String reason, String actor) {
        log.info("Renewing license for tenant: {} by actor: {}", tenantId, actor);
        License license = getActiveLicenseEntityByTenantId(tenantId);

        if (encryptedLicenseToken != null && !encryptedLicenseToken.isBlank()) {
            Claims tokenClaims = parseAndValidateLicenseToken(encryptedLicenseToken);
            String tokenTenantId = tokenClaims.get("tenantId", String.class);
            if (tokenTenantId != null && !tokenTenantId.isBlank() && !tenantId.equalsIgnoreCase(tokenTenantId)) {
                throw new BusinessException("Token tenant mismatch: token is for " + tokenTenantId + " but renewing " + tenantId);
            }
            return applyTokenUpgrade(license, tokenClaims, actor, reason);
        }

        int years = (validityYears != null && validityYears > 0) ? validityYears : 1;
        Instant now = Instant.now();
        Instant expiry = (license.getExpiryDate() != null && license.getExpiryDate().isAfter(now))
                ? license.getExpiryDate().plus(years * 365L, ChronoUnit.DAYS)
                : now.plus(years * 365L, ChronoUnit.DAYS);

        String effectivePlanId = (planId != null && !planId.isBlank())
                ? planId
                : (license.getPlan() != null ? license.getPlan().getPlanId() : "PLAN_ENTERPRISE");
        String effectivePlanName = (license.getPlan() != null && license.getPlan().getPlanName() != null)
                ? license.getPlan().getPlanName()
                : "Enterprise";
        String effectivePlanType = (license.getPlan() != null && license.getPlan().getPlanType() != null)
                ? license.getPlan().getPlanType()
                : "PAID";

        List<String> effectiveModules = (modules != null && !modules.isEmpty())
                ? modules
                : (license.getModules() != null && !license.getModules().isEmpty() ? license.getModules() : List.of("MOD-MDM", "MOD-IIOT"));

        Integer effectiveMaxUsers = (maxUsers != null && maxUsers > 0)
                ? maxUsers
                : (license.getMaxUsers() != null ? license.getMaxUsers() : 500);

        String generatedToken;
        try {
            generatedToken = generateSignedToken(tenantId, effectivePlanId, effectivePlanName, effectivePlanType,
                    effectiveModules, effectiveMaxUsers, now, expiry);
        } catch (Exception ex) {
            log.error("Failed to generate signed license token: {}", ex.getMessage(), ex);
            throw new BusinessException("Failed to generate signed license token: " + ex.getMessage());
        }

        String beforeStatus = license.getStatus();
        Integer beforeMaxUsers = license.getMaxUsers();
        List<String> beforeModules = license.getModules() != null ? new ArrayList<>(license.getModules()) : Collections.emptyList();
        Instant beforeExpiry = license.getExpiryDate();

        Map<String, Object> metadata = license.getMetadata() != null ? new HashMap<>(license.getMetadata()) : new HashMap<>();
        metadata.put("encryptedLicenseToken", generatedToken);
        metadata.put("tenantId", tenantId);
        metadata.put("tokenIssuer", expectedIssuer);
        metadata.put("renewedAt", now.toString());
        license.setMetadata(metadata);
        license.setExpiryDate(expiry);
        license.setMaxUsers(effectiveMaxUsers);
        license.setModules(effectiveModules);
        license.setStatus("ACTIVE");
        license.setUpgradeCount(license.getUpgradeCount() != null ? license.getUpgradeCount() + 1 : 1);
        license.setUpdatedAt(now);
        license.setUpdatedBy(actor);

        License savedLicense = licenseRepository.save(license);

        createHistory(savedLicense, "RENEWED", beforeStatus, savedLicense.getStatus(),
                beforeMaxUsers, savedLicense.getMaxUsers(), beforeModules, savedLicense.getModules(),
                beforeExpiry, savedLicense.getExpiryDate(), firstNonBlank(reason, "Annual license renewal"), actor);

        publishAuditEvent(savedLicense, "LICENSE_RENEWED", actor, "SUCCESS", null,
                null, snapshot(savedLicense));

        return mapToResponse(savedLicense);
    }

    private String generateSignedToken(String tenantId, String planId, String planName, String planType,
                                       List<String> modules, Integer maxUsers, Instant startDate, Instant expiryDate)
            throws IOException, GeneralSecurityException {
        RSAPrivateKey privateKey = loadPrivateKey();
        Instant now = Instant.now();

        Map<String, Object> planMap = new HashMap<>();
        planMap.put("planId", planId);
        planMap.put("planName", planName);
        planMap.put("planType", planType);

        Map<String, Object> claims = new HashMap<>();
        claims.put("tenantId", tenantId);
        claims.put("licenceKey", "LIC-" + tenantId);
        claims.put("plan", planMap);
        claims.put("modules", modules);
        claims.put("maxUsers", maxUsers);
        claims.put("startDate", startDate.toString());
        claims.put("expiryDate", expiryDate.toString());
        claims.put("version", 1);

        return Jwts.builder()
                .issuer(expectedIssuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiryDate))
                .claims(claims)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    private RSAPrivateKey loadPrivateKey() throws IOException, GeneralSecurityException {
        String pem = null;

        List<Path> candidates = List.of(
                Path.of(privateKeyPath),
                Path.of("license_generation_module/keys/private_key.pem"),
                Path.of("license_generation_module/keys_txt/private_key.txt"),
                Path.of("ADAVIS_PLATFORM_BACKEND/license_generation_module/keys/private_key.pem"),
                Path.of("ADAVIS_PLATFORM_BACKEND/license_generation_module/keys_txt/private_key.txt"),
                Path.of("../license_generation_module/keys/private_key.pem"),
                Path.of("../license_generation_module/keys_txt/private_key.txt"),
                Path.of("../../license_generation_module/keys/private_key.pem"),
                Path.of("../../license_generation_module/keys_txt/private_key.txt")
        );

        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.exists(normalized)) {
                pem = Files.readString(normalized, StandardCharsets.UTF_8);
                break;
            }
        }

        if (pem == null) {
            Resource resource = null;
            try {
                if (privateKeyPath.startsWith("classpath:") || privateKeyPath.startsWith("file:")) {
                    resource = resourceLoader.getResource(privateKeyPath);
                }
            } catch (IllegalArgumentException ex) {
                log.debug("Ignoring invalid resource path for private key: {}", privateKeyPath);
            }

            if (resource == null || !resource.exists()) {
                resource = resourceLoader.getResource("classpath:keys/private_key.pem");
            }
            if (resource == null || !resource.exists()) {
                resource = resourceLoader.getResource("file:license_generation_module/keys/private_key.pem");
            }
            if (resource == null || !resource.exists()) {
                resource = resourceLoader.getResource("file:ADAVIS_PLATFORM_BACKEND/license_generation_module/keys/private_key.pem");
            }
            if (resource == null || !resource.exists()) {
                throw new IOException("Private key file not found. Checked path: " + privateKeyPath);
            }
            pem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }

        String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) keyFactory.generatePrivate(spec);
    }

    private RSAPublicKey loadPublicKey() throws IOException, GeneralSecurityException {
        String pem = null;

        List<Path> candidates = List.of(
                Path.of(publicKeyPath),
                Path.of("license_generation_module/keys/public_key.pem"),
                Path.of("license_generation_module/keys_txt/public_key.txt"),
                Path.of("ADAVIS_PLATFORM_BACKEND/license_generation_module/keys/public_key.pem"),
                Path.of("ADAVIS_PLATFORM_BACKEND/license_generation_module/keys_txt/public_key.txt"),
                Path.of("../license_generation_module/keys/public_key.pem"),
                Path.of("../license_generation_module/keys_txt/public_key.txt"),
                Path.of("../../license_generation_module/keys/public_key.pem"),
                Path.of("../../license_generation_module/keys_txt/public_key.txt")
        );

        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.exists(normalized)) {
                pem = Files.readString(normalized, StandardCharsets.UTF_8);
                break;
            }
        }

        if (pem == null) {
            Resource resource = null;
            try {
                if (publicKeyPath.startsWith("classpath:") || publicKeyPath.startsWith("file:")) {
                    resource = resourceLoader.getResource(publicKeyPath);
                }
            } catch (IllegalArgumentException ex) {
                log.debug("Ignoring invalid resource path for public key: {}", publicKeyPath);
            }

            if (resource == null || !resource.exists()) {
                resource = resourceLoader.getResource("classpath:keys/public_key.pem");
            }
            if (resource == null || !resource.exists()) {
                resource = resourceLoader.getResource("file:license_generation_module/keys/public_key.pem");
            }
            if (resource == null || !resource.exists()) {
                resource = resourceLoader.getResource("file:ADAVIS_PLATFORM_BACKEND/license_generation_module/keys/public_key.pem");
            }
            if (resource == null || !resource.exists()) {
                throw new IOException("Public key file not found. Checked path: " + publicKeyPath);
            }
            pem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }

        String normalized = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(spec);
    }

    private String normalizeModuleCode(String module) {
        if (module == null || module.isBlank()) {
            return null;
        }
        String trimmed = module.trim().toUpperCase().replace('_', '-');
        if (trimmed.startsWith("MOD-")) {
            return trimmed.substring(4);
        }
        return "MOD-" + trimmed;
    }

    private Map<String, Object> safeMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new HashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return Collections.emptyMap();
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private String stringClaim(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer intClaim(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer intValue) {
            return intValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String normalizeActionType(String actionType) {
        if (actionType == null || actionType.isBlank()) {
            throw new BusinessException("actionType is required");
        }
        return actionType.trim().toUpperCase();
    }

    private Instant resolveInstantClaim(Object rawClaim, java.util.Date fallbackDate, Instant requestFallback) {
        if (rawClaim instanceof Number epochSeconds) {
            return Instant.ofEpochSecond(epochSeconds.longValue());
        }
        if (rawClaim instanceof String dateString && !dateString.isBlank()) {
            return LocalDate.parse(dateString).atStartOfDay().toInstant(ZoneOffset.UTC);
        }
        if (rawClaim instanceof java.util.Date date) {
            return date.toInstant();
        }
        if (fallbackDate != null) {
            return fallbackDate.toInstant();
        }
        return requestFallback;
    }

    private Map<String, Object> snapshot(License license) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("licenseKey", license.getLicenseKey());
        snapshot.put("status", license.getStatus());
        snapshot.put("planId", license.getPlan() != null ? license.getPlan().getPlanId() : null);
        snapshot.put("maxUsers", license.getMaxUsers());
        snapshot.put("currentUsers", license.getCurrentUsers());
        snapshot.put("modules", license.getModules());
        snapshot.put("expiryDate", license.getExpiryDate());
        return snapshot;
    }

    private void publishValidationAudit(String tenantId, ValidateLicenseRequest request, String status, String reason) {
        Map<String, Object> after = new HashMap<>();
        after.put("tenantId", tenantId);
        after.put("moduleId", request.getModuleId());
        after.put("currentUserCount", request.getCurrentUserCount());
        publishAuditEvent(null, "LICENSE_VALIDATED", "SYSTEM", status, reason, null, after);
    }

    private void publishAuditEvent(License license,
                                   String action,
                                   String performedBy,
                                   String status,
                                   String failureReason,
                                   Map<String, Object> before,
                                   Map<String, Object> after) {
        if (!auditEnabled) {
            return;
        }

        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("userId", performedBy);
            event.put("username", performedBy);
            event.put("action", action);
            event.put("entity", "LICENSE");
            event.put("entityId", license != null ? license.getId() : null);
            event.put("before", before);
            event.put("after", after);
            event.put("tenantId", resolveTenantId(license));
            event.put("status", status);
            event.put("failureReason", failureReason);
            event.put("timestamp", Instant.now());

            RestClientException lastException = null;
            for (String url : auditEndpointCandidates()) {
                try {
                    restTemplate.postForEntity(url, event, Void.class);
                    return;
                } catch (RestClientException ex) {
                    lastException = ex;
                    log.debug("Audit publish attempt failed for action {} at {}: {}", action, url, ex.getMessage());
                }
            }

            if (lastException != null) {
                log.warn("Audit publish failed for action {}: {}", action, lastException.getMessage());
            }
        } catch (RuntimeException ex) {
            log.warn("Audit publish failed for action {}: {}", action, ex.getMessage());
        }
    }

    private List<String> auditEndpointCandidates() {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        urls.add(normalizeAuditUrl());
        urls.add(normalizeAuditUrl("/internal/v1/audit/logs"));
        return new ArrayList<>(urls);
    }

    private String resolveTenantId(License license) {
        if (license == null) {
            return null;
        }

        if (license.getTenantId() != null && !license.getTenantId().isBlank()) {
            return license.getTenantId();
        }

        if (license.getMetadata() == null) {
            return null;
        }

        Object tenant = license.getMetadata().get("tenantId");
        return tenant == null ? null : String.valueOf(tenant);
    }

    private String normalizeAuditUrl() {
        return normalizeAuditUrl(auditEndpoint);
    }

    private String normalizeAuditUrl(String endpointOverride) {
        String base = auditServiceUrl == null ? "http://audit-service:8084" : auditServiceUrl.trim();
        String endpoint = endpointOverride == null ? "/api/v1/audit" : endpointOverride.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!endpoint.startsWith("/")) {
            endpoint = "/" + endpoint;
        }
        return base + endpoint;
    }
}