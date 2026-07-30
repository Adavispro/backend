package com.adavis.audit.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivityTrendResponse {

    private String mode;
    private Integer year;
    private Integer month;
    private Integer quarter;
    private String rangeStart;
    private String rangeEnd;
    private List<WeeklyBucket> weeks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklyBucket {
        private String weekStart;
        private String weekEnd;
        private long distinctUserCount;
        private List<UserSummary> users;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSummary {
        private String userId;
        private String username;
    }
}