"use client";

import { useCallback, useEffect, useState } from "react";
import { getAllTopologyRecords } from "../api";
import type { Area, Block, Plant, Room, TopologyKind, TopologyRecord } from "../api";

export interface TopologyData {
  plants: Plant[];
  blocks: Block[];
  areas: Area[];
  rooms: Room[];
}

const emptyData: TopologyData = { plants: [], blocks: [], areas: [], rooms: [] };

export function usePlantTopology() {
  const [data, setData] = useState<TopologyData>(emptyData);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  const reload = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage("");
    try {
      const [plants, blocks, areas, rooms] = await Promise.all([
        getAllTopologyRecords("plants"),
        getAllTopologyRecords("blocks"),
        getAllTopologyRecords("areas"),
        getAllTopologyRecords("rooms"),
      ]);
      setData({ plants: plants as Plant[], blocks: blocks as Block[], areas: areas as Area[], rooms: rooms as Room[] });
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Unable to load plant topology.");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void Promise.resolve().then(reload);
  }, [reload]);

  const replaceRecord = (kind: TopologyKind, record: TopologyRecord) => {
    const idField = { plants: "plantId", blocks: "blockId", areas: "areaId", rooms: "roomId" }[kind];
    setData((current) => ({
      ...current,
      [kind]: current[kind].map((item) =>
        String(item[idField as keyof typeof item]) === String(record[idField as keyof TopologyRecord])
          ? record
          : item,
      ),
    }) as TopologyData);
  };

  const addRecord = (kind: TopologyKind, record: TopologyRecord) => {
    setData((current) => ({ ...current, [kind]: [...current[kind], record] }) as TopologyData);
  };

  return { addRecord, clearError: () => setErrorMessage(""), data, errorMessage, isLoading, reload, replaceRecord };
}
