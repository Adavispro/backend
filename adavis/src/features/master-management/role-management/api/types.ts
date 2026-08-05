import type { z } from "zod";
import type {
  createRoleFormSchema,
  createRoleRequestSchema,
  moduleCatalogSchema,
  permissionMatrixSchema,
  rolePermissionRequestSchema,
  rolePermissionSchema,
  roleSchema,
  rolesSchema,
  updateRoleRequestSchema,
} from "../schemas";

export type Role = z.infer<typeof roleSchema>;
export type Roles = z.infer<typeof rolesSchema>;
export type CreateRoleFormValues = z.infer<typeof createRoleFormSchema>;
export type CreateRoleRequest = z.infer<typeof createRoleRequestSchema>;
export type UpdateRoleRequest = z.infer<typeof updateRoleRequestSchema>;
export type ModuleCatalog = z.infer<typeof moduleCatalogSchema>;
export type PermissionMatrix = z.infer<typeof permissionMatrixSchema>;
export type RolePermission = z.infer<typeof rolePermissionSchema>;
export type RolePermissionRequest = z.infer<typeof rolePermissionRequestSchema>;
