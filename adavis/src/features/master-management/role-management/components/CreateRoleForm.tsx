"use client";

import Image, { type StaticImageData } from "next/image";
import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { CaretDown, Check } from "@phosphor-icons/react";
import { ApiError } from "@/api";
import { Button, Snackbar, TextField } from "@/components/ui";
import clearIcon from "@/assets/icons/clear-icon.svg";
import descriptionIcon from "@/assets/icons/division.svg";
import roleCodeIcon from "@/assets/icons/id-icon.svg";
import roleNameIcon from "@/assets/icons/roleName.svg";
import tenantIcon from "@/assets/icons/plant-icon.svg";
import { ROUTES } from "@/config/routes";
import { useTenants } from "../../tenant-management/hooks/useTenants";
import { createRole } from "../api";
import type { CreateRoleFormValues, Role } from "../api/types";
import { createRoleFormSchema } from "../schemas";
import RolePermissionsEditor from "./RolePermissionsEditor";

const emptyValues: CreateRoleFormValues = {
  tenantId: "",
  roleCode: "",
  name: "",
  description: "",
};

type RoleCreationStep = "details" | "permissions";

function Steps({
  activeStep,
  roleCreated,
  onStepChange,
}: {
  activeStep: RoleCreationStep;
  roleCreated: boolean;
  onStepChange: (step: RoleCreationStep) => void;
}) {
  const stepClass = (step: RoleCreationStep) =>
    `module-glass-control flex h-10 min-w-[190px] items-center gap-3 rounded-lg px-5 text-[10px] font-semibold transition-colors ${
      activeStep === step ? "text-primary" : "text-text-secondary hover:text-primary"
    }`;

  return (
    <div className="flex max-w-[520px] items-center">
      <button
        type="button"
        className={stepClass("details")}
        onClick={() => onStepChange("details")}
      >
        <span className="grid h-5 w-5 place-items-center rounded-full bg-primary text-[9px] text-white">
          {roleCreated ? <Check size={11} weight="bold" /> : "1"}
        </span>
        Role Creation
      </button>
      <span className="h-px flex-1 border-t border-dashed border-[#9DB0C5]" />
      <button
        type="button"
        className={stepClass("permissions")}
        onClick={() => onStepChange("permissions")}
      >
        <span className="grid h-5 w-5 place-items-center rounded-full bg-primary text-[9px] text-white">2</span>
        Permissions
      </button>
    </div>
  );
}

function FieldLabel({
  icon,
  children,
  required = false,
}: {
  icon: StaticImageData;
  children: React.ReactNode;
  required?: boolean;
}) {
  return (
    <span className="type-filter-label flex items-center gap-1.5 text-text-heading">
      <Image src={icon} alt="" aria-hidden="true" className="h-3 w-3" />
      {children}
      {required ? (
        <span className="ml-0.5 text-danger" aria-hidden="true">
          *
        </span>
      ) : null}
    </span>
  );
}

export default function CreateRoleForm() {
  const router = useRouter();
  const { tenants, isLoading: isLoadingTenants } = useTenants();
  const [values, setValues] = useState(emptyValues);
  const [activeStep, setActiveStep] = useState<RoleCreationStep>("details");
  const [errors, setErrors] = useState<Partial<Record<keyof CreateRoleFormValues, string>>>({});
  const [createdRole, setCreatedRole] = useState<Role | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [message, setMessage] = useState("");
  const tenantOptions = useMemo(() => tenants.filter((tenant) => tenant.isActive), [tenants]);

  const change = (field: keyof CreateRoleFormValues, value: string) => {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (createdRole) {
      setActiveStep("permissions");
      return;
    }
    const parsed = createRoleFormSchema.safeParse(values);
    if (!parsed.success) {
      setErrors(Object.fromEntries(Object.entries(parsed.error.flatten().fieldErrors).map(([key, messages]) => [key, messages?.[0]])));
      return;
    }
    setIsSubmitting(true);
    try {
      setCreatedRole(await createRole({ ...parsed.data, isActive: true }));
      setActiveStep("permissions");
    } catch (error) {
      setMessage(error instanceof ApiError ? error.message : "Unable to create role.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const clearForm = () => {
    setValues(emptyValues);
    setErrors({});
  };

  return (
    <div className="grid gap-6">
      <Steps
        activeStep={activeStep}
        roleCreated={Boolean(createdRole)}
        onStepChange={setActiveStep}
      />
      {activeStep === "details" ? (
        <form onSubmit={submit} className="module-glass-panel overflow-hidden rounded-lg shadow-[0_12px_24px_rgba(35,50,70,0.1)]">
          <div className="border-b border-[#E3E9F0] px-6 py-5">
            <h2 className="text-[14px] font-semibold text-text-heading">Role Details</h2>
            <p className="mt-2 text-[9px] text-text-secondary">Create the role before assigning module permissions</p>
          </div>
          <div className="grid gap-x-10 gap-y-6 px-6 py-5 md:grid-cols-2">
            <label htmlFor="role-tenant" className="grid gap-2">
              <FieldLabel icon={tenantIcon} required>Tenant</FieldLabel>
              <span className="module-glass-control relative flex h-9 items-center rounded-[4px]">
                <select id="role-tenant" value={values.tenantId} onChange={(event) => change("tenantId", event.target.value)} className="type-filter-value h-full w-full appearance-none rounded-[4px] bg-transparent px-3 pr-8 text-text-secondary outline-none">
                  <option value="">Select Tenant</option>
                  {tenantOptions.map((tenant) => <option key={tenant.tenantId} value={tenant.tenantId}>{tenant.companyName} ({tenant.tenantId})</option>)}
                </select>
                <CaretDown size={11} weight="bold" className="pointer-events-none absolute right-3 text-text-secondary" />
              </span>
              {errors.tenantId ? <span className="text-[9px] text-danger">{errors.tenantId}</span> : null}
            </label>
            <label htmlFor="role-code" className="grid gap-2">
              <FieldLabel icon={roleCodeIcon} required>Role Code</FieldLabel>
              <TextField id="role-code" value={values.roleCode} onChange={(event) => change("roleCode", event.target.value)} error={errors.roleCode} placeholder="Enter Role Code" containerClassName="module-glass-control !rounded-[4px] !px-3 !py-2" inputClassName="type-filter-value placeholder:text-text-secondary" hintClassName="text-[9px]" />
            </label>
            <label htmlFor="role-name" className="grid gap-2">
              <FieldLabel icon={roleNameIcon} required>Role Name</FieldLabel>
              <TextField id="role-name" value={values.name} onChange={(event) => change("name", event.target.value)} error={errors.name} placeholder="Enter Role Name" containerClassName="module-glass-control !rounded-[4px] !px-3 !py-2" inputClassName="type-filter-value placeholder:text-text-secondary" hintClassName="text-[9px]" />
            </label>
            <label htmlFor="role-description" className="grid gap-2 md:col-span-2">
              <FieldLabel icon={descriptionIcon}>Description</FieldLabel>
              <span className="module-glass-control rounded-[4px] px-3 py-2">
                <textarea id="role-description" rows={4} value={values.description} onChange={(event) => change("description", event.target.value)} placeholder="Enter role description" className="type-filter-value min-h-[88px] w-full resize-none bg-transparent text-text-heading outline-none placeholder:text-text-secondary" />
              </span>
              {errors.description ? <span className="text-[9px] text-danger">{errors.description}</span> : null}
            </label>
            <div className="flex justify-end gap-3 md:col-span-2">
              <Button type="button" variant="ghost" size="sm" prefixIcon={<Image src={clearIcon} alt="" aria-hidden="true" className="h-3.5 w-3.5" />} rounded="rounded-[4px]" textSize="text-[10px]" paddingX="px-4" paddingY="py-0" className="h-9 border-primary/35 bg-white/35 !text-primary hover:bg-white/65" onClick={clearForm}>Clear All</Button>
              <Button type="submit" size="sm" rounded="rounded-[4px]" textSize="text-[10px]" paddingX="px-5" paddingY="py-0" className="h-9 shadow-[0_8px_18px_rgba(7,92,175,0.18)]" isLoading={isSubmitting || isLoadingTenants}>{createdRole ? "Proceed to Permissions" : "Save and Proceed"}</Button>
            </div>
          </div>
        </form>
      ) : createdRole ? (
        <RolePermissionsEditor roleId={createdRole.roleId} onSaved={() => { router.push(ROUTES.masterRoles); router.refresh(); }} />
      ) : (
        <div className="module-glass-panel overflow-hidden rounded-lg shadow-[0_12px_24px_rgba(35,50,70,0.1)]">
          <div className="border-b border-[#E3E9F0] px-6 py-5">
            <h2 className="text-[14px] font-semibold text-text-heading">Permissions</h2>
            <p className="mt-2 text-[9px] text-text-secondary">
              Create the role first, then assign module permissions to that role.
            </p>
          </div>
          <div className="flex justify-end px-6 py-5">
            <Button
              type="button"
              size="sm"
              rounded="rounded-[4px]"
              textSize="text-[10px]"
              paddingX="px-5"
              paddingY="py-0"
              className="h-9 shadow-[0_8px_18px_rgba(7,92,175,0.18)]"
              onClick={() => setActiveStep("details")}
            >
              Go to Role Creation
            </Button>
          </div>
        </div>
      )}
      <Snackbar open={Boolean(message)} title="Unable to create role" message={message} variant="error" onClose={() => setMessage("")} />
    </div>
  );
}
