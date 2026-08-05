"use client";

import Image from "next/image";
import type { StaticImageData } from "next/image";
import { CaretDown } from "@phosphor-icons/react/dist/ssr";
import { TextField } from "@/components/ui";
import departmentIcon from "@/assets/icons/department.svg";
import parentIcon from "@/assets/icons/parent.svg";
import type { MasterLookupOption } from "../../lookups/api";
import type { Department } from "../api/types";

export type DepartmentFormFieldId =
  | "tenantId"
  | "plantId"
  | "departmentCode"
  | "name"
  | "description"
  | "parentDepartmentId";

export type DepartmentFormValues = Record<DepartmentFormFieldId, string>;

interface DepartmentFieldConfig {
  id: DepartmentFormFieldId;
  label: string;
  placeholder: string;
  icon: StaticImageData;
  readOnly?: boolean;
  options?: MasterLookupOption[];
  required?: boolean;
  textarea?: boolean;
  type?: "text" | "number";
  colSpanClassName?: string;
}

export const createEmptyDepartmentFormValues = (): DepartmentFormValues => ({
  tenantId: "",
  plantId: "",
  departmentCode: "",
  name: "",
  description: "",
  parentDepartmentId: "",
});

const getDepartmentLabel = (department: Department) =>
  department.departmentName ||
  department.name ||
  department.departmentCode ||
  department.departmentId;

export const createParentDepartmentOptions = ({
  departments,
  excludeDepartmentId,
  plantId,
  tenantId,
}: {
  departments: Department[];
  excludeDepartmentId?: string;
  plantId?: string;
  tenantId?: string;
}): MasterLookupOption[] => {
  const selectedTenantId = tenantId?.trim();
  const selectedPlantId = plantId?.trim();
  const seen = new Set<string>();

  return departments
    .filter((department) => department.isActive)
    .filter((department) => department.departmentId !== excludeDepartmentId)
    .filter((department) =>
      selectedTenantId
        ? !department.tenantId || department.tenantId === selectedTenantId
        : true,
    )
    .filter((department) =>
      selectedPlantId
        ? !department.plantId || department.plantId === selectedPlantId
        : true,
    )
    .map((department) => ({
      value: department.departmentId,
      label: getDepartmentLabel(department),
    }))
    .filter((option) => {
      if (seen.has(option.value)) return false;
      seen.add(option.value);
      return true;
    })
    .sort((left, right) => left.label.localeCompare(right.label));
};

const emptySelectOption = (label: string): MasterLookupOption => ({
  label,
  value: "",
});

export const createDepartmentFields = ({
  departments = [],
  plants = [],
  tenants = [],
}: {
  departments?: MasterLookupOption[];
  plants?: MasterLookupOption[];
  tenants?: MasterLookupOption[];
} = {}): DepartmentFieldConfig[] => [
  {
    id: "tenantId",
    label: "Tenant",
    placeholder: "Select Tenant",
    icon: departmentIcon,
    options: [emptySelectOption("Select Tenant"), ...tenants],
    required: true,
  },
  {
    id: "plantId",
    label: "Plant",
    placeholder: "Select Plant",
    icon: departmentIcon,
    options: [emptySelectOption("Select Plant"), ...plants],
    required: true,
  },
  {
    id: "departmentCode",
    label: "Department Code",
    placeholder: "Enter Department Code",
    icon: departmentIcon,
    required: true,
  },
  {
    id: "name",
    label: "Department Name",
    placeholder: "Enter Department Name",
    icon: departmentIcon,
    required: true,
  },
  {
    id: "description",
    label: "Description",
    placeholder: "Enter Department Description",
    icon: departmentIcon,
    textarea: true,
    colSpanClassName: "md:col-span-2",
  },
  {
    id: "parentDepartmentId",
    label: "Parent Department",
    placeholder: "Select Parent Department",
    icon: parentIcon,
    options: [emptySelectOption("Select Parent Department"), ...departments],
  },
];

export const departmentFields = createDepartmentFields();

export const createEditDepartmentFields = (options?: {
  departments?: MasterLookupOption[];
  plants?: MasterLookupOption[];
  tenants?: MasterLookupOption[];
}) => createDepartmentFields(options);

export const editDepartmentFields = createEditDepartmentFields();

function FieldLabel({
  icon,
  required,
  children,
}: {
  icon: StaticImageData;
  required?: boolean;
  children: string;
}) {
  return (
    <span className="type-filter-label flex items-center gap-1.5 text-text-heading">
      <Image src={icon} alt="" aria-hidden="true" className="h-3.5 w-3.5" />
      <span>
        {children}
        {required ? (
          <span className="ml-0.5 text-danger" aria-hidden="true">
            *
          </span>
        ) : null}
      </span>
    </span>
  );
}

export function DepartmentFormFields({
  fields = departmentFields,
  values,
  errors,
  onChange,
}: {
  fields?: DepartmentFieldConfig[];
  values: DepartmentFormValues;
  errors: Partial<Record<DepartmentFormFieldId, string>>;
  onChange: (field: DepartmentFormFieldId, value: string) => void;
}) {
  return (
    <>
      {fields.map((field) => (
        <label
          key={field.id}
          htmlFor={field.id}
          className={`grid gap-2 ${field.colSpanClassName ?? ""}`}
        >
          <FieldLabel icon={field.icon} required={field.required}>
            {field.label}
          </FieldLabel>
          {field.options ? (
            <>
              <span className="module-glass-control relative flex h-9 items-center rounded-[4px]">
                <select
                  id={field.id}
                  value={values[field.id]}
                  disabled={field.readOnly}
                  onChange={(event) => onChange(field.id, event.target.value)}
                  className="type-filter-value h-full w-full appearance-none rounded-[4px] bg-transparent px-3 pr-8 text-text-secondary outline-none disabled:cursor-not-allowed disabled:opacity-70"
                >
                  {field.options.map((option) => (
                    <option
                      key={`${field.id}-${option.value}-${option.label}`}
                      value={option.value}
                    >
                      {option.label}
                    </option>
                  ))}
                </select>
                <CaretDown
                  aria-hidden="true"
                  size={11}
                  weight="bold"
                  className="pointer-events-none absolute right-3 text-text-secondary"
                />
              </span>
              {errors[field.id] ? (
                <span className="text-[9px] text-danger">
                  {errors[field.id]}
                </span>
              ) : null}
            </>
          ) : field.textarea ? (
            <>
              <span className="module-glass-control flex rounded-[4px] px-3 py-2">
                <textarea
                  id={field.id}
                  rows={4}
                  placeholder={field.placeholder}
                  value={values[field.id]}
                  readOnly={field.readOnly}
                  onChange={(event) => onChange(field.id, event.target.value)}
                  className="type-filter-value min-h-[88px] w-full resize-none bg-transparent outline-none placeholder:text-text-secondary read-only:cursor-not-allowed"
                />
              </span>
              {errors[field.id] ? (
                <span className="text-[9px] text-danger">
                  {errors[field.id]}
                </span>
              ) : null}
            </>
          ) : (
            <TextField
              id={field.id}
              type={field.type ?? "text"}
              min={field.type === "number" ? 0 : undefined}
              placeholder={field.placeholder}
              value={values[field.id]}
              readOnly={field.readOnly}
              onChange={(event) => onChange(field.id, event.target.value)}
              error={errors[field.id]}
              containerClassName="module-glass-control !rounded-[4px] !px-3 !py-2"
              inputClassName="type-filter-value placeholder:text-text-secondary read-only:cursor-not-allowed"
            />
          )}
        </label>
      ))}
    </>
  );
}
