import { z } from "zod";
import { authenticatedUserSchema } from "./session.schema";

export const emailIdentifierSchema = z
  .string()
  .trim()
  .min(1, "Email ID is required.")
  .email("Enter a valid email ID.");

export const loginIdentifierSchema = z
  .string()
  .trim()
  .min(1, "User ID is required.")
  .max(100, "User ID is too long.");

export const passwordSchema = z
  .string()
  .min(1, "Password is required.")
  .max(256, "Password is too long.");

export const loginInitiateRequestSchema = z.object({
  identifier: loginIdentifierSchema,
});

export const loginRequestSchema = loginInitiateRequestSchema.extend({
  password: passwordSchema,
});

export const loginInitiateResponseSchema = z.object({
  userId: z.string(),
  email: z.string(),
  status: z.string(),
  passwordSet: z.boolean(),
  fullName: z.string().nullish(),
});

export const authClientResponseSchema = z.object({
  message: z.string(),
  user: authenticatedUserSchema,
});
