package com.adavis.mdm.controller;

import com.adavis.common.dto.ApiResponse;
import com.adavis.mdm.model.dto.*;
import com.adavis.mdm.model.entity.PrintAuditLog;
import com.adavis.mdm.model.entity.PrintDelegation;
import com.adavis.mdm.model.entity.PrintPermission;
import com.adavis.mdm.security.InternalRequestValidator;
import com.adavis.mdm.security.SecurityContextService;
import com.adavis.mdm.service.PrintPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mdm/print-permissions")
@RequiredArgsConstructor
public class PrintPermissionController {

    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String TENANT_ID_HEADER = "X-Tenant-Id";

    private final PrintPermissionService printPermissionService;
    private final InternalRequestValidator internalRequestValidator;
    private final SecurityContextService securityContextService;

    @PostMapping
    public ResponseEntity<ApiResponse<PrintPermission>> savePermission(
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestHeader(value = TENANT_ID_HEADER, required = false) String headerTenantId,
            @Valid @RequestBody PrintPermissionRequest request,
            HttpServletRequest httpRequest) {

        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        if (request.getTenantId() == null && headerTenantId != null) {
            request.setTenantId(headerTenantId);
        }

        PrintPermission saved = printPermissionService.savePrintPermission(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Print permission saved successfully", saved));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PrintPermission>>> getAllPermissions(
            @RequestParam(required = false) String tenantId,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestHeader(value = TENANT_ID_HEADER, required = false) String headerTenantId,
            HttpServletRequest httpRequest) {

        String effectiveTenant = tenantId != null ? tenantId : headerTenantId;
        List<PrintPermission> permissions = printPermissionService.getAllPermissions(effectiveTenant);
        return ResponseEntity.ok(ApiResponse.success(permissions));
    }

    @GetMapping("/role/{roleId}")
    public ResponseEntity<ApiResponse<List<PrintPermission>>> getRolePermissions(
            @PathVariable String roleId,
            @RequestParam(required = false) String tenantId,
            @RequestHeader(value = TENANT_ID_HEADER, required = false) String headerTenantId) {

        String effectiveTenant = tenantId != null ? tenantId : headerTenantId;
        List<PrintPermission> permissions = printPermissionService.getPermissionsByTarget(
                effectiveTenant, "ROLE", roleId);
        return ResponseEntity.ok(ApiResponse.success(permissions));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<PrintPermission>>> getUserPermissions(
            @PathVariable String userId,
            @RequestParam(required = false) String tenantId,
            @RequestHeader(value = TENANT_ID_HEADER, required = false) String headerTenantId) {

        String effectiveTenant = tenantId != null ? tenantId : headerTenantId;
        List<PrintPermission> permissions = printPermissionService.getPermissionsByTarget(
                effectiveTenant, "USER", userId);
        return ResponseEntity.ok(ApiResponse.success(permissions));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivatePermission(
            @PathVariable String id,
            @RequestParam(required = false) String reason,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {

        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        printPermissionService.deactivatePermission(id, actor, reason);
        return ResponseEntity.ok(ApiResponse.successMessage("Print permission revoked successfully"));
    }

    @GetMapping("/effective")
    public ResponseEntity<ApiResponse<EffectivePrintAccessResponse>> getEffectiveAccess(
            @RequestParam String userId,
            @RequestParam(required = false, defaultValue = "IIOT") String module,
            @RequestParam(required = false, defaultValue = "ALL") String reportType,
            @RequestParam(required = false) String tenantId,
            @RequestHeader(value = TENANT_ID_HEADER, required = false) String headerTenantId) {

        String effectiveTenant = tenantId != null ? tenantId : headerTenantId;
        EffectivePrintAccessResponse access = printPermissionService.calculateEffectiveAccess(
                effectiveTenant, userId, module, reportType);
        return ResponseEntity.ok(ApiResponse.success(access));
    }

    @PostMapping("/check")
    public ResponseEntity<ApiResponse<PrintAccessCheckResponse>> checkAccess(
            @Valid @RequestBody PrintAccessCheckRequest request,
            @RequestHeader(value = TENANT_ID_HEADER, required = false) String headerTenantId) {

        if (request.getTenantId() == null && headerTenantId != null) {
            request.setTenantId(headerTenantId);
        }
        PrintAccessCheckResponse check = printPermissionService.verifyPrintAccess(request);
        return ResponseEntity.ok(ApiResponse.success(check));
    }

    @GetMapping("/searchable-users")
    public ResponseEntity<ApiResponse<Page<PrintUserSearchDto>>> getSearchableUsers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestHeader(value = TENANT_ID_HEADER, required = false) String headerTenantId) {

        String effectiveTenant = headerTenantId != null ? headerTenantId : "TNT-0001";
        Page<PrintUserSearchDto> result = printPermissionService.getSearchableUsers(
                effectiveTenant, search, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // =========================================================================
    // Delegation Endpoints (Super Admin Controlled)
    // =========================================================================

    @GetMapping("/delegations")
    public ResponseEntity<ApiResponse<List<PrintDelegation>>> getDelegations(
            @RequestParam(required = false) String tenantId,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestHeader(value = TENANT_ID_HEADER, required = false) String headerTenantId,
            HttpServletRequest httpRequest) {

        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        String effectiveTenant = tenantId != null ? tenantId : headerTenantId;
        List<PrintDelegation> delegations = printPermissionService.getActiveDelegations(effectiveTenant, actor);
        return ResponseEntity.ok(ApiResponse.success(delegations));
    }

    @PostMapping("/delegations")
    public ResponseEntity<ApiResponse<PrintDelegation>> createDelegation(
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestHeader(value = TENANT_ID_HEADER, required = false) String headerTenantId,
            @Valid @RequestBody PrintDelegationRequest request,
            HttpServletRequest httpRequest) {

        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        if (request.getTenantId() == null && headerTenantId != null) {
            request.setTenantId(headerTenantId);
        }

        PrintDelegation created = printPermissionService.delegateAdmin(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Print management authority delegated successfully", created));
    }

    @DeleteMapping("/delegations/{id}")
    public ResponseEntity<ApiResponse<Void>> revokeDelegation(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "Revoked by Super Admin") String reason,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {

        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        printPermissionService.revokeDelegation(id, reason, actor);
        return ResponseEntity.ok(ApiResponse.successMessage("Delegation revoked successfully"));
    }

    // =========================================================================
    // Audit Trail Endpoints
    // =========================================================================

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Page<PrintAuditLog>>> getAuditLogs(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "25") int size,
            @RequestHeader(value = TENANT_ID_HEADER, required = false) String headerTenantId) {

        String effectiveTenant = tenantId != null ? tenantId : headerTenantId;
        Page<PrintAuditLog> logs = printPermissionService.getAuditLogs(effectiveTenant, page, size);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    @PostMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Void>> recordAuditLog(
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestBody PrintAuditLog logEntry) {

        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        printPermissionService.recordAuditLog(
                logEntry.getTenantId(),
                logEntry.getEventCategory(),
                logEntry.getAction(),
                logEntry.getStatus(),
                logEntry.getUserId(),
                logEntry.getUserRole(),
                logEntry.getPlantId(),
                logEntry.getModule(),
                logEntry.getReportType(),
                logEntry.getDocumentId(),
                logEntry.getCopyNumber(),
                logEntry.getJustificationReason(),
                logEntry.getDenialReason(),
                logEntry.getMetadata()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.successMessage("Audit log recorded"));
    }
}

