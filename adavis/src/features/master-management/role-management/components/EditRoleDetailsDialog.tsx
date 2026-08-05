"use client";

import Image, { type StaticImageData } from "next/image";
import { useMemo, useState } from "react";
import { CaretDown } from "@phosphor-icons/react";
import { ApiError } from "@/api";
import { Button, Dialog, Snackbar, TextField } from "@/components/ui";
import descriptionIcon from "@/assets/icons/division.svg";
import roleCodeIcon from "@/assets/icons/id-icon.svg";
import roleNameIcon from "@/assets/icons/roleName.svg";
import tenantIcon from "@/assets/icons/plant-icon.svg";
import { useTenants } from "../../tenant-management/hooks/useTenants";
import { buildUpdateRoleRequest, updateRole } from "../api";
import type { CreateRoleFormValues, Role } from "../api/types";
import { createRoleFormSchema } from "../schemas";

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

export default function EditRoleDetailsDialog({ role, onClose, onUpdated }: { role: Role | null; onClose: () => void; onUpdated: (role: Role) => void }) {
  const { tenants } = useTenants();
  const [values, setValues] = useState<CreateRoleFormValues>(() => ({
    tenantId: role?.tenantId ?? "",
    roleCode: role?.roleCode ?? "",
    name: role?.roleName || role?.name || "",
    description: role?.description ?? "",
  }));
  const [errors, setErrors] = useState<Partial<Record<keyof CreateRoleFormValues, string>>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [message, setMessage] = useState("");
  const tenantOptions = useMemo(() => tenants.filter((tenant) => tenant.isActive || tenant.tenantId === role?.tenantId), [role?.tenantId, tenants]);
  if (!role) return null;

  const change = (field: keyof CreateRoleFormValues, value: string) => {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setMessage("");

    const nextTenantId = values.tenantId.trim();
    const nextRoleCode = values.roleCode.trim();
    const nextRoleName = values.name.trim();
    const nextDescription = values.description?.trim() ?? "";

    const currentTenantId = (role.tenantId ?? "").trim();
    const currentRoleCode = (role.roleCode ?? "").trim();
    const currentRoleName = (role.roleName || role.name || "").trim();
    const currentDescription = (role.description ?? "").trim();

    const hasChanges =
      nextTenantId !== currentTenantId ||
      nextRoleCode !== currentRoleCode ||
      nextRoleName !== currentRoleName ||
      nextDescription !== currentDescription;

    if (!hasChanges) {
      onClose();
      return;
    }

    const effectiveValues = {
      tenantId: nextTenantId || currentTenantId,
      roleCode: nextRoleCode || currentRoleCode,
      name: nextRoleName,
      description: values.description,
    };

    const parsed = createRoleFormSchema.safeParse(effectiveValues);
    if (!parsed.success) {
      setErrors(Object.fromEntries(Object.entries(parsed.error.flatten().fieldErrors).map(([key, messages]) => [key, messages?.[0]])));
      setMessage(parsed.error.issues[0]?.message ?? "Please correct the highlighted fields.");
      return;
    }

    setIsSubmitting(true);
    try {
      onUpdated(await updateRole(role.roleId, buildUpdateRoleRequest(role, parsed.data)));
      onClose();
    } catch (error) {
      setMessage(error instanceof ApiError ? error.message : "Unable to update role details.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const fieldClass = "module-glass-control !rounded-[4px] !px-3 !py-2";
  return (
    <>
      <Dialog
        isOpen
        title="Edit Role Details"
        onClose={onClose}
        widthClassName="max-w-[760px]"
        contentClassName="p-6"
      >
        <form onSubmit={submit} className="grid gap-x-10 gap-y-6 md:grid-cols-2">
          <label className="grid gap-2">
            <FieldLabel icon={tenantIcon} required>
              Tenant
            </FieldLabel>
            <span className="module-glass-control relative flex h-9 items-center rounded-[4px]">
              <select
                value={values.tenantId}
                onChange={(event) => change("tenantId", event.target.value)}
                className="type-filter-value h-full w-full appearance-none bg-transparent px-3 pr-8 outline-none"
              >
                <option value="">Select Tenant</option>
                {tenantOptions.map((tenant) => (
                  <option key={tenant.tenantId} value={tenant.tenantId}>
                    {tenant.companyName} ({tenant.tenantId})
                  </option>
                ))}
              </select>
              <CaretDown size={11} className="pointer-events-none absolute right-3" />
            </span>
            {errors.tenantId ? (
              <span className="text-[9px] text-danger">{errors.tenantId}</span>
            ) : null}
          </label>

          <label className="grid gap-2">
            <FieldLabel icon={roleCodeIcon} required>
              Role Code
            </FieldLabel>
            <TextField
              value={values.roleCode}
              onChange={(event) => change("roleCode", event.target.value)}
              error={errors.roleCode}
              placeholder="Enter Role Code"
              containerClassName={fieldClass}
              inputClassName="type-filter-value placeholder:text-text-secondary"
              hintClassName="text-[9px]"
            />
          </label>

          <label className="grid gap-2">
            <FieldLabel icon={roleNameIcon} required>
              Role Name
            </FieldLabel>
            <TextField
              value={values.name}
              onChange={(event) => change("name", event.target.value)}
              error={errors.name}
              placeholder="Enter Role Name"
              containerClassName={fieldClass}
              inputClassName="type-filter-value placeholder:text-text-secondary"
              hintClassName="text-[9px]"
            />
          </label>

          <label className="grid gap-2 md:col-span-2">
            <FieldLabel icon={descriptionIcon}>Description</FieldLabel>
            <span className="module-glass-control rounded-[4px] px-3 py-2">
              <textarea
                rows={4}
                value={values.description}
                onChange={(event) => change("description", event.target.value)}
                placeholder="Enter role description"
                className="type-filter-value min-h-[88px] w-full resize-none bg-transparent outline-none placeholder:text-text-secondary"
              />
            </span>
            {errors.description ? (
              <span className="text-[9px] text-danger">{errors.description}</span>
            ) : null}
          </label>

          <div className="flex justify-end gap-3 md:col-span-2">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              rounded="rounded-[4px]"
              className="h-9 border-primary/35 !text-primary"
              onClick={onClose}
            >
              Discard Changes
            </Button>
            <Button
              type="submit"
              size="sm"
              rounded="rounded-[4px]"
              className="h-9"
              isLoading={isSubmitting}
            >
              Save Changes
            </Button>
          </div>
        </form>
      </Dialog>
      <Snackbar
        open={Boolean(message)}
        title="Unable to update role"
        message={message}
        variant="error"
        onClose={() => setMessage("")}
      />
    </>
  );
}
