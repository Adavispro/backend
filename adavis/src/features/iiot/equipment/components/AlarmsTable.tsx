import DataTable, {
  StatusPill,
  type DataTableColumn,
} from "@/components/table/DataTable";
import { MagnifyingGlass } from "@phosphor-icons/react/dist/ssr";
import { useMemo, useState } from "react";
import { Button } from "@/components/ui";
import {
  alarmRows,
  alarmSeverityClasses,
  alarmStatusClasses,
} from "../data/data";
import type { AlarmRow } from "../data/types";

const alarmColumns: DataTableColumn<AlarmRow>[] = [
  {
    key: "date",
    header: "Date",
    render: (row) => row.date,
  },
  {
    key: "time",
    header: "Time",
    render: (row) => (
      <span className="whitespace-pre-line leading-snug">{row.time}</span>
    ),
  },
  {
    key: "metric",
    header: "Metric",
    render: (row) => row.metric,
  },
  {
    key: "alarm",
    header: "Alarm",
    render: (row) => row.alarm,
  },
  {
    key: "severity",
    header: "Severity",
    render: (row) => (
      <StatusPill
        label={row.severity}
        className={alarmSeverityClasses[row.severity]}
      />
    ),
  },
  {
    key: "status",
    header: "Status",
    render: (row) => (
      <StatusPill
        label={row.status}
        className={alarmStatusClasses[row.status]}
      />
    ),
  },
  {
    key: "acknowledgedBy",
    header: "Acknowledged by",
    render: (row) => row.acknowledgedBy,
  },
  {
    key: "acknowledgedAt",
    header: "Acknowledged at",
    render: (row) => row.acknowledgedAt ?? "-",
  },
];

export default function AlarmsTable({
  title = "All Alarms",
  fixedHeight = false,
  rows = alarmRows,
  onAcknowledge,
  acknowledgingId,
}: {
  title?: string;
  fixedHeight?: boolean;
  rows?: AlarmRow[];
  onAcknowledge?: (row: AlarmRow) => void;
  acknowledgingId?: string | null;
}) {
  const [query, setQuery] = useState("");
  const [dateFilter, setDateFilter] = useState("all");
  const [severityFilter, setSeverityFilter] = useState("all");

  const dateOptions = useMemo(
    () => ["all", ...Array.from(new Set(rows.map((row) => row.date).filter(Boolean)))],
    [rows],
  );
  const severityOptions = useMemo(
    () => ["all", ...Array.from(new Set(rows.map((row) => row.severity).filter(Boolean)))],
    [rows],
  );

  const filteredRows = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();

    return rows.filter((row) => {
      if (dateFilter !== "all" && row.date !== dateFilter) return false;
      if (severityFilter !== "all" && row.severity !== severityFilter) return false;

      if (!normalizedQuery) return true;
      return [
        row.date,
        row.time,
        row.metric,
        row.alarm,
        row.severity,
        row.status,
      ]
        .join(" ")
        .toLowerCase()
        .includes(normalizedQuery);
    });
  }, [rows, query, dateFilter, severityFilter]);

  const columns = onAcknowledge
    ? [
        ...alarmColumns,
        {
          key: "actions",
          header: "Actions",
          disableRowLink: true,
          render: (row: AlarmRow) => (
            <Button
              size="sm"
              variant="secondary"
              textSize="text-[10px]"
              paddingX="px-2"
              paddingY="py-0"
              className="h-7"
              disabled={!row.requiresAcknowledge}
              isLoading={acknowledgingId === row.id}
              onClick={() => onAcknowledge(row)}
            >
              Acknowledge
            </Button>
          ),
        } satisfies DataTableColumn<AlarmRow>,
      ]
    : alarmColumns;

  return (
    <DataTable
      title={title}
      columns={columns}
      rows={filteredRows}
      getRowKey={(row, index) => `${row.occurredAtIso}-${row.id}-${index}`}
      toolbar={
        <div className="flex flex-wrap items-center gap-2">
          <label className="module-glass-control flex h-8 items-center gap-2 rounded-[4px] px-3 text-text-secondary">
            <MagnifyingGlass size={14} />
            <span className="sr-only">Search alarms</span>
            <input
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search Date/Time/Metric/Alarm"
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

          <select
            value={severityFilter}
            onChange={(event) => setSeverityFilter(event.target.value)}
            className="module-glass-control type-filter-button h-8 rounded-[4px] px-3 text-text-heading"
          >
            {severityOptions.map((value) => (
              <option key={value} value={value}>
                {value === "all" ? "All Severity" : value}
              </option>
            ))}
          </select>
        </div>
      }
      footerText={`Showing ${filteredRows.length === 0 ? 0 : 1} to ${filteredRows.length} of ${rows.length} entries`}
      currentPage={1}
      totalPages={1}
      fillHeight={!fixedHeight}
      className={fixedHeight ? "h-[460px] min-h-0" : undefined}
      showPagination={!fixedHeight}
    />
  );
}
