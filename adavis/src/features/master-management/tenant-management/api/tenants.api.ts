import { apiClient, ensureApiSuccess, parseApiData, withQuery } from "@/api";
import type { BackendApiResponse } from "@/api/types";
import { tenantSchema, tenantsSchema } from "../schemas";
import type { CreateTenantRequest, Tenant, UpdateTenantRequest } from "./types";

const root = "/api/master-management/mdm/tenants";

const unwrapCollectionPayload = (value: unknown): unknown[] => {
  if (Array.isArray(value)) return value;
  if (!value || typeof value !== "object") return [];

  const record = value as Record<string, unknown>;
  const candidates = [record.data, record.items, record.content, record.records, record.results];
  const list = candidates.find(Array.isArray);
  return Array.isArray(list) ? list : [];
};

const textId = (value: unknown, fallback = "") => {
  if (typeof value === "string" && value.trim()) return value;
  if (value && typeof value === "object" && !Array.isArray(value)) {
    const record = value as Record<string, unknown>;
    return textId(record.id ?? record._id ?? record.tenantId ?? record.companyCode ?? record.code, fallback);
  }
  return fallback;
};

const normalizeTenants = (value: unknown) =>
  unwrapCollectionPayload(value).map((item) => {
    if (!item || typeof item !== "object" || Array.isArray(item)) return item;

    const record = item as Record<string, unknown>;
    return {
      ...record,
      tenantId: textId(record.tenantId, textId(record.companyCode)),
    };
  });

const normalizeTenantRequest = <TRequest extends CreateTenantRequest | UpdateTenantRequest>(
  request: TRequest,
) => ({
  ...request,
  domain: request.domain.trim() || undefined,
});

export async function getTenants(isActive?: boolean, signal?: AbortSignal) {
  const result = await apiClient<BackendApiResponse<Tenant[]>>(
    withQuery(root, isActive === undefined ? undefined : { isActive }),
    { signal },
  );
  return parseApiData(
    { ...result, data: normalizeTenants(result.data) },
    tenantsSchema,
    "Unable to load tenants.",
    "The tenant service returned an invalid response.",
  );
}

export async function getAllTenants(signal?: AbortSignal) {
  const [active, inactive] = await Promise.all([
    getTenants(true, signal),
    getTenants(false, signal),
  ]);
  return [...active, ...inactive].sort((left, right) => left.tenantId.localeCompare(right.tenantId));
}

export async function createTenant(request: CreateTenantRequest) {
  const result = await apiClient<BackendApiResponse<Tenant>, CreateTenantRequest>(root, {
    method: "POST",
    body: normalizeTenantRequest(request),
  });
  return parseApiData(
    result,
    tenantSchema,
    "Unable to create tenant.",
    "The tenant service returned an invalid response.",
  );
}

export async function updateTenant(tenantId: string, request: UpdateTenantRequest) {
  const result = await apiClient<BackendApiResponse<Tenant>, UpdateTenantRequest>(
    `${root}/${encodeURIComponent(tenantId)}`,
    { method: "PUT", body: normalizeTenantRequest(request) },
  );
  return parseApiData(
    result,
    tenantSchema,
    "Unable to update tenant.",
    "The tenant service returned an invalid response.",
  );
}

export async function deactivateTenant(tenantId: string) {
  const result = await apiClient<BackendApiResponse<null>>(
    `${root}/${encodeURIComponent(tenantId)}/deactivate`,
    { method: "POST" },
  );
  ensureApiSuccess(result, "Unable to deactivate tenant.");
  return result;
}

export async function activateTenant(tenantId: string) {
  const result = await apiClient<BackendApiResponse<Tenant>>(
    `${root}/${encodeURIComponent(tenantId)}/activate`,
    { method: "POST" },
  );
  return parseApiData(
    result,
    tenantSchema,
    "Unable to activate tenant.",
    "The tenant service returned an invalid response.",
  );
}
