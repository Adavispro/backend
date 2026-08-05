"use client";

import { useEffect, useMemo, useState } from "react";
import {
  CaretDown,
  MagnifyingGlass,
} from "@phosphor-icons/react/dist/ssr";
import { ApiError } from "@/api";
import DataTable, { type DataTableColumn } from "@/components/table/DataTable";
import { FilterButton, Snackbar } from "@/components/ui";
import { getAuditLogs } from "../api";
import type { AuditLog, AuditLogsPage } from "../api";
import AuditLogFiltersPanel, {
  type AuditLogTableFilters,
} from "./AuditLogFiltersPanel";

const emptyFilters: AuditLogTableFilters = {
  fromDate: "",
  modules: [],
  toDate: "",
};

const countAppliedFilters = (filters: AuditLogTableFilters) =>
  filters.modules.length +
  (filters.fromDate ? 1 : 0) +
  (filters.toDate ? 1 : 0);

const uniqueValues = (values: string[]) =>
  Array.from(new Set(values.filter((value) => value && value !== "-"))).sort(
    (first, second) => first.localeCompare(second),
  );

function ModuleBadge({ module }: { module: string }) {
  return (
    <span className="inline-flex h-6 items-center rounded-full border border-[#DCE3EA] bg-[#F0F2F4] px-3 text-[8px] font-medium text-text-secondary">
      {module}
    </span>
  );
}

function formatTimestamp(value?: string | null) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;

  return new Intl.DateTimeFormat("en-US", {
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    month: "short",
  }).format(date);
}

function formatAction(action: string) {
  return action
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function getAuditDescription(row: AuditLog) {
  if (typeof row.metadata?.description === "string") {
    return row.metadata.description;
  }

  const entity = row.entity ?? "Record";
  const entityId = row.entityId ?? row.userId ?? row.eventId ?? "unknown";
  return `${entity} ${entityId} ${formatAction(row.action).toLowerCase()}`;
}

function getRowSearchText(row: AuditLog) {
  return [
    row.eventId,
    row.userId,
    row.username,
    row.action,
    row.entity,
    row.entityId,
    row.status,
    getAuditDescription(row),
  ]
    .filter(Boolean)
    .join(" ")
    .toLowerCase();
}

const columns: DataTableColumn<AuditLog>[] = [
  {
    key: "timestamp",
    header: "Timestamp",
    render: (row) => formatTimestamp(row.timestamp ?? row.createdAt),
  },
  {
    key: "user",
    header: "User",
    render: (row) => row.username ?? row.userId ?? "-",
  },
  {
    key: "module",
    header: "Module",
    render: (row) => <ModuleBadge module={row.entity ?? "Audit"} />,
  },
  {
    key: "action",
    header: "Actions",
    render: (row) => formatAction(row.action),
  },
  {
    key: "status",
    header: "Status",
    render: (row) => row.status ?? "-",
  },
  {
    key: "description",
    header: "Description",
    render: (row) => getAuditDescription(row),
  },
];

interface AuditLogsToolbarProps {
  appliedFilterCount: number;
  search: string;
  sortDirection: "asc" | "desc";
  onFilterClick: () => void;
  onSearchChange: (value: string) => void;
  onSortToggle: () => void;
}

function AuditLogsToolbar({
  appliedFilterCount,
  search,
  sortDirection,
  onFilterClick,
  onSearchChange,
  onSortToggle,
}: AuditLogsToolbarProps) {
  return (
    <div className="flex items-center gap-3">
      <label className="module-glass-control hidden h-8 w-[310px] items-center gap-2 rounded-[4px] px-3 text-text-secondary md:flex">
        <MagnifyingGlass size={14} />
        <span className="sr-only">Search users, modules</span>
        <input
          type="search"
          value={search}
          onChange={(event) => onSearchChange(event.target.value)}
          placeholder="Search Users, Modules"
          className="type-filter-value min-w-0 flex-1 bg-transparent outline-none placeholder:text-text-secondary"
        />
      </label>

      <FilterButton
        appliedCount={appliedFilterCount}
        onClick={onFilterClick}
      />

      <button
        type="button"
        onClick={onSortToggle}
        className="module-glass-control type-filter-button flex h-8 items-center gap-2 rounded-[4px] px-3 text-text-heading"
      >
        {sortDirection === "desc" ? "Newest" : "Oldest"}
        <CaretDown size={12} weight="bold" />
      </button>
    </div>
  );
}

export default function AuditLogsTable() {
  const [page, setPage] = useState(0);
  const [auditPage, setAuditPage] = useState<AuditLogsPage>({
    content: [],
    first: true,
    hasNext: false,
    hasPrevious: false,
    last: true,
    pageNumber: 0,
    pageSize: 20,
    totalElements: 0,
    totalPages: 0,
  });
  const [search, setSearch] = useState("");
  const [isFilterPanelOpen, setIsFilterPanelOpen] = useState(false);
  const [appliedFilters, setAppliedFilters] =
    useState<AuditLogTableFilters>(emptyFilters);
  const [draftFilters, setDraftFilters] =
    useState<AuditLogTableFilters>(emptyFilters);
  const [sortDirection, setSortDirection] = useState<"asc" | "desc">("desc");
  const [isLoading, setIsLoading] = useState(true);
  const [notification, setNotification] = useState({
    message: "",
    title: "",
  });

  useEffect(() => {
    const controller = new AbortController();

    getAuditLogs({ page, size: 20 }, controller.signal)
      .then((logsPage) => {
        setAuditPage(logsPage);
        setNotification({ message: "", title: "" });
      })
      .catch((error) => {
        if (controller.signal.aborted) return;
        setAuditPage((current) => ({ ...current, content: [] }));
        setNotification({
          title: "Unable to load audit logs",
          message:
            error instanceof ApiError
              ? error.message
              : "Unable to load audit logs. Please try again.",
        });
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });

    return () => controller.abort();
  }, [page]);

  const moduleOptions = useMemo(
    () =>
      uniqueValues(
        auditPage.content.map((row) => row.entity ?? "Audit"),
      ),
    [auditPage.content],
  );
  const appliedFilterCount = countAppliedFilters(appliedFilters);

  const rows = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase();
    const searchedRows = normalizedSearch
      ? auditPage.content.filter((row) =>
          getRowSearchText(row).includes(normalizedSearch),
        )
      : auditPage.content;
    const filteredRows = searchedRows
      .filter((row) =>
        appliedFilters.modules.length > 0
          ? appliedFilters.modules.includes(row.entity ?? "Audit")
          : true,
      )
      .filter((row) => {
        if (!appliedFilters.fromDate && !appliedFilters.toDate) return true;
        const value = row.timestamp ?? row.createdAt;
        if (!value) return false;

        const rowTime = new Date(value).getTime();
        if (Number.isNaN(rowTime)) return false;

        if (appliedFilters.fromDate) {
          const fromTime = new Date(`${appliedFilters.fromDate}T00:00:00`).getTime();
          if (rowTime < fromTime) return false;
        }

        if (appliedFilters.toDate) {
          const toTime = new Date(`${appliedFilters.toDate}T23:59:59`).getTime();
          if (rowTime > toTime) return false;
        }

        return true;
      });

    return [...filteredRows].sort((first, second) => {
      const firstTime = new Date(first.timestamp ?? first.createdAt ?? 0).getTime();
      const secondTime = new Date(second.timestamp ?? second.createdAt ?? 0).getTime();
      return sortDirection === "desc"
        ? secondTime - firstTime
        : firstTime - secondTime;
    });
  }, [appliedFilters, auditPage.content, search, sortDirection]);

  const pageSize = auditPage.pageSize ?? 20;
  const startEntry =
    auditPage.totalElements === 0 ? 0 : page * pageSize + 1;
  const endEntry = Math.min(
    auditPage.totalElements,
    page * pageSize + rows.length,
  );
  const footerText = isLoading
    ? "Loading audit logs..."
    : `Showing ${startEntry} to ${endEntry} of ${auditPage.totalElements} entries`;

  return (
    <>
      <DataTable
        title="Logs"
        columns={columns}
        rows={rows}
        getRowKey={(row, index) =>
          row.id ?? row._id ?? row.eventId ?? `${row.action}-${index}`
        }
        toolbar={
          <AuditLogsToolbar
            appliedFilterCount={appliedFilterCount}
            search={search}
            sortDirection={sortDirection}
            onFilterClick={() => {
              setDraftFilters(appliedFilters);
              setIsFilterPanelOpen(true);
            }}
            onSearchChange={setSearch}
            onSortToggle={() =>
              setSortDirection((current) =>
                current === "desc" ? "asc" : "desc",
              )
            }
          />
        }
        footerText={footerText}
        currentPage={page + 1}
        totalPages={Math.max(auditPage.totalPages, 1)}
        onPageChange={(nextPage) => setPage(Math.max(nextPage - 1, 0))}
        emptyText={isLoading ? "Loading audit logs..." : "No audit logs found."}
      />

      <AuditLogFiltersPanel
        isOpen={isFilterPanelOpen}
        draftFilters={draftFilters}
        moduleOptions={moduleOptions}
        onDraftChange={setDraftFilters}
        onApply={() => {
          setAppliedFilters(draftFilters);
          setIsFilterPanelOpen(false);
        }}
        onClear={() => {
          setDraftFilters(emptyFilters);
          setAppliedFilters(emptyFilters);
        }}
        onClose={() => setIsFilterPanelOpen(false)}
      />

      <Snackbar
        open={Boolean(notification.message)}
        variant="error"
        title={notification.title}
        message={notification.message}
        onClose={() => setNotification({ message: "", title: "" })}
      />
    </>
  );
}
