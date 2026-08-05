"use client";

import { useCallback, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Buildings, Factory, MagnifyingGlass, MapPin, PencilSimple, Plus, Power, SquaresFour } from "@phosphor-icons/react";
import DataTable, { StatusPill, type DataTableColumn } from "@/components/table/DataTable";
import { Button, ConfirmDialog, Snackbar } from "@/components/ui";
import { ROUTES } from "@/config/routes";
import { useLoginContext } from "@/features/auth/hooks/useCurrentUser";
import { useTenants } from "../../tenant-management/hooks/useTenants";
import { setTopologyRecordActive } from "../api";
import type { TopologyKind, TopologyRecord } from "../api";
import { usePlantTopology } from "../hooks/usePlantTopology";
import TopologyRecordDialog from "./TopologyRecordDialog";

const tabs = [
  { kind: "plants" as const, label: "Plants", icon: Factory },
  { kind: "blocks" as const, label: "Blocks", icon: Buildings },
  { kind: "areas" as const, label: "Areas", icon: SquaresFour },
  { kind: "rooms" as const, label: "Rooms", icon: MapPin },
];

const idField = { plants: "plantId", blocks: "blockId", areas: "areaId", rooms: "roomId" } as const;
const codeField = { plants: "plantCode", blocks: "blockCode", areas: "areaCode", rooms: "roomCode" } as const;
const nameField = { plants: "plantName", blocks: "blockName", areas: "areaName", rooms: "roomName" } as const;
const labelFor = (kind: TopologyKind) => tabs.find((tab) => tab.kind === kind)?.label.slice(0, -1) ?? "Resource";
const field = (record: TopologyRecord, key: string) => {
  const value = (record as unknown as Record<string, unknown>)[key];
  return value === null || value === undefined || value === "" ? "-" : String(value);
};
const detail = (kind: TopologyKind, record: TopologyRecord) => {
  if (kind === "plants") return `${field(record, "type")} · ${field(record, "timezone")}`;
  if (kind === "rooms") return field(record, "classification");
  return `Order ${field(record, "displayOrder")}`;
};
const rowKey = (kind: TopologyKind, record: TopologyRecord, index: number) =>
  [
    field(record, idField[kind]),
    field(record, "tenantId"),
    field(record, "plantId"),
    field(record, "blockId"),
    field(record, "areaId"),
    index,
  ].join(":");

type TopologyFilters = {
  plantId: string;
  blockId: string;
  areaId: string;
  status: string;
};

const emptyFilters = (): TopologyFilters => ({ plantId: "", blockId: "", areaId: "", status: "" });

const filterSelectClassName = "module-glass-control h-8 min-w-[150px] rounded-[4px] px-2 text-[10px] text-text-heading outline-none";

export default function PlantTopologyWorkspace() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const context = useLoginContext();
  const { tenants } = useTenants();
  const { clearError, data, errorMessage, isLoading, replaceRecord } = usePlantTopology();
  const requestedKind = searchParams.get("view") as TopologyKind | null;
  const kind = tabs.some((tab) => tab.kind === requestedKind) ? requestedKind! : "plants";
  const tenantId = searchParams.get("tenantId") || context?.tenantId || context?.user.tenantId || tenants.find((tenant) => tenant.isActive)?.tenantId || "";
  const [search, setSearch] = useState("");
  const [dialogKind, setDialogKind] = useState<TopologyKind | null>(null);
  const [editing, setEditing] = useState<TopologyRecord | null>(null);
  const [statusTarget, setStatusTarget] = useState<TopologyRecord | null>(null);
  const [filters, setFilters] = useState<TopologyFilters>(emptyFilters);
  const [isChangingStatus, setIsChangingStatus] = useState(false);
  const [notification, setNotification] = useState({ message: "", variant: "success" as "success" | "error" });

  const navigate = (nextKind: TopologyKind, nextTenant = tenantId) => {
    const query = new URLSearchParams();
    if (nextTenant) query.set("tenantId", nextTenant);
    query.set("view", nextKind);
    router.replace(`${ROUTES.masterPlantTopology}?${query.toString()}`);
    setSearch("");
    setFilters(emptyFilters());
  };

  const plantsById = useMemo(() => new Map(data.plants.map((plant) => [plant.plantId, plant])), [data.plants]);
  const blocksById = useMemo(() => new Map(data.blocks.map((block) => [block.blockId, block])), [data.blocks]);
  const areasById = useMemo(() => new Map(data.areas.map((area) => [area.areaId, area])), [data.areas]);
  const tenantsById = useMemo(() => new Map(tenants.map((tenant) => [tenant.tenantId, tenant])), [tenants]);

  const filteredPlants = useMemo(
    () => data.plants.filter((plant) => !tenantId || plant.tenantId === tenantId),
    [data.plants, tenantId],
  );

  const filteredBlocks = useMemo(
    () => data.blocks.filter((block) => {
      if (tenantId && block.tenantId !== tenantId) return false;
      if (filters.plantId && block.plantId !== filters.plantId) return false;
      return true;
    }),
    [data.blocks, filters.plantId, tenantId],
  );

  const filteredAreas = useMemo(
    () => data.areas.filter((area) => {
      if (tenantId && area.tenantId !== tenantId) return false;
      if (filters.plantId && area.plantId !== filters.plantId) return false;
      if (filters.blockId && area.blockId !== filters.blockId) return false;
      return true;
    }),
    [data.areas, filters.blockId, filters.plantId, tenantId],
  );

  const getTenantLabel = useCallback((tenantId: string) => {
    const tenant = tenantsById.get(tenantId);
    return tenant ? `${tenant.companyName} (${tenant.tenantId})` : tenantId || "-";
  }, [tenantsById]);


  const getPlantLabel = useCallback((plantId: string) => {
    const plant = plantsById.get(plantId);
    return plant ? `${plant.plantName} (${plant.plantCode})` : plantId || "-";
  }, [plantsById]);

  const getBlockLabel = useCallback((blockId: string) => {
    const block = blocksById.get(blockId);
    return block ? `${block.blockName} (${block.blockCode})` : blockId || "-";
  }, [blocksById]);

  const getAreaLabel = useCallback((areaId: string) => {
    const area = areasById.get(areaId);
    return area ? `${area.areaName} (${area.areaCode})` : areaId || "-";
  }, [areasById]);

  const getRoomBlockId = useCallback((record: TopologyRecord) => {
    const areaId = field(record, "areaId");
    return areasById.get(areaId)?.blockId ?? "";
  }, [areasById]);

  const changeFilter = (field: keyof TopologyFilters, value: string) => {
    setFilters((current) => ({
      ...current,
      [field]: value,
      ...(field === "plantId" ? { blockId: "", areaId: "" } : {}),
      ...(field === "blockId" ? { areaId: "" } : {}),
    }));
  };

  const rows = useMemo(() => {
    const query = search.trim().toLowerCase();
    return data[kind].filter((record) => {
      if (tenantId && record.tenantId !== tenantId) return false;
      if (filters.status === "active" && !record.isActive) return false;
      if (filters.status === "inactive" && record.isActive) return false;
      if (kind !== "plants" && filters.plantId && field(record, "plantId") !== filters.plantId) return false;
      if (kind === "areas" && filters.blockId && field(record, "blockId") !== filters.blockId) return false;
      if (kind === "rooms") {
        if (filters.blockId && getRoomBlockId(record) !== filters.blockId) return false;
        if (filters.areaId && field(record, "areaId") !== filters.areaId) return false;
      }
      if (!query) return true;
      return [
        field(record, idField[kind]),
        field(record, codeField[kind]),
        field(record, nameField[kind]),
        field(record, "tenantId"),
        getPlantLabel(field(record, "plantId")),
        getBlockLabel(field(record, "blockId") || getRoomBlockId(record)),
        getAreaLabel(field(record, "areaId")),
      ]
        .some((value) => value.toLowerCase().includes(query));
    }) as TopologyRecord[];
  }, [data, filters, getAreaLabel, getBlockLabel, getPlantLabel, getRoomBlockId, kind, search, tenantId]);

  const columns = useMemo<DataTableColumn<TopologyRecord>[]>(() => {
    const baseColumns: DataTableColumn<TopologyRecord>[] = [
      { key: "serial", header: "S No.", render: (_row, index) => index + 1 },
     // { key: "id", header: `${labelFor(kind)} ID`, render: (row) => field(row, idField[kind]) },
      { key: "name", header: `${labelFor(kind)} Name`, render: (row) => field(row, nameField[kind]) },
      { key: "code", header: `${labelFor(kind)} Code`, render: (row) => field(row, codeField[kind]) },
      
    ];

    if (kind === "plants") {
      baseColumns.push(
        { key: "tenant", header: "Tenant Name", render: (row) => getTenantLabel(field(row, "tenantId")) },
        //{ key: "detail", header: "Details", render: (row) => detail(kind, row) },
      );
    }

    if (kind === "blocks") {
      baseColumns.push({ key: "plant", header: "Plant Name", render: (row) => getPlantLabel(field(row, "plantId")), className: "w-[22%]" });
    }

    if (kind === "areas") {
      baseColumns.push(
        { key: "plant", header: "Plant Name", render: (row) => getPlantLabel(field(row, "plantId")), className: "w-[20%]" },
        { key: "block", header: "Block Name", render: (row) => getBlockLabel(field(row, "blockId")), className: "w-[20%]" },
      );
    }

    if (kind === "rooms") {
      baseColumns.push(
        { key: "plant", header: "Plant Name", render: (row) => getPlantLabel(field(row, "plantId")), className: "w-[18%]" },
        { key: "block", header: "Block Name", render: (row) => getBlockLabel(getRoomBlockId(row)), className: "w-[18%]" },
        { key: "area", header: "Area Name", render: (row) => getAreaLabel(field(row, "areaId")), className: "w-[18%]" },
        //{ key: "classification", header: "Classification", render: (row) => field(row, "classification") },
      );
    }

    return [
      ...baseColumns,
      { key: "status", header: "Status", render: (row) => <StatusPill label={row.isActive ? "Active" : "Inactive"} className={row.isActive ? "bg-[#DDF6DF] text-[#158047]" : "bg-[#EBEEF2] text-text-secondary"} /> },
      { key: "actions", header: "Actions", disableRowLink: true, render: (row) => <div className="flex items-center gap-2"><button type="button" aria-label={`Edit ${labelFor(kind)}`} disabled={!row.isActive} onClick={() => { setEditing(row); setDialogKind(kind); }} className="grid h-6 w-6 place-items-center rounded bg-[#E6F1FF] text-primary disabled:opacity-35"><PencilSimple size={12} /></button><button type="button" aria-label={row.isActive ? "Deactivate" : "Activate"} onClick={() => setStatusTarget(row)} className={`grid h-6 w-6 place-items-center rounded ${row.isActive ? "bg-[#FFF0F0] text-danger" : "bg-[#E7F7EE] text-success"}`}><Power size={12} /></button></div> },
    ];
  }, [getAreaLabel, getBlockLabel, getPlantLabel, getRoomBlockId, kind]);

  const filterControls = (
    <>
      {kind !== "plants" ? (
        <select
          value={filters.plantId}
          onChange={(event) => changeFilter("plantId", event.target.value)}
          className={filterSelectClassName}
          aria-label="Filter by plant"
        >
          <option value="">All plants</option>
          {filteredPlants.map((plant) => (
            <option key={plant.plantId} value={plant.plantId}>
              {plant.plantName} ({plant.plantCode})
            </option>
          ))}
        </select>
      ) : null}
      {(kind === "areas" || kind === "rooms") ? (
        <select
          value={filters.blockId}
          onChange={(event) => changeFilter("blockId", event.target.value)}
          className={filterSelectClassName}
          aria-label="Filter by block"
        >
          <option value="">All blocks</option>
          {filteredBlocks.map((block) => (
            <option key={block.blockId} value={block.blockId}>
              {block.blockName} ({block.blockCode})
            </option>
          ))}
        </select>
      ) : null}
      {kind === "rooms" ? (
        <select
          value={filters.areaId}
          onChange={(event) => changeFilter("areaId", event.target.value)}
          className={filterSelectClassName}
          aria-label="Filter by area"
        >
          <option value="">All areas</option>
          {filteredAreas.map((area) => (
            <option key={area.areaId} value={area.areaId}>
              {area.areaName} ({area.areaCode})
            </option>
          ))}
        </select>
      ) : null}
      <select
        value={filters.status}
        onChange={(event) => changeFilter("status", event.target.value)}
        className="module-glass-control h-8 min-w-[120px] rounded-[4px] px-2 text-[10px] text-text-heading outline-none"
        aria-label="Filter by status"
      >
        <option value="">All status</option>
        <option value="active">Active</option>
        <option value="inactive">Inactive</option>
      </select>
    </>
  );

  const changeStatus = async () => {
    if (!statusTarget) return;
    setIsChangingStatus(true);
    try {
      const updated = await setTopologyRecordActive(kind, statusTarget, !statusTarget.isActive);
      replaceRecord(kind, updated);
      setNotification({ message: `${labelFor(kind)} ${statusTarget.isActive ? "deactivated" : "activated"} successfully.`, variant: "success" });
      setStatusTarget(null);
    } catch (error) {
      setNotification({ message: error instanceof Error ? error.message : `Unable to update ${labelFor(kind).toLowerCase()}.`, variant: "error" });
    } finally {
      setIsChangingStatus(false);
    }
  };

  return (
    <div className="grid min-h-0 gap-4">
      <section className="module-glass-panel flex flex-wrap items-center justify-between gap-4 rounded-xl p-4 shadow-[0_12px_24px_rgba(35,50,70,0.1)]">
        <div className="flex flex-wrap gap-2">
          {tabs.map(({ kind: tabKind, label, icon: Icon }) => <button key={tabKind} type="button" onClick={() => navigate(tabKind)} className={`inline-flex h-9 items-center gap-2 rounded-[5px] px-4 text-[10px] font-semibold transition-colors ${kind === tabKind ? "bg-primary text-white shadow-[0_7px_16px_rgba(7,92,175,0.2)]" : "module-glass-control text-text-secondary hover:text-primary"}`}><Icon size={14} />{label}<span className={`rounded-full px-1.5 py-0.5 text-[8px] ${kind === tabKind ? "bg-white/20" : "bg-primary-light text-primary"}`}>{data[tabKind].filter((item) => !tenantId || item.tenantId === tenantId).length}</span></button>)}
        </div>
        <label className="grid gap-1 text-[9px] font-medium text-text-secondary">Tenant<select value={tenantId} onChange={(event) => navigate(kind, event.target.value)} className="module-glass-control h-9 min-w-[230px] rounded-[4px] px-3 text-[10px] text-text-heading outline-none"><option value="">Select tenant</option>{tenants.filter((tenant) => tenant.isActive).map((tenant) => <option key={tenant.tenantId} value={tenant.tenantId}>{tenant.companyName} ({tenant.tenantId})</option>)}</select></label>
      </section>

      <DataTable
        title={tabs.find((tab) => tab.kind === kind)?.label ?? "Topology"}
        columns={columns}
        rows={rows}
        getRowKey={(row, index) => rowKey(kind, row, index)}
        emptyText={isLoading ? `Loading ${kind}...` : `No ${kind} found for this tenant.`}
        footerText={`SHOWING ${rows.length} ENTRIES`}
        showPagination={false}
        toolbar={<div className="flex flex-wrap items-center justify-end gap-3"><label className="module-glass-control hidden h-8 w-[260px] items-center gap-2 rounded-[4px] px-3 text-text-secondary md:flex"><MagnifyingGlass size={13} /><span className="sr-only">Search {kind}</span><input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder={`Search ${kind}`} className="type-filter-value min-w-0 flex-1 bg-transparent outline-none" /></label>{filterControls}<Button size="sm" rounded="rounded-[4px]" textSize="text-[10px]" paddingX="px-4" paddingY="py-0" className="h-8 shadow-[0_8px_18px_rgba(7,92,175,0.18)]" disabled={!tenantId} prefixIcon={<Plus size={12} />} onClick={() => router.push(`${ROUTES.masterCreateTopology}?view=${kind}&tenantId=${encodeURIComponent(tenantId)}`)}>Create {labelFor(kind)}</Button></div>}
      />

      <TopologyRecordDialog key={`${dialogKind ?? "closed"}-${editing ? field(editing, idField[kind]) : "new"}`} kind={dialogKind} record={editing} tenantId={tenantId} tenants={tenants} topology={data} onClose={() => { setDialogKind(null); setEditing(null); }} onSaved={(saved) => { replaceRecord(kind, saved); setNotification({ message: `${labelFor(kind)} saved successfully.`, variant: "success" }); }} />
      <ConfirmDialog isOpen={Boolean(statusTarget)} title={`${statusTarget?.isActive ? "Deactivate" : "Activate"} ${labelFor(kind)}`} message={`${statusTarget?.isActive ? "Deactivate" : "Activate"} ${statusTarget ? field(statusTarget, nameField[kind]) : `this ${labelFor(kind).toLowerCase()}`}?`} confirmLabel={statusTarget?.isActive ? "Deactivate" : "Activate"} isConfirming={isChangingStatus} onConfirm={changeStatus} onCancel={() => setStatusTarget(null)} />
      <Snackbar open={Boolean(notification.message || errorMessage)} title={notification.variant === "error" || errorMessage ? "Topology operation failed" : "Topology updated"} message={errorMessage || notification.message} variant={errorMessage ? "error" : notification.variant} onClose={() => { clearError(); setNotification({ message: "", variant: "success" }); }} />
    </div>
  );
}
