import { API_CONFIG } from "./config";
import { APP_API_ENDPOINTS } from "./endpoints";
import type { ApiErrorResponse, ApiRequestOptions } from "./types";
import {
  readSelectedPlantId,
  SELECTED_PLANT_HEADER,
} from "@/utils/plantSelection";

let refreshRequest: Promise<boolean> | null = null;

const refreshSession = () => {
  if (typeof window === "undefined") return Promise.resolve(false);

  refreshRequest ??= fetch(APP_API_ENDPOINTS.auth.refresh, {
    method: "POST",
  })
    .then((response) => response.ok)
    .catch(() => false)
    .finally(() => {
      refreshRequest = null;
    });

  return refreshRequest;
};

export class ApiError extends Error {
  status: number;
  details?: unknown;

  constructor(error: ApiErrorResponse) {
    super(error.message);
    this.name = "ApiError";
    this.status = error.status;
    this.details = error.details;
  }
}

const buildUrl = (path: string, baseUrl: string) => {
  if (/^https?:\/\//.test(path)) return path;
  if (path.startsWith("/")) return path;
  return `${baseUrl}${path}`;
};

const shouldAttachPlantSelection = (path: string) =>
  (path.startsWith("/api/master-management/") &&
    !path.startsWith("/api/master-management/mdm/tenants") &&
    !path.startsWith("/api/master-management/iiot/")) ||
  path.startsWith("/api/iiot/") ||
  path.startsWith("/api/audit/");

const appendPlantQuery = (path: string, plantId: string) => {
  if (!plantId || !shouldAttachPlantSelection(path)) return path;
  const hasPlant = /[?&]selectedPlantId=/.test(path);
  if (hasPlant) return path;

  const separator = path.includes("?") ? "&" : "?";
  return `${path}${separator}selectedPlantId=${encodeURIComponent(plantId)}`;
};

const parseResponse = async <TResponse>(response: Response) => {
  const contentType = response.headers.get("content-type") ?? "";

  if (response.status === 204) return undefined as TResponse;

  if (contentType.includes("application/json")) {
    return (await response.json()) as TResponse;
  }

  return (await response.text()) as TResponse;
};

export const apiClient = async <TResponse, TBody = unknown>(
  path: string,
  options: ApiRequestOptions<TBody> = {},
) => {
  const {
    body,
    baseUrl = API_CONFIG.baseUrl,
    headers,
    method = "GET",
    retryOnUnauthorized = true,
    timeoutMs = API_CONFIG.timeoutMs,
    ...requestOptions
  } = options;

  const selectedPlantId = readSelectedPlantId();
  const scopedPath = appendPlantQuery(path, selectedPlantId);
  const scopedHeaders = {
    ...(selectedPlantId && shouldAttachPlantSelection(path)
      ? { [SELECTED_PLANT_HEADER]: selectedPlantId }
      : {}),
    ...headers,
  };

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const request = () => fetch(buildUrl(scopedPath, baseUrl), {
      ...requestOptions,
      method,
      headers: {
        "Content-Type": "application/json",
        ...scopedHeaders,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    });

    let response = await request();

    if (
      response.status === 401 &&
      retryOnUnauthorized &&
      typeof window !== "undefined"
    ) {
      const refreshed = await refreshSession();

      if (refreshed) {
        response = await request();

        if (response.status === 401) {
          window.location.replace("/auth");
        }
      } else {
        window.location.replace("/auth");
      }
    }

    const data = await parseResponse<TResponse>(response);

    if (!response.ok) {
      throw new ApiError({
        status: response.status,
        message:
          typeof data === "object" && data && "message" in data
            ? String(data.message)
            : "Request failed.",
        details: data,
      });
    }

    return data;
  } finally {
    clearTimeout(timeout);
  }
};
