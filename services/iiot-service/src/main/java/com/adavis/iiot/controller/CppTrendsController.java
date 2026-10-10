package com.adavis.iiot.controller;

import com.adavis.common.dto.ApiResponse;
import com.adavis.common.exception.BusinessException;
import com.adavis.iiot.service.CppTrendsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/iiot/reports/cpp-trends")
@RequiredArgsConstructor
public class CppTrendsController {
    private final CppTrendsService service;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTrends(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String plantId,
            @RequestParam(required = false) String equipmentId,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String parameter,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) Integer days) {
        try {
            return ResponseEntity.ok(ApiResponse.success(service.trends(new CppTrendsService.TrendQuery(
                    tenantId, plantId, equipmentId, productCode, parameter, fromDate, toDate, days))));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage(), "INVALID_CPP_TRENDS_QUERY");
        }
    }
}
