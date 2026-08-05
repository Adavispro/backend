import type { ReactNode } from "react";
import ModuleAppLayout from "@/components/layout/ModuleAppLayout";
import { ROUTES } from "@/config/routes";
import { getUserById } from "@/features/master-management/user-management/data/users";
import { equipmentStatusLabels } from "@/features/iiot/equipment/data/equipment-overview";
import {
  findModule,
  getCreateIiotMasterSection,
  getIiotMasterRoute,
  getIiotMasterTitle,
  getModulePageTitle,
  getUserStatusTitle,
  isCreateAssignmentPath,
  isCreateDepartmentPath,
  isCreateIiotMasterPath,
  isCreateRolePath,
  isCreateTenantPath,
  isCreateTopologyPath,
  isCreateUserGroupPath,
  isCreateUserPath,
  isEditProfilePath,
  isEditUserPath,
  isEquipmentDetailPath,
  isEquipmentStatusPath,
  isIiotChildRoute,
  isLicenseHistoryPath,
  isMasterChildRoute,
  modules,
  isUpdatePasswordPath,
  isUserDetailPath,
  isUserStatusPath,
  getEquipmentStatusFilter,
} from "./_route-model";

interface ModuleRouteLayoutProps {
  children: ReactNode;
  params: Promise<{ modulePath: string[] }>;
}

export default async function ModuleRouteLayout({
  children,
  params,
}: ModuleRouteLayoutProps) {
  const modulePath = (await params).modulePath;
  const moduleItem = isIiotChildRoute(modulePath)
    ? modules.find((module) => module.id === "iiot")
    : isMasterChildRoute(modulePath)
      ? modules.find((module) => module.id === "master-management")
    : findModule(modulePath);
  const equipmentName = modulePath[2];
  const selectedUser = isUserDetailPath(modulePath)
    ? getUserById(modulePath[2])
    : undefined;

  return (
    <ModuleAppLayout
      moduleId={moduleItem?.id}
      title={getModulePageTitle(modulePath, moduleItem?.id, moduleItem?.title)}
      breadcrumbs={
        isEquipmentDetailPath(modulePath)
          ? [
              { label: "Analytics", href: ROUTES.iiotMonitoring },
              { label: equipmentName ?? "Equipment", active: true },
            ]
          : isEquipmentStatusPath(modulePath)
            ? [
                { label: "Equipment Overview", href: ROUTES.iiot },
                {
                  label:
                    equipmentStatusLabels[
                      getEquipmentStatusFilter(modulePath) ?? "all"
                    ],
                  active: true,
                },
              ]
          : isUserStatusPath(modulePath)
            ? [
                {
                  label: "System Admin Dashboard",
                  href: ROUTES.masterManagement,
                },
                {
                  label: getUserStatusTitle(modulePath) ?? "Users",
                  active: true,
                },
              ]
          : isEditProfilePath(modulePath)
            ? [
                { label: "Profile", active: false },
                { label: "Edit Profile", active: true },
              ]
          : isUpdatePasswordPath(modulePath)
            ? [
                { label: "Profile", active: false },
                { label: "Update Password", active: true },
              ]
          : isCreateTenantPath(modulePath)
            ? [
                { label: "Tenants", href: ROUTES.masterTenants },
                { label: "Create New Tenant", active: true },
              ]
          : isCreateTopologyPath(modulePath)
            ? [
                { label: "Plant Topology", href: ROUTES.masterPlantTopology },
                { label: "Create", active: true },
              ]
          : isCreateUserPath(modulePath)
            ? [
                { label: "User Management", href: ROUTES.masterUsers },
                { label: "Create New User", active: true },
              ]
          : isCreateRolePath(modulePath)
            ? [
                { label: "Role Management", href: ROUTES.masterRoles },
                { label: "Create New Role", active: true },
              ]
          : isEditUserPath(modulePath)
            ? [
                { label: "Users List", href: ROUTES.masterUsers },
                { label: `Edit ${modulePath[2].toUpperCase()}`, active: true },
              ]
          : isUserDetailPath(modulePath)
            ? [
                { label: "Users List", href: ROUTES.masterUsers },
                { label: selectedUser?.name ?? modulePath[2].toUpperCase(), active: true },
              ]
          : isLicenseHistoryPath(modulePath)
            ? [
                { label: "License Management", href: ROUTES.masterLicenses },
                { label: "License History", active: true },
              ]
          : isCreateAssignmentPath(modulePath)
            ? [
                { label: "User & Group Context Assignments", href: ROUTES.masterAssignments },
                { label: "Create New Assignment", active: true },
              ]
          : isCreateUserGroupPath(modulePath)
            ? [
                { label: "User Group Management", href: ROUTES.masterUserGroups },
                { label: "Create New User Group", active: true },
              ]
          : isCreateDepartmentPath(modulePath)
            ? [
                { label: "Department Management", href: ROUTES.masterDepartments },
                { label: "Create New Department", active: true },
              ]
          : isCreateIiotMasterPath(modulePath)
            ? [
                {
                  label: getCreateIiotMasterSection(modulePath)
                    ? getIiotMasterTitle(getCreateIiotMasterSection(modulePath)!)
                    : "IIOT Master",
                  href: getCreateIiotMasterSection(modulePath)
                    ? getIiotMasterRoute(getCreateIiotMasterSection(modulePath)!)
                    : ROUTES.masterIiotEquipments,
                },
                { label: "Create", active: true },
              ]
          : undefined
      }
    >
      {children}
    </ModuleAppLayout>
  );
}
