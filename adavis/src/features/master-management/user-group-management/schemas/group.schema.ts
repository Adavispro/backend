import { z } from "zod";

export const groupSchema = z.object({
  _id: z.string().optional(),
  id: z.string().optional(),
  groupId: z.string(),
  tenantId: z.string().nullish(),
  groupCode: z.string().nullish(),
  groupName: z.string().nullish(),
  name: z.string().nullish().transform((name) => name ?? ""),
  description: z.string().nullish(),
  isActive: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string().nullish(),
});

export const groupsSchema = z.array(groupSchema);

export const createGroupFormSchema = z.object({
  tenantId: z.string().trim().min(1, "Tenant is required."),
  groupCode: z.string().trim().min(1, "Group code is required."),
  name: z.string().trim().min(1, "Group name is required."),
  description: z.string().trim().optional(),
  assignedUsers: z.array(z.string().trim()).default([]),
  assignedRoles: z.array(z.string().trim()).default([]),
});

export const createGroupRequestSchema = createGroupFormSchema.omit({
  assignedUsers: true,
  assignedRoles: true,
}).extend({
  isActive: z.boolean(),
});

export const updateGroupFormSchema = createGroupFormSchema;

export const updateGroupRequestSchema = updateGroupFormSchema.omit({
  assignedUsers: true,
  assignedRoles: true,
}).extend({
  groupName: z.string().trim().min(1),
  isActive: z.boolean(),
});
