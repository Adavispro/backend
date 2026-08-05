import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { API_ENDPOINTS } from "@/api";
import { serverApiClient } from "@/api/server-client";
import { SERVER_API_CONFIG } from "@/api/server-config";
import type { BackendApiResponse } from "@/api/types";
import { SELECTED_PLANT_HEADER } from "@/utils/plantSelection";
import { AUTH_COOKIE_NAMES } from "@/features/auth/api/auth.server";
import type {
  Department,
  UpdateDepartmentRequest,
} from "@/features/master-management/department-management/api/types";
import {
  departmentSchema,
  updateDepartmentRequestSchema,
} from "@/features/master-management/department-management/schemas";

const INTERNAL_AUTH_HEADER = "X-Internal-Auth";
const INTERNAL_AUTH_VALUE =
  process.env.INTERNAL_AUTH_HEADER_VALUE ??
  process.env.SECURITY_INTERNAL_AUTH_HEADER ??
  "adavis-internal-auth-key";

const unauthorizedResponse = () =>
  NextResponse.json(
    {
      success: false,
      message: "Not authenticated.",
      errorCode: "UNAUTHORIZED",
      timestamp: new Date().toISOString(),
    } satisfies BackendApiResponse<never>,
    { status: 401 },
  );

export async function PUT(
  request: Request,
  { params }: { params: Promise<{ departmentId: string }> },
) {
  const accessToken = (await cookies()).get(
    AUTH_COOKIE_NAMES.accessToken,
  )?.value;
  if (!accessToken) return unauthorizedResponse();

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    body = null;
  }

  const parsedRequest = updateDepartmentRequestSchema.safeParse(body);
  if (!parsedRequest.success) {
    return NextResponse.json(
      {
        success: false,
        message: parsedRequest.error.issues[0]?.message ?? "Invalid request.",
        errorCode: "VALIDATION_ERROR",
        timestamp: new Date().toISOString(),
      } satisfies BackendApiResponse<never>,
      { status: 400 },
    );
  }

  const { departmentId } = await params;
  const selectedPlantId = request.headers.get(SELECTED_PLANT_HEADER)?.trim();

  try {
    const { response, result } = await serverApiClient<
      Department,
      UpdateDepartmentRequest
    >(
      SERVER_API_CONFIG.mdmServiceUrl,
      API_ENDPOINTS.masterManagement.departmentDetail(departmentId),
      {
        method: "PUT",
        body: parsedRequest.data,
        headers: {
          Authorization: `Bearer ${accessToken}`,
          ...(selectedPlantId ? { [SELECTED_PLANT_HEADER]: selectedPlantId } : {}),
          [INTERNAL_AUTH_HEADER]: INTERNAL_AUTH_VALUE,
        },
      },
    );

    if (!response.ok || !result.success || !result.data) {
      return NextResponse.json(result, { status: response.status });
    }

    const parsedDepartment = departmentSchema.safeParse(result.data);
    if (!parsedDepartment.success) {
      return NextResponse.json(
        {
          success: false,
          message: "The department service returned an invalid response.",
          errorCode: "INVALID_UPSTREAM_RESPONSE",
          timestamp: new Date().toISOString(),
        } satisfies BackendApiResponse<never>,
        { status: 502 },
      );
    }

    return NextResponse.json({ ...result, data: parsedDepartment.data });
  } catch {
    return NextResponse.json(
      {
        success: false,
        message: "The department service is unavailable.",
        errorCode: "DEPARTMENT_SERVICE_UNAVAILABLE",
        timestamp: new Date().toISOString(),
      } satisfies BackendApiResponse<never>,
      { status: 503 },
    );
  }
}

export async function DELETE(
  request: Request,
  { params }: { params: Promise<{ departmentId: string }> },
) {
  const accessToken = (await cookies()).get(
    AUTH_COOKIE_NAMES.accessToken,
  )?.value;
  if (!accessToken) return unauthorizedResponse();

  const { departmentId } = await params;
  const selectedPlantId = request.headers.get(SELECTED_PLANT_HEADER)?.trim();

  try {
    const { response, result } = await serverApiClient<null>(
      SERVER_API_CONFIG.mdmServiceUrl,
      API_ENDPOINTS.masterManagement.departmentDetail(departmentId),
      {
        method: "DELETE",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          ...(selectedPlantId ? { [SELECTED_PLANT_HEADER]: selectedPlantId } : {}),
          [INTERNAL_AUTH_HEADER]: INTERNAL_AUTH_VALUE,
        },
      },
    );

    return NextResponse.json(result, { status: response.status });
  } catch {
    return NextResponse.json(
      {
        success: false,
        message: "The department service is unavailable.",
        errorCode: "DEPARTMENT_SERVICE_UNAVAILABLE",
        timestamp: new Date().toISOString(),
      } satisfies BackendApiResponse<never>,
      { status: 503 },
    );
  }
}
