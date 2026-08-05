import { CaretDown } from "@phosphor-icons/react/dist/ssr";
import type { FormEvent } from "react";
import { Button } from "@/components/ui";

export interface EquipmentFilterValues {
  plantId: string;
  blockId: string;
  areaId: string;
  roomNo: string;
}

export interface EquipmentFilterOption {
  value: string;
  label: string;
}

export interface EquipmentFilterOptions {
  plants: EquipmentFilterOption[];
  blocks: EquipmentFilterOption[];
  areas: EquipmentFilterOption[];
  rooms: EquipmentFilterOption[];
}

interface FilterFieldConfig {
  id: keyof EquipmentFilterValues;
  label: string;
  allLabel: string;
  options: EquipmentFilterOption[];
  required?: boolean;
}

function FilterSelectField({
  field,
  value,
  disabled,
  onChange,
}: {
  field: FilterFieldConfig;
  value: string;
  disabled?: boolean;
  onChange: (id: keyof EquipmentFilterValues, value: string) => void;
}) {
  return (
    <label htmlFor={field.id} className="grid gap-1.5">
      <span className="type-filter-label">
        {field.label}
      </span>
      <span className="module-glass-control relative flex h-9 items-center rounded-[4px]">
        <select
          id={field.id}
          name={field.id}
          value={value}
          disabled={disabled}
          required={field.required}
          onChange={(event) => onChange(field.id, event.target.value)}
          className="type-filter-value h-full w-full appearance-none rounded-[4px] bg-transparent px-3 pr-8 outline-none disabled:cursor-not-allowed disabled:opacity-60"
        >
          <option value={field.required ? "" : "all"}>{field.allLabel}</option>
          {field.options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <CaretDown
          aria-hidden="true"
          size={12}
          weight="bold"
          className="pointer-events-none absolute right-3 text-text-secondary"
        />
      </span>
    </label>
  );
}

export default function EquipmentFilterSection({
  values,
  options,
  onChange,
  onSubmit,
}: {
  values: EquipmentFilterValues;
  options: EquipmentFilterOptions;
  onChange: (id: keyof EquipmentFilterValues, value: string) => void;
  onSubmit: () => void;
}) {
  const filterFields: FilterFieldConfig[] = [
    {
      id: "plantId",
      label: "Plant Name",
      allLabel: "All Plants",
      options: options.plants,
    },
    {
      id: "blockId",
      label: "Block",
      allLabel: "All Blocks",
      options: options.blocks,
    },
    {
      id: "areaId",
      label: "Area",
      allLabel: "All Areas",
      options: options.areas,
    },
    {
      id: "roomNo",
      label: "Room No",
      allLabel: "All Rooms",
      options: options.rooms,
    },
  ];

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit();
  }

  return (
    <section
      aria-label="Equipment filters"
      className="module-glass-panel rounded-xl px-6 py-4"
    >
      <form
        className="grid items-end gap-5 md:grid-cols-[repeat(4,minmax(130px,1fr))_auto]"
        onSubmit={handleSubmit}
      >
        {filterFields.map((field) => (
          <FilterSelectField
            key={field.id}
            field={field}
            value={values[field.id]}
            onChange={onChange}
          />
        ))}

        <Button
          type="submit"
          size="sm"
          paddingX="px-6"
          paddingY="py-0"
          textSize="type-filter-button"
          rounded="rounded-[4px]"
          className="h-9 min-w-[78px] shadow-[0_8px_18px_rgba(7,92,175,0.18)]"
        >
          Submit
        </Button>
      </form>
    </section>
  );
}
