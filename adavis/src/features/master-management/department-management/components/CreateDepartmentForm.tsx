"use client";

import Image from "next/image";
import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError } from "@/api";
import { Button, Snackbar } from "@/components/ui";
import { ROUTES } from "@/config/routes";
import clearIcon from "@/assets/icons/clear-icon.svg";
import { usePlantTopology } from "../../plant-topology/hooks/usePlantTopology";
import { useTenants } from "../../tenant-management/hooks/useTenants";
import { useDepartments } from "../hooks/useDepartments";
import { createDepartment } from "../api";
import { departmentFormSchema } from "../schemas";
import {
  createDepartmentFields,
  createEmptyDepartmentFormValues,
  createParentDepartmentOptions,
  type DepartmentFormFieldId,
  type DepartmentFormValues,
  DepartmentFormFields,
} from "./DepartmentFormFields";

const toNullableParent = (value: string) => {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
};

export default function CreateDepartmentForm() {
  const router = useRouter();
  const { departments, isLoading: isLoadingDepartments } = useDepartments();
  const { data: topology, isLoading: isLoadingTopology } = usePlantTopology();
  const { tenants, isLoading: isLoadingTenants } = useTenants();
  const [values, setValues] = useState<DepartmentFormValues>(
    createEmptyDepartmentFormValues,
  );
  const [errors, setErrors] = useState<
    Partial<Record<DepartmentFormFieldId, string>>
  >({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [notification, setNotification] = useState({
    message: "",
    variant: "error" as "error" | "success",
  });

  const handleChange = (field: DepartmentFormFieldId, value: string) => {
    setValues((previous) => ({
      ...previous,
      [field]: value,
      ...(field === "tenantId" ? { plantId: "", parentDepartmentId: "" } : {}),
      ...(field === "plantId" ? { parentDepartmentId: "" } : {}),
    }));
    setErrors((previous) => ({ ...previous, [field]: undefined }));
  };

  const clearForm = () => {
    setValues(createEmptyDepartmentFormValues());
    setErrors({});
  };

  const fields = useMemo(
    () =>
      createDepartmentFields({
        departments: createParentDepartmentOptions({
          departments,
          plantId: values.plantId,
          tenantId: values.tenantId,
        }),
        tenants: tenants.filter((tenant) => tenant.isActive).map((tenant) => ({ value: tenant.tenantId, label: `${tenant.companyName} (${tenant.tenantId})` })),
        plants: topology.plants.filter((plant) => plant.isActive && (!values.tenantId || plant.tenantId === values.tenantId)).map((plant) => ({ value: plant.plantId, label: `${plant.plantName} (${plant.plantId})` })),
      }),
    [departments, tenants, topology.plants, values.plantId, values.tenantId],
  );

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const parsedValues = departmentFormSchema.safeParse(values);

    if (!parsedValues.success) {
      const fieldErrors = parsedValues.error.flatten().fieldErrors;
      setErrors({
        tenantId: fieldErrors.tenantId?.[0],
        plantId: fieldErrors.plantId?.[0],
        departmentCode: fieldErrors.departmentCode?.[0],
        name: fieldErrors.name?.[0],
        description: fieldErrors.description?.[0],
        parentDepartmentId: fieldErrors.parentDepartmentId?.[0],
      });
      return;
    }

    setIsSubmitting(true);
    setNotification({ message: "", variant: "error" });

    try {
      await createDepartment({
        tenantId: parsedValues.data.tenantId,
        plantId: parsedValues.data.plantId,
        departmentCode: parsedValues.data.departmentCode,
        departmentName: parsedValues.data.name,
        name: parsedValues.data.name,
        description: parsedValues.data.description,
        parentDepartmentId: toNullableParent(
          parsedValues.data.parentDepartmentId ?? "",
        ),
      });
      setNotification({
        message: "Department created successfully.",
        variant: "success",
      });
      window.setTimeout(() => {
        router.push(ROUTES.masterDepartments);
        router.refresh();
      }, 700);
    } catch (error) {
      setNotification({
        message:
          error instanceof ApiError
            ? error.message
            : "Unable to create department. Please try again.",
        variant: "error",
      });
      setIsSubmitting(false);
    }
  };

  return (
    <form
      className="module-glass-panel overflow-hidden rounded-lg shadow-[0_12px_24px_rgba(35,50,70,0.1)]"
      onSubmit={handleSubmit}
    >
      <div className="border-b border-[#E3E9F0] px-6 py-5">
        <h2 className="text-[14px] font-semibold text-text-heading">
          Enter Department Details
        </h2>
        <p className="mt-2 text-[9px] text-text-secondary">
          Fill out the required details to create a department
        </p>
      </div>

      <div className="grid gap-6 px-6 py-5 md:grid-cols-2">
        <DepartmentFormFields
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
            isLoading={isSubmitting || isLoadingDepartments || isLoadingTopology || isLoadingTenants}
            size="sm"
            rounded="rounded-[4px]"
            textSize="text-[10px]"
            paddingX="px-5"
            paddingY="py-0"
            className="h-9 shadow-[0_8px_18px_rgba(7,92,175,0.18)]"
          >
            Save Department
          </Button>
        </div>
      </div>

      <Snackbar
        open={Boolean(notification.message)}
        variant={notification.variant}
        title={
          notification.variant === "success"
            ? "Department created"
            : "Unable to create department"
        }
        message={notification.message}
        onClose={() => setNotification({ message: "", variant: "error" })}
      />
    </form>
  );
}
