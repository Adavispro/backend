"use client";

import { useMemo, useState } from "react";
import { ApiError } from "@/api";
import { Button, Dialog, Snackbar } from "@/components/ui";
import {
  verifyPasswordPolicy,
} from "@/features/auth/api";
import { resetManagedUserPassword } from "../api";
import type { User } from "../api/types";
import {
  createEmptyUserFormValues,
  passwordFields,
  type UserFormFieldConfig,
  type UserFormFieldId,
  type UserFormValues,
  UserFormSection,
} from "./UserFormSections";

interface UpdateUserPasswordDialogProps {
  user: User | null;
  isOpen: boolean;
  onClose: () => void;
}

const visiblePasswordFields: UserFormFieldConfig[] = passwordFields.map(
  (field) => ({ ...field, type: "text" }),
);

export default function UpdateUserPasswordDialog({
  user,
  isOpen,
  onClose,
}: UpdateUserPasswordDialogProps) {
  const [values, setValues] = useState<UserFormValues>(createEmptyUserFormValues);
  const [errors, setErrors] = useState<Partial<Record<UserFormFieldId, string>>>({});
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
    if (!user) return;

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
        policyResult.valid ?? policyResult.isValid ?? nextPolicyErrors.length === 0;

      if (!isPolicyValid) {
        setPolicyErrors(nextPolicyErrors);
        setNotification({
          message: "Password does not meet policy requirements.",
          variant: "error",
        });
        return;
      }

      await resetManagedUserPassword(user.userId, values.password);

      setValues(createEmptyUserFormValues());
      setNotification({
        message: "User password updated successfully.",
        variant: "success",
      });
      window.setTimeout(onClose, 450);
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
    <Dialog
      isOpen={isOpen}
      title="Update Password"
      onClose={isSubmitting ? () => undefined : onClose}
      widthClassName="max-w-[640px]"
    >
      <form onSubmit={handleSubmit}>
        <div className="px-6 py-5">
          <UserFormSection
            title="Update or Reset Password"
            fields={visiblePasswordFields}
            values={values}
            errors={{ ...errors, ...passwordErrors }}
            onChange={handleChange}
            columnsClassName="md:grid-cols-2"
            compact
          />
          {policyErrors.length > 0 ? (
            <div className="mt-4 rounded-md border border-danger/15 bg-danger/5 px-4 py-3">
              <p className="text-[10px] font-semibold text-danger">
                Password must be changed to meet these requirements:
              </p>
              <ul className="mt-2 grid gap-1.5 text-[10px] font-medium text-text-secondary">
                {policyErrors.map((policyError) => (
                  <li key={policyError} className="flex gap-2">
                    <span aria-hidden="true" className="mt-[6px] h-1 w-1 rounded-full bg-danger" />
                    <span>{policyError}</span>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </div>

        <div className="flex justify-end gap-3 border-t border-[#E6E6E6] px-6 py-5">
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
            onClick={onClose}
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
      </form>

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
    </Dialog>
  );
}
