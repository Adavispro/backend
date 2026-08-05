import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { serverApiClient } from "@/api/server-client";
import { SERVER_API_CONFIG } from "@/api/server-config";
import type { ApiMethod, BackendApiResponse } from "@/api/types";
import { SELECTED_PLANT_HEADER } from "@/utils/plantSelection";
import { AUTH_COOKIE_NAMES } from "@/features/auth/api/auth.server";

const INTERNAL_AUTH_HEADER = "X-Internal-Auth";
const INTERNAL_AUTH_VALUE =
  process.env.INTERNAL_AUTH_HEADER_VALUE ??
  process.env.SECURITY_INTERNAL_AUTH_HEADER ??
  "adavis-internal-auth-key";

const allowedRoots = new Set([
  "areas",
  "assignments",
  "blocks",
  "departments",
  "features",
  "license",
  "modules",
  "permissions",
  "plants",
  "roles",
  "rooms",
  "screens",
  "tenants",
  "user-groups",
  "users",
]);

const errorResponse = (status: number, message: string, errorCode: string) =>
  NextResponse.json(
    {
      success: false,
      message,
      errorCode,
      timestamp: new Date().toISOString(),
    } satisfies BackendApiResponse<never>,
    { status },
  );

async function proxy(
  request: Request,
  context: { params: Promise<{ path: string[] }> },
  method: ApiMethod,
) {
  const accessToken = (await cookies()).get(
    AUTH_COOKIE_NAMES.accessToken,
  )?.value;
  if (!accessToken) {
    return errorResponse(401, "Not authenticated.", "UNAUTHORIZED");
  }

  const { path } = await context.params;
  if (!path.length || !allowedRoots.has(path[0])) {
    return errorResponse(404, "Unknown MDM resource.", "NOT_FOUND");
  }

  const requestUrl = new URL(request.url);
  const selectedPlantId = request.headers.get(SELECTED_PLANT_HEADER)?.trim();
  const upstreamPath = `/api/v1/mdm/${path.map(encodeURIComponent).join("/")}${requestUrl.search}`;
  let body: unknown;

  if (method !== "GET" && method !== "DELETE") {
    try {
      body = await request.json();
    } catch {
      body = undefined;
    }
  }

  try {
    const internalHeaders: Record<string, string> =
      method === "GET"
        ? {}
        : { [INTERNAL_AUTH_HEADER]: INTERNAL_AUTH_VALUE };

    const { response, result } = await serverApiClient<unknown, unknown>(
      SERVER_API_CONFIG.mdmServiceUrl,
      upstreamPath,
      {
        method,
        body,
        headers: {
          Authorization: `Bearer ${accessToken}`,
          ...(selectedPlantId ? { [SELECTED_PLANT_HEADER]: selectedPlantId } : {}),
          ...internalHeaders,
        },
      },
    );
    return NextResponse.json(result, { status: response.status });
  } catch {
    return errorResponse(
      503,
      "The Master Data Management service is unavailable.",
      "MDM_SERVICE_UNAVAILABLE",
    );
  }
}

type RouteContext = { params: Promise<{ path: string[] }> };

export const GET = (request: Request, context: RouteContext) =>
  proxy(request, context, "GET");
export const POST = (request: Request, context: RouteContext) =>
  proxy(request, context, "POST");
export const PUT = (request: Request, context: RouteContext) =>
  proxy(request, context, "PUT");
export const PATCH = (request: Request, context: RouteContext) =>
  proxy(request, context, "PATCH");
export const DELETE = (request: Request, context: RouteContext) =>
  proxy(request, context, "DELETE");
