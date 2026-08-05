import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { ROUTES } from "@/config/routes";
import AnalyticsOeeScreen from "@/features/iiot/analytics/screens/AnalyticsOeeScreen";
import EquipmentDetailScreen from "@/features/iiot/equipment/screens/EquipmentDetailScreen";
import EquipmentOverviewScreen from "@/features/iiot/equipment/screens/EquipmentOverviewScreen";
import EquipmentStatusListScreen from "@/features/iiot/equipment/screens/EquipmentStatusListScreen";
import { equipmentStatusLabels } from "@/features/iiot/equipment/data/equipment-overview";
import MonitoringConsoleScreen from "@/features/iiot/monitor-console/screens/MonitoringConsoleScreen";
import AuditLogsScreen from "@/features/master-management/audit-logs/screens/AuditLogsScreen";
import SystemAdminDashboardScreen from "@/features/master-management/dashboard/screens/SystemAdminDashboardScreen";
import CreateDepartmentScreen from "@/features/master-management/department-management/screens/CreateDepartmentScreen";
import DepartmentManagementScreen from "@/features/master-management/department-management/screens/DepartmentManagementScreen";
import AssignmentManagementScreen from "@/features/master-management/assignment-management/screens/AssignmentManagementScreen";
import CreateAssignmentScreen from "@/features/master-management/assignment-management/screens/CreateAssignmentScreen";
import CreateIiotMasterScreen from "@/features/master-management/iiot-master/screens/CreateIiotMasterScreen";
import IiotMasterScreen from "@/features/master-management/iiot-master/screens/IiotMasterScreen";
import LicenseHistoryScreen from "@/features/master-management/license-management/screens/LicenseHistoryScreen";
import LicenseManagementScreen from "@/features/master-management/license-management/screens/LicenseManagementScreen";
import ProfileEditScreen from "@/features/profile/screens/ProfileEditScreen";
import UpdatePasswordScreen from "@/features/profile/screens/UpdatePasswordScreen";
import CreateTenantScreen from "@/features/master-management/tenant-management/screens/CreateTenantScreen";
import TenantManagementScreen from "@/features/master-management/tenant-management/screens/TenantManagementScreen";
import CreateTopologyRecordScreen from "@/features/master-management/plant-topology/screens/CreateTopologyRecordScreen";
import PlantTopologyScreen from "@/features/master-management/plant-topology/screens/PlantTopologyScreen";
import CreateRoleScreen from "@/features/master-management/role-management/screens/CreateRoleScreen";
import RoleManagementScreen from "@/features/master-management/role-management/screens/RoleManagementScreen";
import CreateUserGroupScreen from "@/features/master-management/user-group-management/screens/CreateUserGroupScreen";
import UserGroupManagementScreen from "@/features/master-management/user-group-management/screens/UserGroupManagementScreen";
import CreateUserScreen from "@/features/master-management/user-management/screens/CreateUserScreen";
import EditUserScreen from "@/features/master-management/user-management/screens/EditUserScreen";
import UserDetailScreen from "@/features/master-management/user-management/screens/UserDetailScreen";
import UsersScreen from "@/features/master-management/user-management/screens/UsersScreen";
import { users } from "@/features/master-management/user-management/data/users";
import {
  equipmentStatusFilters,
  findModule,
  getCreateIiotMasterSection,
  getCreateTopologyKind,
  getEquipmentDetailTab,
  getEquipmentStatusFilter,
  getIiotMasterSection,
  getIiotMasterTitle,
  getModulePageTitle,
  getUserStatusFilter,
  getUserStatusTitle,
  isAnalyticsPath,
  isAssignmentManagementPath,
  isAuditLogsPath,
  isDashboardPath,
  isCreateAssignmentPath,
  isCreateDepartmentPath,
  isCreateRolePath,
  isCreateTenantPath,
  isCreateTopologyPath,
  isCreateUserGroupPath,
  isCreateUserPath,
  isDepartmentManagementPath,
  isEditProfilePath,
  isEditUserPath,
  isEquipmentDetailPath,
  isEquipmentOverviewPath,
  isLicenseHistoryPath,
  isLicenseManagementPath,
  isManufacturingOverviewPath,
  isMonitoringConsolePath,
  isPlantTopologyPath,
  isRoleManagementPath,
  isTenantManagementPath,
  isUpdatePasswordPath,
  isUserDetailPath,
  isUserGroupManagementPath,
  isUserManagementPath,
  isUserStatusPath,
  isUsersPath,
  modules,
} from "./_route-model";

interface ModuleRoutePageProps {
  params: Promise<{ modulePath: string[] }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}

export function generateStaticParams() {
  return [
    ...modules.map((module) => ({
      modulePath: module.href.slice(1).split("/"),
    })),
    {
      modulePath: ROUTES.iiot.slice(1).split("/"),
    },
    {
      modulePath: ["iiot", "equipment", "fbd-450kg-pviii"],
    },
    {
      modulePath: ["iiot", "equipment", "fbd-450kg-pviii", "trends"],
    },
    {
      modulePath: ["iiot", "equipment", "fbd-450kg-pviii", "alarms"],
    },
    {
      modulePath: ["iiot", "equipment", "fbd-450kg-pviii", "events"],
    },
    ...equipmentStatusFilters.map((status) => ({
      modulePath: ["iiot", "equipment-overview", status],
    })),
    {
      modulePath: ROUTES.iiotMonitoring.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.iiotAnalytics.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.editProfile.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.updatePassword.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterUsers.slice(1).split("/"),
    },
    {
      modulePath: ["master-management", "dashboard"],
    },
    {
      modulePath: ROUTES.masterTenants.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterCreateTenant.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterPlantTopology.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterCreateTopology.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterActiveUsers.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterIdleUsers.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterBlockedUsers.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterDeactivatedUsers.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterCreateUser.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterRoles.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterCreateRole.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterUserGroups.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterCreateUserGroup.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterDepartments.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterCreateDepartment.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterAssignments.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterCreateAssignment.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterIiotEquipments.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterCreateIiotEquipment.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterIiotCriticalParameters.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterCreateIiotCriticalParameter.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterIiotCriticalParameterLimits.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterCreateIiotCriticalParameterLimit
        .slice(1)
        .split("/"),
    },
    {
      modulePath: ROUTES.masterIiotProductMaster.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterCreateIiotProductMaster.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterAuditLogs.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterLicenses.slice(1).split("/"),
    },
    {
      modulePath: ROUTES.masterLicenseHistory.slice(1).split("/"),
    },
    ...users.map((user) => ({
      modulePath: ["master-management", "users", user.id],
    })),
  ];
}

export async function generateMetadata({
  params,
}: ModuleRoutePageProps): Promise<Metadata> {
  const modulePath = (await params).modulePath;
  const moduleItem = findModule(modulePath);

  if (isEquipmentDetailPath(modulePath)) {
    return {
      title: `${modulePath[2].toUpperCase()} | ADAVIS`,
    };
  }

  if (isEquipmentOverviewPath(modulePath)) {
    return {
      title: "Equipment Overview | ADAVIS",
    };
  }

  const equipmentStatusFilter = getEquipmentStatusFilter(modulePath);
  if (equipmentStatusFilter) {
    return {
      title: `${equipmentStatusLabels[equipmentStatusFilter]} | ADAVIS`,
    };
  }

  if (isMonitoringConsolePath(modulePath)) {
    return {
      title: "Analytics | ADAVIS",
    };
  }

  if (isManufacturingOverviewPath(modulePath)) {
    return {
      title: "Equipment Overview | ADAVIS",
    };
  }

  if (isAnalyticsPath(modulePath)) {
    return {
      title: "OEE | ADAVIS",
    };
  }

  if (isEditProfilePath(modulePath)) {
    return {
      title: "Edit Profile | ADAVIS",
    };
  }

  if (isUpdatePasswordPath(modulePath)) {
    return {
      title: "Update Password | ADAVIS",
    };
  }

  if (isUserManagementPath(modulePath)) {
    return {
      title: "System Admin Dashboard | ADAVIS",
    };
  }

  if (isDashboardPath(modulePath)) {
    return {
      title: "System Admin Dashboard | ADAVIS",
    };
  }

  if (isTenantManagementPath(modulePath)) {
    return { title: "Tenant Management | ADAVIS" };
  }

  if (isCreateTenantPath(modulePath)) {
    return { title: "Create New Tenant | ADAVIS" };
  }

  if (isPlantTopologyPath(modulePath)) {
    return { title: "Plant Topology | ADAVIS" };
  }

  if (isCreateTopologyPath(modulePath)) {
    return { title: "Create Plant Topology | ADAVIS" };
  }

  if (isUsersPath(modulePath)) {
    return {
      title: "User Management | ADAVIS",
    };
  }

  if (isUserStatusPath(modulePath)) {
    return {
      title: `${getUserStatusTitle(modulePath)} | ADAVIS`,
    };
  }

  if (isCreateUserPath(modulePath)) {
    return {
      title: "Create New User | ADAVIS",
    };
  }

  if (isEditUserPath(modulePath)) {
    return {
      title: `Edit ${modulePath[2].toUpperCase()} | ADAVIS`,
    };
  }

  if (isRoleManagementPath(modulePath)) {
    return {
      title: "Role Management | ADAVIS",
    };
  }

  if (isCreateRolePath(modulePath)) {
    return {
      title: "Create New Role | ADAVIS",
    };
  }

  if (isUserGroupManagementPath(modulePath)) {
    return {
      title: "User Group Management | ADAVIS",
    };
  }

  if (isCreateUserGroupPath(modulePath)) {
    return {
      title: "Create New User Group | ADAVIS",
    };
  }

  if (isDepartmentManagementPath(modulePath)) {
    return {
      title: "Department Management | ADAVIS",
    };
  }

  if (isCreateDepartmentPath(modulePath)) {
    return {
      title: "Create New Department | ADAVIS",
    };
  }

  if (isAssignmentManagementPath(modulePath)) {
    return { title: "Assignment Management | ADAVIS" };
  }

  if (isCreateAssignmentPath(modulePath)) {
    return { title: "Create New Assignment | ADAVIS" };
  }

  const iiotMasterSection = getIiotMasterSection(modulePath);
  if (iiotMasterSection) {
    return { title: `${getIiotMasterTitle(iiotMasterSection)} | ADAVIS` };
  }

  const createIiotMasterSection = getCreateIiotMasterSection(modulePath);
  if (createIiotMasterSection) {
    return {
      title: `Create ${getIiotMasterTitle(createIiotMasterSection).replace("Manage ", "")} | ADAVIS`,
    };
  }

  if (isAuditLogsPath(modulePath)) {
    return {
      title: "Audit Logs | ADAVIS",
    };
  }

  if (isLicenseManagementPath(modulePath)) {
    return { title: "License Management | ADAVIS" };
  }

  if (isLicenseHistoryPath(modulePath)) {
    return { title: "License History | ADAVIS" };
  }

  if (isUserDetailPath(modulePath)) {
    return {
      title: `${modulePath[2].toUpperCase()} | ADAVIS`,
    };
  }

  return {
    title: getModulePageTitle(modulePath, moduleItem?.id, moduleItem?.title)
      ? `${getModulePageTitle(modulePath, moduleItem?.id, moduleItem?.title)} | ADAVIS`
      : "Module Not Found | ADAVIS",
  };
}

export default async function ModuleRoutePage({
  params,
  searchParams,
}: ModuleRoutePageProps) {
  const modulePath = (await params).modulePath;
  const query = await searchParams;
  const moduleItem = findModule(modulePath);

  if (isEquipmentDetailPath(modulePath)) {
    return (
      <EquipmentDetailScreen
        equipmentId={modulePath[2]}
        activeTab={getEquipmentDetailTab(modulePath)}
      />
    );
  }

  if (isEquipmentOverviewPath(modulePath)) {
    return <EquipmentOverviewScreen />;
  }

  const equipmentStatusFilter = getEquipmentStatusFilter(modulePath);
  if (equipmentStatusFilter) {
    return <EquipmentStatusListScreen statusFilter={equipmentStatusFilter} />;
  }

  if (isMonitoringConsolePath(modulePath)) {
    return <MonitoringConsoleScreen />;
  }

  if (isManufacturingOverviewPath(modulePath)) {
    return <EquipmentOverviewScreen />;
  }

  if (isAnalyticsPath(modulePath)) {
    return <AnalyticsOeeScreen />;
  }

  if (isEditProfilePath(modulePath)) {
    return <ProfileEditScreen />;
  }

  if (isUpdatePasswordPath(modulePath)) {
    return <UpdatePasswordScreen />;
  }

  if (isUserManagementPath(modulePath)) {
    return <SystemAdminDashboardScreen />;
  }

  if (isDashboardPath(modulePath)) {
    return <SystemAdminDashboardScreen />;
  }

  if (isTenantManagementPath(modulePath)) {
    return <TenantManagementScreen />;
  }

  if (isCreateTenantPath(modulePath)) {
    return <CreateTenantScreen />;
  }

  if (isPlantTopologyPath(modulePath)) {
    return <PlantTopologyScreen />;
  }

  if (isCreateTopologyPath(modulePath)) {
    const kind = getCreateTopologyKind(query.view);
    const tenantIdValue = Array.isArray(query.tenantId) ? query.tenantId[0] : query.tenantId;
    return <CreateTopologyRecordScreen kind={kind} tenantId={tenantIdValue ?? ""} />;
  }

  if (isUsersPath(modulePath)) {
    return <UsersScreen />;
  }

  if (isUserStatusPath(modulePath)) {
    return <UsersScreen statusFilter={getUserStatusFilter(modulePath)} />;
  }

  if (isCreateUserPath(modulePath)) {
    return <CreateUserScreen />;
  }

  if (isEditUserPath(modulePath)) {
    return <EditUserScreen userId={modulePath[2]} />;
  }

  if (isRoleManagementPath(modulePath)) {
    return <RoleManagementScreen />;
  }

  if (isCreateRolePath(modulePath)) {
    return <CreateRoleScreen />;
  }

  if (isUserGroupManagementPath(modulePath)) {
    return <UserGroupManagementScreen />;
  }

  if (isCreateUserGroupPath(modulePath)) {
    return <CreateUserGroupScreen />;
  }

  if (isDepartmentManagementPath(modulePath)) {
    return <DepartmentManagementScreen />;
  }

  if (isCreateDepartmentPath(modulePath)) {
    return <CreateDepartmentScreen />;
  }

  if (isAssignmentManagementPath(modulePath)) {
    return <AssignmentManagementScreen />;
  }

  if (isCreateAssignmentPath(modulePath)) {
    return <CreateAssignmentScreen />;
  }

  const iiotMasterSection = getIiotMasterSection(modulePath);
  if (iiotMasterSection) {
    return <IiotMasterScreen section={iiotMasterSection} />;
  }

  const createIiotMasterSection = getCreateIiotMasterSection(modulePath);
  if (createIiotMasterSection) {
    return <CreateIiotMasterScreen section={createIiotMasterSection} />;
  }

  if (isAuditLogsPath(modulePath)) {
    return <AuditLogsScreen />;
  }

  if (isLicenseManagementPath(modulePath)) {
    return <LicenseManagementScreen />;
  }

  if (isLicenseHistoryPath(modulePath)) {
    return <LicenseHistoryScreen />;
  }

  if (isUserDetailPath(modulePath)) {
    return <UserDetailScreen userId={modulePath[2]} />;
  }

  if (!moduleItem) {
    notFound();
  }

  if (moduleItem.id === "iiot" || moduleItem.id === "ai-iot") {
    return <EquipmentOverviewScreen />;
  }

  return (
    <section aria-label={moduleItem.title} className="min-h-full" />
  );
}
