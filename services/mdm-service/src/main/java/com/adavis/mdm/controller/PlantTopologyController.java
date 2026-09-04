package com.adavis.mdm.controller;

import com.adavis.common.dto.ApiResponse;
import com.adavis.mdm.security.InternalRequestValidator;
import com.adavis.mdm.service.PlantTopologyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mdm")
@RequiredArgsConstructor
public class PlantTopologyController {

    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";
    private static final String USER_ID_HEADER = "X-User-Id";

    private final PlantTopologyService plantTopologyService;
    private final InternalRequestValidator internalRequestValidator;

    @org.springframework.beans.factory.annotation.Value("${security.internal-auth-header:adavis-internal-auth-key}")
    private String internalAuthHeaderValue;

    private String resolveActor(String currentUserId, HttpServletRequest request) {
        String internalAuth = request != null ? request.getHeader(INTERNAL_AUTH_HEADER) : null;
        if (internalAuthHeaderValue != null && internalAuthHeaderValue.equals(internalAuth) && StringUtils.hasText(currentUserId)) {
            return currentUserId.trim();
        }
        if (request != null) {
            Object attr = request.getAttribute("authenticatedUserId");
            if (attr != null && StringUtils.hasText(attr.toString())) {
                return attr.toString().trim();
            }
        }
        return "SYSTEM";
    }

    // ==========================================
    // PLANTS
    // ==========================================

    @PostMapping("/plants")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createPlant(
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = resolveActor(currentUserId, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Plant created successfully", plantTopologyService.createPlant(request, actor)));
    }

    @GetMapping("/plants")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listPlants(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) Boolean isActive,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {
        String actor = resolveActor(currentUserId, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(plantTopologyService.listPlants(tenantId, isActive, actor)));
    }

    @GetMapping("/plants/{plantId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPlant(
            @PathVariable String plantId,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {
        String actor = resolveActor(currentUserId, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(plantTopologyService.getPlant(plantId, actor)));
    }

    @PutMapping("/plants/{plantId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updatePlant(
            @PathVariable String plantId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = resolveActor(currentUserId, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Plant updated successfully", plantTopologyService.updatePlant(plantId, request, actor)));
    }

    @DeleteMapping("/plants/{plantId}")
    public ResponseEntity<ApiResponse<Void>> deletePlant(
            @PathVariable String plantId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = resolveActor(currentUserId, httpRequest);
        plantTopologyService.deletePlant(plantId, request, actor);
        return ResponseEntity.ok(ApiResponse.successMessage("Plant deleted successfully"));
    }

    @PostMapping("/plants/{plantId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivatePlant(
            @PathVariable String plantId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = resolveActor(currentUserId, httpRequest);
        plantTopologyService.deletePlant(plantId, request, actor);
        return ResponseEntity.ok(ApiResponse.successMessage("Plant deactivated successfully"));
    }

    @PostMapping("/plants/{plantId}/activate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reactivatePlant(
            @PathVariable String plantId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = resolveActor(currentUserId, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Plant reactivated successfully", plantTopologyService.reactivatePlant(plantId, request, actor)));
    }

    // ==========================================
    // BLOCKS
    // ==========================================

    @PostMapping("/blocks")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createBlock(
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = resolveActor(currentUserId, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Block created successfully", plantTopologyService.createBlock(request, actor)));
    }

    @GetMapping("/blocks")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listBlocks(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) Boolean isActive,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {
        String actor = resolveActor(currentUserId, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(plantTopologyService.listBlocks(tenantId, isActive, actor)));
    }

    @GetMapping("/blocks/{blockId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBlock(
            @PathVariable String blockId,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {
        String actor = resolveActor(currentUserId, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(plantTopologyService.getBlock(blockId, actor)));
    }

    @PutMapping("/blocks/{blockId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateBlock(
            @PathVariable String blockId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = resolveActor(currentUserId, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Block updated successfully", plantTopologyService.updateBlock(blockId, request, actor)));
    }

    @DeleteMapping("/blocks/{blockId}")
    public ResponseEntity<ApiResponse<Void>> deleteBlock(
            @PathVariable String blockId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = resolveActor(currentUserId, httpRequest);
        plantTopologyService.deleteBlock(blockId, request, actor);
        return ResponseEntity.ok(ApiResponse.successMessage("Block deleted successfully"));
    }

    @PostMapping("/blocks/{blockId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateBlock(
            @PathVariable String blockId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = resolveActor(currentUserId, httpRequest);
        plantTopologyService.deleteBlock(blockId, request, actor);
        return ResponseEntity.ok(ApiResponse.successMessage("Block deactivated successfully"));
    }

    @PostMapping("/blocks/{blockId}/activate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reactivateBlock(
            @PathVariable String blockId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = resolveActor(currentUserId, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Block reactivated successfully", plantTopologyService.reactivateBlock(blockId, request, actor)));
    }

    // ==========================================
    // AREAS
    // ==========================================

    @PostMapping("/areas")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createArea(
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = resolveActor(currentUserId, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Area created successfully", plantTopologyService.createArea(request, actor)));
    }

    @GetMapping("/areas")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listAreas(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) Boolean isActive,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {
        String actor = resolveActor(currentUserId, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(plantTopologyService.listAreas(tenantId, isActive, actor)));
    }

    @GetMapping("/areas/{areaId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getArea(
            @PathVariable String areaId,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {
        String actor = resolveActor(currentUserId, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(plantTopologyService.getArea(areaId, actor)));
    }

    @PutMapping("/areas/{areaId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateArea(
            @PathVariable String areaId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = resolveActor(currentUserId, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Area updated successfully", plantTopologyService.updateArea(areaId, request, actor)));
    }

    @DeleteMapping("/areas/{areaId}")
    public ResponseEntity<ApiResponse<Void>> deleteArea(
            @PathVariable String areaId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = resolveActor(currentUserId, httpRequest);
        plantTopologyService.deleteArea(areaId, request, actor);
        return ResponseEntity.ok(ApiResponse.successMessage("Area deleted successfully"));
    }

    @PostMapping("/areas/{areaId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateArea(
            @PathVariable String areaId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = resolveActor(currentUserId, httpRequest);
        plantTopologyService.deleteArea(areaId, request, actor);
        return ResponseEntity.ok(ApiResponse.successMessage("Area deactivated successfully"));
    }

    @PostMapping("/areas/{areaId}/activate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reactivateArea(
            @PathVariable String areaId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = resolveActor(currentUserId, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Area reactivated successfully", plantTopologyService.reactivateArea(areaId, request, actor)));
    }

    // ==========================================
    // ROOMS
    // ==========================================

    @PostMapping("/rooms")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createRoom(
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = resolveActor(currentUserId, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Room created successfully", plantTopologyService.createRoom(request, actor)));
    }

    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listRooms(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) Boolean isActive,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {
        String actor = resolveActor(currentUserId, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(plantTopologyService.listRooms(tenantId, isActive, actor)));
    }

    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRoom(
            @PathVariable String roomId,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {
        String actor = resolveActor(currentUserId, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(plantTopologyService.getRoom(roomId, actor)));
    }

    @PutMapping("/rooms/{roomId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateRoom(
            @PathVariable String roomId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = resolveActor(currentUserId, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Room updated successfully", plantTopologyService.updateRoom(roomId, request, actor)));
    }

    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<ApiResponse<Void>> deleteRoom(
            @PathVariable String roomId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = resolveActor(currentUserId, httpRequest);
        plantTopologyService.deleteRoom(roomId, request, actor);
        return ResponseEntity.ok(ApiResponse.successMessage("Room deleted successfully"));
    }

    @PostMapping("/rooms/{roomId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateRoom(
            @PathVariable String roomId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = resolveActor(currentUserId, httpRequest);
        plantTopologyService.deleteRoom(roomId, request, actor);
        return ResponseEntity.ok(ApiResponse.successMessage("Room deactivated successfully"));
    }

    @PostMapping("/rooms/{roomId}/activate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reactivateRoom(
            @PathVariable String roomId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = resolveActor(currentUserId, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Room reactivated successfully", plantTopologyService.reactivateRoom(roomId, request, actor)));
    }
}
