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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NotificationController.class);

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

    private String resolveTenantId(String headerTenantId, String paramTenantId) {
        if (headerTenantId != null && !headerTenantId.isBlank()) {
            return headerTenantId.trim();
        }
        return (paramTenantId != null && !paramTenantId.isBlank()) ? paramTenantId.trim() : "";
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getNotifications(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String headerTenantId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "userId", required = false) String paramUserId,
            @RequestParam(value = "tenantId", required = false) String paramTenantId,
            @RequestParam(value = "unreadOnly", required = false, defaultValue = "false") Boolean unreadOnly,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {

        String userId = resolveUserId(headerUserId, authHeader, paramUserId);
        String tenantId = resolveTenantId(headerTenantId, paramTenantId);

        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getUserNotifications(userId, tenantId, unreadOnly, page, limit)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUnreadCount(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String headerTenantId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "userId", required = false) String paramUserId,
            @RequestParam(value = "tenantId", required = false) String paramTenantId) {

        String userId = resolveUserId(headerUserId, authHeader, paramUserId);
        String tenantId = resolveTenantId(headerTenantId, paramTenantId);

        long unreadCount = notificationService.getUnreadCount(userId, tenantId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("unreadCount", unreadCount, "userId", userId)));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markAsRead(
            @PathVariable String notificationId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "userId", required = false) String paramUserId) {

        String userId = resolveUserId(headerUserId, authHeader, paramUserId);
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read",
                notificationService.markAsRead(notificationId, userId)));
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markAllAsRead(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String headerTenantId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "userId", required = false) String paramUserId,
            @RequestParam(value = "tenantId", required = false) String paramTenantId) {

        String userId = resolveUserId(headerUserId, authHeader, paramUserId);
        String tenantId = resolveTenantId(headerTenantId, paramTenantId);

        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read",
                notificationService.markAllAsRead(userId, tenantId)));
    }
}
