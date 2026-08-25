package com.adavis.iiot.controller;

import com.adavis.common.dto.ApiResponse;
import com.adavis.iiot.service.NotificationService;
import com.adavis.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping({"/api/v1/notifications", "/api/v1/iiot/notifications"})
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtTokenProvider jwtTokenProvider;

    private String resolveUserId(String headerUserId, String authHeader, String paramUserId) {
        if (headerUserId != null && !headerUserId.isBlank()) {
            return headerUserId.trim();
        }
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            try {
                if (jwtTokenProvider.validateToken(token)) {
                    String uid = jwtTokenProvider.getUserIdFromToken(token);
                    if (uid != null && !uid.isBlank()) return uid.trim();
                    String username = jwtTokenProvider.getUsernameFromToken(token);
                    if (username != null && !username.isBlank()) return username.trim();
                }
            } catch (Exception e) {
                log.warn("Failed to extract userId from JWT token: {}", e.getMessage());
            }
        }
        return (paramUserId != null && !paramUserId.isBlank()) ? paramUserId.trim() : "";
    }

    private String resolveTenantId(String headerTenantId, String authHeader, String paramTenantId) {
        if (headerTenantId != null && !headerTenantId.isBlank()) {
            return headerTenantId.trim();
        }
        return (paramTenantId != null && !paramTenantId.isBlank()) ? paramTenantId.trim() : "";
    }

    private String resolvePlantId(String headerPlantId, String headerSelectedPlantId, String paramPlantId) {
        if (headerPlantId != null && !headerPlantId.isBlank()) {
            return headerPlantId.trim();
        }
        if (headerSelectedPlantId != null && !headerSelectedPlantId.isBlank()) {
            return headerSelectedPlantId.trim();
        }
        return (paramPlantId != null && !paramPlantId.isBlank()) ? paramPlantId.trim() : "";
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getNotifications(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String headerTenantId,
            @RequestHeader(value = "X-Plant-Id", required = false) String headerPlantId,
            @RequestHeader(value = "X-Selected-Plant-Id", required = false) String headerSelectedPlantId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "userId", required = false) String paramUserId,
            @RequestParam(value = "tenantId", required = false) String paramTenantId,
            @RequestParam(value = "plantId", required = false) String paramPlantId,
            @RequestParam(value = "selectedPlantId", required = false) String paramSelectedPlantId,
            @RequestParam(value = "unreadOnly", required = false, defaultValue = "false") Boolean unreadOnly,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {

        String userId = resolveUserId(headerUserId, authHeader, paramUserId);
        String tenantId = resolveTenantId(headerTenantId, authHeader, paramTenantId);
        String effectivePlantId = resolvePlantId(headerPlantId, headerSelectedPlantId,
                (paramPlantId != null && !paramPlantId.isBlank()) ? paramPlantId : paramSelectedPlantId);

        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getUserNotifications(userId, tenantId, effectivePlantId, unreadOnly, page, limit)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUnreadCount(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String headerTenantId,
            @RequestHeader(value = "X-Plant-Id", required = false) String headerPlantId,
            @RequestHeader(value = "X-Selected-Plant-Id", required = false) String headerSelectedPlantId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "userId", required = false) String paramUserId,
            @RequestParam(value = "tenantId", required = false) String paramTenantId,
            @RequestParam(value = "plantId", required = false) String paramPlantId,
            @RequestParam(value = "selectedPlantId", required = false) String paramSelectedPlantId) {

        String userId = resolveUserId(headerUserId, authHeader, paramUserId);
        String tenantId = resolveTenantId(headerTenantId, authHeader, paramTenantId);
        String effectivePlantId = resolvePlantId(headerPlantId, headerSelectedPlantId,
                (paramPlantId != null && !paramPlantId.isBlank()) ? paramPlantId : paramSelectedPlantId);

        long unreadCount = notificationService.getUnreadCount(userId, tenantId, effectivePlantId);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "unreadCount", unreadCount,
                "userId", userId,
                "plantId", effectivePlantId
        )));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markAsRead(
            @PathVariable String notificationId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String headerTenantId,
            @RequestHeader(value = "X-Plant-Id", required = false) String headerPlantId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "userId", required = false) String paramUserId,
            @RequestParam(value = "tenantId", required = false) String paramTenantId,
            @RequestParam(value = "plantId", required = false) String paramPlantId) {

        String userId = resolveUserId(headerUserId, authHeader, paramUserId);
        String tenantId = resolveTenantId(headerTenantId, authHeader, paramTenantId);
        String effectivePlantId = resolvePlantId(headerPlantId, null, paramPlantId);

        return ResponseEntity.ok(ApiResponse.success("Notification marked as read",
                notificationService.markAsRead(notificationId, userId, tenantId, effectivePlantId)));
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markAllAsRead(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String headerTenantId,
            @RequestHeader(value = "X-Plant-Id", required = false) String headerPlantId,
            @RequestHeader(value = "X-Selected-Plant-Id", required = false) String headerSelectedPlantId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "userId", required = false) String paramUserId,
            @RequestParam(value = "tenantId", required = false) String paramTenantId,
            @RequestParam(value = "plantId", required = false) String paramPlantId,
            @RequestParam(value = "selectedPlantId", required = false) String paramSelectedPlantId) {

        String userId = resolveUserId(headerUserId, authHeader, paramUserId);
        String tenantId = resolveTenantId(headerTenantId, authHeader, paramTenantId);
        String effectivePlantId = resolvePlantId(headerPlantId, headerSelectedPlantId,
                (paramPlantId != null && !paramPlantId.isBlank()) ? paramPlantId : paramSelectedPlantId);

        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read",
                notificationService.markAllAsRead(userId, tenantId, effectivePlantId)));
    }
}
