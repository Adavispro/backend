import Image from "next/image";
import { ArrowUp, Check, PencilSimple } from "@phosphor-icons/react";
import { StatusPill } from "@/components/table/DataTable";
import { Button } from "@/components/ui";
import batchProgressShader from "@/assets/monitoringConsole/batch-progress-shader.svg";
import criticalStatusIcon from "@/assets/status/critical.svg";
import warningStatusIcon from "@/assets/status/warning.svg";
import type {
  AlarmRow,
  EventRow,
  ParameterRow,
} from "@/features/iiot/equipment/data/types";
import type { MonitoringValues } from "../data/types";
import type { BatchSummary, EquipmentLiveStatus } from "@/features/iiot/equipment/schemas/reports.schema";
import { normalizeEquipmentStatus } from "@/features/iiot/equipment/data/report-normalizers";
import ParametersTab from "@/features/iiot/equipment/tabs/ParametersTab";
import AlarmsTab from "@/features/iiot/equipment/tabs/AlarmsTab";
import EventsTab from "@/features/iiot/equipment/tabs/EventsTab";

const parseDateTime = (value: unknown) => {
  const date = new Date(String(value ?? ""));
  return Number.isNaN(date.getTime()) ? null : date;
};

const formatPercent = (value: number) => `${Math.max(0, Math.min(100, Math.round(value)))}%`;

const normalizeBatchStatus = (value: unknown) => {
  const status = String(value ?? "").trim();
  return status ? status : "In Progress";
};

const clamp = (value: number, min: number, max: number) =>
  Math.max(min, Math.min(max, value));

const deriveBatchProgress = (
  batchSummary?: BatchSummary,
  liveStatus?: EquipmentLiveStatus | null,
  parameterRows: ParameterRow[] = [],
) => {
  const batchStatus = normalizeBatchStatus(batchSummary?.batchStatus ?? liveStatus?.currentState);
  const normalizedStatus = batchStatus.toLowerCase();
  const start = parseDateTime(batchSummary?.batchStartAt);
  const end = parseDateTime(batchSummary?.batchEndAt);
  const cppCount = Number(batchSummary?.cppRecordCount ?? parameterRows.length ?? 0);
  const alarmCount = Number(batchSummary?.alarmCount ?? 0);
  const eventCount = Number(batchSummary?.eventCount ?? 0);

  if (
    normalizedStatus.includes("complete") ||
    normalizedStatus.includes("closed") ||
    normalizedStatus.includes("finished") ||
    normalizedStatus.includes("done") ||
    end !== null
  ) {
    return { value: 100, label: "Completed", batchStatus };
  }

  if (normalizedStatus.includes("hold") || normalizedStatus.includes("pause")) {
    return { value: 50, label: "On Hold", batchStatus };
  }

  if (normalizedStatus.includes("error") || normalizedStatus.includes("stop")) {
    return { value: 15, label: "Stopped", batchStatus };
  }

  const durationMinutes = start ? Math.max(0, (Date.now() - start.getTime()) / 60000) : 0;
  const elapsedScore = start ? clamp(durationMinutes / 2, 0, 45) : 0;
  const dataScore = clamp(cppCount * 1.5, 0, 35);
  const eventScore = clamp((alarmCount + eventCount) * 1.5, 0, 10);
  const statusScore =
    normalizedStatus.includes("start") ||
    normalizedStatus.includes("run") ||
    normalizedStatus.includes("active")
      ? 10
      : start !== null
        ? 5
        : 0;

  const estimatedProgress = clamp(
    elapsedScore + dataScore + eventScore + statusScore,
    0,
    95,
  );

  return {
    value: start || cppCount > 0 || alarmCount > 0 || eventCount > 0 ? estimatedProgress : 0,
    label: start || cppCount > 0 || alarmCount > 0 || eventCount > 0 ? "In Progress" : "Pending",
    batchStatus,
  };
};

const deriveThroughput = (
  batchSummary?: BatchSummary,
  parameterRows: ParameterRow[] = [],
) => {
  const sampleCount = batchSummary?.cppRecordCount ?? parameterRows.length;
  const start = parseDateTime(batchSummary?.batchStartAt);
  const end = parseDateTime(batchSummary?.batchEndAt);
  const elapsedMs = start
    ? Math.max(1, (end ?? new Date()).getTime() - start.getTime())
    : null;
  const hours = elapsedMs !== null ? Math.max(elapsedMs / 3_600_000, 1 / 60) : null;
  const value = hours !== null ? sampleCount / hours : sampleCount;

  return {
    value,
    unit: hours !== null ? "CPP/hr" : "CPP",
  };
};

const countCriticalParameters = (rows: ParameterRow[]) =>
  rows.filter((row) => row.status === "Critical").length;

const countCriticalAlarms = (rows: AlarmRow[]) =>
  rows.filter((row) => row.severity === "Critical").length;

function BatchInfoPanel({
  values,
  liveStatus,
  batchSummary,
  onEdit,
}: {
  values: MonitoringValues;
  liveStatus?: EquipmentLiveStatus | null;
  batchSummary?: BatchSummary;
  onEdit: () => void;
}) {
  const batchStatus = normalizeBatchStatus(batchSummary?.batchStatus ?? liveStatus?.currentState);
  const details = [
    { label: "PRODUCT NAME", value: batchSummary?.productName ?? "-" },
    { label: "BATCH NO.", value: batchSummary?.batchNo ?? liveStatus?.lastBatchNo ?? "-" },
    { label: "LOT NO.", value: batchSummary?.lotNo ?? liveStatus?.lastLotNo ?? values.lotNo },
    { label: "STATUS", value: batchStatus },
    { label: "PLANT", value: values.plantId ?? liveStatus?.plantId ?? batchSummary?.plantId ?? "-" },
    { label: "AREA", value: values.areaId ?? liveStatus?.areaId ?? batchSummary?.areaId ?? "-" },
    { label: "START", value: batchSummary?.batchStartAt ? new Date(batchSummary.batchStartAt).toLocaleString("en-IN") : "-" },
    { label: "END", value: batchSummary?.batchEndAt ? new Date(batchSummary.batchEndAt).toLocaleString("en-IN") : "-" },
  ];
  const stateLabel = normalizeEquipmentStatus(liveStatus?.currentState);

  return (
    <section className="module-glass-panel flex items-center justify-between gap-5 rounded-lg px-5 py-4">
      <div className="flex items-center gap-4">
        <div>
          <h2 className="text-[14px] font-semibold text-primary">
            {values.equipmentId}
          </h2>
          <div className="mt-2 flex items-center gap-2 text-[10px] font-medium text-text-secondary">
            <span>{values.areaId}</span>
            <span>•</span>
            <span>{values.equipmentId}</span>
            <StatusPill label={stateLabel} className="bg-[#DFF8EA] text-[#158047]" />
          </div>
        </div>
      </div>

      <div className="hidden flex-1 gap-x-8 gap-y-4 lg:grid lg:grid-cols-4">
        {details.map((detail) => (
          <div key={detail.label} className="min-w-0">
            <p className="text-[10px] font-medium uppercase tracking-[0.08em] text-text-secondary">
              {detail.label}
            </p>
            <p className="mt-2 break-words text-[11px] font-semibold leading-snug text-text-heading">
              {detail.value}
            </p>
          </div>
        ))}
      </div>

      <Button
        type="button"
        variant="ghost"
        size="sm"
        prefixIcon={<PencilSimple size={13} weight="regular" />}
        onClick={onEdit}
        rounded="rounded-[4px]"
        textSize="text-[11px]"
        paddingX="px-4"
        paddingY="py-0"
        className="h-8 border-primary/35 bg-white/35 !text-primary hover:bg-white/65"
      >
        Edit Entry
      </Button>
    </section>
  );
}

function MonitoringSummaryCards({
  batchSummary,
  liveStatus,
  parameterRows,
  alarmRows,
}: {
  batchSummary?: BatchSummary;
  liveStatus?: EquipmentLiveStatus | null;
  parameterRows: ParameterRow[];
  alarmRows: AlarmRow[];
}) {
  const batchProgress = deriveBatchProgress(batchSummary, liveStatus, parameterRows);
  const throughput = deriveThroughput(batchSummary, parameterRows);
  const criticalParameters = countCriticalParameters(parameterRows);
  const criticalAlarms = countCriticalAlarms(alarmRows);

  const cards = [
    {
      label: "Batch Progress",
      value: formatPercent(batchProgress.value),
      trendValue: batchProgress.label,
      trendDirection: batchProgress.value >= 50 ? "up" : "down",
      className: "bg-primary text-white",
      icon: undefined,
    },
    {
      label: "Throughput",
      value: throughput.value.toFixed(1),
      meta: throughput.unit,
      className: "bg-[#E7F7EE] text-text-heading",
      icon: Check,
      iconClassName: "bg-[#BFEBD3] text-white",
    },
    {
      label: "Critical Parameters",
      value: String(criticalParameters),
      meta: `of ${parameterRows.length} parameters`,
      subtext: batchProgress.batchStatus,
      className: "bg-[#FFF8DD] text-text-heading",
      statusIcon: warningStatusIcon,
      iconClassName: "opacity-80",
    },
    {
      label: "Critical Alarms",
      value: String(criticalAlarms),
      meta: `of ${alarmRows.length} alarms`,
      subtext: batchProgress.batchStatus,
      className: "bg-[#FCEAEA] text-text-heading",
      statusIcon: criticalStatusIcon,
      iconClassName: "opacity-90",
    },
  ];

  return (
    <div className="grid gap-4 md:grid-cols-4">
      {cards.map(({ icon: Icon, statusIcon, iconClassName, ...card }) => {
        const isBatchProgress = card.label === "Batch Progress";
        const trendColor =
          "trendDirection" in card && card.trendDirection === "up"
            ? "text-[#03D9C4]"
            : "text-[#FF7373]";

        return (
          <article
            key={card.label}
            className={`relative min-h-[88px] overflow-hidden rounded-lg border border-white/65 p-4 shadow-[0_10px_20px_rgba(35,50,70,0.12)] ${card.className}`}
          >
            {isBatchProgress ? (
              <Image
                src={batchProgressShader}
                alt=""
                aria-hidden="true"
                className="absolute right-0 top-0 h-[85px] w-[109px]"
              />
            ) : null}

            <p className={`relative type-dashboard-card-title ${isBatchProgress ? "!text-white text-[15px]" : ""}`}>
              {card.label}
            </p>
            <div className="relative mt-4 flex items-end gap-2">
              <strong className={`type-detail-card-metric ${isBatchProgress ? "text-[32px] leading-none" : "text-[24px]"}`}>
                {card.value}
              </strong>
              {"trendValue" in card && card.trendValue ? (
                <span className={`flex items-center gap-1 pb-1 text-xs font-medium ${trendColor}`}>
                  <ArrowUp size={13} weight="bold" />
                  {card.trendValue}
                </span>
              ) : "meta" in card && card.meta ? (
                <span className="pb-1 text-xs font-medium text-current">
                  {card.meta}
                </span>
              ) : null}
            </div>
            {"subtext" in card && card.subtext ? (
              <p className="relative mt-1 text-[10px] font-semibold text-success">{card.subtext}</p>
            ) : null}
            {isBatchProgress ? (
              <div className="absolute bottom-3 left-4 right-4 h-1 rounded-full bg-white/25">
                <span className="block h-full w-3/4 rounded-full bg-white" />
              </div>
            ) : null}
            {statusIcon ? (
              <Image
                src={statusIcon}
                alt=""
                aria-hidden="true"
                className={`absolute right-5 top-1/2 h-9 w-9 -translate-y-1/2 object-contain ${iconClassName}`}
              />
            ) : Icon ? (
              <span
                className={`absolute right-5 top-1/2 grid h-9 w-9 -translate-y-1/2 place-items-center rounded-full ${iconClassName}`}
              >
                <Icon size={28} weight="bold" />
              </span>
            ) : null}
          </article>
        );
      })}
    </div>
  );
}

export default function MonitoringDashboard({
  values,
  liveStatus,
  batchSummary,
  parameterRows: rows,
  alarmRows,
  eventRows,
  onEdit,
}: {
  values: MonitoringValues;
  liveStatus?: EquipmentLiveStatus | null;
  batchSummary?: BatchSummary;
  parameterRows?: ParameterRow[];
  alarmRows?: AlarmRow[];
  eventRows?: EventRow[];
  onEdit: () => void;
}) {
  const parameters = rows ?? [];
  const alarms = alarmRows ?? [];
  const events = eventRows ?? [];

  return (
    <div className="grid gap-5">
      <BatchInfoPanel
        values={values}
        liveStatus={liveStatus}
        batchSummary={batchSummary}
        onEdit={onEdit}
      />
      <MonitoringSummaryCards
        batchSummary={batchSummary}
        liveStatus={liveStatus}
        parameterRows={parameters}
        alarmRows={alarms}
      />
      <ParametersTab rows={parameters} showSummaryCards={false} />
      <AlarmsTab rows={alarms} />
      <EventsTab rows={events} />
    </div>
  );
}
