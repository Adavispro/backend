"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { CaretDown, ClockCounterClockwise, Key } from "@phosphor-icons/react";
import { ApiError } from "@/api";
import DataTable, { StatusPill, type DataTableColumn } from "@/components/table/DataTable";
import { Button, Snackbar } from "@/components/ui";
import { ROUTES } from "@/config/routes";
import { useLoginContext } from "@/features/auth/hooks/useCurrentUser";
import { useTenants } from "../../tenant-management/hooks/useTenants";
import { applyLicense, upgradeTenantLicense } from "../api";
import type { TenantLicense } from "../api";
import { useTenantLicense } from "../hooks/useTenantLicense";
import LicenseActionDialog, { type LicenseDialogAction } from "./LicenseActionDialog";

interface LicenseRow {
  tenantId: string;
  tenantName: string;
  license: TenantLicense;
}

const formatDate = (value?: string | null) => {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "-" : date.toLocaleDateString("en-GB").replaceAll("/", "-");
};

const statusClass = (status: string) => {
  if (status === "ACTIVE") return "bg-[#DDF6DF] text-[#158047]";
  if (status === "SUSPENDED") return "bg-[#FFF1C7] text-[#A86600]";
  if (status === "EXPIRED") return "bg-[#FFE1E1] text-[#D92D20]";
  return "bg-[#EBEEF2] text-text-secondary";
};

function ModuleTags({ modules }: { modules: string[] }) {
  const visible = modules.slice(0, 2);
  return <div className="flex max-w-[240px] items-center gap-2 overflow-hidden">{visible.length ? visible.map((module) => <span key={module} title={module} className="inline-flex h-6 max-w-[100px] items-center truncate rounded-full border border-[#DCE3EA] bg-[#F0F2F4] px-3 text-[8px] font-medium text-text-secondary">{module}</span>) : <span className="text-[9px] text-text-secondary">No modules</span>}{modules.length > visible.length ? <span className="inline-flex h-6 items-center rounded-full border border-[#DCE3EA] bg-[#F0F2F4] px-2 text-[8px] font-semibold text-text-secondary">+{modules.length - visible.length}</span> : null}</div>;
}

export default function LicenseOverview() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const context = useLoginContext();
  const { tenants } = useTenants();
  const tenantId = searchParams.get("tenantId") || context?.tenantId || context?.user.tenantId || tenants[0]?.tenantId;
  const actorUserId = context?.user.userId;
  const { clearError, errorMessage, isLoading, license, setLicense } = useTenantLicense(tenantId);
  const [action, setAction] = useState<LicenseDialogAction | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [notification, setNotification] = useState({ message: "", variant: "success" as "success" | "error" });
  const selectedTenant = tenants.find((tenant) => tenant.tenantId === tenantId);
  const rows = useMemo<LicenseRow[]>(() => license && tenantId ? [{ tenantId, tenantName: selectedTenant?.companyName ?? tenantId, license }] : [], [license, selectedTenant?.companyName, tenantId]);

  const submitAction = async (values: { encryptedLicenseToken?: string; reason?: string }) => {
    if (!action || !tenantId) return;
    setIsSubmitting(true);
    try {
      const updated = action === "UPGRADE" || action === "ACTIVATE"
        ? await upgradeTenantLicense(tenantId, {
            encryptedLicenseToken: values.encryptedLicenseToken ?? "",
            reason: values.reason,
            upgradedBy: actorUserId,
          })
        : await applyLicense({ actionType: action, encryptedLicenseToken: values.encryptedLicenseToken, performedBy: actorUserId, reason: values.reason });
      setLicense(updated);
      setAction(null);
      setNotification({ message: `License ${action.toLowerCase()} action completed.`, variant: "success" });
    } catch (error) {
      setNotification({ message: error instanceof ApiError ? error.message : "Unable to update the license.", variant: "error" });
    } finally {
      setIsSubmitting(false);
    }
  };

  const columns = useMemo<DataTableColumn<LicenseRow>[]>(() => [
    { key: "tenant", header: "Tenant", render: (row) => <div><p className="font-medium text-text-heading">{row.tenantName}</p><p className="mt-0.5 text-[8px] text-text-secondary">{row.tenantId}</p></div> },
    { key: "license", header: "License ID", render: (row) => row.license.id },
    { key: "plan", header: "Plan", render: (row) => row.license.planName ?? row.license.planId ?? "-" },
    { key: "modules", header: "Modules", className: "w-[20%]", render: (row) => <ModuleTags modules={row.license.modules} /> },
    { key: "users", header: "User Usage", render: (row) => `${row.license.currentUsers ?? 0} / ${row.license.maxUsers ?? 0}` },
    { key: "start", header: "Start Date", render: (row) => formatDate(row.license.startDate) },
    { key: "expiry", header: "Expiry Date", render: (row) => formatDate(row.license.expiryDate) },
    { key: "status", header: "Status", render: (row) => <StatusPill label={row.license.status} className={statusClass(row.license.status)} /> },
    { key: "actions", header: "Actions", disableRowLink: true, render: (row) => <div className="flex items-center gap-2">{row.license.status === "ACTIVE" ? <><button type="button" onClick={() => setAction("UPGRADE")} className="rounded-[4px] bg-[#E6F1FF] px-2.5 py-1.5 text-[8px] font-semibold text-primary">Upgrade</button><button type="button" onClick={() => setAction("SUSPEND")} className="rounded-[4px] bg-[#FFF0F0] px-2.5 py-1.5 text-[8px] font-semibold text-danger">Suspend</button></> : null}{row.license.status === "SUSPENDED" ? <button type="button" onClick={() => setAction("REACTIVATE")} className="rounded-[4px] bg-[#E7F7EE] px-2.5 py-1.5 text-[8px] font-semibold text-success">Reactivate</button> : null}{!["ACTIVE", "SUSPENDED"].includes(row.license.status) ? <span className="text-[9px] text-text-secondary">No actions available</span> : null}</div> },
  ], []);

  if (!context) return <div className="module-glass-panel min-h-[420px] rounded-xl p-5 text-xs text-text-secondary">Loading license details...</div>;

  const toolbar = <div className="flex items-center gap-3"><label className="module-glass-control relative hidden h-8 min-w-[245px] items-center md:flex"><span className="sr-only">Select Tenant</span><select value={tenantId ?? ""} onChange={(event) => router.replace(`${ROUTES.masterLicenses}?tenantId=${encodeURIComponent(event.target.value)}`)} className="type-filter-value h-full w-full appearance-none bg-transparent px-3 pr-8 outline-none"><option value="">Select Tenant</option>{tenants.map((tenant) => <option key={tenant.tenantId} value={tenant.tenantId}>{tenant.companyName} ({tenant.tenantId})</option>)}</select><CaretDown size={11} className="pointer-events-none absolute right-3 text-text-secondary" /></label><Link href={`${ROUTES.masterLicenseHistory}?tenantId=${encodeURIComponent(tenantId ?? "")}`} className="inline-flex h-8 items-center gap-1.5 rounded-[4px] border border-primary/35 bg-white/35 px-3 text-[10px] font-semibold text-primary hover:bg-primary-light"><ClockCounterClockwise size={13} />History</Link>{!license && tenantId ? <Button size="sm" rounded="rounded-[4px]" textSize="text-[10px]" paddingX="px-4" paddingY="py-0" className="h-8 shadow-[0_8px_18px_rgba(7,92,175,0.18)]" prefixIcon={<Key size={13} />} onClick={() => setAction("ACTIVATE")}>Activate License</Button> : null}</div>;

  return <><DataTable title="Licenses" columns={columns} rows={rows} getRowKey={(row) => row.license.id} emptyText={isLoading ? "Loading license..." : tenantId ? "No license found for the selected tenant." : "Select a tenant to view its license."} footerText={`SHOWING ${rows.length} ENTRIES`} showPagination={false} toolbar={toolbar} /><LicenseActionDialog key={action ?? "closed"} action={action} isSubmitting={isSubmitting} onClose={() => setAction(null)} onSubmit={submitAction} /><Snackbar open={Boolean(notification.message || errorMessage)} title={notification.variant === "error" || errorMessage ? "License action failed" : "License updated"} message={errorMessage || notification.message} variant={errorMessage ? "error" : notification.variant} onClose={() => { clearError(); setNotification({ message: "", variant: "success" }); }} /></>;
}
