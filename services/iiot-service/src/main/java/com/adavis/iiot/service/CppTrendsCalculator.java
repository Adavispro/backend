package com.adavis.iiot.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/** Builds CPP trend series from ingested batch records so the UI only renders API results. */
public final class CppTrendsCalculator {

    static final int DEFAULT_SELECTION = 6;
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("dd MMM HH:mm", Locale.US);

    /** Metric keys written by file ingestion mapped to configured critical-parameter codes. */
    private static final Map<String, String> METRIC_ALIASES = Map.ofEntries(
            Map.entry("CURRENT (AMP)", "IMP_AMP"),
            Map.entry("IMPELLER CURRENT", "IMP_AMP"),
            Map.entry("IMPELLER SPEED", "IMP_SPD"),
            Map.entry("CHOPPER SPEED", "CHP_SPD"),
            Map.entry("INLET TEMPARATURE", "INLET_TEMP"),
            Map.entry("INLET TEMPERATURE", "INLET_TEMP"),
            Map.entry("BED TEMPARATURE", "BED_TEMP"),
            Map.entry("PRODUCT BED TEMPERATURE", "BED_TEMP"),
            Map.entry("ACTUAL RPM", "BLD_SPD"),
            Map.entry("INLET AIR TEMP", "INLET_AIR_TEMP"),
            Map.entry("EXHAUST AIR TEMP", "EXHAUST_TEMP"),
            Map.entry("PAN SPEED (RPM)", "PAN_SPEED"),
            Map.entry("SPRAY RATE", "SPRAY_RATE"));

    private CppTrendsCalculator() {
    }

    public record Limits(Double setpoint, Double lowerWarning, Double upperWarning,
                         Double lowerCritical, Double upperCritical, String source) {
        boolean empty() {
            return setpoint == null && lowerWarning == null && upperWarning == null
                    && lowerCritical == null && upperCritical == null;
        }

        boolean sameBand(Limits other) {
            return other != null && Objects.equals(setpoint, other.setpoint)
                    && Objects.equals(lowerWarning, other.lowerWarning) && Objects.equals(upperWarning, other.upperWarning)
                    && Objects.equals(lowerCritical, other.lowerCritical) && Objects.equals(upperCritical, other.upperCritical);
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("setpoint", setpoint);
            map.put("lowerWarning", lowerWarning);
            map.put("upperWarning", upperWarning);
            map.put("lowerCritical", lowerCritical);
            map.put("upperCritical", upperCritical);
            map.put("source", source);
            return map;
        }
    }

    record ParamDef(String code, String name, String unit, String masterCode,
                    Function<Map<String, Object>, Double> value,
                    Function<Map<String, Object>, Double> min,
                    Function<Map<String, Object>, Double> max,
                    Function<Map<String, Object>, Limits> recordLimits) {
    }

    public record Input(Map<String, Object> equipment, List<Map<String, Object>> records,
                        Map<String, Map<String, String>> batchProducts,
                        List<Map<String, Object>> masterParameters, List<Map<String, Object>> masterLimits,
                        List<Map<String, Object>> recipeLimits, String productFilter, String parameter,
                        LocalDate from, LocalDate to, ZoneId zone) {
    }

    private record Row(Map<String, Object> raw, long time, String batchNo, String lotNo,
                       String productCode, String productName, String status) {
    }

    public static Map<String, Object> build(Input input) {
        String equipmentId = OeeCalculator.text(input.equipment().get("id"));
        List<Row> all = rows(input);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataRange", dataRange(all, input.zone()));

        List<Row> inRange = all.stream().filter(r -> {
            LocalDate day = Instant.ofEpochMilli(r.time()).atZone(input.zone()).toLocalDate();
            return !day.isBefore(input.from()) && !day.isAfter(input.to());
        }).toList();
        result.put("productOptions", productOptions(inRange));

        String product = OeeCalculator.text(input.productFilter());
        List<Row> scoped = product.isEmpty() ? inRange : inRange.stream()
                .filter(r -> product.equalsIgnoreCase(r.productCode()) || product.equalsIgnoreCase(r.productName()))
                .toList();

        boolean reportSnapshots = all.stream().anyMatch(r -> r.raw().get("compression_details") instanceof Map<?, ?>);
        List<ParamDef> catalog = reportSnapshots ? compressionParameters() : metricParameters(all, input.masterParameters());
        Map<String, Map<String, Object>> masters = new HashMap<>();
        for (Map<String, Object> master : input.masterParameters()) {
            if (equipmentId.equals(OeeCalculator.text(master.get("equipmentId")))) {
                masters.put(OeeCalculator.text(master.get("parameterCode")).toUpperCase(), master);
            }
        }

        List<ParamDef> available = catalog.stream()
                .filter(def -> all.stream().anyMatch(r -> def.value().apply(r.raw()) != null))
                .toList();
        Set<String> covered = new LinkedHashSet<>();
        available.forEach(def -> { if (def.masterCode() != null) covered.add(def.masterCode()); });
        result.put("parameters", available.stream().map(def -> parameterMap(def, masters.get(def.masterCode()))).toList());
        result.put("unavailableParameters", masters.values().stream()
                .filter(m -> !covered.contains(OeeCalculator.text(m.get("parameterCode")).toUpperCase()))
                .map(m -> Map.of("code", OeeCalculator.text(m.get("parameterCode")),
                        "name", OeeCalculator.text(m.get("parameterName"))))
                .toList());

        ParamDef selected = available.stream()
                .filter(def -> def.code().equalsIgnoreCase(OeeCalculator.text(input.parameter())))
                .findFirst().orElse(available.isEmpty() ? null : available.get(0));
        result.put("selectedParameter", selected == null ? null : selected.code());
        if (selected == null) {
            result.put("series", List.of());
            result.put("limits", null);
            result.put("limitsVary", false);
            result.put("defaultSelection", List.of());
            result.put("summary", summary(List.of()));
            return result;
        }

        Map<String, List<Row>> groups = new LinkedHashMap<>();
        scoped.stream().sorted(Comparator.comparingLong(Row::time)).forEach(r -> {
            String key = reportSnapshots || r.lotNo() == null ? r.batchNo() : r.batchNo() + " / " + r.lotNo();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        });

        List<Map<String, Object>> series = new ArrayList<>();
        List<Limits> seriesLimits = new ArrayList<>();
        for (Map.Entry<String, List<Row>> group : groups.entrySet()) {
            Map<String, Object> built = series(group.getKey(), group.getValue(), selected, equipmentId,
                    masterLimit(input.masterLimits(), equipmentId, selected.masterCode()), input.recipeLimits(),
                    reportSnapshots, seriesLimits);
            if (built != null) series.add(built);
        }
        series.sort(Comparator.comparing((Map<String, Object> s) -> OeeCalculator.text(s.get("startAt"))));

        Limits common = seriesLimits.isEmpty() ? null : seriesLimits.get(0);
        boolean vary = seriesLimits.stream().anyMatch(l -> !l.sameBand(seriesLimits.get(0)));
        result.put("limits", common == null || vary || common.empty() ? null : common.toMap());
        result.put("limitsVary", vary);
        result.put("series", series);
        result.put("defaultSelection", series.subList(Math.max(0, series.size() - DEFAULT_SELECTION), series.size())
                .stream().map(s -> s.get("key")).toList());
        result.put("summary", summary(series));
        return result;
    }

    private static List<Row> rows(Input input) {
        List<Row> rows = new ArrayList<>();
        for (Map<String, Object> raw : input.records()) {
            Map<String, Object> meta = map(raw.get("meta"));
            Long time = millis(raw.get("observedAt"), input.zone());
            if (time == null) time = millis(raw.get("event_time"), input.zone());
            String batchNo = OeeCalculator.text(meta.get("batchNo"));
            if (time == null || batchNo.isEmpty()) continue;
            String lot = firstText(meta.get("derivedLotNo"), meta.get("lotNo"));
            if (lot.equalsIgnoreCase("NA") || lot.equals("-")) lot = "";
            Map<String, String> summary = input.batchProducts().getOrDefault(batchNo.toUpperCase(), Map.of());
            String code = firstText(meta.get("productCode"), summary.get("productCode"));
            String name = firstText(meta.get("productName"), summary.get("productName"), code);
            rows.add(new Row(raw, time, batchNo, lot.isEmpty() ? null : lot, code, name,
                    OeeCalculator.text(meta.get("status"))));
        }
        return rows;
    }

    private static Map<String, Object> series(String key, List<Row> rows, ParamDef def, String equipmentId,
                                              Map<String, Object> masterLimit, List<Map<String, Object>> recipes,
                                              boolean reportSnapshots, List<Limits> seriesLimits) {
        Row first = rows.get(0);
        Limits configured = configuredLimits(masterLimit, recipe(recipes, equipmentId, def.masterCode(), first));
        List<Map<String, Object>> points = new ArrayList<>();
        Limits latest = configured;
        int warnings = 0, criticals = 0;
        List<Double> values = new ArrayList<>();
        for (Row row : rows) {
            Double value = def.value().apply(row.raw());
            if (value == null) continue;
            Limits recordLimits = def.recordLimits() == null ? null : def.recordLimits().apply(row.raw());
            Limits limits = recordLimits == null || recordLimits.empty() ? configured : recordLimits;
            latest = limits;
            String status = status(value, limits);
            if ("WARNING".equals(status)) warnings++;
            if ("CRITICAL".equals(status)) criticals++;
            values.add(value);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("t", row.time());
            point.put("elapsedMin", round((row.time() - first.time()) / 60000.0));
            point.put("value", round(value));
            point.put("min", def.min() == null ? null : round(def.min().apply(row.raw())));
            point.put("max", def.max() == null ? null : round(def.max().apply(row.raw())));
            point.put("status", status);
            point.put("label", reportSnapshots ? pointLabel(row) : row.status());
            if (recordLimits != null && !recordLimits.empty()) point.put("limits", recordLimits.toMap());
            points.add(point);
        }
        if (points.isEmpty()) return null;
        if (latest != null) seriesLimits.add(latest);

        Map<String, Object> series = new LinkedHashMap<>();
        series.put("key", key);
        series.put("batchNo", first.batchNo());
        series.put("lotNo", reportSnapshots ? null : first.lotNo());
        series.put("productCode", first.productCode());
        series.put("productName", first.productName());
        series.put("startAt", Instant.ofEpochMilli(rows.get(0).time()).toString());
        series.put("endAt", Instant.ofEpochMilli(rows.get(rows.size() - 1).time()).toString());
        series.put("limits", latest == null || latest.empty() ? null : latest.toMap());
        series.put("points", points);
        series.put("stats", stats(values, latest, warnings, criticals));
        return series;
    }

    private static String pointLabel(Row row) {
        Map<String, Object> info = map(map(row.raw().get("compression_details")).get("batchInfo"));
        String station = OeeCalculator.text(info.get("stationNo"));
        String lot = row.lotNo() == null ? "" : row.lotNo();
        return station.isEmpty() ? lot : lot.isEmpty() ? station : lot + " · " + station;
    }

    static String status(double value, Limits limits) {
        if (limits == null || limits.empty()) return "NO_LIMITS";
        if ((limits.lowerCritical() != null && value < limits.lowerCritical())
                || (limits.upperCritical() != null && value > limits.upperCritical())) return "CRITICAL";
        if ((limits.lowerWarning() != null && value < limits.lowerWarning())
                || (limits.upperWarning() != null && value > limits.upperWarning())) return "WARNING";
        return "OK";
    }

    static Map<String, Object> stats(List<Double> values, Limits limits, int warnings, int criticals) {
        Map<String, Object> stats = new LinkedHashMap<>();
        int n = values.size();
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        double sd = n < 2 ? 0 : Math.sqrt(values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / (n - 1));
        stats.put("count", n);
        stats.put("min", n == 0 ? null : round(values.stream().mapToDouble(Double::doubleValue).min().orElse(0)));
        stats.put("max", n == 0 ? null : round(values.stream().mapToDouble(Double::doubleValue).max().orElse(0)));
        stats.put("mean", n == 0 ? null : round(mean));
        stats.put("sd", n < 2 ? null : round(sd));
        stats.put("warningCount", warnings);
        stats.put("criticalCount", criticals);
        stats.put("inLimitPct", n == 0 || limits == null || limits.empty() ? null : round(100.0 * (n - warnings - criticals) / n));
        Double cpk = null;
        if (n >= 2 && sd > 0 && limits != null) {
            Double lsl = limits.lowerCritical(), usl = limits.upperCritical();
            if (lsl != null && usl != null) cpk = Math.min(usl - mean, mean - lsl) / (3 * sd);
            else if (usl != null) cpk = (usl - mean) / (3 * sd);
            else if (lsl != null) cpk = (mean - lsl) / (3 * sd);
        }
        stats.put("cpk", cpk == null ? null : round(cpk));
        return stats;
    }

    private static Map<String, Object> summary(List<Map<String, Object>> series) {
        int points = 0, warnings = 0, criticals = 0;
        Set<String> batches = new LinkedHashSet<>();
        for (Map<String, Object> s : series) {
            Map<String, Object> stats = map(s.get("stats"));
            points += ((Number) stats.getOrDefault("count", 0)).intValue();
            warnings += ((Number) stats.getOrDefault("warningCount", 0)).intValue();
            criticals += ((Number) stats.getOrDefault("criticalCount", 0)).intValue();
            batches.add(OeeCalculator.text(s.get("batchNo")));
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("batchCount", batches.size());
        summary.put("seriesCount", series.size());
        summary.put("pointCount", points);
        summary.put("warningCount", warnings);
        summary.put("criticalCount", criticals);
        return summary;
    }

    private static Map<String, Object> dataRange(List<Row> rows, ZoneId zone) {
        if (rows.isEmpty()) return null;
        long min = rows.stream().mapToLong(Row::time).min().orElse(0);
        long max = rows.stream().mapToLong(Row::time).max().orElse(0);
        Map<String, Object> range = new LinkedHashMap<>();
        range.put("fromDate", Instant.ofEpochMilli(min).atZone(zone).toLocalDate().toString());
        range.put("toDate", Instant.ofEpochMilli(max).atZone(zone).toLocalDate().toString());
        range.put("recordCount", rows.size());
        range.put("label", LABEL.format(Instant.ofEpochMilli(min).atZone(zone)) + " – "
                + LABEL.format(Instant.ofEpochMilli(max).atZone(zone)));
        return range;
    }

    private static List<Map<String, String>> productOptions(List<Row> rows) {
        Map<String, String> products = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Row row : rows) {
            String code = row.productCode() == null || row.productCode().isBlank() ? row.productName() : row.productCode();
            if (code == null || code.isBlank()) continue;
            products.putIfAbsent(code, row.productName() == null || row.productName().isBlank() ? code : row.productName());
        }
        return products.entrySet().stream().map(e -> Map.of("code", e.getKey(), "name", e.getValue())).toList();
    }

    private static Map<String, Object> parameterMap(ParamDef def, Map<String, Object> master) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", def.code());
        map.put("name", master == null ? def.name() : OeeCalculator.text(master.get("parameterName")));
        String unit = master == null ? "" : OeeCalculator.text(master.get("unitOfMeasure"));
        map.put("unit", unit.isEmpty() ? def.unit() : unit);
        map.put("configured", master != null);
        map.put("hasRange", def.min() != null);
        return map;
    }

    static Limits configuredLimits(Map<String, Object> master, Map<String, Object> recipe) {
        if (master == null && recipe == null) return null;
        Map<String, Object> m = master == null ? Map.of() : master;
        Double setpoint = OeeCalculator.number(m.get("baseValue"));
        Double lw = OeeCalculator.number(m.get("lowerLimitWarning")), uw = OeeCalculator.number(m.get("upperLimitWarning"));
        Double lc = OeeCalculator.number(m.get("lowerLimitCritical")), uc = OeeCalculator.number(m.get("upperLimitCritical"));
        String source = "Equipment CPP limits";
        if (recipe != null) {
            setpoint = firstNumber(recipe.get("targetSetpoint"), setpoint);
            lw = firstNumber(recipe.get("lowLimit"), lw);
            uw = firstNumber(recipe.get("highLimit"), uw);
            source = "Recipe " + firstText(recipe.get("recipeCode"), recipe.get("recipeName"));
        }
        return new Limits(round(setpoint), round(lw), round(uw), round(lc), round(uc), source);
    }

    private static Map<String, Object> masterLimit(List<Map<String, Object>> limits, String equipmentId, String code) {
        if (code == null) return null;
        return limits.stream()
                .filter(l -> equipmentId.equals(OeeCalculator.text(l.get("equipmentId"))))
                .filter(l -> code.equalsIgnoreCase(OeeCalculator.text(l.get("parameterCode"))))
                .filter(l -> !Boolean.FALSE.equals(l.get("isActive")))
                .max(Comparator.comparing(l -> Objects.requireNonNullElse(millis(l.get("effectiveFrom")), 0L)))
                .orElse(null);
    }

    private static Map<String, Object> recipe(List<Map<String, Object>> recipes, String equipmentId, String code, Row row) {
        if (code == null) return null;
        return recipes.stream()
                .filter(r -> equipmentId.equals(OeeCalculator.text(r.get("equipmentId"))))
                .filter(r -> code.equalsIgnoreCase(OeeCalculator.text(r.get("parameterCode"))))
                .filter(r -> !Boolean.FALSE.equals(r.get("isActive")))
                .filter(r -> matchesProduct(r, row))
                .findFirst().orElse(null);
    }

    private static boolean matchesProduct(Map<String, Object> recipe, Row row) {
        for (String field : List.of("productCode", "productName")) {
            String value = OeeCalculator.text(recipe.get(field));
            if (!value.isEmpty() && (value.equalsIgnoreCase(row.productCode()) || value.equalsIgnoreCase(row.productName()))) {
                return true;
            }
        }
        return false;
    }

    static List<ParamDef> compressionParameters() {
        String base = "compression_details.";
        Function<Map<String, Object>, Limits> mainLimits = raw -> {
            Map<String, Object> cl = map(path(raw, base + "recipeSettings.controlLimits"));
            return new Limits(num(cl, "ref.kn"), num(cl, "lcp.kn"), num(cl, "hcp.kn"),
                    num(cl, "lep.kn"), num(cl, "hep.kn"), "Machine control limits (per lot)");
        };
        Function<Map<String, Object>, Limits> sdLimits = raw -> new Limits(null, null, null, null,
                OeeCalculator.number(path(raw, base + "recipeSettings.controlLimits.sdLimit.percent")),
                "Machine SD limit (per lot)");
        Function<Map<String, Object>, Limits> airLimits = raw -> new Limits(null, null, null,
                OeeCalculator.number(path(raw, base + "recipeSettings.airPressureLowLimitKpa")), null,
                "Machine air-pressure low limit");
        Function<Map<String, Object>, Limits> hydraulicLimits = raw -> new Limits(null, null, null,
                OeeCalculator.number(path(raw, base + "recipeSettings.hydraulicPressureLimits.lowLimitMpa")),
                OeeCalculator.number(path(raw, base + "recipeSettings.hydraulicPressureLimits.highLimitMpa")),
                "Machine hydraulic limits");
        return List.of(
                new ParamDef("MAIN_FORCE", "Main Compression Force", "kN", "MAIN_FORCE",
                        field(base + "pressureData.mainPressure.meanKn"), field(base + "pressureData.mainPressure.minKn"),
                        field(base + "pressureData.mainPressure.maxKn"), mainLimits),
                new ParamDef("PRE_FORCE", "Pre-Compression Force", "kN", "PRE_FORCE",
                        field(base + "pressureData.prePressure.meanKn"), field(base + "pressureData.prePressure.minKn"),
                        field(base + "pressureData.prePressure.maxKn"), null),
                new ParamDef("MAIN_FORCE_SD", "Main Force Variation (SD)", "%", null,
                        field(base + "pressureData.mainPressure.sdPercent"), null, null, sdLimits),
                new ParamDef("PRE_FORCE_SD", "Pre-Force Variation (SD)", "%", null,
                        field(base + "pressureData.prePressure.sdPercent"), null, null, sdLimits),
                new ParamDef("TURRET_SPD", "Turret Speed", "RPM", "TURRET_SPD",
                        field(base + "operationValues.diskSpeedRpm"), null, null, null),
                new ParamDef("FEEDER_SPD", "Feeder Speed", "RPM", "FEEDER_SPD",
                        field(base + "operationValues.feeder.speedRpm"), null, null, null),
                new ParamDef("OUTPUT_RATE", "Output Rate", "tabs/h", null,
                        field(base + "operationValues.capacityTabsPerHour"), null, null, null),
                new ParamDef("FILL_DEPTH", "Filling Depth", "mm", null,
                        field(base + "operationValues.fillingDepthMm"), null, null, null),
                new ParamDef("MAIN_THICKNESS", "Main Compression Thickness", "mm", null,
                        field(base + "operationValues.mainPressure.thicknessMm"), null, null, null),
                new ParamDef("PRE_THICKNESS", "Pre-Compression Thickness", "mm", null,
                        field(base + "operationValues.prePressure.thicknessMm"), null, null, null),
                new ParamDef("AIR_PRESSURE", "Main Air Pressure", "kPa", null,
                        field(base + "operationValues.mainAirPressureKpa"), null, null, airLimits),
                new ParamDef("HYD_PRESSURE", "Hydraulic Pressure", "MPa", null,
                        field(base + "operationValues.hydraulicPressureMpa"), null, null, hydraulicLimits),
                new ParamDef("REJECT_RATE", "Tablet Reject Rate", "%", null, CppTrendsCalculator::rejectRate,
                        null, null, null));
    }

    private static Double rejectRate(Map<String, Object> raw) {
        Map<String, Object> counters = map(path(raw, "compression_details.tabletCounters"));
        Double total = OeeCalculator.number(counters.get("totalCounter"));
        if (total == null || total <= 0) return null;
        double rejects = Objects.requireNonNullElse(num(counters, "hep.count"), 0.0)
                + Objects.requireNonNullElse(num(counters, "lep.count"), 0.0);
        return 100.0 * rejects / total;
    }

    static List<ParamDef> metricParameters(List<Row> rows, List<Map<String, Object>> masters) {
        Map<String, String> masterNames = new HashMap<>();
        for (Map<String, Object> master : masters) {
            masterNames.put(normalize(OeeCalculator.text(master.get("parameterName"))),
                    OeeCalculator.text(master.get("parameterCode")).toUpperCase());
        }
        Set<String> keys = new LinkedHashSet<>();
        rows.forEach(r -> map(r.raw().get("metrics")).forEach((k, v) -> {
            if (OeeCalculator.number(v) != null && !k.toUpperCase().contains("COUNTER")) keys.add(k);
        }));
        List<ParamDef> defs = new ArrayList<>();
        Set<String> codes = new LinkedHashSet<>();
        for (String key : keys) {
            String upper = key.trim().toUpperCase();
            String masterCode = METRIC_ALIASES.getOrDefault(upper, masterNames.get(normalize(key)));
            String code = masterCode != null ? masterCode : upper.replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
            if (!codes.add(code)) continue;
            defs.add(new ParamDef(code, displayName(key), unitFromKey(upper), masterCode,
                    raw -> OeeCalculator.number(map(raw.get("metrics")).get(key)), null, null, null));
        }
        return defs;
    }

    private static String displayName(String key) {
        String cleaned = key.replaceAll("\\(.*?\\)", "").replace("TEMPARATURE", "TEMPERATURE").trim().toLowerCase();
        StringBuilder out = new StringBuilder();
        for (String word : cleaned.split("\\s+")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private static String unitFromKey(String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\(([^)]+)\\)").matcher(key);
        if (m.find()) {
            String unit = m.group(1).trim();
            return switch (unit) {
                case "AMP" -> "A";
                case "SEC" -> "s";
                default -> unit;
            };
        }
        if (key.contains("TEMP")) return "°C";
        if (key.contains("RPM")) return "RPM";
        return "";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toUpperCase().replace("TEMPARATURE", "TEMPERATURE").replaceAll("[^A-Z0-9]", "");
    }

    private static Function<Map<String, Object>, Double> field(String path) {
        return raw -> OeeCalculator.number(path(raw, path));
    }

    private static Double num(Map<String, Object> source, String path) {
        return OeeCalculator.number(path(source, path));
    }

    static Object path(Map<String, Object> source, String path) {
        Object current = source;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> m)) return null;
            current = m.get(part);
        }
        return current;
    }

    static Long millis(Object value) {
        return millis(value, ZoneId.of("UTC"));
    }

    /** Machine report timestamps without an offset are plant-local wall-clock times. */
    static Long millis(Object value, ZoneId zone) {
        if (value instanceof java.util.Date date) return date.getTime();
        if (value instanceof Instant instant) return instant.toEpochMilli();
        if (value instanceof Number number) return number.longValue();
        String text = OeeCalculator.text(value).replaceFirst(" ", "T");
        if (text.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?$")) {
            try {
                return java.time.LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli();
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return OeeCalculator.parseMillis(text);
    }

    private static Double firstNumber(Object value, Double fallback) {
        Double number = OeeCalculator.number(value);
        return number == null ? fallback : number;
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            String text = OeeCalculator.text(value);
            if (!text.isEmpty()) return text;
        }
        return "";
    }

    static Double round(Double value) {
        return value == null || !Double.isFinite(value) ? null : Math.round(value * 1000.0) / 1000.0;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }
}
