"use client";

import { useState } from "react";
import { ApiError } from "@/api";
import { Button, Dialog, Snackbar } from "@/components/ui";
import { updateTenant } from "../api";
import type { Tenant, TenantFormValues } from "../api";
import { tenantFormSchema } from "../schemas";
import TenantFormFields, { type TenantField } from "./TenantFormFields";

export default function EditTenantDialog({ tenant, onClose, onUpdated }: { tenant: Tenant | null; onClose: () => void; onUpdated: (tenant: Tenant) => void }) {
  const [values, setValues] = useState<TenantFormValues>({ companyCode: tenant?.companyCode ?? "", companyName: tenant?.companyName ?? "", domain: tenant?.domain ?? "" });
  const [errors, setErrors] = useState<Partial<Record<TenantField, string>>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [message, setMessage] = useState("");

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!tenant) return;
    const parsed = tenantFormSchema.safeParse(values);
    if (!parsed.success) {
      const fields = parsed.error.flatten().fieldErrors;
      setErrors({ companyCode: fields.companyCode?.[0], companyName: fields.companyName?.[0], domain: fields.domain?.[0] });
      return;
    }
    setIsSubmitting(true);
    try {
      onUpdated(await updateTenant(tenant.tenantId, { ...parsed.data, isActive: tenant.isActive }));
      onClose();
    } catch (error) {
      setMessage(error instanceof ApiError ? error.message : "Unable to update tenant.");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <>
      <Dialog isOpen={Boolean(tenant)} title="Edit Tenant Details" onClose={onClose} widthClassName="max-w-[720px]" contentClassName="p-6">
        <form className="grid gap-6 md:grid-cols-2" onSubmit={submit}>
          <TenantFormFields values={values} errors={errors} onChange={(field, value) => { setValues((current) => ({ ...current, [field]: value })); setErrors((current) => ({ ...current, [field]: undefined })); }} />
          <div className="flex justify-end gap-3 border-t border-line/70 pt-4 md:col-span-2">
            <Button variant="ghost" size="sm" onClick={onClose}>Discard Changes</Button>
            <Button type="submit" size="sm" isLoading={isSubmitting}>Save Changes</Button>
          </div>
        </form>
      </Dialog>
      <Snackbar open={Boolean(message)} title="Unable to update tenant" message={message} variant="error" onClose={() => setMessage("")} />
    </>
  );
}
