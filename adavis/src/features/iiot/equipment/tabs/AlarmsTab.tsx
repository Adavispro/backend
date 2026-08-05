import { useMemo, useState } from "react";
import AlarmsTable from "../components/AlarmsTable";
import type { AlarmRow } from "../data/types";

function AlarmFilterTabs({
  selected,
  onSelect,
  activeCount,
}: {
  selected: "all" | "active" | "ack";
  onSelect: (value: "all" | "active" | "ack") => void;
  activeCount: number;
}) {
  const filters = [
    { id: "all" as const, label: "All Alarms" },
    { id: "active" as const, label: "Active", count: activeCount },
    { id: "ack" as const, label: "Acknowledged" },
  ];

  return (
    <div className="flex flex-wrap items-center gap-3">
      {filters.map((filter) => {
        const active = selected === filter.id;
        return (
        <button
          key={filter.label}
          type="button"
          onClick={() => onSelect(filter.id)}
          className={`type-filter-button flex h-9 items-center gap-2 rounded-[4px] border px-5 ${active
            ? "border-[#BFD8F7] bg-[#E0F0FF] text-primary"
            : "border-white/70 bg-white/45 text-text-heading shadow-[inset_0_1px_0_rgba(255,255,255,0.65)]"
            }`}
        >
          {filter.label}
          {filter.count ? (
            <span className="grid h-4 min-w-4 place-items-center rounded-full bg-[#EF4444] px-1 text-[9px] font-semibold leading-none text-white">
              {filter.count}
            </span>
          ) : null}
        </button>
      );})}
    </div>
  );
}

export default function AlarmsTab({
  rows = [],
  onAcknowledge,
  acknowledgingId,
}: {
  rows?: AlarmRow[];
  onAcknowledge?: (row: AlarmRow) => void;
  acknowledgingId?: string | null;
}) {
  const [selectedFilter, setSelectedFilter] = useState<"all" | "active" | "ack">("active");

  const activeCount = useMemo(
    () => rows.filter((row) => row.status === "Active").length,
    [rows],
  );

  const visibleRows = useMemo(() => {
    if (selectedFilter === "active") {
      return rows.filter((row) => row.status === "Active");
    }
    if (selectedFilter === "ack") {
      return rows.filter((row) => row.status === "Acknowledged");
    }
    return rows;
  }, [rows, selectedFilter]);

  return (
    <>
      <div className="py-4">
        <AlarmFilterTabs
          selected={selectedFilter}
          onSelect={setSelectedFilter}
          activeCount={activeCount}
        />
      </div>

      <AlarmsTable
        title={selectedFilter === "all" ? "All Alarms" : selectedFilter === "active" ? "Active Alarms" : "Acknowledged Alarms"}
        rows={visibleRows}
        onAcknowledge={onAcknowledge}
        acknowledgingId={acknowledgingId}
      />
    </>
  );
}
