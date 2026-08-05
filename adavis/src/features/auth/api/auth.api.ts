import type { z } from "zod";
import { apiClient, APP_API_ENDPOINTS, parseApiData } from "@/api";
import type { BackendApiResponse } from "@/api/types";
import {
  authClientResponseSchema,
  loginContextSchema,
  loginInitiateResponseSchema,
} from "../schemas";
import type {
  AuthClientResponse,
  LoginContext,
  LoginInitiateRequest,
  LoginInitiateResponse,
  LoginRequest,
} from "./types";

const post = async <TResponse, TBody>(
  path: string,
  body: TBody,
  responseSchema: z.ZodType<TResponse>,
) => {
  const result = await apiClient<BackendApiResponse<TResponse>, TBody>(path, {
    method: "POST",
    body,
    retryOnUnauthorized: false,
  });

  return parseApiData(
    result,
    responseSchema,
    "Request failed.",
    "The authentication service returned an invalid response.",
  );
};

export const initiateLogin = (request: LoginInitiateRequest) =>
  post<LoginInitiateResponse, LoginInitiateRequest>(
    APP_API_ENDPOINTS.auth.initiateLogin,
    request,
    loginInitiateResponseSchema,
  );

export const login = (credentials: LoginRequest) =>
  post<AuthClientResponse, LoginRequest>(
    APP_API_ENDPOINTS.auth.login,
    credentials,
    authClientResponseSchema,
  );

export const logout = () =>
  apiClient<BackendApiResponse<null>>(APP_API_ENDPOINTS.auth.logout, {
    method: "POST",
  });

export const getLoginContext = async () => {
  const result = await apiClient<BackendApiResponse<LoginContext>>(
    APP_API_ENDPOINTS.auth.context,
  );

  return parseApiData(
    result,
    loginContextSchema,
    "Unable to load the login context.",
    "The authentication service returned an invalid response.",
  );
};

export const getCurrentUser = async () => (await getLoginContext()).user;
