package com.adavis.mdm.controller;

import com.adavis.common.dto.ApiResponse;
import com.adavis.mdm.model.entity.Role;
import com.adavis.mdm.model.entity.RolePermission;
import com.adavis.mdm.security.InternalRequestValidator;
import com.adavis.mdm.service.RolePermissionService;
import com.adavis.mdm.service.RoleService;
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

    private final RoleService roleService;
    private final RolePermissionService rolePermissionService;
    private final InternalRequestValidator internalRequestValidator;

    @PostMapping
    public ResponseEntity<ApiResponse<Role>> createRole(
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @Valid @RequestBody Role role) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        Role created = roleService.createRole(role);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Role created successfully", created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Role>>> getAllRoles(
            @RequestParam(required = false) Boolean isActive) {
        List<Role> roles = roleService.getAllRoles(isActive);
        return ResponseEntity.ok(ApiResponse.success(roles));
    }

    @PutMapping("/{roleId}")
    public ResponseEntity<ApiResponse<Role>> updateRole(
            @PathVariable String roleId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @Valid @RequestBody Role role) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        Role updated = roleService.updateRole(roleId, role);
        return ResponseEntity.ok(ApiResponse.success("Role updated successfully", updated));
    }

    @DeleteMapping("/{roleId}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(
            @PathVariable String roleId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        roleService.deleteRole(roleId);
        return ResponseEntity.ok(ApiResponse.successMessage("Role deleted successfully"));
    }

    @PostMapping("/{roleId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateRole(
            @PathVariable String roleId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        roleService.deleteRole(roleId);
        return ResponseEntity.ok(ApiResponse.successMessage("Role deactivated successfully"));
    }

    @PostMapping("/{roleId}/activate")
    public ResponseEntity<ApiResponse<Role>> reactivateRole(
            @PathVariable String roleId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        Role reactivated = roleService.reactivateRole(roleId);
        return ResponseEntity.ok(ApiResponse.success("Role reactivated successfully", reactivated));
    }

    @PostMapping("/{roleId}/permissions")
    public ResponseEntity<ApiResponse<RolePermission>> saveRolePermissions(
            @PathVariable String roleId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @Valid @RequestBody RolePermission rolePermission) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        RolePermission saved = rolePermissionService.saveRolePermissions(roleId, rolePermission);
        return ResponseEntity.ok(ApiResponse.success("Role permissions saved successfully", saved));
    }

    @GetMapping("/{roleId}/permissions")
    public ResponseEntity<ApiResponse<List<RolePermission>>> getRolePermissions(
            @PathVariable String roleId,
            @RequestParam(required = false) Boolean isActive) {
        return ResponseEntity.ok(ApiResponse.success(rolePermissionService.getRolePermissions(roleId, isActive)));
    }
}