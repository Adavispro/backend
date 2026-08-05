import {
  apiClient,
  APP_API_ENDPOINTS,
  ensureApiSuccess,
  parseApiData,
  withQuery,
} from "@/api";
import type { BackendApiResponse } from "@/api/types";
import { permissionMatrixSchema, rolePermissionSchema, rolePermissionsSchema, roleSchema, rolesSchema } from "../schemas";
import type { CreateRoleRequest, PermissionMatrix, Role, RolePermission, RolePermissionRequest, Roles, UpdateRoleRequest } from "./types";

export const createRole = async (request: CreateRoleRequest) => {
  const result = await apiClient<BackendApiResponse<Role>, CreateRoleRequest>(
    APP_API_ENDPOINTS.masterManagement.roles,
    { method: "POST", body: request },
  );
  return parseApiData(
    result,
    roleSchema,
    "Unable to create role.",
    "The role service returned an invalid response.",
  );
};

export const updateRole = async (roleId: string, request: UpdateRoleRequest) => {
  const result = await apiClient<BackendApiResponse<Role>, UpdateRoleRequest>(
    APP_API_ENDPOINTS.masterManagement.roleDetail(roleId),
    { method: "PUT", body: request },
  );
  return parseApiData(
    result,
    roleSchema,
    "Unable to update role.",
    "The role service returned an invalid response.",
  );
};

export const getRoles = async (isActive?: boolean, signal?: AbortSignal) => {
  const result = await apiClient<BackendApiResponse<Roles>>(
    withQuery(APP_API_ENDPOINTS.masterManagement.roles, { isActive }),
    { signal },
  );
  return parseApiData(
    result,
    rolesSchema,
    "Unable to load roles.",
    "The role service returned an invalid response.",
  );
};

export const getAllRoles = async (signal?: AbortSignal) => {
  const [active, inactive] = await Promise.all([
    getRoles(true, signal),
    getRoles(false, signal),
  ]);
  return [...active, ...inactive];
};

export const getPermissionMatrix = async (signal?: AbortSignal): Promise<PermissionMatrix> => {
  const result = await apiClient<BackendApiResponse<PermissionMatrix>>(
    "/api/master-management/mdm/permissions/matrix-tree?isActive=true",
    { signal },
  );
  return parseApiData(
    result,
    permissionMatrixSchema,
    "Unable to load permission catalog.",
    "The role service returned an invalid response.",
  );
};

export const getRolePermissions = async (roleId: string, signal?: AbortSignal): Promise<RolePermission[]> => {
  const result = await apiClient<BackendApiResponse<RolePermission[]>>(
    `/api/master-management/mdm/roles/${encodeURIComponent(roleId)}/permissions?isActive=true`,
    { signal },
  );
  return parseApiData(
    result,
    rolePermissionsSchema,
    "Unable to load role permissions.",
    "The role service returned an invalid response.",
  );
};

export const saveRolePermission = async (roleId: string, request: RolePermissionRequest) => {
  const result = await apiClient<BackendApiResponse<RolePermission>, RolePermissionRequest>(
    `/api/master-management/mdm/roles/${encodeURIComponent(roleId)}/permissions`,
    { method: "POST", body: request },
  );
  return parseApiData(
    result,
    rolePermissionSchema,
    "Unable to save role permissions.",
    "The role service returned an invalid response.",
  );
};

export const setRoleActive = async (role: Role, active: boolean) => {
  const result = await apiClient<BackendApiResponse<Role | null>>(
    `/api/master-management/mdm/roles/${encodeURIComponent(role.roleId)}/${active ? "activate" : "deactivate"}`,
    { method: "POST" },
  );
  ensureApiSuccess(
    result,
    `Unable to ${active ? "activate" : "deactivate"} role.`,
  );
  if (!active) return { ...role, isActive: false };
  return roleSchema.parse(result.data);
};
