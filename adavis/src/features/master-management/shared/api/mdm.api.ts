import { apiClient, parseApiData, withQuery } from "@/api";
import type { BackendApiResponse } from "@/api/types";
import type { QueryParams } from "@/api/query";
import type { z } from "zod";

const MDM_PROXY_ROOT = "/api/master-management/mdm";

const resourcePath = (path: string) =>
  `${MDM_PROXY_ROOT}/${path.replace(/^\/+/, "")}`;

export async function getMdmResource<TSchema extends z.ZodTypeAny>(
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
    "The MDM request failed.",
    "The MDM service returned an invalid response.",
  );
}

export async function mutateMdmResource<
  TSchema extends z.ZodTypeAny,
  TBody = unknown,
>(
  path: string,
  method: "POST" | "PUT" | "PATCH" | "DELETE",
  schema: TSchema,
  body?: TBody,
): Promise<z.infer<TSchema>> {
  const response = await apiClient<BackendApiResponse<unknown>, TBody>(
    resourcePath(path),
    { method, body },
  );
  return parseApiData(
    response,
    schema,
    "The MDM request failed.",
    "The MDM service returned an invalid response.",
  );
}
