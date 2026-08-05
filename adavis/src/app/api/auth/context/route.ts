import { Buffer } from "node:buffer";
import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { API_ENDPOINTS } from "@/api/endpoints";
import { serverApiClient } from "@/api/server-client";
import { SERVER_API_CONFIG } from "@/api/server-config";
import type { BackendApiResponse } from "@/api/types";
import { AUTH_COOKIE_NAMES, authErrorResponse } from "@/features/auth/api/auth.server";
import type { LoginContext } from "@/features/auth/api/types";
import { loginContextSchema } from "@/features/auth/schemas";

const readTokenSubject = (token: string) => {
  try {
    const payload = token.split(".")[1];
    if (!payload) return undefined;
    const decoded = JSON.parse(
      Buffer.from(payload, "base64url").toString("utf8"),
    ) as { sub?: unknown };
    return typeof decoded.sub === "string" && decoded.sub.trim()
      ? decoded.sub
      : undefined;
  } catch {
    return undefined;
  }
};

export async function GET() {
  const accessToken = (await cookies()).get(
    AUTH_COOKIE_NAMES.accessToken,
  )?.value;
  if (!accessToken) {
    return authErrorResponse(401, { message: "Not authenticated." });
  }

  const userId = readTokenSubject(accessToken);
  if (!userId) {
    return authErrorResponse(401, { message: "Invalid access token." });
  }

  try {
    const { response, result } = await serverApiClient<LoginContext>(
      SERVER_API_CONFIG.gatewayUrl,
      `${API_ENDPOINTS.masterManagement.userLoginContext(encodeURIComponent(userId))}?includePermissionMatrix=true`,
      { headers: { Authorization: `Bearer ${accessToken}` } },
    );

    if (!response.ok || !result.success || !result.data) {
      return NextResponse.json(result, { status: response.status });
    }

    const context = loginContextSchema.safeParse(result.data);
    if (!context.success) {
      return authErrorResponse(502, {
        message: "The login context response is invalid.",
      });
    }

    return NextResponse.json({ ...result, data: context.data });
  } catch {
    return NextResponse.json(
      {
        success: false,
        message: "Unable to load the user login context.",
        errorCode: "LOGIN_CONTEXT_UNAVAILABLE",
        timestamp: new Date().toISOString(),
      } satisfies BackendApiResponse<never>,
      { status: 503 },
    );
  }
}
