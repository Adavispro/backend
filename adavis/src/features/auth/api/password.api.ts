import type { z } from "zod";
import {
  apiClient,
  APP_API_ENDPOINTS,
  ensureApiSuccess,
  parseApiData,
} from "@/api";
import type { BackendApiResponse } from "@/api/types";
import {
  passwordPolicySchema,
  passwordPolicyVerificationSchema,
  passwordResetResponseSchema,
} from "../schemas";
import type {
  AdminResetPasswordRequest,
  ForgotPasswordRequest,
  PasswordPolicy,
  PasswordResetResponse,
  PasswordPolicyVerification,
  ResetPasswordRequest,
  SetPasswordRequest,
  VerifyPasswordPolicyRequest,
} from "./types";

const postPasswordRequest = async <TBody>(path: string, body: TBody) => {
  const result = await apiClient<BackendApiResponse<null>, TBody>(path, {
    method: "POST",
    body,
  });

  ensureApiSuccess(result, "Password request failed.");
  return result.data ?? null;
};

const postPasswordDataRequest = async <TResponse, TBody>(
  path: string,
  body: TBody,
  responseSchema: z.ZodType<TResponse>,
) => {
  const result = await apiClient<BackendApiResponse<TResponse>, TBody>(path, {
    method: "POST",
    body,
  });

  return parseApiData(
    result,
    responseSchema,
    "Password request failed.",
    "The authentication service returned an invalid response.",
  );
};

export const setPassword = (request: SetPasswordRequest) =>
  postPasswordRequest(APP_API_ENDPOINTS.auth.setPassword, request);

export const forgotPassword = (request: ForgotPasswordRequest) =>
  postPasswordDataRequest<PasswordResetResponse, ForgotPasswordRequest>(
    APP_API_ENDPOINTS.auth.forgotPassword,
    request,
    passwordResetResponseSchema,
  );

export const resetPassword = (request: ResetPasswordRequest) =>
  postPasswordRequest(APP_API_ENDPOINTS.auth.resetPassword, request);

export const adminResetPassword = (request: AdminResetPasswordRequest) =>
  postPasswordDataRequest<PasswordResetResponse, AdminResetPasswordRequest>(
    APP_API_ENDPOINTS.auth.adminResetPassword,
    request,
    passwordResetResponseSchema,
  );

export const getPasswordPolicy = async () => {
  const result = await apiClient<BackendApiResponse<PasswordPolicy>>(
    APP_API_ENDPOINTS.auth.passwordPolicy,
  );

  return parseApiData(
    result,
    passwordPolicySchema,
    "Unable to load the password policy.",
    "The authentication service returned an invalid response.",
  );
};

export const verifyPasswordPolicy = async (
  request: VerifyPasswordPolicyRequest,
) => {
  const result = await apiClient<
    BackendApiResponse<PasswordPolicyVerification>,
    VerifyPasswordPolicyRequest
  >(APP_API_ENDPOINTS.auth.verifyPasswordPolicy, {
    method: "POST",
    body: request,
  });

  return parseApiData(
    result,
    passwordPolicyVerificationSchema,
    "Unable to verify the password policy.",
    "The authentication service returned an invalid response.",
  );
};
