package com.adavis.audit.service;

import com.adavis.audit.model.dto.AuditEvent;
import com.adavis.audit.model.entity.AuditLog;
import com.adavis.audit.repository.AuditLogRepository;
import com.adavis.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuditLogService auditLogService;

    private Pageable pageable;

    @BeforeEach
    void setUp() {
        pageable = PageRequest.of(0, 20, Sort.by("timestamp").descending());
    }

    @Test
    @DisplayName("logEvent should preserve username, tenantId, action, and metadata correctly")
    void testLogEvent_PreservesUsernameAndMetadataAndTenantId() {
        AuditEvent event = AuditEvent.builder()
                .userId("USR-001")
                .username("john_doe")
                .action("USER_CREATED")
                .entity("MDM_USER")
                .entityId("USR-001")
                .tenantId("TNT-0001")
                .status("SUCCESS")
                .metadata(Map.of("email", "john@example.com", "role", "OPERATOR"))
                .timestamp(Instant.now())
                .build();

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuditLog saved = auditLogService.logEvent(event);

        assertNotNull(saved);
        assertEquals("USR-001", saved.getUserId());
        assertEquals("john_doe", saved.getUsername());
        assertEquals("TNT-0001", saved.getTenantId());
        assertEquals("USER_CREATED", saved.getAction());
        assertEquals("SUCCESS", saved.getStatus());
        assertNotNull(saved.getMetadata());
        assertEquals("john@example.com", saved.getMetadata().get("email"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals("john_doe", captor.getValue().getUsername());
        assertEquals("TNT-0001", captor.getValue().getTenantId());
    }

    @Test
    @DisplayName("getAuditTrails with tenantId only should query by tenantId")
    void testGetAuditTrails_WithTenantIdOnly() {
        AuditLog log1 = AuditLog.builder().id("1").tenantId("TNT-0001").userId("USR-001").build();
        Page<AuditLog> expectedPage = new PageImpl<>(List.of(log1), pageable, 1);

        when(auditLogRepository.findByTenantIdOrderByTimestampDesc(eq("TNT-0001"), eq(pageable)))
                .thenReturn(expectedPage);

        Page<AuditLog> result = auditLogService.getAuditTrails("TNT-0001", null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("TNT-0001", result.getContent().get(0).getTenantId());
        verify(auditLogRepository).findByTenantIdOrderByTimestampDesc("TNT-0001", pageable);
        verify(auditLogRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("getAuditTrails with tenantId and userId should query by both")
    void testGetAuditTrails_WithTenantIdAndUserId() {
        AuditLog log1 = AuditLog.builder().id("1").tenantId("TNT-0001").userId("USR-001").build();
        Page<AuditLog> expectedPage = new PageImpl<>(List.of(log1), pageable, 1);

        when(auditLogRepository.findByTenantIdAndUserIdOrderByTimestampDesc(eq("TNT-0001"), eq("USR-001"), eq(pageable)))
                .thenReturn(expectedPage);

        Page<AuditLog> result = auditLogService.getAuditTrails("TNT-0001", "USR-001", pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(auditLogRepository).findByTenantIdAndUserIdOrderByTimestampDesc("TNT-0001", "USR-001", pageable);
    }

    @Test
    @DisplayName("getAuditTrails with userId only should query by userId")
    void testGetAuditTrails_WithUserIdOnly() {
        AuditLog log1 = AuditLog.builder().id("1").userId("USR-001").build();
        Page<AuditLog> expectedPage = new PageImpl<>(List.of(log1), pageable, 1);

        when(auditLogRepository.findByUserIdOrderByTimestampDesc(eq("USR-001"), eq(pageable)))
                .thenReturn(expectedPage);

        Page<AuditLog> result = auditLogService.getAuditTrails(null, "USR-001", pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(auditLogRepository).findByUserIdOrderByTimestampDesc("USR-001", pageable);
    }

    @Test
    @DisplayName("getAuditTrails with neither tenantId nor userId should query findAll")
    void testGetAuditTrails_Unscoped() {
        Page<AuditLog> expectedPage = new PageImpl<>(List.of(), pageable, 0);

        when(auditLogRepository.findAll(eq(pageable))).thenReturn(expectedPage);

        Page<AuditLog> result = auditLogService.getAuditTrails(null, null, pageable);

        assertNotNull(result);
        verify(auditLogRepository).findAll(pageable);
    }

    @Test
    @DisplayName("getAuditTrailsByTenant should throw exception if tenantId is blank")
    void testGetAuditTrailsByTenant_ThrowsWhenBlank() {
        assertThrows(BusinessException.class, () -> auditLogService.getAuditTrailsByTenant("", null, pageable));
        assertThrows(BusinessException.class, () -> auditLogService.getAuditTrailsByTenant(null, null, pageable));
    }

    @Test
    @DisplayName("getUserActivityTrend with tenantId should use tenant-scoped query")
    void testGetUserActivityTrend_WithTenantId() {
        AuditLog loginLog = AuditLog.builder()
                .id("1")
                .userId("SUPER_ADMIN")
                .username("super_admin")
                .action("LOGIN")
                .status("SUCCESS")
                .tenantId("TNT-0001")
                .timestamp(Instant.parse("2026-08-28T10:00:00Z"))
                .build();

        when(auditLogRepository.findByActionAndStatusAndTenantIdAndTimestampRangeOrderByTimestampAsc(
                eq("LOGIN"), eq("SUCCESS"), eq("TNT-0001"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(loginLog));

        var trend = auditLogService.getUserActivityTrend("quarterly", null, 3, 2026, "TNT-0001");

        assertNotNull(trend);
        assertEquals("quarterly", trend.getMode());
        assertEquals(2026, trend.getYear());
        assertEquals(3, trend.getQuarter());
        assertNotNull(trend.getWeeks());
        assertTrue(trend.getWeeks().stream().anyMatch(w -> w.getDistinctUserCount() == 1));

        verify(auditLogRepository).findByActionAndStatusAndTenantIdAndTimestampRangeOrderByTimestampAsc(
                eq("LOGIN"), eq("SUCCESS"), eq("TNT-0001"), any(Instant.class), any(Instant.class));
    }
}
