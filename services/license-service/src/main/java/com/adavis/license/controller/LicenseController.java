package com.adavis.license.controller;

import com.adavis.common.dto.ApiResponse;
import com.adavis.dto.license.request.ApplyLicenseRequest;
import com.adavis.dto.license.response.LicenseResponse;
import com.adavis.license.model.entity.LicenseHistory;
import com.adavis.license.service.LicenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mdm/license")
@RequiredArgsConstructor
public class LicenseController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final LicenseService licenseService;

    @PostMapping("/tenant")
    public ApiResponse<LicenseResponse> activateLicense(
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @Valid @RequestBody ApplyLicenseRequest request) {
        request.setPerformedBy(firstNonBlank(request.getPerformedBy(), currentUserId));
        LicenseResponse response = licenseService.applyLicense(request);
        return ApiResponse.success("License action applied successfully", response);
    }

    @GetMapping("/tenant/{tenantId}")
    public ApiResponse<LicenseResponse> getTenantLicense(@PathVariable String tenantId) {
        return ApiResponse.success(licenseService.getActiveLicenseByTenantId(tenantId));
    }

    @PutMapping("/{licenseId}/upgrade")
    public ApiResponse<LicenseResponse> upgradeLicense(
            @PathVariable String licenseId,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.success(
                "License action applied successfully",
                licenseService.upgradeLicenseById(
                        licenseId,
                    toText(request.get("encryptedLicenseToken")),
                        toStringList(request.get("modules")),
                        toInteger(request.get("maxUsers")),
                        toText(request.get("reason")),
                        firstNonBlank(toText(request.get("upgradedBy")), currentUserId)));
    }

                @PutMapping("/tenant/{tenantId}/upgrade")
                public ApiResponse<LicenseResponse> upgradeLicenseByTenant(
                    @PathVariable String tenantId,
                    @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
                    @RequestBody Map<String, Object> request) {
                return ApiResponse.success(
                    "License action applied successfully",
                    licenseService.upgradeLicenseByTenantId(
                        tenantId,
                        toText(request.get("encryptedLicenseToken")),
                        toStringList(request.get("modules")),
                        toInteger(request.get("maxUsers")),
                        toText(request.get("reason")),
                        firstNonBlank(toText(request.get("upgradedBy")), currentUserId)));
                }

                @PostMapping("/tenant/{tenantId}/renew")
                public ApiResponse<LicenseResponse> renewLicenseByTenant(
                    @PathVariable String tenantId,
                    @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
                    @RequestBody(required = false) Map<String, Object> request) {
                Map<String, Object> safeReq = request != null ? request : Map.of();
                return ApiResponse.success(
                    "License renewed successfully",
                    licenseService.renewTenantLicense(
                        tenantId,
                        toText(safeReq.get("encryptedLicenseToken")),
                        toInteger(safeReq.get("validityYears")),
                        toText(safeReq.get("planId")),
                        toStringList(safeReq.get("modules")),
                        toInteger(safeReq.get("maxUsers")),
                        toText(safeReq.get("reason")),
                        firstNonBlank(toText(safeReq.get("performedBy")), currentUserId)));
                }

    @GetMapping("/tenant/{tenantId}/history")
    public ApiResponse<List<LicenseHistory>> getTenantLicenseHistory(@PathVariable String tenantId) {
        return ApiResponse.success(licenseService.getLicenseHistoryByTenantId(tenantId));
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return null;
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return null;
    }

    private String toText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }
}