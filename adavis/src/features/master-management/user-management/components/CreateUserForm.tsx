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
import { useDepartments } from "../../department-management/hooks/useDepartments";
import { useTenants } from "../../tenant-management/hooks/useTenants";
import {
  assignUserToGroup,
  createUser,
} from "../api";
import { provisionAuthUser } from "@/features/auth/api";
import { createUserFormSchema } from "../schemas";
import {
  accountFields,
  AssignUserGroupFields,
  createEmptyUserFormValues,
  createUserInformationFields,
  type UserFormFieldId,
  type UserFormValues,
  UserFormSection,
  UserStepIndicator,
} from "./UserFormSections";

export default function CreateUserForm() {
  const router = useRouter();
  const loginContext = useLoginContext();
  const { isLoading: isLoadingLookups, options } = useMasterLookups();
  const { departments, isLoading: isLoadingDepartments } = useDepartments();
  const { tenants, isLoading: isLoadingTenants } = useTenants();
  const [activeStep, setActiveStep] = useState<1 | 2>(1);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [notification, setNotification] = useState({
    message: "",
    variant: "error" as "error" | "success",
  });
  const [errors, setErrors] = useState<
    Partial<Record<UserFormFieldId, string>>
  >({});
  const [values, setValues] = useState<UserFormValues>(
    createEmptyUserFormValues,
  );

  const userInformationFields = useMemo(
    () =>
      createUserInformationFields({
        departments: departments.filter((department) => department.isActive && department.tenantId === values.tenantId).map((department) => ({ value: department.departmentId, label: department.departmentName || department.name })),
        tenants: tenants.filter((tenant) => tenant.isActive).map((tenant) => ({ value: tenant.tenantId, label: `${tenant.companyName} (${tenant.tenantId})` })),
      }),
    [departments, tenants, values.tenantId],
  );

  const handleChange = (field: UserFormFieldId, value: string | string[]) => {
    setValues((previous) => ({
      ...previous,
      [field]: value,
      ...(field === "tenantId" ? { department: "" } : {}),
    }));
    setErrors((previous) => ({ ...previous, [field]: undefined }));
  };

  const validateUserInformation = () => {
    const result = createUserFormSchema.safeParse(values);
    if (result.success) {
      setErrors({});
      return true;
    }

    const fieldErrors = result.error.flatten().fieldErrors;
    setErrors(
      Object.fromEntries(
        Object.entries(fieldErrors).map(([field, messages]) => [
          field,
          messages?.[0],
        ]),
      ),
    );
    return false;
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (activeStep === 1) {
      if (validateUserInformation()) setActiveStep(2);
      return;
    }

    setIsSubmitting(true);
    setNotification({ message: "", variant: "error" });

    try {
      const username = values.username.trim();
      const createdUser = await createUser({
        userId: username,
        username,
        email: values.email.trim(),
        firstName: values.firstName.trim(),
        lastName: values.lastName.trim(),
        departmentId: values.department,
        isActive: values.lifecycleStatus === "ACTIVE",
        initialPassword: values.password,
        phoneNumber: values.phone.trim() || null,
        tenantId: values.tenantId,
        userTrackId: null,
        title: values.title,
        userType: values.userType,
        lifecycleStatus: values.lifecycleStatus,
        empId: values.empId,
        supportingDocumentIds: [],
        supportingDocuments: [],
        supportingDocumentType: null,
        reason: values.reason,
      });

      await provisionAuthUser({
        userId: createdUser.userId,
        username,
        email: values.email.trim(),
        initialPassword: values.password,
      });

      const selectedGroupIds = values.userGroup.filter(Boolean);
      if (selectedGroupIds.length > 0) {
        const actorUserId = loginContext?.user.userId;
        if (!actorUserId) {
          throw new ApiError({ status: 400, message: "The current administrator context is unavailable." });
        }
        await Promise.all(
          selectedGroupIds.map((groupId) =>
            assignUserToGroup(groupId, createdUser.userId, actorUserId),
          ),
        );
      }
      setNotification({
        message: "User created successfully.",
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
            : "Unable to create user. Please try again.",
        variant: "error",
      });
      setIsSubmitting(false);
    }
  };

  const clearForm = () => {
    setValues(createEmptyUserFormValues());
    setErrors({});
  };

  return (
    <form
      className="grid gap-6"
      onSubmit={handleSubmit}
    >
      <UserStepIndicator activeStep={activeStep} onStepChange={setActiveStep} />

      {activeStep === 1 ? (
        <>
          <UserFormSection
            title="Account Details"
            subtitle="Set up user's personal information and login credentials"
            fields={accountFields}
            values={values}
            errors={errors}
            onChange={handleChange}
          />
          <UserFormSection
            title="Enter User Information"
            subtitle="Fill out user details"
            fields={userInformationFields}
            values={values}
            errors={errors}
            onChange={handleChange}
          />

          <div className="flex justify-end gap-3">
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
              size="sm"
              rounded="rounded-[4px]"
              textSize="text-[10px]"
              paddingX="px-5"
              paddingY="py-0"
              className="h-9 shadow-[0_8px_18px_rgba(7,92,175,0.18)]"
            >
              Save and Proceed
            </Button>
          </div>
        </>
      ) : (
        <section className="module-glass-panel overflow-hidden rounded-lg shadow-[0_12px_24px_rgba(35,50,70,0.1)]">
          <div className="border-b border-[#E3E9F0] px-6 py-5">
            <h2 className="text-[14px] font-semibold text-text-heading">
              Assign User Group
            </h2>
            <p className="mt-2 text-[9px] text-text-secondary">
              Assign user to an existing user group
            </p>
          </div>

          <div className="px-6 py-5">
            <AssignUserGroupFields
              options={options.groups}
              roleOptions={options.roles}
              value={values.userGroup}
              onChange={(value) => handleChange("userGroup", value)}
            />

            <div className="mt-6 flex justify-end">
              <Button
                type="submit"
                isLoading={isSubmitting || isLoadingLookups || isLoadingDepartments || isLoadingTenants}
                size="sm"
                rounded="rounded-[4px]"
                textSize="text-[10px]"
                paddingX="px-8"
                paddingY="py-0"
                className="h-9 shadow-[0_8px_18px_rgba(7,92,175,0.18)]"
              >
                Save User
              </Button>
            </div>
          </div>
        </section>
      )}

      <Snackbar
        open={Boolean(notification.message)}
        variant={notification.variant}
        title={notification.variant === "success" ? "User created" : "Unable to create user"}
        message={notification.message}
        onClose={() => setNotification({ message: "", variant: "error" })}
      />
    </form>
  );
}
