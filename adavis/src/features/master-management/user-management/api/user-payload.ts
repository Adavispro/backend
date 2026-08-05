import { updateUserRequestSchema } from "../schemas";
import type { UpdateUserFormValues, UpdateUserRequest, User } from "./types";

const fallbackLifecycleStatus = (user: User) => {
  if (user.isBlocked) return "BLOCKED";
  if (!user.isActive) return "DEACTIVATED";
  if (user.lifecycleStatus?.trim()) return user.lifecycleStatus.trim();
  return user.isActive ? "ACTIVE" : "DEACTIVATED";
};

export const buildUpdateUserRequest = (
  user: User,
  values: UpdateUserFormValues,
): UpdateUserRequest => {
  const username = user.username?.trim();
  const title = values.title.trim();

  return updateUserRequestSchema.parse({
    tenantId: values.tenantId.trim(),
    email: values.email.trim(),
    firstName: values.firstName.trim(),
    lastName: values.lastName.trim(),
    phoneNumber: values.phoneNumber?.trim() || null,
    title,
    departmentId: values.departmentId.trim(),
    isActive: user.isActive,
    userId: user.userId,
    ...(username ? { username } : {}),
    userTrackId: user.userTrackId ?? null,
    userType: user.userType?.trim() || "INTERNAL_EMPLOYEE",
    lifecycleStatus: fallbackLifecycleStatus(user),
    empId: user.empId?.trim() || user.userId,
    designation: user.designation ?? title,
    isExternal: user.isExternal ?? false,
    isBlocked: user.isBlocked,
    isDeleted: user.isDeleted,
    supportingDocumentIds: [],
    supportingDocuments: [],
    supportingDocumentType: null,
    reason: "User profile updated by an administrator",
  });
};
