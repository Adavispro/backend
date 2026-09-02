package com.adavis.mdm.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardUserTilesResponse {

    private String tenantId;
    private long totalUsersCount;
    private long activeUsersCount;
    private long idleUsersCount;
    private long totalOnlineUsersCount;
    private long configuredRolesCount;
    private long configuredGroupsCount;
    private String idleThresholdMinutes;
    private String asOf;
}