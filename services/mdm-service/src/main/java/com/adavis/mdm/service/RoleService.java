package com.adavis.mdm.service;

import com.adavis.common.exception.BusinessException;
import com.adavis.common.exception.ResourceNotFoundException;
import com.adavis.mdm.model.entity.Role;
import com.adavis.mdm.repository.RoleRepository;
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
public class RoleService {

    private final RoleRepository roleRepository;
    private final BusinessIdGeneratorService businessIdGeneratorService;
    private final AuditEventPublisher auditEventPublisher;
    private final SecurityContextService securityContextService;

    @CacheEvict(value = "roles", allEntries = true)
    public Role createRole(Role role) {
        return createRole(role, null);
    }

    @CacheEvict(value = "roles", allEntries = true)
    public Role createRole(Role role, String actorUserId) {
        if (!StringUtils.hasText(role.getTenantId())) {
            throw new BusinessException("tenantId is required", "TENANT_ID_REQUIRED");
        }
        if (!StringUtils.hasText(role.getRoleCode())) {
            throw new BusinessException("roleCode is required", "ROLE_CODE_REQUIRED");
        }

        securityContextService.verifyTenantAccess(actorUserId, role.getTenantId());
        if (!securityContextService.isSuperAdmin(actorUserId) && securityContextService.isAdminRoleCode(role.getRoleCode())) {
            throw new BusinessException("Privilege escalation detected: cannot create administrative role", "FORBIDDEN");
        }

        role.setRoleId(businessIdGeneratorService.nextId("mdm_roles", "roleId", "ROLE-", 4));
        if (roleRepository.existsByRoleId(role.getRoleId())) {
            throw new BusinessException("Role ID already exists: " + role.getRoleId(), "DUPLICATE_ROLE");
        }

        if (roleRepository.existsByTenantIdAndRoleCode(role.getTenantId(), role.getRoleCode())) {
            throw new BusinessException("roleCode already exists: " + role.getRoleCode(), "DUPLICATE_RESOURCE");
        }

        normalizeRoleFields(role);

        if (role.getParentRoleId() != null) {
            Role parent = getRoleByRoleId(role.getParentRoleId(), actorUserId);
            role.setLevel(parent.getLevel() != null ? parent.getLevel() + 1 : 1);
        } else {
            role.setLevel(0);
        }

        role.setIsActive(true);
        role.setCreatedAt(Instant.now());
        role.setUpdatedAt(Instant.now());

        log.info("Creating role: {}", role.getRoleId());
        Role saved = roleRepository.save(role);
        auditEventPublisher.publish(
                actorUserId != null ? actorUserId : "SYSTEM",
                "ROLE_CREATED",
                "MDM_ROLE",
                saved.getRoleId(),
                "SUCCESS",
                metadataOf("roleCode", saved.getRoleCode()));
        return saved;
    }

    @Cacheable(value = "roles", key = "#roleId")
    public Role getRoleByRoleId(String roleId) {
        return getRoleByRoleId(roleId, null);
    }

    public Role getRoleByRoleId(String roleId, String actorUserId) {
        Role role = roleRepository.findByRoleId(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + roleId));
        securityContextService.verifyTenantAccess(actorUserId, role.getTenantId());
        return role;
    }

    public List<Role> getAllRoles() {
        return getAllRoles(null, null, null);
    }

    public List<Role> getAllRoles(Boolean isActive) {
        return getAllRoles(null, isActive, null);
    }

    public List<Role> getAllRoles(String tenantId, Boolean isActive, String actorUserId) {
        String effectiveTenantId = securityContextService.resolveEffectiveTenantId(actorUserId, tenantId);
        if (StringUtils.hasText(effectiveTenantId)) {
            if (isActive == null) {
                return roleRepository.findByTenantIdAndIsActiveTrue(effectiveTenantId);
            }
            return roleRepository.findByTenantIdAndIsActive(effectiveTenantId, isActive);
        }
        if (isActive == null) {
            return roleRepository.findByIsActiveTrue();
        }
        return roleRepository.findByIsActive(isActive);
    }

    public List<Role> getRolesByParent(String parentRoleId) {
        return roleRepository.findByParentRoleId(parentRoleId);
    }

    @CacheEvict(value = "roles", key = "#roleId")
    public Role updateRole(String roleId, Role updatedRole) {
        return updateRole(roleId, updatedRole, null);
    }

    @CacheEvict(value = "roles", key = "#roleId")
    public Role updateRole(String roleId, Role updatedRole, String actorUserId) {
        Role existing = getRoleByRoleId(roleId, actorUserId);
        securityContextService.verifyTenantAccess(actorUserId, existing.getTenantId());

        if (!securityContextService.isSuperAdmin(actorUserId)) {
            if (securityContextService.isAdminRoleCode(existing.getRoleCode())
                    || (StringUtils.hasText(updatedRole.getRoleCode()) && securityContextService.isAdminRoleCode(updatedRole.getRoleCode()))) {
                throw new BusinessException("Privilege escalation detected: cannot modify administrative role", "FORBIDDEN");
            }
            if (StringUtils.hasText(updatedRole.getTenantId()) && !existing.getTenantId().equalsIgnoreCase(updatedRole.getTenantId())) {
                throw new BusinessException("Cannot transfer role to another tenant", "FORBIDDEN");
            }
        }

        String tenantId = StringUtils.hasText(updatedRole.getTenantId()) ? updatedRole.getTenantId() : existing.getTenantId();
        String roleCode = StringUtils.hasText(updatedRole.getRoleCode()) ? updatedRole.getRoleCode() : existing.getRoleCode();
        String roleName = StringUtils.hasText(updatedRole.getRoleName())
                ? updatedRole.getRoleName()
                : (StringUtils.hasText(updatedRole.getName()) ? updatedRole.getName() : existing.getRoleName());
        String name = StringUtils.hasText(updatedRole.getName())
                ? updatedRole.getName()
                : (StringUtils.hasText(updatedRole.getRoleName()) ? updatedRole.getRoleName() : existing.getName());
        if (!StringUtils.hasText(tenantId)) {
            throw new BusinessException("tenantId is required", "TENANT_ID_REQUIRED");
        }
        if (!StringUtils.hasText(roleCode)) {
            throw new BusinessException("roleCode is required", "ROLE_CODE_REQUIRED");
        }

        if (roleRepository.existsByTenantIdAndRoleCodeAndRoleIdNot(
                tenantId,
                roleCode,
                roleId)) {
            throw new BusinessException("roleCode already exists: " + roleCode, "DUPLICATE_RESOURCE");
        }

        existing.setTenantId(tenantId);
        existing.setRoleCode(roleCode);
        existing.setRoleName(roleName);
        existing.setName(name);
        if (!StringUtils.hasText(existing.getName())) {
            existing.setName(existing.getRoleName());
        }
        if (updatedRole.getDescription() != null) {
            existing.setDescription(updatedRole.getDescription());
        }
        if (updatedRole.getParentRoleId() != null) {
            existing.setParentRoleId(updatedRole.getParentRoleId());
        }
        if (updatedRole.getIsActive() != null) {
            existing.setIsActive(updatedRole.getIsActive());
        }
        existing.setUpdatedAt(Instant.now());

        log.info("Updating role: {}", roleId);
        Role saved = roleRepository.save(existing);
        auditEventPublisher.publish(
                actorUserId != null ? actorUserId : "SYSTEM",
                "ROLE_UPDATED",
                "MDM_ROLE",
                saved.getRoleId(),
                "SUCCESS",
                metadataOf("roleCode", saved.getRoleCode()));
        return saved;
    }

    @CacheEvict(value = "roles", key = "#roleId")
    public void deleteRole(String roleId) {
        deleteRole(roleId, null);
    }

    @CacheEvict(value = "roles", key = "#roleId")
    public void deleteRole(String roleId, String actorUserId) {
        Role role = getRoleByRoleId(roleId, actorUserId);
        securityContextService.verifyTenantAccess(actorUserId, role.getTenantId());

        if (!securityContextService.isSuperAdmin(actorUserId) && securityContextService.isAdminRoleCode(role.getRoleCode())) {
            throw new BusinessException("Privilege escalation detected: cannot delete administrative role", "FORBIDDEN");
        }

        List<Role> children = roleRepository.findByParentRoleId(roleId);
        if (!children.isEmpty()) {
            throw new BusinessException("Cannot delete role with child roles", "ROLE_HAS_CHILDREN");
        }

        role.setIsActive(false);
        role.setUpdatedAt(Instant.now());
        roleRepository.save(role);
        auditEventPublisher.publish(actorUserId != null ? actorUserId : "SYSTEM", "ROLE_DELETED", "MDM_ROLE", role.getRoleId(), "SUCCESS", Map.of());
        log.info("Deleted role: {}", roleId);
    }

    @CacheEvict(value = "roles", key = "#roleId")
    public Role reactivateRole(String roleId) {
        return reactivateRole(roleId, null);
    }

    @CacheEvict(value = "roles", key = "#roleId")
    public Role reactivateRole(String roleId, String actorUserId) {
        Role role = getRoleByRoleId(roleId, actorUserId);
        securityContextService.verifyTenantAccess(actorUserId, role.getTenantId());

        if (!securityContextService.isSuperAdmin(actorUserId) && securityContextService.isAdminRoleCode(role.getRoleCode())) {
            throw new BusinessException("Privilege escalation detected: cannot reactivate administrative role", "FORBIDDEN");
        }

        role.setIsActive(true);
        role.setUpdatedAt(Instant.now());
        Role saved = roleRepository.save(role);
        auditEventPublisher.publish(actorUserId != null ? actorUserId : "SYSTEM", "ROLE_REACTIVATED", "MDM_ROLE", saved.getRoleId(), "SUCCESS", Map.of());
        return saved;
    }

    private void normalizeRoleFields(Role role) {
        if (!StringUtils.hasText(role.getRoleName()) && StringUtils.hasText(role.getName())) {
            role.setRoleName(role.getName());
        }
        if (!StringUtils.hasText(role.getName()) && StringUtils.hasText(role.getRoleName())) {
            role.setName(role.getRoleName());
        }
    }

    private Map<String, Object> metadataOf(String key, String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        return Map.of(key, value);
    }
}