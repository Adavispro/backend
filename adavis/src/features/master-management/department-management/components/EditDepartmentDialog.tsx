"use client";

import { useMemo, useState } from "react";
import { ApiError } from "@/api";
import { Button, Dialog, Snackbar } from "@/components/ui";
import { usePlantTopology } from "../../plant-topology/hooks/usePlantTopology";
import { useTenants } from "../../tenant-management/hooks/useTenants";
import { useDepartments } from "../hooks/useDepartments";
import { updateDepartment } from "../api";
import type { Department } from "../api/types";
import { updateDepartmentFormSchema } from "../schemas";
import {
  createEditDepartmentFields,
  createEmptyDepartmentFormValues,
  createParentDepartmentOptions,
  type DepartmentFormFieldId,
  type DepartmentFormValues,
  DepartmentFormFields,
} from "./DepartmentFormFields";

interface EditDepartmentDialogProps {
  department: Department;
  onClose: () => void;
  onUpdated: (department: Department) => void;
}

const departmentToValues = (department: Department): DepartmentFormValues => ({
  ...createEmptyDepartmentFormValues(),
  tenantId: department.tenantId ?? "",
  plantId: department.plantId ?? "",
  departmentCode: department.departmentCode ?? "",
  name: department.departmentName || department.name,
  description: department.description ?? "",
  parentDepartmentId: department.parentDepartmentId ?? "",
});

const toNullableParent = (value: string) => {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
};

export default function EditDepartmentDialog({
  department,
  onClose,
  onUpdated,
}: EditDepartmentDialogProps) {
  const { departments, isLoading: isLoadingDepartments } = useDepartments();
  const { data: topology, isLoading: isLoadingTopology } = usePlantTopology();
  const { tenants, isLoading: isLoadingTenants } = useTenants();
  const [values, setValues] = useState<DepartmentFormValues>(() =>
    departmentToValues(department),
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

  const fields = useMemo(
    () =>
      createEditDepartmentFields({
        departments: createParentDepartmentOptions({
          departments,
          excludeDepartmentId: department.departmentId,
          plantId: values.plantId,
          tenantId: values.tenantId,
        }),
        tenants: tenants.filter((tenant) => tenant.isActive).map((tenant) => ({ value: tenant.tenantId, label: `${tenant.companyName} (${tenant.tenantId})` })),
        plants: topology.plants.filter((plant) => plant.isActive && (!values.tenantId || plant.tenantId === values.tenantId)).map((plant) => ({ value: plant.plantId, label: `${plant.plantName} (${plant.plantId})` })),
      }),
    [department.departmentId, departments, tenants, topology.plants, values.plantId, values.tenantId],
  );

  const handleClose = () => {
    if (isSubmitting) return;
    onClose();
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const parsedValues = updateDepartmentFormSchema.safeParse({
      tenantId: values.tenantId,
      plantId: values.plantId,
      departmentCode: values.departmentCode,
      name: values.name,
      description: values.description,
      parentDepartmentId: values.parentDepartmentId,
    });

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
      const updatedDepartment = await updateDepartment(department.departmentId, {
        tenantId: parsedValues.data.tenantId,
        plantId: parsedValues.data.plantId,
        departmentCode: parsedValues.data.departmentCode,
        departmentName: parsedValues.data.name,
        name: parsedValues.data.name,
        description: parsedValues.data.description,
        parentDepartmentId: toNullableParent(
          parsedValues.data.parentDepartmentId ?? "",
        ),
        isActive: department.isActive,
      });
      onUpdated(updatedDepartment);
      setNotification({
        message: "Department updated successfully.",
        variant: "success",
      });
      window.setTimeout(onClose, 450);
    } catch (error) {
      setNotification({
        message:
          error instanceof ApiError
            ? error.message
            : "Unable to update department. Please try again.",
        variant: "error",
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog
      isOpen
      title="Edit Department Details"
      onClose={handleClose}
      widthClassName="max-w-[680px]"
    >
      <form onSubmit={handleSubmit}>
        <div className="grid gap-6 px-6 py-5 md:grid-cols-2">
          <DepartmentFormFields
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
            isLoading={isSubmitting || isLoadingDepartments || isLoadingTopology || isLoadingTenants}
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
            ? "Department updated"
            : "Unable to update department"
        }
        message={notification.message}
        onClose={() => setNotification({ message: "", variant: "error" })}
      />
    </Dialog>
  );
}
