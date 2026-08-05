import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { API_ENDPOINTS, withQuery } from "@/api";
import { serverApiClient } from "@/api/server-client";
import { SERVER_API_CONFIG } from "@/api/server-config";
import type { BackendApiResponse } from "@/api/types";
import { SELECTED_PLANT_HEADER } from "@/utils/plantSelection";
import { AUTH_COOKIE_NAMES } from "@/features/auth/api/auth.server";
import type {
  CreateUserRequest,
  User,
  UsersPage,
} from "@/features/master-management/user-management/api/types";
import {
  createUserRequestSchema,
  userSchema,
  usersListQuerySchema,
  usersPageSchema,
} from "@/features/master-management/user-management/schemas";

const INTERNAL_AUTH_HEADER = "X-Internal-Auth";
const INTERNAL_AUTH_VALUE =
  process.env.INTERNAL_AUTH_HEADER_VALUE ??
  process.env.SECURITY_INTERNAL_AUTH_HEADER ??
  "adavis-internal-auth-key";

export async function GET(request: Request) {
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

  const url = new URL(request.url);
  const parsedQuery = usersListQuerySchema.safeParse({
    page: url.searchParams.get("page") ?? undefined,
    size: url.searchParams.get("size") ?? undefined,
    isActive: url.searchParams.get("isActive") ?? undefined,
    isBlocked: url.searchParams.get("isBlocked") ?? undefined,
    lifecycleStatus: url.searchParams.get("lifecycleStatus") ?? undefined,
  });

  if (!parsedQuery.success) {
    return NextResponse.json(
      {
        success: false,
        message: parsedQuery.error.issues[0]?.message ?? "Invalid query.",
        errorCode: "VALIDATION_ERROR",
        timestamp: new Date().toISOString(),
      } satisfies BackendApiResponse<never>,
      { status: 400 },
    );
  }

  const selectedPlantId = request.headers.get(SELECTED_PLANT_HEADER)?.trim();

  try {
    const { response, result } = await serverApiClient<UsersPage>(
      SERVER_API_CONFIG.mdmServiceUrl,
      withQuery(API_ENDPOINTS.masterManagement.users, {
        ...parsedQuery.data,
        selectedPlantId,
      }),
      {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          ...(selectedPlantId ? { [SELECTED_PLANT_HEADER]: selectedPlantId } : {}),
        },
      },
    );

    if (!response.ok || !result.success || !result.data) {
      return NextResponse.json(result, { status: response.status });
    }

    const parsedUsers = usersPageSchema.safeParse(result.data);
    if (!parsedUsers.success) {
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

    return NextResponse.json({ ...result, data: parsedUsers.data });
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

export async function POST(request: Request) {
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
        message: "Invalid create user request.",
        errorCode: "VALIDATION_ERROR",
        timestamp: new Date().toISOString(),
      } satisfies BackendApiResponse<never>,
      { status: 400 },
    );
  }

  const parsedRequest = createUserRequestSchema.safeParse(body);
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

  try {
    const selectedPlantId = request.headers.get(SELECTED_PLANT_HEADER)?.trim();
    const { response, result } = await serverApiClient<
      User,
      CreateUserRequest
    >(
      SERVER_API_CONFIG.mdmServiceUrl,
      API_ENDPOINTS.masterManagement.onboardUser,
      {
        method: "POST",
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

    return NextResponse.json({ ...result, data: parsedUser.data }, { status: 201 });
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
