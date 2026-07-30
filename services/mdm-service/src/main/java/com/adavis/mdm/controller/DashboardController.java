package com.adavis.mdm.controller;

import com.adavis.common.dto.ApiResponse;
import com.adavis.mdm.model.dto.DashboardUserTilesResponse;
import com.adavis.mdm.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mdm/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/user-tiles")
    public ResponseEntity<ApiResponse<DashboardUserTilesResponse>> getUserTiles(
            @RequestParam(required = false) String tenantId) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getUserTiles(tenantId)));
    }
}