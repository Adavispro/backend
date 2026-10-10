package com.adavis.iiot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads ingested batch records and returns API-ready CPP trend series. */
@Service
@RequiredArgsConstructor
public class CppTrendsService {

    static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");
    private static final int MAX_RANGE_DAYS = 366;
    private static final String BATCH_TS_COLLECTION = "iiot_ts_batch_";

    private final MongoTemplate mongoTemplate;
    private Clock clock = Clock.systemUTC();

    public record TrendQuery(String tenantId, String plantId, String equipmentId, String productCode,
                             String parameter, String fromDate, String toDate, Integer days) {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public Map<String, Object> trends(TrendQuery query) {
        String tenant = text(query.tenantId()), plant = text(query.plantId());
        List<Map<String, Object>> equipment = equipmentOptions(tenant, plant);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("equipment", equipment);
        if (equipment.isEmpty()) {
            result.put("message", "No equipment with ingested batch records was found.");
            return result;
        }
        String requested = text(query.equipmentId());
        Map<String, Object> selected = equipment.stream()
                .filter(e -> requested.equalsIgnoreCase(String.valueOf(e.get("id"))))
                .findFirst().orElse(equipment.get(0));
        String equipmentId = String.valueOf(selected.get("id"));

        Query recordsQuery = new Query();
        if (!tenant.isEmpty()) recordsQuery.addCriteria(Criteria.where("meta.tenantId").is(tenant));
        if (!plant.isEmpty()) recordsQuery.addCriteria(Criteria.where("meta.plantId").is(plant));
        List<Map<String, Object>> records = (List) mongoTemplate.find(recordsQuery, Map.class, BATCH_TS_COLLECTION + equipmentId);

        LocalDate[] range = range(query, records);
        CppTrendsCalculator.Input input = new CppTrendsCalculator.Input(selected, records, batchProducts(),
                find("iiot_equipment_critical_parameters", equipmentId),
                find("iiot_equipment_critical_parameters_limit", equipmentId),
                find("iiot_recipe_management", equipmentId),
                query.productCode(), query.parameter(), range[0], range[1], DEFAULT_ZONE);
        result.putAll(CppTrendsCalculator.build(input));
        result.put("selectedEquipmentId", equipmentId);
        result.put("fromDate", range[0].toString());
        result.put("toDate", range[1].toString());
        result.put("message", message(result, records.isEmpty()));
        return result;
    }

    LocalDate[] range(TrendQuery query, List<Map<String, Object>> records) {
        if (query.fromDate() != null && !query.fromDate().isBlank()) {
            LocalDate from = OeeCalculator.parseLocalDate(query.fromDate());
            LocalDate to = OeeCalculator.parseLocalDate(query.toDate());
            if (from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
                throw new IllegalArgumentException("Select a valid start and end date (maximum " + MAX_RANGE_DAYS + " days).");
            }
            return new LocalDate[]{from, to};
        }
        if (query.days() != null) {
            int days = query.days();
            if (days < 1 || days > MAX_RANGE_DAYS) throw new IllegalArgumentException("days must be between 1 and " + MAX_RANGE_DAYS);
            LocalDate end = LocalDate.now(clock.withZone(DEFAULT_ZONE));
            return new LocalDate[]{end.minusDays(days - 1L), end};
        }
        // No explicit range: show the span that actually has ingested records for this equipment.
        LocalDate min = null, max = null;
        for (Map<String, Object> record : records) {
            Long time = CppTrendsCalculator.millis(record.get("observedAt"), DEFAULT_ZONE);
            if (time == null) continue;
            LocalDate day = java.time.Instant.ofEpochMilli(time).atZone(DEFAULT_ZONE).toLocalDate();
            if (min == null || day.isBefore(min)) min = day;
            if (max == null || day.isAfter(max)) max = day;
        }
        LocalDate today = LocalDate.now(clock.withZone(DEFAULT_ZONE));
        if (min == null) return new LocalDate[]{today.minusDays(29), today};
        if (ChronoUnit.DAYS.between(min, max) >= MAX_RANGE_DAYS) min = max.minusDays(MAX_RANGE_DAYS - 1L);
        return new LocalDate[]{min, max};
    }

    private static String message(Map<String, Object> result, boolean noRecords) {
        if (noRecords) return "No ingested batch records exist for this equipment yet.";
        if (result.get("selectedParameter") == null) return "Ingested records for this equipment contain no numeric process parameters.";
        if (((List<?>) result.get("series")).isEmpty()) {
            Object data = result.get("dataRange");
            String hint = data instanceof Map<?, ?> m ? " Data is available " + m.get("fromDate") + " to " + m.get("toDate") + "." : "";
            return "No batches recorded this parameter in the selected range/product." + hint;
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<Map<String, Object>> equipmentOptions(String tenant, String plant) {
        List<Map<String, Object>> masters = (List) mongoTemplate.findAll(Map.class, "iiot_equipment_master");
        List<Map<String, Object>> options = new ArrayList<>();
        java.util.Set<String> collections = mongoTemplate.getCollectionNames();
        for (Map<String, Object> master : masters) {
            String id = text(master.get("equipmentId"));
            if (id.isEmpty() || !collections.contains(BATCH_TS_COLLECTION + id)) continue;
            if (!tenant.isEmpty() && master.get("tenantId") != null && !tenant.equals(text(master.get("tenantId")))) continue;
            if (!plant.isEmpty() && master.get("plantId") != null && !plant.equals(text(master.get("plantId")))) continue;
            if (Boolean.FALSE.equals(master.get("isActive"))) continue;
            if (mongoTemplate.estimatedCount(BATCH_TS_COLLECTION + id) == 0) continue;
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("id", id);
            option.put("name", text(master.get("equipmentName")).isEmpty() ? id : text(master.get("equipmentName")));
            option.put("type", text(master.get("equipmentType")));
            option.put("stageName", text(master.get("stageName")));
            option.put("stageOrder", master.get("stageOrder") instanceof Number n ? n.intValue() : 99);
            options.add(option);
        }
        options.sort(Comparator.comparingInt((Map<String, Object> o) -> (Integer) o.get("stageOrder"))
                .thenComparing(o -> String.valueOf(o.get("id"))));
        return options;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map<String, Map<String, String>> batchProducts() {
        Query query = new Query().with(Sort.by(Sort.Direction.ASC, "batchStartAt"));
        query.fields().include("batchNo").include("productCode").include("productName");
        Map<String, Map<String, String>> products = new HashMap<>();
        for (Map<String, Object> summary : (List<Map<String, Object>>) (List) mongoTemplate.find(query, Map.class, "iiot_batch_summary")) {
            String batchNo = text(summary.get("batchNo")).toUpperCase();
            if (batchNo.isEmpty()) continue;
            products.putIfAbsent(batchNo, Map.of("productCode", text(summary.get("productCode")),
                    "productName", text(summary.get("productName"))));
        }
        return products;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<Map<String, Object>> find(String collection, String equipmentId) {
        return (List) mongoTemplate.find(new Query(Criteria.where("equipmentId").is(equipmentId)), Map.class, collection);
    }

    private static String text(Object value) {
        return OeeCalculator.text(value);
    }
}
