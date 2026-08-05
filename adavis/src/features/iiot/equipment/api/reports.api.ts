import { apiClient, parseApiData, withQuery } from "@/api";
import type { QueryParams } from "@/api/query";
import type { BackendApiResponse } from "@/api/types";
import type { z } from "zod";
import {
  alarmEventRecordListSchema,
  batchSummaryListSchema,
  criticalParameterLimitListSchema,
  cppRecordListSchema,
  equipmentLiveStatusListSchema,
  equipmentLiveStatusSchema,
  oeeAnalyticsPayloadSchema,
} from "../schemas/reports.schema";

const IIOT_PROXY_ROOT = "/api/iiot";

const resourcePath = (path: string) =>
  `${IIOT_PROXY_ROOT}/${path.replace(/^\/+/, "")}`;

async function getIiotResource<TSchema extends z.ZodTypeAny>(
  path: string,
  schema: TSchema,
  query?: QueryParams,
  signal?: AbortSignal,
): Promise<z.infer<TSchema>> {
  const response = await apiClient<BackendApiResponse<unknown>>(
    withQuery(resourcePath(path), query),
    { signal },
  );

  return parseApiData(
    response,
    schema,
    "The IIOT request failed.",
    "The IIOT service returned an invalid response.",
  );
}

export const getEquipmentLiveStatuses = (
  query: QueryParams = {},
  signal?: AbortSignal,
) =>
  getIiotResource(
    "equipment-live-status",
    equipmentLiveStatusListSchema,
    query,
    signal,
  );

export const getEquipmentLiveStatus = (
  equipmentId: string,
  signal?: AbortSignal,
) =>
  getIiotResource(
    `equipment-live-status/${encodeURIComponent(equipmentId)}`,
    equipmentLiveStatusSchema,
    undefined,
    signal,
  );

export const getBatchSummary = (
  query: QueryParams = {},
  signal?: AbortSignal,
) => getIiotResource("reports/batch-summary", batchSummaryListSchema, query, signal);

export const getOeeAnalytics = (
  query: QueryParams = {},
  signal?: AbortSignal,
) =>
  getIiotResource(
    "analytics/oee",
    oeeAnalyticsPayloadSchema,
    query,
    signal,
  );

export const getCppData = (
  equipmentId: string,
  query: QueryParams = {},
  signal?: AbortSignal,
) =>
  getIiotResource(
    "reports/cpp",
    cppRecordListSchema,
    { equipmentId, ...query },
    signal,
  );

export const getAlarmEventData = (
  equipmentId: string,
  query: QueryParams = {},
  signal?: AbortSignal,
) =>
  getIiotResource(
    "reports/alarm-events",
    alarmEventRecordListSchema,
    { equipmentId, ...query },
    signal,
  );

export const getCriticalParameterLimits = (
  query: QueryParams = {},
  signal?: AbortSignal,
) =>
  getIiotResource(
    "critical-parameter-limits",
    criticalParameterLimitListSchema,
    query,
    signal,
  );

export const acknowledgeAlarmEvent = (
  equipmentId: string,
  eventId: string,
  payload: { acknowledgedBy?: string; reason?: string; comment?: string } = {},
) =>
  apiClient<BackendApiResponse<unknown>>(
    resourcePath(
      `reports/alarm-events/${encodeURIComponent(equipmentId)}/${encodeURIComponent(eventId)}/acknowledge`,
    ),
    {
      method: "POST",
      body: payload,
    },
  );
