import { z } from "zod";

export const updateUserFormSchema = z.object({
  tenantId: z.string().trim().min(1, "Tenant is required."),
  email: z.string().trim().email("Enter a valid email address."),
  firstName: z.string().trim().min(1, "First name is required."),
  lastName: z.string().trim().min(1, "Last name is required."),
  phoneNumber: z.string().nullish(),
  title: z.string().trim().min(1, "Title is required."),
  departmentId: z.string().trim().min(1, "Department is required."),
  isActive: z.boolean(),
});

export const updateUserRequestSchema = updateUserFormSchema.extend({
  userId: z.string().trim().min(1, "User ID is required."),
  username: z.string().trim().min(1, "Username is required.").optional(),
  userTrackId: z.string().nullish().optional(),
  userType: z.string().trim().min(1, "User type is required."),
  lifecycleStatus: z.string().trim().min(1, "Lifecycle status is required."),
  empId: z.string().trim().min(1, "Employee ID is required."),
  designation: z.string().nullish().optional(),
  isExternal: z.boolean().optional(),
  isBlocked: z.boolean().optional(),
  isDeleted: z.boolean().optional(),
  supportingDocumentIds: z.array(z.string()).optional(),
  supportingDocuments: z.array(z.record(z.string(), z.unknown())).optional(),
  supportingDocumentType: z.string().nullish().optional(),
  reason: z.string().trim().min(1).optional(),
});
