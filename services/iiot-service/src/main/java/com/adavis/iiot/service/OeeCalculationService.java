package com.adavis.iiot.service;

import com.adavis.iiot.service.OeeCalculator.Batch;
import com.adavis.iiot.service.OeeCalculator.Equipment;
import com.adavis.iiot.service.OeeCalculator.Params;
import com.adavis.iiot.service.OeeCalculator.Setting;
import com.adavis.iiot.service.OeeCalculator.SourceData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Serves complete OEE dashboard metrics so the UI only renders API results. */
@Service
@RequiredArgsConstructor
@Slf4j
public class OeeCalculationService {

    static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");
    private static final long SOURCE_TTL_MS = 60_000;
    private static final int MAX_RANGE_DAYS = 366;

    private final IiotOperationsService operationsService;
    private final OeeInputsService inputsService;
    private Clock clock = Clock.systemUTC();

    private volatile CachedSource cached;

    private record CachedSource(SourceData data, String evidenceError, long loadedAt) {
    }

    public record Query(String tenantId, String plantId, String equipmentId, String productCode, String shift,
                        String fromDate, String toDate, Integer days, boolean refresh) {
    }

    public Map<String, Object> metrics(Query query) {
        CachedSource source = source(query.refresh());
        Map<String, Object> inputs = inputsService.getInputs(null, null);
        List<Setting> settings = maps(inputs.get("settings")).stream().map(OeeCalculator::toSetting).toList();
        List<OeeCalculator.Downtime> downtime = maps(inputs.get("downtime")).stream().map(OeeCalculator::toDowntime).toList();

        String tenant = all(query.tenantId()), plant = all(query.plantId()), equipmentId = all(query.equipmentId());
        String product = all(query.productCode()).toUpperCase();
        String shift = all(query.shift()).isEmpty() ? "ALL" : query.shift();
        if (!"ALL".equals(shift) && OeeCalculator.DEFAULT_SHIFTS.stream().noneMatch(s -> s.id().equals(shift))) {
            throw new IllegalArgumentException("Unknown shift: " + shift);
        }

        List<Equipment> active = source.data().equipment().stream()
                .filter(eq -> tenant.isEmpty() || tenant.equals(eq.tenantId()))
                .filter(eq -> plant.isEmpty() || plant.equals(eq.plantId()))
                .filter(eq -> equipmentId.isEmpty() || equipmentId.equals(eq.id()))
                .toList();
        Set<String> ids = active.stream().map(Equipment::id).collect(Collectors.toSet());
        List<Batch> scopedBatches = source.data().batches().stream().filter(b -> ids.contains(b.equipmentId)).toList();
        List<Batch> batches = product.isEmpty() ? scopedBatches
                : scopedBatches.stream().filter(b -> b.productCode.toUpperCase().equals(product)).toList();

        long now = clock.millis();
        Setting selectedConfig = settings.stream().filter(s -> s.key().equals(equipmentId)).findFirst().orElse(null);
        ZoneId rangeZone = selectedConfig == null ? DEFAULT_ZONE : OeeCalculator.zone(selectedConfig.timeZone(), DEFAULT_ZONE);
        String[] range = range(query, now, rangeZone);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fromDate", range[0]);
        result.put("toDate", range[1]);
        result.put("equipment", source.data().equipment().stream().map(Equipment::toMap).toList());
        result.put("productOptions", productOptions(scopedBatches));
        result.put("unmappedExecutions", source.data().unmappedExecutions());
        result.put("evidenceError", source.evidenceError());
        result.put("idealSuggestions", OeeCalculator.idealSuggestions(scopedBatches));

        Params params = new Params(active, batches, downtime, settings, range[0], range[1], shift, now, DEFAULT_ZONE, true);
        result.put("calculation", OeeCalculator.calculate(params));
        result.put("trendPoints", OeeCalculator.trendPoints(params));
        result.put("visibleDowntime", OeeCalculator.visibleDowntime(params));
        result.put("calculatedAt", java.time.Instant.ofEpochMilli(now).toString());
        return result;
    }

    static String[] range(Query query, long now, ZoneId zone) {
        if (query.fromDate() != null && !query.fromDate().isBlank()) {
            LocalDate from = OeeCalculator.parseLocalDate(query.fromDate());
            LocalDate to = OeeCalculator.parseLocalDate(query.toDate());
            if (from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
                throw new IllegalArgumentException("Select a valid start and end date (maximum " + MAX_RANGE_DAYS + " production days).");
            }
            return new String[]{from.toString(), to.toString()};
        }
        int days = query.days() == null ? 7 : query.days();
        if (days < 1 || days > MAX_RANGE_DAYS) throw new IllegalArgumentException("days must be between 1 and " + MAX_RANGE_DAYS);
        LocalDate end = LocalDate.parse(OeeCalculator.productionDate(now, zone));
        return new String[]{end.minusDays(days - 1L).toString(), end.toString()};
    }

    private static List<Map<String, String>> productOptions(List<Batch> batches) {
        Map<String, String> products = new TreeMap<>();
        for (Batch batch : batches) {
            if (batch.productCode == null || batch.productCode.isBlank()) continue;
            String code = batch.productCode.toUpperCase();
            String label = batch.productName == null || batch.productName.isBlank() ? code : batch.productName + " (" + code + ")";
            products.merge(code, label, (current, next) -> current.equals(code) ? next : current);
        }
        return products.entrySet().stream()
                .map(e -> Map.of("code", e.getKey(), "name", e.getValue()))
                .sorted((a, b) -> a.get("name").compareToIgnoreCase(b.get("name")))
                .toList();
    }

    private CachedSource source(boolean refresh) {
        CachedSource current = cached;
        if (!refresh && current != null && clock.millis() - current.loadedAt() < SOURCE_TTL_MS) return current;
        synchronized (this) {
            current = cached;
            if (!refresh && current != null && clock.millis() - current.loadedAt() < SOURCE_TTL_MS) return current;
            cached = loadSource();
            return cached;
        }
    }

    private CachedSource loadSource() {
        SourceData data = OeeCalculator.buildSourceData(
                operationsService.getEquipmentMasters(null, null),
                operationsService.getEquipmentLiveStatuses(new HashMap<>()),
                operationsService.getBatchSummary(new HashMap<>()));
        String evidenceError = null;
        try {
            List<Map<String, Object>> cpp = new ArrayList<>();
            for (Equipment eq : data.equipment()) {
                Map<String, Object> filter = new HashMap<>();
                filter.put("tenantId", eq.tenantId());
                filter.put("plantId", eq.plantId());
                filter.put("equipmentId", eq.code());
                filter.put("limit", 100000);
                cpp.addAll(operationsService.getCppData(filter));
            }
            OeeCalculator.attachEvidence(data.batches(), data.equipment(), cpp, operationsService.getCriticalParameterLimits());
        } catch (RuntimeException ex) {
            log.warn("OEE CPP evidence unavailable: {}", ex.getMessage());
            evidenceError = ex.getMessage() == null ? "Unable to load CPP calculation evidence." : ex.getMessage();
        }
        return new CachedSource(data, evidenceError, clock.millis());
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    private static String all(String value) {
        return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value) ? "" : value.trim();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
    }
}
