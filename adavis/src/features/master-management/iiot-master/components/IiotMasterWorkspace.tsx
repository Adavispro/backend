"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import {
  Cpu,
  Factory,
  MagnifyingGlass,
  PencilSimple,
  Plus,
  Power,
  SlidersHorizontal,
} from "@phosphor-icons/react";
import DataTable, {
  StatusPill,
  type DataTableColumn,
} from "@/components/table/DataTable";
import { ConfirmDialog, Snackbar } from "@/components/ui";
import { ROUTES } from "@/config/routes";
import { usePlantTopology } from "../../plant-topology/hooks/usePlantTopology";
import { useTenants } from "../../tenant-management/hooks/useTenants";
import type {
  CriticalParameter,
  CriticalParameterLimit,
  IiotAsset,
  IiotMasterRecord,
  IiotMasterSection,
  ProductMaster,
  UpdateCriticalParameterLimitValues,
  UpdateCriticalParameterValues,
  UpdateIiotAssetValues,
  UpdateProductMasterValues,
} from "../api";
import { isMutableIiotMasterSection } from "../api";
import { useIiotMasterData } from "../hooks/useIiotMasterData";
import IiotMasterEditDialog from "./IiotMasterEditDialog";

const sectionTabs: {
  key: IiotMasterSection;
  label: string;
  href: string;
  icon: typeof Factory;
}[] = [
  {
    key: "equipments",
    label: "Equipments",
    href: ROUTES.masterIiotEquipments,
    icon: Factory,
  },
  {
    key: "critical-parameters",
    label: "Critical Parameters",
    href: ROUTES.masterIiotCriticalParameters,
    icon: SlidersHorizontal,
  },
  {
    key: "critical-parameter-limits",
    label: "Parameter Limits",
    href: ROUTES.masterIiotCriticalParameterLimits,
    icon: Cpu,
  },
  {
    key: "product-master",
    label: "Product Master",
    href: ROUTES.masterIiotProductMaster,
    icon: Factory,
  },
];

const sectionTitles: Record<IiotMasterSection, string> = {
  equipments: "Manage Equipments",
  "critical-parameters": "Manage Critical Parameters",
  "critical-parameter-limits": "Manage Critical Parameter Limits",
  "product-master": "Manage Product Master",
};

const sectionCreateRoutes: Record<IiotMasterSection, string> = {
  equipments: ROUTES.masterCreateIiotEquipment,
  "critical-parameters": ROUTES.masterCreateIiotCriticalParameter,
  "critical-parameter-limits": ROUTES.masterCreateIiotCriticalParameterLimit,
  "product-master": ROUTES.masterCreateIiotProductMaster,
};

const sectionCreateLabels: Record<IiotMasterSection, string> = {
  equipments: "Create Equipment",
  "critical-parameters": "Create Parameter",
  "critical-parameter-limits": "Create Limit",
  "product-master": "Create Product",
};

const sectionLabelFor = (section: IiotMasterSection) =>
  sectionTabs.find((tab) => tab.key === section)?.label ?? "Records";

const formatDate = (value?: string | number | Date | null) => {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "-"
    : date.toLocaleDateString("en-GB").replaceAll("/", "-");
};

const formatValue = (value: unknown) => {
  if (value === undefined || value === null || value === "") return "-";
  if (typeof value === "boolean") return value ? "Yes" : "No";
  return String(value);
};

const statusPill = (isActive: boolean) => (
  <StatusPill
    label={isActive ? "Active" : "Inactive"}
    className={
      isActive
        ? "bg-[#DDF6DF] text-[#158047]"
        : "bg-[#EBEEF2] text-text-secondary"
    }
  />
);

const actionButton = (
  row: IiotMasterRecord,
  onClick: (record: IiotMasterRecord) => void,
  onEdit: (record: IiotMasterRecord) => void,
) => {
  const active = "isActive" in row ? Boolean(row.isActive) : false;

  return (
    <div className="flex items-center gap-2">
      <button
        type="button"
        aria-label="Edit record"
        onClick={() => onEdit(row)}
        className="grid h-6 w-6 place-items-center rounded bg-[#EAF3FF] text-primary"
      >
        <PencilSimple size={12} />
      </button>
      <button
        type="button"
        aria-label={active ? "Deactivate record" : "Activate record"}
        onClick={() => onClick(row)}
        className={`grid h-6 w-6 place-items-center rounded ${
          active ? "bg-[#FFF0F0] text-danger" : "bg-[#E7F7EE] text-success"
        }`}
      >
        <Power size={12} />
      </button>
    </div>
  );
};

function equipmentColumns(
  getTenantLabel: (tenantId: string) => string,
  getPlantLabel: (plantId: string) => string,
  getAreaLabel: (areaId: string) => string,
  getRoomLabel: (roomId: string) => string,
  onStatusClick: (record: IiotMasterRecord) => void,
  onEditClick: (record: IiotMasterRecord) => void,
): DataTableColumn<IiotAsset>[] {
  return [
    { key: "serial", header: "S No.", render: (_row, index) => index + 1 },
    {
      key: "equipmentCode",
      header: "Equipment Code",
      render: (row) => row.equipmentCode || row.equipmentId,
    },
    { key: "name", header: "Equipment Name", render: (row) => row.equipmentName },
    { key: "tenant", header: "Tenant Name", render: (row) => getTenantLabel(row.tenantId) },
    { key: "plant", header: "Plant Name", render: (row) => getPlantLabel(row.plantId) },
    { key: "area", header: "Area Name", render: (row) => getAreaLabel(row.areaId) },
    { key: "room", header: "Room Name", render: (row) => getRoomLabel(row.roomId) },
    {
      key: "status",
      header: "Status",
      render: (row) => statusPill(row.isActive),
    },
    {
      key: "actions",
      header: "Actions",
      disableRowLink: true,
      render: (row) => actionButton(row, onStatusClick, onEditClick),
    },
  ];
}

function parameterColumns(
  getEquipmentLabel: (equipmentId: string) => string,
  onStatusClick: (record: IiotMasterRecord) => void,
  onEditClick: (record: IiotMasterRecord) => void,
): DataTableColumn<CriticalParameter>[] {
  return [
    { key: "serial", header: "S No.", render: (_row, index) => index + 1 },
    { key: "equipment", header: "Equipment Name", render: (row) => getEquipmentLabel(row.equipmentId) },
    { key: "parameterCode", header: "Parameter Code", render: (row) => row.parameterCode },
    { key: "parameterName", header: "Parameter Name", render: (row) => row.parameterName },
    { key: "unitOfMeasure", header: "Unit Of Measure", render: (row) => formatValue(row.unitOfMeasure) },
    {
      key: "parameterType",
      header: "Type",
      render: (row) => formatValue(row.parameterType),
    },
    {
      key: "status",
      header: "Status",
      render: (row) => statusPill(row.isActive),
    },
    {
      key: "actions",
      header: "Actions",
      disableRowLink: true,
      render: (row) => actionButton(row, onStatusClick, onEditClick),
    },
  ];
}

function limitColumns(
  getParameterLabel: (parameterId: string) => string,
  getParameterType: (parameterId: string) => string,
  onStatusClick: (record: IiotMasterRecord) => void,
  onEditClick: (record: IiotMasterRecord) => void,
): DataTableColumn<CriticalParameterLimit>[] {
  const renderLimitValue = (row: CriticalParameterLimit) => {
    const rowRecord = row as Record<string, unknown>;
    const type = String(
      rowRecord.parameterType || getParameterType(row.parameterId),
    ).toUpperCase();

    if (type === "INT" || type === "FLOAT" || type === "RANGE") {
      return `${formatValue(row.lowCriticalValue)} - ${formatValue(row.highCriticalValue)}`;
    }
    if (type === "BOOLEAN") {
      const explicit = rowRecord.booleanValue;
      if (typeof explicit === "boolean") return explicit ? "True" : "False";
      const inferred = row.lowCriticalValue === 1 && row.highCriticalValue === 1;
      return inferred ? "True" : "False";
    }
    if (type === "ENUM") {
      return formatValue(rowRecord.enumValue);
    }
    if (type === "STRING") {
      return formatValue(rowRecord.stringValue);
    }

    return `${formatValue(row.lowCriticalValue)} - ${formatValue(row.highCriticalValue)}`;
  };

  return [
    { key: "serial", header: "S No.", render: (_row, index) => index + 1 },
    
    {
      key: "parameterName",
      header: "Parameter Name",
      render: (row) => getParameterLabel(row.parameterId),
    },
    {
      key: "parameterType",
      header: "Parameter Type",
      render: (row) => {
        const rowRecord = row as Record<string, unknown>;
        return formatValue(rowRecord.parameterType || getParameterType(row.parameterId));
      },
    },
    {
      key: "parameterLimitCode",
      header: "Parameter Limit Code",
      render: (row) => {
        const rowRecord = row as Record<string, unknown>;
        return String(rowRecord.parameterLimitCode ?? row.parameterLimitId);
      },
    },
    {
      key: "limitValue",
      header: "Limit Value",
      render: (row) => renderLimitValue(row),
    },
    {
      key: "status",
      header: "Status",
      render: (row) => statusPill(row.isActive),
    },
    {
      key: "actions",
      header: "Actions",
      disableRowLink: true,
      render: (row) => actionButton(row, onStatusClick, onEditClick),
    },
  ];
}

function productColumns(
  getTenantLabel: (tenantId: string) => string,
  getPlantLabel: (plantId: string) => string,
  onStatusClick: (record: IiotMasterRecord) => void,
  onEditClick: (record: IiotMasterRecord) => void,
): DataTableColumn<ProductMaster>[] {
  return [
    { key: "serial", header: "S No.", render: (_row, index) => index + 1 },
    { key: "code", header: "Product Code", render: (row) => row.productCode || row.productId },
    { key: "name", header: "Product Name", render: (row) => row.productName },
    { key: "tenant", header: "Tenant", render: (row) => getTenantLabel(row.tenantId) },
    { key: "plant", header: "Plant", render: (row) => getPlantLabel(row.plantId) },
    {
      key: "status",
      header: "Status",
      render: (row) => statusPill(row.isActive),
    },
    {
      key: "created",
      header: "Created",
      render: (row) => formatDate(row.createdAt),
    },
    {
      key: "actions",
      header: "Actions",
      disableRowLink: true,
      render: (row) => actionButton(row, onStatusClick, onEditClick),
    },
  ];
}

const getRecordId = (section: IiotMasterSection, record: IiotMasterRecord) => {
  if (section === "equipments" && "equipmentId" in record) return record.equipmentId;
  if (section === "critical-parameters" && "parameterId" in record) {
    return (
      record.parameterId ||
      `${record.equipmentId || "equipment"}::${record.parameterCode || "parameter"}`
    );
  }
  if (section === "critical-parameter-limits" && "parameterLimitId" in record) {
    return (
      record.parameterLimitId ||
      `${record.equipmentId || "equipment"}::${record.parameterId || "parameter"}::limit`
    );
  }
  if (section === "product-master" && "productId" in record)
    return record.productCode || record.productId;
  return "record";
};

interface IiotMasterWorkspaceProps {
  section: IiotMasterSection;
}

export default function IiotMasterWorkspace({
  section,
}: IiotMasterWorkspaceProps) {
  const { tenants } = useTenants();
  const { data: topology } = usePlantTopology();
  const {
    changeStatus,
    clearError,
    errorMessage,
    isLoading,
    records,
    updateRecord,
  } =
    useIiotMasterData();
  const [search, setSearch] = useState("");
  const [target, setTarget] = useState<IiotMasterRecord | null>(null);
  const [editTarget, setEditTarget] = useState<IiotMasterRecord | null>(null);
  const [changing, setChanging] = useState(false);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState({
    message: "",
    variant: "success" as "success" | "error",
  });

  const tenantLabels = useMemo(
    () =>
      Object.fromEntries(
        tenants
          .filter((tenant) => tenant.tenantId)
          .map((tenant) => [tenant.tenantId, tenant.companyName || tenant.tenantId]),
      ),
    [tenants],
  );

  const plantLabels = useMemo(
    () =>
      Object.fromEntries(
        topology.plants.map((plant) => [plant.plantId, plant.plantName || plant.plantId]),
      ),
    [topology.plants],
  );

  const areaLabels = useMemo(
    () =>
      Object.fromEntries(
        topology.areas.map((area) => [area.areaId, area.areaName || area.areaId]),
      ),
    [topology.areas],
  );

  const roomLabels = useMemo(
    () =>
      Object.fromEntries(
        topology.rooms.map((room) => [room.roomId, room.roomName || room.roomId]),
      ),
    [topology.rooms],
  );

  const getTenantLabel = (tenantId: string) => tenantLabels[tenantId] || tenantId;
  const getPlantLabel = (plantId: string) => plantLabels[plantId] || plantId;
  const getAreaLabel = (areaId: string) => areaLabels[areaId] || areaId;
  const getRoomLabel = (roomId: string) => roomLabels[roomId] || roomId;
  const equipmentLabels = useMemo(
    () =>
      Object.fromEntries(
        records.equipments.map((equipment) => [
          equipment.equipmentId,
          `${equipment.equipmentName} (${equipment.equipmentCode || equipment.equipmentId})`,
        ]),
      ),
    [records.equipments],
  );
  const getEquipmentLabel = (equipmentId: string) =>
    equipmentLabels[equipmentId] || equipmentId;
  const parameterLabels = useMemo(
    () =>
      Object.fromEntries(
        records["critical-parameters"].map((parameter) => [
          parameter.parameterId,
          `${parameter.parameterName} (${parameter.parameterCode})`,
        ]),
      ),
    [records],
  );
  const parameterTypes = useMemo(
    () =>
      Object.fromEntries(
        records["critical-parameters"].map((parameter) => [
          parameter.parameterId,
          parameter.parameterType,
        ]),
      ),
    [records],
  );
  const getParameterLabel = (parameterId: string) =>
    parameterLabels[parameterId] || parameterId;
  const getParameterType = (parameterId: string) =>
    parameterTypes[parameterId] || "";

  const activeRows = records[section];
  const rows = useMemo(
    () =>
      activeRows.filter(
        (item) =>
          !search.trim() ||
          Object.values(item).some((value) =>
            String(value ?? "")
              .toLowerCase()
              .includes(search.trim().toLowerCase()),
          ),
      ),
    [activeRows, search],
  );

  const columns = useMemo(() => {
    if (section === "equipments") {
      return equipmentColumns(
        getTenantLabel,
        getPlantLabel,
        getAreaLabel,
        getRoomLabel,
        setTarget,
        setEditTarget,
      ) as DataTableColumn<IiotMasterRecord>[];
    }
    if (section === "critical-parameters") {
      return parameterColumns(
        getEquipmentLabel,
        setTarget,
        setEditTarget,
      ) as DataTableColumn<IiotMasterRecord>[];
    }
    if (section === "critical-parameter-limits") {
      return limitColumns(
        getParameterLabel,
        getParameterType,
        setTarget,
        setEditTarget,
      ) as DataTableColumn<IiotMasterRecord>[];
    }
    return productColumns(
      getTenantLabel,
      getPlantLabel,
      setTarget,
      setEditTarget,
    ) as DataTableColumn<IiotMasterRecord>[];
  }, [
    getAreaLabel,
    getEquipmentLabel,
    getParameterLabel,
    getParameterType,
    getPlantLabel,
    getRoomLabel,
    getTenantLabel,
    section,
  ]);

  const changeTargetStatus = async () => {
    if (!target || !isMutableIiotMasterSection(section)) return;

    setChanging(true);
    try {
      await changeStatus(section, target, !Boolean(target.isActive));
      setNotice({
        message: `${getRecordId(section, target)} ${target.isActive ? "deactivated" : "activated"} successfully.`,
        variant: "success",
      });
      setTarget(null);
    } catch (error) {
      setNotice({
        message:
          error instanceof Error
            ? error.message
            : "Unable to update IIOT master record.",
        variant: "error",
      });
    } finally {
      setChanging(false);
    }
  };

  const saveTarget = async (
    values:
      | UpdateIiotAssetValues
      | UpdateCriticalParameterValues
      | UpdateCriticalParameterLimitValues
      | UpdateProductMasterValues,
  ) => {
    if (!editTarget || !isMutableIiotMasterSection(section)) return;

    setSaving(true);
    try {
      await updateRecord(section, editTarget, values);
      setNotice({
        message: `${getRecordId(section, editTarget)} updated successfully.`,
        variant: "success",
      });
      setEditTarget(null);
    } catch (error) {
      setNotice({
        message:
          error instanceof Error
            ? error.message
            : "Unable to update IIOT master record.",
        variant: "error",
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <div className="grid min-h-0 gap-4">
        <section className="module-glass-panel flex flex-wrap items-center justify-between gap-4 rounded-xl p-4 shadow-[0_12px_24px_rgba(35,50,70,0.1)]">
          <div className="flex flex-wrap gap-2">
            {sectionTabs.map(({ key, label, href, icon: Icon }) => {
              const active = key === section;

              return (
                <Link
                  key={key}
                  href={href}
                  className={`inline-flex h-9 items-center gap-2 rounded-[5px] px-4 text-[10px] font-semibold transition-colors ${
                    active
                      ? "bg-primary text-white shadow-[0_7px_16px_rgba(7,92,175,0.2)]"
                      : "module-glass-control text-text-secondary hover:text-primary"
                  }`}
                >
                  <Icon size={14} weight="regular" />
                  <span>{label}</span>
                  <span
                    className={`rounded-full px-1.5 py-0.5 text-[8px] ${
                      active ? "bg-white/20" : "bg-primary-light text-primary"
                    }`}
                  >
                    {records[key].length}
                  </span>
                </Link>
              );
            })}
          </div>
        </section>

        <DataTable
          title={sectionTitles[section]}
          columns={columns}
          rows={rows as IiotMasterRecord[]}
          getRowKey={(row, index) => `${getRecordId(section, row)}-${index}`}
          emptyText={isLoading ? "Loading IIOT master data..." : "No records found."}
          footerText={`SHOWING ${rows.length} ENTRIES`}
          showPagination={false}
          toolbar={
            <div className="flex items-center gap-3">
              <label className="module-glass-control hidden h-8 w-[290px] items-center gap-2 rounded-[4px] px-3 text-text-secondary md:flex">
                <MagnifyingGlass size={13} />
                <span className="sr-only">Search {sectionLabelFor(section)}</span>
                <input
                  type="search"
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder={`Search ${sectionLabelFor(section).toLowerCase()}`}
                  className="type-filter-value min-w-0 flex-1 bg-transparent outline-none"
                />
              </label>
              <Link
                href={sectionCreateRoutes[section]}
                className="inline-flex h-8 items-center justify-center gap-1.5 rounded-[4px] bg-primary px-4 text-[10px] font-semibold text-white shadow-[0_8px_18px_rgba(7,92,175,0.18)] transition-colors hover:bg-primary-hover"
              >
                <Plus size={12} />
                {sectionCreateLabels[section]}
              </Link>
            </div>
          }
        />
      </div>

      <ConfirmDialog
        isOpen={Boolean(target)}
        title={`${target?.isActive ? "Deactivate" : "Activate"} Record`}
        message={`${target?.isActive ? "Deactivate" : "Activate"} ${target ? getRecordId(section, target) : "this record"}?`}
        confirmLabel={target?.isActive ? "Deactivate" : "Activate"}
        isConfirming={changing}
        onConfirm={changeTargetStatus}
        onCancel={() => setTarget(null)}
      />

      {editTarget ? (
        <IiotMasterEditDialog
          key={`${section}-${getRecordId(section, editTarget)}`}
          isOpen
          section={section}
          record={editTarget}
          equipments={records.equipments}
          criticalParameters={records["critical-parameters"]}
          isSaving={saving}
          onClose={() => {
            if (!saving) setEditTarget(null);
          }}
          onSave={saveTarget}
        />
      ) : null}

      <Snackbar
        open={Boolean(errorMessage || notice.message)}
        title={
          errorMessage || notice.variant === "error"
            ? "IIOT master operation failed"
            : "IIOT master updated"
        }
        message={errorMessage || notice.message}
        variant={errorMessage ? "error" : notice.variant}
        onClose={() => {
          clearError();
          setNotice({ message: "", variant: "success" });
        }}
      />
    </>
  );
}
