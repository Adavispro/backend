"use client";

import { useMemo, useState } from "react";
import { ApiError } from "@/api";
import { Button, Dialog, Snackbar } from "@/components/ui";
import { useLoginContext } from "@/features/auth/hooks/useCurrentUser";
import { useMasterLookups } from "../../lookups/hooks";
import { useTenants } from "../../tenant-management/hooks/useTenants";
import { mapRoleToGroup, mapUserToGroup, updateGroup } from "../api";
import type { Group } from "../api/types";
import { updateGroupFormSchema } from "../schemas";
import {
  createEmptyUserGroupFormValues,
  createEditUserGroupFields,
  type UserGroupFormFieldId,
  type UserGroupFormValues,
  UserGroupFormFields,
} from "./UserGroupFormFields";

interface EditUserGroupDialogProps {
  group: Group;
  assignments?: {
    roleIds: string[];
    userIds: string[];
  };
  onClose: () => void;
  onUpdated: (
    group: Group,
    assignmentUpdates?: { roleIds?: string[]; userIds?: string[] },
  ) => void;
}

const groupToValues = (
  group: Group,
  assignments?: EditUserGroupDialogProps["assignments"],
): UserGroupFormValues => ({
  ...createEmptyUserGroupFormValues(),
  tenantId: group.tenantId ?? "",
  groupCode: group.groupCode ?? "",
  name: group.groupName || group.name,
  description: group.description ?? "",
  assignedUsers: assignments?.userIds ?? [],
  assignedRoles: assignments?.roleIds ?? [],
});

export default function EditUserGroupDialog({
  assignments,
  group,
  onClose,
  onUpdated,
}: EditUserGroupDialogProps) {
  const { isLoading: isLoadingLookups, options } = useMasterLookups();
  const loginContext = useLoginContext();
  const { tenants, isLoading: isLoadingTenants } = useTenants();
  const [values, setValues] = useState<UserGroupFormValues>(() =>
    groupToValues(group, assignments),
  );
  const [errors, setErrors] = useState<
    Partial<Record<UserGroupFormFieldId, string>>
  >({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [notification, setNotification] = useState({
    message: "",
    variant: "error" as "error" | "success",
  });

  const handleChange = (field: UserGroupFormFieldId, value: string | string[]) => {
    setValues((previous) => ({ ...previous, [field]: value }));
    setErrors((previous) => ({ ...previous, [field]: undefined }));
  };

  const fields = useMemo(
    () =>
      createEditUserGroupFields({
        roles: options.roles,
        tenants: tenants.filter((tenant) => tenant.isActive).map((tenant) => ({ value: tenant.tenantId, label: `${tenant.companyName} (${tenant.tenantId})` })),
        users: options.users,
      }),
    [options.roles, options.users, tenants],
  );

  const handleClose = () => {
    if (isSubmitting) return;
    onClose();
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const parsedValues = updateGroupFormSchema.safeParse({
      tenantId: values.tenantId,
      groupCode: values.groupCode,
      name: values.name,
      description: values.description,
      assignedUsers: values.assignedUsers,
      assignedRoles: values.assignedRoles,
    });

    if (!parsedValues.success) {
      const fieldErrors = parsedValues.error.flatten().fieldErrors;
      setErrors({
        tenantId: fieldErrors.tenantId?.[0],
        groupCode: fieldErrors.groupCode?.[0],
        name: fieldErrors.name?.[0],
        description: fieldErrors.description?.[0],
      });
      return;
    }

    setIsSubmitting(true);
    setNotification({ message: "", variant: "error" });

    try {
      const updatedGroup = await updateGroup(group.groupId, {
        tenantId: parsedValues.data.tenantId,
        groupCode: parsedValues.data.groupCode,
        groupName: parsedValues.data.name,
        name: parsedValues.data.name,
        description: parsedValues.data.description,
        isActive: group.isActive,
      });
      const assignedUserIds = assignments?.userIds ?? [];
      const assignedRoleIds = assignments?.roleIds ?? [];
      const nextUserIds = parsedValues.data.assignedUsers.filter(
        (userId) => userId && !assignedUserIds.includes(userId),
      );
      const nextRoleIds = parsedValues.data.assignedRoles.filter(
        (roleId) => roleId && !assignedRoleIds.includes(roleId),
      );
      if (nextUserIds.length > 0 || nextRoleIds.length > 0) {
        const actorUserId = loginContext?.user.userId;
        if (!actorUserId) throw new ApiError({ status: 400, message: "The current administrator context is unavailable." });
        await Promise.all([
          ...nextUserIds.map((userId) =>
            mapUserToGroup(group.groupId, userId, actorUserId),
          ),
          ...nextRoleIds.map((roleId) =>
            mapRoleToGroup(group.groupId, roleId, actorUserId),
          ),
        ]);
      }
      onUpdated(updatedGroup, {
        roleIds: nextRoleIds,
        userIds: nextUserIds,
      });
      setNotification({
        message: "User group updated successfully.",
        variant: "success",
      });
      window.setTimeout(onClose, 450);
    } catch (error) {
      setNotification({
        message:
          error instanceof ApiError
            ? error.message
            : "Unable to update user group. Please try again.",
        variant: "error",
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog
      isOpen
      title="Edit User Group Details"
      onClose={handleClose}
      widthClassName="max-w-[620px]"
    >
      <form onSubmit={handleSubmit}>
        <div className="grid gap-6 px-6 py-5 md:grid-cols-2">
          <UserGroupFormFields
            fields={fields}
            values={values}
            errors={errors}
            onChange={handleChange}
          />
        </div>

        <div className="flex justify-end gap-3 px-6 pb-6">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            rounded="rounded-[4px]"
            textSize="text-[10px]"
            paddingX="px-5"
            paddingY="py-0"
            className="h-9 border-primary/35 bg-white/35 !text-primary hover:bg-white/65"
            disabled={isSubmitting}
            onClick={handleClose}
          >
            Discard Changes
          </Button>
          <Button
            type="submit"
            size="sm"
            rounded="rounded-[4px]"
            textSize="text-[10px]"
            paddingX="px-6"
            paddingY="py-0"
            className="h-9 shadow-[0_8px_18px_rgba(7,92,175,0.18)]"
            isLoading={isSubmitting}
            disabled={isLoadingLookups || isLoadingTenants}
          >
            Save Changes
          </Button>
        </div>
      </form>

      <Snackbar
        open={Boolean(notification.message)}
        variant={notification.variant}
        title={
          notification.variant === "success"
            ? "User group updated"
            : "Unable to update user group"
        }
        message={notification.message}
        onClose={() => setNotification({ message: "", variant: "error" })}
      />
    </Dialog>
  );
}
