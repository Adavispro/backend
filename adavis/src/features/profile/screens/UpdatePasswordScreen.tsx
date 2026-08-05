"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError } from "@/api";
import { Button, Snackbar } from "@/components/ui";
import { verifyPasswordPolicy } from "@/features/auth/api";
import { useCurrentUser } from "@/features/auth/hooks/useCurrentUser";
import {
  getUser,
  getUsers,
  resetManagedUserPassword,
} from "@/features/master-management/user-management/api";
import type { CurrentUser } from "@/features/auth/api";
import type { User } from "@/features/master-management/user-management/api";
import {
  createEmptyUserFormValues,
  passwordFields,
  type UserFormFieldId,
  type UserFormValues,
  UserFormSection,
} from "@/features/master-management/user-management/components/UserFormSections";

const USERS_PAGE_SIZE = 100;

const normalizeIdentifier = (value: string | null | undefined) =>
  value?.trim().toLowerCase() ?? "";

const matchesCurrentUser = (user: User, currentUser: CurrentUser) => {
  const currentIdentifiers = [
    currentUser.userId,
    currentUser.username,
    currentUser.email,
  ]
    .map(normalizeIdentifier)
    .filter(Boolean);

  const userIdentifiers = [user.userId, user.username, user.email]
    .map(normalizeIdentifier)
    .filter(Boolean);

  return currentIdentifiers.some((identifier) =>
    userIdentifiers.includes(identifier),
  );
};

const findCurrentUserInPage = (
  users: User[],
  currentUser: CurrentUser,
) => users.find((user) => matchesCurrentUser(user, currentUser));

const resolveManagedUserId = async (currentUser: CurrentUser) => {
  const directUserId = currentUser.userId.trim();

  try {
    const user = await getUser(directUserId);
    return user.userId;
  } catch {
    // Some auth subjects differ from the MDM userId shown in the Users table.
  }

  const firstPage = await getUsers({ page: 0, size: USERS_PAGE_SIZE });
  const firstMatch = findCurrentUserInPage(firstPage.content, currentUser);
  if (firstMatch) return firstMatch.userId;

  for (let page = 1; page < firstPage.totalPages; page += 1) {
    const usersPage = await getUsers({ page, size: USERS_PAGE_SIZE });
    const match = findCurrentUserInPage(usersPage.content, currentUser);
    if (match) return match.userId;
  }

  return directUserId;
};

export default function UpdatePasswordScreen() {
  const router = useRouter();
  const currentUser = useCurrentUser();
  const [values, setValues] = useState<UserFormValues>(createEmptyUserFormValues);
  const [errors, setErrors] =
    useState<Partial<Record<UserFormFieldId, string>>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [policyErrors, setPolicyErrors] = useState<string[]>([]);
  const [notification, setNotification] = useState({
    message: "",
    variant: "error" as "error" | "success",
  });

  const passwordErrors = useMemo(() => {
    if (!values.password) return { password: "Password is required." };
    if (!values.confirmPassword) {
      return { confirmPassword: "Confirm password is required." };
    }
    if (values.password === values.confirmPassword) return {};
    return { confirmPassword: "Passwords do not match." };
  }, [values.confirmPassword, values.password]);

  const handleChange = (field: UserFormFieldId, value: string) => {
    setValues((previous) => ({ ...previous, [field]: value }));
    setErrors((previous) => ({ ...previous, [field]: undefined }));
    if (field === "password") setPolicyErrors([]);
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!currentUser?.userId) {
      setNotification({
        message: "The current user context is unavailable.",
        variant: "error",
      });
      return;
    }

    if (passwordErrors.password || passwordErrors.confirmPassword) {
      setErrors((previous) => ({
        ...previous,
        password: passwordErrors.password,
        confirmPassword: passwordErrors.confirmPassword,
      }));
      return;
    }

    setIsSubmitting(true);
    setPolicyErrors([]);
    setNotification({ message: "", variant: "error" });

    try {
      const policyResult = await verifyPasswordPolicy({
        password: values.password,
      });
      const nextPolicyErrors = policyResult.errors ?? policyResult.messages ?? [];
      const isPolicyValid =
        policyResult.valid ??
        policyResult.isValid ??
        nextPolicyErrors.length === 0;

      if (!isPolicyValid) {
        setPolicyErrors(nextPolicyErrors);
        setNotification({
          message: "Password does not meet policy requirements.",
          variant: "error",
        });
        return;
      }

      const managedUserId = await resolveManagedUserId(currentUser);
      await resetManagedUserPassword(managedUserId, values.password);

      setValues(createEmptyUserFormValues());
      setNotification({
        message: "Password updated successfully.",
        variant: "success",
      });
    } catch (error) {
      setNotification({
        message:
          error instanceof ApiError
            ? error.message
            : "Unable to update password. Please try again.",
        variant: "error",
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="grid gap-6">
      <UserFormSection
        title="Update Password"
        subtitle="Set a new password for your signed in account"
        fields={passwordFields}
        values={values}
        errors={{ ...errors, ...passwordErrors }}
        onChange={handleChange}
        columnsClassName="md:grid-cols-2"
      />

      {policyErrors.length > 0 ? (
        <section className="module-glass-panel rounded-lg border border-danger/15 px-6 py-5">
          <p className="text-[10px] font-semibold text-danger">
            Password must meet these requirements:
          </p>
          <ul className="mt-3 grid gap-1.5 text-[10px] font-medium text-text-secondary">
            {policyErrors.map((policyError) => (
              <li key={policyError} className="flex gap-2">
                <span
                  aria-hidden="true"
                  className="mt-[6px] h-1 w-1 rounded-full bg-danger"
                />
                <span>{policyError}</span>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      <div className="flex justify-end gap-3">
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
          onClick={() => router.back()}
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
        >
          Update Password
        </Button>
      </div>

      <Snackbar
        open={Boolean(notification.message)}
        variant={notification.variant}
        title={
          notification.variant === "success"
            ? "Password updated"
            : "Unable to update password"
        }
        message={notification.message}
        onClose={() => setNotification({ message: "", variant: "error" })}
      />
    </form>
  );
}
