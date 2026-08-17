package com.adavis.mdm.controller;

import com.adavis.common.dto.ApiResponse;
import com.adavis.mdm.security.InternalRequestValidator;
import com.adavis.mdm.service.BulkUploadService;
import com.adavis.mdm.service.CsvTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/mdm/bulk")
@RequiredArgsConstructor
public class BulkUploadController {

    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";
    private final BulkUploadService bulkUploadService;
    private final CsvTemplateService csvTemplateService;
    private final InternalRequestValidator internalRequestValidator;

    @GetMapping("/template/{type}")
    public ResponseEntity<byte[]> downloadTemplate(
            @PathVariable String type,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth) {

        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String csvContent = csvTemplateService.generateTemplateCsv(type);
        byte[] bytes = csvContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + type.toLowerCase() + "_template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadCsv(
            @RequestParam("type") String type,
            @RequestParam(value = "mode", defaultValue = "UPDATE") String mode,
            @RequestParam(value = "tenantId", required = false) String tenantId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth) {

        internalRequestValidator.validateInternalGatewayRequest(internalAuth);

        try {
            Map<String, Object> result = bulkUploadService.processBulkUpload(
                    type,
                    mode,
                    tenantId,
                    userId != null ? userId : "SUPER_ADMIN",
                    file.getInputStream());

            boolean isSuccess = "SUCCESS".equals(result.get("status"));
            return ResponseEntity.ok(ApiResponse.success(
                    isSuccess ? "Bulk upload completed successfully" : "Bulk upload failed validation",
                    result));
        } catch (Exception e) {
            log.error("Bulk upload failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage(), "BULK_UPLOAD_ERROR"));
        }
    }
}
