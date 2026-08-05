import DataTable, {
  StatusPill,
  type DataTableColumn,
} from "@/components/table/DataTable";
import {
  plantPerformanceRows,
  type PlantPerformanceRow,
} from "../data/manufacturing-overview-data";

const columns: DataTableColumn<PlantPerformanceRow>[] = [
  { key: "site", header: "Site", render: (row) => row.site },
  { key: "location", header: "Location", render: (row) => row.location },
  {
    key: "productionOutput",
    header: "Production Output (MT)",
    render: (row) => row.productionOutput,
  },
  {
    key: "batchesCompleted",
    header: "Batches Completed",
    render: (row) => row.batchesCompleted,
  },
  {
    key: "qualitySuccessRate",
    header: "Quality Success Rate",
    render: (row) => row.qualitySuccessRate,
  },
  {
    key: "status",
    header: "Status",
    render: (row) => (
      <StatusPill
        label={row.status}
        className={
          row.status === "Operational"
            ? "bg-[#DFF8EA] text-[#158047]"
            : "bg-[#FFF4D2] text-[#B48205]"
        }
      />
    ),
  },
];

export default function PlantPerformanceTable() {
  return (
    <DataTable
      title="Site Performance Summary"
      columns={columns}
      rows={plantPerformanceRows}
      getRowKey={(row) => row.site}
      footerText={`Showing 1 to ${plantPerformanceRows.length} of ${plantPerformanceRows.length} entries`}
      currentPage={1}
      totalPages={1}
      fillHeight={false}
      className="h-[330px] min-h-0"
    />
  );
}
