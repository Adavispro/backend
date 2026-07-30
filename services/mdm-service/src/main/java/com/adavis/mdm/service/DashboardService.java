package com.adavis.mdm.service;

import com.adavis.common.exception.BusinessException;
import com.adavis.mdm.model.dto.DashboardUserTilesResponse;
import com.adavis.mdm.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserProfileRepository userProfileRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${services.auth.base-url:http://auth-service:9081}")
    private String authServiceBaseUrl;

    @Transactional(readOnly = true)
    public DashboardUserTilesResponse getUserTiles(String tenantId) {
        long totalUsersCount = StringUtils.hasText(tenantId)
                ? userProfileRepository.countByTenantIdAndIsActiveTrueAndIsBlockedFalse(tenantId)
                : userProfileRepository.countByIsActiveTrueAndIsBlockedFalse();

        Map<String, Object> presenceSummary = fetchPresenceSummary(tenantId);

        long activeUsersCount = toLong(presenceSummary.get("activeUsersCount"));
        long idleUsersCount = toLong(presenceSummary.get("idleUsersCount"));
        long totalOnlineUsersCount = toLong(presenceSummary.get("totalOnlineUsersCount"));

        return DashboardUserTilesResponse.builder()
                .tenantId(tenantId)
                .totalUsersCount(totalUsersCount)
                .activeUsersCount(activeUsersCount)
                .idleUsersCount(idleUsersCount)
                .totalOnlineUsersCount(totalOnlineUsersCount)
                .idleThresholdMinutes(String.valueOf(presenceSummary.getOrDefault("idleThresholdMinutes", "10")))
                .asOf(String.valueOf(presenceSummary.getOrDefault("asOf", "")))
                .build();
    }

    private Map<String, Object> fetchPresenceSummary(String tenantId) {
        String url = authServiceBaseUrl + "/internal/v1/auth/users/session-presence-summary";
        if (StringUtils.hasText(tenantId)) {
            url += "?tenantId=" + tenantId;
        }

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Map.of();
            }

            Object data = response.getBody().get("data");
            if (data instanceof Map<?, ?> payload) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : payload.entrySet()) {
                    if (entry.getKey() != null) {
                        result.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                return result;
            }
        } catch (RestClientException ex) {
            log.warn("Failed to fetch session presence summary for tenantId {}: {}", tenantId, ex.getMessage());
        }

        throw new BusinessException("Unable to fetch user session presence summary", "SESSION_PRESENCE_UNAVAILABLE");
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0L;
    }
}