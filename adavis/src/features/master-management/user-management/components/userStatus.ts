import type { UserLifecycleAction } from "../api";
import type { User } from "../api/types";

export type EditableUserStatus = "Active" | "Blocked" | "Deactivated";

export const getUserStatusLabel = (user: User): EditableUserStatus => {
  const lifecycleStatus = user.lifecycleStatus?.toUpperCase();
  if (user.isBlocked) return "Blocked";
  if (lifecycleStatus === "DEACTIVATED" || lifecycleStatus === "INACTIVE") {
    return "Deactivated";
  }
  return user.isActive ? "Active" : "Deactivated";
};

export const resolveStatusAction = (
  user: User,
  status: EditableUserStatus,
): UserLifecycleAction => {
  if (status === "Active") return user.isBlocked ? "unblock" : "reactivate";
  return status === "Blocked" ? "block" : "deactivate";
};

export const applyStatusToUser = (
  user: User,
  status: EditableUserStatus,
): User => ({
  ...user,
  isActive: status === "Active",
  isBlocked: status === "Blocked",
  lifecycleStatus: status.toUpperCase(),
  updatedAt: new Date().toISOString(),
});

export const parseEditableStatus = (
  status: string,
): EditableUserStatus | null => {
  if (status === "Active" || status === "Blocked" || status === "Deactivated") {
    return status;
  }

  return null;
};
