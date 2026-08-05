"use client";

import Image from "next/image";
import type { StaticImageData } from "next/image";
import { CaretDown } from "@phosphor-icons/react";
import { TextField } from "@/components/ui";
import idIcon from "@/assets/icons/id-icon.svg";
import modulesIcon from "@/assets/icons/modules.svg";
import plantIcon from "@/assets/icons/plant-icon.svg";
import userIcon from "@/assets/icons/user.svg";
import userGroupIcon from "@/assets/icons/userGroup.svg";
import type { CreateAssignmentValues } from "../api";

export type AssignmentField = keyof CreateAssignmentValues;

export interface AssignmentSelectOption {
  value: string;
  label: string;
}

interface SelectFieldConfig {
  field: AssignmentField;
  label: string;
  icon: StaticImageData;
  required?: boolean;
  options: AssignmentSelectOption[];
}

const requiredFields = new Set<AssignmentField>([
  "assignmentType",
  "tenantId",
  "scopeType",
]);

function FieldLabel({
  icon,
  label,
  required,
}: {
  icon: StaticImageData;
  label: string;
  required?: boolean;
}) {
  return (
    <span className="type-filter-label flex items-center gap-1.5 text-text-heading">
      <Image src={icon} alt="" aria-hidden="true" className="h-3.5 w-3.5" />
      <span>
        {label}
        {required ? (
          <span className="ml-0.5 text-danger" aria-hidden="true">
            *
          </span>
        ) : null}
      </span>
    </span>
  );
}

function SelectField({
  field,
  label,
  icon,
  required,
  options,
  value,
  error,
  onChange,
}: SelectFieldConfig & {
  value: string;
  error?: string;
  onChange: (field: AssignmentField, value: string) => void;
}) {
  return (
    <label className="grid gap-2">
      <FieldLabel icon={icon} label={label} required={required} />
      <span className="module-glass-control relative flex h-9 items-center rounded-[4px]">
        <select
          value={value}
          onChange={(event) => onChange(field, event.target.value)}
          className="type-filter-value h-full w-full appearance-none rounded-[4px] bg-transparent px-3 pr-8 text-text-secondary outline-none"
        >
          <option value="">Select {label}</option>
          {options.map((item) => (
            <option key={`${field}-${item.value}-${item.label}`} value={item.value}>
              {item.label}
            </option>
          ))}
        </select>
        <CaretDown
          size={11}
          weight="bold"
          className="pointer-events-none absolute right-3 text-text-secondary"
        />
      </span>
      {error ? <span className="text-[9px] text-danger">{error}</span> : null}
    </label>
  );
}

export default function AssignmentFormFields({
  values,
  errors,
  tenantOptions,
  userOptions,
  groupOptions,
  plantOptions,
  onChange,
}: {
  values: CreateAssignmentValues;
  errors: Partial<Record<AssignmentField, string>>;
  tenantOptions: AssignmentSelectOption[];
  userOptions: AssignmentSelectOption[];
  groupOptions: AssignmentSelectOption[];
  plantOptions: AssignmentSelectOption[];
  onChange: (field: AssignmentField, value: string) => void;
}) {
  const isPlantScope = values.scopeType === "PLANT";

  const selectFields: SelectFieldConfig[] = [
    {
      field: "assignmentType",
      label: "Assignment Type",
      icon: modulesIcon,
      required: requiredFields.has("assignmentType"),
      options: [
        { value: "GROUP_SCOPE", label: "GROUP_SCOPE" },
        { value: "USER_OVERRIDE", label: "USER_OVERRIDE" },
      ],
    },
    {
      field: "tenantId",
      label: "Tenant",
      icon: idIcon,
      required: requiredFields.has("tenantId"),
      options: tenantOptions,
    },
    {
      field: "groupId",
      label: "User Group",
      icon: userGroupIcon,
      required: true,
      options: groupOptions,
    },
    {
      field: "userId",
      label: "User",
      icon: userIcon,
      required: true,
      options: userOptions,
    },
    {
      field: "scopeType",
      label: "Scope Type",
      icon: modulesIcon,
      required: requiredFields.has("scopeType"),
      options: [
        { value: "PLANT", label: "PLANT" },
        { value: "RESOURCE", label: "RESOURCE" },
      ],
    },
  ];

  return (
    <>
      {selectFields.map((field) => (
        <SelectField
          key={field.field}
          {...field}
          value={String(values[field.field] ?? "")}
          error={errors[field.field]}
          onChange={onChange}
        />
      ))}

      {isPlantScope ? (
        <SelectField
          field="plantId"
          label="Plant"
          icon={plantIcon}
          required
          options={plantOptions}
          value={values.plantId ?? ""}
          error={errors.plantId}
          onChange={onChange}
        />
      ) : (
        <label className="grid gap-2">
          <FieldLabel
            icon={plantIcon}
            label="Asset ID"
            required={requiredFields.has("resourceId")}
          />
          <TextField
            value={values.resourceId ?? ""}
            onChange={(event) => onChange("resourceId", event.target.value)}
            placeholder="Enter Asset ID"
            error={errors.resourceId}
            containerClassName="module-glass-control !rounded-[4px] !px-3 !py-2"
            inputClassName="type-filter-value"
          />
        </label>
      )}

      <label className="grid gap-2 md:col-span-2 xl:col-span-3">
        <FieldLabel
          icon={idIcon}
          label="Reason"
          required={requiredFields.has("reason")}
        />
        <span className="module-glass-control rounded-[4px] px-3 py-2">
          <textarea
            rows={4}
            value={values.reason}
            onChange={(event) => onChange("reason", event.target.value)}
            className="type-filter-value min-h-[88px] w-full resize-none bg-transparent outline-none"
            placeholder="Enter assignment reason"
          />
        </span>
        {errors.reason ? (
          <span className="text-[9px] text-danger">{errors.reason}</span>
        ) : null}
      </label>
    </>
  );
}
