import type { z } from "zod";
import type {
  authenticatedUserSchema,
  authClientResponseSchema,
  authTokensSchema,
  currentUserSchema,
  loginContextSchema,
  loginInitiateRequestSchema,
  loginInitiateResponseSchema,
  loginRequestSchema,
  adminResetPasswordRequestSchema,
  forgotPasswordRequestSchema,
  passwordPolicySchema,
  passwordPolicyVerificationSchema,
  passwordResetResponseSchema,
  resetPasswordRequestSchema,
  setPasswordRequestSchema,
  userProvisionRequestSchema,
  userAccountActionSchema,
  verifyPasswordPolicyRequestSchema,
  refreshTokenResponseSchema,
} from "../schemas";

export type LoginRequest = z.infer<typeof loginRequestSchema>;
export type LoginInitiateRequest = z.infer<
  typeof loginInitiateRequestSchema
>;
export type LoginInitiateResponse = z.infer<
  typeof loginInitiateResponseSchema
>;
export type AuthTokens = z.infer<typeof authTokensSchema>;
export type AuthenticatedUser = z.infer<typeof authenticatedUserSchema>;
export type CurrentUser = z.infer<typeof currentUserSchema>;
export type LoginContext = z.infer<typeof loginContextSchema>;
export type AuthClientResponse = z.infer<typeof authClientResponseSchema>;
export type RefreshTokenResponse = z.infer<typeof refreshTokenResponseSchema>;
export type SetPasswordRequest = z.infer<typeof setPasswordRequestSchema>;
export type ForgotPasswordRequest = z.infer<typeof forgotPasswordRequestSchema>;
export type ResetPasswordRequest = z.infer<typeof resetPasswordRequestSchema>;
export type AdminResetPasswordRequest = z.infer<
  typeof adminResetPasswordRequestSchema
>;
export type VerifyPasswordPolicyRequest = z.infer<
  typeof verifyPasswordPolicyRequestSchema
>;
export type PasswordPolicy = z.infer<typeof passwordPolicySchema>;
export type PasswordPolicyVerification = z.infer<
  typeof passwordPolicyVerificationSchema
>;
export type PasswordResetResponse = z.infer<typeof passwordResetResponseSchema>;
export type UserProvisionRequest = z.infer<typeof userProvisionRequestSchema>;
export type UserAccountAction = z.infer<typeof userAccountActionSchema>;
