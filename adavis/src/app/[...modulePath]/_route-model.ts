import { ROUTES } from "@/config/routes";
import {
  equipmentStatusLabels,
  type EquipmentStatusFilter,
} from "@/features/iiot/equipment/data/equipment-overview";
import type { TopologyKind } from "@/features/master-management/plant-topology/api";
import type { IiotMasterSection } from "@/features/master-management/iiot-master/api";
import { MODULE_SECTIONS } from "@/features/modules/data/module-data";

export type ModulePath = string[];

export const modules = MODULE_SECTIONS.flatMap((section) => section.modules);

export const equipmentStatusFilters: EquipmentStatusFilter[] = [
  "all",
  "running",
  "idle",
  "communication-error",
  "maintenance",
  "offline",
];

export const iiotMasterSections: IiotMasterSection[] = [
  "equipments",
  "critical-parameters",
  "critical-parameter-limits",
  "product-master",
];

export function findModule(modulePath: ModulePath) {
  const pathname = `/${modulePath.join("/")}`;
  return modules.find((module) => module.href === pathname);
}

export function isEquipmentDetailPath(modulePath: ModulePath) {
  return (
    modulePath[0] === "iiot" &&
    modulePath[1] === "equipment" &&
    modulePath[2] !== undefined
  );
}

export function isEquipmentOverviewPath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.iiot;
}

export function getEquipmentStatusFilter(modulePath: ModulePath) {
  if (
    modulePath[0] !== "iiot" ||
    modulePath[1] !== "equipment-overview" ||
    modulePath.length !== 3
  ) {
    return undefined;
  }

  const status = modulePath[2] as EquipmentStatusFilter;
  return equipmentStatusFilters.includes(status) ? status : undefined;
}

export function isEquipmentStatusPath(modulePath: ModulePath) {
  return getEquipmentStatusFilter(modulePath) !== undefined;
}

export function isMonitoringConsolePath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.iiotMonitoring;
}

export function isManufacturingOverviewPath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.iiotManufacturingOverview;
}

export function isAnalyticsPath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.iiotAnalytics;
}

export function isEditProfilePath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.editProfile;
}

export function isUpdatePasswordPath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.updatePassword;
}

export function isUserManagementPath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.masterManagement;
}

export function isDashboardPath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === "/master-management/dashboard";
}

export function isTenantManagementPath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.masterTenants;
}

export function isCreateTenantPath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.masterCreateTenant;
}

export function isPlantTopologyPath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.masterPlantTopology;
}

export function isCreateTopologyPath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.masterCreateTopology;
}

export function isUsersPath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.masterUsers;
}

export function getUserStatusFilter(modulePath: ModulePath) {
  const pathname = `/${modulePath.join("/")}`;

  if (pathname === ROUTES.masterActiveUsers) return "active";
  if (pathname === ROUTES.masterIdleUsers) return "idle";
  if (pathname === ROUTES.masterBlockedUsers) return "blocked";
  if (pathname === ROUTES.masterDeactivatedUsers) return "deactivated";

  return undefined;
}

export function isUserStatusPath(modulePath: ModulePath) {
  return getUserStatusFilter(modulePath) !== undefined;
}

export function getUserStatusTitle(modulePath: ModulePath) {
  const statusFilter = getUserStatusFilter(modulePath);

  if (statusFilter === "active") return "Active Users";
  if (statusFilter === "idle") return "Idle Users";
  if (statusFilter === "blocked") return "Blocked Users";
  if (statusFilter === "deactivated") return "Deactivated Users";

  return undefined;
}

export function isCreateUserPath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.masterCreateUser;
}

export function isEditUserPath(modulePath: ModulePath) {
  return (
    modulePath[0] === "master-management" &&
    modulePath[1] === "users" &&
    modulePath[2] !== undefined &&
    modulePath[3] === "edit"
  );
}

export function isRoleManagementPath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.masterRoles;
}

export function isCreateRolePath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.masterCreateRole;
}

export function isUserGroupManagementPath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.masterUserGroups;
}

export function isCreateUserGroupPath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.masterCreateUserGroup;
}

export function isDepartmentManagementPath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.masterDepartments;
}

export function isCreateDepartmentPath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.masterCreateDepartment;
}

export function isAssignmentManagementPath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.masterAssignments;
}

export function isCreateAssignmentPath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.masterCreateAssignment;
}

export function getIiotMasterSection(modulePath: ModulePath) {
  if (
    modulePath[0] !== "master-management" ||
    modulePath[1] !== "iiot-master" ||
    modulePath.length !== 3
  ) {
    return undefined;
  }

  const section = modulePath[2] as IiotMasterSection;
  return iiotMasterSections.includes(section) ? section : undefined;
}

export function isIiotMasterPath(modulePath: ModulePath) {
  return getIiotMasterSection(modulePath) !== undefined;
}

export function getCreateIiotMasterSection(modulePath: ModulePath) {
  if (
    modulePath[0] !== "master-management" ||
    modulePath[1] !== "iiot-master" ||
    modulePath[3] !== "create" ||
    modulePath.length !== 4
  ) {
    return undefined;
  }

  const section = modulePath[2] as IiotMasterSection;
  return iiotMasterSections.includes(section) ? section : undefined;
}

export function isCreateIiotMasterPath(modulePath: ModulePath) {
  return getCreateIiotMasterSection(modulePath) !== undefined;
}

export function getIiotMasterTitle(section: IiotMasterSection) {
  if (section === "equipments") return "Manage Equipments";
  if (section === "critical-parameters") return "Manage Critical Parameters";
  if (section === "critical-parameter-limits") {
    return "Manage Critical Parameter Limits";
  }
  return "Manage Product Master";
}

export function getIiotMasterRoute(section: IiotMasterSection) {
  if (section === "critical-parameters") {
    return ROUTES.masterIiotCriticalParameters;
  }
  if (section === "critical-parameter-limits") {
    return ROUTES.masterIiotCriticalParameterLimits;
  }
  if (section === "product-master") {
    return ROUTES.masterIiotProductMaster;
  }
  return ROUTES.masterIiotEquipments;
}

export function isAuditLogsPath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.masterAuditLogs;
}

export function isLicenseManagementPath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.masterLicenses;
}

export function isLicenseHistoryPath(modulePath: ModulePath) {
  return `/${modulePath.join("/")}` === ROUTES.masterLicenseHistory;
}

export function isUserDetailPath(modulePath: ModulePath) {
  return (
    modulePath[0] === "master-management" &&
    modulePath[1] === "users" &&
    modulePath[2] !== undefined &&
    !isUserStatusPath(modulePath) &&
    !isCreateUserPath(modulePath) &&
    !isEditUserPath(modulePath)
  );
}

export function getEquipmentDetailTab(modulePath: ModulePath) {
  if (
    modulePath[3] === "trends" ||
    modulePath[3] === "alarms" ||
    modulePath[3] === "events"
  ) {
    return modulePath[3];
  }

  return "parameters";
}

export function getCreateTopologyKind(
  view: string | string[] | undefined,
): TopologyKind {
  const requestedKind = Array.isArray(view) ? view[0] : view;

  if (
    requestedKind === "blocks" ||
    requestedKind === "areas" ||
    requestedKind === "rooms"
  ) {
    return requestedKind;
  }

  return "plants";
}

export function isIiotChildRoute(modulePath: ModulePath) {
  return (
    isEquipmentOverviewPath(modulePath) ||
    isEquipmentDetailPath(modulePath) ||
    isEquipmentStatusPath(modulePath) ||
    isManufacturingOverviewPath(modulePath) ||
    isMonitoringConsolePath(modulePath) ||
    isAnalyticsPath(modulePath)
  );
}

export function isMasterChildRoute(modulePath: ModulePath) {
  return (
    isEditProfilePath(modulePath) ||
    isUpdatePasswordPath(modulePath) ||
    isUserManagementPath(modulePath) ||
    isTenantManagementPath(modulePath) ||
    isCreateTenantPath(modulePath) ||
    isPlantTopologyPath(modulePath) ||
    isCreateTopologyPath(modulePath) ||
    isUsersPath(modulePath) ||
    isUserStatusPath(modulePath) ||
    isCreateUserPath(modulePath) ||
    isEditUserPath(modulePath) ||
    isRoleManagementPath(modulePath) ||
    isCreateRolePath(modulePath) ||
    isUserGroupManagementPath(modulePath) ||
    isCreateUserGroupPath(modulePath) ||
    isDepartmentManagementPath(modulePath) ||
    isCreateDepartmentPath(modulePath) ||
    isAssignmentManagementPath(modulePath) ||
    isCreateAssignmentPath(modulePath) ||
    isIiotMasterPath(modulePath) ||
    isCreateIiotMasterPath(modulePath) ||
    isLicenseManagementPath(modulePath) ||
    isLicenseHistoryPath(modulePath) ||
    isAuditLogsPath(modulePath) ||
    isUserDetailPath(modulePath)
  );
}

export function getModulePageTitle(
  modulePath: ModulePath,
  moduleId?: string,
  moduleTitle?: string,
) {
  const equipmentStatusFilter = getEquipmentStatusFilter(modulePath);
  if (equipmentStatusFilter) {
    return equipmentStatusLabels[equipmentStatusFilter];
  }

  if (isMonitoringConsolePath(modulePath)) return "Analytics";
  if (isEquipmentOverviewPath(modulePath)) return "Equipment Overview";
  if (isManufacturingOverviewPath(modulePath)) return "Equipment Overview";
  if (isAnalyticsPath(modulePath)) return "OEE";
  if (isEditProfilePath(modulePath)) return "Edit Profile";
  if (isUpdatePasswordPath(modulePath)) return "Update Password";
  if (isUserManagementPath(modulePath)) return "System Admin Dashboard";
  if (isTenantManagementPath(modulePath)) return "Tenant Management";
  if (isCreateTenantPath(modulePath)) return "Create New Tenant";
  if (isPlantTopologyPath(modulePath)) return "Plant Topology";
  if (isCreateTopologyPath(modulePath)) return "Create Plant Topology";
  if (isUsersPath(modulePath)) return "User Management";
  if (isUserStatusPath(modulePath)) return getUserStatusTitle(modulePath);
  if (isCreateUserPath(modulePath)) return "Create New User";
  if (isEditUserPath(modulePath)) return "Edit User";
  if (isRoleManagementPath(modulePath)) return "Role Management";
  if (isCreateRolePath(modulePath)) return "Create New Role";
  if (isUserGroupManagementPath(modulePath)) return "User Group Management";
  if (isCreateUserGroupPath(modulePath)) return "Create New User Group";
  if (isDepartmentManagementPath(modulePath)) return "Department Management";
  if (isCreateDepartmentPath(modulePath)) return "Create New Department";
  if (isAssignmentManagementPath(modulePath)) return "User & Group Context Assignments";
  if (isCreateAssignmentPath(modulePath)) return "Create New Assignment";

  const iiotMasterSection = getIiotMasterSection(modulePath);
  if (iiotMasterSection) {
    return getIiotMasterTitle(iiotMasterSection);
  }

  const createIiotMasterSection = getCreateIiotMasterSection(modulePath);
  if (createIiotMasterSection) {
    return `Create ${getIiotMasterTitle(createIiotMasterSection).replace("Manage ", "")}`;
  }

  if (isAuditLogsPath(modulePath)) return "Audit Logs";
  if (isLicenseManagementPath(modulePath)) return "License Management";
  if (isLicenseHistoryPath(modulePath)) return "License History";
  if (isUserDetailPath(modulePath)) return "User Management";
  if (moduleId === "iiot" || moduleId === "ai-iot") return "Equipment Overview";

  return moduleTitle;
}
