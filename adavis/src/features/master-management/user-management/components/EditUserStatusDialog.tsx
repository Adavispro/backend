"use client";

import { useState } from "react";
import { ApiError } from "@/api";
import { Button, Dialog, Snackbar } from "@/components/ui";
import { changeUserLifecycle } from "../api";
import type { User } from "../api/types";
import {
  createEmptyUserFormValues,
  type UserFormFieldId,
  type UserFormValues,
  UserFormSection,
  userStatusField,
} from "./UserFormSections";
import {
  getUserStatusLabel,
  parseEditableStatus,
  resolveStatusAction,
} from "./userStatus";

interface EditUserStatusDialogProps {
  user: User | null;
  isOpen: boolean;
  onClose: () => void;
  onUpdated: (user: User) => void;
}

const userToValues = (user: User | null): UserFormValues => ({
  ...createEmptyUserFormValues(),
  status: user ? getUserStatusLabel(user) : "",
});

export default function EditUserStatusDialog({
  user,
  isOpen,
  onClose,
  onUpdated,
}: EditUserStatusDialogProps) {
  const [values, setValues] = useState<UserFormValues>(() => userToValues(user));
  const [errors, setErrors] = useState<Partial<Record<UserFormFieldId, string>>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [notification, setNotification] = useState({
    message: "",
    variant: "error" as "error" | "success",
  });

  const handleChange = (field: UserFormFieldId, value: string) => {
    setValues((previous) => ({ ...previous, [field]: value }));
    setErrors((previous) => ({ ...previous, [field]: undefined }));
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!user) return;

    const selectedStatus = parseEditableStatus(values.status);
    if (!selectedStatus) {
      setErrors({ status: "User status is required." });
      return;
    }

    const currentStatus = getUserStatusLabel(user);
    if (selectedStatus === currentStatus) {
      setNotification({
        message: "User status is already up to date.",
        variant: "success",
      });
      window.setTimeout(onClose, 450);
      return;
    }

    setIsSubmitting(true);
    setNotification({ message: "", variant: "error" });

    try {
      const updatedUser = await changeUserLifecycle(
        user.userId,
        resolveStatusAction(user, selectedStatus),
      );
      onUpdated(updatedUser);
      setNotification({
        message: "User status updated successfully.",
        variant: "success",
      });
      window.setTimeout(onClose, 450);
    } catch (error) {
      setNotification({
        message:
          error instanceof ApiError
            ? error.message
            : "Unable to update user status. Please try again.",
        variant: "error",
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog
      isOpen={isOpen}
      title="Edit User Status"
      onClose={isSubmitting ? () => undefined : onClose}
      widthClassName="max-w-[520px]"
    >
      <form onSubmit={handleSubmit}>
        <div className="px-6 py-5">
          <UserFormSection
            title="Change Status"
            fields={[userStatusField]}
            values={values}
            errors={errors}
            onChange={handleChange}
            columnsClassName="md:grid-cols-1"
            compact
          />
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
            Save Status
          </Button>
        </div>
      </form>

      <Snackbar
        open={Boolean(notification.message)}
        variant={notification.variant}
        title={
          notification.variant === "success"
            ? "Status updated"
            : "Unable to update status"
        }
        message={notification.message}
        onClose={() => setNotification({ message: "", variant: "error" })}
      />
    </Dialog>
  );
}
