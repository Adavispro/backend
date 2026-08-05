"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { Key, MagnifyingGlass, PencilSimple, Plus, Power } from "@phosphor-icons/react";
import DataTable, { StatusPill, type DataTableColumn } from "@/components/table/DataTable";
import { ConfirmDialog, FilterButton, Snackbar } from "@/components/ui";
import { ROUTES } from "@/config/routes";
import { activateTenant, deactivateTenant } from "../api";
import type { Tenant } from "../api";
import { useTenants } from "../hooks/useTenants";
import EditTenantDialog from "./EditTenantDialog";
import TenantFiltersPanel, {
  type TenantTableFilters,
} from "./TenantFiltersPanel";

const formatDate = (value?: string | null) => {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "-" : date.toLocaleDateString("en-GB");
};

const emptyFilters: TenantTableFilters = {
  companyCodes: [],
  companyNames: [],
  domains: [],
  statuses: [],
};

const countAppliedFilters = (filters: TenantTableFilters) =>
  filters.companyCodes.length +
  filters.companyNames.length +
  filters.domains.length +
  filters.statuses.length;

const uniqueValues = (values: string[]) =>
  Array.from(new Set(values.filter((value) => value && value !== "-"))).sort(
    (first, second) => first.localeCompare(second),
  );

const tenantStatus = (tenant: Tenant) =>
  tenant.isActive ? "Active" : "Inactive";

export default function TenantsTable() {
  const { clearError, errorMessage, isLoading, setTenants, tenants } = useTenants();
  const [search, setSearch] = useState("");
  const [isFilterPanelOpen, setIsFilterPanelOpen] = useState(false);
  const [appliedFilters, setAppliedFilters] =
    useState<TenantTableFilters>(emptyFilters);
  const [draftFilters, setDraftFilters] =
    useState<TenantTableFilters>(emptyFilters);
  const [editing, setEditing] = useState<Tenant | null>(null);
  const [statusTarget, setStatusTarget] = useState<Tenant | null>(null);
  const [isChangingStatus, setIsChangingStatus] = useState(false);
  const [notification, setNotification] = useState({ message: "", variant: "success" as "success" | "error" });
  const appliedFilterCount = countAppliedFilters(appliedFilters);
  const filterOptions = useMemo(
    () => ({
      companyCodes: uniqueValues(tenants.map((tenant) => tenant.companyCode)),
      companyNames: uniqueValues(tenants.map((tenant) => tenant.companyName)),
      domains: uniqueValues(tenants.map((tenant) => tenant.domain ?? "-")),
      statuses: ["Active", "Inactive"],
    }),
    [tenants],
  );

  const rows = useMemo(() => {
    const query = search.trim().toLowerCase();
    return tenants
      .filter((tenant) =>
        appliedFilters.companyCodes.length > 0
          ? appliedFilters.companyCodes.includes(tenant.companyCode)
          : true,
      )
      .filter((tenant) =>
        appliedFilters.companyNames.length > 0
          ? appliedFilters.companyNames.includes(tenant.companyName)
          : true,
      )
      .filter((tenant) =>
        appliedFilters.domains.length > 0
          ? appliedFilters.domains.includes(tenant.domain ?? "-")
          : true,
      )
      .filter((tenant) =>
        appliedFilters.statuses.length > 0
          ? appliedFilters.statuses.includes(tenantStatus(tenant))
          : true,
      )
      .filter((tenant) => {
        if (!query) return true;
        return [
          tenant.tenantId,
          tenant.companyCode,
          tenant.companyName,
          tenant.domain,
          tenantStatus(tenant),
        ].some((value) => value?.toLowerCase().includes(query));
      });
  }, [appliedFilters, search, tenants]);

  const columns = useMemo<DataTableColumn<Tenant>[]>(() => [
    { key: "serial", header: "S No.", render: (_row, index) => index + 1 },
    //{ key: "id", header: "Tenant ID", render: (row) => row.tenantId },
    { key: "name", header: "Tenant Name", render: (row) => row.companyName },
    { key: "code", header: "Tenant Code", render: (row) => row.companyCode },
    
    //{ key: "domain", header: "Domain", render: (row) => row.domain ?? "-", className: "w-[22%]" },
    { key: "status", header: "Status", render: (row) => <StatusPill label={row.isActive ? "Active" : "Inactive"} className={row.isActive ? "bg-[#DDF6DF] text-[#158047]" : "bg-[#EBEEF2] text-text-secondary"} /> },
    { key: "created", header: "Created", render: (row) => formatDate(row.createdAt) },
    {
      key: "actions",
      header: "Actions",
      disableRowLink: true,
      render: (row) => (
        <div className="flex items-center gap-2">
          <button type="button" aria-label={`Edit ${row.companyName}`} onClick={() => setEditing(row)} className="grid h-6 w-6 place-items-center rounded bg-[#E6F1FF] text-primary hover:bg-[#D6E8FF]"><PencilSimple size={12} /></button>
          <button type="button" aria-label={row.isActive ? "Deactivate tenant" : "Activate tenant"} onClick={() => setStatusTarget(row)} className={`grid h-6 w-6 place-items-center rounded ${row.isActive ? "bg-[#FFF0F0] text-danger" : "bg-[#E7F7EE] text-success"}`}><Power size={12} /></button>
          <Link aria-label={`Manage license for ${row.companyName}`} href={`${ROUTES.masterLicenses}?tenantId=${encodeURIComponent(row.tenantId)}`} className="grid h-6 w-6 place-items-center rounded bg-[#E6F1FF] text-primary hover:bg-[#D6E8FF]"><Key size={12} /></Link>
        </div>
      ),
    },
  ], []);

  const changeStatus = async () => {
    if (!statusTarget) return;
    setIsChangingStatus(true);
    try {
      if (statusTarget.isActive) {
        await deactivateTenant(statusTarget.tenantId);
        setTenants((current) => current.map((tenant) => tenant.tenantId === statusTarget.tenantId ? { ...tenant, isActive: false } : tenant));
      } else {
        const updated = await activateTenant(statusTarget.tenantId);
        setTenants((current) => current.map((tenant) => tenant.tenantId === updated.tenantId ? updated : tenant));
      }
      setNotification({ message: `Tenant ${statusTarget.isActive ? "deactivated" : "activated"} successfully.`, variant: "success" });
      setStatusTarget(null);
    } catch (error) {
      setNotification({ message: error instanceof Error ? error.message : "Unable to change tenant status.", variant: "error" });
    } finally {
      setIsChangingStatus(false);
    }
  };

  const handleOpenFilters = () => {
    setDraftFilters(appliedFilters);
    setIsFilterPanelOpen(true);
  };

  const handleApplyFilters = () => {
    setAppliedFilters(draftFilters);
    setIsFilterPanelOpen(false);
  };

  const handleClearFilters = () => {
    setDraftFilters(emptyFilters);
    setAppliedFilters(emptyFilters);
  };

  return (
    <>
      <DataTable
        title="Tenants"
        columns={columns}
        rows={rows}
        getRowKey={(row) => row.tenantId}
        emptyText={isLoading ? "Loading tenants..." : "No tenants found."}
        footerText={`SHOWING ${rows.length} OF ${tenants.length} ENTRIES`}
        showPagination={false}
        toolbar={
          <div className="flex items-center gap-3">
            <label className="module-glass-control hidden h-8 w-[300px] items-center gap-2 rounded-[4px] px-3 text-text-secondary md:flex"><MagnifyingGlass size={13} /><span className="sr-only">Search tenants</span><input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search Tenants" className="type-filter-value min-w-0 flex-1 bg-transparent outline-none" /></label>
            <FilterButton
              appliedCount={appliedFilterCount}
              onClick={handleOpenFilters}
            />
            <Link href={ROUTES.masterCreateTenant} className="inline-flex h-8 items-center gap-1.5 rounded-[4px] bg-primary px-4 text-[10px] font-semibold text-white hover:bg-primary-hover"><Plus size={12} /> Create Tenant</Link>
          </div>
        }
      />

      <TenantFiltersPanel
        isOpen={isFilterPanelOpen}
        draftFilters={draftFilters}
        companyCodeOptions={filterOptions.companyCodes}
        companyNameOptions={filterOptions.companyNames}
        domainOptions={filterOptions.domains}
        statusOptions={filterOptions.statuses}
        onDraftChange={setDraftFilters}
        onApply={handleApplyFilters}
        onClear={handleClearFilters}
        onClose={() => setIsFilterPanelOpen(false)}
      />

      <EditTenantDialog key={editing?.tenantId ?? "closed"} tenant={editing} onClose={() => setEditing(null)} onUpdated={(updated) => { setTenants((current) => current.map((tenant) => tenant.tenantId === updated.tenantId ? updated : tenant)); setNotification({ message: "Tenant updated successfully.", variant: "success" }); }} />
      <ConfirmDialog isOpen={Boolean(statusTarget)} title={statusTarget?.isActive ? "Deactivate Tenant" : "Activate Tenant"} message={`${statusTarget?.isActive ? "Deactivate" : "Activate"} ${statusTarget?.companyName ?? "this tenant"}?`} confirmLabel={statusTarget?.isActive ? "Deactivate" : "Activate"} isConfirming={isChangingStatus} onConfirm={changeStatus} onCancel={() => setStatusTarget(null)} />
      <Snackbar open={Boolean(notification.message || errorMessage)} title={notification.variant === "error" || errorMessage ? "Tenant operation failed" : "Tenant updated"} message={errorMessage || notification.message} variant={errorMessage ? "error" : notification.variant} onClose={() => { clearError(); setNotification({ message: "", variant: "success" }); }} />
    </>
  );
}
