package com.adavis.iiot.controller;

import com.adavis.common.dto.ApiResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/iiot")
@RequiredArgsConstructor
public class IiotOperationsController {

    private final IiotOperationsService iiotOperationsService;

    @PostMapping("/equipment-master")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createEquipmentMaster(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Equipment master created", iiotOperationsService.createEquipmentMaster(request)));
    }

    @GetMapping("/equipment-master")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getEquipmentMasters() {
        return ResponseEntity.ok(ApiResponse.success(iiotOperationsService.getEquipmentMasters()));
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

    @GetMapping("/reports/batch-summary")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getBatchSummary(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String plantId,
            @RequestParam(required = false) String areaId,
            @RequestParam(required = false) String equipmentId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String lotNo,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        Map<String, Object> filter = Map.ofEntries(
                Map.entry("tenantId", tenantId == null ? "" : tenantId),
                Map.entry("plantId", plantId == null ? "" : plantId),
                Map.entry("areaId", areaId == null ? "" : areaId),
                Map.entry("equipmentId", equipmentId == null ? "" : equipmentId),
                Map.entry("productName", productName == null ? "" : productName),
                Map.entry("batchNo", batchNo == null ? "" : batchNo),
                Map.entry("lotNo", lotNo == null ? "" : lotNo),
                Map.entry("fromDate", fromDate == null ? "" : fromDate),
                Map.entry("toDate", toDate == null ? "" : toDate),
                Map.entry("limit", limit == null ? 500 : limit),
                Map.entry("offset", offset == null ? 0 : offset));
        return ResponseEntity.ok(ApiResponse.success(iiotOperationsService.getBatchSummary(filter)));
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