import { apiClient, ApiError, APP_API_ENDPOINTS, withQuery } from "@/api";
import type { BackendApiResponse } from "@/api/types";
import {
  auditCountSchema,
  auditLogSchema,
  auditLogsArraySchema,
  auditLogsResultSchema,
} from "../schemas";
import type {
  AuditActionQuery,
  AuditCount,
  AuditCountByActionQuery,
  AuditEntityQuery,
  AuditListQuery,
  AuditLog,
  AuditLogsPage,
  AuditLogsResult,
  AuditTenantQuery,
  CreateAuditLogRequest,
} from "./types";

const parseData = <TData>(
  result: BackendApiResponse<TData>,
  fallbackMessage: string,
) => {
  if (!result.success || result.data === undefined || result.data === null) {
    throw new ApiError({
      status: 400,
      message: result.message || fallbackMessage,
      details: result,
    });
  }

  return result.data;
};

const toAuditLog = (value: unknown): AuditLog | null => {
  if (!value || typeof value !== "object") return null;

  const record = value as Record<string, unknown>;
  const userId =
    typeof record.userId === "string"
      ? record.userId
      : typeof record.user_id === "string"
        ? record.user_id
        : undefined;
  const username =
    typeof record.username === "string"
      ? record.username
      : typeof record.userName === "string"
        ? record.userName
        : undefined;
  const timestamp =
    typeof record.timestamp === "string"
      ? record.timestamp
      : typeof record.loginTime === "string"
        ? record.loginTime
        : typeof record.createdAt === "string"
          ? record.createdAt
          : undefined;

  return {
    action:
      typeof record.action === "string" && record.action.trim().length > 0
        ? record.action
        : "LOGIN",
    userId,
    username,
    timestamp,
    createdAt: typeof record.createdAt === "string" ? record.createdAt : timestamp,
    metadata:
      record.metadata && typeof record.metadata === "object"
        ? (record.metadata as Record<string, unknown>)
        : undefined,
  };
};

const parseAuditLogsResultLoose = (
  value: unknown,
  fallbackPageSize = 20,
): AuditLogsPage | null => {
  const strict = auditLogsResultSchema.safeParse(value);
  if (strict.success) {
    return normalizeAuditLogsPage(strict.data, fallbackPageSize);
  }

  if (!value || typeof value !== "object") return null;

  const record = value as Record<string, unknown>;
  const rawCollection = [
    record.content,
    record.items,
    record.records,
    record.logs,
    record.data,
  ].find(Array.isArray) as unknown[] | undefined;

  if (!rawCollection) return null;

  const content = rawCollection
    .map((item) => toAuditLog(item))
    .filter((item): item is AuditLog => Boolean(item));

  const totalElements =
    typeof record.totalElements === "number"
      ? record.totalElements
      : typeof record.total === "number"
        ? record.total
        : content.length;
  const pageNumber =
    typeof record.pageNumber === "number"
      ? record.pageNumber
      : typeof record.page === "number"
        ? record.page
        : 0;
  const pageSize =
    typeof record.pageSize === "number"
      ? record.pageSize
      : typeof record.size === "number"
        ? record.size
        : fallbackPageSize;
  const totalPages =
    typeof record.totalPages === "number"
      ? record.totalPages
      : pageSize > 0
        ? Math.ceil(totalElements / pageSize)
        : 0;

  return {
    content,
    totalElements,
    totalPages,
    pageNumber,
    pageSize,
    first: pageNumber <= 0,
    last: totalPages <= 1 || pageNumber >= totalPages - 1,
    hasNext: totalPages > 0 && pageNumber < totalPages - 1,
    hasPrevious: pageNumber > 0,
  };
};

export const normalizeAuditLogsPage = (
  data: AuditLogsResult,
  fallbackPageSize = 20,
): AuditLogsPage => {
  if (Array.isArray(data)) {
    return {
      content: data,
      totalElements: data.length,
      totalPages: data.length > 0 ? 1 : 0,
      pageNumber: 0,
      pageSize: fallbackPageSize,
      first: true,
      last: true,
      hasNext: false,
      hasPrevious: false,
    };
  }

  return {
    ...data,
    pageNumber: data.pageNumber ?? 0,
    pageSize: data.pageSize ?? fallbackPageSize,
    first: data.first ?? true,
    last: data.last ?? true,
    hasNext: data.hasNext ?? false,
    hasPrevious: data.hasPrevious ?? false,
  };
};

export const getAuditLogs = async (
  query: AuditListQuery,
  signal?: AbortSignal,
) => {
  const result = await apiClient<BackendApiResponse<AuditLogsResult>>(
    withQuery(APP_API_ENDPOINTS.audit.logs, query),
    { signal },
  );
  const data = parseData(result, "Unable to load audit logs.");
  const parsedLogs = auditLogsResultSchema.safeParse(data);

  if (!parsedLogs.success) {
    throw new ApiError({
      status: 502,
      message: "The audit service returned an invalid response.",
      details: parsedLogs.error.flatten(),
    });
  }

  return normalizeAuditLogsPage(parsedLogs.data, query.size);
};

export const getAuditLog = async (auditId: string, signal?: AbortSignal) => {
  const result = await apiClient<BackendApiResponse<AuditLog>>(
    APP_API_ENDPOINTS.audit.detail(auditId),
    { signal },
  );
  const data = parseData(result, "Unable to load audit log.");
  const parsedLog = auditLogSchema.safeParse(data);

  if (!parsedLog.success) {
    throw new ApiError({
      status: 502,
      message: "The audit service returned an invalid response.",
      details: parsedLog.error.flatten(),
    });
  }

  return parsedLog.data;
};

export const getAuditLogsByEntity = async (
  query: AuditEntityQuery,
  signal?: AbortSignal,
) => {
  const result = await apiClient<BackendApiResponse<AuditLog[]>>(
    withQuery(APP_API_ENDPOINTS.audit.byEntity, query),
    { signal },
  );
  const data = parseData(result, "Unable to load audit logs by entity.");
  const parsedLogs = auditLogsArraySchema.safeParse(data);

  if (!parsedLogs.success) {
    throw new ApiError({
      status: 502,
      message: "The audit service returned an invalid response.",
      details: parsedLogs.error.flatten(),
    });
  }

  return parsedLogs.data;
};

export const getAuditLogsByUser = async (
  userId: string,
  query: AuditListQuery,
  signal?: AbortSignal,
) => {
  const page = await getAuditLogs(query, signal);
  const normalizedUserId = userId.trim().toLowerCase();
  const content = page.content.filter((log) => {
    const logUserId = log.userId?.trim().toLowerCase();
    const username = log.username?.trim().toLowerCase();
    return logUserId === normalizedUserId || username === normalizedUserId;
  });

  return {
    ...page,
    content,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    first: true,
    last: true,
    hasNext: false,
    hasPrevious: false,
  };
};

export const getAuditLogsByAction = async (
  query: AuditActionQuery,
  signal?: AbortSignal,
) => {
  const result = await apiClient<BackendApiResponse<AuditLogsResult>>(
    withQuery(APP_API_ENDPOINTS.audit.byAction, query),
    { signal },
  );
  const data = parseData(result, "Unable to load action audit logs.");
  const normalized = parseAuditLogsResultLoose(data, query.size);

  if (!normalized) {
    throw new ApiError({
      status: 502,
      message: "The audit service returned an invalid response.",
      details: { receivedDataType: typeof data },
    });
  }

  return normalized;
};

export const getAuditLogsByTenant = async (
  tenantId: string,
  query: AuditTenantQuery,
  signal?: AbortSignal,
) => {
  const result = await apiClient<BackendApiResponse<AuditLog[]>>(
    withQuery(APP_API_ENDPOINTS.audit.byTenant(tenantId), query),
    { signal },
  );
  const data = parseData(result, "Unable to load tenant audit logs.");
  const parsedLogs = auditLogsArraySchema.safeParse(data);

  if (!parsedLogs.success) {
    throw new ApiError({
      status: 502,
      message: "The audit service returned an invalid response.",
      details: parsedLogs.error.flatten(),
    });
  }

  return parsedLogs.data;
};

export const countAuditLogsByAction = async (
  query: AuditCountByActionQuery,
  signal?: AbortSignal,
) => {
  const result = await apiClient<BackendApiResponse<AuditCount>>(
    withQuery(APP_API_ENDPOINTS.audit.countByAction, query),
    { signal },
  );
  const data = parseData(result, "Unable to count action audit logs.");
  const parsedCount = auditCountSchema.safeParse(data);

  if (!parsedCount.success) {
    throw new ApiError({
      status: 502,
      message: "The audit service returned an invalid response.",
      details: parsedCount.error.flatten(),
    });
  }

  return parsedCount.data;
};

export const createAuditLog = async (request: CreateAuditLogRequest) => {
  const result = await apiClient<
    BackendApiResponse<AuditLog>,
    CreateAuditLogRequest
  >(APP_API_ENDPOINTS.audit.logs, {
    method: "POST",
    body: request,
  });
  const data = parseData(result, "Unable to create audit log.");
  const parsedLog = auditLogSchema.safeParse(data);

  if (!parsedLog.success) {
    throw new ApiError({
      status: 502,
      message: "The audit service returned an invalid response.",
      details: parsedLog.error.flatten(),
    });
  }

  return parsedLog.data;
};
