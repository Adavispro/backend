import "server-only";
import type { NextResponse } from "next/server";
import { NextResponse as NextServerResponse } from "next/server";
import { API_ENDPOINTS } from "@/api/endpoints";
import { serverApiClient } from "@/api/server-client";
import { SERVER_API_CONFIG } from "@/api/server-config";
import type { ApiMethod, BackendApiResponse } from "@/api/types";
import type { AuthTokens } from "./types";

export const AUTH_COOKIE_NAMES = {
  accessToken: "adavis_access_token",
  refreshToken: "adavis_refresh_token",
} as const;

const resolveSecureCookieFlag = () => {
  const override = process.env.AUTH_COOKIE_SECURE?.trim().toLowerCase();
  if (override === "true") return true;
  if (override === "false") return false;
  return process.env.NODE_ENV === "production";
};

export const requestAuthService = <TData, TBody = unknown>(
  path: string,
  options: {
    method?: ApiMethod;
    body?: TBody;
    accessToken?: string;
  } = {},
) => {
  const { accessToken, ...requestOptions } = options;

  return serverApiClient<TData, TBody>(
    SERVER_API_CONFIG.authServiceUrl,
    path,
    {
      ...requestOptions,
      headers: accessToken
        ? { Authorization: `Bearer ${accessToken}` }
        : undefined,
    },
  );
};

export const authServiceEndpoints = API_ENDPOINTS.auth;

const cookieOptions = (maxAge: number) => ({
  httpOnly: true,
  secure: resolveSecureCookieFlag(),
  sameSite: "lax" as const,
  path: "/",
  maxAge,
});

export const setAuthCookies = (
  response: NextResponse,
  tokens: Pick<
    AuthTokens,
    "accessToken" | "expiresIn" | "refreshExpiresIn" | "refreshToken"
  >,
) => {
  setAccessTokenCookie(response, tokens);
  response.cookies.set(
    AUTH_COOKIE_NAMES.refreshToken,
    tokens.refreshToken,
    cookieOptions(Math.floor(tokens.refreshExpiresIn / 1000)),
  );
};

export const setAccessTokenCookie = (
  response: NextResponse,
  tokens: Pick<AuthTokens, "accessToken" | "expiresIn">,
) => {
  response.cookies.set(
    AUTH_COOKIE_NAMES.accessToken,
    tokens.accessToken,
    cookieOptions(Math.floor(tokens.expiresIn / 1000)),
  );
};

export const clearAuthCookies = (response: NextResponse) => {
  response.cookies.set(
    AUTH_COOKIE_NAMES.accessToken,
    "",
    cookieOptions(0),
  );
  response.cookies.set(
    AUTH_COOKIE_NAMES.refreshToken,
    "",
    cookieOptions(0),
  );
};

export const authErrorResponse = (
  status: number,
  backendResponse?: Partial<BackendApiResponse<unknown>>,
) => {
  const rawMessage = backendResponse?.message?.trim();
  const exposesEndpoint = Boolean(
    rawMessage && (rawMessage.includes("/api/") || /https?:\/\//i.test(rawMessage)),
  );
  const userWasNotFound = rawMessage?.toLowerCase().includes("user not found");
  const message =
    userWasNotFound
      ? "No account was found for this user ID."
      : status === 401
      ? "Incorrect user ID or password."
      : status === 403
        ? "This account cannot sign in. Contact your administrator."
        : status === 404
          ? "No account was found for this user ID."
          : status >= 500 || exposesEndpoint
            ? "The authentication service is temporarily unavailable."
            : rawMessage || "Authentication request failed.";

  return NextServerResponse.json(
    {
      success: false,
      message,
      errorCode: backendResponse?.errorCode ?? "AUTH_REQUEST_FAILED",
      timestamp: backendResponse?.timestamp ?? new Date().toISOString(),
    } satisfies BackendApiResponse<never>,
    { status },
  );
};

export const authServiceUnavailableResponse = () =>
  NextServerResponse.json(
    {
      success: false,
      message: "The authentication service is unavailable.",
      errorCode: "AUTH_SERVICE_UNAVAILABLE",
      timestamp: new Date().toISOString(),
    } satisfies BackendApiResponse<never>,
    { status: 503 },
  );
