import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import {
  AUTH_COOKIE_NAMES,
  authServiceEndpoints,
  clearAuthCookies,
  requestAuthService,
} from "@/features/auth/api/auth.server";
import type { BackendApiResponse } from "@/api/types";

export async function POST() {
  const cookieStore = await cookies();
  const accessToken = cookieStore.get(AUTH_COOKIE_NAMES.accessToken)?.value;

  if (accessToken) {
    try {
      await requestAuthService(authServiceEndpoints.logout, {
        method: "POST",
        accessToken,
      });
    } catch {
      // Local session cleanup must still succeed if the auth service is down.
    }
  }

  const result: BackendApiResponse<null> = {
    success: true,
    message: "Logged out successfully.",
    data: null,
    timestamp: new Date().toISOString(),
  };
  const response = NextResponse.json(result);
  clearAuthCookies(response);
  return response;
}
