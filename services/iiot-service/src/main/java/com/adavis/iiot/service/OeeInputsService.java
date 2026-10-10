package com.adavis.iiot.service;

import com.adavis.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OeeInputsService {
    private final MongoTemplate mongoTemplate;
    private static final String SETTINGS = "iiot_oee_batch_settings";
    private static final String DOWNTIME = "iiot_oee_downtime";
    private static final List<String> SHIFTS = List.of("Shift 1", "Shift 2", "Shift 3");
    private static final List<String> PLANNED = List.of("Preventive Maintenance", "Cleaning", "Changeover",
            "Calibration", "Planned Shutdown", "Break", "Other Planned Downtime");
    private static final List<String> UNPLANNED = List.of("Equipment Failure", "Minor Stoppage",
            "Process Deviation", "Utility Failure", "Unplanned Breakdown");

    public record SettingsRequest(String tenantId, String plantId, String equipmentId,
            String fromDate, String toDate, List<String> scheduledShifts,
            List<Integer> scheduledWeekdays, Map<String, Double> idealBatchHours, boolean downtimeComplete, String timeZone) {}

    public record DowntimeRequest(String tenantId, String plantId, String equipmentId,
            String date, String startTime, String endTime, String classification,
            String category, String reason, String comments, String timeZone) {}

    public Map<String, Object> getInputs(String tenantId, String plantId) {
        Query query = new Query();
        if (tenantId != null && !tenantId.isBlank()) query.addCriteria(Criteria.where("tenantId").is(tenantId));
        if (plantId != null && !plantId.isBlank()) query.addCriteria(Criteria.where("plantId").is(plantId));
        return Map.of("settings", mongoTemplate.find(query, Document.class, SETTINGS).stream().map(this::payload).toList(),
                "downtime", mongoTemplate.find(query, Document.class, DOWNTIME).stream().map(this::payload).toList());
    }

    public Map<String, Object> saveSettings(SettingsRequest request, String actor) {
        requireEquipment(request.tenantId(), request.plantId(), request.equipmentId());
        LocalDate from = date(request.fromDate());
        LocalDate to = date(request.toDate());
        ZoneId zone = zone(request.timeZone());
        if (to.isBefore(from)) throw invalid("End date must not precede start date.");
        if (request.scheduledShifts() == null || request.scheduledShifts().isEmpty()
                || !SHIFTS.containsAll(request.scheduledShifts())) throw invalid("Select valid production shifts.");
        if (request.scheduledWeekdays() == null || request.scheduledWeekdays().isEmpty()
                || request.scheduledWeekdays().stream().anyMatch(day -> day == null || day < 0 || day > 6)) {
            throw invalid("Select valid production weekdays (0 = Sunday, 6 = Saturday).");
        }
        Map<String, Double> ideals = new LinkedHashMap<>();
        if (request.idealBatchHours() != null) {
            request.idealBatchHours().forEach((product, hours) -> {
                if (product == null || product.isBlank() || hours == null || !Double.isFinite(hours) || hours <= 0) {
                    throw invalid("Each ideal duration requires a product code and a positive number of hours.");
                }
                String code = product.trim().toUpperCase(java.util.Locale.ROOT);
                if (ideals.putIfAbsent(code, hours) != null) throw invalid("Duplicate product codes are not allowed.");
            });
        }
        String id = request.tenantId() + "|" + request.plantId() + "|" + request.equipmentId();
        Document previous = mongoTemplate.findById(id, Document.class, SETTINGS);
        Instant now = Instant.now();
        Document doc = scope(request.tenantId(), request.plantId(), request.equipmentId())
                .append("_id", id).append("fromDate", from.toString()).append("toDate", to.toString())
                .append("scheduledShifts", request.scheduledShifts().stream().distinct().toList())
                .append("scheduledWeekdays", request.scheduledWeekdays().stream().distinct().toList())
                // Stored as a list: MongoDB field names cannot contain '.', e.g. "CARVEDILOL 12.5MG".
                .append("idealBatchHours", ideals.entrySet().stream()
                        .map(e -> new Document("productCode", e.getKey()).append("hours", e.getValue())).toList())
                .append("downtimeComplete", request.downtimeComplete())
                .append("timeZone", zone.getId())
                .append("updatedBy", actor).append("updatedAt", now.toString());
        Document saved = mongoTemplate.save(doc, SETTINGS);
        mongoTemplate.insert(new Document("recordId", id).append("action", "SAVE_SETTINGS")
                .append("previous", previous).append("current", new Document(doc))
                .append("actor", actor).append("timestamp", now.toString()), "iiot_oee_input_audit");
        return payload(saved);
    }

    public Map<String, Object> deleteSettings(String tenantId, String plantId, String equipmentId, String actor) {
        if (tenantId == null || plantId == null || equipmentId == null) throw invalid("Equipment scope is required.");
        String id = tenantId + "|" + plantId + "|" + equipmentId;
        Document previous = mongoTemplate.findById(id, Document.class, SETTINGS);
        if (previous == null) throw invalid("OEE configuration not found for " + equipmentId + ".");
        mongoTemplate.remove(new Query(Criteria.where("_id").is(id)), SETTINGS);
        audit(id, "DELETE_SETTINGS", previous, null, actor);
        return Map.of("id", id, "deleted", true);
    }

    public Map<String, Object> addDowntime(DowntimeRequest request, String actor) {
        Document doc = downtimeDocument(request, UUID.randomUUID().toString())
                .append("createdBy", actor).append("createdAt", Instant.now().toString());
        Map<String, Object> saved = payload(mongoTemplate.insert(doc, DOWNTIME));
        audit(doc.getString("_id"), "ADD_DOWNTIME", null, doc, actor);
        return saved;
    }

    public Map<String, Object> updateDowntime(String id, DowntimeRequest request, String actor) {
        Document previous = existingDowntime(id);
        Document doc = downtimeDocument(request, id)
                .append("createdBy", previous.get("createdBy")).append("createdAt", previous.get("createdAt"))
                .append("updatedBy", actor).append("updatedAt", Instant.now().toString());
        Map<String, Object> saved = payload(mongoTemplate.save(doc, DOWNTIME));
        audit(id, "UPDATE_DOWNTIME", previous, doc, actor);
        return saved;
    }

    public Map<String, Object> deleteDowntime(String id, String actor) {
        Document previous = existingDowntime(id);
        mongoTemplate.remove(new Query(Criteria.where("_id").is(id)), DOWNTIME);
        audit(id, "DELETE_DOWNTIME", previous, null, actor);
        return Map.of("id", id, "deleted", true);
    }

    /** Validates every row first; nothing is saved unless all rows are valid. */
    public Map<String, Object> importDowntime(List<DowntimeRequest> rows, String actor) {
        if (rows == null || rows.isEmpty()) throw invalid("The CSV contains no downtime rows.");
        if (rows.size() > 1000) throw invalid("Import at most 1000 downtime rows at a time.");
        String now = Instant.now().toString();
        List<Document> docs = new java.util.ArrayList<>();
        List<String> errors = new java.util.ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            try {
                docs.add(downtimeDocument(rows.get(i), UUID.randomUUID().toString())
                        .append("createdBy", actor).append("createdAt", now).append("source", "CSV_IMPORT"));
            } catch (BusinessException ex) {
                errors.add("Row " + (i + 2) + ": " + ex.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw invalid("No rows were imported. " + String.join(" ", errors.subList(0, Math.min(errors.size(), 10)))
                    + (errors.size() > 10 ? " (+" + (errors.size() - 10) + " more errors)" : ""));
        }
        mongoTemplate.insert(docs, DOWNTIME);
        audit("CSV_IMPORT", "IMPORT_DOWNTIME", null,
                new Document("count", docs.size()).append("ids", docs.stream().map(d -> d.getString("_id")).toList()), actor);
        return Map.of("imported", docs.size(), "downtime", docs.stream().map(this::payload).toList());
    }

    private Document existingDowntime(String id) {
        Document doc = id == null ? null : mongoTemplate.findById(id, Document.class, DOWNTIME);
        if (doc == null) throw new BusinessException("Downtime record not found.", "NOT_FOUND");
        return doc;
    }

    private void audit(String recordId, String action, Document previous, Document current, String actor) {
        mongoTemplate.insert(new Document("recordId", recordId).append("action", action)
                .append("previous", previous).append("current", current == null ? null : new Document(current))
                .append("actor", actor).append("timestamp", Instant.now().toString()), "iiot_oee_input_audit");
    }

    private Document downtimeDocument(DowntimeRequest request, String id) {
        if (request == null) throw invalid("Downtime details are required.");
        requireEquipment(request.tenantId(), request.plantId(), request.equipmentId());
        LocalDate day = date(request.date());
        LocalTime start = time(request.startTime());
        LocalTime end = time(request.endTime());
        ZoneId zone = zone(request.timeZone());
        if (start.equals(end)) throw invalid("Start and end time must differ; use explicit intervals for full-day downtime.");
        if (!"PLANNED".equals(request.classification()) && !"UNPLANNED".equals(request.classification())) {
            throw invalid("Select a valid downtime classification.");
        }
        List<String> categories = "PLANNED".equals(request.classification()) ? PLANNED : UNPLANNED;
        if (!categories.contains(request.category())) throw invalid("Category does not match the downtime classification.");
        if (request.reason() == null || request.reason().isBlank()) throw invalid("A downtime reason is required.");
        LocalDate endDay = end.isBefore(start) ? day.plusDays(1) : day;
        var startDateTime = day.atTime(start).atZone(zone);
        var endDateTime = endDay.atTime(end).atZone(zone);
        if (!startDateTime.toLocalTime().equals(start) || !endDateTime.toLocalTime().equals(end)) {
            throw invalid("A downtime time falls in a daylight-saving gap.");
        }
        long minutes = Duration.between(startDateTime, endDateTime).toMinutes();
        if (minutes <= 0) throw invalid("Downtime must have a positive duration.");
        if (minutes > 24 * 60) throw invalid("A single downtime entry cannot exceed 24 hours.");
        return scope(request.tenantId(), request.plantId(), request.equipmentId())
                .append("_id", id).append("date", day.toString())
                .append("startTime", start.toString()).append("endTime", end.toString())
                .append("durationHours", minutes / 60.0).append("classification", request.classification())
                .append("category", request.category()).append("reason", request.reason().trim())
                .append("comments", request.comments() == null ? "" : request.comments().trim())
                .append("timeZone", zone.getId());
    }

    private void requireEquipment(String tenantId, String plantId, String equipmentId) {
        if (tenantId == null || tenantId.isBlank() || plantId == null || plantId.isBlank()
                || equipmentId == null || equipmentId.isBlank()) throw invalid("Equipment, tenant and plant are required.");
        Criteria criteria = new Criteria().andOperator(Criteria.where("tenantId").is(tenantId),
                Criteria.where("plantId").is(plantId),
                new Criteria().orOperator(Criteria.where("equipmentCode").is(equipmentId),
                        Criteria.where("equipmentId").is(equipmentId)));
        if (!mongoTemplate.exists(new Query(criteria), "iiot_equipment_master")) {
            throw invalid("Equipment does not belong to the selected tenant and plant.");
        }
    }

    private Document scope(String tenantId, String plantId, String equipmentId) {
        return new Document("tenantId", tenantId).append("plantId", plantId).append("equipmentId", equipmentId);
    }

    private Map<String, Object> payload(Document doc) {
        Map<String, Object> result = new LinkedHashMap<>(doc);
        result.put("id", result.remove("_id"));
        if (result.get("idealBatchHours") instanceof List<?> entries) {
            Map<String, Object> ideals = new LinkedHashMap<>();
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> map && map.get("productCode") != null) {
                    ideals.put(map.get("productCode").toString(), map.get("hours"));
                }
            }
            result.put("idealBatchHours", ideals);
        }
        return result;
    }

    private LocalDate date(String value) {
        if (value == null) throw invalid("A date is required.");
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw invalid("Dates must use YYYY-MM-DD.");
        }
    }

    private LocalTime time(String value) {
        if (value == null) throw invalid("Start and end time are required.");
        try {
            LocalTime result = LocalTime.parse(value);
            if (result.getSecond() != 0 || result.getNano() != 0) throw invalid("Times must have minute precision.");
            return result;
        } catch (DateTimeParseException ex) {
            throw invalid("Times must use HH:mm.");
        }
    }

    private ZoneId zone(String value) {
        if (value == null || value.isBlank()) throw invalid("A plant timezone is required.");
        try {
            return ZoneId.of(value);
        } catch (DateTimeException ex) {
            throw invalid("Use a valid IANA timezone, for example Asia/Kolkata.");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(message, "INVALID_OEE_INPUT");
    }
}
