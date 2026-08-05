import type { CompactEventItem } from "@/components/ui/CompactEventsCard";
import type {
  AlarmEventRecord,
  BatchSummary,
  CriticalParameterLimit,
  CppRecord,
  EquipmentLiveStatus,
} from "../schemas/reports.schema";
import type {
  AlarmRow,
  AlarmSeverity,
  AlarmStatus,
  EventRow,
  EventSeverity,
  EventType,
  ParameterRow,
  ParameterStatus,
} from "./types";
import type { EquipmentRow, EquipmentStatus } from "./equipment-overview";

const metricLabels: Record<string, string> = {
  impellerA: "Impeller Current",
  chopperA: "Chopper Current",
  bedTemp: "Bed Temperature",
  batchSize: "Batch Size",
};

const metricUnits: Record<string, string> = {
  impellerA: "A",
  chopperA: "A",
  bedTemp: "°C",
  batchSize: "KG",
};

const normalizeKey = (value: unknown) =>
  String(value ?? "")
    .toLowerCase()
    .replace(/[^a-z0-9]/g, "");

const toNumberSafe = (value: unknown) => {
  const numeric = Number.parseFloat(String(value));
  return Number.isFinite(numeric) ? numeric : null;
};

const findLimitValue = (limit: CriticalParameterLimit, keys: string[]) => {
  for (const key of keys) {
    const candidate = toNumberSafe(limit[key as keyof CriticalParameterLimit]);
    if (candidate !== null) return candidate;
  }
  return null;
};

const buildConfiguredLimitMap = (limits: CriticalParameterLimit[]) => {
  const map = new Map<string, CriticalParameterLimit>();
  limits.forEach((limit) => {
    if (limit.isActive === false) return;
    const keys = [limit.parameterCode, limit.parameterType, limit.parameterId]
      .map((value) => normalizeKey(value))
      .filter(Boolean);

    keys.forEach((key) => map.set(key, limit));
  });
  return map;
};

const text = (value: unknown, fallback = "-") => {
  if (value === undefined || value === null || value === "") return fallback;
  return String(value);
};

const titleCase = (value: string) =>
  value
    .toLowerCase()
    .split(/[\s_-]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");

const parseDate = (value: unknown) => {
  if (!value) return undefined;
  const date = new Date(String(value));
  return Number.isNaN(date.getTime()) ? undefined : date;
};

export const formatDateTime = (value: unknown) => {
  const date = parseDate(value);
  if (!date) return "-";

  return new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
    hour12: true,
  }).format(date);
};

const formatDate = (value: unknown) => {
  const date = parseDate(value);
  if (!date) return "-";

  return new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  }).format(date);
};

const formatTimeMultiline = (value: unknown) => {
  const formatted = formatDateTime(value);
  if (formatted === "-") return "-";
  const [date, time] = formatted.split(", ");
  return `${date},\n${time ?? ""}`;
};

const formatTime = (value: unknown) => {
  const date = parseDate(value);
  if (!date) return "-";
  return new Intl.DateTimeFormat("en-IN", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: true,
  }).format(date);
};

export const normalizeEquipmentStatus = (value: unknown): EquipmentStatus => {
  const state = text(value, "UNKNOWN").toUpperCase();

  if (state.includes("RUN")) return "Running";
  if (state.includes("MAINT")) return "Maintenance";
  if (state.includes("COMM") || state.includes("ERROR")) {
    return "Communication Error";
  }
  if (state.includes("OFF") || state.includes("UNKNOWN")) return "Offline";
  return "Idle";
};

export const normalizeLiveStatusToEquipmentRow = (
  status: EquipmentLiveStatus,
): EquipmentRow => ({
  id: status.equipmentId,
  tenantId: text(status.tenantId),
  plantId: text(status.plantId),
  blockId: text(status.blockId),
  areaId: text(status.areaId),
  roomNo: text(status.roomNo ?? status.roomId),
  status: normalizeEquipmentStatus(status.currentState),
  stateReason: text(status.stateReason ?? status.currentState),
  lastBatchNo: text(status.lastBatchNo),
  lastLotNo: text(status.lastLotNo),
  lastActive: formatDateTime(status.lastEventAt ?? status.heartbeatAt ?? status.updatedAt),
});

export const summarizeEquipmentRows = (rows: EquipmentRow[]) => {
  const counts = {
    all: rows.length,
    running: 0,
    idle: 0,
    "communication-error": 0,
    maintenance: 0,
    offline: 0,
  };

  rows.forEach((row) => {
    if (row.status === "Running") counts.running += 1;
    if (row.status === "Idle") counts.idle += 1;
    if (row.status === "Communication Error") counts["communication-error"] += 1;
    if (row.status === "Maintenance") counts.maintenance += 1;
    if (row.status === "Offline") counts.offline += 1;
  });

  return counts;
};

const metricToStatus = (value: unknown): ParameterStatus => {
  const numeric = Number.parseFloat(String(value));
  if (!Number.isFinite(numeric)) return "Normal";
  if (numeric >= 9 || numeric <= 1) return "Critical";
  if (numeric >= 8 || numeric <= 2) return "Warning";
  return "Normal";
};

const metricToStatusByLimits = (
  value: unknown,
  min: number | null,
  max: number | null,
): ParameterStatus => {
  const numeric = toNumberSafe(value);
  if (numeric === null || min === null || max === null || min >= max) {
    return metricToStatus(value);
  }

  const range = max - min;
  const warningBand = range * 0.1;
  const warningMin = min + warningBand;
  const warningMax = max - warningBand;

  if (numeric < min || numeric > max) return "Critical";
  if (numeric < warningMin || numeric > warningMax) return "Warning";
  return "Normal";
};

export const normalizeCppToParameterRows = (
  records: CppRecord[],
  limits: CriticalParameterLimit[] = [],
  masterParameterLabels: string[] = [],
): ParameterRow[] => {
  void masterParameterLabels;
  const sorted = [...records].sort(
    (a, b) =>
      (parseDate(b.observedAt)?.getTime() ?? 0) -
      (parseDate(a.observedAt)?.getTime() ?? 0),
  );
  const configuredLimits = buildConfiguredLimitMap(limits);

  return sorted.map((record) => {
    const metrics = record.metrics ?? {};
    const metricEntries = Object.entries(metrics).filter(
      ([key]) => normalizeKey(key) !== "batchsize",
    );

    const evaluated = metricEntries.map(([key, value]) => {
      const lookupKey = normalizeKey(key);
      const configured = configuredLimits.get(lookupKey);
      const min = configured
        ? findLimitValue(configured, ["lowerLimit", "minValue"])
        : null;
      const max = configured
        ? findLimitValue(configured, ["upperLimit", "maxValue"])
        : null;
      const status = metricToStatusByLimits(value, min, max);
      const numeric = toNumberSafe(value);
      const rangeText =
        min !== null && max !== null ? `${min} - ${max}` : "2 - 9";
      return {
        normalizedKey: normalizeKey(key),
        key,
        label: metricLabels[key] ?? titleCase(key),
        value: text(value),
        unit: metricUnits[key] ?? "",
        status,
        range: rangeText,
        numeric,
      };
    });

    const overallStatus: ParameterStatus = evaluated.some(
      (item) => item.status === "Critical",
    )
      ? "Critical"
      : evaluated.some((item) => item.status === "Warning")
        ? "Warning"
        : "Normal";

    const valueText = evaluated
      .map((item) => `${item.label}: ${item.value}${item.unit ? ` ${item.unit}` : ""}`)
      .join(" | ");
    const rangeText = evaluated
      .map((item) => `${item.label}: ${item.range}`)
      .join(" | ");
    const unitText = Array.from(
      new Set(evaluated.map((item) => item.unit).filter(Boolean)),
    ).join(", ");

    const firstNumeric = evaluated.find((item) => item.numeric !== null)?.numeric;

    const evaluatedByNormalizedLabel = new Map(
      evaluated.map((item) => [normalizeKey(item.label), item]),
    );
    const dynamicLabels = evaluated.map((item) => item.label);

    const metricValues: Record<string, string> = {};
    const metricStatuses: Record<string, ParameterStatus> = {};
    dynamicLabels.forEach((label) => {
      const metric = evaluatedByNormalizedLabel.get(normalizeKey(label));
      metricValues[label] = metric
        ? `${metric.value}${metric.unit ? ` ${metric.unit}` : ""}`
        : "-";
      metricStatuses[label] = metric?.status ?? "Normal";
    });

    const meta = (record.meta ?? {}) as Record<string, unknown>;
    const source = (record.source ?? {}) as Record<string, unknown>;

    return {
      observedAtIso: text(record.observedAt, ""),
      date: formatDate(record.observedAt),
      time: formatTime(record.observedAt),
      metricValues,
      metricKeys: evaluated.map((item) => item.key),
      metric: text(source.tableName, "CPP"),
      params: `Batch ${text(meta.batchNo, "-")}`,
      impellerCurrent: metricValues["Impeller Current"] ?? "-",
      chopperCurrent: metricValues["Chopper Current"] ?? "-",
      bedTemperature: metricValues["Bed Temperature"] ?? "-",
      mode: metricValues["Mode"] ?? "-",
      processStatus: metricValues["Status"] ?? "-",
      cycle: metricValues["Cycle"] ?? "-",
      impellerb: metricValues["Impellerb"] ?? "-",
      granulationtime: metricValues["Granulationtime"] ?? "-",
      metricStatuses,
      parameterKey: evaluated[0]?.key ?? "",
      parameter: "All Metrics",
      currentValue: valueText || "-",
      unit: unitText || "-",
      status: overallStatus,
      range: rangeText || "-",
      rangePosition:
        firstNumeric !== undefined && firstNumeric !== null
          ? Math.min(92, Math.max(8, firstNumeric * 10))
          : 50,
      lastUpdated: formatTime(record.observedAt),
    };
  });
};

export const normalizeCppToTrendPoints = (records: CppRecord[]) => {
  return normalizeCppToTrendPointsForParameter(records);
};

export const normalizeCppToTrendPointsForParameter = (
  records: CppRecord[],
  parameterKey?: string,
) => {
  const preferredKey = parameterKey?.trim();
  const numericKey =
    preferredKey && preferredKey.length > 0
      ? preferredKey
      : records
          .flatMap((record) => Object.keys(record.metrics ?? {}))
          .find(
            (key) =>
              typeof records.find((record) => record.metrics?.[key])?.metrics?.[
                key
              ] === "number",
          ) ?? "bedTemp";

  return [...records]
    .sort(
      (a, b) =>
        (parseDate(a.observedAt)?.getTime() ?? 0) -
        (parseDate(b.observedAt)?.getTime() ?? 0),
    )
    .slice(-16)
    .map((record) => ({
      label: formatTime(record.observedAt),
      value: Number(record.metrics?.[numericKey] ?? 0),
    }))
    .filter((point) => Number.isFinite(point.value));
};

const normalizeSeverity = (value: unknown): AlarmSeverity => {
  const severity = text(value, "MEDIUM").toUpperCase();
  return severity === "HIGH" || severity === "CRITICAL"
    ? "Critical"
    : "Warning";
};

const normalizeEventSeverity = (value: unknown): EventSeverity => {
  const severity = text(value, "LOW").toUpperCase();
  return severity === "LOW" || severity === "INFO" ? "Normal" : "Warning";
};

const normalizeAlarmStatus = (value: unknown): AlarmStatus => {
  const state = text(value, "OPEN").toUpperCase();
  return state === "OPEN" || state === "ACTIVE" ? "Active" : "Acknowledged";
};

export const normalizeAlarmRows = (
  records: AlarmEventRecord[],
): AlarmRow[] =>
  records
    .filter((record) => text(record.event?.eventCategory, "").toUpperCase() === "ALARM")
    .map((record) => ({
      id: text((record as Record<string, unknown>)._id, ""),
      occurredAtIso: text(record.eventAt, ""),
      date: formatDate(record.eventAt),
      metric: text(record.event?.eventCode ?? record.event?.eventText),
      params: text(record.source?.tableName),
      batchNo: text(record.meta?.batchNo),
      time: formatTimeMultiline(record.eventAt),
      severity: normalizeSeverity(record.event?.severity),
      alarm: text(record.event?.eventText),
      currentValue: "-",
      threshold: "-",
      status: normalizeAlarmStatus(record.event?.eventState),
      acknowledgedBy: text(record.event?.acknowledgedBy),
      acknowledgedAt: formatDateTime(record.event?.acknowledgedAt),
      requiresAcknowledge: normalizeAlarmStatus(record.event?.eventState) === "Active",
    }));

const normalizeEventType = (value: unknown): EventType => {
  const eventCode = text(value, "").toUpperCase();
  return eventCode.includes("THRESHOLD") ? "Threshold Crossed" : "Status Updated";
};

export const normalizeEventRows = (
  records: AlarmEventRecord[],
): EventRow[] =>
  records
    .filter((record) => text(record.event?.eventCategory, "").toUpperCase() !== "ALARM")
    .map((record) => ({
      occurredAtIso: text(record.eventAt, ""),
      date: formatDate(record.eventAt),
      metric: text(record.event?.eventCode ?? record.event?.eventText),
      params: text(record.source?.tableName),
      batchNo: text(record.meta?.batchNo),
      time: formatTimeMultiline(record.eventAt),
      eventType: normalizeEventType(record.event?.eventCode),
      severity: normalizeEventSeverity(record.event?.severity),
      source: text(record.source?.tableName),
      description: text(record.event?.eventText),
      acknowledgedBy: "-",
    }));

export const normalizeBatchSummary = (summaries: BatchSummary[]) => {
  const latest = [...summaries]
    .sort(
      (a, b) =>
        (parseDate(b.batchEndAt ?? b.updatedAt)?.getTime() ?? 0) -
        (parseDate(a.batchEndAt ?? a.updatedAt)?.getTime() ?? 0),
    )
    .at(0);

  return latest;
};

export const normalizeOutOfRangeEvents = (
  records: AlarmEventRecord[],
): CompactEventItem[] =>
  normalizeAlarmRows(records).slice(0, 4).map((alarm) => ({
    label: alarm.alarm,
    status: alarm.severity,
    value: alarm.time.split("\n").at(1) ?? "-",
  }));
