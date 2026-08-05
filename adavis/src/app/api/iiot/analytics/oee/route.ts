import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { serverApiClient } from "@/api/server-client";
import { SERVER_API_CONFIG } from "@/api/server-config";
import type { BackendApiResponse } from "@/api/types";
import { SELECTED_PLANT_HEADER } from "@/utils/plantSelection";
import { AUTH_COOKIE_NAMES } from "@/features/auth/api/auth.server";
import {
  alarmEventRecordListSchema,
  batchSummaryListSchema,
  type AlarmEventRecord,
  type BatchSummary,
} from "@/features/iiot/equipment/schemas/reports.schema";
import type { OeeAnalyticsPayload } from "@/features/iiot/equipment/schemas/reports.schema";

const TARGET_CPP_PER_HOUR = 120;

const downtimePalette: Record<string, { color: string; gradientTo: string; legendOrder: number }> = {
  "Equipment Failure": { color: "#2FB1A6", gradientTo: "#89D4CD", legendOrder: 1 },
  "Minor Stoppage": { color: "#3F7ED4", gradientTo: "#5A8FE0", legendOrder: 2 },
  Changeover: { color: "#FF8588", gradientTo: "#EF646E", legendOrder: 3 },
  Cleaning: { color: "#806BDF", gradientTo: "#B49AF8", legendOrder: 4 },
  Others: { color: "#9FA3A6", gradientTo: "#B8BBBD", legendOrder: 5 },
};

const shiftColors = {
  "Shift 1": { color: "#2FB1A6", gradientTo: "#8FD1CA" },
  "Shift 2": { color: "#7C63D9", gradientTo: "#A893F5" },
  "Shift 3": { color: "#145AA9", gradientTo: "#4F8FDF" },
} as const;

const topLossPalette = {
  "Speed Losses": "#DDA647",
  "Minor Stops": "#9D7AF4",
  "Quality Losses": "#FF929C",
  "Startup Losses": "#5E9DEE",
} as const;

const clamp = (value: number, min: number, max: number) =>
  Math.min(max, Math.max(min, value));

const text = (value: unknown) => String(value ?? "").trim();

const toNumber = (value: unknown) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
};

const parseDate = (value: unknown) => {
  const date = new Date(String(value ?? ""));
  return Number.isNaN(date.getTime()) ? null : date;
};

const formatDayMonth = (value: unknown) => {
  const date = parseDate(value);
  if (!date) return "-";
  return new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short" }).format(date);
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

const parseDateRange = (
  value: string | null,
  customStart: string | null,
  customEnd: string | null,
) => {
  if (!value || value === "Select Date Range") return null;

  const now = new Date();
  if (value === "Today") {
    const start = new Date(now);
    start.setHours(0, 0, 0, 0);
    return { start, end: now };
  }

  if (value === "Last 7 Days") {
    const start = new Date(now);
    start.setDate(start.getDate() - 6);
    start.setHours(0, 0, 0, 0);
    return { start, end: now };
  }

  if (value === "Last 1 Month") {
    const start = new Date(now);
    start.setMonth(start.getMonth() - 1);
    start.setHours(0, 0, 0, 0);
    return { start, end: now };
  }

  if (value === "Last 3 Months") {
    const start = new Date(now);
    start.setMonth(start.getMonth() - 3);
    start.setHours(0, 0, 0, 0);
    return { start, end: now };
  }

  if (value === "Specific Range") {
    const start = parseDate(customStart);
    const end = parseDate(customEnd);
    if (!start || !end) return null;
    start.setHours(0, 0, 0, 0);
    end.setHours(23, 59, 59, 999);
    return { start, end };
  }

  const [rawStart, rawEnd] = value.split("to").map((part) => part.trim());
  const parseLegacy = (input: string) => {
    const [day, month, year] = input.split("-").map((part) => Number(part));
    if (!day || !month || !year) return null;
    const fullYear = year < 100 ? 2000 + year : year;
    const date = new Date(fullYear, month - 1, day);
    return Number.isNaN(date.getTime()) ? null : date;
  };

  const start = parseLegacy(rawStart);
  const end = parseLegacy(rawEnd);
  if (!start || !end) return null;
  end.setHours(23, 59, 59, 999);
  return { start, end };
};

const getBatchReferenceDate = (batch: BatchSummary) =>
  parseDate(batch.batchEndAt ?? batch.updatedAt ?? batch.batchStartAt);

const getEventReferenceDate = (record: AlarmEventRecord) =>
  parseDate(record.eventAt ?? record.ingestedAt ?? record.source?.observedAt);

const eventDurationMinutes = (record: AlarmEventRecord) => {
  const direct = toNumber(record.event?.durationMinutes ?? record.event?.downtimeMinutes);
  if (direct > 0) return direct;

  const metaDuration = toNumber(record.meta?.durationMinutes ?? record.meta?.downtimeMinutes);
  if (metaDuration > 0) return metaDuration;

  const severity = text(record.event?.severity).toUpperCase();
  if (severity === "CRITICAL" || severity === "HIGH") return 15;
  if (severity === "WARNING" || severity === "MEDIUM") return 8;
  return 5;
};

const eventCategory = (record: AlarmEventRecord) => {
  const raw = text(
    record.event?.downtimeCategory ??
      record.event?.eventCategory ??
      record.event?.eventType ??
      record.event?.eventText ??
      record.meta?.category,
  ).toLowerCase();

  if (raw.includes("failure") || raw.includes("breakdown") || raw.includes("fault")) {
    return "Equipment Failure";
  }
  if (raw.includes("minor") || raw.includes("stop")) return "Minor Stoppage";
  if (raw.includes("changeover") || raw.includes("change over")) return "Changeover";
  if (raw.includes("clean")) return "Cleaning";
  return "Others";
};

type ComputedBatch = {
  batch: BatchSummary;
  runtimeHours: number;
  availability: number;
  performance: number;
  quality: number;
  oee: number;
};

const computeBatch = (batch: BatchSummary): ComputedBatch => {
  const start = parseDate(batch.batchStartAt);
  const end = parseDate(batch.batchEndAt) ?? new Date();
  const runtimeHours =
    start === null ? 0 : clamp((end.getTime() - start.getTime()) / 3_600_000, 0, 24 * 30);

  const cpp = toNumber(batch.cppRecordCount);
  const alarms = toNumber(batch.alarmCount);
  const events = toNumber(batch.eventCount);

  const downtimeHours = alarms * 0.08 + events * 0.04;
  const availability = runtimeHours > 0
    ? clamp((runtimeHours / (runtimeHours + downtimeHours)) * 100, 0, 100)
    : 0;
  const performance = runtimeHours > 0
    ? clamp(((cpp / runtimeHours) / TARGET_CPP_PER_HOUR) * 100, 0, 100)
    : 0;
  const quality = cpp > 0
    ? clamp(((Math.max(cpp - (alarms * 2 + events), 0)) / cpp) * 100, 0, 100)
    : 0;
  const oee = (availability * performance * quality) / 10_000;

  return {
    batch,
    runtimeHours,
    availability,
    performance,
    quality,
    oee,
  };
};

const inferShiftLabel = (value: unknown) => {
  const date = parseDate(value);
  if (!date) return "Shift 1";
  const hour = date.getHours();
  if (hour >= 6 && hour < 14) return "Shift 1";
  if (hour >= 14 && hour < 22) return "Shift 2";
  return "Shift 3";
};

const errorResponse = (status: number, message: string, errorCode: string) =>
  NextResponse.json(
    {
      success: false,
      message,
      errorCode,
      timestamp: new Date().toISOString(),
    } satisfies BackendApiResponse<never>,
    { status },
  );

export async function GET(request: Request) {
  const accessToken = (await cookies()).get(AUTH_COOKIE_NAMES.accessToken)?.value;

  if (!accessToken) {
    return errorResponse(401, "Not authenticated.", "UNAUTHORIZED");
  }

  const requestUrl = new URL(request.url);
  const equipmentId = text(requestUrl.searchParams.get("equipmentId"));
  const dateRange = requestUrl.searchParams.get("dateRange");
  const startDate = requestUrl.searchParams.get("startDate");
  const endDate = requestUrl.searchParams.get("endDate");
  const limit = Math.max(1, Math.min(1000, Number(requestUrl.searchParams.get("limit") ?? 300)));

  if (!equipmentId || equipmentId === "Select ID") {
    return errorResponse(400, "equipmentId is required.", "BAD_REQUEST");
  }

  const range = parseDateRange(dateRange, startDate, endDate);
  const query = new URLSearchParams({ equipmentId, limit: String(limit) }).toString();
  const selectedPlantId = request.headers.get(SELECTED_PLANT_HEADER)?.trim();
  const headers = {
    Authorization: `Bearer ${accessToken}`,
    ...(selectedPlantId ? { [SELECTED_PLANT_HEADER]: selectedPlantId } : {}),
  };

  try {
    const [batchResponse, eventResponse] = await Promise.all([
      serverApiClient<unknown>(
        SERVER_API_CONFIG.iiotServiceUrl,
        `/api/v1/iiot/reports/batch-summary?${query}`,
        { headers },
      ),
      serverApiClient<unknown>(
        SERVER_API_CONFIG.iiotServiceUrl,
        `/api/v1/iiot/reports/alarm-events?${query}`,
        { headers },
      ),
    ]);

    if (!batchResponse.result.success || !eventResponse.result.success) {
      return errorResponse(502, "Failed to retrieve IIOT analytics data.", "IIOT_UPSTREAM_FAILED");
    }

    const parsedBatches = batchSummaryListSchema.safeParse(batchResponse.result.data ?? []);
    const parsedEvents = alarmEventRecordListSchema.safeParse(eventResponse.result.data ?? []);

    if (!parsedBatches.success || !parsedEvents.success) {
      return errorResponse(502, "Invalid IIOT analytics response.", "IIOT_INVALID_RESPONSE");
    }

    const filteredBatches = parsedBatches.data
      .filter((batch) => {
        const reference = getBatchReferenceDate(batch);
        if (!reference) return false;
        if (!range) return true;
        return reference >= range.start && reference <= range.end;
      })
      .sort((left, right) => {
        const leftTime = getBatchReferenceDate(left)?.getTime() ?? 0;
        const rightTime = getBatchReferenceDate(right)?.getTime() ?? 0;
        return leftTime - rightTime;
      });

    const filteredEvents = parsedEvents.data.filter((record) => {
      const reference = getEventReferenceDate(record);
      if (!reference) return false;
      if (!range) return true;
      return reference >= range.start && reference <= range.end;
    });

    const computed = filteredBatches.map(computeBatch);
    const weight = computed.reduce((sum, item) => sum + Math.max(item.runtimeHours, 1), 0);
    const weightedAverage = (selector: (item: ComputedBatch) => number) =>
      weight === 0
        ? 0
        : computed.reduce(
            (sum, item) => sum + selector(item) * Math.max(item.runtimeHours, 1),
            0,
          ) / weight;

    const availability = weightedAverage((item) => item.availability);
    const performance = weightedAverage((item) => item.performance);
    const quality = weightedAverage((item) => item.quality);
    const overallOee = (availability * performance * quality) / 10_000;

    const trendPoints = computed.map((item) => ({
      label: formatDayMonth(item.batch.batchEndAt ?? item.batch.updatedAt ?? item.batch.batchStartAt),
      value: Number(item.oee.toFixed(1)),
    }));

    const trendDelta = trendPoints.length >= 2
      ? trendPoints[trendPoints.length - 1].value - trendPoints[trendPoints.length - 2].value
      : 0;

    const downtimeByCategory = {
      "Equipment Failure": 0,
      "Minor Stoppage": 0,
      Changeover: 0,
      Cleaning: 0,
      Others: 0,
    };

    filteredEvents.forEach((record) => {
      const category = eventCategory(record);
      const hours = eventDurationMinutes(record) / 60;
      downtimeByCategory[category] += hours;
    });

    const totalDowntimeHours = Object.values(downtimeByCategory).reduce((sum, item) => sum + item, 0);

    const downtimeSegments = Object.entries(downtimeByCategory).map(([label, hours]) => {
      const palette = downtimePalette[label];
      return {
        label,
        value: Number(hours.toFixed(2)),
        displayValue: `${hours.toFixed(1)} hrs`,
        color: palette.color,
        gradientTo: palette.gradientTo,
        legendOrder: palette.legendOrder,
      };
    });

    const shiftAccumulator: Record<"Shift 1" | "Shift 2" | "Shift 3", { total: number; count: number }> = {
      "Shift 1": { total: 0, count: 0 },
      "Shift 2": { total: 0, count: 0 },
      "Shift 3": { total: 0, count: 0 },
    };

    computed.forEach((item) => {
      const shift = inferShiftLabel(item.batch.batchEndAt ?? item.batch.batchStartAt) as "Shift 1" | "Shift 2" | "Shift 3";
      shiftAccumulator[shift].total += item.oee;
      shiftAccumulator[shift].count += 1;
    });

    const shiftComparison = (Object.keys(shiftAccumulator) as Array<"Shift 1" | "Shift 2" | "Shift 3">).map((label) => ({
      label,
      value: Number(
        (shiftAccumulator[label].count === 0
          ? 0
          : shiftAccumulator[label].total / shiftAccumulator[label].count).toFixed(1),
      ),
      color: shiftColors[label].color,
      gradientTo: shiftColors[label].gradientTo,
    }));

    const minorStopShare =
      totalDowntimeHours > 0
        ? (downtimeByCategory["Minor Stoppage"] / totalDowntimeHours) * 100
        : 0;
    const changeoverCleaningShare =
      totalDowntimeHours > 0
        ? ((downtimeByCategory.Changeover + downtimeByCategory.Cleaning) / totalDowntimeHours) * 100
        : 0;

    const topBreakdownLosses = [
      { label: "Speed Losses", value: clamp(100 - performance, 0, 100), color: topLossPalette["Speed Losses"] },
      { label: "Minor Stops", value: clamp(minorStopShare, 0, 100), color: topLossPalette["Minor Stops"] },
      { label: "Quality Losses", value: clamp(100 - quality, 0, 100), color: topLossPalette["Quality Losses"] },
      { label: "Startup Losses", value: clamp((100 - availability) * 0.6 + changeoverCleaningShare, 0, 100), color: topLossPalette["Startup Losses"] },
    ].map((item) => ({
      ...item,
      value: Number(item.value.toFixed(1)),
    }));

    const summaryRows = computed
      .slice(-20)
      .reverse()
      .map((item) => ({
        date: formatDayMonth(item.batch.batchEndAt ?? item.batch.updatedAt ?? item.batch.batchStartAt),
        productName: text(item.batch.productName) || "-",
        batchNo: text(item.batch.batchNo) || "-",
        startTime: formatTime(item.batch.batchStartAt),
        endTime: formatTime(item.batch.batchEndAt),
        runTimePercent: `${Math.round(item.availability)}%`,
        runTimeHrs: `${item.runtimeHours.toFixed(1)} hrs`,
      }));

    const payload: OeeAnalyticsPayload = {
      metrics: {
        overallOee,
        availability,
        performance,
        quality,
        trendDelta,
      },
      trendPoints,
      downtimeSegments,
      shiftComparison,
      topBreakdownLosses,
      summaryRows,
    };

    return NextResponse.json(
      {
        success: true,
        message: "OEE analytics generated successfully.",
        data: payload,
        timestamp: new Date().toISOString(),
      } satisfies BackendApiResponse<OeeAnalyticsPayload>,
      { status: 200 },
    );
  } catch {
    return errorResponse(503, "The IIOT service is unavailable.", "IIOT_SERVICE_UNAVAILABLE");
  }
}
