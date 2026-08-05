import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import {
  AUTH_COOKIE_NAMES,
  authErrorResponse,
  authServiceEndpoints,
  authServiceUnavailableResponse,
  clearAuthCookies,
  requestAuthService,
  setAccessTokenCookie,
} from "@/features/auth/api/auth.server";
import type { RefreshTokenResponse } from "@/features/auth/api/types";
import { refreshTokenResponseSchema } from "@/features/auth/schemas";

export async function POST() {
  const cookieStore = await cookies();
  const refreshToken = cookieStore.get(AUTH_COOKIE_NAMES.refreshToken)?.value;

  if (!refreshToken) {
    return authErrorResponse(401, { message: "No refresh token is available." });
  }

  try {
    const { response, result } = await requestAuthService<
      RefreshTokenResponse,
      { refreshToken: string }
    >(authServiceEndpoints.refresh, {
      method: "POST",
      body: { refreshToken },
    });

    if (!response.ok || !result.success || !result.data) {
      const errorResponse = authErrorResponse(response.status, result);
      clearAuthCookies(errorResponse);
      return errorResponse;
    }

    const parsedTokens = refreshTokenResponseSchema.safeParse(result.data);
    if (!parsedTokens.success) {
      const errorResponse = authErrorResponse(502, {
        message: "The authentication service returned an invalid response.",
      });
      clearAuthCookies(errorResponse);
      return errorResponse;
    }

    const nextResponse = NextResponse.json({
      success: true,
      message: result.message,
      data: null,
      timestamp: result.timestamp,
    });
    setAccessTokenCookie(nextResponse, parsedTokens.data);
    return nextResponse;
  } catch {
    return authServiceUnavailableResponse();
  }
}
