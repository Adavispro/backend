import { z } from "zod";
import { emailIdentifierSchema, passwordSchema } from "./login.schema";

export const passwordTokenSchema = z
  .string()
  .trim()
  .min(1, "Password reset token is required.");

export const setPasswordRequestSchema = z.object({
  token: passwordTokenSchema,
  newPassword: passwordSchema,
});

export const forgotPasswordRequestSchema = z.object({
  email: emailIdentifierSchema,
});

export const resetPasswordRequestSchema = z.object({
  email: emailIdentifierSchema,
  token: passwordTokenSchema,
  newPassword: passwordSchema,
});

export const userProvisionRequestSchema = z.object({
  userId: z.string().trim().min(1, "User ID is required."),
  username: z.string().trim().min(1, "Username is required."),
  email: emailIdentifierSchema.nullish(),
  initialPassword: passwordSchema,
});

export const adminResetPasswordRequestSchema = z.object({
  userId: z.string().trim().min(1, "User ID is required."),
  email: emailIdentifierSchema,
});

export const verifyPasswordPolicyRequestSchema = z.object({
  password: passwordSchema,
});

export const passwordPolicySchema = z.record(z.string(), z.unknown());

export const passwordPolicyVerificationSchema = z
  .object({
    valid: z.boolean().optional(),
    isValid: z.boolean().optional(),
    errors: z.array(z.string()).optional(),
    messages: z.array(z.string()).optional(),
  })
  .passthrough();

export const passwordResetResponseSchema = z.object({
  message: z.string().nullish(),
  resetToken: z.string().nullish(),
  expiresIn: z.number().nullish(),
});
