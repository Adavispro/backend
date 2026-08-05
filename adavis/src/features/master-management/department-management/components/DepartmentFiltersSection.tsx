"use client";

import Image from "next/image";
import type { StaticImageData } from "next/image";
import { CaretDown, X } from "@phosphor-icons/react";
import { Button } from "@/components/ui";
import clearIcon from "@/assets/icons/clear-icon.svg";
import departmentIcon from "@/assets/icons/department.svg";
import parentIcon from "@/assets/icons/parent.svg";
import plantIcon from "@/assets/icons/plant-icon.svg";
import statusIcon from "@/assets/status/roles.svg";

export interface DepartmentTableFilters {
  parents: string[];
  plants: string[];
  statuses: string[];
  tenants: string[];
}

interface DepartmentFiltersSectionProps {
  filters: DepartmentTableFilters;
  parentOptions: string[];
  plantOptions: string[];
  statusOptions: string[];
  tenantOptions: string[];
  onChange: (filters: DepartmentTableFilters) => void;
  onApply: () => void;
  onClear: () => void;
}

function addValue(values: string[], value: string) {
  if (!value || values.includes(value)) return values;
  return [...values, value];
}

function removeValue(values: string[], value: string) {
  return values.filter((item) => item !== value);
}

function FilterGroup({
  id,
  icon,
  label,
  options,
  placeholder,
  values,
  onAdd,
  onRemove,
}: {
  id: keyof DepartmentTableFilters;
  icon: StaticImageData;
  label: string;
  options: string[];
  placeholder: string;
  values: string[];
  onAdd: (field: keyof DepartmentTableFilters, value: string) => void;
  onRemove: (field: keyof DepartmentTableFilters, value: string) => void;
}) {
  return (
    <div className="grid gap-2.5">
      <label
        htmlFor={id}
        className="type-filter-label flex items-center gap-1.5 text-text-heading"
      >
        <Image src={icon} alt="" aria-hidden="true" className="h-3.5 w-3.5" />
        {label}
      </label>

      <span className="module-glass-control relative flex h-9 items-center rounded-[4px]">
        <select
          id={id}
          value=""
          onChange={(event) => onAdd(id, event.target.value)}
          className="type-filter-value h-full w-full appearance-none rounded-[4px] bg-transparent px-3 pr-8 text-text-secondary outline-none"
        >
          <option value="">{placeholder}</option>
          {options.map((option) => (
            <option key={option} value={option}>
              {option}
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

      {values.length > 0 ? (
        <div className="flex flex-wrap gap-2">
          {values.map((value) => (
            <button
              key={value}
              type="button"
              onClick={() => onRemove(id, value)}
              className="inline-flex items-center gap-1 rounded-full bg-[#EEF2F6] px-2 py-1 text-[9px] font-medium text-text-heading"
            >
              {value}
              <X size={9} weight="bold" />
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}

export default function DepartmentFiltersSection({
  filters,
  parentOptions,
  plantOptions,
  statusOptions,
  tenantOptions,
  onChange,
  onApply,
  onClear,
}: DepartmentFiltersSectionProps) {
  const handleAdd = (field: keyof DepartmentTableFilters, value: string) => {
    onChange({
      ...filters,
      [field]: addValue(filters[field], value),
    });
  };

  const handleRemove = (field: keyof DepartmentTableFilters, value: string) => {
    onChange({
      ...filters,
      [field]: removeValue(filters[field], value),
    });
  };

  return (
    <div className="grid gap-6">
      <FilterGroup
        id="tenants"
        icon={departmentIcon}
        label="Tenant"
        values={filters.tenants}
        options={tenantOptions}
        placeholder="Select Tenant"
        onAdd={handleAdd}
        onRemove={handleRemove}
      />

      <FilterGroup
        id="plants"
        icon={plantIcon}
        label="Plant"
        values={filters.plants}
        options={plantOptions}
        placeholder="Select Plant"
        onAdd={handleAdd}
        onRemove={handleRemove}
      />

      <FilterGroup
        id="parents"
        icon={parentIcon}
        label="Parent Department"
        values={filters.parents}
        options={parentOptions}
        placeholder="Select Parent Department"
        onAdd={handleAdd}
        onRemove={handleRemove}
      />

      <FilterGroup
        id="statuses"
        icon={statusIcon}
        label="Status"
        values={filters.statuses}
        options={statusOptions}
        placeholder="Select Status"
        onAdd={handleAdd}
        onRemove={handleRemove}
      />

      <div className="mt-4 flex justify-end gap-3">
        <Button
          type="button"
          variant="ghost"
          size="sm"
          prefixIcon={
            <Image
              src={clearIcon}
              alt=""
              aria-hidden="true"
              className="h-3.5 w-3.5"
            />
          }
          rounded="rounded-[4px]"
          textSize="text-[10px]"
          paddingX="px-4"
          paddingY="py-0"
          className="h-9 border-primary/35 bg-white/35 !text-primary hover:bg-white/65"
          onClick={onClear}
        >
          Clear All
        </Button>
        <Button
          type="button"
          size="sm"
          rounded="rounded-[4px]"
          textSize="text-[10px]"
          paddingX="px-5"
          paddingY="py-0"
          className="h-9 shadow-[0_8px_18px_rgba(7,92,175,0.18)]"
          onClick={onApply}
        >
          Apply Filters
        </Button>
      </div>
    </div>
  );
}
