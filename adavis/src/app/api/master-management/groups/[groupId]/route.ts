import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { API_ENDPOINTS } from "@/api";
import { serverApiClient } from "@/api/server-client";
import { SERVER_API_CONFIG } from "@/api/server-config";
import type { BackendApiResponse } from "@/api/types";
import { SELECTED_PLANT_HEADER } from "@/utils/plantSelection";
import { AUTH_COOKIE_NAMES } from "@/features/auth/api/auth.server";
import type {
  Group,
  UpdateGroupRequest,
} from "@/features/master-management/user-group-management/api/types";
import {
  groupSchema,
  updateGroupRequestSchema,
} from "@/features/master-management/user-group-management/schemas";

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
  { params }: { params: Promise<{ groupId: string }> },
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

  const parsedRequest = updateGroupRequestSchema.safeParse(body);
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

  const { groupId } = await params;
  const selectedPlantId = request.headers.get(SELECTED_PLANT_HEADER)?.trim();

  try {
    const { response, result } = await serverApiClient<
      Group,
      UpdateGroupRequest
    >(
      SERVER_API_CONFIG.mdmServiceUrl,
      API_ENDPOINTS.masterManagement.userGroupDetail(groupId),
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

    const parsedGroup = groupSchema.safeParse(result.data);
    if (!parsedGroup.success) {
      return NextResponse.json(
        {
          success: false,
          message: "The group service returned an invalid response.",
          errorCode: "INVALID_UPSTREAM_RESPONSE",
          timestamp: new Date().toISOString(),
        } satisfies BackendApiResponse<never>,
        { status: 502 },
      );
    }

    return NextResponse.json({ ...result, data: parsedGroup.data });
  } catch {
    return NextResponse.json(
      {
        success: false,
        message: "The group service is unavailable.",
        errorCode: "GROUP_SERVICE_UNAVAILABLE",
        timestamp: new Date().toISOString(),
      } satisfies BackendApiResponse<never>,
      { status: 503 },
    );
  }
}

export async function DELETE(
  request: Request,
  { params }: { params: Promise<{ groupId: string }> },
) {
  const accessToken = (await cookies()).get(
    AUTH_COOKIE_NAMES.accessToken,
  )?.value;
  if (!accessToken) return unauthorizedResponse();

  const { groupId } = await params;
  const selectedPlantId = request.headers.get(SELECTED_PLANT_HEADER)?.trim();

  try {
    const { response, result } = await serverApiClient<null>(
      SERVER_API_CONFIG.mdmServiceUrl,
      API_ENDPOINTS.masterManagement.userGroupDetail(groupId),
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
        message: "The group service is unavailable.",
        errorCode: "GROUP_SERVICE_UNAVAILABLE",
        timestamp: new Date().toISOString(),
      } satisfies BackendApiResponse<never>,
      { status: 503 },
    );
  }
}
