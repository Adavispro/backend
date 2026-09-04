package com.adavis.mdm.controller;

import com.adavis.common.dto.ApiResponse;
import com.adavis.mdm.model.entity.Role;
import com.adavis.mdm.model.entity.RolePermission;
import com.adavis.mdm.security.InternalRequestValidator;
import com.adavis.mdm.security.SecurityContextService;
import com.adavis.mdm.service.RolePermissionService;
import com.adavis.mdm.service.RoleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mdm/roles")
@RequiredArgsConstructor
public class RoleController {

    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";
    private static final String USER_ID_HEADER = "X-User-Id";

    private final RoleService roleService;
    private final RolePermissionService rolePermissionService;
    private final InternalRequestValidator internalRequestValidator;
    private final SecurityContextService securityContextService;

    @PostMapping
    public ResponseEntity<ApiResponse<Role>> createRole(
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @Valid @RequestBody Role role,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        Role created = roleService.createRole(role, actor);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Role created successfully", created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Role>>> getAllRoles(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) Boolean isActive,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        List<Role> roles = roleService.getAllRoles(tenantId, isActive, actor);
        return ResponseEntity.ok(ApiResponse.success(roles));
    }

    @GetMapping("/{roleId}")
    public ResponseEntity<ApiResponse<Role>> getRole(
            @PathVariable String roleId,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(roleService.getRoleByRoleId(roleId, actor)));
    }

    @PutMapping("/{roleId}")
    public ResponseEntity<ApiResponse<Role>> updateRole(
            @PathVariable String roleId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @Valid @RequestBody Role role,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        Role updated = roleService.updateRole(roleId, role, actor);
        return ResponseEntity.ok(ApiResponse.success("Role updated successfully", updated));
    }

    @DeleteMapping("/{roleId}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(
            @PathVariable String roleId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        roleService.deleteRole(roleId, actor);
        return ResponseEntity.ok(ApiResponse.successMessage("Role deleted successfully"));
    }

    @PostMapping("/{roleId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateRole(
            @PathVariable String roleId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        roleService.deleteRole(roleId, actor);
        return ResponseEntity.ok(ApiResponse.successMessage("Role deactivated successfully"));
    }

    @PostMapping("/{roleId}/activate")
    public ResponseEntity<ApiResponse<Role>> reactivateRole(
            @PathVariable String roleId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        Role reactivated = roleService.reactivateRole(roleId, actor);
        return ResponseEntity.ok(ApiResponse.success("Role reactivated successfully", reactivated));
    }

    @PostMapping("/{roleId}/permissions")
    public ResponseEntity<ApiResponse<RolePermission>> saveRolePermissions(
            @PathVariable String roleId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @Valid @RequestBody RolePermission rolePermission,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        RolePermission saved = rolePermissionService.saveRolePermissions(roleId, rolePermission, actor);
        return ResponseEntity.ok(ApiResponse.success("Role permissions saved successfully", saved));
    }

    @GetMapping("/{roleId}/permissions")
    public ResponseEntity<ApiResponse<List<RolePermission>>> getRolePermissions(
            @PathVariable String roleId,
            @RequestParam(required = false) Boolean isActive,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(rolePermissionService.getRolePermissions(roleId, isActive, actor)));
    }
}