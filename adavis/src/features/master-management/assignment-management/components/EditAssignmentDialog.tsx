"use client";

import Image from "next/image";
import { useState } from "react";
import { ApiError } from "@/api";
import { Button, Dialog, Snackbar } from "@/components/ui";
import clearIcon from "@/assets/icons/clear-icon.svg";
import { useLoginContext } from "@/features/auth/hooks/useCurrentUser";
import { useMasterLookups } from "../../lookups/hooks";
import { usePlantTopology } from "../../plant-topology/hooks/usePlantTopology";
import { useTenants } from "../../tenant-management/hooks/useTenants";
import { updateAssignment } from "../api";
import type { Assignment, CreateAssignmentValues } from "../api";
import { createAssignmentSchema } from "../schemas";
import AssignmentFormFields, { type AssignmentField } from "./AssignmentFormFields";

const valuesFromAssignment = (assignment: Assignment | null): CreateAssignmentValues => {
  const assignmentType = assignment?.assignmentType === "USER_OVERRIDE"
    ? "USER_OVERRIDE"
    : assignment?.assignmentType === "GROUP_SCOPE"
      ? "GROUP_SCOPE"
      : assignment?.userId
        ? "USER_OVERRIDE"
        : "GROUP_SCOPE";

  const scopeType = assignment?.scopeType === "RESOURCE"
    ? "RESOURCE"
    : assignment?.scopeType === "PLANT"
      ? "PLANT"
      : assignment?.resourceType === "ASSET"
        ? "RESOURCE"
        : "PLANT";

  return {
    assignmentType,
    tenantId: assignment?.tenantId ?? "",
    userId: assignment?.userId ?? "",
    groupId: assignment?.groupId ?? "",
    scopeType,
    plantId: scopeType === "PLANT" ? assignment?.plantId ?? assignment?.resourceId ?? "" : "",
    resourceId: scopeType === "RESOURCE" ? assignment?.resourceId ?? "" : "",
    assignedBy: assignment?.assignedBy ?? "",
    reason: assignment?.reason ?? "",
  };
};

export default function EditAssignmentDialog({
  assignment,
  onClose,
  onUpdated,
}: {
  assignment: Assignment | null;
  onClose: () => void;
  onUpdated: (assignment: Assignment, previousId: string) => void;
}) {
  const context = useLoginContext();
  const { options, isLoading: lookupsLoading } = useMasterLookups();
  const { tenants, isLoading: tenantsLoading } = useTenants();
  const { data: topology, isLoading: topologyLoading } = usePlantTopology();
  const [values, setValues] = useState<CreateAssignmentValues>(() => valuesFromAssignment(assignment));
  const [errors, setErrors] = useState<Partial<Record<AssignmentField, string>>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [message, setMessage] = useState("");

  if (!assignment) return null;

  const change = (field: AssignmentField, value: string) => {
    setValues((current) => ({
      ...current,
      [field]: value,
      ...(field === "scopeType" ? (value === "PLANT" ? { resourceId: "" } : { plantId: "" }) : {}),
      ...(field === "tenantId" ? { plantId: "" } : {}),
    } as CreateAssignmentValues));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    const candidate = { ...values, assignedBy: context?.user.userId ?? values.assignedBy };
    const parsed = createAssignmentSchema.safeParse(candidate);

    if (!parsed.success) {
      setErrors(Object.fromEntries(parsed.error.issues.map((issue) => [String(issue.path[0]), issue.message])));
      return;
    }

    setIsSubmitting(true);
    try {
      const updated = await updateAssignment(assignment, parsed.data);
      onUpdated(updated, assignment.assignmentId);
      onClose();
    } catch (error) {
      setMessage(error instanceof ApiError ? error.message : "Unable to update assignment.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const plants = topology.plants
    .filter((plant) => plant.isActive && (!values.tenantId || plant.tenantId === values.tenantId))
    .map((plant) => ({ value: plant.plantId, label: `${plant.plantName} (${plant.plantId})` }));
  const tenantOptions = tenants
    .filter((tenant) => tenant.isActive)
    .map((tenant) => ({ value: tenant.tenantId, label: `${tenant.companyName} (${tenant.tenantId})` }));

  return (
    <>
      <Dialog
        isOpen={Boolean(assignment)}
        title="Edit Assignment Details"
        onClose={onClose}
        widthClassName="max-w-[980px]"
        contentClassName="p-6"
      >
        <form className="grid gap-x-10 gap-y-6 md:grid-cols-2 xl:grid-cols-3" onSubmit={submit}>
          <AssignmentFormFields
            values={values}
            errors={errors}
            tenantOptions={tenantOptions}
            userOptions={options.users}
            groupOptions={options.groups}
            plantOptions={plants}
            onChange={change}
          />

          <div className="flex justify-end gap-3 border-t border-line/70 pt-4 md:col-span-2 xl:col-span-3">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              rounded="rounded-[4px]"
              textSize="text-[10px]"
              paddingX="px-4"
              paddingY="py-0"
              className="h-9 border-primary/35 bg-white/35 !text-primary"
              prefixIcon={<Image src={clearIcon} alt="" className="h-3.5 w-3.5" />}
              onClick={() => {
                setValues(valuesFromAssignment(assignment));
                setErrors({});
              }}
            >
              Discard Changes
            </Button>
            <Button
              type="submit"
              size="sm"
              rounded="rounded-[4px]"
              textSize="text-[10px]"
              paddingX="px-5"
              paddingY="py-0"
              className="h-9"
              isLoading={isSubmitting || lookupsLoading || tenantsLoading || topologyLoading}
            >
              Save Changes
            </Button>
          </div>
        </form>
      </Dialog>
      <Snackbar
        open={Boolean(message)}
        title="Unable to update assignment"
        message={message}
        variant="error"
        onClose={() => setMessage("")}
      />
    </>
  );
}
