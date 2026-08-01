package com.adavis.mdm.service;

import com.adavis.common.exception.BusinessException;
import com.adavis.common.exception.ResourceNotFoundException;
import com.adavis.dto.license.request.ValidateLicenseRequest;
import com.adavis.mdm.model.entity.DmsDocument;
import com.adavis.mdm.model.entity.Group;
import com.adavis.mdm.model.entity.UserProfile;
import com.adavis.mdm.model.entity.GroupRoleAssignment;
import com.adavis.mdm.model.entity.Role;
import com.adavis.mdm.model.entity.RolePermission;
import com.adavis.mdm.model.entity.UserGroupAssignment;
import com.adavis.mdm.repository.DmsDocumentRepository;
import com.adavis.mdm.repository.GroupRepository;
import com.adavis.mdm.repository.GroupRoleAssignmentRepository;
import com.adavis.mdm.repository.RoleRepository;
import com.adavis.mdm.repository.RolePermissionRepository;
import com.adavis.mdm.repository.UserGroupAssignmentRepository;
import com.adavis.mdm.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private static final String USER_ASSIGNMENTS_COLLECTION = "mdm_user_context_assignments";
    private static final String PLANTS_COLLECTION = "mdm_plants";
    private static final String DEPARTMENTS_COLLECTION = "mdm_departments";

    private final UserProfileRepository userProfileRepository;
    private final DmsDocumentRepository dmsDocumentRepository;
    private final GroupRepository groupRepository;
    private final UserGroupAssignmentRepository userGroupAssignmentRepository;
    private final GroupRoleAssignmentRepository groupRoleAssignmentRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final MetadataCatalogService metadataCatalogService;
    private final AuditEventPublisher auditEventPublisher;
    private final BusinessIdGeneratorService businessIdGeneratorService;
    private final MongoTemplate mongoTemplate;

    @Value("${services.auth.base-url:http://auth-service:9081}")
    private String authServiceBaseUrl;

    @Value("${services.license.base-url:http://license-service:8082}")
    private String licenseServiceBaseUrl;

    @Value("${services.license.module-id:MOD-MDM}")
    private String moduleId;

    @Value("${password.policy.min-length:8}")
    private int passwordPolicyMinLength;

    @Value("${password.policy.require-uppercase:true}")
    private boolean passwordPolicyRequireUppercase;

    @Value("${password.policy.require-lowercase:true}")
    private boolean passwordPolicyRequireLowercase;

    @Value("${password.policy.require-numbers:true}")
    private boolean passwordPolicyRequireNumbers;

    @Value("${password.policy.require-special:true}")
    private boolean passwordPolicyRequireSpecial;

    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern NUMBER_PATTERN = Pattern.compile(".*\\d.*");
    private static final Pattern SPECIAL_PATTERN = Pattern.compile(".*[^a-zA-Z0-9].*");

    private final RestTemplate restTemplate = new RestTemplate();

    @CacheEvict(value = "users", allEntries = true)
    public UserProfile createUser(UserProfile userProfile) {
        return createUser(userProfile, null, null, null, List.of(), List.of(), null, null);
    }

    @CacheEvict(value = "users", allEntries = true)
    public UserProfile createUser(UserProfile userProfile, String initialPassword, String currentUserId) {
        return createUser(userProfile, initialPassword, currentUserId, null, List.of(), List.of(), null, null);
    }

    @CacheEvict(value = "users", allEntries = true)
    public UserProfile createUser(UserProfile userProfile,
                                  String initialPassword,
                                  String currentUserId,
                                  String itAdminUserId,
                                  List<String> supportingDocumentIds,
                                  List<Map<String, Object>> supportingDocuments,
                                  String supportingDocumentType,
                                  String reason) {
        if (!StringUtils.hasText(userProfile.getTenantId())) {
            throw new BusinessException("tenantId is required for user onboarding", "TENANT_ID_REQUIRED");
        }
        ensureItAdminCanCreateUser(currentUserId, userProfile.getTenantId());

        if (!StringUtils.hasText(userProfile.getUserId())) {
            throw new BusinessException("User ID is required for onboarding", "USER_ID_REQUIRED");
        }
        userProfile.setUserId(userProfile.getUserId().trim());

        // userTrackId is always sequence-generated by the platform.
        userProfile.setUserTrackId(businessIdGeneratorService.nextId("mdm_user_profiles", "userTrackId", "USR-", 4));

        if (userProfileRepository.existsByUserId(userProfile.getUserId())) {
            throw new BusinessException("User ID already exists: " + userProfile.getUserId(), "DUPLICATE_USER");
        }
        if (userProfileRepository.existsByUserTrackId(userProfile.getUserTrackId())) {
            throw new BusinessException("User track ID already exists: " + userProfile.getUserTrackId(), "DUPLICATE_USER_TRACK_ID");
        }

        if (StringUtils.hasText(userProfile.getEmail())
                && userProfileRepository.findByEmail(userProfile.getEmail()).isPresent()) {
            throw new BusinessException("Email already exists: " + userProfile.getEmail(), "DUPLICATE_EMAIL");
        }

        // Backfill username for create payloads that omit it.
        if (userProfile.getUsername() == null || userProfile.getUsername().isBlank()) {
            userProfile.setUsername(deriveUsername(userProfile.getEmail(), userProfile.getUserId()));
        }

        if (userProfileRepository.findByUsername(userProfile.getUsername()).isPresent()) {
            throw new BusinessException("Username already exists: " + userProfile.getUsername(), "DUPLICATE_USERNAME");
        }

        if (!StringUtils.hasText(initialPassword)) {
            throw new BusinessException("Initial password is required for user onboarding", "INITIAL_PASSWORD_REQUIRED");
        }
        validatePasswordPolicy(initialPassword, "Initial password");

        if (userProfile.getIsActive() == null) {
            userProfile.setIsActive(true);
        }
        if (userProfile.getIsBlocked() == null) {
            userProfile.setIsBlocked(false);
        }
        if (userProfile.getIsExternal() == null) {
            userProfile.setIsExternal(false);
        }

        normalizeUserFields(userProfile);

        validateSeatAvailability(userProfile.getTenantId(), userProfile.getIsActive(), userProfile.getIsBlocked());
        provisionAuthUser(userProfile, initialPassword);
        userProfile.setCreatedAt(Instant.now());
        userProfile.setUpdatedAt(Instant.now());

        log.info("Creating user: {}", userProfile.getUserId());
        UserProfile saved = userProfileRepository.save(userProfile);
        syncLicenseUserCount(userProfile.getTenantId());

        recordLifecycleRequest(
                saved,
                "NEW_USER",
                null,
                itAdminUserId,
                supportingDocumentIds,
            supportingDocuments,
                supportingDocumentType,
                firstNonBlank(reason, "User onboarding completed"));

        auditEventPublisher.publish(saved.getUserId(), "USER_CREATED", "MDM_USER", saved.getUserId(), "SUCCESS",
                Map.of(
                "tenantId", saved.getTenantId() == null ? "" : saved.getTenantId(),
                        "email", saved.getEmail() == null ? "" : saved.getEmail(),
                        "userTrackId", saved.getUserTrackId() == null ? "" : saved.getUserTrackId()));
        return saved;
    }

    @Cacheable(value = "users", key = "#userId")
    public UserProfile getUserByUserId(String userId) {
        UserProfile user = userProfileRepository.findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        user.setSupportingDocuments(loadSupportingDocuments(userId));
        return user;
    }

        public Map<String, Object> getLoginContext(String userId, Boolean includePermissionMatrix) {
        UserProfile user = getUserByUserId(userId);
        String tenantId = user.getTenantId();

        List<UserGroupAssignment> activeGroupAssignments = userGroupAssignmentRepository.findByUserIdAndIsActiveTrue(userId);
        List<String> groupIds = activeGroupAssignments.stream()
            .map(UserGroupAssignment::getGroupId)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();

        List<Group> groups = groupIds.stream()
            .map(groupRepository::findByGroupId)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .filter(group -> Boolean.TRUE.equals(group.getIsActive()))
            .filter(group -> !StringUtils.hasText(tenantId) || tenantId.equals(group.getTenantId()))
            .toList();

        List<GroupRoleAssignment> groupRoleAssignments = groupIds.isEmpty()
            ? List.of()
            : groupRoleAssignmentRepository.findByGroupIdInAndIsActiveTrue(groupIds);

        List<String> roleIdsFromGroups = groupRoleAssignments.stream()
            .map(GroupRoleAssignment::getRoleId)
            .filter(StringUtils::hasText)
            .toList();

        List<String> roleIds = new ArrayList<>();
        roleIds.addAll(roleIdsFromGroups);
        roleIds = roleIds.stream().distinct().toList();

        List<Role> roles = roleIds.stream()
            .map(roleRepository::findByRoleId)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .filter(role -> Boolean.TRUE.equals(role.getIsActive()))
            .filter(role -> !StringUtils.hasText(tenantId) || tenantId.equals(role.getTenantId()))
            .toList();

        List<String> activeRoleIds = roles.stream().map(Role::getRoleId).toList();
        Map<String, Object> rolePermissions = new HashMap<>();
        for (String roleId : activeRoleIds) {
            List<RolePermission> permissions = rolePermissionRepository.findByRoleIdAndIsActiveTrue(roleId);
            rolePermissions.put(roleId, permissions);
        }

        Query assignmentQuery = new Query(Criteria.where("userId").is(userId).and("isActive").is(true));
        List<Document> assignments = mongoTemplate.find(assignmentQuery, Document.class, USER_ASSIGNMENTS_COLLECTION);

        List<String> assignedPlantIds = assignments.stream()
            .map(this::extractPlantIdFromAssignment)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();

        Map<String, Set<String>> departmentIdsByPlantId = new LinkedHashMap<>();
        for (Document assignment : assignments) {
            String assignmentPlantId = extractPlantIdFromAssignment(assignment);
            String departmentId = stringValue(assignment.get("departmentId"));
            if (!StringUtils.hasText(assignmentPlantId) || !StringUtils.hasText(departmentId)) {
                continue;
            }
            departmentIdsByPlantId
                .computeIfAbsent(assignmentPlantId, ignored -> new LinkedHashSet<>())
                .add(departmentId);
        }

        Set<String> allDepartmentIds = departmentIdsByPlantId.values().stream()
            .flatMap(Set::stream)
            .filter(StringUtils::hasText)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        Map<String, Map<String, Object>> departmentsById = new HashMap<>();
        if (!allDepartmentIds.isEmpty()) {
            Query departmentQuery = new Query(Criteria.where("departmentId").in(allDepartmentIds)
                .and("isActive").is(true));
            List<Map<String, Object>> departmentNodes = mongoTemplate.find(departmentQuery, Document.class, DEPARTMENTS_COLLECTION).stream()
                .map(this::toMap)
                .map(this::toCompactDepartment)
                .toList();
            for (Map<String, Object> departmentNode : departmentNodes) {
                String departmentId = stringValue(departmentNode.get("departmentId"));
                if (StringUtils.hasText(departmentId)) {
                    departmentsById.put(departmentId, departmentNode);
                }
            }
        }

        List<Map<String, Object>> assignedPlants = List.of();
        if (!assignedPlantIds.isEmpty()) {
            Query plantQuery = new Query(Criteria.where("plantId").in(assignedPlantIds)
                .and("isActive").is(true));
            plantQuery.with(Sort.by(Sort.Direction.ASC, "plantCode", "plantId"));
            assignedPlants = mongoTemplate.find(plantQuery, Document.class, PLANTS_COLLECTION).stream()
                .map(this::toMap)
                .map(plant -> toCompactPlantWithDepartments(
                    plant,
                    departmentIdsByPlantId.getOrDefault(stringValue(plant.get("plantId")), Set.of()),
                    departmentsById))
                .toList();
        }

        String selectedPlantId = assignedPlants.size() == 1
            ? stringValue(assignedPlants.get(0).get("plantId"))
                : null;

        Map<String, Object> selectedPlant = assignedPlants.stream()
            .filter(plant -> selectedPlantId != null && selectedPlantId.equals(stringValue(plant.get("plantId"))))
            .findFirst()
            .orElse(null);

        Map<String, Object> userSummary = new LinkedHashMap<>();
        userSummary.put("userId", user.getUserId());
        userSummary.put("userTrackId", user.getUserTrackId());
        userSummary.put("tenantId", user.getTenantId());
        userSummary.put("email", user.getEmail());
        userSummary.put("firstName", user.getFirstName());
        userSummary.put("lastName", user.getLastName());
        userSummary.put("phoneNumber", user.getPhoneNumber());
        userSummary.put("title", user.getTitle());
        userSummary.put("userType", user.getUserType());
        userSummary.put("isExternal", user.getIsExternal());
        userSummary.put("isActive", user.getIsActive());
        userSummary.put("isBlocked", user.getIsBlocked());
        Map<String, Object> lockStatus = resolveLoginLockStatus(user.getUserId(), Boolean.TRUE.equals(user.getIsBlocked()));
        userSummary.put("isLocked", lockStatus.get("isLocked"));
        userSummary.put("failedAttempts", lockStatus.get("failedAttempts"));
        userSummary.put("empId", user.getEmpId());
        userSummary.put("designation", user.getDesignation());

        List<Map<String, Object>> compactGroups = groups.stream()
            .map(this::toCompactGroup)
            .toList();

        List<Map<String, Object>> compactRoles = roles.stream()
            .map(this::toCompactRole)
            .toList();

        boolean isPrivilegedTenantAdmin = hasTenantAdminRole(roles);

        Map<String, Object> compactRolePermissions = toCompactRolePermissions(rolePermissions);

        Map<String, Object> context = new HashMap<>();
        context.put("user", userSummary);
        context.put("tenantId", tenantId);
        context.put("groups", compactGroups);
        context.put("roles", compactRoles);
        context.put("rolePermissions", compactRolePermissions);
        context.put("assignedPlants", assignedPlants);
        context.put("plantSelectionRequired", assignedPlants.size() > 1 && !isPrivilegedTenantAdmin);
        context.put("selectedPlantId", selectedPlantId);
        context.put("selectedPlant", selectedPlant);

        if (!Boolean.FALSE.equals(includePermissionMatrix)) {
            context.put("permissionMatrix", buildPermissionMatrix(tenantId, roles, activeRoleIds));
        }

        return context;
        }

    public Map<String, Object> selectPlantContext(String userId, String plantId, Boolean includePermissionMatrix) {
        Map<String, Object> context = new HashMap<>(getLoginContext(userId, includePermissionMatrix));
        List<Map<String, Object>> roles = castList(context.get("roles"));
        boolean isPrivilegedTenantAdmin = hasTenantAdminRoleFromContextRoles(roles);

        if (!StringUtils.hasText(plantId)) {
            if (isPrivilegedTenantAdmin) {
                context.put("plantSelectionRequired", false);
                return context;
            }
            throw new BusinessException("plantId is required", "PLANT_ID_REQUIRED");
        }

        List<Map<String, Object>> assignedPlants = castList(context.get("assignedPlants"));

        Map<String, Object> selectedPlant = assignedPlants.stream()
                .filter(plant -> plantId.equals(stringValue(plant.get("plantId"))))
                .findFirst()
                .orElseThrow(() -> new BusinessException("User is not assigned to selected plant", "PLANT_ACCESS_DENIED"));

        context.put("selectedPlantId", plantId);
        context.put("selectedPlant", selectedPlant);
        context.put("plantSelectionRequired", false);
        return context;
    }

    public Page<UserProfile> getAllUsers(Pageable pageable) {
        return getAllUsers(pageable, null);
    }

    public Page<UserProfile> getAllUsers(Pageable pageable, Boolean isActive) {
        return getAllUsers(pageable, isActive, null, null);
    }

    public Page<UserProfile> getAllUsers(Pageable pageable,
                                         Boolean isActive,
                                         Boolean isBlocked,
                                         String lifecycleStatus) {
        Query query = new Query();

        if (isActive != null) {
            query.addCriteria(Criteria.where("isActive").is(isActive));
        }

        if (isBlocked != null) {
            query.addCriteria(Criteria.where("isBlocked").is(isBlocked));
        }

        if (StringUtils.hasText(lifecycleStatus)) {
            query.addCriteria(Criteria.where("lifecycleStatus").is(lifecycleStatus.trim().toUpperCase(Locale.ROOT)));
        }

        long total = mongoTemplate.count(query, UserProfile.class);
        query.with(pageable);
        if (pageable.getSort().isUnsorted()) {
            query.with(Sort.by(Sort.Direction.DESC, "updatedAt"));
        }

        List<UserProfile> users = mongoTemplate.find(query, UserProfile.class);
        return new PageImpl<>(users, pageable, total);
    }

    @CacheEvict(value = "users", key = "#userId")
    public UserProfile updateUser(String userId, UserProfile updatedProfile) {
        UserProfile existing = getUserByUserId(userId);
        String previousTenantId = existing.getTenantId();
        boolean wasSeatConsumer = Boolean.TRUE.equals(existing.getIsActive()) && !Boolean.TRUE.equals(existing.getIsBlocked());

        if (StringUtils.hasText(updatedProfile.getEmail())) {
            String updatedEmail = updatedProfile.getEmail().trim();
            userProfileRepository.findByEmail(updatedEmail)
                    .filter(found -> !userId.equalsIgnoreCase(found.getUserId()))
                    .ifPresent(found -> {
                        throw new BusinessException("Email already exists: " + updatedEmail, "DUPLICATE_EMAIL");
                    });
            existing.setEmail(updatedEmail);
        }

        existing.setTenantId(updatedProfile.getTenantId());
        existing.setFirstName(updatedProfile.getFirstName());
        existing.setLastName(updatedProfile.getLastName());
        existing.setPhoneNumber(updatedProfile.getPhoneNumber());
        existing.setTitle(updatedProfile.getTitle());
        existing.setUserType(updatedProfile.getUserType());
        existing.setLifecycleStatus(updatedProfile.getLifecycleStatus());
        existing.setEmpId(updatedProfile.getEmpId());
        existing.setDepartmentId(updatedProfile.getDepartmentId());
        existing.setDesignation(updatedProfile.getDesignation());
        existing.setIsExternal(updatedProfile.getIsExternal());
        if (updatedProfile.getIsBlocked() != null) {
            existing.setIsBlocked(updatedProfile.getIsBlocked());
        }
        if (updatedProfile.getIsActive() != null) {
            existing.setIsActive(updatedProfile.getIsActive());
        }
        existing.setLifecycleStatus(updatedProfile.getLifecycleStatus());
        normalizeUserFields(existing);
        existing.setUpdatedAt(Instant.now());

        log.info("Updating user: {}", userId);
        UserProfile saved = userProfileRepository.save(existing);
        boolean isSeatConsumer = Boolean.TRUE.equals(saved.getIsActive()) && !Boolean.TRUE.equals(saved.getIsBlocked());
        if (!Objects.equals(previousTenantId, saved.getTenantId()) || wasSeatConsumer != isSeatConsumer) {
            syncLicenseUserCount(previousTenantId);
            syncLicenseUserCount(saved.getTenantId());
        }
        auditEventPublisher.publish(saved.getUserId(), "USER_UPDATED", "MDM_USER", saved.getUserId(), "SUCCESS",
            Map.of(
                "tenantId", saved.getTenantId() == null ? "" : saved.getTenantId(),
                "designation", saved.getDesignation() == null ? "" : saved.getDesignation()));
        return saved;
    }

    @CacheEvict(value = "users", key = "#userId")
    public void deleteUser(String userId) {
        UserProfile user = getUserByUserId(userId);
        String tenantId = user.getTenantId();
        Map<String, Object> before = userLifecycleSnapshot(user);
        user.setIsActive(false);
        user.setIsBlocked(true);
        user.setLifecycleStatus("DELETED");
        user.setUpdatedAt(Instant.now());
        UserProfile saved = userProfileRepository.save(user);
        syncLicenseUserCount(tenantId);
        auditEventPublisher.publish(saved.getUserId(), "USER_DELETED", "MDM_USER", saved.getUserId(), "SUCCESS",
            before,
            userLifecycleSnapshot(saved),
            Map.of("tenantId", saved.getTenantId() == null ? "" : saved.getTenantId()));
        log.info("Soft deleted user: {}", userId);
    }

    @CacheEvict(value = "users", key = "#userId")
    public UserProfile updateLifecycle(String userId, String action) {
        return updateLifecycle(userId, action, null, List.of(), List.of(), null, null);
    }

    @CacheEvict(value = "users", key = "#userId")
    public UserProfile updateLifecycle(String userId,
                                       String action,
                                       String itAdminUserId,
                                       List<String> supportingDocumentIds,
                                       List<Map<String, Object>> supportingDocuments,
                                       String supportingDocumentType,
                                       String reason) {
        if (!StringUtils.hasText(action)) {
            throw new BusinessException("Lifecycle action is required", "LIFECYCLE_ACTION_REQUIRED");
        }

        String normalizedAction = action.trim().toLowerCase(Locale.ROOT);
        UserProfile user = getUserByUserId(userId);
        Map<String, Object> before = userLifecycleSnapshot(user);

        switch (normalizedAction) {
            case "activate", "reactivate" -> {
                user.setIsActive(true);
                user.setIsBlocked(false);
                user.setLifecycleStatus(deriveLifecycleStatus(user));
            }
            case "deactivate" -> {
                user.setIsActive(false);
                user.setLifecycleStatus(deriveLifecycleStatus(user));
            }
            case "block" -> {
                user.setIsActive(false);
                user.setIsBlocked(true);
                user.setLifecycleStatus(deriveLifecycleStatus(user));
            }
            case "unblock" -> {
                user.setIsBlocked(false);
                user.setIsActive(true);
                user.setLifecycleStatus(deriveLifecycleStatus(user));
            }
            default -> throw new BusinessException("Unsupported lifecycle action: " + action, "INVALID_LIFECYCLE_ACTION");
        }

        user.setUpdatedAt(Instant.now());
        UserProfile saved = userProfileRepository.save(user);
        syncAuthUserStatus(saved);
        syncLicenseUserCount(saved.getTenantId());

        recordLifecycleRequest(
                saved,
                "LIFECYCLE_CHANGE",
                normalizedAction.toUpperCase(Locale.ROOT),
                itAdminUserId,
                supportingDocumentIds,
            supportingDocuments,
                supportingDocumentType,
                firstNonBlank(reason, "Lifecycle action " + normalizedAction));

        auditEventPublisher.publish(saved.getUserId(), "USER_LIFECYCLE_UPDATED", "MDM_USER", saved.getUserId(),
            "SUCCESS",
            before,
            userLifecycleSnapshot(saved),
            Map.of(
                "tenantId", saved.getTenantId() == null ? "" : saved.getTenantId(),
                "action", normalizedAction));
        return saved;
    }

    public Map<String, Object> adminResetPassword(String userId, String email) {
        return adminResetPassword(userId, email, null);
    }

    public Map<String, Object> adminResetPassword(String userId, String email, String tempPassword) {
        return adminResetPassword(userId, email, tempPassword, null, List.of(), List.of(), null, null);
    }

    public Map<String, Object> adminResetPassword(String userId,
                                                  String email,
                                                  String tempPassword,
                                                  String itAdminUserId,
                                                  List<String> supportingDocumentIds,
                                                  List<Map<String, Object>> supportingDocuments,
                                                  String supportingDocumentType,
                                                  String reason) {
        UserProfile user = getUserByUserId(userId);
                            ensureItAdminCanCreateUser(itAdminUserId, user.getTenantId());
        String resolvedEmail = StringUtils.hasText(email) ? email : user.getEmail();

        if (StringUtils.hasText(email)
                && StringUtils.hasText(user.getEmail())
                && !user.getEmail().equalsIgnoreCase(email)) {
            throw new BusinessException("Email does not match user", "EMAIL_MISMATCH");
        }

        if (!StringUtils.hasText(tempPassword)) {
            throw new BusinessException("Temporary password is required for IT admin reset", "TEMP_PASSWORD_REQUIRED");
        }
        validatePasswordPolicy(tempPassword, "Temporary password");

        String url = authServiceBaseUrl + "/internal/v1/auth/users/provision";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("username", firstNonBlank(user.getUsername(), deriveUsername(resolvedEmail, userId)));
        payload.put("email", resolvedEmail);
        payload.put("initialPassword", tempPassword);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BusinessException("Failed to reset password", "PASSWORD_RESET_FAILED");
            }

            recordLifecycleRequest(
                    user,
                    "FORGOT_PASSWORD",
                    null,
                    itAdminUserId,
                    supportingDocumentIds,
                    supportingDocuments,
                    supportingDocumentType,
                    firstNonBlank(reason, "IT admin password reset completed"));

            auditEventPublisher.publish(user.getUserId(), "USER_PASSWORD_RESET_INITIATED", "MDM_USER", user.getUserId(),
                    "SUCCESS", Map.of(
                        "tenantId", user.getTenantId() == null ? "" : user.getTenantId(),
                        "tempPasswordSet", StringUtils.hasText(tempPassword)));

            Map<String, Object> result = new HashMap<>();
            result.put("userId", userId);
            result.put("email", resolvedEmail);
            result.put("tempPasswordSet", true);
            result.put("response", response.getBody());
            return result;
        } catch (RestClientException ex) {
            throw new BusinessException("Failed to reset password: " + ex.getMessage(), "PASSWORD_RESET_FAILED");
        }
    }

    private void syncAuthUserStatus(UserProfile user) {
        if (user == null || !StringUtils.hasText(user.getUserId())) {
            return;
        }

        String url = authServiceBaseUrl + "/internal/v1/auth/users/status";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String lifecycleStatus = StringUtils.hasText(user.getLifecycleStatus())
            ? user.getLifecycleStatus().trim().toUpperCase(Locale.ROOT)
            : deriveLifecycleStatus(user);

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", user.getUserId());
        payload.put("status", lifecycleStatus);
        payload.put("isLocked", isUserLocked(user));

        try {
            restTemplate.postForEntity(url, new HttpEntity<>(payload, headers), Map.class);
        } catch (RestClientException ex) {
            log.warn("Failed to sync auth status for user {}: {}", user.getUserId(), ex.getMessage());
        }
    }

    private void validatePasswordPolicy(String password, String label) {
        PasswordPolicySettings settings = resolvePasswordPolicySettings();
        List<String> validationErrors = new ArrayList<>();

        if (!StringUtils.hasText(password)) {
            validationErrors.add(label + " is required");
        } else {
            if (password.length() < settings.minLength()) {
                validationErrors.add(label + " must be at least " + settings.minLength() + " characters long");
            }
            if (settings.requireUppercase() && !UPPERCASE_PATTERN.matcher(password).matches()) {
                validationErrors.add(label + " must contain at least one uppercase letter");
            }
            if (settings.requireLowercase() && !LOWERCASE_PATTERN.matcher(password).matches()) {
                validationErrors.add(label + " must contain at least one lowercase letter");
            }
            if (settings.requireNumbers() && !NUMBER_PATTERN.matcher(password).matches()) {
                validationErrors.add(label + " must contain at least one number");
            }
            if (settings.requireSpecial() && !SPECIAL_PATTERN.matcher(password).matches()) {
                validationErrors.add(label + " must contain at least one special character");
            }
        }

        if (!validationErrors.isEmpty()) {
            throw new BusinessException(String.join("; ", validationErrors), "INVALID_PASSWORD");
        }
    }

    private void provisionAuthUser(UserProfile userProfile, String initialPassword) {
        String url = authServiceBaseUrl + "/internal/v1/auth/users/provision";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> payload = new HashMap<>();
        payload.put("userId", userProfile.getUserId());
        payload.put("username", userProfile.getUsername());
        payload.put("email", userProfile.getEmail());
        payload.put("initialPassword", initialPassword);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BusinessException("Failed to provision auth user for " + userProfile.getUserId());
            }
        } catch (RestClientException ex) {
            throw new BusinessException("Failed to provision auth user for " + userProfile.getUserId() + ": " + ex.getMessage());
        }
    }

    private void validateSeatAvailability(String tenantId, Boolean isActive, Boolean isBlocked) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BusinessException("tenantId is required for license validation", "TENANT_ID_REQUIRED");
        }

        String url = licenseServiceBaseUrl + "/internal/v1/mdm/license/tenant/" + tenantId + "/validate";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        boolean contributesToSeat = Boolean.TRUE.equals(isActive) && !Boolean.TRUE.equals(isBlocked);
        long projectedCount = userProfileRepository.countByTenantIdAndIsActiveTrueAndIsBlockedFalse(tenantId)
                + (contributesToSeat ? 1 : 0);
        ValidateLicenseRequest requestBody = ValidateLicenseRequest.builder()
            .moduleId(normalizeModuleId(moduleId))
                .currentUserCount((int) projectedCount)
                .build();

        HttpEntity<ValidateLicenseRequest> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BusinessException("License validation failed for user onboarding", "LICENSE_VALIDATION_FAILED");
            }

            Map<String, Object> body = response.getBody();
            boolean valid = extractBooleanData(body);
            if (!valid) {
                throw new BusinessException("User seat limit exceeded or module not licensed", "LICENSE_SEAT_LIMIT_EXCEEDED");
            }
        } catch (RestClientException ex) {
            throw new BusinessException("License validation failed for user onboarding: " + ex.getMessage(), "LICENSE_VALIDATION_FAILED");
        }
    }

    private void syncLicenseUserCount(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return;
        }

        String url = licenseServiceBaseUrl + "/internal/v1/mdm/license/tenant/" + tenantId + "/user-count?userCount="
            + userProfileRepository.countByTenantIdAndIsActiveTrueAndIsBlockedFalse(tenantId);

        try {
            restTemplate.postForEntity(url, null, Map.class);
        } catch (RestClientException ex) {
            log.warn("Failed to sync license user count for tenantId {}: {}", tenantId, ex.getMessage());
        }
    }

    private PasswordPolicySettings resolvePasswordPolicySettings() {
        PasswordPolicySettings defaults = new PasswordPolicySettings(
                passwordPolicyMinLength,
                passwordPolicyRequireUppercase,
                passwordPolicyRequireLowercase,
                passwordPolicyRequireNumbers,
                passwordPolicyRequireSpecial
        );

        try {
            Document policyDoc = mongoTemplate.findOne(new Query(Criteria.where("tenantId").is("TNT-0001")),
                    Document.class, "mdm_password_policies");
            if (policyDoc != null) {
                return fromPolicyDocument(policyDoc, defaults);
            }

            Query systemConfigQuery = new Query(Criteria.where("configKey").is("PASSWORD_POLICY_DEFAULT"));
            Document systemConfig = mongoTemplate.findOne(systemConfigQuery, Document.class, "mdm_system_config");
            if (systemConfig != null) {
                Object value = systemConfig.get("value");
                if (value instanceof Map<?, ?> mapValue) {
                    Document valueDoc = new Document();
                    mapValue.forEach((k, v) -> valueDoc.put(String.valueOf(k), v));
                    return fromPolicyDocument(valueDoc, defaults);
                }
            }
        } catch (Exception ex) {
            log.warn("Failed loading password policy config, using application defaults: {}", ex.getMessage());
        }

        return defaults;
    }

    private PasswordPolicySettings fromPolicyDocument(Document source, PasswordPolicySettings defaults) {
        int minLength = intOrDefault(source.get("minLength"), defaults.minLength());
        boolean requireUppercase = boolOrDefault(
                firstNonNull(source.get("requireUppercase"), source.get("requireMixedCase")),
                defaults.requireUppercase());
        boolean requireLowercase = boolOrDefault(
                firstNonNull(source.get("requireLowercase"), source.get("requireMixedCase")),
                defaults.requireLowercase());
        boolean requireNumbers = boolOrDefault(source.get("requireNumbers"), defaults.requireNumbers());
        boolean requireSpecial = boolOrDefault(source.get("requireSpecialChar"), defaults.requireSpecial());
        return new PasswordPolicySettings(minLength, requireUppercase, requireLowercase, requireNumbers, requireSpecial);
    }

    private int intOrDefault(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str && StringUtils.hasText(str)) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private boolean boolOrDefault(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str && StringUtils.hasText(str)) {
            return Boolean.parseBoolean(str.trim());
        }
        return defaultValue;
    }

    private Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private record PasswordPolicySettings(int minLength,
                                          boolean requireUppercase,
                                          boolean requireLowercase,
                                          boolean requireNumbers,
                                          boolean requireSpecial) {
    }

    private void ensureItAdminCanCreateUser(String currentUserId, String tenantId) {
        if (!StringUtils.hasText(currentUserId)) {
            throw new BusinessException("X-User-Id header is required", "MISSING_USER_CONTEXT");
        }

        UserProfile actor = userProfileRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new BusinessException("Current user not found", "USER_CONTEXT_NOT_FOUND"));

        List<UserGroupAssignment> activeAssignments = userGroupAssignmentRepository.findByUserIdAndIsActiveTrue(currentUserId);
        if (activeAssignments.isEmpty()) {
            throw new BusinessException("Only IT admin or super admin can create users", "FORBIDDEN_OPERATION");
        }

        List<String> groupIds = activeAssignments.stream()
                .map(UserGroupAssignment::getGroupId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        List<GroupRoleAssignment> groupRoles = groupRoleAssignmentRepository.findByGroupIdInAndIsActiveTrue(groupIds);

        boolean hasPrivilegedAdminRole = groupRoles.stream()
                .map(GroupRoleAssignment::getRoleId)
                .filter(StringUtils::hasText)
                .map(roleRepository::findByRoleId)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .anyMatch(role -> tenantId.equals(role.getTenantId())
                        && Boolean.TRUE.equals(role.getIsActive())
                        && ("IT_ADMIN".equalsIgnoreCase(role.getRoleCode())
                        || "SUPER_ADMIN".equalsIgnoreCase(role.getRoleCode())));

        if (!hasPrivilegedAdminRole) {
            if (StringUtils.hasText(actor.getTenantId()) && !tenantId.equals(actor.getTenantId())) {
                throw new BusinessException("Current user cannot create users outside assigned tenant", "TENANT_ACCESS_DENIED");
            }
            throw new BusinessException("Only IT admin or super admin can create users", "FORBIDDEN_OPERATION");
        }
    }

    private void recordLifecycleRequest(UserProfile user,
                                        String requestType,
                                        String lifecycleAction,
                                        String itAdminUserId,
                                        List<String> supportingDocumentIds,
                        List<Map<String, Object>> supportingDocumentPayloads,
                                        String supportingDocumentType,
                                        String reason) {
        String requestId = businessIdGeneratorService.nextId("mdm_user_lifecycle_requests", "requestId", "REQ-", 6);
        List<Map<String, Object>> supportingDocuments = resolveSupportingDocuments(
                supportingDocumentIds,
            supportingDocumentPayloads,
                user.getTenantId(),
                itAdminUserId,
                requestType,
                lifecycleAction,
                supportingDocumentType);

        Document payload = new Document();
        payload.put("requestId", requestId);
        payload.put("tenantId", user.getTenantId());
        payload.put("targetUserId", user.getUserId());
        payload.put("requestType", requestType);
        payload.put("lifecycleAction", lifecycleAction);
        payload.put("requestStatus", "COMPLETED");
        payload.put("itAdminUserId", StringUtils.hasText(itAdminUserId) ? itAdminUserId : "SYSTEM");
        payload.put("supportingDocuments", supportingDocuments);
        payload.put("reason", reason);
        payload.put("createdAt", Instant.now());
        payload.put("updatedAt", Instant.now());
        mongoTemplate.insert(payload, "mdm_user_lifecycle_requests");
    }

    private List<Map<String, Object>> loadSupportingDocuments(String userId) {
        Query query = new Query(Criteria.where("targetUserId").is(userId));
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));

        List<Document> requests = mongoTemplate.find(query, Document.class, "mdm_user_lifecycle_requests");
        if (requests.isEmpty()) {
            return List.of();
        }

        Map<String, Map<String, Object>> deduplicated = new LinkedHashMap<>();
        for (Document request : requests) {
            Object rawSupportingDocuments = request.get("supportingDocuments");
            if (!(rawSupportingDocuments instanceof List<?> supportingDocuments)) {
                continue;
            }
            for (Object entry : supportingDocuments) {
                if (!(entry instanceof Map<?, ?> rawMap)) {
                    continue;
                }
                Map<String, Object> normalized = new LinkedHashMap<>();
                for (Map.Entry<?, ?> mapEntry : rawMap.entrySet()) {
                    normalized.put(String.valueOf(mapEntry.getKey()), mapEntry.getValue());
                }
                String documentId = stringValue(normalized.get("documentId"));
                String key = StringUtils.hasText(documentId)
                        ? documentId
                        : stringValue(normalized.get("downloadContentUrl"));
                if (!StringUtils.hasText(key)) {
                    continue;
                }
                Map<String, Object> existing = deduplicated.get(key);
                if (existing == null) {
                    existing = normalized;
                    existing.putIfAbsent("actions", new ArrayList<Map<String, Object>>());
                    deduplicated.put(key, existing);
                }
                appendSupportingDocumentAction(existing, request);
            }
        }

        return new ArrayList<>(deduplicated.values());
    }

    private List<Map<String, Object>> resolveSupportingDocuments(List<String> documentIds,
                                                                 List<Map<String, Object>> documentPayloads,
                                                                 String tenantId,
                                                                 String uploadedBy,
                                                                 String requestType,
                                                                 String lifecycleAction,
                                                                 String documentType) {
        boolean hasIds = documentIds != null && !documentIds.isEmpty();
        boolean hasPayloads = documentPayloads != null && !documentPayloads.isEmpty();
        if (!hasIds && !hasPayloads) {
            return Collections.emptyList();
        }

        List<String> requestedIds = new ArrayList<>();
        if (hasIds) {
            requestedIds.addAll(documentIds);
        }
        if (hasPayloads) {
            for (Map<String, Object> payload : documentPayloads) {
                if (payload == null) {
                    continue;
                }
                Object docIdCandidate = firstNonNull(payload.get("documentId"), payload.get("id"));
                if (docIdCandidate == null && payload.get("data") instanceof Map<?, ?> nestedData) {
                    docIdCandidate = firstNonNull(nestedData.get("documentId"), nestedData.get("id"));
                }
                if (docIdCandidate != null) {
                    requestedIds.add(String.valueOf(docIdCandidate));
                }
            }
        }

        List<Map<String, Object>> resolved = new ArrayList<>();
        for (String rawDocumentId : requestedIds.stream().distinct().toList()) {
            if (!StringUtils.hasText(rawDocumentId)) {
                continue;
            }

            String documentId = rawDocumentId.trim();
            DmsDocument doc = dmsDocumentRepository.findByDocumentId(documentId)
                    .orElse(null);
            if (doc == null) {
                log.warn("Skipping missing supporting document {} for tenant {}", documentId, tenantId);
                continue;
            }

            if (StringUtils.hasText(tenantId) && !tenantId.equals(doc.getTenantId())) {
                throw new BusinessException("Supporting document tenant mismatch for document: " + documentId,
                        "SUPPORTING_DOCUMENT_TENANT_MISMATCH");
            }

            Map<String, Object> entry = new HashMap<>();
            entry.put("documentId", doc.getDocumentId());
            entry.put("documentType", firstNonBlank(documentType, "PHYSICAL_SIGN_OFF_FORM"));
            entry.put("fileName", doc.getFileName());
            entry.put("mimeType", doc.getMimeType());
            entry.put("fileSizeBytes", doc.getFileSizeBytes());
            entry.put("downloadInfoUrl", "/api/v1/dms/documents/" + doc.getDocumentId() + "/download");
            entry.put("downloadContentUrl", "/api/v1/dms/documents/" + doc.getDocumentId() + "/content");
            entry.put("repositoryDetails", doc.getRepositoryDetails());
            entry.put("uploadedBy", StringUtils.hasText(uploadedBy) ? uploadedBy : doc.getUploadedBy());
            entry.put("uploadedAt", Instant.now());
            entry.put("requestType", requestType);
            entry.put("action", firstNonBlank(lifecycleAction, requestType));
            resolved.add(entry);
        }

        return resolved;
    }

    @SuppressWarnings("unchecked")
    private void appendSupportingDocumentAction(Map<String, Object> supportingDocument, Document request) {
        Object rawActions = supportingDocument.get("actions");
        List<Map<String, Object>> actions;
        if (rawActions instanceof List<?> existingActions) {
            actions = (List<Map<String, Object>>) existingActions;
        } else {
            actions = new ArrayList<>();
            supportingDocument.put("actions", actions);
        }

        Map<String, Object> actionEntry = new LinkedHashMap<>();
        actionEntry.put("requestId", stringValue(request.get("requestId")));
        actionEntry.put("requestType", stringValue(request.get("requestType")));
        actionEntry.put("action", firstNonBlank(stringValue(request.get("lifecycleAction")), stringValue(request.get("requestType"))));
        actionEntry.put("reason", stringValue(request.get("reason")));
        actionEntry.put("requestStatus", stringValue(request.get("requestStatus")));
        actionEntry.put("createdAt", request.get("createdAt"));

        String requestId = stringValue(request.get("requestId"));
        boolean exists = actions.stream().anyMatch(existing -> Objects.equals(stringValue(existing.get("requestId")), requestId));
        if (!exists) {
            actions.add(actionEntry);
        }
    }

    private boolean extractBooleanData(Map<String, Object> body) {
        if (body == null) {
            return false;
        }
        Object data = body.get("data");
        if (data instanceof Boolean value) {
            return value;
        }
        return false;
    }

    private void normalizeUserFields(UserProfile userProfile) {
        if (!StringUtils.hasText(userProfile.getLifecycleStatus())) {
            if (Boolean.TRUE.equals(userProfile.getIsBlocked())) {
                userProfile.setLifecycleStatus("BLOCKED");
            } else if (Boolean.FALSE.equals(userProfile.getIsActive())) {
                userProfile.setLifecycleStatus("DEACTIVATED");
            } else {
                userProfile.setLifecycleStatus("ACTIVE");
            }
        }

        if (!StringUtils.hasText(userProfile.getUserType())) {
            userProfile.setUserType("INTERNAL_EMPLOYEE");
        }
    }

    private String deriveUsername(String email, String userId) {
        if (email != null) {
            int atIndex = email.indexOf('@');
            if (atIndex > 0) {
                return email.substring(0, atIndex).trim();
            }
        }
        return userId;
    }

    private Map<String, Object> userLifecycleSnapshot(UserProfile user) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (user == null) {
            return snapshot;
        }
        snapshot.put("userId", user.getUserId());
        snapshot.put("tenantId", user.getTenantId());
        snapshot.put("isActive", user.getIsActive());
        snapshot.put("isBlocked", user.getIsBlocked());
        snapshot.put("isLocked", isUserLocked(user));
        snapshot.put("lifecycleStatus", deriveLifecycleStatus(user));
        snapshot.put("departmentId", user.getDepartmentId());
        return snapshot;
    }

    private String deriveLifecycleStatus(UserProfile user) {
        if (user == null) {
            return null;
        }
        if (isUserLocked(user)) {
            return "BLOCKED";
        }
        if (Boolean.TRUE.equals(user.getIsActive())) {
            return "ACTIVE";
        }
        return "DEACTIVATED";
    }

    private boolean isUserLocked(UserProfile user) {
        if (user == null) {
            return false;
        }
        return Boolean.TRUE.equals(user.getIsBlocked()) || !Boolean.TRUE.equals(user.getIsActive());
    }

    private String extractPlantIdFromAssignment(Document assignment) {
        if (assignment == null) {
            return null;
        }

        String plantId = stringValue(assignment.get("plantId"));
        if (StringUtils.hasText(plantId)) {
            return plantId;
        }

        String resourceType = stringValue(assignment.get("resourceType"));
        if ("PLANT".equalsIgnoreCase(resourceType)) {
            return stringValue(assignment.get("resourceId"));
        }

        return null;
    }

    private Map<String, Object> toMap(Document document) {
        return document == null ? Map.of() : new HashMap<>(document);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean hasTenantAdminRole(List<Role> roles) {
        return roles.stream()
                .map(Role::getRoleCode)
                .filter(StringUtils::hasText)
                .anyMatch(this::isTenantAdminRoleCode);
    }

    private boolean hasTenantAdminRoleFromContextRoles(List<Map<String, Object>> roles) {
        return roles.stream()
                .map(role -> stringValue(role.get("roleCode")))
                .filter(StringUtils::hasText)
                .anyMatch(this::isTenantAdminRoleCode);
    }

    private boolean isTenantAdminRoleCode(String roleCode) {
        return "SUPER_ADMIN".equalsIgnoreCase(roleCode) || "IT_ADMIN".equalsIgnoreCase(roleCode);
    }

    private Map<String, Object> buildPermissionMatrix(String tenantId,
                                                      List<Role> roles,
                                                      List<String> activeRoleIds) {
        Set<String> actionCatalog = new LinkedHashSet<>(List.of("READ", "WRITE", "APPROVE"));
        Set<String> licensedModules = fetchLicensedModules(tenantId);
        boolean isAdmin = roles.stream()
                .map(Role::getRoleCode)
                .filter(StringUtils::hasText)
                .anyMatch(code -> "SUPER_ADMIN".equalsIgnoreCase(code) || "IT_ADMIN".equalsIgnoreCase(code));

        Map<String, Set<String>> screenActionsByKey = new HashMap<>();
        Map<String, Set<String>> featureActionsByKey = new HashMap<>();

        if (!isAdmin) {
            for (String roleId : activeRoleIds) {
                List<RolePermission> permissions = rolePermissionRepository.findByRoleIdAndIsActiveTrue(roleId);
                for (RolePermission permission : permissions) {
                    String moduleId = permission.getModuleId();
                    if (!StringUtils.hasText(moduleId) || permission.getScreenPermissions() == null) {
                        continue;
                    }
                    for (RolePermission.ScreenPermission screenPermission : permission.getScreenPermissions()) {
                        if (screenPermission == null || !StringUtils.hasText(screenPermission.getScreenId())) {
                            continue;
                        }
                        String screenKey = key(moduleId, screenPermission.getScreenId());
                        screenActionsByKey.computeIfAbsent(screenKey, ignored -> new LinkedHashSet<>())
                                .addAll(sanitizeActions(screenPermission.getActions(), actionCatalog));

                        if (screenPermission.getFeaturePermissions() == null) {
                            continue;
                        }
                        for (RolePermission.FeaturePermission featurePermission : screenPermission.getFeaturePermissions()) {
                            if (featurePermission == null || !StringUtils.hasText(featurePermission.getFeatureId())) {
                                continue;
                            }
                            String featureKey = key(moduleId, screenPermission.getScreenId(), featurePermission.getFeatureId());
                            featureActionsByKey.computeIfAbsent(featureKey, ignored -> new LinkedHashSet<>())
                                    .addAll(sanitizeActions(featurePermission.getActions(), actionCatalog));
                        }
                    }
                }
            }
        }

        Map<String, Object> tree = metadataCatalogService.getPermissionMatrixTree(true);
        List<Map<String, Object>> moduleNodes = castList(tree.get("modules"));
        List<Map<String, Object>> visibleModules = new ArrayList<>();

        for (Map<String, Object> moduleNode : moduleNodes) {
            String moduleId = stringValue(moduleNode.get("moduleId"));
            String moduleCode = stringValue(moduleNode.get("moduleCode"));

            if (!isModuleLicensed(moduleId, moduleCode, licensedModules)) {
                continue;
            }

            boolean fullAccess = isAdmin && isMasterDataModule(moduleNode);
            List<Map<String, Object>> screens = castList(moduleNode.get("screens"));
            List<Map<String, Object>> visibleScreens = new ArrayList<>();

            for (Map<String, Object> screenNode : screens) {
                String screenId = stringValue(screenNode.get("screenId"));
                if (!StringUtils.hasText(screenId)) {
                    continue;
                }

                List<Map<String, Object>> features = castList(screenNode.get("features"));
                List<Map<String, Object>> visibleFeatures = new ArrayList<>();
                LinkedHashSet<String> screenActionUnion = new LinkedHashSet<>();

                for (Map<String, Object> featureNode : features) {
                    String featureId = stringValue(featureNode.get("featureId"));
                    if (!StringUtils.hasText(featureId)) {
                        continue;
                    }

                    Set<String> featureActions = fullAccess
                            ? actionCatalog
                            : featureActionsByKey.getOrDefault(key(moduleId, screenId, featureId), Set.of());

                    if (featureActions.isEmpty()) {
                        continue;
                    }

                    screenActionUnion.addAll(featureActions);
                    Map<String, Object> visibleFeature = toCompactFeature(featureNode);
                    visibleFeature.put("actions", new ArrayList<>(featureActions));
                    visibleFeatures.add(visibleFeature);
                }

                if (visibleFeatures.isEmpty()) {
                    continue;
                }

                if (!fullAccess) {
                    screenActionUnion.addAll(screenActionsByKey.getOrDefault(key(moduleId, screenId), Set.of()));
                    screenActionUnion.retainAll(actionCatalog);
                    if (screenActionUnion.isEmpty()) {
                        screenActionUnion.addAll(inferScreenActionsFromFeatures(visibleFeatures));
                    }
                }

                Map<String, Object> visibleScreen = toCompactScreen(screenNode);
                visibleScreen.put("features", visibleFeatures);
                visibleScreen.put("actions", fullAccess ? new ArrayList<>(actionCatalog) : new ArrayList<>(screenActionUnion));
                visibleScreens.add(visibleScreen);
            }

            if (visibleScreens.isEmpty()) {
                continue;
            }

            Map<String, Object> visibleModule = toCompactModule(moduleNode);
            visibleModule.put("screens", visibleScreens);
            visibleModule.put("actions", fullAccess ? new ArrayList<>(actionCatalog) : List.of());
            visibleModules.add(visibleModule);
        }

        Map<String, Object> permissionMatrix = new LinkedHashMap<>();
        permissionMatrix.put("actionCatalog", new ArrayList<>(actionCatalog));
        permissionMatrix.put("modules", visibleModules);
        permissionMatrix.put("licensedModules", new ArrayList<>(licensedModules));
        permissionMatrix.put("isAdminDefaultGrant", isAdmin);
        return permissionMatrix;
    }

    private Map<String, Object> toCompactRole(Role role) {
        Map<String, Object> compact = new LinkedHashMap<>();
        compact.put("roleId", role.getRoleId());
        compact.put("roleCode", role.getRoleCode());
        compact.put("roleName", role.getRoleName());
        return compact;
    }

    private Map<String, Object> toCompactGroup(Group group) {
        Map<String, Object> compact = new LinkedHashMap<>();
        compact.put("groupId", group.getGroupId());
        compact.put("groupCode", group.getGroupCode());
        compact.put("groupName", group.getGroupName());
        return compact;
    }

    private Map<String, Object> toCompactPlantWithDepartments(Map<String, Object> plant,
                                                               Set<String> departmentIds,
                                                               Map<String, Map<String, Object>> departmentsById) {
        Map<String, Object> compactPlant = new LinkedHashMap<>();
        compactPlant.put("plantId", stringValue(plant.get("plantId")));
        compactPlant.put("plantCode", stringValue(plant.get("plantCode")));
        compactPlant.put("plantName", stringValue(plant.get("plantName")));
        compactPlant.put("type", stringValue(plant.get("type")));
        compactPlant.put("isActive", plant.get("isActive"));

        List<Map<String, Object>> departments = departmentIds.stream()
            .map(departmentsById::get)
            .filter(Objects::nonNull)
            .toList();
        compactPlant.put("departments", departments);
        return compactPlant;
    }

    private Map<String, Object> toCompactDepartment(Map<String, Object> department) {
        Map<String, Object> compact = new LinkedHashMap<>();
        compact.put("departmentId", stringValue(department.get("departmentId")));
        compact.put("departmentCode", stringValue(department.get("departmentCode")));
        compact.put("departmentName", stringValue(department.get("departmentName")));
        compact.put("path", stringValue(department.get("path")));
        return compact;
    }

    private Map<String, Object> toCompactRolePermissions(Map<String, Object> rolePermissions) {
        Map<String, Map<String, String>> catalog = buildPermissionCatalogIndex();
        Map<String, String> moduleCodeById = catalog.getOrDefault("moduleCodeById", Map.of());
        Map<String, String> moduleNameById = catalog.getOrDefault("moduleNameById", Map.of());
        Map<String, String> screenCodeById = catalog.getOrDefault("screenCodeById", Map.of());
        Map<String, String> screenNameById = catalog.getOrDefault("screenNameById", Map.of());
        Map<String, String> featureCodeById = catalog.getOrDefault("featureCodeById", Map.of());
        Map<String, String> featureNameById = catalog.getOrDefault("featureNameById", Map.of());

        Map<String, Object> compact = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rolePermissions.entrySet()) {
            String roleId = entry.getKey();
            Object value = entry.getValue();
            if (!(value instanceof List<?> permissions)) {
                compact.put(roleId, List.of());
                continue;
            }

            List<Map<String, Object>> compactPermissions = new ArrayList<>();
            for (Object item : permissions) {
                if (!(item instanceof RolePermission permission)) {
                    continue;
                }

                Map<String, Object> permissionNode = new LinkedHashMap<>();
                permissionNode.put("moduleId", permission.getModuleId());
                permissionNode.put("moduleCode", firstNonBlank(moduleCodeById.get(permission.getModuleId()), ""));
                permissionNode.put("moduleName", firstNonBlank(moduleNameById.get(permission.getModuleId()), ""));

                List<Map<String, Object>> compactScreens = new ArrayList<>();
                if (permission.getScreenPermissions() != null) {
                    for (RolePermission.ScreenPermission screenPermission : permission.getScreenPermissions()) {
                        if (screenPermission == null || !StringUtils.hasText(screenPermission.getScreenId())) {
                            continue;
                        }
                        Map<String, Object> screenNode = new LinkedHashMap<>();
                        screenNode.put("screenId", screenPermission.getScreenId());
                        screenNode.put("screenCode", firstNonBlank(screenCodeById.get(screenPermission.getScreenId()), ""));
                        screenNode.put("screenName", firstNonBlank(screenNameById.get(screenPermission.getScreenId()), ""));
                        screenNode.put("actions", screenPermission.getActions() == null ? List.of() : screenPermission.getActions());

                        List<Map<String, Object>> compactFeatures = new ArrayList<>();
                        if (screenPermission.getFeaturePermissions() != null) {
                            for (RolePermission.FeaturePermission featurePermission : screenPermission.getFeaturePermissions()) {
                                if (featurePermission == null || !StringUtils.hasText(featurePermission.getFeatureId())) {
                                    continue;
                                }
                                Map<String, Object> featureNode = new LinkedHashMap<>();
                                featureNode.put("featureId", featurePermission.getFeatureId());
                                featureNode.put("featureCode", firstNonBlank(featureCodeById.get(featurePermission.getFeatureId()), ""));
                                featureNode.put("featureName", firstNonBlank(featureNameById.get(featurePermission.getFeatureId()), ""));
                                featureNode.put("actions", featurePermission.getActions() == null ? List.of() : featurePermission.getActions());
                                compactFeatures.add(featureNode);
                            }
                        }

                        screenNode.put("features", compactFeatures);
                        compactScreens.add(screenNode);
                    }
                }

                permissionNode.put("screens", compactScreens);
                compactPermissions.add(permissionNode);
            }
            compact.put(roleId, compactPermissions);
        }
        return compact;
    }

    private Map<String, Map<String, String>> buildPermissionCatalogIndex() {
        Map<String, String> moduleCodeById = new HashMap<>();
        Map<String, String> moduleNameById = new HashMap<>();
        Map<String, String> screenCodeById = new HashMap<>();
        Map<String, String> screenNameById = new HashMap<>();
        Map<String, String> featureCodeById = new HashMap<>();
        Map<String, String> featureNameById = new HashMap<>();

        Map<String, Object> tree = metadataCatalogService.getPermissionMatrixTree(true);
        List<Map<String, Object>> modules = castList(tree.get("modules"));
        for (Map<String, Object> module : modules) {
            String moduleId = stringValue(module.get("moduleId"));
            if (!StringUtils.hasText(moduleId)) {
                continue;
            }
            moduleCodeById.put(moduleId, firstNonBlank(stringValue(module.get("moduleCode")), ""));
            moduleNameById.put(moduleId, firstNonBlank(stringValue(module.get("moduleName")), ""));

            List<Map<String, Object>> screens = castList(module.get("screens"));
            for (Map<String, Object> screen : screens) {
                String screenId = stringValue(screen.get("screenId"));
                if (!StringUtils.hasText(screenId)) {
                    continue;
                }
                screenCodeById.put(screenId, firstNonBlank(stringValue(screen.get("screenCode")), ""));
                screenNameById.put(screenId, firstNonBlank(stringValue(screen.get("screenName")), ""));

                List<Map<String, Object>> features = castList(screen.get("features"));
                for (Map<String, Object> feature : features) {
                    String featureId = stringValue(feature.get("featureId"));
                    if (!StringUtils.hasText(featureId)) {
                        continue;
                    }
                    featureCodeById.put(featureId, firstNonBlank(stringValue(feature.get("featureCode")), ""));
                    featureNameById.put(featureId, firstNonBlank(stringValue(feature.get("featureName")), ""));
                }
            }
        }

        Map<String, Map<String, String>> index = new HashMap<>();
        index.put("moduleCodeById", moduleCodeById);
        index.put("moduleNameById", moduleNameById);
        index.put("screenCodeById", screenCodeById);
        index.put("screenNameById", screenNameById);
        index.put("featureCodeById", featureCodeById);
        index.put("featureNameById", featureNameById);
        return index;
    }

    private Map<String, Object> toCompactModule(Map<String, Object> moduleNode) {
        Map<String, Object> compact = new LinkedHashMap<>();
        compact.put("moduleId", stringValue(moduleNode.get("moduleId")));
        compact.put("moduleCode", stringValue(moduleNode.get("moduleCode")));
        compact.put("moduleName", stringValue(moduleNode.get("moduleName")));
        return compact;
    }

    private Map<String, Object> toCompactScreen(Map<String, Object> screenNode) {
        Map<String, Object> compact = new LinkedHashMap<>();
        compact.put("screenId", stringValue(screenNode.get("screenId")));
        compact.put("screenCode", stringValue(screenNode.get("screenCode")));
        compact.put("screenName", stringValue(screenNode.get("screenName")));
        return compact;
    }

    private Map<String, Object> toCompactFeature(Map<String, Object> featureNode) {
        Map<String, Object> compact = new LinkedHashMap<>();
        compact.put("featureId", stringValue(featureNode.get("featureId")));
        compact.put("featureCode", stringValue(featureNode.get("featureCode")));
        compact.put("featureName", stringValue(featureNode.get("featureName")));
        return compact;
    }

    private Set<String> fetchLicensedModules(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return Set.of();
        }

        String url = licenseServiceBaseUrl + "/internal/v1/mdm/license/tenant/" + tenantId + "/modules";
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Set.of();
            }

            Object wrapperData = response.getBody().get("data");
            if (!(wrapperData instanceof Map<?, ?> payload)) {
                return Set.of();
            }

            Object modulesValue = payload.get("modules");
            if (!(modulesValue instanceof List<?> modules)) {
                return Set.of();
            }

            return modules.stream()
                    .map(String::valueOf)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        } catch (RestClientException ex) {
            log.warn("Failed to fetch licensed modules for tenantId {}: {}", tenantId, ex.getMessage());
            return Set.of();
        }
    }

    private Map<String, Object> resolveLoginLockStatus(String userId, boolean fallbackValue) {
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("isLocked", fallbackValue);
        fallback.put("failedAttempts", 0);

        if (!StringUtils.hasText(userId)) {
            return fallback;
        }

        String url = authServiceBaseUrl + "/internal/v1/auth/users/" + userId + "/lock-status";
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return fallback;
            }

            Object wrapperData = response.getBody().get("data");
            if (!(wrapperData instanceof Map<?, ?> payload)) {
                return fallback;
            }

            Object rawLocked = payload.get("isLocked");
            boolean resolvedLocked = fallbackValue;
            if (rawLocked instanceof Boolean locked) {
                resolvedLocked = locked;
            } else if (rawLocked != null) {
                resolvedLocked = Boolean.parseBoolean(String.valueOf(rawLocked));
            }

            int failedAttempts = 0;
            Object rawFailedAttempts = payload.get("failedAttempts");
            if (rawFailedAttempts instanceof Number n) {
                failedAttempts = n.intValue();
            } else if (rawFailedAttempts != null) {
                try {
                    failedAttempts = Integer.parseInt(String.valueOf(rawFailedAttempts));
                } catch (NumberFormatException ignored) {
                    failedAttempts = 0;
                }
            }

            Map<String, Object> resolved = new LinkedHashMap<>();
            resolved.put("isLocked", resolvedLocked);
            resolved.put("failedAttempts", failedAttempts);
            return resolved;
        } catch (RestClientException ex) {
            log.warn("Failed to fetch auth lock status for userId {}: {}", userId, ex.getMessage());
            return fallback;
        }
    }

    private boolean isModuleLicensed(String moduleId, String moduleCode, Set<String> licensedModules) {
        if (licensedModules == null || licensedModules.isEmpty()) {
            return true;
        }
        return (StringUtils.hasText(moduleId) && licensedModules.contains(moduleId))
                || (StringUtils.hasText(moduleCode) && licensedModules.contains(moduleCode));
    }

    private String normalizeModuleId(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.trim().toUpperCase().replace('_', '-');
    }

    private boolean isMasterDataModule(Map<String, Object> moduleNode) {
        String code = firstNonBlank(stringValue(moduleNode.get("moduleCode")), "");
        String name = firstNonBlank(stringValue(moduleNode.get("moduleName")), "");
        String normalizedCode = code.toUpperCase(Locale.ROOT);
        String normalizedName = name.toUpperCase(Locale.ROOT);
        return normalizedCode.contains("MDM") || normalizedName.contains("MASTER DATA");
    }

    private Set<String> sanitizeActions(List<String> actions, Set<String> allowedActions) {
        if (actions == null || actions.isEmpty()) {
            return Set.of();
        }
        return actions.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .filter(allowedActions::contains)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> inferScreenActionsFromFeatures(List<Map<String, Object>> features) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (Map<String, Object> feature : features) {
            Object raw = feature.get("actions");
            if (raw instanceof List<?> actionList) {
                for (Object action : actionList) {
                    if (action != null) {
                        actions.add(String.valueOf(action));
                    }
                }
            }
        }
        return actions;
    }

    private String key(String... parts) {
        return String.join("|", parts);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> output = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                output.add((Map<String, Object>) map);
            }
        }
        return output;
    }
}