"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { MagnifyingGlass, PencilSimple, Plus, Power } from "@phosphor-icons/react";
import DataTable, { StatusPill, type DataTableColumn } from "@/components/table/DataTable";
import { ActionTooltip, ConfirmDialog, Snackbar } from "@/components/ui";
import { ROUTES } from "@/config/routes";
import { useTenants } from "../../tenant-management/hooks/useTenants";
import { setRoleActive } from "../api";
import type { Role } from "../api/types";
import { useRoles } from "../hooks/useRoles";
import EditRolePermissionsDialog from "./EditRolePermissionsDialog";
import EditRoleDetailsDialog from "./EditRoleDetailsDialog";

interface RoleRow { id: string; code: string; tenant: string; name: string; description: string; modules: string[]; created: string; status: "Active" | "Inactive"; source: Role }

const formatDate = (value: string) => { const date = new Date(value); return Number.isNaN(date.getTime()) ? "-" : date.toLocaleDateString("en-GB").replaceAll("/", "-"); };

function Tags({ values }: { values: string[] }) {
  const visible = values.slice(0, 2);
  return <div className="flex max-w-[320px] items-center gap-2 overflow-hidden">{visible.length ? visible.map((value) => <span key={value} className="inline-flex h-6 min-w-0 max-w-[145px] items-center overflow-hidden whitespace-nowrap rounded-full border border-[#DCE3EA] bg-[#F0F2F4] px-3 text-[8px] font-medium text-text-secondary" title={value}><span className="block min-w-0 truncate">{value}</span></span>) : <span className="text-[9px] text-text-secondary">No permissions</span>}{values.length > visible.length ? <span className="inline-flex h-6 shrink-0 items-center rounded-full border border-[#DCE3EA] bg-[#F0F2F4] px-2 text-[8px] font-semibold text-text-secondary">+{values.length - visible.length}</span> : null}</div>;
}

export default function RolesTable() {
  const { tenants } = useTenants();
  const { clearError, errorMessage, isLoading, matrix, permissions, replaceRole, roles } = useRoles();
  const [search, setSearch] = useState("");
  const [editingDetails, setEditingDetails] = useState<Role | null>(null);
  const [editingPermissions, setEditingPermissions] = useState<Role | null>(null);
  const [statusTarget, setStatusTarget] = useState<Role | null>(null);
  const [isChangingStatus, setIsChangingStatus] = useState(false);
  const [notification, setNotification] = useState({ text: "", variant: "success" as "success" | "error" });
  const tenantNamesById = useMemo(() => Object.fromEntries(tenants.map((tenant) => [tenant.tenantId, tenant.companyName || tenant.tenantId])), [tenants]);
  const moduleNames = useMemo(() => Object.fromEntries(matrix.modules.map((module) => [module.moduleId, module.moduleName])), [matrix.modules]);
  const rows = useMemo<RoleRow[]>(() => roles.map((role): RoleRow => ({ id: role.roleId, code: role.roleCode ?? "-", tenant: role.tenantId ? (tenantNamesById[role.tenantId] || role.tenantId) : "-", name: role.roleName || role.name, description: role.description ?? "-", modules: (permissions[role.roleId] ?? []).filter((permission) => permission.isActive).map((permission) => moduleNames[permission.moduleId] ?? permission.moduleId), created: formatDate(role.createdAt), status: role.isActive ? "Active" : "Inactive", source: role })).filter((row) => { const query = search.trim().toLowerCase(); return !query || [row.id, row.code, row.tenant, row.name, row.description, ...row.modules].some((value) => value.toLowerCase().includes(query)); }), [moduleNames, permissions, roles, search, tenantNamesById]);
  const columns = useMemo<DataTableColumn<RoleRow>[]>(() => [
    { key: "serial", header: "S No.", render: (_row, index) => index + 1 },
    //{ key: "id", header: "Role ID", render: (row) => row.id },
    { key: "name", header: "Role Name", render: (row) => row.name },
    { key: "code", header: "Role Code", render: (row) => row.code },
    
    //{ key: "description", header: "Description", render: (row) => row.description },
    { key: "modules", header: "Role Permissions", render: (row) => <Tags values={row.modules} />, className: "w-[20%]" },
    { key: "tenant", header: "Tenant Name", render: (row) => row.tenant },
    { key: "created", header: "Created", render: (row) => row.created },
    { key: "status", header: "Status", render: (row) => <StatusPill label={row.status} className={row.status === "Active" ? "bg-[#DDF6DF] text-[#158047]" : "bg-[#EBEEF2] text-text-secondary"} /> },
    { key: "actions", header: "Actions", disableRowLink: true, render: (row) => <div className="flex gap-2"><ActionTooltip ariaLabel="Open role edit actions" trigger={<span className="grid h-6 w-6 place-items-center rounded bg-[#E6F1FF] text-primary transition-colors hover:bg-[#D6E8FF]"><PencilSimple size={12} /></span>} options={[{ label: "Edit Role Details", onClick: () => setEditingDetails(row.source) }, { label: "Role Permissions", onClick: () => setEditingPermissions(row.source) }]} /><button type="button" aria-label={row.source.isActive ? "Deactivate role" : "Activate role"} onClick={() => setStatusTarget(row.source)} className={`grid h-6 w-6 place-items-center rounded ${row.source.isActive ? "bg-[#FFF0F0] text-danger" : "bg-[#E7F7EE] text-success"}`}><Power size={12} /></button></div> },
  ], []);
  const changeStatus = async () => { if (!statusTarget) return; setIsChangingStatus(true); try { const updated = await setRoleActive(statusTarget, !statusTarget.isActive); replaceRole(updated); setNotification({ text: `Role ${statusTarget.isActive ? "deactivated" : "activated"} successfully.`, variant: "success" }); setStatusTarget(null); } catch (error) { setNotification({ text: error instanceof Error ? error.message : "Unable to update role status.", variant: "error" }); } finally { setIsChangingStatus(false); } };

  return <><DataTable title="Roles List" columns={columns} rows={rows} getRowKey={(row) => row.id} emptyText={isLoading ? "Loading roles..." : "No roles found."} showPagination={false} footerText={`SHOWING ${rows.length} ENTRIES`} toolbar={<div className="flex items-center gap-3"><label className="module-glass-control hidden h-8 w-[310px] items-center gap-2 rounded-[4px] px-3 text-text-secondary md:flex"><MagnifyingGlass size={14} /><span className="sr-only">Search roles</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search Roles" className="min-w-0 flex-1 bg-transparent text-[10px] outline-none" /></label><Link href={ROUTES.masterCreateRole} className="inline-flex h-8 items-center gap-1.5 rounded-[4px] bg-primary px-4 text-[10px] font-semibold text-white shadow-[0_8px_18px_rgba(7,92,175,0.18)]"><Plus size={13} />Create Role</Link></div>} /><ConfirmDialog isOpen={Boolean(statusTarget)} title={`${statusTarget?.isActive ? "Deactivate" : "Activate"} Role`} message={`${statusTarget?.isActive ? "Deactivate" : "Activate"} ${statusTarget?.roleName || statusTarget?.name || statusTarget?.roleId || "this role"}?`} confirmLabel={statusTarget?.isActive ? "Deactivate" : "Activate"} isConfirming={isChangingStatus} onConfirm={() => void changeStatus()} onCancel={() => setStatusTarget(null)} />{editingDetails ? <EditRoleDetailsDialog role={editingDetails} onClose={() => setEditingDetails(null)} onUpdated={(role) => { replaceRole(role); setNotification({ text: "Role details updated successfully.", variant: "success" }); }} /> : null}{editingPermissions ? <EditRolePermissionsDialog role={editingPermissions} onClose={() => setEditingPermissions(null)} /> : null}<Snackbar open={Boolean(errorMessage || notification.text)} title={errorMessage || notification.variant === "error" ? "Role operation failed" : "Role updated"} message={errorMessage || notification.text} variant={errorMessage ? "error" : notification.variant} onClose={() => { clearError(); setNotification({ text: "", variant: "success" }); }} /></>;
}
