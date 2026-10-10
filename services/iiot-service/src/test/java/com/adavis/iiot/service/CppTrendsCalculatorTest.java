package com.adavis.iiot.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CppTrendsCalculatorTest {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static Map<String, Object> compressionReport(String batch, String lot, String time, double mean, double ref) {
        return Map.of(
                "observedAt", time,
                "meta", Map.of("batchNo", batch, "derivedLotNo", lot, "productCode", "Amisulpride 200mg",
                        "productName", "Amisulpride 200mg"),
                "compression_details", Map.of(
                        "batchInfo", Map.of("stationNo", "Station 1"),
                        "pressureData", Map.of("mainPressure", Map.of("meanKn", mean, "minKn", mean - 1, "maxKn", mean + 1, "sdPercent", 2.5)),
                        "operationValues", Map.of("diskSpeedRpm", 30),
                        "tabletCounters", Map.of("totalCounter", 1000, "hep", Map.of("count", 5), "lep", Map.of("count", 5)),
                        "recipeSettings", Map.of("controlLimits", Map.of(
                                "ref", Map.of("kn", ref), "lcp", Map.of("kn", ref - 1), "hcp", Map.of("kn", ref + 1),
                                "lep", Map.of("kn", ref - 5), "hep", Map.of("kn", ref + 5),
                                "sdLimit", Map.of("percent", 15)))));
    }

    @Test
    void compressionLotsBecomePointsWithPerLotLimits() {
        List<Map<String, Object>> records = List.of(
                compressionReport("B1", "Lot-01", "2026-09-01 08:00:00", 20.0, 20.0),
                compressionReport("B1", "Lot-02", "2026-09-01 10:00:00", 22.0, 20.0),
                compressionReport("B2", "Lot-01", "2026-09-05 09:00:00", 12.0, 12.0),
                compressionReport("B3", "Lot-01", "2026-10-15 09:00:00", 12.0, 12.0));
        Map<String, Object> result = CppTrendsCalculator.build(new CppTrendsCalculator.Input(
                Map.of("id", "MC081"), records, Map.of(), List.of(), List.of(), List.of(), "", "MAIN_FORCE",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), IST));

        List<Map<String, Object>> series = maps(result.get("series"));
        assertEquals(2, series.size(), "out-of-range batch excluded, lots grouped by batch");
        Map<String, Object> b1 = series.get(0);
        assertEquals("B1", b1.get("batchNo"));
        List<Map<String, Object>> points = maps(b1.get("points"));
        assertEquals(2, points.size());
        assertEquals(120.0, points.get(1).get("elapsedMin"));
        assertEquals("OK", points.get(0).get("status"));
        assertEquals("WARNING", points.get(1).get("status"));
        assertEquals("Lot-02 · Station 1", points.get(1).get("label"));
        assertTrue((Boolean) result.get("limitsVary"), "B1 and B2 use different machine reference limits");
        assertNull(result.get("limits"));

        Map<String, Object> stats = map(b1.get("stats"));
        assertEquals(21.0, stats.get("mean"));
        assertEquals(1, stats.get("warningCount"));
        assertEquals(50.0, stats.get("inLimitPct"));

        List<String> codes = maps(result.get("parameters")).stream().map(p -> (String) p.get("code")).toList();
        assertTrue(codes.containsAll(List.of("MAIN_FORCE", "MAIN_FORCE_SD", "TURRET_SPD", "REJECT_RATE")));
        assertFalse(codes.contains("PRE_FORCE"), "parameters without data are hidden");
    }

    @Test
    void metricRecordsUseMasterAliasesAndRecipeLimits() {
        Date t0 = Date.from(java.time.Instant.parse("2026-09-12T03:30:00Z"));
        Date t1 = Date.from(java.time.Instant.parse("2026-09-12T03:40:00Z"));
        List<Map<String, Object>> records = List.of(
                Map.of("observedAt", t0, "meta", Map.of("batchNo", "AG1", "lotNo", "NA"), "metrics", Map.of("INLET AIR TEMP", 55, "CYCLE COUNTER", 3)),
                Map.of("observedAt", t1, "meta", Map.of("batchNo", "AG1", "lotNo", "NA"), "metrics", Map.of("INLET AIR TEMP", 85)));
        List<Map<String, Object>> masters = List.of(Map.of("equipmentId", "MB041", "parameterCode", "INLET_AIR_TEMP",
                "parameterName", "Inlet Air Temperature", "unitOfMeasure", "°C"));
        List<Map<String, Object>> limits = List.of(Map.of("equipmentId", "MB041", "parameterCode", "INLET_AIR_TEMP",
                "baseValue", 60, "lowerLimitWarning", 40, "upperLimitWarning", 80, "lowerLimitCritical", 36, "upperLimitCritical", 88));
        List<Map<String, Object>> recipes = List.of(Map.of("equipmentId", "MB041", "parameterCode", "INLET_AIR_TEMP",
                "productCode", "Prod A", "targetSetpoint", 58, "lowLimit", 45, "highLimit", 70, "recipeCode", "R1"));

        Map<String, Object> result = CppTrendsCalculator.build(new CppTrendsCalculator.Input(
                Map.of("id", "MB041"), records, Map.of("AG1", Map.of("productCode", "Prod A", "productName", "Prod A")),
                masters, limits, recipes, "Prod A", null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), IST));

        assertEquals("INLET_AIR_TEMP", result.get("selectedParameter"));
        assertEquals(1, maps(result.get("parameters")).size(), "counters are excluded");
        Map<String, Object> resultLimits = map(result.get("limits"));
        assertEquals(58.0, resultLimits.get("setpoint"));
        assertEquals(70.0, resultLimits.get("upperWarning"));
        assertEquals(88.0, resultLimits.get("upperCritical"));
        List<Map<String, Object>> points = maps(maps(result.get("series")).get(0).get("points"));
        assertEquals("OK", points.get(0).get("status"));
        assertEquals("WARNING", points.get(1).get("status"));
        assertEquals(10.0, points.get(1).get("elapsedMin"));
    }

    @Test
    void naiveMachineTimestampsArePlantLocal() {
        assertEquals(java.time.Instant.parse("2026-09-01T02:36:43Z").toEpochMilli(),
                CppTrendsCalculator.millis("2026-09-01 08:06:43", IST));
    }

    @Test
    void cpkUsesCriticalLimits() {
        Map<String, Object> stats = CppTrendsCalculator.stats(List.of(9.0, 10.0, 11.0),
                new CppTrendsCalculator.Limits(10.0, 8.0, 12.0, 7.0, 13.0, "x"), 0, 0);
        assertEquals(1.0, stats.get("cpk"));
        assertEquals(1.0, stats.get("sd"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
