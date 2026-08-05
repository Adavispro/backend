export type EquipmentStatus =
  | "Running"
  | "Idle"
  | "Communication Error"
  | "Maintenance"
  | "Offline";

export type EquipmentStatusFilter =
  | "all"
  | "running"
  | "idle"
  | "communication-error"
  | "maintenance"
  | "offline";

export interface EquipmentRow {
  id: string;
  tenantId: string;
  plantId: string;
  plantName?: string;
  blockId: string;
  blockName?: string;
  areaId: string;
  areaName?: string;
  roomNo: string;
  roomName?: string;
  status: EquipmentStatus;
  stateReason: string;
  lastBatchNo: string;
  lastLotNo: string;
  lastActive: string;
}

export const equipmentRows: EquipmentRow[] = [
  {
    id: "FBD-450KG-PVIII",
    tenantId: "TNT-0001",
    plantId: "PLANT-01",
    blockId: "BLOCK-01",
    areaId: "AREA-01",
    roomNo: "ROOM-101",
    status: "Running",
    stateReason: "RUNNING",
    lastBatchNo: "B01-2026-01",
    lastLotNo: "L01",
    lastActive: "2 mins ago",
  },
  {
    id: "CHL-15467-ID",
    tenantId: "TNT-0001",
    plantId: "PLANT-01",
    blockId: "BLOCK-01",
    areaId: "AREA-02",
    roomNo: "ROOM-102",
    status: "Idle",
    stateReason: "STOP",
    lastBatchNo: "B01-2026-02",
    lastLotNo: "L02",
    lastActive: "2 mins ago",
  },
  {
    id: "MIX-204-A",
    tenantId: "TNT-0001",
    plantId: "PLANT-02",
    blockId: "BLOCK-02",
    areaId: "AREA-03",
    roomNo: "ROOM-201",
    status: "Communication Error",
    stateReason: "COMMUNICATION ERROR",
    lastBatchNo: "B02-2026-01",
    lastLotNo: "L01",
    lastActive: "8 mins ago",
  },
  {
    id: "TAB-301-B",
    tenantId: "TNT-0001",
    plantId: "PLANT-02",
    blockId: "BLOCK-02",
    areaId: "AREA-04",
    roomNo: "ROOM-204",
    status: "Maintenance",
    stateReason: "MAINTENANCE",
    lastBatchNo: "B02-2026-02",
    lastLotNo: "L02",
    lastActive: "12 mins ago",
  },
  {
    id: "AHU-101-C",
    tenantId: "TNT-0001",
    plantId: "PLANT-03",
    blockId: "BLOCK-03",
    areaId: "AREA-05",
    roomNo: "ROOM-301",
    status: "Offline",
    stateReason: "UNKNOWN",
    lastBatchNo: "-",
    lastLotNo: "-",
    lastActive: "25 mins ago",
  },
  {
    id: "CHL-15468-ID",
    tenantId: "TNT-0001",
    plantId: "PLANT-03",
    blockId: "BLOCK-03",
    areaId: "AREA-06",
    roomNo: "ROOM-302",
    status: "Running",
    stateReason: "RUNNING",
    lastBatchNo: "B03-2026-01",
    lastLotNo: "L01",
    lastActive: "1 min ago",
  },
];

export const equipmentStatusLabels: Record<EquipmentStatusFilter, string> = {
  all: "Total Equipment",
  running: "Running Equipment",
  idle: "Idle Equipment",
  "communication-error": "Communication Error Equipment",
  maintenance: "Equipment Under Maintenance",
  offline: "Offline Equipment",
};

const equipmentStatusByFilter: Record<
  Exclude<EquipmentStatusFilter, "all">,
  EquipmentStatus
> = {
  running: "Running",
  idle: "Idle",
  "communication-error": "Communication Error",
  maintenance: "Maintenance",
  offline: "Offline",
};

export const filterEquipmentRows = (filter: EquipmentStatusFilter) =>
  filter === "all"
    ? equipmentRows
    : equipmentRows.filter(
        (equipment) => equipment.status === equipmentStatusByFilter[filter],
      );
