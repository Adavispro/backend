package com.adavis.mdm.controller;

import com.adavis.common.dto.ApiResponse;
import com.adavis.mdm.security.InternalRequestValidator;
import com.adavis.mdm.security.SecurityContextService;
import com.adavis.mdm.service.PlantTopologyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mdm/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";
    private static final String USER_ID_HEADER = "X-User-Id";

    private final PlantTopologyService plantTopologyService;
    private final InternalRequestValidator internalRequestValidator;
    private final SecurityContextService securityContextService;

    @PostMapping("/grant")
    public ResponseEntity<ApiResponse<Map<String, Object>>> grant(
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Assignment granted successfully", plantTopologyService.createAssignment(request, actor)));
    }

    @PostMapping("/exclude")
    public ResponseEntity<ApiResponse<Map<String, Object>>> exclude(
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Assignment exclusion created successfully", plantTopologyService.createExclusion(request, actor)));
    }

    @PostMapping("/iiot/grant")
    public ResponseEntity<ApiResponse<Map<String, Object>>> iiotGrant(
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("IIOT assignment granted successfully", plantTopologyService.createIiotAssignment(request, actor)));
    }

    @PostMapping("/iiot/exclude")
    public ResponseEntity<ApiResponse<Map<String, Object>>> iiotExclude(
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("IIOT assignment exclusion created successfully", plantTopologyService.createIiotExclusion(request, actor)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listAssignments(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) Boolean isActive,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(plantTopologyService.listAssignments(tenantId, isActive, actor)));
    }

    @DeleteMapping("/{assignmentId}")
    public ResponseEntity<ApiResponse<Void>> deleteAssignment(
            @PathVariable String assignmentId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        plantTopologyService.deleteAssignment(assignmentId, actor);
        return ResponseEntity.ok(ApiResponse.successMessage("Assignment deleted successfully"));
    }

    @PostMapping("/{assignmentId}/activate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reactivateAssignment(
            @PathVariable String assignmentId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Assignment reactivated successfully", plantTopologyService.reactivateAssignment(assignmentId, actor)));
    }
}
