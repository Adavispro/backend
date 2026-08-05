import { apiClient, APP_API_ENDPOINTS, parseApiData } from "@/api";
import type { BackendApiResponse } from "@/api/types";
import {
  licenseActionRequestSchema,
  licenseHistoryListSchema,
  licenseSchema,
} from "../schemas";
import type {
  LicenseActionRequest,
  LicenseHistory,
  LicenseUpgradeRequest,
  TenantLicense,
} from "./types";

const root = APP_API_ENDPOINTS.masterManagement.licenseBase;

export async function getTenantLicense(tenantId: string) {
  const result = await apiClient<BackendApiResponse<TenantLicense>>(
    APP_API_ENDPOINTS.masterManagement.tenantLicense(tenantId),
  );
  return parseApiData(
    result,
    licenseSchema,
    "Unable to load the tenant license.",
    "The license service returned an invalid response.",
  );
}

export async function applyLicense(request: LicenseActionRequest) {
  const payload = licenseActionRequestSchema.parse(request);
  const result = await apiClient<
    BackendApiResponse<TenantLicense>,
    LicenseActionRequest
  >(`${root}/tenant`, { method: "POST", body: payload });
  return parseApiData(
    result,
    licenseSchema,
    "Unable to apply the license action.",
    "The license service returned an invalid response.",
  );
}

export async function upgradeLicenseById(
  licenseId: string,
  request: LicenseUpgradeRequest,
) {
  const result = await apiClient<
    BackendApiResponse<TenantLicense>,
    LicenseUpgradeRequest
  >(APP_API_ENDPOINTS.masterManagement.upgradeLicense(licenseId), {
    method: "PUT",
    body: request,
  });
  return parseApiData(
    result,
    licenseSchema,
    "Unable to upgrade the license.",
    "The license service returned an invalid response.",
  );
}

export async function upgradeTenantLicense(
  tenantId: string,
  request: LicenseUpgradeRequest,
) {
  const result = await apiClient<
    BackendApiResponse<TenantLicense>,
    LicenseUpgradeRequest
  >(APP_API_ENDPOINTS.masterManagement.upgradeTenantLicense(tenantId), {
    method: "PUT",
    body: request,
  });
  return parseApiData(
    result,
    licenseSchema,
    "Unable to upgrade the license.",
    "The license service returned an invalid response.",
  );
}

export async function getLicenseHistory(tenantId: string): Promise<LicenseHistory[]> {
  const result = await apiClient<BackendApiResponse<LicenseHistory[]>>(
    APP_API_ENDPOINTS.masterManagement.licenseHistory(tenantId),
  );
  return parseApiData(
    result,
    licenseHistoryListSchema,
    "Unable to load license history.",
    "The license service returned an invalid response.",
  );
}
