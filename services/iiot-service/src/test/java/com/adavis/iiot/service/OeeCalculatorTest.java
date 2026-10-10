package com.adavis.iiot.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OeeCalculatorTest {
    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final String KEY = OeeCalculator.scopeKey("T", "P", "eq1");

    @Test
    void mergesIntervalsAndParsesReportRuntime() {
        List<OeeCalculator.Interval> merged = OeeCalculator.merge(List.of(
                new OeeCalculator.Interval(5, 10), new OeeCalculator.Interval(0, 6), new OeeCalculator.Interval(12, 15)));
        assertEquals(List.of(new OeeCalculator.Interval(0, 10), new OeeCalculator.Interval(12, 15)), merged);
        assertEquals(2.5, OeeCalculator.reportRuntimeHours("2 Hour 30 Min"));
        assertEquals("T|P|EQ1", KEY);
    }

    @Test
    void calculatesAvailabilityPerformanceAndQuality() {
        Map<String, Object> result = OeeCalculator.calculate(params(true));
        assertEquals(24.0, result.get("totalScheduledHours"));
        assertEquals(2.0, result.get("totalUnplannedDowntimeHours"));
        assertEquals(91.67, result.get("availabilityPercent"));
        assertEquals(66.67, result.get("performancePercent"));
        assertEquals(100.0, result.get("qualityPercent"));
        assertEquals(61.11, result.get("overallOeePercent"));
        assertEquals(3, ((List<?>) result.get("shiftWise")).size());
    }

    @Test
    void availabilityStaysUnknownUntilDowntimeIsConfirmedComplete() {
        Map<String, Object> result = OeeCalculator.calculate(params(false));
        assertNull(result.get("availabilityPercent"));
        assertNull(result.get("overallOeePercent"));
        assertEquals(66.67, result.get("performancePercent"));
    }

    @Test
    void mapsDottedProductCodesFromSavedSettings() {
        Map<String, Object> raw = setting(true);
        raw.put("idealBatchHours", Map.of("carvedilol 12.5mg", 4));
        assertEquals(4.0, OeeCalculator.toSetting(raw).idealBatchHours().get("CARVEDILOL 12.5MG"));
    }

    @Test
    void resolvesDefaultAndExplicitRanges() {
        long now = Instant.parse("2025-01-10T03:00:00Z").toEpochMilli();
        String[] defaults = OeeCalculationService.range(query(null, null, 7), now, UTC);
        assertEquals("2025-01-03", defaults[0]);
        assertEquals("2025-01-09", defaults[1]);
        assertThrows(IllegalArgumentException.class,
                () -> OeeCalculationService.range(query("2025-01-01", "2026-06-01", null), now, UTC));
    }

    private static OeeCalculationService.Query query(String from, String to, Integer days) {
        return new OeeCalculationService.Query(null, null, null, null, null, from, to, days, false);
    }

    private static OeeCalculator.Params params(boolean downtimeComplete) {
        OeeCalculator.Equipment equipment = new OeeCalculator.Equipment(KEY, "EQ1", "Equipment 1", "RUNNING", "T", "P", List.of());
        OeeCalculator.Batch batch = new OeeCalculator.Batch();
        batch.batchNo = "B1";
        batch.lotNo = "1";
        batch.equipmentId = KEY;
        batch.productCode = "PROD";
        batch.productName = "Product";
        batch.status = "APPROVED";
        batch.startAt = "2025-01-06T09:00:00Z";
        batch.endAt = "2025-01-06T12:00:00Z";
        Map<String, Object> downtime = new HashMap<>(Map.of("tenantId", "T", "plantId", "P", "equipmentId", "EQ1",
                "date", "2025-01-06", "startTime", "08:00", "endTime", "10:00", "classification", "UNPLANNED",
                "category", "Breakdown", "timeZone", "UTC"));
        return new OeeCalculator.Params(List.of(equipment), List.of(batch), List.of(OeeCalculator.toDowntime(downtime)),
                List.of(OeeCalculator.toSetting(setting(downtimeComplete))), "2025-01-06", "2025-01-06", "ALL",
                Instant.parse("2025-02-01T00:00:00Z").toEpochMilli(), UTC, true);
    }

    private static Map<String, Object> setting(boolean downtimeComplete) {
        Map<String, Object> raw = new HashMap<>();
        raw.put("tenantId", "T");
        raw.put("plantId", "P");
        raw.put("equipmentId", "EQ1");
        raw.put("fromDate", "2025-01-01");
        raw.put("toDate", "2025-01-31");
        raw.put("scheduledShifts", List.of("Shift 1", "Shift 2", "Shift 3"));
        raw.put("idealBatchHours", Map.of("PROD", 2));
        raw.put("downtimeComplete", downtimeComplete);
        raw.put("timeZone", "UTC");
        return raw;
    }
}
