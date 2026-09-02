package com.adavis.mdm.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponse {
    private DashboardUserTilesResponse userTiles;
    private List<RoleUserCountDto> usersByRole;
    private List<GroupActivityDto> teamActivity;
    private List<Map<String, Object>> recentUsers;
}
