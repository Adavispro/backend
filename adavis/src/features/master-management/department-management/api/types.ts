import type { z } from "zod";
import type {
  createDepartmentRequestSchema,
  departmentFormSchema,
  departmentSchema,
  departmentsSchema,
  updateDepartmentRequestSchema,
} from "../schemas";

export type Department = z.infer<typeof departmentSchema>;
export type Departments = z.infer<typeof departmentsSchema>;
export type DepartmentFormValues = z.infer<typeof departmentFormSchema>;
export type CreateDepartmentRequest = z.infer<
  typeof createDepartmentRequestSchema
>;
export type UpdateDepartmentRequest = z.infer<
  typeof updateDepartmentRequestSchema
>;
