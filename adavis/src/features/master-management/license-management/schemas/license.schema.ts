import { z } from "zod";

export const licenseStatusSchema = z.enum([
  "ACTIVE",
  "EXPIRED",
  "SUSPENDED",
  "INACTIVE",
  "UPGRADED",
]);

export const licenseSchema = z.object({
  id: z.string(),
  licenseKey: z.string().nullish(),
  planId: z.string().nullish(),
  planName: z.string().nullish(),
  planType: z.string().nullish(),
  modules: z.array(z.string()).default([]),
  maxUsers: z.number().int().nonnegative().nullish(),
  currentUsers: z.number().int().nonnegative().nullish(),
  status: licenseStatusSchema.or(z.string()),
  startDate: z.string().nullish(),
  expiryDate: z.string().nullish(),
  metadata: z.record(z.string(), z.unknown()).nullish(),
  createdAt: z.string().nullish(),
  updatedAt: z.string().nullish(),
});

export const licenseHistorySchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  licenseId: z.string(),
  action: z.string(),
  beforeStatus: z.string().nullish(),
  afterStatus: z.string().nullish(),
  beforeMaxUsers: z.number().int().nonnegative().nullish(),
  afterMaxUsers: z.number().int().nonnegative().nullish(),
  beforeModules: z.array(z.string()).nullish(),
  afterModules: z.array(z.string()).nullish(),
  beforeExpiry: z.string().nullish(),
  afterExpiry: z.string().nullish(),
  reason: z.string().nullish(),
  performedBy: z.string().nullish(),
  performedAt: z.string(),
});

export const licenseHistoryListSchema = z.array(licenseHistorySchema);

export const licenseActionRequestSchema = z.object({
  actionType: z.enum(["ACTIVATE", "UPGRADE", "RENEW", "SUSPEND", "REACTIVATE"]),
  encryptedLicenseToken: z.string().trim().optional(),
  performedBy: z.string().trim().optional(),
  reason: z.string().trim().optional(),
}).superRefine((request, context) => {
  if (!request.encryptedLicenseToken) {
    context.addIssue({
      code: "custom",
      path: ["encryptedLicenseToken"],
      message: "Encrypted license token is required.",
    });
  }
});

export const licenseUpgradeRequestSchema = z.object({
  encryptedLicenseToken: z.string().trim().min(1, "License token is required."),
  reason: z.string().trim().optional(),
  upgradedBy: z.string().trim().optional(),
});
