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
}
