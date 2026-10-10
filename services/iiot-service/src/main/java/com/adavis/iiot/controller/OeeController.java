package com.adavis.iiot.controller;

import com.adavis.common.dto.ApiResponse;
import com.adavis.common.exception.BusinessException;
import com.adavis.iiot.service.OeeCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/iiot/reports/oee")
@RequiredArgsConstructor
public class OeeController {
    private final OeeCalculationService service;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMetrics(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String plantId,
            @RequestParam(required = false) String equipmentId,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String shift,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) Integer days,
            @RequestParam(defaultValue = "false") boolean refresh) {
        try {
            return ResponseEntity.ok(ApiResponse.success(service.metrics(new OeeCalculationService.Query(
                    tenantId, plantId, equipmentId, productCode, shift, fromDate, toDate, days, refresh))));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage(), "INVALID_OEE_QUERY");
        }
    }
}
