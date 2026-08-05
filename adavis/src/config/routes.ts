export const ROUTES = {
  login: "/auth",
  forgotPassword: "/forgot-password",
  resetPassword: "/reset-password",
  editProfile: "/profile/edit",
  updatePassword: "/profile/update-password",
  modules: "/modules",
  masterManagement: "/master-management/user-management",
  masterTenants: "/master-management/tenants",
  masterCreateTenant: "/master-management/tenants/create",
  masterPlantTopology: "/master-management/plant-topology",
  masterCreateTopology: "/master-management/plant-topology/create",
  masterUsers: "/master-management/users",
  masterActiveUsers: "/master-management/users/active",
  masterIdleUsers: "/master-management/users/idle",
  masterBlockedUsers: "/master-management/users/blocked",
  masterDeactivatedUsers: "/master-management/users/deactivated",
  masterCreateUser: "/master-management/users/create",
  masterRoles: "/master-management/roles",
  masterCreateRole: "/master-management/roles/create",
  masterUserGroups: "/master-management/user-groups",
  masterCreateUserGroup: "/master-management/user-groups/create",
  masterDepartments: "/master-management/departments",
  masterCreateDepartment: "/master-management/departments/create",
  masterAssignments: "/master-management/assignments",
  masterCreateAssignment: "/master-management/assignments/create",
  masterIiotEquipments: "/master-management/iiot-master/equipments",
  masterCreateIiotEquipment: "/master-management/iiot-master/equipments/create",
  masterIiotCriticalParameters:
    "/master-management/iiot-master/critical-parameters",
  masterCreateIiotCriticalParameter:
    "/master-management/iiot-master/critical-parameters/create",
  masterIiotCriticalParameterLimits:
    "/master-management/iiot-master/critical-parameter-limits",
  masterCreateIiotCriticalParameterLimit:
    "/master-management/iiot-master/critical-parameter-limits/create",
  masterIiotProductMaster: "/master-management/iiot-master/product-master",
  masterCreateIiotProductMaster:
    "/master-management/iiot-master/product-master/create",
  masterLicenses: "/master-management/licenses",
  masterLicenseHistory: "/master-management/licenses/history",
  masterAuditLogs: "/master-management/audit-logs",
  iiotManufacturingOverview: "/iiot/manufacturing-overview",
  iiot: "/iiot/equipment-overview",
  iiotMonitoring: "/iiot/monitoring-console",
  iiotAnalytics: "/iiot/analytics",
  projectEngine: "/project-engine",
  manufacturingElogbook: "/manufacturing/elogbook",
  manufacturingEbmr: "/manufacturing/ebmr",
  iiotEquipment: "/iiot/equipment",
  manufacturingCleaningValidations: "/manufacturing/cleaning-validations",
  qualityQms: "/quality/qms",
  qualityApqr: "/quality/apqr",
  qualityInvestigation: "/quality/investigation",
  complianceAudit: "/compliance/audit",
  complianceDms: "/compliance/dms",
} as const;

export const ROUTE_BUILDERS = {
  iiotEquipmentStatus: (status: string) =>
    `/iiot/equipment-overview/${encodeURIComponent(status)}`,
  masterEditUser: (userId: string) =>
    `/master-management/users/${encodeURIComponent(userId)}/edit`,
} as const;

export type AppRoute = (typeof ROUTES)[keyof typeof ROUTES];
