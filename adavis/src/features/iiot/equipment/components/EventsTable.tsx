import DataTable, {
  StatusPill,
  type DataTableColumn,
} from "@/components/table/DataTable";
import { MagnifyingGlass } from "@phosphor-icons/react/dist/ssr";
import { useMemo, useState } from "react";
import { eventRows, eventSeverityClasses } from "../data/data";
import type { EventRow } from "../data/types";

const eventColumns: DataTableColumn<EventRow>[] = [
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
    key: "eventType",
    header: "Event Type",
    render: (row) => (
      <span className="inline-flex items-center gap-2 font-semibold text-primary">
        <span
          className={`h-1.5 w-1.5 rounded-full ${
            row.eventType === "Threshold Crossed"
              ? "bg-[#EF4444]"
              : "bg-primary"
          }`}
        />
        {row.eventType}
      </span>
    ),
  },
  {
    key: "severity",
    header: "Severity",
    render: (row) => (
      <StatusPill
        label={row.severity}
        className={eventSeverityClasses[row.severity]}
      />
    ),
  },
  { key: "source", header: "Source", render: (row) => row.source },
  {
    key: "description",
    header: "Description",
    render: (row) => row.description,
  },
];

export default function EventsTable({
  title = "All Events",
  fixedHeight = false,
  rows = eventRows,
}: {
  title?: string;
  fixedHeight?: boolean;
  rows?: EventRow[];
}) {
  const [query, setQuery] = useState("");
  const [dateFilter, setDateFilter] = useState("all");
  const [eventTypeFilter, setEventTypeFilter] = useState("all");
  const [severityFilter, setSeverityFilter] = useState("all");

  const dateOptions = useMemo(
    () => ["all", ...Array.from(new Set(rows.map((row) => row.date).filter(Boolean)))],
    [rows],
  );
  const eventTypeOptions = useMemo(
    () => ["all", ...Array.from(new Set(rows.map((row) => row.eventType).filter(Boolean)))],
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
      if (eventTypeFilter !== "all" && row.eventType !== eventTypeFilter) return false;
      if (severityFilter !== "all" && row.severity !== severityFilter) return false;

      if (!normalizedQuery) return true;
      return [
        row.date,
        row.time,
        row.metric,
        row.eventType,
        row.severity,
        row.description,
      ]
        .join(" ")
        .toLowerCase()
        .includes(normalizedQuery);
    });
  }, [rows, query, dateFilter, eventTypeFilter, severityFilter]);

  return (
    <DataTable
      title={title}
      columns={eventColumns}
      rows={filteredRows}
      getRowKey={(row, index) => `${row.occurredAtIso}-${row.metric}-${index}`}
      toolbar={
        <div className="flex flex-wrap items-center gap-2">
          <label className="module-glass-control flex h-8 items-center gap-2 rounded-[4px] px-3 text-text-secondary">
            <MagnifyingGlass size={14} />
            <span className="sr-only">Search events</span>
            <input
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search Date/Time/Metric/Event"
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
            value={eventTypeFilter}
            onChange={(event) => setEventTypeFilter(event.target.value)}
            className="module-glass-control type-filter-button h-8 rounded-[4px] px-3 text-text-heading"
          >
            {eventTypeOptions.map((value) => (
              <option key={value} value={value}>
                {value === "all" ? "All Event Types" : value}
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
