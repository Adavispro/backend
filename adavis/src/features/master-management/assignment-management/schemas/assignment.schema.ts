import { z } from "zod";

export const assignmentTypeSchema = z.enum(["GROUP_SCOPE", "USER_OVERRIDE"]);
export const assignmentScopeTypeSchema = z.enum(["PLANT", "RESOURCE"]);

export const assignmentSchema = z.object({
  _id: z.string().optional(),
  assignmentId: z.string(),
  tenantId: z.string(),
  assignmentType: assignmentTypeSchema.default("GROUP_SCOPE"),
  userId: z.string().nullish(),
  groupId: z.string().nullish(),
  scopeType: assignmentScopeTypeSchema.default("PLANT"),
  resourceType: z.string().nullish(),
  resourceId: z.string().nullish(),
  plantId: z.string().nullish(),
  departmentId: z.string().nullish(),
  assignedBy: z.string().nullish(),
  reason: z.string().nullish(),
  isActive: z.boolean().default(true),
  createdAt: z.string(),
  updatedAt: z.string().nullish(),
}).passthrough();

export const assignmentsSchema = z.array(assignmentSchema);

const optionalString = z.preprocess(
  (value) => {
    if (typeof value !== "string") return value;
    const trimmed = value.trim();
    return trimmed.length ? trimmed : undefined;
  },
  z.string().trim().min(1).optional(),
);

export const createAssignmentSchema = z.object({
  assignmentType: assignmentTypeSchema,
  tenantId: z.string().trim().min(1, "Tenant is required."),
  userId: optionalString,
  groupId: optionalString,
  scopeType: assignmentScopeTypeSchema,
  plantId: optionalString,
  resourceId: optionalString,
  assignedBy: optionalString,
  reason: optionalString,
}).superRefine((value, context) => {
  if (!value.groupId) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      message: "User group is required.",
      path: ["groupId"],
    });
  }

  if (!value.userId) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      message: "User is required.",
      path: ["userId"],
    });
  }

  if (value.scopeType === "PLANT" && !value.plantId) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      message: "Plant is required.",
      path: ["plantId"],
    });
  }

  if (value.scopeType === "PLANT" && value.resourceId) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      message: "Resource ID must be empty for PLANT scope.",
      path: ["resourceId"],
    });
  }

  if (value.scopeType === "RESOURCE" && !value.resourceId) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      message: "Resource is required.",
      path: ["resourceId"],
    });
  }

  if (value.scopeType === "RESOURCE" && value.plantId) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      message: "Plant must be empty for RESOURCE scope.",
      path: ["plantId"],
    });
  }
});
