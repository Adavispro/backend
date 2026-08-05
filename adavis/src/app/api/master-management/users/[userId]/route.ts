import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { API_ENDPOINTS, withQuery } from "@/api";
import { serverApiClient } from "@/api/server-client";
import { SERVER_API_CONFIG } from "@/api/server-config";
import type { BackendApiResponse } from "@/api/types";
import { SELECTED_PLANT_HEADER } from "@/utils/plantSelection";
import { AUTH_COOKIE_NAMES } from "@/features/auth/api/auth.server";
import type {
  UpdateUserRequest,
  User,
  UsersPage,
} from "@/features/master-management/user-management/api/types";
import {
  updateUserRequestSchema,
  userSchema,
  usersPageSchema,
} from "@/features/master-management/user-management/schemas";

const INTERNAL_AUTH_HEADER = "X-Internal-Auth";
const INTERNAL_AUTH_VALUE =
  process.env.INTERNAL_AUTH_HEADER_VALUE ??
  process.env.SECURITY_INTERNAL_AUTH_HEADER ??
  "adavis-internal-auth-key";

export async function GET(
  request: Request,
  { params }: { params: Promise<{ userId: string }> },
) {
  const cookieStore = await cookies();
  const accessToken = cookieStore.get(AUTH_COOKIE_NAMES.accessToken)?.value;

  if (!accessToken) {
    return NextResponse.json(
      {
        success: false,
        message: "Not authenticated.",
        errorCode: "UNAUTHORIZED",
        timestamp: new Date().toISOString(),
      } satisfies BackendApiResponse<never>,
      { status: 401 },
    );
  }

  const { userId } = await params;
  const selectedPlantId = request.headers.get(SELECTED_PLANT_HEADER)?.trim();

  try {
    const { response, result } = await serverApiClient<User>(
      SERVER_API_CONFIG.mdmServiceUrl,
      API_ENDPOINTS.masterManagement.userDetail(userId),
      {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          ...(selectedPlantId ? { [SELECTED_PLANT_HEADER]: selectedPlantId } : {}),
          [INTERNAL_AUTH_HEADER]: INTERNAL_AUTH_VALUE,
        },
      },
    );

    if (!response.ok || !result.success || !result.data) {
      const fallback = await serverApiClient<UsersPage>(
        SERVER_API_CONFIG.mdmServiceUrl,
        withQuery(API_ENDPOINTS.masterManagement.users, {
          page: 0,
          size: 100,
          selectedPlantId,
        }),
        {
          headers: {
            Authorization: `Bearer ${accessToken}`,
            ...(selectedPlantId ? { [SELECTED_PLANT_HEADER]: selectedPlantId } : {}),
          },
        },
      );
      const parsedPage = usersPageSchema.safeParse(fallback.result.data);
      const fallbackUser = parsedPage.success
        ? parsedPage.data.content.find((user) => user.userId === userId)
        : undefined;

      if (!fallbackUser) {
        return NextResponse.json(result, { status: response.status });
      }

      return NextResponse.json({
        success: true,
        message: "User retrieved",
        data: fallbackUser,
        timestamp: new Date().toISOString(),
      } satisfies BackendApiResponse<User>);
    }

    const parsedUser = userSchema.safeParse(result.data);
    if (!parsedUser.success) {
      return NextResponse.json(
        {
          success: false,
          message: "The user service returned an invalid response.",
          errorCode: "INVALID_UPSTREAM_RESPONSE",
          timestamp: new Date().toISOString(),
        } satisfies BackendApiResponse<never>,
        { status: 502 },
      );
    }

    return NextResponse.json({ ...result, data: parsedUser.data });
  } catch {
    return NextResponse.json(
      {
        success: false,
        message: "The user service is unavailable.",
        errorCode: "USER_SERVICE_UNAVAILABLE",
        timestamp: new Date().toISOString(),
      } satisfies BackendApiResponse<never>,
      { status: 503 },
    );
  }
}

export async function PUT(
  request: Request,
  { params }: { params: Promise<{ userId: string }> },
) {
  const cookieStore = await cookies();
  const accessToken = cookieStore.get(AUTH_COOKIE_NAMES.accessToken)?.value;

  if (!accessToken) {
    return NextResponse.json(
      {
        success: false,
        message: "Not authenticated.",
        errorCode: "UNAUTHORIZED",
        timestamp: new Date().toISOString(),
      } satisfies BackendApiResponse<never>,
      { status: 401 },
    );
  }

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json(
      {
        success: false,
        message: "Invalid update user request.",
        errorCode: "VALIDATION_ERROR",
        timestamp: new Date().toISOString(),
      } satisfies BackendApiResponse<never>,
      { status: 400 },
    );
  }

  const parsedRequest = updateUserRequestSchema.safeParse(body);
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

  const { userId } = await params;
  const selectedPlantId = request.headers.get(SELECTED_PLANT_HEADER)?.trim();

  try {
    const { response, result } = await serverApiClient<
      User,
      UpdateUserRequest
    >(
      SERVER_API_CONFIG.mdmServiceUrl,
      API_ENDPOINTS.masterManagement.userDetail(userId),
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

    const parsedUser = userSchema.safeParse(result.data);
    if (!parsedUser.success) {
      return NextResponse.json(
        {
          success: false,
          message: "The user service returned an invalid response.",
          errorCode: "INVALID_UPSTREAM_RESPONSE",
          timestamp: new Date().toISOString(),
        } satisfies BackendApiResponse<never>,
        { status: 502 },
      );
    }

    return NextResponse.json({ ...result, data: parsedUser.data });
  } catch {
    return NextResponse.json(
      {
        success: false,
        message: "The user service is unavailable.",
        errorCode: "USER_SERVICE_UNAVAILABLE",
        timestamp: new Date().toISOString(),
      } satisfies BackendApiResponse<never>,
      { status: 503 },
    );
  }
}

export async function DELETE(
  request: Request,
  { params }: { params: Promise<{ userId: string }> },
) {
  const cookieStore = await cookies();
  const accessToken = cookieStore.get(AUTH_COOKIE_NAMES.accessToken)?.value;

  if (!accessToken) {
    return NextResponse.json(
      {
        success: false,
        message: "Not authenticated.",
        errorCode: "UNAUTHORIZED",
        timestamp: new Date().toISOString(),
      } satisfies BackendApiResponse<never>,
      { status: 401 },
    );
  }

  const { userId } = await params;
  const selectedPlantId = request.headers.get(SELECTED_PLANT_HEADER)?.trim();

  try {
    const { response, result } = await serverApiClient<null>(
      SERVER_API_CONFIG.mdmServiceUrl,
      API_ENDPOINTS.masterManagement.userDetail(userId),
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
        message: "The user service is unavailable.",
        errorCode: "USER_SERVICE_UNAVAILABLE",
        timestamp: new Date().toISOString(),
      } satisfies BackendApiResponse<never>,
      { status: 503 },
    );
  }
}
