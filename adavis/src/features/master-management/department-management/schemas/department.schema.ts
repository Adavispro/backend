import { z } from "zod";

export const departmentSchema = z.object({
  _id: z.string().optional(),
  id: z.string().optional(),
  departmentId: z.string(),
  tenantId: z.string().nullish(),
  plantId: z.string().nullish(),
  departmentCode: z.string().nullish(),
  departmentName: z.string().nullish(),
  path: z.string().nullish(),
  name: z.string().nullish().transform((name) => name ?? ""),
  description: z.string().nullish(),
  parentDepartmentId: z.string().nullish(),
  isActive: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string().nullish(),
});

export const departmentsSchema = z.array(departmentSchema);

export const departmentFormSchema = z.object({
  tenantId: z.string().trim().min(1, "Tenant is required."),
  plantId: z.string().trim().min(1, "Plant is required."),
  departmentCode: z.string().trim().min(1, "Department code is required."),
  name: z.string().trim().min(1, "Department name is required."),
  description: z.string().trim().optional(),
  parentDepartmentId: z.string().trim().optional(),
});

export const createDepartmentRequestSchema = departmentFormSchema.extend({
  departmentName: z.string().trim().min(1),
  parentDepartmentId: z.string().trim().nullish(),
});

export const updateDepartmentFormSchema = departmentFormSchema;

export const updateDepartmentRequestSchema = updateDepartmentFormSchema.extend({
  departmentName: z.string().trim().min(1),
  parentDepartmentId: z.string().trim().nullish(),
  isActive: z.boolean(),
});
