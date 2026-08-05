"use client";

import { Snackbar } from "@/components/ui";
import UserDetailDashboard from "../components/UserDetailDashboard";
import type { UserRow } from "../data/users";
import { useUser } from "../hooks/useUser";
import { useUserAuditActivity } from "../hooks/useUserAuditActivity";

const toUserRow = (user: NonNullable<ReturnType<typeof useUser>["user"]>): UserRow => ({
  id: user.userId,
  name:
    [user.firstName, user.lastName].filter(Boolean).join(" ") ||
    user.username ||
    user.userId,
  email: user.email,
  department: user.departmentId || "-",
  plant: "Plant 01",
  status: user.isActive && !user.isDeleted ? "Active" : "Blocked",
});

export default function UserDetailScreen({ userId }: { userId: string }) {
  const { clearError, errorMessage, isLoading, user } = useUser(userId);
  const {
    activityRows,
    clearError: clearActivityError,
    errorMessage: activityErrorMessage,
    isLoading: isActivityLoading,
    metrics,
  } = useUserAuditActivity(userId);

  if (isLoading) {
    return (
      <section className="module-glass-panel rounded-lg p-5 text-[12px] font-medium text-text-secondary">
        Loading user details...
      </section>
    );
  }

  return (
    <>
      {user ? (
        <UserDetailDashboard
          activityRows={activityRows}
          isActivityLoading={isActivityLoading}
          metrics={metrics}
          user={toUserRow(user)}
        />
      ) : (
        <section className="module-glass-panel rounded-lg p-5 text-[12px] font-medium text-text-secondary">
          User details are unavailable.
        </section>
      )}

      <Snackbar
        open={Boolean(errorMessage)}
        variant="error"
        title="Unable to load user"
        message={errorMessage}
        onClose={clearError}
      />
      <Snackbar
        open={Boolean(activityErrorMessage)}
        variant="error"
        title="Unable to load user activity"
        message={activityErrorMessage}
        onClose={clearActivityError}
      />
    </>
  );
}
