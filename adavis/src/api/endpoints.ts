export const API_ENDPOINTS = {
  auth: {
    initiateLogin: "/api/v1/auth/login-initiate",
    login: "/api/v1/auth/authenticate",
    refresh: "/api/v1/auth/refresh",
    logout: "/api/v1/auth/logout",
    internalProvision: "/internal/v1/auth/users/provision",
    setPassword: "/api/auth/v1/password/set",
    forgotPassword: "/api/auth/v1/password/forgot",
    resetPassword: "/api/auth/v1/password/reset",
    adminResetPassword: "/api/auth/v1/password/admin-reset",
    passwordPolicy: "/api/auth/password/policy",
    verifyPasswordPolicy: "/api/auth/password/policy/verify",
    accountAction: (
      userId: string,
      action: "activate" | "block" | "deactivate" | "unblock",
    ) => `/api/auth/v1/accounts/${userId}/${action}`,
  },
  masterManagement: {
    tenants: "/api/v1/mdm/tenants",
    tenantDetail: (tenantId: string) => `/api/v1/mdm/tenants/${tenantId}`,
    plants: "/api/v1/mdm/plants",
    plantDetail: (plantId: string) => `/api/v1/mdm/plants/${plantId}`,
    blocks: "/api/v1/mdm/blocks",
    blockDetail: (blockId: string) => `/api/v1/mdm/blocks/${blockId}`,
    areas: "/api/v1/mdm/areas",
    areaDetail: (areaId: string) => `/api/v1/mdm/areas/${areaId}`,
    rooms: "/api/v1/mdm/rooms",
    roomDetail: (roomId: string) => `/api/v1/mdm/rooms/${roomId}`,
    users: "/api/v1/mdm/users",
    onboardUser: "/api/v1/mdm/users/onboard",
    userDetail: (userId: string) => `/api/v1/mdm/users/${userId}`,
    userLoginContext: (userId: string) =>
      `/api/v1/mdm/users/${userId}/login-context`,
    userLifecycle: (userId: string) => `/api/v1/mdm/users/${userId}/lifecycle`,
    userPasswordReset: (userId: string) =>
      `/api/v1/mdm/users/${userId}/password-reset`,
    roles: "/api/v1/mdm/roles",
    roleDetail: (roleId: string) => `/api/v1/mdm/roles/${roleId}`,
    rolePermissions: (roleId: string) =>
      `/api/v1/mdm/roles/${roleId}/permissions`,
    userGroups: "/api/v1/mdm/user-groups",
    userGroupDetail: (groupId: string) =>
      `/api/v1/mdm/user-groups/${groupId}`,
    departments: "/api/v1/mdm/departments",
    departmentDetail: (departmentId: string) =>
      `/api/v1/mdm/departments/${departmentId}`,
    departmentTree: "/api/v1/mdm/departments/tree",
    assignments: "/api/v1/mdm/assignments",
    assignmentDetail: (assignmentId: string) =>
      `/api/v1/mdm/assignments/${assignmentId}`,
    modules: "/api/v1/mdm/modules",
    screens: "/api/v1/mdm/screens",
    features: "/api/v1/mdm/features",
    permissionMatrix: "/api/v1/mdm/permissions/matrix-tree",
    license: "/api/v1/mdm/license",
    tenantLicense: (tenantId: string) =>
      `/api/v1/mdm/license/tenant/${tenantId}`,
    licenseHistory: (tenantId: string) =>
      `/api/v1/mdm/license/tenant/${tenantId}/history`,
    upgradeLicense: (licenseId: string) =>
      `/api/v1/mdm/license/${licenseId}/upgrade`,
    upgradeTenantLicense: (tenantId: string) =>
      `/api/v1/mdm/license/tenant/${tenantId}/upgrade`,
  },
  audit: {
    logs: "/api/v1/audit/trails",
    loginHistory: "/api/v1/audit/login-history",
    byUser: (userId: string) =>
      `/api/v1/audit/trails/user/${encodeURIComponent(userId)}`,
  },
} as const;

export const APP_API_ENDPOINTS = {
  auth: {
    initiateLogin: "/api/auth/initiate",
    login: "/api/auth/login",
    logout: "/api/auth/logout",
    context: "/api/auth/context",
    refresh: "/api/auth/refresh",
    internalProvision: "/api/auth/internal/provision",
    setPassword: "/api/auth/password/set",
    forgotPassword: "/api/auth/password/forgot",
    resetPassword: "/api/auth/password/reset",
    adminResetPassword: "/api/auth/password/admin-reset",
    passwordPolicy: "/api/auth/password/policy",
    verifyPasswordPolicy: "/api/auth/password/policy/verify",
    userAction: (
      userId: string,
      action: "activate" | "block" | "deactivate" | "unblock",
    ) => `/api/auth/users/${encodeURIComponent(userId)}/${action}`,
  },
  masterManagement: {
    users: "/api/master-management/users",
    userDetail: (userId: string) =>
      `/api/master-management/users/${encodeURIComponent(userId)}`,
    userLifecycle: (userId: string) =>
      `/api/master-management/mdm/users/${encodeURIComponent(userId)}/lifecycle`,
    userPasswordReset: (userId: string) =>
      `/api/master-management/mdm/users/${encodeURIComponent(userId)}/password-reset`,
    roles: "/api/master-management/roles",
    roleDetail: (roleId: string) =>
      `/api/master-management/mdm/roles/${encodeURIComponent(roleId)}`,
    groups: "/api/master-management/groups",
    groupDetail: (groupId: string) =>
      `/api/master-management/groups/${encodeURIComponent(groupId)}`,
    departments: "/api/master-management/departments",
    departmentDetail: (departmentId: string) =>
      `/api/master-management/departments/${encodeURIComponent(departmentId)}`,
    licenseBase: "/api/master-management/license",
    tenantLicense: (tenantId: string) =>
      `/api/master-management/license/tenant/${encodeURIComponent(tenantId)}`,
    licenseHistory: (tenantId: string) =>
      `/api/master-management/license/tenant/${encodeURIComponent(tenantId)}/history`,
    upgradeLicense: (licenseId: string) =>
      `/api/master-management/license/${encodeURIComponent(licenseId)}/upgrade`,
    upgradeTenantLicense: (tenantId: string) =>
      `/api/master-management/license/tenant/${encodeURIComponent(tenantId)}/upgrade`,
  },
  audit: {
    logs: "/api/audit/logs",
    detail: (auditId: string) =>
      `/api/audit/logs/${encodeURIComponent(auditId)}`,
    byEntity: "/api/audit/logs/entity",
    byUser: (userId: string) =>
      `/api/audit/logs/user/${encodeURIComponent(userId)}`,
    byAction: "/api/audit/logs/action",
    byTenant: (tenantId: string) =>
      `/api/audit/logs/tenant/${encodeURIComponent(tenantId)}`,
    countByAction: "/api/audit/logs/count/action",
  },
} as const;
