"use client";

import Image from "next/image";
import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError } from "@/api";
import { Button, Snackbar } from "@/components/ui";
import { ROUTES } from "@/config/routes";
import { useLoginContext } from "@/features/auth/hooks/useCurrentUser";
import clearIcon from "@/assets/icons/clear-icon.svg";
import { useMasterLookups } from "../../lookups/hooks";
import { useTenants } from "../../tenant-management/hooks/useTenants";
import { createGroup, mapRoleToGroup, mapUserToGroup } from "../api";
import { createGroupFormSchema } from "../schemas";
import {
  createUserGroupFields,
  createEmptyUserGroupFormValues,
  type UserGroupFormFieldId,
  type UserGroupFormValues,
  UserGroupFormFields,
} from "./UserGroupFormFields";

export default function CreateUserGroupForm() {
  const router = useRouter();
  const loginContext = useLoginContext();
  const { errorMessage: lookupErrorMessage, isLoading: isLoadingLookups, options } =
    useMasterLookups();
  const { tenants, isLoading: isLoadingTenants } = useTenants();
  const [values, setValues] = useState<UserGroupFormValues>(
    createEmptyUserGroupFormValues,
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

  const clearForm = () => {
    setValues(createEmptyUserGroupFormValues());
    setErrors({});
  };

  const fields = useMemo(
    () =>
      createUserGroupFields({
        roles: options.roles,
        tenants: tenants.filter((tenant) => tenant.isActive).map((tenant) => ({ value: tenant.tenantId, label: `${tenant.companyName} (${tenant.tenantId})` })),
        users: options.users,
      }),
    [options.roles, options.users, tenants],
  );

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const parsedValues = createGroupFormSchema.safeParse(values);

    if (!parsedValues.success) {
      const fieldErrors = parsedValues.error.flatten().fieldErrors;
      setErrors(
        Object.fromEntries(
          Object.entries(fieldErrors).map(([field, messages]) => [
            field,
            messages?.[0],
          ]),
        ),
      );
      return;
    }

    setIsSubmitting(true);
    setNotification({ message: "", variant: "error" });

    try {
      const group = await createGroup({
        tenantId: parsedValues.data.tenantId,
        groupCode: parsedValues.data.groupCode,
        name: parsedValues.data.name,
        description: parsedValues.data.description,
        isActive: true,
      });
      const actorUserId = loginContext?.user.userId;
      const assignedUsers = parsedValues.data.assignedUsers.filter(Boolean);
      const assignedRoles = parsedValues.data.assignedRoles.filter(Boolean);
      if ((assignedUsers.length > 0 || assignedRoles.length > 0) && !actorUserId) {
        throw new ApiError({ status: 400, message: "The current administrator context is unavailable." });
      }
      await Promise.all([
        ...assignedUsers.map((userId) =>
          actorUserId
            ? mapUserToGroup(group.groupId, userId, actorUserId)
            : Promise.resolve(),
        ),
        ...assignedRoles.map((roleId) =>
          actorUserId
            ? mapRoleToGroup(group.groupId, roleId, actorUserId)
            : Promise.resolve(),
        ),
      ]);
      setNotification({
        message: "User group created successfully.",
        variant: "success",
      });
      window.setTimeout(() => {
        router.push(ROUTES.masterUserGroups);
        router.refresh();
      }, 700);
    } catch (error) {
      setNotification({
        message:
          error instanceof ApiError
            ? error.message
            : "Unable to create user group. Please try again.",
        variant: "error",
      });
      setIsSubmitting(false);
    }
  };

  return (
    <form
      className="module-glass-panel overflow-visible rounded-lg shadow-[0_12px_24px_rgba(35,50,70,0.1)]"
      onSubmit={handleSubmit}
    >
      <div className="border-b border-[#E3E9F0] px-6 py-5">
        <h2 className="text-[14px] font-semibold text-text-heading">
          Enter Group Details
        </h2>
        <p className="mt-2 text-[9px] text-text-secondary">
          Fill out the required details to create a user group
        </p>
      </div>

      <div className="grid gap-6 px-6 py-5 md:grid-cols-2">
        <UserGroupFormFields
          fields={fields}
          values={values}
          errors={errors}
          onChange={handleChange}
        />

        <div className="flex justify-end gap-3 md:col-span-2">
          <Button
            type="reset"
            onClick={clearForm}
            variant="ghost"
            size="sm"
            prefixIcon={<Image src={clearIcon} alt="" aria-hidden="true" className="h-3.5 w-3.5" />}
            rounded="rounded-[4px]"
            textSize="text-[10px]"
            paddingX="px-4"
            paddingY="py-0"
            className="h-9 border-primary/35 bg-white/35 !text-primary hover:bg-white/65"
          >
            Clear All
          </Button>
          <Button
            type="submit"
            isLoading={isSubmitting || isLoadingLookups || isLoadingTenants}
            size="sm"
            rounded="rounded-[4px]"
            textSize="text-[10px]"
            paddingX="px-5"
            paddingY="py-0"
            className="h-9 shadow-[0_8px_18px_rgba(7,92,175,0.18)]"
          >
            Save User Group
          </Button>
        </div>
      </div>

      <Snackbar
        open={Boolean(notification.message || lookupErrorMessage)}
        variant={notification.variant}
        title={
          notification.variant === "success"
            ? "User group created"
            : "Unable to create user group"
        }
        message={notification.message || lookupErrorMessage}
        onClose={() => setNotification({ message: "", variant: "error" })}
      />
    </form>
  );
}
