"use client";

import Image from "next/image";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError } from "@/api";
import { Button, Snackbar, TextField } from "@/components/ui";
import { ROUTES } from "@/config/routes";
import emailIcon from "@/assets/icons/email.svg";
import plantIcon from "@/assets/icons/plant-icon.svg";
import phoneIcon from "@/assets/icons/phone.svg";
import userIcon from "@/assets/icons/user.svg";
import { buildUpdateUserRequest, getUser, updateUser } from "../api";
import type { UpdateUserFormValues, User } from "../api/types";
import { updateUserFormSchema } from "../schemas";

interface EditUserFormProps {
  userId: string;
}

export default function EditUserForm({ userId }: EditUserFormProps) {
  const router = useRouter();
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
    const controller = new AbortController();

    void getUser(userId, controller.signal)
      .then((loadedUser) => {
        setUser(loadedUser);
        setValues({
          tenantId: loadedUser.tenantId ?? "",
          email: loadedUser.email,
          firstName: loadedUser.firstName ?? "",
          lastName: loadedUser.lastName ?? "",
          phoneNumber: loadedUser.phoneNumber ?? null,
          title: loadedUser.title ?? "",
          departmentId: loadedUser.departmentId ?? "",
          isActive: loadedUser.isActive,
        });
      })
      .catch((error) => {
        if (error instanceof DOMException && error.name === "AbortError") return;
        setNotification({
          message:
            error instanceof ApiError
              ? error.message
              : "Unable to load user. Please try again.",
          variant: "error",
        });
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });

    return () => controller.abort();
  }, [userId]);

  const handleChange = (field: keyof UpdateUserFormValues, value: string) => {
    setValues((previous) =>
      previous ? { ...previous, [field]: value } : previous,
    );
    setErrors((previous) => ({ ...previous, [field]: undefined }));
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
      await updateUser(user.userId, requestPayload);
      setNotification({
        message: "User updated successfully.",
        variant: "success",
      });
      window.setTimeout(() => {
        router.push(ROUTES.masterUsers);
        router.refresh();
      }, 700);
    } catch (error) {
      setNotification({
        message:
          error instanceof ApiError
            ? error.message
            : "Unable to update user. Please try again.",
        variant: "error",
      });
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return (
      <section className="module-glass-panel rounded-lg px-6 py-10 text-center text-xs text-text-secondary">
        Loading user details...
      </section>
    );
  }

  if (!user || !values) {
    return (
      <section className="module-glass-panel rounded-lg px-6 py-10 text-center text-xs text-text-secondary">
        User details could not be loaded.
        <Snackbar
          open={Boolean(notification.message)}
          variant="error"
          title="Unable to load user"
          message={notification.message}
          onClose={() => setNotification({ message: "", variant: "error" })}
        />
      </section>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="grid gap-6">
      <section className="module-glass-panel overflow-hidden rounded-lg shadow-[0_12px_24px_rgba(35,50,70,0.1)]">
        <div className="border-b border-[#E3E9F0] px-6 py-5">
          <h2 className="text-[14px] font-semibold text-text-heading">
            Update User Information
          </h2>
          <p className="mt-2 text-[9px] text-text-secondary">
            Review and update the user&apos;s profile details
          </p>
        </div>

        <div className="grid gap-x-10 gap-y-6 px-6 py-5 md:grid-cols-2 xl:grid-cols-3">
          <TextField
            label="Email Address"
            required
            prefixIcon={<Image src={emailIcon} alt="" className="h-3 w-3" />}
            value={values.email}
            onChange={(event) => handleChange("email", event.target.value)}
            error={errors.email}
            containerClassName="module-glass-control !rounded-[4px]"
          />
          <TextField
            label="First Name"
            required
            prefixIcon={<Image src={userIcon} alt="" className="h-3 w-3" />}
            value={values.firstName}
            onChange={(event) => handleChange("firstName", event.target.value)}
            error={errors.firstName}
            containerClassName="module-glass-control !rounded-[4px]"
          />
          <TextField
            label="Last Name"
            required
            prefixIcon={<Image src={userIcon} alt="" className="h-3 w-3" />}
            value={values.lastName}
            onChange={(event) => handleChange("lastName", event.target.value)}
            error={errors.lastName}
            containerClassName="module-glass-control !rounded-[4px]"
          />
          <TextField
            label="Phone Number"
            prefixIcon={<Image src={phoneIcon} alt="" className="h-3 w-3" />}
            value={values.phoneNumber ?? ""}
            onChange={(event) => handleChange("phoneNumber", event.target.value)}
            error={errors.phoneNumber}
            containerClassName="module-glass-control !rounded-[4px]"
          />
          <TextField
            label="Title"
            required
            prefixIcon={<Image src={userIcon} alt="" className="h-3 w-3" />}
            value={values.title ?? ""}
            onChange={(event) => handleChange("title", event.target.value)}
            error={errors.title}
            containerClassName="module-glass-control !rounded-[4px]"
          />
          <TextField
            label="Tenant"
            required
            prefixIcon={<Image src={plantIcon} alt="" className="h-3 w-3" />}
            value={values.tenantId ?? ""}
            onChange={(event) => handleChange("tenantId", event.target.value)}
            error={errors.tenantId}
            containerClassName="module-glass-control !rounded-[4px]"
          />
          <TextField
            label="Department"
            required
            prefixIcon={<Image src={plantIcon} alt="" className="h-3 w-3" />}
            value={values.departmentId}
            onChange={(event) =>
              handleChange("departmentId", event.target.value)
            }
            error={errors.departmentId}
            containerClassName="module-glass-control !rounded-[4px]"
          />
        </div>
      </section>

      <div className="flex justify-end gap-3">
        <Button
          type="button"
          variant="ghost"
          size="sm"
          rounded="rounded-[4px]"
          onClick={() => router.push(ROUTES.masterUsers)}
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
          Update User
        </Button>
      </div>

      <Snackbar
        open={Boolean(notification.message)}
        variant={notification.variant}
        title={
          notification.variant === "success"
            ? "User updated"
            : "Unable to update user"
        }
        message={notification.message}
        onClose={() => setNotification({ message: "", variant: "error" })}
      />
    </form>
  );
}
