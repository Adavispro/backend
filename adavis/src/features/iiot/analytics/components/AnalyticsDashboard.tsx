import Image from "next/image";
import { ArrowUp, PencilSimple } from "@phosphor-icons/react";
import type { ReactNode } from "react";
import BarChart from "@/components/charts/BarChart";
import DoughnutChart from "@/components/charts/DoughnutChart";
import LineChart from "@/components/charts/LineChart";
import DataTable, {
  StatusPill,
  type DataTableColumn,
} from "@/components/table/DataTable";
import { Button } from "@/components/ui";
import batchProgressShader from "@/assets/monitoringConsole/batch-progress-shader.svg";
import {
  downtimeSegments as downtimeSegmentsData,
  oeeRows,
  oeeTrendPoints,
  shiftComparison as shiftComparisonData,
  topBreakdownLosses as topBreakdownLossesData,
} from "../data/data";
import type {
  AnalyticsValues,
  OeeDowntimeSegment,
  OeeMetrics,
  OeeShiftComparisonItem,
  OeeSummaryRow,
  OeeTopBreakdownLoss,
  OeeTrendPoint,
} from "../data/types";

const oeeColumns: DataTableColumn<OeeSummaryRow>[] = [
  { key: "date", header: "Date", render: (row) => row.date },
  { key: "productName", header: "Product Name", render: (row) => row.productName },
  { key: "batchNo", header: "Batch No.", render: (row) => row.batchNo },
  { key: "startTime", header: "Start Time", render: (row) => row.startTime },
  { key: "endTime", header: "End Time", render: (row) => row.endTime },
  { key: "runTimePercent", header: "Run Time %", render: (row) => row.runTimePercent },
  { key: "runTimeHrs", header: "Run Time Hrs", render: (row) => row.runTimeHrs },
];

function AnalyticsHeader({
  values,
  onEdit,
}: {
  values: AnalyticsValues;
  onEdit: () => void;
}) {
  const details = [
    { label: "PLANT", value: values.plant },
    { label: "BLOCK", value: values.block },
    { label: "AREA", value: values.area },
    { label: "ROOM NO.", value: values.roomNo },
    { label: "EQUIPMENT ID", value: values.equipmentId },
    { label: "DATE RANGE", value: values.dateRange },
  ];

  return (
    <section className="module-glass-panel rounded-lg px-5 py-4">
      <div className="flex items-start justify-between gap-5">
        <div>
          <h2 className="text-[14px] font-semibold text-primary">OEE Analytics</h2>
          <p className="mt-2 text-[10px] font-medium text-text-secondary">
            Selected context for the current analytics view
          </p>
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
      </div>

      <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        {details.map((detail) => (
          <div
            key={detail.label}
            className="rounded-md border border-[#D9E2EE] bg-white/45 px-3 py-2"
          >
            <p className="text-[10px] font-medium uppercase tracking-[0.08em] text-text-secondary">
              {detail.label}
            </p>
            <p className="mt-1 break-words text-[11px] font-semibold text-text-heading">
              {detail.value}
            </p>
          </div>
        ))}
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-2 text-[10px] font-medium text-text-secondary sm:hidden">
        <span>{values.plant}</span>
        <span>•</span>
        <span>{values.block}</span>
        <span>•</span>
        <span>{values.area}</span>
        <span>•</span>
        <span>{values.equipmentId}</span>
      </div>
    </section>
  );
}

const formatPercent = (value: number) => `${Math.round(value)}%`;

function OeeMetricCards({ metrics }: { metrics: OeeMetrics }) {
  const trendLabel = `${metrics.trendDelta >= 0 ? "+" : ""}${metrics.trendDelta.toFixed(1)}%`;
  const cards = [
    { label: "Overall OEE", value: formatPercent(metrics.overallOee), trend: trendLabel, className: "bg-primary text-white", bar: "#FFFFFF", track: "bg-white/25" },
    { label: "Availability", value: formatPercent(metrics.availability), trend: trendLabel, className: "bg-[#E7F7EE] text-text-heading", bar: "#38C172", track: "bg-[#C6CCD3]" },
    { label: "Performance", value: formatPercent(metrics.performance), trend: trendLabel, className: "bg-[#FFF8DD] text-text-heading", bar: "#FF6A2A", track: "bg-[#C6CCD3]" },
    { label: "Quality", value: formatPercent(metrics.quality), trend: trendLabel, className: "bg-[#EFE8FF] text-text-heading", bar: "#A944DB", track: "bg-[#C6CCD3]" },
  ];

  return (
    <div className="grid gap-4 md:grid-cols-4">
      {cards.map((card) => {
        const isPrimary = card.label === "Overall OEE";

        return (
          <article
            key={card.label}
            className={`relative min-h-[92px] overflow-hidden rounded-lg border border-white/65 p-4 shadow-[0_10px_20px_rgba(35,50,70,0.12)] ${card.className}`}
          >
            {isPrimary ? (
              <Image
                src={batchProgressShader}
                alt=""
                aria-hidden="true"
                className="absolute right-0 top-0 h-[85px] w-[109px]"
              />
            ) : null}
            <p className={`relative type-dashboard-card-title ${isPrimary ? "!text-white text-[15px]" : ""}`}>
              {card.label}
            </p>
            <div className="relative mt-4 flex items-end gap-2">
              <strong className={`type-detail-card-metric ${isPrimary ? "text-[32px] leading-none" : "text-[28px] leading-none"}`}>
                {card.value}
              </strong>
              <span className={`flex items-center gap-1 pb-1 text-xs font-medium ${card.bar === "#FF6A2A" ? "text-[#FF6A2A]" : "text-[#03B879]"}`}>
                <ArrowUp size={13} weight="bold" />
                {card.trend}
              </span>
            </div>
            <div className={`absolute bottom-3 left-4 right-4 h-1 rounded-full ${card.track}`}>
              <span className="block h-full w-3/4 rounded-full" style={{ backgroundColor: card.bar }} />
            </div>
          </article>
        );
      })}
    </div>
  );
}

function ChartPanel({
  title,
  toolbar,
  children,
  bodyClassName = "mt-4",
}: {
  title: string;
  toolbar?: ReactNode;
  children: ReactNode;
  bodyClassName?: string;
}) {
  return (
    <article className="module-glass-panel rounded-lg p-4 shadow-[0_14px_26px_rgba(35,50,70,0.12)]">
      <div className="flex items-center justify-between">
        <h2 className="type-table-title">{title}</h2>
        {toolbar}
      </div>
      <div className={bodyClassName}>{children}</div>
    </article>
  );
}

function TopBreakdownLosses({ losses }: { losses: OeeTopBreakdownLoss[] }) {
  return (
    <ChartPanel title="Top Breakdown Losses">
      <div className="flex h-[150px] flex-col justify-around">
        {losses.map((loss) => (
          <div key={loss.label} className="grid grid-cols-[130px_1fr_32px] items-center gap-3">
            <span className="type-table-compact">{loss.label}</span>
            <span className="h-2 rounded-full bg-[#D6D2D2]">
              <span
                className="block h-full rounded-full"
                style={{ width: `${loss.value}%`, backgroundColor: loss.color }}
              />
            </span>
            <span className="type-table-compact text-text-secondary">{loss.value}%</span>
          </div>
        ))}
      </div>
    </ChartPanel>
  );
}

function OeeSummaryTable({ rows }: { rows: OeeSummaryRow[] }) {
  return (
    <DataTable
      title="OEE Summary"
      columns={oeeColumns}
      rows={rows}
      getRowKey={(row, index) => `${row.date}-${row.batchNo}-${index}`}
      footerText={`Showing ${rows.length === 0 ? 0 : 1} to ${rows.length} of ${rows.length} entries`}
      currentPage={1}
      totalPages={1}
    />
  );
}

export default function AnalyticsDashboard({
  values,
  metrics,
  trendPoints,
  downtimeSegments,
  shiftComparison,
  topBreakdownLosses,
  summaryRows,
  onEdit,
}: {
  values: AnalyticsValues;
  metrics?: OeeMetrics;
  trendPoints?: OeeTrendPoint[];
  downtimeSegments?: OeeDowntimeSegment[];
  shiftComparison?: OeeShiftComparisonItem[];
  topBreakdownLosses?: OeeTopBreakdownLoss[];
  summaryRows?: OeeSummaryRow[];
  onEdit: () => void;
}) {
  const resolvedMetrics: OeeMetrics =
    metrics ??
    {
      overallOee: 75,
      availability: 93,
      performance: 93,
      quality: 93,
      trendDelta: 2.1,
    };
  const resolvedTrendPoints = trendPoints ?? oeeTrendPoints;
  const resolvedDowntimeSegments = downtimeSegments ?? downtimeSegmentsData;
  const resolvedShiftComparison = shiftComparison ?? shiftComparisonData;
  const resolvedTopBreakdownLosses = topBreakdownLosses ?? topBreakdownLossesData;
  const resolvedSummaryRows = summaryRows ?? oeeRows;
  const totalDowntimeHours = resolvedDowntimeSegments.reduce(
    (sum, segment) => sum + segment.value,
    0,
  );

  return (
    <div className="grid gap-4">
      <AnalyticsHeader values={values} onEdit={onEdit} />
      <OeeMetricCards metrics={resolvedMetrics} />
      <div className="grid gap-4 lg:grid-cols-2">
        <ChartPanel
          title="OEE Trend"
          toolbar={
            <button className="module-glass-control type-filter-button h-7 rounded-[4px] px-3 text-text-heading">
              Daily
            </button>
          }
        >
          <LineChart
            points={resolvedTrendPoints}
            maxValue={100}
            ticks={[100, 75, 50, 25, 0]}
            height={150}
            chartWidth={420}
            chartHeight={110}
            lineColor="#FF775C"
            fillColor="rgba(255,119,92,0.18)"
            fillColorTo="rgba(255,119,92,0.02)"
            labelInterval={1}
            markerSize="h-1 w-1"
          />
        </ChartPanel>

        <ChartPanel
          title="Downtime Breakdown"
          bodyClassName="mt-3 flex min-h-[150px] items-center justify-center"
        >
          <div className="flex justify-center">
            <DoughnutChart
              segments={resolvedDowntimeSegments}
              centerValue={`${totalDowntimeHours.toFixed(1)} H`}
              centerLabel="Total Downtime"
              size={118}
              strokeWidth={18}
              gapDegrees={3}
              legendValueSuffix=""
              legendLabelWidth={96}
              centerValueClassName="text-[12px] font-semibold leading-none text-text-heading"
              centerLabelClassName="mt-1 text-[7px] font-medium leading-none text-text-secondary"
            />
          </div>
        </ChartPanel>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <ChartPanel title="Shift Comparison (OEE %)">
          <BarChart
            items={resolvedShiftComparison}
            maxValue={100}
            ticks={[100, 80, 60, 40, 20]}
            height={150}
          />
        </ChartPanel>
        <TopBreakdownLosses losses={resolvedTopBreakdownLosses} />
      </div>

      <OeeSummaryTable rows={resolvedSummaryRows} />
    </div>
  );
}
