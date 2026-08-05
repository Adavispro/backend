import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { API_ENDPOINTS, withQuery } from "@/api";
import type { QueryParams } from "@/api";
import { serverApiClient } from "@/api/server-client";
import { SERVER_API_CONFIG } from "@/api/server-config";
import type { ApiMethod, BackendApiResponse } from "@/api/types";
import { AUTH_COOKIE_NAMES } from "@/features/auth/api/auth.server";
import type { z } from "zod";

export const auditUnauthorizedResponse = () =>
  NextResponse.json(
    {
      success: false,
      message: "Not authenticated.",
      errorCode: "UNAUTHORIZED",
      timestamp: new Date().toISOString(),
    } satisfies BackendApiResponse<never>,
    { status: 401 },
  );

export const auditValidationResponse = (message: string) =>
  NextResponse.json(
    {
      success: false,
      message,
      errorCode: "VALIDATION_ERROR",
      timestamp: new Date().toISOString(),
    } satisfies BackendApiResponse<never>,
    { status: 400 },
  );

export const getAuditAccessToken = async () => {
  const cookieStore = await cookies();
  return cookieStore.get(AUTH_COOKIE_NAMES.accessToken)?.value;
};

export const readJsonBody = async (request: Request) => {
  try {
    return await request.json();
  } catch {
    return undefined;
  }
};

export const parseSearchParams = <TQuery>(
  request: Request,
  schema: z.ZodType<TQuery>,
) => {
  const url = new URL(request.url);
  const rawQuery = Object.fromEntries(url.searchParams.entries());
  return schema.safeParse(rawQuery);
};

export const proxyAuditRequest = async <TData, TBody = unknown>(
  path: string,
  options: {
    accessToken: string;
    body?: TBody;
    method?: ApiMethod;
    query?: QueryParams;
    unavailableMessage?: string;
  },
) => {
  const {
    accessToken,
    body,
    method = "GET",
    query,
    unavailableMessage = "The audit service is unavailable.",
  } = options;

  try {
    const { response, result } = await serverApiClient<TData, TBody>(
      SERVER_API_CONFIG.gatewayUrl,
      withQuery(path, query),
      {
        method,
        body,
        headers: { Authorization: `Bearer ${accessToken}` },
      },
    );

    return NextResponse.json(result, { status: response.status });
  } catch {
    return NextResponse.json(
      {
        success: false,
        message: unavailableMessage,
        errorCode: "AUDIT_SERVICE_UNAVAILABLE",
        timestamp: new Date().toISOString(),
      } satisfies BackendApiResponse<never>,
      { status: 503 },
    );
  }
};

export { API_ENDPOINTS };
