import { z } from "zod";
import { tenantSchema } from "../../shared/schemas";

export { tenantSchema };
export const tenantsSchema = z.array(tenantSchema);

export const tenantFormSchema = z.object({
  companyCode: z.string().trim().min(1, "Company code is required."),
  companyName: z.string().trim().min(1, "Company name is required."),
  domain: z.string().trim(),
});

export const createTenantRequestSchema = tenantFormSchema.extend({
  isActive: z.boolean().default(true),
});

export const updateTenantRequestSchema = createTenantRequestSchema;
