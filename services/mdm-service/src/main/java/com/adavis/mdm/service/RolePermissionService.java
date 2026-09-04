package com.adavis.mdm.service;

import com.adavis.common.exception.BusinessException;
import com.adavis.mdm.model.entity.Role;
import com.adavis.mdm.model.entity.RolePermission;
import com.adavis.mdm.repository.RolePermissionRepository;
import com.adavis.mdm.security.SecurityContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RolePermissionService {

    private final RoleService roleService;
    private final RolePermissionRepository rolePermissionRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final SecurityContextService securityContextService;

    public RolePermission saveRolePermissions(String roleId, RolePermission rolePermission) {
        return saveRolePermissions(roleId, rolePermission, null);
    }

    public RolePermission saveRolePermissions(String roleId, RolePermission rolePermission, String actorUserId) {
        Role role = roleService.getRoleByRoleId(roleId, actorUserId);
        securityContextService.verifyTenantAccess(actorUserId, role.getTenantId());

        if (!securityContextService.isSuperAdmin(actorUserId) && securityContextService.isAdminRoleCode(role.getRoleCode())) {
            throw new BusinessException("Privilege escalation detected: cannot modify permissions of administrative role", "FORBIDDEN");
        }

        rolePermission.setRoleId(roleId);
        if (rolePermission.getIsActive() == null) {
            rolePermission.setIsActive(true);
        }

        Optional<RolePermission> existing = rolePermissionRepository.findByRoleIdAndModuleId(roleId, rolePermission.getModuleId());
        if (existing.isPresent()) {
            RolePermission current = existing.get();
            rolePermission.setId(current.getId());
            int currentVersion = current.getVersion() == null || current.getVersion() < 1 ? 0 : current.getVersion();
            rolePermission.setVersion(currentVersion + 1);
        } else {
            rolePermission.setVersion(1);
        }

        RolePermission saved = rolePermissionRepository.save(rolePermission);
        auditEventPublisher.publish(
                actorUserId != null ? actorUserId : "SYSTEM",
                "ROLE_PERMISSION_SAVED",
                "MDM_ROLE_PERMISSION",
                roleId,
                "SUCCESS",
                Map.of("moduleId", saved.getModuleId() == null ? "" : saved.getModuleId()));
        return saved;
    }

    public List<RolePermission> getRolePermissions(String roleId, Boolean isActive) {
        return getRolePermissions(roleId, isActive, null);
    }

    public List<RolePermission> getRolePermissions(String roleId, Boolean isActive, String actorUserId) {
        roleService.getRoleByRoleId(roleId, actorUserId);
        if (isActive == null) {
            return rolePermissionRepository.findByRoleIdAndIsActiveTrue(roleId);
        }
        return rolePermissionRepository.findByRoleIdAndIsActive(roleId, isActive);
    }

    public void deactivateRolePermissionsByModule(String roleId, String moduleId) {
        deactivateRolePermissionsByModule(roleId, moduleId, null);
    }

    public void deactivateRolePermissionsByModule(String roleId, String moduleId, String actorUserId) {
        Role role = roleService.getRoleByRoleId(roleId, actorUserId);
        securityContextService.verifyTenantAccess(actorUserId, role.getTenantId());

        if (!securityContextService.isSuperAdmin(actorUserId) && securityContextService.isAdminRoleCode(role.getRoleCode())) {
            throw new BusinessException("Privilege escalation detected: cannot deactivate permissions of administrative role", "FORBIDDEN");
        }

        RolePermission permission = rolePermissionRepository.findByRoleIdAndModuleId(roleId, moduleId)
                .orElseThrow(() -> new BusinessException("Role permission not found for module: " + moduleId, "RESOURCE_NOT_FOUND"));
        permission.setIsActive(false);
        rolePermissionRepository.save(permission);
        auditEventPublisher.publish(
                actorUserId != null ? actorUserId : "SYSTEM",
                "ROLE_PERMISSION_DEACTIVATED",
                "MDM_ROLE_PERMISSION",
                roleId,
                "SUCCESS",
                Map.of("moduleId", moduleId));
    }
}
