import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { API_ENDPOINTS } from "@/api";
import { serverApiClient } from "@/api/server-client";
import type { BackendApiResponse } from "@/api/types";
import {
  AUTH_COOKIE_NAMES,
  authErrorResponse,
} from "@/features/auth/api/auth.server";
import { SERVER_API_CONFIG } from "@/api/server-config";
import { userAccountActionSchema } from "@/features/auth/schemas";

export async function PATCH(
  _request: Request,
  {
    params,
  }: {
    params: Promise<{ userId: string; action: string }>;
  },
) {
  const { action, userId } = await params;
  const parsedAction = userAccountActionSchema.safeParse(action);

  if (!parsedAction.success) {
    return authErrorResponse(400, { message: "Invalid user status action." });
  }

  const cookieStore = await cookies();
  const accessToken = cookieStore.get(AUTH_COOKIE_NAMES.accessToken)?.value;

  if (!accessToken) {
    return authErrorResponse(401, { message: "Not authenticated." });
  }

  try {
    const { response, result } = await serverApiClient<null>(
      SERVER_API_CONFIG.authServiceUrl,
      API_ENDPOINTS.auth.accountAction(userId, parsedAction.data),
      {
        method: "PATCH",
        headers: { Authorization: `Bearer ${accessToken}` },
      },
    );

    return NextResponse.json(result, { status: response.status });
  } catch {
    return NextResponse.json(
      {
        success: false,
        message: "The authentication service is unavailable.",
        errorCode: "AUTH_SERVICE_UNAVAILABLE",
        timestamp: new Date().toISOString(),
      } satisfies BackendApiResponse<never>,
      { status: 503 },
    );
  }
}
