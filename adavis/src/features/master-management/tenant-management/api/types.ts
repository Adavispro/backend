import type { z } from "zod";
import type {
  createTenantRequestSchema,
  tenantFormSchema,
  tenantSchema,
  updateTenantRequestSchema,
} from "../schemas";

export type Tenant = z.infer<typeof tenantSchema>;
export type TenantFormValues = z.infer<typeof tenantFormSchema>;
export type CreateTenantRequest = z.infer<typeof createTenantRequestSchema>;
export type UpdateTenantRequest = z.infer<typeof updateTenantRequestSchema>;
