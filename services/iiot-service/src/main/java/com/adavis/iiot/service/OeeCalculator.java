package com.adavis.iiot.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Pure OEE calculation: maps ingested equipment/batch/CPP records to executions, derives
 * estimate evidence and aggregates availability, performance and quality per equipment and shift.
 */
public final class OeeCalculator {
    private OeeCalculator() {
    }

    public record Shift(String id, String name, int startHour, int endHour) {
    }

    public static final List<Shift> DEFAULT_SHIFTS = List.of(
            new Shift("Shift 1", "Shift 1 (06:00 - 14:00)", 6, 14),
            new Shift("Shift 2", "Shift 2 (14:00 - 22:00)", 14, 22),
            new Shift("Shift 3", "Shift 3 (22:00 - 06:00)", 22, 30));
    private static final Set<String> GOOD = Set.of("APPROVED", "QA_APPROVED", "RELEASED", "GOOD");
    private static final Set<String> BAD = Set.of("REJECTED", "FAILED", "DEVIATION_REJECTED");
    private static final Set<String> APPROVALS = Set.of("APPROVED", "QA_APPROVED", "RELEASED");
    private static final String[] COLORS = {"#806BDF", "#EF646E", "#2FB1A6", "#3F7ED4", "#f59e0b"};
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern NAIVE_TIME = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}$");
    private static final Pattern RUNTIME = Pattern.compile("^(\\d+)\\s*Hour\\s+(\\d+)\\s*Min\\s*(?:(\\d+)\\s*Sec)?$", Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> METRIC_CODES = Map.of(
            "CURRENT (AMP)", "IMP_AMP", "INLET TEMPARATURE", "INLET_TEMP", "ACTUAL RPM", "BLD_SPD",
            "INLET AIR TEMP", "INLET_AIR_TEMP", "EXHAUST AIR TEMP", "EXHAUST_TEMP", "PAN SPEED (RPM)", "PAN_SPEED");

    public record Interval(long start, long end) {
    }

    public record Equipment(String id, String code, String name, String status, String tenantId, String plantId,
                            List<String> aliases) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("code", code);
            map.put("name", name);
            map.put("status", status);
            map.put("tenantId", tenantId);
            map.put("plantId", plantId);
            map.put("aliases", aliases);
            return map;
        }
    }

    public static final class Evidence {
        public Double runtimeHours;
        public Double idealHours;
        public Double qualityPercent;
        public String performanceBasis;
        public String qualityBasis;
    }

    public static final class Batch {
        public String batchNo;
        public String lotNo;
        public String equipmentId;
        public String productCode;
        public String productName;
        public String status;
        public String startAt;
        public String endAt;
        public Evidence evidence;
    }

    public record Setting(String tenantId, String plantId, String equipmentId, String fromDate, String toDate,
                          List<String> scheduledShifts, List<Integer> scheduledWeekdays,
                          Map<String, Double> idealBatchHours, boolean downtimeComplete, String timeZone) {
        String key() {
            return scopeKey(tenantId, plantId, equipmentId);
        }
    }

    public record Downtime(Map<String, Object> raw, String tenantId, String plantId, String equipmentId, String date,
                           String startTime, String endTime, String classification, String category, String timeZone) {
        String key() {
            return scopeKey(tenantId, plantId, equipmentId);
        }
    }

    public record SourceData(List<Equipment> equipment, List<Batch> batches, int unmappedExecutions) {
    }

    public record Params(List<Equipment> equipment, List<Batch> batches, List<Downtime> downtime,
                         List<Setting> settings, String fromDate, String toDate, String shift, long now,
                         ZoneId defaultZone, boolean includeShiftBreakdown) {
        Params withRange(String from, String to) {
            return new Params(equipment, batches, downtime, settings, from, to, shift, now, defaultZone, false);
        }
    }

    // ---------------------------------------------------------------- source mapping

    public static String scopeKey(String tenantId, String plantId, String equipmentId) {
        return text(tenantId) + "|" + text(plantId) + "|" + text(equipmentId).toUpperCase();
    }

    public static SourceData buildSourceData(List<Map<String, Object>> assets, List<Map<String, Object>> statuses,
                                             List<Map<String, Object>> summaries) {
        List<Equipment> equipment = new ArrayList<>();
        for (Map<String, Object> asset : assets) {
            if (Boolean.FALSE.equals(bool(asset.get("isActive")))) continue;
            String assetId = firstText(asset.get("equipmentId"), asset.get("equipmentCode"));
            String code = firstText(asset.get("equipmentCode"), asset.get("equipmentId")).toUpperCase();
            if (code.isEmpty()) continue;
            String tenant = text(asset.get("tenantId")), plant = text(asset.get("plantId"));
            String status = statuses.stream().filter(s -> {
                String id = text(s.get("equipmentId"));
                String st = text(s.get("tenantId")), sp = text(s.get("plantId"));
                return (id.equals(code) || id.equals(assetId)) && (st.isEmpty() || st.equals(tenant))
                        && (sp.isEmpty() || sp.equals(plant));
            }).map(s -> text(s.get("currentState"))).filter(s -> !s.isEmpty()).findFirst().orElse("Not reported");
            String name = firstText(asset.get("equipmentName"), code) + " (" + code + ")";
            equipment.add(new Equipment(scopeKey(tenant, plant, code), code, name, status, tenant, plant,
                    List.of(code, assetId.toUpperCase())));
        }
        Map<String, Batch> batches = new LinkedHashMap<>();
        int unmapped = 0;
        for (Map<String, Object> summary : summaries) {
            List<Map<String, Object>> stages = maps(summary.get("stages"));
            if (stages.isEmpty()) {
                Map<String, Object> stage = new HashMap<>();
                stage.put("equipmentCode", summary.get("equipmentId"));
                stage.put("stageStartAt", summary.get("batchStartAt"));
                stage.put("stageEndAt", summary.get("batchEndAt"));
                stage.put("approval", Map.of("status", firstText(summary.get("batchStatus"), summary.get("overallStatus"))));
                stages = List.of(stage);
            }
            String batchNo = text(summary.get("batchNo"));
            String summaryTenant = text(summary.get("tenantId")), summaryPlant = text(summary.get("plantId"));
            for (Map<String, Object> stage : stages) {
                String startAt = textOrNull(stage.get("stageStartAt")), endAt = textOrNull(stage.get("stageEndAt"));
                if (startAt == null && endAt == null) continue;
                String code = firstText(stage.get("equipmentCode"), stage.get("equipmentId")).toUpperCase();
                List<Equipment> matching = equipment.stream().filter(eq -> eq.aliases().contains(code)
                        && (summaryTenant.isEmpty() || summaryTenant.equals(eq.tenantId()))
                        && (summaryPlant.isEmpty() || summaryPlant.equals(eq.plantId()))).toList();
                if (matching.size() != 1 || batchNo.isEmpty()) {
                    unmapped++;
                    continue;
                }
                Equipment eq = matching.get(0);
                String lotNo = firstText(stage.get("derivedLotNo"), stage.get("lotNo"), summary.get("lotNo"));
                String approval = text(map(stage.get("approval")).get("status")).toUpperCase();
                // A workflow return/rejection is not evidence that the manufactured product failed QA.
                String outcome = firstText(stage.get("qualityOutcome"), summary.get("qualityOutcome")).toUpperCase();
                Batch batch = new Batch();
                batch.batchNo = batchNo;
                batch.lotNo = lotNo;
                batch.equipmentId = eq.id();
                batch.productCode = text(summary.get("productCode"));
                batch.productName = text(summary.get("productName"));
                batch.status = !outcome.isEmpty() ? outcome : APPROVALS.contains(approval) ? approval : "UNKNOWN";
                batch.startAt = startAt;
                batch.endAt = endAt;
                String key = eq.id() + "|" + batchNo + "|" + lotNo + "|" + Objects.toString(startAt, "") + "|" + Objects.toString(endAt, "");
                Batch previous = batches.get(key);
                if (previous == null || "UNKNOWN".equals(previous.status)) batches.put(key, batch);
            }
        }
        Map<String, Equipment> unique = new LinkedHashMap<>();
        equipment.forEach(eq -> unique.putIfAbsent(eq.id(), eq));
        List<Equipment> sorted = new ArrayList<>(unique.values());
        sorted.sort(Comparator.comparing(Equipment::name, String.CASE_INSENSITIVE_ORDER));
        return new SourceData(sorted, new ArrayList<>(batches.values()), unmapped);
    }

    // ---------------------------------------------------------------- evidence

    public static Double reportRuntimeHours(Object value) {
        Matcher match = RUNTIME.matcher(text(value));
        if (!match.matches()) return null;
        int minutes = Integer.parseInt(match.group(2));
        int seconds = match.group(3) == null ? 0 : Integer.parseInt(match.group(3));
        if (minutes >= 60 || seconds >= 60) return null;
        double hours = Long.parseLong(match.group(1)) + minutes / 60.0 + seconds / 3600.0;
        return hours > 0 ? hours : null;
    }

    static String normalizedLot(Object value) {
        return text(value).toUpperCase().replaceFirst("^LOT-", "").replaceFirst("^0+(?=\\d)", "");
    }

    static Long observedTime(Object value) {
        String timestamp = text(value).replaceFirst(" ", "T");
        return parseMillis(NAIVE_TIME.matcher(timestamp).matches() ? timestamp + "Z" : timestamp);
    }

    /** Attaches estimate evidence to executions; estimates are never persisted as standards or QA outcomes. */
    public static void attachEvidence(List<Batch> batches, List<Equipment> equipment,
                                      List<Map<String, Object>> cpp, List<Map<String, Object>> limits) {
        Map<String, Equipment> byId = equipment.stream().collect(Collectors.toMap(Equipment::id, e -> e, (a, b) -> a));
        Map<String, List<Map<String, Object>>> rowsByBatch = cpp.stream()
                .collect(Collectors.groupingBy(row -> text(map(row.get("meta")).get("batchNo"))));
        for (Batch batch : batches) {
            Equipment eq = byId.get(batch.equipmentId);
            if (eq == null) continue;
            Long start = parseMillis(batch.startAt), end = parseMillis(batch.endAt);
            List<Map<String, Object>> rows = rowsByBatch.getOrDefault(batch.batchNo, List.of()).stream().filter(row -> {
                Map<String, Object> meta = map(row.get("meta"));
                String code = firstText(meta.get("equipmentCode"), meta.get("equipmentId")).toUpperCase();
                if (!eq.aliases().contains(code) && !code.equals(eq.code())) return false;
                if (!firstText(meta.get("tenantId"), row.get("tenantId")).equals(eq.tenantId())
                        || !firstText(meta.get("plantId"), row.get("plantId")).equals(eq.plantId())) return false;
                if (!normalizedLot(firstText(meta.get("derivedLotNo"), meta.get("lotNo"))).equals(normalizedLot(batch.lotNo))) return false;
                Long timestamp = observedTime(row.get("observedAt"));
                return timestamp != null && start != null && end != null && timestamp >= start && timestamp <= end;
            }).toList();
            Evidence evidence = new Evidence();
            List<Map<String, Object>> reports = rows.stream().filter(row -> !map(row.get("compression_details")).isEmpty()).toList();
            if (reports.size() == 1) {
                Map<String, Object> details = map(reports.get(0).get("compression_details"));
                Map<String, Object> counters = map(details.get("tabletCounters"));
                Double total = number(counters.get("totalCounter")), good = number(map(counters.get("good")).get("count"));
                Double runtime = reportRuntimeHours(map(details.get("batchInfo")).get("runningTime"));
                Double capacity = number(map(details.get("operationValues")).get("capacityTabsPerHour"));
                if (total != null && total > 0 && good != null && good >= 0 && good <= total) {
                    evidence.qualityPercent = good / total * 100;
                    evidence.qualityBasis = "Report tablet yield (good / total), not QA release";
                }
                if (runtime != null) evidence.runtimeHours = runtime;
                if (runtime != null && capacity != null && capacity > 0 && total != null && total > 0) {
                    evidence.idealHours = total / capacity;
                    evidence.performanceBasis = "Report output / reported capacity / running hours (not rated ideal speed)";
                }
            } else {
                int assessed = 0, compliant = 0;
                for (Map<String, Object> row : rows) {
                    for (Map.Entry<String, Object> entry : map(row.get("metrics")).entrySet()) {
                        String code = METRIC_CODES.get(entry.getKey().toUpperCase());
                        Double value = number(entry.getValue());
                        if (code == null || value == null) continue;
                        if ("IMP_AMP".equals(code) && !text(map(row.get("meta")).get("status")).toUpperCase().contains("IMPELLER")) continue;
                        Long timestamp = observedTime(row.get("observedAt"));
                        Map<String, Object> limit = limits.stream().filter(l -> !Boolean.FALSE.equals(bool(l.get("isActive")))
                                        && code.equals(text(l.get("parameterCode")))
                                        && (text(l.get("equipmentId")).equals(eq.code()) || text(l.get("equipmentId")).equals(eq.aliases().get(1)))
                                        && text(l.get("tenantId")).equals(eq.tenantId()) && text(l.get("plantId")).equals(eq.plantId())
                                        && effective(l, timestamp))
                                .max(Comparator.comparingLong(l -> parseMillis(text(l.get("effectiveFrom")))))
                                .orElse(null);
                        if (limit == null) continue;
                        Double low = number(limit.get("lowerLimitCritical")), high = number(limit.get("upperLimitCritical"));
                        if (low == null || high == null || low > high) continue;
                        assessed++;
                        if (value >= low && value <= high) compliant++;
                    }
                }
                if (assessed > 0) {
                    evidence.qualityPercent = compliant * 100.0 / assessed;
                    evidence.qualityBasis = "Recorded CPP compliance (" + compliant + "/" + assessed + " readings); not product yield/QA";
                }
            }
            batch.evidence = evidence;
        }
        List<Batch> original = List.copyOf(batches);
        Map<Batch, Double> fallbackIdeals = new HashMap<>();
        Map<Batch, String> fallbackBases = new HashMap<>();
        for (Batch batch : original) {
            Evidence evidence = batch.evidence;
            if (evidence == null || positive(evidence.idealHours)) continue;
            Long start = parseMillis(batch.startAt), end = parseMillis(batch.endAt);
            if (positive(evidence.runtimeHours)) {
                // Report runtime is known but capacity is missing: use other same-product report ideals.
                List<Double> ideals = original.stream().filter(b -> b != batch && b.equipmentId.equals(batch.equipmentId)
                                && b.productCode.equalsIgnoreCase(batch.productCode) && b.evidence != null
                                && positive(b.evidence.runtimeHours) && positive(b.evidence.idealHours))
                        .map(b -> b.evidence.idealHours).toList();
                if (!ideals.isEmpty()) {
                    fallbackIdeals.put(batch, median(ideals));
                    fallbackBases.put(batch, "Report capacity missing; median report ideal of " + ideals.size()
                            + " other same-product lots (not rated ideal speed)");
                }
            } else if (start != null && end != null && end > start) {
                // Collapse same-time lot aliases so replayed summaries cannot manufacture a baseline.
                Map<String, Batch> peerMap = new LinkedHashMap<>();
                for (Batch b : original) {
                    Long bs = parseMillis(b.startAt), be = parseMillis(b.endAt);
                    if (!b.equipmentId.equals(batch.equipmentId) || b.batchNo.equals(batch.batchNo)
                            || bs == null || be == null || be <= bs
                            || (Objects.equals(b.startAt, batch.startAt) && Objects.equals(b.endAt, batch.endAt))) continue;
                    peerMap.put(b.batchNo + "|" + b.startAt + "|" + b.endAt, b);
                }
                List<Batch> peers = new ArrayList<>(peerMap.values());
                List<Batch> sameProduct = peers.stream().filter(b -> b.productCode.equalsIgnoreCase(batch.productCode)).toList();
                List<Batch> baseline = sameProduct.isEmpty() ? peers : sameProduct;
                List<Double> durations = baseline.stream()
                        .map(b -> (parseMillis(b.endAt) - parseMillis(b.startAt)) / 3_600_000.0).toList();
                if (!durations.isEmpty()) {
                    fallbackIdeals.put(batch, median(durations));
                    fallbackBases.put(batch, "Ingested-history median (" + durations.size() + " other "
                            + (sameProduct.isEmpty() ? "equipment, mixed-product" : "same-product") + " executions); not validated ideal");
                }
            }
        }
        fallbackIdeals.forEach((batch, ideal) -> {
            batch.evidence.idealHours = ideal;
            batch.evidence.performanceBasis = fallbackBases.get(batch);
        });
    }

    private static boolean effective(Map<String, Object> limit, Long timestamp) {
        Long from = parseMillis(text(limit.get("effectiveFrom")));
        if (from == null || timestamp == null || from > timestamp) return false;
        String toText = text(limit.get("effectiveTo"));
        if (toText.isEmpty()) return true;
        Long to = parseMillis(toText);
        return to != null && to >= timestamp;
    }

    // ---------------------------------------------------------------- engine

    public static LocalDate parseLocalDate(String value) {
        if (value == null || !DATE.matcher(value).matches()) return null;
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String productionDate(long millis, ZoneId zone) {
        ZonedDateTime time = Instant.ofEpochMilli(millis).atZone(zone);
        LocalDate date = time.toLocalDate();
        return (time.getHour() < 6 ? date.minusDays(1) : date).toString();
    }

    static long zonedTimestamp(LocalDate date, int hour, int minute, ZoneId zone) {
        return date.atStartOfDay().plusHours(hour).plusMinutes(minute).atZone(zone).toInstant().toEpochMilli();
    }

    public static List<Interval> productionWindows(String from, String to, Collection<String> shifts, long now,
                                                   ZoneId zone, List<Integer> weekdays) {
        LocalDate first = parseLocalDate(from), last = parseLocalDate(to);
        if (first == null || last == null || first.isAfter(last)) return List.of();
        List<Interval> result = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            if (weekdays != null && !weekdays.contains(day.getDayOfWeek().getValue() % 7)) continue;
            for (Shift shift : DEFAULT_SHIFTS) {
                if (!shifts.contains(shift.id())) continue;
                long start = zonedTimestamp(day, shift.startHour(), 0, zone);
                long end = Math.min(zonedTimestamp(day, shift.endHour(), 0, zone), now);
                if (end > start) result.add(new Interval(start, end));
            }
        }
        return merge(result);
    }

    public static List<Interval> downtimeInterval(Downtime record, ZoneId fallback) {
        if (Objects.equals(record.startTime(), record.endTime())) return List.of();
        LocalDate date = parseLocalDate(record.date());
        int[] start = clock(record.startTime()), end = clock(record.endTime());
        if (date == null || start == null || end == null) return List.of();
        boolean overnight = end[0] * 60 + end[1] < start[0] * 60 + start[1];
        ZoneId zone = zone(record.timeZone(), fallback);
        return List.of(new Interval(zonedTimestamp(date, start[0], start[1], zone),
                zonedTimestamp(date, end[0] + (overnight ? 24 : 0), end[1], zone)));
    }

    public static List<Interval> merge(List<Interval> intervals) {
        List<Interval> sorted = intervals.stream().filter(i -> i.end() > i.start())
                .sorted(Comparator.comparingLong(Interval::start)).toList();
        List<Interval> result = new ArrayList<>();
        for (Interval current : sorted) {
            if (!result.isEmpty() && current.start() <= result.get(result.size() - 1).end()) {
                Interval previous = result.remove(result.size() - 1);
                result.add(new Interval(previous.start(), Math.max(previous.end(), current.end())));
            } else {
                result.add(current);
            }
        }
        return result;
    }

    static List<Interval> intersect(List<Interval> left, List<Interval> right) {
        List<Interval> result = new ArrayList<>();
        for (Interval a : left) for (Interval b : right) {
            result.add(new Interval(Math.max(a.start(), b.start()), Math.min(a.end(), b.end())));
        }
        return merge(result);
    }

    static List<Interval> subtract(List<Interval> intervals, List<Interval> removed) {
        List<Interval> current = intervals;
        for (Interval cut : removed) {
            List<Interval> next = new ArrayList<>();
            for (Interval i : current) {
                if (cut.end() <= i.start() || cut.start() >= i.end()) {
                    next.add(i);
                    continue;
                }
                if (Math.min(i.end(), cut.start()) > i.start()) next.add(new Interval(i.start(), Math.min(i.end(), cut.start())));
                if (i.end() > Math.max(i.start(), cut.end())) next.add(new Interval(Math.max(i.start(), cut.end()), i.end()));
            }
            current = next;
        }
        return current;
    }

    static boolean overlaps(List<Interval> left, List<Interval> right) {
        return left.stream().anyMatch(i -> right.stream().anyMatch(w -> i.start() < w.end() && i.end() > w.start()));
    }

    private static double hours(List<Interval> intervals) {
        return intervals.stream().mapToDouble(i -> (i.end() - i.start()) / 3_600_000.0).sum();
    }

    private static long daysBetween(String from, String to) {
        return ChronoUnit.DAYS.between(LocalDate.parse(from), LocalDate.parse(to)) + 1;
    }

    static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private static Double rounded(Double value) {
        return value == null ? null : round(value);
    }

    private static Double percent(double part, double total) {
        return total > 0 ? Math.min(100, Math.max(0, part / total * 100)) : null;
    }

    private static Map<String, Object> aggregate(Params params, String shift) {
        List<String> selected = "ALL".equals(shift) ? DEFAULT_SHIFTS.stream().map(Shift::id).toList() : List.of(shift);
        String fromDate = params.fromDate(), toDate = params.toDate();
        boolean validRange = parseLocalDate(fromDate) != null && parseLocalDate(toDate) != null && fromDate.compareTo(toDate) <= 0;
        long rangeDays = validRange ? daysBetween(fromDate, toDate) : 0;
        double scheduled = 0, planned = 0, unplanned = 0, operating = 0, actual = 0, ideal = 0, qualitySum = 0;
        int completed = 0, good = 0, rejected = 0, scheduledEquipment = 0, availEquipment = 0;
        double availScheduled = 0, availPlanned = 0;
        long maxCoveredDays = 0;
        boolean performanceKnown = true, qualityKnown = true;
        Map<String, Map<String, Object>> segments = new LinkedHashMap<>();
        List<Map<String, Object>> equipmentWise = new ArrayList<>();
        for (Equipment eq : params.equipment()) {
            Setting config = params.settings().stream().filter(s -> s.key().equals(eq.id())).findFirst().orElse(null);
            ZoneId zone = config == null ? params.defaultZone() : zone(config.timeZone(), params.defaultZone());
            List<Interval> scope = productionWindows(fromDate, toDate, selected, params.now(), zone, null);
            // Schedule only the part of the selected range the saved configuration covers;
            // days outside it are treated as not scheduled (excluded from availability), not as zero output.
            String coveredFrom = config != null && config.fromDate().compareTo(fromDate) > 0 ? config.fromDate() : fromDate;
            String coveredTo = config != null && config.toDate().compareTo(toDate) < 0 ? config.toDate() : toDate;
            boolean covered = config != null && validRange && coveredFrom.compareTo(coveredTo) <= 0;
            long coveredDays = covered ? daysBetween(coveredFrom, coveredTo) : 0;
            List<Interval> scheduledWindows = covered ? productionWindows(coveredFrom, coveredTo,
                    config.scheduledShifts().stream().filter(selected::contains).toList(), params.now(), zone,
                    config.scheduledWeekdays()) : List.of();
            List<Interval> downtimeScope = covered ? scheduledWindows : scope;
            List<Downtime> records = params.downtime().stream().filter(d -> d.key().equals(eq.id())).toList();
            List<Downtime> plannedRecords = records.stream().filter(d -> "PLANNED".equals(d.classification())).toList();
            List<Downtime> unplannedRecords = records.stream().filter(d -> "UNPLANNED".equals(d.classification())).toList();
            List<Interval> plannedWindows = intersect(intervals(plannedRecords, params.defaultZone()), downtimeScope);
            // Planned downtime takes precedence where classifications overlap.
            List<Interval> unplannedWindows = subtract(intersect(intervals(unplannedRecords, params.defaultZone()), downtimeScope), plannedWindows);
            Double eqScheduled = covered ? hours(scheduledWindows) : null;
            double eqPlanned = hours(plannedWindows), eqUnplanned = hours(unplannedWindows);
            Double eqProduction = eqScheduled == null ? null : Math.max(0, eqScheduled - eqPlanned);
            Double eqOperating = config != null && config.downtimeComplete() && eqProduction != null
                    ? Math.max(0, eqProduction - eqUnplanned) : null;
            Double eqAvailability = eqOperating != null ? percent(eqOperating, eqProduction) : null;
            List<Interval> attributed = new ArrayList<>();
            List<Downtime> ordered = new ArrayList<>(plannedRecords);
            ordered.addAll(unplannedRecords);
            for (Downtime record : ordered) {
                List<Interval> pieces = subtract(intersect(downtimeInterval(record, params.defaultZone()), downtimeScope), attributed);
                List<Interval> combined = new ArrayList<>(attributed);
                combined.addAll(pieces);
                attributed = merge(combined);
                double value = hours(pieces);
                if (value == 0) continue;
                String key = record.classification() + "|" + record.category();
                Map<String, Object> previous = segments.get(key);
                Map<String, Object> segment = new LinkedHashMap<>();
                segment.put("label", record.category());
                segment.put("hours", (previous == null ? 0 : (double) previous.get("hours")) + value);
                segment.put("classification", record.classification());
                segments.put(key, segment);
            }
            // Count complete batch/lot executions once, in the shift containing completion.
            List<Batch> eqCompleted = params.batches().stream().filter(b -> {
                Long end = parseMillis(b.endAt);
                return b.equipmentId.equals(eq.id()) && end != null && scope.stream().anyMatch(w -> end > w.start() && end <= w.end());
            }).toList();
            int eqGood = (int) eqCompleted.stream().filter(b -> GOOD.contains(b.status.toUpperCase())).count();
            int eqRejected = (int) eqCompleted.stream().filter(b -> BAD.contains(b.status.toUpperCase())).count();
            double eqActual = 0, eqIdeal = 0;
            Set<String> bases = new LinkedHashSet<>();
            boolean eqPerformanceKnown = true;
            for (Batch b : eqCompleted) {
                Long start = parseMillis(b.startAt), end = parseMillis(b.endAt);
                Double duration = b.evidence != null && b.evidence.runtimeHours != null ? b.evidence.runtimeHours
                        : start == null ? null : (end - start) / 3_600_000.0;
                Double configuredIdeal = config == null ? null : config.idealBatchHours().get(b.productCode.toUpperCase());
                String date = productionDate(end - 1, zone);
                boolean validConfig = config != null && date.compareTo(config.fromDate()) >= 0 && date.compareTo(config.toDate()) <= 0;
                boolean useConfig = validConfig && configuredIdeal != null && configuredIdeal != 0;
                Double idealHours = useConfig ? configuredIdeal : b.evidence == null ? null : b.evidence.idealHours;
                if (duration == null || !Double.isFinite(duration) || duration <= 0 || idealHours == null
                        || !Double.isFinite(idealHours) || idealHours <= 0) {
                    eqPerformanceKnown = false;
                    break;
                }
                if (!useConfig && b.evidence != null && b.evidence.performanceBasis != null) bases.add(b.evidence.performanceBasis);
                eqActual += duration;
                eqIdeal += idealHours;
            }
            double eqQualitySum = 0;
            boolean eqQualityKnown = true;
            for (Batch b : eqCompleted) {
                String status = b.status.toUpperCase();
                if (GOOD.contains(status)) {
                    eqQualitySum += 100;
                    continue;
                }
                if (BAD.contains(status)) continue;
                Double value = b.evidence == null ? null : b.evidence.qualityPercent;
                if (value == null || !Double.isFinite(value) || value < 0 || value > 100) {
                    eqQualityKnown = false;
                    break;
                }
                eqQualitySum += value;
                if (b.evidence.qualityBasis != null) bases.add(b.evidence.qualityBasis);
            }
            Double performance = eqPerformanceKnown && !eqCompleted.isEmpty() ? percent(eqIdeal, eqActual) : null;
            Double quality = eqQualityKnown && !eqCompleted.isEmpty() ? eqQualitySum / eqCompleted.size() : null;
            Double oee = eqAvailability != null && performance != null && quality != null
                    ? eqAvailability * performance * quality / 10_000 : null;
            planned += eqPlanned;
            unplanned += eqUnplanned;
            if (eqScheduled != null) {
                scheduled += eqScheduled;
                scheduledEquipment++;
            }
            if (eqOperating != null && eqScheduled != null) {
                operating += eqOperating;
                availScheduled += eqScheduled;
                availPlanned += eqPlanned;
                availEquipment++;
            }
            maxCoveredDays = Math.max(maxCoveredDays, coveredDays);
            performanceKnown &= eqPerformanceKnown;
            qualityKnown &= eqQualityKnown;
            actual += eqActual;
            ideal += eqIdeal;
            completed += eqCompleted.size();
            good += eqGood;
            rejected += eqRejected;
            qualitySum += eqQualitySum;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("estimated", !bases.isEmpty());
            row.put("calculationBasis", bases.isEmpty() ? "Validated ideal duration / final manufacturing outcome" : String.join("; ", bases));
            row.put("scopeKey", eq.id());
            row.put("equipmentId", eq.code());
            row.put("equipmentName", eq.name());
            row.put("status", eq.status());
            row.put("scheduledTimeHours", rounded(eqScheduled));
            row.put("plannedDowntimeHours", round(eqPlanned));
            row.put("plannedProductionTimeHours", rounded(eqProduction));
            row.put("unplannedDowntimeHours", round(eqUnplanned));
            row.put("operatingTimeHours", rounded(eqOperating));
            row.put("availabilityPercent", rounded(eqAvailability));
            row.put("performancePercent", rounded(performance));
            row.put("performanceStatus", performance == null ? "CONFIG_REQUIRED" : "AVAILABLE");
            row.put("qualityPercent", rounded(quality));
            row.put("qualityStatus", quality == null ? "DATA_UNAVAILABLE" : "AVAILABLE");
            row.put("oeePercent", rounded(oee));
            row.put("oeeStatus", oee == null ? "INCOMPLETE_DATA" : "AVAILABLE");
            row.put("equipmentUtilizationPercent", rounded(eqOperating != null && eqScheduled != null ? percent(eqOperating, eqScheduled) : null));
            row.put("totalCompletedBatches", eqCompleted.size());
            row.put("goodReleasedBatches", eqGood);
            row.put("rejectedFailedBatches", eqRejected);
            row.put("scheduleCoveredDays", coveredDays);
            row.put("rangeDays", rangeDays);
            equipmentWise.add(row);
        }
        boolean scheduleKnown = scheduledEquipment > 0, downtimeKnown = availEquipment > 0;
        Double availability = downtimeKnown ? percent(operating, availScheduled - availPlanned) : null;
        Double performance = performanceKnown && completed > 0 ? percent(ideal, actual) : null;
        Double quality = qualityKnown && completed > 0 ? qualitySum / completed : null;
        Double oee = availability != null && performance != null && quality != null ? availability * performance * quality / 10_000 : null;
        double totalDowntime = planned + unplanned;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("estimated", equipmentWise.stream().anyMatch(eq -> Boolean.TRUE.equals(eq.get("estimated"))));
        result.put("overallOeePercent", rounded(oee));
        result.put("oeeStatus", oee == null ? "INCOMPLETE_DATA" : "AVAILABLE");
        result.put("availabilityPercent", rounded(availability));
        result.put("performancePercent", rounded(performance));
        result.put("performanceStatus", performance == null ? "CONFIG_REQUIRED" : "AVAILABLE");
        result.put("qualityPercent", rounded(quality));
        result.put("qualityStatus", quality == null ? "DATA_UNAVAILABLE" : "AVAILABLE");
        result.put("equipmentUtilizationPercent", rounded(downtimeKnown ? percent(operating, availScheduled) : null));
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("coveredDays", maxCoveredDays);
        coverage.put("rangeDays", rangeDays);
        coverage.put("scheduledEquipment", scheduledEquipment);
        coverage.put("availabilityEquipment", availEquipment);
        coverage.put("totalEquipment", params.equipment().size());
        result.put("scheduleCoverage", coverage);
        result.put("totalOperatingHours", downtimeKnown ? round(operating) : null);
        result.put("totalPlannedDowntimeHours", round(planned));
        result.put("totalUnplannedDowntimeHours", round(unplanned));
        result.put("totalScheduledHours", scheduleKnown ? round(scheduled) : null);
        result.put("totalCompletedBatches", completed);
        result.put("totalGoodReleasedBatches", good);
        result.put("totalRejectedFailedBatches", rejected);
        result.put("equipmentWise", equipmentWise);
        List<Map<String, Object>> segmentList = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> segment : segments.values()) {
            double value = (double) segment.get("hours");
            Map<String, Object> item = new LinkedHashMap<>(segment);
            item.put("hours", round(value));
            item.put("percent", round(totalDowntime > 0 ? value / totalDowntime * 100 : 0));
            item.put("color", COLORS[index++ % COLORS.length]);
            item.put("gradientTo", "#94a3b8");
            segmentList.add(item);
        }
        result.put("downtimeSegments", segmentList);
        return result;
    }

    public static Map<String, Object> calculate(Params params) {
        String shift = params.shift() == null ? "ALL" : params.shift();
        Map<String, Object> overall = aggregate(params, shift);
        List<Map<String, Object>> shiftWise = new ArrayList<>();
        if (params.includeShiftBreakdown()) {
            for (Shift s : DEFAULT_SHIFTS) {
                if (!"ALL".equals(shift) && !shift.equals(s.id())) continue;
                Map<String, Object> result = aggregate(params, s.id());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("shiftId", s.id());
                row.put("shiftName", s.name());
                row.put("scheduledHours", result.get("totalScheduledHours"));
                row.put("plannedDowntimeHours", result.get("totalPlannedDowntimeHours"));
                row.put("unplannedDowntimeHours", result.get("totalUnplannedDowntimeHours"));
                row.put("operatingHours", result.get("totalOperatingHours"));
                row.put("availabilityPercent", result.get("availabilityPercent"));
                row.put("completedBatches", result.get("totalCompletedBatches"));
                row.put("goodReleasedBatches", result.get("totalGoodReleasedBatches"));
                row.put("rejectedBatches", result.get("totalRejectedFailedBatches"));
                row.put("qualityPercent", result.get("qualityPercent"));
                row.put("performancePercent", result.get("performancePercent"));
                row.put("oeePercent", result.get("overallOeePercent"));
                shiftWise.add(row);
            }
        }
        overall.put("shiftWise", shiftWise);
        return overall;
    }

    private static final DateTimeFormatter TREND_LABEL = DateTimeFormatter.ofPattern("MMM d", Locale.US);

    public static List<Map<String, Object>> trendPoints(Params params) {
        List<Map<String, Object>> points = new ArrayList<>();
        LocalDate start = parseLocalDate(params.fromDate()), end = parseLocalDate(params.toDate());
        if (start == null || end == null) return points;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            Map<String, Object> point = aggregate(params.withRange(day.toString(), day.toString()),
                    params.shift() == null ? "ALL" : params.shift());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", day.format(TREND_LABEL));
            row.put("oee", point.get("overallOeePercent"));
            points.add(row);
        }
        return points;
    }

    /** Downtime records that overlap the scheduled (or default) windows of the selected range and shift. */
    public static List<Map<String, Object>> visibleDowntime(Params params) {
        String shift = params.shift() == null ? "ALL" : params.shift();
        List<String> selected = "ALL".equals(shift) ? DEFAULT_SHIFTS.stream().map(Shift::id).toList() : List.of(shift);
        Map<String, List<Interval>> windows = new HashMap<>();
        for (Equipment eq : params.equipment()) {
            Setting config = params.settings().stream().filter(s -> s.key().equals(eq.id())
                    && s.fromDate().compareTo(params.fromDate()) <= 0 && s.toDate().compareTo(params.toDate()) >= 0).findFirst().orElse(null);
            ZoneId zone = config == null ? params.defaultZone() : zone(config.timeZone(), params.defaultZone());
            windows.put(eq.id(), productionWindows(params.fromDate(), params.toDate(),
                    config == null ? selected : config.scheduledShifts().stream().filter(selected::contains).toList(),
                    params.now(), zone, config == null ? null : config.scheduledWeekdays()));
        }
        return params.downtime().stream().filter(record -> {
            List<Interval> eqWindows = windows.get(record.key());
            return eqWindows != null && overlaps(downtimeInterval(record, params.defaultZone()), eqWindows);
        }).map(Downtime::raw).toList();
    }

    /** Suggested ideal hours per equipment and product: median report/capacity ideal, else median actual run time. */
    public static Map<String, Map<String, Map<String, Object>>> idealSuggestions(List<Batch> batches) {
        Map<String, Map<String, List<List<Double>>>> grouped = new TreeMap<>();
        for (Batch batch : batches) {
            if (batch.productCode.isEmpty()) continue;
            List<List<Double>> entry = grouped.computeIfAbsent(batch.equipmentId, k -> new TreeMap<>())
                    .computeIfAbsent(batch.productCode.toUpperCase(), k -> List.of(new ArrayList<>(), new ArrayList<>()));
            if (batch.evidence != null && positive(batch.evidence.idealHours)) entry.get(0).add(batch.evidence.idealHours);
            Double actualHours = actualHours(batch);
            if (actualHours != null) entry.get(1).add(actualHours);
        }
        Map<String, Map<String, Map<String, Object>>> result = new LinkedHashMap<>();
        grouped.forEach((equipmentId, products) -> {
            Map<String, Map<String, Object>> byProduct = new LinkedHashMap<>();
            products.forEach((code, values) -> {
                Map<String, Object> suggestion = new LinkedHashMap<>();
                if (!values.get(0).isEmpty()) {
                    suggestion.put("hours", round(median(values.get(0))));
                    suggestion.put("basis", "median rated ideal of " + values.get(0).size() + " lot(s)");
                } else {
                    suggestion.put("hours", values.get(1).isEmpty() ? null : round(median(values.get(1))));
                    suggestion.put("basis", "median actual run of " + values.get(1).size() + " lot(s)");
                }
                byProduct.put(code, suggestion);
            });
            result.put(equipmentId, byProduct);
        });
        return result;
    }

    private static Double actualHours(Batch batch) {
        if (batch.evidence != null && positive(batch.evidence.runtimeHours)) return batch.evidence.runtimeHours;
        Long start = parseMillis(batch.startAt), end = parseMillis(batch.endAt);
        return start != null && end != null && end > start ? (end - start) / 3_600_000.0 : null;
    }

    // ---------------------------------------------------------------- input mapping

    public static Setting toSetting(Map<String, Object> raw) {
        Map<String, Double> ideal = new HashMap<>();
        map(raw.get("idealBatchHours")).forEach((code, value) -> {
            Double hours = number(value);
            if (hours != null) ideal.put(code.trim().toUpperCase(), hours);
        });
        List<String> shifts = raw.get("scheduledShifts") instanceof List<?> list
                ? list.stream().map(OeeCalculator::text).toList() : List.of();
        List<Integer> weekdays = raw.get("scheduledWeekdays") instanceof List<?> list
                ? list.stream().map(OeeCalculator::number).filter(Objects::nonNull).map(Double::intValue).toList() : null;
        return new Setting(text(raw.get("tenantId")), text(raw.get("plantId")), text(raw.get("equipmentId")),
                text(raw.get("fromDate")), text(raw.get("toDate")), shifts, weekdays, ideal,
                Boolean.TRUE.equals(bool(raw.get("downtimeComplete"))), text(raw.get("timeZone")));
    }

    public static Downtime toDowntime(Map<String, Object> raw) {
        return new Downtime(raw, text(raw.get("tenantId")), text(raw.get("plantId")), text(raw.get("equipmentId")),
                text(raw.get("date")), text(raw.get("startTime")), text(raw.get("endTime")),
                text(raw.get("classification")).toUpperCase(), text(raw.get("category")), text(raw.get("timeZone")));
    }

    // ---------------------------------------------------------------- helpers

    private static List<Interval> intervals(List<Downtime> records, ZoneId fallback) {
        return records.stream().flatMap(r -> downtimeInterval(r, fallback).stream()).toList();
    }

    static ZoneId zone(String value, ZoneId fallback) {
        try {
            return value == null || value.isBlank() ? fallback : ZoneId.of(value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int[] clock(String value) {
        String[] parts = text(value).split(":");
        if (parts.length < 2) return null;
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    private static boolean positive(Double value) {
        return value != null && value > 0;
    }

    static Long parseMillis(String value) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        try {
            return Instant.parse(text).toEpochMilli();
        } catch (RuntimeException ignored) {
        }
        try {
            return OffsetDateTime.parse(text).toInstant().toEpochMilli();
        } catch (RuntimeException ignored) {
        }
        try {
            return LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (RuntimeException ignored) {
        }
        try {
            return LocalDate.parse(text).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli();
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    static String text(Object value) {
        return value instanceof String s ? s.trim() : value == null ? "" : value instanceof Number || value instanceof Boolean ? value.toString() : "";
    }

    private static String textOrNull(Object value) {
        String result = text(value);
        return result.isEmpty() ? null : result;
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            String result = text(value);
            if (!result.isEmpty()) return result;
        }
        return "";
    }

    static Double number(Object value) {
        if (value instanceof Number n) return Double.isFinite(n.doubleValue()) ? n.doubleValue() : null;
        if (value instanceof String s && !s.isBlank()) {
            try {
                double result = Double.parseDouble(s.trim());
                return Double.isFinite(result) ? result : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Boolean bool(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s && !s.isBlank()) return Boolean.parseBoolean(s.trim());
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
    }
}
