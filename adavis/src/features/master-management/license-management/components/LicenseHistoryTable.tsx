"use client";

import { useEffect, useMemo, useState } from "react";
import { ArrowLeft, MagnifyingGlass } from "@phosphor-icons/react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import DataTable, { StatusPill, type DataTableColumn } from "@/components/table/DataTable";
import { Snackbar } from "@/components/ui";
import { ROUTES } from "@/config/routes";
import { useLoginContext } from "@/features/auth/hooks/useCurrentUser";
import { getLicenseHistory } from "../api";
import type { LicenseHistory } from "../api";

const formatDateTime = (value: string) => {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "-" : date.toLocaleString("en-GB");
};

const columns: DataTableColumn<LicenseHistory>[] = [
  { key: "serial", header: "S No.", render: (_row, index) => index + 1 },
  { key: "date", header: "Date & Time", render: (row) => formatDateTime(row.performedAt) },
  {
    key: "action",
    header: "Action",
    render: (row) => (
      <StatusPill
        label={row.action}
        className="bg-primary-light text-primary"
      />
    ),
  },
  { key: "transition", header: "Status Change", render: (row) => `${row.beforeStatus ?? "-"} → ${row.afterStatus ?? "-"}` },
  { key: "users", header: "User Limit", render: (row) => `${row.beforeMaxUsers ?? "-"} → ${row.afterMaxUsers ?? "-"}` },
  { key: "modules", header: "Modules", render: (row) => (row.afterModules ?? row.beforeModules ?? []).join(", ") || "-", className: "w-[20%]" },
  { key: "actor", header: "Performed By", render: (row) => row.performedBy ?? "-" },
  { key: "reason", header: "Reason", render: (row) => row.reason ?? "-", className: "w-[20%]" },
];

export default function LicenseHistoryTable() {
  const context = useLoginContext();
  const searchParams = useSearchParams();
  const tenantId = searchParams.get("tenantId") || context?.tenantId || context?.user.tenantId;
  const [history, setHistory] = useState<LicenseHistory[]>([]);
  const [search, setSearch] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    if (!context) return;
    if (!tenantId) {
      return;
    }
    let active = true;
    Promise.resolve()
      .then(() => {
        if (active) setIsLoading(true);
        return getLicenseHistory(tenantId);
      })
      .then((records) => {
        if (active) setHistory(records);
      })
      .catch((error) => {
        if (active) setErrorMessage(error instanceof Error ? error.message : "Unable to load license history.");
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });
    return () => {
      active = false;
    };
  }, [context, tenantId]);

  const rows = useMemo(() => {
    const query = search.trim().toLowerCase();
    if (!query) return history;
    return history.filter((record) =>
      [record.action, record.beforeStatus, record.afterStatus, record.performedBy, record.reason]
        .some((value) => value?.toLowerCase().includes(query)),
    );
  }, [history, search]);

  return (
    <>
      <DataTable
        title="License History"
        columns={columns}
        rows={rows}
        getRowKey={(row) => row.id}
        emptyText={!tenantId && context ? "No tenant context is assigned to this user." : isLoading ? "Loading license history..." : "No license history found."}
        footerText={`SHOWING ${rows.length} OF ${history.length} ENTRIES`}
        showPagination={false}
        toolbar={
          <div className="flex items-center gap-3">
            <label className="module-glass-control hidden h-8 w-[290px] items-center gap-2 rounded-[4px] px-3 text-text-secondary md:flex">
              <MagnifyingGlass size={13} />
              <span className="sr-only">Search license history</span>
              <input
                type="search"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search history"
                className="type-filter-value min-w-0 flex-1 bg-transparent outline-none"
              />
            </label>
            <Link href={tenantId ? `${ROUTES.masterLicenses}?tenantId=${encodeURIComponent(tenantId)}` : ROUTES.masterLicenses} className="inline-flex h-8 items-center gap-2 rounded-[4px] border border-primary/35 px-3 text-[10px] font-semibold text-primary hover:bg-primary-light">
              <ArrowLeft size={12} /> License
            </Link>
          </div>
        }
      />
      <Snackbar open={Boolean(errorMessage)} title="Unable to load license history" message={errorMessage} variant="error" onClose={() => setErrorMessage("")} />
    </>
  );
}
