package com.adavis.mdm.service;

import com.adavis.common.exception.BusinessException;
import com.adavis.common.exception.ResourceNotFoundException;
import com.adavis.mdm.model.entity.Group;
import com.adavis.mdm.model.entity.UserGroupAssignment;
import com.adavis.mdm.repository.GroupRepository;
import com.adavis.mdm.repository.UserGroupAssignmentRepository;
import com.adavis.mdm.security.SecurityContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserGroupService {

    private final GroupRepository groupRepository;
    private final UserGroupAssignmentRepository userGroupAssignmentRepository;
    private final BusinessIdGeneratorService businessIdGeneratorService;
    private final AuditEventPublisher auditEventPublisher;
    private final SecurityContextService securityContextService;

    @CacheEvict(value = "groups", allEntries = true)
    public Group createGroup(Group group) {
        return createGroup(group, null);
    }

    @CacheEvict(value = "groups", allEntries = true)
    public Group createGroup(Group group, String actorUserId) {
        if (!StringUtils.hasText(group.getTenantId())) {
            throw new BusinessException("tenantId is required", "TENANT_ID_REQUIRED");
        }
        if (!StringUtils.hasText(group.getGroupCode())) {
            throw new BusinessException("groupCode is required", "GROUP_CODE_REQUIRED");
        }

        securityContextService.verifyTenantAccess(actorUserId, group.getTenantId());
        if (!securityContextService.isSuperAdmin(actorUserId) && securityContextService.isAdminRoleCode(group.getGroupCode())) {
            throw new BusinessException("Privilege escalation detected: cannot create administrative group", "FORBIDDEN");
        }

        group.setGroupId(businessIdGeneratorService.nextId("mdm_user_groups", "groupId", "GRP-", 4));
        if (groupRepository.existsByGroupId(group.getGroupId())) {
            throw new BusinessException("Group ID already exists: " + group.getGroupId(), "DUPLICATE_GROUP");
        }

        if (groupRepository.existsByTenantIdAndGroupCode(group.getTenantId(), group.getGroupCode())) {
            throw new BusinessException("groupCode already exists: " + group.getGroupCode(), "DUPLICATE_RESOURCE");
        }

        normalizeGroupFields(group);

        group.setIsActive(true);
        group.setCreatedAt(Instant.now());
        group.setUpdatedAt(Instant.now());

        log.info("Creating group: {}", group.getGroupId());
        Group saved = groupRepository.save(group);
        auditEventPublisher.publish(
                actorUserId != null ? actorUserId : "SYSTEM",
                "GROUP_CREATED",
                "MDM_GROUP",
                saved.getGroupId(),
                "SUCCESS",
                metadataOf("groupCode", saved.getGroupCode()));
        return saved;
    }

    @Cacheable(value = "groups", key = "#groupId")
    public Group getGroupByGroupId(String groupId) {
        return getGroupByGroupId(groupId, null);
    }

    public Group getGroupByGroupId(String groupId, String actorUserId) {
        Group group = groupRepository.findByGroupId(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + groupId));
        securityContextService.verifyTenantAccess(actorUserId, group.getTenantId());
        return group;
    }

    public List<Group> getAllGroups() {
        return getAllGroups(null, null, null);
    }

    public List<Group> getAllGroups(Boolean isActive) {
        return getAllGroups(null, isActive, null);
    }

    public List<Group> getAllGroups(String tenantId, Boolean isActive, String actorUserId) {
        String effectiveTenantId = securityContextService.resolveEffectiveTenantId(actorUserId, tenantId);
        if (StringUtils.hasText(effectiveTenantId)) {
            if (isActive == null) {
                return groupRepository.findByTenantIdAndIsActiveTrue(effectiveTenantId);
            }
            return groupRepository.findByTenantIdAndIsActive(effectiveTenantId, isActive);
        }
        if (isActive == null) {
            return groupRepository.findByIsActiveTrue();
        }
        return groupRepository.findByIsActive(isActive);
    }

    @CacheEvict(value = "groups", key = "#groupId")
    public Group updateGroup(String groupId, Group updatedGroup) {
        return updateGroup(groupId, updatedGroup, null);
    }

    @CacheEvict(value = "groups", key = "#groupId")
    public Group updateGroup(String groupId, Group updatedGroup, String actorUserId) {
        Group existing = getGroupByGroupId(groupId, actorUserId);
        securityContextService.verifyTenantAccess(actorUserId, existing.getTenantId());

        if (!securityContextService.isSuperAdmin(actorUserId)) {
            if (securityContextService.isAdminRoleCode(existing.getGroupCode())
                    || (StringUtils.hasText(updatedGroup.getGroupCode()) && securityContextService.isAdminRoleCode(updatedGroup.getGroupCode()))) {
                throw new BusinessException("Privilege escalation detected: cannot modify administrative group", "FORBIDDEN");
            }
            if (StringUtils.hasText(updatedGroup.getTenantId()) && !existing.getTenantId().equalsIgnoreCase(updatedGroup.getTenantId())) {
                throw new BusinessException("Cannot transfer group to another tenant", "FORBIDDEN");
            }
        }

        String tenantId = StringUtils.hasText(updatedGroup.getTenantId()) ? updatedGroup.getTenantId() : existing.getTenantId();
        String groupCode = StringUtils.hasText(updatedGroup.getGroupCode()) ? updatedGroup.getGroupCode() : existing.getGroupCode();
        if (!StringUtils.hasText(tenantId)) {
            throw new BusinessException("tenantId is required", "TENANT_ID_REQUIRED");
        }
        if (!StringUtils.hasText(groupCode)) {
            throw new BusinessException("groupCode is required", "GROUP_CODE_REQUIRED");
        }

        if (groupRepository.existsByTenantIdAndGroupCodeAndGroupIdNot(
                tenantId,
                groupCode,
                groupId)) {
            throw new BusinessException("groupCode already exists: " + groupCode, "DUPLICATE_RESOURCE");
        }

        existing.setTenantId(tenantId);
        existing.setGroupCode(groupCode);
        existing.setGroupName(StringUtils.hasText(updatedGroup.getGroupName()) ? updatedGroup.getGroupName() : updatedGroup.getName());
        existing.setName(updatedGroup.getName());
        if (!StringUtils.hasText(existing.getName())) {
            existing.setName(existing.getGroupName());
        }
        existing.setDescription(updatedGroup.getDescription());
        if (updatedGroup.getIsActive() != null) {
            existing.setIsActive(updatedGroup.getIsActive());
        }
        existing.setUpdatedAt(Instant.now());

        log.info("Updating group: {}", groupId);
        Group saved = groupRepository.save(existing);
        auditEventPublisher.publish(
                actorUserId != null ? actorUserId : "SYSTEM",
                "GROUP_UPDATED",
                "MDM_GROUP",
                saved.getGroupId(),
                "SUCCESS",
                metadataOf("groupCode", saved.getGroupCode()));
        return saved;
    }

    @CacheEvict(value = "groups", key = "#groupId")
    public void deleteGroup(String groupId) {
        deleteGroup(groupId, null);
    }

    @CacheEvict(value = "groups", key = "#groupId")
    public void deleteGroup(String groupId, String actorUserId) {
        Group group = getGroupByGroupId(groupId, actorUserId);
        securityContextService.verifyTenantAccess(actorUserId, group.getTenantId());

        if (!securityContextService.isSuperAdmin(actorUserId) && securityContextService.isAdminRoleCode(group.getGroupCode())) {
            throw new BusinessException("Privilege escalation detected: cannot delete administrative group", "FORBIDDEN");
        }

        List<UserGroupAssignment> members = userGroupAssignmentRepository.findByGroupIdAndIsActiveTrue(groupId);
        if (!members.isEmpty()) {
            throw new BusinessException("Cannot delete group with active members", "GROUP_HAS_MEMBERS");
        }

        group.setIsActive(false);
        group.setUpdatedAt(Instant.now());
        groupRepository.save(group);
        auditEventPublisher.publish(actorUserId != null ? actorUserId : "SYSTEM", "GROUP_DELETED", "MDM_GROUP", group.getGroupId(), "SUCCESS", Map.of());
        log.info("Deleted group: {}", groupId);
    }

    @CacheEvict(value = "groups", key = "#groupId")
    public Group reactivateGroup(String groupId) {
        return reactivateGroup(groupId, null);
    }

    @CacheEvict(value = "groups", key = "#groupId")
    public Group reactivateGroup(String groupId, String actorUserId) {
        Group group = getGroupByGroupId(groupId, actorUserId);
        securityContextService.verifyTenantAccess(actorUserId, group.getTenantId());

        if (!securityContextService.isSuperAdmin(actorUserId) && securityContextService.isAdminRoleCode(group.getGroupCode())) {
            throw new BusinessException("Privilege escalation detected: cannot reactivate administrative group", "FORBIDDEN");
        }

        group.setIsActive(true);
        group.setUpdatedAt(Instant.now());
        Group saved = groupRepository.save(group);
        auditEventPublisher.publish(actorUserId != null ? actorUserId : "SYSTEM", "GROUP_REACTIVATED", "MDM_GROUP", saved.getGroupId(), "SUCCESS", Map.of());
        return saved;
    }

    private void normalizeGroupFields(Group group) {
        if (!StringUtils.hasText(group.getGroupName()) && StringUtils.hasText(group.getName())) {
            group.setGroupName(group.getName());
        }
        if (!StringUtils.hasText(group.getName()) && StringUtils.hasText(group.getGroupName())) {
            group.setName(group.getGroupName());
        }
    }

    private Map<String, Object> metadataOf(String key, String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        return Map.of(key, value);
    }
}