import type { z } from "zod";
import { apiClient, ApiError, withQuery } from "@/api";
import type { BackendApiResponse } from "@/api/types";
import { areaSchema, blockSchema, plantSchema, roomSchema } from "../../shared/schemas";
import { areasSchema, blocksSchema, plantsSchema, roomsSchema } from "../schemas";
import type { TopologyKind, TopologyRecord, TopologyRequest } from "./types";

const root = "/api/master-management/mdm";
const listSchemas = { plants: plantsSchema, blocks: blocksSchema, areas: areasSchema, rooms: roomsSchema } as const;
const itemSchemas = { plants: plantSchema, blocks: blockSchema, areas: areaSchema, rooms: roomSchema } as const;
const idFields = { plants: "plantId", blocks: "blockId", areas: "areaId", rooms: "roomId" } as const;

const unwrapCollectionPayload = (value: unknown): unknown[] => {
  if (Array.isArray(value)) return value;
  if (!value || typeof value !== "object") return [];

  const record = value as Record<string, unknown>;
  const candidates = [record.data, record.items, record.content, record.records, record.results];
  const list = candidates.find(Array.isArray);
  return Array.isArray(list) ? list : [];
};

const stringId = (value: unknown, fallback = "") => {
  if (typeof value === "string" && value.trim()) return value;
  if (value && typeof value === "object" && !Array.isArray(value)) {
    const record = value as Record<string, unknown>;
    const nestedId =
      record.id ??
      record._id ??
      record.tenantId ??
      record.plantId ??
      record.blockId ??
      record.areaId ??
      record.roomId ??
      record.code ??
      record.plantCode ??
      record.blockCode ??
      record.areaCode ??
      record.roomCode;
    return stringId(nestedId, fallback);
  }
  return fallback;
};

const normalizeTopologyRecord = (kind: TopologyKind, value: unknown) => {
  if (!value || typeof value !== "object" || Array.isArray(value)) return value;

  const record = value as Record<string, unknown>;
  if (kind === "plants") {
    return {
      ...record,
      plantId: stringId(record.plantId, stringId(record.plantCode)),
      tenantId: stringId(record.tenantId),
    };
  }
  if (kind === "blocks") {
    return {
      ...record,
      blockId: stringId(record.blockId, stringId(record.blockCode)),
      tenantId: stringId(record.tenantId),
      plantId: stringId(record.plantId),
    };
  }
  if (kind === "areas") {
    return {
      ...record,
      areaId: stringId(record.areaId, stringId(record.areaCode)),
      tenantId: stringId(record.tenantId),
      plantId: stringId(record.plantId),
      blockId: stringId(record.blockId),
    };
  }
  if (kind === "rooms") {
    return {
      ...record,
      roomId: stringId(record.roomId, stringId(record.roomCode)),
      tenantId: stringId(record.tenantId),
      plantId: stringId(record.plantId),
      areaId: stringId(record.areaId),
    };
  }
  return record;
};

const normalizeTopologyData = (kind: TopologyKind, value: unknown) =>
  unwrapCollectionPayload(value).map((record) => normalizeTopologyRecord(kind, record));

const dataOrThrow = <T>(result: BackendApiResponse<T>, fallback: string) => {
  if (!result.success || result.data === null || result.data === undefined) {
    throw new ApiError({ status: 400, message: result.message || fallback, details: result });
  }
  return result.data;
};

export async function getTopologyRecords<K extends TopologyKind>(kind: K, isActive: boolean, signal?: AbortSignal) {
  const result = await apiClient<BackendApiResponse<unknown>>(
    withQuery(`${root}/${kind}`, { isActive }),
    { signal },
  );
  const data = dataOrThrow(result, `Unable to load ${kind}.`);
  return listSchemas[kind].parse(normalizeTopologyData(kind, data)) as z.infer<(typeof listSchemas)[K]>;
}

export async function getAllTopologyRecords<K extends TopologyKind>(kind: K, signal?: AbortSignal) {
  const [active, inactive] = await Promise.all([
    getTopologyRecords(kind, true, signal),
    getTopologyRecords(kind, false, signal),
  ]);
  const idField = idFields[kind];
  const records = [...active, ...inactive] as TopologyRecord[];
  const uniqueRecords = new Map<string, TopologyRecord>();

  records.forEach((record, index) => {
    const id = String(record[idField as keyof TopologyRecord] ?? "");
    const key = id
      ? [
          id,
          String(record.tenantId ?? ""),
          String("plantId" in record ? record.plantId : ""),
          String("blockId" in record ? record.blockId : ""),
          String("areaId" in record ? record.areaId : ""),
          String(record.isActive),
        ].join(":")
      : `${kind}:${index}`;
    uniqueRecords.set(key, record);
  });

  return Array.from(uniqueRecords.values()) as TopologyRecord[];
}

export async function createTopologyRecord(kind: TopologyKind, request: TopologyRequest) {
  const result = await apiClient<BackendApiResponse<unknown>, TopologyRequest>(`${root}/${kind}`, {
    method: "POST", body: request,
  });
  const data = dataOrThrow(result, `Unable to create ${kind.slice(0, -1)}.`);
  return itemSchemas[kind].parse(normalizeTopologyData(kind, data)) as TopologyRecord;
}

export async function updateTopologyRecord(kind: TopologyKind, record: TopologyRecord, request: TopologyRequest) {
  const id = String(record[idFields[kind] as keyof TopologyRecord]);
  const result = await apiClient<BackendApiResponse<unknown>, TopologyRequest>(`${root}/${kind}/${encodeURIComponent(id)}`, {
    method: "PUT", body: request,
  });
  const data = dataOrThrow(result, `Unable to update ${kind.slice(0, -1)}.`);
  return itemSchemas[kind].parse(normalizeTopologyData(kind, data)) as TopologyRecord;
}

export async function setTopologyRecordActive(kind: TopologyKind, record: TopologyRecord, active: boolean) {
  const id = String(record[idFields[kind] as keyof TopologyRecord]);
  const result = await apiClient<BackendApiResponse<unknown>>(
    `${root}/${kind}/${encodeURIComponent(id)}/${active ? "activate" : "deactivate"}`,
    { method: "POST" },
  );
  if (!active) return { ...record, isActive: false } as TopologyRecord;
  const data = dataOrThrow(result, `Unable to activate ${kind.slice(0, -1)}.`);
  return itemSchemas[kind].parse(normalizeTopologyData(kind, data)) as TopologyRecord;
}
