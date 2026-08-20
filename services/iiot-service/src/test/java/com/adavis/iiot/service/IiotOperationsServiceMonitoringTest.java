package com.adavis.iiot.service;

import com.adavis.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class IiotOperationsServiceMonitoringTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private BatchPdfGeneratorService batchPdfGeneratorService;

    private IiotOperationsService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new IiotOperationsService(mongoTemplate, stringRedisTemplate, new ObjectMapper(), batchPdfGeneratorService);
    }

    @Test
    void getEquipmentMonitoringViewRequiresEquipmentAndBatch() {
        Map<String, Object> filter = Map.of(
                "equipmentId", "RMG-100L-2-PVII",
                "batchNo", "B001-2026"
        );

        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_equipment_master")))
                .thenReturn(new Document("equipmentId", "RMG-100L-2-PVII"));
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_equipment_live_status")))
                .thenReturn(new Document("equipmentId", "RMG-100L-2-PVII")
                        .append("currentState", "RUNNING")
                        .append("lastBatchNo", "B001-2026")
                        .append("lastEventAt", Instant.parse("2026-07-01T00:36:00Z"))
                        .append("updatedAt", Instant.parse("2026-07-01T00:36:00Z")));
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("iiot_ts_cpp_tnt_0001_rmg_100l_2_pvii")))
                .thenReturn(List.of(new Document("meta", new Document("batchNo", "B001-2026"))));
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("iiot_ts_alarm_event_tnt_0001_rmg_100l_2_pvii")))
                .thenReturn(List.of(new Document("event", new Document("eventCategory", "ALARM"))));

        Map<String, Object> response = service.getEquipmentMonitoringView(filter);

        assertEquals("RMG-100L-2-PVII", response.get("equipmentId"));
        assertEquals("B001-2026", response.get("batchNo"));
        assertEquals(1, ((List<?>) response.get("cppData")).size());
        assertEquals(1, ((List<?>) response.get("alarmData")).size());
    }

    @Test
    void getEquipmentMonitoringViewRejectsMissingBatch() {
        Map<String, Object> filter = Map.of("equipmentId", "RMG-100L-2-PVII");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getEquipmentMonitoringView(filter));

        assertEquals("batchNo is required", exception.getMessage());
    }

    @Test
    void getOeeMetricReturnsDashboardAndDetailPayload() {
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_oee_metrics")))
                .thenReturn(null);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_oee_config")))
                .thenReturn(new Document("plannedProductionTime", 7200)
                        .append("qualityFactor", 0.97)
                        .append("targetUnitsPerHour", 1200));
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("iiot_asset_states")))
                .thenReturn(List.of(new Document("state", "RUNNING")
                        .append("startTime", Date.from(Instant.parse("2026-07-01T00:00:00Z")))
                        .append("endTime", Date.from(Instant.parse("2026-07-01T02:00:00Z")))));
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("iiot_telemetry")))
                .thenReturn(List.of(new Document("tagCode", "RPM").append("value", 1200.0)));
        when(mongoTemplate.save(any(Document.class), eq("iiot_oee_metrics")))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> response = service.getOeeMetric("RMG-100L-2-PVII", LocalDate.of(2026, 7, 1));

        assertEquals("RMG-100L-2-PVII", response.get("assetCode"));
        Map<String, Object> dashboard = (Map<String, Object>) response.get("dashboard");
        assertEquals("ON_TRACK", dashboard.get("status"));
        Map<String, Object> details = (Map<String, Object>) response.get("details");
        assertEquals(7200.0, details.get("runningSeconds"));
        assertEquals(0.0, details.get("downtimeSeconds"));
        assertEquals(2328.0, details.get("goodUnits"));
    }
}
