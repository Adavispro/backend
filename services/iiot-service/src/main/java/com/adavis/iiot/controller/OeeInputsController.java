package com.adavis.iiot.controller;

import com.adavis.common.dto.ApiResponse;
import com.adavis.common.exception.BusinessException;
import com.adavis.iiot.service.OeeInputsService;
import com.adavis.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/iiot/reports/oee-inputs")
@RequiredArgsConstructor
public class OeeInputsController {
    private final OeeInputsService service;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getInputs(
            @RequestParam(required = false) String tenantId, @RequestParam(required = false) String plantId) {
        return ResponseEntity.ok(ApiResponse.success(service.getInputs(tenantId, plantId)));
    }

    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveSettings(
            @RequestBody OeeInputsService.SettingsRequest request,
            @RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(ApiResponse.success("OEE inputs saved", service.saveSettings(request, actor(authorization))));
    }

    @DeleteMapping("/settings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteSettings(
            @RequestParam String tenantId, @RequestParam String plantId, @RequestParam String equipmentId,
            @RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(ApiResponse.success("OEE inputs deleted",
                service.deleteSettings(tenantId, plantId, equipmentId, actor(authorization))));
    }

    @PostMapping("/downtime")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addDowntime(
            @RequestBody OeeInputsService.DowntimeRequest request,
            @RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(ApiResponse.success("Downtime recorded", service.addDowntime(request, actor(authorization))));
    }

    @PutMapping("/downtime/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateDowntime(
            @PathVariable String id, @RequestBody OeeInputsService.DowntimeRequest request,
            @RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(ApiResponse.success("Downtime updated", service.updateDowntime(id, request, actor(authorization))));
    }

    @DeleteMapping("/downtime/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteDowntime(
            @PathVariable String id, @RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(ApiResponse.success("Downtime deleted", service.deleteDowntime(id, actor(authorization))));
    }

    @PostMapping("/downtime/import")
    public ResponseEntity<ApiResponse<Map<String, Object>>> importDowntime(
            @RequestBody List<OeeInputsService.DowntimeRequest> rows,
            @RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(ApiResponse.success("Downtime imported", service.importDowntime(rows, actor(authorization))));
    }

    private String actor(String authorization) {
        if (!authorization.startsWith("Bearer ") || !jwtTokenProvider.validateToken(authorization.substring(7))) {
            throw new BusinessException("A valid authenticated user is required.", "UNAUTHORIZED");
        }
        String actor = jwtTokenProvider.getUserIdFromToken(authorization.substring(7));
        if (actor == null || actor.isBlank()) throw new BusinessException("A user identity is required.", "UNAUTHORIZED");
        return actor;
    }
}
