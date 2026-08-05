"use client";

import { useEffect, useMemo, useState } from "react";
import { Snackbar } from "@/components/ui";
import { getEquipmentLiveStatuses } from "../api/reports.api";
import EquipmentTable from "../components/EquipmentTable";
import {
  equipmentStatusLabels,
  type EquipmentStatusFilter,
} from "../data/equipment-overview";
import {
  normalizeLiveStatusToEquipmentRow,
} from "../data/report-normalizers";
import type { EquipmentRow } from "../data/equipment-overview";

const equipmentStatusByFilter: Record<
  Exclude<EquipmentStatusFilter, "all">,
  EquipmentRow["status"]
> = {
  running: "Running",
  idle: "Idle",
  "communication-error": "Communication Error",
  maintenance: "Maintenance",
  offline: "Offline",
};

export default function EquipmentStatusListScreen({
  statusFilter,
}: {
  statusFilter: EquipmentStatusFilter;
}) {
  const [allRows, setAllRows] = useState<EquipmentRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const rows = useMemo(
    () =>
      statusFilter === "all"
        ? allRows
        : allRows.filter(
            (row) => row.status === equipmentStatusByFilter[statusFilter],
          ),
    [allRows, statusFilter],
  );

  useEffect(() => {
    const controller = new AbortController();

    getEquipmentLiveStatuses({}, controller.signal)
      .then((statuses) => {
        setAllRows(statuses.map(normalizeLiveStatusToEquipmentRow));
        setErrorMessage("");
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        setAllRows([]);
        setErrorMessage(
          error instanceof Error
            ? error.message
            : "Unable to load equipment live status.",
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setIsLoading(false);
        }
      });

    return () => controller.abort();
  }, []);

  return (
    <section
      aria-label={equipmentStatusLabels[statusFilter]}
      className="flex min-h-full flex-col"
    >
      <EquipmentTable
        title={
          isLoading
            ? `Loading ${equipmentStatusLabels[statusFilter]}`
            : equipmentStatusLabels[statusFilter]
        }
        rows={rows}
      />
      <Snackbar
        open={Boolean(errorMessage)}
        title="Unable to load equipment"
        message={errorMessage}
        variant="error"
        onClose={() => setErrorMessage("")}
      />
    </section>
  );
}
