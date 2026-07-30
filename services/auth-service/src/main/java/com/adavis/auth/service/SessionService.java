package com.adavis.auth.service;

import com.adavis.auth.model.entity.Session;
import com.adavis.auth.repository.SessionRepository;
import com.adavis.auth.model.entity.User;
import com.adavis.dto.auth.response.SessionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SessionService {

    private final SessionRepository sessionRepository;
    private final AuditEventPublisher auditEventPublisher;

    @Value("${session.timeout-minutes:30}")
    private int sessionTimeoutMinutes;

    @Value("${session.idle-threshold-minutes:10}")
    private int idleThresholdMinutes;

    // ✅ This method exists and is used by AuthService
    public Session createSession(String userId, String tenantId, String refreshToken, String deviceInfo, String ipAddress) {
        String sessionId = UUID.randomUUID().toString();
        Session session = Session.builder()
            .id(sessionId)
            .sessionId(sessionId)
                .tenantId(tenantId)
                .userId(userId)
                .refreshToken(refreshToken)
                .deviceInfo(deviceInfo != null ? deviceInfo : "Unknown")
                .ipAddress(ipAddress != null ? ipAddress : "Unknown")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(sessionTimeoutMinutes * 60L))
                .lastActivity(Instant.now())
                .isActive(true)
                .build();

        Session saved = sessionRepository.save(session);
        publishSessionAudit("SESSION_CREATED", saved);
        log.info("Created session for user: {}", userId);
        return saved;
    }

    public Session createOrExtendSession(String userId,
                                         String tenantId,
                                         String refreshToken,
                                         String deviceInfo,
                                         String ipAddress) {
        expireSessions();

        if (ipAddress != null && !ipAddress.isBlank()) {
            List<Session> activeFromSameIp = sessionRepository.findByUserIdAndIpAddressAndIsActiveTrue(userId, ipAddress);
            if (!activeFromSameIp.isEmpty()) {
                Session existing = activeFromSameIp.stream()
                        .max(Comparator.comparing(Session::getLastActivity, Comparator.nullsLast(Comparator.naturalOrder())))
                        .orElse(activeFromSameIp.get(0));
                existing.setRefreshToken(refreshToken);
                existing.setDeviceInfo(deviceInfo != null ? deviceInfo : existing.getDeviceInfo());
                existing.setLastActivity(Instant.now());
                existing.setExpiresAt(Instant.now().plusSeconds(sessionTimeoutMinutes * 60L));
                existing.setIsActive(true);
                Session saved = sessionRepository.save(existing);
                publishSessionAudit("SESSION_EXTENDED", saved);
                log.info("Extended existing session for user {} from ip {} with sessionId {}", userId, ipAddress, saved.getSessionId());
                return saved;
            }
        }

        return createSession(userId, tenantId, refreshToken, deviceInfo, ipAddress);
    }

    public List<SessionResponse> getActiveSessions(String userId) {
        expireSessions();
        List<Session> sessions = sessionRepository.findByUserIdAndIsActiveTrue(userId);
        return sessions.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSessionPresenceSummary(String tenantId) {
        expireSessions();

        List<Session> sessions = (tenantId != null && !tenantId.isBlank())
                ? sessionRepository.findByTenantIdAndIsActiveTrue(tenantId)
                : sessionRepository.findByIsActiveTrue();

        Instant idleCutoff = Instant.now().minusSeconds(Math.max(1, idleThresholdMinutes) * 60L);

        Map<String, Session> latestSessionByUser = new LinkedHashMap<>();
        for (Session session : sessions) {
            if (session == null || session.getUserId() == null || session.getUserId().isBlank()) {
                continue;
            }

            Session existing = latestSessionByUser.get(session.getUserId());
            if (existing == null) {
                latestSessionByUser.put(session.getUserId(), session);
                continue;
            }

            Instant existingActivity = existing.getLastActivity();
            Instant currentActivity = session.getLastActivity();
            if (existingActivity == null || (currentActivity != null && currentActivity.isAfter(existingActivity))) {
                latestSessionByUser.put(session.getUserId(), session);
            }
        }

        long activeUsersCount = latestSessionByUser.values().stream()
                .filter(session -> session.getLastActivity() != null && !session.getLastActivity().isBefore(idleCutoff))
                .count();

        long idleUsersCount = latestSessionByUser.size() - activeUsersCount;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tenantId", tenantId);
        response.put("idleThresholdMinutes", idleThresholdMinutes);
        response.put("activeUsersCount", activeUsersCount);
        response.put("idleUsersCount", idleUsersCount);
        response.put("totalOnlineUsersCount", latestSessionByUser.size());
        response.put("asOf", Instant.now().toString());
        return response;
    }

    public void terminateSession(String sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setIsActive(false);
            Session saved = sessionRepository.save(session);
            publishSessionAudit("SESSION_TERMINATED", saved);
            log.info("Terminated session: {}", sessionId);
        });
    }

    public void terminateAllSessions(String userId) {
        List<Session> sessions = sessionRepository.findByUserIdAndIsActiveTrue(userId);
        sessions.forEach(session -> {
            session.setIsActive(false);
            Session saved = sessionRepository.save(session);
            publishSessionAudit("SESSION_TERMINATED", saved);
        });
        log.info("Terminated all sessions for user: {}", userId);
    }

    public void terminateLatestActiveSession(String userId, String ipAddress, String deviceInfo) {
        expireSessions();

        List<Session> activeSessions = sessionRepository.findByUserIdAndIsActiveTrue(userId);
        if (activeSessions.isEmpty()) {
            return;
        }

        Session target = activeSessions.stream()
                .filter(session -> ipAddress != null && ipAddress.equals(session.getIpAddress()))
                .max(Comparator.comparing(Session::getLastActivity, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseGet(() -> activeSessions.stream()
                        .max(Comparator.comparing(Session::getLastActivity, Comparator.nullsLast(Comparator.naturalOrder())))
                        .orElse(activeSessions.get(0)));

        target.setIsActive(false);
        Session saved = sessionRepository.save(target);
        publishSessionAudit("SESSION_TERMINATED", saved);
        log.info("Terminated session {} for user {} (ip={}, device={})", saved.getSessionId(), userId, ipAddress, deviceInfo);
    }

    public void terminateSessionByRefreshToken(String refreshToken) {
        sessionRepository.findByRefreshToken(refreshToken).ifPresent(session -> {
            session.setIsActive(false);
            Session saved = sessionRepository.save(session);
            publishSessionAudit("SESSION_TERMINATED", saved);
            log.info("Terminated session by refresh token for user: {}", session.getUserId());
        });
    }

    @Scheduled(fixedDelayString = "${session.expiry-scan-interval-ms:60000}")
    public void expireSessions() {
        Instant now = Instant.now();
        List<Session> expiredSessions = sessionRepository.findByIsActiveTrueAndExpiresAtBefore(now);
        if (expiredSessions.isEmpty()) {
            return;
        }

        expiredSessions.forEach(session -> session.setIsActive(false));
        List<Session> savedSessions = sessionRepository.saveAll(expiredSessions);
        savedSessions.forEach(session -> publishSessionAudit("SESSION_EXPIRED", session));
        log.info("Marked {} expired sessions inactive", expiredSessions.size());
    }

    private void publishSessionAudit(String action, Session session) {
        auditEventPublisher.publish(
                session.getUserId(),
                session.getUserId(),
                action,
                "AUTH_SESSION",
                session.getSessionId(),
                "SUCCESS",
                null,
                java.util.Map.of(
                        "tenantId", session.getTenantId() == null ? "" : session.getTenantId(),
                        "ipAddress", session.getIpAddress() == null ? "" : session.getIpAddress(),
                "deviceInfo", session.getDeviceInfo() == null ? "" : session.getDeviceInfo(),
                        "isActive", Boolean.TRUE.equals(session.getIsActive())
                )
        );
    }

    private SessionResponse toResponse(Session session) {
        return SessionResponse.builder()
                .sessionId(session.getSessionId())
                .userId(session.getUserId())
                .deviceInfo(session.getDeviceInfo())
                .ipAddress(session.getIpAddress())
                .createdAt(session.getCreatedAt())
                .expiresAt(session.getExpiresAt())
                .lastActivity(session.getLastActivity())
                .isActive(session.getIsActive())
                .build();
    }
}