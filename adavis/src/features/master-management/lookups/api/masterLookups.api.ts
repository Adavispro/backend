import { getDepartments } from "../../department-management/api";
import { getRoles } from "../../role-management/api";
import { getGroups } from "../../user-group-management/api";
import { getUsers } from "../../user-management/api";
import type { Department } from "../../department-management/api/types";
import type { Role } from "../../role-management/api/types";
import type { Group } from "../../user-group-management/api/types";
import type { User } from "../../user-management/api/types";
import type { MasterLookupOption, MasterLookupOptions } from "./types";

const sortByLabel = (options: MasterLookupOption[]) =>
  [...options].sort((first, second) => first.label.localeCompare(second.label));

const userDisplayName = (user: User) =>
  [user.firstName, user.lastName].filter(Boolean).join(" ") ||
  user.username ||
  user.email ||
  user.userId;

const roleDisplayName = (role: Role) =>
  role.roleName || role.name || role.roleCode || role.roleId;

export const mapUsersToLookupOptions = (
  users: User[],
): MasterLookupOption[] =>
  sortByLabel(
    users
      .filter((user) => user.isActive && !user.isBlocked)
      .map((user) => ({
        label: userDisplayName(user),
        value: user.userId,
      })),
  );

export const mapRolesToLookupOptions = (
  roles: Role[],
): MasterLookupOption[] =>
  sortByLabel(
    roles
      .filter((role) => role.isActive && role.roleId)
      .map((role) => ({
        label: roleDisplayName(role),
        value: role.roleId,
      }))
      .filter((option) => option.label && option.value),
  );

export const mapGroupsToLookupOptions = (
  groups: Group[],
): MasterLookupOption[] =>
  sortByLabel(
    groups
      .filter((group) => group.isActive)
      .map((group) => ({
        label: group.groupName || group.name || group.groupId,
        value: group.groupId,
      })),
  );

export const mapDepartmentsToLookupOptions = (
  departments: Department[],
): MasterLookupOption[] =>
  sortByLabel(
    departments
      .filter((department) => department.isActive)
      .map((department) => ({
        label: department.departmentName || department.name,
        value: department.departmentId,
      })),
  );

export const getMasterLookupOptions = async (
  signal?: AbortSignal,
): Promise<MasterLookupOptions> => {
  const [usersPage, roles, groups, departments] = await Promise.all([
    getUsers({ page: 0, size: 100 }, signal),
    getRoles(true, signal),
    getGroups(true, signal),
    getDepartments(true, signal),
  ]);

  return {
    users: mapUsersToLookupOptions(usersPage.content),
    roles: mapRolesToLookupOptions(roles),
    groups: mapGroupsToLookupOptions(groups),
    departments: mapDepartmentsToLookupOptions(departments),
  };
};
