import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import {
  AUTH_COOKIE_NAMES,
  authErrorResponse,
  authServiceEndpoints,
  authServiceUnavailableResponse,
  requestAuthService,
} from "@/features/auth/api/auth.server";
import { firstSchemaError, userProvisionRequestSchema } from "@/features/auth/schemas";
import type { UserProvisionRequest } from "@/features/auth/api/types";

export async function POST(request: Request) {
  const cookieStore = await cookies();
  const accessToken = cookieStore.get(AUTH_COOKIE_NAMES.accessToken)?.value;

  if (!accessToken) {
    return authErrorResponse(401, { message: "Not authenticated." });
  }

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return authErrorResponse(400, { message: "Invalid provision request." });
  }

  const parsedRequest = userProvisionRequestSchema.safeParse(body);
  if (!parsedRequest.success) {
    return authErrorResponse(400, {
      message: firstSchemaError(parsedRequest.error),
    });
  }

  try {
    const { response, result } = await requestAuthService<null, UserProvisionRequest>(
      authServiceEndpoints.internalProvision,
      {
        method: "POST",
        body: parsedRequest.data,
        accessToken,
      },
    );

    if (!response.ok || !result.success) {
      return authErrorResponse(response.status, result);
    }

    return NextResponse.json(result);
  } catch {
    return authServiceUnavailableResponse();
  }
}