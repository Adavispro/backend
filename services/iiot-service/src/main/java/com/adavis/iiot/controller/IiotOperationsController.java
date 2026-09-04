package com.adavis.iiot.controller;

import com.adavis.common.dto.ApiResponse;
import com.adavis.iiot.service.BatchWorkflowService;
import com.adavis.iiot.service.DynamicWorkflowEngine;
import com.adavis.iiot.service.IiotOperationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.adavis.security.JwtTokenProvider;

@RestController
@RequestMapping("/api/v1/iiot")
@RequiredArgsConstructor
public class IiotOperationsController {

    private final IiotOperationsService iiotOperationsService;
    private final BatchWorkflowService batchWorkflowService;
    private final DynamicWorkflowEngine dynamicWorkflowEngine;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/equipment-master")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createEquipmentMaster(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Equipment master created", iiotOperationsService.createEquipmentMaster(request)));
    }

    @GetMapping("/equipment-master")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getEquipmentMasters(
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String tenantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String headerTenantId) {
        String effectiveTenantId = (tenantId != null && !tenantId.isBlank()) ? tenantId : headerTenantId;
        return ResponseEntity.ok(ApiResponse.success(iiotOperationsService.getEquipmentMasters(isActive, effectiveTenantId)));
    }

    @GetMapping("/equipment-master/{equipmentId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEquipmentMaster(@PathVariable String equipmentId) {
        return ResponseEntity.ok(ApiResponse.success(iiotOperationsService.getEquipmentMaster(equipmentId)));
    }

    @PutMapping("/equipment-master/{equipmentId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateEquipmentMaster(
            @PathVariable String equipmentId,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(ApiResponse.success("Equipment master updated", iiotOperationsService.updateEquipmentMaster(equipmentId, request)));
    }

    @DeleteMapping("/equipment-master/{equipmentId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deactivateEquipmentMaster(@PathVariable String equipmentId) {
        return ResponseEntity.ok(ApiResponse.success("Equipment master deactivated", iiotOperationsService.deactivateEquipmentMaster(equipmentId)));
    }

    @PostMapping("/equipment-master/{equipmentId}/activate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> activateEquipmentMaster(@PathVariable String equipmentId) {
        return ResponseEntity.ok(ApiResponse.success("Equipment master activated", iiotOperationsService.activateEquipmentMaster(equipmentId)));
    }

    @PostMapping("/critical-parameters")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createCriticalParameter(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Critical parameter created", iiotOperationsService.createCriticalParameter(request)));
    }

    @GetMapping("/critical-parameters")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getCriticalParameters() {
        return ResponseEntity.ok(ApiResponse.success(iiotOperationsService.getCriticalParameters()));
    }

    @GetMapping("/critical-parameters/{parameterId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCriticalParameter(@PathVariable String parameterId) {
        return ResponseEntity.ok(ApiResponse.success(iiotOperationsService.getCriticalParameter(parameterId)));
    }

    @PutMapping("/critical-parameters/{parameterId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateCriticalParameter(
            @PathVariable String parameterId,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(ApiResponse.success("Critical parameter updated", iiotOperationsService.updateCriticalParameter(parameterId, request)));
    }

    @DeleteMapping("/critical-parameters/{parameterId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deactivateCriticalParameter(@PathVariable String parameterId) {
        return ResponseEntity.ok(ApiResponse.success("Critical parameter deactivated", iiotOperationsService.deactivateCriticalParameter(parameterId)));
    }

    @PostMapping("/critical-parameters/{parameterId}/activate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> activateCriticalParameter(@PathVariable String parameterId) {
        return ResponseEntity.ok(ApiResponse.success("Critical parameter activated", iiotOperationsService.activateCriticalParameter(parameterId)));
    }

    @PostMapping("/critical-parameter-limits")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createCriticalParameterLimit(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Critical parameter limit created", iiotOperationsService.createCriticalParameterLimit(request)));
    }

    @GetMapping("/critical-parameter-limits")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getCriticalParameterLimits() {
        return ResponseEntity.ok(ApiResponse.success(iiotOperationsService.getCriticalParameterLimits()));
    }

    @GetMapping("/critical-parameter-limits/{parameterLimitId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCriticalParameterLimit(@PathVariable String parameterLimitId) {
        return ResponseEntity.ok(ApiResponse.success(iiotOperationsService.getCriticalParameterLimit(parameterLimitId)));
    }

    @PutMapping("/critical-parameter-limits/{parameterLimitId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateCriticalParameterLimit(
            @PathVariable String parameterLimitId,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(ApiResponse.success("Critical parameter limit updated", iiotOperationsService.updateCriticalParameterLimit(parameterLimitId, request)));
    }

    @DeleteMapping("/critical-parameter-limits/{parameterLimitId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deactivateCriticalParameterLimit(@PathVariable String parameterLimitId) {
        return ResponseEntity.ok(ApiResponse.success("Critical parameter limit deactivated", iiotOperationsService.deactivateCriticalParameterLimit(parameterLimitId)));
    }

    @PostMapping("/critical-parameter-limits/{parameterLimitId}/activate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> activateCriticalParameterLimit(@PathVariable String parameterLimitId) {
        return ResponseEntity.ok(ApiResponse.success("Critical parameter limit activated", iiotOperationsService.activateCriticalParameterLimit(parameterLimitId)));
    }

    @PostMapping("/product-master")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createProductMaster(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product master created", iiotOperationsService.createProductMaster(request)));
    }

    @GetMapping("/product-master")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getProductMasters() {
        return ResponseEntity.ok(ApiResponse.success(iiotOperationsService.getProductMasters()));
    }

    @GetMapping("/product-master/{productId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProductMaster(@PathVariable String productId) {
        return ResponseEntity.ok(ApiResponse.success(iiotOperationsService.getProductMaster(productId)));
    }

    @PutMapping("/product-master/{productId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateProductMaster(
            @PathVariable String productId,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(ApiResponse.success("Product master updated", iiotOperationsService.updateProductMaster(productId, request)));
    }

    @DeleteMapping("/product-master/{productId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deactivateProductMaster(@PathVariable String productId) {
        return ResponseEntity.ok(ApiResponse.success("Product master deactivated", iiotOperationsService.deactivateProductMaster(productId)));
    }

    @PostMapping("/product-master/{productId}/activate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> activateProductMaster(@PathVariable String productId) {
        return ResponseEntity.ok(ApiResponse.success("Product master activated", iiotOperationsService.activateProductMaster(productId)));
    }

    @PostMapping("/ingestion/{equipmentId}/trigger")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerBatchIngestion(@PathVariable String equipmentId) {
        return ResponseEntity.ok(ApiResponse.success("Batch ingestion triggered", iiotOperationsService.triggerBatchIngestion(equipmentId)));
    }

    @GetMapping("/ingestion/{equipmentId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getIngestionStatus(
            @PathVariable String equipmentId,
            @RequestParam(defaultValue = "BATCH_CPP") String streamType) {
        return ResponseEntity.ok(ApiResponse.success(iiotOperationsService.getIngestionStatus(equipmentId, streamType)));
    }

    @GetMapping("/source-mappings")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSourceMappings() {
        return ResponseEntity.ok(ApiResponse.success(iiotOperationsService.getSourceMappings()));
    }

    @GetMapping("/source-mappings/{equipmentId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSourceMapping(@PathVariable String equipmentId) {
        return ResponseEntity.ok(ApiResponse.success(iiotOperationsService.getSourceMapping(equipmentId)));
    }

    @GetMapping("/equipment-live-status")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getEquipmentLiveStatuses(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String plantId,
            @RequestParam(required = false) String blockId,
            @RequestParam(required = false) String areaId,
            @RequestParam(required = false) String roomNo) {
        Map<String, Object> filter = Map.ofEntries(
                Map.entry("tenantId", tenantId == null ? "" : tenantId),
                Map.entry("plantId", plantId == null ? "" : plantId),
                Map.entry("blockId", blockId == null ? "" : blockId),
                Map.entry("areaId", areaId == null ? "" : areaId),
                Map.entry("roomNo", roomNo == null ? "" : roomNo));
        return ResponseEntity.ok(ApiResponse.success(iiotOperationsService.getEquipmentLiveStatuses(filter)));
    }

    @GetMapping("/equipment-live-status/{equipmentId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEquipmentLiveStatus(@PathVariable String equipmentId) {
        return ResponseEntity.ok(ApiResponse.success(iiotOperationsService.getEquipmentLiveStatus(equipmentId)));
    }

    @GetMapping("/topology")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPlantTopology(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        return ResponseEntity.ok(ApiResponse.success(iiotOperationsService.getPlantTopology(tenantId)));
    }

    @GetMapping("/reports/batch-summary")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getBatchSummary(
            @RequestHeader(value = "X-Tenant-Id", required = false) String headerTenantId,
            @RequestHeader(value = "X-Plant-Id", required = false) String headerPlantId,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String plantId,
            @RequestParam(required = false) String areaId,
            @RequestParam(required = false) String equipmentId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String lotNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        String effectiveTenantId = (tenantId != null && !tenantId.isBlank()) ? tenantId : (headerTenantId != null ? headerTenantId : "");
        String effectivePlantId = (plantId != null && !plantId.isBlank()) ? plantId : (headerPlantId != null ? headerPlantId : "");
        Map<String, Object> filter = Map.ofEntries(
                Map.entry("tenantId", effectiveTenantId),
                Map.entry("plantId", effectivePlantId),
                Map.entry("areaId", areaId == null ? "" : areaId),
                Map.entry("equipmentId", equipmentId == null ? "" : equipmentId),
                Map.entry("productName", productName == null ? "" : productName),
                Map.entry("productCode", productCode == null ? "" : productCode),
                Map.entry("batchNo", batchNo == null ? "" : batchNo),
                Map.entry("lotNo", lotNo == null ? "" : lotNo),
                Map.entry("status", status == null ? "" : status),
                Map.entry("fromDate", fromDate == null ? "" : fromDate),
                Map.entry("toDate", toDate == null ? "" : toDate),
                Map.entry("limit", limit == null ? 500 : limit),
                Map.entry("offset", offset == null ? 0 : offset));
        return ResponseEntity.ok(ApiResponse.success(iiotOperationsService.getBatchSummary(filter)));
    }

    @GetMapping("/batch-reports/{batchNo}/pdf")
    public ResponseEntity<byte[]> downloadBatchPdf(
            @PathVariable String batchNo,
            @RequestParam(required = false) String lotNo,
            @RequestParam(required = false) String equipmentCode,
            @RequestHeader(value = "X-Tenant-Id", required = false) String headerTenantId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        byte[] pdfBytes = iiotOperationsService.getBatchPdfBytes(
                batchNo, lotNo, equipmentCode, headerTenantId, userId, userRole);

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        String cleanBatch = batchNo != null ? batchNo.replaceAll("[^a-zA-Z0-9.-]", "_") : "Report";
        String cleanLot = lotNo != null && !lotNo.isBlank() ? "_" + lotNo.replaceAll("[^a-zA-Z0-9.-]", "_") : "";
        String filename = String.format("Batch_Dossier_%s%s.pdf", cleanBatch, cleanLot);
        headers.setContentDisposition(org.springframework.http.ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(pdfBytes.length);

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    @PostMapping("/reports/batch-summary/approval")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateBatchSummaryApproval(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Tenant-Id", required = false) String headerTenantId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> request) {

        // Extract fields from request body
        String batchNo = (String) request.getOrDefault("batchNo", "");
        String lotNo = (String) request.getOrDefault("lotNo", "");
        String equipmentCode = (String) request.getOrDefault("equipmentCode", "");
        String targetStatus = (String) request.getOrDefault("status", "");
        String comments = (String) request.getOrDefault("comments", "");
        String tenantId = (String) request.getOrDefault("tenantId", "");
        if ((tenantId == null || tenantId.isBlank()) && headerTenantId != null && !headerTenantId.isBlank()) {
            tenantId = headerTenantId.trim();
        }
        String supervisorName = (String) request.get("supervisorName");

        // Resolve userId from authHeader / request body if not in header
        if (userId == null || userId.isBlank()) {
            userId = (String) request.get("approvedBy");
        }
        if ((userId == null || userId.isBlank()) && authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7).trim();
                userId = jwtTokenProvider.getUserIdFromToken(token);
            } catch (Exception ignored) {}
        }
        if (userId == null || userId.isBlank()) {
            userId = "SYSTEM";
        }

        // Resolve role from request body if not in header (backward compatibility)
        if (userRole == null || userRole.isBlank()) {
            userRole = (String) request.getOrDefault("userRole", "");
        }

        Map<String, Object> result = batchWorkflowService.executeTransition(
                userId, userRole, tenantId, batchNo, lotNo, equipmentCode,
                targetStatus, comments, supervisorName);

        return ResponseEntity.ok(
                ApiResponse.success("Batch summary approval updated", result));
    }

    @GetMapping("/workflow/assignees")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getWorkflowAssignees(
            @RequestParam(required = false) String targetStatus,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String plantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String headerTenantId) {
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : headerTenantId;
        List<Map<String, Object>> assignees = batchWorkflowService.getEligibleAssignees(targetStatus, effectiveTenant, plantId);
        return ResponseEntity.ok(ApiResponse.success(assignees));
    }

    @GetMapping("/workflow/audit-trail")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getWorkflowAuditTrail(
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String lotNo,
            @RequestParam(required = false) String equipmentCode,
            @RequestParam(required = false) String tenantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String headerTenantId) {
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : headerTenantId;
        List<Map<String, Object>> auditTrail = dynamicWorkflowEngine.getWorkflowAuditTrail(batchNo, lotNo, equipmentCode, effectiveTenant);
        return ResponseEntity.ok(ApiResponse.success(auditTrail));
    }

    @GetMapping("/workflow/allowed-actions")
    public ResponseEntity<ApiResponse<List<DynamicWorkflowEngine.AllowedActionDto>>> getAllowedActions(
            @RequestParam String batchNo,
            @RequestParam String lotNo,
            @RequestParam String equipmentCode,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String plantId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "X-User-Role", required = false) String headerUserRole,
            @RequestHeader(value = "X-Tenant-Id", required = false) String headerTenantId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String userId = headerUserId;
        if ((userId == null || userId.isBlank()) && authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                userId = jwtTokenProvider.getUserIdFromToken(authHeader.substring(7).trim());
            } catch (Exception ignored) {}
        }
        if (userId == null || userId.isBlank()) {
            userId = "SYSTEM";
        }

        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : headerTenantId;
        List<DynamicWorkflowEngine.AllowedActionDto> actions = dynamicWorkflowEngine.getAllowedActions(
                userId, headerUserRole, effectiveTenant, plantId, batchNo, lotNo, equipmentCode);

        return ResponseEntity.ok(ApiResponse.success(actions));
    }

    @PostMapping("/workflow/execute-action")
    public ResponseEntity<ApiResponse<Map<String, Object>>> executeWorkflowAction(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "X-User-Role", required = false) String headerUserRole,
            @RequestHeader(value = "X-Tenant-Id", required = false) String headerTenantId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody DynamicWorkflowEngine.ActionExecutionRequest request) {

        String userId = headerUserId;
        if ((userId == null || userId.isBlank()) && authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                userId = jwtTokenProvider.getUserIdFromToken(authHeader.substring(7).trim());
            } catch (Exception ignored) {}
        }
        if (userId == null || userId.isBlank()) {
            userId = request.getUserId();
        }
        if (userId == null || userId.isBlank()) {
            userId = "SYSTEM";
        }
        request.setUserId(userId);

        if (request.getUserRole() == null || request.getUserRole().isBlank()) {
            request.setUserRole(headerUserRole);
        }
        if (request.getTenantId() == null || request.getTenantId().isBlank()) {
            request.setTenantId(headerTenantId);
        }

        Map<String, Object> result = dynamicWorkflowEngine.executeAction(request);
        return ResponseEntity.ok(ApiResponse.success("Workflow action executed successfully", result));
    }

    @PostMapping("/workflow/bulk-action")
    public ResponseEntity<ApiResponse<DynamicWorkflowEngine.BulkExecutionResult>> executeBulkAction(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "X-User-Role", required = false) String headerUserRole,
            @RequestHeader(value = "X-Tenant-Id", required = false) String headerTenantId,
            @RequestHeader(value = "X-Plant-Id", required = false) String headerPlantId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody DynamicWorkflowEngine.BulkActionExecutionRequest request) {

        String userId = headerUserId;
        if ((userId == null || userId.isBlank()) && authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                userId = jwtTokenProvider.getUserIdFromToken(authHeader.substring(7).trim());
            } catch (Exception ignored) {}
        }
        if (userId == null || userId.isBlank()) {
            userId = request.getUserId();
        }
        if (userId == null || userId.isBlank()) {
            userId = "SYSTEM";
        }
        request.setUserId(userId);

        if (request.getUserRole() == null || request.getUserRole().isBlank()) {
            request.setUserRole(headerUserRole);
        }
        if (request.getTenantId() == null || request.getTenantId().isBlank()) {
            request.setTenantId(headerTenantId);
        }
        if (request.getPlantId() == null || request.getPlantId().isBlank()) {
            request.setPlantId(headerPlantId);
        }

        DynamicWorkflowEngine.BulkExecutionResult result = dynamicWorkflowEngine.executeBulkAction(request);
        return ResponseEntity.ok(ApiResponse.success("Bulk workflow action executed", result));
    }

    @GetMapping("/workflow/dashboard-counts")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWorkflowDashboardCounts(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String plantId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "X-User-Role", required = false) String headerUserRole,
            @RequestHeader(value = "X-Tenant-Id", required = false) String headerTenantId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String userId = headerUserId;
        if ((userId == null || userId.isBlank()) && authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                userId = jwtTokenProvider.getUserIdFromToken(authHeader.substring(7).trim());
            } catch (Exception ignored) {}
        }
        if (userId == null || userId.isBlank()) {
            userId = "SYSTEM";
        }

        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : headerTenantId;
        Map<String, Object> counts = dynamicWorkflowEngine.getDashboardCounts(
                userId, headerUserRole, effectiveTenant, plantId);

        return ResponseEntity.ok(ApiResponse.success(counts));
    }

    @GetMapping("/workflow/instance")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWorkflowInstance(
            @RequestParam String batchNo,
            @RequestParam String lotNo,
            @RequestParam String equipmentCode,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String plantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String headerTenantId) {

        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : headerTenantId;
        var instance = dynamicWorkflowEngine.getOrCreateWorkflowInstance(
                batchNo, lotNo, equipmentCode, effectiveTenant, plantId, null, "SYSTEM");
        var history = dynamicWorkflowEngine.getWorkflowActionHistory(batchNo, lotNo, equipmentCode);

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("instance", instance);
        response.put("history", history);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/workflow/claim-task")
    public ResponseEntity<ApiResponse<Map<String, Object>>> claimWorkflowTask(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "X-User-Role", required = false) String headerUserRole,
            @RequestHeader(value = "X-Tenant-Id", required = false) String headerTenantId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> request) {

        String userId = headerUserId;
        if ((userId == null || userId.isBlank()) && authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                userId = jwtTokenProvider.getUserIdFromToken(authHeader.substring(7).trim());
            } catch (Exception ignored) {}
        }
        if (userId == null || userId.isBlank()) {
            userId = request.getOrDefault("userId", "SYSTEM");
        }

        String userRole = headerUserRole != null ? headerUserRole : request.getOrDefault("userRole", "");
        String tenantId = request.getOrDefault("tenantId", headerTenantId != null ? headerTenantId : "TNT-0001");
        String plantId = request.getOrDefault("plantId", "PLNT-0001");
        String batchNo = request.getOrDefault("batchNo", "");
        String lotNo = request.getOrDefault("lotNo", "");
        String equipmentCode = request.getOrDefault("equipmentCode", "");

        Map<String, Object> result = dynamicWorkflowEngine.claimWorkflowTask(
                batchNo, lotNo, equipmentCode, userId, userRole, tenantId, plantId);

        return ResponseEntity.ok(ApiResponse.success("Task claimed successfully", result));
    }

    @PostMapping("/workflow/unclaim-task")
    public ResponseEntity<ApiResponse<Map<String, Object>>> unclaimWorkflowTask(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String headerTenantId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> request) {

        String userId = headerUserId;
        if ((userId == null || userId.isBlank()) && authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                userId = jwtTokenProvider.getUserIdFromToken(authHeader.substring(7).trim());
            } catch (Exception ignored) {}
        }
        if (userId == null || userId.isBlank()) {
            userId = request.getOrDefault("userId", "SYSTEM");
        }

        String tenantId = request.getOrDefault("tenantId", headerTenantId != null ? headerTenantId : "TNT-0001");
        String batchNo = request.getOrDefault("batchNo", "");
        String lotNo = request.getOrDefault("lotNo", "");
        String equipmentCode = request.getOrDefault("equipmentCode", "");

        Map<String, Object> result = dynamicWorkflowEngine.unclaimWorkflowTask(
                batchNo, lotNo, equipmentCode, userId, tenantId);

        return ResponseEntity.ok(ApiResponse.success("Task released successfully", result));
    }

    @PostMapping("/reports/batch-summary/bulk-approval")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateBatchSummaryBulkApproval(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Tenant-Id", required = false) String headerTenantId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> request) {

        String targetStatus = (String) request.getOrDefault("status", "");
        String comments = (String) request.getOrDefault("comments", "");
        String tenantId = (String) request.getOrDefault("tenantId", "");
        if ((tenantId == null || tenantId.isBlank()) && headerTenantId != null && !headerTenantId.isBlank()) {
            tenantId = headerTenantId.trim();
        }
        String supervisorName = (String) request.get("supervisorName");

        if (userId == null || userId.isBlank()) {
            userId = (String) request.get("approvedBy");
        }
        if ((userId == null || userId.isBlank()) && authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7).trim();
                userId = jwtTokenProvider.getUserIdFromToken(token);
            } catch (Exception ignored) {}
        }
        if (userId == null || userId.isBlank()) {
            userId = "SYSTEM";
        }

        if (userRole == null || userRole.isBlank()) {
            userRole = (String) request.getOrDefault("userRole", "");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, String>> items = (List<Map<String, String>>) request.get("items");
        if (items == null) items = List.of();

        Map<String, Object> result = batchWorkflowService.executeBulkTransition(
                userId, userRole, tenantId, items, targetStatus, comments, supervisorName);

        return ResponseEntity.ok(
                ApiResponse.success("Batch summary bulk approval completed", result));
    }

    @GetMapping("/reports/cpp")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getCppData(
            @RequestParam(required = false) String tenantId,
            @RequestParam String equipmentId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String lotNo,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        Map<String, Object> filter = Map.ofEntries(
                Map.entry("tenantId", tenantId == null ? "" : tenantId),
                Map.entry("equipmentId", equipmentId),
                Map.entry("productName", productName == null ? "" : productName),
                Map.entry("batchNo", batchNo == null ? "" : batchNo),
                Map.entry("lotNo", lotNo == null ? "" : lotNo),
                Map.entry("fromDate", fromDate == null ? "" : fromDate),
                Map.entry("toDate", toDate == null ? "" : toDate),
                Map.entry("limit", limit == null ? 1000 : limit),
                Map.entry("offset", offset == null ? 0 : offset));
        return ResponseEntity.ok(ApiResponse.success(iiotOperationsService.getCppData(filter)));
    }

    @GetMapping("/reports/alarm-events")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAlarmEventData(
            @RequestParam(required = false) String tenantId,
            @RequestParam String equipmentId,
            @RequestParam(required = false) String eventCategory,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String lotNo,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        Map<String, Object> filter = Map.ofEntries(
                Map.entry("tenantId", tenantId == null ? "" : tenantId),
                Map.entry("equipmentId", equipmentId),
                Map.entry("eventCategory", eventCategory == null ? "" : eventCategory),
                Map.entry("productName", productName == null ? "" : productName),
                Map.entry("batchNo", batchNo == null ? "" : batchNo),
                Map.entry("lotNo", lotNo == null ? "" : lotNo),
                Map.entry("fromDate", fromDate == null ? "" : fromDate),
                Map.entry("toDate", toDate == null ? "" : toDate),
                Map.entry("limit", limit == null ? 1000 : limit),
                Map.entry("offset", offset == null ? 0 : offset));
        return ResponseEntity.ok(ApiResponse.success(iiotOperationsService.getAlarmEventData(filter)));
    }

    @GetMapping("/oee/metric")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOeeMetric(
            @RequestParam String assetCode,
            @RequestParam(required = false) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(iiotOperationsService.getOeeMetric(assetCode, date)));
    }

    @GetMapping("/oee/report")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getOeeReport(
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) String assetCode) {
        return ResponseEntity.ok(ApiResponse.success(iiotOperationsService.getOeeReport(fromDate, toDate, assetCode)));
    }

    @GetMapping("/monitoring/equipment")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEquipmentMonitoringView(
            @RequestParam(required = false) String tenantId,
            @RequestParam String equipmentId,
            @RequestParam String batchNo) {
        Map<String, Object> filter = Map.ofEntries(
                Map.entry("tenantId", tenantId == null ? "" : tenantId),
                Map.entry("equipmentId", equipmentId),
                Map.entry("batchNo", batchNo));
        return ResponseEntity.ok(ApiResponse.success(iiotOperationsService.getEquipmentMonitoringView(filter)));
    }

    @PostMapping("/reports/alarm-events/{equipmentId}/{eventId}/acknowledge")
    public ResponseEntity<ApiResponse<Map<String, Object>>> acknowledgeAlarmEvent(
            @PathVariable String equipmentId,
            @PathVariable String eventId,
            @RequestParam(required = false) String tenantId,
            @RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> payload = request == null ? new java.util.LinkedHashMap<>() : request;
        Map<String, Object> filter = Map.ofEntries(
                Map.entry("tenantId", tenantId == null ? "" : tenantId),
                Map.entry("equipmentId", equipmentId));

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Alarm acknowledged",
                        iiotOperationsService.acknowledgeAlarmEvent(filter, eventId, payload)));
    }
}