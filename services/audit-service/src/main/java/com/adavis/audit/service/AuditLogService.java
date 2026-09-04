package com.adavis.audit.service;

import com.adavis.audit.model.dto.AuditEvent;
import com.adavis.audit.model.dto.UserActivityTrendResponse;
import com.adavis.audit.model.entity.AuditLog;
import com.adavis.audit.repository.AuditLogRepository;
import com.adavis.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final Set<String> SESSION_METADATA_KEYS = Set.of(
        "tenantId", "ipAddress", "userAgent", "isActive", "deviceInfo"
    );

    private static final Set<String> SENSITIVE_KEY_HINTS = Set.of(
        "token", "password", "secret", "credential", "authorization"
    );

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public AuditLog logEvent(AuditEvent event) {
        log.info("Logging audit event: action={}, user={}, entity={}", 
                event.getAction(), event.getUserId(), event.getEntity());

        Map<String, Object> metadata = event.getMetadata() == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(event.getMetadata());

        String tenantId = firstNonBlank(event.getTenantId(), asString(metadata.remove("tenantId")));
        String ipAddress = firstNonBlank(event.getIpAddress(), asString(metadata.remove("ipAddress")));
        String sessionId = firstNonBlank(event.getSessionId(), asString(metadata.remove("sessionId")));
        String userAgent = firstNonBlank(event.getUserAgent(), asString(metadata.remove("userAgent")));
        String username = firstNonBlank(event.getUsername(), asString(metadata.remove("username")));
        if (username == null || username.isBlank()) {
            username = event.getUserId();
        }

        metadata = sanitizeMetadata(event.getEntity(), metadata);

        AuditLog auditLog = AuditLog.builder()
                .id(UUID.randomUUID().toString())
                .userId(event.getUserId())
                .username(username)
                .action(event.getAction())
                .entity(event.getEntity())
                .entityId(event.getEntityId())
                .before(event.getBefore())
                .after(event.getAfter())
                .metadata(metadata.isEmpty() ? null : metadata)
                .ipAddress(ipAddress)
                .sessionId(sessionId)
                .userAgent(userAgent)
                .tenantId(tenantId)
                .status(event.getStatus() != null ? event.getStatus() : "SUCCESS")
                .failureReason(event.getFailureReason())
                .timestamp(event.getTimestamp() != null ? event.getTimestamp() : Instant.now())
                .version(1)
                .build();

        return auditLogRepository.save(auditLog);
    }

    public AuditLog getAuditLog(String id) {
        return auditLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Audit log not found: " + id));
    }

    public List<AuditLog> getAuditLogsByEntity(String entity, String entityId) {
        return auditLogRepository.findByEntityAndEntityIdOrderByTimestampDesc(entity, entityId);
    }

    public Page<AuditLog> getAuditLogsByUser(String userId, Pageable pageable) {
        return auditLogRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
    }

    public Page<AuditLog> getAuditTrailsForUserOrEntity(String tenantId, String userId, Pageable pageable) {
        if (tenantId != null && !tenantId.isBlank()) {
            return auditLogRepository.findByTenantIdAndUserIdOrEntityIdOrderByTimestampDesc(tenantId.trim(), userId.trim(), pageable);
        }
        return auditLogRepository.findByUserIdOrEntityIdOrderByTimestampDesc(userId.trim(), pageable);
    }

    public Page<AuditLog> getAuditTrails(String userId, Pageable pageable) {
        return getAuditTrails(null, userId, pageable);
    }

    public Page<AuditLog> getAuditTrails(String tenantId, String userId, Pageable pageable) {
        boolean hasTenant = tenantId != null && !tenantId.isBlank();
        boolean hasUser = userId != null && !userId.isBlank();

        if (hasTenant && hasUser) {
            return auditLogRepository.findByTenantIdAndUserIdOrderByTimestampDesc(tenantId.trim(), userId.trim(), pageable);
        } else if (hasTenant) {
            return auditLogRepository.findByTenantIdOrderByTimestampDesc(tenantId.trim(), pageable);
        } else if (hasUser) {
            return auditLogRepository.findByUserIdOrderByTimestampDesc(userId.trim(), pageable);
        }
        return auditLogRepository.findAll(pageable);
    }

    public Page<AuditLog> getAuditTrailsByTenant(String tenantId, String userId, Pageable pageable) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException("tenantId is required", "VALIDATION_ERROR");
        }
        return getAuditTrails(tenantId, userId, pageable);
    }

    public Page<AuditLog> getAuditLogsByAction(String action, Instant from, Instant to, Pageable pageable) {
        return auditLogRepository.findByActionAndTimestampBetween(action, from, to, pageable);
    }

    public List<AuditLog> getAuditLogsByTenant(String tenantId, Instant from, Instant to) {
        return auditLogRepository.findByTenantIdAndTimestampBetween(tenantId, from, to);
    }

    public long countActionsByDateRange(String action, Instant from, Instant to) {
        return auditLogRepository.countByActionAndTimestampBetween(action, from, to);
    }

    @Transactional(readOnly = true)
    public UserActivityTrendResponse getUserActivityTrend(String mode, Integer month, Integer quarter, Integer year) {
        return getUserActivityTrend(mode, month, quarter, year, null);
    }

    @Transactional(readOnly = true)
    public UserActivityTrendResponse getUserActivityTrend(String mode, Integer month, Integer quarter, Integer year, String tenantId) {
        if (mode == null || mode.isBlank()) {
            throw new BusinessException("mode is required", "VALIDATION_ERROR");
        }
        if (year == null) {
            throw new BusinessException("year is required", "VALIDATION_ERROR");
        }

        String normalizedMode = mode.trim().toLowerCase();
        LocalDate periodStart;
        LocalDate periodEnd;

        switch (normalizedMode) {
            case "monthly" -> {
                if (month == null) {
                    throw new BusinessException("month is required for monthly mode", "VALIDATION_ERROR");
                }
                YearMonth selectedMonth = YearMonth.of(year, month);
                periodStart = selectedMonth.atDay(1);
                periodEnd = selectedMonth.atEndOfMonth();
            }
            case "quarterly" -> {
                if (quarter == null) {
                    throw new BusinessException("quarter is required for quarterly mode", "VALIDATION_ERROR");
                }
                if (quarter < 1 || quarter > 4) {
                    throw new BusinessException("quarter must be between 1 and 4", "VALIDATION_ERROR");
                }
                int startMonth = ((quarter - 1) * 3) + 1;
                periodStart = LocalDate.of(year, startMonth, 1);
                periodEnd = YearMonth.of(year, startMonth + 2).atEndOfMonth();
            }
            default -> throw new BusinessException("Unsupported mode: " + mode, "VALIDATION_ERROR");
        }

        Instant rangeStart = periodStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant rangeEndExclusive = periodEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<AuditLog> loginEvents = (tenantId != null && !tenantId.isBlank())
                ? auditLogRepository.findByActionAndStatusAndTenantIdAndTimestampRangeOrderByTimestampAsc("LOGIN", "SUCCESS", tenantId, rangeStart, rangeEndExclusive)
                : auditLogRepository.findByActionAndStatusAndTimestampRangeOrderByTimestampAsc("LOGIN", "SUCCESS", rangeStart, rangeEndExclusive);

        List<WeeklyBucketAccumulator> buckets = buildWeeklyBuckets(periodStart, periodEnd);
        for (AuditLog loginEvent : loginEvents) {
            Instant timestamp = loginEvent.getTimestamp();
            if (timestamp == null) {
                continue;
            }

            LocalDate eventDate = timestamp.atZone(ZoneOffset.UTC).toLocalDate();
            if (eventDate.isBefore(periodStart) || eventDate.isAfter(periodEnd)) {
                continue;
            }

            int bucketIndex = (int) (ChronoUnit.DAYS.between(periodStart, eventDate) / 7);
            if (bucketIndex < 0 || bucketIndex >= buckets.size()) {
                continue;
            }

            buckets.get(bucketIndex).addLogin(loginEvent.getUserId(), loginEvent.getUsername());
        }

        return UserActivityTrendResponse.builder()
                .mode(normalizedMode)
                .year(year)
                .month(month)
                .quarter(quarter)
                .rangeStart(periodStart.toString())
                .rangeEnd(periodEnd.toString())
                .weeks(buckets.stream().map(WeeklyBucketAccumulator::toDto).toList())
                .build();
    }

    @Transactional
    public AuditLog logUserAction(String userId, String username, String action, 
                                   String entity, String entityId, 
                                   Map<String, Object> before, Map<String, Object> after,
                                   String ipAddress, String sessionId) {
        AuditEvent event = AuditEvent.builder()
                .userId(userId)
                .username(username)
                .action(action)
                .entity(entity)
                .entityId(entityId)
                .before(before)
                .after(after)
                .ipAddress(ipAddress)
                .sessionId(sessionId)
                .status("SUCCESS")
                .timestamp(Instant.now())
                .build();

        return logEvent(event);
    }

    @Transactional
    public AuditLog logFailure(String userId, String username, String action, 
                                String entity, String entityId, 
                                String failureReason, String ipAddress, String sessionId) {
        AuditEvent event = AuditEvent.builder()
                .userId(userId)
                .username(username)
                .action(action)
                .entity(entity)
                .entityId(entityId)
                .ipAddress(ipAddress)
                .sessionId(sessionId)
                .status("FAILURE")
                .failureReason(failureReason)
                .timestamp(Instant.now())
                .build();

        return logEvent(event);
    }

    private String asString(Object value) {
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

    private Map<String, Object> sanitizeMetadata(String entity, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return metadata;
        }

        Map<String, Object> sanitized = new LinkedHashMap<>();
        Set<String> allowList = "AUTH_SESSION".equalsIgnoreCase(entity)
                ? new HashSet<>(SESSION_METADATA_KEYS)
                : null;

        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }

            String normalized = key.toLowerCase();
            boolean sensitive = SENSITIVE_KEY_HINTS.stream().anyMatch(normalized::contains);
            if (sensitive) {
                continue;
            }

            if (allowList != null && !allowList.contains(key)) {
                continue;
            }

            sanitized.put(key, entry.getValue());
        }

        return sanitized;
    }

    private List<WeeklyBucketAccumulator> buildWeeklyBuckets(LocalDate periodStart, LocalDate periodEnd) {
        List<WeeklyBucketAccumulator> buckets = new ArrayList<>();
        LocalDate cursor = periodStart;

        while (!cursor.isAfter(periodEnd)) {
            LocalDate weekEnd = cursor.plusDays(6);
            if (weekEnd.isAfter(periodEnd)) {
                weekEnd = periodEnd;
            }
            buckets.add(new WeeklyBucketAccumulator(cursor, weekEnd));
            cursor = weekEnd.plusDays(1);
        }

        return buckets;
    }

    private static class WeeklyBucketAccumulator {
        private final LocalDate weekStart;
        private final LocalDate weekEnd;
        private final Map<String, UserActivityTrendResponse.UserSummary> users = new LinkedHashMap<>();
        private long loginCount = 0;

        private WeeklyBucketAccumulator(LocalDate weekStart, LocalDate weekEnd) {
            this.weekStart = weekStart;
            this.weekEnd = weekEnd;
        }

        private void addLogin(String userId, String username) {
            loginCount++;
            if (userId == null || userId.isBlank()) {
                return;
            }

            users.compute(userId, (key, existing) -> {
                if (existing == null) {
                    return UserActivityTrendResponse.UserSummary.builder()
                            .userId(userId)
                            .username(username)
                            .build();
                }
                if ((existing.getUsername() == null || existing.getUsername().isBlank()) && username != null && !username.isBlank()) {
                    existing.setUsername(username);
                }
                return existing;
            });
        }

        private UserActivityTrendResponse.WeeklyBucket toDto() {
            return UserActivityTrendResponse.WeeklyBucket.builder()
                    .weekStart(weekStart.toString())
                    .weekEnd(weekEnd.toString())
                    .distinctUserCount(users.size())
                    .loginCount(loginCount)
                    .users(new ArrayList<>(users.values()))
                    .build();
        }
    }
}