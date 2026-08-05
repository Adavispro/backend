import { z } from "zod";

const timestamps = {
  createdAt: z.string().nullish(),
  updatedAt: z.string().nullish(),
};

export const tenantSchema = z.object({
  tenantId: z.string(),
  companyName: z.string(),
  companyCode: z.string(),
  domain: z.string().nullish(),
  isActive: z.boolean(),
  ...timestamps,
});

export const plantSchema = z.object({
  plantId: z.string(),
  tenantId: z.string(),
  plantName: z.string(),
  plantCode: z.string(),
  type: z.string().nullish(),
  timezone: z.string().nullish(),
  address: z.record(z.string(), z.unknown()).nullish(),
  isActive: z.boolean(),
  ...timestamps,
});

export const blockSchema = z.object({
  blockId: z.string(),
  tenantId: z.string(),
  plantId: z.string(),
  blockCode: z.string(),
  blockName: z.string(),
  displayOrder: z.number().nullish(),
  isActive: z.boolean(),
  ...timestamps,
});

export const areaSchema = z.object({
  areaId: z.string(),
  tenantId: z.string(),
  plantId: z.string(),
  blockId: z.string(),
  areaCode: z.string(),
  areaName: z.string(),
  displayOrder: z.number().nullish(),
  isActive: z.boolean(),
  ...timestamps,
});

export const roomSchema = z.object({
  roomId: z.string(),
  tenantId: z.string(),
  plantId: z.string(),
  areaId: z.string(),
  roomCode: z.string(),
  roomName: z.string(),
  classification: z.string().nullish(),
  isActive: z.boolean(),
  ...timestamps,
});

export const moduleSchema = z.object({
  moduleId: z.string(),
  moduleCode: z.string(),
  moduleName: z.string(),
  displayOrder: z.number().nullish(),
  isActive: z.boolean(),
});

export const screenSchema = z.object({
  screenId: z.string(),
  moduleId: z.string(),
  screenCode: z.string(),
  screenName: z.string(),
  displayOrder: z.number().nullish(),
  isActive: z.boolean(),
});

export const featureSchema = z.object({
  featureId: z.string(),
  screenId: z.string(),
  featureCode: z.string(),
  featureName: z.string(),
  displayOrder: z.number().nullish(),
  isActive: z.boolean(),
});

export const contextAssignmentSchema = z.object({
  assignmentId: z.string(),
  userId: z.string(),
  roleId: z.string().nullish(),
  groupId: z.string().nullish(),
  tenantId: z.string().nullish(),
  plantId: z.string().nullish(),
  isActive: z.boolean(),
  ...timestamps,
});

export type Tenant = z.infer<typeof tenantSchema>;
export type Plant = z.infer<typeof plantSchema>;
export type Block = z.infer<typeof blockSchema>;
export type Area = z.infer<typeof areaSchema>;
export type Room = z.infer<typeof roomSchema>;
export type MdmModule = z.infer<typeof moduleSchema>;
export type MdmScreen = z.infer<typeof screenSchema>;
export type MdmFeature = z.infer<typeof featureSchema>;
export type ContextAssignment = z.infer<typeof contextAssignmentSchema>;
