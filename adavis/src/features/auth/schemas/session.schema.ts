import { z } from "zod";

export const authTokensSchema = z.object({
  accessToken: z.string(),
  refreshToken: z.string(),
  tokenType: z.string(),
  expiresIn: z.number(),
  refreshExpiresIn: z.number(),
  userId: z.string(),
  username: z.string().nullish(),
  email: z.string().nullish(),
  fullName: z.string().nullish(),
  tenantId: z.string().nullish(),
});

export const refreshTokenResponseSchema = authTokensSchema.extend({
  refreshExpiresIn: z.number().nullish(),
});

export const authenticatedUserSchema = authTokensSchema.pick({
  userId: true,
  username: true,
  email: true,
  fullName: true,
  tenantId: true,
});

export const currentUserSchema = z.object({
  userId: z.string(),
  userTrackId: z.string().nullish(),
  tenantId: z.string().nullish(),
  username: z.string().nullish(),
  email: z.string(),
  status: z.string().nullish(),
  lifecycleStatus: z.string().nullish(),
  firstName: z.string().nullish(),
  lastName: z.string().nullish(),
  isActive: z.boolean().nullish(),
  isBlocked: z.boolean().nullish(),
});

const contextRecordSchema = z.record(z.string(), z.unknown());

export const loginContextSchema = z
  .object({
    user: currentUserSchema,
    tenantId: z.string().nullish(),
    groupAssignments: z.array(contextRecordSchema).default([]),
    groups: z.array(contextRecordSchema).default([]),
    roles: z.array(contextRecordSchema).default([]),
    rolePermissions: z.record(z.string(), z.array(contextRecordSchema)).default({}),
    assignedPlantIds: z.array(z.string()).default([]),
    assignedPlants: z.array(contextRecordSchema).default([]),
    plantSelectionRequired: z.boolean().default(false),
    selectedPlantId: z.string().nullish(),
    selectedPlant: contextRecordSchema.nullish(),
    permissionMatrix: z.unknown().optional(),
  })
  .passthrough();
