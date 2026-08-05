import type {
  AlarmEventRecord,
  BatchSummary,
  CppRecord,
} from "../schemas/reports.schema";

interface ExportPayload {
  equipmentId: string;
  batchSummaries: BatchSummary[];
  cppRecords: CppRecord[];
  alarmEventRecords: AlarmEventRecord[];
}

const text = (value: unknown, fallback = "-") => {
  if (value === undefined || value === null) return fallback;
  const normalized = String(value).trim();
  return normalized.length > 0 ? normalized : fallback;
};

const normalizeKey = (value: unknown) =>
  String(value ?? "").toLowerCase().replace(/[^a-z0-9]/g, "");

const toNumberSafe = (value: unknown): number | null => {
  const numeric = Number.parseFloat(String(value));
  return Number.isFinite(numeric) ? numeric : null;
};

const parseDate = (value: unknown) => {
  const date = new Date(String(value ?? ""));
  return Number.isNaN(date.getTime()) ? null : date;
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

const formatTime = (value: unknown) => {
  const date = parseDate(value);
  if (!date) return "-";
  return new Intl.DateTimeFormat("en-IN", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: true,
  }).format(date);
};

const isAlarmRecord = (record: AlarmEventRecord) =>
  text(record.event?.eventCategory, "").toUpperCase() === "ALARM";

const toRows = (records: Array<Record<string, unknown>>) =>
  records.map((record) => {
    const normalized: Record<string, string | number | boolean | null> = {};
    Object.entries(record).forEach(([key, value]) => {
      if (value === undefined) return;
      if (value === null) {
        normalized[key] = null;
        return;
      }
      if (
        typeof value === "string" ||
        typeof value === "number" ||
        typeof value === "boolean"
      ) {
        normalized[key] = value;
        return;
      }
      normalized[key] = String(value);
    });
    return normalized;
  });

const sortByObservedAtDesc = <T,>(
  rows: T[],
  getDateValue: (row: T) => unknown,
) =>
  [...rows].sort(
    (a, b) =>
      (parseDate(getDateValue(b))?.getTime() ?? 0) -
      (parseDate(getDateValue(a))?.getTime() ?? 0),
  );

const getCppMetricHeaders = (records: CppRecord[]) => {
  const set = new Set<string>();

  records.forEach((record) => {
    Object.keys(record.metrics ?? {}).forEach((key) => {
      if (normalizeKey(key) !== "batchsize") {
        set.add(key);
      }
    });
  });

  return Array.from(set);
};

export async function exportEquipmentReportAsExcel({
  equipmentId,
  batchSummaries,
  cppRecords,
  alarmEventRecords,
}: ExportPayload) {
  const XLSX = await import("xlsx");
  const workbook = XLSX.utils.book_new();

  const batchRows = toRows(
    sortByObservedAtDesc(batchSummaries, (row) => row.updatedAt ?? row.batchEndAt).map(
      (row) => ({
        equipmentId: row.equipmentId ?? equipmentId,
        batchNo: text(row.batchNo),
        lotNo: text(row.lotNo),
        productName: text(row.productName),
        batchStatus: text(row.batchStatus),
        batchStartAt: text(row.batchStartAt),
        batchEndAt: text(row.batchEndAt),
        cppRecordCount: toNumberSafe(row.cppRecordCount) ?? row.cppRecordCount ?? "-",
        alarmCount: toNumberSafe(row.alarmCount) ?? row.alarmCount ?? "-",
        eventCount: toNumberSafe(row.eventCount) ?? row.eventCount ?? "-",
        plantId: text(row.plantId),
        areaId: text(row.areaId),
        updatedAt: text(row.updatedAt),
      }),
    ),
  );

  const cppMetricHeaders = getCppMetricHeaders(cppRecords);
  const cppRows = toRows(
    sortByObservedAtDesc(cppRecords, (row) => row.observedAt).map((row) => {
      const record: Record<string, unknown> = {
        observedAt: text(row.observedAt),
        date: formatDate(row.observedAt),
        time: formatTime(row.observedAt),
        batchNo: text(row.meta?.batchNo),
        sourceTable: text(row.source?.tableName),
      };

      cppMetricHeaders.forEach((metricKey) => {
        record[metricKey] = row.metrics?.[metricKey] ?? "-";
      });

      return record;
    }),
  );

  const alarmRows = toRows(
    sortByObservedAtDesc(
      alarmEventRecords.filter(isAlarmRecord),
      (row) => row.eventAt,
    ).map((row) => ({
      eventAt: text(row.eventAt),
      date: formatDate(row.eventAt),
      time: formatTime(row.eventAt),
      batchNo: text(row.meta?.batchNo),
      metric: text(row.event?.eventCode ?? row.event?.eventText),
      alarm: text(row.event?.eventText),
      severity: text(row.event?.severity),
      status: text(row.event?.eventState),
      acknowledgedBy: text(row.event?.acknowledgedBy),
      acknowledgedAt: text(row.event?.acknowledgedAt),
      source: text(row.source?.tableName),
      ingestedAt: text(row.ingestedAt),
    })),
  );

  const eventRows = toRows(
    sortByObservedAtDesc(
      alarmEventRecords.filter((row) => !isAlarmRecord(row)),
      (row) => row.eventAt,
    ).map((row) => ({
      eventAt: text(row.eventAt),
      date: formatDate(row.eventAt),
      time: formatTime(row.eventAt),
      batchNo: text(row.meta?.batchNo),
      metric: text(row.event?.eventCode ?? row.event?.eventText),
      eventType: text(row.event?.eventCategory),
      severity: text(row.event?.severity),
      source: text(row.source?.tableName),
      description: text(row.event?.eventText),
      ingestedAt: text(row.ingestedAt),
    })),
  );

  XLSX.utils.book_append_sheet(
    workbook,
    XLSX.utils.json_to_sheet(batchRows),
    "Batch Details",
  );
  XLSX.utils.book_append_sheet(
    workbook,
    XLSX.utils.json_to_sheet(cppRows),
    "CPP",
  );
  XLSX.utils.book_append_sheet(
    workbook,
    XLSX.utils.json_to_sheet(alarmRows),
    "Alarms",
  );
  XLSX.utils.book_append_sheet(
    workbook,
    XLSX.utils.json_to_sheet(eventRows),
    "Events",
  );

  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  XLSX.writeFile(workbook, `${equipmentId}-report-${timestamp}.xlsx`);
}
