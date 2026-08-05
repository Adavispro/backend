import { NextResponse } from "next/server";
import {
  authErrorResponse,
  authServiceEndpoints,
  authServiceUnavailableResponse,
  requestAuthService,
  setAuthCookies,
} from "@/features/auth/api/auth.server";
import {
  authTokensSchema,
  firstSchemaError,
  loginRequestSchema,
} from "@/features/auth/schemas";
import type {
  AuthClientResponse,
  AuthTokens,
  LoginRequest,
} from "@/features/auth/api/types";
import type { BackendApiResponse } from "@/api/types";

export async function POST(request: Request) {
  let body: unknown;

  try {
    body = await request.json();
  } catch {
    return authErrorResponse(400, { message: "Invalid login request." });
  }

  const parsedRequest = loginRequestSchema.safeParse(body);
  if (!parsedRequest.success) {
    return authErrorResponse(400, {
      message: firstSchemaError(parsedRequest.error),
    });
  }

  const credentials: LoginRequest = parsedRequest.data;

  try {
    const { response, result } = await requestAuthService<
      AuthTokens,
      LoginRequest
    >(authServiceEndpoints.login, {
      method: "POST",
      body: credentials,
    });

    if (!response.ok || !result.success || !result.data) {
      return authErrorResponse(response.status, result);
    }

    const parsedTokens = authTokensSchema.safeParse(result.data);
    if (!parsedTokens.success) {
      return authErrorResponse(502, {
        message: "The authentication service returned an invalid response.",
      });
    }

    const tokens = parsedTokens.data;
    const clientResult: BackendApiResponse<AuthClientResponse> = {
      success: true,
      message: result.message,
      data: {
        message: result.message,
        user: {
          userId: tokens.userId,
          username: tokens.username,
          email: tokens.email,
          fullName: tokens.fullName,
          tenantId: tokens.tenantId,
        },
      },
      timestamp: result.timestamp,
    };
    const nextResponse = NextResponse.json(clientResult);
    setAuthCookies(nextResponse, tokens);
    return nextResponse;
  } catch {
    return authServiceUnavailableResponse();
  }
}
