import Link from "next/link";
import DataTable, {
  StatusPill,
  type DataTableColumn,
} from "@/components/table/DataTable";
import type { EquipmentRow } from "../data/equipment-overview";

const statusClasses: Record<EquipmentRow["status"], string> = {
  Running: "bg-[#DFF8EA] text-[#158047]",
  Idle: "bg-[#FFF4D2] text-[#B48205]",
  "Communication Error": "bg-[#FFE2E2] text-[#D43B3B]",
  Maintenance: "bg-[#E8F2FF] text-[#3976BE]",
  Offline: "bg-[#ECEFF3] text-[#7A8490]",
};

const equipmentColumns: DataTableColumn<EquipmentRow>[] = [
  {
    key: "id",
    header: "Equipment ID",
    render: (row) => row.id,
  },
  {
    key: "status",
    header: "Status",
    render: (row) => (
      <StatusPill label={row.status} className={statusClasses[row.status]} />
    ),
  },
  {
    key: "lastBatchNo",
    header: "Batch No.",
    render: (row) => row.lastBatchNo,
  },
  {
    key: "lastLotNo",
    header: "Lot No.",
    render: (row) => row.lastLotNo,
  },
  {
    key: "plantId",
    header: "Plant",
    render: (row) => row.plantName ?? row.plantId,
  },
  {
    key: "areaId",
    header: "Area",
    render: (row) => row.areaName ?? row.areaId,
  },
  {
    key: "lastActive",
    header: "Last Event",
    render: (row) => row.lastActive,
  },
  {
    key: "actions",
    header: "Actions",
    disableRowLink: true,
    render: (row) => (
      <Link
        href={`/iiot/equipment/${encodeURIComponent(row.id)}`}
        className="font-semibold text-primary"
      >
        View ›
      </Link>
    ),
  },
];

export default function EquipmentTable({
  rows,
  title = "Available Equipments",
}: {
  rows: EquipmentRow[];
  title?: string;
}) {
  return (
    <DataTable
      title={title}
      columns={equipmentColumns}
      rows={rows}
      getRowKey={(row) => row.id}
      getRowHref={(row) =>
        `/iiot/equipment/${encodeURIComponent(row.id)}`
      }
      footerText={`Showing ${rows.length === 0 ? 0 : 1} to ${rows.length} of ${rows.length} entries`}
      currentPage={1}
      totalPages={1}
      emptyText="No equipment found for this status."
    />
  );
}
