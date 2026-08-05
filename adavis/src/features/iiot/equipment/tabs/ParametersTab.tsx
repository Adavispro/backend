import {
  Check,
  SlidersHorizontal,
} from "@phosphor-icons/react/dist/ssr";
import { useMemo, useState } from "react";
import Image from "next/image";
import type { StaticImageData } from "next/image";
import totalParametersIcon from "@/assets/iiot/totalparameters.svg";
import criticalStatusIcon from "@/assets/status/critical.svg";
import warningStatusIcon from "@/assets/status/warning.svg";
import DataTable, { type DataTableColumn } from "@/components/table/DataTable";
import { MagnifyingGlass } from "@phosphor-icons/react/dist/ssr";
import { parameterRows } from "../data/data";
import type { ParameterRow, ParameterStatus } from "../data/types";

const summaryCardTemplates = [
  {
    label: "Total Parameters",
    value: "24",
    className:
      "bg-[linear-gradient(90deg,#0254C7_0%,#0E5880_100%)] text-white shadow-[0_14px_28px_rgba(6,79,165,0.25)]",
    icon: SlidersHorizontal,
    imageIcon: totalParametersIcon,
    iconClassName: "text-white/35",
  },
  {
    label: "Normal",
    value: "18",
    meta: "(79%)",
    className: "bg-[#E7F7EE] text-text-heading",
    icon: Check,
    iconClassName: "bg-[#BFEBD3] text-white",
  },
  {
    label: "Warning",
    value: "18",
    meta: "(79%)",
    className: "bg-[#FFF8DD] text-text-heading",
    statusIcon: warningStatusIcon,
    iconClassName: "opacity-80",
  },
  {
    label: "Critical",
    value: "18",
    meta: "(79%)",
    className: "bg-[#FCEAEA] text-text-heading",
    statusIcon: criticalStatusIcon,
    iconClassName: "opacity-90",
  },
];

function metricCellClass(status: ParameterStatus) {
  if (status === "Critical") return "text-[#D43B3B]";
  if (status === "Warning") return "text-[#B48205]";
  return "text-success";
}

function MetricValueCell({
  value,
  status,
}: {
  value: string;
  status: ParameterStatus;
}) {
  return <span className={`font-semibold ${metricCellClass(status)}`}>{value}</span>;
}

function DetailSummaryCard({
  label,
  value,
  meta,
  className,
  icon: Icon,
  imageIcon,
  statusIcon,
  iconClassName,
}: (typeof summaryCardTemplates)[number] & { statusIcon?: StaticImageData }) {
  const isPrimaryCard = label === "Total Parameters";

  return (
    <article
      className={`relative min-h-[76px] overflow-hidden rounded-md border border-white/65 p-3 shadow-[0_10px_20px_rgba(35,50,70,0.12)] ${className}`}
    >
      <p className={`type-dashboard-card-title ${isPrimaryCard ? "!text-white" : ""}`}>
        {label}
      </p>
      <div className="mt-4 flex items-end gap-1.5">
        <strong className="type-detail-card-metric text-[24px]">{value}</strong>
        {meta ? (
          <span className="pb-1 text-xs font-medium text-current">{meta}</span>
        ) : null}
      </div>
      <span
        className={`absolute grid place-items-center rounded-full ${isPrimaryCard ? "right-7 top-[58%] h-14 w-14 -translate-y-1/2" : "right-5 top-1/2 h-8 w-8 -translate-y-1/2"} ${iconClassName}`}
      >
        {statusIcon !== undefined ? (
          <Image
            src={statusIcon}
            alt=""
            aria-hidden="true"
            className="h-full w-full object-contain"
          />
        ) : imageIcon !== undefined ? (
          <span
            className="h-full w-full bg-current"
            style={{
              maskImage: `url(${imageIcon.src})`,
              WebkitMaskImage: `url(${imageIcon.src})`,
              maskRepeat: "no-repeat",
              WebkitMaskRepeat: "no-repeat",
              maskPosition: "center",
              WebkitMaskPosition: "center",
              maskSize: "contain",
              WebkitMaskSize: "contain",
            }}
          />
        ) : Icon !== undefined ? (
          <Icon size={24} weight="bold" />
        ) : null}
      </span>
    </article>
  );
}

const parameterColumns: DataTableColumn<ParameterRow>[] = [
  { key: "date", header: "Date", render: (row) => row.date },
  { key: "time", header: "Time", render: (row) => row.time },
];

export default function ParametersTab({
  rows = parameterRows,
  showSummaryCards = true,
}: {
  rows?: ParameterRow[];
  showSummaryCards?: boolean;
}) {
  const [query, setQuery] = useState("");
  const [dateFilter, setDateFilter] = useState("all");

  const effectiveMetricLabels = useMemo(() => {
    const fallback = new Set<string>();
    rows.forEach((row) => {
      Object.keys(row.metricValues ?? {}).forEach((label) => fallback.add(label));
    });
    return Array.from(fallback);
  }, [rows]);

  const dynamicColumns = useMemo<DataTableColumn<ParameterRow>[]>(() => {
    const metricCols = effectiveMetricLabels.map((label) => ({
      key: `metric-${label}`,
      header: label,
      render: (row: ParameterRow) => {
        const status = row.metricStatuses[label] ?? "Normal";
        const value = row.metricValues[label] ?? "-";
        const isBlackLabel = ["mode", "status", "cycle"].includes(
          label.toLowerCase(),
        );

        if (isBlackLabel) {
          return <span className="font-semibold text-black">{value}</span>;
        }

        return <MetricValueCell value={value} status={status} />;
      },
    }));

    return [...parameterColumns, ...metricCols];
  }, [effectiveMetricLabels]);

  const dateOptions = useMemo(
    () => ["all", ...Array.from(new Set(rows.map((row) => row.date).filter(Boolean)))],
    [rows],
  );
  const filteredRows = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();

    return rows.filter((row) => {
      if (dateFilter !== "all" && row.date !== dateFilter) return false;

      if (!normalizedQuery) return true;
      const dynamicValues = effectiveMetricLabels
        .map((label) => row.metricValues[label] ?? "")
        .join(" ");

      return [
        row.date,
        row.time,
        dynamicValues,
      ]
        .join(" ")
        .toLowerCase()
        .includes(normalizedQuery);
    });
  }, [rows, query, dateFilter, effectiveMetricLabels]);

  const normalCount = rows.filter((row) => row.status === "Normal").length;
  const warningCount = rows.filter((row) => row.status === "Warning").length;
  const criticalCount = rows.filter((row) => row.status === "Critical").length;
  const totalCount = rows.length;

  const summaryCards = summaryCardTemplates.map((card) => {
    if (card.label === "Total Parameters") {
      return { ...card, value: String(totalCount) };
    }
    if (card.label === "Normal") {
      return { ...card, value: String(normalCount), meta: "" };
    }
    if (card.label === "Warning") {
      return { ...card, value: String(warningCount), meta: "" };
    }
    if (card.label === "Critical") {
      return { ...card, value: String(criticalCount), meta: "" };
    }
    return card;
  });

  return (
    <div className="grid gap-4">
      {showSummaryCards ? (
        <div className="grid gap-4 lg:grid-cols-4">
          {summaryCards.map((card) => (
            <DetailSummaryCard key={card.label} {...card} />
          ))}
        </div>
      ) : null}

      <DataTable
        title="Parameters"
        columns={dynamicColumns}
        rows={filteredRows}
        getRowKey={(row, index) => `${row.observedAtIso}-${row.parameterKey}-${index}`}
        toolbar={
          <div className="flex flex-wrap items-center gap-2">
            <label className="module-glass-control flex h-8 items-center gap-2 rounded-[4px] px-3 text-text-secondary">
              <MagnifyingGlass size={14} />
              <span className="sr-only">Search parameters</span>
              <input
                type="search"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search Date/Time/Parameters"
                className="type-filter-value min-w-[220px] bg-transparent outline-none placeholder:text-text-secondary"
              />
            </label>

            <select
              value={dateFilter}
              onChange={(event) => setDateFilter(event.target.value)}
              className="module-glass-control type-filter-button h-8 rounded-[4px] px-3 text-text-heading"
            >
              {dateOptions.map((value) => (
                <option key={value} value={value}>
                  {value === "all" ? "All Dates" : value}
                </option>
              ))}
            </select>
          </div>
        }
        footerText={`Showing ${filteredRows.length === 0 ? 0 : 1} to ${filteredRows.length} of ${rows.length} entries`}
        currentPage={1}
        totalPages={3}
      />
    </div>
  );
}
