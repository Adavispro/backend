"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError } from "@/api";
import { Button, Snackbar } from "@/components/ui";
import {
  invalidateLoginContext,
  useCurrentUser,
} from "@/features/auth/hooks/useCurrentUser";
import {
  buildUpdateUserRequest,
  getUser,
  updateUser,
} from "@/features/master-management/user-management/api";
import type {
  UpdateUserFormValues,
  User,
} from "@/features/master-management/user-management/api";
import {
  accountFields,
  type UserFormFieldConfig,
  type UserFormFieldId,
  type UserFormValues,
  UserFormSection,
} from "@/features/master-management/user-management/components/UserFormSections";
import { updateUserFormSchema } from "@/features/master-management/user-management/schemas";

const editableProfileFields: UserFormFieldConfig[] = [
  ...accountFields.filter((field) =>
    ["firstName", "lastName", "email", "phone"].includes(field.id),
  ),
  {
    id: "title",
    label: "Title",
    placeholder: "Enter Job Title",
    icon: accountFields[0].icon,
    required: true,
  },
];

const userToValues = (user: User): UpdateUserFormValues => ({
  tenantId: user.tenantId ?? "",
  email: user.email,
  firstName: user.firstName ?? "",
  lastName: user.lastName ?? "",
  phoneNumber: user.phoneNumber ?? null,
  title: user.title ?? "",
  departmentId: user.departmentId ?? "",
  isActive: user.isActive,
});

const toUserFormValues = (
  values: UpdateUserFormValues,
): UserFormValues => ({
  userId: "",
  tenantId: values.tenantId,
  username: "",
  department: values.departmentId,
  title: values.title,
  userType: "",
  lifecycleStatus: "",
  empId: "",
  reason: "",
  firstName: values.firstName,
  lastName: values.lastName,
  email: values.email,
  phone: values.phoneNumber ?? "",
  password: "",
  confirmPassword: "",
  userGroup: [],
  status: "",
});

const toUserFormErrors = (
  errors: Partial<Record<keyof UpdateUserFormValues, string>>,
): Partial<Record<UserFormFieldId, string>> => ({
  firstName: errors.firstName,
  lastName: errors.lastName,
  email: errors.email,
  phone: errors.phoneNumber,
  title: errors.title,
});

export default function ProfileEditScreen() {
  const router = useRouter();
  const currentUser = useCurrentUser();
  const [user, setUser] = useState<User | null>(null);
  const [values, setValues] = useState<UpdateUserFormValues | null>(null);
  const [errors, setErrors] = useState<
    Partial<Record<keyof UpdateUserFormValues, string>>
  >({});
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [notification, setNotification] = useState({
    message: "",
    variant: "error" as "error" | "success",
  });

  useEffect(() => {
    if (!currentUser?.userId) return;

    const controller = new AbortController();

    void getUser(currentUser.userId, controller.signal)
      .then((loadedUser) => {
        setUser(loadedUser);
        setValues(userToValues(loadedUser));
      })
      .catch((error) => {
        if (error instanceof DOMException && error.name === "AbortError") return;
        setNotification({
          message:
            error instanceof ApiError
              ? error.message
              : "Unable to load your profile. Please try again.",
          variant: "error",
        });
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });

    return () => controller.abort();
  }, [currentUser?.userId]);

  const handleProfileChange = (field: UserFormFieldId, value: string) => {
    const fieldMap: Partial<Record<UserFormFieldId, keyof UpdateUserFormValues>> =
      {
        email: "email",
        firstName: "firstName",
        lastName: "lastName",
        phone: "phoneNumber",
        title: "title",
      };
    const updateField = fieldMap[field];
    if (!updateField) return;

    setValues((previous) =>
      previous ? { ...previous, [updateField]: value } : previous,
    );
    setErrors((previous) => ({ ...previous, [updateField]: undefined }));
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!values || !user) return;

    const parsedValues = updateUserFormSchema.safeParse(values);
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
      const requestPayload = buildUpdateUserRequest(user, parsedValues.data);
      const updatedUser = await updateUser(user.userId, requestPayload);
      setUser(updatedUser);
      setValues(userToValues(updatedUser));
      setNotification({
        message: "Profile updated successfully.",
        variant: "success",
      });
      invalidateLoginContext();
      router.refresh();
    } catch (error) {
      setNotification({
        message:
          error instanceof ApiError
            ? error.message
            : "Unable to update your profile. Please try again.",
        variant: "error",
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return (
      <section className="module-glass-panel rounded-lg px-6 py-10 text-center text-xs text-text-secondary">
        Loading your profile...
      </section>
    );
  }

  if (!user || !values) {
    return (
      <section className="module-glass-panel rounded-lg px-6 py-10 text-center text-xs text-text-secondary">
        Your profile details could not be loaded.
        <Snackbar
          open={Boolean(notification.message)}
          variant="error"
          title="Unable to load profile"
          message={notification.message}
          onClose={() => setNotification({ message: "", variant: "error" })}
        />
      </section>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="grid gap-6">
      <UserFormSection
        title="Edit Profile Details"
        subtitle="Update your personal account details"
        fields={editableProfileFields}
        values={toUserFormValues(values)}
        errors={toUserFormErrors(errors)}
        onChange={handleProfileChange}
        columnsClassName="md:grid-cols-2 xl:grid-cols-3"
      />

      <section className="module-glass-panel rounded-lg px-6 py-5">
        <h2 className="text-[14px] font-semibold text-text-heading">
          Account Context
        </h2>
        <div className="mt-4 grid gap-3 text-[11px] font-semibold text-text-heading md:grid-cols-3">
          <ProfileReadOnlyDetail label="User ID" value={user.userId} />
          <ProfileReadOnlyDetail label="Tenant" value={user.tenantId ?? "-"} />
          <ProfileReadOnlyDetail
            label="Department"
            value={user.departmentId ?? "-"}
          />
        </div>
      </section>

      <div className="flex justify-end gap-3">
        <Button
          type="button"
          variant="ghost"
          size="sm"
          rounded="rounded-[4px]"
          onClick={() => router.back()}
          disabled={isSubmitting}
        >
          Cancel
        </Button>
        <Button
          type="submit"
          size="sm"
          rounded="rounded-[4px]"
          isLoading={isSubmitting}
          className="shadow-[0_8px_18px_rgba(7,92,175,0.18)]"
        >
          Save Profile
        </Button>
      </div>

      <Snackbar
        open={Boolean(notification.message)}
        variant={notification.variant}
        title={
          notification.variant === "success"
            ? "Profile updated"
            : "Unable to update profile"
        }
        message={notification.message}
        onClose={() => setNotification({ message: "", variant: "error" })}
      />
    </form>
  );
}

function ProfileReadOnlyDetail({
  label,
  value,
}: {
  label: string;
  value: string;
}) {
  return (
    <div className="rounded-[4px] border border-[#D8E2ED] bg-white/45 px-3 py-2">
      <p className="text-[9px] font-semibold uppercase tracking-[0.08em] text-text-secondary">
        {label}
      </p>
      <p className="mt-1 truncate">{value}</p>
    </div>
  );
}
