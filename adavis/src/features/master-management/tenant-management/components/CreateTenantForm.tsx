"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Image from "next/image";
import { ApiError } from "@/api";
import { Button, Snackbar } from "@/components/ui";
import { ROUTES } from "@/config/routes";
import { createTenant } from "../api";
import type { TenantFormValues } from "../api";
import { tenantFormSchema } from "../schemas";
import TenantFormFields, { type TenantField } from "./TenantFormFields";
import clearIcon from "@/assets/icons/clear-icon.svg";

const emptyValues = (): TenantFormValues => ({ companyCode: "", companyName: "", domain: "" });

export default function CreateTenantForm() {
  const router = useRouter();
  const [values, setValues] = useState(emptyValues);
  const [errors, setErrors] = useState<Partial<Record<TenantField, string>>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [notification, setNotification] = useState({ message: "", variant: "error" as "error" | "success" });

  const change = (field: TenantField, value: string) => {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    const parsed = tenantFormSchema.safeParse(values);
    if (!parsed.success) {
      const fields = parsed.error.flatten().fieldErrors;
      setErrors({ companyCode: fields.companyCode?.[0], companyName: fields.companyName?.[0], domain: fields.domain?.[0] });
      return;
    }
    setIsSubmitting(true);
    try {
      const tenant = await createTenant({ ...parsed.data, isActive: true });
      setNotification({ message: `Tenant ${tenant.tenantId} created successfully.`, variant: "success" });
      window.setTimeout(() => {
        router.push(`${ROUTES.masterLicenses}?tenantId=${encodeURIComponent(tenant.tenantId)}`);
        router.refresh();
      }, 700);
    } catch (error) {
      setNotification({ message: error instanceof ApiError ? error.message : "Unable to create tenant.", variant: "error" });
      setIsSubmitting(false);
    }
  };

  return (
    <form onSubmit={submit} className="module-glass-panel overflow-hidden rounded-lg shadow-[0_12px_24px_rgba(35,50,70,0.1)]">
      <div className="border-b border-[#E3E9F0] px-6 py-5">
        <h2 className="text-[14px] font-semibold text-text-heading">Enter Tenant Details</h2>
        <p className="mt-2 text-[9px] text-text-secondary">The tenant ID is generated automatically by the system.</p>
      </div>
      <div className="grid gap-x-10 gap-y-6 px-6 py-5 md:grid-cols-2 xl:grid-cols-3">
        <TenantFormFields values={values} errors={errors} onChange={change} />
        <div className="flex justify-end gap-3 md:col-span-2 xl:col-span-3">
          <Button type="button" variant="ghost" size="sm" rounded="rounded-[4px]" textSize="text-[10px]" paddingX="px-4" paddingY="py-0" className="h-9 border-primary/35 bg-white/35 !text-primary hover:bg-white/65" prefixIcon={<Image src={clearIcon} alt="" aria-hidden="true" className="h-3.5 w-3.5" />} onClick={() => { setValues(emptyValues()); setErrors({}); }}>Clear All</Button>
          <Button type="submit" size="sm" rounded="rounded-[4px]" textSize="text-[10px]" paddingX="px-5" paddingY="py-0" className="h-9 shadow-[0_8px_18px_rgba(7,92,175,0.18)]" isLoading={isSubmitting}>Create Tenant</Button>
        </div>
      </div>
      <Snackbar open={Boolean(notification.message)} title={notification.variant === "success" ? "Tenant created" : "Unable to create tenant"} message={notification.message} variant={notification.variant} onClose={() => setNotification({ message: "", variant: "error" })} />
    </form>
  );
}
