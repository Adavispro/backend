import { apiClient, ApiError, APP_API_ENDPOINTS, withQuery } from "@/api";
import type { BackendApiResponse } from "@/api/types";
import { groupSchema, groupsSchema } from "../schemas";
import type {
  CreateGroupRequest,
  Group,
  Groups,
  UpdateGroupRequest,
} from "./types";

export const getGroups = async (isActive?: boolean, signal?: AbortSignal) => {
  const result = await apiClient<BackendApiResponse<Groups>>(
    withQuery(APP_API_ENDPOINTS.masterManagement.groups, { isActive }),
    { signal },
  );

  if (!result.success || !result.data) {
    throw new ApiError({
      status: 400,
      message: result.message || "Unable to load user groups.",
      details: result,
    });
  }

  const parsedGroups = groupsSchema.safeParse(result.data);
  if (!parsedGroups.success) {
    throw new ApiError({
      status: 502,
      message: "The group service returned an invalid response.",
      details: parsedGroups.error.flatten(),
    });
  }

  return parsedGroups.data;
};

export const getAllGroups = async (signal?: AbortSignal) => {
  const [active, inactive] = await Promise.all([
    getGroups(true, signal),
    getGroups(false, signal),
  ]);
  return [...active, ...inactive];
};

export const createGroup = async (request: CreateGroupRequest) => {
  const result = await apiClient<BackendApiResponse<Group>, CreateGroupRequest>(
    APP_API_ENDPOINTS.masterManagement.groups,
    { method: "POST", body: request },
  );

  if (!result.success || !result.data) {
    throw new ApiError({
      status: 400,
      message: result.message || "Unable to create user group.",
      details: result,
    });
  }

  const parsedGroup = groupSchema.safeParse(result.data);
  if (!parsedGroup.success) {
    throw new ApiError({
      status: 502,
      message: "The group service returned an invalid response.",
      details: parsedGroup.error.flatten(),
    });
  }

  return parsedGroup.data;
};

export const updateGroup = async (
  groupId: string,
  request: UpdateGroupRequest,
) => {
  const result = await apiClient<BackendApiResponse<Group>, UpdateGroupRequest>(
    APP_API_ENDPOINTS.masterManagement.groupDetail(groupId),
    { method: "PUT", body: request },
  );

  if (!result.success || !result.data) {
    throw new ApiError({
      status: 400,
      message: result.message || "Unable to update user group.",
      details: result,
    });
  }

  const parsedGroup = groupSchema.safeParse(result.data);
  if (!parsedGroup.success) {
    throw new ApiError({
      status: 502,
      message: "The group service returned an invalid response.",
      details: parsedGroup.error.flatten(),
    });
  }

  return parsedGroup.data;
};

export const setGroupActive = async (group: Group, active: boolean) => {
  const result = await apiClient<BackendApiResponse<Group | null>>(
    `/api/master-management/mdm/user-groups/${encodeURIComponent(group.groupId)}/${active ? "activate" : "deactivate"}`,
    { method: "POST" },
  );

  if (!result.success) {
    throw new ApiError({
      status: 400,
      message: result.message || `Unable to ${active ? "activate" : "deactivate"} user group.`,
      details: result,
    });
  }

  if (!active) return { ...group, isActive: false };
  return groupSchema.parse(result.data);
};

const mapGroupMember = async (
  groupId: string,
  resource: "roles" | "users",
  memberId: string,
  assignedBy: string,
) => {
  const key = resource === "roles" ? "roleId" : "userId";
  const result = await apiClient<BackendApiResponse<unknown>>(
    `/api/master-management/mdm/user-groups/${encodeURIComponent(groupId)}/${resource}`,
    { method: "POST", body: { [key]: memberId, assignedBy } },
  );
  if (!result.success) {
    throw new ApiError({ status: 400, message: result.message || `Unable to assign ${resource.slice(0, -1)}.`, details: result });
  }
  return result;
};

export const mapRoleToGroup = (groupId: string, roleId: string, assignedBy: string) =>
  mapGroupMember(groupId, "roles", roleId, assignedBy);

export const mapUserToGroup = (groupId: string, userId: string, assignedBy: string) =>
  mapGroupMember(groupId, "users", userId, assignedBy);

export const getGroupAssignments = async (groupId: string, signal?: AbortSignal) => {
  const base = `/api/master-management/mdm/user-groups/${encodeURIComponent(groupId)}`;
  const [rolesResult, usersResult] = await Promise.all([
    apiClient<BackendApiResponse<Array<Record<string, unknown>>>>(`${base}/roles?isActive=true`, { signal }),
    apiClient<BackendApiResponse<Array<Record<string, unknown>>>>(`${base}/users?isActive=true`, { signal }),
  ]);
  return {
    roleIds: (rolesResult.data ?? []).map((item) => String(item.roleId ?? "")).filter(Boolean),
    userIds: (usersResult.data ?? []).map((item) => String(item.userId ?? "")).filter(Boolean),
  };
};
