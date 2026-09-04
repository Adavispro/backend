package com.adavis.mdm.controller;

import com.adavis.common.dto.ApiResponse;
import com.adavis.mdm.model.entity.Department;
import com.adavis.mdm.security.InternalRequestValidator;
import com.adavis.mdm.security.SecurityContextService;
import com.adavis.mdm.service.DepartmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mdm/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String TENANT_ID_HEADER = "X-Tenant-Id";
    private static final String PLANT_ID_HEADER = "X-Plant-Id";

    private final DepartmentService departmentService;
    private final InternalRequestValidator internalRequestValidator;
    private final SecurityContextService securityContextService;

    @PostMapping
    public ResponseEntity<ApiResponse<Department>> createDepartment(
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestHeader(value = TENANT_ID_HEADER, required = false) String headerTenantId,
            @Valid @RequestBody Department department,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        if (!StringUtils.hasText(department.getTenantId()) && StringUtils.hasText(headerTenantId)) {
            department.setTenantId(headerTenantId);
        }
        Department created = departmentService.createDepartment(department, actor);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Department created successfully", created));
    }

    @GetMapping({"", "/tree"})
    public ResponseEntity<ApiResponse<List<Department>>> getAllDepartments(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String plantId,
            @RequestParam(required = false) Boolean isActive,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestHeader(value = TENANT_ID_HEADER, required = false) String headerTenantId,
            @RequestHeader(value = PLANT_ID_HEADER, required = false) String headerPlantId,
            HttpServletRequest httpRequest) {
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        String resolvedTenant = StringUtils.hasText(tenantId) ? tenantId : headerTenantId;
        String resolvedPlant = StringUtils.hasText(plantId) ? plantId : headerPlantId;
        if (StringUtils.hasText(actor) && !"SYSTEM".equalsIgnoreCase(actor)) {
            resolvedTenant = securityContextService.resolveEffectiveTenantId(actor, resolvedTenant);
        }
        List<Department> departments = departmentService.getAllDepartments(resolvedTenant, resolvedPlant, isActive);
        return ResponseEntity.ok(ApiResponse.success(departments));
    }

    @PutMapping("/{departmentId}")
    public ResponseEntity<ApiResponse<Department>> updateDepartment(
            @PathVariable String departmentId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @Valid @RequestBody Department department,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        Department updated = departmentService.updateDepartment(departmentId, department, actor);
        return ResponseEntity.ok(ApiResponse.success("Department updated successfully", updated));
    }

    @DeleteMapping("/{departmentId}")
    public ResponseEntity<ApiResponse<Void>> deleteDepartment(
            @PathVariable String departmentId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestParam(required = false) String remarks,
            @RequestParam(required = false) String esignPassword,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        String effectiveRemarks = extractRemarks(request, remarks);
        String effectivePassword = extractPassword(request, esignPassword);
        departmentService.deleteDepartment(departmentId, actor, effectiveRemarks, effectivePassword);
        return ResponseEntity.ok(ApiResponse.successMessage("Department deleted successfully"));
    }

    @PostMapping("/{departmentId}/deactivate")
    public ResponseEntity<ApiResponse<Department>> deactivateDepartment(
            @PathVariable String departmentId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestParam(required = false) String remarks,
            @RequestParam(required = false) String esignPassword,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        String effectiveRemarks = extractRemarks(request, remarks);
        String effectivePassword = extractPassword(request, esignPassword);
        Department deactivated = departmentService.deactivateDepartment(departmentId, actor, effectiveRemarks, effectivePassword);
        return ResponseEntity.ok(ApiResponse.success("Department deactivated successfully", deactivated));
    }

    @PostMapping("/{departmentId}/activate")
    public ResponseEntity<ApiResponse<Department>> reactivateDepartment(
            @PathVariable String departmentId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestParam(required = false) String remarks,
            @RequestParam(required = false) String esignPassword,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        String effectiveRemarks = extractRemarks(request, remarks);
        String effectivePassword = extractPassword(request, esignPassword);
        Department reactivated = departmentService.reactivateDepartment(departmentId, actor, effectiveRemarks, effectivePassword);
        return ResponseEntity.ok(ApiResponse.success("Department reactivated successfully", reactivated));
    }

    private String extractRemarks(Map<String, Object> request, String fallbackRemarks) {
        if (request != null) {
            if (request.get("remarks") != null && StringUtils.hasText(String.valueOf(request.get("remarks")))) {
                return String.valueOf(request.get("remarks")).trim();
            }
            if (request.get("reason") != null && StringUtils.hasText(String.valueOf(request.get("reason")))) {
                return String.valueOf(request.get("reason")).trim();
            }
        }
        return fallbackRemarks;
    }

    private String extractPassword(Map<String, Object> request, String fallbackPassword) {
        if (request != null) {
            if (request.get("esignPassword") != null && StringUtils.hasText(String.valueOf(request.get("esignPassword")))) {
                return String.valueOf(request.get("esignPassword"));
            }
            if (request.get("password") != null && StringUtils.hasText(String.valueOf(request.get("password")))) {
                return String.valueOf(request.get("password"));
            }
        }
        return fallbackPassword;
    }
}