"use client";

import { usePathname } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { SidePanel } from "@/components/ui";
import { getTopologyRecords } from "@/features/master-management/plant-topology/api/topology.api";
import type { Area, Block, Plant, Room } from "@/features/master-management/shared/schemas";
import {
  getEquipmentLiveStatuses,
  getOeeAnalytics,
} from "@/features/iiot/equipment/api/reports.api";
import AnalyticsDashboard from "../components/AnalyticsDashboard";
import AnalyticsFormCard, {
  getInitialAnalyticsValues,
  isCompleteAnalyticsSelection,
  type AnalyticsHierarchyRow,
} from "../components/AnalyticsFormCard";
import AnalyticsWaitingState from "../components/AnalyticsWaitingState";
import type {
  AnalyticsValues,
  OeeDowntimeSegment,
  OeeMetrics,
  OeeShiftComparisonItem,
  OeeSummaryRow,
  OeeTopBreakdownLoss,
  OeeTrendPoint,
} from "../data/types";
import {
  clearTopBarSelection,
  setTopBarSelection,
} from "@/utils/topBarSelection";

export default function AnalyticsOeeScreen() {
  const pathname = usePathname();
  const [hierarchyRows, setHierarchyRows] = useState<AnalyticsHierarchyRow[]>([]);
  const [values, setValues] = useState<AnalyticsValues>(() => getInitialAnalyticsValues());
  const [draftValues, setDraftValues] = useState<AnalyticsValues>(() => getInitialAnalyticsValues());
  const [metrics, setMetrics] = useState<OeeMetrics | undefined>();
  const [trendPoints, setTrendPoints] = useState<OeeTrendPoint[] | undefined>();
  const [downtimeSegments, setDowntimeSegments] = useState<OeeDowntimeSegment[] | undefined>();
  const [shiftComparison, setShiftComparison] = useState<OeeShiftComparisonItem[] | undefined>();
  const [topBreakdownLosses, setTopBreakdownLosses] = useState<OeeTopBreakdownLoss[] | undefined>();
  const [summaryRows, setSummaryRows] = useState<OeeSummaryRow[] | undefined>();
  const [showAnalytics, setShowAnalytics] = useState(false);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const canView = useMemo(() => isCompleteAnalyticsSelection(values), [values]);
  const canSaveDraft = useMemo(
    () => isCompleteAnalyticsSelection(draftValues),
    [draftValues],
  );

  useEffect(() => {
    const controller = new AbortController();

    Promise.all([
      getEquipmentLiveStatuses({}, controller.signal),
      getTopologyRecords("plants", true, controller.signal),
      getTopologyRecords("blocks", true, controller.signal),
      getTopologyRecords("areas", true, controller.signal),
      getTopologyRecords("rooms", true, controller.signal),
    ])
      .then(([statuses, plants, blocks, areas, rooms]) => {
        if (controller.signal.aborted) return;

        const plantMap = new Map(plants.map((item: Plant) => [item.plantId, item.plantName?.trim() || item.plantId]));
        const blockMap = new Map(blocks.map((item: Block) => [item.blockId, item.blockName?.trim() || item.blockId]));
        const areaMap = new Map(areas.map((item: Area) => [item.areaId, item.areaName?.trim() || item.areaId]));
        const roomMap = new Map(rooms.map((item: Room) => [item.roomId, item.roomName?.trim() || item.roomId]));

        const rows = statuses
          .map((status) => {
            const plantId = String(status.plantId ?? "").trim();
            const blockId = String(status.blockId ?? "").trim();
            const areaId = String(status.areaId ?? "").trim();
            const roomId = String(status.roomId ?? status.roomNo ?? "").trim();
            const equipmentId = String(status.equipmentId ?? "").trim();

            if (!plantId || !blockId || !areaId || !roomId || !equipmentId) {
              return null;
            }

            return {
              plant: plantMap.get(plantId) ?? plantId,
              block: blockMap.get(blockId) ?? blockId,
              area: areaMap.get(areaId) ?? areaId,
              roomNo: roomMap.get(roomId) ?? roomId,
              equipmentId,
            } satisfies AnalyticsHierarchyRow;
          })
          .filter((row): row is AnalyticsHierarchyRow => row !== null);

        const uniqueRows = new Map<string, AnalyticsHierarchyRow>();
        rows.forEach((row) => {
          const key = `${row.plant}|${row.block}|${row.area}|${row.roomNo}|${row.equipmentId}`;
          if (!uniqueRows.has(key)) uniqueRows.set(key, row);
        });

        setHierarchyRows(Array.from(uniqueRows.values()));
      })
      .catch(() => {
        if (controller.signal.aborted) return;
        setHierarchyRows([]);
      });

    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (showAnalytics) {
      setTopBarSelection(pathname, values.equipmentId);
      return;
    }

    clearTopBarSelection(pathname);
  }, [pathname, showAnalytics, values.equipmentId]);

  function handleValueChange(id: string, value: string) {
    setValues((current) => {
      if (id === "plant") {
        return {
          ...current,
          plant: value,
          block: "Select Block",
          area: "Select Area",
          roomNo: "Select Room Number",
          equipmentId: "Select ID",
        };
      }
      if (id === "block") {
        return {
          ...current,
          block: value,
          area: "Select Area",
          roomNo: "Select Room Number",
          equipmentId: "Select ID",
        };
      }
      if (id === "area") {
        return {
          ...current,
          area: value,
          roomNo: "Select Room Number",
          equipmentId: "Select ID",
        };
      }
      if (id === "roomNo") {
        return {
          ...current,
          roomNo: value,
          equipmentId: "Select ID",
        };
      }
      if (id === "dateRange") {
        return {
          ...current,
          dateRange: value,
          startDate: value === "Specific Range" ? current.startDate ?? "" : "",
          endDate: value === "Specific Range" ? current.endDate ?? "" : "",
        };
      }
      return { ...current, [id]: value };
    });
  }

  function handleDraftValueChange(id: string, value: string) {
    setDraftValues((current) => {
      if (id === "plant") {
        return {
          ...current,
          plant: value,
          block: "Select Block",
          area: "Select Area",
          roomNo: "Select Room Number",
          equipmentId: "Select ID",
        };
      }
      if (id === "block") {
        return {
          ...current,
          block: value,
          area: "Select Area",
          roomNo: "Select Room Number",
          equipmentId: "Select ID",
        };
      }
      if (id === "area") {
        return {
          ...current,
          area: value,
          roomNo: "Select Room Number",
          equipmentId: "Select ID",
        };
      }
      if (id === "roomNo") {
        return {
          ...current,
          roomNo: value,
          equipmentId: "Select ID",
        };
      }
      if (id === "dateRange") {
        return {
          ...current,
          dateRange: value,
          startDate: value === "Specific Range" ? current.startDate ?? "" : "",
          endDate: value === "Specific Range" ? current.endDate ?? "" : "",
        };
      }
      return { ...current, [id]: value };
    });
  }

  function handleClear() {
    const initialValues = getInitialAnalyticsValues();
    setValues(initialValues);
    setDraftValues(initialValues);
    setMetrics(undefined);
    setTrendPoints(undefined);
    setDowntimeSegments(undefined);
    setShiftComparison(undefined);
    setTopBreakdownLosses(undefined);
    setSummaryRows(undefined);
    setShowAnalytics(false);
    setIsEditOpen(false);
  }

  async function loadAnalyticsData(nextValues: AnalyticsValues) {
    const isSpecificRange = nextValues.dateRange === "Specific Range";
    const payload = await getOeeAnalytics({
      equipmentId: nextValues.equipmentId,
      dateRange: nextValues.dateRange,
      startDate: isSpecificRange ? nextValues.startDate : "",
      endDate: isSpecificRange ? nextValues.endDate : "",
      limit: 300,
    });

    setMetrics(payload.metrics);
    setTrendPoints(payload.trendPoints);
    setDowntimeSegments(payload.downtimeSegments);
    setShiftComparison(payload.shiftComparison);
    setTopBreakdownLosses(payload.topBreakdownLosses);
    setSummaryRows(payload.summaryRows);
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (canView) {
      void (async () => {
        try {
          await loadAnalyticsData(values);
          setDraftValues(values);
          setShowAnalytics(true);
        } catch {
          setShowAnalytics(false);
        }
      })();
    }
  }

  function handleEditOpen() {
    setDraftValues(values);
    setIsEditOpen(true);
  }

  function handleEditClear() {
    setDraftValues(getInitialAnalyticsValues());
  }

  function handleEditSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (canSaveDraft) {
      void (async () => {
        try {
          await loadAnalyticsData(draftValues);
          setValues(draftValues);
          setShowAnalytics(true);
          setIsEditOpen(false);
        } catch {
          setShowAnalytics(false);
        }
      })();
    }
  }

  return (
    <section aria-label="Analytics - OEE" className="grid gap-5">
      {showAnalytics ? (
        <AnalyticsDashboard
          values={values}
          metrics={metrics}
          trendPoints={trendPoints}
          downtimeSegments={downtimeSegments}
          shiftComparison={shiftComparison}
          topBreakdownLosses={topBreakdownLosses}
          summaryRows={summaryRows}
          onEdit={handleEditOpen}
        />
      ) : (
        <>
          <AnalyticsFormCard
            values={values}
            hierarchyRows={hierarchyRows}
            onValueChange={handleValueChange}
            onClear={handleClear}
            onSubmit={handleSubmit}
          />
          <AnalyticsWaitingState />
        </>
      )}

      <SidePanel
        isOpen={isEditOpen}
        title="Edit Entry"
        onClose={() => setIsEditOpen(false)}
      >
        <AnalyticsFormCard
          compact
          values={draftValues}
          hierarchyRows={hierarchyRows}
          onValueChange={handleDraftValueChange}
          onClear={handleEditClear}
          onSubmit={handleEditSubmit}
        />
      </SidePanel>
    </section>
  );
}
