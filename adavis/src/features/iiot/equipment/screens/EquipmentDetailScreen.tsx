"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { Snackbar } from "@/components/ui";
import {
  acknowledgeAlarmEvent,
  getAlarmEventData,
  getBatchSummary,
  getCriticalParameterLimits,
  getCppData,
  getEquipmentLiveStatus,
} from "../api/reports.api";
import EquipmentDetailHeader from "../components/EquipmentDetailHeader";
import AlarmsTab from "../tabs/AlarmsTab";
import EventsTab from "../tabs/EventsTab";
import ParametersTab from "../tabs/ParametersTab";
import TrendsTab from "../tabs/TrendsTab";
import type { TrendInsightItem } from "../tabs/TrendsTab";
import type {
  AlarmRow,
  DetailTab,
  EventRow,
  ParameterRow,
} from "../data/types";
import type { BatchSummary, EquipmentLiveStatus } from "../schemas/reports.schema";
import {
  normalizeAlarmRows,
  normalizeBatchSummary,
  normalizeCppToParameterRows,
  normalizeCppToTrendPointsForParameter,
  normalizeEquipmentStatus,
  normalizeEventRows,
} from "../data/report-normalizers";
import type { CompactEventItem } from "@/components/ui/CompactEventsCard";
import type { CppRecord, CriticalParameterLimit } from "../schemas/reports.schema";
import { exportEquipmentReportAsExcel } from "../utils/export-report";

interface EquipmentDetailScreenProps {
  equipmentId: string;
  activeTab?: DetailTab;
}

function toDisplayEquipmentId(equipmentId: string) {
  return decodeURIComponent(equipmentId).toUpperCase();
}

function formatSubtitleDateTime(value: string | null | undefined) {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "-" : date.toLocaleString("en-IN");
}

function normalizeKey(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]/g, "");
}

function titleCase(value: string) {
  return value
    .toLowerCase()
    .split(/[\s_-]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function isNumericMetricValue(value: unknown) {
  const numeric = Number.parseFloat(String(value));
  return Number.isFinite(numeric);
}

function parseObservedMs(value: unknown) {
  const date = new Date(String(value ?? ""));
  return Number.isNaN(date.getTime()) ? null : date.getTime();
}

function formatObservedTime(value: unknown) {
  const date = new Date(String(value ?? ""));
  if (Number.isNaN(date.getTime())) return "-";
  return new Intl.DateTimeFormat("en-IN", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: true,
  }).format(date);
}

function normalizedKey(value: unknown) {
  return String(value ?? "").toLowerCase().replace(/[^a-z0-9]/g, "");
}

function toNumber(value: unknown) {
  const numeric = Number.parseFloat(String(value));
  return Number.isFinite(numeric) ? numeric : null;
}

function resolveParameterLimits(
  limits: CriticalParameterLimit[],
  parameterKey: string,
) {
  const key = normalizedKey(parameterKey);
  const match = limits.find((limit) =>
    [limit.parameterCode, limit.parameterType, limit.parameterId]
      .map((candidate) => normalizedKey(candidate))
      .includes(key),
  );
  if (!match) return { min: null as number | null, max: null as number | null };

  const min = toNumber(match.lowerLimit ?? match.minValue);
  const max = toNumber(match.upperLimit ?? match.maxValue);
  return { min, max };
}

function classifyValue(
  value: number,
  min: number | null,
  max: number | null,
): "Normal" | "Warning" | "Critical" {
  if (min === null || max === null || min >= max) return "Normal";
  const range = max - min;
  const warningBand = range * 0.1;
  const warningMin = min + warningBand;
  const warningMax = max - warningBand;

  if (value < min || value > max) return "Critical";
  if (value < warningMin || value > warningMax) return "Warning";
  return "Normal";
}

const detailTabIds: DetailTab[] = ["parameters", "trends", "alarms", "events"];
const AUTO_REFRESH_INTERVAL_MS = 5 * 60 * 1000;

export default function EquipmentDetailScreen({
  equipmentId,
  activeTab = "parameters",
}: EquipmentDetailScreenProps) {
  const displayEquipmentId = toDisplayEquipmentId(equipmentId);
  const containerRef = useRef<HTMLElement>(null);
  const headerRef = useRef<HTMLDivElement>(null);
  const [isHeaderSolid, setIsHeaderSolid] = useState(false);
  const [visibleTab, setVisibleTab] = useState<DetailTab>(activeTab);
  const [liveStatus, setLiveStatus] = useState<EquipmentLiveStatus | null>(null);
  const [batchSummary, setBatchSummary] = useState<BatchSummary | undefined>();
  const [parameterRows, setParameterRows] = useState<ParameterRow[]>([]);
  const [trendPoints, setTrendPoints] = useState<Array<{ label: string; value: number }>>([]);
  const [alarmRows, setAlarmRows] = useState<AlarmRow[]>([]);
  const [eventRows, setEventRows] = useState<EventRow[]>([]);
  const [cppRecords, setCppRecords] = useState<CppRecord[]>([]);
  const [configuredLimits, setConfiguredLimits] = useState<CriticalParameterLimit[]>([]);
  const [selectedParameterKey, setSelectedParameterKey] = useState("");
  const [acknowledgingAlarmId, setAcknowledgingAlarmId] = useState<string | null>(null);
  const [outOfRangeEvents, setOutOfRangeEvents] = useState<CompactEventItem[]>([]);
  const [trendInsights, setTrendInsights] = useState<TrendInsightItem[]>([]);
  const [errorMessage, setErrorMessage] = useState("");
  const [autoRefreshEnabled, setAutoRefreshEnabled] = useState(false);

  const trendLabelByKey = useRef<Record<string, string>>({});

  const trendParameterOptions = Object.entries(trendLabelByKey.current).map(([, label]) => label);
  const selectedParameterLabel =
    trendLabelByKey.current[selectedParameterKey] ?? trendParameterOptions[0] ?? "";

  const detailSections: Array<{
    id: DetailTab;
    label: string;
    content: ReactNode;
  }> = [
    {
      id: "parameters",
      label: "Parameters",
      content: <ParametersTab rows={parameterRows} />,
    },
    {
      id: "trends",
      label: "Trends",
      content: (
        <TrendsTab
          points={trendPoints}
          events={outOfRangeEvents}
          insights={trendInsights}
          selectedParameter={selectedParameterLabel}
          selectedParameterKey={selectedParameterKey}
          parameterOptions={trendParameterOptions}
          onParameterChange={(parameter) => {
            const key = Object.entries(trendLabelByKey.current).find(
              ([, label]) => label === parameter,
            )?.[0];
            if (key) setSelectedParameterKey(key);
          }}
        />
      ),
    },
    {
      id: "alarms",
      label: "Alarms",
      content: (
        <AlarmsTab
          rows={alarmRows}
          acknowledgingId={acknowledgingAlarmId}
          onAcknowledge={async (row) => {
            if (!row.id || !row.requiresAcknowledge) return;

            try {
              setAcknowledgingAlarmId(row.id);
              await acknowledgeAlarmEvent(displayEquipmentId, row.id, {
                acknowledgedBy: "SUPER_ADMIN",
              });

              setAlarmRows((previous) =>
                previous.map((alarm) =>
                  alarm.id === row.id
                    ? {
                        ...alarm,
                        status: "Acknowledged",
                        acknowledgedBy: "SUPER_ADMIN",
                        acknowledgedAt: new Date().toISOString(),
                        requiresAcknowledge: false,
                      }
                    : alarm,
                ),
              );
            } catch (error) {
              setErrorMessage(
                error instanceof Error
                  ? error.message
                  : "Unable to acknowledge alarm.",
              );
            } finally {
              setAcknowledgingAlarmId(null);
            }
          }}
        />
      ),
    },
    { id: "events", label: "Events", content: <EventsTab rows={eventRows} /> },
  ];

  const loadEquipmentReport = useCallback(
    async (signal?: AbortSignal) => {
      const query = { limit: 100 };

      try {
        const [liveStatusResult, batchSummaries, limits] = await Promise.all([
          getEquipmentLiveStatus(displayEquipmentId, signal),
          getBatchSummary({ equipmentId: displayEquipmentId, limit: 20 }, signal),
          getCriticalParameterLimits({ equipmentId: displayEquipmentId }, signal),
        ]);

        if (signal?.aborted) return;

        setLiveStatus(liveStatusResult);

        const latestBatch = normalizeBatchSummary(batchSummaries);
        setBatchSummary(latestBatch);

        const limitsForEquipment = limits.filter((limit) => {
          const eq = typeof limit.equipmentId === "string" ? limit.equipmentId : "";
          return eq.toUpperCase() === displayEquipmentId;
        });
        setConfiguredLimits(limitsForEquipment);
        const labelsFromMaster = Array.from(
          new Set(
            limitsForEquipment
              .map((limit) =>
                String(
                  limit.parameterCode ??
                    limit.parameterType ??
                    limit.parameterId ??
                    "",
                ).trim(),
              )
              .filter(Boolean)
              .map((label) => titleCase(label)),
          ),
        );

        const batchScopedQuery = {
          ...query,
          ...(latestBatch?.batchNo ? { batchNo: latestBatch.batchNo } : {}),
        };

        const [cppData, alarmEvents] = await Promise.all([
          getCppData(displayEquipmentId, batchScopedQuery, signal),
          getAlarmEventData(displayEquipmentId, batchScopedQuery, signal),
        ]);

        if (signal?.aborted) return;

        setCppRecords(cppData);
        const rows = normalizeCppToParameterRows(
          cppData,
          limitsForEquipment,
          labelsFromMaster,
        );
        setParameterRows(rows);
        const metricKeySet = new Set(
          cppData
            .flatMap((record) => Object.keys(record.metrics ?? {}))
            .filter((key) => normalizeKey(key) !== "batchsize"),
        );
        const numericMetricKeySet = new Set(
          cppData.flatMap((record) =>
            Object.entries(record.metrics ?? {})
              .filter(
                ([key, value]) =>
                  normalizeKey(key) !== "batchsize" && isNumericMetricValue(value),
              )
              .map(([key]) => key),
          ),
        );

        const orderedTrendMetricKeys = [
          ...Array.from(numericMetricKeySet),
          ...Array.from(metricKeySet).filter((key) => !numericMetricKeySet.has(key)),
        ];

        trendLabelByKey.current = orderedTrendMetricKeys.reduce<Record<string, string>>((acc, key) => {
          acc[key] = titleCase(key);
          return acc;
        }, {});

        const defaultParameterKey =
          orderedTrendMetricKeys[0] ??
          rows[0]?.metricKeys?.[0] ??
          rows[0]?.parameterKey ??
          "";
        setSelectedParameterKey((previous) => previous || defaultParameterKey);
        setTrendPoints(normalizeCppToTrendPointsForParameter(cppData, defaultParameterKey));

        const normalizedAlarms = normalizeAlarmRows(alarmEvents);
        const normalizedEvents = normalizeEventRows(alarmEvents);
        setAlarmRows(
          latestBatch?.batchNo
            ? normalizedAlarms.filter((alarm) => alarm.batchNo === latestBatch.batchNo)
            : normalizedAlarms,
        );
        setEventRows(
          latestBatch?.batchNo
            ? normalizedEvents.filter((event) => event.batchNo === latestBatch.batchNo)
            : normalizedEvents,
        );
        setOutOfRangeEvents([]);
        setTrendInsights([]);
        setErrorMessage("");
      } catch (error) {
        if (signal?.aborted) return;
        setErrorMessage(
          error instanceof Error ? error.message : "Unable to load equipment report.",
        );
      }
    },
    [displayEquipmentId],
  );

  useEffect(() => {
    const controller = new AbortController();
    void loadEquipmentReport(controller.signal);
    return () => controller.abort();
  }, [loadEquipmentReport]);

  useEffect(() => {
    if (!autoRefreshEnabled) return;

    const intervalId = window.setInterval(() => {
      void loadEquipmentReport();
    }, AUTO_REFRESH_INTERVAL_MS);

    return () => window.clearInterval(intervalId);
  }, [autoRefreshEnabled, loadEquipmentReport]);

  useEffect(() => {
    if (!selectedParameterKey || cppRecords.length === 0) return;
    const sorted = [...cppRecords].sort(
      (a, b) => (parseObservedMs(a.observedAt) ?? 0) - (parseObservedMs(b.observedAt) ?? 0),
    );
    const points = normalizeCppToTrendPointsForParameter(sorted, selectedParameterKey);
    setTrendPoints(points);

    const { min, max } = resolveParameterLimits(configuredLimits, selectedParameterKey);
    type EvaluationItem = {
      observedAt: string | null | undefined;
      value: number;
      status: "Normal" | "Warning" | "Critical";
    };
    const evaluations = sorted.reduce<EvaluationItem[]>((acc, record) => {
      const numeric = toNumber(record.metrics?.[selectedParameterKey]);
      if (numeric === null) return acc;

      acc.push({
        observedAt: record.observedAt,
        value: numeric,
        status: classifyValue(numeric, min, max),
      });
      return acc;
    }, []);

    const warnings = evaluations.filter((item) => item.status === "Warning");
    const criticals = evaluations.filter((item) => item.status === "Critical");

    const avg = points.length
      ? points.reduce((sum, point) => sum + point.value, 0) / points.length
      : null;
    const minObserved = points.length ? Math.min(...points.map((point) => point.value)) : null;
    const maxObserved = points.length ? Math.max(...points.map((point) => point.value)) : null;

    const label = trendLabelByKey.current[selectedParameterKey] ?? selectedParameterKey;
    const insights: TrendInsightItem[] = [
      {
        color: "#38C172",
        title: `${label} average trend`,
        detail:
          avg === null
            ? "No numeric trend samples available"
            : `Average ${avg.toFixed(2)} | Observed range ${minObserved?.toFixed(2)} to ${maxObserved?.toFixed(2)}`,
      },
      {
        color: criticals.length > 0 ? "#FF5A5F" : "#E39A05",
        title: `${label} limit violations`,
        detail:
          min === null || max === null
            ? "No configured limits found for selected parameter"
            : `${criticals.length} critical and ${warnings.length} warning points outside optimal band (${min} - ${max})`,
      },
      {
        color: criticals.length > 0 ? "#FF5A5F" : "#38C172",
        title: `${label} latest status`,
        detail:
          evaluations.at(-1) === undefined
            ? "No latest sample available"
            : `Latest value ${evaluations.at(-1)?.value.toFixed(2)} at ${formatObservedTime(evaluations.at(-1)?.observedAt)} is ${evaluations.at(-1)?.status}`,
      },
    ];
    setTrendInsights(insights);

    const events = [...criticals, ...warnings]
      .sort(
        (a, b) =>
          (parseObservedMs(b.observedAt) ?? 0) - (parseObservedMs(a.observedAt) ?? 0),
      )
      .slice(0, 8)
      .map((item) => ({
        label: `${label} ${item.status === "Critical" ? "out of range" : "near limit"}`,
        status: item.status,
        value: `${item.value.toFixed(2)} @ ${formatObservedTime(item.observedAt)}`,
      })) satisfies CompactEventItem[];

    setOutOfRangeEvents(events);
  }, [cppRecords, selectedParameterKey, configuredLimits]);

  const statusLabel = normalizeEquipmentStatus(liveStatus?.currentState);
  const subtitle = [
    batchSummary?.productName,
    `Batch: ${batchSummary?.batchNo ?? "-"}`,
    `Lot: ${batchSummary?.lotNo ?? "-"}`,
    `Status: ${batchSummary?.batchStatus ?? "-"}`,
    `Plant: ${liveStatus?.plantId ?? batchSummary?.plantId ?? "-"}`,
    `Area: ${liveStatus?.areaId ?? batchSummary?.areaId ?? "-"}`,
    `Start: ${formatSubtitleDateTime(batchSummary?.batchStartAt)}`,
    `End: ${formatSubtitleDateTime(batchSummary?.batchEndAt)}`,
  ]
    .filter(Boolean)
    .join(" | ");

  const headerMetadataItems = [
    { label: "Product", value: String(batchSummary?.productName ?? "-") },
    { label: "Batch", value: String(batchSummary?.batchNo ?? "-") },
    { label: "Lot", value: String(batchSummary?.lotNo ?? "-") },
    { label: "Status", value: String(batchSummary?.batchStatus ?? "-") },
    {
      label: "Plant",
      value: String(liveStatus?.plantId ?? batchSummary?.plantId ?? "-"),
    },
    {
      label: "Area",
      value: String(liveStatus?.areaId ?? batchSummary?.areaId ?? "-"),
    },
    {
      label: "Start",
      value: formatSubtitleDateTime(batchSummary?.batchStartAt),
    },
    {
      label: "End",
      value: formatSubtitleDateTime(batchSummary?.batchEndAt),
    },
  ];

  const scrollToSection = useCallback(
    (tab: DetailTab, behavior: ScrollBehavior) => {
      const container = containerRef.current;
      const section = container?.querySelector<HTMLElement>(
        `[data-detail-section="${tab}"]`,
      );
      const scrollRoot = container?.closest("main");
      if (!section || !scrollRoot) return;

      const rootTop = scrollRoot.getBoundingClientRect().top;
      const sectionTop = section.getBoundingClientRect().top;
      const headerHeight = headerRef.current?.offsetHeight ?? 0;
      const targetTop =
        scrollRoot.scrollTop + sectionTop - rootTop - headerHeight - 16;

      // Keep navigation constrained to the layout's content scroller.
      window.scrollTo({ top: 0, left: 0, behavior: "auto" });
      scrollRoot.scrollTo({
        top: Math.max(0, targetTop),
        behavior,
      });
    },
    [],
  );

  useEffect(() => {
    const container = containerRef.current;
    const scrollRoot = container?.closest("main");
    if (!container || !scrollRoot) return;

    const sections = detailTabIds
      .map((id) =>
        container.querySelector<HTMLElement>(`[data-detail-section="${id}"]`),
      )
      .filter((section): section is HTMLElement => section !== null);

    const updateVisibleTab = () => {
      setIsHeaderSolid(scrollRoot.scrollTop > 8);
      const rootTop = scrollRoot.getBoundingClientRect().top;
      const headerHeight = headerRef.current?.offsetHeight ?? 0;
      const activationLine = rootTop + headerHeight + 24;
      let nextTab = detailTabIds[0];

      sections.forEach((section) => {
        if (section.getBoundingClientRect().top <= activationLine) {
          nextTab = section.dataset.detailSection as DetailTab;
        }
      });

      const isAtBottom =
        scrollRoot.scrollTop + scrollRoot.clientHeight >=
        scrollRoot.scrollHeight - 2;
      setVisibleTab(
        isAtBottom ? detailTabIds[detailTabIds.length - 1] : nextTab,
      );
    };

    scrollRoot.addEventListener("scroll", updateVisibleTab, { passive: true });
    window.addEventListener("resize", updateVisibleTab);

    const initialScroll = window.requestAnimationFrame(() => {
      if (activeTab !== "parameters") {
        scrollToSection(activeTab, "auto");
      }
      updateVisibleTab();
    });

    return () => {
      window.cancelAnimationFrame(initialScroll);
      scrollRoot.removeEventListener("scroll", updateVisibleTab);
      window.removeEventListener("resize", updateVisibleTab);
    };
  }, [activeTab, scrollToSection]);

  return (
    <section
      ref={containerRef}
      aria-label={`${displayEquipmentId} details`}
      className="grid gap-4"
    >
      <div ref={headerRef} className="sticky top-0 z-30 pb-1">
        <EquipmentDetailHeader
          equipmentId={displayEquipmentId}
          activeTab={visibleTab}
          isSolid={isHeaderSolid}
          statusLabel={statusLabel}
          subtitle={subtitle || "Live equipment report"}
          metadataItems={headerMetadataItems}
          autoRefreshEnabled={autoRefreshEnabled}
          onToggleAutoRefresh={() => setAutoRefreshEnabled((current) => !current)}
          onDownloadReport={() => {
            void (async () => {
              try {
                const batchSummaries = await getBatchSummary({
                  equipmentId: displayEquipmentId,
                  limit: 20,
                });
                const latestBatch = normalizeBatchSummary(batchSummaries);

                const backendQuery = {
                  limit: 500,
                  ...(latestBatch?.batchNo ? { batchNo: latestBatch.batchNo } : {}),
                };

                const [cppData, alarmEvents] = await Promise.all([
                  getCppData(displayEquipmentId, backendQuery),
                  getAlarmEventData(displayEquipmentId, backendQuery),
                ]);

                await exportEquipmentReportAsExcel({
                  equipmentId: displayEquipmentId,
                  batchSummaries,
                  cppRecords: cppData,
                  alarmEventRecords: alarmEvents,
                });
              } catch (error) {
                setErrorMessage(
                  error instanceof Error
                    ? error.message
                    : "Unable to download report.",
                );
              }
            })();
          }}
          onTabSelect={(tab) => scrollToSection(tab, "smooth")}
        />
      </div>

      {detailSections.map(({ id, label, content }) => (
        <section
          key={id}
          id={`equipment-${id}`}
          data-detail-section={id}
          aria-label={label}
          className="scroll-mt-[140px]"
        >
          {content}
        </section>
      ))}

      <Snackbar
        open={Boolean(errorMessage)}
        title="Unable to load equipment report"
        message={errorMessage}
        variant="error"
        onClose={() => setErrorMessage("")}
      />
    </section>
  );
}
