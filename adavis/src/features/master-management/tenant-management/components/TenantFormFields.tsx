"use client";

import { Buildings, Globe, IdentificationCard } from "@phosphor-icons/react";
import { TextField } from "@/components/ui";
import type { TenantFormValues } from "../api";

export type TenantField = keyof TenantFormValues;

export default function TenantFormFields({
  values,
  errors,
  onChange,
}: {
  values: TenantFormValues;
  errors: Partial<Record<TenantField, string>>;
  onChange: (field: TenantField, value: string) => void;
}) {
  const fields = [
    { id: "companyCode" as const, label: "Tenant Code", placeholder: "Enter company code", icon: IdentificationCard, required: true },
    { id: "companyName" as const, label: "Tenant Name", placeholder: "Enter company name", icon: Buildings, required: true },
    //{ id: "domain" as const, label: "Domain", placeholder: "tenant.adavis.local", icon: Globe, required: false },
  ];

  return fields.map(({ id, label, placeholder, icon: Icon, required }) => (
    <label key={id} htmlFor={id} className="grid gap-2">
      <span className="type-filter-label flex items-center gap-1.5 text-text-heading">
        <Icon size={12} className="text-primary" />
        {label}
        {required ? (
          <span className="ml-0.5 text-danger" aria-hidden="true">
            *
          </span>
        ) : null}
      </span>
      <TextField
        id={id}
        value={values[id] ?? ""}
        placeholder={placeholder}
        error={errors[id]}
        onChange={(event) => onChange(id, event.target.value)}
        containerClassName="module-glass-control !rounded-[4px] !px-3 !py-2"
        inputClassName="type-filter-value placeholder:text-text-secondary"
        hintClassName="text-[9px]"
      />
    </label>
  ));
}
