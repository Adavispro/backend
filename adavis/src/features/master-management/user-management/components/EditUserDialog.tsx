"use client";

import { useMemo, useState } from "react";
import { ApiError } from "@/api";
import { Button, Dialog, Snackbar } from "@/components/ui";
import { useDepartments } from "../../department-management/hooks/useDepartments";
import { useTenants } from "../../tenant-management/hooks/useTenants";
import { buildUpdateUserRequest, updateUser } from "../api";
import type { User } from "../api/types";
import { updateUserFormSchema } from "../schemas";
import {
  createEmptyUserFormValues,
  accountFields,
  type UserFormFieldConfig,
  type UserFormFieldId,
  type UserFormValues,
  UserFormSection,
} from "./UserFormSections";

interface EditUserDialogProps {
  user: User | null;
  isOpen: boolean;
  onClose: () => void;
  onUpdated: (user: User) => void;
}

const editableAccountFields: UserFormFieldConfig[] = accountFields.filter(
  (field) => ["firstName", "lastName", "email", "phone"].includes(field.id),
);

const userToValues = (user: User | null): UserFormValues => {
  const values = createEmptyUserFormValues();
  if (!user) return values;

  return {
    ...values,
    tenantId: user.tenantId ?? "",
    department: user.departmentId ?? "",
    title: user.title ?? "",
    firstName: user.firstName ?? "",
    lastName: user.lastName ?? "",
    email: user.email ?? "",
    phone: user.phoneNumber ?? "",
  };
};

export default function EditUserDialog({
  user,
  isOpen,
  onClose,
  onUpdated,
}: EditUserDialogProps) {
  const { departments, isLoading: isLoadingDepartments } = useDepartments();
  const { tenants, isLoading: isLoadingTenants } = useTenants();
  const [values, setValues] = useState<UserFormValues>(() => userToValues(user));
  const [errors, setErrors] = useState<Partial<Record<UserFormFieldId, string>>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [notification, setNotification] = useState({
    message: "",
    variant: "error" as "error" | "success",
  });

  const handleChange = (field: UserFormFieldId, value: string) => {
    setValues((previous) => ({
      ...previous,
      [field]: value,
      ...(field === "tenantId" ? { department: "" } : {}),
    }));
    setErrors((previous) => ({ ...previous, [field]: undefined }));
  };

  const editableUserInformationFields = useMemo<UserFormFieldConfig[]>(
    () => [
      {
        id: "tenantId",
        label: "Tenant",
        placeholder: "Select Tenant",
        icon: accountFields[0].icon,
        options: [
          { value: "", label: "Select Tenant" },
          ...tenants.filter((tenant) => tenant.isActive).map((tenant) => ({ value: tenant.tenantId, label: `${tenant.companyName} (${tenant.tenantId})` })),
        ],
        required: true,
      },
      {
        id: "department",
        label: "Department",
        placeholder: "Select Department",
        icon: accountFields[0].icon,
        options: [
          { value: "", label: "Select Department" },
          ...departments.filter((department) => department.isActive && department.tenantId === values.tenantId).map((department) => ({ value: department.departmentId, label: department.departmentName || department.name })),
        ],
        required: true,
      },
      {
        id: "title",
        label: "Title",
        placeholder: "Enter Job Title",
        icon: accountFields[0].icon,
        required: true,
      },
    ],
    [departments, tenants, values.tenantId],
  );

  const handleClose = () => {
    if (isSubmitting) return;
    onClose();
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!user) return;

    const payload = {
      tenantId: values.tenantId,
      email: values.email,
      firstName: values.firstName,
      lastName: values.lastName,
      phoneNumber: values.phone || null,
      title: values.title,
      departmentId: values.department,
      isActive: user.isActive,
    };
    const parsedValues = updateUserFormSchema.safeParse(payload);

    if (!parsedValues.success) {
      const fieldErrors = parsedValues.error.flatten().fieldErrors;
      setErrors((previous) => ({
        ...previous,
        tenantId: fieldErrors.tenantId?.[0],
        email: fieldErrors.email?.[0],
        firstName: fieldErrors.firstName?.[0],
        lastName: fieldErrors.lastName?.[0],
        phone: fieldErrors.phoneNumber?.[0],
        department: fieldErrors.departmentId?.[0],
        title: fieldErrors.title?.[0],
      }));
      return;
    }

    setIsSubmitting(true);
    setNotification({ message: "", variant: "error" });

    try {
      const requestPayload = buildUpdateUserRequest(user, parsedValues.data);
      const updatedUser = await updateUser(user.userId, requestPayload);
      onUpdated(updatedUser);
      setNotification({
        message: "User details updated successfully.",
        variant: "success",
      });
      window.setTimeout(onClose, 450);
    } catch (error) {
      setNotification({
        message:
          error instanceof ApiError
            ? error.message
            : "Unable to update user. Please try again.",
        variant: "error",
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog
      isOpen={isOpen}
      title="Edit User Details"
      onClose={handleClose}
      widthClassName="max-w-[720px]"
    >
      <form onSubmit={handleSubmit}>
        <div className="grid gap-7 px-6 py-5">
          <UserFormSection
            title="Account Details"
            fields={editableAccountFields}
            values={values}
            errors={errors}
            onChange={handleChange}
            columnsClassName="md:grid-cols-2"
            compact
          />

          <UserFormSection
            title="Enter User Information"
            fields={editableUserInformationFields}
            values={values}
            errors={errors}
            onChange={handleChange}
            columnsClassName="md:grid-cols-2"
            compact
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
            isLoading={isSubmitting || isLoadingDepartments || isLoadingTenants}
          >
            Save Changes
          </Button>
        </div>
      </form>

      <Snackbar
        open={Boolean(notification.message)}
        variant={notification.variant}
        title={notification.variant === "success" ? "User updated" : "Unable to update user"}
        message={notification.message}
        onClose={() => setNotification({ message: "", variant: "error" })}
      />
    </Dialog>
  );
}
