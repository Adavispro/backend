package com.adavis.auth.service;

import com.adavis.auth.model.entity.Session;
import com.adavis.auth.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @InjectMocks
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sessionService, "sessionTimeoutMinutes", 30);
        ReflectionTestUtils.setField(sessionService, "idleThresholdMinutes", 10);
    }

    @Test
    void testGetSessionPresenceSummary_ActiveAndIdleClassification() {
        Instant now = Instant.now();
        Instant recentActivity = now.minusSeconds(2 * 60); // 2 minutes ago -> Active
        Instant idleActivity = now.minusSeconds(15 * 60);  // 15 minutes ago -> Idle

        Session activeUserSession = Session.builder()
                .sessionId("sess-1")
                .userId("USR-001")
                .tenantId("TNT-0001")
                .lastActivity(recentActivity)
                .expiresAt(now.plusSeconds(1800))
                .isActive(true)
                .build();

        Session idleUserSession = Session.builder()
                .sessionId("sess-2")
                .userId("USR-002")
                .tenantId("TNT-0001")
                .lastActivity(idleActivity)
                .expiresAt(now.plusSeconds(1800))
                .isActive(true)
                .build();

        when(sessionRepository.findByTenantIdAndIsActiveTrue("TNT-0001"))
                .thenReturn(List.of(activeUserSession, idleUserSession));

        Map<String, Object> summary = sessionService.getSessionPresenceSummary("TNT-0001");

        assertNotNull(summary);
        assertEquals("TNT-0001", summary.get("tenantId"));
        assertEquals(1L, summary.get("activeUsersCount"));
        assertEquals(1L, summary.get("idleUsersCount"));
        assertEquals(2L, summary.get("totalOnlineUsersCount"));

        @SuppressWarnings("unchecked")
        List<String> activeUserIds = (List<String>) summary.get("activeUserIds");
        @SuppressWarnings("unchecked")
        List<String> idleUserIds = (List<String>) summary.get("idleUserIds");

        assertTrue(activeUserIds.contains("USR-001"));
        assertTrue(idleUserIds.contains("USR-002"));
    }

    @Test
    void testGetSessionPresenceSummary_EmptySessions() {
        when(sessionRepository.findByTenantIdAndIsActiveTrue("TNT-9999"))
                .thenReturn(List.of());

        Map<String, Object> summary = sessionService.getSessionPresenceSummary("TNT-9999");

        assertNotNull(summary);
        assertEquals(0L, summary.get("activeUsersCount"));
        assertEquals(0L, summary.get("idleUsersCount"));
        assertEquals(0L, summary.get("totalOnlineUsersCount"));
    }

    @Test
    void testGetSessionPresenceSummary_MultipleSessionsSameUser_ActiveTakesPrecedence() {
        Instant now = Instant.now();
        Instant activeActivity = now.minusSeconds(2 * 60);  // 2 min ago -> Active
        Instant idleActivity = now.minusSeconds(20 * 60);  // 20 min ago -> Idle

        // User A has 2 sessions: 1 active and 1 idle
        Session session1 = Session.builder()
                .sessionId("sess-1a")
                .userId("USR-A")
                .tenantId("TNT-0001")
                .lastActivity(activeActivity)
                .expiresAt(now.plusSeconds(1800))
                .isActive(true)
                .build();

        Session session2 = Session.builder()
                .sessionId("sess-1b")
                .userId("USR-A")
                .tenantId("TNT-0001")
                .lastActivity(idleActivity)
                .expiresAt(now.plusSeconds(1800))
                .isActive(true)
                .build();

        when(sessionRepository.findByTenantIdAndIsActiveTrue("TNT-0001"))
                .thenReturn(List.of(session1, session2));

        Map<String, Object> summary = sessionService.getSessionPresenceSummary("TNT-0001");

        assertNotNull(summary);
        assertEquals(1L, summary.get("activeUsersCount"));
        assertEquals(0L, summary.get("idleUsersCount"));
        assertEquals(1L, summary.get("totalOnlineUsersCount"));

        @SuppressWarnings("unchecked")
        List<String> activeUserIds = (List<String>) summary.get("activeUserIds");
        assertTrue(activeUserIds.contains("USR-A"));
    }

    @Test
    void testGetSessionPresenceSummary_MultipleSessionsSameUser_BothIdleDeduplicated() {
        Instant now = Instant.now();
        Instant idleActivity1 = now.minusSeconds(15 * 60);
        Instant idleActivity2 = now.minusSeconds(25 * 60);

        // User B has 2 idle sessions
        Session session1 = Session.builder()
                .sessionId("sess-2a")
                .userId("USR-B")
                .tenantId("TNT-0001")
                .lastActivity(idleActivity1)
                .expiresAt(now.plusSeconds(1800))
                .isActive(true)
                .build();

        Session session2 = Session.builder()
                .sessionId("sess-2b")
                .userId("USR-B")
                .tenantId("TNT-0001")
                .lastActivity(idleActivity2)
                .expiresAt(now.plusSeconds(1800))
                .isActive(true)
                .build();

        when(sessionRepository.findByTenantIdAndIsActiveTrue("TNT-0001"))
                .thenReturn(List.of(session1, session2));

        Map<String, Object> summary = sessionService.getSessionPresenceSummary("TNT-0001");

        assertNotNull(summary);
        assertEquals(0L, summary.get("activeUsersCount"));
        assertEquals(1L, summary.get("idleUsersCount"));
        assertEquals(1L, summary.get("totalOnlineUsersCount"));

        @SuppressWarnings("unchecked")
        List<String> idleUserIds = (List<String>) summary.get("idleUserIds");
        assertTrue(idleUserIds.contains("USR-B"));
    }

    @Test
    void testGetSessionPresenceSummary_IdleThresholdBoundary() {
        Instant now = Instant.now();
        // 9 minutes ago -> Within 10-minute threshold -> Active
        Instant activeActivity = now.minusSeconds(9 * 60);
        // 11 minutes ago -> Beyond 10-minute threshold -> Idle
        Instant idleActivity = now.minusSeconds(11 * 60);

        Session activeSession = Session.builder()
                .sessionId("sess-act")
                .userId("USR-ACT")
                .tenantId("TNT-0001")
                .lastActivity(activeActivity)
                .expiresAt(now.plusSeconds(1800))
                .isActive(true)
                .build();

        Session idleSession = Session.builder()
                .sessionId("sess-idle")
                .userId("USR-IDLE")
                .tenantId("TNT-0001")
                .lastActivity(idleActivity)
                .expiresAt(now.plusSeconds(1800))
                .isActive(true)
                .build();

        when(sessionRepository.findByTenantIdAndIsActiveTrue("TNT-0001"))
                .thenReturn(List.of(activeSession, idleSession));

        Map<String, Object> summary = sessionService.getSessionPresenceSummary("TNT-0001");

        assertNotNull(summary);
        assertEquals(1L, summary.get("activeUsersCount"));
        assertEquals(1L, summary.get("idleUsersCount"));
        assertEquals(2L, summary.get("totalOnlineUsersCount"));
    }

    @Test
    void testExpireSessions_MarksExpiredSessionsInactive() {
        Instant now = Instant.now();
        Session expiredSession = Session.builder()
                .sessionId("sess-exp")
                .userId("USR-EXP")
                .tenantId("TNT-0001")
                .lastActivity(now.minusSeconds(3600))
                .expiresAt(now.minusSeconds(60)) // Expired 1 min ago
                .isActive(true)
                .build();

        when(sessionRepository.findByIsActiveTrueAndExpiresAtBefore(any(Instant.class)))
                .thenReturn(List.of(expiredSession));
        when(sessionRepository.saveAll(anyList()))
                .thenReturn(List.of(expiredSession));

        sessionService.expireSessions();

        assertFalse(expiredSession.getIsActive());
        verify(sessionRepository, times(1)).saveAll(anyList());
    }
}
