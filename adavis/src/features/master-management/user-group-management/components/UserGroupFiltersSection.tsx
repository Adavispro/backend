"use client";

import Image from "next/image";
import type { StaticImageData } from "next/image";
import { CaretDown, X } from "@phosphor-icons/react";
import { Button } from "@/components/ui";
import clearIcon from "@/assets/icons/clear-icon.svg";
import roleIcon from "@/assets/icons/roleName.svg";
import userGroupIcon from "@/assets/icons/userGroup.svg";

export interface UserGroupTableFilters {
  groupNames: string[];
  roles: string[];
}

interface UserGroupFiltersSectionProps {
  filters: UserGroupTableFilters;
  groupNameOptions: string[];
  roleOptions: string[];
  onChange: (filters: UserGroupTableFilters) => void;
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
  id: keyof UserGroupTableFilters;
  icon: StaticImageData;
  label: string;
  options: string[];
  placeholder: string;
  values: string[];
  onAdd: (field: keyof UserGroupTableFilters, value: string) => void;
  onRemove: (field: keyof UserGroupTableFilters, value: string) => void;
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

export default function UserGroupFiltersSection({
  filters,
  groupNameOptions,
  roleOptions,
  onChange,
  onApply,
  onClear,
}: UserGroupFiltersSectionProps) {
  const handleAdd = (field: keyof UserGroupTableFilters, value: string) => {
    onChange({
      ...filters,
      [field]: addValue(filters[field], value),
    });
  };

  const handleRemove = (field: keyof UserGroupTableFilters, value: string) => {
    onChange({
      ...filters,
      [field]: removeValue(filters[field], value),
    });
  };

  return (
    <div className="grid gap-6">
      <FilterGroup
        id="groupNames"
        icon={userGroupIcon}
        label="User Group Name"
        values={filters.groupNames}
        options={groupNameOptions}
        placeholder="Select User Groups"
        onAdd={handleAdd}
        onRemove={handleRemove}
      />

      <FilterGroup
        id="roles"
        icon={roleIcon}
        label="Roles"
        values={filters.roles}
        options={roleOptions}
        placeholder="Select Roles"
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
