import type { z } from "zod";
import type {
  licenseActionRequestSchema,
  licenseHistorySchema,
  licenseSchema,
  licenseUpgradeRequestSchema,
} from "../schemas";

export type TenantLicense = z.infer<typeof licenseSchema>;
export type LicenseHistory = z.infer<typeof licenseHistorySchema>;
export type LicenseActionRequest = z.infer<typeof licenseActionRequestSchema>;
export type LicenseUpgradeRequest = z.infer<typeof licenseUpgradeRequestSchema>;
