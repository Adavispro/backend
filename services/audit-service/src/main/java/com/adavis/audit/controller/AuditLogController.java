package com.adavis.audit.controller;

import com.adavis.audit.model.dto.UserActivityTrendResponse;
import com.adavis.audit.model.entity.AuditLog;
import com.adavis.audit.service.AuditLogService;
import com.adavis.common.dto.ApiResponse;
import com.adavis.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping("/trails")
    public ApiResponse<PageResponse<AuditLog>> getAuditTrails(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<AuditLog> auditLogs = auditLogService.getAuditTrails(tenantId, userId, pageable);
        return ApiResponse.success(PageResponse.from(auditLogs));
    }

    @GetMapping({"/trails/tenant/{tenantId}", "/logs/tenant/{tenantId}"})
    public ApiResponse<PageResponse<AuditLog>> getAuditTrailsByTenant(
            @PathVariable String tenantId,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<AuditLog> auditLogs = auditLogService.getAuditTrailsByTenant(tenantId, userId, pageable);
        return ApiResponse.success(PageResponse.from(auditLogs));
    }

    @GetMapping("/login-history")
    public ApiResponse<PageResponse<AuditLog>> getLoginHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Instant effectiveTo = to == null ? Instant.now() : to;
        Instant effectiveFrom = from == null ? effectiveTo.minusSeconds(30L * 24L * 60L * 60L) : from;
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<AuditLog> loginHistory = auditLogService.getAuditLogsByAction("LOGIN", effectiveFrom, effectiveTo, pageable);
        return ApiResponse.success(PageResponse.from(loginHistory));
    }

    @GetMapping("/user-activity-trend")
    public ApiResponse<UserActivityTrendResponse> getUserActivityTrend(
            @RequestParam String mode,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer quarter,
            @RequestParam Integer year,
            @RequestParam(required = false) String tenantId) {
        return ApiResponse.success(auditLogService.getUserActivityTrend(mode, month, quarter, year, tenantId));
    }
}