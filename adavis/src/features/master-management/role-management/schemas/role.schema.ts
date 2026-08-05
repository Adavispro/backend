import { z } from "zod";

export const roleSchema = z.object({
  _id: z.string().optional(),
  id: z.string().optional(),
  roleId: z.string(),
  tenantId: z.string().nullish(),
  roleCode: z.string().nullish(),
  roleName: z.string().nullish(),
  name: z.string().nullish().transform((name) => name ?? ""),
  description: z.string().nullish(),
  parentRoleId: z.string().nullish(),
  level: z.number().int().nonnegative().nullish().transform((level) => level ?? 0),
  isActive: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string().nullish(),
});

export const rolesSchema = z.array(roleSchema);

export const createRoleFormSchema = z.object({
  tenantId: z.string().trim().min(1, "Tenant is required."),
  roleCode: z.string().trim().min(1, "Role code is required."),
  name: z.string().trim().min(1, "Role name is required."),
  description: z.string().trim().optional(),
});

export const createRoleRequestSchema = createRoleFormSchema.extend({
  isActive: z.boolean(),
});

export const updateRoleRequestSchema = z.object({
  tenantId: z.string().trim().min(1, "Tenant is required."),
  roleCode: z.string().trim().min(1, "Role code is required."),
  name: z.string().trim().min(1, "Role name is required."),
  roleName: z.string().trim().min(1, "Role name is required."),
  description: z.string().trim().optional(),
  parentRoleId: z.string().nullish().optional(),
  level: z.number().int().nonnegative(),
  isActive: z.boolean(),
});

export const featureCatalogSchema = z.object({
  featureId: z.string(),
  moduleId: z.string(),
  moduleCode: z.string(),
  screenId: z.string(),
  screenCode: z.string(),
  featureCode: z.string(),
  featureName: z.string(),
  displayOrder: z.number(),
  isActive: z.boolean(),
});

export const screenCatalogSchema = z.object({
  screenId: z.string(),
  moduleId: z.string(),
  moduleCode: z.string(),
  screenCode: z.string(),
  screenName: z.string(),
  displayOrder: z.number(),
  isActive: z.boolean(),
  features: z.array(featureCatalogSchema).default([]),
});

export const moduleCatalogSchema = z.object({
  moduleId: z.string(),
  moduleCode: z.string(),
  moduleName: z.string(),
  displayOrder: z.number(),
  isActive: z.boolean(),
  screens: z.array(screenCatalogSchema).default([]),
});

export const permissionMatrixSchema = z.object({
  modules: z.array(moduleCatalogSchema),
});

export const featurePermissionSchema = z.object({
  featureId: z.string(),
  actions: z.array(z.string()),
});

export const screenPermissionSchema = z.object({
  screenId: z.string(),
  actions: z.array(z.string()),
  featurePermissions: z.array(featurePermissionSchema),
});

export const rolePermissionRequestSchema = z.object({
  moduleId: z.string(),
  version: z.number().int().min(1),
  isActive: z.boolean(),
  screenPermissions: z.array(screenPermissionSchema),
});

export const rolePermissionSchema = rolePermissionRequestSchema.extend({
  id: z.string().optional(),
  roleId: z.string(),
  effectiveFrom: z.string().nullish(),
  effectiveTo: z.string().nullish(),
});

export const rolePermissionsSchema = z.array(rolePermissionSchema);
